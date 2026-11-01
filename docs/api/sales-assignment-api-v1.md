# API Sales Assignment v1

Ngày: 02/11/2026 · Bổ sung cho API Contract Foundation v1 và Offering API v1.

Contract này triển khai UC-04 phân công Offering cho Sales qua frontend BFF, Spring Boot modular monolith và PostgreSQL. Organization luôn lấy từ actor hiện tại; client không gửi `organizationId` để chọn tenant. Backend là nguồn kiểm tra permission, team scope, eligibility, hiệu lực, audit và trạng thái.

## 1. Quy ước và quyền

- Base path: `/api/v1/sales-assignments`; JSON camelCase; UUID ID; timestamp ISO-8601 UTC; response `Cache-Control: no-store`.
- Danh sách dùng `{items,page,size,totalItems,totalPages}`; `page` bắt đầu từ 0, `size` 1–100. Scope được áp trước lọc và phân trang.
- `MANAGER` cần `sales_assignment.manage` và chỉ xem/thao tác Offering thuộc `managedTeamIds` hiện hành. `ADMIN` cần cùng permission và được xem/thao tác trong organization hiện tại.
- Offering phải `ACTIVE` và có team ACTIVE. Sale nhận phân công phải là user ACTIVE, có role `SALES_REP`, permission `offering.read`, membership ACTIVE còn hiệu lực trong đúng team sở hữu Offering và cùng organization.
- Sale thuộc team khác bị từ chối kể cả khi Manager đang quản lý cả hai team. Không hard-delete Sales Assignment.
- Record khác tenant hoặc ngoài team scope không được tiết lộ; trả `403 FORBIDDEN_SCOPE` theo error envelope hiện có.

## 2. Routes

| Method | Path | Mục đích |
|---|---|---|
| `GET` | `/sales-assignments` | Danh sách trong scope Manager/Admin |
| `GET` | `/sales-assignments/candidates?offeringId={uuid}` | Sales ACTIVE đủ điều kiện trong đúng team của Offering |
| `GET` | `/sales-assignments/{id}` | Chi tiết assignment trong scope |
| `GET` | `/sales-assignments/{id}/open-work` | Xem Lead/Opportunity/Task còn mở trước khi thu hồi |
| `POST` | `/sales-assignments` | Tạo assignment |
| `PATCH` | `/sales-assignments/{id}` | Gia hạn assignment |
| `POST` | `/sales-assignments/{id}/revoke` | Thu hồi assignment sau khi xác nhận và không còn hồ sơ mở cần xử lý |

### Lọc danh sách

`GET /sales-assignments` nhận `page`, `size`, `search`, `teamId`, `offeringId`, `salesSearch`, `status`, `validity`.

- `search`: tìm trong tên/mã Offering hoặc tên Sale; `salesSearch` giới hạn tìm theo tên/email Sale; tối đa 200 ký tự.
- `teamId`, `offeringId`: filter trong scope đã xác thực; Manager chọn team thuộc managed scope.
- `status`: `ACTIVE`, `EXPIRED`, `REVOKED` là trạng thái lưu trong DB.
- `validity`: `CURRENT`, `UPCOMING`, `EXPIRED`, `REVOKED` được suy ra từ trạng thái và `effectiveFrom`/`effectiveTo` tại thời điểm truy vấn.

Response mỗi assignment gồm `id`, `offeringId`, `offeringCode`, `offeringName`, `teamId`, `teamName`, `salesUserId`, `salesUserName`, `status`, `effectiveState`, `effectiveFrom`, `effectiveTo`, `revokedAt`, `createdAt`, `updatedAt`.

### Lấy Sales đủ điều kiện

`GET /sales-assignments/candidates?offeringId={uuid}` chỉ trả user ACTIVE có `SALES_REP`, `offering.read` và membership ACTIVE trong team sở hữu Offering. Backend tự lấy team từ Offering, không nhận `teamId` từ client.

### Tạo assignment

```json
{
  "offeringId": "uuid",
  "salesUserId": "uuid",
  "effectiveFrom": "2026-11-02T00:00:00Z",
  "effectiveTo": null
}
```

`effectiveFrom` bắt buộc. `effectiveTo` tùy chọn và phải bằng hoặc sau `effectiveFrom`. Thành công trả `201` cùng assignment mới. Mutation và audit `SALES_ASSIGNMENT_CREATED` nằm trong cùng transaction.

### Gia hạn

