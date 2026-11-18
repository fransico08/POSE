# Contract API Hội thoại v1 — W13 AI-02

Trạng thái: **đã triển khai backend** theo [DEC-23](../meetings/decision-log.md#dec-23--owner-scope-và-task-cho-hội-thoại-kênh-ai-02). Schema ban đầu nằm ở V13; V14 bổ sung team/owner mặc định cho kênh, actor SYSTEM và quan hệ Conversation với Interaction/Task. Không sửa V1–V13.

## 1. Quyết định nghiệp vụ

- Account kênh thuộc một team. Conversation mới nhận owner từ `defaultAssigneeUserId`; trường này có thể để trống. Manager quản lý team đó có thể xem và giao hội thoại cho thành viên active của team có `conversation.reply`.
- Sales và Customer Care chỉ xem hội thoại được giao cho mình. Admin chỉ đọc nội dung nếu có `conversation.read`. Data Staff không được đọc nội dung hội thoại.
- Tin nhắn chưa khớp Lead/Customer được giữ chưa gắn hồ sơ. Không tự tạo Lead. Người có scope có thể liên kết với Lead/Customer có sẵn; tạo Lead mới phải đi qua API Lead hiện có.
- Tin inbound ghi `MESSAGE_RECEIVED` vào outbox cùng transaction. Worker tạo tối đa một Task `Phản hồi đầu tiên` cho owner hiện tại. Không có owner thì không tạo Task. Task/Interaction do automation ghi nhận actor `SYSTEM`, không mượn danh owner.
- Một phiên Interaction gom tin của cùng Conversation khi khoảng cách tin liên tiếp không quá 4 giờ. Summary chỉ là mô tả loại/số lượng tin, không sao chép nội dung tin nhắn.
- `ZALO_SIM` là adapter/payload nội bộ để kiểm thử, không gọi Zalo OA thật.

## 2. Quy tắc chung

- API người dùng yêu cầu Bearer JWT và trả `Cache-Control: no-store`; webhook không yêu cầu JWT nhưng phải có secret đúng.
- ID là UUID. Lỗi dùng envelope chung `{code,message,fieldErrors,traceId}`.
- Không ghi secret, token, raw webhook hoặc nội dung tin vào audit/log. Webhook chỉ hỗ trợ text, giới hạn 4.000 ký tự; body tối đa 64 KiB.
- Trang dùng shape `items`, `page`, `size`, `totalItems`, `totalPages`; page bắt đầu từ 0. Endpoint inbox tối đa 100 hội thoại/trang; messages tối đa 100 tin/trang.

## 3. Quản lý tài khoản kênh

Cần Admin tenant và `channel.manage`.

### `GET /api/v1/channels/accounts`

Trả `id`, `channelType`, `displayName`, `externalAccountId`, `teamId`, `defaultAssigneeUserId`, `status`, `secretRef`, `createdAt`, `updatedAt`. `secretRef` chỉ là tên biến môi trường; giá trị secret không bao giờ được trả về.

### `POST /api/v1/channels/accounts`

```json
{
  "channelType": "TELEGRAM",
  "displayName": "Telegram EDU demo",
  "externalAccountId": "telegram-bot-edu",
  "teamId": "<team-uuid>",
  "defaultAssigneeUserId": "<user-uuid-or-null>",
  "secretRef": "POSE_CHANNEL_TELEGRAM_WEBHOOK_SECRET"
}
```

`channelType` là `TELEGRAM` hoặc `ZALO_SIM`; team phải thuộc tenant; owner nếu có phải là thành viên active của team, active và có `conversation.reply`. Account được tạo `ACTIVE`. Khóa `(tenant, channelType, externalAccountId)` là duy nhất. Trả `201`.

### `PATCH /api/v1/channels/accounts/{id}`

Whitelist: `displayName`, `status` (`ACTIVE`/`INACTIVE`), `secretRef`, `teamId`, `defaultAssigneeUserId`. Thay team phải đồng thời để owner hợp lệ với team mới; có thể bỏ owner bằng `null`. Tắt account chặn webhook và gửi tin mới. Trả `200`.

## 4. Nhận webhook

### `POST /api/v1/channels/{type}/webhook`

Loại được hỗ trợ: `telegram`, `zalo-sim`. Secret được đọc từ biến môi trường được tham chiếu bởi `secret_ref`, so sánh an toàn. Secret sai trả `401 CHANNEL_WEBHOOK_UNAUTHORIZED` và không ghi dữ liệu; account/secret chưa cấu hình trả `503 CHANNEL_WEBHOOK_NOT_CONFIGURED`; body lớn trả `413`, content type khác JSON trả `415`, payload sai trả `400`.

Khi nhận tin hợp lệ, hệ thống tạo/lấy Conversation, gán owner mặc định từ account, chèn tin inbound idempotent, mở/cập nhật Interaction session và ghi outbox `MESSAGE_RECEIVED`; trả `200` `{ "ok": true, "duplicate": false, "conversationId": "<uuid>" }`. Gửi lại cùng khóa trả cùng Conversation ID và `duplicate: true`; không phát sinh message, Interaction mới hoặc outbox trùng.

Telegram xác thực bằng `X-Telegram-Bot-Api-Secret-Token`; payload text:

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

Thread ID lấy từ `message.chat.id`, ID ngoài kênh từ `message.message_id`.

ZALO_SIM xác thực bằng `X-POSE-Webhook-Secret`; payload mô phỏng:

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

## 5. Hộp thư hội thoại

### `GET /api/v1/conversations?page=0&size=20&status=OPEN&assignedToMe=false`

Cần `conversation.read` và scope theo account/team/owner. `status` tùy chọn: `OPEN`, `PENDING`, `CLOSED`; `assignedToMe` mặc định `false`. Trả trang Conversation, mỗi item gồm `id`, `channelType`, `channelName`, `status`, `assignedUserId`, `leadId`, `customerId`, `lastInboundAt`, `lastMessageAt`, `lastMessagePreview`, `lastMessageDirection`. Preview là tối đa 80 ký tự Unicode đầu của tin mới nhất theo `occurredAt`, rồi `id`; direction là `INBOUND` hoặc `OUTBOUND`. Hai trường này null khi chưa có tin. Không trả tên hồ sơ trong danh sách. Sắp xếp hội thoại theo tin gần nhất trước.

### `GET /api/v1/conversations/{id}/messages?page=0&size=50`

Cùng `conversation.read` và scope; trả tin mới nhất trước. Item gồm `id`, `direction`, `contentType`, `content`, `sentByUserId` (null với tin vào), `deliveryStatus`, `occurredAt`, `createdAt`.

### `POST /api/v1/conversations/{id}/messages`

Cần `conversation.reply`, vai trò Manager, Sales hoặc Customer Care, cùng scope đọc Conversation. Admin chỉ gửi được nếu được cấp thêm `conversation.reply`; quyền này không cấp mặc định. Chỉ nhận text `content` không rỗng, tối đa 4.000 ký tự. Hội thoại `CLOSED` hoặc account `INACTIVE` không nhận reply. Gửi đồng bộ qua adapter; khi nền tảng xác nhận thành công, lưu tin OUTBOUND, nối Interaction session và trả `201` với `id`, `direction`, `content`, `deliveryStatus`, `externalMessageId`, `occurredAt`. Không retry tự động outbound ở W13.

### `POST /api/v1/conversations/{id}/link`

Cần scope `conversation.read` và scope hồ sơ Lead/Customer của người dùng. Chỉ gửi đúng một ID:

```json
{ "leadId": "<lead-uuid>" }
```

hoặc

```json
{ "customerId": "<customer-uuid>" }
```

Chỉ gắn hồ sơ hiện có; nếu đã gắn bất kỳ hồ sơ khác (kể cả khác loại Lead/Customer) trả `409 CONVERSATION_ALREADY_LINKED`. Gửi lại đúng hồ sơ đã gắn trả `200` và không ghi lại dữ liệu hay audit. Cập nhật cả Interaction do SYSTEM ghi nhận. Trả `200` Conversation.

Theo DEC-30, khi chuyển Lead thành Customer, hệ thống chuyển chủ thể của các hội thoại liên quan trong cùng transaction; đây không phải lời gọi link thủ công và không áp dụng 409 nêu trên. Người được giao và scope hội thoại DEC-23 giữ nguyên. Quyền xem Customer và tín hiệu vẫn được kiểm riêng; không có quyền thì hộp thư không hiển thị dữ liệu hồ sơ.

### `POST /api/v1/conversations/{id}/lead`

Theo đề xuất DEC-29 (chờ duyệt), người dùng chủ động tạo Lead và gắn hội thoại trong cùng transaction. Cần `lead.write` với role/scope tạo Lead hiện có và `conversation.read` cùng scope gắn hội thoại; Data Staff bị chặn. Body dùng các field của `POST /leads`: `displayName`, `email` và/hoặc `phone`, `offeringId`, `ownerUserId` cho Manager, `notes` tùy chọn, `source` tùy chọn. Bỏ `source` với Telegram/ZALO_SIM/Messenger thì mặc định `SOCIAL_MEDIA`; nếu gửi, phải thuộc danh mục Lead hiện có. Kênh cụ thể xem qua `channelType` của hội thoại đã gắn.

Trả `201` dạng `{ "lead": { ... }, "conversation": { ... } }`, `Cache-Control: no-store`. Hội thoại đã gắn trả `409 CONVERSATION_ALREADY_LINKED`; liên hệ trùng trả `409 LEAD_DUPLICATE`; field sai trả `400 VALIDATION_ERROR` với `fieldErrors`; ngoài scope hội thoại trả `404 RESOURCE_NOT_FOUND`, thiếu quyền trả `403 FORBIDDEN_PERMISSION`. Tạo Lead, gắn hội thoại, cập nhật Interaction SYSTEM và audit cùng transaction; lỗi gắn rollback Lead và audit tạo. Không tự tạo Lead từ webhook.

### `GET /api/v1/conversations/{id}/assignees`

Cần Manager có `conversation.assign`, `conversation.read` và quản lý team đang hoạt động của account hội thoại. Trả mảng `{id,fullName}` các thành viên active có membership và role grant đang hiệu lực với `conversation.reply`. Ngoài team quản lý, không tồn tại hoặc khác tenant trả `403 FORBIDDEN_SCOPE`; không dùng scope được giao từ Sales để giao lại.

### `POST /api/v1/conversations/{id}/assign`

Cần vai trò Manager, `conversation.assign`, `conversation.read` và quản lý team đang hoạt động của account. Scope đọc do được giao không cấp quyền giao lại; ngoài team, không tồn tại hoặc khác tenant trả `403 FORBIDDEN_SCOPE`. Body `{ "assignedUserId": "<user-uuid>" }`; người nhận phải là thành viên active của team, active và có `conversation.reply`. Việc giao lại cập nhật Conversation và owner của Task workflow còn mở; trả `200` Conversation.

## 6. Quyền và actor

| Thao tác | Quyền/vai trò | Phạm vi |
|---|---|---|
| Cấu hình account kênh | Admin + `channel.manage` | Tenant |
| Đọc inbox/tin/link hồ sơ | `conversation.read` | Admin được cấp quyền; Manager của team account; Sales/Care được giao |
| Trả lời | `conversation.reply` + Manager/Sales/Care (Admin chỉ khi được cấp quyền này) | Phải nằm trong scope đọc |
| Giao hội thoại | Manager + `conversation.assign` | Team account đang quản lý |
| Đọc nội dung | Data Staff bị từ chối | Không có ngoại lệ |

Task đầu tiên và Interaction session tự động lưu `actor_type=SYSTEM`, user ID null. Người được giao Task chỉ là owner, không phải creator. System Interaction được bảo vệ khỏi sửa/xóa qua API Interaction thông thường.

## 7. Biến môi trường

| Biến | Mục đích |
|---|---|
| `POSE_CHANNEL_TELEGRAM_WEBHOOK_SECRET` | Secret Telegram; `secretRef` của account tham chiếu tên biến này |
| `POSE_CHANNEL_ZALO_SIM_WEBHOOK_SECRET` | Secret của webhook mô phỏng Zalo |
| `POSE_CHANNEL_TELEGRAM_BOT_TOKEN` | Token gửi reply Telegram; chỉ đặt trong môi trường chạy backend |
| `POSE_WORKFLOW_WORKER_ENABLED` | `true` để chạy worker; mặc định tắt |
| `POSE_WORKFLOW_WORKER_INTERVAL_MS` | Chu kỳ worker, mặc định 60000 ms |

Chỉ nhập secret trong môi trường process/secret manager. Không ghi giá trị secret vào repo, log, tài liệu hoặc ảnh evidence. Kiểm thử backend không gửi message tới bot thật nếu không cấu hình và không được chủ bot cho phép.
