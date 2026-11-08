package com.pose.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.api.*;
import com.pose.backend.audit.FoundationAuditRepository;
import com.pose.backend.identity.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class AiManagementService {
    public record Suggestion(UUID id,UUID invocationId,String kind,String subjectType,UUID subjectId,String content,String evidence,String status,String finalContent,UUID reviewedBy,Instant reviewedAt,Instant createdAt) {}
    public record MetricCount(String useCase,String status,long count) {}
    private final AiSettings settings; private final BudgetGuard budget; private final AiContextBuilder contexts;
    private final JdbcTemplate jdbc; private final FoundationAuditRepository audit; private final Clock clock;
    public AiManagementService(AiSettings settings,BudgetGuard budget,AiContextBuilder contexts,JdbcTemplate jdbc,FoundationAuditRepository audit,Clock clock) {
        this.settings=settings;this.budget=budget;this.contexts=contexts;this.jdbc=jdbc;this.audit=audit;this.clock=clock;
    }
    @Transactional(readOnly=true)
    public Map<String,Object> status(ActorContext actor) {
        require(actor,PermissionCode.AI_ASSIST_USE);Map<String,Object> result=new LinkedHashMap<>();
        result.put("enabled",settings.enabled()&&(!settings.provider().equals("claude")||!settings.apiKey().isBlank()));
        if(actor.hasPermission(PermissionCode.AI_ADMIN)){result.put("provider",settings.provider());result.put("remainingBudgetUsd",budget.remaining(actor.organizationId()));}
        return result;
    }
    @Transactional(readOnly=true)
    public List<Suggestion> list(ActorContext actor,String type,UUID subject) {
        require(actor,PermissionCode.AI_ASSIST_USE);contexts.build(actor,type,subject);
        return jdbc.query("SELECT s.* FROM ai_suggestions s JOIN ai_invocations i ON i.id=s.invocation_id AND i.organization_id=s.organization_id WHERE s.organization_id=? AND s.subject_type=? AND s.subject_id=? AND i.actor_user_id=? ORDER BY s.created_at DESC,s.id",(rs,n)->map(rs),actor.organizationId(),type,subject,actor.userId());
    }
    @Transactional
    public Suggestion review(ActorContext actor,UUID id,JsonNode body,boolean accept) {
        require(actor,PermissionCode.AI_ASSIST_USE);
        var rows=jdbc.query("SELECT s.* FROM ai_suggestions s WHERE s.organization_id=? AND s.id=? AND EXISTS (SELECT 1 FROM ai_invocations i WHERE i.id=s.invocation_id AND i.organization_id=s.organization_id AND i.actor_user_id=?) FOR UPDATE",(rs,n)->map(rs),actor.organizationId(),id,actor.userId());
        if(rows.isEmpty()) {FoundationAccess.denyScope();throw new IllegalStateException();}
        Suggestion before=rows.getFirst();contexts.build(actor,before.subjectType(),before.subjectId());
        if(!before.status().equals("PROPOSED")) throw ApiRequestException.conflict("AI_SUGGESTION_REVIEWED");
        String content=null;
        if(body!=null&&!body.isNull()) {
            JsonInput input=JsonInput.of(body,accept?new String[]{"finalContent"}:new String[]{});
            if(input.has("finalContent")) {JsonNode field=body.get("finalContent");if(!field.isTextual()||field.asText().isBlank()||field.asText().length()>12000)throw JsonInput.invalid("finalContent","Nội dung không hợp lệ.");content=field.asText();}
        }
        String status=accept?(content==null?"ACCEPTED":"EDITED"):"REJECTED";Instant now=clock.instant();
        jdbc.update("UPDATE ai_suggestions SET status=?,final_content=?,reviewed_by=?,reviewed_at=? WHERE organization_id=? AND id=?",status,content,actor.userId(),Timestamp.from(now),actor.organizationId(),id);
        audit.record(actor,"AI_SUGGESTION_"+status,"AI_SUGGESTION",id,null,Map.of("id",id),now);
        return jdbc.queryForObject("SELECT * FROM ai_suggestions WHERE organization_id=? AND id=?",(rs,n)->map(rs),actor.organizationId(),id);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> metrics(ActorContext actor,Instant start,Instant end) {
        require(actor,PermissionCode.AI_METRICS_READ);
        if(start==null||end==null||!start.isBefore(end)) throw JsonInput.invalid("start","Khoảng thời gian không hợp lệ.");
        Object[] args={actor.organizationId(),Timestamp.from(start),Timestamp.from(end)};
        var counts=jdbc.query("SELECT use_case,status,count(*) AS total FROM ai_invocations WHERE organization_id=? AND created_at>=? AND created_at<? GROUP BY use_case,status ORDER BY use_case,status",(rs,n)->new MetricCount(rs.getString("use_case"),rs.getString("status"),rs.getLong("total")),args);
        var totals=jdbc.queryForMap("SELECT COALESCE(sum(input_tokens),0) AS input_tokens,COALESCE(sum(output_tokens),0) AS output_tokens,COALESCE(sum(cost_estimate),0) AS cost_usd FROM ai_invocations WHERE organization_id=? AND created_at>=? AND created_at<?",args);
        var latency=jdbc.queryForList("SELECT latency_ms FROM ai_invocations WHERE organization_id=? AND created_at>=? AND created_at<? AND latency_ms IS NOT NULL ORDER BY latency_ms",Long.class,args);
        var reviewed=jdbc.queryForMap("SELECT count(*) AS reviewed, count(CASE WHEN status IN ('ACCEPTED','EDITED') THEN 1 END) AS accepted FROM ai_suggestions WHERE organization_id=? AND reviewed_at>=? AND reviewed_at<? AND status IN ('ACCEPTED','EDITED','REJECTED')",args);
        long n=((Number)reviewed.get("reviewed")).longValue(),a=((Number)reviewed.get("accepted")).longValue();
        Map<String,Object> result=new LinkedHashMap<>();result.put("start",start);result.put("end",end);result.put("counts",counts);result.put("inputTokens",totals.get("input_tokens"));result.put("outputTokens",totals.get("output_tokens"));result.put("costEstimateUsd",totals.get("cost_usd"));result.put("acceptanceRate",n==0?null:(double)a/n);result.put("latencyP50Ms",percentile(latency,.50));result.put("latencyP95Ms",percentile(latency,.95));return result;
    }
    private static Long percentile(List<Long> sorted,double p) {return sorted.isEmpty()?null:sorted.get(Math.max(0,(int)Math.ceil(sorted.size()*p)-1));}
    private static void require(ActorContext actor,PermissionCode p){
        if(!actor.hasPermission(p) || (p==PermissionCode.AI_ASSIST_USE && actor.hasRole(RoleCode.DATA_STAFF))) FoundationAccess.denyPermission();
    }
    private static Suggestion map(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp reviewed=rs.getTimestamp("reviewed_at");return new Suggestion(rs.getObject("id",UUID.class),rs.getObject("invocation_id",UUID.class),rs.getString("kind"),rs.getString("subject_type"),rs.getObject("subject_id",UUID.class),rs.getString("content"),normalizeJson(rs.getString("evidence")),rs.getString("status"),rs.getString("final_content"),rs.getObject("reviewed_by",UUID.class),reviewed==null?null:reviewed.toInstant(),rs.getTimestamp("created_at").toInstant());
    }
    public static String normalizeJson(String raw) {
        if(raw==null||raw.isBlank()) return null;
        String trimmed=raw.strip();
        if(trimmed.startsWith("\"")&&trimmed.endsWith("\"")) {
            try { return new com.fasterxml.jackson.databind.ObjectMapper().readValue(trimmed,String.class); }
            catch(Exception ignored) {}
        }
        return raw;
    }
}
