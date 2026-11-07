# XÂY DỰNG HỆ THỐNG CRM HỖ TRỢ QUẢN LÝ VÀ CHĂM SÓC KHÁCH HÀNG CHO DOANH NGHIỆP

(Developing a Customer Relationship Management (CRM) System for Businesses)

**Định hướng sản phẩm: POSE CRM.AI — biến mỗi tương tác thành tài sản khách hàng**

| Phiên bản | Ngày | Nội dung | Người chốt |
|---|---|---|---|
| 1.0 | 11/09/2026 | SRS CRM đa ngành: lõi CRM, dữ liệu đa nguồn, workflow theo quy tắc; AI Assistant thuộc Won't Have | Huỳnh Minh Tài |
| 2.0 | 07/11/2026 | **Giai đoạn thay đổi phạm vi và tầm nhìn (DEC-18):** chuyển định hướng sang CRM.AI xoay quanh bài toán rò rỉ giá trị khách hàng; bổ sung kênh hội thoại, lớp AI có kiểm soát và chỉ số rò rỉ giá trị; giữ nguyên toàn bộ lõi CRM đã triển khai; DISC dành cho KLTN | Huỳnh Minh Tài |

Tên đề tài đã đăng ký được giữ nguyên. Việc bổ sung định hướng CRM.AI và thay đổi MoSCoW cần giảng viên hướng dẫn xác nhận.

---

# PHẦN I. ĐỀ XUẤT TIỂU LUẬN CHUYÊN NGÀNH

## 1. Tóm tắt ý tưởng đề tài

POSE là hệ thống quản lý quan hệ khách hàng (Customer Relationship Management – CRM) có lõi nghiệp vụ dùng chung cho nhiều loại doanh nghiệp. Lõi CRM tập trung dữ liệu khách hàng, sản phẩm hoặc dịch vụ (Offering), phạm vi phụ trách của nhân viên sale, cơ hội bán hàng, tương tác và nhiệm vụ chăm sóc trên một nền tảng có kiểm soát quyền tại backend.

Phiên bản 2.0 đặt POSE vào một bài toán kinh doanh cụ thể: **doanh nghiệp có lead và tệp khách hàng nhưng không biến chúng thành tài sản**. Giá trị bị rò rỉ vì lead không được theo dõi kịp thời, tri thức về khách hàng nằm trong đầu từng nhân viên sale và doanh nghiệp không biết nên dồn sức vào khách hàng nào. CRM truyền thống chỉ ghi lại những gì nhân viên tự nhập, nên không giải quyết được gốc của vấn đề.

POSE CRM.AI giải bài toán bằng một vòng khép kín: tương tác với khách hàng được tiếp nhận trực tiếp từ kênh hội thoại, mỗi lần tư vấn sinh ra dữ liệu có cấu trúc, AI hỗ trợ hiểu khách hàng và tóm tắt ngữ cảnh, hệ thống định lượng giá trị đang bị rò rỉ và nhân viên quyết định hành động. Kết quả của mỗi gợi ý được ghi nhận để đánh giá và cải thiện. AI trong POSE luôn có người duyệt, tôn trọng phạm vi dữ liệu của từng vai trò và không tự hành động ra bên ngoài.

Trong giai đoạn TLCN, nhóm hoàn thiện lõi CRM đã xây dựng, chốt kiến trúc CRM.AI và triển khai một lát cắt chạy được: hộp thư hội thoại với một kênh thật, AI Copilot có kiểm soát, tín hiệu khách hàng có phản hồi của người dùng và nhóm chỉ số rò rỉ giá trị. Phân khúc RFM, dự đoán CLV, nhận diện phong cách giao tiếp (DISC), Next Best Action bằng mô hình và omnichannel đầy đủ thuộc giai đoạn KLTN.

> TIẾP NHẬN ĐA KÊNH → HIỂU KHÁCH HÀNG → ĐỊNH LƯỢNG GIÁ TRỊ → ĐỀ XUẤT HÀNH ĐỘNG → THỰC THI VÀ ĐO KẾT QUẢ → HỌC LẠI

## 2. Bối cảnh và bài toán cần giải quyết

### 2.1. Bài toán gốc: rò rỉ giá trị khách hàng

Doanh nghiệp ở các lĩnh vực khác nhau đều đầu tư để có lead và khách hàng, nhưng phần lớn giá trị của tệp khách hàng không được khai thác. Giá trị bị rò rỉ ở ba điểm:

1. **Rò rỉ lead.** Lead không được phản hồi kịp thời hoặc không được theo dõi tiếp nên nguội dần. Trên dữ liệu demo ngành giáo dục tháng 09/2026, dashboard POSE ghi nhận chỉ 6/10 lead được phản hồi, thời gian phản hồi trung bình 2 ngày, và 8 hồ sơ chưa từng được tương tác.
2. **Phụ thuộc vào cá nhân sale.** Nhu cầu, cách trao đổi, cam kết đã đưa ra và lịch sử tư vấn nằm trong điện thoại và trí nhớ của nhân viên. Khi nhân viên nghỉ việc hoặc được điều chuyển, khách hàng mất theo hoặc người tiếp nhận phải bắt đầu lại.
3. **Không biết dồn sức vào đâu.** Doanh nghiệp không biết khách hàng nào có giá trị lâu dài, khách hàng nào sắp rời bỏ và nên tiếp cận từng khách hàng theo cách nào để chốt được giao dịch.

Nguyên nhân chung là tương tác thực tế diễn ra trên Zalo, Messenger, điện thoại hoặc email, còn CRM chỉ nhận một bản ghi chú do nhân viên tự nhập lại. Dữ liệu tư vấn — nguồn hiểu biết giá trị nhất về khách hàng — không trở thành tài sản của doanh nghiệp.

### 2.2. Các vấn đề nghiệp vụ

