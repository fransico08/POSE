[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateScript({ Test-Path -LiteralPath $_ -PathType Leaf })]
    [string]$InputPath,

    [string]$ReportPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$Taxonomy = [ordered]@{
    INTENT = @('ASK_PRICE', 'ASK_INFO', 'BOOKING', 'PURCHASE_READY', 'COMPLAINT', 'SUPPORT', 'OTHER')
    SENTIMENT = @('POSITIVE', 'NEUTRAL', 'NEGATIVE')
}
$ValidStatuses = @('PROPOSED', 'ACCEPTED', 'EDITED', 'REJECTED')
$ValidSources = @('AI', 'HUMAN')

function Assert-NonEmptyString {
    param(
        [Parameter(Mandatory)]$Value,
        [Parameter(Mandatory)][string]$Name
    )

    if ($Value -isnot [string] -or [string]::IsNullOrWhiteSpace($Value)) {
        throw "$Name phải là chuỗi không rỗng."
    }
}

function Assert-Array {
    param(
        [Parameter(Mandatory)]$Value,
        [Parameter(Mandatory)][string]$Name
    )

    if ($Value -isnot [System.Array]) {
        throw "$Name phải là mảng JSON."
    }
}

function Get-SignalLabel {
    param(
        [Parameter(Mandatory)]$Signal,
        [Parameter(Mandatory)][string]$Context,
        [switch]$Candidate
    )

    Assert-NonEmptyString -Value $Signal.signalType -Name "$Context.signalType"
    Assert-NonEmptyString -Value $Signal.value -Name "$Context.value"
    $signalType = [string]$Signal.signalType
    $value = [string]$Signal.value

    if (-not $Taxonomy.Contains($signalType) -or [array]::IndexOf([string[]]$Taxonomy[$signalType], $value) -lt 0) {
        throw "$Context không thuộc taxonomy W15."
    }

    if ($Candidate) {
        Assert-NonEmptyString -Value $Signal.source -Name "$Context.source"
        Assert-NonEmptyString -Value $Signal.status -Name "$Context.status"
        $source = [string]$Signal.source
        $status = [string]$Signal.status
        if ($ValidSources -cnotcontains $source -or $ValidStatuses -cnotcontains $status) {
            throw "$Context có source hoặc status không hợp lệ."
        }
        if ($source -ceq 'HUMAN' -and $status -cne 'ACCEPTED') {
            throw "$Context HUMAN phải có status ACCEPTED theo AI-07 v0."
        }
    }

    return "$signalType|$value"
}

function Test-EligibleDecision {
    param([Parameter(Mandatory)]$Signal)

    return $Signal.source -ceq 'HUMAN' -or $Signal.status -cin @('ACCEPTED', 'EDITED')
}

$document = Get-Content -Raw -LiteralPath $InputPath | ConvertFrom-Json
if ($null -eq $document -or $document.schemaVersion -ne 1) {
    throw 'Input phải có schemaVersion = 1.'
}
Assert-Array -Value $document.cases -Name 'cases'

$caseKeys = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
$referencePairs = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
$predictionPairs = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
$labels = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
$caseCount = 0

foreach ($case in $document.cases) {
    Assert-NonEmptyString -Value $case.organizationId -Name 'organizationId'
    Assert-NonEmptyString -Value $case.messageId -Name 'messageId'
    $organizationId = [guid]::Empty
    $messageId = [guid]::Empty
    if (-not [guid]::TryParse([string]$case.organizationId, [ref]$organizationId)) {
        throw 'organizationId không phải UUID canonical.'
    }
    if (-not [guid]::TryParse([string]$case.messageId, [ref]$messageId)) {
        throw 'messageId không phải UUID canonical.'
    }
    $caseKey = "$organizationId|$messageId"
    if (-not $caseKeys.Add($caseKey)) {
        throw "Trùng khóa (organizationId, messageId): $caseKey."
    }
    Assert-Array -Value $case.reference -Name "reference[$caseKey]"
    Assert-Array -Value $case.candidates -Name "candidates[$caseKey]"

    $localReference = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    $localCandidates = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($signal in $case.reference) {
        $label = Get-SignalLabel -Signal $signal -Context "reference[$caseKey]"
        if (-not $localReference.Add($label)) {
            throw "Reference trùng trong message: $label."
        }
        [void]$labels.Add($label)
        [void]$referencePairs.Add("$caseKey|$label")
    }
    foreach ($signal in $case.candidates) {
        $label = Get-SignalLabel -Signal $signal -Context "candidates[$caseKey]" -Candidate
        $candidateKey = "$label|$($signal.source)|$($signal.status)"
        if (-not $localCandidates.Add($candidateKey)) {
            throw "Candidate trùng trong message: $candidateKey."
        }
        if (Test-EligibleDecision -Signal $signal) {
            [void]$labels.Add($label)
            [void]$predictionPairs.Add("$caseKey|$label")
        }
    }
    $caseCount++
}

$metrics = [ordered]@{}
$macroF1 = 0.0
$sortedLabels = @($labels | Sort-Object)
foreach ($label in $sortedLabels) {
    $truePositive = 0
    $falsePositive = 0
    $falseNegative = 0
    foreach ($caseKey in $caseKeys) {
        $pair = "$caseKey|$label"
        $hasReference = $referencePairs.Contains($pair)
        $hasPrediction = $predictionPairs.Contains($pair)
        if ($hasReference -and $hasPrediction) { $truePositive++ }
        elseif ($hasPrediction) { $falsePositive++ }
        elseif ($hasReference) { $falseNegative++ }
    }
    $precision = if ($truePositive + $falsePositive -eq 0) { 0.0 } else { $truePositive / [double]($truePositive + $falsePositive) }
    $recall = if ($truePositive + $falseNegative -eq 0) { 0.0 } else { $truePositive / [double]($truePositive + $falseNegative) }
    $f1 = if ($precision + $recall -eq 0) { 0.0 } else { 2 * $precision * $recall / ($precision + $recall) }
    $metrics[$label] = [ordered]@{
        truePositive = $truePositive
        falsePositive = $falsePositive
        falseNegative = $falseNegative
        f1 = [math]::Round($f1, 6)
    }
    $macroF1 += $f1
}

$result = [pscustomobject][ordered]@{
    schemaVersion = 1
    caseCount = $caseCount
    eligiblePredictionCount = $predictionPairs.Count
    referenceCount = $referencePairs.Count
    macroF1 = if ($sortedLabels.Count -eq 0) { $null } else { [math]::Round($macroF1 / $sortedLabels.Count, 6) }
    metrics = $metrics
}

if ($PSBoundParameters.ContainsKey('ReportPath')) {
    $directory = Split-Path -Parent $ReportPath
    if (-not [string]::IsNullOrWhiteSpace($directory)) {
        New-Item -ItemType Directory -Force -Path $directory | Out-Null
    }
    $result | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $ReportPath -Encoding UTF8
}

$result
