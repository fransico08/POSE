package com.pose.backend.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pose.backend.api.ApiRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class MessengerReplyAdapter implements ChannelReplyAdapter {
    private static final String DEFAULT_GRAPH_API_VERSION = "v27.0";
    private final Environment environment;
    private final ObjectMapper mapper;
    private volatile HttpClient http;

    @Autowired
    public MessengerReplyAdapter(Environment environment, ObjectMapper mapper) {
        this.environment = environment;
        this.mapper = mapper;
    }

    MessengerReplyAdapter(Environment environment, ObjectMapper mapper, HttpClient http) {
        this.environment = environment;
        this.mapper = mapper;
        this.http = http;
    }

    @Override
    public String channelType() {
        return "MESSENGER";
    }

    @Override
    public String send(String externalThreadId, String text) {
        throw ApiRequestException.unavailable("CHANNEL_REPLY_NOT_CONFIGURED");
    }

    @Override
    public String send(String pageId, String psid, String text) {
        String token = environment.getProperty("POSE_CHANNEL_MESSENGER_PAGE_TOKEN");
        String version = environment.getProperty("POSE_CHANNEL_MESSENGER_GRAPH_API_VERSION", DEFAULT_GRAPH_API_VERSION);
        if (token == null || token.isBlank() || pageId == null || pageId.isBlank()
                || !version.matches("v[0-9]{1,3}\\.[0-9]")) {
            throw ApiRequestException.unavailable("CHANNEL_REPLY_NOT_CONFIGURED");
        }
        try {
            var payload = mapper.createObjectNode();
            payload.putObject("recipient").put("id", psid);
            payload.putObject("message").put("text", text);
            URI uri = URI.create("https://graph.facebook.com/" + version + "/" + pageId + "/messages");
            HttpRequest request = HttpRequest.newBuilder().uri(uri).timeout(Duration.ofSeconds(12))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw ApiRequestException.badGateway("CHANNEL_REPLY_FAILED");
            }
            JsonNode result = mapper.readTree(response.body());
            JsonNode messageId = result.get("message_id");
            if (messageId == null || !messageId.isTextual() || messageId.asText().isBlank()) {
                throw ApiRequestException.badGateway("CHANNEL_REPLY_FAILED");
            }
            return messageId.asText();
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw ApiRequestException.badGateway("CHANNEL_REPLY_FAILED");
        } catch (Exception exception) {
            throw ApiRequestException.badGateway("CHANNEL_REPLY_FAILED");
        }
    }

    private HttpClient client() {
        HttpClient current = http;
        if (current != null) return current;
        synchronized (this) {
            if (http == null) http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
            return http;
        }
    }
}
