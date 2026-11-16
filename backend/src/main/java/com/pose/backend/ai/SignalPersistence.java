package com.pose.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Service
public class SignalPersistence {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    public SignalPersistence(JdbcTemplate jdbc,PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.transactions=new TransactionTemplate(manager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public int write(UUID organizationId,String type,UUID subjectId,UUID messageId,UUID invocationId,String model,Instant now,JsonNode items) {
        Integer count=transactions.execute(status->{
            String subjectColumn="LEAD".equals(type)?"lead_id":"CUSTOMER".equals(type)?"customer_id":null;
            if(subjectColumn==null)throw new IllegalArgumentException("Invalid signal subject type");
            var linked=jdbc.query("SELECT c.id FROM conversations c JOIN conversation_messages m ON m.organization_id=c.organization_id AND m.conversation_id=c.id WHERE c.organization_id=? AND c."+subjectColumn+"=? AND m.id=? AND m.direction='INBOUND' FOR UPDATE",(r,n)->r.getObject(1,UUID.class),organizationId,subjectId,messageId);
            if(linked.isEmpty())throw new IllegalStateException("Signal message is no longer linked to subject");
            int created=0;
            for(JsonNode item:items) {
                jdbc.update("INSERT INTO customer_signals(id,organization_id,lead_id,customer_id,message_id,signal_type,\"value\",confidence,evidence_text,source,ai_message_id,invocation_id,status,model_version,created_at) VALUES (?,?,?,?,?,?,?,?,?,'AI',?,?,'PROPOSED',?,?)",
                        UUID.randomUUID(),organizationId,"LEAD".equals(type)?subjectId:null,"CUSTOMER".equals(type)?subjectId:null,messageId,item.path("signalType").asText(),item.path("value").asText(),item.path("confidence").asDouble(),item.path("evidenceText").asText(),messageId,invocationId,model,Timestamp.from(now));
                created++;
            }
            jdbc.update("INSERT INTO customer_signal_extractions(id,organization_id,message_id,invocation_id,status,error_code,success_message_id,created_at) VALUES (?,?,?,?,'SUCCEEDED',NULL,?,?)",UUID.randomUUID(),organizationId,messageId,invocationId,messageId,Timestamp.from(now));
            return created;
        });
        return count==null?0:count;
    }
    public void recordAttempt(UUID organizationId,UUID messageId,UUID invocationId,String status,String error,Instant now) {
        transactions.executeWithoutResult(ignored->jdbc.update("INSERT INTO customer_signal_extractions(id,organization_id,message_id,invocation_id,status,error_code,success_message_id,created_at) VALUES (?,?,?,(SELECT id FROM ai_invocations WHERE organization_id=? AND id=?),?,?,NULL,?)",UUID.randomUUID(),organizationId,messageId,organizationId,invocationId,status,error,Timestamp.from(now)));
    }
}
