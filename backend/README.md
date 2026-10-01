# POSE CRM Backend

Backend là modular monolith Spring Boot, nơi thực thi business rule, RBAC, organization/team scope, transaction, audit và API.

## Công nghệ

- Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA và OpenAPI.
- PostgreSQL 16 là nguồn dữ liệu giao dịch.
- Đã có login theo organization, refresh/logout, `/auth/me`, JWT bearer, session revocation, policy RBAC và `GET/PATCH /organization`. API Team là lát triển khai tiếp theo.

## Module

- Identity & Access: user, role, permission, authentication.
- Organization: organization, team và team membership.
- Offering & Assignment: Offering, category, attribute và Sales Assignment.
- CRM: Customer, Lead, Opportunity, Interaction, Feedback và Task.
- Customer 360 & Reporting: query/projection chỉ đọc.
- Import integration, workflow command và audit.

## Chạy local

Cần Java 21, Maven 3.6.3 trở lên và PostgreSQL 16. Cấu hình kết nối bằng `POSE_DATABASE_URL`, `POSE_DATABASE_USERNAME`, `POSE_DATABASE_PASSWORD`; mặc định trỏ tới PostgreSQL local database `pose`. JWT yêu cầu `POSE_JWT_SECRET_BASE64` chứa ít nhất 32 byte ngẫu nhiên đã mã hóa Base64. Access token mặc định 15 phút (`POSE_ACCESS_TOKEN_TTL`), phiên refresh mặc định 7 ngày (`POSE_REFRESH_TOKEN_TTL`).

Trước khi chạy với dữ liệu thật, đặt **V1 đã sửa của Nhi** vào `src/main/resources/db/migration/` cùng V2 của Auth. Thứ tự Flyway: V1 identity/CRM → `V2__auth_sessions.sql`. Chưa có V1 đồng bộ trong nhánh này; app chưa thể khởi động trên database trống chỉ bằng V2. Không nạp SQL E-commerce cũ để thay V1. Nếu database đã được tạo thủ công, cần baseline Flyway đúng version trước khi chạy V2; không tự động baseline database đang có dữ liệu.

```powershell
mvn spring-boot:run
```

## Auth hiện có

| Endpoint | Request | Kết quả |
|---|---|---|
| `POST /api/v1/auth/login` | `organizationCode`, `email`, `password` | JWT, opaque refresh token, `expiresIn`, user summary |
| `POST /api/v1/auth/refresh` | `refreshToken` | Cặp token mới và quyền hiện tại |
| `POST /api/v1/auth/logout` | Bearer JWT + `refreshToken` của cùng phiên | `204`; thu hồi phiên ngay |
| `GET /api/v1/auth/me` | Bearer JWT | Hồ sơ user, organization, role, permission và team scope hiện tại |

Refresh token chỉ được lưu dưới dạng SHA-256 của 32 byte ngẫu nhiên; password dùng BCrypt. Mỗi refresh tiêu thụ token cũ, giữ nguyên hạn kết thúc phiên; access token mới không vượt hạn phiên còn lại. Dùng lại token đã tiêu thụ thu hồi cả phiên và trả `401`. Frontend phải gộp các yêu cầu refresh thành một lần đang chạy và lưu token mới trước khi gửi yêu cầu tiếp theo; không tự retry bằng token cũ. Logout thu hồi phiên hiện tại, không logout mọi thiết bị.

JWT chứa `sessionId`; mỗi request kiểm tra lại phiên, trạng thái user/organization và quyền trong DB. JWT còn hạn không giữ lại quyền đã bị thu hồi. Manager chỉ quản lý `managedTeamIds`, tách khỏi `teamIds` là membership. Response Auth dùng `Cache-Control: no-store`; audit chỉ lưu actor, hành động, session ID, organization và thời điểm.

## Organization hiện có

| Endpoint | Quyền | Kết quả |
|---|---|---|
| `GET /api/v1/organization` | User đã xác thực | Organization hiện tại: `id`, `code`, `name`, `industry`, `active` |
| `PATCH /api/v1/organization` | `ADMIN` **và** `organization.team.manage` | Cập nhật `name`/`industry`, trả organization sau cập nhật |

Không nhận tenant ID từ client. PATCH chỉ nhận `name` (chuỗi không trống, tối đa 150 ký tự) và `industry` (chuỗi không trống, tối đa 120 ký tự hoặc `null` để xóa). Chuỗi được trim; trường không gửi giữ nguyên. JSON phải là object có ít nhất một trường hợp lệ; trường ngoài whitelist, kể cả `id`, `organizationId`, `code`, `active` và `metadata`, trả `400 VALIDATION_ERROR`. Mã login và trạng thái organization không thay đổi qua API này.

