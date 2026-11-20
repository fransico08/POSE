param(
    [string]$PsqlPath = 'psql.exe',
    [string]$FlywayPath = 'flyway',
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
$baselineSourcePath = Join-Path $repoRoot 'backend\src\main\resources\db\baseline\V1__nhi_v2_schema.sql'
$migrationSourceDirectory = Join-Path $repoRoot 'backend\src\main\resources\db\migration'

function Assert-IsolatedCluster {
    if ($Port -ne 54329) {
        throw 'Only the dedicated PostgreSQL test port 54329 is permitted for this integration script.'
    }

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

function Get-ApprovedMigrationFile {
    param(
        [Parameter(Mandatory)]
        [ValidateRange(2, 14)]
        [int]$Version,
        [string]$ExpectedName
    )

    $filter = 'V{0}__*.sql' -f $Version
    [IO.FileInfo[]]$migrationCandidates = @(Get-ChildItem -LiteralPath $migrationSourceDirectory -File -Filter $filter)
    if ($migrationCandidates.Count -ne 1) {
        throw "Expected exactly one V$Version migration matching $filter; found $($migrationCandidates.Count)."
    }

    [IO.FileInfo]$migration = $migrationCandidates[0]
    $namePattern = '^V{0}__[A-Za-z0-9_]+\.sql$' -f $Version
    if ($migration.Name -notmatch $namePattern) {
        throw "Migration V$Version has an invalid filename: $($migration.Name)"
    }
    if (-not [string]::IsNullOrWhiteSpace($ExpectedName) -and $migration.Name -cne $ExpectedName) {
        throw "Migration V$Version must be named $ExpectedName; found $($migration.Name)."
    }

    return $migration
}

function Copy-VerifiedSqlFile {
    param(
        [Parameter(Mandatory)]
        [IO.FileInfo]$Source,
        [Parameter(Mandatory)]
        [string]$DestinationDirectory
    )

    if (-not $Source.Exists -or $Source.Extension -cne '.sql') {
        throw "Invalid SQL source file: $($Source.FullName)"
    }

    $sourceHash = (Get-FileHash -LiteralPath $Source.FullName -Algorithm SHA256).Hash
    $destinationPath = Join-Path $DestinationDirectory $Source.Name
    Copy-Item -LiteralPath $Source.FullName -Destination $destinationPath -ErrorAction Stop
    if (-not (Test-Path -LiteralPath $destinationPath)) {
        throw "Migration copy is missing: $destinationPath"
    }
    if ((Get-FileHash -LiteralPath $destinationPath -Algorithm SHA256).Hash -cne $sourceHash) {
        throw "Migration copy checksum mismatch: $($Source.Name)"
    }
}

if ($Port -ne 54329) {
    throw 'Only the dedicated PostgreSQL test port 54329 is permitted for this integration script.'
}

if (-not $Execute) {
    & (Join-Path $PSScriptRoot 'load_w12_w13_integration_w12.ps1') -PsqlPath $PsqlPath -Port $Port
    & (Join-Path $PSScriptRoot 'load_w12_w13_integration_w13.ps1') -PsqlPath $PsqlPath -Port $Port
    'W12_W13_INTEGRATION_DRY_RUN=PASS execution=NOT_RUN'
    exit 0
}
$PsqlPath = Resolve-PsqlExecutable -Value $PsqlPath
Assert-IsolatedCluster
$identity = & $PsqlPath -X -w -h $targetHost -p $Port -U $postgresUser -d $targetDatabase -At -v ON_ERROR_STOP=1 -c "SELECT current_database() || '|' || current_setting('server_version_num');"
if ($LASTEXITCODE -ne 0 -or $identity.Trim() -notmatch '^pose_w12_w13_integration_test\|18') { throw 'Target must be local PostgreSQL 18 database pose_w12_w13_integration_test.' }
$existing = & $PsqlPath -X -w -h $targetHost -p $Port -U $postgresUser -d $targetDatabase -At -v ON_ERROR_STOP=1 -c "SELECT count(*) FROM pg_catalog.pg_tables WHERE schemaname='public';"
if ($LASTEXITCODE -ne 0 -or $existing.Trim() -ne '0') { throw 'Target must be empty before the first migration; this runner never resets databases.' }
$stage = Join-Path $env:TEMP ('pose_w12_w13_flyway_' + [Guid]::NewGuid().ToString('N'))
try {
    $baseline = Join-Path $stage 'baseline'; $migrations = Join-Path $stage 'migration'
    New-Item -ItemType Directory -Force -Path $baseline,$migrations | Out-Null
    [IO.FileInfo]$baselineFile = Get-Item -LiteralPath $baselineSourcePath
    if ($baselineFile.Name -cne 'V1__nhi_v2_schema.sql') {
        throw "Unexpected V1 baseline filename: $($baselineFile.Name)"
    }
    Copy-VerifiedSqlFile -Source $baselineFile -DestinationDirectory $baseline
    foreach ($version in 2..12) {
        $migrationFile = Get-ApprovedMigrationFile -Version $version
        Copy-VerifiedSqlFile -Source $migrationFile -DestinationDirectory $migrations
    }
    & $FlywayPath "-url=jdbc:postgresql://$targetHost`:$Port/$targetDatabase" "-user=$postgresUser" "-locations=filesystem:$baseline,filesystem:$migrations" migrate
    if ($LASTEXITCODE -ne 0) { throw 'Flyway V1-V12 failed.' }
    & (Join-Path $PSScriptRoot 'load_w12_w13_integration_w12.ps1') -PsqlPath $PsqlPath -Port $Port -Execute
    & (Join-Path $PSScriptRoot 'verify_w12_w13_integration_w12.ps1') -PsqlPath $PsqlPath -Port $Port
    $v13Migration = Get-ApprovedMigrationFile -Version 13 -ExpectedName 'V13__conversations.sql'
    $v14Migration = Get-ApprovedMigrationFile -Version 14 -ExpectedName 'V14__channel_account_ownership.sql'
    Copy-VerifiedSqlFile -Source $v13Migration -DestinationDirectory $migrations
    Copy-VerifiedSqlFile -Source $v14Migration -DestinationDirectory $migrations
    & $FlywayPath "-url=jdbc:postgresql://$targetHost`:$Port/$targetDatabase" "-user=$postgresUser" "-locations=filesystem:$baseline,filesystem:$migrations" migrate
    if ($LASTEXITCODE -ne 0) { throw 'Flyway V13-V14 failed.' }
    & (Join-Path $PSScriptRoot 'load_w12_w13_integration_w13.ps1') -PsqlPath $PsqlPath -Port $Port -Execute
    & (Join-Path $PSScriptRoot 'verify_w12_w13_integration.ps1') -PsqlPath $PsqlPath -Port $Port
}
finally { Remove-Item -LiteralPath $stage -Recurse -Force -ErrorAction SilentlyContinue }
