# POSE CRM — Domain Class Diagram v1

**Phạm vi:** mô hình đối tượng cho TLCN/MVP CRM đa ngành. **Trạng thái:** bản thiết kế để rà soát, chưa chốt schema hay mã triển khai. **Phụ trách:** Huỳnh Minh Tài.

## 1. Cách đọc bộ sơ đồ

[Tệp draw.io chỉnh sửa được](domain-class-diagram-v1.drawio) là bản chính, gồm 6 trang. Mỗi trang có lớp, thuộc tính, hành vi, quan hệ và các ràng buộc quan trọng. Tách thành nhiều trang để nhìn rõ hơn; các hộp `<<reference>>` trỏ tới **cùng một lớp** đã mô tả ở trang khác, không phải lớp mới.

| Trang | Nội dung | Xem nhanh |
|---|---|---|
| 01 | Organization, Department, Team, User, tư cách thành viên, Role, Permission, Credential | [PNG](domain-class-diagram-v1-preview-01-access.png) |
| 02 | Offering sản phẩm/dịch vụ, thuộc tính linh hoạt, giá và Sales Assignment có thời hạn | [PNG](domain-class-diagram-v1-preview-02-offering.png) |
| 03 | Customer, Lead, Opportunity, định danh chuẩn hóa, liên kết nguồn và xử lý nghi trùng | [PNG](domain-class-diagram-v1-preview-03-sales.png) |
| 04 | Interaction, Feedback, CRM Task và Customer 360 chỉ đọc | [PNG](domain-class-diagram-v1-preview-04-care.png) |
| 05 | Import, phê duyệt, audit, outbox, workflow và các lớp điều phối ứng dụng | [PNG](domain-class-diagram-v1-preview-05-integration.png) |
| 06 | Yêu cầu hỗ trợ, phản hồi, tệp đính kèm và yêu cầu bàn giao khách hàng | [PNG](domain-class-diagram-v1-preview-06-support.png) |

