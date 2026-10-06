# Phase 9.2 – Order State Machine + History Foundation

Ngày kiểm chứng: 06/10/2026. Phạm vi chỉ Phase 9.2. Không triển khai action fulfillment production của Phase 9.3/9.4/9.5.

## 1. Audit trước khi sửa

Đối chiếu hai tài liệu `OneShop_Roadmap_KyThuat_V2_KhaoSatChuoiCuaHang (1).md` và
`OneShop_PhanChiaGiaiDoan_ThucHien_TheoRoadmapV2.md`, request Phase 9.2 và schema V2.

Đã kiểm tra Order, OrderItem, OrderStatusHistory, Payment; các repository tương ứng; OrderService, PaymentService,
CheckoutService, InventoryService; controller checkout/payment; StaffStoreScopeInterceptor; SecurityConfig;
BadRequestException, ResourceNotFoundException, WebExceptionHandler; các enum fulfillment/payment/order.

Audit toàn source cho setter trạng thái Order xác định:

| Nơi ghi | Trước Phase 9.2 | Sau Phase 9.2 |
|---|---|---|
| CheckoutServiceImpl | Khởi tạo Order.status và payment_status, history NULL → initial | Giữ nguyên Phase 8 |
| OrderServiceImpl | Hai helper payment đổi cả status và payment_status, ghi history | Một `apply` chung đổi order_status và ghi history |
| PaymentServiceImpl | Quản lý Payment.status; gọi helper đổi Order | Quản lý thêm Order.payment_status; gọi helper state/history chung |

Các `setStatus` còn lại thuộc Payment, catalog hoặc HTTP/DTO, không phải chuyển Order. Schema đã có toàn bộ enum và history;
không thêm table/cột/version. READY_FOR_PICKUP đã bị CHECK yêu cầu pickup_code và ready_at.

## 2. Refactor Phase 9.1

Giữ nguyên endpoint, ownership, CSRF, amount/method lấy từ DB, lock Order trước Payment/stock và transaction bao ngoài.
Chuyển việc đặt `Order.payment_status=PAID/FAILED` từ OrderService về PaymentService. Hai helper payment dùng policy và
`apply` chung. FAILED vẫn restore từng OrderItem trước khi CANCELLED, khóa StoreProduct theo id tăng dần.
Idempotent SUCCESS tiếp tục trả receipt đã thành công khi Order đã tiến lên fulfillment; không ghi history lại hoặc đưa
Order về CONFIRMED. FAILED lặp vẫn chỉ hợp lệ với FAILED + CANCELLED. Kết quả đối nghịch tiếp tục bị chặn.

## 3. File thêm

* `src/main/java/com/oneshop/service/OrderTransitionPolicy.java`
* `src/test/java/com/oneshop/service/OrderTransitionPolicyTest.java`
* `src/test/java/com/oneshop/service/OrderServiceTest.java`
* `src/test/java/com/oneshop/OrderStateDatabaseIntegrationTest.java`
* `docs/Phase9_2_OrderStateMachine_Report.md`

## 4. File sửa

* `src/main/java/com/oneshop/service/OrderService.java`: contract primitive nội bộ và ranh giới orchestration.
* `src/main/java/com/oneshop/service/impl/OrderServiceImpl.java`: execution/history dùng chung.
* `src/main/java/com/oneshop/service/impl/PaymentServiceImpl.java`: payment_status thuộc PaymentService, duplicate SUCCESS khi tiến trạng thái.
* `src/main/java/com/oneshop/repository/OrderRepository.java`: cập nhật comment lock dùng chung; truy vấn không đổi.
* `src/test/java/com/oneshop/service/PaymentServiceTest.java`: constructor và fixture fulfillment hợp lệ; giữ assertions Phase 9.1.
* `README.md`: current phase, graph, transaction, actor/note, boundary và hướng dẫn test database.

## 5. File xóa

Không có file bị xóa khỏi kết quả cuối cùng.

## 6. Class state machine

`OrderTransitionPolicy` chứa graph bất biến dùng enum và phân biệt `Cause.FULFILLMENT`, `PAYMENT_SUCCESS`, `PAYMENT_FAILURE`.
`OrderServiceImpl` thực thi graph và ghi history qua một method private `apply`. Policy không phụ thuộc DB, Payment,
inventory, HTTP hoặc Staff assignment. Không tạo layer framework mới.

