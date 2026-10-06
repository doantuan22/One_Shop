# OneShop

**Xây dựng website bán mỹ phẩm OneShop theo mô hình chuỗi cửa hàng.**

Giai đoạn hiện tại: **Phase 9.5 – Integration + Verification**. **PHASE 9 – PAYMENT + FULFILLMENT COMPLETED** (Roadmap V2). Các phần đã triển khai:

* **Phase 9.5 – Integration + Verification**: TC-15 full E2E ba Store với DELIVERY/COD, PICKUP/ONLINE và
  PICKUP/PAY_AT_STORE; payment failure chỉ hủy/hoàn kho target Order; ownership, Staff scope, CSRF,
  snapshot, concurrency và rollback trên SQL Server thật. Không thêm nghiệp vụ hoặc sửa schema.

* **Phase 9.4 – STORE_PICKUP Flow**: Staff chuẩn bị, sinh mã khi READY và xác minh mã khi khách nhận;
  PAY_AT_STORE thu tiền nguyên tử cùng completion/history; Customer xem snapshot, timeline và mã của đơn mình.

* **Phase 9.3 – DELIVERY Flow**: Staff của assigned Store xử lý chuẩn bị/đóng gói/giao/hoàn tất đơn DELIVERY;
  ONLINE cần PAID, COD được ghi nhận SUCCESS + PAID khi giao thành công, nguyên tử với trạng thái và history.

* **Phase 9.2 – Order State Machine + History Foundation**: policy chung cho DELIVERY/STORE_PICKUP; primitive nội bộ
  khóa Order, kiểm trạng thái kỳ vọng, cập nhật trạng thái và ghi history nguyên tử. Payment tái sử dụng foundation này.

* **Phase 9.1 – Payment Foundation**: Payment ONLINE theo từng Order; tạo attempt PENDING; SUCCESS xác nhận đơn;
  FAILED hủy đúng đơn, hoàn tồn và ghi audit trong một transaction. COD/PAY_AT_STORE khởi tạo UNPAID.

* **Phase 8 – Checkout + Orders**: từ các dòng giỏ đã chọn tạo một CheckoutSession và mỗi chi nhánh một Order, lưu
  snapshot OrderItem, trừ tồn kho có khóa và ghi biến động kho, tất cả trong một transaction.

* **Phase 7 – Cart nhiều Store**: thêm / sửa số lượng / xóa trong giỏ, giỏ nhóm theo chi nhánh, chọn cả chi nhánh
  hoặc từng sản phẩm để chuyển sang bước đặt hàng.
* **Phase 6 – Catalog + Store chain**: Admin quản lý Danh mục, Thương hiệu, Sản phẩm/SKU, Chi nhánh, Sản phẩm theo
  chi nhánh và ảnh (Cloudinary); Client có Hệ thống cửa hàng, chọn chi nhánh, catalog toàn chuỗi / theo chi nhánh và
  trang chi tiết sản phẩm với giá, tình trạng hàng theo từng chi nhánh.

* **Phase 3 – Spring Boot foundation**: 19 Entity JPA khớp schema `database/*.sql` (Phase 2), 19 Repository (kèm
  `PESSIMISTIC_WRITE` cho `StoreProduct`), khung 11 Service, Controller tách `client/staff/admin`, DTO + validation.
* **Phase 4 – SiteMesh + UI nền**: 3 layout riêng cho Client, Staff, Admin và bộ component Bootstrap dùng lại.
* **Phase 5 – Auth + JWT**: đăng ký/đăng nhập/đăng xuất, JWT trong cookie HttpOnly, phân quyền CUSTOMER/STAFF/ADMIN ở
  backend, Store scope của Staff theo `StaffStoreAssignment` ACTIVE, CSRF.

Phase 9 đã hoàn tất. Staff Store Operations đầy đủ của Phase 10 chưa được triển khai.

### Kiểm chứng tích hợp và đóng Phase 9 (Phase 9.5)

CheckoutSession chỉ nhóm Orders, không aggregate status sau payment/fulfillment. Mỗi Order giữ đúng Store,
payment method, fulfillment, receipt và timeline riêng. TC-15 kiểm đến kết quả cuối cả ba đơn, đồng thời
kiểm mixed failure với một Order CANCELLED/FAILED và hai sibling tiếp tục COMPLETED/PAID.

`Phase9IntegrationWebDatabaseTest` dùng Tomcat/Security/controllers/services/repositories/SQL Server thật,
không mock repository hoặc transaction. Có 19 test mới, gồm exact sibling/stock/history isolation, catalog snapshot,
matrix business + DB CHECK, scope/ownership/CSRF, bốn rollback representative, năm race cùng Order,
hai Order cùng checkout commit độc lập và kiểm số query list/detail. Test cũ giữ nguyên.

Standalone verifier chạy trên JAR dev riêng, sau ba verifier payment/delivery/pickup cũ:

