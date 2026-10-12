# OneShop – Audit UI/UX hiện trạng

> Phạm vi: **chỉ đọc và ghi nhận**. Không sửa code, không đổi schema, không đề xuất đổi stack. Tông màu đã chốt (coral `#FF7F50`) được giữ nguyên.
> Ngày audit: 12/10/2026. Nhánh `tuntun`, commit `130af6b` cộng thay đổi chưa commit: giao diện từ `One_Shop_UIUX` đã được trộn vào `src/main/resources` (thư mục `One_Shop_UIUX/` bị bỏ qua, không audit riêng).

---

## 0. Tóm tắt

### 0.1 Phương pháp và phạm vi

| Hạng mục | Nội dung |
| --- | --- |
| Phương pháp | **Phân tích tĩnh.** Đọc template Thymeleaf, CSS, JS, controller, service, repository, schema `database/*.sql` và seed. **Không chạy ứng dụng để duyệt từng màn hình ở 375/768/1280px**: không có SQL Server + Cloudinary + JWT secret dùng được trong phiên này, và việc audit không được sửa/cấu hình môi trường. |
| Bằng chứng gián tiếp | Trước đó, trong cùng phiên, các test web đã chạy qua render HTML thật (`*WebIntegrationTest`, `LayoutsIntegrationTest`, `AuthFlowIntegrationTest`, `AccessControlIntegrationTest`, `Phase1*Test`: 185 test pass). Chúng chứng minh template render được với dữ liệu mock, **không** chứng minh giao diện đẹp/đúng ở từng viewport. |
| Tham chiếu | `docs/audit/full-system/02_Frontend_UI_UX_Audit.md` (audit Chrome thật ở commit `88c2213`, **trước** khi trộn giao diện mới). Không dùng lại kết luận của file đó; mọi nhận định dưới đây đều tự rút ra từ mã hiện tại. |
| Đã xem | Toàn bộ 56 template trong `src/main/resources/templates`, 6 file CSS + `main.js`, 32 file controller (`client/staff/admin/api/web`), các repository/service lọc – sắp xếp – phân trang, `02_create_tables.sql`, `03_create_indexes.sql`, `04_seed_data.sql`. |
| Chưa xem | Hiển thị thực tế ở trình duyệt (khoảng cách, cuộn ngang, chồng lấn, focus ring), hiệu năng thực (thời gian tải, số request), collation của DB đang chạy thật, dữ liệu thật ngoài seed. Mỗi điểm liên quan được ghi "chưa xác minh" ở dưới. |

### 0.2 10 vấn đề UX nghiêm trọng nhất

| # | Vấn đề | Màn hình bị ảnh hưởng | ID |
| --- | --- | --- | --- |
| 1 | Layout Client nạp `/css/bootstrap.min.css` (file không tồn tại → 404); fragment `head-resources.html` trỏ tới `libs.bundle.css`, `theme.bundle.css` cũng không tồn tại. Nhiều trang Client nạp thêm Bootstrap 5.3.3 từ CDN đè lên Bootstrap 5.3.8 cục bộ. | Toàn bộ Client | UX-001 |
| 2 | Không có sắp xếp ở bất kỳ danh sách nào của Client, Staff, Admin (thứ tự cứng trong repository). | Catalog, đơn hàng, tồn kho… | UX-002 |
| 3 | Catalog thiếu lọc đã có sẵn dữ liệu: khoảng giá, "chỉ còn hàng", và khi đã chọn chi nhánh thì không có lọc theo tình trạng hàng; không có nút "Xóa lọc". | `/products` | UX-003 |
| 4 | Đơn hàng của khách (`/orders`) không phân trang, không lọc theo trạng thái/ngày, chỉ có một danh sách thẻ dài. Hiển thị trạng thái đơn bằng badge xám thống nhất cho mọi trạng thái. | `/orders` | UX-004 |
| 5 | Staff không có bất kỳ bộ lọc/tìm kiếm nào (đơn, hàng chờ nhận, tồn kho, lịch sử tồn kho), kể cả khi được phân công nhiều chi nhánh. | `/staff/orders`, `/staff/pickup`, `/staff/stock` | UX-005 |
| 6 | Giỏ hàng: ô "Tạm tính" và "Số lượng mặt hàng" luôn là tổng cả giỏ, **không cập nhật theo các dòng được chọn**; mỗi lần đổi số lượng là reload cả trang. Nút Xóa hiện 2 hộp xác nhận (do 2 script cùng gắn `data-confirm`). | `/cart` | UX-006 |
| 7 | Hành động có hậu quả không có xác nhận: hủy đơn, ẩn đánh giá, bỏ phân công Staff, vô hiệu hóa user, điều chỉnh tồn, "Mô phỏng thất bại và hủy đơn". Không có chống bấm lặp. | Nhiều màn hình | UX-007 |
| 8 | Trạng thái đơn/thanh toán/tồn kho hiển thị không nhất quán: badge xám cho mọi trạng thái đơn; `checkout/result` có 3 màu riêng; Admin và Staff lộ enum thô (`DELIVERY`, `COD · UNPAID`, `ACTIVE`, `STOCK_ADJUST`). | Đơn hàng, Admin, Lịch sử tồn kho | UX-008 |
| 9 | Màu coral `#FF7F50` làm chữ trắng trên nút và chữ link trên nền trắng chỉ đạt **2,50:1** (chuẩn WCAG AA cần 4,5:1). | Mọi nút `.btn-primary`, link | UX-009 |
| 10 | Staff header cao 112px và sticky, còn sidebar sticky được tính theo `--os-appbar-height: 56px` → sidebar bị header che 56px đầu và cắt 56px cuối trên desktop; trên mobile header 112px chiếm phần lớn khung nhìn. | Toàn bộ `/staff/**` | UX-010 |

---

## 1. Nền tảng giao diện

### 1.1 Thư viện và tài nguyên

| Thành phần | Hiện trạng | Bằng chứng |
| --- | --- | --- |
| Bootstrap CSS/JS | **5.3.8** cục bộ (`bootstrap.min.css` 232 KB, `bootstrap.bundle.min.js` 80 KB) | `static/vendor/bootstrap/*` (banner phiên bản) |
| Bootstrap qua CDN | **5.3.3** (`cdn.jsdelivr.net`) được `<link>` lại trong 8 trang Client: `auth/login`, `auth/register`, `cart/index`, `checkout/index`, `checkout/result`, `product/detail`, `product/list`, `store/list` | dòng 7–9 mỗi file |
| Icon set | **Không dùng icon font**. Icon là `<svg>` nội tuyến lặp lại trong từng trang. `static/fonts/remixicon.woff` có mặt nhưng không có CSS nào `@font-face` hay class `ri-*` tham chiếu (grep trống) | `static/fonts/`, grep `remixicon` |
| JS | Chỉ `main.js` (4 KB) + `bootstrap.bundle`. Mỗi trang giỏ/checkout/chi tiết SP có `<script>` nội tuyến riêng. Không jQuery, không thư viện biểu đồ, không thư viện ngày | `static/js/main.js`, `templates/**/*.html` |
| Font | **Không khai báo font** (không `@font-face`, không `font-family` trong CSS tự viết) → dùng font stack mặc định của Bootstrap | grep `font-family` trong `static/css`, `templates` trống |
| Ảnh | `static/images/One.png` (logo). Ảnh sản phẩm lấy từ Cloudinary URL | `fragments/navbar.html:175` |
| CSS tự viết | `main.css` 4 KB, `oneshop.css` 2,4 KB, `bootstrap-coral.css` 5,5 KB, `home.css` 7 KB, `soyesencial-components.css` 6,7 KB, `custom.css` **0 byte** | `static/css/` |

### 1.2 Cấu trúc SiteMesh

Cấu hình: `config/SiteMeshConfig.java`. `DecoratorController` forward về `layouts/{client,staff,admin}` và đặt `pageTitle/pageHead/pageBody/currentPath`.

| Đường dẫn | Decorator | Layout | Ghi chú |
| --- | --- | --- | --- |
| `/*` (mặc định) | `/decorators/client` | `layouts/client.html` | gồm cả `/login`, `/register`, `/error` do controller render |
| `/staff`, `/staff/*` | `/decorators/staff` | `layouts/staff.html` | `.addDecoratorPath("/staff/*")` – SiteMesh `*` chỉ khớp một cấp (xem ghi chú dưới) |
| `/admin`, `/admin/*` | `/decorators/admin` | `layouts/admin.html` | tương tự |
| Loại trừ | `/decorators/*`, `/api/*`, `/health`, `/css/*`, `/js/*`, `/images/*`, `/vendor/*` | – | – |

> **Chưa xác minh:** `/staff/*` và `/admin/*` của SiteMesh có khớp cả đường dẫn nhiều cấp (`/staff/orders/delivery/5`, `/admin/products/3/edit`) hay không. `LayoutsIntegrationTest` đã pass nên nhiều khả năng có, nhưng chưa đọc kỹ từng test để kết luận cho mọi đường dẫn nhiều cấp.

**Phần nằm ở layout:** `<head>` chung (`fragments/assets :: head` = Bootstrap cục bộ + `main.css`), title, header chi nhánh + navbar + footer (Client), topbar + sidebar (Staff/Admin), `bootstrap.bundle` + `main.js`.

**Phần lặp lại trong từng trang (lẽ ra nên ở layout):**

- Thẻ `<link>` Bootstrap CDN + `bootstrap-coral.css` + `home.css` ở 8 trang Client (vì SiteMesh gộp `<head>` của trang vào sau `assets :: head`).
- Khối `<style>` riêng: **23 khối** trong 23 template. Tổng cộng 565 mã hex màu nằm trong template.
- `<meta charset>` và `<meta viewport>` bị lặp trong các trang Client đầy đủ (trong khi layout đã có).
- Hệ breadcrumb ở `product/detail`, `checkout/index` là markup riêng (`breadcrumb-coral`, `breadcrumb-clean`), các trang khác không có breadcrumb.

**Thứ tự nạp CSS của Client** (theo `layouts/client.html` + `assets.html`): Bootstrap 5.3.8 local → `main.css` → `pageHead` (CDN Bootstrap 5.3.3 → `bootstrap-coral.css` → `home.css` → `<style>` của trang) → `/css/bootstrap.min.css` (**404**) → `oneshop.css` (cuối cùng). Hệ quả: `oneshop.css` được nạp sau và định nghĩa `.btn-primary` bằng `#ff6f61`, khác với `#FF7F50` đã chốt (xem 1.3, 7).

