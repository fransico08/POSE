package com.pose.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConversationApiIntegrationTest extends FoundationTestSupport {
    private static final UUID ACCOUNT = id(2910);
    private static final UUID FIRST_CONVERSATION = id(2911);
    private static final UUID SECOND_CONVERSATION = id(2912);
    private static final UUID SALES_USER = id(2913);
    private static final UUID DATA_USER = id(2914);
    private static final UUID INBOUND = id(2915);

    @BeforeEach
    void fixtures() {
        for (String permission : List.of("conversation.read", "conversation.reply", "conversation.assign")) {
            if (jdbc.queryForObject("SELECT count(*) FROM permissions WHERE code=?", Integer.class, permission) == 0) {
                permission(permission);
            }
        }
        grant(ORG_ONE, "MANAGER", "conversation.read");
        grant(ORG_ONE, "MANAGER", "conversation.reply");
        grant(ORG_ONE, "MANAGER", "conversation.assign");
        grant(ORG_ONE, "CUSTOMER_CARE", "conversation.read");
        grant(ORG_ONE, "CUSTOMER_CARE", "conversation.reply");
        grant(ORG_ONE, "SALES_REP", "conversation.read");
        grant(ORG_ONE, "SALES_REP", "conversation.reply");

        role(ORG_ONE, "DATA_STAFF");
        user(DATA_USER, ORG_ONE, "Conversation Data Staff", "conversation-data@example.test", hashOne);
        userRole(ORG_ONE, DATA_USER, "DATA_STAFF");
        grant(ORG_ONE, "DATA_STAFF", "conversation.read");
        grant(ORG_ONE, "DATA_STAFF", "conversation.reply");

        user(SALES_USER, ORG_ONE, "Conversation Sales", "conversation-sales@example.test", hashOne);
        userRole(ORG_ONE, SALES_USER, "SALES_REP");
        jdbc.update("INSERT INTO team_memberships(id,organization_id,team_id,user_id,joined_at,status) VALUES (?,?,?,?,?,'ACTIVE')",
                id(2916), ORG_ONE, TEAM_MANAGED, SALES_USER, time());

        jdbc.update("""
                INSERT INTO channel_accounts(id,organization_id,channel_type,display_name,external_account_id,
                    team_id,default_assignee_user_id,status,secret_ref,created_at,updated_at)
                VALUES (?,?,'ZALO_SIM','Conversation API test','conversation-api-test',?,?, 'ACTIVE','POSE_TEST_SECRET',?,?)
                """, ACCOUNT, ORG_ONE, TEAM_MANAGED, USER_ONE, time(), time());
        jdbc.update("""
                INSERT INTO conversations(id,organization_id,channel_account_id,external_thread_id,assigned_user_id,
                    status,consent_status,last_message_at,created_at,updated_at)
                VALUES (?,?,?,'first-thread',?,'OPEN','UNKNOWN',?,?,?),
                       (?,?,?,'second-thread',?,'OPEN','UNKNOWN',?,?,?)
                """, FIRST_CONVERSATION, ORG_ONE, ACCOUNT, USER_ONE, time(), time(), time(),
                SECOND_CONVERSATION, ORG_ONE, ACCOUNT, USER_ONE, time(), time(), time());
        jdbc.update("""
                INSERT INTO conversation_messages(id,organization_id,conversation_id,direction,external_message_id,
                    content_type,content,delivery_status,occurred_at,created_at)
                VALUES (?,? ,?,'INBOUND','inbound-1','TEXT','Xin chào','RECEIVED',?,?)
                """, INBOUND, ORG_ONE, FIRST_CONVERSATION, time(), time());
    }

    @Test
    void managerAssignsInboxReplyIsRecordedAndSalesAndDataStaffRemainScoped() throws Exception {
        JsonNode manager = loginOne();
        JsonNode managerInbox = response(mvc.perform(MockMvcRequestBuilders.get("/api/v1/conversations")
                .header("Authorization", bearer(manager))), 200);
        assertThat(managerInbox.path("totalItems").asInt()).isEqualTo(2);

        response(jsonRequest(MockMvcRequestBuilders.post("/api/v1/conversations/" + FIRST_CONVERSATION + "/assign"),
                manager, Map.of("assignedUserId", SALES_USER)), 200);

        JsonNode sales = response(login("one", "conversation-sales@example.test", PASSWORD_ONE), 200);
        JsonNode salesInbox = response(mvc.perform(MockMvcRequestBuilders.get("/api/v1/conversations")
                .header("Authorization", bearer(sales))), 200);
        assertThat(salesInbox.path("totalItems").asInt()).isEqualTo(1);
        assertThat(salesInbox.path("items").toString()).contains(FIRST_CONVERSATION.toString())
                .doesNotContain(SECOND_CONVERSATION.toString());
        error(mvc.perform(MockMvcRequestBuilders.get("/api/v1/conversations/" + SECOND_CONVERSATION + "/messages")
                .header("Authorization", bearer(sales))), 404, "RESOURCE_NOT_FOUND");

        JsonNode sent = response(jsonRequest(MockMvcRequestBuilders.post("/api/v1/conversations/" + FIRST_CONVERSATION + "/messages"),
                sales, Map.of("content", "Tôi sẽ hỗ trợ bạn.")), 201);
        assertThat(sent.path("direction").asText()).isEqualTo("OUTBOUND");
        assertThat(sent.path("deliveryStatus").asText()).isEqualTo("SENT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM conversation_messages WHERE conversation_id=? AND direction='OUTBOUND' AND sent_by_user_id=?",
                Integer.class, FIRST_CONVERSATION, SALES_USER)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM interactions WHERE conversation_id=? AND recorded_by_actor_type='SYSTEM'",
                Integer.class, FIRST_CONVERSATION)).isEqualTo(1);

        JsonNode data = response(login("one", "conversation-data@example.test", PASSWORD_ONE), 200);
        error(mvc.perform(MockMvcRequestBuilders.get("/api/v1/conversations")
                .header("Authorization", bearer(data))), 403, "FORBIDDEN_PERMISSION");
        error(mvc.perform(MockMvcRequestBuilders.get("/api/v1/conversations/" + FIRST_CONVERSATION + "/messages")
                .header("Authorization", bearer(data))), 403, "FORBIDDEN_PERMISSION");
        error(jsonRequest(MockMvcRequestBuilders.post("/api/v1/conversations/" + FIRST_CONVERSATION + "/messages"),
                data, Map.of("content", "Không được phép.")), 403, "FORBIDDEN_PERMISSION");
    }

    @Test
    void inboxShowsUnicodeSafePreviewOfLatestMessageAndNoMessageAsNull() throws Exception {
        String content = "a".repeat(79) + "😀" + "tail";
        jdbc.update("INSERT INTO conversation_messages(id,organization_id,conversation_id,direction,external_message_id,content_type,content,delivery_status,occurred_at,created_at) VALUES (?,?,?,'OUTBOUND','preview','TEXT',?,'SENT',?,?)",
                id(2930), ORG_ONE, FIRST_CONVERSATION, content, java.sql.Timestamp.from(clock.instant().plusSeconds(5)), time());
        JsonNode manager = loginOne();
        JsonNode items = response(mvc.perform(MockMvcRequestBuilders.get("/api/v1/conversations")
                .header("Authorization", bearer(manager))), 200).path("items");
        JsonNode first = null, empty = null;
        for (JsonNode item : items) {
            if (FIRST_CONVERSATION.toString().equals(item.path("id").asText())) first = item;
            if (SECOND_CONVERSATION.toString().equals(item.path("id").asText())) empty = item;
        }
        assertThat(first).isNotNull();
        assertThat(first.path("lastMessagePreview").asText()).isEqualTo("a".repeat(79) + "😀");
        assertThat(first.path("lastMessageDirection").asText()).isEqualTo("OUTBOUND");
        assertThat(empty).isNotNull();
        assertThat(empty.path("lastMessagePreview").isNull()).isTrue();
        assertThat(empty.path("lastMessageDirection").isNull()).isTrue();
        JsonNode data = response(login("one", "conversation-data@example.test", PASSWORD_ONE), 200);
        error(mvc.perform(MockMvcRequestBuilders.get("/api/v1/conversations")
                .header("Authorization", bearer(data))), 403, "FORBIDDEN_PERMISSION");
    }

    @Test
    void messagesAreNewestFirstAndPaginated() throws Exception {
        jdbc.update("INSERT INTO conversation_messages(id,organization_id,conversation_id,direction,external_message_id,content_type,content,delivery_status,occurred_at,created_at) VALUES (?,? ,?,'INBOUND','newer','TEXT','Newer','RECEIVED',?,?)",id(2928),ORG_ONE,FIRST_CONVERSATION,java.sql.Timestamp.from(clock.instant().plusSeconds(5)),time());
        JsonNode manager=loginOne();
        JsonNode first=response(mvc.perform(MockMvcRequestBuilders.get("/api/v1/conversations/"+FIRST_CONVERSATION+"/messages?page=0&size=1").header("Authorization",bearer(manager))),200);
        assertThat(first.path("totalItems").asInt()).isEqualTo(2);assertThat(first.path("items").get(0).path("id").asText()).isEqualTo(id(2928).toString());
        JsonNode next=response(mvc.perform(MockMvcRequestBuilders.get("/api/v1/conversations/"+FIRST_CONVERSATION+"/messages?page=1&size=1").header("Authorization",bearer(manager))),200);
        assertThat(next.path("items").get(0).path("id").asText()).isEqualTo(INBOUND.toString());
    }

    @Test
    void assignmentCandidatesRequireManagementOfAccountTeam() throws Exception {
        JsonNode manager=loginOne();
        JsonNode candidates=response(mvc.perform(MockMvcRequestBuilders.get("/api/v1/conversations/"+FIRST_CONVERSATION+"/assignees").header("Authorization",bearer(manager))),200);
        assertThat(candidates.toString()).contains(SALES_USER.toString());
        JsonNode sales=response(login("one","conversation-sales@example.test",PASSWORD_ONE),200);
        error(mvc.perform(MockMvcRequestBuilders.get("/api/v1/conversations/"+FIRST_CONVERSATION+"/assignees").header("Authorization",bearer(sales))),403,"FORBIDDEN_PERMISSION");
        JsonNode data=response(login("one","conversation-data@example.test",PASSWORD_ONE),200);
        error(mvc.perform(MockMvcRequestBuilders.get("/api/v1/conversations/"+FIRST_CONVERSATION+"/assignees").header("Authorization",bearer(data))),403,"FORBIDDEN_PERMISSION");
        user(id(2929),ORG_ONE,"Care","conversation-care@example.test",hashOne);userRole(ORG_ONE,id(2929),"CUSTOMER_CARE");
        JsonNode care=response(login("one","conversation-care@example.test",PASSWORD_ONE),200);
        error(mvc.perform(MockMvcRequestBuilders.get("/api/v1/conversations/"+FIRST_CONVERSATION+"/assignees").header("Authorization",bearer(care))),403,"FORBIDDEN_PERMISSION");
        grant(ORG_TWO,"MANAGER","conversation.read");grant(ORG_TWO,"MANAGER","conversation.assign");userRole(ORG_TWO,USER_TWO,"MANAGER");
        JsonNode foreign=response(login("two","shared@example.com",PASSWORD_TWO),200);
        error(mvc.perform(MockMvcRequestBuilders.get("/api/v1/conversations/"+FIRST_CONVERSATION+"/assignees").header("Authorization",bearer(foreign))),403,"FORBIDDEN_SCOPE");
        userRole(ORG_ONE,USER_ONE,"SALES_REP");jdbc.update("UPDATE channel_accounts SET team_id=? WHERE id=?",TEAM_MEMBER_ONLY,ACCOUNT);
        manager=loginOne();
        response(mvc.perform(MockMvcRequestBuilders.get("/api/v1/conversations/"+FIRST_CONVERSATION+"/messages").header("Authorization",bearer(manager))),200);
        response(jsonRequest(MockMvcRequestBuilders.post("/api/v1/conversations/"+FIRST_CONVERSATION+"/messages"),manager,Map.of("content","Reply")),201);
        error(mvc.perform(MockMvcRequestBuilders.get("/api/v1/conversations/"+FIRST_CONVERSATION+"/assignees").header("Authorization",bearer(manager))),403,"FORBIDDEN_SCOPE");
        error(jsonRequest(MockMvcRequestBuilders.post("/api/v1/conversations/"+FIRST_CONVERSATION+"/assign"),manager,Map.of("assignedUserId",SALES_USER)),403,"FORBIDDEN_SCOPE");
        error(mvc.perform(MockMvcRequestBuilders.get("/api/v1/conversations/"+id(2999)+"/assignees").header("Authorization",bearer(manager))),403,"FORBIDDEN_SCOPE");
    }

    @Test
    void linkRejectsAnyReplacementAndLeavesSystemInteractionUntouched() throws Exception {
        permission("lead.read");grant(ORG_ONE,"MANAGER","lead.read");
        jdbc.update("INSERT INTO offering_categories(id,organization_id,code,name,status,created_at,updated_at) VALUES (?,?,'LINK','Link','ACTIVE',?,?)",id(2920),ORG_ONE,time(),time());
        jdbc.update("INSERT INTO offerings(id,organization_id,category_id,team_id,code,name,kind,status,created_at,updated_at) VALUES (?,?,?,?,'LINK','Link','SERVICE','ACTIVE',?,?)",id(2921),ORG_ONE,id(2920),TEAM_MANAGED,time(),time());
        jdbc.update("INSERT INTO leads(id,organization_id,owner_user_id,offering_id,display_name,email,normalized_email,status,created_at,updated_at) VALUES (?,?,?,?,'Lead','link-lead@example.test','link-lead@example.test','NEW',?,?)",id(2922),ORG_ONE,USER_ONE,id(2921),time(),time());
        jdbc.update("INSERT INTO leads(id,organization_id,owner_user_id,offering_id,display_name,email,normalized_email,status,created_at,updated_at) VALUES (?,?,?,?,'Lead2','link-lead2@example.test','link-lead2@example.test','NEW',?,?)",id(2924),ORG_ONE,USER_ONE,id(2921),time(),time());
        jdbc.update("INSERT INTO customers(id,organization_id,owner_user_id,display_name,email,normalized_email,status,created_at,updated_at) VALUES (?,?,?,'Customer','link-customer@example.test','link-customer@example.test','ACTIVE',?,?)",id(2923),ORG_ONE,USER_ONE,time(),time());
        JsonNode manager=loginOne();
        response(jsonRequest(MockMvcRequestBuilders.post("/api/v1/conversations/"+FIRST_CONVERSATION+"/messages"),manager,Map.of("content","Reply")),201);
        response(jsonRequest(MockMvcRequestBuilders.post("/api/v1/conversations/"+FIRST_CONVERSATION+"/link"),manager,Map.of("leadId",id(2922))),200);
        jdbc.update("INSERT INTO customer_signals(id,organization_id,lead_id,message_id,signal_type,\"value\",source,human_message_id,status,created_at) VALUES (?,?,?,?,'INTENT','OTHER','HUMAN',?,'ACCEPTED',?)",id(2925),ORG_ONE,id(2922),INBOUND,INBOUND,time());
        long audits=jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action='CONVERSATION_LINKED'",Long.class);
        response(jsonRequest(MockMvcRequestBuilders.post("/api/v1/conversations/"+FIRST_CONVERSATION+"/link"),manager,Map.of("leadId",id(2922))),200);
        error(jsonRequest(MockMvcRequestBuilders.post("/api/v1/conversations/"+FIRST_CONVERSATION+"/link"),manager,Map.of("customerId",id(2923))),409,"CONVERSATION_ALREADY_LINKED");
        error(jsonRequest(MockMvcRequestBuilders.post("/api/v1/conversations/"+FIRST_CONVERSATION+"/link"),manager,Map.of("leadId",id(2924))),409,"CONVERSATION_ALREADY_LINKED");
        assertThat(jdbc.queryForObject("SELECT lead_id FROM interactions WHERE conversation_id=?",UUID.class,FIRST_CONVERSATION)).isEqualTo(id(2922));
        assertThat(jdbc.queryForObject("SELECT lead_id FROM customer_signals WHERE id=?",UUID.class,id(2925))).isEqualTo(id(2922));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action='CONVERSATION_LINKED'",Long.class)).isEqualTo(audits);
        response(jsonRequest(MockMvcRequestBuilders.post("/api/v1/conversations/"+SECOND_CONVERSATION+"/link"),manager,Map.of("customerId",id(2923))),200);
        error(jsonRequest(MockMvcRequestBuilders.post("/api/v1/conversations/"+SECOND_CONVERSATION+"/link"),manager,Map.of("leadId",id(2922))),409,"CONVERSATION_ALREADY_LINKED");
    }

    @Test
    void adminConfiguresChannelTeamAndDefaultOwnerWithoutExposingSecret() throws Exception {
        if (jdbc.queryForObject("SELECT count(*) FROM permissions WHERE code='channel.manage'", Integer.class) == 0) {
            permission("channel.manage");
        }
        admin();
        grant(ORG_ONE, "ADMIN", "channel.manage");

        JsonNode admin = loginOne();
        Map<String, Object> invalidOwner = Map.of(
                "channelType", "TELEGRAM",
                "displayName", "Invalid owner channel",
                "externalAccountId", "invalid-owner-bot",
                "teamId", TEAM_MANAGED.toString(),
                "defaultAssigneeUserId", USER_THREE.toString(),
                "secretRef", "POSE_CHANNEL_TELEGRAM_WEBHOOK_SECRET");
        jsonRequest(MockMvcRequestBuilders.post("/api/v1/channels/accounts"), admin, invalidOwner)
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM channel_accounts WHERE external_account_id='invalid-owner-bot'",
                Integer.class)).isZero();

        JsonNode created = response(jsonRequest(MockMvcRequestBuilders.post("/api/v1/channels/accounts"), admin,
                Map.of("channelType", "telegram", "displayName", "Telegram EDU demo",
                        "externalAccountId", "telegram-edu-demo", "teamId", TEAM_MANAGED.toString(),
                        "defaultAssigneeUserId", USER_ONE.toString(),
                        "secretRef", "POSE_CHANNEL_TELEGRAM_WEBHOOK_SECRET")), 201);
        assertThat(created.path("channelType").asText()).isEqualTo("TELEGRAM");
        assertThat(created.path("teamId").asText()).isEqualTo(TEAM_MANAGED.toString());
        assertThat(created.path("defaultAssigneeUserId").asText()).isEqualTo(USER_ONE.toString());
        assertThat(created.path("secretRef").asText()).isEqualTo("POSE_CHANNEL_TELEGRAM_WEBHOOK_SECRET");

        JsonNode accounts = response(mvc.perform(MockMvcRequestBuilders.get("/api/v1/channels/accounts")
                .header("Authorization", bearer(admin))), 200);
        assertThat(accounts.isArray()).isTrue();
        assertThat(accounts.toString()).contains("telegram-edu-demo", "POSE_CHANNEL_TELEGRAM_WEBHOOK_SECRET")
                .doesNotContain("telegram-test-secret");

        ObjectNode patch = mapper.createObjectNode().put("status", "INACTIVE");
        patch.putNull("defaultAssigneeUserId");
        JsonNode updated = response(jsonRequest(MockMvcRequestBuilders.patch(
                "/api/v1/channels/accounts/" + created.path("id").asText()), admin, patch), 200);
        assertThat(updated.path("status").asText()).isEqualTo("INACTIVE");
        assertThat(updated.path("defaultAssigneeUserId").isNull()).isTrue();
    }

    private void error(org.springframework.test.web.servlet.ResultActions result, int expectedStatus, String code)
            throws Exception {
        result.andExpect(status().is(expectedStatus)).andExpect(jsonPath("$.code").value(code));
    }
}
