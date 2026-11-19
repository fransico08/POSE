package com.pose.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.channel.ChannelWebhookAck;
import com.pose.backend.channel.ChannelWebhookService;
import com.pose.backend.conversation.ConversationReplyResponse;
import com.pose.backend.conversation.ConversationService;
import com.pose.backend.identity.ActorContext;
import com.pose.backend.identity.FoundationAccess;
import com.pose.backend.lead.LeadConversionResponse;
import com.pose.backend.lead.LeadConversionService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "pose.sales-assignment.expiry-scan-delay-ms=86400000")
class W17ConcurrencyIntegrationTest extends FoundationTestSupport {
    private static final UUID CAT = id(4101);
    private static final UUID OFFER = id(4102);
    private static final UUID ACCOUNT = id(4103);
    private static final UUID CONVERSATION = id(4104);
    private static final UUID LEAD = id(4105);
    private static final UUID SALES_USER = id(4106);
    private static final String EXTERNAL_ACCOUNT = "w17-concurrency-account";
    private static final String EXTERNAL_THREAD = "w17-concurrency-thread";
    private static final String WEBHOOK_SECRET = "pose-backend";

    @Autowired FoundationAccess foundationAccess;
    @Autowired AuthAccountRepository accounts;
    @Autowired ConversationService conversationService;
    @Autowired ChannelWebhookService webhookService;
    @Autowired LeadConversionService leadConversionService;
    @Autowired PlatformTransactionManager txManager;

    private boolean isPostgres() {
        return Boolean.TRUE.equals(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>)
                connection -> "PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())));
    }

    @BeforeEach
    void fixtures() {
        for (String permission : List.of("conversation.read", "conversation.reply", "lead.read", "lead.write",
                "customer.read", "customer.write", "offering.read")) {
            if (jdbc.queryForObject("SELECT count(*) FROM permissions WHERE code=?", Integer.class, permission) == 0) {
                permission(permission);
            }
            if (jdbc.queryForObject("""
                    SELECT count(*) FROM role_permissions rp
                    JOIN roles r ON r.id=rp.role_id JOIN permissions p ON p.id=rp.permission_id
                    WHERE r.organization_id=? AND r.code='MANAGER' AND p.code=?
                    """, Integer.class, ORG_ONE, permission) == 0) {
                grant(ORG_ONE, "MANAGER", permission);
            }
        }
        if (jdbc.queryForObject("""
                SELECT count(*) FROM role_permissions rp
                JOIN roles r ON r.id=rp.role_id JOIN permissions p ON p.id=rp.permission_id
                WHERE r.organization_id=? AND r.code='SALES_REP' AND p.code='offering.read'
                """, Integer.class, ORG_ONE) == 0) {
            grant(ORG_ONE, "SALES_REP", "offering.read");
        }
        user(SALES_USER, ORG_ONE, "W17 Sales", "w17-sales@example.test", hashOne);
        userRole(ORG_ONE, SALES_USER, "SALES_REP");
        jdbc.update("INSERT INTO team_memberships(id,organization_id,team_id,user_id,joined_at,status) VALUES (?,?,?,?,?,'ACTIVE')",
                id(4107), ORG_ONE, TEAM_MANAGED, SALES_USER, time());
        jdbc.update("INSERT INTO offering_categories(id,organization_id,code,name,status,created_at,updated_at) VALUES (?,?,'W17','W17','ACTIVE',?,?)",
                CAT, ORG_ONE, time(), time());
        jdbc.update("INSERT INTO offerings(id,organization_id,category_id,team_id,code,name,kind,status,created_at,updated_at) VALUES (?,?,?,?,'W17-OFF','W17 Offer','SERVICE','ACTIVE',?,?)",
                OFFER, ORG_ONE, CAT, TEAM_MANAGED, time(), time());
        jdbc.update("INSERT INTO sales_assignments(id,organization_id,offering_id,sales_user_id,assigned_by_user_id,status,effective_from,created_at,updated_at) VALUES (?,?,?,?,?,'ACTIVE',?,?,?)",
                UUID.randomUUID(), ORG_ONE, OFFER, SALES_USER, USER_ONE, time(), time(), time());
        jdbc.update("INSERT INTO leads(id,organization_id,owner_user_id,offering_id,display_name,email,normalized_email,phone,normalized_phone,status,notes,qualified_at,created_at,updated_at) VALUES (?,?,?,?,?,'w17@example.test','w17@example.test','0912000111','+84912000111','QUALIFIED','W17 note',?,?,?)",
                LEAD, ORG_ONE, SALES_USER, OFFER, "W17 Lead", time(), time(), time());
        jdbc.update("""
                INSERT INTO channel_accounts(id,organization_id,channel_type,display_name,external_account_id,
                    team_id,default_assignee_user_id,status,secret_ref,created_at,updated_at)
                VALUES (?,?,'ZALO_SIM','W17 channel',?,?,?,'ACTIVE','spring.application.name',?,?)
                """, ACCOUNT, ORG_ONE, EXTERNAL_ACCOUNT, TEAM_MANAGED, USER_ONE, time(), time());
        jdbc.update("""
                INSERT INTO conversations(id,organization_id,channel_account_id,external_thread_id,assigned_user_id,
                    lead_id,status,consent_status,last_message_at,created_at,updated_at)
                VALUES (?,?,?,?,?,?,'OPEN','UNKNOWN',?,?,?)
                """, CONVERSATION, ORG_ONE, ACCOUNT, EXTERNAL_THREAD, USER_ONE, LEAD, time(), time(), time());
    }

