# Phase 9.5 – Integration + Verification

Ngày kiểm chứng: 06/10/2026. **PHASE 9.5 COMPLETED**.
**PHASE 9 – PAYMENT + FULFILLMENT COMPLETED**.
Phạm vi chỉ tích hợp/kiểm chứng 9.1–9.4; không triển khai Phase 10.

## 1. Kết quả audit 9.1–9.4

Đọc hai tài liệu nguồn Roadmap V2/phân chia giai đoạn và bốn report 9.1–9.4. Đối chiếu 6.4/6.5/6.6/6.8,
BR-08/11/13/14/18, Phase 9 và TC-09/10/15. Audit CheckoutService, PaymentService, OrderService/policy,
DeliveryFulfillmentService, PickupFulfillmentService, CustomerOrderService, OrderViewService, InventoryService,
StoreService, repositories, security/controllers/templates và bảy entity CheckoutSession/Order/OrderItem/Payment/
OrderStatusHistory/InventoryMovement/StoreProduct.

Quan hệ đúng `CheckoutSession 1–N Order 1–N Payment`. Checkout snapshot và trừ kho một lần, mỗi Order một Store;
payment/status/history/fulfillment dùng Order id. OrderService giữ state/history, PaymentService giữ payment state,
orchestration kiểm quyền/type/expected/payment trước side effect. Pickup fields được đặt trước flush READY.
Không có batch update sibling hoặc aggregation CheckoutSession trong các action.

Thứ tự lock: checkout Cart → StoreProducts id tăng dần; FAILED Order → StoreProducts id tăng dần;
COD/PAY_AT_STORE Order → Payment/state/history. REQUIRED/MANDATORY giữ cùng transaction, không REQUIRES_NEW.
Các race SQL thật không phát hiện deadlock; đây là bằng chứng trên các tình huống đã kiểm, không phải chứng minh mọi tải thực tế.

Audit N+1: ba list dùng query fetch Store, không load items/payment/history từng Order. Detail query theo một Order,
history sort changed_at/id, actor lazy được dùng lại trong persistence context. Test mới đo Hibernate prepared statement:
query count từng list giữ nguyên trước/sau thêm ba Order ở ba Store; Customer detail và hai Staff detail đều không quá
12 statement trên timeline đầy đủ. Không thêm cache/Redis hoặc chỉnh query production.

Baseline đầu 9.5 là working tree đã có 9.4 chưa commit, HEAD `46701df` Phase 9.3; 728 tests/727 PASS/1 skip cũ.
Giữ nguyên toàn bộ thay đổi 9.4. Danh sách file ở mục 4–6 là phần bổ sung riêng của 9.5.

## 2. Có phát hiện integration defect không

Không phát hiện defect production trong các integration test mới và regression đã chạy.
Không bắt đầu bằng refactor, không sửa nghiệp vụ để làm test thuận tiện.

## 3. Defect và cách sửa

Không có production fix Phase 9.5. Không thay business rule, service/controller/security/repository/template,
entity, persisted enum, dependency, Hibernate config, seed hoặc database schema trong phase này.
Không xóa/loosen/skip assertions cũ. Các file production đang có thay đổi trong Git thuộc 9.4 đã có trước 9.5.

## 4. File thêm

* `src/test/java/com/oneshop/Phase9IntegrationScenario.java`: scenario HTTP/JDBC dùng chung, scoped fixture,
  exact cleanup, TC-15, mixed failure, snapshot, quyền, CSRF và matrix.
* `src/test/java/com/oneshop/Phase9IntegrationWebDatabaseTest.java`: JUnit, Tomcat + SQL Server thật,
  service rollback/concurrency và đo query; không Mockito overrides.
* `src/test/java/com/oneshop/Phase9IntegrationLiveSmoke.java`: standalone chạy trên JAR dev riêng.
* `docs/Phase9_5_IntegrationVerification_Report.md`: báo cáo này.

## 5. File sửa

