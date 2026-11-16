package com.pose.workflow.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** W13 processor for durable MESSAGE_RECEIVED events. It never impersonates the conversation owner. */
public final class JdbcConversationMessageProcessor {
    private static final String WORKFLOW_TYPE = "CONVERSATION_FIRST_RESPONSE";
    private static final String TASK_TITLE = "Phản hồi đầu tiên";
    private static final String TASK_DESCRIPTION = "Tin nhắn mới cần được nhân viên phụ trách kiểm tra và phản hồi.";

    private final WorkflowTransactionRunner transactions;
    private final WorkflowTaskAudit audit;
    private final Clock clock;
    private final int maxAttempts;
    private final long dueDays;

    public JdbcConversationMessageProcessor(WorkflowTransactionRunner transactions, WorkflowTaskAudit audit,
                                            Clock clock, int maxAttempts, long dueDays) {
        if (maxAttempts < 1 || dueDays < 0) throw new IllegalArgumentException("Invalid W13 workflow settings");
        this.transactions = transactions;
        this.audit = audit;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.dueDays = dueDays;
    }

    public int processPendingBatch(int batchSize) {
        if (batchSize < 1) throw new IllegalArgumentException("batchSize must be positive");
        Set<UUID> attempted = new HashSet<>();
        while (attempted.size() < batchSize) {
            UUID processed = processOne(attempted);
            if (processed == null) return attempted.size();
            attempted.add(processed);
        }
        return attempted.size();
    }

    private UUID processOne(Set<UUID> excluded) {
        ClaimedEvent[] current = new ClaimedEvent[1];
        try {
            return transactions.execute(connection -> {
                current[0] = claimNext(connection, excluded);
                if (current[0] == null) return null;
                processClaimed(connection, current[0]);
                return current[0].id();
            });
        } catch (RuntimeException exception) {
            if (current[0] == null) throw exception;
            recordTransientFailure(current[0], "WORKFLOW_PROCESSING_ERROR");
            return current[0].id();
        }
    }

    private void processClaimed(Connection connection, ClaimedEvent event) throws SQLException {
        Owner owner = loadOwner(connection, event);
        if (owner == null || owner.userId() == null) {
            markPublished(connection, event);
            return;
        }
        if (!eligibleOwner(connection, event, owner)) {
            markFailure(connection, event, "CONVERSATION_OWNER_NOT_ELIGIBLE");
            return;
        }

        String key = idempotencyKey(event.conversationId());
        Execution execution = startOrLoadExecution(connection, event, key);
        if (execution.done()) {
            markPublished(connection, event);
            return;
        }
        UUID taskId = createTaskIfAbsent(connection, event, owner.userId(), key, execution.id());
        markDone(connection, event, execution.id(), taskId);
    }

