-- POSE CRM W08-01 read-only workflow/outbox verification for PostgreSQL 18.
-- Run manually in pgAdmin 4 or VS Code SQLTools after Tài integrates the JDBC adapters.

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

-- Database uniqueness should prevent all of these groups from existing.
SELECT 'outbox_idempotency_duplicates' AS check_name, count(*) AS violation_count
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
) AS duplicates;

-- Tenant boundaries and completed workflow/task linkage.
SELECT 'execution_outbox_tenant_mismatch' AS check_name, count(*) AS violation_count
FROM workflow_executions AS execution
JOIN outbox_events AS outbox ON outbox.id = execution.outbox_event_id
WHERE outbox.organization_id <> execution.organization_id
UNION ALL
SELECT 'execution_task_tenant_or_origin_mismatch', count(*)
FROM workflow_executions AS execution
LEFT JOIN crm_tasks AS task ON task.id = execution.created_task_id
WHERE execution.created_task_id IS NOT NULL
  AND (task.id IS NULL OR task.organization_id <> execution.organization_id OR task.origin <> 'WORKFLOW')
UNION ALL
SELECT 'done_execution_missing_task', count(*)
FROM workflow_executions
WHERE status = 'DONE' AND created_task_id IS NULL
UNION ALL
SELECT 'published_qualified_outbox_without_done_execution', count(*)
FROM outbox_events AS outbox
LEFT JOIN workflow_executions AS execution
  ON execution.organization_id = outbox.organization_id
 AND execution.outbox_event_id = outbox.id
 AND execution.status = 'DONE'
WHERE outbox.event_type = 'LEAD_QUALIFIED'
  AND outbox.delivery_status = 'PUBLISHED'
  AND execution.id IS NULL;

-- The task key and execution key must agree for completed W08-01 runs.
SELECT 'done_execution_task_key_mismatch' AS check_name, count(*) AS violation_count
FROM workflow_executions AS execution
JOIN crm_tasks AS task ON task.id = execution.created_task_id
WHERE execution.status = 'DONE'
  AND (task.origin <> 'WORKFLOW' OR task.idempotency_key <> execution.idempotency_key);

-- Existing schema has only PENDING/PUBLISHED/FAILED and no persisted backoff/lease.
SELECT delivery_status, count(*) AS event_count, max(retry_count) AS maximum_retry_count
FROM outbox_events
GROUP BY delivery_status
ORDER BY delivery_status;

SELECT status, count(*) AS execution_count, max(retry_count) AS maximum_retry_count
FROM workflow_executions
GROUP BY status
ORDER BY status;

COMMIT;
