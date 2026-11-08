# API Customer và Lead v1

Ngày 03/11/2026 · Theo UC-09/UC-10 và quyết định D1–D4 của Tài. Backend lấy organization từ actor, kiểm permission và scope trước khi trả dữ liệu hoặc ghi.

## Scope

Quyền đọc cần permission đọc hiệu lực và dùng hợp scope của các role. Quyền ghi cần một role vừa có permission ghi vừa có scope phù hợp. Admin đọc/sửa toàn organization, không tạo. Manager thao tác Lead theo team sở hữu Offering được quản lý; Customer theo owner hoặc assignee có membership hiệu lực tại team ACTIVE được quản lý. Sales thao tác Lead của mình khi còn đọc được Offering; Customer khi là owner hoặc Sales assignee hiệu lực. Customer Care chỉ đọc Customer có CARE assignment hiệu lực của mình. Data Staff có permission đọc nhưng scope import hoãn sang card import và trả `403 FORBIDDEN_SCOPE`. Khác tenant, ngoài scope và ID không tồn tại cùng trả `403 FORBIDDEN_SCOPE`; thiếu permission trả `403 FORBIDDEN_PERMISSION`. List bỏ bản ghi archived và áp scope trước phân trang.

Manager tạo phải gửi `ownerUserId`: owner ACTIVE, có role SALES_REP hiệu lực và đọc được Offering liên quan. Owner không đủ điều kiện trả `422` với field error `ownerUserId`; owner-candidates cho Manager/Admin chỉ trả Sales đủ điều kiện của Offering trong scope. Sales tạo với owner là chính mình. Không cho đổi owner qua PATCH.

Danh sách `/leads/owners` và `/customers/owners` chỉ dành cho Admin hoặc Manager có permission đọc tương ứng. Mỗi mục `{id, fullName}` là owner của ít nhất một hồ sơ chưa archived trong chính read scope hiện tại; distinct, sắp xếp theo tên. Không lộ owner qua hồ sơ khác tenant hoặc ngoài scope. Các role khác hoặc thiếu permission đọc nhận `403 FORBIDDEN_PERMISSION`. Đây là lựa chọn lọc danh sách, khác với owner-candidates dùng khi tạo hồ sơ.

Với Offering thuộc scope nhưng không ACTIVE hoặc legacy chưa có team, owner-candidates trả danh sách rỗng; không có Sales nào đủ điều kiện đọc Offering đó.

## Routes và dữ liệu

Base `/api/v1`; response thành công `Cache-Control: no-store`. List có `page`, `size`, `search`, `status`, `ownerUserId`; Lead thêm `offeringId`, Customer thêm `customerType`. Filter owner chỉ cho Manager/Admin. Response list dùng `{items,page,size,totalItems,totalPages}`.

| Method | Path | Mục đích |
|---|---|---|
| GET | `/leads`, `/leads/{id}` | Danh sách, chi tiết trong scope |
| POST | `/leads` | Tạo Lead |
| PATCH | `/leads/{id}` | Sửa thông tin Lead với `expectedUpdatedAt` |
| POST | `/leads/{id}/qualify`, `/leads/{id}/disqualify` | Đủ điều kiện hoặc loại; gửi `expectedUpdatedAt`, disqualify cần `reason` |
| POST | `/leads/{id}/convert` | Chuyển Lead QUALIFIED thành Customer theo DEC-13 |
| GET | `/leads/owner-candidates?offeringId=...` | Sales đủ điều kiện |
| GET | `/leads/owners` | Owner có Lead trong read scope, cho Admin/Manager |
| GET | `/customers`, `/customers/{id}` | Danh sách, chi tiết trong scope |
| POST | `/customers` | Tạo Customer và SALES assignment INITIAL trong cùng transaction |
| PATCH | `/customers/{id}` | Sửa hồ sơ với `expectedUpdatedAt` |
| POST | `/customers/{id}/reassign` | Đổi người phụ trách Customer kèm chuyển việc mở và ghi nhận bàn giao (DEC-20) |
| GET | `/customers/owner-candidates?offeringId=...` | Sales đủ điều kiện |
| GET | `/customers/owners` | Owner có Customer trong read scope, cho Admin/Manager |

