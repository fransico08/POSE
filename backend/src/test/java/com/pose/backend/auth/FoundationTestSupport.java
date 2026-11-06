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
@Import({FoundationTestSupport.ClockConfiguration.class, AuthIntegrationTest.ProbeController.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class FoundationTestSupport {
    protected static final Instant START = Instant.parse("2026-10-01T00:00:00Z");
    protected static final UUID ORG_ONE = id(1), ORG_TWO = id(2);
    protected static final UUID USER_ONE = id(11), USER_TWO = id(12), USER_THREE = id(13);
    protected static final UUID TEAM_MANAGED = id(21), TEAM_MEMBER_ONLY = id(22);
    protected static final String PASSWORD_ONE = "tenant-one-password", PASSWORD_TWO = "tenant-two-password";
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
            if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:54329/pose_foundation_test_[a-z0-9_]+"))
                throw new IllegalArgumentException("Destructive regression tests require the isolated foundation test database");
            properties.add("spring.datasource.url", () -> url);
            properties.add("spring.datasource.username", () -> System.getenv("POSE_TEST_DATABASE_USERNAME"));
            properties.add("spring.datasource.password", () -> Optional.ofNullable(System.getenv("POSE_TEST_DATABASE_PASSWORD")).orElse(""));
            if ("true".equals(System.getenv("POSE_TEST_PRODUCTION_SCHEMA"))) {
                if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:54329/pose_foundation_test_[a-z0-9_]+"))
                    throw new IllegalArgumentException("Production-schema tests require the isolated foundation test database");
                properties.add("spring.flyway.locations", () -> "classpath:db/baseline,classpath:db/migration");
            }
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
        if (System.getenv("POSE_TEST_DATABASE_URL") == null) {
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS outbox_events (
                        id UUID PRIMARY KEY, organization_id UUID NOT NULL, aggregate_type VARCHAR(80) NOT NULL,
                        aggregate_id UUID NOT NULL, event_type VARCHAR(100) NOT NULL, payload JSONB NOT NULL,
                        idempotency_key VARCHAR(180) NOT NULL, delivery_status VARCHAR(30) NOT NULL,
                        retry_count INT NOT NULL, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        published_at TIMESTAMP WITH TIME ZONE, last_error VARCHAR(4000),
                        UNIQUE (organization_id, idempotency_key))
                    """);
        } else {
            jdbc.update("DELETE FROM workflow_executions");
        }
        jdbc.update("DELETE FROM outbox_events");
        for (String table : List.of("source_record_links", "source_systems", "feedbacks", "support_requests", "interactions", "crm_tasks", "opportunity_lines", "opportunities", "leads", "customer_assignments", "customers",
                "auth_refresh_tokens", "auth_sessions", "audit_logs", "sales_assignments",
                "offering_prices", "offerings", "offering_categories", "team_memberships", "teams", "departments",
                "role_permissions", "user_role_grants", "credentials", "permissions", "roles", "users", "organizations")) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("INSERT INTO organizations(id, code, name, created_at, updated_at) VALUES (?, 'one', 'Tenant One', ?, ?), (?, 'two', 'Tenant Two', ?, ?)",
                ORG_ONE, time(), time(), ORG_TWO, time(), time());
        user(USER_ONE, ORG_ONE, "User One", "shared@example.com", hashOne);
        user(USER_TWO, ORG_TWO, "User Two", "shared@example.com", hashTwo);
        user(USER_THREE, ORG_ONE, "Other Manager", "other@example.com", hashOne);
        for (UUID organization : List.of(ORG_ONE, ORG_TWO)) {
            for (String code : List.of("MANAGER", "CUSTOMER_CARE", "SALES_REP")) role(organization, code);
            jdbc.update("INSERT INTO departments(id,organization_id,code,name,created_at) VALUES (?,?,'DEFAULT','Default',?)",
                    department(organization), organization, time());
        }
        for (String code : List.of("identity.user.read", "customer.read", "feedback.write", "identity.role.manage")) permission(code);
        userRole(ORG_ONE, USER_ONE, "MANAGER");
        userRole(ORG_ONE, USER_ONE, "CUSTOMER_CARE");
        userRole(ORG_TWO, USER_TWO, "SALES_REP");
        grant(ORG_ONE, "MANAGER", "identity.user.read");
        grant(ORG_ONE, "MANAGER", "customer.read");
        grant(ORG_ONE, "CUSTOMER_CARE", "customer.read");
        grant(ORG_ONE, "CUSTOMER_CARE", "feedback.write");
        grant(ORG_TWO, "MANAGER", "identity.role.manage");
        grant(ORG_TWO, "SALES_REP", "customer.read");
        team(TEAM_MANAGED, ORG_ONE, "M", "Managed", USER_ONE);
        team(TEAM_MEMBER_ONLY, ORG_ONE, "N", "Member only", USER_THREE);
        membership(TEAM_MANAGED, LocalDate.of(2026, 9, 1), null, true);
        membership(TEAM_MEMBER_ONLY, LocalDate.of(2026, 9, 1), null, true);
    }

    protected Timestamp time() { return Timestamp.from(START.minusSeconds(1)); }
    protected UUID department(UUID org) { return new UUID(1, org.getLeastSignificantBits()); }
    protected void user(UUID id, UUID org, String name, String email, String hash) {
        jdbc.update("INSERT INTO users(id,organization_id,full_name,email,normalized_email,created_at,updated_at) VALUES (?,?,?,?,?,?,?)",
                id, org, name, email, email, time(), time());
        jdbc.update("INSERT INTO credentials(user_id,password_hash,password_changed_at,updated_at) VALUES (?,?,?,?)", id, hash, time(), time());
    }
    protected void role(UUID org, String code) {
        jdbc.update("INSERT INTO roles(id,organization_id,code,display_name,created_at) VALUES (?,?,?,?,?)", UUID.randomUUID(), org, code, code, time());
    }
    protected void permission(String code) {
        jdbc.update("INSERT INTO permissions(id,code,description,created_at) VALUES (?,?,?,?)", UUID.randomUUID(), code, code, time());
    }
    protected void userRole(UUID org, UUID user, String code) {
        jdbc.update("INSERT INTO user_role_grants(id,organization_id,user_id,role_id,granted_at) SELECT ?,?,?,id,? FROM roles WHERE organization_id=? AND code=?",
                UUID.randomUUID(), org, user, time(), org, code);
    }
    protected void grant(UUID org, String role, String permission) {
        jdbc.update("INSERT INTO role_permissions(role_id,permission_id,granted_at) SELECT r.id,p.id,? FROM roles r CROSS JOIN permissions p WHERE r.organization_id=? AND r.code=? AND p.code=?",
                time(), org, role, permission);
    }
    protected void team(UUID id, UUID org, String code, String name, UUID manager) {
        jdbc.update("INSERT INTO teams(id,organization_id,code,name,manager_user_id,department_id,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?)",
                id, org, code, name, manager, department(org), time(), time());
    }
    protected void membership(UUID team, LocalDate joined, LocalDate ended, boolean active) {
        jdbc.update("INSERT INTO team_memberships(id,organization_id,team_id,user_id,joined_at,left_at,status) VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID(), ORG_ONE, team, USER_ONE, Timestamp.from(joined.atStartOfDay(ZoneOffset.UTC).toInstant()),
                ended == null ? null : Timestamp.from(ended.atStartOfDay(ZoneOffset.UTC).toInstant()), active ? "ACTIVE" : "ENDED");
    }
    protected ResultActions login(String org, String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("organizationCode", org, "email", email, "password", password))));
    }
    protected JsonNode loginOne() throws Exception { return success(login("one", "shared@example.com", PASSWORD_ONE)); }
    protected JsonNode success(ResultActions result) throws Exception {
        return mapper.readTree(result.andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString());
    }
    protected void assertCredentialError(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Thông tin đăng nhập không hợp lệ.")).andExpect(jsonPath("$.traceId").isString());
    }
    protected String refreshBody(JsonNode tokens) throws Exception {
        return mapper.writeValueAsString(Map.of("refreshToken", tokens.get("refreshToken").asText()));
    }
    protected String bearer(JsonNode tokens) { return "Bearer " + tokens.get("accessToken").asText(); }
    protected ResultActions me(JsonNode tokens) throws Exception {
        return mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(tokens)));
    }
    protected ResultActions refresh(JsonNode tokens) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh").contentType("application/json").content(refreshBody(tokens)));
    }
    protected ResultActions logout(JsonNode access, JsonNode refresh) throws Exception {
        return mvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer(access))
                .contentType("application/json").content(refreshBody(refresh)));
    }
    protected String forge(UUID session, UUID org, UUID user, String issuer) {
        return sign(JwtClaimsSet.builder().issuer(issuer).subject(user.toString()).issuedAt(START).expiresAt(START.plusSeconds(900))
                .claim("organizationId", org.toString()).claim("sessionId", session.toString()).build());
    }
    protected String sign(JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
    protected static List<String> strings(JsonNode values) {
        List<String> result = new ArrayList<>();
        values.forEach(value -> result.add(value.asText()));
        return result;
    }
    protected static UUID id(int value) { return new UUID(0, value); }

    protected void admin() {
        role(ORG_ONE, "ADMIN");
        userRole(ORG_ONE, USER_ONE, "ADMIN");
        for (String code : List.of("organization.team.manage", "identity.user.manage", "identity.user.read", "identity.role.manage")) {
            if (jdbc.queryForObject("SELECT count(*) FROM permissions WHERE code=?", Integer.class, code) == 0) permission(code);
            grant(ORG_ONE, "ADMIN", code);
        }
        userRole(ORG_ONE, USER_THREE, "MANAGER");
    }
    protected void managerTeamPermission() {
        permission("organization.team.manage");
        grant(ORG_ONE, "MANAGER", "organization.team.manage");
    }
    protected ResultActions jsonRequest(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, JsonNode tokens, Object body) throws Exception {
        return mvc.perform(request.header("Authorization", bearer(tokens)).contentType("application/json").content(mapper.writeValueAsString(body)));
    }
    protected JsonNode response(ResultActions result, int expectedStatus) throws Exception {
        return mapper.readTree(result.andExpect(status().is(expectedStatus)).andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse().getContentAsString());
    }
    protected JsonNode auditSnapshot(String action, String column) throws Exception {
        if (!List.of("before_data", "after_data").contains(column)) throw new IllegalArgumentException("Invalid audit column");
        String value = jdbc.queryForObject("SELECT CAST(" + column + " AS VARCHAR) FROM audit_logs WHERE action=?", String.class, action);
        JsonNode node = mapper.readTree(value);
        return node.isTextual() ? mapper.readTree(node.asText()) : node;
    }

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
}
