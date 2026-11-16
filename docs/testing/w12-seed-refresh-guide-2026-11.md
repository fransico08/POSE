# W12 — Làm mới seed demo đa ngành

Ngày cập nhật: 16/11/2026
Phạm vi: nạp lại canonical CSV seed W12 trên PostgreSQL local dùng riêng.

## Chọn đúng nguồn dữ liệu

- **Canonical W12:** `database/data/seed/multisector_v2/`, được nạp bằng `database/scripts/seed_v2.psql` và xác minh bằng `database/scripts/verify_seed_v2.psql`.
- **Backup V2 cũ:** `scripts/local-db.ps1 -Action RestoreAndVerify` khôi phục backup `POSE_CRM_DB_v2_FINAL.backup` vào `pose_crm_local`. Đây không phải lệnh nạp lại canonical CSV W12.
- **Chạy ứng dụng:** `scripts/dev.ps1 -Database <database>` chỉ kết nối vào database đã có; không tự tạo database hoặc nạp seed.

Không chạy seed trên Supabase, database dùng chung hoặc database có dữ liệu cần giữ. Quy trình dưới đây dùng một database local riêng. `seed_v2.psql` chỉ nhận các tên database được ghi trong guard của script và từ chối nạp nếu bảng `organizations` đã có dữ liệu. Không sửa guard để dùng tên database khác.

Hiện loader cho phép `pose_w12_v12_final_test`, `pose_w12_v12_clean_test` và `pose_w12_v12_canonical_test`. Hướng dẫn dùng tên cuối làm ví dụ. Cần PostgreSQL 18 client/server, JDK tương thích target Java 21, Maven và Python; xem [Backend README](../../backend/README.md) để thiết lập Java/Maven.

## Chuẩn bị database local mới

Mở PowerShell tại thư mục gốc repo. Ví dụ dưới đây dùng PostgreSQL local cổng `54330` và database canonical được guard cho phép. Không dùng cổng `5432` hoặc cluster E2E `54329` cho demo local.

```powershell
$repoRoot = (Resolve-Path '.').Path
$pgBin = 'C:\Program Files\PostgreSQL\18\bin'
$port = 54330
$database = 'pose_w12_v12_canonical_test'
$dataDir = 'D:\POSE-data\local-pg-54330'

powershell.exe -NoProfile -File .\scripts\local-db.ps1 `
  -Action Start `
  -HostAddress 127.0.0.1 `
  -Port $port `
  -DataDir $dataDir
```

`Start` chỉ khởi tạo/khởi động cluster local; không khôi phục backup và không xóa database. Trước khi tạo database, xác nhận cluster đang nghe đúng thư mục dữ liệu:

```powershell
$psql = Join-Path $pgBin 'psql.exe'
$actualDataDir = (& $psql -h 127.0.0.1 -p $port -U postgres -d postgres -Atqc 'SHOW data_directory;').Trim()
if ($LASTEXITCODE -ne 0 -or
    [IO.Path]::GetFullPath($actualDataDir).TrimEnd('\') -ine [IO.Path]::GetFullPath($dataDir).TrimEnd('\')) {
    throw 'STOP: PostgreSQL không trỏ tới data directory local dự kiến.'
}
$actualDataDir
```

Nếu cluster hoặc database mục tiêu đã tồn tại mà bạn không chắc đó là bản dùng riêng, dừng tại đây. Không xóa hoặc ghi đè dữ liệu để “làm mới”. Hãy dùng một cluster local dùng một lần khác, với cổng local còn trống và data directory riêng; giữ nguyên tên database thuộc allowlist của `seed_v2.psql`.

Kiểm tra database mục tiêu chưa tồn tại rồi mới tạo:

```powershell
$exists = (& $psql -h 127.0.0.1 -p $port -U postgres -d postgres -Atqc `
  "SELECT 1 FROM pg_database WHERE datname = '$database';").Trim()
if ($LASTEXITCODE -ne 0) { throw 'Không kiểm tra được database mục tiêu.' }
if ($exists -eq '1') { throw 'Database mục tiêu đã tồn tại; không tự xóa hoặc nạp chồng seed.' }

& (Join-Path $pgBin 'createdb.exe') -h 127.0.0.1 -p $port -U postgres $database
if ($LASTEXITCODE -ne 0) { throw 'Không tạo được database local mới.' }
```

## Áp migration trước khi nạp seed

Database mới phải được tạo schema bằng Flyway của backend hiện tại trước khi chạy loader. Theo `backend/README.md`, database trống được migrate từ V1 tới migration mới nhất trong checkout (hiện V15). Không bật baseline cho database trống và không nạp seed nếu backend chưa khởi động thành công.

Đặt thông tin kết nối chỉ trong phiên PowerShell này. Mật khẩu local để trống chỉ phù hợp với cluster loopback được tạo theo hướng dẫn local; không dùng cấu hình này cho database khác.

```powershell
$env:POSE_DATABASE_URL = "jdbc:postgresql://127.0.0.1:$port/$database"
$env:POSE_DATABASE_USERNAME = 'postgres'
$env:POSE_DATABASE_PASSWORD = ''
$jwtBytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($jwtBytes)
$env:POSE_JWT_SECRET_BASE64 = [Convert]::ToBase64String($jwtBytes)

Set-Location $repoRoot
mvn -pl backend -am package -DskipTests
if ($LASTEXITCODE -ne 0) { throw 'Backend build thất bại; chưa chạy seed.' }

