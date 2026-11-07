# POSE CRM — Project Context

## 1. Thông tin chung

- Tên đề tài: **Xây dựng hệ thống CRM hỗ trợ quản lý và chăm sóc khách hàng cho doanh nghiệp**.
- Tên tiếng Anh: **Developing a Customer Relationship Management (CRM) System for Businesses**.
- Phạm vi: hệ thống CRM có thể áp dụng cho nhiều loại hình doanh nghiệp; không còn phụ thuộc vào EduZ hay một ngành kinh doanh cụ thể.
- Thời gian TLCN: khoảng **14 tuần**, kế hoạch còn lại 7 tuần W12–W18, đến khoảng 27/12/2026 (DEC-18, điều chỉnh 08/11/2026).
- Định hướng sản phẩm từ 07/11/2026: **POSE CRM.AI — biến mỗi tương tác thành tài sản khách hàng**. Mốc **giai đoạn thay đổi phạm vi và tầm nhìn** ghi tại [DEC-18](meetings/decision-log.md#dec-18--chuyển-hướng-phạm-vi-và-tầm-nhìn-crmai); yêu cầu tại [SRS 2.0](SRS-POSE-v2-CRM-AI.md); kiến trúc tại [Kiến trúc CRM.AI v1](api/architecture-crm-ai-v1.md).

## 2. Bài toán và luồng nghiệp vụ chính

Bài toán gốc (SRS 2.0): **rò rỉ giá trị khách hàng** — lead không được theo dõi kịp thời, tri thức về khách hàng phụ thuộc cá nhân sale, doanh nghiệp không biết nên dồn sức vào khách hàng nào. Vòng CRM.AI: tiếp nhận đa kênh → hiểu khách hàng → định lượng giá trị → đề xuất hành động → thực thi và đo kết quả → học lại.

Doanh nghiệp cần quản lý tập trung khách hàng, nhân viên sales, sản phẩm/dịch vụ được phân công và các hoạt động chăm sóc. Hệ thống cần giúp theo dõi tiến trình từ khách hàng tiềm năng đến chăm sóc sau bán, đồng thời bảo đảm dữ liệu có thể áp dụng cho nhiều ngành.

Luồng MVP:

`Organization/Team → User & Role → Product/Service Offering → Sales Assignment → Lead/Customer → Opportunity → Interaction/CRM Task → Customer 360 → Dashboard`.

Luồng CRM.AI bổ sung:

`Kênh hội thoại → Conversation → Lead/Customer + Interaction tự động → Signal/Copilot (người duyệt) → Task/Opportunity → Dashboard nhóm E`.

## 3. Phạm vi TLCN (SRS 2.0)

Đã triển khai (giữ nguyên):

- Xác thực, RBAC và phạm vi truy cập theo tổ chức/nhóm; Organization, Team, User và Sales Assignment.
- Product/Service Offering đa ngành; Lead/Customer, Opportunity nhiều dòng; Interaction, CRM Task, Audit Log.
- Customer 360; workflow rule-based có outbox, idempotency và retry; Dashboard 16 KPI; dữ liệu demo đa ngành và đối soát số liệu.

Bổ sung theo CRM.AI (DEC-18):

- **Must:** kiến trúc CRM.AI v1; hộp thư hội thoại với Telegram và Zalo mô phỏng (tin đến tự sinh Lead và Interaction); AI Gateway có quản trị (RBAC, che dữ liệu, nhật ký, hạn mức, tắt mặc định); Copilot tóm tắt Customer 360 và hồ sơ bàn giao.
- **Should:** tín hiệu khách hàng (ý định, cảm xúc) có phản hồi người dùng và báo cáo đánh giá trên bộ nhãn chuẩn; KPI nhóm E — Giá trị và rò rỉ; bản nháp trả lời; Messenger chế độ phát triển; danh sách "Việc hôm nay" theo quy tắc.
- **Could:** Support request, quy trình duyệt Handover, giao diện import, cập nhật thời gian thực, chiến dịch, export.
- **Won't Have Now:** DISC, RFM nâng cao, Predictive CLV, huấn luyện mô hình, Next Best Action bằng mô hình, AI tự gửi tin hoặc tự đổi dữ liệu, Zalo OA thật, quản trị nhiều cấp.

## 4. Hướng phát triển KLTN

- Omnichannel thật (Zalo OA, Messenger, email), thời gian thực, phân luồng hội thoại.
- Data Mart, Data Quality nâng cao; RFM; Predictive CLV và giá trị tệp khách hàng.
- Conversation Intelligence nâng cao và phong cách giao tiếp DISC (được đánh giá như giả thuyết nghiên cứu).
- Customer Priority, Next Best Action (rule engine trước, mô hình sau), Copilot có công cụ giới hạn theo RBAC, đánh giá tác động.
- Quản trị nhiều cấp cho Admin, HR và quản lý.

## 5. Nguyên tắc kỹ thuật đã thống nhất

- Backend là nơi kiểm soát business rule, validation, phân quyền và thay đổi trạng thái chính.
- PostgreSQL là nguồn dữ liệu nghiệp vụ gốc; Customer 360 là dữ liệu tổng hợp/query projection, không thay thế dữ liệu gốc.
- Workflow bên ngoài (ví dụ n8n nếu dùng) chỉ thực hiện automation; phải có event/outbox, idempotency và theo dõi retry để tránh tạo Task/thông báo trùng.
- Audit Log cần lưu tối thiểu: ai thao tác, thời điểm, hành động, đối tượng, dữ liệu trước/sau khi cần thiết, và kết quả.
- Không phát triển tính năng chỉ vì có thể tạo nhanh bằng AI; mọi hạng mục phải có nghiệp vụ, acceptance criteria và kiểm thử.
- AI trong sản phẩm chỉ đề xuất: ngữ cảnh dựng theo quyền của người gọi, dữ liệu định danh được che, mọi lần gọi được ghi nhật ký, mọi gợi ý cần người duyệt; không tự gửi tin hoặc đổi dữ liệu. Khóa API và token kênh chỉ nằm trong biến môi trường.

## 6. Nhóm và trách nhiệm hiện tại

- **Huỳnh Minh Tài** — Technical Lead, AI & Integration Owner: kiến trúc CRM.AI, AI Gateway và quản trị AI, Copilot, giao diện hộp thư, Signal backend, KPI nhóm E, tích hợp và review. Đã làm: Auth/RBAC, Customer/Lead, Opportunity, Interaction/Task/Audit, Customer 360, workflow, Dashboard API và UI, hardening, hướng dẫn chạy local.
- **Nguyễn Đức Thắng** — Channel Integration & QA Coordinator: adapter Telegram, Zalo mô phỏng, Messenger; webhook bảo mật và chống trùng; API hội thoại; E2E và kiểm thử an toàn AI; kịch bản demo. Đã làm: Offering, Sales Assignment, giao diện Task, QA workflow và E2E đa vai trò.
- **Văn Phạm Thảo Nhi** — Data & AI Evaluation Owner: bộ hội thoại demo đa ngành, hướng dẫn gán nhãn và bộ nhãn chuẩn, báo cáo đánh giá AI, chất lượng dữ liệu tin nhắn và tín hiệu, từ điển và SQL đối soát nhóm E, Data Dictionary cho migration mới. Đã làm: gói dữ liệu V2, Data Dictionary, quy tắc Data Quality, giám sát workflow, từ điển KPI, SQL đối soát và lineage.

## 7. Trello roadmap

Board: **POSE CRM – Kế hoạch TLCN**.

- **Tuần 1–8 (đến 08/11/2026):** lõi CRM theo card W01–W12 (Auth/RBAC, Offering, Assignment, Customer/Lead, Opportunity, Interaction/Task/Audit, Customer 360, workflow, Dashboard, hardening, seed và hướng dẫn chạy local).
- **W12 (09–15/11):** SRS 2.0, kiến trúc CRM.AI, DEC-18; AI-01 Telegram bot, khóa API, cấu hình AI; khép QA W08, W10, W11, W12.
- **W13 (16–22/11):** AI-02 kênh hội thoại, AI-03 AI Gateway, AI-05 bộ hội thoại và bộ nhãn.
- **W14 (23–29/11):** AI-04 giao diện hộp thư, AI-06 Copilot.
- **W15 (30/11–06/12):** AI-07 tín hiệu khách hàng, AI-08 Messenger.
- **W16 — tuần dồn (07–13/12):** AI-09 KPI nhóm E và Việc hôm nay, AI-10 đánh giá AI, AI-11 E2E và demo.
- **W17 (14–20/12):** ổn định, đóng băng tính năng, báo cáo kiểm thử và tài liệu.
- **W18 (21–27/12):** nghiệm thu, technical review, diễn tập demo, khóa phiên bản.

Card CRM.AI đặt tên `W1x — AI-0x …`; hai card ổn định và nghiệm thu là W17, W18. Quy ước Trello: chỉ chuyển card sang **Sẵn sàng thực hiện** khi có phạm vi rõ, người phụ trách, acceptance criteria và phụ thuộc đã được xử lý. Không tự động chuyển card sang Doing chỉ theo mốc tuần.

## 8. Tài liệu hiện có và lưu ý

- Một số tài liệu cũ trong `docs/` có thể vẫn mang bối cảnh E-Commerce SME/EduZ. Chỉ dùng làm tham khảo lịch sử; không coi là yêu cầu chính thức nếu mâu thuẫn với tệp này.
- Khi bắt đầu một chat/build task mới, hãy đọc tệp này trước, sau đó kiểm tra `readme.md`, cấu trúc repository và card Trello liên quan.
