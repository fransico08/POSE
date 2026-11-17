# Kiến trúc CRM.AI v1

Ngày: 07/11/2026 · Owner: Huỳnh Minh Tài · Quyết định: [DEC-18](../meetings/decision-log.md#dec-18--chuyển-hướng-phạm-vi-và-tầm-nhìn-crmai) · Yêu cầu: [SRS 2.0](../SRS-POSE-v2-CRM-AI.md)

Tài liệu này bổ sung [Architecture v1](architecture-v1.md) cho giai đoạn CRM.AI. Lõi CRM, RBAC, outbox và workflow giữ nguyên; tài liệu chỉ mô tả phần thêm mới và điểm nối. Đây là thiết kế đích của TLCN; contract chi tiết và migration được chốt trong card triển khai tương ứng, không sửa migration V1–V11.

## 1. Nguyên tắc

1. **Bài toán trước, AI sau.** Mỗi thành phần AI phải giảm một dạng rò rỉ giá trị trong SRS 2.0 mục 2.1 và có cách đo.
2. **Backend là nơi duy nhất quyết định.** AI chỉ đề xuất; thay đổi dữ liệu nghiệp vụ đi qua service hiện có với RBAC và audit.
3. **AI thấy đúng những gì người gọi được thấy.** Ngữ cảnh gửi tới mô hình được dựng bằng các query đã áp scope của người gọi.
4. **Không hành động tự động ra bên ngoài.** Không tự gửi tin, không tự đổi trạng thái; mọi gợi ý có trạng thái chờ người duyệt.
5. **Mọi lần gọi AI có dấu vết.** Mục đích, mô hình, phiên bản prompt, token, chi phí ước tính, kết quả và quyết định của người dùng.
6. **Hỏng AI không hỏng CRM.** AI tắt mặc định; lỗi, hết hạn mức hoặc mất mạng chỉ làm mất gợi ý, không chặn luồng CRM.
7. **Thay thế được.** Adapter kênh và nhà cung cấp mô hình đứng sau giao diện chung.

## 2. Các tầng

```text
┌──────────── Trải nghiệm (Next.js) ───────────────────────────────┐
│ /app/hoi-thoai · Customer 360 + Copilot · Việc hôm nay ·         │
│ /app/bao-cao nhóm E                                              │
├──────────── Intelligence (backend, mới) ─────────────────────────┤
│ copilot · signal · valueinsight                                  │
├──────────── Governance (backend, mới) ───────────────────────────┤
│ ai.gateway: ContextBuilder(RBAC) · Redactor · Budget · Provider  │
│ (Claude | Mock) · AiInvocationLog · Suggestion/Feedback store    │
├──────────── Event backbone (ĐÃ CÓ) ──────────────────────────────┤
│ outbox_events · workflow-core worker · idempotency · retry       │
├──────────── Lõi CRM (ĐÃ CÓ) ─────────────────────────────────────┤
│ lead · customer · opportunity · interaction · task ·             │
│ customer360 · dashboard · audit · salesassignment                │
├──────────── Tiếp nhận (backend, mới) ────────────────────────────┤
│ channel: TelegramAdapter · MessengerAdapter · ZaloSimAdapter ·   │
│ WebhookController · ConversationService                          │
└──────────────────────────────────────────────────────────────────┘
```

Package backend mới dưới `com.pose.backend`: `channel`, `conversation`, `ai` (gateway, governance), `copilot`, `signal`, `valueinsight`. Mỗi package có controller, service, repository như các module hiện có và chỉ gọi module khác qua service.

## 3. Mô hình dữ liệu đề xuất (migration V12 trở đi)

Tái sử dụng: `interactions.channel`, `source_systems`, `source_record_links`, `outbox_events`, `workflow_executions`, `audit_logs`.

| Bảng | Cột chính | Ràng buộc |
|---|---|---|
| `channel_accounts` | organization_id, channel_type (`TELEGRAM`, `MESSENGER`, `ZALO_SIM`), display_name, external_account_id, status, secret_ref | `secret_ref` là **tên biến môi trường**, không chứa secret; unique (organization_id, channel_type, external_account_id) |
| `conversations` | organization_id, channel_account_id, external_thread_id, lead_id, customer_id, assigned_user_id, status (`OPEN`, `PENDING`, `CLOSED`), consent_status, last_inbound_at, last_message_at | unique (channel_account_id, external_thread_id); tối đa một trong lead_id/customer_id; được phép chưa gắn hồ sơ |
| `conversation_messages` | organization_id, conversation_id, direction (`INBOUND`, `OUTBOUND`), external_message_id, content_type, content, sent_by_user_id, delivery_status, occurred_at, interaction_id | unique (conversation_id, direction, external_message_id) để chống trùng |
| `ai_invocations` | organization_id, actor_type (`USER`, `SYSTEM`), actor_user_id, use_case (`C360_SUMMARY`, `HANDOVER_BRIEF`, `REPLY_DRAFT`, `SIGNAL_EXTRACT`), subject_type, subject_id, provider, model, prompt_version, input_hash, redaction_count, input_tokens, output_tokens, cost_estimate, latency_ms, status (`SUCCEEDED`, `FAILED`, `BLOCKED_BUDGET`, `DISABLED`), error_code | USER có user ID, SYSTEM có user ID null; không lưu prompt thô chứa dữ liệu cá nhân; lưu hash để đối chiếu |
| `ai_suggestions` | organization_id, invocation_id, kind, subject_type, subject_id, content, evidence (jsonb: message id và đoạn trích), confidence, status (`PROPOSED`, `ACCEPTED`, `EDITED`, `REJECTED`, `EXPIRED`), final_content, reviewed_by, reviewed_at | chỉ người có quyền trên subject được duyệt |
| `customer_signals` | organization_id, lead_id, customer_id, message_id, signal_type (`INTENT`, `SENTIMENT`, `URGENCY`, `OBJECTION`, `OFFERING_INTEREST`), value, confidence, evidence_text, source (`AI`, `HUMAN`), suggestion_id, status, model_version | chỉ `ACCEPTED`/`EDITED` hoặc `HUMAN` được dùng cho workflow và KPI |

Bảng giá trị cho `signal_type = INTENT` (TLCN): `ASK_PRICE`, `ASK_INFO`, `BOOKING`, `PURCHASE_READY`, `COMPLAINT`, `SUPPORT`, `OTHER`. `SENTIMENT`: `POSITIVE`, `NEUTRAL`, `NEGATIVE`. Nhóm dữ liệu chốt nhãn cuối cùng trong hướng dẫn gán nhãn.

## 4. Luồng chính

### 4.1. Tin nhắn đến

```text
Nền tảng → POST /api/v1/channels/{type}/webhook
  1. Xác thực: Telegram header X-Telegram-Bot-Api-Secret-Token; Messenger X-Hub-Signature-256;
     Zalo mô phỏng dùng secret riêng. Sai → 401, không ghi dữ liệu.
  2. Trong cùng transaction, xác thực account, tìm/tạo conversation, ghi message, Interaction session và outbox; chỉ trả 200 khi transaction đã lưu thành công.
  3. Chống trùng theo (conversation, INBOUND, external_message_id).
  4. Tìm hoặc tạo conversation; không tự dò hoặc tạo Lead/Customer. Nếu chưa gắn hồ sơ,
     giữ chưa gắn; owner = người phụ trách mặc định của account kênh (DEC-23).
  5. Lưu message; mở hoặc nối phiên Interaction khi tin gần nhất cách không quá 4 giờ.
  6. Ghi outbox MESSAGE_RECEIVED → workflow-core:
       - Hội thoại mới có owner → Task "Phản hồi đầu tiên" (idempotency theo conversation).
  7. Job AI-07 riêng quét tin INBOUND gần đây đã gắn hồ sơ theo DEC-24; không tiêu thụ lại outbox.
     Gateway dùng actor SYSTEM, che dữ liệu và kiểm hạn mức; tín hiệu AI ở trạng thái PROPOSED.
```

### 4.2. Trả lời

`POST /api/v1/conversations/{id}/messages` → kiểm tra quyền `conversation.reply` và scope conversation → adapter gửi đồng bộ → khi nền tảng xác nhận, lưu `OUTBOUND`, nối Interaction session và audit. W13 chưa có retry outbound hoặc kiểm tra cửa sổ nền tảng; ZALO_SIM chỉ mô phỏng. Bản nháp AI chỉ đi qua endpoint này khi người dùng bấm gửi.

### 4.3. Gọi AI

```text
Copilot/Signal → AiGateway.invoke(useCase, subject, actor)
  1. Kiểm tra cờ POSE_AI_ENABLED và quyền ai.assist.use.
  2. ContextBuilder gọi service lõi bằng quyền của actor (không query trực tiếp bảng).
  3. Redactor che điện thoại, email, giấy tờ định danh; đếm số lần che.
  4. Budget kiểm tra hạn mức theo tổ chức; vượt → BLOCKED_BUDGET.
  5. Provider (Claude hoặc Mock) với prompt có phiên bản; giới hạn thời gian.
  6. Ghi ai_invocations; kết quả → ai_suggestions (PROPOSED) hoặc customer_signals.
  7. Lỗi → trạng thái FAILED, giao diện báo "Chưa có gợi ý", CRM tiếp tục bình thường.
```

Provider chọn bằng `POSE_AI_PROVIDER` (`mock` | `gemini` | `claude`). Theo DEC-22, demo dùng Gemini trước (mặc định `gemini-3.6-flash` cho mọi use case, khóa `POSE_AI_GEMINI_API_KEY`, chỉ dữ liệu demo); Claude là dự phòng (`claude-haiku-5-5` cho `SIGNAL_EXTRACT`, `claude-sonnet-5-5` cho tóm tắt, hồ sơ bàn giao và bản nháp; khóa `POSE_AI_API_KEY`). Khóa đặt trong biến môi trường, không commit và không in ra log. Kiểm thử tự động dùng `MockProvider` cho kết quả xác định.

Theo DEC-24, job tự động chỉ gửi nội dung tin INBOUND đã gắn hồ sơ qua Gateway với actor SYSTEM; không giả danh user hay dựng toàn bộ hồ sơ. Luồng vẫn che dữ liệu, kiểm budget theo tổ chức và ghi invocation; không tự gửi tin.

## 5. Quyền mới

| Permission | Admin | Manager | Sales | Care | Data Staff |
|---|---|---|---|---|---|
| `channel.manage` | ✓ | | | | |
| `conversation.read` | ✓ | nhóm | hồ sơ của mình | khách được giao chăm sóc | |
| `conversation.reply` | | ✓ | hồ sơ của mình | khách được giao chăm sóc | |
| `conversation.assign` | | ✓ | | | |
| `ai.assist.use` | ✓ | ✓ | ✓ | ✓ | |
| `ai.signal.review` | | ✓ | ✓ | ✓ | |
| `ai.admin` (cấu hình, nhật ký quản trị) | ✓ | | | | |
| `ai.metrics.read` (chỉ số tổng hợp) | ✓ | | | | ✓ |

Admin cần `conversation.read` để thấy toàn tenant; Manager cần quyền này để thấy account thuộc team mình; Sales/Care chỉ thấy conversation được giao. `conversation.reply` mặc định được cấp cho Manager, Sales và Care; Admin chỉ có thể reply nếu được cấp thêm quyền. Data Staff không đọc nội dung tin nhắn, prompt hay kết quả AI. Việc giao conversation giới hạn trong team account và người nhận phải active, có `conversation.reply`.

## 6. API hội thoại đã triển khai

| Method | Đường dẫn | Mục đích |
|---|---|---|
| POST | `/api/v1/channels/{type}/webhook` | Nhận tin từ nền tảng (không dùng JWT, xác thực chữ ký) |
| GET | `/api/v1/conversations` | Danh sách theo scope, lọc trạng thái và assignedToMe |
| GET | `/api/v1/conversations/{id}/messages` | Tin nhắn có phân trang |
| POST | `/api/v1/conversations/{id}/messages` | Trả lời |
| POST | `/api/v1/conversations/{id}/link` | Gắn hội thoại với Lead hoặc Customer |
| POST | `/api/v1/conversations/{id}/assign` | Manager giao hội thoại trong team account |
| POST | `/api/v1/ai/copilot/customers/{id}/summary` | Tóm tắt Customer 360 |
| POST | `/api/v1/ai/copilot/customers/{id}/handover-brief` | Hồ sơ bàn giao khi đổi người phụ trách |
| POST | `/api/v1/ai/copilot/conversations/{id}/reply-draft` | Bản nháp trả lời |
| GET | `/api/v1/ai/suggestions?subject=…` | Gợi ý đang chờ duyệt |
| POST | `/api/v1/ai/suggestions/{id}/accept` · `/reject` | Duyệt gợi ý hoặc tín hiệu, có thể kèm nội dung đã sửa |
| GET | `/api/v1/ai/metrics` | Số lần gọi, tỷ lệ chấp nhận, chi phí, độ trễ (không có nội dung) |
| GET | `/api/v1/dashboard/kpis` | Bổ sung nhóm E theo DEC-21 |

## 7. KPI nhóm E — Giá trị và rò rỉ

| Mã | Chỉ số | Công thức gợi ý |
|---|---|---|
| E01 | Lead quá hạn phản hồi | Lead mở chưa có Interaction đầu tiên sau SLA (mặc định 24 giờ) |
| E02 | Giá trị cơ hội không được chăm sóc | Tổng expected value của Opportunity mở không có Interaction trong N ngày (mặc định 14) |
| E03 | Khách hàng sau bàn giao chưa được liên hệ | Customer đổi owner trong kỳ, chưa có Interaction của owner mới |
| E04 | Thời gian phản hồi tin nhắn đầu tiên | Trung vị khoảng cách INBOUND đầu tiên → OUTBOUND đầu tiên theo kênh |
| E05 | Tỷ lệ gợi ý AI được chấp nhận | (ACCEPTED + EDITED) / gợi ý đã duyệt, theo loại |

Định nghĩa triển khai, ngưỡng, phạm vi và Việc hôm nay theo [DEC-21](../meetings/decision-log.md#dec-21--kpi-giá-trị-và-rò-rỉ-việc-hôm-nay) và [contract Dashboard](dashboard-kpi-api-v1.md). E04 chưa hỗ trợ đến khi có dữ liệu hội thoại; từ điển và SQL đối soát độc lập do Nhi hoàn thiện.

## 8. Đánh giá AI

- Bộ nhãn chuẩn do nhóm dữ liệu chuẩn bị: hội thoại tiếng Việt đa ngành, hai người gán nhãn độc lập, đo Cohen's kappa; tách tập phát triển prompt và tập kiểm tra.
- Chỉ số: macro-F1 cho ý định và cảm xúc; tỷ lệ chấp nhận gợi ý trong demo; kiểm tra mẫu tính trung thực của tóm tắt; độ trễ p95; chi phí trên 100 tin nhắn.
- Báo cáo đánh giá nêu rõ cỡ mẫu và giới hạn; không tuyên bố độ chính xác ngoài bộ dữ liệu đã đo.

## 9. Cấu hình

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `POSE_AI_ENABLED` | `false` | Bật lớp AI |
| `POSE_AI_PROVIDER` | `mock` | `mock`, `gemini` hoặc `claude` (DEC-22) |
| `POSE_AI_API_KEY` | (trống) | Khóa Claude, chỉ trong môi trường |
| `POSE_AI_GEMINI_API_KEY` | (trống) | Khóa Gemini, chỉ trong môi trường |
| `POSE_AI_GEMINI_MODEL_*`, `POSE_AI_GEMINI_STRUCTURED_OUTPUT` | `gemini-3.6-flash`, `true` | Model Gemini theo use case; tắt gửi schema nếu cần |
| `POSE_AI_GEMINI_THINKING_LEVEL` | rỗng | Mức thinking gửi cho Gemini (`minimal`, `low`, `medium`, `high`); rỗng là không gửi |
| `POSE_AI_GEMINI_INPUT_USD_PER_MILLION`, `POSE_AI_GEMINI_OUTPUT_USD_PER_MILLION` | `0`, `0` | Giá ước tính, 0 cho gói miễn phí |
| `POSE_AI_MONTHLY_BUDGET_USD` | `5` | Hạn mức mỗi tổ chức |
| `POSE_AI_TIMEOUT_SECONDS`, `POSE_AI_MAX_RETRIES` | `30`, `1` | Timeout và số lần thử lại của SDK |
| `POSE_AI_MAX_TOKENS`, `POSE_AI_MAX_CONTEXT_CHARS` | `16000`, `12000` | Giới hạn output và context đã che |
| `POSE_AI_COPILOT_REUSE_MINUTES` | `10` | Khoảng thời gian tái sử dụng gợi ý Copilot đang chờ duyệt (`PROPOSED`) |
| `POSE_AI_MODEL_*`, `POSE_AI_EFFORT_*` | Theo DEC-19 | Model và effort cho từng use case |
| `POSE_AI_HAIKU_*_USD_PER_MILLION`, `POSE_AI_SONNET_*_USD_PER_MILLION` | Theo DEC-19 | Đơn giá ước tính token |
| `POSE_CHANNEL_TELEGRAM_BOT_TOKEN`, `POSE_CHANNEL_TELEGRAM_WEBHOOK_SECRET` | (trống) | Token và secret webhook Telegram |
| `POSE_CHANNEL_MESSENGER_APP_SECRET`, `POSE_CHANNEL_MESSENGER_PAGE_TOKEN` | (trống) | Messenger chế độ phát triển |
| `POSE_CONVERSATION_SESSION_GAP_HOURS` | `4` | Ngưỡng tách phiên Interaction |

Khi demo trên Supabase dùng chung, chỉ người giữ credential đặt các biến này; agent không giữ, nhập hay in giá trị.

## 10. Ngoài phạm vi TLCN

DISC, RFM, Predictive CLV, huấn luyện mô hình, Next Best Action bằng mô hình, Zalo OA thật, cập nhật thời gian thực bắt buộc, Copilot tự gọi công cụ. Các mục này thuộc KLTN theo SRS 2.0 Phần II.

## 11. Quyết định và giới hạn triển khai

- Quy tắc owner cho hội thoại mới, giữ hồ sơ chưa khớp và Task qua outbox đã chốt tại DEC-23 và triển khai theo contract Conversation API v1.
- `ZALO_SIM` chỉ hỗ trợ contract mô phỏng trong repo; không cam kết tương thích Zalo OA thật.
- Ngưỡng SLA cho E01 và số ngày N cho E02 theo từng ngành, hoặc dùng chung.

Theo DEC-25, V16 cho phép `ai_invocations.subject_type = CONVERSATION`; reply-draft dùng scope hội thoại của actor và không tự gửi tin.
