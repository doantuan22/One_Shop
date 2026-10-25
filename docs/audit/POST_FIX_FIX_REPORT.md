# FIX REPORT

Mọi thay đổi chỉ trên test, fixture, script kiểm thử, README và một dòng chú thích cấu hình. **Không đổi business rule, entity, schema hay giao diện.**

## ENV-001 / BUG-003 — Schema test cũ (`order_items.discount_amount`)
- Root cause: DB `oneshop_v3_verify_test` được tạo từ schema cũ. `database/01_schema.sql` hiện hành có cột `discount_amount DECIMAL(18,2) NOT NULL DEFAULT 0` + `CK_order_items_discount`, Entity khớp schema → không sửa Entity.
- Không ALTER/DROP DB có sẵn. Tạo DB mới, rỗng, đúng quy trình: `01_schema.sql` (script tự từ chối DB đã tồn tại) + `06_password_reset_otps.sql`. Hibernate `ddl-auto=validate` OK, context khởi tạo được. 30 bảng.
- Kết quả: ERROR từ 977 (+1 FAIL) → context khởi tạo bình thường.

## FIX-1 Seed không khớp bộ test (nguyên nhân của ~1.470 lỗi sau ENV-001)
- Root cause: `database/02_seed.sql` ở HEAD (commit f7c3bb3) là seed demo sản phẩm thật (5 chi nhánh, 12 tài khoản, 0 đơn). Bộ test viết cho seed kịch bản (6 chi nhánh gồm OS-HAICHAU INACTIVE, OS-HOANKIEM chỉ giao hàng, OS-QUAN10 chỉ nhận tại cửa hàng, 8 đơn mẫu).
- Fix: giữ nguyên seed demo; khôi phục seed kịch bản từ commit d25888b thành `database/test-fixtures/02_test_fixture_seed.sql` (email `@oneshop.vn` → `@example.com` cho khớp việc đổi email test đang dở trong working tree). README ghi quy trình dựng DB test.
- Số lỗi: 1592 → 125 (seed demo) → 51 → 33 (sau sửa encoding & fixture).
## FIX-2 Mojibake khi nạp seed
- Root cause (lỗi của chính lượt chạy đầu): `sqlcmd` thiếu `-f 65001` đọc file UTF-8 như cp1252 (`Thá»§ Äá»©c`). Dựng lại DB với `-f 65001`; ghi vào README.
## FIX-3 Test lỗi thời do UI/thiết kế đã đổi có chủ đích (không sửa production code)
| Test | Nguyên nhân | Sửa |
|---|---|---|
| CategoryAdminSecurityWebIntegrationTest (**BUG-004**) | UI đổi `Danh mục dùng chung toàn chuỗi` → `Danh mục` (commit da632bd); không có đặc tả duyệt giữ cụm cũ. Gợi ý upload đổi thành `JPEG, PNG hoặc WebP · tối đa 5MB.` | Assert `<h1>Danh mục</h1>`, nút `Thêm danh mục`, `tối đa 5MB`; giữ nguyên toàn bộ assertion hành vi (bảng, ảnh, input file, accept, enctype). Không đổi template. |
| CatalogHttp: imageUpload… | Thông báo lỗi đổi sang `Không thể tải ảnh…` | Cập nhật chuỗi |
| CatalogHttp: tc18, aStoreCookie…, searchAndFilters… | `/stores` nay có form `Xem sản phẩm tại cửa hàng` (POST `/stores/select`) cho từng chi nhánh ACTIVE; thẻ sản phẩm không in SKU | Đếm form = 5 chi nhánh ACTIVE (chi nhánh INACTIVE không có form); đếm `<article class="pcard">` thay vì tìm SKU |
| CheckoutV3Http codSplit | Câu chữ `Phí giao hàng:` / `Thanh toán khi nhận:` / `đơn hàng độc lập` | Cập nhật chuỗi |
| FulfillmentHttp be04_65 | Đơn 1 chi nhánh hiển thị tiêu đề `Giao nhận` (nhiều chi nhánh mới là `Phần giao theo chi nhánh`, test be04_66 vẫn xác nhận) | Cập nhật chuỗi |
| CatalogDatabase tc17 ×2 + cloudinaryRefusingADelete… | Thiết kế mới (`ImageTransactionCoordinator`): chỉ xóa file Cloudinary **sau commit**, task xếp hàng trong cùng transaction; test class `@Transactional` không bao giờ commit | Assert `image_cleanup_tasks` có 1 task và `deleteImage` chưa gọi; lỗi Cloudinary không chặn xóa row. Nhánh commit/lỗi vẫn do `QaFixImageLifecycleDatabaseIntegrationTest` bao phủ (PASS) |
| DatabaseFoundation / JpaV3ReadIntegrity / DatabaseV3Regression | Thêm entity/bảng `password_reset_otps` → 29 entity, 30 bảng | 28→29, 29→30 |
| DatabaseV3Regression migration | File `docs/v3/migration-history/20261014_be06_review_uniqueness.sql` bị xóa ở f7c3bb3 | Khôi phục từ git vào `src/test/resources/db/` |
| QaAuditRoute `/forgot-password` | Route công khai mới (như `/login`) | Thêm vào tập route công khai (200) |
| Phase11 userCannotChange… | Sót một chuỗi `admin%40oneshop.vn` khi đổi email | Đổi sang `example.com` |
| QaFixUiBrowser ×2 | `scripts/qa-audit/fix-ui.mjs` bị xóa ở f7c3bb3 và còn email cũ | Khôi phục từ git, đổi email |
| BrowserV3 `FAILED` | Script ném `BLOCKED` cố định | Thực hiện kịch bản FAILED qua endpoint thật `POST /orders/{id}/payments/{pid}/simulate` bằng session + CSRF của khách trong Chrome; xác nhận đơn CANCELLED, hoàn kho đúng 1 lần |
## BUG-001 / BUG-002 (xác minh)
- Thêm `PaymentProductionRuntimeConfigDatabaseIntegrationTest`: context thật profile `prod` trên DB `*_test` → mock gateway tắt, ONLINE checkout tắt, bean `PaymentTimeoutWorker` không tồn tại. PASS.
- Sửa chú thích sai trong `application.properties` ("ON by default" → OFF mặc định, bật bằng `ONESHOP_PAYMENT_TIMEOUT_WORKER_ENABLED=true`). Giá trị không đổi.
- Worker/timeout/hoàn kho một lần/idempotent: `PaymentSettlement` (37), `PaymentConcurrency` (12), `PaymentGatewayDisabled`, `PaymentHttp` (8) PASS trên DB cô lập.

