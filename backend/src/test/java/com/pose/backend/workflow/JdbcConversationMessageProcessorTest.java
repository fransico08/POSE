package com.pose.backend.workflow;

import com.pose.workflow.jdbc.JdbcConversationMessageProcessor;
import com.pose.workflow.jdbc.WorkflowTaskAudit;
import com.pose.workflow.jdbc.WorkflowTransactionRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcConversationMessageProcessorTest {
    private static final UUID ORG = id(1), USER = id(2), TEAM = id(3), ACCOUNT = id(4);
    private static final UUID CONVERSATION = id(5), UNASSIGNED = id(6), EVENT = id(7), EMPTY_EVENT = id(8);
    private static final Instant NOW = Instant.parse("2026-11-12T12:00:00Z");
    private DataSource dataSource;
    private org.springframework.jdbc.core.JdbcTemplate jdbc;
    private JdbcConversationMessageProcessor processor;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new DriverManagerDataSource("jdbc:h2:mem:conversation-workflow" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        schema();
        seed();
        WorkflowTransactionRunner transactions = new WorkflowTransactionRunner() {
            @Override
            public <T> T execute(Work<T> work) {
                try (Connection connection = dataSource.getConnection()) {
                    connection.setAutoCommit(false);
                    try {
                        T result = work.run(connection);
                        connection.commit();
                        return result;
                    } catch (SQLException | RuntimeException exception) {
                        connection.rollback();
                        throw new IllegalStateException(exception);
                    }
                } catch (SQLException exception) {
                    throw new IllegalStateException(exception);
                }
            }
        };
        WorkflowTaskAudit noAudit = (connection, organizationId, outboxId, executionId, taskId) -> {};
        processor = new JdbcConversationMessageProcessor(transactions, noAudit,
                Clock.fixed(NOW, ZoneOffset.UTC), 3, 1);
    }

    @Test
    void createsOneSystemTaskForOwnerAndPublishesUnassignedEventWithoutTask() {
        assertThat(processor.processPendingBatch(10)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT delivery_status FROM outbox_events WHERE id=?", String.class, EVENT))
                .isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT delivery_status FROM outbox_events WHERE id=?", String.class, EMPTY_EVENT))
                .isEqualTo("PUBLISHED");
        var task = jdbc.queryForMap("SELECT owner_user_id,created_by_user_id,created_by_actor_type,title,due_at FROM crm_tasks WHERE conversation_id=?",
                CONVERSATION);
        assertThat(task.get("owner_user_id")).isEqualTo(USER);
        assertThat(task.get("created_by_user_id")).isNull();
        assertThat(task.get("created_by_actor_type")).isEqualTo("SYSTEM");
        assertThat(task.get("title")).isEqualTo("Phản hồi đầu tiên");
        Object dueAt = task.get("due_at");
        Instant dueInstant = dueAt instanceof OffsetDateTime offset ? offset.toInstant() : ((Timestamp) dueAt).toInstant();
        assertThat(dueInstant).isEqualTo(NOW.plusSeconds(86400));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM crm_tasks WHERE conversation_id=?", Integer.class, UNASSIGNED)).isZero();

        jdbc.update("UPDATE outbox_events SET delivery_status='PENDING',published_at=NULL WHERE id=?", EVENT);
        assertThat(processor.processPendingBatch(10)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM crm_tasks WHERE conversation_id=?", Integer.class, CONVERSATION)).isEqualTo(1);
    }

    private void schema() {
        jdbc.execute("CREATE TABLE outbox_events(id UUID PRIMARY KEY,organization_id UUID,aggregate_type VARCHAR(40),aggregate_id UUID,event_type VARCHAR(60),payload JSONB,idempotency_key VARCHAR(180),delivery_status VARCHAR(30),retry_count INT,occurred_at TIMESTAMP WITH TIME ZONE,published_at TIMESTAMP WITH TIME ZONE,last_error VARCHAR(4000),UNIQUE(organization_id,idempotency_key))");
        jdbc.execute("CREATE TABLE workflow_executions(id UUID PRIMARY KEY,organization_id UUID,outbox_event_id UUID,workflow_type VARCHAR(120),idempotency_key VARCHAR(180),status VARCHAR(20),retry_count INT,last_error VARCHAR(4000),started_at TIMESTAMP WITH TIME ZONE,finished_at TIMESTAMP WITH TIME ZONE,created_task_id UUID,UNIQUE(organization_id,idempotency_key))");
        jdbc.execute("CREATE TABLE users(id UUID PRIMARY KEY,organization_id UUID,status VARCHAR(20),archived_at TIMESTAMP WITH TIME ZONE)");
        jdbc.execute("CREATE TABLE teams(id UUID PRIMARY KEY,organization_id UUID,status VARCHAR(20))");
        jdbc.execute("CREATE TABLE team_memberships(organization_id UUID,team_id UUID,user_id UUID,status VARCHAR(20),joined_at TIMESTAMP WITH TIME ZONE,left_at TIMESTAMP WITH TIME ZONE)");
        jdbc.execute("CREATE TABLE roles(id UUID PRIMARY KEY,organization_id UUID,code VARCHAR(40),status VARCHAR(20))");
        jdbc.execute("CREATE TABLE permissions(id UUID PRIMARY KEY,code VARCHAR(60))");
        jdbc.execute("CREATE TABLE user_role_grants(organization_id UUID,user_id UUID,role_id UUID,granted_at TIMESTAMP WITH TIME ZONE,revoked_at TIMESTAMP WITH TIME ZONE)");
        jdbc.execute("CREATE TABLE role_permissions(role_id UUID,permission_id UUID)");
        jdbc.execute("CREATE TABLE channel_accounts(id UUID PRIMARY KEY,organization_id UUID,team_id UUID)");
        jdbc.execute("CREATE TABLE conversations(id UUID PRIMARY KEY,organization_id UUID,channel_account_id UUID,assigned_user_id UUID)");
        jdbc.execute("CREATE TABLE crm_tasks(id UUID PRIMARY KEY,organization_id UUID,customer_id UUID,lead_id UUID,opportunity_id UUID,conversation_id UUID,owner_user_id UUID,created_by_user_id UUID,created_by_actor_type VARCHAR(20),title VARCHAR(220),description TEXT,priority VARCHAR(20),status VARCHAR(20),due_at TIMESTAMP WITH TIME ZONE,origin VARCHAR(30),idempotency_key VARCHAR(180),created_at TIMESTAMP WITH TIME ZONE,updated_at TIMESTAMP WITH TIME ZONE,UNIQUE(organization_id,conversation_id))");
    }

    private void seed() {
        Timestamp now = Timestamp.from(NOW);
        jdbc.update("INSERT INTO users VALUES (?,?, 'ACTIVE',NULL)", USER, ORG);
        jdbc.update("INSERT INTO teams VALUES (?,?,'ACTIVE')", TEAM, ORG);
        jdbc.update("INSERT INTO team_memberships VALUES (?,?,?,'ACTIVE',?,NULL)", ORG, TEAM, USER, now);
        UUID role = id(9), permission = id(10);
        jdbc.update("INSERT INTO roles VALUES (?,?, 'CUSTOMER_CARE','ACTIVE')", role, ORG);
        jdbc.update("INSERT INTO permissions VALUES (?,'conversation.reply')", permission);
        jdbc.update("INSERT INTO role_permissions VALUES (?,?)", role, permission);
        jdbc.update("INSERT INTO user_role_grants VALUES (?,?,?, ?,NULL)", ORG, USER, role, now);
        jdbc.update("INSERT INTO channel_accounts VALUES (?,?,?)", ACCOUNT, ORG, TEAM);
        jdbc.update("INSERT INTO conversations VALUES (?,?,?,?)", CONVERSATION, ORG, ACCOUNT, USER);
        jdbc.update("INSERT INTO conversations VALUES (?,?,?,NULL)", UNASSIGNED, ORG, ACCOUNT);
        event(EVENT, CONVERSATION, now);
        event(EMPTY_EVENT, UNASSIGNED, Timestamp.from(NOW.plusSeconds(1)));
    }

    private void event(UUID event, UUID conversation, Timestamp occurredAt) {
        String payload = "{\"conversationId\":\"" + conversation + "\",\"messageId\":\"" + event + "\"}";
        jdbc.update("INSERT INTO outbox_events VALUES (?,?, 'CONVERSATION',?,'MESSAGE_RECEIVED',CAST(? AS JSONB),?,'PENDING',0,?,NULL,NULL)",
                event, ORG, conversation, payload, "test:" + event, occurredAt);
    }

    private static UUID id(long value) { return new UUID(0, value); }
}
