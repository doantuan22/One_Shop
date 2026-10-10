# SQL Server Integrity, Performance & Concurrency Audit

Ngày audit: 09/10/2026 (Asia/Saigon, UTC+7). Commit: `88c22132955b9c7a88be5a7a6ea08ff167d83776`. **AUDIT ONLY**; không remediation, thay dependency/schema/test, reseed hay deploy.

Kiểm chứng trên Windows, Java 21, Maven 3.9.16, Spring Boot 3.5.16, SQL Server `localhost:1433/oneshop`, Standard Developer Edition 17.0.1000.7. Server audit chỉ bind `127.0.0.1:8787`, profile dev; Chrome headless 155 qua CDP `127.0.0.1:9224`. Hai tiến trình do audit tạo đã dừng.

Các mutation dùng test fixture/SeedGuard hiện hữu hoặc fixture mới mang marker `AUD261009FSA`, ba Store mới và hai User mới. Không dùng stock/cart/order hiện hữu cho hành trình ghi dữ liệu. Cleanup chỉ nhắm fixture; [db-comparison.json](evidence/db-comparison.json) xác nhận 19/19 bảng giữ nguyên số hàng và hash toàn bộ nội dung, bao gồm IDs/timestamps. IDENTITY có thể tăng do fixture; không reset counter. [source-comparison.json](evidence/source-comparison.json): 353 file tracked nguyên trạng, Git chỉ có thư mục audit mới.

## Schema thực tế / mappings

SQL Server Developer Edition là dev/test đã xác minh, 19 bảng đúng Roadmap §7.2; **141 columns, 26 FK, 40 CHECK, 29 unique indexes (bao gồm 19 PK), 35 indexes tổng**. Mọi FK/CHECK enabled + trusted, FK delete/update NO_ACTION. [mapping-comparison.json](evidence/mapping-comparison.json) đối chiếu đủ141 JPA mapped fields (kể cả inherited timestamps) với141 SQL columns: type/nullability/length/decimal precision-scale đều khớp, không unmapped column. Entity @Table/@Column/@JoinColumn và NVARCHAR/BigDecimal/enum mappings được đối chiếu, Hibernate dev `ddl-auto=validate` startup và DatabaseFoundation tests PASS. Validate không thay thế kiểm constraint nên kiểm actual sys catalog độc lập.

UNIQUE: users.email, products.sku, stores.code, StoreProduct(Store,Product), Cart(user), CartItem(cart,storeProduct), Staff assignment(user,store), cùng các lookup keys. CHECK quantity/price>=0, item qty>0 và subtotal=price×qty, rating1–5, enum status, fulfillment/payment pairs, delivery address, pickup timestamps/code constraints, inventory arithmetic/type/reference/staff, successful payment paidAt. Product=SKU riêng, không ProductVariant; ảnh chỉ URL/public_id, không binary column.

Sáu index non-unique theo Roadmap trên StoreProduct(store/status/quantity), StoreProduct(product/status), Order(store/status/created), Order(user/created), InventoryMovement(StoreProduct/created), History(Order/changed). Actual index columns/filter/uniqueness được lưu đầy đủ ở database-inspection.json, không chỉ đọc script.

| SQL table | JPA entity | Rows gốc trước/sau |
| --- | --- | ---: |
| `brands` | `Brand.java` | 3 |
| `cart_items` | `CartItem.java` | 3 |
| `carts` | `Cart.java` | 2 |
| `categories` | `Category.java` | 3 |
| `checkout_sessions` | `CheckoutSession.java` | 3 |
| `customer_addresses` | `CustomerAddress.java` | 2 |
| `inventory_movements` | `InventoryMovement.java` | 9 |
| `order_items` | `OrderItem.java` | 7 |
| `order_status_history` | `OrderStatusHistory.java` | 16 |
| `orders` | `Order.java` | 5 |
| `payments` | `Payment.java` | 5 |
| `product_images` | `ProductImage.java` | 0 |
| `products` | `Product.java` | 7 |
| `reviews` | `Review.java` | 1 |
| `roles` | `Role.java` | 3 |
| `staff_store_assignments` | `StaffStoreAssignment.java` | 4 |
| `store_products` | `StoreProduct.java` | 21 |
| `stores` | `Store.java` | 5 |
| `users` | `User.java` | 8 |

## Data integrity hiện có

Mười nhóm query độc lập đều **0 violations**: disabled/untrusted constraints; order/session total vs sum children; item Store/subtotal; negative price/stock; duplicate ORDER/CANCEL_ORDER per order/SP; broken history chain; order status vs last history; inventory arithmetic/continuity/last quantity; duplicate primary image; binary image columns. FK enabled/trusted kiểm orphan quan hệ trên dữ liệu hiện hữu.

Current Phase12 `existingSqlDataHasCompleteTimelinesConservedStockAndNoDuplicateOrderMovements` kiểm thêm ledger deduction/restore vs item snapshots và payment method/amount/state/paidAt, với phân biệt legacy offline pending; PASS. Seed Order5 Store5 INACTIVE COMPLETED vẫn tồn tại/customer-admin readable. CheckoutSession dùng làm group/total, không là payment source; source convention giữ CREATED, roadmap §7.3 mô tả các status nhưng không yêu cầu derive aggregate status mỗi transition — không tự phát minh rule mới.

