# API Interaction, CRM Task và Audit Log W06

Contract này mô tả hành vi backend W06 hiện được triển khai trong nhánh `Thắng`. Các API dùng Spring Boot, `JdbcTemplate`, quyền và phạm vi dữ liệu hiện có của POSE CRM. Tất cả endpoint cần phiên đăng nhập hợp lệ và trả `Cache-Control: no-store`.

W06 cung cấp lõi ghi nhận Interaction, quản lý Task thủ công và tra cứu audit cho hai loại dữ liệu này. Tạo Task tự động từ workflow/import, giao diện frontend và API feedback không nằm trong phạm vi này.

## Routes

| Method | Path | Chức năng | Quyền và phạm vi |
|---|---|---|---|
| `GET` | `/api/v1/interactions` | Danh sách Interaction có lọc và phân trang | `interaction.read`, subject scope |
| `GET` | `/api/v1/interactions/{id}` | Chi tiết Interaction | `interaction.read`, subject scope và visibility |
| `POST` | `/api/v1/interactions` | Tạo Interaction | Manager, Sales hoặc Customer Care có `interaction.write` và subject scope; actor là người ghi |
| `PATCH` | `/api/v1/interactions/{id}` | Cập nhật field cho phép | Admin, Manager, Sales hoặc Customer Care có `interaction.write`, subject scope, `expectedUpdatedAt` |
| `GET` | `/api/v1/tasks` | Danh sách Task có lọc và phân trang | `task.manage`, subject/owner scope |
| `GET` | `/api/v1/tasks/{id}` | Chi tiết Task | `task.manage`, subject/owner scope |
| `POST` | `/api/v1/tasks` | Tạo Task thủ công ở trạng thái `OPEN` | Manager, Sales hoặc Customer Care có `task.manage` và subject scope |
| `PATCH` | `/api/v1/tasks/{id}` | Cập nhật nội dung, ưu tiên hoặc hạn | `task.manage`, owner/team scope, `expectedUpdatedAt` |
| `POST` | `/api/v1/tasks/{id}/assign` | Giao lại Task | Manager hoặc Admin có `task.manage`; owner phải đủ điều kiện |
| `POST` | `/api/v1/tasks/{id}/start` | Bắt đầu Task | Owner hoặc người quản lý có scope |
| `POST` | `/api/v1/tasks/{id}/complete` | Hoàn tất Task | Owner hoặc người quản lý có scope |
| `POST` | `/api/v1/tasks/{id}/cancel` | Hủy Task | Owner hoặc người quản lý có scope |
| `GET` | `/api/v1/audit` | Tìm sự kiện Interaction/Task | Admin toàn organization; Manager trong team scope; cần `audit.read` |
| `GET` | `/api/v1/audit/{id}` | Xem sự kiện Interaction/Task | Cùng quyền và scope như danh sách audit |

Không có API xóa Interaction, Task hoặc Audit Log. API không nhận `organizationId`, actor, người ghi, người tạo hay `origin` từ client; server lấy các giá trị này từ phiên đăng nhập và đặt Task mới là `MANUAL`.

## Interaction

### Tạo

`POST /api/v1/interactions` nhận các field:

```json
{
  "customerId": "uuid",
  "leadId": null,
  "opportunityId": null,
  "type": "CALL",
  "channel": "PHONE",
  "subject": "Trao đổi nhu cầu",
  "content": "Khách muốn nhận tư vấn vào tuần tới.",
  "outcome": null,
  "visibility": "CARE_VISIBLE",
  "occurredAt": "2026-11-04T08:00:00Z"
}
```

- Phải gắn đúng một trong `customerId` hoặc `leadId`; subject phải tồn tại, chưa archive và nằm trong scope của người dùng.
- `opportunityId` là tùy chọn. Nếu truyền, Opportunity phải cùng organization và cùng Customer/Lead.
- `type`, `channel` là mã tối đa 50 ký tự, chuẩn hóa thành chữ in hoa; định dạng mã là chữ cái đầu, sau đó là chữ/số/dấu gạch dưới. Repo chưa áp đặt danh mục nghiệp vụ cố định.
- `subject` bắt buộc, tối đa 220 ký tự. `content` bắt buộc, `outcome` tùy chọn; mỗi field tối đa 4.000 ký tự.
- `visibility` nhận `CARE_VISIBLE` hoặc `SALES_ONLY`. Customer Care chỉ tạo/đọc/sửa Interaction `CARE_VISIBLE`. Đọc/ghi `SALES_ONLY` cần permission `interaction.sensitive.read` gắn với vai trò Admin, Manager hoặc Sales.
- `recordedByUserId` và `recordedByName` do server xác định. Thành công trả `201`.

