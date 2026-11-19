param(
    [string]$JdkDirectory = 'C:\Program Files\Java\jdk-25.0.2',
    [string]$PgBin = 'C:\Program Files\PostgreSQL\18\bin',
    [string]$MavenPath = 'C:\Users\Dell\.maven\maven-3.9.15\bin\mvn.cmd',
    [int]$Warmup = 5,
    [int]$Iterations = 35,
    [switch]$Build,
    [switch]$Scale,
    [string]$SourceDatabase = ''
)
$ErrorActionPreference = 'Stop'
if ($Iterations -lt 30) { throw 'Iterations must be at least 30.' }

$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$cluster = [IO.Path]::GetFullPath((Join-Path $repo 'backend\target\auth-postgres-test'))
$pgCtl = Join-Path $PgBin 'pg_ctl.exe'
$psql = Join-Path $PgBin 'psql.exe'
$dbArgs = @('-w', '-h', '127.0.0.1', '-p', '54329', '-U', 'pose_auth_test')

foreach ($port in @(8081, 54329)) {
    if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
        throw "Port $port is already listening; refusing to modify an existing service."
    }
}
if (-not (Test-Path -LiteralPath (Join-Path $cluster 'PG_VERSION'))) {
    throw 'Dedicated PostgreSQL cluster is missing; run backend/scripts/test-postgres-v2.ps1 first.'
}
if (Test-Path -LiteralPath (Join-Path $cluster 'postmaster.pid')) {
    throw 'PostgreSQL PID file exists; inspect ownership before starting.'
}

$jar = Join-Path $repo 'backend\target\pose-backend-0.1.0-SNAPSHOT.jar'
$crypto = Join-Path $env:USERPROFILE '.m2\repository\org\springframework\security\spring-security-crypto\6.5.11\spring-security-crypto-6.5.11.jar'

if ($Build) {
    $prevJava = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Process')
    try {
        $env:JAVA_HOME = $JdkDirectory
        Push-Location $repo
        try {
            & $MavenPath -pl backend -am package '-DskipTests'
            if ($LASTEXITCODE -ne 0) { throw 'Backend package failed.' }
        } finally { Pop-Location }
    } finally { [Environment]::SetEnvironmentVariable('JAVA_HOME', $prevJava, 'Process') }
}

foreach ($path in @($jar, $crypto, (Join-Path $JdkDirectory 'bin\java.exe'))) {
    if (-not (Test-Path -LiteralPath $path)) { throw "Required artifact missing: $path" }
}

$temp = Join-Path ([IO.Path]::GetTempPath()) ('pose-w17-perf-' + [guid]::NewGuid().ToString('N'))
$db = 'pose_w17_perf_' + [DateTime]::UtcNow.ToString('yyyyMMddHHmmssfff') + '_' + (Get-Random -Minimum 1000 -Maximum 9999)
$started = $false; $verified = $false; $created = $false; $backend = $null

