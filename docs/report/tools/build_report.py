# -*- coding: utf-8 -*-
"""Builds docs/report/OneShop_BaoCao_KienTruc_ChucNang_GiaoDien.docx and OneShop_BaoCao_Source.md from
 - content_*.py (descriptions, diagram specs, code references, explanations),
 - diagrams/**/*.png (activity diagrams rendered from the specs, architecture diagram),
 - screenshots/manifest.json + annotated screenshots of the running site.
Code excerpts are read verbatim from the repository by (file, first line, last line)."""
import json
import os
import re
import sys
import datetime

from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import content_customer, content_admin, content_staff  # noqa: E402

TOOLS = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(TOOLS, ".."))                 # docs/report
REPO = os.path.abspath(os.path.join(ROOT, "..", ".."))
SRC = os.path.join(REPO, "src", "main", "java", "com", "oneshop")
OUT_DOCX = os.path.join(ROOT, "OneShop_BaoCao_KienTruc_ChucNang_GiaoDien.docx")
OUT_MD = os.path.join(ROOT, "OneShop_BaoCao_Source.md")
MANIFEST = json.load(open(os.path.join(ROOT, "screenshots", "manifest.json"), encoding="utf-8"))

FONT = "Times New Roman"
CODE_FONT = "Consolas"
TODAY = datetime.date(2026, 10, 25)

# ----------------------------------------------------------------------------------------------- docx helpers
doc = Document()
md = []                                   # markdown lines
counters = {"chap": 0, "Hình": 0, "Bảng": 0, "Đoạn mã": 0}
SEQ_ID = {"Hình": "Hình", "Bảng": "Bảng", "Đoạn mã": "DoanMa"}   # a SEQ identifier cannot contain a space
refs = {}                                 # key -> "Hình 2.3"


def rfonts(el, name):
    rpr = el.get_or_add_rPr() if hasattr(el, "get_or_add_rPr") else el
    f = rpr.find(qn("w:rFonts"))
    if f is None:
        f = OxmlElement("w:rFonts")
        rpr.insert(0, f)
    for a in ("w:ascii", "w:hAnsi", "w:cs", "w:eastAsia"):
        f.set(qn(a), name)


def style_font(style, name, size, bold=None, italic=None, color=None):
    style.font.name = name
    style.font.size = Pt(size)
    if bold is not None:
        style.font.bold = bold
    if italic is not None:
        style.font.italic = italic
    style.font.color.rgb = RGBColor(0, 0, 0) if color is None else color
    rfonts(style.element, name)


def setup_styles():
    sec = doc.sections[0]
    sec.page_width, sec.page_height = Cm(21.0), Cm(29.7)
    sec.top_margin, sec.bottom_margin = Cm(2.5), Cm(2.5)
    sec.left_margin, sec.right_margin = Cm(3.0), Cm(2.0)
    sec.header_distance = sec.footer_distance = Cm(1.25)
    n = doc.styles["Normal"]
    style_font(n, FONT, 13)
    pf = n.paragraph_format
    pf.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    pf.line_spacing = 1.4
    pf.space_after = Pt(6)
    pf.space_before = Pt(0)
    for name, size, before, after in (("Heading 1", 16, 0, 12), ("Heading 2", 14, 14, 8), ("Heading 3", 13, 12, 6)):
        h = doc.styles[name]
        style_font(h, FONT, size, bold=True, italic=False)
        h.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.LEFT
        h.paragraph_format.space_before = Pt(before)
        h.paragraph_format.space_after = Pt(after)
        h.paragraph_format.keep_with_next = True
        h.paragraph_format.line_spacing = 1.2
    doc.styles["Heading 1"].paragraph_format.page_break_before = True
    cap = doc.styles["Caption"]
    style_font(cap, FONT, 12, bold=True, italic=False)
    cap.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.CENTER
    cap.paragraph_format.space_before = Pt(4)
    cap.paragraph_format.space_after = Pt(10)
    cap.paragraph_format.line_spacing = 1.2
    code = doc.styles.add_style("CodeBlock", 1)
    style_font(code, CODE_FONT, 9)
    code.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.LEFT
    code.paragraph_format.line_spacing = 1.0
    code.paragraph_format.space_after = Pt(0)
    code.paragraph_format.left_indent = Cm(0.9)
    code.paragraph_format.first_line_indent = Cm(-0.6)
    ppr = code.element.get_or_add_pPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear"); shd.set(qn("w:color"), "auto"); shd.set(qn("w:fill"), "F2F2F2")
    ppr.append(shd)
    plain = doc.styles.add_style("FrontTitle", 1)
    style_font(plain, FONT, 16, bold=True)
    plain.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.CENTER
    plain.paragraph_format.space_after = Pt(12)
    for lvl in (1, 2, 3):
        try:
            t = doc.styles["TOC %d" % lvl]
        except KeyError:
            t = doc.styles.add_style("TOC %d" % lvl, 1)
        style_font(t, FONT, 13)
        t.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.LEFT
        t.paragraph_format.left_indent = Cm(0.6 * (lvl - 1))
        t.paragraph_format.space_after = Pt(2)
        t.paragraph_format.line_spacing = 1.15
    try:
        tf = doc.styles["Table of Figures"]
    except KeyError:
        tf = doc.styles.add_style("Table of Figures", 1)
    style_font(tf, FONT, 12)
    tf.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.LEFT
    tf.paragraph_format.space_after = Pt(2)
    tf.paragraph_format.line_spacing = 1.15


