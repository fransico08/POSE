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

## Skill hỗ trợ UI/UX

Đọc [hướng dẫn agent của POSE](../AGENTS.md) trước khi dùng skill:

- [UI UX Pro Max](../.agents/skills/ui-ux-pro-max/SKILL.md): tra cứu màu, font, phong cách và design system; kết quả là gợi ý, không thay nghiệp vụ/API.
- [Frontend UI Engineering](../.agents/skills/frontend-ui-engineering/SKILL.md): component, state, responsive và accessibility; có [checklist POSE](../.agents/skills/frontend-ui-engineering/references/accessibility-checklist.md).
- [Browser Testing](../.agents/skills/browser-testing-with-devtools/SKILL.md): kiểm tra UI chạy thật khi có công cụ phù hợp; không tự cài MCP.

Skill được lưu cùng repo, không cần cài CLI toàn máy. UI UX Pro Max dùng Python 3 có sẵn, không yêu cầu package Python ngoài standard library. Cài skill không đồng nghĩa đã thiết kế hoặc triển khai frontend.
