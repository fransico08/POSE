# Kiểm thử Dashboard KPI API W09 — 06/11/2026

## Phạm vi và môi trường

Card backend xây `GET /api/v1/dashboard/kpis` theo DEC-17; dữ liệu tham chiếu là W09-01, W09-02 và SQL chỉ đọc của Nhi. Commit nền `e2a69d5`; JDK 25.0.2, Maven 3.9.15, PostgreSQL 18.3. Test PostgreSQL chạy trên cluster cô lập `127.0.0.1:54329`; runner kiểm tra `SHOW data_directory` trước khi tạo database. Không chạy với Supabase hoặc cổng 5432.

Lệnh kiểm thử từ gốc repo:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.2'
& 'C:\Users\Dell\.maven\maven-3.9.15\bin\mvn.cmd' -q -pl backend -am '-Dlogging.level.root=ERROR' '-Dlogging.level.com.pose.backend.api.ApiExceptionHandler=OFF' verify
powershell.exe -NoProfile -File .\backend\scripts\test-postgres-v2.ps1 -DataPackagePath 'D:\POSE-data\POSE_CRM_DATA_V2_FINAL' -JdkDirectory 'C:\Program Files\Java\jdk-25.0.2' -MavenPath 'C:\Users\Dell\.maven\maven-3.9.15\bin\mvn.cmd'
powershell.exe -NoProfile -File .\scripts\e2e-env.ps1 -Build
```

Trước H2, các biến `POSE_TEST_*`, `POSE_DATABASE_*`, `POSE_V2_SMOKE_DATABASE_URL`, `POSE_DEMO_PASSWORD` được xóa khỏi process.

## Kết quả kiểm thử (bản build sạch)

Claude Code hoàn tất lượt làm dở của agent: xóa `backend/target/classes`, `target/test-classes`, `target/surefire-reports` và `automation/workflow-core/target` (giữ cluster `target/auth-postgres-test`, không dùng `mvn clean`) rồi chạy từ thư mục gốc. Số đọc từ Surefire XML:

| Bộ | Tổng | Đạt | Skip | Lỗi | Mốc trước (W08) |
|---|---:|---:|---:|---:|---|
| workflow-core | 1 | 1 | 0 | 0 | 1/1 |
| Backend H2 | 233 | 208 | 25 | 0 | 224 / 200 đạt |
| Backend PostgreSQL (`Verified.`, regression DB `pose_foundation_test_20261106170742617`) | 233 | 220 | 13 | 0 | 224 / 211 đạt |
| E2E `scripts/e2e-env.ps1 -Build` | 24 | 24 | 0 | 0 | 24/24 |

Có 9 test Dashboard mới: `DashboardIntegrationTest` 8 ca và `RestoredDashboardSmokeTest` 1 ca. Cả 9 chạy thật trên PostgreSQL (không skip); trên H2 chỉ ca smoke bản restore bị skip như các test bản restore khác. Cổng 8080, 8081, 3010, 3011, 54329 đều dừng sau khi chạy.

### Đối soát độc lập với kết quả SQL gốc của Nhi

Ngoài ca smoke (so với đoạn SQL chuyển thể), review chạy các file `verify_w09_01/02_*_gui.sql` nguyên bản của Nhi trên bản sao V11 (7/7 file xuất PASS, không FAIL), rồi gọi API thật với tài khoản Admin EDU, cửa sổ `[2026-09-01T00:00Z, 2026-10-01T00:00Z)` đúng cửa sổ trong SQL của Nhi:

| KPI | SQL gốc của Nhi | API | Kết quả |
|---|---|---|---|
| A01 / A03 / A04 | 10 Lead; 0,3; 0,1 | 10; 0,3; 0,1 | Khớp |
| A05 | 6/10 Lead có phản hồi, trung bình 194.400 giây | 6/10; 194.400 | Khớp |
| A06 | 20 Customer: 12 active, 8 chưa từng tương tác | ACTIVE 12, NEVER_ENGAGED 8 | Khớp |
| D01 / D02 | 26 Interaction | 26 ở cả hai (5+6+5+8+2 theo kênh; 12+4+10 theo người ghi) | Khớp |
| B01 | 8 Task mở (`expected_kpis.csv`) | 8 | Khớp |

Các ca HTTP bổ sung: Customer Care chỉ nhận nhóm B (B01 = 3 Task của mình so với 8 toàn tổ chức) và A/C/D là `NOT_APPLICABLE`/`OUTSIDE_ROLE_SCOPE`; Admin lọc `ownerUserId` của một Sales thì A01 = 5, B01 = 2 và C01–C03 là `OWNER_FILTER_NOT_SUPPORTED`; `ownerUserId` không có trong tổ chức trả 403 `FORBIDDEN_SCOPE`; `start > end`, timezone sai và tham số `organizationId` trả 400 `VALIDATION_ERROR`; không có token trả 401. Mật khẩu thử ngẫu nhiên chỉ đặt trong bản sao tạm đã xóa; không dùng tài khoản demo thật.

## KPI và vai trò

| KPI | Admin | Data Staff | Manager | Sales | Customer Care |
|---|---|---|---|---|---|
| A01–A05 — Lead | Tổ chức | Tổ chức | Team được quản lý | Scope cá nhân | NOT_APPLICABLE |
| A06 — Customer engagement | Tổ chức | Tổ chức | Team được quản lý | Scope cá nhân | NOT_APPLICABLE |
| B01–B05 — Task | Tổ chức | Tổ chức | Team được quản lý | Task trong scope cá nhân | Task của mình |
| C01–C03 — Workflow | Tổ chức | Tổ chức | NOT_APPLICABLE | NOT_APPLICABLE | NOT_APPLICABLE |
| D01–D02 — Interaction | Tổ chức | Tổ chức | Team được quản lý, theo visibility | Scope cá nhân, theo visibility | NOT_APPLICABLE |

Các ô có phạm vi trả giá trị thực hoặc trạng thái thiếu dữ liệu/mẫu số theo W09-02; không mặc định bằng 0. Quyền `dashboard.read` bắt buộc, các scope áp dụng trước bộ lọc owner. Data Staff kiêm vai trò khác được xem tổng tổ chức, chỉ nhận số liệu tổng hợp.

## Đối soát bản restore

Kiểm thử `RestoredDashboardSmokeTest` tạo actor Admin tạm trong transaction rollback; không thay tài khoản hoặc credentials demo. Khoảng `[2026-09-01, 2026-10-01)` giờ Việt Nam và `asOf` tại cuối khoảng được so với truy vấn độc lập chuyển từ W09-01 sau các điều chỉnh DEC-17. A06 còn so với SQL gốc tại thời điểm hiện tại; ca `asOf` quá khứ có Interaction tương lai được kiểm bằng dữ liệu test.

| Tổ chức | KPI khớp sau điều chỉnh | Lead archive bị loại | Customer archive bị loại | C02: FAILED trong kỳ | Kỳ trống tương đương |
|---|---:|---:|---:|---:|---|
| CLI | 16/16 | 0 | 0 | 0 | C02 |
| EDU | 16/16 | 0 | 0 | 1 | Không trống ở mức tổ chức |
| LOG | 16/16 | 0 | 0 | 0 | C02 |
| REA | 16/16 | 0 | 0 | 0 | C02 |
| RET | 16/16 | 0 | 0 | 0 | C02 |
| TECH | 16/16 | 0 | 0 | 0 | C02 |

Mỗi tổ chức có 10 Lead được tạo trong kỳ, 26 Interaction trong kỳ và 4 Task DONE dùng làm mẫu số B05. Vì không có bản ghi archive, không KPI nào đổi do loại archive trong mẫu này. Không có Interaction Customer sau `asOf` cuối kỳ; ở `asOf` hiện tại, không Customer nào đổi thời điểm Interaction gần nhất so với SQL gốc. B05 mẫu số 0 và A05/D01/D02 kỳ trống được kiểm bằng fixture riêng; C02 kỳ trống được kiểm cả fixture và bản restore.

## EXPLAIN

Trên bản restore demo, các truy vấn đại diện nhóm Lead, Task, Interaction và Workflow dùng `Seq Scan` và `HashAggregate`; bộ dữ liệu nhỏ nên không thêm index hoặc migration. Đây là kế hoạch ước tính, không chứng minh hiệu năng trên dữ liệu lớn.

## FINDING cho Nhi

1. W09-01 B05 trả `NO_DATA_IN_PERIOD` khi mẫu số 0; API dùng `NOT_AVAILABLE_ZERO_DENOMINATOR` theo W09-02 và quyết định của Tài.
2. W09-01 tính cả Lead/Customer archive; DEC-17 loại bản ghi archive. A01–A05 và A06 có thể thay đổi khi dữ liệu có bản ghi archive.
3. W09-01 A06 lấy cả Interaction sau `asOf`; DEC-17 chỉ lấy `occurred_at <= asOf`, tránh phân nhóm sai khi xem thời điểm quá khứ.
4. Lọc owner: W09-02 cần bổ sung phạm vi A01–A06, B01–B05 và D01–D02, cùng `OWNER_FILTER_NOT_SUPPORTED` cho C01–C03.
5. Dictionary/W09-02 nhắc `last_error`, trong khi SQL C02 nhóm workflow type và retry count. API không trả nội dung lỗi tự do; nếu cần phân loại về sau, dùng mã lỗi chuẩn hóa.
6. Kỳ trống: SQL W09-01 xuất 0 ở A05 `NO_RESPONSE`, D01, D02, C02; API trả null với `NO_DATA_IN_PERIOD`. Hai cách được đối soát là tương đương theo quyết định của Tài; đề nghị Nhi thống nhất lại SQL.

Không sửa SQL, CSV, seed hay Data Dictionary của Nhi.

## Giới hạn

- Chưa bổ sung root `dashboard` vào BFF hoặc làm UI; thuộc công việc frontend tiếp theo.
- Số liệu và các chênh lệch chờ Nhi đối soát; chưa đánh giá hiệu năng dữ liệu lớn hoặc môi trường Supabase.
- Cửa sổ đối soát của agent là `[2026-09-01, 2026-10-01)` giờ Việt Nam, còn SQL của Nhi dùng `[2026-09-01T00:00Z, 2026-10-01T00:00Z)`. Lượt đối soát độc lập ở trên dùng cửa sổ UTC của Nhi và vẫn khớp; Nhi nên thống nhất một cách ghi múi giờ trong W09-02.
- A06 chỉ trả các nhóm có bản ghi (ví dụ EDU không có `AT_RISK`, `INACTIVE`); frontend phải coi nhóm vắng mặt là 0 khi vẽ biểu đồ cột chồng.
- Chạy bản build sạch là do reviewer thực hiện; agent trước đó bị từ chối lệnh xóa thư mục build.
