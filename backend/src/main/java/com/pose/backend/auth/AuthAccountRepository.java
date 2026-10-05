package com.pose.backend.auth;

import com.pose.backend.identity.PermissionCode;
import com.pose.backend.identity.RoleCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.time.Clock;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class AuthAccountRepository {
    private static final String ACCOUNT_QUERY = """
            SELECT u.id, u.organization_id, o.name AS organization_name,
                   u.email, u.full_name, c.password_hash,
                   (u.status = 'ACTIVE' AND u.archived_at IS NULL) AS user_active,
                   (o.status = 'ACTIVE') AS organization_active
            FROM users u JOIN organizations o ON o.id = u.organization_id
            JOIN credentials c ON c.user_id = u.id
            """;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AuthAccountRepository(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public Optional<AuthenticatedAccount> findByLogin(String organizationCode, String email) {
        var matches = jdbc.query(ACCOUNT_QUERY + " WHERE o.code = ? AND lower(u.email) = ?",
                accountMapper(), organizationCode, email);
        // Fail closed if an unsynchronized seed violates normalized uniqueness.
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    public Optional<AuthenticatedAccount> findById(UUID organizationId, UUID userId) {
        return jdbc.query(ACCOUNT_QUERY + " WHERE u.organization_id = ? AND u.id = ?",
                accountMapper(), organizationId, userId).stream().findFirst();
    }

    private RowMapper<AuthenticatedAccount> accountMapper() {
        return (rs, row) -> {
            UUID userId = rs.getObject("id", UUID.class);
            UUID organizationId = rs.getObject("organization_id", UUID.class);
            Timestamp now = Timestamp.from(clock.instant());
            Set<RoleCode> roles = jdbc.queryForList("""
                    SELECT DISTINCT r.code FROM user_role_grants g
                    JOIN roles r ON r.id = g.role_id AND r.organization_id = g.organization_id
                    WHERE g.organization_id = ? AND g.user_id = ? AND r.status = 'ACTIVE'
                      AND g.granted_at <= ? AND (g.revoked_at IS NULL OR g.revoked_at > ?)
                    """, String.class, organizationId, userId, now, now).stream()
                    .map(RoleCode::fromCode).collect(Collectors.toUnmodifiableSet());
            Set<PermissionCode> permissions = jdbc.queryForList("""
                    SELECT DISTINCT p.code FROM user_role_grants g
                    JOIN roles r ON r.id = g.role_id AND r.organization_id = g.organization_id
                    JOIN role_permissions rp ON rp.role_id = r.id
                    JOIN permissions p ON p.id = rp.permission_id
                    WHERE g.organization_id = ? AND g.user_id = ? AND r.status = 'ACTIVE'
                      AND g.granted_at <= ? AND (g.revoked_at IS NULL OR g.revoked_at > ?)
                    """, String.class, organizationId, userId, now, now).stream()
                    .map(PermissionCode::fromCode).collect(Collectors.toUnmodifiableSet());
            Set<UUID> teamIds = Set.copyOf(jdbc.queryForList("""
                    SELECT DISTINCT t.id FROM teams t
                    JOIN team_memberships tm ON tm.team_id = t.id
                    WHERE t.organization_id = ? AND tm.organization_id = t.organization_id
                      AND t.status = 'ACTIVE' AND tm.user_id = ? AND tm.status = 'ACTIVE'
                      AND tm.joined_at <= ? AND (tm.left_at IS NULL OR tm.left_at > ?)
                    """, UUID.class, organizationId, userId, now, now));
            Set<UUID> managedTeamIds = roles.contains(RoleCode.MANAGER) ? Set.copyOf(jdbc.queryForList("""
                    SELECT id FROM teams WHERE organization_id = ? AND status = 'ACTIVE' AND manager_user_id = ?
                    """, UUID.class, organizationId, userId)) : Set.of();
            return new AuthenticatedAccount(userId, organizationId, rs.getString("organization_name"),
                    rs.getString("email"), rs.getString("full_name"), rs.getString("password_hash"),
                    rs.getBoolean("user_active"), rs.getBoolean("organization_active"),
                    roles, permissions, teamIds, managedTeamIds);
        };
    }
}
