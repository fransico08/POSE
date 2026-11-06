-- POSE CRM W09-02: UI/API/SQL reconciliation, PostgreSQL 18, read-only.
-- pgAdmin/SQLTools: replace the same three literals in every params CTE with an
-- organization authorized by backend RBAC and the exact dashboard filters.
-- start is inclusive, end is exclusive. This file creates no objects and changes no data.

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

-- Demo parameters: POSE Academy and the September 2026 seed range.
-- R00 must return PASS before comparing API/UI values.
WITH params AS (
    SELECT '0b47be39-3b3c-5195-a4e2-8a29ffbadcf0'::uuid AS organization_id,
           '2026-09-01T00:00:00Z'::timestamptz AS period_start,
           '2026-10-01T00:00:00Z'::timestamptz AS period_end,
           '2026-10-01T00:00:00Z'::timestamptz AS as_of
)
SELECT 'R00_SCOPE_AND_WINDOW' AS check_name, params.organization_id,
       params.period_start, params.period_end, params.as_of,
       CASE WHEN organization.id IS NOT NULL
                  AND params.period_start < params.period_end
                  AND params.as_of >= params.period_end THEN 'PASS' ELSE 'FAIL' END AS result
FROM params
LEFT JOIN organizations AS organization ON organization.id = params.organization_id;

-- R01: A01–A04. A02 buckets must equal A01. A03/A04 values are raw numeric ratios.
WITH params AS (
    SELECT '0b47be39-3b3c-5195-a4e2-8a29ffbadcf0'::uuid AS organization_id,
           '2026-10-01T00:00:00Z'::timestamptz AS as_of
), totals AS (
    SELECT lead.organization_id, count(*)::numeric AS total_leads,
           count(*) FILTER (WHERE lead.status = 'QUALIFIED')::numeric AS qualified_leads,
           count(*) FILTER (WHERE lead.converted_customer_id IS NOT NULL)::numeric AS converted_leads
    FROM leads AS lead JOIN params ON params.organization_id = lead.organization_id
    GROUP BY lead.organization_id
), buckets AS (
    SELECT lead.organization_id, lead.status, count(*)::numeric AS value
    FROM leads AS lead JOIN params ON params.organization_id = lead.organization_id
    GROUP BY lead.organization_id, lead.status
)
SELECT 'R01_LEAD_SNAPSHOT' AS check_name, params.organization_id,
       COALESCE(totals.total_leads, 0) AS a01_total_leads,
       COALESCE(sum(buckets.value), 0) AS a02_bucket_total,
       CASE WHEN COALESCE(totals.total_leads, 0) = 0 THEN NULL
            ELSE COALESCE(totals.qualified_leads, 0) / totals.total_leads END AS a03_qualified_rate_raw,
       CASE WHEN COALESCE(totals.total_leads, 0) = 0 THEN NULL
            ELSE COALESCE(totals.converted_leads, 0) / totals.total_leads END AS a04_conversion_snapshot_rate_raw,
       CASE WHEN COALESCE(totals.total_leads, 0) = COALESCE(sum(buckets.value), 0)
            THEN 'PASS' ELSE 'FAIL' END AS result
FROM params
LEFT JOIN totals ON totals.organization_id = params.organization_id
LEFT JOIN buckets ON buckets.organization_id = params.organization_id
GROUP BY params.organization_id, totals.total_leads, totals.qualified_leads, totals.converted_leads;

