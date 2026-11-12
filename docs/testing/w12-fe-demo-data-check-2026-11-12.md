# W12 — Kiểm tra giao diện với seed demo mới

Ngày kiểm tra: 12/11/2026. Điểm xuất phát: `f311d93` trên nhánh `Tài`; trong khi kiểm tra có thêm commit tài liệu `c4d075f` từ phiên khác, được giữ nguyên.

## Dữ liệu và môi trường

- Database mới `pose_w12_v12_canonical_test`, PostgreSQL local `127.0.0.1:54330`, data directory `D:\POSE-data\local-pg-54330` đã xác nhận bằng `SHOW data_directory`. Không ghi đè `pose_crm_local`.
- Flyway chạy V1–V12 trên database mới; nạp `database/scripts/seed_v2.psql` từ gốc repo, sau đó chạy `database/scripts/verify_seed_v2.psql`: **TASK 5 PASS**. Backend khởi động tiếp và Flyway áp dụng thành công V13.
- Web local dùng provider AI `mock`. Mật khẩu demo lấy từ biến môi trường, không ghi vào tài liệu hay ảnh.
- Lượt kiểm tra Chromium tự động dùng `scripts/w12-fe-demo-audit.cjs`: vào 9 màn hình cho từng tài khoản, kiểm tra lỗi hiển thị phổ biến, phân trang, vùng 375 px cho Manager và một hồ sơ 360 có thể đọc cho từng tài khoản. Đây là kiểm tra mẫu, không chứng minh mọi bản ghi hay toàn bộ yêu cầu accessibility.

## Màn hình, tổ chức và vai trò

Mỗi ô ghi kết quả theo thứ tự **Tổng quan / Sản phẩm / Lead / Khách hàng / Cơ hội / Tương tác / Nhiệm vụ / tra cứu 360 / Báo cáo / Việc hôm nay**. `✓` là mở được trong lượt kiểm tra; `—` là bị hạn chế theo quyền hoặc không áp dụng; `×` là không đăng nhập được. Bảng được rút gọn vì cùng vai trò cho kết quả giống nhau giữa các tổ chức; riêng Admin CLI khác.

| Tổ chức | Admin | Manager | Sales | Care | Data Staff |
|---|---|---|---|---|---|
| EDU | ✓/✓/✓/✓/✓/✓/✓/✓/✓/✓ | ✓/✓/✓/✓/✓/✓/✓/✓/✓/✓ | ✓/✓/✓/✓/✓/✓/✓/✓/✓/✓ | ✓/—/—/✓/—/✓/✓/✓/✓/✓ | ✓/—/—/—/—/✓/—/✓/✓/— |
| REA | ✓/✓/✓/✓/✓/✓/✓/✓/✓/✓ | ✓/✓/✓/✓/✓/✓/✓/✓/✓/✓ | ✓/✓/✓/✓/✓/✓/✓/✓/✓/✓ | ✓/—/—/✓/—/✓/✓/✓/✓/✓ | ✓/—/—/—/—/✓/—/✓/✓/— |
| TECH | ✓/✓/✓/✓/✓/✓/✓/✓/✓/✓ | ✓/✓/✓/✓/✓/✓/✓/✓/✓/✓ | ✓/✓/✓/✓/✓/✓/✓/✓/✓/✓ | ✓/—/—/✓/—/✓/✓/✓/✓/✓ | ✓/—/—/—/—/✓/—/✓/✓/— |
| RET | ✓/✓/✓/✓/✓/✓/✓/✓/✓/✓ | ✓/✓/✓/✓/✓/✓/✓/✓/✓/✓ | ✓/✓/✓/✓/✓/✓/✓/✓/✓/✓ | ✓/—/—/✓/—/✓/✓/✓/✓/✓ | ✓/—/—/—/—/✓/—/✓/✓/— |
| LOG | ✓/✓/✓/✓/✓/✓/✓/✓/✓/✓ | ✓/✓/✓/✓/✓/✓/✓/✓/✓/✓ | ✓/✓/✓/✓/✓/✓/✓/✓/✓/✓ | ✓/—/—/✓/—/✓/✓/✓/✓/✓ | ✓/—/—/—/—/✓/—/✓/✓/— |
| CLI | × | ✓/✓/✓/✓/✓/✓/✓/✓/✓/✓ | ✓/✓/✓/✓/✓/✓/✓/✓/✓/✓ | ✓/—/—/✓/—/✓/✓/✓/✓/✓ | ✓/—/—/—/—/✓/—/✓/✓/— |

