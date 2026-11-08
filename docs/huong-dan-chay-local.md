# Hướng dẫn chạy local POSE CRM (Backend + Frontend)

Tài liệu này hướng dẫn thành viên mới thiết lập môi trường và chạy toàn bộ ứng dụng **POSE CRM** từ máy trống đến khi đăng nhập được trên trình duyệt, đồng thời tổng hợp cách chạy các bộ kiểm thử và xử lý sự cố thường gặp.

---

## 1. Yêu cầu công cụ

Cài đặt đầy đủ các công cụ sau trên Windows (PowerShell):

| Công cụ | Phiên bản yêu cầu | Cách kiểm tra | Ghi chú |
|---|---|---|---|
| **JDK** | `25.0.2` (compile target Java `21`) | `java -version` | Đặt `JAVA_HOME` trỏ tới thư mục cài đặt JDK 25.0.2. |
| **Maven** | `3.9.x` | `mvn -version` | Chạy Maven tại gốc checkout. |
| **Node.js & npm** | Node `24.13.0`, npm `11.6.2` | `node -v`, `npm -v` | Theo cấu hình chốt tại `frontend/package.json` (`engines`). |
| **PostgreSQL 18** | `18.x` (client tools & local server binaries) | `& "C:\Program Files\PostgreSQL\18\bin\psql.exe" --version` | Cần `initdb`, `pg_ctl`, `pg_isready`, `psql`, `pg_restore` trong `C:\Program Files\PostgreSQL\18\bin`. |

---

## 2. Lấy mã nguồn và cài đặt phụ thuộc

1. Mở PowerShell tại thư mục làm việc `D:\POSE` và kiểm tra nhánh hiện tại:
   ```powershell
   cd D:\POSE
   git status -sb
   git checkout Tài
   ```
2. Cài đặt dependencies cho Frontend theo đúng lockfile:
   ```powershell
   cd D:\POSE\frontend
   npm ci
   ```
3. *(Tùy chọn — bắt buộc nếu chạy kiểm thử E2E bằng Playwright)* Cài đặt trình duyệt Chromium cho Playwright:
   ```powershell
   cd D:\POSE\frontend
   npx playwright install chromium
   ```

---

## 3. Thiết lập cơ sở dữ liệu (Chọn 1 trong 2 cách)

Bạn có thể kết nối vào **Supabase dùng chung của nhóm (Cách 1)** hoặc **khởi tạo một cluster PostgreSQL local riêng từ gói dữ liệu V2 của Nhi (Cách 2)**.

> **Quy tắc an toàn bắt buộc:**
> - **Không** dùng hoặc ghi đè lên PostgreSQL server chính của máy ở cổng `5432`.
> - **Không** dùng chung cluster kiểm thử tự động ở cổng `54329` (`backend/target/auth-postgres-test`) cho mục đích chạy dev hằng ngày vì script test/E2E sẽ `DROP DATABASE` mỗi lần chạy.
> - **Không** ghi mật khẩu Supabase hay mật khẩu demo vào file cấu hình, script hoặc tài liệu trong repo.

### Cách 1 — Kết nối Supabase dùng chung (DEC-08) *(Chưa kiểm thực tế trong phiên agent do tuân thủ quy tắc bảo mật không lưu/nhập credential Supabase trong môi trường agent)*

Dùng khi cần xem dữ liệu demo dùng chung mà không cần khởi tạo PostgreSQL local.

