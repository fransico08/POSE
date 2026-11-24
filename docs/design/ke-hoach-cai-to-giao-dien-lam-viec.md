# Kế hoạch cải tổ giao diện không gian làm việc (UI-01)

> Ngày 24/11/2026. Người đề xuất: Tài (agent hỗ trợ). Phạm vi: **chỉ khu vực đăng nhập `/app/*`** (khung ứng dụng, Tổng quan, Báo cáo, các trang danh sách). **Không** đổi trang giới thiệu `/` và trang đăng nhập. Skill dùng: `ui-ux-pro-max` (hướng thiết kế), `frontend-ui-engineering` (triển khai).

## 1. Hiện trạng và vấn đề

| Thành phần | Hiện tại | Vấn đề |
|---|---|---|
| Sidebar | Nền navy `#0f172a`, mọi mục nằm dưới một nhãn "Chung" | `navigation.ts` đã có nhóm (Bán hàng, Chăm sóc, Dữ liệu, Quản trị) nhưng không hiển thị; danh sách dài, khó quét |
| Tổng quan | Lời chào, khối tổ chức/vai trò, "Việc hôm nay" dạng danh sách chữ, lưới lối tắt | Không có số liệu nào; không trả lời được "hôm nay tôi cần làm gì, đang mất gì" |
| Báo cáo | Bộ lọc, ô KPI viền mảnh 3 cột, biểu đồ | Ô KPI giống nhau, không phân cấp; nhóm E (giá trị rò rỉ) không nổi bật dù là điểm nhấn của đề tài |
| Trang danh sách | Bảng trắng, chữ trạng thái thường | Thiếu nhãn trạng thái màu, avatar người phụ trách, bộ lọc nhanh dạng tab |
| Thẩm mỹ chung | Nền `#f4f7fb`, card viền 1px, bo 12px, gần như không có độ sâu | Phẳng, ít nhịp; lệch xa phong cách trang giới thiệu (navy đậm, điểm nhấn amber, tiêu đề Fraunces) |

## 2. Nguyên tắc lấy từ trang giới thiệu (giữ đồng bộ thương hiệu)

- **Màu:** giữ `--pose-primary #2563eb` làm màu thao tác; thêm điểm nhấn **amber** như landing (`#d97706` cho chữ/viền đạt tương phản, `#f59e0b` chỉ dùng làm nền nhỏ hoặc trang trí) cho số liệu "giá trị/rò rỉ" và mục ưu tiên. Sidebar dùng navy của landing (`#0b1220`).
- **Chữ:** thân chữ giữ Be Vietnam Pro. **Fraunces 700** (đã có sẵn file font Việt hóa) chỉ dùng cho tiêu đề trang H1 và con số lớn ở khối chào, tạo cầu nối thị giác với landing; không dùng cho bảng hay form.
- **Chi tiết:** lưới chấm mờ và quầng sáng radial của hero landing chỉ dùng **một lần** ở khối chào Tổng quan, cường độ thấp; không dùng cho trang dữ liệu.
- **Chuyển động:** hover nâng nhẹ `-2px` như nút landing, 150–200 ms, tắt khi `prefers-reduced-motion`.

## 3. Đối chiếu ba mẫu tham khảo

| Mẫu | Lấy | Không lấy | Lý do |
|---|---|---|---|
| **A. Bảng quản lý bán hàng nền xanh nhạt (mẫu "Seller Central")** | Khối chào + hàng 4 thẻ số liệu có icon và xu hướng; bảng chính có tab lọc (Tất cả/Đang hoạt động…) và nhãn trạng thái màu; cột phải "Thao tác nhanh" và "Hoạt động gần đây"; biểu đồ vòng | Hiệu ứng kính mờ toàn trang, emoji trong lời chào, hình 3D, logo thương hiệu | Bố cục này khớp đúng câu hỏi của nhân viên CRM mỗi sáng. AGENTS.md: không mặc định Glassmorphism; không dùng emoji làm icon; không dùng tài sản thương hiệu khác |
| **B. Bộ HR dashboard xanh dương nhẹ** | Thẻ có bóng rất nhẹ, khoảng trắng rộng; segmented tab dạng viên thuốc; dòng bảng có avatar chữ cái đầu; trạng thái rỗng có minh họa đơn giản | Sidebar trắng (giữ sidebar tối để liền mạch với landing) | Tăng độ "thoáng" và dễ quét cho các trang danh sách |
| **C. Customer Journeys kiểu bảng cột quy trình** | Ý tưởng **bảng hành trình theo cột** cho Lead → Khách hàng → Cơ hội, chồng avatar nhóm có số đếm | Đường nối cong, kéo-thả, ảnh chân dung người thật | Hợp với câu chuyện "biến tương tác thành tài sản" nhưng là tính năng mới (xem mục 6, để sau đóng băng) |

