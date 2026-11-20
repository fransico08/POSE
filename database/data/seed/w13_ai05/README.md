# W13 AI-05 Task 1 - Canonical seed extension

Đây là extension additive của `database/data/seed/multisector_v2`, không phải một canonical seed độc lập và không chứa bản sao của sáu pilot nguồn.

## Nội dung

- 6 `ZALO_SIM` channel accounts.
- 300 conversations: 240 dev, 60 test.
- 1.927 messages và 1.002 round-1 inbound annotations.
- Hướng dẫn gán nhãn, quy tắc Data Quality và thống kê sáu ngành.

## Nguồn và tính bất biến

Các CSV, labeling guide, Data Quality rules và statistics được sao chép byte-for-byte từ `database/data/newitems/w13_ai05_task1_delivery`. `manifest.json` lưu hash của W12 canonical và source delivery. Không sửa, sao chép hoặc nạp lại các thư mục pilot theo ngành.

## Thứ tự nạp khi có pipeline được phê duyệt

1. Nạp và xác minh `multisector_v2`.
2. `channel_accounts.csv`.
3. `conversations.csv`.
4. `conversation_messages.csv`.
5. Dùng `round_1_annotations_consolidated.csv` cho evaluation pipeline AI được phê duyệt.

Không nạp PostgreSQL trong Task 1. Không seed Interaction, Outbox, Task, V15 signals hay nhãn vòng 2.
