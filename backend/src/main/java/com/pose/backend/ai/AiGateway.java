package com.pose.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.Duration;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;

@Service
public class AiGateway {
    public record Result(UUID invocationId,String status,String errorCode,UUID suggestionId,boolean reused,String signalOutput) {
        public Result(UUID invocationId,String status,String errorCode,UUID suggestionId,boolean reused) {this(invocationId,status,errorCode,suggestionId,reused,null);}
    }
    private record FilteredOutput(String content,String evidence) {}
    private static final Logger LOG=LoggerFactory.getLogger(AiGateway.class);
    private static final ObjectMapper JSON=new ObjectMapper();
    private final AiSettings settings; private final AiContextBuilder contexts; private final Redactor redactor;
    private final BudgetGuard budget; private final MockProvider mock; private final ClaudeProvider claude; private final GeminiProvider gemini;
    private final PromptRegistry prompts; private final AiInvocationWriter writer; private final Clock clock; private final JdbcTemplate jdbc;
    private final AtomicBoolean warned=new AtomicBoolean();
    public AiGateway(AiSettings settings,AiContextBuilder contexts,Redactor redactor,BudgetGuard budget,MockProvider mock,
                     ClaudeProvider claude,PromptRegistry prompts,AiInvocationWriter writer,Clock clock,JdbcTemplate jdbc) {
        this(settings,contexts,redactor,budget,mock,claude,new GeminiProvider(settings),prompts,writer,clock,jdbc);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public AiGateway(AiSettings settings,AiContextBuilder contexts,Redactor redactor,BudgetGuard budget,MockProvider mock,
                     ClaudeProvider claude,GeminiProvider gemini,PromptRegistry prompts,AiInvocationWriter writer,Clock clock,JdbcTemplate jdbc) {
        this.gemini=gemini;
        this.settings=settings;this.contexts=contexts;this.redactor=redactor;this.budget=budget;this.mock=mock;
        this.claude=claude;this.prompts=prompts;this.writer=writer;this.clock=clock;this.jdbc=jdbc;
    }
    public Result invoke(ActorContext actor,AiUseCase useCase,String subjectType,UUID subjectId,Map<String,Object> options) {
        UUID id=UUID.randomUUID();long start=System.nanoTime();String provider=settings.provider();
        if(!settings.enabled() || settings.keyMissing()) {
            if(settings.enabled() && warned.compareAndSet(false,true)) LOG.warn("AI provider {} is disabled because its API key is missing",provider);
            return save(id,actor,useCase,subjectType,subjectId,provider,null,0,null,"DISABLED",null,null,start);
        }
        if(!actor.hasPermission(PermissionCode.AI_ASSIST_USE) || actor.hasRole(RoleCode.DATA_STAFF)) FoundationAccess.denyPermission();
        String context;
        try {
            context=contexts.build(actor,subjectType,subjectId,useCase);
            if(useCase==AiUseCase.SIGNAL_EXTRACT) {
                Object message=options==null?null:options.get("signalMessage");
                if(!(message instanceof String text) || text.isBlank()) throw new IllegalArgumentException("Missing signal message");
                context=JSON.writeValueAsString(Map.of("message",text));
            }
        }
        catch(AuthorizationDeniedException | ApiRequestException ex) {throw ex;}
        catch(Exception ex) {return save(id,actor,useCase,subjectType,subjectId,provider,null,0,null,"FAILED","CONTEXT_ERROR",null,start);}
        Redactor.Result masked;
        try { masked=redactor.redactContext(context); }
        catch(RuntimeException ex) {return save(id,actor,useCase,subjectType,subjectId,provider,null,0,null,"FAILED","CONTEXT_ERROR",null,start);}
        String input=masked.text();int max=Math.max(0,settings.maxContextChars());
        if(input.length()>max) input=AiContextBuilder.truncateJsonElementWise(JSON,input,max);
        if(options!=null && Boolean.TRUE.equals(options.get("reuse")) && settings.reuseMinutes()>0) {
            try {
            var existing=jdbc.query("""
                    SELECT i.id AS invocation_id,s.id AS suggestion_id
                    FROM ai_suggestions s JOIN ai_invocations i ON i.id=s.invocation_id AND i.organization_id=s.organization_id
                    WHERE s.organization_id=? AND i.actor_user_id=? AND s.subject_type=? AND s.subject_id=?
                      AND s.kind=? AND s.status='PROPOSED' AND i.status='SUCCEEDED' AND i.input_hash=?
                      AND s.created_at>=? AND s.created_at<=?
                    ORDER BY s.created_at DESC,s.id LIMIT 1
                    """,(rs,n)->new Result(rs.getObject("invocation_id",UUID.class),"SUCCEEDED",null,rs.getObject("suggestion_id",UUID.class),true),
                    actor.organizationId(),actor.userId(),subjectType,subjectId,useCase.name(),hash(input),
                    Timestamp.from(clock.instant().minus(Duration.ofMinutes(settings.reuseMinutes()))),Timestamp.from(clock.instant()));
            if(!existing.isEmpty()) return existing.getFirst();
            } catch(RuntimeException ex) {return save(id,actor,useCase,subjectType,subjectId,provider,input,masked.count(),null,"FAILED","STORAGE_ERROR",null,start);}
        }
        try {if(budget.blocked(actor.organizationId())) return save(id,actor,useCase,subjectType,subjectId,provider,input,masked.count(),null,"BLOCKED_BUDGET",null,null,start);}
        catch(RuntimeException ex) {return save(id,actor,useCase,subjectType,subjectId,provider,input,masked.count(),null,"FAILED","BUDGET_ERROR",null,start);}
        @SuppressWarnings("unchecked") Map<String,Object> schema=options==null?null:(Map<String,Object>)options.get("jsonSchema");
        AiProvider.Reply reply;
        try {
            AiProvider selected=provider.equals("mock")?mock:provider.equals("claude")?claude:provider.equals("gemini")?gemini:null;
            reply=selected==null?AiProvider.Reply.failed("BAD_REQUEST"):selected.generate(new AiProvider.Request(useCase,prompts.prompt(useCase),input,schema));
        } catch(RuntimeException ex) {reply=AiProvider.Reply.failed("PROVIDER_ERROR");}
        String status=reply.errorCode()==null?"SUCCEEDED":"FAILED";
        String error=reply.errorCode();
        FilteredOutput filtered=null;
        if(status.equals("SUCCEEDED") && schema!=null) {
            if(useCase==AiUseCase.SIGNAL_EXTRACT) {
                try {
                    String message=JSON.readTree(input).path("message").asText();
                    var signals=new SignalOutputFilter().filter(reply.text(),message);
                    if(!signals.isEmpty())filtered=new FilteredOutput(JSON.writeValueAsString(signals),null);
                } catch(Exception ignored) {filtered=null;}
            } else {
                Set<String> validSources=AiContextBuilder.extractValidSources(JSON,input);
                filtered=filterStructuredOutput(reply.text(),validSources);
            }
            if(filtered==null) {
                status="FAILED";
                error="INVALID_OUTPUT";
            }
        }
        return save(id,actor,useCase,subjectType,subjectId,provider,input,masked.count(),reply,status,error,filtered,start);
    }
    private FilteredOutput filterStructuredOutput(String rawText,Set<String> validSources) {
        if(rawText==null || rawText.isBlank()) return null;
        try {
            JsonNode root=JSON.readTree(rawText);
            if(root==null || !root.isObject()) return null;
            JsonNode sectionsNode=root.get("sections");
            if(sectionsNode==null || !sectionsNode.isArray()) return null;
            List<Map<String,Object>> keptSections=new ArrayList<>();
            Set<String> usedSources=new LinkedHashSet<>();
            int droppedPoints=0;
            for(JsonNode secNode:sectionsNode) {
                if(secNode==null || !secNode.isObject()) return null;
                JsonNode headingNode=secNode.get("heading");
                JsonNode pointsNode=secNode.get("points");
                if(headingNode==null || !headingNode.isTextual() || headingNode.asText().isBlank()
                        || pointsNode==null || !pointsNode.isArray()) return null;
                String heading=redactor.redact(headingNode.asText().strip()).text();
                List<Map<String,Object>> keptPoints=new ArrayList<>();
                for(JsonNode ptNode:pointsNode) {
                    if(ptNode==null || !ptNode.isObject()) return null;
                    JsonNode textNode=ptNode.get("text");
                    JsonNode srcNode=ptNode.get("sources");
                    if(textNode==null || !textNode.isTextual() || srcNode==null || !srcNode.isArray()) return null;
                    String text=redactor.redact(textNode.asText().strip()).text();
                    List<String> ptSources=new ArrayList<>();
                    for(JsonNode s:srcNode) {
                        if(!s.isTextual()) return null;
                        String code=s.asText().strip();
                        if(!code.isEmpty() && validSources.contains(code) && !ptSources.contains(code)) {
                            ptSources.add(code);
                        }
                    }
                    if(text.isBlank() || ptSources.isEmpty()) {
                        droppedPoints++;
                    } else {
                        Map<String,Object> pt=new LinkedHashMap<>();
                        pt.put("text",text);
                        pt.put("sources",ptSources);
                        keptPoints.add(pt);
                        usedSources.addAll(ptSources);
                    }
                }
                if(!keptPoints.isEmpty()) {
                    Map<String,Object> sec=new LinkedHashMap<>();
                    sec.put("heading",heading);
                    sec.put("points",keptPoints);
                    keptSections.add(sec);
                }
            }
            if(keptSections.isEmpty()) return null;
            String contentJson=JSON.writeValueAsString(Map.of("sections",keptSections));
            Map<String,Object> ev=new LinkedHashMap<>();
            ev.put("sources",new ArrayList<>(usedSources));
            ev.put("droppedPoints",droppedPoints);
            String evidenceJson=JSON.writeValueAsString(ev);
            return new FilteredOutput(contentJson,evidenceJson);
        } catch(Exception ex) {
            return null;
        }
    }
    private Result save(UUID id,ActorContext actor,AiUseCase type,String subjectType,UUID subjectId,String provider,String input,int count,AiProvider.Reply reply,String status,String error,FilteredOutput filtered,long start) {
        long latency=(System.nanoTime()-start)/1000000; boolean success=status.equals("SUCCEEDED");
        String content=success && type!=AiUseCase.SIGNAL_EXTRACT?(filtered!=null?filtered.content():redactor.redact(reply.text()).text()):null;
        String evidence=success && filtered!=null?filtered.evidence():null;
        UUID suggestion;
        try {
            if(evidence==null) {
                suggestion=writer.write(id,actor,type,subjectType,subjectId,provider,settings.model(type),prompts.version(type),input==null?null:hash(input),count,
                        reply==null?null:reply.inputTokens(),reply==null?null:reply.outputTokens(),reply==null?null:settings.cost(type,reply.inputTokens(),reply.outputTokens()),
                        latency,status,error,content,clock.instant());
            } else {
                suggestion=writer.write(id,actor,type,subjectType,subjectId,provider,settings.model(type),prompts.version(type),input==null?null:hash(input),count,
                        reply==null?null:reply.inputTokens(),reply==null?null:reply.outputTokens(),reply==null?null:settings.cost(type,reply.inputTokens(),reply.outputTokens()),
                        latency,status,error,content,evidence,clock.instant());
            }
        }
        catch(RuntimeException ex) {LOG.error("AI invocation={} useCase={} status=FAILED latencyMs={}",id,type,latency);return new Result(id,"FAILED","STORAGE_ERROR",null,false);}
        if(error==null) LOG.info("AI invocation={} useCase={} status={} latencyMs={}",id,type,status,latency);
        else LOG.warn("AI invocation={} useCase={} provider={} status={} errorCode={} latencyMs={}",id,type,provider,status,error,latency);
        return new Result(id,status,error,suggestion,false,success && type==AiUseCase.SIGNAL_EXTRACT && filtered!=null?filtered.content():null);
    }
    private static String hash(String value) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception ex){throw new IllegalStateException("SHA-256 unavailable",ex);}
    }
}
