# Nội dung landing v2 — Concept B đã duyệt

Ngày **13/10/2026**, Owner **Huỳnh Minh Tài**. Tài duyệt bố cục/cấu trúc/nguồn, chọn B và xác nhận ngân sách font **80 KiB = 81.920 byte**. Giữ Fraunces tĩnh 700/800, vietnamese + latin, không variable/axes; số đo cache sạch **81.736 byte đạt**. Bản chữ dưới đây áp dụng toàn bộ chỉnh sửa 2–7, cập nhật trước code.

**D** diễn giải; **N** thuật ngữ nguồn; **M** dữ liệu mẫu trung tính; **Q** theo yêu cầu Tài. BR = business-rules-v1.md, RA = roles-authorization-v1.md, DEC = decision-log.md, ARCH = architecture-v1.md, ROOT = readme.md, UC = bảng vai trò/UC-01–21 đã đọc ở Phase 0. Tất cả file nguồn ở docs/analysis, docs/meetings, docs/api như bản trước; số dòng theo cc5e195. Không bịa module/khách hàng/số liệu hiệu quả. H1 là định hướng lợi ích đã duyệt, không cam kết doanh số.

## 1. Chữ chung và metadata

| Vị trí | Chữ hiển thị | Loại / nguồn |
|---|---|---|
| Wordmark | POSE CRM | N; ROOT:1 |
| Nav | Tính năng / Cách hoạt động / Vai trò / Bảo mật / Câu hỏi | D/Q; prompt header; BR:45–89; RA:17–21 |
| CTA | Đăng nhập / Xem cách hoạt động | N/D/Q; UC-01; K1→K4 #cach-hoat-dong |
| SkipLink | Bỏ qua tới nội dung chính | Q; accessibility prompt |
| Control | Tạm dừng / Tiếp tục / Chuyển động đã tắt; icon dải ngành có aria-label Tạm dừng dải ngành / Tiếp tục dải ngành | Q; WCAG 2.2.2; chỉnh sửa 4 |
| Minh họa | Giao diện minh họa | Q; R2 |
| Footer | © 2026 POSE CRM; Đăng nhập / Câu hỏi thường gặp | Q; R1/footer |
| Meta title | POSE CRM — kết nối bán hàng và chăm sóc khách hàng | D; ROOT:5–13; BR:45–67,80–83 |
| Meta description | Kết nối Lead, Cơ hội bán hàng và Khách hàng trên POSE CRM. Phối hợp chăm sóc, bàn giao và nhập dữ liệu có duyệt, với quyền theo vai trò và phạm vi. | D; BR:45–70,80–83; RA:5,25–28 |
| Alt OG | Giao diện minh họa bán hàng và chăm sóc khách hàng trên POSE CRM | D/Q; UI dựng bằng code, R2 |

## 2. K1 — Hero

| Vị trí | Chữ hiển thị | Loại / nguồn |
|---|---|---|
| Eyebrow | CRM cho đội bán hàng và chăm sóc khách hàng | D/Q; BR:45–67,80–83; chỉnh sửa 2 |
| H1 tĩnh | Bán nhiều hơn. Chăm sóc tốt hơn. Trên một nền tảng. | D/Q; R3 thông điệp; ROOT:5–13; BR:45–67 |
| Phụ đề | Lead, Cơ hội bán hàng và hồ sơ Khách hàng cùng kết nối, từ lần liên hệ đầu tiên đến chăm sóc lâu dài. | D; BR:46,50,58–67; ARCH:102–107 |
| Cụm xoay | Dành cho đội bán hàng / bộ phận chăm sóc / người quản lý | D; RA:18–20; K1; aria-hidden kèm bản sr-only tĩnh |
| Bản sr-only | Dành cho đội bán hàng, bộ phận chăm sóc và người quản lý. | D/Q; K1 |
| Figcaption | Cơ hội, hồ sơ khách hàng và bàn giao trên cùng một màn hình. | D; BR:50–54,80–83 |
| H2 dải ngành | Một nền tảng, nhiều ngành | D; BR:13,32; DEC-04 |

