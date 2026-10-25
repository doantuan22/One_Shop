# -*- coding: utf-8 -*-
"""Architecture diagram of OneShop built from components found in the source tree."""
from xml.sax.saxutils import escape
from PIL import ImageFont
F = ImageFont.truetype(r"C:\Windows\Fonts\arial.ttf", 15)
out = []
def wrap(t, w):
    ws, ls, c = t.split(), [], ""
    for x in ws:
        if F.getlength((c + " " + x).strip()) <= w: c = (c + " " + x).strip()
        else: ls.append(c); c = x
    ls.append(c); return ls
def box(x, y, w, h, text, fill="#fff", stroke="#333", bold=False, size=15, rx=10, dash=False):
    d = ' stroke-dasharray="6 4"' if dash else ""
    out.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{fill}" stroke="{stroke}" stroke-width="1.4"{d}/>')
    ls = []
    for part in text.split("\n"): ls += wrap(part, w - 16)
    ty = y + h / 2 - (len(ls) - 1) * 9 + 5
    for i, l in enumerate(ls):
        wgt = ' font-weight="bold"' if bold and i == 0 else ""
        out.append(f'<text x="{x+w/2}" y="{ty+i*18}" text-anchor="middle" font-size="{size}"{wgt}>{escape(l)}</text>')
