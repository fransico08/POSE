# Hệ thống E-Commerce & CRM - Backend

## Giới thiệu
Đây là mã nguồn Backend cho dự án Hệ thống quản lý Thương mại điện tử tích hợp CRM dành cho doanh nghiệp SME. Backend chịu trách nhiệm xử lý nghiệp vụ bán hàng, quản lý đơn hàng và dữ liệu khách hàng CRM cốt lõi.

## Kiến trúc & Công nghệ
- **Framework**: Spring Boot 3.x (Java 21)
- **Kiến trúc**: Layered Architecture (Controller - Service - Repository) tích hợp Modular Monolith.
- **Database ORM**: Spring Data JPA (Kết nối PostgreSQL 16)
- **Xác thực & Phân quyền**: Spring Security 6 + JWT (RBAC)
- **API Documentation**: OpenAPI (Swagger)
- **Testing**: JUnit 5, Mockito

## Cấu trúc Module
Backend được tổ chức theo cấu trúc package theo domain (domain-driven package structure) bao gồm:
- `auth`: Xác thực, phân quyền và JWT.
- `user`: Quản lý tài khoản và Role.
- `customer`: Quản lý dữ liệu Customer và Customer 360.
- `product`: Quản lý danh mục, sản phẩm, biến thể và tồn kho.
- `order`: Quản lý giỏ hàng (Cart), đặt hàng (Order), voucher và thanh toán.
- `interaction`: Ghi nhận tương tác, phản hồi và nhiệm vụ chăm sóc khách hàng (CRM task).
- `audit`: Nhật ký bảo mật (Audit log) các thao tác quan trọng.
