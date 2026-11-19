package com.pose.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.ai.AiProvider;
import com.pose.backend.ai.MockProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties = {"pose.ai.enabled=true", "pose.ai.provider=mock", "pose.ai.copilot.reuse-minutes=10"})
class ReplyDraftIntegrationTest extends FoundationTestSupport {
    private static final UUID ACCOUNT=id(3401), CONVERSATION=id(3402), MESSAGE=id(3403), OTHER=id(3404), DATA=id(3405), CARE=id(3406);
    @Autowired MockProvider mock;
    @Autowired ConfigurableEnvironment environment;

    @BeforeEach void fixtures() {
        mock.setNextReply(null);
        for(String code: List.of("ai.assist.use","ai.metrics.read","dashboard.read","conversation.read","conversation.reply")) {
            if(jdbc.queryForObject("SELECT count(*) FROM permissions WHERE code=?",Integer.class,code)==0) permission(code);
        }
        for(String role:List.of("MANAGER","SALES_REP","CUSTOMER_CARE")) {
            for(String code:List.of("ai.assist.use","conversation.read","conversation.reply")) grant(ORG_ONE,role,code);
        }
        grant(ORG_ONE,"MANAGER","ai.metrics.read"); grant(ORG_ONE,"MANAGER","dashboard.read");
        role(ORG_ONE,"DATA_STAFF");
        for(String code:List.of("ai.assist.use","conversation.read","conversation.reply")) grant(ORG_ONE,"DATA_STAFF",code);
        user(OTHER,ORG_ONE,"Other Sales","draft-other@example.test",hashOne); userRole(ORG_ONE,OTHER,"SALES_REP");
        user(DATA,ORG_ONE,"Data","draft-data@example.test",hashOne); userRole(ORG_ONE,DATA,"DATA_STAFF");
        userRole(ORG_ONE,DATA,"SALES_REP");
        user(CARE,ORG_ONE,"Other Care","draft-care@example.test",hashOne); userRole(ORG_ONE,CARE,"CUSTOMER_CARE");
        jdbc.update("INSERT INTO channel_accounts(id,organization_id,channel_type,display_name,external_account_id,team_id,default_assignee_user_id,status,secret_ref,created_at,updated_at) VALUES (?,?,'ZALO_SIM','Draft','draft-account',?,?,'ACTIVE','TEST_SECRET',?,?)", ACCOUNT,ORG_ONE,TEAM_MANAGED,USER_ONE,time(),time());
        jdbc.update("INSERT INTO conversations(id,organization_id,channel_account_id,external_thread_id,assigned_user_id,status,consent_status,created_at,updated_at) VALUES (?,?,?,'draft-thread',?,'OPEN','UNKNOWN',?,?)",CONVERSATION,ORG_ONE,ACCOUNT,USER_ONE,time(),time());
        jdbc.update("INSERT INTO conversation_messages(id,organization_id,conversation_id,direction,external_message_id,content_type,content,delivery_status,occurred_at,created_at) VALUES (?,?,?,'INBOUND','draft-message','TEXT',?,'RECEIVED',?,?)", MESSAGE,ORG_ONE,CONVERSATION,"Xin tư vấn 0912 345 678 hoặc reply@example.test",time(),time());
    }

    private JsonNode invoke(JsonNode actor,boolean force) throws Exception {
        return response(jsonRequest(post("/api/v1/ai/copilot/conversations/"+CONVERSATION+"/reply-draft"),actor,Map.of("force",force)),200);
    }

