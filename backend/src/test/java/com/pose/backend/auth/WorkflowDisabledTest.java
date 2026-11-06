package com.pose.backend.auth;

import com.pose.backend.workflow.WorkflowScheduler;
import com.pose.workflow.jdbc.JdbcWorkflowProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

class WorkflowDisabledTest extends FoundationTestSupport {
    @Autowired ApplicationContext context;

    @Test void schedulerAndProcessorAreAbsentByDefault() {
        assertThat(context.getBeansOfType(WorkflowScheduler.class)).isEmpty();
        assertThat(context.getBeansOfType(JdbcWorkflowProcessor.class)).isEmpty();
    }
}
