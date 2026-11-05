package com.pose.backend.task;

import java.time.Instant;
import java.util.UUID;
import com.pose.backend.crm.CrmWorkReferences;

public record TaskResponse(UUID id, UUID customerId, UUID leadId, UUID opportunityId,
                           UUID ownerUserId, String ownerName, UUID createdByUserId,
                           String title, String description, String priority, String status,
                           Instant dueAt, String result, String cancellationReason, String origin,
                           Instant completedAt, Instant createdAt, Instant updatedAt,
                           CrmWorkReferences.Subject customer, CrmWorkReferences.Subject lead,
                           CrmWorkReferences.Opportunity opportunity) {
    public TaskResponse redacted() {
        return new TaskResponse(id, customerId, leadId, opportunityId, ownerUserId, ownerName,
                createdByUserId, null, null, priority, status, dueAt, null, null, origin,
                completedAt, createdAt, updatedAt, customer, lead, opportunity);
    }
    public TaskResponse withoutOpportunityTitle() {
        return new TaskResponse(id, customerId, leadId, opportunityId, ownerUserId, ownerName,
                createdByUserId, title, description, priority, status, dueAt, result, cancellationReason, origin,
                completedAt, createdAt, updatedAt, customer, lead,
                opportunity == null ? null : new CrmWorkReferences.Opportunity(opportunity.id(), null));
    }
}
