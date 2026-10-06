# Phase 9.3 – DELIVERY Flow

Trạng thái: **COMPLETED**, kiểm chứng ngày 06/10/2026. Phạm vi chỉ DELIVERY cho ONLINE/COD.
Dừng trước Phase 9.4 và không mở rộng Phase 10.

## 1. Audit trước khi code

Đã đọc toàn bộ hai tài liệu nguồn trong docs, report Phase 9.1/9.2 và request Phase 9.3. Đối chiếu Roadmap V2 mục
6.4/6.5/6.8, BR-08/10/13/14/18, TC-09/11/14/15, Phase 9 và ranh giới Staff Operations Phase 10.

Audit Order, OrderItem, Payment, OrderStatusHistory, StaffStoreAssignment, Store, User; Order/Payment/assignment repositories;
OrderService, PaymentService, InventoryService, StoreService; policy/expected state/cause; security, interceptor,
  Staff controller/template và SiteMesh layout. Phát hiện:

* Checkout đã khởi tạo COD CONFIRMED + UNPAID, trừ stock/snapshot và history initial; không tạo COD Payment.
* PaymentService đã quản lý Payment.status và Order.payment_status. Convention code là PAY-UUID; paid_at dùng giờ server chính xác giây.
* State machine đã có đầy đủ DELIVERY graph, history atomic và khóa PK Order; không có action Staff production.
* Scope Phase 5 hỗ trợ nhiều Store: ACTIVE Staff + ACTIVE assignment + ACTIVE Store. Interceptor thiết lập scope mỗi request;
  StoreService.requireAssignedStore là kiểm quyền trong service. Không cần cơ chế scope mới.
* Staff sidebar dùng navItem với cờ enabled; link DELIVERY mới được bật. Dashboard/stock/pickup vẫn là phần nền Phase 10.
* Bearer được miễn CSRF cho API cũ; cần loại các form DELIVERY khỏi exemption này để mọi POST DELIVERY luôn có CSRF.
  Test bổ sung phát hiện kiểm prefix URL thô có thể bypass bằng percent-encoded path; đổi sang PathPatternRequestMatcher
  cho cả DELIVERY và payment forms để khớp cùng parsed path với Spring MVC. Test tái hiện đã PASS sau sửa.
* Schema đã đủ cột và constraints; DELIVERY + PAY_AT_STORE bị CHECK chặn. Fixture phòng vệ này được kiểm trong unit test,
  không tắt constraint để tạo dữ liệu lỗi trên SQL Server.

## 2. Giữ nguyên từ Phase 9.1

ONLINE attempt/result endpoints, ownership, amount/method từ DB, pending reuse, SUCCESS/FAILED idempotency,
opposite-result guard, restore stock/CANCEL_ORDER và transaction/lock ordering. Không đổi InventoryService,
CheckoutService, PaymentController hoặc template payment. Generator PAY-UUID được tách thành helper dùng chung,
không đổi convention ONLINE. Payment luôn gắn Order, không gắn CheckoutSession.

## 3. Giữ nguyên từ Phase 9.2

OrderTransitionPolicy, Cause, toàn bộ graph DELIVERY/STORE_PICKUP, expectedStatus, terminal/same/backward/skip guards,
OrderService/OrderServiceImpl và cơ chế history/auditing/lock. Không thêm setter Order.status ở orchestration hoặc controller.
Toàn bộ 158 test Phase 9.2 giữ nguyên và PASS.

## 4. File thêm

* `src/main/java/com/oneshop/controller/staff/StaffDeliveryController.java`
* `src/main/java/com/oneshop/service/DeliveryFulfillmentService.java`
* `src/main/java/com/oneshop/service/impl/DeliveryFulfillmentServiceImpl.java`
* `src/main/java/com/oneshop/dto/response/DeliveryAction.java`
* `src/main/java/com/oneshop/dto/response/DeliveryOrderResponse.java`
* `src/main/java/com/oneshop/dto/response/DeliveryOrderDetailResponse.java`
* `src/main/java/com/oneshop/dto/response/OrderHistoryResponse.java`
* `src/main/resources/templates/staff/delivery/index.html`
* `src/main/resources/templates/staff/delivery/detail.html`
* `src/main/resources/templates/staff/delivery/fragments.html`
* `src/test/java/com/oneshop/service/DeliveryFulfillmentServiceTest.java`
* `src/test/java/com/oneshop/service/CodPaymentServiceTest.java`
* `src/test/java/com/oneshop/DeliveryWebIntegrationTest.java`
* `src/test/java/com/oneshop/DeliveryDatabaseIntegrationTest.java`
* `src/test/java/com/oneshop/DeliveryLiveSmoke.java`
* `docs/Phase9_3_DeliveryFlow_Report.md`

