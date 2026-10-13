# Sửa review frontend foundation — 13/10/2026

Owner: Huỳnh Minh Tài · Nhánh Tài · Commit cục bộ, không push.

Review của Tài phát hiện ba lỗi chưa được bộ kiểm ngày 12/10 bao phủ đúng. Báo cáo này ghi phép kiểm bổ sung; không lấy kết quả trước làm bằng chứng cho clean install hoặc refresh mất mạng.

## 1. Lockfile đồng bộ

Chạy `npm install --no-fund --no-audit` trong frontend. Giữ nguyên package.json và toàn bộ version trực tiếp. Lock mới sửa @emnapi/wasi-threads cấp gốc thành 1.2.3; bổ sung @emnapi/core 1.10.0 và wasi-threads 1.2.1 lồng dưới resolver WASM theo dependency graph.

Tạo thư mục tạm mới, chỉ copy các file frontend do Git quản lý từ worktree. Trước cài xác nhận không có node_modules, .next hoặc next-env.d.ts sinh tự động; không copy dependency/build cache từ worktree. npm được phép dùng download cache thông thường. Node 24.13.0/npm 11.6.2.

| Lệnh trên bản sao sạch thứ nhất | Kết quả |
|---|---|
| npm ci --no-fund --no-audit | Exit 0, cài 448 package; không lỗi lockfile |
| npm run lint | Exit 0, không warning lint |
| npm run typecheck | Exit 0, trước build và chưa có .next |
| npm run test | Exit 0, 41/41 test trong 5 file |
| npm run build | Exit 0, production build |

Helper xuất log lần đầu không mã hóa được ký hiệu Unicode trong output build; đã đặt UTF-8 và chạy lại build thành công. Các lệnh ci/lint/typecheck/test trước đó đều exit 0. npm còn cảnh báo ESLint 9 hết hỗ trợ như evidence cũ; không thay version hoặc stack ngoài yêu cầu.

[Kết quả clean install cho lỗi 1](assets/frontend-foundation-review-2026-10-13/lock-clean-checks.json).

## 2. Refresh khi mất mạng

Client chỉ expire khi POST refresh trả 401 AUTH_INVALID_REFRESH_TOKEN hoặc me trong Web Lock trả 401 khác AUTH_TOKEN_EXPIRED. Lỗi mạng/timeout/5xx thành 503 NETWORK_ERROR, không cleanup/navigate; không retry POST tự động. Promise single-flight được reset để người dùng có thể thử lại.

Route refresh giữ cookie trên lỗi mạng/5xx. Dedupe server giữ các request đang chạy chung một Promise, nhưng không giữ kết quả lỗi tạm thời 10 giây; lần Thử lại chủ động có thể phục hồi ngay khi backend trở lại. Nếu backend đã tiêu thụ token trước khi kết nối mất, lần sau 401/replay vẫn xử lý hết phiên như Tài chấp nhận.

`npm run test`: 54/54 trong 5 file sau sửa lỗi 2, gồm 13 regression mới: refresh 503 giữ phiên và lần thử lại phục hồi, Web Lock me offline/timeout/500, POST refresh offline/timeout không retry, invalid me 401 vẫn expire, server transient dedupe được giải phóng, route network/500/503 không xóa cookie và invalid token 400/401 vẫn xóa. Giữ test cũ refresh 401 expire đúng một lần cho các request đồng thời. Kiểm backend thật được ghi sau tích hợp cả ba sửa đổi.

## 3. Phân biệt lỗi login

Login trả 400/401 hiển thị “Mã tổ chức, email hoặc mật khẩu không đúng.”; 5xx, 403 FORBIDDEN_ORIGIN, fetch reject và timeout hiển thị “Không thể kết nối. Vui lòng thử lại.”. Cả hai dùng role="alert", giữ mã tổ chức/email, xóa mật khẩu và không tự chuyển focus. Route login ánh xạ lỗi tài khoản thành 401 AUTH_INVALID_CREDENTIALS; lỗi backend/kết nối thành 503 NETWORK_ERROR, không đưa chi tiết backend ra client. Payload sai vẫn trả 400 với câu chung.

Bổ sung 7 ca RTL cho các nhánh response/network/timeout và 5 ca route cho backend 500/503, network/timeout, response thành công sai shape. Sau cả ba sửa đổi: **66/66 test trong 5 file**, tăng 25 ca so với 41 ca trước review. Wordmark Poppins tùy chọn chưa được Tài xác nhận; giữ Be Vietnam Pro.

## 4. Kiểm lại toàn bộ trên bản sao sạch

