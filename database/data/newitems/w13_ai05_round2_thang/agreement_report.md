# W13 AI-05 — lượt gán nhãn vòng hai và độ đồng thuận

## Cập nhật theo comment mới nhất của Tài (25/11/2026)

Comment gần nhất trên card là của Văn lúc 16:30 ngày 24/11: template 197 tin test và hướng dẫn đã có trên nhánh Nhi. Quyết định gần nhất của Tài lúc 14:17 ngày 24/11 chốt đúng 197 tin `INBOUND` thuộc split `test`, giữ `dev` riêng, ngưỡng kappa `>= 0.60` cho từng nhãn; không cần chờ xử lý bất đồng mới được tick. Nếu dưới ngưỡng, phải báo nhãn khó và chưa dùng F1.

Đã dùng template mù DEC-37, đóng băng bản Codex hỗ trợ với SHA-256 `ED87D0FD7EC13FFDBBE8E25B5D8C3C12CDA5A94ED289BF04CB9CB5105DA4B226`, rồi mới chạy evaluator. Không thay nhãn sau khi biết kappa. Đây là phép so sánh vòng một với **Codex AI-assisted**, không phải kappa giữa hai người hay xác nhận Thắng tự gán tay.

| Trục | N | Tỷ lệ trùng | Kappa | Ngưỡng | Kết quả |
|---|---:|---:|---:|---:|---|
| Intent | 197 | 55.3% | 0.4106 | 0.60 | Dưới ngưỡng |
| Sentiment | 197 | 85.3% | 0.4837 | 0.60 | Dưới ngưỡng |

Có 101 tin bất đồng: 72 chỉ khác intent, 13 chỉ khác sentiment, 16 khác cả hai. Các cặp intent khó gặp nhiều nhất là `ASK_INFO -> OTHER` (37), `SUPPORT -> OTHER` (7), `SUPPORT -> ASK_INFO` (5), `ASK_INFO -> PURCHASE_READY` (5); 34 lượt còn lại rải trên nhiều cặp ít gặp. Sentiment khó nhất là `NEUTRAL -> NEGATIVE` (21), tiếp theo `NEGATIVE -> NEUTRAL` (4); hai cặp có `POSITIVE` mỗi cặp 2.

Kết quả được ghi đúng là AI-assisted và dưới ngưỡng; chưa tính F1/gold. Tài cho phép tick sau khi lượt hai được khóa và có kappa, không phải chờ Văn adjudicate; tuy vậy, vì bản hiện tại do Codex gán thay vì Thắng tự gán, không được nhận đây là hoàn tất checklist của Thắng nếu chưa được Tài chấp nhận cách gán AI hỗ trợ.

## Phạm vi và phương pháp

- Gán độc lập mù trên toàn bộ split `test`: **197** tin inbound từ **60** hội thoại, đủ **6** ngành; split `dev` không được đưa vào để giữ riêng cho phát triển prompt.
- Vòng hai được tạo trước khi đọc/đối chiếu nhãn vòng một; sau khi khóa file vòng hai mới tính thống kê.
- Đây là lượt gán ngữ nghĩa do Codex hỗ trợ theo phân công Thắng (`THANG_CODEX_ASSISTED`), không phải lượt gán tay đã được Thắng xác nhận. Vì vậy kappa dưới đây là **đồng thuận giữa vòng một và một lượt AI hỗ trợ**, không được mô tả là kappa giữa hai người cho tới khi Thắng/người gán thứ hai duyệt và xác nhận.
- Không sửa file hội thoại, nhãn vòng một, seed canonical hay Data Dictionary do Văn sở hữu. Artefact mới không lưu nội dung tin nhắn.

## Kết quả tổng thể

| Nhãn | Số cặp | Tỷ lệ trùng | Cohen's kappa |
|---|---:|---:|---:|
| Intent | 197 | 55.3% | 0.4106 |
| Sentiment | 197 | 85.3% | 0.4837 |

## Phân bố nhãn trên tập test

| Nhóm | Vòng một | Vòng hai |
|---|---|---|
| Intent | `ASK_INFO` 96, `ASK_PRICE` 31, `BOOKING` 7, `COMPLAINT` 8, `OTHER` 30, `PURCHASE_READY` 9, `SUPPORT` 16 | `ASK_INFO` 63, `ASK_PRICE` 31, `BOOKING` 8, `COMPLAINT` 9, `OTHER` 69, `PURCHASE_READY` 12, `SUPPORT` 5 |
| Sentiment | `NEGATIVE` 20, `NEUTRAL` 173, `POSITIVE` 4 | `NEGATIVE` 37, `NEUTRAL` 156, `POSITIVE` 4 |

## Theo ngành

