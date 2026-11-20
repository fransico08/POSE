# W13 AI-05 - Hướng dẫn gán nhãn vòng 1

## Phạm vi

- Chỉ gán nhãn tin nhắn `INBOUND`; mỗi bản ghi annotation phải thuộc đúng một tin nhắn inbound.
- Nhãn được xác định theo ý nghĩa và ngữ cảnh hội thoại, không dựa vào từ khóa đơn lẻ.
- Đây là artifact đánh giá dữ liệu, không phải `customer_signals`, không tạo Interaction, Outbox, Task hay nhãn vòng 2.

## Intent

| Intent | Sử dụng khi |
|---|---|
| `ASK_PRICE` | Hỏi giá, phí, thanh toán hoặc chi phí bao gồm. |
| `ASK_INFO` | Hỏi thông tin về sản phẩm/dịch vụ, đặc tính hoặc cách hoạt động. |
| `BOOKING` | Đề nghị đặt lịch, hẹn, lấy hàng, giao nhận hoặc đổi lịch. |
| `PURCHASE_READY` | Thể hiện ý định mua/đăng ký/đặt dịch vụ có điều kiện rõ ràng. |
| `COMPLAINT` | Phản ánh không hài lòng về dịch vụ/trải nghiệm. |
| `SUPPORT` | Cần hỗ trợ xử lý sự cố, vấn đề sau mua/đang sử dụng. |
| `OTHER` | Chào hỏi, cảm ơn, bổ sung ngữ cảnh hoặc nội dung không có intent rõ hơn. |

## Sentiment

- `POSITIVE`: có biểu hiện hài lòng, cảm ơn hoặc đồng tình rõ ràng.
- `NEGATIVE`: có thật sự bức xúc, lo ngại, không hài lòng hay phàn nàn.
- `NEUTRAL`: hỏi thông tin, xác nhận hoặc nội dung không biểu lộ cảm xúc rõ ràng.

Không ép phân bố nhãn. Trường hợp mơ hồ được ghi trong review của pilot nguồn và cần người review quyết định trước khi dùng cho đánh giá chất lượng.
