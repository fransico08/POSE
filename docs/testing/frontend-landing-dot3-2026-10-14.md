# Frontend landing đợt 3 — review điểm dừng 2, ngày 14/10/2026

Owner: **Huỳnh Minh Tài**. Concept B Fraunces 700/800 + hổ phách giữ nguyên. Phạm vi: A1–A5 và B6–B13 theo review; hoàn tất ảnh, kiểm sạch và tài liệu, **chưa duyệt điểm 2, chưa Phase 5**. Các mốc A1–A5 thu ngày 13/10; lượt xác nhận cuối, ảnh cuối và clean check thu ngày 14/10. [Evidence trước review](frontend-landing-dot3-2026-10-13.md) giữ làm lịch sử.

**Kết luận:** trung vị mobile cuối Performance **89**, LCP **3,613 giây**, chưa qua cổng 90 và 2,5 giây. CLS đạt. Dừng tối ưu theo A5; chưa áp dụng display optional hoặc đổi H2 sang Be Vietnam Pro. Inline CSS là phép thử cục bộ đã được Tài cho phép, chưa là quyết định triển khai. Desktop đạt **100/100/100/100**.

## Môi trường và phương pháp

Nhánh Tài, worktree đã chỉ định; source kiểm sạch cuối **ad2b46a**. Node 24.13.0, npm 11.6.2, Next 16.4.0, React 19.3.0, TypeScript 6.0.3, Vitest 5.0.4, Chrome 141.0.7390.123, Lighthouse **13.5.0**. Production next start tại 127.0.0.1:3003; SITE_URL cùng địa chỉ khi build. Chưa domain thật.

Không có Chrome DevTools MCP; dùng Chrome headless profile cô lập qua CDP với công cụ Python có sẵn. Runtime chỉ đọc DOM/style/font, thao tác bằng Input, DOM.focus và DOM.scrollIntoViewIfNeeded. Không dùng profile cá nhân hoặc credentials thật. Lighthouse tuần tự, cache/profile sạch mỗi lượt, không build hoặc kiểm browser khác đồng thời. Mobile dùng preset và throttling mô phỏng mặc định. Số LCP/FCP trong bảng là số Lighthouse mô phỏng, khác thời gian trace localhost gốc. Không chọn riêng lượt đẹp; mỗi bước đủ 5 lượt.

## Kiểm bản sao sạch

Tạo bản sao frontend bằng git archive, không mang node_modules, .next hoặc .env từ worktree. npm ci cài đúng lockfile. Sau khi bổ sung đủ kiểu cho fixture IntersectionObserver, nạp lại archive source ad2b46a và chạy lại lint/typecheck/test/build. Không sửa dependency hoặc lockfile.

```powershell
git archive --format=tar --output=<thư mục tạm>/source.tar ad2b46a frontend
tar -xf <thư mục tạm>/source.tar -C <thư mục tạm>
npm ci
$env:SITE_URL='http://127.0.0.1:3003'
npx next typegen
npm run typecheck
npm run lint
npm test
npm run build
npm start -- --port 3003
npx --yes lighthouse@13.5.0 http://127.0.0.1:3003/ --chrome-flags="--headless=new --no-sandbox" --only-categories=performance,accessibility,best-practices,seo --output=json --output-path=<thư mục tạm>/<lượt>.json --quiet
# Desktop: thêm --preset=desktop
```

