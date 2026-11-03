# API Contract Foundation v1

## Phạm vi

Contract này bao phủ nền tảng do Huỳnh Minh Tài sở hữu: Authentication, User, Role, Permission, Organization và Team. Các endpoint Offering, Sales Assignment, CRM, Import, Dashboard và Automation được bổ sung trong contract theo module owner tương ứng.

Cập nhật 12/10/2026 trên nhánh `Tài`: đã nghiệm thu 20 API nền tảng với gói final và tài khoản demo thật, gồm role/permission/team scope và HTTP app. Hướng dẫn ở [Backend README](../../backend/README.md).

## 1. Quy ước chung

- Base URL: `/api/v1`.
- Payload dùng JSON `camelCase`; ID dùng UUID; thời gian dùng ISO-8601 UTC.
- Endpoint đã xác thực yêu cầu `Authorization: Bearer <accessToken>`.
- User luôn hoạt động trong một organization; backend tự xác định organization từ principal/token, không nhận `organizationId` từ client cho request nghiệp vụ thông thường.
- List API dùng `page` từ `0`, `size` mặc định `20`, tối đa `100`, và trả `items`, `page`, `size`, `totalItems`, `totalPages`.
- Backend kiểm tra permission, team scope và record scope; frontend không thay thế lớp kiểm tra này.
- Pagination áp dụng cho `/users` và `/teams`, page tối đa 1000000. `/users` có `search` theo tên/email, luôn lọc scope trước khi đếm và phân trang. Role/permission catalog trả mảng cấu hình nhỏ.

### Error shape

```json
{
  "code": "FORBIDDEN_SCOPE",
  "message": "Bạn không có quyền thao tác dữ liệu ngoài phạm vi được giao.",
  "fieldErrors": [
    { "field": "email", "message": "Email không hợp lệ." }
  ],
  "traceId": "01J..."
}
```

Các mã lỗi dùng trong foundation: `AUTH_INVALID_CREDENTIALS`, `AUTH_INVALID_TOKEN`, `AUTH_INVALID_REFRESH_TOKEN`, `AUTH_TOKEN_EXPIRED`, `FORBIDDEN_PERMISSION`, `FORBIDDEN_SCOPE`, `FORBIDDEN_SELF_PRIVILEGE_ESCALATION`, `RESOURCE_NOT_FOUND`, `VALIDATION_ERROR`, `CONFLICT`, `USER_EMAIL_EXISTS`, `TEAM_CODE_EXISTS`, `TEAM_INACTIVE`, `TEAM_MANAGER_INACTIVE`, `ACTIVE_TEAM_MANAGER_REQUIRED`, `LAST_ACTIVE_ADMIN_REQUIRED`.

CRM có thể thêm object `details` tùy chọn trong lỗi trùng dữ liệu. `details.duplicateId` chỉ xuất hiện khi actor được phép đọc bản ghi trùng; mọi lỗi khác giữ nguyên shape trên.

## 2. Authentication

| Method | Path | Role | Mục đích |
|---|---|---|---|
| `POST` | `/auth/login` | Public | Đăng nhập bằng organization code, email và password. |
| `POST` | `/auth/refresh` | Public với refresh token hợp lệ | Lấy access token mới. |
| `POST` | `/auth/logout` | Authenticated | Thu hồi refresh token hiện tại. |
| `GET` | `/auth/me` | Authenticated | Lấy user, role, permission và team scope hiện tại. |

### `POST /auth/login`

```json
{
  "organizationCode": "EDU",
  "email": "edu.admin@example.com",
  "password": "<from-POSE_DEMO_PASSWORD>"
}
```

`organizationCode` là mã đăng nhập ổn định của organization, duy nhất và không phân biệt chữ hoa/thường. Trường này bắt buộc vì email chỉ unique bên trong từng organization; không tra cứu user toàn hệ thống chỉ bằng email. Backend tìm organization đang hoạt động theo code trước, sau đó xác thực cặp `(organizationId, normalizedEmail)`. Code được tạo cùng organization và không đổi trong v1. Không nhận `organizationId` từ client ở các request nghiệp vụ sau đăng nhập.

