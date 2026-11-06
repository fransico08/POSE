package com.pose.workflow;

import org.junit.jupiter.api.Test;

class WorkflowWorkerJUnitTest {
    @Test void runsOriginalKernelScenariosWithAssertionsEnabled() {
        if (!WorkflowWorkerJUnitTest.class.desiredAssertionStatus()) {
            throw new IllegalStateException("The original scenarios require Java assertions");
        }
        WorkflowWorkerTest.main(new String[0]);
    }
}
