package com.pose.backend.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pose.backend.api.ApiRequestException;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class TelegramReplyAdapter implements ChannelReplyAdapter {
    private final Environment environment;
    private final ObjectMapper mapper;
    private volatile HttpClient http;

    public TelegramReplyAdapter(Environment environment, ObjectMapper mapper) {
        this.environment = environment;
        this.mapper = mapper;
    }

    @Override
    public String channelType() {
        return "TELEGRAM";
    }

    @Override
    public String send(String externalThreadId, String text) {
        String token = environment.getProperty("POSE_CHANNEL_TELEGRAM_BOT_TOKEN");
        if (token == null || token.isBlank()) throw ApiRequestException.unavailable("CHANNEL_REPLY_NOT_CONFIGURED");
        try {
            var payload = mapper.createObjectNode().put("chat_id", externalThreadId).put("text", text);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.telegram.org/bot" + token + "/sendMessage"))
                    .timeout(Duration.ofSeconds(12))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw ApiRequestException.badGateway("CHANNEL_REPLY_FAILED");
            }
            JsonNode result = mapper.readTree(response.body());
            JsonNode messageId = result.path("result").path("message_id");
            if (!result.path("ok").asBoolean(false) || !messageId.canConvertToLong()) {
                throw ApiRequestException.badGateway("CHANNEL_REPLY_FAILED");
            }
            return Long.toString(messageId.longValue());
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
