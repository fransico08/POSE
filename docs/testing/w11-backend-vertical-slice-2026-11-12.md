# W11 — Kiểm chứng vertical slice phía backend

Ngày chạy: 12/11/2026 (UTC+07:00)
Phạm vi: phần `[Thắng] BE` của W11 — Assignment → Customer → Task → Customer 360.

## Luồng được kiểm chứng

Integration test `VerticalSliceIntegrationTest` gọi các endpoint Spring MVC thật qua MockMvc. Test được chạy trên H2 in-memory và được đưa vào full backend suite trên PostgreSQL kiểm thử cô lập:

1. Manager tạo Sales Assignment cho một Offering thuộc team mình; API trả trạng thái `CURRENT`.
2. Sales thuộc cùng team đọc được Offering đã phân công.
3. Sales tạo Lead gắn Offering, qualify Lead rồi convert thành Customer.
4. Hệ thống gán Customer cho Sales; Sales ghi Interaction và tạo Task.
5. Sales và Manager đọc Customer 360, thấy Lead đã chuyển đổi, Interaction và Task mở.
6. Kiểm tra audit log cho Assignment, tạo/qualify/convert Lead, tạo Customer, Interaction và Task.

## Kết quả

| Kiểm tra | Kết quả |
|---|---|
| `VerticalSliceIntegrationTest` | **1/1 đạt; 0 failure, 0 error, 0 skipped** |
| Maven reactor (`workflow-core`, `backend`) targeted H2 | **BUILD SUCCESS** |
| PostgreSQL full backend verify | **291 test, 0 failure, 0 error, 13 skipped**; `VerticalSliceIntegrationTest` có trong suite và đạt 1/1 |
| Database | H2 in-memory; PostgreSQL 18.6 cô lập trên `127.0.0.1:54329`; migration test tới V13 |
| Kiểm tra dữ liệu nền | Script xác nhận đúng `data_directory`; restore verifier và Foundation DB v2 assertions PASS; V4 có đủ 6 organization code; 36 user và credential fingerprint gốc không đổi |
| Trạng thái môi trường sau test | PostgreSQL cluster đã dừng; hai database kiểm thử được giữ lại: `pose_foundation_test_20261112143207124` và `pose_v2_review_20261112143207124` |

Targeted H2 test và PostgreSQL full verify được chạy với Maven 3.9.15, Java 22.0.2. Mockito agent được truyền trực tiếp cho Surefire trong lượt test H2 vì dynamic attach trên Windows gặp lỗi pipe access ở lần chạy mặc định. PostgreSQL full verify dùng `backend/scripts/test-postgres-v2.ps1` theo guard trong README, không dùng Supabase hoặc PostgreSQL 5432.

## Kết luận và giới hạn

- Luồng BE W11 trên các endpoint hiện có hoạt động xuyên suốt theo contract; lần kiểm chứng này không tái hiện lỗi cần sửa trong production code.
- Integration test mới khóa luồng bằng một test Spring Boot/MockMvc, bao gồm scope/role, liên kết dữ liệu, Customer 360 và audit.
- Full PostgreSQL verify đạt. Có 13 test bị skip; `POSE_DEMO_PASSWORD` không được cấu hình nên demo acceptance tests bị bỏ qua theo thông báo của runner.
- Đây không phải kiểm thử trình duyệt/UI. FE đã có evidence E2E riêng của Tài tại `vertical-slice-ui-2026-11-05.md`.
