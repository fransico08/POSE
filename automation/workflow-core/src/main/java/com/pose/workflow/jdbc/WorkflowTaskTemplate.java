package com.pose.workflow.jdbc;

import com.pose.workflow.WorkflowEvent;

import java.time.Instant;

/** Approved business wording and due-date policy are supplied by the integrating backend. */
@FunctionalInterface
public interface WorkflowTaskTemplate {
    Details forQualifiedLead(WorkflowEvent event, Instant processingAt);

    record Details(String title, String description, String priority, Instant dueAt) {
        public Details {
            if (title == null || title.isBlank() || priority == null || priority.isBlank() || dueAt == null) {
                throw new IllegalArgumentException("Workflow task details are incomplete");
            }
        }
    }
}
