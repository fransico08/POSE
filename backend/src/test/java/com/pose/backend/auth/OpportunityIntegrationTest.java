package com.pose.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties = "pose.sales-assignment.expiry-scan-delay-ms=86400000")
class OpportunityIntegrationTest extends FoundationTestSupport {
    private static final UUID CATEGORY = id(601), OFFERING_A = id(602), OFFERING_B = id(603), OFFERING_UNASSIGNED = id(604);
    private static final UUID SALES = id(605), SECOND_SALES = id(606), CUSTOMER = id(607);
    private static final UUID ASSIGNMENT_A = id(608), ASSIGNMENT_B = id(609);

    @BeforeEach
    void opportunityFixtures() {
        for (String code : List.of("opportunity.read", "opportunity.write", "opportunity.reopen", "customer.read",
                "customer.write", "offering.read")) {
            if (jdbc.queryForObject("SELECT count(*) FROM permissions WHERE code=?", Integer.class, code) == 0) permission(code);
        }
        user(SALES, ORG_ONE, "Opportunity Sales", "opportunity-sale@example.test", hashOne);
        user(SECOND_SALES, ORG_ONE, "Second Sales", "opportunity-sale-2@example.test", hashOne);
        userRole(ORG_ONE, SALES, "SALES_REP");
        userRole(ORG_ONE, SECOND_SALES, "SALES_REP");
        for (String permission : List.of("opportunity.read", "opportunity.write", "customer.read", "customer.write", "offering.read")) {
            grantOnce("SALES_REP", permission);
            grantOnce("MANAGER", permission);
        }
        grantOnce("MANAGER", "opportunity.reopen");
        membership(id(610), TEAM_MANAGED, SALES, START.minusSeconds(1), null, "ACTIVE");
        jdbc.update("INSERT INTO offering_categories(id,organization_id,code,name,status,created_at,updated_at) VALUES (?,?,'CRM','CRM','ACTIVE',?,?)",
                CATEGORY, ORG_ONE, time(), time());
        offering(OFFERING_A, "OFFER-A", "Active Offering A");
        offering(OFFERING_B, "OFFER-B", "Active Offering B");
        offering(OFFERING_UNASSIGNED, "OFFER-X", "Unassigned Offering");
        assignment(ASSIGNMENT_A, OFFERING_A, SALES);
        assignment(ASSIGNMENT_B, OFFERING_B, SALES);
        jdbc.update("""
                INSERT INTO customers(id,organization_id,owner_user_id,display_name,email,normalized_email,status,created_at,updated_at)
                VALUES (?,?,?,'Opportunity Customer','opportunity-customer@example.test','opportunity-customer@example.test','ACTIVE',?,?)
                """, CUSTOMER, ORG_ONE, SALES, time(), time());
    }

    private void grantOnce(String role, String permission) {
        int count = jdbc.queryForObject("""
                SELECT count(*) FROM role_permissions rp JOIN roles r ON r.id=rp.role_id
                JOIN permissions p ON p.id=rp.permission_id WHERE r.organization_id=? AND r.code=? AND p.code=?
                """, Integer.class, ORG_ONE, role, permission);
        if (count == 0) grant(ORG_ONE, role, permission);
    }

    private void membership(UUID id, UUID team, UUID user, Instant from, Instant to, String status) {
        jdbc.update("""
                INSERT INTO team_memberships(id,organization_id,team_id,user_id,joined_at,left_at,status)
                VALUES (?,?,?,?,?,?,?)
                """, id, ORG_ONE, team, user, Timestamp.from(from), to == null ? null : Timestamp.from(to), status);
    }

    private void offering(UUID id, String code, String name) {
        jdbc.update("""
                INSERT INTO offerings(id,organization_id,category_id,team_id,code,name,kind,status,created_at,updated_at)
                VALUES (?,?,?,?,?,?,'SERVICE','ACTIVE',?,?)
                """, id, ORG_ONE, CATEGORY, TEAM_MANAGED, code, name, time(), time());
    }

    private void assignment(UUID id, UUID offering, UUID sales) {
        jdbc.update("""
                INSERT INTO sales_assignments(id,organization_id,offering_id,sales_user_id,assigned_by_user_id,status,
                    effective_from,effective_to,created_at,updated_at)
                VALUES (?,?,?,?,?,'ACTIVE',?,NULL,?,?)
                """, id, ORG_ONE, offering, sales, USER_ONE, time(), time(), time());
    }

    private JsonNode loginSales() throws Exception {
        return response(login("one", "opportunity-sale@example.test", PASSWORD_ONE), 200);
    }

    private Map<String, Object> createBody() {
        return Map.of("title", "W05 multi-line opportunity", "customerId", CUSTOMER.toString(),
                "expectedCloseDate", "2026-10-21", "notes", "W05 acceptance",
                "lines", List.of(
                        Map.of("offeringId", OFFERING_A.toString(), "quantity", 2, "unitPrice", 100.00, "discountPercent", 10),
                        Map.of("offeringId", OFFERING_B.toString(), "quantity", 1, "unitPrice", 500.00, "discountPercent", 0)));
    }

