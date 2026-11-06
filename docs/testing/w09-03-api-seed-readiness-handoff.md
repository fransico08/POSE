# W09-03 — API/seed readiness handoff

## Test matrix

| ID | Cùng input SQL/API/UI | Expected | Owner | Status |
|---|---|---|---|---|
| R01 | authorized org + period/asOf | A01–A04 status partition/rates raw khớp | Tài/Thắng | PENDING_INTEGRATION |
| R02 | org + asOf | A06 segment sum = customer total; boundaries 30/60 ngày | Thắng | PENDING_INTEGRATION |
| R03 | org + period/asOf | B01–B05 bucket/rate khớp, zero denominator = null | Thắng | PENDING_INTEGRATION |
| R04 | org + period | C01–C03 terminal/retry/cycle khớp; no terminal = no-data | Thắng | PENDING_INTEGRATION |
| R05 | org + period/channel/recorder | D01/D02 bucket sum khớp | Thắng | PENDING_INTEGRATION |
| R06 | sáu tenant + forbidden orgId | RBAC chặn cross-tenant, không zero giả | Tài/Thắng | PENDING_INTEGRATION |
| R07 | invalid range/timezone/filter | 400/validation; không broad query | Tài | PENDING_INTEGRATION |
| R08 | detail pagination/filter refresh | page ổn định, same filter/asOf | Thắng | PENDING_INTEGRATION |
| R09 | W12 seed lớn/nhiều kỳ | seed local reproducible/idempotent, FK/enum/tenant valid, totals dynamic reconcile | Nhi/Thắng | NOT RUN |
| R10 | performance baseline sau W12 | query plan/latency được ghi, không kết luận trước đo | Tài | NOT RUN |
| R11 | V1 baseline rồi V9–V11 migrated DB | S01 xác nhận profile/composite FK; 16 KPI không đổi formula | Nhi/Tài | NOT RUN |

## SQL/Data verification summary — manually confirmed

- **Environment/scope:** Supabase `pose-crm-dev`, branch `main` (PRODUCTION), read-only SQL only; org `0b47be39-3b3c-5195-a4e2-8a29ffbadcf0`; period `[2026-09-01T00:00:00Z, 2026-10-01T00:00:00Z)`; `as_of=2026-10-01T00:00:00Z`.
- **S01 PASS:** schema and migrations V1–V11 verified. **S02 PASS:** A01=10, A03=0.30, A04=0.10, A02 status total=10, A06 segments total=20.
- **S03 PASS:** B01=8, B02=0, B03 total=14, B04 total=14, B05=0.50.
- **S04 PASS:** C01 ASSIGNMENT_CREATED=1.00, IMPORT_COMMITTED=1.00, TASK_DUE_SOON=0.50; C02 has one failed TASK_DUE_SOON execution (`retry=1`, `error_present=true`); C03=60 seconds for each displayed workflow; D01/D02 totals=26.
- **S05 PASS:** all 10 persisted-state integrity checks have `violation_count=0`.
- Verified scope is current seed SQL/Data reconciliation only. API integration, dashboard UI, RBAC, tenant authorization, runtime/retry behavior and performance remain **PENDING_INTEGRATION / NOT_TESTED**.
- Snapshot metrics do not establish historical point-in-time reconstruction; V10 `opportunities.final_value` must not be described as recognized revenue.

## Evidence template

Ghi cho mỗi case: case ID, người/thời điểm, environment/database, role, authorized org, periodStart/periodEnd/asOf/timezone/dimension, SQL block/check, endpoint đã được phê duyệt (không tự đặt URL), redacted request/response, screenshot UI, SQL/API/UI value, expected/actual/difference, PASS/FAIL/NOT RUN và defect link. Không lưu token, cookie hay PII. SQL `PASS` chỉ là persisted-state; API/UI/runtime chỉ PASS khi có evidence tương ứng.

## Handoff và risk

Nhi cung cấp formula, verifier và diễn giải availability; Tài phê duyệt route/params/auth, bind `ActorContext.organizationId()`, gọi organization-scope authorization, parameterized query, pagination và performance; Thắng kiểm thử API/UI/RBAC/timezone. Acceptance chỉ PASS khi cùng actor/org/period/asOf cho SQL/API/UI cùng raw value, dimension và availability; cross-tenant phải 403/empty theo policy, không zero giả. Backend chưa tích hợp: **PENDING_INTEGRATION**, không PASS. W12 không ghi đè canonical CSV hay chạy Supabase chung khi chưa duyệt.

Rủi ro cần giữ: snapshot không phải event-time/funnel history; thiếu nguồn history/transaction đặc thù; V10 `final_value`/opportunity lines không phải recognized revenue; user-provided orgId gây privilege escalation nếu backend không chặn; KPI formula/provenance đổi cần versioning; nullable denominator phải giữ null.
