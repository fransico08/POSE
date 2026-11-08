package com.pose.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CustomerReassignIntegrationTest extends FoundationTestSupport {
    private static final UUID ADMIN_USER = id(2101);
    private static final UUID SALE_OLD = id(2102);
    private static final UUID SALE_NEW = id(2103);
    private static final UUID SALE_UNASSIGNED = id(2104);
    private static final UUID CARE_USER = id(2105);
    private static final UUID DATA_USER = id(2106);
    private static final UUID CATEGORY = id(2110);
    private static final UUID OFFERING_MANAGED = id(2111);
    private static final UUID OFFERING_OTHER_TEAM = id(2112);
    private static final UUID CUSTOMER_MAIN = id(2120);
    private static final UUID CUSTOMER_NO_OFFERING = id(2121);
    private static final UUID CUSTOMER_ARCHIVED = id(2122);
    private static final UUID CUSTOMER_OTHER_TEAM = id(2123);
    private static final UUID CUSTOMER_FOREIGN = id(2124);
    private static final UUID OPP_OPEN_OLD = id(2130);
    private static final UUID OPP_PROPOSAL_OLD = id(2131);
    private static final UUID OPP_WON_OLD = id(2132);
    private static final UUID OPP_OPEN_OTHER = id(2133);
    private static final UUID TASK_OPEN_OLD = id(2140);
    private static final UUID TASK_IN_PROGRESS_OLD = id(2141);
    private static final UUID TASK_DONE_OLD = id(2142);
    private static final UUID TASK_OPEN_OTHER = id(2143);

    @BeforeEach
    void setupFixtures() {
        for (String p : List.of("customer.write", "offering.read", "offering.manage", "customer_360.read")) {
            permission(p);
        }
        role(ORG_ONE, "ADMIN");
        role(ORG_ONE, "DATA_STAFF");
        grant(ORG_ONE, "ADMIN", "customer.read");
        grant(ORG_ONE, "SALES_REP", "customer.read");
        for (String r : List.of("ADMIN", "MANAGER", "SALES_REP")) {
            grant(ORG_ONE, r, "customer.write");
            grant(ORG_ONE, r, "customer_360.read");
        }
        grant(ORG_ONE, "SALES_REP", "offering.read");
        grant(ORG_ONE, "MANAGER", "offering.manage");
        grant(ORG_ONE, "ADMIN", "offering.manage");
        grant(ORG_ONE, "DATA_STAFF", "customer.read");

        user(ADMIN_USER, ORG_ONE, "Admin One", "admin-reassign@example.test", hashOne);
        user(SALE_OLD, ORG_ONE, "Sale Old", "sale-old@example.test", hashOne);
        user(SALE_NEW, ORG_ONE, "Sale New", "sale-new@example.test", hashOne);
        user(SALE_UNASSIGNED, ORG_ONE, "Sale Unassigned", "sale-unassigned@example.test", hashOne);
        user(CARE_USER, ORG_ONE, "Care User", "care-reassign@example.test", hashOne);
        user(DATA_USER, ORG_ONE, "Data Staff", "data-reassign@example.test", hashOne);

        userRole(ORG_ONE, ADMIN_USER, "ADMIN");
        userRole(ORG_ONE, SALE_OLD, "SALES_REP");
        userRole(ORG_ONE, SALE_NEW, "SALES_REP");
        userRole(ORG_ONE, SALE_UNASSIGNED, "SALES_REP");
        userRole(ORG_ONE, CARE_USER, "CUSTOMER_CARE");
        userRole(ORG_ONE, DATA_USER, "DATA_STAFF");
        userRole(ORG_ONE, USER_THREE, "MANAGER");

        jdbc.update("INSERT INTO team_memberships(id,organization_id,team_id,user_id,joined_at,status) VALUES (?,?,?,?,?,'ACTIVE')",
                id(2150), ORG_ONE, TEAM_MANAGED, SALE_OLD, time());
        jdbc.update("INSERT INTO team_memberships(id,organization_id,team_id,user_id,joined_at,status) VALUES (?,?,?,?,?,'ACTIVE')",
                id(2151), ORG_ONE, TEAM_MANAGED, SALE_NEW, time());
        jdbc.update("INSERT INTO team_memberships(id,organization_id,team_id,user_id,joined_at,status) VALUES (?,?,?,?,?,'ACTIVE')",
                id(2152), ORG_ONE, TEAM_MEMBER_ONLY, SALE_UNASSIGNED, time());

        jdbc.update("INSERT INTO offering_categories(id,organization_id,code,name,status,created_at,updated_at) VALUES (?,?,'CAT-R','Category R','ACTIVE',?,?)",
                CATEGORY, ORG_ONE, time(), time());
        jdbc.update("INSERT INTO offerings(id,organization_id,category_id,team_id,code,name,kind,status,created_at,updated_at) VALUES (?,?,?,?,'OFF-M','Managed Offering','SERVICE','ACTIVE',?,?)",
                OFFERING_MANAGED, ORG_ONE, CATEGORY, TEAM_MANAGED, time(), time());
        jdbc.update("INSERT INTO offerings(id,organization_id,category_id,team_id,code,name,kind,status,created_at,updated_at) VALUES (?,?,?,?,'OFF-O','Other Team Offering','SERVICE','ACTIVE',?,?)",
                OFFERING_OTHER_TEAM, ORG_ONE, CATEGORY, TEAM_MEMBER_ONLY, time(), time());

        jdbc.update("INSERT INTO sales_assignments(id,organization_id,offering_id,sales_user_id,assigned_by_user_id,status,effective_from,created_at,updated_at) VALUES (?,?,?,?,?,'ACTIVE',?,?,?)",
                id(2160), ORG_ONE, OFFERING_MANAGED, SALE_OLD, USER_ONE, time(), time(), time());
        jdbc.update("INSERT INTO sales_assignments(id,organization_id,offering_id,sales_user_id,assigned_by_user_id,status,effective_from,created_at,updated_at) VALUES (?,?,?,?,?,'ACTIVE',?,?,?)",
                id(2161), ORG_ONE, OFFERING_MANAGED, SALE_NEW, USER_ONE, time(), time(), time());

        insertCustomer(CUSTOMER_MAIN, ORG_ONE, SALE_OLD, "Main Customer", "main@example.test", OFFERING_MANAGED, null);
        insertCustomer(CUSTOMER_NO_OFFERING, ORG_ONE, SALE_OLD, "No Offering Customer", "no-off@example.test", null, null);
        insertCustomer(CUSTOMER_ARCHIVED, ORG_ONE, SALE_OLD, "Archived Customer", "arch@example.test", OFFERING_MANAGED, time());
        insertCustomer(CUSTOMER_OTHER_TEAM, ORG_ONE, SALE_UNASSIGNED, "Other Team Customer", "other-team@example.test", OFFERING_OTHER_TEAM, null);
        insertCustomer(CUSTOMER_FOREIGN, ORG_TWO, USER_TWO, "Foreign Customer", "foreign@example.test", null, null);

        jdbc.update("INSERT INTO customer_assignments(id,organization_id,customer_id,user_id,assignment_type,effective_from,status,assigned_by_user_id,source,created_at) VALUES (?,?,?,?,'SALES',?,'ACTIVE',?,'INITIAL',?)",
                id(2170), ORG_ONE, CUSTOMER_MAIN, SALE_OLD, time(), USER_ONE, time());

        insertOpportunity(OPP_OPEN_OLD, CUSTOMER_MAIN, SALE_OLD, "NEW");
        insertOpportunity(OPP_PROPOSAL_OLD, CUSTOMER_MAIN, SALE_OLD, "PROPOSAL");
        insertOpportunity(OPP_WON_OLD, CUSTOMER_MAIN, SALE_OLD, "WON");
        insertOpportunity(OPP_OPEN_OTHER, CUSTOMER_MAIN, SALE_NEW, "QUALIFIED");

        insertTask(TASK_OPEN_OLD, CUSTOMER_MAIN, SALE_OLD, "OPEN");
        insertTask(TASK_IN_PROGRESS_OLD, CUSTOMER_MAIN, SALE_OLD, "IN_PROGRESS");
        insertTask(TASK_DONE_OLD, CUSTOMER_MAIN, SALE_OLD, "DONE");
        insertTask(TASK_OPEN_OTHER, CUSTOMER_MAIN, CARE_USER, "OPEN");
    }

    private void insertCustomer(UUID id, UUID org, UUID owner, String name, String email, UUID offering, Timestamp archivedAt) {
        jdbc.update("""
                INSERT INTO customers(id,organization_id,owner_user_id,display_name,email,normalized_email,status,customer_type,related_offering_id,archived_at,created_at,updated_at)
                VALUES (?,?,?,?,?,?,'ACTIVE','BUSINESS',?,?,?,?)
                """, id, org, owner, name, email, email, offering, archivedAt, time(), time());
    }

    private void insertOpportunity(UUID id, UUID customer, UUID owner, String stage) {
        Timestamp closedAt = "WON".equals(stage) ? time() : null;
        Integer finalValue = "WON".equals(stage) ? 1000000 : null;
        java.time.LocalDate actualCloseDate = "WON".equals(stage) ? java.time.LocalDate.of(2026, 10, 1) : null;
        String closingReason = "WON".equals(stage) ? "Khách đồng ý ký hợp đồng" : null;
        jdbc.update("""
                INSERT INTO opportunities(id,organization_id,customer_id,offering_id,owner_user_id,title,stage,expected_value,final_value,actual_close_date,closing_reason,currency_code,closed_at,created_at,updated_at)
                VALUES (?,?,?,?,?,'Opp',?,1000000,?,?,?, 'VND',?,?,?)
                """, id, ORG_ONE, customer, OFFERING_MANAGED, owner, stage, finalValue, actualCloseDate, closingReason, closedAt, time(), time());
    }

    private void insertTask(UUID id, UUID customer, UUID owner, String status) {
        Timestamp completedAt = "DONE".equals(status) ? time() : null;
        jdbc.update("""
                INSERT INTO crm_tasks(id,organization_id,customer_id,owner_user_id,created_by_user_id,title,priority,status,due_at,completed_at,origin,created_at,updated_at)
                VALUES (?,?,?,?,?,'Task','HIGH',?,?,?,?,?,?)
                """, id, ORG_ONE, customer, owner, USER_ONE, status, Timestamp.from(START.plusSeconds(86400)), completedAt, "MANUAL", time(), time());
    }

    private JsonNode loginAs(String email) throws Exception {
        return response(login("one", email, PASSWORD_ONE), 200);
    }

    @Test
    void managerReassignsCustomerMovesOpenWorkWritesHandoverAndAuditWithoutReason() throws Exception {
        JsonNode managerTokens = loginOne();
        String secretReason = "Bàn giao vì khách cần tư vấn chuyên sâu từ tuần tới";

        JsonNode res = response(jsonRequest(
                post("/api/v1/customers/" + CUSTOMER_MAIN + "/reassign"),
                managerTokens,
                Map.of("newOwnerUserId", SALE_NEW, "reason", secretReason)
        ), 200);

        assertThat(res.path("ownerUserId").asText()).isEqualTo(SALE_NEW.toString());
        assertThat(res.path("ownerName").asText()).isEqualTo("Sale New");
        assertThat(res.path("handoverId").asText()).isNotBlank();
        assertThat(strings(res.path("movedOpportunityIds")))
                .containsExactly(OPP_OPEN_OLD.toString(), OPP_PROPOSAL_OLD.toString());
        assertThat(strings(res.path("movedTaskIds")))
                .containsExactly(TASK_OPEN_OLD.toString(), TASK_IN_PROGRESS_OLD.toString());

        assertThat(jdbc.queryForObject("SELECT owner_user_id FROM customers WHERE id=?", UUID.class, CUSTOMER_MAIN))
                .isEqualTo(SALE_NEW);
        assertThat(jdbc.queryForObject("SELECT owner_user_id FROM opportunities WHERE id=?", UUID.class, OPP_OPEN_OLD))
                .isEqualTo(SALE_NEW);
        assertThat(jdbc.queryForObject("SELECT owner_user_id FROM opportunities WHERE id=?", UUID.class, OPP_PROPOSAL_OLD))
                .isEqualTo(SALE_NEW);
        assertThat(jdbc.queryForObject("SELECT owner_user_id FROM opportunities WHERE id=?", UUID.class, OPP_WON_OLD))
                .isEqualTo(SALE_OLD);
        assertThat(jdbc.queryForObject("SELECT owner_user_id FROM opportunities WHERE id=?", UUID.class, OPP_OPEN_OTHER))
                .isEqualTo(SALE_NEW);

        assertThat(jdbc.queryForObject("SELECT owner_user_id FROM crm_tasks WHERE id=?", UUID.class, TASK_OPEN_OLD))
                .isEqualTo(SALE_NEW);
        assertThat(jdbc.queryForObject("SELECT owner_user_id FROM crm_tasks WHERE id=?", UUID.class, TASK_IN_PROGRESS_OLD))
                .isEqualTo(SALE_NEW);
        assertThat(jdbc.queryForObject("SELECT owner_user_id FROM crm_tasks WHERE id=?", UUID.class, TASK_DONE_OLD))
                .isEqualTo(SALE_OLD);
        assertThat(jdbc.queryForObject("SELECT owner_user_id FROM crm_tasks WHERE id=?", UUID.class, TASK_OPEN_OTHER))
                .isEqualTo(CARE_USER);

        Map<String, Object> hr = jdbc.queryForMap("SELECT * FROM handover_requests WHERE customer_id=?", CUSTOMER_MAIN);
        assertThat(hr.get("handover_type")).isEqualTo("SALES");
        assertThat(hr.get("status")).isEqualTo("APPROVED");
        assertThat(hr.get("requested_by_user_id")).isEqualTo(USER_ONE);
        assertThat(hr.get("reviewed_by_user_id")).isEqualTo(USER_ONE);
        assertThat(hr.get("target_user_id")).isEqualTo(SALE_NEW);
        assertThat(hr.get("reason")).isEqualTo(secretReason);
        assertThat(hr.get("reviewed_at")).isNotNull();

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM customer_assignments WHERE customer_id=? AND user_id=? AND assignment_type='SALES' AND status='ACTIVE'",
                Integer.class, CUSTOMER_MAIN, SALE_OLD)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM customer_assignments WHERE customer_id=? AND user_id=? AND assignment_type='SALES' AND status='ACTIVE' AND source='HANDOVER'",
                Integer.class, CUSTOMER_MAIN, SALE_NEW)).isEqualTo(1);

        JsonNode beforeAudit = auditSnapshot("OWNER_REASSIGNED", "before_data");
        JsonNode afterAudit = auditSnapshot("OWNER_REASSIGNED", "after_data");
        assertThat(beforeAudit.path("ownerUserId").asText()).isEqualTo(SALE_OLD.toString());
        assertThat(afterAudit.path("ownerUserId").asText()).isEqualTo(SALE_NEW.toString());
        assertThat(afterAudit.path("handoverId").asText()).isEqualTo(res.path("handoverId").asText());
        assertThat(afterAudit.path("movedOpportunities").asInt()).isEqualTo(2);
        assertThat(afterAudit.path("movedTasks").asInt()).isEqualTo(2);

        List<String> allAuditRows = jdbc.queryForList(
                "SELECT CAST(before_data AS VARCHAR) || CAST(after_data AS VARCHAR) FROM audit_logs", String.class);
        assertThat(String.join(" ", allAuditRows)).doesNotContain(secretReason, "reason");

        JsonNode oldSaleTokens = loginAs("sale-old@example.test");
        mvc.perform(get("/api/v1/customers/" + CUSTOMER_MAIN).header("Authorization", bearer(oldSaleTokens)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_SCOPE"));

        JsonNode newSaleTokens = loginAs("sale-new@example.test");
        mvc.perform(get("/api/v1/customers/" + CUSTOMER_MAIN).header("Authorization", bearer(newSaleTokens)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerUserId").value(SALE_NEW.toString()));
    }

    @Test
    void adminInSameOrganizationCanReassignCustomer() throws Exception {
        jdbc.update("INSERT INTO sales_assignments(id,organization_id,offering_id,sales_user_id,assigned_by_user_id,status,effective_from,created_at,updated_at) VALUES (?,?,?,?,?,'ACTIVE',?,?,?)",
                id(2162), ORG_ONE, OFFERING_OTHER_TEAM, SALE_UNASSIGNED, USER_THREE, time(), time(), time());
        UUID secondSaleOtherTeam = id(2107);
        user(secondSaleOtherTeam, ORG_ONE, "Sale Other 2", "sale-other-2@example.test", hashOne);
        userRole(ORG_ONE, secondSaleOtherTeam, "SALES_REP");
        jdbc.update("INSERT INTO team_memberships(id,organization_id,team_id,user_id,joined_at,status) VALUES (?,?,?,?,?,'ACTIVE')",
                id(2153), ORG_ONE, TEAM_MEMBER_ONLY, secondSaleOtherTeam, time());
        jdbc.update("INSERT INTO sales_assignments(id,organization_id,offering_id,sales_user_id,assigned_by_user_id,status,effective_from,created_at,updated_at) VALUES (?,?,?,?,?,'ACTIVE',?,?,?)",
                id(2163), ORG_ONE, OFFERING_OTHER_TEAM, secondSaleOtherTeam, USER_THREE, time(), time(), time());

        JsonNode adminTokens = loginAs("admin-reassign@example.test");
        JsonNode res = response(jsonRequest(
                post("/api/v1/customers/" + CUSTOMER_OTHER_TEAM + "/reassign"),
                adminTokens,
                Map.of("newOwnerUserId", secondSaleOtherTeam, "reason", "Admin điều phối liên nhóm")
        ), 200);
        assertThat(res.path("ownerUserId").asText()).isEqualTo(secondSaleOtherTeam.toString());
    }

    @Test
    void rejectsUnauthorizedRolesOutOfScopeAndValidationErrors() throws Exception {
        JsonNode saleTokens = loginAs("sale-old@example.test");
        jsonRequest(post("/api/v1/customers/" + CUSTOMER_MAIN + "/reassign"), saleTokens,
                Map.of("newOwnerUserId", SALE_NEW, "reason", "Sale tự đổi"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_PERMISSION"));

        JsonNode careTokens = loginAs("care-reassign@example.test");
        jsonRequest(post("/api/v1/customers/" + CUSTOMER_MAIN + "/reassign"), careTokens,
                Map.of("newOwnerUserId", SALE_NEW, "reason", "Care đổi"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_PERMISSION"));

        JsonNode dataTokens = loginAs("data-reassign@example.test");
        jsonRequest(post("/api/v1/customers/" + CUSTOMER_MAIN + "/reassign"), dataTokens,
                Map.of("newOwnerUserId", SALE_NEW, "reason", "Data đổi"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_PERMISSION"));

        JsonNode managerTokens = loginOne();
        jsonRequest(post("/api/v1/customers/" + CUSTOMER_OTHER_TEAM + "/reassign"), managerTokens,
                Map.of("newOwnerUserId", SALE_NEW, "reason", "Ngoài team quản lý"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_SCOPE"));

        jsonRequest(post("/api/v1/customers/" + CUSTOMER_FOREIGN + "/reassign"), managerTokens,
                Map.of("newOwnerUserId", SALE_NEW, "reason", "Khác tổ chức"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_SCOPE"));

        jsonRequest(post("/api/v1/customers/" + CUSTOMER_ARCHIVED + "/reassign"), managerTokens,
                Map.of("newOwnerUserId", SALE_NEW, "reason", "Hồ sơ đã lưu trữ"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_SCOPE"));

        jsonRequest(post("/api/v1/customers/" + CUSTOMER_NO_OFFERING + "/reassign"), managerTokens,
                Map.of("newOwnerUserId", SALE_NEW, "reason", "Thiếu sản phẩm liên quan"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("relatedOfferingId"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Hồ sơ chưa có sản phẩm/dịch vụ liên quan nên chưa đổi được người phụ trách."));

        jsonRequest(post("/api/v1/customers/" + CUSTOMER_MAIN + "/reassign"), managerTokens,
                Map.of("newOwnerUserId", SALE_OLD, "reason", "Trùng người cũ"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("newOwnerUserId"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Người phụ trách mới trùng với người phụ trách hiện tại."));

        jsonRequest(post("/api/v1/customers/" + CUSTOMER_MAIN + "/reassign"), managerTokens,
                Map.of("newOwnerUserId", SALE_UNASSIGNED, "reason", "Chưa được phân công sản phẩm"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("newOwnerUserId"));

        jsonRequest(post("/api/v1/customers/" + CUSTOMER_MAIN + "/reassign"), managerTokens,
                Map.of("newOwnerUserId", SALE_NEW, "reason", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("reason"));

        jsonRequest(post("/api/v1/customers/" + CUSTOMER_MAIN + "/reassign"), managerTokens,
                Map.of("newOwnerUserId", SALE_NEW, "reason", "a".repeat(1001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("reason"));
    }

    @Test
    void rejectsIneligibleOfferingsOnOpenOpportunitiesBeforeAnyMutation() throws Exception {
        JsonNode manager=loginOne();
        jdbc.update("UPDATE opportunities SET offering_id=? WHERE id=?",OFFERING_OTHER_TEAM,OPP_OPEN_OLD);
        jdbc.update("INSERT INTO opportunity_lines(id,organization_id,opportunity_id,offering_id,position,quantity,unit_price,line_total,created_at,updated_at) VALUES (?,?,?,?,1,1,1,1,?,?)",id(2198),ORG_ONE,OPP_PROPOSAL_OLD,OFFERING_OTHER_TEAM,time(),time());
        Map<String,String> queries=Map.of("customers","SELECT id,owner_user_id,updated_at FROM customers ORDER BY id",
                "opportunities","SELECT id,owner_user_id,stage,updated_at FROM opportunities ORDER BY id",
                "crm_tasks","SELECT id,owner_user_id,status,updated_at FROM crm_tasks ORDER BY id",
                "customer_assignments","SELECT id,user_id,status,effective_to FROM customer_assignments ORDER BY id",
                "handover_requests","SELECT id,status FROM handover_requests ORDER BY id",
                "audit_logs","SELECT id,action,entity_id FROM audit_logs ORDER BY id");
        Map<String,List<Map<String,Object>>> before=new LinkedHashMap<>();queries.forEach((key,sql)->before.put(key,jdbc.queryForList(sql)));
        jsonRequest(post("/api/v1/customers/"+CUSTOMER_MAIN+"/reassign"),manager,Map.of("newOwnerUserId",SALE_NEW,"reason","Bàn giao"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("newOwnerUserId"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Người phụ trách mới chưa được phân công sản phẩm/dịch vụ của 2 cơ hội đang mở trên hồ sơ này."));
        for(var entry:before.entrySet()) assertThat(jdbc.queryForList(queries.get(entry.getKey()))).as(entry.getKey()).isEqualTo(entry.getValue());
    }

    @Test
    void rollsBackAllChangesWhenTransactionFailsMidFlight() throws Exception {
        JsonNode managerTokens = loginOne();
        jdbc.update("INSERT INTO customer_assignments(id,organization_id,customer_id,user_id,assignment_type,effective_from,effective_to,status,assigned_by_user_id,source,created_at) VALUES (?,?,?,?,'SALES',?,?,'ENDED',?,'HANDOVER',?)",
                id(2199), ORG_ONE, CUSTOMER_NO_OFFERING, SALE_NEW, time(), time(), USER_ONE, time());
        jdbc.execute("CREATE UNIQUE INDEX uq_test_midflight_rollback ON customer_assignments(organization_id, user_id, source)");
        try {
            jsonRequest(
                    post("/api/v1/customers/" + CUSTOMER_MAIN + "/reassign"),
                    managerTokens,
                    Map.of("newOwnerUserId", SALE_NEW, "reason", "Thử rollback giữa chừng")
            ).andExpect(status().isConflict());
        } finally {
            jdbc.execute("DROP INDEX uq_test_midflight_rollback");
        }

        assertThat(jdbc.queryForObject("SELECT owner_user_id FROM customers WHERE id=?", UUID.class, CUSTOMER_MAIN))
                .isEqualTo(SALE_OLD);
        assertThat(jdbc.queryForObject("SELECT owner_user_id FROM opportunities WHERE id=?", UUID.class, OPP_OPEN_OLD))
                .isEqualTo(SALE_OLD);
        assertThat(jdbc.queryForObject("SELECT owner_user_id FROM crm_tasks WHERE id=?", UUID.class, TASK_OPEN_OLD))
                .isEqualTo(SALE_OLD);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM handover_requests WHERE customer_id=?", Integer.class, CUSTOMER_MAIN))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action IN ('OWNER_REASSIGNED','OPPORTUNITY_ASSIGNED','TASK_ASSIGNED')", Integer.class))
                .isZero();
    }
}
