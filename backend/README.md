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

- Đọc `docs/analysis/business-rules-v1.md`, `docs/analysis/roles-authorization-v1.md` và `docs/design/domain-class-diagram-v1.md` trước khi tạo entity hoặc endpoint. Chỉ tạo migration sau khi Data Dictionary v1 được chốt.
- Scope check nằm trong service/policy backend, không dựa vào frontend.
- Mọi mutation nhạy cảm tạo audit log; workflow command phải idempotent.
- Chưa tạo backend module trước khi Domain Class Diagram và API Contract liên quan hoàn thành; chưa tạo migration trước khi Data Dictionary v1 được chốt.
