> Historical phase evidence (up to 14/10/2026). Superseded script names and readiness claims describe that run only. Current setup: [database README](../../seed/DATABASE_SETUP.md); current acceptance: [deep audit](../final-deep-audit/DEEP_AUDIT_REPORT.md).

# V3-BE-03 — HANDOVER TO V3-BE-04 AND V3-BE-05

**Phase completed:** V3-BE-03 (status **DONE for its scope**, see `V3_BE03_IMPLEMENTATION_REPORT.md`; OPEN-01/02/03 decided as DEC-23/24/25; ONLINE stays gated until BE-05)  
**Next phases:** V3-BE-04 (Fulfillment operations), V3-BE-05 (payments, timeout, cancellation, refund)  
BE-01 and BE-02 acceptance reports are not modified.

---

## 1. Contracts provided

### 1.1 Checkout (`CheckoutService`)

| Method | Meaning |
|---|---|
| `prepare(email, cartItemIds)` | page with default choices (DELIVERY, COD); delegates to `preview` |
| `preview(email, ids, fulfillmentType, paymentMethod, pickupStoreId, shippingAddress)` | read-only: allocation plan, pickup Stores, fee, order count, total, `splitConsentRequired`, closed-flow reasons |
| `placeOrder(email, PlaceOrderRequest)` | the V3 transaction. Throws `CheckoutConflictException` (code) or `BadRequestException` |
| `placeOrder(email, CheckoutRequest)` | V2 shape; `default` method that always refuses (`@Deprecated`) |
| `getCheckout(email, id)` | session with its Orders, Fulfillments, items, deadline |

`PlaceOrderRequest`: `cartItemIds`, `fulfillmentType`, `paymentMethod`, `receiverName`, `receiverPhone`, `shippingAddress` (DELIVERY), `pickupStoreId` (STORE_PICKUP), `splitCodConsent` (`Boolean`), `planSignature`, `expectedTotal`. No price, no fee, no Store for DELIVERY, no status.

`planSignature` is the fingerprint of the previewed plan: customer, lines, allocation (StoreProduct by StoreProduct), method, address/pickup Store, fee per Order, number of Orders and total. It is recomputed on the backend; a mismatch gives `PLAN_CHANGED`, a changed total gives `TOTAL_CHANGED`.

`CheckoutConflictException.getCode()`: `PLAN_CHANGED`, `TOTAL_CHANGED`, `COD_SPLIT_CONSENT_REQUIRED`, `DELIVERY_POLICY_PENDING`, `ONLINE_CHECKOUT_DISABLED`, `NOT_ALLOCATABLE`.

### 1.2 Allocation (`StoreAllocationService`)

`preview(...)` (unlocked, read-only), `allocateForUpdate(...)` (must be in a write transaction; returns the locked rows with the result), `pickupStoresCovering(...)`. The engine itself is `AllocationEngine` (pure).

### 1.3 Payment (`PaymentService`) and deadline (`PaymentDeadlinePolicy`)

- `initializeOnlineAttempt(Order)` — MANDATORY transaction; one `PENDING` attempt for `orders.total_amount`, reference `MOCK-<uuid>`.
- `markOnlinePaymentSuccess/Failed` — always refused (DEC-21). BE-05 provides the verified path.
- `PaymentDeadlinePolicy.paymentDeadline(order)` = persisted `created_at` (rounded to the stored second) + `oneshop.payment.pending-timeout-minutes`; `reconciliationDeadline(order)`; `isPayableNow`; `isExpired`; `allowsFulfillmentWork`.

### 1.4 Data written by a checkout

| Table | Rows |
|---|---|
| `checkout_sessions` | 1 (`CREATED`, total = Σ Orders) |
| `orders` | 1 (ONLINE, COD one Store, PICKUP) or N independent (consented COD split); `shipping_fee` 30.000 (DELIVERY) or 0 (pickup) **per Order** |
| `order_fulfillments` | 1 per supplying Store of each Order, `CONFIRMED` |
| `order_items` | 1 per allocated **source**: a SKU split between Stores has one item per StoreProduct, each with its `fulfillment_id` |
| `inventory_movements` | 1 `ORDER` per deducted StoreProduct per Order |
| `order_status_history`, `order_fulfillment_status_history` | initial row each |
| `payments` | ONLINE only: 1 `PENDING` |
| `cart_items` | the checked-out lines are deleted |

