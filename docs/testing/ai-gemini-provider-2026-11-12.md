# Evidence — provider Gemini cho AI Gateway (DEC-22)

**Ngày:** 12/11/2026. **Người thực hiện:** Claude Code theo yêu cầu của Tài. **Phạm vi:** thêm provider `gemini` bên cạnh `claude` và `mock`; không đổi hành vi `ClaudeProvider`, không migration, không đổi giao diện.

## Nguồn tài liệu Google

Đọc ngày 12/11/2026 (các URL `ai.google.dev/...` chuyển hướng sang `aistudio.google.com/docs/...`): `api/generate-content`, `gemini-api/docs/structured-output`, `models`, `pricing`, `terms`, `rate-limits`, `troubleshooting`. Các trang không ghi ngày cập nhật.

## Thiết kế

| Hạng mục | Cách làm |
|---|---|
| Gọi API | `POST {base}/v1beta/models/{model}:generateContent` bằng `java.net.http.HttpClient`, không thêm thư viện |
| Khóa | Header `x-goog-api-key`; không nằm trong URL, log hay body; khóa Gemini tách khỏi khóa Claude |
| Prompt và input | `systemInstruction` (prompt có phiên bản) và `contents` (input đã che dữ liệu) |
| Structured output | `generationConfig.responseMimeType=application/json` và `responseJsonSchema`; bỏ các từ khóa schema bị Gemini bỏ qua; cờ `POSE_AI_GEMINI_STRUCTURED_OUTPUT=false` để tắt |
| Token | Đầu vào `promptTokenCount`; đầu ra `candidatesTokenCount` + `thoughtsTokenCount` |
| Thử lại | Chỉ 429, 408 và 5xx, theo `pose.ai.max-retries`; không thử lại 4xx khác |
| Model và giá | Mặc định `gemini-3.6-flash`, giá 0; cấu hình được; `AiSettings.model` và `cost` phân biệt theo provider |

## Bảng ánh xạ lỗi

| Tình huống Gemini | Mã chuẩn |
|---|---|
| HTTP 429 | `RATE_LIMIT` |
| HTTP 408, 504 | `TIMEOUT` |
| HTTP 500, 502, 503 và 5xx khác | `PROVIDER_ERROR` |
| HTTP 400, 401, 403, 404 và 4xx khác | `BAD_REQUEST` |
| `promptFeedback.blockReason`, hoặc `finishReason` thuộc SAFETY, BLOCKLIST, PROHIBITED_CONTENT, SPII, RECITATION, ESCALATION | `REFUSAL` |
| Không có candidate hoặc không có text, JSON hỏng | `PROVIDER_ERROR` |
| `InterruptedIOException`, `HttpTimeoutException` (kể cả trong chuỗi nguyên nhân) | `TIMEOUT` |
| Lỗi kết nối khác (ví dụ `ConnectException`) | `NETWORK` |
| Thiếu khóa hoặc tên model không hợp lệ | `BAD_REQUEST` trước khi gọi mạng; ở gateway thiếu khóa là `DISABLED` |

Từ khóa schema bị bỏ khi gửi Gemini: `minProperties`, `maxProperties`, `minLength`, `maxLength`, `uniqueItems`, `contains`, `minContains`, `maxContains`, `$schema`. `additionalProperties`, `required`, `items`, `minItems` được giữ.

## Test

| Test | Nội dung | Kết quả |
|---|---|---|
| `GeminiProviderTest` (10 test, server giả loopback) | Khóa ở header không ở URL; endpoint và model đúng; body có systemInstruction, contents, maxOutputTokens, schema; log không chứa khóa, prompt, input; chuyển đổi schema; tắt structured output; blockReason và finishReason an toàn; phản hồi rỗng, JSON hỏng, phần thought bị bỏ; ánh xạ HTTP; 429 thử lại đúng 1 lần; 400 không thử lại, 500 thử lại; ánh xạ lỗi I/O không phụ thuộc thời gian; timeout socket thật; thiếu khóa và model sai | Đạt; chạy riêng 10 lượt liên tiếp, 10/10 đạt |
| `AiGeminiGatewayTest` (3 test) | Thiếu khóa Gemini → `DISABLED`, không gọi provider nào, ghi `provider=gemini`; output Gemini đi qua lớp lọc nguồn (ý nguồn lạ bị loại, toàn bộ nguồn lạ → `INVALID_OUTPUT`); `AiSettings` dùng đúng khóa, model, giá theo provider, giữ nguyên Claude | Đạt |
| Hồi quy | `AiUnitTest` (sửa một test dùng settings giả để stub `keyMissing()`), `ClaudeProviderTest`, `AiGatewayIntegrationTest`, `AiDisabledIntegrationTest`, `CopilotIntegrationTest` | Đạt |

