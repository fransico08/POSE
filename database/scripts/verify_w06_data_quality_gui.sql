-- POSE CRM W06 read-only verification for PostgreSQL 18.
-- Run manually in pgAdmin 4 Query Tool or VS Code SQLTools against pose_crm_v2.
-- It returns PASS/FAIL evidence and makes no data or schema change.

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

-- Canonical W06 seed counts.
SELECT 'interactions' AS check_name,
       count(*) AS actual_count,
       156 AS expected_count,
       CASE WHEN count(*) = 156 THEN 'PASS' ELSE 'FAIL' END AS result
FROM interactions
UNION ALL
SELECT 'crm_tasks', count(*), 84,
       CASE WHEN count(*) = 84 THEN 'PASS' ELSE 'FAIL' END
FROM crm_tasks
UNION ALL
SELECT 'audit_logs', count(*), 63,
       CASE WHEN count(*) = 63 THEN 'PASS' ELSE 'FAIL' END
FROM audit_logs;

-- Interaction PK/FK, tenant scope, subject XOR, visibility and timeline.
SELECT 'interaction_integrity' AS check_name, count(*) AS violation_count
FROM interactions AS i
LEFT JOIN users AS u ON u.id = i.recorded_by_user_id
LEFT JOIN customers AS c ON c.id = i.customer_id
LEFT JOIN leads AS l ON l.id = i.lead_id
LEFT JOIN opportunities AS o ON o.id = i.opportunity_id
WHERE ((i.customer_id IS NOT NULL)::integer + (i.lead_id IS NOT NULL)::integer) <> 1
   OR i.visibility NOT IN ('CARE_VISIBLE', 'SALES_ONLY')
   OR btrim(i.type) = ''
   OR btrim(i.channel) = ''
   OR btrim(i.subject) = ''
   OR btrim(i.content) = ''
   OR u.id IS NULL OR u.organization_id <> i.organization_id
   OR c.id IS NOT NULL AND c.organization_id <> i.organization_id
   OR l.id IS NOT NULL AND l.organization_id <> i.organization_id
   OR o.id IS NOT NULL AND o.organization_id <> i.organization_id
   OR i.occurred_at > i.created_at
   OR i.created_at > i.updated_at;

-- CRM task integrity, lifecycle and idempotency uniqueness.
SELECT 'task_integrity' AS check_name, count(*) AS violation_count
FROM crm_tasks AS t
LEFT JOIN users AS owner_user ON owner_user.id = t.owner_user_id
LEFT JOIN users AS creator_user ON creator_user.id = t.created_by_user_id
LEFT JOIN customers AS c ON c.id = t.customer_id
LEFT JOIN leads AS l ON l.id = t.lead_id
LEFT JOIN opportunities AS o ON o.id = t.opportunity_id
WHERE ((t.customer_id IS NOT NULL)::integer + (t.lead_id IS NOT NULL)::integer) <> 1
   OR t.status NOT IN ('OPEN', 'IN_PROGRESS', 'DONE', 'CANCELLED')
   OR t.origin NOT IN ('MANUAL', 'WORKFLOW', 'IMPORT')
   OR btrim(t.title) = ''
   OR btrim(t.priority) = ''
   OR owner_user.id IS NULL OR owner_user.organization_id <> t.organization_id
   OR creator_user.id IS NULL OR creator_user.organization_id <> t.organization_id
   OR c.id IS NOT NULL AND c.organization_id <> t.organization_id
   OR l.id IS NOT NULL AND l.organization_id <> t.organization_id
   OR o.id IS NOT NULL AND o.organization_id <> t.organization_id
   OR t.updated_at < t.created_at
   OR t.due_at < t.created_at
   OR t.completed_at IS NOT NULL AND (t.completed_at < t.created_at OR t.completed_at > t.updated_at)
   OR t.status = 'DONE' AND t.completed_at IS NULL;

SELECT 'task_idempotency_duplicates' AS check_name, count(*) AS violation_count
FROM (
    SELECT organization_id, idempotency_key
    FROM crm_tasks
    WHERE idempotency_key IS NOT NULL
    GROUP BY organization_id, idempotency_key
    HAVING count(*) > 1
) AS duplicates;

-- Audit history: actor scope, valid JSONB payloads and no secret-like JSON keys.
SELECT 'audit_integrity' AS check_name, count(*) AS violation_count
FROM audit_logs AS a
LEFT JOIN users AS u ON u.id = a.actor_user_id
WHERE btrim(a.action) = ''
   OR btrim(a.entity_type) = ''
   OR btrim(a.result) = ''
   OR u.id IS NULL OR u.organization_id <> a.organization_id
   OR jsonb_typeof(a.before_data) <> 'object'
   OR jsonb_typeof(a.after_data) <> 'object'
   OR jsonb_typeof(a.safe_metadata) <> 'object'
   OR a.before_data::text ~* '"(password|secret|token|api[_-]?key|authorization)"[[:space:]]*:'
   OR a.after_data::text ~* '"(password|secret|token|api[_-]?key|authorization)"[[:space:]]*:'
   OR a.safe_metadata::text ~* '"(password|secret|token|api[_-]?key|authorization)"[[:space:]]*:';

-- Exact natural/event-key duplicates are review candidates; no row is changed by this query.
SELECT 'interaction_exact_event_duplicates' AS check_name, count(*) AS violation_count
FROM (
    SELECT organization_id, recorded_by_user_id, type, channel, subject, occurred_at
    FROM interactions
    GROUP BY organization_id, recorded_by_user_id, type, channel, subject, occurred_at
    HAVING count(*) > 1
) AS duplicates
UNION ALL
SELECT 'task_exact_event_duplicates', count(*)
FROM (
    SELECT organization_id, owner_user_id, title, due_at
    FROM crm_tasks
    GROUP BY organization_id, owner_user_id, title, due_at
    HAVING count(*) > 1
) AS duplicates
UNION ALL
SELECT 'audit_exact_event_duplicates', count(*)
FROM (
    SELECT organization_id, actor_user_id, action, entity_type, entity_id, occurred_at
    FROM audit_logs
    GROUP BY organization_id, actor_user_id, action, entity_type, entity_id, occurred_at
    HAVING count(*) > 1
) AS duplicates;

-- Coverage per organization: each table must be present in all six canonical tenants.
SELECT table_name,
       count(DISTINCT organization_id) AS organization_count,
       CASE WHEN count(DISTINCT organization_id) = 6 THEN 'PASS' ELSE 'FAIL' END AS result
FROM (
    SELECT 'interactions' AS table_name, organization_id FROM interactions
    UNION ALL
    SELECT 'crm_tasks', organization_id FROM crm_tasks
    UNION ALL
    SELECT 'audit_logs', organization_id FROM audit_logs
) AS coverage
GROUP BY table_name
ORDER BY table_name;

COMMIT;
