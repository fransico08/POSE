package com.pose.backend.interaction;

import java.time.Instant;
import java.util.UUID;
import com.pose.backend.crm.CrmWorkReferences;

public record InteractionResponse(UUID id, UUID customerId, UUID leadId, UUID opportunityId, UUID conversationId,
                                  UUID recordedByUserId, String recordedByActorType, String recordedByName,
                                  String type, String channel,
                                  String subject, String content, String outcome, String visibility,
                                  Instant occurredAt, Instant createdAt, Instant updatedAt,
                                  CrmWorkReferences.Subject customer, CrmWorkReferences.Subject lead,
                                  CrmWorkReferences.Opportunity opportunity) {
    public InteractionResponse redacted() {
        return new InteractionResponse(id, customerId, leadId, opportunityId, conversationId, recordedByUserId,
                recordedByActorType, recordedByName, type, channel, null, null, null, visibility, occurredAt, createdAt, updatedAt,
                customer, lead, opportunity);
    }
    public InteractionResponse withoutOpportunityTitle() {
        return new InteractionResponse(id, customerId, leadId, opportunityId, conversationId, recordedByUserId,
                recordedByActorType, recordedByName, type, channel, subject, content, outcome, visibility, occurredAt, createdAt, updatedAt,
                customer, lead, opportunity == null ? null : new CrmWorkReferences.Opportunity(opportunity.id(), null));
    }
}
