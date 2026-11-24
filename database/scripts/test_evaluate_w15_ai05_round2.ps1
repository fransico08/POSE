$ErrorActionPreference = 'Stop'
$databaseRoot = Split-Path -Parent $PSScriptRoot
$script = Join-Path $PSScriptRoot 'evaluate_w15_ai05_round2.ps1'
$fixture = Join-Path $databaseRoot 'data\newitems\w15_ai05_evaluation\fixtures\w15-ai05-evaluation-synthetic.json'

function Assert-EvaluationFailure {
    param(
        [Parameter(Mandatory)][string]$Json,
        [Parameter(Mandatory)][string]$ExpectedMessage
    )

    $temporaryFile = Join-Path $env:TEMP ("w15-ai05-evaluation-" + [guid]::NewGuid().ToString() + '.json')
    try {
        [System.IO.File]::WriteAllText($temporaryFile, $Json, (New-Object System.Text.UTF8Encoding($false)))
        try {
            & $script -InputPath $temporaryFile | Out-Null
            throw 'Expected evaluator validation failure.'
        } catch {
            if ($_.Exception.Message -notmatch $ExpectedMessage) { throw }
        }
    } finally {
        Remove-Item -LiteralPath $temporaryFile -Force -ErrorAction SilentlyContinue
    }
}

$result = & $script -InputPath $fixture
if ($result.caseCount -ne 4) { throw 'Expected four synthetic messages.' }
if ($result.eligiblePredictionCount -ne 3) { throw 'Expected three eligible predictions.' }
if ($result.referenceCount -ne 3) { throw 'Expected three reference labels.' }
if ($result.macroF1 -ne 0.555556) { throw 'Expected message-scoped macro-F1 of 0.555556.' }
if ($result.metrics['INTENT|ASK_PRICE'].truePositive -ne 1 -or $result.metrics['INTENT|ASK_PRICE'].falseNegative -ne 1) { throw 'Expected repeated ASK_PRICE to be counted per message.' }
if ($result.metrics['INTENT|SUPPORT'].falsePositive -ne 1) { throw 'Expected unsupported prediction false positive.' }

Assert-EvaluationFailure -ExpectedMessage 'taxonomy W15' -Json '{"schemaVersion":1,"cases":[{"organizationId":"00000000-0000-0000-0000-000000000001","messageId":"00000000-0000-0000-0000-000000000001","reference":[{"signalType":"INTENT","value":"INVALID"}],"candidates":[]}]}'
Assert-EvaluationFailure -ExpectedMessage 'HUMAN phải có status ACCEPTED' -Json '{"schemaVersion":1,"cases":[{"organizationId":"00000000-0000-0000-0000-000000000001","messageId":"00000000-0000-0000-0000-000000000001","reference":[],"candidates":[{"signalType":"INTENT","value":"ASK_PRICE","source":"HUMAN","status":"PROPOSED"}]}]}'
Assert-EvaluationFailure -ExpectedMessage 'Trùng khóa' -Json '{"schemaVersion":1,"cases":[{"organizationId":"00000000-0000-0000-0000-000000000001","messageId":"00000000-0000-0000-0000-000000000001","reference":[],"candidates":[]},{"organizationId":"00000000-0000-0000-0000-000000000001","messageId":"00000000-0000-0000-0000-000000000001","reference":[],"candidates":[]}]}'
Assert-EvaluationFailure -ExpectedMessage 'mảng JSON' -Json '{"schemaVersion":1,"cases":{}}'

Write-Host 'PASS: W15 AI-05 evaluator synthetic and invalid-data contract.'