```powershell
mvn package dependency:build-classpath '-Dmdep.outputFile=target/payment-classpath.txt'
java -jar target/oneshop.jar --spring.profiles.active=dev --server.port=18081
# Terminal khác, sau khi app sẵn sàng; dùng DB dev không có writer khác:
$phase9Classpath = 'target/test-classes;target/classes;' + (Get-Content target/payment-classpath.txt -Raw).Trim()
java -cp $phase9Classpath com.oneshop.PaymentLiveSmoke http://localhost:18081
java -cp $phase9Classpath com.oneshop.DeliveryLiveSmoke http://localhost:18081
java -cp $phase9Classpath com.oneshop.PickupLiveSmoke http://localhost:18081
java -cp $phase9Classpath com.oneshop.Phase9IntegrationLiveSmoke http://localhost:18081
```

Verifiers cleanup trong finally và so sánh exact rows, không reseed IDENTITY. Dừng app thử sau khi chạy.
Kết quả/audit và ranh giới Phase 10: [Báo cáo Phase 9.5](docs/Phase9_5_IntegrationVerification_Report.md).

### Nhận tại cửa hàng (Phase 9.4)

Customer mở **Đơn hàng của tôi** (`GET /orders`) và `GET /orders/{orderId}`. Backend dùng principal và ownership DB,
không tin userId hoặc selected Store. Chi tiết dùng OrderItem snapshot, Store/address, payment/status và history theo
changed_at/id. Mã chỉ xuất hiện khi READY_FOR_PICKUP hoặc COMPLETED; mã của đơn đã hoàn tất không dùng lại được.
Customer không có action nhận hàng. Checkout result có link sang chi tiết từng Order.

Staff mở **Đơn nhận tại cửa hàng** (`GET /staff/orders/pickup`) và chi tiết `/{orderId}`. List chỉ STORE_PICKUP thuộc
ACTIVE assignments/Stores của ACTIVE Staff; hỗ trợ nhiều Store. Staff nhập mã khách cung cấp, không nhìn thấy mã lưu DB.

| Action POST | Expected | Target |
|---|---|---|
| `/staff/orders/pickup/{orderId}/prepare` | CONFIRMED | PREPARING |
| `/staff/orders/pickup/{orderId}/ready` | PREPARING | READY_FOR_PICKUP |
| `/staff/orders/pickup/{orderId}/complete` | READY_FOR_PICKUP | COMPLETED |

* `PickupFulfillmentService` khóa PK Order trước, kiểm scope/actor, STORE_PICKUP, method/payment và trạng thái cố định;
  gọi `OrderService.transition` foundation 9.2 cho status/history. Không nhận targetStatus, storeId, money hoặc timestamps.
* ONLINE cần PAID ở mọi bước. PAY_AT_STORE cần UNPAID và chưa có receipt SUCCESS; không repair dữ liệu inconsistent.
  COD và DELIVERY bị chặn. Stock, movements, OrderItem, Store và CheckoutSession giữ nguyên trong fulfillment.
* READY sinh mã **8 ký tự** uppercase từ `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` bằng SecureRandom (40 bit ngẫu nhiên).
  Mã và ready_at giờ server chính xác giây được set **trước** flush READY để giữ CHECK hiện hữu. Không đổi schema/constraints.
  Action trùng trả 400 sau lock, không regenerate mã hoặc rewrite ready_at.
* Verification chỉ dùng code của Order đang khóa và thuộc assigned Store. Trim ngoài, normalize uppercase,
  so sánh String bằng MessageDigest.isEqual; không parse integer, log, đưa code vào URL hoặc error. Mã seed cũ 6 ký tự
  A-Z/0-9 được chấp nhận chỉ khi chính Order đó giữ format này, không rewrite code cũ. Không global lookup/UNIQUE;
  cặp Order ID + code đã có scope là định danh nhận hàng. Không SMS/email/QR hoặc external service.
* Complete form chỉ gửi pickupCode và CSRF. Với PAY_AT_STORE, Staff chỉ bấm xác nhận sau khi đã thu đủ tổng tiền.
  Mã sai/malformed trả 400 an toàn, không ghi dữ liệu. Mã đúng và guards hợp lệ: PaymentService.recordPayAtStoreCollected
  (MANDATORY) ghi một SUCCESS, amount = Order.total_amount snapshot, PAY-UUID, paid_at server và PAID; đặt picked_up_at;
  foundation chuyển COMPLETED và ghi Staff history. Tất cả cùng transaction REQUIRED, không REQUIRES_NEW.
* ONLINE complete chỉ đặt picked_up_at, status/history; giữ nguyên receipt/code/paid_at. Lỗi ghi rollback toàn bộ.
  Order lock serialize READY/complete: một winner, request còn lại reject; không double collect/history.
* POST pickup cần STAFF, assigned Store và CSRF, kể cả Bearer hoặc encoded path (parsed path matcher).
  ADMIN/CUSTOMER không có quyền. Wrong-scope detail 404, mutation 403, business/duplicate 400, DB/lock 409 an toàn.
  GET action trả 405; không generic transition endpoint. UI chỉ đủ demo flow, chưa làm full Phase 10.

Audit, kết quả SQL/HTTP/concurrency/rollback và cleanup: [Báo cáo Phase 9.4](docs/Phase9_4_StorePickupFlow_Report.md).

### Giao hàng theo chi nhánh (Phase 9.3)