Tạo bản sao nguồn mới thứ hai sau khi sửa cả ba lỗi, chỉ lấy file frontend do Git quản lý từ worktree. Xác nhận không có node_modules, .next hoặc next-env.d.ts trước cài; không dùng dependency/build cache của worktree. Hash package.json trùng bản trước review; không đổi version trực tiếp. Node 24.13.0, npm 11.6.2.

| Lệnh trong frontend của bản sao sạch | Kết quả |
|---|---|
| npm ci --no-fund --no-audit | Exit 0, 448 package, không lỗi lockfile |
| npm run lint | Exit 0 |
| npm run typecheck | Exit 0, chạy trước build |
| npm run test | Exit 0, 66/66 test trong 5 file |
| npm run build | Exit 0, production build thành công |

[Kết quả toàn bộ clean checks](assets/frontend-foundation-review-2026-10-13/final-clean-checks.json). Production build này được dùng cho kiểm trình duyệt với backend thật bên dưới.

## 5. Hai kịch bản với backend thật

Chrome 141.0.7390.123 dùng profile thử nghiệm riêng; thao tác bằng CDP input và đọc DOM, không mock response. Frontend production chạy tại 127.0.0.1:3001 vì cổng 3000 có tiến trình khác; giữ nguyên tiến trình đó. Spring bind 127.0.0.1:8080, PostgreSQL 18.3 cô lập 127.0.0.1:54329, xác nhận SHOW data_directory đúng cluster thử nghiệm trước thao tác. Restore riêng pose_v2_review_frontend_20261013085843 từ backup final đã kiểm hash; onboarding baseline V1, không sửa backend/migrations hoặc gói Nhi. Credentials chỉ đọc vào môi trường process; JWT secret ngẫu nhiên giữ trong bộ nhớ, dùng lại khi khởi động backend để kiểm phục hồi cùng phiên.

| Kịch bản | Thao tác và kết quả |
|---|---|
| (i) Login khi backend tắt | Dừng backend do helper sở hữu, nhập form và submit. PASS: alert kết nối đúng câu, giữ mã/email, mật khẩu trống. |
| (ii) App mất access cookie và backend tắt | Đăng nhập thật, xóa riêng pose_at bằng CDP, dừng backend, tải lại /app để bootstrap gọi me/refresh. PASS: H1 “Không thể kết nối” + Thử lại; vẫn /app, pose_rt và cờ phiên còn nguyên. Một POST refresh thất bại, không logout hoặc tự retry POST. |
| Phục hồi kịch bản (ii) | Bật lại backend cùng DB/JWT secret, bấm Thử lại. PASS: Tổng quan với cùng danh tính, một refresh thành công; không cần đăng nhập lại. |

Không có uncaught runtime exception. Sau kiểm đã logout bằng UI, đóng browser/profile thử nghiệm, dừng frontend 3001, backend và cluster do helper sở hữu. Kiểm cuối: vẫn 6 tổ chức/36 người dùng và fingerprint credentials không đổi. Không chạm cổng 5432.

[Kết quả runtime, chỉ metadata cookie](assets/frontend-foundation-review-2026-10-13/backend-network-review.json) · [Login khi backend tắt](assets/frontend-foundation-review-2026-10-13/login-backend-offline.png) · [Refresh khi backend tắt](assets/frontend-foundation-review-2026-10-13/refresh-backend-offline.png) · [Thử lại sau phục hồi](assets/frontend-foundation-review-2026-10-13/retry-backend-restored.png).

## 6. Commit và giới hạn kiểm

- c0b1b5e — fix(frontend): synchronize lockfile for clean installs.
- 34a1dab — fix(frontend): preserve sessions during refresh network failures.
- Commit chứa mục 3–6 và tài liệu cập nhật — fix(frontend): distinguish login connectivity errors; hash xem git log/báo cáo gửi Tài.

Ba commit cục bộ trên Tài, author/committer fransico08, không Co-authored-by, không push. Không đổi package.json, font, token hay layout. Wireframe sửa nhãn nhánh lỗi để phân biệt lỗi mạng với hết phiên.

Timeout được kiểm bằng unit/RTL; browser thật kiểm mất kết nối bằng dừng backend. Chưa tạo timeout thật sau khi backend đã tiêu thụ refresh token; hành vi lần sau 401/replay chấp nhận theo review. Không chạy lại toàn bộ kiểm responsive/zoom và năm role ngày 12/10 trong lượt sửa này; bộ 66 test gồm toàn bộ 41 test cũ. Evidence cũ giữ lịch sử, không được dùng để chứng minh clean install hoặc lỗi mạng refresh đã đúng trước review.
