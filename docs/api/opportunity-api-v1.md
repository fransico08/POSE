# Opportunity Pipeline API v1

Ngày: 04/11/2026 · Contract backend cho backlog W05 — Xây dựng Opportunity pipeline và trạng thái nghiệp vụ.

Contract này dùng Opportunity có một Customer và một hoặc nhiều dòng Offering. Mỗi dòng giữ Offering, số lượng, đơn giá và chiết khấu phần trăm; `expectedValue` do backend tính từ các dòng. Contract bổ sung nhiều dòng cho dữ liệu CRM hiện có nhưng không giả định được đơn giá của dữ liệu cũ.

## 1. Quy ước và phạm vi

- Base path: `/api/v1/opportunities`; JSON camelCase; UUID ID; ngày dùng `yyyy-MM-dd`; timestamp dùng ISO-8601 UTC; response có `Cache-Control: no-store`.
- Organization lấy từ actor hiện tại; client không gửi `organizationId`.
- Danh sách dùng `{items,page,size,totalItems,totalPages}`. `page` bắt đầu từ 0; `size` mặc định 20, tối đa 100. Scope được áp trước đếm và phân trang.
- Không có thao tác xóa Opportunity trong contract này. `WON` và `LOST` là trạng thái đóng; không thêm trạng thái `CANCELLED`.
- Dữ liệu Lead-linked cũ vẫn đọc được. Tạo mới ở contract này yêu cầu Customer.

## 2. Quyền và phạm vi

| Actor | Điều kiện | Phạm vi |
|---|---|---|
| Sales | `opportunity.read` hoặc `opportunity.write` tương ứng | Chỉ Opportunity do chính Sale sở hữu; tất cả Offering trong từng dòng phải đang hoạt động và được phân công hiệu lực cho Sale trong team sở hữu Offering. Sale chỉ tạo cho chính mình. |
| Manager | `opportunity.read` hoặc `opportunity.write` tương ứng | Opportunity chỉ hiển thị khi mọi Offering trong các dòng thuộc team ACTIVE do Manager quản lý. Khi tạo/chuyển người phụ trách, Sale phải đủ điều kiện cho tất cả Offering. |
| Admin | `opportunity.read` hoặc `opportunity.write` tương ứng | Trong organization hiện tại. |
| Customer Care | `opportunity.read` | Chỉ đọc Opportunity của Customer đang được giao CARE hiệu lực. Không tạo/sửa/chuyển giai đoạn. |

Backend kiểm tra permission, role, organization, team, Customer và Sales Assignment cho từng mutation. Ẩn nút ở frontend không thay thế kiểm tra này.

## 3. Routes

| Method | Path | Mục đích |
|---|---|---|
| `GET` | `/opportunities` | Danh sách trong scope |
| `GET` | `/opportunities/{id}` | Chi tiết trong scope |
| `POST` | `/opportunities` | Tạo Opportunity ở trạng thái `NEW` |
| `PATCH` | `/opportunities/{id}` | Cập nhật thông tin, dòng Offering hoặc chuyển giai đoạn |
| `POST` | `/opportunities/{id}/reopen` | Manager mở lại Opportunity đã đóng về `QUALIFIED` |

### Danh sách

`GET /opportunities` nhận `page`, `size`, `search`, `stage`, `ownerUserId` tùy chọn. `search` tìm theo tên Opportunity, tên Customer hoặc tên/mã Offering. `stage` nhận `NEW`, `QUALIFIED`, `PROPOSAL`, `WON`, `LOST`. Lọc theo owner không mở rộng scope hiện có.

Mỗi Opportunity trả các trường `id`, `title`, `customerId`, `customerName`, `leadId`, `leadName`, `ownerUserId`, `ownerName`, `stage`, `expectedCloseDate`, `expectedValue`, `currencyCode`, `actualValue`, `actualCloseDate`, `notes`, `closingReason`, `lines`, `createdAt`, `updatedAt`.

### Tạo Opportunity

```json
{
  "title": "Triển khai CRM cho Công ty Ánh Dương",
  "customerId": "uuid",
  "ownerUserId": "uuid",
  "expectedCloseDate": "2026-12-15",
  "notes": "Khách cần hoàn tất trước cuối năm.",
  "lines": [
    { "offeringId": "uuid", "quantity": 2, "unitPrice": 6900000, "discountPercent": 10 },
    { "offeringId": "uuid", "quantity": 1, "unitPrice": 500000, "discountPercent": 0 }
  ]
}
```

`customerId`, `title`, `expectedCloseDate` và ít nhất một dòng là bắt buộc. `ownerUserId` có thể bỏ với Sales và khi đó Opportunity thuộc Sales hiện tại; Manager/Admin phải cung cấp Sale đủ điều kiện. `stage` nếu được gửi chỉ nhận `NEW`.

