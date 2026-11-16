package com.pose.backend.channel;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ZaloSimReplyAdapter implements ChannelReplyAdapter {
    @Override
    public String channelType() {
        return "ZALO_SIM";
    }

    @Override
    public String send(String externalThreadId, String text) {
        return "zalo-sim-" + UUID.randomUUID();
    }
}
