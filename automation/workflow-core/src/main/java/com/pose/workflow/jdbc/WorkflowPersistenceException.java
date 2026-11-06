package com.pose.workflow.jdbc;

/** Adapter-level failure. The Spring scheduler decides when the next bounded batch runs. */
public final class WorkflowPersistenceException extends RuntimeException {
    public WorkflowPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
