# Opportunity API v1 — đề xuất W05

**Trạng thái:** chưa triển khai. Các quy tắc dưới đây là contract dự kiến theo yêu cầu W05; các điểm mâu thuẫn ở cuối tài liệu cần được giải quyết trước khi sử dụng.

## Scope và route dự kiến

Base URL `/api/v1/opportunities`; xác thực và error envelope theo [contract foundation](api-contract-foundation-v1.md). Danh sách phân trang `page`, `size`, lọc `search`, `stage`, `customerId`, `ownerUserId` (Manager/Admin), `expectedCloseFrom`, `expectedCloseTo`.

| Phương thức | Đường dẫn | Mục đích |
|---|---|---|
| GET | `/` | Danh sách theo phạm vi đọc |
| GET | `/owners` | Người phụ trách khác nhau trong phạm vi đọc |
| GET | `/owner-candidates?customerId=&offeringIds=` | Sales đủ điều kiện cho Customer và mọi Offering; Manager/Admin |
| GET | `/{id}` | Chi tiết |
| POST | `/` | Tạo, dành cho Sales/Manager |
| PATCH | `/{id}` | Sửa, cần `expectedUpdatedAt` |
| POST | `/{id}/win`, `/lose`, `/cancel`, `/reopen` | Chuyển trạng thái, cần `expectedUpdatedAt` |

Admin đọc toàn organization, được sửa và mở lại nhưng không tạo; Manager đọc theo team của ít nhất một dòng và ghi theo phạm vi được giao; Sales đọc bản ghi do mình phụ trách và subject trong phạm vi tương ứng. Data Staff trả `403 FORBIDDEN_SCOPE`; Customer Care không có permission trong dữ liệu hiện hành, trả `403 FORBIDDEN_PERMISSION`. Không phân biệt bản ghi ngoài scope, tenant khác và không tồn tại.

## Dữ liệu và tính tiền dự kiến

Request tạo gồm `customerId`, `title`, `expectedCloseDate`, `stage`, `notes`, `ownerUserId` (Manager) và `lines`; mỗi dòng gồm `offeringId`, `quantity`, `unitPrice`, `discountAmount`. Từ 1 đến 50 dòng không trùng Offering. Tiền tệ `VND`; backend tính `lineTotal = quantity × unitPrice − discountAmount`, làm tròn HALF_UP hai chữ số, và `expectedValue` là tổng các dòng. Client không được gửi `expectedValue` hoặc `lineTotal`. PATCH không nhận subject hay owner và thay toàn bộ dòng khi gửi `lines`.

Response dự kiến gồm `id`, `title`, `stage`, `expectedValue`, `currencyCode`, `expectedCloseDate`, `notes`, Customer hoặc Lead, owner, `lines` (Offering id/code/name, quantity, unitPrice, discountAmount, lineTotal), `finalValue`, `actualCloseDate`, `closingReason`, `closedAt`, `createdAt`, `updatedAt`. Tiền được biểu diễn bằng chuỗi thập phân.

## Giai đoạn và lỗi dự kiến

Stage mở `NEW → QUALIFIED → PROPOSAL` chỉ giữ nguyên hoặc tiến; `WON`, `LOST`, `CANCELLED` bị khóa. Win cần giá trị chốt và ngày thực tế không trong tương lai; lose/cancel cần lý do. Reopen cần permission `opportunity.reopen`, chuyển về `QUALIFIED` và xóa các trường chốt.

| HTTP | Mã | Trường hợp |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Field không hợp lệ hoặc không được phép; lỗi dòng dùng `lines[i].field` |
| 403 | `FORBIDDEN_PERMISSION`, `FORBIDDEN_SCOPE` | Thiếu quyền hoặc ngoài phạm vi |
| 409 | `OPPORTUNITY_CHANGED`, `OPPORTUNITY_LOCKED`, `OPPORTUNITY_STAGE_BACKWARD` | Dữ liệu đã đổi, trạng thái khóa hoặc lùi stage |
| 422 | `VALIDATION_ERROR` | `ownerUserId` không đủ điều kiện |

## FINDING cần quyết định

- D4 yêu cầu mọi Offering sau khi lưu thỏa `OfferingRepository.SALES_READ_SCOPE`; fragment này bắt buộc `o.status='ACTIVE'`. D5 lại cho giữ Offering cũ đã `INACTIVE`. Hai yêu cầu không thể cùng đúng cho Sales nếu giữ nguyên fragment. Chưa áp dụng ngoại lệ scope nào.
- Reopen yêu cầu giữ giá trị cũ trong audit `before`, trong khi quy tắc audit chỉ cho ghi stage/trạng thái và cấm ghi lý do. Cần xác định chính xác trường chốt nào được lưu trong audit trước khi triển khai thao tác này.
- `SalesAssignmentRepository` hiện tìm công việc mở qua `opportunities.offering_id`, tức chỉ Offering chính. Khi cho phép nhiều dòng, cần quyết định có chặn thu hồi assignment theo mọi dòng hay không; code hiện tại chưa áp dụng quy tắc đó.

Chưa bao gồm chuyển Lead, Interaction/Task hay dashboard.
