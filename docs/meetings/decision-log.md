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

## DEC-11 — Customer và Lead core

**Ngày: 03/11/2026. Owner: Huỳnh Minh Tài.**

- D1: Đọc dùng hợp scope các role khi có permission đọc hiệu lực; ghi cần một role vừa có permission ghi vừa có scope tương ứng. Sales chỉ thao tác Lead của mình nếu còn đọc được Offering; Customer theo owner hoặc SALES assignment hiệu lực. Manager theo team sở hữu Offering của Lead; với Customer theo membership hiệu lực của owner/assignee ở team ACTIVE được quản lý. Admin đọc/sửa trong tenant, không tạo với vai trò Admin. Care chỉ đọc Customer được giao CARE; Data Staff chưa có scope import. Manager tạo phải chọn owner là Sales ACTIVE đủ quyền đọc Offering. Không đổi owner qua PATCH.
- D2: V9 thêm trường hồ sơ nullable cho Customer/Lead và khóa ngoại Offering cùng tenant; không thay dữ liệu cũ.
- D3: NEW có thể qualify; NEW/QUALIFIED có thể disqualify với lý do. Lead đóng không sửa; convert hoãn W05.
- D4: Chặn trùng email/phone trong từng loại hồ sơ; Lead archived, `DISQUALIFIED` hoặc `CONVERTED` không chặn (khách cũ quay lại), Customer theo unique hiện có. Chỉ lộ ID trùng nếu actor được đọc bản trùng.
- Tài duyệt ngày 03/11/2026: `customer_type` (`INDIVIDUAL`, `BUSINESS`); `source` (`WEBSITE`, `REFERRAL`, `EVENT`, `SOCIAL_MEDIA`, `PHONE`, `WALK_IN`, `OTHER`); ghi chú/lý do tối đa 4000 ký tự, field tùy chọn không nhận chuỗi rỗng; qualify khi Lead không còn NEW trả `409 LEAD_STATE_CONFLICT`. `roles-authorization-v1.md` (scope Customer của Sales) đồng bộ theo D1 trong task đồng bộ tài liệu.

## DEC-12 — Opportunity theo UC-12

**Ngày: 03/11/2026. Owner: Huỳnh Minh Tài; agent đề xuất, Tài giao quyết định theo đề xuất.**

- D1: UC-12 thay DEC-01 trong TLCN: Opportunity có một hoặc nhiều dòng; `offering_id` là Offering dòng đầu, `expected_value` do backend tính và tiền tệ là VND.
- D2: V10 bổ sung dòng sản phẩm và các trường chốt; backfill mỗi cơ hội cũ thành một dòng, giữ nguyên `expected_value`.
- D3: Stage mở chỉ đi tiến NEW → QUALIFIED → PROPOSAL; WON/LOST/CANCELLED khóa, chỉ người có quyền reopen được đưa về QUALIFIED. Ngày kiểm theo múi giờ Việt Nam.
- D4: Scope đọc là hợp các role; ghi cần cùng role có permission và scope. Admin không tạo; Sales cần owner và subject trong scope, Manager theo team của Offering; Data Staff chưa có scope dashboard. Customer Care hiện không có permission Opportunity trong dữ liệu.
- D5: Từ 1 đến 50 dòng, không trùng Offering; tổng tính bằng BigDecimal, làm tròn HALF_UP hai chữ số; thay toàn bộ dòng khi lưu. Yêu cầu cho giữ Offering cũ đã INACTIVE trước đây cần làm rõ so với `SALES_READ_SCOPE`; **đã thay bằng F1 dưới đây**.
- Các mặc định từng chờ Tài xác nhận: chiết khấu tính bằng tiền, thêm CANCELLED, Opportunity mới bắt buộc gắn Customer và giới hạn 50 dòng; **đã xác nhận ngày 04/11/2026 bên dưới**. Quy tắc audit reopen trước đây cần làm rõ; **đã chốt tại F2 dưới đây**.