### 1.3 Design tokens

| Nhóm | Hiện có | Nơi định nghĩa |
| --- | --- | --- |
| Màu primary | `--os-primary: #ff7f50` | `main.css:2` |
| | `--bs-primary: #FF7F50`, `--bs-primary-hover: #e86738`, `--bs-primary-active: #cc5427` | `bootstrap-coral.css:11–14` |
| | `--os-coral: #ff6f61`, `--os-coral-dark: #e95b50`, `--os-coral-light: #fff0ed` | `oneshop.css:2–4` |
| | `--os-staff-coral: #FF7F50`, `--os-staff-coral-hover: #e86738` | `fragments/staff.html` (`<style>`) |
| | `:root` riêng trong `cart/index.html:11` | `cart/index.html` |
| Màu nền | `--os-bg #fff8fa` (Client), `#f6f7fb` (Staff/Admin `.os-app`), `#fff5f2`, `#ffede6`, `#fffaf8` (nhiều trang) | `main.css`, `bootstrap-coral.css`, template |
| Màu chữ | `--os-text #292d32`, `--os-muted #707780`, `#0f172a`, `#1e293b`, `#475569`, `#64748b` (hệ xám slate trong template) | `oneshop.css`, template |
| Font / cỡ chữ | Không có biến; dùng `h1…h6`, `.h3`, `.h5`, `.small` của Bootstrap, thêm `font-size` hard-code trong `<style>` từng trang | – |
| Khoảng cách | Không có biến; dùng utility Bootstrap (`g-3`, `py-4`…) + `padding`/`gap` hard-code | – |
| Bo góc | `0.5rem` (main.css), `18px` (`.os-card`), `24px` (`.os-hero`), nhiều giá trị khác trong template | `main.css`, `oneshop.css` |
| Đổ bóng | `0 10px 25px #292d3210` (`.os-card:hover`), `0 2px 10px rgba(44,48,56,.05)` (staff header), nhiều biến thể trong template | `oneshop.css`, `fragments/staff.html` |

**CSS variables:** có, nhưng ở **bốn hệ tên** khác nhau (`--os-*`, `--bs-*`, `--os-coral*`, `--os-staff-coral*`) và chủ yếu **không được dùng trong các khối `<style>` của trang** – các khối đó hard-code hex. Thống kê trong `templates/`:

| Chỉ số | Số lượng |
| --- | --- |
| Thuộc tính `style="…"` | **4** (`checkout/index` 1, `product/detail` 2, `staff/reviews/index` 1) |
| Khối `<style>` | **23** |
| Mã hex trong template | **565** |
| Hex cùng loại coral: `#ff7f50` 70, `#e86738` 23, `#e95b50` 10 (cộng `#ff6f61` trong CSS) | – |
| Hex slate lạc tông: `#0f172a` 15, `#64748b` 16, `#e2e8f0` 17, `#f1f5f9` 13, `#1e293b` 8, `#f8fafc` 10, `#cbd5e1` 7, `#475569` 6 | – |

### 1.4 Fragment/component dùng chung và chỗ lặp

**Hiện có** (`fragments/components.html`, `admin/fragments.html`, `staff/*/fragments.html`, `orders/fragments.html`):

| Component | Nơi định nghĩa | Dùng ở |
| --- | --- | --- |
| `pageHeader(title, subtitle)` | components | Staff, Admin, `store/list`, `product/list` (không dùng) |
| `price(value)` | components | mọi khu vực |
| `availability(inStock)` | components | catalog, chi tiết SP |
| `statusBadge(status)` | components | Admin (ACTIVE/HIDDEN/INACTIVE) |
| `statCard`, `dataTable`, `emptyRow`, `emptyState`, `formField`, `formSelect`, `modal`, `card`, `navItem` | components | `formField`, `formSelect`, `modal`, `card` **không được template nào dùng** (grep không thấy lời gọi) |
| `state(status)`, `payment(status)`, `method(method)`, `action(order)` | `staff/delivery/fragments.html` | Staff, Admin **và Client** (`orders/index`, `orders/fragments`) – Client phụ thuộc vào file thuộc khu vực Staff |
| `table(rows)` | `staff/orders/fragments.html` | Staff dashboard, đơn, hàng chờ |
| `detail(detail)` | `orders/fragments.html` | Client + Staff chi tiết đơn |
| `storeFilter(path)`, `pagination(...)` | `admin/fragments.html` | Admin (4 màn hình) |

**Component viết nhiều phiên bản:**

| Thành phần | Số phiên bản | Chi tiết |
| --- | --- | --- |
| Phân trang | **5** | (a) `product/list` – nút `« Trước` / `Sau »` dạng `.pagination`; (b) `admin/fragments :: pagination` – `Trước`/`Sau`; (c) `admin/products`, `admin/store-products` – `.pagination` viết trực tiếp, không dùng fragment; (d) `staff/orders`, `staff/pickup/queue`, `staff/stock`, `staff/reviews` – nút `Trang trước` / `Trang sau` dạng `.btn`; (e) không có (xem 3.x). Không bản nào cho nhảy tới trang cụ thể |
| Thẻ/nút lọc | 4 | `.filter-panel` (Client), `.row.g-2` (Admin), `d-flex gap-2` (admin/users), không có (Staff) |
| Badge trạng thái đơn | 3 | `state` (luôn `text-bg-secondary`), `.status-badge-*` ở `checkout/result` (3 màu), text thuần ở `payment/index` |
| Page heading | 3 | `pageHeader`; `.os-page-heading` ở `staff/pickup/index`; `<h1 class="h3">` trực tiếp ở `orders/index`, `payment/index`, `admin/store-products` (inventory view) |
| Trạng thái rỗng | 4 | `emptyState` (Client list), `emptyRow` fragment (không ai gọi), `<tr><td colspan>` viết tay, `.os-empty-state` |
| Thông báo | 3 | `alert alert-success` theo `param.success`; `alert` + `th:switch` ở cart; alert tùy biến `.cart-alert` |
| Thẻ sản phẩm | 1 | `.modern-product-card` – chỉ ở `product/list`; giỏ hàng dùng `.cart-item-row` riêng |
| SVG icon | ~60+ bản sao | Cùng icon (địa chỉ, điện thoại, giờ, giỏ hàng) được dán lại trong nhiều trang, không có sprite/fragment |

---

## 2. Bản đồ màn hình

Ký hiệu: **L** = lọc, **S** = sắp xếp, **P** = phân trang. `–` = không có. Kích thước trang lấy từ hằng số trong controller/service.

### 2.1 Client (layout `client`)

| Khu vực | Route | Template | Vai trò | Dữ liệu chính | Thành phần chính | L | S | P (cỡ) | Rỗng/lỗi | Ghi chú |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Client | `GET /` | `home/index` | Public | Chỉ nội dung tĩnh | Hero, 3 thẻ hướng dẫn | – | – | – | – | Không có sản phẩm nổi bật, không có tìm kiếm nhanh |
| Client | `GET /products` | `product/list` | Public | Catalog toàn chuỗi hoặc theo chi nhánh | Hero, ô tìm, 2 select, lưới thẻ SP, phân trang | Có (`q`, `categoryId`, `brandId`) | **Không** | Có (12) | Có (`emptyState`) | Không có nút xóa lọc; không báo số kết quả |
| Client | `GET /products/{id}` | `product/detail` | Public | SP + giá/tình trạng tại chi nhánh + các chi nhánh khác + đánh giá | Gallery, khối chi nhánh, thêm giỏ, mô tả, đánh giá | – | – | Đánh giá: 20 mới nhất, không phân trang | Có (không bán ở chi nhánh, hết hàng) | 404 nếu SP ẩn |
| Client | `GET /stores` | `store/list` | Public | Danh sách chi nhánh | 2 select, thẻ chi nhánh | Có (`provinceCity`, `area`) | Không (cố định tỉnh–khu vực–tên) | **Không** (trả cả `List`) | Có | Hiện cả chi nhánh INACTIVE (nhãn "Tạm ngừng") |
| Client | `POST /stores/select`, `/stores/clear` | – | Public | Cookie chọn chi nhánh | Nút trong header/thẻ | – | – | – | `?error=unavailable` | |
| Client | `GET/POST /login`, `/register` | `auth/login`, `auth/register` | Public | Form | Form có icon, lỗi theo field | – | – | – | Có | Login đã gộp "Email hoặc tên đăng nhập" |
| Client | `GET /cart` | `cart/index` | CUSTOMER | Giỏ nhóm theo Store | Nhóm Store, checkbox, stepper, tóm tắt | – | – | – | Có (giỏ trống, dòng UNAVAILABLE) | Tạm tính không theo chọn (UX-006) |
| Client | `POST /cart/items`, `/{id}`, `/{id}/delete` | – | CUSTOMER | – | – | – | – | – | Lỗi qua `cartError` | |
| Client | `GET /checkout` | `checkout/index` | CUSTOMER | Preview theo Store group | Khối mỗi Store: nhận hàng, thanh toán, người nhận | – | – | – | Lỗi `checkoutError` ở đầu trang | Không có lỗi theo field |
| Client | `POST /checkout`, `GET /checkout/{id}` | `checkout/result` | CUSTOMER | Kết quả nhiều Order | Thẻ từng đơn, nút thanh toán | – | – | – | – | |
| Client | `GET /orders` | `orders/index` | CUSTOMER | Đơn của khách | Thẻ đơn | **Không** | Cố định (mới nhất) | **Không** | Text "Bạn chưa có đơn hàng." | |
| Client | `GET /orders/{id}`, `POST /orders/{id}/cancel`, `POST /reviews` | `orders/detail` | CUSTOMER | Chi tiết, thanh toán, timeline, review | `dl`, bảng SP, list group timeline | – | – | – | `?cancelled` | Hiện `pickup_code` khi READY/COMPLETED |
| Client | `GET /orders/{id}/payments` + 3 POST | `payment/index` | CUSTOMER | Lần thanh toán mô phỏng | Card, nút mô phỏng | – | – | – | – | Nhãn "VNPAY / Thẻ" ở checkout nhưng là thanh toán mô phỏng |
| Client | `/error`, lỗi controller | `error`, `error-message` | Public | Mã lỗi | Số lớn + nút Về trang chủ | – | – | – | – | Dùng chung cho 403/404/500 mọi khu vực |

