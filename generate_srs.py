# -*- coding: utf-8 -*-
"""
SRS Generator – CRMix (Phiên bản văn xuôi, tự nhiên)
Mục tiêu: 15–20 trang, ít bảng, nhiều đoạn văn mạch lạc
"""

try:
    from docx import Document
    from docx.shared import Pt, Cm, RGBColor
    from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_LINE_SPACING
    from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL
    from docx.oxml.ns import qn
    from docx.oxml import OxmlElement
except ImportError:
    import subprocess, sys
    subprocess.check_call([sys.executable, "-m", "pip", "install", "python-docx"])
    from docx import Document
    from docx.shared import Pt, Cm, RGBColor
    from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_LINE_SPACING
    from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL
    from docx.oxml.ns import qn
    from docx.oxml import OxmlElement

doc = Document()
FONT = "Times New Roman"

# ── Cài đặt trang ────────────────────────────────────────────
s = doc.sections[0]
s.page_width  = Cm(21);   s.page_height   = Cm(29.7)
s.left_margin = Cm(3.0);  s.right_margin  = Cm(2.0)
s.top_margin  = Cm(2.5);  s.bottom_margin = Cm(2.5)

# ── Hàm tiện ích ─────────────────────────────────────────────
def rfonts(run):
    rPr = run._r.get_or_add_rPr()
    rf = OxmlElement('w:rFonts')
    for a in ('w:ascii', 'w:hAnsi', 'w:cs'):
        rf.set(qn(a), FONT)
    rPr.insert(0, rf)

def para(text="", sz=12, bold=False, italic=False,
         align=WD_ALIGN_PARAGRAPH.JUSTIFY, sb=0, sa=6,
         indent=None, color=None):
    p = doc.add_paragraph()
    p.alignment = align
    pf = p.paragraph_format
    pf.space_before = Pt(sb)
    pf.space_after  = Pt(sa)
    pf.line_spacing_rule = WD_LINE_SPACING.ONE_POINT_FIVE
    if indent is not None:
        pf.left_indent = Cm(indent)
    if text:
        r = p.add_run(text)
        r.font.name   = FONT
        r.font.size   = Pt(sz)
        r.font.bold   = bold
        r.font.italic = italic
        if color:
            r.font.color.rgb = color
        rfonts(r)
    return p

def mixed_para(parts, align=WD_ALIGN_PARAGRAPH.JUSTIFY, sb=0, sa=6):
    """
    parts = list of (text, bold, italic)
    Tạo đoạn văn với nhiều định dạng khác nhau trên cùng dòng.
    """
    p = doc.add_paragraph()
    p.alignment = align
    p.paragraph_format.space_before = Pt(sb)
    p.paragraph_format.space_after  = Pt(sa)
    p.paragraph_format.line_spacing_rule = WD_LINE_SPACING.ONE_POINT_FIVE
    for text, bold, italic in parts:
        r = p.add_run(text)
        r.font.name   = FONT
        r.font.size   = Pt(12)
        r.font.bold   = bold
        r.font.italic = italic
        rfonts(r)
    return p

def h1(text):
    para(text, sz=13, bold=True, align=WD_ALIGN_PARAGRAPH.LEFT, sb=12, sa=5)

def h2(text):
    para(text, sz=12, bold=True, align=WD_ALIGN_PARAGRAPH.LEFT, sb=8, sa=3)

def indent_para(text, sz=12):
    """Đoạn thụt đầu dòng"""
    para(text, sz=sz, indent=0.7, sb=0, sa=3)

def simple_table(caption, headers, rows, widths=None):
    """Bảng tối giản, chỉ dùng khi cần"""
    cp = doc.add_paragraph()
    cp.alignment = WD_ALIGN_PARAGRAPH.CENTER
    cp.paragraph_format.space_before = Pt(4)
    cp.paragraph_format.space_after  = Pt(2)
    r = cp.add_run(caption)
    r.font.name = FONT; r.font.size = Pt(10); r.font.italic = True
    rfonts(r)

    tbl = doc.add_table(rows=1+len(rows), cols=len(headers))
    tbl.style = 'Table Grid'
    tbl.alignment = WD_TABLE_ALIGNMENT.CENTER

    for i, h in enumerate(headers):
        c = tbl.rows[0].cells[i]
        tc = c._tc; tcPr = tc.get_or_add_tcPr()
        shd = OxmlElement('w:shd')
        shd.set(qn('w:val'),'clear'); shd.set(qn('w:color'),'auto')
        shd.set(qn('w:fill'), 'DAE8FC'); tcPr.append(shd)
        c.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
        p2 = c.paragraphs[0]; p2.alignment = WD_ALIGN_PARAGRAPH.CENTER
        run = p2.add_run(h)
        run.font.name = FONT; run.font.size = Pt(10); run.font.bold = True
        run.font.color.rgb = RGBColor(0x17, 0x37, 0x8A); rfonts(run)

    for ri, row in enumerate(rows):
        bg = 'FFFFFF' if ri % 2 == 0 else 'EEF4FF'
        cells = tbl.rows[ri+1].cells
        for ci, val in enumerate(row):
            c = cells[ci]
            tc = c._tc; tcPr = tc.get_or_add_tcPr()
            shd = OxmlElement('w:shd')
            shd.set(qn('w:val'),'clear'); shd.set(qn('w:color'),'auto')
            shd.set(qn('w:fill'), bg); tcPr.append(shd)
            c.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
            p2 = c.paragraphs[0]; p2.alignment = WD_ALIGN_PARAGRAPH.LEFT
            run = p2.add_run(str(val))
            run.font.name = FONT; run.font.size = Pt(10); rfonts(run)

    if widths:
        for row in tbl.rows:
            for i, w in enumerate(widths):
                if i < len(row.cells):
                    row.cells[i].width = Cm(w)

    doc.add_paragraph().paragraph_format.space_after = Pt(3)

def page_break():
    doc.add_page_break()

# ═══════════════════════════════════════════════════════════════════
#  TRANG BÌA
# ═══════════════════════════════════════════════════════════════════
para("TRƯỜNG ĐẠI HỌC SƯ PHẠM KỸ THUẬT TP. HỒ CHÍ MINH",
     sz=13, bold=True, align=WD_ALIGN_PARAGRAPH.CENTER, sb=0, sa=2)
para("KHOA ĐÀO TẠO TIÊN TIẾN",
     sz=13, bold=True, align=WD_ALIGN_PARAGRAPH.CENTER, sa=30)
para("TIỂU LUẬN CHUYÊN NGÀNH (POSE)",
     sz=14, bold=True, align=WD_ALIGN_PARAGRAPH.CENTER, sb=20, sa=8)
para("TÀI LIỆU ĐẶC TẢ YÊU CẦU PHẦN MỀM",
     sz=14, bold=True, align=WD_ALIGN_PARAGRAPH.CENTER, sa=3)
para("(Software Requirements Specification – SRS)",
     sz=12, italic=True, align=WD_ALIGN_PARAGRAPH.CENTER, sa=20)
para("XÂY DỰNG HỆ THỐNG CRM HỖ TRỢ QUẢN LÝ KHÁCH HÀNG",
     sz=15, bold=True, align=WD_ALIGN_PARAGRAPH.CENTER, sa=3)
para("CHO DOANH NGHIỆP VỪA VÀ NHỎ (SME)",
     sz=15, bold=True, align=WD_ALIGN_PARAGRAPH.CENTER, sa=30)
para("GIẢNG VIÊN HƯỚNG DẪN:  TS. MAI ANH THO",
     sz=12, bold=True, align=WD_ALIGN_PARAGRAPH.CENTER, sa=10)