| Check | Kết quả |
|---|---|
| npm ci | Đạt, 448 package; npm báo 5 advisory high của lockfile hiện có, chưa xử lý ngoài phạm vi |
| lint / typecheck | Đạt, không warning ESLint |
| test | **104/104**, 14 file: **66 foundation + 38 landing** |
| build | Đạt; **○ /**; không route concept trong danh sách build |
| Header regression | Đạt: anchor bỏ qua sentinel của hero cao vẫn đổi nền; quay về hero khôi phục |
| Phạm vi diff | Không đổi backend, package, auth/session/BFF/proxy hoặc logic form; app/login chỉ đổi import CSS |

Typecheck đầu phát hiện fixture test mới thiếu trường IntersectionObserverEntry; đã sửa ở ad2b46a và chạy lại toàn bộ check đạt. Lệnh start có gán env bị công cụ chặn; script npm start có sẵn chạy thành công, dùng SITE_URL đã đưa vào build.

## Số đo trước/sau A1–A5

Mỗi ô: **trung vị (min–max)**, 5 lượt. LCP/FCP giây, TBT ms. Accessibility/Best Practices/SEO **100/100/100 trong mọi lượt**.

| Bước | Performance | LCP (s) | FCP (s) | TBT (ms) | CLS |
|---|---:|---:|---:|---:|---:|
| Trước review | 88 (75–88) | 3.612 (3.595–3.785) | 2.117 (2.114–2.397) | 86 (72–316.00000000000045) | 0.0152 (0.0000–0.0152) |
| A1: preload latin-ext | 90 (85–90) | 3.477 (3.454–3.552) | 1.671 (1.670–2.190) | 102 (78–175) | 0.0152 (0.0000–0.0152) |
| A2: tách CSS app | 89 (88–90) | 3.619 (3.463–3.656) | 1.675 (1.670–1.687) | 87 (80–96) | 0.0152 (0.0152–0.0152) |
| A3: chữ hero server | 90 (88–90) | 3.473 (3.453–3.656) | 1.674 (1.670–1.687) | 92 (78–99) | 0.0171 (0.0171–0.0172) |
| A4: sau chỉnh giao diện | 88 (85–89) | 3.646 (3.614–3.727) | 1.671 (1.669–1.681) | 117 (86–190) | 0.0172 (0.0172–0.0172) |
| A5: inlineCss | 89 (85–90) | 3.614 (3.587–3.811) | 1.664 (1.661–2.186) | 47.5 (36–104) | 0.0172 (0.0000–0.0172) |
| Xác nhận cuối 14/10 | 89 (87–90) | 3.613 (3.585–3.787) | 1.668 (1.665–1.672) | 44.5 (32.5–97) | 0.0172 (0.0000–0.0172) |

A1 preload đủ display font đã tồn tại; A2 CSS app ra khỏi public; A3 chữ hero server, H1 không hiệu ứng; A4 gồm chỉnh giao diện; A5 bật inlineCss vì A4 chưa đạt. Lượt cuối xác nhận source sửa header, không thử thêm font hoặc phương án ngoài phạm vi.

### Từng lượt mobile

| Bước | Lượt | Performance | LCP (s) | FCP (s) | TBT (ms) | CLS |
|---|---:|---:|---:|---:|---:|---:|
| baseline | 1 | 75 | 3.785 | 2.397 | 316.00000000000045 | 0.0000 |
| baseline | 2 | 88 | 3.609 | 2.117 | 80 | 0.0152 |
| baseline | 3 | 87 | 3.634 | 2.119 | 102 | 0.0152 |
| baseline | 4 | 88 | 3.612 | 2.115 | 86 | 0.0152 |
| baseline | 5 | 88 | 3.595 | 2.114 | 72 | 0.0152 |
| A1 | 1 | 85 | 3.511 | 2.190 | 103.00000000000045 | 0.0000 |
| A1 | 2 | 90 | 3.454 | 1.671 | 78 | 0.0152 |
| A1 | 3 | 90 | 3.475 | 1.674 | 96 | 0.0152 |
| A1 | 4 | 87 | 3.552 | 1.671 | 175 | 0.0152 |
| A1 | 5 | 90 | 3.477 | 1.670 | 102 | 0.0152 |
| A2 | 1 | 88 | 3.656 | 1.687 | 96 | 0.0152 |
| A2 | 2 | 89 | 3.605 | 1.670 | 80 | 0.0152 |
| A2 | 3 | 89 | 3.626 | 1.681 | 87 | 0.0152 |
| A2 | 4 | 89 | 3.619 | 1.675 | 87 | 0.0152 |
| A2 | 5 | 90 | 3.463 | 1.673 | 85 | 0.0152 |
| A3 | 1 | 88 | 3.656 | 1.687 | 99 | 0.0171 |
| A3 | 2 | 90 | 3.453 | 1.670 | 78 | 0.0171 |
| A3 | 3 | 89 | 3.622 | 1.674 | 92 | 0.0171 |
| A3 | 4 | 90 | 3.473 | 1.675 | 92 | 0.0171 |
| A3 | 5 | 90 | 3.462 | 1.674 | 82.99999999999977 | 0.0172 |
| A4 | 1 | 89 | 3.619 | 1.672 | 86 | 0.0172 |
| A4 | 2 | 86 | 3.694 | 1.671 | 172 | 0.0172 |
| A4 | 3 | 85 | 3.727 | 1.681 | 190 | 0.0172 |
| A4 | 4 | 89 | 3.614 | 1.669 | 86 | 0.0172 |
| A4 | 5 | 88 | 3.646 | 1.669 | 117 | 0.0172 |
| A5 | 1 | 87 | 3.811 | 1.687 | 100 | 0.0172 |
| A5 | 2 | 89 | 3.614 | 1.664 | 47.5 | 0.0172 |
| A5 | 3 | 90 | 3.601 | 1.662 | 42.5 | 0.0172 |
| A5 | 4 | 85 | 3.770 | 2.186 | 104 | 0.0000 |
| A5 | 5 | 90 | 3.587 | 1.661 | 36 | 0.0172 |
| final | 1 | 87 | 3.787 | 1.672 | 97 | 0.0172 |
| final | 2 | 90 | 3.595 | 1.665 | 37.5 | 0.0172 |
| final | 3 | 89 | 3.613 | 1.668 | 44.5 | 0.0172 |
| final | 4 | 90 | 3.585 | 1.665 | 32.5 | 0.0172 |
| final | 5 | 89 | 3.613 | 1.668 | 44.5 | 0.0000 |

### Desktop, một lượt mỗi mốc

| Mốc | P/A/BP/SEO | LCP (s) | FCP (s) | TBT (ms) | CLS |
|---|---|---:|---:|---:|---:|
| A4 | 99/100/100/100 | 0,902 | 0,494 | 15 | 0,0004 |
| A5 | 100/100/100/100 | 0,768 | 0,410 | 0 | 0,0004 |
| Cuối 14/10 | **100/100/100/100** | **0,777** | 0,414 | 0 | 0 |

### LCP và chuỗi tải tới chữ

LCP mobile là span cuối của h1#hero-title: **“Trên một nền tảng.”**, không phải mock/ảnh. Trace cuối lượt 3: TTFB 12,969 ms, element render delay 329,434 ms; **khác** LCP Lighthouse mô phỏng 3,613 s.

Trước A1: HTML → stylesheet → Fraunces latin-ext chưa preload. Sau A1: HTML → preload trực tiếp ba file Fraunces dùng cho H1. Sau A5: HTML đã có CSS inline → font, không stylesheet request trên root. Trace cuối: ba Fraunces priority High, request bắt đầu khoảng 23,6–24,3 ms và kết thúc 29,4–38,6 ms. Cây dependency còn HTML → file Be Vietnam Pro không preload thuộc subset khác. Chưa đủ bằng chứng kết luận Fraunces là nguyên nhân chính duy nhất. H1 đã tĩnh và server rendered.

Audit mobile cuối còn chưa đạt tối đa: FCP, LCP, max-potential-fid (~140 ms), interactive (~3,7 s), unused-javascript (ước tính 26 KiB), legacy-javascript-insight (ước tính 14 KiB), network-dependency-tree-insight. Không audit fail trong ba category A/BP/SEO. Không tự đổi framework/polyfill hoặc font app.

### Chi phí inline CSS và ngân sách

| Hạng mục | Trước / baseline | Sau / kết quả cuối |
|---|---:|---:|
| JS baseline đợt 2 đã ghi | 149.775 byte | **147.613 byte transferSize Lighthouse**, giảm 2.162 byte |
| JS payload nén Resource Timing | — | **144.367 byte encodedBodySize** |
| DOM | A4 Lighthouse 1.085 | Cuối Lighthouse 1.087; DOM sau hydration **1.127**, dưới 1.200 |
| HTML raw A4 → A5/cuối | 194.750 byte | **323.884 byte**, tăng 129.134 |
| Document transfer A4 → A5 | 28.698 byte | **52.287 byte** |
| CSS external root | A4: 2 file, transfer 3.549 + 8.440 byte | A5/cuối: **0 link stylesheet, 1 style inline** |
| Fraunces raw, cache sạch | 81.736 byte | **81.736 byte**, không tăng |

JS không tăng quá 20 KiB; payload nén và transferSize ghi riêng, vì transferSize có overhead. Inline CSS giảm TBT trung vị A4 117 → A5 47,5 → cuối 44,5 ms, nhưng không giúp qua LCP/Performance. Flag experimental **áp dụng toàn production**, không chỉ root; CSS lặp trong HTML/RSC, không cache như stylesheet độc lập. Document + CSS A4 transfer khoảng 40.687 byte, nhỏ hơn document A5 52.287 byte. Không tuyên bố inlineCss tối ưu tổng tải; giữ phép thử cục bộ chờ Tài quyết.

## Font, CSS và nội dung

Fraunces config weight tĩnh 700/800, subsets vietnamese/latin/latin-ext, display swap, không axes. Hai descriptor dùng chung ba file; không bỏ 700/quay A. Cache sạch, production HTML preload đúng cả ba:

| File production | Byte | Preload |
|---|---:|---|
| 1e219c03c996efbd-s.p.2qo5md4hn_gum.woff2 | 11.536 | Có |
| b35b0dbffda7f2c4-s.p.1d9rlgtqyj_-l.woff2 | 33.640 | Có |
| 03bda585a99c6450-s.p.32sris142tqlb.woff2 | 36.560 | Có |
| Tổng | **81.736 / 81.920 byte** | Đạt |

CSS.getPlatformFontsForNode: H1 49 glyph/H2 35 glyph chỉ Fraunces custom, không fallback hệ thống. Đã xem ảnh dấu tiếng Việt H1/H2 và đối chiếu ảnh glyph weight tĩnh trong evidence 13/10; không tofu/dấu bị cắt. H1 animation none, transform none, opacity 1.

Globals chỉ Tailwind/theme/tokens/base/SkipLink. Root không selector login-layout/sidebar/shortcut-card. App/login nhận components/pages tại route boundary; buttons/status dùng riêng cho error/404. [Content v2](../design/ui-ux/landing-content-v2.md) giữ nguồn từng câu, bổ sung nguồn nhãn mock trước code. Khoảng **446–447 từ marketing** tùy cụm xoay, tab Quản lý mặc định/FAQ đóng; không tính nhãn mock/chip/control/FAQ answer/tab ẩn. Runtime cuối ghi 447. contentUpdatedAt giữ 13/10 vì không thêm module/đổi thông điệp marketing trong kiểm cuối.

## Semantic, SEO và HTTP production

| Check | Kết quả |
|---|---|
| Root | 200, một H1/một header; banner/main/contentinfo/navigation; section aria-labelledby |
| Heading | H1 → H2 từng section → H3 tab/bước/thẻ; panel ẩn giữ HTML |
| Title/description | 50/147 ký tự; không chữ đồ án |
| Canonical/OG | SITE_URL thử nghiệm 3003; canonical root; OG/Twitter giữ ảnh minh họa hiện có |
| JSON-LD | Một block WebSite/SoftwareApplication/FAQPage parse hợp lệ; 5 FAQ khớp details; không offers/rating giả |
| Chữ công khai | Không R1, enum kỹ thuật, permission hoặc chuỗi email/credentials |
| Robots/sitemap | 200, disallow app/API/login; sitemap chỉ root, lastmod 13/10 |
| Login/app/API | Login noindex/nofollow; app 307 sang login; api/session/me 401 + X-Robots-Tag noindex/nofollow |
| Icon | favicon.ico, icon.svg, apple-icon.png 200 |
| Route concept | hero-a, hero-b, font-check 404 production; source route đã gỡ |

Cây heading quan sát; các H3 tab/ngành không chọn vẫn ở HTML với hidden:

```text
H1 Bán nhiều hơn. Chăm sóc tốt hơn. Trên một nền tảng.
 H2 Một nền tảng, nhiều ngành
 H2 Khách hàng không rơi giữa phòng ban
 H2 Đúng việc cho từng vai trò
  H3 Điều phối cả đội
  H3 Theo sát từng cơ hội / Chăm sóc có ngữ cảnh / Chuẩn bị dữ liệu đúng / Quản trị trong tổ chức [hidden]
 H2 Liền mạch từ Lead đến chăm sóc
  H3 Lead / Cơ hội bán hàng / Khách hàng / Chăm sóc
 H2 Một nơi cho công việc
  H3 Customer 360 / Phân quyền theo vai trò / Nhập dữ liệu có duyệt
  H3 Bàn giao khách hàng / Nhiệm vụ và nhắc việc / Đa ngành
 H2 Phù hợp cách bạn kinh doanh
  H3 Gói đào tạo cơ bản
  H3 Dịch vụ tư vấn nhà ở / Gói phần mềm cơ bản / Gói đồ uống mẫu / Dịch vụ giao nhận mẫu / Gói tư vấn sức khỏe [hidden]
 H2 Ai cần thấy gì, thấy đúng thứ đó
 H2 Câu hỏi thường gặp
 H2 Đội ngũ của bạn đã sẵn sàng?
```

## Accessibility, motion và responsive

| Kiểm browser | Kết quả |
|---|---|
| SkipLink/Tab/Shift+Tab | Focus vào main; menu Enter mở/Tab vào nav/Shift+Tab về trigger |
| Disclosure | Escape đóng/trả focus, không dialog/modal |
| Slider | Arrow/Home/End, kéo chuột/touch emulation đổi value; Before trái/POSE phải, aria-valuetext đúng hai phần |
| Role tabs | Arrow/Home/End chọn/focus đúng, aria-selected/hidden đúng; focus dừng sau hơn 6 giây |
| Hero motion | Pause/Space dừng figure/cụm xoay; resume tiếp tục; demo picker Enter đổi emphasis |
| Industry | Arrow/Home/End/dot Enter đổi card/thuộc tính |
| FAQ/security | Enter/Space details hoạt động; góc nhìn đổi ghi chú; mock aria-hidden |
| Ẩn tab/ngoài viewport | Hero visibility hidden/data-running false; hero/marquee/role dừng ngoài viewport |
| Reduced motion | 0 running/0 motion-ready/0 animation; clone none/chip wrap; “Đã tắt chuyển động”, không icon phát |
| Header/anchor | Header surface khi xuống; #cach-hoat-dong top 87,77 px, H2 top 151,77, header bottom 72: không che |
| Responsive | 320/375/768/1024/1440 không scroll ngang; header 64 px mobile/72 px desktop |
| Touch device emulation | 320/375 mobile:true, clientWidth=scrollWidth=viewport; 1 cột hero, H1 ba dòng |
| H1/K4/K7 | Mỗi câu H1 một dòng tại 1440/1024/375/320, không vượt viewport; “chăm sóc”/“thứ đó” cùng dòng |
| Zoom native | 200%/400% không scroll ngang/title cắt; header static/scroll padding 16 px tránh chiếm viewport thấp |
| Console | Không exception/warning/error trong luồng đã kiểm |
| K3 đang chạy | Progress scaleX ~0,388 desktop/~0,317 mobile, đủ 5 dòng avatar/hạn/trạng thái |

Tương phản theo [design system](../design/ui-ux/design-system-v1.md): WCAG sRGB với cận nền sáng nhất khi blue 24% + amber 24% + grid 5,5% + noise 3% cùng điểm. Trắng **7,69:1**, muted **5,18:1**, hổ phách **3,58:1** chỉ H1 lớn; focus 5,41:1, border 3,54:1; nút trắng 5,17:1 và shimmer sáng nhất4,66:1. Navy thuần trắng 18,72:1. Không đổi palette/font app.

K2 có 6 dòng, 2 dòng trùng, 4 ghi chú, 2 tin nhắn và cảnh báo; thêm ảnh trạng thái đầy đủ Before/After để thấy cả hai lớp ngoài phần bị đường chia che.

## Ảnh review

Ảnh cuối 14/10, không secrets/dữ liệu người thật. Chuỗi hero/K3 thay video ngắn. Mobile cuối device emulation 375 px cảm ứng; desktop 1440 px (ảnh nội dung trừ scrollbar).

| Trạng thái | Desktop | Mobile |
|---|---|---|
| Toàn trang đang chuyển động | [Ảnh](assets/frontend-landing-dot3-2026-10-14/review-desktop-motion.png) | [Ảnh](assets/frontend-landing-dot3-2026-10-14/review-mobile-motion.png) |
| Toàn trang reduced motion | [Ảnh](assets/frontend-landing-dot3-2026-10-14/review-desktop-reduced.png) | [Ảnh](assets/frontend-landing-dot3-2026-10-14/review-mobile-reduced.png) |
| K1 hero | [Ảnh](assets/frontend-landing-dot3-2026-10-14/review-k1-desktop.png) | [Ảnh](assets/frontend-landing-dot3-2026-10-14/review-k1-mobile.png) |
| K2 so sánh 50% | [Ảnh](assets/frontend-landing-dot3-2026-10-14/review-k2-desktop.png) | [Ảnh](assets/frontend-landing-dot3-2026-10-14/review-k2-mobile.png) |
| K2 Before đầy đủ | [Ảnh](assets/frontend-landing-dot3-2026-10-14/review-k2-before-desktop.png) | [Ảnh](assets/frontend-landing-dot3-2026-10-14/review-k2-before-mobile.png) |
| K2 After đầy đủ | [Ảnh](assets/frontend-landing-dot3-2026-10-14/review-k2-after-desktop.png) | [Ảnh](assets/frontend-landing-dot3-2026-10-14/review-k2-after-mobile.png) |
| K3 vai trò | [Ảnh](assets/frontend-landing-dot3-2026-10-14/review-k3-desktop.png) | [Ảnh](assets/frontend-landing-dot3-2026-10-14/review-k3-mobile.png) |
| K3 progress đang chạy | [Ảnh](assets/frontend-landing-dot3-2026-10-14/review-k3-running-desktop.png) | [Ảnh](assets/frontend-landing-dot3-2026-10-14/review-k3-running-mobile.png) |
| K4 hành trình | [Ảnh](assets/frontend-landing-dot3-2026-10-14/review-k4-desktop.png) | [Ảnh](assets/frontend-landing-dot3-2026-10-14/review-k4-mobile.png) |
| K5 bento | [Ảnh](assets/frontend-landing-dot3-2026-10-14/review-k5-desktop.png) | [Ảnh](assets/frontend-landing-dot3-2026-10-14/review-k5-mobile.png) |
| K6 ngành | [Ảnh](assets/frontend-landing-dot3-2026-10-14/review-k6-desktop.png) | [Ảnh](assets/frontend-landing-dot3-2026-10-14/review-k6-mobile.png) |
| K7 bảo mật | [Ảnh](assets/frontend-landing-dot3-2026-10-14/review-k7-security-desktop.png) | [Ảnh](assets/frontend-landing-dot3-2026-10-14/review-k7-security-mobile.png) |
| K7 FAQ | [Ảnh](assets/frontend-landing-dot3-2026-10-14/review-k7-faq-desktop.png) | [Ảnh](assets/frontend-landing-dot3-2026-10-14/review-k7-faq-mobile.png) |
| K7 CTA | [Ảnh](assets/frontend-landing-dot3-2026-10-14/review-k7-cta-desktop.png) | [Ảnh](assets/frontend-landing-dot3-2026-10-14/review-k7-cta-mobile.png) |

Chuỗi hero: [Đủ điều kiện](assets/frontend-landing-dot3-2026-10-14/review-hero-qualified.png) → [Đề xuất](assets/frontend-landing-dot3-2026-10-14/review-hero-proposal.png) → [Thắng](assets/frontend-landing-dot3-2026-10-14/review-hero-won.png). [Hero mobile đang chạy](assets/frontend-landing-dot3-2026-10-14/review-k1-mobile-motion.png).

Zoom: [200%](assets/frontend-landing-dot3-2026-10-14/review-zoom-200.png), [H1 200%](assets/frontend-landing-dot3-2026-10-14/review-zoom-200-headline.png), [400%](assets/frontend-landing-dot3-2026-10-14/review-zoom-400.png), [H1 400%](assets/frontend-landing-dot3-2026-10-14/review-zoom-400-headline.png).

### Hồi quy thị giác khi tách CSS

| Trang | A2 ngày 13/10 | Sau inlineCss + build sạch 14/10 |
|---|---|---|
| Login | [Ảnh](assets/frontend-landing-dot3-2026-10-13/review-A2-login.png) | [Ảnh](assets/frontend-landing-dot3-2026-10-14/review-final-login.png) |
| Tổng quan | [Ảnh](assets/frontend-landing-dot3-2026-10-13/review-A2-overview.png) | [Ảnh](assets/frontend-landing-dot3-2026-10-14/review-final-overview.png) |
| 403 | [Ảnh](assets/frontend-landing-dot3-2026-10-13/review-A2-403.png) | [Ảnh](assets/frontend-landing-dot3-2026-10-14/review-final-403.png) |
| 404 | [Ảnh](assets/frontend-landing-dot3-2026-10-13/review-A2-404.png) | [Ảnh](assets/frontend-landing-dot3-2026-10-14/review-final-404.png) |

Login grid 2×720 px, panel navy, input 50 px; app sidebar 256 px, margin 256 px, 10 lối tắt; 403/404 max 640 px, button blue. Đã xem ảnh sau hai bước; SHA-256 của từng cặp A2/cuối giống nhau. Tổng quan/403 dùng Fetch interception cho api/session/me với tên “Người dùng mẫu”, “Tổ chức minh họa”, email/mã để trống; chỉ presence flag trong profile riêng. Đây là kiểm CSS/bố cục, **không** kiểm session/backend thật. Không sửa source BFF, không credentials trong ảnh.

## Commit cục bộ của lượt review

Từng ý riêng; author/committer fransico08, không Co-authored-by. Danh sách sau bd423bb (review đầu):

| Hash | Mô tả commit |
|---|---|
| 2e67442 | perf(frontend): preload the existing Fraunces latin-ext subset |
| 8ebfb6b | perf(frontend): load app styles only from their route boundaries |
| 38b3b80 | docs(content): trace the approved landing review illustrations |
| f1014f5 | perf(frontend): render hero copy on the server outside animated islands |
| eb8d5c9 | fix(frontend): preserve complete hero sentences and balanced headings |
| 4211585 | fix(frontend): place hero overlays below readable pipeline content |
| 7f32ea0 | fix(frontend): explain disabled motion without playback icons |
| c4f0e99 | fix(frontend): compare scattered work on the left with POSE on the right |
| b5c57a9 | fix(frontend): fill role previews with assigned work and deadlines |
| d2d99a1 | fix(frontend): fill the customer timeline and use six compact industry chips |
| 37a1b3d | fix(frontend): show a sample product card beside industry attributes |
| 95fe916 | perf(frontend): remove styles for replaced hero and mock markup |
| ef3bf00 | test(frontend): cover populated previews and reduced-motion controls |
| c43ed0c | fix(frontend): keep hero copy above the fold and preserve sentence wrapping |
| c857469 | perf(frontend): inline production styles for the authorized performance trial |
| 8c0247d | docs(frontend): document the approved CSS and hero review changes |
| d22c283 | fix(frontend): update the header after anchor jumps past the hero |
| ad2b46a | test(frontend): supply a complete observer entry for anchor regression |
| c4660e0 | docs(frontend): link the final review and record anchor regression checks |

Commit tài liệu/ảnh cuối xuất hiện sau source kiểm sạch ad2b46a trong Git. Chưa push/merge/PR/Trello.

## Chưa làm hoặc chưa kiểm

- **Điểm 2/cổng hiệu năng chưa được duyệt.** Dừng theo A5. Hai phương án cần Tài chọn trước khi thử: Fraunces display optional (có thể giữ fallback khi lần đầu tải chậm), hoặc Fraunces chỉ H1/H2 dùng Be Vietnam Pro. Đổi H2 không bảo đảm giảm byte vì H1 vẫn dùng cùng ba file. Chưa thử/chưa hứa mức cải thiện.
- Phase 5 login/app micro-interaction và hồi quy backend thật 5 vai trò/refresh/mất mạng/logout **chưa làm**, đúng yêu cầu. 66 test foundation đạt không thay backend thật.
- Rich Results/Schema Markup Validator URL công khai **chưa kiểm**: localhost không truy cập từ ngoài. JSON-LD parse/đối chiếu local chưa là xác nhận Google.
- Screen reader NVDA/VoiceOver và thiết bị cảm ứng thật **chưa kiểm**; đã kiểm accessibility tree/bàn phím/touch emulation/zoom native. Lighthouse 100 không chứng nhận toàn WCAG 2.2 AA.
- Chưa video, dùng chuỗi ảnh; OG hiện có giữ nguyên, chưa ảnh OG theo bố cục review. Không commit report Lighthouse đầy đủ/dữ liệu môi trường; JSON/trace giữ tạm.

Tiếp theo: Tài xem ảnh, chọn phương án hiệu năng được phép thử và góp ý landing. Chỉ khi đạt cổng/duyệt “tiếp tục” tại điểm 2 mới sang Phase 5.
