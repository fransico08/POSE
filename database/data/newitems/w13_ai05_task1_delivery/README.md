# W13 AI-05 Task 1 - Gói bàn giao tích hợp

## Trạng thái

- `PASS` static build: 6 pilot được ghép bất biến theo thứ tự EDU, REA, CLI, TECH, RET, LOG.
- `WARN` cần human review: chất lượng hội thoại là đánh giá nghiệp vụ; validation tự động không thay thế phê duyệt thủ công.
- `WARN` chưa import PostgreSQL, chưa sửa canonical manifest/loader, chưa tạo Interaction, Outbox, Task, `customer_signals` hay second-round labels.

## Deliverables

| Artifact | Số lượng |
|---|---:|
| ZALO_SIM channel accounts | 6 |
| Conversations | 300 (240 dev / 60 test) |
| Messages | 1927 |
| Inbound round-1 annotations | 1002 |

`round_1_annotations_consolidated.csv` là artifact để review/tích hợp, có cột `sector` và trường provenance để chuẩn hóa khác biệt schema annotation của EDU với năm pilot còn lại. Không phải bảng canonical mới và không thay đổi contract V13-V15.

`message-data-quality-rules.vi.md` hợp nhất các quy tắc kiểm soát message, tenant/FK, nhãn, duplicate, split và reproducibility áp dụng cho cả sáu sector.

## Import order đề xuất khi được phê duyệt

1. Nạp W12 canonical và xác minh organization, users, teams, team_memberships, roles/permissions, Customers, Leads, Offerings/Prices.
2. Nạp `channel_accounts.csv`.
3. Nạp `conversations.csv`.
4. Nạp `conversation_messages.csv`.
5. Nạp artifact annotation vòng 1 qua pipeline AI được phê duyệt; hiện tại không seed V15 signals.

## Data Dictionary audit

Tài liệu hiện hành tìm thấy là `docs/design/POSE-data-dictionary-v2.md`; nó tự mô tả là bản đồng bộ qua migrations V2-V12, không có tệp riêng tên `Data Dictionary V12`. Đối chiếu migration V12 xác nhận tài liệu đã có đủ `ai_invocations`, `ai_suggestions`, enum, FK và permission AI của V12. Tài liệu có `organizations`, `teams`, `users`, `team_memberships` và quy tắc tenant/FK nên phù hợp với các dependency W12 được kiểm tra. Các bảng V13 `channel_accounts`, `conversations`, `conversation_messages` và artifact annotation không thuộc phạm vi V12; contract cần đối chiếu là `docs/api/conversation-api-v1.md` và `docs/api/architecture-crm-ai-v1.md`. Đây là phân định tài liệu, không phải lý do để tự tạo cột/schema.

Xem `validation_evidence.json` sau khi chạy validator; `SHA256SUMS.txt` bảo vệ các artifact delivery bất biến.
