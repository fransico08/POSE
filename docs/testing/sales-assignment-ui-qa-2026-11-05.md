# QA màn hình Phân công theo D1/D2 — 05/11/2026

Card **Xây dựng Sales Assignment và kiểm soát phạm vi truy cập**, mục **[Thắng] FE/QA — kiểm lại màn hình Phân công theo quy tắc D1/D2**. Claude Code thực hiện theo yêu cầu của Tài, trên nhánh `Tài`. Thắng review kết quả trước khi tick mục.

Quy tắc đối chiếu: D1/D2 trong [sales-assignment-scope-integration-2026-11-02.md](sales-assignment-scope-integration-2026-11-02.md) và mục 1 của [sales-assignment-api-v1.md](../api/sales-assignment-api-v1.md).

## Cách kiểm

Kịch bản tự động `frontend/e2e/sales-assignment.spec.ts`, chạy bằng `scripts/e2e-env.ps1` trên bản sao database cô lập (cluster `127.0.0.1:54329`, sao chép từ `pose_v2_review_*` mới nhất, xóa sau mỗi lượt). Người thao tác là Manager EDU (`edu.mgr`). Dữ liệu tạo mới dùng khoảng hiệu lực năm 2098–2099 để không giao với phân công demo (mọi phân công ACTIVE của EDU kết thúc 01/01/2027).

## Kết quả

| Quy tắc | Ca kiểm trên giao diện | Kết quả |
|---|---|---|
| D1 — Offering được chọn | Ô "Sản phẩm hoặc dịch vụ" chỉ có Offering ACTIVE có team; không có `CORPENG-PILOT` (DRAFT) và `TOEIC-WEEKEND-OLD` (INACTIVE) | Đạt |
| D1 — Sales theo team | Chọn DATABOOT → ô "Nhân viên kinh doanh" đúng 2 Sales của team SALES (`edu.s01`, `edu.s02`); không có Manager, Customer Care, Data Staff | Đạt |
| D2 — Chồng thời gian khi tạo | Có phân công 01/01–31/01/2099; tạo thêm 15/01–15/03/2099 cho cùng Offering và Sales → thông báo "Khoảng thời gian phân công trùng với một phân công đang hoạt động.", không có bản ghi mới | Đạt |
| D2 — Khoảng liền kề | Tạo 01/02–28/02/2099 (ngay sau 31/01 23:59) → thành công | Đạt |
| D2 — Chồng thời gian khi gia hạn | Gia hạn phân công tháng 1 đến 15/02/2099 trong khi phân công tháng 2 còn ACTIVE → bị chặn với cùng thông báo | Đạt |
| D2 — REVOKED không chặn | Thu hồi phân công tháng 2 (không có hồ sơ mở) rồi gia hạn tháng 1 đến 15/02/2099 → thành công; API còn đúng 1 phân công ACTIVE và 1 REVOKED trong năm 2099 | Đạt |
| D1 — Theo thời gian, Sales chưa thấy | `edu.s02` chỉ có phân công IELTS65 đã thu hồi → không thấy IELTS65; tạo phân công năm 2099 (Sắp có hiệu lực) → vẫn không thấy | Đạt |
| D1 — Có hiệu lực thì thấy | Tạo phân công từ hiện tại đến 31/12/2098 → `edu.s02` thấy IELTS65 qua API và trên `/app/san-pham` | Đạt |
| D1 — Thu hồi thì mất quyền | Thu hồi phân công hiện tại → `edu.s02` không còn thấy IELTS65; GET chi tiết trả 403 | Đạt |
| Sales/Care/Data Staff mở `/app/phan-cong` | Thông báo "Bạn chưa có quyền quản lý phân công" (đã có trong `menu.spec.ts`) | Đạt |

Toàn bộ bộ E2E sau khi thêm kịch bản: 2 lượt liên tiếp, mỗi lượt 17/17 đạt (21,1 giây; 27,1 giây). Vitest 280/280, typecheck và lint đạt. Sau mỗi lượt, 5 cổng 8080, 8081, 3010, 3011, 54329 đều dừng. Không kết nối Supabase hoặc cổng 5432.

## Giới hạn

- Dữ liệu demo mỗi tổ chức chỉ có một team sở hữu Offering (team SALES), nên chưa kiểm trên giao diện trường hợp Manager quản lý hai team hoặc Sales thuộc team khác. Ca này có trong `SalesAssignmentIntegrationTest` ở backend.
- Chưa kiểm thu hồi khi còn hồ sơ mở trên giao diện (`SALES_ASSIGNMENT_OPEN_WORK`); backend đã có test.
- Chỉ chạy trên Chromium.
