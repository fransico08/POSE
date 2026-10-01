package com.pose.backend.organization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
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
        return jdbc.query("SELECT id, code, name, industry, active FROM organizations WHERE id = ?"
                        + (lock ? " FOR UPDATE" : ""),
                (row, index) -> new OrganizationResponse(row.getObject("id", UUID.class),
                        row.getString("code"), row.getString("name"), row.getString("industry"),
                        row.getBoolean("active")), organizationId).stream().findFirst();
    }

    public void update(OrganizationResponse value) {
        jdbc.update("UPDATE organizations SET name = ?, industry = ? WHERE id = ?",
                value.name(), value.industry(), value.id());
    }

    public void audit(UUID actorUserId, OrganizationResponse before, OrganizationResponse after, Instant now) {
        // Serialization escapes user text; no credentials or session tokens enter the snapshots.
        jdbc.update("""
                INSERT INTO audit_logs
                    (id, actor_user_id, action, entity_type, entity_id, old_value, new_value, occurred_at)
                VALUES (?, ?, 'ORGANIZATION_UPDATED', 'ORGANIZATION', ?, CAST(? AS jsonb), CAST(? AS jsonb), ?)
                """, UUID.randomUUID(), actorUserId, before.id(), json(before), json(after), Timestamp.from(now));
    }

    private String json(OrganizationResponse value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot serialize organization audit", exception); }
    }
}
