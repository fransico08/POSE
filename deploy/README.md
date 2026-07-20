# Hệ thống E-Commerce & CRM - Deploy

## Giới thiệu
Thư mục chứa các cấu hình triển khai toàn bộ dự án trên môi trường local và cloud VPS thông qua Docker.

## Công nghệ & Thành phần
- **Docker & Docker Compose**: Đảm bảo môi trường triển khai nhất quán giữa Dev, Staging và Production.
- **Thành phần containers (Dự kiến)**:
  - `ecommerce-backend`: Container Spring Boot 3 (Java 21).
  - `ecommerce-frontend`: Container chạy ứng dụng Next.js.
  - `ecommerce-db`: PostgreSQL 16 chứa dữ liệu hệ thống.
  - `data-service`: Container FastAPI (Python) xử lý Data Quality & Analytics.
  - `automation-n8n`: Container n8n cho workflow tự động.
  - `storage`: MinIO Server lưu ảnh sản phẩm và file import.

## Hướng dẫn sử dụng
(Các lệnh `docker-compose up -d` và cấu hình biến môi trường sẽ được cập nhật khi dự án hoàn thiện).
