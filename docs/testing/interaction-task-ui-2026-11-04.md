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
