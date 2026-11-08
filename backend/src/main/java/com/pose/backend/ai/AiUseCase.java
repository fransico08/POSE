package com.pose.backend.ai;

import java.util.Locale;

public enum AiUseCase {
    C360_SUMMARY, HANDOVER_BRIEF, REPLY_DRAFT, SIGNAL_EXTRACT;
    public String property() { return name().toLowerCase(Locale.ROOT).replace('_','-'); }
}
