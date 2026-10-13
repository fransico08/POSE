# Frontend landing đợt 3 — evidence từng phase

Ngày **13/10/2026**, Owner **Huỳnh Minh Tài**, đầu vào **cc5e195**, nhánh Tài sạch trước sửa. Phạm vi hiện tại: Phase 0 và chuẩn bị hai concept; chưa thay `/` v1 hoặc micro-interaction login/app. Phải dừng sau Phase 1 chờ Tài chọn A/B và duyệt nội dung.

## Phase 0 — nguồn và font

Đã đọc AGENTS/frontend AGENTS, ba skill và reference được yêu cầu. Tra UI UX Pro Max bằng đường dẫn tuyệt đối, không persist/force: landing `SaaS CRM landing interactive`, style `dark hero glow bento`, ux `auto rotating content pause`, typography `display heading Vietnamese`. Áp dụng Product Demo + Features, bento có thứ bậc, control dừng/hover/focus/reduced motion; không nhận palette/font gợi ý, video, 3D, scroll-jacking hoặc thư viện GSAP. Body giữ Be Vietnam Pro; A/B theo yêu cầu Tài.

BR, RA, DEC-01–05, ARCH/readme và Google Doc use case HTTP 200 đã đối chiếu. [Content v2](../design/ui-ux/landing-content-v2.md) có nguồn từng nhóm chữ; v1 giữ làm lịch sử. Môi trường Node 24.13.0/npm 11.6.2, Next 16.4.0, Chrome 141.0.7390.123; package/lock không đổi.

Kiểm trên `/thu-nghiem/font-check` development, next/font/google thật, không font ảnh hoặc CSS Google bên ngoài. Fraunces variable có wght/opsz/SOFT, optical sizing auto, SOFT 30; kiểm 600/700/800. Chuỗi đầy đủ đợt 1 gồm Đăng nhập/Quản lý/Khách hàng và các dấu tiếng Việt. Chrome CSS.getPlatformFontsForNode báo **125 glyph, chỉ một family custom** cho mỗi sample, không có fallback hệ thống; đã xem ảnh các dấu, không thiếu glyph/tofu hoặc trộn baseline.

| Font | Weight | Kết quả | Ảnh |
|---|---:|---|---|
| Plus Jakarta Sans | 800 | Đủ glyph | [Chuỗi A](assets/frontend-landing-dot3-2026-10-13/jakarta-glyphs.png) |
| Fraunces | 600 | Đủ glyph | [Chuỗi B 600](assets/frontend-landing-dot3-2026-10-13/fraunces-600.png) |
| Fraunces | 700 | Đủ glyph | [Chuỗi B 700](assets/frontend-landing-dot3-2026-10-13/fraunces-700.png) |
| Fraunces | 800 | Đủ glyph | [Chuỗi B 800](assets/frontend-landing-dot3-2026-10-13/fraunces-800.png) |

[Toàn bảng glyph](assets/frontend-landing-dot3-2026-10-13/font-glyphs-all.png). Không có Chrome DevTools MCP ở phiên này; dùng Chrome profile riêng qua CDP, thao tác Input, Runtime chỉ đọc. Probe production gọi notFound và robots noindex. Chưa gọi đây là nghiệm thu toàn bộ WCAG.

Baseline đợt 2: mobile **92/100/96/100**, desktop **100/100/96/100**, JS **149.775 byte**. Chưa đo lại Lighthouse v2; chưa có bản landing v2 công khai, các phase sau và backend regression chưa chạy.
