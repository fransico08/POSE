package com.pose.backend.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.pose.backend.auth.AuthProperties;
import com.pose.backend.auth.AuthService;
import com.pose.backend.identity.AuthorizationPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, AuthProperties.class})
public class SecurityConfiguration {
    @Bean
    public SecretKey jwtSecretKey(JwtProperties properties) {
        final byte[] secret;
        try {
            secret = Base64.getDecoder().decode(properties.getSecretBase64());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("POSE_JWT_SECRET_BASE64 must be valid Base64", exception);
        }
        if (secret.length < 32) {
            throw new IllegalStateException("POSE_JWT_SECRET_BASE64 must contain at least 32 random bytes");
        }
        return new SecretKeySpec(secret, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        OAuth2TokenValidator<Jwt> claims = jwt -> {
            if (jwt.getExpiresAt() == null || jwt.getIssuedAt() == null) {
                return invalidToken("invalid_token", "Required timestamp claims are missing");
            }
            if (!jwt.getExpiresAt().isAfter(clock.instant())) {
                return invalidToken("AUTH_TOKEN_EXPIRED", "Access token has expired");
            }
            try {
                UUID.fromString(jwt.getSubject());
                UUID.fromString(jwt.getClaimAsString("organizationId"));
                UUID.fromString(jwt.getClaimAsString("sessionId"));
            } catch (RuntimeException exception) {
                return invalidToken("invalid_token", "Required identity claims are invalid");
            }
            return OAuth2TokenValidatorResult.success();
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestamps, new JwtIssuerValidator("pose-api"), claims));
        return decoder;
    }

    private static OAuth2TokenValidatorResult invalidToken(String code, String message) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(code, message, null));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public Clock utcClock() {
        return Clock.systemUTC();
    }

    @Bean
    public AuthorizationPolicy authorizationPolicy() {
        return new AuthorizationPolicy();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthService auth, SecurityErrorResponder errors)
            throws Exception {
        DefaultBearerTokenResolver bearerTokens = new DefaultBearerTokenResolver();
        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/channels/telegram/webhook",
                                "/api/v1/channels/zalo-sim/webhook").permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errors::unauthorized)
                        .accessDeniedHandler((request, response, exception) ->
                                errors.write(request, response, 403, "FORBIDDEN_PERMISSION",
                                        "Bạn không có quyền thực hiện thao tác này.")))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(request -> {
                            String path = request.getRequestURI().substring(request.getContextPath().length());
                            // A stale access header must not prevent exchanging a valid refresh token.
                            if ("POST".equals(request.getMethod())
                                    && ("/api/v1/auth/login".equals(path) || "/api/v1/auth/refresh".equals(path))) {
                                return null;
                            }
                            return bearerTokens.resolve(request);
                        })
                        .authenticationEntryPoint(errors::unauthorized)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(token ->
                                new JwtAuthenticationToken(token, List.of()))))
                .addFilterAfter(new SessionAuthenticationFilter(auth, errors), BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}
