package com.pose.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.ai.AiProvider;
import com.pose.backend.ai.MockProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.mockito.Mockito.*;
import org.springframework.test.context.TestPropertySource;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties={"pose.ai.enabled=true","pose.ai.provider=mock","pose.sales-assignment.expiry-scan-delay-ms=86400000"})
class CustomerSignalIntegrationTest extends FoundationTestSupport {
    @MockitoSpyBean MockProvider mock;
    private static final java.util.UUID CUSTOMER=id(2501), ACCOUNT=id(2502), CONVERSATION=id(2503), MESSAGE=id(2504);
    @BeforeEach void signalFixtures() {
        mock.setNextReply(null);
        for(String p:List.of("ai.assist.use","ai.signal.review","customer_360.read","conversation.read"))permission(p);
        for(String p:List.of("ai.assist.use","ai.signal.review","customer_360.read","conversation.read"))grant(ORG_ONE,"MANAGER",p);
        jdbc.update("INSERT INTO customers(id,organization_id,owner_user_id,display_name,email,normalized_email,status,created_at,updated_at) VALUES (?,?,?,'Customer','customer@example.test','customer@example.test','ACTIVE',?,?)",CUSTOMER,ORG_ONE,USER_ONE,time(),time());
        jdbc.update("INSERT INTO channel_accounts(id,organization_id,channel_type,display_name,external_account_id,status,secret_ref,created_at,updated_at) VALUES (?,?,'TELEGRAM','Test','test','ACTIVE','TEST_SECRET',?,?)",ACCOUNT,ORG_ONE,time(),time());
        jdbc.update("INSERT INTO conversations(id,organization_id,channel_account_id,external_thread_id,customer_id,status,created_at,updated_at) VALUES (?,?,?,'thread',?,'OPEN',?,?)",CONVERSATION,ORG_ONE,ACCOUNT,CUSTOMER,time(),time());
        jdbc.update("INSERT INTO conversation_messages(id,organization_id,conversation_id,direction,external_message_id,content_type,content,delivery_status,occurred_at,created_at) VALUES (?,?,?,'INBOUND','message','text','Xin hỏi giá 0912 345 678','RECEIVED',?,?)",MESSAGE,ORG_ONE,CONVERSATION,time(),time());
    }
    private String request() {return "{\"subjectType\":\"CUSTOMER\",\"subjectId\":\""+CUSTOMER+"\"}";}
    @Test void extractsRedactedEvidenceReviewsAndDoesNotDuplicate() throws Exception {
        JsonNode token=loginOne();
        mvc.perform(post("/api/v1/ai/signals/extract").header("Authorization",bearer(token)).contentType("application/json").content(request()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results[0].status").value("SUCCEEDED")).andExpect(jsonPath("$.results[0].created").value(1));
        assertThat(mock.lastInput()).contains("[PHONE_1]").doesNotContain("0912 345 678");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_signals",Integer.class)).isEqualTo(1);
        String id=jdbc.queryForObject("SELECT id FROM customer_signals",java.util.UUID.class).toString();
        mvc.perform(get("/api/v1/ai/signals?subjectType=CUSTOMER&subjectId="+CUSTOMER).header("Authorization",bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].evidenceText").value("Xin hỏi giá [PHONE_1]"));
        mvc.perform(post("/api/v1/ai/signals/"+id+"/edit").header("Authorization",bearer(token)).contentType("application/json").content("{\"value\":\"INVALID\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/ai/signals/"+id+"/accept").header("Authorization",bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACCEPTED"));
        mvc.perform(post("/api/v1/ai/signals/"+id+"/reject").header("Authorization",bearer(token))).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/ai/signals/extract").header("Authorization",bearer(token)).contentType("application/json").content(request())).andExpect(status().isOk()).andExpect(jsonPath("$.results.length()").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action='AI_SIGNAL_ACCEPTED'",Integer.class)).isEqualTo(1);
        String audit=jdbc.queryForObject("SELECT CAST(after_data AS VARCHAR) FROM audit_logs WHERE action='AI_SIGNAL_ACCEPTED'",String.class);
        assertThat(audit).doesNotContain("Xin hỏi giá","0912 345 678","PHONE_1");
    }
    @Test void invalidOutputAndProviderErrorsDoNotCreateSignals() throws Exception {
        JsonNode token=loginOne();mock.setNextReply(new AiProvider.Reply("{}",1,1,null));
        mvc.perform(post("/api/v1/ai/signals/extract").header("Authorization",bearer(token)).contentType("application/json").content(request()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results[0].errorCode").value("INVALID_OUTPUT"));
        mock.setNextReply(AiProvider.Reply.failed("TIMEOUT"));
        mvc.perform(post("/api/v1/ai/signals/extract").header("Authorization",bearer(token)).contentType("application/json").content(request()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results[0].errorCode").value("TIMEOUT"));
        mock.setNextReply(AiProvider.Reply.failed("RATE_LIMIT"));
        mvc.perform(post("/api/v1/ai/signals/extract").header("Authorization",bearer(token)).contentType("application/json").content(request()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results[0].errorCode").value("RATE_LIMIT"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_signals",Integer.class)).isZero();
    }
    @Test void emptyOutputIsSuccessfulAndNeverRetried() throws Exception {
        var token=loginOne();mock.setNextReply(new AiProvider.Reply("{\"signals\":[]}",1,1,null));
        mvc.perform(post("/api/v1/ai/signals/extract").header("Authorization",bearer(token)).contentType("application/json").content(request()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results[0].status").value("SUCCEEDED")).andExpect(jsonPath("$.results[0].created").value(0)).andExpect(jsonPath("$.remaining").value(0));
        mvc.perform(post("/api/v1/ai/signals/extract").header("Authorization",bearer(token)).contentType("application/json").content(request()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results.length()").value(0)).andExpect(jsonPath("$.remaining").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_invocations",Integer.class)).isEqualTo(1);
    }
    @Test void invalidOutputStopsAfterTwoAttempts() throws Exception {
        var token=loginOne();
        for(int i=0;i<2;i++) {mock.setNextReply(new AiProvider.Reply("{}",1,1,null));
            mvc.perform(post("/api/v1/ai/signals/extract").header("Authorization",bearer(token)).contentType("application/json").content(request()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.results[0].errorCode").value("INVALID_OUTPUT")).andExpect(jsonPath("$.remaining").value(i==0?1:0));}
        mvc.perform(post("/api/v1/ai/signals/extract").header("Authorization",bearer(token)).contentType("application/json").content(request()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results.length()").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_invocations",Integer.class)).isEqualTo(2);
    }
    @Test void temporaryErrorRemainsRetryable() throws Exception {
        var token=loginOne();mock.setNextReply(AiProvider.Reply.failed("TIMEOUT"));
        mvc.perform(post("/api/v1/ai/signals/extract").header("Authorization",bearer(token)).contentType("application/json").content(request()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results[0].errorCode").value("TIMEOUT")).andExpect(jsonPath("$.remaining").value(1));
        mock.setNextReply(new AiProvider.Reply("{\"signals\":[]}",1,1,null));
        mvc.perform(post("/api/v1/ai/signals/extract").header("Authorization",bearer(token)).contentType("application/json").content(request()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results[0].status").value("SUCCEEDED")).andExpect(jsonPath("$.remaining").value(0));
    }
    @Test void batchProcessesNewestFirstAndReportsRemaining() throws Exception {
        for(int i=1;i<=3;i++) jdbc.update("INSERT INTO conversation_messages(id,organization_id,conversation_id,direction,external_message_id,content_type,content,delivery_status,occurred_at,created_at) VALUES (?,?,?,'INBOUND',?,'text','Xin hỏi giá','RECEIVED',?,?)",id(2560+i),ORG_ONE,CONVERSATION,"new-"+i,java.sql.Timestamp.from(clock.instant().plusSeconds(i)),time());
        var token=loginOne();
        mvc.perform(post("/api/v1/ai/signals/extract").header("Authorization",bearer(token)).contentType("application/json").content(request()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results.length()").value(2)).andExpect(jsonPath("$.results[0].messageId").value(id(2563).toString())).andExpect(jsonPath("$.results[1].messageId").value(id(2562).toString())).andExpect(jsonPath("$.remaining").value(2));
        mvc.perform(post("/api/v1/ai/signals/extract").header("Authorization",bearer(token)).contentType("application/json").content(request()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results.length()").value(2)).andExpect(jsonPath("$.remaining").value(0));
    }
    @Test void stopsAfterFirstMessageWhenRequestBudgetIsExceeded() throws Exception {
        jdbc.update("INSERT INTO conversation_messages(id,organization_id,conversation_id,direction,external_message_id,content_type,content,delivery_status,occurred_at,created_at) VALUES (?,?,?,'INBOUND','newest','text','Xin hỏi giá','RECEIVED',?,?)",id(2570),ORG_ONE,CONVERSATION,java.sql.Timestamp.from(clock.instant().plusSeconds(1)),time());
        var token=loginOne();
        doAnswer(call->{clock.now=clock.now.plusSeconds(26);return call.callRealMethod();}).when(mock).generate(any());
        mvc.perform(post("/api/v1/ai/signals/extract").header("Authorization",bearer(token)).contentType("application/json").content(request()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results.length()").value(1)).andExpect(jsonPath("$.results[0].messageId").value(id(2570).toString())).andExpect(jsonPath("$.remaining").value(1));
        verify(mock,times(1)).generate(any());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_invocations",Integer.class)).isEqualTo(1);
    }
    @Test void processesBothMessagesWithinRequestBudget() throws Exception {
        jdbc.update("INSERT INTO conversation_messages(id,organization_id,conversation_id,direction,external_message_id,content_type,content,delivery_status,occurred_at,created_at) VALUES (?,?,?,'INBOUND','newest','text','Xin hỏi giá','RECEIVED',?,?)",id(2570),ORG_ONE,CONVERSATION,java.sql.Timestamp.from(clock.instant().plusSeconds(1)),time());
        var token=loginOne();
        doAnswer(call->{clock.now=clock.now.plusSeconds(10);return call.callRealMethod();}).when(mock).generate(any());
        mvc.perform(post("/api/v1/ai/signals/extract").header("Authorization",bearer(token)).contentType("application/json").content(request()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results.length()").value(2)).andExpect(jsonPath("$.remaining").value(0));
        verify(mock,times(2)).generate(any());
    }
    @Test void signalStorageFailureReturnsSafeError() throws Exception {
        JsonNode token=loginOne();
        jdbc.execute("ALTER TABLE customer_signals ADD CONSTRAINT test_block_signal CHECK (status <> 'PROPOSED')");
        try {
            mvc.perform(post("/api/v1/ai/signals/extract").header("Authorization",bearer(token)).contentType("application/json").content(request()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.results[0].status").value("FAILED")).andExpect(jsonPath("$.results[0].errorCode").value("STORAGE_ERROR"));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_signals",Integer.class)).isZero();
        } finally {jdbc.execute("ALTER TABLE customer_signals DROP CONSTRAINT test_block_signal");}
    }
    @Test void storageFailureRollsBackAllSignalsForMessage() throws Exception {
        JsonNode token=loginOne();
        mock.setNextReply(new AiProvider.Reply("{\"signals\":[{\"signalType\":\"INTENT\",\"value\":\"ASK_PRICE\",\"confidence\":0.9,\"evidenceText\":\"hỏi giá\"},{\"signalType\":\"SENTIMENT\",\"value\":\"NEUTRAL\",\"confidence\":0.8,\"evidenceText\":\"hỏi giá\"}]}",1,1,null));
        jdbc.execute("ALTER TABLE customer_signals ADD CONSTRAINT test_block_sentiment CHECK (signal_type <> 'SENTIMENT')");
        try {
            mvc.perform(post("/api/v1/ai/signals/extract").header("Authorization",bearer(token)).contentType("application/json").content(request()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.results[0].status").value("FAILED")).andExpect(jsonPath("$.results[0].created").value(0));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_signals",Integer.class)).isZero();
        } finally {jdbc.execute("ALTER TABLE customer_signals DROP CONSTRAINT test_block_sentiment");}
    }
    @Test void unlinkedMessagesAreNotExtracted() throws Exception {
        jdbc.update("UPDATE conversations SET customer_id=NULL WHERE id=?",CONVERSATION);
        JsonNode token=loginOne();
        mvc.perform(post("/api/v1/ai/signals/extract").header("Authorization",bearer(token)).contentType("application/json").content(request()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results.length()").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_signals",Integer.class)).isZero();
    }
    @Test void outboundMessagesAreNotExtracted() throws Exception {
        jdbc.update("UPDATE conversation_messages SET direction='OUTBOUND' WHERE id=?",MESSAGE);
        JsonNode token=loginOne();
        mvc.perform(post("/api/v1/ai/signals/extract").header("Authorization",bearer(token)).contentType("application/json").content(request()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results.length()").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_invocations",Integer.class)).isZero();
    }
    @Test void deniesOtherTenantAndDataStaffEvenWithContentPermissions() throws Exception {
        grant(ORG_TWO,"SALES_REP","ai.assist.use");grant(ORG_TWO,"SALES_REP","customer_360.read");grant(ORG_TWO,"SALES_REP","conversation.read");
        JsonNode foreign=success(login("two","shared@example.com",PASSWORD_TWO));
        mvc.perform(post("/api/v1/ai/signals/extract").header("Authorization",bearer(foreign)).contentType("application/json").content(request())).andExpect(status().isForbidden());
        role(ORG_ONE,"DATA_STAFF");user(id(2520),ORG_ONE,"Data","signal-data@example.test",hashOne);userRole(ORG_ONE,id(2520),"DATA_STAFF");
        for(String p:List.of("ai.assist.use","customer_360.read","conversation.read"))grant(ORG_ONE,"DATA_STAFF",p);
        JsonNode data=success(login("one","signal-data@example.test",PASSWORD_ONE));
        mvc.perform(get("/api/v1/ai/signals?subjectType=CUSTOMER&subjectId="+CUSTOMER).header("Authorization",bearer(data))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/ai/signals/extract").header("Authorization",bearer(data)).contentType("application/json").content(request())).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_signals",Integer.class)).isZero();
    }
    @Test void reviewRequiresPermissionAndAcceptsValidEdits() throws Exception {
        JsonNode token=loginOne();
        mvc.perform(post("/api/v1/ai/signals/extract").header("Authorization",bearer(token)).contentType("application/json").content(request())).andExpect(status().isOk());
        String id=jdbc.queryForObject("SELECT id FROM customer_signals",java.util.UUID.class).toString();
        jdbc.update("DELETE FROM role_permissions WHERE permission_id=(SELECT id FROM permissions WHERE code='ai.signal.review')");
        mvc.perform(post("/api/v1/ai/signals/"+id+"/reject").header("Authorization",bearer(token))).andExpect(status().isForbidden());
        grant(ORG_ONE,"MANAGER","ai.signal.review");
        mvc.perform(post("/api/v1/ai/signals/"+id+"/edit").header("Authorization",bearer(token)).contentType("application/json").content("{\"value\":\"ASK_PRICE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EDITED")).andExpect(jsonPath("$.value").value("ASK_PRICE"));
    }
    @Test void rejectChangesStatusAndAudits() throws Exception {
        JsonNode token=loginOne();
        mvc.perform(post("/api/v1/ai/signals/extract").header("Authorization",bearer(token)).contentType("application/json").content(request())).andExpect(status().isOk());
        String id=jdbc.queryForObject("SELECT id FROM customer_signals",java.util.UUID.class).toString();
        mvc.perform(post("/api/v1/ai/signals/"+id+"/reject").header("Authorization",bearer(token))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action='AI_SIGNAL_REJECTED'",Integer.class)).isEqualTo(1);
    }
    @Test void careCannotExtractLeadWithoutLeadRead() throws Exception {
        user(id(2530),ORG_ONE,"Care","signal-care@example.test",hashOne);userRole(ORG_ONE,id(2530),"CUSTOMER_CARE");
        grant(ORG_ONE,"CUSTOMER_CARE","ai.assist.use");grant(ORG_ONE,"CUSTOMER_CARE","conversation.read");
        JsonNode care=success(login("one","signal-care@example.test",PASSWORD_ONE));
        String lead="{\"subjectType\":\"LEAD\",\"subjectId\":\""+id(2531)+"\"}";
        mvc.perform(post("/api/v1/ai/signals/extract").header("Authorization",bearer(care)).contentType("application/json").content(lead)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_invocations",Integer.class)).isZero();
    }
    @Test void salesCannotExtractCustomerOutsideOwnScope() throws Exception {
        user(id(2540),ORG_ONE,"Sale","signal-sale@example.test",hashOne);userRole(ORG_ONE,id(2540),"SALES_REP");
        for(String p:List.of("ai.assist.use","customer_360.read","conversation.read"))grant(ORG_ONE,"SALES_REP",p);
        JsonNode sale=success(login("one","signal-sale@example.test",PASSWORD_ONE));
        mvc.perform(post("/api/v1/ai/signals/extract").header("Authorization",bearer(sale)).contentType("application/json").content(request())).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN_SCOPE"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_invocations",Integer.class)).isZero();
    }
    @Test void customerSignalsRequireCustomer360ReadEvenWithCustomerRead() throws Exception {
        jdbc.update("DELETE FROM role_permissions WHERE permission_id=(SELECT id FROM permissions WHERE code='customer_360.read')");
        JsonNode token=loginOne();String before=mock.lastInput();
        mvc.perform(post("/api/v1/ai/signals/extract").header("Authorization",bearer(token)).contentType("application/json").content(request())).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN_PERMISSION"));
        mvc.perform(get("/api/v1/ai/signals?subjectType=CUSTOMER&subjectId="+CUSTOMER).header("Authorization",bearer(token))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN_PERMISSION"));
        assertThat(mock.lastInput()).isEqualTo(before);
    }
    @Test void signalIsHiddenIfItsConversationIsUnlinked() throws Exception {
        JsonNode token=loginOne();
        mvc.perform(post("/api/v1/ai/signals/extract").header("Authorization",bearer(token)).contentType("application/json").content(request())).andExpect(status().isOk());
        String id=jdbc.queryForObject("SELECT id FROM customer_signals",java.util.UUID.class).toString();
        jdbc.update("UPDATE conversations SET customer_id=NULL WHERE id=?",CONVERSATION);
        mvc.perform(get("/api/v1/ai/signals?subjectType=CUSTOMER&subjectId="+CUSTOMER).header("Authorization",bearer(token))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(post("/api/v1/ai/signals/"+id+"/accept").header("Authorization",bearer(token))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN_SCOPE"));
    }
    @Test void extractsLinkedLeadMessageWithinScope() throws Exception {
        permission("lead.read");grant(ORG_ONE,"MANAGER","lead.read");
        jdbc.update("INSERT INTO offering_categories(id,organization_id,code,name,status,created_at,updated_at) VALUES (?,?,'SIGNAL','Signal','ACTIVE',?,?)",id(2550),ORG_ONE,time(),time());
        jdbc.update("INSERT INTO offerings(id,organization_id,category_id,team_id,code,name,kind,status,created_at,updated_at) VALUES (?,?,?,?,'SIGNAL','Signal','SERVICE','ACTIVE',?,?)",id(2551),ORG_ONE,id(2550),TEAM_MANAGED,time(),time());
        jdbc.update("INSERT INTO leads(id,organization_id,owner_user_id,offering_id,display_name,email,normalized_email,status,created_at,updated_at) VALUES (?,?,?,?,'Lead','signal-lead@example.test','signal-lead@example.test','NEW',?,?)",id(2552),ORG_ONE,USER_ONE,id(2551),time(),time());
        jdbc.update("UPDATE conversations SET customer_id=NULL,lead_id=? WHERE id=?",id(2552),CONVERSATION);
        var token=loginOne();String body="{\"subjectType\":\"LEAD\",\"subjectId\":\""+id(2552)+"\"}";
        mvc.perform(post("/api/v1/ai/signals/extract").header("Authorization",bearer(token)).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.results[0].status").value("SUCCEEDED"));
        mvc.perform(get("/api/v1/ai/signals?subjectType=LEAD&subjectId="+id(2552)).header("Authorization",bearer(token))).andExpect(status().isOk()).andExpect(jsonPath("$[0].signalType").value("INTENT"));
    }
}
