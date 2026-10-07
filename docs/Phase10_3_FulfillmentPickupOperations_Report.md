# Phase 10.3 – Fulfillment + Pickup Operations

Trạng thái: **PHASE 10.3 DONE**, kiểm chứng ngày 06/10/2026. Dừng trước Phase 10.4.

## Page / endpoint / action

| Endpoint | Hành vi |
| --- | --- |
| `GET /staff/orders/{orderId}` | Tái sử dụng Order Detail 10.2, thêm duy nhất action tiếp theo hợp lệ do service fulfillment Phase 9 chọn. |
| `GET /staff/pickup?page=0` | Pickup Queue vận hành, chỉ assigned Stores + STORE_PICKUP + CONFIRMED/PREPARING/READY_FOR_PICKUP. |
| `POST /staff/orders/{orderId}/delivery/prepare` | CONFIRMED → PREPARING. |
| `POST /staff/orders/{orderId}/delivery/pack` | PREPARING → PACKED. |
| `POST /staff/orders/{orderId}/delivery/ship` | PACKED → SHIPPING. |
| `POST /staff/orders/{orderId}/delivery/complete` | SHIPPING → COMPLETED, thu COD theo logic Phase 9 nếu cần. |
| `POST /staff/orders/{orderId}/pickup/prepare` | CONFIRMED → PREPARING. |
| `POST /staff/orders/{orderId}/pickup/ready` | PREPARING → READY_FOR_PICKUP, sinh code/ready_at bằng Phase 9. |
| `POST /staff/orders/{orderId}/pickup/complete` | Nhận pickupCode khách cung cấp, xác minh và hoàn tất theo payment rule Phase 9. |

POST thành công redirect về cùng Order Detail 10.2. Không có generic target-status endpoint, không bind Store/actor/payment
từ request. Tất cả form dùng CSRF, kể cả Bearer hoặc path percent-encoded. GET mutation URL trả 405.
Sidebar thêm Hàng chờ nhận; giữ nguyên link/URL Delivery/Pickup Phase 9, dùng Thymeleaf + Bootstrap + SiteMesh hiện có.

## Tái sử dụng rule và Store scope

`StaffOperationsService` gọi `StaffStoreScopeService` nguyên trạng để resolve authenticated ACTIVE STAFF từ SecurityContext
→ ACTIVE assignments → ACTIVE assigned Stores. Mutation chạy write transaction: xác thực scope trước resource lookup,
khóa Order theo PK bằng repository lock hiện có, rồi `requireAssignedStore` kiểm actual Store của locked Order **trước mọi write**.
Staff email truyền xuống service Phase 9 lấy từ SecurityContext; request không thể cấp Store hoặc actor khác.
Cross-Store mutation trả 403, missing resource trả 404; không ACTIVE assignment trả 403/Service AccessDenied.
Hỗ trợ nhiều ACTIVE assignment và resolve lại sau revocation.

Sau guard, wrapper delegate fixed action sang `DeliveryFulfillmentService` hoặc `PickupFulfillmentService` Phase 9.
Các service này vẫn kiểm expected state, fulfillment, payment và authenticated actor, rồi gọi `OrderService.transition`
để dùng graph/history hiện có. `OrderService`, `OrderTransitionPolicy`, `PaymentService`, `PickupCodeService`, entity và schema
không sửa. Không thêm state hoặc copy transition/payment/code rules sang Controller/JavaScript.
Wrapper và Phase 9 cùng REQUIRED transaction; giữ lock tới commit/rollback.

Hai interface fulfillment thêm `getNextAction(Order)` cho authorized Order, tái sử dụng selector hiện có để Order Detail 10.2
render action mà không query/lắp lại full detail. Pickup selector còn dùng chung guard duplicate receipt của mutation
để ẩn action khi PAY_AT_STORE đã có SUCCESS receipt không nhất quán. UI chỉ là hint; POST vẫn recheck rules.

## Pickup Queue / verification / payment

Repository query trực tiếp bằng assigned Store ids + fulfillment + ba open states, sort createdAt DESC/id DESC,
20 đơn/trang và total cùng scope; fetch Store/User. Không load toàn hệ thống rồi lọc UI.
Queue dùng bảng/link Order Detail 10.2, có mã đơn/Store/khách hàng/fulfillment/payment status/order status/tiền/ngày tạo;
không trả stored pickup_code. PENDING_PAYMENT, COMPLETED, CANCELLED và Delivery không nằm trong queue.

Form nhập mã chỉ xuất hiện ở READY_FOR_PICKUP và payment hợp lệ; không autofill/reveal stored code.
`PickupCodeService.verify` hiện có normalize/constant-time compare, hỗ trợ code 8 ký tự và legacy 6 theo chính Order.
Mã sai/missing/malformed không ghi Payment, COMPLETED, picked_up_at hoặc history.

Mã đúng gọi flow Phase 9: ONLINE phải PAID; PAY_AT_STORE phải UNPAID và chưa có SUCCESS receipt.
`PaymentService.recordPayAtStoreCollected` ghi SUCCESS + PAID, amount từ snapshot tổng đơn, **trước** Order completion.
Sau đó ghi picked_up_at và transition/history trong cùng transaction; ready_at/code không đổi.
Form nhắc Staff chỉ xác nhận sau khi đã thu đủ tiền; không thêm payment endpoint hoặc cờ PAID từ request.
Repeat completion/receipt không được thu lần nữa. COD ở Delivery completion giữ đúng rule thu tiền khi giao thành công.

Không sửa Stock, thêm Stock Adjustment/Inventory History/Admin/notification/courier hoặc domain/schema mới.

## File thêm / sửa

Java dưới `src/main/java/com/oneshop/`; templates dưới `src/main/resources/templates/`.

