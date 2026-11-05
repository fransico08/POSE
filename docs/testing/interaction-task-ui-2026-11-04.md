# Giao diện Interaction và CRM Task — 04/11/2026

Làm việc tại `D:\POSE`, nhánh `Tài`, từ `2c4c6df`. Dùng skill `frontend-ui-engineering`, component và token hiện có; không thêm package hoặc thay backend.

| Phạm vi | Kết quả |
|---|---|
| Route | `/app/tuong-tac` và `/app/nhiem-vu`, cùng `/them`, `/[id]`, `/[id]/chinh-sua` cho mỗi module. |
| API và quyền | Qua BFF; query key có tổ chức/người dùng. Quyền từ phiên kết hợp role theo contract; backend quyết định scope. Owner Manager lấy từ `/users`, lọc role/ACTIVE và phân trang. |
| Giờ | Input `dd/MM/yyyy HH:mm` theo `Asia/Ho_Chi_Minh`, chuyển sang UTC; test có ca qua nửa đêm. |
| Interaction | Có danh sách/lọc, tạo, chi tiết và sửa; nội dung null được hiển thị là bị ẩn. Các mã loại/kênh chỉ là gợi ý UI, vẫn cho nhập mã đúng định dạng. |
| Task | Có danh sách/lọc, tạo, chi tiết/sửa và dialog start/complete/cancel/assign. Trạng thái đóng không hiện sửa hoặc action. |
| Test | 22 test W06 trong `tests/work-ui.test.tsx`; toàn bộ 24 file, **252/252 test đạt**. Kiểm permission menu/tạo, visibility, redaction, lỗi field, conflict, giờ, lọc URL, cấu trúc mobile card, hành động Task và focus/Esc dialog. |
| `npm run typecheck`, `npm run lint` | Đạt. |
| `npm run build` | Đạt; cảnh báo `SITE_URL` chưa cấu hình như trước. |

**FINDING:** API Opportunity không hỗ trợ lọc `leadId`; request qua BFF có `leadId` trả HTTP 200 cùng các bản ghi khác Lead. Bộ chọn Opportunity đối với Lead bị vô hiệu hóa. Người không có `opportunity.read` cũng không thể tải bộ chọn Opportunity. Xem [evidence QA](interaction-task-access-2026-11-04.md).

**Giới hạn:** Chưa kiểm UI bằng trình duyệt, screen reader, bàn phím toàn trang hoặc responsive trên thiết bị thực; không tuyên bố đạt toàn bộ WCAG. Test RTL không thay thế acceptance UI. Một số ô trong ma trận quyền chưa được kiểm riêng; phần còn lại và kết quả thực tế được ghi ở evidence QA.

## Bổ sung 05/11/2026

