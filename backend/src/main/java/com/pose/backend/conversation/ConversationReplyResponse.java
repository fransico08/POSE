package com.pose.backend.conversation;

import java.time.Instant;
import java.util.UUID;

public record ConversationReplyResponse(UUID id, String direction, String content, String deliveryStatus,
                                        String externalMessageId, Instant occurredAt) {}