### 2.2 Staff (layout `staff`, `ROLE_STAFF`, chỉ Store được phân công)

| Khu vực | Route | Template | Dữ liệu chính | Thành phần chính | L | S | P (cỡ) | Rỗng/lỗi | Ghi chú |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Staff | `GET /staff`, `/staff/dashboard` | `staff/dashboard` | 4 chỉ số/Store + 5 đơn gần nhất | `statCard`, bảng đơn | – | – | – | Có (chưa phân công) | Ngưỡng sắp hết = 5 (hằng số) |
| Staff | `GET /staff/orders` | `staff/orders/index` | Mọi đơn của Store được phân công | Bảng 8 cột | **Không** | Cố định | Có (20) | Có | |
| Staff | `GET /staff/orders/{id}` + 7 POST | `staff/orders/detail` | Chi tiết + hành động | `orders/fragments :: detail`, form hành động | – | – | – | Có | |
| Staff | `GET /staff/orders/delivery` (+`/{id}`, 4 POST) | `staff/delivery/index`, `detail` | Đơn DELIVERY | Bảng + nút hành động | **Không** | Cố định | **Không** (trả `List`) | Có | |
| Staff | `GET /staff/orders/pickup` (+`/{id}`, 3 POST) | `staff/pickup/index`, `detail` | Đơn STORE_PICKUP | Bảng + link hành động | **Không** | Cố định | **Không** (`List`) | Có | |
| Staff | `GET /staff/pickup` | `staff/pickup/queue` | Hàng chờ nhận | Bảng dùng lại `staff/orders :: table` | **Không** | Cố định | Có (20) | Có | Trùng lặp chức năng với `/staff/orders/pickup` |
| Staff | `GET /staff/stock` | `staff/stock/index` | StoreProduct + tồn thực | Bảng 6 cột | **Không** | Cố định | Có (20) | Có | |
| Staff | `GET/POST /staff/stock/{id}/adjust` | `staff/stock/adjust` | Điều chỉnh tồn | Form | – | – | – | Lỗi theo field | |
| Staff | `GET /staff/inventory-history` | `staff/stock/index` (`historySelection`) | Chọn SP | Cùng bảng | **Không** | Cố định | Có (20) | Có | |
| Staff | `GET /staff/inventory-history/{id}` | `staff/stock/history` | Biến động | Bảng 7 cột | **Không** | Mới nhất | Có (20) | Có | `movement.type` là enum thô |
| Staff | `GET /staff/reviews`, `POST /{id}/reply` | `staff/reviews/index` | Đánh giá của Store | Thẻ + form trả lời | **Không** | Mới nhất | Có (20) | Có | Chỉ ACTIVE |

### 2.3 Admin (layout `admin`, `ROLE_ADMIN`)

| Khu vực | Route | Template | Dữ liệu chính | L | S | P (cỡ) | Rỗng/lỗi | Ghi chú |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Admin | `GET /admin` | `admin/dashboard` | Thống kê theo Store | Có (`storeId`) | – | – | Có | Mô tả giải thích bằng tên enum thô |
| Admin | `/admin/stores` (+`/new`, `/{id}/edit`, 2 POST) | `stores`, `store-form` | Chi nhánh | **Không** | Cố định | **Không** | Có | |
| Admin | `/admin/categories` (+edit, 2 POST) | `categories` | Danh mục | **Không** | Theo tên | **Không** | – | Form và danh sách chung một trang |
| Admin | `/admin/brands` (+edit, logo, 4 POST) | `brands` | Thương hiệu | **Không** | Theo tên | **Không** | – | |
| Admin | `/admin/products` (+`/new`, edit, ảnh) | `products`, `product-form` | SKU | Có (`q`) | Theo tên | Có (20) | Có | Không lọc danh mục/thương hiệu/trạng thái |
| Admin | `/admin/store-products` (+`/new`, edit) | `store-products`, `store-product-form` | SKU theo Store | Có (`storeId`, `productId`) | Theo tên Store, tên SP | Có (20) | Có | `productId` là select **toàn bộ** SP |
| Admin | `/admin/inventory` | `store-products` (`inventoryView`) | Tồn toàn chuỗi | Như trên | Như trên | Có (20) | Có | Cùng template, thêm `<h1>` thứ hai |
| Admin | `/admin/staff-assignments` (+POST) | `staff-assignments` | Phân công | Có (`storeId`) | Theo `id` | Có (20) | – | Form tạo và danh sách chung trang |
| Admin | `/admin/orders`, `/{id}` | `orders`, `order-detail` | Đơn toàn chuỗi | Có (`storeId`) | Mới nhất | Có (20) | Có | |
| Admin | `/admin/reviews` (+status, reply) | `reviews` | Đánh giá | **Không** | Mới nhất | Có (20) | – | Phân trang gọi với `storeId=null` (thừa tham số) |
| Admin | `/admin/users` (+`/new`, edit) | `users`, `user-form` | Người dùng | Có (`role`) | Theo `id` | Có (20) | – | Không tìm theo tên/email |

---

## 3. Bộ lọc, tìm kiếm, sắp xếp

### 3.1 Tìm kiếm văn bản – cơ chế chung

- `ProductSearchCriteria.likePattern()` (`dto/request/ProductSearchCriteria.java`) chuyển keyword về chữ thường (`Locale.ROOT`), escape `\ % _ [`, bọc `%…%`.
- Truy vấn dùng `lower(p.name) like :keyword escape '\\' or lower(p.sku) like :keyword escape '\\'` (`ProductRepository.searchVisible`, `searchAll`, `StoreProductRepository.searchSellableInStore`). **Chỉ tìm theo `name` và `sku`**; không tìm theo `description`, tên thương hiệu, tên danh mục.
- **Phân biệt dấu:** `01_create_database.sql:12` tạo DB với collation `Vietnamese_100_CI_AI` (không phân biệt hoa/thường, không phân biệt dấu). Cột `NVARCHAR` mặc định kế thừa collation DB nên `LIKE` sẽ tìm được "serum bi dao" khớp "Serum Bí Đao". **Chưa xác minh trên DB đang chạy**: nếu DB được tạo bằng cách khác (không qua script) thì collation có thể khác. Cũng chưa có test nào (đã đọc) kiểm tra tìm không dấu.
- Ký tự `[` được escape trong Java nhưng `escape '\\'` chỉ được khai báo trong HQL; cần kiểm tra `[` trong T-SQL thực tế – **chưa xác minh**.
- Tham số rỗng: keyword rỗng → `null` → `"%"` (khớp tất cả). `categoryId`/`brandId` không tồn tại → danh sách rỗng, không báo "bộ lọc không hợp lệ". `page` âm → `Math.max(page, 0)`; `page` quá lớn → trang rỗng (hiển thị empty state, không dẫn về trang cuối).

### 3.2 Bảng chi tiết theo màn hình

**3.2.1 Catalog `/products`** (toàn chuỗi và theo Store)

| Hạng mục | Hiện có | Thiếu / ghi chú |
| --- | --- | --- |
| Tham số URL | `q`, `categoryId`, `brandId`, `page` | – |
| Control | `input[type=search]`, 2 `select`, nút submit "Lọc kết quả" | Không có `range`, `checkbox` |
| Giữ trạng thái khi phân trang | Có (`@{/products(q=…, categoryId=…, brandId=…, page=…)}`) | – |
| Nút xóa lọc | **Không** | |
| Dữ liệu có sẵn chưa có control | `store_products.price` (khoảng giá); `store_products.quantity > 0` (còn hàng – Client chỉ được biết boolean); `products.status`; `categories`/`brands` (chỉ chọn 1 giá trị); lọc theo `stores` trong chế độ toàn chuỗi (SP có ở chi nhánh X) | Khi đã chọn Store, `StoreProductRepository.searchSellableInStore` có đủ `sp.price`, `sp.quantity` để lọc nhưng không có tham số |
| Backend | `ProductRepository.searchVisible` (toàn chuỗi), `StoreProductRepository.searchSellableInStore` (theo Store) – JPQL với `(:categoryId is null or c.id = :categoryId)`. Các điều kiện kết hợp bằng AND | Không dùng Specification; thêm điều kiện = thêm tham số cho JPQL + `countQuery` |
| Sắp xếp | **Không có control.** Cố định `order by p.name, p.id` | Có tiêu chí phụ `p.id` nên ổn định |
| Index phục vụ | `IX_store_products_store_status_qty (store_id, status, quantity)`, `IX_store_products_product_status (product_id, status)`; không có index trên `products.name`/`sku` (có `UQ_products_sku`) → `lower(...) like '%…%'` không dùng được index | Với dữ liệu nhỏ không gây hại; nêu để biết |
| Trạng thái rỗng | `emptyState` – "Không tìm thấy sản phẩm phù hợp." | Không gợi ý "xóa lọc" |

**3.2.2 Store Finder `/stores`**

| Hạng mục | Hiện có | Thiếu |
| --- | --- | --- |
| Tham số | `provinceCity`, `area` | – |
| Control | 2 `select` + nút "Tìm cửa hàng" | Select "khu vực" chỉ nạp được các khu vực của tỉnh **sau khi gửi form** (không phụ thuộc phía client) |
| Giữ trạng thái | Có (form GET) | Nút xóa lọc: **không** |
| Dữ liệu có sẵn chưa có control | `stores.delivery_enabled`, `stores.pickup_enabled`, `stores.status`, `name`/`address` (tìm văn bản) | |
| Backend | `StoreServiceImpl.findStores`: `findAllByOrderBy…` rồi **lọc trong bộ nhớ** bằng `equalsIgnoreCase` (comment trong mã giải thích "chuỗi chỉ có vài Store") | Không dùng `Pageable`; so sánh `equalsIgnoreCase` **có phân biệt dấu** (khác với tìm sản phẩm) |
| Sắp xếp | Cố định `province_city, area, name` | Không có "gần tôi" (đúng ràng buộc, không geolocation) |
| Phân trang | Không (trả `List`) | Tăng số Store sẽ thành trang rất dài |

**3.2.3 Lịch sử đơn của khách `/orders`**