CLI Admin có trạng thái `INACTIVE` trong seed và đăng nhập trả 401; không thay đổi tài khoản. Tổng cộng 29 tài khoản hoạt động vào được, 261 lượt mở màn hình gồm 216 lượt mở và 45 lượt bị hạn chế. Mỗi vai trò hoạt động mở được một hồ sơ 360 trong phạm vi; Data Staff mở qua ID hồ sơ mẫu, không phải danh sách Customer. Sáu Manager gọi Copilot tóm tắt và nhận `SUCCEEDED` từ mock.

Với Manager ở từng tổ chức, nút sang trang 2 hoạt động ở Lead, Customer, Opportunity, Interaction và Task; không phát hiện tràn ngang trên các trang được kiểm ở 375 px. Thời gian từ điều hướng đến hết trạng thái tải trong lần chạy local là 187–3.585 ms cho 261 lượt; đây không phải benchmark hiệu năng hay cam kết SLA. Lượt quét chỉ kiểm các nhãn mã và chuỗi ngày được liệt kê trong script, không loại trừ lỗi ở trang hoặc dữ liệu khác.

## Đối chiếu KPI

Mốc đối chiếu `2026-10-11 00:00` giờ Việt Nam; màn hình Báo cáo dùng Admin, riêng CLI dùng Data Staff vì Admin không hoạt động. Các giá trị hiển thị A01, B01, B02 khớp `database/data/seed/multisector_v2/expected_kpis.csv`:

| Tổ chức | A01 Lead | B01 Task mở/đang làm | B02 Task quá hạn |
|---|---:|---:|---:|
| EDU | 2.040 | 1.799 | 1.650 |
| REA | 2.369 | 1.800 | 1.692 |
| TECH | 2.236 | 1.800 | 1.674 |
| RET | 3.356 | 1.799 | 1.705 |
| LOG | 2.631 | 1.800 | 1.666 |
| CLI | 2.368 | 1.799 | 1.662 |

API cũng được đối chiếu A02 theo trạng thái với CSV tại cùng mốc. Các nhóm A–E có mặt trên màn hình đối với tài khoản có quyền. Chưa đối chiếu từng KPI của cả năm nhóm với CSV: D01 trong CSV bao phủ khoảng 06/01/2025–12/11/2026, dài hơn giới hạn 366 ngày của một request Dashboard. Không kết luận sai lệch từ hai khoảng thời gian khác nhau.

## Lỗi hiển thị đã sửa

Seed có mã loại/kênh như `CALL`, `PHONE` và `CARE_FOLLOW_UP`; danh sách, chi tiết Interaction và Customer 360 từng hiện mã này trực tiếp. Giao diện hiện dùng nhãn tiếng Việt cho các mã đã biết, giữ nguyên chuỗi tùy chỉnh chưa biết và không đổi dữ liệu gửi API. Test bao gồm cả giao diện bảng, thẻ mobile, chi tiết và hồ sơ 360.

## Dữ liệu chuyển Nhi xác nhận

Trên bản nạp có 4.074/10.000 Customer có `related_offering_id` null. Ví dụ trong `database/data/seed/multisector_v2/customers.csv`, cột `related_offering_id`, dòng 122, 126, 127, 129, 134 và 135. Phân bố theo tổ chức:

| Tổ chức | Thiếu | Tổng Customer | Tỷ lệ |
|---|---:|---:|---:|
| EDU | 556 | 1.360 | 40,88% |
| REA | 644 | 1.579 | 40,79% |
| TECH | 609 | 1.491 | 40,85% |
| RET | 907 | 2.237 | 40,55% |
| LOG | 714 | 1.754 | 40,71% |
| CLI | 644 | 1.579 | 40,79% |

Tài xác nhận giao diện khóa chức năng đổi người phụ trách khi thiếu trường này là đúng DEC-20 D1, không sửa FE phần đó. Chuyển Nhi xác nhận dữ liệu thiếu là chủ ý hay cần bổ sung; không tự sửa CSV.

