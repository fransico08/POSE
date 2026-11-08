package com.pose.backend.ai;

import com.pose.backend.api.ApiRequestException;
import com.pose.backend.identity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AiGateway {
    public record Result(UUID invocationId,String status,String errorCode,UUID suggestionId) {}
    private static final Logger LOG=LoggerFactory.getLogger(AiGateway.class);
    private final AiSettings settings; private final AiContextBuilder contexts; private final Redactor redactor;
    private final BudgetGuard budget; private final MockProvider mock; private final ClaudeProvider claude;
    private final PromptRegistry prompts; private final AiInvocationWriter writer; private final Clock clock;
    private final AtomicBoolean warned=new AtomicBoolean();
    public AiGateway(AiSettings settings,AiContextBuilder contexts,Redactor redactor,BudgetGuard budget,MockProvider mock,
                     ClaudeProvider claude,PromptRegistry prompts,AiInvocationWriter writer,Clock clock) {
        this.settings=settings;this.contexts=contexts;this.redactor=redactor;this.budget=budget;this.mock=mock;
        this.claude=claude;this.prompts=prompts;this.writer=writer;this.clock=clock;
    }
    public Result invoke(ActorContext actor,AiUseCase useCase,String subjectType,UUID subjectId,Map<String,Object> options) {
        UUID id=UUID.randomUUID();long start=System.nanoTime();String provider=settings.provider();
        if(!settings.enabled() || (provider.equals("claude") && settings.apiKey().isBlank())) {
            if(settings.enabled() && provider.equals("claude") && warned.compareAndSet(false,true)) LOG.warn("Claude provider is disabled because its API key is missing");
            return save(id,actor,useCase,subjectType,subjectId,provider,null,0,null,"DISABLED",null,start);
        }
        if(!actor.hasPermission(PermissionCode.AI_ASSIST_USE)) FoundationAccess.denyPermission();
        String context;
        try {context=contexts.build(actor,subjectType,subjectId,useCase);}
        catch(AuthorizationDeniedException | ApiRequestException ex) {throw ex;}
        catch(RuntimeException ex) {return save(id,actor,useCase,subjectType,subjectId,provider,null,0,null,"FAILED","CONTEXT_ERROR",start);}
        var masked=redactor.redact(context);String input=masked.text();int max=Math.max(0,settings.maxContextChars());
        if(input.length()>max) input=input.substring(0,max);
        try {if(budget.blocked(actor.organizationId())) return save(id,actor,useCase,subjectType,subjectId,provider,input,masked.count(),null,"BLOCKED_BUDGET",null,start);}
        catch(RuntimeException ex) {return save(id,actor,useCase,subjectType,subjectId,provider,input,masked.count(),null,"FAILED","BUDGET_ERROR",start);}
        AiProvider.Reply reply;
        try {
            @SuppressWarnings("unchecked") Map<String,Object> schema=options==null?null:(Map<String,Object>)options.get("jsonSchema");
            AiProvider selected=provider.equals("mock")?mock:provider.equals("claude")?claude:null;
            reply=selected==null?AiProvider.Reply.failed("BAD_REQUEST"):selected.generate(new AiProvider.Request(useCase,prompts.prompt(useCase),input,schema));
        } catch(RuntimeException ex) {reply=AiProvider.Reply.failed("PROVIDER_ERROR");}
        String status=reply.errorCode()==null?"SUCCEEDED":"FAILED";
        return save(id,actor,useCase,subjectType,subjectId,provider,input,masked.count(),reply,status,reply.errorCode(),start);
    }
    private Result save(UUID id,ActorContext actor,AiUseCase type,String subjectType,UUID subjectId,String provider,String input,int count,AiProvider.Reply reply,String status,String error,long start) {
        long latency=(System.nanoTime()-start)/1000000; boolean success=status.equals("SUCCEEDED");
        String content=success?redactor.redact(reply.text()).text():null;
        UUID suggestion;
        try {suggestion=writer.write(id,actor,type,subjectType,subjectId,provider,settings.model(type),prompts.version(type),input==null?null:hash(input),count,
                reply==null?null:reply.inputTokens(),reply==null?null:reply.outputTokens(),reply==null?null:settings.cost(type,reply.inputTokens(),reply.outputTokens()),
                latency,status,error,content,clock.instant());}
        catch(RuntimeException ex) {LOG.error("AI invocation={} useCase={} status=FAILED latencyMs={}",id,type,latency);return new Result(id,"FAILED","STORAGE_ERROR",null);}
        LOG.info("AI invocation={} useCase={} status={} latencyMs={}",id,type,status,latency);
        return new Result(id,status,error,suggestion);
    }
    private static String hash(String value) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception ex){throw new IllegalStateException("SHA-256 unavailable",ex);}
    }
}