`README.md`: cập nhật phase hiện tại/đóng Phase 9, mô tả integration invariants, test và lệnh chạy verifier.
Không sửa test hoặc verifier cũ; helper mới đọc/reuse SeedGuard hiện hữu mà không đổi implementation.

## 6. File xóa

Không có. Không thay SQL table/column/constraint/index, không disable CHECK. Dev vẫn `ddl-auto=validate`.

## 7. Test mới Phase 9.5

| Nhóm JUnit trong Phase9IntegrationWebDatabaseTest | Tests |
|---|---:|
| TC-15 HTTP full lifecycle, có/không đổi catalog | 2 |
| Mixed failure + sibling tiếp tục hoàn tất | 1 |
| Customer ownership + toàn bộ cross-Store Staff | 1 |
| CSRF/encoded path/roles/GET/cross-flow | 1 |
| Multiple ACTIVE/INACTIVE assignment | 1 |
| Invalid pairing business + SQL CHECK | 2 |
| Representative rollback bốn subsystem | 4 |
| Hai Order cùng checkout commit độc lập | 1 |
| Năm representative race cùng Order | 5 |
| Query count list/detail | 1 |
| **Tổng** | **19** |

Standalone verifier dùng cùng scenario HTTP/JDBC, không tính vào số JUnit.

## 8. Thiết kế TC-15

Một Cart thật của Customer seed, chọn SKU Store 1/2/4. Một POST checkout tạo một CheckoutSession và ba Order.
Fixture đặt quantity mỗi line bằng 1 qua CartService HTTP, kể cả line đã có trong cart; cleanup phục hồi cả seed cart ids/
quantities/timestamps. Scenario hoàn thành cả ba Order, không dừng ở pre-check.

## 9. CheckoutSession được tạo thế nào

HTTP Cart add/update → POST `/checkout` với CSRF và ba group. Backend Phase 8 khóa Cart/stock, tính tiền từ DB,
tạo session CREATED và ba Order, snapshots/ORDER movements/history initial, xóa đúng selected lines.
Assert session count +1, Order count +3, same checkout_id, total bằng tổng ba Order. Result UI link đến từng Order detail.
Session exact row giữ nguyên sau từng payment/fulfillment action; không tự aggregate CREATED/PARTIAL/COMPLETED.

## 10. Các Order trong TC-15

| Order | Store | SKU |
|---|---|---|
| A | 1 – Thủ Đức | COCOON-SERUM-30 |
| B | 2 – Gò Vấp | INNI-TONER-200 |
| C | 4 – Quận 10 | INNI-CLEANS-120 |

Order ids được lấy từ checkout mới, không dùng seed Order để mutate. Mỗi Order chỉ có OrderItem đúng StoreProduct của mình.

## 11. Payment method

A = COD; B = ONLINE; C = PAY_AT_STORE. Payment luôn mang order_id, amount/method từ Order.
Test kiểm metadata SQL không có checkout_id/checkout_session_id trong payments.

## 12. Fulfillment

A = DELIVERY. B/C = STORE_PICKUP. Địa chỉ giao chỉ A; B/C nhận ở Store sở hữu đơn, không đổi Store khi xử lý.

## 13. Initial states

| Order | order_status | payment_status | Payment |
|---|---|---|---|
| A | CONFIRMED | UNPAID | Không có |
| B | PENDING_PAYMENT | UNPAID | Không có |
| C | CONFIRMED | UNPAID | Không có |

Initial history NULL → initial, system actor NULL. Kho Q → Q−1 một lần và ORDER movement đúng reference_order_id.

## 14. Final states

| Order | order_status | payment_status | Receipt |
|---|---|---|---|
| A | COMPLETED | PAID | Một COD SUCCESS |
| B | COMPLETED | PAID | ONLINE SUCCESS ban đầu nguyên vẹn |
| C | COMPLETED | PAID | Một PAY_AT_STORE SUCCESS |

Same checkout_id giữ nguyên. B/C có ready_at/code và picked_up_at. A không pickup fields.

## 15. Order A DELIVERY + COD

