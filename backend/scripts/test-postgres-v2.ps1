param(
    [Parameter(Mandatory=$true)][string]$BackupPath,
    [string]$PgBin = 'C:\Program Files\PostgreSQL\18\bin',
    [string]$ClusterPath = 'D:\POSE\backend\target\auth-postgres-test',
    [string]$JdkDirectory = 'C:\Users\Dell\AppData\Local\POSE\tools\jdk-21.0.12.1+1',
    [string]$MavenPath = 'C:\Users\Dell\AppData\Local\POSE\tools\apache-maven-3.9.16\bin\mvn.cmd'
)
$ErrorActionPreference = 'Stop'
$poseRepo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$poseCluster = (Resolve-Path -LiteralPath $ClusterPath).Path
$poseBackup = (Resolve-Path -LiteralPath $BackupPath).Path
if (-not $poseCluster.EndsWith('\backend\target\auth-postgres-test', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Only the dedicated backend/target/auth-postgres-test cluster is allowed.'
}
if (-not (Test-Path -LiteralPath (Join-Path $poseCluster 'PG_VERSION'))) { throw 'Missing dedicated test cluster.' }
if (Test-Path -LiteralPath (Join-Path $poseCluster 'postmaster.pid')) { throw 'Test cluster already running; inspect ownership first.' }
if ((Get-FileHash -LiteralPath $poseBackup -Algorithm SHA256).Hash -ne '86F8E8EE84616B5EB08FC110DD16305624E857EF0C2BFCCC8B529EE1564F34B0') {
    throw 'Backup is not the reviewed Nhi DB v2 snapshot; compare schema before using this baseline.'
}
if (-not (Test-Path -LiteralPath (Join-Path $JdkDirectory 'bin\java.exe'))) { throw 'Java 21 directory missing.' }
if (-not (Test-Path -LiteralPath $MavenPath)) { throw 'Maven missing.' }
$posePgCtl = Join-Path $PgBin 'pg_ctl.exe'
$posePsql = Join-Path $PgBin 'psql.exe'
$poseCreateDb = Join-Path $PgBin 'createdb.exe'
$poseRestore = Join-Path $PgBin 'pg_restore.exe'
$poseDbArgs = @('-h','127.0.0.1','-p','54329','-U','pose_auth_test')
$poseSuffix = Get-Date -Format 'yyyyMMddHHmmssfff'
$poseRegression = 'pose_foundation_test_' + $poseSuffix
$poseReview = 'pose_v2_review_' + $poseSuffix
$poseEnvNames = @('JAVA_HOME','POSE_TEST_DATABASE_URL','POSE_TEST_DATABASE_USERNAME','POSE_TEST_DATABASE_PASSWORD','POSE_TEST_PRODUCTION_SCHEMA','POSE_V2_SMOKE_DATABASE_URL')
$poseSavedEnv = @{}
foreach ($poseName in $poseEnvNames) { $poseSavedEnv[$poseName] = [Environment]::GetEnvironmentVariable($poseName,'Process') }
$poseStarted = $false
try {
    & $posePgCtl -D $poseCluster -l (Join-Path (Split-Path $poseCluster) 'auth-postgres-test.log') -o '-p 54329 -h 127.0.0.1' -w -t 15 start
    if ($LASTEXITCODE -ne 0) { throw 'Cannot start dedicated test cluster.' }
    $poseStarted = $true
    $poseActualData = & $posePsql @poseDbArgs -d postgres -Atc 'SHOW data_directory'
    if ($LASTEXITCODE -ne 0 -or $poseActualData.Trim().Replace('/','\') -ine $poseCluster) { throw 'Unexpected PostgreSQL server; no tests run.' }
    & $poseCreateDb @poseDbArgs $poseRegression
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create fresh regression database.' }
    & $poseCreateDb @poseDbArgs $poseReview
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create fresh review database.' }
    & $poseRestore @poseDbArgs -d $poseReview --exit-on-error --no-owner --no-privileges $poseBackup
    if ($LASTEXITCODE -ne 0) { throw 'Restore failed.' }
    & $posePsql @poseDbArgs -d $poseReview -v ON_ERROR_STOP=1 -f (Join-Path $poseRepo 'database\verify_v2.sql')
    if ($LASTEXITCODE -ne 0) { throw 'Nhi verification SQL failed.' }
    & $posePsql @poseDbArgs -d $poseReview -v ON_ERROR_STOP=1 -f (Join-Path $poseRepo 'database\verify-foundation-v2.sql')
    if ($LASTEXITCODE -ne 0) { throw 'Foundation data assertions failed.' }
    $env:JAVA_HOME = $JdkDirectory
    $env:POSE_TEST_DATABASE_URL = 'jdbc:postgresql://127.0.0.1:54329/' + $poseRegression
    $env:POSE_TEST_DATABASE_USERNAME = 'pose_auth_test'
    $env:POSE_TEST_DATABASE_PASSWORD = ''
    $env:POSE_TEST_PRODUCTION_SCHEMA = 'true'
    $env:POSE_V2_SMOKE_DATABASE_URL = 'jdbc:postgresql://127.0.0.1:54329/' + $poseReview
    Push-Location (Join-Path $poseRepo 'backend')
    try {
        & $MavenPath -q '-Dlogging.level.root=ERROR' '-Dspring.main.banner-mode=off' verify
        if ($LASTEXITCODE -ne 0) { throw 'Foundation verification failed; inspect target/surefire-reports.' }
    } finally { Pop-Location }
    & $posePsql @poseDbArgs -d $poseReview -v ON_ERROR_STOP=1 -f (Join-Path $poseRepo 'database\verify-foundation-v2.sql')
    if ($LASTEXITCODE -ne 0) { throw 'Post-smoke assertions failed.' }
    $poseCounts = & $posePsql @poseDbArgs -d $poseReview -Atc 'SELECT (SELECT count(*) FROM organizations), (SELECT count(*) FROM users)'
    if ($LASTEXITCODE -ne 0 -or $poseCounts.Trim() -ne '6|36') { throw 'Smoke did not preserve the original demo counts.' }
    Write-Host ('Verified. Regression DB: ' + $poseRegression + '; restored review DB: ' + $poseReview)
    Write-Host 'Test databases retained inside the dedicated cluster. Original backup/passwords unchanged.'
} finally {
    foreach ($poseName in $poseEnvNames) { [Environment]::SetEnvironmentVariable($poseName,$poseSavedEnv[$poseName],'Process') }
    if ($poseStarted) {
        & $posePgCtl -D $poseCluster -m fast -w -t 15 stop
        if ($LASTEXITCODE -ne 0) { Write-Warning 'Dedicated test cluster did not stop; inspect this exact ClusterPath.' }
    }
}
