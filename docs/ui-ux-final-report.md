# Báo cáo hoàn thiện UI/UX OneShop

Ngày: 23/10/2026. Môi trường: Spring Boot dev (cổng 8095, SQL Server), Chrome headless qua CDP.
Số liệu thô: `artifacts/ui-audit/v2/` (`palette-before.json`, `palette-after.json`, `regression.json`, `axe.json`).
Ảnh before (1440×900): `artifacts/ui-audit/v2/before-1440/`. Ảnh after (1440×900): `screenshots/` (148 ảnh, 74 trang, kèm box mở rộng).
Ảnh 1024×768, 768×1024, 375×812 của 48 route: `artifacts/ui-audit/v2/responsive/<viewport>/<role>/`.

## 1. Cấu trúc (route → decorator → CSS)

- SiteMesh (`SiteMeshConfig`): `/*` → `layouts/client.html`; `/staff/**` → `layouts/staff.html`; `/admin/**` → `layouts/admin.html`.
- CSS chung nạp cuối bởi cả 3 decorator: `static/css/ui-system.css` (tokens `--os-*`, hệ nút, badge, tab, bảng). `admin-staff.css` chỉ cho Admin/Staff.
- Partial dùng chung: `fragments/components.html` (statusBadge, availability, dataTable, pageHeader), `staff/delivery/fragments.html` (state, payment), `admin/fragments.html`.
- Template theo route: `admin/*.html`, `admin/payments/*.html`, `staff/**`, `product/*`, `checkout/*`, `orders/*`, `payment/*`, `account/profile.html`, `home/index.html`.

## 2. Khóa bảng màu — bằng chứng

Không đổi hay thêm giá trị token nào. Màu các thành phần tham chiếu đọc từ computed style của trang chạy thật, trước và sau:

| Thành phần | Trước | Sau |
|---|---|---|
| Nút "Thêm vào giỏ" (chữ / nền / viền) | #fff / #ff7f50 / #ff7f50 | giống hệt |
| Header Admin và Staff (nền) | rgb(243,115,72) | giống hệt |
| Sidebar mục đang chọn | #fff / #ff7f50 | giống hệt |
| `.btn-primary` | #fff / #ff7f50 | giống hệt |
| Badge Đang hoạt động | #027a48 / #ecfdf3 / viền #abefc6 | giống hệt |
| Badge neutral | #475467 / #f2f4f7 / viền #e4e7ec | giống hệt |
| Đầu bảng, nền trang (Admin/Staff/Client) | — | giống hệt |

Khác biệt duy nhất là nút outline được đổi sang nút khối (có chủ đích).

Trong phiên làm việc trước tôi đã tự thêm vài mã màu (`#c2461f`, `#f4a58b`, `#f4825a`, `#c2410c`, `#eaecf0`, `#d92d20`). **Tất cả đã bị gỡ** theo ràng buộc mới. Rà lại CSS: không còn mã nào trong số đó do tôi thêm (`#c2410c` trong `product/list.html` có sẵn từ trước).

Token được tái sử dụng cho nút khối (`ui-system.css`):

| Variant | Class (giữ nguyên markup) | Nền / chữ | Hover | Token / mã có sẵn |
|---|---|---|---|---|
| primary | `.btn-primary` | #ff7f50 / trắng | #e86738 | `--os-brand`, `--os-brand-hover` |
| action (Chi tiết, Sửa, Điều chỉnh tồn, Xem tất cả...) | `.btn-outline-primary` | `--os-text-strong` / trắng | `--os-text` | `--os-text-strong`, `--os-text` |
| secondary (Lịch sử, Chi nhánh bán, Xóa lọc, Khác, quick actions) | `.btn-outline-secondary` | #f2f4f7 / `--os-text` | #e4e7ec | màu badge neutral có sẵn |
| danger (Ẩn, Bỏ phân công) | `.btn-outline-danger` | `--os-danger` / trắng | #912018 | `--os-danger`, #912018 có sẵn |

Ghi chú: giữ tên class `*-outline-*` của Bootstrap để không phải sửa href/form của hàng chục template; kiểu hiển thị đã là nút khối. Tab đang chọn dùng `--os-brand`; hover dùng `--os-brand-active`.

## 3. Thay đổi chính

| Vấn đề | File | Trước | Sau |
|---|---|---|---|
| Nút hành động hàng/tiêu đề dạng viền mảnh, không đồng bộ | `ui-system.css` | outline xanh dương Bootstrap, lẫn xám | 4 variant nút khối, cao 34px (40px mobile), min-width 3.75rem, focus ring rõ |
| Badge trạng thái đặc màu giống nút | `staff/delivery/fragments.html`, `fragments/components.html`, `staff/reviews/*`, `admin/order-detail.html`, `admin/product-form.html`, `orders/fragments.html` | `text-bg-*` đặc | `os-soft os-soft-*` nhẹ, map theo trạng thái; "Còn hàng" ở danh sách cửa hàng thành badge nhẹ |
| Còn hàng toàn hệ thống | `product/detail.html` | dot + chữ (đã đúng) | giữ nguyên, không phải nút |
| Dashboard: 4 nút/hàng tràn bảng | `admin/dashboard.html` | cột Thao tác bị cắt | 1 nút "Đơn hàng" + menu "Khác" |
| Link trần dưới KPI | `admin/dashboard.html` | 3 link xanh | 3 quick action nút secondary |
| Payments: cuộn ngang ở 1440 | `admin/payments/index.html`, `ui-system.css` | 1462px / 1138px | vừa 1138px, không cuộn ngang |
| Tab pill | `payments/index.html`, `staff/stock/inventory-history.html`, `ui-system.css` | cắt chữ, lệch màu | `os-tabs` dùng chung, tab chọn = màu brand |
| Staff inventory-history cuộn ngang | `staff/stock/inventory-history.html` | 1141px | vừa, style inline thành class |
| Form người dùng | `admin/user-form.html` | enum thô `STAFF`/`ACTIVE`, 1 cột | 2 cột, nhãn tiếng Việt, value gửi lên giữ nguyên |
| Ký tự `#` trước email/SKU | users, inventory, products, staff-assignments, store-products | `#admin@...` | đã bỏ |
| Chi nhánh ngừng hoạt động mất nút | `admin/stores.html` | cột Sửa lệch | nút "Sản phẩm" disabled |
| Phân trang | `admin/store-products.html` | kiểu riêng | dùng `os-pagination` chung |
| Bộ lọc ngắt dòng | staff fulfillments, inventory-history, admin orders | nút xuống dòng | `os-filter-grid--compact` |
| Copy loại A | `payment/index.html`, `admin/payments/detail.html` | "chỉ để trình diễn", "Giả lập câu trả lời" | câu nghiệp vụ, vẫn ghi rõ mô phỏng/không thu tiền thật |
| A11y | `product/list.html`, `account/profile.html` | select/input không tên | `aria-label` |

