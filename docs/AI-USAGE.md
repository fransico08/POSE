# Nhật ký sử dụng AI của POSE CRM

## Mục đích và cách ghi

Ghi việc AI hỗ trợ và quyết định của Tài để có thể đối chiếu với commit/bằng chứng. Mỗi phiên agent có thay đổi thêm ít nhất một dòng trước commit. Ngày lịch sử lấy từ git log và docs/testing; không suy đoán số giờ, model hoặc mức đóng góp. Bảng không chứa mật khẩu, token, credentials demo hoặc dữ liệu tài khoản. Hash ghi sau khi commit đã có; dòng hiện tại có thể trỏ file rồi được bổ sung hash ở lượt sau.

## Lịch sử có bằng chứng

Các nguồn lịch sử không nêu riêng công cụ/model AI hoặc phân định phần AI làm với phần người làm. Vì vậy các dòng dưới ghi kết quả được chứng minh trong git/evidence, không quy toàn bộ implementation cho AI.

| Ngày | Card/Phạm vi | Công cụ AI dùng | Việc AI làm | Quyết định của Tài | File/commit | Kiểm chứng | Ghi chú |
|---|---|---|---|---|---|---|---|
| 12/10/2026 | Auth foundation | Không nêu trong nguồn | Evidence ghi: kiểm HTTP, DB cô lập và tổng hợp bằng chứng | Contract/roles đã chốt | 95504d8; docs/testing/auth-foundation-acceptance-2026-10-12.md | Evidence acceptance | Điền lại từ git log/evidence, không nhận là kiểm mới |
| 12/10/2026 | Frontend đợt 1 | Không nêu trong nguồn | Evidence ghi: login/app shell, font và kiểm runtime | Chốt Be Vietnam Pro và topbar | 6ee5a9f, dbfcb00; docs/testing/frontend-foundation-dot1-2026-10-12.md | Evidence foundation | Không bổ sung thông tin model không có nguồn |
| 13/10/2026 | Review foundation | Không nêu trong nguồn | Evidence ghi: lockfile và lỗi mạng/refresh | Giữ phiên khi mất kết nối | c0b1b5e, 34a1dab, 6518703; docs/testing/frontend-foundation-review-2026-10-13.md | Evidence review | Hành vi session được kiểm trong lượt gốc |
| 13/10/2026 | Frontend đợt 2 | Không nêu trong nguồn | Evidence ghi: landing công khai, SEO/AEO và accessibility | Duyệt nội dung có nguồn | cc5e195; docs/testing/frontend-landing-dot2-2026-10-13.md | Evidence landing đợt 2 | Các số lab thuộc ngày gốc |
| 13–14/10/2026 | Frontend đợt 3 | Không nêu trong nguồn | Evidence ghi: concept, motion, hydration, micro-interaction và test | Chọn B; giữ font 700/800; duyệt cổng hiệu năng cuối | c8b20a8; docs/testing/frontend-landing-dot3-2026-10-13.md và frontend-landing-dot3-2026-10-14.md | 116 test và các chuỗi lab ghi trong evidence | Không suy ra đạt toàn bộ WCAG từ Lighthouse |
| 25/10/2026 | Security patch | Không nêu trong nguồn | Evidence ghi: pin Next16.4.3, sidebar CSS index và kiểm lại | Giữ phạm vi bản vá Next | 34dd309, c29be3b, bdbb815; docs/testing/frontend-security-patch-2026-10-25.md | 119 test, build/audit và smoke trong evidence | Hiệu năng khi đó chưa đóng |
| 25–30/10/2026 | Fraunces tự host | Không nêu trong nguồn | Evidence ghi: checksum/font CSS, test preload, glyph và đo lại | Giữ Next16.4.3, tự host binary đã nghiệm thu | 159f485, bec3a95, 2827e84; docs/testing/frontend-fraunces-self-hosted-2026-10-30.md | 124 test; chuỗi ổn định P89; audit production0 | Ngày được đối chiếu git log và các lượt trong evidence |

## Phiên coverflow hiện tại

| Ngày | Card/Phạm vi | Công cụ AI dùng | Việc AI làm | Quyết định của Tài | File/commit | Kiểm chứng | Ghi chú |
|---|---|---|---|---|---|---|---|
| 31/10/2026 | Landing hero A–C | Codex; ui-ux-pro-max; frontend-ui-engineering; web search | Đọc scope, tìm ảnh miễn phí từ Download chính thức, kiểm JPEG và lập DEC-06/nhật ký | Duyệt bỏ nút dừng, coverflow ngành và ngoại lệ WCAG; cho phép tìm ảnh, fallback SVG | docs/meetings/decision-log.md DEC-06; frontend/src/assets/landing/frames/SOURCES.txt | Đã kiểm ảnh/JPEG; triển khai và nghiệm thu còn tiếp tục | Bán lẻ/F&B dùng SVG sau các ứng viên có chữ/logo/mặt; không giữ ảnh loại trong repo |
| 31/10/2026 | Hero coverflow và bỏ control | Codex; hai skill UI đã nêu | Dựng chữ server/ảnh static import/thumbnail blur, timer5s và hover/focus chung; cập nhật quy tắc tab | Theo DEC-06; giữ mock dùng chung và app/auth/font | HeroFrames/HeroMotion/HeroMotionScope; IndustryMarquee/RoleTabs; landing-v2.css | Working tree đạt 128 test (trước124); lint phát hiện directive thừa đã gỡ, còn kiểm lại lint/build/browser | Nguồn lịch sử không phân định AI nên ghi rõ thay vì suy đoán tool/model |
| 31/10/2026 | Kiểm biên coverflow | Codex | Cô lập/clip gallery trong cột ảnh để khung xa không chồng lên chữ hero ở breakpoint trung gian | Giữ tương phản và H1 tĩnh theo B3/B5 | landing-v2.css; 5e82d81/50b92f4/f3ec38f là các bước triển khai trước | Bản sạch đầu đang chạy; sẽ nạp lại source CSS cuối trước build | Không đổi kích thước ảnh/font hoặc token |
| 31/10/2026 | Chặn preload tự động | Codex; Chrome headless/CDP | Phát hiện React19 thêm link preload cho img eager; đặt fetchPriority low và thêm kiểm SSR thật | Không preload/ưu tiên cao cho ảnh theo B6 | HeroFrames.tsx; landing-concepts.test.tsx | Bản sạch trước đạt128 test/build2/audit0; đang kiểm lại bản sửa | Không suy ra chỉ bỏ prop preload là đủ |
