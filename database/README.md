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

## DB v2 của Nhi và migration backend

Nguồn kiểm tra ngày 05/10/2026: `POSE_CRM_DB_v2_2026-10-01.backup`, archive PostgreSQL 17.10; SHA-256 `86F8E8EE84616B5EB08FC110DD16305624E857EF0C2BFCCC8B529EE1564F34B0`. Restore có 6 organization, 36 user, 5 role/organization và 28 permission. Giữ backup/mật khẩu băm tại vị trí nhóm cung cấp; schema migration không chứa seed hoặc password hash.

- [V1 schema](../backend/src/main/resources/db/baseline/V1__nhi_v2_schema.sql): trích schema-only, giữ bảng, constraint, index, view; bỏ owner/ACL/session directives.
- V2: Auth session/refresh token. V3: login code normalized/unique, mã mặc định `org-<UUID bỏ dấu gạch>`.
- [verify_v2.sql](verify_v2.sql): nguyên script của Nhi, hiển thị kết quả mong đợi nhưng không tự fail khi count khác; còn thiếu kiểm tra riêng L07/D04.
- [verify-foundation-v2.sql](verify-foundation-v2.sql): assertions bổ sung về credential/email, tenant của grant/member/manager/department, Manager hiệu lực, grant/member trùng và quyền role Admin; dùng psql với ON_ERROR_STOP=1 để fail khi vi phạm.

Database trống: Flyway V1 → V2 → V3. Database **đã restore đúng backup v2**: baseline version **1** sau đối chiếu schema, rồi chạy V2/V3. “v2” trong tên backup là revision dữ liệu Nhi, không phải Flyway version 2. Không bật baseline tự động mặc định cho DB có sẵn hoặc dùng fixture H2 làm production. Hướng dẫn ở [Backend README](../backend/README.md).
