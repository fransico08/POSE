-- POSE CRM W09-01 KPI reconciliation, PostgreSQL 18, read-only.
-- Run each block independently in pgAdmin 4/SQLTools. Replace params only in a copy.

BEGIN READ ONLY;

DO $$
BEGIN
    IF current_database() <> 'pose_crm_v2' THEN
        RAISE EXCEPTION 'Expected database pose_crm_v2; connected to %', current_database();
    END IF;
    IF current_setting('server_encoding') <> 'UTF8' THEN
        RAISE EXCEPTION 'Expected UTF8 server encoding; found %', current_setting('server_encoding');
    END IF;
END $$;

-- A01–A04: Lead snapshot. Conversion is verified by converted_customer_id, not QUALIFIED status.
WITH params AS (SELECT clock_timestamp() AS as_of), lead_totals AS (
    SELECT organization_id, count(*)::numeric AS total_leads
    FROM leads
    GROUP BY organization_id
), lead_status AS (
    SELECT organization_id, status, count(*)::numeric AS value
    FROM leads
    GROUP BY organization_id, status
)
SELECT 'A01_TOTAL_LEADS' AS metric_id,
       total.organization_id,
       'ALL' AS dimension,
       params.as_of AS period_or_as_of,
       total.total_leads AS value,
       'AVAILABLE_SNAPSHOT' AS availability
FROM lead_totals AS total
CROSS JOIN params
UNION ALL
SELECT 'A02_LEAD_STATUS_DISTRIBUTION',
       status.organization_id,
       status.status,
       params.as_of,
       status.value,
       CASE WHEN total.total_leads = 0 THEN 'NOT_AVAILABLE_ZERO_DENOMINATOR' ELSE 'AVAILABLE_SNAPSHOT' END
FROM lead_status AS status
JOIN lead_totals AS total ON total.organization_id = status.organization_id
 CROSS JOIN params
UNION ALL
SELECT 'A03_QUALIFIED_LEAD_RATE',
       total.organization_id,
       'QUALIFIED',
       params.as_of,
       CASE WHEN total.total_leads = 0 THEN NULL ELSE COALESCE(qualified.value, 0) / total.total_leads END,
       CASE WHEN total.total_leads = 0 THEN 'NOT_AVAILABLE_ZERO_DENOMINATOR' ELSE 'AVAILABLE_SNAPSHOT' END
FROM lead_totals AS total
LEFT JOIN lead_status AS qualified
  ON qualified.organization_id = total.organization_id
 AND qualified.status = 'QUALIFIED'
 CROSS JOIN params
UNION ALL
SELECT 'A04_LEAD_TO_CUSTOMER_CONVERSION',
       total.organization_id,
       'CONVERTED_CUSTOMER_REFERENCE',
       params.as_of,
       CASE WHEN total.total_leads = 0 THEN NULL
            ELSE count(lead.id) FILTER (WHERE lead.converted_customer_id IS NOT NULL)::numeric / total.total_leads END,
       CASE WHEN total.total_leads = 0 THEN 'NOT_AVAILABLE_ZERO_DENOMINATOR' ELSE 'AVAILABLE_SNAPSHOT_ONLY' END
FROM lead_totals AS total
LEFT JOIN leads AS lead ON lead.organization_id = total.organization_id
 CROSS JOIN params
GROUP BY total.organization_id, total.total_leads
       , params.as_of
ORDER BY metric_id, organization_id, dimension;

-- A05: First response uses only a linked Lead interaction with occurred_at >= Lead created_at.
WITH params AS (
    SELECT captured_at AS as_of,
           captured_at - interval '30 days' AS period_start
    FROM (SELECT clock_timestamp() AS captured_at) AS captured
), first_response AS (
    SELECT lead.organization_id,
           lead.id AS lead_id,
           lead.created_at,
           min(interaction.occurred_at) FILTER (WHERE interaction.occurred_at >= lead.created_at) AS first_response_at
    FROM leads AS lead
    CROSS JOIN params
    LEFT JOIN interactions AS interaction
      ON interaction.organization_id = lead.organization_id
     AND interaction.lead_id = lead.id
     AND interaction.occurred_at >= params.period_start
     AND interaction.occurred_at < params.as_of
    WHERE lead.created_at >= params.period_start
      AND lead.created_at < params.as_of
    GROUP BY lead.organization_id, lead.id, lead.created_at
)
SELECT 'A05_FIRST_RESPONSE_TIME' AS metric_id,
       organization.id AS organization_id,
       'RESPONDED' AS dimension,
       params.as_of AS period_or_as_of,
       avg(extract(epoch FROM first_response.first_response_at - first_response.created_at)) AS value,
       CASE
           WHEN count(first_response.lead_id) = 0 THEN 'NO_DATA_IN_PERIOD'
           WHEN count(first_response.first_response_at) = 0 THEN 'NOT_AVAILABLE_NO_RESPONSES'
           ELSE 'AVAILABLE_SECONDS'
       END AS availability