def fld(par, instr, cached="", bold=None):
    def run_with(el):
        r = par.add_run()
        if bold is not None:
            r.bold = bold
        r._r.append(el)
        return r
    b = OxmlElement("w:fldChar"); b.set(qn("w:fldCharType"), "begin"); run_with(b)
    i = OxmlElement("w:instrText"); i.set(qn("xml:space"), "preserve"); i.text = " %s " % instr; run_with(i)
    s = OxmlElement("w:fldChar"); s.set(qn("w:fldCharType"), "separate"); run_with(s)
    r = par.add_run(cached)
    if bold is not None:
        r.bold = bold
    e = OxmlElement("w:fldChar"); e.set(qn("w:fldCharType"), "end"); run_with(e)


_bm_id = [100]


def bookmark_start(par, name):
    _bm_id[0] += 1
    el = OxmlElement("w:bookmarkStart")
    el.set(qn("w:id"), str(_bm_id[0])); el.set(qn("w:name"), name)
    par._p.append(el)
    return _bm_id[0]


def bookmark_end(par, bid):
    el = OxmlElement("w:bookmarkEnd")
    el.set(qn("w:id"), str(bid))
    par._p.append(el)


def para(text="", style=None, align=None, bold=False, italic=False, indent=True, keep_next=False, size=None):
    p = doc.add_paragraph(style=style)
    if text:
        r = p.add_run(text)
        r.bold = bold or None
        r.italic = italic or None
        if size:
            r.font.size = Pt(size)
    if align is not None:
        p.alignment = align
    if indent and style is None and align is None:
        p.paragraph_format.first_line_indent = Cm(1.0)
    if keep_next:
        p.paragraph_format.keep_with_next = True
    return p


def heading(text, level):
    p = doc.add_heading(text, level=level)
    for r in p.runs:
        r.font.name = FONT
        rfonts(r._r, FONT)
    return p


def new_chapter(title):
    counters["chap"] += 1
    for k in ("Hình", "Bảng", "Đoạn mã"):
        counters[k] = 0
    heading(title, 1)
    md.append("\n# %s\n" % title)


def caption(kind, text, key):
    counters[kind] += 1
    num = "%d.%d" % (counters["chap"], counters[kind])
    refs[key] = "%s %s" % (kind, num)
    p = doc.add_paragraph(style="Caption")
    bid = bookmark_start(p, "_Ref_" + re.sub(r"\W", "_", key))
    p.add_run("%s %d." % (kind, counters["chap"]))
    fld(p, "SEQ %s \\* ARABIC \\s 1" % SEQ_ID[kind], str(counters[kind]))
    bookmark_end(p, bid)
    p.add_run(". " + text)
    return p


def ref(par, key, cached=None):
    fld(par, "REF _Ref_%s \\h" % re.sub(r"\W", "_", key), cached or refs.get(key, "?"))


def add_picture(path, max_w=15.9, max_h=20.5):
    with Image.open(path) as im:
        w, h = im.size
    cm_w = min(max_w, max_h * w / h)
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.keep_with_next = True
    p.paragraph_format.space_after = Pt(2)
    p.paragraph_format.line_spacing = 1.0
    p.add_run().add_picture(path, width=Cm(cm_w))
    return p


