package com.pose.backend.auth;

import com.pose.backend.ai.MockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties={"pose.ai.enabled=false","pose.ai.provider=mock","pose.sales-assignment.expiry-scan-delay-ms=86400000"})
class CustomerSignalDisabledTest extends FoundationTestSupport {
    @Autowired MockProvider mock;
    @Test void disabledDoesNotInvokeProviderAndRecordsInvocation() throws Exception {
        for(String p:List.of("ai.assist.use","customer_360.read","conversation.read")){permission(p);grant(ORG_ONE,"MANAGER",p);}
        var customer=id(2601);var account=id(2602);var conversation=id(2603);
        jdbc.update("INSERT INTO customers(id,organization_id,owner_user_id,display_name,email,normalized_email,status,created_at,updated_at) VALUES (?,?,?,'Test','disabled@example.test','disabled@example.test','ACTIVE',?,?)",customer,ORG_ONE,USER_ONE,time(),time());
        jdbc.update("INSERT INTO channel_accounts(id,organization_id,channel_type,display_name,external_account_id,status,secret_ref,created_at,updated_at) VALUES (?,?,'TELEGRAM','Test','test','ACTIVE','TEST_SECRET',?,?)",account,ORG_ONE,time(),time());
        jdbc.update("INSERT INTO conversations(id,organization_id,channel_account_id,external_thread_id,customer_id,status,created_at,updated_at) VALUES (?,?,?,'thread',?,'OPEN',?,?)",conversation,ORG_ONE,account,customer,time(),time());
        jdbc.update("INSERT INTO conversation_messages(id,organization_id,conversation_id,direction,external_message_id,content_type,content,delivery_status,occurred_at,created_at) VALUES (?,?,?,'INBOUND','message','text','Xin chào','RECEIVED',?,?)",id(2604),ORG_ONE,conversation,time(),time());
        String before=mock.lastInput();var token=loginOne();
        mvc.perform(post("/api/v1/ai/signals/extract").header("Authorization",bearer(token)).contentType("application/json").content("{\"subjectType\":\"CUSTOMER\",\"subjectId\":\""+customer+"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("DISABLED"));
        assertThat(mock.lastInput()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_invocations WHERE status='DISABLED'",Integer.class)).isEqualTo(1);
    }
}
