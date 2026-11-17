# Tín hiệu khách hàng v0

Trạng thái: contract triển khai AI-07, thử trên database mới; V14 của AI-02 phải vào nhánh trước khi áp V15 lên database lâu dài. Các API yêu cầu phiên hợp lệ, lấy organization từ phiên, trả `Cache-Control: no-store` và dùng envelope lỗi foundation.

## Phạm vi và dữ liệu

Tín hiệu thuộc đúng một Customer hoặc Lead, gắn một tin nhắn INBOUND của hội thoại đã gắn hồ sơ cùng tổ chức. `INTENT`: `ASK_PRICE`, `ASK_INFO`, `BOOKING`, `PURCHASE_READY`, `COMPLAINT`, `SUPPORT`, `OTHER`; `SENTIMENT`: `POSITIVE`, `NEUTRAL`, `NEGATIVE`. Các loại `URGENCY`, `OBJECTION`, `OFFERING_INTEREST` được dành cho sau, v0 không tạo hoặc duyệt khi chưa chốt nhãn.

Mỗi tín hiệu có ID, loại, giá trị, confidence từ 0 đến 1 hoặc null, trích đoạn (đã che với AI, nguyên văn tin với HUMAN), nguồn, trạng thái, message ID, invocation ID, model, thời gian tạo và thông tin duyệt. Mỗi tin chỉ có tối đa một tín hiệu AI cho từng loại. Chỉ `ACCEPTED`, `EDITED` hoặc nguồn `HUMAN` được dùng cho KPI/workflow; v0 không tự kích hoạt workflow. Nhân viên có thể tạo tín hiệu HUMAN từ tin INBOUND gắn hồ sơ.

Đọc hồ sơ theo DEC-11/12/15, đồng thời cần quyền đọc hội thoại để truy cập nội dung nguồn. Theo quyết định của Tài ngày 13/11/2026, tín hiệu Customer cần `ai.assist.use`, `customer_360.read`, `conversation.read` và scope Customer 360 như Copilot; Lead dùng quyền và scope đọc Lead hiện có cùng `conversation.read`. Duyệt thêm `ai.signal.review`; trích Lead thêm `ai.assist.use`. Nếu hội thoại đã gỡ liên kết hoặc đổi sang hồ sơ khác, tín hiệu cũ không được liệt kê hay duyệt qua hồ sơ trước. Data Staff, kể cả khi kiêm vai trò khác, không được đọc hoặc tạo tín hiệu nội dung. Ngoài scope hoặc khác tenant trả `403 FORBIDDEN_SCOPE`; thiếu quyền trả `403 FORBIDDEN_PERMISSION`.

## Endpoint

| Method | Đường dẫn | Mục đích |
|---|---|---|
| GET | `/api/v1/ai/signals?subjectType=CUSTOMER&subjectId=<uuid>` | Danh sách tín hiệu trong phạm vi hồ sơ |
| GET | `/api/v1/ai/signals/messages?subjectType=CUSTOMER&subjectId=<uuid>` | Tối đa 100 tin INBOUND mới nhất của hồ sơ; trả ID, thời gian, 160 ký tự đầu |
| POST | `/api/v1/ai/signals` | Thêm tín hiệu HUMAN cho tin thuộc hồ sơ |
| POST | `/api/v1/ai/signals/extract` | Trích tối đa số tin INBOUND chưa xử lý cho hồ sơ, ưu tiên tin mới nhất |
| POST | `/api/v1/ai/signals/{id}/accept` | Chấp nhận tín hiệu `PROPOSED` |
| POST | `/api/v1/ai/signals/{id}/edit` | Duyệt và sửa nhãn, body `{"value":"ASK_INFO"}` |
| POST | `/api/v1/ai/signals/{id}/reject` | Từ chối tín hiệu `PROPOSED` |

Request extract: `{"subjectType":"CUSTOMER","subjectId":"00000000-0000-0000-0000-000000000001"}`. Hội thoại chưa gắn hồ sơ hoặc tin OUTBOUND không được trích. Job tự động độc lập, không tiêu thụ outbox `MESSAGE_RECEIVED`.

## Job tự động (DEC-24)

Job tắt mặc định (`POSE_AI_SIGNAL_AUTO_EXTRACT=false`) và chỉ chạy khi `POSE_AI_ENABLED=true`. Chu kỳ `POSE_AI_SIGNAL_AUTO_INTERVAL_MS` mặc định 60000 ms; mỗi lượt tối đa `POSE_AI_SIGNAL_AUTO_BATCH` tin toàn hệ thống (mặc định 5, từ 1 đến 20). Chỉ chọn INBOUND trong `POSE_AI_SIGNAL_AUTO_LOOKBACK_HOURS` giờ gần nhất (mặc định 24), đã gắn đúng một Lead/Customer; ưu tiên tin mới nhất. Hội thoại chưa gắn được xét sau khi gắn nếu tin còn trong khung thời gian.

Luồng dùng `customer_signal_extractions` và cùng bộ lọc/lưu với API thủ công. `SUCCEEDED` kể cả không có tín hiệu là hoàn tất; `INVALID_OUTPUT` tối đa hai lần; lỗi tạm thời được thử lại. `DISABLED` dừng lượt, `BLOCKED_BUDGET` bỏ qua tổ chức đó; hai trạng thái không ghi lượt trích. Một instance không chạy chồng, ràng buộc unique bảo vệ kết quả khi nhiều instance cùng xử lý. Gateway ghi `actor_type=SYSTEM`, `actor_user_id=null`, che nội dung và tính budget tenant; không tự gửi tin. Tín hiệu vẫn `PROPOSED` và cần người có quyền duyệt.

