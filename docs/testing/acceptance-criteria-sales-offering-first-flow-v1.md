# Acceptance Criteria và Test Case: Manager phân công Offering cho Sales

Ngày: 02/11/2026  
Trạng thái: Hoàn tất bản acceptance criteria/test case; sẵn sàng review nhóm  
Phạm vi: Luồng đầu tiên giữa UC-04 Phân công sản phẩm và UC-03 Sales xem Offering được phân công.

## 1. Mục tiêu

Chốt kết quả cần đạt và các ca kiểm thử cho luồng: Manager phân công một Offering đang hoạt động cho Sales trong phạm vi quản lý; Sales đăng nhập và chỉ đọc được Offering đang được phân công, còn hiệu lực.

Tài liệu này đặc tả hành vi và kết quả mong đợi. Nó không định nghĩa payload hoặc đường dẫn API Sales Assignment vì contract/API đó chưa có trong checkout hiện tại.

## 2. Nguồn yêu cầu và quy tắc dùng

- [Business Rules v1](../analysis/business-rules-v1.md), mục 3 và 11.1.
- [Roles and Authorization v1](../analysis/roles-authorization-v1.md), ma trận quyền và Addendum Offering.
- [Offering API v1](../api/offering-api-v1.md), quy tắc đọc Offering của Sales.
- [Wireframe Sales/Manager v1](../design/ui-ux/sales-manager-wireframes-v1.md), UC-03 và UC-04.

Quy tắc xác định quyền đọc Offering của Sales: Offering phải có `status='ACTIVE'`; Sales Assignment phải thuộc đúng Sales user, có `status='ACTIVE'`, `revoked_at IS NULL`, `effective_from <= now` và (`effective_to IS NULL` hoặc `effective_to >= now`).

## 3. Vai trò và điều kiện ban đầu

- **Manager:** đăng nhập trong organization hiện tại, có `sales_assignment.manage` và chỉ thao tác trong phạm vi team được quản lý.
- **Sales:** người dùng đang hoạt động, có role `SALES_REP`, permission `offering.read` và thuộc cùng team sở hữu Offering trong cùng organization.
- **Offering:** tồn tại, đang `ACTIVE`, thuộc organization hiện tại và team thuộc scope của Manager.
- **Sales Assignment:** gắn đúng Offering và Sales user; có ngày bắt đầu/kết thúc hiệu lực; trạng thái hiệu lực cho phép Sales đọc.
- Mọi dữ liệu dùng cho integration/E2E test phải là fixture của môi trường kiểm thử cô lập, không tạo seed thử nghiệm trên Supabase dùng chung.

## 4. Luồng chính

1. Manager mở màn hình Phân công sản phẩm.
2. Hệ thống chỉ cung cấp Offering trong team scope của Manager và Sales user đang hoạt động thuộc cùng team sở hữu Offering.
3. Manager chọn Offering đang hoạt động, chọn Sales và khoảng hiệu lực rồi xác nhận phân công.
4. Hệ thống kiểm tra quyền, scope và dữ liệu; lưu Sales Assignment và audit event; thông báo thao tác thành công.
5. Sales đăng nhập vào cùng organization và mở Sản phẩm & dịch vụ.
6. Hệ thống hiển thị Offering được phân công đang hoạt động và còn hiệu lực.
7. Sales mở chi tiết Offering; thông tin được hiển thị ở chế độ chỉ đọc.

## 5. Acceptance Criteria