## Kết quả bản build sạch

Bản sạch: đã xóa `backend/target/classes`, `test-classes`, `surefire-reports`, `automation/workflow-core/target`; không `mvn clean`, giữ cluster `backend/target/auth-postgres-test`. JDK 25.0.2, Maven 3.9.15. Số từ Surefire XML:

| Kiểm thử | Tổng | Đạt | Bỏ qua | Lỗi |
|---|---:|---:|---:|---:|
| workflow-core | 1 | 1 | 0 | 0 |
| Backend H2 | 295 | 269 | 26 | 0 |
| Backend PostgreSQL 54329 (`Verified`) | 295 | 282 | 13 | 0 |
| Frontend Vitest (27 file) | 314 | 314 | 0 | 0 |
| Playwright E2E, `-Build`, provider mock | 31 | 31 | 0 | 0 |

So với mốc trước (281 test): tăng 14 test backend (10 `GeminiProviderTest` và 3 `AiGeminiGatewayTest` mới, cộng 1 do cấu trúc lớp); `typecheck` đạt; các cổng 8080, 8081, 3010, 3011, 54329 đã dừng sau E2E.

## Thử thật — NOT RUN

Chưa gọi Gemini thật: không dùng khóa thật trong phiên này. Bốn giả định chưa xác nhận trong tài liệu phải kiểm ở lượt này (xem DEC-22): header `x-goog-api-key`, vị trí `systemInstruction`, cặp `responseMimeType` + `responseJsonSchema`, hạn mức gói miễn phí của model Flash.

Checklist để Tài tự chạy, khóa nhập kín trong cửa sổ PowerShell của Tài:

1. Lấy khóa mới tại Google AI Studio.
2. `powershell.exe -NoProfile -File .\scripts\dev.ps1 -AiProvider gemini`, dán khóa khi được hỏi.
3. Đăng nhập Manager EDU, mở một Customer 360 có nhiều tương tác; bấm "Tóm tắt hồ sơ" và "Soạn hồ sơ bàn giao".
4. Kiểm: tiếng Việt tự nhiên; mỗi ý có chip nguồn đúng; không bịa; số điện thoại và email bị che; Chấp nhận, Sửa, Từ chối hoạt động.
5. Đăng nhập Admin EDU, mở `/api/backend/ai/metrics` và `/api/backend/ai/status` (đúng `provider=gemini`).
6. Ghi vào bảng dưới, không ghi khóa hay nội dung hồ sơ:

| Chỉ số | Giá trị |
|---|---|
| Số lần gọi, số `SUCCEEDED` | |
| Số `INVALID_OUTPUT` hoặc `ai_suggestions.evidence.droppedPoints` > 0 | |
| Độ trễ p50, p95 (ms) | |
| Có `RATE_LIMIT`, `BAD_REQUEST` hoặc `REFUSAL` không | |
| Chất lượng tiếng Việt, mức bịa thông tin (đạt, chưa đạt) | |

Nếu gặp `BAD_REQUEST` ngay lượt đầu: thử `POSE_AI_GEMINI_STRUCTURED_OUTPUT=false` để loại giả định số 3, rồi báo kết quả để chỉnh.

## Kết quả lượt thử thật đầu tiên (12/11/2026, Tài chạy)

Gemini `gemini-3.6-flash` trả được bản tóm tắt: tiếng Việt tự nhiên, chip nguồn đúng, email và điện thoại bị che. Ba giả định đầu của DEC-22 (header khóa, `systemInstruction`, `responseMimeType` + `responseJsonSchema`) được xác nhận; hạn mức gói miễn phí vẫn chưa rõ. Ba lượt gọi: hai `PROVIDER_ERROR` (token 0), một `SUCCEEDED` (415 token vào, 1601 token ra, 12,7 giây).

Vấn đề phát hiện và cách xử lý:

