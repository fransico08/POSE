# Landing v2 — hai concept và wireframe chờ chọn

Ngày **13/10/2026**. Áp dụng chung prompt đợt 3 và chỉnh sửa Fraunces/ảnh 1b. Các bảng/wireframe Phase 0–1 bên dưới giữ lịch sử bản trình duyệt; `/` vẫn v1. [Nội dung v2](landing-content-v2.md) và quyết định B đã được Tài duyệt ở review tiếp theo, trạng thái hiện tại ghi ngay dưới đây.

**Cập nhật sau review:** Tài đã chọn **B**, duyệt bố cục/cấu trúc/nguồn; yêu cầu font tĩnh 700/800, chữ tự nhiên, hero ba cột có nội dung hoạt động thật, điều khiển gọn và ảnh tham chiếu đúng 2/3/6. Bảng bên dưới giữ lịch sử Phase 1. Phép đo mới **81.736 byte** vượt giới hạn 80.000 byte; đã dừng trước Phase 2–3 theo điều kiện 1. [Số đo, ảnh glyph và phương án](../../testing/frontend-landing-dot3-2026-10-13.md#sau-duyệt-b--dừng-ở-giới-hạn-font-trước-phase-23). Chưa coi wireframe/copy cũ là bản cuối sau các chỉnh sửa mới.

## 1. So sánh có cùng điều kiện

Hai route development dùng cùng H1 tĩnh, chữ, bố cục 2 cột, mock UI, chip demo, khoảng cách và bộ điều khiển chuyển động. H1 800, 40–72px, line-height 1,05; body Be Vietnam Pro. B không được đổi sang hero căn giữa riêng vì sẽ làm phép so sánh font/màu thiếu công bằng. Giữ cảm hứng ít chữ, khung thiết bị và chip từ Almanac; bố cục căn giữa có thể xem xét sau khi Tài chọn.

| Mục | A | B |
|---|---|---|
| Display | Plus Jakarta Sans 800 | Fraunces variable wght/opsz/SOFT; hero 800, optical auto, SOFT 30 |
| Nhấn | Cyan #06B6D4 | Hổ phách #F59E0B |
| Cảm giác, nhận định thiết kế | Gọn, trực tiếp, gần giao diện phần mềm | Serif có tương phản nét, nhịp trang trọng hơn |
| Tiếng Việt | Chuỗi kiểm 800 đủ glyph custom | Chuỗi kiểm 600/700/800 đủ glyph custom |
| H1 thực tế 1440px | 3 dòng, 72px | 3 dòng, 72px |
| H1 mobile 375px | 40px; tự xuống dòng | 40px; tự xuống dòng |
| Font display tải khi mở route riêng | **26.964 byte / 26,33 KiB** | **260.560 byte / 254,45 KiB** |
| Chữ trắng / nền tối thuần | 18,72:1 | 18,72:1 |
| Nhấn / nền sáng nhất theo biên bảo thủ | **4,88:1** | **5,54:1** |

Font byte là tổng ba file WOFF2 display tải ở Chrome với cache profile mới, không gồm Be Vietnam Pro chung; không phải JS gzip hay điểm Lighthouse. B giữ đủ trục theo yêu cầu nên tệp biến thiên lớn hơn đáng kể. Chưa kết luận LCP của bản landing hoàn chỉnh; sẽ đo production ở Phase 4. A/B không tải font display của nhau; probe font riêng tải cả hai để kiểm glyph.

| Ảnh | A | B |
|---|---|---|
| Desktop 1440 | [A desktop](../../testing/assets/frontend-landing-dot3-2026-10-13/concept-a-desktop.png) | [B desktop](../../testing/assets/frontend-landing-dot3-2026-10-13/concept-b-desktop.png) |
| Mobile 375 | [A mobile](../../testing/assets/frontend-landing-dot3-2026-10-13/concept-a-mobile.png) | [B mobile](../../testing/assets/frontend-landing-dot3-2026-10-13/concept-b-mobile.png) |
| Reduced motion | [A tĩnh](../../testing/assets/frontend-landing-dot3-2026-10-13/concept-a-reduced-mobile.png) | [B tĩnh](../../testing/assets/frontend-landing-dot3-2026-10-13/concept-b-reduced-mobile.png) |
| Cận tiêu đề | [Chữ A](../../testing/assets/frontend-landing-dot3-2026-10-13/concept-a-title.png) | [Chữ B](../../testing/assets/frontend-landing-dot3-2026-10-13/concept-b-title.png) |
| Trạng thái Thắng | [A Thắng](../../testing/assets/frontend-landing-dot3-2026-10-13/concept-a-won.png) | [B Thắng](../../testing/assets/frontend-landing-dot3-2026-10-13/concept-b-won.png) |

## 2. Nguồn ảnh thực tế

Chỉ lấy nhịp, bố cục hoặc kiểu điều khiển; không dùng lại ảnh, logo, chữ, số liệu hay tính năng của thương hiệu trong ảnh. Ảnh tĩnh không chứng minh hành vi animation.

| Nguồn Tài gửi | Phần áp dụng | Giới hạn / guidance bổ sung |
|---|---|---|
| 1 Wanderlust | Hero tối, serif lớn, lớp UI đặt trước nền | Không lấy ảnh du lịch, thông số hoặc số liệu; không phải ảnh mock CRM |
| **1b Almanac** | Tiêu đề ít chữ, khung thiết bị, chip đổi demo ở K1 | Không lấy agent/AI; không căn giữa riêng B |
| 2 Panerai | Gợi ý phân lớp trong K2 | Đây là carousel đồng hồ, **không có tham chiếu slider trước/sau thực sự**; input range theo APG/native |
| Ảnh CRM tabs không có tên file trong phần đính kèm | K3: hàng tab, chữ cạnh minh họa | **Không có tham chiếu thanh tiến trình 6s**; nhịp theo prompt/APG |
| 4 Skystructure | K5: các vùng có ưu tiên/kích thước khác nhau | Không phải bento CRM; dùng thêm guidance Bento Box Grid |
| 5 “3D Scroll Animation” | K4: thiết bị cạnh nội dung hành trình | Không áp dụng 3D, scroll-jacking hoặc coi ảnh là bằng chứng motion |
| 6 Travel circles | K6: chọn mục để đổi ngữ cảnh | Không có tham chiếu chip ngành thật; chuyển thành tabs có nhãn rõ |
| 7 Sentry | K7: minh họa quy tắc theo nhóm dễ đọc | Ảnh nền sáng, **không có tham chiếu bảo mật nền tối**; navy theo prompt |
| 8 Kito | Ánh sáng xanh, khung sản phẩm cho CTA/hero | Đây là hero; **không có tham chiếu CTA cuối trang riêng** |
| 9a/9b | Phase 5: panel login chia đôi, minh họa | Chưa áp dụng; không lấy ảnh người/tòa nhà, không đổi form |
| 10 Base CRM inline | Giọng doanh nghiệp, sản phẩm là tâm điểm | MISA AMIS: **không có tham chiếu**; không lấy form demo/claims |
| 11 Duralux | Chữ trái, sản phẩm phải, độ rõ của khung UI | Không lấy dashboard/KPI giả |
| 12 Weihu | Thẻ gọn, badge pastel, lớp UI | Không chép task/người/email trong ảnh |

UI UX Pro Max: áp dụng Product Demo + Features, tương tác nhẹ, bento có thứ bậc, pause/reduced motion. Loại bỏ video, 3D, font/palette gợi ý khác, thư viện chuyển động và số liệu bán hàng không có nguồn. Không dùng Super Admin cho landing.

## 3. Wireframe desktop — 1440px

```text
SKIPLINK → MAIN
HEADER 72px: POSE CRM | Tính năng · Cách hoạt động · Vai trò · Bảo mật · Câu hỏi | Đăng nhập
  transparent trên hero; surface khi sentinel hero đi qua trên viewport
K1 NAVY [eyebrow + H1 tĩnh 3 dòng      | chip Cơ hội/Hồ sơ
         câu phụ + cụm xoay            | khung pipeline + Customer 360 + toast
         Đăng nhập / Xem cách...       | nhãn minh họa + figcaption]
         nút dừng toàn bộ hiệu ứng hero
         H2 + 6 chip ngành marquee + nút dừng riêng
K2 SURFACE [Chưa có POSE | tay nắm range 50% | Với POSE]
           2 câu văn luôn đọc được; label điều khiển
K3 CANVAS [5 tab vai trò + tiến trình transform + nút dừng]
          [panel H3 + 3 ý | mock UI] (tất cả panel có trong HTML)
K4 SURFACE [H2 + minh họa sticky | ol 01 Lead → 02 Cơ hội → 03 Khách hàng → 04 Chăm sóc]
           đường tiến trình theo cuộn, không điều khiển cuộn
K5 CANVAS [Customer 360 lớn | Quyền      | Nhập có duyệt]
          [Bàn giao        | Nhiệm vụ   | Đa ngành]
K6 SURFACE [6 tab ngành | H3 sản phẩm/dịch vụ mẫu + dl 2 thuộc tính]
K7 NAVY [3 quy tắc + minh họa ghi chú bị che]
   SURFACE [FAQ details/summary, 2 cột]
   BLUE GRADIENT [H2 CTA + một nút Đăng nhập]
FOOTER © 2026 POSE CRM | Đăng nhập · Câu hỏi thường gặp
```

Header là một landmark thật, không display: contents. Focus ring có offset, scroll-padding = header + 16px. Viewport cao ≤500px bỏ sticky. Hero 2 cột từ 1024px; thẻ v1 của app không thay đổi.

## 4. Wireframe tablet — 768px

```text
HEADER 64px sticky: POSE CRM | Menu
NAV DISCLOSURE nếu mở: danh sách trong luồng, không sticky, không modal
K1: H1 + câu phụ → 2 CTA → dừng → chip demo → mock UI → caption
    dải ngành ngang + dừng
K2: khung range đủ rộng; hai mô tả dưới khung
K3: tabs xuống hàng khi cần → một panel chữ trên, mock dưới
K4: ol dọc + đường tĩnh; bỏ sticky minh họa
K5: bento 2 cột, Customer 360 chiếm cả hàng
K6: tabs xuống hàng → sample + dl
K7: quy tắc trên, mock dưới → FAQ 1 cột → CTA
FOOTER nav xuống hàng
```

Không tạo thanh cuộn ngang cho tabs; đảm bảo focus từng chip rõ. Range dùng native touch/keyboard. Không parallax trên mobile/tablet.

## 5. Wireframe mobile — 375px, kiểm cả 320px

```text
HEADER 64px: POSE CRM | Menu → disclosure danh sách trong luồng
K1: eyebrow → H1 40px (nội dung tĩnh)
    câu phụ → cụm dành cho... → CTA xuống hàng → nút dừng
    chip demo → frame pipeline → Customer 360 → toast → nhãn/caption
    dải ngành + nút dừng (reduced: danh sách xuống dòng, không clone)
K2: 2 lớp so sánh không ép chữ nhỏ → range + label → 2 câu mô tả
K3: 5 tabs xuống dòng → panel H3 + ul → mock → dừng
K4: 4 li dọc, đường tĩnh
K5: 6 ô một cột; viền mảnh, khoảng cách gọn
K6: 6 tabs xuống dòng → sample + dl 2 thuộc tính
K7: 3 ý → mock → 5 details → CTA
FOOTER © + nav
```

Header không che focus/anchor; bỏ sticky khi chiều cao thấp hoặc native zoom làm viewport ≤500px. Không thu chữ để nhét UI. Mock mobile có vùng dành riêng cho toast/ghi chú, không chồng lên caption.

## 6. Landmark và heading sau khi chọn

```text
SkipLink → main#noi-dung
header / nav “Điều hướng trang giới thiệu”
main
  section K1 → H1 Bán nhiều hơn. Chăm sóc tốt hơn. Trên một nền tảng.
    figure [mock aria-hidden] + figcaption; chip/pause là control thật
  section dải ngành → H2 Một nền tảng, nhiều ngành
  section K2 → H2 Khách hàng không rơi giữa phòng ban
  section K3 → H2 Đúng việc cho từng vai trò
    H3 Điều phối / Nối tiếp cơ hội / Chăm sóc có ngữ cảnh / Chuẩn bị dữ liệu / Quản trị theo quyền
  section K4#cach-hoat-dong → H2 Liền mạch từ Lead đến chăm sóc
    ol → H3 Lead / Cơ hội bán hàng / Khách hàng / Chăm sóc
  section K5#nang-luc → H2 Gọn việc, rõ ngữ cảnh
    H3 Customer 360 / Phân quyền theo vai trò / Nhập dữ liệu có duyệt
    H3 Bàn giao khách hàng / Nhiệm vụ và nhắc việc / Đa ngành
  section K6 → H2 Danh mục theo ngành → H3 tên mẫu ngành đang chọn
  section K7#bao-mat → H2 Ai cần gì, thấy đúng đó
  section K7#cau-hoi → H2 Câu hỏi thường gặp → details/summary (không H3)
  section K7 CTA → H2 Đội ngũ của bạn đã sẵn sàng?
footer / nav phụ
```

Mỗi section có aria-labelledby trỏ heading của chính section. FAQ 5 câu/JSON-LD cùng nguồn, answers hiện ngay trong HTML. Role panel chưa chọn hidden, vẫn có trong server HTML. No form, email/credentials, enum kỹ thuật hoặc claim số liệu.

## 7. Tương tác và fallback

- Hero vào một lần 500ms, stagger 80ms; phrase 4s, chuyển Đề xuất/Thắng 6s; floating 6–8s, glow 24s. Một nút dừng chung. Focus vào CTA/chip dừng và không tự chạy lại cho đến khi bấm Tiếp tục; hover tạm dừng. Không dùng HeroTilt tùy chọn ở prototype.
- Marquee 40s, có nút dừng riêng; dừng hover/focus, ngoài viewport và tab ẩn. Clone aria-hidden. Reduced motion chuyển thành danh sách tĩnh, không chạy timer/animation.
- Observer dùng chung trong prototype cho hero, marquee và header; unsupported IO giữ trạng thái tĩnh. Phase 2 chuyển cơ chế dùng chung sang lib/motion, không tạo observer mỗi thẻ.
- K2 range native với label/aria-valuetext, Arrow/Home/End. K3 tabs APG có roving tabindex, manual activation; tự chạy 6s chỉ khi cho phép, dừng focus/interaction đến khi restart rõ ràng. K6 tabs chỉ đổi bằng người dùng, không tự chạy.
- K4 CSS view timeline chỉ đổi transform/opacity; fallback IO đánh dấu bước. K5 demo một lần khi vào viewport hoặc hover, không loop vô hạn không có nút dừng. Nội dung nghiệp vụ vẫn là chữ thật, không phụ thuộc demo.
- Progressive enhancement: nội dung chính server-render; CSS entry animation chỉ no-preference; JS tắt vẫn có H1, mock, sáu ngành. Prototype CTA phụ/nav dẫn tới anchor **v1 đang tồn tại**; sau duyệt mới chuyển tới id v2 `cach-hoat-dong`. Không dựng link tới K4 rỗng trong route thử nghiệm.
- Phase 4 kiểm toàn trang và ngân sách JS/DOM/LCP/CLS/Lighthouse. Phase 5 chỉ sau điểm dừng 2, thêm micro-interaction login/app và kiểm hồi quy thật. Không coi số đo prototype là nghiệm thu landing hoàn chỉnh.

Nguồn hành vi: [APG tabs](https://www.w3.org/WAI/ARIA/apg/patterns/tabs/), [APG carousel](https://www.w3.org/WAI/ARIA/apg/patterns/carousel/), [Pause, Stop, Hide](https://www.w3.org/WAI/WCAG22/Understanding/pause-stop-hide.html), [Reduced motion](https://developer.mozilla.org/en-US/docs/Web/CSS/@media/prefers-reduced-motion), [IntersectionObserver](https://developer.mozilla.org/en-US/docs/Web/API/Intersection_Observer_API).

**Điểm dừng 1:** Tài chọn A hoặc B và duyệt nội dung v2 (H1 lợi ích và dữ liệu mẫu ngành). Chưa thực hiện Phase 2–6.
