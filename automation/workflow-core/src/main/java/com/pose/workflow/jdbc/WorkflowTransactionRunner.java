package com.pose.workflow.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

/** Runs one callback atomically; the integrating runtime owns commit and rollback. */
@FunctionalInterface
public interface WorkflowTransactionRunner {
    <T> T execute(Work<T> work);

    @FunctionalInterface
    interface Work<T> {
        T run(Connection connection) throws SQLException;
    }
}
