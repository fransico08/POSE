# POSE CRM

## Giới thiệu dự án

POSE CRM là hệ thống quản lý quan hệ khách hàng dành cho doanh nghiệp đa ngành. Hệ thống tập trung dữ liệu Customer/Lead, Offering, nhân viên Sale và hoạt động chăm sóc trên một nền tảng có kiểm soát phạm vi dữ liệu.

Hệ thống giải quyết các nhu cầu chính:

- Quản lý Offering là sản phẩm hoặc dịch vụ theo mô hình mở rộng được cho nhiều ngành.
- Manager phân công Offering cho Sale và Backend kiểm soát phạm vi dữ liệu Sale được phép thao tác.
- Quản lý Customer, Lead, Opportunity, Interaction, Feedback và CRM Task.
- Tổng hợp Customer 360 và Dashboard từ dữ liệu nghiệp vụ có thể đối soát.
- Import, chuẩn hóa dữ liệu, data quality và workflow theo rule.

Đây là sản phẩm Tiểu luận Chuyên ngành của nhóm POSE tại Trường Đại học Sư phạm Kỹ thuật TP. Hồ Chí Minh.

## Đọc tài liệu theo quy trình

Đọc theo thứ tự này trước khi thiết kế, tạo migration hoặc viết code:

1. [SRS POSE](<docs/SRS POSE.docx>) — phạm vi và yêu cầu CRM đa ngành.
2. [Business Rules v1](docs/analysis/business-rules-v1.md) — luồng nghiệp vụ và rule triển khai.
3. [RBAC Matrix v1](docs/analysis/rbac-matrix-v1.md) — role, permission và scope kiểm tra ở Backend.
4. [ERD v1](docs/design/erd-v1.md) — entity, quan hệ và ràng buộc dữ liệu.
5. [Architecture v1](docs/api/architecture-v1.md) — module, ranh giới dịch vụ và luồng tích hợp.
6. [Docs README](docs/README.md) — ownership tài liệu và các artefact sẽ bổ sung.

Không dùng artifact E-Commerce cũ làm yêu cầu triển khai POSE CRM.

## Trạng thái hiện tại

- Đã có baseline: Business Rules v1, RBAC Matrix v1, ERD v1 và Architecture v1.
- Đang chờ hoàn thiện: Data Dictionary v1, API Contract v1, checklist local environment và CI.
- Chưa có source code ứng dụng hoặc migration CRM được phê duyệt.

## Kiến trúc và công nghệ

Hệ thống dùng Modular Monolith cho nghiệp vụ giao dịch; Data Service và n8n được tách theo ranh giới trách nhiệm.

### 1. Frontend CRM

- Framework: Next.js, TypeScript.
- UI/UX: Tailwind CSS.
- Data/State: TanStack Query, Recharts.
- Trách nhiệm: giao diện theo permission, Offering, Sales Assignment, CRM, Customer 360, Dashboard và Import.
- Chi tiết: [Frontend README](frontend/README.md).

### 2. Backend API

- Framework: Java 21, Spring Boot 3, Spring Security, Spring Data JPA, OpenAPI.
- Trách nhiệm: business rule, RBAC, record scope, transaction, audit và workflow command.
- Database: PostgreSQL 16.
- Chi tiết: [Backend README](backend/README.md).

### 3. Data Service

- Framework: Python 3.12, FastAPI, Pandas.
- Trách nhiệm: import CSV/Excel, chuẩn hóa identity, data quality và dashboard/data API.
- Không ghi trực tiếp database nghiệp vụ.
- Chi tiết: [Data Service README](data-service/README.md).

### 4. Automation

- Nền tảng: n8n và SMTP.
- Trách nhiệm: điều phối notification, reminder, scheduled report và workflow retry qua Backend API.
- Chi tiết: [Automation README](automation/README.md).

### 5. Database và triển khai

- Database: PostgreSQL 16.
- Storage: MinIO cho file import và tài liệu liên quan.
- Deploy: Docker, Docker Compose, Nginx và GitHub Actions.
- Chi tiết: [Database README](database/README.md), [Deploy README](deploy/README.md).

## Cấu trúc thư mục

- `backend/`: Spring Boot modular monolith.
- `frontend/`: Next.js CRM UI.
- `data-service/`: import, data quality và data API.
- `database/`: migration, seed và tài liệu dữ liệu.
- `automation/`: workflow n8n.
- `deploy/`: Docker Compose và cấu hình triển khai.
- `docs/`: SRS, business rules, RBAC, ERD, architecture, API và test artefact.

## Phân công TLCN

| Thành viên | Vai trò | Phạm vi TLCN |
|---|---|---|
| Huỳnh Minh Tài | Fullstack Developer & Technical Lead | Kiến trúc; Auth, User, Role; Organization, Team; Customer; Customer 360; Interaction; RBAC; audit; frontend/backend và tích hợp hệ thống. |
| Nguyễn Đức Thắng | Fullstack Developer, Sales Process & QA Coordinator | Offering, Category, Attribute; Sales Assignment; Lead; Opportunity; Task; Notification; frontend/backend; điều phối integration và E2E test. |
| Vắn Phạm Thảo Nhi | Data, Analytics & Automation Owner | Source System; Import; Data Cleaning; Data Quality; Data API; dữ liệu demo đa lĩnh vực; Dashboard; n8n; workflow monitoring. |
  
**Thời gian:** Tháng 9 năm 2026
