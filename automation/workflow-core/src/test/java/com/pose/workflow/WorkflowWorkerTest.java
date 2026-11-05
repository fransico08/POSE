package com.pose.workflow;

import com.pose.workflow.jdbc.JdbcTransactionalOutboxPublisher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Dependency-free tests for the W08-01 kernel; execute with Java assertions enabled. */
public final class WorkflowWorkerTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-05T08:00:00Z"), ZoneOffset.UTC);

    public static void main(String[] args) {
        rejectsInvalidRuleInput();
        createsOneTaskAndPublishes();
        replayDoesNotCreateAnotherTask();
        boundsTransientRetries();
        rejectsTenantOrOwnerMismatch();
        keepsOutboxPublisherKeyStable();
        System.out.println("WorkflowWorkerTest: PASS");
    }

    private static void rejectsInvalidRuleInput() {
        QualifiedLeadRuleEvaluator rules = new QualifiedLeadRuleEvaluator();
        WorkflowEvent event = event("OTHER_EVENT", 0);
        assert !rules.evaluate(event, true, true).shouldCreateTask();
        assert !rules.evaluate(event("LEAD_QUALIFIED", 0), false, true).shouldCreateTask();
    }

    private static void createsOneTaskAndPublishes() {
        Fixture fixture = new Fixture();
        fixture.worker().processBatch(10);
        assert fixture.tasks.createCalls == 1;
        assert fixture.outbox.published == 1;
        assert fixture.executions.done == 1;
        assert fixture.tasks.commands.get(0).idempotencyKey().equals(WorkflowIdempotency.key(fixture.event.id()));
    }

    private static void replayDoesNotCreateAnotherTask() {
        Fixture fixture = new Fixture();
        fixture.worker().processBatch(10);
        fixture.outbox.pending.add(fixture.event);
        fixture.worker().processBatch(10);
        assert fixture.tasks.createCalls == 1;
        assert fixture.outbox.published == 2;
    }

    private static void boundsTransientRetries() {
        Fixture fixture = new Fixture();
        fixture.tasks.failure = WorkflowFailure.transientFailure("TEMPORARY_DATABASE_FAILURE");
        fixture.worker().processBatch(10);
        assert fixture.outbox.rescheduled == 1;
        assert fixture.outbox.failed == 0;

        Fixture exhausted = new Fixture(event("LEAD_QUALIFIED", 2));
        exhausted.tasks.failure = WorkflowFailure.transientFailure("TEMPORARY_DATABASE_FAILURE");
        exhausted.worker().processBatch(10);
        assert exhausted.outbox.failed == 1;
        assert exhausted.outbox.rescheduled == 0;
    }

    private static void rejectsTenantOrOwnerMismatch() {
        Fixture fixture = new Fixture();
        fixture.subjects = new WorkflowPorts.SubjectVerifier() {
            @Override
            public WorkflowPorts.SubjectState load(UUID organizationId, UUID leadId, UUID ownerUserId) {
                return new WorkflowPorts.SubjectState(true, false);
            }
        };
        fixture.worker().processBatch(10);
        assert fixture.tasks.createCalls == 0;
        assert fixture.outbox.failed == 1;
    }

    private static void keepsOutboxPublisherKeyStable() {
        UUID organizationId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Instant qualifiedAt = Instant.parse("2026-11-05T07:00:00Z");
        LeadQualifiedTrigger first = new LeadQualifiedTrigger(organizationId, leadId, ownerId, qualifiedAt);
        LeadQualifiedTrigger replay = new LeadQualifiedTrigger(organizationId, leadId, ownerId, qualifiedAt);
        assert JdbcTransactionalOutboxPublisher.idempotencyKey(first)
                .equals(JdbcTransactionalOutboxPublisher.idempotencyKey(replay));
    }

    private static WorkflowEvent event(String type, int retries) {
        return new WorkflowEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), type,
                Instant.parse("2026-11-05T07:00:00Z"), retries);
    }

    private static final class Fixture {
        private final WorkflowEvent event;
        private final InMemoryOutbox outbox;
        private WorkflowPorts.SubjectVerifier subjects = (organizationId, leadId, ownerUserId) -> new WorkflowPorts.SubjectState(true, true);
        private final InMemoryExecutions executions = new InMemoryExecutions();
        private final InMemoryTasks tasks = new InMemoryTasks();

        private Fixture() {
            this(event("LEAD_QUALIFIED", 0));
        }

        private Fixture(WorkflowEvent event) {
            this.event = event;
            this.outbox = new InMemoryOutbox(event);
        }

        private WorkflowWorker worker() {
            return new WorkflowWorker(outbox, subjects, executions, tasks, new QualifiedLeadRuleEvaluator(), CLOCK, 3);
        }
    }

    private static final class InMemoryOutbox implements WorkflowPorts.OutboxStore {
        private final List<WorkflowEvent> pending = new ArrayList<>();
        private int published;
        private int rescheduled;
        private int failed;

        private InMemoryOutbox(WorkflowEvent event) {
            pending.add(event);
        }

        @Override
        public List<WorkflowEvent> claimPending(int batchSize) {
            int count = Math.min(batchSize, pending.size());
            List<WorkflowEvent> claimed = new ArrayList<>(pending.subList(0, count));
            pending.subList(0, count).clear();
            return claimed;
        }

        @Override public void markPublished(UUID organizationId, UUID eventId, Instant publishedAt) { published++; }
        @Override public void reschedule(UUID organizationId, UUID eventId, int retryCount, String error) { rescheduled++; }
        @Override public void markFailed(UUID organizationId, UUID eventId, int retryCount, String error) { failed++; }
    }

    private static final class InMemoryExecutions implements WorkflowPorts.ExecutionStore {
        private final Map<String, WorkflowPorts.Execution> values = new HashMap<>();
        private int done;

        @Override
        public WorkflowPorts.Execution startOrLoad(UUID organizationId, UUID outboxEventId, String workflowType,
                                                    String key, Instant startedAt) {
            return values.computeIfAbsent(organizationId + key, ignored -> new WorkflowPorts.Execution(UUID.randomUUID(), false));
        }

        @Override
        public void markDone(UUID organizationId, UUID executionId, UUID taskId, Instant finishedAt) {
            values.replaceAll((key, value) -> value.id().equals(executionId) ? new WorkflowPorts.Execution(executionId, true) : value);
            done++;
        }

        @Override public void recordRetry(UUID organizationId, UUID executionId, int retryCount, String error) { }
        @Override public void markFailed(UUID organizationId, UUID executionId, int retryCount, String error, Instant finishedAt) { }
    }

    private static final class InMemoryTasks implements WorkflowPorts.TaskGateway {
        private final Map<String, UUID> values = new HashMap<>();
        private final List<WorkflowTaskCommand> commands = new ArrayList<>();
        private WorkflowFailure failure;
        private int createCalls;

        @Override
        public UUID createLeadTaskIfAbsent(WorkflowTaskCommand command) {
            createCalls++;
            if (failure != null) {
                throw failure;
            }
            commands.add(command);
            return values.computeIfAbsent(command.organizationId() + command.idempotencyKey(), ignored -> UUID.randomUUID());
        }
    }
}