- Có 120 Customer có `customer_type` null và 60 Lead có `source` null; schema cho phép giá trị null của dữ liệu cũ. Ghi nhận để đối chiếu phạm vi dữ liệu kế thừa, chưa tự coi là lỗi.

## Kiểm tra đổi người phụ trách và hồ sơ bàn giao

Tạo `pose_w12_handover_test_20261112` trên cùng cluster 54330 bằng bản sao của `pose_w12_v12_canonical_test`, sau khi xác nhận data directory và database nguồn không có phiên đang kết nối. Chạy web với bản sao và AI mock để các thao tác kiểm thử không sửa bản dùng đối soát.

Trong Chromium, Manager EDU mở Customer có `related_offering_id`, chọn ứng viên hợp lệ khác người hiện tại rồi xác nhận đổi người phụ trách: API trả 200, người phụ trách đã đổi, tạo bản ghi bàn giao và giao diện hiện thông báo thành công. Từ thông báo chọn **Soạn hồ sơ bàn giao**: AI mock trả `SUCCEEDED`, giao diện hiện hồ sơ bàn giao ở trạng thái đề xuất. Không sửa Customer hay phân công để tạo điều kiện thử.

Customer được chọn không có cơ hội mở hoặc nhiệm vụ chưa hoàn thành của người cũ cần chuyển: kết quả lần này là 0 cơ hội và 0 nhiệm vụ. Vì vậy lượt thử này **chưa chứng minh việc chuyển các bản ghi liên quan khi số lượng lớn hơn 0**. Chưa chạy luồng này ở năm tổ chức còn lại.

## Kiểm chứng và giới hạn

- `npm run typecheck`: đạt.
- `npm run lint`: đạt.
- `npx vitest run`: 27 file, 319 test đạt.
- `powershell.exe -NoProfile -File .\scripts\e2e-env.ps1 -Build`: build frontend và backend đạt; Playwright **31/31 đạt** trong 51,2 giây. Runner dùng bản sao database kiểm thử trên cluster 54329, không phải database seed W12 ở 54330; đã dừng cluster và giải phóng các cổng kiểm thử sau khi chạy.
- [Ảnh Báo cáo EDU](w12-fe-demo-2026-11-12/edu-dashboard.png) · [Ảnh hồ sơ 360 EDU](w12-fe-demo-2026-11-12/edu-customer-360.png). Ảnh dùng dữ liệu demo và không chứa mật khẩu.
- Chưa kiểm bằng trình đọc màn hình, chưa đo tải đồng thời, chưa xác thực từng giá trị tiền/ngày của toàn bộ bản ghi hoặc toàn bộ KPI. Không dùng Supabase hay PostgreSQL 5432; không sửa migration hoặc seed của Nhi.

## Review bổ sung ngày 12/11/2026

### Nhãn nhập liệu và bộ lọc

- Form và bộ lọc cùng dùng danh sách nhãn/mã từ `frontend/src/lib/interactions.ts`; người dùng chọn nhãn, request gửi mã. Mã tùy chỉnh vẫn được giữ; form sửa hiển thị nhãn của mã đã biết. Bổ sung TELEGRAM, MESSENGER và ZALO_SIM.
- Vitest kiểm tra payload, form sửa, mã tùy chỉnh, query lọc và label/`aria-describedby`.
- Sửa thêm khi hoàn tất phiên (Claude Code): ô lọc Loại/Kênh trước đó gửi mỗi ký tự lên API, nên khi người dùng gõ dở nhãn tiếng Việt (ví dụ "Cuộc") backend trả 400 "Giá trị lọc không hợp lệ" và danh sách chuyển sang trạng thái lỗi. Nay chuỗi gõ dở được giữ trong ô, chỉ áp dụng khi đã thành mã hợp lệ hoặc rỗng; ô hiện `aria-invalid` và hướng dẫn gắn qua `aria-describedby`. Có test riêng cho trường hợp này.

### Đối soát toàn bộ CSV và nhóm E

SQL độc lập [w12-kpi-reconcile-2026-11-12.sql](w12-kpi-reconcile-2026-11-12.sql) chạy trong transaction chỉ đọc trên `pose_w12_v12_canonical_test` ở cổng 54330, sau khi xác nhận `SHOW data_directory`. Đối chiếu API qua bản sao `pose_w12_review_20261112`. Mốc `asOf=2026-10-10T17:00:00Z` (11/10 00:00 giờ Việt Nam), kỳ `[2026-09-10T17:00:00Z, 2026-10-10T17:00:00Z)`. Ngưỡng E01/E02 tương ứng 24 giờ/14 ngày. EDU, REA, TECH, RET và LOG dùng Admin; CLI dùng Data Staff vì Admin CLI không hoạt động, nên không khẳng định đã kiểm API bằng Admin CLI.

