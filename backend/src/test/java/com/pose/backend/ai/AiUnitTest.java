package com.pose.backend.ai;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import com.pose.backend.identity.*;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

class AiUnitTest {
    @Test void masksVietnameseContactsAndIdentityWithoutMaskingLongNumbers() {
        var result=new Redactor().redact("a@example.test; 0912 345 678; +84 912.345.678; 0912345678; 123456789; 123456789012; 123456789012345");
        assertThat(result.text()).contains("[EMAIL_1]","[PHONE_1]","[PHONE_2]","[PHONE_3]","[IDENTITY_1]","[IDENTITY_2]","123456789012345")
                .doesNotContain("a@example.test","0912 345 678","+84 912.345.678","0912345678");
        assertThat(result.count()).isEqualTo(6);
    }
    @Test void promptVersionsAndCostsAreDeterministic() {
        PromptRegistry prompts=new PromptRegistry();
        for(AiUseCase type:AiUseCase.values()) assertThat(prompts.prompt(type).toLowerCase(java.util.Locale.ROOT)).contains("tiếng việt","không suy đoán");
        assertThat(prompts.version(AiUseCase.C360_SUMMARY)).isEqualTo("c360-summary.v2");
        assertThat(prompts.version(AiUseCase.HANDOVER_BRIEF)).isEqualTo("handover-brief.v3");
        assertThat(prompts.version(AiUseCase.SIGNAL_EXTRACT)).isEqualTo("signal-extract.v3");
        assertThat(prompts.prompt(AiUseCase.HANDOVER_BRIEF)).contains("dd/MM/yyyy");
        for(AiUseCase type:new AiUseCase[]{AiUseCase.C360_SUMMARY,AiUseCase.HANDOVER_BRIEF}) assertThat(prompts.prompt(type)).contains("Không nhắc lại giá trị email");
        AiSettings settings=new AiSettings(new MockEnvironment());
        assertThat(settings.cost(AiUseCase.SIGNAL_EXTRACT,1000000,1000000)).isEqualByComparingTo(new BigDecimal("0.60"));
        assertThat(settings.cost(AiUseCase.C360_SUMMARY,1000000,1000000)).isEqualByComparingTo(new BigDecimal("12.00"));
    }
    @Test void signalBatchLimitIsValidated() {
        assertThat(new AiSettings(new MockEnvironment()).signalMaxMessages()).isEqualTo(2);
        assertThat(new AiSettings(new MockEnvironment().withProperty("pose.ai.signal.max-messages-per-request","1")).signalMaxMessages()).isEqualTo(1);
        assertThat(new AiSettings(new MockEnvironment().withProperty("pose.ai.signal.max-messages-per-request","5")).signalMaxMessages()).isEqualTo(5);
        org.assertj.core.api.Assertions.assertThatThrownBy(()->new AiSettings(new MockEnvironment().withProperty("pose.ai.signal.max-messages-per-request","0")).signalMaxMessages()).isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(()->new AiSettings(new MockEnvironment().withProperty("pose.ai.signal.max-messages-per-request","6")).signalMaxMessages()).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void budgetBlocksAtLimitAndNeverReturnsNegativeRemaining() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(),eq(BigDecimal.class),any(),any(),any())).thenReturn(new BigDecimal("5.10"));
        BudgetGuard budget=new BudgetGuard(jdbc,new AiSettings(new MockEnvironment()),Clock.fixed(Instant.parse("2026-11-08T00:00:00Z"),ZoneOffset.UTC));
        assertThat(budget.blocked(UUID.randomUUID())).isTrue();assertThat(budget.remaining(UUID.randomUUID())).isEqualByComparingTo(BigDecimal.ZERO);
    }
    @Test void providerFailureReturnsFailedWithoutThrowingIntoCrm() {
        AiSettings settings=mock(AiSettings.class);when(settings.enabled()).thenReturn(true);when(settings.provider()).thenReturn("mock");
        when(settings.maxContextChars()).thenReturn(1000);when(settings.model(any())).thenReturn("test-model");
        AiContextBuilder contexts=mock(AiContextBuilder.class);when(contexts.build(any(),anyString(),any(),any())).thenReturn("{\"status\":\"safe\"}");
        BudgetGuard budget=mock(BudgetGuard.class);MockProvider mock=mock(MockProvider.class);
        when(mock.generate(any())).thenThrow(new IllegalStateException("provider failure"));
        PromptRegistry prompts=mock(PromptRegistry.class);when(prompts.prompt(any())).thenReturn("test");when(prompts.version(any())).thenReturn("test.v1");
        AiInvocationWriter writer=mock(AiInvocationWriter.class);
        var gateway=new AiGateway(settings,contexts,new Redactor(),budget,mock,mock(ClaudeProvider.class),prompts,writer,Clock.fixed(Instant.EPOCH,ZoneOffset.UTC),mock(JdbcTemplate.class));
        var actor=new ActorContext(UUID.randomUUID(),UUID.randomUUID(),Set.of(RoleCode.SALES_REP),Set.of(PermissionCode.AI_ASSIST_USE),Set.of());
        var result=gateway.invoke(actor,AiUseCase.C360_SUMMARY,"CUSTOMER",UUID.randomUUID(),Map.of());
        assertThat(result.status()).isEqualTo("FAILED");assertThat(result.errorCode()).isEqualTo("PROVIDER_ERROR");
        verify(writer).write(any(),eq(actor),eq(AiUseCase.C360_SUMMARY),eq("CUSTOMER"),any(),eq("mock"),any(),any(),any(),anyInt(),any(),any(),isNull(),anyLong(),eq("FAILED"),eq("PROVIDER_ERROR"),isNull(),any());
    }
    @Test void missingClaudeKeyDisablesInvocationWithoutContactingProvider() {
        AiSettings settings=mock(AiSettings.class);when(settings.enabled()).thenReturn(true);when(settings.provider()).thenReturn("claude");when(settings.apiKey()).thenReturn("");when(settings.keyMissing()).thenReturn(true);
        when(settings.model(any())).thenReturn("test-model");PromptRegistry prompts=mock(PromptRegistry.class);when(prompts.version(any())).thenReturn("test.v1");
        ClaudeProvider claude=mock(ClaudeProvider.class);AiInvocationWriter writer=mock(AiInvocationWriter.class);
        var gateway=new AiGateway(settings,mock(AiContextBuilder.class),new Redactor(),mock(BudgetGuard.class),mock(MockProvider.class),claude,prompts,writer,Clock.fixed(Instant.EPOCH,ZoneOffset.UTC),mock(JdbcTemplate.class));
        var actor=new ActorContext(UUID.randomUUID(),UUID.randomUUID(),Set.of(RoleCode.SALES_REP),Set.of(PermissionCode.AI_ASSIST_USE),Set.of());
        assertThat(gateway.invoke(actor,AiUseCase.C360_SUMMARY,"CUSTOMER",UUID.randomUUID(),Map.of()).status()).isEqualTo("DISABLED");
        assertThat(gateway.invoke(actor,AiUseCase.C360_SUMMARY,"CUSTOMER",UUID.randomUUID(),Map.of()).status()).isEqualTo("DISABLED");
        verifyNoInteractions(claude);verify(writer,times(2)).write(any(),eq(actor),any(),any(),any(),eq("claude"),any(),any(),isNull(),eq(0),isNull(),isNull(),isNull(),anyLong(),eq("DISABLED"),isNull(),isNull(),any());
    }
    @Test void elementWiseTruncationPreservesValidJsonAndPrioritizesNewestItems() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("source", "PROFILE");
        ctx.put("name", "Khách hàng Doanh nghiệp");
        ctx.put("status", "ACTIVE");
        List<Map<String, Object>> interactions = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("source", "INTERACTION:00000000-0000-0000-0000-00000000000" + (i % 10));
            row.put("subject", "Trao đổi lần " + i);
            row.put("content", "Nội dung chi tiết của cuộc trao đổi số " + i + " với khách hàng.");
            interactions.add(row);
        }
        ctx.put("interactions", interactions);
        String fullJson = mapper.writeValueAsString(ctx);
        assertThat(fullJson.length()).isGreaterThan(450);

        String truncated = AiContextBuilder.truncateJsonElementWise(mapper, fullJson, 380);
        assertThat(truncated.length()).isLessThanOrEqualTo(380);
        com.fasterxml.jackson.databind.JsonNode parsed = mapper.readTree(truncated);
        assertThat(parsed.isObject()).isTrue();
        assertThat(parsed.path("source").asText()).isEqualTo("PROFILE");
        assertThat(parsed.path("interactions").isArray()).isTrue();
        assertThat(parsed.path("interactions").size()).isBetween(1, 9);
        assertThat(parsed.path("interactions").get(0).path("subject").asText()).isEqualTo("Trao đổi lần 1");
    }
    @Test void contextRedactionPreservesSourceAndIdsAndCountsAcrossFields() throws Exception {
        String id="75c03456-7041-5fd3-ac82-4a4e891b451b";
        String context="{\"source\":\"INTERACTION:"+id+"\",\"interactionId\":\""+id+"\",\"notes\":\"Gọi 0912 345 678\",\"items\":[{\"content\":\"Số +84 912.345.678\"}]}";
        var masked=new Redactor().redactContext(context);var json=new com.fasterxml.jackson.databind.ObjectMapper().readTree(masked.text());
        assertThat(json.path("source").asText()).isEqualTo("INTERACTION:"+id);
        assertThat(json.path("interactionId").asText()).isEqualTo(id);
        assertThat(json.path("notes").asText()).contains("[PHONE_1]");
        assertThat(json.path("items").get(0).path("content").asText()).contains("[PHONE_2]");
        assertThat(masked.count()).isEqualTo(2);
    }
}
