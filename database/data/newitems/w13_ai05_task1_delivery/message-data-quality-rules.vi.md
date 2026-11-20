# W13 AI-05 - Quy tắc chất lượng dữ liệu tin nhắn

## Phạm vi

Các quy tắc này áp dụng cho sáu pilot W13 AI-05 và artifact hợp nhất. Chúng kiểm tra dữ liệu tĩnh; không xác nhận webhook, PostgreSQL, Interaction, Outbox, Task hay tín hiệu V15.

## Quy tắc bắt buộc

1. **Định danh và tenant:** UUID channel account, conversation và message là duy nhất; mọi FK giữ cùng `organization_id`. Conversation gắn đúng một Customer hoặc Lead hợp lệ của tenant.
2. **Kênh và người gửi:** account là `ZALO_SIM`, `secret_ref` chỉ là tên biến môi trường. Tin `INBOUND` không có nhân viên gửi và có `RECEIVED`; tin `OUTBOUND` do assignee gửi và có `SENT`.
3. **Thời gian:** message được sắp theo `occurred_at`; `created_at`, `last_inbound_at`, `last_message_at` của conversation phản ánh chuỗi tin. Timestamp trong cửa sổ dữ liệu W13 đã chốt.
4. **Nội dung:** dùng UTF-8 tiếng Việt; không có replacement character, credential, mật khẩu, token, email/điện thoại thật hoặc lời hứa nghiệp vụ chưa được W12/contract xác minh. Lỗi chính tả/không dấu có chủ đích được giữ nếu phản ánh cách nói tự nhiên.
5. **Đa dạng và lặp:** không có message substantive lặp nguyên văn trong cùng pilot; hội thoại phải khớp tình huống/ngành và không dùng template thay tên sản phẩm.
6. **Offering và quan hệ W12:** chỉ dùng Customer/Lead, team, assignee và ACTIVE offering đã được validator sector đối chiếu W12; không thay đổi W12.
7. **Nhãn vòng 1:** chỉ gán cho `INBOUND`, đúng một intent và một sentiment trên mỗi message; chỉ dùng 7 intent và 3 sentiment được phê duyệt; xét ngữ cảnh, không ép phân bố hoặc suy diễn bằng từ khóa.
8. **Chống leakage:** split gán theo conversation, không để một conversation ở cả `dev` và `test`; kiểm tra hash, generation xác định và source manifest trước bàn giao.

## Bằng chứng

Các validator sector kiểm tra quy tắc đặc thù ngành; `validate_task1_delivery.py` kiểm tra tổng hợp schema, FK/tenant/team, chronology, annotation coverage, split, Unicode, source hash và reproducibility.