$clearNames = @(Get-ChildItem Env: | Where-Object { $_.Name -like 'PG*' -or $_.Name -like 'POSE_*' -or $_.Name -like 'SPRING_*' } | ForEach-Object { $_.Name })
$saved = @{}
foreach ($name in ($clearNames + @('JAVA_HOME', 'PORT') | Select-Object -Unique)) {
    $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

try {
    foreach ($name in $clearNames) { [Environment]::SetEnvironmentVariable($name, $null, 'Process') }
    New-Item -ItemType Directory -Path $temp | Out-Null

    & $pgCtl -D $cluster -l (Join-Path $temp 'postgres.log') -o '-p 54329 -h 127.0.0.1' -w -t 20 start
    if ($LASTEXITCODE -ne 0) { throw 'Cannot start dedicated PostgreSQL cluster.' }
    $started = $true

    $actual = (& $psql @dbArgs -d postgres -Atc 'SHOW data_directory').Trim()
    if ($LASTEXITCODE -ne 0 -or $actual.Replace('/','\') -ine $cluster) {
        throw "Unexpected PostgreSQL data_directory: $actual"
    }
    $verified = $true
    Write-Host "mode=$(if ($Scale) { 'SCALE' } else { 'DEMO' })"
    Write-Host "data_directory=$actual"

    if ($SourceDatabase) {
        if ($SourceDatabase -notmatch '^pose_v2_review_[0-9]+$') { throw 'Invalid SourceDatabase name.' }
        $source = $SourceDatabase
    } else {
        $source = (& $psql @dbArgs -d postgres -Atc "SELECT datname FROM pg_database WHERE datname ~ '^pose_v2_review_[0-9]+$' AND datallowconn ORDER BY datname DESC LIMIT 1").Trim()
        if ($LASTEXITCODE -ne 0 -or $source -notmatch '^pose_v2_review_[0-9]+$') {
            throw 'No eligible pose_v2_review database in the dedicated cluster.'
        }
    }
    Write-Host "source_database=$source"
    & (Join-Path $PgBin 'createdb.exe') @dbArgs -T $source $db
    if ($LASTEXITCODE -ne 0) { throw 'Cannot clone review database.' }
    $created = $true

    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes); $benchPassword = [Convert]::ToBase64String($bytes)
        $rng.GetBytes($bytes); $jwtSecret = [Convert]::ToBase64String($bytes)
        $rng.GetBytes($bytes); $zaloSecret = [BitConverter]::ToString($bytes).Replace('-', '')
    } finally { $rng.Dispose() }

    $env:POSE_BENCH_PASSWORD = $benchPassword
    $javaSource = 'import org.springframework.security.crypto.bcrypt.BCrypt; public class BenchHash { public static void main(String[] a) { System.out.print(BCrypt.hashpw(System.getenv("POSE_BENCH_PASSWORD"), BCrypt.gensalt(12))); } }'
    [IO.File]::WriteAllText((Join-Path $temp 'BenchHash.java'), $javaSource)
    & (Join-Path $JdkDirectory 'bin\javac.exe') -cp $crypto (Join-Path $temp 'BenchHash.java')
    if ($LASTEXITCODE -ne 0) { throw 'Cannot compile BCrypt helper.' }
    $hash = & (Join-Path $JdkDirectory 'bin\java.exe') -cp ($temp + ';' + $crypto) BenchHash
    if ($LASTEXITCODE -ne 0 -or $hash -notmatch '^\$2[aby]\$12\$') { throw 'Cannot generate BCrypt hash.' }

    $credSql = "UPDATE credentials SET password_hash='$hash' WHERE user_id IN (SELECT u.id FROM users u JOIN organizations o ON o.id=u.organization_id WHERE lower(o.code)='edu' AND lower(u.email) IN ('edu.admin@example.com','edu.mgr@example.com','edu.s01@example.com'));"
    $credSql | & $psql @dbArgs -d $db -v ON_ERROR_STOP=1 -q
    if ($LASTEXITCODE -ne 0) { throw 'Cannot update isolated benchmark credentials.' }

    # Prepare synthetic channel account + rich Customer 360 target (and full scale dataset when -Scale is set)
    $seedSql = @'
DO $$
DECLARE
    v_org uuid;
    v_team uuid;
    v_mgr uuid;
    v_s01 uuid;
    v_s02 uuid;
    v_off1 uuid;
    v_off2 uuid;
    v_off3 uuid;
    v_target_cust uuid;
    v_acc uuid := '11111111-1111-4111-8111-111111111101';
    v_target_conv uuid := '11111111-1111-4111-8111-111111111102';
    v_target_msg uuid := '11111111-1111-4111-8111-111111111103';
    v_now timestamptz := TIMESTAMPTZ '2026-10-10 10:00:00+00';
    v_scale boolean := '__SCALE_FLAG__' = 'true';
    v_offs uuid[];
    v_custs uuid[];
    v_leads uuid[];
    v_opps uuid[];
    v_convs uuid[];
    v_i int;
BEGIN
    SELECT id INTO v_org FROM organizations WHERE lower(code) = 'edu';
    SELECT id INTO v_team FROM teams WHERE organization_id = v_org AND status = 'ACTIVE' ORDER BY code LIMIT 1;
    SELECT id INTO v_mgr FROM users WHERE organization_id = v_org AND lower(email) = 'edu.mgr@example.com';
    SELECT id INTO v_s01 FROM users WHERE organization_id = v_org AND lower(email) = 'edu.s01@example.com';
    SELECT id INTO v_s02 FROM users WHERE organization_id = v_org AND lower(email) = 'edu.s02@example.com';

    SELECT array_agg(id ORDER BY code) INTO v_offs
    FROM offerings WHERE organization_id = v_org AND status = 'ACTIVE';
    v_off1 := v_offs[1];
    v_off2 := COALESCE(v_offs[2], v_offs[1]);
    v_off3 := COALESCE(v_offs[3], v_offs[1]);

    -- Ensure s01 has an active sales_assignment on v_off1
    IF NOT EXISTS (
        SELECT 1 FROM sales_assignments
        WHERE organization_id = v_org AND offering_id = v_off1 AND sales_user_id = v_s01
          AND status = 'ACTIVE' AND revoked_at IS NULL
    ) THEN
        INSERT INTO sales_assignments(id, organization_id, offering_id, sales_user_id, assigned_by_user_id, status, effective_from, created_at, updated_at)
        VALUES (gen_random_uuid(), v_org, v_off1, v_s01, v_mgr, 'ACTIVE', v_now - interval '60 days', v_now, v_now);
    END IF;

    -- Pick a target Customer owned by s01 that already has seed interactions/opportunities/tasks, or enrich one
    SELECT c.id INTO v_target_cust
    FROM customers c
    WHERE c.organization_id = v_org AND c.owner_user_id = v_s01 AND c.archived_at IS NULL
    ORDER BY (SELECT count(*) FROM interactions i WHERE i.customer_id = c.id) DESC,
             (SELECT count(*) FROM opportunities op WHERE op.customer_id = c.id) DESC,
             c.id
    LIMIT 1;

    UPDATE customers SET related_offering_id = COALESCE(related_offering_id, v_off1), updated_at = v_now
    WHERE organization_id = v_org AND id = v_target_cust;

    -- Guarantee target Customer has at least 1 Opportunity, 1 Interaction, 1 Task
    IF NOT EXISTS (SELECT 1 FROM opportunities WHERE organization_id = v_org AND customer_id = v_target_cust) THEN
        INSERT INTO opportunities(id, organization_id, customer_id, offering_id, owner_user_id, title, stage, expected_value, currency_code, expected_close_date, created_at, updated_at)
        VALUES ('11111111-1111-4111-8111-111111111104', v_org, v_target_cust, v_off1, v_s01, 'W17 Target Opportunity', 'QUALIFIED', 15000000, 'VND', DATE '2026-12-01', v_now, v_now);
        INSERT INTO opportunity_lines(id, organization_id, opportunity_id, offering_id, position, quantity, unit_price, discount_amount, line_total, created_at, updated_at)
        VALUES (gen_random_uuid(), v_org, '11111111-1111-4111-8111-111111111104', v_off1, 1, 1, 15000000, 0, 15000000, v_now, v_now);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM crm_tasks WHERE organization_id = v_org AND customer_id = v_target_cust) THEN
        INSERT INTO crm_tasks(id, organization_id, customer_id, owner_user_id, created_by_user_id, title, priority, status, due_at, origin, created_at, updated_at)
        VALUES (gen_random_uuid(), v_org, v_target_cust, v_s01, v_s01, 'W17 Target Task', 'MEDIUM', 'OPEN', v_now + interval '2 days', 'MANUAL', v_now, v_now);
    END IF;

    -- Create ZALO_SIM channel account for EDU SALES team
    INSERT INTO channel_accounts(id, organization_id, channel_type, display_name, external_account_id, team_id, default_assignee_user_id, status, secret_ref, created_at, updated_at)
    VALUES (v_acc, v_org, 'ZALO_SIM', 'Zalo Sim W17 Bench', 'zalo-w17-bench-edu', v_team, v_s01, 'ACTIVE', 'POSE_CHANNEL_ZALO_SIM_WEBHOOK_SECRET', v_now, v_now);

    -- Create target conversation linked to target Customer and assigned to s01, with inbound messages + customer signal
    INSERT INTO conversations(id, organization_id, channel_account_id, external_thread_id, assigned_user_id, customer_id, status, consent_status, last_inbound_at, last_message_at, created_at, updated_at)
    VALUES (v_target_conv, v_org, v_acc, 'bench-thread-target', v_s01, v_target_cust, 'OPEN', 'UNKNOWN', v_now, v_now, v_now - interval '5 days', v_now);

    INSERT INTO conversation_messages(id, organization_id, conversation_id, direction, external_message_id, content_type, content, delivery_status, occurred_at, created_at)
    VALUES (v_target_msg, v_org, v_target_conv, 'INBOUND', 'seed-target-1', 'TEXT', 'Synthetic inquiry for Customer 360 benchmark', 'RECEIVED', v_now, v_now);

    INSERT INTO customer_signals(id, organization_id, customer_id, message_id, signal_type, "value", confidence, evidence_text, source, human_message_id, status, reviewed_by_user_id, reviewed_at, created_at)
    VALUES (gen_random_uuid(), v_org, v_target_cust, v_target_msg, 'INTENT', 'ASK_PRICE', 0.9500, 'Synthetic inquiry for Customer 360 benchmark', 'HUMAN', v_target_msg, 'ACCEPTED', v_mgr, v_now, v_now);

    -- Additional demo conversations/messages when not in scale mode
    IF NOT v_scale THEN
        INSERT INTO conversation_messages(id, organization_id, conversation_id, direction, external_message_id, content_type, content, delivery_status, occurred_at, created_at)
        SELECT gen_random_uuid(), v_org, v_target_conv,
               CASE WHEN g % 2 = 0 THEN 'OUTBOUND' ELSE 'INBOUND' END,
               'seed-target-' || (g + 1), 'TEXT', 'Synthetic demo message #' || g,
               CASE WHEN g % 2 = 0 THEN 'SENT' ELSE 'RECEIVED' END,
               v_now - ((10 - g) || ' minutes')::interval, v_now
        FROM generate_series(1, 5) g;

        INSERT INTO conversations(id, organization_id, channel_account_id, external_thread_id, assigned_user_id, status, consent_status, last_inbound_at, last_message_at, created_at, updated_at)
        VALUES
            ('11111111-1111-4111-8111-111111111105', v_org, v_acc, 'bench-thread-2', v_s02, 'OPEN', 'UNKNOWN', v_now - interval '1 hour', v_now - interval '1 hour', v_now - interval '2 days', v_now - interval '1 hour'),
            ('11111111-1111-4111-8111-111111111106', v_org, v_acc, 'bench-thread-3', v_mgr, 'OPEN', 'UNKNOWN', v_now - interval '2 hours', v_now - interval '2 hours', v_now - interval '2 days', v_now - interval '2 hours');

        INSERT INTO conversation_messages(id, organization_id, conversation_id, direction, external_message_id, content_type, content, delivery_status, occurred_at, created_at)
        VALUES
            (gen_random_uuid(), v_org, '11111111-1111-4111-8111-111111111105', 'INBOUND', 'seed-2-1', 'TEXT', 'Synthetic demo message 2-1', 'RECEIVED', v_now - interval '1 hour', v_now),
            (gen_random_uuid(), v_org, '11111111-1111-4111-8111-111111111106', 'INBOUND', 'seed-3-1', 'TEXT', 'Synthetic demo message 3-1', 'RECEIVED', v_now - interval '2 hours', v_now);
    ELSE
        -- SCALE MODE: synthetic EDU dataset via generate_series
        -- 1) 5,000 Leads
        INSERT INTO leads(id, organization_id, owner_user_id, offering_id, display_name, email, normalized_email, phone, normalized_phone, status, source, notes, qualified_at, disqualification_reason, created_at, updated_at)
        SELECT
            gen_random_uuid(),
            v_org,
            CASE WHEN g % 2 = 0 THEN v_s01 ELSE v_s02 END,
            v_offs[1 + (g % array_length(v_offs, 1))],
            'Synthetic Lead ' || g,
            'w17s.lead.' || g || '@example.com',
            'w17s.lead.' || g || '@example.com',
            '098' || lpad(g::text, 7, '0'),
            '+8498' || lpad(g::text, 7, '0'),
            CASE WHEN g % 5 = 0 THEN 'QUALIFIED' WHEN g % 11 = 0 THEN 'DISQUALIFIED' ELSE 'NEW' END,
            'SOCIAL_MEDIA',
            'Synthetic scale lead',
            CASE WHEN g % 5 = 0 THEN v_now - ((g % 30) || ' days')::interval ELSE NULL END,
            CASE WHEN g % 5 <> 0 AND g % 11 = 0 THEN 'Synthetic disqualified reason' ELSE NULL END,
            v_now - ((g % 45) || ' days')::interval - ((g % 600) || ' seconds')::interval,
            v_now - ((g % 30) || ' days')::interval - ((g % 600) || ' seconds')::interval
        FROM generate_series(1, 5000) g;

        -- 2) 5,000 Customers + active SALES assignments
        WITH inserted_custs AS (
            INSERT INTO customers(id, organization_id, owner_user_id, related_offering_id, display_name, customer_type, email, normalized_email, phone, normalized_phone, status, created_at, updated_at)
            SELECT
                gen_random_uuid(),
                v_org,
                CASE WHEN g % 2 = 0 THEN v_s01 ELSE v_s02 END,
                v_offs[1 + (g % array_length(v_offs, 1))],
                'Synthetic Customer ' || g,
                CASE WHEN g % 4 = 0 THEN 'BUSINESS' ELSE 'INDIVIDUAL' END,
                'w17s.cust.' || g || '@example.com',
                'w17s.cust.' || g || '@example.com',
                '097' || lpad(g::text, 7, '0'),
                '+8497' || lpad(g::text, 7, '0'),
                'ACTIVE',
                v_now - ((g % 60) || ' days')::interval - ((g % 600) || ' seconds')::interval,
                v_now - ((g % 30) || ' days')::interval - ((g % 600) || ' seconds')::interval
            FROM generate_series(1, 5000) g
            RETURNING id, owner_user_id, created_at
        )
        INSERT INTO customer_assignments(id, organization_id, customer_id, user_id, assignment_type, effective_from, status, assigned_by_user_id, source, created_at)
        SELECT gen_random_uuid(), v_org, id, owner_user_id, 'SALES', created_at, 'ACTIVE', v_mgr, 'MANUAL', created_at
        FROM inserted_custs;

        SELECT array_agg(id ORDER BY created_at DESC) INTO v_custs FROM customers WHERE organization_id = v_org AND archived_at IS NULL;
        SELECT array_agg(id ORDER BY created_at DESC) INTO v_leads FROM leads WHERE organization_id = v_org AND archived_at IS NULL;

        -- 3) 2,000 Opportunities with 1-3 lines each (~4,000 lines)
        WITH inserted_opps AS (
            INSERT INTO opportunities(id, organization_id, customer_id, offering_id, owner_user_id, title, stage, expected_value, currency_code, expected_close_date, final_value, actual_close_date, closing_reason, closed_at, created_at, updated_at)
            SELECT
                gen_random_uuid(),
                v_org,
                v_custs[1 + (g % array_length(v_custs, 1))],
                v_off1,
                CASE WHEN g % 2 = 0 THEN v_s01 ELSE v_s02 END,
                'Synthetic Opp ' || g,
                (ARRAY['NEW','QUALIFIED','PROPOSAL','WON','LOST'])[1 + (g % 5)],
                10000000 * (1 + (g % 3)),
                'VND',
                DATE '2026-12-15',
                CASE WHEN g % 5 = 3 THEN 10000000 * (1 + (g % 3)) ELSE NULL END,
                CASE WHEN g % 5 = 3 THEN DATE '2026-10-01' ELSE NULL END,
                CASE WHEN g % 5 = 4 THEN 'Synthetic lost reason' ELSE NULL END,
                CASE WHEN g % 5 IN (3, 4) THEN v_now - ((g % 20) || ' days')::interval ELSE NULL END,
                v_now - ((g % 45) || ' days')::interval,
                v_now - ((g % 25) || ' days')::interval - ((g % 600) || ' seconds')::interval
            FROM generate_series(1, 2000) g
            RETURNING id, created_at, updated_at
        ), numbered_opps AS (
            SELECT id, created_at, updated_at, row_number() OVER () AS rn FROM inserted_opps
        )
        INSERT INTO opportunity_lines(id, organization_id, opportunity_id, offering_id, position, quantity, unit_price, discount_amount, line_total, created_at, updated_at)
        SELECT
            gen_random_uuid(),
            v_org,
            o.id,
            CASE pos WHEN 1 THEN v_off1 WHEN 2 THEN v_off2 ELSE v_off3 END,
            pos,
            1,
            10000000,
            0,
            10000000,
            o.created_at,
            o.updated_at
        FROM numbered_opps o
        CROSS JOIN LATERAL generate_series(1, 1 + (o.rn % 3)) AS pos;

        SELECT array_agg(id ORDER BY created_at DESC) INTO v_opps FROM opportunities WHERE organization_id = v_org;

        -- 4) 20,000 Interactions
        INSERT INTO interactions(id, organization_id, customer_id, lead_id, opportunity_id, recorded_by_user_id, recorded_by_actor_type, type, channel, subject, content, visibility, occurred_at, created_at, updated_at)
        SELECT
            gen_random_uuid(),
            v_org,
            CASE WHEN g % 5 <> 0 THEN v_custs[1 + (g % array_length(v_custs, 1))] ELSE NULL END,
            CASE WHEN g % 5 = 0 THEN v_leads[1 + (g % array_length(v_leads, 1))] ELSE NULL END,
            CASE WHEN g % 4 = 0 THEN v_opps[1 + (g % array_length(v_opps, 1))] ELSE NULL END,
            CASE WHEN g % 2 = 0 THEN v_s01 ELSE v_s02 END,
            'USER',
            (ARRAY['CALL','EMAIL','MEETING'])[1 + (g % 3)],
            (ARRAY['PHONE','EMAIL','ZALO'])[1 + (g % 3)],
            'Synthetic interaction ' || g,
            'Synthetic interaction body ' || g,
            CASE WHEN g % 3 = 0 THEN 'SALES_ONLY' ELSE 'CARE_VISIBLE' END,
            v_now - ((g % 45) || ' days')::interval - ((g % 3600) || ' seconds')::interval,
            v_now - ((g % 45) || ' days')::interval,
            v_now - ((g % 45) || ' days')::interval
        FROM generate_series(1, 20000) g;

        -- 5) 3,000 Tasks
        INSERT INTO crm_tasks(id, organization_id, customer_id, lead_id, owner_user_id, created_by_user_id, created_by_actor_type, title, priority, status, due_at, origin, completed_at, created_at, updated_at)
        SELECT
            gen_random_uuid(),
            v_org,
            CASE WHEN g % 4 <> 0 THEN v_custs[1 + (g % array_length(v_custs, 1))] ELSE NULL END,
            CASE WHEN g % 4 = 0 THEN v_leads[1 + (g % array_length(v_leads, 1))] ELSE NULL END,
            CASE WHEN g % 2 = 0 THEN v_s01 ELSE v_s02 END,
            v_mgr,
            'USER',
            'Synthetic task ' || g,
            (ARRAY['LOW','MEDIUM','HIGH'])[1 + (g % 3)],
            (ARRAY['OPEN','IN_PROGRESS','DONE'])[1 + (g % 3)],
            v_now + (((g % 20) - 10) || ' days')::interval,
            'MANUAL',
            CASE WHEN g % 3 = 2 THEN v_now - ((g % 15) || ' days')::interval ELSE NULL END,
            v_now - ((g % 30) || ' days')::interval,
            v_now - ((g % 20) || ' days')::interval
        FROM generate_series(1, 3000) g;

        -- 6) 1,999 additional Conversations (total 2,000 with v_target_conv) + ~40,000 Messages (skewed: 5 heavy x 600 = 3,000; 1,995 normal x 19 = 37,905)
        -- Heavy #1 is v_target_conv (assigned to s01, linked to v_target_cust): add 599 more messages = 600 total
        INSERT INTO conversation_messages(id, organization_id, conversation_id, direction, external_message_id, content_type, content, delivery_status, occurred_at, created_at)
        SELECT
            gen_random_uuid(), v_org, v_target_conv,
            CASE WHEN m % 2 = 0 THEN 'OUTBOUND' ELSE 'INBOUND' END,
            'heavy-target-' || m, 'TEXT', 'Synthetic heavy message ' || m,
            CASE WHEN m % 2 = 0 THEN 'SENT' ELSE 'RECEIVED' END,
            v_now - ((600 - m) || ' minutes')::interval, v_now
        FROM generate_series(1, 599) m;

        WITH inserted_convs AS (
            INSERT INTO conversations(id, organization_id, channel_account_id, external_thread_id, assigned_user_id, customer_id, status, consent_status, last_inbound_at, last_message_at, created_at, updated_at)
            SELECT
                gen_random_uuid(),
                v_org,
                v_acc,
                'w17s-thread-' || g,
                CASE WHEN g % 2 = 0 THEN v_s01 ELSE v_s02 END,
                v_custs[1 + (g % array_length(v_custs, 1))],
                CASE WHEN g % 10 = 0 THEN 'CLOSED' ELSE 'OPEN' END,
                'UNKNOWN',
                v_now - ((g % 2000) || ' minutes')::interval,
                v_now - ((g % 2000) || ' minutes')::interval,
                v_now - interval '30 days',
                v_now - ((g % 2000) || ' minutes')::interval
            FROM generate_series(1, 1999) g
            RETURNING id, customer_id, last_message_at
        ), numbered_convs AS (
            SELECT id, customer_id, last_message_at, row_number() OVER () AS rn FROM inserted_convs
        )
        INSERT INTO conversation_messages(id, organization_id, conversation_id, direction, external_message_id, content_type, content, delivery_status, occurred_at, created_at)
        SELECT
            gen_random_uuid(),
            v_org,
            c.id,
            CASE WHEN m % 2 = 0 THEN 'OUTBOUND' ELSE 'INBOUND' END,
            'w17s-msg-' || c.rn || '-' || m,
            'TEXT',
            'Synthetic conversation ' || c.rn || ' message ' || m,
            CASE WHEN m % 2 = 0 THEN 'SENT' ELSE 'RECEIVED' END,
            c.last_message_at - ((CASE WHEN c.rn <= 4 THEN 600 ELSE 19 END - m) || ' minutes')::interval,
            c.last_message_at
        FROM numbered_convs c
        CROSS JOIN LATERAL generate_series(1, CASE WHEN c.rn <= 4 THEN 600 ELSE 19 END) AS m;

        -- 7) 1,999 additional customer_signals (total 2,000) on distinct inbound messages
        INSERT INTO customer_signals(id, organization_id, customer_id, message_id, signal_type, "value", confidence, evidence_text, source, human_message_id, status, reviewed_by_user_id, reviewed_at, created_at)
        SELECT
            gen_random_uuid(),
            v_org,
            c.customer_id,
            m.id,
            'INTENT',
            (ARRAY['ASK_PRICE','BOOKING','COMPLAINT','OTHER'])[1 + (row_number() OVER () % 4)],
            0.9000,
            'Synthetic scale signal',
            'HUMAN',
            m.id,
            'ACCEPTED',
            v_mgr,
            v_now,
            m.occurred_at
        FROM conversation_messages m
        JOIN conversations c ON c.id = m.conversation_id AND c.organization_id = m.organization_id
        WHERE m.organization_id = v_org AND m.direction = 'INBOUND' AND c.customer_id IS NOT NULL AND m.id <> v_target_msg
        LIMIT 1999;
    END IF;
