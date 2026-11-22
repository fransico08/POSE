[CmdletBinding()]
param(
    [string]$JdkDirectory = $env:JAVA_HOME,
    [string]$MavenPath,
    [switch]$ResumeExistingTarget,
    [switch]$ResumeFromRestoredV14
)

# W15-V1-03 manual rehearsal. This script only targets the disposable 127.0.0.1:54331 cluster.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding

$ExpectedCommit = '7fc41a2daa3c670eafc4e39c92e837a777c50fcb'
$ExpectedBackupHash = '651D6DE98FC918FC7DE54DE32898CB915879AD0EB502DEDBF1DA932005F763B1'
$ExpectedV15Hash = 'A06ECDB0993E36EF8DAFCF98F5833D1485482C04CCA95022D7C5F2BC49807B10'
$SourceHost = '127.0.0.1'; $SourcePort = 54329; $SourceDatabase = 'pose_w12_w13_integration_test'; $SourceUser = 'pose_auth_test'
$TargetHost = '127.0.0.1'; $TargetPort = 54331; $TargetDatabase = 'pose_w15_v14_v15_rehearsal'; $TargetUser = 'pose_w15_rehearsal_admin'
$PgBin = 'C:\Program Files\PostgreSQL\18\bin'
$Psql = Join-Path $PgBin 'psql.exe'
$PgRestore = Join-Path $PgBin 'pg_restore.exe'
$InitDb = Join-Path $PgBin 'initdb.exe'
$PgCtl = Join-Path $PgBin 'pg_ctl.exe'
$Createdb = Join-Path $PgBin 'createdb.exe'
$WorktreeRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$TargetDataDirectory = Join-Path $WorktreeRoot 'backend\target\w15-v15-rehearsal-postgres'
$BackupPath = Join-Path $WorktreeRoot 'backend\target\w15-v15-rehearsal-backups\pose_w12_w13_integration_test_v14_20261122.dump'
$BaselineDirectory = Join-Path $WorktreeRoot 'backend\src\main\resources\db\baseline'
$MigrationDirectory = Join-Path $WorktreeRoot 'backend\src\main\resources\db\migration'
$V15Path = Join-Path $MigrationDirectory 'V15__customer_signals.sql'
$SourceDataDirectory = 'E:\POSE\Lab\POSE\backend\target\auth-postgres-test'
$Report = [System.Collections.Generic.List[string]]::new()
$StartedTarget = $false
$ResumedTarget = $false
$PlainPassword = $null
$OriginalPgPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD')
$EnvironmentNames = @(
    'JAVA_HOME',
    'POSE_DATABASE_URL',
    'POSE_DATABASE_USERNAME',
    'POSE_DATABASE_PASSWORD',
    'POSE_JWT_SECRET_BASE64',
    'POSE_AI_ENABLED',
    'POSE_AI_PROVIDER',
    'POSE_WORKFLOW_WORKER_ENABLED',
    'POSE_CHANNEL_TELEGRAM_SMOKE_ENABLED'
)
$SavedEnvironment = @{}
foreach ($environmentName in $EnvironmentNames) {
    $SavedEnvironment[$environmentName] = [Environment]::GetEnvironmentVariable($environmentName, 'Process')
}

function Add-Result([string]$Name, [string]$Status, [string]$Evidence) {
    $line = '{0}|{1}|{2}' -f $Name, $Status, $Evidence
    $Report.Add($line)
    Write-Host $line
}

function Stop-Rehearsal([string]$Message) {
    Add-Result 'W15_V1_03_REHEARSAL' 'FAIL' $Message
    throw $Message
}

function Assert-Equal([string]$Actual, [string]$Expected, [string]$Name) {
    if ($Actual -cne $Expected) { Stop-Rehearsal "$Name actual=$Actual expected=$Expected" }
}

function Normalize-PathText([string]$PathText) {
    return $PathText.Replace('/', '\').TrimEnd('\')
}

function Normalize-VerifiedIpv4Loopback([string]$Address, [string]$Name) {
    if ([string]::IsNullOrWhiteSpace($Address)) {
        Stop-Rehearsal "$Name missing_loopback_address"
    }

    switch ($Address.Trim()) {
        '127.0.0.1' {
            return '127.0.0.1'
        }
        '127.0.0.1/32' {
            return '127.0.0.1'
        }
        default {
            Stop-Rehearsal "$Name invalid_loopback_address=$Address"
        }
    }
}

function Invoke-External([string]$Name, [string]$FilePath, [string[]]$Arguments) {
    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) { Stop-Rehearsal "$Name exit_code=$LASTEXITCODE" }
}

function Get-JavaMajorVersion([string]$JavaPath) {
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $JavaPath
    $startInfo.Arguments = '-version'
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    if (-not $process.Start()) { Stop-Rehearsal 'java_version_process_start_failed' }
    $standardOutput = $process.StandardOutput.ReadToEndAsync()
    $standardError = $process.StandardError.ReadToEndAsync()
    $process.WaitForExit()
    $versionText = $standardOutput.Result + "`n" + $standardError.Result
    if ($process.ExitCode -ne 0) { Stop-Rehearsal "java_version_query_failed exit_code=$($process.ExitCode)" }
    $match = [regex]::Match($versionText, 'version\s+"(?<major>\d+)')
    if (-not $match.Success) { Stop-Rehearsal 'java_version_parse_failed' }
    return [int]$match.Groups['major'].Value
}

