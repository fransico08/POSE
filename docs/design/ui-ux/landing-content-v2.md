# Nội dung landing v2 — bản chờ Tài duyệt

Ngày **13/10/2026**, Owner **Huỳnh Minh Tài**, đầu vào **cc5e195**. Hai prompt đợt 3 được áp dụng cùng nhau: B dùng **Fraunces 600–800**, không dùng Bricolage. A/B cùng bố cục, nội dung và chuyển động. Trang `/` v1 chưa thay trước điểm dừng 1.

**D**: diễn giải; **N**: thuật ngữ nghiệp vụ; **M**: dữ liệu minh họa trung tính theo R2/K6; **Q**: chữ/hành vi theo yêu cầu Tài. Không suy ra module đã hoàn thành từ tài liệu nghiệp vụ. Mock không gọi API, luôn có nhãn “Giao diện minh họa”; không có khách hàng thật, số liệu hiệu quả hay tính năng ngoài nguồn.

Nguồn: **BR** = docs/analysis/business-rules-v1.md; **RA** = docs/analysis/roles-authorization-v1.md; **DEC** = docs/meetings/decision-log.md; **ARCH** = docs/api/architecture-v1.md; **ROOT** = readme.md; **UC** = bảng vai trò/UC-01–21 của Google Doc trong prompt, đọc HTTP 200 ngày 13/10. Dòng nguồn theo HEAD đầu vào, không sửa nghiệp vụ. Mẫu thuộc tính ngành được Tài cho phép ở R2/K6, không phải chức năng chuyên ngành.

## 1. Chữ dùng chung và metadata

| Vị trí | Chữ | Loại / nguồn |
|---|---|---|
| Wordmark | POSE CRM | N; ROOT:1 |
| Nav | Tính năng / Cách hoạt động / Vai trò / Bảo mật / Câu hỏi | D/Q; prompt header; BR:45–89, RA:17–21 |
| SkipLink | Bỏ qua tới nội dung chính | Q; accessibility prompt |
| CTA chính | Đăng nhập | N/Q; UC-01, R1/Phase 3 |
| CTA phụ | Xem cách hoạt động | D/Q; K1 → K4 |
| Pause/resume | Tạm dừng / Tiếp tục | Q; mục 5, WCAG 2.2.2 |
| Nhãn mock | Giao diện minh họa | Q; R2 |
| Footer | © 2026 POSE CRM | Q; R1 |
| Nav footer | Đăng nhập / Câu hỏi thường gặp | Q; K7/footer |
| Meta title | POSE CRM — kết nối bán hàng và chăm sóc khách hàng | D; ROOT:5–13, BR:45–67,80–83 |
| Meta description | Kết nối Lead, Cơ hội bán hàng và Khách hàng trên POSE CRM. Phối hợp chăm sóc, bàn giao và nhập dữ liệu có duyệt, với quyền theo vai trò và phạm vi. | D; BR:45–70,80–83; RA:5,25–28 |

contentUpdatedAt giữ **2026-10-13**. Không dùng câu scopeNote/productCaption v1 trong bản v2; gỡ khi thay trang sau duyệt. Ngày nguồn/tiến độ và nguồn trường/nhóm vẫn giữ trong tài liệu nội bộ, không đưa lên giao diện công khai.

## 2. K1 — Hero, A/B dùng cùng chữ

| Vị trí | Chữ | Loại / nguồn |
|---|---|---|
| Eyebrow | Bán hàng và chăm sóc. | D; BR:45–67,80–83; ROOT:5–13 |
| H1, tĩnh, 11 từ | Bán nhiều hơn. Chăm sóc tốt hơn. Trên một nền tảng. | D/Q; định hướng thông điệp mục 3; ROOT:5–13; BR:45–67. Đây là mục tiêu phối hợp, không cam kết hiệu quả định lượng |
| Phụ đề | Chung dữ liệu Khách hàng, từ Lead đến chăm sóc. | D; BR:46,50,58–67; ARCH:102–107 |
| Dòng đổi cụm từ | Dành cho đội bán hàng / bộ phận chăm sóc / người quản lý | D; RA:18–20; K1. Bản sr-only: “Dành cho đội bán hàng, bộ phận chăm sóc và người quản lý.” |
| Figcaption | Kết nối chăm sóc. | D; BR:50–54,80–83 |
| H2 dải ngành | Một nền tảng, nhiều ngành | D; BR:13,32; DEC-04; ROOT:5–13 |
| Chip | Giáo dục / Bất động sản / Phần mềm/SaaS / Bán lẻ/F&B / Logistics / Phòng khám | N/Q; K6; mẫu nhiều ngành theo ROOT:11 |
| Control hero | Tạm dừng hiệu ứng / Tiếp tục hiệu ứng / Chuyển động đã tắt | Q; mục 5, reduced motion |

