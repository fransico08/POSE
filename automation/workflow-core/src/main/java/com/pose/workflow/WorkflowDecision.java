package com.pose.workflow;

/** A pure rule-evaluation result; no persistence or remote call occurs here. */
public record WorkflowDecision(boolean shouldCreateTask, WorkflowTaskIntent taskIntent, String rejectionReason) {
    public static WorkflowDecision createLeadFollowUp() {
        return new WorkflowDecision(true, WorkflowTaskIntent.LEAD_QUALIFIED_FOLLOW_UP, null);
    }

    public static WorkflowDecision reject(String reason) {
        return new WorkflowDecision(false, null, reason);
    }
}