## File thay đổi
Test: CategoryAdminSecurityWebIntegrationTest, CatalogHttpDatabaseIntegrationTest, CatalogDatabaseIntegrationTest, CheckoutV3HttpDatabaseIntegrationTest, FulfillmentHttpDatabaseIntegrationTest, DatabaseV3RegressionIntegrationTest, DatabaseFoundationIntegrationTest, JpaV3ReadIntegrityTest, QaAuditRouteDatabaseIntegrationTest, Phase11AdminDatabaseIntegrationTest, `src/test/browser/be07-e2e.mjs`; mới: PaymentProductionRuntimeConfigDatabaseIntegrationTest.
Hỗ trợ: `database/test-fixtures/02_test_fixture_seed.sql`, `src/test/resources/db/20261014_be06_review_uniqueness.sql`, `scripts/qa-audit/fix-ui.mjs`, `scripts/qa-audit/responsive-fixture.mjs`, `README.md`.
Main: chỉ chú thích trong `application.properties`.
Đã có sẵn (chưa commit, từ phiên trước, giữ nguyên): đổi email test sang `@example.com`, `application-prod.properties` (mock gateway/ONLINE tắt), worker mặc định `false`, `PaymentProductionDefaultsTest`.

## FIX-4 (lượt 2) Test đa phân công → nghiệp vụ "1 nhân viên – 1 chi nhánh"
Quy tắc: `UX_ssa_one_active_store_per_user` + `AdminStaffAssignmentService` (từ chối phân công ACTIVE thứ hai; dùng "Chuyển chi nhánh"). Thêm helper `transfer(email, store)` trong `ReviewV3TestSupport` (tạm ngưng phân công hiện tại, mở phân công mới; lớp nền tự khôi phục).
| Test cũ | Test mới |
|---|---|
| Delivery…readScopeSupportsMultipleAssignments… | …readScopeFollowsTheStaffsCurrentStoreAndOnlyDeliverySnapshots (trước chuyển: bị từ chối; sau chuyển: đọc được) |
| FulfillmentOperations…be04_08_twoAssignments | …be04_08_transferMovesTheWholeScope (DB từ chối ACTIVE thứ hai; sau chuyển chỉ thấy Store mới) |
| Phase10…revokingOneOfMultipleAssignments… | …revokingTheOnlyAssignmentImmediatelyRemovesReadsAndWritesAcrossAllModules |
| Phase9…multipleAssignmentsAllowOnlyActiveStore… | …transferredStaffActsOnlyAtTheNewStoreAndUsesAuthenticatedActor |
| Phase11…multipleAssignmentsAndStoreChange… | …aStaffWorksAtOneStoreAndTheBrowserSelectorIsNotTrusted + **mới** …adminTransferMovesTheStaffToTheOtherStoreInOneStep (POST `/admin/staff-assignments/transfer` thật) |
| ReviewV3Concurrency…twoStaffReplies… | hai Staff cùng một Store (Staff Gò Vấp được chuyển sang Thủ Đức sau khi hoàn tất phần giao hàng của mình) |
| ReviewV3…multipleStoreAssignments… | …staffRepliesOnlyForTheStoreTheyCurrentlyWorkAt |
| StaffFulfillmentOperations…multipleAssignments… | …transferredStaffMayPrepareAtTheNewStoreThenRevocationBlocks… |
| StaffInventory / StaffOperations / StaffStoreScope …multiple… | …transferred… (Store cũ bị từ chối, Store mới được phép, thu hồi → không còn Store, `AccessDenied`/403) |
Test cũ còn dùng nghiệp vụ cũ: `CatalogHttp…tc02_aStoreCookieNoLongerSwitchesTheCatalog` có tên trái với nội dung → đổi tên `tc02_aSelectedStoreScopesTheCatalogWithAWayBackToTheChain`. Đã rà soát toàn bộ test (grep multi-assignment, unit test mock): không còn test nào dựng nhiều phân công ACTIVE.
## FIX-5 Rò session SQL giữa các test
Root cause: `DatabaseV3RegressionIntegrationTest.approvedReviewMigration…` chạy script migration có `SET NOCOUNT ON; SET XACT_ABORT ON …` trên connection của pool và không hoàn trả → test chạy sau trên connection đó nhận update count sai. Fix: chạy script trong `ConnectionCallback` và `SET NOCOUNT OFF; SET XACT_ABORT OFF` trong `finally`. Bằng chứng: `fix-run18-bisect.log` (tái hiện) → `fix-run19-bisect.log` (PASS) → `fix-run20-final-fixture.log`.