Mock K1: **Gói đào tạo cơ bản**, **Đề xuất**, **Thắng**, **Lý do: Đồng ý**, **Customer 360**, **Khách hàng mẫu**, **Hồ sơ**, **Tương tác**, **Nhiệm vụ**, **Bàn giao đã được Quản lý duyệt**, **Ghi chú chỉ dành cho bán hàng**. N/D/M; BR:50–54,67,80–83; RA:42,84,88; R2. Chip đổi trọng tâm minh họa: **Cơ hội / Hồ sơ** (N/D; K1, ảnh 1b). Không có tiền, email, tên người/tổ chức hoặc KPI. Cơ hội Thắng có lý do; mock không hàm ý phải Thắng mới chuyển Lead.

## 3. K2 — Trước và sau

| Vị trí | Chữ | Loại / nguồn |
|---|---|---|
| H2 | Khách hàng không rơi giữa phòng ban | D/Q; thông điệp mục 3; BR:58–67,80–83 |
| Nhãn trái / câu | Chưa có POSE / Thông tin rời, thiếu ngữ cảnh. | D; diễn giải vấn đề từ BR:58,60,67; không phải khảo sát thị trường |
| Nhãn phải / câu | Với POSE / Nối hồ sơ và trách nhiệm. | D; BR:41,58,60,67,80–83 |
| Label range | So sánh hai cách làm | D/Q; K2 |
| aria-valuetext | Phần với POSE: {value} phần trăm | Q; input range, không phải số liệu hiệu quả |
| Mock | Bảng tính / Ghi chú / Việc / Lead / Cơ hội / Khách hàng | N/D/M; BR:41–67; K2 |

## 4. K3 — Vai trò

H2 **Đúng việc cho từng vai trò**. D; RA:17–28,78–90. Tabs **Quản lý / Kinh doanh / Chăm sóc / Dữ liệu / Quản trị**; N/D; RA:17–21. Mặc định Quản lý, tự chuyển chỉ khi được phép; control Tạm dừng/Tiếp tục.

| H3 của panel | Ba ý | Mock | Loại / nguồn |
|---|---|---|---|
| Điều phối | Phân công.<br>Theo dõi cơ hội.<br>Duyệt dữ liệu, bàn giao. | Nhiệm vụ / Chờ duyệt | D/N/M; RA:18,38–45,103,116; UC-03–08/21; DEC-02/05 |
| Nối tiếp cơ hội | Theo sát Lead.<br>Ghi nhận tương tác.<br>Hoàn thành nhiệm vụ. | Cơ hội mẫu / Đề xuất | D/N/M; RA:19,40–43,83; BR:45,50,58–63; UC-09–14 |
| Chăm sóc có ngữ cảnh | Xem hồ sơ được giao.<br>Ghi phản hồi.<br>Xử lý hỗ trợ. | Khách hàng mẫu / Đang xử lý | D/N/M; RA:20,42–44,84,115; BR:76–79; UC-15–17 |
| Chuẩn bị dữ liệu | Kiểm tra file.<br>Rà soát lỗi, trùng.<br>Gửi kết quả duyệt. | Kiểm tra / Chờ duyệt | D/N/M; RA:21,45,85; BR:70–71; UC-18–20 |
| Quản trị theo quyền | Quản lý người dùng.<br>Phân quyền trong tổ chức.<br>Xem nhật ký. | Quyền truy cập / Nhật ký | D/N/M; RA:17,27,36,47,86 |

