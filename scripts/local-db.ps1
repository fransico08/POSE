param(
    [ValidateSet("RestoreAndVerify", "Start", "Stop", "Status")]
    [string]$Action = "RestoreAndVerify",
    [string]$HostAddress = "127.0.0.1",
    [int]$Port = 54330,
    [string]$Database = "pose_crm_local",
    [string]$DataDir = "D:\POSE-data\local-pg-54330",
    [string]$PackageRoot = "D:\POSE-data\POSE_CRM_DATA_V2_FINAL",
    [string]$PgBin = "C:\Program Files\PostgreSQL\18\bin"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$e2eClusterDir = [IO.Path]::GetFullPath((Join-Path $repoRoot "backend\target\auth-postgres-test"))

if ($HostAddress -ne "127.0.0.1") {
    throw "Chi cho phep HostAddress=127.0.0.1 cho database local. Gia tri hien tai: $HostAddress"
}
if ($Port -eq 5432) {
    throw "Tu choi cong 5432 de bao ve PostgreSQL server chinh tren may."
}
if ($Port -eq 54329) {
    throw "Tu choi cong 54329 vi day la cluster test co lap danh rieng cho E2E/integration test."
}
if ($Port -lt 1024 -or $Port -gt 65535) {
    throw "Cong PostgreSQL khong hop le: $Port"
}

$resolvedTargetDataDir = [IO.Path]::GetFullPath($DataDir)
if ($resolvedTargetDataDir.TrimEnd('\').Equals($e2eClusterDir.TrimEnd('\'), [StringComparison]::OrdinalIgnoreCase) -or
    $resolvedTargetDataDir.StartsWith($e2eClusterDir.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) {
    throw "Tu choi thao tac tren cluster test E2E: $e2eClusterDir"
}
if ($resolvedTargetDataDir -match '(?i)Program Files\\PostgreSQL') {
    throw "Tu choi thao tac tren thu muc cai dat PostgreSQL he thong: $resolvedTargetDataDir"
}

$initdb = Join-Path $PgBin "initdb.exe"
$pgCtl = Join-Path $PgBin "pg_ctl.exe"
$pgIsReady = Join-Path $PgBin "pg_isready.exe"
$psql = Join-Path $PgBin "psql.exe"
$pgRestore = Join-Path $PgBin "pg_restore.exe"

foreach ($requiredPath in @($initdb, $pgCtl, $pgIsReady, $psql, $pgRestore)) {
    if (-not (Test-Path $requiredPath)) {
        throw "Khong tim thay cong cu PostgreSQL tai: $requiredPath"
    }
}

$env:PGPASSWORD = ""

function Confirm-DataDirectory {
    $expectedPath = (Resolve-Path $resolvedTargetDataDir).Path
    $actualDataDir = (& $psql -h $HostAddress -p $Port -U postgres -d postgres -v ON_ERROR_STOP=1 -Atqc "SHOW data_directory;").Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($actualDataDir)) {
        throw "Khong doc duoc SHOW data_directory tren ${HostAddress}:${Port}."
    }
    $resolvedActual = (Resolve-Path ($actualDataDir -replace '/', '\')).Path
    if ($resolvedActual.TrimEnd('\') -ne $expectedPath.TrimEnd('\')) {
        throw "SHOW data_directory khong khop cluster local ($resolvedActual != $expectedPath). Dung ngay de bao ve du lieu."
    }
    Write-Host "Da xac nhan SHOW data_directory = $resolvedActual"
}

function Start-LocalCluster {
    $parentDir = Split-Path -Parent $resolvedTargetDataDir
    if (-not (Test-Path $parentDir)) {
        New-Item -ItemType Directory -Force -Path $parentDir | Out-Null
    }
    $pgVersionFile = Join-Path $resolvedTargetDataDir "PG_VERSION"
    if (-not (Test-Path $pgVersionFile)) {
        Write-Host "Khoi tao cluster PostgreSQL local tai $resolvedTargetDataDir..."
        & $initdb -D $resolvedTargetDataDir -U postgres -A trust -E UTF8 --no-locale | Out-Host
        if ($LASTEXITCODE -ne 0) {
            throw "initdb that bai voi ma loi $LASTEXITCODE"
        }
    }
    & $pgIsReady -h $HostAddress -p $Port -U postgres -d postgres | Out-Null
    if ($LASTEXITCODE -ne 0) {
        $logFile = Join-Path $resolvedTargetDataDir "postgres-$Port.log"
        Write-Host "Khoi dong PostgreSQL local tren ${HostAddress}:${Port}..."
        & $pgCtl -D $resolvedTargetDataDir -l $logFile -o "-p $Port -h $HostAddress" -w -t 20 start
        if ($LASTEXITCODE -ne 0) {
            throw "pg_ctl start that bai voi ma loi $LASTEXITCODE"
        }
    }
    Confirm-DataDirectory
}

if ($Action -eq "Status") {
    & $pgIsReady -h $HostAddress -p $Port -U postgres -d postgres | Out-Host
    if ($LASTEXITCODE -eq 0 -and (Test-Path $resolvedTargetDataDir)) {
        Confirm-DataDirectory
    }
    exit $LASTEXITCODE
}

if ($Action -eq "Stop") {
    if (-not (Test-Path (Join-Path $resolvedTargetDataDir "PG_VERSION"))) {
        Write-Host "Cluster chua duoc khoi tao tai $resolvedTargetDataDir."
        exit 0
    }
    & $pgIsReady -h $HostAddress -p $Port -U postgres -d postgres | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Confirm-DataDirectory
        & $pgCtl -D $resolvedTargetDataDir -m fast -w stop | Out-Host
    } else {
        Write-Host "PostgreSQL khong chay tren ${HostAddress}:${Port}."
    }
    exit 0
}

if ($Action -eq "Start") {
    Start-LocalCluster
    exit 0
}

$backupFile = Join-Path $PackageRoot "release\POSE_CRM_DB_v2_FINAL.backup"
$verifySql = Join-Path $PackageRoot "verify_v2.sql"
$foundationVerifySql = Join-Path $repoRoot "database\verify-foundation-v2.sql"

foreach ($requiredDataFile in @($backupFile, $verifySql, $foundationVerifySql)) {
    if (-not (Test-Path $requiredDataFile)) {
        throw "Khong tim thay file du lieu/kiem tra bat buoc: $requiredDataFile"
    }
}

Start-LocalCluster

Confirm-DataDirectory
Write-Host "Tao lai database $Database va nap goi backup V2..."
& $psql -h $HostAddress -p $Port -U postgres -d postgres -v ON_ERROR_STOP=1 `
    -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$Database' AND pid <> pg_backend_pid();" `
    -c "DROP DATABASE IF EXISTS $Database;" `
    -c "CREATE DATABASE $Database;" | Out-Host
if ($LASTEXITCODE -ne 0) {
    throw "Khong tao lai duoc database $Database"
}

Confirm-DataDirectory
& $pgRestore -h $HostAddress -p $Port -U postgres -d $Database --no-owner --no-privileges --exit-on-error $backupFile | Out-Host
if ($LASTEXITCODE -ne 0) {
    throw "pg_restore that bai voi ma loi $LASTEXITCODE"
}

Confirm-DataDirectory
Push-Location $PackageRoot
try {
    & $psql -h $HostAddress -p $Port -U postgres -d $Database -v ON_ERROR_STOP=1 -f $verifySql | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw "verify_v2.sql that bai voi ma loi $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

& $psql -h $HostAddress -p $Port -U postgres -d $Database -v ON_ERROR_STOP=1 -f $foundationVerifySql | Out-Host
if ($LASTEXITCODE -ne 0) {
    throw "verify-foundation-v2.sql that bai voi ma loi $LASTEXITCODE"
}

Write-Host "Hoan tat restore va kiem tra V1 tren ${HostAddress}:${Port}/$Database (khong dat hay in mat khau demo)."
