# Phase 9.4 – STORE_PICKUP Flow

Ngày kiểm chứng: 06/10/2026. Trạng thái: **COMPLETED**. Chỉ Phase 9.4; dừng trước 9.5 và full Phase 10.

## 1. Audit trước khi code

Đọc hai tài liệu nguồn Roadmap V2/phân chia giai đoạn, report 9.1/9.2/9.3 và request 9.4. Đối chiếu mục 6.4/6.6/6.8,
BR-11/13/14/18, Phase 9, TC-10/15. Audit Order, Payment, OrderItem, OrderStatusHistory, StaffStoreAssignment, Store,
User, các repository và OrderService/policy/PaymentService/DeliveryFulfillmentService/StoreService; security, SiteMesh,
checkout/result, constraints và fixtures/tests hiện hữu.

Baseline code: commit `46701df` Phase 9.3, working tree sạch; 576 tests / 575 PASS / 1 Cloudinary SKIP.
Checkout đúng: pickup ONLINE pending/unpaid, PAY_AT_STORE confirmed/unpaid, snapshot/trừ tồn/history initial.
Pickup fields đã có NVARCHAR(20), ready_at/picked_up_at; CHECK bắt buộc READY có code + ready_at, pickup fields chỉ pickup,
COD chỉ DELIVERY. Foundation đã đủ graph/lock/history, chưa có pickup orchestration hoặc Customer Order Detail.
Staff scope đã hỗ trợ nhiều ACTIVE assignment/Store của ACTIVE STAFF; không cần cơ chế quyền mới.
Seed Order READY có mã 6 ký tự: cần giữ khả năng xử lý đơn cũ khi chọn format mới. Không sửa seed/schema.

## 2. Giữ nguyên Phase 9.1

ONLINE attempt/result, owner, amount/method DB, pending reuse, idempotent SUCCESS/FAILED, opposite-result guard,
FAILED restore đúng stock/CANCEL_ORDER, transaction và thứ tự Order → stock. InventoryService và Checkout không sửa.
Payment vẫn gắn Order; không gắn CheckoutSession. Thêm primitive PAY_AT_STORE không tạo gateway.

## 3. Giữ nguyên Phase 9.2

OrderService/OrderTransitionPolicy và toàn bộ matrix/causes/expected-state/history/lock không sửa.
Không setters order_status ở pickup hoặc controller; mọi cạnh production dùng foundation FULFILLMENT.

## 4. Giữ nguyên Phase 9.3

DeliveryFulfillmentService/controller/DTO/actions không sửa. COD guard/transaction/amount/PAY-UUID giữ nguyên;
chỉ tách private helper thu offline dùng chung, thêm PAY_AT_STORE primitive. Shared state/method fragment bổ sung nhãn pickup.
Parsed path CSRF của DELIVERY/payment giữ nguyên và thêm pickup. Không sửa assertions của bất kỳ test cũ nào.

## 5. File thêm

* `src/main/java/com/oneshop/controller/client/CustomerOrderController.java`
* `src/main/java/com/oneshop/controller/staff/StaffPickupController.java`
* `src/main/java/com/oneshop/dto/response/OrderViewResponse.java`
* `src/main/java/com/oneshop/dto/response/OrderDetailResponse.java`
* `src/main/java/com/oneshop/dto/response/PickupAction.java`
* `src/main/java/com/oneshop/service/CustomerOrderService.java`
* `src/main/java/com/oneshop/service/OrderViewService.java`
* `src/main/java/com/oneshop/service/PickupCodeService.java`
* `src/main/java/com/oneshop/service/PickupFulfillmentService.java`
* `src/main/java/com/oneshop/service/impl/CustomerOrderServiceImpl.java`
* `src/main/java/com/oneshop/service/impl/PickupFulfillmentServiceImpl.java`
* `src/main/resources/templates/orders/index.html`
* `src/main/resources/templates/orders/detail.html`
* `src/main/resources/templates/orders/fragments.html`
* `src/main/resources/templates/staff/pickup/index.html`
* `src/main/resources/templates/staff/pickup/detail.html`
* `src/test/java/com/oneshop/PickupDatabaseIntegrationTest.java`
* `src/test/java/com/oneshop/PickupWebIntegrationTest.java`
* `src/test/java/com/oneshop/PickupLiveSmoke.java`
* `src/test/java/com/oneshop/service/PickupFulfillmentServiceTest.java`
* `src/test/java/com/oneshop/service/PayAtStorePaymentServiceTest.java`
* `src/test/java/com/oneshop/service/OrderViewServiceTest.java`
* Báo cáo này.

