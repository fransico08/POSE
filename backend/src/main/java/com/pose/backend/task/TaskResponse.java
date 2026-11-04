package com.pose.backend.task;

import java.time.Instant;
import java.util.UUID;

public record TaskResponse(UUID id, UUID customerId, UUID leadId, UUID opportunityId,
                           UUID ownerUserId, String ownerName, UUID createdByUserId,
                           String title, String description, String priority, String status,
                           Instant dueAt, String result, String cancellationReason, String origin,
                           Instant completedAt, Instant createdAt, Instant updatedAt) {
    public TaskResponse redacted() {
        return new TaskResponse(id, customerId, leadId, opportunityId, ownerUserId, ownerName,
                createdByUserId, null, null, priority, status, dueAt, null, null, origin,
                completedAt, createdAt, updatedAt);
    }
}