## 7. API nội bộ

```java
boolean canTransition(OrderStatus current, OrderStatus target, FulfillmentType fulfillment);
boolean canTransition(OrderStatus current, OrderStatus target, FulfillmentType fulfillment, Cause cause);

void transition(Long orderId, OrderStatus expectedStatus, OrderStatus targetStatus, User actor, String note);
void confirmAfterOnlinePayment(Order lockedOrder);
void cancelAfterOnlinePaymentFailure(Order lockedOrder);
```

Overload ba tham số là graph đầy đủ; executor luôn dùng overload có cause. `transition` là primitive Java cho orchestration
đã xác thực, không có controller/form gọi. `expectedStatus` chống ý định dựa trên trạng thái cũ. Actor không lấy từ body HTTP.
Caller tương lai phải kiểm quyền/Store scope và làm đầy đủ side effects trong transaction bao ngoài; primitive không phải
action hoàn tất đơn, chuẩn bị pickup hoặc thu tiền. Hai helper payment yêu cầu Order managed đã được khóa và transaction hiện hữu.

## 8. DELIVERY matrix

| Current | Allowed target | Cause |
|---|---|---|
| PENDING_PAYMENT | CONFIRMED | PAYMENT_SUCCESS |
| PENDING_PAYMENT | CANCELLED | PAYMENT_FAILURE |
| CONFIRMED | PREPARING | FULFILLMENT |
| PREPARING | PACKED | FULFILLMENT |
| PACKED | SHIPPING | FULFILLMENT |
| SHIPPING | COMPLETED | FULFILLMENT |
| READY_FOR_PICKUP | Không có | — |
| COMPLETED | Không có | — |
| CANCELLED | Không có | — |

## 9. STORE_PICKUP matrix

| Current | Allowed target | Cause |
|---|---|---|
| PENDING_PAYMENT | CONFIRMED | PAYMENT_SUCCESS |
| PENDING_PAYMENT | CANCELLED | PAYMENT_FAILURE |
| CONFIRMED | PREPARING | FULFILLMENT |
| PREPARING | READY_FOR_PICKUP | FULFILLMENT |
| READY_FOR_PICKUP | COMPLETED | FULFILLMENT |
| PACKED | Không có | — |
| SHIPPING | Không có | — |
| COMPLETED | Không có | — |
| CANCELLED | Không có | — |

## 10. Payment-only transitions

Generic fulfillment primitive từ chối cả hai cạnh từ PENDING_PAYMENT. PaymentService kiểm ONLINE, UNPAID/PENDING_PAYMENT,
Payment PENDING thuộc Order, method/amount trước khi gọi helper có cause riêng. Không thêm generic cancel hoặc quyền
Staff confirm/cancel đơn pending. Helper không tự thay Payment hoặc payment_status.

## 11. Same-state

Không có self-edge. Trả BadRequestException và không ghi Order/history. Khác với payment result trùng, vốn trả receipt cũ
theo idempotency Phase 9.1.

## 12. Backward

Không có cạnh ngược; exhaustive test kiểm tất cả cặp enum cho cả hai fulfillment. SQL Server kiểm PREPARING → CONFIRMED
và xác nhận history giữ nguyên.

## 13. Skip-state

Chỉ các cạnh trực tiếp trong matrix hợp lệ. CONFIRMED → PACKED, CONFIRMED → READY_FOR_PICKUP, PREPARING → SHIPPING
đều bị chặn. `expectedStatus` còn chặn request SHIPPING dựa trên PREPARING ngay cả nếu request PACKED vừa commit.

## 14. Terminal

COMPLETED/CANCELLED không có cạnh ra. Unit matrix kiểm mọi đích; integration kiểm COMPLETED → CANCELLED/PREPARING và
CANCELLED → CONFIRMED/PREPARING. Terminal fixture chỉ sửa Order mới do test checkout tạo, không chạm Order seed.

## 15. History

Validate graph/expected state/note trước mutation; capture old/new, actor, note; đặt order_status; `saveAndFlush(Order)`;
`save(OrderStatusHistory)`. Auditing cấp changed_at. Mỗi cạnh thành công tạo một history. Không ghi history khi invalid,
same-state, stale hoặc rollback. Checkout giữ nguyên entry NULL → initial, không coi null là trạng thái business.

