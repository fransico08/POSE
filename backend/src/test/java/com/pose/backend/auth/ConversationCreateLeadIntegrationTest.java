package com.pose.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ConversationCreateLeadIntegrationTest extends FoundationTestSupport {
    private static final UUID SALE=id(3400), DATA=id(3401), ACCOUNT=id(3402), CONV=id(3403), OTHER=id(3404), CAT=id(3405), OFFER=id(3406);
    @BeforeEach void fixtures() {
        for(String code:List.of("conversation.read","lead.read","lead.write","offering.read"))
            if(jdbc.queryForObject("SELECT count(*) FROM permissions WHERE code=?",Integer.class,code)==0)permission(code);
        for(String code:List.of("conversation.read","lead.read","lead.write","offering.read"))grant(ORG_ONE,"SALES_REP",code);
        role(ORG_ONE,"DATA_STAFF");user(DATA,ORG_ONE,"Data","create-lead-data@example.test",hashOne);userRole(ORG_ONE,DATA,"DATA_STAFF");
        grant(ORG_ONE,"DATA_STAFF","conversation.read");grant(ORG_ONE,"DATA_STAFF","lead.write");
        user(SALE,ORG_ONE,"Sales","create-lead-sale@example.test",hashOne);userRole(ORG_ONE,SALE,"SALES_REP");
        jdbc.update("INSERT INTO team_memberships(id,organization_id,team_id,user_id,joined_at,status) VALUES (?,?,?,?,?,'ACTIVE')",id(3407),ORG_ONE,TEAM_MANAGED,SALE,time());
        jdbc.update("INSERT INTO offering_categories(id,organization_id,code,name,status,created_at,updated_at) VALUES (?,?,'INBOX','Inbox','ACTIVE',?,?)",CAT,ORG_ONE,time(),time());
        jdbc.update("INSERT INTO offerings(id,organization_id,category_id,team_id,code,name,kind,status,created_at,updated_at) VALUES (?,?,?,?,'INBOX','Inbox','SERVICE','ACTIVE',?,?)",OFFER,ORG_ONE,CAT,TEAM_MANAGED,time(),time());
        jdbc.update("INSERT INTO sales_assignments(id,organization_id,offering_id,sales_user_id,assigned_by_user_id,status,effective_from,created_at,updated_at) VALUES (?,?,?,?,?,'ACTIVE',?,?,?)",id(3408),ORG_ONE,OFFER,SALE,USER_ONE,time(),time(),time());
        jdbc.update("INSERT INTO channel_accounts(id,organization_id,channel_type,display_name,external_account_id,team_id,default_assignee_user_id,status,secret_ref,created_at,updated_at) VALUES (?,?,'ZALO_SIM','Inbox','create-lead',?,?,'ACTIVE','POSE_TEST_SECRET',?,?)",ACCOUNT,ORG_ONE,TEAM_MANAGED,SALE,time(),time());
        jdbc.update("INSERT INTO conversations(id,organization_id,channel_account_id,external_thread_id,assigned_user_id,status,consent_status,created_at,updated_at) VALUES (?,?,?,'one',?,'OPEN','UNKNOWN',?,?),(?,?,?,'two',?,'OPEN','UNKNOWN',?,?)",CONV,ORG_ONE,ACCOUNT,SALE,time(),time(),OTHER,ORG_ONE,ACCOUNT,USER_ONE,time(),time());
    }
    private JsonNode sales() throws Exception {return response(login("one","create-lead-sale@example.test",PASSWORD_ONE),200);}
    private Map<String,Object> body(String phone) {var b=new HashMap<String,Object>();b.put("displayName","Khách demo");b.put("offeringId",OFFER);if(phone!=null)b.put("phone",phone);return b;}
    @Test void createsAndLinksAtomicallyWithDefaultOrSelectedSource() throws Exception {
        JsonNode auth=sales();var created=response(jsonRequest(post("/api/v1/conversations/"+CONV+"/lead"),auth,body("0912345678")),201);
        assertThat(created.path("lead").path("source").asText()).isEqualTo("SOCIAL_MEDIA");
        assertThat(created.path("conversation").path("leadId").asText()).isEqualTo(created.path("lead").path("id").asText());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action IN ('LEAD_CREATED','CONVERSATION_LINKED')",Integer.class)).isEqualTo(2);
        jdbc.update("UPDATE conversations SET assigned_user_id=? WHERE id=?",SALE,OTHER);
        var selected=body("0912345679");selected.put("source","REFERRAL");
        assertThat(response(jsonRequest(post("/api/v1/conversations/"+OTHER+"/lead"),auth,selected),201).path("lead").path("source").asText()).isEqualTo("REFERRAL");
    }
    @Test void validationDuplicateAndScopeDoNotChangeData() throws Exception {
        JsonNode auth=sales();String path="/api/v1/conversations/"+CONV+"/lead";
        jsonRequest(post(path),auth,body(null)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors").isArray());
        var invalid=body("0912345678");invalid.put("source","NOT_A_SOURCE");
        jsonRequest(post(path),auth,invalid).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("source"));
        jsonRequest(post("/api/v1/conversations/"+OTHER+"/lead"),auth,body("0912345678")).andExpect(status().isNotFound());
        JsonNode data=response(login("one","create-lead-data@example.test",PASSWORD_ONE),200);
        jsonRequest(post(path),data,body("0912345678")).andExpect(status().isForbidden());
        for(String code:List.of("conversation.read","lead.read","lead.write","offering.read"))grant(ORG_TWO,"SALES_REP",code);
        JsonNode foreign=response(login("two","shared@example.com",PASSWORD_TWO),200);
        jsonRequest(post(path),foreign,body("0912345678")).andExpect(status().isNotFound());
        jdbc.update("DELETE FROM role_permissions WHERE role_id=(SELECT id FROM roles WHERE organization_id=? AND code='SALES_REP') AND permission_id=(SELECT id FROM permissions WHERE code='lead.write')",ORG_ONE);
        jsonRequest(post(path),auth,body("0912345678")).andExpect(status().isForbidden());
        grant(ORG_ONE,"SALES_REP","lead.write");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leads",Integer.class)).isZero();
        response(jsonRequest(post(path),auth,body("0912345678")),201);
        jsonRequest(post(path),auth,body("0912345679")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONVERSATION_ALREADY_LINKED"));
        jdbc.update("UPDATE conversations SET assigned_user_id=? WHERE id=?",SALE,OTHER);
        jsonRequest(post("/api/v1/conversations/"+OTHER+"/lead"),auth,body("0912345678")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LEAD_DUPLICATE"));
        assertThat(jdbc.queryForObject("SELECT lead_id FROM conversations WHERE id=?",UUID.class,OTHER)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leads",Integer.class)).isEqualTo(1);
    }
    @Test void linkAuditFailureRollsBackLeadAndConversation() throws Exception {
        JsonNode auth=sales();jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT test_inbox_link_failure CHECK(action <> 'CONVERSATION_LINKED')");
        try {
            jsonRequest(post("/api/v1/conversations/"+CONV+"/lead"),auth,body("0912345678")).andExpect(status().isConflict());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM leads",Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT lead_id FROM conversations WHERE id=?",UUID.class,CONV)).isNull();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action='LEAD_CREATED'",Integer.class)).isZero();
        } finally {jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT test_inbox_link_failure");}
    }
    @Test void managerCreatesForAnEligibleAssignedSalesOwner() throws Exception {
        for(String code:List.of("conversation.read","lead.read","lead.write"))grant(ORG_ONE,"MANAGER",code);
        var input=body("0912345680");input.put("ownerUserId",SALE);
        var created=response(jsonRequest(post("/api/v1/conversations/"+CONV+"/lead"),loginOne(),input),201);
        assertThat(created.path("lead").path("ownerUserId").asText()).isEqualTo(SALE.toString());
    }
}
