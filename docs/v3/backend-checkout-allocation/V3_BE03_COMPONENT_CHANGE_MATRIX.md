> Historical phase evidence (up to 14/10/2026). Superseded script names and readiness claims describe that run only. Current setup: [database README](../../seed/DATABASE_SETUP.md); current acceptance: [deep audit](../final-deep-audit/DEEP_AUDIT_REPORT.md).

# V3-BE-03 — COMPONENT CHANGE MATRIX

Paths are relative to `src/main/java/com/oneshop/` unless a full path is given. Status: **NEW** (created), **MOD** (modified), **LEGACY** (kept for compatibility, not reachable from a V3 flow), **UNCHANGED** (examined, not changed).

## 1. Production code

| File | Class / method | V2 behaviour | V3 behaviour | Reason | Test | Status |
|---|---|---|---|---|---|---|
| `service/allocation/AllocationEngine.java` | `allocate`, `allocateAtStore`, `mustSplit`, `better` | Whole lines only; `QUANTITY_SPLIT_REQUIRED` when no Store held a whole line | Iterative greedy over remaining units; a line is split only when no Store can still give the whole remaining quantity; a Store gives at most what is missing; prefers Stores already in the plan | DEC-23 (completion) | `AllocationEngineTest` (20, incl. 300 generated carts) | MOD |
| `service/allocation/{AllocationDemand,StockOffer,AllocatedLine,StoreAllocation,AllocationResult,AllocationFailure,LockedAllocation}.java` | records/enum | — | Engine input/output, plan signature | — | `AllocationEngineTest` | NEW |
| `service/allocation/DeliveryServiceAreaPolicy.java` | `isDecided`, `canServe` | default `UNDECIDED` refused | default `ALL_DELIVERY_ENABLED_STORES`: Store `ACTIVE` + `delivery_enabled`, address never used; `UNDECIDED` kept as kill switch | DEC-24 | `PaymentDeadlinePolicyTest`, `CheckoutV3PolicyGate…`, `f13`, `f15` | MOD |
| `service/allocation/ShippingFeeCalculator.java` | `feePerOrder` | DELIVERY: `UNDECIDED` refused, `NONE` opt-in; pickup 0 | DELIVERY `FIXED_PER_ORDER` = `shippingFeePerOrder` (30.000); pickup 0; `UNDECIDED` kill switch | DEC-25 | `f21..f30`, `PaymentDeadlinePolicyTest` | MOD |
| `service/StoreAllocationService.java`, `service/impl/StoreAllocationServiceImpl.java` | `preview`, `allocateForUpdate`, `pickupStoresCovering` | Checkout grouped the cart by the Store of each CartItem's StoreProduct | Allocation on StoreProduct rows locked in ascending id; read-only preview | DEC-04/05 | `CheckoutV3DatabaseIntegrationTest` | NEW |
| `service/PaymentDeadlinePolicy.java` | `paymentDeadline`, `isPayableNow`, `isExpired`, `allowsFulfillmentWork` | no deadline | `created_at (rounded to the stored second) + 15 min`, injected `Clock`; payment gate | DEC-15 | `PaymentDeadlinePolicyTest` (8) | NEW |
| `config/CheckoutProperties.java`, `config/PaymentProperties.java`, `config/ClockConfig.java` | `@ConfigurationProperties`, `Clock` bean | — | `oneshop.checkout.*`: approved defaults (national delivery, fee 30.000), `shipping-fee-per-order`; `online-enabled=false`; `oneshop.payment.*` timings; one server clock | DEC-20, DEC-24, DEC-25 | `PaymentDeadlinePolicyTest` | NEW (completion: MOD) |
| `service/CheckoutService.java` | `prepare`, `preview`, `placeOrder(PlaceOrderRequest)`, `getCheckout` | `placeOrder(CheckoutRequest)` one Order per Store | V3 contract; V2 `placeOrder(CheckoutRequest)` is a `default` method that always refuses | DEC-06 | `legacyV2RequestIsRefused` | MOD |
| `service/impl/CheckoutServiceImpl.java` | `placeOrder`, `createOrder`, `preview`, `toResult`, `contextSignature`, `addressProblem` | Per-Store Orders, `StoreProduct.price`, per-line locks | One transaction: cart lock → candidate lock → allocation (with same-SKU split) → fee per Order → fingerprint/total checks → Order(s)/Fulfillments/Items → deduction → histories → Payment attempt → cart cleanup; address validated; fingerprint covers customer, lines, sources, method, address/pickup Store, fee, orders, total | DEC-05/06/09/10/15/16/23/24/25 | all `CheckoutV3*` | MOD (rewritten, then extended) |
| `controller/client/CheckoutController.java` | `form`, `placeOrder`, `result` | `groups[i].*` per Store | One choice for the whole order; re-render with a fresh preview on refusal; `CheckoutConflictException` code exposed | G2 | `CheckoutWebIntegrationTest` (10) | MOD |
| `dto/request/PlaceOrderRequest.java` | record | — | Cart lines, fulfillment type, payment method, receiver, optional `pickupStoreId`, `splitCodConsent` (wrapper: unticked box sends nothing), `planSignature`, `expectedTotal` | no price/Store/status from the browser | `CheckoutWebIntegrationTest` | NEW |
| `dto/request/CheckoutRequest.java`, `StoreGroupCheckoutRequest.java` | records | V2 request | unchanged; only legacy tests construct them | keep legacy tests compiling | `legacyV2RequestIsRefused` | LEGACY |
| `dto/response/{AllocationPlanResponse,PickupStoreOption,FulfillmentSummaryResponse}.java` | records | — | Plan shown on the page; Fulfillment parts of an Order | G1/G4 | web + HTTP tests | NEW |
| `dto/response/CheckoutPreviewResponse.java` | record | 4 fields | adds choice, policy flags, pickup Stores, plan, fee, order count, total, split flag (4-arg constructor kept) | C2 | `CheckoutWebIntegrationTest` | MOD |
| `dto/response/OrderSummaryResponse.java` | record | one Store per Order | `storeId` null for several Stores; adds Fulfillments, payment deadline, shipping fee (13-arg constructor kept) | G4 | `CheckoutV3DatabaseIntegrationTest` | MOD |
| `dto/response/OrderPaymentResponse.java` | record | — | adds `paymentDeadline`, `expired`; `payable()` false once expired (8-arg constructor kept) | F2 | `be03_19b…` | MOD |
| `exception/CheckoutConflictException.java` | class | — | Business refusal with a code (`PLAN_CHANGED`, `TOTAL_CHANGED`, `COD_SPLIT_CONSENT_REQUIRED`, `DELIVERY_POLICY_PENDING`, `ONLINE_CHECKOUT_DISABLED`, `NOT_ALLOCATABLE`) | C3 | DB + web tests | NEW |
| `service/PaymentService.java`, `service/impl/PaymentServiceImpl.java` | `initializeOnlineAttempt`, `markOnlinePaymentSuccess/Failed`, `createOnlinePaymentAttempt`, `getOrderPayments` | browser could mark SUCCESS/FAILED | success/failure from the browser always refused; attempt created by the backend with whole total and `MOCK-` reference; no attempt after the deadline; Store label null-safe | DEC-21, F3 | `be03_47…`, `be03_19…`, `be03_19b…` | MOD |
| `controller/client/PaymentController.java` | `success`, `failure` | calls the service | unchanged; both routes now end in a business refusal from the service | F4 | HTTP test | UNCHANGED |
| `service/impl/InventoryServiceImpl.java` | `deductForOrder`, `restoreForCancelledOrder` | deduct `<= quantity`; restore compared to `orders.store_id` | deduct `<= quantity - reserved_quantity`; restore compares to the item's Fulfillment Store (works for several Stores) | D2, DEC-16 | `be03_12…`, `InventoryRestoreTest` | MOD |
| `service/impl/OrderServiceImpl.java` | `writeStatusAndHistory` → `cancelFulfillments` | cancel changed only the Order | cancelling an Order also cancels its `CONFIRMED` Fulfillments (+history) in the same transaction; refuses if any has started | keep V3 data consistent until BE-05 | `OrderServiceTest` (existing) | MOD (bridge) |
| `service/OrderViewService.java` | `summary` | `o.getStore()` | null-safe for Orders without a single Store | multi-store Orders | HTTP test | MOD |
| `service/impl/DeliveryFulfillmentServiceImpl.java`, `service/impl/PickupFulfillmentServiceImpl.java`, `service/StaffOperationsService.java` | `perform` / `lockedStaffEmail` | `order.getStore().getId()` (NPE for a multi-store Order) | clean business refusal for an Order without a single Store; ONLINE still blocked until PAID | F4 | `be03_20…` | MOD (guard) |
| `repository/StoreProductRepository.java` | `findCheckoutCandidateIdsByProductIds`, `findCheckoutCandidatesByProductIds` | — | ids-only candidate query for the locking read; entity query for the preview | D2 | DB tests | MOD |
| `repository/OrderFulfillmentRepository.java` | `findByOrderIdInOrderByIdAsc` | — | Fulfillments of several Orders with Store | G4 | HTTP test | MOD |
| `src/main/resources/application.properties` | `oneshop.payment.*`, `oneshop.checkout.*` | — | official property names; defaults updated by the completion work (see 1.1): national delivery, fee 30.000, ONLINE closed | DEC-20, DEC-24, DEC-25 | `PaymentDeadlinePolicyTest` | MOD |

