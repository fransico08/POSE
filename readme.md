# POSE CRM

## Giới thiệu dự án

POSE CRM là hệ thống quản lý quan hệ khách hàng dành cho doanh nghiệp đa ngành, phát triển theo định hướng **CRM.AI — biến mỗi tương tác thành tài sản khách hàng**.

Bài toán gốc là **rò rỉ giá trị khách hàng**: lead không được theo dõi kịp thời, tri thức về khách hàng nằm trong đầu từng nhân viên sale, và doanh nghiệp không biết nên dồn sức vào khách hàng nào. POSE giải bài toán bằng vòng khép kín:

> Tiếp nhận đa kênh → hiểu khách hàng → định lượng giá trị → đề xuất hành động → thực thi và đo kết quả → học lại

Hệ thống gồm:

- **Lõi CRM (đã triển khai):** Offering đa ngành, phân công Sale có kiểm soát phạm vi, Lead, Customer, Opportunity, Interaction, CRM Task, Customer 360, Dashboard 16 KPI, Audit Log và workflow theo quy tắc có outbox/idempotency.
- **Lớp CRM.AI (đang triển khai):** hộp thư hội thoại (Telegram, Zalo mô phỏng, Messenger), AI Copilot có kiểm soát (tóm tắt, hồ sơ bàn giao, bản nháp trả lời), tín hiệu khách hàng có người duyệt và chỉ số rò rỉ giá trị.

AI trong POSE chỉ đề xuất: tôn trọng phạm vi dữ liệu của từng vai trò, che dữ liệu định danh, ghi nhật ký mọi lần gọi và không tự gửi tin hay thay đổi dữ liệu.

Đây là sản phẩm Tiểu luận Chuyên ngành của nhóm POSE tại Trường Đại học Sư phạm Kỹ thuật TP. Hồ Chí Minh; phần phân tích và dự đoán (RFM, Predictive CLV, DISC, Next Best Action) thuộc Khóa luận tốt nghiệp.

## Các giai đoạn dự án

