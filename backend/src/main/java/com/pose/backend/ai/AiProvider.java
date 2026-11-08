package com.pose.backend.ai;

import java.util.Map;

public interface AiProvider {
    record Request(AiUseCase useCase,String prompt,String input,Map<String,Object> jsonSchema) {}
    record Reply(String text,long inputTokens,long outputTokens,String errorCode) {
        public static Reply failed(String code) { return new Reply(null,0,0,code); }
    }
    Reply generate(Request request);
}