Mock K1 là **M/N/Q** theo R2/chỉnh sửa 3, BR:50–54,58–67,80–83 và RA:42,84,88: pipeline **Mới / Đủ điều kiện / Đề xuất → Thắng**; mẫu Gói đào tạo cơ bản, Dịch vụ tư vấn mẫu, Gói chăm sóc cơ bản, Gói hỗ trợ mẫu, Dịch vụ triển khai mẫu, Gói dịch vụ mẫu; avatar GĐ/DV/CS/HT là chữ đầu dữ liệu mẫu, không người thật. Nhãn **Liên hệ hôm nay / Theo dõi ngày mai / Rà soát nhu cầu**, lý do Thắng **Đồng ý điều kiện**. Vòng lặp là reset minh họa, không ngụ ý tự mở lại cơ hội đã khóa.

Customer 360: **Khách hàng mẫu**, **Tương tác: Đã ghi nhận nhu cầu**, **Nhiệm vụ: Liên hệ lại ngày mai**, **Cơ hội: Gói đào tạo · Đề xuất**. Toast **Bàn giao đã được Quản lý duyệt**; badge **Ghi chú chỉ dành cho bán hàng**. Chip demo **Cơ hội / Hồ sơ**. Không tiền, KPI, tên người/công ty thật, email hoặc mã tổ chức.

## 3. K2 — Trước và sau

| Vị trí | Chữ hiển thị | Loại / nguồn |
|---|---|---|
| title | Khách hàng không rơi giữa phòng ban | D/Q; BR:41,58–67,80–83; ảnh 2 phi hành gia chỉ lấy cơ chế chia lớp |
| introduction | Thông tin liền mạch giúp các bộ phận tiếp nối công việc. | D/Q; BR:41,58–67,80–83; ảnh 2 phi hành gia chỉ lấy cơ chế chia lớp |
| before | Ghi chú rời khiến người tiếp nhận thiếu ngữ cảnh khách hàng. | D/Q; BR:41,58–67,80–83; ảnh 2 phi hành gia chỉ lấy cơ chế chia lớp |
| after | Hồ sơ, tương tác và trách nhiệm được nối theo khách hàng. | D/Q; BR:41,58–67,80–83; ảnh 2 phi hành gia chỉ lấy cơ chế chia lớp |
| Label range | So sánh hai cách làm việc | Q; K2; native range |
| aria-valuetext | Phần với POSE: {value} phần trăm | Q; giá trị điều khiển, không số liệu hiệu quả |
| Mock | Chưa có POSE / Với POSE; Bảng tính / Ghi chú / Công việc; Lead / Cơ hội / Khách hàng | M/N; K2; BR:41–67 |

## 4. K3 — Vai trò

| Vị trí | Chữ hiển thị | Loại / nguồn |
|---|---|---|
| title | Đúng việc cho từng vai trò | D; RA:17–28,78–90 |
| introduction | Cả đội phối hợp, mỗi người rõ phần việc. | D; RA:17–28,78–90 |

## K3 — Các panel

| Vị trí | Chữ hiển thị | Loại / nguồn |
|---|---|---|
| Quản lý / H3 Điều phối cả đội | Phân công sản phẩm và công việc cho đúng người phụ trách.<br>Theo dõi Cơ hội bán hàng trong phạm vi đội quản lý.<br>Duyệt dữ liệu nhập và Bàn giao khách hàng cần xử lý. | D/N; RA:18,38–45,103,116; UC-03–08/21; DEC-02/05 |
| Kinh doanh / H3 Theo sát từng cơ hội | Làm việc với Lead và Khách hàng trong phạm vi được giao.<br>Ghi nhận tương tác để nối tiếp thông tin khách hàng.<br>Theo dõi cơ hội và hoàn thành nhiệm vụ của mình. | D/N; RA:19,40–43,83; BR:45,50,58–63; UC-09–14 |
| Chăm sóc / H3 Chăm sóc có ngữ cảnh | Xem hồ sơ và lịch sử của khách hàng được giao.<br>Ghi nhận phản hồi để đội ngũ tiếp tục chăm sóc.<br>Xử lý nhiệm vụ và yêu cầu hỗ trợ được phân công. | D/N; RA:20,42–44,84,115; BR:76–79; UC-15–17 |
| Dữ liệu / H3 Chuẩn bị dữ liệu đúng | Chuẩn hóa dữ liệu từ file trước khi đưa vào CRM.<br>Kiểm tra lỗi và rà soát các bản ghi nghi trùng.<br>Gửi kết quả để Quản lý duyệt trước khi nhập dữ liệu. | D/N; RA:21,45,85; BR:70–71; UC-18–20 |
| Quản trị / H3 Quản trị trong tổ chức | Quản lý người dùng và vai trò trong tổ chức của mình.<br>Cấp quyền phù hợp với trách nhiệm của từng người dùng.<br>Xem nhật ký để theo dõi các thao tác trong tổ chức. | D/N; RA:17,27,36,47,86 |