| Vấn đề | Ảnh hưởng đến doanh nghiệp |
|---|---|
| Dữ liệu khách hàng phân tán | Khó xác định hồ sơ chính xác và theo dõi toàn bộ quá trình chăm sóc. |
| Phạm vi sale chưa rõ | Nhân viên có thể xem hoặc cập nhật sản phẩm, dịch vụ ngoài trách nhiệm được giao. |
| Danh mục khác nhau giữa các ngành | Khó dùng chung một cấu trúc dữ liệu nếu sản phẩm và dịch vụ bị thiết kế cố định. |
| Tương tác không được ghi nhận | Hội thoại diễn ra ngoài CRM; nhân viên phải nhập lại, dễ thiếu hoặc sai lệch. |
| Rò rỉ lead | Lead không được phản hồi hoặc theo dõi kịp thời; doanh nghiệp không biết mình đang mất bao nhiêu. |
| Tri thức phụ thuộc cá nhân | Đổi người phụ trách làm mất ngữ cảnh khách hàng và cơ hội đang mở. |
| Không có thứ tự ưu tiên | Nhân viên chăm sóc theo cảm tính, bỏ sót khách hàng có giá trị cao hoặc có nguy cơ rời bỏ. |
| Báo cáo tách rời | Khó đánh giá kết quả theo khách hàng, nhân viên, sản phẩm hoặc nguồn dữ liệu. |
| Dữ liệu chưa chuẩn hóa | Khó đối chiếu và phát triển RFM, CLV hoặc học máy trong giai đoạn sau. |

*Bảng 1. Tổng hợp các vấn đề nghiệp vụ cần giải quyết*

### 2.3. Từ khóa chính

| Từ khóa | Ý nghĩa trong POSE |
|---|---|
| AI-native CRM | AI nằm trong luồng dữ liệu của CRM, không phải chatbox gắn bên cạnh. |
| Lead / Revenue leakage | Giá trị bị mất do lead và cơ hội không được theo dõi. |
| Customer Lifetime Value (CLV), Customer Equity | Giá trị tương lai của một khách hàng và của cả tệp khách hàng. |
| Value at Risk | Giá trị đang có nguy cơ mất: lead quá hạn phản hồi, cơ hội không có tương tác, khách hàng vừa đổi người phụ trách. |
| Conversation Intelligence | Trích ý định, cảm xúc, mức khẩn cấp, phản đối và sản phẩm quan tâm từ hội thoại. |
| Knowledge retention | Giữ lại tri thức về khách hàng khi đổi người phụ trách. |
| Human-in-the-loop | Mọi gợi ý AI được người dùng chấp nhận, sửa hoặc từ chối trước khi có hiệu lực. |
| Explainable AI | Mỗi gợi ý có bằng chứng trích từ dữ liệu gốc và độ tin cậy. |
| Closed-loop learning | Kết quả thật (phản hồi, chốt hoặc mất cơ hội) quay lại thành dữ liệu đánh giá AI. |

*Bảng 2. Các từ khóa của đề tài*

## 3. Giải pháp đề xuất

POSE được xây dựng theo kiến trúc Modular Monolith với sáu tầng: tiếp nhận kênh, lõi CRM, event backbone, lớp trí tuệ (Intelligence), lớp quản trị AI (Governance) và trải nghiệm người dùng. Lõi CRM và event backbone đã được triển khai trong các tuần trước; giai đoạn còn lại bổ sung tầng tiếp nhận kênh, Intelligence và Governance ở mức lát cắt mỏng. Chi tiết tại tài liệu *Kiến trúc CRM.AI v1*.

### 3.1. Quản lý tổ chức, sản phẩm và dịch vụ (đã triển khai)

- Quản lý doanh nghiệp, nhóm sale, người dùng, vai trò và quyền.
- Quản lý danh mục sản phẩm, dịch vụ, nhóm phân loại, giá tham khảo và thuộc tính mở rộng theo ngành.
- Phân công Offering cho nhân viên sale theo thời gian hiệu lực; lưu lịch sử phân công.

Nhân viên sale chỉ được xem và cập nhật dữ liệu trong phạm vi Offering được giao. Người quản lý theo dõi, điều chuyển phạm vi và kiểm tra lịch sử thay đổi.

### 3.2. Quản lý khách hàng và hoạt động sale (đã triển khai)

- Quản lý Lead, Customer, nguồn khách hàng và người phụ trách; chuyển Lead thành Customer.
- Quản lý cơ hội bán hàng nhiều dòng Offering, giai đoạn, giá trị kỳ vọng, thắng hoặc thua.
- Ghi nhận tương tác theo loại và kênh; tạo, giao và theo dõi nhiệm vụ chăm sóc.

### 3.3. Hồ sơ Customer 360 (đã triển khai)

Customer 360 tổng hợp thông tin định danh, người phụ trách, Offering quan tâm, cơ hội, tương tác, phản hồi và nhiệm vụ theo phạm vi quyền của người xem. Nhân viên dữ liệu chỉ thấy metadata và số đếm phục vụ đối soát. Ở phiên bản 2.0, Customer 360 bổ sung bản tóm tắt do AI soạn và các tín hiệu khách hàng đã được xác nhận.

### 3.4. Dữ liệu đa nguồn

Mỗi bản ghi có thể gắn với hệ thống nguồn và mã tham chiếu bên ngoài. Định danh trên kênh hội thoại (ví dụ mã người dùng Telegram) được lưu như một liên kết nguồn để đối chiếu khách hàng. Dữ liệu demo đa ngành được nạp bằng gói dữ liệu V2 và script của nhóm dữ liệu; giao diện import trong ứng dụng chuyển xuống mức Could Have.

### 3.5. Kênh hội thoại và hộp thư (mới)

