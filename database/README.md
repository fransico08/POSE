# POSE CRM Database

PostgreSQL 16 là nguồn dữ liệu giao dịch gốc của POSE CRM.

## Nhóm dữ liệu cần có trong ERD v1

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

`001_m0_schema.sql` là schema E-Commerce cũ, không phải migration POSE CRM và không được chạy cho phạm vi hiện tại. Migration CRM bắt đầu sau khi ERD/Data Dictionary v1 được chốt.
