# API Offering v1

Ngày: 01/11/2026 · Bổ sung cho API Contract Foundation v1 · Nguồn nghiệp vụ: UC-03 trong Word cập nhật.

Contract này mở rộng API foundation hiện có để triển khai UC-03 qua frontend BFF, Backend và PostgreSQL. Organization luôn được xác định từ actor hiện tại; `organizationId` không được nhận trong body/query. Manager bị giới hạn ở team đang quản lý. Sale chỉ đọc Offering đang hoạt động, được assign cho mình và còn hiệu lực. Backend là nguồn quyết định cuối cùng.

## 1. Quy ước

- Base path: `/api/v1`; JSON camelCase; ID UUID; timestamp ISO-8601 UTC; response `Cache-Control: no-store`.
- List dùng `{items,page,size,totalItems,totalPages}`; `page` bắt đầu từ 0, `size` 1–100. Scope được áp dụng trước bộ lọc đếm và phân trang.
- `kind`: `PRODUCT` hoặc `SERVICE`.
- `active`: boolean giao diện/API. `true` ánh xạ DB `status='ACTIVE'`; `false` ánh xạ `INACTIVE` hoặc legacy `DRAFT`. API không yêu cầu người dùng thao tác trực tiếp với DRAFT.
- `referencePrice` là chuỗi thập phân tối đa hai chữ số lẻ để tránh mất độ chính xác JavaScript, ví dụ `"12000000.00"`. Offering mới dùng `VND`; khi sửa, Backend giữ currency của giá hiện hành trong `offering_prices` vì UC-03 không có thao tác đổi loại tiền.
- `code` được Backend sinh dạng `OFF-<UUID>` vì cột hiện có bắt buộc nhưng UC-03 không yêu cầu người dùng nhập mã.
- Danh mục là bắt buộc theo UC-03; Category phải ACTIVE và thuộc organization hiện tại khi tạo hoặc khi đổi Category.
- Team bắt buộc cho Offering mới. `teamId=null` chỉ có thể còn ở dữ liệu legacy chưa được gán lại; bản ghi đó không nằm trong Manager team scope. Admin có permission `offering.manage` có thể đọc/chỉnh sửa và gán team cho bản ghi legacy.
- Không có hard delete. “Xóa” trong ngữ cảnh danh mục là ngừng kích hoạt; không xóa assignment, lead, opportunity, giá hoặc audit history.

## 2. Permission và data scope

| Tác nhân | Permission/role | Đọc danh sách/chi tiết | Ghi |
|---|---|---|---|
| Manager | `MANAGER` + `offering.manage` | Offering có `teamId` thuộc `managedTeamIds` hiện hành của actor | Tạo, sửa, kích hoạt/ngừng kích hoạt; team đích phải thuộc `managedTeamIds` |
| Admin | `ADMIN` + `offering.manage` | Organization hiện tại, gồm legacy Offering chưa gán team | Tạo/sửa/status; có thể gán legacy Offering vào Team ACTIVE cùng tenant |
| Sales Staff | `SALES_REP` + `offering.read` | Offering `ACTIVE`, cùng organization, có assignment của chính Sales `ACTIVE`, chưa thu hồi, trong khoảng hiệu lực; Sales đồng thời có membership `ACTIVE` còn hiệu lực ở team sở hữu `ACTIVE` | Không có mutation Offering |
| Role khác | Không có permission phù hợp | 403 | 403 |

Nếu actor có nhiều role, permission là hợp nhưng vẫn áp dụng policy tương ứng cho từng request. Nhánh Manager không mở rộng sang membership team thường; nhánh Sales không mở rộng sang Team hoặc toàn organization. Record ngoài tenant/scope không được phân biệt với record không tồn tại trong response ngoài `403 FORBIDDEN_SCOPE` dùng chung.

## 3. Routes

| Method | Path | Permission | Mục đích |
|---|---|---|---|
| `GET` | `/offerings` | `offering.manage` hoặc `offering.read` + role/scope tương ứng | Danh sách và tìm kiếm |
| `GET` | `/offerings/categories` | `offering.manage` | Category lookup cùng tenant; chỉ category ACTIVE |
| `GET` | `/offerings/{id}` | `offering.manage` hoặc `offering.read` + scope | Chi tiết Offering và giá tham khảo hiện hành |
| `POST` | `/offerings` | `offering.manage` + `MANAGER`/`ADMIN` | Tạo Offering |
| `PUT` | `/offerings/{id}` | `offering.manage` + `MANAGER`/`ADMIN` | Cập nhật thông tin và giá tham khảo |
| `POST` | `/offerings/{id}/activate` | `offering.manage` + `MANAGER`/`ADMIN` | Kích hoạt |
| `POST` | `/offerings/{id}/deactivate` | `offering.manage` + `MANAGER`/`ADMIN` | Ngừng kích hoạt |

