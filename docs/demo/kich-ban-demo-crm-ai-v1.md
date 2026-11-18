# Kịch bản demo CRM.AI v1

Áp dụng DEC-30 và DEC-31, ngày 18/11/2026. Dùng dữ liệu giả trong tổ chức EDU; tài khoản `edu.admin@example.com`, `edu.mgr@example.com`, `edu.s01@example.com`, `edu.s02@example.com`. Người vận hành lấy mật khẩu từ biến môi trường `POSE_DEMO_PASSWORD`, không trình chiếu hoặc ghi lại. Tên khách và số điện thoại ví dụ dưới đây là dữ liệu minh họa.

## Chuẩn bị

- Admin kiểm tra kênh **Zalo mô phỏng**, team và nhân viên được giao mặc định; dùng secret webhook thử nghiệm được cấu hình an toàn trong môi trường.
- Bật worker `POSE_WORKFLOW_WORKER_ENABLED=true`; kiểm tra sự kiện outbox được xử lý và có Task “Phản hồi đầu tiên”. Hạn mặc định là một ngày; mở Task qua **Nhiệm vụ**, không hứa sẽ hiện trong **Việc hôm nay** ngay.
- Manager kiểm tra Offering ACTIVE, phân công cho `edu.s01` và `edu.s02`; cả hai phải đủ điều kiện với các Offering của Cơ hội sẽ bàn giao.
- Bật AI mock cho buổi diễn tập, không dùng dữ liệu thật. Kết quả mock minh họa cơ chế, không đánh giá chất lượng mô hình.

Để kiểm tra tự động riêng luồng có worker trên database E2E cô lập, chạy từ gốc checkout:

```powershell
powershell.exe -NoProfile -File .\scripts\e2e-env.ps1 -Build -WorkflowWorker
```

Chế độ này chỉ chạy các test `@workflow`, bật worker với chu kỳ một giây trong tiến trình test và không đổi hạn mặc định. Khi bỏ `-WorkflowWorker`, script chạy các test còn lại với worker tắt.

## Bảy bước

1. **Manager EDU — Sản phẩm & dịch vụ / Phân công:** mở Offering đã chuẩn bị và kiểm tra hai Sales. Giải thích: quyền xem sản phẩm và quyền nhận bàn giao phụ thuộc phân công hiệu lực.
2. **Sales s01 — Hội thoại:** người vận hành gửi webhook Zalo mô phỏng với nội dung giả “Tôi muốn hỏi học phí, số 0900000000”. Chọn hội thoại, bấm **Tạo Lead từ hội thoại**, tự nhập tên khách giả, xác nhận số gợi ý và chọn Offering. Nếu chưa có liên hệ, hỏi khách trước. Trong **Nhiệm vụ**, kiểm Task phản hồi đầu tiên sau khi worker xử lý. Giải thích: nhân viên chủ động tạo hoặc gắn Lead, hệ thống không tự tạo.
3. **Sales s01 — Hội thoại / Tín hiệu khách hàng:** bấm **Trích tín hiệu**, kiểm câu trích và chấp nhận **Ý định: Hỏi giá**. Nói rõ đây là mock dựa từ khóa, chỉ hỗ trợ ý định và cảm xúc; Offering quan tâm thuộc phạm vi KLTN.
4. **Sales s01 — Hội thoại / Copilot:** bấm **Soạn bản nháp**, đọc và dùng bản nháp, sửa nếu cần, sau đó tự bấm **Gửi trả lời**. Giải thích: AI không tự gửi; Interaction được ghi theo kênh hội thoại.
5. **Sales s01 — Lead, Khách hàng:** xác nhận đủ điều kiện, kiểm Task theo dõi khi worker xử lý, chuyển Lead thành Customer. Gửi thêm một tin mô phỏng trong cùng hội thoại, mở Customer 360 kiểm Interaction và tín hiệu. Tạo Cơ hội với giá trị giả 12.000.000 VND.
6. **Manager EDU — Customer 360:** đổi người phụ trách từ s01 sang s02 đủ điều kiện, kiểm Cơ hội và Task được chuyển theo quy tắc hiện hành; bấm **Soạn hồ sơ bàn giao**. Đọc các ý có nguồn; phần thiếu nguồn là “Chưa có dữ liệu”, không tự thêm cam kết.
7. **Manager EDU — Báo cáo:** mở nhóm **Giá trị và rò rỉ**; giải thích E01/E02 theo ngưỡng và thời điểm, E04 theo kênh và E05 theo gợi ý đã duyệt. Đối chiếu số trước/sau trong cùng kỳ; dữ liệu sẵn có cũng ảnh hưởng tổng số.

## Dự phòng và lời thoại

Nếu Telegram hoặc tunnel lỗi, dùng ZALO_SIM và AI mock theo cùng bảy bước, ghi rõ **mô phỏng**. Kênh và mô hình thật chưa được chứng minh bằng lần chạy này; Tài tự smoke bằng khóa của mình khi phù hợp. Hội thoại chưa lưu tên người gửi, nên nhân viên phải hỏi và nhập tên. Mọi bản nháp cần người kiểm tra trước khi gửi; mock không chứng minh chất lượng hay an toàn của mô hình thật.
