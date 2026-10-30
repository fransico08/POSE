# POSE CRM Frontend

Frontend gồm trang giới thiệu công khai và giao diện CRM nội bộ đa ngành cho Quản trị hệ thống, Quản lý, Nhân viên kinh doanh, Chăm sóc khách hàng và Nhân viên dữ liệu.

## Công nghệ và phạm vi

Next.js App Router **16.4.3**, `eslint-config-next` **16.4.3**, TypeScript strict, Tailwind CSS, TanStack Query và Heroicons. Hai package Next được pin đúng bản patch sau review bảo mật 25/10; không nâng package độc lập khác. Font chính **Be Vietnam Pro**, được Tài chốt sau kiểm glyph next/font. Recharts chưa cài; biểu đồ thuộc giai đoạn sau.

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

`.env.example` có BACKEND_URL và SITE_URL; cả hai chỉ server đọc, không dùng NEXT_PUBLIC. SITE_URL là origin tuyệt đối của trang công khai. Khi NODE_ENV=production mà thiếu SITE_URL, build ghi cảnh báo rõ và vẫn chạy local; fallback `http://127.0.0.1:3000`. Đặt domain thật **trước khi build** để canonical, Open Graph, robots và sitemap dùng đúng URL. Có thể đặt trong môi trường process hoặc file `.env.local` được ignore. Không đưa credentials demo vào frontend hay file môi trường. Be Vietnam Pro của app vẫn dùng next/font/google và cần mạng khi build lần đầu. Fraunces của landing được lưu trong repo, không tải từ Google lúc build hoặc runtime.

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
| `src/lib/motion/` | Một IntersectionObserver dùng chung, scheduler sau idle, reduced motion/page visibility và chuyển động giới hạn thời gian |
| `src/lib/site.ts`, `src/lib/landing-seo.ts` | SITE_URL server, graph JSON-LD và escape an toàn |
| `src/config/` | Menu đầy đủ, permission any-of và nhãn role tiếng Việt |
| `src/lib/api/` | Fetch wrapper, refresh single-flight/Web Locks và cleanup |
| `src/lib/server/` | Backend adapter, CSRF, cookie và refresh dedupe |
| `src/lib/session/` | Public types và kiểm returnTo |
| `src/styles/` | Token, Tailwind theme, component/layout/page CSS |
| `src/proxy.ts` | Chuyển hướng sớm theo cờ hiện diện phiên, theo Next.js 16 |
| `tests/` | 124 ca Vitest/RTL: 66 ca foundation, 50 ca landing/motion/SEO/disclosure/micro-interaction, 3 ca sidebar CSS index/bàn phím và 5 ca khóa font/preload |

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

Nguồn chữ ở `src/content/landing.ts`, truy vết trong [landing-content-v2.md](../docs/design/ui-ux/landing-content-v2.md). FAQ HTML và FAQPage JSON-LD dùng cùng mảng; không viết hai bản trả lời. Mỗi lần có module mới phải rà lại chữ marketing, **heroCopy.description, heroCopy.caption và contentUpdatedAt** cùng bảng nguồn để phản ánh đúng nghiệp vụ/tiến độ. Hai trường scopeNote/productCaption của v1 đã bỏ theo duyệt v2. `sitemap.xml` lấy ngày hằng, không lấy thời điểm build. Ngân sách 350–450 từ marketing loại mock UI, chip, control, câu trả lời FAQ đóng và panel tab ẩn; vẫn tính câu hỏi FAQ; bản hiện tại 448 từ mặc định, 447–449 theo trạng thái.

`page.tsx` là Server Component tĩnh. CSS riêng `landing-v2.css`, token `--landing-*` scope `.landing-page`; không đổi token/font app. Mock UI có dữ liệu mẫu trung tính, không phải module nghiệp vụ hoạt động; aria-hidden và nhãn minh họa. HeroSection là Server Component: H1, eyebrow, phụ đề và CTA có sẵn trong HTML, H1 không animation/transform. HeroMotion chỉ dựng figure động; RotatingPhrase riêng; HeroMotionScope chia sẻ pause/focus qua context và nhận chữ server bằng children. Các client islands khác: marquee, range so sánh, role tabs, journey fallback, industry tabs, security preview, header/disclosure. Không package animation mới. Thẻ vào viewport dùng shared observer, chỉ transform/opacity; hero/marquee/tabs tự chạy có nút dừng, dừng khi ngoài viewport hoặc tab ẩn. Reduced motion hiện trạng thái tĩnh, marquee xuống dòng, không tự đổi tab. Mobile journey là danh sách tĩnh.

