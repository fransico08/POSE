param(
    [string]$JdkDirectory = 'C:\Program Files\Java\jdk-25.0.2',
    [string]$PgBin = 'C:\Program Files\PostgreSQL\18\bin',
    [string]$MavenPath = 'C:\Users\Dell\.maven\maven-3.9.15\bin\mvn.cmd',
    [int]$Warmup = 5,
    [int]$Iterations = 35,
    [switch]$Build,
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

    $sql = "UPDATE credentials SET password_hash='$hash' WHERE user_id IN (SELECT u.id FROM users u JOIN organizations o ON o.id=u.organization_id WHERE lower(o.code)='edu' AND lower(u.email) IN ('edu.admin@example.com','edu.mgr@example.com'));"
    $sql | & $psql @dbArgs -d $db -v ON_ERROR_STOP=1 -q
    if ($LASTEXITCODE -ne 0) { throw 'Cannot update isolated benchmark credentials.' }

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
        if ($backend.HasExited) {
            throw ("Backend exited early with code " + $backend.ExitCode)
        }
        try {
            $resp = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8081/api/v1/auth/me' -TimeoutSec 2 -ErrorAction Stop
            $ready = $true; break
        } catch {
            if ($_.Exception.Response -and ([int]$_.Exception.Response.StatusCode -eq 401)) {
                $ready = $true; break
            }
            Start-Sleep -Milliseconds 500
        }
    }
    if (-not $ready) { throw 'Backend did not become ready in time.' }

    $nodeScript = @'
const BASE = 'http://127.0.0.1:8081';
const PASSWORD = process.env.POSE_BENCH_PASSWORD;
const ZALO_SECRET = process.env.POSE_CHANNEL_ZALO_SIM_WEBHOOK_SECRET;
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

async function bench(name, thresholdMs, fn) {
  for (let i = 0; i < WARMUP; i++) {
    const r = await fn(`warmup-${i}`);
    if (r.status < 200 || r.status >= 300) {
      throw new Error(`${name} warmup failed with HTTP ${r.status}`);
    }
  }
  const times = [];
  for (let i = 0; i < ITERATIONS; i++) {
    const r = await fn(`run-${i}`);
    if (r.status < 200 || r.status >= 300) {
      throw new Error(`${name} iteration ${i} failed with HTTP ${r.status}`);
    }
    times.push(r.elapsed);
  }
  times.sort((a, b) => a - b);
  const min = times[0];
  const p50 = percentile(times, 50);
  const p95 = percentile(times, 95);
  const max = times[times.length - 1];
  const pass = p95 < thresholdMs && max < thresholdMs;
  return { name, count: times.length, min, p50, p95, max, thresholdMs, pass };
}