`GET /teams?page=0&size=100` hiện có được dùng để nạp lựa chọn team; backend đã trả đúng team scope theo role. BFF hiện whitelist root `offerings` và `teams`; request vẫn đi qua `/api/backend/*`, không gọi Backend trực tiếp từ browser.

### Query danh sách

| Query | Kiểu | Hành vi |
|---|---|---|
| `page`, `size` | integer | Mặc định `0`, `20`; giới hạn theo foundation |
| `search` | string | Trim; tìm chuỗi literal trong tên hoặc mã; tối đa 200 ký tự |
| `kind` | enum | Lọc `PRODUCT` hoặc `SERVICE` |
| `categoryId` | UUID | Lọc Category |
| `active` | boolean | `true`: ACTIVE; `false`: INACTIVE/DRAFT; bỏ trống: mọi trạng thái |
| `teamId` | UUID | Manager chỉ lọc team mình quản lý; Admin có thể lọc team trong tenant; ngoài scope trả 403 |

Sale không dùng bộ lọc team; scope assignment của Sale được áp trước mọi query filter. Kết quả không bao giờ bao gồm offering inactive hoặc assignment bị thu hồi/hết hiệu lực.

Khoảng assignment gồm cả hai đầu (`effectiveFrom <= now`, `effectiveTo` null hoặc `>= now`); membership yêu cầu `joinedAt <= now` và `leftAt` null hoặc `> now`. Team null, team INACTIVE hoặc membership hết hiệu lực làm mất quyền đọc ngay, không tự thu hồi assignment.

## 4. JSON shape

```json
{
  "id": "uuid",
  "code": "OFF-uuid",
  "name": "Gói tư vấn CRM",
  "kind": "SERVICE",
  "categoryId": "uuid",
  "categoryName": "Tư vấn",
  "description": "Mô tả ngắn",
  "unit": "tháng",
  "referencePrice": "12000000.00",
  "currencyCode": "VND",
  "referencePriceEffectiveFrom": "2026-11-01T00:00:00Z",
  "active": true,
  "teamId": "uuid",
  "teamName": "Sales HCM",
  "createdAt": "2026-11-01T00:00:00Z",
  "updatedAt": "2026-11-01T00:00:00Z"
}
```

`referencePrice`, `referencePriceEffectiveFrom`, `teamId`, `teamName` có thể `null` khi dữ liệu legacy chưa có giá/team hoặc không có price đang hiệu lực. Sale chỉ nhận Offering ACTIVE có price đã được kiểm tra theo scope assignment. `active=false` gộp trạng thái DB `DRAFT` và `INACTIVE`, không phát tán khái niệm legacy vào UI.

## 5. Request body

### Tạo

```json
{
  "name": "Gói tư vấn CRM",
  "kind": "SERVICE",
  "categoryId": "uuid",
  "teamId": "uuid",
  "description": "Mô tả ngắn",
  "unit": "tháng",
  "referencePrice": "12000000.00",
  "active": true
}
```

Tạo thành công trả `201` và chi tiết Offering. Không nhận `id`, `organizationId`, `code`, `status`, price row ID hay audit fields từ client.

### Cập nhật

```json
{
  "name": "Gói tư vấn CRM nâng cao",
  "kind": "SERVICE",
  "categoryId": "uuid",
  "teamId": "uuid",
  "description": "Mô tả cập nhật",
  "unit": "tháng",
  "referencePrice": "15000000.00",
  "expectedUpdatedAt": "2026-11-01T00:00:00Z"
}
```

PUT cập nhật toàn bộ trường form; `description` và `unit` nhận `null` để xóa. `expectedUpdatedAt` bắt buộc để phát hiện form cũ. `active` không được thay qua PUT; đổi trạng thái phải gọi command riêng và xác nhận trong UI. Thành công trả `200` và chi tiết mới. Nếu giá thay đổi, Backend kết thúc khoảng giá hiện hành tại thời điểm mutation và thêm price row mới trong cùng transaction; không ghi đè row lịch sử. Price row tương lai (nếu có) vẫn giữ nguyên khoảng hiệu lực.

### Activate/deactivate

