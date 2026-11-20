# Evidence nghiệm thu kênh thật Telegram (DEC-33) và thử Gemini thật — 20/11/2026

Người chạy: Huỳnh Minh Tài (thao tác trên máy và điện thoại), Claude Code hỗ trợ hướng dẫn và đối chiếu database. Nhánh `Tài`, commit `6c698a2`.

Evidence không ghi bot token, webhook secret, URL tunnel hay khóa AI. Token bot bị lộ một lần trong ảnh chụp lúc thiết lập nên đã được thu hồi bằng `/revoke` trước khi chạy phần nghiệm thu; token hiện hành chỉ nằm trong biến môi trường của tiến trình PowerShell.

## 1. Môi trường

| Thành phần | Cấu hình |
|---|---|
| Database | Cụm dev cục bộ `127.0.0.1:54330`, database `pose_dev_w13_demo` (canonical W12 + hội thoại AI-05, Flyway V17). Không dùng `5432`, `54329` hay Supabase |
| Backend / Frontend | `scripts/dev.ps1 -Database pose_dev_w13_demo`, cổng `8080` / `3000`, `POSE_WORKFLOW_WORKER_ENABLED=true`, không bật profile `telegram-smoke` |
| Kênh | Bot Telegram demo riêng của nhóm; một `channel_accounts` loại `TELEGRAM` của EDU, `secretRef=POSE_CHANNEL_TELEGRAM_WEBHOOK_SECRET`, người nhận mặc định `edu.s01` |
| Tunnel | `cloudflared` 2026.11.1 quick tunnel tới `http://localhost:8080`; webhook đăng ký bằng `setWebhook` kèm `secret_token` |
| AI | Phần nghiệm thu kênh dùng AI mock (DEC-31). Gemini thật thử riêng ở mục 4 |

## 2. Kết quả nghiệm thu Telegram thật

| # | Bước | Kết quả | Căn cứ |
|---|---|---|---|
| 1 | Khách gửi tin từ ứng dụng Telegram trên điện thoại | **PASS** | 3 tin `INBOUND`, `delivery_status=RECEIVED`, đều có `external_message_id` |
| 2 | Hội thoại mới xuất hiện trong hộp thư của người được giao | **PASS** | `edu.s01` thấy hội thoại nhãn **Telegram · Đang mở**, trạng thái "Chờ phản hồi"; tin thứ ba vào đúng hội thoại cũ |
| 3 | Sales trả lời từ CRM, tin đến Telegram của khách | **PASS** | 1 tin `OUTBOUND`, `delivery_status=SENT`, có `external_message_id` do Telegram trả về; điện thoại nhận đúng nội dung |
| 4 | Bản nháp Copilot (mock): soạn → đưa vào ô trả lời → người duyệt rồi gửi | **PASS** | Thông báo "Đã đưa bản nháp vào ô trả lời. Hãy kiểm tra trước khi gửi."; không có đường tự gửi |
| 5 | Worker tạo Task phản hồi đầu tiên | **PASS** | Một `crm_tasks` "Phản hồi đầu tiên", trạng thái `OPEN` |
| 6 | Interaction sinh từ hội thoại | **PASS** | Cả 4 tin (3 đến, 1 đi) đều có `interaction_id`, gắn vào Interaction của hội thoại Telegram |

Kết luận: kênh Telegram thật đạt điều kiện nghiệm thu end-to-end hai chiều của DEC-33 trên môi trường local qua tunnel HTTPS.

**Chưa chạy trong phiên này:** tạo Lead từ hội thoại Telegram, trích tín hiệu sau khi gắn hồ sơ, kiểm tra `edu.s02` không thấy hội thoại. Các luồng này đã có E2E với `ZALO_SIM` và dùng chung `ConversationService`, nhưng chưa được chạy với Telegram thật.

## 3. Sự cố khi thiết lập và cách xử lý

