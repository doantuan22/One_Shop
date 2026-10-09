# Phase 11 – Admin toàn chuỗi

**Kết luận: PHASE 11 DONE.** Kiểm chứng ngày 09/10/2026, kết thúc clean package lúc 13:11:33 UTC+7. Không triển khai Phase 12.

## 1. Audit và phần tái sử dụng

Audit được viết trước code trong [Phase11_AdminChain_Audit.md](Phase11_AdminChain_Audit.md), baseline `cf5111e`.

| Có trước Phase 11 | Cách tái sử dụng |
| --- | --- |
| SecurityConfig/JWT/BCrypt/CSRF/SiteMesh Admin layout Phase 4/5 | Giữ route protection ROLE_ADMIN, xác thực đọc User/Role hiện tại từ DB, component/layout/form hiện có |
| AdminStoreController + StoreService | Giữ list/create/update Store, metadata Store Finder và status INACTIVE; không xây lại CRUD |
| AdminCategoryController / AdminBrandController + ProductService | Giữ CRUD, ACTIVE/HIDDEN, logo Cloudinary |
| AdminProductController + ProductService | Giữ CRUD SKU, SKU bất biến khi sửa, Cloudinary upload/primary/delete ảnh; không ProductVariant |
| AdminStoreProductController + StoreProductService | Giữ Store/Product filter, exact stock, create/update, price/status, identity Store/Product bất biến; InventoryService là stock writer duy nhất |
| OrderViewService + orders/fragments | Dùng lại snapshot OrderItem, payment receipts, timeline cho Order Detail Admin |
| User/Role/StaffStoreAssignment/Review entity + repository | Bổ sung quản trị trên đúng bảng/model đã có |
| Staff scope resolver/interceptor Phase 5/10 | Giữ nguyên. Không dùng Staff assignment predicate trong Admin reads |

Không đổi `pom.xml`, dependency, entity, schema 19 bảng hoặc SQL seed. Không thêm procurement, warehouse, transfer stock, ERP, BI, promotion, loyalty, gateway hoặc fulfillment/cancel của Admin.

## 2. Trang và endpoint

Tất cả các route dưới đây yêu cầu ROLE_ADMIN ở SecurityConfig; các service Admin mới bổ sung `@PreAuthorize("hasRole('ADMIN')")`. Form web dùng CSRF. Controller chỉ nhận request/DTO và trả ViewModel, không gọi repository trực tiếp.

| Method / endpoint | Hành vi |
| --- | --- |
| GET `/admin?storeId=` | Overview thật theo từng Store, thay dashboard mẫu |
| GET `/admin/users?role=&page=` | User toàn hệ thống, filter CUSTOMER/STAFF/ADMIN |
| GET `/admin/users/new` / POST `/admin/users` | Tạo tài khoản, password ban đầu BCrypt |
| GET `/admin/users/{id}/edit` / POST `/admin/users/{id}` | Sửa fullName/phone/role/status; email cố định sau khi tạo |
| GET `/admin/staff-assignments?storeId=&page=` | Xem phân công toàn chuỗi, gồm cả INACTIVE |
| POST `/admin/staff-assignments` | Gán / cập nhật / reactivate cặp User–Store hiện có |
| POST `/admin/staff-assignments/{id}/status` | Bỏ phân công (INACTIVE) / bật lại |
| GET `/admin/inventory?storeId=&productId=&page=` | Inventory overview reuse StoreProduct Phase 6 |
| GET `/admin/orders?storeId=&page=` | Order mọi status toàn hệ thống, filter Store trong SQL |
| GET `/admin/orders/{id}` | Snapshot, khách hàng/người nhận, Store/address, payment, timestamps và timeline |
| GET `/admin/reviews?page=` | Nội dung/rating/customer/SKU/status của Review |
| POST `/admin/reviews/{id}/status` | ACTIVE/HIDDEN, không sửa tác giả/rating/nội dung/createdAt |

CRUD Phase 6 tại `/admin/stores`, `/admin/categories`, `/admin/brands`, `/admin/products`, `/admin/store-products` giữ nguyên. Sidebar bật liên kết tới các chức năng đã hoàn thiện.

## 3. Store / Staff assignment / User

- Store metadata: code, name, address, provinceCity, area, phone, openingHours, deliveryEnabled, pickupEnabled, status được quản trị qua form Phase 6.
- Staff được tạo/sửa tại Users, có filter STAFF; form assignment liệt kê toàn bộ tài khoản STAFF, không giới hạn lựa chọn ở trang User đầu tiên.
- Assignment chỉ bật ACTIVE khi User là ACTIVE STAFF và Store ACTIVE. Một Staff có thể có nhiều assignment.
- Cặp `(user_id, store_id)` được upsert, không tạo bản ghi trùng; reactivate giữ nguyên assignment_id và assigned_at. Bỏ phân công là soft-disable, không xóa row. Chuyển Store thực hiện bỏ phân công cũ và gán Store mới.
- Mutation assignment và đổi role khóa cùng User row để serialize. Rời role STAFF sẽ tắt assignment cũ trong cùng transaction; đổi lại STAFF không tự phục hồi assignment.
- User INACTIVE, role không còn STAFF hoặc assignment INACTIVE làm JWT cũ mất quyền Staff ngay qua cơ chế đọc DB hiện có. Browser Store cookie/query không mở rộng scope.
- User quản trị gồm ba role đã có, không có role/permission model mới. Không expose password/hash/token trong DTO/View. Email giữ nguyên sau creation; không đổi password bằng form update; không cho tự tắt Admin hoặc tự bỏ quyền Admin đang dùng.

