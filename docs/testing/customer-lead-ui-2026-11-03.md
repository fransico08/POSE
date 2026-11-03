# W04 — giao diện Customer và Lead

Ngày 03/11/2026 · Owner: Huỳnh Minh Tài · checkout `D:\POSE`, nhánh `Tài`; baseline `7f72c4d`. Skill đã dùng: [frontend-ui-engineering](../../.agents/skills/frontend-ui-engineering/SKILL.md), để triển khai giao diện, state, responsive và accessibility theo design system hiện có. Không tạo token/font hoặc thêm package.

## Phạm vi đã thực hiện

- Lead: `/app/lead`, `/app/lead/them`, `/app/lead/[id]`, `/app/lead/[id]/chinh-sua`. Customer có bốn route tương tự dưới `/app/khach-hang`.
- Danh sách có URL query, tìm kiếm debounce, lọc, phân trang, loading/empty/lỗi/403, bảng desktop và thẻ mobile. Hồ sơ, tạo/sửa, lỗi trường, trùng, xung đột phiên bản và cảnh báo thay đổi chưa lưu dùng BFF.
- Manager/Sales có nút tạo khi có quyền ghi; Admin có thể sửa theo permission nhưng không tự tạo nếu chỉ có role Admin; Customer Care chỉ đọc Customer; Data Staff không thấy menu và nhận giao diện không có quyền khi vào trực tiếp. Các kiểm tra này phục vụ UX; backend vẫn áp permission và scope.
- Lead hỗ trợ qualify và dialog disqualify có lý do; Lead đóng không hiện sửa. Chưa làm chuyển Lead, bàn giao, đổi owner hoặc Customer 360.
- BFF chỉ chuyển tiếp `details.duplicateId` là UUID hợp lệ cho lỗi trùng 409; API client giữ trường này. Không thay token, refresh hoặc session. Form gửi `expectedUpdatedAt` khi PATCH, không gửi owner và dùng null cho trường tùy chọn đã xóa.

## Môi trường và lệnh

Windows; Node 24.13.0, npm 11.6.2, Next 16.4.3, React 19.3.0, Vitest 5.0.4; Java 25.0.2 và Maven 3.9.15. Frontend dùng dependency đã có; không chạy npm install/ci. Build production cảnh báo SITE_URL chưa cấu hình và dùng fallback local.

| Kiểm | Lệnh | Kết quả thực chạy |
|---|---|---|
| Frontend lint | `npm run lint` | Đạt, không lỗi/cảnh báo |
| Frontend typecheck | `npm run typecheck` | Đạt |
| Frontend test | `npm test` | **167/167**, 21 file; mốc 133 → tăng 34 |
| Frontend build | `npm run build` | Đạt, tám route mới có trong kết quả build |
| Backend H2 | Maven `'-Dlogging.level.root=ERROR' verify`, sau khi xóa POSE_TEST_*, POSE_V2_SMOKE_DATABASE_URL, POSE_DEMO_PASSWORD và cấu hình POSE_DATABASE khỏi process | **134 tổng: 118 đạt, 16 skip, 0 lỗi**; mốc 116 đạt/16 skip |
| Backend PostgreSQL | `powershell.exe -NoProfile -File .\scripts\test-postgres-v2.ps1 -DataPackagePath 'D:\POSE-data\POSE_CRM_DATA_V2_FINAL' -JdkDirectory 'C:\Program Files\Java\jdk-25.0.2' -MavenPath 'C:\Users\Dell\.maven\maven-3.9.15\bin\mvn.cmd'` | **134 tổng: 121 đạt, 13 skip, 0 lỗi**, có `Verified.`; mốc 119 đạt/13 skip |
| PostgreSQL sau kiểm | Kiểm listener `127.0.0.1:54329` | Không còn lắng nghe |

