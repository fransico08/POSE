package com.pose.workflow.jdbc;

import com.pose.workflow.LeadQualifiedTrigger;
import com.pose.workflow.TransactionalOutboxPublisher;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;

/** JDBC producer that deliberately never commits: LeadService owns the surrounding transaction. */
public final class JdbcTransactionalOutboxPublisher implements TransactionalOutboxPublisher {
    private static final String EVENT_TYPE = "LEAD_QUALIFIED";
    private static final String KEY_NAMESPACE = "outbox.lead-qualified.v1";
    private final TransactionalConnection connection;

    public JdbcTransactionalOutboxPublisher(TransactionalConnection connection) {
        this.connection = connection;
    }

    @Override
    public void publishLeadQualified(LeadQualifiedTrigger trigger) {
        String key = idempotencyKey(trigger);
        String payload = "{\"leadId\":\"" + trigger.leadId() + "\",\"ownerUserId\":\""
                + trigger.ownerUserId() + "\",\"qualifiedByUserId\":\"" + trigger.qualifiedByUserId() + "\"}";
        try {
            Connection transaction = connection.currentConnection();
            String conflictClause = "H2".equals(transaction.getMetaData().getDatabaseProductName())
                    ? " ON CONFLICT DO NOTHING" : " ON CONFLICT (organization_id, idempotency_key) DO NOTHING";
            try (PreparedStatement statement = transaction.prepareStatement("""
                    INSERT INTO outbox_events (
                        id, organization_id, aggregate_type, aggregate_id, event_type, payload,
                        idempotency_key, delivery_status, retry_count, occurred_at
                    )
                    VALUES (?, ?, 'LEAD', ?, ?, CAST(? AS jsonb), ?, 'PENDING', 0, ?)
                    """ + conflictClause)) {
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, trigger.organizationId());
                statement.setObject(3, trigger.leadId());
                statement.setString(4, EVENT_TYPE);
                statement.setString(5, payload);
                statement.setString(6, key);
                statement.setTimestamp(7, Timestamp.from(trigger.occurredAt()));
                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            throw new WorkflowPersistenceException("Unable to publish qualified Lead outbox event", exception);
        }
    }

    public static String idempotencyKey(LeadQualifiedTrigger trigger) {
        return KEY_NAMESPACE + ":" + trigger.leadId() + ":" + trigger.occurredAt().toEpochMilli();
    }
}
