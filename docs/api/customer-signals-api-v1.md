# Tín hiệu khách hàng v0

Trạng thái: contract triển khai AI-07, thử trên database mới; V14 của AI-02 phải vào nhánh trước khi áp V15 lên database lâu dài. Các API yêu cầu phiên hợp lệ, lấy organization từ phiên, trả `Cache-Control: no-store` và dùng envelope lỗi foundation.

## Phạm vi và dữ liệu

Tín hiệu thuộc đúng một Customer hoặc Lead, gắn một tin nhắn INBOUND của hội thoại đã gắn hồ sơ cùng tổ chức. `INTENT`: `ASK_PRICE`, `ASK_INFO`, `BOOKING`, `PURCHASE_READY`, `COMPLAINT`, `SUPPORT`, `OTHER`; `SENTIMENT`: `POSITIVE`, `NEUTRAL`, `NEGATIVE`. Các loại `URGENCY`, `OBJECTION`, `OFFERING_INTEREST` được dành cho sau, v0 không tạo hoặc duyệt khi chưa chốt nhãn.

Mỗi tín hiệu có ID, loại, giá trị, confidence từ 0 đến 1, trích đoạn đã che, nguồn, trạng thái, message ID, invocation ID, model, thời gian tạo và thông tin duyệt. Mỗi tin chỉ có tối đa một tín hiệu AI cho từng loại. Chỉ `ACCEPTED`, `EDITED` hoặc nguồn `HUMAN` được dùng cho KPI/workflow; v0 không tự kích hoạt workflow và không có API tạo tín hiệu thủ công.

Đọc hồ sơ theo DEC-11/12/15, đồng thời cần quyền đọc hội thoại để truy cập nội dung nguồn. Theo quyết định của Tài ngày 13/11/2026, tín hiệu Customer cần `ai.assist.use`, `customer_360.read`, `conversation.read` và scope Customer 360 như Copilot; Lead dùng quyền và scope đọc Lead hiện có cùng `conversation.read`. Duyệt thêm `ai.signal.review`; trích Lead thêm `ai.assist.use`. Nếu hội thoại đã gỡ liên kết hoặc đổi sang hồ sơ khác, tín hiệu cũ không được liệt kê hay duyệt qua hồ sơ trước. Data Staff, kể cả khi kiêm vai trò khác, không được đọc hoặc tạo tín hiệu nội dung. Ngoài scope hoặc khác tenant trả `403 FORBIDDEN_SCOPE`; thiếu quyền trả `403 FORBIDDEN_PERMISSION`.

## Endpoint

| Method | Đường dẫn | Mục đích |
|---|---|---|
| GET | `/api/v1/ai/signals?subjectType=CUSTOMER&subjectId=<uuid>` | Danh sách tín hiệu trong phạm vi hồ sơ |
| POST | `/api/v1/ai/signals/extract` | Trích tối đa số tin INBOUND chưa xử lý cho hồ sơ, ưu tiên tin mới nhất |
| POST | `/api/v1/ai/signals/{id}/accept` | Chấp nhận tín hiệu `PROPOSED` |
| POST | `/api/v1/ai/signals/{id}/edit` | Duyệt và sửa nhãn, body `{"value":"ASK_INFO"}` |
| POST | `/api/v1/ai/signals/{id}/reject` | Từ chối tín hiệu `PROPOSED` |

Request extract: `{"subjectType":"CUSTOMER","subjectId":"00000000-0000-0000-0000-000000000001"}`. Hội thoại chưa gắn hồ sơ hoặc tin OUTBOUND không được trích. Không nối outbox `MESSAGE_RECEIVED` cho tới khi AI-02 phát sự kiện đó.

Extract trả đối tượng gồm `results` theo từng tin và số `remaining` còn chờ, ví dụ `{"results":[{"messageId":"00000000-0000-0000-0000-000000000002","invocationId":"00000000-0000-0000-0000-000000000003","status":"SUCCEEDED","errorCode":null,"created":1}],"remaining":3}`. Không có tin phù hợp trả `{"results":[],"remaining":0}`. Mỗi request xử lý tối đa `POSE_AI_SIGNAL_MAX_MESSAGES` tin mới nhất; mặc định 2, chỉ nhận 1–5. Trước tin thứ hai trở đi, nếu thời gian từ đầu request vượt `POSE_AI_SIGNAL_REQUEST_BUDGET_SECONDS` (mặc định 25 giây, chỉ nhận 5–60), backend dừng; tin đầu luôn được xử lý. `remaining` gồm tin chưa xử lý và tin có thể thử lại. Danh sách tín hiệu trả mảng; mỗi thao tác duyệt trả tín hiệu sau cập nhật.

Provider nhận ngữ cảnh đã che và JSON schema. Chỉ giữ nhãn hợp lệ, confidence hợp lệ và `evidenceText` là đoạn có thật trong tin nhắn đã che. `{"signals":[]}` là kết quả hợp lệ: ghi `SUCCEEDED`, không tạo tín hiệu và không gọi lại cho tin đó. JSON sai hoặc mảng có tín hiệu nhưng tất cả bị lọc là `FAILED`/`INVALID_OUTPUT`; cho thử lại một lần, sau hai lượt như vậy tin được coi đã xử lý với 0 tín hiệu. Các lỗi tạm thời `TIMEOUT`, `RATE_LIMIT`, `PROVIDER_ERROR`, `NETWORK` được thử lại trong request sau. AI tắt trả `DISABLED`, vượt hạn mức trả `BLOCKED_BUDGET`; cả hai dừng vòng lặp ngay và không lưu lượt trích theo tin. Các tin đó vẫn được tính trong `remaining`. Lỗi lưu trả `FAILED`/`STORAGE_ERROR` và có thể thử lại. Các tín hiệu và dấu hoàn tất của một tin được lưu cùng transaction. Audit duyệt chỉ ghi ID và trạng thái, không ghi nội dung.

Sai value trả 422 với lỗi trường `value`; trạng thái đã duyệt trả 409. Ví dụ tín hiệu giả: `{"signalType":"INTENT","value":"ASK_PRICE","confidence":0.9,"evidenceText":"Cho tôi hỏi giá","source":"AI","status":"PROPOSED"}`.
