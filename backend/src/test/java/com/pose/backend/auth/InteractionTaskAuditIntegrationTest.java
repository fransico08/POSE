package com.pose.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties = "pose.sales-assignment.expiry-scan-delay-ms=86400000")
class InteractionTaskAuditIntegrationTest extends FoundationTestSupport {
    private static final UUID SALE = id(901), CARE = id(902), ADMIN = id(903), DATA = id(904);
    private static final UUID CUSTOMER = id(905), LEAD = id(906), CATEGORY = id(907), OFFERING = id(908), OPPORTUNITY = id(909);
    private static final String INTERACTIONS = "/api/v1/interactions", TASKS = "/api/v1/tasks", AUDIT = "/api/v1/audit";

    @BeforeEach
    void fixtures() {
        for (String code : List.of("interaction.read", "interaction.write", "interaction.sensitive.read", "task.manage", "audit.read", "offering.read", "opportunity.read")) {
            if (jdbc.queryForObject("SELECT count(*) FROM permissions WHERE code=?", Integer.class, code) == 0) permission(code);
        }
        for (String role : List.of("ADMIN", "DATA_STAFF")) role(ORG_ONE, role);
        for (String role : List.of("MANAGER", "SALES_REP", "CUSTOMER_CARE", "ADMIN", "DATA_STAFF")) {
            grantOnce(role, "interaction.read");
            grantOnce(role, "task.manage");
        }
        for (String role : List.of("MANAGER", "SALES_REP", "CUSTOMER_CARE", "ADMIN")) grantOnce(role, "interaction.write");
        for (String role : List.of("MANAGER", "SALES_REP", "ADMIN")) grantOnce(role, "interaction.sensitive.read");
        grantOnce("MANAGER", "audit.read"); grantOnce("ADMIN", "audit.read"); grantOnce("SALES_REP", "offering.read");
        grantOnce("MANAGER", "opportunity.read"); grantOnce("SALES_REP", "opportunity.read"); grantOnce("DATA_STAFF", "opportunity.read");
        user(SALE, ORG_ONE, "Sale W06", "sale-w06@example.test", hashOne);
        user(CARE, ORG_ONE, "Care W06", "care-w06@example.test", hashOne);
        user(ADMIN, ORG_ONE, "Admin W06", "admin-w06@example.test", hashOne);
        user(DATA, ORG_ONE, "Data W06", "data-w06@example.test", hashOne);
        userRole(ORG_ONE, SALE, "SALES_REP"); userRole(ORG_ONE, CARE, "CUSTOMER_CARE");
        userRole(ORG_ONE, ADMIN, "ADMIN"); userRole(ORG_ONE, DATA, "DATA_STAFF");
        member(id(910), TEAM_MANAGED, SALE); member(id(911), TEAM_MANAGED, CARE);
        userRole(ORG_ONE, USER_THREE, "SALES_REP");
        jdbc.update("INSERT INTO offering_categories(id,organization_id,code,name,status,created_at,updated_at) VALUES (?,?,'W06','W06','ACTIVE',?,?)",
                CATEGORY, ORG_ONE, time(), time());
        jdbc.update("INSERT INTO offerings(id,organization_id,category_id,team_id,code,name,kind,status,created_at,updated_at) VALUES (?,?,?,?,'W06','W06','SERVICE','ACTIVE',?,?)",
                OFFERING, ORG_ONE, CATEGORY, TEAM_MANAGED, time(), time());
        jdbc.update("INSERT INTO sales_assignments(id,organization_id,offering_id,sales_user_id,assigned_by_user_id,status,effective_from,created_at,updated_at) VALUES (?,?,?,?,?,'ACTIVE',?,?,?)",
                id(912), ORG_ONE, OFFERING, SALE, USER_ONE, time(), time(), time());
        customer(CUSTOMER, SALE); careAssignment(CUSTOMER, CARE);
        jdbc.update("INSERT INTO customer_assignments(id,organization_id,customer_id,user_id,assignment_type,effective_from,status,assigned_by_user_id,source,created_at) VALUES (?,?,?,?,'SALES',?,'ACTIVE',?,'MANUAL',?)",
                id(914), ORG_ONE, CUSTOMER, USER_THREE, time(), USER_ONE, time());
        jdbc.update("INSERT INTO leads(id,organization_id,owner_user_id,offering_id,display_name,email,normalized_email,status,created_at,updated_at) VALUES (?,?,?,?,?,'w06-lead@example.test','w06-lead@example.test','NEW',?,?)",
                LEAD, ORG_ONE, SALE, OFFERING, "W06 Lead", time(), time());
        jdbc.update("INSERT INTO opportunities(id,organization_id,customer_id,offering_id,owner_user_id,title,stage,created_at,updated_at) VALUES (?,?,?,?,?,'W06 Opportunity','NEW',?,?)",
                OPPORTUNITY, ORG_ONE, CUSTOMER, OFFERING, SALE, time(), time());
        jdbc.update("INSERT INTO opportunity_lines(id,organization_id,opportunity_id,offering_id,position,quantity,unit_price,line_total,created_at,updated_at) VALUES (?,?,?,?,1,1,1,1,?,?)",
                id(913), ORG_ONE, OPPORTUNITY, OFFERING, time(), time());
    }

