# Evidence KPI giá trị và rò rỉ W16 — 09–10/11/2026

## Trạng thái

Triển khai theo DEC-21 với `dashboard.read`; nhóm E dùng phạm vi đọc và Việc hôm nay dùng phạm vi làm việc. Ngưỡng hệ thống mặc định E01 24 giờ, E02 14 ngày. E04 chờ dữ liệu hội thoại AI-02; từ điển và SQL đối soát độc lập thuộc phần của Văn.

## FINDING-01 — Phạm vi Interaction của E02

`OpportunityService.scope` cho Manager đọc Opportunity khi một dòng Offering thuộc team mình quản lý. `CrmWorkAccess.subjectScope` lại xác định scope Interaction dựa vào Customer/Lead. Vì vậy một Manager có thể đọc Opportunity nhưng không được đọc Interaction gắn Customer/Lead của Opportunity đó. Tài chốt ngày 09/11: mọi điều kiện Interaction nhóm E và Việc hôm nay áp dụng scope của chính người xem. Manager có thể thấy Opportunity trong E02 trong khi Admin không thấy; phải kiểm chứng bằng test.

## Test fixture

| Kịch bản | Giá trị mong đợi và kết quả API/test |
|---|---|
| E01: đúng biên 24 giờ, Lead NEW/QUALIFIED, Interaction tương lai | 2 Lead; Lead mới hơn biên, đã liên hệ, CONVERTED, DISQUALIFIED và archive không tính |
| E02: Interaction ở đúng biên 14 ngày không nằm trong khoảng mở; Interaction gần hơn được tính | USD 123,45 và EUR 0 với numerator 1 mỗi loại; VND đã liên hệ không có trong kết quả |
| E02: Opportunity trong scope Manager, Interaction ngoài scope | Manager 100 VND; Admin 0 — khác nhau có chủ đích theo quyết định Tài |
| E03: hai bàn giao, liên hệ trước bàn giao hoặc bởi người khác | 1/1 chưa liên hệ; sau Interaction của target còn 0/1; kỳ rỗng `NO_DATA_IN_PERIOD` |
| E04 | `NOT_SUPPORTED`, `CONVERSATION_DATA_NOT_AVAILABLE`, value null |
| E05: ACCEPTED, EDITED, REJECTED | Manager/Sales 2/3; Data Staff mẫu số 4; kỳ không có dữ liệu `NOT_AVAILABLE_ZERO_DENOMINATOR` |
| Đổi ngưỡng 24/14 sang 48/7 trong fixture | E01 từ 1 xuống 0; E02 từ 0 lên 100 |
| Việc hôm nay | Đúng thứ tự 5 nhóm, giới hạn 2, hạn 23:30 giờ Việt Nam được xếp trong ngày; mỗi mục có lý do và thời điểm |

Các test backend này chạy trên H2 và PostgreSQL. Fixture là dữ liệu riêng của test, không sửa gói Nhi.

## API trên bản restore sáu tổ chức

Gọi API với actor Admin test trong transaction rollback, `asOf=2026-09-30T17:00:00Z`, kỳ `[2026-08-31T17:00:00Z, 2026-09-30T17:00:00Z)`; đây là số API thực tế, **chưa phải kết quả SQL đối soát độc lập**. E02 đều có numerator 7 và currency VND.

| Mã tổ chức | E01 | E02 value | E03 value / denominator |
|---|---:|---:|---|
| CLI | 1 | 2973500.0 | null / 0 — `NO_DATA_IN_PERIOD` |
| EDU | 1 | 5.9185E7 | 1 / 1 |
| LOG | 1 | 734750.0 | null / 0 — `NO_DATA_IN_PERIOD` |
| REA | 1 | 3.854E10 | null / 0 — `NO_DATA_IN_PERIOD` |
| RET | 1 | 321050.0 | null / 0 — `NO_DATA_IN_PERIOD` |
| TECH | 1 | 2.4575E7 | null / 0 — `NO_DATA_IN_PERIOD` |

Cả sáu tổ chức: E04 value null, `NOT_SUPPORTED`; E05 value null, numerator 0, denominator 0, `NOT_AVAILABLE_ZERO_DENOMINATOR`.

## Phân quyền và kiểm chứng

| Test âm | Kết quả |
|---|---|
| Thiếu `dashboard.read` | 403 `FORBIDDEN_PERMISSION` |
| `ownerUserId` ngoài scope | 403 `FORBIDDEN_SCOPE` |
| Customer Care | E01–E05 `NOT_APPLICABLE`; Việc hôm nay chỉ Task của mình |
| Data Staff đơn role | KPI tổng hợp toàn tổ chức; Việc hôm nay `applicable=false` |
| Data Staff kiêm Manager | KPI tổng hợp toàn tổ chức |
| Manager/Sales, tổ chức khác | Scope theo dữ liệu actor; bản ghi khác team/tenant không lọt |

| Bộ kiểm thử | Tổng | Đạt | Bỏ qua | Lỗi |
|---|---:|---:|---:|---:|
| H2, Surefire XML — lượt 09/11 | 276 | 250 | 26 | 0 |
| PostgreSQL cô lập, Surefire XML — lượt 09/11 | 276 | 263 | 13 | 0 |
| Vitest sau sửa trạng thái tải | 314 | 314 | 0 | 0 |

