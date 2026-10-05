package com.pose.backend.identity;

import com.pose.backend.auth.AuthAccountRepository;
import com.pose.backend.auth.AuthException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class FoundationAccess {
    private final JdbcTemplate jdbc;
    private final AuthAccountRepository accounts;
    private final AuthorizationPolicy policy;
    public FoundationAccess(JdbcTemplate jdbc, AuthAccountRepository accounts, AuthorizationPolicy policy) {
        this.jdbc = jdbc; this.accounts = accounts; this.policy = policy;
    }
    // Serialize identity/team administration within a tenant, then re-read current grants.
    public ActorContext lockAndRefresh(ActorContext actor) {
        if (jdbc.queryForList("SELECT id FROM organizations WHERE id=? FOR UPDATE", UUID.class, actor.organizationId()).isEmpty())
            throw AuthException.invalidAccessToken();
        var account = accounts.findById(actor.organizationId(), actor.userId()).orElseThrow(AuthException::invalidAccessToken);
        if (!account.active()) throw AuthException.invalidAccessToken();
        return account.actor();
    }
    public void requireAdmin(ActorContext actor, PermissionCode permission) {
        policy.requirePermission(actor, permission);
        if (!actor.hasRole(RoleCode.ADMIN)) denyPermission();
    }
    public static void denyScope() {
        throw new AuthorizationDeniedException(AuthorizationFailureCode.FORBIDDEN_SCOPE, "Scope denied");
    }
    public static void denyPermission() {
        throw new AuthorizationDeniedException(AuthorizationFailureCode.FORBIDDEN_PERMISSION, "Permission denied");
    }
    public static void denySelf() {
        throw new AuthorizationDeniedException(AuthorizationFailureCode.FORBIDDEN_SELF_PRIVILEGE_ESCALATION, "Self administration denied");
    }
    public static String placeholders(int count) { return String.join(",", Collections.nCopies(count, "?")); }
}
