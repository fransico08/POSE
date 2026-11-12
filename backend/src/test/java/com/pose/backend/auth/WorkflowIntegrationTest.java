package com.pose.backend.auth;

import com.pose.workflow.jdbc.JdbcWorkflowProcessor;
import com.pose.workflow.LeadQualifiedTrigger;
import com.pose.workflow.TransactionalOutboxPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
    @Autowired TransactionalOutboxPublisher outboxPublisher;
    @Autowired PlatformTransactionManager transactionManager;

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
        UUID taskId=(UUID)task.get("id");
        jdbc.update("UPDATE outbox_events SET delivery_status='PENDING',published_at=NULL WHERE id=?",outbox);
        processor.processPendingBatch(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM crm_tasks WHERE lead_id=?",Integer.class,LEAD)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT id FROM crm_tasks WHERE lead_id=?",UUID.class,LEAD)).isEqualTo(taskId);
        assertThat(jdbc.queryForObject("SELECT created_task_id FROM workflow_executions WHERE outbox_event_id=?",UUID.class,outbox)).isEqualTo(taskId);
    }
    @Test void closedLeadDoesNotCreateTask() throws Exception {
        qualify(); jdbc.update("UPDATE leads SET status='DISQUALIFIED',disqualification_reason='closed' WHERE id=?",LEAD);
        processor.processPendingBatch(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM crm_tasks WHERE lead_id=?",Integer.class,LEAD)).isZero();
        assertThat(jdbc.queryForObject("SELECT delivery_status FROM outbox_events WHERE aggregate_id=?",String.class,LEAD)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT retry_count FROM outbox_events WHERE aggregate_id=?",Integer.class,LEAD)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT last_error FROM outbox_events WHERE aggregate_id=?",String.class,LEAD)).isEqualTo("LEAD_NOT_QUALIFIED");
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
        assertThat(jdbc.queryForObject("SELECT last_error FROM outbox_events WHERE id=?",String.class,foreign)).isEqualTo("LEAD_UNAVAILABLE");
        assertThat(jdbc.queryForObject("SELECT retry_count FROM outbox_events WHERE id=?",Integer.class,foreign)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM crm_tasks WHERE organization_id=?",Integer.class,ORG_TWO)).isZero();
    }
    @Test void ineligibleOwnerDoesNotCreateTask() throws Exception {
        qualify();
        jdbc.update("UPDATE sales_assignments SET status='REVOKED',revoked_at=? WHERE offering_id=?",time(),OFFER);
        processor.processPendingBatch(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM crm_tasks WHERE lead_id=?",Integer.class,LEAD)).isZero();
        assertThat(jdbc.queryForObject("SELECT delivery_status FROM outbox_events WHERE aggregate_id=?",String.class,LEAD)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT retry_count FROM outbox_events WHERE aggregate_id=?",Integer.class,LEAD)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT last_error FROM outbox_events WHERE aggregate_id=?",String.class,LEAD)).isEqualTo("OWNER_NOT_ELIGIBLE");
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

    @Test void transientAuditFailureRecoversToExactlyOneTask() throws Exception {
        qualify();
        UUID outbox=jdbc.queryForObject("SELECT id FROM outbox_events WHERE aggregate_id=?",UUID.class,LEAD);
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT reject_w08_transient_audit CHECK(action <> 'TASK_CREATED')");
        try {
            processor.processPendingBatch(10);
            assertThat(jdbc.queryForObject("SELECT delivery_status FROM outbox_events WHERE id=?",String.class,outbox)).isEqualTo("PENDING");
            assertThat(jdbc.queryForObject("SELECT retry_count FROM outbox_events WHERE id=?",Integer.class,outbox)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT last_error FROM outbox_events WHERE id=?",String.class,outbox)).isEqualTo("WORKFLOW_PROCESSING_ERROR");
            assertThat(jdbc.queryForObject("SELECT status FROM workflow_executions WHERE outbox_event_id=?",String.class,outbox)).isEqualTo("PENDING");
            assertThat(jdbc.queryForObject("SELECT retry_count FROM workflow_executions WHERE outbox_event_id=?",Integer.class,outbox)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM crm_tasks WHERE lead_id=?",Integer.class,LEAD)).isZero();

            jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT reject_w08_transient_audit");
            processor.processPendingBatch(10);

            assertThat(jdbc.queryForObject("SELECT delivery_status FROM outbox_events WHERE id=?",String.class,outbox)).isEqualTo("PUBLISHED");
            assertThat(jdbc.queryForObject("SELECT status FROM workflow_executions WHERE outbox_event_id=?",String.class,outbox)).isEqualTo("DONE");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM crm_tasks WHERE lead_id=?",Integer.class,LEAD)).isEqualTo(1);
        } finally {
            jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT IF EXISTS reject_w08_transient_audit");
        }
    }

    @Test void duplicateOutboxKeyWithChangedPayloadPreservesOriginalEvent() throws Exception {
        qualify();
        UUID originalId=jdbc.queryForObject("SELECT id FROM outbox_events WHERE aggregate_id=?",UUID.class,LEAD);
        String originalPayload=jdbc.queryForObject("SELECT CAST(payload AS VARCHAR) FROM outbox_events WHERE id=?",String.class,originalId);
        UUID changedOwner=USER_ONE, changedQualifier=USER_THREE;
        assertThat(changedOwner).isNotEqualTo(USER_THREE);
        assertThat(changedQualifier).isNotEqualTo(USER_ONE);

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                outboxPublisher.publishLeadQualified(new LeadQualifiedTrigger(
                        ORG_ONE,LEAD,changedOwner,changedQualifier,clock.instant())));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE organization_id=? AND idempotency_key=(SELECT idempotency_key FROM outbox_events WHERE id=?)",Integer.class,ORG_ONE,originalId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT id FROM outbox_events WHERE idempotency_key=(SELECT idempotency_key FROM outbox_events WHERE id=?)",UUID.class,originalId)).isEqualTo(originalId);
        assertThat(jdbc.queryForObject("SELECT CAST(payload AS VARCHAR) FROM outbox_events WHERE id=?",String.class,originalId)).isEqualTo(originalPayload);
    }

    @Test void sameOutboxIdempotencyKeyIsIsolatedByTenant() throws Exception {
        qualify();
        UUID secondLead=id(904), secondCategory=id(905), secondOffering=id(906), secondTeam=id(907);
        String sharedKey=jdbc.queryForObject("SELECT idempotency_key FROM outbox_events WHERE aggregate_id=?",String.class,LEAD);

        grant(ORG_TWO,"SALES_REP","offering.read");
        team(secondTeam,ORG_TWO,"W08","W08 tenant two",USER_TWO);
        jdbc.update("INSERT INTO team_memberships(id,organization_id,team_id,user_id,joined_at,status) VALUES (?,?,?,?,?,'ACTIVE')",
                UUID.randomUUID(),ORG_TWO,secondTeam,USER_TWO,time());
        jdbc.update("INSERT INTO offering_categories(id,organization_id,code,name,status,created_at,updated_at) VALUES (?,?,'W08','W08','ACTIVE',?,?)",
                secondCategory,ORG_TWO,time(),time());
        jdbc.update("INSERT INTO offerings(id,organization_id,category_id,team_id,code,name,kind,status,created_at,updated_at) VALUES (?,?,?,?,'W08','W08','SERVICE','ACTIVE',?,?)",
                secondOffering,ORG_TWO,secondCategory,secondTeam,time(),time());
        jdbc.update("INSERT INTO sales_assignments(id,organization_id,offering_id,sales_user_id,assigned_by_user_id,status,effective_from,created_at,updated_at) VALUES (?,?,?,?,?,'ACTIVE',?,?,?)",
                UUID.randomUUID(),ORG_TWO,secondOffering,USER_TWO,USER_TWO,time(),time(),time());
        jdbc.update("INSERT INTO leads(id,organization_id,owner_user_id,offering_id,display_name,email,normalized_email,status,created_at,updated_at) VALUES (?,?,?,?,'Tenant two lead','w08-tenant-two@example.test','w08-tenant-two@example.test','QUALIFIED',?,?)",
                secondLead,ORG_TWO,USER_TWO,secondOffering,time(),time());
        UUID secondOutbox=UUID.randomUUID();
        jdbc.update("INSERT INTO outbox_events(id,organization_id,aggregate_type,aggregate_id,event_type,payload,idempotency_key,delivery_status,retry_count,occurred_at) VALUES (?,?,'LEAD',?,'LEAD_QUALIFIED',CAST(? AS jsonb),?,'PENDING',0,?)",
                secondOutbox,ORG_TWO,secondLead,"{\"leadId\":\""+secondLead+"\",\"ownerUserId\":\""+USER_TWO+"\",\"qualifiedByUserId\":\""+USER_TWO+"\"}",sharedKey,time());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE idempotency_key=?",Integer.class,sharedKey)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT organization_id) FROM outbox_events WHERE idempotency_key=?",Integer.class,sharedKey)).isEqualTo(2);
        assertThat(processor.processPendingBatch(10)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT delivery_status FROM outbox_events WHERE aggregate_id=?",String.class,LEAD)).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT delivery_status FROM outbox_events WHERE id=?",String.class,secondOutbox)).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM crm_tasks WHERE organization_id=? AND lead_id=?",Integer.class,ORG_ONE,LEAD)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM crm_tasks WHERE organization_id=? AND lead_id=?",Integer.class,ORG_TWO,secondLead)).isEqualTo(1);
    }

    @Test void interruptedPostgresSessionRollsBackAndCanBeReplayed() throws Exception {
        try (var connection=Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
            Assumptions.assumeTrue("PostgreSQL".equals(connection.getMetaData().getDatabaseProductName()),
                    "Session termination recovery is verified on the isolated PostgreSQL suite");
        }
        qualify();
        UUID outbox=jdbc.queryForObject("SELECT id FROM outbox_events WHERE aggregate_id=?",UUID.class,LEAD);
        jdbc.execute("CREATE OR REPLACE FUNCTION w08_pause_task_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action='TASK_CREATED' THEN PERFORM pg_sleep(30); END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER w08_pause_task_audit BEFORE INSERT ON audit_logs FOR EACH ROW EXECUTE FUNCTION w08_pause_task_audit()");
        try {
            try (ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor()) {
                Future<Integer> worker=executor.submit(()->processor.processPendingBatch(10));
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
                Integer backendPid=null;
                while (System.nanoTime()<deadline && backendPid==null) {
                    backendPid=jdbc.query("SELECT pid FROM pg_stat_activity WHERE datname=current_database() AND pid<>pg_backend_pid() AND state='active' AND query ILIKE '%INSERT INTO audit_logs%' ORDER BY backend_start DESC LIMIT 1",
                            rows->rows.next()?rows.getInt(1):null);
                    if (backendPid==null) Thread.sleep(50);
                }
                assertThat(backendPid).as("worker PostgreSQL session blocked inside audit insert").isNotNull();
                assertThat(jdbc.queryForObject("SELECT pg_terminate_backend(?)",Boolean.class,backendPid)).isTrue();
                worker.get(15,TimeUnit.SECONDS);
            }

            assertThat(jdbc.queryForObject("SELECT delivery_status FROM outbox_events WHERE id=?",String.class,outbox)).isEqualTo("PENDING");
            assertThat(jdbc.queryForObject("SELECT retry_count FROM outbox_events WHERE id=?",Integer.class,outbox)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT status FROM workflow_executions WHERE outbox_event_id=?",String.class,outbox)).isEqualTo("PENDING");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM crm_tasks WHERE lead_id=?",Integer.class,LEAD)).isZero();

            jdbc.execute("DROP TRIGGER w08_pause_task_audit ON audit_logs");
            jdbc.execute("DROP FUNCTION w08_pause_task_audit()");
            processor.processPendingBatch(10);
            assertThat(jdbc.queryForObject("SELECT delivery_status FROM outbox_events WHERE id=?",String.class,outbox)).isEqualTo("PUBLISHED");
            assertThat(jdbc.queryForObject("SELECT status FROM workflow_executions WHERE outbox_event_id=?",String.class,outbox)).isEqualTo("DONE");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM crm_tasks WHERE lead_id=?",Integer.class,LEAD)).isEqualTo(1);
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS w08_pause_task_audit ON audit_logs");
            jdbc.execute("DROP FUNCTION IF EXISTS w08_pause_task_audit()");
        }
    }

    @Test void crashedWorkerJvmRollsBackAndCanBeReplayed() throws Exception {
        try (var connection=Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
            Assumptions.assumeTrue("PostgreSQL".equals(connection.getMetaData().getDatabaseProductName()),
                    "Worker JVM crash recovery is verified on the isolated PostgreSQL suite");
        }
        qualify();
        UUID outbox=jdbc.queryForObject("SELECT id FROM outbox_events WHERE aggregate_id=?",UUID.class,LEAD);
        jdbc.execute("CREATE OR REPLACE FUNCTION w08_pause_task_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action='TASK_CREATED' THEN PERFORM pg_sleep(10); END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER w08_pause_task_audit BEFORE INSERT ON audit_logs FOR EACH ROW EXECUTE FUNCTION w08_pause_task_audit()");

        Process worker=null;
        java.nio.file.Path workerLog=java.nio.file.Path.of("target","w08-crash-worker.log");
        try {
            String classpath=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
            assertThat(classpath).as("forked worker test classpath").isNotBlank();
            String javaExecutable=java.nio.file.Path.of(System.getProperty("java.home"),"bin",
                    System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")?"java.exe":"java").toString();
            java.nio.file.Files.createDirectories(workerLog.getParent());
            ProcessBuilder builder=new ProcessBuilder(javaExecutable,"-cp",classpath,WorkflowCrashWorkerMain.class.getName())
                    .directory(java.nio.file.Path.of("").toAbsolutePath().toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(workerLog.toFile());
            Map<String,String> environment=builder.environment();
            environment.put("POSE_DATABASE_URL",System.getenv("POSE_TEST_DATABASE_URL"));
            environment.put("POSE_DATABASE_USERNAME",System.getenv("POSE_TEST_DATABASE_USERNAME"));
            environment.put("POSE_DATABASE_PASSWORD",Optional.ofNullable(System.getenv("POSE_TEST_DATABASE_PASSWORD")).orElse(""));
            environment.put("POSE_JWT_SECRET_BASE64","MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
            environment.put("POSE_WORKFLOW_WORKER_ENABLED","true");
            environment.put("POSE_WORKFLOW_WORKER_INTERVAL_MS","86400000");
            environment.put("SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE","1");
            worker=builder.start();

            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
            Integer backendPid=null;
            while (System.nanoTime()<deadline && backendPid==null) {
                if (!worker.isAlive()) {
                    String output=java.nio.file.Files.exists(workerLog)?java.nio.file.Files.readString(workerLog):"<no worker log>";
                    throw new AssertionError("Worker JVM exited before entering the transaction: "+output);
                }
                backendPid=jdbc.query("SELECT pid FROM pg_stat_activity WHERE datname=current_database() AND application_name='w08-crash-worker' AND state='active' AND query ILIKE '%INSERT INTO audit_logs%' ORDER BY backend_start DESC LIMIT 1",
                        rows->rows.next()?rows.getInt(1):null);
                if (backendPid==null) Thread.sleep(50);
            }
            assertThat(backendPid).as("child worker JVM blocked inside its Task audit transaction").isNotNull();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM crm_tasks WHERE lead_id=?",Integer.class,LEAD))
                    .as("uncommitted Task is invisible before the worker crash").isZero();

            worker.destroyForcibly();
            assertThat(worker.waitFor(10,TimeUnit.SECONDS)).as("child worker JVM stops").isTrue();
            long releaseDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
            Integer activeSessionCount=1;
            while (System.nanoTime()<releaseDeadline && activeSessionCount>0) {
                activeSessionCount=jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE pid=?",Integer.class,backendPid);
                if (activeSessionCount>0) Thread.sleep(50);
            }
            assertThat(activeSessionCount).as("PostgreSQL releases the crashed worker transaction").isZero();

            assertThat(jdbc.queryForObject("SELECT delivery_status FROM outbox_events WHERE id=?",String.class,outbox)).isEqualTo("PENDING");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM crm_tasks WHERE lead_id=?",Integer.class,LEAD)).isZero();

            jdbc.execute("DROP TRIGGER w08_pause_task_audit ON audit_logs");
            jdbc.execute("DROP FUNCTION w08_pause_task_audit()");
            processor.processPendingBatch(10);
            assertThat(jdbc.queryForObject("SELECT delivery_status FROM outbox_events WHERE id=?",String.class,outbox)).isEqualTo("PUBLISHED");
            assertThat(jdbc.queryForObject("SELECT status FROM workflow_executions WHERE outbox_event_id=?",String.class,outbox)).isEqualTo("DONE");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM crm_tasks WHERE lead_id=?",Integer.class,LEAD)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_id=(SELECT id FROM crm_tasks WHERE lead_id=?) AND action='TASK_CREATED'",Integer.class,LEAD)).isEqualTo(1);
        } finally {
            if (worker!=null && worker.isAlive()) {
                worker.destroyForcibly();
                worker.waitFor(10,TimeUnit.SECONDS);
            }
            jdbc.execute("DROP TRIGGER IF EXISTS w08_pause_task_audit ON audit_logs");
            jdbc.execute("DROP FUNCTION IF EXISTS w08_pause_task_audit()");
        }
    }
}