## 6. File sửa

* `README.md`: current phase, pickup/customer flow, routes, guards và conventions.
* `SecurityConfig.java`: pickup forms luôn yêu cầu CSRF, kể cả Bearer/encoded path.
* `OrderRepository.java`: list theo email principal, created_at/id giảm dần, fetch Store.
* `PaymentService.java` / `impl/PaymentServiceImpl.java`: primitive PAY_AT_STORE MANDATORY; private offline helper dùng chung COD.
* `templates/checkout/result.html`: link chi tiết từng Order.
* `templates/fragments/navbar.html`: Customer Đơn hàng của tôi.
* `templates/fragments/staff.html`: bật link pickup đúng route.
* `templates/staff/delivery/fragments.html`: nhãn READY_FOR_PICKUP và PAY_AT_STORE.

## 7. File xóa

Không có. Không sửa SQL/schema, constraints, entity, persisted enums, Hibernate config, dependency hoặc hai tài liệu nguồn.
PickupAction là enum thao tác/view nội bộ, không persisted và không bind từ request.

## 8. Service STORE_PICKUP

PickupFulfillmentServiceImpl orchestration: quyền, khóa, guards, code/time, thu offline và gọi state/history.
PickupCodeService sinh/so sánh code; OrderViewService đọc chung snapshot/history/payment cho Customer và Staff pickup.
Các service này vẫn trong kiến trúc Controller → Service → Repository; không framework flow hoặc dependency vòng.
Reuse StoreService, OrderService, repository scope/lock, DTO history/payment và fragment trạng thái thay vì copy DELIVERY.

## 9. Production actions

`startPreparingPickup(staffEmail, orderId)`, `markReadyForPickup(staffEmail, orderId)`,
`completePickup(staffEmail, orderId, pickupCode)`. Expected/target cố định từ PickupAction; không action generic/GET mutation.

## 10. Routes/controller

| HTTP | Route | Mục đích |
|---|---|---|
| GET | /orders | Đơn của Customer |
| GET | /orders/{orderId} | Chi tiết read-only |
| GET | /staff/orders/pickup | Pickup thuộc assigned Stores |
| GET | /staff/orders/pickup/{orderId} | Snapshot/history và action kế tiếp |
| POST | /staff/orders/pickup/{orderId}/prepare | CONFIRMED → PREPARING |
| POST | /staff/orders/pickup/{orderId}/ready | PREPARING → READY_FOR_PICKUP |
| POST | /staff/orders/pickup/{orderId}/complete | Verify code, thu nếu cần, COMPLETED |

Controller nhận principal/orderId và riêng complete nhận pickupCode; form chỉ có code + CSRF. Không DTO amount/method/status/
timestamps/transaction code/Store/user/actor. Field giả không quyết định nghiệp vụ. GET action 405, generic transition 404.
Business 400, owner/detail sai scope 404, mutation sai scope 403, DB/lock 409 với message an toàn.

## 11. Customer Order Detail

Store/tên/địa chỉ, created_at, fulfillment/method/payment/order status, receiver/phone/address nếu DELIVERY,
OrderItem snapshot và total, receipts, timeline, ready_at/picked_up_at nếu có. Code chỉ READY/COMPLETED.
Sau COMPLETED có thông báo mã không còn dùng được. Không nút Customer complete/cancel/refund/reorder.

## 12. Staff UI tối thiểu

List pickup có id/Store/created/receiver/method/payment/status/ready_at/total/next action. Detail dùng snapshot chung,
history và fixed action. COMPLETE nhập mã khách cung cấp; Staff không nhận mã lưu DB trong view DTO.
PAY_AT_STORE có lời nhắc chỉ bấm khi đã thu đủ tiền. SiteMesh/Bootstrap và sidebar hiện hữu.
Không queue nâng cao, search/filter/pagination/dashboard/export/stock screens.

## 13. Staff Store scope

StaffStoreScopeInterceptor kiểm request; service kiểm lại bằng getAssignedStores/requireAssignedStore.
ACTIVE User + STAFF role + ACTIVE assignment + ACTIVE Store, supports nhiều Store. Mutation lấy Store từ Order đã khóa.
Không dùng selectedStoreId, query/hidden field/cookie/JWT Store claim/header làm quyền.

## 14. Customer ownership

