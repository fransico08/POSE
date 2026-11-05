package com.pose.workflow;

import java.util.Objects;
import java.util.UUID;

/** Tenant-scoped request for an idempotent WORKFLOW task insert. */
public record WorkflowTaskCommand(
        UUID organizationId,
        UUID leadId,
        UUID ownerUserId,
        WorkflowTaskIntent taskIntent,
        String idempotencyKey) {

    public WorkflowTaskCommand {
        Objects.requireNonNull(organizationId, "organizationId is required");
        Objects.requireNonNull(leadId, "leadId is required");
        Objects.requireNonNull(ownerUserId, "ownerUserId is required");
        Objects.requireNonNull(taskIntent, "taskIntent is required");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");
    }
}