| ID | Tiêu chí nghiệm thu | Kết quả mong đợi |
|---|---|---|
| AC-01 | Manager có `sales_assignment.manage`, quản lý team sở hữu Offering và chọn Sales ACTIVE thuộc đúng team đó. | Phân công liên kết đúng Sales, Offering và khoảng hiệu lực; có thông báo thành công và audit event. |
| AC-02 | Manager chọn Sales thuộc team khác với Offering, kể cả khi Manager quản lý cả hai team. | Backend từ chối bằng lỗi scope; không tạo hoặc sửa assignment. |
| AC-03 | Manager thao tác với Offering ngoài team scope hoặc record thuộc organization khác. | Backend trả `403 FORBIDDEN_SCOPE`; không tạo hoặc sửa assignment; không lộ dữ liệu ngoài scope. |
| AC-04 | Sales tải danh sách Offering. | Chỉ trả Offering `ACTIVE` có assignment cho chính Sales user, cùng team, trạng thái `ACTIVE`, chưa bị thu hồi và đang trong khoảng hiệu lực. |
| AC-05 | Sales mở chi tiết Offering nằm trong assignment hợp lệ của mình. | Trả chi tiết ở chế độ chỉ đọc. |
| AC-06 | Sales mở chi tiết Offering chưa được assign cho mình hoặc thuộc scope khác. | Backend trả `403 FORBIDDEN_SCOPE`; response không chứa chi tiết Offering. |
| AC-07 | Offering bị ngừng kích hoạt trong khi assignment còn hiệu lực. | Offering không xuất hiện và không mở được với Sales; assignment và lịch sử vẫn được giữ. |
| AC-08 | Assignment chưa bắt đầu, đã hết hạn hoặc đã bị thu hồi. | Offering không xuất hiện và không mở được với Sales qua assignment đó. |
| AC-09 | Sales thử tạo, sửa, kích hoạt hoặc ngừng kích hoạt Offering. | Giao diện không hiển thị thao tác quản lý; Backend trả `403 FORBIDDEN_PERMISSION` và không đổi dữ liệu. |
| AC-10 | Một thao tác phân công bị từ chối do quyền, scope hoặc validation. | Không phát sinh thay đổi dữ liệu hoặc audit event thành công giả. |

## 6. Test Case

| ID | Loại | Dữ liệu/điều kiện | Thao tác | Kết quả mong đợi |
|---|---|---|---|---|
| TC-01 | Integration/E2E — đường chính | Manager quản lý Team A; Offering ACTIVE thuộc Team A; Sales ACTIVE, role `SALES_REP`, thuộc Team A và có `offering.read` | Manager phân công Offering cho Sales; Sales tải danh sách và mở chi tiết | Manager nhận thành công; Sales thấy đúng Offering và xem được chi tiết chỉ đọc. |
| TC-02 | Authorization — Sale khác team | Manager quản lý cả Team A và B; Offering thuộc Team A; Sales ACTIVE thuộc Team B | Manager thử phân công Offering Team A cho Sales Team B | Backend từ chối do không cùng team; không tạo assignment. |
| TC-03 | Integration — chưa phân công | Offering ACTIVE nhưng không có assignment cho Sales A | Sales A tải danh sách; thử mở chi tiết nếu biết ID | Offering không nằm trong danh sách; chi tiết bị từ chối, không lộ dữ liệu. |
| TC-04 | Integration — phân công cho người khác | Offering ACTIVE được assign cho Sales B thuộc cùng team, không phải Sales A | Sales A tải danh sách và thử mở chi tiết | Sales A không thấy và không mở được Offering; Sales B vẫn thấy nếu assignment hợp lệ. |
| TC-05 | Integration — assignment chưa hiệu lực | `effective_from` nằm trong tương lai | Sales tải danh sách và thử mở chi tiết | Offering chưa hiển thị cho tới thời điểm bắt đầu hiệu lực. |
| TC-06 | Integration — assignment hết hạn | `effective_to` đã qua thời điểm hiện tại | Sales tải danh sách và thử mở chi tiết | Offering không hiển thị và không mở được theo assignment đã hết hạn. |
| TC-07 | Integration — assignment bị thu hồi | Assignment có trạng thái REVOKED hoặc `revoked_at` đã có giá trị | Sales tải danh sách và thử mở chi tiết | Offering không hiển thị và không mở được theo assignment bị thu hồi. |
| TC-08 | Integration — Offering inactive | Assignment còn hiệu lực nhưng Offering có trạng thái INACTIVE | Sales tải danh sách và mở chi tiết | Offering không hiển thị và không mở được; dữ liệu assignment vẫn còn. |
| TC-09 | Authorization — Manager ngoài team | Manager không quản lý team sở hữu Offering | Manager tạo hoặc xem assignment cho Offering đó | Backend trả `403 FORBIDDEN_SCOPE`; không tạo hoặc thay đổi assignment. |
| TC-10 | Authorization — khác organization | Offering hoặc Sales user thuộc tenant khác | Manager thử tạo assignment bằng ID ngoài tenant | Backend trả lỗi scope chung; không tiết lộ record ngoài tenant. |
| TC-11 | Authorization — Sales mutation | Đăng nhập bằng Sales | Mở chi tiết; thử gọi thao tác quản lý qua UI/API | UI không có nút quản lý; API trả `403 FORBIDDEN_PERMISSION`; dữ liệu không đổi. |
| TC-12 | Audit | Thao tác tạo/cập nhật/thu hồi assignment hợp lệ | Thực hiện mutation qua Manager | Mỗi mutation thành công có audit event cùng transaction theo quy tắc Sales Assignment. |
| TC-13 | Transaction | Ép thao tác assignment thất bại ở bước ghi audit trong môi trường kiểm thử | Tạo hoặc cập nhật assignment | Toàn bộ mutation rollback; không để assignment thay đổi mà thiếu audit. |

