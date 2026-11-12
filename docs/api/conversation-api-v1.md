# Contract API Hội thoại v1 — W13

Trạng thái: **Contract phần tiếp nhận kênh đã triển khai; API hộp thư đang chờ chốt RBAC/owner/outbox với Tài.** Migration mới là V13; không sửa V1–V12.

## 1. Quyết định

**Đã chốt ngày 12/11/2026 tại [DEC-23](../meetings/decision-log.md#dec-23--owner-scope-và-task-cho-hội-thoại-kênh-ai-02):** owner mặc định theo tài khoản kênh (V14 thêm `default_assignee_user_id`, `team_id` vào `channel_accounts`; Manager của team thấy và giao lại); người nhắn chưa khớp hồ sơ được giữ chưa gắn, gắn tay qua `/link`; Task "Phản hồi đầu tiên" tạo qua outbox `MESSAGE_RECEIVED`. Các gạch đầu dòng dưới đây là bối cảnh trước khi chốt.

### Bối cảnh trước khi chốt

- Người nhắn chưa khớp Lead/Customer được giữ thành Conversation chưa gắn hồ sơ; không tự tạo Lead thiếu Offering/email/điện thoại.
- Quy tắc gán owner cho Conversation mới và cách tạo Task “Phản hồi đầu tiên” chưa được chốt. Vì vậy API danh sách/gán hội thoại và workflow Task chưa triển khai. Cần xác định ai được nhìn thấy, nhận, và xử lý hội thoại chưa gán; kiến trúc hiện nêu quyền Manager theo team nhưng Channel Account/Conversation chưa có team để tính scope.
- Tài cần chốt Conversation/Lead owner mặc định và Task chạy qua outbox hay tại webhook trước khi mở rộng xử lý Lead/Interaction.
- `ZALO_SIM` mô phỏng payload có hình dạng sự kiện tin nhắn kiểu Zalo OA; không kết nối Zalo OA thật.
- Cấu hình `channel_accounts` dùng API Admin bên dưới ở môi trường kiểm thử. Không chạy migration hay kiểm thử có ghi dữ liệu trên Supabase dùng chung khi chưa được người giữ credential duyệt.

## 2. Nguyên tắc chung

- API người dùng dùng Bearer JWT; phản hồi không được cache (`Cache-Control: no-store`). Webhook không yêu cầu JWT, nhưng phải có secret kênh hợp lệ.
- ID là UUID. Lỗi API dùng envelope hiện có `{code,message,fieldErrors,traceId}`.
- Không ghi secret, token, raw payload hoặc nội dung tin nhắn vào log/audit. Audit nhận tin chỉ có Conversation ID, kênh, hướng và loại nội dung.
- W13 hiện chỉ lưu tin nhắn dạng text. Loại nội dung khác phải trả lỗi validation, không được âm thầm bỏ hoặc lưu sai.

## 3. Quản lý cấu hình kênh

Mọi route dưới đây yêu cầu Admin tenant và quyền `channel.manage`.

### `GET /api/v1/channels/accounts`

Trả danh sách account thuộc tenant hiện tại. Response có `id`, `channelType`, `displayName`, `externalAccountId`, `status`, `secretRef`, `createdAt`, `updatedAt`. `secretRef` chỉ là tên biến môi trường; API không bao giờ trả giá trị secret.

### `POST /api/v1/channels/accounts`

Tạo account đang `ACTIVE`:

```json
{
  "channelType": "TELEGRAM",
  "displayName": "Telegram EDU demo",
  "externalAccountId": "telegram-bot-edu",
  "secretRef": "POSE_CHANNEL_TELEGRAM_WEBHOOK_SECRET"
}
```

`channelType` hiện nhận `TELEGRAM` hoặc `ZALO_SIM`; tên tối đa 160 ký tự; ID ngoài kênh tối đa 180; `secretRef` phải là tên biến hợp lệ (không phải giá trị secret). Khóa `(tenant, channelType, externalAccountId)` không được trùng. Thành công trả `201`; audit chỉ ghi metadata an toàn.

### `PATCH /api/v1/channels/accounts/{id}`

Chỉ nhận một hoặc nhiều trường `displayName`, `status` (`ACTIVE`/`INACTIVE`), `secretRef`; body rỗng hoặc trường ngoài whitelist bị từ chối. ID của tenant khác không thể được cập nhật. Thành công trả `200`; tắt account chặn webhook mới.

## 4. Nhận webhook

### `POST /api/v1/channels/{type}/webhook`

Loại được hỗ trợ: `telegram`, `zalo-sim`. Body tối đa 64 KiB; yêu cầu JSON. Giá trị secret được đọc từ biến môi trường có tên nằm trong `channel_accounts.secret_ref`; đối chiếu constant-time. DB chỉ lưu tên biến.

- `401 CHANNEL_WEBHOOK_UNAUTHORIZED`: secret sai hoặc không khớp account; không ghi Conversation, Message hay audit.
- `503 CHANNEL_WEBHOOK_NOT_CONFIGURED`: không có account ACTIVE hoặc biến secret chưa được cấu hình.
- `400 VALIDATION_ERROR`: payload thiếu/sai trường hoặc text rỗng/quá 4.000 ký tự.
- `413 REQUEST_TOO_LARGE`, `415 UNSUPPORTED_MEDIA_TYPE`: vượt giới hạn body hoặc content type không phải JSON.
- `200`: message đã được lưu bền vững; response `{ "ok": true, "duplicate": false, "conversationId": "<uuid>" }`. Gửi lại cùng khóa trả `duplicate: true` và cùng Conversation ID.

Telegram xác thực bằng `X-Telegram-Bot-Api-Secret-Token`; payload text tối thiểu:

```json
{
  "update_id": 1234,
  "message": {
    "message_id": 55,
    "date": 1794355200,
    "chat": { "id": -10077 },
    "from": { "id": 888 },
    "text": "Xin chào POSE"
  }
}
```

Thread ID là `message.chat.id`, message ngoài kênh là `message.message_id`; `update_id` không dùng làm khóa vì một update có thể chứa nhiều loại event.

`ZALO_SIM` xác thực bằng `X-POSE-Webhook-Secret`; payload mô phỏng:

```json
{
  "event_name": "user_send_text",
  "timestamp": "2026-11-11T10:00:00Z",
  "app_id": "zalo-app-edu",
  "sender": { "id": "zalo-user-21" },
  "recipient": { "id": "zalo-app-edu" },
  "message": { "msg_id": "zalo-msg-31", "text": "Tin nhắn mô phỏng" }
}
```

Thread ID lấy từ `sender.id`, account lấy từ `recipient.id`, khóa message là `message.msg_id`. `app_id` phục vụ payload mô phỏng; không có cam kết tương thích API thật.

Một tin hợp lệ tạo Conversation `OPEN` chưa gắn Lead/Customer và một `INBOUND` Message. Chống trùng ở cả unique constraint DB và xử lý insert. Không tự tạo Lead, Interaction, Task, owner hoặc trả lời tự động trong phiên bản này.

## 5. API hội thoại chưa triển khai

Các route dưới đây là hướng contract dự kiến, **không được xem là API đang dùng được**:

- `GET /api/v1/conversations`: hộp thư theo scope.
- `GET /api/v1/conversations/{id}/messages`: đọc tin theo scope.
- `POST /api/v1/conversations/{id}/messages`: trả lời qua channel adapter.
- `POST /api/v1/conversations/{id}/link`: gắn với Lead/Customer.

Chỉ triển khai sau khi chốt owner mặc định, Manager/team scope cho hội thoại chưa gắn, cơ chế nhận/giao hội thoại, phạm vi Link hồ sơ, và gửi/retry outbound. Data Staff không được đọc nội dung tin nhắn. `conversation.read`/`conversation.reply` được tạo trong catalog quyền nhưng chưa gắn endpoint hộp thư ở phần code này.

## 6. Lưu trữ

- `channel_accounts`: tenant, loại kênh, account ngoài kênh, trạng thái và `secret_ref`.
- `conversations`: một thread theo account; tối đa một trong Lead/Customer; hồ sơ có thể để trống.
- `conversation_messages`: INBOUND/OUTBOUND, content, trạng thái giao nhận và ID ngoài kênh.
- Unique key `(channel_account_id, external_thread_id)` và `(conversation_id,direction,external_message_id)` giữ webhook retry idempotent.
- Các khóa ngoại ghép tenant bảo đảm Channel Account, Lead, Customer, actor và Interaction (nếu liên kết sau này) không xuyên organization.
- Audit `MESSAGE_RECEIVED` ghi cùng transaction với message. Task workflow và outbox chưa được bật.

## 7. Biến môi trường

| Biến | Mục đích |
|---|---|
| `POSE_CHANNEL_TELEGRAM_WEBHOOK_SECRET` | Giá trị secret của Telegram; `secretRef` của account chỉ tới tên này |
| `POSE_CHANNEL_ZALO_SIM_WEBHOOK_SECRET` | Secret mô phỏng Zalo; không dùng cho Zalo OA thật |
| `POSE_CHANNEL_TELEGRAM_BOT_TOKEN` | Token bot dùng cho cấu hình webhook Telegram; W13 chưa gửi tin outbound |

Giữ secret trong biến môi trường của process local hoặc secret manager. Không ghi giá trị vào repo, tài liệu, Trello, ảnh evidence hay log.
