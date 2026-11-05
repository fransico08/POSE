package com.pose.workflow;

/** Worker failures are explicitly classified so transient failures cannot retry forever. */
public final class WorkflowFailure extends RuntimeException {
    private final boolean retryable;

    private WorkflowFailure(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public static WorkflowFailure transientFailure(String message) {
        return new WorkflowFailure(message, true);
    }

    public static WorkflowFailure permanentFailure(String message) {
        return new WorkflowFailure(message, false);
    }

    public boolean retryable() {
        return retryable;
    }
}
