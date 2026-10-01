package com.pose.backend.identity;

import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class AuthorizationPolicyTest {
    private final AuthorizationPolicy policy = new AuthorizationPolicy();
    private final UUID user = UUID.randomUUID(), organization = UUID.randomUUID();
    private final UUID managedTeam = UUID.randomUUID(), memberOnlyTeam = UUID.randomUUID();

    @Test
    void multipleRolesAndAllPermissionsCannotEscapeOrganization() {
        var actor = new ActorContext(user, organization, Set.of(RoleCode.ADMIN, RoleCode.MANAGER),
                Set.of(PermissionCode.values()), Set.of(memberOnlyTeam), Set.of(managedTeam));
        assertThatThrownBy(() -> policy.requireUserRead(actor, UUID.randomUUID(), UUID.randomUUID(), Set.of(managedTeam)))
                .isInstanceOfSatisfying(AuthorizationDeniedException.class,
                        exception -> assertThat(exception.code()).isEqualTo(AuthorizationFailureCode.FORBIDDEN_SCOPE));
    }

    @Test
    void managerCannotReadUserThroughMembershipInUnmanagedTeam() {
        var actor = manager();
        policy.requireUserRead(actor, organization, UUID.randomUUID(), Set.of(managedTeam));
        assertThatThrownBy(() -> policy.requireUserRead(actor, organization, UUID.randomUUID(), Set.of(memberOnlyTeam)))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    @Test
    void administratorCannotChangeOwnRolesEvenWithIdentityPermission() {
        var actor = new ActorContext(user, organization, Set.of(RoleCode.ADMIN),
                Set.of(PermissionCode.IDENTITY_ROLE_MANAGE), Set.of());
        assertThatThrownBy(() -> policy.requireRoleAdministration(actor, organization, user))
                .isInstanceOfSatisfying(AuthorizationDeniedException.class,
                        exception -> assertThat(exception.code()).isEqualTo(AuthorizationFailureCode.FORBIDDEN_SELF_PRIVILEGE_ESCALATION));
    }

    @Test
    void roleAdministrationRequiresBothAdminAndExplicitPermission() {
        var adminWithoutPermission = new ActorContext(user, organization, Set.of(RoleCode.ADMIN), Set.of(), Set.of());
        var managerWithPermission = new ActorContext(user, organization, Set.of(RoleCode.MANAGER),
                Set.of(PermissionCode.IDENTITY_ROLE_MANAGE), Set.of());
        UUID target = UUID.randomUUID();
        assertThatThrownBy(() -> policy.requireRoleAdministration(adminWithoutPermission, organization, target))
                .isInstanceOf(AuthorizationDeniedException.class);
        assertThatThrownBy(() -> policy.requireRoleAdministration(managerWithPermission, organization, target))
                .isInstanceOf(AuthorizationDeniedException.class);
        var allowed = new ActorContext(user, organization, Set.of(RoleCode.ADMIN),
                Set.of(PermissionCode.IDENTITY_ROLE_MANAGE), Set.of());
        policy.requireRoleAdministration(allowed, organization, target);
    }

    private ActorContext manager() {
        return new ActorContext(user, organization, Set.of(RoleCode.MANAGER, RoleCode.CUSTOMER_CARE),
                Set.of(PermissionCode.IDENTITY_USER_READ), Set.of(memberOnlyTeam), Set.of(managedTeam));
    }
}