### 1.1 Added by the completion work (OPEN-01/02/03)

| File | Class / method | Previous behaviour | New behaviour | Test | Status |
|---|---|---|---|---|---|
| `service/allocation/AllocationFailure.java` | enum | had `QUANTITY_SPLIT_REQUIRED` | constant removed (no longer an outcome) | `AllocationEngineTest` | MOD |
| `service/allocation/ShippingAddressValidator.java` | `requireValid` | free text only length-checked | structure check: 10–500 chars, ≥ 3 words, ≥ 2 with letters, no control chars or `<` `>`; whitespace normalized; cannot verify the country | `ShippingAddressValidatorTest` (14), `f17` | NEW |
| `dto/response/CheckoutPreviewResponse.java` | `shippingFeeTotal()` | — | fee × number of Orders, for the page | `CheckoutV3HttpDatabaseIntegrationTest` | MOD |
| `src/main/resources/templates/checkout/index.html` | plan section, summary | per Order only subtotal | per Order: items, fee, amount to pay on delivery; total fee line "(n đơn)"; note for a multi-Store ONLINE order | `CheckoutV3HttpDatabaseIntegrationTest`, `CheckoutWebIntegrationTest` | MOD |
| `src/main/resources/templates/checkout/result.html` | order footer | "Tổng đơn tại chi nhánh" | shows the shipping fee and "Tổng đơn" | HTTP test | MOD |
| `src/main/resources/application.properties` | `oneshop.checkout.*` | `UNDECIDED` defaults | `ALL_DELIVERY_ENABLED_STORES`, `FIXED_PER_ORDER`, `30000`; `online-enabled=false` kept | `CheckoutV3PolicyGateDatabaseIntegrationTest` | MOD |
| `src/test/.../CheckoutV3SplitAndFeeDatabaseIntegrationTest.java` | BE03-F02..F30 | — | split, national delivery, fee, fingerprint binding | itself (18) | NEW |
| `src/test/.../CheckoutV3PolicyGateDatabaseIntegrationTest.java` | default config | tested the closed state | tests the approved default (open DELIVERY, fee, ONLINE closed, kill switch) | itself (4) | MOD |
| `src/test/.../CheckoutV3ConcurrencyDatabaseIntegrationTest.java` | races | 6 tests | +3: split SKU race, Store/StoreProduct/reserved change, COD split rollback | itself (9) | MOD |
| `src/test/.../SeedGuardV3.java` | restore | no Store flags | also restores `stores.status/delivery_enabled/pickup_enabled` | concurrency tests | MOD |