- Kết nối kênh hội thoại qua adapter: Telegram Bot là kênh thật bắt buộc; Messenger (chế độ phát triển) là kênh thật thứ hai nếu kịp tiến độ; Zalo được mô phỏng bằng webhook cùng định dạng để trình diễn luồng, Zalo Official Account thật thuộc KLTN.
- Tin nhắn đến được xác thực nguồn, chống nhận trùng theo mã tin nhắn của nền tảng, lưu vào hội thoại và đối chiếu với Lead hoặc Customer. Người gửi chưa có hồ sơ được tạo Lead mới theo quy tắc.
- Mỗi phiên hội thoại sinh một Interaction với kênh tương ứng, nên lịch sử tư vấn tự động có trong Customer 360 và dashboard mà không cần nhập lại.
- Nhân viên xem và trả lời khách hàng ngay trong hộp thư của POSE, trong phạm vi khách hàng mình được phép thao tác và trong cửa sổ trả lời mà nền tảng cho phép.

### 3.6. Lớp AI có kiểm soát (mới)

**AI Copilot.** Tóm tắt hồ sơ Customer 360 và hội thoại; soạn hồ sơ bàn giao khi đổi người phụ trách; soạn nháp câu trả lời. Bản nháp chỉ được gửi khi nhân viên bấm gửi.

**Tín hiệu khách hàng (Signal).** Từ tin nhắn đến, AI trích ý định, cảm xúc, mức khẩn cấp, phản đối và Offering quan tâm. Mỗi tín hiệu có câu trích dẫn làm bằng chứng, độ tin cậy và phiên bản mô hình. Nhân viên chấp nhận, sửa hoặc từ chối; chỉ tín hiệu đã chấp nhận mới được dùng cho workflow và dashboard. Mỗi lần xác nhận trở thành dữ liệu có nhãn cho đánh giá ở TLCN và huấn luyện ở KLTN.

**Chỉ số rò rỉ giá trị (Value at Risk).** Dashboard bổ sung nhóm chỉ số giải thích được bằng công thức: lead quá hạn phản hồi, cơ hội đang mở không có tương tác trong N ngày cùng tổng giá trị kỳ vọng, khách hàng vừa đổi người phụ trách chưa được liên hệ và thời gian phản hồi tin nhắn đầu tiên trên kênh.

### 3.7. Tự động hóa quy trình

Workflow chạy theo quy tắc qua module `workflow-core` với outbox, khóa idempotency và theo dõi retry (đã triển khai cho sự kiện Lead đủ điều kiện tạo nhiệm vụ). Phiên bản 2.0 bổ sung sự kiện tin nhắn đến: tạo nhiệm vụ phản hồi đầu tiên cho lead mới từ kênh và kích hoạt trích tín hiệu khi AI được bật. AI không tự ra quyết định thay đổi trạng thái nghiệp vụ.

### 3.8. Dashboard và báo cáo

Dashboard hiện có 16 KPI thuộc bốn nhóm (Lead, Nhiệm vụ, Workflow, Tương tác) với phạm vi theo vai trò. Phiên bản 2.0 bổ sung nhóm E — Giá trị và rò rỉ — cùng chỉ số theo kênh và tỷ lệ gợi ý AI được chấp nhận.

### 3.9. Phân quyền, truy vết và quản trị AI

Xác thực và phân quyền theo vai trò được kiểm tra tại backend; thao tác quan trọng được ghi audit log. Lớp quản trị AI bảo đảm: AI chỉ nhận dữ liệu mà người gọi được phép xem; dữ liệu định danh được che trước khi gửi tới nhà cung cấp mô hình; mỗi lần gọi AI được ghi nhật ký (mục đích, mô hình, phiên bản prompt, số token, chi phí ước tính, kết quả); AI tắt theo mặc định và có giới hạn chi phí; không có hành động tự động ra bên ngoài.

### 3.10. Khác biệt của POSE CRM.AI

| Tiêu chí | CRM truyền thống | CRM gắn chatbot | POSE CRM.AI |
|---|---|---|---|
| Dữ liệu tương tác | Nhân viên tự nhập | Nhân viên tự nhập | Tiếp nhận trực tiếp từ kênh; mỗi lần tư vấn sinh dữ liệu |
| Hiểu khách hàng | Ghi chú tự do | Hỏi thì AI trả lời | Tín hiệu có bằng chứng, được người dùng xác nhận |
| Giá trị | Doanh số đã chốt | Không có | Giá trị đang rò rỉ; CLV và giá trị tệp khách hàng ở KLTN |
| Khi đổi người phụ trách | Mất ngữ cảnh | Mất ngữ cảnh | Hồ sơ bàn giao do AI soạn từ toàn bộ lịch sử |
| Kiểm soát | Theo vai trò | Thường bỏ qua phạm vi dữ liệu | AI tuân theo RBAC, có nhật ký và người duyệt |

*Bảng 3. So sánh POSE CRM.AI với các cách tiếp cận khác*

## 4. Đối tượng sử dụng

| Vai trò | Nhu cầu chính | Phạm vi dữ liệu | Quyền AI và hội thoại |
|---|---|---|---|
| Quản trị hệ thống | Tài khoản, vai trò, quyền, cấu hình kênh và AI, audit log | Toàn tổ chức | Cấu hình kênh, bật/tắt AI, xem nhật ký AI |
| Người quản lý | Nhóm, Offering, phân công, theo dõi báo cáo và rò rỉ | Nhóm phụ trách | Đọc và trả lời hội thoại trong nhóm; dùng Copilot; duyệt tín hiệu |
| Nhân viên sale | Lead, Customer, cơ hội, hội thoại được giao | Phạm vi phân công | Đọc và trả lời hội thoại của mình; dùng Copilot; duyệt tín hiệu |
| Nhân viên chăm sóc | Customer 360, tương tác, nhiệm vụ chăm sóc | Khách hàng được giao chăm sóc | Đọc và trả lời hội thoại của khách được giao; dùng Copilot |
| Nhân viên dữ liệu | Kiểm tra dữ liệu, đối soát, đánh giá chất lượng AI | Metadata và số liệu tổng hợp | Chỉ số liệu tổng hợp về AI; không đọc nội dung hội thoại |

*Bảng 4. Các nhóm người dùng chính của hệ thống*

## 5. Mục tiêu của tiểu luận chuyên ngành

### 5.1. Mục tiêu tổng quát

