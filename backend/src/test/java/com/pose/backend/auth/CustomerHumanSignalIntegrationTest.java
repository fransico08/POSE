package com.pose.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties={"pose.ai.enabled=false","pose.ai.provider=mock","pose.sales-assignment.expiry-scan-delay-ms=86400000"})
class CustomerHumanSignalIntegrationTest extends FoundationTestSupport {
    static final UUID CUSTOMER=id(2701),ACCOUNT=id(2702),CONVERSATION=id(2703),MESSAGE=id(2704);
    @BeforeEach void fixtures() {
        for(String permission:List.of("ai.assist.use","ai.signal.review","customer_360.read","conversation.read")) {permission(permission);grant(ORG_ONE,"MANAGER",permission);}
        jdbc.update("INSERT INTO customers(id,organization_id,owner_user_id,display_name,email,normalized_email,status,created_at,updated_at) VALUES (?,?,?,'Customer','human@example.test','human@example.test','ACTIVE',?,?)",CUSTOMER,ORG_ONE,USER_ONE,time(),time());
        jdbc.update("INSERT INTO channel_accounts(id,organization_id,channel_type,display_name,external_account_id,status,secret_ref,created_at,updated_at) VALUES (?,?,'TELEGRAM','Test','human','ACTIVE','TEST_SECRET',?,?)",ACCOUNT,ORG_ONE,time(),time());
        jdbc.update("INSERT INTO conversations(id,organization_id,channel_account_id,external_thread_id,customer_id,status,created_at,updated_at) VALUES (?,?,?,'human-thread',?,'OPEN',?,?)",CONVERSATION,ORG_ONE,ACCOUNT,CUSTOMER,time(),time());
        jdbc.update("INSERT INTO conversation_messages(id,organization_id,conversation_id,direction,external_message_id,content_type,content,delivery_status,occurred_at,created_at) VALUES (?,?,?,'INBOUND','human-message','text','Xin hỏi giá','RECEIVED',?,?)",MESSAGE,ORG_ONE,CONVERSATION,time(),time());
    }
    String body(UUID customer,UUID message,String type,String value,String evidence) throws Exception {
        var data=mapper.createObjectNode().put("subjectType","CUSTOMER").put("subjectId",customer.toString()).put("messageId",message.toString()).put("signalType",type).put("value",value);
        if(evidence!=null)data.put("evidenceText",evidence);return mapper.writeValueAsString(data);
    }
    @Test void createsAcceptedHumanWhenAiIsDisabledAndAuditsWithoutText() throws Exception {
        var token=loginOne(); var result=mvc.perform(post("/api/v1/ai/signals").header("Authorization",bearer(token)).contentType("application/json").content(body(CUSTOMER,MESSAGE,"INTENT","ASK_PRICE","hỏi giá")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.source").value("HUMAN")).andExpect(jsonPath("$.status").value("ACCEPTED")).andExpect(jsonPath("$.reviewedBy").value(USER_ONE.toString())).andExpect(jsonPath("$.confidence").isEmpty()).andExpect(jsonPath("$.invocationId").isEmpty()).andExpect(jsonPath("$.modelVersion").isEmpty()).andExpect(jsonPath("$.reviewedAt").isNotEmpty()).andReturn();
        var id=mapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_invocations",Integer.class)).isZero();
        String audit=jdbc.queryForObject("SELECT CAST(after_data AS VARCHAR) FROM audit_logs WHERE action='AI_SIGNAL_CREATED'",String.class);
        assertThat(audit).contains("ASK_PRICE").doesNotContain("Xin hỏi giá","hỏi giá");
        mvc.perform(post("/api/v1/ai/signals/"+id+"/accept").header("Authorization",bearer(token))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AI_SIGNAL_REVIEWED"));
        mvc.perform(get("/api/v1/ai/signals/messages?subjectType=CUSTOMER&subjectId="+CUSTOMER).header("Authorization",bearer(token))).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(MESSAGE.toString()));
    }
    @Test void validatesTypeValueAndExactEvidence() throws Exception {
        var token=loginOne();
        for(var data:List.of(body(CUSTOMER,MESSAGE,"URGENCY","HIGH",null),body(CUSTOMER,MESSAGE,"INTENT","BAD",null),body(CUSTOMER,MESSAGE,"INTENT","ASK_PRICE","không có"),body(CUSTOMER,MESSAGE,"INTENT","ASK_PRICE","   ")))
            mvc.perform(post("/api/v1/ai/signals").header("Authorization",bearer(token)).contentType("application/json").content(data)).andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_signals",Integer.class)).isZero();
    }
    @Test void rejectsDuplicateHumanAndInvalidSourceKey() throws Exception {
        var token=loginOne();var data=body(CUSTOMER,MESSAGE,"INTENT","ASK_PRICE",null);
        mvc.perform(post("/api/v1/ai/signals").header("Authorization",bearer(token)).contentType("application/json").content(data)).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/ai/signals").header("Authorization",bearer(token)).contentType("application/json").content(data)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AI_SIGNAL_DUPLICATE"));
        assertThatThrownBy(()->jdbc.update("INSERT INTO customer_signals(id,organization_id,customer_id,message_id,signal_type,\"value\",source,human_message_id,status,created_at) VALUES (?,?,?,?,?,'ASK_PRICE','HUMAN',?,'ACCEPTED',?)",id(2709),ORG_ONE,CUSTOMER,MESSAGE,"INTENT",MESSAGE,time())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE customer_signals SET human_message_id=NULL WHERE organization_id=?",ORG_ONE)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void rejectsOutboundMissingAndUnlinkedMessagesWithoutDistinguishing() throws Exception {
        var token=loginOne();var data=body(CUSTOMER,MESSAGE,"INTENT","ASK_PRICE",null);
        jdbc.update("UPDATE conversation_messages SET direction='OUTBOUND' WHERE id=?",MESSAGE);
        mvc.perform(post("/api/v1/ai/signals").header("Authorization",bearer(token)).contentType("application/json").content(data)).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.fieldErrors[0].field").value("messageId"));
        jdbc.update("UPDATE conversation_messages SET direction='INBOUND' WHERE id=?",MESSAGE);jdbc.update("UPDATE conversations SET customer_id=NULL WHERE id=?",CONVERSATION);
        mvc.perform(post("/api/v1/ai/signals").header("Authorization",bearer(token)).contentType("application/json").content(data)).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.fieldErrors[0].field").value("messageId"));
        mvc.perform(post("/api/v1/ai/signals").header("Authorization",bearer(token)).contentType("application/json").content(body(CUSTOMER,id(2799),"INTENT","ASK_PRICE",null))).andExpect(status().isUnprocessableEntity());
    }
    @Test void deniesOtherTenantAndMessagesOfAnotherCustomer() throws Exception {
        var token=loginOne();var other=id(2710);
        jdbc.update("INSERT INTO customers(id,organization_id,owner_user_id,display_name,email,normalized_email,status,created_at,updated_at) VALUES (?,?,?,'Other','other-human@example.test','other-human@example.test','ACTIVE',?,?)",other,ORG_ONE,USER_ONE,time(),time());
        mvc.perform(post("/api/v1/ai/signals").header("Authorization",bearer(token)).contentType("application/json").content(body(other,MESSAGE,"INTENT","ASK_PRICE",null))).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.fieldErrors[0].field").value("messageId"));
        grant(ORG_TWO,"SALES_REP","ai.assist.use");grant(ORG_TWO,"SALES_REP","ai.signal.review");grant(ORG_TWO,"SALES_REP","customer_360.read");grant(ORG_TWO,"SALES_REP","conversation.read");
        JsonNode foreign=success(login("two","shared@example.com",PASSWORD_TWO));
        mvc.perform(post("/api/v1/ai/signals").header("Authorization",bearer(foreign)).contentType("application/json").content(body(CUSTOMER,MESSAGE,"INTENT","ASK_PRICE",null))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN_SCOPE"));
    }
    @Test void deniesEachMissingPermissionAndDataStaff() throws Exception {
        var token=loginOne();String data=body(CUSTOMER,MESSAGE,"INTENT","ASK_PRICE",null);
        for(String permission:List.of("ai.assist.use","ai.signal.review","customer_360.read","conversation.read")) {
            jdbc.update("DELETE FROM role_permissions WHERE permission_id=(SELECT id FROM permissions WHERE code=?)",permission);
            mvc.perform(post("/api/v1/ai/signals").header("Authorization",bearer(token)).contentType("application/json").content(data)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN_PERMISSION"));
            grant(ORG_ONE,"MANAGER",permission);
        }
        role(ORG_ONE,"DATA_STAFF");userRole(ORG_ONE,USER_ONE,"DATA_STAFF");token=loginOne();
        mvc.perform(post("/api/v1/ai/signals").header("Authorization",bearer(token)).contentType("application/json").content(data)).andExpect(status().isForbidden());
    }
}
