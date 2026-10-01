package com.pose.backend.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AuthSessionRepository {
    private final JdbcTemplate jdbc;

    public AuthSessionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Session(UUID id, UUID userId, UUID organizationId, Instant expiresAt, Instant revokedAt) {
        public boolean activeAt(Instant now) {
            return revokedAt == null && expiresAt.isAfter(now);
        }
    }

    public record RefreshToken(UUID sessionId, Instant expiresAt, Instant consumedAt) {}

    public Optional<Session> findSession(UUID sessionId, boolean lock) {
        return jdbc.query("SELECT * FROM auth_sessions WHERE id = ?" + (lock ? " FOR UPDATE" : ""),
                sessionMapper(), sessionId).stream().findFirst();
    }

    public Optional<RefreshToken> findRefreshToken(String hash) {
        return jdbc.query("SELECT session_id, expires_at, consumed_at FROM auth_refresh_tokens WHERE token_hash = ?",
                (rs, row) -> new RefreshToken(rs.getObject("session_id", UUID.class),
                        rs.getTimestamp("expires_at").toInstant(), instantOrNull(rs.getTimestamp("consumed_at"))),
                hash).stream().findFirst();
    }

    public void createSession(UUID id, AuthenticatedAccount account, Instant now, Instant expiresAt) {
        jdbc.update("""
                INSERT INTO auth_sessions (id, user_id, organization_id, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?)
                """, id, account.userId(), account.organizationId(), Timestamp.from(now), Timestamp.from(expiresAt));
    }

    public void createRefreshToken(UUID sessionId, String hash, Instant now, Instant expiresAt) {
        jdbc.update("""
                INSERT INTO auth_refresh_tokens (id, session_id, token_hash, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), sessionId, hash, Timestamp.from(now), Timestamp.from(expiresAt));
    }

    public void consume(String hash, Instant now) {
        jdbc.update("UPDATE auth_refresh_tokens SET consumed_at = ? WHERE token_hash = ? AND consumed_at IS NULL",
                Timestamp.from(now), hash);
    }

    public void revoke(UUID sessionId, Instant now) {
        jdbc.update("UPDATE auth_sessions SET revoked_at = ? WHERE id = ? AND revoked_at IS NULL",
                Timestamp.from(now), sessionId);
    }

    private RowMapper<Session> sessionMapper() {
        return (rs, row) -> new Session(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getObject("organization_id", UUID.class), rs.getTimestamp("expires_at").toInstant(),
                instantOrNull(rs.getTimestamp("revoked_at")));
    }

    private static Instant instantOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
