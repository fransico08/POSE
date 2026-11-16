package com.pose.backend.conversation;

import java.time.Instant;
import java.util.UUID;

public record ConversationMessageResponse(UUID id, String direction, String contentType, String content,
                                          UUID sentByUserId, String deliveryStatus,
                                          Instant occurredAt, Instant createdAt) {}
