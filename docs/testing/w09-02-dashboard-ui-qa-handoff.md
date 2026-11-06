# W09-02 — QA handoff: Dashboard UI ↔ API ↔ PostgreSQL

## Điều kiện chạy

Nhi chỉ bàn giao SQL/data contract; toàn bộ case dưới đây là **NOT RUN** đến khi có API/UI tích hợp. Tài chạy bằng tenant/role đã xác thực; Thắng không dùng organization UUID khác quyền để truy vấn. Mở `database/scripts/verify_w09_02_dashboard_ui_gui.sql` trong pgAdmin 4/SQLTools, giữ `BEGIN READ ONLY`, thay đồng nhất `organization_id`, `period_start`, `period_end`, `as_of` trong mọi CTE, rồi chạy R00–R07.

Ghi UTC thực thi, timezone hiển thị `Asia/Ho_Chi_Minh`, SQL output, API request/response và screenshot UI. API phải trả `metricId`, `organizationId`, dimensions, raw `value`, `unit`, `numerator`, `denominator`, `availability`, `reason`, `periodOrAsOf`, `generatedAt` và period/filter đã áp dụng. UI format rate 2 decimals nhưng không sửa raw value.

## Ma trận acceptance

| ID | Actor/filter | SQL evidence | Expected UI/API | Owner | Status |
|---|---|---|---|---|---|
| Q01 | Authorized user, một org, September 2026 | R00, R01 | A01 và tổng A02 trùng; A03/A04 raw ratio trùng | Tài/Thắng | NOT RUN |
| Q02 | Cùng org/range | R02 | A05, D01 channel, D02 recorder cùng total; no-response riêng | Thắng | NOT RUN |
| Q03 | Cùng org, `asOf` xác định | R03 | A06 bốn segment cộng đúng Customer; ACTIVE <30, AT_RISK 30–60, INACTIVE >60, NEVER NULL | Thắng | NOT RUN |
| Q04 | Cùng org/range | R04 | B01/B02 snapshot và B05 completed-period có denominator/raw rate giống SQL | Thắng | NOT RUN |
| Q05 | Cùng org/range | R05 | B03/B04 bucket sums trùng Task total; C01–C03 chỉ terminal workflow | Thắng | NOT RUN |
| Q06 | Cùng org | R06 | Mọi violation = 0; API không nhân row do join | Tài | NOT RUN |
| Q07 | Tenant A, đổi URL/body sang tenant B | R00 A/B chạy riêng | API 403/404 hoặc only A; UI không render B/zero giả | Tài/Thắng | NOT RUN |
| Q08 | Staff owner scope | R04/R05 với owner được cấp quyền | Cards/charts/list cùng owner; không lộ owner khác | Tài/Thắng | NOT RUN |
| Q09 | Default last 30 days sau seed tháng 09/2026 | R02/R05 với cùng range | Không record → `NO_DATA_IN_PERIOD`, không 0% rate | Thắng | NOT RUN |
| Q10 | Seed window `[2026-09-01,2026-10-01)` | R01–R05 | Có record demo khi source có; chart/card reconcile | Thắng | NOT RUN |
| Q11 | Zero denominator | R04/R05 | B05/C01 là N/A/NOT_AVAILABLE, không 0% | Thắng | NOT RUN |
| Q12 | Đổi date, owner, workflow rồi refresh | R00–R05 với filter mới | API echo filter; widgets cùng asOf; chart cũ bị clear | Tài/Thắng | NOT RUN |
| Q13 | Retail/Clinic/Education/Logistics/Realty/SaaS | R07 + `organizations.industry` | Chỉ core CRM; revenue/appointment/enrollment/shipment/contract/retention là NOT_AVAILABLE | Thắng | NOT RUN |
| Q14 | `start >= end`, timezone/filter invalid | API validation | 400; không broad query/zero sai | Tài | NOT RUN |
| Q15 | API 5xx/access denied | Browser/API evidence | ERROR/access state rõ ràng; không hiển thị zero như dữ liệu mới | Thắng | NOT RUN |

## Evidence và sign-off

Mỗi case ghi: case ID, environment/database, reviewer, role, authorized organization UUID, owner scope, `start/end/asOf/timezone`, SQL block, API request/response (redact token/PII), screenshot, SQL/API/UI values, expected/actual, PASS/FAIL/NOT RUN và defect link. PASS yêu cầu đủ ba output cùng input; SQL-only chỉ là database evidence. R06 PASS là persisted-data integrity, không chứng minh workflow runtime/concurrency.

## Dependencies

Tài: implement endpoint parameterized, authenticated/role-scoped theo `docs/implementation/w09-02-sector-kpi-ui-contract.md`; giữ KPI/formula W09-01, review query plan và pagination. Thắng: bind filter/empty/error state và lưu evidence matrix. Nhi: hỗ trợ SQL/data semantics. Chưa có UI/API integration được tuyên bố; Data Mart/RFM/CLV/NBA cần transaction, consent, outcome, model-version source được duyệt.
