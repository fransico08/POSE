# Acceptance Opportunity và chuyển Lead — 04/11/2026

Thực hiện tại `D:\POSE`, nhánh `Tài`, HEAD kiểm thử `7f06dbd`. Không sửa mã nguồn. Kiểm qua BFF `http://127.0.0.1:3011`, backend `127.0.0.1:8081` và PostgreSQL `127.0.0.1:54329`. Trước thao tác đã kiểm `SHOW data_directory` là `D:/POSE/backend/target/auth-postgres-test`; URL backend là `jdbc:postgresql://127.0.0.1:54329/pose_v2_acceptance_202611041315`. Không kết nối Supabase hoặc PostgreSQL 5432.

Database `pose_v2_acceptance_202611041315` được giữ lại. Nguồn là `pose_v2_review_20261104030055798`; trước kiểm thử bản sao có Flyway V10, 72 Opportunity và 72 dòng. Flyway backend báo up to date. Đã chạy hai lượt trên cùng bản sao; lượt đầu chọn Lead không có Opportunity mở, lượt sau chọn Lead có một Opportunity mở. Mỗi lượt sinh mật khẩu thử nghiệm ngẫu nhiên trong process, dùng BCrypt của backend và chỉ cập nhật credentials của năm user trong bản sao.

| Vai trò | User ID |
|---|---|
| Admin | `cd6bed61-dbbc-52bc-b0e0-5e8598d89dbf` |
| Manager | `a6fd80d1-2f4f-57d8-8650-c8ed8870a0b9` |
| Sales A | `16bf03a9-05d2-57ef-9017-be06b9552839` |
| Sales B (đồng thời Customer Care) | `c17b7640-3f09-554c-af94-a2fa5f3d01f6` |
| Customer Care | `8a294c3d-8926-5ab0-aeec-b75765f1cccc` |

## Kết quả lượt kiểm cuối

Dữ liệu request được rút gọn, không ghi thông tin liên hệ hoặc thông tin xác thực. HTTP 200 chỉ áp dụng cho request HTTP; các dòng khởi động và SQL được đánh dấu “—”. “Chưa kiểm” nghĩa là không có dữ liệu phù hợp, không phải response lỗi.