## 2. Templates

| File | V2 | V3 | Status |
|---|---|---|---|
| `templates/checkout/index.html` | one block per Store (`groups[i]`) | one choice for the whole order; closed flows shown as closed; allocation plan; COD split consent; pickup Store list; 15-minute rule text | MOD (body replaced, head/CSS kept) |
| `templates/checkout/result.html` | "an order per Store" | one Order with its Fulfillments, or N independent COD Orders; payment deadline | MOD |
| `templates/payment/index.html` | buttons "Mô phỏng thành công / thất bại" | buttons removed; deadline shown | MOD |

## 3. Tests

| File | Purpose | Status |
|---|---|---|
| `service/allocation/AllocationEngineTest.java` | engine rules | NEW |
| `service/PaymentDeadlinePolicyTest.java` | deadline, gate, fail-closed switches (controlled clock) | NEW |
| `CheckoutV3DatabaseIntegrationTest.java` | BE03-01..30 on SQL Server (rolled back) | NEW |
| `CheckoutV3SplitAndFeeDatabaseIntegrationTest.java` | BE03-F02..F30: split, national delivery, fee, fingerprint | NEW |
| `service/allocation/ShippingAddressValidatorTest.java` | address structure rules | NEW |
| `CheckoutV3PolicyGateDatabaseIntegrationTest.java` | default configuration: DELIVERY open with fee, ONLINE closed, kill switch | NEW (updated) |
| `CheckoutV3ConcurrencyDatabaseIntegrationTest.java` | races, fault injection, lock order (committed, restored) | NEW |
| `CheckoutV3HttpDatabaseIntegrationTest.java` | real HTTP + real data + Thymeleaf | NEW |
| `SeedGuardV3.java` | V3 restore helper (refuses non-`*_test` databases) | NEW |
| `CheckoutWebIntegrationTest.java` | rewritten for the V3 form | MOD |

## 4. Not changed on purpose

`Cart*` (BE-02), schema and seed (`database/`), `OrderTransitionPolicy`, review flows, Staff/Admin screens other than the null-store guards, the legacy `StoreProduct.price` column, `reserved_quantity`.
