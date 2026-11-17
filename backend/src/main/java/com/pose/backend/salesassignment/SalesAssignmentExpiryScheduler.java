package com.pose.backend.salesassignment;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@org.springframework.context.annotation.Profile("!ai-eval")
public class SalesAssignmentExpiryScheduler {
    private final SalesAssignmentService assignments;

    public SalesAssignmentExpiryScheduler(SalesAssignmentService assignments) {
        this.assignments = assignments;
    }

    @Scheduled(
            initialDelayString = "${pose.sales-assignment.expiry-scan-delay-ms:300000}",
            fixedDelayString = "${pose.sales-assignment.expiry-scan-delay-ms:300000}")
    public void expireDueAssignments() {
        assignments.expireDueAssignments();
    }
}
