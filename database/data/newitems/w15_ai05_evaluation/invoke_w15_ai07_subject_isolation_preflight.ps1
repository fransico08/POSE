[CmdletBinding()]
param(
    [string]$PsqlPath = 'C:\Program Files\PostgreSQL\18\bin\psql.exe',
    [string]$HostName = '127.0.0.1',
    [int]$Port = 54331,
    [string]$Database = 'pose_w15_v14_v15_rehearsal',
    [string]$UserName = 'pose_w15_rehearsal_admin',
    [string]$ExpectedDataDirectory = 'E:\POSE\Lab\POSE-W15-PUBLISH\backend\target\w15-v15-rehearsal-postgres',
    [switch]$SelfTest,
    [switch]$EmitSql
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-Value {
    param([string]$Name, [string]$Actual, [string]$Expected)
    if ($Actual -cne $Expected) { throw "$Name không khớp." }
}

function Require-Guid {
    param([string]$Value, [string]$Name)
    $guid = [guid]::Empty
    if (-not [guid]::TryParse($Value, [ref]$guid)) { throw "$Name không phải UUID." }
    return $guid.ToString()
}

function Get-SubjectIsolationOutcome {
    param(
        [int]$CanonicalRows,
        [int]$ValidRows,
        [int]$PendingRows,
        [int]$OutOfScopeRows,
        [int]$ExtractionRows,
        [int]$SignalRows,
        [int]$InvocationRows
    )

    $failures = [System.Collections.Generic.List[string]]::new()
    if ($CanonicalRows -ne 197) { $failures.Add("canonical_expected=197|actual=$CanonicalRows") }
    if ($ValidRows -ne 197) { $failures.Add("valid_expected=197|actual=$ValidRows") }
    if ($PendingRows -ne 197) { $failures.Add("pending_expected=197|actual=$PendingRows") }
    if ($OutOfScopeRows -ne 0) { $failures.Add("out_of_scope_pending_expected=0|actual=$OutOfScopeRows") }
    if ($ExtractionRows -ne 0) { $failures.Add("extractions_expected=0|actual=$ExtractionRows") }
    if ($SignalRows -ne 0) { $failures.Add("signals_expected=0|actual=$SignalRows") }
    if ($InvocationRows -ne 0) { $failures.Add("invocations_expected=0|actual=$InvocationRows") }

    if ($failures.Count -eq 0) {
        return 'W15_AI07_SUBJECT_ISOLATION=PASS'
    }

    return 'W15_AI07_SUBJECT_ISOLATION=BLOCKED|' + ($failures -join '|')
}

if ($SelfTest) {
    if ((Get-SubjectIsolationOutcome 197 197 197 0 0 0 0) -cne 'W15_AI07_SUBJECT_ISOLATION=PASS') {
        throw 'Self-test PASS path failed.'
    }
    $blocked = Get-SubjectIsolationOutcome 197 196 197 1 0 2 0
    if ($blocked -notmatch 'valid_expected=197|out_of_scope_pending_expected=0|signals_expected=0') {
        throw 'Self-test BLOCKED path failed.'
    }
    Write-Output 'W15_AI07_SUBJECT_ISOLATION_SELFTEST=PASS'
    return
}

if (-not (Test-Path -LiteralPath $PsqlPath -PathType Leaf)) {
    throw "Thiếu psql PostgreSQL 18: $PsqlPath"
}

$root = $PSScriptRoot
$roundTwo = Join-Path $root 'round_2_codex_blind_2026-11-25.csv'
$gold = Join-Path $root 'gold_test_annotations.csv'
Assert-Value 'ROUND_2_SHA256' (Get-FileHash $roundTwo -Algorithm SHA256).Hash.ToUpperInvariant() 'E71C0C1882166C90C112C39D8D4BF86A22A13935C706F353F53414FE4B260FD0'
Assert-Value 'GOLD_SHA256' (Get-FileHash $gold -Algorithm SHA256).Hash.ToUpperInvariant() 'D6E0A82F177651D8A6825517ADE8BF0A7A6767FF9AD8244148169CCE21FBE51A'

$rows = @(Import-Csv -LiteralPath $roundTwo -Encoding UTF8)
if ($rows.Count -ne 197) { throw 'Round-2 phải có đúng 197 dòng.' }

$keys = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
$values = foreach ($row in $rows) {
    $organizationId = Require-Guid $row.organization_id 'organization_id'
    $messageId = Require-Guid $row.message_id 'message_id'
    $conversationId = Require-Guid $row.conversation_id 'conversation_id'
    if (-not $keys.Add("$organizationId|$messageId")) { throw 'Trùng khóa canonical.' }
    "('$organizationId'::uuid,'$messageId'::uuid,'$conversationId'::uuid)"
}

$dataDirectory = "'" + $ExpectedDataDirectory.Replace("'", "''") + "'"
$sql = @"
BEGIN TRANSACTION READ ONLY;
SELECT format(
    'W15_TARGET_IDENTITY|database=%s|user=%s|addr=%s|port=%s|data_directory=%s|server_version_num=%s',
    current_database(),
    current_user,
    coalesce(inet_server_addr()::text, '<null>'),
    inet_server_port(),
    current_setting('data_directory'),
    current_setting('server_version_num')
);
DO `$identity_guard`$
DECLARE
    actual_database text := current_database();
    actual_user text := current_user;
    actual_address text := coalesce(inet_server_addr()::text, '<null>');
    actual_port integer := inet_server_port();
    actual_directory text := current_setting('data_directory');
    actual_version integer := current_setting('server_version_num')::int;
BEGIN
    IF actual_database <> '$Database' THEN
        RAISE EXCEPTION 'W15_TARGET_IDENTITY=BLOCKED|database_expected=%|database_actual=%', '$Database', actual_database;
    ELSIF actual_user <> '$UserName' THEN
        RAISE EXCEPTION 'W15_TARGET_IDENTITY=BLOCKED|user_expected=%|user_actual=%', '$UserName', actual_user;
    ELSIF actual_address NOT IN ('127.0.0.1', '127.0.0.1/32') THEN
        RAISE EXCEPTION 'W15_TARGET_IDENTITY=BLOCKED|address_expected=127.0.0.1|address_actual=%', actual_address;
    ELSIF actual_port <> $Port THEN
        RAISE EXCEPTION 'W15_TARGET_IDENTITY=BLOCKED|port_expected=$Port|port_actual=%', actual_port;
    ELSIF replace(actual_directory, chr(92), '/') <>
      replace($dataDirectory, chr(92), '/') THEN
        RAISE EXCEPTION 'W15_TARGET_IDENTITY=BLOCKED|data_directory_expected=%|data_directory_actual=%', $dataDirectory, actual_directory;
    ELSIF actual_version < 180000 OR actual_version >= 190000 THEN
        RAISE EXCEPTION 'W15_TARGET_IDENTITY=BLOCKED|postgres_major_expected=18|server_version_num_actual=%', actual_version;
    END IF;
END
`$identity_guard`$;
WITH expected(organization_id,message_id,conversation_id) AS (
    VALUES
        $($values -join ",`n        ")
), canonical AS (
    SELECT e.*,m.id actual_message_id,m.direction,m.conversation_id actual_conversation_id,c.lead_id,c.customer_id
    FROM expected e
    LEFT JOIN conversation_messages m ON m.organization_id=e.organization_id AND m.id=e.message_id
    LEFT JOIN conversations c ON c.organization_id=m.organization_id AND c.id=m.conversation_id
), subjects AS (
    SELECT DISTINCT organization_id,CASE WHEN lead_id IS NOT NULL THEN 'LEAD' ELSE 'CUSTOMER' END subject_type,COALESCE(lead_id,customer_id) subject_id
    FROM canonical
    WHERE actual_message_id IS NOT NULL AND direction='INBOUND' AND actual_conversation_id=conversation_id
      AND (lead_id IS NOT NULL)<>(customer_id IS NOT NULL)
), pending AS (
    SELECT m.organization_id,m.id message_id,CASE WHEN c.lead_id IS NOT NULL THEN 'LEAD' ELSE 'CUSTOMER' END subject_type,COALESCE(c.lead_id,c.customer_id) subject_id
    FROM conversation_messages m JOIN conversations c ON c.organization_id=m.organization_id AND c.id=m.conversation_id
    WHERE m.direction='INBOUND' AND (c.lead_id IS NOT NULL)<>(c.customer_id IS NOT NULL)
      AND NOT EXISTS (SELECT 1 FROM customer_signal_extractions e WHERE e.organization_id=m.organization_id AND e.message_id=m.id AND e.status='SUCCEEDED')
      AND (SELECT count(*) FROM customer_signal_extractions e WHERE e.organization_id=m.organization_id AND e.message_id=m.id AND e.status='FAILED' AND e.error_code='INVALID_OUTPUT')<2
), checks AS (
    SELECT
        (SELECT count(*) FROM canonical) canonical_rows,
        (SELECT count(*) FROM canonical WHERE actual_message_id IS NOT NULL AND direction='INBOUND' AND actual_conversation_id=conversation_id AND (lead_id IS NOT NULL)<>(customer_id IS NOT NULL)) valid_rows,
        (SELECT count(*) FROM pending p JOIN expected e ON e.organization_id=p.organization_id AND e.message_id=p.message_id) pending_rows,
        (SELECT count(*) FROM pending p JOIN subjects s ON s.organization_id=p.organization_id AND s.subject_type=p.subject_type AND s.subject_id=p.subject_id LEFT JOIN expected e ON e.organization_id=p.organization_id AND e.message_id=p.message_id WHERE e.message_id IS NULL) out_of_scope_rows,
        (SELECT count(*) FROM customer_signal_extractions) extraction_rows,
        (SELECT count(*) FROM customer_signals) signal_rows,
        (SELECT count(*) FROM ai_invocations) invocation_rows
)
SELECT format('W15_AI07_SUBJECT_ISOLATION|canonical=%s|valid=%s|pending=%s|out_of_scope_pending=%s|extractions=%s|signals=%s|invocations=%s',canonical_rows,valid_rows,pending_rows,out_of_scope_rows,extraction_rows,signal_rows,invocation_rows) FROM checks
UNION ALL
SELECT format(
    'W15_AI07_SUBJECT_ISOLATION=%s|canonical_expected=197|canonical_actual=%s|valid_expected=197|valid_actual=%s|pending_expected=197|pending_actual=%s|out_of_scope_pending_expected=0|out_of_scope_pending_actual=%s|extractions_expected=0|extractions_actual=%s|signals_expected=0|signals_actual=%s|invocations_expected=0|invocations_actual=%s',
    CASE WHEN canonical_rows=197 AND valid_rows=197 AND pending_rows=197 AND out_of_scope_rows=0 AND extraction_rows=0 AND signal_rows=0 AND invocation_rows=0 THEN 'PASS' ELSE 'BLOCKED' END,
    canonical_rows, valid_rows, pending_rows, out_of_scope_rows, extraction_rows, signal_rows, invocation_rows
) FROM checks;
ROLLBACK;
"@

if ($EmitSql) {
    Write-Output $sql
    return
}

$temporarySql = New-TemporaryFile
$temporaryError = New-TemporaryFile
try {
    Set-Content -LiteralPath $temporarySql.FullName -Value $sql -Encoding UTF8
    Write-Host 'Nhập mật khẩu target 127.0.0.1:54331 khi psql yêu cầu; script không lưu mật khẩu.'
    $output = @(& $PsqlPath -X -W -v ON_ERROR_STOP=1 -qAt -h $HostName -p $Port -U $UserName -d $Database -f $temporarySql.FullName 2> $temporaryError.FullName)
    $exitCode = $LASTEXITCODE
    $stderr = @(Get-Content -LiteralPath $temporaryError.FullName)
    $output | ForEach-Object { Write-Output $_ }
    $stderr | ForEach-Object { Write-Host "psql stderr: $_" }
    if ($exitCode -ne 0) {
        throw "Subject-isolation preflight BLOCKED với psql exit code $exitCode."
    }
    if (($output -join "`n") -notmatch 'W15_AI07_SUBJECT_ISOLATION=PASS') {
        throw 'Subject-isolation preflight BLOCKED; xem dòng W15_AI07_SUBJECT_ISOLATION để biết expected/actual.'
    }
}
finally {
    if (Test-Path -LiteralPath $temporarySql.FullName) { Remove-Item -LiteralPath $temporarySql.FullName -Force }
    if (Test-Path -LiteralPath $temporaryError.FullName) { Remove-Item -LiteralPath $temporaryError.FullName -Force }
}