para("NHÓM THỰC HIỆN:", sz=12, bold=True, align=WD_ALIGN_PARAGRAPH.CENTER, sa=4)
para("Huỳnh Minh Tài  –  MSSV: 22110068",
     sz=12, align=WD_ALIGN_PARAGRAPH.CENTER, sa=3)
para("Văn Phạm Thảo Nhi  –  MSSV: 23110049",
     sz=12, align=WD_ALIGN_PARAGRAPH.CENTER, sa=3)
para("Nguyễn Đức Thắng  –  MSSV: 23110062",
     sz=12, align=WD_ALIGN_PARAGRAPH.CENTER, sa=22)
para("TP. Hồ Chí Minh, tháng 7 năm 2026",
     sz=12, italic=True, align=WD_ALIGN_PARAGRAPH.CENTER, sb=20, sa=0)
page_break()

# ═══════════════════════════════════════════════════════════════════
#  MỤC LỤC
# ═══════════════════════════════════════════════════════════════════
para("MỤC LỤC", sz=13, bold=True, align=WD_ALIGN_PARAGRAPH.LEFT, sb=0, sa=6)
toc = [
    ("1.", "GIỚI THIỆU", "3"),
    ("2.", "MÔ TẢ TỔNG QUAN VÀ BỐI CẢNH", "4"),
    ("3.", "YÊU CẦU CHỨC NĂNG", "6"),
    ("4.", "YÊU CẦU PHI CHỨC NĂNG", "10"),
    ("5.", "MÔ HÌNH USE CASE", "11"),
    ("6.", "MÔ HÌNH DỮ LIỆU (ERD)", "13"),
    ("7.", "KIẾN TRÚC HỆ THỐNG VÀ CÔNG NGHỆ", "15"),
    ("8.", "KẾ HOẠCH, PHÂN CÔNG VÀ RỦI RO", "16"),
    ("9.", "KẾT LUẬN", "19"),
]
for num, title, pg in toc:
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after  = Pt(3)
    p.paragraph_format.line_spacing_rule = WD_LINE_SPACING.SINGLE
    p.alignment = WD_ALIGN_PARAGRAPH.LEFT
    r1 = p.add_run(f"{num}  {title}"); r1.font.name=FONT; r1.font.size=Pt(12); r1.font.bold=True; rfonts(r1)
    r2 = p.add_run(f"\t{pg}");        r2.font.name=FONT; r2.font.size=Pt(12); rfonts(r2)
page_break()

# ═══════════════════════════════════════════════════════════════════
#  CHƯƠNG 1: GIỚI THIỆU
# ═══════════════════════════════════════════════════════════════════
h1("1. GIỚI THIỆU")

h2("1.1. Mục đích tài liệu")
para(
    "Tài liệu này là Đặc tả Yêu cầu Phần mềm (Software Requirements Specification – SRS) "
    "được xây dựng theo chuẩn IEEE 830 cho hệ thống CRMix – một nền tảng quản lý quan hệ "
    "khách hàng (CRM) dành cho doanh nghiệp vừa và nhỏ. Mục tiêu của tài liệu là xác lập "
    "một nền tảng chung để nhóm phát triển, giảng viên hướng dẫn và hội đồng phản biện "
    "có cùng hiểu biết về phạm vi, chức năng và ràng buộc kỹ thuật của hệ thống trước khi "
    "bước vào giai đoạn thiết kế và lập trình."
)
para(
    "Tài liệu bao gồm các nội dung chính: mô tả bối cảnh và vấn đề cần giải quyết, "
    "danh sách yêu cầu chức năng và phi chức năng, mô hình use case, thiết kế dữ liệu "
    "sơ bộ, kiến trúc và công nghệ dự kiến, kế hoạch triển khai cùng phân công công việc "
    "cho nhóm ba thành viên."
)

h2("1.2. Phạm vi hệ thống")
para(
    "CRMix là một ứng dụng web hỗ trợ doanh nghiệp SME (Small and Medium Enterprise) "
    "quản lý toàn bộ vòng đời quan hệ khách hàng, từ lúc tiếp cận, chăm sóc, ký kết "
    "hợp đồng cho đến hậu mãi. Hệ thống được thiết kế cho các doanh nghiệp có quy mô "
    "từ 5 đến 200 nhân viên, hiện đang dùng Excel hoặc ghi chép thủ công để quản lý "
    "thông tin khách hàng và hợp đồng."
)
para(
    "Về mặt kỹ thuật, CRMix là một ứng dụng web chạy trên trình duyệt, không yêu cầu "
    "cài đặt phần mềm phía client. Trong phạm vi Tiểu luận Chuyên ngành (POSE), hệ thống "
    "sẽ hỗ trợ tối đa 50 người dùng đồng thời và khoảng 10.000 bản ghi khách hàng. "
    "Các tính năng nâng cao như tích hợp phần mềm kế toán, ứng dụng mobile hay phân tích "
    "AI/ML sẽ được xem xét ở giai đoạn Capstone Project."
)

h2("1.3. Thuật ngữ và viết tắt")
para(
    "Trong toàn bộ tài liệu này, một số thuật ngữ được dùng thống nhất như sau: "
    "CRM (Customer Relationship Management) là quản lý quan hệ khách hàng; "
    "SME (Small and Medium Enterprise) là doanh nghiệp vừa và nhỏ; "
    "FR (Functional Requirement) là yêu cầu chức năng; "
    "NFR (Non-Functional Requirement) là yêu cầu phi chức năng; "
    "UC (Use Case) là kịch bản sử dụng; "
    "ERD (Entity Relationship Diagram) là sơ đồ quan hệ thực thể; "
    "JWT (JSON Web Token) là cơ chế xác thực phiên đăng nhập; "
    "RBAC (Role-Based Access Control) là phân quyền theo vai trò; "
    "và MVP (Minimum Viable Product) là sản phẩm khả thi tối thiểu đủ để demo và đánh giá."
)

page_break()

# ═══════════════════════════════════════════════════════════════════
#  CHƯƠNG 2: MÔ TẢ TỔNG QUAN
# ═══════════════════════════════════════════════════════════════════
h1("2. MÔ TẢ TỔNG QUAN VÀ BỐI CẢNH")

h2("2.1. Bối cảnh và vấn đề cần giải quyết")
para(
    "Trong quá trình khảo sát thực tế, nhóm nhận thấy phần lớn doanh nghiệp SME tại Việt "
    "Nam vẫn đang quản lý thông tin khách hàng theo cách truyền thống: lưu trữ trong file "
    "Excel, sổ tay ghi chép, hoặc trao đổi qua các kênh không chính thức như Zalo và email "
    "cá nhân. Phương thức này tuy đơn giản nhưng kéo theo rất nhiều hệ quả tiêu cực trong "
    "vận hành kinh doanh thực tế."
)
para(
    "Vấn đề đầu tiên và dễ thấy nhất là dữ liệu bị phân tán. Thông tin về một khách hàng "
    "có thể nằm rải rác trong máy tính của nhiều nhân viên khác nhau, không ai có bức tranh "
    "toàn cảnh. Khi nhân viên nghỉ việc, những dữ liệu đó gần như mất đi hoàn toàn. "
    "Người mới tiếp quản không biết lịch sử giao dịch, không rõ khách hàng đã được chăm "
    "sóc như thế nào, và rất dễ mắc sai lầm trong bước đầu tiếp cận lại."
)
para(
    "Vấn đề thứ hai liên quan đến hợp đồng. Việc theo dõi ngày hết hạn hợp đồng hoàn toàn "
    "phụ thuộc vào trí nhớ của nhân viên hoặc một file Excel được cập nhật thủ công. Thực "
    "tế cho thấy không ít hợp đồng bị bỏ lỡ cơ hội gia hạn chỉ vì không có ai nhắc nhở "
    "kịp thời, gây thiệt hại cả về doanh thu lẫn mối quan hệ với khách hàng."
)
para(
    "Vấn đề thứ ba là thiếu công cụ để đánh giá hiệu suất đội ngũ bán hàng. Người quản "
    "lý muốn biết tháng này nhân viên nào đang có bao nhiêu deal đang tiến hành, cơ hội "
    "nào có khả năng chốt cao nhất, hay tỷ lệ chuyển đổi từ lead sang hợp đồng là bao "
    "nhiêu – nhưng không có công cụ nào hỗ trợ điều này. Tất cả đều phải tổng hợp thủ "
    "công, tốn thời gian và không đảm bảo độ chính xác."
)
para(
    "CRMix được xây dựng để giải quyết trực tiếp ba nhóm vấn đề trên: tập trung hóa dữ "
    "liệu khách hàng, tự động hóa giám sát vòng đời hợp đồng, và cung cấp công cụ quản "
    "lý Pipeline bán hàng kèm báo cáo tổng hợp cho người quản lý."
)