Lead tạo nhận `displayName`, `email` và/hoặc `phone`, `source`, `offeringId`, `notes` tùy chọn và `ownerUserId` cho Manager. Customer tạo nhận `displayName`, `email` và/hoặc `phone`, `customerType`, `relatedOfferingId`, `address`, `notes` và `ownerUserId` cho Manager. Response gồm ID, thông tin hồ sơ, owner ID/tên, Offering ID/mã/tên, status và timestamps; trường V9 của dữ liệu cũ có thể null. Customer type `INDIVIDUAL|BUSINESS`; Lead source `WEBSITE|REFERRAL|EVENT|SOCIAL_MEDIA|PHONE|WALK_IN|OTHER` đã được Tài duyệt (DEC-11).

Ví dụ response Lead: `{ "id":"uuid", "displayName":"Khách tiềm năng", "email":"khach@example.test", "phone":null, "source":"WEBSITE", "notes":null, "ownerUserId":"uuid", "ownerName":"Sale", "offeringId":"uuid", "offeringCode":"OFF-...", "offeringName":"Dịch vụ", "status":"NEW", "qualifiedAt":null, "convertedCustomerId":null, "convertedAt":null, "disqualificationReason":null, "createdAt":"2026-11-03T00:00:00Z", "updatedAt":"2026-11-03T00:00:00Z" }`. Customer trả các trường chung cùng `customerType`, `address`, `relatedOfferingId`, `offeringCode`, `offeringName`.

Tạm chọn giới hạn ghi chú và lý do tối đa 4000 ký tự; field chuỗi tùy chọn dùng `null` hoặc bỏ field để không có giá trị, không nhận chuỗi rỗng. Khi đổi Offering của Lead hoặc Customer, owner hiện tại phải còn đủ điều kiện đọc Offering mới; với Customer, actor cũng phải có scope Offering mới qua vai trò phù hợp. Tài đã duyệt các lựa chọn này (DEC-11). Audit ghi action/entity/status và không lưu email, phone, địa chỉ, ghi chú hoặc lý do.

Email được trim và chuẩn hóa chữ thường; phone lưu giá trị trim gốc và bản chuẩn: bỏ khoảng trắng/dấu chấm/gạch/ngặc, đổi đầu `0` hoặc `84` sang `+84`, sau đó kiểm `^\\+[1-9]\\d{7,14}$`. Thiếu thông tin bắt buộc hoặc liên hệ sai định dạng trả 400 và `fieldErrors`. Chỉ kiểm trùng với Lead cùng organization chưa archived và chưa `DISQUALIFIED`/`CONVERTED` (Lead đã đóng không chặn khách cũ quay lại); Customer kiểm với Customer cùng organization. Không kiểm trùng chéo Lead/Customer.

## Lỗi và giới hạn

Mọi lỗi giữ envelope `{code,message,fieldErrors,traceId}`; riêng `LEAD_DUPLICATE`/`CUSTOMER_DUPLICATE` có thể thêm `details.duplicateId` chỉ khi actor có quyền đọc bản trùng. Không lộ ID hoặc thông tin hồ sơ ngoài scope.

