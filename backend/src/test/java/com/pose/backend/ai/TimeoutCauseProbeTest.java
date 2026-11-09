package com.pose.backend.ai;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.LogLevel;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeoutCauseProbeTest {
    @Test void sdkTimeoutCauseIsClassifiedAsTimeout() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        var client = AnthropicOkHttpClient.builder().apiKey("fake-test-only")
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .timeout(Duration.ofSeconds(1)).maxRetries(0).logLevel(LogLevel.OFF).openTelemetry(false).build();
        try {
            var request = MessageCreateParams.builder().model("claude-haiku-5-5").maxTokens(10).addUserMessage("test").build();
            assertThatThrownBy(() -> client.beta().messages().create(request))
                    .isInstanceOf(AnthropicIoException.class)
                    .satisfies(ex -> assertThat(ClaudeProvider.ioErrorCode((AnthropicIoException) ex)).isEqualTo("TIMEOUT"));
        } finally { release.countDown(); client.close(); server.stop(0); }
    }
}
