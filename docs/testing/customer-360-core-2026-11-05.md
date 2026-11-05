# W07 — Bằng chứng Customer 360 core

Ngày 05/11/2026; checkout `D:\POSE`, nhánh `Tài`, xuất phát `de579e8`. Backend Java target 21, JDK 25.0.2, Maven 3.9.15; H2 MODE PostgreSQL và PostgreSQL 18 trên cluster cô lập `127.0.0.1:54329`. Không kết nối Supabase hoặc PostgreSQL 5432; không thêm migration (không có V12).

## Phạm vi đã làm

| Route | Nội dung |
|---|---|
| `GET /api/v1/customers/{id}/360` | Hồ sơ chỉ đọc theo DEC-15: định danh, owner, team của owner, assignee CARE, nguồn, Lead đã chuyển, Opportunity, Interaction, Task, Feedback, Support; mỗi phần có `restricted` theo permission/scope |
| `GET /api/v1/customer-360/reconciliation` | Admin/Data Staff so số đếm trực tiếp từ bảng gốc với view `customer_360_summary` theo từng Customer của organization |

Contract: [customer-360-api-v1.md](../api/customer-360-api-v1.md). Quyết định: DEC-15 trong decision log.

## Kiểm thử

| Nhóm | Bằng chứng |
|---|---|
| Scope theo vai trò | Sales owner/assignee, Manager trong/ngoài team, Care có/không có assignment CARE, Admin, Data Staff, tenant khác, ID không tồn tại → 403 `FORBIDDEN_SCOPE`/`FORBIDDEN_PERMISSION` đúng contract |
| D1 — số đếm theo quyền | Care đếm 1/2 Interaction (chỉ `CARE_VISIBLE`), Opportunity `restricted`, chỉ thấy Task mình phụ trách; Sales/Manager đếm đủ |
| D2 — Data Staff | Không có email/phone/địa chỉ/ghi chú; nội dung Interaction/Task và tiêu đề Opportunity `null`; vẫn có số đếm. Actor kiêm Data Staff và Admin vẫn chỉ nhận metadata |
| Feedback | Theo dòng “Interaction, Feedback” của ma trận quyền: Sales 2 bản ghi, điểm trung bình 2,5; Care 1 bản ghi `CARE_VISIBLE`, 4,0; Data Staff 2; response không chứa nội dung Feedback |
| DEC-13/14 | Lead đã chuyển xuất hiện ở `leads`; Interaction/Task chuyển cùng Lead xuất hiện trong hồ sơ Customer |
| Task | Quá hạn tính theo `Clock`; ưu tiên `MEDIUM` giữ nguyên |
| Đối soát | Dữ liệu khớp → 0 lệch; thêm `source_record_links` → đúng Customer, cột `source_count`; Sales → 403 |
| Đọc không ghi audit | Số `audit_logs` không đổi sau các lần đọc 360 |
| Batch | `Customer360BatchRedactionTest`: số lần kiểm quyền không tăng theo số bản ghi Interaction/Task (không N+1) |

## Kết quả trên bản restore V2 (PostgreSQL)

| Tổ chức | Customer | Lệch so với view | Interaction | Task mở/đang làm | `expected_kpis.csv` |
|---|---:|---:|---:|---:|---|
| CLI | 20 | 0 | 26 | 8 | Khớp |
| EDU | 20 | 0 | 26 | 8 | Khớp |
| LOG | 20 | 0 | 26 | 8 | Khớp |
| REA | 20 | 0 | 26 | 8 | Khớp |
| RET | 20 | 0 | 26 | 8 | Khớp |
| TECH | 20 | 0 | 26 | 8 | Khớp |

`EXPLAIN` trên bản restore: truy vấn Interaction dùng index; truy vấn Task, Opportunity và đối soát dùng sequential scan vì mỗi tổ chức chỉ có 20 Customer, planner chọn quét tuần tự là hợp lý ở cỡ dữ liệu demo. Chưa cần index mới; xem lại khi dữ liệu tăng (W13).

`scripts/test-postgres-v2.ps1` truyền thêm `POSE_V2_DATA_PACKAGE_PATH` để test KPI đọc `expected_kpis.csv` (chỉ đọc) và khôi phục biến sau khi chạy.

## Số test (bản sạch)

Chạy sau khi xóa `target/classes`, `target/test-classes`, `target/surefire-reports`; số đọc từ Surefire XML.

| Bộ | Tổng | Đạt | Skip | Lỗi | So với mốc |
|---|---:|---:|---:|---:|---|
| H2 | 202 | 181 | 21 | 0 | +5 đạt so với 176 |
| PostgreSQL | 202 | 189 | 13 | 0 | +8 đạt so với 181; `Verified.` |

Cổng 54329 không lắng nghe sau khi chạy.

## FINDING và giới hạn

- **Data Staff và Opportunity:** scope Opportunity từ chối Data Staff (DEC-12 F4), nên phần Opportunity trong hồ sơ của Data Staff là `restricted`; số đếm Opportunity vẫn có qua API đối soát.
- **Data Staff kiêm vai trò khác (W06):** hồ sơ 360 che nội dung cho mọi actor có role Data Staff vì scope hợp role cho Data Staff đọc toàn tổ chức. API Interaction của W06 hiện chỉ che khi Data Staff là vai trò duy nhất, nên actor vừa Data Staff vừa Manager có thể đọc nội dung Interaction ngoài team. Dữ liệu demo không có user như vậy (6 Data Staff, đều đơn vai trò). Cần quyết định trước khi cấp role kép.
- View `customer_360_summary` (baseline V1) không lọc organization trong subquery; đối soát tính bảng gốc theo organization nên liên kết sai tenant sẽ hiện thành chênh lệch.
- Chưa có giao diện (card W07 Giao diện) và chưa áp gì lên Supabase; W07 không thêm migration nên không cần thao tác database.