---

## 2. For V3-BE-04 (Fulfillment operations)

1. **Queue source:** `order_fulfillments.store_id` through ACTIVE `staff_store_assignments`. The existing Staff services (`DeliveryFulfillmentServiceImpl`, `PickupFulfillmentServiceImpl`, `StaffOperationsService`) are still order-level (`orders.store_id`); BE-03 only made them refuse a multi-Store Order cleanly instead of failing with a null pointer. They must be replaced, not extended.
2. **Payment gate:** `PaymentDeadlinePolicy.allowsFulfillmentWork(order)` is the rule (ONLINE only when `PAID`; never `PENDING_PAYMENT`, `NEEDS_ATTENTION`, `CANCELLED`). Use it in every Fulfillment transition, under the Order lock.
3. **Initial state:** Fulfillments are created `CONFIRMED` (the only initial value the schema allows) while the Order may be `PENDING_PAYMENT`; `CONFIRMED` therefore does not mean "payable work".
4. **No second deduction:** `PREPARING`, `PACKED`, `SHIPPING`, `READY_FOR_PICKUP`, `COMPLETED` never touch `StoreProduct.quantity`.
5. **Aggregate Order status:** nothing in BE-03 computes it. The payment `NEEDS_ATTENTION` of BE-05 must not be overwritten by the aggregator.
6. **Single-Store Orders** still carry `orders.store_id` (legacy convenience). Do not treat it as an authorization source.
7. **Pickup code:** not generated at checkout; `order_fulfillments.pickup_code` / `ready_at` are BE-04.

## 3. For V3-BE-05 (payments, timeout, cancellation, refund)

1. **Boundary already in place:** PENDING status, `PENDING` attempt with whole total and `MOCK-` reference, deadline policy, ONLINE gate, browser results refused. The mock ledger/verification, `@Scheduled` worker (`oneshop.payment.timeout-scan-interval-ms`), reconciliation (`oneshop.payment.reconciliation-window-minutes`), Admin resolution and refund simulation are all still to build; see `BACKEND_V3_MIGRATION_PLAN.md` §5.
2. **Cancel-and-restore:** `InventoryService.restoreForCancelledOrder` now validates the Store through each item's Fulfillment and works for Orders with several Stores. `OrderServiceImpl.writeStatusAndHistory` cancels `CONFIRMED` Fulfillments (with history) when an Order becomes `CANCELLED`. This is a **bridge**, not the complete workflow: there is no race handling between cancel, timeout, callback and Staff, no refund, and `PaymentService.closePendingForCustomerCancellation` still closes a pending attempt as `FAILED`.
3. **Customer cancel while the payment result is unknown** is not specified by an approved rule beyond DEC-17; the plan proposes refusing it. Not implemented in BE-03.
4. **`PaymentService.createOnlinePaymentAttempt`** now refuses after the deadline and returns the existing `PENDING` attempt otherwise (it does not extend the deadline).
5. **Do not re-enable** `markOnlinePaymentSuccess/Failed` for browsers. The customer-facing simulate action must go through the mock gateway (DEC-21).

---

## 4. Audit result of checkout V2 (Part A)

| V2 element | Finding | Action |
|---|---|---|
| `CheckoutServiceImpl.placeOrder` read `line.getStoreProduct()`, grouped by Store, one Order per Store | would NPE after BE-02 and creates N Orders for ONLINE | **REWORK** (done) |
| `StoreGroupCheckoutRequest`, `CheckoutRequest` | per-Store request | **LEGACY** — kept only so legacy tests compile; service refuses `CheckoutRequest` |
| Price from `StoreProduct.price` | wrong source | **REWORK** (done: `Product.price`) |
| `orders.store_id NOT NULL` assumption in Staff/Review/Admin readers | NPE for multi-Store Orders | Staff services **guarded** (BE-04 to replace); `OrderViewService`, `PaymentServiceImpl` null-safe; **remaining**: `ReviewServiceImpl` (lines using `order.getStore()`), `AdminReviewService` → BE-06; `DeliveryFulfillmentServiceImpl.summary` only reads Orders found by Store |
| Browser `/orders/{id}/payments/{pid}/success|failure` | customer could confirm payment | **LEGACY** — service refuses; buttons removed from the page; routes remain until BE-05 replaces them |
| Payment per Order in a multi-Store checkout | N payments | **REWORK** (ONLINE multi-Store = one payment) |
| Staff approving Orders | does not exist (system confirms) | KEEP |
| Stock deducted at PACKED | does not exist (V2 deducted at checkout) | KEEP (deduct at creation) |
| Idempotency | cart row lock only | KEEP; no key storage added (no schema change) |
| `StoreProduct.price` column | still read by `StoreProductMapper` as a fallback and by Admin StoreProduct forms | **REMOVE AFTER VERIFICATION** (not part of BE-03) |
| `cart_items.store_product_id` | nullable bridge from BE-02 | KEEP; checkout never reads it |

