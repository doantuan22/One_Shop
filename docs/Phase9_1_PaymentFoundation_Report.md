# Phase 9.1 – Payment Foundation

Trạng thái: **COMPLETED**, kiểm chứng ngày 06/10/2026. Dừng trước Phase 9.2.

## 1. Kết quả audit trước khi code

Đã đọc toàn bộ hai tài liệu nguồn `OneShop_Roadmap_KyThuat_V2_KhaoSatChuoiCuaHang (1).md` và
`OneShop_PhanChiaGiaiDoan_ThucHien_TheoRoadmapV2.md`. Đối chiếu mục 6.4, 6.7, 7.3, 8.1–8.2,
BR-08/10/12/13/18 và Phase 9. Audit Entity/Repository/Service, security, checkout/controller/template và test Phase 8.

Schema `payments` đã có payment_id/order_id/method/amount/status/transaction_code/paid_at/created_at,
FK tới Order và CHECK SUCCESS cần paid_at. Entity Payment là ManyToOne Order, Order.payments là OneToMany.
Không có Payment DTO/endpoint/workflow trước phase này; PaymentService và OrderService còn khung.

Phase 8 đúng: ONLINE = PENDING_PAYMENT + UNPAID; COD/PAY_AT_STORE = CONFIRMED + UNPAID. Checkout không tạo Payment,
đã trừ kho và ghi ORDER movement, snapshot OrderItem và history tạo đơn. InventoryService đã có adjustStock và deductForOrder;
StoreProductRepository có write lock một/nhiều row và query id tăng dần. OrderRepository chưa có write lock.

Roadmap 6.6 mô tả pickup từ CONFIRMED; ONLINE pickup phải trải qua thanh toán PENDING_PAYMENT theo mục 6.4 trước.
Không hiểu mục 6.6 là bỏ qua thanh toán ONLINE. README còn nhắc `/cart/selection` đã cũ; code Phase 8 dùng `/checkout`,
đã cập nhật mô tả. Các lệch khác ngoài payment không mở rộng xử lý trong phase này.

## 2. Phần đã có sẵn và giữ nguyên

Giữ nguyên schema 19 bảng, Entity và enum; CheckoutService/CartService và snapshot; JWT/role/Staff scope hiện có;
deductForOrder/adjustStock; các test cũ. Không đổi Hibernate: dev validate, prod giữ cấu hình hiện có (none mặc định).

## 3. Phần triển khai mới

PaymentService có getOrderPayments/createOnlinePaymentAttempt/markOnlinePaymentSuccess/markOnlinePaymentFailed.
Thêm Order write lock, truy vấn Payment theo Order, response DTO, hai primitive OrderService, restore kho có audit,
Client controller/view và kiểm thử. Controller không truy cập Repository; các rule và transaction nằm trong Service.

## 4. File thêm/sửa/xóa

**Thêm (10 file):**

- `src/main/java/com/oneshop/controller/client/PaymentController.java`
- `src/main/java/com/oneshop/dto/response/PaymentResponse.java`
- `src/main/java/com/oneshop/dto/response/OrderPaymentResponse.java`
- `src/main/resources/templates/payment/index.html`
- `src/test/java/com/oneshop/service/PaymentServiceTest.java`
- `src/test/java/com/oneshop/service/InventoryRestoreTest.java`
- `src/test/java/com/oneshop/PaymentDatabaseIntegrationTest.java`
- `src/test/java/com/oneshop/PaymentWebIntegrationTest.java`
- `src/test/java/com/oneshop/PaymentLiveSmoke.java`
- `docs/Phase9_1_PaymentFoundation_Report.md`

**Sửa (12 file):**

- `README.md`
- `src/main/java/com/oneshop/config/SecurityConfig.java`
- `src/main/java/com/oneshop/repository/OrderRepository.java`
- `src/main/java/com/oneshop/repository/PaymentRepository.java`
- `src/main/java/com/oneshop/service/PaymentService.java`
- `src/main/java/com/oneshop/service/OrderService.java`
- `src/main/java/com/oneshop/service/InventoryService.java`
- `src/main/java/com/oneshop/service/impl/PaymentServiceImpl.java`
- `src/main/java/com/oneshop/service/impl/OrderServiceImpl.java`
- `src/main/java/com/oneshop/service/impl/InventoryServiceImpl.java`
- `src/main/resources/templates/checkout/result.html`
- `src/test/java/com/oneshop/SeedGuard.java`

