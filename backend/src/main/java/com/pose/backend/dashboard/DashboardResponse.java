package com.pose.backend.dashboard;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DashboardResponse(UUID organizationId, Instant asOf, Period period, Filters filters, List<Metric> metrics) {
    public record Period(Instant start, Instant end, String timezone) {}
    public record Filters(UUID ownerUserId, String workflowType, List<String> metricIds) {}
    public record Metric(String metricId, Map<String, Object> dimensions, BigDecimal value, String unit,
                         BigDecimal numerator, BigDecimal denominator, String availability, String reason,
                         Instant periodOrAsOf, Instant generatedAt, String sourceSql) {}
}