## 5. Compatibility bridges still in place

`CheckoutRequest`/`StoreGroupCheckoutRequest` (legacy DTOs), 4-arg `CheckoutPreviewResponse`, 13-arg `OrderSummaryResponse`, 8-arg `OrderPaymentResponse`, `orders.store_id` for single-Store Orders, `CartResponse.groups`, `cart_items.store_product_id`, `StoreProduct.price`, V2 order-level Staff screens, V2 `OrderTransitionPolicy` graph, V2 customer cancel (`/orders/{id}/cancel`).

---

## 6. Configuration

| Property (default) | Meaning |
|---|---|
| `oneshop.payment.pending-timeout-minutes` (15) | time to pay from `created_at` |
| `oneshop.payment.reconciliation-window-minutes` (15) | BE-05 |
| `oneshop.payment.timeout-scan-interval-ms` (60000) | BE-05 |
| `oneshop.checkout.delivery-service-area-policy` (`ALL_DELIVERY_ENABLED_STORES`) | approved national coverage (DEC-24); `UNDECIDED` = kill switch that closes DELIVERY |
| `oneshop.checkout.shipping-fee-policy` (`FIXED_PER_ORDER`) | approved fee rule (DEC-25); `UNDECIDED` = kill switch |
| `oneshop.checkout.shipping-fee-per-order` (`30000`) | the fee per DELIVERY Order, VND |
| `oneshop.checkout.online-enabled` (`false`) | opens ONLINE checkout; **set only after BE-05** |

---

## 7. Decisions (all decided)

| # | Decision | State |
|---|---|---|
| OPEN-01 / DEC-23 | the quantity of one SKU may come from several Stores | Implemented. Review per OrderItem (OPEN-05) is a BE-06 matter: one SKU can now have several OrderItems and so several reviewable lines |
| OPEN-02 / DEC-24 | every ACTIVE Store with `delivery_enabled` serves every valid address in Vietnam | Implemented; no service-area table (`store_delivery_areas` is not needed) |
| OPEN-03 / DEC-25 | 30.000 VND per DELIVERY Order, once; COD split once per Order; pickup free | Implemented |

Still open for BE-05 (see plan §5.11): whether a customer may cancel an ONLINE Order while its payment result is UNKNOWN (proposed: no), reconciliation window value, Admin action wording, mock ledger persistence limits.

## 8. Known technical limits

- **Address:** only structure is validated; the schema has a single free-text line and no geographic data, so the country cannot be verified and is not claimed to be.
- Greedy cover: not minimal in the number of Stores; stock only, never distance.
- Checkout locks every sellable StoreProduct of the demanded SKUs, so two checkouts of the same SKU serialize (chosen for correctness).
- No exactly-once response guarantee for a request whose first attempt committed but whose answer was lost (no idempotency-key storage may be added).
- `created_at` has one-second precision (`DATETIME2(0)`); the deadline uses the stored, rounded value.
- ONLINE Orders created in tests stay `PENDING_PAYMENT` until BE-05; that is why the switch is off by default.
- The legacy V2 test suite (383 test cases) still fails exactly as before BE-03 and has to be rebuilt in BE-04/05/07 (`V3_BE03_TEST_RESULTS.md` §5).

## 9. Feature gates at hand-over

| Flow | State | Condition to change |
|---|---|---|
| STORE_PICKUP + PAY_AT_STORE | open | — |
| DELIVERY + COD (incl. consented split) | **open** | — |
| Same-SKU quantity split | **open** (algorithm) | — |
| ONLINE (pickup or delivery) | **closed** | BE-05 complete (verified payment, timeout, reconciliation, cancel/restore, refund simulation, Admin resolution) and its race matrix passing, then `oneshop.checkout.online-enabled=true` |
