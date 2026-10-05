package com.pose.backend.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
public class AuthAuditRepository {
    private final JdbcTemplate jdbc;

    public AuthAuditRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void record(String action, AuthSessionRepository.Session session, Instant now) {
        // Only scope metadata is persisted, never credentials or token values/hashes.
        jdbc.update("""
                INSERT INTO audit_logs
                    (id, organization_id, actor_user_id, action, entity_type, entity_id,
                     before_data, after_data, safe_metadata, result, occurred_at)
                VALUES (?, ?, ?, ?, 'AUTH_SESSION', ?, CAST('{}' AS jsonb), CAST(? AS jsonb),
                        CAST('{}' AS jsonb), 'SUCCESS', ?)
                """, UUID.randomUUID(), session.organizationId(), session.userId(), action, session.id(),
                "{\"organizationId\":\"" + session.organizationId() + "\"}", Timestamp.from(now));
    }
}