## 5. K4 — Hành trình

H2 **Liền mạch từ Lead đến chăm sóc**. D; BR:45–67,80–83. Số 01–04 chỉ là thứ tự đọc, không ép một workflow tuyến tính sai nghiệp vụ.

| H3 | Câu ngắn | Loại / nguồn |
|---|---|---|
| Lead | Xác nhận đủ thông tin. | D/N; BR:45–46; UC-09 |
| Cơ hội bán hàng | Một sản phẩm/dịch vụ; gắn Lead/Khách hàng. | D/N; BR:50; DEC-01 |
| Khách hàng | Nối hồ sơ, tương tác, nhiệm vụ. | D/N; BR:46,58–67 |
| Chăm sóc | Bàn giao do Quản lý duyệt. | D/N; BR:80–83; DEC-05 |

## 6. K5 — Bento

H2 **Gọn việc, rõ ngữ cảnh**. D; BR:58–71,80–89.

| H3 | Câu trong ô | Nhãn micro-demo | Loại / nguồn |
|---|---|---|---|
| Customer 360 | Nối ngữ cảnh. | Hồ sơ / Tương tác | D/N/M; BR:67; ARCH:102–107 |
| Phân quyền theo vai trò | Đúng phạm vi. | Quản lý hoặc Chăm sóc (chỉ một nhãn hiện tại) | D/N/M; RA:5,25–28,78–90 |
| Nhập dữ liệu có duyệt | Rà lỗi. | Lỗi / Trùng / Chờ Quản lý duyệt | D/N/M; BR:70–71; DEC-02; RA:85 |
| Bàn giao khách hàng | Rõ người nhận. | Đã duyệt | D/N/M; BR:80–83; DEC-05 |
| Nhiệm vụ và nhắc việc | Theo sát việc. | Hoàn thành | D/N/M; BR:60–63; ARCH:67–69 |
| Đa ngành | Mô hình chung. | Ngành mẫu | D/N/M; BR:13,32; DEC-04 |

## 7. K6 — Ngành

H2 **Danh mục theo ngành**. D; BR:13,32; DEC-04. Chip sáu ngành dùng cùng nhãn K1. Nhãn thẻ **Sản phẩm/dịch vụ mẫu**, **Giao diện minh họa** (N/Q; K6). Thuộc tính dưới đây là **M**, không hứa phần mềm quản lý đào tạo, bệnh án, vận chuyển, POS hoặc chuyên ngành khác.

| Ngành | Tên mẫu | Thuộc tính mẫu | Nguồn |
|---|---|---|---|
| Giáo dục, mặc định | Gói đào tạo cơ bản | Hình thức: Trực tuyến / Cấp độ: Cơ bản | R2/K6; BR:32; DEC-04 |
| Bất động sản | Dịch vụ tư vấn nhà ở | Loại: Tư vấn / Khu vực: Nội thành | R2/K6; BR:32; DEC-04 |
| Phần mềm/SaaS | Gói phần mềm cơ bản | Quy mô: Nhóm nhỏ / Kỳ hạn: Theo tháng | R2/K6; BR:32; DEC-04 |
| Bán lẻ/F&B | Gói đồ uống mẫu | Danh mục: Đồ uống / Hình thức: Mang đi | R2/K6; BR:32; DEC-04 |
| Logistics | Dịch vụ giao nhận mẫu | Phạm vi: Nội vùng / Loại hàng: Hàng mẫu | R2/K6; BR:32; DEC-04 |
| Phòng khám | Gói tư vấn sức khỏe | Loại: Tư vấn / Hình thức: Trực tiếp | R2/K6; BR:32; DEC-04 |

## 8. K7 — Bảo mật, FAQ, CTA

