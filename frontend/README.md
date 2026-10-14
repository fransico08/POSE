# POSE CRM Frontend

Frontend gồm trang giới thiệu công khai và giao diện CRM nội bộ đa ngành cho Quản trị hệ thống, Quản lý, Nhân viên kinh doanh, Chăm sóc khách hàng và Nhân viên dữ liệu.

## Công nghệ và phạm vi

Next.js App Router, TypeScript strict, Tailwind CSS, TanStack Query và Heroicons. Font chính **Be Vietnam Pro**, được Tài chốt sau kiểm glyph next/font. Recharts chưa cài; biểu đồ thuộc giai đoạn sau.

Đã triển khai foundation đợt 1: đăng nhập, đăng xuất, session BFF, routing, app shell, Tổng quan theo permission và checking/network/empty/403/404/error. Chỉ Tổng quan là route nghiệp vụ đã triển khai. Các lối tắt tương lai là thẻ tĩnh “Sẽ có ở giai đoạn sau”, không có KPI giả.

Trang `/` dùng landing v2 Concept B hướng doanh nghiệp: Fraunces tĩnh 700/800 cho tiêu đề, Be Vietnam Pro cho body, 7 khối và mock UI HTML/CSS có nhãn minh họa. FAQ 5 câu, metadata/JSON-LD, robots/sitemap và favicon tĩnh. CTA Đăng nhập; tài khoản do quản trị viên tổ chức cấp. Không có form đăng ký hoặc thu dữ liệu.

Phạm vi TLCN các đợt sau giữ nguyên: Offering, Sales Assignment, Customer/Lead, Opportunity, Interaction, CRM Task, Customer 360, Dashboard và import/data quality khi có API. Không tạo storefront, cart, checkout, payment, voucher hoặc inventory.

## Chạy frontend

Môi trường đã kiểm: Node **24.13.0**, npm **11.6.2**. Từ thư mục `frontend/`:

```powershell
npm ci
$env:BACKEND_URL = 'http://127.0.0.1:8080'
npm run dev
```

Mở `http://127.0.0.1:3000`; `/` là trang giới thiệu, không gọi API và không cần backend. `/app` khi chưa có phiên vẫn chuyển sang đăng nhập. Backend phải chạy riêng tại địa chỉ BACKEND_URL để đăng nhập/dùng app, theo [backend/README.md](../backend/README.md), có DB cô lập 127.0.0.1:54329 và kiểm SHOW data_directory trước thao tác. Không dùng cổng 5432. Khi chưa có backend, login không xác thực được và bootstrap có trạng thái lỗi kết nối.

`.env.example` có BACKEND_URL và SITE_URL; cả hai chỉ server đọc, không dùng NEXT_PUBLIC. SITE_URL là origin tuyệt đối của trang công khai. Khi NODE_ENV=production mà thiếu SITE_URL, build ghi cảnh báo rõ và vẫn chạy local; fallback `http://127.0.0.1:3000`. Đặt domain thật **trước khi build** để canonical, Open Graph, robots và sitemap dùng đúng URL. Có thể đặt trong môi trường process hoặc file `.env.local` được ignore. Không đưa credentials demo vào frontend hay file môi trường. Next.js cần mạng để tải font lúc build lần đầu; font được self-host trong bản build.

```powershell
npm run lint
npm run typecheck
npm run test
npm run build
npm run start
```

Dừng dev trước khi build/start. Nếu vừa xóa route và typecheck báo route cũ trong `.next/types`, chạy build để Next tạo lại type rồi typecheck; không chỉnh file sinh tự động.

## Cấu trúc và session

