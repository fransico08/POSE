package com.pose.backend.channel;

import java.util.UUID;

public record ChannelWebhookAck(boolean ok, boolean duplicate, UUID conversationId) {}