## 5. File sửa

* `src/main/java/com/oneshop/service/PaymentService.java`: thêm primitive COD nội bộ nhận Order đã khóa.
* `src/main/java/com/oneshop/service/impl/PaymentServiceImpl.java`: COD SUCCESS/PAID và generator code dùng chung.
* `src/main/java/com/oneshop/repository/OrderRepository.java`: truy vấn danh sách/detail từ assigned Store ids.
* `src/main/java/com/oneshop/config/SecurityConfig.java`: form DELIVERY/payment luôn cần CSRF, kể cả Bearer và URL encoded,
  dùng parsed path matcher hiện hữu của Spring Security.
* `src/main/resources/templates/fragments/staff.html`: bật link Đơn giao hàng.
* `README.md`: current phase, routes, guards, lifecycle, transaction và boundary.

## 6. File xóa

Không có. Không sửa SQL/schema, CHECK, entity, OrderStatus, cấu hình Hibernate, hai tài liệu nguồn hoặc assertions test cũ.
DeliveryAction là enum thao tác/view nội bộ, không phải enum trạng thái persisted hoặc thay đổi schema.

## 7. Service chịu trách nhiệm DELIVERY

DeliveryFulfillmentServiceImpl orchestrate quyền, business guards, actor, COD collection và state machine.
Tách orchestration này giữ OrderService là foundation state/history, PaymentService là payment; cũng tránh dependency
vòng OrderService ↔ PaymentService khi thêm business flow. Vẫn kiến trúc Controller → Service → Repository hiện hữu.

## 8. Production actions

```java
void startPreparingDelivery(String staffEmail, Long orderId);
void markDeliveryPacked(String staffEmail, Long orderId);
void markDeliveryShipping(String staffEmail, Long orderId);
void completeDelivery(String staffEmail, Long orderId);
```

Mỗi action có expected/target cố định, kiểm DELIVERY, payment guard, Staff scope/actor, khóa Order và dùng foundation 9.2.
Không có public generic transition endpoint, completeOrder chung hai flow hoặc cancel action.

## 9. Routes/controller

StaffDeliveryController mỏng, chỉ nhận orderId, principal và model; không truy cập repository hoặc tự tạo Payment/history.

| HTTP | Route | Nghiệp vụ |
|---|---|---|
| GET | /staff/orders/delivery | Danh sách assigned Store, chỉ DELIVERY |
| GET | /staff/orders/delivery/{orderId} | Snapshot, thanh toán, lịch sử và action kế tiếp |
| POST | /staff/orders/delivery/{orderId}/prepare | CONFIRMED → PREPARING |
| POST | /staff/orders/delivery/{orderId}/pack | PREPARING → PACKED |
| POST | /staff/orders/delivery/{orderId}/ship | PACKED → SHIPPING |
| POST | /staff/orders/delivery/{orderId}/complete | SHIPPING → COMPLETED; ghi nhận thu COD nếu COD |

GET các URL action trả 405, không ghi dữ liệu. Route generic /{id}/transition không tồn tại. Những trường giả storeId,
userId, staffEmail, amount, method, expectedStatus, targetStatus, code không quyết định nghiệp vụ.

## 10. Staff UI tối thiểu

Danh sách hiển thị mã đơn/Store, created_at, người nhận, tổng tiền, phương thức/trạng thái thanh toán, trạng thái đơn
và nút action kế tiếp. Chi tiết có người nhận/phone/address, DELIVERY, snapshot tên/giá/số lượng/subtotal,
total, payment receipt, history với actor/time/note. Thymeleaf escape dữ liệu; test kiểm snapshot chứa script không thực thi.
Không có field sửa price, quantity, Store, method hoặc fulfillment. Nút completion COD nhắc chỉ xác nhận khi đã giao và thu đủ tiền.
Form có CSRF; Staff layout/SiteMesh/sidebar hiện hữu được giữ và link DELIVERY được bật.

## 11. Assigned Store

Tái sử dụng StaffStoreScopeInterceptor và StoreService.getAssignedStores/requireAssignedStore. Assignment đọc từ DB,
phải ACTIVE, User ACTIVE + STAFF, Store ACTIVE. Một Staff được phép có nhiều assignment như Phase 5.
Không suy luận scope từ selectedStoreId, cookie, JWT store claim, query, hidden input hoặc header.