**Xóa:** không có. SQL/schema và hai tài liệu nguồn không sửa. SeedGuard bổ sung khôi phục timestamps và cart mới để
test commit/HTTP không để lại thay đổi trên dữ liệu dev.

## 5. Payment model thực tế

`CheckoutSession 1–N Order 1–N Payment`. Payment độc lập từng Order; CheckoutSession không chứa nguồn thanh toán
và status của nó không đổi bởi kết quả một Payment trong 9.1. View hiển thị được danh sách attempt, không đổi quan hệ thành 1–1.

## 6. Khi nào tạo Payment PENDING

Chỉ POST `/orders/{orderId}/payments/attempts` khi Customer sở hữu Order bấm bắt đầu. GET/checkout không tạo attempt.
Order phải ONLINE, PENDING_PAYMENT, UNPAID. Amount = Order.totalAmount và method = Order.paymentMethod từ DB.
Không có request DTO nhận amount/method/owner/status/code/paidAt; các field giả bị bỏ qua. PENDING có paidAt/code NULL.

## 7. Convention nhiều Payment attempt

Giữ khả năng 1–N ở schema/mapping. Workflow hiện tại trả lại attempt PENDING đang có khi bấm nhiều lần,
kể cả hai request đồng thời. FAILED hủy/hoàn tồn ngay và kết thúc Order; không retry Order CANCELLED.
Khách muốn mua lại tạo Order mới. Không tự bổ sung abandoned attempt/retry/timeout.

## 8. Convention transaction_code

Sinh phía server khi ghi SUCCESS hoặc FAILED: `PAY-<UUID>`. Không lấy dữ liệu client, không chứa secret,
không phải transaction của gateway. PENDING giữ NULL. Schema không có UNIQUE; độ duy nhất dùng UUID ngẫu nhiên.

## 9. ONLINE SUCCESS

Khóa Order, kiểm owner và Payment thuộc Order, method/amount, Payment PENDING và Order payable.
Payment SUCCESS + paidAt giờ server (chính xác giây) + code; OrderService đổi Order thành PAID + CONFIRMED và ghi history.
Không gọi InventoryService, không trừ tồn thêm, không ghi ORDER movement mới, không thay OrderItem.

## 10. ONLINE FAILED

Cùng guards, Payment FAILED + paidAt NULL + code; InventoryService hoàn tồn; OrderService đổi FAILED + CANCELLED
và ghi history. Toàn bộ cùng transaction PaymentService; bất kỳ lỗi restore/history/constraint làm rollback.

## 11. Cách restore stock

`restoreForCancelledOrder` đọc OrderItem snapshot, lấy đúng store_product_id/quantity, khóa kho rồi cộng trả mỗi item.
Không dùng adjustStock. Kiểm đủ StoreProduct, đúng Store của Order, quantity dương và không overflow int.
Entity bị INACTIVE vẫn được hoàn tồn vì đây là dữ liệu giao dịch đã có, không phải thao tác mở bán.

## 12. InventoryMovement CANCEL_ORDER

Một movement mỗi OrderItem: StoreProduct đúng, type CANCEL_ORDER, change dương bằng item.quantity;
before/after lấy dưới lock; reference_order_id = Order, staff_id NULL, created_at qua JPA auditing.
History/stock/movement giữ cùng transaction, không có commit riêng.

## 13. Locking khi restore

Thứ tự: khóa Order theo PK → đọc Payment → load items → khóa toàn bộ StoreProduct bằng PESSIMISTIC_WRITE,
id tăng dần như Phase 8 → restore/audit → commit. Không đọc quantity trước khi khóa.
Order lock serialize attempt/result của cùng Order; stock locks bảo vệ cả các Order khác nhau có chung kho.

## 14. Chống double-processing

