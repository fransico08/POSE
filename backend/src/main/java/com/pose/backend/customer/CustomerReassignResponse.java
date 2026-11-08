package com.pose.backend.customer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CustomerReassignResponse(
        UUID id,
        String displayName,
        String email,
        String phone,
        String customerType,
        String address,
        String notes,
        UUID ownerUserId,
        String ownerName,
        UUID relatedOfferingId,
        String offeringCode,
        String offeringName,
        String status,
        Instant createdAt,
        Instant updatedAt,
        UUID handoverId,
        List<UUID> movedOpportunityIds,
        List<UUID> movedTaskIds
) {
    public static CustomerReassignResponse from(CustomerResponse customer, UUID handoverId,
                                                List<UUID> movedOpportunityIds, List<UUID> movedTaskIds) {
        return new CustomerReassignResponse(
                customer.id(),
                customer.displayName(),
                customer.email(),
                customer.phone(),
                customer.customerType(),
                customer.address(),
                customer.notes(),
                customer.ownerUserId(),
                customer.ownerName(),
                customer.relatedOfferingId(),
                customer.offeringCode(),
                customer.offeringName(),
                customer.status(),
                customer.createdAt(),
                customer.updatedAt(),
                handoverId,
                List.copyOf(movedOpportunityIds),
                List.copyOf(movedTaskIds)
        );
    }
}
