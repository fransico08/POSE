# Frontend landing đợt 3 — evidence từng phase

**Review tiếp theo:** xem [evidence 14/10](frontend-landing-dot3-2026-10-14.md) cho A1–A5/B6–B13 và kiểm sạch cuối. Các số đo dưới đây giữ nguyên làm lịch sử.

**Trạng thái tại 13/10:** Concept B đã dựng Phase 2–3; dừng tại điểm2 do cổng Performance/LCP mobile chưa đạt. Xem [phần Phase 2–4](#phase-24--concept-b-sau-xác-nhận-80-kib). Các ghi chú chờ chọn/font80KB phía dưới là lịch sử.


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

## Sau duyệt B — dừng ở giới hạn font trước Phase 2–3

Ngày 13/10/2026, Tài chọn **B (Fraunces + hổ phách)**, duyệt bố cục/cấu trúc/nguồn và bổ sung chỉnh sửa chữ/hero/điều khiển/ảnh 2,3,6. Điều kiện mới số 1 yêu cầu `weight: ["700", "800"]`, `subsets: ["vietnamese", "latin"]`, không weight variable, không axes opsz/SOFT; **≤80 KB, vượt thì dừng**. Đã đổi cấu hình route B/probe và bỏ CSS SOFT/optical sizing cũ để đo, chưa dựng các khối tiếp theo.

Chrome dùng profile mới/cache sạch, next/font thật. Đọc CSSFontFaceRule đúng family Fraunces, phân giải URL tương đối theo URL stylesheet, GET từng WOFF2 local và đối chiếu resource entries. Có **6 face descriptor** (700/800), cùng tham chiếu **3 file duy nhất**; tính byte mỗi file một lần, không cộng trùng hai weight và không tính Be Vietnam Pro/Poppins/Jakarta.

| File display duy nhất | Weight descriptor tham chiếu | Byte |
|---|---|---:|
| File 1 | 700, 800 | 11.536 |
| File 2 | 700, 800 | 33.640 |
| File 3 | 700, 800 | 36.560 |
| **Tổng** | 3 file; cả 3 tải trong browser | **81.736** |

Đo lại trong profile mới lần hai vẫn **81.736 byte**. Quy đổi rõ đơn vị: **81,736 KB thập phân**, **79,820 KiB**. Dùng giới hạn bảo thủ đúng chữ KB = **80.000 byte** thì vượt **1.736 byte**; không tự coi KB là KiB hoặc tự tăng ngân sách. Nếu Tài muốn giới hạn 80 KiB = 81.920 byte, cần xác nhận cách hiểu đó.

Glyph 700 và 800 đều **125 glyph custom Fraunces**, không system fallback, không lỗi console/runtime. Đã xem ảnh chuỗi dấu: [700 tĩnh](assets/frontend-landing-dot3-2026-10-13/fraunces-static-700.png), [800 tĩnh](assets/frontend-landing-dot3-2026-10-13/fraunces-static-800.png). CSS/next/font không còn khai báo SOFT/opsz; font-optical-sizing none. Request Google/Next có thể dùng chung binary tối ưu cho hai face; không suy ra kích thước từ tên weight hoặc số descriptor.

Lệnh: `npm run dev -- --port 3002`, phép đo CDP/CSS.getPlatformFontsForNode và Python GET WOFF2; `npm run lint`, `npm run typecheck` kiểm phần thay cấu hình. Browser profile và server của phép đo đã dừng. Package/lock/root layout/font app/session/BFF không đổi; không đọc credentials hoặc chạy backend.

**Dừng theo điều kiện 1 của Tài.** Phương án để Tài quyết: (a) thử chỉ Fraunces 800 cho H1 rồi đo lại, chưa hứa chắc đạt; hoặc (b) quay về A, số đo lịch sử 26.964 byte nhưng cần kiểm lại khi áp dụng. Chưa đổi `landing-content-v2.md` theo bản chữ mới/chưa code K1–K7; không bỏ qua cổng font để thực hiện Phase 2–3.

## Phase 2–4 — Concept B sau xác nhận 80 KiB

Đây là trạng thái hiện tại, thay các ghi chú chờ chọn/vượt 80 KB ở phần lịch sử. Tài xác nhận **80 KiB = 81.920 byte**, chọn B và duyệt cấu trúc/nguồn. Giữ Fraunces tĩnh 700/800, vietnamese + latin, không axes; không thử 800-only/A. Bản chữ tự nhiên cập nhật **trước code** trong [content v2](../design/ui-ux/landing-content-v2.md), truy vết D/N/M/Q đầy đủ. Cổng hiệu năng mobile còn **chưa đạt**; dừng tại điểm 2, không làm Phase 5.

### Phạm vi đã dựng

K1–K7, header duy nhất/disclosure, footer, token landing riêng, motion dùng chung, mock kit, favicon và metadata/JSON-LD v2. Pipeline 7 thẻ giữ 2–3 thẻ/cột khi chuyển; Customer 360 có 3 hoạt động mẫu; badge ghi chú có nền tối riêng, không chìm vào thẻ sáng. Hero có pause 44px ở dưới phải và chip đổi trọng tâm. Dải ngành clone aria-hidden/pause icon; slider native range + nút xem 0/100; tabs APG; journey sticky desktop/fallback danh sách mobile; bento 6 ô; industry 6 tabs + mũi tên/chấm button; bảo mật 2 góc nhìn; FAQ 5 details và CTA.

Không package mới, không sửa root/app font hoặc token màu app, backend/session/BFF/proxy/form. Providers đã tách khỏi public từ đợt 2, không đổi ở đợt 3. Diff package/lock/auth/session/root layout so với 2608429 rỗng. Không credentials/email/mã tổ chức trong nội dung. Route concept/probe/font A và component/CSS v1 không dùng đã gỡ. OG mới 1200×630 **49.770 byte**, ảnh chụp hero thật có nhãn minh họa; không ảnh stock/AI.

### Môi trường và kiểm bản sao sạch

Windows, PowerShell 5.1, Node 24.13.0/npm 11.6.2; Next 16.4.0, React 19.3.0, TypeScript 6.0.3, Vitest 5.0.4, ESLint 9.39.5, Lighthouse 13.5.0, Chrome 141.0.7390.123. Chrome DevTools MCP không có trong phiên; dùng Chrome headless/CDP với profile riêng. Đây là fallback kiểm runtime, không nhận đã dùng MCP. Chỉ đọc DOM/styles/AX/network tài nguyên công khai; thao tác bằng Input/DOM.focus. Không cookies/storage/credentials/backend/DB.

Bản cuối source **670124f** từ `git archive --format=tar HEAD frontend`, thư mục tạm mới, ban đầu không node_modules/.next/.env. Chạy tuần tự:

```powershell
npm ci
npm run lint
npm exec -- next typegen
npm run typecheck
npm test
$env:SITE_URL = 'http://127.0.0.1:3003'
npm run build
npm run start -- --port 3003
```

| Kiểm | Kết quả |
|---|---|
| npm ci / lint / typecheck | Đạt |
| Vitest/RTL | **100/100**, 14 file; 66 foundation giữ nguyên + 34 landing/motion/SEO |
| Build | Đạt; **○ /**, concept không còn trong route table |
| SITE_URL thiếu ở production | Build local vẫn đạt, có cảnh báo rõ; kiểm riêng trong worktree trước đó |
| Author/committer | fransico08; không Co-authored-by; không push/merge/PR/Trello |

npm ci ghi cảnh báo ESLint 9.39.5 hết hỗ trợ và 5 advisory mức high của dependency hiện có. Package/lock giữ nguyên; chưa phân tích/khắc phục advisory, không chạy audit fix hoặc đổi dependency ngoài phạm vi.

### SEO/AEO và HTTP production

| Đường dẫn / kiểm | Kết quả |
|---|---|
| / |200; 1 H1; title 50 ký tự; description 147; canonical theo SITE_URL |
| JSON-LD |1 script, JSON.parse được; WebSite/SoftwareApplication/FAQPage; 5 câu/đáp án khớp details; escape<; không offers/rating/review giả |
| Heading/section/nav |Không nhảy cấp; mỗi section aria-labelledby tới heading riêng; anchor tới id có thật |
| Chữ công khai |Không chữ R1, SALES_ONLY/CARE_VISIBLE/permission hoặc email/credentials |
| /robots.txt |200; allow /; disallow /app,/api,/dang-nhap; sitemap đúng origin |
| /sitemap.xml |200; chỉ /; lastmod 2026-10-13 từ contentUpdatedAt |
| /favicon.ico,/icon.svg,/apple-icon.png |200 |
| /app chưa đăng nhập |307→/dang-nhap?returnTo=%2Fapp |
| /dang-nhap |200,meta noindex/nofollow; chưa đăng nhập backend thật đợt 3 |
| /api/session/me |401 khi chưa phiên, X-Robots-Tag noindex,nofollow |
| /thu-nghiem/hero-a,hero-b,font-check |404 production |

JSON-LD đối chiếu kiểu/property schema.org: WebSite name/url/inLanguage; SoftwareApplication name/url/applicationCategory/operatingSystem/description; FAQPage mainEntity→Question/name/acceptedAnswer→Answer/text. Chưa chạy Rich Results Test hoặc Schema Markup Validator qua trình duyệt: localhost không có URL công khai. FAQ markup mô tả ngữ nghĩa, không hứa FAQ rich result.

### Lighthouse và ngân sách thực tế

Npx tạm, không dependency; report đầy đủ chỉ trong thư mục tạm, không commit. Hai lượt cuối chạy tuần tự trên next start, không đồng thời npm/test hoặc browser acceptance. Default mobile simulated slow 4G/CPU ×4; desktop preset. Ví dụ:

```powershell
npx --yes lighthouse@13.5.0 http://127.0.0.1:3003/ --chrome-flags="--headless=new --no-sandbox" --only-categories=performance,accessibility,best-practices,seo --output=json --output-path=<thu-muc-tam>/mobile.json --quiet
# Desktop: thêm --preset=desktop, output riêng.
```

| Bản / thiết bị | P/A/BP/SEO | LCP | CLS | TBT |
|---|---|---:|---:|---:|
| Baseline đợt 2 mobile |92/100/96/100 |3,168s |0,0094 |57ms |
| Baseline đợt 2 desktop |100/100/96/100 |0,679s |0,0067 |0ms |
| V2 mobile đầu (SVG noise) |80/97/100/100 |3,750s |0 |240ms |
| V2 mobile trước chốt badge |86/100/100/100 |3,674s |0,01517 |144ms |
| **V2 mobile cuối** |**82/100/100/100** |**3,667s** |**0** |**141ms** |
| **V2 desktop cuối** |**100/100/100/100** |**0,748s** |**0,00167** |**0ms** |

Đã sửa nền chip marquee để audit contrast không so chữ sáng ngoài vùng clip với canvas sáng; A lên 100. Thay SVG noise bằng CSS 3%; H1 giữ opacity 1, chỉ translate; will-change chỉ khi running; auto motion chỉ sau 2 frame/viewport. Các lượt chẩn đoán mobile: CSS noise 79/LCP 3,767/TBT 236; H1 luôn đọc 84/3,682/100; defer theo viewport 84/3,651/87; sau first paint 86/3,655/98. Chẩn đoán reduced-motion 88/3,598/71 dùng cùng font, **không dùng làm điểm nghiệm thu**. Lượt chốt trước 7 thẻ 87/3,653/85. Điểm dao động 82–87 sau tối ưu, không chọn lượt tốt nhất làm kết luận.

| Ngân sách | Trước / sau | Đánh giá |
|---|---|---|
| JS / Lighthouse tổng Script.transferSize (body nén + overhead) |149.775→**147.842 byte**, chênh −1.933 byte |Đạt tăng≤20 KiB |
| JS / Chrome cache sạch encodedBodySize |**144.596 byte** |Body nén; khác transfer gồm overhead |
| DOM / document.querySelectorAll('*') |V2 **979** |Đạt≤1.200; gồm panel ẩn/mock |
| DOM / Lighthouse insight |939 |Cách đếm khác toàn document, không nhầm với CDP |
| Display WOFF2 unique |11.536+33.640+36.560=**81.736** |Đạt 80 KiB=81.920; 6 face descriptor dùng 3 file |
| Marketing nhìn thấy |**446** mặc định; 445–447 theo trạng thái |Đạt 350–450; tính câu hỏi FAQ, loại câu trả lời đóng/mock/chip/control |
| LCP mobile <2,5s / P mobile≥90 |**Chưa đạt** |Dừng báo trước đổi font |
| CLS<0,1 / P desktop≥95 |Đạt |Không suy ra đủ toàn bộ hiệu năng |

Audit Performance còn hạn chế LCP/FCP/Speed Index/TBT; cơ hội render-blocking CSS và unused JavaScript 26 KiB. Bản trước badge báo render-blocking ước tính 1.160ms (mô phỏng), CSS 7.855 + 6.029 byte. Network tree có chuỗi HTML→CSS→Fraunces Latin-ext 33.640 byte; H1 span “Trên một nền tảng.” là LCP. **Suy luận**: ưu tiên tải CSS/font có thể giúp LCP; chưa tách thực nghiệm để chứng minh Fraunces là nguyên nhân chính hoặc duy nhất. Giữ nguyên font/weight/subsets/stack/app; chưa thêm preload latin-ext hoặc experimental inlineCss. Phương án cần review tiếp: ưu tiên tải sớm file đang dùng, rồi đo lại; không hứa trước đạt 90/<2,5s. Không đổi font khi chưa báo số đo và được Tài quyết.

### Accessibility, motion và responsive runtime

| Kiểm trên Chrome | Kết quả |
|---|---|
| AX landmarks |1 banner, 1 main, 1 contentinfo; nav header/footer; 1 header thật |
| Glyph tiếng Việt |700/800 probe 125 custom glyph mỗi weight; H1/H2 hiện tại chỉ Fraunces custom, không system fallback |
| Hero |Space pause; phrase đứng sau 4,2s; resume tới Đề xuất/Thắng; Enter đổi chip demo |
| Marquee |Nút icon 44px, pause/continue; clone aria-hidden; hover/focus pause CSS |
| Slider |ArrowRight/Home/End cập nhật value+aria-valuetext; chuột kéo 20→80%; touch CDP 30→70%; nút 0/100 RTL đạt |
| RoleTabs |Arrow/Home/End,aria-selected/panel hidden; focus 6,2s không đổi; pause/resume RTL; 6s progress; panel nằm trong thứ tự Tab |
| IndustrySwitcher |Arrow/Home/End,ngành/thuộc tính đổi; Enter chấm Logistics; prev/next RTL |
| Security/FAQ |Enter đổi góc nhìn; FAQ Enter/Space mở/đóng |
| Mobile disclosure |Enter mở; Tab vào link; Shift+Tab về trigger; Esc đóng/trả focus; không modal |
| SkipLink/anchor |SkipLink đưa focus main; #cach-hoat-dong H2 top 151,58>header bottom 72; section top 87,58 |
| Header sentinel |Cuộn qua hero có data-scrolled/nền surface; đúng 72/64px |
| Tab ẩn / ngoài viewport |Tab thật visibility hidden→hero không data-running; cuộn tới FAQ, hero/marquee/role đều dừng |
| Reduced motion |0 data-running/0 data-motion-ready/**0 animation**; clone none; chip wrap; không tự đổi tab |
| 320/375/768/1024/1440 |Không tràn ngang; scroll-padding 80/88px; header 64/72px |
| Native zoom 200% |Viewport 629×351,DPR 2; không tràn ngang; header static, padding 16px |
| Native zoom 400% |Viewport 314×175,DPR 4; không tràn ngang; header static, padding 16px |
| Console/runtime |Không error/warning/exception trong acceptance |

Chỉ kiểm touch mô phỏng CDP, chưa thiết bị vật lý. Không screen reader thực tế. Không tuyên bố đạt toàn bộ WCAG 2.2 AA từ Lighthouse 100.

Tương phản sRGB, compositing biên sáng nhất bảo thủ: white/navy 18,72; muted/navy 12,61; white/glow 7,69; muted/glow 5,18; amber/glow 3,58 (**chỉ H1 lớn**); focus/glow 5,41; border mới #A3B1C3/glow 3,54; white/primary 5,17; white/shimmer max 4,66. Badge private nền navy riêng nên muted 12,61/amber icon 8,72; không chữ nhỏ amber trên trắng. Xem [design system](../design/ui-ux/design-system-v1.md) cho token/lớp/source và [wireframe](../design/ui-ux/landing-wireframe-v2.md) cho cây heading thực tế.

### Ảnh tại điểm dừng 2

- [Toàn trang desktop 1440](assets/frontend-landing-dot3-2026-10-13/landing-v2-desktop.png), [mobile 375](assets/frontend-landing-dot3-2026-10-13/landing-v2-mobile.png), [mobile reduced](assets/frontend-landing-dot3-2026-10-13/landing-v2-mobile-reduced.png).
- Chuỗi hero thật: [Đủ điều kiện](assets/frontend-landing-dot3-2026-10-13/landing-v2-hero-qualified.png) → [Đề xuất](assets/frontend-landing-dot3-2026-10-13/landing-v2-hero-proposal.png) → [Thắng](assets/frontend-landing-dot3-2026-10-13/landing-v2-hero-won.png). Không quay video; dùng chuỗi ảnh theo prompt.
- [Zoom 200%](assets/frontend-landing-dot3-2026-10-13/landing-v2-zoom-200.png), [zoom 400%](assets/frontend-landing-dot3-2026-10-13/landing-v2-zoom-400.png).

| Khối | Desktop | Mobile |
|---|---|---|
| k1 | [Ảnh](assets/frontend-landing-dot3-2026-10-13/landing-v2-k1-desktop.png) | [Ảnh](assets/frontend-landing-dot3-2026-10-13/landing-v2-k1-mobile.png) |
| k2 | [Ảnh](assets/frontend-landing-dot3-2026-10-13/landing-v2-k2-desktop.png) | [Ảnh](assets/frontend-landing-dot3-2026-10-13/landing-v2-k2-mobile.png) |
| k3 | [Ảnh](assets/frontend-landing-dot3-2026-10-13/landing-v2-k3-desktop.png) | [Ảnh](assets/frontend-landing-dot3-2026-10-13/landing-v2-k3-mobile.png) |
| k4 | [Ảnh](assets/frontend-landing-dot3-2026-10-13/landing-v2-k4-desktop.png) | [Ảnh](assets/frontend-landing-dot3-2026-10-13/landing-v2-k4-mobile.png) |
| k5 | [Ảnh](assets/frontend-landing-dot3-2026-10-13/landing-v2-k5-desktop.png) | [Ảnh](assets/frontend-landing-dot3-2026-10-13/landing-v2-k5-mobile.png) |
| k6 | [Ảnh](assets/frontend-landing-dot3-2026-10-13/landing-v2-k6-desktop.png) | [Ảnh](assets/frontend-landing-dot3-2026-10-13/landing-v2-k6-mobile.png) |
| k7-security | [Ảnh](assets/frontend-landing-dot3-2026-10-13/landing-v2-k7-security-desktop.png) | [Ảnh](assets/frontend-landing-dot3-2026-10-13/landing-v2-k7-security-mobile.png) |
| k7-faq | [Ảnh](assets/frontend-landing-dot3-2026-10-13/landing-v2-k7-faq-desktop.png) | [Ảnh](assets/frontend-landing-dot3-2026-10-13/landing-v2-k7-faq-mobile.png) |
| k7-cta | [Ảnh](assets/frontend-landing-dot3-2026-10-13/landing-v2-k7-cta-desktop.png) | [Ảnh](assets/frontend-landing-dot3-2026-10-13/landing-v2-k7-cta-mobile.png) |

### Commit cục bộ trong lượt tiếp tục

| Hash | Message |
|---|---|
| e5c619a | docs(content): refine landing copy for business readers |
| a37d6fe | feat(frontend): establish the selected landing visual and motion system |
| 8e1ecaa | feat(frontend): bring the landing hero and industry strip to life |
| cfe7dc3 | feat(frontend): add an accessible before-and-after comparison |
| d08cd0f | feat(frontend): add keyboard-operated role demonstrations |
| 51fc36d | feat(frontend): illustrate the customer journey without scroll capture |
| 80eb34c | feat(frontend): present CRM capabilities in an animated bento grid |
| 41a1a92 | feat(frontend): add industry samples with keyboard navigation |
| c5474a9 | feat(frontend): clarify data access and shared FAQ content |
| e77360f | feat(frontend): finalize landing metadata and static brand icons |
| b8b0c35 | perf(frontend): defer decorative motion until the first painted frame |
| 044bc5b | fix(frontend): keep the static permission demo readable |
| 3cd382c | feat(frontend): refresh the static preview image with the live hero |
| 670aa93 | fix(frontend): make industry pagination dots keyboard accessible |
| 3402e2f | fix(frontend): keep demo pipeline columns populated throughout the cycle |
| ee6c002 | fix(frontend): include the complete demo pipeline in the static preview |
| e3782b9 | fix(frontend): strengthen dark control borders against the brightest glow |
| 5aa9046 | fix(frontend): keep the private-note badge readable over demo layers |
| 70a68f0 | fix(frontend): restrict bento hover animation to transform |
| 670124f | fix(frontend): capture the readable private-note badge in the preview |
| 242dd87 | docs(frontend): document the selected landing system and content maintenance |

Commit evidence/ảnh tiếp theo bổ sung bảng kiểm và ảnh review; README/wireframe/design system đã ở 242dd87. Không trailer Co-authored-by; không push.

### Phần chưa đạt / chưa kiểm và điểm dừng

- **Performance mobile/LCP chưa đạt**; font giữ B. Dừng báo số đo, chưa đổi font/weight/subsets hoặc ưu tiên tải file ngoài cấu hình đã duyệt.
- **Chờ Tài review điểm dừng 2**: ảnh landing, chuyển động và bảng Lighthouse. Chưa Phase 5 micro-interaction login/app; chưa hồi quy backend thật 5 role/refresh/mất mạng/logout đợt 3. Foundation 66 test đạt không thay thế hồi quy backend.
- Chưa Rich Results Test/Schema Markup Validator qua URL công khai, screen reader và thiết bị touch vật lý. Domain production chưa cấp; SITE_URL local chỉ để kiểm metadata.
- Dependency advisory kế thừa chưa phân tích. Không đổi package/stack ngoài phạm vi.

Các server/profile riêng của phép kiểm đã dừng cuối lượt. Không can thiệp process/backend/DB khác. Bước sau cần xử lý cổng hiệu năng và duyệt landing trước khi sang Phase 5.
