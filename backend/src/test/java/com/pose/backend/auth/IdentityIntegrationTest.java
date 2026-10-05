package com.pose.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class IdentityIntegrationTest extends FoundationTestSupport {
    private static final String NEW_PASSWORD=" new-user-password ";
    @Test void userReadsFilterManagedTeamBeforeSearchAndPaginationAndHideForeignRecords() throws Exception {
        mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
        JsonNode tokens=loginOne();
        mvc.perform(get("/api/v1/users").header("Authorization",bearer(tokens))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1)).andExpect(jsonPath("$.items[0].id").value(USER_ONE.toString()));
        mvc.perform(get("/api/v1/users").param("search","Other").header("Authorization",bearer(tokens)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
        for(UUID target:List.of(USER_THREE,USER_TWO,UUID.randomUUID()))
            mvc.perform(get("/api/v1/users/"+target).header("Authorization",bearer(tokens))).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN_SCOPE"));
        jdbc.update("DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE organization_id=?)",ORG_ONE);
        mvc.perform(get("/api/v1/users/"+USER_ONE).header("Authorization",bearer(tokens))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/users").header("Authorization",bearer(tokens))).andExpect(status().isForbidden());
    }
    @Test void adminReadsTenantUsersOnlyAndRoleCatalogDoesNotExposePermissions() throws Exception {
        admin(); JsonNode tokens=loginOne();
        mvc.perform(get("/api/v1/users").param("size","1").param("page","1").header("Authorization",bearer(tokens)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(2)).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get("/api/v1/users").param("size","0").header("Authorization",bearer(tokens))).andExpect(status().isBadRequest());
        JsonNode roles=response(mvc.perform(get("/api/v1/roles").header("Authorization",bearer(tokens))),200);
        assertThat(roles.toString()).doesNotContain("permissions","permissionCodes","identity.role.manage");
        mvc.perform(get("/api/v1/permissions").header("Authorization",bearer(tokens))).andExpect(status().isOk());
    }
    @Test void createHashesExactPasswordNormalizesEmailGrantsRolesAndNeverAuditsCredentials() throws Exception {
        admin(); JsonNode tokens=loginOne(); JsonNode created=create(tokens," New@Example.com ","CUSTOMER_CARE");
        UUID id=UUID.fromString(created.get("id").asText());
        assertThat(created.get("email").asText()).isEqualTo("new@example.com");
        assertThat(created.toString()).doesNotContain("password","credential",NEW_PASSWORD);
        String hash=jdbc.queryForObject("SELECT password_hash FROM credentials WHERE user_id=?",String.class,id);
        assertThat(passwords.matches(NEW_PASSWORD,hash)).isTrue();
        JsonNode newTokens=success(login("one","NEW@EXAMPLE.COM",NEW_PASSWORD));
        assertThat(strings(newTokens.at("/user/roles"))).containsExactly("CUSTOMER_CARE");
        assertThat(strings(newTokens.at("/user/teamIds"))).containsExactly(TEAM_MANAGED.toString());
        for(String json:jdbc.queryForList("SELECT CAST(after_data AS VARCHAR) FROM audit_logs WHERE action='USER_CREATED'",String.class))
            assertThat(json).doesNotContain(NEW_PASSWORD,hash,"password","refreshToken");
        jsonRequest(post("/api/v1/users"),tokens,userBody("NEW@example.com","SALES_REP")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_EMAIL_EXISTS"));
    }
    @Test void createRejectsUnauthorizedInvalidRolesForeignTeamsAndUnknownPayloadFields() throws Exception {
        JsonNode manager=loginOne();
        jsonRequest(post("/api/v1/users"),manager,userBody("new@example.com","CUSTOMER_CARE")).andExpect(status().isForbidden());
        admin(); JsonNode tokens=loginOne();
        for(String code:List.of("CUSTOMER","SUPER_ADMIN"))
            jsonRequest(post("/api/v1/users"),tokens,userBody("new@example.com",code)).andExpect(status().isBadRequest());
        Map<String,Object> body=new HashMap<>(userBody("new@example.com","CUSTOMER_CARE"));
        body.put("teamIds",List.of(UUID.randomUUID())); jsonRequest(post("/api/v1/users"),tokens,body).andExpect(status().isBadRequest());
        body.put("teamIds",List.of(TEAM_MANAGED)); body.put("organizationId",ORG_TWO); jsonRequest(post("/api/v1/users"),tokens,body).andExpect(status().isBadRequest());
        body.remove("organizationId"); body.put("initialPassword","á".repeat(40)); jsonRequest(post("/api/v1/users"),tokens,body).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users",Integer.class)).isEqualTo(3);
    }
    @Test void statusPatchRevokesSessionsAndCannotDisableSelfOrActiveTeamManager() throws Exception {
        admin(); JsonNode tokens=loginOne(); JsonNode created=create(tokens,"new@example.com","CUSTOMER_CARE");
        UUID id=UUID.fromString(created.get("id").asText()); JsonNode user=success(login("one","new@example.com",NEW_PASSWORD));
        jsonRequest(patch("/api/v1/users/"+id),tokens,Map.of("status","INACTIVE","fullName","Changed","metadata",Map.of("locale","vi-VN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
        me(user).andExpect(status().isUnauthorized()); refresh(user).andExpect(status().isUnauthorized());
        jsonRequest(patch("/api/v1/users/"+USER_ONE),tokens,Map.of("status","INACTIVE")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_SELF_PRIVILEGE_ESCALATION"));
        jsonRequest(patch("/api/v1/users/"+USER_THREE),tokens,Map.of("status","INACTIVE")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_TEAM_MANAGER_REQUIRED"));
        for(Object body:List.of(Map.of("email","new@other.com"),Map.of("metadata",Map.of("accessToken","x")),Map.of("status","DELETED"),Map.of()))
            jsonRequest(patch("/api/v1/users/"+id),tokens,body).andExpect(status().isBadRequest());
    }
    @Test void roleChangePreservesHistoryUpdatesOldTokenAndRejectsSelfEscalationAndManagerRemoval() throws Exception {
        admin(); JsonNode admin=loginOne(); JsonNode created=create(admin,"new@example.com","CUSTOMER_CARE");
        UUID id=UUID.fromString(created.get("id").asText()); JsonNode user=success(login("one","new@example.com",NEW_PASSWORD));
        clock.now=START.plusSeconds(1);
        jsonRequest(put("/api/v1/users/"+id+"/roles"),admin,Map.of("roleCodes",List.of("SALES_REP"))).andExpect(status().isOk());
        me(user).andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("SALES_REP"));
        mvc.perform(get("/api/v1/test/feedback").header("Authorization",bearer(user))).andExpect(status().isForbidden());
        jsonRequest(put("/api/v1/users/"+id+"/roles"),admin,Map.of("roleCodes",List.of("SALES_REP"))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_role_grants WHERE user_id=? AND revoked_at IS NOT NULL",Integer.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action='USER_ROLES_CHANGED'",Integer.class)).isEqualTo(1);
        jsonRequest(put("/api/v1/users/"+USER_ONE+"/roles"),admin,Map.of("roleCodes",List.of("ADMIN"))).andExpect(status().isForbidden());
        jsonRequest(put("/api/v1/users/"+USER_THREE+"/roles"),admin,Map.of("roleCodes",List.of("SALES_REP"))).andExpect(status().isConflict());
        jsonRequest(put("/api/v1/users/"+USER_TWO+"/roles"),admin,Map.of("roleCodes",List.of("SALES_REP"))).andExpect(status().isForbidden());
    }
    @Test void rolePermissionChangesAreTenantScopedImmediatelyEffectiveAndCannotChangeHeldRole() throws Exception {
        admin(); JsonNode tokens=loginOne(); create(tokens,"new@example.com","SALES_REP");
        JsonNode user=success(login("one","new@example.com",NEW_PASSWORD));
        mvc.perform(get("/api/v1/test/feedback").header("Authorization",bearer(user))).andExpect(status().isForbidden());
        jsonRequest(put("/api/v1/roles/SALES_REP/permissions"),tokens,Map.of("permissionCodes",List.of("feedback.write"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/test/feedback").header("Authorization",bearer(user))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM role_permissions rp JOIN roles r ON r.id=rp.role_id JOIN permissions p ON p.id=rp.permission_id WHERE r.organization_id=? AND r.code='SALES_REP' AND p.code='customer.read'",Integer.class,ORG_TWO)).isEqualTo(1);
        jsonRequest(put("/api/v1/roles/SALES_REP/permissions"),tokens,Map.of("permissionCodes",List.of())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/test/feedback").header("Authorization",bearer(user))).andExpect(status().isForbidden());
        jsonRequest(put("/api/v1/roles/ADMIN/permissions"),tokens,Map.of("permissionCodes",List.of("feedback.write"))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_SELF_PRIVILEGE_ESCALATION"));
        jsonRequest(put("/api/v1/roles/SALES_REP/permissions"),tokens,Map.of("permissionCodes",List.of("identity.role.manage"))).andExpect(status().isForbidden());
        jsonRequest(put("/api/v1/roles/SALES_REP/permissions"),tokens,Map.of("permissionCodes",List.of("invented.permission"))).andExpect(status().isBadRequest());
    }
    @Test void managerCannotAdministerRolesEvenIfAccidentallyGrantedRoleManage() throws Exception {
        grant(ORG_ONE,"MANAGER","identity.role.manage"); JsonNode tokens=loginOne();
        mvc.perform(get("/api/v1/permissions").header("Authorization",bearer(tokens))).andExpect(status().isForbidden());
        jsonRequest(put("/api/v1/users/"+USER_THREE+"/roles"),tokens,Map.of("roleCodes",List.of("ADMIN"))).andExpect(status().isForbidden());
        jsonRequest(put("/api/v1/roles/SALES_REP/permissions"),tokens,Map.of("permissionCodes",List.of("feedback.write"))).andExpect(status().isForbidden());
    }
    @Test void userCreateAndRoleMutationRollBackWhenAuditFails() throws Exception {
        admin(); JsonNode tokens=loginOne(); JsonNode created=create(tokens,"old@example.com","SALES_REP"); UUID id=UUID.fromString(created.get("id").asText());
        jdbc.update("DELETE FROM audit_logs WHERE action='USER_CREATED'");
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT reject_identity_audit CHECK(action NOT IN ('USER_CREATED','USER_ROLES_CHANGED','ROLE_PERMISSIONS_CHANGED'))");
        try {
            jsonRequest(post("/api/v1/users"),tokens,userBody("rollback@example.com","CUSTOMER_CARE")).andExpect(status().isConflict());
            jsonRequest(put("/api/v1/users/"+id+"/roles"),tokens,Map.of("roleCodes",List.of("CUSTOMER_CARE"))).andExpect(status().isConflict());
            jsonRequest(put("/api/v1/roles/SALES_REP/permissions"),tokens,Map.of("permissionCodes",List.of("feedback.write"))).andExpect(status().isConflict());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email='rollback@example.com'",Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT r.code FROM user_role_grants g JOIN roles r ON r.id=g.role_id WHERE g.user_id=? AND g.revoked_at IS NULL",String.class,id)).isEqualTo("SALES_REP");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM role_permissions rp JOIN roles r ON r.id=rp.role_id WHERE r.organization_id=? AND r.code='SALES_REP'",Integer.class,ORG_ONE)).isZero();
        } finally { jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT reject_identity_audit"); }
    }
    @Test void concurrentUserCreateAcceptsOneNormalizedEmailAndOneCredential() throws Exception {
        admin(); JsonNode tokens=loginOne(); CountDownLatch start=new CountDownLatch(1);
        Callable<Integer> create=()->{start.await();return jsonRequest(post("/api/v1/users"),tokens,userBody("same@example.com","SALES_REP")).andReturn().getResponse().getStatus();};
        try(ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Integer> a=executor.submit(create),b=executor.submit(create);start.countDown();
            assertThat(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE normalized_email='same@example.com'",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM credentials c JOIN users u ON u.id=c.user_id WHERE u.normalized_email='same@example.com'",Integer.class)).isEqualTo(1);
    }
    @Test void futureRevokedAndInactiveRolesDoNotGivePermissionsOrManagementScope() throws Exception {
        jdbc.update("UPDATE user_role_grants SET granted_at=? WHERE user_id=? AND role_id IN (SELECT id FROM roles WHERE code='CUSTOMER_CARE')",Timestamp.from(START.plusSeconds(10)),USER_ONE);
        JsonNode tokens=loginOne(); assertThat(strings(tokens.at("/user/roles"))).containsExactly("MANAGER");
        jdbc.update("UPDATE roles SET status='INACTIVE' WHERE organization_id=? AND code='MANAGER'",ORG_ONE);
        me(tokens).andExpect(status().isOk()).andExpect(jsonPath("$.roles.length()").value(0)).andExpect(jsonPath("$.permissions.length()").value(0));
        mvc.perform(get("/api/v1/teams").header("Authorization",bearer(tokens))).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(2));
        // Losing MANAGER falls back to ordinary membership reads, never management permission.
        jsonRequest(patch("/api/v1/teams/"+TEAM_MANAGED),tokens,Map.of("name","Denied")).andExpect(status().isForbidden());
    }
    private Map<String,Object> userBody(String email,String role) {
        return Map.of("email",email,"fullName","New User","initialPassword",NEW_PASSWORD,"roleCodes",List.of(role),"teamIds",List.of(TEAM_MANAGED));
    }

    @Test void roleReplacementEndsFutureRevocationGrantImmediatelyAndCancelsOmittedFutureRole() throws Exception {
        admin(); JsonNode tokens=loginOne(); UUID id=UUID.fromString(create(tokens,"future@example.com","CUSTOMER_CARE").get("id").asText());
        jdbc.update("UPDATE user_role_grants SET revoked_at=? WHERE user_id=?",Timestamp.from(START.plusSeconds(100)),id);
        clock.now=START.plusSeconds(1);
        jsonRequest(put("/api/v1/users/"+id+"/roles"),tokens,Map.of("roleCodes",List.of("SALES_REP"))).andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("SALES_REP"));
        jdbc.update("INSERT INTO user_role_grants(id,organization_id,user_id,role_id,granted_at) SELECT ?,?,?,id,? FROM roles WHERE organization_id=? AND code='CUSTOMER_CARE'",
                UUID.randomUUID(),ORG_ONE,id,Timestamp.from(START.plusSeconds(100)),ORG_ONE);
        jsonRequest(put("/api/v1/users/"+id+"/roles"),tokens,Map.of("roleCodes",List.of("SALES_REP"))).andExpect(status().isOk());
        clock.now=START.plusSeconds(100);
        mvc.perform(get("/api/v1/users/"+id).header("Authorization",bearer(tokens))).andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(1)).andExpect(jsonPath("$.roles[0]").value("SALES_REP"));
    }
    private JsonNode create(JsonNode tokens,String email,String role) throws Exception { return response(jsonRequest(post("/api/v1/users"),tokens,userBody(email,role)),201); }
}