def arrow(x1, y1, x2, y2, label=None, lx=None, ly=None, both=False):
    m = ' marker-start="url(#a)"' if both else ""
    out.append(f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="#222" stroke-width="1.6" marker-end="url(#a)"{m}/>')
    if label:
        out.append(f'<text x="{lx if lx is not None else x1+8}" y="{ly if ly is not None else (y1+y2)/2}" font-size="13" font-style="italic">{escape(label)}</text>')
def num(x, y, n):
    out.append(f'<circle cx="{x}" cy="{y}" r="11" fill="#e8590c"/><text x="{x}" y="{y+5}" text-anchor="middle" font-size="13" fill="#fff" font-weight="bold">{n}</text>')
def layer(x, y, w, h, title):
    out.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="8" fill="#f6f8fb" stroke="#8aa" stroke-width="1.2"/>')
    out.append(f'<text x="{x+10}" y="{y+19}" font-size="14" font-weight="bold" fill="#345">{escape(title)}</text>')

W, H = 1000, 1010
box(250, 14, 500, 62, "Trình duyệt (Client Browser)\nHTML do Thymeleaf dựng, Bootstrap, JavaScript (static/js)", fill="#fff4e6", bold=True)
box(40, 14, 170, 62, "Khách vãng lai / Khách hàng / Nhân viên / Quản trị viên", fill="#fff", size=13, rx=31, dash=True)
arrow(210, 45, 250, 45)
# application container
out.append('<rect x="20" y="118" width="700" height="760" rx="14" fill="#fcfcfc" stroke="#2b6cb0" stroke-width="2"/>')
out.append('<text x="36" y="142" font-size="16" font-weight="bold" fill="#2b6cb0">Ứng dụng Spring Boot OneShop (Java 21)</text>')
layer(36, 156, 668, 112, "1. Bộ lọc (Filter) và bảo mật")
box(48, 182, 200, 72, "Spring Security\n(SecurityConfig, phân quyền URL, CSRF)", size=13)
box(260, 182, 200, 72, "JwtAuthenticationFilter\n(JWT từ cookie ONESHOP_TOKEN hoặc Bearer)", size=13)
box(472, 182, 220, 72, "SiteMesh Filter\n(3 decorator: client, staff, admin)", size=13)
layer(36, 286, 668, 96, "2. Interceptor (Spring MVC)")
box(48, 310, 320, 60, "SelectedStoreInterceptor\n(chi nhánh khách đang xem, cookie)", size=13)
box(380, 310, 312, 60, "StaffStoreScopeInterceptor\n(chi nhánh nhân viên được phân công)", size=13)
layer(36, 400, 668, 112, "3. Tầng trình bày và điều khiển")
box(48, 426, 320, 72, "Controller\n(client, staff, admin, api: 37 lớp)", size=13)
box(380, 426, 312, 72, "Thymeleaf templates + Bootstrap\n(layouts/client, staff, admin, fragments)", size=13)
layer(36, 530, 668, 190, "4. Tầng nghiệp vụ (Service)")
box(48, 556, 156, 74, "Giỏ hàng, đặt hàng, phân bổ nguồn hàng (AllocationEngine), khuyến mãi", size=12)
box(214, 556, 156, 74, "Thanh toán mô phỏng, đối soát, hủy đơn, hoàn tiền, hoàn trả", size=12)
box(380, 556, 156, 74, "Fulfillment, tồn kho, hàng trả, phạm vi chi nhánh nhân viên", size=12)
box(546, 556, 146, 74, "Sản phẩm, chi nhánh, đánh giá, tài khoản, xác thực", size=12)
box(48, 644, 322, 62, "Mapper, DTO (record request / response)", fill="#eef7ee", size=13)
box(380, 644, 312, 62, "Tác vụ nền PaymentTimeoutWorker (@Scheduled)", fill="#eef7ee", size=13)
layer(36, 738, 668, 124, "5. Tầng truy cập dữ liệu")
box(48, 764, 644, 84, "Spring Data JPA / Hibernate: 29 Repository, các Entity (User, Product, StoreProduct, Order, OrderFulfillment, Payment, ...). ddl-auto=validate, giao dịch @Transactional, khóa dòng (FOR UPDATE)", size=13)
# externals
box(740, 628, 240, 90, "Cloudinary\nLưu ảnh sản phẩm, danh mục, thương hiệu, ảnh trả hàng (HTTPS)", fill="#e7f5ff", bold=True, size=13)
box(740, 736, 240, 80, "Máy chủ SMTP\nGửi mã OTP quên mật khẩu (nếu cấu hình MAIL_HOST)", fill="#e7f5ff", bold=True, size=13)
box(740, 520, 240, 92, "Cổng thanh toán MÔ PHỎNG\n(MockPaymentGateway, MockLedger: chạy trong ứng dụng, không kết nối ngân hàng)", fill="#fff9db", size=12)
box(200, 912, 400, 76, "SQL Server (cơ sở dữ liệu oneshop)\n30 bảng: users, products, store_products, orders, order_fulfillments, payments, ...", fill="#e6fcf5", bold=True)
# flows
arrow(500, 76, 500, 156, "HTTP request", 508, 108)
arrow(380, 76, 380, 156)
num(440, 106, 1)
arrow(370, 268, 370, 286); num(352, 277, 2)
arrow(370, 382, 370, 400); num(352, 391, 3)
arrow(370, 512, 370, 530); num(352, 521, 4)
arrow(370, 720, 370, 738); num(352, 729, 5)
arrow(400, 862, 400, 912, "JDBC (SQL Server)", 410, 892, both=True)
num(380, 886, 6)
arrow(704, 566, 740, 566, both=True); arrow(704, 673, 740, 673, both=True); arrow(704, 704, 740, 760)
# response path
out.append('<line x1="620" y1="156" x2="620" y2="76" stroke="#e8590c" stroke-width="1.8" stroke-dasharray="6 4" marker-end="url(#a)"/>')
out.append('<text x="632" y="98" font-size="13" font-style="italic" fill="#e8590c">HTML (đã gắn layout)</text>')
num(606, 116, 7)
svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" font-family="Arial, Helvetica, sans-serif">'
       '<defs><marker id="a" markerWidth="9" markerHeight="9" refX="8" refY="4.5" orient="auto-start-reverse"><path d="M0,0 L9,4.5 L0,9 z" fill="#222"/></marker></defs>'
       f'<rect width="{W}" height="{H}" fill="#fff"/>' + "".join(out) + "</svg>")
import os
d = os.path.join(os.path.dirname(__file__), "..", "diagrams", "architecture"); os.makedirs(d, exist_ok=True)
open(os.path.join(d, "architecture.svg"), "w", encoding="utf-8").write(svg)
print("ok")
