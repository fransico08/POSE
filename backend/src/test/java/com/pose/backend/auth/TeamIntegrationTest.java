package com.pose.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TeamIntegrationTest extends FoundationTestSupport {
    @Test void renameAuditKeepsTeamIdentityAndChangedFieldsWithoutDescriptionOrMemberNames() throws Exception {
        managerTeamPermission(); JsonNode tokens=loginOne();
        jsonRequest(patch("/api/v1/teams/"+TEAM_MANAGED),tokens,Map.of("name","Renamed team","description","private team note")).andExpect(status().isOk());
        JsonNode before=auditSnapshot("TEAM_UPDATED","before_data"), after=auditSnapshot("TEAM_UPDATED","after_data");
        assertThat(before.get("name").asText()).isEqualTo("Managed");
        assertThat(after.get("name").asText()).isEqualTo("Renamed team");
        assertThat(before.get("code").asText()).isEqualTo(after.get("code").asText());
        assertThat(after.get("changedFields").toString()).contains("name","description");
        assertThat(before.has("description")).isFalse(); assertThat(after.has("description")).isFalse();
        assertThat(before.toString()+after).doesNotContain("private team note","fullName","password","token","hash","email");
        for (JsonNode member : after.get("members")) assertThat(member.fieldNames()).toIterable().containsOnly("id","status");
    }
    @Test void listAndDetailsEnforceTenantAndManagedScopeBeforePagination() throws Exception {
        mvc.perform(get("/api/v1/teams")).andExpect(status().isUnauthorized());
        JsonNode tokens=loginOne();
        JsonNode page=response(mvc.perform(get("/api/v1/teams").param("size","1").param("organizationId",ORG_TWO.toString()).header("Authorization",bearer(tokens))),200);
        assertThat(page.get("totalItems").asInt()).isEqualTo(1);
        assertThat(page.at("/items/0/id").asText()).isEqualTo(TEAM_MANAGED.toString());
        mvc.perform(get("/api/v1/teams").param("page","1").param("size","1").header("Authorization",bearer(tokens)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0)).andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get("/api/v1/teams/"+TEAM_MANAGED).header("Authorization",bearer(tokens))).andExpect(status().isOk());
        for(UUID denied:List.of(TEAM_MEMBER_ONLY,UUID.randomUUID()))
            mvc.perform(get("/api/v1/teams/"+denied).header("Authorization",bearer(tokens))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN_SCOPE"));
        mvc.perform(get("/api/v1/teams").param("size","101").header("Authorization",bearer(tokens))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/teams/not-a-uuid").header("Authorization",bearer(tokens))).andExpect(status().isBadRequest());
    }
    @Test void ordinaryMemberCanReadOnlyMembershipTeamsAndCannotManageEvenWithPermission() throws Exception {
        jdbc.update("UPDATE user_role_grants SET revoked_at=? WHERE user_id=? AND role_id IN (SELECT id FROM roles WHERE code='MANAGER')",java.sql.Timestamp.from(START),USER_ONE);
        permission("organization.team.manage"); grant(ORG_ONE,"CUSTOMER_CARE","organization.team.manage");
        JsonNode tokens=loginOne();
        mvc.perform(get("/api/v1/teams").header("Authorization",bearer(tokens))).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(2));
        jsonRequest(patch("/api/v1/teams/"+TEAM_MANAGED),tokens,Map.of("name","Forbidden"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN_PERMISSION"));
    }
    @Test void createUsesDepartmentAndManagerInCurrentTenantAndRejectsDuplicateCode() throws Exception {
        admin(); JsonNode tokens=loginOne();
        JsonNode created=response(jsonRequest(post("/api/v1/teams"),tokens,createBody("NEW_TEAM",USER_THREE,department(ORG_ONE))),201);
        assertThat(created.get("managerUserId").asText()).isEqualTo(USER_THREE.toString());
        assertThat(created.get("departmentId").asText()).isEqualTo(department(ORG_ONE).toString());
        assertThat(created.get("status").asText()).isEqualTo("ACTIVE");
        jsonRequest(post("/api/v1/teams"),tokens,createBody("new_team",USER_THREE,department(ORG_ONE)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TEAM_CODE_EXISTS"));
        jsonRequest(post("/api/v1/teams"),tokens,createBody("OTHER",USER_TWO,department(ORG_ONE))).andExpect(status().isBadRequest());
        jsonRequest(post("/api/v1/teams"),tokens,createBody("OTHER",USER_THREE,department(ORG_TWO))).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action='TEAM_CREATED'",Integer.class)).isEqualTo(1);
    }
    @Test void managerCanCreateOwnTeamAndOnlyPatchManagedTeamWithStrictWhitelist() throws Exception {
        managerTeamPermission(); userRole(ORG_ONE,USER_THREE,"MANAGER"); JsonNode tokens=loginOne();
        jsonRequest(post("/api/v1/teams"),tokens,createBody("OWN",USER_ONE,department(ORG_ONE))).andExpect(status().isCreated());
        jsonRequest(post("/api/v1/teams"),tokens,createBody("OUTSIDE",USER_THREE,department(ORG_ONE))).andExpect(status().isForbidden());
        jsonRequest(patch("/api/v1/teams/"+TEAM_MEMBER_ONLY),tokens,Map.of("name","Denied")).andExpect(status().isForbidden());
        jsonRequest(patch("/api/v1/teams/"+TEAM_MANAGED),tokens,Map.of("name"," Updated ","description"," Desc ")).andExpect(status().isOk());
        jsonRequest(patch("/api/v1/teams/"+TEAM_MANAGED),tokens,Map.of("name","Updated","description","Desc")).andExpect(status().isOk());
        for(Object body:List.of(Map.of(),Map.of("organizationId",ORG_TWO),Map.of("managerUserId",USER_THREE),Map.of("code","X"),Map.of("status","DELETED"),Map.of("name"," ")))
            jsonRequest(patch("/api/v1/teams/"+TEAM_MANAGED),tokens,body).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action='TEAM_UPDATED'",Integer.class)).isEqualTo(1);
    }
    @Test void memberReplacementPreservesHistoryIsIdempotentAndRejectsForeignTenant() throws Exception {
        admin(); JsonNode tokens=loginOne();
        jsonRequest(put("/api/v1/teams/"+TEAM_MANAGED+"/members"),tokens,Map.of("memberUserIds",List.of(USER_TWO))).andExpect(status().isBadRequest());
        jsonRequest(put("/api/v1/teams/"+TEAM_MANAGED+"/members"),tokens,Map.of("memberUserIds",List.of(USER_THREE,USER_THREE))).andExpect(status().isBadRequest());
        JsonNode members=response(jsonRequest(put("/api/v1/teams/"+TEAM_MANAGED+"/members"),tokens,Map.of("memberUserIds",List.of(USER_THREE))),200);
        assertThat(members.get(0).get("id").asText()).isEqualTo(USER_THREE.toString());
        jsonRequest(put("/api/v1/teams/"+TEAM_MANAGED+"/members"),tokens,Map.of("memberUserIds",List.of(USER_THREE))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM team_memberships WHERE team_id=? AND user_id=? AND status='ENDED' AND left_at IS NOT NULL",Integer.class,TEAM_MANAGED,USER_ONE)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action='TEAM_MEMBERS_CHANGED'",Integer.class)).isEqualTo(1);
    }
    @Test void managerCannotAddUserOutsideManagedTeams() throws Exception {
        managerTeamPermission(); JsonNode tokens=loginOne();
        jsonRequest(put("/api/v1/teams/"+TEAM_MANAGED+"/members"),tokens,Map.of("memberUserIds",List.of(USER_THREE)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN_SCOPE"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM team_memberships WHERE team_id=? AND status='ACTIVE'",Integer.class,TEAM_MANAGED)).isEqualTo(1);
    }
    @Test void inactiveTeamImmediatelyLosesManagerScopeButAdminCanReactivate() throws Exception {
        admin(); JsonNode admin=loginOne();
        JsonNode manager=success(login("one","other@example.com",PASSWORD_ONE));
        jsonRequest(patch("/api/v1/teams/"+TEAM_MEMBER_ONLY),admin,Map.of("status","INACTIVE")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/teams/"+TEAM_MEMBER_ONLY).header("Authorization",bearer(manager))).andExpect(status().isForbidden());
        jsonRequest(put("/api/v1/teams/"+TEAM_MEMBER_ONLY+"/members"),admin,Map.of("memberUserIds",List.of())).andExpect(status().isConflict());
        jsonRequest(patch("/api/v1/teams/"+TEAM_MEMBER_ONLY),admin,Map.of("status","ACTIVE")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/teams/"+TEAM_MEMBER_ONLY).header("Authorization",bearer(manager))).andExpect(status().isOk());
    }
    @Test void auditFailureRollsBackTeamAndMembershipChanges() throws Exception {
        admin(); JsonNode tokens=loginOne();
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT reject_team_audit CHECK(action NOT IN ('TEAM_UPDATED','TEAM_MEMBERS_CHANGED'))");
        try {
            jsonRequest(patch("/api/v1/teams/"+TEAM_MANAGED),tokens,Map.of("name","Rollback")).andExpect(status().isConflict());
            jsonRequest(put("/api/v1/teams/"+TEAM_MANAGED+"/members"),tokens,Map.of("memberUserIds",List.of(USER_THREE))).andExpect(status().isConflict());
            assertThat(jdbc.queryForObject("SELECT name FROM teams WHERE id=?",String.class,TEAM_MANAGED)).isEqualTo("Managed");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM team_memberships WHERE team_id=? AND user_id=? AND status='ACTIVE'",Integer.class,TEAM_MANAGED,USER_ONE)).isEqualTo(1);
        } finally { jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT reject_team_audit"); }
    }
    @Test void concurrentIdenticalMemberReplacementCreatesOneMembershipAndAudit() throws Exception {
        admin(); JsonNode tokens=loginOne(); CountDownLatch start=new CountDownLatch(1);
        Callable<Void> replace=()->{ start.await(); jsonRequest(put("/api/v1/teams/"+TEAM_MANAGED+"/members"),tokens,Map.of("memberUserIds",List.of(USER_THREE))).andExpect(status().isOk()); return null; };
        try(ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Void> a=executor.submit(replace),b=executor.submit(replace); start.countDown(); a.get(10,TimeUnit.SECONDS); b.get(10,TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM team_memberships WHERE team_id=? AND user_id=? AND status='ACTIVE'",Integer.class,TEAM_MANAGED,USER_THREE)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action='TEAM_MEMBERS_CHANGED'",Integer.class)).isEqualTo(1);
    }
    private Map<String,Object> createBody(String code,UUID manager,UUID department) {
        return Map.of("code",code,"name","New team","managerUserId",manager,"departmentId",department);
    }

    @Test void completeMemberReplacementAlsoCancelsOmittedFutureMembership() throws Exception {
        admin(); JsonNode tokens=loginOne();
        jdbc.update("INSERT INTO team_memberships(id,organization_id,team_id,user_id,joined_at,status) VALUES (?,?,?,?,?,'ACTIVE')",
                UUID.randomUUID(),ORG_ONE,TEAM_MANAGED,USER_THREE,java.sql.Timestamp.from(START.plusSeconds(100)));
        jsonRequest(put("/api/v1/teams/"+TEAM_MANAGED+"/members"),tokens,Map.of("memberUserIds",List.of(USER_ONE))).andExpect(status().isOk());
        clock.now=START.plusSeconds(100);
        mvc.perform(get("/api/v1/teams/"+TEAM_MANAGED).header("Authorization",bearer(tokens))).andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(1));
        assertThat(jdbc.queryForObject("SELECT status FROM team_memberships WHERE team_id=? AND user_id=?",String.class,TEAM_MANAGED,USER_THREE)).isEqualTo("ENDED");
    }
}
