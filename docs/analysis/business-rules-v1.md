# Business Rules v1

## Vai trò của tài liệu

Đây là baseline nghiệp vụ dùng trực tiếp để thiết kế ERD, RBAC, API Contract và backend. Quy tắc trong tài liệu áp dụng cho MVP CRM đa ngành của TLCN.

**Owner nghiệp vụ:** Nguyễn Đức Thắng. **Review kỹ thuật:** Huỳnh Minh Tài. **Review dữ liệu/import:** Vân Phạm Thảo Nhi.

## 1. Luồng nghiệp vụ chính

| Bước | Actor | Kết quả bắt buộc |
|---|---|---|
| Cấu hình Offering | Manager | Offering có category, trạng thái và thuộc tính mở rộng cần thiết. |
| Phân công Sale | Manager | Tạo Sales Assignment có Sale, Offering và khoảng hiệu lực. |
| Tiếp nhận khách hàng | Sale | Tạo Customer hoặc Lead có organization, nguồn, identity đã chuẩn hóa và owner. |
| Chăm sóc | Sale/Customer Care | Ghi Interaction, Feedback hoặc Task gắn với Customer/Lead. |
| Theo dõi cơ hội | Sale | Tạo/cập nhật Opportunity gắn với Customer/Lead và Offering. |
| Tổng hợp | Hệ thống | Customer 360 và Dashboard đọc dữ liệu gốc/projection có thể đối soát. |

## 2. Organization, role và phạm vi dữ liệu

- Mỗi bản ghi nghiệp vụ thuộc đúng một organization.
- User có role và có thể thuộc team.
- Manager chỉ quản lý dữ liệu của team/đơn vị được giao.
- Sale chỉ đọc hoặc cập nhật dữ liệu khi có Sales Assignment đang hiệu lực cho Offering liên quan và là owner của bản ghi hoặc hoạt động được giao.
- Customer Care chỉ thao tác Customer, Interaction, Feedback và Task được giao.
- Data Staff chuẩn bị và kiểm tra import; Manager hoặc Admin phê duyệt commit dữ liệu nghiệp vụ.
- Backend là lớp kiểm tra quyền cuối cùng; frontend chỉ hiển thị theo quyền.

## 3. Offering và Sales Assignment

- `Offering` là mô hình chung cho sản phẩm hoặc dịch vụ.
- Offering có trạng thái `DRAFT`, `ACTIVE` hoặc `INACTIVE`; chỉ Offering `ACTIVE` được dùng để tạo Lead hoặc Opportunity mới.
- Sales Assignment có trạng thái `ACTIVE`, `EXPIRED` hoặc `REVOKED`, `effectiveFrom` và `effectiveTo`.
- Sale không được tự tạo, sửa, thu hồi hoặc gia hạn Sales Assignment.
- Khi Assignment hết hạn hoặc bị thu hồi, Sale mất quyền xem và cập nhật danh sách dữ liệu thuộc Offering đó. Manager phải reassign các Lead, Opportunity và Task đang mở trước khi thu hồi nếu còn cần xử lý.
- Tạo, sửa, gia hạn, thu hồi hoặc hết hạn Assignment đều tạo audit event.

## 4. Customer và Lead

- Customer và Lead lưu `ownerUserId`, source system và external reference khi có.
- Một Customer có một primary owner tại một thời điểm; Customer có thể có nhiều Lead/Opportunity liên quan đến nhiều Offering.
- Email được chuẩn hóa bằng trim + lowercase; phone được chuẩn hóa theo một quy tắc dùng chung.
- Trùng chính xác email hoặc phone không tự tạo Customer mới. Bản ghi mâu thuẫn hoặc gần giống được đưa vào danh sách review, không tự gộp.
- Lead dùng các trạng thái `NEW`, `QUALIFIED`, `CONVERTED`, `DISQUALIFIED`.
- Lead được chuyển thành Customer khi Manager hoặc owner Sale xác nhận đủ thông tin; hệ thống tái sử dụng Customer khớp identity nếu tồn tại và liên kết toàn bộ Interaction, Task, Opportunity của Lead vào Customer đó.

## 5. Opportunity

- Opportunity bắt buộc gắn với Customer hoặc Lead, một Offering và owner Sale hợp lệ.
- Trạng thái Opportunity: `NEW`, `QUALIFIED`, `PROPOSAL`, `WON`, `LOST`.
- Sale chỉ chuyển Opportunity theo chiều tiến: `NEW -> QUALIFIED -> PROPOSAL -> WON|LOST`.
- `WON` hoặc `LOST` bắt buộc lưu closing reason; Manager có thể mở lại Opportunity về `QUALIFIED` khi cần.
- Thay đổi stage hoặc owner tạo audit event và có thể phát sinh Task follow-up qua workflow.

## 6. Interaction, Feedback và CRM Task

- Interaction và Feedback phải gắn với Customer hoặc Lead; không lưu activity chỉ bằng tên tự do.
- Interaction lưu type, channel, subject, content, outcome, occurredAt và owner.
- CRM Task lưu owner, priority, status, dueAt, result và nguồn tạo task.
- Task có trạng thái `OPEN`, `IN_PROGRESS`, `DONE`, `CANCELLED`.
- Sale/Customer Care chỉ sửa Task của mình hoặc được giao; Manager có thể điều phối Task trong team.
- Workflow tạo Task theo assignment, assignment Customer/Lead, Opportunity đổi stage, sắp quá hạn hoặc quá hạn. Mỗi command retry dùng idempotency key để không tạo Task trùng.

## 7. Customer 360, Dashboard, Import và Automation

- Customer 360 là read model: định danh, owner/team, Offering quan tâm, Lead, Opportunity, Interaction, Feedback, Task và metrics tổng hợp.
- Dashboard chỉ đọc số liệu từ dữ liệu giao dịch gốc hoặc projection đối soát được; không cho phép sửa metric trực tiếp.
- Import Job có trạng thái `RECEIVED`, `VALIDATING`, `REVIEW_REQUIRED`, `COMMITTED`, `FAILED`.
- Data Staff upload, chuẩn hóa và validate file. Các dòng hợp lệ chỉ được commit sau khi Manager/Admin phê duyệt; dòng lỗi được trả về báo cáo để sửa.
- File import gốc, kết quả validate, actor, thời điểm và lỗi được lưu để truy vết.
- n8n chỉ gọi API, gửi thông báo và chạy lịch; không ghi trực tiếp database hoặc quyết định quyền.

## 8. Audit và vòng đời dữ liệu

- Audit log ghi actor, action, entity type, entity id, metadata an toàn và thời điểm.
- Audit áp dụng cho auth quan trọng, user/role, Offering, Sales Assignment, owner/stage, import, workflow và mutation dữ liệu CRM.
- Không ghi password, token hoặc bí mật vào log.
- Dữ liệu nghiệp vụ dùng archive/inactive; không hard-delete Customer, Lead, Opportunity, Interaction, Feedback, Task, Assignment hoặc audit log trong MVP.

## 9. Ràng buộc phạm vi

Không triển khai cart, checkout, payment, voucher, inventory, vận chuyển, AI Assistant, RFM, Predictive CLV, Next Best Action hoặc quản trị nhiều cấp trong TLCN.