| Ca | Request / body rút gọn | HTTP / mã / field | Kết quả và ghi chú |
|---|---|---|---|
| Khởi động Flyway V10 | `START backend` | — | Đạt — Flyway báo up to date |
| Đăng nhập admin | `POST /api/session/login` · `{"organizationCode":"EDU","email":"[ẩn]","password":"[ẩn]"}` | 200 | Đạt |
| Đăng nhập manager | `POST /api/session/login` · `{"organizationCode":"EDU","email":"[ẩn]","password":"[ẩn]"}` | 200 | Đạt |
| Đăng nhập sales_a | `POST /api/session/login` · `{"organizationCode":"EDU","email":"[ẩn]","password":"[ẩn]"}` | 200 | Đạt |
| Đăng nhập sales_b | `POST /api/session/login` · `{"organizationCode":"EDU","email":"[ẩn]","password":"[ẩn]"}` | 200 | Đạt |
| Đăng nhập care | `POST /api/session/login` · `{"organizationCode":"EDU","email":"[ẩn]","password":"[ẩn]"}` | 200 | Đạt |
| Sales A chỉ thấy cơ hội của mình | `GET /api/backend/opportunities?size=100` | 200 | Đạt |
| Sales A tải khách hàng trong scope | `GET /api/backend/customers?size=20` | 200 | Đạt |
| Sales A tải sản phẩm trong scope | `GET /api/backend/offerings?size=100` | 200 | Đạt |
| Sales A tạo hai dòng và tính tổng | `POST /api/backend/opportunities` · `{"title":"TEST-ACC Opportunity A","stage":"NEW","lines":2,"discountAmount":"1380000.00"}` | 201 | Đạt |
| Ngày dự kiến hôm qua | `POST /api/backend/opportunities` · `{"expectedCloseDate":"hôm qua","lines":2}` | 400 / `VALIDATION_ERROR` / `expectedCloseDate` | Đạt |
| Sản phẩm trùng | `POST /api/backend/opportunities` · `{"lines":"trùng sản phẩm"}` | 400 / `VALIDATION_ERROR` / `lines[1].offeringId` | Đạt |
| NEW sang PROPOSAL | `PATCH /api/backend/opportunities/9ea96886-c4ee-4141-a15b-f111b5a53542` · `{"expectedUpdatedAt":"2026-11-04T06:28:06.530403Z","stage":"PROPOSAL"}` | 200 | Đạt |
| Không lùi giai đoạn | `PATCH /api/backend/opportunities/9ea96886-c4ee-4141-a15b-f111b5a53542` · `{"expectedUpdatedAt":"2026-11-04T06:28:06.764674Z","stage":"NEW"}` | 409 / `OPPORTUNITY_STAGE_BACKWARD` | Đạt |
| Không ghi đè phiên bản cũ | `PATCH /api/backend/opportunities/9ea96886-c4ee-4141-a15b-f111b5a53542` · `{"expectedUpdatedAt":"2026-11-04T06:28:06.530403Z","title":"TEST-ACC stale"}` | 409 / `OPPORTUNITY_CHANGED` | Đạt |
| Sales A chốt thành công | `POST /api/backend/opportunities/9ea96886-c4ee-4141-a15b-f111b5a53542/win` · `{"expectedUpdatedAt":"2026-11-04T06:28:06.764674Z","finalValue":"25320000.00","actualCloseDate":"2026-11-04"}` | 200 | Đạt |
| Không sửa cơ hội đã đóng | `PATCH /api/backend/opportunities/9ea96886-c4ee-4141-a15b-f111b5a53542` · `{"expectedUpdatedAt":"2026-11-04T06:28:06.952645Z","title":"TEST-ACC locked"}` | 409 / `OPPORTUNITY_LOCKED` | Đạt |
| Sales không được mở lại | `POST /api/backend/opportunities/9ea96886-c4ee-4141-a15b-f111b5a53542/reopen` · `{"expectedUpdatedAt":"2026-11-04T06:28:06.952645Z"}` | 403 / `FORBIDDEN_PERMISSION` | Đạt |
| Tạo cơ hội để hủy | `POST /api/backend/opportunities` · `{"title":"TEST-ACC Cancel","lines":2}` | 201 | Đạt |
| Hủy thiếu lý do | `POST /api/backend/opportunities/38e303d1-1301-4af9-9035-fa6917b679a2/cancel` · `{"expectedUpdatedAt":"2026-11-04T06:28:07.110174Z"}` | 400 / `VALIDATION_ERROR` / `reason` | Đạt |
| Hủy có lý do | `POST /api/backend/opportunities/38e303d1-1301-4af9-9035-fa6917b679a2/cancel` · `{"expectedUpdatedAt":"[phiên bản]","reason":"[ẩn]"}` | 200 | Đạt |
| Sales A không đọc cơ hội Sales B | `GET /api/backend/opportunities/17d20800-5944-5ac4-9df8-b2d22a98dacb` | 403 / `FORBIDDEN_SCOPE` | Đạt |
| Manager thấy cơ hội team | `GET /api/backend/opportunities?size=100` | 200 | Đạt |
| Owners sắp theo tên | `GET /api/backend/opportunities/owners` | 200 | Đạt |
| Owner candidates đủ điều kiện | `GET /api/backend/opportunities/owner-candidates?customerId=63b2699b-d5f4-4651-a2a5-acdb3453e2a2&offeringIds=9238565a-a0ee-57ef-a1d7-cdb9944abeb7%2C714a9ee5-3118-597f-ab20-d4676569abf3` | 200 | Kiểm một phần — Đã xác nhận Sales A có trong danh sách; chưa đối chiếu toàn bộ phần tử trả về. |
| Manager tạo thiếu owner | `POST /api/backend/opportunities` · `{"ownerUserId":"thiếu"}` | 400 / `VALIDATION_ERROR` / `ownerUserId` | Đạt |
| Manager tạo với owner hợp lệ | `POST /api/backend/opportunities` · `{"title":"TEST-ACC Manager","ownerUserId":"Sales A"}` | 201 | Đạt |
| Manager mở lại | `POST /api/backend/opportunities/9ea96886-c4ee-4141-a15b-f111b5a53542/reopen` · `{"expectedUpdatedAt":"2026-11-04T06:28:06.952645Z"}` | 200 | Đạt |
| Manager lọc Customer và owner | `GET /api/backend/opportunities?size=100&customerId=63b2699b-d5f4-4651-a2a5-acdb3453e2a2&ownerUserId=16bf03a9-05d2-57ef-9017-be06b9552839` | 200 | Đạt |
| Sales A tìm Lead mới | `GET /api/backend/leads?status=NEW&size=100` | 200 | Đạt |
| Qualify Lead NEW | `POST /api/backend/leads/93cc0449-7c61-5e50-9486-837a906275e2/qualify` · `{"expectedUpdatedAt":"2026-09-16T09:00:00Z"}` | 200 | Đạt |
| Convert thiếu customerType | `POST /api/backend/leads/93cc0449-7c61-5e50-9486-837a906275e2/convert` · `{"expectedUpdatedAt":"2026-11-04T06:28:08.210240Z"}` | 400 / `VALIDATION_ERROR` / `customerType` | Đạt |
| Convert Lead | `POST /api/backend/leads/93cc0449-7c61-5e50-9486-837a906275e2/convert` · `{"expectedUpdatedAt":"[phiên bản]","customerType":"INDIVIDUAL"}` | 200 | Đạt — customerCreated=True; cơ hội mở trước chuyển=1 |
| Lead chuyển đổi bị khóa | `PATCH /api/backend/leads/93cc0449-7c61-5e50-9486-837a906275e2` · `{"expectedUpdatedAt":"2026-11-04T06:28:08.326295Z","displayName":"TEST-ACC Lead"}` | 409 / `LEAD_CLOSED` | Đạt |
| Opportunity mở theo Lead đã chuyển | `SELECT acceptance DB` | — | Đạt — Số cơ hội mở ban đầu: 1 |
| Conflict Lead trùng Customer Sales khác | `POST /leads/{id}/convert` | — | Chưa kiểm — Không có dữ liệu phù hợp; không tạo dữ liệu ép ca |
| Admin không tạo Opportunity | `POST /api/backend/opportunities` · `{"title":"TEST-ACC Admin"}` | 403 / `FORBIDDEN_PERMISSION` | Đạt |
| Admin PATCH | `PATCH /api/backend/opportunities/9ea96886-c4ee-4141-a15b-f111b5a53542` · `{"expectedUpdatedAt":"2026-11-04T06:28:07.810283Z","title":"TEST-ACC Admin update"}` | 200 | Đạt |
| Admin reopen | `POST /api/backend/opportunities/38e303d1-1301-4af9-9035-fa6917b679a2/reopen` · `{"expectedUpdatedAt":"2026-11-04T06:28:07.225438Z"}` | 200 | Đạt |
| Customer Care không đọc Opportunity | `GET /api/backend/opportunities` | 403 / `FORBIDDEN_PERMISSION` | Đạt |
| Logout rồi gọi API | `POST/GET /api/session/logout → /api/backend/opportunities` · `{}` | 401 / `AUTH_TOKEN_EXPIRED` | Đạt |
| Kiểm tra an toàn response lỗi | `HTTP BFF errors` | — | Đạt — Số response lỗi đã kiểm: 14; số chứa dấu hiệu dữ liệu nội bộ: 0 |
| Đối chiếu số dòng và tổng DB | `SELECT acceptance DB` | — | Đạt — count|lines|sum=6|12|151920000.00; final_value null sau reopen |
| Audit Opportunity | `SELECT acceptance DB` | — | Đạt — OPPORTUNITY_CANCELLED|1, OPPORTUNITY_CREATED|2, OPPORTUNITY_REOPENED|2, OPPORTUNITY_STAGE_CHANGED|1, OPPORTUNITY_UPDATED|2, OPPORTUNITY_WON|1 |
| Audit không chứa contact hoặc lý do | `SELECT acceptance DB` | — | Đạt — Số audit chứa trường cấm: 0 |
| Audit chuyển Lead và tạo Customer | `SELECT acceptance DB` | — | Đạt — CUSTOMER_CREATED, LEAD_CONVERTED |