Xây dựng MVP của hệ thống CRM đa ngành theo định hướng CRM.AI: quản lý tập trung khách hàng, Offering, phạm vi sale và hoạt động chăm sóc; tiếp nhận tương tác trực tiếp từ kênh hội thoại; dùng AI có kiểm soát để giảm rò rỉ giá trị khách hàng; và chứng minh kiến trúc đủ nền cho phân tích, dự đoán ở KLTN.

### 5.2. Mục tiêu cụ thể

1. Xác thực, phân quyền theo vai trò và phạm vi dữ liệu tại backend. *(đã đạt)*
2. Quản lý tổ chức, nhóm, người dùng và Offering đa ngành có thuộc tính mở rộng. *(đã đạt)*
3. Phân công Offering cho sale và kiểm soát phạm vi truy cập. *(đã đạt)*
4. Quản lý Lead, Customer, cơ hội, tương tác, nhiệm vụ và Customer 360. *(đã đạt)*
5. Workflow theo quy tắc có outbox, idempotency và retry. *(đã đạt)*
6. Dashboard KPI theo phạm vi vai trò, đối soát được với SQL độc lập. *(đã đạt)*
7. Chốt kiến trúc CRM.AI và mô hình dữ liệu cho hội thoại, AI và tín hiệu.
8. Tiếp nhận và trả lời hội thoại từ ít nhất một kênh thật, tự động sinh Interaction và Lead.
9. Triển khai AI Copilot có kiểm soát: tóm tắt Customer 360, hồ sơ bàn giao và bản nháp trả lời.
10. Trích tín hiệu khách hàng có bằng chứng, thu phản hồi người dùng và đánh giá trên bộ nhãn chuẩn.
11. Đo rò rỉ giá trị bằng nhóm KPI giải thích được.
12. Triển khai MVP có dữ liệu demo đa ngành, kịch bản trình diễn và bằng chứng kiểm thử.

## 6. Phạm vi thực hiện trong tiểu luận chuyên ngành

| Mức ưu tiên | Nội dung |
|---|---|
| Must Have | Đăng nhập và RBAC; tổ chức, nhóm, người dùng; Offering; phân công sale; Lead, Customer; Opportunity; Interaction; Task; Customer 360; dashboard 16 KPI; audit log; workflow theo quy tắc *(đã triển khai)*. **Mới:** kiến trúc CRM.AI v1; hộp thư hội thoại với kênh Telegram và Zalo mô phỏng; AI Gateway có quản trị (RBAC, che dữ liệu, nhật ký, giới hạn chi phí, tắt mặc định); AI Copilot tóm tắt Customer 360 và hồ sơ bàn giao. |
| Should Have | Tín hiệu khách hàng (ý định, cảm xúc) có phản hồi người dùng và báo cáo đánh giá trên bộ nhãn chuẩn; nhóm KPI E — Giá trị và rò rỉ; bản nháp trả lời; kênh Messenger chế độ phát triển; danh sách "Việc hôm nay" sắp theo quy tắc giải thích được; thuộc tính mở rộng theo ngành; báo cáo chất lượng dữ liệu. |
| Could Have | Yêu cầu hỗ trợ (Support request); quy trình duyệt Handover (thay bằng hồ sơ bàn giao AI trên luồng đổi người phụ trách hiện có); giao diện import trong ứng dụng; cập nhật hội thoại thời gian thực (SSE); chiến dịch cơ bản; export Excel/PDF. |
| Won't Have Now | Nhận diện phong cách giao tiếp DISC; RFM nâng cao; Predictive CLV; huấn luyện mô hình học máy; Next Best Action bằng mô hình; AI tự gửi tin hoặc tự thay đổi dữ liệu; Zalo Official Account thật và tin ZNS; quản trị Admin/HR nhiều cấp; workflow phê duyệt phức tạp. |

*Bảng 5. Phạm vi MVP theo phương pháp MoSCoW (phiên bản 2.0)*

## 7. Yêu cầu chức năng mức cao

| Mã | Yêu cầu | Trạng thái |
|---|---|---|
| FR-01 | Quản lý tài khoản, vai trò và quyền truy cập. | Đã triển khai |
| FR-02 | Quản lý doanh nghiệp, nhóm và nhân viên. | Đã triển khai |
| FR-03 | Quản lý danh mục sản phẩm, dịch vụ và thuộc tính mở rộng. | Đã triển khai |
| FR-04 | Phân công Offering cho nhân viên sale theo thời gian hiệu lực. | Đã triển khai, đang kiểm thử |
| FR-05 | Quản lý khách hàng, lead, nguồn dữ liệu và người phụ trách. | Đã triển khai |
| FR-06 | Quản lý cơ hội bán hàng nhiều dòng Offering. | Đã triển khai |
| FR-07 | Hiển thị hồ sơ Customer 360 theo phạm vi quyền. | Đã triển khai |
| FR-08 | Ghi nhận tương tác và phản hồi; yêu cầu hỗ trợ ở mức Could. | Tương tác đã triển khai |
| FR-09 | Tạo, phân công và theo dõi nhiệm vụ chăm sóc. | Đã triển khai |
| FR-10 | Import dữ liệu và báo cáo lỗi hoặc nghi ngờ trùng. | Nạp bằng gói dữ liệu; giao diện ở mức Could |
| FR-11 | Dashboard và báo cáo theo bộ lọc và phạm vi vai trò. | Đã triển khai (16 KPI) |
| FR-12 | Workflow tự động theo sự kiện, có idempotency và retry. | Đã triển khai (Lead đủ điều kiện) |
| FR-13 | Ghi audit log cho thao tác quan trọng. | Đã triển khai |
| FR-14 | Kết nối kênh hội thoại, nhận tin qua webhook có xác thực và chống trùng. | Mới |
| FR-15 | Hộp thư hội thoại: xem, gắn khách hàng, trả lời trong phạm vi quyền; tự sinh Interaction và Lead. | Mới |
| FR-16 | AI Copilot: tóm tắt Customer 360 và hội thoại, hồ sơ bàn giao, bản nháp trả lời. | Mới |
| FR-17 | Tín hiệu khách hàng có bằng chứng, độ tin cậy và phản hồi chấp nhận, sửa hoặc từ chối. | Mới |
| FR-18 | Nhóm KPI Giá trị và rò rỉ (Value at Risk). | Mới |
| FR-19 | Quản trị AI: cấu hình, bật/tắt, giới hạn chi phí, nhật ký gọi AI và chỉ số chất lượng. | Mới |

