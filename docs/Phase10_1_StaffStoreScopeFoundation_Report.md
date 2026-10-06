# Phase 10.1 – Staff Store Scope Foundation

Trạng thái: **PHASE 10.1 DONE**, kiểm chứng ngày 06/10/2026.
Phạm vi chỉ 10.1, theo Roadmap V2 BR-14/mục 6.8 và request hiện tại.

Đã đọc cấu trúc Auth/JWT/SecurityContext, User/StaffStoreAssignment, StoreService, interceptor,
Order/StoreProduct/Inventory services và repositories, các controller Staff và báo cáo Phase 9.
Baseline commit `e27a701` (Phase 9 hoàn tất), working tree sạch, 747 tests/746 PASS/1 skip Cloudinary cũ.

## File thêm/sửa

Thêm:

* `src/main/java/com/oneshop/service/StaffStoreScopeService.java` – foundation dùng chung lấy identity từ SecurityContext,
  resolve assigned Stores và lookup resource có scope.
* `src/test/java/com/oneshop/StaffStoreScopeDatabaseIntegrationTest.java` – 17 test Service/SQL Server/JWT/HTTP thật.
* Báo cáo này.

Sửa:

* `src/main/java/com/oneshop/repository/StoreProductRepository.java` – thêm `findByIdAndStoreIdIn`, fetch Store/Product.
* `src/main/java/com/oneshop/service/InventoryService.java` – chỉ bổ sung Javadoc về sử dụng scope/lock cho Staff caller.
* `README.md` – phase hiện tại, API foundation và ranh giới.

Không xóa file, sửa schema/config/Auth/JWT, refactor Delivery/Pickup hoặc thay assertions test cũ.

## Resolve assigned Store

`StaffStoreScopeService` kiểm Authentication đã xác thực có `ROLE_STAFF`, lấy email từ `SecurityContextHolder`.
Không nhận staffEmail/userId từ Controller/form/body. Tái sử dụng:

`StoreService.getAssignedStores(auth.getName())`
→ `StaffStoreAssignmentRepository.findActiveStoresByStaffEmail`.

Query kiểm User ACTIVE/STAFF, assignment ACTIVE và Store ACTIVE. Không có scope hợp lệ ném AccessDenied (403 ở HTTP).
User INACTIVE bị JWT layer từ chối, API unauthenticated trả 401. Quyền đọc lại từ DB mỗi call, không cache vào JWT/request.

Giữ multiple assignments đã có: scope là tất cả assigned Store ids; không chọn tùy ý Store đầu tiên hoặc thêm Store selector.
`requireAssignedStore(storeId)` chỉ kiểm resource Store/requested subset thuộc tập này; id request không cấp assignment mới.

## Backend enforce scope

* Request: SecurityConfig giữ STAFF-only `/staff/**`, `/api/staff/**`. StaffStoreScopeInterceptor hiện hữu đọc assignments
  mỗi request và từ chối data routes nếu scope rỗng. `/staff` landing vẫn chỉ thông báo chưa phân công như baseline.
* Service: `getAssignedStores/getAssignedStoreIds` lấy Staff từ SecurityContext; `requireAssignedStore` kiểm Store thuộc scope.
* Order: `requireOrder` dùng query hiện hữu `OrderRepository.findByIdAndStoreIdIn(orderId, assignedIds)`.
  Delivery/Pickup đã kiểm scope theo principal ở mọi read/action, giữ nguyên đầy đủ.
* StoreProduct: `requireStoreProduct` dùng `StoreProductRepository.findByIdAndStoreIdIn(id, assignedIds)`.
  Entity ngoài scope/unknown đều ResourceNotFound (404); không load unscoped rồi cho phép client chọn Store.
* Inventory: scope theo Store thật của StoreProduct, tái sử dụng `requireStoreProduct` cho đọc resource.
  Caller mutation trong phase sau phải giữ lock và gọi `requireAssignedStore(lockedResource.store.id)` trong cùng transaction.
  Phase này không thêm Staff stock mutation, inventory history hoặc endpoint/UI Inventory; primitive Admin/checkout hiện hữu giữ nguyên.

Foundation trả entity nội bộ cho Service caller, không serialize entity ra Client. Các resource id là mục tiêu truy cập;
assigned Store ids truyền xuống repository chỉ lấy từ backend. Store id từ request có thể bị bỏ qua hoặc bị kiểm như subset,
không được dùng để mở rộng quyền.

## Kết quả kiểm thử bắt buộc

| Yêu cầu | Kiểm chứng |
|---|---|
| 1. Assignment ACTIVE resolve đúng Store | Staff Thủ Đức → Store 1, Gò Vấp → Store 2; nhiều ACTIVE Store cũng đúng |
| 2. Không có assignment ACTIVE → từ chối | Không có valid scope, assignment/Store/User INACTIVE, role DB thay đổi đều bị chặn; cùng JWT cũ không giữ quyền |
| 3. Staff A truy cập Store A | Order 1 và StoreProduct Store 1 được phép qua Service; HTTP read own resource 200 |
| 4. Order/StoreProduct Store B bị chặn | Service ResourceNotFound; Order production URL và StoreProduct HTTP adapter 404; requested Store ngoài scope 403 |
| 5. Thay store_id không vượt scope | Query/form/JSON/path tampering đều bị chặn hoặc bỏ qua; own resource vẫn Store 1, peer resource không trả dữ liệu |

HTTP Order sử dụng controller production hiện hữu. HTTP StoreProduct dùng adapter `@TestConfiguration` chỉ import vào
test context để kiểm Security → Context → Service → Repository → SQL Server; không thêm production route hoặc Stock UI.
Adapter có mapping ResourceNotFound → 404 vì API advice hiện hữu chỉ áp dụng package controller.api; không đổi error handler production.

17 tests mới gồm resolve (1), invalid scope/account (5), unauthenticated (1), wrong context role (2), own resource (1),
cross/unknown/null resource (1), multiple assignments/revocation (1), bốn request sources (4), production Order URLs (1).
Không mock repository, transaction hoặc JWT/Store scope trong suite mới, dùng SQL Server dev thật/schema validate.
Fixtures status/role/assignment khôi phục finally; so sánh exact users metadata/stores/assignments và chín bảng business sau mỗi test.

**17/17 test mới PASS** trên SQL Server thật, đủ năm yêu cầu bắt buộc.
Full regression/package: **764 tests / 38 suites: 763 PASS, 0 FAIL, 0 ERROR, 1 SKIP Cloudinary cũ**.
Baseline 747 test giữ nguyên; skip vẫn test upload Cloudinary đã cấu hình, không SQL/scope test nào bị skip.
**BUILD SUCCESS**, `git diff --check` PASS. JAR chứa foundation service và không chứa HTTP adapter/test configuration.
Evidence local: `target-phase10_1-regression.log`, Surefire XML. Fixtures exact cleanup PASS sau từng test;
không reset/reseed DB. Không khởi chạy app standalone hoặc để app thử chạy nền trong phase này.

Không có Dashboard, Order/Pickup queue, state transition mới, Stock Adjustment, Inventory History hoặc Admin feature mới.
Không tự chuyển sang Phase 10.2.
