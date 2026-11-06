package com.pose.backend.auth;

import com.pose.backend.workflow.WorkflowScheduler;
import com.pose.workflow.jdbc.JdbcWorkflowProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named="POSE_V2_SMOKE_DATABASE_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:54329/pose_v2_review_[a-z0-9_]+")
@SpringBootTest(properties={"pose.security.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "spring.jpa.hibernate.ddl-auto=none","pose.workflow.worker.enabled=true","pose.workflow.worker.interval-ms=86400000"})
class RestoredWorkflowSmokeTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired JdbcWorkflowProcessor processor;
    @Autowired WorkflowScheduler scheduler;

    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        String url=System.getenv("POSE_V2_SMOKE_DATABASE_URL");
        if(url==null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:54329/pose_v2_review_[a-z0-9_]+"))
            throw new IllegalArgumentException("Smoke test requires isolated restored database");
        properties.add("spring.datasource.url",()->url);
        properties.add("spring.datasource.username",()->"pose_auth_test");
        properties.add("spring.datasource.password",()->"");
        properties.add("spring.flyway.baseline-on-migrate",()->"true");
        properties.add("spring.flyway.baseline-version",()->"1");
    }
    @Test void enabledWorkerLeavesAllUnrelatedDemoEventsAndExecutionsUnchanged() {
        var beforeEvents=jdbc.queryForList("SELECT * FROM outbox_events ORDER BY id");
        var beforeExecutions=jdbc.queryForList("SELECT * FROM workflow_executions ORDER BY id");
        assertThat(beforeEvents).hasSize(19); assertThat(beforeExecutions).hasSize(19);
        assertThat(scheduler).isNotNull();
        assertThat(processor.processPendingBatch(50)).isZero();
        assertThat(jdbc.queryForList("SELECT * FROM outbox_events ORDER BY id")).isEqualTo(beforeEvents);
        assertThat(jdbc.queryForList("SELECT * FROM workflow_executions ORDER BY id")).isEqualTo(beforeExecutions);
        System.out.println("W08_RESTORE_ENABLED outbox=19->19 executions=19->19");
    }
}
