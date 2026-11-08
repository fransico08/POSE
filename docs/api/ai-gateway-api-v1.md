# AI Gateway và quản trị AI v1

Áp dụng DEC-19. API yêu cầu phiên hợp lệ và luôn trả `Cache-Control: no-store`; organization lấy từ phiên. Gateway nội bộ không phải endpoint Copilot.

| Method | Đường dẫn | Permission | Scope |
|---|---|---|---|
| GET | `/api/v1/ai/status` | `ai.assist.use` | Organization trong phiên; `ai.admin` được xem thêm provider và ngân sách còn lại |
| GET | `/api/v1/ai/suggestions?subjectType=CUSTOMER&subjectId=<uuid>` | `ai.assist.use` | Quyền đọc hồ sơ theo service Customer 360 hoặc Lead |
| POST | `/api/v1/ai/suggestions/{id}/accept` | `ai.assist.use` | Đọc được hồ sơ liên quan; chỉ `PROPOSED` |
| POST | `/api/v1/ai/suggestions/{id}/reject` | `ai.assist.use` | Như accept |
| GET | `/api/v1/ai/metrics?start=<instant>&end=<instant>` | `ai.metrics.read` | Tổng hợp của organization; không trả nội dung |

`accept` nhận `{}` hoặc `{"finalContent":"Nội dung đã chỉnh"}`; có nội dung chỉnh thì chuyển `EDITED`, nếu không thì `ACCEPTED`. `reject` chuyển `REJECTED`. Những trạng thái khác không chuyển lại và trả 409 `AI_SUGGESTION_REVIEWED`. Audit chỉ chứa ID, không ghi nội dung. List trả các trường định danh, nội dung đã che và trạng thái; không mở rộng scope của hồ sơ gốc.

Ví dụ trạng thái cho user thường: `{"enabled":false}`. Admin có thêm `provider` và `remainingBudgetUsd`. Các response ví dụ dùng dữ liệu giả; không gửi hoặc lưu credential vào API quản trị.

Ví dụ `GET /ai/suggestions?subjectType=CUSTOMER&subjectId=00000000-0000-0000-0000-000000000001`:

```json
[{"id":"00000000-0000-0000-0000-000000000002","invocationId":"00000000-0000-0000-0000-000000000003","kind":"C360_SUMMARY","subjectType":"CUSTOMER","subjectId":"00000000-0000-0000-0000-000000000001","content":"Khách hàng cần được theo dõi.","status":"PROPOSED","finalContent":null,"reviewedBy":null,"reviewedAt":null,"createdAt":"2026-11-08T00:00:00Z"}]
```

`metrics` nhận `start`, `end` ISO-8601; lấy các invocation được tạo trong `[start,end)` và suggestion được duyệt trong cùng khoảng. Trả `counts` theo use case/status, `inputTokens`, `outputTokens`, `costEstimateUsd`, `acceptanceRate`, `latencyP50Ms`, `latencyP95Ms`. Tỷ lệ chấp nhận là `(ACCEPTED + EDITED) / (ACCEPTED + EDITED + REJECTED)`; không có mẫu số thì null. Percentile latency dùng thứ hạng gần nhất; không có dữ liệu thì null.

Gateway xử lý tuần tự: cờ bật → `ai.assist.use` → service đọc hồ sơ theo actor → che dữ liệu → kiểm ngân sách → provider → ghi invocation. Khi tắt, thiếu khóa hoặc vượt ngân sách, kết quả lần lượt là `DISABLED` hoặc `BLOCKED_BUDGET`; lỗi provider trả `FAILED`, không làm hỏng luồng CRM. Có thể có `errorCode`: `REFUSAL`, `RATE_LIMIT`, `PROVIDER_ERROR`, `TIMEOUT`, `NETWORK`, `BAD_REQUEST`. Thành công là `SUCCEEDED`, tạo suggestion `PROPOSED`. Invocation chỉ lưu hash SHA-256 của input đã che và metadata; không lưu prompt hoặc output thô. Ngân sách là kiểm tra mềm: các lời gọi song song có thể vượt nhẹ.

V1 tạm tính tháng ngân sách theo UTC; đơn giá do môi trường cấu hình và chi phí là ước tính.

Nếu dựng context hoặc đọc ngân sách gặp lỗi nội bộ, gateway trả `FAILED` với `CONTEXT_ERROR` hoặc `BUDGET_ERROR`. Nếu database không ghi được invocation, trả `FAILED`/`STORAGE_ERROR` và chỉ log ID/trạng thái; trong tình huống database lỗi không thể bảo đảm có hàng invocation.

Thiếu permission trả 403 `FORBIDDEN_PERMISSION`; hồ sơ ngoài scope hoặc khác tenant trả 403 `FORBIDDEN_SCOPE`. Tham số không hợp lệ trả 400 `VALIDATION_ERROR`; lỗi dùng envelope `{code,message,fieldErrors,traceId}` của foundation. Không tạo endpoint sinh nội dung trong card này; endpoint Copilot thuộc AI-06.
