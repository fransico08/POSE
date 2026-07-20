# Hệ thống E-Commerce & CRM - Frontend

## Giới thiệu
Đây là mã nguồn Frontend cho dự án Hệ thống quản lý Thương mại điện tử tích hợp CRM. Ứng dụng cung cấp giao diện tương tác cho khách hàng (mua sắm) và giao diện quản trị (CRM, quản lý bán hàng) dành cho nhân viên SME.

## Công nghệ sử dụng
- **Core**: Next.js, TypeScript
- **UI/UX**: Tailwind CSS (Thay thế cho Ant Design cũ để tăng tính tuỳ biến)
- **Data Fetching / State**: TanStack Query
- **Charting**: Recharts (Cho Dashboard)

## Chức năng chính
- **Cửa hàng trực tuyến (Storefront)**: Tìm kiếm sản phẩm, giỏ hàng, đặt hàng, áp dụng voucher.
- **Giao diện quản trị (Admin/Nhân viên)**:
  - Đăng nhập và phân quyền hiển thị (RBAC).
  - Quản lý sản phẩm, biến thể, tồn kho và xử lý đơn hàng.
  - Quản lý Customer 360: Hồ sơ khách hàng, lịch sử mua hàng, lịch sử tương tác.
  - Quản lý nhiệm vụ chăm sóc và phản hồi của khách hàng.
  - Dashboard báo cáo doanh thu, đơn hàng, khách hàng mới.
