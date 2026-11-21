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
$verifyPath = Join-Path $PSScriptRoot 'verify_seed_v2.psql'
$temporaryScript = Join-Path $env:TEMP ('pose_w12_w13_verify_w12_' + [Guid]::NewGuid().ToString('N') + '.psql')

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
if ($Port -ne 54329) {
    throw 'Only the dedicated PostgreSQL test port 54329 is permitted for this integration script.'
}
Assert-IsolatedCluster

$original = [IO.File]::ReadAllText($verifyPath, [Text.Encoding]::UTF8)
$canonicalGuard = "current_database() NOT IN ('pose_w12_v12_final_test', 'pose_w12_v12_clean_test', 'pose_w12_v12_canonical_test')"
$integrationGuard = "current_database() <> '$targetDatabase'"
if (([regex]::Matches($original, [regex]::Escape($canonicalGuard))).Count -ne 1) {
    throw 'Canonical W12 verifier guard is missing or ambiguous.'
}
[IO.File]::WriteAllText($temporaryScript, $original.Replace($canonicalGuard, $integrationGuard), [Text.UTF8Encoding]::new($false))

try {
    $identity = & $PsqlPath -X -w -h $targetHost -p $Port -U $postgresUser -d $targetDatabase -At -v ON_ERROR_STOP=1 -c 'SELECT current_database();'
    if ($LASTEXITCODE -ne 0 -or $identity.Trim() -cne $targetDatabase) {
        throw 'Local integration target verification failed.'
    }

    Push-Location $repoRoot
    try {
        & $PsqlPath -X -w -h $targetHost -p $Port -U $postgresUser -d $targetDatabase -v ON_ERROR_STOP=1 -f $temporaryScript
        if ($LASTEXITCODE -ne 0) {
            throw 'W12 canonical integration verification failed.'
        }
    }
    finally {
        Pop-Location
    }
}
finally {
    Remove-Item -LiteralPath $temporaryScript -Force -ErrorAction SilentlyContinue
}
