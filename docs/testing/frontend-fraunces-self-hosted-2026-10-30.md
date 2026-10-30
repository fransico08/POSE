# Đợt 3 — khóa Fraunces tự host và nghiệm thu lại, 30/10/2026

Owner **Huỳnh Minh Tài**. Tiếp tục phần đang dở từ 25/10, giữ Next.js/eslint-config-next **16.4.3**. Source font **159f485**, source kiểm sạch/đo **bec3a95**. **Đạt cổng chính: Performance mobile trung vị 89, TBT 36,5 ms, A/BP/SEO 100, CLS 0,0172; desktop 100. Đủ điều kiện đóng đợt 3 theo quyết định cuối của Tài.** Không cần dùng mức ngoại lệ 85/250; LCP mobile vẫn là chỉ số theo dõi theo duyệt trước.

## Phát hiện của reviewer và phạm vi sửa

Reviewer báo A/B xen kẽ cùng phiên Lighthouse 13.5.0 mobile, 4 vòng: Next 16.4.0 trung vị P 85,5/LCP 3,73 s; Next 16.4.3 P 80,5/LCP 4,06 s, benchmarkIndex tương đương. Đây là số reviewer cung cấp trong yêu cầu, không phải chuỗi do tác vụ này tự đo lại. CSS @font-face giống nhưng binary Google trả lúc build khác: tổng **81.736 → 114.324 byte**, vượt 81.920; vietnamese **11.536 → 36.536**, latin-ext **33.640 → 46.460**. Không thể chỉ dựa vào CSS hoặc pin phiên bản Next để khóa byte của font tải qua Google lúc build.

Giữ bản vá Next 16.4.3, đổi riêng nguồn Fraunces sang ba binary đã nghiệm thu. Không tải lại Fraunces từ Google, không subset/convert file, không đổi weight/font/thiết kế, không inlineCss/package mới. Be Vietnam Pro của app vẫn dùng cấu hình next/font/google cũ. Backend/session/BFF/proxy/form login và token app không đổi.

## Nguồn, giấy phép và checksum

Tài bàn giao tại `D:\POSE\handoff\fraunces-700-800-known-good`. Kiểm SHA-256 theo manifest nguồn trước copy, xác nhận lại khi tiếp tục ngày 30/10; chỉ đổi tên file cho dễ đọc. Font name table **Version 1.000;[b76b70a41]**. Thư mục handoff không sửa/xóa.

| File trong frontend/public/fonts/fraunces | Byte | SHA-256 |
|---|---:|---|
| fraunces-vietnamese.woff2 | 11.536 | 250cc2966c658fb6d336731de9d82a8129025e9839c20c253bbc477852f6cf4f |
| fraunces-latin-ext.woff2 | 33.640 | f1451edd6434085c4f9f3a8b4a674182dd7d6acccf53bfced19fd167f0705a06 |
| fraunces-latin.woff2 | 36.560 | 88e17be075f1be50ab67b057b99e3701b828f44ed28f9452df6c02645bb0cba9 |
| **Tổng** | **81.736 / 81.920** | Ba file khớp nguồn và manifest repo |

