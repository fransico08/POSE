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

**Điểm dừng 1 (lịch sử):** đã được Tài duyệt B và nguồn/cấu trúc nội dung. Các bảng A/B phía trên lưu phép so sánh ban đầu, không phải cấu hình đang áp dụng.

## 8. Bản áp dụng sau duyệt B và chỉnh sửa 2–7

Fraunces tĩnh 700/800, vietnamese + latin, không axes. Tổng display 81.736 byte đạt ngân sách đã làm rõ 81.920 byte. Scope token `.landing-page`; app không đổi. Chữ tự nhiên và nguồn mới ở [landing-content-v2.md](landing-content-v2.md), đã sửa trước code; 446 từ marketing mặc định, 445–447 theo tab/cụm xoay. Mock, chip, control, câu trả lời FAQ đóng và panel ẩn không tính; câu hỏi FAQ vẫn tính.

Tham chiếu đúng số: **ẢNH 2 phi hành gia** chỉ cơ chế đường chia + tay nắm ◂▸; **ẢNH 3 Pipedrive** tab phía trên, UI trái và ý phải; **ẢNH 4 nhà gỗ** nhịp lưới; **ẢNH 6 Panerai** minh họa trái, thuộc tính phải, mũi tên/chấm chọn. Không dùng ảnh đính kèm làm asset. 1/1b giữ serif, chiều sâu, khung thiết bị và chip; 5/7/8 là guidance lịch sử, không nhận là ảnh mới gửi.

```text
1440: header 72px; hero 2 cột chữ | khung pipeline lơ lửng + Customer 360/toast
      dải ngành + icon dừng → slider toàn khung → tabs/UI trái/ý phải
      hành trình sticky trái | ol phải → bento 3 cột, ô lớn 2×2
      ngành hình | thuộc tính → bảo mật ý | mock → FAQ 2 cột → CTA → footer
768:  header 64px + disclosure static; hero chữ → mock, dải ngành ngang
      slider → tabs cuộn ngang + panel xếp dọc → hành trình danh sách
      bento 2 cột → ngành minh họa → thuộc tính → bảo mật → FAQ 1 cột → CTA
375:  header 64px; hero chữ → CTA wrap → chip demo → mock → pause góc phải/caption
      dải ngành + pause → slider native → tabs cuộn ngang + UI/ý dọc
      ol tĩnh → bento 1 cột → ngành tabs/hình/thuộc tính/chấm → bảo mật → FAQ → CTA
```

Pipeline 7 thẻ, phân bố 2/3/2 trước chuyển và 2/2/3 sau chuyển; avatar chữ đầu, badge và việc/hạn. Customer 360 có tương tác, nhiệm vụ, cơ hội mẫu; không thanh xám, tiền, KPI hoặc người thật. Mock figure aria-hidden; caption có nhãn “Giao diện minh họa”. H1 tĩnh; “Dành cho…” aria-hidden kèm bản sr-only. Pause 44px góc dưới phải khung, marquee nút icon có aria-label. Secondary CTA trỏ đúng **#cach-hoat-dong**.

Cây heading thực tế:

```text
header (duy nhất) / nav; SkipLink → main#noi-dung
main
  K1 H1 Bán nhiều hơn. Chăm sóc tốt hơn. Trên một nền tảng.
  H2 Một nền tảng, nhiều ngành
  K2 H2 Khách hàng không rơi giữa phòng ban
  K3 H2 Đúng việc cho từng vai trò
     H3 Điều phối cả đội / Theo sát từng cơ hội / Chăm sóc có ngữ cảnh
     H3 Chuẩn bị dữ liệu đúng / Quản trị trong tổ chức (panel ẩn vẫn SSR)
  K4 H2 Liền mạch từ Lead đến chăm sóc
     H3 Lead / Cơ hội bán hàng / Khách hàng / Chăm sóc
  K5 H2 Một nơi cho công việc
     H3 Customer 360 / Phân quyền theo vai trò / Nhập dữ liệu có duyệt
     H3 Bàn giao khách hàng / Nhiệm vụ và nhắc việc / Đa ngành
  K6 H2 Phù hợp cách bạn kinh doanh
     H3 tên sản phẩm/dịch vụ mẫu ngành được chọn
  K7 H2 Ai cần thấy gì, thấy đúng thứ đó
     H2 Câu hỏi thường gặp (details/summary)
     H2 Đội ngũ của bạn đã sẵn sàng?
footer / nav phụ
```