function Invoke-SourceSql([string]$Sql) {
    $output = @(& $Psql -X -w -q -t -A -v ON_ERROR_STOP=1 -h $SourceHost -p $SourcePort -U $SourceUser -d $SourceDatabase -c $Sql)
    if ($LASTEXITCODE -ne 0) { Stop-Rehearsal 'source_psql_query_failed' }
    return ConvertTo-SqlResult $output
}

function Invoke-TargetSql([string]$Sql) {
    $output = @(& $Psql -X -w -q -t -A -v ON_ERROR_STOP=1 -h $TargetHost -p $TargetPort -U $TargetUser -d $TargetDatabase -c $Sql)
    if ($LASTEXITCODE -ne 0) { Stop-Rehearsal 'target_psql_query_failed' }
    return ConvertTo-SqlResult $output
}

function Invoke-TargetAdminSql([string]$Sql) {
    $output = @(& $Psql -X -w -q -t -A -v ON_ERROR_STOP=1 -h $TargetHost -p $TargetPort -U $TargetUser -d postgres -c $Sql)
    if ($LASTEXITCODE -ne 0) { Stop-Rehearsal 'target_admin_psql_query_failed' }
    return ConvertTo-SqlResult $output
}

function ConvertTo-SqlResult([object[]]$Output) {
    $rows = New-Object 'System.Collections.Generic.List[string]'

    foreach ($value in $Output) {
        if ($null -eq $value) {
            continue
        }

        $row = ([string]$value).Trim()
        if (-not [string]::IsNullOrWhiteSpace($row)) {
            [void]$rows.Add($row)
        }
    }

    return [pscustomobject]@{
        Rows     = [string[]]$rows.ToArray()
        RowCount = [int]$rows.Count
    }
}

function Get-SingleSqlRow([psobject]$SqlResult, [string]$Name) {
    if ($null -eq $SqlResult -or $SqlResult.RowCount -ne 1) {
        $actualRowCount = if ($null -eq $SqlResult) { 'null' } else { $SqlResult.RowCount }
        Stop-Rehearsal "$Name expected_one_row actual_count=$actualRowCount"
    }

    $row = [string]$SqlResult.Rows[0]
    if ([string]::IsNullOrWhiteSpace($row)) {
        Stop-Rehearsal "$Name expected_nonempty_row"
    }

    return $row
}

function Split-DelimitedSqlRow([string]$Row, [int]$ExpectedFieldCount, [string]$Name) {
    $fields = @($Row.Split([char]'|'))
    if ($fields.Count -ne $ExpectedFieldCount) {
        Stop-Rehearsal "$Name expected_fields=$ExpectedFieldCount actual_fields=$($fields.Count)"
    }
    return $fields
}

function New-FingerprintSnapshot([psobject]$SqlResult, [string]$Name) {
    if ($null -eq $SqlResult -or $SqlResult.RowCount -ne 7) {
        $actualRowCount = if ($null -eq $SqlResult) { 'null' } else { $SqlResult.RowCount }
        Stop-Rehearsal "$Name expected_rows=7 actual_rows=$actualRowCount"
    }

    $lines = [string[]]$SqlResult.Rows
    return [pscustomobject]@{
        Lines    = $lines
        RowCount = [int]$SqlResult.RowCount
        Text     = [string]::Join("`n", $lines)
    }
}

function Assert-FingerprintSnapshotEqual([psobject]$Actual, [psobject]$Expected, [string]$Name) {
    if ($null -eq $Actual -or $null -eq $Expected) {
        Stop-Rehearsal "$Name fingerprint_snapshot_missing"
    }

    if ($Actual.RowCount -ne 7 -or $Expected.RowCount -ne 7) {
        Stop-Rehearsal "$Name fingerprint_row_count_invalid actual=$($Actual.RowCount) expected=$($Expected.RowCount)"
    }

    if ($Actual.Text -cne $Expected.Text) {
        Stop-Rehearsal $Name
    }
}

function Read-TargetPassword([string]$Prompt) {
    $securePassword = Read-Host $Prompt -AsSecureString
    $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)

    try {
        $script:PlainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
        $securePassword.Dispose()
    }

    if ([string]::IsNullOrWhiteSpace($PlainPassword)) {
        Stop-Rehearsal 'empty_rehearsal_password'
    }

    $env:PGPASSWORD = $PlainPassword
}