QA trên bản sao W06 đã xác nhận Data Staff có thể đọc metadata Interaction nhưng `GET /customers` và `GET /leads` qua BFF đều trả `403 FORBIDDEN_SCOPE`, phù hợp scope CRM hiện hành. Chưa có API danh sách đối tượng phù hợp để chọn bộ lọc; bộ chọn hiển thị rõ việc không có quyền tải danh sách. Đây là giới hạn phụ thuộc API, không phải yêu cầu nới quyền. Các thao tác theo bảy vai trò được bổ sung trong [evidence QA](interaction-task-access-2026-11-04.md#bổ-sung-ma-trận--05112026).

Đã thêm một test cho thông báo 403 của bộ chọn. Sau khi dừng các dịch vụ QA: `npm run typecheck`, `npm run lint`, `npx vitest run` **253/253 test trên 24 file** và `npm run build` đạt. Một lượt Vitest chạy đồng thời với dịch vụ QA trước đó có hai test cũ hết thời gian chờ; chạy lại sau khi dừng dịch vụ đạt toàn bộ. Không đủ bằng chứng để kết luận nguyên nhân của hai timeout. Build vẫn cảnh báo thiếu `SITE_URL`.

## Khắc phục review — 05/11/2026

| Mục | Thay đổi và kiểm chứng |
|---|---|
| R1 | Interaction và Task trả thêm tên Customer, Lead và tiêu đề Opportunity bằng JOIN cùng organization, giữ các ID cũ. Integration test kiểm tra list, detail, command, Customer Care và metadata Data Staff. |
| R2 | Opportunity list nhận `leadId` trong scope đọc hiện có; dùng đồng thời Customer/Lead trả 400 tại `leadId`. Integration test kiểm lọc, ngoài scope và tham số xung đột. FINDING bộ lọc Lead ở trên đã được xử lý. |
| R3 | Danh sách bảng và thẻ mobile hiển thị đối tượng, chi tiết/form sửa hiển thị tên và tiêu đề. Bộ chọn tải cơ hội theo Lead; dữ liệu thiếu trường tên vẫn hiển thị an toàn. RTL bổ sung 4 test. |

Kiểm chứng thực tế tại checkout `D:\POSE`, nhánh `Tài`, dùng skill `frontend-ui-engineering`:

| Lệnh | Kết quả |
|---|---|
| `npm run typecheck`, `npm run lint`, `npm run build` | Đạt; build cảnh báo `SITE_URL` chưa cấu hình. |
| `npx vitest run` | 24 file, **257/257** đạt; mốc trước 253/253. |
| Maven `verify` với H2, JDK 25.0.2 | Surefire: **189 test, 172 đạt, 17 skip, 0 lỗi**; mốc 187/170 đạt. Maven biên dịch lại 89 file mã và 17 file test. |
| `scripts/test-postgres-v2.ps1` với gói V2 FINAL, cluster 127.0.0.1:54329 | Surefire: **189 test, 176 đạt, 13 skip, 0 lỗi**; mốc 187/174 đạt. Script báo `Verified.`, đối soát dữ liệu và dừng cluster; kiểm tra lại 54329 không lắng nghe. |

**Giới hạn kiểm chứng backend:** Lệnh xóa riêng `backend/target/classes` và `backend/target/test-classes` trước khi chạy bị cơ chế kiểm duyệt tự động từ chối với lý do “blocked by policy”. Vì vậy cả hai kết quả backend ở trên là kết quả chạy thật nhưng **không đáp ứng điều kiện chạy sau khi dọn class**; cần chạy lại trên bản build sạch khi môi trường cho phép. Các biến database/test nhạy cảm được kiểm tra là không có trong process trước khi chạy. Không kết nối Supabase hoặc cổng 5432.

Chưa kiểm UI bằng trình duyệt, screen reader hoặc responsive thực tế; test RTL không chứng minh toàn bộ WCAG. DEC-14 về Lead đã đóng và chuyển Interaction/Task vẫn chờ quyết định, không thay đổi trong lần này.

## R4 và DEC-14 — 05/11/2026

Tài đã duyệt DEC-14, thay trạng thái “chờ quyết định” trong phần lịch sử phía trên. Interaction mọi visibility và Task OPEN/IN_PROGRESS đủ điều kiện chuyển cùng Lead trong transaction; các bản ghi gắn Opportunity không chuyển và Task đã đóng giữ ở Lead. Audit ghi số lượng chuyển, không ghi nội dung. Tạo mới trên Lead đóng trả `LEAD_CLOSED`; dữ liệu cũ vẫn xử lý theo quyền hiện có. Form lọc Lead NEW/QUALIFIED, báo lỗi Lead đóng và liên kết Customer nếu có quyền. Sau chuyển Lead, cache Interaction/Task được làm mới.

R4 che tiêu đề Opportunity trong mọi response khi thiếu `opportunity.read`. Data Staff đơn vai trò cũng bị che: role này có thể có permission nhưng chưa có scope Opportunity theo DEC-12. UI không hiển thị liên kết Customer/Lead khi thiếu quyền đọc và không liên kết Opportunity khi thiếu permission hoặc title đã bị che.

| Kiểm thử | Kết quả |
|---|---|
| `npm run typecheck`, `npm run lint`, `npm run build` | Đạt; build cảnh báo `SITE_URL` chưa cấu hình. |
| `npx vitest run` | **262/262**, 24 file; mốc 257/257. |
| H2 `mvn verify`, JDK 25.0.2 | Kết quả chẩn đoán từ Surefire: 191 test, 17 skip, 0 failure/error; mốc 189 test, 17 skip. Không tính là đạt nghiệm thu bản sạch. |
| PostgreSQL `scripts/test-postgres-v2.ps1` | Kết quả chẩn đoán từ Surefire: 191 test, 13 skip, 0 failure/error; mốc 189 test, 13 skip. Script báo `Verified.`, cluster 54329 đã dừng. Không tính là đạt nghiệm thu bản sạch. |

**Giới hạn bắt buộc:** Lệnh xóa `backend/target/classes`, `backend/target/test-classes` và `backend/target/surefire-reports` bị cơ chế kiểm duyệt tự động từ chối với lý do “blocked by policy”. Hai lượt backend trên được chạy thực tế nhưng chưa đáp ứng điều kiện dọn class/reports; cần chạy lại sau khi dọn theo yêu cầu. Các biến kết nối được kiểm tra không hiện diện trước khi chạy; không kết nối Supabase hay 5432. Chưa kiểm UI bằng trình duyệt, screen reader hoặc responsive thực tế.

## F1–F3 — 05/11/2026

- **F1:** Tài cung cấp kết quả trước sửa: 25/25 Task có owner chỉ giữ vai trò Customer Care không hiển thị với owner khi Customer chưa có assignment CARE. Scope Task nay bao gồm Task do Care phụ trách với subject chưa archive, cùng organization; scope Customer, Lead và Interaction không mở rộng. Integration test kiểm list/detail, PATCH, start/complete/cancel, từ chối assign, Care khác và Interaction cùng Customer.
- **F2:** Backend và frontend dùng `LOW`, `MEDIUM`, `HIGH`, `URGENT`; mặc định tạo là `MEDIUM`. Mã lạ hiển thị nguyên mã, form sửa không tự chuyển giá trị. Test kiểm create/PATCH/filter MEDIUM, từ chối NORMAL và dữ liệu cũ có mã lạ.
- **F3:** Form Interaction/Task hiển thị một thông báo tổng hợp khi server trả `VALIDATION_ERROR` kèm field errors. Vitest kiểm cả hai form.

| Kiểm chứng | Kết quả |
|---|---|
| `npm run typecheck`, `npm run lint`, `npm run build` | Đạt; build cảnh báo thiếu `SITE_URL`. |
| `npx vitest run` | **267/267** test, 24 file; mốc 262/262. |
| H2 `mvn verify`, JDK 25.0.2 | Chẩn đoán: 194 test, 18 skip, 0 failure/error; mốc bản sạch 191 test, 17 skip. Không tính là đạt nghiệm thu bản sạch. |
| PostgreSQL `scripts/test-postgres-v2.ps1` | Chẩn đoán: 194 test, 13 skip, 0 failure/error; mốc bản sạch 191 test, 13 skip. Script báo `Verified.`; kiểm tra lại 54329 không lắng nghe. Không tính là đạt nghiệm thu bản sạch. |
| Bản restore V2 | Test `careOnlyOwnersCanReadTheirRestoredTasks` gọi chính `TaskService.get`: chẩn đoán `total=25 visible=25`, không in dữ liệu cá nhân. |

**Giới hạn:** Lệnh dọn ba thư mục `backend/target/classes`, `test-classes`, `surefire-reports` bị cơ chế kiểm duyệt tự động từ chối với lý do “blocked by policy”. H2 và PostgreSQL đã chạy thật nhưng không đáp ứng bước dọn bắt buộc; cần chạy lại trên bản sạch trước khi nghiệm thu. Các biến kết nối/test được kiểm tra không có trong process; không kết nối Supabase hoặc PostgreSQL 5432. Chưa kiểm giao diện bằng trình duyệt hoặc screen reader.

## Kiểm thử giao diện bằng trình duyệt — 05/11/2026

Claude Code kiểm bằng trình duyệt tích hợp của Claude Desktop trên hai bản sao cô lập trong cluster `127.0.0.1:54329` (đã kiểm `SHOW data_directory`): `pose_v2_w06ui_202611050959` (trước F1–F3) và `pose_v2_w06ui2_202611051318` (sau F1–F3), cả hai ở Flyway V11. Backend 8081, frontend 3011. Mật khẩu thử nghiệm sinh trong phiên, chỉ đặt cho user dùng kiểm thử trong bản sao; file tạm đã xóa. Không kết nối Supabase hoặc PostgreSQL 5432.

Bộ backend chạy lại sau khi xóa `target/classes`, `target/test-classes`, `target/surefire-reports`: H2 194 test, 176 đạt, 18 skip; PostgreSQL 194 test, 181 đạt, 13 skip, `Verified.`; Vitest 267/267.

| Vai trò | Ca | Kết quả |
|---|---|---|
| Sales | Danh sách Nhiệm vụ có cột Đối tượng, đánh dấu “⚠ Quá hạn” | Đạt |
| Sales | Bộ chọn Lead chỉ có Lead NEW/QUALIFIED (Lead CONVERTED bị loại); bộ chọn Cơ hội theo Lead chỉ trả Cơ hội của Lead | Đạt |
| Sales | Tạo Interaction 05/11/2026 00:30 giờ VN → lưu `2026-11-04 17:30 UTC`; chi tiết hiện tên Lead và tên Cơ hội | Đạt |
| Customer Care | Trước F1: 0/5 Task của chính mình, chi tiết 403 `FORBIDDEN_SCOPE`; trên toàn dữ liệu 25/25 Task của user chỉ có vai trò Care bị ẩn | Lỗi, đã sửa ở F1 |
| Customer Care | Sau F1: thấy 5/5 Task của mình; sửa tiêu đề Task `MEDIUM` lưu được; “Bắt đầu” → Đang thực hiện; không có “Giao lại” | Đạt |
| Customer Care | Interaction: không có bản ghi `SALES_ONLY`; `opportunity.title` là `null` | Đạt |
| Manager | Trước F2: Task `MEDIUM` hiển thị “Thấp”, lưu trả 400 “Mức ưu tiên không hợp lệ” | Lỗi, đã sửa ở F2 |
| Chung | Form tạo Task: mặc định `MEDIUM`; lỗi validation chỉ hiện một thông báo tổng hợp, các ô lỗi có `aria-invalid` | Đạt |
| 375px | Danh sách Nhiệm vụ và Tương tác: `scrollWidth` = 375, thẻ mobile có tên đối tượng | Đạt |

Sau kiểm thử đã dừng 3011, 8081 và cluster 54329; các cổng 8080, 8081, 3010, 3011, 54329 không lắng nghe. Hai database bản sao được giữ lại.

Giới hạn: không kiểm trình đọc màn hình thật, zoom 200% hoặc `prefers-reduced-motion`; không đo tương phản bằng công cụ. Một số thao tác trong phiên dùng `form_input`/`requestSubmit` vì cửa sổ trình duyệt bị ẩn, nên chưa kiểm đầy đủ thao tác chuột và bàn phím cho W06. V11 chưa áp lên Supabase.
