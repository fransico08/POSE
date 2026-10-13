# Frontend landing đợt 3 — evidence từng phase

Ngày **13/10/2026**, Owner **Huỳnh Minh Tài**, đầu vào **cc5e195**, nhánh Tài sạch trước sửa. Phạm vi hiện tại: **Phase 0–1 đã thực hiện**, chưa thay `/` v1 hoặc micro-interaction login/app. Dừng sau Phase 1 chờ Tài chọn A/B và duyệt nội dung.

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

## Phase 1 — prototype và kiểm thật

[Wireframe v2](../design/ui-ux/landing-wireframe-v2.md) có khung 1440/768/375, cây landmark/heading, bảng nguồn ảnh thực tế, A/B và font byte. [Design system](../design/ui-ux/design-system-v1.md#8-landing-v2--concept-chờ-tài-chọn-13102026) bổ sung token landing có scope riêng. [Content v2](../design/ui-ux/landing-content-v2.md) đã rút inventory xuống **448 từ** mặc định, không tính FAQ trả lời đóng/panel chưa chọn; chưa coi đó là số từ DOM toàn trang.

Hero A/B cùng cấu trúc/nội dung/motion, chỉ khác font display và accent. A Plus Jakarta Sans 800; B **Fraunces** variable wght/opsz/SOFT (thay Bricolage theo bổ sung), hero 800/SOFT 30/optical auto. Nguồn 1b Almanac áp dụng khung thiết bị/chip demo/ít chữ; không đổi riêng B thành căn giữa. UI dựng bằng HTML/CSS với dữ liệu mẫu trung tính, nhãn minh họa; không lấy hình/claim/email của ảnh tham chiếu.

Một observer dùng chung cho hero/marquee/header; CSS animation chủ yếu transform/opacity, nút shimmer dùng background-position. Hero có một nút dừng toàn bộ; marquee có nút riêng. SSR/unsupported IO mặc định tĩnh, reduced motion không có data-running/timer hoạt động. Hero focus vào CTA/chip dừng đến khi người dùng chọn Tiếp tục. Tilt tùy chọn chưa dùng; không thêm package hoặc animation library.

### Lệnh và kết quả

Các truy vấn UI UX Pro Max dùng Python sẵn có và **đường dẫn tuyệt đối** tới `.agents/skills/ui-ux-pro-max/scripts/search.py` trong worktree, không `--persist`/`--force`:

```text
search.py --domain landing "SaaS CRM landing interactive"
search.py --domain style "dark hero glow bento"
search.py --domain ux "auto rotating content pause"
search.py --domain typography "display heading Vietnamese"
npm run dev -- --port 3002
npm run test -- tests/landing-concepts.test.tsx
npm run lint
npm run typecheck
npm test
# Dừng dev trước build
npm run build
npm run start -- --port 3003
git diff --check
```

| Kiểm | Kết quả |
|---|---|
| Lint | 0 lỗi/cảnh báo; delegate focus/hover của section có comment giải thích ngoại lệ a11y, không biến section thành control |
| Typecheck | Đạt |
| Test prototype | 8/8: H1/chữ A/B giống nhau, source credentials, production guard, pause/focus, hidden/reduced, demo chip, marquee, disclosure/header |
| Toàn bộ test | **86/86**, 7 file; giữ 66 foundation + 12 landing v1 + 8 prototype |
| Build | Đạt; `/` vẫn **○**; các route concept được prerender thành kết quả notFound, kiểm HTTP riêng bên dưới |
| Package/lock | Không thay đổi, không cài package |
| Phần đợt 1 | Không sửa root layout/font/token app, login form, backend/BFF/proxy/session |

Next Link dùng trong CTA. Lint ban đầu phát hiện link native về `/` ở HeroMotion và event delegation trên section; đã sửa Link và giải thích đúng phạm vi handler chỉ dừng trang trí. Không vô hiệu hóa cả nhóm lint.

### Trình duyệt, motion và responsive

Chrome profile riêng qua CDP; Input/DOM.focus/Tab/Enter/Space/Esc thật, Runtime chỉ đọc DOM/style. Chụp lúc hover tạm dừng để chữ xoay không nằm ở frame crossfade; không sửa DOM bằng JS để làm đẹp ảnh. Có ảnh riêng khi animation chuyển sang Thắng.

| Mục | A và B |
|---|---|
| Landmark | 1 H1 tĩnh, 1 header; mỗi section aria-labelledby trỏ heading trong section |
| Font H1 desktop | Chỉ family custom đúng concept, không system fallback; 72px/800, 3 dòng ở 1440px |
| Menu mobile | Enter mở; Tab vào link; Esc đóng, trả focus nút; không modal |
| SkipLink | Enter đưa focus tới main#noi-dung |
| Hero pause/resume | Space dừng; phrase giữ nguyên sau 4,2s; tiếp tục chuyển được tới Thắng |
| Chip demo | Enter chọn Hồ sơ, aria-pressed cập nhật; focus giữ hiệu ứng dừng đến explicit restart |
| Tab trình duyệt ẩn | Mở tab riêng/bật tab đó: visibility hidden, data-running tắt; không chỉ mô phỏng CSS |
| Reduced motion | 0 data-running, **0 animation**, clone display none, chip wrap thành danh sách tĩnh |
| Console/runtime | Không lỗi hoặc warning trong các lượt concept kiểm |
| 320/375/768/1024/1440 | Không tràn ngang; H1 tương ứng 40/40/40/51,2/72px |
| Header | 64px dưới 1024, 72px desktop; chiều cao viewport 450px bỏ sticky, scroll-padding 16px |
| Native zoom 200% | CSS viewport 629×351, DPR 2, không tràn ngang, header static, scroll-padding 16px |
| Native zoom 400% | CSS viewport 314×175, DPR 4, không tràn ngang, header static, scroll-padding 16px |

Header đổi nền theo sentinel có test IO cho ba trường hợp: chưa tới / đã qua trên viewport / quay lại. Chưa kiểm tay chuyển nền trong một trang v2 đầy đủ: prototype chỉ K1 và dải ngành nên chưa có các section dưới để cuộn dài; sẽ kiểm lại ở Phase 4. Touch kéo slider, tabs vai trò và bộ chọn ngành chưa có ở Phase 1, không báo đạt.

### Tương phản và font byte

Đo WCAG sRGB bằng Python, compositing các lớp ở biên sáng nhất (chi tiết trong design system). Alpha glow ban đầu 0,16 cho cyan chỉ đạt **4,33:1** ở biên bảo thủ; đã giảm cả A/B xuống **0,12**. Cyan/amber không dùng làm chữ trên nền sáng.

| Cặp chữ | A | B |
|---|---:|---:|
| Trắng / navy #0B1220 | 18,72:1 | 18,72:1 |
| Muted / navy | 12,61:1 | 12,61:1 |
| Trắng / biên glow+grid+noise | 11,85:1 | 11,89:1 |
| Muted / biên glow+grid+noise | 7,98:1 | 8,01:1 |
| Accent / biên glow+grid+noise | **4,88:1** | **5,54:1** |
| Trắng / shimmer CTA sáng nhất | 4,66:1 | 4,66:1 |

Font WOFF2 display tải ở profile mới: A **26.964 byte** (26,33 KiB), B **260.560 byte** (254,45 KiB). Dùng resource entries và độ dài response font, loại các file Be Vietnam Pro chung bằng đối chiếu hai route; không gọi đây là JS gzip. DOM prototype **208 phần tử** tại lượt chụp development, gồm markup dev; chưa có số DOM của toàn landing v2.

Ảnh: [A desktop](assets/frontend-landing-dot3-2026-10-13/concept-a-desktop.png), [A mobile](assets/frontend-landing-dot3-2026-10-13/concept-a-mobile.png), [B desktop](assets/frontend-landing-dot3-2026-10-13/concept-b-desktop.png), [B mobile](assets/frontend-landing-dot3-2026-10-13/concept-b-mobile.png). [A reduced](assets/frontend-landing-dot3-2026-10-13/concept-a-reduced-mobile.png), [B reduced](assets/frontend-landing-dot3-2026-10-13/concept-b-reduced-mobile.png), [A Thắng](assets/frontend-landing-dot3-2026-10-13/concept-a-won.png), [B Thắng](assets/frontend-landing-dot3-2026-10-13/concept-b-won.png), [B zoom 200%](assets/frontend-landing-dot3-2026-10-13/concept-b-zoom-200.png), [B zoom 400%](assets/frontend-landing-dot3-2026-10-13/concept-b-zoom-400.png). Cận tiêu đề/glyph nằm trong bảng wireframe/Phase 0.

### Production guard và kiểm giữ v1

HTTP GET bằng Python requests, không theo redirect, chỉ ghi status/robots/location; không log body auth hoặc cookie:

| URL | HTTP / kết quả |
|---|---|
| /thu-nghiem/hero-a | **404**, robots noindex |
| /thu-nghiem/hero-b | **404**, robots noindex |
| /thu-nghiem/font-check | **404**, robots noindex |
| / | **200**, 1 H1, robots index/follow; nội dung v1 giữ nguyên |
| /app chưa có phiên | **307** → /dang-nhap?returnTo=%2Fapp |
| /dang-nhap | **200**, 1 H1, noindex/nofollow |
| /api/session/me chưa có phiên | **401**, X-Robots-Tag noindex/nofollow |
| /robots.txt, /sitemap.xml | **200** |

Dev và production server/profile Chrome do tác vụ khởi chạy đã dừng. Không chạy backend/PostgreSQL hoặc đọc credentials trong phase này.

## Phần chưa thực hiện — theo điểm dừng 1

- Chờ Tài chọn A/B và duyệt chữ. Chưa thay landing v1; chưa dựng K2–K7 hay xóa route concept/font không chọn.
- Chưa đo Lighthouse 13.x, JS gzip, LCP/CLS/TBT hoặc DOM toàn trang v2; chưa chạy npm ci trên bản sao git archive. Các bước đó thuộc Phase 4 sau duyệt.
- Chưa screen reader, axe, toàn trang touch/keyboard hoặc hồi quy backend 5 role/refresh/network/logout; Phase 5 cần điểm dừng 2 được duyệt. Không tuyên bố đạt toàn bộ WCAG.
- Favicon/OG mới, metadata/FAQ JSON-LD v2, SITE_URL production warning và micro-interaction login/app chưa làm; nằm ở các phase sau, không phải bỏ sót tự chuyển phạm vi.

Commit cục bộ: **e7680e9** — glyph next/font và draft content; **842d593** — hero A/B dev-only và 8 test. Tài liệu/ảnh review ở commit tiếp theo. Author/committer fransico08, không Co-authored-by; không push/merge/PR/Trello.
