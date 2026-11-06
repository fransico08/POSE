# W08-02 — Ma trận retry, idempotency và evidence workflow

Mọi ca chạy trên PostgreSQL cô lập sau khi W08-01 được Tài tích hợp. `NOT RUN / PENDING_QA` là trạng thái thực tế mặc định; seed CSV hoặc unit test không phải evidence runtime.

| ID | Priority | Input / precondition | Bước QA | Kết quả mong đợi | Evidence cần thu thập | Owner | Actual status |
|---|---|---|---|---|---|---|---|
| TC01 | P0 | Lead `NEW`, owner SALES active, automation actor đã duyệt | Qualify Lead, chạy worker một batch | 1 outbox, 1 execution `DONE`, 1 Task `WORKFLOW` cùng tenant/key | SQL before/after, task ID, log worker | Thắng | NOT RUN / PENDING_QA |
| TC02 | P0 | Giao lại đúng outbox TC01 trong cùng tenant/key | Chạy worker hai lần | Tổng Task WORKFLOW theo key vẫn là 1 | Query duplicate key và count trước/sau | Thắng | NOT RUN / PENDING_QA |
| TC03 | P1 | Hai tenant, cùng suffix nghiệp vụ | Qualify một Lead mỗi tenant | Mỗi tenant có đúng 1 Task riêng | Organization ID, key, task ID | Thắng | NOT RUN / PENDING_QA |
| TC04 | P0 | Một outbox `PENDING` hợp lệ | Khởi chạy hai worker đồng thời | Một worker claim; đúng 1 Task | Log hai worker, SQL count | Thắng | NOT RUN / PENDING_QA |
| TC05 | P0 | Lỗi DB tạm thời, `maxAttempts` đã cấu hình | Chạy scheduler đến khi dependency phục hồi | Retry hữu hạn, cuối cùng `DONE`/`PUBLISHED`, 1 Task | Log lỗi/rerun, status mỗi lần | Thắng | NOT RUN / PENDING_QA |
| TC06 | P0 | Owner không eligible hoặc automation actor thiếu | Chạy worker | `FAILED`, có `last_error`, không retry vô hạn, không Task | SQL status/retry, log lỗi | Thắng | NOT RUN / PENDING_QA |
| TC07 | P0 | Outbox PENDING | Dừng worker giữa transaction rồi restart | Không success giả; rerun an toàn, tối đa 1 Task | Transaction log, SQL before/after | Thắng | NOT RUN / PENDING_QA |
| TC08 | P0 | Transaction qualify Lead bị ép rollback | Gây lỗi sau publisher trước commit | Không outbox committed và không Task mới | Query Lead/outbox/task, exception log | Thắng | NOT RUN / PENDING_QA |
| TC09 | P0 | Execution `DONE`, outbox giao lại | Replay event/scheduler | Giữ Task ID; không Task thứ hai | Task ID/key, worker log | Thắng | NOT RUN / PENDING_QA |
| TC10 | P0 | Fixture cố ý sai tenant/FK outbox, execution, Task, Lead/owner | Chạy validator/SQL trên DB test fixture | Violation > 0; worker không xử lý sai scope | Output SQL, assertion integration | Thắng | NOT RUN / PENDING_QA |
| TC11 | P0 | Automation actor tenant-scoped, khác owner | Qualify rồi xử lý Lead | `created_by_user_id` là actor đã duyệt | Row Task, actor configuration | Thắng | NOT RUN / PENDING_QA |
| TC12 | P0 | Cùng tenant/key nhưng payload khác | Publish lần hai | Báo/giữ event gốc, không xử lý payload sai | Event gốc, conflict/log, task count | Thắng | NOT RUN / PENDING_QA |

## Quy tắc evidence

- Lưu database name, UTC timestamp, organization ID, outbox/execution/task UUID và key; không chụp credential hoặc payload nhạy cảm.
- TC04, TC05, TC07, TC08 và TC12 yêu cầu backend thật; validator CSV/SQL read-only không thể biến chúng thành PASS.
- Schema chưa có `next_retry_at`/lease. Với TC05/TC07, ghi scheduler cadence và limitation recovery để Tài quyết định migration.
