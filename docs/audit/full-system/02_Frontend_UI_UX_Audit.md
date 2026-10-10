# Frontend / UI / UX Audit

Ngày audit: 09/10/2026 (Asia/Saigon, UTC+7). Commit: `88c22132955b9c7a88be5a7a6ea08ff167d83776`. **AUDIT ONLY**; không remediation, thay dependency/schema/test, reseed hay deploy.

## Phương pháp và giới hạn

Chrome **thật** 155 headless, CDP qua Node builtin WebSocket; không có Playwright/Puppeteer nên không cài dependency. Screenshot là raster từ Page.captureScreenshot. Browser navigation, radio/checkbox change, button.click/requestSubmit và form submit thực qua Thymeleaf/SiteMesh/controllers. Browser fetch chỉ dùng negative probes/duplicate/race đã ghi nhãn; không gọi đó là click UI. Không chọn mock page/data renderer.

Survey: 46 route/ngữ cảnh ×3 viewport, 138 DOM records. Có intentional404 (missing product/cross-Store order); một probe đoán sai `/staff/stock/1/history` trả404 — **không là broken UI link**. Link/redirect production đúng `/staff/inventory-history/{id}`, đã mở và screenshot ở additional records. 137 PNG gồm survey/journey/extra probes. Không tuyên bố đã click mọi permutation của 97 endpoints.

## Visual/layout và accessibility cơ bản

Đã xem trực quan screenshots Client catalog mobile, Staff desktop, Admin inventory mobile; DOM/PNG còn lại được capture để reviewer kiểm lại. SiteMesh chọn đúng client/staff/admin; Bootstrap/navbar/table/card/forms hoạt động. Mobile navbar toggle đổi `collapse` → `collapse show`; desktop sidebar/Store badges hiển thị đúng scope. Survey không có **overflow toàn trang**, broken loaded images hoặc unlabeled native controls. Các bảng Admin dùng vùng scroll ngang nội bộ trên mobile; quantity nằm ngoài viewport đầu nhưng có thể scroll, không phải dữ liệu bị mất. Catalog seed không có ảnh Product (0 product_images), placeholder lớn là empty image state có chủ đích, không là CDN lỗi.

Form labels, accessible selection labels, invalid-feedback, semantic fieldsets, error/empty states được kiểm bằng DOM và một số screenshot. Chưa đo toàn bộ contrast/keyboard focus order/screen-reader, chưa cuộn mọi lazy image hoặc mọi breakpoint; **NOT VERIFIED** cho WCAG compliance và mọi ảnh lazy chưa load. Không lấy kết quả `brokenImages=[]` làm chứng minh Cloudinary CDN hiện tại PASS.

## Functional workflows

| Workflow | UI thực tế / DB evidence | Kết quả |
| --- | --- | --- |
| Customer đăng ký/login/logout | Fixture register→login→home; logout→/login?logout, ONESHOP_TOKEN removed. Invalid email/name/password/phone có feedback | PASS thông thường; Unicode case500 FSA-02 |
| Catalog→detail→selected Store→cart | SKU503 ở Store47/48/49 giá111k/121k/131k; đổi Store không đổi lines trước, repeated add quantity2 | PASS |
| Cart selection→checkout | `.os-cart-group-toggle` chọn cả Store, item flags/total thay đổi; 3 groups → preview 3 blocks, fulfillment/payment radio JS đúng pairing | PASS |
| Checkout→payment→delivery | Ba orders từ một checkout; B ONLINE attempt/success; A COD prepare/pack/ship/complete, Paid at completion | PASS |
| Pickup | B/C ready; Customer code chỉ hiển thị khi ready; Staff input code complete, repeat không có transition/payment thứ hai | PASS |
| Customer cancel | CONFIRMED unpaid order9812 form CSRF→CANCELLED; repeat backend no-op, completed reject400 | PASS |
| Admin quản trị | Create Store/Category/Brand/Product/StoreProduct/Staff/assignment; price edit; inventory/order store filters GET submit; soft-disable giữ lịch sử | PASS cho actions đã chạy |
| Staff vận hành | Login→3 Store dashboard→order actions→stock count→history; same JWT revoke/reactivate và user inactive/reactivate | PASS |
| Admin Review/User | Actual lists/edit controls + current SQL/HTTP test basic moderation and role/status changes | PASS; live UI moderation seed không bị sửa |
| Customer completed→review | Completed order/customer product detail không có action đánh giá; ReviewService skeleton, no customer controller | **FAIL FSA-01** |
| Image upload/replace/delete | UI form/action/CSRF render; không submit live trong audit. Current mocked provider tests, historical live Product | **NOT VERIFIED hiện tại** cho live UI/provider |

Tất cả mutation UI dùng fixture mới; [fixture-db.json](evidence/fixture-db.json) sau mutation khớp UI: 3 COMPLETED/PAID, 1 CANCELLED/UNPAID, 3 successful per-order payments, 16 history/11 inventory movements.

## Filter, pagination, validation/error

Admin order filter Store48 chỉ có fixture Order9810, inventory Store49 chỉ có SKU503/stock821 cùng dữ liệu Store đó; current DB pagination test `orderStockUserAssignmentReviewPaginationKeepsFiltersAndStableTotals` kiểm >20 fixtures, stable sort/totals/filter preservation. Browser baseline ít rows không có link trang kế; **không tuyên bố đã click next-page trên dataset không có next-page**. Catalog `q=AUDIT_NOT_FOUND_261009` empty result; missing Product404 và foreign order404 không lộ dữ liệu.