Chỉ với job tự động, một tin bị loại khỏi lượt chọn khi đã có `POSE_AI_SIGNAL_AUTO_MAX_ATTEMPTS` lượt `FAILED` thuộc mọi mã lỗi (mặc định 3, từ 1–10), hoặc khi lượt `FAILED` gần nhất chưa cách hiện tại đủ `POSE_AI_SIGNAL_AUTO_RETRY_DELAY_MINUTES` phút (mặc định 15, từ 1–1440); đúng mốc hết thời gian chờ có thể thử lại. Các lượt FAILED từ API thủ công cũng được tính. Quy tắc hai lượt `INVALID_OUTPUT` vẫn áp dụng. Lỗi `TIMEOUT`, `PROVIDER_ERROR` hoặc `RATE_LIMIT` (429) dừng toàn bộ lượt; `BLOCKED_BUDGET` vẫn chỉ bỏ qua tenant và `DISABLED` dừng lượt. Cấu hình batch, lookback, số lần thử hoặc thời gian chờ không hợp lệ làm ứng dụng báo lỗi khi khởi động nếu job được bật. Giới hạn và thời gian chờ này không áp dụng cho endpoint extract thủ công hay phép tính `remaining` của endpoint đó.

Extract trả đối tượng gồm `results` theo từng tin và số `remaining` còn chờ, ví dụ `{"results":[{"messageId":"00000000-0000-0000-0000-000000000002","invocationId":"00000000-0000-0000-0000-000000000003","status":"SUCCEEDED","errorCode":null,"created":1}],"remaining":3}`. Không có tin phù hợp trả `{"results":[],"remaining":0}`. Mỗi request xử lý tối đa `POSE_AI_SIGNAL_MAX_MESSAGES` tin mới nhất; mặc định 2, chỉ nhận 1–5. Trước tin thứ hai trở đi, nếu thời gian từ đầu request vượt `POSE_AI_SIGNAL_REQUEST_BUDGET_SECONDS` (mặc định 25 giây, chỉ nhận 5–60), backend dừng; tin đầu luôn được xử lý. `remaining` gồm tin chưa xử lý và tin có thể thử lại. Danh sách tín hiệu trả mảng; mỗi thao tác duyệt trả tín hiệu sau cập nhật.

Provider nhận ngữ cảnh đã che và JSON schema. Chỉ giữ nhãn hợp lệ, confidence hợp lệ và `evidenceText` là đoạn có thật trong tin nhắn đã che. `{"signals":[]}` là kết quả hợp lệ: ghi `SUCCEEDED`, không tạo tín hiệu và không gọi lại cho tin đó. JSON sai hoặc mảng có tín hiệu nhưng tất cả bị lọc là `FAILED`/`INVALID_OUTPUT`; cho thử lại một lần, sau hai lượt như vậy tin được coi đã xử lý với 0 tín hiệu. Các lỗi tạm thời `TIMEOUT`, `RATE_LIMIT`, `PROVIDER_ERROR`, `NETWORK` được thử lại trong request sau. AI tắt trả `DISABLED`, vượt hạn mức trả `BLOCKED_BUDGET`; cả hai dừng vòng lặp ngay và không lưu lượt trích theo tin. Các tin đó vẫn được tính trong `remaining`. Lỗi lưu trả `FAILED`/`STORAGE_ERROR` và có thể thử lại. Các tín hiệu và dấu hoàn tất của một tin được lưu cùng transaction. Audit duyệt chỉ ghi ID và trạng thái, không ghi nội dung.

Sai value trả 422 với lỗi trường `value`; trạng thái đã duyệt trả 409. Ví dụ tín hiệu giả: `{"signalType":"INTENT","value":"ASK_PRICE","confidence":0.9,"evidenceText":"Cho tôi hỏi giá","source":"AI","status":"PROPOSED"}`.

## Nhân viên thêm tín hiệu

`POST /api/v1/ai/signals` nhận `{"subjectType":"CUSTOMER","subjectId":"00000000-0000-0000-0000-000000000001","messageId":"00000000-0000-0000-0000-000000000002","signalType":"INTENT","value":"ASK_PRICE","evidenceText":"Cho tôi hỏi giá"}`; `evidenceText` có thể bỏ qua hoặc null, tối đa 500 ký tự và phải là đoạn nguyên văn của tin gốc. Thành công trả 201, `source=HUMAN`, `status=ACCEPTED`, người tạo là người duyệt, không có confidence/model/invocation. Không gọi AI nên sử dụng được khi AI tắt.

Cần `ai.signal.review`, `conversation.read` và scope hồ sơ; Customer còn cần `ai.assist.use` và `customer_360.read`. Data Staff bị chặn kể cả khi kiêm role. Mỗi tin/loại chỉ có một tín hiệu HUMAN; `human_message_id` bằng `message_id` với HUMAN, null với AI; unique theo organization, human_message_id và signal_type. Tín hiệu AI cùng loại không bị ảnh hưởng. Duyệt lại HUMAN trả 409 `AI_SIGNAL_REVIEWED`; trùng HUMAN trả 409 `AI_SIGNAL_DUPLICATE`. Sai loại, nhãn hay trích đoạn trả 422 với field tương ứng. Tin không tồn tại, OUTBOUND, khác tenant, thuộc hồ sơ khác hoặc đã gỡ liên kết cùng trả 422 tại `messageId`, không phân biệt sự tồn tại; scope hồ sơ vẫn kiểm trước. Audit chỉ gồm ID, loại, nhãn, người tạo.
