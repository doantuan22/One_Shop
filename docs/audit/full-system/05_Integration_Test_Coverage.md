# Integration & Acceptance Coverage

Ngày audit: 09/10/2026 (Asia/Saigon, UTC+7). Commit: `88c22132955b9c7a88be5a7a6ea08ff167d83776`. **AUDIT ONLY**; không remediation, thay dependency/schema/test, reseed hay deploy.

## Kết quả chạy mới

**927 executions / 926 PASS / 0 FAIL / 0 ERROR / 1 SKIP, 44 suites, Maven clean package BUILD SUCCESS.** Surefire XML và Maven log khớp. [regression-summary.json](evidence/regression-summary.json) giữ tên từng method/status; [build-summary.json](evidence/build-summary.json) giữ command/time/artifact SHA256. Java 21/Maven 3.9.16 offline cache, không đổi POM/Surefire excludes/test. Auto-review cho Maven escalation do Windows sandbox ZipFS; không có rejection.

Baseline historical15:11:16 có928/927 PASS/1 SKIP, 45 suites vì explicit opt-in `Phase12CloudinaryLiveVerification`. Current default clean package không include class ngoài patterns, không upload/delete live. Không cộng historical1 PASS vào current totals để giả lập928. 33 Phase12 hardening tests current PASS không SKIP. SKIP duy nhất `CatalogHttpDatabaseIntegrationTest.imageUploadReachesCloudinaryServiceThroughTheMultipartFormWithCsrf` dùng assumeFalse nhánh “chưa configured”, environment đã configured; giữ nguyên.

Các suite unit dùng mocks và graph cases; DB/HTTP suite dùng production services/repositories/SQL Server, có mock Cloudinary adapter hoặc spy failure injection được ghi rõ. 927 là tổng execution mọi loại, **không phải 927 SQL integration tests**. Browser/audit14assertions là verification riêng, không JUnit tests mới. Không thêm/sửa tests trong src/test.

## TC-01–18

Mọi tên class trong bảng trỏ tới source `src/test/java/com/oneshop/`; các package security/service có thư mục tương ứng trong inventory. Status là kiểm chứng **hiện tại**, không mặc định từ Phase12 report. Một TC có nhiều coverage; expected/actual tóm tắt, exact persisted assertions ở tests/current XML summary và JSON browser/SQL evidence.