FROM organizations AS organization
CROSS JOIN params
LEFT JOIN first_response ON first_response.organization_id = organization.id
GROUP BY organization.id, params.as_of
UNION ALL
SELECT 'A05_FIRST_RESPONSE_TIME',
       organization.id,
       'NO_RESPONSE',
       params.as_of,
       count(first_response.lead_id) FILTER (WHERE first_response.first_response_at IS NULL),
       CASE WHEN count(first_response.lead_id) = 0 THEN 'NO_DATA_IN_PERIOD' ELSE 'INFO_COUNT' END
FROM organizations AS organization
CROSS JOIN params
LEFT JOIN first_response ON first_response.organization_id = organization.id
GROUP BY organization.id, params.as_of
ORDER BY organization_id, dimension;

-- A06: one current Customer belongs to exactly one engagement segment based on direct Interaction history.
WITH params AS (SELECT clock_timestamp() AS as_of), customer_activity AS (
    SELECT customer.organization_id,
           customer.id,
           max(interaction.occurred_at) AS last_interaction_at
    FROM customers AS customer
    LEFT JOIN interactions AS interaction
      ON interaction.organization_id = customer.organization_id
     AND interaction.customer_id = customer.id
    GROUP BY customer.organization_id, customer.id
), segments AS (
    SELECT activity.organization_id,
           CASE
               WHEN activity.last_interaction_at IS NULL THEN 'NEVER_ENGAGED'
               WHEN activity.last_interaction_at > params.as_of - interval '30 days' THEN 'ACTIVE'
               WHEN activity.last_interaction_at >= params.as_of - interval '60 days' THEN 'AT_RISK'
               ELSE 'INACTIVE'
           END AS segment
    FROM customer_activity AS activity
    CROSS JOIN params
)
SELECT 'A06_CUSTOMER_ENGAGEMENT_SEGMENTS' AS metric_id,
       organization_id,
       segment AS dimension,
       params.as_of AS period_or_as_of,
       count(*) AS value,
       'AVAILABLE_SNAPSHOT' AS availability
FROM segments
 CROSS JOIN params
GROUP BY organization_id, segment
       , params.as_of
ORDER BY organization_id, segment;

-- B01–B05: Task snapshot and completed_at period. OPEN/IN_PROGRESS are schema-defined open states.
WITH params AS (
    SELECT captured_at AS as_of,
           captured_at - interval '30 days' AS period_start
    FROM (SELECT clock_timestamp() AS captured_at) AS captured
), task_totals AS (
    SELECT organization_id, count(*)::numeric AS total_tasks
    FROM crm_tasks
    GROUP BY organization_id
), task_status AS (
    SELECT organization_id, status, count(*)::numeric AS value
    FROM crm_tasks
    GROUP BY organization_id, status
), completed AS (
    SELECT task.organization_id,
           count(*) FILTER (WHERE task.completed_at <= task.due_at)::numeric AS on_time_count,
           count(*)::numeric AS completed_count
    FROM crm_tasks AS task
    CROSS JOIN params
    WHERE task.status = 'DONE'
      AND task.completed_at IS NOT NULL
      AND task.completed_at >= params.period_start
      AND task.completed_at < params.as_of
    GROUP BY task.organization_id
)
SELECT 'B01_OPEN_TASKS' AS metric_id,
       total.organization_id,
       'OPEN_OR_IN_PROGRESS' AS dimension,
       params.as_of AS period_or_as_of,
       COALESCE(sum(status.value) FILTER (WHERE status.status IN ('OPEN', 'IN_PROGRESS')), 0) AS value,
       'AVAILABLE_SNAPSHOT' AS availability
FROM task_totals AS total
LEFT JOIN task_status AS status ON status.organization_id = total.organization_id
 CROSS JOIN params
GROUP BY total.organization_id, params.as_of
UNION ALL
SELECT 'B02_OVERDUE_OPEN_TASKS',
       organization.id,
       'OPEN_OR_IN_PROGRESS',
       params.as_of,
       count(task.id),
       'AVAILABLE_SNAPSHOT'