h2("2.2. Người dùng của hệ thống")
para(
    "CRMix phục vụ ba nhóm người dùng với nhu cầu và phạm vi truy cập khác nhau. "
    "Nhóm thứ nhất là Quản trị hệ thống (Admin) – người chịu trách nhiệm vận hành nền "
    "tảng: tạo và quản lý tài khoản nhân viên, phân quyền, cấu hình hệ thống và theo dõi "
    "nhật ký bảo mật. Nhóm thứ hai là Quản lý kinh doanh (Sales Manager) – người cần "
    "bức tranh tổng thể: xem báo cáo toàn doanh nghiệp, phân công khách hàng cho nhân viên, "
    "theo dõi Pipeline của cả đội và phê duyệt các thay đổi quan trọng. Nhóm thứ ba là "
    "Nhân viên kinh doanh (Sales Rep) – người dùng hàng ngày: tạo và cập nhật hồ sơ "
    "khách hàng, ghi nhận mọi tương tác, theo dõi hợp đồng của mình và cập nhật tiến "
    "độ cơ hội kinh doanh trên Pipeline."
)
para(
    "Điểm quan trọng trong thiết kế phân quyền của hệ thống là Sales Rep chỉ có thể xem "
    "và thao tác trên dữ liệu của những khách hàng được Manager phân công cho mình. "
    "Điều này tránh xung đột giữa nhân viên khi cùng tiếp cận một khách hàng, và bảo vệ "
    "thông tin nhạy cảm trong nội bộ doanh nghiệp."
)

h2("2.3. Giả định và ràng buộc")
para(
    "Tài liệu này được xây dựng dựa trên một số giả định quan trọng. Thứ nhất, người dùng "
    "cuối có kết nối Internet ổn định (tối thiểu 1 Mbps) và sử dụng trình duyệt hiện đại. "
    "Thứ hai, dữ liệu được nhập chủ yếu thủ công; chức năng import từ Excel sẽ được hỗ "
    "trợ ở mức cơ bản để giúp doanh nghiệp chuyển đổi từ Excel sang hệ thống mới dễ dàng. "
    "Thứ ba, trong phạm vi POSE, hệ thống không xử lý thanh toán trực tuyến, không tích "
    "hợp với phần mềm kế toán và không cung cấp ứng dụng mobile – những tính năng này có "
    "thể được bổ sung trong giai đoạn phát triển lên Capstone Project."
)

page_break()

# ═══════════════════════════════════════════════════════════════════
#  CHƯƠNG 3: YÊU CẦU CHỨC NĂNG
# ═══════════════════════════════════════════════════════════════════
h1("3. YÊU CẦU CHỨC NĂNG (FUNCTIONAL REQUIREMENTS)")
para(
    "Phần này mô tả chi tiết các chức năng mà hệ thống CRMix phải cung cấp. Các yêu cầu "
    "được phân nhóm theo module nghiệp vụ và gán mã FR-XX để dễ truy vết trong quá trình "
    "phát triển và kiểm thử. Mức độ ưu tiên được xác định theo phương pháp MoSCoW: "
    "\"Cao\" tương ứng Must Have (bắt buộc trong MVP), \"Trung bình\" tương ứng Should Have."
)

h2("3.1. Quản lý tài khoản và xác thực (FR-01 đến FR-05)")
para(
    "Đây là nhóm chức năng nền tảng, không có nó thì không có hệ thống. "
    "FR-01 yêu cầu Admin có khả năng tạo tài khoản mới với đầy đủ thông tin cơ bản như "
    "họ tên, email, vai trò (Admin / Sales Manager / Sales Rep) và mật khẩu tạm thời. "
    "Sau khi tạo, hệ thống tự động gửi email kích hoạt tài khoản có hiệu lực trong 24 giờ."
)
para(
    "FR-02 xử lý việc đăng nhập và đăng xuất: người dùng đăng nhập bằng email và mật khẩu, "
    "hệ thống xác thực thông qua JWT kết hợp refresh token để duy trì phiên làm việc an "
    "toàn. Khi đăng xuất, token phía server bị vô hiệu hóa ngay lập tức – đây là yêu cầu "
    "bảo mật tối thiểu để ngăn token bị tái sử dụng sau khi người dùng rời khỏi hệ thống."
)
para(
    "FR-03 là cơ chế phân quyền RBAC: mỗi vai trò có tập quyền cố định, và mọi thao tác "
    "đều được kiểm tra phía backend trước khi xử lý – frontend chỉ ẩn/hiện giao diện, "
    "không thể thay thế việc kiểm tra phía server. FR-04 và FR-05 xử lý đổi mật khẩu và "
    "quên mật khẩu: link đặt lại được gửi qua email, chỉ dùng được một lần và hết hạn "
    "sau 1 giờ. Admin cũng có thể vô hiệu hóa tài khoản nhân viên đã nghỉ việc để ngăn "
    "truy cập trái phép (FR-05)."
)

h2("3.2. Quản lý khách hàng (FR-06 đến FR-11)")
para(
    "Module này là trái tim của hệ thống CRM. FR-06 cho phép tạo hồ sơ khách hàng mới "
    "với các thông tin cơ bản: tên khách hàng hoặc tên công ty, loại khách hàng (cá nhân "
    "hay doanh nghiệp), số điện thoại, email, địa chỉ, ngành nghề hoạt động và ghi chú "
    "ban đầu. Đặc biệt, hệ thống sẽ tự động kiểm tra trùng lặp theo email và số điện "
    "thoại ngay khi người dùng nhập dữ liệu, tránh tình trạng một khách hàng bị tạo "
    "nhiều lần trong hệ thống."
)
para(
    "FR-07 đảm bảo khả năng tra cứu linh hoạt: tìm kiếm nhanh theo tên, số điện thoại "
    "hoặc email, kết hợp với bộ lọc nâng cao theo trạng thái, loại khách hàng, người "
    "phụ trách, địa phương và ngành nghề. Sales Rep chỉ thấy danh sách khách hàng được "
    "phân công cho mình, trong khi Sales Manager có thể xem toàn bộ."
)
para(
    "FR-08 yêu cầu mọi thay đổi trên hồ sơ khách hàng đều được ghi lại lịch sử đầy đủ: "
    "trường nào bị thay đổi, giá trị trước và sau khi thay đổi là gì, ai thực hiện và "
    "vào lúc nào. Điều này cực kỳ quan trọng để giải quyết tranh chấp và kiểm tra trách "
    "nhiệm trong nội bộ doanh nghiệp."
)
para(
    "FR-09 hỗ trợ phân loại khách hàng theo bốn trạng thái chính trong vòng đời quan hệ "
    "kinh doanh: Tiềm năng (mới tiếp cận), Đang chăm sóc (đang trao đổi tích cực), "
    "Đã ký hợp đồng và Không tiếp tục. Ngoài ra, nhân viên có thể gắn nhãn tùy chỉnh "
    "như VIP, Ưu tiên hay Đối tác chiến lược để phân nhóm linh hoạt hơn. "
    "FR-10 cho phép Manager phân công khách hàng cho nhân viên cụ thể, và lịch sử phân "
    "công được ghi lại để theo dõi trách nhiệm. Cuối cùng, FR-11 hỗ trợ import danh sách "
    "khách hàng từ file Excel theo mẫu chuẩn và export danh sách đang lọc ra Excel."
)