1. Liên hệ người giữ credential của nhóm (Tài) để nhận `<session-pooler-host>`, `<project-ref>`, mật khẩu database Supabase và mật khẩu tài khoản demo, theo mục Supabase của `backend/README.md`. **Tuyệt đối không ghi bất kỳ host, project-ref hay mật khẩu thật nào vào repo.**
2. Trong cửa sổ PowerShell sẽ chạy Backend, đặt các biến môi trường sau (chỉ tồn tại trong phiên PowerShell hiện tại, **không ghi vào file**):
   ```powershell
   $env:POSE_DATABASE_URL = "jdbc:postgresql://<session-pooler-host>:5432/postgres?sslmode=require"
   $env:POSE_DATABASE_USERNAME = "postgres.<project-ref>"
   $env:POSE_DATABASE_PASSWORD = Read-Host "Nhập mật khẩu Supabase"
   $env:SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE = "5"

   # Sinh secret JWT ngẫu nhiên tối thiểu 32 bytes (Base64) cho phiên chạy local:
   $jwtBytes = New-Object byte[] 32
   [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($jwtBytes)
   $env:POSE_JWT_SECRET_BASE64 = [Convert]::ToBase64String($jwtBytes)
   ```
3. **Lưu ý quan trọng với Supabase:**
   - Luôn giữ `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=5` để tránh lỗi vượt giới hạn kết nối của Supabase Session Pooler (`EMAXCONNSESSION`).
   - Database Supabase dùng chung đã ở schema `V11`; Flyway sẽ tự kiểm tra lịch sử và không chạy lại migration.
   - **Tuyệt đối không** chạy `mvn test`, `mvn verify`, `test-postgres-v2.ps1` hay `e2e-env.ps1` trỏ vào Supabase.

---

### Cách 2 — Database local riêng từ gói dữ liệu V2 của Nhi (Khuyên dùng khi dev/debug)

Dùng khi muốn có một bản sao dữ liệu độc lập trên máy cá nhân tại cổng riêng (ví dụ `127.0.0.1:54330`, thư mục `D:\POSE-data\local-pg-54330`, database `pose_crm_local`).

#### Bước 2.1: Chuẩn bị gói dữ liệu V2

Đảm bảo gói dữ liệu chuẩn `POSE_CRM_DATA_V2_FINAL` đã được giải nén tại `D:\POSE-data\POSE_CRM_DATA_V2_FINAL` (có file `release\POSE_CRM_DB_v2_FINAL.backup` và `verify_v2.sql`).

#### Bước 2.2: Khởi tạo cluster local và restore dữ liệu V1

**Phương án A — Dùng script trợ giúp an toàn `scripts/local-db.ps1`:**

Script `scripts/local-db.ps1` tự động từ chối cổng `5432`, cổng `54329`, host khác `127.0.0.1` và thư mục `backend/target/auth-postgres-test`, đồng thời luôn kiểm tra `SHOW data_directory;` trước mọi thao tác:

```powershell
cd D:\POSE
powershell.exe -NoProfile -File .\scripts\local-db.ps1 `
  -Action RestoreAndVerify `
  -Port 54330 `
  -Database pose_crm_local `
  -DataDir "D:\POSE-data\local-pg-54330" `
  -PackageRoot "D:\POSE-data\POSE_CRM_DATA_V2_FINAL"
```

Các thao tác quản lý cluster về sau:
- Kiểm tra trạng thái: `powershell.exe -NoProfile -File .\scripts\local-db.ps1 -Action Status -Port 54330 -DataDir "D:\POSE-data\local-pg-54330"`
- Khởi động lại khi bật máy: `powershell.exe -NoProfile -File .\scripts\local-db.ps1 -Action Start -Port 54330 -DataDir "D:\POSE-data\local-pg-54330"`
- Dừng cluster khi nghỉ: `powershell.exe -NoProfile -File .\scripts\local-db.ps1 -Action Stop -Port 54330 -DataDir "D:\POSE-data\local-pg-54330"`

**Phương án B — Chạy từng lệnh thủ công:**

```powershell
$pgBin = "C:\Program Files\PostgreSQL\18\bin"
$dataDir = "D:\POSE-data\local-pg-54330"
$pkgRoot = "D:\POSE-data\POSE_CRM_DATA_V2_FINAL"

# 1. Khởi tạo và bật cluster riêng ở cổng 54330
& "$pgBin\initdb.exe" -D $dataDir -U postgres -A trust -E UTF8 --no-locale
& "$pgBin\pg_ctl.exe" -D $dataDir -l "$dataDir\postgres-54330.log" -w start -o "-p 54330 -h 127.0.0.1"