FROM organizations AS organization
CROSS JOIN params
LEFT JOIN crm_tasks AS task
  ON task.organization_id = organization.id
 AND task.status IN ('OPEN', 'IN_PROGRESS')
 AND task.due_at < params.as_of
GROUP BY organization.id, params.as_of
UNION ALL
SELECT 'B03_TASK_PRIORITY_DISTRIBUTION',
       task.organization_id,
       task.priority || ':' || task.status,
       params.as_of,
       count(*),
       'AVAILABLE_SNAPSHOT'
FROM crm_tasks AS task
 CROSS JOIN params
GROUP BY task.organization_id, task.priority, task.status, params.as_of
UNION ALL
SELECT 'B04_TASKS_PER_USER',
       task.organization_id,
       task.owner_user_id::text,
       params.as_of,
       count(*),
       'AVAILABLE_SNAPSHOT_OWNER_REQUIRED_BY_SCHEMA'
FROM crm_tasks AS task
 CROSS JOIN params
GROUP BY task.organization_id, task.owner_user_id, params.as_of
UNION ALL
SELECT 'B05_ON_TIME_COMPLETION_RATE',
       organization.id,
       'DONE_COMPLETED_AT',
       params.as_of,
       CASE WHEN COALESCE(completed.completed_count, 0) = 0 THEN NULL
            ELSE completed.on_time_count / completed.completed_count END,
       CASE WHEN COALESCE(completed.completed_count, 0) = 0 THEN 'NO_DATA_IN_PERIOD' ELSE 'AVAILABLE_PERIOD' END
FROM organizations AS organization
CROSS JOIN params
LEFT JOIN completed ON completed.organization_id = organization.id
ORDER BY metric_id, organization_id, dimension;

-- C01–C03: workflow terminal population; period is finished_at and malformed intervals are excluded.
WITH params AS (
    SELECT captured_at AS as_of,
           captured_at - interval '30 days' AS period_start
    FROM (SELECT clock_timestamp() AS captured_at) AS captured
), terminal AS (
    SELECT scope.organization_id,
           scope.workflow_type,
           count(execution.id) FILTER (WHERE execution.status = 'DONE')::numeric AS done_count,
           count(execution.id) FILTER (WHERE execution.status = 'FAILED')::numeric AS failed_count,
           avg(extract(epoch FROM execution.finished_at - execution.started_at)) FILTER (
               WHERE execution.finished_at >= execution.started_at
           ) AS average_cycle_seconds,
           count(execution.id) FILTER (WHERE execution.finished_at >= execution.started_at) AS cycle_eligible_count
    FROM (
        SELECT DISTINCT organization_id, workflow_type
        FROM workflow_executions
    ) AS scope
    CROSS JOIN params
    LEFT JOIN workflow_executions AS execution
      ON execution.organization_id = scope.organization_id
     AND execution.workflow_type = scope.workflow_type
     AND execution.finished_at >= params.period_start
     AND execution.finished_at < params.as_of
     AND execution.status IN ('DONE', 'FAILED')
    GROUP BY scope.organization_id, scope.workflow_type
)
SELECT 'C01_TERMINAL_WORKFLOW_SUCCESS_RATE' AS metric_id,
       organization_id,
       workflow_type AS dimension,
       params.as_of AS period_or_as_of,
       CASE WHEN done_count + failed_count = 0 THEN NULL ELSE done_count / (done_count + failed_count) END AS value,
       CASE WHEN done_count + failed_count = 0 THEN 'NOT_AVAILABLE_ZERO_DENOMINATOR' ELSE 'AVAILABLE_PERIOD' END AS availability
FROM terminal
CROSS JOIN params
UNION ALL
SELECT 'C02_FAILED_EXECUTIONS_AND_RETRY',
       organization_id,
       workflow_type || ':retry=' || retry_count,
       params.as_of,
       count(*),
       'AVAILABLE_PERIOD'
FROM workflow_executions AS execution
CROSS JOIN params
WHERE execution.status = 'FAILED'
  AND execution.finished_at >= params.period_start
  AND execution.finished_at < params.as_of
GROUP BY organization_id, workflow_type, retry_count, params.as_of
UNION ALL
SELECT 'C02_FAILED_EXECUTIONS_AND_RETRY',
       terminal.organization_id,
       terminal.workflow_type || ':NO_FAILED_EXECUTIONS_IN_PERIOD',
       params.as_of,
       0,
       'NO_DATA_IN_PERIOD'
