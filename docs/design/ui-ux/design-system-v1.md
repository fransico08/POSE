# Design system v1 — POSE CRM

Ngày: **12/10/2026** · Owner: **Huỳnh Minh Tài** · Phạm vi: frontend foundation đợt 1.

**Trạng thái: Tài đã xác nhận tiếp tục Phase 2–5b ngày 12/10/2026**, kèm chỉnh sửa topbar, icon Người dùng và kiểm font thật. Đây không phải xác nhận nghiệm thu của Thắng. Đọc cùng [wireframe](frontend-foundation-wireframe.md) và [evidence](../../testing/frontend-foundation-dot1-2026-10-12.md).

**Quyết định font sau scaffold:** Tài đã chọn **Be Vietnam Pro làm font chính**, thay D4 Poppins/fallback. Đã kiểm lại bốn weight bằng next/font, không còn trộn glyph; đã triển khai Phase 3–5 và kiểm Phase 5b. Kết quả zoom native 200/400% và giới hạn screen reader ở evidence; chưa thay xác nhận nghiệm thu của Thắng.

## 1. Định hướng và nguồn quyết định

CRM nội bộ đa ngành: sidebar navy, vùng làm việc sáng, chữ rõ, mật độ vừa đủ cho nghiệp vụ. Đợt 1 có login, app shell, Tổng quan theo permission và các trạng thái; không có KPI, biểu đồ, Kanban nghiệp vụ, dark mode hay trang giới thiệu công khai.

Thứ tự ảnh thực tế trong chat khác số thứ tự ở prompt; dùng **tên mẫu** để truy vết. Ảnh được xem trực tiếp trong yêu cầu, chưa có bản file nguồn trong repo.

| Nguồn ảnh | Quyết định áp dụng | Phạm vi |
|---|---|---|
| Admindek — ảnh đính kèm thứ 4 | Sidebar navy, nhóm menu, active rõ; nền xám xanh; breadcrumb dưới tên trang | Sidebar, canvas, breadcrumb |
| Weihu — ảnh thứ 1 | Nút thu gọn, user ở đáy sidebar; nút chính đậm; điểm nhấn pastel | Sidebar, button, badge và vùng icon lối tắt |
| Duralux — ảnh thứ 2 | Bề mặt trắng, viền mảnh, ít bóng; tham khảo khung admin ở phần dashboard sáng | Topbar và surface; ảnh không phải “Super Admin 3.2” như mô tả prompt |
| Ghi chú đợt 2, 13/10/2026 — Super Admin 3.2 | Tài xác nhận ảnh Duralux được gửi nhầm ở đợt 1; Super Admin 3.2 đã được xem lại với topbar sáng, card viền mảnh, ít bóng, khớp UI hiện tại | Giữ UI và token đợt 1; dành ảnh Super Admin cho dashboard/module sau, không dùng cho trang giới thiệu |
| CRMS — ảnh thứ 3 | Hàng thẻ dễ quét theo nhóm | Lối tắt Tổng quan; primary vẫn là xanh dương |
| Prompt D4 và mục 3 | Scale chữ, token semantic, focus, spacing và motion; font đã đổi sang Be Vietnam Pro theo quyết định sau scaffold | Toàn hệ thống |

Không dùng logo, nội dung, số liệu hoặc chứng nhận trong ảnh. Brand đợt này dùng chữ **POSE / POSE CRM**; chưa tạo logo mới.

Đã chạy `ui-ux-pro-max` bằng đường dẫn tuyệt đối với truy vấn `B2B CRM internal dashboard multi-industry`, `--design-system --density 7 -p "POSE CRM"`, không `--persist`/`--force`. Kết quả: “Accessible & Ethical”, navy, bề mặt sáng, chữ 16px+, focus và reduced motion. Phần landing/conversion và font Plus Jakarta Sans không phù hợp phạm vi/quyết định đã chốt nên không áp dụng. Tra bổ sung UX authentication/focus/error summary và Next.js font/route handler; lệnh đầy đủ ở evidence.

## 2. Màu semantic