| Hạng mục | Hiện có | Thiếu |
| --- | --- | --- |
| Tham số | Không có (kể cả `page`) | – |
| Dữ liệu có sẵn | `orders.order_status`, `payment_status`, `payment_method`, `fulfillment_type`, `store_id`, `created_at`, `total_amount` | Tất cả đều có thể lọc |
| Backend | `OrderRepository.findByUserEmailOrderByCreatedAtDescIdDesc(email)` + `@EntityGraph("store")` – trả `List` toàn bộ | Không `Pageable` |
| Sắp xếp | Mới nhất trước, `id desc` phụ | |
| Index | `IX_orders_user_created (user_id, created_at)` phục vụ đúng truy vấn này (qua `user.email` → join) | |

**3.2.4 Staff – đơn `/staff/orders`, hàng chờ `/staff/pickup`, giao hàng, nhận tại cửa hàng**

| Hạng mục | Hiện có | Thiếu |
| --- | --- | --- |
| Tham số | `page` duy nhất (delivery/pickup list: không có) | `storeId` (khi Staff có nhiều Store), `status`, `fulfillmentType`, `paymentMethod`, khoảng ngày, mã đơn, tên/SĐT người nhận |
| Backend | `findByStoreIdInOrderByCreatedAtDescIdDesc` (page); `…AndFulfillmentTypeAndOrderStatusIn…` (queue); `…AndFulfillmentType…` (list, không page) – derived query với `storeIds` từ phân công | Mỗi lọc mới cần thêm repository method; `Store id` luôn từ phân công, không từ request (đúng ràng buộc) |
| Sắp xếp | `created_at desc, id desc` | Có tiêu chí phụ |
| Index | `IX_orders_store_status_created (store_id, order_status, created_at)` – phù hợp lọc trạng thái | |

**3.2.5 Staff – tồn kho `/staff/stock`, `/staff/inventory-history`**

| Hạng mục | Hiện có | Thiếu |
| --- | --- | --- |
| Tham số | `page` | Tìm theo SKU/tên, lọc `status`, lọc "hết hàng / sắp hết" (dashboard đã tính 2 con số này nhưng không dẫn tới danh sách đã lọc), lọc theo Store |
| Backend | `findByStoreIdInOrderByStoreNameAscProductNameAscIdAsc` (derived) | Không có truy vấn theo `quantity` |
| Sắp xếp | `store.name, product.name, id` | |
| Index | `IX_store_products_store_status_qty (store_id, status, quantity)` sẵn sàng cho lọc `quantity` | Đang chưa được dùng cho lọc |
| Lịch sử biến động | `findByStoreProductId…OrderByCreatedAtDescIdDesc` (page 20) | Không lọc theo `type` (`SALE`, `STOCK_ADJUST`, …), khoảng ngày; index `IX_inventory_movements_sp_created` phù hợp |

**3.2.6 Admin**

| Màn hình | Tham số hiện có | Dữ liệu có sẵn nhưng chưa có control | Backend | Sắp xếp / index |
| --- | --- | --- | --- | --- |
| `/admin/products` | `q`, `page` | `category_id`, `brand_id`, `status` | `ProductRepository.searchAll` – `lower(name)/lower(sku) like` | `name, id`; không index trên name |
| `/admin/store-products` | `storeId`, `productId`, `page` | `status`, `quantity` (hết hàng/sắp hết), khoảng giá | `searchForAdmin` – `(:storeId is null or …)` | `s.name, p.name, sp.id`; `IX_store_products_*` |
| `/admin/inventory` | như trên | như trên | cùng truy vấn | cùng |
| `/admin/orders` | `storeId`, `page` | `order_status`, `payment_status`, `payment_method`, `fulfillment_type`, khoảng `created_at`, khách hàng | `OrderRepository.searchAdmin` + `@EntityGraph(store,user)` | `created_at desc, id desc`; `IX_orders_store_status_created` |
| `/admin/users` | `role`, `page` | `status`, tìm theo `full_name`/`email`/`user_name`/`phone` | `UserRepository.searchAdmin` `(:role is null or …) order by u.id` | Theo `id`; `UQ` trên email, `UX_users_user_name` |
| `/admin/reviews` | `page` | `status` (ACTIVE/HIDDEN), `rating`, `product_id`, "chưa phản hồi" (`reply is null`), Store | `findAllByOrderByCreatedAtDescIdDesc` + `@EntityGraph` | `created_at desc, id desc`; chỉ có `IX_reviews_order (order_id)` |
| `/admin/staff-assignments` | `storeId`, `page` | `status`, theo Staff | `StaffStoreAssignmentRepository.searchAdmin` `order by a.id` | Theo `id` |
| `/admin/stores` | – | `status`, `province_city`, `area` | `getAllStores()` (List) | – |
| `/admin/categories`, `/admin/brands` | – | `status`, tìm tên | `findAllByOrderByName` (List) | – |

### 3.3 Kích thước dữ liệu seed (đếm từ `database/04_seed_data.sql`)

Số dòng được đếm theo số bộ `VALUES` trong script seed. **Chưa đối chiếu với DB đang chạy.**

| Bảng | Dòng | Bảng | Dòng |
| --- | --- | --- | --- |
| `roles` | 3 | `orders` | 5 |
| `users` | 7 | `order_items` | 7 |
| `stores` | 5 (4 ACTIVE + 1 INACTIVE) | `payments` | 5 |
| `staff_store_assignments` | 4 | `checkout_sessions` | 3 |
| `categories` | 3 | `inventory_movements` | 9 |
| `brands` | 3 | `order_status_history` | 16 |
| `products` | 7 (6 ACTIVE + 1 INACTIVE) | `reviews` | 1 |
| `store_products` | 21 | `cart_items` / `carts` | 3 / 1 |

**Kết luận:** với 6 sản phẩm đang bán và cỡ trang catalog 12, 5 đơn và cỡ trang 20, 21 StoreProduct và cỡ trang 20 thì **phân trang chưa bao giờ được kích hoạt** bằng dữ liệu seed (các test web dùng dữ liệu mock). Các liên kết `Trang trước/Trang sau`, giữ tham số khi lật trang, hành vi khi vượt trang, truy vấn lọc trên dữ liệu lớn đều **chưa được kiểm chứng với dữ liệu đủ lớn**.

### 3.4 Sắp xếp

Không có control sắp xếp ở **bất kỳ** màn hình nào. Thứ tự cố định:

| Màn hình | Thứ tự | Tiêu chí phụ ổn định? |
| --- | --- | --- |
| Catalog | `p.name, p.id` | Có |
| Đơn (khách/Staff/Admin) | `created_at desc, id desc` | Có |
| `/staff/orders/delivery`, `/pickup` (list) | `created_at desc, id desc` | Có |
| Tồn kho Staff / Admin | `store.name, product.name, id` | Có |
| Admin users / assignments | `u.id` / `a.id` | Có (chỉ id) |
| Admin reviews | `created_at desc, id desc` | Có |
| Store Finder | `province_city, area, name` | **Không có `id` phụ** (trùng tên → thứ tự không xác định) |
| Danh mục / thương hiệu | `name` | **Không có `id` phụ** |
| Chi nhánh bán SP (chi tiết SP) | `s.name, s.id` | Có |

---

## 4. Bố cục và luồng ở các màn hình trọng yếu

Mỗi mục nêu: bố cục, số click, câu hỏi bắt buộc, vấn đề. "Chi nhánh đang chọn?" tức người dùng có biết mình đang ở chi nhánh nào.

### 4.1 Trang chủ `/`

- **Bố cục:** header chi nhánh → navbar → hero (eyebrow, H1, đoạn dẫn, 2 nút) → 3 thẻ hướng dẫn "Mua sắm theo chi nhánh" → footer.
- **Click:** xem SP = 1 click; chọn chi nhánh = 1 click (`/stores`) + 1 click chọn.
- **Chi nhánh đang chọn?** Có (header `#store-context`, `data-store-scope`). Đạt ràng buộc.
- **Hành động chính:** 2 nút nổi bật, rõ.
- **Vấn đề:** trang không có dữ liệu thật (sản phẩm nổi bật, khuyến mãi); 3 thẻ hướng dẫn giải thích cơ chế hệ thống thay vì hỗ trợ mua; không có ô tìm kiếm ở trang chủ hay navbar; header hiển thị "Miễn phí vận chuyển cho đơn từ 500.000 ₫" và "Hotline 1900 0000" (hard-code ở `fragments/header.html:137`) trong khi không có logic phí vận chuyển nào trong mã (grep `shipping fee` không thấy).

### 4.2 Danh sách sản phẩm `/products`

- **Bố cục:** hero (tiêu đề + pill ngữ cảnh) → panel lọc (4 cột: tìm / danh mục / thương hiệu / nút) → lưới 1/2/3/4 cột → phân trang giữa.
- **Click:** từ danh sách đến chi tiết 1 click; thêm giỏ **không thể** từ danh sách (phải vào chi tiết): tổng ≥ 3 click.
- **Chi nhánh đang chọn?** Có (pill `#catalog-context` + header).
- **Chế độ toàn chuỗi:** mỗi thẻ hiện danh sách tất cả chi nhánh + Còn hàng/Hết hàng. Với 5 chi nhánh thẻ đã cao; số chi nhánh tăng thì thẻ dài vô hạn.
- **Vấn đề:** không sắp xếp, không lọc giá/tình trạng, không xóa lọc, không hiện tổng kết quả; thẻ SP **không có nút thêm giỏ**; `product/list.html` là trang Client đầy đủ có `<html><head>` riêng (CDN Bootstrap, CSS lặp); khối "Hero" chiếm chiều cao đầu trang trước khi tới bộ lọc (ở 375px người dùng phải cuộn mới thấy kết quả đầu tiên – **chưa xác minh bằng trình duyệt**).
- **Bảo vệ BR-15:** chỉ dùng `inStock` (boolean). Không vi phạm.

### 4.3 Chi tiết sản phẩm `/products/{id}`

