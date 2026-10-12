# Evidence frontend foundation đợt 1 — 12/10/2026

Owner: **Huỳnh Minh Tài** · Nhánh: **Tài** · Múi giờ báo cáo: **Asia/Saigon**.

**Lịch sử Phase 0–1 và tiến trình Phase 2–5b.** Tài đã xác nhận “Tiếp tục Phase 2–5b” ngày 12/10/2026. Các bảng Phase 0–1 dưới đây là kết quả lúc thiết kế; phần cập nhật cuối ghi kiểm thực tế sau scaffold. [Design system](../design/ui-ux/design-system-v1.md) · [Wireframe](../design/ui-ux/frontend-foundation-wireframe.md).

## 1. Tiếp quản và môi trường

Đã đọc HandOff ở `D:\POSE\AGENT_HANDOFF.md`, lấy snapshot 0/0B/13 ngày 12/10 làm trạng thái hiện hành; các transcript/snapshot cũ chỉ là lịch sử. Đề tài là CRM nội bộ đa ngành. Backend 20 API foundation đã có evidence nghiệm thu; **lượt này không chạy lại** 83 test PostgreSQL, 54 test H2 hay HTTP backend và không suy ra toàn TLCN đã hoàn thành.

Trước khi sửa, `git status --short` rỗng; branch `Tài`; HEAD và remote-tracking `origin/Tài` cùng `451dce35be969c086dbcec63ccb3d6b030b14f16`. Đây là ref local đã kiểm, không gọi fetch/ls-remote và không tuyên bố đã kiểm remote live. Git author cấu hình `fransico08`, giữ nguyên cấu hình; không trailer Co-authored-by.

| Thành phần | Phiên bản / trạng thái quan sát |
|---|---|
| Node.js | v24.13.0 |
| npm | 11.6.2 |
| Python sẵn có | 3.13.9 |
| fontTools sẵn có | 4.60.1; đã đối chiếu JSON probe |
| Browser probe | Chrome/141.0.7390.123, headless, profile mới riêng mỗi lần |
| Chrome DevTools MCP | Không có tool tương ứng trong phiên; không cài MCP/extension |
| Package frontend | **Chưa cài**; chưa có package.json/lockfile. Chốt stable tương thích và ghi version ở Phase 2 |
| Cổng 54329 và 8080 | Không có listener tại thời điểm preflight; phải kiểm lại trước Phase 5 |
| Frontend | Chỉ README và thư mục giữ chỗ, không có code UI |

Đọc theo thứ tự yêu cầu: AGENTS → frontend skill/checklist → UI UX Pro Max skill/quick-reference/pro-rules → browser skill → frontend README, contract phần 1/2, roles, backend README → Google Doc. Đã đọc thêm root README và code xử lý 401 liên quan để thiết kế refresh đúng contract.

## 2. Lệnh và tra cứu đã thực hiện

Các lệnh Git chạy trong worktree Tài; không sửa checkout main.

```text
git status --short
git branch --show-current
git rev-parse HEAD
git rev-parse origin/Tài
git config user.name
node --version
npm.cmd --version
Get-NetTCPConnection -State Listen (lọc 54329, 8080)
```

Tra cứu bằng Python có sẵn và **đường dẫn tuyệt đối**, không persist/force:

```powershell
$poseSearch = 'C:\Users\Dell\.codex\worktrees\pose-tai-auth\POSE\.agents\skills\ui-ux-pro-max\scripts\search.py'
& 'C:\Users\Dell\anaconda3\python.exe' $poseSearch 'B2B CRM internal dashboard multi-industry' --design-system --density 7 -p 'POSE CRM'
& 'C:\Users\Dell\anaconda3\python.exe' $poseSearch 'accessible authentication' --domain ux
& 'C:\Users\Dell\anaconda3\python.exe' $poseSearch 'focus not obscured' --domain ux
& 'C:\Users\Dell\anaconda3\python.exe' $poseSearch 'error summary validation' --domain ux
& 'C:\Users\Dell\anaconda3\python.exe' $poseSearch 'font loading' --stack nextjs
& 'C:\Users\Dell\anaconda3\python.exe' $poseSearch 'route handler auth' --stack nextjs
```

