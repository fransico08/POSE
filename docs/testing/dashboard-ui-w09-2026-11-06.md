# Kiểm thử giao diện Dashboard W09 — 06/11/2026

Card **W09 — Giao diện Dashboard, bộ lọc và kiểm thử số liệu**, mục **[Tài] FE**. Trang `/app/bao-cao` đọc `GET /api/v1/dashboard/kpis` (DEC-17, [contract](../api/dashboard-kpi-api-v1.md)). Agent làm phần lớn giao diện và test; lượt bị dừng giữa chừng do hết hạn mức, Claude Code hoàn tất phần còn lại, kiểm trên trình duyệt thật, sửa lỗi biểu đồ và ghi evidence. Không sửa backend, SQL, seed hay tài liệu dữ liệu của Nhi.

## Phạm vi đã làm

| Phần | Nội dung |
|---|---|
| Route và menu | `/app/bao-cao`; mục "Báo cáo" bật theo `dashboard.read` (cả 5 vai trò đều có quyền này trong dữ liệu demo) |
| Lớp API | `frontend/src/lib/dashboard.ts`: type theo response thật, query key gắn organization và user, root `dashboard` đã có trong BFF |
| Biểu đồ | `recharts` **3.3.0**, ghim chính xác; phát hành 17/10/2025, `npm view` xác nhận hỗ trợ React 19. Nạp bằng `next/dynamic` (`ssr: false`). Mỗi biểu đồ có bảng số liệu tương đương, tắt animation |
| Bố cục | Thẻ tổng quan A01, A03, A04, B01, B02, C01; nhóm chi tiết A02/A05/A06, B03/B04/B05, C02/C03, D01/D02 theo W09-02 |
| Bộ lọc | Preset 7/30/90 ngày và "Tùy chọn" (dd/MM/yyyy, đến ngày là bao gồm); người phụ trách cho vai trò có `identity.user.read` (Admin, Manager, Data Staff); loại workflow khi có dữ liệu nhóm C. Bộ lọc lưu trên URL |
| Ngữ nghĩa | `null` không bao giờ hiện là 0; `ZERO` hiện 0; `NO_DATA_IN_PERIOD`, `NOT_AVAILABLE*`, `NOT_APPLICABLE` (`OUTSIDE_ROLE_SCOPE`, `OWNER_FILTER_NOT_SUPPORTED`) có thông báo tiếng Việt riêng; A06 bù đủ 4 nhóm; tỷ lệ là phần trăm tối đa 2 chữ số thập phân |
| Tên người | API chỉ trả UUID: vai trò có `identity.user.read` thấy tên; vai trò khác thấy "Bạn" và "Người dùng khác N" |

**Mốc snapshot (`asOf`):** khi chọn "Tùy chọn", giao diện gửi `asOf` bằng cuối kỳ để các KPI snapshot (A01–A04, A06, B01–B03) phản ánh đúng trạng thái tại cuối khoảng đã chọn; preset 7/30/90 ngày dùng thời điểm hiện tại. Lý do: lượt E2E đầu phát hiện A06 dùng `asOf` hiện tại thì nhóm engagement tháng 09 không còn là 12/8. Trang hiển thị rõ mốc phân nhóm.

## Kiểm thử tự động

| Kiểm tra | Kết quả |
|---|---|
| `npx vitest run` | 26 file, **300/300** đạt (thêm 11 test Dashboard so với mốc 289) |
| `npm run typecheck`, `npm run lint`, `npm run build` | Đạt |
| `scripts/e2e-env.ps1 -Build` | **28/28** đạt (57,5 giây) |
| `scripts/e2e-env.ps1` (không build) | **28/28** đạt (53,8 giây) |

E2E mới `frontend/e2e/dashboard.spec.ts` (4 test) và `menu.spec.ts` cập nhật mục "Báo cáo" hiện với cả 6 user. Sau mỗi lượt, cổng 8080, 8081, 3010, 3011, 54329 dừng.

## Đối soát UI ↔ API ↔ SQL của Nhi (EDU, 01/09/2026–30/09/2026)