function Assert-TargetClusterIdentity {
    $identityResult = Invoke-TargetAdminSql @'
BEGIN READ ONLY;
SELECT
    current_setting('data_directory') || '|'
    || inet_server_addr()::text || '|'
    || inet_server_port() || '|'
    || current_setting('server_version_num') || '|'
    || current_user;
COMMIT;
'@
    $identity = Get-SingleSqlRow $identityResult 'target_cluster_identity'
    $parts = @(Split-DelimitedSqlRow $identity 5 'target_cluster_identity')
    Assert-Equal (Normalize-VerifiedIpv4Loopback $parts[1] 'target_cluster_host') $TargetHost 'target_cluster_host'
    Assert-Equal $parts[2] "$TargetPort" 'target_cluster_port'
    if ([int]$parts[3] -lt 180000 -or [int]$parts[3] -ge 190000) {
        Stop-Rehearsal "target_not_postgresql_18 version_num=$($parts[3])"
    }

    Assert-Equal (Normalize-PathText $parts[0]) (Normalize-PathText $TargetDataDirectory) 'target_data_directory'
    Assert-Equal $parts[4] $TargetUser 'target_admin_role'
}

function Assert-EmptyTargetServer {
    $databaseRows = Invoke-TargetAdminSql @'
BEGIN READ ONLY;
SELECT datname
FROM pg_database
WHERE datistemplate = false
ORDER BY datname;
COMMIT;
'@
    if ($databaseRows.RowCount -ne 1 -or $databaseRows.Rows[0] -cne 'postgres') {
        Stop-Rehearsal "target_unexpected_databases=$($databaseRows.Rows -join ',')"
    }

    $targetDatabaseRows = Invoke-TargetAdminSql "BEGIN READ ONLY; SELECT datname FROM pg_database WHERE datname='$TargetDatabase'; COMMIT;"
    if ($targetDatabaseRows.RowCount -ne 0) {
        Stop-Rehearsal "target_database_already_exists=$TargetDatabase"
    }

    $objectCount = Get-SingleSqlRow (Invoke-TargetAdminSql @'
BEGIN READ ONLY;
SELECT count(*)
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public'
  AND c.relkind IN ('r', 'v', 'm', 'p', 'S');
COMMIT;
'@) 'target_public_object_count'
    Assert-Equal $objectCount '0' 'target_public_object_count'
}

function Assert-RestoredTargetServer {
    $databaseRows = Invoke-TargetAdminSql @'
BEGIN READ ONLY;
SELECT datname
FROM pg_database
WHERE datistemplate = false
ORDER BY datname;
COMMIT;
'@
    $expectedDatabases = @('postgres', $TargetDatabase) | Sort-Object
    $unexpectedDatabases = @(Compare-Object -ReferenceObject $expectedDatabases -DifferenceObject $databaseRows.Rows -SyncWindow 0)

    if ($databaseRows.RowCount -ne 2 -or $unexpectedDatabases.Count -ne 0) {
        Stop-Rehearsal "target_restored_database_set_invalid=$($databaseRows.Rows -join ',')"
    }
}

function Get-FingerprintSql([string]$Prefix) {
@"
BEGIN READ ONLY;
SELECT '$Prefix|customers|' || count(*) || '|' || coalesce(md5(string_agg(id::text || ':' || organization_id::text, ',' ORDER BY id)),md5('')) FROM customers;
SELECT '$Prefix|leads|' || count(*) || '|' || coalesce(md5(string_agg(id::text || ':' || organization_id::text, ',' ORDER BY id)),md5('')) FROM leads;
SELECT '$Prefix|interactions|' || count(*) || '|' || coalesce(md5(string_agg(id::text || ':' || organization_id::text || ':' || coalesce(conversation_id::text,'-'), ',' ORDER BY id)),md5('')) FROM interactions;
SELECT '$Prefix|crm_tasks|' || count(*) || '|' || coalesce(md5(string_agg(id::text || ':' || organization_id::text || ':' || coalesce(conversation_id::text,'-'), ',' ORDER BY id)),md5('')) FROM crm_tasks;
SELECT '$Prefix|channel_accounts|' || count(*) || '|' || coalesce(md5(string_agg(id::text || ':' || organization_id::text || ':' || coalesce(team_id::text,'-') || ':' || coalesce(default_assignee_user_id::text,'-'), ',' ORDER BY id)),md5('')) FROM channel_accounts;
SELECT '$Prefix|conversations|' || count(*) || '|' || coalesce(md5(string_agg(id::text || ':' || organization_id::text || ':' || channel_account_id::text || ':' || coalesce(lead_id::text,'-') || ':' || coalesce(customer_id::text,'-') || ':' || coalesce(assigned_user_id::text,'-'), ',' ORDER BY id)),md5('')) FROM conversations;
SELECT '$Prefix|conversation_messages|' || count(*) || '|' || coalesce(md5(string_agg(id::text || ':' || organization_id::text || ':' || conversation_id::text || ':' || coalesce(sent_by_user_id::text,'-') || ':' || coalesce(interaction_id::text,'-'), ',' ORDER BY id)),md5('')) FROM conversation_messages;
COMMIT;
"@
}

function Assert-Guards {
    if ($ResumeExistingTarget -and $ResumeFromRestoredV14) {
        Stop-Rehearsal 'resume_modes_are_mutually_exclusive'
    }

    $allowedUntrackedScript = '?? database/scripts/run_w15_v14_to_v15_rehearsal.ps1'
    $unexpectedGitStatus = @(git -C $WorktreeRoot status --porcelain | Where-Object { $_ -cne $allowedUntrackedScript })
    if ($unexpectedGitStatus.Count -ne 0) { Stop-Rehearsal "unexpected_git_status=$($unexpectedGitStatus -join ',')" }
    Assert-Equal (git -C $WorktreeRoot rev-parse HEAD) $ExpectedCommit 'git_head'
    Assert-Equal (git -C $WorktreeRoot rev-parse origin/Nhi) $ExpectedCommit 'origin_nhi'
    if (-not (Test-Path -LiteralPath $BackupPath -PathType Leaf)) { Stop-Rehearsal "missing_backup=$BackupPath" }
    Assert-Equal ((Get-FileHash -LiteralPath $BackupPath -Algorithm SHA256).Hash.ToUpperInvariant()) $ExpectedBackupHash 'backup_sha256'

    $targetListeners = @(Get-NetTCPConnection -State Listen -LocalPort $TargetPort -ErrorAction SilentlyContinue)
    $usesExistingTarget = $ResumeExistingTarget -or $ResumeFromRestoredV14
    if ($usesExistingTarget) {
        if (-not (Test-Path -LiteralPath $TargetDataDirectory -PathType Container)) {
            Stop-Rehearsal "resume_target_data_directory_missing=$TargetDataDirectory"
        }
        if ($targetListeners.Count -ne 1) {
            Stop-Rehearsal "resume_target_listener_count=$($targetListeners.Count)"
        }
    }
    else {
        if (Test-Path -LiteralPath $TargetDataDirectory) {
            Stop-Rehearsal "target_data_directory_already_exists=$TargetDataDirectory"
        }
        if ($targetListeners.Count -ne 0) {
            Stop-Rehearsal "target_port_already_listening=$TargetPort"
        }
    }

    if ((Get-PSDrive -Name E).Free -lt 3GB) { Stop-Rehearsal 'insufficient_free_space_under_3GB' }
    foreach ($tool in $Psql,$PgRestore,$InitDb,$PgCtl,$Createdb) { if (-not (Test-Path -LiteralPath $tool -PathType Leaf)) { Stop-Rehearsal "missing_postgresql_tool=$tool" } }
    $backupList = @(& $PgRestore --list $BackupPath)
    if ($LASTEXITCODE -ne 0 -or $backupList.Count -eq 0) { Stop-Rehearsal 'backup_archive_unreadable' }
    if ([string]::IsNullOrWhiteSpace($JdkDirectory)) { Stop-Rehearsal 'BLOCKED_jdk_21_or_newer_required' }
    $script:JavaPath = Join-Path $JdkDirectory 'bin\java.exe'
    if (-not (Test-Path -LiteralPath $JavaPath -PathType Leaf)) { Stop-Rehearsal "BLOCKED_java_not_found=$JavaPath" }
    if ((Get-JavaMajorVersion $JavaPath) -lt 21) { Stop-Rehearsal 'BLOCKED_jdk_21_or_newer_required' }
    if ([string]::IsNullOrWhiteSpace($MavenPath)) {
        $mavenCommand = Get-Command mvn.cmd -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($null -eq $mavenCommand) { Stop-Rehearsal 'BLOCKED_maven_not_found' }
        $script:MavenPath = $mavenCommand.Source
    } else {
        if (-not (Test-Path -LiteralPath $MavenPath -PathType Leaf)) { Stop-Rehearsal "BLOCKED_maven_not_found=$MavenPath" }
        $script:MavenPath = $MavenPath
    }
    Assert-Equal ((Get-FileHash -LiteralPath $V15Path -Algorithm SHA256).Hash.ToUpperInvariant()) $ExpectedV15Hash 'canonical_v15_sha256'
    $targetMode = if ($ResumeFromRestoredV14) {
        'resume_from_restored_v14'
    }
    elseif ($ResumeExistingTarget) {
        'resume_existing_target'
    }
    else {
        'fresh_target'
    }
    Add-Result 'STATIC_GUARDS' 'PASS' "baseline_backup_archive_tools_target_path_port_disk_jdk_maven_v15_hash_mode=$targetMode"
}

function Assert-SourceUnchanged([string]$Stage, [psobject]$ExpectedFingerprintSnapshot) {
    $identityResult = Invoke-SourceSql "BEGIN READ ONLY; SELECT current_database() || '|' || inet_server_addr()::text || '|' || inet_server_port() || '|' || current_user || '|' || current_setting('data_directory'); COMMIT;"
    $identity = Get-SingleSqlRow $identityResult "source_identity_$Stage"
    $parts = @(Split-DelimitedSqlRow $identity 5 "source_identity_$Stage")
    Assert-Equal $parts[0] $SourceDatabase "source_database_$Stage"
    Assert-Equal (Normalize-VerifiedIpv4Loopback $parts[1] "source_host_$Stage") $SourceHost "source_host_$Stage"
    Assert-Equal $parts[2] "$SourcePort" "source_port_$Stage"
    Assert-Equal $parts[3] $SourceUser "source_role_$Stage"
    Assert-Equal (Normalize-PathText $parts[4]) (Normalize-PathText $SourceDataDirectory) "source_data_directory_$Stage"
    $historyResult = Invoke-SourceSql "BEGIN READ ONLY; SELECT 'history|success=' || count(*) FILTER (WHERE success) || '|failed=' || count(*) FILTER (WHERE NOT success) || '|v14=' || count(*) FILTER (WHERE version='14' AND success) || '|v15=' || count(*) FILTER (WHERE version='15') FROM flyway_schema_history; COMMIT;"
    $history = Get-SingleSqlRow $historyResult "source_flyway_$Stage"
    Assert-Equal $history 'history|success=14|failed=0|v14=1|v15=0' "source_flyway_$Stage"
    $fingerprintResult = Invoke-SourceSql (Get-FingerprintSql 'SOURCE')
    $actualFingerprintSnapshot = New-FingerprintSnapshot $fingerprintResult "source_fingerprints_$Stage"

    if ($null -ne $ExpectedFingerprintSnapshot) {
        Assert-FingerprintSnapshotEqual $actualFingerprintSnapshot $ExpectedFingerprintSnapshot "source_fingerprint_changed_$Stage"
    }

    Add-Result "SOURCE_$Stage" 'PASS' 'v14_and_fingerprints_unchanged'
    return $actualFingerprintSnapshot
}

function Initialize-TargetCluster {
    Invoke-External 'initdb' $InitDb @(
        '-D', $TargetDataDirectory,
        '-U', $TargetUser,
        '--encoding=UTF8',
        '--auth-local=scram-sha-256',
        '--auth-host=scram-sha-256',
        '--pwprompt'
    )

    Read-TargetPassword 'Nhap lai mat khau vua dat cho pose_w15_rehearsal_admin'
    if (@(Get-NetTCPConnection -State Listen -LocalPort $TargetPort -ErrorAction SilentlyContinue).Count -ne 0) {
        Stop-Rehearsal "target_port_became_busy=$TargetPort"
    }

    Invoke-External 'pg_ctl_start' $PgCtl @(
        '-D', $TargetDataDirectory,
        '-o', "-h $TargetHost -p $TargetPort",
        '-l', (Join-Path $TargetDataDirectory 'postgresql.log'),
        '-w',
        'start'
    )
    $script:StartedTarget = $true

    Assert-TargetClusterIdentity
    Assert-EmptyTargetServer
    Add-Result 'TARGET_CLUSTER' 'PASS' 'initialized_postgresql_18_at_127.0.0.1_54331'
}

function Resume-ExistingTargetCluster {
    Read-TargetPassword 'Nhap mat khau hien tai cho pose_w15_rehearsal_admin'
    Assert-TargetClusterIdentity
    Assert-EmptyTargetServer
    $script:ResumedTarget = $true
    Add-Result 'TARGET_CLUSTER' 'PASS' 'resumed_existing_empty_postgresql_18_at_127.0.0.1_54331'
}

function Resume-FromRestoredV14 {
    Read-TargetPassword 'Nhap mat khau hien tai cho pose_w15_rehearsal_admin'
    Assert-TargetClusterIdentity
    Assert-RestoredTargetServer
    $script:ResumedTarget = $true
    Add-Result 'TARGET_CLUSTER' 'PASS' 'resumed_existing_restored_v14_postgresql_18_at_127.0.0.1_54331'
}

function Restore-V14 {
    Invoke-External 'createdb' $Createdb @('-w', '-h', $TargetHost, '-p', $TargetPort, '-U', $TargetUser, '-T', 'template0', $TargetDatabase)
    Invoke-External 'pg_restore' $PgRestore @('-w', '-h', $TargetHost, '-p', $TargetPort, '-U', $TargetUser, '-d', $TargetDatabase, '--no-owner', '--no-privileges', '--exit-on-error', '--single-transaction', $BackupPath)
    Add-Result 'RESTORE_V14' 'PASS' 'custom_archive_restored_to_disposable_target'
}

function Assert-RestoredV14([psobject]$SourceFingerprintSnapshot) {
    $identity = Get-SingleSqlRow (Invoke-TargetSql "BEGIN READ ONLY; SELECT current_database() || '|' || inet_server_addr()::text || '|' || inet_server_port() || '|' || current_user; COMMIT;") 'target_database_identity'
    $parts = @(Split-DelimitedSqlRow $identity 4 'target_database_identity')
    Assert-Equal $parts[0] $TargetDatabase 'target_database_name'
    Assert-Equal (Normalize-VerifiedIpv4Loopback $parts[1] 'target_database_host') $TargetHost 'target_database_host'
    Assert-Equal $parts[2] "$TargetPort" 'target_database_port'
    Assert-Equal $parts[3] $TargetUser 'target_database_role'
    $history = Get-SingleSqlRow (Invoke-TargetSql "BEGIN READ ONLY; SELECT 'history|success=' || count(*) FILTER (WHERE success) || '|failed=' || count(*) FILTER (WHERE NOT success) || '|v14=' || count(*) FILTER (WHERE version='14' AND success) || '|v15=' || count(*) FILTER (WHERE version='15') FROM flyway_schema_history; COMMIT;") 'restored_flyway_history'
    Assert-Equal $history 'history|success=14|failed=0|v14=1|v15=0' 'restored_flyway_history'
    $counts = Get-SingleSqlRow (Invoke-TargetSql "BEGIN READ ONLY; SELECT 'counts|' || (SELECT count(*) FROM organizations) || '|' || (SELECT count(*) FROM users) || '|' || (SELECT count(*) FROM customers) || '|' || (SELECT count(*) FROM leads) || '|' || (SELECT count(*) FROM opportunities) || '|' || (SELECT count(*) FROM interactions) || '|' || (SELECT count(*) FROM crm_tasks) || '|' || (SELECT count(*) FROM channel_accounts) || '|' || (SELECT count(*) FROM conversations) || '|' || (SELECT count(*) FROM conversation_messages); COMMIT;") 'restored_w12_w13_counts'
    Assert-Equal $counts 'counts|6|36|10000|15000|8347|45006|18000|6|300|1927' 'restored_w12_w13_counts'
    Invoke-TargetSql @'
BEGIN READ ONLY;
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM conversation_messages GROUP BY organization_id,id HAVING count(*) > 1) THEN RAISE EXCEPTION 'message_org_id_duplicate'; END IF;
  IF EXISTS (SELECT 1 FROM channel_accounts a LEFT JOIN organizations o ON o.id=a.organization_id LEFT JOIN teams t ON t.id=a.team_id AND t.organization_id=a.organization_id LEFT JOIN users u ON u.id=a.default_assignee_user_id AND u.organization_id=a.organization_id WHERE o.id IS NULL OR t.id IS NULL OR u.id IS NULL) THEN RAISE EXCEPTION 'channel_account_tenant_mismatch'; END IF;
  IF EXISTS (SELECT 1 FROM conversations c LEFT JOIN channel_accounts a ON a.id=c.channel_account_id AND a.organization_id=c.organization_id LEFT JOIN customers cu ON cu.id=c.customer_id AND cu.organization_id=c.organization_id LEFT JOIN leads l ON l.id=c.lead_id AND l.organization_id=c.organization_id LEFT JOIN users u ON u.id=c.assigned_user_id AND u.organization_id=c.organization_id WHERE a.id IS NULL OR u.id IS NULL OR (c.customer_id IS NULL)=(c.lead_id IS NULL) OR (c.customer_id IS NOT NULL AND cu.id IS NULL) OR (c.lead_id IS NOT NULL AND l.id IS NULL)) THEN RAISE EXCEPTION 'conversation_tenant_mismatch'; END IF;
  IF EXISTS (SELECT 1 FROM conversation_messages m LEFT JOIN conversations c ON c.id=m.conversation_id AND c.organization_id=m.organization_id LEFT JOIN users u ON u.id=m.sent_by_user_id AND u.organization_id=m.organization_id WHERE c.id IS NULL OR (m.sent_by_user_id IS NOT NULL AND u.id IS NULL)) THEN RAISE EXCEPTION 'message_tenant_mismatch'; END IF;
  IF EXISTS (SELECT 1 FROM channel_accounts GROUP BY organization_id HAVING count(*) <> 1) OR EXISTS (SELECT 1 FROM conversations GROUP BY organization_id HAVING count(*) <> 50) OR (SELECT count(DISTINCT organization_id) FROM users) <> 6 OR (SELECT count(DISTINCT organization_id) FROM teams) <> 6 OR (SELECT count(DISTINCT organization_id) FROM customers) <> 6 OR (SELECT count(DISTINCT organization_id) FROM leads) <> 6 OR (SELECT count(DISTINCT organization_id) FROM opportunities) <> 6 OR (SELECT count(DISTINCT organization_id) FROM interactions) <> 6 OR (SELECT count(DISTINCT organization_id) FROM crm_tasks) <> 6 OR (SELECT count(DISTINCT organization_id) FROM conversation_messages) <> 6 THEN RAISE EXCEPTION 'tenant_distribution_mismatch'; END IF;
  IF (SELECT count(*) FROM customers) <> (SELECT count(DISTINCT id) FROM customers) OR (SELECT count(*) FROM leads) <> (SELECT count(DISTINCT id) FROM leads) OR (SELECT count(*) FROM opportunities) <> (SELECT count(DISTINCT id) FROM opportunities) OR (SELECT count(*) FROM interactions) <> (SELECT count(DISTINCT id) FROM interactions) OR (SELECT count(*) FROM crm_tasks) <> (SELECT count(DISTINCT id) FROM crm_tasks) OR (SELECT count(*) FROM channel_accounts) <> (SELECT count(DISTINCT id) FROM channel_accounts) OR (SELECT count(*) FROM conversations) <> (SELECT count(DISTINCT id) FROM conversations) OR (SELECT count(*) FROM conversation_messages) <> (SELECT count(DISTINCT id) FROM conversation_messages) THEN RAISE EXCEPTION 'uuid_integrity_mismatch'; END IF;
END $$;
COMMIT;
'@ | Out-Null
    $targetFingerprintSnapshot = New-FingerprintSnapshot (Invoke-TargetSql (Get-FingerprintSql 'SOURCE')) 'restored_fingerprints'
    Assert-FingerprintSnapshotEqual $targetFingerprintSnapshot $SourceFingerprintSnapshot 'restored_fingerprints_do_not_match_source'
    Add-Result 'RESTORED_V14_VERIFICATION' 'PASS' 'flyway_counts_uuid_fk_tenant_and_fingerprints'
    return $targetFingerprintSnapshot
}