h2("3.3. Quản lý hợp đồng (FR-12 đến FR-16)")
para(
    "Đây là module giải quyết trực tiếp bài toán hợp đồng bị quản lý thủ công tại các "
    "doanh nghiệp SME. FR-12 cho phép tạo hợp đồng gắn liền với một khách hàng, bao gồm "
    "các thông tin thiết yếu: mã hợp đồng (tự sinh hoặc tùy chỉnh), loại hợp đồng, "
    "ngày ký, ngày hiệu lực, ngày hết hạn, giá trị và đơn vị tiền tệ. Nhân viên cũng "
    "có thể đính kèm file hợp đồng gốc (PDF hoặc Word) với dung lượng tối đa 10 MB."
)
para(
    "FR-13 quản lý vòng đời hợp đồng qua sáu trạng thái liên tiếp: từ Dự thảo khi mới "
    "được tạo, qua Chờ ký khi đã gửi cho khách hàng, đến Đang hiệu lực khi đã ký xong, "
    "rồi Sắp hết hạn khi còn 7 ngày, Hết hạn và cuối cùng là Đã thanh lý. Mỗi lần "
    "chuyển trạng thái đều được ghi nhận người thực hiện và thời gian."
)
para(
    "FR-14 là một trong những tính năng được đánh giá cao nhất với người dùng thực tế: "
    "hệ thống tự động cảnh báo khi hợp đồng còn 30, 15 và 7 ngày đến ngày hết hạn. "
    "Cảnh báo được gửi đồng thời qua giao diện (thông báo trong hệ thống) và qua email "
    "đến nhân viên phụ trách, đảm bảo không bỏ sót dù người dùng không mở ứng dụng."
)
para(
    "FR-15 xử lý việc gia hạn hợp đồng bằng cách tạo phụ lục gia hạn riêng, ghi nhận "
    "ngày hết hạn mới và điều chỉnh giá trị nếu cần. Toàn bộ lịch sử các lần gia hạn "
    "được lưu lại để tra cứu. FR-16 hoàn thiện module với khả năng tìm kiếm, lọc đa "
    "điều kiện và xem chi tiết toàn bộ thông tin hợp đồng bao gồm các phụ lục, file "
    "đính kèm và lịch sử thay đổi trạng thái."
)

h2("3.4. Lịch sử tương tác và giao dịch (FR-17 đến FR-20)")
para(
    "Một trong những điểm yếu lớn nhất khi quản lý bằng Excel là không có chỗ để ghi "
    "chép lịch sử giao tiếp với khách hàng một cách có hệ thống. Module này giải quyết "
    "vấn đề đó. FR-17 cho phép nhân viên ghi lại mọi hoạt động tương tác với khách hàng "
    "theo từng loại cụ thể: cuộc gọi điện thoại, email trao đổi, buổi gặp mặt trực tiếp, "
    "cuộc họp online hoặc các hình thức khác. Mỗi bản ghi bao gồm thời gian, thời lượng, "
    "nội dung tóm tắt, kết quả đạt được và bước tiếp theo cần làm."
)
para(
    "FR-18 hiển thị toàn bộ lịch sử này dưới dạng timeline – bản ghi mới nhất ở trên, "
    "cũ hơn ở dưới – giúp nhân viên mới tiếp quản khách hàng nắm bắt ngay bối cảnh mà "
    "không cần hỏi lại người cũ. FR-19 bổ sung khả năng ghi nhận các giao dịch tài chính "
    "liên quan đến hợp đồng: số tiền, ngày thanh toán, phương thức và trạng thái "
    "(Đã thanh toán, Chờ thanh toán hoặc Trễ hạn). FR-20 cho phép tạo lịch hẹn và task "
    "với khách hàng, kèm nhắc nhở tự động trước 15 phút, 1 giờ hoặc 1 ngày."
)

h2("3.5. Sales Pipeline và Cơ hội kinh doanh (FR-21 đến FR-23)")
para(
    "Pipeline bán hàng là công cụ để nhân viên và quản lý theo dõi tiến độ các cơ hội "
    "kinh doanh từ lúc phát sinh đến khi chốt hợp đồng. FR-21 cho phép tạo một opportunity "
    "gắn với khách hàng, bao gồm tên cơ hội, giá trị ước tính, xác suất thành công theo "
    "đánh giá chủ quan của nhân viên, ngày dự kiến chốt và nguồn phát sinh lead "
    "(referral, website, cold call,...)."
)
para(
    "FR-22 tổ chức các opportunity theo giai đoạn mặc định: Lead (tiếp cận ban đầu), "
    "Qualify (xác nhận tiềm năng), Proposal (gửi báo giá/đề xuất), Negotiation (đàm phán) "
    "và cuối cùng là Closed Won (chốt thành công) hoặc Closed Lost (không thành công). "
    "Giao diện Kanban board giúp nhân viên kéo thả các opportunity qua lại giữa các cột "
    "giai đoạn một cách trực quan. FR-23 hỗ trợ cập nhật thông tin và xem lịch sử thay "
    "đổi giai đoạn kèm lý do chuyển, giúp phân tích điểm nghẽn trong quy trình bán hàng."
)

h2("3.6. Báo cáo, Dashboard và Thông báo (FR-24 đến FR-27)")
para(
    "FR-24 xây dựng trang Dashboard tổng quan ngay khi đăng nhập, hiển thị các chỉ số "
    "quan trọng: tổng số khách hàng, số hợp đồng đang hiệu lực, tổng giá trị hợp đồng "
    "trong tháng hiện tại và số opportunity đang mở. Bên cạnh đó là biểu đồ số khách "
    "hàng mới theo từng tháng, phân bổ trạng thái khách hàng và widget cảnh báo hợp "
    "đồng sắp hết hạn trong 7 ngày tới – những thông tin mà người quản lý cần thấy "
    "ngay khi mở ứng dụng mỗi buổi sáng."
)
para(
    "FR-25 cung cấp các báo cáo có thể lọc theo nhiều tiêu chí: báo cáo khách hàng theo "
    "trạng thái và người phụ trách, báo cáo hợp đồng theo trạng thái và khoảng thời gian, "
    "báo cáo danh sách hợp đồng sắp hết hạn trong 30 ngày tới. Tất cả có thể xuất ra "
    "file Excel hoặc PDF. FR-26 xây dựng hệ thống thông báo trong ứng dụng (bell icon "
    "với badge số lượng chưa đọc) và thông báo email tự động cho các sự kiện quan trọng "
    "như hợp đồng sắp hết hạn hay cuộc hẹn sắp đến. Người dùng có thể tự cấu hình loại "
    "thông báo muốn nhận. FR-27 ghi nhận nhật ký bảo mật (audit log) cho toàn bộ thao "
    "tác nhạy cảm trong hệ thống, chỉ Admin mới có quyền xem."
)

page_break()

# ═══════════════════════════════════════════════════════════════════
#  CHƯƠNG 4: YÊU CẦU PHI CHỨC NĂNG
# ═══════════════════════════════════════════════════════════════════
h1("4. YÊU CẦU PHI CHỨC NĂNG (NON-FUNCTIONAL REQUIREMENTS)")

