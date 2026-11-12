# W08-02 — Kết quả QA retry, idempotency và evidence workflow

Phần QA của backlog W08 đã hoàn tất trên PostgreSQL cô lập. Xem [biên bản QA 09/11](w08-02-qa-run-2026-11-09.md), [biên bản QA 10/11](w08-02-qa-run-2026-11-10.md), [biên bản hoàn tất 12/11](w08-02-qa-run-2026-11-12.md) và [ma trận TC01–TC12](w08-02-retry-idempotency-data-cases.md).

## Kết quả đã xác nhận

- Backend `mvn verify` qua script guard của repo: 290 test, 0 failure, 0 error, 13 skip. 13 ca demo acceptance bị skip vì process không có `POSE_DEMO_PASSWORD`.
- Các suite trọng tâm W08 đạt 41/41: `WorkflowIntegrationTest` 11/11, `WorkflowSchedulerIntegrationTest` 1/1, `CustomerLeadIntegrationTest` 27/27, `WorkflowDisabledTest` 1/1 và `RestoredWorkflowSmokeTest` 1/1. `WorkflowWorkerTest` preflight cũng báo PASS.
- TC07 đã kill JVM worker con khi transaction đang ghi audit. Task chưa commit nên không nhìn thấy; sau khi connection PostgreSQL kết thúc, outbox vẫn `PENDING`, transaction rollback; lần xử lý tiếp theo hoàn tất với đúng một Task và một audit.
- TC12 gửi lại cùng tenant/idempotency key nhưng đổi owner/người qualify trong payload. Database chỉ giữ event ban đầu với cùng UUID và payload, không sinh bản ghi thứ hai.
- TC11 theo DEC-16 D2: `created_by_user_id` là người qualify Lead, `owner_user_id` vẫn là Sale được giao; audit workflow dùng system actor.
- TC03, TC05, TC10 và các ca còn lại giữ evidence ở ma trận và hai biên bản trước.

## Môi trường kiểm chứng

- JDK 22.0.2, Maven 3.9.15, PostgreSQL 18.6.
- Script: `backend/scripts/test-postgres-v2.ps1`; `SHOW data_directory` xác nhận cluster `C:\Users\WIN 11\Downloads\POSE\backend\target\auth-postgres-test` tại `127.0.0.1:54329`.
- Database mới trong lượt này: `pose_foundation_test_20261112003703153` và `pose_v2_review_20261112003703153`.
- Script kết thúc thành công (`Verified`), PostgreSQL đã dừng. Không dùng Supabase hoặc cổng 5432. Clone kiểm thử được giữ lại để đối chiếu.

## Phạm vi và giới hạn

- W08 là backlog kiểm thử backend workflow; không cần kiểm thử UI cho các ca retry/outbox này. Không chạy UI/E2E trong lượt này.
- Test chứng minh rollback/replay sau lỗi tạm thời và JVM worker chết giữa transaction. Schema hiện chưa có `next_retry_at`, lease hoặc stale-worker expiry; không kết luận có delayed backoff bền vững hay xử lý outage database kéo dài.
- Contract hiện có không yêu cầu cảnh báo/log conflict riêng cho cùng idempotency key nhưng payload khác. W08 xác nhận hành vi chống trùng và giữ nguyên event gốc; không thay đổi production code để thêm cảnh báo chưa được đặc tả.
- Trello không được cập nhật tự động trong phiên này; người phụ trách có thể tick mục QA sau khi đối chiếu biên bản và ma trận.
