package com.pose.backend.organization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.pose.backend.audit.AuditWriteException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class OrganizationRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public OrganizationRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Optional<OrganizationResponse> find(UUID organizationId, boolean lock) {
        return jdbc.query("SELECT id, code, name, industry, (status = 'ACTIVE') AS active FROM organizations WHERE id = ?"
                        + (lock ? " FOR UPDATE" : ""),
                (row, index) -> new OrganizationResponse(row.getObject("id", UUID.class),
                        row.getString("code"), row.getString("name"), row.getString("industry"),
                        row.getBoolean("active")), organizationId).stream().findFirst();
    }

    public void update(OrganizationResponse value) {
        jdbc.update("UPDATE organizations SET name = ?, industry = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                value.name(), value.industry(), value.id());
    }

    public void audit(UUID actorUserId, OrganizationResponse before, OrganizationResponse after, Instant now) {
        java.util.List<String> changedFields = new java.util.ArrayList<>();
        if (!java.util.Objects.equals(before.name(), after.name())) changedFields.add("name");
        if (!java.util.Objects.equals(before.industry(), after.industry())) changedFields.add("industry");
        try {
        jdbc.update("""
                INSERT INTO audit_logs
                    (id, organization_id, actor_user_id, action, entity_type, entity_id,
                     before_data, after_data, safe_metadata, result, occurred_at)
                VALUES (?, ?, ?, 'ORGANIZATION_UPDATED', 'ORGANIZATION', ?,
                        CAST(? AS jsonb), CAST(? AS jsonb), CAST('{}' AS jsonb), 'SUCCESS', ?)
                """, UUID.randomUUID(), before.id(), actorUserId, before.id(), json(before,changedFields), json(after,changedFields), Timestamp.from(now));
        } catch (RuntimeException ex) { throw new AuditWriteException(ex); }
    }

    private String json(OrganizationResponse value, java.util.List<String> changedFields) {
        try { return mapper.writeValueAsString(java.util.Map.of("id",value.id(),"active",value.active(),"changedFields",changedFields)); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot serialize organization audit", exception); }
    }
}
