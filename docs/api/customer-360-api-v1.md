# API Customer 360 v1

Các route chỉ đọc, yêu cầu phiên hợp lệ và trả `Cache-Control: no-store`; không ghi audit. Dữ liệu được giới hạn theo organization và scope hiện hành.

| Route | Quyền |
|---|---|
| `GET /api/v1/customers/{id}/360` | `customer_360.read`; Admin toàn tổ chức, Manager theo team, Sales theo owner/assignment SALES, Care theo assignment CARE, Data Staff toàn tổ chức chỉ metadata. Không có quyền hoặc ngoài scope trả 403 theo foundation. |
| `GET /api/v1/customer-360/reconciliation?page=0&size=20` | Admin hoặc Data Staff có `customer_360.read`; đối soát toàn organization, tối đa 100 dòng mỗi trang. |

## Hồ sơ

Response gồm `customer.profile` theo CustomerResponse, `customer.owner`, `customer.ownerTeams`, `customer.careAssignees`; `sources.count/systems`; các phần `leads`, `opportunities`, `interactions`, `tasks`, `feedback`, `support`; và `refreshedAt`. Dữ liệu nguồn chỉ gồm mã hệ thống, không trả external ID.

Ví dụ rút gọn (dữ liệu giả):

```json
{
  "customer": {"profile":{"id":"00000000-0000-0000-0000-000000000001","displayName":"Khách mẫu"},"owner":{"id":"00000000-0000-0000-0000-000000000002","name":"Nhân viên mẫu"},"ownerTeams":[],"careAssignees":[]},
  "sources":{"count":0,"systems":[]},
  "leads":{"restricted":true,"count":null,"items":[]},
  "opportunities":{"restricted":true,"stages":null,"openExpectedValue":null,"wonFinalValue":null,"items":[]},
  "interactions":{"restricted":false,"count":0,"lastInteractionAt":null,"items":[]},
  "tasks":{"restricted":false,"statuses":{"OPEN":0,"IN_PROGRESS":0,"DONE":0,"CANCELLED":0},"overdue":0,"items":[]},
  "feedback":{"restricted":false,"count":0,"averageRating":null,"items":[]},
  "support":{"restricted":true,"count":null,"averageRating":null,"items":[]},
  "refreshedAt":"2026-11-05T00:00:00Z"
}
```

Theo DEC-15, các số Interaction/Task/Opportunity dùng scope của API tương ứng; Interaction áp visibility và redaction W06. Actor có role Data Staff, kể cả khi có thêm role khác, chỉ nhận metadata trong profile 360: email, phone, địa chỉ, ghi chú Customer; nội dung Interaction/Task; tiêu đề và giá trị Opportunity đều null. Lý do: scope hợp role cho Data Staff đọc toàn tổ chức, nên trả đầy đủ cho actor kiêm vai trò khác sẽ lộ dữ liệu ngoài scope của vai trò kia. Opportunity của Data Staff đơn vai trò hiện `restricted` vì API Opportunity chưa cấp scope dù role có thể có permission; đây là FINDING cần quyết định. Feedback theo dòng “Interaction, Feedback” của ma trận quyền: cần `interaction.read`, scope Customer/Lead như Interaction và cùng quy tắc visibility (Care chỉ đếm `CARE_VISIBLE`); chỉ trả `count` và `averageRating` (làm tròn hai chữ số), không trả nội dung hay danh mục. Không suy quyền đọc từ `feedback.write`. Support chỉ là số đếm: Admin có `support_request.manage` xem trong tenant, Care xem yêu cầu được giao, Manager xem yêu cầu giao cho thành viên team quản lý; role/permission khác nhận restricted.

## Đối soát

`totals` là tổng trực tiếp của các cột đếm; `matched`, `mismatched` đếm Customer; `differences` phân trang theo foundation và mỗi phần tử chỉ gồm `customerId` cùng danh sách tên cột lệch. So sánh `opportunity_count`, `interaction_count`, `feedback_count`, `open_task_count`, `open_support_count`, `source_count`, `last_interaction_at`. Đối soát so toàn bộ dữ liệu của organization, không lọc visibility; không trả contact hoặc nội dung.

View `customer_360_summary` chỉ dùng cho đối soát, không cấp số đếm cho profile. Các bảng gốc được tính theo organization; view V1 đối chiếu theo Customer ID và chưa có điều kiện organization trong các subquery, vì vậy liên kết đa hình sai tenant có thể được phát hiện như một chênh lệch.
