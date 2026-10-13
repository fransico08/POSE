# Nghiệm thu trang giới thiệu POSE CRM — đợt 2

Ngày **13/10/2026** · Owner **Huỳnh Minh Tài** · Nhánh **Tài**. Đầu vào Phase A 320d321, foundation đã review tại 6518703.

Tài duyệt Phase B–C, hai diễn đạt BR:46 và BR:27/ARCH bước 3/DEC-02, cùng sáu chỉnh sửa ngày 13/10. Chữ đã cập nhật trước code tại ea2a44d. [Nội dung/nguồn](../design/ui-ux/landing-content-v1.md) · [Wireframe](../design/ui-ux/landing-wireframe-v1.md) · [Design system](../design/ui-ux/design-system-v1.md) · [README](../../frontend/README.md).

## 1. Môi trường và phạm vi

Windows PowerShell 5.1, Node **24.13.0**, npm **11.6.2**, Chrome **141.0.7390.123**, Lighthouse **13.5.0** (stable xác minh bằng npm view). Không có Chrome DevTools MCP; dùng Chrome headless/profile riêng qua CDP với Python/công cụ sẵn có, không cài MCP/extension/dependency frontend. DOM/style chỉ đọc; thao tác bằng Input/DOM.focus/CDP, không dùng JS sửa DOM hoặc gọi API.

Không đổi package/lock, font, token màu hoặc logic backend/BFF/proxy/session. Be Vietnam Pro 400/500/600/700 giữ nguyên. Providers chỉ chuyển vị trí bọc theo duyệt. Production worktree chạy 3002, bản sao sạch chạy 3003; tiến trình khác ở 3000 được giữ nguyên. SITE_URL không đặt trong lần build này: kiểm canonical/OG/sitemap theo mặc định 127.0.0.1:3000; unit test còn kiểm origin HTTPS cấu hình. Đặt domain thật trước build triển khai.

| Package | Version giữ nguyên |
|---|---|
| next; react/react-dom | 16.4.0; 19.3.0 |
| @tanstack/react-query; @heroicons/react | 5.104.2; 2.2.0 |
| typescript; @types/node; @types/react/react-dom | 6.0.3; 24.19.2; 19.3.0 |
| tailwindcss; @tailwindcss/postcss | 4.3.3; 4.3.3 |
| eslint; eslint-config-next; eslint-plugin-jsx-a11y | 9.39.5; 16.4.0; 6.10.2 |
| vitest; @vitejs/plugin-react; jsdom | 5.0.4; 6.1.2; 29.1.1 |
| @testing-library/react; user-event; jest-dom | 16.3.3; 14.6.7; 7.0.1 |

## 2. Clean install và production

Bản sao cuối từ các file frontend Git theo dõi tại source 7cbce2d, dưới `<TEMP>/pose-landing-acceptance-*`; ban đầu không có node_modules, .next hoặc next-env.d.ts. Không sao chép file môi trường riêng. Chạy tuần tự, dừng khi lệnh trả mã khác 0. Production HTTP/Lighthouse dùng bản copy trước đó có cùng code ứng dụng; thay đổi sau chỉ là README và kiểm test đối chiếu credentials:

```powershell
npm ci
npm run lint
npm run typecheck
npm run test
npm run build
npm run start -- --port 3003
curl.exe --fail --silent --show-error http://127.0.0.1:3003/ --output <TEMP>/landing.html
```

| Phép kiểm | Kết quả |
|---|---|
| npm ci | Đạt, 448 package; package/lock không thay đổi |
| lint | Đạt, 0 warning/error |
| typecheck trước build | Đạt, không cần next-env.d.ts sinh tự động |
| Vitest/RTL | **78/78**, 6 file; 66 test cũ + 12 test landing; 0 skip/fail |
| build | Đạt; `○ /`, `○ /robots.txt`, `○ /sitemap.xml`; API/login vẫn dynamic |
| HTML từ curl | Một H1, lang vi, meta/title/canonical/OG/Twitter và graph JSON-LD có ngay trong HTML |
| robots.txt | Allow /; Disallow /app, /api, /dang-nhap; sitemap theo SITE_URL |
| sitemap.xml | Chỉ /, lastmod cố định 2026-10-13 |
| Root metadata | index, follow; Next chuẩn hóa canonical root thành URL không có dấu / cuối |
| Login metadata / API header | Login noindex, nofollow; GET /api/session/me 401 có X-Robots-Tag noindex, nofollow |
| /app chưa đăng nhập | 307 tới /dang-nhap?returnTo=%2Fapp |

