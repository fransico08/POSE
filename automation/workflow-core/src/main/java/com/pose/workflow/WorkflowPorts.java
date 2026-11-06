package com.pose.workflow;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Persistence boundaries to implement with tenant-scoped JDBC transactions on Tài's backend. */
public final class WorkflowPorts {
    private WorkflowPorts() {
    }

    public interface OutboxStore {
        /** Claim rows atomically (for example FOR UPDATE SKIP LOCKED), never by a read-then-update race. */
        List<WorkflowEvent> claimPending(int batchSize);

        void markPublished(UUID organizationId, UUID eventId, Instant publishedAt);

        void reschedule(UUID organizationId, UUID eventId, int retryCount, String error);

        void markFailed(UUID organizationId, UUID eventId, int retryCount, String error);
    }

    public interface SubjectVerifier {
        /** Must query Lead and owner under organizationId; payload fields are not trusted. */
        SubjectState load(UUID organizationId, UUID leadId, UUID ownerUserId);
    }

    public record SubjectState(boolean qualified, boolean ownerEligible) {
    }

    public interface ExecutionStore {
        /** INSERT ... ON CONFLICT scoped by organization_id and idempotency_key. */
        Execution startOrLoad(UUID organizationId, UUID outboxEventId, String workflowType, String idempotencyKey, Instant startedAt);

        void markDone(UUID organizationId, UUID executionId, UUID taskId, Instant finishedAt);

        void recordRetry(UUID organizationId, UUID executionId, int retryCount, String error);

        void markFailed(UUID organizationId, UUID executionId, int retryCount, String error, Instant finishedAt);
    }

    public record Execution(UUID id, boolean done) {
    }

    public interface TaskGateway {
        /** Must insert origin=WORKFLOW via DB uniqueness, never a SELECT-then-INSERT sequence. */
        UUID createLeadTaskIfAbsent(WorkflowTaskCommand command);
    }
}
