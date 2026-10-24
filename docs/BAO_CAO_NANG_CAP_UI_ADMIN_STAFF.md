# Báo cáo nâng cấp UI Admin + Staff — OneShop V3

Ngày hoàn tất: **23/10/2026** · Repo: `D:\One_Shop` · Kết quả cuối: **BUILD SUCCESS — 1671/1671 test pass (0 failure, 0 error, 0 skipped)**

---

## 1. Mục tiêu và phạm vi

Nâng cấp toàn bộ giao diện hai không gian quản trị (**Admin** — toàn chuỗi, **Staff** — theo chi nhánh) từ phiên bản Bootstrap MVP lên một hệ thống quản trị chuỗi nhất quán, mà **không thay đổi**:

- Backend V3 (Spring Boot / Thymeleaf / Bootstrap / JPA / SQL Server / SiteMesh / JWT / Cloudinary),
- DB schema, entity, nghiệp vụ V3,
- Bộ test suite đã có (1671 test) — mọi thay đổi code thỏa mãn ràng buộc test (test-driven: chuỗi text/attr chính xác, chữ ký hàm, redirect…).

## 2. Kiến trúc UI mới

| Thành phần | Vai trò |
|---|---|
| `templates/layouts/admin.html`, `templates/layouts/staff.html` | Decorator SiteMesh: thanh chủ đề coral, sidebar phân quyền, scope badge (`id="admin-scope-badge"` "Toàn chuỗi cửa hàng" / `id="staff-store-scope"`), nút "Trang bán hàng" |
| `static/css/admin-staff.css` (mới) | Toàn bộ ngôn ngữ hình ảnh: theme coral `#ff7f50 / #e86738`, KPI chips, soft badge (`os-soft-*`), filter card + grid, bảng `os-table`, pagination, sticky sidebar, hàng đợi (mục 16: cột phụ ≥1600px), thumb/placeholder, modal, delta tồn kho, responsive breaks 991.98 / 768 / 1600px |
| `static/js/admin-staff.js` (mới) | Hành vi dùng chung: **modal xác nhận có lý do** (`data-confirm-reason` → `#osReasonModal` + `#osReasonInput` + `#osReasonConfirm`, bắt buộc lý do khi `data-reason-required`), preview nhập tồn kho tuyệt đối (`data-qty-preview`: giá trị → delta + cảnh báo 0), tint hàng, khởi tạo an toàn DOMContentLoaded |
| `templates/fragments/components.html`, `fragments/staff.html` | Fragment dùng chung: `pageHeader`, `dataTable`, `statusBadge`, phân trang, scope badge |
| `templates/admin/staff/...` | 12 trang admin + 11 trang staff viết lại theo ngôn ngữ mới, giữ nguyên mọi ràng buộc text/attr của test |

Nguyên tắc thiết kế: KPI/live counters đọc từ dữ liệu thật (không hard-code), nhãn tiếng Việt tự nhiên, badge màu mềm thay `text-bg-*` chói, mọi bảng trong `div.table-responsive` (overflow an toàn), đầy đủ CSRF thuần form (không JSPOST với token riêng).

## 3. Màn hình đã nâng cấp

Mỗi trang dưới đây có thư mục riêng `screenshots/<role>/<số>-<tên trang>/`, bên trong là ảnh theo
4 tỉ lệ thiết bị chuẩn — desktop 1440×900, laptop 1366×768, tablet 768×1024, phone 390×844 —
và trang dài được tách thành `-p01/-p02/...` theo lượt cuộn (chi tiết trong `screenshots/manifest.json`).

