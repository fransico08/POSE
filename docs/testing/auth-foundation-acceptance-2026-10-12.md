# Nghiệm thu nền tảng Auth/RBAC/Organization/Team — 12/10/2026

Owner card: Huỳnh Minh Tài. Kết quả: đạt điều kiện kỹ thuật để review nhóm; chưa thay thế xác nhận review hoặc tự chuyển Trello.

## Phạm vi và nguồn

- Worktree: `C:\Users\Dell\.codex\worktrees\pose-tai-auth\POSE`, nhánh `Tài`. Không sửa checkout main, gói Nhi, V1–V3 hoặc credentials của 36 user.
- Gói final được tìm thấy tại `D:\POSE\POSE_CRM_DATA_V2_FINAL_SUBMIT.zip` (Downloads không còn file). Đợt review giải nén bản sao nguyên vẹn bằng Python zipfile, đổi `\` thành `/`, ra **D:\POSE-data\POSE_CRM_DATA_V2_FINAL**. Không dùng gói dưới backend/target vì mvn clean xóa cả dữ liệu/cluster/report trong target; dừng cluster trước khi clean.
- Backup tương đối: `release/POSE_CRM_DB_v2_FINAL.backup`; pg_dump 17.10, khớp release/BACKUP_SHA256.txt.
- SHA-256: `37994087124A2E38707B3C29853D58B9C0A0BCED959F7DC1875FD921340E48A6`.
- Schema final khớp baseline V1 backend không thay đổi, sau bỏ psql/dump session directives và hai dòng mô tả snapshot. Không sửa V1 để làm khớp. Tương đương V1__pose_crm_schema.sql của Nhi; việc dựng lại toàn bộ seed đã được xác nhận trước task nên không lặp lại.
- Verifier gói final chạy từ gốc gói, báo `TASK 5 PASS`. Assertions foundation của backend đạt trước/sau suite.
- Commit HTTP nghiệm thu gốc: `bae0ef64e29b259777d14c96181fbbd9ed20dc8c`. Review runner tại `5951e3a`, source test cuối tại `c36a2832a0355cc99a4e345af3ab9f52d6a5304b`. Commit tài liệu/evidence riêng không đổi implementation.
- Đợt review: Windows PowerShell **5.1.26100.9444**, Oracle JDK **25.0.2** (25.0.2+10-LTS-69), Maven **3.9.15**, PostgreSQL **18.3**. POM giữ target bytecode Java 21. HTTP gốc chạy PowerShell 7.6.5/JDK 21.0.12.1+1/Maven 3.9.16; không gán kết quả HTTP lịch sử cho môi trường PS 5.1. Chỉ dùng cluster `C:\Users\Dell\.codex\worktrees\pose-tai-auth\POSE\backend\target\auth-postgres-test`, 127.0.0.1:54329, user pose_auth_test. SHOW data_directory được kiểm tra trước tạo/restore/query DB; không dùng 5432.

## Kiểm thử tự động

| Chế độ / nhóm | Đạt | Skip | Lỗi |
|---|---:|---:|---:|
| H2 mặc định: regression API/policy + V4 | 54 | 0 | 0 |
| H2: RestoredV2SmokeTest | 0 | 2 | 0 |
| H2: DemoAccountAcceptanceTest | 0 | 13 template/method | 0 |
| PostgreSQL: regression API/policy | 52 | 0 | 0 |
| Suite PostgreSQL: V4 compatibility chạy H2 | 2 | 0 | 0 |
| PostgreSQL: smoke trên restore final | 2 | 0 | 0 |
| PostgreSQL: tài khoản demo thật | 27 | 0 | 0 |

Đợt review chạy trực tiếp bằng powershell.exe 5.1, không gộp stderr khi gọi script. Baseline **79/79** đạt đúng cơ cấu 26 Auth + 23 Acceptance + 12 Identity + 2 V4 + 2 Smoke + 10 Team + 4 Policy.

Sau thêm 4 ca review, suite **83/83** đạt: 26 Auth + **27 Acceptance** + 12 Identity + 2 V4 + 2 Smoke + 10 Team + 4 Policy; không skip/lỗi. Hai test V4 vẫn dùng H2 trong suite PostgreSQL.

H2 tổng **69 mục: 54 đạt, 15 skip**, không lỗi. 13 mục acceptance khi disable là 9 test thường + 4 parameterized template chưa mở rộng; khi bật có 27 ca. Hai smoke skip khi không có URL restore.

Acceptance chỉ bật khi có đồng thời POSE_V2_SMOKE_DATABASE_URL đúng mẫu pose_v2_review_* và POSE_DEMO_PASSWORD. Mật khẩu đọc từ môi trường; MockMvcPrint.NONE, không ghi password/token ra report. Các test restore có transaction rollback; fingerprint credentials và 6 organization/36 user giữ nguyên sau suite.

Ca nghiệm thu: 5 role EDU login/me, permission BẰNG ĐÚNG tập lấy từ role_permissions qua grant hiệu lực; lowercase edu; tài khoản ACTIVE của RET/REA/TECH/LOG/CLI; lỗi INACTIVE/sai tenant/sai password đồng nhất; Admin tạo User 201 và bốn role khác 403; DATA_STAFF sửa Organization 403; Manager team scope; Sales membership scope; Clinic Manager hoạt động dù Admin duy nhất INACTIVE.

Report review local: backend/target/review-ps51-baseline-79, backend/target/review-ps51-postgres-83, backend/target/review-ps51-h2 và target/surefire-reports. Không commit output chứa dữ liệu môi trường.

## Lỗi runner PS 5.1 đã sửa

- Start-Process -PassThru chưa giữ Handle có thể trả ExitCode null; điều kiện cũ báo Dedicated PostgreSQL startup failed dù server đã start. Thêm `$null = $poseControl.Handle` ngay sau Start-Process trước đọc ExitCode. Rà soát chỉ một vị trí đọc ExitCode trong script tracked; artifact manual tạm cũng được thêm giữ Handle.
- Bỏ mặc định đường dẫn tool cũ: JdkDirectory dùng JAVA_HOME, MavenPath chọn mvn đầu tiên trên PATH. Get-Command trong PS 5.1 có thể trả nhiều ứng viên nên phải Select-Object -First 1. Lần nghiệm thu truyền rõ JDK 25 và Maven 3.9.15.
- RequireAcceptance thiếu POSE_DEMO_PASSWORD throw trước thao tác DB; sau Maven bắt buộc report acceptance mới, có test chạy, không skip/lỗi trước in Verified.
- Bốn probe PS 5.1 đạt: thiếu mật khẩu; thiếu JAVA_HOME; không tìm thấy Maven; discovery từ JAVA_HOME/PATH đi đến guard checksum mà không ghi DB.
- Ba ca self-only của edu.s01/edu.care/edu.data trả totalItems=1, đúng ID/email chính mình. Ca vòng đời demo chứng minh refresh token khác giá trị, JWT gốc còn hoạt động trước logout và JWT gốc/refresh cùng bị 401 sau logout; refresh sau logout cũng 401. Chỉ assert boolean để không lộ token khi test lỗi.

## App và HTTP thật — kết quả lịch sử giữ nguyên

- `mvn spring-boot:run` từ backend, bind 127.0.0.1:18081. Listener được xác nhận thuộc cây process Maven vừa khởi động trước khi gửi credential.
- Database riêng: `pose_v2_review_manual_20261012093550519`. Restore final sạch, baseline version 1 chỉ cho lần onboarding này. JWT secret sinh ngẫu nhiên 32 byte lúc chạy, không lưu giá trị ra file/evidence.
- 35 request nghiệm thu bên dưới đạt; không tính probe readiness. Mỗi login/me thành công đối chiếu đúng tenant, role và tập permission với DB, không chỉ so số lượng.
- HTTP chỉ phát sinh auth session/audit trong bản restore manual; không tạo/sửa user nghiệp vụ hoặc reset credentials. Fingerprint toàn bộ user và password hash/user ID trước/sau đều không đổi; không ghi fingerprint credential vào evidence.

| Flyway version | Loại | Success |
|---|---|---|
| 1 | BASELINE | true |
| 2 | SQL — Auth session | true |
| 3 | SQL — Organization code | true |
| 4 | SQL — Demo code | true |

V4 có đủ edu/rea/tech/ret/log/cli, thỏa constraint V3. H2 không lỗi, organization ngoài danh sách giữ code tự sinh và mã riêng đã có không bị ghi đè.

| Ca | Request (base /api/v1) | Tenant | Status | Role trả về | Số permission |
|---|---|---|---:|---|---:|
| EDU ADMIN đăng nhập | `POST /auth/login` | EDU | 200 | ADMIN | 28 |
| EDU ADMIN hồ sơ | `GET /auth/me` | EDU | 200 | ADMIN | 28 |
| EDU MANAGER đăng nhập | `POST /auth/login` | EDU | 200 | MANAGER | 23 |
| EDU MANAGER hồ sơ | `GET /auth/me` | EDU | 200 | MANAGER | 23 |
| EDU SALES_REP đăng nhập | `POST /auth/login` | EDU | 200 | SALES_REP | 15 |
| EDU SALES_REP hồ sơ | `GET /auth/me` | EDU | 200 | SALES_REP | 15 |
| EDU CUSTOMER_CARE đăng nhập | `POST /auth/login` | EDU | 200 | CUSTOMER_CARE | 9 |
| EDU CUSTOMER_CARE hồ sơ | `GET /auth/me` | EDU | 200 | CUSTOMER_CARE | 9 |
| EDU DATA_STAFF đăng nhập | `POST /auth/login` | EDU | 200 | DATA_STAFF | 10 |
| EDU DATA_STAFF hồ sơ | `GET /auth/me` | EDU | 200 | DATA_STAFF | 10 |
| RET manager đăng nhập | `POST /auth/login` | RET | 200 | MANAGER | 23 |
| RET manager hồ sơ | `GET /auth/me` | RET | 200 | MANAGER | 23 |
| REA manager đăng nhập | `POST /auth/login` | REA | 200 | MANAGER | 23 |
| REA manager hồ sơ | `GET /auth/me` | REA | 200 | MANAGER | 23 |
| TECH manager đăng nhập | `POST /auth/login` | TECH | 200 | MANAGER | 23 |
| TECH manager hồ sơ | `GET /auth/me` | TECH | 200 | MANAGER | 23 |
| LOG manager đăng nhập | `POST /auth/login` | LOG | 200 | MANAGER | 23 |
| LOG manager hồ sơ | `GET /auth/me` | LOG | 200 | MANAGER | 23 |
| CLI manager đăng nhập | `POST /auth/login` | CLI | 200 | MANAGER | 23 |
| CLI manager hồ sơ | `GET /auth/me` | CLI | 200 | MANAGER | 23 |
| Manager Clinic xem User | `GET /users` | CLI | 200 | — | — |
| Manager Clinic xem Team | `GET /teams` | CLI | 200 | — | — |
| Manager Clinic xem Organization | `GET /organization` | CLI | 200 | — | — |
| Đăng nhập edu chữ thường | `POST /auth/login` | edu | 200 | MANAGER | 23 |
| Admin Clinic INACTIVE | `POST /auth/login` | CLI | 401 | — | — |
| Sai mật khẩu | `POST /auth/login` | EDU | 401 | — | — |
| EDU Manager đăng nhập sai tenant RET | `POST /auth/login` | RET | 401 | — | — |
| Data Staff không được sửa Organization | `PATCH /organization` | EDU | 403 | — | — |
| Manager không được tạo User | `POST /users` | EDU | 403 | — | — |
| Manager xem Team mình quản lý | `GET /teams/38bb3193-4121-569f-b095-d0ce10f45392` | EDU | 200 | — | — |
| Manager xem Team RET ngoài scope | `GET /teams/2bf47c90-3790-57aa-be77-8610ec31d5ef` | EDU | 403 | — | — |
| Sales xem Team RET không là member | `GET /teams/2bf47c90-3790-57aa-be77-8610ec31d5ef` | EDU | 403 | — | — |
| EDU Admin refresh | `POST /auth/refresh` | EDU | 200 | ADMIN | 28 |
| EDU Admin logout | `POST /auth/logout` | EDU | 204 | — | — |
| EDU Admin me sau logout | `GET /auth/me` | EDU | 401 | — | — |

Ba lỗi credential đều 401 AUTH_INVALID_CREDENTIALS, cùng message/fieldErrors và cùng cấu trúc JSON; traceId được sinh riêng cho mỗi request nên chỉ khác giá trị traceId. Response không lộ trạng thái INACTIVE. Các từ chối quyền trả FORBIDDEN_PERMISSION; ngoài team/tenant trả FORBIDDEN_SCOPE. Sau logout, JWT đã thu hồi trả 401 AUTH_INVALID_TOKEN.

App và cluster do task khởi động đã được dừng; không còn listener 18081/54329 sau nghiệm thu. DB test riêng giữ để truy vết, gói final không đổi.

## Lệnh đã dùng

Chạy từ worktree nhánh Tài bằng **Windows PowerShell 5.1** (powershell.exe). Runtime phiên review theo đường dẫn sau; POSE_DEMO_PASSWORD cấp vào môi trường, giá trị không ghi ở đây.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.2'
$poseMvn = 'C:\Users\Dell\.maven\maven-3.9.15\bin\mvn.cmd'
$posePackage = 'D:\POSE-data\POSE_CRM_DATA_V2_FINAL'
# H2: không đặt các biến POSE_TEST_*, POSE_V2_SMOKE_DATABASE_URL, POSE_DEMO_PASSWORD.
Push-Location backend
& $poseMvn -q '-Dlogging.level.root=ERROR' '-Dspring.main.banner-mode=off' verify
Pop-Location
# PostgreSQL: đã cấp POSE_DEMO_PASSWORD cho process, không truyền qua -D.
powershell.exe -NoProfile -File .\backend\scripts\test-postgres-v2.ps1 `
  -DataPackagePath $posePackage `
  -JdkDirectory 'C:\Program Files\Java\jdk-25.0.2' `
  -MavenPath $poseMvn `
  -RequireAcceptance
```

