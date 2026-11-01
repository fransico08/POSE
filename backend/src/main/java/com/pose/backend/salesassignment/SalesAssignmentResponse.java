package com.pose.backend.salesassignment;

import java.time.Instant;
import java.util.UUID;

public record SalesAssignmentResponse(
        UUID id,
        UUID offeringId,
        String offeringCode,
        String offeringName,
        UUID teamId,
        String teamName,
        UUID salesUserId,
        String salesUserName,
        String status,
        String effectiveState,
        Instant effectiveFrom,
        Instant effectiveTo,
        Instant revokedAt,
        Instant createdAt,
        Instant updatedAt) {
}
