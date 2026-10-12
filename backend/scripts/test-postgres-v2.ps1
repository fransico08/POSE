param(
    [Parameter(Mandatory=$true)][string]$DataPackagePath,
    [ValidatePattern('^[0-9A-Fa-f]{64}$')][string]$ExpectedSha256 = '37994087124A2E38707B3C29853D58B9C0A0BCED959F7DC1875FD921340E48A6',
    [string]$PgBin = 'C:\Program Files\PostgreSQL\18\bin',
    [string]$ClusterPath,
    [string]$JdkDirectory = $env:JAVA_HOME,
    [string]$MavenPath,
    [switch]$RequireAcceptance
)
$ErrorActionPreference = 'Stop'
if ($RequireAcceptance -and [string]::IsNullOrWhiteSpace($env:POSE_DEMO_PASSWORD)) {
    throw 'RequireAcceptance requires POSE_DEMO_PASSWORD; no database operations were performed.'
}
if ([string]::IsNullOrWhiteSpace($JdkDirectory)) {
    throw 'JAVA_HOME is not set. Provide -JdkDirectory pointing to a JDK that can compile Java 21.'
}
if (-not (Test-Path -LiteralPath (Join-Path $JdkDirectory 'bin\java.exe'))) {
    throw ('JDK executable missing under ' + $JdkDirectory + '. Set JAVA_HOME or provide -JdkDirectory.')
}
if ([string]::IsNullOrWhiteSpace($MavenPath)) {
    $poseMavenCommand = Get-Command mvn.cmd -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $poseMavenCommand) { $poseMavenCommand = Get-Command mvn -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1 }
    if (-not $poseMavenCommand) { throw 'Maven was not found on PATH. Provide -MavenPath pointing to mvn.cmd.' }
    $MavenPath = $poseMavenCommand.Source
}
if (-not (Test-Path -LiteralPath $MavenPath)) { throw ('Maven executable missing: ' + $MavenPath + '. Provide a valid -MavenPath.') }
$poseRepo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$posePackage = (Resolve-Path -LiteralPath $DataPackagePath).Path
$poseExpectedCluster = [IO.Path]::GetFullPath((Join-Path $poseRepo 'backend\target\auth-postgres-test'))
if ([string]::IsNullOrWhiteSpace($ClusterPath)) { $ClusterPath = $poseExpectedCluster }
$poseCluster = [IO.Path]::GetFullPath($ClusterPath)
if ($poseCluster -ine $poseExpectedCluster) { throw 'Only the test cluster under this worktree/backend/target is allowed.' }
$poseBackup = Join-Path $posePackage 'release\POSE_CRM_DB_v2_FINAL.backup'
if (-not (Test-Path -LiteralPath $poseBackup)) { throw 'Final package backup missing.' }
if ((Get-FileHash -LiteralPath $poseBackup -Algorithm SHA256).Hash -ne $ExpectedSha256) { throw 'STOP: final backup SHA-256 mismatch.' }
$poseManifest = Join-Path $posePackage 'release\BACKUP_SHA256.txt'
if (-not (Test-Path -LiteralPath $poseManifest) -or [IO.File]::ReadAllText($poseManifest) -notmatch [regex]::Escape($ExpectedSha256)) {
    throw 'STOP: backup manifest differs from the reviewed SHA-256.'
}
if (-not (Test-Path -LiteralPath (Join-Path $posePackage 'verify_v2.sql'))) { throw 'Package verifier missing.' }
$posePgCtl = Join-Path $PgBin 'pg_ctl.exe'
$posePsql = Join-Path $PgBin 'psql.exe'
$poseCreateDb = Join-Path $PgBin 'createdb.exe'
$poseRestore = Join-Path $PgBin 'pg_restore.exe'
$poseInitDb = Join-Path $PgBin 'initdb.exe'
$poseDbArgs = @('-w','-h','127.0.0.1','-p','54329','-U','pose_auth_test')
$poseListener = Get-NetTCPConnection -LocalPort 54329 -State Listen -ErrorAction SilentlyContinue
if ($poseListener) {
    $poseActualData = & $posePsql @poseDbArgs -d postgres -Atc 'SHOW data_directory'
    if ($LASTEXITCODE -ne 0 -or $poseActualData.Trim().Replace('/','\') -ine $poseCluster) {
        throw 'STOP: port 54329 belongs to another PostgreSQL server.'
    }
    throw 'Dedicated cluster already running; inspect it before invoking this script. It has not been stopped.'
}
if (Test-Path -LiteralPath (Join-Path $poseCluster 'postmaster.pid')) { throw 'Cluster PID file exists; inspect ownership first.' }
if (-not (Test-Path -LiteralPath (Join-Path $poseCluster 'PG_VERSION'))) {
    New-Item -ItemType Directory -Force -Path $poseCluster | Out-Null
    if ((Get-ChildItem -LiteralPath $poseCluster -Force).Count -gt 0) { throw 'Cluster directory is not empty; refusing initdb.' }
    & $poseInitDb -D $poseCluster -U pose_auth_test --auth-local=trust --auth-host=trust --encoding=UTF8
    if ($LASTEXITCODE -ne 0) { throw 'Dedicated cluster initialization failed.' }
}
$poseSuffix = Get-Date -Format 'yyyyMMddHHmmssfff'
$poseRegression = 'pose_foundation_test_' + $poseSuffix
$poseReview = 'pose_v2_review_' + $poseSuffix
$poseEnvNames = @('JAVA_HOME','POSE_TEST_DATABASE_URL','POSE_TEST_DATABASE_USERNAME','POSE_TEST_DATABASE_PASSWORD','POSE_TEST_PRODUCTION_SCHEMA','POSE_V2_SMOKE_DATABASE_URL','POSE_DEMO_PASSWORD')
$poseSavedEnv = @{}
foreach ($poseName in $poseEnvNames) { $poseSavedEnv[$poseName] = [Environment]::GetEnvironmentVariable($poseName,'Process') }
$poseStarted = $false
try {
    $poseStartArgs = '-D "' + $poseCluster + '" -l "' + (Join-Path (Split-Path $poseCluster) 'auth-postgres-test.log') + '" -o "-p 54329 -h 127.0.0.1" -w -t 15 start'
    $poseStartOptions = @{
        FilePath=$posePgCtl; ArgumentList=$poseStartArgs; WindowStyle='Hidden'; PassThru=$true
        RedirectStandardOutput=(Join-Path (Split-Path $poseCluster) 'pg-start.stdout.log')
        RedirectStandardError=(Join-Path (Split-Path $poseCluster) 'pg-start.stderr.log')
    }
    $poseControl = Start-Process @poseStartOptions
    $null = $poseControl.Handle
    $poseStarted = $true
    for ($poseAttempt=0; $poseAttempt -lt 80 -and -not $poseControl.HasExited; $poseAttempt++) {
        Start-Sleep -Milliseconds 200
        $poseControl.Refresh()
    }
    if (-not $poseControl.HasExited -or $poseControl.ExitCode -ne 0) { throw 'Dedicated PostgreSQL startup failed or timed out.' }
    $poseActualData = & $posePsql @poseDbArgs -d postgres -Atc 'SHOW data_directory'
    if ($LASTEXITCODE -ne 0 -or $poseActualData.Trim().Replace('/','\') -ine $poseCluster) { throw 'STOP: unexpected PostgreSQL data_directory.' }
    & $poseCreateDb @poseDbArgs $poseRegression
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create fresh regression database.' }
    & $poseCreateDb @poseDbArgs $poseReview
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create fresh review database.' }
    & $poseRestore @poseDbArgs -d $poseReview --exit-on-error --no-owner --no-privileges $poseBackup
    if ($LASTEXITCODE -ne 0) { throw 'Final restore failed.' }
    Push-Location $posePackage
    try {
        & $posePsql @poseDbArgs -d $poseReview -v ON_ERROR_STOP=1 -f '.\verify_v2.sql'
        if ($LASTEXITCODE -ne 0) { throw 'Final package verifier failed.' }
    } finally { Pop-Location }
    & $posePsql @poseDbArgs -d $poseReview -v ON_ERROR_STOP=1 -f (Join-Path $poseRepo 'database\verify-foundation-v2.sql')
    if ($LASTEXITCODE -ne 0) { throw 'Foundation assertions failed.' }
    $poseCredentialSql = "SELECT md5(string_agg(user_id::text || ':' || password_hash, ',' ORDER BY user_id)) FROM credentials"
    $poseCredentialsBefore = & $posePsql @poseDbArgs -d $poseReview -Atc $poseCredentialSql
    if ($LASTEXITCODE -ne 0) { throw 'Cannot fingerprint original credentials.' }
    $env:JAVA_HOME = $JdkDirectory
    $env:POSE_TEST_DATABASE_URL = 'jdbc:postgresql://127.0.0.1:54329/' + $poseRegression
    $env:POSE_TEST_DATABASE_USERNAME = 'pose_auth_test'
    $env:POSE_TEST_DATABASE_PASSWORD = ''
    $env:POSE_TEST_PRODUCTION_SCHEMA = 'true'
    $env:POSE_V2_SMOKE_DATABASE_URL = 'jdbc:postgresql://127.0.0.1:54329/' + $poseReview
    if ([string]::IsNullOrWhiteSpace($env:POSE_DEMO_PASSWORD)) { Write-Host 'POSE_DEMO_PASSWORD is absent: demo acceptance tests will be skipped.' }
    Push-Location (Join-Path $poseRepo 'backend')
    try {
        $poseVerificationStarted = [DateTime]::UtcNow
        & $MavenPath -q '-Dlogging.level.root=ERROR' '-Dspring.main.banner-mode=off' verify
        if ($LASTEXITCODE -ne 0) { throw 'Foundation verification failed; inspect target/surefire-reports.' }
    } finally { Pop-Location }
    if ($RequireAcceptance) {
        $poseAcceptanceReport = Join-Path $poseRepo 'backend\target\surefire-reports\TEST-com.pose.backend.auth.DemoAccountAcceptanceTest.xml'
        if (-not (Test-Path -LiteralPath $poseAcceptanceReport)) { throw 'Required acceptance report was not generated.' }
        $poseAcceptanceInfo = Get-Item -LiteralPath $poseAcceptanceReport
        if ($poseAcceptanceInfo.LastWriteTimeUtc -lt $poseVerificationStarted) { throw 'Required acceptance report is stale.' }
        $poseAcceptanceXml = [xml](Get-Content -LiteralPath $poseAcceptanceReport -Raw -Encoding UTF8)
        $poseAcceptanceSuite = $poseAcceptanceXml.testsuite
        if ([int]$poseAcceptanceSuite.tests -le 0 -or [int]$poseAcceptanceSuite.skipped -ne 0 -or
            [int]$poseAcceptanceSuite.failures -ne 0 -or [int]$poseAcceptanceSuite.errors -ne 0) {
            throw 'Required demo acceptance did not run successfully; verification is incomplete.'
        }
    }
    & $posePsql @poseDbArgs -d $poseReview -v ON_ERROR_STOP=1 -f (Join-Path $poseRepo 'database\verify-foundation-v2.sql')
    if ($LASTEXITCODE -ne 0) { throw 'Post-acceptance assertions failed.' }
    $poseCounts = & $posePsql @poseDbArgs -d $poseReview -Atc 'SELECT (SELECT count(*) FROM organizations), (SELECT count(*) FROM users)'
    if ($LASTEXITCODE -ne 0 -or $poseCounts.Trim() -ne '6|36') { throw 'Tests did not preserve demo organization/user counts.' }
    $poseCredentialsAfter = & $posePsql @poseDbArgs -d $poseReview -Atc $poseCredentialSql
    if ($LASTEXITCODE -ne 0 -or $poseCredentialsBefore.Trim() -cne $poseCredentialsAfter.Trim()) { throw 'Original credentials changed.' }
    $poseCodes = & $posePsql @poseDbArgs -d $poseReview -Atc "SELECT string_agg(code,',' ORDER BY code) FROM organizations"
    if ($LASTEXITCODE -ne 0 -or $poseCodes.Trim() -ne 'cli,edu,log,rea,ret,tech') { throw 'V4 demo login codes are incomplete.' }
    Write-Host ('Verified. Regression DB: ' + $poseRegression + '; restored review DB: ' + $poseReview)
    Write-Host 'V4 has all six codes; original 36 users and credentials unchanged. Test databases retained.'
} finally {
    foreach ($poseName in $poseEnvNames) { [Environment]::SetEnvironmentVariable($poseName,$poseSavedEnv[$poseName],'Process') }
    if ($poseStarted) {
        & $posePgCtl -D $poseCluster -m fast -w -t 15 stop
        if ($LASTEXITCODE -ne 0) { Write-Warning 'Dedicated test cluster did not stop; inspect this exact ClusterPath.' }
    }
}
