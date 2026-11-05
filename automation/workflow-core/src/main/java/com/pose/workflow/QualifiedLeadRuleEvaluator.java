package com.pose.workflow;

/** W08-01's only supported workflow: a qualified Lead receives one follow-up Task. */
public final class QualifiedLeadRuleEvaluator implements WorkflowRuleEvaluator {
    public static final String EVENT_TYPE = "LEAD_QUALIFIED";

    @Override
    public WorkflowDecision evaluate(WorkflowEvent event, boolean leadIsQualified, boolean ownerIsEligible) {
        if (!EVENT_TYPE.equals(event.eventType())) {
            return WorkflowDecision.reject("UNSUPPORTED_EVENT_TYPE");
        }
        if (!leadIsQualified) {
            return WorkflowDecision.reject("LEAD_NOT_QUALIFIED");
        }
        if (!ownerIsEligible) {
            return WorkflowDecision.reject("OWNER_NOT_ELIGIBLE");
        }
        return WorkflowDecision.createLeadFollowUp();
    }
}