Staff mở **Đơn giao hàng** trên menu tại `/staff/orders/delivery`, rồi mở `/staff/orders/delivery/{orderId}`.
Danh sách chỉ có DELIVERY thuộc các Store ACTIVE được phân công ACTIVE; không nhận Store scope từ query/cookie/header.
Chi tiết hiển thị người nhận, địa chỉ, tổng tiền, snapshot sản phẩm, thanh toán và history. GET không thay đổi dữ liệu.

| Action POST | Trạng thái kỳ vọng | Trạng thái đích |
|---|---|---|
| `/staff/orders/delivery/{orderId}/prepare` | CONFIRMED | PREPARING |
| `/staff/orders/delivery/{orderId}/pack` | PREPARING | PACKED |
| `/staff/orders/delivery/{orderId}/ship` | PACKED | SHIPPING |
| `/staff/orders/delivery/{orderId}/complete` | SHIPPING | COMPLETED |

* `DeliveryFulfillmentService` kiểm quyền, flow và payment guards, khóa Order theo PK bằng PESSIMISTIC_WRITE,
  rồi gọi State Machine Phase 9.2 với expected/target cố định. Actor là User Staff từ principal, không từ body.
  `StoreService.requireAssignedStore` kiểm lại Store của Order ở backend; hỗ trợ nhiều assignment như Phase 5.
* Chỉ ONLINE/COD + DELIVERY dùng các action này. ONLINE phải PAID ở mọi bước; PENDING_PAYMENT hoặc CONFIRMED + UNPAID
  bị từ chối. COD phải UNPAID trước completion. STORE_PICKUP, PAY_AT_STORE và terminal state đều bị chặn.
* COD không tạo Payment ở prepare/pack/ship. Khi Staff xác nhận đã giao hàng và thu tiền, PaymentService ghi một
  Payment COD SUCCESS với amount = Order.total_amount, paid_at = giờ server độ chính xác giây, code `PAY-<UUID>`.
  Payment, Order.payment_status = PAID, SHIPPING → COMPLETED và Staff history cùng transaction.
  Lỗi Payment/transition/history rollback toàn bộ. ONLINE completion giữ nguyên Payment đã SUCCESS, code và paid_at.
* Double click/race cùng action: một thành công, request còn lại trả 400 sau khóa; không thêm history/receipt lần hai.
  Đã có receipt SUCCESS dù COD Order vẫn UNPAID thì từ chối thu lại, không tự sửa dữ liệu lỗi.
* UI chỉ hiển thị action kế tiếp hợp lệ theo state/payment. Backend vẫn kiểm đầy đủ; không nhận targetStatus, actor,
  amount, method hoặc code từ form. Các POST yêu cầu STAFF + assignment + CSRF, kể cả Bearer và URL percent-encoded;
  matcher dùng parsed path như Spring MVC. ADMIN/CUSTOMER không có quyền.
  Wrong-scope detail/unknown Order trả 404, mutation sai scope trả 403, guard sai trả 400, lỗi lock/database trả 409 thân thiện.
* Fulfillment không đổi tồn, không tạo InventoryMovement, không sửa snapshot, Store, phương thức hoặc CheckoutSession.
  Giá/tên catalog đổi sau mua không làm thay đổi chi tiết hay amount thu COD.
* SHIPPING là trạng thái nội bộ. Chưa tích hợp vận chuyển, gateway, cancel/return/refund hoặc dashboard/stock UI của Phase 10.

Audit, kiểm thử và cleanup: [Báo cáo Phase 9.3](docs/Phase9_3_DeliveryFlow_Report.md).

### State machine và history nội bộ (Phase 9.2)

`OrderTransitionPolicy` là nguồn luật chung; `canTransition(current, target, fulfillment)` mô tả graph,
overload nhận `Cause` phân biệt fulfillment với PAYMENT_SUCCESS/PAYMENT_FAILURE. Không dùng string để so sánh trạng thái.

| Trạng thái hiện tại | DELIVERY: đích hợp lệ | STORE_PICKUP: đích hợp lệ |
|---|---|---|
| PENDING_PAYMENT | CONFIRMED (Payment SUCCESS); CANCELLED (Payment FAILED) | CONFIRMED (Payment SUCCESS); CANCELLED (Payment FAILED) |
| CONFIRMED | PREPARING | PREPARING |
| PREPARING | PACKED | READY_FOR_PICKUP |
| PACKED | SHIPPING | Không có |
| SHIPPING | COMPLETED | Không có |
| READY_FOR_PICKUP | Không có | COMPLETED |
| COMPLETED | Không có | Không có |
| CANCELLED | Không có | Không có |

* `OrderService.transition(orderId, expectedStatus, targetStatus, actor, note)` là primitive Java nội bộ,
  không có endpoint/UI. Nó khóa đúng Order bằng truy vấn PK `PESSIMISTIC_WRITE`, đọc trạng thái trong transaction,
  từ chối trạng thái kỳ vọng đã cũ và cạnh không hợp lệ, flush Order rồi ghi history trong cùng transaction.
  Same-state, backward, skip-state, sai nhánh và mọi cạnh ra khỏi terminal trả `BadRequestException`, không ghi history.
