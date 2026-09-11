# POSE CRM Data Service

Data Service là dịch vụ Python hỗ trợ import, chuẩn hóa identity, phát hiện lỗi dữ liệu và cung cấp dữ liệu mô tả cho dashboard.

## Công nghệ

- Python 3.12, FastAPI và Pandas.

## Trách nhiệm

- Nhận file CSV/Excel và lưu metadata Import Job.
- Chuẩn hóa email/phone, kiểm tra định dạng và phát hiện duplicate nghi ngờ.
- Trả về báo cáo validate để Manager/Admin phê duyệt commit qua backend.
- Hỗ trợ data-quality metrics và dashboard contract.

## Ranh giới

- Không ghi trực tiếp PostgreSQL nghiệp vụ.
- Không tự gộp Customer hoặc tự phê duyệt import.
- Không triển khai RFM, CLV, machine learning hoặc AI trong TLCN.