*Bảng 6. Danh sách yêu cầu chức năng mức cao*

## 8. Yêu cầu phi chức năng

| Nhóm | Yêu cầu |
|---|---|
| Bảo mật | Mật khẩu được băm; API có xác thực; quyền kiểm tra tại backend; webhook xác thực chữ ký hoặc secret token của nền tảng; khóa API và token kênh chỉ nằm trong biến môi trường, không lưu trong mã nguồn hay cơ sở dữ liệu. |
| Quyền riêng tư | Che số điện thoại, email và giấy tờ định danh trước khi gửi tới mô hình; chỉ gửi dữ liệu tối thiểu cho mục đích; không suy luận thuộc tính nhạy cảm; hội thoại có trạng thái đồng ý xử lý dữ liệu theo quy định bảo vệ dữ liệu cá nhân. |
| An toàn AI | AI tắt theo mặc định; mọi gợi ý cần người duyệt; không tự gửi tin hay thay đổi dữ liệu nghiệp vụ; có giới hạn chi phí theo tổ chức; lỗi hoặc hết hạn mức AI không làm gián đoạn chức năng CRM. |
| Hiệu năng | API tra cứu phổ biến dưới 3 giây trong môi trường demo; webhook trả xác nhận dưới 2 giây và xử lý tiếp bất đồng bộ; tác vụ AI có giới hạn thời gian và trạng thái đang xử lý trên giao diện. |
| Tính nhất quán | Phân công, trạng thái cơ hội, nhiệm vụ, hội thoại và Customer 360 cập nhật đúng business rule và giao dịch; tin nhắn trùng không tạo bản ghi trùng. |
| Khả năng mở rộng | Adapter kênh và nhà cung cấp AI thay thế được qua giao diện chung; thuộc tính theo ngành cấu hình có kiểm soát. |
| Khả năng kiểm thử | Unit, integration (PostgreSQL cô lập) và E2E cho luồng cốt lõi; nhà cung cấp AI giả lập cho kiểm thử tự động; bộ nhãn chuẩn để đánh giá AI. |
| Khả năng sử dụng | Giao diện tiếng Việt, responsive, hướng tới WCAG 2.2 AA; trạng thái đang xử lý, lỗi, rỗng, không có quyền rõ ràng; gợi ý AI được đánh dấu là do AI tạo. |
| Truy vết | Lưu lịch sử phân công, dữ liệu khách hàng, cơ hội, workflow, tin nhắn, lần gọi AI và quyết định của người dùng với gợi ý. |
| Triển khai | Có hướng dẫn chạy local; Supabase làm cơ sở dữ liệu dùng chung; kênh demo dùng tunnel HTTPS; có kịch bản dự phòng bằng kênh mô phỏng khi mạng không ổn định. |

*Bảng 7. Các yêu cầu phi chức năng chính*

## 9. Kiến trúc và công nghệ

POSE dùng kiến trúc web nhiều lớp và Modular Monolith. Backend gồm các module Identity & Access, Organization, Offering, Sales Assignment, Customer & Lead, Opportunity, Interaction & Task, Customer 360, Dashboard, Audit, Workflow; phiên bản 2.0 bổ sung Channel (adapter và hội thoại), AI Gateway, Copilot, Signal và Value Insight.

```
┌──────────── Trải nghiệm ─────────────────────────────────────────┐
│ Hộp thư hội thoại · Customer 360 + tóm tắt và tín hiệu ·         │
│ Việc hôm nay · Dashboard giá trị và rò rỉ                        │
├──────────── Intelligence ────────────────────────────────────────┤
│ Signal · Hồ sơ hiểu khách hàng · Value Insight · Copilot         │
├──────────── Governance ──────────────────────────────────────────┤
│ Ngữ cảnh theo RBAC · che dữ liệu · nhật ký AI · người duyệt ·    │
│ kho phản hồi và nhãn · đánh giá · giới hạn chi phí               │
├──────────── Event backbone: outbox, idempotency, retry ──────────┤
├──────────── Lõi CRM: Lead, Customer, Opportunity, Interaction,   │
│             Task, Customer 360, Dashboard, Audit                 │
├──────────── Tiếp nhận: adapter Telegram, Messenger, Zalo mô      │
│             phỏng; webhook; liên kết nguồn                       │
└──────────────────────────────────────────────────────────────────┘
      ▲ kết quả thật và quyết định của người dùng quay lại kho nhãn
```

*Hình 1. Kiến trúc phân tầng của POSE CRM.AI*

| Thành phần | Công nghệ | Vai trò |
|---|---|---|
| Frontend | Next.js 16, React 19, TypeScript, Tailwind CSS, TanStack Query, Recharts 3 | Giao diện CRM, hộp thư, Copilot, Customer 360 và dashboard; BFF chuyển tiếp API. |
| Backend API | Java 25, Spring Boot 3.5, Spring Security, Spring Data JPA, Flyway, Maven đa module | Business rule, xác thực, phân quyền, giao dịch, audit, adapter kênh và AI Gateway. |
| Workflow | Module `workflow-core` (outbox, idempotency, retry) | Thực thi workflow theo sự kiện; n8n không còn là thành phần bắt buộc. |
| Cơ sở dữ liệu | PostgreSQL (Supabase dùng chung; cluster cô lập cho kiểm thử) | Dữ liệu nghiệp vụ, hội thoại, nhật ký AI, tín hiệu và audit. |
| Mô hình AI | Claude API qua AI Gateway (Claude Haiku cho trích tín hiệu, Claude Sonnet cho tóm tắt); nhà cung cấp giả lập cho kiểm thử | Tóm tắt, soạn nháp, trích tín hiệu; thay nhà cung cấp được qua giao diện chung. |
| Kênh hội thoại | Telegram Bot API; Messenger Platform (chế độ phát triển); Zalo mô phỏng; tunnel HTTPS khi demo | Nhận và gửi tin nhắn. |
| Dữ liệu và đánh giá | SQL, Python (gói dữ liệu V2, script đối soát, bộ nhãn chuẩn) | Dữ liệu demo đa ngành, đối soát KPI, đánh giá AI. |
| Kiểm thử | JUnit 5, Mockito, PostgreSQL test cô lập, Vitest, Playwright | Kiểm thử backend, API, giao diện, E2E và an toàn AI. |
| Triển khai | Hướng dẫn chạy local, Supabase, GitHub; Docker Compose ở giai đoạn ổn định | Môi trường demo và bàn giao. |