* Primitive fulfillment không được dùng hai cạnh từ PENDING_PAYMENT. Hai helper payment `MANDATORY` tái sử dụng
  policy/history và yêu cầu caller PaymentService giữ khóa Order. PaymentService quản lý `Payment.status` và
  `Order.payment_status`; FAILED vẫn hoàn kho trước khi hủy trong cùng transaction, theo thứ tự khóa Order rồi StoreProduct.
* History lưu old/new, actor do orchestration đã xác thực cung cấp (`NULL` nếu hệ thống), thời điểm do JPA auditing,
  note tùy chọn tối đa 500 đơn vị UTF-16 để khớp NVARCHAR(500). Caller chỉ cung cấp nội dung audit an toàn,
  không token, secret hoặc dữ liệu nhạy cảm. Note không quyết định luật. History tạo đơn NULL → initial state ở Checkout giữ nguyên.
* Foundation chỉ đổi trạng thái và history. Caller phải kiểm role/Staff Store assignment, quyền truy cập,
  payment guard và thực hiện side effects trong transaction bao ngoài trước khi dùng primitive.
  Policy không truy vấn assignment và không xử lý thanh toán, kho, mã pickup hay thời điểm nhận hàng.
* READY_FOR_PICKUP/COMPLETED được mô hình hóa trong graph. Phase 9.3 đã có action hoàn tất DELIVERY và ghi nhận thu COD.
  Phase 9.4 bổ sung action production pickup, mã, timestamps và thu PAY_AT_STORE qua orchestration riêng.
  Schema vẫn yêu cầu mã và ready_at khi vào READY_FOR_PICKUP;
  graph hợp lệ không thay thế các điều kiện của schema hoặc orchestration ở phase sau.
* Concurrent duplicate bị từ chối sau khi khóa được giải phóng, đúng một history. Payment duplicate giữ cơ chế
  idempotent của Phase 9.1; SUCCESS lặp sau khi fulfillment tiến lên vẫn trả receipt cũ, không kéo trạng thái về CONFIRMED.

Ma trận đầy đủ, kiểm thử SQL Server, cleanup và phạm vi phase sau: [Báo cáo Phase 9.2](docs/Phase9_2_OrderStateMachine_Report.md).

### Thanh toán nội bộ theo Order (Phase 9.1)

* Từ `/checkout/{id}`, mở **Xem thanh toán trực tuyến** của từng Order ONLINE. `GET /orders/{orderId}/payments`
  hiển thị Order, chi nhánh, tổng tiền, trạng thái và các Payment attempt. Chỉ CUSTOMER sở hữu Order được truy cập;
  sai ownership/Order/Payment id trả 404. GET không tạo hay thay đổi Payment.
* `POST /orders/{orderId}/payments/attempts` bắt đầu thanh toán: Order phải ONLINE, PENDING_PAYMENT + UNPAID.
  Amount và method lấy từ Order trong DB; request không nhận owner, amount, method, status hay transaction code.
  PENDING có `paid_at=NULL`, `transaction_code=NULL`. Bấm nhiều lần trả lại attempt PENDING hiện có.
* Đây là **workflow mô phỏng nội bộ, không thu tiền thật**. Hai form CSRF gọi riêng
  `POST /orders/{orderId}/payments/{paymentId}/success` và `/failure`. Backend vẫn kiểm ownership, Payment thuộc
  đúng Order, method/amount khớp và state guard. Các route này luôn cần CSRF, kể cả khi có Bearer header.
* SUCCESS: Payment SUCCESS, `paid_at` là giờ server hiện tại (độ chính xác giây); Order PAID + CONFIRMED.
  Không trừ kho thêm và không sửa OrderItem. FAILED: Payment FAILED, `paid_at=NULL`; Order FAILED + CANCELLED;
  hoàn số lượng snapshot của mỗi OrderItem về đúng StoreProduct, ghi một CANCEL_ORDER movement dương mỗi item.
* `transaction_code` chỉ sinh khi nhận kết quả, cho cả SUCCESS/FAILED: `PAY-<UUID>` do server sinh, phục vụ audit/demo,
  không phải mã giao dịch gateway. Schema hiện tại không có UNIQUE cho mã này; UUID cung cấp độ duy nhất thực tế.
* Quan hệ **Order 1–N Payment** giữ nguyên. Phase 9.1 chỉ có một attempt đang chờ trên luồng ứng dụng; FAILED là
  kết quả cuối, hủy và hoàn tồn ngay nên **không retry đơn đã CANCELLED**. Muốn mua lại phải tạo Order mới.
* Mọi thao tác ghi khóa Order bằng PESSIMISTIC_WRITE trước khi đọc Payment. Hoàn tồn khóa các StoreProduct theo id
  tăng dần như checkout. Payment, Order, tồn kho, InventoryMovement và OrderStatusHistory cùng transaction;
  lỗi bất kỳ bước nào rollback toàn bộ. Kết quả trùng trả lại Payment đã kết thúc, không ghi audit/hoàn kho lần nữa;
  đổi SUCCESS thành FAILED hoặc ngược lại bị từ chối.
