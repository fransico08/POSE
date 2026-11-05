# API Opportunity v1

Ngày 03/11/2026 · Theo UC-12 và DEC-12. Base URL `/api/v1/opportunities`; xác thực, error envelope và pagination theo [contract foundation](api-contract-foundation-v1.md). Response thành công có `Cache-Control: no-store`.

## Scope

Đọc dùng hợp các role khi có `opportunity.read`; ghi cần một role vừa có permission tương ứng vừa có scope. Admin đọc/sửa/reopen toàn organization nhưng không tạo. Manager đọc và ghi Opportunity có ít nhất một dòng thuộc team ACTIVE do mình quản lý. Sales đọc/ghi Opportunity do mình phụ trách nếu Customer thuộc scope Sales hoặc Lead cũ thuộc scope Sales của Lead. Data Staff trả `403 FORBIDDEN_SCOPE`; Customer Care không có permission trong data nên trả `403 FORBIDDEN_PERMISSION`. ID không tồn tại, khác tenant hoặc ngoài scope cùng trả `403 FORBIDDEN_SCOPE`.

Tạo bắt buộc Customer. Sales làm owner; Manager gửi `ownerUserId` là Sales đủ điều kiện đọc **mọi** Offering và là owner hoặc SALES assignee hiệu lực của Customer. Owner sai trả `422` tại `ownerUserId`. Manager/Admin dùng `/owner-candidates` để chọn Sales; `/owners` chỉ trả các owner khác nhau trong read scope, sắp xếp tên. Customer ngoài scope tạo trả `400` tại `customerId` với thông báo chung.

Khi tạo hoặc PATCH, mọi dòng phải ACTIVE, cùng organization và thuộc scope ghi của cùng một role: Sales theo `SALES_READ_SCOPE`, Manager theo team ACTIVE mình quản lý, Admin theo organization. Không có ngoại lệ cho dòng cũ INACTIVE. Các trường hợp Offering không tồn tại, khác tenant, INACTIVE hoặc ngoài scope cùng trả `400`, field `lines[i].offeringId`, thông báo “Sản phẩm/dịch vụ không còn hoạt động hoặc không thuộc phạm vi của bạn.” Win/lose/cancel/reopen không kiểm tra lại dòng.

## Routes

| Method | Path | Mục đích |
|---|---|---|
| GET | `/` | Danh sách phân trang `page`, `size`; lọc `search` (tối đa 200 ký tự, không phân biệt hoa thường) theo tên cơ hội, tên Customer/Lead hoặc tên/mã Offering ở bất kỳ dòng nào, `stage`, `customerId` hoặc `leadId`, `ownerUserId` (Manager/Admin), `expectedCloseFrom`, `expectedCloseTo`. Dùng đồng thời `customerId` và `leadId` trả 400 tại field `leadId`. |
| GET | `/owners` | Owner distinct trong read scope, Manager/Admin |
| GET | `/owner-candidates?customerId=&offeringIds=a,b` | Sales đủ điều kiện với Customer và mọi Offering, Manager/Admin |
| GET | `/{id}` | Chi tiết gồm các dòng |
| POST | `/` | Tạo, Sales/Manager |
| PATCH | `/{id}` | Sửa, cần `expectedUpdatedAt` |
| POST | `/{id}/win`, `/lose`, `/cancel`, `/reopen` | Chuyển trạng thái, cần `expectedUpdatedAt` |

Ví dụ tạo (giá trị thập phân có thể gửi bằng chuỗi hoặc số JSON):

```json
{
  "customerId": "00000000-0000-0000-0000-000000000001",
  "title": "Cơ hội mẫu",
  "stage": "NEW",
  "expectedCloseDate": "2026-12-01",
  "notes": null,
  "lines": [{"offeringId":"00000000-0000-0000-0000-000000000002","quantity":"2.00","unitPrice":"1500000.00","discountAmount":"200000.00"}]
}
```

