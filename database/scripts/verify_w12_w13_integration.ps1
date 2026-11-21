param(
    [string]$PsqlPath = 'psql.exe',
    [int]$Port = 54329
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$targetDatabase = 'pose_w12_w13_integration_test'
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$targetHost = '127.0.0.1'
$postgresUser = 'pose_auth_test'
$expectedDataDirectory = [IO.Path]::GetFullPath((Join-Path $repoRoot 'backend\target\auth-postgres-test')).TrimEnd('\')

function Assert-IsolatedCluster {
    $actualDataDirectory = & $PsqlPath -X -w -h $targetHost -p $Port -U $postgresUser -d postgres -At -v ON_ERROR_STOP=1 -c 'SHOW data_directory;'
    $normalizedDataDirectory = $actualDataDirectory.Trim().Replace('/', '\').TrimEnd('\')
    if ($LASTEXITCODE -ne 0 -or $normalizedDataDirectory -ine $expectedDataDirectory) {
        throw 'Dedicated PostgreSQL cluster verification failed.'
    }
}

function Resolve-PsqlExecutable {
    param([Parameter(Mandatory)][string]$Value)

    if (Test-Path -LiteralPath $Value -PathType Leaf) {
        return (Resolve-Path -LiteralPath $Value).Path
    }

    $command = Get-Command -Name $Value -CommandType Application -ErrorAction SilentlyContinue
    if ($null -eq $command) {
        throw "psql executable was not found: $Value. Add PostgreSQL 18 psql.exe to PATH or pass -PsqlPath."
    }

    return $command.Source
}

$PsqlPath = Resolve-PsqlExecutable -Value $PsqlPath
if ($Port -ne 54329) { throw 'Only the dedicated PostgreSQL test port 54329 is permitted for this integration script.' }
Assert-IsolatedCluster

$sqlDollarQuote = '$$'
$sql = @"
BEGIN READ ONLY;
DO $sqlDollarQuote
BEGIN
  IF current_database() <> '$targetDatabase' THEN RAISE EXCEPTION 'wrong target database'; END IF;
  IF current_setting('server_version_num')::int < 180000 OR current_setting('server_version_num')::int >= 190000 THEN RAISE EXCEPTION 'PostgreSQL 18 is required'; END IF;
  IF (SELECT count(*) FROM flyway_schema_history WHERE success) <> 14 THEN RAISE EXCEPTION 'expected exactly Flyway V1-V14'; END IF;
  IF (SELECT count(*) FROM organizations)<>6 OR (SELECT count(*) FROM users)<>36 OR (SELECT count(*) FROM customers)<>10000 OR (SELECT count(*) FROM leads)<>15000 OR (SELECT count(*) FROM opportunities)<>8347 OR (SELECT count(*) FROM interactions)<>45006 OR (SELECT count(*) FROM crm_tasks)<>18000 THEN RAISE EXCEPTION 'W12 canonical counts changed'; END IF;
  IF (SELECT count(*) FROM channel_accounts)<>6 OR (SELECT count(*) FROM conversations)<>300 OR (SELECT count(*) FROM conversation_messages)<>1927 THEN RAISE EXCEPTION 'W13 count mismatch'; END IF;
  IF EXISTS (SELECT 1 FROM channel_accounts a LEFT JOIN organizations o ON o.id=a.organization_id LEFT JOIN teams t ON t.id=a.team_id AND t.organization_id=a.organization_id LEFT JOIN users u ON u.id=a.default_assignee_user_id AND u.organization_id=a.organization_id WHERE o.id IS NULL OR t.id IS NULL OR u.id IS NULL) THEN RAISE EXCEPTION 'channel account tenant FK mismatch'; END IF;
  IF EXISTS (SELECT 1 FROM conversations c LEFT JOIN channel_accounts a ON a.id=c.channel_account_id AND a.organization_id=c.organization_id LEFT JOIN customers cu ON cu.id=c.customer_id AND cu.organization_id=c.organization_id LEFT JOIN leads l ON l.id=c.lead_id AND l.organization_id=c.organization_id LEFT JOIN users u ON u.id=c.assigned_user_id AND u.organization_id=c.organization_id WHERE a.id IS NULL OR u.id IS NULL OR (c.customer_id IS NULL)=(c.lead_id IS NULL) OR (c.customer_id IS NOT NULL AND cu.id IS NULL) OR (c.lead_id IS NOT NULL AND l.id IS NULL)) THEN RAISE EXCEPTION 'conversation tenant FK mismatch'; END IF;
  IF EXISTS (SELECT 1 FROM conversation_messages m LEFT JOIN conversations c ON c.id=m.conversation_id AND c.organization_id=m.organization_id LEFT JOIN users u ON u.id=m.sent_by_user_id AND u.organization_id=m.organization_id WHERE c.id IS NULL OR (m.sent_by_user_id IS NOT NULL AND u.id IS NULL) OR position(chr(65533) IN m.content)>0) THEN RAISE EXCEPTION 'message tenant FK or Unicode mismatch'; END IF;
  IF EXISTS (SELECT 1 FROM channel_accounts GROUP BY organization_id,channel_type,external_account_id HAVING count(*)>1) OR EXISTS (SELECT 1 FROM conversations GROUP BY channel_account_id,external_thread_id HAVING count(*)>1) OR EXISTS (SELECT 1 FROM conversation_messages GROUP BY conversation_id,direction,external_message_id HAVING count(*)>1) THEN RAISE EXCEPTION 'W13 duplicate business key'; END IF;
END $sqlDollarQuote;
COMMIT;
SELECT 'W12_W13_INTEGRATION_VERIFIED|w12=PASS|accounts=6|conversations=300|messages=1927';
"@
$result = $sql | & $PsqlPath -X -w -h $targetHost -p $Port -U $postgresUser -d $targetDatabase -At -v ON_ERROR_STOP=1
if ($LASTEXITCODE -ne 0 -or $result -notcontains 'W12_W13_INTEGRATION_VERIFIED|w12=PASS|accounts=6|conversations=300|messages=1927') { throw 'W12/W13 integration verification failed.' }
$result
