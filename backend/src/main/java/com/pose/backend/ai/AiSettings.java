package com.pose.backend.ai;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Supplier;

@Component
public class AiSettings {
    private final Environment env;
    private final Supplier<String> apiKey;
    private final Supplier<String> geminiKey;
    @Autowired public AiSettings(Environment env) { this(env,()->Optional.ofNullable(System.getenv("POSE_AI_API_KEY")).orElse(""),()->Optional.ofNullable(System.getenv("POSE_AI_GEMINI_API_KEY")).orElse("")); }
    AiSettings(Environment env,Supplier<String> apiKey) { this(env,apiKey,()->""); }
    AiSettings(Environment env,Supplier<String> apiKey,Supplier<String> geminiKey) { this.env=env;this.apiKey=apiKey;this.geminiKey=geminiKey; }
    public boolean enabled() { return env.getProperty("pose.ai.enabled",Boolean.class,false); }
    public String provider() { return env.getProperty("pose.ai.provider","mock"); }
    /** Claude key (POSE_AI_API_KEY). */
    public String apiKey() { return apiKey.get(); }
    /** Gemini key (POSE_AI_GEMINI_API_KEY), kept separate from the Claude key. */
    public String geminiApiKey() { return geminiKey.get(); }
    public boolean isGemini() { return "gemini".equals(provider()); }
    /** True when the selected provider needs a key and none is configured. */
    public boolean keyMissing() {
        String p=provider();
        return (p.equals("claude")&&apiKey().isBlank())||(p.equals("gemini")&&geminiApiKey().isBlank());
    }
    public String geminiBaseUrl() { return env.getProperty("pose.ai.gemini.base-url","https://generativelanguage.googleapis.com"); }
    public boolean geminiStructuredOutput() { return env.getProperty("pose.ai.gemini.structured-output",Boolean.class,true); }
    public BigDecimal budget() { return env.getProperty("pose.ai.monthly-budget-usd",BigDecimal.class,new BigDecimal("5")); }
    public long timeout() { return env.getProperty("pose.ai.timeout-seconds",Long.class,30L); }
    public int retries() { return env.getProperty("pose.ai.max-retries",Integer.class,1); }
    public int maxTokens() { return env.getProperty("pose.ai.max-tokens",Integer.class,16000); }
    public int maxContextChars() { return env.getProperty("pose.ai.max-context-chars",Integer.class,12000); }
    public int reuseMinutes() { return env.getProperty("pose.ai.copilot.reuse-minutes",Integer.class,10); }
    public String model(AiUseCase useCase) {
        if(isGemini()) return env.getProperty("pose.ai.gemini.models."+useCase.property(),"gemini-3.6-flash");
        return env.getProperty("pose.ai.models."+useCase.property(),useCase==AiUseCase.SIGNAL_EXTRACT?"claude-haiku-5-5":"claude-sonnet-5-5");
    }
    public String effort(AiUseCase useCase) { return env.getProperty("pose.ai.effort."+useCase.property(),useCase==AiUseCase.SIGNAL_EXTRACT?"low":"medium"); }
    public BigDecimal cost(AiUseCase useCase,long input,long output) {
        if(isGemini()) {
            BigDecimal in=env.getProperty("pose.ai.gemini.pricing.input",BigDecimal.class,BigDecimal.ZERO);
            BigDecimal out=env.getProperty("pose.ai.gemini.pricing.output",BigDecimal.class,BigDecimal.ZERO);
            return in.multiply(BigDecimal.valueOf(input)).add(out.multiply(BigDecimal.valueOf(output))).divide(BigDecimal.valueOf(1000000));
        }
        String family=model(useCase).startsWith("claude-haiku")?"haiku":"sonnet";
        BigDecimal in=env.getProperty("pose.ai.pricing."+family+"-input",BigDecimal.class,new BigDecimal(family.equals("haiku")?"0.10":"2.00"));
        BigDecimal out=env.getProperty("pose.ai.pricing."+family+"-output",BigDecimal.class,new BigDecimal(family.equals("haiku")?"0.50":"10.00"));
        return in.multiply(BigDecimal.valueOf(input)).add(out.multiply(BigDecimal.valueOf(output))).divide(BigDecimal.valueOf(1000000));
    }
}
