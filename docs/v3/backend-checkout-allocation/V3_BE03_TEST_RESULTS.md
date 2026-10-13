# V3-BE-03 — TEST RESULTS

All results come from runs of 13/10/2026 against the local SQL Server, **database `oneshop_test`** (`DB_NAME=oneshop_test`, taken over the `.env` value by the process environment). The `oneshop` database was not written to. Raw logs and XML reports were kept in the session scratchpad, not in the repository.

## 1. Commands

```text
./mvnw -B -DskipTests test-compile
DB_NAME=oneshop_test ./mvnw -B test                     # full suite, BASELINE: pre-BE-03 commit ec14f3c in a detached worktree
DB_NAME=oneshop_test ./mvnw -B test -Dtest='AllocationEngineTest,ShippingAddressValidatorTest,PaymentDeadlinePolicyTest,
        CheckoutWebIntegrationTest,CheckoutV3DatabaseIntegrationTest,CheckoutV3SplitAndFeeDatabaseIntegrationTest,
        CheckoutV3PolicyGateDatabaseIntegrationTest'
DB_NAME=oneshop_test ./mvnw -B test -Dtest='CheckoutV3ConcurrencyDatabaseIntegrationTest,CheckoutV3HttpDatabaseIntegrationTest'
DB_NAME=oneshop_test ./mvnw -B test                     # full suite, FINAL (working tree after BE-03 completion)
```

BE-03 database tests only run when `select db_name()` ends with `_test` (otherwise they are skipped, not failed). Committing tests (`SeedGuardV3`) restore the data they touched, including Store flags.

## 2. Full-suite comparison, per test case

The comparison was made from the Surefire XML reports (one entry per test case: class + method), not from the Maven summary text.

| | Test cases | PASS | FAIL | ERROR | SKIP | Failing (FAIL+ERROR) |
|---|---:|---:|---:|---:|---:|---:|
| Baseline (before BE-03, commit `ec14f3c`) | 1247 | 822 | 52 | 331 | 42 | **383** |
| Final (after BE-03 completion) | 1358 | 933 | 52 | 331 | 42 | **383** |

- **Newly failing test cases: 0.** Test cases that failed before and pass now: 0. The failing set is identical.
- Test cases present in the baseline and not in the final: **8** — V2-only behaviours that DEC-21 / V3 removed and were rewritten, listed in §6.
- Test cases added: **119**, all PASS.
- No test that passed in the baseline fails now.

> Correction: the first BE-03 report spoke of "197 distinct failing tests". That number was a count of distinct *first stack frames* printed by Maven, not of test cases. The true number of failing test cases, before and after, is **383**.

BE-01 and BE-02 tests still pass: `JpaV3ReadIntegrityTest` 9/9, `CentralCartIntegrationTest` 15/15, `CartServiceV3Test` 7/7, `ProductServiceV3Test` 6/6.

Database state (row counts and checksums of orders, items, fulfillments, payments, movements, store_products, cart_items, checkout_sessions, products) was compared before and after all runs: **identical for `oneshop` and `oneshop_test`**.

## 3. Tests of the BE-03 work

| Class | Tests | Result |
|---|---:|---|
| `service.allocation.AllocationEngineTest` (incl. 300 generated carts) | 20 | PASS |
| `service.allocation.ShippingAddressValidatorTest` | 14 | PASS |
| `service.PaymentDeadlinePolicyTest` | 10 | PASS |
| `CheckoutV3DatabaseIntegrationTest` (SQL Server, rolled back) | 31 | PASS |
| `CheckoutV3SplitAndFeeDatabaseIntegrationTest` (SQL Server, rolled back) | 18 | PASS |
| `CheckoutV3PolicyGateDatabaseIntegrationTest` (default configuration) | 4 | PASS |
| `CheckoutV3ConcurrencyDatabaseIntegrationTest` (real commits) | 9 | PASS |
| `CheckoutV3HttpDatabaseIntegrationTest` (real HTTP + data) | 3 | PASS |
| `CheckoutWebIntegrationTest` (rewritten for V3) | 10 | PASS |
| `service.PaymentServiceTest`, `PaymentWebIntegrationTest` (updated) | 9 + 6 | PASS |

### 3.1 BE03-F01 .. F30