## 12. Kiểm Order thuộc Store

Read list dùng store_id IN assigned ids + fulfillment_type DELIVERY. Detail dùng orderId + assigned ids,
không tìm thấy hoặc sai scope cùng trả 404. Mutation khóa Order theo PK rồi gọi requireAssignedStore với Store thật của Order;
sai scope trả 403. Không dựa vào việc UI đã lọc. User actor tải từ staffEmail principal trong service và kiểm ACTIVE/STAFF.

## 13. Locking strategy

PESSIMISTIC_WRITE qua OrderRepository.findByIdForUpdate(orderId) chỉ lọc PK, như Phase 9.1/9.2. Khóa trước scope/guard
và mọi Payment operations. Khi gọi OrderService.transition, foundation lấy lại cùng khóa của cùng Order trong cùng transaction.
Không khóa StoreProduct vì fulfillment không đổi tồn. Không thêm distributed lock hoặc optimistic version.

## 14. Transaction boundary

Bốn public mutation method @Transactional REQUIRED là transaction bao ngoài. Read methods readOnly và trả DTO đã detached.
COD recordCodCollected dùng MANDATORY, tham gia transaction delivery. OrderService.transition cũng tham gia transaction đó.
Không có REQUIRES_NEW/commit độc lập. Bất kỳ exception ở Payment/transition/history làm rollback toàn bộ.

## 15. DELIVERY status flow

| Business action | Expected | Target |
|---|---|---|
| startPreparingDelivery | CONFIRMED | PREPARING |
| markDeliveryPacked | PREPARING | PACKED |
| markDeliveryShipping | PACKED | SHIPPING |
| completeDelivery | SHIPPING | COMPLETED |

Skip/backward/same-state/stale intent bị từ chối. COMPLETED/CANCELLED không có action tiếp. STORE_PICKUP và PAY_AT_STORE
bị chặn trong mọi action; không convert dữ liệu lỗi. Staff không được confirm/cancel PENDING_PAYMENT thay PaymentService.

## 16. ONLINE payment guards

ONLINE phải PAID tại mọi bước. PENDING_PAYMENT không có prepare/pack/ship/complete; CONFIRMED + UNPAID và các stage
UNPAID inconsistent đều bị từ chối, không tự đánh dấu PAID hoặc tạo SUCCESS. Không bổ sung kiểm Payment SUCCESS tồn tại
cho ONLINE vì convention hiện hữu dùng Order.payment_status; service không tự sửa dữ liệu. Completion giữ nguyên receipt cũ.

## 17. COD payment lifecycle

Checkout tạo CONFIRMED + UNPAID và chưa có Payment. PREPARING/PACKED/SHIPPING vẫn UNPAID, không tạo Payment.
Chỉ xác nhận giao thành công/thu tiền mới tạo COD SUCCESS và PAID cùng COMPLETED. COD PAID/FAILED trước completion bị
từ chối. Không có action thu tiền riêng từ HTTP và không có COD PENDING do ứng dụng tạo sớm.

## 18. Thời điểm tạo COD Payment

Trong completeDelivery, sau Order lock, Staff scope/actor, DELIVERY, method/payment guard và expected SHIPPING.
PaymentService.recordCodCollected còn kiểm lại COD + DELIVERY + SHIPPING + UNPAID, amount hợp lệ từ Order và receipt SUCCESS đã có.
Tạo Payment bằng Order.total_amount snapshot, không tính lại từ StoreProduct price hoặc đọc amount form.

## 19. transaction_code COD

PAY-UUID từ generator dùng chung với ONLINE; paid_at = LocalDateTime.now().withNano(0), created_at do auditing.
Không lấy code/time client, không giả gateway, không thêm UNIQUE constraint. UUID cung cấp độ duy nhất thực tế theo convention 9.1.

## 20. COD complete atomic

Thứ tự trong transaction:

1. Khóa Order, kiểm scope/actor, DELIVERY/COD/SHIPPING/UNPAID.
2. PaymentService kiểm receipt thành công đã tồn tại, tạo COD SUCCESS, code/time/amount và đặt Order.payment_status=PAID.
3. saveAndFlush Payment ghi Payment và Order payment state trong transaction hiện tại.
4. State machine kiểm expected SHIPPING và cạnh COMPLETED, flush Order, ghi Staff history.
5. Commit chung. Nếu bước nào lỗi, Payment và mọi update/history rollback.

