package com.pose.backend.ai;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class PromptRegistry {
    public String version(AiUseCase type) { return type.property()+(type==AiUseCase.HANDOVER_BRIEF || type==AiUseCase.SIGNAL_EXTRACT?".v3":type==AiUseCase.C360_SUMMARY?".v2":".v1"); }
    public String prompt(AiUseCase type) {
        try { return new ClassPathResource("ai/prompts/"+version(type)+".txt").getContentAsString(StandardCharsets.UTF_8); }
        catch(IOException ex) { throw new IllegalStateException("AI prompt unavailable",ex); }
    }
}