def shade(cell, fill):
    tcpr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear"); shd.set(qn("w:color"), "auto"); shd.set(qn("w:fill"), fill)
    tcpr.append(shd)


def cell_text(cell, text, bold=False, align=None, size=12):
    cell.text = ""
    p = cell.paragraphs[0]
    p.paragraph_format.line_spacing = 1.15
    p.paragraph_format.space_after = Pt(2)
    p.paragraph_format.space_before = Pt(2)
    p.paragraph_format.first_line_indent = Cm(0)
    p.alignment = align if align is not None else WD_ALIGN_PARAGRAPH.LEFT
    r = p.add_run(text)
    r.bold = bold or None
    r.font.size = Pt(size)
    r.font.name = FONT
    rfonts(r._r, FONT)


def table(headers, rows, widths_cm, size=12):
    t = doc.add_table(rows=1, cols=len(headers))
    t.style = "Table Grid"
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    t.autofit = False
    for i, h in enumerate(headers):
        c = t.rows[0].cells[i]
        cell_text(c, h, bold=True, align=WD_ALIGN_PARAGRAPH.CENTER, size=size)
        shade(c, "D9D9D9")
    trpr = t.rows[0]._tr.get_or_add_trPr()
    th = OxmlElement("w:tblHeader"); th.set(qn("w:val"), "true"); trpr.append(th)
    for row in rows:
        cells = t.add_row().cells
        for i, v in enumerate(row):
            cell_text(cells[i], str(v), align=WD_ALIGN_PARAGRAPH.CENTER if i == 0 else WD_ALIGN_PARAGRAPH.LEFT, size=size)
    for r in t.rows:
        trpr = r._tr.get_or_add_trPr()
        cs = OxmlElement("w:cantSplit"); cs.set(qn("w:val"), "true"); trpr.append(cs)
        for i, w in enumerate(widths_cm):
            r.cells[i].width = Cm(w)
    doc.add_paragraph().paragraph_format.space_after = Pt(4)
    return t


def code_block(lines):
    n = len(lines)
    for i, line in enumerate(lines):
        p = doc.add_paragraph(style="CodeBlock")
        r = p.add_run(line.replace("\t", "    ") if line else " ")
        r.font.name = CODE_FONT
        rfonts(r._r, CODE_FONT)
        r.font.size = Pt(9)
        if i < n - 1:
            p.paragraph_format.keep_with_next = True
        p.paragraph_format.keep_together = True
    sp = doc.add_paragraph()
    sp.paragraph_format.space_after = Pt(4)


def read_code(rel, a, b):
    path = os.path.join(SRC, rel)
    lines = open(path, encoding="utf-8").read().split("\n")
    seg = lines[a - 1:b]
    while seg and not seg[-1].strip():
        seg.pop()
    # strip the common indentation so that the excerpt is compact; content is otherwise untouched
    ind = min((len(l) - len(l.lstrip(" ")) for l in seg if l.strip()), default=0)
    return [l[ind:] if l.strip() else "" for l in seg], ind


# ----------------------------------------------------------------------------------------------- header/footer
def header_footer():
    sec = doc.sections[0]
    sec.different_first_page_header_footer = True
    hp = sec.header.paragraphs[0]
    hp.text = ""
    r = hp.add_run("OneShop – Báo cáo kiến trúc, sơ đồ hoạt động và giao diện")
    r.font.size = Pt(10); r.italic = True; r.font.name = FONT
    hp.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    ppr = hp._p.get_or_add_pPr()
    bd = OxmlElement("w:pBdr")
    bt = OxmlElement("w:bottom")
    for k, v in (("w:val", "single"), ("w:sz", "4"), ("w:space", "1"), ("w:color", "808080")):
        bt.set(qn(k), v)
    bd.append(bt); ppr.append(bd)
    fp = sec.footer.paragraphs[0]
    fp.text = ""
    fp.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = fp.add_run("Trang "); r.font.size = Pt(11); r.font.name = FONT
    fld(fp, "PAGE", "1")
    for rr in fp.runs:
        rr.font.size = Pt(11); rr.font.name = FONT