Display font **Fraunces 700/800 tự host** tại `public/fonts/fraunces/`, không import Fraunces từ next/font/google. `landing-fraunces.css` giữ đúng sáu @font-face (700/800 × vietnamese/latin-ext/latin), unicode-range nguyên bản và font-display:swap; thêm Fraunces Fallback/Times New Roman với ascent 84.71%, descent 22.09%, line-gap 0%, size-adjust 115.45%. Biến `--landing-display-font` trỏ `"Fraunces", "Fraunces Fallback", serif`. Ba file dùng chung hai descriptor, cache sạch **81.736/81.920 byte**; H1 800/H2 700 giữ nguyên. Nguồn, phiên bản, OFL và SHA-256 ở [README font](public/fonts/fraunces/README.md). Không tải lại/chuyển subset hoặc bỏ 700 nếu chưa có duyệt mới.

Chỉ HomePage route `/` gọi react-dom preload cho ba file, as=font/type=font/woff2/crossOrigin=anonymous; root layout, login và app không preload Fraunces. Test `fraunces-assets.test.ts` khóa số file, tổng byte, checksum, sáu unicode-range và metric fallback; test landing khóa ba preload. Khi nâng Next, vẫn kiểm font thực tế qua CSS.getPlatformFontsForNode và network production, không chỉ tên CSS. Giữ nguyên `D:\POSE\handoff` do Tài quản lý.

Icon chữ P: `app/icon.svg`, `app/apple-icon.png`, `public/favicon.ico`. OG tĩnh `public/images/pose-crm-overview-og.webp` là ảnh chụp hero thật 1200×630, 49.770 byte; khi chụp lại chỉ dùng dữ liệu mẫu và loại thông tin tài khoản/tổ chức.

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

Đợt 3 đã hoàn tất Phase 2–6 và kiểm lại sau bản vá Next 16.4.3: chuỗi mobile 7 lượt ổn định ngày 30/10 đạt Performance trung vị **89**, TBT **36,5 ms**, A/BP/SEO **100**, CLS **0,0172**; desktop **100**. Đạt cổng chính để đóng đợt theo quyết định cuối của Tài. Route `/thu-nghiem/*` và font A đã gỡ; production trả404. Login/app đã thêm micro-interaction và kiểm hồi quy backend thật đủ 5 vai trò ở các lượt trước. [Evidence tự host mới nhất](../docs/testing/frontend-fraunces-self-hosted-2026-10-30.md) ghi toàn bộ chuỗi, checksum, glyph và audit; [evidence 14/10](../docs/testing/frontend-landing-dot3-2026-10-14.md) giữ lịch sử Phase 5–6. Không chạy build đồng thời với dev trong cùng `.next`.

- [Nội dung v2](../docs/design/ui-ux/landing-content-v2.md) và [wireframe v2](../docs/design/ui-ux/landing-wireframe-v2.md): nguồn chữ, tương tác và bố cục đang áp dụng.
- [Evidence đợt3](../docs/testing/frontend-landing-dot3-2026-10-14.md): kiểm bản sao sạch, Lighthouse, font/JS/DOM, ảnh và phần chưa đạt.

Đọc [AGENTS.md](../AGENTS.md), [API contract](../docs/api/api-contract-foundation-v1.md) và [roles/authorization](../docs/analysis/roles-authorization-v1.md). Code theo wireframe được Tài duyệt; UI chỉ dùng permissions để điều hướng, backend kiểm quyền thật. Không hardcode tài khoản/mật khẩu hay kiểm tên role để cấp quyền.

Skill repo: [UI UX Pro Max](../.agents/skills/ui-ux-pro-max/SKILL.md) cho tra cứu thiết kế; [Frontend UI Engineering](../.agents/skills/frontend-ui-engineering/SKILL.md) và [checklist](../.agents/skills/frontend-ui-engineering/references/accessibility-checklist.md) cho UI; [Browser Testing](../.agents/skills/browser-testing-with-devtools/SKILL.md) cho kiểm runtime khi có công cụ phù hợp. Không cần cài CLI toàn máy; không tự cài MCP hoặc package ngoài phạm vi đã duyệt.

## Review điểm dừng 2 — hiệu năng và bố cục