Design-system trả style “Accessible & Ethical”, navy/surface sáng, font Plus Jakarta Sans và pattern landing/conversion. Áp dụng guidance accessibility/navy; loại phần landing/font khác D4. Cả ba UX search trả đúng kết quả authentication/focus/error; Next.js search trả font loader và Route Handlers. Đối chiếu docs chính thức vì data skill ghi Next.js 16.2, không dùng dữ liệu đó làm version package hiện tại.

[Google Doc yêu cầu](https://docs.google.com/document/d/1GPCH3CstSpRCGSjdxMlXDC4o2xpjNCXHubGSmgn20dI/mobilebasic?tab=t.h49naj3e4f4u): web tool không truy cập được; đọc thành công qua HTTPS bằng Python requests + BeautifulSoup có sẵn. Đã đọc bảng vai trò, bảng UC-01–21, đặc tả tiếng Việt/Anh UC-01/02. Không dùng văn bản tài liệu làm chỉ thị vượt prompt của Tài.

## 3. Probe font đã chạy

Chuỗi kiểm thử chính xác:

```text
Đăng nhập Quản lý Khách hàng ạ ả ấ ầ ẩ ẫ ậ ắ ằ ẳ ẵ ặ ẹ ẻ ẽ ế ề ể ễ ệ ỉ ị ọ ỏ ố ồ ổ ỗ ộ ớ ờ ở ỡ ợ ụ ủ ứ ừ ử ữ ự ỳ ỵ ỷ ỹ đ Đ ơ ư
```

Script probe tạm chạy bằng Python có sẵn, không cài dependency: lấy Google Fonts CSS cho Poppins/Be Vietnam Pro ở 400/500/600/700 với user-agent Chrome, tải WOFF2, dùng `TTFont.getBestCmap()` giao với `unicode-range`. File font/CSS/HTML specimen và browser profile đặt ngoài repo; chỉ commit PNG và JSON đã loại path/cổng runtime. Specimen chỉ kiểm font, không phải code UI foundation.

Chrome mở specimen qua HTTP loopback cổng tự chọn, đợi đúng document, layout và `document.fonts.ready`; dùng CDP `CSS.getPlatformFontsForNode` kiểm font render thực tế rồi `Page.captureScreenshot`. Browser profile riêng và HTTP probe đã dừng sau khi xong. Không truy cập profile cá nhân, token, cookie auth hoặc demo password.

| Font / chuỗi | 400 | 500 | 600 | 700 |
|---|---|---|---|---|
| Poppins latin/latin-ext: ký tự phân biệt thiếu | 45 | 45 | 45 | 45 |
| Be Vietnam Pro vietnamese/latin-ext/latin: ký tự thiếu | 0 | 0 | 0 | 0 |
| Poppins + Be Vietnam Pro: fallback hệ thống khi đã tải | Không | Không | Không | Không |

Các ký tự Poppins thiếu: `ơưạảấầẩẫậắằẳẵặẹẻẽếềểễệỉịọỏốồổỗộớờởỡợụủứừửữựỵỷ`. Đây là coverage của **web font và CSS tải thực tế**, không suy từ tên subset hoặc font TTF đầy đủ. Chromium báo 78 glyph Poppins + 47 glyph Be Vietnam Pro cho mỗi mẫu kết hợp; glyph count không đồng nghĩa số ký tự Unicode phân biệt.

Chốt chuỗi `Poppins, 'Be Vietnam Pro', system-ui, sans-serif`, sẽ nạp qua `next/font/google` sau khi được duyệt. [PNG kiểm glyph](assets/frontend-foundation-dot1-2026-10-12/font-glyph-probe.png) · [JSON: fontTools version, URL WOFF2, SHA-256, coverage và font thực tế](assets/frontend-foundation-dot1-2026-10-12/font-glyph-probe.json).

Lần probe đầu đọc font trước khi document/fonts ổn định nên còn fallback hệ thống. Đã sửa điều kiện chờ, chạy lại với profile mới và assertion không có system font trong mẫu kết hợp/đối chứng. Artefact commit là kết quả sau sửa. Chưa kiểm next/font runtime, font offline, Linux/macOS, screen reader hoặc chuỗi Unicode NFD.

## 4. Đo màu và quyết định đã xác nhận

Đã chạy Python tính relative luminance sRGB: channel ≤0,04045 chia 12,92, còn lại `((channel + 0.055)/1.055)^2.4`; trọng số 0,2126/0,7152/0,0722. Ratio dùng giá trị đầy đủ để đánh giá, làm tròn khi trình bày. [JSON 28 cặp màu](assets/frontend-foundation-dot1-2026-10-12/color-contrast.json).

Giữ màu đề xuất; thêm `control-border #64748B` vì border/surface gốc chỉ **1,23:1**, viền mới **4,76:1**. Thêm `focus-sidebar #BFDBFE` vì primary/active-sidebar chỉ **2,00:1**, focus mới **7,29:1**; focus trên navy **12,56:1**. Primary trắng/xanh **5,17:1**; badge pastel có chữ **4,75–5,91:1**. Hai cặp không đạt vẫn nằm trong JSON để giải thích vì sao không dùng cho control/focus.

Tài đã xác nhận trong chat ngày 12/10: **thêm cookie HttpOnly chỉ làm cờ hiện diện phiên, Path `/`, để middleware chuyển hướng sớm; quyền thật vẫn kiểm ở backend**. Wireframe ghi `pose_session_present=1`, không token/user/permission; giữ nguyên Path của hai cookie token. Đây là xác nhận riêng về kiến trúc, **chưa phải** xác nhận “tiếp tục” Phase 2.

## 5. Bảng kiểm Phase 0–1

| Mục | Cách kiểm | Kết quả |
|---|---|---|
| Đúng worktree/branch, sạch ban đầu | Git status/branch/refs | Đạt trước sửa; nhánh Tài |
| Tiếp quản HandOff và thứ tự tài liệu | Đọc nguồn trong worktree, Google Doc | Đã đọc; phân biệt snapshot hiện hành và lịch sử |
| Môi trường | Node/npm/Python/browser version | Đã ghi, chưa cài frontend packages |
| Poppins tiếng Việt | WOFF2 cmap + CSS ranges, 4 weights | Thiếu 45 ký tự phân biệt; chọn fallback D4 |
| Chuỗi kết hợp | Chrome font tree + screenshot sau font ready | Đủ, không fallback hệ thống trong probe |
| Tương phản token | Phép tính sRGB trên 28 cặp | 26 cặp đạt; 2 cặp bị loại khỏi ngữ cảnh control/focus |
| Design system | Token, nguồn ảnh theo tên, scale và hành vi | Đã viết, chờ review |
| User flow và wireframe | Login, shell 1440/768/375, logout, 403/404; landmark/heading | Đã viết, chờ review |
| Cờ session cho middleware | Tài chọn phương án trong chat | Đã xác nhận |
| Gitignore | Kiểm pattern env/build trước commit | Đã bổ sung `.env*`, exception `.env.example`, tsbuildinfo, coverage |
| Backend / migrations / gói Nhi | Rà phạm vi diff | Không thay đổi |

## 6. Lệch UC / contract / thiết kế

| Bằng chứng | Cách xử lý |
|---|---|
| Google Doc 2.2.2.1 luồng chính chỉ email/password; contract `docs/api/api-contract-foundation-v1.md:43` có organizationCode | Đã chốt D2: login ba trường |
| UC-01 E3.2 lộ trạng thái inactive; contract `docs/api/api-contract-foundation-v1.md:75` trả lỗi chung | Đã chốt D2: một câu chung, không lộ inactive |
| UC-01 chuyển dashboard theo role; prompt D7/mục 5.1 | Tổng quan chung, nội dung theo permission, hỗ trợ returnTo an toàn |
| Contract `docs/api/api-contract-foundation-v1.md:142` / roles tài liệu dùng Sales Staff | Dùng nhãn tiếng Việt trong prompt, giữ nguyên SALES_REP |
| Cookie Path theo D3 không tới page `/app` | Tài đã duyệt cookie cờ Path `/`; không sửa Path token |
| `backend/src/main/java/com/pose/backend/security/SecurityErrorResponder.java:21` mặc định AUTH_INVALID_TOKEN; dòng 24–25 chỉ AUTH_TOKEN_EXPIRED khi JWT validation có lỗi hết hạn | BFF nhận thiếu access cookie cần kích hoạt nhánh refresh, không đợi backend trả mã hết hạn; ghi thiết kế, chưa code/test |
| Contract `docs/api/api-contract-foundation-v1.md:81` refresh không gia hạn hạn phiên; cookie D3 Max-Age 7 ngày mỗi lần đặt lại | Backend vẫn chặn theo hạn tuyệt đối; tuổi cookie không chứng minh phiên còn hạn |
| Docs Next.js hiện hành đổi convention middleware thành proxy | Giữ vai trò guard UX; dùng tên file theo major stable được cài ở Phase 2 |

Chưa phát hiện endpoint/shape backend thiếu cho login/me/refresh/logout/organization. Các nhánh BFF mới ở trên vẫn cần kiểm thử thực tế ở Phase 4–5, không ghi đạt chỉ nhờ đọc code.

## 7. Chưa làm / chưa kiểm ở thời điểm kết thúc Phase 0–1

Lúc kết thúc Phase 0–1, Phase 2–5b chưa được phép bắt đầu. Danh sách khi đó gồm scaffold/package versions, token CSS/next-font runtime, component RTL, BFF/proxy/cookie/CSRF, single-flight/retry, routing thật, đăng nhập năm role, lowercase code, sai password/inactive, logout, refresh khi xóa cookie, network/error/403/404. Tài đã cho tiếp tục ở lượt sau; kết quả mới ở mục 9.

Chưa chạy lint/typecheck/test/build vì frontend chưa có scaffold. Chưa nghiệm thu bàn phím/APG, responsive/zoom, nội dung dài, accessibility tree/axe của ứng dụng, screen reader, tương phản computed CSS hoặc reduced motion. Không khởi động backend/DB, không restore backup và không đọc credentials demo ở Phase 0–1.

Phạm vi đợt 2 dự kiến theo prompt: trang giới thiệu SEO/AEO theo bố cục CloudGO và nghiệm thu Lighthouse; không tự triển khai trong đợt 1.

## 8. Cập nhật được Tài duyệt khi tiếp tục

Mobile chỉ hàng Menu · POSE · User cao 56–64px sticky; breadcrumb/tenant trong luồng. Desktop hàng chính 64–72px, thêm breadcrumb khi cần; bỏ sticky khi viewport cao ≤500px hoặc topbar chiếm quá nhiều sau zoom. Đo chiều cao thật cho scroll-padding-top, nghiệm thu 320px/200%/400%. Người dùng (Quản trị) đổi sang UserCircleIcon, cần kiểm export. Sau scaffold kiểm font next/font thật trên H1 login/label/nút/sidebar ở bốn độ đậm; nếu lệch rõ trong cùng từ thì dừng và báo ảnh. Các quyết định khác giữ nguyên.

## 9. Scaffold và điểm dừng font

Tài đã cho tiếp tục Phase 2–5b. Scaffold thủ công, giữ README; Node v24.13.0 / npm 11.6.2. Không thay backend, migrations, gói Nhi, backup hoặc credentials. Không khởi động backend/DB, không đọc mật khẩu demo. Chạy frontend dev trên loopback; browser headless với profile mới và CDP có sẵn, không cài package trình duyệt/MCP. Skill Sites áp dụng nhánh local-only theo phạm vi repo, không đăng ký/publish site.

### Package trực tiếp đã cài

Các phiên bản được pin trong package.json/lockfile. Kiểm registry dist-tag stable và peer/engine trước cài; không cài package ngoài D6.

| Package | Phiên bản |
|---|---|
| next, eslint-config-next | 16.4.0 |
| react, react-dom | 19.3.0 |
| @tanstack/react-query | 5.104.2 |
| @heroicons/react | 2.2.0 |
| typescript | 6.0.3 |
| @types/node | 24.19.2 |
| @types/react, @types/react-dom | 19.3.0 |
| tailwindcss, @tailwindcss/postcss | 4.3.3 |
| eslint | 9.39.5 |
| eslint-plugin-jsx-a11y | 6.10.2 |
| vitest | 5.0.4 |
| @vitejs/plugin-react | 6.1.2 |
| jsdom | 29.1.1 |
| @testing-library/react | 16.3.3 |
| @testing-library/user-event | 14.6.7 |
| @testing-library/jest-dom | 7.0.1 |

Chọn TypeScript 6.0.3 vì typescript-eslint đi kèm Next chưa nhận major 7; ESLint 9.39.5 vì jsx-a11y chưa nhận major 10; jsdom 29.1.1 vì major 30 yêu cầu Node ≥24.15 trong nhánh Node 24, môi trường hiện tại 24.13. npm có cảnh báo ESLint 9 đã hết hỗ trợ; ghi rõ giới hạn, không đổi stack hoặc bỏ jsx-a11y để chạy bản không tương thích. Các dependency gián tiếp do npm giải quyết, không phải package trực tiếp bổ sung.

`next dev` tự tạo `frontend/AGENTS.md` với block hướng dẫn đọc docs đóng gói. Đã đối chiếu `node_modules/next/dist/server/lib/generate-agent-files.js`, đọc tài liệu font tương ứng trong `node_modules/next/dist/docs/`; giữ block để không tái tạo thay đổi chưa commit khi chạy dev. Chỉ thị root AGENTS và yêu cầu của Tài vẫn áp dụng.

### Kiểm font sau scaffold

Mẫu chẩn đoán dev `/font-probe?weight=400|500|600|700` dùng root layout `next/font/google` thật. H1 “Đăng nhập POSE CRM”, label Mã tổ chức/Email/Mật khẩu, nút Đăng nhập và mẫu sidebar được chụp ở cả bốn weight. Mẫu không có credentials và chưa phải trang `/dang-nhap` hoặc app shell tích hợp. Route yêu cầu `POSE_FONT_PROBE=1` và dev; production trả 404.

Chrome 141.0.7390.123, viewport 1440×1000, đợi document/layout và `document.fonts.ready`. `CSS.getPlatformFontsForNode` cho H1/label/nút/sidebar/chuỗi kiểm chỉ có hai web font, không có font hệ thống; đủ glyph ở cả bốn weight. [JSON font thực tế](assets/frontend-foundation-dot1-2026-10-12/next-font-probe.json).

[Ảnh 400](assets/frontend-foundation-dot1-2026-10-12/next-font-400.png) · [500](assets/frontend-foundation-dot1-2026-10-12/next-font-500.png) · [600](assets/frontend-foundation-dot1-2026-10-12/next-font-600.png) · [700](assets/frontend-foundation-dot1-2026-10-12/next-font-700.png) · [Ảnh chi tiết 700, browser capture scale 3](assets/frontend-foundation-dot1-2026-10-12/next-font-700-detail.png).

**Kết quả thị giác: chữ `ậ` từ Be Vietnam Pro trong “nhập” có thân chữ nhỏ hơn các chữ Poppins liền kề, rõ nhất ở 700. Chưa quan sát baseline trượt rõ.** Kiểm WOFF2 do Next.js tạo bằng fontTools, lấy `OS/2.sxHeight / head.unitsPerEm`:

| Weight | Poppins x-height (em) | Be Vietnam Pro (em) | Be nhỏ hơn |
|---|---:|---:|---:|
| 400 | 0,548 | 0,530 | 3,3% |
| 500 | 0,551 | 0,530 | 3,8% |
| 600 | 0,554 | 0,530 | 4,3% |
| 700 | 0,558 | 0,530 | 5,0% |

[JSON số đo từ font Next.js](assets/frontend-foundation-dot1-2026-10-12/next-font-metrics.json). X-height là số đo hỗ trợ quan sát, không chứng minh mọi glyph đều lệch đúng phần trăm đó; không coi đây là ngưỡng WCAG. Theo **yêu cầu bổ sung số 3 của Tài**, dừng trước Phase 3, báo kèm ảnh; giữ nguyên chuỗi font D4, không tự đổi hoặc hiệu chỉnh fallback. Cần Tài quyết định trước khi tiếp tục công việc phụ thuộc font.

### Lệnh và nghiệm thu scaffold

```text
npm install --no-fund --no-audit
npm ls --depth=0
npm run dev (POSE_FONT_PROBE=1; NEXT_TELEMETRY_DISABLED=1)
node -e kiểm export UserCircleIcon
npm run lint
npm run typecheck
npm run build
git diff --check
```

| Mục | Cách kiểm | Kết quả |
|---|---|---|
| Scaffold App Router, TS strict, Tailwind/PostCSS | Config + dev/build | Đạt cho scaffold |
| Root font/lang/token CSS | Runtime, computed CSS, font tree | Đã nạp đúng; hình thức fallback cần quyết định |
| Người dùng UserCircleIcon | Export package 2.2.0, render trong mẫu | Tồn tại; chưa có navigation config |
| Lint | ESLint Next + jsx-a11y strict, max-warnings 0 | Đạt sau sửa default export PostCSS |
| Typecheck | tsc --noEmit | Đạt |
| Build | next build | Đạt cho scaffold; chưa có UI/BFF đợt 1 |
| HTTP production | next start + requests, không theo redirect | `/` trả 307 Location `/app`; `/font-probe?weight=700` trả 404 |
| Test | Vitest/jsdom/RTL đã cấu hình | Chưa chạy; chưa có test hành vi Phase 3–4 |
| Kiểm font 400/500/600/700 | Screenshot runtime + CDP + WOFF2 metrics | Đủ glyph, có khác biệt cỡ thân chữ; kích hoạt điểm dừng |
| Topbar 320px/zoom 200–400% | Chưa có shell/topbar | Chưa kiểm; đã ghi chỉnh sửa vào design/wireframe |
| Session/permission/5 role/logout/refresh | Chưa triển khai Phase 3–5 | Chưa kiểm; không suy ra đạt từ scaffold |
| Keyboard/APG/axe hoặc a11y tree/reflow/long text | Chưa có component tích hợp | Chưa kiểm Phase 5b |

Browser probe riêng đã đóng; frontend dev/start được dừng sau khi kiểm. Backend/cluster không được khởi động. `.env.example` chỉ có BACKEND_URL, `.env*` còn lại và node_modules/.next không commit. Bổ sung ignore `frontend/next-env.d.ts` vì Next.js tái tạo tham chiếu type khi dev/build; tránh thay đổi file sinh tự động sau mỗi chế độ chạy. Không push.

## 10. Tài chốt Be Vietnam Pro và tiếp tục

Tài chọn “Dùng Be Vietnam Pro làm font chính” trong chat sau báo cáo điểm dừng. Quyết định này thay D4 Poppins/fallback; không thay token màu hay stack. Root layout chỉ nạp Be Vietnam Pro qua next/font/google, subset vietnamese, weight 400/500/600/700; chuỗi CSS `Be Vietnam Pro, system-ui, sans-serif`.

Chụp lại cùng mẫu và bốn weight, `CSS.getPlatformFontsForNode` xác nhận toàn bộ H1/label/nút/sidebar/chuỗi kiểm chỉ dùng Be Vietnam Pro, không hệ thống hoặc Poppins. Xem ảnh 400/500/600/700: không còn khác biệt cỡ thân chữ do trộn font. [400](assets/frontend-foundation-dot1-2026-10-12/be-font-400.png) · [500](assets/frontend-foundation-dot1-2026-10-12/be-font-500.png) · [600](assets/frontend-foundation-dot1-2026-10-12/be-font-600.png) · [700](assets/frontend-foundation-dot1-2026-10-12/be-font-700.png) · [Chi tiết](assets/frontend-foundation-dot1-2026-10-12/be-font-700-detail.png) · [JSON font tree](assets/frontend-foundation-dot1-2026-10-12/be-font-probe.json). Probe ban đầu assertion tên family bằng đúng “Be Vietnam Pro” không nhận tên Medium/SemiBold; đã sửa kiểm family prefix và chạy lại đủ bốn weight, artefact lưu là kết quả sau sửa.

Điểm dừng font đã được giải quyết; tiếp tục component/session/trang/tích hợp và nghiệm thu Phase 3–5b. Các bảng chưa kiểm ở mục 9 là trạng thái trước quyết định này.
