-- POSE CRM W04 read-only verification for PostgreSQL 18.
-- Run manually in pgAdmin 4 Query Tool or VS Code SQLTools after any approved
-- W04 synchronization. This script changes no data and leaves the session read-only.

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

SELECT current_database() AS database_name,
       current_setting('server_encoding') AS server_encoding,
       current_user AS database_user;

-- Canonical W04 baseline totals.
SELECT 'customers' AS table_name, count(*) AS actual_count, 120 AS expected_count,
       CASE WHEN count(*) = 120 THEN 'PASS' ELSE 'FAIL' END AS result
FROM customers
UNION ALL
SELECT 'leads', count(*), 60,
       CASE WHEN count(*) = 60 THEN 'PASS' ELSE 'FAIL' END
FROM leads;

-- Identity normalization, UTF-8 replacement characters, and per-organization duplicates.
SELECT 'customer_identity_quality' AS check_name, count(*) AS violation_count
FROM customers
WHERE normalized_email IS NULL AND normalized_phone IS NULL
   OR email IS NOT NULL AND normalized_email <> lower(btrim(email))
   OR phone IS NOT NULL AND normalized_phone <> phone
   OR display_name <> btrim(display_name)
   OR display_name LIKE '%' || chr(65533) || '%'
UNION ALL
SELECT 'lead_identity_quality', count(*)
FROM leads
WHERE normalized_email IS NULL AND normalized_phone IS NULL
   OR email IS NOT NULL AND normalized_email <> lower(btrim(email))
   OR phone IS NOT NULL AND normalized_phone <> phone
   OR display_name <> btrim(display_name)
   OR display_name LIKE '%' || chr(65533) || '%'
UNION ALL
SELECT 'customer_duplicate_email', count(*)
FROM (
    SELECT organization_id, normalized_email
    FROM customers
    WHERE normalized_email IS NOT NULL
    GROUP BY organization_id, normalized_email
    HAVING count(*) > 1
) AS duplicates
UNION ALL
SELECT 'customer_duplicate_phone', count(*)
FROM (
    SELECT organization_id, normalized_phone
    FROM customers
    WHERE normalized_phone IS NOT NULL
    GROUP BY organization_id, normalized_phone
    HAVING count(*) > 1
) AS duplicates
UNION ALL
SELECT 'lead_duplicate_email', count(*)
FROM (
    SELECT organization_id, normalized_email
    FROM leads
    WHERE normalized_email IS NOT NULL
    GROUP BY organization_id, normalized_email
    HAVING count(*) > 1
) AS duplicates
UNION ALL
SELECT 'lead_duplicate_phone', count(*)
FROM (
    SELECT organization_id, normalized_phone
    FROM leads
    WHERE normalized_phone IS NOT NULL
    GROUP BY organization_id, normalized_phone
    HAVING count(*) > 1
) AS duplicates;

-- Owner, offering and converted-customer organization scope.
SELECT 'customer_owner_scope' AS check_name, count(*) AS violation_count
FROM customers AS c
LEFT JOIN users AS u ON u.id = c.owner_user_id
WHERE u.id IS NULL OR u.organization_id <> c.organization_id
UNION ALL
SELECT 'lead_owner_scope', count(*)
FROM leads AS l
LEFT JOIN users AS u ON u.id = l.owner_user_id
WHERE u.id IS NULL OR u.organization_id <> l.organization_id
UNION ALL
SELECT 'lead_offering_scope', count(*)
FROM leads AS l
LEFT JOIN offerings AS o ON o.id = l.offering_id
WHERE o.id IS NULL OR o.organization_id <> l.organization_id
UNION ALL
SELECT 'lead_conversion_scope', count(*)
FROM leads AS l
LEFT JOIN customers AS c ON c.id = l.converted_customer_id
WHERE l.status = 'CONVERTED'
  AND (c.id IS NULL OR c.organization_id <> l.organization_id);

-- Lifecycle and timestamp rules not fully expressed by the schema CHECK constraints.
SELECT 'customer_timeline' AS check_name, count(*) AS violation_count
FROM customers
WHERE updated_at < created_at
   OR archived_at IS NOT NULL AND (archived_at < created_at OR archived_at > updated_at)
UNION ALL
SELECT 'lead_timeline', count(*)
FROM leads
WHERE updated_at < created_at
   OR archived_at IS NOT NULL AND (archived_at < created_at OR archived_at > updated_at)
   OR qualified_at IS NOT NULL AND (qualified_at < created_at OR qualified_at > updated_at)
   OR converted_at IS NOT NULL AND (converted_at < created_at OR converted_at > updated_at)
   OR qualified_at IS NOT NULL AND converted_at IS NOT NULL AND qualified_at > converted_at
UNION ALL
SELECT 'lead_status_conversion', count(*)
FROM leads
WHERE status = 'CONVERTED' AND (converted_customer_id IS NULL OR converted_at IS NULL)
   OR status <> 'CONVERTED' AND (converted_customer_id IS NOT NULL OR converted_at IS NOT NULL)
   OR status = 'DISQUALIFIED' AND nullif(btrim(disqualification_reason), '') IS NULL;

-- Human-review candidates only: do not automatically merge any returned rows.
SELECT organization_id,
       display_name,
       count(*) AS same_name_count,
       string_agg(id::text, ', ' ORDER BY id::text) AS customer_ids
FROM customers
GROUP BY organization_id, display_name
HAVING count(*) > 1
ORDER BY organization_id, display_name;

COMMIT;
