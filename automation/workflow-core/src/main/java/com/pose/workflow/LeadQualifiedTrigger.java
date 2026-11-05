package com.pose.workflow;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Validated Lead transition facts passed from LeadService inside its existing transaction. */
public record LeadQualifiedTrigger(UUID organizationId, UUID leadId, UUID ownerUserId, Instant occurredAt) {
    public LeadQualifiedTrigger {
        Objects.requireNonNull(organizationId, "organizationId is required");
        Objects.requireNonNull(leadId, "leadId is required");
        Objects.requireNonNull(ownerUserId, "ownerUserId is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
    }
}