Web POST form trong actual surveys có `_csrf`; logout cũng có. Negative CSRF403 xác minh backend. Validation lỗi ordinary render feedback; Unicode signup500 là field validation mismatch confirmed. Preliminary security probes dùng raw cookie với XOR CSRF trả403 cho guessed review paths; đã kiểm lại bằng rendered hidden token ở journey và nhận404. Không dùng probe403 ban đầu để kết luận route review thiếu.

Không tìm thấy success banner giả ở những mutation đã kiểm: create/update/cancel/read-after-write khớp SQL. Chưa thử mọi modal/confirmation với mọi dataset hoặc UI lỗi provider live; không phát sinh finding suy đoán. Không redesign/tự sửa màu sắc/layout.

## Screenshot chọn lọc

- [Catalog mobile](evidence/screenshots/guest-_products-mobile.png), [Staff dashboard desktop](evidence/screenshots/staff-_staff-desktop.png), [Admin inventory mobile](evidence/screenshots/admin-_admin_inventory-mobile.png).
- [Unicode đăng ký500](evidence/screenshots/registration-unicode-error.png).
- [History mobile sau Admin/Staff race](evidence/screenshots/additional-mobile-history.png), [Mobile navbar mở](evidence/screenshots/additional-mobile-navbar-toggle.png).
- Completed/no-review, checkout ba Order, pickup ready/completed, cancellation và disabled-history có PNG theo số record trong [screenshots](evidence/screenshots/), đối chiếu label ở browser-journey.json.

## Danh sách survey đầy đủ

| Actor | Route/ngữ cảnh đã render | Desktop / tablet / mobile |
| --- | --- | --- |
| guest | `/` | 1440×900 / 768×1024 / 390×844 |
| guest | `/products` | 1440×900 / 768×1024 / 390×844 |
| guest | `/products/1` | 1440×900 / 768×1024 / 390×844 |
| guest | `/stores` | 1440×900 / 768×1024 / 390×844 |
| guest | `/login` | 1440×900 / 768×1024 / 390×844 |
| guest | `/register` | 1440×900 / 768×1024 / 390×844 |
| guest | `/products?q=AUDIT_NOT_FOUND_261009` | 1440×900 / 768×1024 / 390×844 |
| guest | `/products/999999` | 1440×900 / 768×1024 / 390×844 |
| customer | `/cart` | 1440×900 / 768×1024 / 390×844 |
| customer | `/orders` | 1440×900 / 768×1024 / 390×844 |
| customer | `/orders/1` | 1440×900 / 768×1024 / 390×844 |
| customer | `/orders/2` | 1440×900 / 768×1024 / 390×844 |
| customer | `/orders/5` | 1440×900 / 768×1024 / 390×844 |
| customer | `/orders/2/payments` | 1440×900 / 768×1024 / 390×844 |
| staff | `/staff` | 1440×900 / 768×1024 / 390×844 |
| staff | `/staff/orders` | 1440×900 / 768×1024 / 390×844 |
| staff | `/staff/orders/1` | 1440×900 / 768×1024 / 390×844 |
| staff | `/staff/orders/delivery` | 1440×900 / 768×1024 / 390×844 |
| staff | `/staff/orders/delivery/1` | 1440×900 / 768×1024 / 390×844 |
| staff | `/staff/orders/pickup` | 1440×900 / 768×1024 / 390×844 |
| staff | `/staff/stock` | 1440×900 / 768×1024 / 390×844 |
| staff | `/staff/stock/1/adjust` | 1440×900 / 768×1024 / 390×844 |
| staff | `/staff/stock/1/history` | 1440×900 / 768×1024 / 390×844 |
| staff | `/staff/orders/2` | 1440×900 / 768×1024 / 390×844 |
| admin | `/admin` | 1440×900 / 768×1024 / 390×844 |
| admin | `/admin/stores` | 1440×900 / 768×1024 / 390×844 |
| admin | `/admin/stores/new` | 1440×900 / 768×1024 / 390×844 |
| admin | `/admin/stores/1/edit` | 1440×900 / 768×1024 / 390×844 |
| admin | `/admin/categories` | 1440×900 / 768×1024 / 390×844 |
| admin | `/admin/categories/1/edit` | 1440×900 / 768×1024 / 390×844 |
| admin | `/admin/brands` | 1440×900 / 768×1024 / 390×844 |
| admin | `/admin/brands/1/edit` | 1440×900 / 768×1024 / 390×844 |
| admin | `/admin/products` | 1440×900 / 768×1024 / 390×844 |
| admin | `/admin/products/new` | 1440×900 / 768×1024 / 390×844 |
| admin | `/admin/products/1/edit` | 1440×900 / 768×1024 / 390×844 |
| admin | `/admin/store-products` | 1440×900 / 768×1024 / 390×844 |
| admin | `/admin/store-products/new` | 1440×900 / 768×1024 / 390×844 |
| admin | `/admin/store-products/1/edit` | 1440×900 / 768×1024 / 390×844 |
| admin | `/admin/users` | 1440×900 / 768×1024 / 390×844 |
| admin | `/admin/users/new` | 1440×900 / 768×1024 / 390×844 |
| admin | `/admin/users/5/edit` | 1440×900 / 768×1024 / 390×844 |
| admin | `/admin/staff-assignments` | 1440×900 / 768×1024 / 390×844 |
| admin | `/admin/inventory` | 1440×900 / 768×1024 / 390×844 |
| admin | `/admin/orders` | 1440×900 / 768×1024 / 390×844 |
| admin | `/admin/orders/1` | 1440×900 / 768×1024 / 390×844 |
| admin | `/admin/reviews` | 1440×900 / 768×1024 / 390×844 |
