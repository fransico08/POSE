# W08-02 — Ma trận retry, idempotency và evidence workflow

Mọi ca chạy trên PostgreSQL cô lập sau khi W08-01 được Tài tích hợp. `NOT RUN / PENDING_QA` là trạng thái thực tế mặc định; seed CSV hoặc unit test không phải evidence runtime.

| ID | Priority | Input / precondition | Bước QA | Kết quả mong đợi | Evidence cần thu thập | Owner | Actual status |
|---|---|---|---|---|---|---|---|
| TC01 | P0 | Lead `NEW`, owner SALES active, người qualify `ACTIVE`, audit system actor được cấu hình | Qualify Lead, chạy worker một batch | 1 outbox, 1 execution `DONE`, 1 Task `WORKFLOW` cùng tenant/key | SQL before/after, task ID, qualifying user ID và system audit row | Thắng | PASS — PostgreSQL cô lập, xem [biên bản 09/11](w08-02-qa-run-2026-11-09.md) |
| TC02 | P0 | Giao lại đúng outbox TC01 trong cùng tenant/key | Chạy worker hai lần | Tổng Task WORKFLOW theo key vẫn là 1 | Query duplicate key và count trước/sau | Thắng | PASS — replay giữ Task UUID |
| TC03 | P1 | Hai tenant, cùng suffix nghiệp vụ | Qualify một Lead mỗi tenant | Mỗi tenant có đúng 1 Task riêng | Organization ID, key, task ID | Thắng | PASS — cùng idempotency key ở hai tenant cho hai Task độc lập; [biên bản 10/11](w08-02-qa-run-2026-11-10.md) |
| TC04 | P0 | Một outbox `PENDING` hợp lệ | Khởi chạy hai worker đồng thời | Một worker claim; đúng 1 Task | Log hai worker, SQL count | Thắng | PASS — PostgreSQL cô lập, một Task |
| TC05 | P0 | Lỗi DB tạm thời, `maxAttempts` đã cấu hình | Chạy scheduler đến khi dependency phục hồi | Retry hữu hạn, cuối cùng `DONE`/`PUBLISHED`, 1 Task | Log lỗi/rerun, status mỗi lần | Thắng | PASS — scheduler tự retry sau lỗi ghi audit; gỡ constraint lỗi thì hoàn tất và chỉ có 1 Task. Chưa mô phỏng DB server/network outage hoặc backoff bền vững; xem [biên bản 10/11](w08-02-qa-run-2026-11-10.md) |
| TC06 | P0 | Owner không eligible, người qualify không còn ACTIVE/không hợp lệ, hoặc event sai tenant | Chạy worker | `FAILED`, có `last_error`, không retry vô hạn, không Task | SQL status/retry, log lỗi | Thắng | PASS — Lead đóng, owner sai và event cross-tenant bị từ chối |
| TC07 | P0 | Outbox PENDING | Dừng worker giữa transaction rồi xử lý lại | Không success giả; transaction rollback; xử lý lại an toàn, tối đa 1 Task | Transaction log, SQL before/after, trạng thái JVM worker | Thắng | PASS — kill JVM worker khi transaction đang ghi audit; PostgreSQL rollback, outbox còn `PENDING`, không có Task/audit dở dang; worker xử lý lại tạo đúng 1 Task và 1 audit. Xem [biên bản 12/11](w08-02-qa-run-2026-11-12.md) |
| TC08 | P0 | Transaction qualify Lead bị ép rollback | Gây lỗi sau publisher trước commit | Không outbox committed và không Task mới | Query Lead/outbox/task, exception log | Thắng | PASS — ép lỗi audit và xác nhận rollback |
| TC09 | P0 | Execution `DONE`, outbox giao lại | Replay event/scheduler | Giữ Task ID; không Task thứ hai | Task ID/key, worker log | Thắng | PASS — Task UUID và `created_task_id` giữ nguyên |
| TC10 | P0 | Fixture cố ý sai tenant/FK outbox, execution, Task, Lead/owner | Chạy validator/SQL trên DB test fixture | Violation > 0; worker không xử lý sai scope | Output SQL, assertion integration | Thắng | PASS — clone sạch 10/10; clone âm phát hiện `execution_outbox_fk_or_tenant_mismatch=1`, chín check còn lại 0; [biên bản 10/11](w08-02-qa-run-2026-11-10.md) |
| TC11 | P0 | Lead `NEW`; người qualify là user `ACTIVE` trong tenant và khác owner Sales | Qualify Lead rồi xử lý outbox | Task có `created_by_user_id` bằng người qualify; `owner_user_id` vẫn là Sales được giao; audit workflow vẫn ghi system actor | Row Task, payload outbox có `qualifiedByUserId`, audit row | Thắng | PASS — khớp DEC-16 D2; assertion trong `WorkflowIntegrationTest` và `CustomerLeadIntegrationTest`, chạy PostgreSQL cô lập; xem [biên bản 09/11](w08-02-qa-run-2026-11-09.md) và [biên bản 10/11](w08-02-qa-run-2026-11-10.md) |
| TC12 | P0 | Cùng tenant/key nhưng payload khác | Publish lần hai | Giữ event gốc, không tạo event thứ hai hoặc ghi đè bằng payload mới | Event gốc, số event trước/sau, payload trước/sau | Thắng | PASS — test gửi lại cùng key với owner/người qualify khác; chỉ còn event gốc, UUID và payload không đổi. Contract workflow hiện không quy định cảnh báo conflict riêng; xem [biên bản 12/11](w08-02-qa-run-2026-11-12.md) |

## Quy tắc evidence

Kết quả QA ngày 09/11/2026, 10/11/2026 và lượt hoàn tất ngày 12/11/2026 được ghi tại [biên bản ban đầu](w08-02-qa-run-2026-11-09.md), [biên bản bổ sung](w08-02-qa-run-2026-11-10.md) và [biên bản hoàn tất](w08-02-qa-run-2026-11-12.md). Trạng thái PARTIAL/BLOCKED/NOT RUN chỉ được đổi sau khi có evidence runtime tương ứng.

- Lưu database name, UTC timestamp, organization ID, outbox/execution/task UUID và key; không chụp credential hoặc payload nhạy cảm.
- TC04, TC05, TC07, TC08 và TC12 yêu cầu backend thật; validator CSV/SQL read-only không thể biến chúng thành PASS.
- Schema chưa có `next_retry_at`/lease. TC05 kiểm chứng retry hữu hạn sau lỗi xử lý tạm thời; TC07 kiểm chứng rollback/replay sau khi JVM worker chết giữa transaction. Hai ca này không tuyên bố có delayed backoff bền vững hoặc lease/stale-worker expiry.
- TC12 chỉ yêu cầu idempotency giữ event gốc theo contract hiện có; không thêm hành vi cảnh báo conflict khi contract chưa quy định.