| Thư mục | Nội dung |
|---|---|
| `src/app/` | Root layout, login, nhóm app, trạng thái và Route Handlers |
| `src/components/ui/` | Button/field/alert/badge/dialog/skeleton/empty/error |
| `src/components/layout/` | Sidebar, topbar, breadcrumbs, tenant, user menu, drawer |
| `src/components/auth/`, `session/`, `overview/` | Form login, provider/gate và Tổng quan |
| `src/components/landing/`, `src/content/landing.ts` | Landing v2, client islands nhỏ và nguồn chữ/FAQ có kiểu dùng chung |
| `src/components/landing/mock/` | Mock UI kit HTML/CSS: pipeline, DealCard, Customer 360, review, handover, tasks |
| `src/lib/motion/` | Một IntersectionObserver dùng chung; hook reduced motion/page visibility/auto motion |
| `src/lib/site.ts`, `src/lib/landing-seo.ts` | SITE_URL server, graph JSON-LD và escape an toàn |
| `src/config/` | Menu đầy đủ, permission any-of và nhãn role tiếng Việt |
| `src/lib/api/` | Fetch wrapper, refresh single-flight/Web Locks và cleanup |
| `src/lib/server/` | Backend adapter, CSRF, cookie và refresh dedupe |
| `src/lib/session/` | Public types và kiểm returnTo |
| `src/styles/` | Token, Tailwind theme, component/layout/page CSS |
| `src/proxy.ts` | Chuyển hướng sớm theo cờ hiện diện phiên, theo Next.js 16 |
| `tests/` | 104 ca Vitest/RTL: 66 ca foundation và 38 ca landing/motion/SEO/disclosure |

Trình duyệt chỉ gọi `/api/session/*` và `/api/backend/*`. Access/refresh token chỉ có trong HttpOnly cookies và server; không trả token JSON, không dùng localStorage/sessionStorage, không cấu hình CORS backend.

| Cookie | Path | Thuộc tính |
|---|---|---|
| `pose_at` | `/api` | HttpOnly, SameSite Strict, lifetime expiresIn |
| `pose_rt` | `/api/session` | HttpOnly, SameSite Strict, tối đa 7 ngày |
| `pose_session_present` | `/` | Cờ hằng 1, HttpOnly, SameSite Strict, tối đa 7 ngày |

Cả ba Secure ở production. Cookie cờ chỉ hỗ trợ UX; backend là nguồn kiểm quyền. me gọi auth/me và organization để lấy mã tenant. Mutation kiểm Origin/Sec-Fetch-Site; proxy chỉ chuyển tiếp endpoint/method/header cần thiết và không tự refresh. Client refresh một lần trong tab, dùng Web Locks giữa tab; server dedupe theo hash refresh token trong cùng process khoảng 10 giây, giải phóng kết quả lỗi kết nối để lần Thử lại chủ động có thể phục hồi. Không tự retry POST refresh. Logout/hết phiên xóa state và toàn bộ query cache; lỗi xác thực phiên mới chuyển login với thông báo hết phiên. Lỗi mạng/timeout/5xx khi kiểm me hoặc refresh giữ cookie, state/cache và hiện “Không thể kết nối” + Thử lại. `returnTo` chỉ chấp nhận đường dẫn app nội bộ.

Login 400/401 dùng câu lỗi tài khoản chung. Lỗi backend/kết nối, timeout và 403 Origin dùng “Không thể kết nối. Vui lòng thử lại.”; giữ mã tổ chức/email, xóa mật khẩu. BFF dùng NETWORK_ERROR/503 cho lỗi backend/kết nối, không lộ chi tiết backend.

Providers/TanStack Query chỉ bọc layout `/app` và trang `/dang-nhap`; trang công khai không nạp bundle Query/cache phiên. Component Providers và logic session/BFF/proxy giữ nguyên; cleanup vẫn hủy query và xóa cache. Đây là thay đổi Tài duyệt sau đo mobile 89 → 92 điểm.

## Duy trì nội dung và kiểm SEO

Nguồn chữ ở `src/content/landing.ts`, truy vết trong [landing-content-v2.md](../docs/design/ui-ux/landing-content-v2.md). FAQ HTML và FAQPage JSON-LD dùng cùng mảng; không viết hai bản trả lời. Mỗi lần có module mới phải rà lại chữ marketing, **heroCopy.description, heroCopy.caption và contentUpdatedAt** cùng bảng nguồn để phản ánh đúng nghiệp vụ/tiến độ. Hai trường scopeNote/productCaption của v1 đã bỏ theo duyệt v2. `sitemap.xml` lấy ngày hằng, không lấy thời điểm build. Ngân sách 350–450 từ marketing loại mock UI, chip, control, câu trả lời FAQ đóng và panel tab ẩn; vẫn tính câu hỏi FAQ; bản hiện tại 446 từ mặc định, 445–447 theo trạng thái.