Tài duyệt điểm 2 có điều kiện: cổng mới đạt và ba sửa K1 desktop/mobile, K6 hoàn tất mới sang Phase 5. Giữ Fraunces 700/800; latin-ext preload cùng vietnamese/latin, ba file vẫn 81.736 byte. Hero ba câu không ngắt; desktop khoảng 895 px, Customer 360 chồng đáy 31,6%, toast/badge lấn cạnh phải 25,2%, không che thẻ. Mobile chỉ pipeline hai thẻ/toast; Customer 360, badge và picker ẩn bằng display:none, không Tab stop thừa. Reduced motion không icon phát/chữ “Tĩnh”. K2 trái/chưa có, phải/với POSE; K3 mỗi vai trò 5 dòng; K5 Customer 360 bốn hoạt động/sáu chip ngành. K6 thẻ sản phẩm trái hiển thị giá trị mẫu; phải là tên/kiểu thuộc tính danh mục và câu giải thích. Nguồn BR:32/DEC-04 ở content v2, contentUpdatedAt 14/10.

`globals.css` chỉ Tailwind/theme/tokens/base/SkipLink dùng chung. `components.css` và `pages.css` import ở `(app)/app/layout.tsx` và `dang-nhap/page.tsx`. Button/style trạng thái tách `buttons.css`/`status.css`, được app và 404/error dùng; trang `/` không kéo CSS login/sidebar/lối tắt. Không đổi form/session/BFF/proxy hoặc font/token app.

**Đã gỡ experimental.inlineCss** theo quyết định 14/10, giữ A1–A3. LCP A5 không cải thiện, document transfer tăng và cờ thử nghiệm áp dụng toàn app. Root quay về hai stylesheet cache độc lập, không CSS app. Không thử optional hoặc đổi H2 sang Be Vietnam Pro.

Cổng mới: Performance mobile trung vị ≥88, A/BP/SEO 100, CLS <0,1, TBT mobile trung vị <200 ms, desktop ≥95. LCP mô phỏng mobile chỉ theo dõi: H1 web font và chi phí tải khoảng 1,6 Mbps; trace localhost trước review TTFB ~13 ms/render delay ~0,33 s không thay dữ liệu thực. Đo lại với domain triển khai/thiết bị/mạng thật khi có. Hai chuỗi trước hydration có trung vị 85/84 được giữ làm lịch sử. Sau tối ưu hydration, chuỗi đầu 7 lượt có benchmarkIndex chênh 86,9%, nên đo lại toàn bộ 7 lượt: chênh 13,5%, Performance 89 (86–89), TBT 78 (72–175 ms), desktop 100. Đạt cổng chính, không dùng mức ngoại lệ 85/250. [Evidence](../docs/testing/frontend-landing-dot3-2026-10-14.md) giữ mọi lượt.

## Hydration và micro-interaction đợt 3

IndustrySection/IndustryPanel, RoleSection/RoleMock, CompareSection và SecurityCard dựng các panel, mock UI và danh sách thuộc tính trên server. Client chỉ nhận opaque ReactNode cùng id/label cần để xử lý state, APG Tabs, bàn phím, range hoặc góc nhìn. HeroSection giữ toàn bộ chữ chính trên server; HeroMotion/HeroMotionScope không import bộ mock tĩnh vào bundle client. Reveal vẫn dùng shared observer để giới hạn demo khi thấy được và dừng khi tab ẩn. Journey dùng CSS view timeline khi hỗ trợ, fallback hiển thị đường dẫn tĩnh; component nhỏ chỉ đọc reduced-motion.

`scheduleWhenIdle` dùng requestIdleCallback có timeout, fallback setTimeout 250 ms; cleanup hủy hoặc bỏ qua callback muộn. Observer và bộ đếm tự chạy chỉ bắt đầu sau idle và khi khối vào viewport. Reduced-motion và visibilitychange vẫn có hiệu lực. Không thêm dependency hoặc bật inlineCss.

LoginIllustration dùng lại Customer360Card server trong panel trái, không có control và không đổi thứ tự Tab của form. Hiệu ứng và câu lợi ích chạy một chuỗi **4,8 giây hoạt động**, rồi đứng ở trạng thái cuối; ngân sách tạm dừng khi tab ẩn/ngoài viewport. Đây là lựa chọn để giữ panel không có control và tránh tự chạy quá 5 giây theo WCAG 2.2.2. Reduced-motion hiện ngay nội dung tĩnh. CSS module riêng, không tải font/CSS landing vào login.

Skeleton có shimmer cùng giới hạn 4,8 giây, sau đó đứng yên nhưng vẫn giữ status/aria-busy đến khi kiểm phiên xong. Sidebar có chỉ mục chuyển transform/opacity: số `--active-index` được truyền qua CSS custom property, transform nằm trong stylesheet thay vì chuỗi inline; lối tắt nâng 2 px khi hover. Các hiệu ứng này tắt khi reduced-motion. Hiện chỉ Tổng quan có route được triển khai; không tạo route giả để trình diễn chỉ mục. TimeGreeting lấy giờ trình duyệt, cập nhật mỗi phút và khi tab trở lại: 05–11 giờ buổi sáng, 12–17 giờ buổi chiều, các giờ còn lại buổi tối; server ban đầu render “Chào” để tránh lệch hydration.