CustomerOrderService kiểm ACTIVE CUSTOMER từ principal email, query id + user.email hoặc list theo email.
Order khác Customer trả 404 trước khi đọc items/history/payments/code/receiver. Historical Store INACTIVE vẫn đọc được.
ADMIN/STAFF/guest bị chặn tại security boundary /orders. Customer chỉ đọc, không tự completion.

## 15. Locking

OrderRepository.findByIdForUpdate PK-only PESSIMISTIC_WRITE trước scope/guards/code/Payment.
Foundation reuses lock trong cùng transaction. Không khóa StoreProduct vì không thay inventory.
Thứ tự Order → Payment → Order transition/history giống COD.

## 16. Transaction

Ba public pickup mutations @Transactional REQUIRED. Read services readOnly, DTO detached.
recordPayAtStoreCollected MANDATORY; transition REQUIRED tham gia orchestration. Reader snapshot MANDATORY read-only.
Không REQUIRES_NEW hoặc commit riêng. Runtime/DataAccess/history errors rollback toàn bộ transaction.

## 17. STORE_PICKUP lifecycle

CONFIRMED → PREPARING → READY_FOR_PICKUP → COMPLETED. Mỗi action chỉ một cạnh và một history.
Same/skip/backward/stale/COMPLETED/CANCELLED bị từ chối. Wrong fulfillment/method bị chặn trước side effect.

## 18. ONLINE guards

PAID tại mọi action; PENDING_PAYMENT hoặc UNPAID/FAILED ở stage khác bị reject. Không tự tạo SUCCESS/repair.
Payment 9.1 SUCCESS đưa pending → confirmed trước fulfillment. ONLINE completion không sửa payment/status/code/paid_at.

## 19. PAY_AT_STORE lifecycle

CONFIRMED/PREPARING/READY đều UNPAID, không Payment do flow này tạo. PAID/FAILED sớm hoặc receipt SUCCESS dù Order UNPAID
bị reject tại mọi action, không repair. Chỉ complete đúng code và Staff đã xác nhận thu đủ tiền mới thu.

## 20. Thời điểm tạo Payment

Trong completePickup, sau Order lock, quyền/actor/flow/method/expected/payment guards và code verification.
PaymentService kiểm lại STORE_PICKUP/PAY_AT_STORE/READY/UNPAID, ready fields, amount hợp lệ và chưa có SUCCESS.
Tạo SUCCESS thẳng; không PENDING sớm. amount = total_amount snapshot DB, không giá catalog hoặc form.

## 21. Payment transaction_code

PAY-UUID generator chung với ONLINE/COD; paid_at giờ server chính xác giây, created_at auditing.
Khác hoàn toàn pickup_code; không gateway/Staff code hoặc constraint mới.

## 22. pickup_code format

Mã mới 8 ký tự uppercase, alphabet A-Z bỏ I/O và digits 2-9; dài hơn mã seed nhưng trong NVARCHAR(20).
Input strip khoảng trắng ngoài, uppercase Locale.ROOT. Không integer: mã seed có leading zero vẫn giữ String.
Legacy chỉ 6 ký tự A-Z/0-9 khi chính Order đang giữ format đó; không chấp nhận mã 6 cho Order mã 8.

## 23. Generator

SecureRandom, 8 lần chọn đều trên alphabet 32 ký tự, 40 bit entropy. Không Order id/user/phone/time/prefix đoán được.
Không logging code trong service/controller, exception hoặc URL. Form gửi POST body; Staff view không có stored code.

## 24. Collision strategy

Không global uniqueness check hoặc thêm UNIQUE/index/schema. Verification có orderId + assigned Store + code của chính
Order đã khóa; code trùng ở hai Order không đổi phạm vi identity/quyền. Không global findByPickupCode.

## 25. Không regenerate

Expected PREPARING kiểm sau lock trước generator. Request READY thứ hai thấy READY/terminal và reject.
PREPARING có code/time/picked_up_at bất nhất cũng reject, không rewrite. Mã/ready_at giữ sau completion.

## 26. ready_at

LocalDateTime.now().withNano(0) chỉ trong READY thành công; set cùng code trước foundation transition.
Request timestamp bị bỏ qua. Rollback trả NULL/giá trị trước transaction; duplicate giữ nguyên.

## 27. picked_up_at

Chỉ set sau code verification, guards và payment nếu PAY_AT_STORE, ngay trước completion transition.
Giờ server chính xác giây. Trước completion/wrong code vẫn NULL; rollback NULL; duplicate không rewrite.

## 28. CHECK READY_FOR_PICKUP