Mọi giá trị dưới đây là **CSV = SQL**, không có cột để trống. A01/A02/B01/B02 cũng đã so trực tiếp với API và khớp 6/6 tổ chức. Các cột khác không có KPI trực tiếp hoặc khác khoảng/định nghĩa, nên đối chiếu bằng SQL thay vì suy luận từ một KPI khác.

| Cột CSV | EDU | REA | TECH | RET | LOG | CLI |
|---|---:|---:|---:|---:|---:|---:|
| customers_total | 1360 | 1579 | 1491 | 2237 | 1754 | 1579 |
| customers_active | 1359 | 1578 | 1490 | 2236 | 1753 | 1578 |
| leads_total | 2040 | 2369 | 2236 | 3356 | 2631 | 2368 |
| leads_new | 1125 | 1290 | 1241 | 1831 | 1426 | 1252 |
| leads_qualified | 612 | 704 | 659 | 1003 | 835 | 737 |
| leads_converted | 1 | 1 | 1 | 1 | 1 | 1 |
| opportunities_total | 1423 | 1384 | 1381 | 1430 | 1350 | 1379 |
| opp_new | 267 | 267 | 268 | 267 | 267 | 267 |
| opp_qualified | 356 | 316 | 314 | 364 | 283 | 312 |
| opp_proposal | 267 | 266 | 266 | 266 | 267 | 266 |
| opp_won | 267 | 268 | 267 | 267 | 267 | 268 |
| opp_lost | 134 | 135 | 134 | 134 | 134 | 134 |
| won_expected_value_vnd | 6962116000.00 | 33876785000.00 | 7109986000.00 | 7047048100.00 | 7249396500.00 | 7168257000.00 |
| interactions_total | 7500 | 7500 | 7500 | 7500 | 7500 | 7500 |
| tasks_open_or_in_progress | 1799 | 1800 | 1800 | 1799 | 1800 | 1799 |
| tasks_overdue_as_of_demo_date | 1650 | 1692 | 1674 | 1705 | 1666 | 1662 |

Ánh xạ từng nhóm cột:

| Cột | Đối chiếu |
|---|---|
| customers_total, customers_active | SQL số Customer hiện có và ACTIVE; Dashboard không có KPI trực tiếp tương ứng. |
| leads_total | A01; API loại archive còn CSV đếm mọi Lead. Bộ seed hiện tại cho cùng kết quả. |
| leads_new, leads_qualified, leads_converted | Các bucket tương ứng của A02; cùng lưu ý archive. |
| opportunities_total, opp_new, opp_qualified, opp_proposal, opp_won, opp_lost | SQL số Opportunity và từng stage; W09 Dashboard không có KPI Opportunity trực tiếp tương ứng. |
| won_expected_value_vnd | SQL tổng expected_value của Opportunity WON, VND; không gọi đây là doanh thu hoặc final value. |
| interactions_total | SQL toàn bộ Interaction. D01/D02 là số theo kỳ; khoảng toàn bộ dữ liệu vượt giới hạn 366 ngày của API nên không so trực tiếp với một request. |
| tasks_open_or_in_progress | B01. |
| tasks_overdue_as_of_demo_date | B02 tại đầu ngày Việt Nam; CSV so ngày, API so instant. Tại mốc này hai cách tương đương. |

Nhóm E, số độc lập SQL **bằng** API tại mốc trên; E02 chỉ có VND trong kết quả:

| Tổ chức | E01 | E02 số cơ hội | E02 giá trị VND | E03 chưa liên hệ / tổng bàn giao | E04 | E05 |
|---|---:|---:|---:|---:|---|---|
| EDU | 1686 | 341 | 7124812000.00 | 22/22 | NOT_SUPPORTED | NOT_AVAILABLE_ZERO_DENOMINATOR (0/0, value null) |
| REA | 962 | 726 | 52379274000.00 | 19/19 | NOT_SUPPORTED | NOT_AVAILABLE_ZERO_DENOMINATOR (0/0, value null) |
| TECH | 1418 | 743 | 18864832000.00 | 20/20 | NOT_SUPPORTED | NOT_AVAILABLE_ZERO_DENOMINATOR (0/0, value null) |
| RET | 2126 | 794 | 18761746750.00 | 33/33 | NOT_SUPPORTED | NOT_AVAILABLE_ZERO_DENOMINATOR (0/0, value null) |
| LOG | 1503 | 654 | 17196081750.00 | 21/21 | NOT_SUPPORTED | NOT_AVAILABLE_ZERO_DENOMINATOR (0/0, value null) |
| CLI | 1486 | 745 | 19095283500.00 | 20/20 | NOT_SUPPORTED | NOT_AVAILABLE_ZERO_DENOMINATOR (0/0, value null) |

| Tổ chức | KPI/cột lệch | Kỳ vọng | Thực tế | Nguyên nhân |
|---|---|---|---|---|
| Không có | Không phát hiện lệch trong các phép so sánh đã nêu | — | — | Các cột khác định nghĩa được đối chiếu SQL riêng như bảng ánh xạ. |

Ghi chú dữ liệu chuyển Nhi: E03 bằng 100% ở cả 6 tổ chức (mọi Customer có bàn giao SALES APPROVED trong 30 ngày trước mốc đều chưa có Interaction của owner mới). Công thức và API khớp SQL độc lập, nhưng phân bố này khiến biểu đồ E03 trên demo luôn ở mức tối đa; cần Nhi xác nhận là chủ ý hay bổ sung Interaction sau bàn giao cho một phần khách hàng.

### Bàn giao trên bản sao

Các Customer có cơ hội mở và Task chưa xong được chọn bằng SQL chỉ đọc từ seed TECH; không sửa các hàng nguồn. Thao tác ghi và fixture D6 chỉ thực hiện trên `pose_w12_review_20261112`. Trường hợp D6 thêm một opportunity line của Offering ACTIVE mà người nhận chưa được phân công **trên bản sao**, sau đó chụp trạng thái trước khi gửi request.

| Trường hợp | Kết quả | Cơ hội mở trước → sau | Task chưa xong trước → sau | Handover trước → sau | Assignment trước → sau | Audit liên quan trước → sau |
|---|---|---|---|---|---|---|
| D2: người nhận đủ điều kiện | 200; owner Customer, 1 cơ hội và 1 Task đổi đúng; AI mock tạo đề xuất hồ sơ bàn giao | 1 → 1, đổi owner | 1 → 1, đổi owner | 1 → 2 | 2 → 3 | 0 → 3: OWNER_REASSIGNED, OPPORTUNITY_ASSIGNED, TASK_ASSIGNED |
| D6: thiếu phân công Offering của một opportunity line | 422 tại `newOwnerUserId`; so sánh snapshot dữ liệu trước/sau không đổi | 1 → 1 | 1 → 1 | 1 → 1 | 2 → 2 | 0 → 0 |
| D1: Customer không có sản phẩm liên quan | Nút bị khóa, có hướng dẫn sửa hồ sơ; không gửi yêu cầu đổi owner | Không thao tác | Không thao tác | Không thao tác | Không thao tác | Không thao tác |

### Ghi nhận sai sót môi trường

Trước khi chuyển ứng dụng sang bản sao, đã thực hiện một lần đăng nhập để xem API trong khi backend còn trỏ canonical. Login tạo phiên/refresh token và audit đăng nhập trên canonical; không ghi vào các bảng seed nghiệp vụ. Sau khi phát hiện đã dừng ứng dụng, tạo bản sao và chuyển toàn bộ login, API, bàn giao tiếp theo sang bản sao. Không xóa hoặc sửa các bản ghi đăng nhập đó. Các phép SQL đối soát canonical dùng transaction chỉ đọc.

### Kiểm thử lượt review

- Typecheck: đạt; lint: đạt; Vitest: 27 file, 323/323 test đạt (gồm test ô lọc gõ dở thêm khi hoàn tất phiên).
- E2E: `scripts/e2e-env.ps1 -Build` 31/31 đạt (1,0 phút), cluster 54329 và các cổng kiểm thử đã dừng.
- Chưa kiểm trình đọc màn hình; chưa có thử nghiệm bàn giao cho từng tổ chức.
