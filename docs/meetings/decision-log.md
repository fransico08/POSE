# Decision Log

Căn cứ chốt: tài liệu của task đã Hoàn thành trên Trello (Roles and Authorization v1, Architecture v1, API Contract Foundation v1) và phạm vi TLCN, KLTN trong `SRS POSE.docx`.

| Ngày | Mã | Quyết định | Căn cứ | Tài liệu đã cập nhật |
|---|---|---|---|---|
| 26/09/2026 | DEC-01 | Trong TLCN mỗi Opportunity có đúng một Offering và gắn Customer hoặc Lead. Opportunity nhiều dòng sản phẩm thuộc hướng KLTN. | SRS FR-06; Business Rules v1 mục 5 | Business Rules v1 mục 5, 10; Domain Class Diagram v1 trang 03 |
| 26/09/2026 | DEC-02 | Data Staff nhập và kiểm tra dữ liệu; Manager hoặc Admin duyệt lần nữa trước khi commit để tránh mất dữ liệu. Chỉ một bước duyệt, không có workflow phê duyệt nhiều cấp. Thắng xác nhận ngày 26/09/2026. | Roles and Authorization v1 dòng 45, 85; SRS Bảng 3 (Won't Have chỉ loại phê duyệt phức tạp) | Business Rules v1 mục 7; Domain Class Diagram v1 trang 05 |
| 26/09/2026 | DEC-03 | Support request và Handover thuộc phạm vi TLCN. | SRS FR-08; Bảng 3 (Should Have) | Business Rules v1 mục 8; Roles and Authorization v1 mục 7; Domain Class Diagram v1 trang 06 |
| 26/09/2026 | DEC-04 | Admin cơ bản, Permission, visibility cho ghi chú nhạy cảm, organization của User, outbox, workflow và thuộc tính Offering thuộc TLCN. Quản trị Admin/HR nhiều cấp thuộc KLTN. | SRS mục 3.1, 3.5, 3.7, Bảng 2, Bảng 5; mục II.3.5 | Đã có trong Roles and Authorization v1, Architecture v1, API Contract Foundation v1 |
| 26/09/2026 | DEC-05 | Handover có hai loại: `SALES` (Sale sang Sale, đã có trong UC-11) và `CARE` (Sale sang Customer Care, Thắng bổ sung). Cả hai cần Manager duyệt. Thắng xác nhận ngày 26/09/2026. | UC-11; tài liệu Roles-Authorization của Thắng (mục 2 "Bàn giao và báo cáo", mục 3 "Khách hàng") | Business Rules v1 mục 8; Roles and Authorization v1 mục 7; Domain Class Diagram v1 trang 06 |

## DEC-06 — hero coverflow và chuyển động landing

**Ngày quyết định: 30/10/2026. Owner: Huỳnh Minh Tài.** Triển khai tiếp tục ngày 31/10/2026 theo cập nhật quyền tìm ảnh.

- Thay hình mock bên phải và nền hero bằng coverflow sáu ngành; desktop có perspective/rotateY, mobile chỉ crossfade. H1 vẫn server-rendered và tĩnh; bộ mock giữ cho các nơi đang dùng.
- Bỏ nút dừng/tiếp tục ở hero, dải ngành và tab vai trò; bỏ demo picker Cơ hội/Hồ sơ. Quyết định này thay quy định “không 3D” và “nút dừng bắt buộc” trong landing-wireframe-v2.md; các mục cũ giữ làm lịch sử.
- **Tài chấp nhận ngoại lệ WCAG 2.2.2 Pause, Stop, Hide (mức A)** cho các nội dung tự chạy quá năm giây không còn nút dừng độc lập. Hover/focus không thay thế đầy đủ cơ chế dừng của tiêu chí này; landing không tuyên bố đạt toàn bộ WCAG 2.2 AA, dù audit tự động có thể đạt 100.
- Biện pháp giảm thiểu: dừng khi hover, focus trong vùng, ngoài viewport hoặc tab ẩn; tiếp tục sau khi hover/focus rời vùng. Reduced-motion không timer tự chuyển, không 3D, Giáo dục/nền tĩnh. RoleTabs dừng tự chuyển đến khi trang được tải lại sau khi người dùng chọn bằng click hoặc phím.
- Không package mới, không đổi app/auth/font/token; chỉ transform/opacity chuyển tiếp 600–800 ms. Ảnh static import/next/image, chỉ khung đầu tải ngay, không preload/fetchPriority cao; kiểm ngân sách ảnh mobile 150 KB và LCP phải là chữ.
- Cập nhật 31/10: được tìm/tải ảnh miễn phí từ link Download chính thức của Unsplash/Pexels, kiểm JPEG/kích thước và ghi giấy phép. Sau khoảng ba lượt tìm vẫn không đạt tiêu chí logo/chữ/mặt người thì dùng CSS/SVG; Tài duyệt ảnh trong báo cáo cuối và có quyền yêu cầu thay.

## DEC-07 — đính chính hero: giữ mock trắng, ảnh ngành làm nền

**Ngày quyết định: 31/10/2026. Owner: Huỳnh Minh Tài.**

- Đính chính phần đầu của DEC-06: Tài muốn **giữ nguyên mock trắng bên phải** (pipeline, Customer 360, toast, picker Cơ hội/Hồ sơ) như trước DEC-06. Ảnh ngành chỉ **thay nền navy phía sau hero**, không thay mock.
- Nền hero gồm: ảnh ngành đang chọn được làm mờ phủ toàn hero và dải sáu khung ảnh chạy ở khoảng trống phía dưới cột chữ trên desktop (từ 1024 px), mock trắng nằm đè phía trước. Mobile/tablet dưới 1024 px chỉ có ảnh nền mờ, không dải khung.
- Giữ nguyên từ DEC-06: bỏ nút dừng ở hero, dải ngành và RoleTabs; quy tắc dừng khi hover/focus/ngoài viewport/tab ẩn; reduced-motion tĩnh; RoleTabs khóa sau khi người dùng chọn; **ngoại lệ WCAG 2.2.2 mức A**. Picker Cơ hội/Hồ sơ được khôi phục vì là control chọn nội dung, không phải nút dừng.
- Header có nền tối đặc ngay khi nội dung hero cuộn xuống dưới nó, để link menu không đè lên mock trắng; sau hero vẫn chuyển nền sáng như cũ.
- Bán lẻ/F&B tiếp tục dùng SVG nội bộ như DEC-06 ghi nhận.
- **Bổ sung cùng ngày (Tài duyệt):** dải khung nhỏ dưới cột chữ đổi thành **coverflow lớn phủ cả hero** trên desktop (khung cao gần bằng hero), lớp phủ tối đậm phía cột chữ và nhạt dần sang phải. Nền ảnh **không dừng khi rê chuột** vì phủ toàn hero (hover sẽ làm nền đứng yên suốt lúc xem); vẫn dừng khi focus bàn phím trong hero, ngoài viewport, tab ẩn và reduced-motion. Mock trắng và cụm “Dành cho…” giữ quy tắc dừng khi hover. Desktop chấp nhận LCP là ảnh nền; mobile giữ LCP là chữ và không tải ảnh lớn.

## DEC-08 — Supabase làm PostgreSQL dùng chung cho giai đoạn backend

**Ngày quyết định: 01/11/2026. Thắng đề xuất, Huỳnh Minh Tài duyệt.**

- Dùng một project Supabase (PostgreSQL 17, region Singapore) làm database dùng chung khi phát triển backend. Sau này dùng cho demo; khi deploy sẽ quyết định tách project demo riêng hay dùng chung.
- Dữ liệu nạp từ backup final v2 của Nhi bằng `backend/scripts/setup-supabase-v2.ps1`: kiểm SHA-256, restore vào database trống, chạy verifier gói và assertions foundation, baseline Flyway version 1 rồi V2–V4, kiểm 6 organization/36 user/sáu mã đăng nhập. Không sửa gói Nhi và migrations V1–V4.
- Đóng Data API của Supabase với bảng CRM: thu hồi quyền của `anon`/`authenticated`, bật RLS cho mọi bảng `public`, khuyến nghị tắt Data API trong dashboard. Backend kết nối trực tiếp Postgres qua session pooler cổng 5432, không dùng transaction pooler 6543.
- Tài giữ thông tin đăng nhập, chia sẻ qua kênh an toàn; không đưa mật khẩu/connection string có mật khẩu vào Git, chat, log hoặc evidence. Agent không giữ hay nhập mật khẩu Supabase.
- Test làm thay đổi dữ liệu vẫn chạy trên cluster cô lập `127.0.0.1:54329`; migration mới phải đạt ở đó trước khi chạy lên Supabase.

## DEC-09 — cập nhật UC-03 Offering và UC-12 Opportunity

**Ngày quyết định: 01/11/2026.** Nguồn: Word mới do người dùng cung cấp; các quyết định phân quyền/team tiếp tục theo câu trả lời đã xác nhận trong cuộc hội thoại.

- Ưu tiên UC-03 và UC-12 của Word mới làm nguồn yêu cầu hiện hành; giữ DEC-01 và các phần baseline cũ trong lịch sử.
- UC-03 yêu cầu Category bắt buộc dù Class Diagram hiện hành ghi tùy chọn. UI/API triển khai theo UC; người dùng sẽ cập nhật Class Diagram sau. Không tự sửa diagram trong backlog này.
- Offering thuộc một Team; Manager chỉ quản lý team được giao. Trạng thái hiển thị là active/inactive, còn storage hiện tại tiếp tục dùng ACTIVE/INACTIVE/DRAFT; không mass-update DRAFT. Giá tham khảo dùng offering_prices để giữ lịch sử.
- Migration không đoán team cho Offering cũ. Team ID để nullable cho các row legacy chưa được ánh xạ; không đưa row đó vào Manager scope. Admin có thể gán một Team ACTIVE cùng tenant. Offering tạo/cập nhật từ ứng dụng phải có team.
- Bổ sung `offering.read` cho Sales theo assignment đang hiệu lực; không cấp `offering.manage` cho Sales.
- UC-12 mới cho phép một hoặc nhiều OpportunityLine và tính Expected Value từ các dòng. Backlog hiện tại chỉ cập nhật wireframe/user flow, không triển khai Opportunity.

## DEC-10 — Google Doc là baseline Use Case và Class Diagram; tích hợp nhánh Thắng

**Ngày quyết định: 02/11/2026. Owner: Huỳnh Minh Tài.**

- Use Case và Class Diagram trong Google Doc "Task làm việc POSE" (Chương 3) là baseline nghiệp vụ hiện hành. Tài liệu trong repo (`business-rules-v1.md`, `roles-authorization-v1.md`, `domain-class-diagram-v1.md`, ERD) đang lệch so với Doc và sẽ được đồng bộ theo Doc trong một việc riêng. Trong lúc chờ đồng bộ, khi repo và Doc mâu thuẫn thì làm theo Doc và ghi lại điểm lệch; DEC-09 (UC-03, UC-12) nằm trong hướng này. Agent không tự sửa Google Doc.
- Điểm cần chốt khi đồng bộ: UC-12 trong Doc cho phép nhiều OpportunityLine, khác DEC-01 (TLCN một Offering mỗi Opportunity). Tài quyết định giữ hay thay DEC-01 trước khi triển khai Opportunity.
- Nhánh `Thắng` (3 commit: Offering CRUD + wireframe, acceptance criteria, Sales Assignment; migration V5–V7) được tạo từ đầu nhánh `Tài` (`0ab5b15`) và `Tài` không có commit riêng, nên tích hợp bằng **fast-forward** vào `Tài`, không tạo merge commit. `Tài` là nhánh tích hợp; `main` và `Nhi` đã nằm trọn trong `Tài`. Chưa đưa lên `main` cho đến khi nhóm review.
- Kiểm trước khi tích hợp: suite PostgreSQL trên cluster `54329` 61 đạt/13 skip (acceptance cần mật khẩu demo)/0 lỗi, gồm 5 test Offering; rehearsal script Supabase áp baseline 1 + V2–V7 lên bản restore thật, Hibernate validate, RLS/quyền API đạt; frontend lint/typecheck/131 test/build đạt. Sales Assignment chưa có test tự động.
- Lần đầu chạy backend từ `Tài` mới lên Supabase, Flyway tự áp V5–V7 (chỉ thêm cột/permission/ràng buộc, không tạo bảng mới). Người chạy đầu tiên là Tài.

### Quyết định sửa phạm vi Sales và assignment chồng thời gian

**Ngày: 02/11/2026. Owner: Huỳnh Minh Tài.** Sales chỉ đọc Offering ACTIVE trong tenant khi assignment ACTIVE chưa thu hồi còn hiệu lực, đồng thời membership ACTIVE còn hiệu lực tại đúng team sở hữu ACTIVE; team null không được đọc. Mất membership hoặc team INACTIVE làm mất quyền đọc ngay, không thu hồi assignment. Từ chối tạo/gia hạn nếu khoảng đóng giao với assignment ACTIVE chưa thu hồi khác cùng organization/Offering/Sales; `effectiveTo=null` là vô hạn, trả `409 SALES_ASSIGNMENT_OVERLAP`. Kiểm tra trong transaction sau khóa organization, không thêm migration.

### Việc còn lại trước DEC-06

- Thắng cập nhật báo cáo "Task làm việc POSE" Chương 3:
  - UC-18: thêm bước Manager duyệt trước khi commit (DEC-02).
  - Thêm đặc tả luồng bàn giao `CARE` và sửa Use Case Diagram cho khớp (DEC-05).
  - Class Diagram: theo DEC-01 đến DEC-05.
- Thắng sửa tài liệu Roles-Authorization: mục 4 Data Staff "Thực hiện import khi dữ liệu đã vượt qua validation" đổi thành cần Manager duyệt trước khi commit (DEC-02).
- Chốt bộ enum trạng thái Lead, Task, Offering, Assignment khi đóng review Business Rules v1.
- Nhi sửa Data Dictionary, migration và seed theo góp ý trong `docs/reviews/`.