## 5. K4 — Hành trình

| Vị trí | Chữ hiển thị | Loại / nguồn |
|---|---|---|
| H2 | Liền mạch từ Lead đến chăm sóc | D; BR:45–67,80–83 |
| Lead | Xác nhận đủ thông tin để chuyển Lead thành Khách hàng. | D/N; BR:45–46; UC-09 |
| Cơ hội bán hàng | Gắn cơ hội với Lead hoặc Khách hàng và một sản phẩm/dịch vụ. | D/N; BR:50; DEC-01 |
| Khách hàng | Liên kết hồ sơ với tương tác, nhiệm vụ và cơ hội. | D/N; BR:46,58–67 |
| Chăm sóc | Bàn giao khách hàng cho chăm sóc sau khi Quản lý duyệt. | D/N; BR:80–83; DEC-05 |

Số 01–04 chỉ là thứ tự đọc. Lead chuyển khi xác nhận đủ thông tin; cơ hội có thể gắn Lead **hoặc** Khách hàng. Bàn giao chăm sóc do Quản lý duyệt. Không ép thành workflow chỉ chuyển khách hàng sau Thắng.

## 6. K5 — Bento

| Vị trí | Chữ hiển thị | Loại / nguồn |
|---|---|---|
| H2 | Một nơi cho công việc | D; BR:58–71,80–89 |
| Customer 360 | Xem tương tác, nhiệm vụ và cơ hội của khách hàng ở một nơi. | D/N; BR:67; ARCH:102–107 |
| Phân quyền theo vai trò | Mỗi người xem đúng thông tin trong phạm vi được giao. | D/N; RA:5,25–28,78–90 |
| Nhập dữ liệu có duyệt | Kiểm tra lỗi và dữ liệu trùng trước khi Quản lý duyệt. | D/N; BR:70–71; DEC-02 |
| Bàn giao khách hàng | Phân công người tiếp nhận sau khi yêu cầu được duyệt. | D/N; BR:80–83; DEC-05; không ngụ ý CARE thay owner bán hàng |
| Nhiệm vụ và nhắc việc | Theo dõi công việc, thời hạn và việc cần làm tiếp theo. | D/N; BR:60–63; ARCH:67–69 |
| Đa ngành | Sắp xếp sản phẩm, dịch vụ theo danh mục và thuộc tính. | D/N; BR:13,32; DEC-04 |

Micro-demo M/N: Hồ sơ / Tương tác / Nhiệm vụ; góc nhìn Quản lý hoặc Chăm sóc; Lỗi / Trùng / Chờ Quản lý duyệt; Bán hàng → Chăm sóc / Đã duyệt; Liên hệ lại / Ghi phản hồi / Hoàn thành; icon ngành. Chỉ minh họa, không control nghiệp vụ thật.

## 7. K6 — Ngành

| Vị trí | Chữ hiển thị | Loại / nguồn |
|---|---|---|
| title | Phù hợp cách bạn kinh doanh | D; BR:13,32; DEC-04 |
| introduction | Dùng chung mô hình sản phẩm, dịch vụ với thuộc tính theo ngành. | D; BR:13,32; DEC-04 |
| Giáo dục | Gói đào tạo cơ bản; Hình thức: Trực tuyến / Cấp độ: Cơ bản | M/N/Q; R2/K6; BR:32; DEC-04 |
| Bất động sản | Dịch vụ tư vấn nhà ở; Loại: Tư vấn / Khu vực: Nội thành | M/N/Q; R2/K6; BR:32; DEC-04 |
| Phần mềm/SaaS | Gói phần mềm cơ bản; Quy mô: Nhóm nhỏ / Kỳ hạn: Theo tháng | M/N/Q; R2/K6; BR:32; DEC-04 |
| Bán lẻ/F&B | Gói đồ uống mẫu; Danh mục: Đồ uống / Hình thức: Mang đi | M/N/Q; R2/K6; BR:32; DEC-04 |
| Logistics | Dịch vụ giao nhận mẫu; Phạm vi: Nội vùng / Loại hàng: Hàng mẫu | M/N/Q; R2/K6; BR:32; DEC-04 |
| Phòng khám | Gói tư vấn sức khỏe; Loại: Tư vấn / Hình thức: Trực tiếp | M/N/Q; R2/K6; BR:32; DEC-04 |

