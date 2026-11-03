package com.pose.backend.opportunity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record OpportunityResponse(UUID id, String title, String stage, String expectedValue, String currencyCode,
        LocalDate expectedCloseDate, String notes, Subject customer, Subject lead, Subject owner,
        List<Line> lines, String finalValue, LocalDate actualCloseDate, String closingReason,
        Instant closedAt, Instant createdAt, Instant updatedAt) {
    public record Subject(UUID id, String name) {}
    public record Line(UUID offeringId, String offeringCode, String offeringName, String quantity,
                       String unitPrice, String discountAmount, String lineTotal) {}
}