Gói final công bố metadata.loginCode EDU/REA/TECH/RET/LOG/CLI. V4 ánh xạ 6 UUID demo sang code tương ứng (lưu lowercase), kế thừa constraint V3. Gõ EDU hoặc edu đều hợp lệ. Organization ngoài danh sách giữ code V3; mã riêng đã có không bị ghi đè. Lấy mã thực tế bằng `SELECT code, name FROM organizations ORDER BY name`. Shape request/response không thay đổi.

```json
{
  "accessToken": "<jwt>",
  "refreshToken": "<opaque-token>",
  "expiresIn": 900,
  "user": {
    "id": "uuid",
    "email": "edu.admin@example.com",
    "fullName": "EDU Admin",
    "status": "ACTIVE",
    "organization": { "id": "uuid", "name": "POSE Academy" },
    "roles": ["ADMIN"],
    "permissions": ["identity.user.manage", "audit.read"],
    "teamIds": ["uuid"]
  }
}
```

Login thất bại luôn trả `401 AUTH_INVALID_CREDENTIALS`; không tiết lộ organization, email hay user có tồn tại hay không. Password chỉ được truyền qua TLS và không log.

`roles` có thể chứa nhiều role code. `permissions` là hợp permission hiệu lực của các role đó; Backend vẫn kiểm tra organization, team scope và record scope cho từng request.

### Refresh, logout và phiên đăng nhập

`POST /auth/refresh` nhận `{ "refreshToken": "<opaque-token>" }` và trả cùng response shape như login. Access token mặc định tối đa 15 phút, không vượt hạn phiên còn lại; phiên refresh mặc định 7 ngày tính từ login, không gia hạn mỗi lần refresh. Refresh token sinh từ 32 byte ngẫu nhiên, chỉ lưu SHA-256 trong database, dùng một lần và được thay bằng token mới sau mỗi refresh.

Refresh đã hết hạn, bị thu hồi hoặc không tồn tại trả `401 AUTH_INVALID_REFRESH_TOKEN`. Dùng lại token đã tiêu thụ trả cùng lỗi và thu hồi toàn bộ phiên. Các yêu cầu refresh đồng thời được serialize bằng khóa phiên; client phải chỉ có một request refresh đang chạy và không retry token cũ. Header Bearer hết hạn không ngăn việc refresh bằng refresh token hợp lệ.

`POST /auth/logout` yêu cầu Bearer access token còn hiệu lực và body `{ "refreshToken": "<opaque-token>" }` thuộc cùng phiên/user/organization. Thành công trả `204`, thu hồi toàn bộ phiên hiện tại và làm access JWT của phiên đó mất hiệu lực ngay. Token của phiên/organization khác không được dùng để logout, trả `401 AUTH_INVALID_REFRESH_TOKEN`. Các phiên đăng nhập khác vẫn hoạt động.

`GET /auth/me` trả trực tiếp object `user` trong response login, không trả credential/token. JWT sai chữ ký, thiếu claim, phiên bị thu hồi, user/organization bị khóa trả `401 AUTH_INVALID_TOKEN`; JWT hết hạn trả `401 AUTH_TOKEN_EXPIRED`. Mỗi request bearer lấy quyền hiện tại từ DB, không duy trì quyền cũ trong JWT sau khi role/permission bị thu hồi. `teamIds` trong user summary là hợp membership/team quản lý đang hiệu lực; policy Manager dùng tập team được giao quản lý riêng.

Auth responses không được cache (`Cache-Control: no-store`). Login thành công, refresh, logout và phát hiện replay tạo audit log; audit không lưu password, access/refresh token hoặc token hash.

## 3. User, Role và Permission

### User

| Method | Path | Permission | Mục đích |
|---|---|---|---|
| `GET` | `/users` | `identity.user.read` | Danh sách user trong organization; Manager chỉ thấy team scope. |
| `POST` | `/users` | `ADMIN` + `identity.user.manage` + `identity.role.manage` | Tạo user mới trong organization. |
| `GET` | `/users/{userId}` | `identity.user.read` hoặc self | Chi tiết user trong scope. |
| `PATCH` | `/users/{userId}` | `ADMIN` + `identity.user.manage` | Sửa full name, status và metadata an toàn. |
| `PUT` | `/users/{userId}/roles` | `identity.role.manage` + `ADMIN` | Thay role của user khác trong organization. |

