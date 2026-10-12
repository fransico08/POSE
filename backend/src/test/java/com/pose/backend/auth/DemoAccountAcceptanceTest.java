package com.pose.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@EnabledIfEnvironmentVariable(named = "POSE_V2_SMOKE_DATABASE_URL",
        matches = "jdbc:postgresql://127\\.0\\.0\\.1:54329/pose_v2_review_[a-z0-9_]+")
@EnabledIfEnvironmentVariable(named = "POSE_DEMO_PASSWORD", matches = ".+")
@SpringBootTest(properties = {
        "pose.security.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "spring.jpa.hibernate.ddl-auto=none"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class DemoAccountAcceptanceTest {
    private static final UUID EDUCATION = UUID.fromString("0b47be39-3b3c-5195-a4e2-8a29ffbadcf0");
    private static final UUID CLINIC = UUID.fromString("68809a13-d597-5c34-a5d8-0d18d9c6cc7a");
    private static final Map<String, UUID> ORGANIZATIONS = Map.of(
            "EDU", EDUCATION,
            "REA", UUID.fromString("47dcbd1f-c1c3-5130-8596-58d76fd2676e"),
            "TECH", UUID.fromString("001ba809-bda6-52d8-8645-5e4ba33533b4"),
            "RET", UUID.fromString("83236f5d-2b8b-52bb-af64-ccde174487ab"),
            "LOG", UUID.fromString("a477cbf4-afb4-5960-b165-41537f66ac13"),
            "CLI", CLINIC);

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        RestoredV2SmokeTest.database(properties);
    }

    static Stream<Arguments> educationAccounts() {
        return Stream.of(
                Arguments.of("edu.admin@example.com", "ADMIN"),
                Arguments.of("edu.mgr@example.com", "MANAGER"),
                Arguments.of("edu.s01@example.com", "SALES_REP"),
                Arguments.of("edu.care@example.com", "CUSTOMER_CARE"),
                Arguments.of("edu.data@example.com", "DATA_STAFF"));
    }

    static Stream<Arguments> otherOrganizations() {
        return Stream.of(
                Arguments.of("RET", "ret.mgr@example.com"),
                Arguments.of("REA", "rea.mgr@example.com"),
                Arguments.of("TECH", "tech.mgr@example.com"),
                Arguments.of("LOG", "log.mgr@example.com"),
                Arguments.of("CLI", "cli.mgr@example.com"));
    }

    @Test
    void migrationHasAllSixPublishedLoginCodes() {
        Map<String, UUID> actual = new TreeMap<>();
        jdbc.query("SELECT code,id FROM organizations",
                (org.springframework.jdbc.core.RowCallbackHandler) row ->
                        actual.put(row.getString("code").toUpperCase(Locale.ROOT), row.getObject("id", UUID.class)));
        assertThat(actual).isEqualTo(ORGANIZATIONS);
    }

    @ParameterizedTest(name = "{0}: login and current permissions")
    @MethodSource("educationAccounts")
    void educationRolesLoginAndMeMatchExactDatabaseGrants(String email, String primaryRole) throws Exception {
        JsonNode tokens = login("EDU", email);
        JsonNode profile = successful(get("/api/v1/auth/me"), tokens, null, 200);
        assertProfile(profile, email, EDUCATION, primaryRole);
        assertThat(tokens.get("user")).isEqualTo(profile);
    }

    @Test
    void lowercaseOrganizationCodeAlsoLogsIn() throws Exception {
        JsonNode tokens = login("edu", "edu.mgr@example.com");
        assertProfile(successful(get("/api/v1/auth/me"), tokens, null, 200),
                "edu.mgr@example.com", EDUCATION, "MANAGER");
    }

    @Test
    void inactiveAccountHasSamePublicErrorAsWrongPassword() throws Exception {
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE organization_id=? AND email=?",
                String.class, CLINIC, "cli.admin@example.com")).isEqualTo("INACTIVE");
        JsonNode inactive = credentialError(loginResult("CLI", "cli.admin@example.com", demoPassword()));
        JsonNode wrongPassword = credentialError(loginResult("EDU", "edu.admin@example.com",
                demoPassword() + UUID.randomUUID()));
        assertThat(withoutTrace(inactive)).isEqualTo(withoutTrace(wrongPassword));
    }

    @Test
    void wrongOrganizationHasSamePublicErrorAsWrongPassword() throws Exception {
        JsonNode wrongOrganization = credentialError(loginResult("RET", "edu.mgr@example.com", demoPassword()));
        JsonNode wrongPassword = credentialError(loginResult("EDU", "edu.mgr@example.com",
                demoPassword() + UUID.randomUUID()));
        assertThat(withoutTrace(wrongOrganization)).isEqualTo(withoutTrace(wrongPassword));
    }

    @ParameterizedTest(name = "{0}: real demo manager login")
    @MethodSource("otherOrganizations")
    void everyOtherOrganizationHasAWorkingActiveAccount(String code, String email) throws Exception {
        JsonNode tokens = login(code, email);
        assertProfile(successful(get("/api/v1/auth/me"), tokens, null, 200),
                email, ORGANIZATIONS.get(code), "MANAGER");
    }

    @ParameterizedTest(name = "{1}: user creation authorization")
    @MethodSource("educationAccounts")
    void onlyAdminCanCreateUsers(String email, String primaryRole) throws Exception {
        JsonNode tokens = login("EDU", email);
        Map<String, Object> request = Map.of(
                "email", "acceptance-" + UUID.randomUUID() + "@example.com",
                "fullName", "Acceptance User",
                "initialPassword", demoPassword(),
                "roleCodes", List.of("SALES_REP"));
        int expectedStatus = primaryRole.equals("ADMIN") ? 201 : 403;
        MvcResult result = perform(post("/api/v1/users"), tokens, request);
        assertThat(result.getResponse().getStatus()).as("User creation by %s", primaryRole).isEqualTo(expectedStatus);
        JsonNode body = body(result);
        if (expectedStatus == 201) {
            assertThat(strings(body.get("roles"))).containsExactly("SALES_REP");
            assertThat(body.has("initialPassword")).isFalse();
            assertThat(body.has("passwordHash")).isFalse();
        } else {
            assertThat(body.get("code").asText()).isEqualTo("FORBIDDEN_PERMISSION");
        }
    }

    @Test
    void dataStaffCannotChangeOrganization() throws Exception {
        JsonNode tokens = login("EDU", "edu.data@example.com");
        MvcResult result = perform(patch("/api/v1/organization"), tokens, Map.of("name", "Must not change"));
        assertForbidden(result, "FORBIDDEN_PERMISSION");
        assertThat(jdbc.queryForObject("SELECT name FROM organizations WHERE id=?", String.class, EDUCATION))
                .isEqualTo("POSE Academy");
    }

    @Test
    void managerReadsManagedTeamAndCannotReadUnmanagedTeam() throws Exception {
        JsonNode tokens = login("EDU", "edu.mgr@example.com");
        UUID managerId = UUID.fromString(tokens.at("/user/id").asText());
        UUID managed = jdbc.queryForList(
                "SELECT id FROM teams WHERE organization_id=? AND manager_user_id=? AND status='ACTIVE' ORDER BY id",
                UUID.class, EDUCATION, managerId).getFirst();
        UUID unmanaged = jdbc.queryForList("""
                SELECT id FROM teams WHERE status='ACTIVE' AND (organization_id<>? OR manager_user_id<>?)
                ORDER BY CASE WHEN organization_id=? THEN 0 ELSE 1 END,id
                """, UUID.class, EDUCATION, managerId, EDUCATION).getFirst();
        JsonNode detail = successful(get("/api/v1/teams/" + managed), tokens, null, 200);
        assertThat(detail.get("id").asText()).isEqualTo(managed.toString());
        assertForbidden(perform(get("/api/v1/teams/" + unmanaged), tokens, null), "FORBIDDEN_SCOPE");
    }

    @Test
    void salesRepresentativeCannotReadTeamOutsideMembership() throws Exception {
        JsonNode tokens = login("EDU", "edu.s01@example.com");
        UUID userId = UUID.fromString(tokens.at("/user/id").asText());
        UUID outside = jdbc.queryForList("""
                SELECT t.id FROM teams t WHERE t.status='ACTIVE'
                  AND NOT EXISTS(SELECT 1 FROM team_memberships m WHERE m.team_id=t.id
                    AND m.organization_id=t.organization_id AND m.user_id=? AND m.status='ACTIVE'
                    AND m.joined_at<=CURRENT_TIMESTAMP AND (m.left_at IS NULL OR m.left_at>CURRENT_TIMESTAMP))
                ORDER BY CASE WHEN t.organization_id=? THEN 0 ELSE 1 END,t.id
                """, UUID.class, userId, EDUCATION).getFirst();
        assertForbidden(perform(get("/api/v1/teams/" + outside), tokens, null), "FORBIDDEN_SCOPE");
    }

    @Test
    void clinicManagerLoginMeAndListsDoNotRequireAnActiveAdmin() throws Exception {
        assertThat(jdbc.queryForObject("""
                SELECT count(DISTINCT u.id) FROM users u JOIN user_role_grants g
                  ON g.user_id=u.id AND g.organization_id=u.organization_id
                JOIN roles r ON r.id=g.role_id AND r.organization_id=u.organization_id
                WHERE u.organization_id=? AND r.code='ADMIN' AND g.revoked_at IS NULL
                """, Integer.class, CLINIC)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(DISTINCT u.id) FROM users u JOIN user_role_grants g
                  ON g.user_id=u.id AND g.organization_id=u.organization_id
                JOIN roles r ON r.id=g.role_id AND r.organization_id=u.organization_id
                WHERE u.organization_id=? AND r.code='ADMIN' AND u.status='ACTIVE'
                  AND u.archived_at IS NULL AND g.revoked_at IS NULL
                """, Integer.class, CLINIC)).isZero();
        JsonNode tokens = login("CLI", "cli.mgr@example.com");
        assertProfile(successful(get("/api/v1/auth/me"), tokens, null, 200),
                "cli.mgr@example.com", CLINIC, "MANAGER");
        successful(get("/api/v1/users"), tokens, null, 200);
        successful(get("/api/v1/teams"), tokens, null, 200);
        successful(get("/api/v1/organization"), tokens, null, 200);
        assertForbidden(perform(post("/api/v1/users"), tokens, Map.of(
                "email", "clinic-" + UUID.randomUUID() + "@example.com",
                "fullName", "Must not create",
                "initialPassword", demoPassword(),
                "roleCodes", List.of("SALES_REP"))), "FORBIDDEN_PERMISSION");
    }

    private JsonNode login(String code, String email) throws Exception {
        MvcResult result = loginResult(code, email, demoPassword());
        assertThat(result.getResponse().getStatus()).as("Demo login %s / %s", code, email).isEqualTo(200);
        return body(result);
    }

    private MvcResult loginResult(String code, String email, String password) throws Exception {
        return perform(post("/api/v1/auth/login"), null,
                Map.of("organizationCode", code, "email", email, "password", password));
    }

    private JsonNode successful(MockHttpServletRequestBuilder request, JsonNode tokens, Object payload, int status)
            throws Exception {
        MvcResult result = perform(request, tokens, payload);
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        return body(result);
    }

    private MvcResult perform(MockHttpServletRequestBuilder request, JsonNode tokens, Object payload) throws Exception {
        if (tokens != null) request.header("Authorization", "Bearer " + tokens.get("accessToken").asText());
        if (payload != null) request.contentType("application/json").content(mapper.writeValueAsString(payload));
        MvcResult result = mvc.perform(request).andReturn();
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        return result;
    }

    private JsonNode body(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private void assertProfile(JsonNode profile, String email, UUID organization, String primaryRole) {
        UUID userId = jdbc.queryForObject("SELECT id FROM users WHERE organization_id=? AND email=?",
                UUID.class, organization, email);
        assertThat(profile.get("id").asText()).isEqualTo(userId.toString());
        assertThat(profile.get("email").asText()).isEqualTo(email);
        assertThat(profile.at("/organization/id").asText()).isEqualTo(organization.toString());
        assertThat(profile.get("status").asText()).isEqualTo("ACTIVE");
        Set<String> expectedRoles = new TreeSet<>(jdbc.queryForList("""
                SELECT DISTINCT r.code FROM user_role_grants g JOIN roles r
                  ON r.id=g.role_id AND r.organization_id=g.organization_id
                WHERE g.organization_id=? AND g.user_id=? AND r.status='ACTIVE'
                  AND g.granted_at<=CURRENT_TIMESTAMP AND (g.revoked_at IS NULL OR g.revoked_at>CURRENT_TIMESTAMP)
                """, String.class, organization, userId));
        Set<String> expectedPermissions = new TreeSet<>(jdbc.queryForList("""
                SELECT DISTINCT p.code FROM user_role_grants g JOIN roles r
                  ON r.id=g.role_id AND r.organization_id=g.organization_id
                JOIN role_permissions rp ON rp.role_id=r.id JOIN permissions p ON p.id=rp.permission_id
                WHERE g.organization_id=? AND g.user_id=? AND r.status='ACTIVE'
                  AND g.granted_at<=CURRENT_TIMESTAMP AND (g.revoked_at IS NULL OR g.revoked_at>CURRENT_TIMESTAMP)
                """, String.class, organization, userId));
        assertThat(strings(profile.get("roles"))).contains(primaryRole).isEqualTo(expectedRoles);
        assertThat(strings(profile.get("permissions"))).isEqualTo(expectedPermissions);
    }

    private Set<String> strings(JsonNode values) {
        assertThat(values.isArray()).isTrue();
        Set<String> result = new TreeSet<>();
        values.forEach(value -> result.add(value.asText()));
        assertThat(result).hasSize(values.size());
        return result;
    }

    private JsonNode credentialError(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        JsonNode error = body(result);
        Set<String> fields = new TreeSet<>();
        error.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactly("code", "fieldErrors", "message", "traceId");
        assertThat(error.get("code").asText()).isEqualTo("AUTH_INVALID_CREDENTIALS");
        assertThat(error.get("fieldErrors").isArray()).isTrue();
        assertThat(error.get("fieldErrors").size()).isZero();
        assertThat(error.get("traceId").isTextual()).isTrue();
        return error;
    }

    private ObjectNode withoutTrace(JsonNode error) {
        ObjectNode result = error.deepCopy();
        result.remove("traceId");
        return result;
    }

    private void assertForbidden(MvcResult result, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(body(result).get("code").asText()).isEqualTo(code);
    }

    private String demoPassword() {
        String value = System.getenv("POSE_DEMO_PASSWORD");
        if (value == null || value.isBlank()) throw new IllegalStateException("POSE_DEMO_PASSWORD is required");
        return value;
    }
}
