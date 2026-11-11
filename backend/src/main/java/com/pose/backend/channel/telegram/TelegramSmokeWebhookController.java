package com.pose.backend.channel.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pose.backend.api.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

/**
 * Local-only connectivity probe for W12. It deliberately does not persist or process messages;
 * durable Telegram ingestion belongs to the AI-02 conversation implementation.
 */
@RestController
@Profile("telegram-smoke")
@ConditionalOnProperty(prefix = "pose.channel.telegram.smoke", name = "enabled", havingValue = "true")
@RequestMapping("/api/v1/channels/telegram")
public class TelegramSmokeWebhookController {
    private static final Logger log = LoggerFactory.getLogger(TelegramSmokeWebhookController.class);
    private static final int MAX_BODY_BYTES = 64 * 1024;

    private final String configuredSecret;
    private final ObjectMapper objectMapper;

    public TelegramSmokeWebhookController(
            org.springframework.core.env.Environment environment,
            ObjectMapper objectMapper) {
        String configuredSecret = environment.getProperty("pose.channel.telegram.webhook-secret", "");
        this.configuredSecret = configuredSecret == null ? "" : configuredSecret;
        this.objectMapper = objectMapper;
    }

    @PostMapping(path = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> receive(
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String suppliedSecret,
            HttpServletRequest request) {
        if (configuredSecret.isBlank()) {
            return error(request, 503, "CHANNEL_WEBHOOK_NOT_CONFIGURED",
                    "Kênh Telegram chưa được cấu hình.");
        }
        if (!matchesSecret(suppliedSecret)) {
            return error(request, 401, "CHANNEL_WEBHOOK_UNAUTHORIZED",
                    "Webhook không được xác thực.");
        }
        if (!isJson(request.getContentType())) {
            return error(request, 415, "UNSUPPORTED_MEDIA_TYPE",
                    "Webhook chỉ nhận nội dung JSON.");
        }
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            return error(request, 413, "REQUEST_TOO_LARGE",
                    "Payload webhook vượt quá giới hạn cho phép.");
        }

        final byte[] body;
        try (InputStream input = request.getInputStream()) {
            body = input.readNBytes(MAX_BODY_BYTES + 1);
        } catch (IOException exception) {
            return error(request, 400, "VALIDATION_ERROR", "Payload webhook không hợp lệ.");
        }
        if (body.length > MAX_BODY_BYTES) {
            return error(request, 413, "REQUEST_TOO_LARGE",
                    "Payload webhook vượt quá giới hạn cho phép.");
        }

        final JsonNode update;
        try {
            update = objectMapper.readTree(body);
        } catch (IOException exception) {
            return error(request, 400, "VALIDATION_ERROR", "Payload webhook không hợp lệ.");
        }
        if (!isMessageUpdate(update)) {
            return error(request, 400, "VALIDATION_ERROR",
                    "Payload không phải Telegram message update hợp lệ.");
        }

        long updateId = update.path("update_id").longValue();
        log.info("Telegram smoke webhook received update_id={}", updateId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("ok", true, "mode", "SMOKE_ONLY"));
    }

    private boolean matchesSecret(String suppliedSecret) {
        if (suppliedSecret == null || suppliedSecret.isBlank()) return false;
        return MessageDigest.isEqual(
                configuredSecret.getBytes(StandardCharsets.UTF_8),
                suppliedSecret.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isJson(String contentType) {
        if (contentType == null) return false;
        try {
            return MediaType.parseMediaType(contentType).isCompatibleWith(MediaType.APPLICATION_JSON);
        } catch (org.springframework.http.InvalidMediaTypeException exception) {
            return false;
        }
    }

    private static boolean isMessageUpdate(JsonNode update) {
        if (update == null || !update.isObject()) return false;
        JsonNode updateId = update.get("update_id");
        return updateId != null && updateId.isIntegralNumber() && updateId.canConvertToLong()
                && updateId.longValue() >= 0 && update.path("message").isObject();
    }

    private static ResponseEntity<ApiError> error(
            HttpServletRequest request, int status, String code, String message) {
        ApiError body = ApiError.of(request, code, message, List.<ApiError.FieldError>of());
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.noStore()).header("X-Trace-Id", body.traceId()).body(body);
    }
}