## 4. Inventory / Order / overview

Inventory overview dùng lại query/DTO/template StoreProduct: Store name/id, product SKU/name, price, **exact quantity**, status. Filter Store/Product ở SQL, hiển thị mọi trạng thái, kể cả Store INACTIVE; link sửa trỏ về CRUD Phase 6. Không có inventory table/writer mới. Quantity mutation vẫn qua InventoryService và InventoryMovement hiện có.

Order list không áp assignment Staff, không lọc Store ACTIVE. Filter và total nằm trong SQL; sort `createdAt DESC, id DESC`, 20 rows/trang. Detail gọi OrderViewService trong transaction đọc, không tính lại giá từ catalog. Code pickup không hiện trong Admin reader; history note có code seed được che như reader Staff. Trang Admin không có nút thực hiện fulfillment hoặc cancel.

User/Assignment/Review lists cũng phân trang 20 rows với sort ổn định; link phân trang giữ filter. Unknown Store filter của list trả rỗng, không fallback sang toàn chuỗi.

Overview dùng COUNT theo Store từ dữ liệu hiện có: tổng Order, cần xử lý (CONFIRMED/PREPARING/PACKED/SHIPPING/READY_FOR_PICKUP), hoàn tất, hủy, số StoreProduct mọi status, SKU ACTIVE còn 1–5/hết 0 và Staff có quyền hiệu lực. Store INACTIVE vẫn có overview/lịch sử nhưng số Staff có quyền hiệu lực bằng 0. Có link tới Orders/inventory/assignment cùng Store. Không BI/revenue aggregation mới.

## 5. File thêm / sửa

File thêm:

- `docs/Phase11_AdminChain_Audit.md`, `docs/Phase11_AdminChain_Report.md`.
- `controller/admin/`: `AdminInventoryController.java`, `AdminManagementExceptionHandler.java`, `AdminOrderController.java`, `AdminReviewController.java`, `AdminStaffAssignmentController.java`, `AdminUserController.java`.
- `service/`: `AdminOperationsService.java`, `AdminReviewService.java`, `AdminStaffAssignmentService.java`, `AdminUserService.java`.
- `dto/request/`: `AdminUserRequest.java`, `StaffAssignmentRequest.java`.
- `dto/response/`: `AdminOrderDetailResponse.java`, `AdminOrderSummaryResponse.java`, `AdminReviewResponse.java`, `AdminStoreOverviewResponse.java`, `AdminUserResponse.java`, `StaffAssignmentResponse.java`.
- `templates/admin/`: `fragments.html`, `order-detail.html`, `orders.html`, `reviews.html`, `staff-assignments.html`, `user-form.html`, `users.html`.
- `src/test/java/com/oneshop/Phase11AdminDatabaseIntegrationTest.java`.

File sửa:

- `README.md`.
- `controller/admin/AdminDashboardController.java`.
- `repository/`: `OrderRepository.java`, `ReviewRepository.java`, `StaffStoreAssignmentRepository.java`, `StoreProductRepository.java`, `UserRepository.java` (bổ sung query; không đổi Staff predicates cũ).
- `templates/admin/dashboard.html`, `templates/admin/store-products.html`, `templates/fragments/admin.html`.
- `src/test/java/com/oneshop/AbstractIntegrationTest.java`: mock Admin reader trong bộ layout test không dùng DB.
- `src/test/java/com/oneshop/LayoutsIntegrationTest.java`: thay assertion dành cho dashboard mẫu bằng assertion cho overview hiện tại, giữ kiểm tra layout/auth/responsive.
- `src/test/java/com/oneshop/Phase10IntegrationWebDatabaseTest.java`: sửa riêng assertion timestamp TC-13 thành biên trên inclusive để phản ánh DATETIME2(0) làm tròn; toàn bộ kiểm tra audit/scope/rollback còn nguyên.

Đường dẫn package Java ở trên tương đối với `src/main/java/com/oneshop/`; đường dẫn template tương đối với `src/main/resources/`.

## 6. Kiểm thử và TC-17 / TC-18

`Phase11AdminDatabaseIntegrationTest` chạy **bắt buộc** SQL Server/seed thật, Tomcat/JWT/CSRF/controllers/services/JPA production, không mock nghiệp vụ. Fixture cleanup dùng SeedGuard và snapshot, chỉ xóa các row do test tạo; khôi phục original columns/IDs/timestamps, không reseed IDENTITY. Kiểm exact cleanup sau mỗi test.

