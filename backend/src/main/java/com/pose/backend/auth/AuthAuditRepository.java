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
                    (id, actor_user_id, action, entity_type, entity_id, new_value, occurred_at)
                VALUES (?, ?, ?, 'AUTH_SESSION', ?, CAST(? AS jsonb), ?)
                """, UUID.randomUUID(), session.userId(), action, session.id(),
                "{\"organizationId\":\"" + session.organizationId() + "\"}", Timestamp.from(now));
    }
}