END $$;
ANALYZE;
'@
    $seedSql = $seedSql.Replace('__SCALE_FLAG__', $(if ($Scale) { 'true' } else { 'false' }))
    $seedFile = Join-Path $temp 'seed-bench.sql'
    [IO.File]::WriteAllText($seedFile, $seedSql)
    & $psql @dbArgs -d $db -v ON_ERROR_STOP=1 -q -f $seedFile
    if ($LASTEXITCODE -ne 0) { throw 'Failed to seed benchmark dataset.' }

    # Print exact EDU row counts and fetch target IDs
    $countsSql = @"
SELECT
  (SELECT count(*) FROM leads l JOIN organizations o ON o.id=l.organization_id WHERE lower(o.code)='edu') AS leads,
  (SELECT count(*) FROM customers c JOIN organizations o ON o.id=c.organization_id WHERE lower(o.code)='edu') AS customers,
  (SELECT count(*) FROM opportunities p JOIN organizations o ON o.id=p.organization_id WHERE lower(o.code)='edu') AS opportunities,
  (SELECT count(*) FROM opportunity_lines ol JOIN organizations o ON o.id=ol.organization_id WHERE lower(o.code)='edu') AS opp_lines,
  (SELECT count(*) FROM interactions i JOIN organizations o ON o.id=i.organization_id WHERE lower(o.code)='edu') AS interactions,
  (SELECT count(*) FROM crm_tasks t JOIN organizations o ON o.id=t.organization_id WHERE lower(o.code)='edu') AS tasks,
  (SELECT count(*) FROM conversations cv JOIN organizations o ON o.id=cv.organization_id WHERE lower(o.code)='edu') AS conversations,
  (SELECT count(*) FROM conversation_messages cm JOIN organizations o ON o.id=cm.organization_id WHERE lower(o.code)='edu') AS messages,
  (SELECT count(*) FROM customer_signals cs JOIN organizations o ON o.id=cs.organization_id WHERE lower(o.code)='edu') AS signals;