* History do payment chỉ thêm PENDING_PAYMENT → CONFIRMED/CANCELLED; `changed_by_user_id=NULL` vì hệ thống đổi trạng thái từ
  kết quả thanh toán, nhất quán history tạo đơn Phase 8. Movement tự động có `staff_id=NULL`.
* COD/PAY_AT_STORE khởi tạo CONFIRMED + UNPAID; ONLINE workflow không tạo SUCCESS cho hai method này.
  COD thu tiền qua completion DELIVERY Phase 9.3; PAY_AT_STORE/pickup thuộc Phase 9.4. CheckoutSession
  vẫn chỉ nhóm Order; kết quả một Order không đổi Order khác hoặc status của CheckoutSession trong phase này.
* Không sửa schema, enum hay cấu hình Hibernate. Profile dev vẫn `ddl-auto=validate`; prod giữ cấu hình hiện có.

Kiểm chứng và danh sách file: [Báo cáo Phase 9.1](docs/Phase9_1_PaymentFoundation_Report.md).

### Đặt hàng (Phase 8)

* `GET /checkout?cartItemIds=…` hiện trang đặt hàng: mỗi chi nhánh một khối, tự chọn cách nhận (giao hàng / nhận tại
  cửa hàng) và thanh toán (COD, tại cửa hàng, trực tuyến) riêng. `POST /checkout` đặt hàng; `GET /checkout/{id}` xem
  kết quả. Chỉ `CUSTOMER`.
* Request chỉ mang `cartItemIds` và lựa chọn của từng chi nhánh. Chủ đơn lấy từ người đăng nhập; chi nhánh của đơn
  lấy từ StoreProduct của các dòng giỏ; giá, thành tiền, trạng thái do máy chủ tính.
* Trong **một transaction**: khóa giỏ của khách, khóa các StoreProduct (`PESSIMISTIC_WRITE`, theo id tăng dần), đọc lại
  giá / tồn / trạng thái, kiểm tra mọi dòng và mọi chi nhánh, rồi tạo CheckoutSession, Order, OrderItem (snapshot tên,
  đơn giá, số lượng, thành tiền), trừ tồn kèm `InventoryMovement` loại `ORDER`, xóa đúng các dòng giỏ đã đặt. Một chỗ
  không hợp lệ thì không có gì được tạo.
* Trạng thái đầu của Order: thanh toán `ONLINE` → `PENDING_PAYMENT`; `COD` và `PAY_AT_STORE` → `CONFIRMED`; tất cả
  `UNPAID`. Checkout không tạo dòng `payments`; Phase 9.1 tạo Payment khi khách bắt đầu thanh toán.
  Chưa có mã nhận hàng (phần STORE_PICKUP ở phase sau).
* Giao hàng chỉ đi với COD hoặc trực tuyến; nhận tại cửa hàng chỉ đi với thanh toán tại cửa hàng hoặc trực tuyến; chi
  nhánh không bật giao hàng / nhận tại cửa hàng thì không chọn được cách đó.

### Giỏ hàng nhiều chi nhánh (Phase 7)

* Mỗi dòng giỏ gắn với một **StoreProduct** (SKU tại một chi nhánh), không gắn với Product. Cùng một SKU ở hai chi
  nhánh là hai dòng riêng; thêm lại đúng StoreProduct đã có thì cộng số lượng vào dòng cũ.
* Giỏ thuộc về khách đang đăng nhập (lấy từ JWT); không request nào nhận `userId`, giá hay thành tiền. Id dòng giỏ
  không thuộc giỏ của mình được coi như không tồn tại (404).
* Giỏ **chỉ kiểm tra** tồn kho mỗi lần thêm / sửa, không giữ hàng, không trừ kho, không ghi `InventoryMovement`. Bước
  đặt hàng (Phase 8) sẽ kiểm tra lại và mới trừ kho.
* Trang `/cart` nhóm theo chi nhánh, giá là giá hiện tại của StoreProduct (không lưu trong giỏ), không hiển thị số tồn.
  Dòng không còn mua được (hết hàng, thiếu hàng, ngừng bán) vẫn nằm trong giỏ, được đánh dấu và không chọn được.
* Checkbox "Chọn cả chi nhánh" / từng dòng chỉ là trạng thái trên trang; bấm tiếp tục gửi danh sách `cartItemIds`
  tới `GET /checkout`, nơi máy chủ kiểm tra lại và hiển thị các dòng đã chọn theo chi nhánh. Chưa tạo đơn.
* Đổi "Chi nhánh đang chọn" không làm thay đổi giỏ hàng.

### Catalog và mô hình chuỗi (Phase 6)

* **Product = một SKU** dùng chung toàn chuỗi, không có giá hay tồn kho. **StoreProduct** (SKU tại một chi nhánh) là
  nguồn duy nhất của giá, tồn kho, trạng thái bán. Một SKU được coi là *đang bán* tại một chi nhánh khi StoreProduct
  `ACTIVE`, Store `ACTIVE`, Product `ACTIVE` và Danh mục/Thương hiệu của nó `ACTIVE`.
