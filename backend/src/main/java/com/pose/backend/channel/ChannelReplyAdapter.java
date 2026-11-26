package com.pose.backend.channel;

public interface ChannelReplyAdapter {
    String channelType();

    String send(String externalThreadId, String text);

    default String send(String externalAccountId, String externalThreadId, String text) {
        return send(externalThreadId, text);
    }
}
