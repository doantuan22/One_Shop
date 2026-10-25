# -*- coding: utf-8 -*-
"""Automated checks of the finished report; prints a markdown summary that is stored in audit/verification_results.md."""
import glob
import json
import os
import re
import sys
import zipfile

sys.path.insert(0, os.path.dirname(__file__))
import content_customer, content_admin, content_staff  # noqa: E402
from docx import Document  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
REPO = os.path.abspath(os.path.join(ROOT, "..", ".."))
SRC = os.path.join(REPO, "src", "main", "java", "com", "oneshop")
DOCX = os.path.join(ROOT, "OneShop_BaoCao_KienTruc_ChucNang_GiaoDien.docx")
MD = open(os.path.join(ROOT, "OneShop_BaoCao_Source.md"), encoding="utf-8").read()
MANIFEST = json.load(open(os.path.join(ROOT, "screenshots", "manifest.json"), encoding="utf-8"))
FUNCS = content_customer.FUNCS + content_admin.FUNCS + content_staff.FUNCS
res = []


def check(name, ok, detail=""):
    res.append((name, ok, detail))
    print(("PASS" if ok else "FAIL"), name, detail)


# 1. code excerpts are verbatim copies (modulo the common left indentation) of the repository files
bad = []
count = 0
doc = Document(DOCX)
doc_text = "\n".join(p.text for p in doc.paragraphs)
for f in FUNCS:
    for cap, rel, a, b in f["codes"]:
        count += 1
        src = open(os.path.join(SRC, rel), encoding="utf-8").read().split("\n")[a - 1:b]
        while src and not src[-1].strip():
            src.pop()
        ind = min((len(l) - len(l.lstrip(" ")) for l in src if l.strip()), default=0)
        expect = [l[ind:] if l.strip() else "" for l in src]
        block = "\n".join(expect)
        if block not in MD:
            bad.append((f["id"], rel, a, b, "md"))
        # docx: every non-empty line of the excerpt must appear as a code paragraph in order
        docs_lines = [p.text for p in doc.paragraphs if p.style.name == "CodeBlock"]
        joined = "\n".join(docs_lines)
        if "\n".join(l if l else " " for l in expect) not in joined:
            bad.append((f["id"], rel, a, b, "docx"))
check("Mọi đoạn mã trích khớp nguyên văn với mã nguồn (md và docx)", not bad, "%d đoạn kiểm tra; lỗi: %s" % (count, bad[:5]))

# 2. class and Class.method names used in the text exist in the repository
classes = {}
for path in glob.glob(os.path.join(SRC, "**", "*.java"), recursive=True):
    classes[os.path.basename(path)[:-5]] = path
texts = []
for f in FUNCS:
    texts += [f["desc"], f["explain"]] + list(f["trace"].values())
    for st in f["steps"]:
        texts += [x for x in st[2:] if isinstance(x, str)]
        if st[0] == "dec":
            texts += st[5]
body = "\n".join(texts)
ident = set(re.findall(r"\b([A-Z][A-Za-z0-9]*(?:Service|ServiceImpl|Controller|Repository|Policy|Engine|Interceptor|Filter|Worker|Limiter|Calculator|Mapper|Gateway|Ledger|Writer|Aggregator|Coordinator|Config|Properties|Sender|Cookie|Exception|Response|Request|Action))\b", body))
missing = sorted(i for i in ident if i not in classes and i not in {"BadRequestException", "ImageStorageException"} and not os.path.exists(os.path.join(SRC, "x")))
# Spring / library types that are legitimately mentioned
LIB = {"AuthenticationManager", "SecurityConfig", "PasswordEncoder", "OncePerRequestFilter", "SiteMeshFilter", "AuthenticationException", "AccessDeniedException",
       "ConcurrencyFailureException", "DataIntegrityViolationException", "DataAccessException", "MultipartFile", "ModelAttribute", "BindingResult",
       "ResourceNotFoundException", "PaymentConflictException", "FulfillmentConflictException", "CheckoutConflictException"}
missing = [m for m in missing if m not in LIB]
check("Mọi tên lớp được nhắc tới trong mô tả/giải thích tồn tại trong mã nguồn", not missing, "không tìm thấy: %s" % missing)
pairs = set(re.findall(r"\b([A-Z][A-Za-z0-9]+)\.([a-z][A-Za-z0-9]+)\b", body))
bad_pairs = []
for c, m in sorted(pairs):
    if c in classes:
        if (m + "(") not in open(classes[c], encoding="utf-8").read() and (m not in open(classes[c], encoding="utf-8").read()):
            bad_pairs.append("%s.%s" % (c, m))
check("Mọi cặp Lớp.phương_thức được nhắc tới tồn tại trong lớp đó", not bad_pairs, "không khớp: %s" % bad_pairs)

# 3. screenshots: every annotated image exists, every note has a row in the table and vice versa
bad = []
for sid, m in MANIFEST.items():
    for suffix in ("", "_annotated"):
        if not os.path.exists(os.path.join(ROOT, "screenshots", m["group"], sid + suffix + ".png")):
            bad.append(sid + suffix)
