package com.pose.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Gemini REST provider (DEC-22). Uses only the JDK HttpClient. The API key is sent in the
 * {@code x-goog-api-key} header, never in the URL; prompts, inputs and keys are never logged.
 */
@Component
public class GeminiProvider implements AiProvider {
    private static final Logger LOG = LoggerFactory.getLogger(GeminiProvider.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern MODEL = Pattern.compile("[A-Za-z0-9._-]{1,100}");
    /** JSON Schema keywords the Gemini structured-output page says are ignored; removed before sending. */
    private static final Set<String> UNSUPPORTED = Set.of("minProperties", "maxProperties", "minLength", "maxLength",
            "uniqueItems", "contains", "minContains", "maxContains", "$schema");
    private static final Set<String> BLOCKED_FINISH = Set.of("SAFETY", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII",
            "IMAGE_SAFETY", "IMAGE_PROHIBITED_CONTENT", "RECITATION", "ESCALATION");
    private final AiSettings settings;

    public GeminiProvider(AiSettings settings) { this.settings = settings; }

    @Override public Reply generate(Request request) { return generate(request, settings.geminiBaseUrl()); }

    Reply generate(Request request, String baseUrl) {
        String model = settings.model(request.useCase());
        if (!MODEL.matcher(model).matches() || settings.geminiApiKey().isBlank()) return Reply.failed("BAD_REQUEST");
        final String body;
        try { body = JSON.writeValueAsString(requestBody(request)); }
        catch (IOException ex) { return Reply.failed("BAD_REQUEST"); }
        URI uri = URI.create(baseUrl.replaceAll("/+$", "") + "/v1beta/models/" + model + ":generateContent");
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(Math.max(1, settings.timeout()))).build();
        int attempts = 1 + Math.max(0, settings.retries());
        Reply last = Reply.failed("PROVIDER_ERROR");
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                HttpRequest http = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(Math.max(1, settings.timeout())))
                        .header("Content-Type", "application/json").header("x-goog-api-key", settings.geminiApiKey())
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
                HttpResponse<String> response = client.send(http, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = response.statusCode();
                if (status >= 200 && status < 300) return parse(response.body());
                last = Reply.failed(errorCodeForStatus(status));
                LOG.warn("Gemini HTTP status={} code={} attempt={}/{}", status, last.errorCode(), attempt, attempts);
                if (!retryable(status)) return last;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return Reply.failed("NETWORK");
            } catch (IOException ex) {
                String code = ioErrorCode(ex);
                LOG.warn("Gemini I/O failure type={} code={} attempt={}/{}", ex.getClass().getSimpleName(), code, attempt, attempts);
                return Reply.failed(code);
            } catch (RuntimeException ex) {
                LOG.warn("Gemini request could not be sent type={} code=PROVIDER_ERROR attempt={}/{}", ex.getClass().getSimpleName(), attempt, attempts);
                return Reply.failed("PROVIDER_ERROR");
            }
            if (attempt < attempts) {
                try { Thread.sleep(Math.min(1000L, 200L * attempt)); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); return last; }
            }
        }
        return last;
    }

    Map<String, Object> requestBody(Request request) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("systemInstruction", Map.of("parts", List.of(Map.of("text", request.prompt()))));
        root.put("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", request.input())))));
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("maxOutputTokens", settings.maxTokens());
        if (request.jsonSchema() != null && settings.geminiStructuredOutput()) {
            config.put("responseMimeType", "application/json");
            config.put("responseJsonSchema", toGeminiSchema(request.jsonSchema()));
        }
        String thinking = settings.geminiThinkingLevel();
        if (!thinking.isBlank()) config.put("thinkingConfig", Map.of("thinkingLevel", thinking));
        root.put("generationConfig", config);
        return root;
    }

    /** Copies a JSON Schema, dropping keywords Gemini ignores. The gateway's source filter remains the final check. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> toGeminiSchema(Map<String, Object> schema) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (var entry : schema.entrySet()) {
            if (UNSUPPORTED.contains(entry.getKey())) continue;
            out.put(entry.getKey(), convert(entry.getValue()));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Object convert(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (var entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (UNSUPPORTED.contains(key)) continue;
                copy.put(key, convert(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            for (Object item : list) copy.add(convert(item));
            return copy;
        }
        return value;
    }

    static Reply parse(String body) {
        final JsonNode root;
        try { root = JSON.readTree(body); }
        catch (IOException ex) { return Reply.failed("PROVIDER_ERROR"); }
        JsonNode usage = root.path("usageMetadata");
        long input = usage.path("promptTokenCount").asLong(0);
        long output = usage.path("candidatesTokenCount").asLong(0) + usage.path("thoughtsTokenCount").asLong(0);
        if (!root.path("promptFeedback").path("blockReason").asText("").isBlank())
            return new Reply(null, input, output, "REFUSAL");
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            LOG.warn("Gemini returned no candidates");
            return new Reply(null, input, output, "PROVIDER_ERROR");
        }
        JsonNode candidate = candidates.get(0);
        if (BLOCKED_FINISH.contains(candidate.path("finishReason").asText("")))
            return new Reply(null, input, output, "REFUSAL");
        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            if (part.path("thought").asBoolean(false)) continue;
            if (part.path("text").isTextual()) text.append(part.path("text").asText());
        }
        if (text.length() == 0) {
            LOG.warn("Gemini returned no text finishReason={}", candidate.path("finishReason").asText("none"));
            return new Reply(null, input, output, "PROVIDER_ERROR");
        }
        return new Reply(text.toString(), input, output, null);
    }

    static String errorCodeForStatus(int status) {
        if (status == 429) return "RATE_LIMIT";
        if (status == 408 || status == 504) return "TIMEOUT";
        if (status >= 500) return "PROVIDER_ERROR";
        return "BAD_REQUEST";
    }

    private static boolean retryable(int status) { return status == 429 || status == 408 || status >= 500; }

    static String ioErrorCode(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause())
            if (t instanceof InterruptedIOException || t instanceof java.net.http.HttpTimeoutException) return "TIMEOUT";
        return "NETWORK";
    }
}
