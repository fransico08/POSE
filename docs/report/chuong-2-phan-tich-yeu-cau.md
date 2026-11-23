# CHƯƠNG 2. PHÂN TÍCH YÊU CẦU

> Bản nháp ngày 23/11/2026 (Tài). Nguồn: SRS POSE 2.3 mục 2–10, `docs/analysis/business-rules-v1.md`, `docs/analysis/roles-authorization-v1.md`, decision log. Sơ đồ use case và sơ đồ hoạt động bổ sung khi chuyển sang docx.

## 2.1. Các vấn đề nghiệp vụ

| Vấn đề | Ảnh hưởng | Hướng giải quyết trong POSE |
|---|---|---|
| Dữ liệu khách hàng phân tán | Khó xác định hồ sơ chính xác và theo dõi quá trình chăm sóc | Customer 360 tổng hợp theo phạm vi quyền; liên kết nguồn cho dữ liệu đa hệ thống |
| Phạm vi sale chưa rõ | Nhân viên xem hoặc sửa dữ liệu ngoài trách nhiệm | Phân công Offering theo thời gian hiệu lực; kiểm tra phạm vi tại backend |
| Danh mục khác nhau giữa các ngành | Cấu trúc sản phẩm cố định không dùng chung được | Offering có thuộc tính mở rộng theo ngành |
| Tương tác không được ghi nhận | Nhân viên nhập lại, dễ thiếu hoặc sai | Tiếp nhận hội thoại trực tiếp từ kênh, tự sinh Interaction |
| Rò rỉ lead | Không biết đang mất bao nhiêu | Nhóm KPI E — Giá trị và rò rỉ |
| Tri thức phụ thuộc cá nhân | Đổi người phụ trách làm mất ngữ cảnh | Hồ sơ bàn giao do AI soạn có trích nguồn |
| Không có thứ tự ưu tiên | Bỏ sót khách giá trị cao | Danh sách "Việc hôm nay" sắp theo quy tắc giải thích được |

*Bảng 2.1. Vấn đề nghiệp vụ và hướng giải quyết*

## 2.2. Tác nhân và vai trò

Hệ thống có năm vai trò. Một người dùng có thể giữ nhiều vai trò trong cùng tổ chức; quyền hiệu lực là hợp quyền của các vai trò, nhưng mọi yêu cầu vẫn phải qua kiểm tra tổ chức và phạm vi bản ghi.

| Mã vai trò | Tên hiển thị | Mục tiêu |
|---|---|---|
| `ADMIN` | Quản trị hệ thống | Người dùng, vai trò, quyền, cấu hình kênh và AI, nhật ký |
| `MANAGER` | Người quản lý | Nhóm, Offering, phân công sale, Customer/Lead/Opportunity và kết quả của nhóm |
| `SALES_REP` | Nhân viên sale | Offering được phân công, Customer/Lead/Opportunity thuộc phạm vi, hội thoại và nhiệm vụ của mình |
| `CUSTOMER_CARE` | Nhân viên chăm sóc | Customer được giao, Interaction/Feedback, nhiệm vụ, hội thoại của khách được giao |
| `DATA_STAFF` | Nhân viên dữ liệu | Kiểm tra, đối soát dữ liệu, số liệu tổng hợp về AI; không đọc nội dung hội thoại |

*Bảng 2.2. Vai trò hệ thống*

Ngoài người dùng, hệ thống tương tác với ba tác nhân bên ngoài: **nền tảng nhắn tin** (Telegram Bot API, Messenger Platform, Zalo mô phỏng) gửi webhook và nhận tin trả lời; **nhà cung cấp mô hình AI** (Gemini, Claude) qua AI Gateway; và **worker workflow** chạy tác vụ theo sự kiện với tư cách hệ thống.

## 2.3. Yêu cầu chức năng

