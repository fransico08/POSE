-- POSE CRM W08-02 read-only retry/idempotency evidence for PostgreSQL 18.
-- Run manually in pgAdmin 4 or VS Code SQLTools against pose_crm_v2.
-- A zero violation count validates persisted data only; it is not runtime QA evidence.

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

WITH checks AS (
    SELECT 'outbox_idempotency_duplicates' AS check_name, count(*)::bigint AS violation_count
    FROM (
        SELECT organization_id, idempotency_key
        FROM outbox_events
        GROUP BY organization_id, idempotency_key
        HAVING count(*) > 1
    ) AS duplicates
    UNION ALL
    SELECT 'execution_idempotency_duplicates', count(*)
    FROM (
        SELECT organization_id, idempotency_key
        FROM workflow_executions
        GROUP BY organization_id, idempotency_key
        HAVING count(*) > 1
    ) AS duplicates
    UNION ALL
    SELECT 'workflow_task_idempotency_duplicates', count(*)
    FROM (
        SELECT organization_id, idempotency_key
        FROM crm_tasks
        WHERE origin = 'WORKFLOW' AND idempotency_key IS NOT NULL
        GROUP BY organization_id, idempotency_key
        HAVING count(*) > 1
    ) AS duplicates
    UNION ALL
    SELECT 'execution_outbox_fk_or_tenant_mismatch', count(*)
    FROM workflow_executions AS execution
    LEFT JOIN outbox_events AS outbox ON outbox.id = execution.outbox_event_id
    WHERE execution.outbox_event_id IS NOT NULL
      AND (outbox.id IS NULL OR outbox.organization_id <> execution.organization_id)
    UNION ALL
    SELECT 'execution_task_fk_tenant_origin_or_key_mismatch', count(*)
    FROM workflow_executions AS execution
    LEFT JOIN crm_tasks AS task ON task.id = execution.created_task_id
    WHERE execution.created_task_id IS NOT NULL
      AND (task.id IS NULL
           OR task.organization_id <> execution.organization_id
           OR task.origin <> 'WORKFLOW'
           OR task.idempotency_key <> execution.idempotency_key)
    UNION ALL
    SELECT 'task_subject_or_user_tenant_mismatch', count(*)
    FROM crm_tasks AS task
    LEFT JOIN leads AS lead ON lead.id = task.lead_id
    LEFT JOIN users AS owner_user ON owner_user.id = task.owner_user_id
    LEFT JOIN users AS creator_user ON creator_user.id = task.created_by_user_id
    WHERE task.origin = 'WORKFLOW'
      AND (task.idempotency_key IS NULL
           OR lead.id IS NOT NULL AND lead.organization_id <> task.organization_id
           OR owner_user.id IS NULL OR owner_user.organization_id <> task.organization_id
           OR creator_user.id IS NULL OR creator_user.organization_id <> task.organization_id)
    UNION ALL
    SELECT 'lead_qualified_outbox_scope_mismatch', count(*)
    FROM outbox_events AS outbox
    LEFT JOIN leads AS lead ON lead.id = outbox.aggregate_id
    WHERE outbox.event_type = 'LEAD_QUALIFIED'
      AND (outbox.aggregate_type <> 'LEAD'
           OR lead.id IS NULL
           OR lead.organization_id <> outbox.organization_id)
    UNION ALL
    SELECT 'outbox_status_retry_timeline_invalid', count(*)
    FROM outbox_events
    WHERE delivery_status NOT IN ('PENDING', 'PUBLISHED', 'FAILED')
       OR retry_count < 0
       OR occurred_at IS NULL
       OR delivery_status = 'PUBLISHED' AND published_at IS NULL
       OR published_at IS NOT NULL AND published_at < occurred_at
    UNION ALL
    SELECT 'execution_status_retry_timeline_invalid', count(*)
    FROM workflow_executions
    WHERE status NOT IN ('PENDING', 'RUNNING', 'DONE', 'FAILED')
       OR retry_count < 0
       OR started_at IS NOT NULL AND finished_at IS NOT NULL AND finished_at < started_at
       OR status = 'DONE' AND finished_at IS NULL
       OR workflow_type = 'LEAD_QUALIFIED_FOLLOW_UP' AND status = 'DONE' AND created_task_id IS NULL
    UNION ALL
    SELECT 'published_qualified_outbox_without_done_execution', count(*)
    FROM outbox_events AS outbox
    LEFT JOIN workflow_executions AS execution
      ON execution.organization_id = outbox.organization_id
     AND execution.outbox_event_id = outbox.id
     AND execution.status = 'DONE'
    WHERE outbox.event_type = 'LEAD_QUALIFIED'
      AND outbox.delivery_status = 'PUBLISHED'
      AND execution.id IS NULL
)
SELECT check_name,
       violation_count,
       CASE WHEN violation_count = 0 THEN 'PASS' ELSE 'FAIL' END AS result
FROM checks
ORDER BY check_name;

-- Information only: capture these distributions before and after each runtime QA case.
SELECT 'outbox_status' AS information_type,
       delivery_status AS value,
       count(*) AS record_count,
       max(retry_count) AS maximum_retry_count
FROM outbox_events
GROUP BY delivery_status
UNION ALL
SELECT 'execution_status', status, count(*), max(retry_count)
FROM workflow_executions
GROUP BY status
UNION ALL
SELECT 'workflow_task_status', status, count(*), NULL::integer
FROM crm_tasks
WHERE origin = 'WORKFLOW'
GROUP BY status
ORDER BY information_type, value;

SELECT source.table_name,
       count(DISTINCT source.organization_id) AS organization_count,
       CASE WHEN count(DISTINCT source.organization_id) = 6 THEN 'PASS' ELSE 'INFO' END AS result
FROM (
    SELECT 'outbox_events' AS table_name, organization_id FROM outbox_events
    UNION ALL
    SELECT 'workflow_executions', organization_id FROM workflow_executions
    UNION ALL
    SELECT 'workflow_tasks', organization_id FROM crm_tasks WHERE origin = 'WORKFLOW'
) AS source
GROUP BY source.table_name
ORDER BY source.table_name;

COMMIT;