- **Bố cục (cột phải):** eyebrow thương hiệu · danh mục → H1 → SKU → khối chi nhánh đã chọn (giá, Còn/Hết hàng, số lượng + Thêm giỏ) → "Chi nhánh khác" (giá + trạng thái + nút đổi) → mô tả → đánh giá (cuối cột).
- **Click:** thêm vào giỏ khi đã chọn Store: 2 (chọn số lượng + thêm). Khi chưa chọn: 3+ (chọn Store tại chi tiết → trang reload → thêm).
- **Chi nhánh đang chọn?** Có (`Chi nhánh đang xem: …` + header).
- **Vấn đề:**
  - Ảnh chính + thumbnail lấy `imageUrl` gốc từ Cloudinary, không dùng biến đổi kích thước; thumbnail dùng `onclick` nội tuyến, không có điều khiển bàn phím (`img` không focus được).
  - Khi chưa chọn chi nhánh, mỗi dòng "chi nhánh đang bán" có nút "Chọn chi nhánh này" = **đổi Store toàn trang** thay vì "thêm vào giỏ ở chi nhánh này" → người dùng phải thêm một bước.
  - "Hết hàng" cho chi nhánh đã chọn: nút "Tạm hết hàng" bị `disabled`; chi nhánh khác còn hàng nằm bên dưới chứ không được đề xuất trực tiếp tại chỗ.
  - Đánh giá nằm sau mô tả, tải 20 bản ghi không phân trang; điểm trung bình hiển thị "4.5 / 5 sao" không có sao đồ họa; markup đánh giá là `list-group` Bootstrap mặc định, **không bám phong cách** với khu vực phía trên (xem 7).
  - Các thông báo `reviewed`/`reviewError` nằm cuối trang (sau mô tả) → sau khi gửi đánh giá, trang reload về đầu và người dùng không thấy thông báo mà không cuộn (không có `#reviews` trong URL redirect – **chưa xác minh trong `ReviewController`**).

### 4.4 Store Finder `/stores`

- **Bố cục:** hero → bộ lọc (2 select + nút) → lưới thẻ (1/2/3 cột).
- **Click:** chọn chi nhánh = 1 click từ thẻ.
- **Chi nhánh đang chọn?** Có (thẻ đánh dấu "Đang mua sắm tại đây").
- **Vấn đề:** eyebrow "HỆ THỐNG PHÂN PHỐI · TOÀN QUỐC" trong khi seed chỉ có Hồ Chí Minh + Đà Nẵng (Hải Châu); không có tìm theo tên/địa chỉ; không có bản đồ (**đúng ràng buộc**); chi nhánh INACTIVE vẫn hiển thị (có chú thích, nút bị ẩn); không phân trang; `areas` select chỉ cập nhật sau submit.

### 4.5 Giỏ hàng nhiều Store `/cart`

- **Bố cục:** cột trái (nhóm theo Store: header checkbox "Chọn chi nhánh" + tên + địa chỉ, dòng SP gồm checkbox, ảnh, tên, SKU, đơn giá, stepper, thành tiền, nút xóa; chân nhóm "Tạm tính tại chi nhánh") + cột phải (tóm tắt, nút "Đặt hàng sản phẩm đã chọn").
- **Click:** từ giỏ đến checkout: chọn (1+) + Đặt hàng (1).
- **Chi nhánh đang chọn?** Giỏ nhóm theo Store (đúng ràng buộc); ở header vẫn hiện Store đang duyệt, không liên quan tới các nhóm giỏ – dễ nhầm giữa "Store đang xem" và "Store của dòng giỏ" (**cần người dùng thật xác nhận**).
- **Vấn đề:**
  1. `cart.total` / `cart.itemCount` ở panel tóm tắt là tổng **cả giỏ**, trong khi nút bên dưới chỉ đặt các dòng đã chọn; không có JS cập nhật tổng theo lựa chọn (grep `cart-total` chỉ xuất hiện 1 lần, không có logic cập nhật).
  2. Đổi số lượng tự gửi form sau 350–650ms → reload cả trang, mất vị trí cuộn và trạng thái checkbox (**chưa xác minh** việc trình duyệt khôi phục).
  3. Hộp xác nhận xóa hiện **hai lần**: `main.js` gắn listener `form[data-confirm]` và `cart/index.html` (dòng ~885) gắn thêm listener `[data-confirm]` → hai `confirm()` nối tiếp.
  4. Cùng logic chọn nhóm/dòng được viết hai lần (`main.js: initCartSelection` và script nội tuyến trong `cart/index.html`).
  5. Thông báo thành công dùng `param.success` (query string) → còn lại trên URL, làm mới trang vẫn hiện.
- **Mật độ:** dòng SP dày (ảnh + 5 dòng thông tin + stepper + tổng + xóa) – ở 375px chưa xác minh.

### 4.6 Checkout theo Store group `/checkout`

- **Bố cục:** breadcrumb → tiêu đề + tóm tắt "Bạn đang đặt N sản phẩm từ M chi nhánh" → mỗi Store một thẻ (tên, SP, "Cách nhận hàng" + "Thanh toán" dạng tile, người nhận, SĐT, địa chỉ khi DELIVERY, tạm tính) → cột phải tóm tắt.
- **Click:** 1 (Xác nhận đặt hàng) nếu mặc định đúng; mỗi Store là 1 form con.
- **Chi nhánh đang chọn?** Tên + địa chỉ Store trên mỗi khối; đạt.
- **Vấn đề:**
  - Mỗi Store nhập lại **tên người nhận + SĐT + địa chỉ** (không có "dùng chung cho mọi chi nhánh"); với 3 chi nhánh = 9 trường.
  - Không có lỗi theo từng field: toàn bộ lỗi là một `alert` ở đầu (`#checkout-error`); form mất trạng thái chọn nếu server từ chối? Có `choices[group.storeId]` để điền lại (đạt).
  - Nhãn "Thanh toán trực tuyến (VNPAY / Thẻ)" nhưng luồng thực tế là thanh toán mô phỏng (`payment/index`) – gây hiểu lầm.
  - `th:name="${prefix} + '.receiverName'"` dùng chỉ số → thay đổi thứ tự có thể lệch (đã kiểm bằng test).
  - Tile thanh toán ẩn/hiện bằng JS; nếu JS lỗi, cả 3 phương thức hiển thị (server vẫn kiểm).

### 4.7 Kết quả đặt hàng `/checkout/{id}` và chi tiết đơn `/orders/{id}`

- **Chi tiết đơn:** tiêu đề → tên Store/địa chỉ → `dl` (ngày, hình thức, người nhận, SĐT, địa chỉ, thanh toán, trạng thái, mã nhận hàng) → bảng SP → thanh toán → **Lịch sử trạng thái** (`list-group-numbered`, nằm cuối trang) → hủy đơn → đánh giá → nút "Xem thanh toán trực tuyến".
- **Vấn đề:**
  - Trạng thái hiện tại là badge xám (`text-bg-secondary`) giống mọi trạng thái; timeline là danh sách số thứ tự, không phải timeline có tiến trình.
  - `pickup_code` hiển thị trong `alert-info` ngay giữa trang (đạt về nội dung: chỉ khi READY/COMPLETED).
  - Hàng "Thanh toán" liệt kê `payment.method + ' · ' + payment.status` là enum thô (`ONLINE · SUCCESS`).
  - Nút **Hủy đơn** không có xác nhận.
  - Thứ tự: hành động chính (Hủy/Thanh toán) nằm dưới timeline; với đơn `PENDING_PAYMENT` người dùng phải cuộn tới cuối mới thấy nút thanh toán.

### 4.8 Danh sách đơn của khách `/orders`

- **Bố cục:** H1 → loạt `card` (tên đơn + Store, ngày, hình thức nhận, badge trạng thái · text thanh toán, tổng).
- **Vấn đề:** không phân trang/lọc/sắp xếp (UX-004); không có hành động nhanh (thanh toán / xem mã nhận hàng) trên thẻ; mã nhận hàng chỉ thấy sau khi vào chi tiết; không hiển thị số SP hay ảnh.

### 4.9 Dashboard Staff `/staff`

- **Bố cục:** tiêu đề → với mỗi Store được phân công: tên + địa chỉ + 4 `statCard` (đơn cần xử lý, chờ nhận, sắp hết, hết hàng) → bảng 5 đơn gần nhất (chung cho cả các Store).
- **Chi nhánh đang chọn?** Staff có nhiều Store: mỗi Store một khối; header có badge phạm vi và sidebar có `#staff-store-scope`. Đạt.
- **Vấn đề:** 4 chỉ số **không click được** để đi tới danh sách tương ứng (không có `href`); "5 đơn gần nhất" gộp mọi Store vào một bảng, cột "Chi nhánh" có nhưng không lọc được; ngưỡng "sắp hết" (5) là hằng số trong mã, không hiển thị trong bảng tồn kho để Staff đối chiếu (hint trong thẻ có số).

### 4.10 Hàng đợi đơn / nhận tại cửa hàng Staff

- **Bố cục:** `/staff/orders`: bảng 8 cột chỉ xem; `/staff/orders/delivery`: bảng 6 cột có nút hành động ngay trong dòng; `/staff/orders/pickup`: bảng 8 cột có link hành động; `/staff/pickup`: lại bảng 8 cột (như `/staff/orders`).
- **Vấn đề:**
  - Hai nơi cho cùng nghiệp vụ nhận tại cửa hàng (`/staff/orders/pickup` và `/staff/pickup`) với cột/hành động khác nhau.
  - Nhập mã nhận hàng chỉ có ở trang chi tiết (đúng nghiệp vụ nhưng mất 2 click+nhập); không có ô "tra cứu nhanh theo mã nhận hàng".
  - `/staff/orders` không có nút hành động trong dòng, trong khi `/staff/orders/delivery` có – hành vi bảng không đồng nhất.
  - Bảng 8 cột ở 768px dựa vào `.table-responsive` (cuộn ngang) – **chưa xác minh bằng trình duyệt**.

### 4.11 Tồn kho Staff

- **Bố cục:** tiêu đề → "Số SKU tại chi nhánh: N" → bảng 6 cột (Chi nhánh, SKU/Sản phẩm, Giá, Trạng thái, Tồn thực tế, Thao tác) → phân trang.
- **Vấn đề:** không lọc/tìm; số lượng tồn là chữ đậm không có chỉ báo màu (sắp hết/hết); thao tác "Điều chỉnh" và "Lịch sử" là hai nút outline cùng cấp; `/staff/inventory-history` dùng chính template này chỉ khác ẩn nút điều chỉnh (người dùng thấy cùng bảng ở hai menu); biến động tồn hiển thị `type` enum thô.

### 4.12 Trang quản trị Admin chính