    private void grantOnce(String role, String permission) {
        if (jdbc.queryForObject("SELECT count(*) FROM role_permissions rp JOIN roles r ON r.id=rp.role_id JOIN permissions p ON p.id=rp.permission_id WHERE r.organization_id=? AND r.code=? AND p.code=?",
                Integer.class, ORG_ONE, role, permission) == 0) grant(ORG_ONE, role, permission);
    }
    private void member(UUID membershipId, UUID team, UUID user) {
        jdbc.update("INSERT INTO team_memberships(id,organization_id,team_id,user_id,joined_at,status) VALUES (?,?,?, ?,?,'ACTIVE')",
                membershipId, ORG_ONE, team, user, time());
    }
    private void customer(UUID id, UUID owner) {
        jdbc.update("INSERT INTO customers(id,organization_id,owner_user_id,display_name,email,normalized_email,status,created_at,updated_at) VALUES (?,?,?,'W06 Customer',? ,?,'ACTIVE',?,?)",
                id, ORG_ONE, owner, "w06-" + id + "@example.test", "w06-" + id + "@example.test", time(), time());
    }
    private void careAssignment(UUID customer, UUID user) {
        jdbc.update("INSERT INTO customer_assignments(id,organization_id,customer_id,user_id,assignment_type,effective_from,status,assigned_by_user_id,source,created_at) VALUES (?,?,?,?,'CARE',?,'ACTIVE',?,'MANUAL',?)",
                UUID.randomUUID(), ORG_ONE, customer, user, time(), USER_ONE, time());
    }
    private JsonNode actor(String email) throws Exception { return response(login("one", email, PASSWORD_ONE), 200); }
    private JsonNode actorSale() throws Exception { return actor("sale-w06@example.test"); }
    private JsonNode actorCare() throws Exception { return actor("care-w06@example.test"); }
    private JsonNode actorAdmin() throws Exception { return actor("admin-w06@example.test"); }
    private JsonNode actorData() throws Exception { return actor("data-w06@example.test"); }
    private Map<String,Object> interaction(UUID customer, UUID lead, String visibility) {
        Map<String,Object> body = new LinkedHashMap<>(); body.put("customerId", customer == null ? null : customer.toString());
        body.put("leadId", lead == null ? null : lead.toString()); body.put("type", "CALL"); body.put("channel", "PHONE");
        body.put("subject", "Discuss requirements"); body.put("content", "Private interaction body"); body.put("outcome", "Follow up next week");
        body.put("visibility", visibility); body.put("occurredAt", START.toString()); return body;
    }
    private Map<String,Object> task(UUID customer, UUID lead, UUID owner) {
        Map<String,Object> body = new LinkedHashMap<>(); body.put("customerId", customer == null ? null : customer.toString());
        body.put("leadId", lead == null ? null : lead.toString()); body.put("ownerUserId", owner == null ? null : owner.toString());
        body.put("title", "Call customer"); body.put("description", "Private task description"); body.put("priority", "NORMAL");
        body.put("dueAt", START.plusSeconds(86400).toString()); return body;
    }
    private String version(JsonNode entity) { return entity.get("updatedAt").asText(); }
    private JsonNode getAs(String path, JsonNode actor, int status) throws Exception {
        return response(mvc.perform(get(path).header("Authorization", bearer(actor))), status);
    }
    private void error(ResultActions result, int status, String code) throws Exception {
        result.andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code));
    }
    private long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class); }

    @Test void closedLeadsRejectNewWorkButExistingWorkRemainsEditable() throws Exception {
        JsonNode sale=actorSale();
        JsonNode interaction=response(jsonRequest(post(INTERACTIONS),sale,interaction(null,LEAD,"CARE_VISIBLE")),201);
        JsonNode task=response(jsonRequest(post(TASKS),sale,task(null,LEAD,null)),201);
        jdbc.update("UPDATE leads SET status='DISQUALIFIED',disqualification_reason='Closed' WHERE id=?",LEAD);
        error(jsonRequest(post(INTERACTIONS),sale,interaction(null,LEAD,"CARE_VISIBLE")),409,"LEAD_CLOSED");
        error(jsonRequest(post(TASKS),sale,task(null,LEAD,null)),409,"LEAD_CLOSED");
        jsonRequest(patch(INTERACTIONS+"/"+interaction.get("id").asText()),sale,Map.of("expectedUpdatedAt",version(interaction),"subject","Updated")).andExpect(status().isOk());
        jsonRequest(post(TASKS+"/"+task.get("id").asText()+"/complete"),sale,Map.of("expectedUpdatedAt",version(task),"result","Done")).andExpect(status().isOk());
        jdbc.update("UPDATE leads SET status='CONVERTED',disqualification_reason=NULL,converted_customer_id=?,converted_at=? WHERE id=?",CUSTOMER,time(),LEAD);
        error(jsonRequest(post(INTERACTIONS),sale,interaction(null,LEAD,"CARE_VISIBLE")),409,"LEAD_CLOSED");
        error(jsonRequest(post(TASKS),sale,task(null,LEAD,null)),409,"LEAD_CLOSED");
    }

    private void assertCustomerReferences(JsonNode value) { assertCustomerReferences(value, true); }
    private void assertCustomerReferences(JsonNode value, boolean visible) {
        assertThat(value.path("customerId").asText()).isEqualTo(CUSTOMER.toString());
        assertThat(value.path("customer").path("id").asText()).isEqualTo(CUSTOMER.toString());
        assertThat(value.path("customer").path("name").asText()).isEqualTo("W06 Customer");
        assertThat(value.path("lead").isNull()).isTrue();
        assertThat(value.path("opportunityId").asText()).isEqualTo(OPPORTUNITY.toString());
        assertThat(value.path("opportunity").path("id").asText()).isEqualTo(OPPORTUNITY.toString());
        if (visible) assertThat(value.path("opportunity").path("title").asText()).isEqualTo("W06 Opportunity");
        else assertThat(value.path("opportunity").path("title").isNull()).isTrue();
    }

    @Test
    void referencesAreReturnedForInteractionsAndPreservedWhenRedacted() throws Exception {
        JsonNode sale = actorSale(), care = actorCare(), data = actorData();
        Map<String,Object> customerBody = interaction(CUSTOMER, null, "CARE_VISIBLE");
        customerBody.put("opportunityId", OPPORTUNITY.toString());
        JsonNode created = response(jsonRequest(post(INTERACTIONS), sale, customerBody), 201);
        assertCustomerReferences(created);
        String id = created.path("id").asText();
        assertCustomerReferences(getAs(INTERACTIONS + "/" + id, care, 200), false);
        JsonNode metadata = getAs(INTERACTIONS + "/" + id, data, 200);
        assertCustomerReferences(metadata, false);
        assertThat(metadata.path("content").isNull()).isTrue();
        assertCustomerReferences(getAs(INTERACTIONS + "?customerId=" + CUSTOMER, sale, 200).path("items").get(0));
        JsonNode updated = response(jsonRequest(patch(INTERACTIONS + "/" + id), sale,
                Map.of("expectedUpdatedAt", version(created), "subject", "Updated")), 200);
        assertCustomerReferences(updated);
        JsonNode lead = response(jsonRequest(post(INTERACTIONS), sale, interaction(null, LEAD, "CARE_VISIBLE")), 201);
        assertThat(lead.path("lead").path("id").asText()).isEqualTo(LEAD.toString());
        assertThat(lead.path("lead").path("name").asText()).isEqualTo("W06 Lead");
        assertThat(lead.path("customer").isNull()).isTrue();
        assertThat(lead.path("opportunity").isNull()).isTrue();
    }

    @Test
    void referencesAreReturnedForTaskCommandsAndRedactedMetadata() throws Exception {
        JsonNode sale = actorSale(), manager = loginOne(), data = actorData(), care = actorCare();
        Map<String,Object> customerBody = task(CUSTOMER, null, SALE);
        customerBody.put("opportunityId", OPPORTUNITY.toString());
        JsonNode created = response(jsonRequest(post(TASKS), manager, customerBody), 201);
        assertCustomerReferences(created);
        String id = created.path("id").asText();
        assertCustomerReferences(getAs(TASKS + "/" + id, sale, 200));
        JsonNode metadata = getAs(TASKS + "/" + id, data, 200);
        assertCustomerReferences(metadata, false);
        assertThat(metadata.path("description").isNull()).isTrue();
        assertCustomerReferences(getAs(TASKS + "?customerId=" + CUSTOMER, sale, 200).path("items").get(0));
        JsonNode updated = response(jsonRequest(patch(TASKS + "/" + id), sale,
                Map.of("expectedUpdatedAt", version(created), "priority", "HIGH")), 200);
        assertCustomerReferences(updated);
        JsonNode assigned = response(jsonRequest(post(TASKS + "/" + id + "/assign"), manager,
                Map.of("expectedUpdatedAt", version(updated), "ownerUserId", CARE.toString())), 200);
        assertCustomerReferences(assigned);
        JsonNode started = response(jsonRequest(post(TASKS + "/" + id + "/start"), care,
                Map.of("expectedUpdatedAt", version(assigned))), 200);
        assertCustomerReferences(started, false);
        JsonNode completed = response(jsonRequest(post(TASKS + "/" + id + "/complete"), care,
                Map.of("expectedUpdatedAt", version(started), "result", "Done")), 200);
        assertCustomerReferences(completed, false);
        JsonNode another = response(jsonRequest(post(TASKS), manager, customerBody), 201);
        JsonNode cancelled = response(jsonRequest(post(TASKS + "/" + another.path("id").asText() + "/cancel"), manager,
                Map.of("expectedUpdatedAt", version(another), "reason", "Cancelled")), 200);
        assertCustomerReferences(cancelled);
        JsonNode lead = response(jsonRequest(post(TASKS), sale, task(null, LEAD, null)), 201);
        assertThat(lead.path("lead").path("id").asText()).isEqualTo(LEAD.toString());
        assertThat(lead.path("lead").path("name").asText()).isEqualTo("W06 Lead");
        assertThat(lead.path("customer").isNull()).isTrue();
        assertThat(lead.path("opportunity").isNull()).isTrue();
    }

    @Test
    void interactionCreateReadPatchAndAuditExcludeSensitiveText() throws Exception {
        JsonNode sale = actorSale();
        JsonNode created = response(jsonRequest(post(INTERACTIONS), sale, interaction(CUSTOMER, null, "CARE_VISIBLE")), 201);
        UUID id = UUID.fromString(created.get("id").asText());
        assertThat(created.get("recordedByUserId").asText()).isEqualTo(SALE.toString());
        assertThat(getAs(INTERACTIONS + "/" + id, sale, 200).get("content").asText()).isEqualTo("Private interaction body");
        JsonNode changed = response(jsonRequest(patch(INTERACTIONS + "/" + id), sale,
                Map.of("expectedUpdatedAt", version(created), "content", "Updated private text", "visibility", "SALES_ONLY")), 200);
        assertThat(changed.get("visibility").asText()).isEqualTo("SALES_ONLY");
        String audit = jdbc.queryForObject("SELECT CAST(after_data AS VARCHAR) FROM audit_logs WHERE entity_type='INTERACTION' AND entity_id=? AND action='INTERACTION_UPDATED'", String.class, id);
        assertThat(audit).contains("content").doesNotContain("Updated private text", "Private interaction body", "Follow up next week");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_type='INTERACTION' AND entity_id=?", Integer.class, id)).isEqualTo(2);
        error(jsonRequest(patch(INTERACTIONS + "/" + id), sale, Map.of("expectedUpdatedAt", version(created), "subject", "Stale")), 409, "INTERACTION_CHANGED");
    }

    @Test
    void careCannotSeeSalesOnlyInteractionAndCanOnlyWriteCareVisible() throws Exception {
        JsonNode sale = actorSale(), care = actorCare();
        JsonNode hidden = response(jsonRequest(post(INTERACTIONS), sale, interaction(CUSTOMER, null, "SALES_ONLY")), 201);
        error(mvc.perform(get(INTERACTIONS + "/" + hidden.get("id").asText()).header("Authorization", bearer(care))), 403, "FORBIDDEN_SCOPE");
        assertThat(getAs(INTERACTIONS + "?customerId=" + CUSTOMER, care, 200).get("totalItems").asInt()).isZero();
        error(jsonRequest(post(INTERACTIONS), care, interaction(CUSTOMER, null, "SALES_ONLY")), 403, "FORBIDDEN_PERMISSION");
        JsonNode visible = response(jsonRequest(post(INTERACTIONS), care, interaction(CUSTOMER, null, "CARE_VISIBLE")), 201);
        assertThat(visible.get("recordedByUserId").asText()).isEqualTo(CARE.toString());
    }

    @Test
    void sensitiveReadPermissionControlsSalesOnlyRowsWhileDataStaffGetsRedactedMetadata() throws Exception {
        JsonNode sale = actorSale();
        JsonNode hidden = response(jsonRequest(post(INTERACTIONS), sale, interaction(CUSTOMER, null, "SALES_ONLY")), 201);
        UUID interactionId = UUID.fromString(hidden.get("id").asText());
        jdbc.update("DELETE FROM role_permissions WHERE role_id=(SELECT id FROM roles WHERE organization_id=? AND code='SALES_REP') AND permission_id=(SELECT id FROM permissions WHERE code='interaction.sensitive.read')", ORG_ONE);
        JsonNode limitedSale = actorSale();
        assertThat(getAs(INTERACTIONS + "?customerId=" + CUSTOMER, limitedSale, 200).get("totalItems").asInt()).isZero();
        error(mvc.perform(get(INTERACTIONS + "/" + interactionId).header("Authorization", bearer(limitedSale))), 403, "FORBIDDEN_SCOPE");
        JsonNode metadata = getAs(INTERACTIONS + "/" + interactionId, actorData(), 200);
        assertThat(metadata.get("subject").isNull()).isTrue();
        assertThat(metadata.get("content").isNull()).isTrue();
        assertThat(metadata.get("outcome").isNull()).isTrue();
    }

    @Test
    void interactionRejectsInvalidSubjectOpportunityAndFields() throws Exception {
        JsonNode sale = actorSale();
        Map<String,Object> invalid = interaction(CUSTOMER, LEAD, "CARE_VISIBLE");
        error(jsonRequest(post(INTERACTIONS), sale, invalid), 400, "VALIDATION_ERROR");
        Map<String,Object> mismatch = interaction(CUSTOMER, null, "CARE_VISIBLE"); mismatch.put("opportunityId", id(920).toString());
        jsonRequest(post(INTERACTIONS), sale, mismatch).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("opportunityId"));
        Map<String,Object> bad = interaction(CUSTOMER, null, "CARE_VISIBLE"); bad.put("visibility", "PUBLIC");
        jsonRequest(post(INTERACTIONS), sale, bad).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("visibility"));
        Map<String,Object> linked = interaction(CUSTOMER, null, "CARE_VISIBLE"); linked.put("opportunityId", OPPORTUNITY.toString());
        response(jsonRequest(post(INTERACTIONS), sale, linked), 201);
        assertThat(count("interactions")).isEqualTo(1);
    }

    @Test
    void taskLifecycleOwnerRestrictionsAndAuditAreAtomic() throws Exception {
        JsonNode manager = loginOne(), sale = actorSale(), care = actorCare();
        JsonNode created = response(jsonRequest(post(TASKS), manager, task(CUSTOMER, null, SALE)), 201);
        UUID id = UUID.fromString(created.get("id").asText());
        assertThat(created.get("status").asText()).isEqualTo("OPEN"); assertThat(created.get("origin").asText()).isEqualTo("MANUAL");
        assertThat(getAs(TASKS + "/" + id, sale, 200).get("title").asText()).isEqualTo("Call customer");
        error(mvc.perform(get(TASKS + "/" + id).header("Authorization", bearer(care))), 403, "FORBIDDEN_SCOPE");
        JsonNode started = response(jsonRequest(post(TASKS + "/" + id + "/start"), sale, Map.of("expectedUpdatedAt", version(created))), 200);
        JsonNode done = response(jsonRequest(post(TASKS + "/" + id + "/complete"), sale,
                Map.of("expectedUpdatedAt", version(started), "result", "Completed")), 200);
        assertThat(done.get("status").asText()).isEqualTo("DONE"); assertThat(done.get("completedAt").asText()).isNotBlank();
        error(jsonRequest(post(TASKS + "/" + id + "/start"), sale, Map.of("expectedUpdatedAt", version(done))), 409, "TASK_STATE_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_type='CRM_TASK' AND entity_id=?", Integer.class, id)).isEqualTo(3);
        String data = jdbc.queryForObject("SELECT CAST(after_data AS VARCHAR) FROM audit_logs WHERE entity_type='CRM_TASK' AND entity_id=? AND action='TASK_COMPLETED'", String.class, id);
        assertThat(data).doesNotContain("Completed", "Private task description");
    }

    @Test
    void taskCancelKeepsReasonSeparateAndManagerCanReassignEligibleOwner() throws Exception {
        JsonNode manager = loginOne(), sale = actorSale();
        JsonNode created = response(jsonRequest(post(TASKS), manager, task(CUSTOMER, null, SALE)), 201);
        UUID id = UUID.fromString(created.get("id").asText());
        jsonRequest(post(TASKS + "/" + id + "/assign"), manager,
                Map.of("expectedUpdatedAt", version(created), "ownerUserId", USER_THREE.toString()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.fieldErrors[0].field").value("ownerUserId"));
        JsonNode assigned = response(jsonRequest(post(TASKS + "/" + id + "/assign"), manager,
                Map.of("expectedUpdatedAt", version(created), "ownerUserId", CARE.toString())), 200);
        assertThat(assigned.get("ownerUserId").asText()).isEqualTo(CARE.toString());
        JsonNode reassigned = response(jsonRequest(post(TASKS + "/" + id + "/assign"), manager,
                Map.of("expectedUpdatedAt", version(assigned), "ownerUserId", SALE.toString())), 200);
        JsonNode cancelled = response(jsonRequest(post(TASKS + "/" + id + "/cancel"), sale,
                Map.of("expectedUpdatedAt", version(reassigned), "reason", "No longer needed")), 200);
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.get("cancellationReason").asText()).isEqualTo("No longer needed");
        assertThat(cancelled.get("result").isNull()).isTrue();
        error(jsonRequest(post(TASKS + "/" + id + "/assign"), manager,
                Map.of("expectedUpdatedAt", version(cancelled), "ownerUserId", CARE.toString())), 409, "TASK_STATE_CONFLICT");
    }

    @Test
    void managerAuditIsTeamScopedSalesAndDataStaffCannotReadW06Audit() throws Exception {
        JsonNode manager = loginOne(), sale = actorSale(), admin = actorAdmin(), data = actorData();
        JsonNode task = response(jsonRequest(post(TASKS), manager, task(CUSTOMER, null, SALE)), 201);
        JsonNode managerList = getAs(AUDIT + "?entityType=CRM_TASK", manager, 200);
        assertThat(managerList.get("totalItems").asInt()).isEqualTo(1);
        assertThat(getAs(AUDIT + "/" + managerList.get("items").get(0).get("id").asText(), manager, 200).get("entityId").asText()).isEqualTo(task.get("id").asText());
        error(mvc.perform(get(AUDIT).header("Authorization", bearer(sale))), 403, "FORBIDDEN_PERMISSION");
        error(mvc.perform(get(AUDIT).header("Authorization", bearer(data))), 403, "FORBIDDEN_PERMISSION");
        assertThat(getAs(AUDIT, admin, 200).get("totalItems").asInt()).isEqualTo(1);
    }

    @Test
    void auditInsertFailureRollsBackInteractionAndTaskMutation() throws Exception {
        JsonNode sale = actorSale(); long tasksBefore = count("crm_tasks"), interactionsBefore = count("interactions");
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT reject_w06_audit CHECK(action NOT IN ('INTERACTION_CREATED','TASK_CREATED'))");
        try {
            error(jsonRequest(post(INTERACTIONS), sale, interaction(CUSTOMER, null, "CARE_VISIBLE")), 409, "CONFLICT");
            error(jsonRequest(post(TASKS), sale, task(CUSTOMER, null, null)), 409, "CONFLICT");
            assertThat(count("crm_tasks")).isEqualTo(tasksBefore); assertThat(count("interactions")).isEqualTo(interactionsBefore);
        } finally { jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT reject_w06_audit"); }
    }

    @Test
    void dataStaffReceivesRedactedMetadataOnlyAndListFiltersAndValidationWork() throws Exception {
        JsonNode sale = actorSale(), data = actorData();
        JsonNode created = response(jsonRequest(post(INTERACTIONS), sale, interaction(CUSTOMER, null, "CARE_VISIBLE")), 201);
        JsonNode redacted = getAs(INTERACTIONS + "/" + created.get("id").asText(), data, 200);
        assertThat(redacted.get("content").isNull()).isTrue(); assertThat(redacted.get("outcome").isNull()).isTrue(); assertThat(redacted.get("subject").isNull()).isTrue();
        assertThat(getAs(INTERACTIONS + "?type=CALL&channel=PHONE&page=0&size=1", sale, 200).get("totalItems").asInt()).isEqualTo(1);
        mvc.perform(get(TASKS + "?status=NOT_A_STATUS").header("Authorization", bearer(sale)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("status"));
    }
}
