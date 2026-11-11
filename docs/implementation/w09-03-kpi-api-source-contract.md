# W09-03 — KPI API source and SQL reconciliation contract

## Source readiness and migration compatibility (formula chuẩn: W09-01)

Mọi grain bắt đầu bằng `organization_id`; join cross-table phải khớp cả ID và tenant. Snapshot dùng một `asOf` capture cho toàn bộ refresh; period dùng `[periodStart, periodEnd)`. `organizations.id` là tenant source, `users(id, organization_id)` chỉ được join cho B04/D02.

| KPI | Nguồn đã xác minh / grain | Time & formula W09-01 | Readiness |
|---|---|---|---|
| A01 | `leads(id,organization_id)`; org | snapshot `count(id)` | AVAILABLE snapshot |
| A02 | `leads(status)`; org × status | snapshot count/share trên A01 | AVAILABLE snapshot |
| A03 | `leads(status)`; org | QUALIFIED/A01 snapshot | AVAILABLE snapshot |
| A04 | `leads(converted_customer_id,converted_at)`; org | converted customer reference/A01 snapshot | AVAILABLE snapshot, không cohort history |
| A05 | `leads(created_at)` + `interactions(lead_id,occurred_at)`; org × lead | first valid response trong period minus lead created | AVAILABLE; no-response riêng |
| A06 | `customers` + `interactions(customer_id,occurred_at)`; org × customer | last interaction at asOf; 4 segment | AVAILABLE snapshot |
| B01 | `crm_tasks(status)`; org | OPEN/IN_PROGRESS snapshot | AVAILABLE |
| B02 | `crm_tasks(status,due_at)`; org | open và due before asOf | AVAILABLE |
| B03 | `crm_tasks(priority,status)`; org × priority × status | stored distribution snapshot | AVAILABLE |
| B04 | `crm_tasks(owner_user_id)` + `users`; org × owner | count task per owner | AVAILABLE; owner tenant join bắt buộc |
| B05 | `crm_tasks(status,completed_at,due_at)`; org | DONE complete in period, on-time/complete | AVAILABLE; zero denominator is null |
| C01 | `workflow_executions(status,finished_at,workflow_type)`; org × type | DONE/(DONE+FAILED) in terminal period | AVAILABLE persisted-state only |
| C02 | `workflow_executions(status,retry_count,last_error,finished_at)`; org × type × retry/error | FAILED distribution in terminal period | AVAILABLE; null error is valid dimension |
| C03 | `workflow_executions(started_at,finished_at,workflow_type)`; org × type | valid finish-start interval in terminal period | AVAILABLE; invalid/missing excluded and counted separately |
| D01 | `interactions(channel,occurred_at)`; org × channel | count in period | AVAILABLE |
| D02 | `interactions(recorded_by_user_id,occurred_at)` + `users`; org × recorder | count in period | AVAILABLE; recorder tenant join bắt buộc |

### V1 versus origin/Tài V9–V11

- **V1 Nhi:** là nguồn chốt cho 16 KPI: PK UUID và `organization_id` trên mọi fact; enum Lead/Task/Workflow nêu trong W09-01; timestamp có timezone.
- **V9:** thêm `customers.customer_type/address/notes/related_offering_id` và `leads.source/notes`; tất cả optional và không đổi KPI/grain/formula. `related_offering_id` có composite FK cùng tenant.
- **V10:** thêm `opportunities.final_value, actual_close_date, notes` và `opportunity_lines`. Đây là dữ liệu opportunity/closing, **không** là transaction, payment, invoice hay recognized revenue; không được thêm Revenue/RFM Monetary/CLV vào dashboard hiện tại.
- **V11:** thêm composite unique/FK theo `(organization_id,id)` cho users/customers/leads/opportunities/offerings và interaction/task/audit references. SQL KPI vẫn join cả ID và tenant để tương thích V1 lẫn V11; catalog block S01 xác nhận migration thực tế trước khi kết luận FK enforcement.

Do đó source KPI core hợp lệ ở V1; V9–V11 chỉ tăng profile/integrity, không thay 16 formula. Data Warehouse tương lai cần nguồn transaction/value immutable, currency semantics, event time, consent, action/outcome label và model version; `final_value` một mình không đáp ứng điều kiện đó.

