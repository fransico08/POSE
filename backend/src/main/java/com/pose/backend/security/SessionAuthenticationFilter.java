package com.pose.backend.security;

import com.pose.backend.auth.AuthException;
import com.pose.backend.auth.AuthService;
import com.pose.backend.identity.ActorContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

public class SessionAuthenticationFilter extends OncePerRequestFilter {
    private final AuthService auth;
    private final SecurityErrorResponder errors;

    public SessionAuthenticationFilter(AuthService auth, SecurityErrorResponder errors) {
        this.auth = auth;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            try {
                Jwt jwt = jwtAuthentication.getToken();
                ActorContext actor = auth.validateAccess(UUID.fromString(jwt.getClaimAsString("sessionId")),
                        UUID.fromString(jwt.getClaimAsString("organizationId")), UUID.fromString(jwt.getSubject()));
                var validated = new JwtAuthenticationToken(jwt, actor.permissions().stream()
                        .map(permission -> new SimpleGrantedAuthority(permission.code())).toList());
                validated.setDetails(actor);
                SecurityContextHolder.getContext().setAuthentication(validated);
            } catch (AuthException | IllegalArgumentException exception) {
                SecurityContextHolder.clearContext();
                errors.write(request, response, 401, "AUTH_INVALID_TOKEN", "Phiên đăng nhập không còn hiệu lực.");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