Không tồn tại commit partial SUCCESS + SHIPPING, COMPLETED + UNPAID hoặc COMPLETED không receipt do flow này tạo.

## 21. Chống duplicate COD Payment

Order lock serialize requests trước khi kiểm hoặc tạo Payment. Request thứ hai thấy terminal COMPLETED và bị từ chối.
PaymentService còn từ chối khi đã có receipt SUCCESS dù Order.payment_status vẫn UNPAID, không thu lại hoặc tự sửa.
Không dùng constraint mới thay cho guard. paid_at/code/receipt cũ không bị rewrite khi double click.

## 22. History

Chỉ OrderService foundation tạo history. Một action hợp lệ tạo đúng một row old/new, actor, note và changed_at.
Không có history song song trong DeliveryFulfillmentService/PaymentService/controller. Checkout NULL → initial và payment
system history giữ nguyên. UI dùng DTO đọc history theo changed_at/id trong transaction readOnly.

## 23. Actor convention

Production DELIVERY history dùng User Staff đã xác thực, không null và không từ request body. Note là label action cố định
an toàn; không nhận free-text note ở phase này. History initial/payment vẫn null actor hệ thống theo 9.1/9.2.

## 24. Dùng State Machine 9.2

Delivery gọi transition(orderId, fixedExpected, fixedTarget, trustedActor, fixedNote). Foundation giữ source graph và cause
FULFILLMENT, mutation status và history. Audit setter sau sửa: chỉ Checkout khởi tạo và OrderService đổi order_status;
PaymentService khởi tạo/cập nhật payment_status sau Checkout. Orchestration không setOrderStatus/setPaymentStatus.

## 25. TC-09

PASS SQL Server thật và real HTTP test: checkout COD với snapshot/items → bốn Staff action → COMPLETED + PAID,
một Payment COD SUCCESS với đúng amount, paid_at/code/created_at, bốn history fulfillment có actor và đúng các cạnh.
Không tạo receipt sớm, không trừ stock hoặc thêm movement sau checkout. CheckoutSession/OrderItem giữ nguyên.

## 26. ONLINE DELIVERY

PASS: checkout ONLINE → SUCCESS Phase 9.1 → CONFIRMED/PAID → đủ bốn action. So sánh toàn bộ receipt sau từng bước:
id, amount/method/status, paid_at, code và created_at giữ nguyên; không Payment mới. Bốn fulfillment history có Staff actor,
payment history trước đó actor hệ thống null. Stock/movement và snapshots giữ nguyên.

## 27. ONLINE PENDING_PAYMENT guard

PASS cả bốn action, service và real HTTP. Mọi row Order/Payment/history/inventory không đổi. UI pending không có nút prepare.
SQL test còn kiểm ONLINE UNPAID ở CONFIRMED/PREPARING/PACKED/SHIPPING: tất cả bị từ chối.

## 28. Store scope tests

PASS: Staff Thủ Đức không đọc detail hoặc chạy action Order Gò Vấp, kể cả body gửi storeId=Thủ Đức; list không lộ Order khác.
Service recheck không thể bypass bằng gọi trực tiếp. ACTIVE assignment thứ hai cho phép đọc Store thứ hai đúng convention.
Assignment/Store/User INACTIVE đều thu hồi read/mutation scope. Fixtures scope được khôi phục trong finally và so sánh
rows/timestamps của assignments, Stores, users metadata sau mỗi test. CUSTOMER/ADMIN/guest bị chặn theo role hiện hữu.

## 29. STORE_PICKUP isolation

PASS mọi DELIVERY action với Order STORE_PICKUP của chính Store Staff. Backend trả 400 trước mutation, kể cả CONFIRMED
có cạnh PREPARING giống graph pickup. Không triển khai pickup action. DELIVERY + PAY_AT_STORE được kiểm in-memory vì SQL CHECK
đã cấm combination này; guard reject và không đổi method, không disable CHECK.

## 30. Concurrency

SQL Server thật: cùng prepare và cùng ship, mỗi case chạy ba lượt độc lập với hai transaction/thread đồng thời.
Mỗi lượt có một success, một BadRequestException sau lock, final PREPARING/SHIPPING, đúng một history và không Payment mới.
Không dùng mock lock/transaction hoặc H2.

## 31. COD complete concurrency

Ba lượt race hai completeDelivery trên SHIPPING/UNPAID. Mỗi lượt một success, một reject, final COMPLETED/PAID,
chỉ một Payment SUCCESS và một completion history. Gọi lại completion bị từ chối; so sánh rows xác nhận receipt/code/paid_at
và history không thay đổi. Không tạo double collection.

