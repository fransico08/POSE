-- POSE CRM W08-03: monitoring workflow và Customer 360 read-only, PostgreSQL 18.
-- Chạy từng khối trong pgAdmin 4/SQLTools. Không có query nào gửi alert hay sửa dữ liệu.

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

-- A1. Operations: phân bố theo tenant; 0 row là INFO, không phải runtime PASS.
SELECT 'outbox' AS metric_group,
       organization_id,
       event_type AS metric_dimension,
       delivery_status AS status,
       count(*) AS record_count,
       max(retry_count) AS maximum_retry_count,
       CASE WHEN count(*) = 0 THEN 'INFO' ELSE 'OBSERVED' END AS result
FROM outbox_events
GROUP BY organization_id, event_type, delivery_status
UNION ALL
SELECT 'execution',
       organization_id,
       workflow_type,
       status,
       count(*),
       max(retry_count),
       CASE WHEN count(*) = 0 THEN 'INFO' ELSE 'OBSERVED' END
FROM workflow_executions
GROUP BY organization_id, workflow_type, status
ORDER BY metric_group, organization_id, metric_dimension, status;

-- KPI terminal: chỉ DONE/FAILED là mẫu số, nên chưa có terminal execution sẽ là NOT_AVAILABLE.
WITH terminal AS (
    SELECT organization_id,
           workflow_type,
           count(*) FILTER (WHERE status = 'DONE') AS done_count,
           count(*) FILTER (WHERE status = 'FAILED') AS failed_count
    FROM workflow_executions
    GROUP BY organization_id, workflow_type
)
SELECT organization_id,
       workflow_type,
       done_count,
       failed_count,
       done_count + failed_count AS terminal_execution_count,
       CASE
           WHEN done_count + failed_count = 0 THEN NULL
           ELSE round(done_count::numeric / (done_count + failed_count), 4)
       END AS success_rate,
       CASE
           WHEN done_count + failed_count = 0 THEN NULL
           ELSE round(failed_count::numeric / (done_count + failed_count), 4)
       END AS failure_rate,
       CASE WHEN done_count + failed_count = 0 THEN 'NOT_AVAILABLE_NO_TERMINAL_EXECUTION' ELSE 'OBSERVED' END AS result
FROM terminal
ORDER BY organization_id, workflow_type;

-- Ngưỡng 30 phút chỉ là cấu hình quan sát đề xuất; không phải SLA đã phê duyệt.
WITH thresholds AS (
    SELECT interval '30 minutes' AS pending_or_running_age
), aged AS (
    SELECT 'PENDING_OUTBOX' AS monitor_type,
           organization_id,
           id,
           occurred_at AS started_at,
           clock_timestamp() - occurred_at AS age
    FROM outbox_events
    WHERE delivery_status = 'PENDING'
    UNION ALL
    SELECT 'RUNNING_EXECUTION',
           organization_id,
           id,
           started_at,
           clock_timestamp() - started_at
    FROM workflow_executions
    WHERE status = 'RUNNING' AND started_at IS NOT NULL
), monitor_scope AS (
    SELECT organization.id AS organization_id, monitor_type
    FROM organizations AS organization
    CROSS JOIN (VALUES ('PENDING_OUTBOX'), ('RUNNING_EXECUTION')) AS types(monitor_type)
)
SELECT monitor_scope.monitor_type,
       monitor_scope.organization_id,
       count(aged.id) AS aged_record_count,
       max(aged.age) AS oldest_age,
       CASE
           WHEN count(aged.id) = 0 THEN 'INFO_NO_RECORDS'
           WHEN max(aged.age) > thresholds.pending_or_running_age THEN 'WARNING_CONFIGURED_THRESHOLD'
           ELSE 'INFO_BELOW_CONFIGURED_THRESHOLD'
       END AS result
FROM thresholds
JOIN monitor_scope ON TRUE
LEFT JOIN aged
  ON aged.monitor_type = monitor_scope.monitor_type
 AND aged.organization_id = monitor_scope.organization_id
GROUP BY monitor_scope.monitor_type, monitor_scope.organization_id, thresholds.pending_or_running_age
ORDER BY monitor_scope.monitor_type, monitor_scope.organization_id;

-- LEAD_QUALIFIED chỉ được link khi outbox/execution/task cùng tenant và key khớp.
SELECT outbox.organization_id,
       count(*) AS qualified_event_count,
       count(execution.id) AS execution_count,
       count(task.id) AS linked_workflow_task_count,
       CASE
           WHEN count(*) = 0 THEN 'INFO_NO_LEAD_QUALIFIED_EVENTS'
           WHEN count(execution.id) = 0 THEN 'NOT_RUN_OR_NOT_INTEGRATED'
           ELSE 'OBSERVED'
       END AS result
FROM outbox_events AS outbox
LEFT JOIN workflow_executions AS execution
  ON execution.organization_id = outbox.organization_id
 AND execution.outbox_event_id = outbox.id
