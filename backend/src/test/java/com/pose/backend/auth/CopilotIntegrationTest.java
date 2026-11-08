package com.pose.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.ai.AiProvider;
import com.pose.backend.ai.MockProvider;
import com.pose.backend.customer.CustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties = {
        "pose.ai.enabled=true",
        "pose.ai.provider=mock",
        "pose.ai.copilot.reuse-minutes=10",
        "pose.sales-assignment.expiry-scan-delay-ms=86400000"
})
class CopilotIntegrationTest extends FoundationTestSupport {
    private static final UUID SALE_ONE = id(2201);
    private static final UUID SALE_TWO = id(2202);
    private static final UUID CARE_ASSIGNED = id(2203);
    private static final UUID CARE_UNASSIGNED = id(2204);
    private static final UUID DATA_USER = id(2205);
    private static final UUID CATEGORY = id(2210);
    private static final UUID OFFERING = id(2211);
    private static final UUID CUSTOMER_ONE = id(2220);
    private static final UUID CUSTOMER_OTHER = id(2221);
    private static final UUID OPP_ONE = id(2230);
    private static final UUID INT_CARE_VISIBLE = id(2240);
    private static final UUID INT_SALES_ONLY = id(2241);
    private static final UUID TASK_ONE = id(2250);

    @Autowired MockProvider mock;
    @Autowired CustomerService customerService;

