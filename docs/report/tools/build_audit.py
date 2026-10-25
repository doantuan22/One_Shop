# -*- coding: utf-8 -*-
"""Writes docs/report/audit/function_inventory.md and source_traceability.md from the same content modules that
feed the report, so the three documents cannot drift apart."""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import content_customer, content_admin, content_staff  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
MANIFEST = json.load(open(os.path.join(ROOT, "screenshots", "manifest.json"), encoding="utf-8"))
GROUPS = [("Customer", "customer", content_customer), ("Admin", "admin", content_admin), ("Staff", "staff", content_staff)]

SCREENS = {
    "KH-01": ["C05"], "KH-02": ["C04"], "KH-03": ["C06"], "KH-04": ["C07", "C01", "C02", "C03"], "KH-05": ["C08"], "KH-06": ["C09"],
    "KH-07": ["C08", "C10"], "KH-08": ["C10"], "KH-09": ["C11"], "KH-10": ["C11", "C12"], "KH-11": ["C18"],
    "KH-12": ["C13", "C14", "C16"], "KH-13": ["C14", "C16"], "KH-14": ["C16", "C17"], "KH-15": ["C15"], "KH-16": ["C19"], "KH-17": ["C19"],
    "QT-01": ["A02", "A03"], "QT-02": ["A04"], "QT-03": ["A05", "A06"], "QT-04": ["A07", "A08"], "QT-05": ["A09", "A10"], "QT-06": ["A10"],
    "QT-07": ["A11", "A12", "A13"], "QT-08": ["A14", "A15"], "QT-09": ["A15"], "QT-10": ["A16", "A17"], "QT-11": ["A18"],
    "QT-12": ["A19", "A20"], "QT-13": ["A21"], "QT-14": ["A01"],
    "NV-01": ["S01"], "NV-02": ["S01"], "NV-03": ["S02", "S03"], "NV-04": ["S04"], "NV-05": ["S05", "S03"], "NV-06": ["S06", "S07"],
    "NV-07": ["S08", "S09"], "NV-08": ["S10"], "NV-09": ["S11", "S12"],
}
SCREEN_NOTES = {
    "KH-13": "Nút Hủy đơn chỉ xuất hiện với đơn còn hủy được; các đơn dữ liệu mẫu của khách thử nghiệm đều đã giao hoặc đã hủy nên nút này không có trên ảnh chụp.",
    "QT-09": "Khối xử lý ngoại lệ trong chi tiết đơn chỉ hiện khi đơn NEEDS_ATTENTION; dữ liệu mẫu không có đơn như vậy nên giao diện này không được chụp.",
    "KH-11": "Nút 'Thanh toán' chỉ hiện với đơn ONLINE đang chờ thanh toán; ảnh chụp là trang của một đơn đã thanh toán.",
    "QT-11": "Danh sách yêu cầu trả hàng rỗng trong dữ liệu mẫu; chi tiết yêu cầu (nút Duyệt, Từ chối) không được chụp.",
    "NV-08": "Danh sách hàng khách trả rỗng trong dữ liệu mẫu.",
}