Khối dưới là minh họa cách chạy app của lần HTTP gốc, không được chạy lại trong đợt review PS 5.1. Môi trường gốc JDK 21/Maven 3.9.16/PowerShell 7.6.5 đã ghi ở mục nguồn; chỉ dùng database restore mới khi muốn lặp lại:

```powershell
$posePgBin = 'C:\Program Files\PostgreSQL\18\bin'
$poseDb = 'pose_v2_review_manual_20261012093550519'
& "$posePgBin\psql.exe" -w -h 127.0.0.1 -p 54329 -U pose_auth_test -d postgres -Atc 'SHOW data_directory'
& "$posePgBin\createdb.exe" -w -h 127.0.0.1 -p 54329 -U pose_auth_test $poseDb
& "$posePgBin\pg_restore.exe" -w -h 127.0.0.1 -p 54329 -U pose_auth_test -d $poseDb --exit-on-error --no-owner --no-privileges "$posePackage\release\POSE_CRM_DB_v2_FINAL.backup"
$env:POSE_DATABASE_URL = "jdbc:postgresql://127.0.0.1:54329/$poseDb"
$env:POSE_DATABASE_USERNAME = 'pose_auth_test'
$env:POSE_DATABASE_PASSWORD = ''
$env:SPRING_FLYWAY_BASELINE_ON_MIGRATE = 'true'
$env:SPRING_FLYWAY_BASELINE_VERSION = '1'
$poseBytes = [byte[]]::new(32)
$poseRng = [Security.Cryptography.RandomNumberGenerator]::Create()
$poseRng.GetBytes($poseBytes)
$poseRng.Dispose()
$env:POSE_JWT_SECRET_BASE64 = [Convert]::ToBase64String($poseBytes)
$env:PORT = '18081'
$env:SERVER_ADDRESS = '127.0.0.1'
Push-Location backend
& $poseMvn -q spring-boot:run
# HTTP bằng Invoke-WebRequest tại process khác, body password từ env và token chỉ giữ trong RAM.
# Không xuất login/refresh response ra console hoặc ghi token vào file.
```

