package com.pose.workflow.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

/** Supplies the connection already bound to the caller's business transaction. */
@FunctionalInterface
public interface TransactionalConnection {
    Connection currentConnection() throws SQLException;
}
