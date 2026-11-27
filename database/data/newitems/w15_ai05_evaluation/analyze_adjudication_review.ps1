[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateScript({ Test-Path -LiteralPath $_ -PathType Leaf })]
    [string]$InputPath,

    [Parameter(Mandatory)]
    [string]$OutputPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$priceTerms = @('giá', 'phí', 'tiền', 'chi trả', 'thanh toán', 'bao nhiêu')
$bookingTerms = @('đặt ', 'đặt?', 'lịch', 'nhận ngay', 'giao hàng', 'lấy hàng')
$purchaseTerms = @('mua ', 'lấy ', 'đăng ký', 'chốt')
$supportTerms = @('lỗi', 'không vào', 'không hoạt động', 'đổi ', 'hủy ')
$complaintTerms = @('sao ', 'lại ', 'không thống nhất', 'bị ép', 'chờ lâu')
$negativeTerms = @('lo', 'sợ', 'ngại', 'khó', 'không muốn', 'gấp', 'đau', 'cấp cứu', 'lỗi', 'bận')
$positiveTerms = @('cảm ơn', 'dễ hiểu', 'tốt', 'hài lòng')

function Test-ContainsAny {
    param(
        [Parameter(Mandatory)][string]$Text,
        [Parameter(Mandatory)][string[]]$Terms
    )

    foreach ($term in $Terms) {
        if ($Text.Contains($term)) {
            return $true
        }
    }

    return $false
}

function Get-DisagreementCategory {
    param([Parameter(Mandatory)]$Row)

    $intentDiffers = $Row.round_1_intent -cne $Row.round_2_intent
    $sentimentDiffers = $Row.round_1_sentiment -cne $Row.round_2_sentiment
    if ($intentDiffers -and $sentimentDiffers) {
        return 'BOTH'
    }
    if ($intentDiffers) {
        return 'INTENT_ONLY'
    }

    return 'SENTIMENT_ONLY'
}

function Get-IntentProposal {
    param([Parameter(Mandatory)][string]$Message)

    $text = $Message.ToLowerInvariant()
    $isQuestion = $text.Contains('?')

    if ($isQuestion -and (Test-ContainsAny -Text $text -Terms $priceTerms)) { return 'ASK_PRICE' }
    if (Test-ContainsAny -Text $text -Terms $bookingTerms) { return 'BOOKING' }
    if (Test-ContainsAny -Text $text -Terms $supportTerms) { return 'SUPPORT' }
    if (Test-ContainsAny -Text $text -Terms $complaintTerms) { return 'COMPLAINT' }
    if (Test-ContainsAny -Text $text -Terms $purchaseTerms) { return 'PURCHASE_READY' }
    if ($isQuestion) { return 'ASK_INFO' }

    return 'OTHER'
}

function Get-SentimentProposal {
    param([Parameter(Mandatory)][string]$Message)

    $text = $Message.ToLowerInvariant()
    if (Test-ContainsAny -Text $text -Terms $positiveTerms) { return 'POSITIVE' }
    if (Test-ContainsAny -Text $text -Terms $negativeTerms) { return 'NEGATIVE' }

    return 'NEUTRAL'
}

function Get-ReviewAssessment {
    param(
        [Parameter(Mandatory)]$Row,
        [Parameter(Mandatory)][string]$ProposedIntent,
        [Parameter(Mandatory)][string]$ProposedSentiment
    )

    $intentBoundary = @($Row.round_1_intent, $Row.round_2_intent) -contains 'ASK_INFO' -and
        @($Row.round_1_intent, $Row.round_2_intent) -contains 'OTHER'
    $sentimentBoundary = @($Row.round_1_sentiment, $Row.round_2_sentiment) -contains 'NEUTRAL' -and
        @($Row.round_1_sentiment, $Row.round_2_sentiment) -contains 'NEGATIVE'
    $contextDependent = -not [string]::IsNullOrWhiteSpace([string]$Row.prior_context) -and
        $Row.message_text -notmatch '[?]' -and $Row.message_text -match '^(ừ|uh|dạ|vậy|thế|tôi|mình|anh|chị)'
    $multiIntent = $Row.message_text -match '(?i)(nhưng|mà|vừa|còn)' -and
        ($ProposedIntent -in @('ASK_PRICE', 'ASK_INFO', 'BOOKING', 'PURCHASE_READY'))
    $unseenCandidate = @($Row.round_1_intent, $Row.round_2_intent) -notcontains $ProposedIntent -or
        @($Row.round_1_sentiment, $Row.round_2_sentiment) -notcontains $ProposedSentiment

    $reasons = [System.Collections.Generic.List[string]]::new()
    if ($intentBoundary) { [void]$reasons.Add('Ranh giới ASK_INFO/OTHER') }
    if ($sentimentBoundary) { [void]$reasons.Add('Ranh giới NEUTRAL/NEGATIVE') }
    if ($contextDependent) { [void]$reasons.Add('Phụ thuộc context trước') }
    if ($multiIntent) { [void]$reasons.Add('Có nhiều ý, chọn mục đích thúc đẩy phản hồi tiếp theo') }
    if ($unseenCandidate) { [void]$reasons.Add('Không suy diễn nhãn thứ ba ngoài hai round') }

    if ($reasons.Count -eq 0) {
        return [pscustomobject]@{
            Status = 'PROPOSED_FOR_HUMAN_REVIEW'
            Reason = 'Đề xuất theo taxonomy W13 từ nội dung và context; chưa phải nhãn phê duyệt.'
        }
    }

    return [pscustomobject]@{
        Status = 'UNCERTAIN_HUMAN_DECISION_REQUIRED'
        Reason = ($reasons -join '; ')
    }
}