`page.tsx` là Server Component tĩnh. CSS riêng `landing-v2.css`, token `--landing-*` scope `.landing-page`; không đổi token/font app. Mock UI có dữ liệu mẫu trung tính, không phải module nghiệp vụ hoạt động; aria-hidden và nhãn minh họa. HeroSection là Server Component: H1, eyebrow, phụ đề và CTA có sẵn trong HTML, H1 không animation/transform. HeroMotion chỉ dựng figure động; RotatingPhrase riêng; HeroMotionScope chia sẻ pause/focus qua context và nhận chữ server bằng children. Các client islands khác: marquee, range so sánh, role tabs, journey fallback, industry tabs, security preview, header/disclosure. Không package animation mới. Thẻ vào viewport dùng shared observer, chỉ transform/opacity; hero/marquee/tabs tự chạy có nút dừng, dừng khi ngoài viewport hoặc tab ẩn. Reduced motion hiện trạng thái tĩnh, marquee xuống dòng, không tự đổi tab. Mobile journey là danh sách tĩnh.

Display font giữ `Fraunces({ weight: ["700", "800"], subsets: ["vietnamese", "latin", "latin-ext"], display: "swap" })`, không weight variable/axes. Ba file dùng chung hai descriptor, cache sạch **81.736/81.920 byte**. Không bỏ 700 hoặc đổi font nếu chưa có duyệt mới. Icon chữ P: `app/icon.svg`, `app/apple-icon.png`, `public/favicon.ico`. OG tĩnh `public/images/pose-crm-overview-og.webp` là ảnh chụp hero thật 1200×630, 49.770 byte; khi chụp lại chỉ dùng dữ liệu mẫu và loại thông tin tài khoản/tổ chức.

Sau production build/start, kiểm HTML và metadata routes:

```powershell
curl.exe --fail --silent http://127.0.0.1:3000/
curl.exe --fail --silent http://127.0.0.1:3000/robots.txt
curl.exe --fail --silent http://127.0.0.1:3000/sitemap.xml
curl.exe --silent --head http://127.0.0.1:3000/api/session/me
```

Build phải hiển thị `○ /`. HTML có một H1, canonical, metadata và một script JSON-LD gồm WebSite/SoftwareApplication/FAQPage. Trang công khai index/follow; app/login noindex/nofollow; `/api/:path*` có X-Robots-Tag noindex/nofollow qua Next config. Disallow không thay cho noindex và không là kiểm soát quyền truy cập. Next chuẩn hóa canonical root không có dấu `/` cuối; hai dạng cùng một URL gốc.

Chạy Lighthouse tạm bằng `npx --yes lighthouse@13.5.0 <URL> --output=json --output-path=<thư mục tạm> --chrome-flags="--headless=new" --quiet`; thêm `--preset=desktop` cho desktop. Không thêm dependency hoặc commit report đầy đủ. Điểm đo không thay cho kiểm bàn phím, focus, tương phản, reflow và screen reader.

## Thiết kế và nghiệm thu

- [Design system v1](../docs/design/ui-ux/design-system-v1.md): font đã duyệt, token và tương phản.
- [Wireframe/user flow](../docs/design/ui-ux/frontend-foundation-wireframe.md): route, landmark và khung responsive.
- [Evidence đợt 1](../docs/testing/frontend-foundation-dot1-2026-10-12.md): versions, commits, kiểm backend thật, ảnh và giới hạn.
- [Sửa review 13/10](../docs/testing/frontend-foundation-review-2026-10-13.md): lockfile, refresh/login khi mất kết nối, clean install và kiểm backend thật.
- [Nội dung landing](../docs/design/ui-ux/landing-content-v1.md) và [wireframe](../docs/design/ui-ux/landing-wireframe-v1.md): chữ đã duyệt, nguồn và heading/landmark.
- [Evidence đợt 2](../docs/testing/frontend-landing-dot2-2026-10-13.md): clean install, 78 test, Lighthouse, SEO, ảnh và nghiệm thu bàn phím/zoom.

Sau sửa review 13/10, npm ci, lint, typecheck, 66 test và build đạt trên bản sao sạch không có node_modules/.next. Kiểm backend thật: login khi backend tắt hiện lỗi kết nối; app thiếu access cookie khi backend tắt giữ phiên và Thử lại phục hồi sau khi backend chạy lại. Lượt 12/10 đã đăng nhập thật 5 role EDU, kiểm lỗi chung, refresh đồng thời, logout, bàn phím, accessibility tree và reflow 320px; đã kiểm viewport tương đương và zoom native Chrome 200/400%. Screen reader còn chưa kiểm; không tuyên bố đạt toàn bộ WCAG 2.2 AA. Route chẩn đoán font đã gỡ; ảnh bốn weight lưu trong evidence.