function Invoke-CanonicalV15 {
    $permissions = Get-SingleSqlRow (Invoke-TargetSql "BEGIN READ ONLY; SELECT has_database_privilege(current_user,current_database(),'CREATE')::text || '|' || has_schema_privilege(current_user,'public','CREATE')::text; COMMIT;") 'target_flyway_create_privileges'
    Assert-Equal $permissions 'true|true' 'target_flyway_create_privileges'
    $targetJdbcUrl = "jdbc:postgresql://${TargetHost}:${TargetPort}/${TargetDatabase}?sslmode=disable"
    if ($targetJdbcUrl -cne 'jdbc:postgresql://127.0.0.1:54331/pose_w15_v14_v15_rehearsal?sslmode=disable') { Stop-Rehearsal 'target_jdbc_url_guard_failed' }
    $env:JAVA_HOME = $JdkDirectory
    $env:POSE_DATABASE_URL = $targetJdbcUrl
    $env:POSE_DATABASE_USERNAME = $TargetUser
    $env:POSE_DATABASE_PASSWORD = $PlainPassword
    $jwtBytes = New-Object byte[] 48
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($jwtBytes)
    $env:POSE_JWT_SECRET_BASE64 = [Convert]::ToBase64String($jwtBytes)
    $env:POSE_AI_ENABLED = 'false'
    $env:POSE_AI_PROVIDER = 'mock'
    $env:POSE_WORKFLOW_WORKER_ENABLED = 'false'
    $env:POSE_CHANNEL_TELEGRAM_SMOKE_ENABLED = 'false'
    if ($env:POSE_DATABASE_URL -cne $targetJdbcUrl) { Stop-Rehearsal 'target_database_environment_guard_failed' }
    Push-Location $WorktreeRoot
    try {
        Invoke-External 'backend_package_for_project_managed_flyway' $script:MavenPath @('-q', '-pl', 'backend', '-am', '-DskipTests', 'package')
    }
    finally {
        Pop-Location
    }
    $backendJar = Get-ChildItem -LiteralPath (Join-Path $WorktreeRoot 'backend\target') -Filter 'pose-backend-*.jar' |
        Where-Object { $_.Name -notmatch 'plain|sources' } |
        Select-Object -First 1
    if ($null -eq $backendJar) { Stop-Rehearsal 'backend_jar_missing_after_package' }
    $runId = [guid]::NewGuid().ToString('N')
    $appLog = Join-Path ([IO.Path]::GetTempPath()) "pose-w15-flyway-$runId.out.log"
    $appErrorLog = Join-Path ([IO.Path]::GetTempPath()) "pose-w15-flyway-$runId.err.log"
    $started = Get-Date
    try {
        # Spring Boot uses the Maven-managed Flyway PostgreSQL module and validates before migrating.
        $backendProcess = Start-Process -FilePath $script:JavaPath -WindowStyle Hidden -PassThru -RedirectStandardOutput $appLog -RedirectStandardError $appErrorLog -ArgumentList @(
            '-jar', ('"' + $backendJar.FullName + '"'), '--server.port=0', '--server.address=127.0.0.1', '--spring.main.banner-mode=off',
            '--logging.level.root=WARN', '--logging.level.com.pose.backend.PoseBackendApplication=INFO',
            '--spring.flyway.target=15', '--spring.flyway.out-of-order=false', '--spring.flyway.baseline-on-migrate=false',
            '--spring.flyway.clean-disabled=true', '--spring.flyway.validate-on-migrate=true',
            '--pose.sales-assignment.expiry-scan-delay-ms=600000'
        )
        $deadline = (Get-Date).AddSeconds(180)
        $startedApplication = $false
        while (-not $backendProcess.HasExited -and (Get-Date) -lt $deadline) {
            if ((Get-Content -LiteralPath $appLog -Raw -ErrorAction SilentlyContinue) -match 'Started PoseBackendApplication') {
                $startedApplication = $true
                break
            }
            Start-Sleep -Milliseconds 500
        }
        if (-not $backendProcess.HasExited) {
            Stop-Process -Id $backendProcess.Id -Force
            $backendProcess.WaitForExit()
        }
        if (-not $startedApplication) { Stop-Rehearsal 'project_managed_flyway_failed' }
    }
    finally {
        $duration = [Math]::Round(((Get-Date) - $started).TotalSeconds, 3)
        Remove-Item -LiteralPath $appLog, $appErrorLog -ErrorAction SilentlyContinue
    }
    Add-Result 'FLYWAY_V15' 'PASS' "project_managed_spring_boot_flyway_duration_seconds=$duration"
}