FROM terminal
CROSS JOIN params
WHERE terminal.failed_count = 0
UNION ALL
SELECT 'C03_WORKFLOW_CYCLE_TIME',
       organization_id,
       workflow_type,
       params.as_of,
       average_cycle_seconds,
       CASE WHEN cycle_eligible_count = 0 THEN 'NOT_AVAILABLE_NO_VALID_INTERVAL' ELSE 'AVAILABLE_SECONDS' END
FROM terminal
CROSS JOIN params
ORDER BY metric_id, organization_id, dimension;

-- D01–D02: Interaction period uses occurred_at; channel is NOT NULL in V2 but UNKNOWN remains defensive.
WITH params AS (
    SELECT captured_at AS as_of,
           captured_at - interval '30 days' AS period_start
    FROM (SELECT clock_timestamp() AS captured_at) AS captured
)
SELECT 'D01_INTERACTIONS_BY_CHANNEL' AS metric_id,
       interaction.organization_id,
       COALESCE(NULLIF(interaction.channel, ''), 'UNKNOWN') AS dimension,
       params.as_of AS period_or_as_of,
       count(*) AS value,
       'AVAILABLE_PERIOD' AS availability
FROM interactions AS interaction
CROSS JOIN params
WHERE interaction.occurred_at >= params.period_start
  AND interaction.occurred_at < params.as_of
GROUP BY interaction.organization_id, COALESCE(NULLIF(interaction.channel, ''), 'UNKNOWN'), params.as_of
UNION ALL
SELECT 'D01_INTERACTIONS_BY_CHANNEL',
       organization.id,
       'NO_DATA',
       params.as_of,
       0,
       'NO_DATA_IN_PERIOD'
FROM organizations AS organization
CROSS JOIN params
WHERE NOT EXISTS (
    SELECT 1
    FROM interactions AS interaction
    WHERE interaction.organization_id = organization.id
      AND interaction.occurred_at >= params.period_start
      AND interaction.occurred_at < params.as_of
)
UNION ALL
SELECT 'D02_INTERACTIONS_BY_ORG_USER',
       interaction.organization_id,
       interaction.recorded_by_user_id::text,
       params.as_of,
       count(*),
       'AVAILABLE_PERIOD'
FROM interactions AS interaction
CROSS JOIN params
WHERE interaction.occurred_at >= params.period_start
  AND interaction.occurred_at < params.as_of
GROUP BY interaction.organization_id, interaction.recorded_by_user_id, params.as_of
UNION ALL
SELECT 'D02_INTERACTIONS_BY_ORG_USER',
       organization.id,
       'NO_DATA',
       params.as_of,
       0,
       'NO_DATA_IN_PERIOD'
FROM organizations AS organization
CROSS JOIN params
WHERE NOT EXISTS (
    SELECT 1
    FROM interactions AS interaction
    WHERE interaction.organization_id = organization.id
      AND interaction.occurred_at >= params.period_start
      AND interaction.occurred_at < params.as_of
)
ORDER BY metric_id, organization_id, dimension;

-- Reconciliation: all violation_count values should be zero. Empty metric populations are INFO elsewhere.
WITH checks AS (
    SELECT 'lead_status_missing_or_invalid' AS check_name, count(*)::bigint AS violation_count
    FROM leads
    WHERE status NOT IN ('NEW', 'QUALIFIED', 'CONVERTED', 'DISQUALIFIED') OR status IS NULL
    UNION ALL
    SELECT 'task_status_missing_or_invalid', count(*)
    FROM crm_tasks
    WHERE status NOT IN ('OPEN', 'IN_PROGRESS', 'DONE', 'CANCELLED') OR status IS NULL
    UNION ALL
    SELECT 'task_priority_missing', count(*) FROM crm_tasks WHERE priority IS NULL OR btrim(priority) = ''
    UNION ALL
    SELECT 'task_owner_tenant_mismatch', count(*)
    FROM crm_tasks AS task
    LEFT JOIN users AS owner_user ON owner_user.id = task.owner_user_id
    WHERE owner_user.id IS NULL OR owner_user.organization_id <> task.organization_id
    UNION ALL
    SELECT 'interaction_recorder_tenant_mismatch', count(*)
    FROM interactions AS interaction
    LEFT JOIN users AS recorder ON recorder.id = interaction.recorded_by_user_id
    WHERE recorder.id IS NULL OR recorder.organization_id <> interaction.organization_id
    UNION ALL
    SELECT 'customer_engagement_partition_violation', count(*)
    FROM (
        SELECT customer.organization_id, customer.id, count(*) AS bucket_count
        FROM customers AS customer
        GROUP BY customer.organization_id, customer.id
        HAVING count(*) <> 1
    ) AS invalid
    UNION ALL
    SELECT 'workflow_terminal_missing_finished_at', count(*)
    FROM workflow_executions
    WHERE status IN ('DONE', 'FAILED') AND finished_at IS NULL
    UNION ALL
    SELECT 'workflow_terminal_invalid_interval', count(*)
    FROM workflow_executions
    WHERE started_at IS NOT NULL AND finished_at IS NOT NULL AND finished_at < started_at
)
SELECT check_name,
       violation_count,
       CASE WHEN violation_count = 0 THEN 'PASS' ELSE 'FAIL' END AS result