h2("4.1. Hiệu năng")
para(
    "Hệ thống cần phản hồi đủ nhanh để không làm gián đoạn luồng làm việc của nhân viên. "
    "Cụ thể, các trang chính (dashboard, danh sách khách hàng, chi tiết hồ sơ) phải tải "
    "xong trong vòng 2 giây với điều kiện không quá 50 người dùng đồng thời. Danh sách "
    "lên đến 1.000 bản ghi cần hiển thị trong tối đa 3 giây với cơ chế phân trang "
    "20 bản ghi mỗi trang. Tính năng xuất báo cáo cho 500 bản ghi không được vượt quá "
    "10 giây. Để đạt được các tiêu chí này, cần áp dụng chỉ mục (index) đúng chỗ trên "
    "database, phân trang ở tất cả danh sách và tránh các N+1 query phổ biến trong ORM."
)

h2("4.2. Bảo mật")
para(
    "Bảo mật là yêu cầu không thể thỏa hiệp với một hệ thống lưu trữ thông tin kinh "
    "doanh nhạy cảm. Mật khẩu phải được mã hóa bằng BCrypt với salt factor tối thiểu 12 "
    "trước khi lưu vào cơ sở dữ liệu – tuyệt đối không lưu plain text. Mọi API đều yêu "
    "cầu JWT hợp lệ, và phân quyền RBAC được kiểm tra tại tầng backend trước mọi thao "
    "tác, không phụ thuộc vào frontend. JWT access token hết hạn sau 15 phút, refresh "
    "token hết hạn sau 7 ngày và bị hủy ngay khi đăng xuất."
)
para(
    "Hệ thống áp dụng rate limiting tối đa 100 request mỗi phút mỗi địa chỉ IP để chống "
    "tấn công brute force. Toàn bộ giao tiếp client-server đi qua HTTPS (TLS 1.2 trở lên) "
    "trong môi trường production. Các lỗ hổng phổ biến như SQL injection được ngăn chặn "
    "thông qua việc dùng ORM (JPA), còn XSS được xử lý qua input validation và output "
    "encoding. Mọi thao tác nhạy cảm (đăng nhập, thay đổi quyền, xóa dữ liệu, xuất báo "
    "cáo) đều được ghi vào audit log với đầy đủ thông tin: người dùng, địa chỉ IP, "
    "thời điểm, hành động và đối tượng bị ảnh hưởng."
)

h2("4.3. Khả năng sử dụng và giao diện")
para(
    "Toàn bộ giao diện người dùng được thiết kế bằng tiếng Việt, từ nhãn các trường dữ "
    "liệu đến thông báo lỗi. Giao diện phải responsive, hoạt động tốt trên cả màn hình "
    "desktop (từ 1280px trở lên), tablet (768-1279px) và mobile (dưới 768px). Khi người "
    "dùng mắc lỗi nhập liệu, thông báo lỗi phải hiện ngay tại trường dữ liệu tương ứng, "
    "viết bằng tiếng Việt rõ ràng, không để lộ bất kỳ thông tin kỹ thuật hay stack trace "
    "nào ra ngoài. Toàn bộ hệ thống sử dụng bộ component UI nhất quán về màu sắc, "
    "font chữ và khoảng cách để đem lại trải nghiệm chuyên nghiệp và dễ học."
)

h2("4.4. Độ tin cậy, bảo trì và triển khai")
para(
    "Cơ sở dữ liệu phải được sao lưu tự động hàng ngày và giữ ít nhất 7 bản backup gần "
    "nhất, kèm tài liệu hướng dẫn khôi phục rõ ràng. Môi trường demo cần đảm bảo uptime "
    "tối thiểu 95% trong khoảng thời gian bảo vệ đề tài. Về bảo trì, backend được tổ "
    "chức theo kiến trúc phân tầng (Controller → Service → Repository) và module hóa theo "
    "domain nghiệp vụ để dễ mở rộng về sau. Unit test cần đạt coverage ít nhất 60% trên "
    "tầng business logic. Toàn bộ REST API được tài liệu hóa bằng Swagger/OpenAPI 3.0 "
    "với khả năng test trực tiếp trên giao diện. Hệ thống được đóng gói bằng Docker "
    "Compose để có thể triển khai nhất quán trên bất kỳ môi trường nào."
)

page_break()

# ═══════════════════════════════════════════════════════════════════
#  CHƯƠNG 5: MÔ HÌNH USE CASE
# ═══════════════════════════════════════════════════════════════════
h1("5. MÔ HÌNH USE CASE")

h2("5.1. Tổng quan các Use Case")
para(
    "Hệ thống CRMix có năm actor tham gia: Admin, Sales Manager và Sales Rep là ba actor "
    "chính tương tác trực tiếp qua giao diện; Email Server và Scheduler là hai actor phụ "
    "hoạt động tự động phía hệ thống. Dựa trên các yêu cầu chức năng đã xác định, nhóm "
    "xác định 15 use case cốt lõi được tổ chức theo bảng dưới đây."
)

simple_table(
    "Bảng 5.1. Danh sách Use Case",
    ["Mã UC", "Tên Use Case", "Actor chính", "Ưu tiên"],
    [("UC-01","Đăng nhập / Đăng xuất hệ thống","Tất cả","Cao"),
     ("UC-02","Quản lý tài khoản và phân quyền","Admin","Cao"),
     ("UC-03","Tạo và quản lý hồ sơ khách hàng","Sales Rep, Manager","Cao"),
     ("UC-04","Phân công khách hàng cho nhân viên","Sales Manager","Cao"),
     ("UC-05","Tìm kiếm và lọc khách hàng","Sales Rep, Manager","Cao"),
     ("UC-06","Tạo và theo dõi vòng đời hợp đồng","Sales Rep, Manager","Cao"),
     ("UC-07","Cảnh báo tự động HĐ sắp hết hạn","Scheduler, Email Server","Cao"),
     ("UC-08","Gia hạn hợp đồng và tạo phụ lục","Sales Rep, Manager","Cao"),
     ("UC-09","Ghi nhận hoạt động tương tác KH","Sales Rep","Cao"),
     ("UC-10","Xem timeline hoạt động khách hàng","Sales Rep, Manager","Cao"),
     ("UC-11","Quản lý Pipeline cơ hội kinh doanh","Sales Rep, Manager","Cao"),
     ("UC-12","Xem Dashboard và xuất báo cáo","Manager, Admin","Cao"),
     ("UC-13","Nhận và quản lý thông báo","Tất cả","TB"),
     ("UC-14","Import/Export danh sách khách hàng","Sales Rep, Manager","TB"),
     ("UC-15","Xem audit log bảo mật","Admin","TB"),],
    [1.8, 6.5, 3.7, 1.5])

h2("5.2. Mô tả chi tiết Use Case ưu tiên")
para(
    "Dưới đây là mô tả chi tiết hai use case có độ phức tạp nghiệp vụ cao nhất và thể "
    "hiện rõ nhất giá trị của hệ thống."
)

