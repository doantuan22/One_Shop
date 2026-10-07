# Phase 10.4 – Store Inventory Operations

Trạng thái: **PHASE 10.4 DONE**, kiểm chứng ngày 06/10/2026. Dừng trước Phase 10.5.

## Endpoint / page

| Endpoint | Hành vi |
| --- | --- |
| `GET /staff/stock?page=0` | Stock List mọi status thuộc assigned Stores, SKU/Product/giá/status/exact quantity, 20 rows/trang. |
| `GET /staff/stock/{id}/adjust` | Form tồn thực tế và lý do của StoreProduct trong scope. |
| `POST /staff/stock/{id}/adjust` | Điều chỉnh tồn qua InventoryService; thành công redirect sang history của resource. |
| `GET /staff/inventory-history?page=0` | Chọn StoreProduct trong assigned Stores để xem history. |
| `GET /staff/inventory-history/{id}?page=0` | Type/before/after/change/staff/note/time, 20 movements/trang theo StoreProduct. |

Thymeleaf/Bootstrap/SiteMesh Staff layout/sidebar được tái sử dụng; Stock/Inventory History được bật vì page đã triển khai.
History có nullable actor của ORDER/CANCEL_ORDER hiển thị Hệ thống. Note được escape khi render.
Staff routes chỉ STAFF; Admin tiếp tục thấy exact stock qua màn hình Admin đã có, không thêm Admin feature.

## Store scope và privacy

`StaffInventoryService` tái sử dụng nguyên trạng `StaffStoreScopeService` 10.1:
SecurityContext → authenticated ACTIVE STAFF → ACTIVE StaffStoreAssignment → ACTIVE Stores.
Không nhận staff email/userId/store_id từ request làm nguồn quyền. Giữ nhiều ACTIVE assignments; revocation resolve lại từ DB.

Stock list dùng `StoreProductRepository.findByStoreIdInOrderByStoreNameAscProductNameAscIdAsc` với assigned ids.
Data/total được scoped trước pagination trong SQL, fetch Store/Product; mọi status có thể được Staff kiểm đếm.
Tái sử dụng `StoreProductMapper.toStockView` và `StoreProductStockResponse` chứa exact quantity.
Client mapping/DTO hiện có không thay đổi; Guest/Customer bị chặn khỏi Staff stock/history/form.

Form/history đọc qua `scope.requireStoreProduct(id)`. Cross-Store/missing cùng trả 404 trước khi trả resource/history.
History repository còn có predicate StoreProduct id **và** actual Store id IN assigned ids, fetch nullable Staff,
sort createdAt DESC/movement id DESC để phân trang ổn định, không N+1 theo actor.

Mutation xác thực scope trước resource lookup, khóa StoreProduct theo PK, kiểm actual Store bằng `requireAssignedStore`
trước khi gọi writer. Cross-Store mutation 403; missing 404; không ACTIVE assignment 403/Service AccessDenied.
Form invalid của resource khác Store vẫn không trả stock data. Query/form/cookie Store/actor/price/status không cấp quyền.

## Stock adjustment, audit, transaction và locking

Reused `StockAdjustRequest`: newQuantity required và >= 0; note required, tối đa 500 ký tự.
Controller dùng Bean Validation/binding, Service còn có method validation cho direct calls; business writer vẫn là
`InventoryService.adjustStock`. Invalid HTTP input trả form 400, không ghi dữ liệu.

Write method `StaffInventoryService.adjust` dùng `@Transactional`; `InventoryService.adjustStock` joins cùng REQUIRED
transaction. `findByIdForUpdate` dùng PESSIMISTIC_WRITE có sẵn (SQL Server UPDLOCK/HOLDLOCK/ROWLOCK), giữ lock tới commit.
Writer lấy quantity_before từ managed locked row, cập nhật quantity_after, tính quantity_change và ghi một STOCK_ADJUST:
actual StoreProduct, authenticated staff_id, note trim theo helper hiện có, created_at từ JPA auditing, reference_order_id null.
Stock dirty checking/updated_at và INSERT movement commit hoặc rollback cùng nhau; không đổi price/status.

Giữ quy tắc writer/DB CHECK hiện có: quantity không đổi là **no-op**, không tạo zero-change STOCK_ADJUST.
Mỗi thay đổi thực sự tạo đúng một movement. UI giải thích và thông báo riêng khi số lượng không đổi.
CSRF bắt buộc cho Stock forms cả cookie/Bearer/percent-encoded paths; không thêm auth scheme.

Không sửa core `InventoryServiceImpl`, Checkout inventory flow, fulfillment/pickup 10.3, entity hoặc schema.
Không thêm purchase/transfer/WMS/supplier/Admin hoặc nghiệp vụ ngoài roadmap.

## File thêm / sửa trong 10.4

Java dưới `src/main/java/com/oneshop/`; template dưới `src/main/resources/templates/`.
Các thay đổi 10.3 có sẵn trong working tree được giữ, không tính là thay đổi mới của 10.4.

