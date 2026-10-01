package com.pose.backend.security;

import com.pose.backend.identity.ActorContext;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;


@Component
public class JwtActorContextResolver {

    public ActorContext currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getDetails() instanceof ActorContext actor) {
            return actor;
        }
        throw new InsufficientAuthenticationException("A validated access token is required");
    }
}