"@
    $countsRow = (& $psql @dbArgs -d $db -Atc $countsSql).Trim()
    Write-Host "EDU_COUNTS (leads|customers|opps|opp_lines|interactions|tasks|conversations|messages|signals)=$countsRow"

    $targetCustomerId = (& $psql @dbArgs -d $db -Atc "SELECT customer_id FROM conversations WHERE id='11111111-1111-4111-8111-111111111102'").Trim()
    $targetConvId = '11111111-1111-4111-8111-111111111102'

    $env:JAVA_HOME = $JdkDirectory
    $env:POSE_DATABASE_URL = "jdbc:postgresql://127.0.0.1:54329/$db"
    $env:POSE_DATABASE_USERNAME = 'pose_auth_test'
    $env:POSE_DATABASE_PASSWORD = ''
    $env:POSE_JWT_SECRET_BASE64 = $jwtSecret
    $env:POSE_AI_ENABLED = 'true'
    $env:POSE_AI_PROVIDER = 'mock'
    $env:POSE_CHANNEL_ZALO_SIM_WEBHOOK_SECRET = $zaloSecret
    $env:SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE = '5'
    $env:PORT = '8081'

    $backendArgs = @(
        '-jar', ('"{0}"' -f $jar),
        '--server.address=127.0.0.1',
        "--spring.datasource.url=$env:POSE_DATABASE_URL",
        '--spring.datasource.username=pose_auth_test',
        '--spring.datasource.password=',
        '--pose.ai.enabled=true',
        '--pose.ai.provider=mock'
    )
    $backend = Start-Process -FilePath (Join-Path $JdkDirectory 'bin\java.exe') -ArgumentList $backendArgs -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $temp 'backend.stdout') -RedirectStandardError (Join-Path $temp 'backend.stderr')
    $null = $backend.Handle

    $ready = $false
    for ($i = 0; $i -lt 90; $i++) {
        if ($backend.HasExited) { throw ("Backend exited early with code " + $backend.ExitCode) }
        try {
            $resp = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8081/api/v1/auth/me' -TimeoutSec 2 -ErrorAction Stop
            $ready = $true; break
        } catch {
            if ($_.Exception.Response -and ([int]$_.Exception.Response.StatusCode -eq 401)) { $ready = $true; break }
            Start-Sleep -Milliseconds 500
        }
    }
    if (-not $ready) { throw 'Backend did not become ready in time.' }

    $nodeScript = @'
