package com.pose.backend.workflow;

import com.pose.workflow.TransactionalOutboxPublisher;
import com.pose.workflow.jdbc.JdbcTransactionalOutboxPublisher;
import com.pose.workflow.jdbc.*;
import com.pose.backend.audit.FoundationAuditRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

@Configuration
public class WorkflowConfiguration {
    @Bean
    TransactionalOutboxPublisher transactionalOutboxPublisher(DataSource dataSource) {
        return new JdbcTransactionalOutboxPublisher(() -> {
            if (!TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new IllegalStateException("Publishing requires a Spring transaction");
            }
            return DataSourceUtils.getConnection(dataSource);
        });
    }

    @Bean
    @ConditionalOnProperty(name="pose.workflow.worker.enabled", havingValue="true")
    WorkflowTransactionRunner workflowTransactionRunner(DataSource dataSource, PlatformTransactionManager manager) {
        TransactionTemplate template = new TransactionTemplate(manager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return new WorkflowTransactionRunner() {
            public <T> T execute(Work<T> work) {
                return template.execute(status -> {
                    try { return work.run(DataSourceUtils.getConnection(dataSource)); }
                    catch (java.sql.SQLException ex) { throw new WorkflowPersistenceException("Workflow transaction failed", ex); }
                });
            }
        };
    }

    @Bean
    @ConditionalOnProperty(name="pose.workflow.worker.enabled", havingValue="true")
    JdbcWorkflowProcessor workflowProcessor(DataSource dataSource, WorkflowTransactionRunner transactions,
            FoundationAuditRepository audit, Clock clock, ObjectMapper json,
            @Value("${pose.workflow.worker.max-retry:3}") int maxAttempts,
            @Value("${pose.workflow.worker.due-days:1}") long dueDays) {
        AutomationActorResolver actors = (connection, org, event) -> {
            try (var query = connection.prepareStatement("SELECT CAST(payload AS VARCHAR) FROM outbox_events WHERE organization_id=? AND id=?")) {
                query.setObject(1,org); query.setObject(2,event);
                try (var row=query.executeQuery()) {
                    if (!row.next()) return null;
                    try {
                        var payload=json.readTree(row.getString(1));
                        if (payload.isTextual()) payload=json.readTree(payload.asText());
                        var value=payload.get("qualifiedByUserId");
                        return value == null || !value.isTextual() ? null : UUID.fromString(value.asText());
                    } catch (java.io.IOException | IllegalArgumentException ex) { return null; }
                }
            }
        };
        WorkflowTaskTemplate task = (event, now) -> new WorkflowTaskTemplate.Details(
                "Theo dõi Lead đủ điều kiện", null, "MEDIUM", now.plus(dueDays,ChronoUnit.DAYS));
        WorkflowTaskAudit taskAudit = (connection, org, event, execution, taskId) ->
                audit.recordSystem(org,"workflow-lead-qualified","TASK_CREATED","CRM_TASK",taskId,null,
                        Map.of("outboxId",event,"executionId",execution),clock.instant());
        return new JdbcWorkflowProcessor(transactions,actors,task,taskAudit,clock,maxAttempts);
    }

    @Bean
    @ConditionalOnProperty(name="pose.workflow.worker.enabled", havingValue="true")
    JdbcConversationMessageProcessor conversationMessageProcessor(WorkflowTransactionRunner transactions,
            FoundationAuditRepository audit, Clock clock,
            @Value("${pose.workflow.worker.max-retry:3}") int maxAttempts,
            @Value("${pose.workflow.worker.due-days:1}") long dueDays) {
        WorkflowTaskAudit taskAudit = (connection, org, event, execution, taskId) ->
                audit.recordSystem(org, "workflow-conversation-message", "TASK_CREATED", "CRM_TASK", taskId, null,
                        Map.of("outboxId", event, "executionId", execution), clock.instant());
        return new JdbcConversationMessageProcessor(transactions, taskAudit, clock, maxAttempts, dueDays);
    }
}
