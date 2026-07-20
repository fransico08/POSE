# Hệ thống E-Commerce & CRM - Data Service

## Giới thiệu
Thư mục này chứa mã nguồn của dịch vụ xử lý dữ liệu (Data Service). Đây là một module độc lập chịu trách nhiệm về chất lượng dữ liệu, import và cung cấp API phân tích cho Dashboard, chuẩn bị nền tảng cho việc nâng cấp RFM, CLV và Machine Learning trong giai đoạn Khóa luận tốt nghiệp.

## Công nghệ sử dụng
- **Ngôn ngữ**: Python 3.12
- **Framework API**: FastAPI
- **Xử lý dữ liệu**: Pandas

## Chức năng chính
- **Import & Data Quality**:
  - Nhận file CSV/Excel từ người dùng.
  - Chuẩn hóa dữ liệu (email, số điện thoại).
  - Tìm kiếm và cảnh báo trùng lặp (Levenshtein / Jaro-Winkler).
- **Data API**: Cung cấp các endpoint tính toán và phân tích số liệu cho Dashboard.
