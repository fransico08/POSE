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