    @Test void createsRedactedSourcedDraftAndReusesOnlyForSameActor() throws Exception {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"flyway_schema_history\" WHERE \"version\"='16' AND \"success\"=true",Integer.class)).isEqualTo(1);
        JsonNode manager=loginOne(); JsonNode first=invoke(manager,false);
        assertThat(first.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(first.path("suggestion").path("subjectType").asText()).isEqualTo("CONVERSATION");
        assertThat(first.path("suggestion").path("content").asText()).contains("MESSAGE:"+MESSAGE);
        assertThat(mock.lastInput()).contains("[PHONE_1]","[EMAIL_1]").doesNotContain("0912 345 678","reply@example.test");
        assertThat(invoke(manager,false).path("reused").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM conversation_messages WHERE conversation_id=? AND direction='OUTBOUND'",Integer.class,CONVERSATION)).isZero();
        String suggestion=first.path("suggestion").path("id").asText();
        response(jsonRequest(post("/api/v1/ai/suggestions/"+suggestion+"/accept"),manager,Map.of()),200);
        response(mvc.perform(get("/api/v1/ai/metrics?start=2026-09-30T00:00:00Z&end=2026-10-02T00:00:00Z").header("Authorization",bearer(manager))),200);
        response(mvc.perform(get("/api/v1/dashboard/kpis?start=2026-09-30T00:00:00Z&end=2026-10-02T00:00:00Z&asOf=2026-10-01T00:00:00Z&metricIds=E05_AI_SUGGESTION_ACCEPTANCE_RATE").header("Authorization",bearer(manager))),200);
    }

    @Test void removesInventedSourcesAndRejectsAnUnsourcedDraft() throws Exception {
        JsonNode manager=loginOne();
        mock.setNextReply(new AiProvider.Reply("{\"draft\":\"Cảm ơn bạn.\",\"sources\":[\"MESSAGE:"+MESSAGE+"\",\"MESSAGE:invented\"]}",1,1,null));
        JsonNode valid=invoke(manager,true);
        assertThat(valid.path("suggestion").path("content").asText()).contains(MESSAGE.toString()).doesNotContain("invented");
        mock.setNextReply(new AiProvider.Reply("{\"draft\":\"Cảm ơn bạn.\",\"sources\":[\"MESSAGE:invented\"]}",1,1,null));
        assertThat(invoke(manager,true).path("errorCode").asText()).isEqualTo("INVALID_OUTPUT");
        mock.setNextReply(new AiProvider.Reply("{\"draft\":\"Gọi [PHONE_1]\",\"sources\":[\"MESSAGE:"+MESSAGE+"\"]}",1,1,null));
        assertThat(invoke(manager,true).path("errorCode").asText()).isEqualTo("INVALID_OUTPUT");
    }

    @Test void maliciousDraftIsRejectedWithoutSendingOrExposingDetail() throws Exception {
        JsonNode manager=loginOne();
        mock.setNextReply(new AiProvider.Reply("{\"draft\":\"Xem https://outside.example.test\",\"sources\":[\"MESSAGE:"+MESSAGE+"\"]}",1,1,null));
        JsonNode result=invoke(manager,true);
        assertThat(result.path("status").asText()).isEqualTo("FAILED");assertThat(result.path("errorCode").asText()).isEqualTo("INVALID_OUTPUT");
        assertThat(result.path("suggestion").isNull()).isTrue();assertThat(result.has("errorDetail")).isFalse();
        assertThat(jdbc.queryForObject("SELECT error_detail FROM ai_invocations WHERE id=?",String.class,UUID.fromString(result.path("invocationId").asText()))).isEqualTo("UNKNOWN_URL");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM conversation_messages WHERE conversation_id=? AND direction='OUTBOUND'",Integer.class,CONVERSATION)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM conversations WHERE id=?",String.class,CONVERSATION)).isEqualTo("OPEN");
        mock.setNextReply(new AiProvider.Reply("{\"draft\":\"Cảm ơn bạn.\",\"sources\":[\"MESSAGE:"+MESSAGE+"\",\"MESSAGE:11111111-2222-3333-4444-555555555555\"]}",1,1,null));
        JsonNode unknownSource=invoke(manager,true);
        assertThat(unknownSource.path("errorCode").asText()).isEqualTo("INVALID_OUTPUT");
        assertThat(jdbc.queryForObject("SELECT error_detail FROM ai_invocations WHERE id=?",String.class,UUID.fromString(unknownSource.path("invocationId").asText()))).isEqualTo("UNKNOWN_UUID");
    }

    @Test void rejectsOtherSalesDataStaffAndClosedConversation() throws Exception {
        JsonNode other=response(login("one","draft-other@example.test",PASSWORD_ONE),200);
        jsonRequest(post("/api/v1/ai/copilot/conversations/"+CONVERSATION+"/reply-draft"),other,Map.of()).andExpect(status().isNotFound());
        JsonNode data=response(login("one","draft-data@example.test",PASSWORD_ONE),200);
        jsonRequest(post("/api/v1/ai/copilot/conversations/"+CONVERSATION+"/reply-draft"),data,Map.of()).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN_PERMISSION"));
        JsonNode care=response(login("one","draft-care@example.test",PASSWORD_ONE),200);
        jsonRequest(post("/api/v1/ai/copilot/conversations/"+CONVERSATION+"/reply-draft"),care,Map.of()).andExpect(status().isNotFound());
        jdbc.update("UPDATE conversations SET status='CLOSED' WHERE id=?",CONVERSATION);
        jsonRequest(post("/api/v1/ai/copilot/conversations/"+CONVERSATION+"/reply-draft"),loginOne(),Map.of()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONVERSATION_CLOSED"));
    }

    @Test void providerFailureDoesNotSendAnyMessage() throws Exception {
        mock.setNextReply(AiProvider.Reply.failed("TIMEOUT"));
        assertThat(invoke(loginOne(),true).path("errorCode").asText()).isEqualTo("TIMEOUT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM conversation_messages WHERE conversation_id=? AND direction='OUTBOUND'",Integer.class,CONVERSATION)).isZero();
    }

    @Test void scopeAndCreatorRemainEnforcedWhenConversationIsReassigned() throws Exception {
        JsonNode manager=loginOne(); JsonNode first=invoke(manager,false);
        String suggestion=first.path("suggestion").path("id").asText();
        jdbc.update("UPDATE conversations SET assigned_user_id=? WHERE id=?",OTHER,CONVERSATION);
        JsonNode sales=response(login("one","draft-other@example.test",PASSWORD_ONE),200);
        JsonNode list=response(mvc.perform(get("/api/v1/ai/suggestions?subjectType=CONVERSATION&subjectId="+CONVERSATION).header("Authorization",bearer(sales))),200);
        assertThat(list.toString()).doesNotContain(suggestion);
        jsonRequest(post("/api/v1/ai/suggestions/"+suggestion+"/accept"),sales,Map.of()).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN_SCOPE"));
        jsonRequest(post("/api/v1/ai/suggestions/"+suggestion+"/reject"),sales,Map.of()).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN_SCOPE"));
        JsonNode second=invoke(sales,false); assertThat(second.path("reused").asBoolean()).isFalse();
        assertThat(second.path("suggestion").path("id").asText()).isNotEqualTo(suggestion);
    }

    @Test void inaccessibleLinkedProfileIsOmittedFromContext() throws Exception {
        jdbc.update("INSERT INTO customers(id,organization_id,owner_user_id,display_name,email,normalized_email,status,created_at,updated_at) VALUES (?,?,?,'Private profile','private@example.test','private@example.test','ACTIVE',?,?)",id(3410),ORG_ONE,USER_THREE,time(),time());
        jdbc.update("UPDATE conversations SET customer_id=?,assigned_user_id=? WHERE id=?",id(3410),OTHER,CONVERSATION);
        JsonNode sales=response(login("one","draft-other@example.test",PASSWORD_ONE),200);
        assertThat(invoke(sales,true).path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(mock.lastInput()).doesNotContain("Private profile","private@example.test");
    }

    @Test void managerOfAnotherTeamCannotDraftEvenWhenConversationExists() throws Exception {
        jdbc.update("UPDATE channel_accounts SET team_id=? WHERE id=?",TEAM_MEMBER_ONLY,ACCOUNT);
        jdbc.update("UPDATE conversations SET assigned_user_id=? WHERE id=?",OTHER,CONVERSATION);
        jsonRequest(post("/api/v1/ai/copilot/conversations/"+CONVERSATION+"/reply-draft"),loginOne(),Map.of()).andExpect(status().isNotFound());
    }

    @Test void visibleLinkedProfileIsIncluded() throws Exception {
        if(jdbc.queryForObject("SELECT count(*) FROM permissions WHERE code='customer_360.read'",Integer.class)==0) permission("customer_360.read");
        grant(ORG_ONE,"MANAGER","customer_360.read");
        jdbc.update("INSERT INTO customers(id,organization_id,owner_user_id,display_name,email,normalized_email,status,created_at,updated_at) VALUES (?,?,?,'Visible profile','visible@example.test','visible@example.test','ACTIVE',?,?)",id(3411),ORG_ONE,USER_ONE,time(),time());
        jdbc.update("UPDATE conversations SET customer_id=? WHERE id=?",id(3411),CONVERSATION);
        assertThat(invoke(loginOne(),true).path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(mock.lastInput()).contains("Visible profile").doesNotContain("visible@example.test");
    }

    @Test void disabledAndBudgetBlockedKeepManualMessagesAvailable() throws Exception {
        JsonNode manager=loginOne();
        try {
            environment.getPropertySources().addFirst(new MapPropertySource("reply-draft-test",Map.of("pose.ai.enabled",false)));
            assertThat(invoke(manager,true).path("status").asText()).isEqualTo("DISABLED");
            environment.getPropertySources().replace("reply-draft-test",new MapPropertySource("reply-draft-test",Map.of("pose.ai.enabled",true,"pose.ai.monthly-budget-usd","0")));
            assertThat(invoke(manager,true).path("status").asText()).isEqualTo("BLOCKED_BUDGET");
            response(mvc.perform(get("/api/v1/conversations/"+CONVERSATION+"/messages").header("Authorization",bearer(manager))),200);
        } finally { environment.getPropertySources().remove("reply-draft-test"); }
    }

    @Test void missingPermissionAndForeignTenantAreDenied() throws Exception {
        jdbc.update("DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE organization_id=? AND code IN ('MANAGER','CUSTOMER_CARE')) AND permission_id=(SELECT id FROM permissions WHERE code='ai.assist.use')",ORG_ONE);
        jsonRequest(post("/api/v1/ai/copilot/conversations/"+CONVERSATION+"/reply-draft"),loginOne(),Map.of()).andExpect(status().isForbidden());
        grant(ORG_ONE,"MANAGER","ai.assist.use");
        jdbc.update("DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE organization_id=? AND code IN ('MANAGER','CUSTOMER_CARE')) AND permission_id=(SELECT id FROM permissions WHERE code='conversation.reply')",ORG_ONE);
        jsonRequest(post("/api/v1/ai/copilot/conversations/"+CONVERSATION+"/reply-draft"),loginOne(),Map.of()).andExpect(status().isForbidden());
        for(String code:List.of("ai.assist.use","conversation.read","conversation.reply")) grant(ORG_TWO,"SALES_REP",code);
        JsonNode foreign=response(login("two","shared@example.com",PASSWORD_TWO),200);
        jsonRequest(post("/api/v1/ai/copilot/conversations/"+CONVERSATION+"/reply-draft"),foreign,Map.of()).andExpect(status().isNotFound());
    }
}