# ----------------------------------------------------------------------------------------------- front matter
def front_matter():
    for _ in range(3):
        doc.add_paragraph()
    para("BÁO CÁO KỸ THUẬT", "FrontTitle")
    para("KIẾN TRÚC ỨNG DỤNG, SƠ ĐỒ HOẠT ĐỘNG CÁC CHỨC NĂNG VÀ GIAO DIỆN HỆ THỐNG", "FrontTitle")
    doc.add_paragraph()
    p = para("Đề tài: Xây dựng website bán mỹ phẩm OneShop theo mô hình chuỗi cửa hàng", align=WD_ALIGN_PARAGRAPH.CENTER, bold=True)
    para("Công nghệ: Spring Boot, Thymeleaf, Bootstrap, Spring Data JPA, SQL Server, SiteMesh, JWT, Cloudinary",
         align=WD_ALIGN_PARAGRAPH.CENTER)
    para("Phiên bản nghiệp vụ được mô tả: V3, xác minh từ source code và cơ sở dữ liệu đang chạy",
         align=WD_ALIGN_PARAGRAPH.CENTER)
    for _ in range(6):
        doc.add_paragraph()
    para("TP. Hồ Chí Minh, ngày %02d tháng %02d năm %d" % (TODAY.day, TODAY.month, TODAY.year), align=WD_ALIGN_PARAGRAPH.CENTER)
    md.append("# BÁO CÁO KỸ THUẬT: KIẾN TRÚC ỨNG DỤNG, SƠ ĐỒ HOẠT ĐỘNG VÀ GIAO DIỆN ONESHOP\n")
    md.append("Đề tài: Xây dựng website bán mỹ phẩm OneShop theo mô hình chuỗi cửa hàng. Công nghệ: Spring Boot, Thymeleaf, Bootstrap, "
              "Spring Data JPA, SQL Server, SiteMesh, JWT, Cloudinary. Ngày lập: %s.\n" % TODAY.strftime("%d/%m/%Y"))


def toc_pages():
    def lst(title, instr):
        p = doc.add_paragraph(style="FrontTitle")
        p.paragraph_format.page_break_before = True
        p.add_run(title)
        q = doc.add_paragraph()
        q.paragraph_format.first_line_indent = Cm(0)
        q.alignment = WD_ALIGN_PARAGRAPH.LEFT
        fld(q, instr, "(Bấm chuột phải, chọn Update Field để cập nhật)")
    lst("MỤC LỤC", 'TOC \\o "1-3" \\h \\z \\u')
    lst("DANH MỤC HÌNH", 'TOC \\h \\z \\c "Hình"')
    lst("DANH MỤC BẢNG", 'TOC \\h \\z \\c "Bảng"')
    lst("DANH MỤC ĐOẠN MÃ", 'TOC \\h \\z \\c "DoanMa"')
    md.append("\n*(Mục lục, danh mục hình, bảng, đoạn mã được tạo tự động trong tệp Word.)*\n")


