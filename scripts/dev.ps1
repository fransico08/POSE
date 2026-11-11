param(
    [ValidateSet("Up", "Down", "Status")]
    [string]$Action = "Up",
    [switch]$Rebuild,
    [switch]$NoBrowser,
    [ValidateSet("mock", "claude")]
    [string]$AiProvider = "mock",
    [string]$JdkDirectory = "C:\Program Files\Java\jdk-25.0.2",
    [string]$MavenPath = "C:\Users\Dell\.maven\maven-3.9.15\bin\mvn.cmd",
    [string]$PgBin = "C:\Program Files\PostgreSQL\18\bin",
    [string]$DataDir = "D:\POSE-data\local-pg-54330"
)
# Dev một lệnh: PostgreSQL local (54330) + Backend (8080) + Frontend (3000).
# Dùng: powershell -File .\scripts\dev.ps1            (bật tất cả và mở trình duyệt)
#       powershell -File .\scripts\dev.ps1 -Action Down   (tắt tất cả)
#       -Rebuild để build lại backend; mặc định chỉ build khi chưa có JAR hoặc mã nguồn mới hơn JAR.
# Không dùng cổng 5432/54329, không in mật khẩu hay khóa. AI mặc định mock; -AiProvider claude để thử Claude thật (hỏi khóa kín).
$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$frontend = Join-Path $repo "frontend"
$jar = Join-Path $repo "backend\target\pose-backend-0.1.0-SNAPSHOT.jar"
$logs = Join-Path $env:TEMP "pose-dev"
$pidFile = Join-Path $logs "pids.txt"
$port = 54330; $db = "pose_crm_local"
New-Item -ItemType Directory -Force $logs | Out-Null

function Test-Port($p) { [bool](Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue) }
function Stop-Port($p) {
    Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue | ForEach-Object {
        Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue
    }
}
function Wait-Url($url, $seconds) {
    $end = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $end) {
        try { if ((Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 3).StatusCode -lt 500) { return $true } }
        catch { $r = $_.Exception.Response; if ($r -and [int]$r.StatusCode -lt 500) { return $true } }
        Start-Sleep -Seconds 2
    }
    return $false
}

if ($Action -eq "Status") {
    "PostgreSQL $port : $(if (Test-Port $port) { 'đang chạy' } else { 'tắt' })"
    "Backend    8080 : $(if (Test-Port 8080) { 'đang chạy' } else { 'tắt' })"
    "Frontend   3000 : $(if (Test-Port 3000) { 'đang chạy' } else { 'tắt' })"
    return
}

if ($Action -eq "Down") {
    Stop-Port 3000; Stop-Port 8080
    if (Test-Path (Join-Path $DataDir "postmaster.pid")) {
        & powershell.exe -NoProfile -File (Join-Path $PSScriptRoot "local-db.ps1") -Action Stop -Port $port -DataDir $DataDir
    }
    "Đã tắt frontend, backend và PostgreSQL local."
    return
}

