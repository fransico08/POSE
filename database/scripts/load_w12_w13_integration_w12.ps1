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
$seedRoot = Join-Path $repoRoot 'database\data\seed\multisector_v2'
$manifestPath = Join-Path $seedRoot 'manifest.json'
$seedPath = Join-Path $PSScriptRoot 'seed_v2.psql'
$expectedManifestHash = '7AECD2D50D19975209F50ACE65759AB25A370C5A54269557B9137FA0B78686B6'

function Get-Sha256FromBytes {
    param(
        [Parameter(Mandatory)]
        [byte[]]$Bytes
    )

    $sha256 = [Security.Cryptography.SHA256]::Create()
    try {
        return (($sha256.ComputeHash($Bytes) | ForEach-Object {
            $_.ToString('x2')
        }) -join '').ToUpperInvariant()
    }
    finally {
        $sha256.Dispose()
    }
}

function Get-CanonicalSourceRecordLinksHash {
    param(
        [Parameter(Mandatory)]
        [string]$Path
    )

    [byte[]]$rawBytes = [IO.File]::ReadAllBytes($Path)
    if ($rawBytes.Length -lt 3 -or
        $rawBytes[0] -ne 0xEF -or
        $rawBytes[1] -ne 0xBB -or
        $rawBytes[2] -ne 0xBF) {
        throw 'source_record_links.csv must use UTF-8 with a BOM.'
    }

    $utf8 = [Text.UTF8Encoding]::new($false, $true)
    try {
        [void]$utf8.GetString($rawBytes)
    }
    catch [Text.DecoderFallbackException] {
        throw 'source_record_links.csv contains invalid UTF-8 bytes.'
    }

    $lineEnding = $null
    [byte[]]$canonicalBytes = New-Object byte[] $rawBytes.Length
    $outputIndex = 0
    for ($index = 0; $index -lt $rawBytes.Length; $index++) {
        $currentByte = $rawBytes[$index]
        if ($currentByte -eq 0x0D) {
            if ($index + 1 -ge $rawBytes.Length -or $rawBytes[$index + 1] -ne 0x0A) {
                throw 'source_record_links.csv contains a malformed CR line ending.'
            }
            if ($null -eq $lineEnding) {
                $lineEnding = 'CRLF'
            }
            elseif ($lineEnding -ne 'CRLF') {
                throw 'source_record_links.csv mixes LF and CRLF line endings.'
            }
            $canonicalBytes[$outputIndex++] = 0x0A
            $index++
            continue
        }

        if ($currentByte -eq 0x0A) {
            if ($null -eq $lineEnding) {
                $lineEnding = 'LF'
            }
            elseif ($lineEnding -ne 'LF') {
                throw 'source_record_links.csv mixes LF and CRLF line endings.'
            }
        }
        $canonicalBytes[$outputIndex++] = $currentByte
    }

    if ($null -eq $lineEnding) {
        throw 'source_record_links.csv does not contain valid line endings.'
    }

    if ($outputIndex -lt $canonicalBytes.Length) {
        [byte[]]$trimmedBytes = New-Object byte[] $outputIndex
        [Array]::Copy($canonicalBytes, $trimmedBytes, $outputIndex)
        $canonicalBytes = $trimmedBytes
    }

    return [pscustomobject]@{
        RawSha256 = Get-Sha256FromBytes -Bytes $rawBytes
        CanonicalLfSha256 = Get-Sha256FromBytes -Bytes $canonicalBytes
        LineEnding = $lineEnding
    }
}

function Assert-CanonicalW12Assets {
    if ((Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash -cne $expectedManifestHash) {
        throw 'W12 canonical manifest checksum mismatch.'
    }

    $manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
    foreach ($property in $manifest.fileSha256.PSObject.Properties) {
        $path = Join-Path $seedRoot $property.Name
        if (-not (Test-Path -LiteralPath $path)) {
            throw "W12 canonical artifact is missing: $($property.Name)"
        }
        if ($property.Name -eq 'source_record_links.csv') {
            $sourceRecordLinksHash = Get-CanonicalSourceRecordLinksHash -Path $path
            Write-Output (
                'W12_SOURCE_RECORD_LINKS_SHA256 raw={0} canonical_lf={1} line_endings={2}' -f
                $sourceRecordLinksHash.RawSha256.ToUpperInvariant(),
                $sourceRecordLinksHash.CanonicalLfSha256.ToUpperInvariant(),
                $sourceRecordLinksHash.LineEnding
            )
            if ($sourceRecordLinksHash.CanonicalLfSha256 -cne [string]$property.Value) {
                throw 'W12 canonical artifact checksum mismatch: source_record_links.csv'
            }
            continue
        }
        if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -cne [string]$property.Value) {
            throw "W12 canonical artifact checksum mismatch: $($property.Name)"
        }
    }
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

function Get-IntegrationW12Script {
    $original = [IO.File]::ReadAllText($seedPath, [Text.Encoding]::UTF8)
    $canonicalGuard = "current_database() NOT IN ('pose_w12_v12_final_test', 'pose_w12_v12_clean_test', 'pose_w12_v12_canonical_test')"
    $integrationGuard = "current_database() <> '$targetDatabase'"
    $guardCount = ([regex]::Matches($original, [regex]::Escape($canonicalGuard))).Count
    if ($guardCount -ne 1) {
        throw "Canonical W12 seed guard is missing or ambiguous: $guardCount"
    }
    return $original.Replace($canonicalGuard, $integrationGuard)
}

Assert-CanonicalW12Assets
$integrationScript = Get-IntegrationW12Script

if ($Port -ne 54329) {
    throw 'Only the dedicated PostgreSQL test port 54329 is permitted for this integration script.'
}

if (-not $Execute) {
    'W12_W13_INTEGRATION_W12_DRY_RUN=PASS canonical_manifest=7AECD2D50D19975209F50ACE65759AB25A370C5A54269557B9137FA0B78686B6'
    exit 0
}

$PsqlPath = Resolve-PsqlExecutable -Value $PsqlPath
Assert-IsolatedCluster

$temporaryScript = Join-Path $env:TEMP ('pose_w12_w13_w12_' + [Guid]::NewGuid().ToString('N') + '.psql')
[IO.File]::WriteAllText($temporaryScript, $integrationScript, [Text.UTF8Encoding]::new($false))

try {
    $identity = & $PsqlPath -X -w -h $targetHost -p $Port -U $postgresUser -d $targetDatabase -At -v ON_ERROR_STOP=1 -c 'SELECT current_database();'
    if ($LASTEXITCODE -ne 0 -or $identity.Trim() -cne $targetDatabase) {
        throw 'Local integration target verification failed.'
    }

    Push-Location $repoRoot
    try {
        & $PsqlPath -X -w -h $targetHost -p $Port -U $postgresUser -d $targetDatabase -v ON_ERROR_STOP=1 -f $temporaryScript
        if ($LASTEXITCODE -ne 0) {
            throw 'W12 canonical integration seed failed; PostgreSQL rolled back the transaction.'
        }
    }
    finally {
        Pop-Location
    }
}
finally {
    Remove-Item -LiteralPath $temporaryScript -Force -ErrorAction SilentlyContinue
}