function Get-ProposalRationale {
    param(
        [Parameter(Mandatory)][string]$Intent,
        [Parameter(Mandatory)][string]$Sentiment
    )

    $intentReason = switch ($Intent) {
        'ASK_PRICE' { 'Tin trực tiếp hỏi giá, phí hoặc phần chi trả.' }
        'ASK_INFO' { 'Tin trực tiếp hỏi thông tin hoặc cách thực hiện.' }
        'BOOKING' { 'Tin trực tiếp đề nghị đặt hoặc sắp lịch.' }
        'PURCHASE_READY' { 'Tin thể hiện ý định mua hoặc đăng ký đủ rõ.' }
        'COMPLAINT' { 'Tin phản ánh trải nghiệm hoặc dịch vụ không đạt kỳ vọng.' }
        'SUPPORT' { 'Tin yêu cầu xử lý sự cố hoặc thao tác.' }
        default { 'Tin là xác nhận, cảm ơn hoặc bổ sung bối cảnh.' }
    }
    $sentimentReason = switch ($Sentiment) {
        'POSITIVE' { 'Có biểu đạt hài lòng hoặc cảm ơn rõ ràng.' }
        'NEGATIVE' { 'Có biểu đạt lo ngại, khó chịu hoặc phản ánh rõ ràng.' }
        default { 'Không có sắc thái cảm xúc rõ ràng.' }
    }

    return "$intentReason $sentimentReason"
}

$rows = @(Import-Csv -LiteralPath $InputPath)
if ($rows.Count -ne 104) {
    throw "Cần đúng 104 disagreement rows, nhận được $($rows.Count)."
}
if (Test-Path -LiteralPath $OutputPath) {
    throw "Không ghi đè artifact phân tích đã tồn tại: $OutputPath"
}

$outputDirectory = Split-Path -Parent $OutputPath
if (-not [string]::IsNullOrWhiteSpace($outputDirectory) -and -not (Test-Path -LiteralPath $outputDirectory)) {
    throw "Thư mục output chưa tồn tại: $outputDirectory"
}

$analysis = foreach ($row in $rows) {
    $proposedIntent = Get-IntentProposal -Message ([string]$row.message_text)
    $proposedSentiment = Get-SentimentProposal -Message ([string]$row.message_text)
    $assessment = Get-ReviewAssessment -Row $row -ProposedIntent $proposedIntent -ProposedSentiment $proposedSentiment
    $proposalRationale = Get-ProposalRationale -Intent $proposedIntent -Sentiment $proposedSentiment
    if ($assessment.Status -eq 'UNCERTAIN_HUMAN_DECISION_REQUIRED') {
        $proposedIntent = ''
        $proposedSentiment = ''
        $proposalRationale = ''
    }

    [pscustomobject][ordered]@{
        message_id = $row.message_id
        organization_id = $row.organization_id
        conversation_id = $row.conversation_id
        sector = $row.sector
        occurred_at = $row.occurred_at
        prior_context = $row.prior_context
        message_text = $row.message_text
        round_1_intent = $row.round_1_intent
        round_1_sentiment = $row.round_1_sentiment
        round_2_intent = $row.round_2_intent
        round_2_sentiment = $row.round_2_sentiment
        disagreement_category = Get-DisagreementCategory -Row $row
        proposed_intent_for_human_review = $proposedIntent
        proposed_sentiment_for_human_review = $proposedSentiment
        proposed_rationale_for_human_review = $proposalRationale
        review_status = $assessment.Status
        ambiguity_or_review_reason = $assessment.Reason
        adjudicated_intent = ''
        adjudicated_sentiment = ''
        approved_by = ''
        approved_at = ''
        rationale = ''
    }
}

$analysis | Export-Csv -LiteralPath $OutputPath -NoTypeInformation -Encoding UTF8
Write-Output "Wrote $($analysis.Count) rows to $OutputPath"
