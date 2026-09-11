# POSE CRM Automation

Automation dùng n8n và SMTP để điều phối thông báo/lịch, không thay thế business rule trong backend.

## Workflow TLCN

- Thông báo khi Offering được phân công cho Sale.
- Tạo Task khi Customer hoặc Lead được giao.
- Nhắc Task sắp đến hạn và cảnh báo Task quá hạn.
- Tạo Task follow-up khi Opportunity đổi stage.
- Báo lỗi import hoặc duplicate nghi ngờ.
- Báo cáo định kỳ theo lịch.

## Ranh giới bắt buộc

- n8n chỉ gọi API đã xác thực; không ghi trực tiếp database.
- Backend kiểm tra quyền, idempotency và cập nhật dữ liệu nghiệp vụ.
- Mỗi execution lưu trạng thái, thời điểm, lỗi và số lần retry.
