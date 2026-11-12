package com.pose.backend.ai;

import com.pose.backend.identity.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiGeminiGatewayTest {
    private static final ActorContext ACTOR = new ActorContext(UUID.randomUUID(), UUID.randomUUID(), Set.of(RoleCode.SALES_REP), Set.of(PermissionCode.AI_ASSIST_USE), Set.of());
    private static final Map<String, Object> SCHEMA = Map.of("type", "object");

    private AiGateway gateway(AiSettings settings, GeminiProvider gemini, ClaudeProvider claude, MockProvider mock, AiInvocationWriter writer, String context) {
        AiContextBuilder contexts = mock(AiContextBuilder.class);
        when(contexts.build(any(), anyString(), any(), any())).thenReturn(context);
        PromptRegistry prompts = mock(PromptRegistry.class);
        when(prompts.prompt(any())).thenReturn("test"); when(prompts.version(any())).thenReturn("test.v1");
        return new AiGateway(settings, contexts, new Redactor(), mock(BudgetGuard.class), mock, claude, gemini, prompts, writer,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), mock(JdbcTemplate.class));
    }

    @Test void missingGeminiKeyDisablesWithoutContactingAnyProvider() {
        AiSettings settings = mock(AiSettings.class);
        when(settings.enabled()).thenReturn(true); when(settings.provider()).thenReturn("gemini"); when(settings.keyMissing()).thenReturn(true);
        when(settings.model(any())).thenReturn("gemini-test");
        GeminiProvider gemini = mock(GeminiProvider.class); ClaudeProvider claude = mock(ClaudeProvider.class); MockProvider mock = mock(MockProvider.class);
        AiInvocationWriter writer = mock(AiInvocationWriter.class);
        var result = gateway(settings, gemini, claude, mock, writer, "{}").invoke(ACTOR, AiUseCase.C360_SUMMARY, "CUSTOMER", UUID.randomUUID(), Map.of());
        assertThat(result.status()).isEqualTo("DISABLED");
        verifyNoInteractions(gemini, claude, mock);
        verify(writer).write(any(), eq(ACTOR), any(), any(), any(), eq("gemini"), eq("gemini-test"), any(), isNull(), eq(0), isNull(), isNull(), isNull(), anyLong(), eq("DISABLED"), isNull(), isNull(), any());
    }

    @Test void geminiOutputGoesThroughTheSameSourceFilter() {
        AiSettings settings = mock(AiSettings.class);
        when(settings.enabled()).thenReturn(true); when(settings.provider()).thenReturn("gemini"); when(settings.keyMissing()).thenReturn(false);
        when(settings.maxContextChars()).thenReturn(1000); when(settings.model(any())).thenReturn("gemini-test");
        when(settings.cost(any(), anyLong(), anyLong())).thenReturn(BigDecimal.ZERO);
        GeminiProvider gemini = mock(GeminiProvider.class); ClaudeProvider claude = mock(ClaudeProvider.class); MockProvider mock = mock(MockProvider.class);
        AiInvocationWriter writer = mock(AiInvocationWriter.class);
        when(writer.write(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), any(), any(), any(), anyLong(), any(), any(), any(), any(), any())).thenReturn(UUID.randomUUID());
        String context = "{\"source\":\"PROFILE\",\"status\":\"safe\"}";
        String valid = "{\"sections\":[{\"heading\":\"H\",\"points\":[{\"text\":\"ok\",\"sources\":[\"PROFILE\"]},{\"text\":\"invented\",\"sources\":[\"TASK:unknown\"]}]}]}";
        when(gemini.generate(any())).thenReturn(new AiProvider.Reply(valid, 10, 5, null));
        var gateway = gateway(settings, gemini, claude, mock, writer, context);
        var ok = gateway.invoke(ACTOR, AiUseCase.C360_SUMMARY, "CUSTOMER", UUID.randomUUID(), Map.of("jsonSchema", SCHEMA));
        assertThat(ok.status()).isEqualTo("SUCCEEDED");
        verify(gemini).generate(any()); verifyNoInteractions(claude, mock);
        when(gemini.generate(any())).thenReturn(new AiProvider.Reply("{\"sections\":[{\"heading\":\"H\",\"points\":[{\"text\":\"x\",\"sources\":[\"TASK:none\"]}]}]}", 10, 5, null));
        var invalid = gateway.invoke(ACTOR, AiUseCase.C360_SUMMARY, "CUSTOMER", UUID.randomUUID(), Map.of("jsonSchema", SCHEMA));
        assertThat(invalid.status()).isEqualTo("FAILED"); assertThat(invalid.errorCode()).isEqualTo("INVALID_OUTPUT");
    }

    @Test void settingsUseGeminiKeyModelAndPriceOnlyForGemini() {
        var gemini = new AiSettings(new MockEnvironment().withProperty("pose.ai.provider", "gemini")
                .withProperty("pose.ai.gemini.models.c360-summary", "gemini-custom")
                .withProperty("pose.ai.gemini.pricing.input", "0.5").withProperty("pose.ai.gemini.pricing.output", "2"), () -> "claude-key", () -> "gemini-key");
        assertThat(gemini.geminiApiKey()).isEqualTo("gemini-key"); assertThat(gemini.apiKey()).isEqualTo("claude-key");
        assertThat(gemini.model(AiUseCase.C360_SUMMARY)).isEqualTo("gemini-custom");
        assertThat(gemini.model(AiUseCase.SIGNAL_EXTRACT)).isEqualTo("gemini-3.6-flash");
        assertThat(gemini.cost(AiUseCase.C360_SUMMARY, 1_000_000, 1_000_000)).isEqualByComparingTo("2.5");
        assertThat(gemini.keyMissing()).isFalse();
        var free = new AiSettings(new MockEnvironment().withProperty("pose.ai.provider", "gemini"), () -> "claude-key", () -> "");
        assertThat(free.keyMissing()).isTrue(); assertThat(free.cost(AiUseCase.C360_SUMMARY, 1000, 1000)).isEqualByComparingTo("0");
        var claude = new AiSettings(new MockEnvironment().withProperty("pose.ai.provider", "claude"), () -> "", () -> "gemini-key");
        assertThat(claude.keyMissing()).isTrue();
        assertThat(claude.model(AiUseCase.C360_SUMMARY)).isEqualTo("claude-sonnet-5-5");
        assertThat(claude.model(AiUseCase.SIGNAL_EXTRACT)).isEqualTo("claude-haiku-5-5");
        assertThat(new AiSettings(new MockEnvironment(), () -> "", () -> "").keyMissing()).isFalse();
        var padded = new AiSettings(new MockEnvironment().withProperty("pose.ai.provider", "gemini"), () -> "", () -> "  gemini-key\r\n");
        assertThat(padded.geminiApiKey()).isEqualTo("gemini-key"); assertThat(padded.keyMissing()).isFalse();
    }
}
