# API Contract Foundation v1

## Phạm vi

Contract này bao phủ nền tảng do Huỳnh Minh Tài sở hữu: Authentication, User, Role, Permission, Organization và Team. Các endpoint Offering, Sales Assignment, CRM, Import, Dashboard và Automation được bổ sung trong contract theo module owner tương ứng.

## 1. Quy ước chung

- Base URL: `/api/v1`.
- Payload dùng JSON `camelCase`; ID dùng UUID; thời gian dùng ISO-8601 UTC.
- Endpoint đã xác thực yêu cầu `Authorization: Bearer <accessToken>`.
- User luôn hoạt động trong một organization; backend tự xác định organization từ principal/token, không nhận `organizationId` từ client cho request nghiệp vụ thông thường.
- List API dùng `page` từ `0`, `size` mặc định `20`, tối đa `100`, và trả `items`, `page`, `size`, `totalItems`, `totalPages`.
- Backend kiểm tra permission, team scope và record scope; frontend không thay thế lớp kiểm tra này.

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

Các mã lỗi dùng trong foundation: `AUTH_INVALID_CREDENTIALS`, `AUTH_TOKEN_EXPIRED`, `FORBIDDEN_PERMISSION`, `FORBIDDEN_SCOPE`, `FORBIDDEN_SELF_PRIVILEGE_ESCALATION`, `RESOURCE_NOT_FOUND`, `VALIDATION_ERROR`, `CONFLICT`, `USER_EMAIL_EXISTS`, `TEAM_NAME_EXISTS`.

## 2. Authentication

| Method | Path | Role | Mục đích |
|---|---|---|---|
| `POST` | `/auth/login` | Public | Đăng nhập bằng email và password. |
| `POST` | `/auth/refresh` | Public với refresh token hợp lệ | Lấy access token mới. |
| `POST` | `/auth/logout` | Authenticated | Thu hồi refresh token hiện tại. |
| `GET` | `/auth/me` | Authenticated | Lấy user, role, permission và team scope hiện tại. |

### `POST /auth/login`

```json
{
  "email": "tai@pose.local",
  "password": "example-password"
}
```

```json
{
  "accessToken": "<jwt>",
  "refreshToken": "<opaque-token>",
  "expiresIn": 900,
  "user": {
    "id": "uuid",
    "email": "tai@pose.local",
    "fullName": "Huỳnh Minh Tài",
    "status": "ACTIVE",
    "organization": { "id": "uuid", "name": "POSE Demo" },
    "roles": ["ADMIN"],
    "permissions": ["identity.user.manage", "audit.read"],
    "teamIds": ["uuid"]
  }
}
```

Login thất bại luôn trả `401 AUTH_INVALID_CREDENTIALS`; không tiết lộ email có tồn tại hay không. Password chỉ được truyền qua TLS và không log.

`roles` có thể chứa nhiều role code. `permissions` là hợp permission hiệu lực của các role đó; Backend vẫn kiểm tra organization, team scope và record scope cho từng request.

## 3. User, Role và Permission

### User

| Method | Path | Permission | Mục đích |
|---|---|---|---|
| `GET` | `/users` | `identity.user.read` | Danh sách user trong organization; Manager chỉ thấy team scope. |
| `POST` | `/users` | `identity.user.manage` | Tạo user mới trong organization. |
| `GET` | `/users/{userId}` | `identity.user.read` hoặc self | Chi tiết user trong scope. |
| `PATCH` | `/users/{userId}` | `identity.user.manage` | Sửa full name, status và metadata an toàn. |
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

### `PUT /users/{userId}/roles`

```json
{
  "roleCodes": ["SALES_REP", "CUSTOMER_CARE"]
}
```

Response trả user summary mới, gồm toàn bộ role code và permission hiệu lực. Backend từ chối nếu role không thuộc organization, actor không phải `ADMIN`, hoặc `userId` trùng actor đang đăng nhập; trường hợp tự thay đổi role trả `403 FORBIDDEN_SELF_PRIVILEGE_ESCALATION`. Mọi thay đổi role ghi audit event `USER_ROLES_CHANGED` với actor, target user, role trước/sau và timestamp.

### Role và Permission

| Method | Path | Permission | Mục đích |
|---|---|---|---|
| `GET` | `/roles` | Authenticated | Danh sách role code được hỗ trợ để hiển thị; không kèm permission nhạy cảm. |
| `GET` | `/permissions` | `identity.role.manage` | Danh sách permission hệ thống. |
| `GET` | `/roles/{roleCode}/permissions` | `identity.role.manage` | Permission hiện có của role. |
| `PUT` | `/roles/{roleCode}/permissions` | `identity.role.manage` | Cập nhật permission của role. |

Role mặc định của MVP: `ADMIN`, `MANAGER`, `SALES_REP`, `CUSTOMER_CARE`, `DATA_STAFF`. User có thể giữ nhiều role; UI dùng tên hiển thị `Sales Staff` cho role code `SALES_REP`.

## 4. Organization và Team

### Organization

| Method | Path | Permission | Mục đích |
|---|---|---|---|
| `GET` | `/organization` | Authenticated | Xem organization hiện tại. |
| `PATCH` | `/organization` | `organization.team.manage` + `ADMIN` | Cập nhật tên, ngành và metadata organization. |

```json
{
  "name": "POSE Demo",
  "industry": "Education Services"
}
```

Organization không được đổi bằng ID trên URL trong MVP; mọi user chỉ thao tác organization có trong token/principal.

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
  "managerUserId": "uuid"
}
```

`code` unique trong organization. `managerUserId` phải là user `ACTIVE` cùng organization và có role `MANAGER`. Backend ghi audit event `TEAM_CREATED`.

### `PUT /teams/{teamId}/members`

```json
{
  "memberUserIds": ["uuid-1", "uuid-2"]
}
```

Response trả danh sách member hiện tại. Backend không cho Manager sửa team ngoài scope và ghi audit event `TEAM_MEMBERS_CHANGED`.

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
