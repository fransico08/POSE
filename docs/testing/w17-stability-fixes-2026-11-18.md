# Evidence W17 — Ổn định backend, giai đoạn 2 (18/11/2026)

Thực hiện trên nhánh `Tài` (`D:\POSE`), theo quyết định [DEC-32](../meetings/decision-log.md#dec-32--ổn-định-backend-w17-giai-đoạn-2) của Tài trên báo cáo giai đoạn 1 ([w17-stability-review-2026-11-19.md](w17-stability-review-2026-11-19.md)). Không sửa migration `V1–V17` (không tạo `V18`), không sửa seed/CSV/Data Dictionary của Nhi, không kết nối Supabase hoặc PostgreSQL cổng `5432`, không gọi AI/Telegram thật.

## 1. Tóm tắt thay đổi theo từng mục

### W17-04 — Khóa `FOR NO KEY UPDATE` chống deadlock khóa ngoại (ưu tiên 1)

- **Tái hiện trước khi sửa (PostgreSQL 18.3, cụm cô lập `127.0.0.1:54329`):**
  - Viết `W17ConcurrencyIntegrationTest` chạy hai transaction trên hai kết nối riêng có `CountDownLatch` đồng bộ:
    - Cặp (a) `reply ↔ webhook INBOUND` trên cùng hội thoại: tái hiện **`SQLSTATE=40P01`** (`PessimisticLockingFailureException`, PostgreSQL báo `while locking tuple (0,1) in relation "organizations" ... FOR KEY SHARE OF x` do `FoundationAccess.lockAndRefresh` giữ `organizations FOR UPDATE` còn `INSERT INTO conversation_messages` cần `FOR KEY SHARE` qua FK `organization_id`).
    - Cặp (b) `convert Lead ↔ webhook INBOUND` trên hội thoại của Lead: tái hiện **`SQLSTATE=40P01`** (`PessimisticLockingFailureException`, PostgreSQL báo `while locking tuple (0,5) in relation "conversations"`).
  - Trong lượt chạy toàn bộ bộ test PostgreSQL sau khi đổi `organizations`, phát hiện thêm vòng chờ thứ hai của cặp (b): `LeadConversionService` giữ `leads FOR UPDATE` trong khi `ChannelWebhookService` gọi `interactionSessions.attach(...)` chèn `interactions` cần `FOR KEY SHARE` lên `leads` qua FK `lead_id` → tiếp tục tái hiện **`SQLSTATE=40P01`**.
- **Cách sửa:**
  - `FoundationAccess.lockAndRefresh`: dùng `FOR NO KEY UPDATE` trên PostgreSQL, giữ `FOR UPDATE` trên H2 (nhận diện dialect qua `DatabaseMetaData.getDatabaseProductName()` tương tự `ChannelWebhookService`, cache theo instance).
  - `OrganizationRepository.find(id, true)`: áp dụng cùng nguyên tắc `FOR NO KEY UPDATE` trên PostgreSQL / `FOR UPDATE` trên H2.
  - `LeadConversionService.convert`: đổi khóa dòng `leads` sang `FOR NO KEY UPDATE` trên PostgreSQL / `FOR UPDATE` trên H2.
- **Kiểm chứng sau sửa:**
  - Cả hai cặp (a) và (b) đều hoàn tất không `40P01`, dữ liệu nhất quán (đủ cả tin `INBOUND`/`OUTBOUND`; Lead chuyển `CONVERTED` và hội thoại chuyển sang Customer).
  - Ca `twoMutationsInSameOrganizationStillSerializeOnLockAndRefresh` xác nhận hai mutation cùng tổ chức vẫn chờ nhau tuần tự.

### W17-02 — Refresh quyền không khóa cho các API chỉ đọc

- Rà toàn bộ 41 lời gọi `lockAndRefresh` trong `backend/src/main`: xác định đúng **5 phương thức chỉ đọc**:
  1. `ChannelAccountService.list`
  2. `ConversationService.list`
  3. `ConversationService.messages`
  4. `ConversationService.draftContext`
  5. `ConversationService.assignees`
- Chuyển cả 5 phương thức sang `@Transactional(readOnly = true)` và `FoundationAccess.refresh(incoming)` (không khóa bảng `organizations`); giữ nguyên `lockAndRefresh` cho toàn bộ mutation.
- **Kiểm chứng:**
  - `W17ConcurrencyIntegrationTest#revokingConversationReadPermissionImmediatelyReturns403OnNextRead`: thu hồi `conversation.read` → request đọc kế tiếp nhận ngay `403 FORBIDDEN_PERMISSION`.
  - `W17ConcurrencyIntegrationTest#readApisDoNotBlockOnInFlightOrganizationMutation`: trong lúc một mutation đang giữ khóa `organizations`, `ConversationService.list` và `messages` hoàn tất ngay mà không bị chặn.
- **Ghi chú phát hiện ở E2E:** Sau khi đường đọc không còn chờ khóa của mutation, lượt E2E đầu tiên phát hiện `frontend/e2e/inbox-authorization.spec.ts:231` dùng `getByText(...)` khớp nhầm vào nội dung đang nằm trong `<textarea>` trước khi `POST /messages` commit xong; đã thu hẹp selector về `getByRole("list", { name: "Tin nhắn" })` để chờ tin `OUTBOUND` hiển thị thực sự.

### W17-01 — Batch `opportunity_lines` trong `OpportunityRepository.list`

- Tách `mapRow` (không truy vấn con) và tải `opportunity_lines` bằng một truy vấn duy nhất `WHERE ol.organization_id=? AND ol.opportunity_id IN (?,...) ORDER BY ol.opportunity_id, ol.position`, sau đó ghép vào danh sách theo đúng thứ tự trang ban đầu và thứ tự `position`.
- **Kiểm chứng:** `OpportunityIntegrationTest#listLoadsOpportunityLinesInSingleBatchWithoutGrowingSqlCount` bật Logback DEBUG cho `JdbcTemplate`, so sánh số câu `Executing prepared SQL statement` giữa trang 1 Cơ hội và trang 5 Cơ hội (mỗi Cơ hội 2 dòng): số câu SQL **không đổi** (`countForFive == countForOne`), giữ đúng thứ tự `updated_at DESC` và thứ tự dòng `FIRST → SECOND`.

### W17-05 — Log lỗi an toàn trong `ApiExceptionHandler`

- Rà toàn bộ logger trong `backend/src/main` và `automation/`: chỉ `ApiExceptionHandler` từng truyền nguyên `exception` vào `log.error(...)`.
- Sửa `ApiExceptionHandler` (`dataConflict`, `auditConflict`, `unexpected`) chỉ ghi có cấu trúc `traceId`, `exception`, `rootException`, `sqlState` và `constraint` (đọc tên constraint từ `PSQLException.getServerErrorMessage().getConstraint()` hoặc Hibernate `ConstraintViolationException`, lọc bằng regex định danh an toàn `^[A-Za-z0-9_.$"-]{1,128}$`), **không** truyền `Throwable` vào logger và **không** ghi `message`/`cause`. Response API giữ nguyên contract.
- **Kiểm chứng:** `W17SafeErrorLoggingIntegrationTest` kiểm tra với dữ liệu giả (`Canary Customer W17`, `canary-w17-secret@example.test`, `0909888777`, `SENSITIVE_PROMPT_PAYLOAD_W17`):
  - Vi phạm `CHECK` trên PostgreSQL (`SQLSTATE=23514`, `constraint=w17_check_canary`) qua HTTP `POST /api/v1/customers` → HTTP `409 CONFLICT`, log không có stack trace và không chứa bất kỳ giá trị giả nào.
  - Vi phạm `UNIQUE` trên PostgreSQL (`SQLSTATE=23505`, `constraint=uq_customers_org_email`) → HTTP `409`/`500` đúng contract, log không chứa giá trị giả.

### W17-06 — Fail-fast khi thiếu `POSE_DATABASE_URL`

- Bỏ giá trị mặc định `jdbc:postgresql://localhost:5432/pose` trong `backend/src/main/resources/application.yml` (`url: ${POSE_DATABASE_URL:}`).
- Thêm `DatabaseUrlGuard` (`BeanFactoryPostProcessor`) kiểm tra `spring.datasource.url` trước khi khởi tạo `DataSource`; nếu trống thì ném `IllegalStateException` với thông báo yêu cầu đặt `POSE_DATABASE_URL` (không in mật khẩu/credential).
- Cập nhật `backend/README.md` và `docs/huong-dan-chay-local.md` (chỉ ghi tên biến).
- **Lưu ý triển khai cho nhóm:** Từ mốc commit `ba8a795` trở đi, **các thành viên khác bắt buộc phải đặt biến môi trường `POSE_DATABASE_URL` khi chạy backend local** (trỏ cụm local riêng ở cổng `54330` hoặc Supabase dùng chung); nếu bỏ trống backend sẽ dừng ngay khi khởi động thay vì tự nối vào `localhost:5432`. Các bộ test H2, `backend/scripts/test-postgres-v2.ps1` và `scripts/e2e-env.ps1` tự truyền URL riêng nên không bị ảnh hưởng.

### W17-03 và W17-07 / W17-08 / W17-09

- **W17-03:** Không đổi luồng gửi trả lời; đã ghi nhận giới hạn gửi lặp khi rollback DB trong `DEC-32` và `docs/api/conversation-api-v1.md`. Outbox/idempotency chuyển sang KLTN.
- **W17-07, W17-08, W17-09:** Không sửa trong W17; để đo thêm / chuyển sang KLTN.

---

## 2. Nhật ký toàn bộ lượt kiểm thử (bao gồm lượt lỗi và mã lỗi)

| Lượt chạy | Lệnh / Phạm vi | Kết quả | Mã thoát / Mã lỗi | Ghi chú |
|---|---|---|---|---|
| W17-04 tái hiện trước sửa | `W17ConcurrencyIntegrationTest` trên PostgreSQL 54329 | 1 đạt, 2 lỗi | `SQLSTATE=40P01` (cặp a); cặp b ban đầu `LEAD_CHANGED` do lệch `expectedUpdatedAt` trong test, sau khi sửa fixture chạy lại tái hiện **`SQLSTATE=40P01`** | Xác nhận đúng vòng chờ FK `organizations` |
| PostgreSQL 54329 full lần 1 (trước sửa `leads` lock) | `backend/scripts/test-postgres-v2.ps1` | 472/474 đạt (1 fail, 1 error) | `exit=1`; `W17ConcurrencyIntegrationTest` lỗi `SQLSTATE=40P01` (vòng chờ thứ 2 trên `leads FOR UPDATE` ↔ FK `interactions.lead_id`); `SignalAutoExtractIntegrationTest` lỗi `ArrayIndexOutOfBoundsException` khi parse class | Sửa `LeadConversionService` sang `FOR NO KEY UPDATE` trên PostgreSQL; chạy lại 2 suite 27/27 PASS |
| PostgreSQL 54329 full lần 2 (sau sửa `leads` lock) | `backend/scripts/test-postgres-v2.ps1` | 494/495 đạt (1 fail) | `exit=1`; `LeadConversionIntegrationTest#conversionLocksConversationsBeforeMovingInteractions:165` (`expected >= 0, actual -1`) vì assertion cũ chỉ kiểm `endsWith("FOR UPDATE")` | Cập nhật assertion nhận cả `FOR UPDATE` và `FOR NO KEY UPDATE` (`13b1ed4`) |
| **PostgreSQL 54329 bản sạch cuối (`13b1ed4`)** | `backend/scripts/test-postgres-v2.ps1` (xóa `classes`, `test-classes`, `surefire-reports`, `workflow-core/target`) | **`Verified.`** — `workflow-core` **1/1**, `pose-backend` **495/495** (60 file XML, 0 fail, 0 error, 0 skip) | **`exit=0`** | Regression DB `pose_foundation_test_20261119090103038`, review DB `pose_v2_review_20261119090103038`; cụm 54329 đã dừng sạch |
| **Backend H2 bản sạch cuối (`13b1ed4`)** | `mvn -pl backend -am -Dlogging.level.root=ERROR verify` | `workflow-core` **1/1**, `pose-backend` **481 tổng, 447 đạt, 34 bỏ qua, 0 lỗi** (60 file XML) | **`exit=0`** | 34 ca skip là các test chỉ chạy trên PostgreSQL (gồm 5 ca PG-only mới của W17) và test acceptance/smoke |
| **Frontend Typecheck, Lint & Vitest** | `npm run typecheck`, `npm run lint`, `npx vitest run` | Typecheck **PASS**, Lint **PASS**, Vitest **375/375** (32 file) | **`exit=0`** | Không có cảnh báo |
| E2E mặc định lượt 1 (`-Build`) — trước sửa selector | `scripts/e2e-env.ps1 -Build` | 37/38 đạt, 1 lỗi | `exit=1`; `e2e\inbox-authorization.spec.ts:236` (`Expected: true, Received: false`) | Do dòng 231 khớp text trong `<textarea>` trước khi `POST /messages` commit xong; W17-02 làm `GET /messages` không còn bị chặn bởi khóa tổ chức. Đã sửa selector (`0869a54`) |
| **E2E mặc định lượt 1 (sau sửa selector)** | `scripts/e2e-env.ps1` | **38/38 đạt (1,3 phút)** | **`exit=0`** | 5 cổng `8080, 8081, 3010, 3011, 54329` đã dừng sạch |
| **E2E mặc định lượt 2** | `scripts/e2e-env.ps1` | **38/38 đạt (1,2 phút)** | **`exit=0`** | 5 cổng `8080, 8081, 3010, 3011, 54329` đã dừng sạch |
| **E2E `-WorkflowWorker`** | `scripts/e2e-env.ps1 -WorkflowWorker` | **1/1 đạt (10,5 giây)** | **`exit=0`** | 5 cổng `8080, 8081, 3010, 3011, 54329` đã dừng sạch |

## 3. Giới hạn

- Không gọi API AI thật hay Telegram Bot API thật; không kết nối Supabase hoặc PostgreSQL cổng `5432`.
- Không tạo index hay migration `V18`; mọi đề xuất tối ưu truy vấn (`W17-07/08/09`) và outbox outbound (`W17-03`) giữ nguyên để đo thêm / triển khai ở KLTN.
