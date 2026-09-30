package com.pose.backend.identity;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record ActorContext(
        UUID userId,
        UUID organizationId,
        Set<RoleCode> roles,
        Set<PermissionCode> permissions,
        Set<UUID> teamIds) {

    public ActorContext {
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(organizationId, "organizationId is required");
        roles = Set.copyOf(Objects.requireNonNull(roles, "roles are required"));
        permissions = Set.copyOf(Objects.requireNonNull(permissions, "permissions are required"));
        teamIds = Set.copyOf(Objects.requireNonNull(teamIds, "teamIds are required"));
    }

    public boolean hasRole(RoleCode role) {
        return roles.contains(role);
    }

    public boolean hasPermission(PermissionCode permission) {
        return permissions.contains(permission);
    }
}
