package com.pose.backend.channel;

public interface ChannelReplyAdapter {
    String channelType();

    String send(String externalThreadId, String text);
}