# 2. Xác nhận SHOW data_directory đúng là thư mục local vừa tạo (bắt buộc!)
& "$pgBin\psql.exe" -h 127.0.0.1 -p 54330 -U postgres -d postgres -v ON_ERROR_STOP=1 -c "SHOW data_directory;"

# 3. Tạo database pose_crm_local và restore gói backup V2 (schema V1)
& "$pgBin\psql.exe" -h 127.0.0.1 -p 54330 -U postgres -d postgres -v ON_ERROR_STOP=1 `
  -c "DROP DATABASE IF EXISTS pose_crm_local;" `
  -c "CREATE DATABASE pose_crm_local;"
& "$pgBin\pg_restore.exe" -h 127.0.0.1 -p 54330 -U postgres -d pose_crm_local `
  --no-owner --no-privileges --exit-on-error "$pkgRoot\release\POSE_CRM_DB_v2_FINAL.backup"

# 4. Kiểm tra dữ liệu nền sau khi restore (verify_v2.sql cần chạy tại thư mục gốc của gói dữ liệu)
Push-Location $pkgRoot
& "$pgBin\psql.exe" -h 127.0.0.1 -p 54330 -U postgres -d pose_crm_local -v ON_ERROR_STOP=1 -f "$pkgRoot\verify_v2.sql"
Pop-Location
& "$pgBin\psql.exe" -h 127.0.0.1 -p 54330 -U postgres -d pose_crm_local -v ON_ERROR_STOP=1 -f "D:\POSE\database\verify-foundation-v2.sql"
```

#### Bước 2.3: Baseline Flyway version 1 ở lần chạy Backend đầu tiên

Gói backup của Nhi đã có sẵn schema và dữ liệu `V1` nhưng chưa có bảng `flyway_schema_history`. Do đó, **ở lần chạy Backend đầu tiên ngay sau khi restore**, cần bật `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` và `SPRING_FLYWAY_BASELINE_VERSION=1` để Flyway đánh dấu baseline `V1` rồi tự động áp tiếp các migration `V2` đến `V11`:

```powershell
cd D:\POSE\backend
$jwtBytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($jwtBytes)

$env:POSE_DATABASE_URL = "jdbc:postgresql://127.0.0.1:54330/pose_crm_local"
$env:POSE_DATABASE_USERNAME = "postgres"
$env:POSE_DATABASE_PASSWORD = ""
$env:POSE_JWT_SECRET_BASE64 = [Convert]::ToBase64String($jwtBytes)

# Chỉ bật ở lần chạy đầu tiên sau khi restore gói V2:
$env:SPRING_FLYWAY_BASELINE_ON_MIGRATE = "true"
$env:SPRING_FLYWAY_BASELINE_VERSION = "1"

Set-Location D:\POSE
mvn -pl backend -am package -DskipTests
java -jar .\backend\target\pose-backend-0.1.0-SNAPSHOT.jar
```

Từ các lần khởi động Backend tiếp theo trên cùng database `pose_crm_local`, **bỏ 2 biến baseline**:
```powershell
Remove-Item Env:SPRING_FLYWAY_BASELINE_ON_MIGRATE -ErrorAction SilentlyContinue
Remove-Item Env:SPRING_FLYWAY_BASELINE_VERSION -ErrorAction SilentlyContinue
```

#### Bước 2.4: Lệnh kiểm tra schema và lịch sử Flyway sau khi migrate

Mở một cửa sổ PowerShell khác và chạy lệnh sau để xác nhận Flyway đã áp đủ `V1` (BASELINE) và `V2…V11` (`success = t`), đủ 6 tổ chức / 36 người dùng và 6 mã đăng nhập:

```powershell
$psql = "C:\Program Files\PostgreSQL\18\bin\psql.exe"
& $psql -h 127.0.0.1 -p 54330 -U postgres -d pose_crm_local -v ON_ERROR_STOP=1 `
  -c "SHOW data_directory;" `
  -c "SELECT version, description, type, success FROM public.flyway_schema_history ORDER BY installed_rank;" `
  -c "SELECT (SELECT count(*) FROM public.organizations) AS orgs, (SELECT count(*) FROM public.users) AS users;" `
  -c "SELECT code, name, status FROM public.organizations ORDER BY code;"
```

Kết quả mong đợi:
- `flyway_schema_history` có 11 dòng từ version `1` (`<< Flyway Baseline >>`) đến version `11` (`interaction task audit core`), tất cả có `success = t`.
- `orgs = 6`, `users = 36`.
- 6 mã đăng nhập (`code`): `cli`, `edu`, `log`, `rea`, `ret`, `tech` đều ở trạng thái `ACTIVE`.

---

## 4. Thứ tự khởi động Backend và Frontend Dev

Luôn khởi động theo thứ tự: **Database -> Backend (`8080`) -> Frontend Dev (`3000`)**.

| Thành phần | Cổng mặc định | URL kiểm tra | Biến môi trường bắt buộc |
|---|---|---|---|
| **PostgreSQL local** *(Cách 2)* | `54330` | `127.0.0.1:54330` | Không cần nếu dùng `-A trust` trên loopback `127.0.0.1`. |
| **Backend (Spring Boot)** | `8080` | `http://127.0.0.1:8080/actuator/health` | `POSE_DATABASE_URL`, `POSE_DATABASE_USERNAME`, `POSE_DATABASE_PASSWORD`, `POSE_JWT_SECRET_BASE64` |
| **Frontend Dev (Next.js)** | `3000` | `http://localhost:3000/dang-nhap` | `BACKEND_URL=http://127.0.0.1:8080` |

> **Cảnh báo cấu hình mặc định của Backend:** Trong `backend/src/main/resources/application.yml`, giá trị mặc định khi thiếu biến môi trường `POSE_DATABASE_URL` là `jdbc:postgresql://localhost:5432/pose_crm`. Vì vậy khi chạy Cách 2 ở cổng `54330` (hoặc Cách 1 trên Supabase), **bắt buộc** phải đặt biến `POSE_DATABASE_URL` trong cửa sổ terminal chạy Backend.

### Cửa sổ Terminal 1 — Chạy Backend (cổng `8080`)

Sau khi đã đặt các biến `POSE_*` theo Cách 1 hoặc Cách 2 ở Mục 3:
```powershell
cd D:\POSE
mvn -pl backend -am package -DskipTests
java -jar .\backend\target\pose-backend-0.1.0-SNAPSHOT.jar
```
Chờ đến khi log hiện `Started BackendApplication` và kiểm tra:
```powershell
Invoke-WebRequest -Uri "http://127.0.0.1:8080/actuator/health" -UseBasicParsing
```
(Trả về HTTP `200` với `{"status":"UP"}`).

### Cửa sổ Terminal 2 — Chạy Frontend Dev (cổng `3000`)

```powershell
cd D:\POSE\frontend
$env:BACKEND_URL = "http://127.0.0.1:8080"
npm run dev
```
Mở trình duyệt tại địa chỉ: `http://localhost:3000/dang-nhap`.

---

## 5. Thông tin đăng nhập theo tổ chức và vai trò

Màn hình đăng nhập (`/dang-nhap`) yêu cầu 3 thông tin: **Mã tổ chức** (không phân biệt chữ hoa/thường), **Email**, và **Mật khẩu**.

### Bảng 6 tổ chức và tài khoản mẫu theo vai trò

