# W12 — Kịch bản demo lõi CRM và evidence

Ngày rà soát: 15/11/2026 (UTC+07:00)

Nhánh rà soát: `Thắng`, nền commit `f3a414e`; các thay đổi font và evidence trong lượt này chưa commit.

Phạm vi: các màn hình CRM lõi đã có; phần hội thoại, Copilot, Tín hiệu khách hàng và nhóm E thuộc AI-11, không nằm trong kịch bản này.

## Chuẩn bị môi trường demo

1. Chạy theo [Hướng dẫn chạy local](../huong-dan-chay-local.md), dùng PostgreSQL local và một bản sao dùng riêng cho demo.
2. Không chạy các bước tạo Lead, Opportunity, Interaction hay Task trên Supabase dùng chung hoặc database canonical. Các bản ghi CRM không xóa cứng; mỗi lượt demo nên bắt đầu từ một bản sao mới.
3. Dùng một Manager và một Sales đang hoạt động, cùng team với Offering ACTIVE. Dùng tài khoản đã được cấp cho môi trường local; không ghi mật khẩu, token hoặc thông tin đăng nhập vào evidence.
4. Chọn một Offering Sales hiện chưa được phân công hiệu lực cho mình. Nếu không có cặp phù hợp, tạo bản sao demo sạch theo hướng dẫn; không sửa dữ liệu seed dùng chung.
5. Dữ liệu kiểm thử phải là dữ liệu giả: tên `W12 Demo <timestamp>`, email miền `example.test`, số điện thoại không thuộc người thật.

## Kịch bản trình diễn

| Bước | Vai trò / màn hình | Thao tác | Kết quả cần thấy |
|---|---|---|---|
| 1 | Manager — **Phân công sản phẩm** | Chọn Offering ACTIVE và Sales cùng team; đặt hiệu lực bắt đầu hiện tại, để trống ngày kết thúc; xác nhận. | Có thông báo thành công; dòng phân công ở trạng thái đang hiệu lực. |
| 2 | Sales — **Sản phẩm & dịch vụ** | Đăng nhập bằng Sales được giao; tìm Offering theo tên/mã và mở chi tiết. | Offering vừa giao xuất hiện trong danh sách và có thao tác tạo Lead. Offering ngoài phạm vi không được dùng để tạo Lead. |
| 3 | Sales — **Lead** | Tạo Lead giả từ Offering; nhập tên, email giả và nguồn; lưu. | Mở được chi tiết Lead mới, trạng thái ban đầu `NEW`, Offering và người phụ trách đúng. |
| 4 | Sales — **Lead** | Chọn xác nhận đủ điều kiện, sau đó chuyển Lead thành Customer; chọn loại Customer theo dữ liệu giả. | Lead thành `CONVERTED`; ứng dụng mở chi tiết Customer được tạo hoặc tái sử dụng theo quy tắc identity. |
| 5 | Sales — **Cơ hội bán hàng** | Từ Customer tạo Opportunity; chọn Offering đang được phân công, nhập ngày dự kiến và lưu ở giai đoạn đầu. | Opportunity xuất hiện với đúng Customer, Offering và owner; không chốt WON/LOST trong buổi demo. |
| 6 | Sales — **Tương tác** | Ghi Interaction gắn với Customer; dùng tiêu đề/nội dung giả và chọn loại/kênh phù hợp. | Chi tiết Interaction mở được; loại/kênh hiển thị nhãn dễ đọc thay vì mã enum đã biết. |
| 7 | Sales — **Nhiệm vụ** | Tạo Task gắn với Customer, đặt hạn trong tương lai và owner trong phạm vi. | Chi tiết Task hiển thị đúng trạng thái, hạn và Customer liên quan. |
| 8 | Sales, sau đó Manager — **Customer 360** | Mở hồ sơ 360 theo liên kết từ Customer hoặc Task; Manager mở cùng hồ sơ. | Hai vai trò trong phạm vi thấy Lead đã chuyển, Opportunity, Interaction và Task gắn với đúng Customer; chỉ số/tổng số khớp các mục vừa tạo. |
| 9 | Manager — **Báo cáo** | Mở Dashboard trong cùng tenant, kiểm tra bộ lọc thời gian và phạm vi người phụ trách. | Màn hình tải được số liệu theo quyền; bộ lọc thể hiện trên URL. Không đối chiếu KPI của dữ liệu vừa tạo với seed chuẩn. |

### Kiểm tra vai trò và trạng thái giao diện

- Manager và Sales chỉ thao tác trên bản ghi/Offering thuộc scope backend cấp cho họ; ẩn nút ở frontend không thay cho kiểm tra quyền backend.
- Có thể dùng Data Staff để xác nhận trạng thái che nội dung trên Customer 360 theo permission. Không yêu cầu vai trò này đọc nội dung nhạy cảm.
- Khi demo, quan sát trạng thái đang tải, lỗi API, empty state và phản hồi sau khi lưu; lỗi phải gắn với thao tác/trường tương ứng, không để lộ stack trace, token hay thông tin bí mật.
- Kiểm tra một lượt bàn phím trên trang danh sách và dialog: focus nhìn thấy được, thứ tự Tab hợp lý và không bị cuốn khỏi nội dung đang thao tác.
- Không trình diễn các màn hình Hội thoại, AI Copilot, Tín hiệu khách hàng hoặc nhóm E trong W12; các phần này thuộc AI-11.