HTML parse bằng BeautifulSoup; JSON-LD kiểm bằng Python json.loads và JSON.parse trong test. Robots/sitemap/guard/header kiểm HTTP thực tế bằng requests. Lần clean đầu phát hiện import WebP tĩnh cần declaration từ next-env.d.ts. Đã sửa b25ada7: dùng đường dẫn public với width=1105/height=741 thật, rồi chạy lại **toàn bộ** trên bản sao mới trống. Không chỉnh file type sinh tự động. Test landing đối chiếu cả mật khẩu demo qua POSE_DEMO_PASSWORD chỉ ở process, dùng assertion boolean để không lộ giá trị trong output.

npm ci báo audit 5 high và ESLint hết hỗ trợ; dependency giữ nguyên theo phạm vi L4, chưa phân tích/nâng version ở đợt này.

**Kiểm lại bản cuối sau khi phiên trước bị gián đoạn (13/10/2026).** Xuất đúng các file Git theo dõi bằng `git archive HEAD frontend` tại **7cbce2d** ra thư mục tạm trống, rồi chạy tuần tự: `npm ci` (448 package), lint, typecheck, `npm test` với POSE_DEMO_PASSWORD chỉ đặt trong process (**78/78**, 6 file; test đối chiếu mật khẩu chạy thật), build (`○ /`, `○ /robots.txt`, `○ /sitemap.xml`). Production `next start` cổng 3004:

| Phép kiểm HTTP | Kết quả |
|---|---|
| `/` | 1 H1, 8 H2, title 52 ký tự, robots index/follow, canonical và og:image theo SITE_URL mặc định, 0 form, không chuỗi email/mật khẩu demo |
| JSON-LD | Đúng 1 thẻ script `application/ld+json`; chuỗi thứ hai trong HTML là payload RSC của Next.js, không phải script trùng. Graph WebSite/SoftwareApplication/FAQPage; 8 câu hỏi khớp 8 summary hiển thị |
| `/robots.txt`, `/sitemap.xml` | Như bảng trên; sitemap chỉ `/`, lastmod 2026-10-13 |
| `/app` chưa đăng nhập | 307 |
| `/api/session/me` | 401, `X-Robots-Tag: noindex, nofollow` |
| `/dang-nhap` | robots noindex, nofollow |

Server dừng sau kiểm, cổng 3004 không còn listener. Lighthouse và kiểm tay ở mục 3, 5 không chạy lại trong lượt này vì không đổi mã ứng dụng sau 7cbce2d.

## 3. Lighthouse trước/sau

Production next start, không dev server. Report JSON đầy đủ ở TEMP, không commit. Lệnh thực tế, đường dẫn tạm được rút gọn:

```powershell
npm view lighthouse version
npx --yes lighthouse@13.5.0 http://127.0.0.1:3002/ --output=json --output-path=<TEMP>/lighthouse-mobile-root.json --chrome-flags="--headless=new" --quiet
npx --yes lighthouse@13.5.0 http://127.0.0.1:3003/ --output=json --output-path=<TEMP>/lighthouse-mobile-after.json --chrome-flags="--headless=new" --quiet
npx --yes lighthouse@13.5.0 http://127.0.0.1:3003/ --preset=desktop --output=json --output-path=<TEMP>/lighthouse-desktop-final.json --chrome-flags="--headless=new" --quiet
```