## FINDING và giới hạn

- Chưa phát hiện lỗi trong các kiểm tra đã thực hiện. Ca Lead QUALIFIED có contact trùng Customer của Sales khác không có dữ liệu phù hợp; không sửa dữ liệu để tạo tình huống. Không có kết quả cho `LEAD_CONVERSION_CONFLICT` hoặc quy tắc `duplicateId` trong lượt acceptance này.
- Với `owner-candidates`, đã kiểm Sales A đủ điều kiện có trong response; chưa đối chiếu đầy đủ từng phần tử nên chưa kết luận tính loại trừ của toàn bộ danh sách.
- Đã kiểm 14 response lỗi qua BFF theo các dấu hiệu token, mật khẩu, stack trace, chuỗi JDBC và tên framework; không phát hiện các dấu hiệu đó. Đây không phải kiểm định toàn diện về rò rỉ dữ liệu. Audit kiểm các entity Lead và Opportunity trong ca thử, không phải toàn bộ audit của hệ thống.
- Phiên không có công cụ trình duyệt: **chưa kiểm UI**, responsive, bàn phím, dialog hoặc screenshot. Dữ liệu là bản sao cô lập, không đại diện cho Supabase hay acceptance bằng tài khoản demo thật.
- Backend 8081 và frontend 3011 đã dừng; `pg_ctl stop` đã dừng cluster 54329. Kiểm sau dọn: 8081, 3011, 8080, 3010 và 54329 đều không lắng nghe. Database acceptance được giữ lại theo yêu cầu.