| Loại | File |
| --- | --- |
| Thêm Controller | `controller/staff/StaffPickupQueueController.java` |
| Thêm template | `staff/pickup/queue.html` |
| Thêm test | `src/test/java/com/oneshop/StaffFulfillmentOperationsDatabaseIntegrationTest.java` |
| Thêm report | `docs/Phase10_3_FulfillmentPickupOperations_Report.md` |
| Sửa orchestration | `service/StaffOperationsService.java` |
| Sửa Controller / DTO | `controller/staff/StaffOrderController.java`, `dto/response/StaffOrderDetailResponse.java` |
| Sửa query | `repository/OrderRepository.java` |
| Sửa view selector API | `service/DeliveryFulfillmentService.java`, `service/PickupFulfillmentService.java` |
| Sửa view selector implementation | `service/impl/DeliveryFulfillmentServiceImpl.java`, `service/impl/PickupFulfillmentServiceImpl.java` |
| Sửa CSRF coverage | `config/SecurityConfig.java` (thêm matcher Staff Order forms, giữ Auth/JWT/roles) |
| Sửa UI | `staff/orders/detail.html`, `fragments/staff.html` |
| Sửa test fixture/assertion | `src/test/java/com/oneshop/AbstractIntegrationTest.java`, `src/test/java/com/oneshop/StaffOperationsDatabaseIntegrationTest.java` |
| Sửa tài liệu | `README.md` |

Test 10.2 giữ toàn bộ assertions về resource/snapshot/privacy; cập nhật assertion không có action trên Detail vì 10.3
đã nối valid completion form. Queue 10.2 vẫn chỉ đọc. Layout/auth tests không dùng DB có mock read model cho Pickup Queue.

## Kiểm thử và kết quả

36 test mới dùng Tomcat/JWT/CSRF/SiteMesh/Service/Repository/SQL Server thật, không mock state machine/payment/authorization.

| Yêu cầu bắt buộc | Kết quả |
| --- | --- |
| 1. Delivery đúng đủ chain | PASS: COD và ONLINE qua từng POST mới, UI từng bước, receipt/history/actor chính xác. |
| 2. Pickup đúng đủ chain | PASS: PAY_AT_STORE và ONLINE, code sinh ở READY, verify trước completion. |
| 3. Transition sai/nhảy trạng thái | PASS: pack/ship/complete từ CONFIRMED, ready/complete quá sớm, cross-flow, duplicate completion; exact snapshot không đổi. |
| 4. Cross-Store update bị chặn | PASS: cả 7 POST với giả mạo Store/staff/payment/target status đều 403, không write. |
| 5. Queue đúng fulfillment/Store/state | PASS: mixed states/Stores/Delivery, exact ids/order/total hai trang, page âm/trống, cookie/query store_id không mở rộng scope. |
| 6. Mã sai không hoàn tất | PASS: null/empty/wrong/malformed/valid-format wrong code; receipt/timestamps/history/stock không đổi. |
| 7. Mã đúng hoàn tất theo rule | PASS: customer thấy code của mình, Staff không thấy; trim/lowercase normalize đúng, completion PAID. |
| 8. PAY_AT_STORE không vượt payment rule | PASS: trước completion UNPAID/no receipt; sau completion một SUCCESS receipt đúng total/PAID; prior PAID/duplicate receipt bị từ chối. |
| 9. Timestamps/history đúng | PASS: ready_at/picked_up_at/payment time, giữ code/ready_at, từng old/new state + authenticated actor; repeat không ghi thêm. |
| 10. Regression Phase 1–10.2 | PASS: full 823 tests, 0 failure, 0 error; 822 passed, 1 conditional Cloudinary skip có sẵn. |

Còn kiểm CSRF với cookie/Bearer/encoded path; Customer/Admin/no assignment; missing Order; nhiều assignment và revoke
Store B chặn ngay mutation tiếp theo với JWT cũ; ba rollback cases qua wrapper mới; race hai pickup completion chỉ
một receipt/một completion. Fulfillment actions đối chiếu tồn kho/movements/cart/checkout/items không đổi.

Fixtures dùng Checkout hiện có; cleanup sau từng test so sánh exact rows/fields/timestamps chín bảng business,
User metadata/Store/assignments và products; không reseed IDENTITY. Cleanup PASS.

Lệnh đã chạy:

```powershell
mvn -o '-Dmaven.repo.local=C:\Users\Admin\.m2\repository' '-Dtest=StaffFulfillmentOperationsDatabaseIntegrationTest,StaffOperationsDatabaseIntegrationTest,LayoutsIntegrationTest,AccessControlIntegrationTest' test
mvn -o '-Dmaven.repo.local=C:\Users\Admin\.m2\repository' package
```

Focused run: **87/87 PASS** (35 test mới trước khi thêm assignment-revocation case).
Final full package: **823 tests / 0 failure / 0 error / 1 skipped, BUILD SUCCESS**, 40 suites;
36/36 test mới, 23/23 test 10.2 và 17/17 test 10.1 PASS. JAR `target/oneshop.jar`.
Skip là test Cloudinary unconfigured-case có `assumeFalse(isConfigured)` để tránh upload thật khi đã có cấu hình;
không có test 10.3/scope bị skip. Full regression đầu phát hiện link Pickup sidebar cũ bị thay; đã khôi phục link cũ,
thêm link queue riêng và final full run PASS, không bỏ test hoặc nới assertion sidebar cũ.

Evidence local: `target-phase10_3-focused.log`, `target-phase10_3-regression.log`, Surefire XML.
Không chạy standalone verifiers cũ; các integration tests dùng embedded Tomcat thật + SQL Server thật.
Không commit, không tự chuyển Phase 10.4.