| Id | Scenario | Test | Result |
|---|---|---|---|
| F01 | One Store holds the whole cart | `AllocationEngineTest.oneStoreSuppliesTheWholeCart`, `be03_01` | PASS |
| F02 | SKU ×5, Stores hold 3 + 2: 1 Order, 2 Fulfillments, 2 OrderItems | `f02_sameSkuSplitOverTwoStores` | PASS |
| F03 | SKU over three Stores, totals exact | `f03_sameSkuSplitOverThreeStores` | PASS |
| F04 | Several SKUs, one split | `f04_mixedCartWithOneSplitSku` | PASS |
| F05 | Eligible Stores together short: nothing created | `f05_totalShortageCreatesNothing` | PASS |
| F06 | INACTIVE Store with stock never used | `f06_f07_inactiveSourcesAreNotUsed` | PASS |
| F07 | INACTIVE StoreProduct with stock never used | same | PASS |
| F08 | `reserved_quantity > 0`: available = quantity − reserved, CHECK holds | `f08_reservedQuantityLimitsWhatIsAllocated` | PASS |
| F09 | Same input and stock → same plan and fingerprint | `f09_planIsDeterministic`, engine tests | PASS |
| F10 | Revalidation after lock; no oversell under contention | `f10_sameSkuSplitUnderContention`, `f10b_sourcesChangeAfterTheCart`, `be03_22*` | PASS |
| F11 | Split SKU: OrderItem/Fulfillment/StoreProduct sources right | `f02…`, `be03_09…` | PASS |
| F12 | No StoreProduct used twice; no double deduction | `AllocationEngineTest.noStoreProductIsUsedTwice`, `f02…` (movements) | PASS |
| F13 | HCM customer, Ha Noi Store supplies | `f13_northernStoreServesASouthernAddress` | PASS |
| F14 | ACTIVE + `delivery_enabled` Store takes part | `f13…`, `f06…` | PASS |
| F15 | ACTIVE but delivery disabled: not used | `f15_deliveryDisabledStoreIsNotUsed`, `be03_05` | PASS |
| F16 | INACTIVE Store: not used | `f06_f07…` | PASS |
| F17 | Invalid address: no Order | `f17_invalidAddressIsRefused`, `ShippingAddressValidatorTest` | PASS |
| F18 | DELIVERY without a chosen Store | every delivery test | PASS |
| F19 | Pickup Store missing a SKU: refused | `f19_f20_pickupDoesNotBorrow`, `be03_18` | PASS |
| F20 | Pickup borrows nothing, no split | same + `AllocationEngineTest.pickupNeeds…` | PASS |
| F21–F23 | ONLINE DELIVERY 1/2/3 Stores: fee 30.000 once, `total = items + fee`, payment amount = total | `f21_f22_f23_onlineFeeIsOnePerOrder` | PASS |
| F24 | COD DELIVERY one Store: 30.000 | `f24_codOneStore` | PASS |
| F25–F26 | COD split 2 and 3 Orders: 30.000 each, session = Σ | `f25_f26_codSplitFeePerOrder`, `c5_codSplitOfOneSku` | PASS |
| F27–F28 | STORE_PICKUP ONLINE / PAY_AT_STORE: 0 | `f27_f28_pickupIsFree` | PASS |
| F29 | Forged fee/total from the browser | HTTP test (`shippingFee=1` ignored, DB fee 30000), web test | PASS |
| F30 | Fee changed after the preview: reconfirmation | `f30_feeChangedAfterPreview` | PASS |
| — | Fingerprint bound to customer and address | `fingerprintIsBoundToCustomerAndAddress` | PASS |
| — | COD split rolls back every Order on failure | `f11_codSplitRollsBackAllOrders` | PASS |
| — | Default config: DELIVERY open, ONLINE closed, kill switch | `CheckoutV3PolicyGateDatabaseIntegrationTest` | PASS |

BE03-01 .. BE03-30 of the first delivery (`CheckoutV3DatabaseIntegrationTest`, `CheckoutV3ConcurrencyDatabaseIntegrationTest`) still pass; BE03-06b now asserts the **split** instead of a refusal.

## 4. Concurrency and rollback evidence (SQL Server, committed, restored)