| HTTP/code | Trường hợp |
|---|---|
| `400 VALIDATION_ERROR` | Field bắt buộc/định dạng sai, body có ownerUserId khi PATCH |
| `422 VALIDATION_ERROR` | Owner Manager chọn không đủ điều kiện, trùng người phụ trách hiện tại hoặc Customer chưa có `relatedOfferingId` khi đổi người phụ trách |
| `403 FORBIDDEN_PERMISSION` | Không có role hoặc permission cho thao tác |
| `403 FORBIDDEN_SCOPE` | Dữ liệu ngoài tenant/record scope hoặc Data Staff chưa có scope import |
| `409 LEAD_DUPLICATE`, `409 CUSTOMER_DUPLICATE` | Trùng email hoặc phone cùng loại hồ sơ |
| `409 LEAD_CHANGED`, `409 CUSTOMER_CHANGED` | `expectedUpdatedAt` đã cũ |
| `409 LEAD_CLOSED` | Lead CONVERTED/DISQUALIFIED không sửa được |
| `409 LEAD_STATE_CONFLICT` | Yêu cầu qualify khi Lead không ở trạng thái NEW |
| `409 LEAD_CONVERSION_CONFLICT` | Contact trùng không thể dùng lại hoặc Opportunity mở có owner ngoài scope Customer đích |

### Chuyển Lead thành Customer

`POST /leads/{id}/convert` nhận `expectedUpdatedAt`, `customerType` (`INDIVIDUAL`/`BUSINESS`), `address` tùy chọn tối đa 500 ký tự, `notes` tùy chọn tối đa 4000 ký tự; nếu bỏ `notes` thì dùng ghi chú Lead. Field khác trả 400. Response 200 là `{lead,customer,customerCreated}` và `Cache-Control: no-store`.

Sales hoặc Manager cần cùng một role có cả `lead.write` và `customer.write`, cùng scope ghi Lead; Admin không chuyển. Owner Lead phải còn đủ điều kiện đọc Offering của Lead, nếu không trả 422 tại `ownerUserId`. Lead NEW trả `LEAD_STATE_CONFLICT`, Lead CONVERTED/DISQUALIFIED trả `LEAD_CLOSED`, phiên bản cũ trả `LEAD_CHANGED`.

Backend so contact chuẩn hóa chính xác với Customer cùng tổ chức, gồm cả hồ sơ archive. Không trùng thì tạo Customer với owner, Offering và contact của Lead, cùng SALES assignment INITIAL và audit `CUSTOMER_CREATED`. Một Customer chưa archive được dùng lại không sửa hồ sơ khi owner Lead là owner hoặc SALES assignee hiệu lực. Trùng nhiều hồ sơ, hồ sơ archive hoặc không cùng scope Sales trả `LEAD_CONVERSION_CONFLICT`; chỉ thêm `details.duplicateId` khi đúng một bản trùng và actor được đọc bản đó.

Opportunity mở của Lead chuyển sang Customer nếu owner của từng Opportunity thuộc scope Sales Customer đích; ngược lại giao dịch bị từ chối. Theo DEC-14, Interaction ở mọi visibility và Task OPEN/IN_PROGRESS chuyển nếu không gắn Opportunity hoặc gắn Opportunity được chuyển trong lần này. Task DONE/CANCELLED, Interaction/Task gắn Opportunity không chuyển, Opportunity WON/LOST/CANCELLED và Feedback vẫn ở Lead. Không có hoàn tác; nguồn Lead của Opportunity đã chuyển tra qua audit. Tất cả cập nhật và audit trong cùng transaction; snapshot `LEAD_CONVERTED` thêm số `movedInteractions`, `movedTasks` và không ghi contact hoặc nội dung.

### Đổi người phụ trách Customer (DEC-20)

`POST /customers/{id}/reassign` nhận `{newOwnerUserId, reason}`:
- `newOwnerUserId` (`UUID`, bắt buộc): ID của Sales mới.
- `reason` (`string`, bắt buộc, sau khi trim dài 1–1000 ký tự): lý do bàn giao.

