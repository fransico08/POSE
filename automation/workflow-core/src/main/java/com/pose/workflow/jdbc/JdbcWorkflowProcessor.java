package com.pose.workflow.jdbc;

import com.pose.workflow.QualifiedLeadRuleEvaluator;
import com.pose.workflow.WorkflowDecision;
import com.pose.workflow.WorkflowEvent;
import com.pose.workflow.WorkflowIdempotency;
import com.pose.workflow.WorkflowTaskCommand;
import com.pose.workflow.WorkflowTaskIntent;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * W08-01 processor. Every claimed event is completed in one DB transaction;
 * PostgreSQL provides production locking, while H2 syntax supports isolated tests.
 */
public final class JdbcWorkflowProcessor {
    private static final String EVENT_TYPE = QualifiedLeadRuleEvaluator.EVENT_TYPE;
    private static final String WORKFLOW_TYPE = "LEAD_QUALIFIED_FOLLOW_UP";
    private final DataSource dataSource;
    private final QualifiedLeadRuleEvaluator rules;
    private final AutomationActorResolver automationActors;
    private final WorkflowTaskTemplate template;
    private final Clock clock;
    private final int maxAttempts;
    private final WorkflowTransactionRunner transactions;
    private final WorkflowTaskAudit audit;

    public JdbcWorkflowProcessor(DataSource dataSource, AutomationActorResolver automationActors,
                                 WorkflowTaskTemplate template, Clock clock, int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least one");
        }
        this.dataSource = dataSource;
        this.rules = new QualifiedLeadRuleEvaluator();
        this.automationActors = automationActors;
        this.template = template;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.transactions = new WorkflowTransactionRunner() {
            public <T> T execute(Work<T> work) {
                try (Connection connection = dataSource.getConnection()) {
                    connection.setAutoCommit(false);
                    try {
                        T result = work.run(connection);
                        connection.commit();
                        return result;
                    } catch (SQLException | RuntimeException exception) {
                        rollback(connection);
                        throw new WorkflowPersistenceException("Workflow transaction failed", exception);
                    }
                } catch (SQLException exception) {
                    throw new WorkflowPersistenceException("Unable to open workflow transaction", exception);
                }
            }
        };
        this.audit = (connection, organization, outbox, execution, task) -> {};
    }

    public JdbcWorkflowProcessor(WorkflowTransactionRunner transactions, AutomationActorResolver automationActors,
                                 WorkflowTaskTemplate template, WorkflowTaskAudit audit, Clock clock, int maxAttempts) {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be at least one");
        this.dataSource = null;
        this.rules = new QualifiedLeadRuleEvaluator();
        this.automationActors = automationActors;
        this.template = template;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.transactions = transactions;
        this.audit = audit;
    }

    /** Processes each event once per call, avoiding a tight retry loop in one scheduler tick. */
    public int processPendingBatch(int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be at least one");
        }
        Set<UUID> attempted = new HashSet<>();
        while (attempted.size() < batchSize) {
            UUID processed = processOne(attempted);
            if (processed == null) {
                return attempted.size();
            }
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
        Execution execution = startOrLoadExecution(connection, event, WorkflowIdempotency.key(event.id()));
        if (execution.done()) {
            markPublished(connection, event);
            return;
        }
        Subject subject = loadQualifiedSubject(connection, event);
        if (subject == null) {
            markFailed(connection, event, execution.id(), event.retryCount() + 1, "LEAD_UNAVAILABLE");
            return;
        }
        WorkflowEvent workflowEvent = new WorkflowEvent(event.id(), event.organizationId(), event.leadId(),
                subject.ownerUserId(), event.eventType(), event.occurredAt(), event.retryCount());
        WorkflowDecision decision = rules.evaluate(workflowEvent, subject.qualified(), subject.ownerEligible());
        if (!decision.shouldCreateTask()) {
            markFailed(connection, event, execution.id(), event.retryCount() + 1, decision.rejectionReason());
            return;
        }
        UUID actor = automationActors.resolve(connection, event.organizationId(), event.id());
        if (actor == null || !activeUserExists(connection, event.organizationId(), actor)) {
            markFailed(connection, event, execution.id(), event.retryCount()+1, "QUALIFIER_NOT_ELIGIBLE");
            return;
        }
        UUID taskId = createTaskIfAbsent(connection, workflowEvent, actor, execution.idempotencyKey(), event.id(), execution.id());
        markDone(connection, event, execution.id(), taskId);
    }

    private ClaimedEvent claimNext(Connection connection, Set<UUID> excluded) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT outbox.id, outbox.organization_id, outbox.aggregate_id, outbox.event_type,
                       outbox.occurred_at, outbox.retry_count
                FROM outbox_events AS outbox
                WHERE outbox.delivery_status = 'PENDING'
                  AND outbox.event_type = ?
                  AND outbox.aggregate_type = 'LEAD'
                """);
        if (!excluded.isEmpty()) {
            sql.append(" AND outbox.id NOT IN (");
            sql.append("?,".repeat(excluded.size()));
            sql.setLength(sql.length() - 1);
            sql.append(')');
        }
        sql.append(" ORDER BY outbox.occurred_at, outbox.id LIMIT 1 FOR UPDATE SKIP LOCKED");
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            statement.setString(1, EVENT_TYPE);
            int index = 2;
            for (UUID id : excluded) {
                statement.setObject(index++, id);
            }
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return null;
                }
                return new ClaimedEvent(row.getObject("id", UUID.class), row.getObject("organization_id", UUID.class),
                        row.getObject("aggregate_id", UUID.class), row.getString("event_type"),
                        row.getTimestamp("occurred_at").toInstant(), row.getInt("retry_count"));
            }
        }
    }

    private Subject loadQualifiedSubject(Connection connection, ClaimedEvent event) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT lead.status, lead.owner_user_id,
                       EXISTS (
                           SELECT 1
                           FROM users AS owner_user
                           JOIN user_role_grants AS grant_record
                             ON grant_record.organization_id = owner_user.organization_id
                            AND grant_record.user_id = owner_user.id
                           JOIN roles AS role_record
                             ON role_record.organization_id = grant_record.organization_id
                            AND role_record.id = grant_record.role_id
                           JOIN sales_assignments AS assignment
                             ON assignment.organization_id = lead.organization_id
                            AND assignment.offering_id = lead.offering_id
                            AND assignment.sales_user_id = owner_user.id
                           JOIN offerings AS offering
                             ON offering.organization_id=lead.organization_id AND offering.id=lead.offering_id
                           JOIN teams AS team
                             ON team.organization_id=offering.organization_id AND team.id=offering.team_id AND team.status='ACTIVE'
                           JOIN team_memberships AS membership
                             ON membership.organization_id=team.organization_id AND membership.team_id=team.id
                             AND membership.user_id=owner_user.id AND membership.status='ACTIVE'
                           WHERE owner_user.organization_id = lead.organization_id
                             AND owner_user.id = lead.owner_user_id
                             AND owner_user.status = 'ACTIVE'
                             AND owner_user.archived_at IS NULL
                             AND role_record.code = 'SALES_REP'
                             AND role_record.status = 'ACTIVE'
                             AND grant_record.granted_at <= ?
                             AND (grant_record.revoked_at IS NULL OR grant_record.revoked_at > ?)
                             AND assignment.status = 'ACTIVE'
                             AND assignment.revoked_at IS NULL
                             AND offering.status = 'ACTIVE'
                             AND assignment.effective_from <= ?
                             AND (assignment.effective_to IS NULL OR assignment.effective_to >= ?)
                             AND membership.joined_at <= ?
                             AND (membership.left_at IS NULL OR membership.left_at > ?)
                             AND EXISTS (SELECT 1 FROM user_role_grants pg
                                 JOIN roles pr ON pr.id=pg.role_id AND pr.organization_id=pg.organization_id AND pr.status='ACTIVE'
                                 JOIN role_permissions rp ON rp.role_id=pr.id
                                 JOIN permissions p ON p.id=rp.permission_id AND p.code='offering.read'
                                 WHERE pg.organization_id=owner_user.organization_id AND pg.user_id=owner_user.id
                                   AND pg.granted_at<=? AND (pg.revoked_at IS NULL OR pg.revoked_at>?))
                       ) AS owner_eligible
                FROM leads AS lead
                WHERE lead.organization_id = ? AND lead.id = ? AND lead.archived_at IS NULL
                FOR UPDATE
                """)) {
            Timestamp now = Timestamp.from(clock.instant());
            statement.setTimestamp(1, now);
            statement.setTimestamp(2, now);
            statement.setTimestamp(3, now);
            statement.setTimestamp(4, now);
            statement.setTimestamp(5, now); statement.setTimestamp(6, now);
            statement.setTimestamp(7, now); statement.setTimestamp(8, now);
            statement.setObject(9, event.organizationId());
            statement.setObject(10, event.leadId());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return null;
                }
                return new Subject("QUALIFIED".equals(row.getString("status")),
                        row.getObject("owner_user_id", UUID.class), row.getBoolean("owner_eligible"));
            }
        }
    }

    private Execution startOrLoadExecution(Connection connection, ClaimedEvent event, String key) throws SQLException {
        Instant now = clock.instant();
        String conflict = "H2".equals(connection.getMetaData().getDatabaseProductName())
                ? " ON CONFLICT DO NOTHING" : " ON CONFLICT (organization_id, idempotency_key) DO NOTHING";
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO workflow_executions (
                    id, organization_id, outbox_event_id, workflow_type, idempotency_key,
                    status, retry_count, started_at
                )
                VALUES (?, ?, ?, ?, ?, 'RUNNING', ?, ?)
                """ + conflict)) {
            insert.setObject(1, UUID.randomUUID());
            insert.setObject(2, event.organizationId());
            insert.setObject(3, event.id());
            insert.setString(4, WORKFLOW_TYPE);
            insert.setString(5, key);
            insert.setInt(6, event.retryCount());
            insert.setTimestamp(7, Timestamp.from(now));
            insert.executeUpdate();
        }
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT id, status, idempotency_key
                FROM workflow_executions
                WHERE organization_id = ? AND idempotency_key = ?
                FOR UPDATE
                """)) {
            select.setObject(1, event.organizationId());
            select.setString(2, key);
            try (ResultSet row = select.executeQuery()) {
                if (!row.next()) {
                    throw new SQLException("Workflow execution was not persisted");
                }
                UUID id = row.getObject("id", UUID.class);
                boolean done = "DONE".equals(row.getString("status"));
                if (!done) {
                    resumeExecution(connection, event, id);
                }
                return new Execution(id, key, done);
            }
        }
    }

    private void resumeExecution(Connection connection, ClaimedEvent event, UUID executionId) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE workflow_executions
                SET status = 'RUNNING', retry_count = ?, last_error = NULL, started_at = ?, finished_at = NULL
                WHERE organization_id = ? AND id = ?
                """)) {
            update.setInt(1, event.retryCount());
            update.setTimestamp(2, Timestamp.from(clock.instant()));
            update.setObject(3, event.organizationId());
            update.setObject(4, executionId);
            update.executeUpdate();
        }
    }

    private boolean activeUserExists(Connection connection, UUID organizationId, UUID userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1
                FROM users
                WHERE organization_id = ? AND id = ? AND status = 'ACTIVE' AND archived_at IS NULL
                """)) {
            statement.setObject(1, organizationId);
            statement.setObject(2, userId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next();
            }
        }
    }

    private UUID createTaskIfAbsent(Connection connection, WorkflowEvent event, UUID actor, String key, UUID outboxId, UUID executionId) throws SQLException {
        WorkflowTaskTemplate.Details details = template.forQualifiedLead(event, clock.instant());
        boolean h2 = "H2".equals(connection.getMetaData().getDatabaseProductName());
        UUID newTaskId = UUID.randomUUID();
        String conflict = h2 ? " ON CONFLICT DO NOTHING"
                : " ON CONFLICT (organization_id, idempotency_key) WHERE idempotency_key IS NOT NULL DO NOTHING RETURNING id";
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO crm_tasks (
                    id, organization_id, lead_id, owner_user_id, created_by_user_id,
                    title, description, priority, status, due_at, origin, idempotency_key, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', ?, 'WORKFLOW', ?, ?, ?)
                """ + conflict)) {
            Instant now = clock.instant();
            statement.setObject(1, newTaskId);
            statement.setObject(2, event.organizationId());
            statement.setObject(3, event.leadId());
            statement.setObject(4, event.ownerUserId());
            statement.setObject(5, actor);
            statement.setString(6, details.title());
            statement.setString(7, details.description());
            statement.setString(8, details.priority());
            statement.setTimestamp(9, Timestamp.from(details.dueAt()));
            statement.setString(10, key);
            statement.setTimestamp(11, Timestamp.from(now));
            statement.setTimestamp(12, Timestamp.from(now));
            if (h2) {
                if (statement.executeUpdate() == 1) {
                    audit.taskCreated(connection, event.organizationId(), outboxId, executionId, newTaskId);
                    return newTaskId;
                }
            } else {
                try (ResultSet row = statement.executeQuery()) {
                    if (row.next()) {
                        UUID task = row.getObject("id", UUID.class);
                        audit.taskCreated(connection, event.organizationId(), outboxId, executionId, task);
                        return task;
                    }
                }
            }
        }
        try (PreparedStatement query = connection.prepareStatement("SELECT id FROM crm_tasks WHERE organization_id=? AND idempotency_key=?")) {
            query.setObject(1, event.organizationId()); query.setString(2, key);
            try (ResultSet row=query.executeQuery()) {
                if (!row.next()) throw new SQLException("Workflow task was not persisted");
                return row.getObject("id", UUID.class);
            }
        }
    }

    private void markDone(Connection connection, ClaimedEvent event, UUID executionId, UUID taskId) throws SQLException {
        Instant now = clock.instant();
        try (PreparedStatement execution = connection.prepareStatement("""
                UPDATE workflow_executions
                SET status = 'DONE', created_task_id = ?, finished_at = ?, last_error = NULL
                WHERE organization_id = ? AND id = ?
                """)) {
            execution.setObject(1, taskId);
            execution.setTimestamp(2, Timestamp.from(now));
            execution.setObject(3, event.organizationId());
            execution.setObject(4, executionId);
            execution.executeUpdate();
        }
        markPublished(connection, event);
    }

    private void markPublished(Connection connection, ClaimedEvent event) throws SQLException {
        try (PreparedStatement outbox = connection.prepareStatement("""
                UPDATE outbox_events
                SET delivery_status = 'PUBLISHED', published_at = ?, last_error = NULL
                WHERE organization_id = ? AND id = ? AND delivery_status = 'PENDING'
                """)) {
            outbox.setTimestamp(1, Timestamp.from(clock.instant()));
            outbox.setObject(2, event.organizationId());
            outbox.setObject(3, event.id());
            outbox.executeUpdate();
        }
    }

    private void markFailed(Connection connection, ClaimedEvent event, UUID executionId, int attempts, String error) throws SQLException {
        Instant now = clock.instant();
        try (PreparedStatement execution = connection.prepareStatement("""
                UPDATE workflow_executions
                SET status = 'FAILED', retry_count = ?, last_error = ?, finished_at = ?
                WHERE organization_id = ? AND id = ?
                """)) {
            execution.setInt(1, attempts);
            execution.setString(2, error);
            execution.setTimestamp(3, Timestamp.from(now));
            execution.setObject(4, event.organizationId());
            execution.setObject(5, executionId);
            execution.executeUpdate();
        }
        try (PreparedStatement outbox = connection.prepareStatement("""
                UPDATE outbox_events
                SET delivery_status = 'FAILED', retry_count = ?, last_error = ?
                WHERE organization_id = ? AND id = ? AND delivery_status = 'PENDING'
                """)) {
            outbox.setInt(1, attempts);
            outbox.setString(2, error);
            outbox.setObject(3, event.organizationId());
            outbox.setObject(4, event.id());
            outbox.executeUpdate();
        }
    }

    private void recordTransientFailure(ClaimedEvent event, String error) {
        transactions.execute(connection -> {
            try (PreparedStatement lock = connection.prepareStatement("SELECT retry_count FROM outbox_events WHERE organization_id=? AND id=? AND delivery_status='PENDING' FOR UPDATE")) {
                lock.setObject(1,event.organizationId()); lock.setObject(2,event.id());
                try (ResultSet row=lock.executeQuery()) {
                    if (!row.next() || row.getInt(1) != event.retryCount()) return null;
                }
                int attempts = event.retryCount() + 1;
                String key = WorkflowIdempotency.key(event.id());
                Execution execution = startOrLoadExecution(connection, event, key);
                if (execution.done()) {
                    markPublished(connection, event);
                } else if (attempts >= maxAttempts) {
                    markFailed(connection, event, execution.id(), attempts, error);
                } else {
                    recordRetry(connection, event, execution.id(), attempts, error);
                }
            }
            return null;
        });
    }

    private void recordRetry(Connection connection, ClaimedEvent event, UUID executionId, int attempts, String error) throws SQLException {
        try (PreparedStatement execution = connection.prepareStatement("""
                UPDATE workflow_executions
                SET status = 'PENDING', retry_count = ?, last_error = ?, finished_at = NULL
                WHERE organization_id = ? AND id = ?
                """)) {
            execution.setInt(1, attempts);
            execution.setString(2, error);
            execution.setObject(3, event.organizationId());
            execution.setObject(4, executionId);
            execution.executeUpdate();
        }
        try (PreparedStatement outbox = connection.prepareStatement("""
                UPDATE outbox_events
                SET retry_count = ?, last_error = ?
                WHERE organization_id = ? AND id = ? AND delivery_status = 'PENDING'
                """)) {
            outbox.setInt(1, attempts);
            outbox.setString(2, error);
            outbox.setObject(3, event.organizationId());
            outbox.setObject(4, event.id());
            outbox.executeUpdate();
        }
    }

    private static void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // The original failure remains the useful error for the scheduler.
        }
    }

    private record ClaimedEvent(UUID id, UUID organizationId, UUID leadId, String eventType,
                                Instant occurredAt, int retryCount) {
    }

    private record Subject(boolean qualified, UUID ownerUserId, boolean ownerEligible) {
    }

    private record Execution(UUID id, String idempotencyKey, boolean done) {
    }
}
