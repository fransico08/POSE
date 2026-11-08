package com.pose.backend.ai;

import com.pose.backend.identity.ActorContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Service
public class AiInvocationWriter {
    private final JdbcTemplate jdbc;
    public AiInvocationWriter(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public UUID write(UUID id,ActorContext actor,AiUseCase useCase,String subjectType,UUID subjectId,String provider,String model,String version,String hash,int redactions,
                      Long inputTokens,Long outputTokens,BigDecimal cost,long latency,String status,String error,String content,Instant now) {
        return write(id,actor,useCase,subjectType,subjectId,provider,model,version,hash,redactions,inputTokens,outputTokens,cost,latency,status,error,content,null,now);
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public UUID write(UUID id,ActorContext actor,AiUseCase useCase,String subjectType,UUID subjectId,String provider,String model,String version,String hash,int redactions,
                      Long inputTokens,Long outputTokens,BigDecimal cost,long latency,String status,String error,String content,String evidence,Instant now) {
        jdbc.update("""
                INSERT INTO ai_invocations(id,organization_id,actor_user_id,use_case,subject_type,subject_id,provider,model,prompt_version,input_hash,redaction_count,input_tokens,output_tokens,cost_estimate,latency_ms,status,error_code,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,id,actor.organizationId(),actor.userId(),useCase.name(),subjectType,subjectId,provider,model,version,hash,redactions,inputTokens,outputTokens,cost,latency,status,error,Timestamp.from(now));
        if(content==null)return null;
        UUID suggestion=UUID.randomUUID();
        if(evidence==null) {
            jdbc.update("""
                    INSERT INTO ai_suggestions(id,organization_id,invocation_id,kind,subject_type,subject_id,content,status,created_at)
                    VALUES (?,?,?,?,?,?,?,'PROPOSED',?)
                    """,suggestion,actor.organizationId(),id,useCase.name(),subjectType,subjectId,content,Timestamp.from(now));
        } else {
            jdbc.update("""
                    INSERT INTO ai_suggestions(id,organization_id,invocation_id,kind,subject_type,subject_id,content,evidence,status,created_at)
                    VALUES (?,?,?,?,?,?,?,CAST(? AS jsonb),'PROPOSED',?)
                    """,suggestion,actor.organizationId(),id,useCase.name(),subjectType,subjectId,content,evidence,Timestamp.from(now));
        }
        return suggestion;
    }
}
