# Task 5 Runbook — PostgreSQL 17 / Windows

## Lần đầu thay schema cũ trên database `pose_crm`

```powershell
cd "<PATH_TO_EXTRACTED_POSE_CRM_DATA_V2_FINAL>"
# If PostgreSQL tools are not already on PATH, add your own PostgreSQL bin folder here
$env:POSE_DATABASE_URL = "postgresql://postgres:YOUR_PASSWORD@localhost:5432/pose_crm"
powershell -ExecutionPolicy Bypass -File database/scripts/run_task5.ps1 -RecreateSchema
```

`-RecreateSchema` xóa toàn bộ `public` schema. Chỉ dùng với database local/demo có thể xóa.

## Kiểm tra riêng từng bước

```powershell
python database/scripts/validate_seed_files.py
python database/scripts/check_contract_static.py
psql $env:POSE_DATABASE_URL -v ON_ERROR_STOP=1 -f database/migrations/V1__pose_crm_schema.sql
psql $env:POSE_DATABASE_URL -v ON_ERROR_STOP=1 -f database/scripts/seed_v2.psql
psql $env:POSE_DATABASE_URL -v ON_ERROR_STOP=1 -f database/scripts/verify_seed_v2.psql
```

## Kiểm tra trong pgAdmin

Refresh `pose_crm → Schemas → public → Tables` và `Views`, sau đó có thể chạy:

```sql
SELECT COUNT(*) FROM organizations;          -- 6
SELECT COUNT(*) FROM customers;              -- 120
SELECT COUNT(*) FROM leads;                  -- 60
SELECT COUNT(*) FROM opportunities;          -- 72
SELECT COUNT(*) FROM customer_360_summary;   -- 120

SELECT handover_type, status, COUNT(*)
FROM handover_requests
GROUP BY handover_type, status
ORDER BY handover_type, status;

SELECT status, COUNT(*) FROM import_jobs GROUP BY status ORDER BY status;
```