### `POST /users`

```json
{
  "email": "sale.a@pose.local",
  "fullName": "Sale A",
  "initialPassword": "temporary-password",
  "roleCodes": ["SALES_REP"],
  "teamIds": ["uuid"]
}
```

`roleCodes` nhận một hoặc nhiều role code hợp lệ. Backend chuẩn hóa email, kiểm tra unique trong organization, băm password và ghi audit event `USER_CREATED`. `initialPassword` không xuất hiện trong response hoặc audit log.

POST trả `201` với `{id, email, fullName, status, metadata, roles, permissions, teamIds}`; GET/PATCH/PUT role trả cùng shape với `200`. `fullName` tối đa 160 ký tự. Password giữ nguyên khoảng trắng, tối thiểu 12 ký tự, tối đa 72 byte UTF-8. Role phải ACTIVE trong tenant; roleCodes không rỗng/trùng. teamIds tùy chọn, tối đa 100 UUID không trùng, chỉ chọn Team ACTIVE trong tenant. Payload không nhận tenant/password hash/trường ngoài whitelist.

PATCH chỉ nhận `fullName`, `status`, `metadata`. Status là `ACTIVE/INACTIVE/ARCHIVED`; vô hiệu hóa/archive thu hồi các phiên của target, không tự khôi phục phiên cũ khi activate. Actor không được tự đổi trạng thái. User đang quản lý Team ACTIVE không được disable hoặc mất role MANAGER; cần xử lý Team trước. Metadata là object tối đa 4096 ký tự/5 cấp, không có key xác thực (`password`, `token`, `secret`, `credential`). Thay đổi ghi `USER_UPDATED`; no-op không thêm audit.

### `PUT /users/{userId}/roles`

```json
{
  "roleCodes": ["SALES_REP", "CUSTOMER_CARE"]
}
```

Response trả user summary mới, gồm toàn bộ role code và permission hiệu lực. Backend từ chối nếu role không thuộc organization, actor không phải `ADMIN`, hoặc `userId` trùng actor đang đăng nhập; trường hợp tự thay đổi role trả `403 FORBIDDEN_SELF_PRIVILEGE_ESCALATION`. Mọi thay đổi role ghi audit event `USER_ROLES_CHANGED` với actor, target user, role trước/sau và timestamp.

Role cũ được đánh dấu revoked_at, không xóa lịch sử. Bộ role mới thay toàn bộ, hủy grant tương lai bị bỏ và thu hồi ngay grant bị bỏ dù đã có thời điểm revoke trong tương lai. Không để mất Admin ACTIVE cuối cùng; no-op không sinh grant/audit trùng. JWT cũ nhận quyền mới ở request kế tiếp.

### Role và Permission

| Method | Path | Permission | Mục đích |
|---|---|---|---|
| `GET` | `/roles` | Authenticated | Danh sách role code được hỗ trợ để hiển thị; không kèm permission nhạy cảm. |
| `GET` | `/permissions` | `ADMIN` + `identity.role.manage` | Danh sách permission hệ thống. |
| `GET` | `/roles/{roleCode}/permissions` | `ADMIN` + `identity.role.manage` | Permission hiện có của role. |
| `PUT` | `/roles/{roleCode}/permissions` | `ADMIN` + `identity.role.manage` | Cập nhật permission của role. |

Role mặc định của MVP: `ADMIN`, `MANAGER`, `SALES_REP`, `CUSTOMER_CARE`, `DATA_STAFF`. User có thể giữ nhiều role; UI dùng tên hiển thị `Sales Staff` cho role code `SALES_REP`.

