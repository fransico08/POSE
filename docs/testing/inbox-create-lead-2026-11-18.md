# Evidence — Tạo Lead từ hội thoại — 18/11/2026

## Quyết định và phạm vi

Theo DEC-29, nhân viên chủ động tạo Lead và gắn hội thoại trong cùng transaction, dùng lại validation, scope, kiểm trùng và audit của Lead/link. `SOCIAL_MEDIA` là nguồn mặc định cho Telegram/ZALO_SIM; có thể chọn nguồn khác trong danh mục đã duyệt. Kênh cụ thể truy qua hội thoại đã gắn. Không thêm migration.

Hội thoại chưa lưu tên người gửi; ô tên để trống và yêu cầu nhân viên nhập. Đề xuất lưu tên từ webhook thuộc adapter của Thắng, cần migration và chờ Tài quyết định W17 hay KLTN. Gợi ý liên hệ chỉ từ tin INBOUND đã tải và phải được nhân viên chọn; không có thì hướng dẫn hỏi khách. Không dùng mã thread làm tên.

**Lệch tài liệu cần Tài quyết định:** SRS mục 10 bước 2 mô tả tự tạo Lead khi nhận tin, trong khi DEC-23 D2 quy định không tự tạo; tính năng này giữ DEC-23 và yêu cầu thao tác chủ động. Chưa sửa SRS.

## Kiểm chứng

- Backend mục tiêu H2: 3/3 test của lượt đầu đạt; bản cuối bổ sung test Manager có owner hợp lệ và được chạy trong hai suite đầy đủ.
- Vitest toàn bộ: 374/374 đạt, 32 file. Typecheck và lint đạt.
- Giao diện dùng dialog hiện có, label và field error, focus về tiêu đề hồ sơ sau tạo; kiểm tra thực tế và kết quả 375 px ghi theo lượt E2E bên dưới.
- `scripts/e2e-env.ps1 -Build` chạy riêng: Chromium **37/37 đạt** (runner báo 1,1 phút). Kịch bản ZALO_SIM dùng số giả, Sales xác nhận gợi ý liên hệ, tạo và gắn Lead, trích tín hiệu; kiểm không cuộn ngang ở 375 px cả khi dialog mở. Kịch bản inbox/E04 cũ vẫn đạt. Cổng 8080, 8081, 3010, 3011 và 54329 đã dừng.

| Backend | Tổng | Đạt | Bỏ qua | Lỗi |
|---|---:|---:|---:|---:|
| H2 | 455 | 426 | 29 | 0 |
| PostgreSQL 54329 | 455 | 442 | 13 | 0 |

Số đọc từ 56 file Surefire XML trên mỗi bản sao mã sạch; `ConversationCreateLeadIntegrationTest` 4/4 trên cả hai. PostgreSQL báo `Verified`, regression `pose_foundation_test_20261118125300536`; `SHOW data_directory` khớp `C:/Users/Dell/AppData/Local/Temp/pose-inbox-lead-13jxy47r/pg/backend/target/auth-postgres-test`. Hai suite chạy trên bản sao mới do lệnh xóa build tại checkout từng bị cơ chế kiểm duyệt tự động từ chối `blocked by policy`; không chạy `mvn clean`, không xóa cluster gốc, không đặt `POSE_DEMO_PASSWORD`.

## Giới hạn

Không thay webhook, schema V1–V17, seed, CSV, manifest hay Data Dictionary. Không gọi AI thật hoặc dùng Supabase/5432. Email/phone gợi ý chưa được xác minh chỉ vì xuất hiện trong tin; nhân viên phải tự xác nhận trước khi chọn.

Chỉ kiểm trình duyệt Chromium qua Playwright; chưa kiểm trình đọc màn hình và chưa tuyên bố đạt toàn bộ WCAG 2.2 AA.