| Sự cố | Nguyên nhân | Xử lý |
|---|---|---|
| `setWebhook`/`getMe` trả `401`, `404` | Token nhập sai: ô `Read-Host -AsSecureString` trong terminal nhận thừa ký tự khi dán; lần khác dán cả tin nhắn BotFather nhiều dòng | Nhập bằng `Read-Host` thường, lọc token bằng regex `\d{6,}:[A-Za-z0-9_-]{30,}`, xóa màn hình ngay, kiểm tra bằng `getMe` trước khi khởi động backend |
| Hộp thư trống dù đã nhắn | Webhook chưa đăng ký với token mới; bộ lọc trạng thái đang để "Đã đóng" | Đăng ký lại `setWebhook`; chọn "Đang mở"/"Tất cả" |
| Máy không có `winget` | — | Tải `cloudflared-windows-amd64.exe` bản chính thức từ GitHub Cloudflare vào thư mục ngoài repo |

Gợi ý cho hướng dẫn chạy local: thêm bước kiểm tra `getMe` và cách nhập token an toàn như trên.

## 4. Thử Gemini thật (DEC-22) với use case `REPLY_DRAFT`

Dữ liệu lấy từ bảng `ai_invocations` của database `pose_dev_w13_demo`.

| Model | Cấu hình | Số lần | Kết quả | Độ trễ |
|---|---|---:|---|---|
| `gemini-3.6-flash` (mặc định) | timeout 30 giây | 1 | `FAILED` / `TIMEOUT` | 30,3 giây |
| `gemini-3.6-flash` | timeout 60 giây, `thinking_level=minimal` | 1 | `FAILED` / `TIMEOUT` | 60,2 giây |
| `gemini-3.5-flash-lite` | timeout 60 giây, `thinking_level=minimal` | 7 | `FAILED` / `INVALID_OUTPUT` cả 7 lần | 1,5–21,4 giây |

- Gọi trực tiếp Gemini API (không qua POSE): `gemini-3.6-flash`, `gemini-3.8-flash` trả `503 Server Unavailable`; `gemini-3.5-flash` đóng kết nối; `gemini-3.5-flash-lite` và `gemini-flash-lite-latest` trả lời sau 7–8 giây. Nguyên nhân timeout ở POSE là Google quá tải ở thời điểm thử, không phải lỗi mạng (gọi máy chủ Gemini không kèm khóa phản hồi trong 0,25 giây).
- `INVALID_OUTPUT` nghĩa là `AiGateway.filterReplyOutput` loại bản nháp: thiếu mã nguồn `MESSAGE:`/`INTERACTION:`/`OPPORTUNITY:` có thật trong ngữ cảnh, hoặc bản nháp chứa số điện thoại/email/token che dữ liệu. Hội thoại có tin chứa số điện thoại (`redaction_count=1`). POSE không lưu đầu ra bị loại và `error_detail` để trống nên chưa xác định được quy tắc cụ thể.
- Hệ quả: không có gợi ý không an toàn nào đến người dùng; giao diện hiển thị thông báo lỗi rõ ràng.

## 5. Kết luận và đề xuất

1. Telegram thật **đạt** nghiệm thu DEC-33. Messenger (AI-08) còn chờ adapter backend.
2. Demo dùng AI mock làm phương án chính thức khi Gemini không ổn định (DEC-31), ghi rõ "AI mô phỏng".
3. Đề xuất bổ sung mã lý do loại đầu ra (ví dụ `REPLY_NO_VALID_SOURCE`, `REPLY_CONTAINS_MASK_TOKEN`) vào `ai_invocations.error_detail`, không lưu nội dung, để chẩn đoán và làm dữ liệu cho báo cáo đánh giá AI-10. Chưa thực hiện, chờ Tài duyệt.
4. Thử lại `gemini-3.6-flash` khi Google hết quá tải trước khi kết luận về chất lượng mô hình thật.
5. Sau buổi test: gỡ webhook (`deleteWebhook`), tắt tunnel; thu hồi token bot sau mỗi đợt demo nếu token từng xuất hiện ngoài máy.
