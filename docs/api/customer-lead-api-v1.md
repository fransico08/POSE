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
| `422 VALIDATION_ERROR` | Owner Manager chọn không đủ điều kiện |
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

Opportunity mở của Lead chuyển sang Customer nếu owner của từng Opportunity thuộc scope Sales Customer đích; ngược lại giao dịch bị từ chối. Opportunity WON/LOST/CANCELLED, Task, Interaction và Feedback không chuyển. Không có hoàn tác; nguồn Lead của Opportunity đã chuyển tra qua audit. Cập nhật Customer, Lead, Opportunity và audit cùng transaction; audit không ghi contact hoặc ghi chú.

Không đổi owner và không triển khai Customer 360 hoặc import trong card này.
