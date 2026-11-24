# W13 AI-05 — lượt gán nhãn vòng hai

Đây là phần bổ sung riêng cho checklist `[Thắng] Data — gán nhãn độc lập lần 2 để đo Cohen's kappa`. Không sửa dữ liệu gốc, annotation vòng một hay các artefact của Văn.

## Phạm vi

- Gán vòng hai mù cho toàn bộ split `test`: 197 tin inbound thuộc 60 hội thoại, đủ sáu ngành. Split `dev` giữ riêng cho phát triển prompt.
- Dữ liệu nguồn là hội thoại tổng hợp. Template vòng hai có nội dung tin hiện tại và context trước đó để gán nhãn mù; file đánh giá theo schema này giữ nội dung tổng hợp, không chứa dữ liệu khách thật.
- Lượt đánh giá khóa ngày 25/11 được Codex thực hiện theo yêu cầu của Thắng trên template trống và hướng dẫn nhãn; không xem nhãn từng tin vòng một trước khi khóa. Artifact ghi rõ người gán là Codex được Thắng ủy quyền, không mạo nhận Thắng tự gán tay.
- Theo comment DEC-37 của Tài ngày 24/11, có thể bàn giao/tick phần đo sau khi khóa lượt hai và có kappa; không cần chờ Văn adjudicate. Lượt này đã khóa đủ 197 nhãn và có kappa, nhưng cả hai chỉ số dưới 0,60 nên phải báo rõ các nhãn khó và không dùng F1/gold.

## Tệp

- `round_2_annotations.csv`: kết quả gán mù theo external message ID.
- `round_2_annotations_enriched.csv`: kết quả đã ghép ID tenant/hội thoại, ngành và split.
- `agreement_report.md`: phân bố nhãn, phần trăm đồng thuận và Cohen's kappa toàn bộ/theo ngành.
- `disagreements_for_review.csv`: ID và nhãn từ bản đánh giá thăm dò trước; file mới do evaluator tạo có message text tổng hợp để giải thích bất đồng.
- `build_round2_evidence.py`: validator offline và bộ tạo lại báo cáo.
- `round_2_codex_assisted.csv` và `round_2_handoff_codex_draft.json`: bản theo schema evaluator DEC-37 và hash đóng băng; vẫn ghi rõ đây là lượt Codex hỗ trợ, không phải lời xác nhận thủ công của Thắng.
- `round_2_codex_blind_2026-11-25.csv` và `round_2_handoff_codex_blind_2026-11-25.json`: lượt gán mù đã khóa theo yêu cầu của Thắng; SHA-256 và thời điểm khóa được ghi trong handoff.
- `disagreements_codex_blind_2026-11-25.csv`: 104 bất đồng sau khi lượt hai đã khóa; chỉ dùng để phân tích khó khăn, không tạo F1/gold.
- `docs/testing/w13-ai05-round2-2026-11-25.md`: kết quả evaluator mới nhất, kappa tổng thể/theo ngành và giới hạn.

Chạy từ gốc repo: `python database/data/newitems/w13_ai05_round2_thang/build_round2_evidence.py`.

## Kết quả lượt đã khóa (25/11/2026)

- Intent: agreement 56,35%, Cohen's κ=0,4327; Sentiment: agreement 85,79%, κ=0,5337; cả hai dưới DEC-37 threshold 0,60.
- 104 bất đồng: 76 chỉ khác intent, 18 chỉ khác sentiment, 10 khác cả hai. Các cặp phổ biến: `ASK_INFO → OTHER` (30), `ASK_INFO → PURCHASE_READY` (7), `SUPPORT → OTHER` (7), `SUPPORT → ASK_INFO` (6), `ASK_INFO → ASK_PRICE` (6), `NEUTRAL → NEGATIVE` (23).
- Khó khăn chính là phân biệt câu hỏi với câu bổ sung bối cảnh/đồng ý (`ASK_INFO`/`OTHER`), câu hỏi thông tin với ý định mua/đặt có điều kiện, và lo ngại chưa rõ có đủ sắc thái tiêu cực hay chỉ trung tính.
- Theo comment Tài, đã khóa lượt và có kappa thì không cần chờ Văn adjudicate để bàn giao task; vì kappa dưới ngưỡng, không chạy F1/gold. Người gán được ghi trung thực là Codex được Thắng ủy quyền.
- SHA-256 lượt đã khóa: `E71C0C1882166C90C112C39D8D4BF86A22A13935C706F353F53414FE4B260FD0`.
