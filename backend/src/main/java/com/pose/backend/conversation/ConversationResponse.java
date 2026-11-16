package com.pose.backend.conversation;

import java.time.Instant;
import java.util.UUID;

public record ConversationResponse(UUID id, String channelType, String channelName, String status,
                                   UUID assignedUserId, UUID leadId, UUID customerId,
                                   Instant lastInboundAt, Instant lastMessageAt,
                                   String lastMessagePreview, String lastMessageDirection) {
    public ConversationResponse(UUID id, String channelType, String channelName, String status,
                                UUID assignedUserId, UUID leadId, UUID customerId,
                                Instant lastInboundAt, Instant lastMessageAt) {
        this(id, channelType, channelName, status, assignedUserId, leadId, customerId,
                lastInboundAt, lastMessageAt, null, null);
    }
}
