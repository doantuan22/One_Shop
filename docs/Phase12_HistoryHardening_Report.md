# Phase 12 – History + Hardening

Ngày kiểm chứng: 09/10/2026 (+07:00). Baseline: commit f9fd4fa (Phase 11), workspace sạch trước audit.

**Kết luận: PHASE 12 DONE trong môi trường hiện tại.** TC-01–TC-18 PASS; full clean package có 928 tests, 927 PASS, 0 FAIL, 0 ERROR, 1 conditional SKIP cũ, BUILD SUCCESS. Cả 34 test Phase12 PASS, không skip.

## 1. Audit trước code và phần tái sử dụng

Đã đọc Roadmap V2, toàn bộ TC-01–TC-18, mục 6.7/BR-12 về hủy/hoàn kho và yêu cầu Phase 12. Audit ghi trước khi sửa production: [Phase12_HistoryHardening_Audit.md](Phase12_HistoryHardening_Audit.md).

Baseline chạy lại trước thay đổi: **894 tests, 893 PASS, 0 FAIL, 0 ERROR, 1 Cloudinary conditional SKIP, BUILD SUCCESS**, kết thúc 14:38:46 +07:00.

| Phần đã có ở Phase 1–11 | Evidence và cách tái sử dụng |
| --- | --- |
| Checkout nhiều Store, snapshot và trừ tồn | CheckoutService/InventoryService, StoreProduct PK locks theo ID tăng, một transaction; giữ nguyên implementation. CheckoutDatabase/CheckoutConcurrency/CheckoutHttpDatabaseIntegrationTest |
| State machine và OrderStatusHistory | OrderTransitionPolicy, OrderServiceImpl; checkout ghi entry tạo đơn, mọi transition sau đó qua service. Không có controller tự ghi status |
| Payment failure cancellation | PaymentService khóa Order, hoàn đúng snapshot, CANCEL_ORDER + history trong transaction; giữ nguyên rule/result/idempotency |
| Fulfillment DELIVERY/STORE_PICKUP | Delivery/PickupFulfillmentService dùng state machine, payment collection atomic, code/ready_at/picked_up_at; không sửa chức năng |
| STOCK_ADJUST | InventoryService và StaffInventoryService hiện có; giữ writer, movement, SQL constraints và thứ tự lock |
| Staff scope | Resolver đọc ACTIVE User/Staff/assignment/Store từ DB; Phase 10 giữ nguyên |
| Admin/soft status/lịch sử | CRUD và list/detail Phase 6/11; giữ Product/Store/StoreProduct có lịch sử, filter Order/stock theo Store |
| JWT/CSRF | SecurityConfig, JwtAuthenticationFilter, JwtCookieService; endpoint hủy nằm trong /orders/** đã được bảo vệ |
| Cloudinary | ProductService/CloudinaryService hiện có; SQL URL/public_id; reuse adapter tests Phase 6 và bổ sung verification live |

Hai việc phải sửa/bổ sung: customer chưa có luồng hủy chủ động (TC-12), và Admin stock edit đọc entity trước khi khóa, khiến ledger dùng quantity cũ khi transaction khác commit.

Không thêm schema/table/entity/enum, audit system, Redis/queue, nghiệp vụ kho, refund hoặc module Staff/Admin mới.

## 2. Bug / business rule và cách sửa

### 2.1. TC-12 thiếu customer cancellation

Trước Phase 12, /orders chỉ có reads; CANCELLED chỉ phát sinh khi ONLINE payment thất bại. Roadmap yêu cầu hủy hợp lệ nhưng chưa chỉ rõ ngưỡng. Người dùng xác nhận: **trước PREPARING, chưa thanh toán**.

Rule thực thi:

- DELIVERY + COD hoặc STORE_PICKUP + PAY_AT_STORE: CONFIRMED + UNPAID.
- DELIVERY/STORE_PICKUP + ONLINE: PENDING_PAYMENT + UNPAID.
- PREPARING và các bước sau, PAID/FAILED hoặc fulfillment/payment pairing sai: từ chối trước mutation.
- Chỉ ACTIVE CUSTOMER hủy đơn của mình. Principal là nguồn actor/owner; body không chọn Store/owner/status/quantity/price/amount.
- Đã CANCELLED: idempotent, không ghi lại stock/payment/history/movement/timestamp.
- Không refund. Customer cancellation giữ payment_status UNPAID; pending Payment hiện có chuyển FAILED, có transaction code, paid_at vẫn NULL. Không tạo Payment nếu chưa có attempt.
- Seed cũ có Payment PENDING COD/PAY_AT_STORE, model cho phép và chưa thu tiền. Nếu Order còn CONFIRMED + UNPAID thì hủy được, pending record kết thúc cùng transaction. Hai test riêng kiểm legacy pending offline.
- Receipt SUCCESS, paid_at đã có, method/amount bất nhất: chặn trước side effect.
- Callback SUCCESS/FAILURE sau customer cancellation bị chặn, không thu tiền hoặc hoàn tồn lại.

CustomerOrderService.cancelOrder mở transaction, xác thực customer, khóa Order bằng PK, kiểm ownership/state, kết thúc pending attempts, gọi inventory restore và state machine. State machine dùng cùng status/history writer. Restore giữ primitive và locks hiện có, nhận thêm reason để không ghi nhầm customer cancellation là payment failure.

Endpoint mới: **POST /orders/{orderId}/cancel**. GET /orders/{orderId} hiển thị form có CSRF khi policy cho phép; banner chỉ hiển thị nếu trạng thái thực tế là CANCELLED.

### 2.2. Admin stock edit stale quantity

StoreProductServiceImpl.updateStoreProduct trước đây đọc entity không khóa rồi InventoryService mới khóa cùng row. Persistence context giữ entity đã load; khóa sau đó không refresh quantity.

Test SQL Server có barrier tái hiện trước sửa: transaction khác commit **40 → 44**, Admin set 49 nhưng movement ghi **before=40/change=9**, thay vì **before=44/change=5**. Evidence: adminStockEditMustLockBeforeLoadingQuantityToPreserveConcurrentMovementLedger, target-phase12-stale-read.log, 14:48:05 +07:00: **1 FAIL, 0 ERROR, 0 SKIP**.

Sửa tối thiểu: Admin update dùng findByIdForUpdate ngay lần load đầu. Giữ InventoryService writer và metadata update. Sau sửa test kiểm hai movements nối nhau 40 → 44 → 49, đúng before/change/after và stock cuối.

### 2.3. Chẩn đoán test

Không sửa/xóa test Phase 1–11 để đổi kết quả. Test mới được hoàn thiện đúng nguyên nhân:

- Spy Spring Data delegate repository proxy thật; không callRealMethod trên abstract interface.
- Test hai Store ban đầu quét movement khi transaction khác giữ insert chưa commit; chính assertion query bị READ COMMITTED chặn. Assertion hiện kiểm Order theo PK, transaction thứ nhất vẫn mở khi cancellation thứ hai commit, rồi kiểm ledger sau khi thả transaction thứ nhất. Giữ kiểm independence/stock/history.
- Audit ban đầu áp nhầm rule PENDING_PAYMENT của ONLINE lên pending offline trong seed. Đối chiếu Roadmap/model/seed, kiểm lại theo payment method; giữ consistency checks và thêm test hủy với pending offline.

## 3. File thêm/sửa

| File | Thay đổi |
| --- | --- |
| src/main/java/com/oneshop/controller/client/CustomerOrderController.java | GET canCancel, POST chỉ delegate principal/id |
| src/main/java/com/oneshop/service/CustomerOrderService.java | Contract cancellation |
| src/main/java/com/oneshop/service/impl/CustomerOrderServiceImpl.java | ACTIVE CUSTOMER, ownership, method authorization, Order lock, transaction, idempotency |
| src/main/java/com/oneshop/service/OrderTransitionPolicy.java | Guard threshold; giữ graph/payment/fulfillment cũ |
| src/main/java/com/oneshop/service/OrderService.java | Internal cancellation primitive |
| src/main/java/com/oneshop/service/impl/OrderServiceImpl.java | Customer cancel dùng cùng status/history writer, MANDATORY transaction |
| src/main/java/com/oneshop/service/InventoryService.java | Restore overload có note |
| src/main/java/com/oneshop/service/impl/InventoryServiceImpl.java | Customer reason, giữ restore algorithm/locks/payment-failure note |
| src/main/java/com/oneshop/service/PaymentService.java | Internal pending-attempt closure |
| src/main/java/com/oneshop/service/impl/PaymentServiceImpl.java | Consistency checks và closure cùng transaction |
| src/main/java/com/oneshop/service/impl/StoreProductServiceImpl.java | Lock trước load entity khi Admin update |
| src/main/resources/templates/orders/detail.html | Form hủy và banner theo Thymeleaf/Bootstrap/layout hiện có |
| src/test/java/com/oneshop/Phase12HistoryHardeningDatabaseIntegrationTest.java (mới) | 33 SQL/HTTP/security/rollback/concurrency tests, không conditional skip |
| src/test/java/com/oneshop/Phase12CloudinaryLiveVerification.java (mới) | 1 live HTTP/provider/SQL/cleanup test, explicit opt-in, không mock/skip |
| docs/Phase12_HistoryHardening_Audit.md (mới) | Audit và evidence defect trước sửa |
| docs/Phase12_HistoryHardening_Report.md (mới) | Báo cáo này |
| README.md | Phase 12, rule và lệnh verification |

Không thay đổi database scripts, schema/seed, production Staff scope/fulfillment hoặc test Phase 1–11.

## 4. Bảng TC-01 → TC-18

Evidence là method đã chạy trong full acceptance cuối trên SQL Server thật. HTTP tests dùng Tomcat/controllers/services/JPA production. Class tương ứng file trong src/test/java/com/oneshop; XML tại target/surefire-reports/TEST-com.oneshop.&lt;Class&gt;.xml.

| TC | Yêu cầu Roadmap V2 | Evidence | Trạng thái |
| --- | --- | --- | --- |
| TC-01 | SKU nhiều Store, availability/giá đúng | CatalogDatabaseIntegrationTest.tc01_chainCatalogShowsEachSkuOnceWithPriceAndAvailabilityPerStore; catalog query/DTO hiện có | PASS |
| TC-02 | Tìm Store A chỉ có StoreProduct A | CatalogDatabaseIntegrationTest.tc02_storeCatalogOnlyReflectsTheStoreProductsOfThatStore; catalog HTTP suite | PASS |
| TC-03 | Đổi Store ở detail, cart cũ không đổi | CatalogDatabaseIntegrationTest.tc03_productDetailFollowsTheSelectedStoreAndLeavesCartsAlone; price/availability/cart snapshot | PASS |
| TC-04 | Add cùng StoreProduct hai lần, một line tăng quantity | CartDatabaseIntegrationTest.tc04_addingTheSameStoreProductTwiceGivesOneLineWithTheSummedQuantity; CartHttpDatabaseIntegrationTest.tc04_twoSimultaneousAddsOfTheSameStoreProductStillGiveOneLine | PASS |
| TC-05 | Cart ba Store, UI ba group | CartHttpDatabaseIntegrationTest.tc05_cartPageShowsOneGroupPerStoreWithItsOwnLines; CartDatabaseIntegrationTest.tc05_aCustomerBuildsACartAcrossThreeStores | PASS |
| TC-06 | Ba Store checkout tạo ba Order đúng Store | CheckoutDatabaseIntegrationTest.tc06_checkoutOfThreeStoresCreatesOneSessionAndOneOrderPerStore; CheckoutHttpDatabaseIntegrationTest.tc06_tc08_checkoutOfThreeStoresThroughTheFormWithTamperedPrices | PASS |
| TC-07 | SKU cuối không oversell/âm kho | CheckoutConcurrencyDatabaseIntegrationTest.tc07_twoCustomersRacingForTheLastUnitNeverOversell, tc07_whenStockCoversOnlyOneOfTwoLargerOrdersOnlyOneIsPlaced; SQL quantity constraint | PASS |
| TC-08 | Giá client sửa không đổi giá thật | CheckoutHttpDatabaseIntegrationTest.tc06_tc08_checkoutOfThreeStoresThroughTheFormWithTamperedPrices; CheckoutDatabaseIntegrationTest.tc08_priceComesFromTheStoreProductAtTheMomentOfCheckoutNotFromTheCartPage | PASS |
| TC-09 | DELIVERY COD đủ lifecycle | DeliveryDatabaseIntegrationTest.tc09CodLifecyclePreservesInventorySnapshotsAndCreatesPaymentOnlyAtCompletion; Phase9/10 HTTP lifecycle | PASS |
| TC-10 | PICKUP PAY_AT_STORE đủ lifecycle + code | PickupDatabaseIntegrationTest.tc10AndOnlineLifecyclePreserveInventoryAndSnapshots; Phase9IntegrationWebDatabaseTest.tc15FullHttpLifecycleWithOptionalCatalogChanges; Phase10 pickup duplicate race | PASS |
| TC-11 | Staff A không đọc/mutate Store B | Phase10IntegrationWebDatabaseTest.tc11PeerOrderAndInventoryCannotBeReadOrMutatedByForgingAnyStoreSource, absentOrInactiveAssignmentBlocksEveryStaffModuleAndMutationWithForgedStore; Phase11.assignmentRevocationAndReactivationAffectTheSameOldJwtImmediately | PASS |
| TC-12 | Hủy hợp lệ, hoàn đúng stock + movement | Phase12HistoryHardeningDatabaseIntegrationTest.customerCancellationRestoresOnlyItsStoreAndKeepsSnapshotsAndSiblings (7 cases), simultaneousDuplicateHttpCancellationsRestoreExactlyOnce, failureOnSecondItemRestorationRollsBackFirstItemAndPaymentClosure; payment-failure restore regression | PASS |
| TC-13 | Staff chỉnh tồn, STOCK_ADJUST đúng | Phase10IntegrationWebDatabaseTest.tc13PhysicalCountCreatesOneCompleteAuditAndImmediatelyUpdatesDashboardAndExactStock; StaffInventoryDatabaseIntegrationTest.concurrentAdjustmentsKeepSerialBeforeAfterValuesAndOneMovementPerActualChange; Phase12 Admin stale-read regression | PASS |
| TC-14 | Đổi giá, snapshot giữ nguyên | CheckoutDatabaseIntegrationTest.tc14_orderItemsKeepTheirSnapshotWhenPriceAndProductNameChangeLater; CheckoutHttpDatabaseIntegrationTest.tc14_resultPageKeepsShowingTheSnapshotAfterThePriceChanges; cancellation giữ snapshots | PASS |
| TC-15 | Payment/status từng Order độc lập | Phase9IntegrationWebDatabaseTest.tc15FullHttpLifecycleWithOptionalCatalogChanges, mixedFailedPaymentRestoresOnlyItsOrderAndSiblingsStillCompleteOverHttp; Phase12 giữ exact sibling aggregates + CheckoutSession | PASS |
| TC-16 | Timeline đủ transition | OrderStateDatabaseIntegrationTest.validTransitionHasOneHistoryActorTimestampNoteAndNoSideEffects, historyFailureRollsBackAlreadyFlushedOrderAndOptionalFlushedHistory, concurrentDuplicateHasOneWinnerAndExactlyOneAudit; Phase12 persisted timeline audit/customer actor | PASS |
| TC-17 | Cloudinary file, SQL URL/public_id | CatalogDatabaseIntegrationTest.tc17_productImageGoesToCloudinaryAndTheDatabaseKeepsOnlyUrlAndPublicId / tc17_brandLogoIsStoredAsUrlAndPublicIdAndTheOldFileIsRemoved (**mock adapter + SQL**); Phase12CloudinaryLiveVerification.tc17LiveUploadStoresOnlyUrlAndPublicIdAndRemovesOnlyItsFixture (**live HTTP Admin/Cloudinary/SQL**) | PASS |
| TC-18 | Store INACTIVE không order mới, lịch sử còn | Phase11AdminDatabaseIntegrationTest.tc18InactiveStoreRejectsNewCheckoutButAdminAndCustomerKeepHistoricalOrders, productStoreAndStoreProductWithHistoryAreSoftDisabledAndNotHardDeleted; Phase12.cancellationAfterStoreIsInactiveStillRestoresTheOriginalStockAndPreservesHistory | PASS |

## 5. Transaction, rollback và concurrency

| Kiểm chứng | Evidence / kết quả |
| --- | --- |
| Cancellation atomic | Closure + restore + movement + status/history cùng REQUIRED transaction; primitives MANDATORY |
| Lỗi sau payment flush | cancellationRollsBackRealFlushedPaymentStockOrderAndAuditAndCanRetry[PAYMENT_FLUSH]: exact DB snapshot khôi phục; retry một restore/history |
| Lỗi sau restore đã ghi SQL | Cùng test [FIRST_RESTORE] và failureOnSecondItemRestorationRollsBackFirstItemAndPaymentClosure: persist/flush rồi throw, rollback cả payment/stock trước đó |
| Lỗi sau status/history đã ghi SQL | Cùng test [HISTORY_FLUSH]: kiểm CANCELLED trong transaction, persist/flush history rồi throw; rows/timestamps rollback |
| Outer transaction rollback | Cùng test [OUTER_TRANSACTION]: cancellation đã flush nhưng outer throw thì DB nguyên trạng |
| Multi-Store checkout rollback | CheckoutConcurrencyDatabaseIntegrationTest.failureAfterTheFirstStoreGroupWasWrittenRollsBackTheWholeCheckout; Phase9 composed rollback tests |
| Duplicate cancel | Hai HTTP requests đồng thời redirect idempotent; chỉ một CANCEL_ORDER/item và một cancellation history |
| Multi-item/multi-order restore | Hai Order chung stocks, thứ tự item ngược, qty 2/3: locks ổn định, không deadlock/lost restore, stock về đúng ban đầu |
| Cancel ↔ payment success/failure | State/payment/stock/history theo transaction thắng; callback muộn không đảo trạng thái/thu tiền/hoàn lại |
| Cancel ↔ Staff prepare | CANCELLED hoặc PREPARING, chỉ một transition hợp lệ; transaction thua bị state guard chặn |
| Cancel ↔ stock adjustment | Hai movements nối before/after, stock cuối đúng, không lost update |
| Admin stale read | Query/lock barrier kiểm quantity đã commit, PASS sau sửa |
| Staff concurrent adjustment | StaffInventory/Phase10 HTTP checkout-adjust race giữ ledger serial |
| SKU cuối/negative stock | TC-07 và databaseRejectsNegativeStock PASS |
| Hai Store độc lập | Cancellation Store thứ hai commit khi Store thứ nhất còn transaction mở; kiểm Future chưa done, sau đó kiểm đủ stock/history/movement |
| Overflow / receipt sai | INT_MAX + restore và SUCCESS receipt lệch UNPAID bị chặn; không partial writes |

Spies chỉ failure injection sau SQL flush hoặc scheduling barrier; không mock business services, lock hay persistence outcomes. Concurrency dùng transactions/connections SQL Server riêng, futures/latches có timeout, không H2.

## 6. Security và audit dữ liệu hiện có

- CUSTOMER/STAFF bị chặn Admin; role/status thay đổi có hiệu lực với JWT đã phát hành: tests Phase 11 giữ nguyên.
- Staff chỉ ACTIVE assignments trong DB, không tin Store từ query/header/cookie/body: Phase 10 suites.
- Cancel: guest không mutation; STAFF/ADMIN 403, customer khác/missing Order 404; inactive customer với JWT cũ bị chặn. Direct service impersonation/role bị PreAuthorize chặn.
- Cookie/Bearer và encoded paths thiếu CSRF trả 403; valid form requests tới controller thật PASS.
- HttpOnly cookie/token validation reuse AuthFlow/JwtCookie/JwtSecurity tests. Role/User/Store scope reload từ DB.
- Forged price/amount/owner/status/Store/quantity không thay snapshots/actor; checkout TC-08/14.
- INACTIVE Store vẫn giữ history, Product/Store/StoreProduct có giao dịch không hard-delete.

existingSqlDataHasCompleteTimelinesConservedStockAndNoDuplicateOrderMovements kiểm **toàn bộ dữ liệu hiện có**, chỉ đọc:

1. Mọi Order có history, status hiện tại khớp history cuối.
2. Timeline bắt đầu NULL, old/new nối nhau, không transition trùng.
3. Không duplicate ORDER/CANCEL_ORDER theo Order + StoreProduct.
4. Snapshot quantity khớp deduction/restore đúng Store; cancelled hoàn đủ.
5. Ledger arithmetic và continuity đúng, entry cuối khớp exact stock hiện tại.
6. Payment method/amount/paid_at/state ONLINE nhất quán; phân biệt legacy offline pending.
7. CHECK/FK enabled + trusted.
8. Exact business snapshot trước/sau bằng nhau.

Fixtures mutation dùng SeedGuard, so sánh **exact rows/IDs/timestamps** sau cleanup, gồm business tables, User/Store/assignment và Product. Không chạy 00_run_all.sql, reset/reseed hoặc sửa lịch sử để đạt PASS. IDENTITY có thể tăng tự nhiên do insert/rollback fixture; không reset counter.

## 7. Cloudinary live và giới hạn

- Tests Phase 6: mock CloudinaryService adapter, ProductService/JPA/SQL thật; Product image/Brand logo, URL/public_id, replacement/compensation.
- Test Phase12 **không mock Cloudinary**: Product fixture riêng, Admin JWT login, GET form CSRF/cookie, POST multipart PNG qua /admin/products/{id}/images, upload thật, SQL URL/public_id, provider metadata, HTTPS CDN 200/image bytes; POST delete với CSRF, provider xác nhận resource không còn; xóa đúng fixture, Products/ProductImages nguyên trạng.
- Không thay ảnh hiện có. Brand branch không được báo cáo là live upload riêng; live provider dùng chung được kiểm bằng Product image.

Phase12CloudinaryLiveVerification nằm ngoài Surefire default patterns để regression thường không tự upload. Acceptance cuối **explicit include class này**; thiếu credentials/network thì fail, không skip.

Giới hạn: SQL Server/môi trường hiện tại và các lịch concurrency có kiểm soát, không stress/distributed test. ONLINE vẫn demo, không gateway/refund. Không kiểm môi trường deploy, không triển khai Phase 13.

## 8. Lệnh và kết quả

Maven 3.9.16, Java 21, profile dev/schema validate. Dùng cache Maven (-o). Maven Wrapper không dùng được với HOME môi trường; Maven chạy qua auto-review escalation do sandbox Windows chặn Java ZipFS/JAR closing, không có rejection.

~~~powershell
# Baseline trước sửa
mvn -o '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' clean package

# Focused cuối
mvn -o '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' '-Dtest=Phase12HistoryHardeningDatabaseIntegrationTest,Phase12CloudinaryLiveVerification' test

# Final acceptance: đủ bốn default Surefire patterns + explicit live verification
mvn -o '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' '-Dtest=Test*,*Test,*Tests,*TestCase,Phase12CloudinaryLiveVerification' clean package
~~~

| Run | TOTAL | PASS | FAIL | ERROR | SKIP | Build |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| Baseline trước sửa | 894 | 893 | 0 | 0 | 1 | SUCCESS |
| Focused Phase12 cuối (15:00:52 +07:00) | 34 | 34 | 0 | 0 | 0 | SUCCESS |
| Final full clean package (15:11:16 +07:00) | 928 | 927 | 0 | 0 | 1 | SUCCESS |

Final run: **45 suites**, **02:00 phút**. Đối chiếu tên suite với baseline: đủ toàn bộ 43 suites Phase 1–11 cộng hai suite Phase12. Regression Phase 1–11 giữ kết quả 893 PASS/1 SKIP; phần mới 34 PASS/0 SKIP. Surefire XML totals khớp Maven summary. Artifact target/oneshop.jar được repackage thành công.

Trong quá trình chốt, lệnh chọn riêng *Test thiếu hai test của OneShopApplicationTests. Đã sửa selection sang đủ bốn default Surefire patterns và chạy lại clean package toàn bộ; số liệu cuối ở trên bao gồm hai test này, không dùng run thiếu suite làm kết luận regression.

Log: target-phase12-baseline.log, target-phase12-stale-read.log, target-phase12-final-focused.log, target-phase12-package.log. XML/text reports: target/surefire-reports. Artifact: target/oneshop.jar.

SKIP cũ duy nhất: CatalogHttpDatabaseIntegrationTest.imageUploadReachesCloudinaryServiceThroughTheMultipartFormWithCsrf dành cho nhánh **chưa configured**, assumeFalse khi credentials có. Giữ nguyên test; nhánh configured upload được test live Phase12. Không skip test Phase12.

## 9. Definition of Done

| Điều kiện | Kết quả |
| --- | --- |
| Đối chiếu đầy đủ TC-01–TC-18, evidence theo môi trường | PASS, không TC FAIL/BLOCKED |
| OrderStatusHistory / InventoryMovement chính xác | PASS, production paths và read-only audit DB hiện có |
| Cancel + restore atomic, không double restoration | PASS |
| Concurrency không oversell/lost update | PASS, bao gồm Admin stale-read regression |
| Authorization, Staff scope, JWT/CSRF | PASS |
| Data integrity và độc lập transaction theo Store | PASS; không blocking defect còn tồn tại trong các scenario kiểm chứng |
| Regression Phase 1–11 | PASS |
| Test mới không skip | 34/34 PASS, 0 SKIP |
| Maven clean package | BUILD SUCCESS |

**PHASE 12 DONE.** Không còn TC chưa kiểm chứng trong phạm vi môi trường hiện tại; giới hạn live Brand riêng/stress/deploy đã nêu rõ và không mở rộng scope. Không tự triển khai Phase 13 hoặc deploy ứng dụng.
