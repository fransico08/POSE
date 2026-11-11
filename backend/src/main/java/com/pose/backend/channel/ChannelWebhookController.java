package com.pose.backend.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pose.backend.api.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@RestController
@Profile("!telegram-smoke")
@RequestMapping("/api/v1/channels")
public class ChannelWebhookController {
    private static final int MAX_BODY_BYTES = 64 * 1024;
    private final ChannelWebhookService service;
    private final ObjectMapper mapper;

    public ChannelWebhookController(ChannelWebhookService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping(path = "/{type}/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> receive(
            @PathVariable String type,
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String telegramSecret,
            @RequestHeader(value = "X-POSE-Webhook-Secret", required = false) String zaloSecret,
            HttpServletRequest request) {
        if (!isJson(request.getContentType())) return error(request, 415, "UNSUPPORTED_MEDIA_TYPE", "Webhook chỉ nhận nội dung JSON.");
        if (request.getContentLengthLong() > MAX_BODY_BYTES) return error(request, 413, "REQUEST_TOO_LARGE", "Payload webhook vượt quá giới hạn cho phép.");

        final byte[] body;
        try (InputStream input = request.getInputStream()) {
            body = input.readNBytes(MAX_BODY_BYTES + 1);
        } catch (IOException exception) {
            return error(request, 400, "VALIDATION_ERROR", "Payload webhook không hợp lệ.");
        }
        if (body.length > MAX_BODY_BYTES) return error(request, 413, "REQUEST_TOO_LARGE", "Payload webhook vượt quá giới hạn cho phép.");

        final JsonNode payload;
        try {
            payload = mapper.readTree(body);
        } catch (IOException exception) {
            return error(request, 400, "VALIDATION_ERROR", "Payload webhook không hợp lệ.");
        }
        String normalized = type.replace('-', '_');
        String secret = "zalo_sim".equalsIgnoreCase(normalized) ? zaloSecret : telegramSecret;
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(service.receive(type, secret, payload));
        } catch (ChannelWebhookException exception) {
            return error(request, exception.status(), exception.code(), exception.getMessage());
        }
    }

    private static boolean isJson(String contentType) {
        if (contentType == null) return false;
        try {
            return MediaType.parseMediaType(contentType).isCompatibleWith(MediaType.APPLICATION_JSON);
        } catch (org.springframework.http.InvalidMediaTypeException exception) {
            return false;
        }
    }

    private static ResponseEntity<ApiError> error(HttpServletRequest request, int status, String code, String message) {
        ApiError body = ApiError.of(request, code, message, List.of());
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.noStore()).header("X-Trace-Id", body.traceId()).body(body);
    }
}