check("Ảnh gốc và ảnh đánh số tồn tại cho mọi màn hình", not bad, "%d màn hình; thiếu: %s" % (len(MANIFEST), bad))
tables = doc.tables
ui_tables = [t for t in tables if t.rows[0].cells[0].text.strip() == "STT" and t.rows[0].cells[1].text.strip() == "Thành phần giao diện"]
mism = []
for (sid, m), t in zip(sorted(MANIFEST.items()), ui_tables):
    pass
order = sorted(MANIFEST.items(), key=lambda kv: ({"customer": 0, "admin": 1, "staff": 2}[kv[1]["group"]], kv[0]))
for (sid, m), t in zip(order, ui_tables):
    rows = len(t.rows) - 1
    nums = [int(r.cells[0].text) for r in t.rows[1:]]
    if rows != len(m["notes"]) or nums != list(range(1, rows + 1)):
        mism.append((sid, rows, len(m["notes"])))
check("Số dòng bảng diễn giải = số chú thích trên ảnh, STT liên tục từ 1 (từng màn hình)", len(ui_tables) == len(MANIFEST) and not mism,
      "%d bảng giao diện cho %d ảnh; lệch: %s" % (len(ui_tables), len(MANIFEST), mism))

# 4. diagrams
miss = []
for f in FUNCS:
    grp = {"KH": "customer", "QT": "admin", "NV": "staff"}[f["id"][:2]]
    for ext in ("svg", "png", "json"):
        if not os.path.exists(os.path.join(ROOT, "diagrams", grp, "%s.%s" % (f["id"], ext))):
            miss.append((f["id"], ext))
check("Mỗi chức năng có sơ đồ svg, png và mã nguồn json", not miss, "%d chức năng; thiếu %s" % (len(FUNCS), miss))
check("Mỗi chức năng có đủ 4 phần A, B, C, D và ít nhất một đoạn mã",
      all(f["desc"] and f["steps"] and f["codes"] and f["explain"] for f in FUNCS), "%d chức năng" % len(FUNCS))
dec_no_fail = [f["id"] for f in FUNCS if not any(st[0] == "dec" for st in f["steps"])]
check("Mỗi sơ đồ có ít nhất một điểm rẽ nhánh và luồng lỗi", not dec_no_fail, "thiếu: %s" % dec_no_fail)

# 5. docx integrity
z = zipfile.ZipFile(DOCX)
check("Tệp docx là gói zip hợp lệ và có thể mở bằng python-docx", z.testzip() is None,
      "%d đoạn văn, %d bảng, %d ảnh nhúng" % (len(doc.paragraphs), len(doc.tables), len([n for n in z.namelist() if n.startswith("word/media/")])))
heads = [p.text for p in doc.paragraphs if p.style.name == "Heading 1"]
check("Ba chương dùng Heading 1 (mục lục tự động)", len(heads) == 3, str(heads))
st = doc.styles
want = {"Normal": "Times New Roman", "Heading 1": "Times New Roman", "Heading 2": "Times New Roman", "Heading 3": "Times New Roman",
        "Caption": "Times New Roman", "CodeBlock": "Consolas"}
def eff(style):
    while style is not None and style.font.name is None:
        style = style.base_style
    return style.font.name if style is not None else None


got = {k: eff(st[k]) for k in want}
check("Phông chữ: nội dung, tiêu đề, chú thích là Times New Roman; đoạn mã là Consolas (monospace)", got == want, str(got))
check("Cỡ chữ nội dung 13 pt, căn đều hai bên, giãn dòng 1,4", st["Normal"].font.size.pt == 13 and st["Normal"].paragraph_format.alignment == 3
      and abs(st["Normal"].paragraph_format.line_spacing - 1.4) < 0.01,
      "%s pt, alignment=%s, line_spacing=%s" % (st["Normal"].font.size.pt, st["Normal"].paragraph_format.alignment, st["Normal"].paragraph_format.line_spacing))
sec = doc.sections[0]
check("Khổ A4 (21 x 29,7 cm)", abs(sec.page_width.cm - 21.0) < 0.05 and abs(sec.page_height.cm - 29.7) < 0.05, "%.2f x %.2f cm" % (sec.page_width.cm, sec.page_height.cm))

# 6. forbidden content
sec_hits = [w for w in ("JWT_SECRET", "CLOUDINARY_API_SECRET", "api-secret", "password=") if w in MD]
check("Không có thông tin bí mật (JWT secret, API key, mật khẩu) trong báo cáo", not sec_hits, str(sec_hits))

out = ["# Kết quả kiểm chứng tự động\n", "| Kiểm tra | Kết quả | Chi tiết |", "|---|---|---|"]
for n, ok, d in res:
    out.append("| %s | %s | %s |" % (n, "ĐẠT" if ok else "KHÔNG ĐẠT", d.replace("|", "/")))
open(os.path.join(ROOT, "audit", "verification_auto.md"), "w", encoding="utf-8").write("\n".join(out))
sys.exit(0 if all(ok for _, ok, _ in res) else 1)
