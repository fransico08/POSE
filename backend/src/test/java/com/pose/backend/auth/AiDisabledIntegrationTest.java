package com.pose.backend.auth;

import com.pose.backend.ai.*;
import com.pose.backend.identity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties={"pose.ai.enabled=false","pose.ai.provider=mock","pose.sales-assignment.expiry-scan-delay-ms=86400000"})
class AiDisabledIntegrationTest extends FoundationTestSupport {
    @Autowired AiGateway gateway; @Autowired MockProvider mock;
    @BeforeEach void permissionSetup(){permission("ai.assist.use");grant(ORG_ONE,"MANAGER","ai.assist.use");}
    @Test void disabledIsRecordedWithoutCallingProvider() throws Exception {
        var actor=new ActorContext(USER_ONE,ORG_ONE,Set.of(RoleCode.MANAGER),Set.of(PermissionCode.AI_ASSIST_USE),Set.of());
        String before=mock.lastInput();var result=gateway.invoke(actor,AiUseCase.C360_SUMMARY,"CUSTOMER",id(1),Map.of());
        assertThat(result.status()).isEqualTo("DISABLED");assertThat(mock.lastInput()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT status FROM ai_invocations WHERE id=?",String.class,result.invocationId())).isEqualTo("DISABLED");
        mvc.perform(get("/api/v1/ai/status").header("Authorization",bearer(loginOne()))).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
    }
}
