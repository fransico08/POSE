package com.pose.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pose.backend.identity.AuthorizationPolicy;
import com.pose.backend.security.JwtActorContextResolver;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:poseauth;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.flyway.locations=classpath:db/auth-test-baseline,classpath:db/migration",
        "pose.security.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "spring.jpa.hibernate.ddl-auto=none"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import({AuthIntegrationTest.ClockConfiguration.class, AuthIntegrationTest.ProbeController.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthIntegrationTest {
    private static final Instant START = Instant.parse("2026-10-01T00:00:00Z");
    private static final UUID ORG_ONE = id(1), ORG_TWO = id(2);
    private static final UUID USER_ONE = id(11), USER_TWO = id(12), USER_THREE = id(13);
    private static final UUID TEAM_MANAGED = id(21), TEAM_MEMBER_ONLY = id(22);
    private static final String PASSWORD_ONE = "tenant-one-password", PASSWORD_TWO = "tenant-two-password";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtDecoder decoder;
    @Autowired JwtEncoder encoder;
    @Autowired AuthService auth;
    @Autowired MutableClock clock;
    String hashOne, hashTwo;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = System.getenv("POSE_TEST_DATABASE_URL");
        if (url != null) {
            if (!url.startsWith("jdbc:postgresql:")) throw new IllegalArgumentException("Expected PostgreSQL test database");
            properties.add("spring.datasource.url", () -> url);
            properties.add("spring.datasource.username", () -> System.getenv("POSE_TEST_DATABASE_USERNAME"));
            properties.add("spring.datasource.password", () -> Optional.ofNullable(System.getenv("POSE_TEST_DATABASE_PASSWORD")).orElse(""));
        }
    }

    @BeforeAll
    void passwordHashes() {
        hashOne = passwords.encode(PASSWORD_ONE);
        hashTwo = passwords.encode(PASSWORD_TWO);
    }

    @BeforeEach
    void seed() {
        clock.now = START;
        for (String table : List.of("auth_refresh_tokens", "auth_sessions", "audit_logs", "team_memberships",
                "teams", "role_permissions", "user_roles", "permissions", "roles", "users", "organizations")) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("INSERT INTO organizations(id, code, name) VALUES (?, 'one', 'Tenant One'), (?, 'two', 'Tenant Two')",
                ORG_ONE, ORG_TWO);
        jdbc.update("INSERT INTO users(id, organization_id, full_name, email, password_hash) VALUES (?, ?, ?, ?, ?)",
                USER_ONE, ORG_ONE, "User One", "shared@example.com", hashOne);
        jdbc.update("INSERT INTO users(id, organization_id, full_name, email, password_hash) VALUES (?, ?, ?, ?, ?)",
                USER_TWO, ORG_TWO, "User Two", "shared@example.com", hashTwo);
        jdbc.update("INSERT INTO users(id, organization_id, full_name, email, password_hash) VALUES (?, ?, ?, ?, ?)",
                USER_THREE, ORG_ONE, "Other Manager", "other@example.com", hashOne);
        for (UUID organization : List.of(ORG_ONE, ORG_TWO)) {
            for (String role : List.of("MANAGER", "CUSTOMER_CARE", "SALES_REP")) {
                jdbc.update("INSERT INTO roles(organization_id, code) VALUES (?, ?)", organization, role);
            }
        }
        for (String permission : List.of("identity.user.read", "customer.read", "feedback.write", "identity.role.manage")) {
            jdbc.update("INSERT INTO permissions(code) VALUES (?)", permission);
        }
        jdbc.update("INSERT INTO user_roles(user_id, role) VALUES (?, 'MANAGER'), (?, 'CUSTOMER_CARE'), (?, 'SALES_REP')",
                USER_ONE, USER_ONE, USER_TWO);
        grant(ORG_ONE, "MANAGER", "identity.user.read");
        grant(ORG_ONE, "MANAGER", "customer.read");
        grant(ORG_ONE, "CUSTOMER_CARE", "customer.read");
        grant(ORG_ONE, "CUSTOMER_CARE", "feedback.write");
        grant(ORG_TWO, "MANAGER", "identity.role.manage");
        grant(ORG_TWO, "SALES_REP", "customer.read");
        jdbc.update("INSERT INTO teams(id, organization_id, code, name, manager_user_id) VALUES (?, ?, 'M', 'Managed', ?)",
                TEAM_MANAGED, ORG_ONE, USER_ONE);
        jdbc.update("INSERT INTO teams(id, organization_id, code, name, manager_user_id) VALUES (?, ?, 'N', 'Member only', ?)",
                TEAM_MEMBER_ONLY, ORG_ONE, USER_THREE);
        membership(TEAM_MANAGED, LocalDate.of(2026, 9, 1), null, true);
        membership(TEAM_MEMBER_ONLY, LocalDate.of(2026, 9, 1), null, true);
    }

    @Test
    void loginUsesTenantAndReturnsUnionOfRolesWithoutCredentialLeak() throws Exception {
        JsonNode one = success(login(" ONE ", " SHARED@example.com ", PASSWORD_ONE));
        JsonNode two = success(login("two", "shared@example.com", PASSWORD_TWO));
        assertThat(one.at("/user/id").asText()).isEqualTo(USER_ONE.toString());
        assertThat(two.at("/user/id").asText()).isEqualTo(USER_TWO.toString());
        assertThat(strings(one.at("/user/roles"))).containsExactly("CUSTOMER_CARE", "MANAGER");
        assertThat(strings(one.at("/user/permissions"))).containsExactly("customer.read", "feedback.write", "identity.user.read");
        assertThat(one.toString()).doesNotContain("passwordHash", hashOne, PASSWORD_ONE);
        assertThat(one.get("expiresIn").asInt()).isEqualTo(900);
        assertThat(decoder.decode(one.get("accessToken").asText()).getClaimAsString("organizationId")).isEqualTo(ORG_ONE.toString());
        assertThat(jdbc.queryForList("SELECT token_hash FROM auth_refresh_tokens", String.class))
                .allSatisfy(hash -> assertThat(hash).hasSize(64).isNotEqualTo(one.get("refreshToken").asText()));
    }

    @Test
    void wrongTenantEmailPasswordAndDisabledAccountsShareCredentialError() throws Exception {
        assertCredentialError(login("missing", "shared@example.com", PASSWORD_ONE));
        assertCredentialError(login("one", "missing@example.com", PASSWORD_ONE));
        assertCredentialError(login("two", "shared@example.com", PASSWORD_ONE));
        assertCredentialError(login("one", "shared@example.com", "wrong-password"));
        jdbc.update("UPDATE users SET active = FALSE WHERE id = ?", USER_ONE);
        assertCredentialError(login("one", "shared@example.com", PASSWORD_ONE));
        jdbc.update("UPDATE users SET active = TRUE WHERE id = ?", USER_ONE);
        jdbc.update("UPDATE organizations SET active = FALSE WHERE id = ?", ORG_ONE);
        assertCredentialError(login("one", "shared@example.com", PASSWORD_ONE));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_sessions", Integer.class)).isZero();
    }

    @Test
    void ambiguousCaseFoldedEmailFailsClosed() throws Exception {
        jdbc.update("INSERT INTO users(id, organization_id, full_name, email, password_hash) VALUES (?, ?, ?, ?, ?)",
                id(14), ORG_ONE, "Duplicate", "SHARED@EXAMPLE.COM", hashOne);
        assertCredentialError(login("one", "shared@example.com", PASSWORD_ONE));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_sessions", Integer.class)).isZero();
    }

    @Test
    void multibytePasswordBeyondBcryptLimitReturnsCredentialError() throws Exception {
        assertCredentialError(login("one", "shared@example.com", "á".repeat(40)));
    }

    @Test
    void validatesInputAndMalformedJsonWithContractShape() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content("{\"email\":\"not-an-email\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty()).andExpect(jsonPath("$.traceId").isString());
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/v1/auth/refresh").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void meRequiresValidSignatureAndRequiredIdentityClaims() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"));
        JsonNode tokens = loginOne();
        me(tokens).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(USER_ONE.toString()));
        String access = tokens.get("accessToken").asText();
        String[] parts = access.split("\\.");
        parts[2] = (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + String.join(".", parts)))
                .andExpect(status().isUnauthorized());
        UUID session = UUID.fromString(decoder.decode(access).getClaimAsString("sessionId"));
        for (String forged : List.of(forge(session, ORG_TWO, USER_ONE, "pose-api"),
                forge(session, ORG_ONE, USER_TWO, "pose-api"), forge(UUID.randomUUID(), ORG_ONE, USER_ONE, "pose-api"),
                forge(session, ORG_ONE, USER_ONE, "other-issuer"))) {
            mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + forged)).andExpect(status().isUnauthorized());
        }
        JwtClaimsSet missing = JwtClaimsSet.builder().issuer("pose-api").subject(USER_ONE.toString())
                .issuedAt(START).expiresAt(START.plusSeconds(900)).build();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + sign(missing))).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesTokenAndReplayRevokesWholeSessionEvenAfter401() throws Exception {
        JsonNode first = loginOne(), second = success(refresh(first));
        assertThat(second.get("refreshToken").asText()).isNotEqualTo(first.get("refreshToken").asText());
        me(second).andExpect(status().isOk());
        refresh(first).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_INVALID_REFRESH_TOKEN"));
        refresh(second).andExpect(status().isUnauthorized());
        me(first).andExpect(status().isUnauthorized());
        me(second).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_sessions WHERE revoked_at IS NOT NULL", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'AUTH_REFRESH_REUSE_DETECTED'", Integer.class)).isEqualTo(1);
    }

    @Test
    void concurrentRefreshAllowsOnlyOneExchangeAndRevokesReplay() throws Exception {
        JsonNode first = loginOne();
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> exchange = () -> {
            start.await();
            try { auth.refresh(first.get("refreshToken").asText()); return true; }
            catch (AuthException exception) { return false; }
        };
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Boolean> a = executor.submit(exchange), b = executor.submit(exchange);
            start.countDown();
            assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        me(first).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_refresh_tokens", Integer.class)).isEqualTo(2);
    }

    @Test
    void logoutImmediatelyRevokesCurrentSessionAndKeepsOtherLoginActive() throws Exception {
        JsonNode first = loginOne(), other = loginOne();
        logout(first, first).andExpect(status().isNoContent());
        me(first).andExpect(status().isUnauthorized());
        refresh(first).andExpect(status().isUnauthorized());
        me(other).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'AUTH_LOGOUT'", Integer.class)).isEqualTo(1);
    }

    @Test
    void logoutCannotRevokeAnotherSessionOrTenant() throws Exception {
        JsonNode one = loginOne(), other = loginOne(), two = success(login("two", "shared@example.com", PASSWORD_TWO));
        logout(one, other).andExpect(status().isUnauthorized());
        logout(one, two).andExpect(status().isUnauthorized());
        me(one).andExpect(status().isOk());
        me(other).andExpect(status().isOk());
        me(two).andExpect(status().isOk());
    }

    @Test
    void expiredAccessTokenCanRefreshDespiteStaleBearerHeader() throws Exception {
        JsonNode first = loginOne();
        clock.now = START.plus(Duration.ofMinutes(16));
        me(first).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_TOKEN_EXPIRED"));
        JsonNode second = success(mvc.perform(post("/api/v1/auth/refresh").header("Authorization", bearer(first))
                .contentType("application/json").content(refreshBody(first))));
        me(second).andExpect(status().isOk());
    }

    @Test
    void refreshDoesNotExtendAbsoluteSevenDaySessionLifetime() throws Exception {
        JsonNode first = loginOne();
        clock.now = START.plus(Duration.ofDays(7)).minusSeconds(30);
        JsonNode second = success(refresh(first));
        assertThat(second.get("expiresIn").asInt()).isEqualTo(30);
        Instant expires = jdbc.queryForObject("SELECT expires_at FROM auth_sessions", Timestamp.class).toInstant();
        assertThat(expires).isEqualTo(START.plus(Duration.ofDays(7)));
        clock.now = expires;
        refresh(second).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"users", "organizations"})
    void disablingAccountOrOrganizationInvalidatesBothTokenTypes(String table) throws Exception {
        JsonNode first = loginOne();
        jdbc.update("UPDATE " + table + " SET active = FALSE WHERE id = ?", table.equals("users") ? USER_ONE : ORG_ONE);
        me(first).andExpect(status().isUnauthorized());
        refresh(first).andExpect(status().isUnauthorized());
    }

    @Test
    void currentDatabasePermissionsReplaceStaleJwtPermissions() throws Exception {
        JsonNode first = loginOne();
        mvc.perform(get("/api/v1/test/feedback").header("Authorization", bearer(first))).andExpect(status().isOk());
        jdbc.update("DELETE FROM user_roles WHERE user_id = ? AND role = 'CUSTOMER_CARE'", USER_ONE);
        me(first).andExpect(status().isOk()).andExpect(jsonPath("$.permissions.length()").value(2));
        mvc.perform(get("/api/v1/test/feedback").header("Authorization", bearer(first)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN_PERMISSION"));
        assertThat(strings(success(refresh(first)).at("/user/roles"))).containsExactly("MANAGER");
    }

    @Test
    void managerMembershipDoesNotGrantManagementScope() throws Exception {
        JsonNode tokens = loginOne();
        mvc.perform(get("/api/v1/test/team/" + TEAM_MANAGED).header("Authorization", bearer(tokens))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/test/team/" + TEAM_MEMBER_ONLY).header("Authorization", bearer(tokens)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN_SCOPE"));
    }

    @Test
    void futureEndedInactiveAndForeignTenantMembershipsAreExcluded() throws Exception {
        jdbc.update("DELETE FROM team_memberships");
        membership(TEAM_MANAGED, LocalDate.of(2026, 10, 2), null, true);
        membership(TEAM_MEMBER_ONLY, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), true);
        membership(TEAM_MEMBER_ONLY, LocalDate.of(2026, 9, 1), null, false);
        UUID foreignTeam = id(23);
        jdbc.update("INSERT INTO teams(id, organization_id, code, name) VALUES (?, ?, 'X', 'Foreign')", foreignTeam, ORG_TWO);
        membership(foreignTeam, LocalDate.of(2026, 9, 1), null, true);
        assertThat(strings(loginOne().at("/user/teamIds"))).containsExactly(TEAM_MANAGED.toString());
    }

    @Test
    void authAuditContainsScopeMetadataButNoSecretMaterial() throws Exception {
        JsonNode first = loginOne(), second = success(refresh(first));
        logout(second, second).andExpect(status().isNoContent());
        assertThat(jdbc.queryForList("SELECT action FROM audit_logs", String.class))
                .containsExactlyInAnyOrder("AUTH_LOGIN_SUCCEEDED", "AUTH_TOKEN_REFRESHED", "AUTH_LOGOUT");
        for (String value : jdbc.queryForList("SELECT CAST(new_value AS VARCHAR) FROM audit_logs", String.class)) {
            assertThat(value).contains("organizationId").doesNotContain(PASSWORD_ONE, hashOne,
                    first.get("refreshToken").asText(), first.get("accessToken").asText());
        }
    }

    private void grant(UUID org, String role, String permission) {
        jdbc.update("INSERT INTO role_permissions(organization_id, role_code, permission_code) VALUES (?, ?, ?)", org, role, permission);
    }
    private void membership(UUID team, LocalDate joined, LocalDate ended, boolean active) {
        jdbc.update("INSERT INTO team_memberships(id, team_id, user_id, joined_at, ended_at, active) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), team, USER_ONE, joined, ended, active);
    }
    private ResultActions login(String org, String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("organizationCode", org, "email", email, "password", password))));
    }
    private JsonNode loginOne() throws Exception { return success(login("one", "shared@example.com", PASSWORD_ONE)); }
    private JsonNode success(ResultActions result) throws Exception {
        return mapper.readTree(result.andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString());
    }
    private void assertCredentialError(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Thông tin đăng nhập không hợp lệ.")).andExpect(jsonPath("$.traceId").isString());
    }
    private String refreshBody(JsonNode tokens) throws Exception {
        return mapper.writeValueAsString(Map.of("refreshToken", tokens.get("refreshToken").asText()));
    }
    private String bearer(JsonNode tokens) { return "Bearer " + tokens.get("accessToken").asText(); }
    private ResultActions me(JsonNode tokens) throws Exception {
        return mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(tokens)));
    }
    private ResultActions refresh(JsonNode tokens) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh").contentType("application/json").content(refreshBody(tokens)));
    }
    private ResultActions logout(JsonNode access, JsonNode refresh) throws Exception {
        return mvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer(access))
                .contentType("application/json").content(refreshBody(refresh)));
    }
    private String forge(UUID session, UUID org, UUID user, String issuer) {
        return sign(JwtClaimsSet.builder().issuer(issuer).subject(user.toString()).issuedAt(START).expiresAt(START.plusSeconds(900))
                .claim("organizationId", org.toString()).claim("sessionId", session.toString()).build());
    }
    private String sign(JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
    private static List<String> strings(JsonNode values) {
        List<String> result = new ArrayList<>();
        values.forEach(value -> result.add(value.asText()));
        return result;
    }
    private static UUID id(int value) { return new UUID(0, value); }

    static class MutableClock extends Clock {
        volatile Instant now = START;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
    @TestConfiguration
    static class ClockConfiguration {
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
    }
    @RestController
    static class ProbeController {
        private final AuthorizationPolicy policy;
        private final JwtActorContextResolver actors;
        ProbeController(AuthorizationPolicy policy, JwtActorContextResolver actors) {
            this.policy = policy;
            this.actors = actors;
        }
        @GetMapping("/api/v1/test/feedback") @PreAuthorize("hasAuthority('feedback.write')")
        public String feedback() { return "ok"; }
        @GetMapping("/api/v1/test/team/{teamId}")
        public String team(@PathVariable UUID teamId) {
            var actor = actors.currentActor();
            policy.requireTeamScope(actor, actor.organizationId(), teamId);
            return "ok";
        }
    }
}