| Cấu hình | Performance | Accessibility | Best Practices | SEO | FCP | LCP | TBT | CLS |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Mobile, Providers root | 89 | 100 | 96 | 100 | 2,019s | 3,221s | 35ms | 0 |
| Mobile, Providers app/login | **92** | **100** | **96** | **100** | 1,690s | 3,168s | 57ms | 0,0094 |
| Desktop, bản cuối | **100** | **100** | **96** | **100** | 0,427s | 0,679s | 0ms | 0,0067 |

Mobile simulated 150ms RTT/1.638,4 Kbps/CPU ×4; desktop 40ms RTT/10.240 Kbps/CPU ×1. Lượt đầu trước sửa import ảnh: 88/100/96/100; baseline so sánh sau sửa import là **89**. Root tải bundle Query/cache phiên không dùng trên trang công khai; chuyển Providers nguyên vẹn xuống app/login tại f9bc80e. JavaScript tải **160.370 → 149.775 byte**, giảm **10.595 byte**; public không gọi /api. Mobile sau chuyển **92**, giữ mục tiêu ≥90. Đây là một lượt chính mỗi cấu hình, có biến thiên lab; giảm byte bundle là đối chiếu cụ thể, không suy ra cải thiện giống nhau trên mọi máy.

| Audit chưa đạt hoàn toàn | Quan sát / giới hạn |
|---|---|
| errors-in-console | /favicon.ico 404 làm Best Practices 96; chưa có favicon riêng trong phạm vi này |
| unused-javascript | Runtime Next/React còn phần chưa dùng; chỉ component dự án mới MobileNavDisclosure là client |
| lcp-discovery-insight | Ảnh discoverable/eager qua preload nhưng chưa có hint fetchpriority=high; preload thay priority deprecated trong Next 16 |
| legacy-javascript-insight | Runtime còn polyfill; không đổi stack/build targets |
| network-dependency-tree, render-blocking | Còn chuỗi font/CSS/runtime; ảnh/font self-host; không đổi font/token |
| FCP/LCP/SI/interactive, max-potential-fid | Một số metric mobile chưa đạt thang 100; tổng qua mục tiêu. Desktop LCP audit 0,99, tổng Performance 100 |

Không dùng Lighthouse để tuyên bố đạt toàn bộ WCAG hoặc bảo đảm xếp hạng/rich result.

## 4. Heading, JSON-LD và nguồn chữ

H1 duy nhất: **POSE CRM — quản lý bán hàng và chăm sóc khách hàng đa ngành**. Cây thực tế từ HTML có tám H2, 21 H3, không nhảy cấp:

| H2, theo thứ tự | H3 trực thuộc, theo thứ tự |
|---|---|
| Năm nhóm năng lực chính | Lead và Cơ hội bán hàng; Khách hàng và Customer 360; Chăm sóc và yêu cầu hỗ trợ; Dữ liệu và nhập liệu có duyệt; Phân quyền theo vai trò |
| Những khó khăn POSE CRM hướng tới | Hồ sơ và công việc thiếu liên kết; Bàn giao chưa rõ trách nhiệm; Truy cập vượt phần việc được giao; File nhập có lỗi hoặc nghi ngờ trùng; Cơ hội đã đóng vẫn bị sửa |
| Quy trình bán hàng và chăm sóc trên POSE | Tiếp nhận Lead; Xác nhận đủ điều kiện; Theo dõi Cơ hội bán hàng; Chuyển Lead thành Khách hàng; Đề nghị Bàn giao chăm sóc; Chăm sóc và xử lý hỗ trợ |
| Mỗi vai trò làm gì trên POSE | Quản trị hệ thống; Quản lý; Nhân viên kinh doanh; Nhân viên chăm sóc khách hàng; Nhân viên dữ liệu |
| Một nền tảng cho nhiều ngành | Không có H3 |
| Phân quyền và an toàn dữ liệu | Không có H3, dùng dt/dd |
| Câu hỏi thường gặp | Không có H3, dùng summary/details |
| Vào không gian làm việc của tổ chức | Không có H3 |

