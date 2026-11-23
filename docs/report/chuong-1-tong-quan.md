# CHƯƠNG 1. TỔNG QUAN ĐỀ TÀI

> Bản nháp ngày 23/11/2026 (Tài). Nguồn: SRS POSE 2.3, `docs/PROJECT_CONTEXT.md`, decision log DEC-18 đến DEC-36. Chuyển sang docx khi định dạng báo cáo.

## 1.1. Lý do chọn đề tài

Doanh nghiệp ở nhiều lĩnh vực như giáo dục, bất động sản, công nghệ, bán lẻ, logistics và y tế đều tốn chi phí để có lead và khách hàng, nhưng phần lớn giá trị của tệp khách hàng không được khai thác hết. Trên thực tế, tương tác với khách hàng diễn ra qua Zalo, Messenger, Telegram, điện thoại hoặc email, còn hệ thống quản lý quan hệ khách hàng (CRM) chỉ nhận lại một bản ghi chú do nhân viên tự nhập. Dữ liệu tư vấn, vốn là nguồn hiểu biết giá trị nhất về khách hàng, vì vậy không trở thành tài sản của doanh nghiệp.

Nhóm gọi hiện tượng này là **rò rỉ giá trị khách hàng**, xảy ra ở ba điểm:

1. **Rò rỉ lead.** Lead không được phản hồi kịp thời hoặc không được theo dõi tiếp nên nguội dần. Trên dữ liệu demo ngành giáo dục tháng 09/2026, dashboard POSE ghi nhận chỉ 6/10 lead được phản hồi, thời gian phản hồi trung bình 2 ngày và 8 hồ sơ chưa từng có tương tác.
2. **Tri thức phụ thuộc cá nhân.** Nhu cầu, cam kết và lịch sử tư vấn nằm trong điện thoại và trí nhớ của nhân viên sale. Khi đổi người phụ trách, người tiếp nhận phải bắt đầu lại từ đầu.
3. **Không biết dồn sức vào đâu.** Doanh nghiệp không biết khách hàng nào có giá trị, khách hàng nào sắp rời bỏ và nên ưu tiên ai trước.

CRM truyền thống chỉ lưu những gì nhân viên nhập, còn các CRM gắn chatbot thường bỏ qua phạm vi dữ liệu của từng vai trò. Đề tài POSE CRM.AI hướng tới một CRM đa ngành trong đó tương tác được tiếp nhận trực tiếp từ kênh hội thoại, mỗi lần tư vấn sinh ra dữ liệu có cấu trúc, và trí tuệ nhân tạo (AI) hỗ trợ nhân viên dưới sự kiểm soát của con người.

## 1.2. Mục tiêu đề tài

### 1.2.1. Mục tiêu tổng quát

Xây dựng phiên bản sản phẩm khả dụng tối thiểu (MVP) của hệ thống CRM đa ngành theo định hướng CRM.AI: quản lý tập trung khách hàng, sản phẩm/dịch vụ (Offering), phạm vi phụ trách của nhân viên sale và hoạt động chăm sóc; tiếp nhận tương tác trực tiếp từ kênh hội thoại; dùng AI có kiểm soát để giảm rò rỉ giá trị; và làm nền cho phân tích, dự đoán ở giai đoạn khóa luận tốt nghiệp (KLTN).

### 1.2.2. Mục tiêu cụ thể

1. Xác thực, phân quyền theo vai trò và phạm vi dữ liệu kiểm tra tại backend.
2. Quản lý tổ chức, nhóm, người dùng và Offering đa ngành có thuộc tính mở rộng.
3. Phân công Offering cho nhân viên sale theo thời gian hiệu lực và kiểm soát phạm vi truy cập.
4. Quản lý Lead, Customer, cơ hội bán hàng, tương tác, nhiệm vụ và hồ sơ Customer 360.
5. Tự động hóa quy trình theo quy tắc có outbox, khóa idempotency và thử lại.
6. Dashboard KPI theo phạm vi vai trò, đối soát được bằng SQL độc lập.
7. Chốt kiến trúc CRM.AI và mô hình dữ liệu cho hội thoại, AI và tín hiệu khách hàng.
8. Tiếp nhận và trả lời hội thoại từ hai kênh thật (Telegram và Messenger), tự động sinh Interaction và hỗ trợ tạo Lead.
9. AI Copilot có kiểm soát: tóm tắt Customer 360, hồ sơ bàn giao và bản nháp trả lời.
10. Trích tín hiệu khách hàng có bằng chứng, thu phản hồi người dùng và đánh giá trên bộ nhãn chuẩn.
11. Đo rò rỉ giá trị bằng nhóm KPI giải thích được.
12. Hoàn thiện MVP có dữ liệu demo đa ngành, kịch bản trình diễn và bằng chứng kiểm thử.