## 4. Rà soát ghi chú "mô phỏng"

- Loại A (kỹ thuật thừa): 2 vị trí đã viết lại.
- Loại B (nghiệp vụ): giữ nguyên.
- Loại C (thanh toán/hoàn tiền thử nghiệm): giữ nguyên và nhất quán, hiển thị tại checkout, trang thanh toán, đơn hàng, admin payments/order-detail, nhãn hoàn tiền. Không có chỗ nào nói đã hoàn tiền mà thiếu chú thích "(mô phỏng)".
- Giữ cách dùng chữ "mô phỏng" (không đổi thành "thử nghiệm") vì `CheckoutTemplateContractTest` khóa nguyên văn "Thanh toán trực tuyến (mô phỏng, không thu tiền thật)". Chuỗi nhãn "mô phỏng" trong Java (PaymentCaseCategory, service message) cũng không đổi vì thuộc phạm vi nghiệp vụ/test.

## 5. Kiểm thử trên web thật

| Hạng mục | Kết quả |
|---|---|
| 48 route × 4 viewport (1440×900, 1024×768, 768×1024, 375×812) = 192 lượt, 4 role | **PASS**: không overflow ngang body, không nút <30px, không chữ lỗi/raw template, không ô bị cắt; HTTP đúng (404/403 chỉ ở trang lỗi cố ý) |
| Bảng cuộn ngang ở 1440 | **PASS**: 0 trang (trước: payments, inventory-history). Ở ≤1024 bảng nằm trong khung cuộn riêng, trang không tràn |
| Link/form còn nguyên | **PASS**: dashboard giữ đủ 4 href mỗi chi nhánh; form người dùng giữ value enum; trang sửa user nạp đúng dữ liệu |
| Bàn phím/modal | **PASS**: focus ring 2px thấy được; modal mở → focus vào modal, Esc đóng, focus trả về nút mở |
| axe-core (WCAG 2 A/AA) 25 trang | **PASS** cho `label`/`select-name` sau khi sửa; còn `color-contrast` (xem mục 6) |
| `CheckoutTemplateContractTest` | **PASS** (4 test). Các test tích hợp DB không chạy để tránh ghi vào DB dev |
| Console | Chỉ 4 dòng 404/403 của trang lỗi chụp cố ý |

## 6. Vấn đề chưa xử lý (cần chủ dự án quyết định)

1. **Tương phản màu chữ trắng trên nền cam (2.49:1) và chữ cam trên nền sáng (2.2–2.5:1)** theo axe; là palette hiện có (nút "Thêm vào giỏ", "Đăng ký", header Admin, menu active, nhãn coral). Không đổi vì ràng buộc palette. Hướng không đổi màu: tăng cỡ/độ đậm chữ ở CTA, hoặc chủ dự án chấp nhận token chữ đậm hơn (`--brand-primary-text` #bf360c đã có sẵn trong CSS).
2. Một vài chữ phụ SKU `#98a2b3` trên nền trắng (2.57:1), chú thích ảnh `#94a3b8` (2.4:1), badge "Còn hàng toàn hệ thống" `#16a34a` (3.1:1): cùng nguyên nhân palette.
3. Ô ngày hiển thị `mm/dd/yyyy` vì lấy ngôn ngữ của trình duyệt/OS; dữ liệu đã hiển thị dd/MM/yyyy. Muốn cố định cần thư viện date picker.
4. Nội dung đánh giá seed có tiền tố "[Dữ liệu mô phỏng]": do `RealProductSeedIntegrityTest` yêu cầu; cần quyết định cùng seed data.
5. Nhãn "mô phỏng" trong Java và test (xem mục 4).
6. `/staff/orders` vẫn dùng chung dữ liệu với `/staff/fulfillments` (chỉ khác tiêu đề). Muốn tách nội dung cần đổi Controller.
7. Chưa kiểm thử tự động: regression phân quyền Staff A/B và Order/Payment/Refund V3 (không đụng service/controller; không chạy test tích hợp DB để khỏi thay đổi DB dev).
8. Chưa làm: radio card cho checkout (template đã có `selection-tile`, chưa kiểm lại từng trạng thái chọn); chuẩn hóa icon toàn bộ 4 layout; skeleton loading cho bảng. Các hạng mục này chưa được triển khai.
