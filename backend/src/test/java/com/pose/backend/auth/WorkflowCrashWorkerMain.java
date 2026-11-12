package com.pose.backend.auth;

import com.pose.workflow.jdbc.JdbcWorkflowProcessor;
import com.pose.workflow.jdbc.WorkflowTaskTemplate;
import com.pose.workflow.jdbc.WorkflowTransactionRunner;
import org.postgresql.ds.PGSimpleDataSource;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Test-only worker entry point used to exercise recovery after terminating a worker JVM mid-transaction. */
public final class WorkflowCrashWorkerMain {
    private WorkflowCrashWorkerMain() {}

    public static void main(String[] args) {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(required("POSE_DATABASE_URL"));
        dataSource.setUser(required("POSE_DATABASE_USERNAME"));
        dataSource.setPassword(System.getenv().getOrDefault("POSE_DATABASE_PASSWORD", ""));
        dataSource.setApplicationName("w08-crash-worker");

        WorkflowTransactionRunner transactions = new WorkflowTransactionRunner() {
            @Override
            public <T> T execute(Work<T> work) {
                try (var connection = dataSource.getConnection()) {
                    connection.setAutoCommit(false);
                    try {
                        T result = work.run(connection);
                        connection.commit();
                        return result;
                    } catch (java.sql.SQLException | RuntimeException failure) {
                        try { connection.rollback(); } catch (java.sql.SQLException ignored) { }
                        throw new IllegalStateException("Workflow transaction failed", failure);
                    }
                } catch (java.sql.SQLException failure) {
                    throw new IllegalStateException("Unable to open workflow transaction", failure);
                }
            }
        };
        Clock clock = Clock.systemUTC();
        JdbcWorkflowProcessor processor = new JdbcWorkflowProcessor(
                transactions,
                (connection, organizationId, outboxId) -> {
                    try (PreparedStatement query = connection.prepareStatement(
                            "SELECT CAST(payload->>'qualifiedByUserId' AS uuid) FROM outbox_events WHERE organization_id=? AND id=?")) {
                        query.setObject(1, organizationId);
                        query.setObject(2, outboxId);
                        try (var row = query.executeQuery()) {
                            return row.next() ? row.getObject(1, UUID.class) : null;
                        }
                    }
                },
                (event, now) -> new WorkflowTaskTemplate.Details(
                        "W08 crash recovery check", null, "MEDIUM", now.plus(1, ChronoUnit.DAYS)),
                (connection, organizationId, outboxId, executionId, taskId) -> {
                    String after = "{\"outboxId\":\"" + outboxId + "\",\"executionId\":\"" + executionId + "\"}";
                    try (PreparedStatement insert = connection.prepareStatement("""
                            INSERT INTO audit_logs(id,organization_id,actor_user_id,actor_type,actor_system_key,
                                action,entity_type,entity_id,before_data,after_data,safe_metadata,result,occurred_at)
                            VALUES (?,?,NULL,'SYSTEM','workflow-lead-qualified','TASK_CREATED','CRM_TASK',?,
                                CAST('{}' AS jsonb),CAST(? AS jsonb),CAST('{}' AS jsonb),'SUCCESS',?)
                            """)) {
                        insert.setObject(1, UUID.randomUUID());
                        insert.setObject(2, organizationId);
                        insert.setObject(3, taskId);
                        insert.setString(4, after);
                        insert.setTimestamp(5, Timestamp.from(clock.instant()));
                        insert.executeUpdate();
                    }
                },
                clock,
                3);
        processor.processPendingBatch(10);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing " + name);
        return value;
    }
}