### Bổ sung 03/11/2026

- F1: Không có ngoại lệ giữ Offering INACTIVE: mọi dòng khi tạo hoặc PATCH phải ACTIVE, cùng tổ chức và thuộc scope ghi của cùng một role. Ngoài phạm vi, khác tổ chức, không tồn tại và INACTIVE trả cùng lỗi field `lines[i].offeringId`; Customer ngoài scope trả lỗi field `customerId`. Chốt và mở lại không kiểm tra lại dòng.
- F2: Audit reopen lưu `before` gồm `stage`, `finalValue`, `actualCloseDate`, `closedAt`; `after` chỉ gồm stage QUALIFIED. Không lưu lý do hay ghi chú; xóa các trường chốt khi mở lại.
- F3: Việc mở chặn thu hồi Sales Assignment nếu Offering thuộc bất kỳ dòng nào của Opportunity mở do Sales phụ trách; mỗi Opportunity chỉ liệt kê một lần. Task gắn Opportunity cũng kiểm Offering theo mọi dòng (sửa 04/11/2026).
- F4: Customer Care không được cấp permission Opportunity trong data; API theo data và giữ finding lệch ma trận roles để đồng bộ sau.
- F5: Offering mới thêm qua PATCH phải đủ điều kiện với owner hiện tại sau khi kiểm scope người sửa. Các Offering đã có trong cơ hội không kiểm lại assignment owner khi sửa dòng hoặc đổi vị trí; PATCH không gửi `lines` không kiểm lại owner.

### Hợp nhất bản backend song song của Thắng — 04/11/2026

- Commit `d960ba8` trên nhánh Thắng làm lại backend Opportunity song song với nhánh Tài: migration `V10__opportunity_pipeline_lines`, chiết khấu theo phần trăm, không có CANCELLED, chốt cơ hội bằng PATCH stage, ngày theo UTC.
- Tài chọn giữ bản của nhánh Tài: V10 `opportunity_lines_and_closing` đã áp lên Supabase, contract theo D1–D5 và F1–F5 ở trên, cùng DEC-13. Merge `66bdac1` không revert commit của Thắng; V10 thứ hai, các class thay thế và phần sửa test baseline `V1_2` không được đưa vào.
- Từ bản của Thắng, nhánh Tài giữ lại: giới hạn pool Hikari khi test, và tìm cơ hội theo tên Customer/Lead hoặc tên/mã Offering ở bất kỳ dòng nào (`4b85ca1`). Customer Care đọc cơ hội theo assignment CARE vẫn chờ quyết định F4.
- Từ 04/11/2026, card có nhiều người có checklist "Phân công" trên Trello; Tài làm phần FE khi làm chung một card.

### Tài xác nhận 04/11/2026

- Giữ CANCELLED theo UC-12 A1.3 “Hủy cơ hội”.
- Bắt buộc Customer khi tạo: Doc quy định một cơ hội gắn một khách hàng.
- Chiết khấu tính bằng **số tiền**: Doc không quy định đơn vị; Data Dictionary v1.1 ghi phần trăm, còn V2 FINAL không có bảng dòng. UI có thể quy phần trăm ra tiền.
- Tối đa 50 dòng là giới hạn kỹ thuật.

## DEC-13 — Chuyển Lead thành Customer (bổ sung ngoài Doc UC-09)

**Ngày: 04/11/2026. Owner: Huỳnh Minh Tài.** Căn cứ: business-rules §4 và UC-12 bắt buộc Customer; UC-09 trong Doc hiện chưa có bước chuyển.

