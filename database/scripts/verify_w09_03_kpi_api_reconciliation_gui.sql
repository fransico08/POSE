-- POSE CRM W09-03: read-only KPI SQL/API reconciliation for PostgreSQL 18.
-- Run blocks independently in pgAdmin/SQLTools. Replace fixture params in EACH
-- block with the exact backend-authorized org/time inputs; never use client orgId
-- as authorization. No DDL, DML or temporary object is used.

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

-- S01 source readiness: catalog report for core sources, PK/FK and tenant/time columns.
SELECT c.relname AS table_name, a.attname AS column_name, pg_catalog.format_type(a.atttypid, a.atttypmod) AS data_type,
       CASE WHEN pk.attnum IS NOT NULL THEN 'PK' END AS primary_key,
       CASE WHEN fk.conname IS NOT NULL THEN fk.conname END AS foreign_key,
       CASE WHEN a.attname = 'organization_id' THEN 'TENANT_KEY'
            WHEN a.attname IN ('created_at','occurred_at','completed_at','due_at','finished_at','started_at') THEN 'TIMESTAMP'
            ELSE NULL END AS kpi_role
FROM pg_class AS c
JOIN pg_namespace AS n ON n.oid = c.relnamespace
JOIN pg_attribute AS a ON a.attrelid = c.oid AND a.attnum > 0 AND NOT a.attisdropped
LEFT JOIN LATERAL (
    SELECT u.attnum FROM pg_constraint AS con CROSS JOIN unnest(con.conkey) AS u(attnum)
    WHERE con.contype = 'p' AND con.conrelid = c.oid
) AS pk ON pk.attnum = a.attnum
LEFT JOIN LATERAL (
    SELECT con.conname FROM pg_constraint AS con CROSS JOIN unnest(con.conkey) AS u(attnum)
    WHERE con.contype = 'f' AND con.conrelid = c.oid AND u.attnum = a.attnum LIMIT 1
) AS fk ON TRUE
WHERE n.nspname = 'public'
  AND c.relname IN ('organizations','users','leads','customers','interactions','crm_tasks','outbox_events','workflow_executions')
ORDER BY c.relname, a.attnum;

-- S02 fixture params only. Replace in a copy; values are not production constants.
-- A01–A04 and A06 snapshot output with a single as_of, raw numeric values.
WITH params AS (
    SELECT '00000000-0000-0000-0000-000000000000'::uuid AS organization_id,
           '2026-09-01T00:00:00Z'::timestamptz AS period_start,
           '2026-10-01T00:00:00Z'::timestamptz AS period_end,
           '2026-10-01T00:00:00Z'::timestamptz AS as_of
), lead_total AS (
    SELECT count(*)::numeric AS value FROM leads AS l JOIN params AS p ON p.organization_id = l.organization_id
), lead_status AS (
    SELECT l.status::text AS dimension, count(*)::numeric AS value
    FROM leads AS l JOIN params AS p ON p.organization_id = l.organization_id GROUP BY l.status
), customer_segment AS (
    SELECT CASE WHEN max(i.occurred_at) IS NULL THEN 'NEVER_ENGAGED'
                WHEN max(i.occurred_at) > p.as_of - interval '30 days' THEN 'ACTIVE'
                WHEN max(i.occurred_at) >= p.as_of - interval '60 days' THEN 'AT_RISK'
                ELSE 'INACTIVE' END AS dimension
    FROM customers AS c JOIN params AS p ON p.organization_id = c.organization_id
    LEFT JOIN interactions AS i ON i.organization_id = c.organization_id AND i.customer_id = c.id
    GROUP BY c.id, p.as_of
), output AS (
    SELECT 'A01_TOTAL_LEADS'::text AS metric_id, 'ALL'::text AS dimension, total.value AS sql_value,
           'COUNT'::text AS unit, 'AVAILABLE_SNAPSHOT'::text AS availability FROM lead_total AS total
    UNION ALL SELECT 'A02_LEAD_STATUS_DISTRIBUTION', s.dimension, s.value, 'COUNT',
           CASE WHEN t.value = 0 THEN 'NOT_AVAILABLE_ZERO_DENOMINATOR' ELSE 'AVAILABLE_SNAPSHOT' END FROM lead_status AS s CROSS JOIN lead_total AS t
    UNION ALL SELECT 'A03_QUALIFIED_LEAD_RATE', 'QUALIFIED',
           CASE WHEN t.value = 0 THEN NULL ELSE COALESCE(sum(s.value) FILTER (WHERE s.dimension = 'QUALIFIED'),0) / t.value END, 'RATIO',
           CASE WHEN t.value = 0 THEN 'NOT_AVAILABLE_ZERO_DENOMINATOR' ELSE 'AVAILABLE_SNAPSHOT' END FROM lead_total AS t LEFT JOIN lead_status AS s ON TRUE GROUP BY t.value
    UNION ALL SELECT 'A04_LEAD_TO_CUSTOMER_CONVERSION', 'CONVERTED_CUSTOMER_REFERENCE',
           CASE WHEN t.value = 0 THEN NULL ELSE count(l.id) FILTER (WHERE l.converted_customer_id IS NOT NULL)::numeric / t.value END, 'RATIO',
           CASE WHEN t.value = 0 THEN 'NOT_AVAILABLE_ZERO_DENOMINATOR' ELSE 'AVAILABLE_SNAPSHOT_ONLY' END
           FROM lead_total AS t CROSS JOIN params AS p
           LEFT JOIN leads AS l ON l.organization_id = p.organization_id GROUP BY t.value
    UNION ALL SELECT 'A06_CUSTOMER_ENGAGEMENT_SEGMENTS', s.dimension, count(*)::numeric, 'COUNT', 'AVAILABLE_SNAPSHOT' FROM customer_segment AS s GROUP BY s.dimension
)
SELECT o.metric_id, p.organization_id, o.dimension, o.sql_value, o.unit, o.availability,
       p.as_of AS as_of, p.as_of AS generated_at, NULL::timestamptz AS period_start, NULL::timestamptz AS period_end