## Quy tắc và skill

Đợt 3: Phase2–3 đã dựng Concept B và toàn bộ chữ/hero/điều khiển được duyệt. Route `/thu-nghiem/*` và font A đã gỡ; production trả404. Đang **dừng ở điểm dừng2**, chờ review landing và quyết định xử lý cổng hiệu năng mobile trước Phase 5. Lighthouse và kiểm runtime trong [evidence](../docs/testing/frontend-landing-dot3-2026-10-14.md); không coi landing đã nghiệm thu hiệu năng. Micro-interaction login/app và hồi quy backend đợt3 chưa làm. Không chạy build đồng thời với dev trong cùng `.next`.

- [Nội dung v2](../docs/design/ui-ux/landing-content-v2.md) và [wireframe v2](../docs/design/ui-ux/landing-wireframe-v2.md): nguồn chữ, tương tác và bố cục đang áp dụng.
- [Evidence đợt3](../docs/testing/frontend-landing-dot3-2026-10-14.md): kiểm bản sao sạch, Lighthouse, font/JS/DOM, ảnh và phần chưa đạt.

Đọc [AGENTS.md](../AGENTS.md), [API contract](../docs/api/api-contract-foundation-v1.md) và [roles/authorization](../docs/analysis/roles-authorization-v1.md). Code theo wireframe được Tài duyệt; UI chỉ dùng permissions để điều hướng, backend kiểm quyền thật. Không hardcode tài khoản/mật khẩu hay kiểm tên role để cấp quyền.

Skill repo: [UI UX Pro Max](../.agents/skills/ui-ux-pro-max/SKILL.md) cho tra cứu thiết kế; [Frontend UI Engineering](../.agents/skills/frontend-ui-engineering/SKILL.md) và [checklist](../.agents/skills/frontend-ui-engineering/references/accessibility-checklist.md) cho UI; [Browser Testing](../.agents/skills/browser-testing-with-devtools/SKILL.md) cho kiểm runtime khi có công cụ phù hợp. Không cần cài CLI toàn máy; không tự cài MCP hoặc package ngoài phạm vi đã duyệt.

## Review điểm dừng 2 — hiệu năng và bố cục

Tài chưa duyệt điểm 2, chưa Phase 5. Đã áp dụng A1–A5/B6–B13, giữ Fraunces 700/800; latin-ext được preload cùng vietnamese/latin, ba file vẫn 81.736 byte. Hero ba câu không ngắt, mock desktop 3 cột/mobile 1 cột với thẻ nổi dưới khung; reduced motion không icon phát hoặc chữ “Tĩnh”. K2 trái/chưa có, phải/với POSE; native range là phần trăm bên trái, aria-valuetext mô tả cả hai. K3 mỗi vai trò 5 dòng; Customer 360 bento 4 hoạt động, 6 chip ngành; K6 có thẻ mẫu bên trái và thuộc tính bên phải.

`globals.css` chỉ Tailwind/theme/tokens/base/SkipLink dùng chung. `components.css` và `pages.css` import ở `(app)/app/layout.tsx` và `dang-nhap/page.tsx`. Button/style trạng thái tách `buttons.css`/`status.css`, được app và 404/error dùng; trang `/` không kéo CSS login/sidebar/lối tắt. Không đổi form/session/BFF/proxy hoặc font/token app.

`experimental.inlineCss: true` đang giữ **cho phép thử cục bộ đã duyệt**, chưa coi là quyết định triển khai. Flag áp dụng toàn production, không từng route; CSS vào HTML và lặp trong RSC, stylesheet không cache độc lập như trước. Năm lượt mobile cho mỗi bước A1–A5 và desktop ghi trong [evidence](../docs/testing/frontend-landing-dot3-2026-10-14.md). Sau A5 vẫn chưa đạt cổng Performance/LCP; dừng, không tự thử font-display optional hoặc đổi H2 sang Be Vietnam Pro. Chờ Tài chọn phương án và review ảnh trước Phase 5.
