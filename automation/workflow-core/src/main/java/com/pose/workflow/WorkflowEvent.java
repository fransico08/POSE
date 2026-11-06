package com.pose.workflow;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A committed outbox event whose tenant and subject were validated by the producer. */
public record WorkflowEvent(
        UUID id,
        UUID organizationId,
        UUID leadId,
        UUID ownerUserId,
        String eventType,
        Instant occurredAt,
        int retryCount) {

    public WorkflowEvent {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(organizationId, "organizationId is required");
        Objects.requireNonNull(leadId, "leadId is required");
        Objects.requireNonNull(ownerUserId, "ownerUserId is required");
        Objects.requireNonNull(eventType, "eventType is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must not be negative");
        }
    }
}
