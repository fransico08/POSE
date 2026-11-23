package com.pose.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.ai.AiProvider;
import com.pose.backend.ai.MockProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "pose.ai.enabled=true",
        "pose.ai.provider=mock",
        "pose.sales-assignment.expiry-scan-delay-ms=86400000"
})
class W15ConcurrencyVerificationTest extends FoundationTestSupport {
    private static final UUID CUSTOMER = id(2901);
    private static final UUID ACCOUNT = id(2902);
    private static final UUID CONVERSATION = id(2903);
    private static final UUID MESSAGE = id(2904);

    @MockitoSpyBean
    MockProvider mock;

    @BeforeEach
    void fixtures() {
        mock.setNextReply(null);
        clearInvocations(mock);
        for (String permission : List.of("ai.assist.use", "ai.signal.review", "customer_360.read", "conversation.read")) {
            permission(permission);
            grant(ORG_ONE, "MANAGER", permission);
        }
        jdbc.update("INSERT INTO customers(id, organization_id, owner_user_id, display_name, email, normalized_email, status, created_at, updated_at) VALUES (?, ?, ?, 'Concurrency Customer', 'w15-concurrency@example.test', 'w15-concurrency@example.test', 'ACTIVE', ?, ?)",
                CUSTOMER, ORG_ONE, USER_ONE, time(), time());
        jdbc.update("INSERT INTO channel_accounts(id, organization_id, channel_type, display_name, external_account_id, status, secret_ref, created_at, updated_at) VALUES (?, ?, 'TELEGRAM', 'Concurrency Test', 'w15-concurrency', 'ACTIVE', 'TEST_SECRET', ?, ?)",
                ACCOUNT, ORG_ONE, time(), time());
        jdbc.update("INSERT INTO conversations(id, organization_id, channel_account_id, external_thread_id, customer_id, status, created_at, updated_at) VALUES (?, ?, ?, 'w15-concurrency-thread', ?, 'OPEN', ?, ?)",
                CONVERSATION, ORG_ONE, ACCOUNT, CUSTOMER, time(), time());
        jdbc.update("INSERT INTO conversation_messages(id, organization_id, conversation_id, direction, external_message_id, content_type, content, delivery_status, occurred_at, created_at) VALUES (?, ?, ?, 'INBOUND', 'w15-concurrency-message', 'text', 'Xin hỏi giá', 'RECEIVED', ?, ?)",
                MESSAGE, ORG_ONE, CONVERSATION, time(), time());
    }

    @Test
    void concurrentExtractionPersistsOnceButInvokesProviderTwice() throws Exception {
        JsonNode token = loginOne();
        CountDownLatch providerEntered = new CountDownLatch(2);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        AtomicInteger providerCalls = new AtomicInteger();
        doAnswer(invocation -> {
            providerCalls.incrementAndGet();
            providerEntered.countDown();
            assertThat(releaseProvider.await(5, TimeUnit.SECONDS)).isTrue();
            return invocation.callRealMethod();
        }).when(mock).generate(any(AiProvider.Request.class));

        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = workers.submit(() -> extractStatus(token));
            Future<Integer> second = workers.submit(() -> extractStatus(token));

            assertThat(providerEntered.await(5, TimeUnit.SECONDS)).isTrue();
            releaseProvider.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(200);
        }

        assertThat(providerCalls.get()).isEqualTo(2);
        assertThat(count("customer_signals")).isEqualTo(1);
        assertThat(count("customer_signal_extractions")).isEqualTo(1);
        assertThat(countSignalInvocations()).isEqualTo(2);
    }

    @Test
    void successfulExtractionRetryDoesNotInvokeProviderAgain() throws Exception {
        JsonNode token = loginOne();

        assertThat(extractStatus(token)).isEqualTo(200);
        assertThat(extractStatus(token)).isEqualTo(200);

        assertThat(count("customer_signals")).isEqualTo(1);
        assertThat(count("customer_signal_extractions")).isEqualTo(1);
        assertThat(countSignalInvocations()).isEqualTo(1);
    }

    @Test
    void concurrentReviewCreatesOneTerminalStateAndOneAuditEvent() throws Exception {
        JsonNode token = loginOne();
        assertThat(extractStatus(token)).isEqualTo(200);
        UUID signalId = jdbc.queryForObject("SELECT id FROM customer_signals", UUID.class);
        assertThat(signalId).isNotNull();

        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<Integer> accept = workers.submit(() -> mvc.perform(post("/api/v1/ai/signals/{id}/accept", signalId)
                    .header("Authorization", bearer(token)))
                    .andReturn()
                    .getResponse()
                    .getStatus());
            Future<Integer> edit = workers.submit(() -> mvc.perform(post("/api/v1/ai/signals/{id}/edit", signalId)
                    .header("Authorization", bearer(token))
                    .contentType("application/json")
                    .content("{\"value\":\"ASK_PRICE\"}"))
                    .andReturn()
                    .getResponse()
                    .getStatus());

            List<Integer> statuses = List.of(accept.get(10, TimeUnit.SECONDS), edit.get(10, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        }

        String signalStatus = jdbc.queryForObject("SELECT status FROM customer_signals WHERE id=?", String.class, signalId);
        assertThat(signalStatus).isIn("ACCEPTED", "EDITED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action IN ('AI_SIGNAL_ACCEPTED', 'AI_SIGNAL_EDITED')", Integer.class)).isEqualTo(1);
    }

    private int extractStatus(JsonNode token) throws Exception {
        return mvc.perform(post("/api/v1/ai/signals/extract")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"subjectType\":\"CUSTOMER\",\"subjectId\":\"" + CUSTOMER + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private int countSignalInvocations() {
        return jdbc.queryForObject("SELECT count(*) FROM ai_invocations WHERE use_case='SIGNAL_EXTRACT'", Integer.class);
    }
}