## 32. Rollback

Năm test inject lỗi: Payment save, sau persist+flush Payment, trước transition COMPLETED, khi save history,
sau persist+flush history. Trong transaction lỗi có native SQL xác nhận Payment SUCCESS + PAID/SHIPPING hoặc Order đã
COMPLETED thực sự được ghi. Sau rollback mọi row/timestamp bằng snapshot trước: SHIPPING + UNPAID, không receipt mới,
không completion history. Helper COD ngoài transaction bị MANDATORY từ chối.

## 33. Stock không đổi

TC-09/ONLINE kiểm toàn bộ StoreProduct rows sau từng action bằng bản sau checkout; quantity/price/status/timestamps
không đổi. Test đổi catalog price/name sau mua còn xác nhận detail và COD amount lấy từ purchase snapshot; fixture được phục hồi.

## 34. Không InventoryMovement mới

TC-09/ONLINE so sánh toàn bộ inventory_movements sau từng action bằng bản sau checkout, không chỉ count.
DeliveryFulfillmentService không phụ thuộc InventoryService. ORDER/CANCEL_ORDER/STOCK_ADJUST không được tạo từ fulfillment.
Phase 9.1 FAILED vẫn restore/movement như cũ.

## 35. Phase 9.1 regression

52/52 PASS: 11 Payment unit, 4 inventory restore unit, 6 Payment web, 31 Payment database.
SUCCESS/FAILED, stock restore/CANCEL_ORDER, duplicate/opposite, rollback và races giữ nguyên.

## 36. Phase 9.2 regression

158/158 PASS: 129 policy, 5 OrderService, 24 SQL state/history. Graph hai flow, causes, guards, auditing,
rollback và concurrency giữ nguyên. Không sửa test cũ để che behavior khác.

## 37. Test mới Phase 9.3

**140/140 PASS**, không skip:

| Suite | Tests |
|---|---:|
| DeliveryFulfillmentServiceTest | 55 |
| CodPaymentServiceTest | 14 |
| DeliveryWebIntegrationTest | 14 |
| DeliveryDatabaseIntegrationTest | 57 |

DeliveryLiveSmoke là standalone verifier, không cộng vào số JUnit test. Nó dùng JAR dev riêng, HTTP/JDBC, không Spring test context/mock.

## 38. Tổng PASS / FAIL / ERROR / SKIP

Full regression/package: **576 tests / 31 suites: 575 PASS, 0 FAIL, 0 ERROR, 1 SKIP**.
Baseline 436 tests, tăng đúng 140. Skip cũ là CatalogHttpDatabaseIntegrationTest upload Cloudinary có điều kiện;
Cloudinary đã cấu hình nên không upload thật từ test. Không SQL/DELIVERY test nào skip.
Surefire XML và target-phase9_3-regression.log chứa kết quả. Maven Wrapper Windows có lỗi cũ; dùng Maven sẵn có với offline cache,
không sửa wrapper/pom. PowerShell có thể báo NativeCommandError cho Mockito agent warning; Maven và Surefire vẫn BUILD SUCCESS.

## 39. App JAR + SQL Server thật

Đã build JAR và khởi động riêng profile dev trên cổng 18081, Hibernate schema validate với SQL Server thật.
Sau sửa parsed path CSRF matcher, chạy lại full regression/build và cả hai standalone verifier với JAR cuối:

* DeliveryLiveSmoke: PASS TC-09 COD, bốn Staff histories, một receipt đúng amount/time/code, snapshots/stock/movements,
  ONLINE paid DELIVERY và giữ nguyên receipt, terminal/double click, pending ONLINE, cross Store, pickup isolation,
  role, CSRF cookie/Bearer, percent-encoded DELIVERY/payment URLs và GET guards.
* PaymentLiveSmoke: PASS ONLINE SUCCESS/FAILED, audit/amount/isolation, ownership/CSRF, offline methods,
  restore mọi item/CANCEL_ORDER, history, duplicate/terminal và role guards.
* Cả hai verifier đối chiếu exact rows sau cleanup đều PASS. Đã dừng launcher/process Java con của app test;
  xác minh không còn Java smoke JAR và cổng 18081 đã đóng.

Logs local: `target/phase9_3-live-delivery.log`, `target/phase9_3-live-payment.log`, `target/phase9_3-dev.log`.
Không mock services, transaction hoặc JDBC trong standalone verification.

