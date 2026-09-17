# Architecture v1

## Phạm vi và ownership

Architecture này áp dụng cho MVP POSE CRM đa ngành.

| Phạm vi | Owner |
|---|---|
| Kiến trúc, Auth, User, Role, Organization, Team, Customer, Customer 360, Interaction, RBAC, audit và tích hợp | Huỳnh Minh Tài |
| Offering, Category, Attribute, Sales Assignment, Lead, Opportunity, Task, Notification, frontend/backend sales flow và E2E | Nguyễn Đức Thắng |
| Source System, Import, Data Cleaning, Data Quality, Data API, dữ liệu demo đa lĩnh vực, Dashboard, n8n và workflow monitoring | Vân Phạm Thảo Nhi |

## 1. Kiến trúc tổng thể

POSE CRM dùng modular monolith cho nghiệp vụ giao dịch và tách Data Service/Automation theo ranh giới trách nhiệm.

```text
Next.js Frontend
      |
      v
Spring Boot Backend API
  ├─ Identity & Access
  ├─ Organization
  ├─ Offering & Sales Assignment
  ├─ Customer & Lead
  ├─ Opportunity
  ├─ Interaction & Task
  ├─ Customer 360 & Reporting
  ├─ Import Integration
  └─ Audit & Workflow Command
      |
      v
PostgreSQL 16

FastAPI Data Service <-> Backend API <-> n8n
        |                         |
        └──── validate/import ────┴──── notification/scheduled workflow
```

## 2. Thành phần và ranh giới

### Frontend

- Next.js hiển thị giao diện theo permission và scope đã trả về từ API.
- Frontend không tự quyết định quyền hoặc ghi trực tiếp vào database.
- Module giao diện bám theo domain: auth, organization, offering, assignment, customer, lead, opportunity, activity/task, dashboard và import.

### Backend API

- Spring Boot modular monolith là nơi duy nhất thực thi business rule, RBAC, scope check, transaction, audit và idempotency.
- Mỗi module có controller, service, repository và contract riêng; module chỉ gọi nhau qua service/command rõ ràng.
- API công khai dùng `/api/v1`; API internal cho import/workflow tách namespace và yêu cầu xác thực service-to-service.

### PostgreSQL

- Lưu dữ liệu nghiệp vụ, assignment, audit, outbox và metadata workflow/import.
- Customer 360 và Dashboard lấy từ query/projection đối soát được.
- Data Service và n8n không ghi trực tiếp database.

### Data Service

- FastAPI nhận CSV/Excel, chuẩn hóa email/phone, kiểm tra lỗi và duplicate nghi ngờ.
- Data Service trả kết quả validate cho Backend; Backend phê duyệt và commit dữ liệu nghiệp vụ.
- Data Service cung cấp data-quality/dashboard API khi cần, nhưng không trở thành nguồn dữ liệu giao dịch.

### Automation

- n8n chạy lịch, gửi thông báo và gọi workflow command của Backend.
- Backend tạo outbox event, kiểm tra idempotency và lưu trạng thái execution/retry.
- n8n không quyết định permission, transition nghiệp vụ hoặc owner.

## 3. Module backend và trách nhiệm

| Module | Trách nhiệm |
|---|---|
| Identity & Access | Đăng nhập, token/session, User, Role, Permission và policy check. |
| Organization | Organization, Team, team membership và Manager scope. |
| Offering & Sales Assignment | Offering/category/attribute, assignment hiệu lực và phạm vi Sale. |
| Customer & Lead | Identity chuẩn hóa, owner, source system, duplicate review và lead conversion. |
| Opportunity | Pipeline, Offering liên quan, stage transition và closing reason. |
| Interaction & Task | Interaction, Feedback, CRM Task, owner, deadline và activity history. |
| Customer 360 & Reporting | Read model/query cho Customer 360 và dashboard scope-aware. |
| Import Integration | Import Job, validate result, approval và commit command. |
| Audit & Workflow | Audit log, outbox, idempotent workflow command, retry state. |

## 4. Luồng tích hợp bắt buộc

### Sales Assignment

1. Manager tạo hoặc điều chỉnh Sales Assignment.
2. Backend kiểm tra Manager scope và Offering cùng organization.
3. Backend lưu Assignment, audit event và outbox event.
4. n8n có thể gửi notification, nhưng quyền Sale được thay đổi ngay trong Backend.

### Import

1. Data Staff upload file qua Backend/Data Service contract.
2. Data Service validate và trả lỗi/duplicate nghi ngờ.
3. Manager hoặc Admin phê duyệt import.
4. Backend ghi Customer/Lead/Source System theo transaction, audit và outbox.

### Customer 360

1. API nhận Customer ID.
2. Backend kiểm tra organization, permission, assignment và owner scope.
3. Backend truy vấn dữ liệu nghiệp vụ/projection.
4. API trả hồ sơ chỉ đọc gồm Customer, Lead/Opportunity, Interaction, Task và metrics.

## 5. Ràng buộc kỹ thuật

- API mutation chạy trong transaction phù hợp; lỗi không để lại trạng thái nửa chừng.
- Mọi list API phải có pagination và filter theo scope.
- API trả `403` khi user không đủ permission/scope; không tiết lộ record ngoài scope.
- Không log password, access token hoặc dữ liệu bí mật.
- Event retry và workflow command bắt buộc dùng idempotency key.
- API phổ biến trong môi trường demo mục tiêu phản hồi dưới 3 giây.

## 6. Thứ tự triển khai sau architecture

1. API Contract v1.
2. Database migration và seed v1 dựa trên Data Dictionary; Domain Class Diagram là đầu vào cho cấu trúc Backend.
3. Auth/RBAC/Organization/Team foundation.
4. Offering/Sales Assignment và CRM core theo module ownership.
5. Customer 360, Dashboard, Import và workflow integration.