mixed_para([("UC-06 – Tạo và theo dõi vòng đời hợp đồng", True, False)], sb=6, sa=3)
para(
    "Actor chính là Sales Rep hoặc Sales Manager, với điều kiện tiên quyết là đã đăng "
    "nhập và khách hàng liên quan đã tồn tại trong hệ thống. Luồng chính diễn ra như "
    "sau: nhân viên mở hồ sơ khách hàng và chọn 'Tạo hợp đồng mới'. Hệ thống hiển thị "
    "form nhập liệu, nhân viên điền đầy đủ thông tin (mã hợp đồng, loại, các mốc ngày, "
    "giá trị, mô tả) và upload file hợp đồng gốc. Sau khi lưu, hợp đồng ở trạng thái "
    "Dự thảo. Nhân viên tự chuyển trạng thái khi phù hợp: sang Chờ ký khi đã gửi cho "
    "khách hàng, sang Đang hiệu lực khi đã nhận bản ký chính thức.", sb=0, sa=4
)
para(
    "Luồng ngoại lệ xử lý hai trường hợp phổ biến: nếu ngày hết hạn nhỏ hơn ngày hiệu "
    "lực, hệ thống báo lỗi ngay tại trường ngày hết hạn và không cho lưu; nếu file đính "
    "kèm vượt quá 10 MB, hệ thống từ chối và yêu cầu chọn file khác. Sau khi use case "
    "hoàn tất thành công, hợp đồng hiện trong hồ sơ khách hàng và danh sách hợp đồng "
    "chung, sẵn sàng để theo dõi và nhận cảnh báo tự động."
)

mixed_para([("UC-07 – Cảnh báo tự động hợp đồng sắp hết hạn", True, False)], sb=6, sa=3)
para(
    "Đây là use case hoàn toàn tự động, không có người dùng chủ động khởi tạo. Scheduler "
    "kích hoạt lúc 8:00 sáng mỗi ngày. Hệ thống truy vấn tất cả hợp đồng đang ở trạng "
    "thái Đang hiệu lực có ngày hết hạn rơi vào mốc 30, 15 hoặc 7 ngày kể từ hôm nay. "
    "Với mỗi hợp đồng tìm thấy, hệ thống tạo thông báo trong ứng dụng cho nhân viên "
    "phụ trách và Manager, đồng thời gửi email cảnh báo (nếu chưa gửi trong ngày hôm "
    "nay để tránh spam). Với hợp đồng còn 7 ngày hoặc ít hơn, hệ thống tự động chuyển "
    "trạng thái sang Sắp hết hạn. Toàn bộ quá trình xử lý được ghi log để kiểm tra "
    "sau này. Kết quả mong đợi: nhân viên nhận được cảnh báo kịp thời mà không cần "
    "phải tự nhớ hay kiểm tra thủ công.", sb=0, sa=4
)

page_break()

# ═══════════════════════════════════════════════════════════════════
#  CHƯƠNG 6: MÔ HÌNH DỮ LIỆU (ERD)
# ═══════════════════════════════════════════════════════════════════
h1("6. MÔ HÌNH DỮ LIỆU (ERD)")

h2("6.1. Tổng quan các thực thể")
para(
    "Cơ sở dữ liệu của CRMix được thiết kế xoay quanh hai thực thể trung tâm là khách "
    "hàng (customers) và hợp đồng (contracts), với các thực thể vệ tinh bổ sung cho "
    "từng nghiệp vụ cụ thể. Mối quan hệ tổng quát như sau: mỗi người dùng (users) có "
    "một vai trò xác định (roles) và có thể được phân công phụ trách nhiều khách hàng "
    "và nhiều hợp đồng. Mỗi khách hàng có thể có nhiều hợp đồng, nhiều lần tương tác "
    "được ghi lại (activities) và nhiều cơ hội kinh doanh (opportunities) đang tiến hành "
    "song song. Mỗi hợp đồng có thể đính kèm nhiều file (contract_attachments) và ghi "
    "nhận nhiều giao dịch tài chính (transactions). Các opportunity được theo dõi theo "
    "giai đoạn pipeline (pipeline_stages). Ngoài ra, hệ thống còn có bảng notifications "
    "để lưu thông báo cho từng người dùng và bảng audit_logs để ghi nhật ký bảo mật."
)

h2("6.2. Thiết kế bảng dữ liệu cốt lõi")
para(
    "Bảng users lưu thông tin tài khoản: id (khóa chính tự tăng), full_name, email "
    "(UNIQUE – dùng để đăng nhập), password_hash (BCrypt), role_id (khóa ngoại đến "
    "bảng roles), is_active (boolean quản lý trạng thái tài khoản), last_login_at, "
    "created_at và updated_at."
)
para(
    "Bảng customers là trọng tâm của hệ thống: id, name (tên khách hàng hoặc công ty), "
    "type (ENUM: INDIVIDUAL hoặc BUSINESS), phone và email (cả hai UNIQUE để kiểm tra "
    "trùng lặp), address, industry (ngành nghề), status (ENUM gồm POTENTIAL, ACTIVE, "
    "CONTRACTED, INACTIVE, ARCHIVED), assigned_to (khóa ngoại đến users.id – nhân viên "
    "phụ trách), created_by (khóa ngoại đến users.id – người tạo hồ sơ), notes, "
    "created_at và updated_at."
)
para(
    "Bảng contracts: id, contract_number (UNIQUE – mã hợp đồng), customer_id (khóa "
    "ngoại đến customers.id), contract_type, sign_date, effective_date, expiry_date "
    "(ba trường DATE bắt buộc với ràng buộc expiry_date > effective_date), value "
    "(DECIMAL 18,2), currency (CHAR 3, mặc định 'VND'), status (ENUM: DRAFT, PENDING, "
    "ACTIVE, EXPIRING, EXPIRED, TERMINATED), description, assigned_to (khóa ngoại), "
    "created_at và updated_at."
)
para(
    "Bảng opportunities: id, title, customer_id (khóa ngoại), stage_id (khóa ngoại "
    "đến pipeline_stages.id), estimated_value, probability (TINYINT 0-100 – xác suất "
    "thành công), expected_close_date, lead_source, assigned_to (khóa ngoại), notes, "
    "created_at. Bảng pipeline_stages lưu cấu hình các giai đoạn: id, name (Lead, "
    "Qualify, Proposal, Negotiation, Closed Won, Closed Lost), order_index để sắp xếp "
    "thứ tự hiển thị trên Kanban board."
)
para(
    "Bảng activities ghi lịch sử tương tác: id, customer_id, user_id, type (ENUM: "
    "CALL, EMAIL, MEETING, ONLINE_MEETING, OTHER), activity_date, duration_minutes, "
    "summary (nội dung tóm tắt), outcome (kết quả), next_action (bước tiếp theo), "
    "created_at. Bảng audit_logs ghi nhật ký bảo mật: id, user_id, action (thao tác "
    "được thực hiện), entity_type, entity_id, old_value và new_value (JSON), ip_address, "
    "created_at."
)

page_break()

# ═══════════════════════════════════════════════════════════════════
#  CHƯƠNG 7: KIẾN TRÚC VÀ CÔNG NGHỆ
# ═══════════════════════════════════════════════════════════════════
h1("7. KIẾN TRÚC HỆ THỐNG VÀ CÔNG NGHỆ")

h2("7.1. Kiến trúc tổng quan")
para(
    "CRMix được xây dựng theo kiến trúc web ba tầng (Three-tier Architecture), kết hợp "
    "với Layered Architecture (phân tầng theo trách nhiệm) ở phía backend. Lý do lựa "
    "chọn kiến trúc này là sự phù hợp với quy mô nhóm ba người: mỗi thành viên có thể "
    "tập trung vào một tầng riêng biệt mà không ảnh hưởng quá nhiều đến công việc của "
    "người khác, đồng thời vẫn dễ tích hợp thông qua API contract được định nghĩa rõ ràng "
    "từ đầu."
)
para(
    "Tầng trình bày (Presentation Layer) là ứng dụng ReactJS chạy trên trình duyệt, "
    "giao tiếp với backend thông qua REST API. Tầng ứng dụng (Application Layer) là "
    "Spring Boot chịu trách nhiệm xử lý toàn bộ business logic, kiểm tra phân quyền, "
    "tương tác với cơ sở dữ liệu và điều phối các tác vụ tự động (Scheduler, Email). "
    "Tầng dữ liệu (Data Layer) là MySQL 8 lưu trữ toàn bộ dữ liệu nghiệp vụ, cùng "
    "với một storage server (MinIO hoặc local storage trong môi trường dev) để lưu file "
    "đính kèm hợp đồng."
)
para(
    "Luồng xử lý tổng quát: người dùng thao tác trên giao diện React, frontend gọi REST "
    "API đến backend Spring Boot, backend xác thực JWT và kiểm tra quyền RBAC, xử lý "
    "business logic và tương tác với MySQL, sau đó trả về kết quả JSON để frontend render. "
    "Tác vụ cảnh báo hợp đồng chạy tự động theo lịch, không cần tương tác người dùng."
)

