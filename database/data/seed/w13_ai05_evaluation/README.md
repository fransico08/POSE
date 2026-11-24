# W13 AI-05 DEC-37 - Đánh giá nhãn vòng 2

## Trạng thái và phạm vi

Thư mục này là lớp đánh giá tách biệt cho W13 AI-05 Task 1. Nó không phải seed PostgreSQL, không thay đổi `database/data/seed/multisector_v2` hoặc `database/data/seed/w13_ai05`, và không sao chép sáu pilot theo ngành.

Theo DEC-37 ngày 24/11/2026, Văn giữ toàn bộ 1.002 nhãn vòng 1; Thắng gán nhãn độc lập chính xác 197 tin `INBOUND` thuộc split `test`. Tập `dev` chỉ dùng để phát triển prompt/quy tắc và phải tách khỏi test. Hạn hoàn tất đánh giá vòng 2 là 25/11/2026.

`round_2_test_template.csv` có đúng 197 message test, kèm context trước đó trong cùng hội thoại, nhưng không có nhãn vòng 1. Không được lộ nhãn test vòng 1 cho Thắng trước khi vòng 2 được khóa. Context chỉ chứa tin trước message đang gán nhãn; không có future-message leakage.

## Hướng dẫn gán nhãn tiếng Việt

Mỗi inbound nhận đúng một intent và một sentiment. Intent là mục đích chính của khách hàng trong ngữ cảnh hiện có; sentiment là sắc thái cảm xúc thực sự, không phải từ khóa. Không ép cân bằng phân bố.

| Message | Intent | Sentiment |
|---|---|---|
| Khóa học này bao nhiêu tiền? | `ASK_PRICE` | `NEUTRAL` |
| Khóa học này có nội dung gì? | `ASK_INFO` | `NEUTRAL` |
| Cho em đặt lịch tư vấn chiều mai | `BOOKING` | `NEUTRAL` |
| Em đồng ý mua gói này, gửi thông tin thanh toán nhé | `PURCHASE_READY` | `NEUTRAL` |
| Sao giao hàng lâu quá vậy? | `COMPLAINT` | `NEGATIVE` |
| Hướng dẫn giúp em đổi địa chỉ giao hàng | `SUPPORT` | `NEUTRAL` |
| Bao giờ đơn hàng tới vậy? | `ASK_INFO` | `NEUTRAL` |
| Đợi ba ngày rồi mà vẫn chưa nhận được hàng | `COMPLAINT` | `NEGATIVE` |
| Cảm ơn bạn tư vấn nhiệt tình nhé | `OTHER` | `POSITIVE` |
| Dạ, em nhận được thông tin rồi | `OTHER` | `NEUTRAL` |
| Mắc quá, có giảm thêm được không? | `ASK_PRICE` | `NEGATIVE` |
| App bị lỗi đăng nhập, hỗ trợ em với | `SUPPORT` | `NEUTRAL` |

Các ví dụ chỉ minh họa taxonomy, không tiết lộ nhãn vòng 1 của 197 message test.

Quy tắc xử lý:

- `ASK_PRICE`: khách thật sự hỏi giá, phí, thanh toán hoặc phần chi phí bao gồm. Hỏi tiến độ giao/đơn không phải `ASK_PRICE`.
- `ASK_INFO`: hỏi thông tin sản phẩm/dịch vụ, trạng thái hoặc cách hoạt động. `Bao giờ đơn hàng tới vậy?` là `ASK_INFO` khi chưa có biểu hiện không hài lòng.
- `BOOKING`: đề nghị đặt, đổi hoặc sắp lịch/lấy hàng/giao nhận.
- `PURCHASE_READY`: ý định mua/đăng ký/đặt dịch vụ đủ rõ, kể cả có điều kiện hợp lý.
- `COMPLAINT`: phản ánh trải nghiệm/dịch vụ không đạt kỳ vọng.
- `SUPPORT`: yêu cầu xử lý sự cố hoặc thao tác sau mua/đang sử dụng; yêu cầu hỗ trợ không tự động là `NEGATIVE`.
- `OTHER`: chào, xác nhận, cảm ơn, bổ sung bối cảnh hoặc nội dung không có mục đích rõ hơn.
- Tin có nhiều ý: chọn mục đích chính thúc đẩy phản hồi tiếp theo. Nếu cần, ghi phần còn lại trong `notes`.
- Dùng context trước đó nhất quán; không suy luận từ tin nhắn sau. Viết tắt, thiếu dấu, emoji hoặc lỗi gõ không đổi taxonomy nếu ý nghĩa vẫn rõ.
- Lịch sự không tự động là `POSITIVE`; thuật ngữ kỹ thuật không tự động là `NEGATIVE`. Với cảm xúc lẫn lộn hoặc nghĩa thật sự mơ hồ, ghi `notes` để đưa vào review thay vì suy đoán từ từ khóa.