- **Dashboard:** một `card` bộ lọc + `card` cho từng chi nhánh (4 `statCard` + `dl` + 4 nút). Đoạn mô tả liệt kê tên enum thô (`CONFIRMED, PREPARING…`). Bộ lọc chi nhánh được đặt trong `card` riêng gây thừa khoảng trắng.
- **Danh sách:** hầu hết dùng `table table-hover` Bootstrap mặc định; phần lớn là markup một dòng dày đặc (`orders.html`, `reviews.html`, `users.html` viết trên một dòng dài).
- **Admin reviews:** mỗi dòng chứa cả form phản hồi `textarea` trong ô bảng → hàng rất cao khi có nhiều đánh giá; không lọc đánh giá chưa phản hồi/ẩn.
- **Form tạo/sửa:** `store-form`, `product-form`, `store-product-form` có lỗi theo field; `user-form` có `th:errors` = 0 (xem 5.1).
- **Nhất quán:** hai pattern tiêu đề (`pageHeader` fragment vs `<h1 class="h3">` trực tiếp ở `inventoryView`) → trang `/admin/inventory` có hai thẻ `<h1>` cạnh nhau.

---

## 5. Chất lượng tương tác và trạng thái

### 5.1 Form

| Form | Lỗi cạnh trường | Giữ dữ liệu khi lỗi | Nhãn gắn input | Kiểu input | Chống bấm lặp |
| --- | --- | --- | --- | --- | --- |
| Đăng nhập | Có (`th:errors`, `is-invalid`) + alert chung `loginError` | Có (`th:field`) | Có (`for`) | `type=text` (identifier) | **Không** |
| Đăng ký | Có (5 trường) | Có | Có | `email`, `tel` | Không |
| Checkout | **Không**, chỉ `#checkout-error` đầu trang | Có (`choices[...]`) | Có (`th:for`) | `tel`; nhưng `type=text` cho tên | Không |
| Stock adjust (Staff) | Có (`th:errors` text nhỏ, không `is-invalid`) | Có | Có | `number` | Không |
| Admin product/store/store-product/brand/category | Có (`is-invalid` + `th:errors`) | Có | Có | `number`, `tel` | Không |
| Admin user-form | `th:errors` = 0 → lỗi chỉ qua `FormErrors.reject` + alert tổng (**chưa xác minh** rằng template hiển thị `#fields.hasGlobalErrors`) | Có | Có | – | Không |
| Admin staff-assignments | Alert tổng (`#fields.allErrors()`) | Có | Có | `select` | Không |
| Review (chi tiết SP / chi tiết đơn) | Chỉ alert theo `param.reviewError` | **Không** – form được tạo lại sau redirect, mất nội dung nhận xét đã gõ (nhận xét 1000 ký tự) | Có | `select`, `textarea` | Không |
| Staff reply / Admin reply | Alert theo `param.replyError` | Không | Có | `textarea` | Không |

- Không có `disabled`/`aria-busy` khi submit ở bất kỳ form nào (`main.js` không có xử lý submit).
- `type="date"` và `type="datetime-local"` không xuất hiện trong template nào (chưa có chỗ nhập ngày).

### 5.2 Thông báo thành công/lỗi (flash)

- Toàn bộ thành công qua `redirect:…?success=…` (21 điểm trong controller; 0 dùng `RedirectAttributes`). Thông báo còn trên URL; làm mới/ chia sẻ URL giữ thông báo.
- Vị trí: đầu nội dung trang (Staff/Admin/giỏ); riêng đánh giá nằm cuối trang chi tiết SP.
- Kiểu: `alert-success`, `alert-danger`, `alert-warning`, `alert-info`; `.cart-alert` ở giỏ. `data-auto-dismiss` có trong `main.js` nhưng **không template nào dùng thuộc tính này** (grep trống).
- Lỗi nghiệp vụ ở checkout/giỏ: `cartError`/`checkoutError` (model); lỗi ở Admin: alert tổng.

### 5.3 Trạng thái rỗng, lỗi và hết hàng

| Trường hợp | Hiện trạng |
| --- | --- |
| Không có kết quả catalog | `emptyState`; không gợi ý xóa lọc |
| Chi nhánh chưa có SP | "Chi nhánh này chưa có sản phẩm phù hợp." |
| Giỏ trống | Thẻ riêng + nút "Tiếp tục mua sắm" (tốt) |
| Chưa có đơn | Chỉ một dòng chữ |
| Staff chưa được phân công | Alert cảnh báo ở dashboard; các trang khác hiển thị bảng rỗng |
| Hết hàng | Badge xám "Hết hàng"; nút "Tạm hết hàng" disabled |
| Store INACTIVE | Thẻ Store có nhãn "Tạm ngừng"; trong giỏ có badge "Chi nhánh tạm ngừng" |
| 403/404/500 | `error.html` (Client chrome, kể cả cho `/staff` và `/admin`), mã lỗi cỡ `display-3` + nút "Về trang chủ"; **không có nút quay lại trang trước**; không phân biệt khu vực |
| Lỗi 404 do controller (SP/đơn) | `error-message.html` bọc trong layout theo khu vực (tốt) |

### 5.4 Hành động nguy hiểm

| Hành động | Xác nhận? |
| --- | --- |
| Xóa dòng giỏ | Có (nhưng hiện 2 lần – UX-006) |
| Hủy đơn của khách | **Không** (nút `btn-outline-danger`) |
| "Mô phỏng thất bại và hủy đơn" | **Không** |
| Staff: chuyển trạng thái (chuẩn bị/đóng gói/giao/hoàn tất) | **Không** (hoàn tất COD có dòng nhắc, không chặn) |
| Staff: điều chỉnh tồn | **Không** (có ghi chú giải thích) |
| Admin: ẩn đánh giá, bỏ phân công, đổi trạng thái user/store/SP | **Không** |
| Admin: xóa ảnh SP / xóa logo thương hiệu | **Không** |

> `fragments/components :: modal` tồn tại nhưng **không được dùng**; `data-confirm` chỉ có ở một form.

### 5.5 Badge/màu trạng thái giữa 3 khu vực

| Đối tượng | Client | Staff | Admin |
| --- | --- | --- | --- |
| Trạng thái đơn | `state` (xám); `checkout/result`: `.status-badge-pending/confirmed/cancelled/default` (3 màu); `payment/index`: chữ thuần | `state` (xám) | `state` (xám) |
| Trạng thái thanh toán | Text thuần / `text-success`/`text-danger` ở `checkout/result` | Text thuần (`payment`) | `o.paymentMethod + ' · ' + o.paymentStatus` – enum thô |
| Hình thức nhận | Chữ tiếng Việt | Chữ tiếng Việt | **Enum thô** (`DELIVERY`/`STORE_PICKUP`) |
| Trạng thái ACTIVE/INACTIVE | – (badge "Đang hoạt động/Tạm ngừng" ở Store Finder, màu riêng `.status-badge-active/paused`) | Chữ thuần ("Đang kinh doanh") | `statusBadge` (xanh/vàng/xám) hoặc enum thô (`reviews`, `staff-assignments`) |
| Tồn kho | `availability` (xanh/xám) | Số thô | Số thô |

---

## 6. Responsive, truy cập, hiệu năng giao diện

> Toàn bộ mục này là **phân tích tĩnh**. Hành vi thật ở 375/768/1280px **chưa xác minh**.

### 6.1 Responsive

| Khu vực | Quan sát từ mã | Rủi ro |
| --- | --- | --- |
| Client navbar | `navbar-expand-lg` + toggler `#mainNav`; header chi nhánh `flex-wrap`; `topbar-right-info` ẩn dưới `md` | Hotline/miễn phí vận chuyển biến mất ở mobile (chấp nhận được) |
| Client catalog | `row-cols-1 row-cols-sm-2 row-cols-lg-3 row-cols-xl-4`; bộ lọc `col-12 col-md-*` (2 select cùng hàng `col-6` ở mobile) | Hero + bộ lọc chiếm màn hình đầu ở 375px |
| Giỏ hàng | `col-12 col-lg-8` / `col-lg-4`; có `@media` trong `<style>` | Dòng SP có 6 phần tử, `d-flex flex-wrap` – khả năng ngắt dòng xấu ở 375px |
| Checkout | `col-12 col-lg-8/4`, tile 2 cột `col-md-6` | Tóm tắt đơn nằm **dưới** form ở mobile (người dùng không thấy tổng tiền trước khi nhập) |
| Staff/Admin | Sidebar `offcanvas-lg`, nút toggler `d-lg-none` (tốt) | Staff header 112px (40+72) + sticky (UX-010) |
| Bảng rộng | `.table-responsive` có ở bảng Staff/Admin/`dataTable` | Cuộn ngang thay vì thu gọn thành thẻ; hàng đầu trang có cuộn nhưng cột "Thao tác" ở cuối bảng nằm ngoài khung nhìn |
| Bộ lọc | Không có thu gọn (collapse) – bộ lọc luôn mở; ở Client, bộ lọc xếp dọc 4 hàng ở mobile | Dài |
| Nút bấm | `btn-sm` ở nhiều thao tác Staff/Admin (cao ~31px) | Dưới 44px khuyến nghị cho chạm |

### 6.2 Truy cập (a11y)

| Hạng mục | Kết quả |
| --- | --- |
| Semantic | Có `<nav aria-label>`, `<main id="main-content">`, `<article>`, `<section aria-labelledby>`, `<fieldset><legend>` (checkout); **không có skip-link** tới `#main-content` |
| Label–input | Có `for` ở hầu hết; checkbox giỏ có `aria-label` động; `visually-hidden` label ở số lượng |
| Alt ảnh | `th:alt` có ở ảnh SP; logo `alt="OneShop Logo"`; ảnh thumbnail `th:alt="${product.name}"` giống nhau cho mọi thumbnail (không phân biệt) |
| Tiêu đề | Hai `<h1>` ở `/admin/inventory`; trang chủ dùng `<h1>`/`<h2>`/`<h3>` hợp lý |
| Bàn phím | Thumbnail ảnh và vài phần tử `onclick` nội tuyến (`switchMainImage`) trên `<img>` → không focus được; stepper dùng `button` (tốt) |
| Focus | Không thấy CSS tùy biến `:focus-visible`; dựa vào Bootstrap (outline mặc định có thể bị đè bởi `outline:none` trong `<style>` trang – **chưa kiểm** từng khối) |
| Live region | `role="alert"` ở lỗi giỏ/checkout (tốt); thông báo thành công không có `role="status"` |
| Tương phản | Xem bảng dưới |

**Tương phản của tông đã chốt (tính theo công thức WCAG 2.x):**