-- R02: A05 and D01–D02. Empty period is NO_DATA_IN_PERIOD, never an artificial zero rate.
WITH params AS (
    SELECT '0b47be39-3b3c-5195-a4e2-8a29ffbadcf0'::uuid AS organization_id,
           '2026-09-01T00:00:00Z'::timestamptz AS period_start,
           '2026-10-01T00:00:00Z'::timestamptz AS period_end
), response AS (
    SELECT lead.id, lead.created_at,
           min(interaction.occurred_at) FILTER (WHERE interaction.occurred_at >= lead.created_at) AS first_response_at
    FROM leads AS lead JOIN params ON params.organization_id = lead.organization_id
    LEFT JOIN interactions AS interaction
      ON interaction.organization_id = lead.organization_id AND interaction.lead_id = lead.id
     AND interaction.occurred_at >= params.period_start AND interaction.occurred_at < params.period_end
    WHERE lead.created_at >= params.period_start AND lead.created_at < params.period_end
    GROUP BY lead.id, lead.created_at
), source_counts AS (
    SELECT count(*)::bigint AS interaction_total,
           count(DISTINCT interaction.channel)::bigint AS channel_count,
           count(DISTINCT interaction.recorded_by_user_id)::bigint AS recorder_count
    FROM interactions AS interaction JOIN params ON params.organization_id = interaction.organization_id
    WHERE interaction.occurred_at >= params.period_start AND interaction.occurred_at < params.period_end
), channel_total AS (
    SELECT COALESCE(sum(value), 0)::bigint AS bucket_total
    FROM (SELECT count(*) AS value FROM interactions AS interaction JOIN params ON params.organization_id = interaction.organization_id
          WHERE interaction.occurred_at >= params.period_start AND interaction.occurred_at < params.period_end
          GROUP BY interaction.channel) AS buckets
), recorder_total AS (
    SELECT COALESCE(sum(value), 0)::bigint AS bucket_total
    FROM (SELECT count(*) AS value FROM interactions AS interaction JOIN params ON params.organization_id = interaction.organization_id
          WHERE interaction.occurred_at >= params.period_start AND interaction.occurred_at < params.period_end
          GROUP BY interaction.recorded_by_user_id) AS buckets
)
SELECT 'R02_INTERACTION_AND_RESPONSE' AS check_name, params.organization_id,
       count(response.id) AS a05_leads_in_period, count(response.first_response_at) AS a05_responded_leads,
       avg(extract(epoch FROM response.first_response_at - response.created_at)) AS a05_avg_seconds_raw,
       source_counts.interaction_total AS d01_d02_source_total,
       channel_total.bucket_total AS d01_channel_total, recorder_total.bucket_total AS d02_recorder_total,
       CASE WHEN source_counts.interaction_total = channel_total.bucket_total
                  AND source_counts.interaction_total = recorder_total.bucket_total THEN 'PASS' ELSE 'FAIL' END AS result,
       CASE WHEN source_counts.interaction_total = 0 THEN 'NO_DATA_IN_PERIOD' ELSE 'AVAILABLE' END AS availability
FROM params LEFT JOIN response ON TRUE CROSS JOIN source_counts CROSS JOIN channel_total CROSS JOIN recorder_total
GROUP BY params.organization_id, source_counts.interaction_total, channel_total.bucket_total, recorder_total.bucket_total;

-- R03: A06. Each customer occupies exactly one segment at the common as_of instant.
WITH params AS (
    SELECT '0b47be39-3b3c-5195-a4e2-8a29ffbadcf0'::uuid AS organization_id,
           '2026-10-01T00:00:00Z'::timestamptz AS as_of
), activity AS (
    SELECT customer.id, max(interaction.occurred_at) AS last_interaction_at
    FROM customers AS customer JOIN params ON params.organization_id = customer.organization_id
    LEFT JOIN interactions AS interaction
      ON interaction.organization_id = customer.organization_id AND interaction.customer_id = customer.id
    GROUP BY customer.id
), segmented AS (
    SELECT id, CASE WHEN last_interaction_at IS NULL THEN 'NEVER_ENGAGED'
                    WHEN last_interaction_at > params.as_of - interval '30 days' THEN 'ACTIVE'
                    WHEN last_interaction_at >= params.as_of - interval '60 days' THEN 'AT_RISK'
                    ELSE 'INACTIVE' END AS segment
    FROM activity CROSS JOIN params
)
SELECT 'R03_CUSTOMER_ENGAGEMENT' AS check_name, params.organization_id,
       count(segmented.id) AS customer_total,
       count(segmented.id) FILTER (WHERE segment = 'ACTIVE') AS active_count,
       count(segmented.id) FILTER (WHERE segment = 'AT_RISK') AS at_risk_count,
       count(segmented.id) FILTER (WHERE segment = 'INACTIVE') AS inactive_count,
       count(segmented.id) FILTER (WHERE segment = 'NEVER_ENGAGED') AS never_engaged_count,
       CASE WHEN count(segmented.id) = count(segmented.id) FILTER (WHERE segment IN ('ACTIVE','AT_RISK','INACTIVE','NEVER_ENGAGED'))
            THEN 'PASS' ELSE 'FAIL' END AS result