| Yêu cầu | Minh chứng |
| --- | --- |
| CUSTOMER/STAFF bị chặn | 11 trang Admin × hai role, guest redirect; POST mutation bị 403 và không đổi business rows; service method security khi gọi trực tiếp |
| Store + assignment | Create/update Store Finder metadata, ACTIVE/INACTIVE; gán/bỏ/reactivate, giữ ID/time, nhiều Store và đổi phân công |
| Scope hiện tại | Cùng JWT Staff trước/sau thu hồi và bật assignment; role/status change mất quyền ngay; cookie/query Store không cấp quyền |
| Exact stock / Store filter | Đối chiếu HTML IDs/count/quantity với SQL ở Store 1/2/4/5; gồm INACTIVE Store; unknown filter trả rỗng |
| Orders toàn chuỗi | SQL IDs/count theo mọi Store, snapshot/payment/history detail, missing detail 404; không có child data từ Store khác |
| Pagination | Tạo hơn 20 Orders/StoreProducts/Users/Assignments/Reviews; đối chiếu Order page IDs, total, stable order, links giữ Store/role filter |
| User/Review | BCrypt, không render password/hash, invalid/duplicate/enum không ghi, role/status revoke, email/self-admin guards; review visibility-only và HTML escaping |
| Overview | Đối chiếu từng metric với SQL độc lập, cả Store INACTIVE, filter một Store |
| TC-18 | Checkout production nhiều Store tạo lịch sử; Admin disable Store; checkout mới/cart add bị 400, không có stock/order/audit mutation; Admin/Customer vẫn xem lịch sử |
| Không hard-delete | Disable Product HIDDEN, Store/StoreProduct INACTIVE có lịch sử; row/FK/snapshot/payment/history vẫn nguyên; không có endpoint delete các resource đó |

**TC-17 PASS theo kiểm chứng tái sử dụng Phase 6:** `CatalogDatabaseIntegrationTest.tc17_productImageGoesToCloudinaryAndTheDatabaseKeepsOnlyUrlAndPublicId` và `tc17_brandLogoIsStoredAsUrlAndPublicIdAndTheOldFileIsRemoved`. SQL Server thật kiểm URL/public_id, primary/delete/replacement và không có cột binary/image; external Cloudinary adapter được mock để không tạo/xóa asset thật. `CloudinaryServiceTest` kiểm cấu hình provider và guard.

**Giới hạn Cloudinary:** không upload thật ra dịch vụ trong regression. Một test cũ `CatalogHttpDatabaseIntegrationTest.imageUploadReachesCloudinaryServiceThroughTheMultipartFormWithCsrf` chỉ dành cho nhánh Cloudinary **chưa cấu hình** đã skip theo assumption vì môi trường hiện tại **đã cấu hình** Cloudinary. Đây là conditional skip cũ, không phải test Phase 11 bị bỏ qua. Production image code không thay đổi.

## 7. Kết quả build

| Lượt kiểm chứng | Tests | PASS | FAIL | ERROR | SKIP | Kết quả |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| LayoutsIntegrationTest focused | 9 | 9 | 0 | 0 | 0 | BUILD SUCCESS |
| Phase11AdminDatabaseIntegrationTest focused | 30 | 30 | 0 | 0 | 0 | BUILD SUCCESS |
| Clean package cuối cùng, 43 suites | 894 | 893 | 0 | 0 | 1 | BUILD SUCCESS |

Full regression gồm 864 test baseline Phase 1–10 và 30 test Phase 11. Phase 10 vẫn PASS 117/117. Artifact `target/oneshop.jar` được Spring Boot repackage thành công.

Command kiểm chứng:

```powershell
mvn -o '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' '-Dtest=LayoutsIntegrationTest' test
mvn -o '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' '-Dtest=Phase11AdminDatabaseIntegrationTest' test
mvn -o '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' clean package
```

Offline/cache override chỉ phục vụ môi trường thực thi, không đổi project config. Build clean trong sandbox gặp Java ZipFS `AccessDeniedException` khi đóng dependency JAR; thử cache ở temp vẫn gặp cùng lỗi. Clean package được chạy ngoài sandbox sau auto-review approval.

Lượt regression đầu: 894 tests, 1 failure ở TC-13 timestamp Phase 10, 0 errors, 1 skip. SQL DATETIME2(0) làm tròn created_at tới đúng mốc `finished`; AssertJ `isBetween` loại trừ mốc này. Đã sửa assertion thành `isAfterOrEqualTo(started).isBeforeOrEqualTo(finished)` cho đúng precision window, không đổi nghiệp vụ. Lượt clean package sau sửa PASS như bảng trên.

Logs local (gitignored): `target-phase11-focused.log`, `target-phase11-layout.log`, `target-phase11-regression-initial.log`, `target-phase11-regression.log`. Surefire XML/TXT ở `target/surefire-reports/`.

**PHASE 11 DONE. Dừng trước Phase 12.**
