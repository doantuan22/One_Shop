# V3-BE-04 — IMPLEMENTATION REPORT
## Multi-Branch Fulfillment Operations

**Phase status: DONE for the BE-04 scope** (13/10/2026).  
DONE here does **not** mean ONLINE payments are ready: ONLINE checkout stays closed
(`oneshop.checkout.online-enabled=false`) until BE-05. BE-05 and BE-06 were **not** started.

---

## 1. Executive summary

| Area | Result |
|---|---|
| Staff authorization by `StaffStoreAssignment` + `order_fulfillments.store_id` (never `orders.store_id`) | Implemented, 403/404 semantics tested, re-checked on the locked row |
| Fulfillment queue, detail and dashboard | Implemented (SQL-scoped, paginated, filters; counters per Store) |
| DELIVERY state machine | Implemented: CONFIRMED → PREPARING → PACKED → SHIPPING → COMPLETED / FAILED |
| STORE_PICKUP state machine | Implemented: CONFIRMED → PREPARING → READY_FOR_PICKUP → COMPLETED / FAILED |
| Pickup code (per Fulfillment, unique per Store, never shown to Staff, 5-try lock) | Implemented |
| Order status aggregation (`OrderStatusAggregator`) | Implemented; never overrides PENDING_PAYMENT / NEEDS_ATTENTION / CANCELLED |
| Payment gate (`allowsFulfillmentWork`) on every step | Implemented, under the Order lock |
| COD / PAY_AT_STORE collection without faking PAID | Implemented (`recordOfflineCollected`) |
| No second stock deduction, no InventoryMovement for any status change | Verified |
| FAILED → NEEDS_ATTENTION only (no restock, no refund) | Implemented, verified |
| Lock order Order → Fulfillment; cancel vs PREPARING race | Verified on SQL Server (committed rounds) |
| Customer order page with several Fulfillments | Implemented |
| ONLINE checkout | Closed by design (gate) |
| Schema / DDL / seed | **Unchanged** |
| Regression (per test case) | **0 regressions**; 120 new cases, all pass |

---

## 2. Audit (Part A) — what existed and what happened to it

| V2 element | Finding | Action |
|---|---|---|
| `StaffOrderController`, `StaffDeliveryController`, `StaffPickupController`, `StaffPickupQueueController` | order-level, `orders.store_id` | **DEL** → `StaffFulfillmentController`; V2 GET URLs redirect |
| `StaffOperationsService` | order-level dashboard/queue/mutation | dashboard **MOD** (Fulfillment counters); legacy methods **GUARD** |
| `DeliveryFulfillmentServiceImpl`, `PickupFulfillmentServiceImpl` | wrote V2 Order statuses (`PREPARING`, `PACKED`, `READY_FOR_PICKUP`) that the Order CHECK constraint rejects | **GUARD** (refuse Orders with Fulfillments); remove in BE-07 |
| `OrderTransitionPolicy` fulfilment graph, `DeliveryAction`, `PickupAction` | V2 graph | kept for the V2 customer cancel policy; Staff no longer uses it |
| `PaymentService.recordCodCollected` / `recordPayAtStoreCollected` | require V2 Order statuses | kept; V3 uses `recordOfflineCollected` |
| `PickupCodeService` | 8-char generator + constant-time verify (order-scoped) | **reused** per Fulfillment, + limiter |
| `StaffStoreScopeService` | SecurityContext + ACTIVE assignments | **reused** unchanged |
| Order status aggregation | none | **NEW** |
| Templates under `staff/orders|pickup|delivery` | order-level | **DEL**/replaced |

---

## 3. Architecture

```text
StaffFulfillmentController   (reads trackingCode / pickupCode / reason only; GET filters)
   |
StaffFulfillmentService  (@Transactional)
   |-- StaffStoreScopeService          assigned Store ids from SecurityContext + DB, every call
   |-- OrderFulfillmentRepository.findKeyById      scalar scope check  (404 / 403)
   |-- OrderRepository.findByIdForUpdate           Order lock           (1st)
   |-- OrderFulfillmentRepository.findByIdForUpdate Fulfillment lock    (2nd) + Store re-check
   |-- PaymentDeadlinePolicy.allowsFulfillmentWork  payment gate
   |-- FulfillmentAction state machine
   |-- PickupCodeService + PickupAttemptLimiter    pickup code
   |-- PaymentService.recordOfflineCollected       COD / PAY_AT_STORE
   |-- order_fulfillments (+ status history)       status, timestamps
   `-- OrderStatusAggregator.recompute             orders.order_status (+ history when changed)
