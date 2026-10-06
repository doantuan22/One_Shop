# Phase 10.2 – Staff Dashboard + Order Operations

Trạng thái: **PHASE 10.2 DONE**, kiểm chứng ngày 06/10/2026. Dừng trước Phase 10.3.

## Endpoint / page

| GET endpoint | Kết quả |
| --- | --- |
| `/staff`, `/staff/` | Dashboard thật nếu có assignment; giữ landing chỉ thông báo chưa phân công, không trả Store data khi không có scope. |
| `/staff/dashboard` | Dashboard cần ACTIVE assignment, không có scope trả 403. |
| `/staff/orders?page=0` | Queue mọi fulfillment/status thuộc scope, 20 đơn/trang. |
| `/staff/orders/{orderId}` | Chi tiết chỉ đọc của Order trong scope; khác Store hoặc không tồn tại cùng trả 404. |

Queue có mã đơn, Store, khách hàng, fulfillment, payment status, order status, tổng tiền và thời gian tạo.
Detail có Store/address, khách hàng/người nhận/điện thoại/địa chỉ giao, snapshot OrderItem, payment/method/status,
pickup timestamps và timeline. Tái sử dụng Thymeleaf fragments, Bootstrap và SiteMesh Staff layout/sidebar.

## Query và enforcement tại backend

`StaffOperationsService` chạy read-only transaction và tái sử dụng nguyên trạng `StaffStoreScopeService` 10.1:
SecurityContext → authenticated ACTIVE STAFF → ACTIVE StaffStoreAssignment → ACTIVE assigned Stores.
Không nhận staffEmail/userId/storeId từ request làm nguồn quyền. Layout attribute chỉ quyết định hiển thị landing;
mọi lần đọc dữ liệu vẫn resolve scope độc lập trong Service. Interceptor/Auth/JWT 10.1 giữ nguyên.

Dashboard dùng bốn SQL COUNT cho **từng Store đã resolve**; nhiều assignment có nhiều bộ thống kê riêng:

* Đơn cần xử lý: CONFIRMED, PREPARING, PACKED, SHIPPING. Không tính PENDING_PAYMENT, READY_FOR_PICKUP hoặc terminal states.
* Chờ khách nhận: fulfillment STORE_PICKUP **và** trạng thái READY_FOR_PICKUP.
* Sắp hết: StoreProduct ACTIVE có quantity từ 1 đến 5, bao gồm hai biên.
* Hết: StoreProduct ACTIVE có quantity = 0. Không tính StoreProduct INACTIVE; không trùng nhóm sắp hết.

Ngưỡng 5 là quy ước mặc định của Dashboard nhỏ và được hiển thị rõ trên UI. Không thêm setting/nghiệp vụ khác.
Năm đơn gần đây dùng cùng query scoped queue, không đọc toàn hệ thống.

Queue dùng `findByStoreIdInOrderByCreatedAtDescIdDesc(assignedIds, pageable)`;
data và total đều có predicate Store, sắp xếp createdAt DESC/id DESC ổn định, fetch Store/User để tránh N+1.
Detail dùng `scope.requireOrder(orderId)` → SQL Order id + assigned Store ids, rồi mới đọc items/payment/history
qua `OrderViewService`. Cross-Store/missing resource không trả child data.

Test phát hiện history seed Order #2 có ghi chú chứa pickup_code dù summary đã ẩn mã. Sửa nhỏ `OrderViewService`
che mã trong ghi chú khi reader trả dữ liệu Staff; không sửa DB/history/transition. Customer của Order vẫn thấy mã
theo quy tắc Phase 9.4. Trang Staff Pickup cũ cũng giữ đúng quy tắc ẩn mã.

Không thêm action/state transition, Pickup Queue mới, xác minh mã, Stock Adjustment, Inventory History hoặc Admin feature.

## File thêm / sửa trong 10.2

Đường dẫn Java dưới `src/main/java/com/oneshop/`, template dưới `src/main/resources/templates/`.
Các thay đổi 10.1 có sẵn trong working tree được giữ; không tính chúng thành thay đổi mới 10.2.