def inventory():
    out = ["# Bảng kiểm kê chức năng OneShop\n",
           "Lập ngày 25/10/2026 từ việc đọc mã nguồn (controller, service, repository, entity, template) và chạy thử website. "
           "Chỉ các chức năng có bằng chứng trong dự án mới được liệt kê; cột Trạng thái nêu rõ điều kiện hoặc hạn chế.\n",
           "| STT | Tác nhân | Chức năng | Controller | Service | Giao diện | Trạng thái |", "|---|---|---|---|---|---|---|"]
    n = 0
    for actor, _, mod in GROUPS:
        for f in mod.FUNCS:
            n += 1
            t = f["trace"]
            out.append("| %d | %s | %s (%s) | %s | %s | %s | %s |" % (n, actor, f["title"], f["id"], t["controller"], t["service"], t["ui"], f["status"]))
    out += ["", "## Các hạng mục đã khảo sát nhưng không có (hoặc chưa đầy đủ) trong hệ thống hiện hành\n",
            "| Hạng mục | Kết luận | Bằng chứng |", "|---|---|---|",
            "| Quản lý địa chỉ giao hàng của khách | Chưa có chức năng trên giao diện | AccountController đưa danh sách địa chỉ vào Model nhưng templates/account/profile.html không hiển thị; không có endpoint thêm, sửa, xóa địa chỉ. Địa chỉ nhận hàng được nhập trực tiếp ở trang xác nhận đặt hàng. |",
            "| Khách tự xác nhận nhận hàng | Không có | Hoàn tất phần giao và nhận tại cửa hàng do nhân viên thực hiện (StaffFulfillmentService.perform); khách chỉ xem mã nhận hàng. |",
            "| Admin trả lời đánh giá | Bị chặn có chủ đích | AdminReviewController.reply luôn ném AccessDeniedException; phản hồi thuộc nhân viên chi nhánh nguồn. |",
            "| Xử lý đơn theo cả Order của nhân viên (V2) | Chỉ còn là mã kế thừa | StaffOperationsService.lockedStaffEmail từ chối khi đơn có Fulfillment; các URL /staff/orders chỉ chuyển hướng đọc (StaffFulfillmentController). DeliveryFulfillmentServiceImpl và PickupFulfillmentServiceImpl không còn là luồng chính. |",
            "| Thanh toán qua cổng thật | Không có | Cổng thanh toán mô phỏng MockPaymentGateway; profile prod đóng thanh toán ONLINE. |",
            "| Đăng nhập bằng mạng xã hội, quên mật khẩu qua SMS | Không có | Không có mã nguồn tương ứng. |",
            "| Dashboard biểu đồ | Không có | Dashboard quản trị và nhân viên hiển thị thẻ số liệu và bảng. |",
            "| Thao tác dọn ảnh Cloudinary (retry) | Có endpoint, không có trang riêng | AdminImageCleanupController (POST /admin/images/cleanup/retry) không gắn với màn hình riêng; không đưa vào báo cáo như một chức năng giao diện. |",
            "", "## Khác biệt giữa tài liệu, cấu hình và mã đang chạy\n",
            "| Nội dung | Tài liệu/cấu hình ghi | Thực tế xác minh | Căn cứ được dùng trong báo cáo |", "|---|---|---|---|",
            "| Tài liệu đặc tả V3, kế hoạch migration, Roadmap V2 | README liên kết tới docs/OneShop_V3_DacTaNghiepVu_*.md, docs/v3/... | Thư mục docs của kho mã không chứa các tệp này | Mã nguồn và CSDL đang chạy; nghiệp vụ V3 được xác nhận bằng entity OrderFulfillment, giá trên Product, tồn trên StoreProduct |",
            "| Tác vụ nền hủy đơn ONLINE quá hạn | README và chú thích PaymentProperties nói mặc định TẮT | application.properties đặt oneshop.payment.timeout-worker-enabled=${...:true}; log khởi động: 'Payment timeout worker is ON' và đã hủy 2 đơn ONLINE quá hạn của dữ liệu mẫu | Báo cáo mô tả tác vụ là thành phần có thật và bật theo cấu hình mặc định hiện tại |",
            "| Tài khoản mô phỏng trong các script cũ | scripts/*.mjs dùng email @oneshop.vn | CSDL hiện có admin@example.com, staff.*@example.com, khachhang1..6@example.com | Ảnh chụp dùng tài khoản thật trong CSDL |",
            "| Số lượng dữ liệu mẫu | README: 40 SKU, 12 thương hiệu, 10 danh mục, 5 chi nhánh, 12 tài khoản | CSDL: 12 users, 40 products, 5 stores, 15 orders | Khớp |",
            ""]
    open(os.path.join(ROOT, "audit", "function_inventory.md"), "w", encoding="utf-8").write("\n".join(out))


def traceability():
    out = ["# Truy vết chức năng – mã nguồn – sơ đồ – giao diện\n",
           "Mỗi dòng nối một chức năng với các đoạn mã được trích trong báo cáo (đường dẫn tính từ src/main/java/com/oneshop/, kèm số dòng), "
           "tệp sơ đồ hoạt động và ảnh giao diện chụp từ website chạy thật.\n"]
    for actor, grp, mod in GROUPS:
        out.append("\n## %s\n" % actor)
        for f in mod.FUNCS:
            t = f["trace"]
            out.append("### %s. %s\n" % (f["id"], f["title"]))
            out.append("- Controller: %s" % t["controller"])
            out.append("- Service: %s" % t["service"])
            out.append("- Dữ liệu: %s" % t["data"])
            out.append("- Giao diện (template): %s" % t["ui"])
            out.append("- Sơ đồ: `diagrams/%s/%s.svg` (ảnh `.png`, mã nguồn sơ đồ `.json`)" % (grp, f["id"]))
            out.append("- Đoạn mã trích:")
            for cap, rel, a, b in f["codes"]:
                out.append("  - `src/main/java/com/oneshop/%s` dòng %d–%d: %s" % (rel, a, b, cap))
            shots = SCREENS.get(f["id"], [])
            if shots:
                out.append("- Ảnh giao diện: " + ", ".join("`screenshots/%s/%s_annotated.png` (%s, %s)" % (
                    MANIFEST[s]["group"], s, MANIFEST[s]["url"].split("?")[0], MANIFEST[s]["title"]) for s in shots if s in MANIFEST))
            if f["id"] in SCREEN_NOTES:
                out.append("- Ghi chú về ảnh giao diện: " + SCREEN_NOTES[f["id"]])
            out.append("")
    out.append("\n## Danh sách ảnh giao diện\n")
    out.append("| Mã ảnh | Đường dẫn | Tiêu đề | Số thành phần đánh số | Ảnh gốc |")
    out.append("|---|---|---|---|---|")
    for sid, m in sorted(MANIFEST.items()):
        out.append("| %s | %s | %s | %d | `screenshots/%s/%s.png` |" % (sid, m["url"], m["title"], len(m["notes"]), m["group"], sid))
    open(os.path.join(ROOT, "audit", "source_traceability.md"), "w", encoding="utf-8").write("\n".join(out))


if __name__ == "__main__":
    inventory()
    traceability()
    print("audit written")