LEFT JOIN crm_tasks AS task
  ON task.organization_id = execution.organization_id
 AND task.id = execution.created_task_id
 AND task.origin = 'WORKFLOW'
 AND task.idempotency_key = execution.idempotency_key
WHERE outbox.event_type = 'LEAD_QUALIFIED'
  AND outbox.aggregate_type = 'LEAD'
GROUP BY outbox.organization_id
ORDER BY outbox.organization_id;

-- Integrity: 0 means persisted-state PASS only, never evidence of worker/concurrency runtime.
WITH checks AS (
    SELECT 'duplicate_outbox_idempotency_per_tenant' AS check_name, count(*)::bigint AS violation_count
    FROM (
        SELECT organization_id, idempotency_key
        FROM outbox_events
        GROUP BY organization_id, idempotency_key
        HAVING count(*) > 1
    ) AS duplicate_rows
    UNION ALL
    SELECT 'duplicate_execution_idempotency_per_tenant', count(*)
    FROM (
        SELECT organization_id, idempotency_key
        FROM workflow_executions
        GROUP BY organization_id, idempotency_key
        HAVING count(*) > 1
    ) AS duplicate_rows
    UNION ALL
    SELECT 'duplicate_workflow_task_idempotency_per_tenant', count(*)
    FROM (
        SELECT organization_id, idempotency_key
        FROM crm_tasks
        WHERE origin = 'WORKFLOW' AND idempotency_key IS NOT NULL
        GROUP BY organization_id, idempotency_key
        HAVING count(*) > 1
    ) AS duplicate_rows
    UNION ALL
    SELECT 'execution_outbox_orphan_or_tenant_mismatch', count(*)
    FROM workflow_executions AS execution
    LEFT JOIN outbox_events AS outbox ON outbox.id = execution.outbox_event_id
    WHERE execution.outbox_event_id IS NOT NULL
      AND (outbox.id IS NULL OR outbox.organization_id <> execution.organization_id)
    UNION ALL
    SELECT 'execution_task_orphan_tenant_origin_or_key_mismatch', count(*)
    FROM workflow_executions AS execution
    LEFT JOIN crm_tasks AS task ON task.id = execution.created_task_id
    WHERE execution.created_task_id IS NOT NULL
      AND (task.id IS NULL
           OR task.organization_id <> execution.organization_id
           OR task.origin <> 'WORKFLOW'
           OR task.idempotency_key <> execution.idempotency_key)
    UNION ALL
    SELECT 'lead_qualified_done_execution_missing_task', count(*)
    FROM workflow_executions AS execution
    JOIN outbox_events AS outbox
      ON outbox.id = execution.outbox_event_id
     AND outbox.organization_id = execution.organization_id
    WHERE outbox.event_type = 'LEAD_QUALIFIED'
      AND execution.workflow_type = 'LEAD_QUALIFIED_FOLLOW_UP'
      AND execution.status = 'DONE'
      AND execution.created_task_id IS NULL
    UNION ALL
    SELECT 'workflow_task_owner_or_creator_tenant_mismatch', count(*)
    FROM crm_tasks AS task
    LEFT JOIN users AS owner_user ON owner_user.id = task.owner_user_id
    LEFT JOIN users AS creator_user ON creator_user.id = task.created_by_user_id
    WHERE task.origin = 'WORKFLOW'
      AND (owner_user.id IS NULL
           OR creator_user.id IS NULL
           OR owner_user.organization_id <> task.organization_id
           OR creator_user.organization_id <> task.organization_id)
)
SELECT check_name,
       violation_count,
       CASE WHEN violation_count = 0 THEN 'PASS_PERSISTED_DATA' ELSE 'FAIL' END AS result
FROM checks
ORDER BY check_name;