## 16. Actor

Orchestration truyền User từ ngữ cảnh đáng tin cậy. Hệ thống dùng null; hai cạnh payment giữ changed_by_user_id=NULL như
Phase 8/9.1. SQL test kiểm actor Staff thật và timestamp. Policy không kiểm role hoặc truy vấn StaffStoreAssignment.

## 17. Note

Tùy chọn null; giữ nguyên text an toàn từ caller. Từ chối quá 500 đơn vị UTF-16 trước mutation để khớp NVARCHAR(500).
Không dùng note để quyết định trạng thái. Caller chịu trách nhiệm không đưa token, secret hoặc nội dung nhạy cảm;
không có UI/body HTTP nhập note trong Phase 9.2. Unit và SQL test kiểm 501 bị chặn, 500 được lưu và actor hệ thống null.

## 18. Transaction

`transition` dùng REQUIRED, tham gia transaction orchestration nếu có. Hai helper payment dùng MANDATORY để không
tạo transaction rời. PaymentService giữ transaction bao ngoài cho Payment, payment_status, stock/movement và state/history.
Order được flush trước history để có thể kiểm chứng rollback sau UPDATE thật. History được commit cùng Order.

## 19. Lock

Tái sử dụng `OrderRepository.findByIdForUpdate` với PESSIMISTIC_WRITE và truy vấn chỉ theo PK. Primitive thông thường
chỉ yêu cầu khóa Order; không đọc/khóa stock, checkout, cart hoặc Store. Payment FAILED giữ thứ tự Order → StoreProduct
theo id tăng dần. Không thêm optimistic version hoặc thay schema. Caller phải tuân thủ đọc Order dưới khóa trước khi ra quyết định.

## 20. Concurrency

Trên SQL Server thật, hai transaction cùng CONFIRMED → PREPARING: ba lượt độc lập đều một thành công, một
BadRequestException sau khi khóa được giải phóng, final PREPARING và một history CONFIRMED → PREPARING.
Race PREPARING → PACKED với PREPARING → SHIPPING: PACKED thắng, SHIPPING bị từ chối, không có skip-edge hoặc history trùng.
Hồi quy các race payment cùng kết quả, kết quả đối nghịch và hoàn tồn nhiều Order dùng chung stock vẫn PASS.

## 21. Rollback

Hai test inject lỗi ở history: trước persist và sau persist+flush history. Trong transaction lỗi, query native xác nhận
Order đã thật sự UPDATE thành PREPARING. Sau rollback, mọi cột của Order (kể cả updated_at) bằng bản gốc và không có history mới.
Rollback Payment FAILED giữa các movement và sau history tiếp tục được kiểm trong PaymentDatabaseIntegrationTest.

## 22. Regression

Chạy toàn bộ Maven test, đóng gói JAR và dựng classpath smoke với Maven offline/cache hiện có. Các suite cart, checkout,
catalog, auth, Staff scope, CSRF, UI/layout, Payment và inventory đều chạy lại. Không thay exception handler,
SecurityConfig, interceptor, controller, templates, entity/schema, Hibernate config hoặc checkout.

## 23. Payment SUCCESS

Unit + SQL integration xác nhận PAID/CONFIRMED, một system history và policy cause PAYMENT_SUCCESS. Không đổi stock/movement
hoặc snapshot. SQL test thêm duplicate SUCCESS sau PREPARING: receipt không đổi, history không thêm, Order không rewind.
Live HTTP qua bản JAR dev cổng 18081: PASS amount, SUCCESS audit fields, isolation, duplicate, ownership, CSRF và offline
methods. Dùng form/endpoint hiện hữu, không thêm route state-changing.

## 24. Payment FAILED

Unit + SQL integration xác nhận FAILED/CANCELLED, policy cause PAYMENT_FAILURE, hoàn kho đúng từng item, một system
history, không restore lần hai. Toàn bộ test Phase 9.1 giữ PASS. Live HTTP FAILED: PASS restore mọi item, CANCEL_ORDER,
history, duplicate, terminal guards và role guard.

## 25. Test mới

**158 test mới Phase 9.2**, đều PASS:

| Suite mới | Tests |
|---|---:|
| OrderTransitionPolicyTest | 129 (128 cặp enum và null context) |
| OrderServiceTest | 5 |
| OrderStateDatabaseIntegrationTest | 24 |