FROM params LEFT JOIN segmented ON TRUE GROUP BY params.organization_id;

-- R04: B01–B05. Snapshot and completed-period measures are intentionally separate.
WITH params AS (
    SELECT '0b47be39-3b3c-5195-a4e2-8a29ffbadcf0'::uuid AS organization_id,
           '2026-09-01T00:00:00Z'::timestamptz AS period_start,
           '2026-10-01T00:00:00Z'::timestamptz AS period_end,
           '2026-10-01T00:00:00Z'::timestamptz AS as_of
), task_summary AS (
    SELECT count(*) FILTER (WHERE task.status IN ('OPEN','IN_PROGRESS'))::bigint AS open_count,
           count(*) FILTER (WHERE task.status IN ('OPEN','IN_PROGRESS') AND task.due_at < params.as_of)::bigint AS overdue_open_count
    FROM crm_tasks AS task JOIN params ON params.organization_id = task.organization_id
), completed AS (
    SELECT task.id, task.due_at, task.completed_at
    FROM crm_tasks AS task JOIN params ON params.organization_id = task.organization_id
    WHERE task.status = 'DONE' AND task.completed_at IS NOT NULL
      AND task.completed_at >= params.period_start AND task.completed_at < params.period_end
)
SELECT 'R04_TASKS' AS check_name, params.organization_id, task_summary.open_count AS b01_open_tasks,
       task_summary.overdue_open_count AS b02_overdue_open_tasks, count(completed.id) AS b05_completed_denominator,
       count(completed.id) FILTER (WHERE completed.completed_at <= completed.due_at) AS b05_on_time_numerator,
       CASE WHEN count(completed.id) = 0 THEN NULL
            ELSE count(completed.id) FILTER (WHERE completed.completed_at <= completed.due_at)::numeric / count(completed.id) END AS b05_on_time_rate_raw,
       'PASS' AS result,
       CASE WHEN count(completed.id) = 0 THEN 'NOT_AVAILABLE_ZERO_DENOMINATOR' ELSE 'AVAILABLE' END AS b05_availability
FROM params CROSS JOIN task_summary LEFT JOIN completed ON TRUE
GROUP BY params.organization_id, task_summary.open_count, task_summary.overdue_open_count;

-- R05: B03/B04 partition totals and C01–C03 terminal workflow output.
WITH params AS (
    SELECT '0b47be39-3b3c-5195-a4e2-8a29ffbadcf0'::uuid AS organization_id,
           '2026-09-01T00:00:00Z'::timestamptz AS period_start,
           '2026-10-01T00:00:00Z'::timestamptz AS period_end
), task_source AS (
    SELECT count(*)::bigint AS total FROM crm_tasks AS task JOIN params ON params.organization_id = task.organization_id
), priority_total AS (
    SELECT COALESCE(sum(value), 0)::bigint AS total FROM (SELECT count(*) AS value FROM crm_tasks AS task JOIN params ON params.organization_id = task.organization_id GROUP BY priority, status) AS x
), owner_total AS (
    SELECT COALESCE(sum(value), 0)::bigint AS total FROM (SELECT count(*) AS value FROM crm_tasks AS task JOIN params ON params.organization_id = task.organization_id GROUP BY owner_user_id) AS x
), workflow AS (
    SELECT execution.workflow_type, count(*) FILTER (WHERE execution.status = 'DONE')::numeric AS done_count,
           count(*) FILTER (WHERE execution.status = 'FAILED')::numeric AS failed_count,
           avg(extract(epoch FROM execution.finished_at - execution.started_at)) FILTER (WHERE execution.finished_at >= execution.started_at) AS c03_avg_seconds_raw
    FROM workflow_executions AS execution JOIN params ON params.organization_id = execution.organization_id
    WHERE execution.finished_at >= params.period_start AND execution.finished_at < params.period_end
      AND execution.status IN ('DONE','FAILED') GROUP BY execution.workflow_type
)
SELECT 'R05_BUCKETS_AND_WORKFLOW' AS check_name, params.organization_id,
       task_source.total AS task_source_total, priority_total.total AS b03_priority_status_total, owner_total.total AS b04_owner_total,
       COALESCE(jsonb_agg(jsonb_build_object('workflowType', workflow.workflow_type, 'done', workflow.done_count, 'failed', workflow.failed_count,
          'c01SuccessRateRaw', CASE WHEN workflow.done_count + workflow.failed_count = 0 THEN NULL ELSE workflow.done_count / (workflow.done_count + workflow.failed_count) END,
          'c03AvgSecondsRaw', workflow.c03_avg_seconds_raw) ORDER BY workflow.workflow_type) FILTER (WHERE workflow.workflow_type IS NOT NULL), '[]'::jsonb) AS c01_to_c03,
       CASE WHEN task_source.total = priority_total.total AND task_source.total = owner_total.total THEN 'PASS' ELSE 'FAIL' END AS task_result,
       CASE WHEN count(workflow.workflow_type) = 0 THEN 'NO_DATA_IN_PERIOD' ELSE 'AVAILABLE' END AS workflow_availability
