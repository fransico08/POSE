package com.pose.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.ai.*;
import com.pose.backend.identity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.sql.Timestamp;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties={"pose.ai.enabled=true","pose.ai.provider=mock","pose.sales-assignment.expiry-scan-delay-ms=86400000"})
class AiGatewayIntegrationTest extends FoundationTestSupport {
    private static final UUID SALE=id(1801),CARE=id(1802),DATA=id(1803),CUSTOMER=id(1804),OTHER=id(1805),LEAD=id(1806),CATEGORY=id(1807),OFFERING=id(1808);
    @Autowired AiGateway gateway; @Autowired MockProvider mock; @Autowired PlatformTransactionManager transactionManager;
    @BeforeEach void fixtures() {
        for(String p:List.of("ai.assist.use","ai.signal.review","ai.metrics.read","ai.admin","customer_360.read","interaction.read","task.manage","lead.read","offering.read")) permission(p);
        role(ORG_ONE,"ADMIN");role(ORG_ONE,"DATA_STAFF");
        for(String r:List.of("MANAGER","SALES_REP","CUSTOMER_CARE")) {grant(ORG_ONE,r,"ai.assist.use");grant(ORG_ONE,r,"customer_360.read");}
        grant(ORG_ONE,"DATA_STAFF","ai.metrics.read");grant(ORG_ONE,"SALES_REP","lead.read");grant(ORG_ONE,"SALES_REP","offering.read");
        user(SALE,ORG_ONE,"Sale","ai-sale@example.test",hashOne);user(CARE,ORG_ONE,"Care","ai-care@example.test",hashOne);user(DATA,ORG_ONE,"Data","ai-data@example.test",hashOne);
        userRole(ORG_ONE,SALE,"SALES_REP");userRole(ORG_ONE,CARE,"CUSTOMER_CARE");userRole(ORG_ONE,DATA,"DATA_STAFF");
        jdbc.update("INSERT INTO team_memberships(id,organization_id,team_id,user_id,joined_at,status) VALUES (?,?,?,?,?,'ACTIVE')",id(1809),ORG_ONE,TEAM_MANAGED,SALE,time());
        jdbc.update("INSERT INTO offering_categories(id,organization_id,code,name,status,created_at,updated_at) VALUES (?,?,'AI','AI','ACTIVE',?,?)",CATEGORY,ORG_ONE,time(),time());
        jdbc.update("INSERT INTO offerings(id,organization_id,category_id,team_id,code,name,kind,status,created_at,updated_at) VALUES (?,?,?,?,'AI','AI','SERVICE','ACTIVE',?,?)",OFFERING,ORG_ONE,CATEGORY,TEAM_MANAGED,time(),time());
        jdbc.update("INSERT INTO sales_assignments(id,organization_id,offering_id,sales_user_id,assigned_by_user_id,status,effective_from,created_at,updated_at) VALUES (?,?,?,?,?,'ACTIVE',?,?,?)",id(1810),ORG_ONE,OFFERING,SALE,USER_ONE,time(),time(),time());
        jdbc.update("INSERT INTO customers(id,organization_id,owner_user_id,display_name,email,normalized_email,phone,normalized_phone,status,created_at,updated_at) VALUES (?,?,?,'Example','person@example.test','person@example.test','0912 345 678','+84912345678','ACTIVE',?,?)",CUSTOMER,ORG_ONE,SALE,time(),time());
        jdbc.update("INSERT INTO customers(id,organization_id,owner_user_id,display_name,email,normalized_email,status,created_at,updated_at) VALUES (?,?,?,'Other','other-ai@example.test','other-ai@example.test','ACTIVE',?,?)",OTHER,ORG_ONE,USER_THREE,time(),time());
        jdbc.update("INSERT INTO leads(id,organization_id,owner_user_id,offering_id,display_name,email,normalized_email,status,created_at,updated_at) VALUES (?,?,?,?,'Lead','lead-ai@example.test','lead-ai@example.test','NEW',?,?)",LEAD,ORG_ONE,SALE,OFFERING,time(),time());
    }
    private ActorContext actor(UUID user,RoleCode role,PermissionCode... permissions){return new ActorContext(user,ORG_ONE,Set.of(role),Set.of(permissions),Set.of(TEAM_MANAGED));}
    private ActorContext sale(){return actor(SALE,RoleCode.SALES_REP,PermissionCode.AI_ASSIST_USE,PermissionCode.CUSTOMER_360_READ,PermissionCode.LEAD_READ,PermissionCode.OFFERING_READ);}
    private JsonNode loginUser(String email) throws Exception{return response(login("one",email,PASSWORD_ONE),200);}
    private AiGateway.Result create(){return gateway.invoke(sale(),AiUseCase.C360_SUMMARY,"CUSTOMER",CUSTOMER,Map.of());}
    @Test void successfulInvocationMasksContactsAndStoresNoPrompt() {
        var result=create();assertThat(result.status()).isEqualTo("SUCCEEDED");assertThat(result.suggestionId()).isNotNull();
        assertThat(mock.lastInput()).contains("[PHONE_1]","[EMAIL_1]").doesNotContain("person@example.test","0912 345 678");
        var row=jdbc.queryForMap("SELECT input_hash,redaction_count,status FROM ai_invocations WHERE id=?",result.invocationId());
        assertThat(row.get("input_hash").toString()).hasSize(64);assertThat(((Number)row.get("redaction_count")).intValue()).isEqualTo(2);
    }
    @Test void signalContextOmitsUnneededContactAndTasks() {
        var result=gateway.invoke(sale(),AiUseCase.SIGNAL_EXTRACT,"CUSTOMER",CUSTOMER,Map.of());
        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(mock.lastInput()).doesNotContain("email","phone","tasks","person@example.test","0912 345 678");
    }
    @Test void scopeAndPermissionRejectionsDoNotInvokeProvider() {
        String previous=mock.lastInput();long before=jdbc.queryForObject("SELECT count(*) FROM ai_invocations",Long.class);
        assertThatThrownBy(()->gateway.invoke(sale(),AiUseCase.C360_SUMMARY,"CUSTOMER",OTHER,Map.of())).isInstanceOf(AuthorizationDeniedException.class);
        var care=actor(CARE,RoleCode.CUSTOMER_CARE,PermissionCode.AI_ASSIST_USE,PermissionCode.CUSTOMER_360_READ);
        assertThatThrownBy(()->gateway.invoke(care,AiUseCase.C360_SUMMARY,"LEAD",LEAD,Map.of())).isInstanceOf(AuthorizationDeniedException.class);
        var data=actor(DATA,RoleCode.DATA_STAFF,PermissionCode.AI_METRICS_READ);
        assertThatThrownBy(()->gateway.invoke(data,AiUseCase.C360_SUMMARY,"CUSTOMER",CUSTOMER,Map.of())).isInstanceOf(AuthorizationDeniedException.class);
        var foreign=new ActorContext(USER_TWO,ORG_TWO,Set.of(RoleCode.SALES_REP),Set.of(PermissionCode.AI_ASSIST_USE,PermissionCode.CUSTOMER_360_READ),Set.of());
        assertThatThrownBy(()->gateway.invoke(foreign,AiUseCase.C360_SUMMARY,"CUSTOMER",CUSTOMER,Map.of())).isInstanceOf(AuthorizationDeniedException.class);
        jdbc.update("UPDATE leads SET owner_user_id=? WHERE id=?",USER_THREE,LEAD);
        assertThatThrownBy(()->gateway.invoke(sale(),AiUseCase.C360_SUMMARY,"LEAD",LEAD,Map.of())).isInstanceOf(AuthorizationDeniedException.class);
        var noAssist=actor(SALE,RoleCode.SALES_REP,PermissionCode.CUSTOMER_360_READ);
        assertThatThrownBy(()->gateway.invoke(noAssist,AiUseCase.C360_SUMMARY,"CUSTOMER",CUSTOMER,Map.of())).isInstanceOf(AuthorizationDeniedException.class);
        assertThat(mock.lastInput()).isEqualTo(previous);assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_invocations",Long.class)).isEqualTo(before);
    }
    @Test void budgetBlocksBeforeProvider() {
        jdbc.update("INSERT INTO ai_invocations(id,organization_id,actor_user_id,use_case,subject_type,subject_id,provider,cost_estimate,status,created_at) VALUES (?,?,?,'C360_SUMMARY','CUSTOMER',?,'mock',5,'SUCCEEDED',?)",id(1811),ORG_ONE,SALE,CUSTOMER,Timestamp.from(START));
        String before=mock.lastInput();var result=create();assertThat(result.status()).isEqualTo("BLOCKED_BUDGET");assertThat(mock.lastInput()).isEqualTo(before);
    }
    @Test void invocationSurvivesOuterTransactionRollback() {
        UUID[] invocation={null};
        assertThatThrownBy(()->new TransactionTemplate(transactionManager).executeWithoutResult(status->{invocation[0]=create().invocationId();throw new IllegalStateException("rollback");})).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_invocations WHERE id=?",Integer.class,invocation[0])).isEqualTo(1);
    }
    @Test void reviewTransitionsAuditAndEnforceScopeAndMetricsPermission() throws Exception {
        var first=create();JsonNode sale=loginUser("ai-sale@example.test"),data=loginUser("ai-data@example.test");
        String path="/api/v1/ai/suggestions/"+first.suggestionId();
        mvc.perform(get("/api/v1/ai/status").header("Authorization",bearer(sale))).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true)).andExpect(jsonPath("$.provider").doesNotExist());
        mvc.perform(get("/api/v1/ai/suggestions?subjectType=CUSTOMER&subjectId="+CUSTOMER).header("Authorization",bearer(sale))).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(first.suggestionId().toString()));
        JsonNode care=loginUser("ai-care@example.test");mvc.perform(post(path+"/accept").header("Authorization",bearer(care))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN_SCOPE"));
        mvc.perform(post(path+"/accept").header("Authorization",bearer(sale)).contentType("application/json").content("{\"finalContent\":\"Edited\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EDITED"));
        mvc.perform(post(path+"/reject").header("Authorization",bearer(sale))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AI_SUGGESTION_REVIEWED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action='AI_SUGGESTION_EDITED'",Integer.class)).isEqualTo(1);
        String audit=jdbc.queryForObject("SELECT CAST(after_data AS VARCHAR) FROM audit_logs WHERE action='AI_SUGGESTION_EDITED'",String.class);
        assertThat(audit).doesNotContain("Edited","person@example.test");
        var second=create();mvc.perform(post("/api/v1/ai/suggestions/"+second.suggestionId()+"/reject").header("Authorization",bearer(sale))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        var third=create();mvc.perform(post("/api/v1/ai/suggestions/"+third.suggestionId()+"/accept").header("Authorization",bearer(sale))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACCEPTED"));
        String metrics="/api/v1/ai/metrics?start=2026-09-01T00:00:00Z&end=2026-11-01T00:00:00Z";
        String metricsBody=mvc.perform(get(metrics).header("Authorization",bearer(data))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString();
        assertThat(metricsBody).doesNotContain("person@example.test","Edited","[PHONE_1]","Gợi ý");
        mvc.perform(get(metrics).header("Authorization",bearer(sale))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN_PERMISSION"));
        mvc.perform(get("/api/v1/ai/suggestions?subjectType=CUSTOMER&subjectId="+OTHER).header("Authorization",bearer(sale))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN_SCOPE"));
    }
}
