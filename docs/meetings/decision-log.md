# Decision Log

Căn cứ chốt: tài liệu của task đã Hoàn thành trên Trello (Roles and Authorization v1, Architecture v1, API Contract Foundation v1) và phạm vi TLCN, KLTN trong `SRS POSE.docx`.

| Ngày | Mã | Quyết định | Căn cứ | Tài liệu đã cập nhật |
|---|---|---|---|---|
| 26/09/2026 | DEC-01 | Trong TLCN mỗi Opportunity có đúng một Offering và gắn Customer hoặc Lead. Opportunity nhiều dòng sản phẩm thuộc hướng KLTN. | SRS FR-06; Business Rules v1 mục 5 | Business Rules v1 mục 5, 10; Domain Class Diagram v1 trang 03 |
| 26/09/2026 | DEC-02 | Data Staff nhập và kiểm tra dữ liệu; Manager hoặc Admin duyệt lần nữa trước khi commit để tránh mất dữ liệu. Chỉ một bước duyệt, không có workflow phê duyệt nhiều cấp. Thắng xác nhận ngày 26/09/2026. | Roles and Authorization v1 dòng 45, 85; SRS Bảng 3 (Won't Have chỉ loại phê duyệt phức tạp) | Business Rules v1 mục 7; Domain Class Diagram v1 trang 05 |
| 26/09/2026 | DEC-03 | Support request và Handover thuộc phạm vi TLCN. | SRS FR-08; Bảng 3 (Should Have) | Business Rules v1 mục 8; Roles and Authorization v1 mục 7; Domain Class Diagram v1 trang 06 |
| 26/09/2026 | DEC-04 | Admin cơ bản, Permission, visibility cho ghi chú nhạy cảm, organization của User, outbox, workflow và thuộc tính Offering thuộc TLCN. Quản trị Admin/HR nhiều cấp thuộc KLTN. | SRS mục 3.1, 3.5, 3.7, Bảng 2, Bảng 5; mục II.3.5 | Đã có trong Roles and Authorization v1, Architecture v1, API Contract Foundation v1 |
| 26/09/2026 | DEC-05 | Handover có hai loại: `SALES` (Sale sang Sale, đã có trong UC-11) và `CARE` (Sale sang Customer Care, Thắng bổ sung). Cả hai cần Manager duyệt. Thắng xác nhận ngày 26/09/2026. | UC-11; tài liệu Roles-Authorization của Thắng (mục 2 "Bàn giao và báo cáo", mục 3 "Khách hàng") | Business Rules v1 mục 8; Roles and Authorization v1 mục 7; Domain Class Diagram v1 trang 06 |

## Việc còn lại

- Thắng cập nhật báo cáo "Task làm việc POSE" Chương 3:
  - UC-18: thêm bước Manager duyệt trước khi commit (DEC-02).
  - Thêm đặc tả luồng bàn giao `CARE` và sửa Use Case Diagram cho khớp (DEC-05).
  - Class Diagram: theo DEC-01 đến DEC-05.
- Thắng sửa tài liệu Roles-Authorization: mục 4 Data Staff "Thực hiện import khi dữ liệu đã vượt qua validation" đổi thành cần Manager duyệt trước khi commit (DEC-02).
- Chốt bộ enum trạng thái Lead, Task, Offering, Assignment khi đóng review Business Rules v1.
- Nhi sửa Data Dictionary, migration và seed theo góp ý trong `docs/reviews/`.
