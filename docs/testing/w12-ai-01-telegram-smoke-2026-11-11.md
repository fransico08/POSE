# W12 AI-01 — Telegram webhook smoke test

**Ngày:** 11/11/2026
**Phạm vi:** receiver smoke-only cho Telegram bot demo; chưa phải xử lý hội thoại AI-02.
**Môi trường:** JDK 22.0.2, Maven 3.9.15; test controller trực tiếp, không kết nối database, Supabase hay Telegram thật.

## Kết quả tự động

Lệnh đã chạy từ thư mục gốc repo:

```powershell
mvn -pl backend -am `
  -Dtest=TelegramSmokeWebhookControllerTest `
  -Dsurefire.failIfNoSpecifiedTests=false `
  -DforkCount=0 test
```

**Kết quả: PASS — 5 tests, 0 failures, 0 errors, 0 skipped.**

| Ca kiểm tra | Kết quả |
|---|---|
| Telegram message update hợp lệ, đúng webhook secret | HTTP 200, `mode=SMOKE_ONLY`, `Cache-Control: no-store`; nội dung và secret không có trong response |
| Thiếu hoặc sai secret | HTTP 401, mã `CHANNEL_WEBHOOK_UNAUTHORIZED` |
| Chưa cấu hình secret | HTTP 503, mã `CHANNEL_WEBHOOK_NOT_CONFIGURED` |
| JSON sai định dạng hoặc không phải message update | HTTP 400, mã `VALIDATION_ERROR` |
| Payload vượt 64 KiB | HTTP 413, mã `REQUEST_TOO_LARGE` |
| Content-Type không phải JSON | HTTP 415, mã `UNSUPPORTED_MEDIA_TYPE` |

## Giới hạn của unit test

- `TelegramSmokeWebhookController` chỉ xác nhận request và bỏ payload; test này không lưu tin nhắn, chống trùng hay tạo Lead/Interaction. Đây là receiver smoke-only, khác endpoint production V13 được dùng trong live test bên dưới.
- `SecurityConfiguration` cho phép POST webhook không cần JWT. Smoke-only receiver cần profile `telegram-smoke` và `POSE_CHANNEL_TELEGRAM_SMOKE_ENABLED=true`; mặc định receiver này tắt.
- Không lưu token, secret, nội dung tin nhắn hay URL tunnel trong evidence.

## Live acceptance — 17/11/2026 — PASS

Đã kiểm thử bot demo thật qua Cloudflare Quick Tunnel tạm thời tới endpoint V13 chính thức `/api/v1/channels/telegram/webhook`, trên nhánh `Thắng`, commit `47a5075`.

- Tạo DB clone cô lập từ nguồn review ở Flyway V12 trên cluster `127.0.0.1:54329`; xác minh `SHOW data_directory` trỏ đúng `backend/target/auth-postgres-test`. Flyway validate 16 migration, áp V13–V15 thành công; database test ở V15. Không dùng Supabase.
- Backend khởi động trên cổng `8080`; `GET /api/v1/auth/me` trả `401` như dự kiến khi không có bearer token. Log xác nhận ứng dụng started. Cảnh báo Flyway về PostgreSQL 18.6 (phiên bản mới nhất được Flyway kiểm thử là 17) không ngăn ứng dụng hoặc migration khởi động.
- Cấu hình một Telegram channel account thử nghiệm trong DB clone; `secret_ref` chỉ tham chiếu tên biến môi trường `POSE_CHANNEL_TELEGRAM_WEBHOOK_SECRET`, không lưu giá trị secret.
- Cloudflare Quick Tunnel phân giải DNS và chuyển request HTTPS tới backend; Telegram `setWebhook` thành công và `getWebhookInfo` xác nhận cấu hình. Sau khi gửi tin nhắn thử, Telegram báo `pending_update_count=0`, không có lỗi giao nhận.
- Trước khi gửi: `0` conversation, `0` inbound message. Sau khi gửi: `1` conversation, `2` inbound message được lưu trong clone. Evidence chỉ ghi số lượng, không đọc hoặc lưu nội dung.
- Cleanup đã xác minh: `deleteWebhook` thành công, tunnel và backend đã dừng, log tunnel tạm đã xóa, token và webhook secret đã xóa khỏi phiên PowerShell. DB clone cô lập được giữ lại để có thể rà soát nếu cần.

Acceptance criterion W12 **“Gửi tin tới bot Telegram nhận được webhook thử qua tunnel HTTPS” — PASS**. Chỉ tiêu chí live này được xác nhận; Trello chưa được cập nhật tự động.