    @BeforeEach
    void setupCopilotFixtures() {
        mock.setNextReply(null);
        for (String p : List.of("ai.assist.use", "ai.metrics.read", "customer_360.read", "customer.write",
                "opportunity.read", "interaction.read", "task.manage", "lead.read", "offering.read", "offering.manage")) {
            permission(p);
        }
        role(ORG_ONE, "ADMIN");
        role(ORG_ONE, "DATA_STAFF");
        for (String r : List.of("ADMIN", "MANAGER", "SALES_REP", "CUSTOMER_CARE")) {
            grant(ORG_ONE, r, "ai.assist.use");
            grant(ORG_ONE, r, "customer_360.read");
            grant(ORG_ONE, r, "interaction.read");
            grant(ORG_ONE, r, "task.manage");
        }
        for (String r : List.of("ADMIN", "MANAGER", "SALES_REP")) {
            grant(ORG_ONE, r, "customer.write");
            grant(ORG_ONE, r, "opportunity.read");
            grant(ORG_ONE, r, "lead.read");
        }
        grant(ORG_ONE, "SALES_REP", "offering.read");
        grant(ORG_ONE, "MANAGER", "offering.manage");
        grant(ORG_ONE, "DATA_STAFF", "customer_360.read");
        grant(ORG_ONE, "DATA_STAFF", "ai.metrics.read");

        user(SALE_ONE, ORG_ONE, "Sale One", "copilot-sale1@example.test", hashOne);
        user(SALE_TWO, ORG_ONE, "Sale Two", "copilot-sale2@example.test", hashOne);
        user(CARE_ASSIGNED, ORG_ONE, "Care Assigned", "copilot-care1@example.test", hashOne);
        user(CARE_UNASSIGNED, ORG_ONE, "Care Unassigned", "copilot-care2@example.test", hashOne);
        user(DATA_USER, ORG_ONE, "Data Staff", "copilot-data@example.test", hashOne);

        userRole(ORG_ONE, SALE_ONE, "SALES_REP");
        userRole(ORG_ONE, SALE_TWO, "SALES_REP");
        userRole(ORG_ONE, CARE_ASSIGNED, "CUSTOMER_CARE");
        userRole(ORG_ONE, CARE_UNASSIGNED, "CUSTOMER_CARE");
        userRole(ORG_ONE, DATA_USER, "DATA_STAFF");

        jdbc.update("INSERT INTO team_memberships(id,organization_id,team_id,user_id,joined_at,status) VALUES (?,?,?,?,?,'ACTIVE')",
                id(2260), ORG_ONE, TEAM_MANAGED, SALE_ONE, time());
        jdbc.update("INSERT INTO team_memberships(id,organization_id,team_id,user_id,joined_at,status) VALUES (?,?,?,?,?,'ACTIVE')",
                id(2261), ORG_ONE, TEAM_MANAGED, SALE_TWO, time());

        jdbc.update("INSERT INTO offering_categories(id,organization_id,code,name,status,created_at,updated_at) VALUES (?,?,'CAT-C','Category C','ACTIVE',?,?)",
                CATEGORY, ORG_ONE, time(), time());
        jdbc.update("INSERT INTO offerings(id,organization_id,category_id,team_id,code,name,kind,status,created_at,updated_at) VALUES (?,?,?,?,'OFF-C','Copilot Offering','SERVICE','ACTIVE',?,?)",
                OFFERING, ORG_ONE, CATEGORY, TEAM_MANAGED, time(), time());

        jdbc.update("INSERT INTO sales_assignments(id,organization_id,offering_id,sales_user_id,assigned_by_user_id,status,effective_from,created_at,updated_at) VALUES (?,?,?,?,?,'ACTIVE',?,?,?)",
                id(2262), ORG_ONE, OFFERING, SALE_ONE, USER_ONE, time(), time(), time());
        jdbc.update("INSERT INTO sales_assignments(id,organization_id,offering_id,sales_user_id,assigned_by_user_id,status,effective_from,created_at,updated_at) VALUES (?,?,?,?,?,'ACTIVE',?,?,?)",
                id(2263), ORG_ONE, OFFERING, SALE_TWO, USER_ONE, time(), time(), time());

        jdbc.update("""
                INSERT INTO customers(id,organization_id,owner_user_id,display_name,email,normalized_email,phone,normalized_phone,status,customer_type,related_offering_id,created_at,updated_at)
                VALUES (?,?,?,'Công ty Ánh Dương','anhduong@example.test','anhduong@example.test','0901 222 333','+84901222333','ACTIVE','BUSINESS',?,?,?)
                """, CUSTOMER_ONE, ORG_ONE, SALE_ONE, OFFERING, time(), time());
        jdbc.update("INSERT INTO customer_assignments(id,organization_id,customer_id,user_id,assignment_type,effective_from,status,assigned_by_user_id,source,created_at) VALUES (?,?,?,?,'SALES',?,'ACTIVE',?,'INITIAL',?)",
                id(2264), ORG_ONE, CUSTOMER_ONE, SALE_ONE, time(), USER_ONE, time());
        jdbc.update("INSERT INTO customer_assignments(id,organization_id,customer_id,user_id,assignment_type,effective_from,status,assigned_by_user_id,source,created_at) VALUES (?,?,?,?,'CARE',?,'ACTIVE',?,'INITIAL',?)",
                id(2265), ORG_ONE, CUSTOMER_ONE, CARE_ASSIGNED, time(), USER_ONE, time());

        jdbc.update("""
                INSERT INTO customers(id,organization_id,owner_user_id,display_name,email,normalized_email,status,customer_type,created_at,updated_at)
                VALUES (?,?,?,'Khách ngoài phạm vi','other-c@example.test','other-c@example.test','ACTIVE','INDIVIDUAL',?,?)
                """, CUSTOMER_OTHER, ORG_ONE, USER_THREE, time(), time());

        jdbc.update("""
                INSERT INTO opportunities(id,organization_id,customer_id,offering_id,owner_user_id,title,stage,expected_value,currency_code,created_at,updated_at)
                VALUES (?,?,?,?,?,'Gói đào tạo doanh nghiệp','QUALIFIED',50000000,'VND',?,?)
                """, OPP_ONE, ORG_ONE, CUSTOMER_ONE, OFFERING, SALE_ONE, time(), time());

        jdbc.update("""
                INSERT INTO interactions(id,organization_id,customer_id,recorded_by_user_id,type,channel,subject,content,visibility,occurred_at,created_at,updated_at)
                VALUES (?,?,?,?,'CALL','PHONE','Gọi tư vấn lộ trình','Khách quan tâm lộ trình học cho 20 nhân sự','CARE_VISIBLE',?,?,?)
                """, INT_CARE_VISIBLE, ORG_ONE, CUSTOMER_ONE, SALE_ONE, time(), time(), time());
        jdbc.update("""
                INSERT INTO interactions(id,organization_id,customer_id,recorded_by_user_id,type,channel,subject,content,visibility,occurred_at,created_at,updated_at)
                VALUES (?,?,?,?,'NOTE','INTERNAL','Đàm phán chiết khấu bí mật','Chiết khấu nội bộ tối đa 12 phần trăm','SALES_ONLY',?,?,?)
                """, INT_SALES_ONLY, ORG_ONE, CUSTOMER_ONE, SALE_ONE, time(), time(), time());

        jdbc.update("""
                INSERT INTO crm_tasks(id,organization_id,customer_id,owner_user_id,created_by_user_id,title,priority,status,due_at,origin,created_at,updated_at)
                VALUES (?,?,?,?,?,'Gửi báo giá chi tiết','HIGH','OPEN',?,'MANUAL',?,?)
                """, TASK_ONE, ORG_ONE, CUSTOMER_ONE, SALE_ONE, SALE_ONE, Timestamp.from(START.plusSeconds(86400)), time(), time());
    }

    private JsonNode loginAs(String email) throws Exception {
        return response(login("one", email, PASSWORD_ONE), 200);
    }

