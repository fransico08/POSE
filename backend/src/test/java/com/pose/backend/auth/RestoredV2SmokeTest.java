package com.pose.backend.auth;

import com.fasterxml.jackson.databind.*;
import com.pose.backend.task.TaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Separate from destructive fixture tests: every scenario rolls back; original demo passwords remain untouched.
@EnabledIfEnvironmentVariable(named="POSE_V2_SMOKE_DATABASE_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:54329/pose_v2_review_[a-z0-9_]+")
@SpringBootTest(properties={"pose.security.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=","spring.jpa.hibernate.ddl-auto=none"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@Transactional
class RestoredV2SmokeTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PasswordEncoder passwords;
    @Autowired AuthAccountRepository accounts;
    @Autowired TaskService tasks;
    private static final String TEST_PASSWORD="temporary-smoke-password";
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        String url=System.getenv("POSE_V2_SMOKE_DATABASE_URL");
        if(url==null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:54329/pose_v2_review_[a-z0-9_]+"))
            throw new IllegalArgumentException("Smoke tests require a dedicated restored review database");
        properties.add("spring.datasource.url",()->url);
        properties.add("spring.datasource.username",()->"pose_auth_test");
        properties.add("spring.datasource.password",()->"");
        // Explicit onboarding for this restored snapshot only. Production config never enables automatic baseline.
        properties.add("spring.flyway.baseline-on-migrate",()->"true");
        properties.add("spring.flyway.baseline-version",()->"1");
    }
    @Test void allOriginalDemoAccountsMapToCurrentTenantRolesAndCredentials() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM organizations",Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users",Integer.class)).isEqualTo(36);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customers",Integer.class)).isEqualTo(120);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leads",Integer.class)).isEqualTo(60);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM opportunities",Integer.class)).isEqualTo(72);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM opportunity_lines",Integer.class)).isEqualTo(72);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM (SELECT o.organization_id FROM opportunities o
                  JOIN opportunity_lines l ON l.organization_id=o.organization_id AND l.opportunity_id=o.id
                  GROUP BY o.organization_id HAVING sum(l.line_total) <> sum(o.expected_value)) differences
                """,Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM opportunities WHERE stage='WON' AND (final_value IS NULL OR final_value <> expected_value)",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customers WHERE customer_type IS NOT NULL OR address IS NOT NULL OR notes IS NOT NULL OR related_offering_id IS NOT NULL",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leads WHERE source IS NOT NULL OR notes IS NOT NULL",Integer.class)).isZero();
        jdbc.query("SELECT id,organization_id FROM users",(org.springframework.jdbc.core.RowCallbackHandler) row->{
            var account=accounts.findById(row.getObject("organization_id",UUID.class),row.getObject("id",UUID.class));
            assertThat(account).isPresent();
            assertThat(account.orElseThrow().roles()).isNotEmpty();
        });
    }
    @Test void legacyDemoOfferingsBelongToTheirOrganizationSalesTeam() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offerings",Integer.class)).isEqualTo(26);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM offerings o JOIN teams t ON t.id=o.team_id AND t.organization_id=o.organization_id
                WHERE t.code='SALES' AND t.status='ACTIVE'
                """,Integer.class)).isEqualTo(26);
    }
    @Test void reportRestoredLeadConversionCountsWithoutPersonalData() {
        int qualified=jdbc.queryForObject("SELECT count(*) FROM leads WHERE status='QUALIFIED'",Integer.class);
        int matched=jdbc.queryForObject("""
                SELECT count(*) FROM leads l WHERE l.status='QUALIFIED' AND EXISTS
                (SELECT 1 FROM customers c WHERE c.organization_id=l.organization_id
                 AND ((l.normalized_email IS NOT NULL AND c.normalized_email=l.normalized_email)
                   OR (l.normalized_phone IS NOT NULL AND c.normalized_phone=l.normalized_phone)))
                """,Integer.class);
        int differentOwner=jdbc.queryForObject("""
                SELECT count(*) FROM leads l WHERE l.status='QUALIFIED' AND EXISTS
                (SELECT 1 FROM opportunities o WHERE o.organization_id=l.organization_id AND o.lead_id=l.id
                 AND o.stage IN ('NEW','QUALIFIED','PROPOSAL') AND o.owner_user_id<>l.owner_user_id)
                """,Integer.class);
        int openTasks=jdbc.queryForObject("SELECT count(*) FROM crm_tasks WHERE status IN ('OPEN','IN_PROGRESS') AND opportunity_id IS NOT NULL",Integer.class);
        System.out.println("RESTORE_CONVERSION_COUNTS qualified="+qualified+" matched="+matched+" different_owner="+differentOwner+" open_opportunity_tasks="+openTasks);
    }
    @Test void careOnlyOwnersCanReadTheirRestoredTasks() {
        List<Map<String,Object>> records=jdbc.queryForList("""
                SELECT t.id,t.organization_id,t.owner_user_id FROM crm_tasks t
                JOIN users u ON u.id=t.owner_user_id AND u.organization_id=t.organization_id
                WHERE u.status='ACTIVE' AND u.archived_at IS NULL
                  AND EXISTS (SELECT 1 FROM user_role_grants g JOIN roles r ON r.id=g.role_id AND r.organization_id=g.organization_id
                    WHERE g.organization_id=u.organization_id AND g.user_id=u.id AND r.code='CUSTOMER_CARE' AND r.status='ACTIVE'
                      AND g.granted_at<=CURRENT_TIMESTAMP AND (g.revoked_at IS NULL OR g.revoked_at>CURRENT_TIMESTAMP))
                  AND NOT EXISTS (SELECT 1 FROM user_role_grants g JOIN roles r ON r.id=g.role_id AND r.organization_id=g.organization_id
                    WHERE g.organization_id=u.organization_id AND g.user_id=u.id AND r.code<>'CUSTOMER_CARE' AND r.status='ACTIVE'
                      AND g.granted_at<=CURRENT_TIMESTAMP AND (g.revoked_at IS NULL OR g.revoked_at>CURRENT_TIMESTAMP))
                """);
        int visible=0;
        for (Map<String,Object> row:records) {
            UUID org=(UUID)row.get("organization_id"), owner=(UUID)row.get("owner_user_id"), id=(UUID)row.get("id");
            var account=accounts.findById(org,owner).orElseThrow();
            if (tasks.get(account.actor(),id)!=null) visible++;
        }
        assertThat(records.size()).isEqualTo(25);
        assertThat(visible).isEqualTo(records.size());
        System.out.println("RESTORE_CARE_TASK_COUNTS total="+records.size()+" visible="+visible);
    }
    @Test void foundationWorkflowRunsOnRestoredDemoSchemaWithoutChangingOriginalPasswords() throws Exception {
        UUID org=jdbc.queryForObject("SELECT id FROM organizations ORDER BY name LIMIT 1",UUID.class);
        String code=jdbc.queryForObject("SELECT code FROM organizations WHERE id=?",String.class,org);
        UUID admin=UUID.randomUUID(); Timestamp now=Timestamp.from(Instant.now()); String email="smoke-"+admin+"@example.com";
        jdbc.update("INSERT INTO users(id,organization_id,full_name,email,normalized_email,created_at,updated_at) VALUES (?,?,?,?,?,?,?)",admin,org,"Smoke Admin",email,email,now,now);
        jdbc.update("INSERT INTO credentials(user_id,password_hash,password_changed_at,updated_at) VALUES (?,?,?,?)",admin,passwords.encode(TEST_PASSWORD),now,now);
        jdbc.update("INSERT INTO user_role_grants(id,organization_id,user_id,role_id,granted_at) SELECT ?,?,?,id,? FROM roles WHERE organization_id=? AND code='ADMIN'",UUID.randomUUID(),org,admin,now,org);
        JsonNode adminTokens=response(request(post("/api/v1/auth/login"),null,Map.of("organizationCode",code,"email",email,"password",TEST_PASSWORD)),200);
        mvc.perform(get("/api/v1/organization").header("Authorization",bearer(adminTokens))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(code));
        request(patch("/api/v1/organization"),adminTokens,Map.of("industry","Smoke Test")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/users").header("Authorization",bearer(adminTokens))).andExpect(status().isOk());
        UUID manager=jdbc.queryForObject("SELECT t.manager_user_id FROM teams t WHERE t.organization_id=? AND t.status='ACTIVE' ORDER BY t.id LIMIT 1",UUID.class,org);
        UUID department=jdbc.queryForObject("SELECT id FROM departments WHERE organization_id=? AND status='ACTIVE' ORDER BY id LIMIT 1",UUID.class,org);
        JsonNode team=response(request(post("/api/v1/teams"),adminTokens,Map.of("code","SMOKE_"+admin.toString().substring(0,8),"name","Smoke Team","managerUserId",manager,"departmentId",department)),201);
        String teamId=team.get("id").asText();
        request(patch("/api/v1/teams/"+teamId),adminTokens,Map.of("name","Smoke Updated")).andExpect(status().isOk());
        String userEmail="new-"+admin+"@example.com";
        JsonNode user=response(request(post("/api/v1/users"),adminTokens,Map.of("email",userEmail,"fullName","Smoke User","initialPassword",TEST_PASSWORD,"roleCodes",List.of("CUSTOMER_CARE"),"teamIds",List.of(teamId))),201);
        String userId=user.get("id").asText();
        request(put("/api/v1/teams/"+teamId+"/members"),adminTokens,Map.of("memberUserIds",List.of(userId,manager.toString()))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/teams/"+teamId).header("Authorization",bearer(adminTokens))).andExpect(status().isOk()).andExpect(jsonPath("$.members.length()").value(2));
        request(patch("/api/v1/users/"+userId),adminTokens,Map.of("fullName","Updated User")).andExpect(status().isOk());
        request(put("/api/v1/users/"+userId+"/roles"),adminTokens,Map.of("roleCodes",List.of("SALES_REP"))).andExpect(status().isOk());
        request(put("/api/v1/roles/SALES_REP/permissions"),adminTokens,Map.of("permissionCodes",List.of("customer.read"))).andExpect(status().isOk());
        JsonNode userTokens=response(request(post("/api/v1/auth/login"),null,Map.of("organizationCode",code,"email",userEmail,"password",TEST_PASSWORD)),200);
        mvc.perform(get("/api/v1/auth/me").header("Authorization",bearer(userTokens))).andExpect(status().isOk()).andExpect(jsonPath("$.permissions.length()").value(1));
        JsonNode rotated=response(request(post("/api/v1/auth/refresh"),null,Map.of("refreshToken",userTokens.get("refreshToken").asText())),200);
        request(post("/api/v1/auth/logout"),rotated,Map.of("refreshToken",rotated.get("refreshToken").asText())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/me").header("Authorization",bearer(rotated))).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE actor_user_id=? AND result='SUCCESS'",Integer.class,admin)).isGreaterThanOrEqualTo(7);
    }
    private ResultActions request(MockHttpServletRequestBuilder builder,JsonNode token,Object body) throws Exception {
        if(token!=null) builder.header("Authorization",bearer(token));
        return mvc.perform(builder.contentType("application/json").content(mapper.writeValueAsString(body)));
    }
    private JsonNode response(ResultActions result,int status) throws Exception { return mapper.readTree(result.andExpect(status().is(status)).andReturn().getResponse().getContentAsString()); }
    private String bearer(JsonNode token) { return "Bearer "+token.get("accessToken").asText(); }
}