Backend chạy từ gốc với `mvn -pl backend -am ... verify`; PostgreSQL chạy bằng `backend/scripts/test-postgres-v2.ps1` với gói final, JDK 25.0.2 và Maven 3.9.15, trả `Verified`. `SHOW data_directory` là `D:/POSE/backend/target/auth-postgres-test`; cổng 54329 đã dừng. Frontend typecheck, lint và build đạt; build cảnh báo thiếu `SITE_URL` và dùng fallback local. Kết quả E2E và bản build sạch nằm ở mục bên dưới.

## FINDING-02 — Độ dài số thập phân ở H2

Trong test fixture có tỷ lệ Lead đủ điều kiện 1/3, KPI A03 cũ dùng phép chia numeric của H2 tạo số có 100.000 chữ số; Jackson từ chối đọc vì giới hạn 1.000. Test E01 dùng `metricIds` chỉ chọn nhóm E để cô lập, không sửa công thức W09. Chưa kết luận cùng lỗi xảy ra trên PostgreSQL; cần review riêng nếu tiếp tục dùng H2 với phân số này.

## FINDING-03 — Landmark khi đang tải Việc hôm nay

Lượt E2E đầu sau triển khai đạt 27/31; bốn smoke test thất bại vì skeleton toàn trang tạo thêm `<main>` trong `<main>` của ứng dụng. Đã thay bằng trạng thái tải nội bộ và thêm Vitest xác nhận chỉ có một landmark. Hai lượt E2E sau khi sửa được ghi ở mục "Nghiệm thu 10/11/2026".

## Giới hạn

- E04 chờ dữ liệu hội thoại AI-02; Văn cần hoàn thiện từ điển KPI và SQL đối soát độc lập.
- Agent bị chặn xóa output build nên các số ở trên chưa phải bản sạch; bản sạch đã được chạy lại ở mục "Nghiệm thu 10/11/2026".
- Không kết nối Supabase, PostgreSQL 5432 hoặc Claude thật. Chưa kiểm bằng trình đọc màn hình; kiểm trình duyệt chỉ Chromium.

## Nghiệm thu 10/11/2026

Phiên hoàn tất tại HEAD `cf82bf6` (Claude Code, theo yêu cầu của Tài, sau khi agent hết hạn mức). Số liệu từ Surefire XML và log runner:

| Kiểm thử | Tổng | Đạt | Bỏ qua | Lỗi |
|---|---:|---:|---:|---:|
| workflow-core | 1 | 1 | 0 | 0 |
| Backend H2, bản build sạch | 276 | 250 | 26 | 0 |
| Backend PostgreSQL 54329, bản build sạch (`Verified`) | 276 | 263 | 13 | 0 |
| Frontend Vitest (27 file) | 314 | 314 | 0 | 0 |
| Playwright E2E, lượt 1 với `-Build` | 31 | 31 | 0 | 0 |
| Playwright E2E, lượt 2 không `-Build` | 31 | 31 | 0 | 0 |

Bản build sạch: đã xóa `backend/target/classes`, `test-classes`, `surefire-reports`, `automation/workflow-core/target`; không chạy `mvn clean`, giữ cluster `backend/target/auth-postgres-test`. `typecheck` và `lint` đạt. Cả hai lượt E2E gồm `value-leakage.spec.ts` (Manager thấy nhóm E và mở một mục ưu tiên; Customer Care không thấy nhóm E); các cổng 8080, 8081, 3010, 3011, 54329 đã dừng sau mỗi lượt.

Giới hạn còn lại: E04 chờ AI-02; từ điển KPI và SQL đối soát độc lập thuộc Văn; chưa gọi Claude thật; E2E chỉ Chromium; chưa kiểm bằng trình đọc màn hình.

## Đối soát của reviewer 10/11/2026

Reviewer (Claude Code) tự tính lại E01, E02 và E03 từ các file CSV của gói `POSE_CRM_DATA_V2_FINAL` bằng script Python độc lập, không qua API và không dùng SQL của agent, tại cùng `asOf=2026-09-30T17:00:00Z` và ngưỡng 24 giờ, 14 ngày:

| Tổ chức | E01 (script) | E02 số cơ hội / giá trị (script) | Khớp API |
|---|---:|---|---|
| EDU | 1 | 7 / 59.185.000 | Khớp |
| CLI | 1 | 7 / 2.973.500 | Khớp |
| LOG | 1 | 7 / 734.750 | Khớp |
| REA | 1 | 7 / 38.540.000.000 | Khớp |
| RET | 1 | 7 / 321.050 | Khớp |
| TECH | 1 | 7 / 24.575.000 | Khớp |

E03: seed chỉ có đúng một bàn giao `SALES` ở trạng thái `APPROVED` (tổ chức EDU, duyệt 25/09/2026), nên EDU 1/1 và năm tổ chức còn lại `NO_DATA_IN_PERIOD` là đúng. Đây là đối soát của reviewer, không thay thế SQL đối soát độc lập của Văn.

Ghi chú review (không chặn nghiệm thu):
- Liên kết của mục `HANDOVER_NOT_CONTACTED` đang trỏ `/app/khach-hang/{id}`; nên trỏ `/app/khach-hang-360/{id}` để người nhận thấy ngay Copilot và hồ sơ bàn giao.
- Danh sách "Việc hôm nay" chưa hiện tên người phụ trách dù API có `ownerName`; với Manager (xem Task của cả team) nên hiện để biết việc của ai.
- Theo DEC-21 D4, Manager nhận Task theo `taskScope` của cả team, nên danh sách 20 mục có thể bị Task của người khác chiếm chỗ; cần Tài xác nhận đây là ý muốn.
