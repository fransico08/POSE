# W15-V1-05: Xác minh tổng hợp AI-07

## Phạm vi và nguồn

Không tìm thấy Task Note V1-05 trong `E:\POSE\Lab\Task_Notes`; report này dùng evidence V1-01 đến V1-04, `V15__customer_signals.sql`, `ai-07-signals-2026-11-13.md` và các test hiện có. Không sửa production, migration, test hiện có hay dữ liệu.

## Kết quả

| Hạng mục | Trạng thái | Chứng cứ |
| --- | --- | --- |
| V15 schema | PASS (evidence V1-03) | `customer_signal_extractions` có unique success theo `(organization_id, success_message_id)`; `customer_signals` có FK tenant composite, unique AI/HUMAN và status PROPOSED/ACCEPTED/EDITED/REJECTED. |
| Backend AI-07 | PASS có điều kiện | Evidence đã chạy: `CustomerSignalIntegrationTest` 21 và `CustomerHumanSignalIntegrationTest` 6, tổng 27 PASS với MockProvider/JDK 21. Session này không chạy lại: Maven không mở được cache `.m2` do file lock/quyền. |
| Frontend AI-07 | PASS theo evidence cũ | `ai-07-signals-2026-11-13.md`: typecheck, lint, Vitest 334/334 và E2E 33/33. Session này NOT RUN: workspace không có `frontend/node_modules`; `npm exec` bị chặn khi cố gắng tải Vitest. |
| Trích, review, audit, retry | PASS có điều kiện | Test backend cũ bao phủ extract, retry sau thành công, HUMAN, permission, tenant/scope và audit không chứa nội dung tin nhắn. Review dùng `FOR UPDATE`; chỉ một quyết định terminal và audit được thiết kế. |
| Lọc tín hiệu được phê duyệt cho workflow/KPI | NOT VERIFIED | Không có consumer workflow/KPI của `customer_signals` trong AI-07 v0. Rule ACCEPTED/EDITED/HUMAN là contract, chưa có điểm tích hợp để xác minh. |
| AI-05 second-round labeling | BLOCKED | Chưa có kết quả labeling của Thắng; không thể đánh giá macro-F1 hay chất lượng nhãn/model. |
| Đồng thời provider | FAIL mở | V1-04 xác định hai worker có thể gọi provider hai lần trước unique write. Unique constraint chỉ bảo vệ persisted signal/extraction; message claim/lease của owner backend chưa có. |

## Kết luận

Không đạt overall V1 PASS. Cần hoàn tất labeling AI-05 của Thắng, thêm message claim/lease để chặn duplicate provider invocation, và chạy lại test frontend/backend trong môi trường có dependency cache khả dụng. Không tự commit/push/merge/chuyển nhánh.
