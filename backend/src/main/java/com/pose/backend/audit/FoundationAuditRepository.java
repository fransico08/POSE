package com.pose.backend.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pose.backend.identity.ActorContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class FoundationAuditRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public FoundationAuditRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    public void record(ActorContext actor, String action, String type, UUID entityId, Object before, Object after, Instant now) {
        jdbc.update("""
                INSERT INTO audit_logs(id,organization_id,actor_user_id,actor_type,actor_system_key,
                    action,entity_type,entity_id,before_data,after_data,safe_metadata,result,occurred_at)
                VALUES (?,?,?,'USER',NULL,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),CAST('{}' AS jsonb),'SUCCESS',?)
                """, UUID.randomUUID(), actor.organizationId(), actor.userId(), action, type, entityId,
                serialize(before), serialize(after), Timestamp.from(now));
    }
    public void recordSystem(UUID organizationId, String actorKey, String action, String type,
                             UUID entityId, Object before, Object after, Instant now) {
        jdbc.update("""
                INSERT INTO audit_logs(id,organization_id,actor_user_id,actor_type,actor_system_key,
                    action,entity_type,entity_id,before_data,after_data,safe_metadata,result,occurred_at)
                VALUES (?,?,NULL,'SYSTEM',?,?,?, ?,CAST(? AS jsonb),CAST(? AS jsonb),CAST('{}' AS jsonb),'SUCCESS',?)
                """, UUID.randomUUID(), organizationId, actorKey, action, type, entityId,
                serialize(before), serialize(after), Timestamp.from(now));
    }
    private String serialize(Object value) {
        try { return json.writeValueAsString(value == null ? Map.of() : value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Cannot serialize foundation audit", ex); }
    }
}
