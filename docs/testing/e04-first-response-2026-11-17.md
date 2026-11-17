# Evidence E04 — Thời gian phản hồi tin đầu tiên — 17/11/2026

## Phạm vi và định nghĩa

Theo DEC-26, lấy hội thoại có tin INBOUND đầu tiên trong kỳ `[start,end)` và không sau `asOf`. Phản hồi là tin OUTBOUND đầu tiên do người dùng gửi, trạng thái SENT, từ thời điểm INBOUND đến `asOf`. Giá trị theo kênh là trung vị giây đồng hồ của các hội thoại đã phản hồi; tử số là số đã phản hồi, mẫu số là tổng hội thoại. Chưa trừ giờ nghỉ. Kỳ rỗng trả `NO_DATA_IN_PERIOD`; có hội thoại nhưng chưa phản hồi trả `NOT_AVAILABLE_ZERO_DENOMINATOR`, giá trị null.

Admin/Data Staff nhận số tổng hợp toàn tổ chức; Manager theo team quản lý hoặc hội thoại tự phụ trách; Sales/Care theo hội thoại tự phụ trách. Nhiều vai trò lấy hợp phạm vi; bộ lọc owner theo người được giao hiện tại, không theo lịch sử. Task workflow “Phản hồi đầu tiên” vẫn dùng nhóm Task sẵn có của Việc hôm nay. Không thêm migration hoặc index.

## Đối soát SQL độc lập trên PostgreSQL

Test `E04FirstResponseIntegrationTest.postgresIndependentSqlMatchesApi` chạy [SQL đối soát](e04-reconcile-2026-11-17.sql) độc lập với truy vấn backend trong transaction fixture và so từng kết quả với API Admin. Thời gian chốt `2026-10-01T00:00:00Z`, kỳ `[2026-09-30T00:00:00Z, 2026-10-02T00:00:00Z)`.

| Kênh | SQL: đã phản hồi / tổng | SQL: trung vị giây | API | Kết quả |
|---|---:|---:|---|---|
| Telegram | 1/1 | 90 | 1/1; 90 giây | Khớp |
| Zalo mô phỏng | 2/3 | 90 | 2/3; 90 giây | Khớp |

Fixture riêng cũng kiểm tra trung vị chẵn/lẻ, chưa phản hồi, thời điểm ngoài phạm vi, tin gửi lỗi hoặc không có người gửi, nhiều kênh, scope vai trò và tenant; không dùng hay sửa seed của Nhi.

Trên bản restore V2, lượt smoke tại `asOf=2026-09-30T17:00:00Z` trả E04 `NO_DATA_IN_PERIOD`, value null, 0/0 cho cả EDU, REA, TECH, RET, LOG và CLI; đây là kết quả API trong kỳ smoke, không phải đối soát SQL trên hội thoại thật.

## Kiểm chứng

| Bộ kiểm thử | Tổng | Đạt | Bỏ qua | Lỗi |
|---|---:|---:|---:|---:|
| Backend H2 | 392 | 363 | 29 | 0 |
| Backend PostgreSQL 54329 | 392 | 379 | 13 | 0 |
| Vitest | 367 | 367 | 0 | 0 |
| Playwright E2E Chromium | 35 | 35 | 0 | 0 |

H2 và PostgreSQL chạy từ hai bản sao sạch của checkout với `mvn -pl backend -am ... verify` và `backend/scripts/test-postgres-v2.ps1`; số lấy từ Surefire XML. PostgreSQL trả `Verified`, và `SHOW data_directory` xác nhận thư mục `backend/target/auth-postgres-test` của bản sao PostgreSQL. Không đặt `POSE_DEMO_PASSWORD`; một test đối soát SQL dành riêng cho PostgreSQL được bỏ qua trên H2. Frontend typecheck và lint đạt; build đạt khi chạy môi trường E2E.

`scripts/e2e-env.ps1 -Build` chạy riêng, 35/35 đạt trong 55,4 giây. Luồng hộp thư nhận tin Zalo mô phỏng, gửi trả lời rồi kiểm Báo cáo hiện “Đã phản hồi 1/1 hội thoại”. Các test Báo cáo theo vai trò và kiểm tra reflow 375 px cũng đạt. Runner xác nhận dừng các cổng 8080, 8081, 3010, 3011 và 54329 sau lượt chạy.

Lệnh xóa ba thư mục build tại checkout bị cơ chế kiểm duyệt tự động từ chối với lý do `blocked by policy`. Vì vậy hai lượt backend được chạy trong bản sao sạch thay thế, không xóa cluster hiện có và không chạy `mvn clean`.

## Ghi chú cho Văn

Cập nhật từ điển KPI E04 theo DEC-26: bucket kênh, trung vị giây của phản hồi do người dùng gửi thành công, số đã phản hồi/tổng, khoảng kỳ và giới hạn theo người được giao hiện tại. Đối soát ở đây dùng fixture PostgreSQL; chưa thay thế công việc đối soát dữ liệu độc lập của Văn trên các bộ dữ liệu khác.

## Giới hạn

- Chỉ dùng giây đồng hồ, chưa trừ giờ nghỉ; không tái dựng lịch sử giao hội thoại.
- Không kết nối Supabase, PostgreSQL 5432 hoặc AI thật. Không đặt `POSE_DEMO_PASSWORD` khi kiểm backend.
- E2E chỉ kiểm Chromium, chưa kiểm bằng trình đọc màn hình.

## Fix-up sau review 17/11/2026

- Nhóm E ẩn từng panel có `NOT_APPLICABLE` và `OUTSIDE_ROLE_SCOPE`; các trạng thái khác, kể cả `OWNER_FILTER_NOT_SUPPORTED`, giữ nguyên cách hiển thị. Nếu mọi KPI đều không áp dụng, cả nhóm tiếp tục được ẩn.
- Scope E04 của Manager, Sales và Care chỉ lấy nhánh gắn với role có `conversation.read`. Admin/Data Staff vẫn xem số tổng hợp toàn tổ chức theo DEC-26; không đổi scope hộp thư.
- Test âm kiểm Sales bị gỡ quyền không nhận số; Manager kiêm Sales nhận hợp team quản lý và hội thoại tự phụ trách, không đếm trùng; quyền Sales không mở scope team của Manager khi Manager thiếu quyền; Data Staff kiêm Sales vẫn nhận số toàn tổ chức.

| Kiểm thử lượt fix-up | Tổng | Đạt | Bỏ qua | Lỗi |
|---|---:|---:|---:|---:|
| Backend H2 | 396 | 367 | 29 | 0 |
| Backend PostgreSQL 54329 | 396 | 383 | 13 | 0 |
| Vitest | 369 | 369 | 0 | 0 |
| Playwright E2E Chromium | 35 | 35 | 0 | 0 |

Typecheck và lint đạt. H2 và PostgreSQL chạy trên các bản sao riêng ban đầu sạch; sau khi siết fixture chỉ còn hai role Manager và Sales, cả hai suite được chạy lại. Số backend đọc từ Surefire XML; PostgreSQL trả `Verified`, `SHOW data_directory` khớp cluster riêng của bản sao. Không đặt `POSE_DEMO_PASSWORD`; không có migration mới.

`scripts/e2e-env.ps1 -Build` chạy riêng, build đạt và E2E 35/35 đạt (runner báo 1,2 phút). Ở viewport 375 px, Care thấy E04, không thấy các panel E01–E03/E05 và không cuộn ngang. Runner xác nhận đã dừng các cổng 8080, 8081, 3010, 3011 và 54329.
