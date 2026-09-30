# POSE CRM Backend

Backend là modular monolith Spring Boot, nơi thực thi business rule, RBAC, organization/team scope, transaction, audit và API.

## Công nghệ

- Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA và OpenAPI.
- PostgreSQL 16 là nguồn dữ liệu giao dịch.
- Module Maven hiện được khởi tạo tại thư mục này; nghiệp vụ và migration sẽ được thêm theo từng lát triển khai.

## Module

- Identity & Access: user, role, permission, authentication.
- Organization: organization, team và team membership.
- Offering & Assignment: Offering, category, attribute và Sales Assignment.
- CRM: Customer, Lead, Opportunity, Interaction, Feedback và Task.
- Customer 360 & Reporting: query/projection chỉ đọc.
- Import integration, workflow command và audit.

## Chạy local

Cần Java 21, Maven 3.6.3 trở lên và PostgreSQL 16. Cấu hình kết nối bằng `POSE_DATABASE_URL`, `POSE_DATABASE_USERNAME`, `POSE_DATABASE_PASSWORD`; mặc định trỏ tới PostgreSQL local database `pose`.

```powershell
mvn spring-boot:run
```

## Quy tắc triển khai

- Dùng `docs/analysis/business-rules-v1.md`, `docs/analysis/roles-authorization-v1.md`, `docs/design/domain-class-diagram-v1.md` và contract tại `docs/api/api-contract-foundation-v1.md` làm chuẩn trước khi tạo entity/endpoint.
- Scope check nằm trong service/policy backend, không dựa vào frontend.
- Mọi mutation nhạy cảm tạo audit log; workflow command phải idempotent.
- Chưa tạo migration cho tới khi Data Dictionary và schema/seed được đồng bộ với các quyết định RBAC, organization và team hiện hành.