### Admin (toàn chuỗi)
| Trang | Điểm mới | Thư mục ảnh |
|---|---|---|
| Tổng quan `/admin` | KPI chuỗi, nút nhanh | `admin/010-tong-quan/` |
| Chi nhánh `/admin/stores` | Filter (tìm/khu vực/trạng thái) + phân trang; nút "Thêm chi nhánh" | `admin/200-chi-nhanh/` |
| Danh mục `/admin/categories` | Bảng thumb + placeholder chuẩn, form upload có validate | `admin/130-danh-muc/` |
| Thương hiệu `/admin/brands` | Bảng thumb + placeholder | `admin/150-thuong-hieu/` |
| Sản phẩm / SKU `/admin/products` | Thumb, trạng thái, filter | `admin/100-san-pham/` |
| Sản phẩm theo chi nhánh `/admin/store-products` | Filter store, giá từng điểm bán | `admin/070-san-pham-theo-chi-nhanh/` |
| Tồn kho toàn chuỗi `/admin/inventory` | KPI tổng hợp, filter, bảng tồn | `admin/060-ton-kho/` |
| Đơn hàng `/admin/orders` | Card-list + filter trạng thái (thiết kế bắt buộc: 0 `<table>`) | `admin/020-don-hang/` |
| Thanh toán `/admin/payments` | Bảng thanh toán + badge trạng thái | `admin/040-thanh-toan/` |
| Đánh giá `/admin/reviews` | **KPI chips (tổng / chưa phản hồi / đang Ẩn)** + bộ lọc q/status/replied + modal Ẩn bắt buộc lý do | `admin/240-danh-gia/` |
| Phân công nhân viên `/admin/staff-assignments` | Bảng phân công + status badge | `admin/230-phan-cong-nhan-vien/` |
| Người dùng `/admin/users` | Bảng theo vai trò | `admin/170-nguoi-dung/` |

### Staff (chi nhánh)
| Trang | Điểm mới | Thư mục ảnh |
|---|---|---|
| Tổng quan `/staff` | KPI chi nhánh + "Phần giao cần xử lý" (`data-metric`), **không** chứa "Đơn cần xử lý" (ràng buộc test) | `staff/010-tong-quan/` |
| Hàng đợi phần giao `/staff/fulfillments` | Heading "Đơn cần xử lý · giao tận nơi / nhận tại cửa hàng", `#fulfillment-count`, filter `q`, các cột đúng test (Khách hàng/Hình thức/Thanh toán/Trạng thái phần giao/Trạng thái đơn/Phân công lúc) | `staff/020-don-can-xu-ly/`, `staff/030-loc-theo-khach-hang/` |
| Giao tận nơi `/staff/fulfillments/delivery` (+`/pickup`) | 2 tab theo hình thức | `staff/040-giao-tan-noi/`, `staff/050-nhan-tai-cua-hang/` |
| Chi tiết phần giao `/staff/fulfillments/{id}` | Timeline lịch sử, hành vi bàn giao vận chuyển, CSRF | `staff/060-chi-tiet-phan-giao/` |
| Tồn kho chi nhánh `/staff/stock` | KPI (18 SKU…), modal điều chỉnh `adjust-modal-*`, preview **giá trị tuyệt đối** + delta | `staff/130-ton-kho/`, `staff/150-dieu-chinh-ton/` |
| Lịch sử tồn kho `/staff/inventory-history` | 2 tab (log mặc định / select), phân trang riêng từng view | `staff/160-lich-su-ton-kho/`, `staff/170-lich-su-ton-kho-select/`, `staff/180-lich-su-theo-san-pham/` |
| Đánh giá chi nhánh `/staff/reviews` | Hàng đợi + trả lời | `staff/110-danh-gia/` |
| Đơn hàng `/staff/orders` | Alias + base phân trang `/staff/orders?page=1`, 2 tab giao/nhận | `staff/070-don-hang-tong-hop/`, `staff/080-don-hang-giao-tan-noi/`, `staff/090-don-hang-nhan-tai-cua-hang/`, `staff/100-chi-tiet-don-hang/` |

### Khách (chưa đăng nhập) và khách hàng — bộ chụp đầy đủ mới
| Role | Trang chụp | Thư mục |
|---|---|---|
| Khách | Trang chủ, Đăng nhập (+ lỗi, vừa đăng xuất), Đăng ký, Sản phẩm (+ lọc `?q=serum`), Chi tiết sản phẩm, Hệ thống cửa hàng (+ lọc tỉnh/ TP), Ghi công hình ảnh, Lỗi 404 | `guest/010-trang-chu` … `guest/120-loi-404` |
| Khách hàng | Trang chủ, Tài khoản, Sản phẩm, Chi tiết sản phẩm, Giỏ hàng, Đặt hàng **giao tận nơi** + **nhận tại cửa hàng**, Đơn của tôi, Chi tiết đơn, Trang thanh toán, Lỗi 404 | `customer/010-trang-chu` … `customer/110-loi-404` |
| Cả hai | Modal mở: điều chỉnh tồn (staff) và lý do Ẩn đánh giá (admin, capture mở rồi hủy, không POST) | `staff/200-ton-kho-mo-cua-so-dieu-chinh/`, `admin/260-danh-gia-mo-cua-so-ly-do/` |

