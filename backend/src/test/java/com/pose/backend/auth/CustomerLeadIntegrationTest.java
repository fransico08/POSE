package com.pose.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties="pose.sales-assignment.expiry-scan-delay-ms=86400000")
class CustomerLeadIntegrationTest extends FoundationTestSupport {
    private static final UUID CAT=id(401), OFFER=id(402), OTHER_OFFER=id(403), SALE=id(404), OTHER_SALE=id(405), CARE=id(406), DATA=id(407), ADMIN=id(408), ASSIGN=id(409);
    @BeforeEach void crmFixtures() {
        for(String code:List.of("customer.read","customer.write","lead.read","lead.write","offering.read"))
            if(jdbc.queryForObject("SELECT count(*) FROM permissions WHERE code=?",Integer.class,code)==0) permission(code);
        for(String code:List.of("ADMIN","DATA_STAFF")) role(ORG_ONE,code);
        for(String role:List.of("MANAGER","SALES_REP","ADMIN")) for(String perm:List.of("customer.read","customer.write","lead.read","lead.write")) grantOnce(role,perm);
        grantOnce("SALES_REP","offering.read"); grantOnce("CUSTOMER_CARE","customer.read"); grantOnce("DATA_STAFF","customer.read"); grantOnce("DATA_STAFF","lead.read");
        for(var pair:Map.of(SALE,"sale",OTHER_SALE,"other-sale",CARE,"care",DATA,"data",ADMIN,"admin").entrySet())
            user(pair.getKey(),ORG_ONE,pair.getValue(),pair.getValue()+"@example.test",hashOne);
        userRole(ORG_ONE,SALE,"SALES_REP"); userRole(ORG_ONE,OTHER_SALE,"SALES_REP"); userRole(ORG_ONE,CARE,"CUSTOMER_CARE"); userRole(ORG_ONE,DATA,"DATA_STAFF"); userRole(ORG_ONE,ADMIN,"ADMIN");
        member(id(410),TEAM_MANAGED,SALE,START.minusSeconds(1),null,"ACTIVE"); member(id(411),TEAM_MEMBER_ONLY,OTHER_SALE,START.minusSeconds(1),null,"ACTIVE");
        jdbc.update("INSERT INTO offering_categories(id,organization_id,code,name,status,created_at,updated_at) VALUES (?,?,'CRM','CRM','ACTIVE',?,?)",CAT,ORG_ONE,time(),time());
        offering(OFFER,TEAM_MANAGED,"ACTIVE"); offering(OTHER_OFFER,TEAM_MEMBER_ONLY,"ACTIVE");
        salesAssignment(ASSIGN,OFFER,SALE,START.minusSeconds(1),null,"ACTIVE");
        salesAssignment(id(412),OTHER_OFFER,OTHER_SALE,START.minusSeconds(1),null,"ACTIVE");
    }
    private void grantOnce(String role,String permission) {
        if(jdbc.queryForObject("SELECT count(*) FROM role_permissions rp JOIN roles r ON r.id=rp.role_id JOIN permissions p ON p.id=rp.permission_id WHERE r.organization_id=? AND r.code=? AND p.code=?",Integer.class,ORG_ONE,role,permission)==0) grant(ORG_ONE,role,permission);
    }
    private void member(UUID id,UUID team,UUID user,Instant from,Instant to,String status) {
        jdbc.update("INSERT INTO team_memberships(id,organization_id,team_id,user_id,joined_at,left_at,status) VALUES (?,?,?,?,?,?,?)",id,ORG_ONE,team,user,Timestamp.from(from),to==null?null:Timestamp.from(to),status);
    }
    private void offering(UUID id,UUID team,String status) {
        jdbc.update("INSERT INTO offerings(id,organization_id,category_id,team_id,code,name,kind,status,created_at,updated_at) VALUES (?,?,?,?,?,?,'SERVICE',?,?,?)",id,ORG_ONE,CAT,team,"OFF-"+id,"Offer "+id,status,time(),time());
    }
    private void salesAssignment(UUID id,UUID offer,UUID sale,Instant from,Instant to,String status) {
        jdbc.update("INSERT INTO sales_assignments(id,organization_id,offering_id,sales_user_id,assigned_by_user_id,status,effective_from,effective_to,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)",id,ORG_ONE,offer,sale,USER_ONE,status,Timestamp.from(from),to==null?null:Timestamp.from(to),time(),time());
    }
    private void lead(UUID id,UUID owner,UUID offer,String email,String status) {
        jdbc.update("INSERT INTO leads(id,organization_id,owner_user_id,offering_id,display_name,email,normalized_email,status,disqualification_reason,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",id,ORG_ONE,owner,offer,"Legacy lead",email,email,status,status.equals("DISQUALIFIED")?"Closed":null,time(),time());
    }
    private void customer(UUID id,UUID owner,String email) {
        jdbc.update("INSERT INTO customers(id,organization_id,owner_user_id,display_name,email,normalized_email,status,created_at,updated_at) VALUES (?,?,?,'Legacy customer',?,?,'ACTIVE',?,?)",id,ORG_ONE,owner,email,email,time(),time());
    }
    private void customerAssignment(UUID id,UUID customer,UUID user,String type,Instant from,Instant to,String status) {
        jdbc.update("INSERT INTO customer_assignments(id,organization_id,customer_id,user_id,assignment_type,effective_from,effective_to,status,assigned_by_user_id,source,created_at) VALUES (?,?,?,?,?,?,?,?,?,'MANUAL',?)",id,ORG_ONE,customer,user,type,Timestamp.from(from),to==null?null:Timestamp.from(to),status,USER_ONE,time());
    }
    private JsonNode loginAs(String name) throws Exception { return response(login("one",name+"@example.test",PASSWORD_ONE),200); }
    private Map<String,Object> leadBody(String email,UUID offer) { var b=new LinkedHashMap<String,Object>(); b.put("displayName","Lead test"); b.put("email",email); b.put("source","WEBSITE"); b.put("offeringId",offer); return b; }
    private Map<String,Object> customerBody(String email,UUID offer) { var b=new LinkedHashMap<String,Object>(); b.put("displayName","Customer test"); b.put("email",email); b.put("customerType","INDIVIDUAL"); b.put("relatedOfferingId",offer); return b; }
    private JsonNode getJson(String path,JsonNode actor,int status) throws Exception { return response(mvc.perform(get(path).header("Authorization",bearer(actor))),status); }
    private long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM "+table,Long.class); }
    private void error(org.springframework.test.web.servlet.ResultActions result,int status,String code) throws Exception { result.andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code)); }
    @Test void salesCreatesLeadAndInitialCustomerWithAudit() throws Exception {
        JsonNode sales=loginAs("sale"); JsonNode l=response(jsonRequest(post("/api/v1/leads"),sales,leadBody("lead@example.test",OFFER)),201);
        assertThat(l.get("ownerUserId").asText()).isEqualTo(SALE.toString());
        JsonNode c=response(jsonRequest(post("/api/v1/customers"),sales,customerBody("customer@example.test",OFFER)),201);
        assertThat(c.get("ownerUserId").asText()).isEqualTo(SALE.toString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_assignments WHERE customer_id=? AND user_id=? AND assignment_type='SALES' AND source='INITIAL' AND status='ACTIVE'",Integer.class,UUID.fromString(c.get("id").asText()),SALE)).isEqualTo(1);
        for(String action:List.of("LEAD_CREATED","CUSTOMER_CREATED")) assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action=?",Integer.class,action)).isEqualTo(1);
    }
    @Test void salesCannotCreateAgainstUnassignedInactiveOrOtherTeamOffering() throws Exception {
        UUID unassigned=id(413); offering(unassigned,TEAM_MANAGED,"ACTIVE"); JsonNode sales=loginAs("sale"); long audits=count("audit_logs");
        for(UUID offer:List.of(unassigned,OTHER_OFFER)) for(String kind:List.of("leads","customers"))
            error(jsonRequest(post("/api/v1/"+kind),sales,kind.equals("leads")?leadBody("x@example.test",offer):customerBody("x@example.test",offer)),403,"FORBIDDEN_SCOPE");
        jdbc.update("UPDATE offerings SET status='INACTIVE' WHERE id=?",OFFER);
        error(jsonRequest(post("/api/v1/leads"),sales,leadBody("x@example.test",OFFER)),403,"FORBIDDEN_SCOPE");
        assertThat(count("leads")).isZero(); assertThat(count("customers")).isZero(); assertThat(count("audit_logs")).isEqualTo(audits);
    }
    @Test void validationAndPhoneNormalization() throws Exception {
        JsonNode sales=loginAs("sale");
        var missing=leadBody("x@example.test",OFFER); missing.remove("source");
        jsonRequest(post("/api/v1/leads"),sales,missing).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("source"));
        for(String field:List.of("displayName","email","offeringId")) {
            var invalid=leadBody("x@example.test",OFFER); invalid.remove(field);
            jsonRequest(post("/api/v1/leads"),sales,invalid).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0]").exists());
        }
        var nullOffering=leadBody("null-offering@example.test",OFFER); nullOffering.put("offeringId",null);
        jsonRequest(post("/api/v1/leads"),sales,nullOffering).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Vui lòng điền đầy đủ các trường bắt buộc"));
        error(jsonRequest(post("/api/v1/leads"),sales,leadBody("bad-email",OFFER)),400,"VALIDATION_ERROR");
        var bad=leadBody("x@example.test",OFFER); bad.put("phone","bad"); error(jsonRequest(post("/api/v1/leads"),sales,bad),400,"VALIDATION_ERROR");
        bad.put("phone","01234567890"); error(jsonRequest(post("/api/v1/leads"),sales,bad),400,"VALIDATION_ERROR");
        for(String phone:List.of("0912 345 678","84912345678","+84 912-345-678")) {
            var b=leadBody(null,OFFER); b.put("phone",phone); JsonNode result=response(jsonRequest(post("/api/v1/leads"),sales,b),201);
            assertThat(jdbc.queryForObject("SELECT normalized_phone FROM leads WHERE id=?",String.class,UUID.fromString(result.get("id").asText()))).isEqualTo("+84912345678");
            jdbc.update("DELETE FROM leads WHERE id=?",UUID.fromString(result.get("id").asText()));
        }
        for(String field:List.of("displayName","email","customerType","relatedOfferingId")) { var b=customerBody("x@example.test",OFFER); b.remove(field); jsonRequest(post("/api/v1/customers"),sales,b).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0]").exists()); }
    }
    @Test void searchUsesNormalizedVietnamesePhoneForLeadsAndCustomers() throws Exception {
        UUID l=id(461), c=id(462); lead(l,SALE,OFFER,"phone-lead@example.test","NEW"); customer(c,SALE,"phone-customer@example.test");
        jdbc.update("UPDATE leads SET phone=?, normalized_phone=? WHERE id=?","+84910000001","+84910000001",l);
        jdbc.update("UPDATE customers SET phone=?, normalized_phone=? WHERE id=?","+84910000001","+84910000001",c);
        JsonNode sales=loginAs("sale");
        assertThat(getJson("/api/v1/leads?search=0910000001",sales,200).toString()).contains(l.toString());
        assertThat(getJson("/api/v1/customers?search=0910000001",sales,200).toString()).contains(c.toString());
    }
    @Test void ownerOptionsRespectReadScopeRolesTenantAndDistinctOrder() throws Exception {
        lead(id(471),SALE,OFFER,"scope-a@example.test","NEW"); lead(id(472),SALE,OFFER,"scope-b@example.test","NEW");
        lead(id(473),OTHER_SALE,OTHER_OFFER,"scope-c@example.test","NEW"); lead(id(474),ADMIN,OFFER,"scope-archived@example.test","NEW");
        jdbc.update("UPDATE leads SET archived_at=? WHERE id=?",time(),id(474));
        customer(id(475),SALE,"scope-ca@example.test"); customer(id(476),SALE,"scope-cb@example.test"); customer(id(477),OTHER_SALE,"scope-cc@example.test"); customer(id(478),ADMIN,"scope-archived-c@example.test");
        jdbc.update("UPDATE customers SET archived_at=? WHERE id=?",time(),id(478));
        jdbc.update("INSERT INTO offering_categories(id,organization_id,code,name,status,created_at,updated_at) VALUES (?,?,'OTHER','Other','ACTIVE',?,?)",id(483),ORG_TWO,time(),time());
        jdbc.update("INSERT INTO offerings(id,organization_id,category_id,code,name,kind,status,created_at,updated_at) VALUES (?,?,?,'FOREIGN','Foreign offer','SERVICE','ACTIVE',?,?)",id(484),ORG_TWO,id(483),time(),time());
        jdbc.update("INSERT INTO leads(id,organization_id,owner_user_id,offering_id,display_name,email,normalized_email,status,created_at,updated_at) VALUES (?,?,?,?,'Foreign lead','foreign-lead@example.test','foreign-lead@example.test','NEW',?,?)",id(479),ORG_TWO,USER_TWO,id(484),time(),time());
        jdbc.update("INSERT INTO customers(id,organization_id,owner_user_id,display_name,email,normalized_email,status,created_at,updated_at) VALUES (?,?,?,'Foreign customer','foreign-customer@example.test','foreign-customer@example.test','ACTIVE',?,?)",id(480),ORG_TWO,USER_TWO,time(),time());
        JsonNode manager=response(login("one","shared@example.com",PASSWORD_ONE),200), admin=loginAs("admin"), sales=loginAs("sale"), care=loginAs("care");
        for (String kind:List.of("leads","customers")) {
            JsonNode managerOwners=getJson("/api/v1/"+kind+"/owners",manager,200);
            assertThat(managerOwners.size()).isEqualTo(1); assertThat(managerOwners.get(0).get("id").asText()).isEqualTo(SALE.toString());
            JsonNode adminOwners=getJson("/api/v1/"+kind+"/owners",admin,200);
            assertThat(adminOwners.size()).isEqualTo(2);
            assertThat(adminOwners.get(0).get("id").asText()).isEqualTo(OTHER_SALE.toString());
            assertThat(adminOwners.get(1).get("id").asText()).isEqualTo(SALE.toString());
            assertThat(adminOwners.toString()).doesNotContain(USER_TWO.toString(), ADMIN.toString());
            for (JsonNode actor:List.of(sales,care)) error(mvc.perform(get("/api/v1/"+kind+"/owners").header("Authorization",bearer(actor))),403,"FORBIDDEN_PERMISSION");
        }
    }
    @Test void partialPhoneSearchAndLikeWildcardsAreHandledSafely() throws Exception {
        UUID l=id(481), c=id(482); lead(l,SALE,OFFER,"partial-lead@example.test","NEW"); customer(c,SALE,"partial-customer@example.test");
        jdbc.update("UPDATE leads SET phone=?,normalized_phone=? WHERE id=?","+84910000001","+84910000001",l);
        jdbc.update("UPDATE customers SET phone=?,normalized_phone=? WHERE id=?","+84910000001","+84910000001",c);
        JsonNode sales=loginAs("sale");
        for (String kind:List.of("leads","customers")) {
            for (String term:List.of("0910","910000","+84910","84910")) {
                JsonNode result=response(mvc.perform(get("/api/v1/"+kind).param("search",term).header("Authorization",bearer(sales))),200);
                assertThat(result.get("items").size()).isEqualTo(1);
            }
            for (String term:List.of("%","_")) {
                JsonNode result=response(mvc.perform(get("/api/v1/"+kind).param("search",term).header("Authorization",bearer(sales))),200);
                assertThat(result.get("items").size()).isZero();
            }
        }
    }
    @Test void requiredTextOverLengthReportsLimitInsteadOfMissing() throws Exception {
        JsonNode sales=loginAs("sale"); var b=leadBody("length@example.test",OFFER); b.put("displayName","a".repeat(181));
        jsonRequest(post("/api/v1/leads"),sales,b).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("displayName"))
            .andExpect(jsonPath("$.fieldErrors[0].message").value("Tối đa 180 ký tự."));
    }
    @Test void duplicatesOnlyRevealReadableIdsAndAreNotCrossEntity() throws Exception {
        UUID own=id(414), hidden=id(415); lead(own,SALE,OFFER,"visible@example.test","NEW"); lead(hidden,OTHER_SALE,OTHER_OFFER,"hidden@example.test","NEW");
        customer(id(416),SALE,"customer-only@example.test"); JsonNode sales=loginAs("sale");
        jsonRequest(post("/api/v1/leads"),sales,leadBody("visible@example.test",OFFER)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LEAD_DUPLICATE")).andExpect(jsonPath("$.details.duplicateId").value(own.toString()));
        UUID edit=id(447); lead(edit,SALE,OFFER,"edit@example.test","NEW");
        jsonRequest(patch("/api/v1/leads/"+edit),sales,Map.of("email","visible@example.test","expectedUpdatedAt",time().toInstant().toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LEAD_DUPLICATE"));
        String body=jsonRequest(post("/api/v1/leads"),sales,leadBody("hidden@example.test",OFFER)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LEAD_DUPLICATE")).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(hidden.toString(),"hidden@example.test","duplicateId");
        jsonRequest(post("/api/v1/leads"),sales,leadBody("customer-only@example.test",OFFER)).andExpect(status().isCreated());
        UUID visibleCustomer=id(417), hiddenCustomer=id(418); customer(visibleCustomer,SALE,"customer-visible@example.test"); customer(hiddenCustomer,OTHER_SALE,"customer-hidden@example.test");
        jsonRequest(post("/api/v1/customers"),sales,customerBody("customer-visible@example.test",OFFER)).andExpect(status().isConflict()).andExpect(jsonPath("$.details.duplicateId").value(visibleCustomer.toString()));
        String hiddenBody=jsonRequest(post("/api/v1/customers"),sales,customerBody("customer-hidden@example.test",OFFER)).andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();
        assertThat(hiddenBody).doesNotContain(hiddenCustomer.toString(),"customer-hidden@example.test","duplicateId");
    }
    @Test void salesLeadScopeRespondsToAssignmentAndMembershipChanges() throws Exception {
        UUID own=id(419), other=id(420); lead(own,SALE,OFFER,"mine@example.test","NEW"); lead(other,OTHER_SALE,OTHER_OFFER,"other@example.test","NEW"); JsonNode sales=loginAs("sale");
        assertThat(getJson("/api/v1/leads",sales,200).toString()).contains(own.toString()).doesNotContain(other.toString());
        getJson("/api/v1/leads/"+own,sales,200); getJson("/api/v1/leads/"+other,sales,403);
        jdbc.update("UPDATE sales_assignments SET effective_to=? WHERE id=?",Timestamp.from(START.plusSeconds(10)),ASSIGN); clock.now=START.plusSeconds(11);
        assertThat(getJson("/api/v1/leads",sales,200).toString()).doesNotContain(own.toString()); getJson("/api/v1/leads/"+own,sales,403);
        clock.now=START; jdbc.update("UPDATE sales_assignments SET effective_to=NULL WHERE id=?",ASSIGN);
        jdbc.update("UPDATE team_memberships SET left_at=? WHERE id=?",Timestamp.from(START.plusSeconds(10)),id(410)); getJson("/api/v1/leads/"+own,sales,200);
        clock.now=START.plusSeconds(10); assertThat(getJson("/api/v1/leads",sales,200).toString()).doesNotContain(own.toString()); getJson("/api/v1/leads/"+own,sales,403);
        clock.now=START; jdbc.update("UPDATE team_memberships SET status='ENDED',left_at=? WHERE id=?",Timestamp.from(START),id(410));
        getJson("/api/v1/leads/"+own,sales,403);
        jdbc.update("UPDATE team_memberships SET status='ACTIVE',left_at=NULL WHERE id=?",id(410)); jdbc.update("UPDATE sales_assignments SET status='REVOKED',revoked_at=? WHERE id=?",Timestamp.from(START),ASSIGN);
        getJson("/api/v1/leads/"+own,sales,403);
    }
    @Test void managerScopeCandidatesAndOwnerValidation() throws Exception {
        UUID own=id(421), outside=id(422); lead(own,SALE,OFFER,"team@example.test","NEW"); lead(outside,OTHER_SALE,OTHER_OFFER,"outside@example.test","NEW"); JsonNode manager=loginOne();
        assertThat(getJson("/api/v1/leads",manager,200).toString()).contains(own.toString()).doesNotContain(outside.toString());
        getJson("/api/v1/leads/"+outside,manager,403);
        for(String entity:List.of("leads","customers")) {
            mvc.perform(get("/api/v1/"+entity+"/owner-candidates").header("Authorization",bearer(manager))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR")).andExpect(jsonPath("$.fieldErrors[0].field").value("offeringId"));
            String candidates=getJson("/api/v1/"+entity+"/owner-candidates?offeringId="+OFFER,manager,200).toString(); assertThat(candidates).contains(SALE.toString()).doesNotContain(OTHER_SALE.toString());
            var valid=entity.equals("leads")?leadBody("newlead@example.test",OFFER):customerBody("newcustomer@example.test",OFFER); valid.put("ownerUserId",SALE);
            jsonRequest(post("/api/v1/"+entity),manager,valid).andExpect(status().isCreated());
            valid.put("ownerUserId",OTHER_SALE); jsonRequest(post("/api/v1/"+entity),manager,valid).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.fieldErrors[0].field").value("ownerUserId"));
        }
        jdbc.update("UPDATE offerings SET status='INACTIVE' WHERE id=?",OFFER);
        assertThat(getJson("/api/v1/leads/owner-candidates?offeringId="+OFFER,manager,200).isEmpty()).isTrue();
        UUID legacy=id(459); offering(legacy,null,"ACTIVE");
        assertThat(getJson("/api/v1/customers/owner-candidates?offeringId="+legacy,loginAs("admin"),200).isEmpty()).isTrue();
    }
    @Test void leadTransitionsRejectClosedAndStaleUpdates() throws Exception {
        UUID l=id(423); lead(l,SALE,OFFER,"transition@example.test","NEW"); JsonNode sales=loginAs("sale");
        error(jsonRequest(patch("/api/v1/leads/"+l),sales,Map.of("expectedUpdatedAt",START.minusSeconds(9).toString(),"displayName","X")),409,"LEAD_CHANGED");
        error(jsonRequest(post("/api/v1/leads/"+l+"/disqualify"),sales,Map.of("expectedUpdatedAt",time().toInstant().toString())),400,"VALIDATION_ERROR");
        JsonNode qualified=response(jsonRequest(post("/api/v1/leads/"+l+"/qualify"),sales,Map.of("expectedUpdatedAt",time().toInstant().toString())),200);
        assertThat(qualified.get("status").asText()).isEqualTo("QUALIFIED"); assertThat(qualified.get("qualifiedAt").isNull()).isFalse();
        jsonRequest(post("/api/v1/leads/"+l+"/disqualify"),sales,Map.of("expectedUpdatedAt",qualified.get("updatedAt").asText(),"reason","Not suitable")).andExpect(status().isOk());
        for(String action:List.of("LEAD_QUALIFIED","LEAD_DISQUALIFIED")) assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action=? AND entity_id=?",Integer.class,action,l)).isEqualTo(1);
        error(jsonRequest(patch("/api/v1/leads/"+l),sales,Map.of("expectedUpdatedAt",START.toString(),"displayName","X")),409,"LEAD_CLOSED");
        UUID converted=id(424), c=id(425); customer(c,SALE,"converted@example.test"); lead(converted,SALE,OFFER,"converted-lead@example.test","NEW");
        jdbc.update("UPDATE leads SET status='CONVERTED',converted_customer_id=?,converted_at=? WHERE id=?",c,time(),converted);
        error(jsonRequest(patch("/api/v1/leads/"+converted),sales,Map.of("expectedUpdatedAt",time().toInstant().toString(),"displayName","X")),409,"LEAD_CLOSED");
        UUID directlyClosed=id(452); lead(directlyClosed,SALE,OFFER,"direct-close@example.test","NEW");
        jsonRequest(post("/api/v1/leads/"+directlyClosed+"/disqualify"),sales,Map.of("expectedUpdatedAt",time().toInstant().toString(),"reason","No fit")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISQUALIFIED"));
    }
    @Test void qualifyPublishesOutboxWithQualifyingActorAndRollsBackOnFailure() throws Exception {
        UUID first=id(492), second=id(493);
        lead(first,SALE,OFFER,"outbox-a@example.test","NEW"); lead(second,SALE,OFFER,"outbox-b@example.test","NEW");
        JsonNode actor=loginAs("sale");
        jsonRequest(post("/api/v1/leads/"+first+"/qualify"),actor,Map.of("expectedUpdatedAt",START.minusSeconds(30).toString()))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id=?",Integer.class,first)).isZero();
        jsonRequest(post("/api/v1/leads/"+first+"/qualify"),actor,Map.of("expectedUpdatedAt",time().toInstant().toString()))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE organization_id=? AND aggregate_id=? AND event_type='LEAD_QUALIFIED' AND delivery_status='PENDING'",Integer.class,ORG_ONE,first)).isEqualTo(1);
        String payload=jdbc.queryForObject("SELECT CAST(payload AS VARCHAR) FROM outbox_events WHERE aggregate_id=?",String.class,first);
        assertThat(payload).contains(SALE.toString(),"qualifiedByUserId");
        jdbc.update("DELETE FROM outbox_events WHERE aggregate_id=?",first);
        jdbc.execute("ALTER TABLE outbox_events ADD CONSTRAINT reject_outbox_w08 CHECK(event_type <> 'LEAD_QUALIFIED')");
        try {
            jsonRequest(post("/api/v1/leads/"+second+"/qualify"),actor,Map.of("expectedUpdatedAt",time().toInstant().toString()))
                    .andExpect(status().is5xxServerError());
            assertThat(jdbc.queryForObject("SELECT status FROM leads WHERE id=?",String.class,second)).isEqualTo("NEW");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id=?",Integer.class,second)).isZero();
        } finally { jdbc.execute("ALTER TABLE outbox_events DROP CONSTRAINT reject_outbox_w08"); }
    }
    @Test void permissionsForCareDataAdminAndOtherTenant() throws Exception {
        UUID l=id(426); lead(l,SALE,OFFER,"access@example.test","NEW"); JsonNode care=loginAs("care"),data=loginAs("data"),admin=loginAs("admin");
        getJson("/api/v1/leads",care,403); assertThat(getJson("/api/v1/leads",data,403).get("code").asText()).isEqualTo("FORBIDDEN_SCOPE");
        getJson("/api/v1/customers",data,403); error(jsonRequest(post("/api/v1/leads"),admin,leadBody("admin@example.test",OFFER)),403,"FORBIDDEN_PERMISSION");
        getJson("/api/v1/leads/"+l,admin,200); jsonRequest(patch("/api/v1/leads/"+l),admin,Map.of("expectedUpdatedAt",time().toInstant().toString(),"displayName","Updated")).andExpect(status().isOk());
        UUID customer=id(448); customer(customer,OTHER_SALE,"admin-customer@example.test");
        getJson("/api/v1/customers/"+customer,admin,200); jsonRequest(patch("/api/v1/customers/"+customer),admin,Map.of("expectedUpdatedAt",time().toInstant().toString(),"displayName","Updated")).andExpect(status().isOk());
        error(jsonRequest(post("/api/v1/customers"),admin,customerBody("admin-customer-create@example.test",OFFER)),403,"FORBIDDEN_PERMISSION");
        getJson("/api/v1/leads/"+USER_TWO,admin,403);
    }
    @Test void actualForeignTenantRecordsRemainHiddenFromAdmin() throws Exception {
        UUID category=id(438), team=id(439), offering=id(440), lead=id(441), customer=id(442);
        team(team,ORG_TWO,"FOREIGN","Foreign",USER_TWO);
        jdbc.update("INSERT INTO offering_categories(id,organization_id,code,name,status,created_at,updated_at) VALUES (?,?,'FOREIGN','Foreign','ACTIVE',?,?)",category,ORG_TWO,time(),time());
        jdbc.update("INSERT INTO offerings(id,organization_id,category_id,team_id,code,name,kind,status,created_at,updated_at) VALUES (?,?,?,?,?,?,'SERVICE','ACTIVE',?,?)",offering,ORG_TWO,category,team,"FOREIGN","Foreign offer",time(),time());
        jdbc.update("INSERT INTO leads(id,organization_id,owner_user_id,offering_id,display_name,email,normalized_email,status,created_at,updated_at) VALUES (?,?,?,?,'Foreign lead','foreign@example.test','foreign@example.test','NEW',?,?)",lead,ORG_TWO,USER_TWO,offering,time(),time());
        jdbc.update("INSERT INTO customers(id,organization_id,owner_user_id,display_name,email,normalized_email,status,created_at,updated_at) VALUES (?,?,?,'Foreign customer','foreign@example.test','foreign@example.test','ACTIVE',?,?)",customer,ORG_TWO,USER_TWO,time(),time());
        JsonNode admin=loginAs("admin");
        for(String path:List.of("/api/v1/leads/"+lead,"/api/v1/customers/"+customer)) assertThat(getJson(path,admin,403).get("code").asText()).isEqualTo("FORBIDDEN_SCOPE");
    }
    @Test void normalizedPhoneDuplicatesAreBlockedOnCreateAndPatch() throws Exception {
        UUID customer=id(443),lead=id(444); customer(customer,SALE,"phone-customer@example.test"); lead(lead,SALE,OFFER,"phone-lead@example.test","NEW");
        jdbc.update("UPDATE customers SET phone=?,normalized_phone=? WHERE id=?","0912 345 678","+84912345678",customer);
        jdbc.update("UPDATE leads SET phone=?,normalized_phone=? WHERE id=?","0912 345 678","+84912345678",lead);
        JsonNode sales=loginAs("sale"); var cb=customerBody("new-phone@example.test",OFFER); cb.put("phone","84912345678");
        jsonRequest(post("/api/v1/customers"),sales,cb).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CUSTOMER_DUPLICATE")).andExpect(jsonPath("$.details.duplicateId").value(customer.toString()));
        var lb=leadBody("new-phone@example.test",OFFER); lb.put("phone","+84 912-345-678");
        jsonRequest(post("/api/v1/leads"),sales,lb).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LEAD_DUPLICATE")).andExpect(jsonPath("$.details.duplicateId").value(lead.toString()));
        UUID hidden=id(449); customer(hidden,OTHER_SALE,"hidden-phone@example.test");
        jdbc.update("UPDATE customers SET phone=?,normalized_phone=? WHERE id=?","0933 123 456","+84933123456",hidden);
        var outside=customerBody("outside-phone@example.test",OFFER); outside.put("phone","+84 933-123-456");
        String body=jsonRequest(post("/api/v1/customers"),sales,outside).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CUSTOMER_DUPLICATE")).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(hidden.toString(),"duplicateId","hidden-phone@example.test");
        UUID secondCustomer=id(455),secondLead=id(456); customer(secondCustomer,SALE,"phone-edit-customer@example.test"); lead(secondLead,SALE,OFFER,"phone-edit-lead@example.test","NEW");
        jsonRequest(patch("/api/v1/customers/"+secondCustomer),sales,Map.of("phone","84912345678","expectedUpdatedAt",time().toInstant().toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CUSTOMER_DUPLICATE"));
        jsonRequest(patch("/api/v1/leads/"+secondLead),sales,Map.of("phone","84912345678","expectedUpdatedAt",time().toInstant().toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LEAD_DUPLICATE"));
    }
    @Test void roleUnionDoesNotBorrowWritePermissionForAdminScope() throws Exception {
        UUID other=id(445); lead(other,OTHER_SALE,OTHER_OFFER,"role-union@example.test","NEW"); userRole(ORG_ONE,SALE,"ADMIN");
        jdbc.update("DELETE FROM role_permissions WHERE role_id=(SELECT id FROM roles WHERE organization_id=? AND code='ADMIN') AND permission_id IN (SELECT id FROM permissions WHERE code IN ('lead.read','lead.write'))",ORG_ONE);
        JsonNode sales=loginAs("sale"); getJson("/api/v1/leads/"+other,sales,200);
        error(jsonRequest(patch("/api/v1/leads/"+other),sales,Map.of("displayName","Blocked","expectedUpdatedAt",time().toInstant().toString())),403,"FORBIDDEN_SCOPE");
    }
    @Test void offeringReadPermissionFromAnotherRoleStillAllowsSalesScope() throws Exception {
        userRole(ORG_ONE,SALE,"CUSTOMER_CARE"); grantOnce("CUSTOMER_CARE","offering.read");
        jdbc.update("DELETE FROM role_permissions WHERE role_id=(SELECT id FROM roles WHERE organization_id=? AND code='SALES_REP') AND permission_id=(SELECT id FROM permissions WHERE code='offering.read')",ORG_ONE);
        JsonNode sales=loginAs("sale"); JsonNode lead=response(jsonRequest(post("/api/v1/leads"),sales,leadBody("union-read@example.test",OFFER)),201);
        getJson("/api/v1/leads/"+lead.get("id").asText(),sales,200);
        assertThat(getJson("/api/v1/leads/owner-candidates?offeringId="+OFFER,loginOne(),200).toString()).contains(SALE.toString());
    }
    @Test void changedLeadOfferingRequiresEligibleOwner() throws Exception {
        UUID lead=id(446); lead(lead,SALE,OFFER,"offering-change@example.test","NEW"); JsonNode manager=loginOne();
        error(jsonRequest(patch("/api/v1/leads/"+lead),manager,Map.of("offeringId",OTHER_OFFER,"expectedUpdatedAt",time().toInstant().toString())),403,"FORBIDDEN_SCOPE");
        assertThat(jdbc.queryForObject("SELECT offering_id FROM leads WHERE id=?",UUID.class,lead)).isEqualTo(OFFER);
    }
    @Test void customerScopeForSalesCareAndManagerUsesLiveAssignmentsAndMembership() throws Exception {
        UUID own=id(427), assigned=id(428), other=id(429); customer(own,SALE,"own@example.test"); customer(assigned,OTHER_SALE,"assigned@example.test"); customer(other,OTHER_SALE,"other-customer@example.test");
        customerAssignment(id(430),assigned,SALE,"SALES",START.minusSeconds(1),null,"ACTIVE"); customerAssignment(id(431),assigned,CARE,"CARE",START.minusSeconds(1),null,"ACTIVE");
        JsonNode sales=loginAs("sale"),care=loginAs("care"),manager=loginOne();
        String list=getJson("/api/v1/customers",sales,200).toString(); assertThat(list).contains(own.toString(),assigned.toString()).doesNotContain(other.toString()); getJson("/api/v1/customers/"+other,sales,403);
        jdbc.update("UPDATE customer_assignments SET effective_to=? WHERE id=?",Timestamp.from(START.plusSeconds(10)),id(430));
        clock.now=START.plusSeconds(11); getJson("/api/v1/customers/"+assigned,sales,403); clock.now=START;
        assertThat(getJson("/api/v1/customers",care,200).toString()).contains(assigned.toString()).doesNotContain(own.toString());
        error(jsonRequest(patch("/api/v1/customers/"+assigned),care,Map.of("expectedUpdatedAt",time().toInstant().toString(),"displayName","X")),403,"FORBIDDEN_PERMISSION");
        assertThat(getJson("/api/v1/customers",manager,200).toString()).contains(own.toString(),assigned.toString()).doesNotContain(other.toString());
        jdbc.update("UPDATE customer_assignments SET effective_from=? WHERE id=?",Timestamp.from(START.plusSeconds(10)),id(431)); getJson("/api/v1/customers/"+assigned,care,403);
        jdbc.update("UPDATE customer_assignments SET status='ENDED' WHERE id=?",id(431)); getJson("/api/v1/customers/"+assigned,care,403);
        jdbc.update("UPDATE team_memberships SET status='ENDED',left_at=? WHERE id=?",Timestamp.from(START),id(410)); getJson("/api/v1/customers/"+own,manager,403); getJson("/api/v1/customers/"+assigned,manager,403);
    }
    @Test void customerPatchValidatesVersionOwnerAndDuplicate() throws Exception {
        UUID first=id(432),second=id(433); customer(first,SALE,"first@example.test"); customer(second,SALE,"second@example.test"); JsonNode sales=loginAs("sale");
        error(jsonRequest(patch("/api/v1/customers/"+first),sales,Map.of("ownerUserId",OTHER_SALE,"expectedUpdatedAt",time().toInstant().toString())),400,"VALIDATION_ERROR");
        error(jsonRequest(patch("/api/v1/customers/"+first),sales,Map.of("email","second@example.test","expectedUpdatedAt",time().toInstant().toString())),409,"CUSTOMER_DUPLICATE");
        error(jsonRequest(patch("/api/v1/customers/"+first),sales,Map.of("displayName","X","expectedUpdatedAt",START.minusSeconds(9).toString())),409,"CUSTOMER_CHANGED");
    }
    @Test void profileUpdatesPersistWithoutExposingContactOrNotesInAudit() throws Exception {
        UUID l=id(453),c=id(454); lead(l,SALE,OFFER,"profile-lead@example.test","NEW"); customer(c,SALE,"profile-customer@example.test");
        JsonNode sales=loginAs("sale");
        JsonNode updatedLead=response(jsonRequest(patch("/api/v1/leads/"+l),sales,Map.of("expectedUpdatedAt",time().toInstant().toString(),"displayName","Lead updated","source","EVENT","phone","0912 345 678","notes","private lead note")),200);
        assertThat(updatedLead.get("source").asText()).isEqualTo("EVENT");
        assertThat(jdbc.queryForObject("SELECT normalized_phone FROM leads WHERE id=?",String.class,l)).isEqualTo("+84912345678");
        JsonNode updatedCustomer=response(jsonRequest(patch("/api/v1/customers/"+c),sales,Map.of("expectedUpdatedAt",time().toInstant().toString(),"customerType","BUSINESS","address","Private address","notes","private customer note")),200);
        assertThat(updatedCustomer.get("customerType").asText()).isEqualTo("BUSINESS");
        String audit=jdbc.queryForObject("SELECT CAST(after_data AS VARCHAR) FROM audit_logs WHERE action='CUSTOMER_UPDATED' AND entity_id=?",String.class,c);
        assertThat(audit).doesNotContain("private customer note","Private address","profile-customer@example.test");
    }
    @Test void filtersApplyAfterScopeAndSalesCannotFilterByOwner() throws Exception {
        UUID l=id(457),c=id(458); lead(l,SALE,OFFER,"filter-lead@example.test","NEW"); customer(c,SALE,"filter-customer@example.test");
        jdbc.update("UPDATE customers SET customer_type='BUSINESS' WHERE id=?",c);
        JsonNode sales=loginAs("sale"),manager=loginOne();
        assertThat(getJson("/api/v1/leads?search=filter-lead&status=NEW&offeringId="+OFFER,sales,200).toString()).contains(l.toString());
        assertThat(getJson("/api/v1/customers?search=filter-customer&customerType=BUSINESS&status=ACTIVE",sales,200).toString()).contains(c.toString());
        assertThat(getJson("/api/v1/leads?ownerUserId="+SALE,manager,200).toString()).contains(l.toString());
        assertThat(getJson("/api/v1/customers?ownerUserId="+SALE,sales,403).get("code").asText()).isEqualTo("FORBIDDEN_PERMISSION");
    }
    @Test void legacyNullFieldsAreReturned() throws Exception {
        UUID l=id(434),c=id(435); lead(l,SALE,OFFER,"oldlead@example.test","NEW"); customer(c,SALE,"oldcustomer@example.test"); JsonNode sales=loginAs("sale");
        assertThat(getJson("/api/v1/leads/"+l,sales,200).get("source").isNull()).isTrue();
        JsonNode result=getJson("/api/v1/customers/"+c,sales,200); assertThat(result.get("customerType").isNull()).isTrue(); assertThat(result.get("relatedOfferingId").isNull()).isTrue();
    }
    @Test void archivedRecordsAreHiddenAndOnlyArchivedLeadStopsBlockingDuplicates() throws Exception {
        UUID l=id(450),c=id(451); lead(l,SALE,OFFER,"archived-lead@example.test","NEW"); customer(c,SALE,"archived-customer@example.test");
        jdbc.update("UPDATE leads SET archived_at=? WHERE id=?",time(),l); jdbc.update("UPDATE customers SET archived_at=? WHERE id=?",time(),c);
        JsonNode sales=loginAs("sale"); assertThat(getJson("/api/v1/leads",sales,200).toString()).doesNotContain(l.toString());
        assertThat(getJson("/api/v1/customers",sales,200).toString()).doesNotContain(c.toString());
        jsonRequest(post("/api/v1/leads"),sales,leadBody("archived-lead@example.test",OFFER)).andExpect(status().isCreated());
        String body=jsonRequest(post("/api/v1/customers"),sales,customerBody("archived-customer@example.test",OFFER)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CUSTOMER_DUPLICATE")).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(c.toString(),"duplicateId");
    }
    @Test void closedLeadsDoNotBlockReturningContactButOpenLeadsDo() throws Exception {
        UUID disqualified=id(452),converted=id(453),qualified=id(454),c=id(455); customer(c,SALE,"converted-customer@example.test");
        lead(disqualified,SALE,OFFER,"disqualified@example.test","DISQUALIFIED"); lead(qualified,SALE,OFFER,"qualified@example.test","QUALIFIED");
        lead(converted,SALE,OFFER,"converted@example.test","NEW");
        jdbc.update("UPDATE leads SET status='CONVERTED',converted_customer_id=?,converted_at=? WHERE id=?",c,time(),converted);
        JsonNode sales=loginAs("sale");
        jsonRequest(post("/api/v1/leads"),sales,leadBody("disqualified@example.test",OFFER)).andExpect(status().isCreated());
        jsonRequest(post("/api/v1/leads"),sales,leadBody("converted@example.test",OFFER)).andExpect(status().isCreated());
        jsonRequest(post("/api/v1/leads"),sales,leadBody("qualified@example.test",OFFER)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEAD_DUPLICATE")).andExpect(jsonPath("$.details.duplicateId").value(qualified.toString()));
    }
    @Test void duplicateCustomerConcurrentCreatesAreSerialized() throws Exception {
        JsonNode sales=loginAs("sale"); CountDownLatch start=new CountDownLatch(1);
        Callable<MvcResult> create=()->{ start.await(); return jsonRequest(post("/api/v1/customers"),sales,customerBody("race@example.test",OFFER)).andReturn(); };
        List<MvcResult> results; try(ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor()) { var a=executor.submit(create); var b=executor.submit(create); start.countDown(); results=List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS)); }
        assertThat(results.stream().map(r->r.getResponse().getStatus()).toList()).containsExactlyInAnyOrder(201,409);
        var conflict=results.stream().filter(r->r.getResponse().getStatus()==409).findFirst().orElseThrow(); assertThat(mapper.readTree(conflict.getResponse().getContentAsString()).get("code").asText()).isEqualTo("CUSTOMER_DUPLICATE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customers WHERE normalized_email='race@example.test'",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action='CUSTOMER_CREATED'",Integer.class)).isEqualTo(1);
    }
    @Test void auditFailureRollsBackCreatesAndUpdates() throws Exception {
        UUID l=id(436),c=id(437); lead(l,SALE,OFFER,"audit-lead@example.test","NEW"); customer(c,SALE,"audit-customer@example.test"); JsonNode sales=loginAs("sale");
        long leads=count("leads"),customers=count("customers"),assignments=count("customer_assignments"),audits=count("audit_logs");
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT reject_crm_audit CHECK(action NOT IN ('LEAD_CREATED','LEAD_UPDATED','CUSTOMER_CREATED','CUSTOMER_UPDATED'))");
        try {
            error(jsonRequest(post("/api/v1/leads"),sales,leadBody("audit-newlead@example.test",OFFER)),409,"CONFLICT");
            error(jsonRequest(post("/api/v1/customers"),sales,customerBody("audit-newcustomer@example.test",OFFER)),409,"CONFLICT");
            error(jsonRequest(patch("/api/v1/leads/"+l),sales,Map.of("displayName","Changed","expectedUpdatedAt",time().toInstant().toString())),409,"CONFLICT");
            error(jsonRequest(patch("/api/v1/customers/"+c),sales,Map.of("displayName","Changed","expectedUpdatedAt",time().toInstant().toString())),409,"CONFLICT");
            assertThat(count("leads")).isEqualTo(leads); assertThat(count("customers")).isEqualTo(customers); assertThat(count("customer_assignments")).isEqualTo(assignments); assertThat(count("audit_logs")).isEqualTo(audits);
            assertThat(jdbc.queryForObject("SELECT display_name FROM leads WHERE id=?",String.class,l)).isEqualTo("Legacy lead"); assertThat(jdbc.queryForObject("SELECT display_name FROM customers WHERE id=?",String.class,c)).isEqualTo("Legacy customer");
        } finally { jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT reject_crm_audit"); }
    }
}