Maven chạy với `JAVA_HOME=C:\Program Files\Java\jdk-25.0.2`; PostgreSQL dùng script và cluster test 54329 của repo, không pipe output `pg_ctl`. Hai test backend mới kiểm tìm `0910000001` khớp số chuẩn hóa `+84910000001` trên cả Lead/Customer và thông báo độ dài trường bắt buộc. Không kết nối Supabase hoặc PostgreSQL 5432, không đăng nhập tài khoản demo, không thêm migration.

## Kiểm thử giao diện và accessibility

Vitest/RTL thực chạy kiểm menu bốn vai trò; danh sách, query URL, loading qua query, empty, retry và 403; Customer Care đọc; validation liên hệ, fieldErrors và focus; tạo Lead/Customer, thông báo thành công; duplicate có/không ID; owner candidates theo Offering và 422; PATCH version/null/không owner và tải lại sau conflict; trạng thái Lead, lý do disqualify, Escape và trả focus. Test BFF kiểm chỉ chuyển tiếp UUID cho mã trùng và client giữ details. Test cũng kiểm lấy sản phẩm ACTIVE qua nhiều trang và xác nhận khi rời form có thay đổi.

- [x] Field có label; email/tel có autocomplete và inputmode; lỗi trường liên kết `aria-describedby` và focus về lỗi đầu.
- [x] Test bàn phím Escape đóng dialog và trả focus; focus trap dùng helper chung, bao gồm textarea/select.
- [x] Bảng có caption; giao diện mobile có thẻ tương ứng; trạng thái từ chối không tiết lộ dữ liệu ngoài scope.
- [x] Nút và màu dùng token/component đã có; không thêm animation mới.
- [ ] Chưa kiểm trình đọc màn hình, bàn phím toàn trang, tương phản đo thực tế, responsive/zoom bằng trình duyệt thật. Không tuyên bố đạt toàn bộ WCAG 2.2 AA.
- [ ] **Chưa E2E**: phiên không có công cụ trình duyệt phù hợp; không chạy backend local với database cô lập để kiểm qua trình duyệt. Không dùng tài khoản demo hoặc dữ liệu thật để thay thế.

## FINDING và lựa chọn tạm

- **FINDING — quyền tra cứu Offering:** contract Customer/Lead cho phép Manager hoặc Sales tạo khi có quyền ghi, nhưng API `/offerings` còn yêu cầu `offering.manage` cho Manager/Admin hoặc `offering.read` cho Sales. Tài khoản có quyền ghi CRM nhưng thiếu quyền đọc danh mục vẫn thấy nút tạo theo contract song không thể chọn Offering; giao diện thông báo và chặn gửi. Cần Tài/owner quyết định cấp quyền tương ứng hoặc thiết kế lookup theo scope; không tự nới quyền backend.
- **FINDING — mẫu URL:** OfferingList hiện giữ filter trong state, không đồng bộ URL như mô tả của yêu cầu. Card này tự đồng bộ query cho Customer/Lead, không sửa OfferingList.
- Bộ lọc owner của Manager/Admin tạm nhập UUID, kiểm hợp lệ trước khi gửi; contract list không cung cấp lookup owner toàn cục. Form tạo Manager dùng owner-candidates theo Offering. Manager đồng thời mang role Sales vẫn được yêu cầu chọn owner trên form theo yêu cầu UI, dù backend có nhánh tạo cho chính mình.
- Bảo vệ dữ liệu chưa lưu dùng xác nhận trình duyệt cho link, Back và beforeunload; đã test mô phỏng, chưa kiểm E2E các cách điều hướng thực tế. Các record V9 cũ null được hiển thị “Chưa cập nhật” và phải bổ sung trường bắt buộc khi sửa.
- Không chạy E2E với tài khoản demo; không triển khai convert/bàn giao/Customer 360. Không sửa business rules, roles, wireframe, Google Doc hoặc gói dữ liệu của Nhi.

Commit: backend `87c902f`; Lead `0f285c7`; Customer/navigation/test `2b5f505`; bổ sung test loading và trường bắt buộc `4eb820d`. Chỉ commit cục bộ, chưa push.