    private ActorContext managerActor() {
        return accounts.findById(ORG_ONE, USER_ONE).orElseThrow().actor();
    }

    private JsonNode inboundPayload(String externalMessageId, String text) {
        return mapper.valueToTree(Map.of(
                "event_name", "user_send_text",
                "timestamp", Instant.parse("2026-10-01T00:05:00Z").toString(),
                "app_id", EXTERNAL_ACCOUNT,
                "sender", Map.of("id", EXTERNAL_THREAD),
                "recipient", Map.of("id", EXTERNAL_ACCOUNT),
                "message", Map.of("msg_id", externalMessageId, "text", text)
        ));
    }

    private TransactionTemplate newTx() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx;
    }

    private static String extractSqlState(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }

    @Test
    void replyAndInboundWebhookOnSameConversationDoNotDeadlock() throws Exception {
        Assumptions.assumeTrue(isPostgres(), "PostgreSQL-only two-transaction lock test");
        ActorContext manager = managerActor();
        CountDownLatch tx1LockedOrg = new CountDownLatch(1);
        CountDownLatch tx2LockedConv = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ConversationReplyResponse> replyFuture = pool.submit(() -> newTx().execute(status -> {
                ActorContext refreshed = foundationAccess.lockAndRefresh(manager);
                tx1LockedOrg.countDown();
                await(tx2LockedConv);
                return conversationService.reply(refreshed, CONVERSATION, mapper.valueToTree(Map.of("content", "W17 reply")));
            }));
            Future<ChannelWebhookAck> webhookFuture = pool.submit(() -> newTx().execute(status -> {
                jdbc.queryForObject(
                        "SELECT id FROM conversations WHERE organization_id=? AND channel_account_id=? AND external_thread_id=? FOR UPDATE",
                        UUID.class, ORG_ONE, ACCOUNT, EXTERNAL_THREAD);
                tx2LockedConv.countDown();
                await(tx1LockedOrg);
                return webhookService.receive("zalo-sim", WEBHOOK_SECRET, inboundPayload("w17-msg-reply-pair", "Inbound during reply"));
            }));

            ConversationReplyResponse reply = getOrFailWithSqlState(replyFuture, "reply");
            ChannelWebhookAck ack = getOrFailWithSqlState(webhookFuture, "webhook");

            assertThat(reply.direction()).isEqualTo("OUTBOUND");
            assertThat(ack.ok()).isTrue();
            assertThat(ack.duplicate()).isFalse();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM conversation_messages WHERE conversation_id=?",
                    Integer.class, CONVERSATION)).isEqualTo(2);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void leadConversionAndInboundWebhookOnLeadConversationDoNotDeadlock() throws Exception {
        Assumptions.assumeTrue(isPostgres(), "PostgreSQL-only two-transaction lock test");
        ActorContext manager = managerActor();
        CountDownLatch tx1LockedOrg = new CountDownLatch(1);
        CountDownLatch tx2LockedConv = new CountDownLatch(1);
        JsonNode convertBody = mapper.valueToTree(Map.of(
                "expectedUpdatedAt", START.minusSeconds(1).toString(),
                "customerType", "INDIVIDUAL"
        ));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<LeadConversionResponse> convertFuture = pool.submit(() -> newTx().execute(status -> {
                ActorContext refreshed = foundationAccess.lockAndRefresh(manager);
                jdbc.queryForObject("SELECT id FROM leads WHERE organization_id=? AND id=? FOR NO KEY UPDATE",
                        UUID.class, ORG_ONE, LEAD);
                tx1LockedOrg.countDown();
                await(tx2LockedConv);
                return leadConversionService.convert(refreshed, LEAD, convertBody);
            }));
            Future<ChannelWebhookAck> webhookFuture = pool.submit(() -> newTx().execute(status -> {
                jdbc.queryForObject(
                        "SELECT id FROM conversations WHERE organization_id=? AND channel_account_id=? AND external_thread_id=? FOR UPDATE",
                        UUID.class, ORG_ONE, ACCOUNT, EXTERNAL_THREAD);
                tx2LockedConv.countDown();
                await(tx1LockedOrg);
                return webhookService.receive("zalo-sim", WEBHOOK_SECRET, inboundPayload("w17-msg-convert-pair", "Inbound during convert"));
            }));

            LeadConversionResponse converted = getOrFailWithSqlState(convertFuture, "convert");
            ChannelWebhookAck ack = getOrFailWithSqlState(webhookFuture, "webhook");

            assertThat(converted.lead().status()).isEqualTo("CONVERTED");
            assertThat(ack.ok()).isTrue();
            assertThat(ack.duplicate()).isFalse();
            UUID customerId = converted.customer().id();
            assertThat(jdbc.queryForObject("SELECT customer_id FROM conversations WHERE id=?", UUID.class, CONVERSATION))
                    .isEqualTo(customerId);
            assertThat(jdbc.queryForObject("SELECT lead_id FROM conversations WHERE id=?", UUID.class, CONVERSATION))
                    .isNull();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM conversation_messages WHERE conversation_id=? AND external_message_id='w17-msg-convert-pair'",
                    Integer.class, CONVERSATION)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void twoMutationsInSameOrganizationStillSerializeOnLockAndRefresh() throws Exception {
        Assumptions.assumeTrue(isPostgres(), "PostgreSQL-only two-transaction lock test");
        ActorContext manager = managerActor();
        CountDownLatch tx1Locked = new CountDownLatch(1);
        AtomicLong tx1ReleasedAt = new AtomicLong();
        AtomicLong tx2AcquiredAt = new AtomicLong();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Void> tx1 = pool.submit(() -> newTx().execute(status -> {
                foundationAccess.lockAndRefresh(manager);
                tx1Locked.countDown();
                sleepQuietly(250);
                tx1ReleasedAt.set(System.nanoTime());
                return null;
            }));
            Future<Void> tx2 = pool.submit(() -> {
                await(tx1Locked);
                return newTx().execute(status -> {
                    foundationAccess.lockAndRefresh(manager);
                    tx2AcquiredAt.set(System.nanoTime());
                    return null;
                });
            });
            tx1.get(15, TimeUnit.SECONDS);
            tx2.get(15, TimeUnit.SECONDS);
            assertThat(tx2AcquiredAt.get()).isGreaterThanOrEqualTo(tx1ReleasedAt.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void revokingConversationReadPermissionImmediatelyReturns403OnNextRead() throws Exception {
        JsonNode manager = loginOne();
        response(mvc.perform(MockMvcRequestBuilders.get("/api/v1/conversations")
                .header("Authorization", bearer(manager))), 200);
        response(mvc.perform(MockMvcRequestBuilders.get("/api/v1/conversations/" + CONVERSATION + "/messages")
                .header("Authorization", bearer(manager))), 200);

        jdbc.update("""
                DELETE FROM role_permissions
                WHERE role_id IN (SELECT id FROM roles WHERE organization_id=? AND code='MANAGER')
                  AND permission_id IN (SELECT id FROM permissions WHERE code='conversation.read')
                """, ORG_ONE);

        mvc.perform(MockMvcRequestBuilders.get("/api/v1/conversations")
                        .header("Authorization", bearer(manager)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_PERMISSION"));
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/conversations/" + CONVERSATION + "/messages")
                        .header("Authorization", bearer(manager)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_PERMISSION"));
    }

    @Test
    void readApisDoNotBlockOnInFlightOrganizationMutation() throws Exception {
        Assumptions.assumeTrue(isPostgres(), "PostgreSQL-only two-transaction lock test");
        ActorContext manager = managerActor();
        CountDownLatch mutationLockedOrg = new CountDownLatch(1);
        CountDownLatch readsCompleted = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Void> mutation = pool.submit(() -> newTx().execute(status -> {
                foundationAccess.lockAndRefresh(manager);
                mutationLockedOrg.countDown();
                await(readsCompleted);
                return null;
            }));
            Future<Integer> readCount = pool.submit(() -> {
                await(mutationLockedOrg);
                try {
                    var list = conversationService.list(manager, 0, 20, null, false);
                    var msgs = conversationService.messages(manager, CONVERSATION, 0, 20);
                    return list.items().size() + msgs.items().size();
                } finally {
                    readsCompleted.countDown();
                }
            });
            assertThat(readCount.get(15, TimeUnit.SECONDS)).isGreaterThanOrEqualTo(1);
            mutation.get(15, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    private static <T> T getOrFailWithSqlState(Future<T> future, String label) throws Exception {
        try {
            return future.get(20, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException ex) {
            String sqlState = extractSqlState(ex.getCause());
            throw new AssertionError(label + " failed (SQLSTATE=" + sqlState + "): " + ex.getCause().getClass().getSimpleName(), ex.getCause());
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Barrier timed out");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted waiting on barrier", ex);
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}