Role catalog trả `[{code, displayName}]` của role ACTIVE trong tenant; permission catalog trả `[{code, description}]`. GET/PUT permission của role trả mảng permission code. PUT nhận `{ "permissionCodes": ["customer.read"] }`, thay toàn bộ (cho phép mảng rỗng). Không cấp identity.role.manage cho role khác ADMIN. Không thay permission của role actor đang giữ; trả `403 FORBIDDEN_SELF_PRIVILEGE_ESCALATION`. Thay đổi ghi `ROLE_PERMISSIONS_CHANGED` trước/sau trong cùng transaction; no-op không thêm audit. Không có CRUD tạo role/permission tùy ý.

## 4. Organization và Team

### Organization

| Method | Path | Permission | Mục đích |
|---|---|---|---|
| `GET` | `/organization` | Authenticated | Xem organization hiện tại. |
| `PATCH` | `/organization` | `organization.team.manage` + `ADMIN` | Cập nhật tên và ngành organization. |

```json
{
  "name": "POSE Demo",
  "industry": "Education Services"
}
```

Organization không được đổi bằng ID trên URL trong MVP; mọi user chỉ thao tác organization có trong token/principal.

GET và PATCH thành công trả `200` với object `{id, code, name, industry, active}`, kèm `Cache-Control: no-store`. `industry` có thể là `null`. Không có organization ID trên URL/body để chọn tenant; query parameter không thay tenant hiện tại.

PATCH là cập nhật một phần, chỉ cho phép:

- `name`: chuỗi sau trim không trống, tối đa 150 ký tự; không nhận `null`.
- `industry`: chuỗi sau trim không trống, tối đa 120 ký tự; `null` xóa ngành.
- Trường không gửi giữ nguyên. Body phải là JSON object có ít nhất một trong hai trường trên. Trường ngoài whitelist (bao gồm `id`, `organizationId`, `code`, `active`, `metadata`) bị từ chối bằng `400 VALIDATION_ERROR` và `fieldErrors`. Metadata không thuộc phạm vi PATCH Organization v1.

Backend kiểm tra cả role Admin và permission hiện tại trong DB; thiếu một trong hai trả `403 FORBIDDEN_PERMISSION`. Chưa xác thực/phiên không hợp lệ trả `401` theo contract Auth.

Khóa row khi merge PATCH để tránh mất cập nhật đồng thời. Mọi thay đổi thực tế ghi audit `ORGANIZATION_UPDATED`, entity `ORGANIZATION`, actor, thời điểm và dữ liệu trước/sau, cùng transaction với update. Audit lỗi thì rollback. PATCH không thay giá trị trả `200` và không thêm audit cập nhật giả.

### Team

| Method | Path | Permission | Mục đích |
|---|---|---|---|
| `GET` | `/teams` | Authenticated | Danh sách team user được phép xem. |
| `POST` | `/teams` | `organization.team.manage` | Tạo team trong organization. |
| `GET` | `/teams/{teamId}` | Team scope | Chi tiết team và member summary. |
| `PATCH` | `/teams/{teamId}` | `organization.team.manage` + team scope | Sửa tên, mô tả hoặc trạng thái. |
| `PUT` | `/teams/{teamId}/members` | `organization.team.manage` + team scope | Thay danh sách member của team. |

### `POST /teams`

```json
{
  "code": "SALES_NORTH",
  "name": "Sales North",
  "description": "Nhóm sale khu vực phía Bắc",
  "departmentId": "uuid",
  "managerUserId": "uuid"
}
```

POST trả `201`; GET/PATCH trả `200` với `{id, code, name, description, status, departmentId, managerUserId, members}`. Member summary là `{id, fullName, status}`, không chứa credential/email/permission. Code tối đa 60 ký tự chữ/số/_/-, uppercase và unique không phân biệt hoa/thường trong tenant. Name tối đa 160; description tùy chọn/null hoặc chuỗi không trống tối đa 2000. DepartmentId bắt buộc theo DB v2: Department ACTIVE cùng organization. ManagerUserId phải ACTIVE và có role MANAGER hiệu lực trong tenant. Admin tạo Team cho Manager hợp lệ; Manager chỉ tạo Team do mình quản lý. Ghi `TEAM_CREATED`.