h2("7.2. Stack công nghệ")
simple_table(
    "Bảng 7.1. Stack công nghệ đề xuất",
    ["Thành phần","Công nghệ & Phiên bản","Lý do lựa chọn"],
    [("Frontend","ReactJS 18 + Vite 5","Component-based, hệ sinh thái lớn, nhóm có kinh nghiệm."),
     ("UI Library","Ant Design 5.x","Component phong phú cho CRUD app, hỗ trợ tiếng Việt tốt."),
     ("State / Data","Zustand + React Query","Nhẹ hơn Redux; React Query xử lý server state + caching tốt."),
     ("Backend","Spring Boot 3.x (Java 17)","Mature ecosystem, tích hợp sẵn Security, JPA, Mail, Scheduler."),
     ("ORM & Migration","Spring Data JPA + Flyway","Giảm SQL thủ công, quản lý schema version an toàn."),
     ("Auth","Spring Security 6 + JWT","Chuẩn ngành cho RBAC và refresh token, filter chain linh hoạt."),
     ("Database","MySQL 8.x","Phổ biến, dễ vận hành, tích hợp tốt với Spring JPA."),
     ("API Docs","SpringDoc OpenAPI (Swagger 2)","Tự sinh tài liệu từ annotation, UI test trực tiếp."),
     ("Email","JavaMailSender + Gmail SMTP","Gửi email thông báo tự động, cấu hình đơn giản."),
     ("Triển khai","Docker + Docker Compose","Môi trường nhất quán, deploy cloud VM dễ dàng."),
     ("Test","JUnit 5 + Mockito + Postman","Unit test, mock test và API test collection CI-ready."),],
    [3, 4, 8.5])

h2("7.3. Cấu trúc module backend")
para(
    "Backend được tổ chức theo cấu trúc package theo domain (domain-driven package "
    "structure) gồm mười module chính: auth (xác thực và JWT), user (tài khoản người "
    "dùng), customer (khách hàng), contract (hợp đồng), activity (tương tác và lịch hẹn), "
    "pipeline (cơ hội kinh doanh), report (báo cáo và dashboard), notification (thông "
    "báo), scheduler (tác vụ định kỳ) và common (utilities, exceptions, response "
    "wrapper và audit log dùng chung). Mỗi module bao gồm Controller, Service, "
    "Repository và các DTO tương ứng, đảm bảo tính cohesion cao và coupling thấp."
)

page_break()

# ═══════════════════════════════════════════════════════════════════
#  CHƯƠNG 8: KẾ HOẠCH, PHÂN CÔNG VÀ RỦI RO
# ═══════════════════════════════════════════════════════════════════
h1("8. KẾ HOẠCH, PHÂN CÔNG VÀ RỦI RO")

h2("8.1. Phạm vi MVP và chiến lược ưu tiên")
para(
    "Nhóm áp dụng phương pháp MoSCoW để xác định phạm vi triển khai trong Tiểu luận "
    "Chuyên ngành. Nhóm Must Have (bắt buộc trong MVP) bao gồm toàn bộ module xác thực "
    "và phân quyền RBAC, CRUD khách hàng hoàn chỉnh kèm phân công, CRUD hợp đồng với "
    "vòng đời trạng thái và cảnh báo tự động, ghi nhận tương tác với khách hàng, "
    "Sales Pipeline với Kanban board, Dashboard KPI cơ bản và Audit log bảo mật."
)
para(
    "Nhóm Should Have (nên có nhưng không bắt buộc ngay) gồm báo cáo chi tiết xuất "
    "được ra Excel/PDF, import/export danh sách khách hàng từ Excel, chức năng gia hạn "
    "hợp đồng và tạo phụ lục, ghi nhận giao dịch tài chính, thông báo email tự động "
    "và lịch hẹn/task. Nhóm Could Have (có thể bổ sung nếu còn thời gian) gồm báo cáo "
    "hiệu suất bán hàng chi tiết, calendar view cho lịch hẹn và tùy chỉnh giai đoạn "
    "Pipeline. Các tính năng như tích hợp kế toán, ứng dụng mobile, AI/ML hay multi-tenant "
    "nằm hoàn toàn ngoài phạm vi đề tài này và được xếp vào hướng phát triển Capstone."
)
para(
    "MVP được coi là hoàn thành khi nhóm có thể chạy thông suốt một luồng nghiệp vụ đầy "
    "đủ: Admin tạo tài khoản và cấu hình hệ thống → Manager phân công khách hàng cho "
    "Sales Rep → Sales Rep tạo hồ sơ khách hàng, ký hợp đồng và ghi tương tác → Hệ "
    "thống tự động cảnh báo hợp đồng sắp hết hạn → Manager xem Dashboard tổng quan và "
    "xuất báo cáo → Admin kiểm tra audit log toàn bộ thao tác trên."
)

h2("8.2. Kế hoạch triển khai")
para(
    "Dự án được chia thành bảy giai đoạn trong khoảng 16 tuần. Hai tuần đầu (Giai đoạn 1) "
    "dành cho phân tích yêu cầu, xây dựng SRS và xác định business rules, tiêu chí "
    "nghiệm thu. Hai tuần tiếp theo (Giai đoạn 2) tập trung thiết kế: wireframe giao "
    "diện, ERD, API contract trên Swagger và database schema với Flyway migration. "
    "Giai đoạn 3 (tuần 5-7) phát triển nền tảng: authentication, phân quyền RBAC và "
    "module khách hàng cơ bản. Giai đoạn 4 (tuần 8-11) là giai đoạn dài nhất và quan "
    "trọng nhất, hoàn thiện toàn bộ nghiệp vụ cốt lõi: hợp đồng, tương tác, pipeline và "
    "dashboard – đây là lúc MVP được hoàn chỉnh. Giai đoạn 5 (tuần 12-13) bổ sung các "
    "tính năng Should Have. Giai đoạn 6 (tuần 14-15) dành riêng cho kiểm thử toàn diện "
    "và sửa lỗi. Giai đoạn 7 (tuần 16) hoàn thiện việc đóng gói Docker, triển khai demo "
    "trên cloud, hoàn thiện tài liệu và chuẩn bị slide thuyết trình."
)

