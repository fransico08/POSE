# Roles and Authorization v1

## Nguyên tắc authorization

Authorization của POSE CRM luôn kiểm tra theo thứ tự: authenticated user -> organization -> permission -> team scope -> record scope. Không có quyền mặc định. Frontend không được dùng để thay thế backend authorization.

**Owner:** Nguyễn Đức Thắng. **Review:** Huỳnh Minh Tài và Vân Phạm Thảo Nhi.

### Quy ước thuật ngữ

Tên hiển thị được dùng trong tài liệu nghiệp vụ và giao diện. Role code được dùng trong database, API, kiểm tra quyền và source code. Không tạo thêm một role code thứ hai chỉ vì cách gọi hiển thị khác nhau; ví dụ, `Sales Staff` là tên hiển thị của `SALES_REP`.

## 1. Role hệ thống

| Role code | Tên hiển thị | Mục tiêu |
|---|---|---|
| `ADMIN` | System Admin | Quản lý user, role, permission, cấu hình hệ thống và audit; hỗ trợ dữ liệu CRM khi có permission được cấp. |
| `MANAGER` | Manager | Quản lý team, Offering, Sales Assignment, Customer/Lead/Opportunity và kết quả trong team phụ trách. |
| `SALES_REP` | Sales Staff | Làm việc với Offering được assign, Customer/Lead/Opportunity thuộc phạm vi và Task của mình. |
| `CUSTOMER_CARE` | Customer Care Staff | Chăm sóc Customer được giao, ghi Interaction/Feedback và xử lý Task của mình. |
| `DATA_STAFF` | Data Staff | Upload, chuẩn hóa, validate import, theo dõi data quality và đọc dashboard theo quyền. |

## 2. Nhiều role và quyền hiệu lực

- Một user có thể đồng thời giữ nhiều role trong cùng một organization, ví dụ `SALES_REP` và `CUSTOMER_CARE`.
- Permission hiệu lực là hợp của permission thuộc các role được gán. Tuy nhiên, mọi request vẫn phải vượt qua organization scope và ít nhất một scope rule hợp lệ cho record đang thao tác.
- Không có role nào được phép tự gán role, permission hoặc quyền cao hơn cho chính user đó. Chỉ `ADMIN` có permission quản trị identity mới được thay đổi role/permission của user khác trong cùng organization.
- Việc có nhiều role không cho phép vượt organization scope, truy cập record ngoài scope, hay bỏ qua các rule khóa dữ liệu.

## 3. Ma trận quyền

Ký hiệu: `M` quản lý, `C` tạo, `R` xem, `U` cập nhật, `A` phê duyệt/điều phối, `-` không có quyền.

| Tài nguyên | ADMIN | MANAGER | SALES_REP | CUSTOMER_CARE | DATA_STAFF |
|---|---|---|---|---|---|
| User, Role, Permission | M organization | R team | R bản thân | R bản thân | R bản thân |
| Organization, Team | M organization | C/R/U team phụ trách | R team của mình | R team của mình | R theo quyền |
| Offering, Category, Attribute | M cấu hình | M team/đơn vị phụ trách | R Offering được assign | R theo quyền | R theo quyền |
| Sales Assignment | M | C/R/U/revoke trong team | R assignment của mình | - | R phục vụ đối soát |
| Customer, Lead | R/U hỗ trợ theo permission | C/R/U trong team | C/R/U khi có assignment + owner scope | R khi được giao | R dữ liệu import theo quyền |
| Opportunity | R/U hỗ trợ theo permission | C/R/U trong team, reopen khi cần | C/R/U khi có assignment + owner scope; không sửa record locked | R, không đổi stage/owner/giá trị | R phục vụ dashboard |
| Interaction, Feedback | R/U hỗ trợ theo permission | C/R/U trong team, gồm note `SALES_ONLY` | C/R/U trong scope; có thể tạo note `SALES_ONLY` | C/R/U trong scope nhưng chỉ xem note `CARE_VISIBLE` | R metadata phục vụ data quality, không đọc nội dung nhạy cảm |
| CRM Task | R/U hỗ trợ theo permission | C/R/U/assign trong team | C/R/U task của mình | C/R/U task của mình | R theo quyền |
| Customer 360 | R theo permission | R team scope | R assignment + owner scope | R customer được giao | R phục vụ data quality |
| Source System, Import Job | M cấu hình/A commit | R/A import team | - | - | C/R/U validate và retry job `FAILED` |
| Dashboard | R theo permission | R team scope | R KPI cá nhân | R KPI task | R dashboard/data quality |
| Audit Log | R organization | R audit team | - | - | R import/workflow scope |

