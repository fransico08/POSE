# POSE CRM Backend

Backend là modular monolith Spring Boot, nơi thực thi business rule, RBAC, organization/team scope, transaction, audit và API.

## Công nghệ

- Target bytecode Java 21 theo pom.xml; Spring Boot 3.5.16, Spring Security, JDBC/JPA và Flyway.
- Đợt review đã chạy thực tế bằng Windows PowerShell **5.1.26100.9444**, Oracle JDK **25.0.2** (build 25.0.2+10-LTS-69), Maven **3.9.15**. Không đổi target Java 21.
- PostgreSQL 16 là mục tiêu kiến trúc; đã kiểm thử thực tế trên PostgreSQL 18.3, backup nguồn dùng 17.10. Chưa chứng nhận trên 16.
- OpenAPI/Swagger còn là công nghệ dự kiến; contract hiện dùng [API Contract Foundation v1](../docs/api/api-contract-foundation-v1.md).
- Ngày 12/10/2026, nhánh `Tài` đã nghiệm thu 20 API foundation với tài khoản demo thật của gói final Nhi; đủ evidence để review nhóm. Xem [biên bản nghiệm thu](../docs/testing/auth-foundation-acceptance-2026-10-12.md).

## Module

- Dashboard W16 theo DEC-21: các ngưỡng hệ thống `POSE_KPI_LEAD_RESPONSE_SLA_HOURS` (mặc định 24 giờ) và `POSE_KPI_OPPORTUNITY_IDLE_DAYS` (mặc định 14 ngày); contract và phạm vi tại [Dashboard KPI API](../docs/api/dashboard-kpi-api-v1.md). E04 chờ dữ liệu hội thoại AI-02.
- AI Gateway & AI Copilot (W12 AI-01, W13 AI-03, W14 AI-06): mặc định tắt, provider `mock`; contract và quyền ở [AI Gateway API v1](../docs/api/ai-gateway-api-v1.md). Cấu hình và cách nhập khóa an toàn ở [Biến môi trường CRM.AI](../docs/huong-dan-chay-local.md#biến-môi-trường-crmai). W14 bổ sung API Copilot cho Customer 360 (`POST /api/v1/ai/copilot/customers/{id}/summary`, `/handover-brief`) kèm kiểm tra trích dẫn nguồn có cấu trúc và API đổi người phụ trách Customer tối giản (`POST /api/v1/customers/{id}/reassign`) theo [DEC-20](../docs/meetings/decision-log.md#dec-20--đổi-người-phụ-trách-customer-tối-giản-và-ai-copilot-customer-360-ai-06); xem [evidence W14](../docs/testing/copilot-w14-2026-11-08.md).
- Identity & Access: Auth, User, Role, Permission.
- Organization: Organization, Team, membership có lịch sử.
- Các module Offering/Assignment, CRM, Customer 360, Reporting, Import/Workflow được triển khai ở các card sau theo owner.
- W07 Customer 360: hồ sơ theo scope và API đối soát; xem [contract Customer 360](../docs/api/customer-360-api-v1.md) và giới hạn quyền trong evidence W07.
- W08 workflow: qualify Lead ghi outbox trong transaction; worker chỉ xử lý `LEAD_QUALIFIED`.

Worker **tắt mặc định**. Chỉ Tài quyết định bật trên dữ liệu dùng chung bằng
`POSE_WORKFLOW_WORKER_ENABLED=true`; cấu hình tùy chọn gồm
`POSE_WORKFLOW_WORKER_INTERVAL_MS` (60000), `POSE_WORKFLOW_WORKER_BATCH_SIZE` (20),
`POSE_WORKFLOW_WORKER_MAX_RETRY` (3) và `POSE_WORKFLOW_WORKER_DUE_DAYS` (1).
Worker được lên lịch sau khoảng interval đầu tiên; không dùng token hay mật khẩu
trong payload. Chưa có retry backoff bền vững hoặc lease.

## Chạy local

> Xem hướng dẫn đầy đủ từng bước cho thành viên mới (cả Backend và Frontend) tại [Hướng dẫn chạy local](../docs/huong-dan-chay-local.md).

Cần JDK hỗ trợ compile target Java 21, Maven và PostgreSQL. Build và chạy lệnh từ gốc **checkout nhánh Tài**. Cấu hình `POSE_DATABASE_URL`, `POSE_DATABASE_USERNAME`, `POSE_DATABASE_PASSWORD`; **cảnh báo:** trong `src/main/resources/application.yml`, giá trị mặc định của `POSE_DATABASE_URL` khi thiếu biến môi trường đang trỏ tới `jdbc:postgresql://localhost:5432/pose_crm`, do đó bắt buộc phải đặt biến `POSE_DATABASE_URL` trỏ đúng vào cluster local riêng (ví dụ `jdbc:postgresql://127.0.0.1:54330/pose_crm_local`) hoặc Supabase dùng chung, tuyệt đối không để rơi vào PostgreSQL server thật ở cổng `5432`. `POSE_JWT_SECRET_BASE64` bắt buộc là Base64 của ít nhất 32 byte ngẫu nhiên, không dùng secret fixture test để chạy demo/production. Không dựa vào java trên PATH; đặt JAVA_HOME tới JDK được chọn. Lần review dùng `C:\Program Files\Java\jdk-25.0.2` và `C:\Users\Dell\.maven\maven-3.9.15\bin\mvn.cmd`.

```powershell
cd D:\POSE
mvn -pl backend -am package -DskipTests
java -jar backend/target/pose-backend-0.1.0-SNAPSHOT.jar
```

Database trống chạy Flyway V1 → V2 → … → V11. V1 ở `src/main/resources/db/baseline/` là schema-only trích từ backup v2 (không có seed/password). V2 giữ nguyên migration session/token đã có; V3 thêm code; V4 đồng bộ 6 mã demo final bằng UUID cố định; V5–V11 bổ sung Offering team scope, Sales Assignment permission, system audit actor, gán Offering cũ cho team SALES, trường hồ sơ Customer/Lead, Opportunity lines/closing và lõi Interaction/Task/Audit W06. V1–V4 không bị sửa. Schema/constraint/view đã được đối chiếu bằng restore và test trên baseline thật.

Nếu dùng **bản restore đã đối chiếu đúng backup final Nhi v2** (có thể khởi tạo nhanh qua `scripts/local-db.ps1` ở cổng riêng như `54330`), baseline Flyway **version 1** ở lần khởi động đầu tiên rồi để Flyway tự áp V2 → V11. Có thể bật `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true`, `SPRING_FLYWAY_BASELINE_VERSION=1` chỉ trong lần onboarding bản restore đã kiểm tra; bỏ hai biến sau đó. App không tự bật baseline mặc định. Không dùng cách này cho schema cũ/không rõ nguồn hoặc DB fixture Auth cũ.

Nguồn: gói `POSE_CRM_DATA_V2_FINAL_SUBMIT.zip`, thư mục giải nén `POSE_CRM_DATA_V2_FINAL`; backup tương đối `release/POSE_CRM_DB_v2_FINAL.backup`, pg_dump 17.10. SHA-256 `37994087124A2E38707B3C29853D58B9C0A0BCED959F7DC1875FD921340E48A6`. Không sửa hoặc commit gói Nhi.

V4 đồng bộ metadata.loginCode được công bố vào cột code. Input không phân biệt hoa/thường, DB lưu lowercase:

| Organization | Mã đăng nhập |
|---|---|
| POSE Academy | EDU / edu |
| POSE Realty | REA / rea |
| POSE Tech | TECH / tech |
| POSE Retail | RET / ret |
| POSE Logistics | LOG / log |
| POSE Clinic | CLI / cli |

Organization ngoài danh sách giữ mã do V3 sinh; mã riêng đã có không bị ghi đè. Đọc mã thực tế bằng:

```sql
SELECT code, name FROM organizations ORDER BY name;
```

Mật khẩu demo chỉ đọc từ `POSE_DEMO_PASSWORD`, không lưu vào code/log/evidence và không reset credentials. Đã login thật 5 role EDU và Manager RET/REA/TECH/LOG/CLI; `/auth/me` khớp chính xác role/permission DB. User `cli.admin@example.com` INACTIVE bị từ chối cùng lỗi công khai với sai mật khẩu. Test chạm dữ liệu gốc dùng transaction rollback trên restore riêng; HTTP chỉ tạo session/audit trong DB manual cô lập, không sửa user/credentials.

## Supabase (database dùng chung, DEC-08)

Supabase chỉ thay PostgreSQL; backend vẫn chạy bằng Spring Boot. Người giữ tài khoản Supabase (Tài) làm các bước sau, không đưa mật khẩu vào Git/chat/log.

1. Tạo project trên supabase.com: region Southeast Asia (Singapore), PostgreSQL 17 (mặc định cho project mới; script từ chối bản dưới 17 vì backup là pg_dump 17), mật khẩu database mạnh lưu trong password manager.
2. Khuyến nghị: Project Settings → Data API → tắt Data API. Script vẫn tự thu hồi quyền API và bật RLS kể cả khi chưa tắt.
3. Nút **Connect** → **Session pooler**: lấy host `aws-…pooler.supabase.com`, cổng 5432, user `postgres.<project-ref>`, database `postgres`. Kết nối trực tiếp `db.<ref>.supabase.co` chỉ có IPv6 ở gói free; không dùng transaction pooler 6543.
4. Chạy từ thư mục `backend` bằng Windows PowerShell 5.1, gói Nhi giải nén ngoài `backend/target`. Nhập mật khẩu bằng prompt ẩn để không lưu vào lịch sử lệnh:

```powershell
$env:PGHOST = '<session-pooler-host>'
$env:PGPORT = '5432'
$env:PGDATABASE = 'postgres'
$env:PGUSER = 'postgres.<project-ref>'
$env:PGPASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR((Read-Host 'Supabase DB password' -AsSecureString)))
powershell.exe -NoProfile -File .\scripts\setup-supabase-v2.ps1 `
  -DataPackagePath 'D:\POSE-data\POSE_CRM_DATA_V2_FINAL' `
  -JdkDirectory 'C:\Program Files\Java\jdk-25.0.2' `
  -MavenPath 'C:\Users\Dell\.maven\maven-3.9.15\bin\mvn.cmd'
```

Script chỉ nhận host `*.supabase.com`/`*.supabase.co` và database chưa có bảng/view trong `public`. Thứ tự: kiểm SHA-256 → thu hồi default privileges của `anon`/`authenticated` → `pg_restore --single-transaction --no-owner --no-privileges` (bỏ entry schema `public` vì Supabase đã có) → `verify_v2.sql` của gói và `database/verify-foundation-v2.sql` → build JAR, chạy app một lần trên cổng loopback ngẫu nhiên với baseline Flyway 1 để áp V2–V4 và Hibernate validate, rồi tắt → thu hồi quyền API, bật RLS mọi bảng → kiểm lại assertions, lịch sử Flyway `1,2,3,4`, `6|36`, sáu mã, RLS và quyền API. Khóa JWT trong lần chạy này là ngẫu nhiên, chỉ dùng để khởi động.

`-Rehearsal` chỉ nhận cluster test `127.0.0.1:54329` (kiểm `SHOW data_directory`) và database `pose_supabase_rehearsal_*`, tự tạo role `anon`/`authenticated` giả lập. Đã chạy thử đạt ngày 01/11/2026; chốt chặn từ chối chạy lại trên database đã có dữ liệu, host không phải Supabase và cổng 6543. Cùng ngày Tài chạy trên Supabase thật (project `pose-crm-dev`, ap-southeast-1, Data API tắt khi tạo project, session pooler): verifier gói, assertions foundation hai lần và dòng `Verified` đều đạt. Chưa chạy lại được vì script chỉ nhận database trống. Sau đó backend chạy với Supabase (PostgreSQL 17.11, Flyway báo version 4, không migrate thêm) và Tài đăng nhập thành công qua frontend bằng tài khoản demo. Ngày 02/11/2026, Tài sao lưu Supabase bằng `pg_dump` (ngoài repo) rồi chạy backend từ `Tài`: Flyway áp V5–V7, log báo `Successfully applied 3 migrations`. V8 gán 26 Offering cũ vào team `SALES` của từng tổ chức; V8 áp ở lần chạy backend kế tiếp. Tài áp V9 ngày 03/11/2026 và V10 ngày 04/11/2026 theo cùng cách: sao lưu bằng `pg_dump`, rồi chạy backend từ `Tài`. Sau V10, Supabase ở version 10, có 72 Opportunity và 72 dòng; `anon` và `authenticated` không có quyền SELECT trên `opportunity_lines`. Migration mới áp bằng cách chạy backend, không dùng `setup-supabase-v2.ps1` (script chỉ nhận database trống). Tài áp V11 ngày 05/11/2026 theo cùng cách; log Flyway báo `now at version v11`.

Chạy backend với Supabase: `POSE_DATABASE_URL=jdbc:postgresql://<session-pooler-host>:5432/postgres?sslmode=require`, `POSE_DATABASE_USERNAME=postgres.<project-ref>`, `POSE_DATABASE_PASSWORD` và `POSE_JWT_SECRET_BASE64` riêng. Backend dùng role chủ bảng nên không bị RLS chặn. Session pooler giới hạn 15 client, còn Hikari mặc định giữ 10 kết nối. Vì vậy khi chạy với Supabase, đặt thêm `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=5` để `psql`/`pg_dump` vẫn kết nối được. Nếu gặp `EMAXCONNSESSION`, tắt backend hoặc chờ pooler thu hồi kết nối. Mỗi cửa sổ PowerShell mới phải đặt lại `PG*` và `POSE_DATABASE_*`; thiếu biến thì công cụ sẽ trỏ về `localhost:5432`.

Quy tắc database dùng chung: test làm thay đổi dữ liệu chỉ chạy trên cluster `54329`; migration mới phải đạt ở đó và được duyệt trước khi app chạy lên Supabase. Bảng mới không nhận quyền API nhờ default privileges đã thu hồi; nếu sau này bật lại Data API thì phải bật RLS và viết policy cho bảng mới. Project free tạm dừng khi không hoạt động khoảng một tuần, cần Restore trong dashboard.

## Auth hiện có

| Endpoint | Request | Kết quả |
|---|---|---|
| POST /api/v1/auth/login | organizationCode, email, password | JWT, refresh token, expiresIn, user summary |
| POST /api/v1/auth/refresh | refreshToken | Token xoay vòng và quyền hiện tại |
| POST /api/v1/auth/logout | Bearer + refreshToken cùng phiên | 204; phiên bị thu hồi ngay |
| GET /api/v1/auth/me | Bearer | Hồ sơ, tenant, roles, permissions, team scope |

Password BCrypt; refresh token ngẫu nhiên chỉ lưu SHA-256. JWT mặc định tối đa 15 phút, refresh session 7 ngày tuyệt đối; refresh không kéo dài phiên. Replay thu hồi cả phiên. Mỗi request đọc lại trạng thái/role/permission/membership trong DB, không giữ quyền cũ trong JWT. Manager management scope tách khỏi membership. Auth response no-store; audit không chứa password/token/hash.

## Organization và Team hiện có

| API | Quyền và hành vi |
|---|---|
| GET/PATCH /organization | GET tenant hiện tại; PATCH Admin + organization.team.manage, chỉ name/industry |
| GET /teams, GET /teams/{id} | Admin tenant; Manager team quản lý; user khác team membership hiệu lực |
| POST /teams | Admin hoặc Manager + organization.team.manage; Manager chỉ tạo team do mình quản lý |
| PATCH /teams/{id} | team scope + quyền quản lý; whitelist name/description/status |
| PUT /teams/{id}/members | Thay toàn bộ danh sách; giữ lịch sử ENDED/left_at, hủy membership tương lai bị bỏ |

Team create yêu cầu departmentId (Department ACTIVE cùng tenant), managerUserId (user ACTIVE + role MANAGER hiệu lực). Code uppercase/unique không phân biệt hoa thường, tối đa 60; tên tối đa 160, description tối đa 2000 hoặc null. Manager không thêm user ngoài team scope để mở rộng quyền đọc. Team INACTIVE mất Manager scope ngay; Admin có thể activate nếu Manager còn hợp lệ. Department CRUD/đổi Manager không thuộc foundation v1.

Organization PATCH giữ giới hạn name 150/industry 120, không đổi code/status/tenant. Administration khóa organization trước khi đọc lại quyền, merge và audit để giữ đúng quyền khi mutation đồng thời.

## User, Role và Permission hiện có

| API | Quyền và hành vi |
|---|---|
| GET /users, GET /users/{id} | identity.user.read + tenant/team scope; GET self không cần user.read |
| POST /users | Admin + user.manage + role.manage; băm password, tạo roles/team cùng transaction |
| PATCH /users/{id} | Admin + user.manage; fullName/status/metadata an toàn |
| PUT /users/{id}/roles | Admin + role.manage; không đổi role bản thân, giữ lịch sử grant |
| GET /roles | Authenticated; role code/displayName trong tenant, không kèm permission |
| GET /permissions, GET/PUT /roles/{code}/permissions | Admin + role.manage; sửa role trong tenant, không sửa role actor đang giữ |

POST User trả 201; các GET/PATCH/PUT trả 200; mọi response no-store. User inactive/archive thu hồi session; activate không khôi phục token cũ. Không disable/mất role của Manager đang quản lý Team ACTIVE. Password ban đầu tối thiểu 12 ký tự/tối đa 72 byte UTF-8, giữ nguyên khoảng trắng. Metadata tối đa 4096 ký tự/5 cấp, không nhận key credential/password/token/secret. User email normalized/unique trong tenant.

List User/Team trả items/page/size/totalItems/totalPages, size 1–100, page 0–1000000; User hỗ trợ search tên/email. Lọc scope trước đếm/phân trang. User/Team ID không trong scope hoặc không tồn tại trả 403 FORBIDDEN_SCOPE cùng shape. Validation 400, duplicate/state/audit constraint conflict 409. Mutation/audit cùng transaction; audit lỗi rollback, no-op không tạo audit giả.

## Schema adapter v2

| Nhóm | Mapping đang dùng |
|---|---|
| organizations/users/teams | status ACTIVE/INACTIVE (User thêm ARCHIVED), timestamp/metadata theo DB v2 |
| credentials | password_hash tách khỏi users |
| roles + user_role_grants | role_id, organization_id, granted_at/revoked_at; không dùng user_roles cũ |
| role_permissions | role_id/permission_id, join catalog permissions |
| team_memberships | organization_id/team_id/user_id/status/joined_at/left_at |
| audit_logs | organization_id/actor/entity, before_data/after_data/safe_metadata/result/occurred_at |
| auth_sessions/auth_refresh_tokens | V2 bổ sung; session liên kết user/organization, token chỉ lưu hash |

Nguồn và checksum ở [Database README](../database/README.md). Thư mục H2 fixture chỉ là subset cho regression, không dùng để tạo production.

## Kiểm thử

```powershell
cd D:\POSE
mvn -pl backend -am verify
```

Mặc định: 54 test trên H2 đạt (52 regression API/policy + 2 test V4); smoke/acceptance skip khi không có biến môi trường tương ứng. Fixture bị xóa/tạo dữ liệu lại trước từng case. Test PostgreSQL chỉ chấp nhận URL loopback:54329 với database tên `pose_foundation_test_...`; không chạy trên database demo.

Giải nén gói Nhi ra **ngoài backend/target**, ví dụ `D:\POSE-data\POSE_CRM_DATA_V2_FINAL`; giữ nguyên file gói. **Không chạy `mvn clean` ở bất kỳ cấp nào**: lệnh sẽ xóa `backend/target/auth-postgres-test`.

Chạy từ backend bằng Windows PowerShell 5.1 (`powershell.exe`), sau khi cấp `POSE_DEMO_PASSWORD` cho process hiện tại; không ghi giá trị mật khẩu trong Git/log. Không gộp stderr khi gọi script:

```powershell
powershell.exe -NoProfile -File .\scripts\test-postgres-v2.ps1 `
  -DataPackagePath 'D:\POSE-data\POSE_CRM_DATA_V2_FINAL' `
  -JdkDirectory 'C:\Program Files\Java\jdk-25.0.2' `
  -MavenPath 'C:\Users\Dell\.maven\maven-3.9.15\bin\mvn.cmd' `
  -RequireAcceptance
```

DataPackagePath trỏ tới thư mục có README.md, verify_v2.sql và release/. ExpectedSha256 mặc định là SHA final nêu trên. Không truyền JdkDirectory thì lấy JAVA_HOME; không truyền MavenPath thì chọn mvn đầu tiên trên PATH. Thiếu JDK/Maven được báo lỗi rõ, không khóa cứng thư mục tools cũ.

Bật RequireAcceptance: thiếu POSE_DEMO_PASSWORD sẽ throw trước mọi thao tác DB; report acceptance thiếu/cũ/skip/lỗi cũng bị từ chối trước khi in Verified. Không bật switch thì vẫn cho phép chạy suite không có mật khẩu và ghi rõ acceptance skip. Lớp acceptance chỉ bật khi có cả URL pose_v2_review_* đúng guard và biến mật khẩu.

Runner chỉ dùng cluster <worktree>/backend/target/auth-postgres-test trên 127.0.0.1:54329, kiểm SHOW data_directory trước thao tác DB; thiếu cluster thì initdb riêng dưới target, không dùng 5432. Runner kiểm checksum/manifest, chạy verifier gói trong đúng thư mục gói để resolve include/CSV, rồi assertions backend, migrate/test/build. Sau test vẫn 6 organization/36 user, fingerprint credentials không đổi, đủ 6 code V4. Biến môi trường được khôi phục và cluster do script khởi động được dừng; DB test giữ để truy vết.

Đã sửa lỗi Windows PowerShell 5.1: ngay sau Start-Process -PassThru giữ `$null = $poseControl.Handle` trước khi đọc ExitCode, tránh ExitCode null bị báo startup failed. Các chỗ đọc ExitCode khác đã được rà soát; artifact manual tạm cũng giữ Handle. Bốn probe PS 5.1 kiểm tra thiếu mật khẩu, JAVA_HOME, Maven và discovery tool đều đạt. Không sửa/commit backup, CSV hoặc Data Dictionary Nhi.

Xác minh review 12/10/2026 bằng PowerShell 5.1/JDK 25.0.2/Maven 3.9.15: baseline ban đầu 79/79 đạt. Sau bổ sung 4 ca, suite PostgreSQL **83/83** gồm 52 regression PostgreSQL, 2 test V4 H2, 2 smoke và **27 acceptance**; không skip/lỗi. H2 **54 đạt, 15 skip conditional**, không lỗi; JAR target Java 21 build đạt. Acceptance gồm 5 role EDU, đủ 6 tenant, lowercase code, tập permission chính xác, lỗi credential đồng nhất, Admin/non-Admin create User, team scope và Clinic không có Admin ACTIVE. Ca bổ sung xác nhận GET /users của edu.s01, edu.care, edu.data chỉ trả chính mình với totalItems=1; refresh token đổi giá trị và logout làm cả JWT gốc/lần refresh trả 401 bằng tài khoản demo thật. Kết quả 35 request HTTP/Flyway 1–4 của lần nghiệm thu gốc được giữ trong evidence; không tuyên bố chạy lại HTTP bằng PS 5.1.

## Quy tắc triển khai

Dùng Business Rules, Roles and Authorization, Domain Class Diagram và API Contract hiện hành. Backend giữ authorization/transaction/audit. Card nền tảng đã có implementation và evidence để Review & Test; chưa thay việc review của nhóm và chưa triển khai frontend/card CRM khác.

## Điểm cần nhóm quyết định và chưa làm

Clinic chỉ có một Admin đang INACTIVE. Manager vẫn login/me/list bình thường, nhưng không có actor ACTIVE để dùng API Admin-only của Clinic. Nhóm chốt kịch bản phục hồi/quản trị tenant này; task giữ nguyên dữ liệu final.

Chưa làm chống brute-force, OpenAPI/Swagger và chứng nhận PostgreSQL 16 theo phạm vi đã giao; không mở rộng frontend/CRM. Không push/merge/PR hoặc đổi Trello trong lượt nghiệm thu.
