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
