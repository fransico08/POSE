package com.pose.backend.customer;
import java.time.Instant;
import java.util.UUID;
public record CustomerResponse(UUID id, String displayName, String email, String phone, String customerType,
                               String address, String notes, UUID ownerUserId, String ownerName,
                               UUID relatedOfferingId, String offeringCode, String offeringName,
                               String status, Instant createdAt, Instant updatedAt) {}