Quyền và điều kiện:
- Actor phải có role `ADMIN` hoặc `MANAGER` hiệu lực kèm permission `customer.write`, đồng thời hồ sơ Customer thuộc write scope và `related_offering_id` của Customer thuộc phạm vi quản lý (`requireManagedOffering`; Admin được phép trong tổ chức theo DEC-11). Sales, Care và Data Staff bị từ chối với `403 FORBIDDEN_PERMISSION`; hồ sơ ngoài scope, không tồn tại hoặc đã archived trả `403 FORBIDDEN_SCOPE`.
- Nếu Customer chưa có `related_offering_id`: trả `422 VALIDATION_ERROR` tại `relatedOfferingId` với thông báo `"Hồ sơ chưa có sản phẩm/dịch vụ liên quan nên chưa đổi được người phụ trách."`
- Nếu `newOwnerUserId` trùng với `ownerUserId` hiện tại hoặc không thỏa `eligibleOwner` cho `related_offering_id` của Customer: trả `422 VALIDATION_ERROR` tại `newOwnerUserId`.
- Nếu người mới không đủ điều kiện với Offering chính hoặc bất kỳ dòng Offering của N cơ hội đang mở thuộc người cũ: trả 422 tại `newOwnerUserId` với thông báo `Người phụ trách mới chưa được phân công sản phẩm/dịch vụ của {N} cơ hội đang mở trên hồ sơ này.`; không thay đổi Customer, cơ hội, Task, assignment, handover hoặc audit.
- DEC-11 “không đổi owner qua PATCH” giữ nguyên.

Thực thi trong cùng một transaction (sau khi khóa dòng Customer `FOR UPDATE`):
1. Cập nhật `owner_user_id` của Customer sang `newOwnerUserId`.
2. Kết thúc phân công `SALES` đang `ACTIVE` trên `customer_assignments` của Customer này (`status = 'ENDED'`, `effective_to = now`) và chèn dòng `customer_assignments` mới (`assignment_type = 'SALES'`, `status = 'ACTIVE'`, `source = 'HANDOVER'`, `handover_request_id = handoverId`, `user_id = newOwnerUserId`, `assigned_by_user_id = actor`).
3. Chuyển các Cơ hội của Customer đang ở stage `NEW`, `QUALIFIED`, `PROPOSAL` có owner là người phụ trách cũ sang `newOwnerUserId`.
4. Chuyển các Nhiệm vụ (`crm_tasks`) của Customer đang ở trạng thái `OPEN`, `IN_PROGRESS` có owner là người phụ trách cũ sang `newOwnerUserId`. Các việc đã đóng hoặc thuộc owner khác giữ nguyên.
5. Ghi 1 dòng `handover_requests` với `handover_type = 'SALES'`, `status = 'APPROVED'`, `requested_by_user_id = actor`, `reviewed_by_user_id = actor`, `target_user_id = newOwnerUserId`, `reason`, `reviewed_at = now`.
6. Ghi audit `OWNER_REASSIGNED` trên `CUSTOMER` (gồm ID owner cũ, ID owner mới, `handoverId`, số Cơ hội và số Nhiệm vụ đã chuyển; không lưu `reason` vào audit theo DEC-11) và audit đổi owner cho từng Cơ hội/Nhiệm vụ đã chuyển (chỉ lưu ID).

Response `200 OK` trả thông tin Customer sau khi đổi kèm `{handoverId, movedOpportunityIds, movedTaskIds}`:

```json
{
  "id": "00000000-0000-0000-0000-000000000001",
  "displayName": "Công ty Minh họa",
  "email": "contact@example.test",
  "phone": "+84901000111",
  "customerType": "BUSINESS",
  "address": null,
  "notes": null,
  "ownerUserId": "00000000-0000-0000-0000-000000000022",
  "ownerName": "Sale Mới",
  "relatedOfferingId": "00000000-0000-0000-0000-000000000033",
  "offeringCode": "OFF-DEMO",
  "offeringName": "Gói dịch vụ",
  "status": "ACTIVE",
  "createdAt": "2026-11-01T00:00:00Z",
  "updatedAt": "2026-11-08T10:00:00Z",
  "handoverId": "00000000-0000-0000-0000-000000000044",
  "movedOpportunityIds": ["00000000-0000-0000-0000-000000000055"],
  "movedTaskIds": ["00000000-0000-0000-0000-000000000066"]
}
```