(async () => {
  const adminLogin = await jsonReq('/api/v1/auth/login', {
    method: 'POST',
    body: { organizationCode: 'edu', email: 'edu.admin@example.com', password: PASSWORD },
  });
  if (adminLogin.status !== 200) throw new Error(`Admin login failed: ${adminLogin.status}`);
  const adminToken = adminLogin.data.accessToken;

  const mgrLogin = await jsonReq('/api/v1/auth/login', {
    method: 'POST',
    body: { organizationCode: 'edu', email: 'edu.mgr@example.com', password: PASSWORD },
  });
  if (mgrLogin.status !== 200) throw new Error(`Manager login failed: ${mgrLogin.status}`);
  const mgrToken = mgrLogin.data.accessToken;
  const mgrUserId = mgrLogin.data.user.id;

  const teamsRes = await jsonReq('/api/v1/teams', { token: adminToken });
  const salesTeam = (teamsRes.data.items || teamsRes.data).find(t => t.code === 'SALES' || t.status === 'ACTIVE');
  if (!salesTeam) throw new Error('No active EDU team found');

  const accountId = 'zalo-w17-bench-edu';
  const createAcc = await jsonReq('/api/v1/channels/accounts', {
    method: 'POST',
    token: adminToken,
    body: {
      channelType: 'ZALO_SIM',
      displayName: 'Zalo Sim W17 Bench',
      externalAccountId: accountId,
      teamId: salesTeam.id,
      defaultAssigneeUserId: mgrUserId,
      secretRef: 'POSE_CHANNEL_ZALO_SIM_WEBHOOK_SECRET',
    },
  });
  if (createAcc.status !== 201) throw new Error(`Create channel account failed: ${createAcc.status}`);

  // Seed 3 conversations with 10 messages total so inbox list & messages have realistic rows
  let conversationId = null;
  for (let t = 1; t <= 3; t++) {
    for (let m = 1; m <= (t === 1 ? 6 : 2); m++) {
      const ack = await jsonReq('/api/v1/channels/zalo-sim/webhook', {
        method: 'POST',
        headers: { 'X-POSE-Webhook-Secret': ZALO_SECRET },
        body: {
          event_name: 'user_send_text',
          timestamp: new Date(Date.UTC(2026, 9, 10, 10, t, m)).toISOString(),
          app_id: accountId,
          sender: { id: `bench-thread-${t}` },
          recipient: { id: accountId },
          message: { msg_id: `seed-${t}-${m}`, text: `Synthetic benchmark message ${t}-${m}` },
        },
      });
      if (ack.status !== 200) throw new Error(`Seed webhook failed: ${ack.status}`);
      if (t === 1) conversationId = ack.data.conversationId;
    }
  }

  let whSeq = 0;
  const results = [];
  results.push(await bench('POST /api/v1/auth/login', 3000, () =>
    jsonReq('/api/v1/auth/login', {
      method: 'POST',
      body: { organizationCode: 'edu', email: 'edu.mgr@example.com', password: PASSWORD },
    })
  ));
  results.push(await bench('GET /api/v1/auth/me', 3000, () =>
    jsonReq('/api/v1/auth/me', { token: mgrToken })
  ));
  results.push(await bench('GET /api/v1/leads', 3000, () =>
    jsonReq('/api/v1/leads?page=0&size=20', { token: mgrToken })
  ));
  results.push(await bench('GET /api/v1/customers', 3000, () =>
    jsonReq('/api/v1/customers?page=0&size=20', { token: mgrToken })
  ));
  results.push(await bench('GET /api/v1/opportunities', 3000, () =>
    jsonReq('/api/v1/opportunities?page=0&size=20', { token: mgrToken })
  ));
  results.push(await bench('GET /api/v1/tasks', 3000, () =>
    jsonReq('/api/v1/tasks?page=0&size=20', { token: mgrToken })
  ));
  results.push(await bench('GET /api/v1/conversations', 3000, () =>
    jsonReq('/api/v1/conversations?page=0&size=20', { token: mgrToken })
  ));
  results.push(await bench('GET /api/v1/conversations/{id}/messages', 3000, () =>
    jsonReq(`/api/v1/conversations/${conversationId}/messages?page=0&size=50`, { token: mgrToken })
  ));
  results.push(await bench('GET /api/v1/dashboard/kpis', 3000, () =>
    jsonReq('/api/v1/dashboard/kpis?start=2026-08-31T17:00:00Z&end=2026-09-30T17:00:00Z&asOf=2026-09-30T17:00:00Z&timezone=Asia/Ho_Chi_Minh', { token: mgrToken })
  ));
  results.push(await bench('GET /api/v1/dashboard/today', 3000, () =>
    jsonReq('/api/v1/dashboard/today', { token: mgrToken })
  ));
  results.push(await bench('POST /api/v1/channels/zalo-sim/webhook', 2000, () => {
    whSeq++;
    return jsonReq('/api/v1/channels/zalo-sim/webhook', {
      method: 'POST',
      headers: { 'X-POSE-Webhook-Secret': ZALO_SECRET },
      body: {
        event_name: 'user_send_text',
        timestamp: new Date(Date.UTC(2026, 9, 10, 11, 0, whSeq)).toISOString(),
        app_id: accountId,
        sender: { id: 'bench-thread-1' },
        recipient: { id: accountId },
        message: { msg_id: `bench-msg-${whSeq}`, text: `Benchmark inbound ${whSeq}` },
      },
    });
  }));

  console.log('\n| Endpoint | N | min (ms) | p50 (ms) | p95 (ms) | max (ms) | SRS 2.0 | Kết quả |');
  console.log('|---|---:|---:|---:|---:|---:|---:|---|');
  let allPass = true;
  for (const r of results) {
    if (!r.pass) allPass = false;
    console.log(`| ${r.name} | ${r.count} | ${r.min.toFixed(1)} | ${r.p50.toFixed(1)} | ${r.p95.toFixed(1)} | ${r.max.toFixed(1)} | < ${r.thresholdMs} ms | ${r.pass ? 'PASS' : 'FAIL'} |`);
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

    & node $nodeFile
    if ($LASTEXITCODE -ne 0) { throw "Benchmark failed with exit code $LASTEXITCODE" }
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
    [Environment]::SetEnvironmentVariable('POSE_BENCH_PASSWORD', $null, 'Process')
    [Environment]::SetEnvironmentVariable('BENCH_WARMUP', $null, 'Process')
    [Environment]::SetEnvironmentVariable('BENCH_ITERATIONS', $null, 'Process')
    Remove-Item -LiteralPath $temp -Recurse -Force -ErrorAction SilentlyContinue
    foreach ($port in @(8081, 54329)) {
        if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
            Write-Host "WARNING: Port $port still listening."
        } else {
            Write-Host "Port $port stopped."
        }
    }
}
