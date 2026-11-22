# Kế hoạch báo cáo TLCN — nộp GVHD ngày 11/12/2026

Theo DEC-34 (22/11/2026). Hạn của khoa: nộp báo cáo hoàn chỉnh (docx) qua email cho GVHD **trước 12/12/2026**; GVHD chấm trước 19/12; GVPB chấm phản biện trước 28/12. Nhóm nộp ngày **11/12** để dư một ngày.

> Bảng dưới là đề xuất ban đầu, Tài chỉnh lại khung chương nếu khoa hoặc GVHD có mẫu riêng. Báo cáo chỉ dùng số liệu đã có evidence trong `docs/testing/`; không đưa token, mật khẩu, URL tunnel hay dữ liệu cá nhân thật.

## 1. Mốc

| Mốc | Ngày | Nội dung |
|---|---|---|
| Đóng băng tính năng | 04/12 | Sau mốc này chỉ sửa lỗi, viết tài liệu, evidence |
| Bản nháp đủ chương | 08/12 | Mỗi người nộp phần của mình vào bản tổng hợp |
| Review chéo | 09/12 | Tài rà khớp SRS 2.3, Kiến trúc CRM.AI v1, decision log; sửa số liệu theo evidence |
| Bản cuối | 10/12 | Định dạng, mục lục, danh mục hình/bảng, tài liệu tham khảo |
| Nộp GVHD | 11/12 | Gửi docx qua email |

## 2. Khung chương đề xuất và nguồn sẵn có

| Chương | Nội dung | Nguồn trong repo | Đề xuất người viết |
|---|---|---|---|
| 1. Tổng quan | Bài toán rò rỉ giá trị khách hàng, mục tiêu, phạm vi TLCN/KLTN, MoSCoW | `docs/SRS-POSE-v2-CRM-AI.md` Phần I, `docs/PROJECT_CONTEXT.md` | Tài |
| 2. Phân tích yêu cầu | Tác nhân, use case, yêu cầu chức năng và phi chức năng, vai trò và phân quyền | SRS mục 3–7, `docs/analysis/roles-authorization-v1.md`, `docs/analysis/business-rules-v1.md` | Tài |
| 3. Thiết kế | Kiến trúc tổng thể và CRM.AI, Domain Class Diagram, cơ sở dữ liệu V1–V18, API | `docs/api/architecture-crm-ai-v1.md`, `docs/design/domain-class-diagram-v1.md`, `docs/design/POSE-data-dictionary-v2.md`, `docs/api/*.md` | Thắng (diagram, Chương 3), Văn (Data Dictionary) |
| 4. Triển khai | Công nghệ, lõi CRM, hộp thư Telegram/Zalo mô phỏng/Messenger, AI Gateway, Copilot, tín hiệu, KPI nhóm E | `readme.md`, `docs/huong-dan-chay-local.md`, `docs/meetings/decision-log.md` | Tài |
| 5. Kiểm thử và đánh giá | Chiến lược kiểm thử, kết quả backend/E2E, hiệu năng, kênh thật, an toàn AI, đánh giá AI | `docs/testing/` (W17 performance, Telegram thật, AI-11, demo rehearsal), báo cáo AI-10 | Thắng (báo cáo kiểm thử), Văn (đánh giá AI, đối soát KPI) |
| 6. Kết luận | Kết quả đạt so với mục tiêu, giới hạn, hướng KLTN | SRS Phần II, decision log | Tài |
| Phụ lục | Hướng dẫn cài đặt, kịch bản demo, bảng quyết định | `docs/huong-dan-chay-local.md`, `docs/demo/kich-ban-demo-crm-ai-v1.md` | Tài |

## 3. Số liệu cần chốt trước 08/12

- Kết quả test backend H2/PostgreSQL và E2E của bản đóng băng 04/12.
- Hiệu năng W17 (demo và `-Scale`) — đã có trong `docs/testing/w17-performance-2026-11-19.md`.
- Nghiệm thu kênh thật: Telegram (đã có, `docs/testing/telegram-real-e2e-2026-11-20.md`), Messenger (AI-08, hạn 03/12).
- Cohen's kappa và bộ nhãn chuẩn (AI-05, hạn 25/11); macro-F1, chi phí, p95 của AI-10 (hạn 04/12), gồm phân bố mã lý do loại đầu ra theo DEC-35.
- Đối soát KPI nhóm A–E (AI-09, hạn 30/11).

## 4. Rủi ro

| Rủi ro | Ứng phó |
|---|---|
| Nhãn vòng 2 trễ làm AI-10 không kịp | Báo cáo kappa và F1 trên phần đã gán; ghi rõ cỡ mẫu và giới hạn |
| Gemini quá tải hoặc lỗi định dạng khi chạy đánh giá | Chạy lại sau, ghi phân bố lỗi theo mã DEC-35; demo dùng AI mock theo DEC-31 |
| Messenger không kịp 03/12 | Ghi lý do trong báo cáo; demo bằng Telegram thật và Zalo mô phỏng |
