# Nội dung trang giới thiệu POSE CRM v1

Ngày: **13/10/2026** · Owner: **Huỳnh Minh Tài** · Nhánh: **Tài**, đầu vào 6518703.

**Tài đã duyệt tiếp tục Phase B–C và hai cách diễn đạt ở mục 12 ngày 13/10/2026.** Bản này cập nhật các chỉnh sửa trước khi code: dùng chữ dễ hiểu cho ghi chú bán hàng/chăm sóc và dùng “quyền” trong nội dung công khai. Đọc cùng [wireframe](landing-wireframe-v1.md). Mọi nội dung dưới đây là chữ dùng để hiển thị, trừ cột nguồn và ghi chú biên tập. Không có số liệu kinh doanh, khách hàng, đối tác hoặc chức năng ngoài phạm vi TLCN.

## 1. Nguồn và cách đọc

Ký hiệu **D**: diễn giải hoặc tổng hợp từ nguồn, không phải trích nguyên văn. **N**: tên/nhãn nghiệp vụ giữ theo nguồn. **Q**: quyết định giao diện/nội dung Tài đã chốt trong prompt. Heading, câu nối và câu hỏi FAQ là D; bảng ghi nguồn cho cả tiêu đề và câu trả lời. Các tên role tiếng Việt theo prompt đợt 1/2, đối chiếu role code trong RA.

