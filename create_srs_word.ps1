# PowerShell script to create SRS Word document using Word COM Automation
# No Python required - uses Microsoft Word directly

$ErrorActionPreference = "Stop"

try {
    Write-Host "Dang khoi dong Microsoft Word..." -ForegroundColor Cyan
    $word = New-Object -ComObject Word.Application
    $word.Visible = $false
    
    $doc = $word.Documents.Add()
    $selection = $word.Selection
    
    # ==========================================
    # HELPER FUNCTIONS
    # ==========================================
    function Set-PageMargins {
        param($doc)
        $doc.PageSetup.LeftMargin = $word.CentimetersToPoints(3.0)
        $doc.PageSetup.RightMargin = $word.CentimetersToPoints(2.0)
        $doc.PageSetup.TopMargin = $word.CentimetersToPoints(2.5)
        $doc.PageSetup.BottomMargin = $word.CentimetersToPoints(2.5)
    }
    
    function Add-Text {
        param(
            [string]$Text,
            [int]$Size = 12,
            [bool]$Bold = $false,
            [bool]$Italic = $false,
            [string]$Align = "Justify",  # Left, Center, Right, Justify
            [bool]$NewPara = $true,
            [int]$SpaceBefore = 0,
            [int]$SpaceAfter = 6
        )
        
        if ($NewPara) {
            $selection.TypeParagraph()
        }
        
        $selection.Font.Name = "Times New Roman"
        $selection.Font.Size = $Size
        $selection.Font.Bold = if($Bold) { -1 } else { 0 }
        $selection.Font.Italic = if($Italic) { -1 } else { 0 }
        
        switch ($Align) {
            "Left"    { $selection.ParagraphFormat.Alignment = 0 }  # wdAlignParagraphLeft
            "Center"  { $selection.ParagraphFormat.Alignment = 1 }  # wdAlignParagraphCenter
            "Right"   { $selection.ParagraphFormat.Alignment = 2 }  # wdAlignParagraphRight
            "Justify" { $selection.ParagraphFormat.Alignment = 3 }  # wdAlignParagraphJustify
        }
        
        $selection.ParagraphFormat.SpaceBefore = $SpaceBefore
        $selection.ParagraphFormat.SpaceAfter = $SpaceAfter
        $selection.ParagraphFormat.LineSpacingRule = 1  # wdLineSpace1pt5
        
        $selection.TypeText($Text)
    }
    
    function Add-Heading {
        param(
            [string]$Text,
            [int]$Level = 1
        )
        
        $selection.TypeParagraph()
        
        if ($Level -eq 1) {
            $selection.Style = $doc.Styles["Heading 1"]
        } elseif ($Level -eq 2) {
            $selection.Style = $doc.Styles["Heading 2"]
        } else {
            $selection.Style = $doc.Styles["Heading 3"]
        }
        
        $selection.Font.Name = "Times New Roman"
        $selection.Font.Size = if($Level -eq 1) { 13 } elseif($Level -eq 2) { 12 } else { 12 }
        $selection.Font.Bold = -1
        $selection.ParagraphFormat.Alignment = 0  # Left
        $selection.TypeText($Text)
        $selection.Style = $doc.Styles["Normal"]
        $selection.Font.Name = "Times New Roman"
        $selection.Font.Size = 12
        $selection.Font.Bold = 0
    }
    
    function Add-Bullet {
        param([string]$Text)
        $selection.TypeParagraph()
        $selection.Font.Name = "Times New Roman"
        $selection.Font.Size = 12
        $selection.Font.Bold = 0
        $selection.ParagraphFormat.Alignment = 3
        $selection.ParagraphFormat.LeftIndent = $word.CentimetersToPoints(0.75)
        $selection.ParagraphFormat.SpaceAfter = 3
        $selection.TypeText([char]0x2022 + " " + $Text)
        $selection.ParagraphFormat.LeftIndent = 0
    }
    
    function Add-PageBreak {
        $selection.InsertBreak(7)  # wdPageBreak
    }
    
    function Add-Table-Simple {
        param(
            [string[]]$Headers,
            [object[][]]$Rows,
            [string]$Caption = ""
        )
        
        if ($Caption) {
            $selection.TypeParagraph()
            $selection.Font.Name = "Times New Roman"
            $selection.Font.Size = 11
            $selection.Font.Italic = -1
            $selection.ParagraphFormat.Alignment = 1
            $selection.TypeText($Caption)
            $selection.Font.Italic = 0
        }
        
        $selection.TypeParagraph()
        
        $numCols = $Headers.Count
        $numRows = 1 + $Rows.Count
        
        $range = $selection.Range
        $table = $doc.Tables.Add($range, $numRows, $numCols)
        $table.Style = "Table Grid"
        $table.Borders.Enable = $true
        
        # Header row - blue background
        for ($c = 1; $c -le $numCols; $c++) {
            $cell = $table.Cell(1, $c)
            $cell.Shading.BackgroundPatternColor = 0x00FCDA  # RGB hex
            # Set light blue - wdColorLightBlue equivalent
            $cell.Shading.BackgroundPatternColor = -603914241  # Light Blue RGB
            $cell.Range.Font.Name = "Times New Roman"
            $cell.Range.Font.Size = 11
            $cell.Range.Font.Bold = -1
            $cell.Range.Font.Color = -721714176  # Dark Blue
            $cell.Range.ParagraphFormat.Alignment = 1  # Center
            $cell.Range.Text = $Headers[$c-1]
        }
        
        # Data rows
        for ($r = 0; $r -lt $Rows.Count; $r++) {
            $rowData = $Rows[$r]
            for ($c = 1; $c -le $numCols; $c++) {
                $cell = $table.Cell($r+2, $c)
                if ($r % 2 -eq 0) {
                    $cell.Shading.BackgroundPatternColor = -1  # White (wdColorWhite)
                } else {
                    $cell.Shading.BackgroundPatternColor = -520093697  # Very light blue
                }
                $cell.Range.Font.Name = "Times New Roman"
                $cell.Range.Font.Size = 11
                $cell.Range.Font.Bold = 0
                $cell.Range.ParagraphFormat.Alignment = 0  # Left
                if ($c -le $rowData.Count) {
                    $cell.Range.Text = [string]$rowData[$c-1]
                }
            }
        }
        
        # Move after table
        $selection.MoveDown(5, 1)  # Move after table
        $selection.TypeParagraph()
        
        return $table
    }
    
    Set-PageMargins -doc $doc
    
    Write-Host "Dang tao trang bia..." -ForegroundColor Yellow
    
    # ==========================================
    # COVER PAGE
    # ==========================================
    
    # Clear the initial empty paragraph's formatting
    $selection.ParagraphFormat.SpaceBefore = 0
    $selection.ParagraphFormat.SpaceAfter = 0
    
    Add-Text -Text "TRƯỜNG ĐẠI HỌC SƯ PHẠM KỸ THUẬT TP. HỒ CHÍ MINH" -Size 13 -Bold $true -Align "Center" -NewPara $false -SpaceAfter 2
    Add-Text -Text "KHOA ĐÀO TẠO TIÊN TIẾN" -Size 13 -Bold $true -Align "Center" -SpaceAfter 24
    Add-Text -Text "TIỂU LUẬN CHUYÊN NGÀNH (POSE)" -Size 14 -Bold $true -Align "Center" -SpaceBefore 20 -SpaceAfter 8
    Add-Text -Text "TÀI LIỆU ĐẶC TẢ YÊU CẦU PHẦN MỀM" -Size 14 -Bold $true -Align "Center" -SpaceAfter 4
    Add-Text -Text "(SOFTWARE REQUIREMENTS SPECIFICATION – SRS)" -Size 13 -Bold $false -Align "Center" -SpaceAfter 18
    Add-Text -Text "XÂY DỰNG HỆ THỐNG CRM HỖ TRỢ QUẢN LÝ KHÁCH HÀNG" -Size 15 -Bold $true -Align "Center" -SpaceAfter 4
    Add-Text -Text "CHO DOANH NGHIỆP VỪA VÀ NHỎ (SME)" -Size 15 -Bold $true -Align "Center" -SpaceAfter 30
    Add-Text -Text "GIẢNG VIÊN HƯỚNG DẪN:  TS. MAI ANH THO" -Size 12 -Bold $true -Align "Center" -SpaceAfter 14
    Add-Text -Text "NHÓM THỰC HIỆN:" -Size 12 -Bold $true -Align "Center" -SpaceAfter 4
    Add-Text -Text "Huỳnh Minh Tài  –  MSSV: 22110068" -Size 12 -Bold $false -Align "Center" -SpaceAfter 3
    Add-Text -Text "Văn Phạm Thảo Nhi  –  MSSV: 23110049" -Size 12 -Bold $false -Align "Center" -SpaceAfter 3
    Add-Text -Text "Nguyễn Đức Thắng  –  MSSV: 23110062" -Size 12 -Bold $false -Align "Center" -SpaceAfter 20
    Add-Text -Text "TP. HỒ CHÍ MINH, THÁNG 7 NĂM 2026" -Size 12 -Bold $false -Italic $true -Align "Center" -SpaceBefore 20 -SpaceAfter 0
    
    Add-PageBreak
    
    Write-Host "Dang tao muc luc..." -ForegroundColor Yellow
    
    # ==========================================
    # TABLE OF CONTENTS
    # ==========================================
    $selection.TypeParagraph()
    $selection.Font.Name = "Times New Roman"
    $selection.Font.Size = 13
    $selection.Font.Bold = -1
    $selection.ParagraphFormat.Alignment = 0
    $selection.TypeText("MỤC LỤC")
    $selection.Font.Bold = 0
    
    $tocItems = @(
        @("1.", "GIỚI THIỆU", "4"),
        @("   1.1.", "Mục đích tài liệu", "4"),
        @("   1.2.", "Phạm vi hệ thống", "4"),
        @("   1.3.", "Định nghĩa và viết tắt", "5"),
        @("   1.4.", "Tài liệu tham khảo", "5"),
        @("   1.5.", "Tổng quan tài liệu", "5"),
        @("2.", "MÔ TẢ TỔNG QUAN", "6"),
        @("   2.1.", "Bối cảnh và vấn đề", "6"),
        @("   2.2.", "Chức năng tổng quát của hệ thống", "7"),
        @("   2.3.", "Đặc điểm người dùng", "7"),
        @("   2.4.", "Giả định và phụ thuộc", "8"),
        @("3.", "YÊU CẦU CHỨC NĂNG", "9"),
        @("   3.1.", "Quản lý tài khoản và xác thực", "9"),
        @("   3.2.", "Quản lý khách hàng", "10"),
        @("   3.3.", "Quản lý hợp đồng", "11"),
        @("   3.4.", "Lịch sử giao dịch và tương tác", "12"),
        @("   3.5.", "Quản lý Pipeline cơ hội kinh doanh", "13"),
        @("   3.6.", "Báo cáo và Dashboard", "13"),
        @("   3.7.", "Thông báo và Nhắc nhở", "14"),
        @("4.", "YÊU CẦU PHI CHỨC NĂNG", "15"),
        @("5.", "MÔ HÌNH USE CASE", "17"),
        @("   5.1.", "Danh sách Actor", "17"),
        @("   5.2.", "Danh sách Use Case", "17"),
        @("   5.3.", "Mô tả Use Case chi tiết", "18"),
        @("6.", "MÔ HÌNH DỮ LIỆU (ERD)", "21"),
        @("   6.1.", "Danh sách thực thể chính", "21"),
        @("   6.2.", "Mô tả các bảng chính", "22"),
        @("7.", "KIẾN TRÚC HỆ THỐNG VÀ CÔNG NGHỆ", "24"),
        @("8.", "PHẠM VI ƯU TIÊN VÀ MVP", "26"),
        @("9.", "KẾ HOẠCH THỰC HIỆN", "27"),
        @("10.", "PHÂN CÔNG CÔNG VIỆC", "28"),
        @("11.", "RỦI RO VÀ HƯỚNG XỬ LÝ", "29"),
        @("12.", "KẾT QUẢ BÀN GIAO DỰ KIẾN", "30"),
        @("13.", "KẾT LUẬN", "31")
    )
    
    foreach ($item in $tocItems) {
        $selection.TypeParagraph()
        $selection.Font.Name = "Times New Roman"
        $selection.Font.Size = 12
        $isBold = -not ($item[0].StartsWith("   "))
        $selection.Font.Bold = if($isBold) { -1 } else { 0 }
        $selection.ParagraphFormat.Alignment = 0
        $selection.ParagraphFormat.SpaceBefore = 0
        $selection.ParagraphFormat.SpaceAfter = 1
        $selection.TypeText("$($item[0])  $($item[1])")
        $selection.Font.Bold = 0
        $selection.TypeText("`t$($item[2])")
    }
    
    Add-PageBreak
    
    Write-Host "Dang tao Chuong 1..." -ForegroundColor Yellow
    
    # ==========================================
    # CHAPTER 1: INTRODUCTION
    # ==========================================
    
    Add-Heading -Text "1. GIỚI THIỆU" -Level 1
    Add-Heading -Text "1.1. Mục đích tài liệu" -Level 2
    Add-Text -Text "Tài liệu Đặc tả Yêu cầu Phần mềm (SRS) này được xây dựng theo chuẩn IEEE 830 nhằm mô tả đầy đủ và chính xác các yêu cầu chức năng, yêu cầu phi chức năng, mô hình dữ liệu và kiến trúc của hệ thống CRM (Customer Relationship Management) hỗ trợ quản lý khách hàng cho doanh nghiệp vừa và nhỏ (SME). Đây là tài liệu nền tảng cho toàn bộ quá trình thiết kế, phát triển và kiểm thử phần mềm trong khuôn khổ Tiểu luận Chuyên ngành IT (POSE) tại Trường Đại học Sư phạm Kỹ thuật TP. Hồ Chí Minh." -NewPara $true
    Add-Text -Text "Đối tượng đọc tài liệu này bao gồm: nhóm sinh viên phát triển, giảng viên hướng dẫn và hội đồng phản biện."
    
    Add-Heading -Text "1.2. Phạm vi hệ thống" -Level 2
    Add-Text -Text "Hệ thống CRM được xây dựng mang tên CRMix – một nền tảng quản lý quan hệ khách hàng dành cho doanh nghiệp vừa và nhỏ (SME). Hệ thống giải quyết bài toán quản lý thông tin khách hàng phân tán, theo dõi hợp đồng thủ công và thiếu báo cáo tổng hợp tức thời trong các doanh nghiệp SME Việt Nam hiện nay." -NewPara $true
    Add-Text -Text "Các chức năng cốt lõi của hệ thống bao gồm:"
    Add-Bullet -Text "Quản lý thông tin khách hàng tập trung (Danh mục, phân loại, tìm kiếm nâng cao)"
    Add-Bullet -Text "Quản lý hợp đồng và vòng đời hợp đồng"
    Add-Bullet -Text "Ghi nhận lịch sử giao dịch và tương tác với khách hàng"
    Add-Bullet -Text "Quản lý cơ hội kinh doanh (Sales Pipeline)"
    Add-Bullet -Text "Báo cáo và Dashboard quản trị"
    Add-Bullet -Text "Hệ thống thông báo và nhắc nhở tự động"
    Add-Text -Text "Hệ thống hướng đến đối tượng là các doanh nghiệp SME có từ 5 đến 200 nhân viên, chưa sử dụng hoặc đang dùng Excel/giấy tờ để quản lý khách hàng."
    
    Add-Heading -Text "1.3. Định nghĩa và viết tắt" -Level 2
    
    $defHeaders = @("Thuật ngữ / Viết tắt", "Ý nghĩa")
    $defRows = @(
        @("CRM", "Customer Relationship Management – Quản lý quan hệ khách hàng"),
        @("SME", "Small and Medium Enterprise – Doanh nghiệp vừa và nhỏ"),
        @("SRS", "Software Requirements Specification – Đặc tả yêu cầu phần mềm"),
        @("IEEE 830", "Chuẩn quốc tế cho tài liệu SRS của IEEE"),
        @("FR", "Functional Requirement – Yêu cầu chức năng"),
        @("NFR", "Non-Functional Requirement – Yêu cầu phi chức năng"),
        @("UC", "Use Case – Kịch bản sử dụng"),
        @("ERD", "Entity Relationship Diagram – Sơ đồ quan hệ thực thể"),
        @("API", "Application Programming Interface – Giao diện lập trình ứng dụng"),
        @("JWT", "JSON Web Token – Cơ chế xác thực phiên đăng nhập"),
        @("RBAC", "Role-Based Access Control – Phân quyền theo vai trò"),
        @("MVP", "Minimum Viable Product – Sản phẩm khả thi tối thiểu"),
        @("UI/UX", "User Interface / User Experience – Giao diện và trải nghiệm người dùng"),
        @("CRUD", "Create, Read, Update, Delete – Các thao tác cơ bản trên dữ liệu"),
        @("Pipeline", "Luồng quản lý cơ hội kinh doanh theo từng giai đoạn")
    )
    Add-Table-Simple -Headers $defHeaders -Rows $defRows -Caption "Bảng 1.1. Định nghĩa và viết tắt"
    
    Add-Heading -Text "1.4. Tài liệu tham khảo" -Level 2
    Add-Bullet -Text "[1] IEEE Std 830-1998 – IEEE Recommended Practice for Software Requirements Specifications."
    Add-Bullet -Text "[2] Đề xuất Capstone – HRLink: Centralized Human Resource Management System (Nhóm 03, HCMUTE, 2026)."
    Add-Bullet -Text "[3] Đề xuất ý tưởng Veriflow – Huỳnh Gia Hân (MSSV: 23110019), HCMUTE, 2026."
    Add-Bullet -Text "[4] Martin, R.C. (2017). Clean Architecture: A Craftsman's Guide to Software Structure and Design."
    Add-Bullet -Text "[5] Salesforce CRM Documentation. https://help.salesforce.com"
    
    Add-Heading -Text "1.5. Tổng quan tài liệu" -Level 2
    Add-Text -Text "Tài liệu được tổ chức theo chuẩn IEEE 830 gồm 13 chương. Chương 1 giới thiệu mục đích, phạm vi và định nghĩa. Chương 2 mô tả tổng quan hệ thống. Chương 3 và 4 trình bày yêu cầu chức năng và phi chức năng. Chương 5 đến 7 cung cấp mô hình use case, ERD và kiến trúc công nghệ. Các chương còn lại trình bày kế hoạch triển khai, phân công, rủi ro và kết quả bàn giao." -NewPara $true
    
    Add-PageBreak
    
    Write-Host "Dang tao Chuong 2..." -ForegroundColor Yellow
    
    # ==========================================
    # CHAPTER 2
    # ==========================================
    Add-Heading -Text "2. MÔ TẢ TỔNG QUAN" -Level 1
    Add-Heading -Text "2.1. Bối cảnh và vấn đề cần giải quyết" -Level 2
    Add-Text -Text "Theo khảo sát thực tế, phần lớn doanh nghiệp SME tại Việt Nam hiện quản lý thông tin khách hàng bằng file Excel, sổ ghi chép hoặc trao đổi qua Zalo/email. Điều này dẫn đến nhiều bất cập nghiêm trọng trong vận hành kinh doanh." -NewPara $true
    
    $probHeaders = @("Vấn đề", "Biểu hiện thực tế", "Hệ quả")
    $probRows = @(
        @("Dữ liệu phân tán", "Thông tin KH nằm trong nhiều file Excel, email, Zalo của nhiều nhân viên khác nhau.", "Mất dữ liệu khi nhân viên nghỉ việc; không có bức tranh tổng thể về khách hàng."),
        @("Thiếu lịch sử giao dịch", "Không có nơi lưu trữ tập trung các cuộc gọi, email, hợp đồng đã ký.", "Nhân viên mới không biết lịch sử; dễ xảy ra xung đột cam kết với khách."),
        @("Quy trình thủ công", "Hợp đồng quản lý qua file Word/PDF, không có cảnh báo hết hạn hay gia hạn.", "Bỏ lỡ cơ hội gia hạn; rủi ro pháp lý khi hợp đồng hết hạn mà không biết."),
        @("Thiếu báo cáo tức thời", "Quản lý phải tổng hợp thủ công từ nhiều nguồn để có báo cáo kinh doanh.", "Ra quyết định chậm; tốn nhiều thời gian nhân lực cho công việc hành chính."),
        @("Không theo dõi cơ hội KD", "Không có quy trình chuẩn theo dõi tiến độ bán hàng từ lead đến chốt hợp đồng.", "Tỷ lệ chuyển đổi thấp; khó đo lường hiệu suất đội ngũ kinh doanh."),
        @("Phân quyền không rõ", "Mọi người đều xem được toàn bộ dữ liệu khách hàng, kể cả thông tin nhạy cảm.", "Rò rỉ thông tin; xung đột khi nhiều nhân viên cùng tiếp cận một khách hàng.")
    )
    Add-Table-Simple -Headers $probHeaders -Rows $probRows -Caption "Bảng 2.1. Các vấn đề chính cần giải quyết"
    
    Add-Heading -Text "2.2. Chức năng tổng quát của hệ thống" -Level 2
    Add-Text -Text "CRMix được thiết kế như một nền tảng web tập trung, cho phép doanh nghiệp SME quản lý toàn bộ vòng đời quan hệ khách hàng từ tiếp cận, chăm sóc, ký hợp đồng đến hậu mãi. Hệ thống cung cấp bảy nhóm chức năng cốt lõi:" -NewPara $true
    
    $funcHeaders = @("STT", "Nhóm chức năng", "Mô tả")
    $funcRows = @(
        @("1", "Quản lý tài khoản & xác thực", "Đăng ký, đăng nhập, phân quyền RBAC theo vai trò"),
        @("2", "Quản lý khách hàng", "Tạo, tra cứu, phân loại, gắn nhãn và lưu trữ thông tin khách hàng"),
        @("3", "Quản lý hợp đồng", "Tạo, theo dõi vòng đời, gia hạn và cảnh báo hết hạn hợp đồng"),
        @("4", "Lịch sử giao dịch & tương tác", "Ghi nhận mọi cuộc gọi, email, gặp mặt với khách hàng"),
        @("5", "Quản lý Pipeline (Cơ hội KD)", "Theo dõi tiến trình bán hàng theo giai đoạn Kanban"),
        @("6", "Báo cáo & Dashboard", "Biểu đồ trực quan, báo cáo tổng hợp, xuất Excel/PDF"),
        @("7", "Thông báo & Nhắc nhở", "Cảnh báo hợp đồng sắp hết hạn, nhắc cuộc hẹn, task")
    )
    Add-Table-Simple -Headers $funcHeaders -Rows $funcRows -Caption "Bảng 2.2. Nhóm chức năng tổng quát"
    
    Add-Heading -Text "2.3. Đặc điểm người dùng" -Level 2
    
    $userHeaders = @("Vai trò", "Mô tả", "Quyền truy cập chính", "Năng lực kỹ thuật")
    $userRows = @(
        @("Admin (Quản trị hệ thống)", "Quản lý toàn bộ hệ thống, tài khoản và cấu hình", "Toàn quyền: tạo/xóa tài khoản, cấu hình vai trò, xem audit log, sao lưu dữ liệu", "Có kiến thức IT cơ bản"),
        @("Sales Manager (Quản lý KD)", "Giám sát đội ngũ kinh doanh, xem báo cáo tổng thể, duyệt hợp đồng lớn", "Xem toàn bộ KH/hợp đồng, xem báo cáo tổng hợp, phân công công việc cho nhân viên", "Người dùng văn phòng thông thường"),
        @("Sales Rep (Nhân viên KD)", "Quản lý KH được phân công, tạo hợp đồng, ghi nhận tương tác", "CRUD trên KH/hợp đồng/giao dịch được phân công; xem pipeline cá nhân", "Người dùng văn phòng thông thường")
    )
    Add-Table-Simple -Headers $userHeaders -Rows $userRows -Caption "Bảng 2.3. Đặc điểm người dùng"
    
    Add-Heading -Text "2.4. Giả định và phụ thuộc" -Level 2
    Add-Bullet -Text "Hệ thống được triển khai trên môi trường web, người dùng truy cập qua trình duyệt hiện đại (Chrome, Firefox, Edge phiên bản mới nhất)."
    Add-Bullet -Text "Mỗi doanh nghiệp SME sử dụng hệ thống như một đơn vị độc lập; dữ liệu giữa các doanh nghiệp được cách ly hoàn toàn."
    Add-Bullet -Text "Môi trường mạng ổn định, tốc độ tối thiểu 1 Mbps cho người dùng cuối."
    Add-Bullet -Text "Dữ liệu được nhập chủ yếu thủ công; tích hợp import từ Excel được hỗ trợ ở mức cơ bản."
    Add-Bullet -Text "Trong phạm vi POSE, hệ thống hỗ trợ tối đa 50 người dùng đồng thời và 10.000 bản ghi khách hàng."
    Add-Bullet -Text "Hệ thống không xử lý thanh toán trực tuyến hay tích hợp với phần mềm kế toán trong phạm vi đề tài này."
    
    Add-PageBreak
    
    Write-Host "Dang tao Chuong 3 (Yeu cau chuc nang)..." -ForegroundColor Yellow
    
    # ==========================================
    # CHAPTER 3: FUNCTIONAL REQUIREMENTS
    # ==========================================
    Add-Heading -Text "3. YÊU CẦU CHỨC NĂNG (FUNCTIONAL REQUIREMENTS)" -Level 1
    Add-Text -Text "Mỗi yêu cầu chức năng được mã hóa theo định dạng FR-XX, trong đó XX là số thứ tự hai chữ số. Mức độ ưu tiên được phân theo ba cấp: Cao (Must Have), Trung bình (Should Have), Thấp (Could Have)." -NewPara $true
    
    Add-Heading -Text "3.1. Quản lý tài khoản và xác thực (Authentication & Authorization)" -Level 2
    $authH = @("Mã", "Tên yêu cầu", "Mô tả chi tiết", "Ưu tiên")
    $authR = @(
        @("FR-01", "Đăng ký tài khoản", "Admin tạo tài khoản mới cho nhân viên với thông tin: họ tên, email, vai trò (Admin/Manager/Sales Rep), mật khẩu khởi tạo. Hệ thống gửi email xác nhận với link kích hoạt có hiệu lực 24 giờ.", "Cao"),
        @("FR-02", "Đăng nhập / Đăng xuất", "Người dùng đăng nhập bằng email và mật khẩu. Hệ thống xác thực qua JWT, lưu refresh token. Đăng xuất hủy token phía client và server.", "Cao"),
        @("FR-03", "Quản lý vai trò (RBAC)", "Admin có thể gán/thay đổi vai trò của người dùng. Ba vai trò: Admin, Sales Manager, Sales Rep với quyền khác nhau. Hệ thống kiểm tra quyền trước mọi thao tác.", "Cao"),
        @("FR-04", "Đổi mật khẩu", "Người dùng tự đổi mật khẩu sau khi xác minh mật khẩu hiện tại. Mật khẩu mới phải đủ mạnh: tối thiểu 8 ký tự, có chữ hoa, số và ký tự đặc biệt.", "Cao"),
        @("FR-05", "Quên mật khẩu", "Gửi link đặt lại mật khẩu về email đăng ký. Link có hiệu lực 1 giờ và chỉ dùng được một lần.", "Cao"),
        @("FR-06", "Quản lý tài khoản người dùng", "Admin xem danh sách tài khoản, kích hoạt/vô hiệu hóa tài khoản, đặt lại mật khẩu, xem lịch sử đăng nhập.", "Cao")
    )
    Add-Table-Simple -Headers $authH -Rows $authR -Caption "Bảng 3.1. Yêu cầu chức năng – Tài khoản và xác thực"
    
    Add-Heading -Text "3.2. Quản lý khách hàng (Customer Management)" -Level 2
    $custH = @("Mã", "Tên yêu cầu", "Mô tả chi tiết", "Ưu tiên")
    $custR = @(
        @("FR-07", "Tạo hồ sơ khách hàng", "Nhân viên tạo hồ sơ KH mới gồm: tên KH/công ty, loại (Cá nhân/Doanh nghiệp), thông tin liên hệ (điện thoại, email, địa chỉ, website), người phụ trách, ghi chú. Hệ thống kiểm tra trùng lặp theo email/điện thoại.", "Cao"),
        @("FR-08", "Xem và tìm kiếm khách hàng", "Nhân viên xem danh sách và hồ sơ chi tiết KH được phân công. Manager xem toàn bộ. Tìm kiếm nhanh theo tên, điện thoại, email. Bộ lọc nâng cao theo loại, trạng thái, người phụ trách, ngành nghề, địa phương.", "Cao"),
        @("FR-09", "Cập nhật thông tin khách hàng", "Nhân viên phụ trách chỉnh sửa thông tin KH. Hệ thống lưu lịch sử thay đổi: trường nào thay đổi, giá trị cũ/mới, người thay đổi, thời gian.", "Cao"),
        @("FR-10", "Phân loại và gắn nhãn", "Phân loại KH theo trạng thái: Tiềm năng, Đang chăm sóc, Đã ký hợp đồng, Không tiếp tục. Gắn nhãn tùy chỉnh (tags) như: VIP, Doanh nghiệp lớn. Lọc KH theo nhãn.", "Cao"),
        @("FR-11", "Phân công khách hàng", "Manager phân công KH cho Sales Rep cụ thể. Nhân viên chỉ xem và thao tác trên KH được phân công. Lịch sử phân công được ghi lại.", "Cao"),
        @("FR-12", "Xóa / Vô hiệu hóa khách hàng", "Không xóa vật lý mà chuyển sang trạng thái Lưu trữ. Dữ liệu liên quan (hợp đồng, giao dịch) vẫn được bảo toàn. Chỉ Admin/Manager có quyền lưu trữ.", "Trung bình"),
        @("FR-13", "Import khách hàng từ Excel", "Nhân viên upload file Excel theo mẫu để import hàng loạt KH. Hệ thống kiểm tra định dạng, báo lỗi từng dòng và thống kê kết quả import.", "Trung bình"),
        @("FR-14", "Export danh sách khách hàng", "Xuất danh sách KH hiện tại theo bộ lọc đang áp dụng ra file Excel.", "Trung bình")
    )
    Add-Table-Simple -Headers $custH -Rows $custR -Caption "Bảng 3.2. Yêu cầu chức năng – Quản lý khách hàng"
    
    Add-Heading -Text "3.3. Quản lý hợp đồng (Contract Management)" -Level 2
    $contH = @("Mã", "Tên yêu cầu", "Mô tả chi tiết", "Ưu tiên")
    $contR = @(
        @("FR-15", "Tạo hợp đồng", "Nhân viên tạo hợp đồng gắn với KH gồm: mã hợp đồng, loại hợp đồng, ngày ký, ngày hiệu lực, ngày hết hạn, giá trị (VNĐ/USD), mô tả nội dung, file đính kèm (PDF/Word, tối đa 10MB).", "Cao"),
        @("FR-16", "Theo dõi trạng thái hợp đồng", "Vòng đời: Dự thảo → Chờ ký → Đang hiệu lực → Sắp hết hạn → Hết hạn → Đã thanh lý. Chuyển trạng thái có ghi nhận người thực hiện và thời gian.", "Cao"),
        @("FR-17", "Cảnh báo hợp đồng sắp hết hạn", "Hệ thống tự động cảnh báo khi hợp đồng còn 30, 15 và 7 ngày đến hạn. Thông báo qua giao diện và email đến người phụ trách.", "Cao"),
        @("FR-18", "Gia hạn hợp đồng", "Nhân viên tạo phụ lục gia hạn với thông tin ngày hết hạn mới, điều chỉnh giá trị (nếu có). Hệ thống lưu lịch sử toàn bộ các lần gia hạn.", "Cao"),
        @("FR-19", "Tìm kiếm và lọc hợp đồng", "Tìm theo mã, tên KH, trạng thái, loại hợp đồng, khoảng thời gian hết hạn, người phụ trách, giá trị. Kết quả hiển thị dạng bảng có phân trang.", "Cao"),
        @("FR-20", "Xem chi tiết hợp đồng", "Hiển thị đầy đủ thông tin hợp đồng, danh sách phụ lục/gia hạn, file đính kèm và lịch sử thay đổi trạng thái.", "Cao"),
        @("FR-21", "Hủy hợp đồng", "Chỉ Admin/Manager có quyền hủy hợp đồng dự thảo hoặc chưa hiệu lực. Hợp đồng đang hiệu lực chỉ được chuyển sang Đã thanh lý, không xóa vật lý.", "Trung bình")
    )
    Add-Table-Simple -Headers $contH -Rows $contR -Caption "Bảng 3.3. Yêu cầu chức năng – Quản lý hợp đồng"
    
    Add-Heading -Text "3.4. Quản lý lịch sử giao dịch và tương tác (Activity Log)" -Level 2
    $actH = @("Mã", "Tên yêu cầu", "Mô tả chi tiết", "Ưu tiên")
    $actR = @(
        @("FR-22", "Ghi nhận hoạt động tương tác", "Nhân viên ghi lại mọi tương tác với KH theo loại: Cuộc gọi điện thoại, Email, Gặp mặt/Demo, Hội nghị online, Khác. Thông tin: loại, ngày giờ, thời lượng, nội dung tóm tắt, kết quả, bước tiếp theo.", "Cao"),
        @("FR-23", "Xem timeline hoạt động KH", "Hiển thị toàn bộ lịch sử tương tác của KH theo dạng timeline (mới nhất ở trên). Lọc theo loại hoạt động và khoảng thời gian.", "Cao"),
        @("FR-24", "Cập nhật và xóa hoạt động", "Nhân viên chỉnh sửa hoạt động do mình tạo (trong vòng 24 giờ). Manager có thể sửa/xóa mọi hoạt động. Lịch sử chỉnh sửa được ghi lại.", "Trung bình"),
        @("FR-25", "Ghi nhận giao dịch tài chính", "Ghi nhận các giao dịch thanh toán liên quan đến hợp đồng: số tiền, ngày thanh toán, phương thức, trạng thái (Đã TT/Chờ TT/Trễ hạn), ghi chú.", "Cao"),
        @("FR-26", "Lên lịch cuộc hẹn / Task", "Tạo cuộc hẹn hoặc task với KH: tiêu đề, mô tả, ngày giờ, người phụ trách, nhắc nhở trước (15 phút, 1 giờ, 1 ngày). Hiển thị trên calendar view.", "Trung bình")
    )
    Add-Table-Simple -Headers $actH -Rows $actR -Caption "Bảng 3.4. Yêu cầu chức năng – Lịch sử giao dịch và tương tác"
    
    Add-Heading -Text "3.5. Quản lý cơ hội kinh doanh (Sales Pipeline)" -Level 2
    $pipH = @("Mã", "Tên yêu cầu", "Mô tả chi tiết", "Ưu tiên")
    $pipR = @(
        @("FR-27", "Tạo cơ hội kinh doanh", "Tạo opportunity gắn với KH gồm: tên cơ hội, giá trị ước tính, ngày dự kiến chốt, xác suất thành công (%), giai đoạn hiện tại, nguồn lead (Referral/Website/Cold Call...), người phụ trách.", "Cao"),
        @("FR-28", "Quản lý giai đoạn Pipeline", "Giai đoạn mặc định: Lead → Qualify → Proposal → Negotiation → Closed Won / Closed Lost. Manager có thể tùy chỉnh tên và số lượng giai đoạn.", "Cao"),
        @("FR-29", "Kanban Board Pipeline", "Hiển thị các opportunity dạng thẻ Kanban theo giai đoạn. Kéo thả thẻ để chuyển giai đoạn. Mỗi thẻ hiển thị: tên KH, giá trị, ngày dự kiến chốt, người phụ trách.", "Cao"),
        @("FR-30", "Cập nhật và theo dõi Opportunity", "Cập nhật thông tin, giai đoạn, thêm ghi chú tiến độ, đính kèm tài liệu. Xem lịch sử thay đổi giai đoạn với lý do chuyển.", "Trung bình"),
        @("FR-31", "Báo cáo Pipeline", "Tổng hợp: tổng giá trị theo giai đoạn, tỷ lệ thắng/thua, thời gian trung bình mỗi giai đoạn, dự báo doanh thu theo tháng.", "Trung bình")
    )
    Add-Table-Simple -Headers $pipH -Rows $pipR -Caption "Bảng 3.5. Yêu cầu chức năng – Sales Pipeline"
    
    Add-Heading -Text "3.6. Báo cáo và Dashboard" -Level 2
    $rptH = @("Mã", "Tên yêu cầu", "Mô tả chi tiết", "Ưu tiên")
    $rptR = @(
        @("FR-32", "Dashboard tổng quan", "Trang chủ sau đăng nhập hiển thị: KPI tổng số KH, hợp đồng đang hiệu lực, tổng giá trị hợp đồng tháng hiện tại, số opportunity đang mở. Biểu đồ KH mới theo tháng, phân bổ trạng thái KH. Widget: HĐ sắp hết hạn (7 ngày), cuộc hẹn hôm nay.", "Cao"),
        @("FR-33", "Báo cáo khách hàng", "Báo cáo: tổng số theo trạng thái/loại/người phụ trách, KH mới theo kỳ, phân bố theo địa phương/ngành nghề. Bộ lọc theo khoảng thời gian. Xuất Excel/PDF.", "Cao"),
        @("FR-34", "Báo cáo hợp đồng", "Báo cáo: tổng số và giá trị theo trạng thái, HĐ sắp hết hạn (30/15/7 ngày), HĐ mới và gia hạn theo kỳ, top KH theo giá trị HĐ. Xuất Excel/PDF.", "Cao"),
        @("FR-35", "Báo cáo doanh số bán hàng", "Báo cáo hiệu suất: tỷ lệ chốt deal theo nhân viên/giai đoạn, doanh thu thực tế vs. dự báo theo tháng/quý, thời gian trung bình từ lead đến close. Xuất Excel.", "Trung bình")
    )
    Add-Table-Simple -Headers $rptH -Rows $rptR -Caption "Bảng 3.6. Yêu cầu chức năng – Báo cáo và Dashboard"
    
    Add-Heading -Text "3.7. Thông báo và Nhắc nhở" -Level 2
    $notH = @("Mã", "Tên yêu cầu", "Mô tả chi tiết", "Ưu tiên")
    $notR = @(
        @("FR-36", "Thông báo trong hệ thống", "Chuông thông báo trên giao diện với badge số chưa đọc. Các loại: HĐ sắp hết hạn, cuộc hẹn sắp đến, task được phân công, KH mới được giao. Đánh dấu đã đọc / xóa thông báo.", "Cao"),
        @("FR-37", "Thông báo qua Email", "Gửi email tự động: HĐ hết hạn (trước 30/15/7 ngày), nhắc cuộc hẹn (trước 1 giờ), tổng kết tuần (mỗi thứ Hai). Người dùng tự cấu hình loại thông báo muốn nhận.", "Trung bình"),
        @("FR-38", "Cấu hình thông báo cá nhân", "Người dùng bật/tắt từng loại thông báo trong hệ thống và email. Admin cài đặt ngưỡng cảnh báo mặc định cho toàn hệ thống.", "Thấp")
    )
    Add-Table-Simple -Headers $notH -Rows $notR -Caption "Bảng 3.7. Yêu cầu chức năng – Thông báo và nhắc nhở"
    
    Add-PageBreak
    
    Write-Host "Dang tao Chuong 4 (YC phi chuc nang)..." -ForegroundColor Yellow
    
    # ==========================================
    # CHAPTER 4: NON-FUNCTIONAL REQUIREMENTS
    # ==========================================
    Add-Heading -Text "4. YÊU CẦU PHI CHỨC NĂNG (NON-FUNCTIONAL REQUIREMENTS)" -Level 1
    
    Add-Heading -Text "4.1. Hiệu năng (Performance)" -Level 2
    $nfr1H = @("Mã", "Yêu cầu", "Tiêu chí đo lường")
    $nfr1R = @(
        @("NFR-01", "Thời gian phản hồi trang", "Tối đa 2 giây cho các trang chính với tối đa 50 người dùng đồng thời trong điều kiện mạng bình thường."),
        @("NFR-02", "Thời gian tải danh sách", "Danh sách tối đa 1.000 bản ghi tải trong tối đa 3 giây với phân trang 20 bản ghi/trang."),
        @("NFR-03", "Thời gian xuất báo cáo", "Xuất báo cáo tối đa 500 bản ghi trong tối đa 10 giây."),
        @("NFR-04", "Xử lý đồng thời", "Hệ thống xử lý tối đa 50 người dùng đồng thời mà không giảm hiệu năng đáng kể (phạm vi POSE).")
    )
    Add-Table-Simple -Headers $nfr1H -Rows $nfr1R -Caption "Bảng 4.1. Yêu cầu phi chức năng – Hiệu năng"
    
    Add-Heading -Text "4.2. Bảo mật (Security)" -Level 2
    $nfr2H = @("Mã", "Yêu cầu", "Mô tả")
    $nfr2R = @(
        @("NFR-05", "Xác thực và phân quyền", "Mọi API đều yêu cầu JWT hợp lệ. Phân quyền RBAC kiểm tra tại tầng backend trước mọi thao tác dữ liệu."),
        @("NFR-06", "Mã hóa mật khẩu", "Mật khẩu được hash bằng BCrypt với salt factor tối thiểu 12 trước khi lưu vào database."),
        @("NFR-07", "Bảo vệ API", "Áp dụng rate limiting: tối đa 100 request/phút/IP. Chặn SQL injection và XSS bằng ORM và input validation."),
        @("NFR-08", "HTTPS", "Toàn bộ giao tiếp client-server qua HTTPS (TLS 1.2+) trong môi trường production."),
        @("NFR-09", "Nhật ký bảo mật (Audit Log)", "Ghi lại mọi thao tác nhạy cảm: đăng nhập/đăng xuất, thay đổi quyền, xóa dữ liệu, xuất báo cáo. Log gồm: user, IP, timestamp, thao tác, đối tượng bị ảnh hưởng."),
        @("NFR-10", "Session Management", "JWT access token hết hạn sau 15 phút. Refresh token hết hạn sau 7 ngày. Đăng xuất hủy refresh token phía server.")
    )
    Add-Table-Simple -Headers $nfr2H -Rows $nfr2R -Caption "Bảng 4.2. Yêu cầu phi chức năng – Bảo mật"
    
    Add-Heading -Text "4.3. Khả năng sử dụng (Usability)" -Level 2
    $nfr3H = @("Mã", "Yêu cầu", "Mô tả")
    $nfr3R = @(
        @("NFR-11", "Giao diện tiếng Việt", "Toàn bộ giao diện, thông báo lỗi và nhãn dữ liệu hiển thị bằng tiếng Việt rõ ràng."),
        @("NFR-12", "Responsive Design", "Giao diện tương thích với màn hình Desktop (1280px trở lên), Tablet (768-1279px) và Mobile (dưới 768px)."),
        @("NFR-13", "Thông báo lỗi thân thiện", "Mọi lỗi người dùng được hiển thị rõ ràng, chỉ đúng trường lỗi, không lộ stack trace kỹ thuật."),
        @("NFR-14", "Tính nhất quán UI", "Sử dụng thư viện component thống nhất. Màu sắc, font chữ, spacing nhất quán toàn hệ thống.")
    )
    Add-Table-Simple -Headers $nfr3H -Rows $nfr3R -Caption "Bảng 4.3. Yêu cầu phi chức năng – Khả năng sử dụng"
    
    Add-Heading -Text "4.4. Độ tin cậy và Tính sẵn sàng (Reliability & Availability)" -Level 2
    $nfr4H = @("Mã", "Yêu cầu", "Mô tả")
    $nfr4R = @(
        @("NFR-15", "Sao lưu dữ liệu", "Tự động backup database hàng ngày. Giữ lại tối thiểu 7 bản backup gần nhất. Có tài liệu hướng dẫn khôi phục."),
        @("NFR-16", "Xử lý lỗi graceful", "Khi service gặp lỗi, hệ thống trả về thông báo lỗi thân thiện thay vì crash. Ghi log lỗi đầy đủ để debug."),
        @("NFR-17", "Tính sẵn sàng demo", "Môi trường demo đảm bảo uptime tối thiểu 95% trong thời gian bảo vệ đề tài.")
    )
    Add-Table-Simple -Headers $nfr4H -Rows $nfr4R -Caption "Bảng 4.4. Yêu cầu phi chức năng – Độ tin cậy"
    
    Add-Heading -Text "4.5. Khả năng bảo trì và mở rộng (Maintainability & Scalability)" -Level 2
    $nfr5H = @("Mã", "Yêu cầu", "Mô tả")
    $nfr5R = @(
        @("NFR-18", "Kiến trúc module hóa", "Backend tổ chức theo layered architecture (Controller – Service – Repository). Module hóa theo domain (Customer, Contract, Pipeline...) để dễ bảo trì và mở rộng."),
        @("NFR-19", "Code coverage", "Unit test coverage tối thiểu 60% cho business logic layer. Integration test cho các API chính."),
        @("NFR-20", "Tài liệu API", "Toàn bộ REST API được tài liệu hóa bằng Swagger/OpenAPI 3.0, có thể test trực tiếp."),
        @("NFR-21", "Môi trường Docker", "Hệ thống được đóng gói bằng Docker Compose để dễ dàng triển khai trên môi trường khác nhau.")
    )
    Add-Table-Simple -Headers $nfr5H -Rows $nfr5R -Caption "Bảng 4.5. Yêu cầu phi chức năng – Khả năng bảo trì"
    
    Add-PageBreak
    
    Write-Host "Dang tao Chuong 5 (Use Case)..." -ForegroundColor Yellow
    
    # ==========================================
    # CHAPTER 5: USE CASE
    # ==========================================
    Add-Heading -Text "5. MÔ HÌNH USE CASE" -Level 1
    
    Add-Heading -Text "5.1. Danh sách Actor" -Level 2
    $actorH = @("Actor", "Loại", "Mô tả")
    $actorR = @(
        @("Admin", "Primary Actor", "Quản trị hệ thống: quản lý tài khoản, vai trò, cấu hình hệ thống, xem audit log."),
        @("Sales Manager", "Primary Actor", "Quản lý kinh doanh: xem toàn bộ dữ liệu, phân công, duyệt, xem báo cáo tổng hợp."),
        @("Sales Rep", "Primary Actor", "Nhân viên kinh doanh: quản lý KH được phân công, tạo hợp đồng, ghi tương tác."),
        @("Email Server", "Secondary Actor", "Hệ thống bên ngoài gửi email thông báo và cảnh báo tự động."),
        @("Hệ thống Scheduler", "Secondary Actor", "Tác nhân nội bộ kích hoạt các tác vụ định kỳ: kiểm tra HĐ sắp hết hạn, gửi nhắc nhở.")
    )
    Add-Table-Simple -Headers $actorH -Rows $actorR -Caption "Bảng 5.1. Danh sách Actor"
    
    Add-Heading -Text "5.2. Danh sách Use Case" -Level 2
    $ucH = @("Mã UC", "Tên Use Case", "Actor chính", "Ưu tiên")
    $ucR = @(
        @("UC-01", "Đăng nhập hệ thống", "Tất cả", "Cao"),
        @("UC-02", "Quản lý tài khoản người dùng", "Admin", "Cao"),
        @("UC-03", "Phân quyền vai trò", "Admin", "Cao"),
        @("UC-04", "Tạo và quản lý khách hàng", "Sales Rep, Manager", "Cao"),
        @("UC-05", "Phân công khách hàng", "Sales Manager", "Cao"),
        @("UC-06", "Tìm kiếm và lọc khách hàng", "Sales Rep, Manager", "Cao"),
        @("UC-07", "Tạo và quản lý hợp đồng", "Sales Rep, Manager", "Cao"),
        @("UC-08", "Theo dõi hợp đồng sắp hết hạn", "Hệ thống, Manager", "Cao"),
        @("UC-09", "Gia hạn hợp đồng", "Sales Rep, Manager", "Cao"),
        @("UC-10", "Ghi nhận hoạt động tương tác", "Sales Rep", "Cao"),
        @("UC-11", "Xem timeline khách hàng", "Sales Rep, Manager", "Cao"),
        @("UC-12", "Quản lý Pipeline cơ hội KD", "Sales Rep, Manager", "Cao"),
        @("UC-13", "Xem Dashboard tổng quan", "Sales Manager, Admin", "Cao"),
        @("UC-14", "Xem và xuất báo cáo", "Sales Manager, Admin", "Cao"),
        @("UC-15", "Nhận và quản lý thông báo", "Tất cả", "Trung bình"),
        @("UC-16", "Import khách hàng từ Excel", "Sales Rep, Manager", "Trung bình"),
        @("UC-17", "Quản lý lịch hẹn và task", "Sales Rep", "Trung bình"),
        @("UC-18", "Xem audit log hệ thống", "Admin", "Trung bình")
    )
    Add-Table-Simple -Headers $ucH -Rows $ucR -Caption "Bảng 5.2. Danh sách Use Case"
    
    Add-Heading -Text "5.3. Mô tả Use Case chi tiết (các UC ưu tiên cao)" -Level 2
    
    Add-Heading -Text "UC-04: Tạo và quản lý hồ sơ khách hàng" -Level 3
    $uc4H = @("Thuộc tính", "Nội dung")
    $uc4R = @(
        @("Mã Use Case", "UC-04"),
        @("Actor chính", "Sales Rep, Sales Manager"),
        @("Mô tả", "Cho phép nhân viên kinh doanh tạo hồ sơ KH mới, cập nhật thông tin và quản lý trạng thái KH trong vòng đời kinh doanh."),
        @("Điều kiện tiên quyết", "Người dùng đã đăng nhập và có vai trò Sales Rep hoặc Sales Manager."),
        @("Luồng chính", "1. Nhân viên chọn 'Thêm khách hàng mới'." + [char]13 + "2. Nhập thông tin: tên, loại (Cá nhân/Doanh nghiệp), điện thoại, email, địa chỉ, ngành nghề, ghi chú." + [char]13 + "3. Hệ thống kiểm tra trùng lặp theo email/điện thoại." + [char]13 + "4. Nếu không trùng: lưu hồ sơ, tự động gán cho người tạo, hiển thị xác nhận." + [char]13 + "5. Nhân viên được chuyển đến trang chi tiết KH vừa tạo."),
        @("Luồng thay thế", "3a. Hệ thống phát hiện trùng lặp: hiển thị danh sách KH có thể trùng, hỏi người dùng xác nhận tạo mới hay xem hồ sơ cũ."),
        @("Luồng ngoại lệ", "2a. Dữ liệu bắt buộc bị thiếu (tên, điện thoại): hiển thị lỗi tại trường tương ứng, không lưu."),
        @("Hậu điều kiện", "Hồ sơ KH được tạo thành công và xuất hiện trong danh sách KH của nhân viên."),
        @("Yêu cầu đặc biệt", "Kiểm tra trùng lặp phải chạy theo thời gian thực khi người dùng nhập email/điện thoại.")
    )
    Add-Table-Simple -Headers $uc4H -Rows $uc4R -Caption "Bảng 5.3. Mô tả chi tiết UC-04"
    
    Add-Heading -Text "UC-07: Tạo và quản lý hợp đồng" -Level 3
    $uc7H = @("Thuộc tính", "Nội dung")
    $uc7R = @(
        @("Mã Use Case", "UC-07"),
        @("Actor chính", "Sales Rep, Sales Manager"),
        @("Mô tả", "Nhân viên tạo hợp đồng mới gắn với KH, upload file đính kèm, theo dõi vòng đời và chuyển trạng thái hợp đồng."),
        @("Điều kiện tiên quyết", "Người dùng đã đăng nhập. KH liên quan đã tồn tại trong hệ thống."),
        @("Luồng chính", "1. Nhân viên vào trang KH, chọn 'Tạo hợp đồng'." + [char]13 + "2. Nhập thông tin: mã HĐ, loại, ngày ký, ngày hiệu lực, ngày hết hạn, giá trị, mô tả." + [char]13 + "3. Upload file HĐ (PDF/Word, tối đa 10MB)." + [char]13 + "4. Hệ thống lưu HĐ ở trạng thái 'Dự thảo'." + [char]13 + "5. Nhân viên chuyển sang 'Chờ ký' khi đã gửi cho KH." + [char]13 + "6. Khi HĐ được ký, chuyển sang 'Đang hiệu lực'."),
        @("Luồng ngoại lệ", "2a. Ngày hết hạn trước ngày hiệu lực: báo lỗi tại trường ngày hết hạn."),
        @("Hậu điều kiện", "Hợp đồng được tạo, lưu trữ và hiển thị trong hồ sơ KH tương ứng.")
    )
    Add-Table-Simple -Headers $uc7H -Rows $uc7R -Caption "Bảng 5.4. Mô tả chi tiết UC-07"
    
    Add-Heading -Text "UC-08: Cảnh báo hợp đồng sắp hết hạn (Tự động)" -Level 3
    $uc8H = @("Thuộc tính", "Nội dung")
    $uc8R = @(
        @("Mã Use Case", "UC-08"),
        @("Actor chính", "Hệ thống Scheduler, Sales Manager"),
        @("Mô tả", "Hệ thống tự động kiểm tra hàng ngày và gửi cảnh báo đến nhân viên phụ trách khi HĐ còn 30, 15 hoặc 7 ngày đến hạn."),
        @("Điều kiện tiên quyết", "Hợp đồng ở trạng thái 'Đang hiệu lực'. Scheduler được cấu hình chạy lúc 8:00 sáng hàng ngày."),
        @("Luồng chính", "1. Scheduler kích hoạt lúc 8:00 sáng." + [char]13 + "2. Hệ thống truy vấn tất cả HĐ 'Đang hiệu lực' có ngày hết hạn trong 30/15/7 ngày tới." + [char]13 + "3. Tạo thông báo trong hệ thống cho nhân viên phụ trách và Manager." + [char]13 + "4. Gửi email cảnh báo đến nhân viên phụ trách (nếu chưa gửi trong ngày)." + [char]13 + "5. Cập nhật trạng thái HĐ sang 'Sắp hết hạn' nếu còn tối đa 7 ngày." + [char]13 + "6. Ghi log kết quả xử lý."),
        @("Hậu điều kiện", "Nhân viên phụ trách nhận được thông báo trong hệ thống và email. HĐ được đánh dấu 'Sắp hết hạn'.")
    )
    Add-Table-Simple -Headers $uc8H -Rows $uc8R -Caption "Bảng 5.5. Mô tả chi tiết UC-08"
    
    Add-PageBreak
    
    Write-Host "Dang tao Chuong 6 (ERD)..." -ForegroundColor Yellow
    
    # ==========================================
    # CHAPTER 6: DATA MODEL
    # ==========================================
    Add-Heading -Text "6. MÔ HÌNH DỮ LIỆU (ERD – Entity Relationship Diagram)" -Level 1
    
    Add-Heading -Text "6.1. Danh sách thực thể chính" -Level 2
    $entH = @("Thực thể (Entity)", "Tên bảng", "Mô tả")
    $entR = @(
        @("User", "users", "Tài khoản người dùng hệ thống: thông tin cá nhân, vai trò, trạng thái."),
        @("Role", "roles", "Vai trò trong hệ thống: Admin, Sales Manager, Sales Rep."),
        @("Customer", "customers", "Hồ sơ khách hàng: cá nhân hoặc doanh nghiệp, thông tin liên hệ, phân loại."),
        @("CustomerTag", "customer_tags", "Nhãn gắn với KH để phân loại linh hoạt (Many-to-Many)."),
        @("Contract", "contracts", "Hợp đồng kinh doanh gắn với KH: thời hạn, giá trị, trạng thái."),
        @("ContractAttachment", "contract_attachments", "File đính kèm của hợp đồng (PDF, Word...)."),
        @("Activity", "activities", "Lịch sử tương tác với KH: cuộc gọi, email, gặp mặt..."),
        @("Transaction", "transactions", "Giao dịch tài chính liên quan đến hợp đồng."),
        @("Opportunity", "opportunities", "Cơ hội kinh doanh theo giai đoạn Pipeline."),
        @("PipelineStage", "pipeline_stages", "Cấu hình các giai đoạn trong Sales Pipeline."),
        @("Appointment", "appointments", "Lịch hẹn và task với KH."),
        @("Notification", "notifications", "Thông báo trong hệ thống cho người dùng."),
        @("AuditLog", "audit_logs", "Nhật ký thao tác bảo mật: ai làm gì, lúc nào, trên dữ liệu nào.")
    )
    Add-Table-Simple -Headers $entH -Rows $entR -Caption "Bảng 6.1. Danh sách thực thể chính"
    
    Add-Heading -Text "6.2. Mô tả các bảng chính" -Level 2
    
    Add-Heading -Text "6.2.1. Bảng users" -Level 3
    $usrH = @("Cột", "Kiểu dữ liệu", "Ràng buộc", "Mô tả")
    $usrR = @(
        @("id", "BIGINT", "PK, AUTO_INCREMENT", "Khóa chính tự tăng"),
        @("full_name", "VARCHAR(100)", "NOT NULL", "Họ và tên đầy đủ"),
        @("email", "VARCHAR(150)", "NOT NULL, UNIQUE", "Địa chỉ email – dùng để đăng nhập"),
        @("password_hash", "VARCHAR(255)", "NOT NULL", "Mật khẩu đã mã hóa BCrypt"),
        @("role_id", "BIGINT", "FK -> roles.id", "Vai trò của người dùng"),
        @("is_active", "BOOLEAN", "DEFAULT TRUE", "Trạng thái kích hoạt tài khoản"),
        @("last_login_at", "TIMESTAMP", "NULLABLE", "Thời điểm đăng nhập cuối cùng"),
        @("created_at", "TIMESTAMP", "DEFAULT NOW()", "Thời điểm tạo tài khoản"),
        @("updated_at", "TIMESTAMP", "DEFAULT NOW()", "Thời điểm cập nhật cuối")
    )
    Add-Table-Simple -Headers $usrH -Rows $usrR -Caption "Bảng 6.2. Cấu trúc bảng users"
    
    Add-Heading -Text "6.2.2. Bảng customers" -Level 3
    $cstH = @("Cột", "Kiểu dữ liệu", "Ràng buộc", "Mô tả")
    $cstR = @(
        @("id", "BIGINT", "PK, AUTO_INCREMENT", "Khóa chính"),
        @("name", "VARCHAR(200)", "NOT NULL", "Tên KH hoặc tên công ty"),
        @("type", "ENUM", "NOT NULL", "Loại: INDIVIDUAL (Cá nhân) hoặc BUSINESS (Doanh nghiệp)"),
        @("phone", "VARCHAR(20)", "UNIQUE, NULLABLE", "Số điện thoại chính"),
        @("email", "VARCHAR(150)", "UNIQUE, NULLABLE", "Địa chỉ email"),
        @("address", "TEXT", "NULLABLE", "Địa chỉ đầy đủ"),
        @("industry", "VARCHAR(100)", "NULLABLE", "Ngành nghề kinh doanh"),
        @("status", "ENUM", "NOT NULL", "Trạng thái: POTENTIAL, ACTIVE, CONTRACTED, INACTIVE, ARCHIVED"),
        @("assigned_to", "BIGINT", "FK -> users.id", "Nhân viên phụ trách"),
        @("created_by", "BIGINT", "FK -> users.id", "Người tạo hồ sơ"),
        @("notes", "TEXT", "NULLABLE", "Ghi chú tổng quan"),
        @("created_at", "TIMESTAMP", "DEFAULT NOW()", "Ngày tạo"),
        @("updated_at", "TIMESTAMP", "DEFAULT NOW()", "Ngày cập nhật")
    )
    Add-Table-Simple -Headers $cstH -Rows $cstR -Caption "Bảng 6.3. Cấu trúc bảng customers"
    
    Add-Heading -Text "6.2.3. Bảng contracts" -Level 3
    $cnH = @("Cột", "Kiểu dữ liệu", "Ràng buộc", "Mô tả")
    $cnR = @(
        @("id", "BIGINT", "PK, AUTO_INCREMENT", "Khóa chính"),
        @("contract_number", "VARCHAR(50)", "NOT NULL, UNIQUE", "Mã hợp đồng (duy nhất)"),
        @("customer_id", "BIGINT", "FK -> customers.id", "Khách hàng liên quan"),
        @("contract_type", "VARCHAR(100)", "NOT NULL", "Loại hợp đồng (Dịch vụ, Mua bán, Đại lý...)"),
        @("sign_date", "DATE", "NOT NULL", "Ngày ký hợp đồng"),
        @("effective_date", "DATE", "NOT NULL", "Ngày hiệu lực"),
        @("expiry_date", "DATE", "NOT NULL", "Ngày hết hạn"),
        @("value", "DECIMAL(18,2)", "NOT NULL", "Giá trị hợp đồng"),
        @("currency", "CHAR(3)", "DEFAULT 'VND'", "Đơn vị tiền tệ (VND, USD...)"),
        @("status", "ENUM", "NOT NULL", "Trạng thái: DRAFT, PENDING, ACTIVE, EXPIRING, EXPIRED, TERMINATED"),
        @("description", "TEXT", "NULLABLE", "Mô tả nội dung hợp đồng"),
        @("assigned_to", "BIGINT", "FK -> users.id", "Nhân viên phụ trách"),
        @("created_at", "TIMESTAMP", "DEFAULT NOW()", "Ngày tạo"),
        @("updated_at", "TIMESTAMP", "DEFAULT NOW()", "Ngày cập nhật")
    )
    Add-Table-Simple -Headers $cnH -Rows $cnR -Caption "Bảng 6.4. Cấu trúc bảng contracts"
    
    Add-Heading -Text "6.3. Quan hệ giữa các thực thể" -Level 2
    $relH = @("Thực thể A", "Quan hệ", "Thực thể B", "Ghi chú")
    $relR = @(
        @("users", "1 - N", "customers", "Một nhân viên phụ trách nhiều khách hàng"),
        @("users", "1 - N", "contracts", "Một nhân viên phụ trách nhiều hợp đồng"),
        @("customers", "1 - N", "contracts", "Một KH có nhiều hợp đồng"),
        @("customers", "1 - N", "activities", "Một KH có nhiều tương tác"),
        @("customers", "1 - N", "opportunities", "Một KH có nhiều cơ hội KD"),
        @("customers", "N - N", "customer_tags", "KH gắn nhiều nhãn, một nhãn thuộc nhiều KH"),
        @("contracts", "1 - N", "contract_attachments", "Một hợp đồng có nhiều file đính kèm"),
        @("contracts", "1 - N", "transactions", "Một hợp đồng có nhiều giao dịch tài chính"),
        @("opportunities", "N - 1", "pipeline_stages", "Nhiều cơ hội KD thuộc một giai đoạn"),
        @("users", "1 - N", "notifications", "Một người dùng nhận nhiều thông báo"),
        @("users", "1 - N", "audit_logs", "Một người dùng tạo nhiều log bảo mật")
    )
    Add-Table-Simple -Headers $relH -Rows $relR -Caption "Bảng 6.5. Quan hệ giữa các thực thể"
    
    Add-PageBreak
    
    Write-Host "Dang tao Chuong 7-13..." -ForegroundColor Yellow
    
    # ==========================================
    # CHAPTER 7: ARCHITECTURE
    # ==========================================
    Add-Heading -Text "7. KIẾN TRÚC HỆ THỐNG VÀ CÔNG NGHỆ" -Level 1
    
    Add-Heading -Text "7.1. Kiến trúc tổng quan" -Level 2
    Add-Text -Text "Hệ thống CRMix được xây dựng theo kiến trúc web 3 tầng (Three-tier Architecture) kết hợp với Layered Architecture ở tầng backend. Kiến trúc này phù hợp với quy mô nhóm 3 thành viên, đảm bảo phân tách trách nhiệm rõ ràng và dễ kiểm thử." -NewPara $true
    
    $archH = @("Tầng", "Vai trò", "Công nghệ")
    $archR = @(
        @("Presentation Layer (Client)", "Giao diện người dùng, tương tác trực tiếp qua trình duyệt", "ReactJS 18 + Vite"),
        @("Application Layer (Backend)", "Xử lý business logic, kiểm tra phân quyền, tương tác DB", "Spring Boot 3.x (Java 17)"),
        @("Data Layer (Database)", "Lưu trữ dữ liệu nghiệp vụ, audit log, cấu hình", "MySQL 8.x"),
        @("File Storage", "Lưu trữ file đính kèm hợp đồng, avatar người dùng", "Local Storage (dev) / MinIO (prod)"),
        @("Scheduler", "Kích hoạt các tác vụ định kỳ (cảnh báo HĐ, gửi email)", "Spring Scheduler (tích hợp trong Backend)"),
        @("Email Service", "Gửi email thông báo và cảnh báo", "JavaMailSender + SMTP Gmail/SendGrid")
    )
    Add-Table-Simple -Headers $archH -Rows $archR -Caption "Bảng 7.1. Kiến trúc 3 tầng của hệ thống"
    Add-Text -Text "Luồng xử lý tổng quát: Người dùng thao tác trên React UI → Frontend gọi REST API đến Spring Boot Backend → Backend xác thực JWT, kiểm tra RBAC → Xử lý business logic → Tương tác với MySQL → Trả kết quả JSON về Frontend → Render giao diện." -NewPara $true
    
    Add-Heading -Text "7.2. Công nghệ đề xuất chi tiết" -Level 2
    $techH = @("Thành phần", "Công nghệ", "Phiên bản", "Lý do lựa chọn")
    $techR = @(
        @("Frontend Framework", "ReactJS + Vite", "React 18, Vite 5", "Hệ sinh thái lớn, component-based, tích hợp tốt REST API, nhóm có kinh nghiệm."),
        @("UI Component Library", "Ant Design (AntD)", "AntD 5.x", "Bộ component phong phú (Table, Form, Chart), hỗ trợ tốt cho CRUD app."),
        @("State Management", "Zustand + React Query", "Mới nhất", "Zustand cho global state, React Query cho server state và cache."),
        @("HTTP Client", "Axios", "1.x", "Hỗ trợ interceptor tốt cho JWT handling và error handling tập trung."),
        @("Backend Framework", "Spring Boot", "3.x (Java 17)", "Mature ecosystem, tích hợp sẵn Security, JPA, Mail, Scheduler."),
        @("ORM", "Spring Data JPA + Hibernate", "Spring Boot 3.x", "Giảm boilerplate SQL, hỗ trợ migration, type-safe query."),
        @("Database Migration", "Flyway", "Mới nhất", "Quản lý schema version, rollback an toàn, tích hợp tốt Spring Boot."),
        @("Authentication", "Spring Security + JWT", "Spring Security 6.x", "Industry standard, hỗ trợ RBAC, refresh token, filter chain linh hoạt."),
        @("API Documentation", "SpringDoc OpenAPI (Swagger)", "2.x", "Tự động sinh API docs từ annotation, UI test trực tiếp."),
        @("Database", "MySQL", "8.x", "Phổ biến, dễ setup, hỗ trợ tốt với Spring JPA."),
        @("Containerization", "Docker + Docker Compose", "Mới nhất", "Đóng gói môi trường nhất quán, dễ deploy demo trên cloud VM."),
        @("Unit Testing", "JUnit 5 + Mockito", "Kèm Spring Boot", "Standard testing framework cho Java Spring."),
        @("Version Control", "Git + GitHub", "-", "Quản lý code, pull request, code review giữa các thành viên.")
    )
    Add-Table-Simple -Headers $techH -Rows $techR -Caption "Bảng 7.2. Stack công nghệ đề xuất"
    
    Add-Heading -Text "7.3. Cấu trúc module Backend (Spring Boot)" -Level 2
    Add-Text -Text "Backend được tổ chức theo domain-driven package structure:" -NewPara $true
    $domains = @(
        @("com.crmix.auth", "Xử lý đăng nhập, JWT, refresh token, Spring Security config"),
        @("com.crmix.user", "Quản lý tài khoản, vai trò, phân quyền"),
        @("com.crmix.customer", "Hồ sơ KH, phân loại, gắn nhãn, phân công"),
        @("com.crmix.contract", "Hợp đồng, phụ lục, file đính kèm, vòng đời"),
        @("com.crmix.activity", "Lịch sử tương tác, lịch hẹn, task"),
        @("com.crmix.pipeline", "Sales Pipeline, giai đoạn, opportunity"),
        @("com.crmix.report", "Dashboard, báo cáo, xuất Excel/PDF"),
        @("com.crmix.notification", "Thông báo trong hệ thống, email"),
        @("com.crmix.scheduler", "Tác vụ định kỳ: kiểm tra HĐ, gửi nhắc nhở"),
        @("com.crmix.common", "Shared utilities, exceptions, response wrapper, audit log")
    )
    foreach ($d in $domains) {
        Add-Bullet -Text "$($d[0]): $($d[1])"
    }
    
    Add-PageBreak
    
    # ==========================================
    # CHAPTER 8: MVP
    # ==========================================
    Add-Heading -Text "8. PHẠM VI ƯU TIÊN VÀ MVP" -Level 1
    Add-Text -Text "Dựa trên phương pháp MoSCoW (Must Have / Should Have / Could Have / Won't Have), nhóm xác định phạm vi sản phẩm khả thi tối thiểu (MVP) như sau:" -NewPara $true
    
    $mvpH = @("Mức ưu tiên", "Tính năng", "Lý do")
    $mvpR = @(
        @("Must Have (MVP - Bắt buộc)", "Đăng nhập/Đăng xuất; Quản lý tài khoản & phân quyền RBAC; CRUD Khách hàng đầy đủ; Phân công KH cho nhân viên; CRUD Hợp đồng với vòng đời trạng thái; Cảnh báo HĐ sắp hết hạn (30/15/7 ngày); Ghi nhận hoạt động tương tác với KH; Sales Pipeline (Kanban board cơ bản); Dashboard KPI cơ bản; Audit Log bảo mật; Thông báo trong hệ thống.", "Đây là core value proposition của CRM – thiếu bất kỳ tính năng nào sẽ làm mất ý nghĩa của hệ thống."),
        @("Should Have (Nên có)", "Báo cáo KH và HĐ (xuất Excel/PDF); Import KH từ Excel; Gia hạn HĐ và phụ lục; Giao dịch tài chính HĐ; Thông báo email tự động; Lịch hẹn & Task với KH; Tìm kiếm nâng cao và bộ lọc đa điều kiện.", "Tăng đáng kể giá trị sản phẩm, thực tế cần thiết nhưng có thể trễ hơn MVP nếu thiếu thời gian."),
        @("Could Have (Có thể có)", "Báo cáo hiệu suất bán hàng chi tiết; Calendar view cho lịch hẹn; Cấu hình giai đoạn Pipeline tùy chỉnh; Export báo cáo PDF có chart; Ghi chú nhanh trên hồ sơ KH.", "Nâng cao trải nghiệm nhưng không ảnh hưởng đến chức năng cốt lõi."),
        @("Won't Have (Không trong scope POSE)", "Tích hợp phần mềm kế toán (MISA, SAP); Tích hợp Zalo/Telegram OA; Ứng dụng mobile (iOS/Android); Phân tích AI/ML (RFM, dự báo); Multi-tenant (nhiều công ty); Tích hợp VOIP/gọi điện trong app.", "Vượt quá phạm vi POSE; có thể là hướng phát triển lên Capstone Project.")
    )
    Add-Table-Simple -Headers $mvpH -Rows $mvpR -Caption "Bảng 8.1. Ma trận ưu tiên MoSCoW"
    Add-Text -Text "MVP được coi là hoàn thành khi nhóm có thể demo luồng xuyên suốt: Admin tạo tài khoản → Manager phân công KH cho Sales Rep → Sales Rep tạo hợp đồng và ghi tương tác → Hệ thống cảnh báo HĐ sắp hết hạn → Manager xem dashboard và báo cáo tổng hợp → Audit log ghi nhận toàn bộ thao tác." -NewPara $true
    
    Add-PageBreak
    
    # ==========================================
    # CHAPTER 9: PROJECT PLAN
    # ==========================================
    Add-Heading -Text "9. KẾ HOẠCH THỰC HIỆN" -Level 1
    Add-Text -Text "Kế hoạch triển khai được chia thành 7 giai đoạn trong khoảng thời gian thực hiện tiểu luận chuyên ngành POSE:" -NewPara $true
    
    $planH = @("Giai đoạn", "Tên giai đoạn", "Thời gian", "Trọng tâm", "Kết quả chính")
    $planR = @(
        @("GD 1", "Khảo sát & Phân tích", "Tuần 1-2", "Nghiên cứu yêu cầu, phỏng vấn người dùng tiềm năng (SME)", "SRS hoàn chỉnh, use case, business rules, tiêu chí nghiệm thu"),
        @("GD 2", "Thiết kế", "Tuần 3-4", "Thiết kế UI/UX, ERD, kiến trúc API, ma trận phân quyền", "Wireframe, ERD, API contract (Swagger), DB schema migration"),
        @("GD 3", "Phát triển nền tảng", "Tuần 5-7", "Xây dựng auth, phân quyền, quản lý user, KH cơ bản", "Module Auth + User + Customer hoạt động end-to-end"),
        @("GD 4", "Phát triển nghiệp vụ chính", "Tuần 8-11", "Hoàn thiện Contract, Activity, Pipeline, Dashboard", "Tất cả Must Have features hoạt động, luồng MVP chạy được"),
        @("GD 5", "Phát triển tính năng bổ sung", "Tuần 12-13", "Import/Export Excel, báo cáo, thông báo email, Scheduler", "Should Have features, hệ thống đủ để demo thực tế"),
        @("GD 6", "Kiểm thử & Tối ưu", "Tuần 14-15", "Unit test, Integration test, Security test, sửa lỗi", "Test report, bug-free core features, performance đạt NFR"),
        @("GD 7", "Triển khai & Báo cáo", "Tuần 16", "Docker hóa, deploy demo, hoàn thiện tài liệu, chuẩn bị thuyết trình", "Demo trên cloud, báo cáo hoàn chỉnh, slide thuyết trình")
    )
    Add-Table-Simple -Headers $planH -Rows $planR -Caption "Bảng 9.1. Kế hoạch triển khai 7 giai đoạn"
    
    Add-PageBreak
    
    # ==========================================
    # CHAPTER 10: TASK ASSIGNMENT
    # ==========================================
    Add-Heading -Text "10. PHÂN CÔNG CÔNG VIỆC" -Level 1
    
    $taskH = @("Thành viên", "MSSV", "Vai trò chính", "Trách nhiệm chi tiết")
    $taskR = @(
        @("Huỳnh Minh Tài", "22110068", "Backend Lead / Architecture", "Thiết kế kiến trúc hệ thống và ERD; Phát triển Backend Spring Boot: Auth, User, Customer, Contract; Thiết kế và triển khai JWT + Spring Security + RBAC; Database migration (Flyway), Audit Log; Docker Compose và triển khai cloud demo; Review code backend và viết unit test backend."),
        @("Văn Phạm Thảo Nhi", "23110049", "Frontend Lead / UI-UX", "Thiết kế Wireframe và UI/UX toàn hệ thống; Phát triển Frontend ReactJS: tất cả các module UI; Tích hợp API Frontend-Backend; State management (Zustand/React Query); Dashboard, Charts, Responsive Design; Kiểm thử UI và trải nghiệm người dùng."),
        @("Nguyễn Đức Thắng", "23110062", "Backend Dev / QA / DevOps", "Phát triển Backend: Activity, Pipeline, Report, Notification, Scheduler; Tích hợp JavaMailSender và Email Service; Viết API Documentation (Swagger); Import/Export Excel (Apache POI); Viết Integration Test và API Test (Postman); Hỗ trợ DevOps: Docker, CI/CD cơ bản; Tổng hợp báo cáo và slide thuyết trình."),
        @("Cả nhóm", "-", "Chung", "Phân tích yêu cầu và xây dựng SRS; Review code chéo (Code Review); Kiểm thử tích hợp end-to-end; Chuẩn bị dữ liệu demo và kịch bản demo; Viết báo cáo cuối và phản biện kết quả.")
    )
    Add-Table-Simple -Headers $taskH -Rows $taskR -Caption "Bảng 10.1. Phân công công việc nhóm"
    Add-Text -Text "Lưu ý: Phân công trên là định hướng ban đầu, có thể điều chỉnh linh hoạt trong quá trình thực hiện tùy theo tiến độ và khó khăn thực tế. Tất cả thành viên đều phải nắm được luồng nghiệp vụ tổng thể để phản biện hiệu quả trong buổi bảo vệ." -NewPara $true
    
    Add-Heading -Text "10.1. Tiêu chí nghiệm thu" -Level 2
    $critH = @("Loại kiểm thử", "Tiêu chí đánh giá")
    $critR = @(
        @("Kiểm thử chức năng", "Tất cả Must Have Use Case chạy đúng theo test case đã xác định từ SRS."),
        @("Kiểm thử phân quyền", "Các kịch bản truy cập đúng quyền và vượt quyền đều cho kết quả đúng."),
        @("Kiểm thử hiệu năng", "Thời gian phản hồi đạt NFR-01, NFR-02, NFR-03 với dữ liệu giả lập 500+ KH."),
        @("Kiểm thử bảo mật", "Không lộ stack trace, rate limiting hoạt động, SQL injection bị chặn."),
        @("Kiểm thử audit log", "Mọi thao tác nhạy cảm đều có log với đầy đủ thông tin (user, time, action, data)."),
        @("Demo end-to-end", "Luồng MVP từ tạo tài khoản đến xem báo cáo chạy liên tục không gián đoạn."),
        @("Code quality", "Unit test coverage tối thiểu 60% cho business logic. Không có critical bug còn mở.")
    )
    Add-Table-Simple -Headers $critH -Rows $critR -Caption "Bảng 10.2. Tiêu chí nghiệm thu"
    
    Add-PageBreak
    
    # ==========================================
    # CHAPTER 11: RISKS
    # ==========================================
    Add-Heading -Text "11. RỦI RO VÀ HƯỚNG XỬ LÝ" -Level 1
    
    $riskH = @("Mã RR", "Rủi ro", "Khả năng", "Ảnh hưởng", "Hướng xử lý")
    $riskR = @(
        @("RR-01", "Phạm vi mở rộng không kiểm soát (Scope Creep)", "Trung bình", "Cao", "Chốt MVP và feature list ngay từ đầu. Mọi thay đổi phải được nhóm thống nhất và ghi lại."),
        @("RR-02", "Tích hợp Frontend-Backend gặp lỗi không tương thích", "Trung bình", "Cao", "Xác định API contract (Swagger) trước khi code. Mock API trong giai đoạn đầu để FE và BE phát triển song song."),
        @("RR-03", "Thiếu thời gian hoàn thành tất cả tính năng", "Cao", "Trung bình", "Ưu tiên Must Have, cắt giảm Could Have. Có kế hoạch dự phòng rõ ràng cho từng giai đoạn."),
        @("RR-04", "Thành viên nhóm gặp vấn đề cá nhân", "Thấp", "Cao", "Tài liệu hóa tốt từ đầu. Tất cả code trên GitHub, thành viên khác hiểu được để tiếp nối."),
        @("RR-05", "Lỗi bảo mật nghiêm trọng (SQL injection, IDOR)", "Thấp", "Cao", "Dùng ORM (JPA) để tránh SQL thuần. Kiểm tra RBAC ở backend. Có security test trong giai đoạn 6."),
        @("RR-06", "Dữ liệu demo không đủ thực tế để thuyết phục", "Trung bình", "Trung bình", "Tạo script seed dữ liệu giả lập 200+ KH, 100+ HĐ với các trạng thái đa dạng từ sớm."),
        @("RR-07", "Môi trường deploy gặp sự cố trước buổi bảo vệ", "Thấp", "Cao", "Chạy song song local demo và cloud demo. Có bản backup Docker image sẵn sàng."),
        @("RR-08", "Hiệu năng kém khi dữ liệu lớn", "Trung bình", "Trung bình", "Thêm index cho các cột tìm kiếm thường xuyên. Phân trang cho tất cả danh sách. Tối ưu N+1 query.")
    )
    Add-Table-Simple -Headers $riskH -Rows $riskR -Caption "Bảng 11.1. Rủi ro và hướng xử lý"
    
    Add-PageBreak
    
    # ==========================================
    # CHAPTER 12: DELIVERABLES
    # ==========================================
    Add-Heading -Text "12. KẾT QUẢ BÀN GIAO DỰ KIẾN" -Level 1
    
    $delH = @("STT", "Hạng mục bàn giao", "Mô tả")
    $delR = @(
        @("1", "Tài liệu SRS (file này)", "Đặc tả yêu cầu phần mềm đầy đủ theo chuẩn IEEE 830"),
        @("2", "Wireframe / UI Mockup", "Thiết kế giao diện các màn hình chính (Figma hoặc AdobeXD)"),
        @("3", "ERD và Database Schema", "Sơ đồ ERD và script SQL/Flyway migration đầy đủ"),
        @("4", "Tài liệu API (Swagger)", "Tài liệu REST API với Swagger UI có thể test trực tiếp"),
        @("5", "Source code Frontend (ReactJS)", "Mã nguồn hoàn chỉnh trên GitHub với README hướng dẫn"),
        @("6", "Source code Backend (Spring Boot)", "Mã nguồn hoàn chỉnh trên GitHub với README hướng dẫn"),
        @("7", "Script dữ liệu giả lập (Seed Data)", "Script tạo 200+ KH, 100+ HĐ, các trạng thái đa dạng"),
        @("8", "Báo cáo kiểm thử (Test Report)", "Kết quả unit test, integration test, performance test"),
        @("9", "Môi trường demo Docker", "Docker Compose file, hướng dẫn build và chạy local"),
        @("10", "Demo cloud", "Hệ thống deployed trên cloud VM với URL truy cập công khai"),
        @("11", "Video demo", "Video 5-10 phút walkthrough các chức năng chính theo kịch bản"),
        @("12", "Báo cáo cuối & Slide thuyết trình", "Báo cáo tổng kết đề tài và slide PowerPoint cho buổi bảo vệ")
    )
    Add-Table-Simple -Headers $delH -Rows $delR -Caption "Bảng 12.1. Danh sách kết quả bàn giao"
    
    Add-PageBreak
    
    # ==========================================
    # CHAPTER 13: CONCLUSION
    # ==========================================
    Add-Heading -Text "13. KẾT LUẬN" -Level 1
    Add-Text -Text "Tài liệu SRS này trình bày đầy đủ các yêu cầu chức năng (38 FR) và yêu cầu phi chức năng (21 NFR) của hệ thống CRMix – giải pháp CRM hỗ trợ quản lý khách hàng dành cho doanh nghiệp vừa và nhỏ (SME). Hệ thống giải quyết trực tiếp bài toán thực tế của các SME Việt Nam: dữ liệu khách hàng phân tán, hợp đồng không được theo dõi chặt chẽ và thiếu công cụ báo cáo tức thời cho người quản lý." -NewPara $true
    Add-Text -Text "Về mặt kỹ thuật, đề tài thể hiện chiều sâu qua các vấn đề: thiết kế phân quyền RBAC kết hợp phạm vi dữ liệu, vòng đời hợp đồng và cơ chế cảnh báo tự động, Sales Pipeline với Kanban board, audit log bảo mật, kiến trúc module hóa và khả năng triển khai Docker. Đây là những vấn đề kỹ thuật có giá trị học thuật và thực tiễn, phù hợp với yêu cầu của Tiểu luận Chuyên ngành IT (POSE) tại trường HCMUTE."
    Add-Text -Text "Nhóm cam kết ưu tiên chất lượng kỹ thuật và một luồng nghiệp vụ hoàn chỉnh (end-to-end) thay vì cố gắng đưa quá nhiều tính năng mà không đảm bảo chất lượng. Sau khi nhận phản hồi từ giảng viên hướng dẫn, nhóm sẽ cập nhật MVP, tiêu chí nghiệm thu và kế hoạch triển khai chi tiết theo tuần."
    Add-Text -Text "Nếu có cơ hội phát triển lên Capstone Project, nhóm định hướng tích hợp thêm mô hình RFM (Recency - Frequency - Monetary) để phân loại khách hàng tự động và ứng dụng AI/ML trong gợi ý chăm sóc khách hàng phù hợp, nâng CRMix từ một CRM cơ bản thành một CRM thông minh cho doanh nghiệp SME."
    
    $selection.TypeParagraph()
    $selection.TypeParagraph()
    Add-Text -Text "— Hết tài liệu SRS —" -Size 11 -Italic $true -Align "Center" -SpaceBefore 20
    
    # ==========================================
    # SAVE
    # ==========================================
    $outputPath = "d:\POSE\SRS_CRM_SME_CRMix.docx"
    Write-Host "Dang luu file: $outputPath" -ForegroundColor Cyan
    $doc.SaveAs([ref]$outputPath, [ref]16)  # 16 = wdFormatDocumentDefault (.docx)
    $doc.Close()
    $word.Quit()
    
    # Release COM objects
    [System.Runtime.Interopservices.Marshal]::ReleaseComObject($doc) | Out-Null
    [System.Runtime.Interopservices.Marshal]::ReleaseComObject($word) | Out-Null
    [System.GC]::Collect()
    [System.GC]::WaitForPendingFinalizers()
    
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "[THANH CONG] File SRS da duoc tao!" -ForegroundColor Green
    Write-Host "Duong dan: $outputPath" -ForegroundColor Green  
    Write-Host "========================================" -ForegroundColor Green
    
} catch {
    Write-Host ""
    Write-Host "[LOI] $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Chi tiet: $($_.ScriptStackTrace)" -ForegroundColor Red
    
    # Try to close Word if it's still open
    try { 
        if ($word) { 
            $word.Quit($false)
            [System.Runtime.Interopservices.Marshal]::ReleaseComObject($word) | Out-Null
        }
    } catch {}
    
    exit 1
}