Nhãn **Sản phẩm/dịch vụ mẫu**, **Giao diện minh họa**. Không hứa tính năng chuyên ngành (đào tạo/bệnh án/vận chuyển/POS). Ảnh 6 Panerai lấy chia đôi minh họa/thuộc tính, mũi tên và dấu chấm; không ảnh đồng hồ, giá hay nội dung thương hiệu.

## 8. K7 — Bảo mật, FAQ và CTA

| Vị trí | Chữ hiển thị | Loại / nguồn |
|---|---|---|
| H2 bảo mật | Ai cần thấy gì, thấy đúng thứ đó | D/Q; RA:5,25–28,78–90 |
| Ý 1 | Dữ liệu của mỗi tổ chức được tách riêng khi truy cập. | D; BR:22,70,87–89; RA:5,25–28; DEC-02 |
| Ý 2 | Quyền theo vai trò luôn đi cùng phạm vi dữ liệu được giao. | D; BR:22,70,87–89; RA:5,25–28; DEC-02 |
| Ý 3 | Nhập dữ liệu cần duyệt, các thao tác được lưu trong nhật ký. | D; BR:22,70,87–89; RA:5,25–28; DEC-02 |
| Mock | Góc nhìn chăm sóc / Góc nhìn bán hàng; Ghi chú bán hàng / Không hiển thị | M/N; RA:42,84,88; blur chỉ minh họa, backend thật lọc nội dung |
| H2 FAQ | Câu hỏi thường gặp | Q; K7 |
| H2 CTA | Đội ngũ của bạn đã sẵn sàng? | D/Q; thông điệp đã duyệt |
| Câu CTA | Đăng nhập để bắt đầu làm việc cùng đội ngũ của bạn. | D; UC-01; phối hợp theo BR:58–67 |

## FAQ — HTML và JSON-LD chung nguồn

| Vị trí | Chữ hiển thị | Loại / nguồn |
|---|---|---|
| POSE CRM giúp doanh nghiệp thế nào? | POSE CRM giúp đội bán hàng và chăm sóc kết nối Lead, Cơ hội bán hàng và hồ sơ Khách hàng. Các giao diện trên trang minh họa cách phối hợp. | D/Q; ROOT:5–13; BR:45–67,80–83; R2 |
| Có phù hợp nhiều ngành không? | Có. Mô hình sản phẩm, dịch vụ chung hỗ trợ danh mục và thuộc tính theo nhu cầu. Sáu ngành trên trang là ví dụ minh họa, không phải cam kết tính năng chuyên ngành. | D/Q; BR:13,32; DEC-04; K6 |
| Dữ liệu tổ chức có tách biệt không? | Có. Bản ghi thuộc từng tổ chức; quyền và phạm vi được kiểm tra trước khi truy cập. Việc có nhiều vai trò không cho phép truy cập tổ chức khác. | D/Q; BR:22; RA:5,25–28,86 |
| Phân quyền hoạt động thế nào? | Quyền theo vai trò đi cùng nhóm và phạm vi từng hồ sơ. Ghi chú chỉ dành cho bán hàng không hiển thị với nhân viên chăm sóc khách hàng; thông tin chia sẻ vẫn cần quyền và phạm vi phù hợp. | D/Q; RA:5,25–28,42,78–90 (enum SALES_ONLY/CARE_VISIBLE chỉ ở cột nguồn) |
| Làm sao để có tài khoản? | Quản trị viên của tổ chức cấp tài khoản và mã tổ chức. Bạn đăng nhập bằng mã tổ chức, email và mật khẩu. | D/Q; RA:17,27,36; UC-01 theo contract đã chốt |

## 9. Cách đếm và quyết định mới

