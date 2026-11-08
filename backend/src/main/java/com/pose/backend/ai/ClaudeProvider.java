package com.pose.backend.ai;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.LogLevel;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.beta.messages.*;
import org.springframework.stereotype.Component;
import java.net.SocketTimeoutException;
import java.time.Duration;

@Component
public class ClaudeProvider implements AiProvider {
    private final AiSettings settings;
    public ClaudeProvider(AiSettings settings) {this.settings=settings;}
    @Override public Reply generate(Request request) {return generate(request,null);}
    Reply generate(Request request,String baseUrl) {
        try {
            var clientBuilder=AnthropicOkHttpClient.builder().apiKey(settings.apiKey()).timeout(Duration.ofSeconds(settings.timeout())).maxRetries(settings.retries()).logLevel(LogLevel.OFF).openTelemetry(false);
            if(baseUrl!=null) clientBuilder.baseUrl(baseUrl);
            var client=clientBuilder.build();
            try {
            var output=BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.of(settings.effort(request.useCase())));
            if(request.jsonSchema()!=null) {
                var schema=BetaJsonOutputFormat.Schema.builder();
                request.jsonSchema().forEach((key,value)->schema.putAdditionalProperty(key,JsonValue.from(value)));
                output.format(BetaJsonOutputFormat.builder().schema(schema.build()).build());
            }
            var params=MessageCreateParams.builder().model(settings.model(request.useCase())).maxTokens(settings.maxTokens())
                    .system(request.prompt()).addUserMessage(request.input()).outputConfig(output.build());
            if(settings.model(request.useCase()).equals("claude-sonnet-5-5"))
                params.addBeta("server-side-fallback-2026-07-01").fallbacks(BetaFallbacksParam.ofDefault());
            var message=client.beta().messages().create(params.build());
            if(message.stopReason().filter(reason->reason.equals(BetaStopReason.REFUSAL)).isPresent()) return new Reply(null,message.usage().inputTokens(),message.usage().outputTokens(),"REFUSAL");
            String text=message.content().stream().flatMap(block->block.text().stream()).map(t->t.text()).reduce("",String::concat);
            return new Reply(text,message.usage().inputTokens(),message.usage().outputTokens(),null);
            } finally {client.close();}
        } catch(AnthropicServiceException ex) {
            int status=ex.statusCode(); return Reply.failed(status==429?"RATE_LIMIT":status>=500?"PROVIDER_ERROR":status==408?"TIMEOUT":"BAD_REQUEST");
        } catch(AnthropicIoException ex) {
            for(Throwable t=ex;t!=null;t=t.getCause()) if(t instanceof SocketTimeoutException || t instanceof java.net.http.HttpTimeoutException) return Reply.failed("TIMEOUT");
            return Reply.failed("NETWORK");
        } catch(RuntimeException ex) {return Reply.failed("PROVIDER_ERROR");}
    }
}