Code + ready_at tồn tại trên managed Order trước saveAndFlush READY của foundation. SQL tests xác nhận update READY
thật với fields, constraints orders không bị disable. Không transition trước rồi cập nhật field sau; không DDL.

## 29. Verify code

Khóa scoped Order, yêu cầu READY và ready fields hợp lệ, normalize/validate format theo code lưu trên Order,
MessageDigest.isEqual bytes ASCII. Không lookup global. Staff biết code của Store khác vẫn bị scope reject trước verify.

## 30. Wrong code

400: “Mã nhận hàng không hợp lệ.” Không echo code đúng/sai, không order/payment/history/timestamp/stock mutation.
Null/blank/whitespace/wrong length/characters/wrong valid code đều có tests; UI escape snapshot/note.

## 31. Duplicate completion

Order lock serialize requests; lần thứ hai thấy COMPLETED, expected READY mismatch và reject.
Offline helper còn chặn SUCCESS có sẵn. Không Payment/picked_up_at/history/code/ready_at lần hai.

## 32. PAY_AT_STORE atomic

Order lock → guards + verify → Payment SUCCESS + PAID + payment flush → picked_up_at → COMPLETED flush + Staff history → commit.
Payment/code/amount/time và mọi Order field/history cùng transaction; rollback không giữ PAID/receipt/time partial.

## 33. ONLINE atomic

Order lock → guards + verify → picked_up_at → COMPLETED + history → commit chung.
History/transition failure khôi phục READY, time NULL, receipt trước đó nguyên vẹn.

## 34. State machine 9.2

Pickup chỉ gọi transition với id/fixedExpected/fixedTarget/trusted actor/fixed label note.
Policy graph/cause unchanged; foundation là nơi duy nhất fulfillment đổi order_status. Không public transition API.

## 35. OrderStatusHistory

Một entry mỗi cạnh qua foundation; changed_at auditing, old/new đúng, actor Staff, note cố định không code.
Initial/payment system history giữ NULL actor. Reader sort changed_at rồi id, Customer timeline chỉ đọc.

## 36. Staff actor

User từ principal email, kiểm ACTIVE/STAFF. Không null hoặc body Staff/user id.
SQL audit kiểm changed_by_user_id bằng Staff thật; completion không đổi actor thành system khi thu PAY_AT_STORE.

## 37. TC-10

SQL/service + real HTTP checkout PAY_AT_STORE → ba Staff actions, Customer thấy READY code, wrong-code reject, correct code
→ một SUCCESS/PAID/COMPLETED/picked_up_at. Đúng ba histories/actor, amount/paid_at/PAY-UUID, code/ready time ổn định.
Không early Payment, inventory/snapshot/CheckoutSession exact rows unchanged sau từng action.

## 38. ONLINE STORE_PICKUP

SQL + HTTP: checkout → 9.1 SUCCESS → CONFIRMED/PAID → prepare/ready/verify/complete.
Receipt exact rows giữ nguyên sau từng action; completion không thêm Payment. Legacy seed format có SQL test trên Order mới,
không mutate seed Order. Duplicate payment SUCCESS sau fulfillment vẫn theo 9.1.

## 39. Wrong-code tests

Unit malformed/null/blank/length/characters + SQL exact 9-table snapshot + HTTP invalid error.
Mã sai không set picked_up_at/PAID hoặc thêm Payment/history. Mã đúng lowercase/outer whitespace được normalize.

## 40. Ownership/code visibility

SQL wrong Customer 404, forged userId không đổi quyền; service list không có code, Staff DTO không có code.
Exhaustive OrderViewServiceTest kiểm tất cả OrderStatus với code đã có: trước READY vẫn hidden, READY/COMPLETED chỉ Customer view.
Real HTTP owner thấy code và timeline, Customer khác không đọc, Customer không có complete endpoint.

## 41. Store scope

SQL direct service và HTTP: cross-Store detail 404/action 403; ACTIVE Staff assignment/store/user mới có scope.
Ba kiểu inactive reject read và mọi mutation. Multiple assignment list đúng Store/type. Scope rows phục hồi sau test.

## 42. DELIVERY isolation

Mọi pickup action với DELIVERY reject, kể cả common prepare edge. Unit COD pickup guard (SQL CHECK không bị tắt).
TC-15 pre-check cùng checkout có DELIVERY COD/pickup ONLINE/pickup PAY_AT_STORE: xử lý một Order không đổi hàng xóm,
neighbor payments, CheckoutSession, stock/movements. Không aggregate checkout status.

## 43. Concurrency READY