| Viết tắt | Tệp / tài liệu nguồn |
|---|---|
| CTX | [docs/PROJECT_CONTEXT.md](../../PROJECT_CONTEXT.md) |
| ROOT | [readme.md](../../../readme.md) |
| DOCS | [docs/README.md](../../README.md) |
| BR | [docs/analysis/business-rules-v1.md](../../analysis/business-rules-v1.md) |
| RA | [docs/analysis/roles-authorization-v1.md](../../analysis/roles-authorization-v1.md) |
| DEC | [docs/meetings/decision-log.md](../../meetings/decision-log.md) |
| ARCH | [docs/api/architecture-v1.md](../../api/architecture-v1.md) |
| UC | [Use case, bảng vai trò và UC-01–21](https://docs.google.com/document/d/1GPCH3CstSpRCGSjdxMlXDC4o2xpjNCXHubGSmgn20dI/mobilebasic?tab=t.h49naj3e4f4u) |
| DEMO | D:\POSE-data\POSE_CRM_DATA_V2_FINAL\README.md:182–193, mục 9; chỉ đối chiếu sáu tổ chức minh họa, không lấy mã hoặc credentials |
| P2 | Yêu cầu đợt 2 của Tài, L1–L6 và mục 3–4 |

Trong bảng, `BR:45–46` nghĩa là `docs/analysis/business-rules-v1.md`, dòng 45–46 tại HEAD đầu vào. Nguồn UC được đọc qua HTTP 200 dạng mobilebasic ngày 13/10; công cụ web không mở được URL này, nên đọc text từ HTML tải vào thư mục tạm. Không đưa bản sao toàn bộ Google Doc vào repo.

Nội dung nghiệp vụ mô tả **phạm vi thiết kế**. Trạng thái giao diện lấy từ quyết định hiện tại của Tài và [kết quả đợt 1](../../testing/frontend-foundation-review-2026-10-13.md); không suy ra module đã triển khai từ tài liệu yêu cầu.

## 2. Metadata và chữ dùng chung

| Vị trí | Chữ chính xác | Loại / nguồn |
|---|---|---|
| Meta title | POSE CRM — đồ án CRM bán hàng và chăm sóc khách hàng | D; ROOT:5–15, CTX:5–7,12. **52 ký tự**, dùng title absolute để không lặp hậu tố root layout |
| Meta description | POSE CRM là đồ án CRM đa ngành, thiết kế cho quản lý Lead, Cơ hội bán hàng, Customer 360, chăm sóc khách hàng và nhập dữ liệu có duyệt theo quyền. | D; CTX:12,20–25; BR:50,67,70; RA:5. **146 ký tự** |
| OG/Twitter title và description | Dùng đúng hai chuỗi trên | Q; P2 mục 3 |
| Alt ảnh hero và OG | Màn hình Tổng quan của POSE CRM với lối tắt theo quyền của vai trò Quản lý | Q; P2 mục 3, ảnh phải chụp thật phiên Quản lý |
| Chú thích ảnh | Tổng quan hiện có các lối tắt theo quyền. Những màn hình nghiệp vụ trong ảnh sẽ có ở giai đoạn sau. | D; P2 phần mở đầu, L2; kết quả đợt 1 / frontend README mục Công nghệ và phạm vi |
| SkipLink | Bỏ qua tới nội dung chính | Q; P2 mục 4–5 |
| Wordmark, link về / | POSE CRM | N; ROOT:1 |
| CTA, link /dang-nhap | Đăng nhập | N/Q; UC-01; P2 L5 |
| Phụ chú tài khoản | Tài khoản do quản trị viên tổ chức cấp | D/Q; P2 L5; RA:17,27,36 |
| Nút mobile | Mở điều hướng / Đóng điều hướng | D/Q; P2 header, hành vi disclosure |
| Nhãn nav chính | Điều hướng trang giới thiệu | Q; P2 header |
| Nhãn nav footer | Điều hướng cuối trang | D/Q; P2 footer |
| Ngày cập nhật | Cập nhật lần cuối: 13/10/2026 | Q; P2 mục 4; hằng nội dung 2026-10-13, không lấy thời điểm build |

Điều hướng chính có năm anchor: **Năng lực** → #nang-luc; **Quy trình** → #quy-trinh; **Vai trò** → #vai-tro; **Bảo mật** → #bao-mat; **Câu hỏi** → #cau-hoi. Nhãn là D/Q, nguồn P2 header. CTA ở header, hero và cuối main cùng nhãn/đích Đăng nhập; anchor không là CTA đăng ký.

## 3. S1 — Hero

| Cấp / vị trí | Chữ hiển thị | Loại / nguồn |
|---|---|---|
| Nhãn mở đầu, không heading | Đồ án CRM đa ngành | D; ROOT:5,15; CTX:7 |
| H1 duy nhất | POSE CRM — quản lý bán hàng và chăm sóc khách hàng đa ngành | D; ROOT:5–13; CTX:12 |
| Đoạn trả lời trực tiếp | POSE CRM là đồ án quản lý quan hệ khách hàng cho doanh nghiệp đa ngành, dành cho Quản trị hệ thống, Quản lý, Nhân viên kinh doanh, Nhân viên chăm sóc khách hàng và Nhân viên dữ liệu. | D; ROOT:5,15; RA:17–21 |
| Đoạn giải thích | Phạm vi thiết kế kết nối Lead, Cơ hội bán hàng, hồ sơ Khách hàng và hoạt động chăm sóc, với quyền truy cập theo tổ chức và phần việc được giao. | D; CTX:12,16,20–25; BR:22–28; RA:5 |
| Ghi chú phạm vi, luôn thấy | Nội dung dưới đây mô tả phạm vi thiết kế của đồ án. Giao diện hiện có đăng nhập và Tổng quan; các màn hình nghiệp vụ sẽ được triển khai ở giai đoạn sau. | D/Q; P2 phần mở đầu, L2; kết quả đợt 1, frontend README mục Công nghệ và phạm vi |
| CTA + phụ chú | Đăng nhập / Tài khoản do quản trị viên tổ chức cấp | D/Q; mục 2 |

Ảnh thật, alt và figcaption dùng mục 2. Không dùng ảnh dashboard có số KPI từ Duralux làm ảnh POSE.

## 4. S2 — Năm nhóm năng lực chính (#nang-luc)

H2: **Năm nhóm năng lực chính**. Mở đầu: **Thiết kế POSE CRM tập trung vào bán hàng, hồ sơ khách hàng, chăm sóc, nhập dữ liệu và phân quyền. Các nhóm này cùng sử dụng dữ liệu nghiệp vụ của từng tổ chức.** D; CTX:16,20–25; BR:22,67,70; RA:5.

| H3 | Một câu mô tả | Loại / nguồn |
|---|---|---|
| Lead và Cơ hội bán hàng | Theo dõi Lead và giai đoạn Cơ hội bán hàng gắn với một sản phẩm hoặc dịch vụ. | D; BR:45–54; DEC-01; UC-09/12 |
| Khách hàng và Customer 360 | Tổng hợp hồ sơ Khách hàng cùng Lead, Cơ hội bán hàng, tương tác và nhiệm vụ liên quan. | D; BR:41–46,67; ARCH:102–107; UC-10/15 |
| Chăm sóc và yêu cầu hỗ trợ | Ghi nhận chăm sóc, nhiệm vụ và quá trình xử lý yêu cầu hỗ trợ được giao. | D; BR:58–62,76–79; UC-15/16/17 |
| Dữ liệu và nhập liệu có duyệt | Kiểm tra, chuẩn hóa dữ liệu từ file trước một bước duyệt để đưa vào CRM. | D; BR:69–71; DEC-02; UC-18/19/21 |
| Phân quyền theo vai trò | Quyền theo vai trò đi cùng kiểm tra tổ chức, nhóm và phạm vi từng bản ghi. | D; RA:5,25–28,78–90; DEC-04 |

## 5. S3 — Những khó khăn POSE CRM hướng tới

H2: **Những khó khăn POSE CRM hướng tới**. Mở đầu: **POSE CRM hướng tới việc nối thông tin khách hàng với người phụ trách và công việc liên quan. Các quy tắc dưới đây định hướng cách tổ chức dữ liệu, bàn giao và kiểm soát thay đổi.** D; CTX:12,16; BR:41–46,58–62,80–88.

Đây là diễn giải nhu cầu từ business rules, không phải khảo sát hay khẳng định doanh nghiệp thực tế đang gặp tất cả vấn đề.

| H3 | Khó khăn và hướng xử lý | Loại / nguồn |
|---|---|---|
| Hồ sơ và công việc thiếu liên kết | Khi thông tin được ghi rời nhau, việc theo dõi khách hàng dễ thiếu ngữ cảnh. Thiết kế POSE gắn tương tác và nhiệm vụ với Lead hoặc Khách hàng, rồi tổng hợp vào Customer 360. | D; CTX:12; BR:58,60,67 |
| Bàn giao chưa rõ trách nhiệm | Việc đổi người phụ trách cần người nhận phù hợp và quyết định rõ ràng. Yêu cầu Bàn giao có lý do, được Quản lý duyệt; trách nhiệm hiện tại giữ nguyên trong lúc chờ. | D; BR:80–83; DEC-05; UC-11 |
| Truy cập vượt phần việc được giao | Có vai trò chưa đủ để truy cập mọi hồ sơ. Thiết kế kiểm tra quyền, tổ chức và phạm vi được giao trước khi cho phép xem hoặc sửa. | D; BR:22–28; RA:5,78–90 |
| File nhập có lỗi hoặc nghi ngờ trùng | Dữ liệu cần được chuẩn hóa và rà soát trước khi sử dụng. Kết quả kiểm tra được gửi duyệt; bản ghi nghi ngờ trùng không tự gộp vào dữ liệu CRM. | D; BR:43–44,70–71; RA:85; DEC-02; UC-19/20/21 |
| Cơ hội đã đóng vẫn bị sửa | Kết quả bán hàng cần lưu lý do và kiểm soát việc mở lại. Cơ hội Thắng hoặc Thua được khóa với Nhân viên kinh doanh; Quản lý có quyền mở lại về Đủ điều kiện. | D; BR:51–54; RA:87,94 |

## 6. S4 — Quy trình bán hàng và chăm sóc trên POSE (#quy-trinh)

H2: **Quy trình bán hàng và chăm sóc trên POSE**. Mở đầu: **Thiết kế kết nối việc tiếp nhận Lead, theo dõi Cơ hội bán hàng và phân công chăm sóc. Cơ hội có thể gắn với Lead hoặc Khách hàng; việc chuyển Lead thành Khách hàng dựa trên xác nhận đủ thông tin.** D; BR:15–18,45–53,80–83; DEC-01/05.

Dùng ol sáu bước sau, mỗi bước có H3 và đoạn mô tả. Thứ tự phục vụ đọc, không hàm ý chỉ được chuyển Lead sau khi một Cơ hội Thắng; cách diễn đạt này đã được Tài duyệt ở mục 12 ngày 13/10/2026.

| Bước / H3 | Chữ mô tả | Loại / nguồn |
|---|---|---|
| 1. Tiếp nhận Lead | Nhân viên kinh doanh tạo Lead trong phạm vi được giao, ghi nguồn, thông tin liên hệ và sản phẩm hoặc dịch vụ quan tâm. | D; BR:15,25,41,45; UC-09 |
| 2. Xác nhận đủ điều kiện | Lead được đánh giá và chuyển sang Đủ điều kiện khi phù hợp; Lead không đủ điều kiện được loại với lý do. | D/N; BR:45; UC-09 A1.2/A1.3 |
| 3. Theo dõi Cơ hội bán hàng | Mỗi Cơ hội gắn với một Lead hoặc Khách hàng và đúng một sản phẩm hoặc dịch vụ. Giai đoạn đi từ Mới → Đủ điều kiện → Đề xuất → Thắng hoặc Thua; khi đóng phải lưu lý do. | D/N; BR:50–53; DEC-01; UC-12 |
| 4. Chuyển Lead thành Khách hàng | Quản lý hoặc Nhân viên kinh doanh phụ trách xác nhận đủ thông tin để chuyển Lead. Hệ thống tái sử dụng Khách hàng khớp định danh nếu đã tồn tại và liên kết tương tác, nhiệm vụ, Cơ hội của Lead. | D; BR:43–46 |
| 5. Đề nghị Bàn giao chăm sóc | Nhân viên kinh doanh phụ trách gửi yêu cầu có lý do. Quản lý duyệt để phân công Khách hàng cho Nhân viên chăm sóc khách hàng phù hợp. | D; BR:80–83; RA:116–118; DEC-03/05 |
| 6. Chăm sóc và xử lý hỗ trợ | Nhân viên chăm sóc khách hàng xem hồ sơ được giao, ghi nhận chăm sóc, nhiệm vụ và yêu cầu hỗ trợ. Yêu cầu chuyển cấp cần lý do và được Quản lý xử lý trong phạm vi nhóm. | D; BR:26,62,76–79; RA:103,115–116; UC-08/15/16/17 |

Tên giai đoạn tiếng Việt là diễn giải: Mới = NEW; Đủ điều kiện = QUALIFIED; Đề xuất = PROPOSAL; Thắng = WON; Thua = LOST (BR:51–53). Enum chỉ dùng trong tài liệu truy vết; trang không cần hiển thị mã kỹ thuật.

## 7. S5 — Mỗi vai trò làm gì trên POSE (#vai-tro)

H2: **Mỗi vai trò làm gì trên POSE**. Mở đầu: **POSE CRM thiết kế năm vai trò với trách nhiệm khác nhau. Quyền thực tế còn phụ thuộc quyền được cấp và phạm vi dữ liệu, kể cả khi một người có nhiều vai trò.** D; RA:17–28,78–90.

Mỗi thẻ có H3 và ul ba ý; các ý là D, không phải cam kết màn hình đã có.

| H3 | Ba ý hiển thị | Nguồn |
|---|---|---|
| Quản trị hệ thống | Quản lý người dùng, vai trò và quyền trong tổ chức.<br>Xem nhật ký thao tác theo quyền.<br>Hỗ trợ dữ liệu CRM khi được cấp quyền phù hợp. | RA:17,27,36,47,86 |
| Quản lý | Quản lý sản phẩm, dịch vụ và phân công trong nhóm phụ trách.<br>Điều phối Lead, Khách hàng, Cơ hội bán hàng và nhiệm vụ.<br>Duyệt nhập dữ liệu, Bàn giao và xử lý yêu cầu hỗ trợ chuyển cấp. | RA:18,38–45,103–104,116; UC-03–08/21; DEC-02/05 |
| Nhân viên kinh doanh | Làm việc với sản phẩm, dịch vụ có phân công hiệu lực.<br>Quản lý Lead, Khách hàng và Cơ hội bán hàng trong phạm vi phụ trách.<br>Ghi tương tác, thực hiện nhiệm vụ và đề nghị Bàn giao Khách hàng. | RA:19,40–43,83,117; UC-09–14 |
| Nhân viên chăm sóc khách hàng | Xem hồ sơ và Customer 360 của Khách hàng được giao.<br>Ghi nhận tương tác, phản hồi và nhiệm vụ chăm sóc.<br>Xử lý hoặc chuyển cấp yêu cầu hỗ trợ được giao. | RA:20,40,42–44,84,103,115; UC-15–17 |
| Nhân viên dữ liệu | Chuẩn bị file, kiểm tra và chuẩn hóa dữ liệu nhập.<br>Rà soát bản ghi lỗi hoặc nghi ngờ trùng để gửi kết quả duyệt.<br>Theo dõi chất lượng dữ liệu và báo cáo theo quyền. | RA:21,45–46,85; BR:70–71; UC-18–20 |

## 8. S6 — Một nền tảng cho nhiều ngành

H2: **Một nền tảng cho nhiều ngành**. Mở đầu: **POSE CRM dùng mô hình chung cho sản phẩm và dịch vụ, có danh mục và thuộc tính mở rộng. Dữ liệu demo minh họa sáu ngành; mỗi tổ chức được tách riêng dữ liệu.** D; BR:13,22,32; ARCH:78; DEC-04; DEMO mục 9; P2 S6.

Ul sáu nhãn (N/Q): **Giáo dục; Bất động sản; Phần mềm/SaaS; Bán lẻ/F&B; Logistics; Phòng khám.** Tên ngành theo danh sách Tài yêu cầu ở P2 S6, đối chiếu sáu tổ chức minh họa ở DEMO:188–193. README demo ghi tên tổ chức, không đặc tả chức năng chuyên ngành; không suy ra tính năng đào tạo, POS, quản lý vận chuyển hoặc bệnh án.

Chú thích luôn hiện: **Dữ liệu minh họa trong bản demo, mỗi tổ chức được tách riêng dữ liệu.** D/Q; DEMO mục 9; BR:22; RA:5,28; P2 S6. Không hiện tên tổ chức demo, mã tổ chức hoặc thông tin tài khoản.

## 9. S7 — Phân quyền và an toàn dữ liệu (#bao-mat)

H2: **Phân quyền và an toàn dữ liệu**. Mở đầu: **Thiết kế POSE CRM kiểm tra tổ chức, quyền và phạm vi bản ghi trước khi cho phép thao tác. Quy tắc nhập dữ liệu, ghi chú nội bộ và nhật ký giúp kiểm soát việc sử dụng dữ liệu.** D; RA:5,78–95; BR:70–71,87–89.

Dl sáu cặp nhãn/nội dung. Dt là nhãn, không thêm H3 chỉ để tạo kiểu.

| Nhãn | Nội dung | Loại / nguồn |
|---|---|---|
| Tách theo tổ chức | Mỗi bản ghi nghiệp vụ thuộc một tổ chức. Vai trò không cho phép truy cập sang tổ chức khác. | D; BR:22; RA:28,86 |
| Vai trò đi cùng quyền | Quyền của các vai trò được kết hợp, nhưng thao tác vẫn phải qua kiểm tra phạm vi dữ liệu. | D; RA:25–28,78 |
| Phần việc được giao | Nhân viên kinh doanh cần phân công sản phẩm hoặc dịch vụ hiệu lực và phạm vi phụ trách phù hợp. Nhân viên chăm sóc thao tác trên hồ sơ hoặc công việc được giao. | D; BR:25–26,62; RA:83–84 |
| Ghi chú nội bộ bán hàng | Ghi chú chỉ dành cho bán hàng không hiển thị với nhân viên chăm sóc khách hàng; ghi chú chia sẻ với bộ phận chăm sóc chỉ được xem khi có quyền và phạm vi phù hợp. | D; RA:42,84,88 (SALES_ONLY, CARE_VISIBLE); DEC-04; chỉnh sửa Tài 13/10 |
| Nhập dữ liệu có duyệt | Nhân viên dữ liệu chuẩn bị kết quả kiểm tra; Quản lý hoặc Quản trị hệ thống duyệt một bước trước khi dữ liệu được ghi vào CRM. | D; BR:27,70; RA:45,85; DEC-02; UC-21 |
| Nhật ký thao tác | Thiết kế ghi người thao tác, hành động, đối tượng và thời điểm cho các thay đổi cần truy vết. | D; BR:87–89; CTX:43; RA:94,119 |

Không thêm huy hiệu đạt chuẩn, chứng nhận hoặc cam kết an toàn tuyệt đối. Đây là mô tả quy tắc thiết kế theo nguồn, không tuyên bố đã nghiệm thu bảo mật tất cả module.

## 10. S8 — Câu hỏi thường gặp (#cau-hoi)

H2: **Câu hỏi thường gặp**. Mở đầu: **Các câu trả lời dưới đây giải thích phạm vi thiết kế, cách truy cập và trách nhiệm sử dụng dữ liệu của POSE CRM. Tài khoản do quản trị viên tổ chức cấp.** D; ROOT:5–15; RA:17–21,27; P2 L5.

Tám details/summary, không JS accordion. Câu hỏi là summary, không thêm H3 bên trong summary. Câu trả lời dùng nguyên chuỗi dưới đây trong HTML và FAQPage JSON-LD.

| Mã | Câu hỏi | Câu trả lời đầy đủ | Loại / nguồn |
|---|---|---|---|
| FAQ-01 | POSE CRM là gì? | POSE CRM là đồ án hệ thống quản lý quan hệ khách hàng dành cho doanh nghiệp đa ngành. Phạm vi thiết kế gồm bán hàng, hồ sơ Khách hàng, chăm sóc và nhập dữ liệu có kiểm soát. | D; ROOT:5–15; CTX:12,20–25; BR:70 |
| FAQ-02 | Ai có thể sử dụng POSE CRM? | POSE CRM thiết kế cho Quản trị hệ thống, Quản lý, Nhân viên kinh doanh, Nhân viên chăm sóc khách hàng và Nhân viên dữ liệu. Chức năng truy cập phụ thuộc quyền và phạm vi được giao. | D; RA:17–21,78–90 |
| FAQ-03 | Làm thế nào để có tài khoản? | Tài khoản do quản trị viên tổ chức cấp. Người dùng liên hệ quản trị viên của tổ chức để nhận thông tin truy cập. | D/Q; RA:17,27,36; P2 L5 |
| FAQ-04 | Đăng nhập cần những thông tin gì? | Đăng nhập cần mã tổ chức, email và mật khẩu của tài khoản được cấp. Người dùng lấy mã tổ chức từ quản trị viên, không tự đăng ký trên trang giới thiệu. | D/Q; P2 L5, S8; quyết định login đợt 1 D2; đối chiếu contract auth mục 2 |
| FAQ-05 | Dữ liệu các tổ chức có tách biệt không? | Có. Theo thiết kế, mỗi bản ghi nghiệp vụ thuộc một tổ chức và quyền truy cập phải được kiểm tra trong tổ chức đó. Có nhiều vai trò không cho phép xem dữ liệu của tổ chức khác. | D; BR:22; RA:5,25–28,86 |
| FAQ-06 | Nhân viên chăm sóc có xem được ghi chú nội bộ của bán hàng không? | Ghi chú chỉ dành cho bán hàng không hiển thị với nhân viên chăm sóc khách hàng; ghi chú chia sẻ với bộ phận chăm sóc chỉ được xem khi có quyền và phạm vi phù hợp. | D; RA:42,84,88 (SALES_ONLY, CARE_VISIBLE); DEC-04; chỉnh sửa Tài 13/10 |
| FAQ-07 | Nhập dữ liệu từ file hoạt động thế nào? | Theo thiết kế, Nhân viên dữ liệu chuẩn bị file CSV/Excel, kiểm tra và chuẩn hóa dữ liệu. Kết quả cần Quản lý hoặc Quản trị hệ thống duyệt một bước trước khi ghi vào CRM; bản ghi lỗi hoặc nghi ngờ trùng được rà soát thay vì tự gộp. | D; ARCH:62–63,97–100; BR:44,70–71; RA:85; DEC-02; UC-19/20/21 |
| FAQ-08 | Khi nào cần Bàn giao Khách hàng? | Khi cần chuyển trách nhiệm bán hàng hoặc giao Khách hàng cho bộ phận chăm sóc, Nhân viên kinh doanh phụ trách gửi yêu cầu Bàn giao có lý do. Quản lý duyệt người nhận phù hợp; người phụ trách hiện tại giữ nguyên trong lúc chờ. | D; BR:80–83; RA:117–118; DEC-03/05; UC-11 |

## 11. S9 — CTA và footer

| Vị trí | Chữ hiển thị | Loại / nguồn |
|---|---|---|
| H2 CTA cuối main | Vào không gian làm việc của tổ chức | D/Q; RA:5; P2 L5, S9 |
| Đoạn CTA | Nếu đã được cấp tài khoản, hãy đăng nhập để vào Tổng quan và xem lối tắt theo quyền của bạn. | D/Q; P2 phần mở đầu, L5; kết quả đợt 1 |
| Link CTA | Đăng nhập | Q; mục 2 |
| Footer tên, không heading | POSE CRM | N; ROOT:1 |
| Footer mô tả | Đồ án Tiểu luận Chuyên ngành năm 2026 của nhóm POSE tại Trường Đại học Sư phạm Kỹ thuật TP. Hồ Chí Minh. | D; ROOT:15,90–98 |
| Nav phụ | Đăng nhập / Câu hỏi thường gặp | Q; P2 footer; đích /dang-nhap và /#cau-hoi |
| Time | Cập nhật lần cuối: 13/10/2026 | Q; mục 2; datetime 2026-10-13 |

## 12. Hai cách diễn đạt đã được Tài duyệt

1. **Câu S4:** “Cơ hội có thể gắn với Lead hoặc Khách hàng; việc chuyển Lead thành Khách hàng dựa trên xác nhận đủ thông tin.” Căn cứ BR:46,50 và DEC-01. Ol là nhịp trình bày, không mô tả Thắng tự tạo Khách hàng hay bắt chuyển đổi sau khi Thắng. Wireframe có nhánh song song để tránh hiểu sai thứ tự.
2. **Câu nhập dữ liệu ở S7/FAQ-07:** “Quản lý hoặc Quản trị hệ thống duyệt một bước trước khi ghi vào CRM.” Căn cứ DEC-02, BR:27,70; UC-21 chỉ mô tả Quản lý. Giữ cả hai actor theo quyết định đã chốt, không mô tả Nhân viên dữ liệu tự ghi hoặc tự gộp dữ liệu nghiệp vụ.

Các lệch đã có quyết định, không tự sửa tài liệu nghiệp vụ: UC-01 đặc tả cũ chỉ email/mật khẩu và dashboard theo role; quyết định đợt 1 dùng ba trường và Tổng quan chung. UC-12 đặc tả chọn Khách hàng, trong khi DEC-01 và BR:50 cho phép Lead hoặc Khách hàng; dùng quyết định này. UC-11 chỉ đặc tả chuyển sang nhân viên bán hàng; Bàn giao chăm sóc lấy DEC-05 và BR:81–83. UC-20 mô tả xử lý/hợp nhất **trong phiên kiểm tra**, chờ UC-21 duyệt; không suy ra được tự merge dữ liệu CRM, phù hợp RA:85.

Các câu D ở S3 là diễn giải nhu cầu, không có bằng chứng khảo sát thị trường; bản chữ cố ý không thêm tỷ lệ, hiệu quả định lượng hoặc lời chứng thực. Sáu ngành là nhãn minh họa Tài đã cho, không cam kết tính năng chuyên ngành.

Điểm dừng Phase A đã được đáp ứng ở commit 320d321; Tài trả lời “Tiếp tục Phase B–C”, duyệt nguyên hai cách diễn đạt trên (BR:46; BR:27, ARCH bước 3, DEC-02). Không cần hỏi lại.

Ghi chú phạm vi hero, figcaption và contentUpdatedAt đặt cạnh nhau trong src/content/landing.ts. Mỗi lần thêm module phải cập nhật hai câu trạng thái cùng ngày nội dung; FAQ hiển thị và JSON-LD cùng nguồn câu trả lời. Không để mã enum hoặc từ “permission” lọt vào chữ công khai. SITE_URL và API noindex qua Next config được duyệt; nếu Lighthouse mobile dưới 90 do root Providers, được chuyển Providers xuống app/login khi test cũ và hành vi session/cache giữ nguyên, ghi số đo trước/sau.