| Cặp màu | Tỉ lệ | Đạt AA chữ thường (4,5:1)? | Chỗ dùng |
| --- | --- | --- | --- |
| Chữ `#FFFFFF` trên nền `#FF7F50` | **2,50:1** | Không (cũng không đạt 3:1 cho chữ lớn) | `.btn-primary`, `.btn-add-cart`, nhãn, thẻ nổi bật |
| Chữ `#FF7F50` trên `#FFFFFF` | **2,50:1** | Không | link mặc định, `text-coral`, `.os-nav-link.active` |
| Chữ `#FF7F50` trên `#FFF8FA` (nền trang) | 2,39:1 | Không | link ở nền trang |
| Chữ `#FF7F50` trên `#FFF5F2` | 2,33:1 | Không | badge/eyebrow nền coral nhạt |
| Chữ `#FFFFFF` trên `#FF6F61` | 2,73:1 | Không | `oneshop.css` `.btn-primary` |
| Chữ `#FFFFFF` trên `#E95B50` | 3,46:1 | Không (chỉ đạt chữ lớn ≥ 18,66px đậm) | hover |
| Chữ `#FFFFFF` trên `#E86738` | 3,27:1 | Không | hover/active |
| Chữ `#707780` trên `#FFFFFF` | 4,53:1 | Sát ngưỡng, đạt | `--os-muted` |
| Chữ `#6C757D` trên `#F6F7FB` | 4,38:1 | **Không** | `text-secondary` ở nền `.os-app` Staff/Admin |
| Chữ `#BF360C` trên `#FFFFFF` | 5,60:1 | Đạt | `--bs-dark-coral` (đã có trong tông, chưa được dùng cho chữ) |
| Chữ `#FFFFFF` trên `#2B2D42` | 13,49:1 | Đạt | `.os-appbar` mặc định (đã bị Staff ghi đè bằng nền trắng) |

### 6.3 Ảnh

- Ảnh SP: `<img class="gallery-main-img">`, `<img>` trong thẻ: `th:src="${product.imageUrl}"` – URL Cloudinary gốc, **không có biến đổi** (`CloudinaryServiceImpl` chỉ `upload()` và lấy `secure_url`; không thêm `w_`, `f_auto`, `q_auto`). Ảnh gốc có thể rất lớn được nạp cho thẻ nhỏ ~200px.
- `loading="lazy"` có ở lưới catalog, thumbnail và ảnh trong giỏ; ảnh chính của chi tiết không lazy (đúng). Không có `width`/`height` trên `<img>` → có thể gây dịch chuyển bố cục (CLS) – dựa vào `aspect-ratio` CSS ở `.os-product-img`; **chưa xác minh** cho `.modern-product-card`.
- Không có `srcset`/`sizes`.
- Placeholder khi thiếu ảnh: SVG nội tuyến (có).

### 6.4 Số request, dung lượng, truy vấn

- Client tải: Bootstrap local 232 KB CSS + 80 KB JS, **cộng** Bootstrap CDN 5.3.3 (~228 KB) ở 8 trang chính (bị trùng); một request `/css/bootstrap.min.css` trả 404 ở mọi trang Client; không có `link rel=preload`.
- CSS nội tuyến lặp: ví dụ `cart/index` 923 dòng, `product/detail` 594 dòng chủ yếu là CSS nội tuyến, không cache được giữa các trang.
- **N+1 / tải thừa:**
  - Staff/Admin dùng `@EntityGraph(store, user)` ở các truy vấn đơn (không phát hiện N+1).
  - `ReviewRepository` dùng `@EntityGraph(user, product, order, order.store)` (không N+1).
  - `StoreServiceImpl.findStores` và `getProvinceCities`/`getAreas` mỗi lần nạp **toàn bộ** bảng `stores` (3 lần/trang `/stores`) rồi lọc bộ nhớ – ổn với 5 dòng.
  - `/orders`, `/staff/orders/delivery`, `/staff/orders/pickup`, Admin `stores/categories/brands` nạp **toàn bộ** không phân trang.
  - `StoreProductRepository.findSellableByProductIds` được gọi một lần cho cả trang (tránh N+1).
  - `OrderViewService.summary` đọc `o.getStore()`; được bảo vệ bằng `@EntityGraph("store")`. `getPickupQueue` đọc `o.getUser().getFullName()` – có `user` trong graph.
  - Chưa chạy SQL logging nên **chưa xác minh** số câu lệnh thực tế mỗi trang.

---

## 7. Nhất quán giữa 3 khu vực và với tông màu đã chốt

### 7.1 Khác nhau cho cùng một mục đích