Mỗi dòng yêu cầu `offeringId`, `quantity` là số nguyên từ 1 đến 1.000.000, `unitPrice` không âm và tối đa hai chữ số thập phân, `discountPercent` từ 0 đến 100 và tối đa hai chữ số thập phân. Tối đa 50 dòng. Offering phải ACTIVE, thuộc team ACTIVE và đang được phân công hợp lệ cho Sale phụ trách.

Backend tính `lineValue = quantity × unitPrice × (1 − discountPercent / 100)`, làm tròn HALF_UP đến hai chữ số thập phân; `expectedValue` là tổng `lineValue`. Client không được đặt `expectedValue` hoặc `lineValue`. Tiền tệ mới dùng `VND`.

`expectedCloseDate` không được trước ngày UTC hiện tại. Tạo thành công trả `201` và ghi `OPPORTUNITY_CREATED` cùng transaction.

### Cập nhật và chuyển giai đoạn

`PATCH /opportunities/{id}` nhận `expectedUpdatedAt` bắt buộc để chống ghi đè dữ liệu mới. Các trường cần đổi gồm `title`, `customerId`, `ownerUserId`, `expectedCloseDate`, `notes`, `lines`, `stage` và trường chốt tương ứng. Chỉ trường gửi lên được cập nhật. Gửi `lines` thay thế toàn bộ các dòng; bỏ trường này giữ nguyên dòng hiện tại.

Luồng giai đoạn hợp lệ: `NEW → QUALIFIED → PROPOSAL → WON|LOST`. Không nhảy cóc hoặc sửa record `WON`/`LOST`. `WON` bắt buộc `closingReason`, `actualValue` không âm và `actualCloseDate` không ở tương lai. `LOST` bắt buộc `closingReason`; không nhận thông tin giá trị chốt. Thành công ghi `OPPORTUNITY_STAGE_CHANGED`; cập nhật khác ghi `OPPORTUNITY_UPDATED`.

### Mở lại

`POST /opportunities/{id}/reopen` yêu cầu `opportunity.write`, role Manager và `opportunity.reopen`, cùng body:

```json
{ "expectedUpdatedAt": "2026-11-04T10:00:00Z" }
```

Chỉ Opportunity `WON` hoặc `LOST` trong team scope mới được mở lại. Kết quả chuyển về `QUALIFIED`, xóa thông tin chốt hiện tại và ghi `OPPORTUNITY_REOPENED` cùng transaction. Lịch sử audit trước đó được giữ.

## 4. Lỗi

Các lỗi dùng error envelope `{code,message,fieldErrors,traceId}` của foundation.

| HTTP/code | Khi dùng |
|---|---|
| `400 VALIDATION_ERROR` | Thiếu/sai dữ liệu; field lỗi có đường dẫn như `lines[0].discountPercent` |
| `403 FORBIDDEN_PERMISSION` | Thiếu role/permission |
| `403 FORBIDDEN_SCOPE` | Customer, Opportunity, Offering hoặc Sale ngoài scope/assignment |
| `404 RESOURCE_NOT_FOUND` | Chỉ dùng khi endpoint con thực sự không tồn tại theo quy tắc chung; record ngoài scope không được tiết lộ |
| `409 OPPORTUNITY_CHANGED` | `expectedUpdatedAt` cũ |
| `409 OPPORTUNITY_LOCKED` | Cố sửa Opportunity `WON`/`LOST` |
| `409 OPPORTUNITY_STAGE_CONFLICT` | Chuyển giai đoạn không hợp lệ |
| `409 OPPORTUNITY_STATE_CONFLICT` | Mở lại Opportunity chưa đóng |
| `422 VALIDATION_ERROR` | Sale phụ trách không đủ điều kiện |

## 5. Lưu trữ và migration

- Flyway V10 thêm `notes`, `actual_value`, `actual_close_date` và bảng `opportunity_lines`; V1–V9 không sửa.
- Mỗi Opportunity cũ được backfill thành một dòng `legacy_value_only=true`. Dòng giữ Offering cũ và tổng giá trị dự kiến hiện có; `unit_price` để `NULL` vì dữ liệu nguồn không có đơn giá/số lượng/chiết khấu để suy ra.
- `opportunities.offering_id` được giữ để tương thích với quan hệ cũ; từ V10, các dòng trong `opportunity_lines` là nguồn chuẩn cho Offering và giá trị từng dòng.
- Sales Assignment tính Opportunity mở theo `EXISTS` trên các dòng, nên nhiều dòng thuộc cùng Offering hoặc nhiều Offering không nhân đôi số hồ sơ.
- Migration và regression suite được chạy trên H2 trong lượt triển khai; PostgreSQL chỉ được kiểm tra trên cluster cô lập `127.0.0.1:54329`. Không chạy migration trên Supabase dùng chung trong lượt này.