UI fixture: checkout6281 gồm Orders9809/9810/9811, một Store mỗi Order, total474000; extra cancel order9812 total222000. Snapshot9809 giữ unit111000/qty2/subtotal222000 sau SP price222000. Có16 nối tiếp status histories, 3 payment SUCCESS đúng method/amount, 11 movements. Cancel chỉ hoàn819:11→12 một lần. Admin/Staff race tại820:9→20(actorAdmin1)→17(actorStaff270), đúng ledger và stock cuối. 14 assertions trong journey-db-checks.json PASS.

Cleanup không reseed và không xóa historical entity của seed. Hai lần so sánh sau regression và sau browser fixtures đều exact match 19 bảng. Script cleanup audit hard-delete **chỉ dữ liệu mới disposable**, không phải production delete endpoint; mỗi fixture được marker/ownership giới hạn trong transaction, abort nếu lẫn foreign order hoặc unexpected provider asset.

## Transaction / concurrency results

| Scenario yêu cầu | Current execution evidence | Kết quả |
| --- | --- | --- |
| Hai Customer mua SKU cuối | CheckoutConcurrencyDatabaseIntegrationTest.tc07_twoCustomersRacingForTheLastUnitNeverOversell / larger orders | PASS: một commit hợp lệ, không stock âm |
| Lỗi giữa multi-Store checkout | failureAfterTheFirstStoreGroupWasWrittenRollsBackTheWholeCheckout | PASS: session/orders/items/movement/history/cart/stock rollback |
| Payment callback đồng thời | PaymentDatabaseIntegrationTest + Phase9 integration concurrent duplicate/conflicting result methods; Phase12 cancel-payment variants | PASS: current persisted invariant/history verified |
| Duplicate concurrent cancel | Phase12.simultaneousDuplicateHttpCancellationsRestoreExactlyOnce | PASS: một restore/history; UI sequential repeat cũng no-op |
| Cancel vs Staff prepare | Phase12.cancellationRacesSerializePaymentFulfillmentAndInventoryWithoutDuplicateAudit PREPARE case | PASS: thắng hợp lệ CANCELLED hoặc PREPARING, không half-write |
| Cancel vs payment success/failure | Cùng test SUCCESS/FAILURE cases | PASS: late result không thu/hoàn kho lại |
| Concurrent stock adjustments | StaffInventoryDatabaseIntegrationTest.concurrentAdjustmentsKeepSerialBeforeAfterValuesAndOneMovementPerActualChange | PASS: serial counts/movements, không lost audit |
| Admin edit vs Staff adjustment | Phase12.adminStockEditMustLockBeforeLoadingQuantityToPreserveConcurrentMovementLedger; thêm actual two-role HTTP race820 qua browser | PASS: primary load đã lock; UI race9→20→17, cả hai actors đúng |
| Duplicate pickup completion | PickupDatabase/StaffFulfillmentOperations/Phase10 concurrency tests; UI repeat B/C | PASS: một completion/payment/history |
| Exception sau flush | Phase12 cancellationRollsBackRealFlushedPaymentStockOrderAndAuditAndCanRetry 4 cases; failureOnSecondItemRestoration; OrderState history failure | PASS: real SQL flush rồi exception, toàn transaction rollback; retry đúng |

Test spies dùng scheduling/failure injection, không mock persistence outcome hay business state machine. SQL connections/transactions thực, không H2. Không stress không giới hạn. Scenario thắng/thua có timeout; không observed deadlock/transaction blocking bug trong lịch đã chạy, **không bảo đảm mọi interleaving**.

## Query/performance review

Catalog page/StoreProduct/Admin orders/Staff stock/queues dùng SQL predicate, pagination/count và stable tie-breaking IDs; EntityGraph/join fetch và primary-image bulk read tránh tải mỗi ảnh/Store qua query rời trên catalog. Filter không chỉ ở JS/UI. Current pagination regression tạo dataset nhiều hơn20 và kiểm count/page/filter chính xác. Browser current seed nhỏ không chứng minh tính scalable.

Hai giới hạn có bằng chứng source nhưng **chưa measured impact**: CustomerOrderServiceImpl.getOrders→List toàn bộ history khách, OrderRepository.findByUserEmailOrderByCreatedAtDescIdDesc không pageable; AdminOperationsService.getOverview chạy tám count queries mỗi Store (source lines45–52), scale tuyến tính theo Store. Vì chỉ5 seed/8 fixture Store, chưa có bằng chứng chậm đáng kể; không tạo severity/performance finding hoặc đề nghị thêm index dựa trên suy đoán.

OrderDetail history actor có lazy reads theo các actor cần hiển thị; chưa chạy bounded query-count test/execution plan hoặc production-size benchmark. Long catalog/image provider operations trong transaction có thể kéo dài lifetime, nhưng lock wait latency/P95/provider outage/crash-compensation **NOT VERIFIED**. Product image primary concurrency không nằm trong current stock-race suite; no duplicate trên DB hiện có (0 images) không chứng minh race-free. Không thêm schema/index hay chạy extended-event/stress gây tác động DB.