Ngân sách **350–450 từ chỉ cho chữ marketing**, đếm theo khoảng trắng: eyebrow, H1, phụ đề/cụm xoay nhìn thấy, H2, đoạn mở, mô tả thẻ, H3 và ý panel đang chọn, câu hỏi FAQ đóng, CTA. Không tính mock UI, figcaption minh họa, chip ngành/vai trò, nhãn điều khiển, navigation/footer, sr-only, clone marquee, FAQ answers đóng hay panel chưa chọn. Các phần tử marketing được đánh dấu riêng trong DOM để kiểm lại runtime, không đếm textContent toàn trang.

| Khối | Từ mặc định |
|---|---:|
| K1 + H2 dải ngành + CTA | 61 |
| K2 | 43 |
| K3 Quản lý | 55 |
| K4 | 64 |
| K5 | 100 |
| K6 | 19 |
| K7 | 104 |
| **Tổng** | **446** |

Cụm xoay Chăm sóc thêm 1 từ; các panel khác vẫn trong ngân sách. Mô tả K3/K4/K5 dài **6–15 từ**, không còn câu 1–3 từ cụt. H1 11 từ; H2 ≤8, riêng câu bảo mật 9 từ giữ nguyên theo chỉnh sửa cụ thể của Tài; đoạn phụ ≤25. Meta title 50 ký tự, description 147 ký tự. contentUpdatedAt giữ 2026-10-13.

Tài đã xác nhận 81.736 byte đạt 80 KiB; không thử chỉ 800/không quay về A. Cổng thật Phase 4: mobile Performance ≥90, LCP <2,5s, CLS <0,1; nếu font là nguyên nhân chính khi không đạt thì dừng báo trước đổi font. Áp dụng nguồn ảnh đúng 1/1b/2/3/4/6, giữ các ảnh 5/7/8/9/10/11/12 ở lịch sử cho phần phù hợp. Dừng tại điểm duyệt 2 sau kiểm landing; chưa thêm micro-interaction login/app trước khi Tài tiếp tục.

## 10. Chỉnh sửa sau review điểm 2 — chưa được duyệt nghiệm thu

Tài cho phép A1–A5 và B6–B13; giữ Fraunces 700/800. Bổ sung latin-ext chỉ để preload file đã có, không thêm byte display. H1/eyebrow/phụ đề/CTA chuyển thành Server Component; không chuyển động H1. Bảng nguồn marketing/FAQ phía trên giữ nguyên, ngân sách từ không tính mock/điều khiển.

| Vị trí | Chữ / thay đổi | Loại / nguồn |
|---|---|---|
| Motion khi reduced | Đã tắt chuyển động; không icon phát | Q; B8; WCAG giảm chuyển động |
| K2 trái / phải | Chưa có POSE / Với POSE; tay nắm biểu thị vị trí đường chia | D/Q; B9; BR:58–67; mô tả trước nằm trái, sau nằm phải |
| K2 aria-valuetext | Chưa có POSE: X phần trăm bên trái; Với POSE: Y phần trăm bên phải | Q; B9; X+Y=100, hai nút xem toàn trạng thái là phương án thay kéo |
| K2 chi tiết mẫu | Bảng tính; Khách hàng mẫu; Dịch vụ mẫu; Trùng; Liên hệ lại; Cần xác nhận; Chờ thông tin; Chưa ghi nhận; Đã nhắn lại; Ai tiếp nhận?; Thiếu người phụ trách | M/D/Q; B9; BR:58–67; hình minh họa cách thông tin rời, không số liệu/khách hàng thật |
| K3 mock chung | Avatar chữ đầu nhãn, Hôm nay/Ngày mai/Tuần này, Đã kiểm tra/Chờ duyệt/Cần xử lý/Đang theo dõi/Được giao | M/Q; B10; không tác vụ thật |
| K3 Quản lý | Phân công nhiệm vụ; Bàn giao chờ duyệt; Dữ liệu chờ duyệt; Cơ hội cần theo dõi; Công việc của đội | D/M; RA:18,38–47; BR:60–70,80–88; UC-03,04,06,08,11,19 |
| K3 Kinh doanh | Lead cần liên hệ; Cơ hội đang theo dõi; Ghi nhận tương tác; Nhiệm vụ đến hạn; Hồ sơ được giao | D/M; RA:19,38–44; BR:45–67; UC-09,10,12–14 |
| K3 Chăm sóc | Hồ sơ được giao; Phản hồi cần xử lý; Nhiệm vụ đến hạn; Yêu cầu hỗ trợ; Lịch sử tương tác | D/M; RA:20,40–44; BR:58–78; UC-15–17 |
| K3 Dữ liệu | Chuẩn hóa dữ liệu; Dòng hợp lệ; Dòng cần sửa; Bản ghi nghi trùng; Gửi kết quả duyệt | D/M; RA:21,45–47; BR:69–71; UC-18,20,21 |
| K3 Quản trị | Người dùng tổ chức; Vai trò được cấp; Quyền theo trách nhiệm; Phạm vi dữ liệu; Nhật ký thao tác | D/M; RA:17,27,36–42; UC-02; không điều khiển quyền thật |
| K5 Customer 360 | Thêm hoạt động mẫu: Theo dõi — Đã lên lịch liên hệ | M/D; B11; BR:58–67; không tính năng mới |
| K5 Đa ngành | Sáu chip Giáo dục/Bất động sản/Phần mềm-SaaS/Bán lẻ-F&B/Logistics/Phòng khám với icon outline | N/M/Q; B11; tên ngành như K6, dữ liệu demo đã duyệt |
| K6 mock trái | Sản phẩm/dịch vụ mẫu; badge tên ngành, tên mẫu và thuộc tính từ cùng dữ liệu K6 | M/Q; B12; BR:13,32; DEC-04; không hứa tính năng chuyên ngành |

