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

## Giới hạn và trạng thái acceptance

- Receiver chỉ xác nhận kết nối, log `update_id`, rồi bỏ payload; không lưu tin nhắn, không chống trùng và không tạo Lead/Interaction. Các chức năng đó thuộc AI-02/V13.
- `SecurityConfiguration` cho phép POST webhook không dùng JWT; endpoint chỉ được tạo khi profile `telegram-smoke` và `POSE_CHANNEL_TELEGRAM_SMOKE_ENABLED=true` cùng bật. Mặc định receiver tắt và CRM.AI vẫn tắt/provider `mock`.
- Chưa kiểm thử qua HTTP server thật, BotFather, HTTPS tunnel hay tin nhắn Telegram thật. Do đó acceptance “gửi tin đến bot và nhận webhook qua tunnel HTTPS” đang **NOT RUN**, không được đánh dấu PASS chỉ từ unit test.
- Chỉ tiếp tục live demo bằng bot demo riêng sau khi Tài duyệt tunnel và người giữ bot cấp token trong PowerShell; không lưu token, secret, nội dung tin nhắn hoặc URL tunnel vào evidence.
