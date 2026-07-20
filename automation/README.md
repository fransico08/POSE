# Hệ thống E-Commerce & CRM - Automation (n8n)

## Giới thiệu
Thư mục này chứa các cấu hình workflow tự động hóa (JSON exports) chạy trên nền tảng n8n, hỗ trợ quy trình thương mại điện tử và chăm sóc khách hàng.

## Công cụ
- **Nền tảng**: n8n (Node-based workflow automation).
- **Giao thức**: REST API, Webhooks, SMTP (Gửi Email).

## Các Workflow chính
Trong giai đoạn MVP, các workflow tự động bao gồm:
- **Xác nhận đơn hàng**: Tự động gửi email thông báo khi khách hàng đặt hàng thành công.
- **Cập nhật trạng thái**: Thông báo khi đơn hàng được giao, bị hủy hoặc yêu cầu đổi trả.
- **Lời cảm ơn**: Gửi lời cảm ơn sau khi đơn hàng hoàn thành thành công.
- **Nhắc nhở & Cảnh báo**: Tạo nhiệm vụ chăm sóc tự động cho nhân viên (CRM task) và gửi cảnh báo nhắc nhở khi nhiệm vụ sắp quá hạn.