FROM checks
ORDER BY check_name;

-- Explicit partition arithmetic, shown as INFO evidence rather than a hidden assumption.
WITH lead_total AS (
    SELECT organization_id, count(*) AS total_count FROM leads GROUP BY organization_id
), lead_status AS (
    SELECT organization_id, sum(status_count) AS partition_count
    FROM (SELECT organization_id, status, count(*) AS status_count FROM leads GROUP BY organization_id, status) AS grouped
    GROUP BY organization_id
), task_total AS (
    SELECT organization_id, count(*) AS total_count FROM crm_tasks GROUP BY organization_id
), task_status AS (
    SELECT organization_id, sum(status_count) AS partition_count
    FROM (SELECT organization_id, status, count(*) AS status_count FROM crm_tasks GROUP BY organization_id, status) AS grouped
    GROUP BY organization_id
), params AS (
    SELECT clock_timestamp() AS as_of
), customer_activity AS (
    SELECT customer.organization_id,
           customer.id,
           max(interaction.occurred_at) AS last_interaction_at
    FROM customers AS customer
    LEFT JOIN interactions AS interaction
      ON interaction.organization_id = customer.organization_id
     AND interaction.customer_id = customer.id
    GROUP BY customer.organization_id, customer.id
), customer_segments AS (
    SELECT activity.organization_id,
           activity.id,
           CASE
               WHEN activity.last_interaction_at IS NULL THEN 'NEVER_ENGAGED'
               WHEN activity.last_interaction_at > params.as_of - interval '30 days' THEN 'ACTIVE'
               WHEN activity.last_interaction_at >= params.as_of - interval '60 days' THEN 'AT_RISK'
               ELSE 'INACTIVE'
           END AS segment
    FROM customer_activity AS activity
    CROSS JOIN params
)
SELECT 'lead_status_partition' AS check_name,
       lead_total.organization_id,
       lead_total.total_count AS source_total,
       lead_status.partition_count AS bucket_total,
       CASE WHEN lead_total.total_count = lead_status.partition_count THEN 'PASS' ELSE 'FAIL' END AS result
FROM lead_total JOIN lead_status USING (organization_id)
UNION ALL
SELECT 'task_status_partition',
       task_total.organization_id,
       task_total.total_count,
       task_status.partition_count,
       CASE WHEN task_total.total_count = task_status.partition_count THEN 'PASS' ELSE 'FAIL' END
FROM task_total JOIN task_status USING (organization_id)
UNION ALL
SELECT 'customer_engagement_partition',
       customer_total.organization_id,
       customer_total.total_count,
       customer_segment_total.bucket_total,
       CASE WHEN customer_total.total_count = customer_segment_total.bucket_total THEN 'PASS' ELSE 'FAIL' END
FROM (
    SELECT organization_id, count(*) AS total_count
    FROM customer_activity
    GROUP BY organization_id
) AS customer_total
JOIN (
    SELECT organization_id, count(*) AS bucket_total
    FROM customer_segments
    GROUP BY organization_id
) AS customer_segment_total USING (organization_id)
ORDER BY check_name, organization_id;

-- Metadata only: unavailable future metrics are not calculated from proxy CRM fields.
SELECT metric_id, availability, reason
FROM (
    VALUES
        ('FUTURE_RFM_MONETARY', 'NOT_AVAILABLE', 'No transaction or revenue source table'),
        ('FUTURE_PREDICTIVE_CLV', 'NOT_AVAILABLE', 'No value history, label, or prediction contract'),
        ('FUTURE_PERSONALIZED_NBA_EVALUATION', 'NOT_AVAILABLE', 'No consent/action/outcome/model-version tracking')
) AS unavailable(metric_id, availability, reason);

COMMIT;
