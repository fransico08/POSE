package com.pose.backend.security;

import com.pose.backend.identity.ActorContext;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtTokenService(JwtEncoder jwtEncoder, JwtProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedAccessToken issue(ActorContext actor, UUID sessionId, Instant sessionExpiresAt) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.getAccessTokenTtl());
        if (sessionExpiresAt.isBefore(expiresAt)) expiresAt = sessionExpiresAt;
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("pose-api")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .subject(actor.userId().toString())
                .claim("organizationId", actor.organizationId().toString())
                .claim("sessionId", sessionId.toString())
                .claim("roles", actor.roles().stream().map(Enum::name).sorted().toList())
                .claim("permissions", actor.permissions().stream().map(permission -> permission.code()).sorted().toList())
                .claim("teamIds", actor.teamIds().stream().map(UUID::toString).sorted().toList())
                .claim("managedTeamIds", actor.managedTeamIds().stream().map(UUID::toString).sorted().toList())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(token, Duration.between(issuedAt, expiresAt).toSeconds());
    }

    public record IssuedAccessToken(String value, long expiresInSeconds) {
    }
}
