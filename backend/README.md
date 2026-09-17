# POSE CRM Backend

Backend là modular monolith Spring Boot chịu trách nhiệm cho business rule, RBAC, data scope, transaction, audit và API.

## Công nghệ

- Java 21, Spring Boot 3, Spring Security, Spring Data JPA và OpenAPI.
- PostgreSQL 16 là nguồn dữ liệu giao dịch.

## Module dự kiến

- Identity & Access: user, role, permission, authentication.
- Organization: organization và team.
- Offering & Assignment: Offering, category, attribute và Sales Assignment.
- CRM: Customer, Lead, Opportunity, Interaction, Feedback và Task.
- Customer 360 & Reporting: query/projection chỉ đọc.
- Import integration, workflow command và audit.

## Quy tắc triển khai

- Đọc `docs/analysis/business-rules-v1.md` và `docs/analysis/roles-authorization-v1.md` trước khi tạo entity, migration hoặc endpoint.
- Scope check nằm trong service/policy backend, không dựa vào frontend.
- Mọi mutation nhạy cảm tạo audit log; workflow command phải idempotent.
- Chưa tạo source code, build file hoặc migration trước khi ERD và API Contract v1 hoàn thành.