Sau Order lock mới đọc Payment. Cùng kết quả đã kết thúc trả lại record đã có, không ghi thêm và không đổi timestamps.
Kết quả trái nhau bị từ chối; PENDING của Order không còn payable bị từ chối. Idempotency terminal còn kiểm
trạng thái Order tương ứng CONFIRMED/PAID hoặc CANCELLED/FAILED trong phạm vi 9.1.

## 15. OrderStatusHistory

Giữ entry tạo đơn `NULL → initial`. Chỉ thêm PENDING_PAYMENT → CONFIRMED/CANCELLED.
changed_by_user_id NULL vì đây là transition hệ thống từ Payment result, dù Customer kích hoạt mô phỏng;
nhất quán convention Phase 8. Repeat không thêm history. Hai helper dùng Propagation.MANDATORY.

## 16. Ranh giới Phase 9.2

OrderService hiện chỉ có confirmAfterOnlinePayment/cancelAfterOnlinePaymentFailure và helper transition riêng.
Chưa có matrix tổng quát, fulfillment, pickup code/timestamps, Staff queue, cancel chủ động hay gateway.
Phase 9.2 có thể mở rộng validator/history helper nhưng phải giữ lock/transaction và ownership của PaymentService.

## 17. Test SUCCESS

PASS unit + SQL Server + HTTP: fields/audit, CONFIRMED/PAID, stock và movement không đổi, snapshot giữ nguyên,
mixed checkout độc lập, duplicate và terminal guards. Trang thật hiển thị Payment/code/paidAt và trạng thái đúng.

## 18. Test FAILED

PASS: FAILED/CANCELLED, order.payment_status FAILED, paidAt NULL, mọi item hoàn đúng kho;
CANCEL_ORDER change dương, before/after/reference/staff/createdAt đúng; history và duplicate đúng.

## 19. Atomic rollback

SQL Server thật: Order có hai item; tiêm lỗi save CANCEL_ORDER thứ hai. Item thứ nhất được persist và entityManager.flush
để stock/movement/Payment FAILED thực sự được viết trong transaction, rồi item thứ hai lỗi.
Sau rollback: Payment PENDING + code/paidAt NULL; Order PENDING_PAYMENT/UNPAID; stock, counts và audit đúng giá trị trước;
không movement/history partial. Bỏ lỗi, chạy lại FAILED thành công. Test lỗi history ở cả SUCCESS/FAILED cũng PASS.

## 20. Concurrency/idempotency

SQL Server thật PASS: hai request SUCCESS cùng Payment, hai FAILED cùng Payment (mỗi tình huống lặp 3 vòng);
chỉ một transition/history và một bộ restore. Hai lần start tạo một PENDING. SUCCESS/FAILED race có một winner,
request còn lại bị state guard từ chối. Hai Order khác nhau cùng hoàn về hai StoreProduct chung không mất lượt hoàn.

## 21. COD hiện tại

CONFIRMED + UNPAID. Không tự tạo SUCCESS/PAID/COMPLETED, flow ONLINE bị từ chối; sẽ ghi nhận thu tiền ở phase delivery sau.

## 22. PAY_AT_STORE hiện tại

CONFIRMED + UNPAID, chỉ STORE_PICKUP như rule Phase 8. Không pickup_code/readyAt/pickedUpAt/COMPLETED hay SUCCESS tự động;
flow ONLINE bị từ chối. Thu tiền tại pickup thuộc phase sau.

## 23. Tổng test PASS/FAIL/SKIP

Báo cáo Surefire cuối: **278 tests = 277 PASS, 0 FAIL/ERROR, 1 SKIP**, 24 suites.
Phase 9.1: **52/52 PASS**, không skip (PaymentServiceTest 11, InventoryRestoreTest 4,
PaymentWebIntegrationTest 6, PaymentDatabaseIntegrationTest 31).

Full regression/package chạy 277 tests trước khi thêm case khác Order/chung kho; sau đó chạy lại toàn bộ
PaymentDatabaseIntegrationTest (31/31 PASS). Tổng cuối ở trên là tổng các báo cáo cuối, không phải cộng các lần chạy lặp.
Skip cũ thuộc CatalogHttpDatabaseIntegrationTest: Cloudinary đã cấu hình nên test không thực hiện upload thật
(`Assumption failed: Cloudinary is configured: not uploading from a test`). Không có test DB Payment bị bỏ qua.

