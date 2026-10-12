# POSE CRM Database

PostgreSQL 16 là nguồn dữ liệu giao dịch gốc của POSE CRM.

## Nhóm dữ liệu cần có trong Data Dictionary v1

- Identity: organization, team, user, role, permission, user-role.
- Offering: offering, category, attribute definition/value, sales assignment và lịch sử assignment.
- CRM: customer, lead, opportunity, interaction, feedback, CRM task và lịch sử thay đổi.
- Data integration: source system, import job, import row/error.
- Cross-cutting: audit log, outbox/workflow execution và projection Customer 360/Dashboard.

## Quy tắc dữ liệu

- Mỗi record nghiệp vụ mang organization scope.
- Customer/Lead lưu identity đã chuẩn hóa, source system và external reference nếu có.
- Customer 360/Dashboard là projection hoặc query từ dữ liệu gốc, không phải dữ liệu được người dùng sửa trực tiếp.
- Dữ liệu CRM dùng archive/inactive, không hard-delete trong MVP.

## Lưu ý về schema hiện có

`001_m0_schema.sql` là schema E-Commerce cũ, không phải migration POSE CRM và không được chạy cho phạm vi hiện tại.

## DB final v2 của Nhi và migration backend

Nguồn nghiệm thu 12/10/2026: gói `POSE_CRM_DATA_V2_FINAL_SUBMIT.zip`, thư mục `POSE_CRM_DATA_V2_FINAL`. Backup tương đối `release/POSE_CRM_DB_v2_FINAL.backup`, pg_dump 17.10, SHA-256 `37994087124A2E38707B3C29853D58B9C0A0BCED959F7DC1875FD921340E48A6`, khớp release/BACKUP_SHA256.txt. Giữ nguyên gói; không commit backup/ZIP/CSV/Data Dictionary.

Schema final khớp schema-only của [V1 backend](../backend/src/main/resources/db/baseline/V1__nhi_v2_schema.sql) sau bỏ dump/session directives và hai dòng mô tả snapshot; không thay V1. Baseline tương đương `database/migrations/V1__pose_crm_schema.sql` trong gói Nhi. Kết quả dựng migration/seed và restore đã được đối chiếu trước task: 35/36 bảng giống nhau; organizations.metadata.dataset chỉ khác nhãn (backup multisector_v2_sync / CSV multisector_v2), không ảnh hưởng foundation. Không dựng lại hoặc sửa dữ liệu để đổi nhãn trong task.

Final có 6 organization, 36 user (35 ACTIVE + 1 INACTIVE), 5 role/organization, 28 permission; Offering có DRAFT/INACTIVE và CARE Handover có PENDING/REJECTED.

- V1: schema nghiệp vụ, không seed/password hash. V2: session/refresh token. V3: code normalized/unique. V4: đổi code tự sinh của 6 UUID demo sang edu/rea/tech/ret/log/cli tương ứng metadata.loginCode; không thay ID/name, mã riêng hoặc organization ngoài danh sách.
- Verifier chính: `verify_v2.sql` của gói final, wrapper gọi database/scripts/verify_seed_v2.psql; chạy từ gốc gói. Kết quả `TASK 5 PASS`.
- [verify-foundation-v2.sql](verify-foundation-v2.sql): assertions credential/email, tenant grant/member/manager/department, Manager hiệu lực, grant/member trùng và quyền role Admin.
- [verify_v2.sql](verify_v2.sql) trong repo là bản 01/10 **SUPERSEDED**, chỉ giữ liên kết tài liệu lịch sử; runner không dùng bản này.

Database trống: Flyway V1 → V2 → V3 → V4. Restore đúng backup final: baseline **version 1**, rồi V2/V3/V4. “v2” trong tên gói là revision dữ liệu, không phải Flyway version. Không sửa checksum V1–V3 hoặc tự baseline schema khác nguồn.

Runner [test-postgres-v2.ps1](../backend/scripts/test-postgres-v2.ps1) nhận DataPackagePath và ExpectedSha256; dùng cluster <worktree>/backend/target trên 127.0.0.1:54329, kiểm SHOW data_directory trước thao tác, không dùng 5432. Mật khẩu nghiệm thu cấp qua POSE_DEMO_PASSWORD; không ghi giá trị vào Git/evidence.

Kết quả tại [evidence 12/10](../docs/testing/auth-foundation-acceptance-2026-10-12.md). 36 user/credentials gốc được xác nhận không đổi. Hướng dẫn ở [Backend README](../backend/README.md).
