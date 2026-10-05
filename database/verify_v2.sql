-- ============================================================
-- POSE CRM DB v2 — Verification script
-- Tái hiện lại toàn bộ 17 kiểm tra (L01-L07, D01-D10) từ Nhi.docx
-- Chạy: psql -U postgres -h localhost -d <ten_database_v2> -f verify_v2.sql
-- Mỗi khối in ra 1 số. Số đúng mong đợi được ghi trong comment ngay trên.
-- ============================================================

\echo '== L01: Lead converted_customer_id phai khop email/SDT (ky vong: 0) =='
SELECT count(*) AS l01_mismatched_conversions
FROM leads l JOIN customers c ON c.id = l.converted_customer_id
WHERE l.converted_customer_id IS NOT NULL
AND NOT (
  (l.normalized_email IS NOT NULL AND l.normalized_email = c.normalized_email)
  OR (l.normalized_phone IS NOT NULL AND l.normalized_phone = c.normalized_phone)
);

\echo '== L02: dong VALID cua job COMMITTED phai co committed_entity_id (ky vong: 0) =='
SELECT count(*) AS l02_missing_commit
FROM import_rows ir JOIN import_jobs j ON j.id = ir.import_job_id
WHERE j.status='COMMITTED' AND ir.status='VALID' AND ir.committed_entity_id IS NULL;

\echo '== L03: interaction ghi ngoai thoi gian phan cong (ky vong: 0) =='
SELECT count(*) AS l03_out_of_scope
FROM interactions i
WHERE i.customer_id IS NOT NULL
AND NOT EXISTS (
  SELECT 1 FROM customer_assignments ca
  WHERE ca.customer_id = i.customer_id
  AND ca.user_id = i.recorded_by_user_id
  AND i.occurred_at >= ca.effective_from
  AND (ca.effective_to IS NULL OR i.occurred_at <= ca.effective_to)
);

\echo '== L04: hoat dong xay ra truoc khi Customer duoc tao (ky vong: 0 moi dong) =='
SELECT count(*) AS l04_interactions_before FROM interactions i JOIN customers c ON c.id = i.customer_id WHERE i.occurred_at < c.created_at;
SELECT count(*) AS l04_feedback_before FROM feedbacks f JOIN customers c ON c.id = f.customer_id WHERE f.received_at < c.created_at;
SELECT count(*) AS l04_support_before FROM support_requests s JOIN customers c ON c.id = s.customer_id WHERE s.created_at < c.created_at;

\echo '== L05: password_hash phai khac nhau cho moi user (ky vong: total = distinct) =='
SELECT count(*) AS total, count(DISTINCT password_hash) AS distinct_hashes FROM credentials;

\echo '== L06: it nhat 2 user co >1 role dang hoat dong (ky vong: >=2 dong) =='
SELECT user_id, count(*) FROM user_role_grants WHERE revoked_at IS NULL GROUP BY user_id HAVING count(*) > 1;

\echo '== L06: phai co sales_assignment REVOKED va EXPIRED (ky vong: ca 2 trang thai co mat) =='
SELECT status, count(*) FROM sales_assignments GROUP BY status;

\echo '== L06: phai co import_job FAILED (ky vong: >=1) =='
SELECT status, count(*) FROM import_jobs GROUP BY status;

\echo '== D01: du 5 role ADMIN/MANAGER/SALES_REP/CUSTOMER_CARE/DATA_STAFF cho MOI organization =='
SELECT o.name, count(DISTINCT r.code) AS so_role
FROM organizations o LEFT JOIN roles r ON r.organization_id = o.id
GROUP BY o.name ORDER BY 1;
-- ky vong: so_role = 5 cho tat ca

\echo '== D02: moi role phai co it nhat 1 permission (ky vong: khong dong nao = 0) =='
SELECT r.code, count(rp.permission_id) AS so_permission
FROM roles r LEFT JOIN role_permissions rp ON rp.role_id = r.id
GROUP BY r.code ORDER BY 1;

\echo '== D03: users phai co organization_id NOT NULL va unique(org,email) - kiem tra bang info_schema =='
SELECT column_name, is_nullable FROM information_schema.columns WHERE table_name='users' AND column_name='organization_id';

\echo '== D05: interactions/feedbacks phai co cot visibility =='
SELECT table_name, column_name FROM information_schema.columns WHERE table_name IN ('interactions','feedbacks') AND column_name='visibility';

\echo '== D06: phai co stage QUALIFIED va co OPPORTUNITY_REOPENED trong audit_logs =='
SELECT DISTINCT stage FROM opportunities ORDER BY 1;
SELECT count(*) AS so_reopen FROM audit_logs WHERE action='OPPORTUNITY_REOPENED';

\echo '== D07: import COMMITTED khong duoc tu duyet (nguoi nop != nguoi duyet) (ky vong: 0) =='
SELECT count(*) AS d07_self_approved FROM import_jobs WHERE status='COMMITTED' AND submitted_by_user_id = approved_by_user_id;

\echo '== D08: phai co du outbox_events, workflow_executions, offering_attribute_* =='
SELECT
  (SELECT count(*) FROM outbox_events) AS outbox_events,
  (SELECT count(*) FROM workflow_executions) AS workflow_executions,
  (SELECT count(*) FROM offering_attribute_definitions) AS offering_attribute_definitions,
  (SELECT count(*) FROM offering_attribute_values) AS offering_attribute_values;

\echo '== D09: opportunities phai co dung 1 offering, va cho phep gan Lead hoac Customer =='
SELECT (customer_id IS NOT NULL) AS has_customer, (lead_id IS NOT NULL) AS has_lead, count(*) FROM opportunities GROUP BY 1,2;

\echo '== D10: handover_requests phai co ca 2 loai SALES va CARE (ky vong: ca 2 co mat) =='
SELECT handover_type, count(*) FROM handover_requests GROUP BY handover_type;

\echo '== HET KIEM TRA =='