FROM output AS o CROSS JOIN params AS p ORDER BY o.metric_id, o.dimension;

-- S03 B01–B05: task snapshot/period outputs. Priority and owner buckets reconcile dynamically.
WITH params AS (
    SELECT '00000000-0000-0000-0000-000000000000'::uuid AS organization_id,
           '2026-09-01T00:00:00Z'::timestamptz AS period_start,
           '2026-10-01T00:00:00Z'::timestamptz AS period_end,
           '2026-10-01T00:00:00Z'::timestamptz AS as_of
), tasks AS (
    SELECT t.* FROM crm_tasks AS t JOIN params AS p ON p.organization_id = t.organization_id
), output AS (
    SELECT 'B01_OPEN_TASKS'::text AS metric_id, 'OPEN_OR_IN_PROGRESS'::text AS dimension,
           count(*) FILTER (WHERE status IN ('OPEN','IN_PROGRESS'))::numeric AS sql_value, 'COUNT'::text AS unit, 'AVAILABLE_SNAPSHOT'::text AS availability FROM tasks
    UNION ALL SELECT 'B02_OVERDUE_OPEN_TASKS','OPEN_OR_IN_PROGRESS',
           count(*) FILTER (WHERE status IN ('OPEN','IN_PROGRESS') AND due_at < p.as_of)::numeric,'COUNT','AVAILABLE_SNAPSHOT' FROM tasks CROSS JOIN params AS p
    UNION ALL SELECT 'B03_TASK_PRIORITY_DISTRIBUTION', priority || ':' || status, count(*)::numeric,'COUNT','AVAILABLE_SNAPSHOT' FROM tasks GROUP BY priority,status
    UNION ALL SELECT 'B04_TASKS_PER_USER', owner_user_id::text, count(*)::numeric,'COUNT','AVAILABLE_SNAPSHOT_OWNER_REQUIRED_BY_SCHEMA' FROM tasks GROUP BY owner_user_id
    UNION ALL SELECT 'B05_ON_TIME_COMPLETION_RATE','DONE_COMPLETED',
           CASE WHEN count(*) FILTER (WHERE status='DONE' AND completed_at IS NOT NULL AND completed_at >= p.period_start AND completed_at < p.period_end)=0 THEN NULL
                ELSE count(*) FILTER (WHERE status='DONE' AND completed_at <= due_at AND completed_at >= p.period_start AND completed_at < p.period_end)::numeric /
                     count(*) FILTER (WHERE status='DONE' AND completed_at IS NOT NULL AND completed_at >= p.period_start AND completed_at < p.period_end) END,'RATIO',
           CASE WHEN count(*) FILTER (WHERE status='DONE' AND completed_at IS NOT NULL AND completed_at >= p.period_start AND completed_at < p.period_end)=0 THEN 'NOT_AVAILABLE_ZERO_DENOMINATOR' ELSE 'AVAILABLE' END
           FROM tasks CROSS JOIN params AS p
)
SELECT o.metric_id, p.organization_id, o.dimension, o.sql_value, o.unit, o.availability,
       p.as_of AS as_of, p.as_of AS generated_at,
       p.period_start AS period_start, p.period_end AS period_end
FROM output AS o CROSS JOIN params AS p ORDER BY o.metric_id,o.dimension;

