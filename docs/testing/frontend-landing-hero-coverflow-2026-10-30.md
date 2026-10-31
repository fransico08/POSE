# Hero coverflow theo ngành — DEC-06

Owner: **Huỳnh Minh Tài**. Quyết định ngày **30/10/2026**, thực hiện và kiểm ngày **31/10/2026**, tại checkout `D:\POSE`, nhánh `Tài`. Tên file/thư mục ảnh giữ ngày quyết định theo yêu cầu. Mốc trước thay đổi: `880a41b`; source nghiệm thu: `e2dd376`.

## Phạm vi và giới hạn accessibility

Hero dùng sáu khung ngành, nền thumbnail blur/crossfade, chữ chính/H1/CTA và danh sách tương đương sr-only vẫn render server. Desktop có perspective; mobile chỉ crossfade. Bỏ picker và nút dừng của hero, marquee, RoleTabs. Dừng tạm khi hover/focus trong vùng, ra viewport hoặc tab ẩn; RoleTabs ngừng tự chuyển đến reload sau lựa chọn thủ công. Reduced-motion hiện Giáo dục tĩnh, không timer/3D. Không sửa auth/app/font/token, không thêm package hoặc inlineCss.

**Ngoại lệ WCAG 2.2.2 mức A được Tài chấp nhận tại [DEC-06](../meetings/decision-log.md#dec-06--hero-coverflow-và-chuyển-động-landing).** Hover/focus/reduced-motion chỉ giảm thiểu; không thay đầy đủ một cơ chế dừng độc lập. Không tuyên bố landing đạt toàn bộ WCAG 2.2 AA dựa vào Lighthouse100.

## Môi trường và kiểm sạch

Windows, Node **24.13.0**, npm **11.6.2**, Next **16.4.3**, React/React DOM **19.3.0**, Chrome headless **141**, Lighthouse **13.5.0**. Production `next start` ở loopback trên bản sao source từ git archive, không dùng dev server để nghiệm thu. `SITE_URL` loopback chỉ cho phép đo local; không commit `.env`, profile, log hoặc report Lighthouse đầy đủ.

Archive ban đầu không có node_modules/.next/.env; `npm ci` đạt theo lockfile. Trong cùng archive, cập nhật source các fix cuối bằng git archive, giữ node_modules đã cài với lockfile không đổi; chạy lại toàn bộ lint/typecheck/test và hai build trên source cuối. Không nhận đây là nhiều lần npm ci độc lập.

| Kiểm | Kết quả thực chạy |
|---|---|
| npm ci | Đạt trên archive sạch |
| next typegen, lint, typecheck | Đạt, lint không warning |
| Test trước → sau | **124 → 129/129**, 18 file; giữ **66 ca foundation** |
| Build1 và build2 | Đạt liên tiếp; root **○ /** |
| npm audit --omit=dev | **0** ở mọi mức, exit0 |
| Package/lockfile/font/app/auth | Không thay đổi |

Test mới/cập nhật kiểm chu kỳ5s, hover/focus và tiếp tục, visibility/viewport, reduced-motion không timer, sr-only đủ6 ngành, không Tab stop trong minh họa, không nút dừng landing, khóa RoleTabs sau click/phím. Kiểm SSR thật chặn React19 tự sinh preload cho ảnh eager; `fetchPriority="low"` giữ tải ảnh không tranh ưu tiên với chữ. Test glyph/hash/font và semantic/credentials cũ giữ nguyên.

Lệnh kiểm: `npm ci`, `npx next typegen`, `npm run lint`, `npm run typecheck`, `npm test`, `npm run build` hai lần, `npm audit --omit=dev --json`. Đo bằng `npx --yes lighthouse@13.5.0 http://127.0.0.1:3003/ --output=json --output-path=<thư-mục-tạm> --chrome-flags="--headless=new --no-sandbox" --only-categories=performance,accessibility,best-practices,seo --quiet`; desktop thêm `--preset=desktop`. Report đầy đủ giữ ở thư mục tác vụ bị git ignore, không commit.

MockScene không còn được production import, vẫn được test dùng và **giữ file** theo yêu cầu. PipelineBoard, Customer360Card, DealCard tiếp tục dùng ở các khối khác/trang đăng nhập. Không tự xóa file mock.

## Ảnh và nguồn để Tài duyệt

Năm file JPEG thật (`FF D8 FF`), Pillow đọc được **1200×1600**, mỗi file dưới600KB. Tải qua link Download chính thức từ trang Unsplash miễn phí; không dùng Unsplash+. Các tham số crop/resize nằm ngay trên redirect Download; không tải từ Google Images/Pinterest. Đã kiểm trực quan bản crop: không mặt nhận diện rõ, logo hoặc chữ đọc được. Giấy phép: [Unsplash License](https://unsplash.com/license). Chi tiết URL Download, chỉnh sửa, byte và SHA-256: [SOURCES.txt](../../frontend/src/assets/landing/frames/SOURCES.txt).

| Ngành | Tác giả, trang nguồn | Giấy phép | File gốc | Bản trong hero |
|---|---|---|---:|---|
| Giáo dục | [Spencer Liao](https://unsplash.com/photos/empty-chairs-with-desks-in-classroom-r-fQhUkbMsg) | Unsplash License | 143.453 byte | [Xem ảnh](assets/frontend-landing-hero-coverflow-2026-10-30/frame-education.png) |
| Bất động sản | [Nha Chill](https://unsplash.com/photos/a-bright-and-airy-living-room-with-modern-decor-ZuHIek5xn_I) | Unsplash License | 363.734 byte | [Xem ảnh](assets/frontend-landing-hero-coverflow-2026-10-30/frame-property.png) |
| Phần mềm/SaaS | [Lorin Both](https://unsplash.com/photos/a-hand-is-holding-a-laptop-_yiETIOBms4) | Unsplash License | 171.251 byte | [Xem ảnh](assets/frontend-landing-hero-coverflow-2026-10-30/frame-software.png) |
| Bán lẻ/F&B | SVG quầy/cốc dựng trong HeroFrames.tsx | Minh họa nội bộ | Không tải ảnh | [Xem ảnh](assets/frontend-landing-hero-coverflow-2026-10-30/frame-retail.png) |
| Logistics | [Brian Wangenheim](https://unsplash.com/photos/a-long-row-of-shelves-in-a-warehouse-1Elnip2SeM8) | Unsplash License | 256.737 byte | [Xem ảnh](assets/frontend-landing-hero-coverflow-2026-10-30/frame-logistics.png) |
| Phòng khám | [Liana S](https://unsplash.com/photos/empty-dental-chair-and-equipment-in-a-room-vVLhkrrdV54) | Unsplash License | 130.698 byte | [Xem ảnh](assets/frontend-landing-hero-coverflow-2026-10-30/frame-clinic.png) |

Bán lẻ/F&B: sau khoảng ba lượt tìm, ứng viên kiểm thực tế có chữ/logo hoặc mặt người; áp dụng fallback SVG được cho phép, không hạ tiêu chí và không giữ ảnh bị loại trong repo. **Bộ hình chờ Tài duyệt, có thể yêu cầu thay trước khi đóng việc.** Câu minh họa sáu ngành được truy nguồn trong landing-content-v2.md, không hứa module chuyên ngành.

## Lighthouse — giữ đủ hai chuỗi

Trước đo đã dừng dev server, build và Chrome profile riêng do tác vụ mở; chỉ giữ production server. Không tắt tiến trình người dùng. Snapshot CPU nền trong2s còn Chrome người dùng, AWCC.UCSubAgent, nvcontainer, claude, Code, TextInputHost, audiodg; cao nhất khoảng0,172 CPU-second/tiến trình, không có build khác của tác vụ. Mỗi lượt Lighthouse dùng profile sạch, chạy tuần tự; không đo browser acceptance đồng thời.

**Chuỗi đầu source8f11c69, card mobile132px:** điểm số đạt nhưng **không đạt B6 vì ảnh là LCP mobile**. Giữ đủ kết quả, không loại lượt xấu. A/BP/SEO100 mọi lượt, DOM1048, ảnh30.224 byte transfer, JS152.973 byte.

| Lượt | Performance | LCP(s) | FCP(s) | TBT(ms) | CLS | benchmarkIndex |
|---|---:|---:|---:|---:|---:|---:|
| Mobile1 | 86 | 3,959 | 1,816 | 69 | 0,01467 | 2738,5 |
| Mobile2 | 87 | 3,894 | 1,815 | 37 | 0,01467 | 3093 |
| Mobile3 | 87 | 3,895 | 1,815 | 37,5 | 0,01467 | 2959,5 |
| Mobile4 | 87 | 3,895 | 1,816 | 37 | 0,01467 | 2995 |
| Mobile5 | 87 | 3,899 | 1,815 | 40 | 0,01467 | 3103,5 |
| Desktop | 100 | 0,798 | 0,460 | 0 | 0,00291 | 3067,5 |

Trung vị/min–max mobile đầu: P87(86–87), LCP3,895(3,894–3,959)s, FCP1,815(1,815–1,816)s, TBT37,5(37–69)ms, CLS0,01467 không đổi. benchmarkIndex2738,5–3103,5, chênh13,33%. Mobile LCP selector `.coverflow-image > img`; desktop đã là chữ H1.

**Sửa đúng B6:** giảm card mobile132→96px và sizes tương ứng; giữ desktop176px/tablet148px, toàn bộ chữ/font/timer. Không đổi weight, preload font, package hoặc config. Source cuối `e2dd376` qua kiểm lại129test/build2/audit0 trước chuỗi sau.

| Lượt cuối | Performance | A/BP/SEO | LCP(s) | FCP(s) | TBT(ms) | CLS | benchmarkIndex |
|---|---:|---|---:|---:|---:|---:|---:|
| Mobile1 | 87 | 100/100/100 | 3,924 | 1,830 | 35 | 0,01467 | 3125,5 |
| Mobile2 | 87 | 100/100/100 | 3,890 | 1,817 | 34 | 0,01467 | 3034,5 |
| Mobile3 | 87 | 100/100/100 | 3,901 | 1,815 | 40,5 | 0,01467 | 3001,5 |
| Mobile4 | 87 | 100/100/100 | 3,890 | 1,817 | 33,5 | 0,01467 | 2969,5 |
| Mobile5 | 87 | 100/100/100 | 3,895 | 1,815 | 37,5 | 0,01467 | 3100,5 |
| **Mobile trung vị** | **87** | **100/100/100** | **3,895** | **1,817** | **35** | **0,01467** | **3034,5** |
| Mobile min–max | 87–87 | 100 | 3,890–3,924 | 1,815–1,830 | 33,5–40,5 | 0,01467–0,01467 | 2969,5–3125,5 |
| Desktop1 | **100** | **100/100/100** | 0,801 | 0,461 | **0** | **0,00291** | 3041 |

benchmarkIndex mobile cuối chênh **5,25%** (max/min−1), không có dấu hiệu cần đo lại cả chuỗi do lệch trên20%. Không cảnh báo Lighthouse/runtime error. **Đạt cổng lần này:** P≥85, TBT<250ms, CLS<0,1, A/BP/SEO100 và desktop≥95.

**LCP cuối cả6lượt là `h1#hero-title > span`, chữ “Trên một nền tảng.”**, không phải ảnh. Document preload ba WOFF2 Fraunces song song với CSS; không thêm ảnh vào chuỗi ưu tiên. [Mốc trước30/10](frontend-fraunces-self-hosted-2026-10-30.md): mobileP89, LCP3,600s, TBT36,5ms, CLS0,0172, desktop100. Mới: P87, LCP3,895s, TBT35ms, CLS0,01467. Đây là so sánh hai lượt lab khác thời điểm, không kết luận A/B nhân quả; LCP mô phỏng tiếp tục theo dõi bằng dữ liệu thực khi có domain.

JS transfer147.103→**152.973 byte** (+5870, dưới20KiB); DOM **1048**, dưới1200. Lighthouse mobile tải đủ5JPEG qua next/image: **28.118 byte resource /30.224 byte transfer**, dưới150.000 byte kể cả header. RetailSVG không request; blur thumbnail inline không fetch ảnh nền riêng. LCP là chữ vẫn tải nguyên bộ Fraunces81.736byte, không thử font-display optional hoặc font khác.

## Trình duyệt, responsive và tương phản

Phiên không có Chrome DevTools MCP. Dùng Chrome headless thật qua CDP có sẵn, profile riêng: thao tác chuột/phím/focus/scroll native; Runtime chỉ đọc DOM/style/network, không chèn handler hoặc sửa state để tạo ảnh. Không đọc profile/cookie của người dùng, không cài công cụ/package mới vào dự án.

Kiểm chạy thật: 1440,768,375,320px không tràn ngang; H1 opacity1/animation none; desktop có 3D, mobile không 3D; reduced-motion cả bốn cỡ không animation/timer tự chạy. Hover và focus xuyên hai CTA giữ cùng khung; rời vùng tiếp tục. Chuyển tab Chrome thật dừng animation, quay lại phục hồi. Marquee hover dừng và rời vùng chạy lại. RoleTabs chọn bằng phím, blur và chờ hơn6s vẫn giữ lựa chọn; Home/End đúng. Tab đi qua Đăng nhập → Xem cách hoạt động, không lọt vào minh họa; sr-only đủ6 ngành theo thứ tự. Zoom native200% không tràn. Console và exception trống.

Nền dùng blurDataURL nhỏ của static import, không phát sinh request ảnh nền lớn. Chỉ foreground Giáo dục eager; năm khung còn lại mount sau idle/in-view rồi lazy (SVG không request). Không link preload ảnh, không ảnh fetchPriority high. Fraunces tự host giữ nguyên; kiểm H1 native dùng custom font. Native mobile DPR1 tải đủ5JPEG: 7008byte resource/8508byte transfer; Lighthouse mobile DPR cao hơn có số30.224byte transfer đã ghi phía trên.

Tương phản tính sRGB theo CSS thực: overlay navy có alpha thấp nhất0,90, xét cả nguồn ảnh trắng hoàn toàn ở điểm sáng nhất (các ảnh nội thất/phần mềm/kho/phòng khám có pixel trắng). Nền tổng hợp bảo thủ RGB(35,4;41,7;54,3), kể cả lúc crossfade. Trắng **14,45:1**, chữ phụ #CBD5E1 **9,73:1**, amber #F59E0B **6,73:1**. Nhãn khung giữa có lớp navy0,90 tương tự; khung bên mờ là trang trí aria-hidden. CTA giữ số đo thiết kế trước **4,66:1** ở điểm sáng nhất. Cột coverflow được isolation/clip để ảnh không che vùng chữ.

| Viewport | Đang chạy | Reduced-motion |
|---|---|---|
| Desktop1440 | [Ảnh](assets/frontend-landing-hero-coverflow-2026-10-30/hero-1440-running.png) | [Ảnh](assets/frontend-landing-hero-coverflow-2026-10-30/hero-1440-reduced.png) |
| Tablet768 | [Ảnh](assets/frontend-landing-hero-coverflow-2026-10-30/hero-768-running.png) | [Ảnh](assets/frontend-landing-hero-coverflow-2026-10-30/hero-768-reduced.png) |
| Mobile375 | [Ảnh](assets/frontend-landing-hero-coverflow-2026-10-30/hero-375-running.png) | [Ảnh](assets/frontend-landing-hero-coverflow-2026-10-30/hero-375-reduced.png) |
| Mobile320 | [Ảnh](assets/frontend-landing-hero-coverflow-2026-10-30/hero-320-running.png) | [Ảnh](assets/frontend-landing-hero-coverflow-2026-10-30/hero-320-reduced.png) |

[Zoom200%](assets/frontend-landing-hero-coverflow-2026-10-30/hero-zoom-200.png). Sáu ảnh trạng thái ngành trong bảng nguồn thể hiện một vòng tự chuyển thật. Lượt reflow mobile kiểm ở chiều cao812px; ảnh dài chụp hết hero/dải ngành dùng viewport cao hơn, chờ hết transition mà không sửa state hoặc tắt animation để tạo ảnh.

## File và commit

| Nhóm | File thay đổi |
|---|---|
| Hero/motion | frontend/src/components/landing/HeroFrames.tsx, HeroMotion.tsx, HeroMotionScope.tsx, HeroSection.tsx, RotatingPhrase.tsx, IndustryMarquee.tsx, RoleTabs.tsx; frontend/src/lib/motion/interaction.ts |
| Nội dung/CSS | frontend/src/content/landing.ts; frontend/src/styles/landing-v2.css |
| Ảnh nguồn | frontend/src/assets/landing/frames/{education,property,software,logistics,clinic}.jpg và SOURCES.txt; retailSVG trong HeroFrames.tsx |
| Test | frontend/tests/landing-concepts.test.tsx; landing-k3.test.tsx; micro-interactions.test.tsx |
| Tài liệu | AGENTS.md; docs/AI-USAGE.md; docs/meetings/decision-log.md; docs/design/ui-ux/landing-content-v2.md, landing-wireframe-v2.md; frontend/README.md; evidence này |
| Bằng chứng ảnh | 15 PNG tại assets/frontend-landing-hero-coverflow-2026-10-30, đã liên kết trong các bảng trên |

Các commit triển khai trước commit evidence: `8b5c60f` (DEC-06/AI log), `5e82d81` (coverflow/ảnh), `50b92f4` (bỏ pause controls), `f3ec38f` (test motion/manual tab), `8e62b80` (cô lập khung ảnh), `8f11c69` (chặn React image preload), `e2dd376` (giảm khung mobile để giữ text LCP). Author/committer fransico08, local trên Tài, không trailer AI. Các file untracked có sẵn của Tài được giữ nguyên, không đưa vào commit tác vụ.

## Phần chưa kiểm

Chưa kiểm screen reader với người dùng thật, thiết bị chạm vật lý hoặc Safari/Firefox. Chưa đo field data trên domain triển khai. Không chạy lại backend/DB vì không sửa auth/app/session; 66 test foundation đạt, không coi đó là một lượt hồi quy backend thật mới. Audit đầy đủ dev dependency không thuộc cổng lần này; không nhận đã sửa finding dev-only từ evidence trước. Không push/PR/Trello và không đụng handoff/dữ liệu gốc.
