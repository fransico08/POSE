# W08-02 — Handoff QA cho Thắng

Kết quả lượt QA 09–10/11/2026: xem [biên bản 09/11](w08-02-qa-run-2026-11-09.md), [biên bản bổ sung 10/11](w08-02-qa-run-2026-11-10.md) và [ma trận cập nhật TC01–TC12](w08-02-retry-idempotency-data-cases.md). TC11 theo DEC-16 D2: `created_by_user_id` là người qualify Lead; người này khác owner Sales, còn audit vẫn ghi system actor. TC03, TC05 và TC10 đã PASS; TC07 có bằng chứng session interruption nhưng còn PARTIAL vì chưa kill/restart JVM worker; TC12 còn PARTIAL theo giới hạn evidence.

## Điều kiện trước

Chạy trên PostgreSQL cô lập, không phải Supabase/shared database. W08-01 phải có Spring hook publish outbox trong transaction qualify Lead, user qualify còn ACTIVE trong tổ chức, system actor cho audit workflow, scheduler/worker JDBC và policy Task đã được Tài review. Xem `docs/testing/w08-02-retry-idempotency-data-cases.md` để chạy TC01–TC12.

## Thứ tự chạy

1. Trong workspace Nhi chạy:

   ```powershell
   python database/scripts/validate_w08_02_retry_idempotency.py
   ```

   Đây chỉ là PASS offline của canonical seed và negative fixtures nội bộ.
2. Trên database cô lập sau tích hợp, ghi counts/key trước test, chạy từng TC01–TC12, sau mỗi ca chạy `database/scripts/verify_w08_02_retry_idempotency_gui.sql` trong pgAdmin 4/SQLTools. Violation phải là `0` trừ fixture cố ý sai TC10.
3. Với TC02/TC04/TC09/TC12, ghi count Task `origin='WORKFLOW'` theo `(organization_id, idempotency_key)` trước/sau. Kết quả hợp lệ luôn là một row.
4. Với TC05–TC08, lưu log transaction/worker, `retry_count`, `last_error`, trạng thái outbox/execution và Data Output trước/sau. Không khẳng định crash recovery nếu chưa quan sát.

## Evidence tối thiểu

- Database name, thời gian UTC, backend build, `maxAttempts` và cadence scheduler.
- Screenshot/CSV Data Output SQL verifier; UUID tenant, outbox, execution, Task và key.
- Log hai worker TC04; exception + rollback TC05–TC08; conflict evidence TC12.
- Với TC11, xác nhận `created_by_user_id` là user thực hiện qualify Lead; `owner_user_id` giữ Sales được giao. Audit ghi system actor riêng, không dùng owner giả danh người qualify.

## Giới hạn cần nêu

PASS offline không phải PASS integration. Schema chưa có `next_retry_at` hoặc lease/stale-worker expiry; Tài phải duyệt migration/chiến lược vận hành trước khi kết luận retry delayed/crash recovery bền vững. Không sửa seed hay tạo QA data trên shared DB.
