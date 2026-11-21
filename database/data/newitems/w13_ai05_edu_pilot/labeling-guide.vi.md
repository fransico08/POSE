# Hướng dẫn gán nhãn lần 1 - EDU pilot

## Đơn vị gán nhãn

Gán đúng một `INTENT` và một `SENTIMENT` cho từng tin `INBOUND`. Không gán nhãn cho `OUTBOUND`. Đọc lượt hiện tại cùng ngữ cảnh trước đó trong cùng hội thoại, nhưng không dùng kết quả xảy ra sau lượt đang xét.

## INTENT

| Nhãn | Dùng khi |
| --- | --- |
| `ASK_PRICE` | Hỏi học phí, tổng chi phí, ưu đãi hoặc điều kiện giá. |
| `ASK_INFO` | Hỏi nội dung lớp, lịch, hình thức học, trải nghiệm hoặc thông tin chung. |
| `BOOKING` | Muốn đặt, giữ, đổi hoặc xác nhận lịch tư vấn/học. |
| `PURCHASE_READY` | Nêu ý định đăng ký hoặc chốt sau khi có điều kiện cần thiết. |
| `COMPLAINT` | Phản ánh sự không hài lòng về phản hồi, trải nghiệm hoặc thông tin đã nhận. |
| `SUPPORT` | Cần trợ giúp xử lý thanh toán, truy cập, lịch học hoặc vấn đề vận hành. |
| `OTHER` | Lời cảm ơn, xác nhận chờ, hoặc nội dung không thuộc các nhóm trên. |

Khi một lượt có nhiều ý, chọn mục đích hành động chính. Hỏi giá không tự động là `PURCHASE_READY`; yêu cầu gấp không tự động là `COMPLAINT`.

## SENTIMENT

| Nhãn | Dùng khi |
| --- | --- |
| `POSITIVE` | Thể hiện hài lòng, thiện chí hoặc sẵn sàng tiếp tục. |
| `NEUTRAL` | Hỏi hoặc xác nhận theo giọng điệu trung tính. |
| `NEGATIVE` | Thể hiện khó chịu, lo lắng, thất vọng hoặc áp lực rõ ràng. |

`COMPLAINT` và `NEGATIVE` là hai chiều độc lập; tình huống khiếu nại không bắt buộc dùng ngôn từ gay gắt. Không thêm nhãn tín hiệu như `URGENT`, `CHURN_RISK` hoặc `RETURNING_CUSTOMER`.

## Phạm vi

Nhãn lần 1 nằm ở `round_1_annotations.csv`, tách khỏi dữ liệu gốc bằng `message_id`. Pilot không tạo bản ghi V15, `Interaction`, `Outbox`, `Task`, `customer_signals` hoặc `customer_signal_extractions`.

## Ngữ cảnh EDU

Đọc đúng vai trò vận hành trước khi gán nhãn: admissions thu thập mục tiêu, mức hiện tại và lịch để kiểm tra lớp/phí; học thuật xử lý lộ trình, đánh giá đầu vào khi có và khó khăn học tập; customer care tiếp nhận phản ánh, onboarding, tài liệu, tài khoản và đổi lịch. Nhân viên chỉ nói sẽ kiểm tra/xác nhận khi chưa có fact trong fixture; không suy diễn học phí, ưu đãi, lịch, chính sách hoàn tiền hoặc kết quả học tập.
