# Hệ thống E-Commerce & CRM - Database

## Giới thiệu
Thư mục chứa các script, sơ đồ ERD và các tài liệu liên quan đến Cơ sở dữ liệu chính của hệ thống.

## Hệ quản trị CSDL
- **PostgreSQL 16** (Nâng cấp từ MySQL để hỗ trợ xử lý dữ liệu lớn tốt hơn và các tính năng JSON/Analytics sau này).

## Mô hình dữ liệu cốt lõi (ERD)
- `users` & `roles`: Thông tin tài khoản người dùng và RBAC.
- `products`, `categories`, `variants`, `inventory`: Quản lý danh mục, sản phẩm và tồn kho.
- `orders`, `cart`, `vouchers`: Quản lý giỏ hàng, đơn đặt hàng và áp dụng khuyến mãi.
- `customers`: Quản lý thông tin khách hàng (Liên kết E-commerce & CRM).
- `interactions`, `tasks`: Lịch sử tương tác và nhiệm vụ chăm sóc khách hàng.
- `audit_logs`: Nhật ký bảo mật lưu lại mọi thao tác quan trọng.
