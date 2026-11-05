package com.pose.workflow;

/**
 * Spring/JDBC implementation inserts the outbox row in the caller's transaction.
 * The caller must supply facts from the locked Lead record, never HTTP payload data.
 */
public interface TransactionalOutboxPublisher {
    void publishLeadQualified(LeadQualifiedTrigger trigger);
}
