package com.pose.backend.auth;

import com.pose.backend.identity.PermissionCode;
import com.pose.backend.identity.RoleCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class AuthAccountRepository {
    private static final String ACCOUNT_QUERY = """
            SELECT u.id, u.organization_id, o.name AS organization_name,
                   u.email, u.full_name, u.password_hash,
                   u.active AS user_active, o.active AS organization_active
            FROM users u JOIN organizations o ON o.id = u.organization_id
            """;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AuthAccountRepository(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public Optional<AuthenticatedAccount> findByLogin(String organizationCode, String email) {
        var matches = jdbc.query(ACCOUNT_QUERY + " WHERE lower(o.code) = ? AND lower(u.email) = ?",
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
            Set<RoleCode> roles = jdbc.queryForList("""
                    SELECT r.code FROM user_roles ur
                    JOIN roles r ON r.code = ur.role AND r.organization_id = ?
                    WHERE ur.user_id = ?
                    """, String.class, organizationId, userId).stream()
                    .map(RoleCode::fromCode).collect(Collectors.toUnmodifiableSet());
            Set<PermissionCode> permissions = jdbc.queryForList("""
                    SELECT DISTINCT p.code FROM user_roles ur
                    JOIN roles r ON r.code = ur.role AND r.organization_id = ?
                    JOIN role_permissions rp ON rp.organization_id = r.organization_id AND rp.role_code = r.code
                    JOIN permissions p ON p.code = rp.permission_code
                    WHERE ur.user_id = ?
                    """, String.class, organizationId, userId).stream()
                    .map(PermissionCode::fromCode).collect(Collectors.toUnmodifiableSet());
            LocalDate today = LocalDate.now(clock);
            Set<UUID> teamIds = Set.copyOf(jdbc.queryForList("""
                    SELECT DISTINCT t.id FROM teams t
                    JOIN team_memberships tm ON tm.team_id = t.id
                    WHERE t.organization_id = ? AND t.active = TRUE AND tm.user_id = ?
                      AND tm.active = TRUE AND tm.joined_at <= ?
                      AND (tm.ended_at IS NULL OR tm.ended_at >= ?)
                    """, UUID.class, organizationId, userId, today, today));
            Set<UUID> managedTeamIds = roles.contains(RoleCode.MANAGER) ? Set.copyOf(jdbc.queryForList("""
                    SELECT id FROM teams WHERE organization_id = ? AND active = TRUE AND manager_user_id = ?
                    """, UUID.class, organizationId, userId)) : Set.of();
            return new AuthenticatedAccount(userId, organizationId, rs.getString("organization_name"),
                    rs.getString("email"), rs.getString("full_name"), rs.getString("password_hash"),
                    rs.getBoolean("user_active"), rs.getBoolean("organization_active"),
                    roles, permissions, teamIds, managedTeamIds);
        };
    }
}
