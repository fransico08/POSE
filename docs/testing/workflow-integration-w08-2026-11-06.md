# W08 — Tích hợp workflow Lead vào backend, 06/11/2026

Phạm vi: DEC-16; checkout `D:\POSE`, nhánh Tài, điểm xuất phát `8588886`.
Không kết nối Supabase hoặc PostgreSQL 5432, không sửa seed/CSV/Data Dictionary
của Nhi, không thêm migration.

```mermaid
sequenceDiagram
    participant S as LeadService
    participant DB as PostgreSQL
    participant W as Worker (mặc định tắt)
    S->>DB: Qualify Lead + audit + outbox PENDING
    Note over S,DB: Cùng transaction Spring
    W->>DB: Claim LEAD_QUALIFIED (SKIP LOCKED)
    W->>DB: Kiểm Lead, owner và người qualify
    W->>DB: Task WORKFLOW + audit SYSTEM + execution DONE + PUBLISHED
    Note over W,DB: Cùng transaction; lỗi rollback và retry có giới hạn
```

## Môi trường và giới hạn kiểm thử

- Windows, JDK 25.0.2 (bytecode Java 21), Maven 3.9.15; PostgreSQL test
  `127.0.0.1:54329`, script xác nhận `SHOW data_directory`.
- Lệnh dọn ba thư mục `backend/target/classes`, `target/test-classes`,
  `target/surefire-reports` bị hệ thống tự động từ chối `blocked by policy`.
  Không chạy `mvn clean`, không xóa cluster; kết quả Maven dưới đây là chạy
  chẩn đoán trên build hiện có, chưa được nghiệm thu từ bản sạch.
- `mvn -pl backend -am verify` (H2); `backend/scripts/test-postgres-v2.ps1`
  với gói V2 FINAL và JDK/Maven ở trên; `scripts/e2e-env.ps1 -Build`.
- `workflow-core`: wrapper JUnit chạy sáu kịch bản gốc của Nhi với assertions.

## Kết quả và kịch bản

| Bộ kiểm thử | Tổng | Đạt | Skip | Lỗi | So với mốc |
|---|---:|---:|---:|---:|---|
| workflow-core JUnit wrapper | 1 | 1 | 0 | 0 | Sáu kịch bản gốc |
| Backend H2 | 224 | 200 | 24 | 0 | Mốc 214 tổng / 192 đạt |
| Backend PostgreSQL | 224 | 211 | 13 | 0 | Mốc 214 tổng / 201 đạt |

Số lấy từ Surefire XML. PostgreSQL script báo `Verified.` với regression DB
`pose_foundation_test_20261106102420848` và bản restore
`pose_v2_review_20261106102420848`; cổng 54329 đã dừng. Không coi kết quả là
chạy sạch do bước dọn thư mục bị chặn như ghi ở trên.

| Kịch bản | Bằng chứng |
|---|---|
| Qualify hợp lệ ghi outbox; stale version không ghi; lỗi insert rollback | `CustomerLeadIntegrationTest` |
| Worker tạo Task với owner/actor đúng, MEDIUM, audit system và PUBLISHED/DONE | `WorkflowIntegrationTest` |
| Replay và hai worker đồng thời | `WorkflowIntegrationTest` |
| Audit lỗi retry ba lần rồi FAILED, không tạo Task | `WorkflowIntegrationTest` |
| Lead đóng, owner/actor không hợp lệ, event khác tenant | `WorkflowIntegrationTest` |
| Worker mặc định không có bean scheduler/processor | `WorkflowDisabledTest` |
| Bản restore, worker tắt và bật | `RestoredV2SmokeTest`, `RestoredWorkflowSmokeTest`: outbox 19→19, execution 19→19 |
| E2E cô lập `-Build` | 24/24 đạt; 8080, 8081, 3010, 3011, 54329 đã dừng |

## Vận hành

Worker mặc định `POSE_WORKFLOW_WORKER_ENABLED=false`. Chỉ Tài quyết định bật
bằng `POSE_WORKFLOW_WORKER_ENABLED=true` sau khi review; có thể đặt
`POSE_WORKFLOW_WORKER_INTERVAL_MS`, `POSE_WORKFLOW_WORKER_BATCH_SIZE`,
`POSE_WORKFLOW_WORKER_MAX_RETRY`, `POSE_WORKFLOW_WORKER_DUE_DAYS`.
Các giá trị mặc định lần lượt là 60000 ms, 20, 3 và 1 ngày.

Chưa có giao diện theo dõi, chưa hỗ trợ trigger khác, chưa áp dụng hoặc bật
trên Supabase. Schema hiện chưa có lịch backoff bền vững hay lease; không tuyên
bố đã kiểm recovery sau crash. Thay đổi trigger/publisher và processor trong
`workflow-core` cần Nhi review.