| TC | Requirement | Implementation / existing tests (vừa rerun) | Dynamic verification / evidence | Expected | Actual | Status |
| --- | --- | --- | --- | --- | --- | --- |
| TC-01 | Một SKU tại3 Store, availability/giá global đúng | ProductService/StoreProduct query; CatalogDatabaseIntegrationTest.tc01_chainCatalogShowsEachSkuOnceWithPriceAndAvailabilityPerStore | Browser catalog-three-stores + detail-global; fixture SP819/820/821 | SKU503 xuất hiện một lần, Store47/48/49 giá111000/121000/131000 | Đúng catalog và SQL; không exact quantity client | PASS |
| TC-02 | Chọn Store A rồi tìm chỉ StoreProduct A | CatalogDatabaseIntegrationTest.tc02_storeCatalogOnlyReflectsTheStoreProductsOfThatStore; CatalogHttpDatabaseIntegrationTest.tc02_selectingAStoreSwitchesTheCatalogToThatStoreOnly | Current SQL/HTTP assertions; UI chọn StoreA trong detail | Catalog/query A không có dữ liệu Store khác | Current suites PASS, selected Store được render đúng | PASS |
| TC-03 | Detail đổi Store, cart cũ không đổi nguồn | CatalogDatabaseIntegrationTest.tc03_productDetailFollowsTheSelectedStoreAndLeavesCartsAlone; CatalogHttpDatabaseIntegrationTest.tc03_productDetailChangesPriceAndAvailabilityWithTheSelectedStore | UI selected-store-A/B/C và CartItem IDs9739/9740/9741 | Giá thay theo Store, CartItem cũ giữ819/820/821 | Giá111k/121k/131k, cart nguồn cũ không chuyển | PASS |
| TC-04 | Add cùng StoreProduct2 lần | CartDatabaseIntegrationTest.tc04_addingTheSameStoreProductTwiceGivesOneLineWithTheSummedQuantity; CartHttpDatabaseIntegrationTest.tc04_twoSimultaneousAddsOfTheSameStoreProductStillGiveOneLine | UI same-store-product-added-twice, order9809 qty2 | Một CartItem quantity2, không duplicate pair | Một line819 qty2; current concurrent test PASS | PASS |
| TC-05 | Cart3 Store UI3 groups | CartDatabaseIntegrationTest.tc05_aCustomerBuildsACartAcrossThreeStores; CartHttpDatabaseIntegrationTest.tc05_cartPageShowsOneGroupPerStoreWithItsOwnLines | Browser cart-three-groups/group-selection | 3 Store sections và item/group toggles đúng | groupCount=3, selected preview3 groups | PASS |
| TC-06 | Checkout3 Store tạo3 Order đúng một Store | CheckoutDatabaseIntegrationTest.tc06_checkoutOfThreeStoresCreatesOneSessionAndOneOrderPerStore; CheckoutHttpDatabaseIntegrationTest.tc06_tc08_checkoutOfThreeStoresThroughTheFormWithTamperedPrices | UI checkout-created-three-orders + fixture-db | Một checkout→3orders, total/stock/cart atomic | Checkout6281→9809/9810/9811 Stores47/48/49,total474000 | PASS |
| TC-07 | Hai khách mua SKU cuối không oversell | CheckoutConcurrencyDatabaseIntegrationTest.tc07_twoCustomersRacingForTheLastUnitNeverOversell; tc07_whenStockCoversOnlyOneOfTwoLargerOrdersOnlyOneIsPlaced | Current SQL race tests, riêng transactions/locks thật | Chỉ đủ tồn thì commit, quantity>=0, loser không half-write | Cả hai current executions PASS; DB negativeStock0 | PASS |
| TC-08 | Client chỉnh giá vẫn dùng price DB | CheckoutDatabaseIntegrationTest.tc08_priceComesFromTheStoreProductAtTheMomentOfCheckoutNotFromTheCartPage; CheckoutHttpDatabaseIntegrationTest.tc06_tc08_checkoutOfThreeStoresThroughTheFormWithTamperedPrices | Current HTTP forged payload + SQL persisted item/amount assertions | Không tin unitPrice/total/owner/status | Current SQL/HTTP tests PASS; normal browser totals khớpDB | PASS |
| TC-09 | DELIVERY COD đủ lifecycle | DeliveryDatabaseIntegrationTest.tc09CodLifecyclePreservesInventorySnapshotsAndCreatesPaymentOnlyAtCompletion; Delivery/Phase9/Phase10 HTTP suites | Browser delivery-prepare/pack/ship/complete; order9809 | CONFIRMED→PREPARING→PACKED→SHIPPING→COMPLETED, thu COD khi complete | 5 history entries đúng, COD SUCCESS amount222000/PAID | PASS |
| TC-10 | PICKUP PAY_AT_STORE/mã nhận | PickupDatabaseIntegrationTest.tc10AndOnlineLifecyclePreserveInventoryAndSnapshots; Phase9IntegrationWebDatabaseTest.tc15FullHttpLifecycleWithOptionalCatalogChanges | Browser C prepare/ready/customer-code/Staff complete; duplicate probe | CONFIRMED→PREPARING→READY_FOR_PICKUP→COMPLETED, verifycode/paidAt | Order9811 COMPLETED/PAID, code+ready/picked timestamps; duplicate không ghi thêm | PASS |
| TC-11 | Staff A không mở Order B | Phase10IntegrationWebDatabaseTest.tc11PeerOrderAndInventoryCannotBeReadOrMutatedByForgingAnyStoreSource; Phase11AdminDatabaseIntegrationTest.assignmentRevocationAndReactivationAffectTheSameOldJwtImmediately | Current cross-Store read/write tests; Chrome revoke/reactivate397 | Backend reject bất kể selector/query/header/body/JWT cũ | Forbidden/404, mutation unchanged; Chrome revoke404/reactivate200 | PASS |
| TC-12 | Hủy hợp lệ+đúng stock+movement | Phase12HistoryHardeningDatabaseIntegrationTest.customerCancellationRestoresOnlyItsStoreAndKeepsSnapshotsAndSiblings; simultaneousDuplicateHttpCancellationsRestoreExactlyOnce | Browser cancel9812 + duplicate; SQL movement/history/stock; rollback injected-after-flush current tests | CANCELLED atomic, restore819 quantity1 đúng một lần | 11→12, 1 CANCEL_ORDER/1cancel history; completed reject400 | PASS |
| TC-13 | Staff adjust+STOCK_ADJUST | Phase10IntegrationWebDatabaseTest.tc13PhysicalCountCreatesOneCompleteAuditAndImmediatelyUpdatesDashboardAndExactStock; StaffInventoryDatabaseIntegrationTest.concurrentAdjustmentsKeepSerialBeforeAfterValuesAndOneMovementPerActualChange | UI Staff adjust8198→12; Admin/Staff actual HTTP race8209→20→17 | Exact physical count + before/change/after/actor/note audit | Movement hợp lệ, continuity và stock cuối khớp | PASS |
| TC-14 | Đổi giá sau mua giữ snapshot | CheckoutDatabaseIntegrationTest.tc14_orderItemsKeepTheirSnapshotWhenPriceAndProductNameChangeLater; CheckoutHttpDatabaseIntegrationTest.tc14_resultPageKeepsShowingTheSnapshotAfterThePriceChanges | UI admin-price-edit + customer order9809 + SQL | SP price thay, order.unitPrice/name/total giữ lúc checkout | SP111000→222000; order unit111000×2=subtotal222000 | PASS |
| TC-15 | Payment/status từng Order độc lập trong checkout | Phase9IntegrationWebDatabaseTest.tc15FullHttpLifecycleWithOptionalCatalogChanges; mixedFailedPaymentRestoresOnlyItsOrderAndSiblingsStillCompleteOverHttp | Browser A COD/B ONLINE/C PAY_AT_STORE; fixture checkout/payment records | 3 Order method/status riêng; fail chỉ target restore | B thanh toán trước; A/C thu completion; 3 payment SUCCESS mỗi Order đúng amount; failure current regression PASS | PASS |
| TC-16 | History đủ transition | OrderStateDatabaseIntegrationTest.validTransitionHasOneHistoryActorTimestampNoteAndNoSideEffects; historyFailureRollsBackAlreadyFlushedOrderAndOptionalFlushedHistory; concurrentDuplicateHasOneWinnerAndExactlyOneAudit | Current SQL timeline audit + fixture history16 entries | NULL initial→chain nối đúng actor/time/note; last status=order | 0 broken timeline/status issues; browser history+SQL checks PASS | PASS |
| TC-17 | Ảnh tại Cloudinary, SQL URL/public_id | ProductService/CloudinaryService; CatalogDatabaseIntegrationTest.tc17_productImageGoesToCloudinaryAndTheDatabaseKeepsOnlyUrlAndPublicId; tc17_brandLogoIsStoredAsUrlAndPublicIdAndTheOldFileIsRemoved | Current adapter mock+realSQL PASS; historical Phase12CloudinaryLiveVerification1 PASS, không rerun | Product/Brand upload/primary/delete/replace/compensation đúng; provider thật verified khi cho phép | DB không binary; mock path PASS. Product live trước audit verified; live current Product/Brand chưa rerun | NOT VERIFIED |
| TC-18 | INACTIVE Store không nhậnOrder mới, lịch sử còn | Phase11AdminDatabaseIntegrationTest.tc18InactiveStoreRejectsNewCheckoutButAdminAndCustomerKeepHistoricalOrders; productStoreAndStoreProductWithHistoryAreSoftDisabledAndNotHardDeleted; Phase12 cancellationAfterStoreIsInactiveStillRestoresTheOriginalStockAndPreservesHistory | Browser Store47/Product503/SP819 inactive; historic order9809 customer+adminread; new add rejected; current checkout negative test | Block new sale/checkout, không delete lịch sử/FK | Historical rows/read giữ; current checkout reject; seed5/baseline rows nguyên trạng | PASS |

