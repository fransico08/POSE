# API AI Copilot v1

Ngày: 08/11/2026 · Owner: Huỳnh Minh Tài · Quyết định: [DEC-19](../meetings/decision-log.md#dec-19--ai-gateway-và-quản-trị-ai), [DEC-20](../meetings/decision-log.md#dec-20--ai-copilot-và-đổi-người-phụ-trách-customer-tối-giản)

Tài liệu này mô tả các endpoint AI Copilot trên hồ sơ Customer 360: tóm tắt hồ sơ (`C360_SUMMARY`) và soạn hồ sơ bàn giao (`HANDOVER_BRIEF`). Mọi phản hồi thành công có `Cache-Control: no-store`.

## 1. Quyền và phạm vi

- Actor phải có permission `ai.assist.use` và đọc được hồ sơ Customer 360 của khách hàng đó (`customer_360.read` kèm scope hợp lệ: Admin toàn tổ chức, Manager theo nhóm quản lý, Sales là owner hoặc có SALES assignment `ACTIVE`, Customer Care có CARE assignment `ACTIVE`).
- Backend chặn role Data Staff kể cả khi kiêm role khác: Data Staff có scope Customer 360 toàn tổ chức, theo DEC-15 D2 chỉ xem metadata đối soát và không được dùng AI nội dung.
- Thiếu permission trả `403 FORBIDDEN_PERMISSION`. Hồ sơ không tồn tại, đã archived, khác tổ chức hoặc ngoài scope đọc Customer 360 trả `403 FORBIDDEN_SCOPE`; backend kiểm tra quyền và scope trước khi gọi provider AI.
- Khi lớp AI đang tắt (`POSE_AI_ENABLED=false`), endpoint ghi một bản ghi `ai_invocations` với `status = "DISABLED"` và trả `200 OK` với `status: "DISABLED"`, không trả `5xx`.

## 2. Routes

Base: `/api/v1`

| Method | Đường dẫn | Use case | Mục đích |
|---|---|---|---|
| POST | `/ai/copilot/customers/{id}/summary` | `C360_SUMMARY` | Tóm tắt hồ sơ Customer 360 theo quyền của người xem |
| POST | `/ai/copilot/customers/{id}/handover-brief` | `HANDOVER_BRIEF` | Soạn hồ sơ bàn giao từ bản ghi bàn giao `APPROVED` mới nhất (hoặc bản chuẩn bị bàn giao nếu chưa có) |

Việc duyệt (`accept`/`reject`) và tra cứu danh sách gợi ý (`GET /api/v1/ai/suggestions`) dùng chung contract trong [API AI Gateway v1](ai-gateway-api-v1.md).

## 3. Request

Body JSON tùy chọn (có thể để trống hoặc `{}`):

```json
{
  "force": false
}
```

- `force` (`boolean`, mặc định `false`):
  - Khi `force = false`: AI Gateway kiểm permission và scope rồi dựng, che và cắt context; chỉ dùng lại suggestion `PROPOSED` cùng Customer, loại, người tạo và hash context trong `pose.ai.copilot.reuse-minutes` (mặc định `10` phút), không gọi provider. Context thay đổi thì tạo mới.
  - Khi `force = true`: bỏ qua gợi ý `PROPOSED` sẵn có và gọi AI tạo gợi ý mới.
- Gửi trường lạ ngoài `force` hoặc kiểu dữ liệu không phải boolean trả `400 VALIDATION_ERROR`.

## 4. Ngữ cảnh, mã nguồn và lọc kết quả (DEC-20 D4)

1. **Dựng ngữ cảnh có mã nguồn:**
   - `AiContextBuilder` gọi `Customer360Service.profile(actor, customerId)` theo đúng quyền người gọi, sau đó gắn trường `source` cho từng thực thể:
     - Hồ sơ khách hàng: `PROFILE`
     - Cơ hội: `OPPORTUNITY:<id>`
     - Tương tác: `INTERACTION:<id>`
     - Nhiệm vụ: `TASK:<id>`
     - Bản ghi bàn giao `SALES` đã `APPROVED` mới nhất của Customer (với `HANDOVER_BRIEF`): `HANDOVER:<id>` gồm lý do, người yêu cầu, người nhận và thời điểm; không có danh sách ID đã chuyển vì bảng bàn giao không lưu. Nếu chưa có bản ghi bàn giao, ngữ cảnh đánh dấu chế độ chuẩn bị bàn giao (`PREPARE_HANDOVER`).
   - Trước khi gửi sang provider, giá trị chuỗi trong JSON được che dữ liệu nhạy cảm (`[EMAIL_1]`, `[PHONE_1]`, `[IDENTITY_1]`…), bỏ qua trường `source` và ID, rồi cắt theo từng phần tử để JSON không vượt `pose.ai.max-context-chars` (mặc định `12000` ký tự).
2. **Định dạng kết quả bắt buộc (JSON Schema):**
   ```json
   {
     "sections": [
       {
         "heading": "Tổng quan hồ sơ",
         "points": [
           {
             "text": "Khách hàng doanh nghiệp đang quan tâm gói dịch vụ...",
             "sources": ["PROFILE", "OPPORTUNITY:00000000-0000-0000-0000-000000000001"]
           }
         ]
       }
     ]
   }
   ```
3. **Hậu xử lý và lọc nguồn:**
   - Backend kiểm tra JSON trả về từ mô hình, che dữ liệu nhạy cảm trên từng `heading` và `text`.
   - Với mỗi ý (`point`), backend chỉ giữ các mã trong `sources` thực sự xuất hiện trong ngữ cảnh đã gửi.
   - Ý không còn mã nguồn hợp lệ nào (hoặc `text` trống) bị loại bỏ và tăng bộ đếm `droppedPoints`. Mục (`section`) không còn ý nào sau khi lọc cũng bị loại bỏ.
   - Nếu JSON sai định dạng hoặc không còn ý nào hợp lệ sau khi lọc:
     - `ai_invocations` ghi `status = "FAILED"`, `error_code = "INVALID_OUTPUT"`.
     - Không tạo dòng trong `ai_suggestions`.
     - Response trả `status: "FAILED"`, `errorCode: "INVALID_OUTPUT"`, `suggestion: null`.
   - Nếu còn ít nhất một ý hợp lệ:
     - Lưu chuỗi JSON đã lọc vào `ai_suggestions.content`.
     - Lưu `evidence` (`jsonb`) dạng `{"sources":["PROFILE",...],"droppedPoints":0}`.

## 5. Response

HTTP `200 OK` cho mọi kết quả xử lý của AI Gateway (`SUCCEEDED`, `DISABLED`, `BLOCKED_BUDGET`, `FAILED`):

```json
{
  "status": "SUCCEEDED",
  "errorCode": null,
  "reused": false,
  "invocationId": "00000000-0000-0000-0000-000000000101",
  "suggestion": {
    "id": "00000000-0000-0000-0000-000000000202",
    "invocationId": "00000000-0000-0000-0000-000000000101",
    "kind": "C360_SUMMARY",
    "subjectType": "CUSTOMER",
    "subjectId": "00000000-0000-0000-0000-000000000303",
    "content": "{\"sections\":[{\"heading\":\"Tổng quan khách hàng\",\"points\":[{\"text\":\"Khách hàng đang theo dõi gói sản phẩm chính.\",\"sources\":[\"PROFILE\"]}]}]}",
    "evidence": "{\"sources\":[\"PROFILE\"],\"droppedPoints\":0}",
    "status": "PROPOSED",
    "finalContent": null,
    "reviewedBy": null,
    "reviewedAt": null,
    "createdAt": "2026-11-08T10:00:00Z"
  }
}
```

Khi `status` là `DISABLED`, `BLOCKED_BUDGET` hoặc `FAILED`:
- `suggestion` là `null`.
- `reused` là `false`.
- `errorCode` là `null` đối với `DISABLED` và `BLOCKED_BUDGET`; với `FAILED` có thể là `REFUSAL`, `INVALID_OUTPUT`, `PROVIDER_ERROR` hoặc mã lỗi gateway khác.

## 6. Bảng mã lỗi HTTP

| HTTP / Code | Trường hợp |
|---|---|
| `400 VALIDATION_ERROR` | UUID không hợp lệ, JSON sai cú pháp, trường lạ hoặc `force` không phải boolean |
| `401 AUTH_INVALID_TOKEN` | Chưa đăng nhập hoặc token không hợp lệ theo contract foundation |
| `403 FORBIDDEN_PERMISSION` | Không có permission `ai.assist.use` hoặc actor có role `DATA_STAFF` |
| `403 FORBIDDEN_SCOPE` | Customer không tồn tại, đã archived, khác tổ chức hoặc ngoài scope đọc Customer 360 |