    @Test
    void salesCreatesManyOfferingLinesManagerReadsAndWonCanBeReopened() throws Exception {
        JsonNode sales = loginSales();
        JsonNode created = response(jsonRequest(post("/api/v1/opportunities"), sales, createBody()), 201);
        assertThat(created.get("stage").asText()).isEqualTo("NEW");
        assertThat(created.get("ownerUserId").asText()).isEqualTo(SALES.toString());
        assertThat(created.get("expectedValue").decimalValue()).isEqualByComparingTo("680.00");
        assertThat(created.get("currencyCode").asText()).isEqualTo("VND");
        assertThat(created.get("lines").size()).isEqualTo(2);
        assertThat(created.get("lines").get(0).get("lineValue").decimalValue()).isEqualByComparingTo("180.00");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action='OPPORTUNITY_CREATED'", Integer.class)).isEqualTo(1);

        JsonNode manager = loginOne();
        JsonNode page = response(mvc.perform(get("/api/v1/opportunities").header("Authorization", bearer(manager))), 200);
        assertThat(page.get("totalItems").asLong()).isEqualTo(1);
        assertThat(page.get("items").get(0).get("id").asText()).isEqualTo(created.get("id").asText());

        JsonNode qualified = patchStage(created, sales, "QUALIFIED", Map.of());
        JsonNode proposal = patchStage(qualified, sales, "PROPOSAL", Map.of());
        JsonNode won = patchStage(proposal, sales, "WON", Map.of(
                "closingReason", "Khách hàng ký hợp đồng", "actualValue", 650.50,
                "actualCloseDate", "2026-10-01"));
        assertThat(won.get("actualValue").decimalValue()).isEqualByComparingTo("650.50");
        assertThat(won.get("expectedValue").decimalValue()).isEqualByComparingTo("680.00");
        assertThat(won.get("actualCloseDate").asText()).isEqualTo("2026-10-01");
        jsonRequest(patch("/api/v1/opportunities/" + won.get("id").asText()), sales,
                Map.of("expectedUpdatedAt", won.get("updatedAt").asText(), "title", "Locked edit"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OPPORTUNITY_LOCKED"));

        JsonNode reopened = response(jsonRequest(post("/api/v1/opportunities/" + won.get("id").asText() + "/reopen"), manager,
                Map.of("expectedUpdatedAt", won.get("updatedAt").asText())), 200);
        assertThat(reopened.get("stage").asText()).isEqualTo("QUALIFIED");
        assertThat(reopened.get("actualValue").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action='OPPORTUNITY_REOPENED'", Integer.class)).isEqualTo(1);
    }

    @Test
    void rejectsInvalidLinesPastDatesAndOfferingsOutsideSalesAssignment() throws Exception {
        JsonNode sales = loginSales();
        Map<String, Object> invalidDiscount = new LinkedHashMap<>(createBody());
        List<Map<String, Object>> lines = new ArrayList<>((List<Map<String, Object>>) invalidDiscount.get("lines"));
        lines.set(0, new LinkedHashMap<>(lines.get(0)));
        lines.get(0).put("discountPercent", 101);
        invalidDiscount.put("lines", lines);
        jsonRequest(post("/api/v1/opportunities"), sales, invalidDiscount).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("lines[0].discountPercent"));

        Map<String, Object> pastDate = new LinkedHashMap<>(createBody());
        pastDate.put("expectedCloseDate", "2026-09-30");
        jsonRequest(post("/api/v1/opportunities"), sales, pastDate).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("expectedCloseDate"));

        Map<String, Object> unassigned = new LinkedHashMap<>(createBody());
        unassigned.put("lines", List.of(Map.of("offeringId", OFFERING_UNASSIGNED.toString(), "quantity", 1,
                "unitPrice", 100, "discountPercent", 0)));
        jsonRequest(post("/api/v1/opportunities"), sales, unassigned).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_SCOPE"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM opportunities", Integer.class)).isZero();
    }

    @Test
    void managerCanCreateForAssignedSalesAndOtherSalesCannotReadIt() throws Exception {
        JsonNode manager = loginOne();
        Map<String, Object> body = new LinkedHashMap<>(createBody());
        body.put("ownerUserId", SALES.toString());
        JsonNode created = response(jsonRequest(post("/api/v1/opportunities"), manager, body), 201);

        JsonNode secondSales = response(login("one", "opportunity-sale-2@example.test", PASSWORD_ONE), 200);
        jsonRequest(get("/api/v1/opportunities/" + created.get("id").asText()), secondSales, Map.of())
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN_SCOPE"));
    }

    private JsonNode patchStage(JsonNode record, JsonNode actor, String stage, Map<String, Object> extra) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("expectedUpdatedAt", record.get("updatedAt").asText());
        body.put("stage", stage);
        body.putAll(extra);
        return response(jsonRequest(patch("/api/v1/opportunities/" + record.get("id").asText()), actor, body), 200);
    }
}