CONFIRMED → PREPARING → PACKED → SHIPPING → COMPLETED. Trước complete vẫn UNPAID/không Payment.
Complete tạo đúng một COD SUCCESS, snapshot amount, PAY-UUID, server paid_at, PAID và Staff completion history.
Customer/Staff detail hiển thị snapshots/timeline/receipt đúng. B/C exact aggregate giữ nguyên sau mỗi cạnh.

## 16. Order B PICKUP + ONLINE

PENDING_PAYMENT → ONLINE SUCCESS → CONFIRMED/PAID → PREPARING → READY_FOR_PICKUP → COMPLETED.
Owner thấy link thanh toán khi pending và code khi ready; Staff view không chứa stored code. Completion đặt picked_up_at,
giữ code/ready_at. Toàn bộ ONLINE Payment row bằng trước pickup, không receipt thứ hai. A/C không đổi.

## 17. Order C PICKUP + PAY_AT_STORE

CONFIRMED/UNPAID → PREPARING → READY_FOR_PICKUP/UNPAID. Không Payment sớm.
Wrong code 400 với toàn chín bảng unchanged. Correct code (outer whitespace/lowercase normalize) → một SUCCESS,
PAID, picked_up_at, COMPLETED và Staff history cùng transaction. A/B không đổi.

## 18. Payment isolation

Mỗi attempt/result/offline completion so sánh exact sibling aggregates: Order, items, payments, history, movements.
ONLINE chỉ đổi B; COD receipt chỉ A; PAY_AT_STORE receipt chỉ C. Không batch update theo checkout/user.
Amount = Order.total_amount; paid_at/code từ server, fields giả không quyết định dữ liệu.

## 19. Fulfillment isolation

Sau từng action, sibling aggregates và CheckoutSession exact row giữ nguyên. Common CONFIRMED → PREPARING vẫn có
type guard của orchestration: pickup endpoint với DELIVERY và delivery endpoint với PICKUP bị 400, không side effects.
Terminal complete bị 400; không recollect hoặc thêm history/time.

## 20. History isolation

Full timeline A: NULL → CONFIRMED → PREPARING → PACKED → SHIPPING → COMPLETED.
B: NULL → PENDING_PAYMENT → CONFIRMED → PREPARING → READY_FOR_PICKUP → COMPLETED.
C: NULL → CONFIRMED → PREPARING → READY_FOR_PICKUP → COMPLETED.
Assert mỗi row đúng order_id/old/new/time; initial/payment actor NULL, fulfillment actor là seed Staff đúng Store từ principal.
Reader/query sort changed_at rồi history_id. ONLINE DELIVERY timeline đầy đủ được kiểm lại trong regression/DeliveryLiveSmoke.

## 21. Inventory isolation

Assert từng OrderItem đúng store_product_id/Store, quantity 1, unit_price/subtotal snapshot.
ORDER movement quantity_change −1, before Q, after Q−1, reference Order và system staff NULL.
Mọi action fulfillment/ONLINE SUCCESS so sánh exact toàn store_products và inventory_movements; không có write mới.

## 22. Mixed payment failure

Một checkout mới vẫn A DELIVERY/COD, B PICKUP/ONLINE, C PICKUP/PAY_AT_STORE.
B FAILED → CANCELLED/FAILED; A/C không đổi và vẫn chạy full flow bình thường.
Kiểm intermediate A COMPLETED/PAID, B CANCELLED/FAILED, C READY/UNPAID hợp lệ; session CREATED exact unchanged.
Sau đó C hoàn tất; final A/C COMPLETED/PAID, B CANCELLED/FAILED. Không cancel sibling/session.

## 23. Stock restore isolation

Chỉ StoreProduct của B Q−1 → Q. Tất cả StoreProduct khác exact row bằng bản sau checkout.
Một CANCEL_ORDER đúng B/SP, change +1, staff NULL. Repeat FAILED idempotent: không thêm movement/history hoặc restore lần hai.
Opposite SUCCESS và fulfillment sau CANCELLED bị reject, toàn chín bảng unchanged.

## 24. Customer ownership