## Quy trình khóa, đo và adjudication

1. Chạy `python evaluate_annotations.py prepare --write`, sau đó xác minh hash template trong manifest.
2. Thắng sao chép template thành `round_2_annotations.csv`, chỉ điền `intent`, `sentiment`, `annotator`, `locked_at`, `notes`; phải khóa đủ 197 hàng trước đánh giá.
3. Khi khóa, Thắng ghi SHA-256 của chính file round 2 vào handoff. `evaluate` chỉ nhận file khớp đúng hash đã ghi, rồi kiểm coverage, distribution, disagreement và Cohen's kappa riêng cho intent/sentiment. Ngưỡng mỗi kappa là `>= 0.60`.
4. Khi đủ 197 nhãn đã khóa và đã có cả hai kết quả kappa, Thắng hoàn tất phần annotation DEC-37 của mình. Không chờ Văn adjudicate trước khi bàn giao kết quả kappa.
5. Nếu một kappa dưới ngưỡng, dừng trước F1/gold, xuất danh sách bất đồng và báo các nhãn khó. Không dùng nhãn vòng 2 chưa khóa để đánh giá prompt.
6. Văn review bất đồng sau handoff. Chỉ sau khi có `adjudication.csv` do người review phê duyệt, `finalize` mới sinh `gold_test_annotations.csv` cho AI-10 và cũng kiểm lại cùng SHA-256 handoff. Khi không có bất đồng, adjudication vẫn phải là CSV chỉ có đúng header; mọi quyết định thừa hoặc trùng đều bị từ chối.

```powershell
python .\database\data\seed\w13_ai05_evaluation\evaluate_annotations.py validate-template
$round2Hash = (Get-FileHash .\database\data\seed\w13_ai05_evaluation\round_2_annotations.csv -Algorithm SHA256).Hash
python .\database\data\seed\w13_ai05_evaluation\evaluate_annotations.py evaluate `
  --round2 .\database\data\seed\w13_ai05_evaluation\round_2_annotations.csv `
  --round2-sha256 $round2Hash `
  --write-disagreements .\database\data\seed\w13_ai05_evaluation\disagreement_review.csv
```

`round_2_annotations.csv`, `disagreement_review.csv`, `adjudication.csv` và `gold_test_annotations.csv` chỉ được tạo sau khi có dữ liệu/human approval thực tế; chúng không thuộc bốn file chuẩn bị hiện tại.

## Thống kê, chất lượng và handoff

- W13 canonical: 6 ngành, 6 `ZALO_SIM` account, 300 hội thoại, 1.927 tin nhắn, 1.002 inbound annotation vòng 1; 240 hội thoại dev và 60 hội thoại test.
- Evaluation test: 197 inbound từ split test, exact-ID coverage bắt buộc.
- `manifest.json` neo SHA-256 của manifest W13, messages và annotation vòng 1. Seed W12/W13, UUID, checksum và dữ liệu đã chốt không bị evaluator sửa.
- Validator W13 hiện có kiểm FK/tenant, ACTIVE offering, chronology, Unicode, split, hash, reproducibility và chất lượng theo ngành. Evaluator kiểm thêm isolation test, coverage, valid label, distribution, kappa theo từng trục và disagreement.
- Dữ liệu synthetic, không có credential. `secret_ref` chỉ là tham chiếu tên biến; evidence hiện có chỉ xác nhận tương thích static với `ZALO_SIM`/webhook contract, không phải runtime webhook test.

W15 và AI-10 tái sử dụng chính thức `database/data/seed/w13_ai05` qua UUID/message ID và artifact evaluation được phê duyệt; không được sao chép thành seed khác. Nhãn evaluation là artifact AI riêng, không thuộc import PostgreSQL hiện tại.

## Handoff hiện tại

Chuẩn bị DEC-37 sẵn sàng cho round 2. Thắng hoàn tất phần annotation sau khi khóa đủ 197 nhãn và có cả hai kết quả kappa; nếu một kappa dưới 0.60 thì phải báo nhãn khó và chặn F1/gold. Adjudication là bước độc lập sau đó của Văn/AI-10. Không có kết quả bịa đặt trong package này.
