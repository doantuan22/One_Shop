> Historical phase evidence (up to 14/10/2026). Superseded script names and readiness claims describe that run only. Current setup: [database README](../../seed/DATABASE_SETUP.md); current acceptance: [deep audit](../final-deep-audit/DEEP_AUDIT_REPORT.md).

# V3-BE-03 — IMPLEMENTATION REPORT
## Centralized Checkout & Multi-Branch Order Allocation

**Phase status: DONE for the BE-03 scope** (after the completion work of 13/10/2026).  
DONE here does **not** mean the website is ready for ONLINE payments: ONLINE checkout is deliberately closed (`oneshop.checkout.online-enabled=false`) until BE-05 exists. History: the first delivery on 13/10/2026 was PARTIAL because OPEN-01/02/03 were undecided; the owner decided them (DEC-23, DEC-24, DEC-25) and this document reflects the final state.

---

## 1. Executive summary

| Area | Result |
|---|---|
| Central checkout (no Store chosen for DELIVERY) | Implemented |
| One-Store priority, greedy Multi-Store | Implemented, deterministic |
| **Same-SKU quantity split between Stores (OPEN-01, DEC-23)** | **Implemented** — an invariant-tested engine, one OrderItem per source StoreProduct |
| **National delivery coverage (OPEN-02, DEC-24)** | **Implemented** — every ACTIVE Store with `delivery_enabled`; no GPS/distance/province rule; address structurally validated |
| **Shipping fee 30.000 VND per Order (OPEN-03, DEC-25)** | **Implemented** — once per Order, per independent Order of a COD split, 0 for pickup; backend only |
| ONLINE multi-Store = 1 Order + N Fulfillments + 1 payment attempt | Implemented, SQL Server verified |
| COD one Store / COD split with consent / pickup | Implemented, verified |
| Immediate stock deduction + `ORDER` movement, no reservation | Implemented, verified |
| Rollback, no overselling, concurrency | Verified on SQL Server |
| 15-minute deadline, payment gate, no browser payment result | Implemented, verified |
| Plan fingerprint (customer, lines, sources, method, address/pickup Store, fee, order count, total) | Implemented, verified |
| ONLINE checkout | **Closed by design (feature gate)** until BE-05 |
| Schema / DDL | Unchanged |

---

## 2. Architecture

```text
CheckoutController
   |  GET  /checkout   -> CheckoutService.preview(...)       read only, reserves nothing
   |  POST /checkout   -> CheckoutService.placeOrder(PlaceOrderRequest)
   v
CheckoutServiceImpl  (one transaction)
   |-- ShippingAddressValidator                structure of the delivery address (DELIVERY)
   |-- CartRepository.findByUserIdForUpdate    customer's own cart, locked
   |-- StoreAllocationService.allocateForUpdate candidate StoreProducts locked (ascending id); allocation on the locked rows
   |       `-- AllocationEngine (pure)  +  DeliveryServiceAreaPolicy (national, delivery-enabled Stores)
   |-- ShippingFeeCalculator                   30.000 per DELIVERY Order, 0 for pickup
   |-- plan fingerprint                        customer + lines + sources + method + address/pickup + fee + orders + total
   |-- Order / OrderFulfillment / OrderItem / CheckoutSession (+ histories)
   |-- InventoryService.deductForOrder         StoreProduct.quantity, InventoryMovement(ORDER)
   |-- PaymentService.initializeOnlineAttempt  ONLINE only, whole total, PENDING, MOCK- reference
   `-- CartItemRepository.deleteAll(checked-out lines)
```

---

## 3. What changed in the completion work (OPEN-01/02/03)

| Part | Change |
|---|---|
| Allocation (A) | `AllocationEngine.allocate` Strategy 2 rewritten: iterative greedy over *remaining units*; a line is split only when no Store can still supply the whole remaining quantity (`mustSplit`); a Store gives at most the missing quantity; prefers Stores already in the plan (no extra Fulfillment). `AllocationFailure.QUANTITY_SPLIT_REQUIRED` removed (it no longer exists as an outcome). Pickup is unchanged: one Store, no split. |
| Delivery (B) | `DeliveryServiceAreaPolicy` default `ALL_DELIVERY_ENABLED_STORES`; `ShippingAddressValidator` (new); candidate query already filters ACTIVE Store/StoreProduct/Product/Category/Brand. `UNDECIDED` constants kept only as a fail-closed switch. |
| Fee (C) | `CheckoutProperties` gains `shippingFeePerOrder` (default 30000) and `ShippingFeePolicy.FIXED_PER_ORDER` replaces the unapproved `NONE`; `ShippingFeeCalculator` returns 30.000 / 0. The per-Order sum was already structural in `placeOrder` (one fee per created Order). |
| Fingerprint (A5) | `contextSignature(...)` in `CheckoutServiceImpl`, same function for preview and place order; checked after the total (`TOTAL_CHANGED` first, then `PLAN_CHANGED`). |
| UI (E) | Checkout page: per-Order amounts (items, fee, to pay) for a COD split, total fee line, note for a multi-Store ONLINE order; result page shows the fee. |
| Config | `application.properties`: defaults updated; `online-enabled` still `false`. |