`PATCH /api/v1/interactions/{id}` chỉ nhận `type`, `channel`, `subject`, `content`, `outcome`, `visibility`, `occurredAt` và `expectedUpdatedAt`. Không đổi Customer/Lead, Opportunity hoặc người ghi. Nếu phiên bản hiện tại khác `expectedUpdatedAt`, trả `409 INTERACTION_CHANGED`. PATCH không tạo audit mới nếu giá trị không đổi.

Danh sách hỗ trợ `page` (mặc định `0`), `size` (mặc định `20`, tối đa `100`), `customerId`, `leadId`, `opportunityId`, `type`, `channel`, `visibility`, `occurredFrom`, `occurredTo`. Không thể lọc đồng thời Customer và Lead; thời điểm bắt đầu phải nhỏ hơn hoặc bằng thời điểm kết thúc.

Data Staff có `interaction.read` chỉ nhận metadata: `subject`, `content`, `outcome` được trả `null`. `SALES_ONLY` không xuất hiện trong danh sách/chi tiết của người không có quyền đọc nhạy cảm, ngoại trừ Data Staff chỉ nhận metadata.

## CRM Task

### Tạo

`POST /api/v1/tasks` nhận:

```json
{
  "customerId": "uuid",
  "leadId": null,
  "opportunityId": null,
  "ownerUserId": "uuid",
  "title": "Gọi lại khách hàng",
  "description": null,
  "priority": "NORMAL",
  "dueAt": "2026-11-05T02:00:00Z"
}
```

- Task phải gắn đúng một Customer hoặc Lead. Opportunity, nếu có, phải cùng organization và cùng subject.
- `title` bắt buộc, tối đa 220 ký tự. `description` tùy chọn, tối đa 4.000 ký tự. `priority` nhận `LOW`, `NORMAL`, `HIGH`, `URGENT`. `dueAt` bắt buộc; có thể là thời điểm quá hạn.
- Sales và Customer Care chỉ tạo Task của chính mình trong scope; Manager phải chọn owner hợp lệ trong team quản lý. Owner phải là Sales/Customer Care đang hoạt động và được phép xử lý subject đó.
- Server đặt `status=OPEN`, `origin=MANUAL`, `createdByUserId` theo actor. Thành công trả `201`.

`PATCH /api/v1/tasks/{id}` chỉ nhận `title`, `description`, `priority`, `dueAt`, `expectedUpdatedAt`. Thay owner phải dùng `/assign`; đổi trạng thái phải dùng command tương ứng. Conflict phiên bản trả `409 TASK_CHANGED`. No-op không tạo audit.

### Trạng thái và command

```text
OPEN ── start ──> IN_PROGRESS
 │                    │
 ├── complete ────────┴──> DONE
 └── cancel ──────────┴──> CANCELLED
```

`DONE` và `CANCELLED` là trạng thái cuối; không có thao tác mở lại. `complete` yêu cầu `expectedUpdatedAt` và `result` không rỗng (tối đa 4.000 ký tự); server ghi `completedAt`. `cancel` yêu cầu `expectedUpdatedAt` và `reason` không rỗng (tối đa 4.000 ký tự); lý do được lưu ở `cancellation_reason`, tách khỏi `result`.

`assign` nhận `ownerUserId` và `expectedUpdatedAt`. Manager/Admin chỉ giao cho user Sales/Customer Care đang hoạt động, đủ điều kiện theo Customer/Lead; Manager chỉ chọn thành viên đang hoạt động thuộc team mình quản lý. Giao lại Task đã `DONE`/`CANCELLED` bị từ chối.