* **Chi nhánh đang chọn** lưu trong cookie `ONESHOP_STORE` (HttpOnly, SameSite=Lax; ứng dụng không dùng session).
  `SelectedStoreInterceptor` kiểm tra id với database ở mỗi request; id không tồn tại, không phải số hoặc của chi nhánh
  đã ngừng hoạt động thì cookie bị xóa và khách quay về "Toàn chuỗi". Đổi chi nhánh chỉ đổi ngữ cảnh duyệt, không động
  tới giỏ hàng. Chọn bằng `POST /stores/select` (có CSRF), bỏ chọn bằng `POST /stores/clear`.
* **Catalog** (`/products`): chưa chọn chi nhánh thì mỗi SKU hiện một lần kèm các chi nhánh đang bán; đã chọn thì chỉ
  hiện StoreProduct của chi nhánh đó với giá của chính chi nhánh đó. Tìm theo tên/SKU, lọc Danh mục, Thương hiệu.
* **Khách chỉ thấy Còn hàng / Hết hàng**; số tồn chính xác chỉ có ở trang Admin.
* **Admin** (`/admin/categories`, `/admin/brands`, `/admin/products`, `/admin/stores`, `/admin/store-products`): thêm,
  sửa, đổi trạng thái; không có xóa cứng. Mỗi lần đổi tồn kho đều ghi một `InventoryMovement` loại `STOCK_ADJUST`.
* **Ảnh**: tải lên từ trang sửa Sản phẩm / Thương hiệu, lưu trên Cloudinary; database chỉ giữ URL và `public_id`.
  Cần đặt `CLOUDINARY_*`, nếu không trang sẽ báo lỗi và không lưu gì.

## 1. Công nghệ sử dụng

| Thành phần | Công nghệ |
|---|---|
| Ngôn ngữ / Build | Java 21 (LTS), Maven (kèm Maven Wrapper) |
| Framework | Spring Boot 3.5.x, Spring MVC |
| View | Thymeleaf, Bootstrap 5.3 (đặt sẵn trong `static/vendor`), HTML5, CSS, JavaScript |
| Layout | SiteMesh 3 (Decorator) |
| Dữ liệu | Spring Data JPA (Hibernate), **Microsoft SQL Server** |
| Bảo mật | Spring Security 6, JWT (jjwt), BCrypt |
| Ảnh | Cloudinary |
| Triển khai | Docker, Render; SQL Server đặt trên cloud |

## 2. Kiến trúc

```text
Browser -> Thymeleaf + Bootstrap -> Controller -> Service -> Repository (Spring Data JPA) -> SQL Server
```

* Controller **không** truy cập Repository trực tiếp; luôn đi qua Service.
* `controller/web`: trang Thymeleaf. `controller/api`: REST/JSON (`/api/**`, `/health`).
* **SiteMesh**: mọi trang HTML được render bình thường, sau đó SiteMesh gói vào layout của khu vực (cấu hình ở
  `SiteMeshConfig`):

  | URL | Layout | Nội dung |
  |---|---|---|
  | `/staff`, `/staff/**` | `layouts/staff.html` | Top bar + sidebar; khu vực **Chi nhánh được phân công** |
  | `/admin`, `/admin/**` | `layouts/admin.html` | Top bar + sidebar quản trị, phạm vi **Toàn chuỗi** |
  | còn lại | `layouts/client.html` | Header (**Chi nhánh đang chọn** / **Toàn chuỗi**), navbar, footer |

  `DecoratorController` (`/decorators/client|staff|admin`) là cầu nối giữa SiteMesh và Thymeleaf; các đường dẫn này
  không thể gọi trực tiếp từ bên ngoài. Trang con chỉ cần viết `<title>` và `<body>`, không cần khai báo layout.
  Sidebar của Staff/Admin thu thành menu off-canvas trên màn hình nhỏ.
* **Ngữ cảnh Store trong layout** chỉ là lớp hiển thị. Layout Client đọc model attribute tùy chọn `selectedStore`
  (`StoreResponse`, vắng mặt = "Toàn chuỗi"; Phase 6 cấp dữ liệu). Layout Staff đọc `assignedStores`
  (`List<StoreResponse>`, rỗng = "Chưa được phân công") do `StaffStoreScopeInterceptor` đặt ở mỗi request.
* **Component UI** trong `templates/fragments/components.html` (dùng bằng `th:replace`): `pageHeader`, `navItem`, `card`,
  `statCard`, `dataTable` + `emptyRow`, `emptyState`, `formField`, `formSelect`, `modal`. Ví dụ dùng đủ bộ nằm ở
  `templates/admin/dashboard.html`; `staff/dashboard.html` là trang mẫu của Staff.
* **JWT**:
  * Luồng web (Thymeleaf): `POST /login` cấp JWT và lưu trong cookie **HttpOnly**, `SameSite=Lax`
    (`Secure` khi chạy HTTPS). Form được bảo vệ CSRF.
  * Luồng API: `POST /api/auth/login` trả `accessToken`; gửi lại bằng header `Authorization: Bearer <token>`.
  * `JwtAuthenticationFilter` chấp nhận cả hai nguồn; session là `STATELESS`.