Kiểm sạch source cuối 5c103c9: npm ci từ archive không .env/node_modules/.next, lint/typecheck, **116/116 test**, build `○ /`. Hồi quy native Chrome với backend thật và DB restore riêng 54329: 5 vai trò đăng nhập/refresh/đăng xuất; mất mạng và backend dừng giữ phiên, Thử lại phục hồi. Backend/cluster của tác vụ đã dừng; credentials và 6 tổ chức/36 người dùng gốc giữ nguyên. Ảnh Tổng quan dùng fixture tên trung tính, không chụp tài khoản thật.

## Bản vá bảo mật 25/10/2026

Nâng đúng Next.js và eslint-config-next 16.4.0 → **16.4.3**, gồm các package `@next/*` đi kèm trong lockfile. Không dùng npm audit fix/--force, không nâng React, jsdom, undici hoặc công cụ khác. Source production **không dùng `next/image`**; mock next/image trong test không phải usage production. `next.config.ts` không bật `images.dangerouslyAllowSVG`; config đã build xác nhận **false**. Icon SVG phục vụ tĩnh, không qua Image Optimization API.

Trên git archive sạch: npm ci, lint, typecheck, **119/119 test (17 file)** và **hai build liên tiếp** đạt, không SecurityFatalError. Audit production từ 1 package critical xuống **0 lỗ hổng**; audit đầy đủ còn **1 package high là undici 7.30.0 qua jsdom (dev-only)**, chưa nâng theo phạm vi review. Smoke production và đăng nhập thật Quản lý EDU qua session BFF/proxy, me/refresh/logout đạt trên backend/DB cô lập, không sửa credentials.

Lighthouse đúng một lượt mỗi chế độ: mobile **83/100/100/100**, TBT **207,5 ms**, LCP **3,879 s**, CLS **0,0172**; desktop **100/100/100/100**. Mobile thấp hơn mốc nghiệm thu 89, benchmarkIndex 1.628 so với chuỗi trước 2.666–3.026. **Chưa đủ bằng chứng giữ cổng hiệu năng hoặc đóng review đợt 3**; không quy chắc cho bản vá và không đo lại chọn điểm đẹp. Xem [evidence bản vá](../docs/testing/frontend-security-patch-2026-10-25.md).

## Khóa Fraunces và nghiệm thu lại ngày 30/10/2026

Đoạn bản vá phía trên là kết quả lịch sử ngày 25/10. Reviewer sau đó xác minh A/B: CSS Fraunces giống nhau nhưng binary Google trả lúc build khác, tổng 81.736 → 114.324 byte, vượt ngân sách. Giữ Next 16.4.3, dùng lại ba WOFF2 đã nghiệm thu từ handoff, có OFL/nguồn/phiên bản/SHA trong repo. Tự host khóa nội dung binary, không phụ thuộc lần tải Google sau khi nâng Next. Không đổi font/weight/token app hoặc logic session.

Giữ kết quả archive sạch source bec3a95 đã làm ngày 25/10: npm ci, lint/typecheck, **124/124 test**, hai build liên tiếp đạt `○ /`. Ngày 30/10 đối chiếu archive với git blob source, khớp toàn bộ; production lại xác nhận 11 nút chữ H1/H2 dùng Fraunces custom, đúng ba request/81.736 byte/hash; root có ba preload, login/app không có. Audit production chạy lại **0**. Audit đầy đủ ngày 25/10 còn một high dev-only undici, không nhận là đã xử lý.

Các chuỗi benchmark biến động ngày 25 và 30-A vẫn được lưu đầy đủ. Chuỗi lặp 30-B: benchmarkIndex **2.750,5–3.176,5**, chênh **15,49%**, Performance **89 (89–89)**, TBT **36,5 (30–48 ms)**, A/BP/SEO **100**, CLS **0,0172**; desktop **100**. Đạt cổng chính 88/200, không cần mức ngoại lệ 85/250. CLS không tăng so với mốc 14/10; LCP mô phỏng **3,600 s** vẫn theo dõi theo ngoại lệ đã duyệt. Đo lại dữ liệu thực khi có domain triển khai. [Evidence cuối](../docs/testing/frontend-fraunces-self-hosted-2026-10-30.md) có trung vị/min–max, benchmark từng lượt, ảnh và giới hạn kiểm. Handoff giữ nguyên; chỉ commit local, chưa push.