SQL Server thật, hai transaction đồng thời, ba vòng độc lập. Một success/một BadRequest sau lock; một READY history,
code/ready_at hợp lệ duy nhất; retry không rewrite. Không mock lock/transaction/H2.

## 44. Concurrency complete

PAY_AT_STORE và ONLINE, mỗi method ba vòng hai request đồng thời đúng code. Một success/một reject; một completion history/time.
Offline một SUCCESS/PAID; ONLINE receipt cũ không đổi. Duplicate sau race exact snapshot unchanged.

## 45. Rollback READY

Bốn điểm inject: sau set fields trước transition (flush PREPARING + fields để chứng minh đã ghi), tại Order READY flush,
history save, sau history persist/flush. Native SQL kiểm trạng thái/fields trong transaction. Rollback exact rows = trước,
PREPARING/code NULL/ready_at NULL, không READY history.

## 46. Rollback complete

Offline bảy điểm: Payment save, sau persist/flush Payment, sau PAID trước insert Payment, sau picked_up_at,
trước transition, history save, sau history flush. SQL quan sát actual writes rồi rollback exact 9 bảng.
Online ba điểm: sau time trước transition, history save, sau history flush; READY/time NULL/receipt nguyên vẹn.
Test helper gọi ngoài transaction bị MANDATORY từ chối. Spies đặt tại repositories/OrderService; không mock transaction.

## 47. Stock

Snapshot toàn bộ StoreProduct rows sau checkout bằng rows sau từng action và sau rejected request/rollback;
quantity/price/status/timestamps không đổi. Pickup không dependency InventoryService.

## 48. InventoryMovement

Compare toàn row sau checkout/từng action, không chỉ count. Không ORDER/CANCEL_ORDER/STOCK_ADJUST trong pickup fulfillment.
ONLINE FAILED Phase 9.1 tiếp tục restore/movement theo contract cũ.

## 49. Snapshot

Customer/Staff detail lấy OrderItem.product_name/unit_price/quantity/subtotal và Order.total_amount.
SQL test đổi tên/giá catalog sau mua: detail/receipt giữ purchase snapshot, fixtures name/price được khôi phục.

## 50. Phase 9.1 regression

**52/52 PASS**: PaymentServiceTest 11, InventoryRestoreTest 4, PaymentWebIntegrationTest 6, PaymentDatabaseIntegrationTest 31.
SUCCESS/FAILED, restore/CANCEL_ORDER, idempotency/opposite, concurrency và rollback đều giữ nguyên.

## 51. Phase 9.2 regression

**158/158 PASS**: policy 129, OrderService 5, OrderStateDatabaseIntegrationTest 24. Không đổi graph/matrix/cause.

## 52. Phase 9.3 regression

**140/140 PASS**: DeliveryFulfillmentServiceTest 55, CodPaymentServiceTest 14, DeliveryWebIntegrationTest 14,
DeliveryDatabaseIntegrationTest 57. COD/ONLINE/scope/concurrency/rollback/parsed CSRF/inventory contract giữ nguyên.

## 53. Test mới 9.4

| Suite | Tests |
|---|---:|
| PickupFulfillmentServiceTest | 53 |
| PayAtStorePaymentServiceTest | 14 |
| OrderViewServiceTest | 8 |
| PickupDatabaseIntegrationTest | 62 |
| PickupWebIntegrationTest | 15 |
| Tổng JUnit mới | 152/152 PASS |

PickupLiveSmoke là standalone verifier, không tính JUnit. Test cũ không sửa để che regression.

## 54. Tổng tests

**728 tests / 36 suites: 727 PASS, 0 FAIL, 0 ERROR, 1 SKIP.** Baseline 576, tăng 152.
Full regression/package cuối BUILD SUCCESS. Skip cũ: CatalogHttpDatabaseIntegrationTest.
imageUploadReachesCloudinaryServiceThroughTheMultipartFormWithCsrf; Cloudinary đã cấu hình nên assumption tránh upload thật từ test.
Không test SQL/pickup bị skip.
Maven offline/cache, wrapper Windows lỗi cũ nên dùng Maven cài sẵn; không sửa pom/wrapper. Evidence local Surefire XML,
target-phase9_4-regression.log; git diff --check.

## 55. JAR + SQL Server thật

