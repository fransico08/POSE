package com.pose.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pose.backend.api.ApiRequestException;
import com.pose.backend.api.JsonInput;
import com.pose.backend.audit.FoundationAuditRepository;
import com.pose.backend.identity.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;

@Service
public class CustomerSignalService {
    public record Signal(UUID id,String signalType,String value,Double confidence,String evidenceText,String source,String status,UUID messageId,UUID invocationId,String modelVersion,UUID reviewedBy,java.time.Instant reviewedAt,java.time.Instant createdAt) {}
    public record Extraction(UUID messageId,UUID invocationId,String status,String errorCode,int created) {}
    private static final Map<String,Object> SCHEMA=Map.of("type","object","additionalProperties",false,"properties",Map.of("signals",Map.of("type","array","items",Map.of("type","object","additionalProperties",false,"properties",Map.of("signalType",Map.of("type","string"),"value",Map.of("type","string"),"confidence",Map.of("type","number"),"evidenceText",Map.of("type","string")),"required",List.of("signalType","value","confidence","evidenceText")))),"required",List.of("signals"));
    private final JdbcTemplate jdbc; private final AiContextBuilder contexts; private final AiGateway gateway; private final AiSettings settings;
    private final FoundationAuditRepository audit; private final Clock clock; private final ObjectMapper json; private final SignalPersistence persistence;
    public CustomerSignalService(JdbcTemplate jdbc,AiContextBuilder contexts,AiGateway gateway,AiSettings settings,FoundationAuditRepository audit,Clock clock,ObjectMapper json,SignalPersistence persistence) {
        this.jdbc=jdbc;this.contexts=contexts;this.gateway=gateway;this.settings=settings;this.audit=audit;this.clock=clock;this.json=json;this.persistence=persistence;
    }
    private void scope(ActorContext actor,String type,UUID id,PermissionCode permission) {
        if(actor.hasRole(RoleCode.DATA_STAFF)||!actor.hasPermission(permission)||!actor.hasPermission(PermissionCode.CONVERSATION_READ)
                || ("CUSTOMER".equals(type) && !actor.hasPermission(PermissionCode.AI_ASSIST_USE)))FoundationAccess.denyPermission();
        contexts.build(actor,type,id,AiUseCase.SIGNAL_EXTRACT);
    }
    @Transactional(readOnly=true)
    public List<Signal> list(ActorContext actor,String type,UUID id) {
        scope(actor,type,id,PermissionCode.CONVERSATION_READ);
        String column=subjectColumn(type);
        return jdbc.query("SELECT s.* FROM customer_signals s JOIN conversation_messages m ON m.organization_id=s.organization_id AND m.id=s.message_id JOIN conversations c ON c.organization_id=m.organization_id AND c.id=m.conversation_id WHERE s.organization_id=? AND s."+column+"=? AND c."+column+"=s."+column+" ORDER BY s.created_at DESC,s.id",(rs,n)->map(rs),actor.organizationId(),id);
    }
    public List<Extraction> extract(ActorContext actor,String type,UUID id) {
        scope(actor,type,id,PermissionCode.AI_ASSIST_USE);
        String column=subjectColumn(type);
        var messages=jdbc.query("SELECT m.id,m.content FROM conversation_messages m JOIN conversations c ON c.organization_id=m.organization_id AND c.id=m.conversation_id WHERE c.organization_id=? AND c."+column+"=? AND m.direction='INBOUND' AND NOT EXISTS (SELECT 1 FROM customer_signals s WHERE s.organization_id=m.organization_id AND s.message_id=m.id AND s.source='AI') ORDER BY m.occurred_at,m.id",(rs,n)->Map.entry(rs.getObject("id",UUID.class),rs.getString("content")),actor.organizationId(),id);
        List<Extraction> results=new ArrayList<>();
        for(var message:messages) {
            var result=gateway.invoke(actor,AiUseCase.SIGNAL_EXTRACT,type,id,Map.of("jsonSchema",SCHEMA,"signalMessage",message.getValue()));
            int created=0; boolean storageFailed=false;
            if("SUCCEEDED".equals(result.status())) {
                try {
                    JsonNode items=json.readTree(result.signalOutput());
                    try {created=persistence.write(actor,type,id,message.getKey(),result.invocationId(),settings.model(AiUseCase.SIGNAL_EXTRACT),clock.instant(),items);}
                    catch(org.springframework.dao.DataIntegrityViolationException ex) {
                        try {
                            for(JsonNode item:items) {
                                Integer existing=jdbc.queryForObject("SELECT count(*) FROM customer_signals WHERE organization_id=? AND ai_message_id=? AND signal_type=?",Integer.class,actor.organizationId(),message.getKey(),item.path("signalType").asText());
                                if(existing==null || existing==0)storageFailed=true;
                            }
                        } catch(org.springframework.dao.DataAccessException ignored) {storageFailed=true;}
                    } catch(RuntimeException ex) {storageFailed=true;}
                } catch(com.fasterxml.jackson.core.JsonProcessingException ex) {storageFailed=true;}
            }
            results.add(new Extraction(message.getKey(),result.invocationId(),storageFailed?"FAILED":result.status(),storageFailed?"STORAGE_ERROR":result.errorCode(),created));
        }
        return List.copyOf(results);
    }
    @Transactional
    public Signal review(ActorContext actor,UUID id,String action,JsonNode body) {
        if(actor.hasRole(RoleCode.DATA_STAFF)||!actor.hasPermission(PermissionCode.AI_SIGNAL_REVIEW)||!actor.hasPermission(PermissionCode.CONVERSATION_READ))FoundationAccess.denyPermission();
        var rows=jdbc.query("SELECT * FROM customer_signals WHERE organization_id=? AND id=? FOR UPDATE",(rs,n)->map(rs),actor.organizationId(),id);
        if(rows.isEmpty()){FoundationAccess.denyScope();throw new IllegalStateException();}
        Signal before=rows.getFirst();
        var subjects=jdbc.queryForMap("SELECT lead_id,customer_id FROM customer_signals WHERE organization_id=? AND id=?",actor.organizationId(),id);
        UUID lead=(UUID)subjects.get("lead_id"), customer=(UUID)subjects.get("customer_id");
        if(customer!=null && !actor.hasPermission(PermissionCode.AI_ASSIST_USE))FoundationAccess.denyPermission();
        contexts.build(actor,lead!=null?"LEAD":"CUSTOMER",lead!=null?lead:customer,AiUseCase.SIGNAL_EXTRACT);
        String column=lead!=null?"lead_id":"customer_id";
        Integer linked=jdbc.queryForObject("SELECT count(*) FROM customer_signals s JOIN conversation_messages m ON m.organization_id=s.organization_id AND m.id=s.message_id JOIN conversations c ON c.organization_id=m.organization_id AND c.id=m.conversation_id WHERE s.organization_id=? AND s.id=? AND c."+column+"=s."+column,Integer.class,actor.organizationId(),id);
        if(linked==null||linked==0)FoundationAccess.denyScope();
        if(!"PROPOSED".equals(before.status()))throw ApiRequestException.conflict("AI_SIGNAL_REVIEWED");
        String value=before.value(); String status;
        if("edit".equals(action)) {
            JsonInput.of(body,"value"); JsonNode field=body==null?null:body.get("value");
            if(field==null||!field.isTextual()||!SignalOutputFilter.valid(before.signalType(),field.asText()))throw ApiRequestException.unprocessable("value","Giá trị tín hiệu không hợp lệ.");
            value=field.asText();status="EDITED";
        } else status="accept".equals(action)?"ACCEPTED":"REJECTED";
        var now=clock.instant();jdbc.update("UPDATE customer_signals SET \"value\"=?,status=?,reviewed_by_user_id=?,reviewed_at=? WHERE organization_id=? AND id=?",value,status,actor.userId(),Timestamp.from(now),actor.organizationId(),id);
        audit.record(actor,"AI_SIGNAL_"+status,"CUSTOMER_SIGNAL",id,null,Map.of("id",id),now);
        return jdbc.queryForObject("SELECT * FROM customer_signals WHERE organization_id=? AND id=?",(rs,n)->map(rs),actor.organizationId(),id);
    }
    private static String subjectColumn(String type) {if("CUSTOMER".equals(type))return "customer_id";if("LEAD".equals(type))return "lead_id";throw JsonInput.invalid("subjectType","Loại hồ sơ không hợp lệ.");}
    private static Signal map(java.sql.ResultSet r) throws java.sql.SQLException {
        Timestamp reviewed=r.getTimestamp("reviewed_at"); Object confidence=r.getObject("confidence");
        return new Signal(r.getObject("id",UUID.class),r.getString("signal_type"),r.getString("value"),confidence==null?null:((Number)confidence).doubleValue(),r.getString("evidence_text"),r.getString("source"),r.getString("status"),r.getObject("message_id",UUID.class),r.getObject("invocation_id",UUID.class),r.getString("model_version"),r.getObject("reviewed_by_user_id",UUID.class),reviewed==null?null:reviewed.toInstant(),r.getTimestamp("created_at").toInstant());
    }
}
