package com.pose.backend.auth;

import com.pose.backend.identity.ActorContext;
import com.pose.backend.identity.PermissionCode;
import com.pose.backend.identity.RoleCode;
import java.util.Set;
import java.util.UUID;

public record AuthenticatedAccount(
        UUID userId, UUID organizationId, String organizationName,
        String email, String fullName, String passwordHash,
        boolean userActive, boolean organizationActive,
        Set<RoleCode> roles, Set<PermissionCode> permissions,
        Set<UUID> teamIds, Set<UUID> managedTeamIds) {

    public ActorContext actor() {
        return new ActorContext(userId, organizationId, roles, permissions, teamIds, managedTeamIds);
    }

    public boolean active() { return userActive && organizationActive; }

    @Override
    public String toString() {
        return "AuthenticatedAccount[userId=" + userId + ", organizationId=" + organizationId + "]";
    }
}