| Mã tổ chức (`organizationCode`) | Tên tổ chức | Admin (`ADMIN`) | Manager (`MANAGER`) | Sales Rep (`SALES_REP`) | Customer Care (`CUSTOMER_CARE`) | Data Staff (`DATA_STAFF`) |
|---|---|---|---|---|---|---|
| `EDU` | **POSE Academy** | `edu.admin@example.com` | `edu.mgr@example.com` | `edu.s01@example.com`<br>`edu.s02@example.com` | `edu.care@example.com` | `edu.data@example.com` |
| `REA` | **POSE Realty** | `rea.admin@example.com` | `rea.mgr@example.com` | `rea.s01@example.com`<br>`rea.s02@example.com` | `rea.care@example.com` | `rea.data@example.com` |
| `TECH` | **POSE Tech** | `tech.admin@example.com` | `tech.mgr@example.com` | `tech.s01@example.com`<br>`tech.s02@example.com` | `tech.care@example.com` | `tech.data@example.com` |
| `RET` | **POSE Retail** | `ret.admin@example.com` | `ret.mgr@example.com` | `ret.s01@example.com`<br>`ret.s02@example.com` | `ret.care@example.com` | `ret.data@example.com` |
| `LOG` | **POSE Logistics** | `log.admin@example.com` | `log.mgr@example.com` | `log.s01@example.com`<br>`log.s02@example.com` | `log.care@example.com` | `log.data@example.com` |
| `CLI` | **POSE Clinic** | `cli.admin@example.com` *(INACTIVE — dùng để test từ chối đăng nhập)* | `cli.mgr@example.com` | `cli.s01@example.com`<br>`cli.s02@example.com` | `cli.care@example.com` | `cli.data@example.com` |

> **Về mật khẩu đăng nhập:**
> - **Mật khẩu demo chuẩn:** Nhận trực tiếp từ người giữ credential của nhóm; **tuyệt đối không** lưu mật khẩu vào tài liệu, code, test hay commit message.
> - **Khi tự kiểm thử trên bản sao database local riêng (Cách 2):** Nếu chưa có mật khẩu demo, bạn có thể tự sinh một BCrypt hash (cost 12) từ mật khẩu thử ngẫu nhiên của riêng bạn và cập nhật vào bảng `public.credentials` **chỉ trên database local `pose_crm_local`** (tuyệt đối không chạy trên Supabase).

---

## 6. Chạy các bộ kiểm thử (Testing)

### 6.1. Kiểm thử Frontend (Vitest + Typecheck + Lint + Build)

Không cần bật Backend hay PostgreSQL:
```powershell
cd D:\POSE\frontend
npx vitest run
npm run typecheck
npm run lint
npm run build
```

### 6.2. Kiểm thử Backend với H2 in-memory

Chạy nhanh toàn bộ unit test và integration test trên H2:
```powershell
cd D:\POSE
mvn -pl backend -am verify
```

### 6.3. Kiểm thử Backend trên PostgreSQL 18 cô lập (`127.0.0.1:54329`)

Script `backend/scripts/test-postgres-v2.ps1` tự động tạo/khởi động cluster test cô lập tại `backend/target/auth-postgres-test` (cổng `54329`), restore gói V2, chạy Flyway `V2…V11`, chạy toàn bộ `mvn verify` trên PostgreSQL thật rồi tự tắt cluster.

Không chạy `mvn clean` ở gốc hay trong module: lệnh xóa `backend/target/auth-postgres-test`. JAR được tạo tại `backend/target/pose-backend-0.1.0-SNAPSHOT.jar`.

Lệnh chạy yêu cầu các tham số bắt buộc/thực dùng gồm `-DataPackagePath` (thư mục gói V2 đã giải nén), `-JdkDirectory` và `-MavenPath` (ví dụ đúng như lệnh trong `backend/README.md`, mục Kiểm thử):

```powershell
cd D:\POSE
powershell.exe -NoProfile -File .\backend\scripts\test-postgres-v2.ps1 `
  -DataPackagePath 'D:\POSE-data\POSE_CRM_DATA_V2_FINAL' `
  -JdkDirectory 'C:\Program Files\Java\jdk-25.0.2' `
  -MavenPath 'C:\Users\Dell\.maven\maven-3.9.15\bin\mvn.cmd'