| Ý nội dung quan trọng | Nguồn / quyết định |
|---|---|
| Đồ án CRM đa ngành, 5 vai trò | readme.md:5–15; PROJECT_CONTEXT:12,20–25; roles-authorization:17–21 |
| Hiện có login/Tổng quan, module ở giai đoạn sau | Foundation review 13/10; scope Tài; scopeNote/productCaption/contentUpdatedAt cạnh nhau trong landing.ts |
| Lead chuyển khi đủ thông tin, không buộc Cơ hội Thắng | business-rules:46; Tài duyệt mục 12 |
| Cơ hội gắn Lead/Khách hàng, một sản phẩm/dịch vụ, có khóa/mở lại | business-rules:50–54; DEC-01 |
| Bàn giao chăm sóc do Quản lý duyệt | business-rules:80–83; DEC-05; roles-authorization:116–118 |
| Import một bước do Quản lý hoặc Quản trị hệ thống duyệt | business-rules:27,70; architecture bước 3/:97–100; DEC-02; Tài duyệt mục 12 |
| Ghi chú bán hàng ẩn với chăm sóc; chia sẻ vẫn cần quyền/phạm vi | roles-authorization:42,84,88; DEC-04; chữ S7/FAQ-06 Tài duyệt |
| Sáu ngành chỉ minh họa | Nhãn theo prompt S6; README gói mục 9, không lấy credentials |
| Trường, nhóm, năm ở footer | readme.md:15,90–98 |

Các câu chi tiết và dấu diễn giải ở [landing-content-v1.md](../design/ui-ux/landing-content-v1.md). Chữ công khai không có mã enum ghi chú, từ permission, email/mật khẩu/mã tổ chức demo, khách hàng giả hoặc KPI. Không phát hiện mâu thuẫn nội dung mới; lệch UC/BR đã được quyết định ở mục 12, không sửa tài liệu nghiệp vụ.

