[CmdletBinding()]
param(
    [int]$Port = 54329,
    [string]$DatabaseName = 'pose_w12_w13_integration_test'
)

$ErrorActionPreference = 'Stop'
$CanonicalCommit = 'd42bde9498179a18acda7ee0edfd77e65f5d49f6'
$V15Hash = 'A06ECDB0993E36EF8DAFCF98F5833D1485482C04CCA95022D7C5F2BC49807B10'
$HostName = '127.0.0.1'; $DbUser = 'pose_auth_test'; $ExpectedDb = 'pose_w12_w13_integration_test'
$PsqlPath = 'C:\Program Files\PostgreSQL\18\bin\psql.exe'
$Expected = @'
1|V1__nhi_v2_schema.sql|8A6AAFF775445919FF18C05DDEE3731F229AEFB530C825314B6EB83F971F804F
2|V2__auth_sessions.sql|D1D64DB83F4210F16DB7F4E9754C9EAAC78F9E062348FDD4864223DB8381BCC1
3|V3__organization_login_code.sql|B5042291F9DD9FF19DEF737790C713CD00FEF4EE8E199A3F838F99DE88B49B9B
4|V4__organization_demo_login_codes.sql|93BBE04C41F29FB9AE7DFDF54F6B1F8A91F646C21092EE66C8DDFE831F62A97B
5|V5__offering_team_scope_and_sales_read.sql|DA92581FBA316A41118F6BC89DEECBC816D74F82C865520183A0926F9273F425
6|V6__sales_assignment_management_permission.sql|DDD5EA8A6B933B076F636B5A6774FCD909DB602D4B976ACB218302D932D714FC
7|V7__system_audit_actor.sql|63BF6911D50591F9604B00AA0CE007245A71FCC0A0E0F87C5EB386E8B10008E5
8|V8__assign_legacy_offerings_to_sales_team.sql|F25633CD395C3CBFAE01145FFE2295BF2EA7096175FFB5451B6BBF633CF05FB3
9|V9__customer_lead_profile_fields.sql|A5C1318D18476B9D720ABB33E08C67CE1FF688927E0A4CF483C53C611132B877
10|V10__opportunity_lines_and_closing.sql|9BE5D964FE9875748CB7DDF5860DE526AD7490B8300FA7101099603E2C0BA171
11|V11__interaction_task_audit_core.sql|CDB569118FAEFC000E3AC3E4ED4E6DF71111792BB8837A51402820E0A2C806F1
12|V12__ai_gateway.sql|D2A21663AC07A91761082D4294069C7CFF51D55D0141FAE2CBB0045AEE2A1272
13|V13__conversations.sql|D3FD1AD777B2A3C2319630AF05BC4E7B3493A6FBD2BAB98C35AA2A8A748DB115
14|V14__channel_account_ownership.sql|02625E77FCCB37794718C2DFA9E555F953619BC567A0CF528B109FF2B42B8DDC
'@ -split "`r?`n" | Where-Object { $_ }
$WorktreeRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$CanonicalBaseline = Join-Path $WorktreeRoot 'backend\src\main\resources\db\baseline'
$CanonicalMigration = Join-Path $WorktreeRoot 'backend\src\main\resources\db\migration'
$CanonicalV15 = Join-Path $CanonicalMigration 'V15__customer_signals.sql'