Tất cả section aria-labelledby hợp lệ. Header không display: contents; desktop sticky 72px, mobile chỉ hàng logo/menu 64px, disclosure bên ngoài trong luồng. Scroll-padding 88/80px; chiều cao ≤500px header static/padding16, đã kiểm zoom native 200/400%.

K3 tabs **automatic activation khi dùng phím**, ArrowLeft/Right/Home/End với roving tabindex; panel tabindex0, hidden khi chưa chọn. Tự chạy6s có pause, dừng focus/hover/tương tác, tab ẩn và ngoài viewport. K6 không tự chạy; tabs, mũi tên và **chấm button có tên** đều chọn được ngành. Slider native range có label/aria-valuetext; thêm hai nút 0/100 để xem trạng thái bằng một lần bấm. Journey chỉ sticky/scroll timeline ở desktop hỗ trợ, mobile đường tĩnh; IO dùng chung cho fallback/reveal/auto effects. Không scroll-jacking.

Reduced motion: không data-running/data-motion-ready, không animation, marquee tĩnh wrap/clone ẩn, tab không tự đổi. SSR/no-JS giữ chữ và trạng thái demo tĩnh; H1 không fade từ opacity0 nhằm giữ nội dung sẵn đọc. Hook chỉ khởi động trang trí sau first paint. Không thêm thư viện.

**Điểm dừng 2:** xem ảnh desktop/mobile, chuỗi trạng thái hero và bảng hiệu năng trong [evidence](../../testing/frontend-landing-dot3-2026-10-13.md). Lighthouse mobile/LCP còn chưa đạt; giữ font B và báo số đo trước điều chỉnh ưu tiên tải font. Chưa làm Phase5 login/app hoặc hồi quy backend.

## 9. Sau review — Tài chưa duyệt điểm 2

Đã thực hiện A1–A5/B6–B13: thêm preload latin-ext không thêm file/byte; CSS app ra khỏi public; chữ hero server và H1 hoàn toàn tĩnh. Font B 700/800 giữ nguyên. Mỗi câu H1 là một dòng ở 1440/1024/375; heading balance, đoạn pretty. Cây landmark/heading/FAQ ở mục 8 giữ nguyên, không thêm H1 hoặc đổi nguồn marketing.

```text
1440/1024: chữ 3 dòng, CTA sẵn HTML | chip demo + pipeline 3 cột
            dưới pipeline: Customer 360 trái | toast + badge phải
375: chữ 3 dòng → CTA → pipeline 1 cột đầy đủ chữ → Customer 360 → toast/badge → pause/caption
K2: Chưa có POSE trái | ◂▸ range | Với POSE phải
K3: tabs/progress 6s → mock 5 dòng trái | ý phải (mobile xếp dọc)
K5: timeline 4 hoạt động trong ô lớn; 6 chip ngành nhỏ
K6: badge/tên/thuộc tính trên thẻ mẫu trái | danh sách thuộc tính phải
```

Slider tăng giá trị là tăng phần Chưa có POSE ở trái; Home 0 xem toàn Với POSE, End 100 xem toàn Chưa có POSE. Hai nút lựa chọn cập nhật đúng chiều và aria-valuetext mô tả cả hai. Role/industry tabs giữ bàn phím/focus như trước; nội dung mẫu và nguồn bổ sung trong content v2 mục 10. Reduced motion không chữ “Tĩnh” hoặc icon phát; nút hero/role disabled với câu đầy đủ, marquee ẩn control.

Ảnh review mới prefix `review-*`, giữ ảnh v2 cũ làm lịch sử. Có toàn trang desktop/mobile cho motion/reduced, ảnh từng khối và K3 khi chạy progress. Mobile Performance/LCP vẫn chưa qua cổng sau inlineCss; giữ font và dừng để Tài chọn phương án theo A5. Không sang Phase 5.