Customer khác không đọc checkout, detail/code/receiver/timeline/receipt của cả A/B/C, không bắt đầu payment bằng forged userId.
404 cùng unknown Order. `/orders` list query dùng email principal, không lấy quyền từ userId/checkoutId.
STAFF/ADMIN không vào Client order/payment; guest redirect login. Customer không có action complete riêng.

## 25. Staff Store scope

Ba Staff Thủ Đức/Gò Vấp/Quận 10 kiểm mọi pair Store khác: detail 404, mọi action 403, kể cả biết correct pickup code.
Scope lấy Store thực của locked Order và ACTIVE assignments từ DB, không browser Store id.
Fixture thêm assignment ACTIVE Store 2/INACTIVE Store 4 cho Staff Store 1: list/action đúng chỉ ACTIVE Store; history actor
vẫn Staff thực. Assignment được xóa riêng trong finally và exact scope snapshot bằng trước test.
Inactive Store/User/assignment regression 9.3/9.4 giữ PASS.

## 26. Pickup code visibility

Trước READY code hidden; READY/COMPLETED chỉ Customer owner detail. Completed có lời nhắc mã không còn sử dụng.
Staff list/detail và Customer list không lộ stored code. Other Customer/cross Store denied trước khi đọc dữ liệu.
Verification dùng locked orderId + scope + code riêng Order; không global lookup/code trong URL hoặc logging mới.
Code mới 8 SecureRandom characters, legacy 6 scoped theo convention 9.4 giữ nguyên.

## 27. Wrong pickup code

Cả ONLINE/PAY_AT_STORE READY: wrong code 400, response không chứa mã thật, exact nine-table snapshot unchanged,
bao gồm target/sibling payment/history/timestamps/inventory. Sau đó correct code vẫn complete thành công.
Malformed/null/length/characters code và legacy tests 9.4 được chạy lại nguyên vẹn.

## 28. Invalid combinations

| Fulfillment | Payment | Hợp lệ | Initial → final |
|---|---|---|---|
| DELIVERY | ONLINE | YES | PENDING_PAYMENT/UNPAID → CONFIRMED/PAID → COMPLETED/PAID |
| DELIVERY | COD | YES | CONFIRMED/UNPAID → COMPLETED/PAID |
| DELIVERY | PAY_AT_STORE | NO | Business guard + SQL CHECK reject |
| STORE_PICKUP | ONLINE | YES | PENDING_PAYMENT/UNPAID → CONFIRMED/PAID → COMPLETED/PAID |
| STORE_PICKUP | PAY_AT_STORE | YES | CONFIRMED/UNPAID → COMPLETED/PAID |
| STORE_PICKUP | COD | NO | Business guard + SQL CHECK reject |

Hai test mới kiểm HTTP checkout reject 400/zero writes rồi thử SQL UPDATE trên fixture Order hợp lệ; DB thật reject
DataIntegrityViolationException/zero writes. Không NOCHECK hoặc fixture phá constraints. ONLINE FAILED là nhánh terminal
CANCELLED/FAILED/restore cho cả fulfillment, không retry hoặc fulfillment tiếp.

## 29. State machine regression

158 test Phase 9.2 giữ nguyên: policy 129, OrderService 5, SQL state/history 24. Full graphs/causes,
same/backward/skip/terminal/expected/lock/history/rollback. Payment-only edges chỉ đi qua PaymentService.

## 30. Payment regression

52 test Phase 9.1 giữ nguyên: unit payment 11, restore 4, web 6, SQL 31. Pending reuse, SUCCESS/FAILED,
owner/amount/method, idempotency/opposite, restore/movements/history/rollback/races. PaymentLiveSmoke chạy trên JAR cuối.

## 31. DELIVERY regression

140 test Phase 9.3 giữ nguyên: fulfillment unit 55, COD unit 14, web 14, SQL 57. COD/ONLINE full flow,
scope/payment/type/state guards, race, failure injection, snapshots/stock và parsed-path CSRF.

## 32. PICKUP regression

152 test Phase 9.4 giữ nguyên: fulfillment unit 53, PAY unit 14, view 8, SQL 62, web 15.
READY/code/time/verify/online/offline/scope/ownership/constraints/races/failure injection/inventory contract.

