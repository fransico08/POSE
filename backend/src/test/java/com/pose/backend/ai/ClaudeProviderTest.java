package com.pose.backend.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import static org.assertj.core.api.Assertions.assertThat;

class ClaudeProviderTest {
    private HttpServer server;
    private final AtomicReference<String> beta=new AtomicReference<>();
    @AfterEach void stop() {if(server!=null)server.stop(0);}
    private String server(int status,String body,AtomicInteger calls,AtomicReference<String> request,AtomicReference<String> key) throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/messages",exchange->{calls.incrementAndGet();request.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));key.set(exchange.getRequestHeaders().getFirst("Authorization"));beta.set(exchange.getRequestHeaders().getFirst("anthropic-beta"));exchange.getResponseHeaders().add("Content-Type","application/json");exchange.getResponseHeaders().add("retry-after","0");byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});
        server.start();return "http://127.0.0.1:"+server.getAddress().getPort();
    }
    private ClaudeProvider provider(){return new ClaudeProvider(new AiSettings(new MockEnvironment().withProperty("pose.ai.max-retries","1"),()->"fake-test-only"));}
    private static String response(String reason){return "{\"id\":\"test\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-sonnet-5-5\",\"content\":[{\"type\":\"text\",\"text\":\"ok\"}],\"stop_reason\":\""+reason+"\",\"stop_sequence\":null,\"usage\":{\"input_tokens\":10,\"output_tokens\":5}}";}
    @Test void sendsOfficialModelEffortFallbackAndSchemaWithoutSamplingParameters() throws Exception {
        AtomicInteger calls=new AtomicInteger();AtomicReference<String> request=new AtomicReference<>(),key=new AtomicReference<>();
        String url=server(200,response("end_turn"),calls,request,key);
        Logger root=(Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);ListAppender<ILoggingEvent> logs=new ListAppender<>();logs.start();root.addAppender(logs);
        var reply=provider().generate(new AiProvider.Request(AiUseCase.C360_SUMMARY,"prompt","masked",Map.of("type","object","properties",Map.of(),"additionalProperties",false)),url);
        root.detachAppender(logs);assertThat(logs.list.toString()).doesNotContain("fake-test-only","masked","prompt");
        assertThat(reply.errorCode()).isNull();assertThat(reply.text()).isEqualTo("ok");assertThat(key.get()).isEqualTo("Bearer fake-test-only");
        var json=new ObjectMapper().readTree(request.get());assertThat(json.path("model").asText()).isEqualTo("claude-sonnet-5-5");
        assertThat(json.path("max_tokens").asInt()).isEqualTo(16000);assertThat(json.at("/output_config/effort").asText()).isEqualTo("medium");
        assertThat(json.at("/output_config/format/schema/type").asText()).isEqualTo("object");assertThat(json.path("fallbacks").asText()).isEqualTo("default");
        assertThat(beta.get()).contains("server-side-fallback-2026-07-01");
        for(String absent:new String[]{"temperature","top_p","top_k","thinking","budget_tokens"})assertThat(json.has(absent)).isFalse();
    }
    @Test void refusalIsNotExposedAsSuccessfulText() throws Exception {
        AtomicInteger calls=new AtomicInteger();String url=server(200,response("refusal"),calls,new AtomicReference<>(),new AtomicReference<>());
        var reply=provider().generate(new AiProvider.Request(AiUseCase.REPLY_DRAFT,"prompt","masked",null),url);
        assertThat(reply.errorCode()).isEqualTo("REFUSAL");assertThat(reply.inputTokens()).isEqualTo(10);assertThat(reply.outputTokens()).isEqualTo(5);
    }
    @Test void rateLimitRetriesAndReturnsNormalizedError() throws Exception {
        AtomicInteger calls=new AtomicInteger();AtomicReference<String> request=new AtomicReference<>();String url=server(429,"{\"type\":\"error\",\"error\":{\"type\":\"rate_limit_error\",\"message\":\"limited\"}}",calls,request,new AtomicReference<>());
        assertThat(provider().generate(new AiProvider.Request(AiUseCase.SIGNAL_EXTRACT,"prompt","masked",null),url).errorCode()).isEqualTo("RATE_LIMIT");assertThat(calls.get()).isEqualTo(2);
        var json=new ObjectMapper().readTree(request.get());assertThat(json.path("model").asText()).isEqualTo("claude-haiku-5-5");assertThat(json.at("/output_config/effort").asText()).isEqualTo("low");assertThat(json.has("fallbacks")).isFalse();
    }
    @Test void socketTimeoutBecomesTimeoutError() throws Exception {
        CountDownLatch release=new CountDownLatch(1);
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/messages",exchange->{try {release.await(5,TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}finally{exchange.close();}});server.start();
        var settings=new AiSettings(new MockEnvironment().withProperty("pose.ai.timeout-seconds","1").withProperty("pose.ai.max-retries","0"),()->"fake-test-only");
        try {assertThat(new ClaudeProvider(settings).generate(new AiProvider.Request(AiUseCase.SIGNAL_EXTRACT,"prompt","masked",null),"http://127.0.0.1:"+server.getAddress().getPort()).errorCode()).isEqualTo("TIMEOUT");}
        finally {release.countDown();}
    }
}