-- S04 C01–C03 and D01–D02: terminal workflow and period interaction output.
WITH params AS (
    SELECT '00000000-0000-0000-0000-000000000000'::uuid AS organization_id,
           '2026-09-01T00:00:00Z'::timestamptz AS period_start,
           '2026-10-01T00:00:00Z'::timestamptz AS period_end,
           '2026-10-01T00:00:00Z'::timestamptz AS as_of
), terminal AS (
    SELECT w.* FROM workflow_executions AS w JOIN params AS p ON p.organization_id=w.organization_id
    WHERE w.status IN ('DONE','FAILED') AND w.finished_at >= p.period_start AND w.finished_at < p.period_end
), output AS (
    SELECT 'C01_TERMINAL_WORKFLOW_SUCCESS_RATE'::text AS metric_id, workflow_type AS dimension,
           CASE WHEN count(*)=0 THEN NULL ELSE count(*) FILTER (WHERE status='DONE')::numeric/count(*) END AS sql_value,'RATIO'::text AS unit,
           CASE WHEN count(*)=0 THEN 'NOT_AVAILABLE_ZERO_DENOMINATOR' ELSE 'AVAILABLE' END AS availability FROM terminal GROUP BY workflow_type
    UNION ALL SELECT 'C02_FAILED_EXECUTIONS_AND_RETRY',
           workflow_type || ':retry=' || retry_count::text || ':error_present=' || (last_error IS NOT NULL)::text,
           count(*)::numeric,'COUNT','AVAILABLE' FROM terminal WHERE status='FAILED' GROUP BY workflow_type,retry_count
           , (last_error IS NOT NULL)
    UNION ALL SELECT 'C03_WORKFLOW_CYCLE_TIME', workflow_type,
           avg(extract(epoch FROM finished_at-started_at)) FILTER (WHERE finished_at>=started_at),'SECONDS',
           CASE WHEN count(*) FILTER (WHERE finished_at>=started_at)=0 THEN 'NOT_AVAILABLE_NO_VALID_INTERVAL' ELSE 'AVAILABLE' END FROM terminal GROUP BY workflow_type
    UNION ALL SELECT 'D01_INTERACTIONS_BY_CHANNEL', i.channel, count(*)::numeric,'COUNT','AVAILABLE'
           FROM interactions AS i JOIN params AS p ON p.organization_id=i.organization_id WHERE i.occurred_at>=p.period_start AND i.occurred_at<p.period_end GROUP BY i.channel
    UNION ALL SELECT 'D02_INTERACTIONS_BY_ORG_USER', i.recorded_by_user_id::text, count(*)::numeric,'COUNT','AVAILABLE'
           FROM interactions AS i JOIN params AS p ON p.organization_id=i.organization_id WHERE i.occurred_at>=p.period_start AND i.occurred_at<p.period_end GROUP BY i.recorded_by_user_id
)
SELECT o.metric_id, p.organization_id, o.dimension, o.sql_value, o.unit, o.availability,
       p.as_of AS as_of, p.as_of AS generated_at,
       p.period_start AS period_start, p.period_end AS period_end
FROM output AS o CROSS JOIN params AS p ORDER BY o.metric_id,o.dimension;

-- S05 dynamic reconciliation and persisted-state integrity. Zero violation means only persisted-state PASS.
WITH checks AS (
    SELECT 'lead_status_partition'::text AS check_name, count(*) FILTER (WHERE source_total<>bucket_total)::bigint AS violation_count
    FROM (SELECT organization_id,count(*) AS source_total,(SELECT sum(n) FROM (SELECT count(*) AS n FROM leads x WHERE x.organization_id=l.organization_id GROUP BY status) b) AS bucket_total FROM leads l GROUP BY organization_id) q
    UNION ALL SELECT 'task_owner_tenant_mismatch',count(*) FROM crm_tasks t LEFT JOIN users u ON u.id=t.owner_user_id AND u.organization_id=t.organization_id WHERE u.id IS NULL
    UNION ALL SELECT 'interaction_recorder_tenant_mismatch',count(*) FROM interactions i LEFT JOIN users u ON u.id=i.recorded_by_user_id AND u.organization_id=i.organization_id WHERE u.id IS NULL
    UNION ALL SELECT 'workflow_outbox_tenant_mismatch',count(*) FROM workflow_executions w LEFT JOIN outbox_events o ON o.id=w.outbox_event_id AND o.organization_id=w.organization_id WHERE w.outbox_event_id IS NOT NULL AND o.id IS NULL
    UNION ALL SELECT 'task_duplicate_idempotency',count(*) FROM (SELECT organization_id,idempotency_key FROM crm_tasks WHERE idempotency_key IS NOT NULL GROUP BY organization_id,idempotency_key HAVING count(*)>1) d
    UNION ALL SELECT 'outbox_duplicate_idempotency',count(*) FROM (SELECT organization_id,idempotency_key FROM outbox_events GROUP BY organization_id,idempotency_key HAVING count(*)>1) d
    UNION ALL SELECT 'invalid_lead_status',count(*) FROM leads WHERE status NOT IN ('NEW','QUALIFIED','CONVERTED','DISQUALIFIED') OR status IS NULL
    UNION ALL SELECT 'invalid_task_status',count(*) FROM crm_tasks WHERE status NOT IN ('OPEN','IN_PROGRESS','DONE','CANCELLED') OR status IS NULL
    UNION ALL SELECT 'invalid_workflow_status',count(*) FROM workflow_executions WHERE status NOT IN ('PENDING','RUNNING','DONE','FAILED') OR status IS NULL
    UNION ALL SELECT 'duplicate_workflow_idempotency',count(*) FROM (SELECT organization_id,idempotency_key FROM workflow_executions GROUP BY organization_id,idempotency_key HAVING count(*)>1) d
)
SELECT check_name,violation_count,CASE WHEN violation_count=0 THEN 'PASS_PERSISTED_STATE' ELSE 'FAIL' END AS result FROM checks ORDER BY check_name;

COMMIT;