## 4. Permission code

```text
identity.user.manage
identity.user.read
identity.role.manage
organization.team.manage
offering.manage
sales_assignment.manage
customer.read
customer.write
lead.read
lead.write
opportunity.read
opportunity.write
opportunity.reopen
interaction.read
interaction.write
interaction.sensitive.read
feedback.write
task.manage
customer_360.read
import.prepare
import.approve
import.retry
dashboard.read
audit.read
```

Role cấp permission mặc định. Mọi quyền đọc/ghi dữ liệu CRM tiếp tục phải vượt qua scope check của record.

## 5. Scope rule bắt buộc cho backend

- `MANAGER`: record thuộc team/đơn vị mà Manager đang quản lý. Manager không được tự thay đổi role hoặc permission của bản thân, không được sửa/xóa audit log và không truy cập dữ liệu ngoài team scope.
- `SALES_REP`: record có `ownerUserId` là Sale hoặc Task được giao cho Sale, đồng thời liên quan tới Offering có Sales Assignment `ACTIVE` của Sale. Sale không được tự tạo, tự gia hạn hoặc tự gán Offering/Sales Assignment cho chính mình.
- `CUSTOMER_CARE`: Customer/Interaction/Feedback/Task được giao cho user hoặc thuộc team chăm sóc được cấp. Customer Care không được xem Interaction note có `visibility = SALES_ONLY`, cũng không được thay đổi Opportunity, Sales Assignment hoặc owner Sale.
- `DATA_STAFF`: chỉ import/data quality/dashboard theo permission; không tự commit dữ liệu CRM. Data Staff chỉ retry Import Job đang `FAILED`; retry phải dùng cùng idempotency key, không được tạo commit trùng. Bản ghi nghi ngờ trùng hoặc có nguy cơ mất dữ liệu phải chuyển Manager phê duyệt, không tự merge.
- `ADMIN`: không tự có quyền vượt organization; quyền CRM hỗ trợ được cấp qua permission riêng.
- `Opportunity locked`: Opportunity ở trạng thái `WON` hoặc `LOST` là locked. Sale không được sửa record locked. Manager có `opportunity.reopen` mới được mở lại về `QUALIFIED`; thao tác này bắt buộc ghi audit.
- `Interaction note visibility`: Interaction note bắt buộc có `visibility` là `CARE_VISIBLE` hoặc `SALES_ONLY`. Backend lọc nội dung note theo permission và scope trước khi trả response.

Backend trả `403 Forbidden` khi user có role nhưng không đủ permission/scope. API không tiết lộ sự tồn tại của record ngoài organization hoặc scope của user.

## 6. Audit và test authorization

- Ghi audit cho đổi role/permission, Offering, Assignment, owner, Opportunity stage, reopen Opportunity, thay đổi visibility của note, import approval và workflow/import retry.
- Test bắt buộc: Sale truy cập Customer/Opportunity ngoài assignment; Manager truy cập record ngoài team; Manager tự nâng quyền; nhiều role chỉ cấp permission trong organization/scope hợp lệ; Customer Care đọc note `SALES_ONLY`; Customer Care đổi Opportunity stage; Sale sửa Opportunity locked; Data Staff commit import hoặc tự merge bản ghi rủi ro; retry Import Job/Workflow tạo dữ liệu trùng.