Response gồm `id`, `title`, `stage`, `expectedValue`, `currencyCode`, `expectedCloseDate`, `notes`; `customer` hoặc `lead` và `owner` dạng `{id,name}`; `lines[]` gồm `offeringId`, `offeringCode`, `offeringName`, `quantity`, `unitPrice`, `discountAmount`, `lineTotal`; cùng `finalValue`, `actualCloseDate`, `closingReason`, `closedAt`, `createdAt`, `updatedAt`. Các số lượng/giá trị tiền được trả bằng chuỗi thập phân; legacy có thể có `expectedValue` hoặc ngày dự kiến null.

## Tính tiền và giai đoạn

Từ 1 đến 50 dòng, không trùng Offering; thứ tự request xác định position từ 1 và Offering dòng đầu được lưu làm Offering chính. `quantity > 0`, `unitPrice >= 0`, tối đa hai chữ số thập phân. `discountAmount` là số tiền từ 0 đến `quantity × unitPrice`, mặc định 0. Backend tính `lineTotal = quantity × unitPrice − discountAmount`, làm tròn HALF_UP hai chữ số; `expectedValue` là tổng. Tiền tệ VND. Client không được gửi `expectedValue` hoặc `lineTotal`. Mỗi lần PATCH lưu lại toàn bộ dòng trong transaction; không nhận `customerId`, `leadId`, `ownerUserId`.

Khi PATCH có `lines`, Offering mới thêm phải được owner hiện tại phân công và đủ điều kiện đọc. Sau khi kiểm scope người sửa, nếu owner không đủ điều kiện thì trả `422` tại `lines[i].offeringId`. Offering đã có trong cơ hội không kiểm lại assignment của owner khi thay số lượng, giá hoặc vị trí; PATCH không gửi `lines` cũng không kiểm lại. Các dòng vẫn phải vượt kiểm tra ACTIVE và scope người sửa. Với cơ hội cũ có `expectedValue` null, lần lưu đầu tiên tính lại tổng các dòng và có thể thành `0.00`.

Stage mở `NEW → QUALIFIED → PROPOSAL` chỉ giữ nguyên hoặc đi tiến. Ngày dự kiến bắt buộc khi tạo, không trước ngày hiện tại ở `Asia/Ho_Chi_Minh`; PATCH chỉ kiểm ngày nếu thay đổi. WON, LOST, CANCELLED bị khóa. Win cần `finalValue` và `actualCloseDate` không trong tương lai; lose/cancel cần `reason` tối đa 4000 ký tự. Reopen cần `opportunity.reopen` và scope Manager/Admin, đưa về QUALIFIED, xóa các trường chốt. Audit reopen lưu stage, finalValue, actualCloseDate, closedAt cũ; không lưu notes/lý do. **Lý do chốt cũ không được giữ sau reopen.**

Audit và mutation cùng transaction. Thu hồi Sales Assignment bị chặn nếu Opportunity mở do Sales phụ trách có Offering ở bất kỳ dòng nào; danh sách công việc chỉ liệt kê Opportunity một lần.

## Lỗi

| HTTP/code | Trường hợp |
|---|---|
| `400 VALIDATION_ERROR` | Field bắt buộc, định dạng, dòng, Customer hoặc Offering không hợp lệ; field lỗi dòng là `lines[i].field` |
| `403 FORBIDDEN_PERMISSION`, `FORBIDDEN_SCOPE` | Thiếu permission hoặc scope |
| `409 OPPORTUNITY_CHANGED` | `expectedUpdatedAt` cũ |
| `409 OPPORTUNITY_LOCKED` | Sửa/chốt Opportunity đóng |
| `409 OPPORTUNITY_STAGE_BACKWARD` | PATCH lùi giai đoạn |
| `409 OPPORTUNITY_STATE_CONFLICT` | Reopen Opportunity đang mở |
| `422 VALIDATION_ERROR` | `ownerUserId` không đủ điều kiện |
| `422 VALIDATION_ERROR` | `lines[i].offeringId` mới thêm không được phân công cho owner hiện tại |

Chưa bao gồm chuyển Lead thành Customer, Interaction/Task hay dashboard.
