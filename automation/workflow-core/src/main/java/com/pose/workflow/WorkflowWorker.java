package com.pose.workflow;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Bounded asynchronous batch processor; scheduling and JDBC transactions belong to its adapter. */
public final class WorkflowWorker {
    private static final String WORKFLOW_TYPE = "LEAD_QUALIFIED_FOLLOW_UP";

    private final WorkflowPorts.OutboxStore outbox;
    private final WorkflowPorts.SubjectVerifier subjects;
    private final WorkflowPorts.ExecutionStore executions;
    private final WorkflowPorts.TaskGateway tasks;
    private final WorkflowRuleEvaluator rules;
    private final Clock clock;
    private final int maxAttempts;

    public WorkflowWorker(WorkflowPorts.OutboxStore outbox, WorkflowPorts.SubjectVerifier subjects,
                          WorkflowPorts.ExecutionStore executions, WorkflowPorts.TaskGateway tasks,
                          WorkflowRuleEvaluator rules, Clock clock, int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least one");
        }
        this.outbox = outbox;
        this.subjects = subjects;
        this.executions = executions;
        this.tasks = tasks;
        this.rules = rules;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
    }

    public int processBatch(int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be at least one");
        }
        List<WorkflowEvent> events = outbox.claimPending(batchSize);
        for (WorkflowEvent event : events) {
            process(event);
        }
        return events.size();
    }

    private void process(WorkflowEvent event) {
        String key = WorkflowIdempotency.key(event.id());
        Instant now = clock.instant();
        WorkflowPorts.Execution execution = executions.startOrLoad(
                event.organizationId(), event.id(), WORKFLOW_TYPE, key, now);
        if (execution.done()) {
            outbox.markPublished(event.organizationId(), event.id(), now);
            return;
        }

        try {
            WorkflowPorts.SubjectState state = subjects.load(event.organizationId(), event.leadId(), event.ownerUserId());
            WorkflowDecision decision = rules.evaluate(event, state.qualified(), state.ownerEligible());
            if (!decision.shouldCreateTask()) {
                failPermanently(event, execution.id(), decision.rejectionReason());
                return;
            }
            UUID taskId = tasks.createLeadTaskIfAbsent(new WorkflowTaskCommand(
                    event.organizationId(), event.leadId(), event.ownerUserId(), decision.taskIntent(), key));
            executions.markDone(event.organizationId(), execution.id(), taskId, now);
            outbox.markPublished(event.organizationId(), event.id(), now);
        } catch (WorkflowFailure failure) {
            handleFailure(event, execution.id(), failure);
        } catch (RuntimeException unexpected) {
            handleFailure(event, execution.id(), WorkflowFailure.transientFailure("UNEXPECTED_WORKFLOW_FAILURE"));
        }
    }

    private void handleFailure(WorkflowEvent event, UUID executionId, WorkflowFailure failure) {
        int attempts = event.retryCount() + 1;
        if (failure.retryable() && attempts < maxAttempts) {
            executions.recordRetry(event.organizationId(), executionId, attempts, failure.getMessage());
            outbox.reschedule(event.organizationId(), event.id(), attempts, failure.getMessage());
            return;
        }
        failPermanently(event, executionId, failure.getMessage(), attempts);
    }

    private void failPermanently(WorkflowEvent event, UUID executionId, String error) {
        failPermanently(event, executionId, error, event.retryCount() + 1);
    }

    private void failPermanently(WorkflowEvent event, UUID executionId, String error, int attempts) {
        Instant now = clock.instant();
        executions.markFailed(event.organizationId(), executionId, attempts, error, now);
        outbox.markFailed(event.organizationId(), event.id(), attempts, error);
    }
}