*Bảng 8. Kiến trúc công nghệ của hệ thống*

## 10. Luồng nghiệp vụ minh họa

1. Người quản lý tạo Offering và phân công cho nhân viên sale.
2. Một khách hàng mới nhắn tin cho doanh nghiệp qua Telegram. POSE xác thực webhook, lưu tin nhắn, không tìm thấy hồ sơ nên tạo Lead mới, gắn hội thoại cho nhân viên phụ trách và tạo nhiệm vụ phản hồi đầu tiên.
3. Khi AI được bật, POSE trích tín hiệu từ tin nhắn: ý định hỏi giá, cảm xúc trung tính, Offering quan tâm. Tín hiệu hiện trong hộp thư kèm câu trích dẫn; nhân viên chấp nhận hoặc sửa.
4. Nhân viên mở Copilot để xem tóm tắt, nhận bản nháp trả lời, chỉnh sửa và bấm gửi. Phiên hội thoại sinh Interaction kênh Telegram trong Customer 360.
5. Lead được đánh giá đủ điều kiện; workflow tạo nhiệm vụ theo dõi; cơ hội được tạo với giá trị kỳ vọng.
6. Nhân viên sale nghỉ việc; người quản lý đổi người phụ trách. Copilot soạn hồ sơ bàn giao gồm nhu cầu, cam kết, cơ hội đang mở và việc cần làm tiếp.
7. Dashboard cho thấy số lead quá hạn phản hồi, giá trị cơ hội không có tương tác và tỷ lệ gợi ý AI được chấp nhận.

*Hình 2. Luồng trình diễn chính của POSE CRM.AI*

## 11. Phân chia công việc

### 11.1. Kết quả đã thực hiện đến ngày 07/11/2026

| Thành viên | Đã hoàn thành hoặc đang kiểm thử |
|---|---|
| Huỳnh Minh Tài | Kiến trúc và API contract; Auth, RBAC, Organization, Team, User (20 API); frontend foundation; Customer/Lead; Opportunity nhiều dòng; Interaction, Task, Audit core; Customer 360 backend và giao diện; workflow Lead đủ điều kiện với outbox và idempotency; Dashboard KPI API và giao diện; hardening RBAC/audit/lỗi; hướng dẫn chạy local; tích hợp nhánh và review. |
| Nguyễn Đức Thắng | Offering CRUD và wireframe; acceptance criteria; Sales Assignment (migration V5–V7); giao diện nhiệm vụ; kiểm thử workflow, E2E đa vai trò và regression QA (đang thực hiện). |
| Văn Phạm Thảo Nhi | Gói dữ liệu V2 đa ngành (6 tổ chức, 36 người dùng); Data Dictionary; quy tắc chất lượng dữ liệu cho Customer/Lead, Interaction/Task/Audit; quy tắc giám sát workflow; từ điển KPI, hợp đồng KPI theo ngành, SQL đối soát và data lineage; seed demo và báo cáo chất lượng dữ liệu (đang thực hiện). |

*Bảng 9. Khối lượng công việc đã thực hiện*

### 11.2. Phân công giai đoạn CRM.AI

| Thành viên | Vai trò | Phạm vi TLCN còn lại | Định hướng KLTN |
|---|---|---|---|
| Huỳnh Minh Tài | Technical Lead, AI & Integration Owner | Kiến trúc CRM.AI và DEC-18; AI Gateway và lớp quản trị AI; Copilot (backend và giao diện); giao diện hộp thư; Signal backend; API và giao diện KPI nhóm E; tích hợp, review và ổn định hiệu năng. | Kiến trúc Customer Intelligence; Copilot có công cụ giới hạn theo RBAC; omnichannel thời gian thực. |
| Nguyễn Đức Thắng | Channel Integration & QA Coordinator | Adapter Telegram, Zalo mô phỏng và Messenger; webhook bảo mật, chống trùng; API hội thoại và trả lời; kiểm thử E2E CRM.AI, kiểm thử an toàn AI (rò rỉ phạm vi, không tự gửi); kịch bản demo và evidence. | Zalo OA thật, phân luồng hội thoại; Next Best Action bằng rule engine; đo hiệu quả theo Offering. |
| Văn Phạm Thảo Nhi | Data & AI Evaluation Owner | Bộ hội thoại demo tiếng Việt đa ngành; hướng dẫn gán nhãn và bộ nhãn chuẩn ý định, cảm xúc; báo cáo đánh giá AI; chất lượng dữ liệu cho tin nhắn và tín hiệu; từ điển và SQL đối soát KPI nhóm E; cập nhật Data Dictionary cho các migration mới. | Data Mart; RFM; Predictive CLV và giá trị tệp khách hàng; DISC; đánh giá mô hình. |

*Bảng 10. Phân công và định hướng phát triển của nhóm*

Mỗi thành viên chịu trách nhiệm từ phân tích, dữ liệu, API đến kiểm thử phần mình sở hữu; tích hợp theo lát cắt dọc gồm cơ sở dữ liệu, backend, giao diện, dữ liệu demo và kiểm thử.

## 12. Kế hoạch thực hiện

