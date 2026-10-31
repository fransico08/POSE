# Decision Log

Căn cứ chốt: tài liệu của task đã Hoàn thành trên Trello (Roles and Authorization v1, Architecture v1, API Contract Foundation v1) và phạm vi TLCN, KLTN trong `SRS POSE.docx`.

| Ngày | Mã | Quyết định | Căn cứ | Tài liệu đã cập nhật |
|---|---|---|---|---|
| 26/09/2026 | DEC-01 | Trong TLCN mỗi Opportunity có đúng một Offering và gắn Customer hoặc Lead. Opportunity nhiều dòng sản phẩm thuộc hướng KLTN. | SRS FR-06; Business Rules v1 mục 5 | Business Rules v1 mục 5, 10; Domain Class Diagram v1 trang 03 |
| 26/09/2026 | DEC-02 | Data Staff nhập và kiểm tra dữ liệu; Manager hoặc Admin duyệt lần nữa trước khi commit để tránh mất dữ liệu. Chỉ một bước duyệt, không có workflow phê duyệt nhiều cấp. Thắng xác nhận ngày 26/09/2026. | Roles and Authorization v1 dòng 45, 85; SRS Bảng 3 (Won't Have chỉ loại phê duyệt phức tạp) | Business Rules v1 mục 7; Domain Class Diagram v1 trang 05 |
| 26/09/2026 | DEC-03 | Support request và Handover thuộc phạm vi TLCN. | SRS FR-08; Bảng 3 (Should Have) | Business Rules v1 mục 8; Roles and Authorization v1 mục 7; Domain Class Diagram v1 trang 06 |
| 26/09/2026 | DEC-04 | Admin cơ bản, Permission, visibility cho ghi chú nhạy cảm, organization của User, outbox, workflow và thuộc tính Offering thuộc TLCN. Quản trị Admin/HR nhiều cấp thuộc KLTN. | SRS mục 3.1, 3.5, 3.7, Bảng 2, Bảng 5; mục II.3.5 | Đã có trong Roles and Authorization v1, Architecture v1, API Contract Foundation v1 |
| 26/09/2026 | DEC-05 | Handover có hai loại: `SALES` (Sale sang Sale, đã có trong UC-11) và `CARE` (Sale sang Customer Care, Thắng bổ sung). Cả hai cần Manager duyệt. Thắng xác nhận ngày 26/09/2026. | UC-11; tài liệu Roles-Authorization của Thắng (mục 2 "Bàn giao và báo cáo", mục 3 "Khách hàng") | Business Rules v1 mục 8; Roles and Authorization v1 mục 7; Domain Class Diagram v1 trang 06 |

## DEC-06 — hero coverflow và chuyển động landing

**Ngày quyết định: 30/10/2026. Owner: Huỳnh Minh Tài.** Triển khai tiếp tục ngày 31/10/2026 theo cập nhật quyền tìm ảnh.

- Thay hình mock bên phải và nền hero bằng coverflow sáu ngành; desktop có perspective/rotateY, mobile chỉ crossfade. H1 vẫn server-rendered và tĩnh; bộ mock giữ cho các nơi đang dùng.
- Bỏ nút dừng/tiếp tục ở hero, dải ngành và tab vai trò; bỏ demo picker Cơ hội/Hồ sơ. Quyết định này thay quy định “không 3D” và “nút dừng bắt buộc” trong landing-wireframe-v2.md; các mục cũ giữ làm lịch sử.
- **Tài chấp nhận ngoại lệ WCAG 2.2.2 Pause, Stop, Hide (mức A)** cho các nội dung tự chạy quá năm giây không còn nút dừng độc lập. Hover/focus không thay thế đầy đủ cơ chế dừng của tiêu chí này; landing không tuyên bố đạt toàn bộ WCAG 2.2 AA, dù audit tự động có thể đạt 100.
- Biện pháp giảm thiểu: dừng khi hover, focus trong vùng, ngoài viewport hoặc tab ẩn; tiếp tục sau khi hover/focus rời vùng. Reduced-motion không timer tự chuyển, không 3D, Giáo dục/nền tĩnh. RoleTabs dừng tự chuyển đến khi trang được tải lại sau khi người dùng chọn bằng click hoặc phím.
- Không package mới, không đổi app/auth/font/token; chỉ transform/opacity chuyển tiếp 600–800 ms. Ảnh static import/next/image, chỉ khung đầu tải ngay, không preload/fetchPriority cao; kiểm ngân sách ảnh mobile 150 KB và LCP phải là chữ.
- Cập nhật 31/10: được tìm/tải ảnh miễn phí từ link Download chính thức của Unsplash/Pexels, kiểm JPEG/kích thước và ghi giấy phép. Sau khoảng ba lượt tìm vẫn không đạt tiêu chí logo/chữ/mặt người thì dùng CSS/SVG; Tài duyệt ảnh trong báo cáo cuối và có quyền yêu cầu thay.

## DEC-07 — đính chính hero: giữ mock trắng, ảnh ngành làm nền

**Ngày quyết định: 31/10/2026. Owner: Huỳnh Minh Tài.**

- Đính chính phần đầu của DEC-06: Tài muốn **giữ nguyên mock trắng bên phải** (pipeline, Customer 360, toast, picker Cơ hội/Hồ sơ) như trước DEC-06. Ảnh ngành chỉ **thay nền navy phía sau hero**, không thay mock.
- Nền hero gồm: ảnh ngành đang chọn được làm mờ phủ toàn hero và dải sáu khung ảnh chạy ở khoảng trống phía dưới cột chữ trên desktop (từ 1024 px), mock trắng nằm đè phía trước. Mobile/tablet dưới 1024 px chỉ có ảnh nền mờ, không dải khung.
- Giữ nguyên từ DEC-06: bỏ nút dừng ở hero, dải ngành và RoleTabs; quy tắc dừng khi hover/focus/ngoài viewport/tab ẩn; reduced-motion tĩnh; RoleTabs khóa sau khi người dùng chọn; **ngoại lệ WCAG 2.2.2 mức A**. Picker Cơ hội/Hồ sơ được khôi phục vì là control chọn nội dung, không phải nút dừng.
- Header có nền tối đặc ngay khi nội dung hero cuộn xuống dưới nó, để link menu không đè lên mock trắng; sau hero vẫn chuyển nền sáng như cũ.
- Bán lẻ/F&B tiếp tục dùng SVG nội bộ như DEC-06 ghi nhận.

### Việc còn lại trước DEC-06

- Thắng cập nhật báo cáo "Task làm việc POSE" Chương 3:
  - UC-18: thêm bước Manager duyệt trước khi commit (DEC-02).
  - Thêm đặc tả luồng bàn giao `CARE` và sửa Use Case Diagram cho khớp (DEC-05).
  - Class Diagram: theo DEC-01 đến DEC-05.
- Thắng sửa tài liệu Roles-Authorization: mục 4 Data Staff "Thực hiện import khi dữ liệu đã vượt qua validation" đổi thành cần Manager duyệt trước khi commit (DEC-02).
- Chốt bộ enum trạng thái Lead, Task, Offering, Assignment khi đóng review Business Rules v1.
- Nhi sửa Data Dictionary, migration và seed theo góp ý trong `docs/reviews/`.
