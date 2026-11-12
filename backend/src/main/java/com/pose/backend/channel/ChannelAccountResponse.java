package com.pose.backend.channel;

import java.time.Instant;
import java.util.UUID;

public record ChannelAccountResponse(
        UUID id,
        String channelType,
        String displayName,
        String externalAccountId,
        String status,
        String secretRef,
        Instant createdAt,
        Instant updatedAt) {}