    private ClaimedEvent claimNext(Connection connection, Set<UUID> excluded) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT id,organization_id,aggregate_id,occurred_at,retry_count
                FROM outbox_events
                WHERE delivery_status='PENDING' AND event_type='MESSAGE_RECEIVED' AND aggregate_type='CONVERSATION'
                """);
        if (!excluded.isEmpty()) {
            sql.append(" AND id NOT IN (").append("?,".repeat(excluded.size()));
            sql.setLength(sql.length() - 1);
            sql.append(')');
        }
        sql.append(" ORDER BY occurred_at,id LIMIT 1 FOR UPDATE SKIP LOCKED");
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int index = 1;
            for (UUID id : excluded) statement.setObject(index++, id);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return null;
                return new ClaimedEvent(row.getObject("id", UUID.class), row.getObject("organization_id", UUID.class),
                        row.getObject("aggregate_id", UUID.class), row.getTimestamp("occurred_at").toInstant(),
                        row.getInt("retry_count"));
            }
        }
    }

    private Owner loadOwner(Connection connection, ClaimedEvent event) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT c.assigned_user_id,ca.team_id
                FROM conversations c JOIN channel_accounts ca
                  ON ca.organization_id=c.organization_id AND ca.id=c.channel_account_id
                WHERE c.organization_id=? AND c.id=?
                FOR UPDATE
                """)) {
            statement.setObject(1, event.organizationId());
            statement.setObject(2, event.conversationId());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) throw new SQLException("Conversation for workflow event is missing");
                return new Owner(row.getObject("assigned_user_id", UUID.class), row.getObject("team_id", UUID.class));
            }
        }
    }

    private boolean eligibleOwner(Connection connection, ClaimedEvent event, Owner owner) throws SQLException {
        if (owner.teamId() == null) return false;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT EXISTS (
                    SELECT 1 FROM users u
                    JOIN team_memberships tm ON tm.organization_id=u.organization_id AND tm.user_id=u.id AND tm.team_id=?
                    JOIN user_role_grants g ON g.organization_id=u.organization_id AND g.user_id=u.id
                    JOIN roles r ON r.organization_id=g.organization_id AND r.id=g.role_id AND r.status='ACTIVE'
                    JOIN role_permissions rp ON rp.role_id=r.id
                    JOIN permissions p ON p.id=rp.permission_id AND p.code='conversation.reply'
                    WHERE u.organization_id=? AND u.id=? AND u.status='ACTIVE' AND u.archived_at IS NULL
                      AND tm.status='ACTIVE' AND tm.joined_at<=? AND (tm.left_at IS NULL OR tm.left_at>?)
                      AND g.granted_at<=? AND (g.revoked_at IS NULL OR g.revoked_at>?)
                      AND r.code IN ('SALES_REP','CUSTOMER_CARE')
                )
                """)) {
            Timestamp now = Timestamp.from(clock.instant());
            statement.setObject(1, owner.teamId());
            statement.setObject(2, event.organizationId());
            statement.setObject(3, owner.userId());
            statement.setTimestamp(4, now); statement.setTimestamp(5, now);
            statement.setTimestamp(6, now); statement.setTimestamp(7, now);
            try (ResultSet row = statement.executeQuery()) { return row.next() && row.getBoolean(1); }
        }
    }

    private Execution startOrLoadExecution(Connection connection, ClaimedEvent event, String key) throws SQLException {
        String conflict = isH2(connection) ? " ON CONFLICT DO NOTHING"
                : " ON CONFLICT (organization_id,idempotency_key) DO NOTHING";
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO workflow_executions(id,organization_id,outbox_event_id,workflow_type,idempotency_key,
                    status,retry_count,started_at)
                VALUES (?,?,?,?,?,'RUNNING',?,?)
                """ + conflict)) {
            statement.setObject(1, UUID.randomUUID()); statement.setObject(2, event.organizationId());
            statement.setObject(3, event.id()); statement.setString(4, WORKFLOW_TYPE); statement.setString(5, key);
            statement.setInt(6, event.retryCount()); statement.setTimestamp(7, Timestamp.from(clock.instant()));
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id,status FROM workflow_executions WHERE organization_id=? AND idempotency_key=? FOR UPDATE
                """)) {
            statement.setObject(1, event.organizationId()); statement.setString(2, key);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) throw new SQLException("Workflow execution was not persisted");
                UUID id = row.getObject("id", UUID.class);
                boolean done = "DONE".equals(row.getString("status"));
                if (!done) try (PreparedStatement resume = connection.prepareStatement("""
                        UPDATE workflow_executions SET status='RUNNING',retry_count=?,last_error=NULL,
                            started_at=?,finished_at=NULL WHERE organization_id=? AND id=?
                        """)) {
                    resume.setInt(1, event.retryCount()); resume.setTimestamp(2, Timestamp.from(clock.instant()));
                    resume.setObject(3, event.organizationId()); resume.setObject(4, id); resume.executeUpdate();
                }
                return new Execution(id, done);
            }
        }
    }

    private UUID createTaskIfAbsent(Connection connection, ClaimedEvent event, UUID owner, String key,
                                    UUID executionId) throws SQLException {
        UUID id = UUID.randomUUID();
        String conflict = isH2(connection) ? " ON CONFLICT DO NOTHING"
                : " ON CONFLICT (organization_id,conversation_id) DO NOTHING RETURNING id";
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO crm_tasks(id,organization_id,customer_id,lead_id,opportunity_id,conversation_id,
                    owner_user_id,created_by_user_id,created_by_actor_type,title,description,priority,status,due_at,
                    origin,idempotency_key,created_at,updated_at)
                VALUES (?,?,NULL,NULL,NULL,?,?,NULL,'SYSTEM',?,?,'MEDIUM','OPEN',?,'WORKFLOW',?,?,?)
                """ + conflict)) {
            Timestamp now = Timestamp.from(clock.instant());
            statement.setObject(1, id); statement.setObject(2, event.organizationId()); statement.setObject(3, event.conversationId());
            statement.setObject(4, owner); statement.setString(5, TASK_TITLE); statement.setString(6, TASK_DESCRIPTION);
            statement.setTimestamp(7, Timestamp.from(clock.instant().plus(dueDays, ChronoUnit.DAYS)));
            statement.setString(8, key); statement.setTimestamp(9, now); statement.setTimestamp(10, now);
            if (isH2(connection)) {
                if (statement.executeUpdate() == 1) {
                    audit.taskCreated(connection, event.organizationId(), event.id(), executionId, id);
                    return id;
                }
            } else {
                try (ResultSet row = statement.executeQuery()) {
                    if (row.next()) {
                        UUID created = row.getObject("id", UUID.class);
                        audit.taskCreated(connection, event.organizationId(), event.id(), executionId, created);
                        return created;
                    }
                }
            }
        }
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT id FROM crm_tasks WHERE organization_id=? AND conversation_id=? AND origin='WORKFLOW'
                """)) {
            select.setObject(1, event.organizationId()); select.setObject(2, event.conversationId());
            try (ResultSet row = select.executeQuery()) {
                if (!row.next()) throw new SQLException("Conversation workflow task was not persisted");
                return row.getObject("id", UUID.class);
            }
        }
    }

    private void markDone(Connection connection, ClaimedEvent event, UUID executionId, UUID taskId) throws SQLException {
        try (PreparedStatement execution = connection.prepareStatement("""
                UPDATE workflow_executions SET status='DONE',created_task_id=?,finished_at=?,last_error=NULL
                WHERE organization_id=? AND id=?
                """)) {
            execution.setObject(1, taskId); execution.setTimestamp(2, Timestamp.from(clock.instant()));
            execution.setObject(3, event.organizationId()); execution.setObject(4, executionId); execution.executeUpdate();
        }
        markPublished(connection, event);
    }

    private void markPublished(Connection connection, ClaimedEvent event) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE outbox_events SET delivery_status='PUBLISHED',published_at=?,last_error=NULL
                WHERE organization_id=? AND id=? AND delivery_status='PENDING'
                """)) {
            statement.setTimestamp(1, Timestamp.from(clock.instant())); statement.setObject(2, event.organizationId());
            statement.setObject(3, event.id()); statement.executeUpdate();
        }
    }

    private void markFailure(Connection connection, ClaimedEvent event, String error) throws SQLException {
        int attempts = event.retryCount() + 1;
        boolean failed = attempts >= maxAttempts;
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE outbox_events SET delivery_status=?,retry_count=?,last_error=?
                WHERE organization_id=? AND id=? AND delivery_status='PENDING'
                """)) {
            statement.setString(1, failed ? "FAILED" : "PENDING"); statement.setInt(2, attempts); statement.setString(3, error);
            statement.setObject(4, event.organizationId()); statement.setObject(5, event.id()); statement.executeUpdate();
        }
    }

    private void recordTransientFailure(ClaimedEvent event, String error) {
        transactions.execute(connection -> {
            try (PreparedStatement lock = connection.prepareStatement("""
                    SELECT retry_count FROM outbox_events WHERE organization_id=? AND id=? AND delivery_status='PENDING' FOR UPDATE
                    """)) {
                lock.setObject(1, event.organizationId()); lock.setObject(2, event.id());
                try (ResultSet row = lock.executeQuery()) {
                    if (!row.next() || row.getInt(1) != event.retryCount()) return null;
                }
            }
            int attempts = event.retryCount() + 1;
            boolean failed = attempts >= maxAttempts;
            String key = idempotencyKey(event.conversationId());
            try (PreparedStatement execution = connection.prepareStatement("""
                    UPDATE workflow_executions SET status=?,retry_count=?,last_error=?,finished_at=?
                    WHERE organization_id=? AND idempotency_key=? AND status<>'DONE'
                    """)) {
                execution.setString(1, failed ? "FAILED" : "PENDING"); execution.setInt(2, attempts);
                execution.setString(3, error); execution.setTimestamp(4, failed ? Timestamp.from(clock.instant()) : null);
                execution.setObject(5, event.organizationId()); execution.setString(6, key); execution.executeUpdate();
            }
            try (PreparedStatement outbox = connection.prepareStatement("""
                    UPDATE outbox_events SET delivery_status=?,retry_count=?,last_error=?
                    WHERE organization_id=? AND id=? AND delivery_status='PENDING'
                    """)) {
                outbox.setString(1, failed ? "FAILED" : "PENDING"); outbox.setInt(2, attempts); outbox.setString(3, error);
                outbox.setObject(4, event.organizationId()); outbox.setObject(5, event.id()); outbox.executeUpdate();
            }
            return null;
        });
    }

    private static String idempotencyKey(UUID conversationId) {
        return "workflow.conversation.first-response.v1:" + conversationId;
    }

    private static boolean isH2(Connection connection) throws SQLException {
        return "H2".equals(connection.getMetaData().getDatabaseProductName());
    }

    private record ClaimedEvent(UUID id, UUID organizationId, UUID conversationId, java.time.Instant occurredAt,
                                int retryCount) {}
    private record Owner(UUID userId, UUID teamId) {}
    private record Execution(UUID id, boolean done) {}
}