## Automated evidence liên quan

- Luồng Manager phân công → Sales thấy Offering → Lead → Customer → Interaction/Task → Customer 360: [`frontend/e2e/vertical-slice.spec.ts`](../../frontend/e2e/vertical-slice.spec.ts).
- Luồng Lead → Customer → Opportunity → Interaction/Task → Customer 360: [`frontend/e2e/manager.spec.ts`](../../frontend/e2e/manager.spec.ts).
- Phân trang/role trên màn hình Dashboard và reflow/mobile keyboard: [`frontend/e2e/dashboard.spec.ts`](../../frontend/e2e/dashboard.spec.ts), [`frontend/e2e/keyboard-mobile.spec.ts`](../../frontend/e2e/keyboard-mobile.spec.ts).
- Kiểm tra dữ liệu đa ngành và giao diện theo vai trò đã ghi tại [W12 FE demo data check](w12-fe-demo-data-check-2026-11-12.md). Ảnh Dashboard và Customer 360 trong thư mục `w12-fe-demo-2026-11-12/` là ảnh ngày 12/11, chỉ dùng tham khảo giao diện của lần chạy đó.
- Font Be Vietnam Pro được nạp từ file local; [`frontend/e2e/font-assets.spec.ts`](../../frontend/e2e/font-assets.spec.ts) kiểm tra không gửi request tới Google Fonts và font local trả HTTP 200.

## Kết quả rà soát ngày 15/11/2026

| Kiểm tra | Kết quả |
|---|---|
| ESLint (`npm run lint`) | PASS — không có warning/error. |
| TypeScript (`npm run typecheck`) | PASS. |
| Vitest (`npm run test -- --maxWorkers=2`) | PASS — 29 file, 333/333 test. Lượt suite mặc định trước đó bị nghẽn worker và hai test bất đồng bộ hết thời gian chờ; hai test đó đạt khi chạy riêng và toàn suite đạt lại với hai worker. |
| Production build | PASS — `CIRCLE_NODE_TOTAL=1 npm run build -- --webpack`; Next.js 16.4.3 biên dịch và tạo đủ 29 trang tĩnh. Be Vietnam Pro không còn cần tải từ Google Fonts. Build Webpack chạy trong môi trường có quyền ghi đầy đủ vì build trong sandbox gặp `EPERM` khi tạo thư mục route. |
| E2E Chromium | PASS — 33/33 qua `scripts/e2e-env.ps1`, gồm luồng CRM, phân quyền, tenant, reflow/mobile và kiểm tra font. Runner dùng bản sao database tạm từ cluster cô lập `127.0.0.1:54329`; database được xóa và cluster đã dừng sau test. |
| Font runtime trên Chromium | PASS — không có request `fonts.googleapis.com`/`fonts.gstatic.com`; asset Be Vietnam Pro local trả HTTP 200 và `document.fonts` xác nhận family `vietnam` đã tải cho nội dung tiếng Việt. |
| Console, accessibility tree và so sánh thị giác thủ công | Chưa rà bằng Chrome DevTools MCP (không có trong phiên). E2E xác nhận hành vi/runtime và reflow đã viết test; kết quả này không thay cho kiểm tra trình đọc màn hình hoặc đánh giá thị giác thủ công. |

Evidence trình duyệt trước đó: [W12 FE demo data check](w12-fe-demo-data-check-2026-11-12.md) ghi nhận 31/31 E2E đạt ngày 12/11 trên checkout và database kiểm thử tại thời điểm đó. Kết quả đó là lịch sử, không thay cho E2E trên build hiện tại.

## Kết luận

Lỗi build ban đầu do `next/font/google` cần truy cập `fonts.googleapis.com`, trong khi môi trường không có kết nối tới dịch vụ đó. Be Vietnam Pro đã được chuyển sang `next/font/local` với các weight đã chốt và license SIL OFL 1.1 đi kèm; build production và toàn bộ 33 E2E Chromium hiện đạt. W12 có evidence runtime mới cho luồng CRM lõi, vai trò và reflow. Lượt này chưa kiểm tra console/accessibility tree bằng DevTools hoặc làm đánh giá thị giác thủ công, nên các phần đó không được tuyên bố đã kiểm chứng.

Để tái chạy build trong PowerShell trên môi trường Windows có giới hạn worker: `$env:CIRCLE_NODE_TOTAL = '1'; npm run build -- --webpack`. Chạy UI E2E bằng runner cô lập theo [Hướng dẫn chạy local](../huong-dan-chay-local.md); runner cần được phép kết nối PostgreSQL cục bộ ở cổng 54329.