Maven Wrapper trên Windows gặp `Cannot index into a null array`; dùng Maven đã cài sẵn, offline dependency cache.
Sandbox chặn javac đọc JAR ngoài workspace; các lệnh build/test được chạy qua escalation đã chấp thuận tự động.
Không sửa wrapper/pom vì ngoài phạm vi. `git diff --check` PASS.

## 24. App thật với SQL Server

Build JAR thành công; chạy riêng `java -jar target/oneshop.jar --spring.profiles.active=dev --server.port=18081`,
Hibernate validate với SQL Server thật. `PaymentLiveSmoke` gọi HTTP/JDBC độc lập, không mock/context Spring test.

PASS SUCCESS/FAILED, amount giả, audit/history, stock/movement, mixed methods, double start/result,
ownership, CSRF, terminal guards, COD/PAY_AT_STORE, Staff/Admin. Đã dừng cả launcher và process Java con thuộc app thử.

Các lệnh có thể dùng trên môi trường đã cấu hình (chọn `mvnw.cmd` hoặc Maven sẵn có):

```powershell
mvn test
mvn package dependency:build-classpath '-Dmdep.outputFile=target/payment-classpath.txt'
# Chạy app dev ở terminal riêng, rồi kiểm chứng trên DB dev seed không có writer khác:
java -jar target/oneshop.jar --spring.profiles.active=dev --server.port=18081
$paymentSmokeClasspath = 'target/test-classes;target/classes;' + (Get-Content target/payment-classpath.txt -Raw).Trim()
java -cp $paymentSmokeClasspath com.oneshop.PaymentLiveSmoke http://localhost:18081
```

## 25. Cleanup

Standalone smoke đã cleanup và so sánh **toàn bộ dòng** của các bảng chạm tới, gồm stock/cart timestamps, đúng trước/sau:

| Bảng | Trước | Sau |
| --- | ---: | ---: |
| checkout_sessions | 3 | 3 |
| orders | 5 | 5 |
| order_items | 7 | 7 |
| payments | 5 | 5 |
| inventory_movements | 9 | 9 |
| order_status_history | 16 | 16 |
| store_products | 21 | 21 |
| cart_items | 3 | 3 |
| carts | 2 | 2 |

Automated test commit dùng SeedGuard trước/sau từng case. Không reseed IDENTITY; khoảng trống ID do tạo/xóa test là bình thường.

## 26. Các convention tự chọn

FAILED là final/no retry; PENDING được reuse; code UUID tạo ở cả hai kết quả; paidAt dùng giờ server chính xác giây;
history/movement tự động dùng actor NULL; cùng result idempotent trả record, trái result trả 400;
owner/id sai trả 404; lock/constraint trên payment controller trả 409 với thông báo thân thiện không lộ SQL/stack trace;
mọi Client payment POST bắt buộc CSRF kể cả Bearer; CheckoutSession status giữ nguyên.
Tất cả đã mô tả trong README. ONLINE demo không thu tiền thật, không giả gateway.

## 27. Điểm cần giữ/chốt trước Phase 9.2

- Validator tổng quát phải giữ payment gate: ONLINE chưa PAID không được đi fulfillment; không mở retry Order CANCELLED.
- Khi Order đã đi tiếp sau CONFIRMED, cần mở rộng kiểm tra Order cho SUCCESS lặp mà vẫn tuyệt đối không lặp history/stock.
- Giữ system actor NULL cho các transition tự động; Staff/Customer transition mới phải xác định actor theo context thật.
- Giữ quy ước Order lock trước stock lock và cùng transaction cho mọi nghiệp vụ cancel/restore bổ sung.
- Chưa có expiry/abandon cho PENDING; nếu muốn thêm phải chốt rule/phạm vi Roadmap trước, không tự triển khai ở 9.1.

Không có vấn đề còn chặn Definition of Done Phase 9.1. Không tự chuyển sang Phase 9.2.
