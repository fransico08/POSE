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

## 8. Landing v2 — lịch sử concept Phase 1, 13/10/2026

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

Sau review 13/10, Tài chọn B nhưng thay cấu hình font bằng **Fraunces [700,800], vietnamese/latin, không variable/axes**; bỏ SOFT và optical sizing cũ trong route/probe. Số đo cache sạch **81.736 byte** (81,736 KB / 79,820 KiB), glyph cả hai weight đủ. Dừng theo giới hạn 80 KB trước khi áp dụng landing v2; bảng token/font Phase 1 trên vẫn là lịch sử, không phải cấu hình font đã vượt cổng nghiệm thu. Không đổi font/token app. [Evidence font tĩnh](../../testing/frontend-landing-dot3-2026-10-13.md#sau-duyệt-b--dừng-ở-giới-hạn-font-trước-phase-23).

### Landing v2 — Concept B đang áp dụng sau duyệt

Tài xác nhận **80 KiB = 81.920 byte**; Fraunces tĩnh 700/800 tổng **81.736 byte đạt**. Không thử bỏ 700 hoặc quay về A. Hai descriptor dùng chung ba file; bỏ descriptor 700 không giảm byte. Cấu hình next/font chỉ `weight: ["700", "800"]`, `subsets: ["vietnamese", "latin"]`, `display: "swap"`; không truyền axes, CSS `font-optical-sizing: none`. H1 800, H2 700; body Be Vietnam Pro của app giữ nguyên. Đã gỡ concept/probe và Plus Jakarta Sans khỏi source.

Token hiện tại scope `.landing-page` trong `frontend/src/styles/landing-v2.css`; không ghi đè token màu/font app:

| Token / quy tắc | Giá trị đang áp dụng | Lý do |
|---|---|---|
| --landing-dark / ink / muted / accent | #0B1220 / #FFFFFF / #CBD5E1 / #F59E0B | B đã duyệt; hổ phách chỉ chữ lớn nền tối và trang trí |
| --landing-glow-blue / amber | rgba(37,99,235,0.24) / rgba(245,158,11,0.24) | Tăng không khí hero theo chỉnh sửa 3 |
| --landing-grid / noise | Trắng 0.055 / 0.03 | Dot grid thấy được; noise CSS nhỏ, không ảnh raster hoặc SVG filter |
| --landing-glass / border-dark | rgba(15,23,42,0.92) / #A3B1C3 | Chỉ hero/bảo mật có lớp tối; border tách control |
| --landing-hero-font / heading-font | clamp(40px,5vw,72px) / clamp(28px,3.1vw,44px) | Tracking −2%, H1 line-height 1.05 |
| --landing-scene-height / mock-slot | 640px / 144px; mobile giảm scene riêng | Dành chỗ thẻ, toast và caption, giảm CLS |
| --landing-float / lift / press | 6px / −2px / 1px | Chỉ transform/opacity cho chuyển động |
| Nút hero / header / pause | 56px / 44px / 44px | Pause nằm dưới phải khung; dải ngành icon 44px |
| Chấm ngành | Button 32×32px, icon 8px | Có tên truy cập, focus và aria-pressed |

Đo lại WCAG sRGB với biên bảo thủ navy → blue 24% → amber 24% → trắng grid 5.5% → trắng noise 3% (giả định tất cả cực đại cùng điểm). Nền xấp xỉ rgb(87,82,80); dùng float trước làm tròn. Đây là cận xấu hơn quầng thực tế tách vị trí, áp dụng cả khối bảo mật.

| Cặp | Tỉ lệ |
|---|---:|
| Trắng / navy thuần | 18,72:1 |
| Muted / navy thuần | 12,61:1 |
| Hổ phách / navy thuần | 8,72:1 |
| Trắng / biên sáng nhất | 7,69:1 |
| Muted / biên sáng nhất | 5,18:1 |
| Hổ phách / biên sáng nhất | 3,58:1 — chỉ H1 lớn, ngưỡng 3:1 |
| Focus #BFDBFE / biên sáng nhất | 5,41:1 |
| Border #A3B1C3 / biên sáng nhất | 3,54:1 |
| Trắng / primary, shimmer sáng nhất | 5,17:1 / 4,66:1 |

Cụm xoay dùng chữ trắng, gạch chân hổ phách; không dùng hổ phách nhỏ trên nền sáng. Nhãn/thẻ sáng dùng token v1. Nguồn ảnh đúng số: 1 Wanderlust (serif/chiều sâu), 1b Almanac (khung thiết bị/chip demo), 2 phi hành gia (đường chia/tay nắm), 3 Pipedrive (tab + UI trái/ý phải), 4 nhà gỗ (lưới nhiều kích thước), 6 Panerai (hai nửa/thuộc tính/mũi tên/chấm). Không sao chép nội dung/hình/logo; không dùng Super Admin cho landing.

H1 luôn đọc được từ HTML, chỉ dịch chuyển khi vào trang; phần trang trí khởi động sau hai frame và chỉ khi nhìn thấy. Motion, fallback, tương phản, ảnh và số đo thực tế trong [evidence đợt 3](../../testing/frontend-landing-dot3-2026-10-13.md). Cổng Lighthouse mobile còn chưa đạt; không suy ra đạt toàn bộ WCAG hoặc nghiệm thu hiệu năng.

### Review điểm 2 — điều chỉnh theo A1–A5/B6–B13

Phần này thay cấu hình đang áp dụng ở đoạn trên: Fraunces vẫn tĩnh 700/800, subsets **vietnamese/latin/latin-ext** để preload đủ 3 file đã tải, tổng 81.736 byte không tăng. **H1 không chuyển động vào trang**, không transform/opacity; H1/eyebrow/phụ đề/CTA dựng bởi HeroSection server. Các phần động nằm trong figure và RotatingPhrase riêng, chia sẻ điều khiển qua context nhận opaque children từ server.

H1 desktop giữ 72px tại 1440, 51,2px tại 1024; mobile 375 dùng 36px, 320 dùng 30,72px để mỗi câu một dòng. Mỗi span white-space:nowrap; text-wrap:balance chỉ ở h1–h3, không ghi đè nowrap của câu; paragraph text-wrap:pretty. H1 có 3 dòng ở các chiều rộng đã kiểm. K4/K7 cân dòng, không để “sóc”/“đó” thành dòng riêng.

Hero thẻ nổi ở hàng dưới khung, lấn nhẹ mép trái/phải; vùng đệm cuối pipeline dành cho phần chồng lớp, không đè cột Đủ điều kiện. Desktop 3 cột; dưới 768px chỉ cột “Cơ hội bán hàng” với đầy đủ thẻ, Customer 360 ở mép dưới. Reduced motion hiển thị “Đã tắt chuyển động”, không icon phát; dải ngành ẩn control khi không còn chuyển động. Các màu/viền/glow và giới hạn tương phản giữ nguyên, không đổi token app.

K2 có lưới 6 dòng, 2 dòng trùng, 4 ghi chú, 2 bong bóng và cảnh báo; trái/chưa có – phải/với POSE. K3 mỗi vai trò 5 dòng có avatar/hạn/trạng thái; K5 có timeline 4 hoạt động và 6 chip ngành outline; K6 minh họa thẻ mẫu có badge/tên/thuộc tính ở nửa trái. Hình vẫn thuần HTML/CSS với nhãn minh họa, không số liệu/khách hàng thật.

Globals chỉ tokens/theme/base/SkipLink. CSS app nạp từ app/login, status/buttons dùng lại riêng cho 404/error. `experimental.inlineCss` được thử theo A5 và đang giữ cục bộ chờ review: chỉ production, áp dụng toàn site, lặp style trong HTML/RSC và mất cache stylesheet độc lập. A5 vẫn chưa đạt mobile/LCP; không tự đổi font-display hoặc font H2. Số đo từng bước và rủi ro trong evidence.

### Quyết định mới của Tài ngày 14/10 — bỏ inlineCss, theo dõi LCP

Mục này thay trạng thái phép thử ở trên. Đã gỡ `experimental.inlineCss`, giữ A1–A3 và Fraunces 700/800, display swap, preload đủ ba subset. Không thử optional hoặc đổi font H2. A5 không cải thiện LCP (3,614 s so với A3 3,473 s), làm document transfer 28.698 → 52.287 byte, lại là cờ thử nghiệm toàn app và mất cache stylesheet độc lập.

Tài chấp nhận ngoại lệ LCP mô phỏng: cổng landing mới **Performance mobile trung vị ≥88; Accessibility/Best Practices/SEO 100; CLS <0,1; TBT mobile trung vị <200 ms; desktop Performance ≥95**. LCP vẫn phải ghi trung vị/min–max, nhưng không còn cổng dừng. LCP là chữ H1 dùng web font; mô phỏng mobile khoảng 1,6 Mbps cùng chi phí font/JS, còn trace thật localhost trước review TTFB khoảng 13 ms, render delay khoảng 0,33 s. Đây là lý do ngoại lệ được owner duyệt, không khẳng định font là nguyên nhân duy nhất hoặc coi trace localhost là trải nghiệm người dùng thực. Khi có domain triển khai, đo lại dữ liệu thực trên thiết bị/mạng và người dùng thật.

Ở 1440×900, hero khoảng **895 px**. Customer 360 chồng vào đáy trống khoảng 31,6%; toast và badge lấn cạnh phải khoảng 25,2%; không che thẻ hoặc chữ. Mobile <768 chỉ pipeline hai thẻ/toast, ẩn Customer 360/badge/picker không còn hữu ích bằng display:none; mock giữ aria-hidden. Hero 375 khoảng 1156 px, giảm rõ so với bản trước; CTA top 442 px và đầu mock 606 px nằm trong màn hình đầu 812 px. H1/font/token màu app không đổi.

K6 phân biệt thẻ sản phẩm mẫu bên trái (giá trị) với danh sách thuộc tính danh mục bên phải (kiểu mẫu). Nguồn BR:32/DEC-04 và bảng chữ cập nhật trong content v2. Điểm dừng2 chỉ tự động được duyệt khi cổng mới đạt và các chỉnh sửa hoàn tất; chưa tự coi là được duyệt khi đo chưa qua Performance.

### Nghiệm thu cuối 14/10 — hydration và micro-interaction

Mục này thay trạng thái đang chờ ở các mục trước. Giữ nguyên B, Fraunces 700/800, ba subset, swap, màu/token app và toàn bộ ngoại lệ LCP đã được Tài duyệt. Không inlineCss/package mới. Chuỗi Lighthouse mobile đầu 7 lượt có benchmarkIndex 1.540–2.878,5 (chênh 86,9%); giữ đủ lượt và đo lại cả chuỗi theo yêu cầu Tài. Chuỗi lặp ổn định 2.666–3.026 (13,5%) đạt **Performance 89 (86–89), TBT 78 (72–175 ms), A/BP/SEO 100, CLS 0,0172**. Desktop 100, TBT 0, CLS 0,0117. Đạt cổng chính 88/200, không cần dùng ngoại lệ hiệu năng mức 85/250; điểm dừng 2 được duyệt theo điều kiện và đã hoàn tất Phase 5–6.

LCP mô phỏng mobile **3,607 s (3,599–3,700)** vẫn được theo dõi. Phần tử LCP là span cuối H1 “Trên một nền tảng.”; nguồn trace localhost khoảng 13 ms TTFB/0,33 s render delay ở lần trước được giữ như dữ liệu lịch sử, không là phép đo của chuỗi này. Chuỗi ổn định lần cuối TTFB khoảng 9–12 ms, render delay 0,31–1,17 s tùy lượt; cần dữ liệu thực khi có domain để đánh giá ảnh hưởng mạng/font/hydration. Không suy ra đạt LCP <2,5 s hoặc đạt toàn bộ WCAG.

Markup mock/tab/thuộc tính chuyển sang server; client chỉ quản lý tương tác. Motion khởi động sau idle và viewport. Journey dùng CSS scroll timeline với đường tĩnh khi không hỗ trợ. Tỉ lệ tương phản bảo thủ ở bảng B đang áp dụng vẫn giữ: trắng 7,69:1, muted 5,18:1, hổ phách lớn 3,58:1 trên điểm sáng nhất; chữ nhỏ dùng trắng.

Login dùng lại mock UI server và ba câu lợi ích có nguồn cũ. Để giữ panel không control/thứ tự Tab và tuân giới hạn tự chạy 5 giây, hiệu ứng dùng hai nhịp 2,4 s, tổng 4,8 s hoạt động rồi dừng. Skeleton shimmer cùng ngân sách; dừng/hiển thị tĩnh khi reduced-motion, tạm dừng khi tab ẩn. Sidebar chuyển chỉ mục 200 ms, lối tắt hover −2 px/150 ms; không tạo thao tác hoặc thông báo nghiệp vụ giả. Lời chào theo giờ trình duyệt có bản server “Chào” ổn định. Font/token app không đổi; CSS hoàn tất đặt sau media rule để thực sự gỡ animation. [Evidence cuối](../../testing/frontend-landing-dot3-2026-10-14.md#nghiệm-thu-cuối--hydration-và-phase-56) có 116 test, ảnh và hồi quy backend 5 vai trò.