| Loại | File |
| --- | --- |
| Thêm Service | `service/StaffOperationsService.java` |
| Thêm Controller | `controller/staff/StaffOrderController.java` |
| Thêm DTO | `dto/response/StaffDashboardResponse.java`, `StaffStoreDashboardResponse.java`, `StaffOrderSummaryResponse.java`, `StaffOrderDetailResponse.java` |
| Thêm template | `staff/orders/index.html`, `staff/orders/detail.html`, `staff/orders/fragments.html` |
| Thêm test | `src/test/java/com/oneshop/StaffOperationsDatabaseIntegrationTest.java` |
| Thêm báo cáo | `docs/Phase10_2_StaffDashboardOrderOperations_Report.md` |
| Sửa Controller | `controller/staff/StaffDashboardController.java` |
| Sửa Repository | `repository/OrderRepository.java`, `repository/StoreProductRepository.java` (thêm scoped queue/count; giữ lookup/lock cũ) |
| Sửa reader | `service/OrderViewService.java` (che mã trong history note cho Staff) |
| Sửa template | `staff/dashboard.html`, `fragments/staff.html` |
| Sửa test fixture | `src/test/java/com/oneshop/AbstractIntegrationTest.java` (mock read model cho layout/auth tests không dùng DB) |
| Sửa assertion route | `src/test/java/com/oneshop/AccessControlIntegrationTest.java` (assigned Staff `/staff/orders` giờ 200 thay vì 404 vì page đã được tạo) |
| Sửa tài liệu | `README.md` |

## Kiểm thử

23 test mới dùng Tomcat/JWT/SiteMesh/Service/Repository/SQL Server thật, không mock data hoặc quyền.

| Yêu cầu | Kết quả / evidence |
| --- | --- |
| 1. Dashboard Staff A chỉ Store A | PASS: DTO và HTML cả `/staff`, `/staff/`, `/staff/dashboard` đối chiếu Store/name/address/counts. |
| 2. Thống kê A không tính B | PASS: SQL đối chiếu độc lập; fixtures hai Store, từng trạng thái trong cả 8 states; quantity 0/1/5/6 và SKU INACTIVE. |
| 3. Queue chỉ Order A | PASS: exact Order ids/order/total; 23 Order A + 27 Order B kiểm hai trang, page âm/page trống và recent 5. |
| 4. Detail đúng Store thành công | PASS: DELIVERY/STORE_PICKUP, receiver/amount/status/items snapshot/payment/history và pickup timestamps. |
| 5. Direct cross-Store bị chặn | PASS: Order B/0/-1/missing cùng ResourceNotFound/HTTP 404, không có Order/child resource. |
| 6. Sửa request/store_id không lộ Order khác | PASS: query/header/selected-Store cookie, GET JSON/form body; own detail vẫn 200, peer detail 404; POST queue không có handler (405). |
| 7. Không ACTIVE assignment bị chặn | PASS: revoke với JWT cũ, Dashboard/Queue/Detail 403 và Service AccessDenied; landing chỉ thông báo; restore có hiệu lực ngay. |
| 8. Regression Phase 1–10.1 | PASS: full suite có 787 tests, 0 failures, 0 errors; 786 passed, 1 conditional skip có sẵn. |

Còn kiểm nhiều ACTIVE assignment, INACTIVE assignment không cấp quyền, revoke Store B mất quyền ngay, Customer/Admin 403,
Staff không lộ mã trong legacy history note và Customer vẫn nhìn thấy mã của mình.
Sau từng test đối chiếu exact rows/fields/timestamps của chín bảng business và User metadata/Store/assignments;
fixture cleanup PASS, không reseed IDENTITY.

Lệnh đã chạy:

```powershell
mvn -o '-Dmaven.repo.local=C:\Users\Admin\.m2\repository' '-Dtest=StaffOperationsDatabaseIntegrationTest,LayoutsIntegrationTest,AccessControlIntegrationTest,StaffStoreScopeDatabaseIntegrationTest' test
mvn -o '-Dmaven.repo.local=C:\Users\Admin\.m2\repository' package
```

Focused suite: **69/69 PASS**. Full package: **787 tests / 0 failure / 0 error / 1 skipped, BUILD SUCCESS**,
39 suites, JAR `target/oneshop.jar`. Skip là test Cloudinary unconfigured-case có sẵn:
`imageUploadReachesCloudinaryServiceThroughTheMultipartFormWithCsrf` dùng `assumeFalse(isConfigured)`
để không upload thật khi môi trường có cấu hình Cloudinary. Không có test scope/10.2 bị skip.
Full run đã kiểm cả assertion mới về legacy Staff Pickup/Customer code và snapshot item names.

Evidence local: `target-phase10_2-focused.log`, `target-phase10_2-regression.log`, Surefire XML.
Không chạy standalone verifier Phase 9 cũ; regression dùng embedded Tomcat thật và SQL Server thật.
Không commit hoặc tự triển khai Phase 10.3.
