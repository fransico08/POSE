package com.pose.backend.lead;
import java.time.Instant;
import java.util.UUID;
public record LeadResponse(UUID id, String displayName, String email, String phone, String source, String notes,
                           UUID ownerUserId, String ownerName, UUID offeringId, String offeringCode, String offeringName,
                           String status, Instant qualifiedAt, UUID convertedCustomerId, Instant convertedAt,
                           String disqualificationReason, Instant createdAt, Instant updatedAt) {}
