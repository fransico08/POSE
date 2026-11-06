param(
    [Parameter(Mandatory=$true)][string]$DataPackagePath,
    [ValidatePattern('^[0-9A-Fa-f]{64}$')][string]$ExpectedSha256 = '37994087124A2E38707B3C29853D58B9C0A0BCED959F7DC1875FD921340E48A6',
    [string]$PgBin = 'C:\Program Files\PostgreSQL\18\bin',
    [string]$JdkDirectory = $env:JAVA_HOME,
    [string]$MavenPath,
    [switch]$Rehearsal
)
# Loads Nhi's final v2 backup into an EMPTY Supabase database, applies the shipped Flyway migrations and closes the Supabase Data API.
# Connection comes from libpq variables set by the operator: PGHOST, PGPORT, PGDATABASE, PGUSER, PGPASSWORD.
# -Rehearsal accepts only the dedicated test cluster on 127.0.0.1:54329 and a pose_supabase_rehearsal_* database.
$ErrorActionPreference = 'Stop'
foreach ($poseName in 'PGHOST','PGUSER','PGPASSWORD') {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($poseName))) { throw ($poseName + ' is not set; no database operations were performed.') }
}
if ([string]::IsNullOrWhiteSpace($env:PGPORT)) { $env:PGPORT = '5432' }
if ([string]::IsNullOrWhiteSpace($env:PGDATABASE)) { $env:PGDATABASE = 'postgres' }
$poseRepo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$posePsql = Join-Path $PgBin 'psql.exe'
$poseRestore = Join-Path $PgBin 'pg_restore.exe'
if ($Rehearsal) {
    if ($env:PGHOST -ne '127.0.0.1' -or $env:PGPORT -ne '54329' -or $env:PGDATABASE -notmatch '^pose_supabase_rehearsal_[a-z0-9_]+$') {
        throw 'Rehearsal only accepts 127.0.0.1:54329 and a pose_supabase_rehearsal_* database.'
    }
    $env:PGSSLMODE = 'disable'
    $poseCluster = [IO.Path]::GetFullPath((Join-Path $poseRepo 'backend\target\auth-postgres-test'))
    $poseActualData = & $posePsql -w -X -d postgres -Atc 'SHOW data_directory'
    if ($LASTEXITCODE -ne 0 -or $poseActualData.Trim().Replace('/','\') -ine $poseCluster) { throw 'STOP: port 54329 is not the dedicated test cluster.' }
} else {
    if ($env:PGHOST -notmatch '\.supabase\.(com|co)$') { throw 'STOP: PGHOST is not a Supabase host.' }
    # Transaction pooling breaks Flyway and JDBC prepared statements; use the session pooler or a direct connection.
    if ($env:PGPORT -eq '6543') { throw 'STOP: port 6543 is the transaction pooler; use the session pooler on 5432.' }
    $env:PGSSLMODE = 'require'
}
if ([string]::IsNullOrWhiteSpace($JdkDirectory) -or -not (Test-Path -LiteralPath (Join-Path $JdkDirectory 'bin\java.exe'))) {
    throw 'JDK missing. Set JAVA_HOME or provide -JdkDirectory pointing to a JDK that can compile Java 21.'
}
if ([string]::IsNullOrWhiteSpace($MavenPath)) {
    $poseMavenCommand = Get-Command mvn.cmd -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $poseMavenCommand) { throw 'Maven was not found on PATH. Provide -MavenPath pointing to mvn.cmd.' }
    $MavenPath = $poseMavenCommand.Source
}
$posePackage = (Resolve-Path -LiteralPath $DataPackagePath).Path
$poseBackup = Join-Path $posePackage 'release\POSE_CRM_DB_v2_FINAL.backup'
if (-not (Test-Path -LiteralPath $poseBackup)) { throw 'Final package backup missing.' }
if ((Get-FileHash -LiteralPath $poseBackup -Algorithm SHA256).Hash -ne $ExpectedSha256) { throw 'STOP: final backup SHA-256 mismatch.' }
$poseManifest = Join-Path $posePackage 'release\BACKUP_SHA256.txt'
if (-not (Test-Path -LiteralPath $poseManifest) -or [IO.File]::ReadAllText($poseManifest) -notmatch [regex]::Escape($ExpectedSha256)) {
    throw 'STOP: backup manifest differs from the reviewed SHA-256.'
}
function Invoke-PoseSql([string]$Sql) {
    $poseOut = & $posePsql -w -X -v ON_ERROR_STOP=1 -Atc $Sql
    if ($LASTEXITCODE -ne 0) { throw 'SQL step failed; the database was left for inspection.' }
    return ($poseOut | Out-String).Trim()
}
# The archive is pg_dump 17 output and sets PostgreSQL 17 parameters.
if ([int](Invoke-PoseSql 'SHOW server_version_num') -lt 170000) { throw 'STOP: target must run PostgreSQL 17 or later.' }
if ((Invoke-PoseSql "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN ('r','v','p')") -ne '0') {
    throw 'STOP: schema public already has tables or views; this script only loads an empty database.'
}
if ($Rehearsal) {
    Invoke-PoseSql "DO `$`$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='anon') THEN CREATE ROLE anon NOLOGIN; END IF; IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN CREATE ROLE authenticated NOLOGIN; END IF; END `$`$" | Out-Null
    Invoke-PoseSql "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO anon, authenticated" | Out-Null
}
# Supabase grants new public tables to its API roles by default; stop that before any table exists.
Invoke-PoseSql "ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM anon, authenticated; ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM anon, authenticated; ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON FUNCTIONS FROM anon, authenticated" | Out-Null

$poseRun = [guid]::NewGuid().ToString('N')
$poseList = Join-Path ([IO.Path]::GetTempPath()) ('pose-restore-' + $poseRun + '.list')
$poseAppLog = Join-Path ([IO.Path]::GetTempPath()) ('pose-migrate-' + $poseRun + '.out.log')
$poseAppErr = Join-Path ([IO.Path]::GetTempPath()) ('pose-migrate-' + $poseRun + '.err.log')
$poseSavedEnv = @{}
$poseEnvNames = 'POSE_DATABASE_URL','POSE_DATABASE_USERNAME','POSE_DATABASE_PASSWORD','POSE_JWT_SECRET_BASE64','JAVA_HOME'
foreach ($poseName in $poseEnvNames) { $poseSavedEnv[$poseName] = [Environment]::GetEnvironmentVariable($poseName) }
try {
    # Supabase already owns schema public; restore only the POSE objects inside it.
    $poseEntries = & $poseRestore -l $poseBackup
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read the backup table of contents.' }
    $poseEntries | Where-Object { $_ -notmatch ' (SCHEMA|COMMENT|ACL) - (SCHEMA )?public ' } | Set-Content -LiteralPath $poseList -Encoding ASCII
    & $poseRestore -w -d $env:PGDATABASE --exit-on-error --single-transaction --no-owner --no-privileges -L $poseList $poseBackup
    if ($LASTEXITCODE -ne 0) { throw 'Restore failed; the single transaction was rolled back.' }
    Push-Location $posePackage
    try {
        & $posePsql -w -X -v ON_ERROR_STOP=1 -q -f '.\verify_v2.sql'
        if ($LASTEXITCODE -ne 0) { throw 'Final package verifier failed.' }
    } finally { Pop-Location }
    & $posePsql -w -X -v ON_ERROR_STOP=1 -q -f (Join-Path $poseRepo 'database\verify-foundation-v2.sql')
    if ($LASTEXITCODE -ne 0) { throw 'Foundation assertions failed.' }

    $env:JAVA_HOME = $JdkDirectory
    Push-Location $poseRepo
    try {
        & $MavenPath -q -pl backend -am -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw 'Backend build failed.' }
    } finally { Pop-Location }
    $poseJar = Get-ChildItem -LiteralPath (Join-Path $poseRepo 'backend\target') -Filter 'pose-backend-*.jar' | Where-Object { $_.Name -notmatch 'plain|sources' } | Select-Object -First 1
    if (-not $poseJar) { throw 'Backend JAR missing after build.' }
    $poseSsl = if ($Rehearsal) { 'disable' } else { 'require' }
    $env:POSE_DATABASE_URL = 'jdbc:postgresql://' + $env:PGHOST + ':' + $env:PGPORT + '/' + $env:PGDATABASE + '?sslmode=' + $poseSsl
    $env:POSE_DATABASE_USERNAME = $env:PGUSER
    $env:POSE_DATABASE_PASSWORD = $env:PGPASSWORD
    # Throwaway signing key: this run only migrates and validates, it serves no requests.
    $poseKey = New-Object byte[] 48
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($poseKey)
    $env:POSE_JWT_SECRET_BASE64 = [Convert]::ToBase64String($poseKey)
    # One-time baseline of the verified restore at version 1, then every later migration. Security needs the servlet context, so the app
    # starts on a random loopback port and is stopped once startup (Flyway plus Hibernate validation) has completed.
    $poseApp = Start-Process -FilePath (Join-Path $JdkDirectory 'bin\java.exe') -NoNewWindow -PassThru -RedirectStandardOutput $poseAppLog -RedirectStandardError $poseAppErr -ArgumentList @(
        '-jar', ('"' + $poseJar.FullName + '"'), '--server.port=0', '--server.address=127.0.0.1', '--spring.main.banner-mode=off',
        '--logging.level.root=WARN', '--logging.level.com.pose.backend.PoseBackendApplication=INFO',
        '--spring.flyway.baseline-on-migrate=true', '--spring.flyway.baseline-version=1')
    $null = $poseApp.Handle
    $poseDeadline = (Get-Date).AddSeconds(180)
    $poseStarted = $false
    while (-not $poseApp.HasExited -and (Get-Date) -lt $poseDeadline) {
        if ((Get-Content -LiteralPath $poseAppLog -Raw -ErrorAction SilentlyContinue) -match 'Started PoseBackendApplication') { $poseStarted = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $poseApp.HasExited) { Stop-Process -Id $poseApp.Id -Force; $poseApp.WaitForExit() }
    if (-not $poseStarted) {
        Get-Content -LiteralPath $poseAppLog, $poseAppErr -Tail 40 -ErrorAction SilentlyContinue
        throw 'Flyway migration or schema validation failed.'
    }

    # Tables created by restore/Flyway: no API-role grants, RLS on as a second barrier. The owner role used by the backend bypasses RLS.
    Invoke-PoseSql "REVOKE ALL ON ALL TABLES IN SCHEMA public FROM anon, authenticated; REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM anon, authenticated; REVOKE ALL ON ALL FUNCTIONS IN SCHEMA public FROM anon, authenticated; DO `$`$ DECLARE t record; BEGIN FOR t IN SELECT tablename FROM pg_tables WHERE schemaname='public' LOOP EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t.tablename); END LOOP; END `$`$" | Out-Null

    & $posePsql -w -X -v ON_ERROR_STOP=1 -q -f (Join-Path $poseRepo 'database\verify-foundation-v2.sql')
    if ($LASTEXITCODE -ne 0) { throw 'Post-migration foundation assertions failed.' }
    # Baseline 1 followed by every versioned migration shipped in this checkout, in order.
    $poseVersions = @('1') + @(Get-ChildItem -LiteralPath (Join-Path $poseRepo 'backend\src\main\resources\db\migration') -Filter 'V*__*.sql' |
        ForEach-Object { [int]($_.Name -replace '^V(\d+)__.*$','$1') } | Sort-Object | ForEach-Object { [string]$_ })
    if ((Invoke-PoseSql "SELECT string_agg(version,',' ORDER BY installed_rank) FROM flyway_schema_history WHERE success") -ne ($poseVersions -join ',')) { throw 'Flyway history differs from baseline 1 plus the shipped migrations.' }
    if ((Invoke-PoseSql 'SELECT (SELECT count(*) FROM organizations) || ''|'' || (SELECT count(*) FROM users)') -ne '6|36') { throw 'Demo organization/user counts differ.' }
    if ((Invoke-PoseSql "SELECT string_agg(code,',' ORDER BY code) FROM organizations") -ne 'cli,edu,log,rea,ret,tech') { throw 'V4 demo login codes are incomplete.' }
    if ((Invoke-PoseSql "SELECT count(*) FROM pg_tables WHERE schemaname='public' AND NOT rowsecurity") -ne '0') { throw 'A public table is missing RLS.' }
    if ((Invoke-PoseSql "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace CROSS JOIN (VALUES ('anon'),('authenticated')) r(name) WHERE n.nspname='public' AND c.relkind IN ('r','v','p') AND has_table_privilege(r.name, c.oid, 'SELECT,INSERT,UPDATE,DELETE')") -ne '0') { throw 'Supabase API roles still reach a public table or view.' }
    Write-Host ('Verified: backup restored, Flyway ' + ($poseVersions -join ',') + ' applied, 6 organizations/36 users, six login codes, API roles without table access, RLS on every public table.')
} finally {
    foreach ($poseName in $poseEnvNames) { [Environment]::SetEnvironmentVariable($poseName,$poseSavedEnv[$poseName],'Process') }
    Remove-Item -LiteralPath $poseList, $poseAppLog, $poseAppErr -ErrorAction SilentlyContinue
}