Mỗi test cặp enum cũng kiểm cả ba cause để bảo vệ payment-only edge. Không sửa kỳ vọng test cũ để che regression.

## 26. Tổng test

**436 tests / 27 suites: 435 PASS, 0 FAIL, 0 ERROR, 1 SKIP.** Baseline Phase 9.1: 278 tests (277 PASS, 1 SKIP).
Skip là `CatalogHttpDatabaseIntegrationTest.imageUploadReachesCloudinaryServiceThroughTheMultipartFormWithCsrf`,
test upload Cloudinary có điều kiện hiện hữu, không phải SQL Server/state machine. Các test Phase 9.1 giữ
11 Payment unit + 4 restore unit + 6 Payment web + 31 Payment database = 52/52 PASS.
Surefire XML và `target-phase9_2-regression.log` ghi kết quả; các artifact/log local được gitignore.

## 27. SQL Server thật

24 test state/history và 31 test Payment database chạy profile dev với datasource SQL Server thật và schema validate,
không dùng H2, không mock transaction/lock. Các test database khác cũng chạy trong full regression khi có cấu hình local.
Cases C–F của request kiểm bằng service integration: hai kiểu CONFIRMED → PREPARING hợp lệ; DELIVERY → READY_FOR_PICKUP
và STORE_PICKUP → PACKED/SHIPPING bị từ chối. Không tạo public endpoint chỉ để test.

## 28. Cleanup

Mỗi test state/history chụp toàn bộ row của 9 bảng checkout_sessions, orders, order_items, payments, inventory_movements,
order_status_history, store_products, cart_items, carts. Sau SeedGuard.restore, assertion đối chiếu toàn bộ row,
id, quantity, snapshot và timestamp với trước test; 24/24 PASS. Fixture chỉ tạo Order mới, seed Order không bị sửa.
SQL Server identity counter có thể tăng do insert/rollback; không reseed identity.
Live smoke cũng so sánh toàn bộ row trước/sau và phục hồi trong finally: **PASS exact rows**.

| Bảng | Trước smoke | Sau cleanup |
|---|---:|---:|
| checkout_sessions | 3 | 3 |
| orders | 5 | 5 |
| order_items | 7 | 7 |
| payments | 5 | 5 |
| inventory_movements | 9 | 9 |
| order_status_history | 16 | 16 |
| store_products | 21 | 21 |
| cart_items | 3 | 3 |
| carts | 2 | 2 |

Assertion kiểm toàn bộ dữ liệu và timestamp, không chỉ số lượng row. Smoke log: `target/phase9_2-live-smoke.log`.
Bản JAR dev dùng cho kiểm chứng được dừng sau smoke; không để tiến trình test chạy nền.

## 29. Convention cho phase sau

Mọi chuyển Order status phải qua OrderService và policy chung. Ngoại lệ duy nhất là Checkout khởi tạo Order/history
initial. Không rải setter trong controller/service fulfillment. Orchestration chịu role, Store assignment tin cậy,
payment guard và side effects; state/history primitive tham gia cùng transaction. Không dùng request actor/store/status
để bỏ qua quyền. PaymentService giữ Payment và Order.payment_status. Thứ tự khóa giữ Order trước stock nếu action cần stock.
Policy graph hợp lệ chưa đủ chứng minh action nghiệp vụ được phép.

## 30. Để Phase 9.3

Production action DELIVERY, xác thực Staff/Store scope cho từng action, giao diện/queue đơn, orchestration chuẩn bị/
đóng gói/giao hàng/hoàn tất và thu COD theo roadmap. Không có generic production completeOrder hoặc cancelOrder ở Phase 9.2.
COMPLETED chỉ có rule; không tuyên bố đã hoàn thành lifecycle hoặc collect COD.

## 31. Để Phase 9.4

Production action STORE_PICKUP, pickup_code duy nhất theo phạm vi nghiệp vụ, ready_at, picked_up_at, xác nhận nhận hàng,
thu PAY_AT_STORE và payment guards. PREPARING → READY_FOR_PICKUP và READY_FOR_PICKUP → COMPLETED chỉ có rule ở foundation.
Không tạo hoặc chỉnh pickup field trong state machine. Không tạo production Staff READY_FOR_PICKUP action.

Phase 9.5 và các phase tiếp theo chưa được triển khai.