Ký hiệu theo [UML 2.5.1 của OMG](https://www.omg.org/spec/UML/2.5.1/About-UML): `-` là thuộc tính được đóng gói, `+` là thao tác công khai; association là đường liền, composition có hình thoi đặc ở phía chủ sở hữu, realization là nét đứt với tam giác rỗng, dependency là nét đứt có mũi tên. Bội số được ghi ở **hai đầu** quan hệ; ví dụ `Organization 1 — 0..* User` nghĩa là mỗi User thuộc một Organization, một Organization có thể có nhiều User. `0..1` là tùy chọn; `1` là bắt buộc. Hình PNG phục vụ xem nhanh; khi cần xem đường nối rõ nhất, mở bản draw.io.

Các stereotype dùng trong sơ đồ:

- `<<entity>>`: đối tượng có định danh, trạng thái và vòng đời. `<<immutable entity>>` và `<<append-only>>` không cho sửa nội dung đã ghi.
- `<<value object>>`: giá trị đi theo đối tượng sở hữu, ví dụ ContactIdentity hoặc OfferingPrice; không dùng như hồ sơ độc lập.
- `<<interface>>`: hợp đồng hành vi, không hàm ý tạo bảng dữ liệu.
- `<<control>>`: lớp điều phối use case/kiểm tra quyền. Đây là trách nhiệm ở mức thiết kế, chưa buộc phải giữ nguyên tên lớp Java.
- `<<read model>>`: dữ liệu tổng hợp chỉ đọc, luôn truy được về nguồn.
- `<<reference>>`: một lớp đã định nghĩa ở trang khác.

## 2. Những quyết định mô hình hóa chính

### Tổ chức, phân quyền và phạm vi bản ghi (trang 01)

`Organization` là ranh giới dữ liệu. `Department` chứa `Team`; `TeamMembership` lưu lịch sử tham gia đội. `UserRoleGrant` lưu thời điểm cấp/thu hồi role và người cấp, thay cho quan hệ User–Role thuần túy. User có thể có nhiều role đang hiệu lực; quyền hiệu lực là hợp các Permission của role, sau đó vẫn phải qua kiểm tra organization, team và record scope. `Credential` tách khỏi hồ sơ User để tránh làm lẫn dữ liệu đăng nhập với dữ liệu nghiệp vụ. Manager quản lý team nhưng không tự cấp thêm quyền cho chính mình.

### Danh mục dùng cho nhiều ngành (trang 02)

`Offering` là sản phẩm **hoặc** dịch vụ, không tạo hai mô hình CRM riêng. `OfferingCategory` tổ chức nhóm; `OfferingAttributeDefinition` mô tả thuộc tính theo nhóm, `OfferingAttributeValue` giữ giá trị trên từng Offering. `OfferingPrice` mô tả giá trị và khoảng hiệu lực, chưa phải module thanh toán. `SalesAssignment` liên kết User–Offering kèm khoảng hiệu lực, người gán và trạng thái. Sale chỉ được xem hoặc xử lý Offering khi assignment còn hiệu lực; việc thu hồi cần xử lý công việc đang mở và ghi audit.

### Chủ thể CRM và vòng đời bán hàng (trang 03)

`CrmSubject` là giao diện chung cho `Customer` và `Lead`, giúp `Opportunity`, `Interaction`, `Feedback`, `CrmTask`, `SourceRecordLink` tham chiếu **đúng một** chủ thể mà không phải tạo hai quan hệ tùy chọn kèm XOR. Customer và Lead thực sự mang `organizationId` và `ownerId`; `CrmSubject` chỉ công bố các thao tác đọc/chuyển owner, không giữ field như một bảng riêng. `ContactIdentity` là giá trị được chuẩn hóa và thuộc Customer/Lead. Lead chuyển thành Customer có thể dùng lại Customer đã khớp; một Lead tối đa chuyển thành một Customer, còn một Customer có thể nhận nhiều Lead được chuyển.

`Opportunity` phải có một chủ thể CRM, một Offering và một owner Sale. Trong TLCN mỗi Opportunity chỉ có đúng một Offering; Opportunity nhiều dòng sản phẩm thuộc hướng KLTN. Stage tiến theo `NEW → QUALIFIED → PROPOSAL → WON/LOST`; đóng cơ hội cần lý do, mở lại là hành vi đặc quyền. `SourceRecordLink` giữ khóa định danh ngoài theo nguồn, cho phép một hồ sơ có nhiều nguồn. Nghi trùng đưa vào `DuplicateReviewCase`, không tự gộp. Quyết định nối/gộp phải truy được người duyệt.

### Chăm sóc và Customer 360 (trang 04)

Mọi Interaction, Feedback và CrmTask thuộc một `CrmSubject`; Opportunity chỉ là ngữ cảnh tùy chọn. Interaction có kênh, loại, nội dung, kết quả và thời điểm; Feedback ghi phân loại/nội dung/điểm đánh giá; Task có người nhận, hạn, ưu tiên, trạng thái và nguồn tạo. `NoteVisibility` tách nội dung chỉ Sale được xem khỏi nội dung dùng cho chăm sóc. `Customer360Summary` là read model gồm các chỉ số và thời điểm làm mới, không có thao tác sửa. Dashboard cũng là query/projection; không tạo entity Dashboard chỉ để minh họa màn hình.

### Import, audit và tự động hóa (trang 05)

`ImportJob` sở hữu các `ImportRow` và tối đa một `ImportDecision`; `DataValidationGateway` trả kết quả kiểm tra, còn `ImportApplicationService` mới điều phối phê duyệt và commit qua Backend. `AuthorizationPolicy` là nơi kiểm tra quyền và scope trước khi đọc/ghi. `AuditLog` ghi nối; nội dung sự kiện trong `OutboxEvent` bất biến sau khi tạo, còn trạng thái gửi có thể đổi. `WorkflowExecution` ghi trạng thái chạy/lỗi/retry và idempotency key. Dịch vụ workflow gửi lệnh tạo Task về Backend; công cụ tự động hóa không ghi trực tiếp PostgreSQL. Duyệt import là một bước duy nhất do Manager hoặc Admin thực hiện; không có workflow phê duyệt nhiều cấp trong TLCN.

### Hỗ trợ khách hàng và bàn giao (trang 06)

`SupportRequest` thuộc một Customer và một Customer Care phụ trách. Mỗi phản hồi là một `SupportResponse` không sửa được, có thể kèm `SupportAttachment`. Khi chuyển cấp, yêu cầu sang `ESCALATED` và Manager ghi quyết định rồi trả lại cho Customer Care. `HandoverRequest` do Sale đang phụ trách Customer tạo, có hai loại: `SALES` (chuyển owner sang Sale khác) và `CARE` (giao Customer cho Customer Care). Mỗi Customer chỉ có một yêu cầu `PENDING`; không có thay đổi nào cho tới khi Manager duyệt. Loại `SALES` yêu cầu người nhận có Sales Assignment `ACTIVE` với Offering liên quan; loại `CARE` yêu cầu người nhận là Customer Care và khi duyệt sẽ tạo phân công chăm sóc.

## 3. Ràng buộc kiểm thử được

| Mã | Quy tắc | Điểm kiểm thử tối thiểu |
|---|---|---|
| ACC-01 | Dữ liệu, membership và role grant không vượt ranh giới Organization | User không đọc/sửa hồ sơ organization khác, kể cả khi có cùng role |
| ACC-02 | Hợp Permission không bỏ qua record scope; cấm tự nâng quyền | Manager/Sale không cấp role hoặc scope mới cho chính mình |
| CAT-01 | Chỉ Offering `ACTIVE` nhận Lead/Opportunity mới | Offering `DRAFT`/`INACTIVE` bị từ chối |
| CAT-02 | Assignment phải `ACTIVE` và nằm trong khoảng hiệu lực | Sale mất quyền khi assignment hết hạn/thu hồi |
| CRM-01 | Opportunity có đúng một CrmSubject, một Offering, một owner hợp lệ | Thiếu một trong ba hoặc gán owner ngoài scope bị từ chối |
| CRM-02 | `WON`/`LOST` cần lý do; chỉ Manager được mở lại | Sale không sửa trực tiếp Opportunity đã đóng |
| CRM-03 | Email/phone chuẩn hóa trước đối chiếu; trùng chính xác không tạo hồ sơ âm thầm | Match chính xác trả về hồ sơ đã có hoặc luồng xác nhận |
| CRM-04 | Mã ngoài duy nhất theo Organization + source + loại bản ghi + externalId | Cùng mã không trỏ sang hai chủ thể; nghi trùng cần review |
| CARE-01 | Mọi hoạt động thuộc đúng một chủ thể; lọc nội dung `SALES_ONLY` | Customer Care không thấy nội dung nhạy cảm |
| CARE-02 | Một command workflow chỉ tạo một Task theo idempotency key | Retry/lặp webhook không tạo Task trùng |
| INT-01 | Data Service chỉ validate; Backend kiểm tra quyền, phê duyệt rồi mới commit | Data Staff không tự commit/merge bản ghi rủi ro |
| INT-02 | Import retry chỉ từ `FAILED`; audit/payload không bị sửa | Retry giữ key, lỗi và actor truy vết được |
| SUP-01 | Customer Care chỉ xử lý yêu cầu hỗ trợ được giao; chuyển cấp cần lý do và Manager nhận | Customer Care không mở được yêu cầu của người khác; chuyển cấp thiếu lý do bị từ chối |
| HND-01 | Một yêu cầu bàn giao `PENDING` mỗi Customer; chỉ đổi khi Manager duyệt; người nhận khớp loại `SALES` hoặc `CARE` | Gửi yêu cầu thứ hai bị từ chối; loại `SALES` mà người nhận không có assignment `ACTIVE` bị từ chối; loại `CARE` mà người nhận không phải Customer Care bị từ chối |

Tất cả đối tượng nghiệp vụ phải tôn trọng organization scope, dù quan hệ tới Organization không được lặp trên từng trang. Thuộc tính `?` nghĩa là có thể vắng mặt; việc nullable cụ thể trong database phải được quyết định ở Data Dictionary. Hạn chế hard delete đối với dữ liệu CRM, assignment và audit trong MVP.

## 4. Truy vết theo luồng sản phẩm

| Luồng/nhu cầu | Các lớp chính | Trang |
|---|---|---|
| Đăng nhập và cấp quyền | User, Credential, Role, Permission, UserRoleGrant, AuthorizationPolicy | 01, 05 |
| Cấu hình sản phẩm/dịch vụ đa ngành | Offering, Category, Definition, Value, Price | 02 |
| Gán phạm vi Sales | SalesAssignment, User, Offering, AuthorizationPolicy, AuditLog | 01, 02, 05 |
| Tiếp nhận và chuyển đổi Lead | Lead, Customer, ContactIdentity, SourceRecordLink, DuplicateReviewCase | 03 |
| Theo dõi cơ hội bán hàng | Opportunity, CrmSubject, Offering, User | 03 |
| Chăm sóc và theo dõi kết quả | Interaction, Feedback, CrmTask, Customer360Summary | 04 |
| Nạp dữ liệu và phê duyệt | SourceSystem, ImportJob, ImportRow, ImportDecision, ImportApplicationService | 05 |
| Tự động hóa có truy vết | OutboxEvent, WorkflowExecution, WorkflowCommandService, AuditLog | 05 |
| Hỗ trợ khách hàng và chuyển cấp | SupportRequest, SupportResponse, SupportAttachment | 06 |
| Bàn giao khách hàng | HandoverRequest, Customer, SalesAssignment | 06, 02 |

Không đưa cart, checkout, payment, voucher, inventory, vận chuyển, AI Assistant, RFM hoặc Predictive CLV vào sơ đồ TLCN hiện tại: các phần này nằm ngoài MVP theo Business Rules v1. Việc có thể mở rộng sau này không đồng nghĩa phải đưa lớp giả định vào hiện tại.

## 5. Ranh giới với database và các điểm cần chốt

Sơ đồ này **không phải ERD**. Nó mô tả trách nhiệm và quan hệ đối tượng. Data Dictionary/schema quyết định tên bảng/cột, kiểu PostgreSQL, khóa, index, unique, nullable, mapping class–table và chiến lược lưu `CrmSubject`. Tránh suy ra mỗi hộp class tương ứng một bảng: interface, value object, read model và control có thể không có bảng độc lập.

Các lựa chọn thiết kế cần xác nhận trước khi khóa migration/implementation:

1. Thuộc tính Offering có bắt buộc gắn ở cấp Category, hay cần định nghĩa riêng cho một Offering đặc thù? Sơ đồ hiện chọn cấp Category để tái sử dụng.
2. Giá Offering cần lưu lịch sử hiệu lực hay chỉ một giá hiện tại? Sơ đồ hiện giữ lịch sử giá để không làm sai dữ liệu quá khứ.
3. Một Lead có bắt buộc quan tâm đúng một Offering ngay khi tạo không? Sơ đồ hiện chọn một Offering; nếu Lead có thể đến trước khi chọn dịch vụ, đổi bội số thành `0..1`.
4. Phân loại `SALES_ONLY` có áp dụng cho Feedback hay chỉ Interaction? Sơ đồ hiện áp dụng cả hai để API lọc nhất quán.
5. Khi Lead chuyển Customer, có chuyển toàn bộ activity/source link vào Customer hay giữ liên kết nguồn rồi resolve khi đọc? Cần chọn một cách và kiểm thử không mất lịch sử.

Đây là các quyết định có tác động thật lên code/schema, không phải chỗ trống để tùy ý hoàn thiện sau. Chốt chúng trước khi dùng sơ đồ làm chuẩn triển khai.
