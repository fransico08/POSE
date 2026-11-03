package com.pose.backend.opportunity;

import java.math.BigDecimal;
import java.util.UUID;

public record OpportunityLineResponse(
        UUID id,
        UUID offeringId,
        String offeringCode,
        String offeringName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal discountPercent,
        BigDecimal lineValue,
        boolean legacyValueOnly) {
}