Các nhãn mock này không thuộc ngân sách marketing; mọi tương tác chỉ đổi minh họa, không gọi API. Câu trước/sau K2 vẫn là hai câu có nguồn ở mục 3, nay được đặt theo chiều trái/chưa có và phải/với POSE đúng B9.

## 11. Duyệt ngoại lệ và chỉnh K1/K6 ngày 14/10

Tài giữ Fraunces 700/800, yêu cầu gỡ inlineCss, không thử display optional hoặc đổi font H2. Điểm dừng 2 được duyệt có điều kiện: cổng mới đạt và ba chỉnh sửa K1 desktop/mobile, K6 hoàn tất thì tiếp tục Phase 5–6.

| Vị trí | Chữ / thay đổi | Loại / nguồn |
|---|---|---|
| K1 desktop | Customer 360, toast và ghi chú lấn mép dưới/cạnh phải 25–35%, chỉ trên đáy trống | Q; review 14/10 mục 2a; không đổi nội dung nghiệp vụ |
| K1 mobile | Chỉ pipeline và toast; Customer 360/ghi chú ẩn bằng display:none trong mock aria-hidden | Q; review 14/10 mục 2b; các ý đã có tại K5/K7 |
| K6 đoạn mở | Thuộc tính sản phẩm, dịch vụ theo danh mục. | D; BR:32; DEC-04; thay câu mở cũ để giữ ngân sách chữ |
| K6 H3 phải | Thuộc tính của danh mục {ngành} | D/M; BR:32; DEC-04; nhãn cấu hình mẫu, không lặp tên sản phẩm bên trái |
| K6 câu giải thích | Danh mục quyết định thuộc tính. | D; BR:32; DEC-04; sáu từ marketing |
| K6 dl phải | Tên thuộc tính — kiểu dữ liệu mẫu: Lựa chọn, Văn bản, Số | M/Q; review 14/10 mục 2c; BR:32; DEC-04; kiểu mẫu, không cam kết danh sách kiểu API |
| K6 Giáo dục | Thêm Số buổi — 8 ở thẻ trái; Số buổi — Số ở danh sách phải | M/Q; review 14/10 mục 2c; dữ liệu mẫu trung tính được phép, không KPI hoặc số hiệu quả |

K6 đoạn mở mới 9 từ thay 13 từ; câu giải thích thêm 6 từ. Marketing mặc định 448, cụm Chăm sóc 449 từ. H3 danh mục, tên/kiểu/giá trị thuộc tính là nhãn mock minh họa, không tính marketing. Ngày nội dung cập nhật thành 2026-10-14 cùng source khi triển khai K6; FAQ/JSON-LD không đổi.