Không cần body. Thành công trả `200` cùng Offering sau thay đổi. Lệnh lặp lại cùng trạng thái là idempotent và không tạo audit giả. Deactivate không tự thu hồi Sales Assignment; Sale không đọc được Offering inactive, còn lịch sử và assignment được giữ nguyên.

## 6. Validation và lỗi

| Trường/rule | Quy tắc |
|---|---|
| `name` | Bắt buộc, trim, 1–200 ký tự |
| `kind` | Bắt buộc, enum PRODUCT/SERVICE |
| `categoryId` | Bắt buộc; Category ACTIVE, cùng organization |
| `teamId` | Bắt buộc trên create/update; Team ACTIVE, cùng organization; Manager phải quản lý Team đó |
| `referencePrice` | Bắt buộc trên create/update; decimal hữu hạn, `0 ≤ giá ≤ 9999999999999999.99`, tối đa 2 chữ số lẻ |
| `unit` | Tùy chọn, tối đa 80 ký tự |
| `description` | Tùy chọn, tối đa 4000 ký tự ở tầng ứng dụng; lưu trong text column hiện có |
| Trùng mục | Không cho cùng tên sau trim/lowercase và cùng kind khi Offering đang ACTIVE trong cùng organization; không phân biệt team |
| `active` | Bắt buộc khi tạo; boolean JSON; update/status thay bằng route activate/deactivate |
| `expectedUpdatedAt` | Bắt buộc khi update, phải khớp bản mới nhất |

| HTTP/code | Dùng khi |
|---|---|
| `400 VALIDATION_ERROR` | Field/body/query sai hoặc thiếu; có `fieldErrors` |
| `403 FORBIDDEN_PERMISSION` | Thiếu permission/role |
| `403 FORBIDDEN_SCOPE` | Team/record/user/assignment ngoài scope |
| `404 RESOURCE_NOT_FOUND` | Endpoint con không tồn tại; không dùng để dò dữ liệu ngoài scope |
| `409 OFFERING_NAME_EXISTS` | Trùng tên/loại đang hoạt động |
| `409 OFFERING_CHANGED` | `expectedUpdatedAt` cũ |
| `409 CONFLICT` | Category/team inactive, price timeline không hợp lệ hoặc conflict dữ liệu khác |
| `401 AUTH_INVALID_TOKEN` / `AUTH_TOKEN_EXPIRED` | Theo contract auth hiện có |

Lỗi dùng error envelope của foundation `{code,message,fieldErrors,traceId}` và không bao gồm record snapshot ngoài scope.

## 7. Lưu trữ và migration

- Dùng bảng `offerings` hiện có; không tạo lại bảng.
- `name`, `kind`, `categoryId`, `description`, `unit`, `teamId` lưu trên row Offering; `code` do Backend sinh.
- `referencePrice` lấy từ `offering_prices` đang hiệu lực (`amount`, `currency_code`, thời điểm hiệu lực). Khi tạo dùng `VND`; khi thay giá, giữ currency hiện hành và lưu lịch sử bằng row mới.
- `active` adapter sang `status`: ACTIVE ↔ true; INACTIVE/DRAFT ↔ false. Legacy DRAFT được giữ nguyên đến khi có thao tác activate; không mass-update status.
- Migration mới thêm `team_id` và composite FK bảo đảm Team cùng tenant. Không suy đoán team cho row cũ; row legacy `team_id=null` được giữ lại, không lộ cho Manager ngoài scope và có thể được Admin gán team.
- Thêm permission `offering.read` để Sales đọc đúng assignment mà không cấp quyền quản lý. Grant mặc định được thêm cho role code SALES_REP; role permission có thể tiếp tục bị Admin thu hồi qua cơ chế hiện có.
- Mọi create/update/status và audit nằm trong transaction. Tenant row lock đang có trong `FoundationAccess.lockAndRefresh` tuần tự hóa các Offering write trong cùng tenant để chống race ở kiểm tra trùng; database còn index tìm kiếm nhưng không ép unique index có thể làm hỏng dữ liệu trùng legacy.

## 8. Bảo mật BFF

Browser gọi `/api/backend/offerings...`; BFF lấy access token từ HttpOnly cookie và chuyển request qua adapter server hiện có. Không trả token cho client, không gọi URL Backend từ browser, không nhận organizationId từ client. Mutation vẫn qua kiểm tra same-origin/CSRF trong Route Handler hiện có. API/backend luôn lặp lại kiểm quyền, tenant, team và assignment.