**17 PASS, 0 FAIL, 1 NOT VERIFIED (TC17 live current), 0 BLOCKED.** Review/customer signup Unicode là extra-roadmap checks FAIL và findings FSA-01/02; TC-01–18 không có case cho chúng. Đây là coverage blind spot: tất cả core TC PASS cũng không chứng minh toàn bộ tác nhân/full roadmap DONE.

## E2E kiểm chứng bằng UI

| Journey | Concrete evidence | Outcome |
| --- | --- | --- |
| Customer catalog→detail→cart→checkout→payment→completed | SKU503, Stores47/48/49, cart9739–9741, checkout6281, orders9809–9811; browser-journey labels/PNGs + fixture-db | 3 Store-specific orders và total474000; A COD delivery completed, B ONLINE pickup completed, C PAY_AT_STORE pickup completed |
| Pickup ready→code→Staff verification | B/C ready pages, Customer code, Staff complete form; duplicate request captured | Completion/payment/history đúng một lần; current negative-code tests PASS |
| Staff login→queue→fulfillment→inventory | Staff270/assignments397–399; stock819 adjust8→12; real history endpoint/additional mobile screenshot | Backend/SQL/UI nhất quán; revoke/reactivate áp JWT đang dùng |
| Admin Store→Staff→SKU/SP→overview→orders | Actual create/update/filter forms, metadata captured; soft-disabled original fixture rows/readhistory | Quản trị scope hiện hữu hoạt động, không CRUD song song |
| Customer cancel/repeat | Order9812/COD CONFIRMED, stock81911→12, 1movement/1history; completed cancel400 | PASS; production atomic paths current flush/rollback tests hỗ trợ |
| Completed→review | Completed9809 + detail Product, reviewActions[], valid CSRF → 404; service skeleton | FAIL FSA-01, không có Customer implementation |