```

> **Lưu ý:**
> - Nếu chạy trực tiếp từ thư mục `D:\POSE\backend`, đường dẫn script là `.\scripts\test-postgres-v2.ps1`.
> - Nếu đã đặt biến môi trường `POSE_DEMO_PASSWORD` cho phiên làm việc hiện tại, bạn có thể thêm cờ `-RequireAcceptance` để chạy cả bộ kiểm thử acceptance đa vai trò với dữ liệu thật như mô tả trong `backend/README.md`.

### 6.4. Kiểm thử End-to-End (Playwright Full-stack)

> **Điều kiện tiên quyết:** Bắt buộc phải chạy mục 6.3 (`test-postgres-v2.ps1`) ít nhất một lần trước đó để khởi tạo cluster cô lập tại `backend/target/auth-postgres-test` và tạo sẵn database review `pose_v2_review_*`. Nếu thiếu, runner sẽ báo lỗi `"No eligible pose_v2_review database in the dedicated cluster."` (xem Mục 7.4).

Script `scripts/e2e-env.ps1` tự động thiết lập và dọn dẹp môi trường kiểm thử E2E:
1. **Kiểm tra trạng thái cổng:** Kiểm tra các cổng `8080`, `8081`, `3010`, `3011`, `54329` phải đang trống trước khi khởi chạy (nếu có cổng nào đang bị chiếm, script từ chối chạy để tránh ảnh hưởng dịch vụ đang có).
2. **Database cô lập:** Khởi động cluster test tại `backend/target/auth-postgres-test` (`127.0.0.1:54329`), tìm database `pose_v2_review_*` mới nhất trong cluster, sao chép thành một database tạm riêng biệt tên `pose_e2e_<thời gian>` (`pose_e2e_yyyyMMddHHmmssfff_<random>`), sinh mật khẩu và JWT secret ngẫu nhiên rồi cập nhật hash BCrypt(12) cho các tài khoản test trong database tạm đó. Database này được tự động drop (`dropdb --force`) ngay sau khi test kết thúc.
3. **Backend Spring Boot:** Khởi động backend production trên cổng `8081` (kết nối tới database tạm ở `127.0.0.1:54329`). Cổng `8080` chỉ được kiểm tra là đang trống.
4. **Frontend Next.js production server:** Khởi động máy chủ Next.js production trên cổng `3011` (với `BACKEND_URL=http://127.0.0.1:8081`). Cổng `3010` chỉ được kiểm tra là đang trống (được dự phòng cho các ca kiểm thử hành vi mạng).
5. **Thực thi Playwright:** Chạy toàn bộ các file test trong `frontend/e2e/` (hoặc chạy file chỉ định qua `-TestFile`).

Lệnh thực thi từ thư mục gốc `D:\POSE`:
```powershell
cd D:\POSE

# Lần đầu hoặc sau khi sửa code Backend/Frontend (dùng cờ -Build để đóng gói lại JAR và build Next.js):
powershell.exe -NoProfile -File .\scripts\e2e-env.ps1 -Build

# Chạy lại nhanh khi artifact build đã mới hơn mã nguồn:
powershell.exe -NoProfile -File .\scripts\e2e-env.ps1

# Chạy riêng một file test cụ thể (ví dụ bộ smoke test 6 tổ chức):
powershell.exe -NoProfile -File .\scripts\e2e-env.ps1 -TestFile e2e/tenants-smoke.spec.ts
```

**Dọn dẹp sau khi chạy:** Kết thúc lượt chạy (dù test đạt hay thất bại), script tự động dọn dẹp sạch sẽ: dừng tiến trình frontend (`3011`) và backend (`8081`), xóa các file session tạm `e2e/.auth/<run-id>`, drop database tạm `pose_e2e_*`, dừng cluster PostgreSQL test `54329` qua `pg_ctl`, xóa thư mục tạm trong `%TEMP%`, và kiểm tra xác nhận dừng sạch toàn bộ 5 cổng: `8080`, `8081`, `3010`, `3011`, `54329`.