| Vấn đề | Nguyên nhân | Xử lý |
|---|---|---|
| Giao diện báo "Không thể kết nối mạng" dù backend đã lưu gợi ý | Proxy BFF hủy request sau 10 giây, Gemini mất 12–13 giây | Timeout BFF cho route `/ai` là 70 giây; trình duyệt chờ 75 giây cho hai lệnh Copilot; route khác giữ 10 giây |
| Hai lượt `PROVIDER_ERROR` chưa rõ nguyên nhân | Log không ghi mã HTTP | Gemini ghi `status`, `code`, `attempt` (không ghi khóa, prompt, nội dung phản hồi); gateway ghi `errorCode` khi thất bại |
| `[EMAIL_1]`, `[PHONE_1]` hiện trong bản tóm tắt | Prompt bảo giữ nguyên token | Prompt `c360-summary` và `handover-brief` lên v2: không nhắc giá trị liên hệ |
| Kết quả FAILED hiện cùng một thông báo | Chưa phân biệt mã lỗi | Thông báo theo `errorCode` (`RATE_LIMIT`, `TIMEOUT`, `NETWORK`, `REFUSAL`, `INVALID_OUTPUT`, `PROVIDER_ERROR`) |

Số liệu 5 lượt thành công đầu (bảng `ai_invocations`, provider `gemini`, model `gemini-3.6-flash`, dữ liệu demo EDU, gói miễn phí, 12/11/2026): độ trễ 12,7; 14,1; 28,9; 31,3 và 41,2 giây (p50 khoảng 29 giây, p95 khoảng 39 giây); token ra 1425 đến 2461; `droppedPoints` bằng 0 ở cả 5 lượt. Lượt thất bại: 9 `PROVIDER_ERROR` (8 lượt tức thời ở lần chạy đầu khi khóa nhập chưa đúng, 1 lượt khoảng 12,9 giây trước khi có log chi tiết) và 2 `TIMEOUT` ở 30 giây. Độ trễ lớn và dao động mạnh, token ra rất cao so với bản tóm tắt ngắn nên nghi do thinking mặc định của model (bảng tài liệu ghi mặc định `medium` cho `gemini-3.6-flash`).

Giảm độ trễ: thêm `POSE_AI_GEMINI_THINKING_LEVEL` (mặc định rỗng, không gửi gì). Khi đặt `minimal`, `low`, `medium` hoặc `high`, backend gửi `generationConfig.thinkingConfig.thinkingLevel`. Tài liệu Google đã đọc chỉ mô tả `thinking_level` của Interactions API, không có trang `generateContent`, nên tên trường này **chưa được xác nhận** cho endpoint đang dùng và chưa chạy thật; nếu Google trả `BAD_REQUEST` thì đặt lại rỗng. Tài liệu ghi `max_output_tokens` gồm cả thinking token.

Prompt `handover-brief` lên v3: ngày viết dd/MM/yyyy (bản v2 để lộ dạng ISO), tránh dấu câu kép. `c360-summary` giữ v2.

Kiểm tra: Vitest 316/316, typecheck đạt; `GeminiProviderTest` 12, `AiUnitTest` 7, `AiGeminiGatewayTest` 3, `CopilotIntegrationTest` 6, `AiGatewayIntegrationTest` 6, `ClaudeProviderTest` 8 đều đạt (H2). Sau toàn bộ sửa đổi (gồm prompt bàn giao v3 và tùy chọn thinking), bản sạch: workflow-core 1/1; H2 297 (271 đạt, 26 bỏ qua, 0 lỗi); PostgreSQL 54329 297 (284 đạt, 13 bỏ qua, 0 lỗi, `Verified`); Vitest 316/316; typecheck đạt; E2E 31/31 (`-Build`, provider mock); các cổng đã dừng. Chưa thử lại với Gemini thật sau khi sửa. Độ trễ 12 giây (nghi do thinking token) chưa xử lý.

## Giới hạn

- Chưa gọi Gemini hoặc Claude thật; không Supabase, không PostgreSQL 5432.
- Gemini gói miễn phí không dùng được với dữ liệu thật (DEC-22 D3).
- Bản SRS `.docx` và tab Google Doc chưa được dán lại sau khi sửa dòng nhà cung cấp trong `docs/SRS-POSE-v2-CRM-AI.md`.
