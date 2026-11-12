-- Đối soát độc lập W12: chỉ đọc database seed; không gọi SQL trong backend.
-- Chạy với psql trên database canonical cổng 54330 sau khi kiểm tra data_directory.
-- demo_as_of = 11/10/2026 00:00 giờ Việt Nam; khoảng E03 là 30 ngày trước đó.
\set ON_ERROR_STOP on
BEGIN READ ONLY;
SELECT current_database(), current_setting('data_directory');

SELECT o.code,
  (SELECT count(*) FROM customers c WHERE c.organization_id=o.id) AS customers_total,
  (SELECT count(*) FROM customers c WHERE c.organization_id=o.id AND c.status='ACTIVE') AS customers_active,
  (SELECT count(*) FROM leads l WHERE l.organization_id=o.id) AS leads_total,
  (SELECT count(*) FROM leads l WHERE l.organization_id=o.id AND l.status='NEW') AS leads_new,
  (SELECT count(*) FROM leads l WHERE l.organization_id=o.id AND l.status='QUALIFIED') AS leads_qualified,
  (SELECT count(*) FROM leads l WHERE l.organization_id=o.id AND l.status='CONVERTED') AS leads_converted,
  (SELECT count(*) FROM opportunities p WHERE p.organization_id=o.id) AS opportunities_total,
  (SELECT count(*) FROM opportunities p WHERE p.organization_id=o.id AND p.stage='NEW') AS opp_new,
  (SELECT count(*) FROM opportunities p WHERE p.organization_id=o.id AND p.stage='QUALIFIED') AS opp_qualified,
  (SELECT count(*) FROM opportunities p WHERE p.organization_id=o.id AND p.stage='PROPOSAL') AS opp_proposal,
  (SELECT count(*) FROM opportunities p WHERE p.organization_id=o.id AND p.stage='WON') AS opp_won,
  (SELECT count(*) FROM opportunities p WHERE p.organization_id=o.id AND p.stage='LOST') AS opp_lost,
  (SELECT coalesce(sum(p.expected_value),0) FROM opportunities p WHERE p.organization_id=o.id AND p.stage='WON' AND p.currency_code='VND') AS won_expected_value_vnd,
  (SELECT count(*) FROM interactions i WHERE i.organization_id=o.id) AS interactions_total,
  (SELECT count(*) FROM crm_tasks t WHERE t.organization_id=o.id AND t.status IN ('OPEN','IN_PROGRESS')) AS tasks_open_or_in_progress,
  (SELECT count(*) FROM crm_tasks t WHERE t.organization_id=o.id AND t.status IN ('OPEN','IN_PROGRESS') AND (t.due_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date < DATE '2026-10-11') AS tasks_overdue_as_of_demo_date
FROM organizations o WHERE o.code IN ('edu','rea','tech','ret','log','cli') ORDER BY o.code;

WITH params AS (SELECT TIMESTAMPTZ '2026-10-10 17:00:00+00' AS as_of, TIMESTAMPTZ '2026-09-10 17:00:00+00' AS period_start),
e01 AS (
 SELECT l.organization_id,count(*) AS total FROM leads l,params p
 WHERE l.archived_at IS NULL AND l.status IN ('NEW','QUALIFIED') AND l.created_at <= p.as_of - interval '24 hours'
 AND NOT EXISTS (SELECT 1 FROM interactions i WHERE i.organization_id=l.organization_id AND i.lead_id=l.id AND i.occurred_at <= p.as_of)
 GROUP BY l.organization_id
), e02 AS (
 SELECT op.organization_id,op.currency_code,count(*) AS total,coalesce(sum(op.expected_value),0) AS value FROM opportunities op,params p
 WHERE op.stage IN ('NEW','QUALIFIED','PROPOSAL') AND NOT EXISTS (
  SELECT 1 FROM interactions i WHERE i.organization_id=op.organization_id
   AND (i.opportunity_id=op.id OR (op.customer_id IS NOT NULL AND i.customer_id=op.customer_id) OR (op.lead_id IS NOT NULL AND i.lead_id=op.lead_id))
   AND i.occurred_at > p.as_of - interval '14 days' AND i.occurred_at <= p.as_of)
 GROUP BY op.organization_id,op.currency_code
), latest AS (
 SELECT DISTINCT ON (h.organization_id,h.customer_id) h.organization_id,h.customer_id,h.target_user_id,h.reviewed_at
 FROM handover_requests h JOIN customers c ON c.id=h.customer_id AND c.organization_id=h.organization_id CROSS JOIN params p
 WHERE c.archived_at IS NULL AND h.handover_type='SALES' AND h.status='APPROVED' AND h.reviewed_at>=p.period_start AND h.reviewed_at<p.as_of
 ORDER BY h.organization_id,h.customer_id,h.reviewed_at DESC,h.id DESC
), e03 AS (
 SELECT h.organization_id,count(*) AS denominator,count(*) FILTER (WHERE NOT EXISTS (
  SELECT 1 FROM interactions i,params p WHERE i.organization_id=h.organization_id AND i.customer_id=h.customer_id
   AND i.recorded_by_user_id=h.target_user_id AND i.occurred_at>=h.reviewed_at AND i.occurred_at<=p.as_of)) AS value
 FROM latest h GROUP BY h.organization_id
)
SELECT o.code,coalesce(e01.total,0) AS e01,e02.currency_code,e02.total AS e02_count,e02.value AS e02_value,
       coalesce(e03.denominator,0) AS e03_denominator,e03.value AS e03_value
FROM organizations o LEFT JOIN e01 ON e01.organization_id=o.id LEFT JOIN e02 ON e02.organization_id=o.id LEFT JOIN e03 ON e03.organization_id=o.id
WHERE o.code IN ('edu','rea','tech','ret','log','cli') ORDER BY o.code,e02.currency_code;
COMMIT;