Mỗi route trên được chụp đủ 4 tỉ lệ (1440×900 / 1366×768 / 768×1024 / 390×844) — tổng **599 ảnh**, 
61-route page × 4 tỉ lệ + 2 modal; trang dài tự tách phần theo chiều cao viewport (vd Tồn kho Staff
3307px → 4 phần `-p01..-p04`). Không còn bộ ảnh `resp-*` riêng — mọi trang đều sweep đủ.

## 4. Thay đổi backend (tối thiểu, tương thích chữ ký)

**Quy tắc: mọi nơi test gọi trực tiếp giữ nguyên chữ ký; bổ sung bằng overload/spec, không đổi hành vi V3.**

- `AdminReviewService` + `AdminReviewController` + `ReviewRepository`: giữ `getReviews(int)`; thêm overload có filter `(int, q, status, replied)` với `Specification` (null khi không lọc, giữ `@EntityGraph`), `countByStatus` / `countByReviewReplyIsNullAndReplyIsNull`, DTO `AdminReviewStats`.
- `StaffFulfillmentController` + `StaffFulfillmentService` + `OrderFulfillmentRepository`: `show()` 12-arg; `q` trống gọi 2-arg `getQueue(filter,page)` (stub của test), `q` có gọi 3-arg; `searchForStaff` 7-arg giữ + overload 10-arg `:pattern` (LIKE escape `!`).
- `AdminOperationsService.getOrders(Long storeId, int page)` overload mới delegate 8-arg.
- `StaffStockController.historySelection` thêm `@RequestParam view` (`select` → tab chọn tồn).
- `StaffInventoryService.staffOptions()` dedupe theo id (LinkedHashSet giữ thứ tự).
- `StaffStoreAssignmentRepository.findActiveStaffOfStores` bỏ `DISTINCT` (SQL Server không cho ORDER BY cột ngoài select-distinct).
- `StoreProductServiceImpl.searchInventory` null-safe khi nhận diện `stockLevel` trước khi dùng (`INVENTORY_LEVELS.contains`) — sửa NPE 500 ở `/admin/inventory`.
- `AdminStoreController.list`: **null-safe** (bản cuối) — `areas` từ `getAllStores()` (guard null) và page từ `searchStores` với fallback `Page.empty()` để template luôn có page object (lỗi body rỗng khi service trả null).

## 5. Bảo mật và phân quyền

- Kiểm chéo bằng automation: staff → `/admin/*` = **403** ("Bạn không có quyền truy cập"); admin → `/staff/*` = **403**; guest → `/admin|staff/*` = **302 về login**.
- Tất cả POST render/redirect giữ CSRF; modal ẩn đánh giá **bắt buộc lý do** (lý do rỗng → chặn bằng `is-invalid`, không POST; lý do được gửi kèm form qua field ẩn `reason`); CSRF token không xuất hiện trong GET query.

## 6. Kiểm chứng

### 6.1 Test suite cuối
```
Tests run: 1671, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS — Total time 5:34 min
(DB test riêng: oneshop_v3_local_test — không chạm DB dev `oneshop`)
```
Các bó test ràng buộc chính đều xanh: `LayoutsIntegrationTest`, `StaffFulfillmentWebIntegrationTest`, `StaffInventoryDatabaseIntegrationTest`, `StaffOperationsDatabaseIntegrationTest`, `Phase11AdminDatabaseIntegrationTest`, `FulfillmentHttpDatabaseIntegrationTest`, `CatalogWebIntegrationTest` (10), `CategoryAdminSecurityWebIntegrationTest` (5), `ReviewV3HttpDatabaseIntegrationTest` (9), `BrowserV3DatabaseIntegrationTest` (7 mode e2e).