-- A2. Customer 360: đúng một row/customer. Task/interaction của Lead chỉ gộp khi Lead đã convert tới Customer.
WITH customer_subjects AS (
    SELECT customer.id AS customer_id, customer.organization_id, customer.id AS subject_customer_id, NULL::uuid AS subject_lead_id
    FROM customers AS customer
    UNION ALL
    SELECT customer.id, customer.organization_id, NULL::uuid, lead.id
    FROM customers AS customer
    JOIN leads AS lead
      ON lead.converted_customer_id = customer.id
     AND lead.organization_id = customer.organization_id
), interaction_activity AS (
    SELECT subject.customer_id,
           subject.organization_id,
           count(interaction.id) AS interaction_count,
           max(interaction.occurred_at) AS last_interaction_at
    FROM customer_subjects AS subject
    LEFT JOIN interactions AS interaction
      ON interaction.organization_id = subject.organization_id
     AND ((subject.subject_customer_id IS NOT NULL AND interaction.customer_id = subject.subject_customer_id)
       OR (subject.subject_lead_id IS NOT NULL AND interaction.lead_id = subject.subject_lead_id))
    GROUP BY subject.customer_id, subject.organization_id
), task_activity AS (
    SELECT subject.customer_id,
           subject.organization_id,
           count(task.id) AS task_count,
           count(task.id) FILTER (WHERE task.status IN ('OPEN', 'IN_PROGRESS')) AS open_task_count,
           count(task.id) FILTER (
               WHERE task.status IN ('OPEN', 'IN_PROGRESS') AND task.due_at < clock_timestamp()
           ) AS overdue_task_count,
           max(GREATEST(task.created_at, task.updated_at, task.completed_at)) AS last_task_activity_at
    FROM customer_subjects AS subject
    LEFT JOIN crm_tasks AS task
      ON task.organization_id = subject.organization_id
     AND ((subject.subject_customer_id IS NOT NULL AND task.customer_id = subject.subject_customer_id)
       OR (subject.subject_lead_id IS NOT NULL AND task.lead_id = subject.subject_lead_id))
    GROUP BY subject.customer_id, subject.organization_id
)
SELECT customer.id AS customer_id,
       customer.organization_id,
       COALESCE(interaction_activity.interaction_count, 0) AS interaction_count,
       interaction_activity.last_interaction_at,
       COALESCE(task_activity.task_count, 0) AS task_count,
       COALESCE(task_activity.open_task_count, 0) AS open_task_count,
       COALESCE(task_activity.overdue_task_count, 0) AS overdue_task_count,
       GREATEST(interaction_activity.last_interaction_at, task_activity.last_task_activity_at) AS last_activity_at
FROM customers AS customer
LEFT JOIN interaction_activity
  ON interaction_activity.customer_id = customer.id
 AND interaction_activity.organization_id = customer.organization_id
LEFT JOIN task_activity
  ON task_activity.customer_id = customer.id
 AND task_activity.organization_id = customer.organization_id
ORDER BY customer.organization_id, customer.id;

-- Lead grain riêng: chỉ Lead chưa convert, không UNION vào Customer 360.
WITH interaction_activity AS (
    SELECT lead.id AS lead_id,
           lead.organization_id,
           count(interaction.id) AS interaction_count,
           max(interaction.occurred_at) AS last_interaction_at
    FROM leads AS lead
    LEFT JOIN interactions AS interaction
      ON interaction.organization_id = lead.organization_id
     AND interaction.lead_id = lead.id
    WHERE lead.converted_customer_id IS NULL
    GROUP BY lead.id, lead.organization_id
), task_activity AS (
    SELECT lead.id AS lead_id,
           lead.organization_id,
           count(task.id) AS task_count,
           count(task.id) FILTER (WHERE task.status IN ('OPEN', 'IN_PROGRESS')) AS open_task_count,
           max(GREATEST(task.created_at, task.updated_at, task.completed_at)) AS last_task_activity_at
    FROM leads AS lead
    LEFT JOIN crm_tasks AS task
      ON task.organization_id = lead.organization_id
     AND task.lead_id = lead.id
    WHERE lead.converted_customer_id IS NULL
    GROUP BY lead.id, lead.organization_id
)
SELECT lead.id AS lead_id,
       lead.organization_id,
       lead.status AS lead_status,
       COALESCE(interaction_activity.interaction_count, 0) AS interaction_count,
       interaction_activity.last_interaction_at,
       COALESCE(task_activity.task_count, 0) AS task_count,
       COALESCE(task_activity.open_task_count, 0) AS open_task_count,
       GREATEST(interaction_activity.last_interaction_at, task_activity.last_task_activity_at) AS last_activity_at
FROM leads AS lead
LEFT JOIN interaction_activity
  ON interaction_activity.lead_id = lead.id
 AND interaction_activity.organization_id = lead.organization_id
LEFT JOIN task_activity
  ON task_activity.lead_id = lead.id
 AND task_activity.organization_id = lead.organization_id
WHERE lead.converted_customer_id IS NULL
ORDER BY lead.organization_id, lead.id;

-- A3. Readiness: không suy ra monetary/retention/consent/evaluation khi schema chưa có nguồn.
SELECT feature_area,
       source_availability,
       status,
       limitation
FROM (
    VALUES
        ('engagement_activity', 'interactions, crm_tasks', 'AVAILABLE_READ_ONLY', 'No channel/event taxonomy policy beyond stored rows'),
        ('workflow_reliability', 'outbox_events, workflow_executions, crm_tasks', 'AVAILABLE_READ_ONLY', 'Runtime worker evidence requires integrated backend'),
        ('monetary_transactions', NULL, 'NOT_AVAILABLE', 'No transaction/revenue table in V2 schema'),
        ('retention_label', NULL, 'NOT_AVAILABLE', 'No approved observation/prediction outcome label'),
        ('consent_preferences', NULL, 'NOT_AVAILABLE', 'No consent model in V2 schema'),
        ('model_evaluation_outcomes', NULL, 'FUTURE', 'No model_version/action/outcome tracking contract')
) AS readiness(feature_area, source_availability, status, limitation)
ORDER BY feature_area;

COMMIT;
