# POSE CRM Frontend

Frontend là giao diện CRM cho Manager, Sale, Customer Care và Data Staff.

## Công nghệ

- Next.js, TypeScript, Tailwind CSS, TanStack Query và Recharts.

## Phạm vi giao diện TLCN

- Đăng nhập và hiển thị menu theo permission.
- Offering, Sales Assignment, Customer/Lead, Opportunity, Interaction và CRM Task.
- Customer 360 và Dashboard theo phạm vi user.
- Import/data-quality view khi có API tương ứng.

## Quy tắc triển khai

- Backend mới là nguồn kiểm soát quyền. Frontend chỉ dùng permission để điều hướng và ẩn/hiện thao tác.
- Không tạo storefront, cart, checkout, payment, voucher hoặc inventory.
- Chưa tạo code đến khi có wireframe/user flow và API Contract v1.
