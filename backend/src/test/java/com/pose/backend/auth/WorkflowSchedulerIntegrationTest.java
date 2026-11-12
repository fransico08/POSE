package com.pose.backend.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "pose.workflow.worker.enabled=true",
        "pose.workflow.worker.interval-ms=2000",
        "pose.workflow.worker.max-retry=3"
})
class WorkflowSchedulerIntegrationTest extends FoundationTestSupport {
    private static final UUID CATEGORY = id(1901);
    private static final UUID OFFERING = id(1902);
    private static final UUID LEAD = id(1903);

    @BeforeEach
    void workflowFixtures() {
        for (String code : java.util.List.of("lead.read", "lead.write", "offering.read")) {
            if (jdbc.queryForObject("SELECT count(*) FROM permissions WHERE code=?", Integer.class, code) == 0) {
                permission(code);
            }
        }
        grant(ORG_ONE, "MANAGER", "lead.read");
        grant(ORG_ONE, "MANAGER", "lead.write");
        grant(ORG_ONE, "SALES_REP", "offering.read");
        userRole(ORG_ONE, USER_THREE, "SALES_REP");
        jdbc.update("INSERT INTO team_memberships(id,organization_id,team_id,user_id,joined_at,status) VALUES (?,?,?,?,?,'ACTIVE')",
                UUID.randomUUID(), ORG_ONE, TEAM_MANAGED, USER_THREE, time());
        jdbc.update("INSERT INTO offering_categories(id,organization_id,code,name,status,created_at,updated_at) VALUES (?,?,'W08S','W08 scheduler','ACTIVE',?,?)",
                CATEGORY, ORG_ONE, time(), time());
        jdbc.update("INSERT INTO offerings(id,organization_id,category_id,team_id,code,name,kind,status,created_at,updated_at) VALUES (?,?,?,?,'W08S','W08 scheduler','SERVICE','ACTIVE',?,?)",
                OFFERING, ORG_ONE, CATEGORY, TEAM_MANAGED, time(), time());
        jdbc.update("INSERT INTO sales_assignments(id,organization_id,offering_id,sales_user_id,assigned_by_user_id,status,effective_from,created_at,updated_at) VALUES (?,?,?,?,?,'ACTIVE',?,?,?)",
                UUID.randomUUID(), ORG_ONE, OFFERING, USER_THREE, USER_ONE, time(), time(), time());
        jdbc.update("INSERT INTO leads(id,organization_id,owner_user_id,offering_id,display_name,email,normalized_email,status,created_at,updated_at) VALUES (?,?,?,?,'Scheduler retry lead','w08-scheduler@example.test','w08-scheduler@example.test','NEW',?,?)",
                LEAD, ORG_ONE, USER_THREE, OFFERING, time(), time());
    }

    @Test
    void schedulerRetriesTransientDatabaseFailureAndCompletesAfterRecovery() throws Exception {
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT reject_w08_scheduled_audit CHECK(action <> 'TASK_CREATED')");
        try {
            var actor = loginOne();
            jsonRequest(post("/api/v1/leads/" + LEAD + "/qualify"), actor,
                    Map.of("expectedUpdatedAt", time().toInstant().toString()))
                    .andExpect(status().isOk());

            await(() -> jdbc.queryForObject("SELECT retry_count FROM outbox_events WHERE aggregate_id=?", Integer.class, LEAD) == 1,
                    "scheduler records the first transient audit failure");
            assertThat(jdbc.queryForObject("SELECT delivery_status FROM outbox_events WHERE aggregate_id=?", String.class, LEAD))
                    .isEqualTo("PENDING");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM crm_tasks WHERE lead_id=?", Integer.class, LEAD)).isZero();

            jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT reject_w08_scheduled_audit");
            await(() -> "PUBLISHED".equals(jdbc.queryForObject(
                            "SELECT delivery_status FROM outbox_events WHERE aggregate_id=?", String.class, LEAD)),
                    "scheduler retries after the database condition recovers");

            UUID outboxId = jdbc.queryForObject("SELECT id FROM outbox_events WHERE aggregate_id=?", UUID.class, LEAD);
            assertThat(jdbc.queryForObject("SELECT status FROM workflow_executions WHERE outbox_event_id=?", String.class, outboxId))
                    .isEqualTo("DONE");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM crm_tasks WHERE lead_id=?", Integer.class, LEAD)).isEqualTo(1);
        } finally {
            jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT IF EXISTS reject_w08_scheduled_audit");
        }
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(12);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(50);
        }
        assertThat(condition.getAsBoolean()).as(description).isTrue();
    }
}