### Lưu ý chất lượng audit driver

Một lỗi escape regex của script CDP dừng bước đọc IDs sau khi checkout đã commit; không phải lỗi OneShop. Driver resume cùng browser contexts/fixtures, không tạo lại checkout. Log journey-resume và combined JSON ghi bước tiếp theo; DB vẫn đúng 3 orders/1session. Route history đoán sai404 được kiểm lại bằng route production, không tạo finding. Không sửa test application để che các lỗi audit tooling này.

## Cloudinary evidence boundaries

- Current `CatalogDatabaseIntegrationTest`: CloudinaryService mocked adapter, ProductService/JPA/SQL thật. Product images và Brand logos URL/publicId, primary/delete/replace/compensation PASS. `CloudinaryServiceTest` kiểm adapter validation, không provider live.
- Historical Product live: `Phase12CloudinaryLiveVerification`, root target-phase12-package.log có1 PASS/0 SKIP. ReportPhase12 mô tả HTTP AdminJWT+CSRF multipart→Cloudinary→SQL→CDN metadata→delete fixture. [historical-cloudinary.json](evidence/historical-cloudinary.json) trích exact suite result.
- Audit này **không** upload/delete asset live, không riêng Brand live test, không current CDN asset check. DB product_images0 sau cleanup. Không báo TC17 current live PASS chỉ vì credentials tồn tại hay mock test PASS.

## Coverage còn giới hạn

Target Render/SQL Server cloud/prod HTTPS not tested; provider failure/crash timing vượt mocked cases; image primary concurrent uploads; scale benchmarks/execution plans; screen reader/full accessibility; every UI modal/path permutation. Bounded stock/payment/order races đã chạy, không exhaustive concurrency proof. Các giới hạn được giữ NOT VERIFIED, không sửa scope hoặc bỏ test để tăng kết quả.

## Suite totals