PATCH chỉ nhận `name`, `description`, `status` (`ACTIVE/INACTIVE`), không đổi code/tenant/department/manager. Manager chỉ sửa Team đang quản lý. Team INACTIVE mất scope Manager ngay; Admin có quyền có thể đọc/activate nếu Manager vẫn hợp lệ. Ghi `TEAM_UPDATED` khi thay đổi thực tế; no-op không thêm audit. Department CRUD và đổi Manager không thuộc contract foundation này.

### `PUT /teams/{teamId}/members`

```json
{
  "memberUserIds": ["uuid-1", "uuid-2"]
}
```

Response trả danh sách member hiện tại. Backend không cho Manager sửa team ngoài scope và ghi audit event `TEAM_MEMBERS_CHANGED`.

PUT members nhận tối đa 100 UUID không trùng, cho phép mảng rỗng; Team/User đều phải ACTIVE trong tenant. Admin chọn member trong tenant; Manager chỉ chọn user đã trong phạm vi team quản lý hoặc bản thân, không thêm người ngoài scope để mở rộng quyền đọc. Membership bị bỏ được kết thúc bằng status ENDED/left_at, hủy cả membership tương lai bị bỏ và giữ lịch sử. Lặp lại danh sách không sinh membership/audit trùng. Membership tương lai, hết hạn, ENDED hoặc chéo tenant không cấp scope.

## 5. Scope và response behavior

| Actor | Scope foundation |
|---|---|
| `ADMIN` | Toàn organization theo permission được cấp. |
| `MANAGER` | Team được phân công quản lý và member của team đó. |
| `SALES_REP`, `CUSTOMER_CARE`, `DATA_STAFF` | Hồ sơ cá nhân, team membership và organization summary theo quyền đọc. |

- Request ngoài organization hoặc ngoài team scope không trả dữ liệu record; backend trả `403 FORBIDDEN_SCOPE`.
- `identity.user.read` chỉ cho phép Manager xem member trong team scope; quyền tạo/sửa user vẫn yêu cầu `identity.user.manage`.
- Chỉ `ADMIN` có `identity.role.manage`. Manager không được thay role/permission của bất kỳ user nào và không được tự thay đổi role của chính mình.
- Khi user có nhiều role, Backend dùng hợp permission nhưng không bỏ qua organization scope, team scope hoặc record scope.
- `GET /users` và `GET /teams` áp dụng pagination/filter ở Backend trước khi trả response.
- Không có endpoint public để tạo role/permission ngoài organization hiện tại.

## 6. Quy tắc phải được kế thừa trong CRM API Contract

Các endpoint CRM chưa thuộc foundation này, nhưng contract của các module sau phải kế thừa các rule đã chốt:

- API mở lại Opportunity yêu cầu `opportunity.reopen`; chỉ Manager trong team scope được đưa Opportunity `WON`/`LOST` về `QUALIFIED` và phải tạo audit log.
- API retry Import Job yêu cầu `import.retry`; chỉ Data Staff trong scope được retry job `FAILED`, dùng idempotency key và không tự commit/merge dữ liệu rủi ro.
- API trả Interaction/Feedback phải lọc note `SALES_ONLY`; Customer Care chỉ nhận note `CARE_VISIBLE` khi có customer/team scope phù hợp.

## 7. Audit và test contract bắt buộc

- Mutation Auth/User/Role/Permission/Organization/Team tạo audit log với actor, action, entity và timestamp.
- Test: login sai credential; token hết hạn; Sale đọc user ngoài scope; Manager sửa team ngoài scope; Manager tự nâng quyền; Admin gán nhiều role hợp lệ cho user khác; role không thuộc organization; tạo user email trùng; cập nhật member team không cùng organization.
- API Contract của module CRM phải tái sử dụng các rule auth, organization và team scope trong tài liệu này.

Administration khóa organization rồi đọc lại quyền trước mutation để không dùng quyền cũ lúc chờ khóa. Mutation/audit cùng transaction; audit hoặc constraint lỗi rollback và trả `409`. User/Team ID không có trong scope (kể cả không tồn tại) trả cùng `403 FORBIDDEN_SCOPE`, không tiết lộ record.
