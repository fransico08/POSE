package com.pose.backend.offering;

import java.time.Instant;
import java.util.UUID;

public record OfferingResponse(
        UUID id,
        String code,
        String name,
        String kind,
        UUID categoryId,
        String categoryName,
        String description,
        String unit,
        String referencePrice,
        String currencyCode,
        String pricingUnit,
        Instant referencePriceEffectiveFrom,
        boolean active,
        UUID teamId,
        String teamName,
        Instant createdAt,
        Instant updatedAt) {
}