| Giai đoạn | Thời gian | Nội dung |
|---|---|---|
| Lõi CRM (W01–W11) | 11/09–08/11/2026 | Phân tích, kiến trúc, Auth/RBAC, Offering, Assignment, Customer/Lead, Opportunity, Interaction/Task/Audit, Customer 360, workflow, Dashboard, hardening |
| **Thay đổi phạm vi và tầm nhìn** | **07/11/2026** | Chuyển định hướng sang CRM.AI xoay quanh bài toán rò rỉ giá trị khách hàng — [DEC-18](docs/meetings/decision-log.md#dec-18--chuyển-hướng-phạm-vi-và-tầm-nhìn-crmai), [SRS 2.0](docs/SRS-POSE-v2-CRM-AI.md), [Kiến trúc CRM.AI v1](docs/api/architecture-crm-ai-v1.md) |
| W12 — Chuyển hướng | 09–15/11/2026 | Chốt kiến trúc, Telegram bot, khóa API và cấu hình AI; khép QA lõi CRM |
| W13 | 16–22/11/2026 | Kênh hội thoại Telegram và Zalo mô phỏng, AI Gateway, bộ hội thoại có nhãn |
| W14 | 23–29/11/2026 | Giao diện hộp thư, Copilot |
| W15 | 30/11–06/12/2026 | Tín hiệu khách hàng có phản hồi, Messenger |
| W16 (tuần dồn) | 07–13/12/2026 | KPI rò rỉ giá trị, Việc hôm nay, báo cáo đánh giá AI, E2E CRM.AI |
| W17 — Ổn định | 14–20/12/2026 | Đóng băng tính năng, hiệu năng, báo cáo kiểm thử |
| W18 — Nghiệm thu | 21–27/12/2026 | Acceptance, diễn tập demo, khóa phiên bản |

## Đọc tài liệu theo quy trình

Đọc theo thứ tự này trước khi thiết kế, tạo migration hoặc viết code:

1. [SRS 2.0 — POSE CRM.AI](docs/SRS-POSE-v2-CRM-AI.md) — bài toán, phạm vi, yêu cầu và kế hoạch hiện hành. [SRS 1.0](<docs/SRS POSE.docx>) chỉ còn giá trị lịch sử.
2. [Kiến trúc CRM.AI v1](docs/api/architecture-crm-ai-v1.md) — tầng kênh, AI Gateway, Copilot, Signal, quyền và dữ liệu mới.
3. [Decision Log](docs/meetings/decision-log.md) — quyết định đã chốt; từ DEC-18 là giai đoạn CRM.AI.
4. [Business Rules v1](docs/analysis/business-rules-v1.md) — luồng nghiệp vụ và rule triển khai.
5. [Roles and Authorization v1](docs/analysis/roles-authorization-v1.md) — role, permission và scope kiểm tra ở Backend.
6. [Domain Class Diagram v1](docs/design/domain-class-diagram-v1.md) ([mở bản draw.io](docs/design/domain-class-diagram-v1.drawio)) — lớp đối tượng, quan hệ nghiệp vụ và ranh giới Backend.
7. [Architecture v1](docs/api/architecture-v1.md) — module, ranh giới dịch vụ và luồng tích hợp.
8. [API Contract Foundation v1](docs/api/api-contract-foundation-v1.md) — endpoint và authorization cho Auth/RBAC/Organization/Team.
9. [Docs README](docs/README.md) — ownership tài liệu và các artefact sẽ bổ sung.
10. [Hướng dẫn chạy local](docs/huong-dan-chay-local.md) — hướng dẫn thiết lập môi trường, database (Supabase dùng chung hoặc PostgreSQL local từ gói V2), chạy Backend + Frontend và kiểm thử.

Không dùng artifact E-Commerce cũ làm yêu cầu triển khai POSE CRM.

Agent tiếp quản đọc [AGENTS.md](AGENTS.md) để chọn skill đúng nhiệm vụ. Bộ UI/UX local nằm trong `.agents/skills/`; cách dùng ở [Frontend README](frontend/README.md#skill-hỗ-trợ-uiux), không thay thế nghiệp vụ hoặc API Contract.

## Trạng thái hiện tại (07/11/2026)

- Lõi CRM đã triển khai trên nhánh `Tài`: 20 API nền tảng Auth/RBAC/Organization/Team/User, Offering, Sales Assignment, Customer/Lead, Opportunity nhiều dòng, Interaction/Task/Audit, Customer 360, workflow `LEAD_QUALIFIED`, Dashboard KPI và giao diện tương ứng. Migration V1 baseline và V2–V11.
- Kiểm thử gần nhất: Vitest 300/300; E2E Playwright 28/28; backend H2 233 test (208 pass, 25 skip) và PostgreSQL cô lập 233 test (220 pass, 13 skip). Dashboard đã đối soát với SQL độc lập của nhóm dữ liệu.
- Đang kiểm thử: Sales Assignment, workflow W08, E2E đa vai trò W10, vertical slice và regression W11.
- Lớp CRM.AI: đã chốt SRS 2.0, kiến trúc CRM.AI v1 và DEC-18; chưa có code AI. Thứ tự triển khai theo bảng giai đoạn ở trên.
- Dữ liệu demo: gói V2 của nhóm dữ liệu (6 tổ chức, 36 người dùng); Supabase là PostgreSQL dùng chung (DEC-08). Không ghi mật khẩu demo hay credential vào repo.

## Kiến trúc và công nghệ

Modular Monolith cho nghiệp vụ giao dịch, chia sáu tầng: tiếp nhận kênh, lõi CRM, event backbone, Intelligence, Governance và trải nghiệm. Chi tiết: [Architecture v1](docs/api/architecture-v1.md) và [Kiến trúc CRM.AI v1](docs/api/architecture-crm-ai-v1.md).

| Thành phần | Công nghệ | Trách nhiệm |
|---|---|---|
| Frontend | Next.js 16, React 19, TypeScript, Tailwind CSS, TanStack Query, Recharts 3 | Giao diện theo permission, CRM, Customer 360, Dashboard, hộp thư và Copilot. [Frontend README](frontend/README.md) |
| Backend API | Java 25, Spring Boot 3.5, Spring Security, Spring Data JPA, Flyway; Maven đa module | Business rule, RBAC, scope, transaction, audit, adapter kênh và AI Gateway. [Backend README](backend/README.md) |
| Workflow | Module `automation/workflow-core` (outbox, idempotency, retry) | Workflow theo sự kiện; worker tắt mặc định. [Automation README](automation/README.md) |
| Cơ sở dữ liệu | PostgreSQL (Supabase dùng chung; cluster cô lập cho kiểm thử) | Dữ liệu nghiệp vụ, hội thoại, nhật ký AI, audit. [Database README](database/README.md) |
| AI | Claude API qua AI Gateway; nhà cung cấp giả lập cho kiểm thử | Tóm tắt, hồ sơ bàn giao, bản nháp, trích tín hiệu; tắt mặc định |
| Kênh hội thoại | Telegram Bot API; Messenger Platform (chế độ phát triển); Zalo mô phỏng | Nhận và gửi tin nhắn qua webhook có xác thực |
| Dữ liệu | SQL và Python của nhóm dữ liệu | Gói dữ liệu demo, đối soát KPI, bộ nhãn đánh giá AI. Data Service FastAPI để giai đoạn sau. [Data Service README](data-service/README.md) |
| Kiểm thử | JUnit 5, Mockito, PostgreSQL test cô lập, Vitest, Playwright | Unit, integration, E2E và kiểm thử an toàn AI |

## Cấu trúc thư mục

- `backend/`: Spring Boot modular monolith.
- `frontend/`: Next.js CRM UI.
- `data-service/`: chỗ dành cho Data Service (giai đoạn sau).
- `database/`: migration, seed và tài liệu dữ liệu.
- `automation/`: module `workflow-core` (outbox, idempotency, retry).
- `deploy/`: Docker Compose và cấu hình triển khai.
- `docs/`: SRS 2.0, business rules, authorization, Domain Class Diagram, architecture (gồm CRM.AI), API, decision log và test artefact.

## Phân công TLCN

| Thành viên | Vai trò | Đã thực hiện | Giai đoạn CRM.AI |
|---|---|---|---|
| Huỳnh Minh Tài | Technical Lead, AI & Integration Owner | Kiến trúc; Auth/RBAC, Organization, Team, User; Customer/Lead; Opportunity; Interaction/Task/Audit; Customer 360; workflow; Dashboard API và UI; hardening; hướng dẫn chạy local; tích hợp | Kiến trúc CRM.AI; AI Gateway và quản trị AI; Copilot; giao diện hộp thư; Signal backend; KPI nhóm E; review và tích hợp |
| Nguyễn Đức Thắng | Channel Integration & QA Coordinator | Offering; Sales Assignment; giao diện Task; QA workflow, E2E đa vai trò, regression | Adapter Telegram, Zalo mô phỏng, Messenger; webhook bảo mật; API hội thoại; E2E và kiểm thử an toàn AI; kịch bản demo |
| Văn Phạm Thảo Nhi | Data & AI Evaluation Owner | Gói dữ liệu V2; Data Dictionary; Data Quality; giám sát workflow; từ điển KPI, SQL đối soát, lineage | Bộ hội thoại demo; bộ nhãn chuẩn và báo cáo đánh giá AI; chất lượng dữ liệu tin nhắn; từ điển và SQL nhóm E |

**Thời gian:** 11/09/2026 – khoảng 27/12/2026 (W18) · **Mốc thay đổi phạm vi và tầm nhìn:** 07/11/2026