const BASE = 'http://127.0.0.1:8081';
const PASSWORD = process.env.POSE_BENCH_PASSWORD;
const ZALO_SECRET = process.env.POSE_CHANNEL_ZALO_SIM_WEBHOOK_SECRET;
const TARGET_CUSTOMER_ID = process.env.BENCH_TARGET_CUSTOMER_ID;
const TARGET_CONV_ID = process.env.BENCH_TARGET_CONV_ID;
const IS_SCALE = process.env.BENCH_SCALE === 'true';
const WARMUP = Number(process.env.BENCH_WARMUP || 5);
const ITERATIONS = Number(process.env.BENCH_ITERATIONS || 35);

async function jsonReq(path, { method = 'GET', token = null, headers = {}, body = null } = {}) {
  const h = { ...headers };
  if (token) h['Authorization'] = `Bearer ${token}`;
  if (body !== null) h['Content-Type'] = 'application/json';
  const t0 = performance.now();
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers: h,
    body: body !== null ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  const elapsed = performance.now() - t0;
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch {}
  return { status: res.status, elapsed, data };
}

function percentile(sorted, p) {
  const idx = Math.ceil((p / 100) * sorted.length) - 1;
  return sorted[Math.max(0, Math.min(sorted.length - 1, idx))];
}