| Mã | Yêu cầu | Nhóm |
|---|---|---|
| FR-01 | Quản lý tài khoản, vai trò và quyền truy cập | Lõi CRM |
| FR-02 | Quản lý doanh nghiệp, nhóm và nhân viên | Lõi CRM |
| FR-03 | Quản lý danh mục sản phẩm, dịch vụ và thuộc tính mở rộng | Lõi CRM |
| FR-04 | Phân công Offering cho nhân viên sale theo thời gian hiệu lực | Lõi CRM |
| FR-05 | Quản lý khách hàng, lead, nguồn dữ liệu và người phụ trách | Lõi CRM |
| FR-06 | Quản lý cơ hội bán hàng nhiều dòng Offering | Lõi CRM |
| FR-07 | Hồ sơ Customer 360 theo phạm vi quyền | Lõi CRM |
| FR-08 | Ghi nhận tương tác và phản hồi | Lõi CRM |
| FR-09 | Tạo, phân công và theo dõi nhiệm vụ chăm sóc | Lõi CRM |
| FR-10 | Nạp dữ liệu đa nguồn (giao diện import ở mức Could) | Lõi CRM |
| FR-11 | Dashboard và báo cáo theo phạm vi vai trò | Lõi CRM |
| FR-12 | Workflow tự động theo sự kiện, có idempotency và thử lại | Lõi CRM |
| FR-13 | Audit log cho thao tác quan trọng | Lõi CRM |
| FR-14 | Kết nối kênh hội thoại, webhook có xác thực và chống trùng | CRM.AI |
| FR-15 | Hộp thư: xem, gắn hồ sơ, trả lời trong phạm vi quyền; tự sinh Interaction; tạo Lead từ hội thoại | CRM.AI |
| FR-16 | AI Copilot: tóm tắt Customer 360, hồ sơ bàn giao, bản nháp trả lời | CRM.AI |
| FR-17 | Tín hiệu khách hàng có bằng chứng, độ tin cậy và phản hồi chấp nhận/sửa/từ chối | CRM.AI |
| FR-18 | Nhóm KPI Giá trị và rò rỉ (Value at Risk) | CRM.AI |
| FR-19 | Quản trị AI: bật/tắt, giới hạn chi phí, nhật ký gọi AI, chỉ số chất lượng | CRM.AI |

*Bảng 2.3. Yêu cầu chức năng mức cao*

## 2.4. Quy tắc nghiệp vụ chính

- **Phạm vi dữ liệu:** kiểm tra theo thứ tự người dùng đã xác thực → tổ chức → quyền → phạm vi nhóm → phạm vi bản ghi. Không có quyền mặc định; ẩn nút trên giao diện không thay cho kiểm tra ở backend.
- **Offering và phân công:** nhân viên sale chỉ thao tác Offering đang được phân công còn hiệu lực; thu hồi phân công bị chặn khi còn cơ hội mở dùng Offering đó.
- **Lead và Customer:** Lead bắt buộc có email hoặc số điện thoại và Offering quan tâm; chuyển Lead thành Customer chuyển cả hội thoại, tín hiệu và Interaction trong cùng giao dịch (DEC-30).
- **Cơ hội bán hàng:** 1–50 dòng Offering không trùng, giá trị kỳ vọng do backend tính bằng VND; giai đoạn chỉ đi tiến NEW → QUALIFIED → PROPOSAL; WON/LOST/CANCELLED bị khóa, chỉ người có quyền mở lại được đưa về QUALIFIED và có audit (DEC-12).
- **Tương tác:** ghi chú `SALES_ONLY` không hiển thị cho nhân viên chăm sóc. Nhân viên chăm sóc không có quyền với cơ hội bán hàng (DEC-12 F4, chốt 22/11/2026).
- **Hội thoại:** người được giao mặc định lấy từ cấu hình tài khoản kênh; người gửi chưa khớp hồ sơ được giữ ở trạng thái chưa gắn, nhân viên chủ động tạo hoặc gắn Lead (DEC-23, DEC-29). Tin trùng theo mã tin của nền tảng không tạo bản ghi mới.
- **AI:** AI tắt mặc định; chỉ nhận dữ liệu người gọi được xem; dữ liệu định danh được che; mọi gợi ý cần người duyệt; không có đường tự gửi tin hay tự đổi dữ liệu nghiệp vụ. Đầu ra vi phạm quy tắc an toàn hoặc sai định dạng bị loại và ghi mã lý do (DEC-27, DEC-35).

## 2.5. Ma trận phân quyền rút gọn

Ký hiệu: C tạo, R xem, U cập nhật, M quản lý, – không có quyền.

