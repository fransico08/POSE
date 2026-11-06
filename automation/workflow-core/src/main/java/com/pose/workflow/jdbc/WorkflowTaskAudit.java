package com.pose.workflow.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

/** Records an audit event in the same transaction as the newly inserted task. */
@FunctionalInterface
public interface WorkflowTaskAudit {
    void taskCreated(Connection connection, UUID organizationId, UUID outboxId, UUID executionId, UUID taskId) throws SQLException;
}