```

Full rules: `V3_BE04_STATE_MACHINE_AND_AUTHORIZATION.md`. Component list: `V3_BE04_COMPONENT_CHANGE_MATRIX.md`.

---

## 4. Decisions taken in this phase (inside the approved rules)

| # | Decision | Why |
|---|---|---|
| D1 | A Fulfillment of a Store the Staff is not assigned to → **403**; unknown id → 404 | spec V3-T16 allows 403/404; 403 is explicit and testable |
| D2 | `FAILED` reachable from `SHIPPING` (DELIVERY) and `READY_FOR_PICKUP` (STORE_PICKUP), reason mandatory, Order → `NEEDS_ATTENTION` | spec 9.1 / 10.2 minimum; nothing is restocked or refunded without a decision (BE-05) |
| D3 | No Staff re-open / re-deliver / cancel of a part | not decided; would need an Admin action (BE-05) |
| D4 | Offline collection only for single-Fulfillment Orders; the whole `total_amount` is collected once | amount per part is not stored; COD split = independent Orders |
| D5 | Pickup code attempts limited in memory (5 → 15 min lock) | a wrong code rolls back the transaction, so a database counter cannot persist |
| D6 | Customer cancel after a Store started → 409 `ORDER_ALREADY_PREPARING`; in `NEEDS_ATTENTION` → 409 `ORDER_NEEDS_ATTENTION` | spec 10.1 |
| D7 | `handover` URL as the plan names it, plus explicit `complete-delivery` / `complete-pickup` | plan BE-04 task list |
| D8 | Aggregation follows spec 9.3 priority (plan's "COMPLETED + CANCELLED → PARTIALLY_COMPLETED" cannot occur) | whole-Order cancel needs every part CONFIRMED |

No approved V3 rule (DEC-01..25) was changed.

---

## 5. Behaviour table (what a Staff member can and cannot do)

| Situation | Result |
|---|---|
| Own Store, COD, steps in order | each step succeeds; Order PROCESSING → SHIPPING → COMPLETED; COD collected once at completion |
| Own Store, PAY_AT_STORE pickup | READY generates the code; completion with the code collects payment |
| Other Store's part | 403, nothing written |
| ONLINE `PENDING_PAYMENT` / unverified | 409, held, shown as "đang bị giữ" |
| `NEEDS_ATTENTION` / `CANCELLED` Order | 409, not cleared by the aggregator |
| Wrong pickup code | 400, nothing written; 5 → locked 15 minutes |
| Repeat / skip a step | 409 `FULFILLMENT_STATUS_CHANGED` |
| Failed part | Order `NEEDS_ATTENTION`, stock and payment untouched |
| Customer cancels after PREPARING | 409 `ORDER_ALREADY_PREPARING` |
| Staff sends `orderStatus`, `storeId`, `paymentStatus` | ignored |

---

## 6. Definition of Done checklist

| Item | Status |
|---|---|
| Staff scope from assignments + `order_fulfillments.store_id` | DONE |
| Queue / detail / dashboard from Fulfillments | DONE |
| DELIVERY and STORE_PICKUP state machines | DONE |
| Pickup code generation and verification | DONE |
| `OrderStatusAggregator`; Staff cannot edit Order status | DONE |
| Payment gate reuse; COD / PAY_AT_STORE not faked | DONE |
| No second deduction / no movement on status change | DONE (tests 18, 41, 42) |
| FAILED → NEEDS_ATTENTION only | DONE |
| Lock order, cancel vs PREPARING race | DONE (tests 49–52) |
| Controllers, DTOs, Thymeleaf (dashboard, queue, detail, pickup, customer parts) | DONE |
| Tests BE04-01..50 + extras, per-case baseline comparison | DONE (120 new, 0 regressions) |
| Five documents in `docs/v3/backend-fulfillment/` | DONE |
| No schema change, ONLINE gate closed, BE-05/06 not started | DONE |

---

## 7. Known limits / follow-ups (also in the handover)

1. V2 order-level Staff services are dead code behind guards; remove with the V2 test rebuild (BE-07).
2. A `FAILED` part cannot be re-opened by anyone until BE-05 defines Admin handling.
3. The pickup-code limiter is per application instance and in memory.
4. COD / PAY_AT_STORE collection refuses an Order with more than one Fulfillment (cannot occur with current checkout).
5. 383 V2-seed-dependent test cases still fail exactly as before (unchanged set); 42 skipped as before.
6. No browser (Selenium) run; HTTP-level tests use the real Tomcat/security/SiteMesh/Thymeleaf stack.

---

## 8. Phase status

| Phase | Status |
|---|---|
| V3-BE-04 Multi-Branch Fulfillment Operations | **DONE** |
| V3-BE-05 (payments verification, timeout, reconciliation, Admin resolution, refund simulation) | NOT STARTED (as instructed) |
| V3-BE-06 (reviews) | NOT STARTED |