## 1.3. Đối tượng và phạm vi

### 1.3.1. Đối tượng sử dụng

| Vai trò | Nhu cầu chính | Phạm vi dữ liệu |
|---|---|---|
| Quản trị hệ thống | Tài khoản, vai trò, cấu hình kênh và AI, nhật ký | Toàn tổ chức |
| Người quản lý | Nhóm, Offering, phân công, báo cáo, chỉ số rò rỉ | Nhóm phụ trách |
| Nhân viên sale | Lead, Customer, cơ hội, hội thoại được giao | Phạm vi phân công |
| Nhân viên chăm sóc | Customer 360, tương tác, nhiệm vụ chăm sóc | Khách hàng được giao |
| Nhân viên dữ liệu | Kiểm tra, đối soát dữ liệu, đánh giá chất lượng AI | Metadata và số liệu tổng hợp |

*Bảng 1.1. Các nhóm người dùng của POSE*

### 1.3.2. Phạm vi tiểu luận chuyên ngành

Phạm vi được xác định theo phương pháp MoSCoW (SRS 2.3):

| Mức | Nội dung |
|---|---|
| Must Have | Đăng nhập và phân quyền; tổ chức, nhóm, người dùng; Offering; phân công sale; Lead, Customer, Opportunity, Interaction, Task; Customer 360; dashboard 16 KPI; audit log; workflow theo quy tắc; kiến trúc CRM.AI; hộp thư hội thoại với hai kênh thật Telegram và Messenger (chế độ phát triển) cùng Zalo mô phỏng; AI Gateway có quản trị; AI Copilot tóm tắt và bàn giao |
| Should Have | Tín hiệu khách hàng (ý định, cảm xúc) có phản hồi người dùng và đánh giá; nhóm KPI E — Giá trị và rò rỉ; bản nháp trả lời; danh sách "Việc hôm nay" |
| Could Have | Yêu cầu hỗ trợ, giao diện import, cập nhật hội thoại thời gian thực, chiến dịch, xuất Excel/PDF |
| Won't Have Now | DISC, RFM nâng cao, Predictive CLV, huấn luyện mô hình, Next Best Action bằng mô hình, AI tự gửi tin hoặc tự đổi dữ liệu, Zalo Official Account thật |

*Bảng 1.2. Phạm vi theo MoSCoW*

Các hạng mục Won't Have Now thuộc hướng phát triển KLTN (Chương 6).

## 1.4. Phương pháp thực hiện

- **Quy trình:** phát triển lặp theo tuần, quản lý công việc trên Trello; mọi quyết định nghiệp vụ được ghi vào nhật ký quyết định (DEC-01 đến DEC-36).
- **Kiến trúc:** Modular Monolith nhiều tầng; backend Spring Boot, frontend Next.js, PostgreSQL; thay đổi cơ sở dữ liệu chỉ qua migration Flyway mới.
- **AI có người duyệt (human-in-the-loop):** AI tắt mặc định; mọi gợi ý được nhân viên chấp nhận, sửa hoặc từ chối; dữ liệu định danh được che trước khi gửi tới mô hình.
- **Kiểm chứng:** kiểm thử đơn vị, tích hợp trên PostgreSQL cô lập và E2E bằng Playwright; đối soát KPI bằng SQL độc lập; nghiệm thu kênh thật bằng chạy end-to-end có evidence.

## 1.5. Bố cục báo cáo

- **Chương 1** giới thiệu bài toán, mục tiêu, phạm vi và phương pháp.
- **Chương 2** phân tích yêu cầu chức năng, phi chức năng, tác nhân và phân quyền.
- **Chương 3** trình bày thiết kế kiến trúc, mô hình lớp miền, cơ sở dữ liệu và API.
- **Chương 4** mô tả triển khai các module lõi CRM và lớp CRM.AI.
- **Chương 5** trình bày kiểm thử, hiệu năng và đánh giá AI.
- **Chương 6** kết luận, giới hạn và hướng phát triển KLTN.