## API contract đã được duyệt

Contract đã được xác minh tại `origin/Tài:docs/api/dashboard-kpi-api-v1.md`: `GET /api/v1/dashboard/kpis` yêu cầu `dashboard.read`, lấy `organizationId` từ phiên xác thực và không nhận tenant do client chỉ định. Request hỗ trợ `asOf`, cặp `start`/`end`, `timezone`, `ownerUserId`, `workflowType` và `metricIds`; khoảng thời gian là `[start, end)`, tối đa 366 ngày.

Response có `organizationId`, `asOf`, `period`, `filters` và `metrics`. Mỗi metric có `metricId`, `dimensions`, `value`, `unit`, `numerator`, `denominator`, `availability`, `reason`, `periodOrAsOf`, `generatedAt` và `sourceSql`. `value`, `numerator`, `denominator` là null khi không áp dụng hoặc không đủ dữ liệu; raw ratio giữ 0–1 và UI nhân 100 đúng một lần. `NOT_AVAILABLE_ZERO_DENOMINATOR`, `NO_DATA_IN_PERIOD` và `NOT_APPLICABLE` không được render thành zero.

Contract bao phủ 16 KPI W09-01 và 5 KPI DEC-21 khi được chọn qua `metricIds`. `ownerUserId` chỉ áp dụng cho nhóm nguồn hỗ trợ; workflow trả `NOT_APPLICABLE` với `OWNER_FILTER_NOT_SUPPORTED`. Backend bind tenant trước mọi filter, và lỗi validation/permission/scope lần lượt dùng `400 VALIDATION_ERROR`, `403 FORBIDDEN_PERMISSION`, `403 FORBIDDEN_SCOPE`.

## Filter, UI và tenant rules

Truyền thời gian UTC, UI render `Asia/Ho_Chi_Minh`; validate `periodStart < periodEnd`. Với contract Tài hiện có, backend lấy tenant từ `ActorContext.organizationId()` và áp dụng `AuthorizationPolicy.requireOrganizationScope`; dashboard không nhận `organizationId` để mở rộng scope. Actor có quyền/role quyết định owner/detail scope; query vẫn bind tenant trước filter. Owner/status/channel/workflow filter phải parameterized, có cùng semantics ở card/chart/detail; detail pagination không đổi filter/denominator. Overview ưu tiên A01, A03, A04, B01, B02, C01; phần chi tiết gồm A02/A05/A06, B03–B05, C02–C03, D01–D02.

SQL fixture dùng UUID zero chỉ để cú pháp chạy không lộ seed identity. Production repository nhận `actor.organizationId()` và parameter `Instant`; không tái sử dụng fixture UUID. Nếu fact scope không có row, API tạo item đúng metric với `NO_DATA_IN_PERIOD` cho period fact, hoặc `ZERO` cho count snapshot đo được bằng 0; không tự tạo dimension/channel/workflow chưa tồn tại. `last_error` workflow chỉ là diagnostic protected/redacted, không đưa nguyên văn vào chart public.

Core CRM áp dụng mọi ngành. V2 không có nguồn transaction/revenue, appointment/revisit, enrollment/completion, shipment/SLA, deposit/contract, retention label, consent, RFM monetary, predictive CLV, NBA hay model outcome; mọi widget đó là `NOT_AVAILABLE`, không suy từ `opportunities.expected_value`.

## W12 và KLTN readiness

W12 có thể tăng số dòng/kỳ lịch sử nhưng không đổi field, enum, PK/FK hay 16 formula. Seed phải idempotent theo business key; không phụ thuộc UUID fixture, 10 Lead/org, 14 Task/org hay tháng 09/2026. DQ tối thiểu: validity enum/timestamp, uniqueness PK/idempotency key, completeness key bắt buộc, tenant isolation, referential integrity, timeline hợp lệ và partition KPI (status/segment/bucket sum = dynamic source total).

KLTN chỉ có thể thêm Data Mart/RFM/CLV/NBA/evaluation sau khi có lịch sử transaction/value với semantics currency/time, consent/contact policy, observation/prediction window, action/outcome label, model version và evaluation cohort được duyệt. Khi đổi formula/provenance, version contract thay vì sửa ngầm KPI W09-01.