function Fail([string]$Message) { Write-Error "W15_AI07_VERIFY_FAIL=$Message"; exit 1 }
function Hash([string]$Path) { if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { Fail "missing_file=$Path" }; (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToUpperInvariant() }
function CanonicalMigrationPath([string]$Version, [string]$Name) { if ($Version -eq '1') { return Join-Path $CanonicalBaseline $Name }; return Join-Path $CanonicalMigration $Name }
function Get-TrustedCommand([string]$Name) { $command = Get-Command $Name -ErrorAction SilentlyContinue; if (-not $command -or $command.CommandType -ne [System.Management.Automation.CommandTypes]::Application -or -not $command.Source) { Fail "$Name`_prerequisite_missing_or_not_application" }; return $command.Source }
function Get-TrustedFlyway { $flyway = Get-TrustedCommand 'flyway'; $version = & $flyway '-v' 2>&1; if ($LASTEXITCODE -ne 0 -or (($version -join "`n") -notmatch '(?i)flyway')) { Fail 'flyway_version_validation_failed' }; return $flyway }
function Psql([string]$Sql) { $result = & $PsqlPath -X -q -t -A -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $DbUser -d $DatabaseName -c $Sql; if ($LASTEXITCODE -ne 0) { Fail 'psql_query_failed' }; @($result | ForEach-Object { $_.Trim() } | Where-Object { $_ }) }
function Assert-StaticContract {
    if ($Port -ne 54329) { Fail "port_must_be_54329 actual=$Port" }
    if ($DatabaseName -cne $ExpectedDb) { Fail "database_must_be_$ExpectedDb actual=$DatabaseName" }
    if (-not (Test-Path -LiteralPath $PsqlPath -PathType Leaf)) { Fail "missing_psql=$PsqlPath" }
    if (-not (Test-Path -LiteralPath $WorktreeRoot -PathType Container)) { Fail "worktree_unavailable=$WorktreeRoot" }
    $git = Get-TrustedCommand 'git'; & $git -C $WorktreeRoot merge-base --is-ancestor $CanonicalCommit HEAD
    if ($LASTEXITCODE -ne 0) { Fail "canonical_commit_not_ancestor=$CanonicalCommit" }
    foreach ($row in $Expected) {
        $part = $row.Split('|'); $hash = Hash (CanonicalMigrationPath $part[0] $part[1])
        if ($hash -cne $part[2]) { Fail "canonical_migration_hash_mismatch=$($part[1]) actual=$hash expected=$($part[2])" }
    }
    if ((Hash $CanonicalV15) -cne $V15Hash) { Fail 'canonical_v15_hash_mismatch' }
    Write-Host "W15_CANONICAL_V15_SOURCE_SHA256=PASS sha256=$V15Hash"
}
function Assert-DatabaseContract {
    if ((Psql 'SHOW data_directory;') -cne (Join-Path $WorktreeRoot 'backend\target\auth-postgres-test')) { Fail 'unexpected_data_directory' }
    if ((Psql "SELECT current_database() || '|' || inet_server_addr()::text || '|' || current_setting('server_version_num');") -notmatch '^pose_w12_w13_integration_test[|]127\.0\.0\.1[|]18') { Fail 'unexpected_database_identity' }
}
function Assert-FlywayHistory {
    $actual = @(Psql "SELECT version || '|' || script FROM flyway_schema_history WHERE success ORDER BY installed_rank;")
    $expected = @($Expected | ForEach-Object { ($_.Split('|')[0..1] -join '|') }) + @('15|V15__customer_signals.sql')
    if (($actual -join "`n") -cne ($expected -join "`n")) { Fail "unexpected_flyway_history=$($actual -join ',')" }
}
function Assert-CanonicalFlywayValidation {
    $flyway = Get-TrustedFlyway
    & $flyway "-url=jdbc:postgresql://$HostName`:$Port/$DatabaseName" "-user=$DbUser" "-locations=filesystem:$CanonicalBaseline,filesystem:$CanonicalMigration" validate
    if ($LASTEXITCODE -ne 0) { Fail 'flyway_canonical_validation_failed' }
    Write-Host "W15_FLYWAY_CANONICAL_VALIDATION=PASS v15_sha256=$V15Hash"
}
function Assert-BaselineAndV15Schema {
    $sql = @'
BEGIN READ ONLY;
DO $$
DECLARE item TEXT;
BEGIN
    IF (SELECT count(*) FROM organizations) <> 6 THEN RAISE EXCEPTION 'organization_count'; END IF;
    IF (SELECT count(*) FROM conversations) <> 300 THEN RAISE EXCEPTION 'conversation_count'; END IF;
    IF (SELECT count(*) FROM conversation_messages) <> 1927 THEN RAISE EXCEPTION 'message_count'; END IF;
    IF to_regclass('public.customer_signal_extractions') IS NULL OR to_regclass('public.customer_signals') IS NULL THEN RAISE EXCEPTION 'missing_v15_table'; END IF;
    FOREACH item IN ARRAY ARRAY['customer_signal_extractions.organization_id','customer_signal_extractions.message_id','customer_signal_extractions.invocation_id','customer_signal_extractions.status','customer_signal_extractions.success_message_id','customer_signals.organization_id','customer_signals.lead_id','customer_signals.customer_id','customer_signals.message_id','customer_signals.signal_type','customer_signals.value','customer_signals.confidence','customer_signals.evidence_text','customer_signals.source','customer_signals.invocation_id','customer_signals.status','customer_signals.reviewed_by_user_id','customer_signals.reviewed_at'] LOOP
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name=split_part(item,'.',1) AND column_name=split_part(item,'.',2)) THEN RAISE EXCEPTION 'missing_column:%', item; END IF;
    END LOOP;
    IF (SELECT is_nullable FROM information_schema.columns WHERE table_schema='public' AND table_name='ai_invocations' AND column_name='actor_user_id') <> 'NO' THEN RAISE EXCEPTION 'actor_user_id_must_be_not_null'; END IF;
    FOREACH item IN ARRAY ARRAY['uq_conversation_messages_org_id','ck_signal_extraction_success','uq_signal_extraction_success','fk_signal_extraction_message','fk_signal_extraction_invocation','ck_customer_signals_subject','ck_customer_signals_ai_key','ck_customer_signals_ai_invocation','uq_customer_signals_ai','uq_customer_signals_human','fk_customer_signals_lead','fk_customer_signals_customer','fk_customer_signals_message','fk_customer_signals_invocation','fk_customer_signals_reviewer'] LOOP
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname=item) THEN RAISE EXCEPTION 'missing_constraint:%', item; END IF;
    END LOOP;
    FOREACH item IN ARRAY ARRAY['idx_signal_extractions_message','idx_customer_signals_customer','idx_customer_signals_lead'] LOOP
        IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='public' AND indexname=item) THEN RAISE EXCEPTION 'missing_index:%', item; END IF;
    END LOOP;
END $$;
COMMIT;
'@
    & $PsqlPath -X -q -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $DbUser -d $DatabaseName -c $sql
    if ($LASTEXITCODE -ne 0) { Fail 'baseline_or_v15_schema_validation_failed' }
}

Assert-StaticContract
Assert-DatabaseContract
Assert-FlywayHistory
Assert-CanonicalFlywayValidation
Assert-BaselineAndV15Schema
Write-Output "W15_AI07_INTEGRATION_VERIFIED|canonical_ancestor=$CanonicalCommit|v15_sha256=$V15Hash"