Đã đặt biến trong `frontend/src/styles/tokens.css`, nối vào theme Tailwind của phiên bản được cài. Component chỉ dùng token; hex chỉ có tại nơi định nghĩa token và tài liệu đo màu.

| Token semantic (tên thiết kế) | Giá trị | Cách dùng / lý do |
|---|---|---|
| `--color-primary` | `#2563EB` | CTA, link, focus trên nền sáng; giữ đề xuất |
| `--color-primary-hover` | `#1D4ED8` | Hover/pressed CTA; giữ đề xuất |
| `--color-on-primary` | `#FFFFFF` | Chữ/icon trên CTA |
| `--color-primary-subtle` | `#EFF6FF` | Điểm nhấn nhẹ cho lối tắt/tenant |
| `--color-sidebar-bg` | `#0F172A` | Sidebar và panel login |
| `--color-sidebar-text` | `#CBD5E1` | Chữ/icon sidebar |
| `--color-sidebar-active-bg` | `#1E3A8A` | Mục hiện tại, kèm `aria-current` và nét chỉ dấu |
| `--color-sidebar-active-text` | `#FFFFFF` | Chữ/icon mục hiện tại |
| `--color-sidebar-hover-bg` | `#1E293B` | Hover/focus vùng menu; bổ sung |
| `--color-canvas` | `#F4F7FB` | Nền nội dung |
| `--color-surface` | `#FFFFFF` | Topbar, form, dialog và card |
| `--color-border` | `#E2E8F0` | Viền trang trí/card; không làm viền nhận diện input |
| `--color-control-border` | `#64748B` | Viền input và button phụ; bổ sung để đạt tương phản UI |
| `--color-text` | `#0F172A` | Chữ chính |
| `--color-text-muted` | `#475569` | Helper, breadcrumb và nhãn phụ; không dùng `#94A3B8` cho chữ |
| `--color-focus` | `#2563EB` | Focus trên surface/canvas |
| `--color-focus-sidebar` | `#BFDBFE` | Focus trên navy/active sidebar; bổ sung |
| `--color-success` / `--color-success-subtle` | `#15803D` / `#F0FDF4` | Thành công, luôn có chữ hoặc icon |
| `--color-warning` / `--color-warning-subtle` | `#B45309` / `#FFFBEB` | Cảnh báo |
| `--color-danger` / `--color-danger-subtle` | `#B91C1C` / `#FEF2F2` | Lỗi và hành động đăng xuất trong dialog |
| `--color-info` / `--color-info-subtle` | `#0369A1` / `#F0F9FF` | Thông tin phiên và nhãn giai đoạn sau |

Không thay mã màu gốc trong đề xuất; **bổ sung** viền control, focus sidebar, hover sidebar và nền pastel. Viền card mảnh giữ tinh thần ảnh; control tương tác dùng viền có thể nhận diện.

### Tương phản đã đo

Đo bằng công cụ Python theo relative luminance sRGB và `(L1 + 0.05) / (L2 + 0.05)`, không suy luận từ tên màu. [JSON đầy đủ](../../testing/assets/frontend-foundation-dot1-2026-10-12/color-contrast.json) lưu cả hai cặp bị loại. Chữ thông thường yêu cầu ≥4,5:1; viền/focus có ý nghĩa yêu cầu ≥3:1 theo mục tiêu thiết kế của prompt.

