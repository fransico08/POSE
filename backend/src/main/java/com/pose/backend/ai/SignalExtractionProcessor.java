package com.pose.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pose.backend.identity.ActorContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.util.*;

@Service
public class SignalExtractionProcessor {
    public record AutoResult(CustomerSignalService.Extraction extraction,String providerError) {}
    public static final Map<String,Object> SCHEMA=Map.of("type","object","additionalProperties",false,"properties",Map.of("signals",Map.of("type","array","items",Map.of("type","object","additionalProperties",false,"properties",Map.of("signalType",Map.of("type","string"),"value",Map.of("type","string"),"confidence",Map.of("type","number"),"evidenceText",Map.of("type","string")),"required",List.of("signalType","value","confidence","evidenceText")))),"required",List.of("signals"));
    private final AiGateway gateway; private final SignalPersistence persistence; private final AiSettings settings;
    private final JdbcTemplate jdbc; private final ObjectMapper json; private final Clock clock;
    public SignalExtractionProcessor(AiGateway gateway,SignalPersistence persistence,AiSettings settings,JdbcTemplate jdbc,ObjectMapper json,Clock clock) {
        this.gateway=gateway;this.persistence=persistence;this.settings=settings;this.jdbc=jdbc;this.json=json;this.clock=clock;
    }
    public CustomerSignalService.Extraction process(UUID org,ActorContext actor,String type,UUID subject,UUID messageId,String message) {
        return processInternal(org,actor,type,subject,messageId,message).extraction();
    }
    public AutoResult processForAuto(UUID org,String type,UUID subject,UUID messageId,String message) {
        return processInternal(org,null,type,subject,messageId,message);
    }
    private AutoResult processInternal(UUID org,ActorContext actor,String type,UUID subject,UUID messageId,String message) {
        var result=actor==null?gateway.invokeSystemSignal(org,type,subject,message,SCHEMA):gateway.invoke(actor,AiUseCase.SIGNAL_EXTRACT,type,subject,Map.of("jsonSchema",SCHEMA,"signalMessage",message));
        if("DISABLED".equals(result.status())||"BLOCKED_BUDGET".equals(result.status()))
            return new AutoResult(new CustomerSignalService.Extraction(messageId,result.invocationId(),result.status(),result.errorCode(),0),result.providerErrorCode());
        int created=0; boolean storageFailed=false;
        if("SUCCEEDED".equals(result.status())) {
            try {
                JsonNode items=json.readTree(result.signalOutput());
                try {created=persistence.write(org,type,subject,messageId,result.invocationId(),settings.model(AiUseCase.SIGNAL_EXTRACT),clock.instant(),items);}
                catch(org.springframework.dao.DataIntegrityViolationException ex) {
                    try {
                        Integer existing=jdbc.queryForObject("SELECT count(*) FROM customer_signal_extractions WHERE organization_id=? AND message_id=? AND status='SUCCEEDED'",Integer.class,org,messageId);
                        if(existing==null || existing==0)storageFailed=true;
                    } catch(org.springframework.dao.DataAccessException ignored) {storageFailed=true;}
                } catch(RuntimeException ex) {storageFailed=true;}
            } catch(com.fasterxml.jackson.core.JsonProcessingException ex) {storageFailed=true;}
        }
        String finalStatus=storageFailed?"FAILED":result.status(), finalError=storageFailed?"STORAGE_ERROR":result.errorCode();
        if(!"SUCCEEDED".equals(finalStatus)) {
            try {persistence.recordAttempt(org,messageId,result.invocationId(),finalStatus,finalError,clock.instant());}
            catch(RuntimeException ex) {finalStatus="FAILED";finalError="STORAGE_ERROR";}
        }
        return new AutoResult(new CustomerSignalService.Extraction(messageId,result.invocationId(),finalStatus,finalError,created),result.providerErrorCode());
    }
}