- Sales/Manager cần cùng một role có `lead.write` và `customer.write` cùng scope ghi Lead; owner Lead còn đủ điều kiện Offering. Chỉ Lead QUALIFIED được chuyển với `expectedUpdatedAt` hiện tại; Admin không chuyển.
- So contact chuẩn hóa với Customer cùng tổ chức, kể cả archive. Không trùng thì tạo Customer theo Lead, có SALES assignment INITIAL; một bản chưa archive có owner Lead là owner/SALES assignee hiệu lực thì dùng lại không thay đổi. Các trường hợp còn lại trả conflict và chỉ lộ ID trùng khi actor được đọc.
- Chỉ chuyển Opportunity mở có owner thuộc scope Sales của Customer đích. Lead, Customer, Opportunity và audit đổi trong một transaction; audit không ghi contact hay ghi chú.
- Không có hoàn tác. Task, Interaction, Feedback và Opportunity đã chốt chưa chuyển; nguồn gốc Lead của Opportunity đã chuyển chỉ còn tra qua audit.
- Nội dung không chuyển Task/Interaction nêu trên được bổ sung và thay đổi theo DEC-14; Feedback và Opportunity đã chốt vẫn giữ tại Lead.
- Việc cần đồng bộ: Thắng/Tài bổ sung luồng A1.4 vào UC-09 trong Google Doc.

## DEC-14 — Interaction/Task khi chuyển Lead

**Ngày: 05/11/2026. Owner: Huỳnh Minh Tài.** Bổ sung DEC-13 theo business-rules-v1.md §4 về liên kết Interaction và Task sang Customer.

- Trong cùng transaction chuyển Lead, chuyển mọi Interaction và Task OPEN/IN_PROGRESS nếu không gắn Opportunity hoặc gắn Opportunity được chuyển trong chính lần này. Giữ Task DONE/CANCELLED và Interaction/Task gắn Opportunity không được chuyển tại Lead.
- Audit `LEAD_CONVERTED` ghi số lượng `movedInteractions`, `movedTasks`; không ghi nội dung, tiêu đề hoặc contact và không tạo audit riêng cho từng bản ghi.
- Không tạo Interaction/Task mới trên Lead CONVERTED hoặc DISQUALIFIED: trả `409 LEAD_CLOSED`. Bản ghi cũ vẫn đọc và xử lý theo quyền hiện có.

### Gán team cho Offering cũ (V8) và đối chiếu UC-04

**Ngày: 02/11/2026. Owner: Huỳnh Minh Tài.**

- Sau V5, 26 Offering trong dữ liệu demo có `team_id` rỗng nên Manager và Sales không thấy. Dữ liệu demo cho thấy mỗi tổ chức chỉ có một team `SALES` và mọi Sales được phân công đều thuộc team đó. Tài chọn gán Offering cũ vào team `SALES` ACTIVE của tổ chức bằng migration V8. Offering đã có team hoặc tổ chức không có team `SALES` ACTIVE được giữ nguyên để Admin gán qua ứng dụng.
- Đối chiếu UC-04 trong Google Doc: không mâu thuẫn với luật đọc theo membership/team và luật chặn phân công chồng. Khác biệt có từ trước, để xử lý khi đồng bộ repo theo Doc: Doc chọn một Sales rồi phân công/thu hồi nhiều sản phẩm một lần, không có khoảng hiệu lực, không chặn thu hồi khi còn việc mở, câu thông báo khác.

### Việc còn lại trước DEC-06

- Thắng cập nhật báo cáo "Task làm việc POSE" Chương 3:
  - UC-18: thêm bước Manager duyệt trước khi commit (DEC-02).
  - Thêm đặc tả luồng bàn giao `CARE` và sửa Use Case Diagram cho khớp (DEC-05).
  - Class Diagram: theo DEC-01 đến DEC-05.
- Thắng sửa tài liệu Roles-Authorization: mục 4 Data Staff "Thực hiện import khi dữ liệu đã vượt qua validation" đổi thành cần Manager duyệt trước khi commit (DEC-02).
- Chốt bộ enum trạng thái Lead, Task, Offering, Assignment khi đóng review Business Rules v1.
- Nhi sửa Data Dictionary, migration và seed theo góp ý trong `docs/reviews/`.
