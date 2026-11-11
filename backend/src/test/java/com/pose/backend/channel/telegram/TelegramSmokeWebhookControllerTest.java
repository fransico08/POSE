package com.pose.backend.channel.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pose.backend.api.ApiError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramSmokeWebhookControllerTest {
    private static final String WEBHOOK = "/api/v1/channels/telegram/webhook";
    private static final String HEADER = "X-Telegram-Bot-Api-Secret-Token";
    private static final String TEST_SECRET = "synthetic-unit-test-secret";
    private static final String MESSAGE_UPDATE =
            "{\"update_id\":121,\"message\":{\"message_id\":7,\"text\":\"private test body\"}}";

    private TelegramSmokeWebhookController controller;

    @BeforeEach
    void setUp() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("pose.channel.telegram.webhook-secret", TEST_SECRET);
        controller = new TelegramSmokeWebhookController(environment, new ObjectMapper());
    }

    @Test
    void acceptsMessageUpdateWithWebhookSecretAndDoesNotEchoPayload() throws Exception {
        ResponseEntity<?> response = send(MESSAGE_UPDATE.getBytes(StandardCharsets.UTF_8),
                MediaType.APPLICATION_JSON_VALUE, TEST_SECRET);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody()).isInstanceOf(Map.class);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("ok")).isEqualTo(true);
        assertThat(body.get("mode")).isEqualTo("SMOKE_ONLY");
        assertThat(body.toString()).doesNotContain("private test body", TEST_SECRET);
    }

    @Test
    void rejectsMissingOrIncorrectWebhookSecret() throws Exception {
        assertError(send(MESSAGE_UPDATE.getBytes(StandardCharsets.UTF_8),
                MediaType.APPLICATION_JSON_VALUE, null), 401, "CHANNEL_WEBHOOK_UNAUTHORIZED");
        assertError(send(MESSAGE_UPDATE.getBytes(StandardCharsets.UTF_8),
                MediaType.APPLICATION_JSON_VALUE, "incorrect-synthetic-secret"),
                401, "CHANNEL_WEBHOOK_UNAUTHORIZED");
    }

    @Test
    void rejectsRequestsWhenWebhookSecretIsNotConfigured() throws Exception {
        controller = new TelegramSmokeWebhookController(new MockEnvironment(), new ObjectMapper());
        assertError(send(MESSAGE_UPDATE.getBytes(StandardCharsets.UTF_8),
                MediaType.APPLICATION_JSON_VALUE, TEST_SECRET), 503, "CHANNEL_WEBHOOK_NOT_CONFIGURED");
    }

    @Test
    void rejectsMalformedAndNonMessageUpdates() throws Exception {
        assertError(send("{".getBytes(StandardCharsets.UTF_8),
                MediaType.APPLICATION_JSON_VALUE, TEST_SECRET), 400, "VALIDATION_ERROR");
        assertError(send("{\"update_id\":121}".getBytes(StandardCharsets.UTF_8),
                MediaType.APPLICATION_JSON_VALUE, TEST_SECRET), 400, "VALIDATION_ERROR");
    }

    @Test
    void rejectsOversizedPayloadAndUnsupportedMediaType() throws Exception {
        byte[] oversized = new byte[64 * 1024 + 1];
        assertError(send(oversized, MediaType.APPLICATION_JSON_VALUE, TEST_SECRET),
                413, "REQUEST_TOO_LARGE");
        assertError(send(MESSAGE_UPDATE.getBytes(StandardCharsets.UTF_8),
                MediaType.TEXT_PLAIN_VALUE, TEST_SECRET), 415, "UNSUPPORTED_MEDIA_TYPE");
    }

    private ResponseEntity<?> send(byte[] content, String contentType, String suppliedSecret) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI(WEBHOOK);
        request.setContentType(contentType);
        request.setContent(content);
        if (suppliedSecret != null) request.addHeader(HEADER, suppliedSecret);
        return controller.receive(suppliedSecret, request);
    }

    private static void assertError(ResponseEntity<?> response, int status, String code) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getBody()).isInstanceOf(ApiError.class);
        assertThat(((ApiError) response.getBody()).code()).isEqualTo(code);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    }
}