# ---- Up ----
if (-not (Test-Path (Join-Path $DataDir "PG_VERSION"))) {
    throw "Chưa có database local tại $DataDir. Chạy trước: powershell -File .\scripts\local-db.ps1 -Action RestoreAndVerify -Port $port -Database $db -DataDir `"$DataDir`" -PackageRoot D:\POSE-data\POSE_CRM_DATA_V2_FINAL"
}
if (-not (Test-Port $port)) {
    & powershell.exe -NoProfile -File (Join-Path $PSScriptRoot "local-db.ps1") -Action Start -Port $port -DataDir $DataDir
}
$ready = Join-Path $PgBin "pg_isready.exe"
$until = (Get-Date).AddSeconds(120)
while ((Get-Date) -lt $until) { & $ready -h 127.0.0.1 -p $port -q; if ($LASTEXITCODE -eq 0) { break }; Start-Sleep -Seconds 2 }
if ($LASTEXITCODE -ne 0) { throw "PostgreSQL local chưa nhận kết nối sau 120 giây." }
foreach ($p in 8080, 3000) { if (Test-Port $p) { throw "Cổng $p đang bị chiếm. Chạy -Action Down hoặc kiểm tra tiến trình khác." } }

# Build backend khi cần
$srcNewest = Get-ChildItem (Join-Path $repo "backend\src"), (Join-Path $repo "backend\pom.xml") -Recurse -File -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
if ($Rebuild -or -not (Test-Path $jar) -or $srcNewest.LastWriteTimeUtc -gt (Get-Item $jar).LastWriteTimeUtc) {
    "Đang build backend (lần đầu hoặc mã nguồn đã đổi)..."
    $env:JAVA_HOME = $JdkDirectory
    Push-Location $repo
    try { & $MavenPath -q -pl backend -am package -DskipTests; if ($LASTEXITCODE -ne 0) { throw "Build backend lỗi." } } finally { Pop-Location }
}

# Flyway baseline chỉ cần khi database vừa restore (chưa có flyway_schema_history)
$psql = Join-Path $PgBin "psql.exe"
$hasHistory = & $psql -h 127.0.0.1 -p $port -U postgres -d $db -tAc "SELECT to_regclass('public.flyway_schema_history') IS NOT NULL"
$bytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$env:JAVA_HOME = $JdkDirectory
$env:POSE_DATABASE_URL = "jdbc:postgresql://127.0.0.1:$port/$db"
$env:POSE_DATABASE_USERNAME = "postgres"; $env:POSE_DATABASE_PASSWORD = ""
$env:POSE_JWT_SECRET_BASE64 = [Convert]::ToBase64String($bytes)
$env:POSE_AI_ENABLED = "true"; $env:POSE_AI_PROVIDER = $AiProvider
if ($AiProvider -eq "claude" -and [string]::IsNullOrEmpty($env:POSE_AI_API_KEY)) {
    # Khóa chỉ nằm trong biến môi trường của tiến trình này và backend con; không ghi file, log hay màn hình.
    $secure = Read-Host "Nhập Claude API key (không hiện ra màn hình)" -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { $env:POSE_AI_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr); $secure.Dispose() }
    if ([string]::IsNullOrEmpty($env:POSE_AI_API_KEY)) { throw "Chưa nhập khóa; chạy lại hoặc dùng -AiProvider mock." }
}
if ("$hasHistory".Trim() -ne "t") { $env:SPRING_FLYWAY_BASELINE_ON_MIGRATE = "true"; $env:SPRING_FLYWAY_BASELINE_VERSION = "1" }
else { Remove-Item Env:SPRING_FLYWAY_BASELINE_ON_MIGRATE, Env:SPRING_FLYWAY_BASELINE_VERSION -ErrorAction SilentlyContinue }

$be = Start-Process -FilePath (Join-Path $JdkDirectory "bin\java.exe") -ArgumentList @("-jar", $jar) -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $logs "backend.out.log") -RedirectStandardError (Join-Path $logs "backend.err.log")
"Đang chờ backend (có thể mất 30–60 giây)..."
if (-not (Wait-Url "http://127.0.0.1:8080/actuator/health" 150)) { throw "Backend chưa sẵn sàng. Xem log: $logs\backend.out.log" }

$env:BACKEND_URL = "http://127.0.0.1:8080"
$fe = Start-Process -FilePath "npm.cmd" -ArgumentList @("run", "dev") -WorkingDirectory $frontend -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $logs "frontend.out.log") -RedirectStandardError (Join-Path $logs "frontend.err.log")
"Đang chờ frontend..."
if (-not (Wait-Url "http://localhost:3000/dang-nhap" 120)) { throw "Frontend chưa sẵn sàng. Xem log: $logs\frontend.out.log" }

"$($be.Id)`n$($fe.Id)" | Set-Content $pidFile
"Sẵn sàng: http://localhost:3000/dang-nhap  (tắt bằng: powershell -File .\scripts\dev.ps1 -Action Down)"
if (-not $NoBrowser) { Start-Process "http://localhost:3000/dang-nhap" }