## Acceptance giao diện — 04/11/2026

Claude Code kiểm bằng trình duyệt tích hợp của Claude Desktop, cùng bản sao `pose_v2_acceptance_202611041315`: frontend `127.0.0.1:3011` (`BACKEND_URL=http://127.0.0.1:8081`), backend 8081, cluster 54329 đã kiểm `SHOW data_directory`. Mật khẩu thử nghiệm sinh mới trong phiên, băm bằng `BCryptPasswordEncoder(12)` như backend và chỉ đặt cho năm user ở bảng trên trong bản sao; file tạm đã xóa sau kiểm thử. Không kết nối Supabase hoặc PostgreSQL 5432.

| Vai trò | Ca | Kết quả |
|---|---|---|
| Sales A | Pipeline 6 cột, chỉ cơ hội của mình | Đạt |
| Sales A | Form: 2 × 6.900.000 ₫, chiết khấu 10% quy đổi 1.380.000 ₫, dòng 12.420.000 ₫; tổng cập nhật khi thêm dòng | Đạt |
| Sales A | Ngày dự kiến hôm qua và dòng 2 trùng sản phẩm: lỗi hiện dưới đúng ô, `aria-invalid` và `aria-describedby` đúng, có thông báo `role=alert` | Đạt |
| Sales A | Lưu → trang chi tiết đúng 2 dòng, tổng 25.320.000 ₫ | Đạt |
| Sales A | Dialog chốt: `aria-modal`, có tiêu đề, focus vào ô đầu; Escape đóng và trả focus về nút mở | Đạt |
| Sales A | Dialog hủy bỏ trống lý do: báo “Vui lòng nhập lý do.”, focus vào ô lý do | Đạt |
| Sales A | Chốt thành công hoàn toàn bằng bàn phím (Enter mở, Enter xác nhận); sau chốt ẩn sửa/chốt, không có “Mở lại” | Đạt |
| Sales A | Lead NEW → xác nhận đủ điều kiện → chuyển: bỏ trống loại khách hàng báo lỗi đúng ô; chọn Cá nhân → sang trang Customer mới; Lead hiển thị “Đã chuyển đổi”, không còn thao tác | Đạt |
| Manager | Mở lại cơ hội đã chốt → “Đủ điều kiện”, mất phần thông tin chốt, hiện lại các thao tác | Đạt |
| Manager | Form tạo: ô người phụ trách chỉ tải ứng viên sau khi chọn Customer và sản phẩm; Customer của Sales A chỉ trả Sales A | Đạt |
| Admin | Không có nút “Thêm cơ hội”; mở thẳng `/app/co-hoi/them` → “Không có quyền truy cập” | Đạt |
| Customer Care | Menu không có “Cơ hội bán hàng”; mở thẳng `/app/co-hoi` → trang không có quyền | Đạt |
| Chung | Đăng xuất qua menu có dialog xác nhận, về trang đăng nhập; console không có lỗi | Đạt |
| 375px | Pipeline, form và chi tiết: `scrollWidth` = 375, không cuộn ngang | Đạt sau khắc phục |

**FINDING UI-1 (đã sửa):** ở độ rộng ≤ 767px, `.opportunity-filters` chuyển sang `flex-direction: column` nhưng `.field` vẫn giữ `flex: 1 1 26rem`; `flex-basis` 26rem trở thành chiều cao, mỗi ô lọc cao 416px và ô nhập cao khoảng 217px. Sửa bằng `.opportunity-filters .field { flex: 0 0 auto; }` trong media query; ô nhập về 48–49px.

**FINDING UI-2 (đã sửa):** trang chi tiết dùng `offering-table-scroll`, lớp này bị `display: none` ở độ rộng ≤ 52rem; trang không có danh sách thay thế nên trên điện thoại không thấy dòng sản phẩm nào, chỉ có tổng. Thêm `offering-mobile-cards` gồm tên, mã, số lượng, đơn giá, chiết khấu, giá trị; thêm `scope="col"` cho tiêu đề bảng và test `renders opportunity lines as mobile cards alongside the table`.

Sau khắc phục: `npm run typecheck`, `npm run lint` đạt; Vitest 23 file, 230 test đạt. Sau kiểm thử đã dừng frontend 3011, backend 8081 và cluster 54329; các cổng 8080, 8081, 3010, 3011, 54329 không lắng nghe.

Giới hạn: không đo tương phản bằng công cụ, không kiểm trình đọc màn hình thật, zoom 200% hay `prefers-reduced-motion`; không kiểm ca conflict khi chuyển Lead vì không có dữ liệu phù hợp (đã có integration test). Ảnh chụp không lưu vào repo.
