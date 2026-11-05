package com.pose.backend.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pose.backend.identity.ActorContext;
import com.pose.backend.identity.UserResponse;
import com.pose.backend.team.TeamResponse;
import com.pose.backend.offering.OfferingResponse;
import com.pose.backend.salesassignment.SalesAssignmentResponse;
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
        try {
        List<String> changedFields = changedFields(action, before, after);
        jdbc.update("""
                INSERT INTO audit_logs(id,organization_id,actor_user_id,actor_type,actor_system_key,
                    action,entity_type,entity_id,before_data,after_data,safe_metadata,result,occurred_at)
                VALUES (?,?,?,'USER',NULL,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),CAST('{}' AS jsonb),'SUCCESS',?)
                """, UUID.randomUUID(), actor.organizationId(), actor.userId(), action, type, entityId,
                serialize(snapshot(before, changedFields)), serialize(snapshot(after, changedFields)), Timestamp.from(now));
        } catch (RuntimeException ex) { throw new AuditWriteException(ex); }
    }
    public void recordSystem(UUID organizationId, String actorKey, String action, String type,
                             UUID entityId, Object before, Object after, Instant now) {
        try {
        jdbc.update("""
                INSERT INTO audit_logs(id,organization_id,actor_user_id,actor_type,actor_system_key,
                    action,entity_type,entity_id,before_data,after_data,safe_metadata,result,occurred_at)
                VALUES (?,?,NULL,'SYSTEM',?,?,?, ?,CAST(? AS jsonb),CAST(? AS jsonb),CAST('{}' AS jsonb),'SUCCESS',?)
                """, UUID.randomUUID(), organizationId, actorKey, action, type, entityId,
                serialize(before), serialize(after), Timestamp.from(now));
        } catch (RuntimeException ex) { throw new AuditWriteException(ex); }
    }
    private String serialize(Object value) {
        try { return json.writeValueAsString(safe(value)); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Cannot serialize foundation audit", ex); }
    }
    private Object snapshot(Object value, List<String> changedFields) {
        Object sanitized = safe(value);
        if (value == null || changedFields == null) return sanitized;
        @SuppressWarnings("unchecked")
        Map<String, Object> result = new LinkedHashMap<>((Map<String, Object>) sanitized);
        result.put("changedFields", changedFields);
        return result;
    }
    private List<String> changedFields(String action, Object before, Object after) {
        List<String> changed = new ArrayList<>();
        if ("USER_UPDATED".equals(action) && before instanceof UserResponse a && after instanceof UserResponse b) {
            addChanged(changed, "fullName", a.fullName(), b.fullName());
            addChanged(changed, "status", a.status(), b.status());
            addChanged(changed, "metadata", a.metadata(), b.metadata());
        } else if ("TEAM_UPDATED".equals(action) && before instanceof TeamResponse a && after instanceof TeamResponse b) {
            addChanged(changed, "name", a.name(), b.name());
            addChanged(changed, "description", a.description(), b.description());
            addChanged(changed, "status", a.status(), b.status());
        } else if ("OFFERING_UPDATED".equals(action) && before instanceof OfferingResponse a && after instanceof OfferingResponse b) {
            addChanged(changed, "name", a.name(), b.name());
            addChanged(changed, "description", a.description(), b.description());
            addChanged(changed, "kind", a.kind(), b.kind());
            addChanged(changed, "categoryId", a.categoryId(), b.categoryId());
            addChanged(changed, "teamId", a.teamId(), b.teamId());
            addChanged(changed, "unit", a.unit(), b.unit());
            addChanged(changed, "referencePrice", a.referencePrice(), b.referencePrice());
        } else return null;
        return List.copyOf(changed);
    }
    private void addChanged(List<String> fields, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) fields.add(field);
    }
    private Object safe(Object value) {
        if (value == null) return Map.of();
        if (value instanceof UserResponse user)
            return Map.of("id",user.id(),"status",user.status(),"roles",user.roles(),"permissions",user.permissions(),"teamIds",user.teamIds());
        if (value instanceof TeamResponse team) {
            Map<String,Object> result=new LinkedHashMap<>();
            result.put("id",team.id());result.put("code",team.code());result.put("name",team.name());result.put("status",team.status());result.put("departmentId",team.departmentId());
            result.put("managerUserId",team.managerUserId());result.put("members",safe(team.members()));return result;
        }
        if (value instanceof TeamResponse.Member member) return Map.of("id",member.id(),"status",member.status());
        if (value instanceof OfferingResponse offering) {
            Map<String,Object> result=new LinkedHashMap<>();
            result.put("id",offering.id());result.put("code",offering.code());result.put("name",offering.name());result.put("kind",offering.kind());result.put("categoryId",offering.categoryId());
            result.put("active",offering.active());result.put("teamId",offering.teamId());result.put("referencePrice",offering.referencePrice());
            result.put("currencyCode",offering.currencyCode());return result;
        }
        if (value instanceof SalesAssignmentResponse assignment) {
            Map<String,Object> result=new LinkedHashMap<>();
            result.put("id",assignment.id());result.put("offeringId",assignment.offeringId());result.put("teamId",assignment.teamId());
            result.put("salesUserId",assignment.salesUserId());result.put("status",assignment.status());
            result.put("effectiveFrom",assignment.effectiveFrom());result.put("effectiveTo",assignment.effectiveTo());result.put("revokedAt",assignment.revokedAt());return result;
        }
        if (value instanceof List<?> values) return values.stream().map(this::safe).toList();
        return value;
    }
}