Danh sách Task hỗ trợ `page`, `size`, `customerId`, `leadId`, `opportunityId`, `ownerUserId`, `status`, `priority`, `dueFrom`, `dueTo`. Giá trị `status` hợp lệ: `OPEN`, `IN_PROGRESS`, `DONE`, `CANCELLED`. Hai filter Customer/Lead không dùng đồng thời; `dueFrom` không lớn hơn `dueTo`.

## Scope và lỗi

- Backend lấy tenant từ actor, sau đó áp scope cho Customer/Lead, Opportunity, owner và Team. Manager chỉ thao tác subject thuộc team quản lý; Sales chỉ trong phạm vi Customer/Lead hiện hành; Customer Care chỉ Customer được giao. ID ngoài tenant/scope không được phân biệt với dữ liệu không tồn tại.
- Admin không vượt organization hiện tại. Trong W06, Admin cập nhật Interaction, đọc/cập nhật/giao Task và đọc Audit Log theo permission; tạo Interaction/Task dành cho Manager, Sales hoặc Customer Care. Data Staff không được quyền ghi Interaction/Task; nội dung được che nếu họ có quyền xem metadata.
- Lỗi field trả `400 VALIDATION_ERROR` hoặc `422 VALIDATION_ERROR` kèm `fieldErrors`. Thiếu permission trả lỗi `403` theo API foundation; sai trạng thái/concurrency trả `409`.
- Mọi list dùng pagination của foundation (`page=0`, `size=20`, tối đa `100`). Response có `Cache-Control: no-store`.

## Audit Log

Audit W06 chỉ truy vấn `entityType=INTERACTION|CRM_TASK`. Filter: `entityType`, `entityId`, `action`, `actorUserId`, `occurredFrom`, `occurredTo`, `page`, `size`; thứ tự mới nhất trước. Admin đọc trong organization; Manager đọc event của record thuộc team mình quản lý. Dù Data Staff có `audit.read` cho import/workflow theo role matrix, API W06 hiện chỉ cung cấp Interaction/Task nên endpoint này chỉ cho Admin/Manager.

Các event được ghi:

| Action | Entity | Khi nào |
|---|---|---|
| `INTERACTION_CREATED` | `INTERACTION` | Tạo Interaction thành công |
| `INTERACTION_UPDATED` | `INTERACTION` | Có thay đổi thực tế |
| `TASK_CREATED` | `CRM_TASK` | Tạo Task thành công |
| `TASK_UPDATED` | `CRM_TASK` | Thay đổi nội dung/hạn/ưu tiên |
| `TASK_ASSIGNED` | `CRM_TASK` | Đổi owner |
| `TASK_STARTED` | `CRM_TASK` | Chuyển sang `IN_PROGRESS` |
| `TASK_COMPLETED` | `CRM_TASK` | Chuyển sang `DONE` |
| `TASK_CANCELLED` | `CRM_TASK` | Chuyển sang `CANCELLED` |

Audit được ghi trong cùng transaction với thay đổi dữ liệu. Nếu insert audit lỗi, thao tác nghiệp vụ rollback. Snapshot chỉ chứa mã/type/visibility/trạng thái/owner/ưu tiên/thời điểm hoặc tên field đổi; không lưu nội dung Interaction, mô tả Task, kết quả hoàn tất, lý do hủy, token hay thông tin liên hệ. API không có update/delete audit.

## Migration và kiểm chứng

`V11__interaction_task_audit_core.sql` giữ nguyên dữ liệu hiện có, thêm `crm_tasks.cancellation_reason`, composite foreign key để ngăn liên kết chéo organization và index cho truy vấn Interaction, Task, Audit. Nếu phát hiện dữ liệu tham chiếu không hợp lệ, Flyway dừng migration thay vì tự sửa record. Migration V1–V10 không bị thay đổi. Permission code và role grant W06 đã có trong bộ dữ liệu POSE hiện hành.

Kiểm chứng W06: integration test `InteractionTaskAuditIntegrationTest` chạy **9/9 trên H2** và **9/9 trên PostgreSQL 18.6** trong cluster cô lập `127.0.0.1:54329`. Kiểm thử bao phủ CRUD, validation, visibility/permission, team/owner scope, Task lifecycle, audit redaction, rollback khi audit lỗi và migration V11. Toàn suite `mvn verify` của repo cũng đạt trong cluster đó. Supabase không được dùng trong test.