Lệnh tái hiện trên DB dev seed đã cấu hình, không có writer khác:

```powershell
mvn package dependency:build-classpath '-Dmdep.outputFile=target/payment-classpath.txt'
# Khởi động app ở terminal riêng:
java -jar target/oneshop.jar --spring.profiles.active=dev --server.port=18081
# Sau khi app sẵn sàng, dùng terminal khác:
$deliverySmokeClasspath = 'target/test-classes;target/classes;' + (Get-Content target/payment-classpath.txt -Raw).Trim()
java -cp $deliverySmokeClasspath com.oneshop.DeliveryLiveSmoke http://localhost:18081
java -cp $deliverySmokeClasspath com.oneshop.PaymentLiveSmoke http://localhost:18081
```

## 40. Cleanup

Automated DELIVERY tests dùng SeedGuard và assertion so sánh toàn bộ row của orders, order_items, payments,
order_status_history, inventory_movements, store_products, checkout_sessions, cart_items, carts sau từng test.
57/57 PASS exact cleanup, gồm id/timestamps/stock/snapshot. Fixture scope và catalog name cũng phục hồi trong finally;
scope có assertion riêng, không chạm Order seed. Không reseed IDENTITY; khoảng trống identity do test là bình thường.
Standalone smoke cũng restore trong finally và kiểm toàn bộ rows; cả DELIVERY và Payment verifier đều PASS:

| Bảng | Trước smoke | Sau cleanup |
|---|---:|---:|
| orders | 5 | 5 |
| order_items | 7 | 7 |
| payments | 5 | 5 |
| order_status_history | 16 | 16 |
| inventory_movements | 9 | 9 |
| store_products | 21 | 21 |
| checkout_sessions | 3 | 3 |
| cart_items | 3 | 3 |
| carts | 2 | 2 |

Assertion kiểm toàn bộ row/id/timestamp, không chỉ count. Không còn fixture hoặc tiến trình app test chạy nền.

## 41. Conventions tự quyết

* Có thể có nhiều assignment, giữ đúng Phase 5; list chứa DELIVERY của tất cả Store được phân công, không thêm Store selector.
* Routes nằm dưới /staff/orders/delivery để tách DELIVERY rõ ràng. List tối thiểu không search/filter/pagination phức tạp.
* Scope detail 404 giống unknown Order; mutation sai scope 403. Business/stale/duplicate/terminal 400, database/lock 409
  với thông báo an toàn. Không SQL/stack trace trên UI.
* COD yêu cầu UNPAID cho mọi action trước completion; bất nhất PAID/FAILED hoặc đã có receipt SUCCESS thì reject,
  không tự repair dữ liệu. Không tạo COD PENDING vì convention hiện hữu không có.
* COD completion cùng action xác nhận đã thu đủ tiền; không có collect endpoint riêng. Ghi nhận bằng PAY-UUID và giờ server
  precision giây như ONLINE, amount từ Order snapshot. Không phải payment/courier gateway.
* Duplicate Staff action explicit reject như 9.2; ONLINE duplicate result vẫn idempotent như 9.1.
* ONLINE PAID là guard theo convention hiện hữu; không bổ sung truy vấn receipt SUCCESS để thay đổi contract.
* Note Staff dùng label action cố định; actor từ principal/DB. Không thêm form nhập note hoặc target status.
* Bearer exemption CSRF của API khác giữ nguyên, riêng payment và form DELIVERY luôn cần CSRF; dùng parsed path matcher
  để percent-encoded characters không bypass guard. Web test và smoke đều kiểm trường hợp này.
* CheckoutSession status không tự aggregate khi Delivery Order hoàn tất, giữ grouping convention Phase 8/9.1.

## 42. Để Phase 9.4

READY_FOR_PICKUP production action, pickup_code, ready_at, xác minh mã, picked_up_at, collect PAY_AT_STORE,
pickup completion/queue. Không thêm bất kỳ field/timestamp pickup nào trong DELIVERY.
Không tự chuyển sang Phase 9.4 sau report.

## 43. Để Phase 10

Dashboard/statistics, advanced order queue/search/filter/export, pickup queue đầy đủ, stock adjustment UI,
inventory history screen và Staff Operations hoàn chỉnh. UI hiện tại chỉ đủ demo DELIVERY.
Gateway/webhook, courier/tracking/GPS/route, delivery failure/retry, cancel/return/refund đều chưa làm theo phạm vi yêu cầu.