PATCH khóa row trước khi merge; cập nhật và audit `ORGANIZATION_UPDATED` có actor, entity, thời điểm, `old_value`/`new_value` cùng transaction. Audit thất bại thì rollback thay đổi. Payload không làm đổi giá trị trả `200` nhưng không tạo audit cập nhật giả. Response dùng `Cache-Control: no-store`.

## Schema mục tiêu của adapter Auth/Organization

Các quyết định nghiệp vụ đã chốt được dùng làm mục tiêu triển khai theo xác nhận của Tài ngày 01/10/2026. **Tên cột/key dưới đây là mapping của adapter hiện tại**, cần đối chiếu với V1 cuối của Nhi trước khi tích hợp; chưa coi fixture test là schema production.

| Bảng | Cột/key adapter đang dùng |
|---|---|
| `organizations` | `id`, `code`, `name`, `industry`, `active`; code unique không phân biệt hoa/thường |
| `users` | `id`, `organization_id`, `full_name`, `email`, `password_hash`, `active`; email normalized, unique trong organization |
| `roles` | `organization_id`, `code`; mỗi role thuộc organization |
| `user_roles` | `user_id`, `role`; giữ tên cột `role` của DD cũ, đổi code theo RBAC đã chốt |
| `permissions` | `code` |
| `role_permissions` | `organization_id`, `role_code`, `permission_code` |
| `teams`, `team_memberships` | Theo DD, bổ sung `teams.manager_user_id`; chỉ lấy membership/team đang hiệu lực cùng organization |
| `audit_logs` | Theo DD-13: `actor_user_id`, `action`, `entity_type`, `entity_id`, `old_value`/`new_value` JSONB, `occurred_at` |

Nếu V1 cuối dùng `role_id` thay role code, chỉ chỉnh query trong `AuthAccountRepository`; không đổi contract login hay rule tenant. `V2__auth_sessions.sql` chỉ tạo hai bảng lưu phiên/token, không sửa hoặc tạo lại bảng nghiệp vụ của Nhi.

## Kiểm thử

```powershell
mvn test
```

Mặc định chạy integration tests trên H2 với fixture identity tối thiểu, có Flyway V1 test và V2 Auth. Fixture nằm riêng ở `src/test/resources/db/auth-test-baseline/`, chỉ dùng trong test. Tests kiểm tra tenant isolation, multi-role, signature/expiry, refresh rotation/replay/concurrency, logout từng phiên, khóa user/organization, quyền bị thu hồi và audit không chứa secret. Organization bổ sung kiểm tra Admin + permission, whitelist/validation, PATCH một phần, xóa industry, no-op, concurrency và rollback khi audit thất bại.

Có thể chạy cùng bộ test trên PostgreSQL **database test riêng** bằng `POSE_TEST_DATABASE_URL`, `POSE_TEST_DATABASE_USERNAME`, `POSE_TEST_DATABASE_PASSWORD`. Bộ test tạo schema fixture và xóa dữ liệu của các bảng fixture trước mỗi case, vì vậy URL này phải trỏ tới database test trống, không phải database demo hoặc production.

Xác minh ngày 01/10/2026: `mvn verify` pass 26 integration tests (18 Auth + 8 Organization) và 4 regression tests RBAC trên cả H2 2.3 và PostgreSQL 18.3 có sẵn ở máy local, đóng gói JAR thành công bằng Java 21. Đây là kiểm thử schema mục tiêu/fixture; chưa kiểm thử trên PostgreSQL 16 hoặc seed demo cuối của Nhi.

## Quy tắc triển khai

- Dùng `docs/analysis/business-rules-v1.md`, `docs/analysis/roles-authorization-v1.md`, `docs/design/domain-class-diagram-v1.md` và contract tại `docs/api/api-contract-foundation-v1.md` làm chuẩn trước khi tạo entity/endpoint.
- Scope check nằm trong service/policy backend, không dựa vào frontend.
- Mọi mutation nhạy cảm tạo audit log; workflow command phải idempotent.
- Lát Auth được triển khai theo schema mục tiêu đã chốt dù V1/seed cập nhật chưa vào repo; đối chiếu mapping với V1 cuối trước khi chạy cùng bộ dữ liệu demo.
