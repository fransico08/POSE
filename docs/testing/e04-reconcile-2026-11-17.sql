-- PostgreSQL, chỉ đọc. Truyền organization_id, start_at, end_at, as_of qua psql -v.
-- Đối soát toàn tổ chức (Admin/Data Staff), độc lập với truy vấn backend.
WITH parameters AS (
  SELECT CAST(:'organization_id' AS uuid) AS organization_id,
         CAST(:'start_at' AS timestamptz) AS start_at,
         CAST(:'end_at' AS timestamptz) AS end_at,
         CAST(:'as_of' AS timestamptz) AS as_of
), first_messages AS (
  SELECT c.id,c.organization_id,a.channel_type,p.start_at,p.end_at,p.as_of,
         (SELECT MIN(m.occurred_at) FROM conversation_messages m
          WHERE m.organization_id=c.organization_id AND m.conversation_id=c.id AND m.direction='INBOUND') AS inbound_at
  FROM conversations c JOIN channel_accounts a ON a.organization_id=c.organization_id AND a.id=c.channel_account_id
  JOIN parameters p ON p.organization_id=c.organization_id
), replies AS (
  SELECT f.channel_type,f.inbound_at,
         (SELECT MIN(m.occurred_at) FROM conversation_messages m
          WHERE m.organization_id=f.organization_id AND m.conversation_id=f.id
            AND m.direction='OUTBOUND' AND m.sent_by_user_id IS NOT NULL AND m.delivery_status='SENT'
            AND m.occurred_at>=f.inbound_at AND m.occurred_at<=f.as_of) AS reply_at
  FROM first_messages f
  WHERE f.inbound_at>=f.start_at AND f.inbound_at<f.end_at AND f.inbound_at<=f.as_of
)
SELECT channel_type,COUNT(*) AS denominator,COUNT(reply_at) AS numerator,
       PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (reply_at-inbound_at))) AS value
FROM replies GROUP BY channel_type ORDER BY channel_type;
