# POSE CRM Frontend

Frontend là giao diện CRM cho Manager, Sale, Customer Care và Data Staff.

## Công nghệ

- Next.js App Router, TypeScript strict, Tailwind CSS và TanStack Query. Recharts chỉ thuộc phạm vi biểu đồ ở giai đoạn sau, chưa cài trong đợt 1.

## Phạm vi giao diện TLCN

- Đăng nhập và hiển thị menu theo permission.
- Offering, Sales Assignment, Customer/Lead, Opportunity, Interaction và CRM Task.
- Customer 360 và Dashboard theo phạm vi user.
- Import/data-quality view khi có API tương ứng.

## Quy tắc triển khai

- Backend mới là nguồn kiểm soát quyền. Frontend chỉ dùng permission để điều hướng và ẩn/hiện thao tác.
- Không tạo storefront, cart, checkout, payment, voucher hoặc inventory.
- Code dựa trên wireframe/user flow đã được Tài duyệt và API Contract v1.

## Tiến độ frontend foundation — 12/10/2026

Đã thực hiện Phase 0–1 và scaffold Phase 2 trên nhánh Tài. **Dừng trước Phase 3 để Tài quyết định font**, theo yêu cầu kiểm `next/font` sau scaffold: glyph fallback có khác biệt cỡ thân chữ trong cùng từ. Chưa có trang đăng nhập, app shell hay session BFF; chưa nghiệm thu Phase 2–5b hoàn chỉnh.

- [Design system v1](../docs/design/ui-ux/design-system-v1.md): token, tương phản đã đo, Poppins với fallback Be Vietnam Pro.
- [Wireframe và user flow](../docs/design/ui-ux/frontend-foundation-wireframe.md): login, shell desktop/tablet/mobile, session, logout và trạng thái.
- [Evidence](../docs/testing/frontend-foundation-dot1-2026-10-12.md): môi trường, phiên bản package, ảnh font runtime, kết quả và phần chưa kiểm.

Đợt 1 chỉ có login/session BFF, routing theo permission, app shell và Tổng quan placeholder; chưa dùng Recharts hay triển khai các màn hình CRM nghiệp vụ. Tài đã chọn thêm cookie HttpOnly chỉ làm cờ hiện diện phiên với Path `/` cho guard sớm; hai cookie token giữ Path `/api` và `/api/session`.

## Chạy scaffold và kiểm font

Node đã kiểm: 24.13.0; npm 11.6.2. Chạy từ thư mục `frontend/`:

```powershell
npm ci
$env:BACKEND_URL = 'http://127.0.0.1:8080'
$env:POSE_FONT_PROBE = '1'
npm run dev
```

Mở `http://127.0.0.1:3000/font-probe?weight=700`; đổi weight thành 400, 500 hoặc 600. Route chẩn đoán chỉ mở khi dev có biến `POSE_FONT_PROBE=1`, production trả 404. Font được nạp qua `next/font/google`; cần mạng khi Next.js tải font lần đầu. Root `/` đã redirect 307 tới `/app`, nhưng `/app` chưa triển khai nên chưa dùng để kiểm nghiệp vụ.

`.env.example` chỉ chứa `BACKEND_URL`. Biến này dự kiến chỉ được server đọc. Mẫu font không cần backend; tích hợp auth sau này cần backend chạy riêng theo [backend/README.md](../backend/README.md). Chưa đọc credentials demo hay khởi động DB trong lượt scaffold.

Các script: `dev`, `build`, `start`, `lint`, `typecheck`, `test`. `lint`, `typecheck`, `build` đã đạt ở scaffold. Vitest và RTL đã cấu hình; chưa có test hành vi vì Phase 3–4 chưa bắt đầu, không coi `test` đã đạt.

| Đường dẫn | Nội dung hiện có |
|---|---|
| `src/app/layout.tsx` | Root layout, lang vi, metadata và hai font |
| `src/app/page.tsx` | Redirect root |
| `src/app/font-probe/` | Mẫu chẩn đoán font trong dev |
| `src/styles/` | Token CSS, Tailwind theme và style mẫu |
| `tests/setup.ts`, `vitest.config.ts` | Nền kiểm thử RTL/jsdom |

BFF/cookie là kiến trúc đã duyệt trong wireframe, chưa có handler/proxy/fetch wrapper. Khi tiếp tục sẽ triển khai và kiểm lại tất cả yêu cầu session, permission, accessibility và responsive.

## Skill hỗ trợ UI/UX

Đọc [hướng dẫn agent của POSE](../AGENTS.md) trước khi dùng skill:

- [UI UX Pro Max](../.agents/skills/ui-ux-pro-max/SKILL.md): tra cứu màu, font, phong cách và design system; kết quả là gợi ý, không thay nghiệp vụ/API.
- [Frontend UI Engineering](../.agents/skills/frontend-ui-engineering/SKILL.md): component, state, responsive và accessibility; có [checklist POSE](../.agents/skills/frontend-ui-engineering/references/accessibility-checklist.md).
- [Browser Testing](../.agents/skills/browser-testing-with-devtools/SKILL.md): kiểm tra UI chạy thật khi có công cụ phù hợp; không tự cài MCP.

Skill được lưu cùng repo, không cần cài CLI toàn máy. UI UX Pro Max dùng Python 3 có sẵn, không yêu cầu package Python ngoài standard library. Cài skill không đồng nghĩa đã thiết kế hoặc triển khai frontend.
