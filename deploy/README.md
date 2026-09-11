# POSE CRM Deployment

Triển khai demo dùng Docker Compose; cấu hình cụ thể được thêm sau khi architecture v1 và môi trường local được chốt.

## Thành phần dự kiến

- `backend`: Spring Boot API.
- `frontend`: Next.js CRM UI.
- `postgres`: PostgreSQL 16.
- `data-service`: FastAPI import/data quality.
- `n8n`: workflow điều phối.
- `minio`: file import và tài liệu liên quan.
- `nginx`: reverse proxy cho demo khi cần.

## Yêu cầu triển khai

- Secret nằm trong biến môi trường, không commit vào repository.
- Môi trường local phải tái lập bằng README và seed data.
- CI tối thiểu kiểm tra format/lint, test, migration validation và build artifact khi source code đã sẵn sàng.