---

## 7. Xử lý sự cố thường gặp (Troubleshooting)

### 7.1. Cổng đã bị chiếm (`Port already in use`: `8080`, `3000`, `8081`, `3010`, `3011`, `54329`, `54330`)
- **Triệu chứng:** Backend báo `Web server failed to start. Port 8080 was already in use`, hoặc Next.js tự nhảy sang cổng `3001`, hoặc `e2e-env.ps1` báo lỗi cổng bận.
- **Cách xử lý:** Kiểm tra và dừng tiến trình đang giữ cổng:
  ```powershell
  Get-NetTCPConnection -LocalPort 8080,3000,8081,3010,3011 -State Listen -ErrorAction SilentlyContinue |
    Select-Object LocalPort, OwningProcess
  Stop-Process -Id <PID> -Force
  ```
  Nếu là PostgreSQL local trên `54330` hoặc `54329`, ưu tiên dừng sạch qua `pg_ctl`:
  ```powershell
  powershell.exe -NoProfile -File .\scripts\local-db.ps1 -Action Stop -Port 54330 -DataDir "D:\POSE-data\local-pg-54330"
  ```

### 7.2. Lỗi `EMAXCONNSESSION` / hết kết nối trên Supabase
- **Triệu chứng:** Log Backend báo `FATAL: MaxClientsInSessionMode: max clients reached - in Session mode max clients are limited to pool_size`.
- **Cách xử lý:** Đảm bảo đặt `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=5` trước khi chạy Backend, và tắt các tiến trình Backend/DB client cũ đang mở kết nối thừa tới Supabase.

### 7.3. Lỗi thiếu trình duyệt Chromium khi chạy Playwright E2E
- **Triệu chứng:** Playwright báo `browserType.launch: Executable doesn't exist at ...\ms-playwright\chromium_headless_shell-...`.
- **Cách xử lý:** Chạy lệnh cài đặt browser trong thư mục `frontend`:
  ```powershell
  cd D:\POSE\frontend
  npx playwright install chromium
  ```

### 7.4. `scripts/e2e-env.ps1` báo lỗi `No eligible pose_v2_review database` hoặc thiếu cluster
- **Triệu chứng:** Khi chạy `e2e-env.ps1`, script dừng ngay và thông báo:
  - `No eligible pose_v2_review database in the dedicated cluster.` hoặc
  - `Dedicated PostgreSQL cluster is missing; initialize it with the approved test runner first.`
- **Nguyên nhân:** Cluster test tại `backend/target/auth-postgres-test` chưa được tạo hoặc chưa có database nền `pose_v2_review_*` do chưa chạy `test-postgres-v2.ps1`.
- **Cách xử lý:** Chạy lệnh ở Mục 6.3 (`powershell.exe -NoProfile -File .\backend\scripts\test-postgres-v2.ps1 -DataPackagePath ...`) ít nhất một lần để khởi tạo cluster `54329` và tạo sẵn database review trước khi chạy E2E.

### 7.5. `scripts/e2e-env.ps1` từ chối chạy vì bản build cũ (Stale build artifact)
- **Triệu chứng:** Script dừng ngay đầu với thông báo `Backend source is newer than the JAR. Rebuild with Maven package -DskipTests or run this script with -Build.` hoặc `Frontend source or configuration is newer than the build. Run npm run build or run this script with -Build.`
- **Cách xử lý:** Đây là cơ chế bảo vệ để tránh chạy E2E trên code cũ. Chạy lại kèm cờ `-Build`:
  ```powershell
  powershell.exe -NoProfile -File .\scripts\e2e-env.ps1 -Build
  ```