| Loại | File |
| --- | --- |
| Thêm Service | `service/StaffInventoryService.java` |
| Thêm Controller | `controller/staff/StaffStockController.java` |
| Thêm DTO | `dto/response/InventoryMovementResponse.java`, `dto/response/StaffInventoryHistoryResponse.java` |
| Thêm template | `staff/stock/index.html`, `staff/stock/adjust.html`, `staff/stock/history.html` |
| Thêm test | `src/test/java/com/oneshop/StaffInventoryDatabaseIntegrationTest.java` |
| Thêm report | `docs/Phase10_4_StoreInventoryOperations_Report.md` |
| Sửa query | `repository/StoreProductRepository.java`, `repository/InventoryMovementRepository.java` (thêm query Staff; giữ query/lock cũ) |
| Sửa CSRF coverage | `config/SecurityConfig.java` (thêm Staff Stock form matcher, không đổi Auth/JWT/roles) |
| Sửa navigation | `fragments/staff.html` (bật hai page inventory đã triển khai) |
| Sửa test fixture | `src/test/java/com/oneshop/AbstractIntegrationTest.java` (mock read model cho layout/auth tests không dùng DB) |
| Sửa test menu | `src/test/java/com/oneshop/LayoutsIntegrationTest.java` (thay assertion menu chưa làm/disabled bằng enabled Stock/History; vẫn kiểm active item) |
| Sửa Javadoc | `service/InventoryService.java` (mô tả caller Staff hiện tại, không đổi API/logic) |
| Sửa tài liệu | `README.md` |

## Kiểm thử

29 test mới dùng Tomcat/JWT/CSRF/SiteMesh/Service/Repository/SQL Server thật.
Repository spy mặc định delegate DB thật; chỉ dùng để chèn lỗi ở hai stage và quan sát actual SQL trước rollback,
không mock stock/movement/business rule hoặc quyền. Probe persist/flush bằng EntityManager như các test Phase 9.

| Yêu cầu bắt buộc | Kết quả |
| --- | --- |
| 1. Stock list chỉ Store A | PASS: exact ids/total/order, Store B query/cookie không mở rộng scope; fixtures hơn 20 rows mỗi Store kiểm hai trang/all status. |
| 2. Exact quantity của assigned Store | PASS: DTO/HTML đối chiếu DB; Guest/Customer không đọc Staff stock; Admin existing page vẫn thấy exact quantity. |
| 3. Chỉnh quantity hợp lệ | PASS: 0, 3 và Integer.MAX_VALUE; quantity SQL đúng, không đổi price/status/Store/Product. |
| 4. Một STOCK_ADJUST mỗi thay đổi | PASS: tổng movement tăng đúng một; no-op không ghi zero-change audit. |
| 5. Audit fields đúng | PASS: before/after/change/authenticated staff_id/trimmed note/created_at/reference null; giả mạo actor/before/change/price/status không có hiệu lực. |
| 6. Quantity âm bị từ chối | PASS: âm/blank/sai kiểu/overflow/phân số HTTP 400 và exact snapshot không đổi; direct Service validation cũng từ chối. |
| 7. Cross-Store adjustment | PASS: 403, không write, không tin store_id/staffEmail của request; missing resource 404. |
| 8. Cross-Store history | PASS: form/history đều 404 trước resource data; không current quantity hoặc movement rows. |
| 9. Stock/movement error không lệch dữ liệu | PASS: inject sau UPDATE stock SQL và sau INSERT movement SQL; direct Service lẫn HTTP 409 rollback exact; outer transaction rollback và race serial ledger PASS. |
| 10. Regression Phase 1–10.3 | PASS: 852 tests / 0 failure / 0 error / 1 conditional Cloudinary skip có sẵn, BUILD SUCCESS. |

Còn kiểm history cả ORDER/CANCEL_ORDER/STOCK_ADJUST và actor Hệ thống, history pagination/total đúng SP,
required/max note, HTML escaping, cookie/Bearer/encoded-path CSRF, Customer/Admin route denial,
không ACTIVE assignment, nhiều assignment và revoke Store B chặn ngay read/write với JWT cũ.

Fixture cleanup sau mỗi test so sánh exact rows/fields/timestamps chín bảng business,
User metadata/Store/assignments và products; temporary Product/StoreProduct pagination fixtures được xóa trong cleanup.
Không reseed IDENTITY. Cleanup PASS.

Lệnh đã chạy:

```powershell
mvn -o '-Dmaven.repo.local=C:\Users\Admin\.m2\repository' '-Dtest=StaffInventoryDatabaseIntegrationTest,LayoutsIntegrationTest,AccessControlIntegrationTest' test
mvn -o '-Dmaven.repo.local=C:\Users\Admin\.m2\repository' package
```

Focused lần đầu: 57/58 PASS, một fault probe dùng callRealMethod không được hỗ trợ trên Spring Data interface spy.
Đã sửa **test probe** sang real EntityManager persist/flush, giữ assertions rollback; production không sửa vì lỗi probe này.
Final full suite/package: **852 tests, 851 passed, 1 skipped, 0 failures/errors**, 41 suites, **BUILD SUCCESS**.
**29/29 test mới**, **36/36 test 10.3**, **23/23 test 10.2**, **17/17 test 10.1** PASS.
Skip là Cloudinary unconfigured-case có `assumeFalse(isConfigured)` để tránh upload thật khi đã có cấu hình;
không có test Inventory/scope bị skip. JAR: `target/oneshop.jar`.

Evidence local: `target-phase10_4-focused.log`, `target-phase10_4-regression.log`, Surefire XML.
Không chạy standalone verifiers cũ; integration tests dùng embedded Tomcat thật và SQL Server thật.
Không commit, không tự chuyển Phase 10.5.
