# W15 AI-08 — Messenger development mode

Ngày kiểm chứng: 26/11/2026 · Nhánh: `Thắng` · Phạm vi: backend Messenger của Thắng; không sửa FE của Tài.

## Đã triển khai

- Thêm `MESSENGER` vào API cấu hình channel account hiện có; `externalAccountId` là Facebook Page ID, `secretRef` chỉ lưu tên biến môi trường App Secret. Không thêm migration và không sửa V1–V16.
- Thêm `GET /api/v1/channels/messenger/webhook` để xác minh Meta callback bằng verify token riêng và trả `hub.challenge` dạng text.
- Mở `POST /api/v1/channels/messenger/webhook` cho Meta; kiểm tra chữ ký `X-Hub-Signature-256` trên raw body bằng HMAC-SHA256, đối chiếu constant-time trước khi ghi dữ liệu.
- Chuẩn hóa tin text Messenger vào cùng luồng Conversation, Message, Interaction, outbox và chống trùng theo `message.mid`; bỏ qua echo và event không phải tin text.
- Thêm adapter gửi reply qua Graph API. Page token được gửi ở `Authorization: Bearer`, không đặt trên URL; lỗi provider trả mã chung, không ghi nội dung response hoặc token vào log. Reply bị chặn trước khi gọi Graph API khi tin inbound gần nhất quá 24 giờ.
- Cập nhật contract Conversation API, hướng dẫn biến môi trường và Backend README. ZALO_SIM vẫn là kênh mô phỏng; không có thay đổi UI.

## Kiểm chứng tự động

Chạy trên JDK 22.0.2, Maven 3.9.15; bộ integration dùng H2 trong bộ nhớ, không kết nối PostgreSQL, Supabase hoặc Meta thật.

```powershell
mvn -pl backend -am `
  '-Dtest=MessengerReplyAdapterTest,ConversationApiIntegrationTest,ConversationWorkflowIntegrationTest,JdbcConversationMessageProcessorTest,ChannelWebhookServiceTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Kết quả focused test: **17/17 PASS, 0 failure/error/skip**.

| Suite | Kết quả | Nội dung liên quan |
|---|---:|---|
| `ConversationApiIntegrationTest` | 10/10 | Meta account type, chữ ký hợp lệ/sai, không ghi dữ liệu khi signature sai, retry không trùng, GET challenge, chặn reply quá 24 giờ |
| `ConversationWorkflowIntegrationTest` | 1/1 | Outbox `MESSAGE_RECEIVED` tạo Task “Phản hồi đầu tiên” cho owner theo workflow hiện có |
| `JdbcConversationMessageProcessorTest` | 1/1 | Idempotency và xử lý owner/Task của outbox |
| `ChannelWebhookServiceTest` | 2/2 | Regression webhook chung |
| `MessengerReplyAdapterTest` | 3/3 | Graph endpoint/version, bearer token header, message ID phản hồi, lỗi provider được che, thiếu token không gọi provider |

Đã thử full backend suite nhưng các test không thuộc W15 cần mở HTTP server loopback bị môi trường Windows sandbox từ chối (`Unable to establish loopback connection`); lượt full suite được dừng. Không dùng kết quả đó để kết luận full backend suite pass.

## Chưa kiểm chứng live với Meta

Không có ba biến `POSE_CHANNEL_MESSENGER_APP_SECRET`, `POSE_CHANNEL_MESSENGER_VERIFY_TOKEN` và `POSE_CHANNEL_MESSENGER_PAGE_TOKEN` trong process hiện tại. Vì vậy chưa cấu hình Meta App/Page, chưa mở HTTPS tunnel, chưa nhận/gửi tin từ Facebook Page và chưa có ảnh/evidence thật. **Chỉ hoàn tất implementation và test giả lập; tiêu chí live Messenger của card chưa đủ bằng chứng để tick hoàn tất.**

### Các bước để đóng kiểm thử live

1. Chủ Meta App/Page tạo cấu hình development mode và cấp vai trò Tester cho tài khoản Facebook sẽ gửi thử; giữ Page Access Token, App Secret và verify token ngoài repo/Trello/chat.
2. Trong cửa sổ backend local dùng database test cô lập, nhập kín ba biến môi trường theo [hướng dẫn chạy local](../huong-dan-chay-local.md#biến-môi-trường-crmai). Tạo channel account `MESSENGER` với Page ID thật, team/owner test và `secretRef=POSE_CHANNEL_MESSENGER_APP_SECRET`.
3. Chỉ sau khi được duyệt, mở HTTPS tunnel tới cổng backend `8080` và đăng ký callback chính xác `/api/v1/channels/messenger/webhook` với verify token; subscribe Page field `messages`.
4. Tài khoản Tester gửi một tin mới tới Page. Xác nhận một tin INBOUND trong Inbox, Interaction và Task phản hồi đầu tiên; nhân viên trả lời trong cửa sổ 24 giờ, rồi người thử xác nhận nhận được tin trả lời trên Messenger.
5. Ghi evidence đã khử dữ liệu nhạy cảm: mã kết quả/giờ chạy, trạng thái inbox/outbox và xác nhận nhận reply. Không lưu token, App Secret, URL tunnel, nội dung chat hoặc screenshot chứa dữ liệu cá nhân. Gỡ webhook, dừng tunnel/backend và xóa biến khỏi process sau khi chạy.

## Tham chiếu

- Contract: `docs/api/conversation-api-v1.md`
- Hướng dẫn biến môi trường và giới hạn live: `docs/huong-dan-chay-local.md`
- Comment/DEC-33 trên Trello: dev mode, Page tester, không App Review.
