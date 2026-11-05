-- POSE CRM W04 Customer/Lead synchronization for PostgreSQL 18.
-- Run manually in pgAdmin 4 Query Tool or VS Code SQLTools against pose_crm_v2.
-- Canonical audit found no deterministic field correction, so this guarded
-- transaction is intentionally a no-op. It protects the existing baseline
-- while confirming it remains eligible for W04 synchronization.

BEGIN;
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;

DO $$
DECLARE
    customer_count integer;
    lead_count integer;
    violation_count integer;
BEGIN
    IF current_database() <> 'pose_crm_v2' THEN
        RAISE EXCEPTION 'Expected database pose_crm_v2; connected to %', current_database();
    END IF;

    IF current_setting('server_encoding') <> 'UTF8' THEN
        RAISE EXCEPTION 'Expected UTF8 server encoding; found %', current_setting('server_encoding');
    END IF;

    SELECT count(*) INTO customer_count FROM customers;
    SELECT count(*) INTO lead_count FROM leads;
    IF customer_count <> 120 OR lead_count <> 60 THEN
        RAISE EXCEPTION 'Expected canonical W04 state of 120 customers and 60 leads; found % and %', customer_count, lead_count;
    END IF;

    SELECT count(*) INTO violation_count
    FROM (
        SELECT c.id
        FROM customers AS c
        LEFT JOIN users AS u ON u.id = c.owner_user_id
        WHERE c.normalized_email IS NULL AND c.normalized_phone IS NULL
           OR c.email IS NOT NULL AND c.normalized_email <> lower(btrim(c.email))
           OR c.phone IS NOT NULL AND c.normalized_phone <> c.phone
           OR u.id IS NULL OR u.organization_id <> c.organization_id
        UNION ALL
        SELECT l.id
        FROM leads AS l
        LEFT JOIN users AS u ON u.id = l.owner_user_id
        LEFT JOIN offerings AS o ON o.id = l.offering_id
        LEFT JOIN customers AS c ON c.id = l.converted_customer_id
        WHERE l.normalized_email IS NULL AND l.normalized_phone IS NULL
           OR l.email IS NOT NULL AND l.normalized_email <> lower(btrim(l.email))
           OR l.phone IS NOT NULL AND l.normalized_phone <> l.phone
           OR u.id IS NULL OR u.organization_id <> l.organization_id
           OR o.id IS NULL OR o.organization_id <> l.organization_id
           OR l.status = 'CONVERTED' AND (c.id IS NULL OR c.organization_id <> l.organization_id)
    ) AS violations;
    IF violation_count <> 0 THEN
        RAISE EXCEPTION 'W04 precondition failed: % customer/lead integrity violations', violation_count;
    END IF;

    RAISE NOTICE 'W04 sync: no canonical Customer/Lead field corrections are required; no rows updated.';
END $$;

COMMIT;