Định hướng chung (theo `ui-ux-pro-max`, kiểu **Data-Dense Dashboard**): lưới KPI rõ phân cấp, bảng dày thông tin nhưng dễ quét, màu xanh cho dữ liệu và amber cho điểm cần chú ý, tooltip/hàng hover rõ, không trang trí rườm rà.

## 4. Thiết kế đích

### 4.1. Token bổ sung (`frontend/src/styles/tokens.css`)

| Token | Giá trị | Dùng cho |
|---|---|---|
| `--pose-accent` | `#b45309` (chữ/viền trên nền sáng, ≥4.5:1) | Số liệu rò rỉ, mục ưu tiên |
| `--pose-accent-subtle` | `#fffbeb` | Nền thẻ nhóm E, nhãn "Ưu tiên" |
| `--pose-sidebar-bg` | `#0b1220` (đổi từ `#0f172a`) | Đồng bộ landing |
| `--pose-canvas` | `#f3f6fb` | Nền vùng làm việc |
| `--radius-panel` | `1rem` | Thẻ lớn (khối chào, panel) |
| `--pose-shadow-sm` / `--pose-shadow-md` | `0 1px 2px rgb(15 23 42/.06)` / `0 8px 24px rgb(15 23 42/.08)` | Độ sâu 2 mức; hover dùng `md` |
| `--font-display` | `"Fraunces", "Fraunces Fallback", serif` | H1 và số liệu lớn khối chào |

Không dùng mã màu thô trong component; mọi màu mới qua token. Kiểm tương phản 4.5:1 cho chữ và 3:1 cho viền/biểu tượng.

### 4.2. Khung ứng dụng (sidebar + topbar)

- **Sidebar:** hiển thị **nhóm điều hướng** theo `group` có sẵn (Chung, Bán hàng, Chăm sóc, Dữ liệu); mục đang chọn dạng viên thuốc nền `--pose-sidebar-active-bg` + vạch amber 3px bên trái; giữ thu gọn chỉ còn icon (đã có) với `aria-label` và tooltip; chân sidebar giữ khối người dùng.
- **Topbar:** tiêu đề trang + mô tả ngắn; vùng phải giữ menu người dùng. **Không thêm** ô tìm kiếm toàn cục hay chuông thông báo (chưa có API; sẽ là tính năng mới).
- **Thành phần dùng chung mới:** `PageHeader` (eyebrow, H1, mô tả, khu thao tác), `StatCard` (icon, nhãn, giá trị, chú thích, tone `default|accent|success|danger`), `StatusPill` (map trạng thái → màu + chữ, không chỉ dựa vào màu), `SegmentedTabs` (nhóm nút có `aria-pressed`/tablist), `Avatar` (chữ cái đầu, không ảnh).

### 4.3. Trang Tổng quan (`/app`) — thay đổi chính

```
┌──────────────────────────────────────────────────────────────┐
│ Khối chào (lưới chấm + quầng sáng nhẹ):                       │
│ "Chào buổi sáng, <tên>" · tổ chức · vai trò                   │
├──────────┬──────────┬──────────┬──────────────────────────────┤
│ Thẻ số 1 │ Thẻ số 2 │ Thẻ số 3 │ Thẻ số 4 (accent: rò rỉ)     │
├──────────┴──────────┴──────────┼──────────────────────────────┤
│ Việc hôm nay (thẻ ưu tiên,     │ Thao tác nhanh (theo quyền)   │
│ nhãn loại + lý do + người      ├──────────────────────────────┤
│ phụ trách + nút mở)            │ Hội thoại mới nhất (5 dòng)   │
└────────────────────────────────┴──────────────────────────────┘
```

- **4 thẻ số liệu** lấy từ `GET /dashboard/kpis` đã có, theo quyền và phạm vi người xem (không API mới). Đề xuất: `E01_OVERDUE_LEAD_RESPONSE` (Lead quá hạn phản hồi), `E02_UNATTENDED_OPPORTUNITY_VALUE` (giá trị cơ hội chưa chăm sóc, tone accent), `B02_OVERDUE_OPEN_TASKS` (nhiệm vụ quá hạn), `E04_FIRST_MESSAGE_RESPONSE_TIME` (thời gian phản hồi tin đầu tiên). Chỉ số không áp dụng cho vai trò thì ẩn thẻ, không hiện số 0 giả. Mỗi thẻ là liên kết tới trang Báo cáo hoặc danh sách tương ứng.
- **Việc hôm nay:** giữ dữ liệu `GET /dashboard/today`; trình bày thành thẻ có `StatusPill` loại việc, lý do ưu tiên, người phụ trách, nút "Mở". Trạng thái rỗng có biểu tượng và một câu hướng dẫn.
- **Thao tác nhanh:** thay lưới lối tắt hiện tại bằng danh sách gọn ở cột phải (icon + tên + mô tả 1 dòng), vẫn lọc theo `permittedNavigation`.
- **Hội thoại mới nhất:** `GET /conversations?size=5` (đã có), chỉ hiện khi có quyền `conversation.read`; Data Staff không thấy.
- Khối tổ chức/vai trò chuyển thành dòng phụ trong khối chào.

