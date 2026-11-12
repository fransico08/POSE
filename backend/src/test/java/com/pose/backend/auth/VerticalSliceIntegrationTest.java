package com.pose.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@TestPropertySource(properties = "pose.sales-assignment.expiry-scan-delay-ms=86400000")
class VerticalSliceIntegrationTest extends FoundationTestSupport {
    private static final UUID CATEGORY = id(1101);
    private static final UUID OFFERING = id(1102);
    private static final UUID SALES = id(1103);

    @BeforeEach
    void fixtures() {
        for (String code : List.of(
                "sales_assignment.manage", "offering.read", "customer.read", "customer.write",
                "lead.read", "lead.write", "interaction.read", "interaction.write",
                "task.manage", "customer_360.read")) {
            if (jdbc.queryForObject("SELECT count(*) FROM permissions WHERE code=?", Integer.class, code) == 0) {
                permission(code);
            }
        }

        grantOnce("MANAGER", "sales_assignment.manage");
        for (String code : List.of("customer.read", "customer.write", "lead.read", "lead.write",
                "interaction.read", "interaction.write", "task.manage", "customer_360.read")) {
            grantOnce("MANAGER", code);
            grantOnce("SALES_REP", code);
        }
        grantOnce("SALES_REP", "offering.read");

        user(SALES, ORG_ONE, "W11 Sales", "w11-sales@example.test", hashOne);
        userRole(ORG_ONE, SALES, "SALES_REP");
        jdbc.update("INSERT INTO team_memberships(id,organization_id,team_id,user_id,joined_at,status) VALUES (?,?,?,?,?,'ACTIVE')",
                id(1104), ORG_ONE, TEAM_MANAGED, SALES, time());
        jdbc.update("INSERT INTO offering_categories(id,organization_id,code,name,status,created_at,updated_at) VALUES (?,?,'W11','W11','ACTIVE',?,?)",
                CATEGORY, ORG_ONE, time(), time());
        jdbc.update("INSERT INTO offerings(id,organization_id,category_id,team_id,code,name,kind,status,created_at,updated_at) VALUES (?,?,?,?,'W11-VERTICAL','W11 Vertical Offering','SERVICE','ACTIVE',?,?)",
                OFFERING, ORG_ONE, CATEGORY, TEAM_MANAGED, time(), time());
    }

    @Test
    void managerAssignmentFlowsThroughLeadConversionWorkAndCustomer360() throws Exception {
        JsonNode manager = loginOne();
        Map<String, Object> assignmentRequest = new LinkedHashMap<>();
        assignmentRequest.put("offeringId", OFFERING);
        assignmentRequest.put("salesUserId", SALES);
        assignmentRequest.put("effectiveFrom", START.minusSeconds(1).toString());
        assignmentRequest.put("effectiveTo", null);

        JsonNode assignment = response(jsonRequest(post("/api/v1/sales-assignments"), manager, assignmentRequest), 201);
        assertThat(assignment.path("effectiveState").asText()).isEqualTo("CURRENT");

        JsonNode sales = response(login("one", "w11-sales@example.test", PASSWORD_ONE), 200);
        JsonNode assignedOfferings = response(mvc.perform(get("/api/v1/offerings")
                .header("Authorization", bearer(sales))), 200);
        assertThat(assignedOfferings.path("items").toString()).contains(OFFERING.toString());

        Map<String, Object> leadRequest = new LinkedHashMap<>();
        leadRequest.put("displayName", "W11 Vertical Slice Customer");
        leadRequest.put("email", "w11-vertical-slice@example.test");
        leadRequest.put("source", "WEBSITE");
        leadRequest.put("offeringId", OFFERING);
        JsonNode lead = response(jsonRequest(post("/api/v1/leads"), sales, leadRequest), 201);
        assertThat(lead.path("ownerUserId").asText()).isEqualTo(SALES.toString());
        assertThat(lead.path("offeringId").asText()).isEqualTo(OFFERING.toString());

        JsonNode qualifiedLead = response(jsonRequest(post("/api/v1/leads/" + lead.path("id").asText() + "/qualify"),
                sales, Map.of("expectedUpdatedAt", lead.path("updatedAt").asText())), 200);
        assertThat(qualifiedLead.path("status").asText()).isEqualTo("QUALIFIED");

        Map<String, Object> conversionRequest = new LinkedHashMap<>();
        conversionRequest.put("expectedUpdatedAt", qualifiedLead.path("updatedAt").asText());
        conversionRequest.put("customerType", "INDIVIDUAL");
        JsonNode conversion = response(jsonRequest(post("/api/v1/leads/" + lead.path("id").asText() + "/convert"),
                sales, conversionRequest), 200);
        assertThat(conversion.path("customerCreated").asBoolean()).isTrue();
        assertThat(conversion.at("/lead/status").asText()).isEqualTo("CONVERTED");
        UUID customerId = UUID.fromString(conversion.at("/customer/id").asText());
        assertThat(conversion.at("/customer/ownerUserId").asText()).isEqualTo(SALES.toString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_assignments WHERE organization_id=? AND customer_id=? AND user_id=? AND assignment_type='SALES' AND source='INITIAL' AND status='ACTIVE'",
                Integer.class, ORG_ONE, customerId, SALES)).isEqualTo(1);

