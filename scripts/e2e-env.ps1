param(
    [string]$JdkDirectory = 'C:\Program Files\Java\jdk-25.0.2',
    [string]$PgBin = 'C:\Program Files\PostgreSQL\18\bin',
    [string]$MavenPath = 'C:\Users\Dell\.maven\maven-3.9.15\bin\mvn.cmd',
    [switch]$Build,
    [string]$TestFile = ''
)
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$frontend = Join-Path $repo 'frontend'
$cluster = [IO.Path]::GetFullPath((Join-Path $repo 'backend\target\auth-postgres-test'))
$pgCtl = Join-Path $PgBin 'pg_ctl.exe'
$psql = Join-Path $PgBin 'psql.exe'
$dbArgs = @('-w', '-h', '127.0.0.1', '-p', '54329', '-U', 'pose_auth_test')
$ports = @(8080, 8081, 3010, 3011, 54329)
foreach ($port in $ports) {
    if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) { throw "Port $port is already listening; refusing to modify an existing service." }
}
if (-not (Test-Path -LiteralPath (Join-Path $cluster 'PG_VERSION'))) { throw 'Dedicated PostgreSQL cluster is missing; initialize it with the approved test runner first.' }
if (Test-Path -LiteralPath (Join-Path $cluster 'postmaster.pid')) { throw 'PostgreSQL PID file exists; inspect it before starting.' }
$jar = Join-Path $repo 'backend\target\pose-backend-0.1.0-SNAPSHOT.jar'
$crypto = Join-Path $env:USERPROFILE '.m2\repository\org\springframework\security\spring-security-crypto\6.5.11\spring-security-crypto-6.5.11.jar'
$buildId = Join-Path $frontend '.next\BUILD_ID'
if ($Build) {
    if (-not (Test-Path -LiteralPath (Join-Path $JdkDirectory 'bin\java.exe'))) { throw 'JDK is missing; provide -JdkDirectory.' }
    if (-not (Test-Path -LiteralPath $MavenPath)) { throw 'Maven is missing; provide -MavenPath.' }
    $previousJavaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Process')
    try {
        $env:JAVA_HOME = $JdkDirectory
        Push-Location $frontend
        try { & npm.cmd run build; if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' } } finally { Pop-Location }
        Push-Location $repo
        try { & $MavenPath -pl backend -am package '-DskipTests'; if ($LASTEXITCODE -ne 0) { throw 'Backend package failed.' } } finally { Pop-Location }
    } finally { [Environment]::SetEnvironmentVariable('JAVA_HOME', $previousJavaHome, 'Process') }
}
foreach ($path in @($jar, $crypto, $buildId, (Join-Path $JdkDirectory 'bin\java.exe'))) {
    if (-not (Test-Path -LiteralPath $path)) { throw "Required build or runtime is missing: $path" }
}
$jarTime = (Get-Item -LiteralPath $jar).LastWriteTimeUtc
$backendSource = Join-Path $repo 'backend\src\main'
$workflowSource = Join-Path $repo 'automation\workflow-core\src'
if (@(Get-ChildItem -LiteralPath @($backendSource,$workflowSource) -File -Recurse | Where-Object { $_.LastWriteTimeUtc -gt $jarTime }).Count) {
    throw 'Backend source is newer than the JAR. Rebuild with Maven package -DskipTests or run this script with -Build.'
}
$frontendTime = (Get-Item -LiteralPath $buildId).LastWriteTimeUtc
$frontendInputs = @(Get-ChildItem -LiteralPath (Join-Path $frontend 'src') -File -Recurse)
$frontendInputs += Get-Item -LiteralPath (Join-Path $frontend 'package.json')
$frontendInputs += @(Get-ChildItem -LiteralPath $frontend -File -Filter 'next.config.*')
if (@($frontendInputs | Where-Object { $_.LastWriteTimeUtc -gt $frontendTime }).Count) {
    throw 'Frontend source or configuration is newer than the build. Run npm run build or run this script with -Build.'
}
$clearNames = @(Get-ChildItem Env: | Where-Object { $_.Name -like 'PG*' -or $_.Name -like 'POSE_*' -or $_.Name -like 'SPRING_*' -or $_.Name -in @('JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','NODE_OPTIONS') } | ForEach-Object { $_.Name })
$envNames = @(@('JAVA_HOME','POSE_DATABASE_URL','POSE_DATABASE_USERNAME','POSE_DATABASE_PASSWORD','POSE_JWT_SECRET_BASE64','POSE_E2E_PASSWORD','POSE_E2E_RUN_ID','POSE_AI_ENABLED','POSE_AI_PROVIDER','BACKEND_URL','PORT','SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE') + $clearNames | Select-Object -Unique)
$saved = @{}
foreach ($name in $envNames) { $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$temp = Join-Path ([IO.Path]::GetTempPath()) ('pose-e2e-' + [guid]::NewGuid().ToString('N'))
$db = 'pose_e2e_' + [DateTime]::UtcNow.ToString('yyyyMMddHHmmssfff') + '_' + (Get-Random -Minimum 1000 -Maximum 9999)
$started = $false; $verified = $false; $created = $false; $backend = $null; $frontendProcess = $null; $exitCode = 1
try {
    foreach ($name in $clearNames) { [Environment]::SetEnvironmentVariable($name, $null, 'Process') }
    New-Item -ItemType Directory -Path $temp | Out-Null
    & $pgCtl -D $cluster -l (Join-Path $temp 'postgres.log') -o '-p 54329 -h 127.0.0.1' -w -t 20 start
    if ($LASTEXITCODE -ne 0) {
        if (Get-NetTCPConnection -LocalPort 54329 -State Listen -ErrorAction SilentlyContinue) {
            $started = $true
            $actual = & $psql @dbArgs -d postgres -Atc 'SHOW data_directory'
            $verified = $LASTEXITCODE -eq 0 -and $actual.Trim().Replace('/','\') -ieq $cluster
        }
        throw 'Cannot start dedicated PostgreSQL cluster.'
    }
    $started = $true
    $actual = & $psql @dbArgs -d postgres -Atc 'SHOW data_directory'
    if ($LASTEXITCODE -ne 0 -or $actual.Trim().Replace('/','\') -ine $cluster) { throw 'Unexpected PostgreSQL data_directory; aborting.' }
    $verified = $true
    $source = & $psql @dbArgs -d postgres -Atc "SELECT datname FROM pg_database WHERE datname ~ '^pose_v2_review_[0-9]+$' AND datallowconn ORDER BY datname DESC LIMIT 1"
    if ($LASTEXITCODE -ne 0 -or $source -notmatch '^pose_v2_review_[0-9]+$') { throw 'No eligible pose_v2_review database in the dedicated cluster.' }
    & (Join-Path $PgBin 'createdb.exe') @dbArgs -T $source $db
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create isolated E2E database.' }
    $created = $true
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes); $env:POSE_E2E_PASSWORD = [Convert]::ToBase64String($bytes); $rng.GetBytes($bytes); $env:POSE_JWT_SECRET_BASE64 = [Convert]::ToBase64String($bytes) } finally { $rng.Dispose() }
    $javaSource = 'import org.springframework.security.crypto.bcrypt.BCrypt; public class PoseE2EHash { public static void main(String[] a) { System.out.print(BCrypt.hashpw(System.getenv("POSE_E2E_PASSWORD"), BCrypt.gensalt(12))); } }'
    [IO.File]::WriteAllText((Join-Path $temp 'PoseE2EHash.java'), $javaSource)
    & (Join-Path $JdkDirectory 'bin\javac.exe') -cp $crypto (Join-Path $temp 'PoseE2EHash.java')
    if ($LASTEXITCODE -ne 0) { throw 'Cannot compile BCrypt helper.' }
    $hash = & (Join-Path $JdkDirectory 'bin\java.exe') -cp ($temp + ';' + $crypto) PoseE2EHash
    if ($LASTEXITCODE -ne 0 -or $hash -notmatch '^\$2[aby]\$12\$') { throw 'Cannot generate BCrypt test hash.' }
    $sql = "UPDATE credentials SET password_hash='$hash' WHERE user_id IN (SELECT u.id FROM users u JOIN organizations o ON o.id=u.organization_id WHERE (lower(o.code)='edu' AND lower(u.email) IN ('edu.admin@example.com','edu.mgr@example.com','edu.s01@example.com','edu.s02@example.com','edu.care@example.com','edu.data@example.com')) OR (lower(o.code) IN ('rea','tech','ret','log','cli') AND lower(u.email) IN ('rea.mgr@example.com','tech.mgr@example.com','ret.mgr@example.com','log.mgr@example.com','cli.mgr@example.com') AND u.status='ACTIVE'));"
    $sql | & $psql @dbArgs -d $db -v ON_ERROR_STOP=1 -q
    if ($LASTEXITCODE -ne 0) { throw 'Cannot configure isolated E2E credentials.' }
    $env:JAVA_HOME = $JdkDirectory; $env:POSE_DATABASE_URL = "jdbc:postgresql://127.0.0.1:54329/$db"; $env:POSE_E2E_RUN_ID = $db
    $env:POSE_DATABASE_USERNAME = 'pose_auth_test'; $env:POSE_DATABASE_PASSWORD = ''
    $env:POSE_AI_ENABLED = 'true'; $env:POSE_AI_PROVIDER = 'mock'
    $env:SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE = '5'; $env:PORT = '8081'; $env:BACKEND_URL = 'http://127.0.0.1:8081'
    $backend = Start-Process -FilePath (Join-Path $JdkDirectory 'bin\java.exe') -ArgumentList @('-jar', $jar, '--server.address=127.0.0.1', "--spring.datasource.url=$env:POSE_DATABASE_URL", '--spring.datasource.username=pose_auth_test', '--spring.datasource.password=', '--pose.ai.enabled=true', '--pose.ai.provider=mock') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $temp 'backend.stdout') -RedirectStandardError (Join-Path $temp 'backend.stderr')
    $null = $backend.Handle
    $next = Join-Path $frontend 'node_modules\next\dist\bin\next'
    $frontendProcess = Start-Process -FilePath (Get-Command node.exe).Source -ArgumentList @($next, 'start', '--hostname', '127.0.0.1', '--port', '3011') -WorkingDirectory $frontend -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $temp 'frontend.stdout') -RedirectStandardError (Join-Path $temp 'frontend.stderr')
    $null = $frontendProcess.Handle
    foreach ($port in @(8081,3011)) {
        $deadline = [DateTime]::UtcNow.AddSeconds(60)
        while (-not (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)) {
            if ([DateTime]::UtcNow -gt $deadline -or $backend.HasExited -or $frontendProcess.HasExited) { throw "E2E service failed to start on port $port." }
            Start-Sleep -Milliseconds 200
        }
    }
    Push-Location $frontend
    try {
        if ($TestFile) { & npm.cmd run e2e -- $TestFile } else { & npm.cmd run e2e }
        $exitCode = $LASTEXITCODE
    } finally { Pop-Location }
} finally {
    $cleanupErrors = [System.Collections.Generic.List[string]]::new()
    foreach ($process in @($frontendProcess,$backend)) {
        try { if ($process -and -not $process.HasExited) { Stop-Process -Id $process.Id -Force -ErrorAction Stop; $process.WaitForExit() } }
        catch { $cleanupErrors.Add('Cannot stop an E2E service.') }
    }
    $authDir = [IO.Path]::GetFullPath((Join-Path $frontend ("e2e\.auth\" + $db)))
    $authRoot = [IO.Path]::GetFullPath((Join-Path $frontend 'e2e\.auth'))
    try { if ($db -match '^pose_e2e_[0-9]+_[0-9]+$' -and $authDir -eq (Join-Path $authRoot $db) -and (Test-Path -LiteralPath $authDir)) { Remove-Item -LiteralPath $authDir -Recurse -Force } }
    catch { $cleanupErrors.Add('Cannot remove the temporary E2E session directory.') }
    try {
        if ($created) {
            $actual = & $psql @dbArgs -d postgres -Atc 'SHOW data_directory'
            if ($LASTEXITCODE -ne 0 -or $actual.Trim().Replace('/','\') -ine $cluster -or $db -notmatch '^pose_e2e_[0-9]+_[0-9]+$') { throw 'Unexpected database or cluster.' }
            & (Join-Path $PgBin 'dropdb.exe') @dbArgs --force $db
            if ($LASTEXITCODE -ne 0) { throw 'Cannot drop E2E database.' }
        }
    } catch { $cleanupErrors.Add('Cannot safely remove the E2E database.') }
    try {
        if ($started -and $verified) { & $pgCtl -D $cluster -m fast -w -t 20 stop; if ($LASTEXITCODE -ne 0) { throw 'Cannot stop test cluster.' } }
        elseif ($started) { $cleanupErrors.Add('Cluster was started but its identity could not be verified; it was not stopped.') }
    } catch { $cleanupErrors.Add('Cannot stop the E2E cluster.') }
    try { if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp -Recurse -Force } }
    catch { $cleanupErrors.Add('Cannot remove the temporary E2E directory.') }
    foreach ($name in $envNames) { [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process') }
    foreach ($port in $ports) { if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) { Write-Warning "Port $port is still listening."; $cleanupErrors.Add("Port $port is still listening.") } else { Write-Host "Port $port stopped." } }
    if ($cleanupErrors.Count) { $cleanupErrors | ForEach-Object { Write-Warning $_ }; $exitCode = 1 }
}
exit $exitCode
