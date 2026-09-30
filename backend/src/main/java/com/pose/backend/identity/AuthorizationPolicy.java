package com.pose.backend.identity;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class AuthorizationPolicy {

    public void requireOrganizationScope(ActorContext actor, UUID targetOrganizationId) {
        Objects.requireNonNull(actor, "actor is required");
        if (!actor.organizationId().equals(targetOrganizationId)) {
            throw new AuthorizationDeniedException(
                    AuthorizationFailureCode.FORBIDDEN_SCOPE,
                    "Organization scope denied");
        }
    }

    public void requirePermission(ActorContext actor, PermissionCode permission) {
        Objects.requireNonNull(actor, "actor is required");
        if (!actor.hasPermission(permission)) {
            throw new AuthorizationDeniedException(
                    AuthorizationFailureCode.FORBIDDEN_PERMISSION,
                    "Required permission is missing");
        }
    }

    public void requireTeamScope(ActorContext actor, UUID targetOrganizationId, UUID teamId) {
        requireOrganizationScope(actor, targetOrganizationId);
        if (!actor.hasRole(RoleCode.ADMIN) && !actor.teamIds().contains(teamId)) {
            throw new AuthorizationDeniedException(
                    AuthorizationFailureCode.FORBIDDEN_SCOPE,
                    "Team scope denied");
        }
    }

    public void requireUserRead(
            ActorContext actor,
            UUID targetOrganizationId,
            UUID targetUserId,
            Set<UUID> targetUserTeamIds) {
        requireOrganizationScope(actor, targetOrganizationId);
        Objects.requireNonNull(targetUserTeamIds, "targetUserTeamIds are required");
        if (actor.userId().equals(targetUserId)) {
            return;
        }
        requirePermission(actor, PermissionCode.IDENTITY_USER_READ);
        if (actor.hasRole(RoleCode.ADMIN)) {
            return;
        }
        if (actor.hasRole(RoleCode.MANAGER)
                && targetUserTeamIds.stream().anyMatch(actor.teamIds()::contains)) {
            return;
        }
        throw new AuthorizationDeniedException(
                AuthorizationFailureCode.FORBIDDEN_SCOPE,
                "User record scope denied");
    }

    public void requireRoleAdministration(
            ActorContext actor,
            UUID targetOrganizationId,
            UUID targetUserId) {
        requireOrganizationScope(actor, targetOrganizationId);
        if (actor.userId().equals(targetUserId)) {
            throw new AuthorizationDeniedException(
                    AuthorizationFailureCode.FORBIDDEN_SELF_PRIVILEGE_ESCALATION,
                    "Self privilege escalation is forbidden");
        }
        requirePermission(actor, PermissionCode.IDENTITY_ROLE_MANAGE);
        if (!actor.hasRole(RoleCode.ADMIN)) {
            throw new AuthorizationDeniedException(
                    AuthorizationFailureCode.FORBIDDEN_PERMISSION,
                    "Administrator role is required");
        }
    }
}