### 6.2 Browser automation trên web thật (8080, headless Chrome CDP)
- `scripts/capture_full_ui.mjs`: bộ chụp đầy đủ theo role — **599 ảnh / 69 trang / 4 role** (guest, customer, staff, admin), mỗi trang × 4 tỉ lệ thiết bị chuẩn (1440×900, 1366×768, 768×1024, 390×844), trang dài tự tách phần `-p01/-p02/...` theo chiều cao viewport; kèm trạng thái mở rộng: đăng nhập (thường / lỗi / vừa đăng xuất), form thêm + sửa từng loại, 403/404, modal mở (điều chỉnh tồn của staff, lý do Ẩn của admin — chỉ mở rồi đóng, không POST). GET-only, trừ 1 lần thêm món vào giỏ khi giỏ trống của khách demo (xóa lại ngay sau khi chụp); tổng kết tại `screenshots/manifest.json` (`summary`, `runtimeErrors` = các trang 404/403 chủ đích).

- `target/be07-evidence/browser/<MODE>-*/`: 7 luồng e2e COD / COD_SPLIT / PICKUP / ONLINE / FAILED / REFUND / **REVIEW** — luồng REVIEW xác minh moderation admin đúng qua **modal confirm-reason**: click Ẩn → `#osReasonModal.show` → điền `#osReasonInput` → submit `#osReasonConfirm` → redirect `?success`, **KPI "Đang bị Ẩn" tăng 0 → 1** (ảnh `06-admin-moderation.png`), review cập nhật HIDDEN + phản hồi staff (xác nhận JDBC bằng test).

### 6.3 Bộ ảnh bàn giao
- `screenshots/{guest,customer,staff,admin}/`: **bộ chụp đầy độc cuối cùng** — 61 route thông thường + 2 trạng thái modal mở, 4 tỉ lệ thiết bị cho từng route, trang dài tách nhiều phần; ánh xạ từng trang ở mục 3, chi tiết từng file (URL, tiêu đề, HTTP status, scrollY) trong `screenshots/manifest.json`.
- `screenshots.zip` (thư mục gốc repo): lưu giữ bộ ảnh **bản đầu quá trình** (theme coral đã lắp nhưng chưa có bộ lọc, KPI chips, phân trang, hàng đợi phân nhóm) — mức trung gian để so sánh.
- Lưu ý: bản UI MVP hoàn toàn gốc (Bootstrap MVP mặc định) không còn file ảnh trên máy; mô tả trạng thái "trước": bảng trần Bootstrap không filter, badge `text-bg-*` chói, sidebar mỗi trang một kiểu, phủ nhiều style inline trùng lặp — đã thống nhất hết sang ngôn ngữ `os-*`.

## 7. Lỗi phát hiện và xử lý trong giai đoạn cuối (ghi nhận)

1. `be07-e2e.mjs` REVIEW — nút Ẩn giờ mở modal nên submission trực tiếp của browser cũ **không** ẩn được; sửa flow như mục 6.2. ✅ pass đủ 7/7 mode
2. `reviews.html` badge cửa hàng chưa xác minh "Nguồn chưa xác minh" → đổi đúng chuỗi test "Chưa xác minh nguồn chi nhánh". ✅
3. `categories.html` — placeholder danh mục không thumb phải giữ đúng chuỗi class cũ `rounded border d-inline-flex align-items-center justify-content-center bg-light text-secondary small` (cộng `os-thumb-ph` cho theme). ✅
4. `/admin/stores` **body rỗng** trong `CatalogWebIntegrationTest` (mock `storeService` không stub) → `stores.isEmpty()` trên null page gây 500; fix fallback `Page.empty()` + guard `areas`. ✅ Trang thật trên 8080 hiển thị đúng, không regression hành vi thật.

## 8. Trạng thái bàn giao

- Test: **1671/1671 pass**; DB test tách riêng, seed tái tạo được từ `database/01_schema.sql` + `02_seed.sql`.
- Web thật: chạy port 8080, mọi trang key admin + staff trả 200, render đúng, 0 lỗi console.
- Ảnh/manifest: `screenshots/{guest,customer,staff,admin}` + `screenshots/manifest.json`; `README.md` mô tả cách đọc.
- Cách tái tạo ảnh: `ONESHOP_APP_URL=http://127.0.0.1:8080 node scripts/capture_full_ui.mjs` (yêu cầu Chrome headless mở `--remote-debugging-port=9222`).
