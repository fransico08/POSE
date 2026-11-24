# Hướng dẫn gán nhãn RET/F&B W13 AI-05

Chỉ annotation cho lượt `INBOUND`; bảng nhãn explicit trong generator được rà theo ngữ cảnh từng hội thoại, không dùng keyword heuristic.

- `ASK_INFO`: hỏi món, khả năng phục vụ, lựa chọn hoặc thông tin áp dụng.
- `ASK_PRICE`: hỏi trực tiếp giá, tổng tiền hoặc khoản chi phí.
- `BOOKING`: trao đổi đặt trước, thời điểm nhận hoặc khả năng giao/nhận.
- `PURCHASE_READY`: chọn món hoặc thể hiện ý định đặt rõ ràng.
- `COMPLAINT`: phản ánh sai món, sai giá, thiếu thông tin hoặc trải nghiệm không phù hợp.
- `SUPPORT`: cần kiểm tra, hướng dẫn hoặc xử lý một yêu cầu cụ thể.
- `OTHER`: xác nhận, bổ sung ngữ cảnh hoặc kết thúc không có yêu cầu mới.

Sentiment chỉ là `NEGATIVE` khi có lo ngại, khó chịu hoặc phản ánh thật sự; `POSITIVE` khi thể hiện hài lòng/cảm ơn rõ ràng; còn lại là `NEUTRAL`.
