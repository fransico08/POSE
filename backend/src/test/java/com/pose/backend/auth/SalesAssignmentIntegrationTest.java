package com.pose.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.salesassignment.SalesAssignmentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties = "pose.sales-assignment.expiry-scan-delay-ms=86400000")
class SalesAssignmentIntegrationTest extends FoundationTestSupport {
    private static final UUID CATEGORY = id(301), OFFER = id(302), SALE_A = id(303), SALE_B = id(304), ASSIGNMENT = id(305);
    @Autowired SalesAssignmentService service;

    @BeforeEach void fixtures() {
        for (String p : List.of("offering.read", "offering.manage", "sales_assignment.manage")) {
            if (jdbc.queryForObject("SELECT count(*) FROM permissions WHERE code=?", Integer.class, p) == 0) permission(p);
        }
        grant(ORG_ONE, "MANAGER", "sales_assignment.manage");
        grant(ORG_ONE, "MANAGER", "offering.manage");
        grant(ORG_ONE, "SALES_REP", "offering.read");
        user(SALE_A, ORG_ONE, "Sale A", "sale-a@example.test", hashOne);
        user(SALE_B, ORG_ONE, "Sale B", "sale-b@example.test", hashOne);
        userRole(ORG_ONE, SALE_A, "SALES_REP"); userRole(ORG_ONE, SALE_B, "SALES_REP");
        member(id(306), TEAM_MANAGED, SALE_A); member(id(307), TEAM_MEMBER_ONLY, SALE_B);
        jdbc.update("INSERT INTO offering_categories(id,organization_id,code,name,status,created_at,updated_at) VALUES (?,?,?,?,'ACTIVE',?,?)", CATEGORY, ORG_ONE, "CAT", "Category", time(), time());
        offering(OFFER, TEAM_MANAGED, "ACTIVE");
    }
    @AfterEach void removeOpenWork() {
        jdbc.update("DELETE FROM crm_tasks"); jdbc.update("DELETE FROM opportunities"); jdbc.update("DELETE FROM leads");
    }
    private void member(UUID id, UUID team, UUID user) {
        jdbc.update("INSERT INTO team_memberships(id,organization_id,team_id,user_id,joined_at,status) VALUES (?,?,?,?,?,'ACTIVE')", id, ORG_ONE, team, user, time());
    }
    private void offering(UUID id, UUID team, String status) {
        jdbc.update("INSERT INTO offerings(id,organization_id,category_id,team_id,code,name,kind,status,created_at,updated_at) VALUES (?,?,?,?,?,?,'SERVICE',?,?,?)", id, ORG_ONE, CATEGORY, team, "CODE-"+id, "Offering "+id, status, time(), time());
    }
    private void assignment(UUID id, UUID offering, UUID user, Instant from, Instant to, String status) {
        jdbc.update("INSERT INTO sales_assignments(id,organization_id,offering_id,sales_user_id,assigned_by_user_id,status,effective_from,effective_to,revoked_at,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)", id, ORG_ONE, offering, user, USER_ONE, status, Timestamp.from(from), to == null ? null : Timestamp.from(to), status.equals("REVOKED") ? time() : null, time(), time());
    }
    private JsonNode saleA() throws Exception { return success(login("one", "sale-a@example.test", PASSWORD_ONE)); }
    private JsonNode saleB() throws Exception { return success(login("one", "sale-b@example.test", PASSWORD_ONE)); }
    private Map<String,Object> createBody(UUID offering, UUID sale, Instant from, Instant to) {
        var body = new java.util.LinkedHashMap<String,Object>(); body.put("offeringId", offering); body.put("salesUserId", sale); body.put("effectiveFrom", from.toString()); body.put("effectiveTo", to == null ? null : to.toString()); return body;
    }
    private ResultActions getWith(String path, JsonNode token) throws Exception { return mvc.perform(get(path).header("Authorization", bearer(token))); }
    private long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM "+table, Long.class); }
    private void hidden(JsonNode sales, UUID offer) throws Exception {
        long rows=count("sales_assignments"), audits=count("audit_logs");
        JsonNode list=response(getWith("/api/v1/offerings",sales),200);
        assertThat(list.toString()).doesNotContain(offer.toString(), "Offering "+offer, "CODE-"+offer);
        String body=getWith("/api/v1/offerings/"+offer,sales).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN_SCOPE")).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(offer.toString(), "Offering "+offer, "CODE-"+offer);
        assertThat(count("sales_assignments")).isEqualTo(rows); assertThat(count("audit_logs")).isEqualTo(audits);
    }
    private void denied(ResultActions result, int status, String code) throws Exception {
        result.andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code)).andExpect(jsonPath("$.traceId").isString());
    }
    @Test void tc01ManagerCreatesAndAssignedSalesReads() throws Exception {
        JsonNode manager=loginOne(); JsonNode created=response(jsonRequest(post("/api/v1/sales-assignments"),manager,createBody(OFFER,SALE_A,START.minusSeconds(1),null)),201);
        assertThat(created.get("offeringId").asText()).isEqualTo(OFFER.toString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action='SALES_ASSIGNMENT_CREATED'",Integer.class)).isEqualTo(1);
        JsonNode sales=saleA(); assertThat(response(getWith("/api/v1/offerings",sales),200).toString()).contains(OFFER.toString());
        getWith("/api/v1/offerings/"+OFFER,sales).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(OFFER.toString()));
    }
    @Test void tc02CrossTeamSaleDeniedEvenWhenManagerManagesBoth() throws Exception {
        jdbc.update("UPDATE teams SET manager_user_id=? WHERE id=?",USER_ONE,TEAM_MEMBER_ONLY); JsonNode manager=loginOne(); long rows=count("sales_assignments"), audits=count("audit_logs");
        denied(jsonRequest(post("/api/v1/sales-assignments"),manager,createBody(OFFER,SALE_B,START,null)),403,"FORBIDDEN_SCOPE");
        assertThat(count("sales_assignments")).isEqualTo(rows); assertThat(count("audit_logs")).isEqualTo(audits);
    }
    @Test void tc03UnassignedOfferingIsHidden() throws Exception { hidden(saleA(),OFFER); }
    @Test void tc04OtherSalesAssignmentIsHidden() throws Exception {
        member(id(308),TEAM_MANAGED,SALE_B); assignment(ASSIGNMENT,OFFER,SALE_B,START.minusSeconds(1),null,"ACTIVE"); hidden(saleA(),OFFER);
        assertThat(response(getWith("/api/v1/offerings",saleB()),200).toString()).contains(OFFER.toString());
    }
    @Test void tc05FutureAssignmentBecomesVisibleOnStart() throws Exception {
        assignment(ASSIGNMENT,OFFER,SALE_A,START.plusSeconds(10),null,"ACTIVE"); JsonNode sales=saleA(); hidden(sales,OFFER); clock.now=START.plusSeconds(10);
        getWith("/api/v1/offerings/"+OFFER,sales).andExpect(status().isOk());
    }
    @Test void tc06ExpiredTimeDeniesAccessBeforeJob() throws Exception {
        assignment(ASSIGNMENT,OFFER,SALE_A,START.minusSeconds(20),START.minusSeconds(1),"ACTIVE"); hidden(saleA(),OFFER);
        assertThat(jdbc.queryForObject("SELECT status FROM sales_assignments WHERE id=?",String.class,ASSIGNMENT)).isEqualTo("ACTIVE");
    }
    @Test void tc06bExpiryJobWritesSystemAuditOnce() {
        assignment(ASSIGNMENT,OFFER,SALE_A,START.minusSeconds(20),START.minusSeconds(1),"ACTIVE");
        assertThat(service.expireDueAssignments()).isEqualTo(1); assertThat(service.expireDueAssignments()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action='SALES_ASSIGNMENT_EXPIRED' AND actor_type='SYSTEM' AND actor_system_key='sales-assignment-expiry' AND actor_user_id IS NULL AND entity_id=?",Integer.class,ASSIGNMENT)).isEqualTo(1);
    }
    @Test void tc07RevocationRemovesSalesAccess() throws Exception {
        assignment(ASSIGNMENT,OFFER,SALE_A,START.minusSeconds(1),null,"ACTIVE"); JsonNode manager=loginOne();
        jsonRequest(post("/api/v1/sales-assignments/"+ASSIGNMENT+"/revoke"),manager,Map.of()).andExpect(status().isOk());
        hidden(saleA(),OFFER); assertThat(jdbc.queryForObject("SELECT status FROM sales_assignments WHERE id=?",String.class,ASSIGNMENT)).isEqualTo("REVOKED");
        assertThat(jdbc.queryForObject("SELECT revoked_at FROM sales_assignments WHERE id=?",Timestamp.class,ASSIGNMENT)).isNotNull();
    }
    @Test void tc08InactiveOfferingIsHiddenWithoutRevokingAssignment() throws Exception {
        assignment(ASSIGNMENT,OFFER,SALE_A,START.minusSeconds(1),null,"ACTIVE"); JsonNode manager=loginOne();
        jsonRequest(post("/api/v1/offerings/"+OFFER+"/deactivate"),manager,Map.of()).andExpect(status().isOk()); hidden(saleA(),OFFER);
        assertThat(jdbc.queryForObject("SELECT status FROM sales_assignments WHERE id=?",String.class,ASSIGNMENT)).isEqualTo("ACTIVE");
    }
    @Test void tc09ManagerCannotAccessOtherTeamAssignments() throws Exception {
        UUID other=id(309); offering(other,TEAM_MEMBER_ONLY,"ACTIVE"); assignment(ASSIGNMENT,other,SALE_B,START.minusSeconds(1),null,"ACTIVE"); JsonNode manager=loginOne();
        long rows=count("sales_assignments"), audits=count("audit_logs");
        assertThat(response(getWith("/api/v1/sales-assignments",manager),200).toString()).doesNotContain(other.toString(),ASSIGNMENT.toString());
        denied(getWith("/api/v1/sales-assignments/"+ASSIGNMENT,manager),403,"FORBIDDEN_SCOPE");
        denied(getWith("/api/v1/sales-assignments/candidates?offeringId="+other,manager),403,"FORBIDDEN_SCOPE");
        denied(jsonRequest(post("/api/v1/sales-assignments"),manager,createBody(other,SALE_B,START,null)),403,"FORBIDDEN_SCOPE");
        denied(jsonRequest(patch("/api/v1/sales-assignments/"+ASSIGNMENT),manager,Map.of("effectiveTo",START.plusSeconds(100).toString(),"expectedUpdatedAt",time().toInstant().toString())),403,"FORBIDDEN_SCOPE");
        denied(jsonRequest(post("/api/v1/sales-assignments/"+ASSIGNMENT+"/revoke"),manager,Map.of()),403,"FORBIDDEN_SCOPE");
        assertThat(count("sales_assignments")).isEqualTo(rows); assertThat(count("audit_logs")).isEqualTo(audits);
    }
    @Test void tc10ForeignTenantAndMissingIdsReturnCommonScopeError() throws Exception {
        JsonNode manager=loginOne(); long rows=count("sales_assignments"), audits=count("audit_logs");
        UUID foreignCategory=id(314), foreignTeam=id(315), foreignOffering=id(316);
        team(foreignTeam,ORG_TWO,"FOREIGN","Foreign",USER_TWO);
        jdbc.update("INSERT INTO offering_categories(id,organization_id,code,name,status,created_at,updated_at) VALUES (?,?,?,?,'ACTIVE',?,?)",foreignCategory,ORG_TWO,"FOREIGN","Foreign category",time(),time());
        jdbc.update("INSERT INTO offerings(id,organization_id,category_id,team_id,code,name,kind,status,created_at,updated_at) VALUES (?,?,?,?,?,?,'SERVICE','ACTIVE',?,?)",foreignOffering,ORG_TWO,foreignCategory,foreignTeam,"FOREIGN-CODE","Foreign offering",time(),time());
        for (UUID invalid : List.of(USER_TWO,id(399))) denied(jsonRequest(post("/api/v1/sales-assignments"),manager,createBody(OFFER,invalid,START,null)),403,"FORBIDDEN_SCOPE");
        for(UUID invalid:List.of(foreignOffering,id(398))) {
            String body=jsonRequest(post("/api/v1/sales-assignments"),manager,createBody(invalid,SALE_A,START,null)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN_SCOPE")).andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain(foreignOffering.toString(),"FOREIGN-CODE","Foreign offering");
        }
        assertThat(count("sales_assignments")).isEqualTo(rows); assertThat(count("audit_logs")).isEqualTo(audits);
    }
    @Test void tc11SalesCannotMutateOfferingsOrAssignments() throws Exception {
        assignment(ASSIGNMENT,OFFER,SALE_A,START.minusSeconds(1),null,"ACTIVE"); JsonNode sales=saleA(); long rows=count("sales_assignments"), audits=count("audit_logs");
        denied(jsonRequest(post("/api/v1/offerings"),sales,Map.of()),403,"FORBIDDEN_PERMISSION");
        denied(jsonRequest(put("/api/v1/offerings/"+OFFER),sales,Map.of()),403,"FORBIDDEN_PERMISSION");
        for(String action:List.of("activate","deactivate")) denied(jsonRequest(post("/api/v1/offerings/"+OFFER+"/"+action),sales,Map.of()),403,"FORBIDDEN_PERMISSION");
        denied(jsonRequest(post("/api/v1/sales-assignments"),sales,createBody(OFFER,SALE_A,START,null)),403,"FORBIDDEN_PERMISSION");
        denied(jsonRequest(patch("/api/v1/sales-assignments/"+ASSIGNMENT),sales,Map.of()),403,"FORBIDDEN_PERMISSION");
        denied(jsonRequest(post("/api/v1/sales-assignments/"+ASSIGNMENT+"/revoke"),sales,Map.of()),403,"FORBIDDEN_PERMISSION");
        assertThat(count("sales_assignments")).isEqualTo(rows); assertThat(count("audit_logs")).isEqualTo(audits);
    }
    @Test void tc12CreateExtendRevokeEachWritesUserAudit() throws Exception {
        JsonNode manager=loginOne(); JsonNode created=response(jsonRequest(post("/api/v1/sales-assignments"),manager,createBody(OFFER,SALE_A,START,START.plusSeconds(100))),201); UUID id=UUID.fromString(created.get("id").asText());
        clock.now=START.plusSeconds(1); jsonRequest(patch("/api/v1/sales-assignments/"+id),manager,Map.of("effectiveTo",START.plusSeconds(200).toString(),"expectedUpdatedAt",created.get("updatedAt").asText())).andExpect(status().isOk());
        jsonRequest(post("/api/v1/sales-assignments/"+id+"/revoke"),manager,Map.of()).andExpect(status().isOk());
        for(String action:List.of("CREATED","EXTENDED","REVOKED")) assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action=? AND entity_id=? AND actor_type='USER' AND actor_user_id=?",Integer.class,"SALES_ASSIGNMENT_"+action,id,USER_ONE)).isEqualTo(1);
    }
    @Test void tc13AuditFailuresRollbackAllMutations() throws Exception {
        assignment(ASSIGNMENT,OFFER,SALE_A,START.minusSeconds(1),START.plusSeconds(100),"ACTIVE"); JsonNode manager=loginOne(); long rows=count("sales_assignments"), audits=count("audit_logs");
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT reject_assignment_audit CHECK(action NOT IN ('SALES_ASSIGNMENT_CREATED','SALES_ASSIGNMENT_EXTENDED','SALES_ASSIGNMENT_REVOKED'))");
        try {
            denied(jsonRequest(post("/api/v1/sales-assignments"),manager,createBody(OFFER,SALE_A,START,null)),409,"CONFLICT");
            denied(jsonRequest(patch("/api/v1/sales-assignments/"+ASSIGNMENT),manager,Map.of("effectiveTo",START.plusSeconds(200).toString(),"expectedUpdatedAt",time().toInstant().toString())),409,"CONFLICT");
            denied(jsonRequest(post("/api/v1/sales-assignments/"+ASSIGNMENT+"/revoke"),manager,Map.of()),409,"CONFLICT");
            assertThat(count("sales_assignments")).isEqualTo(rows); assertThat(count("audit_logs")).isEqualTo(audits); assertThat(jdbc.queryForObject("SELECT status FROM sales_assignments WHERE id=?",String.class,ASSIGNMENT)).isEqualTo("ACTIVE");
            assertThat(jdbc.queryForObject("SELECT effective_to FROM sales_assignments WHERE id=?",Timestamp.class,ASSIGNMENT).toInstant()).isEqualTo(START.plusSeconds(100));
        } finally { jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT reject_assignment_audit"); }
    }
    @Test void conflictsAndValidationPreserveAssignment() throws Exception {
        assignment(ASSIGNMENT,OFFER,SALE_A,START.minusSeconds(1),START.plusSeconds(100),"ACTIVE"); JsonNode manager=loginOne(); long audits=count("audit_logs");
        denied(jsonRequest(patch("/api/v1/sales-assignments/"+ASSIGNMENT),manager,Map.of("effectiveTo",START.plusSeconds(200).toString(),"expectedUpdatedAt",START.minusSeconds(10).toString())),409,"SALES_ASSIGNMENT_CHANGED");
        denied(jsonRequest(patch("/api/v1/sales-assignments/"+ASSIGNMENT),manager,Map.of("effectiveTo",START.plusSeconds(50).toString(),"expectedUpdatedAt",time().toInstant().toString())),400,"VALIDATION_ERROR");
        jsonRequest(post("/api/v1/sales-assignments"),manager,createBody(OFFER,SALE_A,START,START.minusSeconds(1))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR")).andExpect(jsonPath("$.fieldErrors[0]").exists());
        assertThat(count("audit_logs")).isEqualTo(audits); assertThat(count("sales_assignments")).isEqualTo(1);
        jdbc.update("UPDATE sales_assignments SET status='REVOKED',revoked_at=? WHERE id=?",time(),ASSIGNMENT);
        denied(jsonRequest(patch("/api/v1/sales-assignments/"+ASSIGNMENT),manager,Map.of("effectiveTo",START.plusSeconds(200).toString(),"expectedUpdatedAt",time().toInstant().toString())),409,"SALES_ASSIGNMENT_REVOKED");
    }
    @Test void candidatesOnlyIncludeEligibleSalesInOfferingTeam() throws Exception {
        JsonNode manager=loginOne(); JsonNode candidates=response(getWith("/api/v1/sales-assignments/candidates?offeringId="+OFFER,manager),200);
        assertThat(candidates.toString()).contains(SALE_A.toString()).doesNotContain(SALE_B.toString(),USER_TWO.toString());
        assertThat(response(getWith("/api/v1/sales-assignments/candidates?offeringId="+OFFER+"&teamId="+TEAM_MEMBER_ONLY,manager),200)).isEqualTo(candidates);
    }
    @Test void salesLosesOfferingAccessWhenMembershipEnds() throws Exception {
        assignment(ASSIGNMENT,OFFER,SALE_A,START.minusSeconds(1),null,"ACTIVE");
        jdbc.update("UPDATE team_memberships SET status='ENDED',left_at=? WHERE team_id=? AND user_id=?",Timestamp.from(START),TEAM_MANAGED,SALE_A);
        hidden(saleA(),OFFER);
    }
    @Test void salesCannotReadOfferingDetailWhenMembershipEnds() throws Exception {
        assignment(ASSIGNMENT,OFFER,SALE_A,START.minusSeconds(1),null,"ACTIVE");
        jdbc.update("UPDATE team_memberships SET status='ENDED',left_at=? WHERE team_id=? AND user_id=?",Timestamp.from(START),TEAM_MANAGED,SALE_A);
        denied(getWith("/api/v1/offerings/"+OFFER,saleA()),403,"FORBIDDEN_SCOPE");
    }
    @Test void salesMembershipLeftAtBoundaryRemovesReadAccess() throws Exception {
        assignment(ASSIGNMENT,OFFER,SALE_A,START.minusSeconds(1),null,"ACTIVE");
        jdbc.update("UPDATE team_memberships SET left_at=? WHERE id=?",Timestamp.from(START.plusSeconds(10)),id(306));
        JsonNode sales=saleA();
        assertThat(response(getWith("/api/v1/offerings",sales),200).toString()).contains(OFFER.toString());
        getWith("/api/v1/offerings/"+OFFER,sales).andExpect(status().isOk());
        clock.now=START.plusSeconds(10); hidden(sales,OFFER);
        assertThat(jdbc.queryForObject("SELECT status FROM sales_assignments WHERE id=?",String.class,ASSIGNMENT)).isEqualTo("ACTIVE");
    }
    @Test void inactiveOwningTeamRemovesReadAccessWithoutRevocation() throws Exception {
        assignment(ASSIGNMENT,OFFER,SALE_A,START.minusSeconds(1),null,"ACTIVE"); JsonNode sales=saleA();
        jdbc.update("UPDATE teams SET status='INACTIVE' WHERE id=?",TEAM_MANAGED); hidden(sales,OFFER);
        assertThat(jdbc.queryForObject("SELECT status FROM sales_assignments WHERE id=?",String.class,ASSIGNMENT)).isEqualTo("ACTIVE");
    }
    @Test void salesMovingToAnotherTeamLosesOldOfferingAccess() throws Exception {
        assignment(ASSIGNMENT,OFFER,SALE_A,START.minusSeconds(1),null,"ACTIVE");
        jdbc.update("UPDATE team_memberships SET status='ENDED',left_at=? WHERE id=?",Timestamp.from(START),id(306));
        member(id(320),TEAM_MEMBER_ONLY,SALE_A); hidden(saleA(),OFFER);
    }
    @Test void futureMembershipGrantsAccessAtJoinedAt() throws Exception {
        assignment(ASSIGNMENT,OFFER,SALE_A,START.minusSeconds(1),null,"ACTIVE");
        jdbc.update("UPDATE team_memberships SET joined_at=? WHERE id=?",Timestamp.from(START.plusSeconds(10)),id(306));
        JsonNode sales=saleA(); hidden(sales,OFFER); clock.now=START.plusSeconds(10);
        assertThat(response(getWith("/api/v1/offerings",sales),200).toString()).contains(OFFER.toString());
        getWith("/api/v1/offerings/"+OFFER,sales).andExpect(status().isOk());
    }
    @Test void legacyOfferingWithoutTeamIsHiddenFromSales() throws Exception {
        UUID legacy=id(321); offering(legacy,null,"ACTIVE");
        assignment(ASSIGNMENT,legacy,SALE_A,START.minusSeconds(1),null,"ACTIVE"); hidden(saleA(),legacy);
    }
    @Test void legacyOfferingWithoutTeamIsRejected() throws Exception {
        UUID legacy=id(310); offering(legacy,null,"ACTIVE"); JsonNode manager=loginOne();
        denied(jsonRequest(post("/api/v1/sales-assignments"),manager,createBody(legacy,SALE_A,START,null)),409,"OFFERING_TEAM_REQUIRED");
    }
    @Test void inactiveTeamCannotReceiveAssignment() throws Exception {
        admin(); grant(ORG_ONE,"ADMIN","sales_assignment.manage"); JsonNode manager=loginOne();
        jdbc.update("UPDATE teams SET status='INACTIVE' WHERE id=?",TEAM_MANAGED);
        long rows=count("sales_assignments"), audits=count("audit_logs");
        denied(jsonRequest(post("/api/v1/sales-assignments"),manager,createBody(OFFER,SALE_A,START,null)),409,"TEAM_INACTIVE");
        assertThat(count("sales_assignments")).isEqualTo(rows); assertThat(count("audit_logs")).isEqualTo(audits);
    }
    @Test void openWorkListsLeadOpportunityAndTaskAndPreventsRevocation() throws Exception {
        UUID lead=id(311), opportunity=id(312), task=id(313);
        assignment(ASSIGNMENT,OFFER,SALE_A,START.minusSeconds(1),null,"ACTIVE");
        jdbc.update("INSERT INTO leads(id,organization_id,owner_user_id,offering_id,display_name,email,normalized_email,status,created_at,updated_at) VALUES (?,?,?,?,?,?,?,'NEW',?,?)",lead,ORG_ONE,SALE_A,OFFER,"Open lead","lead@example.test","lead@example.test",time(),time());
        jdbc.update("INSERT INTO opportunities(id,organization_id,lead_id,offering_id,owner_user_id,title,stage,created_at,updated_at) VALUES (?,?,?,?,?,?,'NEW',?,?)",opportunity,ORG_ONE,lead,OFFER,SALE_A,"Open opportunity",time(),time());
        jdbc.update("INSERT INTO crm_tasks(id,organization_id,lead_id,owner_user_id,created_by_user_id,title,priority,status,due_at,origin,created_at,updated_at) VALUES (?,?,?,?,?,?,'NORMAL','OPEN',?,'MANUAL',?,?)",task,ORG_ONE,lead,SALE_A,USER_ONE,"Open task",time(),time(),time());
        JsonNode manager=loginOne(); JsonNode work=response(getWith("/api/v1/sales-assignments/"+ASSIGNMENT+"/open-work",manager),200);
        assertThat(work.get("totalItems").asInt()).isEqualTo(3);
        assertThat(work.toString()).contains(lead.toString(),opportunity.toString(),task.toString(),"LEAD","OPPORTUNITY","TASK");
        long audits=count("audit_logs"), rows=count("sales_assignments");
        denied(jsonRequest(post("/api/v1/sales-assignments/"+ASSIGNMENT+"/revoke"),manager,Map.of()),409,"SALES_ASSIGNMENT_OPEN_WORK");
        assertThat(count("sales_assignments")).isEqualTo(rows); assertThat(count("audit_logs")).isEqualTo(audits);
        assertThat(jdbc.queryForObject("SELECT status FROM sales_assignments WHERE id=?",String.class,ASSIGNMENT)).isEqualTo("ACTIVE");
    }
}
