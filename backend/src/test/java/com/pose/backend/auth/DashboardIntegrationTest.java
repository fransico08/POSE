package com.pose.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties="pose.sales-assignment.expiry-scan-delay-ms=86400000")
class DashboardIntegrationTest extends FoundationTestSupport {
    private static final UUID SALE=id(1501), CARE=id(1502), ADMIN=id(1503), DATA=id(1504), BOTH=id(1505);
    private static final UUID CATEGORY=id(1506), OFFERING=id(1507), CUSTOMER=id(1508), LEAD=id(1509);
    private static final String PATH="/api/v1/dashboard/kpis";
    private static final String WINDOW="?start=2026-09-01T00:00:00Z&end=2026-10-01T00:00:00Z&asOf=2026-10-01T00:00:00Z";

    @BeforeEach void fixtures() {
        for(String code:List.of("dashboard.read","lead.read","offering.read","interaction.read","interaction.sensitive.read","task.manage"))
            if(jdbc.queryForObject("SELECT count(*) FROM permissions WHERE code=?",Integer.class,code)==0) permission(code);
        for(String role:List.of("ADMIN","DATA_STAFF")) role(ORG_ONE,role);
        for(String role:List.of("ADMIN","DATA_STAFF","MANAGER","SALES_REP","CUSTOMER_CARE")) grantOnce(role,"dashboard.read");
        for(String role:List.of("MANAGER","SALES_REP")) for(String code:List.of("lead.read","customer.read","interaction.read","interaction.sensitive.read","task.manage")) grantOnce(role,code);
        grantOnce("SALES_REP","offering.read"); grantOnce("CUSTOMER_CARE","task.manage"); grantOnce("CUSTOMER_CARE","interaction.read");
        user(SALE,ORG_ONE,"Sale","dash-sale@example.test",hashOne); user(CARE,ORG_ONE,"Care","dash-care@example.test",hashOne);
        user(ADMIN,ORG_ONE,"Admin","dash-admin@example.test",hashOne); user(DATA,ORG_ONE,"Data","dash-data@example.test",hashOne);
        user(BOTH,ORG_ONE,"Data manager","dash-both@example.test",hashOne);
        userRole(ORG_ONE,SALE,"SALES_REP"); userRole(ORG_ONE,CARE,"CUSTOMER_CARE"); userRole(ORG_ONE,ADMIN,"ADMIN");
        userRole(ORG_ONE,DATA,"DATA_STAFF"); userRole(ORG_ONE,BOTH,"DATA_STAFF"); userRole(ORG_ONE,BOTH,"MANAGER");
        for(UUID user:List.of(SALE,CARE)) jdbc.update("INSERT INTO team_memberships(id,organization_id,team_id,user_id,joined_at,status) VALUES (?,?,?,?,?,'ACTIVE')",UUID.randomUUID(),ORG_ONE,TEAM_MANAGED,user,time());
        jdbc.update("INSERT INTO offering_categories(id,organization_id,code,name,status,created_at,updated_at) VALUES (?,?,'DASH','Dash','ACTIVE',?,?)",CATEGORY,ORG_ONE,time(),time());
        jdbc.update("INSERT INTO offerings(id,organization_id,category_id,team_id,code,name,kind,status,created_at,updated_at) VALUES (?,?,?,?,'DASH','Dash','SERVICE','ACTIVE',?,?)",OFFERING,ORG_ONE,CATEGORY,TEAM_MANAGED,time(),time());
        jdbc.update("INSERT INTO sales_assignments(id,organization_id,offering_id,sales_user_id,assigned_by_user_id,status,effective_from,created_at,updated_at) VALUES (?,?,?,?,?,'ACTIVE',?,?,?)",id(1510),ORG_ONE,OFFERING,SALE,USER_ONE,time(),time(),time());
        customer(CUSTOMER,null); customer(id(1511),time());
        jdbc.update("INSERT INTO customer_assignments(id,organization_id,customer_id,user_id,assignment_type,effective_from,status,assigned_by_user_id,source,created_at) VALUES (?,?,?,?,'CARE',?,'ACTIVE',?,'MANUAL',?)",id(1512),ORG_ONE,CUSTOMER,CARE,time(),USER_ONE,time());
        lead(LEAD,null); lead(id(1513),time());
        interaction(id(1514),LEAD,null,SALE,"CARE_VISIBLE",START.minusSeconds(1));
        interaction(id(1515),null,CUSTOMER,SALE,"SALES_ONLY",START.minusSeconds(40L*86400));
        interaction(id(1516),null,CUSTOMER,SALE,"CARE_VISIBLE",START.plusSeconds(86400));
        task(id(1517),SALE,"OPEN",START.minusSeconds(1),null); task(id(1518),CARE,"OPEN",START.plusSeconds(100),null);
        workflow(id(1519),"DONE",0,null); workflow(id(1520),"FAILED",2,"private diagnostic");
    }
    private void grantOnce(String role,String code) {
        Integer exists=jdbc.queryForObject("SELECT count(*) FROM role_permissions rp JOIN roles r ON r.id=rp.role_id JOIN permissions p ON p.id=rp.permission_id WHERE r.organization_id=? AND r.code=? AND p.code=?",Integer.class,ORG_ONE,role,code);
        if(exists==0) grant(ORG_ONE,role,code);
    }
    private void customer(UUID id,Timestamp archived) {
        String email="customer-"+id+"@example.test";
        jdbc.update("INSERT INTO customers(id,organization_id,owner_user_id,display_name,email,normalized_email,status,created_at,updated_at,archived_at) VALUES (?,?,?,'Dash',?,?,'ACTIVE',?,?,?)",id,ORG_ONE,SALE,email,email,time(),time(),archived);
    }
    private void lead(UUID id,Timestamp archived) {
        String email="lead-"+id+"@example.test";
        jdbc.update("INSERT INTO leads(id,organization_id,owner_user_id,offering_id,display_name,email,normalized_email,status,created_at,updated_at,archived_at) VALUES (?,?,?,?,'Dash',?,?,'NEW',?,?,?)",id,ORG_ONE,SALE,OFFERING,email,email,time(),time(),archived);
    }
    private void interaction(UUID id,UUID lead,UUID customer,UUID recorder,String visibility,Instant at) {
        jdbc.update("INSERT INTO interactions(id,organization_id,lead_id,customer_id,recorded_by_user_id,type,channel,subject,content,visibility,occurred_at,created_at,updated_at) VALUES (?,?,?,?,?,'CALL','PHONE','test','private',?,?,?,?)",id,ORG_ONE,lead,customer,recorder,visibility,Timestamp.from(at),time(),time());
    }
    private void task(UUID id,UUID owner,String status,Instant due,Instant completed) {
        jdbc.update("INSERT INTO crm_tasks(id,organization_id,customer_id,owner_user_id,created_by_user_id,title,priority,status,due_at,completed_at,origin,created_at,updated_at) VALUES (?,?,?,?,?,'test','MEDIUM',?,?,?,'MANUAL',?,?)",id,ORG_ONE,CUSTOMER,owner,USER_ONE,status,Timestamp.from(due),completed==null?null:Timestamp.from(completed),time(),time());
    }
    private void workflow(UUID id,String status,int retry,String error) {
        jdbc.update("INSERT INTO workflow_executions(id,organization_id,workflow_type,idempotency_key,status,retry_count,last_error,started_at,finished_at) VALUES (?,?,'LEAD_QUALIFIED',?,?,?,?,?,?)",id,ORG_ONE,id.toString(),status,retry,error,Timestamp.from(START.minusSeconds(90)),Timestamp.from(START.minusSeconds(1)));
    }
    private JsonNode actor(String name) throws Exception { return response(login("one","dash-"+name+"@example.test",PASSWORD_ONE),200); }
    private JsonNode getKpis(JsonNode actor,String query) throws Exception {return response(mvc.perform(get(PATH+query).header("Authorization",bearer(actor))),200);}
    private JsonNode metric(JsonNode response,String prefix) {
        for(JsonNode m:response.get("metrics")) if(m.get("metricId").asText().startsWith(prefix)) return m;
        throw new AssertionError("Missing "+prefix);
    }