* **Phân quyền** (quyết định ở backend trong `SecurityConfig`, không dựa vào việc ẩn menu):

  | Đường dẫn | Ai được vào |
  |---|---|
  | `/`, `/login`, `/register`, `/products/**`, `/stores/**`, `/health` | Mọi người |
  | `/cart/**` | `CUSTOMER` |
  | `/staff/**`, `/api/staff/**` | `STAFF` (và phải có Store scope, xem dưới) |
  | `/admin/**`, `/api/admin/**` | `ADMIN` |
  | còn lại | Đã đăng nhập |

  Chưa đăng nhập: trang web chuyển về `/login`, API trả 401. Sai role: 403. Role được đọc lại từ database ở mỗi
  request (không tin role trong token), nên tài khoản bị khóa hoặc đổi role mất quyền ngay.
* **Store scope của Staff (BR-14)**: `StaffStoreScopeInterceptor` chạy trước mọi controller của `/staff/**` và
  `/api/staff/**`, lấy danh sách Store từ `staff_store_assignments` (assignment `ACTIVE`, Store `ACTIVE`, tài khoản
  `STAFF` `ACTIVE`) theo email của người đang đăng nhập và đặt vào request attribute `assignedStores`. Store không bao
  giờ lấy từ tham số, header hay token. Staff chưa có assignment hợp lệ chỉ vào được trang `/staff` (hiện thông báo),
  mọi URL Staff khác trả 403. Service xử lý dữ liệu của một Store cụ thể gọi `StoreService.requireAssignedStore`.
* **CSRF**: bật cho mọi request thay đổi dữ liệu đi bằng cookie (form Thymeleaf tự chèn `_csrf`; đăng xuất là
  `POST /logout`). Chỉ bỏ qua cho `/api/auth/**` và request mang `Authorization: Bearer` (trình duyệt không tự gửi được).
  Token không bị đổi ở mỗi request, nên form trên trang mở từ trước (tab khác, nút Back) vẫn gửi được.
* **Đăng ký** luôn tạo tài khoản `CUSTOMER`; tài khoản Staff/Admin không tạo được qua form. Sau đăng nhập, mỗi role
  được chuyển về khu vực của mình (`/`, `/staff`, `/admin`).
* **Ảnh**: `CloudinaryService` upload/xóa ảnh; database chỉ lưu `imageUrl` và `imagePublicId`, không lưu dữ liệu ảnh.

## 3. Yêu cầu môi trường

* JDK 21 (hoặc 17)
* Git
* Một SQL Server truy cập được (cloud hoặc local) và tài khoản Cloudinary
* Không cần cài Maven: dùng `./mvnw` (Windows: `mvnw.cmd`)

## 4. Cấu hình

Ứng dụng đọc cấu hình từ **biến môi trường**; không có thông tin bí mật nào nằm trong source code.
Khi chạy development, có thể tạo file `.env` ở thư mục gốc (đã được `.gitignore`):

```bash
cp .env.example .env    # rồi điền giá trị thật
```

Profile `dev` tự nạp `.env` (nếu có). Trên Render, đặt biến trong dashboard.

### Database (SQL Server)

```text
jdbc:sqlserver://${DB_HOST}:${DB_PORT};databaseName=${DB_NAME};encrypt=true;trustServerCertificate=false
```

* Kết nối luôn mã hóa (`encrypt=true`), phù hợp SQL Server cloud.
* SQL Server local dùng chứng chỉ tự ký: đặt `DB_TRUST_SERVER_CERTIFICATE=true` (chỉ cho development).
* Schema do `database/*.sql` (Phase 2, 19 bảng) quản lý; chạy các script đó trước khi khởi động app.
  `dev`: `ddl-auto=validate` (khởi động thất bại nếu entity lệch schema). `prod`: `ddl-auto=none`.
  Hibernate không bao giờ tự tạo/sửa schema.
* Chuỗi tiếng Việt được lưu bằng `NVARCHAR` (`hibernate.use_nationalized_character_data=true`); cột thời gian là
  `DATETIME2` theo giờ máy chủ SQL (`SYSDATETIME()`), ánh xạ bằng `LocalDateTime`.
* Test đọc dữ liệu seed thật (`DatabaseFoundationIntegrationTest`) tự chạy khi có `.env` hoặc biến `DB_USERNAME`,
  nếu không sẽ bị bỏ qua.
* Ứng dụng vẫn khởi động được khi chưa kết nối được database; chỉ các trang cần dữ liệu mới báo lỗi.

### JWT

* `JWT_SECRET`: chuỗi bí mật **tối thiểu 32 ký tự**, ví dụ `openssl rand -base64 48`.
  Thiếu hoặc quá ngắn thì ứng dụng từ chối khởi động (có chủ đích).
* `JWT_EXPIRATION_MINUTES` (mặc định 60), `JWT_COOKIE_SECURE` (`false` ở dev, `true` ở prod).

### Cloudinary

Đặt `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`. Nếu chưa đặt, ứng dụng vẫn chạy
nhưng `CloudinaryService` báo lỗi rõ ràng khi được gọi.

## 5. Chạy và build

