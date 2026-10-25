# Review đợt 3 — bản vá Next.js, ngày 25/10/2026

Owner: Huỳnh Minh Tài. **Bảo mật/build/chức năng đạt; xác nhận không tụt hiệu năng mobile chưa đạt. Chưa đề nghị đóng đợt.** Không push/merge/PR hoặc đổi Trello.

## Phạm vi và source kiểm chứng

- Worktree `C:\Users\Dell\.codex\worktrees\pose-tai-auth\POSE`, nhánh `Tài`, sạch trước khi làm. Baseline **c8b20a8**; bản vá **34dd309**, source sạch gồm sidebar/test **c29be3b**.
- Commit riêng đúng yêu cầu: `chore(frontend): upgrade Next.js to 16.4.3 for security advisories`.
- Direct dependency next và dev dependency eslint-config-next đổi 16.4.0 → **đúng 16.4.3**. Lockfile đổi phiên bản của 10 package `@next/*` đi kèm (env, eslint-plugin-next, 8 SWC platform) cùng hai package trên. So toàn bộ lockfile không có package khác đổi phiên bản/thêm/xóa. Sharp giữ phiên bản cũ dù range optional của Next được cập nhật; npm có chuẩn hóa peer metadata. Không dùng npm audit fix/--force.
- Sidebar truyền `--active-index` dạng number có kiểu CSSProperties, stylesheet giữ translateY. Opacity/aria-hidden/aria-current và reduced-motion giữ nguyên. Thêm 3 ca RTL: active, route ngoài menu, bàn phím khi thu gọn. Không tạo route giả.
- Node **24.13.0**, npm **11.6.2**, Vitest **5.0.4**, Lighthouse **13.5.0**, Chrome **141.0.7390.123**, Windows. Backend dùng JDK 25.0.2 và JAR đã nghiệm thu; source backend không đổi từ review foundation. PostgreSQL 18.3 cô lập.

## Audit trước/sau

Audit lấy trực tiếp npm registry trong lượt này. Số dưới là **package bị ảnh hưởng**, không phải số advisory. Next gồm 2 critical + 1 high; undici gồm 1 high + 2 moderate, npm tổng hợp severity cao nhất của package.

| Lệnh | Trước: critical / high / tổng | Sau: critical / high / tổng | Exit trước → sau |
|---|---|---|---|
| npm audit --omit=dev --json | 1 / 0 / 1 (next) | **0 / 0 / 0** | 1 → **0** |
| npm audit --json | 1 / 1 / 2 (next, undici) | **0 / 1 / 1** (undici) | 1 → 1 |

Baseline audit với --package-lock-only trên archive c8b20a8, không lấy số historical 5 high của 14/10 làm số hiện tại. Audit sau dùng node_modules cài mới của archive c29be3b. Không gọi audit full là sạch vì exit vẫn 1.

### Next trước vá

Range audit 16.0.0–16.4.0. Sau nâng 16.4.3, cả ba không còn trong audit:

| Advisory | Severity | Nội dung |
|---|---|---|
| [GHSA-2464-fhr4-w9xw](https://github.com/advisories/GHSA-2464-fhr4-w9xw) | critical | RCE Image Optimization API khi dùng SVG |
| [GHSA-673q-mjm6-r5xr](https://github.com/advisories/GHSA-673q-mjm6-r5xr) | critical | RCE use cache với typed arrays |
| [GHSA-p3m5-59mp-jv5r](https://github.com/advisories/GHSA-p3m5-59mp-jv5r) | high | DoS App Router Server Actions |

Source production không dùng/import next/image. Mock trong tests/landing.test.tsx không phải production usage. next.config.ts không bật images.dangerouslyAllowSVG; `.next/required-server-files.json` xác nhận **dangerouslyAllowSVG=false**. Icon SVG phục vụ tĩnh. Không có use cache/use server trong source. Các kiểm tra này không thay việc nâng patch; không chạy payload khai thác.

### Dev dependency còn lại — chưa nâng

`jsdom@29.1.1` (dev) → `undici@7.30.0` (lockfile dev=true), còn ba advisory:

| Advisory | Severity | Nội dung |
|---|---|---|
| [GHSA-xjj6-f33q-929c](https://github.com/advisories/GHSA-xjj6-f33q-929c) | **high** | DoS do error không xử lý khi content-encoding quá nhiều |
| [GHSA-4qv8-2p9j-2685](https://github.com/advisories/GHSA-4qv8-2p9j-2685) | moderate | EventSource parser dùng bộ nhớ không giới hạn |
| [GHSA-f72v-6w45-q472](https://github.com/advisories/GHSA-f72v-6w45-q472) | moderate | Bypass Subresource Integrity với algorithm token không lowercase |

Range chung report 7.0.0–7.30.0, fixAvailable=true. Không nâng undici/jsdom hoặc thêm override ngoài phạm vi. Không có advisory Next trong audit sau.

## Bản sao sạch và build liên tiếp

Git archive c29be3b, chỉ frontend tracked; không copy node_modules/.next/.env từ worktree. npm ci cài mới **448 package**, audit 449. SITE_URL=http://127.0.0.1:3003, BACKEND_URL=http://127.0.0.1:18081. Không đổi font/token/BFF/proxy.

| Check | Kết quả | Thời gian process |
|---|---|---:|
| npm ci | Đạt; cảnh báo dev undici high và deprecation ESLint hiện có | 129,68 s |
| npm exec -- next typegen | Đạt, tạo route type cho archive sạch | 4,63 s |
| npm run lint | Đạt, max-warnings=0 | 18,12 s |
| npm run typecheck | Đạt | 9,31 s |
| npm test | **119/119**, 17 file, không skip/lỗi | 35,76 s |
| npm run build lần 1 | Đạt, **○ /**, không SecurityFatalError | 54,93 s |
| npm run build lần 2 ngay sau lần 1 | Đạt, không SecurityFatalError | 15,58 s |

Lần 1 sạch .next, lần 2 dùng cache Next vừa tạo; build tuần tự, không chạy cùng dev trong một .next. 116 test cũ + 3 sidebar; Vitest báo riêng 33,20 s, không thay thời gian process ở bảng.

Lệnh tương đương đã chạy:

```powershell
git archive --format=tar --output=<artifact>/source.tar c29be3b frontend
tar -xf <artifact>/source.tar -C <artifact>
# Từ <artifact>/frontend, chưa có node_modules/.next/.env.local:
npm ci
$env:SITE_URL = 'http://127.0.0.1:3003'
$env:BACKEND_URL = 'http://127.0.0.1:18081'
npm exec -- next typegen
npm run lint
npm run typecheck
npm test
npm run build
npm run build
npm audit --omit=dev --json
npm audit --json
npm start -- --port 3003
```

## Production smoke và một vai trò backend thật

Next start **127.0.0.1:3003**, build lần 2. Backend riêng **127.0.0.1:18081**, DB restore mới **pose_v2_review_manual_security_20261025212106**, cluster worktree **127.0.0.1:54329**, role pose_auth_test. Process người dùng ở cổng 8080 không bị chạm/dừng.

Đã xác minh SHOW data_directory trước create/restore/query. Backup final SHA-256 **37994087124A2E38707B3C29853D58B9C0A0BCED959F7DC1875FD921340E48A6**, Flyway 1 baseline + 2 + 3 + 4. JWT secret ngẫu nhiên 32 byte chỉ trong môi trường process. Không sửa gói Nhi/credentials; fingerprint credentials trước/sau bằng nhau, không ghi giá trị fingerprint.

| Request | Status | Kiểm tra |
|---|---:|---|
| GET / | 200 | Một H1 |
| GET /dang-nhap | 200 | Trang login production |
| GET /app, chưa phiên | **307** | /dang-nhap?returnTo=%2Fapp |
| GET /api/session/me, chưa phiên | **401** | X-Robots-Tag: noindex, nofollow |
| GET /robots.txt, /sitemap.xml, /favicon.ico | 200 mỗi route | Metadata/icon production |
| GET /icon.svg, /apple-icon.png | 200 mỗi route | Icon tĩnh |
| POST /api/session/login | **200** | Quản lý EDU, MANAGER; không token JSON |
| GET /api/session/me | 200 | Đúng user, tenant edu, 23 permission |
| GET /app, có phiên | 200 | Proxy gate nhận cờ phiên |
| GET /api/backend/organization | 200 | Proxy backend thật, code edu |
| GET /api/backend/teams | 200 | Backend thật, quyền Manager |
| POST /api/session/refresh | 200 | Refresh qua backend |
| GET /api/session/me sau refresh | 200 | Phiên hoạt động |
| POST /api/session/logout | 200 | BFF dọn cookie, thu hồi backend |
| GET /api/session/me sau logout | **401** | Không còn phiên |

Tổng **18 request**, không tính readiness. Mật khẩu được cấp vào POSE_DEMO_PASSWORD của process kiểm; không log body/cookie/token. Cookie login có Secure/HttpOnly. Đây là **HTTP loopback**, cookie Secure chỉ giữ trong RAM và được client kiểm gửi thủ công; không tuyên bố chạy lại browser E2E nhập form bằng bàn phím. Route handlers/BFF/proxy chạy production, không mock backend. Backend/cluster riêng dừng trước Lighthouse; frontend đã dừng sau kiểm.

## Lighthouse — giữ đủ hai lượt được yêu cầu

Đúng **một lượt mỗi chế độ**, Lighthouse 13.5.0/Chrome 141, profile/cache cô lập do Lighthouse tạo. Không dev/build/test hoặc backend test chạy khi đo; không tắt process cá nhân. Mobile preset/throttling simulated mặc định (CPU ×4, throughput 1638,4 Kbps), desktop --preset=desktop. URL http://127.0.0.1:3003/ như trước.

| Mốc | P | A / BP / SEO | FCP (s) | LCP (s) | TBT (ms) | CLS | benchmarkIndex |
|---|---:|---|---:|---:|---:|---:|---:|
| Mobile 14/10, trung vị 7 lượt ổn định | **89** | 100/100/100 | 1,668 | 3,607 | 78 | 0,0172 | 2876 (2666–3026) |
| Mobile sau vá, **1 lượt** | **83** | **100/100/100** | 1,826 | 3,879 | **207,5** | 0,0172 | **1628** |
| Desktop 14/10, 1 lượt | 100 | 100/100/100 | 0,419 | 0,743 | 0 | 0,0117 | 2432 |
| Desktop sau vá, **1 lượt** | **100** | **100/100/100** | 0,466 | 0,805 | 9 | 0,0118 | 2080,5 |

Không runtimeError/runWarnings. LCP vẫn là span cuối H1 “Trên một nền tảng.” Mobile trace TTFB 18,707 ms/render delay 1434,785 ms; không đồng nhất với LCP simulated 3,879 s.

JS transferSize **147.103 byte**, so Lighthouse 14/10 **146.980 byte** (+123 byte). Không so lẫn transferSize với encoded/gzip native. Report mới có **3 stylesheet**, không chứa selector sidebar/login trong CSS landing. Source font/layout/motion landing không đổi; không tự bật inlineCss, đổi font hoặc tối ưu ngoài scope để che kết quả.

**Chưa xác nhận giữ cổng hiệu năng:** mobile 83 và TBT 207,5 ms không đạt P≥88/TBT<200; desktop giữ 100. BenchmarkIndex mobile thấp hơn vùng trước khoảng 39–46%, khiến phép so khó kết luận, nhưng **không chứng minh chỉ do tải máy hoặc không có regression**. Một lượt không tương đương trung vị 7 lượt. Cần thống nhất phép đo tiếp theo/A-B cùng môi trường nếu muốn chốt không tụt; lượt này không đo lại hoặc chọn điểm đẹp.

```powershell
npx --yes lighthouse@13.5.0 http://127.0.0.1:3003/ `
  --chrome-flags="--headless=new --no-sandbox" `
  --only-categories=performance,accessibility,best-practices,seo `
  --output=json --output-path=<artifact>/lighthouse-mobile.report.json --quiet
# Lượt desktop cùng flags, thêm --preset=desktop và output-path riêng.
```

## Artifact và giới hạn

Artifact local, không commit: **backend/target/frontend-security-20261025** chứa source.tar/before.tar, clean-results.json, audit-before/after JSON trong stdout.log, ci/lint/typecheck/test/build logs, smoke-results.json, backend-result.json, lighthouse-*.report.json và lighthouse-results.json. Có thể bị xóa bởi mvn clean; gói Nhi ngoài target không thay đổi. Không lưu password/token/JWT secret trong evidence.

Frontend UI Engineering được dùng để giữ component/state/a11y; UI UX Pro Max chỉ tra reduced-motion, không sinh design system mới. Không có Chrome DevTools MCP: Lighthouse dùng Chrome headless thật; functional smoke là HTTP. Chưa chạy lại screen reader, touch vật lý hoặc toàn bộ browser matrix.

- [x] Next/eslint-config-next đúng 16.4.3; package độc lập khác không nâng.
- [x] SVG config và next/image usage đã kiểm.
- [x] Clean install/lint/typecheck/119 tests/hai build đạt.
- [x] Audit production sạch; dev high ghi nhận, chưa nâng.
- [x] Production smoke và một vai trò backend thật đạt; credentials giữ nguyên.
- [x] Sidebar CSS variable, active/ẩn/bàn phím regression đạt.
- [ ] Xác nhận mobile không tụt: **chưa đạt theo một lượt đo**.
- [ ] Đóng review đợt 3: **chưa đề nghị đóng**.