    @Test
    void summaryGeneratesStructuredCitationsRedactsContactsAndReusesWithinWindow() throws Exception {
        JsonNode saleTokens = loginAs("copilot-sale1@example.test");

        JsonNode first = response(jsonRequest(
                post("/api/v1/ai/copilot/customers/" + CUSTOMER_ONE + "/summary"),
                saleTokens,
                Map.of()
        ), 200);

        assertThat(first.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(first.path("reused").asBoolean()).isFalse();
        assertThat(mock.lastInput()).contains("[EMAIL_1]", "[PHONE_1]", "PROFILE",
                        "OPPORTUNITY:" + OPP_ONE, "INTERACTION:" + INT_CARE_VISIBLE, "TASK:" + TASK_ONE)
                .doesNotContain("anhduong@example.test", "0901 222 333");

        JsonNode suggestion = first.path("suggestion");
        assertThat(suggestion.path("kind").asText()).isEqualTo("C360_SUMMARY");
        assertThat(suggestion.path("status").asText()).isEqualTo("PROPOSED");
        JsonNode content = mapper.readTree(suggestion.path("content").asText());
        assertThat(content.path("sections").isArray()).isTrue();
        assertThat(content.path("sections").size()).isGreaterThanOrEqualTo(2);

        JsonNode evidence = mapper.readTree(suggestion.path("evidence").asText());
        assertThat(strings(evidence.path("sources")))
                .contains("PROFILE", "OPPORTUNITY:" + OPP_ONE, "INTERACTION:" + INT_CARE_VISIBLE, "TASK:" + TASK_ONE);
        assertThat(evidence.path("droppedPoints").asInt()).isZero();

        long invCountBeforeReuse = jdbc.queryForObject("SELECT count(*) FROM ai_invocations", Long.class);
        JsonNode reused = response(jsonRequest(
                post("/api/v1/ai/copilot/customers/" + CUSTOMER_ONE + "/summary"),
                saleTokens,
                Map.of("force", false)
        ), 200);
        assertThat(reused.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(reused.path("reused").asBoolean()).isTrue();
        assertThat(reused.path("suggestion").path("id").asText()).isEqualTo(suggestion.path("id").asText());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_invocations", Long.class)).isEqualTo(invCountBeforeReuse);

        JsonNode forced = response(jsonRequest(
                post("/api/v1/ai/copilot/customers/" + CUSTOMER_ONE + "/summary"),
                saleTokens,
                Map.of("force", true)
        ), 200);
        assertThat(forced.path("reused").asBoolean()).isFalse();
        assertThat(forced.path("suggestion").path("id").asText()).isNotEqualTo(suggestion.path("id").asText());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_invocations", Long.class)).isEqualTo(invCountBeforeReuse + 1);

        clock.now = START.plus(Duration.ofMinutes(11));
        JsonNode afterExpiry = response(jsonRequest(
                post("/api/v1/ai/copilot/customers/" + CUSTOMER_ONE + "/summary"),
                saleTokens,
                Map.of()
        ), 200);
        assertThat(afterExpiry.path("reused").asBoolean()).isFalse();
    }

    @Test
    void handoverBriefUsesLatestApprovedHandoverOrPreparesWhenNoneExists() throws Exception {
        JsonNode managerTokens = loginOne();

        JsonNode prepareBrief = response(jsonRequest(
                post("/api/v1/ai/copilot/customers/" + CUSTOMER_ONE + "/handover-brief"),
                managerTokens,
                Map.of("force", true)
        ), 200);
        assertThat(prepareBrief.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(mock.lastInput()).contains("PREPARE_HANDOVER");

        JsonNode reassignRes = response(jsonRequest(
                post("/api/v1/customers/" + CUSTOMER_ONE + "/reassign"),
                managerTokens,
                Map.of("newOwnerUserId", SALE_TWO, "reason", "Chuyển giao khách hàng chiến lược quý 4")
        ), 200);
        String handoverId = reassignRes.path("handoverId").asText();

        JsonNode approvedBrief = response(jsonRequest(
                post("/api/v1/ai/copilot/customers/" + CUSTOMER_ONE + "/handover-brief"),
                managerTokens,
                Map.of("force", true)
        ), 200);
        assertThat(approvedBrief.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(mock.lastInput()).contains("APPROVED_HANDOVER", "HANDOVER:" + handoverId, "Chuyển giao khách hàng chiến lược quý 4");
        JsonNode evidence = mapper.readTree(approvedBrief.path("suggestion").path("evidence").asText());
        assertThat(strings(evidence.path("sources"))).contains("HANDOVER:" + handoverId, "PROFILE");
    }

    @Test
    void filtersUnknownSourceCitationsAndFailsWhenOutputIsInvalidOrAllPointsDropped() throws Exception {
        JsonNode saleTokens = loginAs("copilot-sale1@example.test");

        String mixedOutput = """
                {"sections":[{"heading":"Tổng hợp","points":[
                  {"text":"Ý hợp lệ từ hồ sơ","sources":["PROFILE","INTERACTION:unknown-id"]},
                  {"text":"Ý bịa nguồn hoàn toàn","sources":["INTERACTION:unknown-id"]}
                ]}]}
                """;
        mock.setNextReply(new AiProvider.Reply(mixedOutput, 50L, 40L, null));

        JsonNode filteredRes = response(jsonRequest(
                post("/api/v1/ai/copilot/customers/" + CUSTOMER_ONE + "/summary"),
                saleTokens,
                Map.of("force", true)
        ), 200);
        assertThat(filteredRes.path("status").asText()).isEqualTo("SUCCEEDED");
        JsonNode content = mapper.readTree(filteredRes.path("suggestion").path("content").asText());
        assertThat(content.path("sections").get(0).path("points")).hasSize(1);
        assertThat(strings(content.path("sections").get(0).path("points").get(0).path("sources")))
                .containsExactly("PROFILE");
        JsonNode evidence = mapper.readTree(filteredRes.path("suggestion").path("evidence").asText());
        assertThat(evidence.path("droppedPoints").asInt()).isEqualTo(1);

        long suggestionsBefore = jdbc.queryForObject("SELECT count(*) FROM ai_suggestions", Long.class);
        mock.setNextReply(new AiProvider.Reply("not-a-json-object", 30L, 10L, null));
        JsonNode malformedRes = response(jsonRequest(
                post("/api/v1/ai/copilot/customers/" + CUSTOMER_ONE + "/summary"),
                saleTokens,
                Map.of("force", true)
        ), 200);
        assertThat(malformedRes.path("status").asText()).isEqualTo("FAILED");
        assertThat(malformedRes.path("errorCode").asText()).isEqualTo("INVALID_OUTPUT");
        assertThat(malformedRes.path("suggestion").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_suggestions", Long.class)).isEqualTo(suggestionsBefore);

        String allUnknownSources = """
                {"sections":[{"heading":"Bịa hoàn toàn","points":[
                  {"text":"Ý không có nguồn thật","sources":["TASK:non-existent"]}
                ]}]}
                """;
        mock.setNextReply(new AiProvider.Reply(allUnknownSources, 30L, 20L, null));
        JsonNode emptyAfterFilterRes = response(jsonRequest(
                post("/api/v1/ai/copilot/customers/" + CUSTOMER_ONE + "/summary"),
                saleTokens,
                Map.of("force", true)
        ), 200);
        assertThat(emptyAfterFilterRes.path("status").asText()).isEqualTo("FAILED");
        assertThat(emptyAfterFilterRes.path("errorCode").asText()).isEqualTo("INVALID_OUTPUT");
        assertThat(emptyAfterFilterRes.path("suggestion").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_suggestions", Long.class)).isEqualTo(suggestionsBefore);
    }

    @Test
    void enforcesRoleAndScopeAndProtectsSalesOnlyContentFromCare() throws Exception {
        JsonNode careAssignedTokens = loginAs("copilot-care1@example.test");
        JsonNode careRes = response(jsonRequest(
                post("/api/v1/ai/copilot/customers/" + CUSTOMER_ONE + "/summary"),
                careAssignedTokens,
                Map.of("force", true)
        ), 200);
        assertThat(careRes.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(mock.lastInput())
                .contains("Gọi tư vấn lộ trình")
                .doesNotContain("Đàm phán chiết khấu bí mật", "Chiết khấu nội bộ", "Gói đào tạo doanh nghiệp");

        String lastInputBeforeRejections = mock.lastInput();
        long invocationsBefore = jdbc.queryForObject("SELECT count(*) FROM ai_invocations", Long.class);

        JsonNode saleOneTokens = loginAs("copilot-sale1@example.test");
        jsonRequest(post("/api/v1/ai/copilot/customers/" + CUSTOMER_OTHER + "/summary"), saleOneTokens, Map.of())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_SCOPE"));

        JsonNode careUnassignedTokens = loginAs("copilot-care2@example.test");
        jsonRequest(post("/api/v1/ai/copilot/customers/" + CUSTOMER_ONE + "/summary"), careUnassignedTokens, Map.of())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_SCOPE"));

        JsonNode dataTokens = loginAs("copilot-data@example.test");
        jsonRequest(post("/api/v1/ai/copilot/customers/" + CUSTOMER_ONE + "/summary"), dataTokens, Map.of())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_PERMISSION"));

        JsonNode foreignTokens = response(login("two", "shared@example.com", PASSWORD_TWO), 200);
        jsonRequest(post("/api/v1/ai/copilot/customers/" + CUSTOMER_ONE + "/summary"), foreignTokens, Map.of())
                .andExpect(status().isForbidden());

        assertThat(mock.lastInput()).isEqualTo(lastInputBeforeRejections);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_invocations", Long.class)).isEqualTo(invocationsBefore);
    }
}