| Ngành | Cặp | Intent trùng | Kappa intent | Sentiment trùng | Kappa sentiment |
|---|---:|---:|---:|---:|---:|
| CLI | 38 | 60.5% | 0.4933 | 71.1% | 0.4500 |
| EDU | 28 | 89.3% | 0.8158 | 82.1% | 0.3694 |
| LOG | 30 | 40.0% | 0.2416 | 90.0% | 0.6087 |
| REA | 30 | 30.0% | 0.0856 | 73.3% | 0.1489 |
| RET | 31 | 64.5% | 0.5297 | 100.0% | 1.0000 |
| TECH | 40 | 50.0% | 0.2876 | 95.0% | -0.0256 |

## Kiểm tra và cách dùng

- Bộ vòng hai phủ đúng toàn bộ **197** nhãn inbound thuộc `test`, không có ID trùng/thiếu/thừa; mọi cặp cùng hội thoại, tenant và split.
- Intent chỉ thuộc danh mục bảy nhãn; sentiment chỉ thuộc ba nhãn trong hướng dẫn AI-05.
- Các khác biệt cần người phụ trách dữ liệu xem lại: **101** tin; danh sách ID và nhãn hai vòng ở `disagreements_for_review.csv`, không sao chép nội dung hội thoại.
- Hash nguồn tin nhắn: `99748894C97A3A53137544BB9725241396D2A9A844AACC840A45C86EBD575EB9`; hash nguồn vòng một: `B936F889DF0A564135946771E79B57A1D8EDC1C0BF0B04F52558509FD2AC6CB0`.
- `round_2_annotations_enriched.csv` là nhãn vòng hai đã ghép ID; `build_round2_evidence.py` kiểm tra độ phủ và tái lập báo cáo. Theo DEC-37, adjudication của Văn là bước sau để chốt gold cho AI-10, không phải điều kiện để ghi nhận kappa hoặc tick checklist; khi kappa dưới 0.60 thì giữ rõ cảnh báo và không dùng F1/gold.

Kappa không đo chất lượng tuyệt đối của nhãn; cần diễn giải cùng phân bố lớp, số lượng cặp và việc adjudication các trường hợp bất đồng.

## Lượt khóa 25/11/2026 — bản mới nhất (Codex delegated blind pass)

Phần dưới đây thay thế số liệu của bản Codex AI-assisted thăm dò ở phía trên. 197 nhãn được gán từ template trống theo labeling guide, khóa trước khi evaluator đối chiếu từng tin với vòng một. Người gán được khai báo rõ là `Codex delegated by Thang (blind pass)`, không nhận là gán tay của Thắng.

- SHA-256: `E71C0C1882166C90C112C39D8D4BF86A22A13935C706F353F53414FE4B260FD0`.
- Intent: 197 mẫu, agreement 56,35%, κ=0,4327 (ngưỡng 0,60).
- Sentiment: 197 mẫu, agreement 85,79%, κ=0,5337 (ngưỡng 0,60).
- 104 bất đồng: 76 intent-only, 18 sentiment-only, 10 cả hai. Evaluator tạo `disagreements_codex_blind_2026-11-25.csv`.
- Intent khó: `ASK_INFO → OTHER` (30), `ASK_INFO → PURCHASE_READY` (7), `SUPPORT → OTHER` (7), `SUPPORT → ASK_INFO` (6), `ASK_INFO → ASK_PRICE` (6), `ASK_PRICE → ASK_INFO` (4), `OTHER → BOOKING` (4), `ASK_INFO → BOOKING` (4).
- Sentiment khó: `NEUTRAL → NEGATIVE` (23), `POSITIVE → NEUTRAL` (2), `NEUTRAL → POSITIVE` (2), `NEGATIVE → NEUTRAL` (1).

| Ngành | N | Intent agreement | Intent κ | Sentiment agreement | Sentiment κ |
|---|---:|---:|---:|---:|---:|
| CLI | 38 | 65,8% | 0,5655 | 71,1% | 0,4722 |
| EDU | 28 | 89,3% | 0,8170 | 71,4% | 0,2222 |
| LOG | 30 | 40,0% | 0,2521 | 93,3% | 0,7600 |
| REA | 30 | 33,3% | 0,1124 | 80,0% | 0,2035 |
| RET | 31 | 61,3% | 0,4946 | 96,8% | 0,7862 |
| TECH | 40 | 50,0% | 0,2889 | 100,0% | 1,0000 |

Round 2 distribution: intents `ASK_INFO` 60, `ASK_PRICE` 34, `BOOKING` 16, `COMPLAINT` 11, `OTHER` 59, `PURCHASE_READY` 16, `SUPPORT` 1; sentiment `NEGATIVE` 42, `NEUTRAL` 151, `POSITIVE` 4. Hai kappa dưới threshold nên không chạy F1/gold; bàn giao kappa không cần chờ Văn adjudicate theo comment Tài.