JAR build thành công, chạy riêng profile dev cổng 18081, Hibernate validate với SQL Server thật.
PickupLiveSmoke dùng HTTP/JDBC độc lập, không Spring test context/mock: PASS TC-10 PAY_AT_STORE, ONLINE pickup,
owner code visibility, wrong code, duplicate ready/complete, timestamps, receipt snapshot, exact stock/movements/items,
mixed checkout isolation, ONLINE pending, cross Store dù biết code, Customer khác, DELIVERY isolation,
Staff/Customer/Admin/guest, CSRF thiếu/sai/cookie/Bearer/encoded path và GET guards.
DeliveryLiveSmoke cũ trên JAR mới cũng PASS COD/ONLINE, actor/history/receipt/inventory/scope/CSRF và cleanup.
PaymentLiveSmoke cũ cũng PASS SUCCESS/FAILED, restore mọi item/CANCEL_ORDER, audit, idempotency, ownership/CSRF/roles/offline guards
và exact cleanup. Logs local: target/phase9_4-live-pickup.log,
target/phase9_4-live-delivery.log, target/phase9_4-live-payment.log, target/phase9_4-dev.log.
Lệnh tái hiện trên dev seed không có writer khác:

```powershell
mvn package dependency:build-classpath '-Dmdep.outputFile=target/payment-classpath.txt'
java -jar target/oneshop.jar --spring.profiles.active=dev --server.port=18081
# Terminal khác:
$pickupSmokeClasspath = 'target/test-classes;target/classes;' + (Get-Content target/payment-classpath.txt -Raw).Trim()
java -cp $pickupSmokeClasspath com.oneshop.PickupLiveSmoke http://localhost:18081
java -cp $pickupSmokeClasspath com.oneshop.DeliveryLiveSmoke http://localhost:18081
java -cp $pickupSmokeClasspath com.oneshop.PaymentLiveSmoke http://localhost:18081
```

## 56. Database verification

Automated SQL và standalone JAR trực tiếp kiểm trước complete: READY/UNPAID/code+ready_at/picked_up_at NULL/no offline Payment.
Sau complete: COMPLETED/PAID/code+ready_at stable/picked_up_at, một SUCCESS amount snapshot/code/time,
ba Staff edges. Tests rollback/races dùng SQL Server thật/schema validate, CHECK orders không disable.

## 57. Cleanup

SeedGuard + exact-row snapshots 9 bảng sau từng SQL test; users metadata/Stores/assignments có scope snapshot riêng.
Catalog name và assignment/inactive fixture khôi phục finally. Không reseed IDENTITY.

Lần phát triển test đầu có fixture Order 1347 bị sót sau lỗi khi spy/stub PaymentService MANDATORY proxy;
full regression phát hiện tồn 39 thay vì 40. Đổi điểm inject sang repository spies, xác định đúng fixture theo Order/time/receiver,
khôi phục quantity và updated_at gốc từ before-image transaction log, cleanup riêng fixture trong transaction.
Không sửa test catalog hoặc reset toàn seed để che lỗi. Sau recovery full regression 728 tests PASS (một skip cũ).
62 SQL pickup tests và cả ba standalone Pickup/Delivery/Payment verifier so sánh toàn bộ rows/id/quantity/snapshot/timestamps
sau cleanup: PASS.

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

Đối chiếu toàn rows, không chỉ count. Đã dừng launcher/process Java con của app test và xác minh cổng 18081 đóng;
không còn Java process chạy smoke JAR.

## 58. Convention tự quyết

Orchestration pickup riêng, shared snapshot reader và labels; codes mới 8 ký tự SecureRandom, legacy scoped 6;
strip + uppercase + String/constant-time comparison; no global collision check; Staff không thấy stored code;
complete action là xác nhận đã nhận/thu đủ tiền, không collect endpoint riêng; PAID ONLINE/UNPAID PAY_AT_STORE,
offline receipt inconsistent reject mọi stage; timestamps server precision giây; duplicate Staff explicit reject;
errors 400/403/404/409; historical inactive Store Customer vẫn xem; no CheckoutSession aggregation.

## 59. Để Phase 9.5

Integration verification tổng thể và phối hợp toàn bộ payment/fulfillment theo phạm vi phase sau.
Phase này chỉ thực hiện TC-15 isolation pre-check bảo vệ pickup, không tự chuyển phase hoặc mở thêm nghiệp vụ.

## 60. Để Phase 10

Staff operations hoàn chỉnh, dashboard/statistics, queue/search/filter/pagination/export, stock adjust/inventory history UI.
Không notification/SMS/email/QR/scanner/gateway/webhook/refund/return/cancel mới/Store transfer/courier/tracking/Redis/queue/table mới.
Dừng sau báo cáo Phase 9.4 để review.
