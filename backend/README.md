# POSE CRM Backend

Backend là modular monolith Spring Boot, nơi thực thi business rule, RBAC, organization/team scope, transaction, audit và API.

## Công nghệ

- Java 21, Spring Boot 3.5.16, Spring Security, JDBC/JPA và Flyway.
- PostgreSQL 16 là mục tiêu kiến trúc; đã kiểm thử thực tế trên PostgreSQL 18.3, backup nguồn dùng 17.10. Chưa chứng nhận trên 16.
- OpenAPI/Swagger còn là công nghệ dự kiến; contract hiện dùng [API Contract Foundation v1](../docs/api/api-contract-foundation-v1.md).
- Ngày 05/10/2026, nhánh `Tài` có đủ 20 API foundation và mapping DB v2 của Nhi; phần implementation sẵn sàng review nhóm.

## Module

- Identity & Access: Auth, User, Role, Permission.
- Organization: Organization, Team, membership có lịch sử.
- Các module Offering/Assignment, CRM, Customer 360, Reporting, Import/Workflow được triển khai ở các card sau theo owner.

## Chạy local

Cần Java 21, Maven và PostgreSQL. Chạy lệnh từ thư mục `backend` của **checkout nhánh Tài**. Cấu hình `POSE_DATABASE_URL`, `POSE_DATABASE_USERNAME`, `POSE_DATABASE_PASSWORD`; mặc định URL trỏ tới database `pose` ở localhost:5432. `POSE_JWT_SECRET_BASE64` bắt buộc là Base64 của ít nhất 32 byte ngẫu nhiên, không dùng secret fixture test để chạy demo/production. Java 17 có thể đứng trước PATH; đặt JAVA_HOME tới Java 21 trước khi chạy.

```powershell
mvn spring-boot:run
```

Database trống chạy Flyway V1 → V2 → V3. V1 ở `src/main/resources/db/baseline/` là schema-only trích từ backup v2 (không có seed/password). V2 giữ nguyên migration session/token đã có; V3 thêm organization login code. Schema/constraint/view đã được đối chiếu bằng restore và test trên baseline thật.

Nếu dùng **bản restore đã đối chiếu đúng backup Nhi v2**, baseline Flyway **version 1** rồi chạy V2/V3. Có thể bật `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true`, `SPRING_FLYWAY_BASELINE_VERSION=1` chỉ trong lần onboarding bản restore đã kiểm tra; bỏ hai biến sau đó. App không tự bật baseline mặc định. Không dùng cách này cho schema cũ/không rõ nguồn hoặc DB fixture Auth cũ.

Backup v2 chưa có code; V3 sinh mã ổn định `org-<UUID bỏ dấu gạch>`, normalized và unique. Đọc mã thật bằng:

```sql
SELECT code, name FROM organizations ORDER BY name;
```

Mật khẩu của 36 user demo vẫn do Nhi cung cấp, không được reset trong lần triển khai này. Hai smoke tests xác minh mapping của toàn bộ account, rồi dùng tài khoản test riêng trong transaction rollback để kiểm tra login/API; không tuyên bố đã login từng user demo bằng mật khẩu gốc.

## Auth hiện có

| Endpoint | Request | Kết quả |
|---|---|---|
| POST /api/v1/auth/login | organizationCode, email, password | JWT, refresh token, expiresIn, user summary |
| POST /api/v1/auth/refresh | refreshToken | Token xoay vòng và quyền hiện tại |
| POST /api/v1/auth/logout | Bearer + refreshToken cùng phiên | 204; phiên bị thu hồi ngay |
| GET /api/v1/auth/me | Bearer | Hồ sơ, tenant, roles, permissions, team scope |

Password BCrypt; refresh token ngẫu nhiên chỉ lưu SHA-256. JWT mặc định tối đa 15 phút, refresh session 7 ngày tuyệt đối; refresh không kéo dài phiên. Replay thu hồi cả phiên. Mỗi request đọc lại trạng thái/role/permission/membership trong DB, không giữ quyền cũ trong JWT. Manager management scope tách khỏi membership. Auth response no-store; audit không chứa password/token/hash.

## Organization và Team hiện có

| API | Quyền và hành vi |
|---|---|
| GET/PATCH /organization | GET tenant hiện tại; PATCH Admin + organization.team.manage, chỉ name/industry |
| GET /teams, GET /teams/{id} | Admin tenant; Manager team quản lý; user khác team membership hiệu lực |
| POST /teams | Admin hoặc Manager + organization.team.manage; Manager chỉ tạo team do mình quản lý |
| PATCH /teams/{id} | team scope + quyền quản lý; whitelist name/description/status |
| PUT /teams/{id}/members | Thay toàn bộ danh sách; giữ lịch sử ENDED/left_at, hủy membership tương lai bị bỏ |