        Map<String, Object> interactionRequest = new LinkedHashMap<>();
        interactionRequest.put("customerId", customerId);
        interactionRequest.put("type", "CALL");
        interactionRequest.put("channel", "PHONE");
        interactionRequest.put("subject", "W11 vertical slice call");
        interactionRequest.put("content", "Customer requested a follow-up.");
        interactionRequest.put("visibility", "CARE_VISIBLE");
        interactionRequest.put("occurredAt", START.toString());
        JsonNode interaction = response(jsonRequest(post("/api/v1/interactions"), sales, interactionRequest), 201);
        assertThat(interaction.path("recordedByUserId").asText()).isEqualTo(SALES.toString());

        Map<String, Object> taskRequest = new LinkedHashMap<>();
        taskRequest.put("customerId", customerId);
        taskRequest.put("ownerUserId", SALES);
        taskRequest.put("title", "W11 vertical slice follow-up");
        taskRequest.put("description", "Contact the customer after the consultation.");
        taskRequest.put("priority", "MEDIUM");
        taskRequest.put("dueAt", START.plusSeconds(3600).toString());
        JsonNode task = response(jsonRequest(post("/api/v1/tasks"), sales, taskRequest), 201);
        assertThat(task.path("status").asText()).isEqualTo("OPEN");
        assertThat(task.path("origin").asText()).isEqualTo("MANUAL");
        assertThat(task.path("createdByUserId").asText()).isEqualTo(SALES.toString());

        JsonNode sales360 = profile(sales, customerId);
        assertThat(sales360.at("/customer/profile/id").asText()).isEqualTo(customerId.toString());
        assertThat(sales360.at("/leads/count").asInt()).isEqualTo(1);
        assertThat(sales360.at("/leads/items/0/id").asText()).isEqualTo(lead.path("id").asText());
        assertThat(sales360.at("/leads/items/0/status").asText()).isEqualTo("CONVERTED");
        assertThat(sales360.at("/interactions/count").asInt()).isEqualTo(1);
        assertThat(sales360.at("/interactions/items/0/id").asText()).isEqualTo(interaction.path("id").asText());
        assertThat(sales360.at("/tasks/statuses/OPEN").asInt()).isEqualTo(1);
        assertThat(sales360.at("/tasks/items/0/id").asText()).isEqualTo(task.path("id").asText());

        JsonNode manager360 = profile(manager, customerId);
        assertThat(manager360.at("/leads/count").asInt()).isEqualTo(1);
        assertThat(manager360.at("/interactions/count").asInt()).isEqualTo(1);
        assertThat(manager360.at("/tasks/statuses/OPEN").asInt()).isEqualTo(1);

        assertAudit("SALES_ASSIGNMENT_CREATED", "SALES_ASSIGNMENT", UUID.fromString(assignment.path("id").asText()));
        assertAudit("LEAD_CREATED", "LEAD", UUID.fromString(lead.path("id").asText()));
        assertAudit("LEAD_QUALIFIED", "LEAD", UUID.fromString(lead.path("id").asText()));
        assertAudit("CUSTOMER_CREATED", "CUSTOMER", customerId);
        assertAudit("LEAD_CONVERTED", "LEAD", UUID.fromString(lead.path("id").asText()));
        assertAudit("INTERACTION_CREATED", "INTERACTION", UUID.fromString(interaction.path("id").asText()));
        assertAudit("TASK_CREATED", "CRM_TASK", UUID.fromString(task.path("id").asText()));
    }

    private JsonNode profile(JsonNode actor, UUID customerId) throws Exception {
        return response(mvc.perform(get("/api/v1/customers/" + customerId + "/360")
                .header("Authorization", bearer(actor))), 200);
    }

    private void assertAudit(String action, String entityType, UUID entityId) {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE organization_id=? AND action=? AND entity_type=? AND entity_id=?",
                Integer.class, ORG_ONE, action, entityType, entityId)).isEqualTo(1);
    }

    private void grantOnce(String role, String permissionCode) {
        if (jdbc.queryForObject("SELECT count(*) FROM role_permissions rp JOIN roles r ON r.id=rp.role_id JOIN permissions p ON p.id=rp.permission_id WHERE r.organization_id=? AND r.code=? AND p.code=?",
                Integer.class, ORG_ONE, role, permissionCode) == 0) {
            grant(ORG_ONE, role, permissionCode);
        }
    }
}