async function bench(name, role, thresholdMs, fn) {
  for (let i = 0; i < WARMUP; i++) {
    const r = await fn(`warmup-${i}`);
    if (r.status < 200 || r.status >= 300) {
      throw new Error(`${name} (${role}) warmup failed with HTTP ${r.status}`);
    }
  }
  const times = [];
  for (let i = 0; i < ITERATIONS; i++) {
    const r = await fn(`run-${i}`);
    if (r.status < 200 || r.status >= 300) {
      throw new Error(`${name} (${role}) iteration ${i} failed with HTTP ${r.status}`);
    }
    times.push(r.elapsed);
  }
  times.sort((a, b) => a - b);
  const min = times[0];
  const p50 = percentile(times, 50);
  const p95 = percentile(times, 95);
  const max = times[times.length - 1];
  const pass = p95 < thresholdMs && max < thresholdMs;
  return { name, role, count: times.length, min, p50, p95, max, thresholdMs, pass };
}

(async () => {
  const mgrLogin = await jsonReq('/api/v1/auth/login', {
    method: 'POST',
    body: { organizationCode: 'edu', email: 'edu.mgr@example.com', password: PASSWORD },
  });
  if (mgrLogin.status !== 200) throw new Error(`Manager login failed: ${mgrLogin.status}`);
  const mgrToken = mgrLogin.data.accessToken;

  const s01Login = await jsonReq('/api/v1/auth/login', {
    method: 'POST',
    body: { organizationCode: 'edu', email: 'edu.s01@example.com', password: PASSWORD },
  });
  if (s01Login.status !== 200) throw new Error(`Sales login failed: ${s01Login.status}`);
  const s01Token = s01Login.data.accessToken;

  const accountId = 'zalo-w17-bench-edu';
  let whSeq = 0;
  const results = [];

  results.push(await bench('POST /api/v1/auth/login', 'Manager', 3000, () =>
    jsonReq('/api/v1/auth/login', {
      method: 'POST',
      body: { organizationCode: 'edu', email: 'edu.mgr@example.com', password: PASSWORD },
    })
  ));
  results.push(await bench('GET /api/v1/auth/me', 'Manager', 3000, () =>
    jsonReq('/api/v1/auth/me', { token: mgrToken })
  ));
  results.push(await bench('GET /api/v1/leads', 'Manager', 3000, () =>
    jsonReq('/api/v1/leads?page=0&size=20', { token: mgrToken })
  ));
  results.push(await bench('GET /api/v1/leads', 'Sales', 3000, () =>
    jsonReq('/api/v1/leads?page=0&size=20', { token: s01Token })
  ));
  results.push(await bench('GET /api/v1/customers', 'Manager', 3000, () =>
    jsonReq('/api/v1/customers?page=0&size=20', { token: mgrToken })
  ));
  results.push(await bench('GET /api/v1/customers', 'Sales', 3000, () =>
    jsonReq('/api/v1/customers?page=0&size=20', { token: s01Token })
  ));
  results.push(await bench('GET /api/v1/customers/{id}/360', 'Manager', 3000, () =>
    jsonReq(`/api/v1/customers/${TARGET_CUSTOMER_ID}/360`, { token: mgrToken })
  ));
  results.push(await bench('GET /api/v1/customers/{id}/360', 'Sales', 3000, () =>
    jsonReq(`/api/v1/customers/${TARGET_CUSTOMER_ID}/360`, { token: s01Token })
  ));
  results.push(await bench('GET /api/v1/opportunities', 'Manager', 3000, () =>
    jsonReq('/api/v1/opportunities?page=0&size=20', { token: mgrToken })
  ));
  results.push(await bench('GET /api/v1/opportunities', 'Sales', 3000, () =>
    jsonReq('/api/v1/opportunities?page=0&size=20', { token: s01Token })
  ));
  results.push(await bench('GET /api/v1/tasks', 'Manager', 3000, () =>
    jsonReq('/api/v1/tasks?page=0&size=20', { token: mgrToken })
  ));
  results.push(await bench('GET /api/v1/tasks', 'Sales', 3000, () =>
    jsonReq('/api/v1/tasks?page=0&size=20', { token: s01Token })
  ));
  results.push(await bench('GET /api/v1/conversations (page 0)', 'Manager', 3000, () =>
    jsonReq('/api/v1/conversations?page=0&size=20', { token: mgrToken })
  ));
  results.push(await bench('GET /api/v1/conversations (page 0)', 'Sales', 3000, () =>
    jsonReq('/api/v1/conversations?page=0&size=20', { token: s01Token })
  ));
  if (IS_SCALE) {
    results.push(await bench('GET /api/v1/conversations (page 25)', 'Manager', 3000, () =>
      jsonReq('/api/v1/conversations?page=25&size=20', { token: mgrToken })
    ));
    results.push(await bench('GET /api/v1/conversations (page 25)', 'Sales', 3000, () =>
      jsonReq('/api/v1/conversations?page=25&size=20', { token: s01Token })
    ));
  }
  results.push(await bench('GET /api/v1/conversations/{id}/messages', 'Manager', 3000, () =>
    jsonReq(`/api/v1/conversations/${TARGET_CONV_ID}/messages?page=0&size=50`, { token: mgrToken })
  ));
  results.push(await bench('GET /api/v1/conversations/{id}/messages', 'Sales', 3000, () =>
    jsonReq(`/api/v1/conversations/${TARGET_CONV_ID}/messages?page=0&size=50`, { token: s01Token })
  ));
  results.push(await bench('GET /api/v1/dashboard/kpis', 'Manager', 3000, () =>
    jsonReq('/api/v1/dashboard/kpis?start=2026-08-31T17:00:00Z&end=2026-10-11T00:00:00Z&asOf=2026-10-11T00:00:00Z&timezone=Asia/Ho_Chi_Minh', { token: mgrToken })
  ));
  results.push(await bench('GET /api/v1/dashboard/kpis', 'Sales', 3000, () =>
    jsonReq('/api/v1/dashboard/kpis?start=2026-08-31T17:00:00Z&end=2026-10-11T00:00:00Z&asOf=2026-10-11T00:00:00Z&timezone=Asia/Ho_Chi_Minh', { token: s01Token })
  ));
  results.push(await bench('GET /api/v1/dashboard/today', 'Manager', 3000, () =>
    jsonReq('/api/v1/dashboard/today', { token: mgrToken })
  ));
  results.push(await bench('GET /api/v1/dashboard/today', 'Sales', 3000, () =>
    jsonReq('/api/v1/dashboard/today', { token: s01Token })
  ));
  results.push(await bench('POST /api/v1/channels/zalo-sim/webhook', 'Webhook', 2000, () => {
    whSeq++;
    return jsonReq('/api/v1/channels/zalo-sim/webhook', {
      method: 'POST',
      headers: { 'X-POSE-Webhook-Secret': ZALO_SECRET },
      body: {
        event_name: 'user_send_text',
        timestamp: new Date(Date.UTC(2026, 9, 10, 11, 0, whSeq)).toISOString(),
        app_id: accountId,
        sender: { id: 'bench-thread-target' },
        recipient: { id: accountId },
        message: { msg_id: `bench-msg-${whSeq}`, text: `Benchmark inbound ${whSeq}` },
      },
    });
  }));

  console.log('\n| Endpoint | Vai trò | N | min (ms) | p50 (ms) | p95 (ms) | max (ms) | SRS 2.0 | Kết quả |');
  console.log('|---|---|---:|---:|---:|---:|---:|---:|---|');
  let allPass = true;
  for (const r of results) {
    if (!r.pass) allPass = false;
    console.log(`| ${r.name} | ${r.role} | ${r.count} | ${r.min.toFixed(1)} | ${r.p50.toFixed(1)} | ${r.p95.toFixed(1)} | ${r.max.toFixed(1)} | < ${r.thresholdMs} ms | ${r.pass ? 'PASS' : 'FAIL'} |`);
  }

  const nonLogin = results.filter(r => !r.name.includes('/auth/login'));
  nonLogin.sort((a, b) => b.p95 - a.p95);
  console.log('\nTOP3_NON_LOGIN_P95:');
  for (const r of nonLogin.slice(0, 3)) {
    console.log(`  - ${r.name} (${r.role}): p50=${r.p50.toFixed(1)}ms p95=${r.p95.toFixed(1)}ms max=${r.max.toFixed(1)}ms`);
  }
  if (!allPass) process.exit(2);
})().catch(err => {
  console.error(err.message || err);
  process.exit(1);
});
'@
    $nodeFile = Join-Path $temp 'bench.mjs'
    [IO.File]::WriteAllText($nodeFile, $nodeScript)
    $env:BENCH_WARMUP = "$Warmup"
    $env:BENCH_ITERATIONS = "$Iterations"
    $env:BENCH_TARGET_CUSTOMER_ID = $targetCustomerId
    $env:BENCH_TARGET_CONV_ID = $targetConvId
    $env:BENCH_SCALE = $(if ($Scale) { 'true' } else { 'false' })

    & node $nodeFile
    $benchExit = $LASTEXITCODE

    if ($Scale) {
        Write-Host "`n=== EXPLAIN (ANALYZE, BUFFERS) ON SCALE DATASET ==="
        $explainSql = @"
\echo '--- EXPLAIN 1: GET /api/v1/conversations (Manager, page 0, 2 correlated subqueries W17-08) ---'
EXPLAIN (ANALYZE, BUFFERS)
SELECT c.id,ca.channel_type,ca.display_name,c.status,c.assigned_user_id,c.lead_id,c.customer_id,
       c.last_inbound_at,c.last_message_at,
       (SELECT m.content FROM conversation_messages m
        WHERE m.organization_id=c.organization_id AND m.conversation_id=c.id
        ORDER BY m.occurred_at DESC,m.id DESC LIMIT 1) AS last_content,
       (SELECT m.direction FROM conversation_messages m
        WHERE m.organization_id=c.organization_id AND m.conversation_id=c.id
        ORDER BY m.occurred_at DESC,m.id DESC LIMIT 1) AS last_direction
FROM conversations c JOIN channel_accounts ca ON ca.organization_id=c.organization_id AND ca.id=c.channel_account_id
WHERE c.organization_id=(SELECT id FROM organizations WHERE lower(code)='edu')
  AND EXISTS (SELECT 1 FROM teams t WHERE t.organization_id=ca.organization_id AND t.id=ca.team_id AND t.status='ACTIVE'
              AND t.manager_user_id=(SELECT u.id FROM users u JOIN organizations o ON o.id=u.organization_id WHERE lower(o.code)='edu' AND lower(u.email)='edu.mgr@example.com'))
ORDER BY COALESCE(c.last_message_at,c.created_at) DESC,c.id LIMIT 20 OFFSET 0;

\echo '--- EXPLAIN 2: GET /api/v1/conversations (Manager, page 25, OFFSET 500) ---'
EXPLAIN (ANALYZE, BUFFERS)
SELECT c.id,ca.channel_type,ca.display_name,c.status,c.assigned_user_id,c.lead_id,c.customer_id,
       c.last_inbound_at,c.last_message_at,
       (SELECT m.content FROM conversation_messages m
        WHERE m.organization_id=c.organization_id AND m.conversation_id=c.id
        ORDER BY m.occurred_at DESC,m.id DESC LIMIT 1) AS last_content,
       (SELECT m.direction FROM conversation_messages m
        WHERE m.organization_id=c.organization_id AND m.conversation_id=c.id
        ORDER BY m.occurred_at DESC,m.id DESC LIMIT 1) AS last_direction
FROM conversations c JOIN channel_accounts ca ON ca.organization_id=c.organization_id AND ca.id=c.channel_account_id
WHERE c.organization_id=(SELECT id FROM organizations WHERE lower(code)='edu')
  AND EXISTS (SELECT 1 FROM teams t WHERE t.organization_id=ca.organization_id AND t.id=ca.team_id AND t.status='ACTIVE'
              AND t.manager_user_id=(SELECT u.id FROM users u JOIN organizations o ON o.id=u.organization_id WHERE lower(o.code)='edu' AND lower(u.email)='edu.mgr@example.com'))
ORDER BY COALESCE(c.last_message_at,c.created_at) DESC,c.id LIMIT 20 OFFSET 500;

\echo '--- EXPLAIN 3: GET /api/v1/dashboard/kpis — E02 unattended opportunities ---'
EXPLAIN (ANALYZE, BUFFERS)
SELECT op.currency_code, count(*) AS total, coalesce(sum(op.expected_value),0) AS value
FROM opportunities op
WHERE op.organization_id=(SELECT id FROM organizations WHERE lower(code)='edu')
  AND op.stage IN ('NEW','QUALIFIED','PROPOSAL')
  AND NOT EXISTS (
    SELECT 1 FROM interactions i
    WHERE i.organization_id=op.organization_id
      AND (i.opportunity_id=op.id OR (op.customer_id IS NOT NULL AND i.customer_id=op.customer_id) OR (op.lead_id IS NOT NULL AND i.lead_id=op.lead_id))
      AND i.occurred_at > TIMESTAMPTZ '2026-09-27 00:00:00+00' AND i.occurred_at <= TIMESTAMPTZ '2026-10-11 00:00:00+00'
  )
GROUP BY op.currency_code;

\echo '--- EXPLAIN 4: GET /api/v1/dashboard/kpis — A06 customer activity recency ---'
EXPLAIN (ANALYZE, BUFFERS)
SELECT CASE
    WHEN max(i.occurred_at) IS NULL THEN 'NEVER_ENGAGED'
    WHEN max(i.occurred_at) >= TIMESTAMPTZ '2026-09-11 00:00:00+00' THEN 'ACTIVE'
    ELSE 'INACTIVE'
  END AS bucket, count(*)
FROM customers c
LEFT JOIN interactions i ON i.organization_id=c.organization_id AND i.customer_id=c.id AND i.occurred_at <= TIMESTAMPTZ '2026-10-11 00:00:00+00'
WHERE c.organization_id=(SELECT id FROM organizations WHERE lower(code)='edu') AND c.archived_at IS NULL
GROUP BY c.id;

\echo '--- EXPLAIN 5: GET /api/v1/customers/{id}/360 — interactions list ---'
EXPLAIN (ANALYZE, BUFFERS)
SELECT i.id, i.type, i.channel, i.subject, i.visibility, i.occurred_at
FROM interactions i
WHERE i.organization_id=(SELECT id FROM organizations WHERE lower(code)='edu')
  AND i.customer_id='$targetCustomerId'::uuid
ORDER BY i.occurred_at DESC, i.id DESC LIMIT 10;

\echo '--- EXPLAIN 6: GET /api/v1/dashboard/today — overdue tasks + E01 leads ---'
EXPLAIN (ANALYZE, BUFFERS)
SELECT l.id, l.display_name, l.created_at
FROM leads l
WHERE l.organization_id=(SELECT id FROM organizations WHERE lower(code)='edu')
  AND l.archived_at IS NULL AND l.status IN ('NEW','QUALIFIED')
  AND l.created_at <= TIMESTAMPTZ '2026-10-10 00:00:00+00'
  AND NOT EXISTS (SELECT 1 FROM interactions i WHERE i.organization_id=l.organization_id AND i.lead_id=l.id AND i.occurred_at <= TIMESTAMPTZ '2026-10-11 00:00:00+00')
ORDER BY l.created_at ASC, l.id ASC LIMIT 50;

\echo '--- EXPLAIN 7: GET /api/v1/tasks (Manager page 0) ---'
EXPLAIN (ANALYZE, BUFFERS)
SELECT t.id, t.title, t.status, t.priority, t.due_at
FROM crm_tasks t
WHERE t.organization_id=(SELECT id FROM organizations WHERE lower(code)='edu')
ORDER BY t.due_at ASC NULLS LAST, t.id ASC LIMIT 20 OFFSET 0;
"@
        $explainFile = Join-Path $temp 'explain.sql'
        [IO.File]::WriteAllText($explainFile, $explainSql)
        & $psql @dbArgs -d $db -v ON_ERROR_STOP=1 -f $explainFile
    }

    if ($benchExit -ne 0) { throw "Benchmark failed with exit code $benchExit" }
} finally {
    if ($backend -and -not $backend.HasExited) {
        try { Stop-Process -Id $backend.Id -Force -ErrorAction SilentlyContinue } catch {}
        try { $backend.WaitForExit(10000) | Out-Null } catch {}
    }
    if ($created -and $verified) {
        try {
            & $psql @dbArgs -d postgres -v ON_ERROR_STOP=1 -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='$db' AND pid<>pg_backend_pid();" | Out-Null
            & $psql @dbArgs -d postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS `"$db`";" | Out-Null
        } catch {}
    }
    if ($started -and $verified) {
        try { & $pgCtl -D $cluster -m fast -w -t 20 stop | Out-Null } catch {}
    }
    foreach ($name in $saved.Keys) { [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process') }
    foreach ($extra in @('POSE_BENCH_PASSWORD','BENCH_WARMUP','BENCH_ITERATIONS','BENCH_TARGET_CUSTOMER_ID','BENCH_TARGET_CONV_ID','BENCH_SCALE')) {
        [Environment]::SetEnvironmentVariable($extra, $null, 'Process')
    }
    Remove-Item -LiteralPath $temp -Recurse -Force -ErrorAction SilentlyContinue
    foreach ($port in @(8081, 54329)) {
        if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
            Write-Host "WARNING: Port $port still listening."
        } else {
            Write-Host "Port $port stopped."
        }
    }
}