| Cặp foreground / background | Tỉ lệ | Sử dụng |
|---|---:|---|
| text / surface; text / canvas | 17,85; 16,61 | Chữ chính |
| text-muted / surface; text-muted / canvas | 7,58; 7,05 | Chữ phụ |
| on-primary / primary; on-primary / primary-hover | 5,17; 6,70 | CTA |
| sidebar-text / sidebar-bg | 12,02 | Menu thường |
| sidebar-active-text / sidebar-active-bg | 10,36 | Menu đang chọn |
| sidebar-text / sidebar-hover-bg | 9,85 | Menu hover |
| primary / primary-subtle | 4,75 | Chữ điểm nhấn |
| success / success-subtle | 4,79 | Badge/alert |
| warning / warning-subtle | 4,84 | Badge/alert |
| danger / danger-subtle | 5,91 | Badge/alert |
| info / info-subtle | 5,57 | Badge/alert |
| on-primary / danger | 6,47 | Xác nhận đăng xuất |
| control-border / surface; control-border / canvas | 4,76; 4,43 | Viền control đạt ≥3 |
| focus / surface; focus / canvas | 5,17; 4,81 | Focus nền sáng |
| focus-sidebar / sidebar-bg; / sidebar-active-bg; / sidebar-hover-bg | 12,56; 7,29; 10,30 | Focus sidebar |
| border / surface | **1,23** | Chỉ trang trí; loại khỏi viền input |
| primary / sidebar-active-bg | **2,00** | Loại khỏi focus sidebar; dùng focus-sidebar |

Đây là phép đo token phẳng; 12 cặp computed CSS trên giao diện thật đã kiểm lại, xem mục 6. Không kết luận đạt toàn bộ WCAG 2.2 AA.

## 3. Font và scale chữ

**Font hiện hành được Tài chốt:** `Be Vietnam Pro, system-ui, sans-serif`, nạp `next/font/google`, subset vietnamese, weight 400/500/600/700. Bỏ tải Poppins. [Ảnh kiểm lại 700](../../testing/assets/frontend-foundation-dot1-2026-10-12/be-font-700.png) · [Font tree đủ bốn weight](../../testing/assets/frontend-foundation-dot1-2026-10-12/be-font-probe.json). Tất cả ngữ cảnh H1/label/nút/sidebar và chuỗi kiểm dùng Be Vietnam Pro, không fallback hệ thống; ảnh không còn khác biệt cỡ chữ do trộn hai font. Phần dưới ghi lịch sử D4 và lý do thay đổi, không phải cấu hình hiện hành.

Chốt theo D4: **`Poppins, 'Be Vietnam Pro', system-ui, sans-serif`**. Poppins là font chính; Google Fonts chỉ cung cấp subset devanagari/latin/latin-ext. Probe WOFF2 và `unicode-range` thiếu **45 ký tự phân biệt** trong chuỗi yêu cầu ở 400/500/600/700; Be Vietnam Pro phủ đủ. Chrome xác nhận chuỗi kết hợp chỉ render bằng hai web font, không fallback sang Segoe UI khi font đã tải.

[Ảnh kiểm glyph](../../testing/assets/frontend-foundation-dot1-2026-10-12/font-glyph-probe.png) · [Cmap, URL và font thực tế trong Chrome](../../testing/assets/frontend-foundation-dot1-2026-10-12/font-glyph-probe.json).