java -jar .\backend\target\pose-backend-0.1.0-SNAPSHOT.jar
```

Chờ backend báo đã khởi động thành công, rồi nhấn `Ctrl+C`. Kiểm tra Flyway và xác nhận database vẫn chưa có tổ chức trước khi nạp:

```powershell
& $psql -h 127.0.0.1 -p $port -U postgres -d $database -v ON_ERROR_STOP=1 `
  -c "SELECT version, description, success FROM public.flyway_schema_history ORDER BY installed_rank;" `
  -c 'SELECT count(*) AS organizations_before_seed FROM public.organizations;'
```

Các migration hiện có phải đều `success = t` tới V15 và `organizations_before_seed` phải bằng `0`. Nếu không đúng, dừng lại và xử lý migration/cluster trước; không thử nạp seed để xem có chạy được không.

Sau khi dừng backend, có thể xóa các biến khỏi terminal khi không cần chạy ứng dụng tiếp:

```powershell
Remove-Item Env:POSE_DATABASE_URL, Env:POSE_DATABASE_USERNAME, Env:POSE_DATABASE_PASSWORD, Env:POSE_JWT_SECRET_BASE64 -ErrorAction SilentlyContinue
```

## Nạp canonical seed và kiểm tra

`\copy` trong loader dùng đường dẫn tương đối từ thư mục hiện tại. Đứng tại gốc repo, rồi chạy loader và verifier riêng rẽ:

```powershell
Set-Location $repoRoot
& $psql -h 127.0.0.1 -p $port -U postgres -d $database -v ON_ERROR_STOP=1 `
  -f .\database\scripts\seed_v2.psql
if ($LASTEXITCODE -ne 0) { throw 'Canonical seed thất bại; xem lỗi trước khi tiếp tục.' }

& $psql -h 127.0.0.1 -p $port -U postgres -d $database -v ON_ERROR_STOP=1 `
  -f .\database\scripts\verify_seed_v2.psql
if ($LASTEXITCODE -ne 0) { throw 'Canonical seed verifier thất bại.' }
```

Chỉ xem lần nạp là đạt khi verifier kết thúc với `TASK 5 PASS - synchronized migration/seed verification passed.` và các KPI theo từng tổ chức khớp `expected_kpis.csv`. Báo cáo Data Quality ghi nhận tổng canonical gồm 6 tổ chức, 10.000 Customer, 15.000 Lead, 8.347 Opportunity, 45.000 Interaction và 18.000 Task; dùng verifier để đối chiếu đầy đủ, không chỉ dựa vào các tổng này.

Các kiểm tra offline, chỉ đọc repo, có thể chạy từ gốc repo:

```powershell
python database/scripts/validate_seed_files.py
python database/scripts/validate_deep_rules.py
```

Đây là kiểm tra CSV/logic tĩnh; chúng không thay thế việc chạy verifier trên database local. `validate_seed_files.py` có thể in các cảnh báo legacy/post-migration; cần đối chiếu với `w12-data-quality-report.md`, không bỏ qua cảnh báo mới nếu chưa phân tích. Không dùng `check_contract_static.py` làm cổng W12: script đó hiện chỉ đọc migration V1 nên báo sai các cột do migration sau V1 thêm và các bảng staging trong loader. Không ghi password vào evidence. Nếu cần xác minh tài khoản demo, chỉ dùng `POSE_DEMO_PASSWORD` theo `docs/testing/w12-local-demo-evidence-2026-11-12.md`, và không đưa giá trị biến vào log hoặc tài liệu.

Khi hoàn tất, có thể chạy ứng dụng trên database này bằng `scripts/dev.ps1 -Database pose_w12_v12_canonical_test -NoBrowser` nếu database nằm trên cluster/data directory mặc định của script ở cổng `54330`. Nếu chọn cổng khác, đặt `POSE_DATABASE_URL` của backend trỏ đúng cổng và chạy FE/BE theo hướng dẫn local; `dev.ps1` hiện cố định cổng `54330`.

## Làm mới lần tiếp theo và xử lý lỗi

- Loader không idempotent. Không chạy lại trên database đã seed; nó sẽ từ chối database có `organizations`.
- Repo không có reset script cho canonical seed. Không dùng `reset_demo_v2.psql` hay lệnh `DROP DATABASE` chung chung. Muốn làm mới, tạo database/cluster local dùng một lần mới, xác nhận lại `SHOW data_directory`, rồi làm lại thứ tự migrate → seed → verify.
- Lỗi “expected an isolated canonical V12 test database”: dùng đúng một database trong allowlist hiện có của `seed_v2.psql`; không sửa guard.
- Không tìm thấy CSV: quay về repo root trước khi chạy `psql -f`.
- Sai version/schema hoặc verifier không in `TASK 5 PASS`: coi là thất bại, không dùng database đó để demo; đối chiếu migration hiện tại, báo cáo Data Quality và log backend.

## Phạm vi evidence

`docs/testing/w12-data-quality-report.md` ghi nhận kết quả Data Quality canonical. `docs/testing/w12-local-demo-evidence-2026-11-12.md` ghi nhận verifier PASS trên database local có sẵn, nhưng lần ghi nhận đó không chạy lại loader. Vì vậy không dùng evidence ngày 12/11 để khẳng định một lần bootstrap mới đã được chạy trong phiên hiện tại.

Quy trình trong tài liệu này chưa được chạy lại trên database mới trong phiên cập nhật; trước khi dùng nó làm evidence cho một lần refresh mới, cần chạy theo đúng các bước trên một database local cô lập và lưu kết quả verifier, không lưu credentials.