## 33. Representative concurrency

Sáu test mới dùng SQL Server thật, hai thread/transaction:

* A PACK được flush, transaction A giữ mở; C READY commit thành công trước khi A được cho phép commit. Latches có timeout,
  không dựa vào đo tốc độ. Chứng minh hai Order cùng checkout không bị lock CheckoutSession/Customer để serialize.
* Cùng Order: ONLINE SUCCESS versus FAILED, Delivery PREPARE, COD COMPLETE, Pickup READY, Pickup COMPLETE.
  Mỗi case một winner/một BadRequest loser; một edge/history, không receipt/restore/code/time trùng, sibling exact unchanged.

Thread futures có timeout, executor shutdown/await trong finally trước cleanup. Old repeated same-result/race suites chạy lại.

## 34. Representative rollback

Bốn test mới: FAILED+restore, COD complete, READY generation, PAY_AT_STORE complete.
TransactionTemplate thật bao production REQUIRED/MANDATORY, flush JPA; JDBC trong cùng transaction quan sát actual
SQL state/payment/history/movement/code/time đã ghi, rồi ném exception trước commit. Sau rollback compare toàn chín bảng
mọi field/id/time = trước, sibling intact. Không mock repository/transaction cho những test này.
Old failure-injection matrices bên trong pipeline 9.1–9.4 cũng chạy lại; representative mới không thay chúng.

## 35. CSRF/security

Ba nhóm forms Payment/Delivery/Pickup: thiếu/sai CSRF → 403; Bearer không được exemption, cả encoded orders/staff/
delivery/pickup path. Valid requests dùng CSRF từ form/cookie thật. GET action 405/zero writes; guest redirect.
Role matrix CUSTOMER/STAFF/ADMIN, ownership/cross Store và shared prepare type guard đều qua Security/Controller/SQL.
Forged userId/storeId/amount/method/paymentMethod/status/targetStatus/code/ready_at/picked_up_at (camel/snake variants)
không điều khiển trạng thái/money/actor/timestamps. Controller chỉ nhận principal/id và pickupCode ở complete.

## 36. Snapshot preservation

TC-15 lặp với Product.name và StoreProduct.price thay đổi sau checkout tại cả ba Store, rồi hoàn thành cả ba flow.
Customer/Staff detail hiển thị purchase name; OrderItem exact rows giữ unit_price/quantity/subtotal; receipt amount giữ
Order.total_amount. Không dùng current catalog price/client amount. Catalog name phục hồi finally; stock price/time qua SeedGuard.

## 37. Inventory conservation

Happy path: mỗi StoreProduct Q → Q−1 ở checkout, sau ONLINE SUCCESS/COD/PAY completion vẫn Q−1.
Mixed failure: B Q−1 → Q, A/C giữ Q−1 đến completion. Fulfillment không thêm ORDER/CANCEL_ORDER/STOCK_ADJUST.
Conservation được kiểm qua quantity, movement FK/change/before/after và exact snapshots, không chỉ counts.

## 38. TC-09 result

DELIVERY + COD: PASS trong TC-15 mới, regression SQL/HTTP cũ và DeliveryLiveSmoke trên JAR cuối.
Đủ bốn cạnh/Staff actor, đúng một COD SUCCESS/PAID, không early receipt hoặc thêm stock movement.

## 39. TC-10 result

STORE_PICKUP + PAY_AT_STORE: PASS trong TC-15 mới, regression SQL/HTTP cũ và PickupLiveSmoke trên JAR cuối.
Code/ready_at → verify → receipt/PAID/picked_up_at/COMPLETED/history atomic, wrong code/duplicate guard.

## 40. TC-15 result

Full E2E ba Store đến final state: PASS. Happy + changed catalog + mixed failure + scope/ownership + concurrency/
rollback đều kiểm đúng một grouping và các Order độc lập. Standalone JAR chạy lại full cases đến trạng thái cuối.

## 41. Số test mới