Team create yêu cầu departmentId (Department ACTIVE cùng tenant), managerUserId (user ACTIVE + role MANAGER hiệu lực). Code uppercase/unique không phân biệt hoa thường, tối đa 60; tên tối đa 160, description tối đa 2000 hoặc null. Manager không thêm user ngoài team scope để mở rộng quyền đọc. Team INACTIVE mất Manager scope ngay; Admin có thể activate nếu Manager còn hợp lệ. Department CRUD/đổi Manager không thuộc foundation v1.

Organization PATCH giữ giới hạn name 150/industry 120, không đổi code/status/tenant. Administration khóa organization trước khi đọc lại quyền, merge và audit để giữ đúng quyền khi mutation đồng thời.

## User, Role và Permission hiện có

| API | Quyền và hành vi |
|---|---|
| GET /users, GET /users/{id} | identity.user.read + tenant/team scope; GET self không cần user.read |
| POST /users | Admin + user.manage + role.manage; băm password, tạo roles/team cùng transaction |
| PATCH /users/{id} | Admin + user.manage; fullName/status/metadata an toàn |
| PUT /users/{id}/roles | Admin + role.manage; không đổi role bản thân, giữ lịch sử grant |
| GET /roles | Authenticated; role code/displayName trong tenant, không kèm permission |
| GET /permissions, GET/PUT /roles/{code}/permissions | Admin + role.manage; sửa role trong tenant, không sửa role actor đang giữ |

POST User trả 201; các GET/PATCH/PUT trả 200; mọi response no-store. User inactive/archive thu hồi session; activate không khôi phục token cũ. Không disable/mất role của Manager đang quản lý Team ACTIVE. Password ban đầu tối thiểu 12 ký tự/tối đa 72 byte UTF-8, giữ nguyên khoảng trắng. Metadata tối đa 4096 ký tự/5 cấp, không nhận key credential/password/token/secret. User email normalized/unique trong tenant.

List User/Team trả items/page/size/totalItems/totalPages, size 1–100, page 0–1000000; User hỗ trợ search tên/email. Lọc scope trước đếm/phân trang. User/Team ID không trong scope hoặc không tồn tại trả 403 FORBIDDEN_SCOPE cùng shape. Validation 400, duplicate/state/audit constraint conflict 409. Mutation/audit cùng transaction; audit lỗi rollback, no-op không tạo audit giả.

## Schema adapter v2

| Nhóm | Mapping đang dùng |
|---|---|
| organizations/users/teams | status ACTIVE/INACTIVE (User thêm ARCHIVED), timestamp/metadata theo DB v2 |
| credentials | password_hash tách khỏi users |
| roles + user_role_grants | role_id, organization_id, granted_at/revoked_at; không dùng user_roles cũ |
| role_permissions | role_id/permission_id, join catalog permissions |
| team_memberships | organization_id/team_id/user_id/status/joined_at/left_at |
| audit_logs | organization_id/actor/entity, before_data/after_data/safe_metadata/result/occurred_at |
| auth_sessions/auth_refresh_tokens | V2 bổ sung; session liên kết user/organization, token chỉ lưu hash |

Nguồn và checksum ở [Database README](../database/README.md). Thư mục H2 fixture chỉ là subset cho regression, không dùng để tạo production.

## Kiểm thử

```powershell
mvn verify
```

Mặc định: 52 test trên H2 đạt; lớp smoke restore được skip khi không có biến môi trường. Fixture bị xóa/tạo dữ liệu lại trước từng case. Test PostgreSQL chỉ chấp nhận URL loopback:54329 với database tên `pose_foundation_test_...`; không chạy trên database demo.

Để chạy lại 54 test trên PostgreSQL (52 regression với V1 production + 2 smoke trên restore riêng), dùng script:

```powershell
.\scripts\test-postgres-v2.ps1 -BackupPath 'D:\POSE\POSE_CRM_DB_v2_2026-10-01.backup'
```

Script kiểm tra checksum backup, chỉ dùng cluster test `D:\POSE\backend\target\auth-postgres-test`, tạo hai DB mới có tên riêng, chạy script của Nhi/assertions, migrate/test/build, kiểm tra còn đúng 6 organization/36 user rồi dừng cluster do nó khởi động. DB test được giữ để truy vết, không xóa backup hoặc reset mật khẩu. Script có tham số đường dẫn PostgreSQL/JDK/Maven để chạy trên máy khác; cluster test cần được chuẩn bị trước, không tự dùng service PostgreSQL chính.

Xác minh 05/10/2026: 54/54 trên PostgreSQL 18.3, 52/52 H2 (smoke skip), đóng gói JAR Java 21 thành công. Coverage: tenant/team/user scope, multi-role, quyền hiện tại, refresh replay/concurrency/expiry, session revocation, pagination/whitelist/validation, lịch sử grant/member tương lai, mutation đồng thời, audit rollback và full workflow trên restored demo.

## Quy tắc triển khai

Dùng Business Rules, Roles and Authorization, Domain Class Diagram và API Contract hiện hành. Backend giữ authorization/transaction/audit. Card nền tảng đã có implementation và evidence để Review & Test; chưa thay việc review của nhóm và chưa triển khai frontend/card CRM khác.