# ----------------------------------------------------------------------------------------------- chapter 1
def chapter1():
    new_chapter("Chương 1. KIẾN TRÚC ỨNG DỤNG")
    heading("1.1. Sơ đồ kiến trúc ứng dụng", 2)
    md.append("\n## 1.1. Sơ đồ kiến trúc ứng dụng\n")
    p = para("Sơ đồ kiến trúc tổng thể của OneShop được dựng từ các thành phần thực sự có trong mã nguồn (cấu hình trong ", keep_next=False)
    key = "fig_arch"
    # figure first, text referencing it after: reserve the reference once the caption is known
    ref_par = p
    add_picture(os.path.join(ROOT, "diagrams", "architecture", "architecture.png"), max_h=19.5)
    caption("Hình", "Sơ đồ kiến trúc ứng dụng OneShop", key)
    md.append("![%s. Sơ đồ kiến trúc ứng dụng OneShop](diagrams/architecture/architecture.png)\n" % refs[key].replace("Hình", "Hình"))
    ref_par.add_run("SecurityConfig, WebConfig, SiteMeshConfig, các gói controller, service, repository và các lớp cấu hình Cloudinary, JWT, thư điện tử). ")
    ref(ref_par, key)
    # the paragraph is placed before the picture in the document order; finish its sentence
    ref_par.add_run(" trình bày các tầng và hướng giao tiếp.")
    md.append("Sơ đồ ở trên thể hiện các tầng thực tế của ứng dụng.\n")
    para("Chú thích các luồng số trong sơ đồ:", bold=True, keep_next=True, indent=False)
    flows = [
        "(1) Trình duyệt gửi yêu cầu HTTP tới ứng dụng; mọi yêu cầu đi qua chuỗi bộ lọc của Spring Security (kiểm tra CSRF, phân quyền theo URL) và JwtAuthenticationFilter, bộ lọc này đọc JWT từ cookie ONESHOP_TOKEN hoặc header Bearer.",
        "(2) Các Interceptor của Spring MVC xác định phạm vi: SelectedStoreInterceptor cho trang khách hàng, StaffStoreScopeInterceptor cho khu vực /staff.",
        "(3) Controller kiểm tra dữ liệu đầu vào (Bean Validation) và gọi tầng Service; Controller không chứa nghiệp vụ.",
        "(4) Service thực hiện nghiệp vụ trong giao dịch (@Transactional), gọi cổng thanh toán mô phỏng, Cloudinary hoặc máy chủ thư khi cần.",
        "(5) Repository (Spring Data JPA, Hibernate) truy vấn và ghi các Entity, có dùng khóa dòng (FOR UPDATE) ở các luồng tranh chấp.",
        "(6) SQL Server lưu dữ liệu qua JDBC; lược đồ do các tệp SQL quản lý, Hibernate chỉ kiểm tra (ddl-auto=validate).",
        "(7) Controller đặt dữ liệu vào Model, Thymeleaf dựng HTML, SiteMesh gắn layout theo khu vực rồi trả về trình duyệt.",
    ]
    for f in flows:
        para(f, indent=False)
        md.append("- " + f)
    md.append("")
    heading("1.2. Mô tả kiến trúc ứng dụng", 2)
    md.append("\n## 1.2. Mô tả kiến trúc ứng dụng\n")
    texts = [
        "OneShop là ứng dụng Spring Boot nguyên khối (Java 21, Maven) theo kiến trúc nhiều tầng kiểu MVC. Tầng trình bày gồm các mẫu Thymeleaf dựng HTML phía máy chủ, Bootstrap, một số tệp JavaScript tĩnh và SiteMesh, bộ lọc gắn một trong ba layout (khách hàng, nhân viên, quản trị viên) vào mọi trang HTML. Tầng điều khiển gồm 37 lớp Controller chia theo khu vực (client, staff, admin, api); phần REST gồm đăng nhập, đăng ký, kiểm tra sức khỏe (/health) và một số endpoint JSON gợi ý tìm kiếm. Tầng nghiệp vụ gồm các Service và các gói chuyên biệt: phân bổ nguồn hàng (allocation), xử lý phần giao (fulfillment), thanh toán và hoàn tiền (payment), khuyến mãi (promotion), trả hàng (returns), đánh giá (review) và xử lý ảnh (image). Tầng truy cập dữ liệu gồm 29 Repository Spring Data JPA trên các Entity ánh xạ tới 30 bảng SQL Server.",
        "Một yêu cầu được xử lý theo trình tự: bộ lọc bảo mật xác thực người dùng và kiểm tra quyền truy cập URL; Interceptor xác định chi nhánh đang xem hoặc chi nhánh được phân công; Controller kiểm tra dữ liệu rồi gọi Service; Service thực hiện quy tắc nghiệp vụ trong một giao dịch, đọc ghi qua Repository; kết quả quay lại Controller để Thymeleaf dựng giao diện hoặc để chuyển hướng (mẫu Post/Redirect/Get). Các lỗi nghiệp vụ được biểu diễn bằng ngoại lệ (BadRequestException, CheckoutConflictException, FulfillmentConflictException...) và Controller chuyển thành thông báo gắn đúng trường hoặc mã trạng thái HTTP phù hợp; giao dịch bị hoàn tác khi có ngoại lệ.",
        "Xác thực dùng JWT không trạng thái (không có phiên HTTP): token được lưu trong cookie HttpOnly, SameSite=Lax hoặc gửi bằng header Bearer cho API, nhưng vai trò và trạng thái tài khoản luôn được nạp lại từ cơ sở dữ liệu theo user_id nên khóa tài khoản hay đổi vai trò có hiệu lực ngay; biểu mẫu được bảo vệ bằng CSRF. Phân quyền theo URL gồm công khai (trang chủ, sản phẩm, cửa hàng, đăng nhập), CUSTOMER (giỏ hàng, đặt hàng, đơn hàng, đánh giá), STAFF (/staff/**) và ADMIN (/admin/**). Dữ liệu theo chi nhánh được tổ chức quanh StoreProduct (tồn kho của từng chi nhánh) và OrderFulfillment (phần giao của từng chi nhánh trong một đơn). Phạm vi của nhân viên là các phân công ACTIVE trong bảng staff_store_assignments, được đọc lại ở mỗi request và kiểm tra lại trên dòng dữ liệu đã khóa trước khi ghi; không bao giờ lấy từ tham số hay token.",
        "SQL Server lưu toàn bộ dữ liệu nghiệp vụ và bảo vệ tính toàn vẹn bằng khóa duy nhất, ràng buộc CHECK, chỉ mục duy nhất có lọc (ví dụ một ảnh chính cho mỗi sản phẩm) và khóa dòng khi tranh chấp (đặt hàng đồng thời, thanh toán, hủy). Cloudinary lưu hình ảnh sản phẩm, danh mục, thương hiệu và ảnh trả hàng; cơ sở dữ liệu chỉ giữ URL và public_id, việc xóa ảnh cũ thực hiện sau khi giao dịch thành công. Máy chủ SMTP (nếu cấu hình) dùng để gửi mã OTP quên mật khẩu. Thanh toán trực tuyến dùng cổng mô phỏng chạy trong ứng dụng (MockPaymentGateway), không kết nối ngân hàng và bị đóng cùng với việc đặt hàng ONLINE ở profile prod; tác vụ nền PaymentTimeoutWorker (mặc định tắt, bật bằng ONESHOP_PAYMENT_TIMEOUT_WORKER_ENABLED=true) hủy các đơn ONLINE quá hạn 15 phút và hoàn tồn, còn khi tắt thì đơn quá hạn được xử lý khi chủ đơn mở trang đơn hàng hoặc trang thanh toán, hoặc nằm trong nhóm "Cần xử lý" của quản trị viên. Nghiệp vụ hiện hành là V3 (một giá online trên Product, giỏ hàng theo sản phẩm, tồn kho theo StoreProduct, một Order có nhiều Fulfillment), được xác minh từ mã nguồn và cơ sở dữ liệu đang chạy.",
    ]
    for t in texts:
        para(t)
        md.append(t + "\n")