Một @graph WebSite/SoftwareApplication/FAQPage; JSON parser và JSON.parse test đạt, tám câu/đáp án khớp HTML từng chuỗi; FAQ-06 dùng chữ mới. Escape `<` thành `\u003c` được test với payload đóng script; không có offers/rating/review. Đối chiếu type/property thủ công với [WebSite](https://schema.org/WebSite), [SoftwareApplication](https://schema.org/SoftwareApplication), [FAQPage](https://schema.org/FAQPage), [Question/acceptedAnswer](https://schema.org/Question) ngày 13/10. Đây chưa phải kiểm validator công khai.

## 5. Accessibility và responsive

| Kiểm tay/quan sát CDP | Kết quả |
|---|---|
| Landmark | Tree Chrome: 1 banner, 1 main, 1 contentinfo; 2 navigation desktop; header display: contents vẫn giữ landmark |
| Heading/section/nav | 1 H1, 8 H2, 21 H3; 9 section aria-labelledby tới heading duy nhất; mọi anchor/id và SkipLink hợp lệ |
| Tab/Shift+Tab | 53 điểm dừng ở 1440, 54 ở 375 qua hai chiều; outline 2px solid, focus thấy được, không bị header che |
| SkipLink | Enter focus main#noi-dung, H1 xuất hiện dưới header |
| Disclosure mobile | Enter/Space mở/đóng; Tab vào nav; Esc đóng/trả focus trigger; không modal/trap, nav mở static trong luồng |
| FAQ | Enter/Space đổi trạng thái native details; mở FAQ-01/06; summary có nhãn rõ |
| Anchor | Heading FAQ sau nhảy: top 135,625/111,5px, dưới header 72/64px |
| Sticky/focus | Desktop 72px/scroll-padding 88px; mobile/tablet 64px/80px; 1440×480 bỏ sticky/16px |
| Target | 15 control/link/summary visible ở 640px; nhỏ nhất 44×44 CSS px |
| Reduced motion | Media reduce thật; transition 0s, scroll auto; không animation cuộn |
| Zoom native 200%/400% | CSS viewport 629×351 / 314×175; không CSS zoom; không overflow; header static/scroll-padding 16px; skip/menu/FAQ thao tác được |
| Runtime | 0 exception JS, 0 warning qua Runtime console; Lighthouse phát hiện riêng network error favicon 404 |
| Alert login | Margin dưới space-4 đo đúng 16px; role alert; password trống, xóa cả mã/email trước chụp |

| Viewport | document scrollWidth | Hero | Năng lực | Vai trò | Header |
|---|---:|---:|---:|---:|---:|
| 320 | 305 | 1 | 1 | 1 | 64px |
| 375 | 360 | 1 | 1 | 1 | 64px |
| 768 | 753 | 1 | 3 | 2 | 64px |
| 1024 | 1009 | 2 | 3 | 3 | 72px |
| 1440 | 1425 | 2 | 5 | 3 | 72px |

scrollWidth nhỏ hơn innerWidth do scrollbar dọc, không cuộn ngang. Kiểm thêm 640px: năng lực 2 cột. Hero hai cột từ 1024; text trước ảnh trên mobile.

Tương phản computed CSS, tính sRGB relative luminance; token không đổi:

| Cặp | Tỷ lệ |
|---|---:|
| Hero body/navy | 12,02:1 |
| Hero H1 trắng/navy | 17,85:1 |
| Eyebrow/navy | 12,56:1 |
| Text/canvas | 16,61:1 |
| Muted/surface; nav/surface | 7,58:1 |
| Muted/canvas | 7,05:1 |
| On-primary/primary | 5,17:1 |
| Icon primary/primary-subtle | 4,75:1 |
| Icon success/canvas | 4,67:1 |
| Focus-sidebar/navy | 12,56:1 |

Chữ đã đo ≥4,5:1; icon/focus ≥3:1. Focus primary/surface cùng cặp primary/on-primary 5,17:1. Border card là trang trí; không dùng màu làm thông tin duy nhất.

## 6. Ảnh thật và hồi quy phiên

Ảnh Tổng quan **Quản lý ở 1440px**, crop chỉ `.shortcuts`, bỏ toàn bộ user/tenant/sidebar. Hero WebP **1105×741 / 49.358 byte**; OG **1200×630 / 36.542 byte**, giữ toàn bộ nội dung ảnh với khoảng trống hai bên. Alt/figcaption dùng content chung; giữ “Sẽ có ở giai đoạn sau”. Đã xem ảnh bằng mắt, không chứa thông tin đăng nhập hoặc DevTools.

Backend thật dùng backup final checksum đúng, DB restore riêng `pose_v2_review_frontend_<timestamp>`, cluster 127.0.0.1:54329; SHOW data_directory đúng worktree trước thao tác. JWT ngẫu nhiên và mật khẩu demo chỉ ở process; không ghi file/log/ảnh. Không dùng 5432, không sửa backend/migrations/gói dữ liệu final.

| Hồi quy sau chuyển Providers | Kết quả |
|---|---|
| Login/me/logout thật 5 vai trò | Đạt; quyền ADMIN/MANAGER/SALES_REP/CUSTOMER_CARE/DATA_STAFF: 28/23/15/9/10; JSON không trả token |
| App bootstrap với Query context | Đạt, Quản lý có 14 thẻ lối tắt; nav chỉ Tổng quan |
| Có phiên → / → /dang-nhap | Đạt, trở lại /app |
| Xóa pose_at, backend đang chạy | Refresh hoạt động, giữ app |
| Xóa pose_at rồi tắt backend | “Không thể kết nối”, giữ refresh/cờ phiên, không chuyển login |
| Bật backend → Thử lại | App phục hồi, cùng JWT process của backend test |
| User menu/dialog/logout bằng bàn phím | Hủy focus đầu, Esc trả focus; xác nhận xóa cookie/state; /app lại bị guard |
| Đăng nhập khi backend tắt | Alert kết nối, giữ mã/email, xóa password |
| Cache cleanup RTL cũ | Query session và dữ liệu tenant seed bị xóa; single-flight/error test cũ vẫn đạt |

Cuối kiểm: **6 tổ chức/36 người dùng, fingerprint credentials giữ nguyên**. Đã dừng ba Next server của lượt này, Chrome riêng, backend và cluster; 3001/3002/3003/8080/54329 không còn listener. Không dừng tiến trình khác ở 3000.

## 7. Ảnh evidence

- [Desktop 1440](assets/frontend-landing-dot2-2026-10-13/landing-1440.png), [1024](assets/frontend-landing-dot2-2026-10-13/landing-1024.png), [tablet 768](assets/frontend-landing-dot2-2026-10-13/landing-768.png).
- [375](assets/frontend-landing-dot2-2026-10-13/landing-375.png), [toàn trang 375](assets/frontend-landing-dot2-2026-10-13/landing-375-full.png), [320](assets/frontend-landing-dot2-2026-10-13/landing-320.png), [toàn trang 320](assets/frontend-landing-dot2-2026-10-13/landing-320-full.png).
- [Disclosure mở](assets/frontend-landing-dot2-2026-10-13/landing-mobile-menu-open.png), [FAQ desktop](assets/frontend-landing-dot2-2026-10-13/landing-faq-1440.png), [FAQ mobile](assets/frontend-landing-dot2-2026-10-13/landing-faq-375.png).
- [Zoom 200%](assets/frontend-landing-dot2-2026-10-13/landing-zoom-200.png), [400%](assets/frontend-landing-dot2-2026-10-13/landing-zoom-400.png).
- [Login lỗi/khoảng cách 16px](assets/frontend-landing-dot2-2026-10-13/login-error-spacing.png), [login lỗi mạng](assets/frontend-landing-dot2-2026-10-13/login-network-error.png), [app lỗi mạng giữ phiên](assets/frontend-landing-dot2-2026-10-13/app-network-preserves-session.png).

## 8. Commit và phần chưa kiểm

| Hash | Mô tả |
|---|---|
| ea2a44d | docs(frontend): apply approved landing copy revisions |
| 317635e | feat(frontend): add approved public landing content |
| 4bcc7f9 | feat(frontend): add sanitized overview product images |
| 4965d7a | feat(frontend): build accessible public landing sections |
| cb5671c | feat(frontend): publish static landing metadata and crawl routes |
| e71923a | fix(frontend): space login alerts above the form |
| 4b58dda | test(frontend): verify landing semantics and disclosure behavior |
| b25ada7 | fix(frontend): typecheck product images without generated files |
| f9bc80e | perf(frontend): scope session providers to app and login |
| 7e8369b | docs(frontend): document landing maintenance and design validation |
| 7cbce2d | test(frontend): check public copy against process-only credentials |

Evidence/ảnh commit sau các commit trên. Author/committer fransico08; không Co-authored-by, push, merge, PR hoặc đổi Trello. Kiểm cuối Git sạch sau commit. Không lưu full Lighthouse HTML/JSON hay dữ liệu môi trường.

**Chưa kiểm**: screen reader NVDA/VoiceOver, Firefox/Safari và thiết bị vật lý; không có môi trường tương ứng. Chưa chạy Rich Results Test/Schema Markup Validator bằng URL công khai vì chỉ có localhost, không deploy trong scope. Chưa kiểm indexing/Search Console/hiển thị tìm kiếm thực tế; không hứa rich result. Favicon chưa có; audit resource ở mục 3. Dependency audit chưa phân tích/nâng version ngoài L4. Không tuyên bố đạt toàn bộ WCAG 2.2 AA hoặc nghiệm thu mọi module nghiệp vụ.

Bước sau: Tài chốt domain/SITE_URL thật để kiểm canonical, OG khi chia sẻ và validator từ ngoài; cân nhắc favicon riêng, đo lại production trên hạ tầng triển khai. Module mới cần cập nhật scopeNote/productCaption/contentUpdatedAt và ảnh thật. Các trang module, blog, giá hoặc form tiếp tục ngoài đợt 2.