19 JUnit mới trong một suite; ba file verifier/scenario/test mới. Standalone smoke không cộng số test.
Baseline 728 + 19 = 747, không giảm test cũ hoặc thêm conditional skip.

## 42. Tổng PASS / FAIL / ERROR / SKIP

**747 tests / 37 suites: 746 PASS, 0 FAIL, 0 ERROR, 1 SKIP.** 19/19 test mới PASS, không skip.
Giữ nguyên baseline 728 tests và các suites Phase 9.1 (52), 9.2 (158), 9.3 (140), 9.4 (152).
Skip duy nhất vẫn CatalogHttpDatabaseIntegrationTest.imageUploadReachesCloudinaryServiceThroughTheMultipartFormWithCsrf:
Cloudinary đã cấu hình, assumption tránh upload thật từ test. Không SQL Server/payment/fulfillment/integration test bị skip.
Evidence local `target-phase9_5-regression.log`, Surefire XML. Maven offline cache có sẵn, không sửa wrapper/pom.

## 43. SQL Server thật

JUnit dev datasource SQL Server, Hibernate validate; repositories/locks/constraints/transaction thật. Core E2E mới không H2,
không mock transaction/repository. HTTP dùng Tomcat random port. Standalone đọc cùng cấu hình env, gọi JAR/DB độc lập.

## 44. JAR build

**BUILD SUCCESS**: Maven package + dependency:build-classpath, full regression trong cùng lần build cuối.
JAR `target/oneshop.jar`; khởi động dev riêng ở cổng 18081 với SQL Server thật/schema validate.
Build/package và dependency classpath bằng Maven cài sẵn, offline cache; không sửa cấu hình build.
Lệnh tái hiện:

```powershell
mvn package dependency:build-classpath '-Dmdep.outputFile=target/payment-classpath.txt'
java -jar target/oneshop.jar --spring.profiles.active=dev --server.port=18081
$phase9Classpath = 'target/test-classes;target/classes;' + (Get-Content target/payment-classpath.txt -Raw).Trim()
java -cp $phase9Classpath com.oneshop.PaymentLiveSmoke http://localhost:18081
java -cp $phase9Classpath com.oneshop.DeliveryLiveSmoke http://localhost:18081
java -cp $phase9Classpath com.oneshop.PickupLiveSmoke http://localhost:18081
java -cp $phase9Classpath com.oneshop.Phase9IntegrationLiveSmoke http://localhost:18081
```

## 45. PaymentLiveSmoke

**PASS** trên JAR cuối: SUCCESS/FAILED, amount/audit/isolation, duplicate/terminal, ownership/CSRF/roles/offline guard,
restore toàn items/CANCEL_ORDER/history và exact cleanup. Log `target/phase9_5-live-payment.log`.

## 46. DeliveryLiveSmoke

**PASS**: TC-09 COD, ONLINE DELIVERY giữ receipt, Staff history, pending/scope/pickup isolation,
roles/CSRF cookie+Bearer+encoded/GET guards và exact cleanup. Log `target/phase9_5-live-delivery.log`.

## 47. PickupLiveSmoke

**PASS**: TC-10 PAY_AT_STORE, ONLINE pickup giữ receipt, code visibility/wrong code/time/duplicate/terminal,
pending/cross Store/ownership/delivery isolation/role/CSRF+encoded/GET và exact cleanup.
Log `target/phase9_5-live-pickup.log`.

## 48. Phase9IntegrationLiveSmoke

**PASS** toàn bộ trên JAR cuối, sau khi ba verifier cũ đều PASS. Log `target/phase9_5-live-integration.log`.
TC-15 full lifecycle, đổi catalog sau mua, mixed failure/restore isolation/sibling completion, all cross Staff scopes,
owner/code visibility, CSRF/roles/encoded path/cross-flow, multi-assignment và invalid pairings business+DB CHECK.
Verifier không Spring test context/mock; dùng HTTP tới JAR thật + JDBC SQL Server, assertions cùng scenarios JUnit.

## 49. Exact cleanup