Phase 2 nạp cả hai qua `next/font/google` tại root layout: Poppins preload subset `latin`, `latin-ext`; Be Vietnam Pro preload `vietnamese`; đều dùng `display: 'swap'`, weight `400/500/600/700` và CSS variable riêng. Với Poppins đặt `adjustFontFallback: false`, `fallback: []` để fallback hệ thống tự tạo không đứng trước Be Vietnam Pro. Token `--font-body` ghép hai CSS variable rồi mới tới `system-ui, sans-serif`. Chụp lại chuỗi sau scaffold; kiểm ảnh Phase 0 chưa thay cho kiểm `next/font` thực tế. [Tài liệu next/font](https://nextjs.org/docs/app/api-reference/components/font).

Đã kiểm runtime Next.js 16.4.0 bằng mẫu chẩn đoán `/font-probe` trong dev: H1 “Đăng nhập POSE CRM”, label, nút và mẫu sidebar ở 400/500/600/700. Đây chưa phải trang `/dang-nhap` hay app shell hoàn chỉnh. Font tree xác nhận chỉ Poppins và Be Vietnam Pro, đủ glyph. Quan sát chữ `ậ` trong “nhập” thấy thân chữ thấp hơn các chữ Poppins bên cạnh, rõ nhất ở 700; chưa thấy baseline trượt rõ. X-height từ WOFF2 do Next.js tạo: Poppins 0,548/0,551/0,554/0,558 em; Be Vietnam Pro 0,530 em ở cả bốn weight, thấp hơn khoảng 3,3–5,0%. Số đo hỗ trợ nhận xét thị giác, không phải ngưỡng WCAG hay tiêu chí font tự đặt.

[Ảnh runtime 400](../../testing/assets/frontend-foundation-dot1-2026-10-12/next-font-400.png) · [500](../../testing/assets/frontend-foundation-dot1-2026-10-12/next-font-500.png) · [600](../../testing/assets/frontend-foundation-dot1-2026-10-12/next-font-600.png) · [700](../../testing/assets/frontend-foundation-dot1-2026-10-12/next-font-700.png) · [Chi tiết 700, chụp browser scale 3](../../testing/assets/frontend-foundation-dot1-2026-10-12/next-font-700-detail.png). Áp dụng đúng yêu cầu bổ sung số 3: **dừng, báo ảnh, không tự đổi font**. Icon UserCircleIcon đã kiểm tồn tại trong `@heroicons/react@2.2.0`; cấu hình navigation chưa triển khai.

| Kiểu | Cỡ / line-height | Weight |
|---|---|---|
| Body và input | 16px / 1,5 | 400 |
| Label, button | 16px / 1,5 | 500 hoặc 600 |
| Helper/badge/breadcrumb | 14px / 1,5 | 400 hoặc 500 |
| H1 | 32px / 1,25; mobile 24px / 1,33 | 600 |
| H2 | 20px / 1,4 | 600 |
| H3 thẻ lối tắt | 16px / 1,5 | 600 |

Dùng rem tương ứng; mỗi trang một H1, không nhảy cấp heading. Topbar lặp tên trang bằng text, H1 nằm trong main. Tên/email/mã dài được xuống dòng (`overflow-wrap: anywhere`, text child có `min-width: 0`); badge nhiều role wrap thay vì cắt mất nội dung.

## 4. Hình học và chuyển động

| Nhóm token | Giá trị / quy tắc |
|---|---|
| Spacing | Bước 4px: 0/4/8/12/16/20/24/32/40/48/64; CSS rem |
| Radius | Control 8px; card/dialog 12px; avatar tròn |
| Border và focus | Border 1px; focus 2px, offset 2px; focus không bị clipping |
| Target | Button/input tối thiểu cao 44px; icon button vùng chạm 44×44px |
| Sidebar | Mở 256px, thu gọn 80px; desktop từ 1024px; tablet mặc định gọn |
| Drawer | Mobile dưới 768px; rộng tối đa 288px và chừa khoảng đóng bên ngoài; hỗ trợ 320px |
| Gutter / gap | Mobile 16px, tablet 24px, desktop 32px; gap thẻ 16px |
| Form / dialog | Form tối đa 448px; dialog tối đa 448px, chừa gutter mobile |
| Topbar | Mobile chỉ hàng Menu · POSE · User sticky, cao 56–64px; breadcrumb/tenant trong luồng. Desktop hàng chính 64–72px, thêm breadcrumb nếu cần; không bắt buộc 96px |
| Shadow | Một mức: `0 4px 12px rgb(15 23 42 / 0.08)`; dùng cho popover/dialog, card thường không cần shadow |
| Overlay | `rgb(15 23 42 / 0.48)`; không giảm opacity nội dung dialog |
| Layer | Base 0, sidebar 10, topbar 20, tooltip/menu 30, drawer 40, dialog 50 |
| Motion | 150ms cho màu/opacity; 200ms transform drawer; không animate width/sidebar layout |

`prefers-reduced-motion: reduce` tắt transition/animation không cần thiết; skeleton tĩnh vẫn diễn đạt checking. `scroll-padding-top` đo chiều cao hàng sticky thực tế. Bỏ sticky khi viewport cao ≤500px hoặc hàng topbar chiếm quá nhiều chiều cao sau zoom; kiểm lại 320px, zoom 200/400% theo WCAG 2.4.11.

Sau scaffold chụp chuỗi glyph trên giao diện dùng `next/font`: login H1, label, nút và sidebar ở 400/500/600/700. Kiểm cùng một từ có glyph từ hai font, baseline và cỡ chữ. Nếu lệch rõ, dừng và báo ảnh cho Tài; không tự đổi font. Icon Người dùng của nhóm Quản trị là **UserCircleIcon**, Khách hàng giữ UsersIcon; kiểm export sau cài package.

## 5. Component và hành vi

| Component | Quyết định |
|---|---|
| Button | Primary xanh, secondary có viền control, danger cho xác nhận đăng xuất; busy có chữ và chặn gửi lặp |
| TextField / PasswordField | Label luôn hiện; required native; helper liên kết; toggle có nhãn, `aria-pressed`, `aria-controls`; cho phép paste/autofill |
| Alert | Lỗi login `role="alert"`; thông tin hết phiên có text; không tự giành focus; không lộ field nào sai |
| Badge / TenantBadge | Chữ + icon khi cần; tenant tên và mã lấy từ me/organization; wrap và đọc được đầy đủ |
| Dialog / MobileNavDrawer | Modal, inert nền, giữ focus, Esc đóng khi có thể hủy, trả focus về trigger; dialog đăng xuất focus ban đầu vào Hủy |
| UserMenu | APG menu button, phím mũi tên/Home/End/Esc; tên/role tiếng Việt và avatar chữ đầu |
| Skeleton / EmptyState | Nhãn checking và `aria-busy`; empty có nội dung, không tạo số liệu |
| Shortcut card | `ul/li`, heading H3 dưới H2 “Lối tắt”, icon nền pastel; mục chưa triển khai là nội dung tĩnh, nhãn “Sẽ có ở giai đoạn sau” |

Heroicons thống nhất: `@heroicons/react/24/outline` cho điều hướng; `20/solid` cho trạng thái nhỏ. Icon trang trí `aria-hidden="true"`; icon button có tên truy cập được. Kiểm tồn tại từng icon sau cài package, ghi thay thế nếu có.

Nguồn hành vi: [WCAG 2.2](https://www.w3.org/WAI/WCAG22/quickref/), [APG modal dialog](https://www.w3.org/WAI/ARIA/apg/patterns/dialog-modal/), [APG menu button](https://www.w3.org/WAI/ARIA/apg/patterns/menu-button/), [Heroicons](https://github.com/tailwindlabs/heroicons). Prompt D2 ưu tiên hơn gợi ý error summary: required/email dùng validation native; lỗi xác thực dùng một alert chung, giữ focus hiện tại.

## 6. Đối chiếu triển khai và nghiệm thu

CSS thực tế dùng `--pose-*` trong tokens.css; các alias `--color-*` ở theme Tailwind dùng lại những biến này. Component không có hex riêng. Bổ sung token kích thước `--measure-*` cho form, menu, drawer, user text, nội dung và trạng thái; tất cả theo scale 4px. Không thay màu gốc so với bảng trên.

Be Vietnam Pro là font duy nhất được nạp. Bốn weight 400/500/600/700 đã chụp bằng next/font; font tree ở trang Tổng quan thật cũng chỉ có Be Vietnam Pro. Route chẩn đoán `/font-probe` và CSS probe đã được gỡ sau kiểm, ảnh lịch sử vẫn giữ trong evidence.

| Mục kiểm thực tế | Kết quả |
|---|---|
| Topbar mobile | 64px; chỉ Menu · POSE · User sticky; tenant/breadcrumb static |
| Topbar tablet/desktop | 72px với nội dung thường; breadcrumb/tenant ở hàng ngoài header |
| Focus không bị header che | Scroll-padding = chiều cao đo bằng ResizeObserver + 16px; 80px mobile, 88px desktop |
| Viewport thấp | Cao ≤500px: bỏ sticky, scroll-padding 16px; đã kiểm viewport tương đương zoom 200/400% |
| Nội dung dài | Tên 160 ký tự, email 205, tenant 150 và 5 role không tràn ngang; topbar tablet 234px tự bỏ sticky vì vượt 1/4 viewport |
| Icon | UserCircleIcon và toàn bộ icon menu tồn tại trong Heroicons 2.2.0; không phải thay icon gần nghĩa |
| Tương phản computed CSS | Text 17,85; helper 7,58; CTA/focus sáng 5,17; control border 4,76; active nav 10,36; badge info 5,57; focus sidebar 7,29 |
| Hành vi | Keyboard + RTL dialog/menu/drawer/password; CDP accessibility tree; reduced motion tắt transition |

[Đo computed CSS và font tree](../../testing/assets/frontend-foundation-dot1-2026-10-12/advanced-acceptance.json) · [Geometry responsive](../../testing/assets/frontend-foundation-dot1-2026-10-12/browser-acceptance.json) · [Ảnh desktop](../../testing/assets/frontend-foundation-dot1-2026-10-12/overview-width-1440.png) · [320px](../../testing/assets/frontend-foundation-dot1-2026-10-12/overview-width-320.png).

Đã bổ sung zoom native Chrome 200/400% qua profile riêng: CSS viewport 629×351 và 314×175, DPR thực tế 2/4, không device metrics/CSS zoom. Login, shell, nội dung dài, drawer/dialog giữ focus thấy được; header relative và scroll-padding 16px. [JSON/ảnh native zoom](../../testing/assets/frontend-foundation-dot1-2026-10-12/native-zoom-acceptance.json). Screen reader còn chưa kiểm. Không cài axe/MCP ngoài phạm vi; dùng accessibility tree và bàn phím thật.

## 7. Áp dụng trang công khai đợt 2 — 13/10/2026

Giữ nguyên font Be Vietnam Pro, bảng màu, spacing, radius và motion. Hero/CTA cuối trang dùng navy sidebar; section xen surface/canvas, icon nền primary-subtle. Không dùng Super Admin cho landing; nguồn ảnh và ghi chú Duralux/Super Admin ở bảng đầu tài liệu vẫn giữ nguyên. Không thay token màu.

Đo computed CSS trên production Chrome: chữ trắng/navy **17,85:1**; chữ sidebar-text/navy **12,02:1**; eyebrow và focus-sidebar/navy **12,56:1**; text/canvas **16,61:1**; muted/surface **7,58:1**, muted/canvas **7,05:1**; on-primary/primary **5,17:1**; icon primary/primary-subtle **4,75:1**; icon success/canvas **4,67:1**. Chữ đạt ngưỡng 4,5:1 và icon/focus đạt 3:1 cho các cặp đã kiểm; không suy ra đạt toàn bộ WCAG.

Header landing desktop 72px, mobile/tablet 64px; chỉ hàng logo/menu sticky, panel disclosure static trong luồng. Scroll-padding tương ứng 88/80px. Khi cao ≤500px, bỏ sticky và giữ scroll-padding 16px; native zoom 200/400% đã kiểm. Header dùng display: contents để hai hàng sticky/static độc lập; accessibility tree Chrome vẫn có đúng một banner, main và contentinfo. Alert login thêm margin dưới space-4 (16px), không đổi component Alert dùng chung.

[Evidence, tương phản và ảnh đợt 2](../../testing/frontend-landing-dot2-2026-10-13.md).

## 8. Landing v2 — concept chờ Tài chọn, 13/10/2026

Áp dụng chung prompt đợt 3 và bổ sung Fraunces/Almanac. Prototype chỉ ở route development; chưa thay trang `/`. Token landing có tiền tố `--landing-*`, scope trong `.concept-page`, không ghi đè màu/font/token của app. Body dùng lại Be Vietnam Pro. Sau chọn sẽ chỉ giữ font display được duyệt và gỡ route concept/probe.

| Token / quy tắc | Giá trị prototype | Lý do / nguồn ảnh |
|---|---|---|
| --landing-dark | #0B1220 | Nhịp tối–sáng theo prompt; ảnh 1 và 8 cho lớp nền tối |
| --landing-ink / muted | #FFFFFF / #CBD5E1 | Dùng lại chữ sáng v1; không dùng #94A3B8 làm chữ |
| --landing-accent A / B | #06B6D4 / #F59E0B | R3 đã chốt, B là Fraunces serif theo 1/1b |
| --landing-glow-blue / accent | Alpha **0,12** mỗi lớp | Hạ từ 0,16 sau đo điểm sáng nhất, cả A/B cùng alpha; không đổi màu nhấn |
| Dot grid / noise | Trắng alpha 0,025 / tối đa 0,03 | Trang trí nhẹ; không bù tương phản bằng bóng chữ |
| --landing-display-font A | Plus Jakarta Sans 800, next/font latin/vietnamese | So sánh sans theo R3 |
| --landing-display-font B | Fraunces variable wght/opsz/SOFT, hero 800; SOFT 30, optical auto | Ảnh 1 serif, 1b ít chữ/device/chip; probe kiểm cả 600/700/800 |
| H1 | clamp(40px, 5vw, 72px); line-height 1,05; tracking −0,02em | R4 thị giác; cùng thông số A/B, H1 tĩnh |
| Body / radius / focus | Token v1; focus 2px, offset 2px | Không đổi app, UI mock dùng token app |
| Nút hero / control | 56px / ≥44px; gradient primary→primary-hover | Prompt; ánh sáng lướt trắng tối đa 0,06 để chữ vẫn đủ tương phản |
| Thẻ | Hover −2px, active 1px, shadow chỉ trong mock hero | Ảnh 11/12; chỉ transform/opacity, không glass ngoài hero |
| Header | Một header thật 72px desktop, 64px tablet/mobile; panel mobile static bên ngoài | Không display: contents; khác cấu trúc landing v1 lịch sử |
| Scroll-padding | 88px desktop, 80px mobile/tablet; 16px khi cao ≤500px | Token chiều cao +16px, bỏ sticky ở viewport thấp/zoom |

Các kích thước scene/frame là token landing theo scale 4px, không sửa bảng spacing app. Icon dùng Heroicons đang cài. Mock HTML/CSS không ảnh stock, được aria-hidden; figcaption/nhãn minh họa vẫn đọc được. Chip đổi trọng tâm là button có aria-pressed, không giả tabs khi cả hai nội dung vẫn hiển thị.

Đo bằng công thức WCAG sRGB tuyến tính `(L_sáng + 0,05)/(L_tối + 0,05)`. Điểm xấu nhất dùng biên bảo thủ: compositing navy → blue 12% → accent 12% → trắng grid 2,5% → trắng noise 3%, giả định cùng đạt cực đại tại một pixel (sáng hơn thực tế vì quầng có vị trí khác nhau). Không đo chỉ background-color bỏ qua gradient. Màu nền biên xấp xỉ A rgb(26,58,85), B rgb(53,55,62); tính tỉ lệ bằng số float trước làm tròn.

| Cặp | A | B |
|---|---:|---:|
| Chữ trắng / navy thuần | 18,72:1 | 18,72:1 |
| Muted / navy thuần | 12,61:1 | 12,61:1 |
| Accent / navy thuần | 7,71:1 | 8,72:1 |
| Chữ trắng / biên nền sáng nhất | 11,85:1 | 11,89:1 |
| Muted / biên nền sáng nhất | 7,98:1 | 8,01:1 |
| Accent / biên nền sáng nhất | **4,88:1** | **5,54:1** |
| Chữ trắng / điểm sáng nhất của shimmer CTA | 4,66:1 | 4,66:1 |

CTA primary thuần giữ 5,17:1; border control và focus tối phải ≥3:1. Cyan/amber không làm chữ trên nền sáng; ô mock sáng vẫn dùng text/primary/success v1. Không suy ra đạt toàn bộ WCAG từ các cặp này. Ảnh, font byte, hành vi và giới hạn kiểm: [wireframe v2](landing-wireframe-v2.md), [evidence đợt 3](../../testing/frontend-landing-dot3-2026-10-13.md).
