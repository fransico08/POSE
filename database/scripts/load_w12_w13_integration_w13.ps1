param(
    [string]$PsqlPath = 'psql.exe',
    [int]$Port = 54329,
    [switch]$Execute
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$targetDatabase = 'pose_w12_w13_integration_test'
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$targetHost = '127.0.0.1'
$postgresUser = 'pose_auth_test'
$expectedDataDirectory = [IO.Path]::GetFullPath((Join-Path $repoRoot 'backend\target\auth-postgres-test')).TrimEnd('\')
$seedRoot = Join-Path $repoRoot 'database\data\seed\w13_ai05'
$manifest = Get-Content -LiteralPath (Join-Path $seedRoot 'manifest.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$expectedHeaders = @{
    'channel_accounts.csv' = 'id,organization_id,channel_type,display_name,external_account_id,status,secret_ref,team_id,default_assignee_user_id,created_at,updated_at'
    'conversations.csv' = 'id,organization_id,channel_account_id,external_thread_id,lead_id,customer_id,assigned_user_id,status,consent_status,last_inbound_at,last_message_at,created_at,updated_at'
    'conversation_messages.csv' = 'id,organization_id,conversation_id,direction,external_message_id,content_type,content,sent_by_user_id,delivery_status,occurred_at,interaction_id,created_at'
}
$expectedCounts = @{ 'channel_accounts.csv' = 6; 'conversations.csv' = 300; 'conversation_messages.csv' = 1927 }

function Assert-W13Assets {
    $w12Manifest = Join-Path $repoRoot 'database\data\seed\multisector_v2\manifest.json'
    if ((Get-FileHash -LiteralPath $w12Manifest -Algorithm SHA256).Hash -cne $manifest.baseSeed.manifestSha256) {
        throw 'W12 canonical manifest checksum mismatch.'
    }
    foreach ($name in $expectedHeaders.Keys) {
        $path = Join-Path $seedRoot $name
        if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -cne $manifest.artifacts.$name) { throw "W13 checksum mismatch: $name" }
        $header = Get-Content -LiteralPath $path -TotalCount 1 -Encoding UTF8
        if ($header -cne $expectedHeaders[$name]) { throw "W13 header mismatch: $name" }
        $rows = @(Import-Csv -LiteralPath $path -Encoding UTF8)
        if ($rows.Count -ne $expectedCounts[$name]) { throw "W13 row count mismatch: $name" }
        if (($rows | ForEach-Object { [Guid]::Empty; $id = [Guid]::Empty; [Guid]::TryParse([string]$_.id, [ref]$id) }).Where({ -not $_ }).Count -ne 0) { throw "W13 invalid UUID: $name" }
    }
    $annotation = Join-Path $seedRoot 'round_1_annotations_consolidated.csv'
    if ((Get-FileHash -LiteralPath $annotation -Algorithm SHA256).Hash -cne $manifest.artifacts.'round_1_annotations_consolidated.csv') { throw 'W13 annotation checksum mismatch.' }
}

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

Assert-W13Assets
if ($Port -ne 54329) { throw 'Only the dedicated PostgreSQL test port 54329 is permitted for this integration script.' }
if (-not $Execute) { 'W12_W13_INTEGRATION_W13_DRY_RUN=PASS accounts=6 conversations=300 messages=1927 annotations=NOT_IMPORTED'; exit 0 }
$PsqlPath = Resolve-PsqlExecutable -Value $PsqlPath
Assert-IsolatedCluster

$accounts = (Join-Path $seedRoot 'channel_accounts.csv').Replace('\', '/').Replace("'", "''")
$conversations = (Join-Path $seedRoot 'conversations.csv').Replace('\', '/').Replace("'", "''")
$messages = (Join-Path $seedRoot 'conversation_messages.csv').Replace('\', '/').Replace("'", "''")
$sqlDollarQuote = '$$'
$sql = @"
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL client_encoding = 'UTF8';
DO $sqlDollarQuote
BEGIN
  IF current_database() <> '$targetDatabase' THEN RAISE EXCEPTION 'wrong target database'; END IF;
  IF current_setting('server_version_num')::int < 180000 OR current_setting('server_version_num')::int >= 190000 THEN RAISE EXCEPTION 'PostgreSQL 18 is required'; END IF;
  IF (SELECT count(*) FROM flyway_schema_history WHERE success) <> 14 OR (SELECT count(*) FROM flyway_schema_history WHERE success AND version IN ('1','2','3','4','5','6','7','8','9','10','11','12','13','14')) <> 14 THEN RAISE EXCEPTION 'expected Flyway V1-V14'; END IF;
  IF (SELECT count(*) FROM organizations) <> 6 OR (SELECT count(*) FROM users) <> 36 OR (SELECT count(*) FROM teams) <> 12 OR (SELECT count(*) FROM team_memberships) <> 36 OR (SELECT count(*) FROM customers) <> 10000 OR (SELECT count(*) FROM leads) <> 15000 THEN RAISE EXCEPTION 'W12 canonical baseline mismatch'; END IF;
  IF (SELECT string_agg(column_name || ':' || data_type || ':' || is_nullable || ':' || coalesce(character_maximum_length::text,'-'), ',' ORDER BY ordinal_position) FROM information_schema.columns WHERE table_schema='public' AND table_name='channel_accounts') <> 'id:uuid:NO:-,organization_id:uuid:NO:-,channel_type:character varying:NO:30,display_name:character varying:NO:160,external_account_id:character varying:NO:180,status:character varying:NO:20,secret_ref:character varying:NO:180,created_at:timestamp with time zone:NO:-,updated_at:timestamp with time zone:NO:-,team_id:uuid:YES:-,default_assignee_user_id:uuid:YES:-' THEN RAISE EXCEPTION 'channel_accounts V13/V14 schema mismatch'; END IF;
  IF (SELECT string_agg(column_name || ':' || data_type || ':' || is_nullable || ':' || coalesce(character_maximum_length::text,'-'), ',' ORDER BY ordinal_position) FROM information_schema.columns WHERE table_schema='public' AND table_name='conversations') <> 'id:uuid:NO:-,organization_id:uuid:NO:-,channel_account_id:uuid:NO:-,external_thread_id:character varying:NO:180,lead_id:uuid:YES:-,customer_id:uuid:YES:-,assigned_user_id:uuid:YES:-,status:character varying:NO:20,consent_status:character varying:NO:30,last_inbound_at:timestamp with time zone:YES:-,last_message_at:timestamp with time zone:YES:-,created_at:timestamp with time zone:NO:-,updated_at:timestamp with time zone:NO:-' THEN RAISE EXCEPTION 'conversations V13 schema mismatch'; END IF;
  IF (SELECT string_agg(column_name || ':' || data_type || ':' || is_nullable || ':' || coalesce(character_maximum_length::text,'-'), ',' ORDER BY ordinal_position) FROM information_schema.columns WHERE table_schema='public' AND table_name='conversation_messages') <> 'id:uuid:NO:-,organization_id:uuid:NO:-,conversation_id:uuid:NO:-,direction:character varying:NO:20,external_message_id:character varying:NO:180,content_type:character varying:NO:40,content:text:NO:-,sent_by_user_id:uuid:YES:-,delivery_status:character varying:NO:30,occurred_at:timestamp with time zone:NO:-,interaction_id:uuid:YES:-,created_at:timestamp with time zone:NO:-' THEN RAISE EXCEPTION 'conversation_messages V13 schema mismatch'; END IF;
END $sqlDollarQuote;
CREATE TEMP TABLE w13_accounts (LIKE public.channel_accounts INCLUDING DEFAULTS) ON COMMIT DROP;
CREATE TEMP TABLE w13_conversations (LIKE public.conversations INCLUDING DEFAULTS) ON COMMIT DROP;
CREATE TEMP TABLE w13_messages (LIKE public.conversation_messages INCLUDING DEFAULTS) ON COMMIT DROP;
\copy w13_accounts (id,organization_id,channel_type,display_name,external_account_id,status,secret_ref,team_id,default_assignee_user_id,created_at,updated_at) FROM '$accounts' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')
\copy w13_conversations (id,organization_id,channel_account_id,external_thread_id,lead_id,customer_id,assigned_user_id,status,consent_status,last_inbound_at,last_message_at,created_at,updated_at) FROM '$conversations' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8', FORCE_NULL (lead_id,customer_id,assigned_user_id,last_inbound_at,last_message_at))
\copy w13_messages (id,organization_id,conversation_id,direction,external_message_id,content_type,content,sent_by_user_id,delivery_status,occurred_at,interaction_id,created_at) FROM '$messages' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8', FORCE_NULL (sent_by_user_id,interaction_id))
DO $sqlDollarQuote
DECLARE loaded integer;
BEGIN
  IF (SELECT count(*) FROM w13_accounts) <> 6 OR (SELECT count(*) FROM w13_conversations) <> 300 OR (SELECT count(*) FROM w13_messages) <> 1927 THEN RAISE EXCEPTION 'W13 stage count mismatch'; END IF;
  IF EXISTS (SELECT id FROM w13_accounts GROUP BY id HAVING count(*) > 1) OR EXISTS (SELECT id FROM w13_conversations GROUP BY id HAVING count(*) > 1) OR EXISTS (SELECT id FROM w13_messages GROUP BY id HAVING count(*) > 1) THEN RAISE EXCEPTION 'W13 duplicate UUID'; END IF;
  IF EXISTS (SELECT 1 FROM w13_accounts a LEFT JOIN organizations o ON o.id=a.organization_id LEFT JOIN teams t ON t.id=a.team_id AND t.organization_id=a.organization_id LEFT JOIN users u ON u.id=a.default_assignee_user_id AND u.organization_id=a.organization_id WHERE o.id IS NULL OR t.id IS NULL OR u.id IS NULL OR a.channel_type <> 'ZALO_SIM' OR a.status <> 'ACTIVE' OR NOT EXISTS (SELECT 1 FROM team_memberships m WHERE m.organization_id=a.organization_id AND m.team_id=a.team_id AND m.user_id=a.default_assignee_user_id AND m.status='ACTIVE' AND m.left_at IS NULL)) THEN RAISE EXCEPTION 'W13 channel account FK or tenant mismatch'; END IF;
  IF EXISTS (SELECT 1 FROM w13_conversations c LEFT JOIN w13_accounts a ON a.id=c.channel_account_id AND a.organization_id=c.organization_id LEFT JOIN users u ON u.id=c.assigned_user_id AND u.organization_id=c.organization_id LEFT JOIN customers cu ON cu.id=c.customer_id AND cu.organization_id=c.organization_id LEFT JOIN leads l ON l.id=c.lead_id AND l.organization_id=c.organization_id WHERE a.id IS NULL OR u.id IS NULL OR (c.customer_id IS NULL) = (c.lead_id IS NULL) OR (c.customer_id IS NOT NULL AND (cu.id IS NULL OR cu.status <> 'ACTIVE')) OR (c.lead_id IS NOT NULL AND (l.id IS NULL OR l.status NOT IN ('NEW','QUALIFIED'))) OR c.assigned_user_id <> a.default_assignee_user_id) THEN RAISE EXCEPTION 'W13 conversation FK or tenant mismatch'; END IF;
  IF EXISTS (SELECT 1 FROM w13_messages m LEFT JOIN w13_conversations c ON c.id=m.conversation_id AND c.organization_id=m.organization_id LEFT JOIN users u ON u.id=m.sent_by_user_id AND u.organization_id=m.organization_id WHERE c.id IS NULL OR btrim(m.content)='' OR position(chr(65533) IN m.content)>0 OR (m.direction='INBOUND' AND (m.sent_by_user_id IS NOT NULL OR m.delivery_status <> 'RECEIVED')) OR (m.direction='OUTBOUND' AND (m.sent_by_user_id <> c.assigned_user_id OR m.delivery_status <> 'SENT')) OR (m.direction NOT IN ('INBOUND','OUTBOUND'))) THEN RAISE EXCEPTION 'W13 message FK, Unicode, or direction mismatch'; END IF;
  SELECT count(*) INTO loaded FROM public.channel_accounts;
  IF loaded NOT IN (0,6) OR (SELECT count(*) FROM public.conversations) NOT IN (0,300) OR (SELECT count(*) FROM public.conversation_messages) NOT IN (0,1927) THEN RAISE EXCEPTION 'W13 partial or foreign dataset state'; END IF;
  IF loaded=6 AND (EXISTS (SELECT 1 FROM w13_accounts s LEFT JOIN public.channel_accounts d ON d.id=s.id WHERE d.id IS NULL OR ROW(d.organization_id,d.channel_type,d.display_name,d.external_account_id,d.status,d.secret_ref,d.team_id,d.default_assignee_user_id,d.created_at,d.updated_at) IS DISTINCT FROM ROW(s.organization_id,s.channel_type,s.display_name,s.external_account_id,s.status,s.secret_ref,s.team_id,s.default_assignee_user_id,s.created_at,s.updated_at)) OR EXISTS (SELECT 1 FROM w13_conversations s LEFT JOIN public.conversations d ON d.id=s.id WHERE d.id IS NULL OR ROW(d.organization_id,d.channel_account_id,d.external_thread_id,d.lead_id,d.customer_id,d.assigned_user_id,d.status,d.consent_status,d.last_inbound_at,d.last_message_at,d.created_at,d.updated_at) IS DISTINCT FROM ROW(s.organization_id,s.channel_account_id,s.external_thread_id,s.lead_id,s.customer_id,s.assigned_user_id,s.status,s.consent_status,s.last_inbound_at,s.last_message_at,s.created_at,s.updated_at)) OR EXISTS (SELECT 1 FROM w13_messages s LEFT JOIN public.conversation_messages d ON d.id=s.id WHERE d.id IS NULL OR ROW(d.organization_id,d.conversation_id,d.direction,d.external_message_id,d.content_type,d.content,d.sent_by_user_id,d.delivery_status,d.occurred_at,d.interaction_id,d.created_at) IS DISTINCT FROM ROW(s.organization_id,s.conversation_id,s.direction,s.external_message_id,s.content_type,s.content,s.sent_by_user_id,s.delivery_status,s.occurred_at,s.interaction_id,s.created_at))) THEN RAISE EXCEPTION 'W13 rerun data mismatch'; END IF;
  IF loaded=0 THEN INSERT INTO public.channel_accounts (id,organization_id,channel_type,display_name,external_account_id,status,secret_ref,team_id,default_assignee_user_id,created_at,updated_at) SELECT id,organization_id,channel_type,display_name,external_account_id,status,secret_ref,team_id,default_assignee_user_id,created_at,updated_at FROM w13_accounts; INSERT INTO public.conversations (id,organization_id,channel_account_id,external_thread_id,lead_id,customer_id,assigned_user_id,status,consent_status,last_inbound_at,last_message_at,created_at,updated_at) SELECT id,organization_id,channel_account_id,external_thread_id,lead_id,customer_id,assigned_user_id,status,consent_status,last_inbound_at,last_message_at,created_at,updated_at FROM w13_conversations; INSERT INTO public.conversation_messages (id,organization_id,conversation_id,direction,external_message_id,content_type,content,sent_by_user_id,delivery_status,occurred_at,interaction_id,created_at) SELECT id,organization_id,conversation_id,direction,external_message_id,content_type,content,sent_by_user_id,delivery_status,occurred_at,interaction_id,created_at FROM w13_messages; END IF;
END $sqlDollarQuote;
COMMIT;
SELECT 'W13_INTEGRATION_LOAD_PASS|accounts=6|conversations=300|messages=1927';
"@
$temporaryScript = Join-Path $env:TEMP ('pose_w12_w13_w13_' + [Guid]::NewGuid().ToString('N') + '.psql')
[IO.File]::WriteAllText($temporaryScript, $sql, [Text.UTF8Encoding]::new($false))
try { & $PsqlPath -X -w -h $targetHost -p $Port -U $postgresUser -d $targetDatabase -f $temporaryScript; if ($LASTEXITCODE -ne 0) { throw 'W13 integration load failed; PostgreSQL rolled back the transaction.' } } finally { Remove-Item -LiteralPath $temporaryScript -Force -ErrorAction SilentlyContinue }