SeedGuard remember trước mutations, restore trong AutoCloseable finally. Compare toàn rows của chín bảng, cùng id/FK/
quantity/amount/status/snapshot/timestamps; security compare users metadata/stores/assignments; catalog compare products.
Assignment/name fixtures có finally riêng, chỉ xóa assignment mới/khôi phục tên đã nhớ. Không reset DB/reseed identity.

**PASS exact rows** sau từng JUnit mới, cả ba verifier cũ và Phase9IntegrationLiveSmoke.
Toàn bộ row/id/FK/status/amount/quantity/timestamp/snapshot bằng trước fixture, không chỉ counts bên dưới.
Security metadata và catalog names cũng exact restored. Không fixture leak hoặc reset/reseed để che lỗi.

| Bảng | Trước | Sau |
|---|---:|---:|
| checkout_sessions | 3 | 3 |
| orders | 5 | 5 |
| order_items | 7 | 7 |
| payments | 5 | 5 |
| order_status_history | 16 | 16 |
| inventory_movements | 9 | 9 |
| store_products | 21 | 21 |
| cart_items | 3 | 3 |
| carts | 2 | 2 |

## 50. App test đã dừng

**Đã dừng** launcher PID 7792 và process app Java con PID 25900 của JAR thử.
Kiểm CIM: không còn Java process chạy smoke JAR; HTTP health cổng 18081 không kết nối được, port đã đóng.
Không dừng Java/app khác. App log `target/phase9_5-dev.log`; test port 18081.

## 51. Conventions cuối Phase 9

Order per Store, Payment per Order (schema 1–N), CheckoutSession grouping only. Checkout snapshot/deduct một lần.
ONLINE PENDING → SUCCESS/FAILED; SUCCESS xác nhận PAID, FAILED terminal cancel/restore/no retry. Repeat cùng result
idempotent kể cả SUCCESS sau fulfillment; opposite reject. COD/PAY không early Payment, chỉ offline SUCCESS lúc completion.
Fixed Staff action/type/payment/scope guard, duplicate explicit reject. Foundation đổi state/history chung; trusted Staff actor,
system actor NULL. Pickup code mới 8 ký tự/legacy 6 scoped, không global lookup, stored code chỉ owner READY/COMPLETED,
normalize input/constant-time compare, time server precision giây; amount purchase snapshot, receipt PAY-UUID.
Business 400, owner/detail wrong scope 404, mutate wrong scope 403, data/lock conflict 409 safe. Protected forms luôn CSRF.

DELIVERY: confirmed → preparing → packed → shipping → completed.
PICKUP: confirmed → preparing → ready → completed. ONLINE có pending/payment-confirm trước các graph này.

## 52. Những phần cố ý không làm

Không gateway/MoMo/VNPAY/Stripe/webhook, customer cancel/refund/return/exchange, notification/email/SMS,
courier/tracking/QR, stock/dashboard/advanced queue/Admin order management, Redis/cache/new locks/schema.
Không polishing UI lớn hoặc refactor khi chưa có defect. Không deploy/publish/commit tự động.

## 53. Phase 9 có thể đóng chưa

**Có thể đóng Phase 9.** Tất cả DoD Phase 9.5 trong phạm vi yêu cầu đều PASS.
Đánh dấu **PHASE 9.5 COMPLETED** và **PHASE 9 – PAYMENT + FULFILLMENT COMPLETED**.
DoD gồm TC-09/10/15 full E2E, bốn valid flows + invalid guards, payment/fulfillment/history/inventory isolation,
failure target restore, ownership/scope/code/wrong-code/CSRF/encoded path, rollback/concurrency/snapshots/conservation,
regression 9.1–9.4/full suite, JAR SQL thật, cả bốn smoke và exact cleanup.

## 54. Chuyển sang Phase 10

Để phase sau: Staff dashboard, Order/pickup queue đầy đủ, search/filter/pagination, StoreProduct stock operations,
STOCK_ADJUST, inventory history và thống kê nhỏ theo Store. Không bắt đầu Phase 10 trong lượt này.
Dừng sau báo cáo để review.