### 4.4. Trang Báo cáo (`/app/bao-cao`)

- `PageHeader` + bộ lọc kỳ gom vào một thanh.
- **Tab nhóm chỉ số** (SegmentedTabs): Lead · Nhiệm vụ · Workflow · Tương tác · **Giá trị và rò rỉ**; nhóm E dùng tone accent.
- Thẻ KPI dùng `StatCard`, giá trị lớn, chú thích công thức ngắn; trạng thái `NO_DATA_IN_PERIOD`/`NOT_AVAILABLE` hiện chữ rõ ràng, không số 0.
- Biểu đồ giữ Recharts, đặt trong panel có tiêu đề, legend và bảng dữ liệu thay thế cho trình đọc màn hình (đã có bảng — giữ).

### 4.5. Trang danh sách (Lead, Khách hàng, Cơ hội, Nhiệm vụ, Tương tác, Sản phẩm)

- `PageHeader` thống nhất; nút tạo mới ở khu thao tác.
- Bộ lọc trạng thái dạng `SegmentedTabs` khi số trạng thái ≤ 6; còn lại giữ select.
- Bảng: hàng cao tối thiểu 44 px, hover nền nhạt, header dính khi cuộn, cột người phụ trách có `Avatar`, cột trạng thái dùng `StatusPill`. Trên ≤ 767 px chuyển thẻ (đã có ở một số trang — thống nhất).
- **Hội thoại:** giữ bố cục 3 cột; chỉ áp token, `StatusPill` kênh/trạng thái, avatar.

### 4.6. Ràng buộc bắt buộc

- Không đổi API, không đổi backend, không thêm thư viện UI/animation (stack đã chốt: Next.js, Tailwind/CSS hiện có, TanStack Query, Recharts).
- Giữ nguyên tên truy cập (accessible name) của nút, heading, liên kết để Vitest/E2E hiện có không gãy; nếu buộc đổi chữ thì sửa test cùng commit.
- WCAG 2.2 AA: tương phản, focus nhìn thấy, bàn phím, reflow 320 px/zoom 200%, `prefers-reduced-motion`.
- Ẩn/hiện theo `permissions` của `/auth/me`, không theo tên vai trò.
- Dữ liệu hiển thị là dữ liệu demo; không ảnh chân dung người thật.

## 5. Lộ trình (trước mốc đóng băng 04/12, DEC-34)

| Giai đoạn | Ngày | Nội dung | Kết quả kiểm |
|---|---|---|---|
| **UI-01a** | 24–26/11 | Token mới, sidebar có nhóm, `PageHeader`, `StatCard`, `StatusPill`, `SegmentedTabs`, `Avatar` | Vitest, typecheck, lint; ảnh trước/sau 1440 px và 375 px |
| **UI-01b** | 26–28/11 | Trang Tổng quan theo mục 4.3 | Vitest mới cho thẻ số liệu theo quyền, trạng thái rỗng/lỗi; E2E mặc định |
| **UI-01c** | 29/11–01/12 | Trang Báo cáo theo mục 4.4 | Đối soát số trên thẻ khớp API (không đổi logic); E2E |
| **UI-01d** | 01–03/12 | Trang danh sách và Hội thoại theo mục 4.5 | E2E đầy đủ, kiểm bàn phím, 375 px |
| Chốt | 03/12 | E2E mặc định + worker, a11y kiểm tay, ảnh chụp cho Chương 4 báo cáo | Giai đoạn chưa xong thì **không merge**, giữ giao diện cũ |

Mỗi giai đoạn là một nhóm commit riêng, có thể dừng ở bất kỳ giai đoạn nào mà ứng dụng vẫn nhất quán.

## 6. Để sau đóng băng / KLTN

- Bảng hành trình theo cột (mẫu C) cho Lead → Khách hàng → Cơ hội, kéo-thả đổi giai đoạn.
- Tìm kiếm toàn cục, trung tâm thông báo, chế độ tối cho vùng làm việc.
- Xu hướng so kỳ trước trên thẻ số liệu (cần gọi thêm kỳ so sánh hoặc API mới).
