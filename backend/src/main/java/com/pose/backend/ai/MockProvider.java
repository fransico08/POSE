package com.pose.backend.ai;

import org.springframework.stereotype.Component;

@Component
public class MockProvider implements AiProvider {
    private volatile String lastInput;
    public String lastInput() { return lastInput; }
    @Override public Reply generate(Request request) {
        lastInput=request.input();
        return new Reply("Gợi ý thử nghiệm: "+request.input(),request.input().length()/4L+1,20,null);
    }
}