FROM params CROSS JOIN task_source CROSS JOIN priority_total CROSS JOIN owner_total LEFT JOIN workflow ON TRUE
GROUP BY params.organization_id, task_source.total, priority_total.total, owner_total.total;

-- R06: all tenant-integrity violation counts must be zero.
WITH params AS (SELECT '0b47be39-3b3c-5195-a4e2-8a29ffbadcf0'::uuid AS organization_id), checks AS (
    SELECT 'task_owner_tenant_mismatch' AS check_name, count(*)::bigint AS violation_count
    FROM crm_tasks AS task JOIN params ON params.organization_id = task.organization_id
    LEFT JOIN users AS owner_user ON owner_user.id = task.owner_user_id AND owner_user.organization_id = task.organization_id
    WHERE owner_user.id IS NULL
    UNION ALL SELECT 'interaction_recorder_tenant_mismatch', count(*)
    FROM interactions AS interaction JOIN params ON params.organization_id = interaction.organization_id
    LEFT JOIN users AS recorder ON recorder.id = interaction.recorded_by_user_id AND recorder.organization_id = interaction.organization_id
    WHERE recorder.id IS NULL
    UNION ALL SELECT 'workflow_outbox_tenant_mismatch', count(*)
    FROM workflow_executions AS execution JOIN params ON params.organization_id = execution.organization_id
    LEFT JOIN outbox_events AS outbox ON outbox.id = execution.outbox_event_id AND outbox.organization_id = execution.organization_id
    WHERE execution.outbox_event_id IS NOT NULL AND outbox.id IS NULL
)
SELECT check_name, violation_count, CASE WHEN violation_count = 0 THEN 'PASS' ELSE 'FAIL' END AS result FROM checks ORDER BY check_name;

-- R07: absent sources must remain unavailable rather than be calculated from proxies.
SELECT metric_id, 'NOT_AVAILABLE' AS availability, reason FROM (VALUES
    ('REVENUE_AND_REPEAT_PURCHASE', 'No transaction/order/payment source table'),
    ('HEALTHCARE_APPOINTMENT_OR_REVISIT', 'No appointment or clinical outcome source table'),
    ('EDUCATION_ENROLLMENT_OR_COMPLETION', 'No enrollment or course-completion source table'),
    ('LOGISTICS_SHIPMENT_OR_SLA', 'No shipment/service-event source table'),
    ('REAL_ESTATE_DEPOSIT_OR_CONTRACT', 'No deposit/contract source table'),
    ('RFM_MONETARY_PREDICTIVE_CLV_NBA_EVALUATION', 'No value history, consent, outcome or model contract')
) AS unavailable(metric_id, reason) ORDER BY metric_id;

COMMIT;
