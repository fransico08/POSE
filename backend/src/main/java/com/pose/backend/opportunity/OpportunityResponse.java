package com.pose.backend.opportunity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record OpportunityResponse(
        UUID id,
        String title,
        UUID customerId,
        String customerName,
        UUID leadId,
        String leadName,
        UUID ownerUserId,
        String ownerName,
        String stage,
        LocalDate expectedCloseDate,
        BigDecimal expectedValue,
        String currencyCode,
        BigDecimal actualValue,
        LocalDate actualCloseDate,
        String notes,
        String closingReason,
        List<OpportunityLineResponse> lines,
        Instant createdAt,
        Instant updatedAt) {
    public OpportunityResponse {
        lines = List.copyOf(lines);
    }
}
