package com.pose.backend.security;

import com.pose.backend.identity.ActorContext;
import com.pose.backend.identity.PermissionCode;
import com.pose.backend.identity.RoleCode;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class JwtActorContextResolver {

    public ActorContext currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new InsufficientAuthenticationException("A valid access token is required");
        }

        try {
            UUID userId = UUID.fromString(jwt.getSubject());
            UUID organizationId = UUID.fromString(jwt.getClaimAsString("organizationId"));
            Set<RoleCode> roles = requiredStringList(jwt, "roles").stream()
                    .map(RoleCode::fromCode)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            Set<PermissionCode> permissions = requiredStringList(jwt, "permissions").stream()
                    .map(PermissionCode::fromCode)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            Set<UUID> teamIds = requiredStringList(jwt, "teamIds").stream()
                    .map(UUID::fromString)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            return new ActorContext(userId, organizationId, roles, permissions, teamIds);
        } catch (RuntimeException exception) {
            throw new InsufficientAuthenticationException("Access token claims are invalid", exception);
        }
    }

    private List<String> requiredStringList(Jwt jwt, String claimName) {
        List<String> values = jwt.getClaimAsStringList(claimName);
        if (values == null) {
            throw new IllegalArgumentException("Missing token claim: " + claimName);
        }
        return values;
    }
}
