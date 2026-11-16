package com.pose.backend.auth;

import com.pose.workflow.jdbc.JdbcConversationMessageProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {"pose.workflow.worker.enabled=true", "pose.workflow.worker.interval-ms=86400000"})
class ConversationWorkflowIntegrationTest extends FoundationTestSupport {
    private static final UUID ACCOUNT = id(2901);
    private static final UUID OWNED_CONVERSATION = id(2902);
    private static final UUID UNASSIGNED_CONVERSATION = id(2903);
    private static final UUID OWNED_EVENT = id(2904);
    private static final UUID UNASSIGNED_EVENT = id(2905);

    @Autowired JdbcConversationMessageProcessor processor;

    @BeforeEach
    void conversationWorkflowFixtures() {
        permission("conversation.reply");
        grant(ORG_ONE, "CUSTOMER_CARE", "conversation.reply");
        jdbc.update("""
                INSERT INTO channel_accounts(id,organization_id,channel_type,display_name,external_account_id,
                    team_id,default_assignee_user_id,status,secret_ref,created_at,updated_at)
                VALUES (?,?,'ZALO_SIM','Workflow test','workflow-test',?,?, 'ACTIVE','POSE_TEST_SECRET',?,?)
                """, ACCOUNT, ORG_ONE, TEAM_MANAGED, USER_ONE, time(), time());
        jdbc.update("""
                INSERT INTO conversations(id,organization_id,channel_account_id,external_thread_id,assigned_user_id,
                    status,consent_status,created_at,updated_at)
                VALUES (?,?,?,'owned-thread',?,'OPEN','UNKNOWN',?,?),(?,?,?,'unassigned-thread',NULL,'OPEN','UNKNOWN',?,?)
                """, OWNED_CONVERSATION, ORG_ONE, ACCOUNT, USER_ONE, time(), time(),
                UNASSIGNED_CONVERSATION, ORG_ONE, ACCOUNT, time(), time());
        outbox(OWNED_EVENT, OWNED_CONVERSATION, "owned-message");
        outbox(UNASSIGNED_EVENT, UNASSIGNED_CONVERSATION, "unassigned-message");
    }

    @Test
    void createsOneSystemAuthoredTaskAndSkipsUnassignedConversations() {
        assertThat(processor.processPendingBatch(10)).isEqualTo(2);

        var task = jdbc.queryForMap("SELECT id,owner_user_id,created_by_user_id,created_by_actor_type,title,origin FROM crm_tasks WHERE conversation_id=?",
                OWNED_CONVERSATION);
        assertThat(task.get("owner_user_id")).isEqualTo(USER_ONE);
        assertThat(task.get("created_by_user_id")).isNull();
        assertThat(task.get("created_by_actor_type")).isEqualTo("SYSTEM");
        assertThat(task.get("title")).isEqualTo("Phản hồi đầu tiên");
        assertThat(task.get("origin")).isEqualTo("WORKFLOW");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM crm_tasks WHERE conversation_id=?", Integer.class,
                UNASSIGNED_CONVERSATION)).isZero();
        assertThat(jdbc.queryForObject("SELECT delivery_status FROM outbox_events WHERE id=?", String.class,
                OWNED_EVENT)).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT delivery_status FROM outbox_events WHERE id=?", String.class,
                UNASSIGNED_EVENT)).isEqualTo("PUBLISHED");

        jdbc.update("UPDATE outbox_events SET delivery_status='PENDING',published_at=NULL WHERE id=?", OWNED_EVENT);
        assertThat(processor.processPendingBatch(10)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM crm_tasks WHERE conversation_id=? AND origin='WORKFLOW'",
                Integer.class, OWNED_CONVERSATION)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_id=? AND action='TASK_CREATED' AND actor_type='SYSTEM'",
                Integer.class, task.get("id"))).isEqualTo(1);
    }

    private void outbox(UUID eventId, UUID conversationId, String messageId) {
        String payload = "{\"conversationId\":\"" + conversationId + "\",\"messageId\":\"" + messageId + "\"}";
        jdbc.update("""
                INSERT INTO outbox_events(id,organization_id,aggregate_type,aggregate_id,event_type,payload,
                    idempotency_key,delivery_status,retry_count,occurred_at)
                VALUES (?,?,'CONVERSATION',?,'MESSAGE_RECEIVED',CAST(? AS JSONB),?,'PENDING',0,?)
                """, eventId, ORG_ONE, conversationId, payload, "test:" + eventId, time());
    }
}