Giai đoạn 1–2 (tuần 1–8, 11/09–08/11/2026) đã hoàn thành lõi CRM. Kế hoạch còn lại khoảng tám tuần:

| Giai đoạn | Thời gian (dự kiến) | Trọng tâm | Đầu ra chính |
|---|---|---|---|
| 3. Chuyển hướng | 09/11–15/11 | Chốt CRM.AI | SRS 2.0, kiến trúc CRM.AI v1, DEC-18, thiết kế dữ liệu hội thoại và AI; thiết lập Telegram bot, khóa API, tunnel; khép QA W08, W10, W11. |
| 4. Tiếp nhận kênh | 16/11–29/11 | Hộp thư và AI Gateway | Hộp thư Telegram và Zalo mô phỏng; tự sinh Lead và Interaction; AI Gateway có quản trị; bộ hội thoại demo và bộ nhãn. |
| 5. Trí tuệ v0 | 30/11–13/12 | Copilot và Signal | Tóm tắt, hồ sơ bàn giao, bản nháp; tín hiệu có phản hồi; Messenger nếu kịp. |
| 6. Giá trị | 14/12–20/12 | Value at Risk | KPI nhóm E; Việc hôm nay; báo cáo đánh giá AI. |
| 7. Ổn định | 21/12–27/12 | Release candidate | Đóng băng tính năng; hiệu năng; sửa lỗi; tài liệu kỹ thuật và báo cáo kiểm thử. |
| 8. Nghiệm thu | 28/12/2026–03/01/2027 | Bàn giao | Acceptance test, diễn tập demo, video, báo cáo, khóa phiên bản. |

*Bảng 11. Kế hoạch thực hiện giai đoạn còn lại. Mốc kết thúc được dời theo lịch nộp chính thức nếu khác.*

## 13. Phương pháp đánh giá và tính khả thi

| Nội dung | Cách đánh giá |
|---|---|
| Chức năng | Kiểm thử theo use case và business rule; luồng Must Have chạy xuyên suốt. |
| Phân quyền | Mỗi vai trò chỉ đọc, ghi và nhận gợi ý AI trong phạm vi được giao; có test âm cho rò rỉ dữ liệu qua AI. |
| Kênh hội thoại | Gửi lặp cùng một tin không tạo bản ghi trùng; webhook sai chữ ký bị từ chối; tin đến sinh Lead và Interaction đúng. |
| Chất lượng AI | Trên bộ nhãn chuẩn: macro-F1 cho ý định và cảm xúc, độ đồng thuận giữa hai người gán nhãn; tỷ lệ gợi ý được chấp nhận; kiểm tra mẫu tính trung thực của bản tóm tắt (không nêu điều không có trong dữ liệu). |
| Chi phí và độ trễ | Chi phí ước tính trên 100 tin nhắn; độ trễ p95 của tác vụ AI trong môi trường demo. |
| Số liệu | Đối soát dashboard với SQL độc lập của nhóm dữ liệu, gồm nhóm E. |
| Khả thi nhân sự | Lõi CRM đã xong; phần mới tách thành ba khối độc lập: kênh, AI, dữ liệu và đánh giá. |
| Khả thi công nghệ | Telegram miễn phí, không cần duyệt; Claude API có giới hạn chi phí; nhà cung cấp giả lập cho phép kiểm thử không phụ thuộc mạng. |

*Bảng 12. Phương pháp đánh giá và cơ sở xác định tính khả thi*

## 14. Rủi ro và hướng xử lý

| Rủi ro | Tác động | Biện pháp |
|---|---|---|
| Mở rộng phạm vi muộn | Không kịp ổn định trước nghiệm thu | Lát cắt mỏng, Must Have nhỏ; đóng băng tính năng ở giai đoạn 7; giữ nguyên các card ổn định cũ. |
| AI trả lời sai hoặc bịa | Mất tin cậy, quyết định sai | Người duyệt mọi gợi ý; bằng chứng trích dẫn; đánh giá trên bộ nhãn; nhãn "do AI tạo". |
| Rò rỉ dữ liệu qua AI | Vi phạm phạm vi và quyền riêng tư | Ngữ cảnh dựng theo RBAC; che dữ liệu định danh; test âm; nhật ký gọi AI. |
| Chi phí AI vượt dự kiến | Hết ngân sách | Giới hạn chi phí theo tổ chức; mô hình nhỏ cho tác vụ trích tín hiệu; nhà cung cấp giả lập khi phát triển. |
| Chính sách nền tảng kênh | Không kết nối được kênh thật | Telegram là kênh bắt buộc; Messenger ở mức Should; Zalo mô phỏng; Zalo OA đăng ký sớm cho KLTN. |
| Mạng hoặc tunnel lỗi khi demo | Trình diễn gián đoạn | Kịch bản dự phòng bằng kênh mô phỏng và dữ liệu đã ghi sẵn. |
| Khối lượng không cân bằng | Một người thành điểm nghẽn | Phân khối độc lập theo bảng 10; review chéo; theo dõi trên Trello theo giai đoạn. |
| Dữ liệu demo hội thoại thiếu thực tế | Đánh giá AI sai lệch | Hướng dẫn gán nhãn, hai người gán nhãn, đo độ đồng thuận. |

*Bảng 13. Các rủi ro chính và phương án ứng phó*

## 15. Kết quả bàn giao dự kiến

- SRS 2.0, use case, business rule, ma trận phân quyền và decision log.
- Kiến trúc CRM.AI v1, Domain Class Diagram, Data Dictionary và API contract.
- Mã nguồn frontend, backend, module workflow, adapter kênh và AI Gateway.
- Cơ sở dữ liệu, migration, gói dữ liệu demo đa ngành và bộ hội thoại có nhãn.
- Bộ kiểm thử, báo cáo kiểm thử và báo cáo đánh giá AI.
- Hướng dẫn chạy local, kịch bản demo, video và báo cáo TLCN.

---

# PHẦN II. HƯỚNG PHÁT TRIỂN LÊN KHÓA LUẬN TỐT NGHIỆP

