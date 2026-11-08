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
    @Autowired public AiSettings(Environment env) { this(env,()->Optional.ofNullable(System.getenv("POSE_AI_API_KEY")).orElse("")); }
    AiSettings(Environment env,Supplier<String> apiKey) { this.env=env;this.apiKey=apiKey; }
    public boolean enabled() { return env.getProperty("pose.ai.enabled",Boolean.class,false); }
    public String provider() { return env.getProperty("pose.ai.provider","mock"); }
    public String apiKey() { return apiKey.get(); }
    public BigDecimal budget() { return env.getProperty("pose.ai.monthly-budget-usd",BigDecimal.class,new BigDecimal("5")); }
    public long timeout() { return env.getProperty("pose.ai.timeout-seconds",Long.class,30L); }
    public int retries() { return env.getProperty("pose.ai.max-retries",Integer.class,1); }
    public int maxTokens() { return env.getProperty("pose.ai.max-tokens",Integer.class,16000); }
    public int maxContextChars() { return env.getProperty("pose.ai.max-context-chars",Integer.class,12000); }
    public String model(AiUseCase useCase) { return env.getProperty("pose.ai.models."+useCase.property(),useCase==AiUseCase.SIGNAL_EXTRACT?"claude-haiku-5-5":"claude-sonnet-5-5"); }
    public String effort(AiUseCase useCase) { return env.getProperty("pose.ai.effort."+useCase.property(),useCase==AiUseCase.SIGNAL_EXTRACT?"low":"medium"); }
    public BigDecimal cost(AiUseCase useCase,long input,long output) {
        String family=model(useCase).startsWith("claude-haiku")?"haiku":"sonnet";
        BigDecimal in=env.getProperty("pose.ai.pricing."+family+"-input",BigDecimal.class,new BigDecimal(family.equals("haiku")?"0.10":"2.00"));
        BigDecimal out=env.getProperty("pose.ai.pricing."+family+"-output",BigDecimal.class,new BigDecimal(family.equals("haiku")?"0.50":"10.00"));
        return in.multiply(BigDecimal.valueOf(input)).add(out.multiply(BigDecimal.valueOf(output))).divide(BigDecimal.valueOf(1000000));
    }
}