Giấy phép **SIL OFL 1.1** đặt cạnh font, lấy từ [repo Fraunces chính thức, revision 284bc5ea73b1233a05d6ec644e641b9404cba8a0](https://github.com/undercasetype/Fraunces/blob/284bc5ea73b1233a05d6ec644e641b9404cba8a0/OFL.txt). Nguồn/phiên bản/SHA và cách duy trì ở [README font](../../frontend/public/fonts/fraunces/README.md), manifest [SHA256SUMS.txt](../../frontend/public/fonts/fraunces/SHA256SUMS.txt).

## Triển khai và test chặn tái phát

`landing-fraunces.css` có **đúng sáu rule Fraunces** (normal 700/800 × ba subset), unicode-range sao chép nguyên văn từ CSS Next 16.4.0 trong handoff; fixture giữ nguyên bản để so sánh. font-display:swap. Rule thứ bảy chỉ dành cho **Fraunces Fallback**: local Times New Roman, ascent 84.71%, descent 22.09%, line-gap 0%, size-adjust 115.45%. `--landing-display-font` trỏ `"Fraunces", "Fraunces Fallback", serif`. Không import Fraunces từ next/font/google.

HomePage server route `/` gọi react-dom preload cho đúng ba URL, as=font/type=font/woff2/crossOrigin=anonymous; không đặt trong root layout. Landing CSS chỉ được import ở landing. H1 vẫn 800, H2 vẫn 700, không variable/axes.

Bổ sung **5 test**: bốn ca kiểm file/hash/manifest/byte budget/sáu unicode-range/metrics trong fraunces-assets.test.ts và một ca ba preload đúng options trong landing.test.tsx. Manifest và hash kỳ vọng độc lập trong test để không chấp nhận thay binary chỉ bằng sửa manifest. Giữ test semantic, credentials, motion và glyph production. Tổng **124 test/18 file**: 66 foundation +50 landing/motion/micro +3 sidebar +5 font/preload.

## Kiểm sạch và production

Bản sao git archive sạch không node_modules/.next/.env ban đầu, source bec3a95. Bộ log kiểm ngày 25/10/10 đã hoàn tất trước khi tác vụ bị ngắt; tiếp tục ngày 30/10/10 đối chiếu **từng file archive với git blob HEAD**, khớp toàn bộ (so với blob để không nhầm CRLF của checkout với LF trong archive). Không build lại hoặc đổi source giữa các chuỗi đo. Giữ kết quả đã làm, không tuyên bố đây là ba lần kiểm sạch mới.

| Check | Kết quả |
|---|---|
| npm ci | Đạt trên archive sạch, đúng lockfile |
| next typegen / lint / typecheck | Đạt |
| Toàn bộ test | **124/124**, 18 file |
| Build1 và build2 liên tiếp | Đạt Next 16.4.3/Turbopack, root **○ /**, không SecurityFatalError |
| npm audit --omit=dev | **0** ngày 25/10, chạy lại ngày 30/10 vẫn **0**, exit0 |
| Audit đầy đủ ngày 25/10 | Còn 1 high undici qua dev dependency jsdom; không sửa ngoài phạm vi, không chạy lại audit đầy đủ ngày 30/10 |
| Glyph native production ngày 25/10 và ngày 30/10 | **11 nút chữ H1/H2** đều Fraunces custom qua CSS.getPlatformFontsForNode; không fallback hệ thống |
| Network production cache sạch | **3 request Fraunces /81.736 byte**, HTTP200, SHA đúng cả ba |
| Preload root | Đúng3, type/as/crossorigin đúng |
| Preload login/app | **0/0**; app dùng cờ hiện diện phiên chỉ để lấy HTML, không giả đăng nhập backend |
| Runtime glyph check | 0 exception; Chrome riêng đã đóng trước đo Lighthouse |

Lệnh tái lập (làm trong archive, báo cáo đầy đủ chỉ giữ ở vùng ignored):

```powershell
npm ci
npm exec -- next typegen
npm run lint
npm run typecheck
npm test
$env:SITE_URL='http://127.0.0.1:3003'
npm run build
npm run build
npm audit --omit=dev --json
npm start -- --hostname 127.0.0.1 --port 3003
npx --yes lighthouse@13.5.0 http://127.0.0.1:3003/ --output=json --output-path=<vùng tạm>/<lượt>.json --chrome-flags="--headless=new --no-sandbox" --only-categories=performance,accessibility,best-practices,seo --quiet
# Desktop: thêm --preset=desktop
```

## Phương pháp và chuỗi Lighthouse

Node 24.13.0/npm 11.6.2, Next 16.4.3/React 19.3.0/Vitest 5.0.4, Chrome 141.0.7390.123, Lighthouse 13.5.0. Production next start trên loopback 3003; không dev/build/test/glyph Chrome chạy đồng thời lúc đo. Mỗi lượt profile/cache sạch, chạy tuần tự. Không có Chrome DevTools MCP: dùng Chrome headless/profile riêng qua CDP; Runtime chỉ đọc, không dùng profile cá nhân.

Đã kiểm các tiến trình do tác vụ mở: trước đo chỉ còn supervisor và Node production; các Chrome kiểm glyph đã đóng. Không dừng tiến trình người dùng. Snapshot CPU 2 giây ngày 30/10 còn BackgroundDownload 0,7344 CPU-s, nvcontainer 0,1875, Chrome 0,1719/0,1094, audiodg 0,1094, PowerShell 0,1094, AWCC.UCSubAgent 0,0781, Code 0,0469 và claude 0,0156. Snapshot không bảo đảm tải nền giữ nguyên trong chuỗi.

Giữ đầy đủ chuỗi ngày 25/10: benchmarkIndex chênh **94,5%**. Chuỗi tiếp tục 30-A vẫn chênh **100,3%** vì lượt 1 = 1.529, các lượt sau 2.859,5–3.063; dù trung vị P 89, không dùng chuỗi đó riêng để nghiệm thu. Theo quy tắc >20%, đo lại **toàn bộ** chuỗi 30-B trên cùng build, không sửa code/chọn lượt/loại lượt thấp. Chuỗi 30-B chênh **15,49%**, đủ độ tin cậy đã chốt. Cả ba chuỗi có 7 lượt mobile + 1 desktop; không có chuỗi tối ưu code bổ sung.

Mỗi ô là **trung vị (min–max)**, LCP/FCP giây, TBT ms. A/BP/SEO **100 trong toàn bộ 21 lượt mobile và 3 desktop**; mọi lượt tải đúng 3 Fraunces / 81.736 byte.

| Chuỗi mobile | Performance | LCP(s) | FCP(s) | TBT(ms) | CLS | benchmarkIndex |
|---|---:|---:|---:|---:|---:|---:|
| 25/10 | 84 (79–89) | 3.850 (3.596–3.928) | 1.817 (1.812–2.314) | 175.0 (35.5–273.5) | 0.0172 (0.0000–0.0172) | 2278.0 (1468.0–2855.5) |
| 30-A | 89 (84–89) | 3.609 (3.599–3.863) | 1.813 (1.812–1.815) | 41.0 (36.5–183.5) | 0.0172 (0.0000–0.0172) | 2973.0 (1529.0–3063.0) |
| 30-B nghiệm thu | 89 (89–89) | 3.600 (3.581–3.623) | 1.813 (1.810–1.815) | 36.5 (30.0–48.0) | 0.0172 (0.0000–0.0172) | 3057.0 (2750.5–3176.5) |

| Chuỗi | Lượt | P | LCP(s) | FCP(s) | TBT(ms) | CLS | benchmarkIndex |
|---|---:|---:|---:|---:|---:|---:|---:|
| 25/10 | 1 | 82 | 3.928 | 1.839 | 228 | 0.0172 | 1468 |
| 25/10 | 2 | 89 | 3.596 | 1.812 | 35.5 | 0.0172 | 2770 |
| 25/10 | 3 | 88 | 3.712 | 1.817 | 105 | 0.0000 | 2210 |
| 25/10 | 4 | 79 | 3.892 | 2.314 | 273.5 | 0.0000 | 1573.5 |
| 25/10 | 5 | 84 | 3.854 | 1.814 | 175 | 0.0172 | 2571.5 |
| 25/10 | 6 | 79 | 3.725 | 2.303 | 250 | 0.0000 | 2855.5 |
| 25/10 | 7 | 85 | 3.850 | 1.817 | 168 | 0.0172 | 2278 |
| 25/10 | Desktop1 | 100 | 0.814 | 0.460 | 5 | 0.0117 | 2426 |
| 30-A | 1 | 84 | 3.863 | 1.815 | 183.5 | 0.0000 | 1529 |
| 30-A | 2 | 89 | 3.609 | 1.813 | 42 | 0.0172 | 2998.5 |
| 30-A | 3 | 89 | 3.603 | 1.815 | 36.5 | 0.0172 | 3063 |
| 30-A | 4 | 89 | 3.610 | 1.814 | 41 | 0.0000 | 2859.5 |
| 30-A | 5 | 89 | 3.611 | 1.813 | 42.5 | 0.0172 | 3023 |
| 30-A | 6 | 89 | 3.602 | 1.812 | 39 | 0.0172 | 2973 |
| 30-A | 7 | 89 | 3.599 | 1.813 | 36.5 | 0.0172 | 2957 |
| 30-A | Desktop1 | 100 | 0.780 | 0.460 | 0 | 0.0002 | 2875 |
| 30-B nghiệm thu | 1 | 89 | 3.601 | 1.813 | 37.5 | 0.0172 | 2961 |
| 30-B nghiệm thu | 2 | 89 | 3.600 | 1.815 | 35 | 0.0000 | 2750.5 |
| 30-B nghiệm thu | 3 | 89 | 3.600 | 1.813 | 37 | 0.0172 | 3004.5 |
| 30-B nghiệm thu | 4 | 89 | 3.623 | 1.813 | 48 | 0.0172 | 3081.5 |
| 30-B nghiệm thu | 5 | 89 | 3.585 | 1.813 | 30 | 0.0172 | 3065.5 |
| 30-B nghiệm thu | 6 | 89 | 3.599 | 1.813 | 36.5 | 0.0172 | 3057 |
| 30-B nghiệm thu | 7 | 89 | 3.581 | 1.810 | 30 | 0.0172 | 3176.5 |
| 30-B nghiệm thu | Desktop1 | 100 | 0.771 | 0.455 | 0 | 0.0117 | 3073 |

## Đối chiếu cổng và CLS

Chuỗi ổn định 30-B: **P 89 (89–89) ≥ 88**, **TBT 36,5 (30–48) ms < 200**, A/BP/SEO 100, CLS 0,017185 (0–0,017185) <0,1; desktop **100/100/100/100**, LCP 0,771 s / FCP 0,455 s / TBT 0 / CLS 0,011677. Đạt **cổng chính**, không dùng mức ngoại lệ P≥85/TBT<250.

CLS mobile trung vị/max vẫn **0,017185**, bằng mốc nghiệm thu 14/10; desktop **0,011677** cũng bằng mốc trước. Khóa metric fallback giữ ổn định bố cục trong các lượt đã đo; không suy ra mọi thiết bị đều có CLS giống nhau. FCP 30-B 1,813 s so với 14/10 1,668 s được ghi đầy đủ, không tuyên bố mọi metric đều cải thiện.

LCP mobile mô phỏng **3,600 s (3,581–3,623)**, vẫn lớn hơn 2,5 s; giữ ngoại lệ đã duyệt. Phần tử LCP 30-B lượt 4 là span cuối H1 “Trên một nền tảng.”; trace gốc TTFB 11,164 ms/render delay 314,888 ms, khác LCP mô phỏng 3,623 s. Chuỗi document/preload → ba WOFF2 self-host là nguồn font của chữ; không ảnh LCP. Đo dữ liệu thực với SITE_URL/domain triển khai và thiết bị/mạng thật khi có.

Lighthouse 30-B lượt 4: JS transfer **147.103 byte**, DOM **1.092**, vẫn dưới ngân sách tăng 20 KiB so với 149.775 và DOM 1.200. Full JSON/trace/log, environment và profile giữ trong backend/target ignored, không commit. Performance còn bị trừ bởi LCP/FCP/interactive và network dependency tree; A/BP/SEO không audit nào chưa đạt.

## Ảnh và giới hạn kiểm

[H1 tiếng Việt](assets/frontend-fraunces-2026-10-30/fraunces-h1-desktop.png), [H2 tiếng Việt](assets/frontend-fraunces-2026-10-30/fraunces-h2-desktop.png), production/reduced-motion để thấy nét cuối. Chỉ dữ liệu minh họa trung tính. Nhãn CSS không là bằng chứng duy nhất: CDP xác nhận glyph thực dùng font custom và network/hash cùng đúng.

Không chạy lại backend 5 vai trò trong lượt đổi font vì không sửa session/BFF/backend; bằng chứng 14/10 đầy đủ 5 vai trò và smoke 16.4.3 ngày 25/10 vẫn được giữ, không nhận là kết quả mới ngày 30/10. Chưa screen reader, thiết bị vật lý/browser khác, Rich Results URL thật hoặc dữ liệu Core Web Vitals thực; không tuyên bố toàn bộ WCAG 2.2 AA chỉ từ Lighthouse. Handoff giữ nguyên, không push/merge/PR/Trello. Đã dừng Node/Chrome do lượt kiểm này mở.

## Commit

| Hash | Mô tả |
|---|---|
| 159f485 | fix(frontend): pin self-hosted Fraunces subsets and landing-only preloads |
| bec3a95 | test(frontend): guard Fraunces checksums budget and fallback metrics |

Commit tài liệu/ảnh tiếp theo ghi kết quả đóng đợt; chỉ commit local trên Tài, author/committer fransico08, không Co-authored-by. Không xóa D:\POSE\handoff.
