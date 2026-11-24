# Hướng dẫn gán nhãn TECH W13 AI-05

Nguồn hội thoại được giữ nguyên từ `TECH_50_hoi_thoai_bien_tap_v4.md`. Chỉ gán nhãn cho message `INBOUND`.

- `ASK_INFO`: hỏi thông tin về gói, phạm vi, khả năng hoặc quy trình.
- `ASK_PRICE`: hỏi giá, phí, chi phí hoặc đơn vị tính.
- `BOOKING`: yêu cầu liên hệ lại, hẹn trao đổi hoặc sắp xếp bước làm việc.
- `PURCHASE_READY`: thể hiện ý định thực hiện/mua/triển khai tương đối rõ.
- `COMPLAINT`: phản ánh lỗi, sai lệch, trải nghiệm không phù hợp hoặc khiếu nại.
- `SUPPORT`: cần hướng dẫn, kiểm tra, chuẩn bị tài liệu hoặc xử lý bước tiếp theo.
- `OTHER`: xác nhận, kết thúc, hoặc nội dung không thuộc các nhóm trên.

Sentiment: `POSITIVE`, `NEUTRAL`, `NEGATIVE`. Nhãn được xác định theo bảng annotation tường minh cho từng lượt inbound và đã đối chiếu ngữ cảnh; không dùng từ khóa đơn lẻ. Các annotation có ghi `Human review required` cần người duyệt xem lại.
