# Kiểm chứng UI Opportunity và chuyển Lead

Ngày 04/11/2026 · `D:\POSE`, nhánh `Tài`, xuất phát từ `c28db1a`. Dùng skill `frontend-ui-engineering` để triển khai theo component và token sẵn có. Kiểm thử với Vitest/Testing Library, mock BFF; không kết nối Supabase, PostgreSQL 5432 hoặc tài khoản demo.

| Kiểm thử | Kết quả |
|---|---|
| `npm run typecheck` | Đạt |
| `npm run lint` | Đạt, không warning |
| `npx vitest run` | 23 file, **216/216 test đạt**, tăng 28 so với mốc 188 |
| `npm run build` | Đạt; `/app/co-hoi`, `/them` được tạo tĩnh, route chi tiết/sửa động. Build cảnh báo thiếu `SITE_URL` và dùng local fallback cho metadata công khai |
| Pipeline và phân quyền | Test tải BFF, sáu cột, lọc/URL, phân trang, empty/network/403; Admin không tạo, Sales không có lọc owner, Care không có menu |
| Form và tiền | Test `%` quy ra số tiền, số lẻ bằng BigInt, trùng sản phẩm, 50 dòng, ngày quá khứ, owner Manager và ứng viên, lỗi dòng/owner; PATCH version và loại field bất biến |
| Chi tiết Opportunity | Test endpoint win/lose/cancel/reopen, reason, ngày tương lai, trạng thái khóa và xử lý xung đột |
| Chuyển Lead | Test quyền/trạng thái, Customer mới hoặc có sẵn, conflict có/không duplicateId, lỗi owner và tải lại khi Lead đã đổi |
| BFF | Test whitelist `LEAD_CONVERSION_CONFLICT` chuyển thông báo và UUID `duplicateId` hợp lệ, loại trường khác và UUID sai |
| Accessibility tự động | Test label, `aria-invalid`, liên kết lỗi và focus tại dòng, vòng focus và Esc của dialog. Không dùng kết quả này để tuyên bố đạt toàn bộ WCAG |

**Giới hạn:** Chưa E2E trình duyệt: phiên không có công cụ trình duyệt phù hợp và không chạy backend cục bộ trên database cô lập. Chưa acceptance bằng tài khoản demo. Chưa kiểm tay bàn phím toàn trang, screen reader, thiết bị chạm, responsive 320 px hoặc zoom 200/400%; CSS dùng các breakpoint sẵn có nhưng chưa đo thực tế. Không chạy test backend vì không sửa backend.

**Đối chiếu:** Backend search Opportunity hỗ trợ tên cơ hội, Customer/Lead và Offering như contract hiện hành. Không phát hiện API backend lệch contract trong phạm vi đối chiếu mã. BFF cũ chỉ cho `duplicateId` của lỗi trùng Lead/Customer và che message; đã bổ sung riêng `LEAD_CONVERSION_CONFLICT` để đáp ứng luồng chuyển Lead.

## Khắc phục review 04/11/2026

Thực hiện tại `D:\POSE`, nhánh `Tài`, từ `e726971`; dùng skill `frontend-ui-engineering` và các component/token hiện có. Test mô phỏng BFF, không kết nối backend hoặc dữ liệu dùng chung.

| Điểm kiểm | Kết quả |
|---|---|
| Bộ chọn khách hàng pipeline | Test chọn khách hàng, khôi phục tên từ danh sách hoặc hồ sơ, xử lý 403/404 với nhãn dự phòng và nút bỏ lọc; UUID sai không được gửi tới API. Lookup tùy chọn không chuyển trang khi 403; lỗi 403 thông thường vẫn theo hành vi API client sẵn có. |
| Debounce | Fake timer xác nhận năm lần gõ liên tục chỉ tạo một request tìm pipeline và một request tìm khách hàng trong form; select cập nhật ngay. |
| Quy đổi chiết khấu | Test đổi 300.000 ₫ → 10,00% → 300.000,00 ₫ bằng BigInt; đơn giá không hợp lệ đưa về 0 và thông báo trạng thái. |
| Owner theo vai trò | Test Manager kiêm Sales có thể chọn “Tôi phụ trách” và không gửi `ownerUserId`; Manager đơn vai trò vẫn phải chọn. |
| Dòng nháp | Test xóa dòng giữa giữ nguyên phần tử và giá trị của dòng còn lại, đồng thời xóa lỗi dòng cũ. |
| `npm run typecheck` | Đạt. |
| `npm run lint` | Đạt, không warning. |
| `npx vitest run` | 23 file, **229/229 test đạt**, tăng 13 so với mốc 216. |
| `npm run build` | Đạt; Next.js 16.4.3 cảnh báo chưa đặt `SITE_URL` và dùng địa chỉ dự phòng cho metadata. |

**Giới hạn:** Chưa E2E hoặc kiểm thủ công trên trình duyệt; các kiểm tra trên dùng Vitest/Testing Library và không thay thế kiểm thử accessibility đầy đủ. Không chạy test backend; không kết nối Supabase hoặc PostgreSQL 5432.
