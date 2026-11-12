package com.pose.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

class GeminiProviderTest {
    private static final String KEY = "fake-gemini-test-only";
    private static final ObjectMapper JSON = new ObjectMapper();
    private HttpServer server;
    @AfterEach void stop() { if (server != null) server.stop(0); }

    private MockEnvironment env() { return new MockEnvironment().withProperty("pose.ai.provider", "gemini").withProperty("pose.ai.max-retries", "1"); }
    private GeminiProvider provider(MockEnvironment env) { return new GeminiProvider(new AiSettings(env, () -> "", () -> KEY)); }
    private GeminiProvider provider() { return provider(env()); }

    private String server(int status, String body, AtomicInteger calls, AtomicReference<String> request,
                          AtomicReference<String> keyHeader, AtomicReference<String> path, AtomicReference<String> query) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            keyHeader.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            path.set(exchange.getRequestURI().getPath());
            query.set(exchange.getRequestURI().getRawQuery());
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static String ok(String text) {
        return "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":" + quote(text) + "}]},\"finishReason\":\"STOP\"}],"
                + "\"usageMetadata\":{\"promptTokenCount\":10,\"candidatesTokenCount\":5,\"thoughtsTokenCount\":3,\"totalTokenCount\":18}}";
    }
    private static String quote(String text) { try { return JSON.writeValueAsString(text); } catch (Exception ex) { throw new IllegalStateException(ex); } }

    private static final Map<String, Object> SCHEMA = Map.of("type", "object", "additionalProperties", false,
            "required", List.of("sections"), "properties", Map.of("sections", Map.of("type", "array", "minItems", 0, "items",
                    Map.of("type", "object", "additionalProperties", false, "properties", Map.of("heading", Map.of("type", "string", "maxLength", 80))))));

    @Test void sendsKeyInHeaderOnlyAndBuildsRequestWithSchema() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> request = new AtomicReference<>(), key = new AtomicReference<>(), path = new AtomicReference<>(), query = new AtomicReference<>();
        String url = server(200, ok("{\"sections\":[]}"), calls, request, key, path, query);
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logs = new ListAppender<>(); logs.start(); root.addAppender(logs);
        var reply = provider().generate(new AiProvider.Request(AiUseCase.C360_SUMMARY, "system prompt text", "masked input text", SCHEMA), url);
        root.detachAppender(logs);
        assertThat(reply.errorCode()).isNull();
        assertThat(reply.text()).isEqualTo("{\"sections\":[]}");
        assertThat(reply.inputTokens()).isEqualTo(10);
        assertThat(reply.outputTokens()).isEqualTo(8);
        assertThat(key.get()).isEqualTo(KEY);
        assertThat(path.get()).isEqualTo("/v1beta/models/gemini-3.6-flash:generateContent");
        assertThat(query.get()).isNull();
        assertThat(logs.list.toString()).doesNotContain(KEY, "system prompt text", "masked input text");
        JsonNode body = JSON.readTree(request.get());
        assertThat(body.at("/systemInstruction/parts/0/text").asText()).isEqualTo("system prompt text");
        assertThat(body.at("/contents/0/role").asText()).isEqualTo("user");
        assertThat(body.at("/contents/0/parts/0/text").asText()).isEqualTo("masked input text");
        assertThat(body.at("/generationConfig/maxOutputTokens").asInt()).isEqualTo(16000);
        assertThat(body.at("/generationConfig/responseMimeType").asText()).isEqualTo("application/json");
        assertThat(body.at("/generationConfig/responseJsonSchema/type").asText()).isEqualTo("object");
        assertThat(body.toString()).doesNotContain(KEY);
    }

    @Test void schemaConversionDropsIgnoredKeywordsOnly() {
        Map<String, Object> converted = GeminiProvider.toGeminiSchema(SCHEMA);
        String json = converted.toString();
        assertThat(json).doesNotContain("maxLength").contains("additionalProperties", "minItems", "required");
        assertThat(SCHEMA.toString()).contains("maxLength");
    }

    @Test void noSchemaMeansNoStructuredOutputFields() throws Exception {
        var body = provider().requestBody(new AiProvider.Request(AiUseCase.REPLY_DRAFT, "p", "i", null));
        assertThat(JSON.valueToTree(body).at("/generationConfig").has("responseMimeType")).isFalse();
        var disabled = provider(env().withProperty("pose.ai.gemini.structured-output", "false"))
                .requestBody(new AiProvider.Request(AiUseCase.C360_SUMMARY, "p", "i", SCHEMA));
        assertThat(JSON.valueToTree(disabled).at("/generationConfig").has("responseJsonSchema")).isFalse();
    }

    @Test void blockedPromptOrSafetyFinishBecomesRefusal() {
        var blocked = GeminiProvider.parse("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"},\"usageMetadata\":{\"promptTokenCount\":4}}");
        assertThat(blocked.errorCode()).isEqualTo("REFUSAL");
        assertThat(blocked.inputTokens()).isEqualTo(4);
        var finish = GeminiProvider.parse("{\"candidates\":[{\"finishReason\":\"PROHIBITED_CONTENT\"}]}");
        assertThat(finish.errorCode()).isEqualTo("REFUSAL");
    }

    @Test void emptyOrMalformedResponsesAreProviderErrorsAndThoughtPartsAreSkipped() {
        assertThat(GeminiProvider.parse("{\"candidates\":[]}").errorCode()).isEqualTo("PROVIDER_ERROR");
        assertThat(GeminiProvider.parse("not json").errorCode()).isEqualTo("PROVIDER_ERROR");
        assertThat(GeminiProvider.parse("{\"candidates\":[{\"content\":{\"parts\":[]},\"finishReason\":\"STOP\"}]}").errorCode()).isEqualTo("PROVIDER_ERROR");
        var withThought = GeminiProvider.parse("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"hidden\",\"thought\":true},{\"text\":\"shown\"}]}}]}");
        assertThat(withThought.text()).isEqualTo("shown");
    }

    @Test void httpStatusesMapToStandardCodes() {
        assertThat(GeminiProvider.errorCodeForStatus(429)).isEqualTo("RATE_LIMIT");
        assertThat(GeminiProvider.errorCodeForStatus(408)).isEqualTo("TIMEOUT");
        assertThat(GeminiProvider.errorCodeForStatus(504)).isEqualTo("TIMEOUT");
        assertThat(GeminiProvider.errorCodeForStatus(500)).isEqualTo("PROVIDER_ERROR");
        assertThat(GeminiProvider.errorCodeForStatus(503)).isEqualTo("PROVIDER_ERROR");
        assertThat(GeminiProvider.errorCodeForStatus(400)).isEqualTo("BAD_REQUEST");
        assertThat(GeminiProvider.errorCodeForStatus(403)).isEqualTo("BAD_REQUEST");
        assertThat(GeminiProvider.errorCodeForStatus(404)).isEqualTo("BAD_REQUEST");
    }

    @Test void rateLimitRetriesOnceThenReturnsRateLimit() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        String url = server(429, "{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\"}}", calls, new AtomicReference<>(), new AtomicReference<>(), new AtomicReference<>(), new AtomicReference<>());
        assertThat(provider().generate(new AiProvider.Request(AiUseCase.SIGNAL_EXTRACT, "p", "i", null), url).errorCode()).isEqualTo("RATE_LIMIT");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test void failedCallsLogHttpStatusWithoutKeyOrContent() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        String url = server(503, "{\"error\":{\"message\":\"secret body text\"}}", calls, new AtomicReference<>(), new AtomicReference<>(), new AtomicReference<>(), new AtomicReference<>());
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logs = new ListAppender<>(); logs.start(); root.addAppender(logs);
        var reply = provider().generate(new AiProvider.Request(AiUseCase.SIGNAL_EXTRACT, "system prompt text", "masked input text", null), url);
        root.detachAppender(logs);
        assertThat(reply.errorCode()).isEqualTo("PROVIDER_ERROR");
        String text = logs.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + " | " + b);
        assertThat(text).contains("status=503", "code=PROVIDER_ERROR", "attempt=1/2", "attempt=2/2");
        assertThat(text).doesNotContain(KEY, "system prompt text", "masked input text", "secret body text");
    }

    @Test void clientErrorsAreNotRetriedAndServerErrorsAre() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        String url = server(400, "{}", calls, new AtomicReference<>(), new AtomicReference<>(), new AtomicReference<>(), new AtomicReference<>());
        assertThat(provider().generate(new AiProvider.Request(AiUseCase.C360_SUMMARY, "p", "i", null), url).errorCode()).isEqualTo("BAD_REQUEST");
        assertThat(calls.get()).isEqualTo(1);
        server.stop(0);
        AtomicInteger serverCalls = new AtomicInteger();
        String url500 = server(500, "{}", serverCalls, new AtomicReference<>(), new AtomicReference<>(), new AtomicReference<>(), new AtomicReference<>());
        assertThat(provider().generate(new AiProvider.Request(AiUseCase.C360_SUMMARY, "p", "i", null), url500).errorCode()).isEqualTo("PROVIDER_ERROR");
        assertThat(serverCalls.get()).isEqualTo(2);
    }

    @Test void ioErrorsMapTimeoutAndNetworkWithoutDependingOnTiming() {
        assertThat(GeminiProvider.ioErrorCode(new InterruptedIOException("timeout"))).isEqualTo("TIMEOUT");
        assertThat(GeminiProvider.ioErrorCode(new HttpTimeoutException("request timed out"))).isEqualTo("TIMEOUT");
        assertThat(GeminiProvider.ioErrorCode(new java.io.IOException("wrap", new HttpTimeoutException("x")))).isEqualTo("TIMEOUT");
        assertThat(GeminiProvider.ioErrorCode(new ConnectException("refused"))).isEqualTo("NETWORK");
    }

    @Test void realSocketTimeoutBecomesTimeout() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> { try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); } finally { exchange.close(); } });
        server.start();
        var env = new MockEnvironment().withProperty("pose.ai.provider", "gemini").withProperty("pose.ai.timeout-seconds", "1").withProperty("pose.ai.max-retries", "0");
        try {
            assertThat(provider(env).generate(new AiProvider.Request(AiUseCase.SIGNAL_EXTRACT, "p", "i", null),
                    "http://127.0.0.1:" + server.getAddress().getPort()).errorCode()).isEqualTo("TIMEOUT");
        } finally { release.countDown(); }
    }

    @Test void missingKeyIsRejectedBeforeAnyCall() {
        var noKey = new GeminiProvider(new AiSettings(env(), () -> "", () -> ""));
        assertThat(noKey.generate(new AiProvider.Request(AiUseCase.SIGNAL_EXTRACT, "p", "i", null), "http://127.0.0.1:1").errorCode()).isEqualTo("BAD_REQUEST");
        var badModel = provider(env().withProperty("pose.ai.gemini.models.signal-extract", "../evil?x=1"));
        assertThat(badModel.generate(new AiProvider.Request(AiUseCase.SIGNAL_EXTRACT, "p", "i", null), "http://127.0.0.1:1").errorCode()).isEqualTo("BAD_REQUEST");
    }
}
