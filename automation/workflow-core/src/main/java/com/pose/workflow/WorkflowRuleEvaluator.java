package com.pose.workflow;

/** Evaluates an event already read from the tenant-scoped transactional outbox. */
public interface WorkflowRuleEvaluator {
    WorkflowDecision evaluate(WorkflowEvent event, boolean leadIsQualified, boolean ownerIsEligible);
}