| Mục đích | Client | Staff | Admin |
| --- | --- | --- | --- |
| Header/app bar | Thanh trên coral (`.os-topbar-coral`) + navbar trắng | Nền trắng 112px, dải coral 40px, logo `One.png` | Nền coral đặc 56px, text "OneShop Admin" |
| Nút chính | `.btn-primary` coral (#ff7f50 hoặc #ff6f61 tùy `oneshop.css`), nhiều biến thể lớp riêng (`.btn-add-cart`, `.btn-checkout-submit`, `.btn-select-store`…) | `.btn-primary` | `.btn-primary`, `.btn-outline-primary` |
| Bảng | Không có (thẻ) / `table` Bootstrap trong chi tiết đơn | `table os-table align-middle` | `table table-hover` / `dataTable` fragment |
| Phân trang | `.pagination` có nhãn « Trước / Sau » | Nút `Trang trước` / `Trang sau` | `.pagination` `Trước` / `Sau` |
| Bộ lọc | `.filter-panel` có nhãn viết hoa | Không có | `row g-2` không có nhãn nhìn thấy (`aria-label`) |
| Form | `.form-control-custom`, `.form-control-icon` | `form-control` Bootstrap | `form-control` Bootstrap |
| Thẻ | `.modern-product-card`, `.cart-store-group`, `.os-guide-card`… | `card shadow-sm` | `card` |
| Page heading | Hero lớn với eyebrow | `pageHeader` | `pageHeader` (một số `<h1>` trực tiếp) |
| Thông báo | `.cart-alert` hoặc `alert` | `alert` | `alert` |

### 7.2 Chỗ không bám tông đã chốt (`#FF7F50`)

| Chỗ | Hex | Bằng chứng |
| --- | --- | --- |
| Nút chính Client sau cùng bị ghi đè | `#ff6f61` / `#e95b50` | `oneshop.css:2–3, 14–22` (nạp cuối trong `layouts/client.html`) |
| Hover/active hard-code | `#e86738`, `#cc5427` | `bootstrap-coral.css`, `<style>` ở 23 template |
| Hero gradient | `#ffe3ec → #fff` (hồng) và `#fff0ed → #fffaf8` | `main.css:.os-hero`, `oneshop.css:.os-hero` – **hai định nghĩa `.os-hero` khác nhau** |
| Nền trang Client | `#fff8fa` (hồng nhạt) trong `main.css`, một số trang đặt nền khác | `main.css:11` |
| Hệ xám slate | `#0f172a`, `#64748b`, `#e2e8f0`, `#f1f5f9`… (~100 lần) | `<style>` trong `cart`, `checkout`, `product/*`, `store/list` |
| Nền Staff/Admin | `#f6f7fb` (xám xanh) | `main.css:.os-app` |
| Appbar Staff trước khi bị ghi đè | `#2b2d42` (navy) | `main.css:.os-appbar` – bị `!important` ghi đè bằng nền trắng ở `fragments/staff.html` |
| Staff dải coral gradient | `#ff8051 → #f05f3b` | `fragments/staff.html` |
| Ngôn ngữ nút/thẻ Admin | Bootstrap mặc định (không có coral đặc trưng ngoài app bar) | `templates/admin/*.html` |

---

## 8. Danh sách vấn đề tổng hợp

Sắp xếp theo mức độ giảm dần. "Đổi backend" = cần sửa Java/SQL/repository.

| ID | Màn hình/Route | Mô tả vấn đề | Bằng chứng | Mức | Ảnh hưởng | Loại | Backend? |
| --- | --- | --- | --- | --- | --- | --- | --- |
| UX-001 | Toàn bộ Client | Layout nạp `/css/bootstrap.min.css` (không tồn tại, 404); `head-resources.html` trỏ tới `libs.bundle.css`, `theme.bundle.css` không tồn tại; 8 trang nạp thêm Bootstrap 5.3.3 CDN đè Bootstrap 5.3.8 local; `custom.css` rỗng; `soyesencial-components.css` không ai dùng | `layouts/client.html:15`; `fragments/head-resources.html:5,8`; 8 template dòng 7–9; `static/css/` | Cao | Khách, Staff (error.html), mọi người dùng Client | Hiệu năng / Nhất quán | Không |
| UX-002 | Mọi danh sách | Không có điều khiển sắp xếp nào (thứ tự cứng trong repository) | 3.4; `order by` trong repository | Cao | Khách, Staff, Admin | Lọc | Có (thêm tham số sort) |
| UX-003 | `/products` | Thiếu lọc khoảng giá, "còn hàng", xóa lọc, hiển thị số kết quả; không có sắp xếp | `product/list.html`; `ProductSearchCriteria`; `StoreProductRepository.searchSellableInStore` | Cao | Khách | Lọc | Có |
| UX-004 | `/orders` | Không phân trang, không lọc theo trạng thái/ngày; thẻ đơn thiếu hành động nhanh | `CustomerOrderController.list`; `OrderRepository.findByUserEmail…` (List) | Cao | Khách | Lọc / Bố cục | Có |
| UX-005 | `/staff/orders`, `/staff/pickup`, `/staff/orders/delivery`, `/staff/orders/pickup`, `/staff/stock`, `/staff/inventory-history` | Không có lọc/tìm kiếm; delivery/pickup list không phân trang | Controller Staff; 3.2.4–3.2.5 | Cao | Staff | Lọc | Có |
| UX-006 | `/cart` | Tổng và số lượng ở tóm tắt không theo dòng đã chọn; đổi số lượng reload trang; xác nhận xóa hiện 2 lần (listener trùng); logic chọn nhóm viết hai lần | `cart/index.html:787`, `~885`; `main.js: initCartSelection` | Cao | Khách | Luồng | Không |
| UX-007 | Hủy đơn, ẩn đánh giá, bỏ phân công, trạng thái user, hoàn tất đơn Staff, điều chỉnh tồn, mô phỏng thất bại | Hành động có hậu quả không có xác nhận; không chống bấm lặp trên mọi form | 5.4; `main.js` | Cao | Khách, Staff, Admin | Trạng thái | Không |
| UX-008 | Đơn hàng (Client/Staff/Admin), lịch sử tồn kho, Admin dashboard | Badge trạng thái xám cho mọi trạng thái; `checkout/result` có bảng màu riêng; Admin/Staff lộ enum thô (`DELIVERY`, `COD · UNPAID`, `STOCK_ADJUST`, `ACTIVE/HIDDEN`) | `staff/delivery/fragments.html:state`; `admin/orders.html:9`; `staff/stock/history.html:172`; `admin/reviews.html:20` | Cao | Mọi vai trò | Nhất quán | Không |
| UX-009 | Mọi nút/ link chính | Chữ trắng trên `#FF7F50` chỉ đạt 2,50:1; link coral trên trắng 2,50:1; `text-secondary` trên nền Staff/Admin 4,38:1 | 6.2 | Cao | Người dùng thị lực kém / ngoài nắng | Truy cập | Không |
| UX-010 | `/staff/**` | Header Staff cao 112px, sticky; sidebar sticky `top: 56px` + `height: calc(100vh - 56px)` (theo `--os-appbar-height`) → lệch 56px so với header thực | `fragments/staff.html` (`grid-template-rows: 40px 72px; min-height:112px`); `main.css:.os-sidebar` | Cao | Staff | Responsive / Bố cục | Không |
| UX-011 | Chi tiết SP, chi tiết đơn (đánh giá) | Form đánh giá mất nội dung khi lỗi; thông báo `reviewed/reviewError` nằm cuối trang; chưa xác minh redirect có neo `#reviews` | `product/detail.html` (khối `#reviews`); `ReviewController` | Trung bình | Khách | Trạng thái | Không |
| UX-012 | `/checkout` | Nhập lại người nhận/SĐT/địa chỉ cho từng chi nhánh; lỗi chỉ ở đầu trang; nhãn "VNPAY / Thẻ" nhưng thanh toán là mô phỏng | `checkout/index.html`; `payment/index.html` | Trung bình | Khách | Luồng | Không (copy) / Có (nếu dùng chung) |
| UX-013 | `/stores` | Thiếu tìm theo tên/địa chỉ; `area` chỉ cập nhật sau submit; `equalsIgnoreCase` phân biệt dấu; không phân trang; eyebrow "TOÀN QUỐC" | `StoreServiceImpl.findStores`; `store/list.html` | Trung bình | Khách | Lọc | Có |
| UX-014 | `/staff`, `/admin` | Chỉ số dashboard không click được; không dẫn tới danh sách đã lọc tương ứng | `staff/dashboard.html`; `admin/dashboard.html` | Trung bình | Staff, Admin | Luồng / Lọc | Có (lọc theo số liệu) |
| UX-015 | `/staff/pickup` vs `/staff/orders/pickup` | Hai màn hình cho cùng nghiệp vụ với cột/hành động khác nhau; không tra cứu nhanh theo mã nhận hàng | `StaffPickupQueueController`, `StaffPickupController` | Trung bình | Staff | Luồng / Nhất quán | Có (tra cứu mã) |
| UX-016 | `/admin/products`, `/store-products`, `/orders`, `/users`, `/reviews` | Thiếu bộ lọc trạng thái, danh mục, thương hiệu, tìm người dùng, "chưa phản hồi"; select SP chứa toàn bộ SP | 3.2.6 | Trung bình | Admin | Lọc | Có |
| UX-017 | Giao diện chung | Phân trang 5 phiên bản; trạng thái rỗng 4 phiên bản; page heading 3; thông báo 3 | 1.4 | Trung bình | Mọi vai trò | Nhất quán | Không |
| UX-018 | Header Client | Hotline và "Miễn phí vận chuyển cho đơn từ 500.000 ₫" hard-code, không có logic phí vận chuyển trong mã | `fragments/header.html:137` | Trung bình | Khách | Trạng thái (thông tin sai) | Không (copy) |
| UX-019 | Phân trang toàn hệ thống | Phân trang chưa được kiểm với dữ liệu lớn (seed 6 SP, 5 đơn); vượt trang trả trang rỗng | 3.3 | Trung bình | Mọi vai trò | Hiệu năng / Lọc | Không |
| UX-020 | Ảnh SP | Dùng URL Cloudinary gốc, không `w_`/`f_auto`/`q_auto`, không `srcset`, không `width/height` | `CloudinaryServiceImpl.java:43–46`; `product/list.html` | Trung bình | Khách (mobile/mạng yếu) | Hiệu năng | Không (template) |
| UX-021 | `/error` | Một trang lỗi Client cho cả `/staff`, `/admin`; không có nút quay lại; 403 không giải thích vai trò | `error.html` | Trung bình | Staff, Admin | Trạng thái | Không |
| UX-022 | Staff/Admin bảng | `.table-responsive` cuộn ngang thay vì thu gọn cột; nút `btn-sm` dưới 44px | `staff/orders/fragments.html`, `staff/delivery/index.html` | Trung bình | Staff trên tablet/mobile | Responsive | Không |
| UX-023 | `/admin/inventory` | Dùng chung template với `store-products`, có hai `<h1>` và nút "Gắn sản phẩm" không phù hợp | `admin/store-products.html` | Thấp | Admin | Nhất quán / Truy cập | Không |
| UX-024 | `/admin/reviews` | Form trả lời trong ô bảng làm hàng quá cao; phân trang gọi với `storeId=null` thừa | `admin/reviews.html:28` | Thấp | Admin | Bố cục | Không |
| UX-025 | Catalog/Store Finder/Admin danh mục-thương hiệu | `order by` thiếu `id` phụ ở Store Finder, categories, brands | `StoreRepository`, `CategoryRepository`, `BrandRepository` | Thấp | – | Lọc | Có |
| UX-026 | Mọi trang | Không có skip-link; thumbnail chỉ click chuột; focus tùy biến chưa kiểm | `layouts/*.html`; `product/detail.html` | Thấp | Người dùng bàn phím | Truy cập | Không |
| UX-027 | `fragments/components` | `formField`, `formSelect`, `modal`, `card`, `emptyRow` được định nghĩa nhưng không ai gọi; `main.js` có `data-auto-dismiss` không ai dùng | grep trong `templates/` | Thấp | Dev | Nhất quán | Không |
| UX-028 | Client | Client phụ thuộc fragment ở `staff/delivery/fragments.html` (state/payment/method) | `orders/index.html`, `orders/fragments.html` | Thấp | Dev | Nhất quán | Không |
| UX-029 | Tìm kiếm sản phẩm | Tìm không dấu phụ thuộc collation DB (`Vietnamese_100_CI_AI`); chưa có test; `lower()` + `%…%` không dùng index | 3.1 | Thấp | Khách | Lọc | Không |
| UX-030 | `/products` | Thẻ SP không có nút thêm giỏ; phải vào chi tiết | `product/list.html` | Thấp | Khách | Luồng | Không |

---

## 9. Chưa xác minh được và câu hỏi cho chủ dự án

### 9.1 Chưa xác minh

| Hạng mục | Lý do |
| --- | --- |
| Hiển thị thật ở 375/768/1280px (cuộn ngang, chồng lấn, header Staff, bảng rộng) | Không chạy được ứng dụng với dữ liệu seed trong phiên này |
| Cascade CSS thực tế trên Client (màu nút cuối cùng là `#ff6f61` hay `#FF7F50`) | Phụ thuộc thứ tự nạp và specificity; cần DevTools |
| Collation thực của DB đang chạy; khả năng tìm không dấu | Chỉ có script tạo DB; chưa kết nối DB thật |
| `/staff/*`, `/admin/*` của SiteMesh với đường dẫn nhiều cấp | Chỉ suy ra từ test layout đã pass |
| Số lượng câu lệnh SQL mỗi trang (N+1 thật) | Chưa bật SQL log |
| Hành vi `ReviewController` sau khi gửi đánh giá (neo `#reviews`) | Chưa đọc chi tiết |
| Số dòng thật trong DB (khác seed) | Chưa kết nối DB |
| Tương phản các màu trong `<style>` từng trang ngoài các cặp đã nêu | Chỉ kiểm cặp trọng yếu |
| `[` trong `LIKE` với `escape '\\'` trên SQL Server | Chưa có test chứng minh |
| Hành vi trình duyệt khi trang giỏ reload (khôi phục checkbox/cuộn) | Cần chạy thật |

### 9.2 Câu hỏi cần chủ dự án quyết định

1. **Tông coral và tương phản:** `#FF7F50` đã chốt không đạt 4,5:1 cho chữ. Chấp nhận giữ nền coral với chữ tối (ví dụ `#292D32`/`#BF360C` đã có trong tông) cho nút, hay chấp nhận rủi ro truy cập?
2. **Sắp xếp và lọc mặc định:** Catalog cần sắp xếp theo giá/tên/mới nhất? Lọc "chỉ còn hàng" mặc định bật hay tắt?
3. **Staff nhiều chi nhánh:** một Staff có thể thuộc nhiều Store – danh sách cần lọc/chuyển Store trực tiếp hay luôn gộp?
4. **Hai luồng nhận tại cửa hàng của Staff:** giữ `/staff/pickup` hay `/staff/orders/pickup`?
5. **Giao diện thanh toán:** nhãn "VNPAY / Thẻ" là nhãn tương lai (để chuẩn bị tích hợp thật) hay cần đổi thành "Thanh toán trực tuyến (mô phỏng)"?
6. **Phí vận chuyển/hotline ở header:** "Miễn phí vận chuyển từ 500.000 ₫" và "Hotline 1900 0000" là dữ liệu thật hay chỗ giữ chỗ?
7. **Admin:** ưu tiên giao diện Admin theo ngôn ngữ coral như Client/Staff hay giữ Bootstrap mặc định?
8. **Khối "Toàn quốc"** ở Store Finder: dự kiến mở rộng ngoài TP.HCM, hay chỉ vài Store?
9. **Quy mô dữ liệu mục tiêu** (SKU, đơn/ngày, chi nhánh) để quyết định ngưỡng phân trang và lọc phía server.
10. **Dữ liệu seed:** có muốn bổ sung seed đủ lớn (≥ 50 SP, ≥ 100 đơn) để kiểm phân trang/lọc trước khi sửa UI không?
