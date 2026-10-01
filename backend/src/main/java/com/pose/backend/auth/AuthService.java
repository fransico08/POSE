package com.pose.backend.auth;

import com.pose.backend.identity.ActorContext;
import com.pose.backend.security.JwtTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {
    private final AuthAccountRepository accounts;
    private final AuthSessionRepository sessions;
    private final AuthAuditRepository audit;
    private final PasswordEncoder passwords;
    private final JwtTokenService accessTokens;
    private final RefreshTokenCodec refreshTokens;
    private final AuthProperties properties;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthService(AuthAccountRepository accounts, AuthSessionRepository sessions, AuthAuditRepository audit,
                       PasswordEncoder passwords, JwtTokenService accessTokens, RefreshTokenCodec refreshTokens,
                       AuthProperties properties, Clock clock) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.audit = audit;
        this.passwords = passwords;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.properties = properties;
        this.clock = clock;
        this.dummyPasswordHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthTokensResponse login(String organizationCode, String email, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw AuthException.invalidCredentials();
        }
        AuthenticatedAccount account = accounts.findByLogin(
                organizationCode.strip().toLowerCase(Locale.ROOT),
                email.strip().toLowerCase(Locale.ROOT)).orElse(null);
        // Spend the same BCrypt work for a missing/disabled account without disclosing which lookup failed.
        boolean matches = passwords.matches(password,
                account != null && account.active() ? account.passwordHash() : dummyPasswordHash);
        if (account == null || !account.active() || !matches) throw AuthException.invalidCredentials();

        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.refreshTokenTtl());
        UUID sessionId = UUID.randomUUID();
        sessions.createSession(sessionId, account, now, expiresAt);
        var session = new AuthSessionRepository.Session(sessionId, account.userId(), account.organizationId(), expiresAt, null);
        AuthTokensResponse response = issue(account, session, now);
        audit.record("AUTH_LOGIN_SUCCEEDED", session, now);
        return response;
    }

    // Replay detection deliberately commits session revocation even though the response is 401.
    @Transactional(noRollbackFor = AuthException.class)
    public AuthTokensResponse refresh(String tokenValue) {
        String hash = refreshTokens.hash(tokenValue);
        var token = sessions.findRefreshToken(hash).orElseThrow(AuthException::invalidRefreshToken);
        var session = sessions.findSession(token.sessionId(), true).orElseThrow(AuthException::invalidRefreshToken);
        // Read again after taking the session lock: another request may have consumed the token.
        token = sessions.findRefreshToken(hash).orElseThrow(AuthException::invalidRefreshToken);
        Instant now = clock.instant();
        if (!session.activeAt(now) || !token.expiresAt().isAfter(now)) throw AuthException.invalidRefreshToken();
        if (token.consumedAt() != null) {
            sessions.revoke(session.id(), now);
            audit.record("AUTH_REFRESH_REUSE_DETECTED", session, now);
            throw AuthException.invalidRefreshToken();
        }
        AuthenticatedAccount account = accounts.findById(session.organizationId(), session.userId())
                .orElseThrow(AuthException::invalidRefreshToken);
        if (!account.active()) {
            sessions.revoke(session.id(), now);
            audit.record("AUTH_SESSION_REVOKED", session, now);
            throw AuthException.invalidRefreshToken();
        }
        sessions.consume(hash, now);
        AuthTokensResponse response = issue(account, session, now);
        audit.record("AUTH_TOKEN_REFRESHED", session, now);
        return response;
    }

    @Transactional(noRollbackFor = AuthException.class)
    public void logout(ActorContext actor, UUID sessionId, String tokenValue) {
        var session = sessions.findSession(sessionId, true).orElseThrow(AuthException::invalidAccessToken);
        var token = sessions.findRefreshToken(refreshTokens.hash(tokenValue))
                .orElseThrow(AuthException::invalidRefreshToken);
        if (!session.userId().equals(actor.userId()) || !session.organizationId().equals(actor.organizationId())
                || !token.sessionId().equals(session.id())) throw AuthException.invalidRefreshToken();
        Instant now = clock.instant();
        if (!session.activeAt(now)) throw AuthException.invalidAccessToken();
        sessions.revoke(session.id(), now);
        audit.record("AUTH_LOGOUT", session, now);
    }

    @Transactional(readOnly = true)
    public ActorContext validateAccess(UUID sessionId, UUID organizationId, UUID userId) {
        var session = sessions.findSession(sessionId, false).orElseThrow(AuthException::invalidAccessToken);
        if (!session.userId().equals(userId) || !session.organizationId().equals(organizationId)
                || !session.activeAt(clock.instant())) throw AuthException.invalidAccessToken();
        AuthenticatedAccount account = accounts.findById(organizationId, userId)
                .orElseThrow(AuthException::invalidAccessToken);
        if (!account.active()) throw AuthException.invalidAccessToken();
        return account.actor();
    }

    @Transactional(readOnly = true)
    public AuthUserResponse me(ActorContext actor) {
        AuthenticatedAccount account = accounts.findById(actor.organizationId(), actor.userId())
                .orElseThrow(AuthException::invalidAccessToken);
        if (!account.active()) throw AuthException.invalidAccessToken();
        return AuthUserResponse.from(account);
    }

    private AuthTokensResponse issue(AuthenticatedAccount account, AuthSessionRepository.Session session, Instant now) {
        String refreshToken = refreshTokens.generate();
        sessions.createRefreshToken(session.id(), refreshTokens.hash(refreshToken), now, session.expiresAt());
        var accessToken = accessTokens.issue(account.actor(), session.id(), session.expiresAt());
        return new AuthTokensResponse(accessToken.value(), refreshToken, accessToken.expiresInSeconds(),
                AuthUserResponse.from(account));
    }
}
