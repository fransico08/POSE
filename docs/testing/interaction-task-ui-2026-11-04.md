# Giao diện Interaction và CRM Task — 04/11/2026

Làm việc tại `D:\POSE`, nhánh `Tài`, từ `2c4c6df`. Dùng skill `frontend-ui-engineering`, component và token hiện có; không thêm package hoặc thay backend.

| Phạm vi | Kết quả |
|---|---|
| Route | `/app/tuong-tac` và `/app/nhiem-vu`, cùng `/them`, `/[id]`, `/[id]/chinh-sua` cho mỗi module. |
| API và quyền | Qua BFF; query key có tổ chức/người dùng. Quyền từ phiên kết hợp role theo contract; backend quyết định scope. Owner Manager lấy từ `/users`, lọc role/ACTIVE và phân trang. |
| Giờ | Input `dd/MM/yyyy HH:mm` theo `Asia/Ho_Chi_Minh`, chuyển sang UTC; test có ca qua nửa đêm. |
| Interaction | Có danh sách/lọc, tạo, chi tiết và sửa; nội dung null được hiển thị là bị ẩn. Các mã loại/kênh chỉ là gợi ý UI, vẫn cho nhập mã đúng định dạng. |
| Task | Có danh sách/lọc, tạo, chi tiết/sửa và dialog start/complete/cancel/assign. Trạng thái đóng không hiện sửa hoặc action. |
| Test | 22 test W06 trong `tests/work-ui.test.tsx`; toàn bộ 24 file, **252/252 test đạt**. Kiểm permission menu/tạo, visibility, redaction, lỗi field, conflict, giờ, lọc URL, cấu trúc mobile card, hành động Task và focus/Esc dialog. |
| `npm run typecheck`, `npm run lint` | Đạt. |
| `npm run build` | Đạt; cảnh báo `SITE_URL` chưa cấu hình như trước. |

**FINDING:** API Opportunity không hỗ trợ lọc `leadId`; request qua BFF có `leadId` trả HTTP 200 cùng các bản ghi khác Lead. Bộ chọn Opportunity đối với Lead bị vô hiệu hóa. Người không có `opportunity.read` cũng không thể tải bộ chọn Opportunity. Xem [evidence QA](interaction-task-access-2026-11-04.md).

**Giới hạn:** Chưa kiểm UI bằng trình duyệt, screen reader, bàn phím toàn trang hoặc responsive trên thiết bị thực; không tuyên bố đạt toàn bộ WCAG. Test RTL không thay thế acceptance UI. Một số ô trong ma trận quyền chưa được kiểm riêng; phần còn lại và kết quả thực tế được ghi ở evidence QA.

## Bổ sung 05/11/2026

QA trên bản sao W06 đã xác nhận Data Staff có thể đọc metadata Interaction nhưng `GET /customers` và `GET /leads` qua BFF đều trả `403 FORBIDDEN_SCOPE`, phù hợp scope CRM hiện hành. Chưa có API danh sách đối tượng phù hợp để chọn bộ lọc; bộ chọn hiển thị rõ việc không có quyền tải danh sách. Đây là giới hạn phụ thuộc API, không phải yêu cầu nới quyền. Các thao tác theo bảy vai trò được bổ sung trong [evidence QA](interaction-task-access-2026-11-04.md#bổ-sung-ma-trận--05112026).

Đã thêm một test cho thông báo 403 của bộ chọn. Sau khi dừng các dịch vụ QA: `npm run typecheck`, `npm run lint`, `npx vitest run` **253/253 test trên 24 file** và `npm run build` đạt. Một lượt Vitest chạy đồng thời với dịch vụ QA trước đó có hai test cũ hết thời gian chờ; chạy lại sau khi dừng dịch vụ đạt toàn bộ. Không đủ bằng chứng để kết luận nguyên nhân của hai timeout. Build vẫn cảnh báo thiếu `SITE_URL`.

## Khắc phục review — 05/11/2026

| Mục | Thay đổi và kiểm chứng |
|---|---|
| R1 | Interaction và Task trả thêm tên Customer, Lead và tiêu đề Opportunity bằng JOIN cùng organization, giữ các ID cũ. Integration test kiểm tra list, detail, command, Customer Care và metadata Data Staff. |
| R2 | Opportunity list nhận `leadId` trong scope đọc hiện có; dùng đồng thời Customer/Lead trả 400 tại `leadId`. Integration test kiểm lọc, ngoài scope và tham số xung đột. FINDING bộ lọc Lead ở trên đã được xử lý. |
| R3 | Danh sách bảng và thẻ mobile hiển thị đối tượng, chi tiết/form sửa hiển thị tên và tiêu đề. Bộ chọn tải cơ hội theo Lead; dữ liệu thiếu trường tên vẫn hiển thị an toàn. RTL bổ sung 4 test. |

Kiểm chứng thực tế tại checkout `D:\POSE`, nhánh `Tài`, dùng skill `frontend-ui-engineering`:

| Lệnh | Kết quả |
|---|---|
| `npm run typecheck`, `npm run lint`, `npm run build` | Đạt; build cảnh báo `SITE_URL` chưa cấu hình. |
| `npx vitest run` | 24 file, **257/257** đạt; mốc trước 253/253. |
| Maven `verify` với H2, JDK 25.0.2 | Surefire: **189 test, 172 đạt, 17 skip, 0 lỗi**; mốc 187/170 đạt. Maven biên dịch lại 89 file mã và 17 file test. |
| `scripts/test-postgres-v2.ps1` với gói V2 FINAL, cluster 127.0.0.1:54329 | Surefire: **189 test, 176 đạt, 13 skip, 0 lỗi**; mốc 187/174 đạt. Script báo `Verified.`, đối soát dữ liệu và dừng cluster; kiểm tra lại 54329 không lắng nghe. |

**Giới hạn kiểm chứng backend:** Lệnh xóa riêng `backend/target/classes` và `backend/target/test-classes` trước khi chạy bị cơ chế kiểm duyệt tự động từ chối với lý do “blocked by policy”. Vì vậy cả hai kết quả backend ở trên là kết quả chạy thật nhưng **không đáp ứng điều kiện chạy sau khi dọn class**; cần chạy lại trên bản build sạch khi môi trường cho phép. Các biến database/test nhạy cảm được kiểm tra là không có trong process trước khi chạy. Không kết nối Supabase hoặc cổng 5432.

Chưa kiểm UI bằng trình duyệt, screen reader hoặc responsive thực tế; test RTL không chứng minh toàn bộ WCAG. DEC-14 về Lead đã đóng và chuyển Interaction/Task vẫn chờ quyết định, không thay đổi trong lần này.