| Vị trí | Chữ | Loại / nguồn |
|---|---|---|
| H2 bảo mật | Ai cần gì, thấy đúng đó | D/Q; mục 3; RA:5,25–28,78–90 |
| Ba ý | Dữ liệu theo tổ chức.<br>Quyền và phạm vi.<br>Nhập liệu có duyệt, nhật ký thao tác. | D; BR:22,70,87–89; RA:5,25–28; DEC-02 |
| Mock | Góc nhìn chăm sóc / Ghi chú bán hàng / Không hiển thị | N/D/M; RA:42,84,88; nội dung nhạy cảm được lọc, không chỉ blur trên UI thật |
| H2 FAQ | Câu hỏi thường gặp | D/Q; K7 |
| H2 CTA | Đội ngũ của bạn đã sẵn sàng? | D/Q; thông điệp mục 3 |
| Câu CTA | Cùng phối hợp. | D; UC-01; ARCH frontend |

| FAQ | Câu trả lời — chung cho HTML và JSON-LD | Loại / nguồn |
|---|---|---|
| POSE CRM giúp doanh nghiệp thế nào? | POSE CRM hỗ trợ tổ chức Lead, Cơ hội bán hàng và hồ sơ Khách hàng để đội bán hàng, chăm sóc phối hợp. Các giao diện trên trang là minh họa cách làm việc. | D; ROOT:5–13; BR:45–67,80–83; R2 |
| Có phù hợp nhiều ngành không? | Có. Mô hình sản phẩm/dịch vụ chung hỗ trợ danh mục và thuộc tính theo nhu cầu. Sáu ngành trên trang là ví dụ minh họa, không phải cam kết chức năng chuyên ngành. | D; BR:13,32; DEC-04; K6 |
| Dữ liệu tổ chức có tách biệt không? | Có. Bản ghi thuộc từng tổ chức; quyền và phạm vi được kiểm tra trước khi truy cập. Nhiều vai trò không cho phép vượt sang tổ chức khác. | D; BR:22; RA:5,25–28,86 |
| Phân quyền hoạt động thế nào? | Quyền theo vai trò đi cùng nhóm và phạm vi từng hồ sơ. Ghi chú nội bộ bán hàng không hiển thị với chăm sóc; thông tin chia sẻ vẫn cần quyền phù hợp. | D; RA:5,25–28,42,78–90 |
| Làm sao để có tài khoản? | Quản trị viên của tổ chức cấp tài khoản và mã tổ chức. Bạn đăng nhập bằng mã tổ chức, email và mật khẩu; trang giới thiệu không có form tự đăng ký. | D/Q; RA:17,27,36; UC-01 theo contract foundation đã chốt |

## 9. Ngân sách và điểm cần duyệt

Đếm từ theo khoảng trắng, tính mỗi lần chữ xuất hiện: header/footer, summary FAQ đóng, control, chip và mock vẫn thấy; không tính sr-only, clone marquee aria-hidden trùng lặp hoặc panel tab chưa chọn. Inventory bản chữ mặc định Quản lý/Giáo dục là **448 từ**, gồm cả lý do Thắng ở mock; đây là dự toán nội dung, chưa phải số đếm DOM của trang v2 hoàn chỉnh. Dòng đổi cụm từ/control và tab do người dùng chọn có thể làm số từ dao động; sẽ đo lại runtime ở Phase 4. Mục tiêu khoảng **350–450 từ**. H1 11 từ; heading section ≤8; đoạn phụ ≤25; mô tả thẻ ≤15. Bản hero A/B dùng cùng chữ; serif không tự đổi câu/giảm font để tạo lợi thế.

| Nhóm chữ trong inventory | Từ |
|---|---:|
| Header | 15 |
| K1 hero, control, mock, caption | 90 |
| K1 dải ngành | 19 |
| K2 | 39 |
| K3, mặc định Quản lý | 39 |
| K4 | 45 |
| K5, gồm nhãn minh họa dùng chung | 67 |
| K6, mặc định Giáo dục | 37 |
| K7 bảo mật | 38 |
| K7 FAQ đóng | 37 |
| K7 CTA và footer | 22 |
| **Tổng** | **448** |

Hai điểm Tài duyệt tại điểm dừng 1: (1) H1 là mục tiêu lợi ích theo định hướng Tài, không cam kết doanh số; (2) bảng thuộc tính ngành là dữ liệu mẫu theo R2, chỉ minh họa Offering linh hoạt. Không đưa claim module đã ra mắt/khách hàng thật vào copy. Chưa đổi trang công khai trước khi Tài chọn concept và duyệt chữ.
