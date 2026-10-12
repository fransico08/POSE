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

## Tiến độ frontend foundation — 12/10/2026

Đã thực hiện Phase 0–1 trên nhánh Tài; **chưa scaffold/code UI**, đang chờ Tài xác nhận “tiếp tục” theo điểm dừng của prompt đợt 1.

- [Design system v1](../docs/design/ui-ux/design-system-v1.md): token, tương phản đã đo, Poppins với fallback Be Vietnam Pro.
- [Wireframe và user flow](../docs/design/ui-ux/frontend-foundation-wireframe.md): login, shell desktop/tablet/mobile, session, logout và trạng thái.
- [Evidence Phase 0–1](../docs/testing/frontend-foundation-dot1-2026-10-12.md): môi trường, ảnh kiểm glyph, kết quả và phần chưa kiểm.

Đợt 1 chỉ có login/session BFF, routing theo permission, app shell và Tổng quan placeholder; chưa dùng Recharts hay triển khai các màn hình CRM nghiệp vụ. Tài đã chọn thêm cookie HttpOnly chỉ làm cờ hiện diện phiên với Path `/` cho guard sớm; hai cookie token giữ Path `/api` và `/api/session`.

## Skill hỗ trợ UI/UX

Đọc [hướng dẫn agent của POSE](../AGENTS.md) trước khi dùng skill:

- [UI UX Pro Max](../.agents/skills/ui-ux-pro-max/SKILL.md): tra cứu màu, font, phong cách và design system; kết quả là gợi ý, không thay nghiệp vụ/API.
- [Frontend UI Engineering](../.agents/skills/frontend-ui-engineering/SKILL.md): component, state, responsive và accessibility; có [checklist POSE](../.agents/skills/frontend-ui-engineering/references/accessibility-checklist.md).
- [Browser Testing](../.agents/skills/browser-testing-with-devtools/SKILL.md): kiểm tra UI chạy thật khi có công cụ phù hợp; không tự cài MCP.

Skill được lưu cùng repo, không cần cài CLI toàn máy. UI UX Pro Max dùng Python 3 có sẵn, không yêu cầu package Python ngoài standard library. Cài skill không đồng nghĩa đã thiết kế hoặc triển khai frontend.
