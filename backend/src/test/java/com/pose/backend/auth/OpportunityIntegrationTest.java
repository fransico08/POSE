package com.pose.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties="pose.sales-assignment.expiry-scan-delay-ms=86400000")
class OpportunityIntegrationTest extends FoundationTestSupport {
    private static final UUID CAT=id(601), FIRST=id(602), SECOND=id(603), FOREIGN=id(604), SALE=id(605), OTHER=id(606), CARE=id(607), DATA=id(608), ADMIN=id(609), CUSTOMER=id(610);
    private static final String URL="/api/v1/opportunities";
    @BeforeEach void fixtures() {
        for(String code:List.of("opportunity.read","opportunity.write","opportunity.reopen","offering.read")) if(jdbc.queryForObject("SELECT count(*) FROM permissions WHERE code=?",Integer.class,code)==0) permission(code);
        for(String role:List.of("ADMIN","DATA_STAFF")) role(ORG_ONE,role);
        for(String role:List.of("ADMIN","MANAGER","SALES_REP")) { grant(ORG_ONE,role,"opportunity.read");grant(ORG_ONE,role,"opportunity.write"); }
        grant(ORG_ONE,"ADMIN","opportunity.reopen");grant(ORG_ONE,"MANAGER","opportunity.reopen");grant(ORG_ONE,"DATA_STAFF","opportunity.read");grant(ORG_ONE,"SALES_REP","offering.read");
        for(var e:Map.of(SALE,"sale",OTHER,"other",CARE,"care",DATA,"data",ADMIN,"admin").entrySet()) user(e.getKey(),ORG_ONE,e.getValue(),e.getValue()+"@example.test",hashOne);
        userRole(ORG_ONE,SALE,"SALES_REP");userRole(ORG_ONE,OTHER,"SALES_REP");userRole(ORG_ONE,CARE,"CUSTOMER_CARE");userRole(ORG_ONE,DATA,"DATA_STAFF");userRole(ORG_ONE,ADMIN,"ADMIN");
        for(var e:Map.of(SALE,TEAM_MANAGED,OTHER,TEAM_MEMBER_ONLY).entrySet()) jdbc.update("INSERT INTO team_memberships(id,organization_id,team_id,user_id,joined_at,status) VALUES (?,?,?,?,?,'ACTIVE')",UUID.randomUUID(),ORG_ONE,e.getValue(),e.getKey(),time());
        jdbc.update("INSERT INTO offering_categories(id,organization_id,code,name,status,created_at,updated_at) VALUES (?,?,'CAT','Category','ACTIVE',?,?)",CAT,ORG_ONE,time(),time());
        offering(FIRST,TEAM_MANAGED,"ACTIVE");offering(SECOND,TEAM_MANAGED,"ACTIVE");offering(FOREIGN,TEAM_MEMBER_ONLY,"ACTIVE");
        assign(FIRST,SALE); assign(SECOND,SALE); assign(FOREIGN,OTHER);
        customer(CUSTOMER,SALE);
    }
    private void offering(UUID id,UUID team,String status) { jdbc.update("INSERT INTO offerings(id,organization_id,category_id,team_id,code,name,kind,status,created_at,updated_at) VALUES (?,?,?,?,?,?,'SERVICE',?,?,?)",id,ORG_ONE,CAT,team,"O-"+id,"Offer "+id,status,time(),time()); }
    private void assign(UUID offering,UUID sale) { jdbc.update("INSERT INTO sales_assignments(id,organization_id,offering_id,sales_user_id,assigned_by_user_id,status,effective_from,created_at,updated_at) VALUES (?,?,?,?,?,'ACTIVE',?,?,?)",UUID.randomUUID(),ORG_ONE,offering,sale,USER_ONE,time(),time(),time()); }
    private void customer(UUID id,UUID owner) { jdbc.update("INSERT INTO customers(id,organization_id,owner_user_id,display_name,email,normalized_email,status,created_at,updated_at) VALUES (?,?,?,'Customer',?,?,'ACTIVE',?,?)",id,ORG_ONE,owner,id+"@example.test",id+"@example.test",time(),time()); }
    private JsonNode actor(String name) throws Exception { return response(login("one",name+"@example.test",PASSWORD_ONE),200); }
    private Map<String,Object> item(UUID offering,Object quantity,Object price,Object discount) { return Map.of("offeringId",offering.toString(),"quantity",quantity,"unitPrice",price,"discountAmount",discount); }
    private Map<String,Object> body() { var b=new LinkedHashMap<String,Object>();b.put("customerId",CUSTOMER.toString());b.put("title","Opportunity test");b.put("stage","NEW");b.put("expectedCloseDate","2026-10-01");b.put("lines",List.of(item(FIRST,"2","1500000","200000"),item(SECOND,"1","999999.99","0")));return b; }
    private JsonNode create(JsonNode actor) throws Exception { return response(jsonRequest(post(URL),actor,body()),201); }
    private JsonNode getAs(String path,JsonNode actor,int code) throws Exception { return response(mvc.perform(get(path).header("Authorization",bearer(actor))),code); }
    private void error(ResultActions r,int status,String code) throws Exception { r.andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code)); }
    private void field(ResultActions r,String field) throws Exception { r.andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value(field)); }
    private String version(JsonNode node) { return node.get("updatedAt").asText(); }
    @Test void salesCreatesTwoLinesWithCalculatedTotalsAndSingleAudit() throws Exception {
        JsonNode a=actor("sale"), created=create(a); UUID id=UUID.fromString(created.get("id").asText());
        assertThat(created.get("expectedValue").asText()).isEqualTo("3799999.99");assertThat(created.get("lines").get(0).get("lineTotal").asText()).isEqualTo("2800000.00");
        assertThat(jdbc.queryForObject("SELECT offering_id FROM opportunities WHERE id=?",UUID.class,id)).isEqualTo(FIRST);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_id=? AND action='OPPORTUNITY_CREATED'",Integer.class,id)).isEqualTo(1);
    }
    @Test void listSearchMatchesTitleCustomerAndLineOffering() throws Exception {
        UUID named=id(670);
        jdbc.update("INSERT INTO customers(id,organization_id,owner_user_id,display_name,email,normalized_email,status,created_at,updated_at) VALUES (?,?,?,'Sunrise Holdings',?,?,'ACTIVE',?,?)",named,ORG_ONE,SALE,"sunrise@example.test","sunrise@example.test",time(),time());
        JsonNode sale=actor("sale"); var b=body(); b.put("customerId",named.toString()); b.put("title","Quarterly plan"); b.put("lines",List.of(item(FIRST,"1","10","0")));
        response(jsonRequest(post(URL),sale,b),201); create(sale);
        for(var entry:Map.of("QUARTERLY",1,"sunrise",1,"O-"+SECOND,1,"O-"+FIRST,2,"no-match",0,"%",0).entrySet())
            assertThat(getAs(URL+"?search="+java.net.URLEncoder.encode(entry.getKey(),java.nio.charset.StandardCharsets.UTF_8),sale,200).get("totalItems").asInt()).as(entry.getKey()).isEqualTo(entry.getValue());
    }
    @Test void validationRejectsMissingBadAndUntrustedFields() throws Exception {
        JsonNode a=actor("sale"); for(String key:List.of("customerId","title","stage","expectedCloseDate","lines")) {var b=body();b.remove(key);field(jsonRequest(post(URL),a,b),key);}
        for(var entry:Map.of("quantity",List.of("0","1.234"),"unitPrice",List.of("-1"),"discountAmount",List.of("999999999")).entrySet()) for(String value:entry.getValue()) {
            var b=body();var line=new LinkedHashMap<>(item(FIRST,"1","10","0"));line.put(entry.getKey(),value);b.put("lines",List.of(line));field(jsonRequest(post(URL),a,b),"lines[0]."+entry.getKey());
        }
        var b=body();b.put("lines",List.of(item(FIRST,"1","1","0"),item(FIRST,"1","1","0")));field(jsonRequest(post(URL),a,b),"lines[1].offeringId");
        b=body();b.put("lines",Collections.nCopies(51,item(FIRST,"1","1","0")));field(jsonRequest(post(URL),a,b),"lines");
        b=body();b.put("expectedValue","1");field(jsonRequest(post(URL),a,b),"expectedValue");
        b=body();var forbiddenLine=new LinkedHashMap<>(item(FIRST,"1","1","0"));forbiddenLine.put("lineTotal","1");b.put("lines",List.of(forbiddenLine));field(jsonRequest(post(URL),a,b),"lines[0].lineTotal");
        b=body();b.put("expectedCloseDate","2026-09-30");field(jsonRequest(post(URL),a,b),"expectedCloseDate");create(a);
    }
    @Test void lineScopeErrorsAreUniformAndClosedActionsIgnoreInactiveLines() throws Exception {
        UUID unassigned=id(611),inactive=id(612),external=id(613),foreignCategory=id(614);
        offering(unassigned,TEAM_MANAGED,"ACTIVE");offering(inactive,TEAM_MANAGED,"INACTIVE");
        jdbc.update("INSERT INTO offering_categories(id,organization_id,code,name,status,created_at,updated_at) VALUES (?,?,'FC','Foreign','ACTIVE',?,?)",foreignCategory,ORG_TWO,time(),time());
        jdbc.update("INSERT INTO offerings(id,organization_id,category_id,code,name,kind,status,created_at,updated_at) VALUES (?,?,?,'F','Foreign','SERVICE','ACTIVE',?,?)",external,ORG_TWO,foreignCategory,time(),time());
        JsonNode a=actor("sale"); String message=null;
        for(UUID o:List.of(unassigned,inactive,FOREIGN,external)) {var b=body();b.put("lines",List.of(item(FIRST,"1","1","0"),item(o,"1","1","0"))); JsonNode failure=response(jsonRequest(post(URL),a,b),400);JsonNode f=failure.get("fieldErrors").get(0);assertThat(f.get("field").asText()).isEqualTo("lines[1].offeringId"); if(message==null) message=f.get("message").asText();else assertThat(f.get("message").asText()).isEqualTo(message);assertThat(failure.toString()).doesNotContain(o.toString()); }
        UUID hidden=id(615);customer(hidden,OTHER);var b=body();b.put("customerId",hidden.toString());field(jsonRequest(post(URL),a,b),"customerId");
        JsonNode c=create(a);String id=c.get("id").asText();jdbc.update("UPDATE offerings SET status='INACTIVE' WHERE id=?",FIRST);
        field(jsonRequest(patch(URL+"/"+id),a,Map.of("expectedUpdatedAt",version(c))),"lines[0].offeringId");
        jsonRequest(post(URL+"/"+id+"/win"),a,Map.of("expectedUpdatedAt",version(c),"finalValue","25","actualCloseDate","2026-10-01")).andExpect(status().isOk());
    }
    @Test void managerOwnersCandidatesAndOwnerEligibility() throws Exception {
        JsonNode m=loginOne(),sale=actor("sale");var b=body();b.put("ownerUserId",SALE.toString());JsonNode c=response(jsonRequest(post(URL),m,b),201);
        jdbc.update("INSERT INTO team_memberships(id,organization_id,team_id,user_id,joined_at,status) VALUES (?,?,?,?,?,'ACTIVE')",id(651),ORG_ONE,TEAM_MANAGED,OTHER,time());
        assign(FIRST,OTHER);assign(SECOND,OTHER);
        jdbc.update("INSERT INTO customer_assignments(id,organization_id,customer_id,user_id,assignment_type,effective_from,status,assigned_by_user_id,source,created_at) VALUES (?,?,?,?,'SALES',?,'ACTIVE',?,'MANUAL',?)",id(652),ORG_ONE,CUSTOMER,OTHER,time(),USER_ONE,time());
        b.put("ownerUserId",OTHER.toString());response(jsonRequest(post(URL),m,b),201);response(jsonRequest(post(URL),m,b),201);
        JsonNode owners=getAs(URL+"/owners",m,200), candidates=getAs(URL+"/owner-candidates?customerId="+CUSTOMER+"&offeringIds="+FIRST+","+SECOND,m,200);
        for(JsonNode values:List.of(owners,candidates)) {assertThat(values.size()).isEqualTo(2);assertThat(values.get(0).get("id").asText()).isEqualTo(OTHER.toString());assertThat(values.get(1).get("id").asText()).isEqualTo(SALE.toString());}
        error(mvc.perform(get(URL+"/owners").header("Authorization",bearer(sale))),403,"FORBIDDEN_PERMISSION");
        jdbc.update("UPDATE sales_assignments SET status='REVOKED',revoked_at=? WHERE offering_id=? AND sales_user_id=?",time(),SECOND,SALE);
        b.put("ownerUserId",SALE.toString());
        jsonRequest(post(URL),m,b).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.fieldErrors[0].field").value("ownerUserId"));
        assertThat(c.get("owner").get("id").asText()).isEqualTo(SALE.toString());
    }
    @Test void salesReadUsesCustomerAssignmentAndLegacyLeadScope() throws Exception {
        UUID assigned=id(620),lead=id(621),legacy=id(622),otherCustomer=id(623); customer(assigned,OTHER); customer(otherCustomer,OTHER);
        jdbc.update("INSERT INTO customer_assignments(id,organization_id,customer_id,user_id,assignment_type,effective_from,status,assigned_by_user_id,source,created_at) VALUES (?,?,?,?,'SALES',?,'ACTIVE',?,'MANUAL',?)",id(624),ORG_ONE,assigned,SALE,time(),USER_ONE,time());
        jdbc.update("INSERT INTO leads(id,organization_id,owner_user_id,offering_id,display_name,email,normalized_email,status,created_at,updated_at) VALUES (?,?,?,?,?,?,?,'NEW',?,?)",lead,ORG_ONE,SALE,FIRST,"Legacy lead","legacy@example.test","legacy@example.test",time(),time());
        jdbc.update("INSERT INTO opportunities(id,organization_id,lead_id,offering_id,owner_user_id,title,stage,created_at,updated_at) VALUES (?,?,?,?,?,'Legacy','NEW',?,?)",legacy,ORG_ONE,lead,FIRST,SALE,time(),time());
        jdbc.update("INSERT INTO opportunity_lines(id,organization_id,opportunity_id,offering_id,position,quantity,unit_price,line_total,created_at,updated_at) VALUES (?,?,?,?,1,1,0,0,?,?)",id(625),ORG_ONE,legacy,FIRST,time(),time());
        JsonNode sale=actor("sale"); var b=body();b.put("customerId",assigned.toString());JsonNode c=response(jsonRequest(post(URL),sale,b),201);
        assertThat(getAs(URL,sale,200).toString()).contains(c.get("id").asText(),legacy.toString());
        jdbc.update("UPDATE customer_assignments SET status='ENDED',effective_to=? WHERE id=?",time(),id(624));
        error(mvc.perform(get(URL+"/"+c.get("id").asText()).header("Authorization",bearer(sale))),403,"FORBIDDEN_SCOPE");
        jdbc.update("UPDATE sales_assignments SET status='REVOKED',revoked_at=? WHERE offering_id=? AND sales_user_id=?",time(),FIRST,SALE);
        error(mvc.perform(get(URL+"/"+legacy).header("Authorization",bearer(sale))),403,"FORBIDDEN_SCOPE");
    }
    @Test void managerCannotChooseOwnerUnrelatedToCustomerAndAdminCanReopen() throws Exception {
        JsonNode manager=loginOne(),admin=actor("admin"),sale=actor("sale");
        UUID otherCustomer=id(626);customer(otherCustomer,OTHER);
        jdbc.update("INSERT INTO customer_assignments(id,organization_id,customer_id,user_id,assignment_type,effective_from,status,assigned_by_user_id,source,created_at) VALUES (?,?,?,?,'CARE',?,'ACTIVE',?,'MANUAL',?)",id(627),ORG_ONE,otherCustomer,USER_ONE,time(),USER_ONE,time());
        var b=body(); b.put("customerId",otherCustomer.toString());b.put("ownerUserId",SALE.toString());
        jsonRequest(post(URL),manager,b).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.fieldErrors[0].field").value("ownerUserId"));
        JsonNode c=create(sale);String id=c.get("id").asText();JsonNode lost=response(jsonRequest(post(URL+"/"+id+"/lose"),sale,Map.of("expectedUpdatedAt",version(c),"reason","Confidential")),200);
        JsonNode reopened=response(jsonRequest(post(URL+"/"+id+"/reopen"),admin,Map.of("expectedUpdatedAt",version(lost))),200);assertThat(reopened.get("stage").asText()).isEqualTo("QUALIFIED");
        String data=jdbc.queryForObject("SELECT CAST(before_data AS VARCHAR) FROM audit_logs WHERE action='OPPORTUNITY_REOPENED' AND entity_id=?",String.class,UUID.fromString(id));assertThat(data).doesNotContain("Confidential","closingReason");
    }
    @Test void managerReadIncludesManagedSecondLineWithoutExposingOtherTenant() throws Exception {
        UUID customer=id(630),opportunity=id(631);customer(customer,OTHER);
        jdbc.update("INSERT INTO opportunities(id,organization_id,customer_id,offering_id,owner_user_id,title,stage,created_at,updated_at) VALUES (?,?,?,?,?,'Team test','NEW',?,?)",opportunity,ORG_ONE,customer,FOREIGN,OTHER,time(),time());
        for(var entry:Map.of(1,FOREIGN,2,FIRST).entrySet()) jdbc.update("INSERT INTO opportunity_lines(id,organization_id,opportunity_id,offering_id,position,quantity,unit_price,line_total,created_at,updated_at) VALUES (?,?,?,?,?,1,0,0,?,?)",UUID.randomUUID(),ORG_ONE,opportunity,entry.getValue(),entry.getKey(),time(),time());
        JsonNode manager=loginOne(); assertThat(getAs(URL,manager,200).toString()).contains(opportunity.toString());getAs(URL+"/"+opportunity,manager,200);
        UUID cat=id(632),offer=id(633),foreignCustomer=id(634),foreignOpportunity=id(635);
        jdbc.update("INSERT INTO offering_categories(id,organization_id,code,name,status,created_at,updated_at) VALUES (?,?,'FC','Foreign','ACTIVE',?,?)",cat,ORG_TWO,time(),time());
        jdbc.update("INSERT INTO offerings(id,organization_id,category_id,code,name,kind,status,created_at,updated_at) VALUES (?,?,?,'FC','Foreign','SERVICE','ACTIVE',?,?)",offer,ORG_TWO,cat,time(),time());
        jdbc.update("INSERT INTO customers(id,organization_id,owner_user_id,display_name,email,normalized_email,status,created_at,updated_at) VALUES (?,?,?,'Foreign','foreign@example.test','foreign@example.test','ACTIVE',?,?)",foreignCustomer,ORG_TWO,USER_TWO,time(),time());
        jdbc.update("INSERT INTO opportunities(id,organization_id,customer_id,offering_id,owner_user_id,title,stage,created_at,updated_at) VALUES (?,?,?,?,?,'Foreign','NEW',?,?)",foreignOpportunity,ORG_TWO,foreignCustomer,offer,USER_TWO,time(),time());
        error(mvc.perform(get(URL+"/"+foreignOpportunity).header("Authorization",bearer(actor("admin")))),403,"FORBIDDEN_SCOPE");
    }
    @Test void vietnamDateBoundaryUsesClockInstant() throws Exception {
        clock.now=java.time.Instant.parse("2026-10-01T18:30:00Z");JsonNode sale=actor("sale");var b=body();b.put("expectedCloseDate","2026-10-01");field(jsonRequest(post(URL),sale,b),"expectedCloseDate");b.put("expectedCloseDate","2026-10-02");jsonRequest(post(URL),sale,b).andExpect(status().isCreated());
    }
    @Test void patchReplacesLinesEnforcesVersionAndProgress() throws Exception {
        UUID third=id(650);offering(third,TEAM_MANAGED,"ACTIVE");assign(third,SALE);
        JsonNode a=actor("sale"),c=create(a);String id=c.get("id").asText();
        JsonNode updated=response(jsonRequest(patch(URL+"/"+id),a,Map.of("expectedUpdatedAt",version(c),"stage","QUALIFIED","lines",List.of(item(SECOND,"3","100","10"),item(third,"1","5","0")))),200);
        assertThat(updated.get("expectedValue").asText()).isEqualTo("295.00");assertThat(updated.get("lines").size()).isEqualTo(2);
        assertThat(updated.get("lines").toString()).doesNotContain(FIRST.toString()).contains(third.toString());
        assertThat(jdbc.queryForObject("SELECT offering_id FROM opportunities WHERE id=?",UUID.class,UUID.fromString(id))).isEqualTo(SECOND);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_id=? AND action='OPPORTUNITY_STAGE_CHANGED'",Integer.class,UUID.fromString(id))).isEqualTo(1);
        error(jsonRequest(patch(URL+"/"+id),a,Map.of("expectedUpdatedAt",version(updated),"stage","NEW")),409,"OPPORTUNITY_STAGE_BACKWARD");
        error(jsonRequest(patch(URL+"/"+id),a,Map.of("expectedUpdatedAt",version(c),"title","Old")),409,"OPPORTUNITY_CHANGED");
        for(String key:List.of("customerId","leadId","ownerUserId")) field(jsonRequest(patch(URL+"/"+id),a,Map.of("expectedUpdatedAt",version(updated),key,CUSTOMER.toString())),key);
    }
    @Test void managerCannotAddOfferingNotAssignedToOwnerAndUpdateRollsBack() throws Exception {
        UUID added=id(660);offering(added,TEAM_MANAGED,"ACTIVE");
        JsonNode created=create(actor("sale")),manager=loginOne();UUID id=UUID.fromString(created.get("id").asText());
        jsonRequest(patch(URL+"/"+id),manager,Map.of("expectedUpdatedAt",version(created),"lines",List.of(item(FIRST,"1","5","0"),item(added,"1","10","0"))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("lines[1].offeringId"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Người phụ trách chưa được phân công sản phẩm/dịch vụ này."));
        assertThat(jdbc.queryForObject("SELECT expected_value FROM opportunities WHERE id=?",java.math.BigDecimal.class,id)).isEqualByComparingTo("3799999.99");
        assertThat(jdbc.queryForList("SELECT offering_id FROM opportunity_lines WHERE opportunity_id=? ORDER BY position",UUID.class,id)).containsExactly(FIRST,SECOND);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_id=? AND action='OPPORTUNITY_UPDATED'",Integer.class,id)).isZero();
    }
    @Test void managerCanAddOfferingAssignedToOwner() throws Exception {
        UUID added=id(661);offering(added,TEAM_MANAGED,"ACTIVE");assign(added,SALE);
        JsonNode created=create(actor("sale")),manager=loginOne();String id=created.get("id").asText();
        JsonNode updated=response(jsonRequest(patch(URL+"/"+id),manager,Map.of("expectedUpdatedAt",version(created),"lines",List.of(item(FIRST,"2","1500000","200000"),item(added,"1","10","0")))),200);
        assertThat(updated.get("lines").size()).isEqualTo(2);assertThat(updated.get("lines").get(1).get("offeringId").asText()).isEqualTo(added.toString());
        assertThat(updated.get("expectedValue").asText()).isEqualTo("2800010.00");
    }
    @Test void adminCannotAddOfferingNotAssignedToOwner() throws Exception {
        UUID added=id(662);offering(added,TEAM_MANAGED,"ACTIVE");
        JsonNode created=create(actor("sale")),admin=actor("admin");String id=created.get("id").asText();
        jsonRequest(patch(URL+"/"+id),admin,Map.of("expectedUpdatedAt",version(created),"lines",List.of(item(FIRST,"1","5","0"),item(added,"1","10","0"))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("lines[1].offeringId"));
    }
    @Test void managerMayEditExistingLinesAfterOwnersAssignmentIsRevoked() throws Exception {
        JsonNode created=create(actor("sale")),manager=loginOne();String id=created.get("id").asText();
        jdbc.update("UPDATE sales_assignments SET status='REVOKED',revoked_at=? WHERE offering_id=? AND sales_user_id=?",time(),FIRST,SALE);
        JsonNode renamed=response(jsonRequest(patch(URL+"/"+id),manager,Map.of("expectedUpdatedAt",version(created),"title","Still editable")),200);
        JsonNode updated=response(jsonRequest(patch(URL+"/"+id),manager,Map.of("expectedUpdatedAt",version(renamed),"lines",List.of(item(SECOND,"1","999999.99","0"),item(FIRST,"3","1500000","200000")))),200);
        assertThat(updated.get("expectedValue").asText()).isEqualTo("5299999.99");assertThat(updated.get("lines").get(1).get("offeringId").asText()).isEqualTo(FIRST.toString());
    }
    @Test void editorScopeErrorTakesPriorityOverNewOfferingOwnerCheck() throws Exception {
        UUID unassigned=id(663);offering(unassigned,TEAM_MANAGED,"ACTIVE");
        JsonNode created=create(actor("sale")),manager=loginOne();String id=created.get("id").asText();
        jsonRequest(patch(URL+"/"+id),manager,Map.of("expectedUpdatedAt",version(created),"lines",List.of(item(unassigned,"1","1","0"),item(FOREIGN,"1","1","0"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("lines[1].offeringId"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Sản phẩm/dịch vụ không còn hoạt động hoặc không thuộc phạm vi của bạn."));
    }
    @Test void savingLegacyNullExpectedValueCalculatesZeroFromBackfilledLines() throws Exception {
        JsonNode created=create(actor("sale")),manager=loginOne();UUID id=UUID.fromString(created.get("id").asText());
        jdbc.update("UPDATE opportunity_lines SET unit_price=0,discount_amount=0,line_total=0 WHERE opportunity_id=?",id);
        jdbc.update("UPDATE opportunities SET expected_value=NULL WHERE id=?",id);
        JsonNode updated=response(jsonRequest(patch(URL+"/"+id),manager,Map.of("expectedUpdatedAt",version(created),"title","Legacy updated")),200);
        assertThat(updated.get("expectedValue").asText()).isEqualTo("0.00");
    }
    @Test void closeLockReopenAndAuditRedactReason() throws Exception {
        JsonNode sale=actor("sale"),manager=loginOne(),c=create(sale);String id=c.get("id").asText();
        field(jsonRequest(post(URL+"/"+id+"/win"),sale,Map.of("expectedUpdatedAt",version(c),"finalValue","10","actualCloseDate","2026-10-02")),"actualCloseDate");
        for(String action:List.of("lose","cancel")) field(jsonRequest(post(URL+"/"+id+"/"+action),sale,Map.of("expectedUpdatedAt",version(c))),"reason");
        JsonNode won=response(jsonRequest(post(URL+"/"+id+"/win"),sale,Map.of("expectedUpdatedAt",version(c),"finalValue","200","actualCloseDate","2026-10-01")),200);
        for(String action:List.of("win","lose","cancel")) error(jsonRequest(post(URL+"/"+id+"/"+action),sale,Map.of("expectedUpdatedAt",version(won))),409,"OPPORTUNITY_LOCKED");
        error(jsonRequest(patch(URL+"/"+id),sale,Map.of("expectedUpdatedAt",version(won))),409,"OPPORTUNITY_LOCKED");
        error(jsonRequest(post(URL+"/"+id+"/reopen"),sale,Map.of("expectedUpdatedAt",version(won))),403,"FORBIDDEN_PERMISSION");
        JsonNode reopened=response(jsonRequest(post(URL+"/"+id+"/reopen"),manager,Map.of("expectedUpdatedAt",version(won))),200);
        assertThat(reopened.get("stage").asText()).isEqualTo("QUALIFIED");for(String field:List.of("closedAt","finalValue","actualCloseDate","closingReason")) assertThat(reopened.get(field).isNull()).isTrue();
        String audit=jdbc.queryForObject("SELECT CAST(before_data AS VARCHAR) FROM audit_logs WHERE action='OPPORTUNITY_REOPENED' AND entity_id=?",String.class,UUID.fromString(id));
        assertThat(audit).contains("stage","finalValue","actualCloseDate","closedAt").doesNotContain("closingReason","notes");
    }
    @Test void permissionsAndTenantScopeAreEnforced() throws Exception {
        JsonNode sale=actor("sale"),c=create(sale);String id=c.get("id").asText();
        error(mvc.perform(get(URL).header("Authorization",bearer(actor("care")))),403,"FORBIDDEN_PERMISSION");
        error(mvc.perform(get(URL).header("Authorization",bearer(actor("data")))),403,"FORBIDDEN_SCOPE");
        JsonNode admin=actor("admin");error(jsonRequest(post(URL),admin,body()),403,"FORBIDDEN_PERMISSION");
        jsonRequest(patch(URL+"/"+id),admin,Map.of("expectedUpdatedAt",version(c),"title","Admin" )).andExpect(status().isOk());
        error(mvc.perform(get(URL+"/"+id).header("Authorization",bearer(actor("other")))),403,"FORBIDDEN_SCOPE");
        error(mvc.perform(get(URL+"/"+UUID.randomUUID()).header("Authorization",bearer(admin))),403,"FORBIDDEN_SCOPE");
    }
    @Test void writePermissionCannotBorrowAnotherRolesScope() throws Exception {
        UUID customer=id(640),opportunity=id(641);customer(customer,OTHER);
        jdbc.update("UPDATE teams SET manager_user_id=? WHERE id=?",SALE,TEAM_MANAGED);userRole(ORG_ONE,SALE,"MANAGER");
        jdbc.update("DELETE FROM role_permissions WHERE role_id=(SELECT id FROM roles WHERE organization_id=? AND code='MANAGER') AND permission_id=(SELECT id FROM permissions WHERE code='opportunity.write')",ORG_ONE);
        jdbc.update("INSERT INTO opportunities(id,organization_id,customer_id,offering_id,owner_user_id,title,stage,created_at,updated_at) VALUES (?,?,?,?,?,'Scope','NEW',?,?)",opportunity,ORG_ONE,customer,FIRST,OTHER,time(),time());
        jdbc.update("INSERT INTO opportunity_lines(id,organization_id,opportunity_id,offering_id,position,quantity,unit_price,line_total,created_at,updated_at) VALUES (?,?,?,?,1,1,0,0,?,?)",id(642),ORG_ONE,opportunity,FIRST,time(),time());
        JsonNode actor=actor("sale");getAs(URL+"/"+opportunity,actor,200);
        error(jsonRequest(patch(URL+"/"+opportunity),actor,Map.of("expectedUpdatedAt",time().toInstant().toString(),"title","Denied")),403,"FORBIDDEN_SCOPE");
    }
    @Test void cancelAcceptsReasonAndLocksRecord() throws Exception {
        JsonNode sale=actor("sale"),c=create(sale);String id=c.get("id").asText();
        JsonNode cancelled=response(jsonRequest(post(URL+"/"+id+"/cancel"),sale,Map.of("expectedUpdatedAt",version(c),"reason","Other")),200);
        assertThat(cancelled.get("stage").asText()).isEqualTo("CANCELLED");assertThat(cancelled.get("closingReason").asText()).isEqualTo("Other");
        error(jsonRequest(patch(URL+"/"+id),sale,Map.of("expectedUpdatedAt",version(cancelled),"title","Denied")),409,"OPPORTUNITY_LOCKED");
    }
    @Test void auditFailuresRollbackCreatePatchAndWin() throws Exception {
        JsonNode sale=actor("sale"),c=create(sale);String id=c.get("id").asText();jdbc.update("DELETE FROM audit_logs WHERE action='OPPORTUNITY_CREATED'");
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT reject_opportunity_audit CHECK(action NOT IN ('OPPORTUNITY_CREATED','OPPORTUNITY_UPDATED','OPPORTUNITY_WON'))");
        try {
            error(jsonRequest(post(URL),sale,body()),409,"CONFLICT");
            error(jsonRequest(patch(URL+"/"+id),sale,Map.of("expectedUpdatedAt",version(c),"lines",List.of(item(SECOND,"1","2","0")))),409,"CONFLICT");
            error(jsonRequest(post(URL+"/"+id+"/win"),sale,Map.of("expectedUpdatedAt",version(c),"finalValue","1","actualCloseDate","2026-10-01")),409,"CONFLICT");
            JsonNode unchanged=getAs(URL+"/"+id,sale,200);assertThat(unchanged.get("stage").asText()).isEqualTo("NEW");assertThat(unchanged.get("lines").size()).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM opportunities",Integer.class)).isEqualTo(1);
        } finally {jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT reject_opportunity_audit");}
    }
    @Test void simultaneousPatchKeepsOnlyOneWholeLineSet() throws Exception {
        JsonNode sale=actor("sale"),c=create(sale);String id=c.get("id").asText();var pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try { List<Callable<Integer>> calls=new ArrayList<>();for(UUID offering:List.of(FIRST,SECOND)) calls.add(()->{start.await();return jsonRequest(patch(URL+"/"+id),sale,Map.of("expectedUpdatedAt",version(c),"lines",List.of(item(offering,"1","100","0")))).andReturn().getResponse().getStatus();});
            var futures=calls.stream().map(pool::submit).toList();start.countDown();assertThat(List.of(futures.get(0).get(30,TimeUnit.SECONDS),futures.get(1).get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
            assertThat(getAs(URL+"/"+id,sale,200).get("lines").size()).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
}