## II.1. Tên đề tài KLTN dự kiến

**PHÁT TRIỂN HỆ THỐNG CRM THÔNG MINH ỨNG DỤNG PHÂN TÍCH DỮ LIỆU VÀ AI TRONG ƯU TIÊN VÀ CÁ NHÂN HÓA CHĂM SÓC KHÁCH HÀNG**

Phương án bổ sung cần giảng viên xác nhận: *… từ dữ liệu hội thoại đa kênh*.

## II.2. Cơ sở kế thừa từ TLCN

TLCN tạo ra lõi CRM, kiến trúc CRM.AI, dữ liệu khách hàng, cơ hội, tương tác và nhiệm vụ, cùng **dữ liệu hội thoại có nhãn do chính người dùng xác nhận** và nhật ký gọi AI. Đây là đầu vào trực tiếp cho Data Mart, phân tích, dự đoán và đánh giá mô hình trong KLTN.

## II.3. Các hạng mục phát triển trọng tâm

### II.3.1. Omnichannel và hội thoại thời gian thực
Kết nối Zalo Official Account, Messenger chính thức và email; cập nhật thời gian thực; phân luồng hội thoại theo nhân viên và mức ưu tiên.

### II.3.2. Data Mart và Data Quality
Pipeline ETL từ cơ sở dữ liệu nghiệp vụ sang Data Mart cho phân tích khách hàng; fact và dimension; theo dõi dữ liệu thiếu, trùng, sai định dạng và độ trễ.

### II.3.3. Phân khúc RFM
Tính Recency, Frequency, Monetary để phân nhóm khách hàng và lưu lịch sử thay đổi phân khúc.

### II.3.4. Predictive CLV và giá trị tệp khách hàng
Dự đoán giá trị vòng đời khách hàng; tổng hợp thành giá trị tệp khách hàng (Customer Equity) và giá trị đang rủi ro; so sánh mô hình nền với mô hình học máy bằng MAE, RMSE hoặc R².

### II.3.5. Conversation Intelligence nâng cao và phong cách giao tiếp DISC
Mở rộng tín hiệu (rủi ro rời bỏ, tín hiệu mua); nhận diện phong cách giao tiếp DISC như gợi ý có độ tin cậy, không gắn nhãn tính cách cố định, luôn cho nhân viên sửa. Hạn chế khoa học của DISC và việc suy luận từ văn bản ngắn được đánh giá như một giả thuyết nghiên cứu.

### II.3.6. Customer Priority và Next Best Action
Kết hợp RFM, CLV, tín hiệu hội thoại và kết quả chăm sóc để xác định mức ưu tiên; Next Best Action triển khai bằng rule engine giải thích được trước, sau đó mới dùng mô hình khi dữ liệu đủ.

### II.3.7. AI Copilot nâng cao, đánh giá tác động và quản trị
Copilot có công cụ tra cứu bị giới hạn theo RBAC; đánh giá tác động của gợi ý tới tỷ lệ phản hồi và chốt; quản trị nhiều cấp cho Admin, HR và quản lý.

## II.4. Câu hỏi nghiên cứu

- **RQ1.** Tín hiệu trích từ hội thoại đa kênh có cải thiện dự đoán chuyển đổi và rời bỏ so với chỉ dùng RFM/CLV không?
- **RQ2.** Gợi ý ưu tiên và Next Best Action có làm giảm giá trị rò rỉ (lead quá hạn, cơ hội không tương tác) không?
- **RQ3.** Điều chỉnh cách tiếp cận theo phong cách giao tiếp có làm tăng tỷ lệ phản hồi không?

## II.5. Lộ trình kế thừa giữa hai giai đoạn

| Tiểu luận chuyên ngành | Khóa luận tốt nghiệp |
|---|---|
| Lõi CRM và dữ liệu đa nguồn | Data Mart và khai thác dữ liệu hợp nhất |
| Hộp thư một kênh thật và kênh mô phỏng | Omnichannel thật, thời gian thực, phân luồng |
| AI Copilot có kiểm soát | Copilot có công cụ, đánh giá tác động |
| Tín hiệu ý định và cảm xúc có nhãn người dùng | Conversation Intelligence nâng cao, DISC, mô hình huấn luyện trên nhãn |
| KPI rò rỉ giá trị bằng công thức | Predictive CLV, giá trị tệp khách hàng, Customer Priority |
| Workflow theo quy tắc | Next Best Action, workflow cá nhân hóa và phê duyệt |
| Phân quyền theo vai trò | Quản trị nhiều cấp |

*Bảng 14. Sự kế thừa từ TLCN lên KLTN*

## II.6. Phạm vi nghiên cứu trọng tâm của KLTN

KLTN tập trung vào Predictive CLV kết hợp tín hiệu hội thoại. RFM, Customer Priority, Next Best Action, DISC và Copilot nâng cao là lớp ứng dụng sử dụng kết quả phân tích. Data Mart bao phủ dữ liệu khách hàng, cơ hội, tương tác, hội thoại và kết quả kinh doanh.

## KẾT LUẬN

POSE giải bài toán rò rỉ giá trị khách hàng bằng một lõi CRM đa ngành có kiểm soát phạm vi và một lớp CRM.AI biến tương tác thành tài sản dữ liệu. Trong TLCN, nhóm hoàn thiện lõi CRM, chốt kiến trúc và chứng minh vòng khép kín bằng lát cắt hộp thư, Copilot, tín hiệu và chỉ số rò rỉ giá trị. KLTN kế thừa dữ liệu hội thoại có nhãn và kiến trúc này để triển khai phân tích, dự đoán và cá nhân hóa chăm sóc khách hàng.

> TIẾP NHẬN ĐA KÊNH → HIỂU KHÁCH HÀNG → ĐỊNH LƯỢNG GIÁ TRỊ → ĐỀ XUẤT HÀNH ĐỘNG → THỰC THI VÀ ĐO KẾT QUẢ → HỌC LẠI