# ----------------------------------------------------------------------------------------------- chapter 2
def function_section(f, idx, sec_no, total_in_sec):
    num = "2.%d.%d" % (sec_no, idx)
    heading("%s. %s" % (num, f["title"]), 3)
    md.append("\n### %s. %s (%s)\n" % (num, f["title"], f["id"]))
    p = para("A. Mô tả chức năng", bold=True, keep_next=True, indent=False)
    md.append("**A. Mô tả chức năng**\n")
    para(f["desc"])
    md.append(f["desc"] + "\n")
    q = para("", indent=False)
    r = q.add_run("Tác nhân: %s. Trạng thái triển khai: %s." % (f["actor"], f["status"]))
    r.italic = True
    md.append("*Tác nhân: %s. Trạng thái triển khai: %s.*\n" % (f["actor"], f["status"]))

    para("B. Sơ đồ hoạt động", bold=True, keep_next=True, indent=False)
    md.append("**B. Sơ đồ hoạt động**\n")
    grp = {"KH": "customer", "QT": "admin", "NV": "staff"}[f["id"][:2]]
    png = os.path.join(ROOT, "diagrams", grp, f["id"] + ".png")
    add_picture(png, max_h=19.3)
    key = "fig_" + f["id"]
    cp = caption("Hình", "Sơ đồ hoạt động chức năng " + f["title"][0].lower() + f["title"][1:], key)
    cp.paragraph_format.keep_with_next = True
    note = doc.add_paragraph()
    note.alignment = WD_ALIGN_PARAGRAPH.CENTER
    nr = note.add_run("Ô xám: thao tác truy cập hoặc cập nhật cơ sở dữ liệu; hình thoi: điểm rẽ nhánh; nhánh bên phải: luồng lỗi hoặc dữ liệu không hợp lệ.")
    nr.italic = True; nr.font.size = Pt(10.5)
    note.paragraph_format.space_after = Pt(8)
    md.append("![%s. Sơ đồ hoạt động chức năng %s](diagrams/%s/%s.png)\n" % (refs[key], f["title"], grp, f["id"]))
    md.append("*%s. Sơ đồ hoạt động chức năng %s.* Mã nguồn sơ đồ: `diagrams/%s/%s.json`.\n" % (refs[key], f["title"], grp, f["id"]))

    para("C. Code xử lý chức năng", bold=True, keep_next=True, indent=False)
    md.append("**C. Code xử lý chức năng**\n")
    code_keys = []
    for ci, (cap_text, rel, a, b) in enumerate(f["codes"]):
        lines, ind = read_code(rel, a, b)
        ckey = "code_%s_%d" % (f["id"], ci)
        code_keys.append(ckey)
        c = caption("Đoạn mã", cap_text, ckey)
        c.alignment = WD_ALIGN_PARAGRAPH.LEFT
        c.paragraph_format.keep_with_next = True
        c.paragraph_format.space_after = Pt(2)
        pp = doc.add_paragraph()
        pp.paragraph_format.keep_with_next = True
        pp.paragraph_format.first_line_indent = Cm(0)
        pp.alignment = WD_ALIGN_PARAGRAPH.LEFT
        pr = pp.add_run("src/main/java/com/oneshop/%s:%d-%d" % (rel, a, b))
        pr.font.name = CODE_FONT; rfonts(pr._r, CODE_FONT); pr.font.size = Pt(9.5)
        code_block(lines)
        md.append("**%s. %s**\n\n`src/main/java/com/oneshop/%s` (dòng %d-%d)\n\n```java\n%s\n```\n" % (refs[ckey], cap_text, rel, a, b, "\n".join(lines)))

    para("D. Giải thích code", bold=True, keep_next=True, indent=False)
    md.append("**D. Giải thích code**\n")
    ep = para(f["explain"] + " Luồng này được thể hiện trong ")
    ref(ep, key)
    ep.add_run(" và mã nguồn tương ứng nằm trong ")
    for i, ck in enumerate(code_keys):
        if i:
            ep.add_run(", " if i < len(code_keys) - 1 else " và ")
        ref(ep, ck)
    ep.add_run(".")
    md.append(f["explain"] + " Luồng này được thể hiện trong %s và mã nguồn trong %s.\n" % (refs[key], ", ".join(refs[k] for k in code_keys)))


