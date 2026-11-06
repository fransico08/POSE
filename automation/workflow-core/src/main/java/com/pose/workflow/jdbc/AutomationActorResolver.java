package com.pose.workflow.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

/** Resolves an approved, tenant-scoped automation actor; never silently reuses the task owner. */
@FunctionalInterface
public interface AutomationActorResolver {
    UUID resolve(Connection connection, UUID organizationId, UUID outboxEventId) throws SQLException;
}
