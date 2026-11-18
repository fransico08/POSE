package com.pose.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.conversation.ConversationInteractionSessionService;
import com.pose.backend.ai.MockProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties={"pose.sales-assignment.expiry-scan-delay-ms=86400000","pose.ai.enabled=true","pose.ai.provider=mock"})
class LeadConversionIntegrationTest extends FoundationTestSupport {
    @Autowired ConversationInteractionSessionService sessions;
    @Autowired MockProvider mockProvider;
    private static final UUID CAT=id(701), OFFER=id(702), SALE=id(703), OTHER=id(704), CARE=id(705), DATA=id(706), ADMIN=id(707), LEAD=id(708);
    private static final String EMAIL="Lead@Example.test", NORMAL_EMAIL="lead@example.test", PHONE="0912 345 678", NORMAL_PHONE="+84912345678";
    @BeforeEach void fixtures() {
        for(String p:List.of("lead.read","lead.write","customer.write","offering.read","opportunity.read","opportunity.write","interaction.read","interaction.write","interaction.sensitive.read","task.manage"))
            if(jdbc.queryForObject("SELECT count(*) FROM permissions WHERE code=?",Integer.class,p)==0) permission(p);
        role(ORG_ONE,"ADMIN");role(ORG_ONE,"DATA_STAFF");
        for(String role:List.of("MANAGER","SALES_REP","ADMIN")) for(String p:List.of("lead.read","lead.write","customer.read","customer.write")) grantOnce(role,p);
        grantOnce("SALES_REP","offering.read");grantOnce("SALES_REP","opportunity.read");grantOnce("SALES_REP","opportunity.write");
        grantOnce("DATA_STAFF","lead.read");grantOnce("DATA_STAFF","customer.read");grantOnce("CUSTOMER_CARE","customer.read");
        for(String role:List.of("SALES_REP","CUSTOMER_CARE")) { grantOnce(role,"interaction.read"); grantOnce(role,"interaction.write"); grantOnce(role,"task.manage"); }
        grantOnce("SALES_REP","interaction.sensitive.read");
        for(var e:Map.of(SALE,"sale",OTHER,"other",CARE,"care",DATA,"data",ADMIN,"admin").entrySet()) user(e.getKey(),ORG_ONE,e.getValue(),e.getValue()+"@example.test",hashOne);
        userRole(ORG_ONE,SALE,"SALES_REP");userRole(ORG_ONE,OTHER,"SALES_REP");userRole(ORG_ONE,CARE,"CUSTOMER_CARE");userRole(ORG_ONE,DATA,"DATA_STAFF");userRole(ORG_ONE,ADMIN,"ADMIN");userRole(ORG_ONE,USER_THREE,"MANAGER");
        jdbc.update("INSERT INTO team_memberships(id,organization_id,team_id,user_id,joined_at,status) VALUES (?,?,?,?,?,'ACTIVE')",id(709),ORG_ONE,TEAM_MANAGED,SALE,time());
        jdbc.update("INSERT INTO team_memberships(id,organization_id,team_id,user_id,joined_at,status) VALUES (?,?,?,?,?,'ACTIVE')",id(710),ORG_ONE,TEAM_MANAGED,OTHER,time());
        jdbc.update("INSERT INTO offering_categories(id,organization_id,code,name,status,created_at,updated_at) VALUES (?,?,'CRM','CRM','ACTIVE',?,?)",CAT,ORG_ONE,time(),time());
        jdbc.update("INSERT INTO offerings(id,organization_id,category_id,team_id,code,name,kind,status,created_at,updated_at) VALUES (?,?,?,?,'OFFER','Offer','SERVICE','ACTIVE',?,?)",OFFER,ORG_ONE,CAT,TEAM_MANAGED,time(),time());
        for(UUID user:List.of(SALE,OTHER)) jdbc.update("INSERT INTO sales_assignments(id,organization_id,offering_id,sales_user_id,assigned_by_user_id,status,effective_from,created_at,updated_at) VALUES (?,?,?,?,?,'ACTIVE',?,?,?)",UUID.randomUUID(),ORG_ONE,OFFER,user,USER_ONE,time(),time(),time());
    }
    private void grantOnce(String role,String permission) {
        if(jdbc.queryForObject("SELECT count(*) FROM role_permissions rp JOIN roles r ON r.id=rp.role_id JOIN permissions p ON p.id=rp.permission_id WHERE r.organization_id=? AND r.code=? AND p.code=?",Integer.class,ORG_ONE,role,permission)==0) grant(ORG_ONE,role,permission);
    }
    private void lead(UUID id,String status) {
        jdbc.update("INSERT INTO leads(id,organization_id,owner_user_id,offering_id,display_name,email,normalized_email,phone,normalized_phone,status,notes,qualified_at,disqualification_reason,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",id,ORG_ONE,SALE,OFFER,"Lead sample",EMAIL,NORMAL_EMAIL,PHONE,NORMAL_PHONE,status,"Lead notes",status.equals("QUALIFIED")?time():null,status.equals("DISQUALIFIED")?"Reason":null,time(),time());
    }
    private void customer(UUID id,UUID owner,String email,String phone,boolean archived) {
        jdbc.update("INSERT INTO customers(id,organization_id,owner_user_id,display_name,email,normalized_email,phone,normalized_phone,status,archived_at,created_at,updated_at) VALUES (?,?,?,'Existing',?,?,?,?,'ACTIVE',?,?,?)",id,ORG_ONE,owner,email,email,phone,phone,archived?time():null,time(),time());
    }
    private void assignment(UUID customer,UUID user) {
        jdbc.update("INSERT INTO customer_assignments(id,organization_id,customer_id,user_id,assignment_type,effective_from,status,assigned_by_user_id,source,created_at) VALUES (?,?,?,?,'SALES',?,'ACTIVE',?,'MANUAL',?)",UUID.randomUUID(),ORG_ONE,customer,user,time(),USER_ONE,time());
    }
    private void opportunity(UUID id,UUID owner,String stage) {
        jdbc.update("INSERT INTO opportunities(id,organization_id,lead_id,offering_id,owner_user_id,title,stage,expected_value,expected_close_date,closing_reason,closed_at,created_at,updated_at) VALUES (?,?,?,?,?,'Opportunity',?,100,?,?,?, ?,?)",id,ORG_ONE,LEAD,OFFER,owner,stage,java.time.LocalDate.of(2026,12,1),stage.equals("LOST")?"Reason":null,stage.equals("LOST")?time():null,time(),time());
        jdbc.update("INSERT INTO opportunity_lines(id,organization_id,opportunity_id,offering_id,position,quantity,unit_price,line_total,created_at,updated_at) VALUES (?,?,?,?,1,1,100,100,?,?)",UUID.randomUUID(),ORG_ONE,id,OFFER,time(),time());
    }
    private void interaction(UUID id,UUID opportunity,String visibility) {
        jdbc.update("INSERT INTO interactions(id,organization_id,lead_id,opportunity_id,recorded_by_user_id,type,channel,subject,content,visibility,occurred_at,created_at,updated_at) VALUES (?,?,?,?,?,'CALL','PHONE','Private subject','Private content',?,?,?,?)",
                id,ORG_ONE,LEAD,opportunity,SALE,visibility,time(),time(),time());
    }
    private void task(UUID id,UUID opportunity,String status) {
        jdbc.update("INSERT INTO crm_tasks(id,organization_id,lead_id,opportunity_id,owner_user_id,created_by_user_id,title,priority,status,due_at,origin,completed_at,created_at,updated_at) VALUES (?,?,?,?,?,?,'Private task','NORMAL',?,?,'MANUAL',?,?,?)",
                id,ORG_ONE,LEAD,opportunity,SALE,SALE,status,time(),status.equals("DONE")?time():null,time(),time());
    }
    private void conversation(UUID conversation,UUID lead,UUID message) {
        UUID account=id(3900);
        if(jdbc.queryForObject("SELECT count(*) FROM channel_accounts WHERE id=?",Integer.class,account)==0)
            jdbc.update("INSERT INTO channel_accounts(id,organization_id,channel_type,display_name,external_account_id,team_id,default_assignee_user_id,status,secret_ref,created_at,updated_at) VALUES (?,?,'ZALO_SIM','Test channel','convert-channel',?,?,'ACTIVE','POSE_TEST_SECRET',?,?)",account,ORG_ONE,TEAM_MANAGED,SALE,time(),time());
        jdbc.update("INSERT INTO conversations(id,organization_id,channel_account_id,external_thread_id,assigned_user_id,lead_id,status,consent_status,created_at,updated_at) VALUES (?,?,?,?,?,?,'OPEN','UNKNOWN',?,?)",conversation,ORG_ONE,account,conversation.toString(),SALE,lead,time(),time());
        jdbc.update("INSERT INTO conversation_messages(id,organization_id,conversation_id,direction,external_message_id,content_type,content,delivery_status,occurred_at,created_at) VALUES (?,?,?,'INBOUND',?,'TEXT','Demo content','RECEIVED',?,?)",message,ORG_ONE,conversation,message.toString(),time(),time());
        sessions.attach(ORG_ONE,conversation,message,"ZALO_SIM",time().toInstant(),time().toInstant());
    }
    private void humanSignal(UUID signal,UUID lead,UUID message,String type) {
        jdbc.update("INSERT INTO customer_signals(id,organization_id,lead_id,message_id,signal_type,\"value\",source,human_message_id,status,created_at) VALUES (?,?,?,?,?,'OTHER','HUMAN',?,'ACCEPTED',?)",signal,ORG_ONE,lead,message,type,message,time());
    }
    @Test void conversionMovesConversationsSignalsAndPreservesInteractionAfterMoreMessages() throws Exception {
        for(String p:List.of("customer_360.read","conversation.read","conversation.reply","ai.assist.use","ai.signal.review","dashboard.read")) {
            if(jdbc.queryForObject("SELECT count(*) FROM permissions WHERE code=?",Integer.class,p)==0) permission(p);
            grantOnce("SALES_REP",p);
        }
        lead(LEAD,"QUALIFIED");UUID c1=id(3901),c2=id(3902),m1=id(3903),m2=id(3904),signal=id(3905);
        conversation(c1,LEAD,m1);conversation(c2,LEAD,m2);humanSignal(signal,LEAD,m1,"INTENT");
        JsonNode sales=actor("sale");
        jsonRequest(post("/api/v1/ai/signals/extract"),sales,Map.of("subjectType","LEAD","subjectId",LEAD)).andExpect(status().isOk());
        UUID proposed=jdbc.queryForObject("SELECT id FROM customer_signals WHERE message_id=? AND source='AI'",UUID.class,m1);
        UUID customer=UUID.fromString(response(convert(LEAD,sales,body()),200).path("customer").path("id").asText());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM conversations WHERE organization_id=? AND customer_id=? AND lead_id IS NULL AND assigned_user_id=?",Integer.class,ORG_ONE,customer,SALE)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT customer_id FROM customer_signals WHERE id=?",UUID.class,signal)).isEqualTo(customer);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action='CONVERSATION_SUBJECT_CHANGED'",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action='AI_SIGNAL_SUBJECT_CHANGED'",Integer.class)).isEqualTo(3);
        JsonNode listed=response(mvc.perform(get("/api/v1/ai/signals?subjectType=CUSTOMER&subjectId="+customer).header("Authorization",bearer(sales))),200);
        assertThat(listed.toString()).contains(signal.toString(),proposed.toString(),"PROPOSED","ACCEPTED");
        jsonRequest(post("/api/v1/ai/signals/"+proposed+"/accept"),sales,Map.of()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACCEPTED"));
        var profileResult=mvc.perform(get("/api/v1/customers/"+customer+"/360").header("Authorization",bearer(sales))).andReturn().getResponse();
        assertThat(profileResult.getStatus()).as(profileResult.getContentAsString()).isEqualTo(200);
        assertThat(mapper.readTree(profileResult.getContentAsString()).path("interactions").path("count").asInt()).isEqualTo(2);
        jsonRequest(post("/api/v1/conversations/"+c1+"/messages"),sales,Map.of("content","Demo reply")).andExpect(status().isCreated());
        assertThat(response(mvc.perform(get("/api/v1/customers/"+customer+"/360").header("Authorization",bearer(sales))),200).path("interactions").path("count").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT customer_id FROM interactions WHERE conversation_id=?",UUID.class,c1)).isEqualTo(customer);
        jdbc.update("INSERT INTO opportunities(id,organization_id,customer_id,offering_id,owner_user_id,title,stage,expected_value,currency_code,created_at,updated_at) VALUES (?,?,?,?,?,'Contacted','NEW',100,'VND',?,?)",id(3906),ORG_ONE,customer,OFFER,SALE,time(),time());
        JsonNode dashboard=response(mvc.perform(get("/api/v1/dashboard/kpis?metricIds=E02_UNATTENDED_OPPORTUNITY_VALUE").header("Authorization",bearer(sales))),200);
        JsonNode e02=dashboard.path("metrics").get(0);assertThat(e02.path("value").decimalValue()).isEqualByComparingTo("0");
        for(String p:List.of("customer_360.read","conversation.read")) grantOnce("CUSTOMER_CARE",p);
        jdbc.update("INSERT INTO customer_assignments(id,organization_id,customer_id,user_id,assignment_type,effective_from,status,assigned_by_user_id,source,created_at) VALUES (?,?,?,?,'CARE',?,'ACTIVE',?,'MANUAL',?)",id(3907),ORG_ONE,customer,CARE,time(),USER_ONE,time());
        JsonNode care=actor("care");response(mvc.perform(get("/api/v1/customers/"+customer+"/360").header("Authorization",bearer(care))),200);
        mvc.perform(get("/api/v1/conversations/"+c1+"/messages").header("Authorization",bearer(care))).andExpect(status().isNotFound());
    }
    @Test void conversionReusesCustomerAndRollsBackWhenSignalAuditFails() throws Exception {
        lead(LEAD,"QUALIFIED");UUID target=id(3910),conversation=id(3911),message=id(3912),signal=id(3913);
        customer(target,SALE,NORMAL_EMAIL,null,false);conversation(conversation,LEAD,message);humanSignal(signal,LEAD,message,"INTENT");
        JsonNode sales=actor("sale");jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT test_signal_transfer_audit CHECK(action <> 'AI_SIGNAL_SUBJECT_CHANGED')");
        try {
            convert(LEAD,sales,body()).andExpect(status().isConflict());
            assertThat(jdbc.queryForObject("SELECT status FROM leads WHERE id=?",String.class,LEAD)).isEqualTo("QUALIFIED");
            assertThat(jdbc.queryForObject("SELECT lead_id FROM conversations WHERE id=?",UUID.class,conversation)).isEqualTo(LEAD);
            assertThat(jdbc.queryForObject("SELECT lead_id FROM customer_signals WHERE id=?",UUID.class,signal)).isEqualTo(LEAD);
            assertThat(jdbc.queryForObject("SELECT lead_id FROM interactions WHERE conversation_id=?",UUID.class,conversation)).isEqualTo(LEAD);
        } finally {jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT test_signal_transfer_audit");}
        assertThat(response(convert(LEAD,sales,body()),200).path("customer").path("id").asText()).isEqualTo(target.toString());
        assertThat(jdbc.queryForObject("SELECT customer_id FROM conversations WHERE id=?",UUID.class,conversation)).isEqualTo(target);
        assertThat(jdbc.queryForObject("SELECT customer_id FROM customer_signals WHERE id=?",UUID.class,signal)).isEqualTo(target);
    }
    @Test void legacyConversationResolvesConvertedLeadForNewSessions() throws Exception {
        lead(LEAD,"QUALIFIED");UUID c=id(3920),m=id(3921);conversation(c,LEAD,m);
        UUID customer=UUID.fromString(response(convert(LEAD,actor("sale"),body()),200).path("customer").path("id").asText());
        jdbc.update("UPDATE conversations SET customer_id=NULL,lead_id=? WHERE id=?",LEAD,c);
        UUID later=id(3922);jdbc.update("INSERT INTO conversation_messages(id,organization_id,conversation_id,direction,external_message_id,content_type,content,delivery_status,occurred_at,created_at) VALUES (?,?,?,'INBOUND','later','TEXT','Demo','RECEIVED',?,?)",later,ORG_ONE,c,time(),time());
        UUID reused=sessions.attach(ORG_ONE,c,later,"ZALO_SIM",time().toInstant(),time().toInstant());
        assertThat(jdbc.queryForObject("SELECT customer_id FROM interactions WHERE id=?",UUID.class,reused)).isEqualTo(customer);
        UUID future=id(3923);var at=time().toInstant().plusSeconds(18000);jdbc.update("INSERT INTO conversation_messages(id,organization_id,conversation_id,direction,external_message_id,content_type,content,delivery_status,occurred_at,created_at) VALUES (?,?,?,'INBOUND','future','TEXT','Demo','RECEIVED',?,?)",future,ORG_ONE,c,Timestamp.from(at),Timestamp.from(at));
        UUID next=sessions.attach(ORG_ONE,c,future,"ZALO_SIM",at,at);assertThat(next).isNotEqualTo(reused);
        assertThat(jdbc.queryForObject("SELECT customer_id FROM interactions WHERE id=?",UUID.class,next)).isEqualTo(customer);
    }
    @Test void unlinkedConversationCanReuseInteractionWithNullCustomer() {
        UUID c=id(3940),first=id(3941),second=id(3942);conversation(c,null,first);
        jdbc.update("INSERT INTO conversation_messages(id,organization_id,conversation_id,direction,external_message_id,content_type,content,delivery_status,occurred_at,created_at) VALUES (?,?,?,'INBOUND','unlinked-next','TEXT','Demo','RECEIVED',?,?)",second,ORG_ONE,c,time(),time());
        UUID result=sessions.attach(ORG_ONE,c,second,"ZALO_SIM",time().toInstant(),time().toInstant());
        assertThat(jdbc.queryForObject("SELECT customer_id FROM interactions WHERE id=?",UUID.class,result)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM interactions WHERE conversation_id=?",Integer.class,c)).isEqualTo(1);
    }
    @Test void assignedSalesKeepsConversationButCannotReadOtherSalesCustomerOrUseItsContext() throws Exception {
        for(String p:List.of("customer_360.read","conversation.read","conversation.reply","ai.assist.use")) {
            if(jdbc.queryForObject("SELECT count(*) FROM permissions WHERE code=?",Integer.class,p)==0) permission(p);
            grantOnce("SALES_REP",p);
        }
        lead(LEAD,"QUALIFIED");jdbc.update("UPDATE leads SET owner_user_id=? WHERE id=?",OTHER,LEAD);
        UUID target=id(3930),c=id(3931),m=id(3932),signal=id(3933);
        customer(target,OTHER,NORMAL_EMAIL,null,false);conversation(c,LEAD,m);humanSignal(signal,LEAD,m,"INTENT");
        assertThat(response(convert(LEAD,actor("other"),body()),200).path("customer").path("id").asText()).isEqualTo(target.toString());
        JsonNode sales=actor("sale");response(mvc.perform(get("/api/v1/conversations/"+c+"/messages").header("Authorization",bearer(sales))),200);
        jsonRequest(post("/api/v1/conversations/"+c+"/messages"),sales,Map.of("content","Demo reply")).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/customers/"+target+"/360").header("Authorization",bearer(sales))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/ai/signals?subjectType=CUSTOMER&subjectId="+target).header("Authorization",bearer(sales))).andExpect(status().isForbidden());
        jsonRequest(post("/api/v1/ai/copilot/conversations/"+c+"/reply-draft"),sales,Map.of()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCEEDED"));
        assertThat(mockProvider.lastInput()).doesNotContain("Existing",NORMAL_EMAIL,NORMAL_PHONE);
    }
    @Test void conversionMovesEligibleWorkAndLeavesClosedOpportunityHistory() throws Exception {
        if(jdbc.queryForObject("SELECT count(*) FROM permissions WHERE code='customer_360.read'",Integer.class)==0) permission("customer_360.read");
        grantOnce("SALES_REP","customer_360.read");
        lead(LEAD,"QUALIFIED");UUID open=id(760),closed=id(761);opportunity(open,SALE,"NEW");opportunity(closed,SALE,"LOST");
        UUID shared=id(762),sensitive=id(763),oldInteraction=id(764),taskOpen=id(765),taskProgress=id(766),taskDone=id(767),taskCancelled=id(768),oldTask=id(769);
        interaction(shared,null,"CARE_VISIBLE");interaction(sensitive,open,"SALES_ONLY");interaction(oldInteraction,closed,"CARE_VISIBLE");
        task(taskOpen,null,"OPEN");task(taskProgress,open,"IN_PROGRESS");task(taskDone,null,"DONE");task(taskCancelled,null,"CANCELLED");task(oldTask,closed,"OPEN");
        JsonNode sale=actor("sale");JsonNode converted=response(convert(LEAD,sale,body()),200);UUID customer=UUID.fromString(converted.get("customer").get("id").asText());
        for(UUID id:List.of(shared,sensitive)) assertThat(jdbc.queryForObject("SELECT customer_id FROM interactions WHERE id=?",UUID.class,id)).isEqualTo(customer);
        assertThat(jdbc.queryForObject("SELECT lead_id FROM interactions WHERE id=?",UUID.class,oldInteraction)).isEqualTo(LEAD);
        for(UUID id:List.of(taskOpen,taskProgress)) assertThat(jdbc.queryForObject("SELECT customer_id FROM crm_tasks WHERE id=?",UUID.class,id)).isEqualTo(customer);
        for(UUID id:List.of(taskDone,taskCancelled,oldTask)) assertThat(jdbc.queryForObject("SELECT lead_id FROM crm_tasks WHERE id=?",UUID.class,id)).isEqualTo(LEAD);
        String snapshot=jdbc.queryForObject("SELECT CAST(after_data AS VARCHAR) FROM audit_logs WHERE action='LEAD_CONVERTED'",String.class);
        assertThat(snapshot).contains("movedInteractions","movedTasks").doesNotContain("Private",EMAIL,PHONE);
        assertThat(snapshot).containsPattern("movedInteractions[^0-9]*2").containsPattern("movedTasks[^0-9]*2");
        JsonNode items=response(mvc.perform(get("/api/v1/interactions?customerId="+customer).header("Authorization",bearer(sale))),200);
        assertThat(items.get("totalItems").asInt()).isEqualTo(2);
        JsonNode profile=response(mvc.perform(get("/api/v1/customers/"+customer+"/360").header("Authorization",bearer(sale))),200);
        assertThat(profile.path("leads").path("items").toString()).contains(LEAD.toString());
        assertThat(profile.path("interactions").path("items").toString()).contains(shared.toString(),sensitive.toString()).doesNotContain(oldInteraction.toString());
        assertThat(profile.path("tasks").path("items").toString()).contains(taskOpen.toString(),taskProgress.toString()).doesNotContain(taskDone.toString(),oldTask.toString());
        jdbc.update("INSERT INTO customer_assignments(id,organization_id,customer_id,user_id,assignment_type,effective_from,status,assigned_by_user_id,source,created_at) VALUES (?,?,?,?,'CARE',?,'ACTIVE',?,'MANUAL',?)",UUID.randomUUID(),ORG_ONE,customer,CARE,time(),USER_ONE,time());
        JsonNode care=actor("care"); JsonNode careItems=response(mvc.perform(get("/api/v1/interactions?customerId="+customer).header("Authorization",bearer(care))),200);
        assertThat(careItems.get("totalItems").asInt()).isEqualTo(1);assertThat(careItems.get("items").get(0).get("id").asText()).isEqualTo(shared.toString());
    }
    private JsonNode actor(String name) throws Exception { return response(login("one",name+"@example.test",PASSWORD_ONE),200); }
    private Map<String,Object> body() { return Map.of("expectedUpdatedAt",time().toInstant().toString(),"customerType","INDIVIDUAL"); }
    private ResultActions convert(UUID lead,JsonNode actor,Object body) throws Exception {return jsonRequest(post("/api/v1/leads/"+lead+"/convert"),actor,body);}
    private void error(ResultActions r,int status,String code) throws Exception {r.andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code));}
    private int count(String table) {return jdbc.queryForObject("SELECT count(*) FROM "+table,Integer.class);}
    @Test void salesCreatesCustomerAndWritesRedactedAudit() throws Exception {
        lead(LEAD,"QUALIFIED");JsonNode result=response(convert(LEAD,actor("sale"),body()),200);
        assertThat(result.get("customerCreated").asBoolean()).isTrue();JsonNode c=result.get("customer"),l=result.get("lead");
        assertThat(c.get("ownerUserId").asText()).isEqualTo(SALE.toString());assertThat(c.get("relatedOfferingId").asText()).isEqualTo(OFFER.toString());
        assertThat(c.get("customerType").asText()).isEqualTo("INDIVIDUAL");assertThat(c.get("notes").asText()).isEqualTo("Lead notes");
        assertThat(l.get("status").asText()).isEqualTo("CONVERTED");assertThat(l.get("convertedCustomerId").asText()).isEqualTo(c.get("id").asText());
        UUID customer=UUID.fromString(c.get("id").asText());assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_assignments WHERE customer_id=? AND user_id=? AND assignment_type='SALES' AND source='INITIAL'",Integer.class,customer,SALE)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action IN ('CUSTOMER_CREATED','LEAD_CONVERTED')",Integer.class)).isEqualTo(2);
        String before=jdbc.queryForList("SELECT CAST(before_data AS VARCHAR) FROM audit_logs WHERE action IN ('CUSTOMER_CREATED','LEAD_CONVERTED')",String.class).toString();
        String after=jdbc.queryForList("SELECT CAST(after_data AS VARCHAR) FROM audit_logs WHERE action IN ('CUSTOMER_CREATED','LEAD_CONVERTED')",String.class).toString();
        assertThat(before+after).doesNotContain(EMAIL,NORMAL_EMAIL,PHONE,NORMAL_PHONE,"Lead notes");
    }
    @Test void openOpportunityMovesAndRemainsUsableWhileLostStaysOnLead() throws Exception {
        lead(LEAD,"QUALIFIED");UUID open=id(711),lost=id(712);opportunity(open,SALE,"NEW");opportunity(lost,SALE,"LOST");JsonNode sales=actor("sale");
        JsonNode result=response(convert(LEAD,sales,body()),200);UUID customer=UUID.fromString(result.get("customer").get("id").asText());
        assertThat(jdbc.queryForObject("SELECT customer_id FROM opportunities WHERE id=?",UUID.class,open)).isEqualTo(customer);
        assertThat(jdbc.queryForObject("SELECT lead_id FROM opportunities WHERE id=?",UUID.class,open)).isNull();
        assertThat(jdbc.queryForObject("SELECT lead_id FROM opportunities WHERE id=?",UUID.class,lost)).isEqualTo(LEAD);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action='OPPORTUNITY_SUBJECT_CHANGED' AND entity_id=?",Integer.class,open)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT CAST(before_data AS VARCHAR) FROM audit_logs WHERE action='OPPORTUNITY_SUBJECT_CHANGED' AND entity_id=?",String.class,open)).contains(LEAD.toString());
        assertThat(jdbc.queryForObject("SELECT CAST(after_data AS VARCHAR) FROM audit_logs WHERE action='OPPORTUNITY_SUBJECT_CHANGED' AND entity_id=?",String.class,open)).contains(customer.toString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action='OPPORTUNITY_SUBJECT_CHANGED' AND entity_id=?",Integer.class,lost)).isZero();
        JsonNode o=response(mvc.perform(get("/api/v1/opportunities/"+open).header("Authorization",bearer(sales))),200);
        jsonRequest(patch("/api/v1/opportunities/"+open),sales,Map.of("title","Updated","expectedUpdatedAt",o.get("updatedAt").asText())).andExpect(status().isOk());
    }
    @Test void reuseByEmailPreservesCustomerAndRequestOverridesAreIgnored() throws Exception {
        lead(LEAD,"QUALIFIED");UUID customer=id(713);customer(customer,SALE,NORMAL_EMAIL,null,false);
        JsonNode result=response(convert(LEAD,actor("sale"),Map.of("expectedUpdatedAt",time().toInstant().toString(),"customerType","BUSINESS","address","New address","notes","New notes")),200);
        assertThat(result.get("customerCreated").asBoolean()).isFalse();assertThat(result.get("customer").get("id").asText()).isEqualTo(customer.toString());
        assertThat(result.get("customer").get("displayName").asText()).isEqualTo("Existing");assertThat(result.get("customer").get("address").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action='CUSTOMER_CREATED'",Integer.class)).isZero();
    }
    @Test void reuseByPhoneWhenOwnerIsSalesAssignee() throws Exception {
        lead(LEAD,"QUALIFIED");UUID customer=id(714);customer(customer,OTHER,"other@example.test",NORMAL_PHONE,false);assignment(customer,SALE);
        JsonNode result=response(convert(LEAD,actor("sale"),body()),200);assertThat(result.get("customerCreated").asBoolean()).isFalse();assertThat(result.get("customer").get("id").asText()).isEqualTo(customer.toString());
    }
    @Test void conflictingMultipleAndArchivedMatchesRollback() throws Exception {
        lead(LEAD,"QUALIFIED");UUID first=id(715),second=id(716);customer(first,SALE,NORMAL_EMAIL,null,false);customer(second,SALE,null,NORMAL_PHONE,false);
        JsonNode sale=actor("sale");error(convert(LEAD,sale,body()),409,"LEAD_CONVERSION_CONFLICT");
        assertThat(count("customers")).isEqualTo(2);assertThat(jdbc.queryForObject("SELECT status FROM leads WHERE id=?",String.class,LEAD)).isEqualTo("QUALIFIED");
        jdbc.update("DELETE FROM customers WHERE id=?",second);jdbc.update("UPDATE customers SET archived_at=? WHERE id=?",time(),first);
        convert(LEAD,sale,body()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LEAD_CONVERSION_CONFLICT")).andExpect(jsonPath("$.details.duplicateId").doesNotExist());
    }
    @Test void conflictingCustomerIdOnlyVisibleWhenActorCanReadIt() throws Exception {
        lead(LEAD,"QUALIFIED");UUID customer=id(717);customer(customer,OTHER,NORMAL_EMAIL,null,false);
        JsonNode sale=actor("sale"),manager=loginOne();
        convert(LEAD,sale,body()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LEAD_CONVERSION_CONFLICT")).andExpect(jsonPath("$.details.duplicateId").doesNotExist());
        convert(LEAD,manager,body()).andExpect(status().isConflict()).andExpect(jsonPath("$.details.duplicateId").value(customer.toString()));
        assertThat(jdbc.queryForObject("SELECT status FROM leads WHERE id=?",String.class,LEAD)).isEqualTo("QUALIFIED");
    }
    @Test void conflictingOpenOpportunityOwnerRollsBackNewCustomer() throws Exception {
        lead(LEAD,"QUALIFIED");UUID open=id(718);opportunity(open,OTHER,"NEW");
        error(convert(LEAD,actor("sale"),body()),409,"LEAD_CONVERSION_CONFLICT");
        assertThat(count("customers")).isZero();assertThat(jdbc.queryForObject("SELECT lead_id FROM opportunities WHERE id=?",UUID.class,open)).isEqualTo(LEAD);
        assertThat(jdbc.queryForObject("SELECT status FROM leads WHERE id=?",String.class,LEAD)).isEqualTo("QUALIFIED");
        // Login is audited; conversion must not add its own audit entries.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action IN ('CUSTOMER_CREATED','LEAD_CONVERTED','OPPORTUNITY_SUBJECT_CHANGED')",Integer.class)).isZero();
    }
    @Test void leadStatesAndStaleVersionAreRejected() throws Exception {
        JsonNode sale=actor("sale");lead(LEAD,"NEW");error(convert(LEAD,sale,body()),409,"LEAD_STATE_CONFLICT");
        jdbc.update("UPDATE leads SET status='DISQUALIFIED',disqualification_reason='Closed' WHERE id=?",LEAD);error(convert(LEAD,sale,body()),409,"LEAD_CLOSED");
        jdbc.update("UPDATE leads SET status='QUALIFIED',disqualification_reason=NULL WHERE id=?",LEAD);
        error(convert(LEAD,sale,Map.of("expectedUpdatedAt",START.minusSeconds(30).toString(),"customerType","INDIVIDUAL")),409,"LEAD_CHANGED");
        response(convert(LEAD,sale,body()),200);error(convert(LEAD,sale,body()),409,"LEAD_CLOSED");
    }
    @Test void validatesRequiredTypeUnknownFieldAndAddressLength() throws Exception {
        lead(LEAD,"QUALIFIED");JsonNode sale=actor("sale");
        for(Object b:List.of(Map.of("expectedUpdatedAt",time().toInstant().toString()),Map.of("expectedUpdatedAt",time().toInstant().toString(),"customerType","INVALID"),Map.of("expectedUpdatedAt",time().toInstant().toString(),"customerType","INDIVIDUAL","extra",true),Map.of("expectedUpdatedAt",time().toInstant().toString(),"customerType","INDIVIDUAL","address","x".repeat(501)))) convert(LEAD,sale,b).andExpect(status().isBadRequest());
    }
    @Test void permissionsAndRoleScopeAreEnforced() throws Exception {
        lead(LEAD,"QUALIFIED");JsonNode sales=actor("sale"),other=actor("other"),manager=loginOne();
        for(String name:List.of("admin","care","data")) error(convert(LEAD,actor(name),body()),403,"FORBIDDEN_PERMISSION");
        error(convert(LEAD,other,body()),403,"FORBIDDEN_SCOPE");
        JsonNode otherManager=response(login("one","other@example.com",PASSWORD_ONE),200);error(convert(LEAD,otherManager,body()),403,"FORBIDDEN_SCOPE");
        jdbc.update("DELETE FROM role_permissions WHERE role_id=(SELECT id FROM roles WHERE organization_id=? AND code='SALES_REP') AND permission_id=(SELECT id FROM permissions WHERE code='customer.write')",ORG_ONE);
        error(convert(LEAD,sales,body()),403,"FORBIDDEN_PERMISSION");
        response(convert(LEAD,manager,body()),200);
    }
    @Test void permissionsCannotBeCombinedAcrossRoles() throws Exception {
        lead(LEAD,"QUALIFIED");userRole(ORG_ONE,USER_ONE,"SALES_REP");
        jdbc.update("DELETE FROM role_permissions WHERE role_id=(SELECT id FROM roles WHERE organization_id=? AND code='MANAGER') AND permission_id=(SELECT id FROM permissions WHERE code='customer.write')",ORG_ONE);
        jdbc.update("DELETE FROM role_permissions WHERE role_id=(SELECT id FROM roles WHERE organization_id=? AND code='SALES_REP') AND permission_id=(SELECT id FROM permissions WHERE code='lead.write')",ORG_ONE);
        error(convert(LEAD,loginOne(),body()),403,"FORBIDDEN_PERMISSION");
    }
    @Test void foreignTenantAndIneligibleLeadOwnerAreRejected() throws Exception {
        lead(LEAD,"QUALIFIED");JsonNode manager=loginOne();
        error(convert(UUID.randomUUID(),manager,body()),403,"FORBIDDEN_SCOPE");
        UUID foreignCategory=id(720),foreignOffering=id(721),foreignLead=id(722);
        jdbc.update("INSERT INTO offering_categories(id,organization_id,code,name,status,created_at,updated_at) VALUES (?,?,'FC','Foreign','ACTIVE',?,?)",foreignCategory,ORG_TWO,time(),time());
        jdbc.update("INSERT INTO offerings(id,organization_id,category_id,code,name,kind,status,created_at,updated_at) VALUES (?,?,?,'F','Foreign','SERVICE','ACTIVE',?,?)",foreignOffering,ORG_TWO,foreignCategory,time(),time());
        jdbc.update("INSERT INTO leads(id,organization_id,owner_user_id,offering_id,display_name,email,normalized_email,status,created_at,updated_at) VALUES (?,?,?,?,'Foreign','foreign@example.test','foreign@example.test','QUALIFIED',?,?)",foreignLead,ORG_TWO,USER_TWO,foreignOffering,time(),time());
        error(convert(foreignLead,manager,body()),403,"FORBIDDEN_SCOPE");
        jdbc.update("UPDATE sales_assignments SET status='REVOKED',revoked_at=? WHERE offering_id=? AND sales_user_id=?",time(),OFFER,SALE);
        convert(LEAD,manager,body()).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.fieldErrors[0].field").value("ownerUserId")).andExpect(jsonPath("$.fieldErrors[0].message").value("Người phụ trách Lead không còn đủ điều kiện."));
    }
    @Test void auditFailureRollsBackCustomerLeadAndOpportunity() throws Exception {
        lead(LEAD,"QUALIFIED");UUID open=id(719);opportunity(open,SALE,"NEW");UUID interaction=id(770),task=id(771);interaction(interaction,open,"CARE_VISIBLE");task(task,null,"OPEN");JsonNode sale=actor("sale");
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT reject_conversion_audit CHECK(action <> 'LEAD_CONVERTED')");
        try { error(convert(LEAD,sale,body()),409,"CONFLICT");assertThat(count("customers")).isZero();assertThat(count("customer_assignments")).isZero();
            assertThat(jdbc.queryForObject("SELECT status FROM leads WHERE id=?",String.class,LEAD)).isEqualTo("QUALIFIED");assertThat(jdbc.queryForObject("SELECT lead_id FROM opportunities WHERE id=?",UUID.class,open)).isEqualTo(LEAD);
            assertThat(jdbc.queryForObject("SELECT lead_id FROM interactions WHERE id=?",UUID.class,interaction)).isEqualTo(LEAD);
            assertThat(jdbc.queryForObject("SELECT lead_id FROM crm_tasks WHERE id=?",UUID.class,task)).isEqualTo(LEAD);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action IN ('CUSTOMER_CREATED','OPPORTUNITY_SUBJECT_CHANGED')",Integer.class)).isZero();
        } finally {jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT reject_conversion_audit");}
    }
    @Test void concurrentConversionsCreateOnlyOneCustomer() throws Exception {
        lead(LEAD,"QUALIFIED");JsonNode sale=actor("sale");var pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {Callable<Integer> call=()->{start.await();return convert(LEAD,sale,body()).andReturn().getResponse().getStatus();};var a=pool.submit(call);var b=pool.submit(call);start.countDown();assertThat(List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);assertThat(count("customers")).isEqualTo(1);}
        finally {pool.shutdownNow();}
    }
    @Test void convertedLeadLocksAndAllowsNewLeadButNotDuplicateCustomer() throws Exception {
        lead(LEAD,"QUALIFIED");JsonNode sale=actor("sale");JsonNode result=response(convert(LEAD,sale,body()),200);String version=result.get("lead").get("updatedAt").asText();
        error(jsonRequest(patch("/api/v1/leads/"+LEAD),sale,Map.of("expectedUpdatedAt",version,"displayName","Changed")),409,"LEAD_CLOSED");
        error(jsonRequest(post("/api/v1/leads/"+LEAD+"/qualify"),sale,Map.of("expectedUpdatedAt",version)),409,"LEAD_CLOSED");
        error(jsonRequest(post("/api/v1/leads/"+LEAD+"/disqualify"),sale,Map.of("expectedUpdatedAt",version,"reason","Reason")),409,"LEAD_CLOSED");
        jsonRequest(post("/api/v1/leads"),sale,Map.of("displayName","New lead","email",EMAIL,"source","WEBSITE","offeringId",OFFER.toString())).andExpect(status().isCreated());
        error(jsonRequest(post("/api/v1/customers"),sale,Map.of("displayName","New customer","email",EMAIL,"customerType","INDIVIDUAL","relatedOfferingId",OFFER.toString())),409,"CUSTOMER_DUPLICATE");
    }
}
