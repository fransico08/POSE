package com.pose.backend.workflow;

import com.pose.workflow.jdbc.JdbcWorkflowProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="pose.workflow.worker.enabled", havingValue="true")
public class WorkflowScheduler {
    private final JdbcWorkflowProcessor processor;
    private final int batchSize;

    public WorkflowScheduler(JdbcWorkflowProcessor processor, @Value("${pose.workflow.worker.batch-size:20}") int batchSize) {
        this.processor = processor;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString="${pose.workflow.worker.interval-ms:60000}", initialDelayString="${pose.workflow.worker.interval-ms:60000}")
    public void process() { processor.processPendingBatch(batchSize); }
}