```bash
# chạy development (profile dev)
./mvnw spring-boot:run

# chạy test
./mvnw test

# build JAR (target/oneshop.jar)
./mvnw clean package
```

Mở http://localhost:8080. Các route mẫu: `/`, `/login`, `/register`, `/products`, `/products/{id}`, `/stores`, `/cart` (Customer), `/health`, `/staff` (Staff), `/admin` (Admin).

Các test profile `test` dùng secret giả và mock các bean truy cập DB. Khi có `.env` hoặc `DB_USERNAME`, các integration
test database chạy profile `dev` với SQL Server thật và `ddl-auto=validate`; fixture được khôi phục sau test.

## 6. Triển khai lên Render

1. Đẩy source lên GitHub.
2. Trên Render chọn **New > Blueprint** và trỏ tới repository (dùng `render.yaml`), hoặc tạo **Web Service** kiểu
   Docker với `Dockerfile` ở thư mục gốc.
3. Đặt các biến môi trường trong bảng bên dưới (các biến bí mật để `sync: false`, nhập tay trên dashboard).
4. Health check path: `/health` (không cần đăng nhập, không phụ thuộc database).

`Dockerfile` build nhiều giai đoạn (Maven build, sau đó chạy JRE), chạy bằng user không phải root, đọc cổng từ `PORT`
(mặc định 8080) và dùng profile `prod`. SQL Server nằm ngoài Render, ứng dụng chỉ kết nối qua biến môi trường.
Nếu nhà cung cấp SQL Server có tường lửa IP, cần cho phép địa chỉ outbound của Render.

## 7. Biến môi trường

| Biến | Bắt buộc | Mô tả |
|---|---|---|
| `DB_HOST` | Có | Host SQL Server |
| `DB_PORT` | Không (1433) | Cổng |
| `DB_NAME` | Có | Tên database |
| `DB_USERNAME` | Có | Tài khoản |
| `DB_PASSWORD` | Có | Mật khẩu |
| `JWT_SECRET` | Có | Khóa ký JWT, tối thiểu 32 ký tự |
| `CLOUDINARY_CLOUD_NAME` | Khi dùng ảnh | Cloud name |
| `CLOUDINARY_API_KEY` | Khi dùng ảnh | API key |
| `CLOUDINARY_API_SECRET` | Khi dùng ảnh | API secret |
| `PORT` | Không (8080) | Cổng HTTP (Render tự đặt) |
| `SPRING_PROFILES_ACTIVE` | Không (`dev`) | `dev` hoặc `prod` (Docker mặc định `prod`) |
| `DB_TRUST_SERVER_CERTIFICATE` | Không (`false`) | `true` chỉ cho SQL Server local chứng chỉ tự ký |
| `JPA_DDL_AUTO` | Không (`none` ở prod) | `none`, `validate`, `update` |
| `JWT_EXPIRATION_MINUTES` | Không (60) | Thời hạn token |
| `JWT_COOKIE_SECURE` | Không | Cờ `Secure` của cookie JWT |
| `DB_POOL_SIZE`, `DB_CONNECTION_TIMEOUT_MS` | Không | Tinh chỉnh connection pool |

## 8. Cấu trúc thư mục

```text
oneshop/
├── pom.xml, mvnw, mvnw.cmd, .mvn/
├── Dockerfile, render.yaml, .env.example
└── src/
    ├── main/
    │   ├── java/com/oneshop/
    │   │   ├── OneShopApplication.java
    │   │   ├── config/        Security, SiteMesh, Cloudinary, Web, JPA, properties
    │   │   ├── controller/
    │   │   │   ├── client/    Trang Thymeleaf của khách (home, products, cart, login/register)
    │   │   │   ├── staff/     Khu vực /staff/** (trang mẫu Phase 4; nghiệp vụ Phase 10)
    │   │   │   ├── admin/     Khu vực /admin/** (trang mẫu Phase 4; nghiệp vụ Phase 6/11)
    │   │   │   ├── web/       DecoratorController (cầu nối SiteMesh - Thymeleaf)
    │   │   │   └── api/       REST: /api/auth/**, /health
    │   │   ├── service/       Interface; impl/ chứa cài đặt
    │   │   ├── repository/    Spring Data JPA
    │   │   ├── entity/        19 entity theo schema V2 + enum trạng thái
    │   │   ├── dto/           request/, response/
    │   │   ├── security/      jwt/ (JwtService, filter, cookie), service/ (UserDetailsService),
    │   │   │                  StaffStoreScopeInterceptor (Store scope của Staff)
    │   │   ├── exception/     GlobalExceptionHandler, WebExceptionHandler, ...
    │   │   ├── mapper/
    │   └── resources/
    │       ├── templates/     layouts/ (client, staff, admin), fragments/ (header, navbar, footer, staff, admin,
    │       │                  assets, components), home/, auth/, product/, cart/, staff/, admin/,
    │       │                  order/, user/ (đang trống), error.html
    │       ├── static/        css/, js/, images/, vendor/bootstrap/
    │       └── application.properties, application-dev.properties, application-prod.properties
    └── test/                  Test tích hợp (Tomcat thật), JWT, Cloudinary
```
