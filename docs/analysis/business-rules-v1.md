# Business Rules v1

## Vai trò của tài liệu

Đây là baseline nghiệp vụ dùng trực tiếp để thiết kế Domain Class Diagram, Data Dictionary, RBAC, API Contract và backend. Quy tắc trong tài liệu áp dụng cho MVP CRM đa ngành của TLCN.

**Owner nghiệp vụ:** Nguyễn Đức Thắng. **Review kỹ thuật:** Huỳnh Minh Tài. **Review dữ liệu/import:** Văn Phạm Thảo Nhi.

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

- Opportunity bắt buộc gắn với Customer hoặc Lead, một Offering và owner Sale hợp lệ. Trong TLCN mỗi Opportunity có đúng một Offering; Opportunity nhiều dòng sản phẩm thuộc hướng KLTN.
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
- Data Staff upload, chuẩn hóa và validate file. Các dòng hợp lệ chỉ được commit sau khi Manager/Admin phê duyệt lần nữa để tránh mất dữ liệu; dòng lỗi được trả về báo cáo để sửa. Việc duyệt là một bước duy nhất, không có workflow phê duyệt nhiều cấp.
- File import gốc, kết quả validate, actor, thời điểm và lỗi được lưu để truy vết.
- n8n chỉ gọi API, gửi thông báo và chạy lịch; không ghi trực tiếp database hoặc quyết định quyền.

## 8. Support request và Handover

- Support request thuộc một Customer, do Customer Care được giao xử lý. Trạng thái: `OPEN`, `IN_PROGRESS`, `ESCALATED`, `RESOLVED`, `CLOSED`. Mức ưu tiên: `LOW`, `MEDIUM`, `HIGH`, `URGENT`.
- Mỗi phản hồi được lưu thành lịch sử, không sửa nội dung đã gửi; có thể kèm tệp đính kèm.
- Chuyển cấp bắt buộc có lý do; yêu cầu sang `ESCALATED` và Manager trong team xử lý. Manager ghi quyết định rồi trả lại cho Customer Care.
- `RESOLVED` và `CLOSED` bắt buộc có kết quả xử lý.
- Sale đang phụ trách Customer mới được gửi yêu cầu bàn giao Customer đó, kèm lý do và loại bàn giao; có thể đề xuất người nhận.
- Handover có hai loại: `SALES` (chuyển owner Sale sang Sale khác) và `CARE` (giao Customer cho Customer Care chăm sóc). Luồng `CARE` do Thắng bổ sung đặc tả Use Case.
- Handover có trạng thái `PENDING`, `APPROVED`, `REJECTED`. Mỗi Customer chỉ có một yêu cầu `PENDING`. Người phụ trách giữ nguyên cho tới khi Manager duyệt; từ chối phải có lý do.
- Với loại `SALES`, người nhận phải là Sale đang hoạt động và có Sales Assignment `ACTIVE` với Offering liên quan tới Customer. Với loại `CARE`, người nhận phải là Customer Care đang hoạt động; khi duyệt, hệ thống tạo phân công chăm sóc cho người nhận.

## 9. Audit và vòng đời dữ liệu

- Audit log ghi actor, action, entity type, entity id, metadata an toàn và thời điểm.
- Audit áp dụng cho auth quan trọng, user/role, Offering, Sales Assignment, owner/stage, import, workflow, chuyển cấp và duyệt bàn giao, và mutation dữ liệu CRM.
- Không ghi password, token hoặc bí mật vào log.
- Dữ liệu nghiệp vụ dùng archive/inactive; không hard-delete Customer, Lead, Opportunity, Interaction, Feedback, Task, Assignment hoặc audit log trong MVP.

## 10. Ràng buộc phạm vi

Không triển khai cart, checkout, payment, voucher, inventory, vận chuyển, AI Assistant, RFM, Predictive CLV, Next Best Action, quản trị nhiều cấp, workflow phê duyệt nhiều cấp hoặc Opportunity nhiều dòng sản phẩm trong TLCN.
