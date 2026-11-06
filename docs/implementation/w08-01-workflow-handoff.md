# W08-01 — Handoff tích hợp workflow/outbox

## Phạm vi đã chuẩn bị

### Bổ sung tích hợp 06/11/2026 — chờ Nhi review

`LeadQualifiedTrigger` và payload outbox bổ sung UUID `qualifiedByUserId`, là
actor đã qualify, bên cạnh ID organization, Lead và owner. Publisher dùng connection
gắn transaction Spring của request; rollback Lead khi ghi outbox lỗi. H2 test dùng
cú pháp conflict tương thích; PostgreSQL giữ conflict theo khóa tenant/idempotency.
Không đưa contact hoặc nội dung Lead vào payload. Processor được bổ sung runner
transaction do Spring cung cấp, resolver nhận ID outbox, callback audit khi insert
Task và nhánh SQL H2 cho regression; cách claim/retry dùng lock và kiểm tra lại
version retry. Thay đổi kernel này cần Nhi review. Worker vẫn tắt mặc định.

`automation/workflow-core` là kernel Java thuần, test được không cần Spring hay
database. Nó đánh giá duy nhất `LEAD_QUALIFIED`, sinh key idempotency ổn định,
xử lý batch hữu hạn và phân loại retry bounded. Kernel không được dùng trực tiếp
trong request HTTP và không tin tenant/owner từ payload.

Module hiện có thêm adapter JDBC độc lập:
`JdbcTransactionalOutboxPublisher` chỉ ghi outbox qua connection transaction do
caller cung cấp; `JdbcWorkflowProcessor` dùng PostgreSQL `FOR UPDATE SKIP LOCKED`,
upsert execution/task và status outbox trong cùng transaction DB. Các lớp này
chỉ dùng `java.sql`/`DataSource`, nên biên dịch trên nhánh Nhi nhưng chưa phải
Spring bean hay scheduler đang chạy.

## Điểm nối cần Tài review

Các file sau là owner của Tài và không bị sửa trên nhánh Nhi:

1. `backend/src/main/java/com/pose/backend/lead/LeadService.java` — trong
   `transition(..., qualify=true)`, sau `leads.transition(...)` và trong cùng
   `@Transactional`, gọi `TransactionalOutboxPublisher.publishLeadQualified(...)`.
   Publisher nhận `organizationId`, Lead ID, owner ID và timestamp từ bản ghi
   đã lock/validate; insert `outbox_events` dùng key namespace theo Lead event.
   Nếu insert outbox lỗi, transaction Lead phải rollback.
2. `backend/src/main/java/com/pose/backend/task/TaskRepository.java` — API tạo
   thủ công hiện hard-code `origin='MANUAL'` và không nhận idempotency key, nên
   không được gọi bởi worker. Tạo adapter/repository workflow riêng hoặc thêm
   insert tenant-scoped `origin='WORKFLOW'` dùng `INSERT ... ON CONFLICT
   (organization_id, idempotency_key) ... RETURNING id`; đồng thời xác thực
   Lead QUALIFIED và owner active/eligible trong transaction.
3. New adapter package đề xuất:
   `com.pose.backend.workflow` trong backend tích hợp. Adapter JDBC phải thực
   hiện claim bằng `FOR UPDATE SKIP LOCKED`; task insert, execution DONE và
   outbox PUBLISHED phải cùng transaction DB. Không gọi HTTP hay giữ lock qua I/O.
   Wiring Spring tối thiểu: cung cấp `TransactionalConnection` bằng connection
   transaction-bound của Spring, `AutomationActorResolver` từ service user đã
   duyệt, `WorkflowTaskTemplate` từ policy Task được duyệt và gọi
   `JdbcWorkflowProcessor.processPendingBatch(...)` từ scheduler độc lập.

## Contract đã đối chiếu với origin/Tài và origin/Thắng

Cả hai ref hiện tại đều chưa có package workflow/outbox. `LeadService.transition`
chỉ đổi Lead và audit; `TaskRepository.insert` luôn ghi `origin='MANUAL'` và
không nhận `idempotency_key`. Vì vậy kernel không thể được gắn vào backend Nhi
hiện tại (chỉ có README) và không được gọi `TaskService.create(...)`.

