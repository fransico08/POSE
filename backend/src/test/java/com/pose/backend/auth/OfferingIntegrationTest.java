package com.pose.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OfferingIntegrationTest extends FoundationTestSupport {
    private static final UUID CATEGORY_ONE = id(31);
    private static final UUID CATEGORY_TWO = id(32);
    private static final UUID TEAM_TWO = id(41);

    @BeforeEach
    void offeringFixtures() {
        permission("offering.manage");
        if (jdbc.queryForObject("SELECT count(*) FROM permissions WHERE code='offering.read'", Integer.class) == 0) {
            permission("offering.read");
        }
        grant(ORG_ONE, "MANAGER", "offering.manage");
        grant(ORG_TWO, "SALES_REP", "offering.read");
        category(CATEGORY_ONE, ORG_ONE, "SERVICE", "Services");
        category(CATEGORY_TWO, ORG_TWO, "PRODUCT", "Products");
        team(TEAM_TWO, ORG_TWO, "TWO", "Tenant Two Sales", USER_TWO);
    }

    @Test
    void managerCanCreateReadUpdateAndPreservePriceHistory() throws Exception {
        JsonNode manager = loginOne();
        JsonNode created = response(jsonRequest(post("/api/v1/offerings"), manager,
                createBody("CRM Setup", "SERVICE", CATEGORY_ONE, TEAM_MANAGED, "1250.50", true)), 201);
        UUID offeringId = UUID.fromString(created.get("id").asText());
        assertThat(created.get("active").asBoolean()).isTrue();
        assertThat(created.get("referencePrice").asText()).isEqualTo("1250.50");
        assertThat(created.get("currencyCode").asText()).isEqualTo("VND");
        assertThat(created.get("teamId").asText()).isEqualTo(TEAM_MANAGED.toString());

        JsonNode list = response(mvc.perform(get("/api/v1/offerings")
                .header("Authorization", bearer(manager)).param("search", "crm")), 200);
        assertThat(list.get("totalItems").asLong()).isEqualTo(1);
        assertThat(list.at("/items/0/id").asText()).isEqualTo(offeringId.toString());
        mvc.perform(get("/api/v1/offerings/" + offeringId).header("Authorization", bearer(manager)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.categoryName").value("Services"));

        clock.now = START.plusSeconds(60);
        Map<String, Object> update = updateBody(created, "CRM Setup Plus", "SERVICE", CATEGORY_ONE,
                TEAM_MANAGED, "1499.00", null, "month");
        JsonNode updated = response(jsonRequest(put("/api/v1/offerings/" + offeringId), manager, update), 200);
        assertThat(updated.get("name").asText()).isEqualTo("CRM Setup Plus");
        assertThat(updated.get("referencePrice").asText()).isEqualTo("1499.00");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offering_prices WHERE offering_id=?", Integer.class, offeringId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT amount FROM offering_prices WHERE offering_id=? ORDER BY effective_from DESC LIMIT 1",
                java.math.BigDecimal.class, offeringId)).isEqualByComparingTo("1499.00");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_type='OFFERING'", Integer.class)).isEqualTo(2);
    }

    @Test
    void managerCannotReadOrWriteOutsideManagedTeamAndSalesReadRequiresLiveAssignment() throws Exception {
        JsonNode manager = loginOne();
        UUID outside = directOffering(ORG_ONE, CATEGORY_ONE, TEAM_MEMBER_ONLY, "Outside", "ACTIVE", START);
        mvc.perform(get("/api/v1/offerings/" + outside).header("Authorization", bearer(manager)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN_SCOPE"));
        jsonRequest(post("/api/v1/offerings"), manager,
                createBody("Invalid team", "SERVICE", CATEGORY_ONE, TEAM_MEMBER_ONLY, "10.00", true))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN_SCOPE"));

        UUID assigned = directOffering(ORG_TWO, CATEGORY_TWO, TEAM_TWO, "Assigned", "ACTIVE", START);
        UUID unassigned = directOffering(ORG_TWO, CATEGORY_TWO, TEAM_TWO, "Unassigned", "ACTIVE", START);
        salesAssignment(assigned, USER_TWO, ORG_TWO, START.minusSeconds(1), null, "ACTIVE", null);
        JsonNode sales = response(login("two", "shared@example.com", PASSWORD_TWO), 200);
        JsonNode saleList = response(mvc.perform(get("/api/v1/offerings").header("Authorization", bearer(sales))), 200);
        assertThat(saleList.get("totalItems").asLong()).isEqualTo(1);
        assertThat(saleList.at("/items/0/id").asText()).isEqualTo(assigned.toString());
        mvc.perform(get("/api/v1/offerings/" + unassigned).header("Authorization", bearer(sales)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN_SCOPE"));
        jsonRequest(post("/api/v1/offerings"), sales,
                createBody("No catalog write", "PRODUCT", CATEGORY_TWO, TEAM_TWO, "1.00", true))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN_PERMISSION"));

        UUID expired = directOffering(ORG_TWO, CATEGORY_TWO, TEAM_TWO, "Expired Assignment", "ACTIVE", START);
        salesAssignment(expired, USER_TWO, ORG_TWO, START.minusSeconds(20), START.minusSeconds(1), "EXPIRED", null);
        JsonNode afterExpiry = response(mvc.perform(get("/api/v1/offerings").header("Authorization", bearer(sales))), 200);
        assertThat(afterExpiry.get("totalItems").asLong()).isEqualTo(1);
    }

    @Test
    void validationDuplicateAndStaleUpdateAreRejectedWithoutChangingData() throws Exception {
        JsonNode manager = loginOne();
        jsonRequest(post("/api/v1/offerings"), manager,
                createBody("Bad price", "SERVICE", CATEGORY_ONE, TEAM_MANAGED, "-1.00", true))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("referencePrice"));
        jsonRequest(post("/api/v1/offerings"), manager,
                createBody("Bad scale", "SERVICE", CATEGORY_ONE, TEAM_MANAGED, "1.001", true))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("referencePrice"));
        jsonRequest(post("/api/v1/offerings"), manager,
                createBody("No category", "SERVICE", CATEGORY_TWO, TEAM_MANAGED, "1.00", true))
                .andExpect(status().isBadRequest());

        JsonNode created = response(jsonRequest(post("/api/v1/offerings"), manager,
                createBody("CRM Package", "SERVICE", CATEGORY_ONE, TEAM_MANAGED, "20.00", true)), 201);
        jsonRequest(post("/api/v1/offerings"), manager,
                createBody(" crm package ", "SERVICE", CATEGORY_ONE, TEAM_MANAGED, "30.00", true))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OFFERING_NAME_EXISTS"));

        Map<String, Object> stale = updateBody(created, "Stale name", "SERVICE", CATEGORY_ONE,
                TEAM_MANAGED, "20.00", null, null);
        stale.put("expectedUpdatedAt", START.minusSeconds(5).toString());
        jsonRequest(put("/api/v1/offerings/" + created.get("id").asText()), manager, stale)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OFFERING_CHANGED"));
        assertThat(jdbc.queryForObject("SELECT name FROM offerings WHERE id=?", String.class,
                UUID.fromString(created.get("id").asText()))).isEqualTo("CRM Package");
    }

    @Test
    void adminCanAssignLegacyUnmappedOfferingWithoutChangingItsData() throws Exception {
        admin();
        grant(ORG_ONE, "ADMIN", "offering.manage");
        UUID legacy = directOffering(ORG_ONE, CATEGORY_ONE, null, "Legacy", "DRAFT", START.minusSeconds(10));
        JsonNode admin = loginOne();
        JsonNode list = response(mvc.perform(get("/api/v1/offerings").header("Authorization", bearer(admin))), 200);
        assertThat(list.get("totalItems").asLong()).isEqualTo(1);
        assertThat(list.at("/items/0/active").asBoolean()).isFalse();
        assertThat(list.at("/items/0/teamId").isNull()).isTrue();

        JsonNode detail = response(mvc.perform(get("/api/v1/offerings/" + legacy).header("Authorization", bearer(admin))), 200);
        JsonNode updated = response(jsonRequest(put("/api/v1/offerings/" + legacy), admin,
                updateBody(detail, "Legacy", "SERVICE", CATEGORY_ONE, TEAM_MANAGED, "10.00", null, null)), 200);
        assertThat(updated.get("teamId").asText()).isEqualTo(TEAM_MANAGED.toString());
        assertThat(updated.get("active").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("SELECT status FROM offerings WHERE id=?", String.class, legacy)).isEqualTo("DRAFT");
    }

    @Test
    void organizationLockPreventsConcurrentDuplicateActiveOfferings() throws Exception {
        JsonNode manager = loginOne();
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> create = () -> {
            start.await();
            MvcResult result = jsonRequest(post("/api/v1/offerings"), manager,
                    createBody("Concurrent CRM", "SERVICE", CATEGORY_ONE, TEAM_MANAGED, "1.00", true)).andReturn();
            return result.getResponse().getStatus();
        };
        List<Integer> statuses;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Integer> first = executor.submit(create);
            Future<Integer> second = executor.submit(create);
            start.countDown();
            statuses = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }
        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offerings WHERE organization_id=? AND lower(name)='concurrent crm'",
                Integer.class, ORG_ONE)).isEqualTo(1);
    }

    private void category(UUID id, UUID organizationId, String code, String name) {
        jdbc.update("""
                INSERT INTO offering_categories(id,organization_id,code,name,status,created_at,updated_at)
                VALUES (?,?,?,?, 'ACTIVE', ?, ?)
                """, id, organizationId, code, name, time(), time());
    }

    private UUID directOffering(UUID organizationId, UUID categoryId, UUID teamId, String name, String status, Instant createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO offerings(id,organization_id,category_id,team_id,code,name,kind,status,created_at,updated_at)
                VALUES (?,?,?,?,?,?, 'SERVICE', ?, ?, ?)
                """, id, organizationId, categoryId, teamId, "TEST-" + id, name, status,
                Timestamp.from(createdAt), Timestamp.from(createdAt));
        jdbc.update("""
                INSERT INTO offering_prices(id,organization_id,offering_id,amount,currency_code,effective_from,created_at)
                VALUES (?,?,?,?, 'VND', ?, ?)
                """, UUID.randomUUID(), organizationId, id, new java.math.BigDecimal("10.00"),
                Timestamp.from(createdAt), Timestamp.from(createdAt));
        return id;
    }

    private void salesAssignment(UUID offeringId, UUID salesUserId, UUID organizationId, Instant from,
                                 Instant to, String status, Instant revokedAt) {
        jdbc.update("""
                INSERT INTO sales_assignments(id,organization_id,offering_id,sales_user_id,assigned_by_user_id,status,
                    effective_from,effective_to,revoked_at,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), organizationId, offeringId, salesUserId, salesUserId, status,
                Timestamp.from(from), to == null ? null : Timestamp.from(to),
                revokedAt == null ? null : Timestamp.from(revokedAt), time(), time());
    }

    private Map<String, Object> createBody(String name, String kind, UUID categoryId, UUID teamId,
                                           String price, boolean active) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("kind", kind);
        body.put("categoryId", categoryId);
        body.put("teamId", teamId);
        body.put("referencePrice", price);
        body.put("active", active);
        body.put("description", null);
        body.put("unit", null);
        return body;
    }

    private Map<String, Object> updateBody(JsonNode current, String name, String kind, UUID categoryId,
                                          UUID teamId, String price, String description, String unit) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("kind", kind);
        body.put("categoryId", categoryId);
        body.put("teamId", teamId);
        body.put("referencePrice", price);
        body.put("expectedUpdatedAt", current.get("updatedAt").asText());
        body.put("description", description);
        body.put("unit", unit);
        return body;
    }
}