| Tài nguyên | ADMIN | MANAGER | SALES_REP | CUSTOMER_CARE | DATA_STAFF |
|---|---|---|---|---|---|
| Người dùng, vai trò | M | R nhóm | R bản thân | R bản thân | R bản thân |
| Offering | M | M nhóm | R được phân công | R theo quyền | R theo quyền |
| Phân công sale | M | C/R/U nhóm | R của mình | – | R đối soát |
| Customer, Lead | R/U hỗ trợ | C/R/U nhóm | C/R/U trong phạm vi | R khi được giao | R dữ liệu import |
| Cơ hội bán hàng | R/U hỗ trợ | C/R/U nhóm, mở lại | C/R/U trong phạm vi, không sửa bản ghi khóa | – | R dashboard |
| Interaction, Task | R/U hỗ trợ | C/R/U nhóm | C/R/U trong phạm vi | C/R/U trong phạm vi | R metadata |
| Hội thoại | R tổ chức | R/trả lời theo nhóm kênh | R/trả lời hội thoại được giao | R/trả lời hội thoại được giao | – |
| Gợi ý AI | Cấu hình, nhật ký | Dùng, duyệt | Dùng, duyệt | Dùng | Số liệu tổng hợp |
| Dashboard | R | R nhóm | R cá nhân | R nhiệm vụ | R đối soát |

*Bảng 2.4. Ma trận phân quyền rút gọn (đầy đủ trong `roles-authorization-v1.md`)*

## 2.6. Yêu cầu phi chức năng

| Nhóm | Yêu cầu |
|---|---|
| Bảo mật | Mật khẩu băm; API có xác thực; quyền kiểm tra tại backend; webhook xác thực secret hoặc chữ ký; khóa API và token kênh chỉ nằm trong biến môi trường |
| Quyền riêng tư | Che số điện thoại, email, giấy tờ định danh trước khi gửi mô hình; chỉ gửi dữ liệu tối thiểu; không lưu nội dung đầu ra AI bị loại |
| An toàn AI | AI tắt mặc định; người duyệt mọi gợi ý; giới hạn chi phí theo tổ chức; lỗi AI không làm gián đoạn CRM |
| Hiệu năng | API tra cứu dưới 3 giây trong môi trường demo; webhook xác nhận dưới 2 giây |
| Tính nhất quán | Business rule theo giao dịch; tin nhắn trùng không tạo bản ghi trùng; không deadlock giữa webhook và thao tác ghi |
| Khả năng mở rộng | Adapter kênh và nhà cung cấp AI thay được qua giao diện chung |
| Khả năng kiểm thử | Unit, integration trên PostgreSQL cô lập, E2E; nhà cung cấp AI giả lập cho kiểm thử tự động |
| Khả năng sử dụng | Giao diện tiếng Việt, responsive, hướng tới WCAG 2.2 AA; gợi ý AI có nhãn "Do AI tạo" |
| Truy vết | Lịch sử phân công, cơ hội, workflow, tin nhắn, lần gọi AI và quyết định duyệt gợi ý |

*Bảng 2.5. Yêu cầu phi chức năng*

## 2.7. Luồng nghiệp vụ minh họa

1. Người quản lý tạo Offering và phân công cho nhân viên sale.
2. Khách hàng nhắn tin qua Telegram, Messenger hoặc Zalo mô phỏng. POSE xác thực webhook, lưu tin và giao hội thoại cho người phụ trách; nhân viên tạo hoặc gắn Lead từ hội thoại. Worker tạo nhiệm vụ "Phản hồi đầu tiên".
3. Khi AI được bật, POSE trích tín hiệu ý định và cảm xúc kèm câu trích dẫn; nhân viên chấp nhận, sửa hoặc từ chối.
4. Nhân viên nhận bản nháp từ Copilot, chỉnh sửa và tự bấm gửi; phiên hội thoại tạo Interaction theo kênh.
5. Lead được đánh giá đủ điều kiện, worker tạo nhiệm vụ theo dõi; nhân viên chuyển Lead thành Customer và tạo cơ hội bán hàng.
6. Người quản lý đổi người phụ trách; Copilot soạn hồ sơ bàn giao từ dữ kiện có nguồn.
7. Dashboard hiển thị lead quá hạn phản hồi, giá trị cơ hội chưa được chăm sóc, thời gian phản hồi đầu tiên theo kênh và tỷ lệ gợi ý AI được duyệt.

*Hình 2.1. Luồng trình diễn chính (vẽ sơ đồ hoạt động khi chuyển docx)*