### 7.6. Frontend báo `Không thể kết nối tới máy chủ` (`503 NETWORK_ERROR`)
- **Triệu chứng:** Trang đăng nhập hoặc các màn hình trong `/app/*` báo lỗi mạng dù Frontend vẫn mở được.
- **Cách xử lý:**
  1. Kiểm tra Backend đã chạy và phản hồi `200` tại `http://127.0.0.1:8080/actuator/health` chưa.
  2. Kiểm tra cửa sổ chạy Frontend có đặt đúng `$env:BACKEND_URL = "http://127.0.0.1:8080"` không (ưu tiên `127.0.0.1` thay vì `localhost` để tránh độ trễ/lỗi phân giải IPv6 `::1` trên Windows).

### 7.7. Phiên đăng nhập hết hạn liên tục hoặc bị đẩy về `/dang-nhap?reason=expired`
- **Triệu chứng:** Vừa đăng nhập xong hoặc khi khởi động lại Backend thì trình duyệt bị chuyển về `/dang-nhap?reason=expired`.
- **Cách xử lý:** Mỗi lần bạn sinh lại `POSE_JWT_SECRET_BASE64` mới khi khởi động lại Backend, các access token cũ sẽ không còn hợp lệ (hoặc refresh token đã xoay vòng/hết hạn). Chỉ cần đăng nhập lại trên giao diện, hoặc giữ nguyên một giá trị `POSE_JWT_SECRET_BASE64` trong suốt phiên làm việc local.
# Biến môi trường CRM.AI

AI mặc định tắt; provider mặc định `mock`. Khóa do Tài giữ và chỉ đưa vào môi trường process; chưa bật AI trên Supabase dùng chung khi Tài chưa cho phép.

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `POSE_AI_ENABLED` | `false` | Bật gateway AI |
| `POSE_AI_PROVIDER` | `mock` | `mock` hoặc `claude` |
| `POSE_AI_API_KEY` | rỗng | Khóa Claude; không ghi Git, log hay chat |
| `POSE_AI_MONTHLY_BUDGET_USD` | `5` | Hạn mức mềm/tháng/tổ chức |
| `POSE_AI_TIMEOUT_SECONDS`, `POSE_AI_MAX_RETRIES` | `30`, `1` | Thời gian chờ mỗi request và số lần thử lại |
| `POSE_AI_MAX_TOKENS`, `POSE_AI_MAX_CONTEXT_CHARS` | `16000`, `12000` | Giới hạn output token và ký tự context sau khi che |
| `POSE_AI_MODEL_SIGNAL_EXTRACT` | `claude-haiku-5-5` | Model trích tín hiệu |
| `POSE_AI_MODEL_C360_SUMMARY`, `POSE_AI_MODEL_HANDOVER_BRIEF`, `POSE_AI_MODEL_REPLY_DRAFT` | `claude-sonnet-5-5` | Model cho ba use case còn lại |
| `POSE_AI_EFFORT_SIGNAL_EXTRACT` | `low` | Effort trích tín hiệu |
| `POSE_AI_EFFORT_C360_SUMMARY`, `POSE_AI_EFFORT_HANDOVER_BRIEF`, `POSE_AI_EFFORT_REPLY_DRAFT` | `medium` | Effort các use case còn lại |
| `POSE_AI_HAIKU_INPUT_USD_PER_MILLION`, `POSE_AI_HAIKU_OUTPUT_USD_PER_MILLION` | `0.10`, `0.50` | Giá ước tính USD/triệu token |
| `POSE_AI_SONNET_INPUT_USD_PER_MILLION`, `POSE_AI_SONNET_OUTPUT_USD_PER_MILLION` | `2.00`, `10.00` | Giá ước tính USD/triệu token |

Nhập khóa trong cửa sổ PowerShell của người giữ khóa, không ghi ra màn hình:

```powershell
$poseAiSecure = Read-Host 'Claude API key' -AsSecureString
$poseAiBstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($poseAiSecure)
try { $env:POSE_AI_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($poseAiBstr) }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($poseAiBstr) }
```

Đóng process hoặc xóa biến môi trường sau khi dùng. Khóa thiếu khi chọn `claude` không ngăn app khởi động: gateway trả `DISABLED`.

