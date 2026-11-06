# W08-02 — Handoff QA cho Thắng

## Điều kiện trước

Chạy trên PostgreSQL cô lập, không phải Supabase/shared database. W08-01 phải có Spring hook publish outbox trong transaction qualify Lead, automation actor hợp lệ, scheduler/worker JDBC và policy Task đã được Tài review. Xem `docs/testing/w08-02-retry-idempotency-data-cases.md` để chạy TC01–TC12.

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
- Xác nhận `created_by_user_id` là automation actor đã duyệt cho TC11, không dùng owner giả danh.

## Giới hạn cần nêu

PASS offline không phải PASS integration. Schema chưa có `next_retry_at` hoặc lease/stale-worker expiry; Tài phải duyệt migration/chiến lược vận hành trước khi kết luận retry delayed/crash recovery bền vững. Không sửa seed hay tạo QA data trên shared DB.