def chapter2():
    new_chapter("Chương 2. SƠ ĐỒ HOẠT ĐỘNG CÁC CHỨC NĂNG")
    intro = ("Chương này trình bày sơ đồ hoạt động (UML Activity Diagram) của từng chức năng nghiệp vụ đang được triển khai, theo ba nhóm tác nhân. "
             "Mỗi chức năng gồm bốn phần: mô tả, sơ đồ, đoạn mã nguồn thực tế trích từ kho mã (kèm đường dẫn và số dòng) và giải thích. "
             "Các sơ đồ dùng hai làn bơi (tác nhân và hệ thống); ô màu xám là thao tác truy cập hoặc cập nhật cơ sở dữ liệu. "
             "Căn cứ để vẽ là mã nguồn hiện hành; chỉ các chức năng có bằng chứng trong dự án mới được đưa vào.")
    para(intro)
    md.append(intro + "\n")
    for sec_no, (title, mod) in enumerate(((" Khách hàng", content_customer), (" Quản trị viên", content_admin), (" Nhân viên", content_staff)), 1):
        heading("2.%d.%s" % (sec_no, title), 2)
        md.append("\n## 2.%d.%s\n" % (sec_no, title))
        for i, f in enumerate(mod.FUNCS, 1):
            function_section(f, i, sec_no, len(mod.FUNCS))