function Assert-V15AndProtectedData([psobject]$PreMigrationFingerprintSnapshot) {
    $history = Get-SingleSqlRow (Invoke-TargetSql "BEGIN READ ONLY; SELECT 'history|success=' || count(*) FILTER (WHERE success) || '|failed=' || count(*) FILTER (WHERE NOT success) || '|v15=' || count(*) FILTER (WHERE version='15' AND success) FROM flyway_schema_history; COMMIT;") 'v15_flyway_history'
    Assert-Equal $history 'history|success=15|failed=0|v15=1' 'v15_flyway_history'
    Invoke-TargetSql @'
BEGIN READ ONLY;
DO $$
DECLARE item TEXT;
BEGIN
  IF to_regclass('public.customer_signal_extractions') IS NULL OR to_regclass('public.customer_signals') IS NULL THEN RAISE EXCEPTION 'missing_v15_table'; END IF;
  FOREACH item IN ARRAY ARRAY['uq_conversation_messages_org_id','ck_signal_extraction_success','uq_signal_extraction_success','fk_signal_extraction_message','fk_signal_extraction_invocation','ck_customer_signals_subject','ck_customer_signals_ai_key','ck_customer_signals_ai_invocation','uq_customer_signals_ai','uq_customer_signals_human','fk_customer_signals_lead','fk_customer_signals_customer','fk_customer_signals_message','fk_customer_signals_invocation','fk_customer_signals_reviewer'] LOOP
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname=item) THEN RAISE EXCEPTION 'missing_constraint:%', item; END IF;
  END LOOP;
  FOREACH item IN ARRAY ARRAY['idx_signal_extractions_message','idx_customer_signals_customer','idx_customer_signals_lead'] LOOP
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='public' AND indexname=item) THEN RAISE EXCEPTION 'missing_index:%', item; END IF;
  END LOOP;
  IF EXISTS (SELECT 1 FROM conversation_messages GROUP BY organization_id,id HAVING count(*) > 1) THEN RAISE EXCEPTION 'message_org_id_duplicate_after_v15'; END IF;