```json
{
  "effectiveTo": "2026-12-31T16:59:59Z",
  "expectedUpdatedAt": "2026-11-02T00:00:00Z"
}
```

`effectiveTo` có thể là `null` để bỏ thời hạn. Chỉ gia hạn hoặc bỏ ngày kết thúc, không rút ngắn; assignment đã thu hồi không thể gia hạn. `expectedUpdatedAt` phải khớp phiên bản mới nhất. Gia hạn thành công đặt lại trạng thái DB `ACTIVE`, ghi audit `SALES_ASSIGNMENT_EXTENDED` và trả `200`.

### Thu hồi và hồ sơ còn mở

Trước khi xác nhận, frontend gọi `/sales-assignments/{id}/open-work`. Response có `totalItems` và tối đa 50 mục đầu tiên `{type,id,title,status}`. Nếu còn mục cần xử lý, UI không cho xác nhận; backend kiểm tra lại trước khi ghi.

Lead được coi là còn mở nếu chưa chuyển đổi/bị loại và chưa archive; Opportunity nếu chưa `WON`/`LOST`; Task nếu `OPEN`/`IN_PROGRESS` và liên kết Lead/Opportunity của đúng Offering, đang thuộc Sale được phân công. Schema hiện tại không có quan hệ Offering trực tiếp cho Task gắn Customer, nên endpoint không suy đoán quan hệ đó.

Thu hồi thành công giữ nguyên row, đặt `status='REVOKED'`, ghi `revokedAt` và audit `SALES_ASSIGNMENT_REVOKED` trong cùng transaction. Nếu có hồ sơ mở, trả `409 SALES_ASSIGNMENT_OPEN_WORK`; không thay đổi assignment và không ghi audit thành công.

## 3. Hết hạn tự động và audit SYSTEM

- Backend job chạy nền theo chu kỳ 5 phút, tìm batch tối đa 100 row `ACTIVE` có `effectiveTo` đã qua và chưa thu hồi; chuyển sang `EXPIRED` đúng một lần.
- Mỗi lần chuyển trạng thái và audit `SALES_ASSIGNMENT_EXPIRED` commit trong cùng transaction. Nếu audit thất bại, toàn bộ update trong batch rollback.
- Audit phân biệt actor bằng `actorType=USER|SYSTEM`. User event có `actorUserId`; job có `actorType=SYSTEM`, `actorSystemKey=sales-assignment-expiry` và không có user giả trong bảng users.
- API Offering của Sales vẫn kiểm tra thời gian hiệu lực trực tiếp; quyền đọc bị chặn ngay khi hết hạn, không phụ thuộc chu kỳ job.

## 4. Lỗi chính

| HTTP/code | Khi dùng |
|---|---|
| `400 VALIDATION_ERROR` | ID, timestamp hoặc khoảng hiệu lực sai; response có `fieldErrors` |
| `403 FORBIDDEN_PERMISSION` | Thiếu permission/role |
| `403 FORBIDDEN_SCOPE` | Team, Offering hoặc Sales user ngoài tenant/team scope; assignment chéo team |
| `404 RESOURCE_NOT_FOUND` | Endpoint con không tồn tại |
| `409 TEAM_INACTIVE` | Team sở hữu Offering đã ngừng hoạt động |
| `409 OFFERING_TEAM_REQUIRED` | Offering legacy chưa có team |
| `409 SALES_ASSIGNMENT_CHANGED` | `expectedUpdatedAt` đã cũ |
| `409 SALES_ASSIGNMENT_REVOKED` | Assignment đã thu hồi |
| `409 SALES_ASSIGNMENT_OPEN_WORK` | Còn Lead/Opportunity/Task mở cần xử lý |

Mọi lỗi dùng error envelope foundation `{code,message,fieldErrors,traceId}`; không trả record ngoài scope.

## 5. Lưu trữ, migration và audit

- Dùng `sales_assignments` hiện có; không tạo bảng trùng. Giữ nguyên migration V1–V5.
- V6 thêm `sales_assignment.manage` và grant cho role `MANAGER`/`ADMIN` trong các organization.
- V7 bổ sung `actor_type` và `actor_system_key` cho `audit_logs`; dữ liệu audit cũ được mặc định là actor `USER`, không tạo tài khoản giả cho job nền.
- Không chạy migration trên Supabase dùng chung trong lượt triển khai này. Tuân thủ `backend/README.md` khi chạy migration trên database cô lập.
