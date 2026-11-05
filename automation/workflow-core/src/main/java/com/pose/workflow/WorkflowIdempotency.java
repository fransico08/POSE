package com.pose.workflow;

import java.util.UUID;

/** Stable, namespaced keys; database uniqueness remains the authoritative duplicate guard. */
public final class WorkflowIdempotency {
    private static final String NAMESPACE = "workflow.lead-qualified.follow-up.v1";

    private WorkflowIdempotency() {
    }

    public static String key(UUID outboxEventId) {
        return NAMESPACE + ":" + outboxEventId;
    }
}