Helper orchestration local đã chạy: backend/target/manual-acceptance.ps1 (artifact tạm, không đưa vào Git). Nó dùng Start-Process -WindowStyle Hidden không -Wait, kiểm lineage listener, gọi Invoke-WebRequest, chiếu status/role/count rồi dừng riêng cây process Maven và cluster. Các biến onboarding/JWT/database được khôi phục sau khi dừng; không bật baseline mặc định trong application.yml.

## Điểm cần nhóm quyết định

POSE Clinic có một Admin và user này INACTIVE (`database/data/seed/multisector_v2/users.csv` của gói, dòng 32). CLI Manager login/me, GET /users, /teams, /organization đều 200; POST /users bị 403 đúng quyền.

Không có actor Admin ACTIVE trong Clinic để tạo/sửa User hoặc quản trị role/permission. Căn cứ [UserService.java](../../backend/src/main/java/com/pose/backend/identity/UserService.java) dòng 48–49, 78, 104; [RoleService.java](../../backend/src/main/java/com/pose/backend/identity/RoleService.java) dòng 32, 37, 46; ca [DemoAccountAcceptanceTest.java](../../backend/src/test/java/com/pose/backend/auth/DemoAccountAcceptanceTest.java) dòng 227–256. Nhóm chốt kịch bản phục hồi/quản trị Admin cho tenant này. Không sửa dữ liệu/luật quyền và không có mâu thuẫn giữa tài liệu nghiệp vụ với các case đã chạy.

## Chưa làm

- Chống brute-force: ngoài phạm vi giao, để bước hardening.
- OpenAPI/Swagger: ngoài phạm vi giao; contract Markdown giữ nguyên shape API.
- PostgreSQL 16: không có môi trường chứng nhận, task chỉ yêu cầu PostgreSQL 18 cô lập và backup 17.10.
- Frontend, CRM modules, chỉnh sửa Nhi/package, push/merge/PR/Trello: không được giao trong task này.

## Đối chiếu tiêu chí hoàn thành

- [x] V4 đủ 6 mã trên restore; H2 tương thích và giữ fallback/mã riêng.
- [x] Login thật 5 role EDU + 5 tenant còn lại; me có đúng tập permission.
- [x] INACTIVE/sai tenant/sai password cùng lỗi công khai.
- [x] Các case 403 đúng Roles-Authorization và Backend README.
- [x] H2 và suite PostgreSQL đạt; có số test cụ thể.
- [x] Evidence app/HTTP/Flyway/checksum/commit, không password/token.
- [x] README backend/database và API contract cập nhật.
- [x] Code/test lưu commit riêng trên Tài; docs/evidence lưu ở commit cuối, chỉ commit cục bộ. Kiểm worktree sạch và chưa push tại bàn giao.
