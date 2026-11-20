# Hướng dẫn gán nhãn lần 1 - REA pilot

Gán đúng một `INTENT` và một `SENTIMENT` cho từng tin `INBOUND`; không gán nhãn cho tin nhân viên. Đọc lượt hiện tại trong ngữ cảnh hội thoại trước đó, không dùng kết quả xảy ra sau lượt này.

| Intent | Dùng khi |
| --- | --- |
| `ASK_PRICE` | Hỏi giá catalog hoặc phí dịch vụ. |
| `ASK_INFO` | Hỏi thông tin property, phạm vi dịch vụ hoặc cách tiếp nhận. |
| `BOOKING` | Muốn đặt, đổi hoặc xác nhận một buổi trao đổi. |
| `PURCHASE_READY` | Nêu ý định cân nhắc dùng dịch vụ sau điều kiện cần thiết. |
| `COMPLAINT` | Phản ánh trải nghiệm hoặc thông tin không nhất quán. |
| `SUPPORT` | Cần hỗ trợ thao tác hoặc theo dõi yêu cầu. |
| `OTHER` | Xác nhận chờ hoặc nội dung ngoài các nhóm trên. |

`COMPLAINT` và `NEGATIVE` độc lập. Hỏi giá không tự động là `PURCHASE_READY`; yêu cầu trao đổi không tự động là lịch đã được xác nhận. Không suy diễn availability, giá thuê, pháp lý, phê duyệt vay hay giao dịch hoàn tất.