## 7. Dữ liệu kiểm thử tối thiểu

Chuẩn bị fixture cô lập có Manager quản lý Team A và Team B để tách kiểm tra team scope khỏi quy tắc cùng team; có ít nhất hai Sales trong cùng organization:

- Offering ACTIVE trong team Manager quản lý, assign cho Sales A và còn hiệu lực.
- Offering ACTIVE chưa assign.
- Offering ACTIVE thuộc Team A và Sales ACTIVE thuộc Team B để xác nhận assignment chéo team bị từ chối, kể cả khi Manager quản lý cả hai team.
- Offering ACTIVE assign cho Sales B thuộc đúng team sở hữu Offering.
- Offering ACTIVE với assignment tương lai, assignment hết hạn và assignment bị thu hồi.
- Offering INACTIVE có assignment còn hiệu lực.
- Offering hoặc team thuộc organization khác để kiểm tra tenant scope.

Không dùng tài khoản/mật khẩu thật trong fixture, ảnh chụp hoặc log. Không chạy các ca ghi dữ liệu trên database Supabase dùng chung.

## 8. Phụ thuộc triển khai và kiểm thử

1. Checkout hiện tại có UC-03 Offering và API đọc Offering theo `offering.read`, nhưng chưa có Sales Assignment API hoặc màn hình UC-04. Cần hoàn thành API/contract Assignment trước khi E2E thao tác phân công từ UI.
2. Người dùng đã chốt: Sales được phân công phải đang hoạt động và thuộc đúng team sở hữu Offering. Quy tắc này áp dụng ngay cả khi Manager quản lý nhiều team.
3. Khi có API contract Assignment, đồng bộ test fixture và mã lỗi validation/scope với contract; không tự thêm field hoặc endpoint vào kiểm thử trước khi có contract.

## 9. Kết quả backlog

- Luồng và quy tắc Sales phải cùng team với Offering đã được người dùng chốt.
- Có 10 Acceptance Criteria và 13 Test Case với actor, dữ liệu, thao tác và kết quả mong đợi.
- Phụ thuộc Sales Assignment API/UI được ghi lại để xếp backlog triển khai riêng.
- Việc chạy TC-01 tới TC-13 sẽ thực hiện sau khi API/UI Assignment sẵn sàng; evidence runtime được ghi riêng, không thuộc kết quả của backlog đặc tả này.