    @Test void scopesFollowRolesAndDataStaffCombinationAndReadsNeverAudit() throws Exception {
        JsonNode manager=loginOne(),sale=actor("sale"),care=actor("care"),admin=actor("admin"),data=actor("data"),both=actor("both");
        int before=jdbc.queryForObject("SELECT count(*) FROM audit_logs",Integer.class);
        for(JsonNode global:List.of(admin,data,both)) {
            JsonNode result=getKpis(global,WINDOW);
            assertThat(metric(result,"A01").get("value").asInt()).isEqualTo(1);
            assertThat(metric(result,"B01").get("value").asInt()).isEqualTo(2);
            assertThat(metric(result,"C01").get("value").decimalValue()).isEqualByComparingTo("0.5");
            assertThat(result.toString()).doesNotContain("private diagnostic","private");
        }
        JsonNode m=getKpis(manager,WINDOW), s=getKpis(sale,WINDOW), c=getKpis(care,WINDOW);
        assertThat(metric(m,"A01").get("value").asInt()).isEqualTo(1);
        assertThat(metric(s,"A01").get("value").asInt()).isEqualTo(1);
        assertThat(metric(m,"B01").get("value").asInt()).isEqualTo(2);
        assertThat(metric(s,"B01").get("value").asInt()).isEqualTo(1);
        assertThat(metric(c,"B01").get("value").asInt()).isEqualTo(1);
        for(String prefix:List.of("A01","A06","C01","D01","D02")) {
            JsonNode hidden=metric(c,prefix); assertThat(hidden.get("availability").asText()).isEqualTo("NOT_APPLICABLE");
            assertThat(hidden.get("value").isNull()).isTrue(); assertThat(hidden.get("numerator").isNull()).isTrue(); assertThat(hidden.get("denominator").isNull()).isTrue();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs",Integer.class)).isEqualTo(before);
    }

    @Test void ownersNarrowAllGroupsAndSuppressWorkflow() throws Exception {
        JsonNode result=getKpis(loginOne(),WINDOW+"&ownerUserId="+SALE);
        assertThat(metric(result,"A01").get("value").asInt()).isEqualTo(1);
        assertThat(metric(result,"A06").at("/dimensions/segment").asText()).isEqualTo("AT_RISK");
        assertThat(metric(result,"B01").get("value").asInt()).isEqualTo(1);
        assertThat(metric(result,"B04").at("/dimensions/ownerUserId").asText()).isEqualTo(SALE.toString());
        assertThat(metric(result,"D01").get("value").asInt()).isEqualTo(1);
        assertThat(metric(result,"D02").at("/dimensions/userId").asText()).isEqualTo(SALE.toString());
        for(String prefix:List.of("C01","C02","C03")) {
            JsonNode m=metric(result,prefix); assertThat(m.get("availability").asText()).isEqualTo("NOT_APPLICABLE");
            assertThat(m.get("reason").asText()).isEqualTo("OWNER_FILTER_NOT_SUPPORTED"); assertThat(m.get("value").isNull()).isTrue();
        }
        mvc.perform(get(PATH+WINDOW+"&ownerUserId="+USER_TWO).header("Authorization",bearer(loginOne())))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN_SCOPE"));
        mvc.perform(get(PATH+WINDOW+"&ownerUserId="+CARE).header("Authorization",bearer(actor("sale"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN_SCOPE"));
    }

    @Test void leadAndCustomerOwnerFiltersDoNotFilterInteractionRecorder() throws Exception {
        Instant created=Instant.parse("2026-09-10T00:00:00Z");
        jdbc.update("UPDATE leads SET created_at=? WHERE id=?",Timestamp.from(created),LEAD);
        interaction(id(1521),LEAD,null,CARE,"CARE_VISIBLE",created.plusSeconds(3600));
        interaction(id(1522),null,CUSTOMER,CARE,"CARE_VISIBLE",Instant.parse("2026-09-25T00:00:00Z"));
        JsonNode result=getKpis(actor("admin"),WINDOW+"&ownerUserId="+SALE);
        assertThat(metric(result,"A05").get("value").decimalValue()).isEqualByComparingTo("3600");
        assertThat(metric(result,"A06").at("/dimensions/segment").asText()).isEqualTo("ACTIVE");
        assertThat(metric(result,"D01").get("value").asInt()).isEqualTo(1);
        assertThat(metric(result,"D02").at("/dimensions/userId").asText()).isEqualTo(SALE.toString());
    }

    @Test void managerAndSalesExcludeOtherTeamsWork() throws Exception {
        UUID other=id(1530), offering=id(1531), customer=id(1532), lead=id(1533);
        user(other,ORG_ONE,"Other sale","dash-other@example.test",hashOne);
        userRole(ORG_ONE,other,"SALES_REP");
        jdbc.update("INSERT INTO team_memberships(id,organization_id,team_id,user_id,joined_at,status) VALUES (?,?,?,?,?,'ACTIVE')",id(1534),ORG_ONE,TEAM_MEMBER_ONLY,other,time());
        jdbc.update("INSERT INTO offerings(id,organization_id,category_id,team_id,code,name,kind,status,created_at,updated_at) VALUES (?,?,?,?,'OTHER','Other','SERVICE','ACTIVE',?,?)",offering,ORG_ONE,CATEGORY,TEAM_MEMBER_ONLY,time(),time());
        jdbc.update("INSERT INTO leads(id,organization_id,owner_user_id,offering_id,display_name,email,normalized_email,status,created_at,updated_at) VALUES (?,?,?,?,'Other','other-lead@example.test','other-lead@example.test','NEW',?,?)",lead,ORG_ONE,other,offering,time(),time());
        jdbc.update("INSERT INTO customers(id,organization_id,owner_user_id,display_name,email,normalized_email,status,created_at,updated_at) VALUES (?,?,?,'Other','other-customer@example.test','other-customer@example.test','ACTIVE',?,?)",customer,ORG_ONE,other,time(),time());
        jdbc.update("INSERT INTO crm_tasks(id,organization_id,customer_id,owner_user_id,created_by_user_id,title,priority,status,due_at,origin,created_at,updated_at) VALUES (?,?,?,?,?,'Other','MEDIUM','OPEN',?,'MANUAL',?,?)",id(1535),ORG_ONE,customer,other,other,time(),time(),time());
        interaction(id(1536),null,customer,other,"SALES_ONLY",START.minusSeconds(1));
        JsonNode manager=getKpis(loginOne(),WINDOW), sale=getKpis(actor("sale"),WINDOW), admin=getKpis(actor("admin"),WINDOW);
        assertThat(metric(manager,"A01").get("value").asInt()).isEqualTo(1);
        assertThat(metric(sale,"A01").get("value").asInt()).isEqualTo(1);
        assertThat(metric(admin,"A01").get("value").asInt()).isEqualTo(2);
        assertThat(metric(manager,"B01").get("value").asInt()).isEqualTo(2);
        assertThat(metric(sale,"B01").get("value").asInt()).isEqualTo(1);
        assertThat(metric(admin,"B01").get("value").asInt()).isEqualTo(3);
        assertThat(metric(manager,"D01").get("value").asInt()).isEqualTo(1);
        assertThat(metric(admin,"D01").get("value").asInt()).isEqualTo(2);
    }

    @Test void emptyPeriodUsesNullInsteadOfFabricatedZeros() throws Exception {
        JsonNode result=getKpis(actor("admin"),"?start=2025-01-01T00:00:00Z&end=2025-02-01T00:00:00Z");
        int checked=0;
        for(JsonNode m:result.path("metrics")) {
            String id=m.path("metricId").asText();
            boolean expected=id.startsWith("D01")||id.startsWith("D02")||id.startsWith("C02")||
                    (id.startsWith("A05")&&m.path("dimensions").path("kind").asText().equals("NO_RESPONSE"));
            if(expected) { checked++; assertThat(m.path("value").isNull()).as(id).isTrue(); assertThat(m.path("availability").asText()).as(id).isEqualTo("NO_DATA_IN_PERIOD"); }
        }
        assertThat(checked).isEqualTo(4);
    }

    @Test void snapshotExcludesArchivedAndFutureInteractionsAndHandlesEmptyRates() throws Exception {
        JsonNode result=getKpis(actor("admin"),WINDOW);
        assertThat(metric(result,"A06").at("/dimensions/segment").asText()).isEqualTo("AT_RISK");
        assertThat(metric(result,"A06").get("value").asInt()).isEqualTo(1);
        assertThat(metric(result,"B05").get("availability").asText()).isEqualTo("NOT_AVAILABLE_ZERO_DENOMINATOR");
        assertThat(metric(result,"B05").get("value").isNull()).isTrue();
        JsonNode empty=getKpis(actor("admin"),"?start=2025-01-01T00:00:00Z&end=2025-02-01T00:00:00Z&ownerUserId="+CARE+"&metricIds=A05_FIRST_RESPONSE_TIME,D01_INTERACTIONS_BY_CHANNEL,B02_OVERDUE_OPEN_TASKS");
        assertThat(metric(empty,"A05").get("availability").asText()).isEqualTo("NO_DATA_IN_PERIOD");
        assertThat(metric(empty,"D01").get("availability").asText()).isEqualTo("NO_DATA_IN_PERIOD");
        assertThat(metric(empty,"B02").get("availability").asText()).isEqualTo("ZERO");
    }

    @Test void anotherTenantCannotContributeToAnySnapshotOrPeriodCount() throws Exception {
        for(String code:List.of("dashboard.read","lead.read","offering.read","interaction.read","task.manage")) grant(ORG_TWO,"SALES_REP",code);
        JsonNode tokens=response(login("two","shared@example.com",PASSWORD_TWO),200);
        JsonNode result=getKpis(tokens,WINDOW);
        assertThat(metric(result,"A01").get("value").asInt()).isZero();
        assertThat(metric(result,"B01").get("value").asInt()).isZero();
        assertThat(metric(result,"D01").get("availability").asText()).isEqualTo("NO_DATA_IN_PERIOD");
        assertThat(result.get("organizationId").asText()).isEqualTo(ORG_TWO.toString());
    }

    @Test void permissionAndInputValidationAreEnforced() throws Exception {
        jdbc.update("DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE organization_id=?) AND permission_id=(SELECT id FROM permissions WHERE code='dashboard.read')",ORG_ONE);
        mvc.perform(get(PATH).header("Authorization",bearer(actor("sale")))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN_PERMISSION"));
        grantOnce("ADMIN","dashboard.read"); JsonNode admin=actor("admin");
        for(String query:List.of("?start=2026-09-01T00:00:00Z","?start=2026-10-01T00:00:00Z&end=2026-09-01T00:00:00Z","?timezone=NoSuch/Zone","?organizationId="+ORG_TWO,"?metricIds=wrong","?start=2024-01-01T00:00:00Z&end=2026-01-01T00:00:00Z"))
            mvc.perform(get(PATH+query).header("Authorization",bearer(admin))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR")).andExpect(jsonPath("$.fieldErrors[0].field").isString());
    }
}