### SQL transaction cần adapter JDBC thực hiện

Trong transaction đang qualify Lead, sau khi Lead row đã được lock/validate:

```sql
INSERT INTO outbox_events (
    id, organization_id, aggregate_type, aggregate_id, event_type, payload,
    idempotency_key, delivery_status, retry_count, occurred_at
)
VALUES (?, ?, 'LEAD', ?, 'LEAD_QUALIFIED', ?::jsonb, ?, 'PENDING', 0, ?)
ON CONFLICT (organization_id, idempotency_key) DO NOTHING;
```

Key outbox phải tái tạo từ facts đã commit, ví dụ
`outbox.lead-qualified.v1:<lead-id>:<qualified-at-utc>`, không dùng UUID ngẫu
nhiên mới ở mỗi lần retry. Worker claim một batch trong transaction DB bằng:

```sql
SELECT id, organization_id, aggregate_id, payload, event_type, retry_count, occurred_at
FROM outbox_events
WHERE delivery_status = 'PENDING' AND event_type = 'LEAD_QUALIFIED'
ORDER BY occurred_at, id
FOR UPDATE SKIP LOCKED
LIMIT ?;
```

Với mỗi event đã claim: load Lead/owner cùng `organization_id`, upsert execution
theo `(organization_id, idempotency_key)`, rồi insert task dùng partial unique
index hiện có:

```sql
INSERT INTO crm_tasks (
    id, organization_id, lead_id, owner_user_id, created_by_user_id, title,
    priority, status, due_at, origin, idempotency_key, created_at, updated_at
)
VALUES (?, ?, ?, ?, ?, ?, ?, 'OPEN', ?, 'WORKFLOW', ?, ?, ?)
ON CONFLICT (organization_id, idempotency_key)
WHERE idempotency_key IS NOT NULL
DO UPDATE SET idempotency_key = EXCLUDED.idempotency_key
RETURNING id;
```

Sau khi task tồn tại, update `workflow_executions` thành `DONE` và outbox thành
`PUBLISHED` trong cùng transaction. Bất cứ lỗi nào rollback toàn bộ ba thay đổi.
Không được giữ lock này qua HTTP/I/O ngoài DB.

### Blocker contract cần Tài quyết định

`crm_tasks.created_by_user_id` là NOT NULL nhưng workflow không được mạo danh
người dùng. Cần một automation/service user hợp lệ theo tenant **hoặc** một
schema/contract đã duyệt biểu diễn system actor trước khi dùng SQL trên. Không
được thay thế tùy tiện bằng `owner_user_id`.

## Giới hạn schema cần quyết định trước production

Schema V2 có `outbox_events` và `workflow_executions` nhưng không có
`next_retry_at`, lease owner/expiry hoặc trạng thái outbox RUNNING. Vì vậy retry
backoff bền vững và recovery stale-worker chưa thể chứng minh an toàn. Tài cần
duyệt migration riêng thêm lịch retry/lease (hoặc một chiến lược claim atomically
được kiểm chứng); không tự thêm migration trong W08-01.

## Contract bắt buộc

- Unique DB là nguồn chống trùng: `(organization_id, idempotency_key)` trên
  outbox, execution và task. Key kernel: `workflow.lead-qualified.follow-up.v1:<outbox-event-id>`.
- Worker chỉ đánh dấu PUBLISHED/DONE sau khi task `origin=WORKFLOW` đã tồn tại;
  replay phải trả đúng task cũ. Retry tối đa do cấu hình adapter truyền vào.
- Không dùng `TaskService.create(...)` công khai để mạo danh actor, không sửa
  Lead để kích trigger, và không log payload/credential nhạy cảm.

## Xác minh thủ công sau tích hợp

1. Chạy unit/integration test backend với JDK 21 và PostgreSQL cô lập.
2. Chạy `database/scripts/verify_w08_01_workflow_gui.sql` trên `pose_crm_v2`.
3. Chỉ chấp nhận khi các query `violation_count` bằng 0 và kiểm tra giao cùng
   outbox event hai lần vẫn có đúng một `crm_tasks` WORKFLOW.
