# W05 — Chuyển Lead và đếm Task theo dòng Opportunity

Ngày 04/11/2026 · Checkout `D:\POSE`, nhánh `Tài`, bắt đầu tại `ee9e5c8`. Backend target Java 21; JDK 25.0.2, Maven 3.9.15; H2 MODE PostgreSQL và PostgreSQL 18 trên cluster cô lập `127.0.0.1:54329`. Không chạy frontend, không kết nối Supabase hoặc cổng 5432, không sử dụng tài khoản demo.

## Kiểm chứng

| Phần | Kiểm thử | Kết quả |
|---|---|---|
| 1A | Tạo Customer từ Lead QUALIFIED, giữ contact/owner/Offering, assignment INITIAL, audit không chứa contact | Đạt trên H2 và PostgreSQL |
| 1A | Chuyển Opportunity mở, giữ Opportunity LOST, đọc và PATCH Opportunity sau chuyển | Đạt trên H2 và PostgreSQL |
| 1A | Dùng lại Customer theo email hoặc phone, không sửa hồ sơ | Đạt trên H2 và PostgreSQL |
| 1A | Trùng nhiều hồ sơ, archive, ngoài scope, owner Opportunity không hợp lệ; kiểm lộ ID theo read scope | Đạt trên H2 và PostgreSQL |
| 1A | Trạng thái, version, validation, permission cùng role, scope/tenant và owner không đủ điều kiện | Đạt trên H2 và PostgreSQL |
| 1A | Audit lỗi rollback toàn bộ và hai request đồng thời chỉ tạo một Customer | Đạt trên H2 và PostgreSQL |
| 1A | Lead đã chuyển bị khóa; Lead mới cùng contact được tạo, Customer trùng bị chặn | Đạt trên H2 và PostgreSQL |
| 1B | Task OPEN ở Offering dòng 2 chặn thu hồi một lần; DONE/CANCELLED và Task không liên quan không chặn | Đạt trên H2 và PostgreSQL |

Lệnh H2: xóa khỏi process các biến `POSE_TEST_*`, `POSE_DATABASE_*`, `POSE_V2_SMOKE_DATABASE_URL`, `POSE_DEMO_PASSWORD`, đặt `JAVA_HOME` tới JDK 25.0.2 rồi chạy `C:\Users\Dell\.maven\maven-3.9.15\bin\mvn.cmd '-Dlogging.level.root=ERROR' verify`.

Lệnh PostgreSQL: sau khi xác nhận cổng `54329` không lắng nghe, chạy `powershell.exe -NoProfile -File .\scripts\test-postgres-v2.ps1 -DataPackagePath 'D:\POSE-data\POSE_CRM_DATA_V2_FINAL' -JdkDirectory 'C:\Program Files\Java\jdk-25.0.2' -MavenPath 'C:\Users\Dell\.maven\maven-3.9.15\bin\mvn.cmd'`. Script kiểm data directory của cluster và in `Verified.`; cổng `54329` không lắng nghe sau khi chạy.

| Bộ kiểm thử | Tổng | Đạt | Skip | Lỗi | So với mốc |
|---|---:|---:|---:|---:|---:|
| H2 | 177 | 160 | 17 | 0 | +16 đạt so với 144; test thống kê restore mới skip trên H2 |
| PostgreSQL | 177 | 164 | 13 | 0 | +17 đạt so với 147; `Verified.` |
| `LeadConversionIntegrationTest` | 15 | 15 | 0 | 0 | Chạy cả hai DB |
| `SalesAssignmentIntegrationTest` | 36 | 36 | 0 | 0 | Chạy cả hai DB |

Số test đọc từ Surefire XML. Các test acceptance cần mật khẩu demo vẫn skip.

## Thống kê bản restore

`RestoredV2SmokeTest.reportRestoredLeadConversionCountsWithoutPersonalData` chạy truy vấn tổng hợp chỉ đọc trên database review cô lập, không in contact hay dữ liệu cá nhân:

| Chỉ số | Số đo |
|---|---:|
| Lead QUALIFIED | 18 |
| Lead QUALIFIED có email hoặc phone chuẩn hóa trùng Customer cùng organization | 0 |
| Lead QUALIFIED có Opportunity mở mà owner khác owner Lead | 0 |
| Task OPEN/IN_PROGRESS gắn Opportunity | 30 |

Phép đếm trùng tính cả Customer archive; mỗi Lead được đếm tối đa một lần. Đây là số liệu của bản restore kiểm thử, không phải phép đo Supabase hiện tại.

## Giới hạn

Chuyển đổi là bổ sung ngoài Doc UC-09, không có hoàn tác. Task, Interaction, Feedback và Opportunity đã chốt không chuyển; nguồn Lead của Opportunity đã chuyển chỉ còn trong audit. Không triển khai Task API, Customer 360 hay dashboard. Business rules và roles chưa đồng bộ với Doc; các finding lịch sử về quyền Customer Care vẫn được ghi trong evidence Opportunity. Không thêm migration.
