# Nghiệm thu nền tảng Auth/RBAC/Organization/Team — 12/10/2026

Owner card: Huỳnh Minh Tài. Kết quả: đạt điều kiện kỹ thuật để review nhóm; chưa thay thế xác nhận review hoặc tự chuyển Trello.

## Phạm vi và nguồn

- Worktree: `C:\Users\Dell\.codex\worktrees\pose-tai-auth\POSE`, nhánh `Tài`. Không sửa checkout main, gói Nhi, V1–V3 hoặc credentials của 36 user.
- Gói final được tìm thấy tại `D:\POSE\POSE_CRM_DATA_V2_FINAL_SUBMIT.zip` (Downloads không còn file). Giải nén bản sao bằng Python zipfile, đổi dấu `\` trong tên entry thành `/`, vào `backend/target/nhi-final-20261012/POSE_CRM_DATA_V2_FINAL`.
- Backup tương đối: `release/POSE_CRM_DB_v2_FINAL.backup`; pg_dump 17.10, khớp release/BACKUP_SHA256.txt.
- SHA-256: `37994087124A2E38707B3C29853D58B9C0A0BCED959F7DC1875FD921340E48A6`.
- Schema final khớp baseline V1 backend không thay đổi, sau bỏ psql/dump session directives và hai dòng mô tả snapshot. Không sửa V1 để làm khớp. Tương đương V1__pose_crm_schema.sql của Nhi; việc dựng lại toàn bộ seed đã được xác nhận trước task nên không lặp lại.
- Verifier gói final chạy từ gốc gói, báo `TASK 5 PASS`. Assertions foundation của backend đạt trước/sau suite.
- Commit code đã test và chạy app: `bae0ef64e29b259777d14c96181fbbd9ed20dc8c`. Commit tài liệu/evidence riêng không thay implementation.
- Java 21; PostgreSQL 18.3. Chỉ dùng cluster `C:\Users\Dell\.codex\worktrees\pose-tai-auth\POSE\backend\target\auth-postgres-test`, 127.0.0.1:54329, user pose_auth_test. SHOW data_directory được kiểm tra trước tạo/restore/query DB; không dùng 5432.

## Kiểm thử tự động

| Chế độ / nhóm | Đạt | Skip | Lỗi |
|---|---:|---:|---:|
| H2 mặc định: regression API/policy + V4 | 54 | 0 | 0 |
| H2: RestoredV2SmokeTest | 0 | 2 | 0 |
| H2: DemoAccountAcceptanceTest | 0 | 11 template/method | 0 |
| PostgreSQL: regression API/policy | 52 | 0 | 0 |
| Suite PostgreSQL: V4 compatibility chạy H2 | 2 | 0 | 0 |
| PostgreSQL: smoke trên restore final | 2 | 0 | 0 |
| PostgreSQL: tài khoản demo thật | 23 | 0 | 0 |

H2 tổng 67 mục: 54 đạt, 13 skip theo điều kiện môi trường, không lỗi. 11 mục acceptance khi disable là 8 test thường + 3 parameterized template chưa mở rộng; khi bật, lớp này có 23 ca. Suite PostgreSQL tổng 79/79 đạt, không skip/lỗi; có 2 test V4 H2 nên không gọi cả 79 là test dùng PostgreSQL.

Acceptance chỉ bật khi có đồng thời POSE_V2_SMOKE_DATABASE_URL đúng mẫu pose_v2_review_* và POSE_DEMO_PASSWORD. Mật khẩu đọc từ môi trường; MockMvcPrint.NONE, không ghi password/token ra report. Các test restore có transaction rollback; fingerprint credentials và 6 organization/36 user giữ nguyên sau suite.

Ca nghiệm thu: 5 role EDU login/me, permission BẰNG ĐÚNG tập lấy từ role_permissions qua grant hiệu lực; lowercase edu; tài khoản ACTIVE của RET/REA/TECH/LOG/CLI; lỗi INACTIVE/sai tenant/sai password đồng nhất; Admin tạo User 201 và bốn role khác 403; DATA_STAFF sửa Organization 403; Manager team scope; Sales membership scope; Clinic Manager hoạt động dù Admin duy nhất INACTIVE.

Report local: backend/target/acceptance-h2-20261012, backend/target/acceptance-postgres-20261012 và target/surefire-reports của lần chạy cuối. Không commit output chứa dữ liệu môi trường.

## App và HTTP thật

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

Chạy từ worktree nhánh Tài. Các đường dẫn tool theo môi trường hiện tại; POSE_DEMO_PASSWORD cấp vào process theo README gói, giá trị không ghi ở đây.

```powershell
$env:JAVA_HOME = 'C:\Users\Dell\AppData\Local\POSE\tools\jdk-21.0.12.1+1'
$poseMvn = 'C:\Users\Dell\AppData\Local\POSE\tools\apache-maven-3.9.16\bin\mvn.cmd'
$posePackage = '.\backend\target\nhi-final-20261012\POSE_CRM_DATA_V2_FINAL'
# H2: không đặt các biến POSE_TEST_*, POSE_V2_SMOKE_DATABASE_URL, POSE_DEMO_PASSWORD.
Push-Location backend
& $poseMvn -q '-Dlogging.level.root=ERROR' '-Dspring.main.banner-mode=off' verify
Pop-Location
# PostgreSQL: đã cấp POSE_DEMO_PASSWORD cho process, không truyền qua -D.
.\backend\scripts\test-postgres-v2.ps1 -DataPackagePath $posePackage
```

Restore manual và khởi động app (sau khi xác minh cluster riêng):

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

Không có actor Admin ACTIVE trong Clinic để tạo/sửa User hoặc quản trị role/permission. Căn cứ [UserService.java](../../backend/src/main/java/com/pose/backend/identity/UserService.java) dòng 48–49, 78, 104; [RoleService.java](../../backend/src/main/java/com/pose/backend/identity/RoleService.java) dòng 32, 37, 46; ca [DemoAccountAcceptanceTest.java](../../backend/src/test/java/com/pose/backend/auth/DemoAccountAcceptanceTest.java) dòng 189–218. Nhóm chốt kịch bản phục hồi/quản trị Admin cho tenant này. Không sửa dữ liệu/luật quyền và không có mâu thuẫn giữa tài liệu nghiệp vụ với các case đã chạy.

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
