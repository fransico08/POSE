package com.pose.backend.copilot;

import com.pose.backend.ai.AiManagementService;
import java.util.UUID;

public record CopilotResponse(
        String status,
        String errorCode,
        boolean reused,
        UUID invocationId,
        AiManagementService.Suggestion suggestion
) {}