# ----------------------------------------------------------------------------------------------- chapter 3
def screens(group, sec_no, title, intro):
    heading("3.%d. %s" % (sec_no, title), 2)
    md.append("\n## 3.%d. %s\n" % (sec_no, title))
    para(intro)
    md.append(intro + "\n")
    items = [(k, v) for k, v in MANIFEST.items() if v["group"] == group]
    items.sort(key=lambda kv: kv[0])
    for n, (sid, m) in enumerate(items, 1):
        heading("3.%d.%d. %s" % (sec_no, n, m["title"].replace("Giao diện ", "Giao diện ", 1)), 3)
        md.append("\n### 3.%d.%d. %s\n" % (sec_no, n, m["title"]))
        png = os.path.join(ROOT, "screenshots", group, sid + "_annotated.png")
        add_picture(png, max_h=19.8)
        key = "fig_" + sid
        c = caption("Hình", "%s (đường dẫn %s)" % (m["title"], m["url"].split("?")[0]), key)
        md.append("![%s. %s](screenshots/%s/%s_annotated.png)\n" % (refs[key], m["title"], group, sid))
        md.append("*%s. %s (đường dẫn %s).* Ảnh gốc: `screenshots/%s/%s.png`.\n" % (refs[key], m["title"], m["url"].split("?")[0], group, sid))
        # table title above table
        counters["Bảng"] += 1
        num = "%d.%d" % (counters["chap"], counters["Bảng"])
        tkey = "tbl_" + sid
        refs[tkey] = "Bảng " + num
        tp = doc.add_paragraph(style="Caption")
        tp.paragraph_format.keep_with_next = True
        bid = bookmark_start(tp, "_Ref_" + re.sub(r"\W", "_", tkey))
        tp.add_run("Bảng %d." % counters["chap"])
        fld(tp, "SEQ Bảng \\* ARABIC \\s 1", str(counters["Bảng"]))
        bookmark_end(tp, bid)
        tp.add_run(". " + m["tableTitle"].replace("Mô tả các thành phần giao diện", "Mô tả các thành phần giao diện", 1))
        rows = [(i, nt["label"], nt["desc"]) for i, nt in enumerate(m["notes"], 1)]
        table(["STT", "Thành phần giao diện", "Chức năng"], rows, [1.3, 4.7, 10.0])
        md.append("**%s. %s**\n\n| STT | Thành phần giao diện | Chức năng |\n|---|---|---|\n%s\n" % (
            refs[tkey], m["tableTitle"], "\n".join("| %d | %s | %s |" % (i, a, b) for i, a, b in rows)))


def chapter3():
    new_chapter("Chương 3. GIAO DIỆN HỆ THỐNG")
    base = ("Toàn bộ ảnh trong chương này được chụp từ website OneShop đang chạy thật (Spring Boot, cơ sở dữ liệu SQL Server cục bộ có dữ liệu mô phỏng), "
            "đăng nhập lần lượt bằng tài khoản thử nghiệm của khách hàng, quản trị viên và nhân viên, bằng trình duyệt Chrome với độ rộng cửa sổ 1280 px. "
            "Các số trên ảnh đánh dấu thành phần chức năng theo thứ tự từ trên xuống dưới, từ trái sang phải; mỗi số tương ứng một dòng của bảng liền sau ảnh. ")
    screens("customer", 1, "Giao diện khách hàng",
            base + "Nhóm này gồm các trang của khách vãng lai và khách hàng. Trang giỏ hàng và trang xác nhận đặt hàng được chụp với một sản phẩm tạm thêm vào giỏ, sau đó được xóa; "
                   "không có đơn hàng nào được tạo trong quá trình chụp. Trang thanh toán trực tuyến được chụp với một đơn đã thanh toán của khách thử nghiệm.")
    screens("admin", 2, "Giao diện quản trị viên",
            "Nhóm này gồm các trang của khu vực /admin. Các trang thêm hoặc sửa có cấu trúc tương tự (danh mục và thương hiệu, thêm và sửa chi nhánh, thêm và sửa mã khuyến mãi) được gộp bằng một trang đại diện. "
            "Trang yêu cầu trả hàng được chụp ở trạng thái rỗng vì dữ liệu mô phỏng chưa có yêu cầu trả hàng nào; không tạo yêu cầu giả để chụp.")
    screens("staff", 3, "Giao diện nhân viên",
            "Nhóm này gồm các trang của khu vực /staff, chụp với tài khoản nhân viên của chi nhánh Thủ Đức; mọi dữ liệu hiển thị chỉ thuộc chi nhánh được phân công. "
            "Trang hàng khách trả được chụp ở trạng thái rỗng vì chưa có yêu cầu trả hàng được duyệt.")


def main():
    setup_styles()
    header_footer()
    front_matter()
    toc_pages()
    chapter1()
    chapter2()
    chapter3()
    doc.core_properties.title = "OneShop - Báo cáo kiến trúc, sơ đồ hoạt động và giao diện"
    doc.core_properties.author = "OneShop"
    doc.save(OUT_DOCX)
    open(OUT_MD, "w", encoding="utf-8").write("\n".join(md))
    json.dump(refs, open(os.path.join(ROOT, "audit", "refs.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("saved", OUT_DOCX, counters)


if __name__ == "__main__":
    main()