END $$;
COMMIT;
'@ | Out-Null
    $postMigrationFingerprintSnapshot = New-FingerprintSnapshot (Invoke-TargetSql (Get-FingerprintSql 'SOURCE')) 'post_migration_fingerprints'
    Assert-FingerprintSnapshotEqual $postMigrationFingerprintSnapshot $PreMigrationFingerprintSnapshot 'protected_w12_w13_fingerprints_changed_after_v15'
    Add-Result 'V15_SCHEMA_AND_DATA' 'PASS' 'tables_constraints_indexes_and_w12_w13_fingerprints'
}

try {
    Assert-Guards
    $sourceFingerprintSnapshot = Assert-SourceUnchanged 'BEFORE'
    if ($ResumeFromRestoredV14) {
        Resume-FromRestoredV14
        $restoredFingerprintSnapshot = Assert-RestoredV14 $sourceFingerprintSnapshot
    }
    elseif ($ResumeExistingTarget) {
        Resume-ExistingTargetCluster
        Restore-V14
        $restoredFingerprintSnapshot = Assert-RestoredV14 $sourceFingerprintSnapshot
    }
    else {
        Initialize-TargetCluster
        Restore-V14
        $restoredFingerprintSnapshot = Assert-RestoredV14 $sourceFingerprintSnapshot
    }
    Invoke-CanonicalV15
    Assert-V15AndProtectedData $restoredFingerprintSnapshot
    Assert-SourceUnchanged 'AFTER' $sourceFingerprintSnapshot | Out-Null
    Add-Result 'W15_V1_03_REHEARSAL' 'PASS' 'disposable_v14_to_v15_complete_source_unchanged'
}
catch {
    if ($Report.Count -eq 0 -or $Report[$Report.Count - 1] -notmatch '^W15_V1_03_REHEARSAL\|FAIL\|') { Add-Result 'W15_V1_03_REHEARSAL' 'FAIL' $_.Exception.Message }
    throw
}
finally {
    $env:PGPASSWORD = $OriginalPgPassword
    foreach ($environmentName in $EnvironmentNames) {
        [Environment]::SetEnvironmentVariable($environmentName, $SavedEnvironment[$environmentName], 'Process')
    }
    $PlainPassword = $null
    Write-Host 'BAO_CAO_W15_V1_03_BEGIN'
    $Report | ForEach-Object { Write-Host $_ }
    Write-Host 'BAO_CAO_W15_V1_03_END'
    if ($StartedTarget) { Write-Host "DISPOSABLE_CLUSTER_LEFT_RUNNING|data_directory=$TargetDataDirectory|port=$TargetPort" }
    if ($ResumedTarget) { Write-Host "EXISTING_CLUSTER_LEFT_RUNNING|data_directory=$TargetDataDirectory|port=$TargetPort" }
}
