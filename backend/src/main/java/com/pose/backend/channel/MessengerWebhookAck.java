package com.pose.backend.channel;

public record MessengerWebhookAck(boolean ok, int accepted, int duplicates) {}
