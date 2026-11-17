package com.pose.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.TestPropertySource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties="pose.sales-assignment.expiry-scan-delay-ms=86400000")
class E04FirstResponseIntegrationTest extends FoundationTestSupport {
    private static final UUID SALE=id(4101), CARE=id(4102), OTHER=id(4103), ADMIN=id(4104), DATA=id(4105);
    private static final UUID OWN_ACCOUNT=id(4110), OTHER_ACCOUNT=id(4111), TELEGRAM=id(4112), FOREIGN_ACCOUNT=id(4113);
    private static final String WINDOW="?start=2026-09-30T00:00:00Z&end=2026-10-02T00:00:00Z&asOf=2026-10-01T00:00:00Z&metricIds=E04_FIRST_MESSAGE_RESPONSE_TIME";
    @BeforeEach void fixtures() {
        for(String code:List.of("dashboard.read","task.manage","conversation.read")) if(jdbc.queryForObject("SELECT count(*) FROM permissions WHERE code=?",Integer.class,code)==0)permission(code);
        role(ORG_ONE,"ADMIN"); role(ORG_ONE,"DATA_STAFF");
        for(String code:List.of("ADMIN","DATA_STAFF","MANAGER","SALES_REP","CUSTOMER_CARE"))grant(ORG_ONE,code,"dashboard.read");
        grant(ORG_ONE,"CUSTOMER_CARE","task.manage");
        for(String code:List.of("MANAGER","SALES_REP","CUSTOMER_CARE"))grant(ORG_ONE,code,"conversation.read");
        for(var entry:List.of(new Object[]{SALE,"Sales","e04-sale@example.test","SALES_REP"},new Object[]{CARE,"Care","e04-care@example.test","CUSTOMER_CARE"},new Object[]{OTHER,"Other","e04-other@example.test","SALES_REP"},new Object[]{ADMIN,"Admin","e04-admin@example.test","ADMIN"},new Object[]{DATA,"Data","e04-data@example.test","DATA_STAFF"})) {
            UUID id=(UUID)entry[0]; user(id,ORG_ONE,(String)entry[1],(String)entry[2],hashOne);userRole(ORG_ONE,id,(String)entry[3]);
        }
        for(UUID id:List.of(SALE,CARE,OTHER))jdbc.update("INSERT INTO team_memberships(id,organization_id,team_id,user_id,joined_at,status) VALUES (?,?,?,?,?,'ACTIVE')",UUID.randomUUID(),ORG_ONE,TEAM_MANAGED,id,time());
        account(OWN_ACCOUNT,ORG_ONE,TEAM_MANAGED,"ZALO_SIM"); account(OTHER_ACCOUNT,ORG_ONE,TEAM_MEMBER_ONLY,"ZALO_SIM");
        account(TELEGRAM,ORG_ONE,TEAM_MANAGED,"TELEGRAM");account(FOREIGN_ACCOUNT,ORG_TWO,null,"ZALO_SIM");
    }
    private void account(UUID id,UUID org,UUID team,String channel) {
        jdbc.update("INSERT INTO channel_accounts(id,organization_id,channel_type,display_name,external_account_id,team_id,status,secret_ref,created_at,updated_at) VALUES (?,?,?,?,?,?,'ACTIVE','TEST_SECRET',?,?)",id,org,channel,"Test",id.toString(),team,time(),time());
    }
    private void conversation(UUID id,UUID org,UUID account,UUID assigned) {
        jdbc.update("INSERT INTO conversations(id,organization_id,channel_account_id,external_thread_id,assigned_user_id,status,consent_status,created_at,updated_at) VALUES (?,?,?,?,?,'OPEN','UNKNOWN',?,?)",id,org,account,id.toString(),assigned,time(),time());
    }
    private void message(UUID id,UUID org,UUID conversation,String direction,Instant at,String status,UUID sender) {
        jdbc.update("INSERT INTO conversation_messages(id,organization_id,conversation_id,direction,external_message_id,content_type,content,sent_by_user_id,delivery_status,occurred_at,created_at) VALUES (?,?,?,?,?,'TEXT','test',?,?,?,?)",id,org,conversation,direction,id.toString(),sender,status,Timestamp.from(at),time());
    }
    private void pair(int n,UUID account,UUID assigned,long age,long responseSeconds) {
        UUID c=id(n);conversation(c,ORG_ONE,account,assigned);Instant inbound=START.minusSeconds(age);
        message(id(n+100),ORG_ONE,c,"INBOUND",inbound,"RECEIVED",null);
        if(responseSeconds>=0)message(id(n+200),ORG_ONE,c,"OUTBOUND",inbound.plusSeconds(responseSeconds),"SENT",assigned);
    }
    private JsonNode actor(String role) throws Exception {return response(login("one","e04-"+role+"@example.test",PASSWORD_ONE),200);}
    private JsonNode metrics(JsonNode actor,String extra) throws Exception {return response(mvc.perform(get("/api/v1/dashboard/kpis"+WINDOW+extra).header("Authorization",bearer(actor))),200).path("metrics");}
    private JsonNode channel(JsonNode items,String channel) {for(JsonNode item:items)if(channel.equals(item.path("dimensions").path("channelType").asText()))return item;throw new AssertionError("Missing channel "+channel);}
    @Test void medianIsExactForOddAndEvenCountsAndSeparatesChannels() throws Exception {
        pair(4200,OWN_ACCOUNT,SALE,500,60);pair(4201,OWN_ACCOUNT,CARE,450,120);pair(4202,OWN_ACCOUNT,OTHER,400,180);
        pair(4203,TELEGRAM,SALE,350,45);pair(4204,TELEGRAM,SALE,300,135);
        JsonNode odd=channel(metrics(actor("admin"),""),"ZALO_SIM");
        assertThat(odd.path("value").decimalValue()).isEqualByComparingTo("120");assertThat(odd.path("numerator").asInt()).isEqualTo(3);
        pair(4205,OWN_ACCOUNT,SALE,290,240);pair(4206,OWN_ACCOUNT,SALE,280,-1);
        JsonNode result=metrics(actor("admin"),"");JsonNode even=channel(result,"ZALO_SIM");
        assertThat(even.path("value").decimalValue()).isEqualByComparingTo("150");assertThat(even.path("numerator").asInt()).isEqualTo(4);assertThat(even.path("denominator").asInt()).isEqualTo(5);
        assertThat(even.path("sourceSql").asText()).isEqualTo("DEC-26:E04");assertThat(channel(result,"TELEGRAM").path("value").decimalValue()).isEqualByComparingTo("90");
    }
    @Test void invalidRepliesAndConversationsOutsideThePeriodAreExcluded() throws Exception {
        UUID c=id(4300);conversation(c,ORG_ONE,OWN_ACCOUNT,SALE);Instant first=START.minusSeconds(100);
        message(id(4301),ORG_ONE,c,"INBOUND",first,"RECEIVED",null);
        message(id(4302),ORG_ONE,c,"OUTBOUND",first.minusSeconds(1),"SENT",SALE);
        message(id(4303),ORG_ONE,c,"OUTBOUND",first.plusSeconds(5),"FAILED",SALE);
        message(id(4304),ORG_ONE,c,"OUTBOUND",START.plusSeconds(1),"SENT",SALE);
        message(id(4305),ORG_ONE,c,"OUTBOUND",first.plusSeconds(10),"SENT",null);
        pair(4310,OWN_ACCOUNT,SALE,90000,-1);
        JsonNode m=channel(metrics(actor("admin"),""),"ZALO_SIM");
        assertThat(m.path("value").isNull()).isTrue();assertThat(m.path("availability").asText()).isEqualTo("NOT_AVAILABLE_ZERO_DENOMINATOR");
        assertThat(m.path("numerator").asInt()).isZero();assertThat(m.path("denominator").asInt()).isEqualTo(1);
    }
    @Test void rolesOwnerFilterAndTenantIsolationUseCurrentAssignee() throws Exception {
        pair(4400,OWN_ACCOUNT,SALE,500,60);pair(4401,OWN_ACCOUNT,CARE,450,120);
        pair(4402,OTHER_ACCOUNT,USER_ONE,400,180);pair(4403,OTHER_ACCOUNT,OTHER,350,240);
        UUID foreign=id(4404);conversation(foreign,ORG_TWO,FOREIGN_ACCOUNT,USER_TWO);message(id(4504),ORG_TWO,foreign,"INBOUND",START.minusSeconds(200),"RECEIVED",null);
        assertThat(channel(metrics(actor("admin"),""),"ZALO_SIM").path("denominator").asInt()).isEqualTo(4);
        assertThat(channel(metrics(actor("data"),""),"ZALO_SIM").path("denominator").asInt()).isEqualTo(4);
        assertThat(channel(metrics(loginOne(),""),"ZALO_SIM").path("denominator").asInt()).isEqualTo(3);
        assertThat(channel(metrics(actor("sale"),""),"ZALO_SIM").path("denominator").asInt()).isEqualTo(1);
        assertThat(channel(metrics(actor("care"),""),"ZALO_SIM").path("denominator").asInt()).isEqualTo(1);
        assertThat(channel(metrics(loginOne(),"&ownerUserId="+SALE),"ZALO_SIM").path("denominator").asInt()).isEqualTo(1);
        mvc.perform(get("/api/v1/dashboard/kpis"+WINDOW+"&ownerUserId="+USER_TWO).header("Authorization",bearer(actor("sale")))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN_SCOPE"));
    }
    @Test void managerAndSalesUnionDoesNotCountAnAssignedManagedConversationTwice() throws Exception {
        jdbc.update("DELETE FROM user_role_grants WHERE organization_id=? AND user_id=? AND role_id=(SELECT id FROM roles WHERE organization_id=? AND code='CUSTOMER_CARE')",ORG_ONE,USER_ONE,ORG_ONE);
        userRole(ORG_ONE,USER_ONE,"SALES_REP");
        pair(4800,OWN_ACCOUNT,USER_ONE,500,60);pair(4801,OWN_ACCOUNT,SALE,450,120);
        pair(4802,OTHER_ACCOUNT,USER_ONE,400,180);pair(4803,OTHER_ACCOUNT,OTHER,350,240);
        JsonNode result=channel(metrics(loginOne(),""),"ZALO_SIM");
        assertThat(result.path("denominator").asInt()).isEqualTo(3);
        assertThat(result.path("numerator").asInt()).isEqualTo(3);
        assertThat(result.path("value").decimalValue()).isEqualByComparingTo("120");
    }
    @Test void salesWithoutConversationReadCannotSeeE04() throws Exception {
        pair(5100,OWN_ACCOUNT,SALE,500,60);
        jdbc.update("DELETE FROM role_permissions WHERE role_id=(SELECT id FROM roles WHERE organization_id=? AND code='SALES_REP') AND permission_id=(SELECT id FROM permissions WHERE code='conversation.read')",ORG_ONE);
        JsonNode result=metrics(actor("sale"),"").get(0);
        assertThat(result.path("availability").asText()).isEqualTo("NOT_APPLICABLE");
        assertThat(result.path("reason").asText()).isEqualTo("OUTSIDE_ROLE_SCOPE");
        assertThat(result.path("unit").asText()).isEqualTo("seconds");
        assertThat(result.path("value").isNull()).isTrue();assertThat(result.path("numerator").isNull()).isTrue();assertThat(result.path("denominator").isNull()).isTrue();
    }
    @Test void dataStaffWithSalesRoleStillSeesOrganizationTotals() throws Exception {
        userRole(ORG_ONE,DATA,"SALES_REP");pair(5200,OWN_ACCOUNT,SALE,500,60);pair(5201,OTHER_ACCOUNT,OTHER,400,120);
        assertThat(channel(metrics(actor("data"),""),"ZALO_SIM").path("denominator").asInt()).isEqualTo(2);
    }
    @Test void permissionFromSalesDoesNotGrantManagerTeamScope() throws Exception {
        jdbc.update("DELETE FROM user_role_grants WHERE organization_id=? AND user_id=? AND role_id=(SELECT id FROM roles WHERE organization_id=? AND code='CUSTOMER_CARE')",ORG_ONE,USER_ONE,ORG_ONE);
        userRole(ORG_ONE,USER_ONE,"SALES_REP");
        jdbc.update("DELETE FROM role_permissions WHERE role_id=(SELECT id FROM roles WHERE organization_id=? AND code='MANAGER') AND permission_id=(SELECT id FROM permissions WHERE code='conversation.read')",ORG_ONE);
        pair(5300,OWN_ACCOUNT,SALE,500,60);pair(5301,OTHER_ACCOUNT,USER_ONE,400,120);
        assertThat(channel(metrics(loginOne(),""),"ZALO_SIM").path("denominator").asInt()).isEqualTo(1);
    }
    @Test void noConversationsReturnsNoDataAndFirstResponseTaskAppearsToday() throws Exception {
        JsonNode empty=metrics(actor("care"),"").get(0);assertThat(empty.path("availability").asText()).isEqualTo("NO_DATA_IN_PERIOD");assertThat(empty.path("value").isNull()).isTrue();
        UUID c=id(4600);conversation(c,ORG_ONE,OWN_ACCOUNT,CARE);
        jdbc.update("INSERT INTO crm_tasks(id,organization_id,conversation_id,owner_user_id,created_by_user_id,created_by_actor_type,title,priority,status,due_at,origin,created_at,updated_at) VALUES (?,?,?,?,NULL,'SYSTEM','Phản hồi đầu tiên','MEDIUM','OPEN',?,'WORKFLOW',?,?)",id(4601),ORG_ONE,c,CARE,Timestamp.from(START.minusSeconds(3600)),time(),time());
        JsonNode today=response(mvc.perform(get("/api/v1/dashboard/today?asOf=2026-10-01T00:00:00Z").header("Authorization",bearer(actor("care")))),200);
        assertThat(today.path("items").toString()).contains("Phản hồi đầu tiên","OVERDUE_TASK",id(4601).toString());
    }
    @Test @EnabledIfEnvironmentVariable(named="POSE_TEST_PRODUCTION_SCHEMA",matches="true")
    void postgresIndependentSqlMatchesApi() throws Exception {
        pair(4700,OWN_ACCOUNT,SALE,500,60);pair(4701,OWN_ACCOUNT,SALE,400,120);pair(4702,OWN_ACCOUNT,SALE,300,-1);
        pair(4703,TELEGRAM,CARE,200,90);
        String sql=java.nio.file.Files.readString(java.nio.file.Path.of("..","docs","testing","e04-reconcile-2026-11-17.sql"))
                .replace(":'organization_id'","?").replace(":'start_at'","?").replace(":'end_at'","?").replace(":'as_of'","?");
        var rows=jdbc.queryForList(sql,ORG_ONE,Timestamp.from(START.minusSeconds(86400)),Timestamp.from(START.plusSeconds(86400)),Timestamp.from(START));
        JsonNode api=metrics(actor("admin"),"");assertThat(rows).hasSize(2);
        for(var row:rows) {
            JsonNode item=channel(api,row.get("channel_type").toString());
            assertThat(item.path("numerator").asLong()).isEqualTo(((Number)row.get("numerator")).longValue());
            assertThat(item.path("denominator").asLong()).isEqualTo(((Number)row.get("denominator")).longValue());
            assertThat(item.path("value").decimalValue()).isEqualByComparingTo(row.get("value").toString());
            System.out.println("E04 independent SQL matched API channel="+row.get("channel_type")+" responded="+row.get("numerator")+" total="+row.get("denominator")+" median="+row.get("value"));
        }
    }
}
