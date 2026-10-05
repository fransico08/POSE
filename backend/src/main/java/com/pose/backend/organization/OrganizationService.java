package com.pose.backend.organization;

import com.pose.backend.api.ApiRequestException;
import com.pose.backend.identity.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;

@Service
public class OrganizationService {
    private final OrganizationRepository organizations;
    private final AuthorizationPolicy policy;
    private final Clock clock;
    private final FoundationAccess access;

    public OrganizationService(OrganizationRepository organizations, AuthorizationPolicy policy, Clock clock, FoundationAccess access) {
        this.organizations = organizations;
        this.policy = policy;
        this.clock = clock;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public OrganizationResponse current(ActorContext actor) {
        return scoped(actor, false);
    }

    @Transactional
    public OrganizationResponse patch(ActorContext actor, OrganizationPatch patch) {
        actor = access.lockAndRefresh(actor);
        policy.requirePermission(actor, PermissionCode.ORGANIZATION_TEAM_MANAGE);
        if (!actor.hasRole(RoleCode.ADMIN)) {
            throw new AuthorizationDeniedException(AuthorizationFailureCode.FORBIDDEN_PERMISSION,
                    "Administrator role is required");
        }
        // Lock before merging partial fields: simultaneous PATCH requests must not overwrite each other.
        OrganizationResponse before = scoped(actor, true);
        OrganizationResponse after = new OrganizationResponse(before.id(), before.code(),
                patch.hasName() ? patch.name() : before.name(),
                patch.hasIndustry() ? patch.industry() : before.industry(), before.active());
        if (!after.equals(before)) {
            organizations.update(after);
            organizations.audit(actor.userId(), before, after, clock.instant());
        }
        return after;
    }

    private OrganizationResponse scoped(ActorContext actor, boolean lock) {
        OrganizationResponse value = organizations.find(actor.organizationId(), lock)
                .orElseThrow(ApiRequestException::notFound);
        policy.requireOrganizationScope(actor, value.id());
        return value;
    }
}