h2("8.3. Phân công công việc")
para(
    "Nhóm gồm ba thành viên với phân công dựa trên thế mạnh kỹ thuật của từng người, "
    "đồng thời đảm bảo ai cũng hiểu toàn bộ luồng nghiệp vụ để phản biện hiệu quả."
)
para(
    "Huỳnh Minh Tài (MSSV: 22110068) đảm nhận vai trò Backend Lead và Architecture. "
    "Trách nhiệm chính bao gồm thiết kế kiến trúc tổng thể và ERD, phát triển các module "
    "Backend cốt lõi (Auth, User, Customer, Contract), triển khai JWT và Spring Security "
    "với RBAC đầy đủ, quản lý database migration bằng Flyway, xây dựng Audit Log và "
    "phụ trách toàn bộ việc đóng gói Docker và triển khai demo cloud. Ngoài ra, thành "
    "viên này chịu trách nhiệm review toàn bộ code backend và viết unit test cho các "
    "service quan trọng."
)
para(
    "Văn Phạm Thảo Nhi (MSSV: 23110049) đảm nhận vai trò Frontend Lead và UI/UX. "
    "Trách nhiệm bao gồm thiết kế wireframe và UI/UX cho toàn bộ hệ thống, phát triển "
    "tất cả module giao diện ReactJS (từ màn hình đăng nhập đến Dashboard và Pipeline "
    "Kanban board), tích hợp API với backend, quản lý state với Zustand và server data "
    "với React Query, đảm bảo responsive design và trải nghiệm người dùng mượt mà."
)
para(
    "Nguyễn Đức Thắng (MSSV: 23110062) đảm nhận vai trò Backend Developer, QA và DevOps. "
    "Trách nhiệm bao gồm phát triển các module backend phụ trợ (Activity, Pipeline, "
    "Report, Notification, Scheduler), tích hợp JavaMailSender cho email tự động, xây "
    "dựng tài liệu API với Swagger, phát triển tính năng import/export Excel bằng "
    "Apache POI, viết integration test với Postman, hỗ trợ DevOps và chịu trách nhiệm "
    "tổng hợp báo cáo cuối cùng cùng slide thuyết trình."
)
para(
    "Cả ba thành viên cùng tham gia vào: phân tích yêu cầu và xây dựng SRS, code review "
    "chéo theo từng sprint, kiểm thử tích hợp end-to-end, chuẩn bị kịch bản và dữ liệu "
    "demo, cũng như phản biện lẫn nhau trong buổi bảo vệ."
)

h2("8.4. Rủi ro và hướng xử lý")
para(
    "Sau khi phân tích, nhóm xác định sáu rủi ro chính có thể ảnh hưởng đến tiến độ và "
    "chất lượng dự án. Rủi ro lớn nhất là phạm vi bị mở rộng không kiểm soát (Scope "
    "Creep): khi bắt đầu code sẽ phát sinh nhiều ý tưởng mới hấp dẫn. Hướng xử lý là "
    "chốt cứng MVP và feature list từ đầu, mọi thay đổi phải được cả nhóm thống nhất "
    "và cân nhắc kỹ trước khi thêm vào backlog."
)
para(
    "Rủi ro thứ hai là sự không tương thích giữa Frontend và Backend khi tích hợp – đây "
    "là vấn đề cực kỳ phổ biến khi phát triển song song. Giải pháp là xác định API "
    "contract chi tiết trên Swagger ngay từ Giai đoạn 2, Frontend dùng mock data để "
    "phát triển độc lập trước khi backend sẵn sàng. Rủi ro thứ ba là thiếu thời gian "
    "hoàn thiện tính năng: nhóm sẽ ưu tiên cứng nhắc theo thứ tự Must Have → Should "
    "Have → Could Have, sẵn sàng cắt bỏ phần Could Have nếu cần để đảm bảo chất lượng "
    "phần cốt lõi."
)
para(
    "Rủi ro thứ tư liên quan đến nhân sự: nếu một thành viên gặp sự cố không thể tham "
    "gia trong thời gian dài, tiến độ sẽ bị ảnh hưởng nghiêm trọng. Biện pháp phòng "
    "ngừa là tài liệu hóa tốt từ đầu và đảm bảo toàn bộ code luôn được push lên GitHub "
    "để thành viên khác có thể tiếp nối nhanh. Rủi ro thứ năm là lỗi bảo mật: sử dụng "
    "ORM thay vì SQL thuần túy và kiểm tra RBAC nghiêm ngặt phía backend sẽ loại bỏ "
    "hầu hết lỗ hổng phổ biến. Rủi ro thứ sáu là môi trường demo gặp sự cố ngay trước "
    "buổi bảo vệ: nhóm sẽ duy trì song song một bản local demo và một bản cloud, kèm "
    "Docker image được backup sẵn sàng để khôi phục trong vài phút."
)

page_break()

# ═══════════════════════════════════════════════════════════════════
#  CHƯƠNG 9: KẾT LUẬN
# ═══════════════════════════════════════════════════════════════════
h1("9. KẾT LUẬN")
para(
    "Tài liệu SRS này đã hoàn thành việc đặc tả đầy đủ hệ thống CRMix – giải pháp CRM "
    "hướng đến doanh nghiệp vừa và nhỏ tại Việt Nam. Từ góc độ nghiệp vụ, hệ thống "
    "giải quyết ba bài toán thực tế mà hầu hết SME đang gặp phải: dữ liệu khách hàng "
    "bị phân tán và không có chủ nhân rõ ràng, hợp đồng không được theo dõi sát và hay "
    "bị bỏ lỡ cơ hội gia hạn, và người quản lý không có công cụ nào để theo dõi hiệu "
    "suất đội ngũ bán hàng theo thời gian thực."
)
para(
    "Từ góc độ kỹ thuật, đề tài thể hiện chiều sâu qua nhiều vấn đề không tầm thường: "
    "thiết kế phân quyền RBAC kết hợp phạm vi dữ liệu (nhân viên chỉ thấy khách hàng "
    "được phân công), vòng đời hợp đồng với cơ chế cảnh báo tự động chạy nền bằng "
    "Scheduler, Kanban board cho Sales Pipeline, audit log bảo mật không thể chỉnh sửa "
    "bởi người dùng thông thường, kiến trúc module hóa theo domain và khả năng đóng gói "
    "Docker để triển khai linh hoạt. Tập hợp những kỹ thuật này tạo ra một hệ thống "
    "có chiều sâu kỹ thuật xứng đáng với yêu cầu của Tiểu luận Chuyên ngành IT (POSE) "
    "tại HCMUTE."
)
para(
    "Nhóm nhận thức rõ rằng với ba thành viên và thời gian có hạn, chất lượng kỹ thuật "
    "của phần cốt lõi quan trọng hơn việc nhồi nhét nhiều tính năng. Chiến lược của "
    "nhóm là hoàn thiện một luồng nghiệp vụ end-to-end thực sự mượt mà và đáng tin cậy, "
    "sau đó mới mở rộng dần. Phần Should Have sẽ được bổ sung khi MVP đã ổn định hoàn "
    "toàn."
)
para(
    "Nhìn về phía trước, nếu có cơ hội phát triển lên Capstone Project, nhóm định hướng "
    "tích hợp mô hình phân tích RFM (Recency – Frequency – Monetary) để tự động phân "
    "nhóm khách hàng theo giá trị và hành vi, từ đó gợi ý chiến lược chăm sóc phù hợp "
    "cho từng nhóm. Kết hợp với một số kỹ thuật Machine Learning cơ bản để dự báo xác "
    "suất gia hạn hợp đồng, CRMix có thể tiến từ một CRM quản lý đơn thuần thành một "
    "CRM thông minh hỗ trợ ra quyết định cho doanh nghiệp SME – đây cũng chính là tên "
    "gọi đầy đủ của định hướng Capstone: Xây dựng hệ thống CRM thông minh ứng dụng "
    "RFM và AI trong phân loại, chăm sóc khách hàng tự động cho doanh nghiệp SME."
)

para("— Hết tài liệu SRS —",
     sz=11, italic=True, align=WD_ALIGN_PARAGRAPH.CENTER, sb=20, sa=0)

# ── Lưu ─────────────────────────────────────────────────────
out = r"d:\POSE\SRS_CRM_SME_CRMix.docx"
doc.save(out)
print(f"\n[OK] Da tao file SRS thanh cong!")
print(f"     File: {out}")
print(f"     Uoc tinh: 15-20 trang A4\n")