Components reused unchanged: `CheckoutService` interface, `StoreAllocationService`, `InventoryService`, `PaymentService`, `PaymentDeadlinePolicy`, transaction/locking order, rollback behaviour.

---

## 4. Flows (final)

- **ONLINE (gated):** one Order `PENDING_PAYMENT`; N Fulfillments; one `PENDING` Payment for `items + 30.000`; deadline `created_at + 15 min`.
- **COD one Store:** one Order `CONFIRMED`; total `items + 30.000`; no Payment row.
- **COD several Stores:** refused without consent; with consent and the matching fingerprint, N independent Orders, each `items_i + 30.000`; session total = Σ Orders.
- **STORE_PICKUP:** one Order, one Fulfillment at the chosen Store which holds the whole cart; fee 0; ONLINE or PAY_AT_STORE.

## 5. Allocation (final)

See `V3_BE03_ALLOCATION_AND_TRANSACTION_DESIGN.md`. One Store with the whole cart first; otherwise greedy over missing units where a line is split only when it must be. Deterministic; same data gives the same plan and fingerprint. Limits: greedy (not minimal in Stores), stock only, never distance.

## 6. Immediate deduction, locking, rollback

Unchanged from the first delivery and re-verified with split sources: every candidate StoreProduct locked in ascending id, allocation on the locked rows, one `ORDER` movement per StoreProduct, `reserved_quantity` never written, availability `quantity - reserved_quantity`, all in one transaction.

---

## 7. Feature gates (final state)

| Gate | Value | Why |
|---|---|---|
| `oneshop.checkout.delivery-service-area-policy` | `ALL_DELIVERY_ENABLED_STORES` | OPEN-02 decided |
| `oneshop.checkout.shipping-fee-policy` / `shipping-fee-per-order` | `FIXED_PER_ORDER` / `30000` | OPEN-03 decided |
| `oneshop.checkout.online-enabled` | **`false`** | BE-05 (verified payment, timeout, reconciliation, cancel/restore, refund simulation) not done |

DELIVERY is now open; ONLINE is not. Quantity split is not a switch: it is the algorithm.

## 8. Known limits

- **Address:** only structure is validated (10–500 chars, ≥ 3 words, ≥ 2 with letters, no control characters/angle brackets). The schema has one free-text `address_line`; the country **cannot** be verified and is not claimed to be.
- **Greedy:** not guaranteed to use the minimum number of Stores.
- **Idempotency:** no key storage; cart lock prevents double deduction, but "exactly-once response" is not claimed.
- **ONLINE:** an ONLINE Order created in tests stays `PENDING_PAYMENT` until BE-05 exists.

## 9. Definition of Done (Part H)

| Item | Status |
|---|---|
| Same-SKU split works; totals equal requested quantity | Done |
| OrderItems trace StoreProduct/Fulfillment; no wrong duplicate Fulfillment | Done |
| Allocation deterministic; One-Store priority kept | Done |
| National DELIVERY by ACTIVE + delivery-enabled Stores; no GPS/nearest | Done |
| No Cross-Store Transfer | Done |
| Fee 30.000 per Order; ONLINE multi-Fulfillment one fee; COD split per Order; pickup 0; backend computed | Done |
| Preview/place order recheck plan, price and fee | Done |
| Immediate deduction unchanged; `reserved_quantity` not increased; `ORDER` movements exact | Done |
| No overselling; atomic rollback; COD split needs consent; no child Orders | Done |
| No schema modification | Done |
| SQL Server integration and concurrency tests pass | Done |
| BE-01 / BE-02 regression tests pass | Done |
| No new failing test vs the pre-BE-03 baseline | Done (0 new failures, per-testcase comparison) |
| Documents synchronized | Done |
| ONLINE feature gate kept for BE-05 | Done |
| Nothing of BE-04/BE-05 implemented | Done |

**Conclusion:** BE-03 is DONE for its implementation scope. Persistent failures of the legacy V2 test suite (383 test cases) are unchanged from the baseline and are not caused by this phase; they must be rebuilt in BE-04/05/07.