| Suite | TOTAL | PASS | FAIL | ERROR | SKIP |
| --- | ---: | ---: | ---: | ---: | ---: |
| `AccessControlIntegrationTest` | 20 | 20 | 0 | 0 | 0 |
| `AuthDatabaseIntegrationTest` | 8 | 8 | 0 | 0 | 0 |
| `AuthFlowIntegrationTest` | 6 | 6 | 0 | 0 | 0 |
| `CartDatabaseIntegrationTest` | 23 | 23 | 0 | 0 | 0 |
| `CartHttpDatabaseIntegrationTest` | 15 | 15 | 0 | 0 | 0 |
| `CartWebIntegrationTest` | 5 | 5 | 0 | 0 | 0 |
| `CatalogDatabaseIntegrationTest` | 30 | 30 | 0 | 0 | 0 |
| `CatalogHttpDatabaseIntegrationTest` | 29 | 28 | 0 | 0 | 1 |
| `CatalogWebIntegrationTest` | 10 | 10 | 0 | 0 | 0 |
| `CheckoutConcurrencyDatabaseIntegrationTest` | 6 | 6 | 0 | 0 | 0 |
| `CheckoutDatabaseIntegrationTest` | 23 | 23 | 0 | 0 | 0 |
| `CheckoutHttpDatabaseIntegrationTest` | 10 | 10 | 0 | 0 | 0 |
| `CheckoutWebIntegrationTest` | 6 | 6 | 0 | 0 | 0 |
| `DatabaseFoundationIntegrationTest` | 6 | 6 | 0 | 0 | 0 |
| `DeliveryDatabaseIntegrationTest` | 57 | 57 | 0 | 0 | 0 |
| `DeliveryWebIntegrationTest` | 14 | 14 | 0 | 0 | 0 |
| `LayoutsIntegrationTest` | 9 | 9 | 0 | 0 | 0 |
| `OneShopApplicationTests` | 2 | 2 | 0 | 0 | 0 |
| `OrderStateDatabaseIntegrationTest` | 24 | 24 | 0 | 0 | 0 |
| `PaymentDatabaseIntegrationTest` | 31 | 31 | 0 | 0 | 0 |
| `PaymentWebIntegrationTest` | 6 | 6 | 0 | 0 | 0 |
| `Phase10IntegrationWebDatabaseTest` | 12 | 12 | 0 | 0 | 0 |
| `Phase11AdminDatabaseIntegrationTest` | 30 | 30 | 0 | 0 | 0 |
| `Phase12HistoryHardeningDatabaseIntegrationTest` | 33 | 33 | 0 | 0 | 0 |
| `Phase9IntegrationWebDatabaseTest` | 19 | 19 | 0 | 0 | 0 |
| `PickupDatabaseIntegrationTest` | 62 | 62 | 0 | 0 | 0 |
| `PickupWebIntegrationTest` | 15 | 15 | 0 | 0 | 0 |
| `security.jwt.JwtServiceTest` | 5 | 5 | 0 | 0 | 0 |
| `service.CartServiceRetryTest` | 4 | 4 | 0 | 0 | 0 |
| `service.CloudinaryServiceTest` | 2 | 2 | 0 | 0 | 0 |
| `service.CodPaymentServiceTest` | 14 | 14 | 0 | 0 | 0 |
| `service.DeliveryFulfillmentServiceTest` | 55 | 55 | 0 | 0 | 0 |
| `service.InventoryRestoreTest` | 4 | 4 | 0 | 0 | 0 |
| `service.OrderServiceTest` | 5 | 5 | 0 | 0 | 0 |
| `service.OrderTransitionPolicyTest` | 129 | 129 | 0 | 0 | 0 |
| `service.OrderViewServiceTest` | 8 | 8 | 0 | 0 | 0 |
| `service.PayAtStorePaymentServiceTest` | 14 | 14 | 0 | 0 | 0 |
| `service.PaymentServiceTest` | 11 | 11 | 0 | 0 | 0 |
| `service.PickupFulfillmentServiceTest` | 53 | 53 | 0 | 0 | 0 |
| `StaffFulfillmentOperationsDatabaseIntegrationTest` | 36 | 36 | 0 | 0 | 0 |
| `StaffInventoryDatabaseIntegrationTest` | 29 | 29 | 0 | 0 | 0 |
| `StaffOperationsDatabaseIntegrationTest` | 23 | 23 | 0 | 0 | 0 |
| `StaffStoreScopeDatabaseIntegrationTest` | 17 | 17 | 0 | 0 | 0 |
| `WebRoutesIntegrationTest` | 7 | 7 | 0 | 0 | 0 |
