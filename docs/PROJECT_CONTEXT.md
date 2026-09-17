# POSE CRM — Project Context

## 1. Thông tin chung

- Tên đề tài: **Xây dựng hệ thống CRM hỗ trợ quản lý và chăm sóc khách hàng cho doanh nghiệp**.
- Tên tiếng Anh: **Developing a Customer Relationship Management (CRM) System for Businesses**.
- Phạm vi: hệ thống CRM có thể áp dụng cho nhiều loại hình doanh nghiệp; không còn phụ thuộc vào EduZ hay một ngành kinh doanh cụ thể.
- Thời gian TLCN: khoảng **14 tuần**.

## 2. Bài toán và luồng nghiệp vụ chính

Doanh nghiệp cần quản lý tập trung khách hàng, nhân viên sales, sản phẩm/dịch vụ được phân công và các hoạt động chăm sóc. Hệ thống cần giúp theo dõi tiến trình từ khách hàng tiềm năng đến chăm sóc sau bán, đồng thời bảo đảm dữ liệu có thể áp dụng cho nhiều ngành.

Luồng MVP:

`Organization/Team → User & Role → Product/Service Offering → Sales Assignment → Lead/Customer → Opportunity → Interaction/CRM Task → Customer 360 → Dashboard`.

## 3. Phạm vi TLCN (MVP)

- Xác thực, RBAC và phạm vi truy cập theo tổ chức/nhóm.
- Organization, Team, User và Sales Assignment.
- Product/Service Offering đa ngành.
- Lead/Customer, Opportunity pipeline và trạng thái nghiệp vụ.
- Interaction, CRM Task, Audit Log.
- Customer 360 ở mức hồ sơ tổng hợp từ dữ liệu giao dịch/tương tác.
- Workflow rule-based đơn giản, có cơ chế tránh thực thi trùng.
- Dashboard KPI cơ bản; dữ liệu demo đa ngành và đối soát số liệu.

Không đưa vào MVP nếu không cần cho luồng chính: AI Assistant, RFM/CLV nâng cao, phân quyền Admin/HR chuyên sâu, dự đoán hoặc gợi ý cá nhân hóa.

## 4. Hướng phát triển KLTN

- RFM, Customer Score/Priority, Customer Value và khả năng mua lại.
- Data Mart, Data Quality nâng cao và dashboard phân tích.
- Next Best Action, workflow cá nhân hóa và AI Assistant.
- Phân quyền/thẩm quyền cao hơn cho Admin, HR và các nghiệp vụ mở rộng.

## 5. Nguyên tắc kỹ thuật đã thống nhất

- Backend là nơi kiểm soát business rule, validation, phân quyền và thay đổi trạng thái chính.
- PostgreSQL là nguồn dữ liệu nghiệp vụ gốc; Customer 360 là dữ liệu tổng hợp/query projection, không thay thế dữ liệu gốc.
- Workflow bên ngoài (ví dụ n8n nếu dùng) chỉ thực hiện automation; phải có event/outbox, idempotency và theo dõi retry để tránh tạo Task/thông báo trùng.
- Audit Log cần lưu tối thiểu: ai thao tác, thời điểm, hành động, đối tượng, dữ liệu trước/sau khi cần thiết, và kết quả.
- Không phát triển tính năng chỉ vì có thể tạo nhanh bằng AI; mọi hạng mục phải có nghiệp vụ, acceptance criteria và kiểm thử.

## 6. Nhóm và trách nhiệm hiện tại

- **Huỳnh Minh Tài** — Technical Lead/Backend: kiến trúc, Auth/RBAC, dữ liệu nghiệp vụ, API, tích hợp, Customer 360 backend, workflow, audit.
- **Nguyễn Đức Thắng** — Commerce Process & QA: nghiệp vụ luồng sales, giao diện, acceptance test, E2E/regression QA, tài liệu/evidence.
- **Văn Phạm Thảo Nhi** — Data, Analytics & Automation: dữ liệu demo, Data Quality, analytics, import và theo dõi automation trên Trello.

Backlog của Nhi trên Trello:

- W02 — Data profiling và quy tắc Data Quality v1.
- W04 — Chuẩn hóa dữ liệu Customer/Lead và cập nhật Data Dictionary.
- W06 — Kiểm tra chất lượng dữ liệu Interaction, CRM Task và Audit Log.
- W08 — Theo dõi workflow automation: retry, idempotency và trạng thái chạy.
- W09 — Đối soát nguồn dữ liệu Dashboard KPI và data lineage cơ bản.
- W12 — Hoàn thiện seed demo đa ngành, báo cáo Data Quality và hướng dẫn làm mới dữ liệu.

## 7. Trello roadmap 14 tuần

Board: **POSE CRM – Kế hoạch TLCN**.

- **Tuần 1–3:** chốt nghiệp vụ đa ngành, Domain Class Diagram/Data Dictionary, kiến trúc/API, repository/CI, Auth/RBAC, Offering, Sales Assignment và UI nền tảng.
- **Tuần 4:** Customer/Lead core (Tài); giao diện Customer/Lead và kiểm tra scope (Thắng); chuẩn hóa dữ liệu Customer/Lead và Data Dictionary (Nhi).
- **Tuần 5:** Opportunity pipeline (Tài); giao diện pipeline/acceptance test (Thắng).
- **Tuần 6:** Interaction, CRM Task, Audit Log core (Tài); UI và QA quyền truy cập (Thắng); Data Quality cho Interaction/Task/Audit (Nhi).
- **Tuần 7:** Customer 360 query/projection/API (Tài); UI Customer 360 và test luồng dữ liệu (Thắng).
- **Tuần 8:** Rule-based workflow, outbox, idempotency (Tài); kiểm thử retry/chống trùng/evidence (Thắng); theo dõi automation/retry/idempotency (Nhi).
- **Tuần 9:** Dashboard KPI API và SQL đối soát (Tài); Dashboard UI, lọc và kiểm thử số liệu (Thắng); đối soát nguồn dữ liệu/KPI và data lineage (Nhi).
- **Tuần 10:** hardening RBAC/audit/error/pagination (Tài); E2E đa vai trò (Thắng).
- **Tuần 11:** vertical slice tích hợp (Tài); regression QA và triage (Thắng).
- **Tuần 12:** seed demo, hướng dẫn chạy local và integration fixes (Tài); UX polish/kịch bản demo/evidence (Thắng); hoàn thiện dữ liệu demo và báo cáo Data Quality (Nhi).
- **Tuần 13:** ổn định backend và hiệu năng (Tài); báo cáo kiểm thử, diagram và tài liệu kỹ thuật (Thắng).
- **Tuần 14:** technical review/khóa phiên bản (Tài); acceptance test cuối và minh chứng trình bày (Thắng).

Quy ước Trello: chỉ chuyển card sang **Sẵn sàng thực hiện** khi có phạm vi rõ, người phụ trách, acceptance criteria và phụ thuộc đã được xử lý. Không tự động chuyển card sang Doing chỉ theo mốc tuần.

## 8. Tài liệu hiện có và lưu ý

- Một số tài liệu cũ trong `docs/` có thể vẫn mang bối cảnh E-Commerce SME/EduZ. Chỉ dùng làm tham khảo lịch sử; không coi là yêu cầu chính thức nếu mâu thuẫn với tệp này.
- Khi bắt đầu một chat/build task mới, hãy đọc tệp này trước, sau đó kiểm tra `readme.md`, cấu trúc repository và card Trello liên quan.