| KPI | SQL gốc của Nhi | API | Giao diện |
|---|---|---|---|
| A01 Tổng Lead | 10 | 10 | 10 |
| A03 / A04 | 0,3 / 0,1 | 0,3 / 0,1 | 30% / 10% |
| A05 phản hồi đầu | 6/10, trung bình 194.400 giây | 6/10, 194.400 | 6/10, 2 ngày |
| A06 engagement | 12 active, 8 chưa từng tương tác | 12 / 8 | 12 / 8 (đủ 4 nhóm) |
| B01 Task mở | 8 (`expected_kpis.csv`) | 8 | 8 |
| D01 / D02 | 26 Interaction | 26 / 26 | 26 / 26 |

Giao diện dùng cửa sổ giờ Việt Nam (`2026-08-31T17:00Z`–`2026-09-30T17:00Z`), còn SQL của Nhi dùng UTC (`2026-09-01T00:00Z`–`2026-10-01T00:00Z`). Trên dữ liệu demo EDU hai cửa sổ cho cùng kết quả; không chỉnh số kỳ vọng.

Theo vai trò: Customer Care chỉ thấy nhóm Nhiệm vụ (B01 = 3, Task của mình), các nhóm khác báo "Không áp dụng cho vai trò của bạn" và không có ô lọc người phụ trách; Data Staff thấy 16 KPI ở dạng số tổng hợp; Admin lọc theo một Sales thì A01 đổi và nhóm Workflow báo "Không lọc được theo người phụ trách", có nút "Xóa bộ lọc".

## Kiểm trên trình duyệt thật (review)

Môi trường: bản sao cô lập từ `pose_v2_review_*` trên cluster `127.0.0.1:54329` (xác nhận `data_directory`), backend 8081, frontend `frontend-acceptance` 3011; mật khẩu thử ngẫu nhiên chỉ đặt trong bản sao, đã xóa sau khi kiểm.

| Kiểm | Kết quả |
|---|---|
| Màu biểu đồ dùng token (`var(--pose-primary)` …) | Trình duyệt tính đúng thành màu thật (ví dụ `rgb(37, 99, 235)`) |
| **Biểu đồ nhiều chuỗi A06, B03, D01** | **Lỗi phát hiện và đã sửa:** chỉ vẽ trục X, không có trục Y, lưới hay cột. Nguyên nhân: `<Legend>` của Recharts được đo trong vùng vẽ 240px, nhãn tiếng Việt dài xuống dòng khiến chiều cao vùng vẽ âm (trục X ở y ≈ −229). Sửa: bỏ `<Legend>` của Recharts, dùng chú thích HTML bên dưới biểu đồ cùng màu với cột. Sau sửa: A02 4 cột, A06 2 cột chồng, B03 9 cột, D01 5 cột, trục X ở y = 202 trên cả 1265px và 375px |
| Thẻ "Workflow thành công" | **Đã sửa:** thẻ liệt kê 3 loại workflow nhưng chỉ in "Mẫu số" của loại đầu. Nay mỗi loại có tỷ lệ kèm tử số/mẫu số riêng (1/1, 1/1, 1/2) |
| 375px | Không cuộn ngang trang (375/375), thẻ KPI một cột |

Lỗi biểu đồ không bị Vitest (jsdom không đo bố cục) hay E2E cũ phát hiện vì các kiểm tra chỉ đọc bảng số liệu. Đã thêm kiểm tra hồi quy vào `dashboard.spec.ts`: A02/A06/B03/D01 phải có đúng số cột và trục X nằm trong vùng vẽ.

## FINDING

1. **Backend (Tài, W13):** dimensions `ownerUserId`/`userId` chỉ có UUID; vai trò không có `identity.user.read` không xem được tên. Đề xuất API trả thêm tên hiển thị trong dimensions.
2. **Nhi (W09-02):** thống nhất cách ghi múi giờ của cửa sổ đối soát (giờ Việt Nam hay UTC).
3. **Nhi (W09-02):** ghi rõ snapshot dùng `asOf` cuối kỳ khi người dùng chọn khoảng tùy chọn, để đối soát giao diện và SQL dùng cùng mốc.

## Giới hạn

- Chỉ kiểm Chromium; chưa kiểm trình đọc màn hình, zoom 200% và thiết bị thật.
- Chưa đo dung lượng route `/app/bao-cao` (bản build Next 16 không in kích thước route).
- Phần "[Văn] kiểm thử số liệu trên giao diện" để Nhi thực hiện.
