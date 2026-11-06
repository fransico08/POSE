package com.pose.backend.auth;

import com.pose.workflow.jdbc.JdbcWorkflowProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties={"pose.workflow.worker.enabled=true","pose.workflow.worker.interval-ms=86400000"})
class WorkflowIntegrationTest extends FoundationTestSupport {
    private static final UUID CAT=id(901), OFFER=id(902), LEAD=id(903);
    @Autowired JdbcWorkflowProcessor processor;

    @BeforeEach void workflowFixtures() {
        for (String code:List.of("lead.read","lead.write","offering.read")) {
            if (jdbc.queryForObject("SELECT count(*) FROM permissions WHERE code=?",Integer.class,code)==0) permission(code);
        }
        grant(ORG_ONE,"MANAGER","lead.read"); grant(ORG_ONE,"MANAGER","lead.write");
        grant(ORG_ONE,"SALES_REP","offering.read"); userRole(ORG_ONE,USER_THREE,"SALES_REP");
        jdbc.update("INSERT INTO team_memberships(id,organization_id,team_id,user_id,joined_at,status) VALUES (?,?,?,?,?,'ACTIVE')",UUID.randomUUID(),ORG_ONE,TEAM_MANAGED,USER_THREE,time());
        jdbc.update("INSERT INTO offering_categories(id,organization_id,code,name,status,created_at,updated_at) VALUES (?,?,'W08','W08','ACTIVE',?,?)",CAT,ORG_ONE,time(),time());
        jdbc.update("INSERT INTO offerings(id,organization_id,category_id,team_id,code,name,kind,status,created_at,updated_at) VALUES (?,?,?,?,'W08','W08','SERVICE','ACTIVE',?,?)",OFFER,ORG_ONE,CAT,TEAM_MANAGED,time(),time());
        jdbc.update("INSERT INTO sales_assignments(id,organization_id,offering_id,sales_user_id,assigned_by_user_id,status,effective_from,created_at,updated_at) VALUES (?,?,?,?,?,'ACTIVE',?,?,?)",UUID.randomUUID(),ORG_ONE,OFFER,USER_THREE,USER_ONE,time(),time(),time());
        jdbc.update("INSERT INTO leads(id,organization_id,owner_user_id,offering_id,display_name,email,normalized_email,status,created_at,updated_at) VALUES (?,?,?,?,'Lead test','w08@example.test','w08@example.test','NEW',?,?)",LEAD,ORG_ONE,USER_THREE,OFFER,time(),time());
    }
    private void qualify() throws Exception {
        var actor=loginOne();
        jsonRequest(post("/api/v1/leads/"+LEAD+"/qualify"),actor,Map.of("expectedUpdatedAt",time().toInstant().toString()))
                .andExpect(status().isOk());
    }
    @Test void createsExactlyOneAuditedTaskAndIgnoresOtherEvents() throws Exception {
        qualify();
        UUID outbox=jdbc.queryForObject("SELECT id FROM outbox_events WHERE aggregate_id=?",UUID.class,LEAD);
        processor.processPendingBatch(10);
        assertThat(jdbc.queryForObject("SELECT delivery_status FROM outbox_events WHERE id=?",String.class,outbox)).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT status FROM workflow_executions WHERE outbox_event_id=?",String.class,outbox)).isEqualTo("DONE");
        var task=jdbc.queryForMap("SELECT id,owner_user_id,created_by_user_id,origin,priority FROM crm_tasks WHERE lead_id=?",LEAD);
        assertThat(task.get("owner_user_id")).isEqualTo(USER_THREE);
        assertThat(task.get("created_by_user_id")).isEqualTo(USER_ONE);
        assertThat(task.get("origin")).isEqualTo("WORKFLOW");
        assertThat(task.get("priority")).isEqualTo("MEDIUM");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_id=? AND action='TASK_CREATED' AND actor_type='SYSTEM' AND actor_system_key='workflow-lead-qualified'",Integer.class,task.get("id"))).isEqualTo(1);
        jdbc.update("UPDATE outbox_events SET delivery_status='PENDING',published_at=NULL WHERE id=?",outbox);
        processor.processPendingBatch(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM crm_tasks WHERE lead_id=?",Integer.class,LEAD)).isEqualTo(1);
    }
    @Test void closedLeadDoesNotCreateTask() throws Exception {
        qualify(); jdbc.update("UPDATE leads SET status='DISQUALIFIED',disqualification_reason='closed' WHERE id=?",LEAD);
        processor.processPendingBatch(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM crm_tasks WHERE lead_id=?",Integer.class,LEAD)).isZero();
        assertThat(jdbc.queryForObject("SELECT delivery_status FROM outbox_events WHERE aggregate_id=?",String.class,LEAD)).isEqualTo("FAILED");
    }
    @Test void inactiveQualifierAndCrossTenantEventDoNotCreateTasks() throws Exception {
        qualify();
        jdbc.update("UPDATE users SET status='INACTIVE' WHERE id=?",USER_ONE);
        processor.processPendingBatch(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM crm_tasks WHERE lead_id=?",Integer.class,LEAD)).isZero();
        assertThat(jdbc.queryForObject("SELECT delivery_status FROM outbox_events WHERE aggregate_id=?",String.class,LEAD)).isEqualTo("FAILED");
        UUID foreign=UUID.randomUUID();
        jdbc.update("INSERT INTO outbox_events(id,organization_id,aggregate_type,aggregate_id,event_type,payload,idempotency_key,delivery_status,retry_count,occurred_at) VALUES (?,?,'LEAD',?,'LEAD_QUALIFIED',CAST(? AS jsonb),?,'PENDING',0,?)",
                foreign,ORG_TWO,LEAD,"{\"qualifiedByUserId\":\""+USER_TWO+"\"}","w08-foreign",time());
        processor.processPendingBatch(10);
        assertThat(jdbc.queryForObject("SELECT delivery_status FROM outbox_events WHERE id=?",String.class,foreign)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM crm_tasks WHERE organization_id=?",Integer.class,ORG_TWO)).isZero();
    }
    @Test void ineligibleOwnerDoesNotCreateTask() throws Exception {
        qualify();
        jdbc.update("UPDATE sales_assignments SET status='REVOKED',revoked_at=? WHERE offering_id=?",time(),OFFER);
        processor.processPendingBatch(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM crm_tasks WHERE lead_id=?",Integer.class,LEAD)).isZero();
        assertThat(jdbc.queryForObject("SELECT delivery_status FROM outbox_events WHERE aggregate_id=?",String.class,LEAD)).isEqualTo("FAILED");
    }
    @Test void simultaneousWorkersDoNotCreateDuplicateTasks() throws Exception {
        qualify(); var gate=new CountDownLatch(1);
        try (ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var first=executor.submit(()->{gate.await(); return processor.processPendingBatch(10);});
            var second=executor.submit(()->{gate.await(); return processor.processPendingBatch(10);});
            gate.countDown(); first.get(15,TimeUnit.SECONDS); second.get(15,TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM crm_tasks WHERE lead_id=?",Integer.class,LEAD)).isEqualTo(1);
    }
    @Test void auditFailureRetriesAndEventuallyFailsWithoutTask() throws Exception {
        qualify();
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT reject_w08_audit CHECK(action <> 'TASK_CREATED')");
        try {
            processor.processPendingBatch(10); processor.processPendingBatch(10); processor.processPendingBatch(10);
            assertThat(jdbc.queryForObject("SELECT delivery_status FROM outbox_events WHERE aggregate_id=?",String.class,LEAD)).isEqualTo("FAILED");
            assertThat(jdbc.queryForObject("SELECT retry_count FROM outbox_events WHERE aggregate_id=?",Integer.class,LEAD)).isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM crm_tasks WHERE lead_id=?",Integer.class,LEAD)).isZero();
        } finally { jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT reject_w08_audit"); }
    }
}