- **Last unit, one Store** (3 rounds): exactly one Order, loser refused, stock 0 not −1, 1 `ORDER` movement `1 → 0`.
- **Last units across two Stores** (ONLINE): one Order wins, both StoreProducts end at 0, 1 Order / 2 Fulfillments / 2 movements / 1 payment.
- **Same SKU that needs the split, two customers** (consented COD): exactly one wins; both Stores end at 0; deltas 2 Orders / 2 Fulfillments / 2 items / 2 movements; the five units are all in the winner's items.
- **Same cart submitted three times at once:** one Order, deducted once.
- **Store, StoreProduct or reservation changes after the cart was filled:** refused each time, table counts unchanged, stock unchanged.
- **Failure on the second Store's deduction (ONLINE)**, **failure on the second Order of a COD split**, **failure of the payment initialization:** table counts, stock and cart equal to before.
- **Opposite SKU order, two customers (3 rounds):** both succeed, no deadlock.
- Every race has a 60-second timeout.

## 5. Classification of the 383 non-passing test cases (identical before and after)

| Cause (message of the **baseline** run) | Cases | Classification |
|---|---:|---|
| `Cannot insert the value NULL into column …` — the V2 checkout inserts `order_items` without `fulfillment_id` (NOT NULL in V3) | 181 | **FAIL — Legacy V2 incompatible** (V2 checkout could not run on the V3 schema even before BE-03; V3 replaces it) |
| `Incorrect result size: expected 1, actual 0` — V2 fixtures look up V2 seed rows that the V3 seed does not contain | 79 | **FAIL — Legacy V2 incompatible / fixture** |
| `NullPointerException … CartItem.getStoreProduct()` — V2 cart helpers on the BE-02 central cart | 26 | **FAIL — Legacy V2 incompatible** |
| `delete from dbo.reviews` blocked by `FK_review_replies…` — V2 cleanup | 24 | **FAIL — Legacy V2 incompatible** (BE-06) |
| Assertion differences in Catalog/Home/Seed/Review/Staff/Admin V2 tests (HTML, ordering, prices, stock) | ≈73 | **FAIL — pre-existing, not analysed one by one** |
| **Total** | **383** | none is new |

After BE-03 the first row's tests fail one step earlier with the explicit refusal "Luồng đặt hàng theo từng chi nhánh (V2) đã ngừng" (184 cases): same test cases, same outcome, clearer cause.

Skipped (42, unchanged): `CartDatabaseIntegrationTest` (23) and `CartHttpDatabaseIntegrationTest` (15) are `@Disabled` since BE-02 ("superseded"); `LargeSeedWebIntegrationTest` (3) needs `SEED_LARGE_TEST=true`; 1 conditional skip in `CatalogHttpDatabaseIntegrationTest`.

## 6. Test cases removed/rewritten (and why)

| Removed | Reason | Replaced by |
|---|---|---|
| `CheckoutWebIntegrationTest.checkoutPageHasOneBlockPerStoreWithSuggested…`, `…theFormIsBoundToOneChoicePerStore…` | V2 form with one block per Store no longer exists | V3 web tests (10) |
| `PaymentWebIntegrationTest.viewHasOrderStoreTotalAndPendingControls` | asserted browser "simulate success/failure" buttons (removed by DEC-21) | `viewHasOrderStoreTotalDeadlineAndNoBrowserPaymentResult` |
| `PaymentServiceTest.successIsIdempotent…`, `failureIsIdempotent…`, `conflictingResultsAreRefused`, `failedCannotBecomeSuccessful`, `missingOrderOrAttemptIsNotFound` | exercised browser-driven confirmation, now always refused | `aBrowserCanNeitherConfirmNorFailAPayment`, `noNewAttemptAfterThePaymentDeadline`, `missingOrderIsNotFound` |

No test was edited to hide a failure.

## 7. Schema validation

Every `dev`-profile Spring context of these runs starts with `spring.jpa.hibernate.ddl-auto=validate` against the 22-table schema; no DDL, `ALTER`, migration, rebuild or seed script was executed.

## 8. BLOCKED / not covered (belongs to later phases)

Payment SUCCESS/FAILED verification, late callback, timeout, reconciliation, Admin resolution, refund, cancel races (BE-05); Staff Fulfillment transitions and aggregate Order status (BE-04); Review per OrderItem and Reply (BE-06). There is no BLOCKED item left for BE-03.
