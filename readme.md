# E-Commerce & CRM Integrated System (SME)

## 📌 Giới thiệu dự án
Hệ thống là nền tảng quản lý tích hợp giữa **Thương mại điện tử (E-Commerce)** và **Quản lý quan hệ khách hàng (CRM)** được thiết kế đặc biệt dành cho các doanh nghiệp vừa và nhỏ (SME). Hệ thống giải quyết các bài toán thực tế:
- Quản lý tập trung sản phẩm, giỏ hàng, đơn hàng và quy trình bán hàng trực tuyến.
- Quản lý tập trung dữ liệu khách hàng theo hướng **Customer 360** (lịch sử mua hàng, voucher, phản hồi).
- Tổ chức quy trình chăm sóc sau bán tự động hóa thông qua workflow (n8n).
- Cung cấp Dashboard theo dõi hiệu suất bán hàng và chăm sóc khách hàng.

Đây là sản phẩm của Tiểu luận Chuyên ngành (POSE) tại Trường Đại học Sư phạm Kỹ thuật TP. Hồ Chí Minh (HCMUTE), Khoa Đào tạo Tiên tiến.

---

## 🏗️ Kiến trúc & Công nghệ

Hệ thống được xây dựng theo kiến trúc **Modular Monolith** và **Microservices-lite** với sự phân tách rõ ràng.

### 1. Frontend (E-Commerce & CRM)
- **Framework**: Next.js, TypeScript
- **UI/UX**: Tailwind CSS
- **State/Data**: TanStack Query, Recharts
- [Xem chi tiết tại Frontend README](./frontend/README.md)

### 2. Backend API
- **Framework**: Spring Boot 3.x (Java 21)
- **Database ORM**: Spring Data JPA
- **Security**: Spring Security 6 + JWT (Mô hình phân quyền RBAC)
- **API Docs**: OpenAPI / Swagger
- [Xem chi tiết tại Backend README](./backend/README.md)

### 3. Data Service (Xử lý dữ liệu)
- **Framework**: Python 3.12, FastAPI
- **Công cụ**: Pandas
- **Chức năng**: Import, chuẩn hóa, kiểm tra chất lượng dữ liệu và cung cấp API phân tích.
- [Xem chi tiết tại Data Service README](./data-service/README.md)

### 4. Automation & Cảnh báo
- **Nền tảng**: n8n
- **Chức năng**: Điều phối workflow tự động (gửi email, tạo task, cảnh báo) qua SMTP.
- [Xem chi tiết tại Automation README](./automation/README.md)

### 5. Database & Triển khai
- **Cơ sở dữ liệu chính**: PostgreSQL 16
- **Lưu trữ file**: MinIO (lưu ảnh sản phẩm, tệp import)
- **Triển khai (Deploy)**: Docker, Docker Compose, GitHub Actions
- [Xem chi tiết tại Database README](./database/README.md)
- [Xem chi tiết tại Deploy README](./deploy/README.md)

---

## 📂 Cấu trúc thư mục

- `/backend`: Mã nguồn phía máy chủ (Spring Boot, Java 21).
- `/frontend`: Mã nguồn giao diện (Next.js, Tailwind).
- `/data-service`: Dịch vụ xử lý dữ liệu và Data Quality (Python, FastAPI).
- `/automation`: Cấu hình workflow tự động hóa (n8n).
- `/database`: Cấu trúc DB PostgreSQL.
- `/deploy`: Cấu hình Docker Compose để triển khai.
- `/docs`: Tài liệu đặc tả (SRS), thiết kế kiến trúc, API. [Xem chi tiết tại Docs README](./docs/README.md)

---

## 👥 Nhóm thực hiện & Phân công

- **Huỳnh Minh Tài** (22110068) – *Fullstack Developer & Technical Lead*
  - Phụ trách: Kiến trúc, Auth, RBAC, Customer 360, Tích hợp hệ thống.
- **Nguyễn Đức Thắng** (23110062) – *Fullstack Developer, Commerce Process & QA Coordinator*
  - Phụ trách: Sản phẩm, Giỏ hàng, Đơn hàng, Voucher rule, Điều phối QA/E2E test.
- **Văn Phạm Thảo Nhi** (23110049) – *Data, Analytics & Automation Owner*
  - Phụ trách: Data Quality, Import, Dashboard phân tích, Workflow n8n.

**Giảng viên hướng dẫn:** TS. Mai Anh Thơ  
**Thời gian:** Tháng 7 năm 2026
