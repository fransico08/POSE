-- POSE CRM V2 + Task 1 read-only GUI verification for PostgreSQL 18.
-- Run after import_task1_gui.sql against the original V2 baseline in pgAdmin 4
-- Query Tool or VS Code SQLTools. It is not a replacement for V2-only verifiers.
-- This script performs only SELECT-based assertions and does not modify data or schema.

BEGIN READ ONLY;
SET LOCAL TIME ZONE 'Asia/Ho_Chi_Minh';

DO $$
DECLARE
    actual_count integer;
    violations integer;
BEGIN
    IF current_database() <> 'pose_crm_v2' THEN
        RAISE EXCEPTION 'Task 1 verification aborted: connected database is %, expected pose_crm_v2.', current_database();
    END IF;

    IF current_setting('server_encoding') <> 'UTF8' THEN
        RAISE EXCEPTION 'Server encoding is %, expected UTF8.', current_setting('server_encoding');
    END IF;

    SELECT count(*) INTO actual_count FROM offering_categories;
    IF actual_count <> 47 THEN RAISE EXCEPTION 'offering_categories expected 47, actual %', actual_count; END IF;
    SELECT count(*) INTO actual_count FROM offerings;
    IF actual_count <> 228 THEN RAISE EXCEPTION 'offerings expected 228, actual %', actual_count; END IF;
    SELECT count(*) INTO actual_count FROM offering_prices;
    IF actual_count <> 226 THEN RAISE EXCEPTION 'offering_prices expected 226, actual %', actual_count; END IF;
    SELECT count(*) INTO actual_count FROM sales_assignments;
    IF actual_count <> 441 THEN RAISE EXCEPTION 'sales_assignments expected 441, actual %', actual_count; END IF;

    SELECT count(*) INTO actual_count FROM offering_categories WHERE created_at='2026-10-01T08:00:00Z';
    IF actual_count <> 35 THEN RAISE EXCEPTION 'Task 1 categories expected 35, actual %', actual_count; END IF;
    SELECT count(*) INTO actual_count FROM offerings WHERE created_at='2026-10-02T08:00:00Z';
    IF actual_count <> 202 THEN RAISE EXCEPTION 'Task 1 offerings expected 202, actual %', actual_count; END IF;
    SELECT count(*) INTO actual_count FROM offering_prices WHERE effective_from='2026-10-02T08:00:00Z';
    IF actual_count <> 202 THEN RAISE EXCEPTION 'Task 1 prices expected 202, actual %', actual_count; END IF;
    SELECT count(*) INTO actual_count FROM sales_assignments WHERE effective_from='2026-10-03T08:00:00Z';
    IF actual_count <> 392 THEN RAISE EXCEPTION 'Task 1 assignments expected 392, actual %', actual_count; END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM offering_categories
        WHERE id='d0b6dc82-f87d-505d-88c5-7f7dfa73acf1'
          AND organization_id='a477cbf4-afb4-5960-b165-41537f66ac13'
          AND code='WAREHOUSING'
    ) THEN
        RAISE EXCEPTION 'Reused LOG/WAREHOUSING category is missing or changed.';
    END IF;

    SELECT count(*) INTO violations
    FROM offerings o
    LEFT JOIN offering_categories c ON c.id=o.category_id
    WHERE c.id IS NULL OR o.organization_id<>c.organization_id;
    IF violations<>0 THEN RAISE EXCEPTION 'Offering/category FK or scope violations: %', violations; END IF;

    SELECT count(*) INTO violations
    FROM offering_prices p
    LEFT JOIN offerings o ON o.id=p.offering_id
    WHERE o.id IS NULL
       OR p.organization_id<>o.organization_id
       OR p.amount<=0
       OR p.currency_code<>'VND'
       OR p.pricing_unit<>o.unit
       OR p.effective_to IS NOT NULL AND p.effective_to<p.effective_from;
    IF violations<>0 THEN RAISE EXCEPTION 'Price FK, scope, amount, unit, currency, or timeline violations: %', violations; END IF;

    SELECT count(*) INTO violations
    FROM sales_assignments a
    LEFT JOIN offerings o ON o.id=a.offering_id
    LEFT JOIN users sales_user ON sales_user.id=a.sales_user_id
    LEFT JOIN users manager_user ON manager_user.id=a.assigned_by_user_id
    WHERE o.id IS NULL
       OR sales_user.id IS NULL
       OR manager_user.id IS NULL
       OR a.organization_id<>o.organization_id
       OR a.organization_id<>sales_user.organization_id
       OR a.organization_id<>manager_user.organization_id
       OR a.effective_to IS NOT NULL AND a.effective_to<a.effective_from
       OR a.status='REVOKED' AND a.revoked_at IS NULL;
    IF violations<>0 THEN RAISE EXCEPTION 'Assignment FK, scope, status, or timeline violations: %', violations; END IF;

    SELECT count(*) INTO violations
    FROM sales_assignments a
    WHERE a.effective_from='2026-10-03T08:00:00Z'
      AND (
        a.status<>'ACTIVE'
        OR a.effective_to IS NOT NULL
        OR a.revoked_at IS NOT NULL
        OR a.revoke_reason IS NOT NULL
        OR NOT EXISTS (
            SELECT 1
            FROM user_role_grants g
            JOIN roles r ON r.id=g.role_id
            WHERE g.user_id=a.sales_user_id
              AND g.revoked_at IS NULL
              AND r.code='SALES_REP'
        )
        OR NOT EXISTS (
            SELECT 1
            FROM user_role_grants g
            JOIN roles r ON r.id=g.role_id
            WHERE g.user_id=a.assigned_by_user_id
              AND g.revoked_at IS NULL
              AND r.code='MANAGER'
        )
      );
    IF violations<>0 THEN RAISE EXCEPTION 'Task 1 active assignment role/status violations: %', violations; END IF;

    SELECT count(*) INTO violations
    FROM offerings o
    WHERE o.created_at='2026-10-02T08:00:00Z'
      AND o.status='INACTIVE'
      AND EXISTS (SELECT 1 FROM sales_assignments a WHERE a.offering_id=o.id AND a.status='ACTIVE');
    IF violations<>0 THEN RAISE EXCEPTION 'INACTIVE Task 1 offerings have active assignments: %', violations; END IF;

    SELECT count(*) INTO actual_count
    FROM offerings
    WHERE created_at='2026-10-02T08:00:00Z' AND status='ACTIVE';
    IF actual_count<>190 THEN RAISE EXCEPTION 'Task 1 ACTIVE offerings expected 190, actual %', actual_count; END IF;
    SELECT count(*) INTO actual_count
    FROM offerings
    WHERE created_at='2026-10-02T08:00:00Z' AND status='DRAFT';
    IF actual_count<>6 THEN RAISE EXCEPTION 'Task 1 DRAFT offerings expected 6, actual %', actual_count; END IF;
    SELECT count(*) INTO actual_count
    FROM offerings
    WHERE created_at='2026-10-02T08:00:00Z' AND status='INACTIVE';
    IF actual_count<>6 THEN RAISE EXCEPTION 'Task 1 INACTIVE offerings expected 6, actual %', actual_count; END IF;

    SELECT count(*) INTO violations
    FROM (
        SELECT name AS value FROM offering_categories
        UNION ALL
        SELECT name FROM offerings
        UNION ALL
        SELECT description FROM offerings WHERE description IS NOT NULL
    ) text_values
    WHERE value LIKE '%' || U&'\FFFD' || '%'
       OR value LIKE '%á»%'
       OR value LIKE '%áº%'
       OR value LIKE '%Äƒ%'
       OR value LIKE '%Ã©%';
    IF violations<>0 THEN RAISE EXCEPTION 'UTF-8/mojibake markers found: %', violations; END IF;
END $$;

COMMIT;

-- Expected result: query completes without an exception.
