> Historical phase evidence (up to 14/10/2026). Superseded script names and readiness claims describe that run only. Current setup: [database README](../../seed/DATABASE_SETUP.md); current acceptance: [deep audit](../final-deep-audit/DEEP_AUDIT_REPORT.md).

# V3-BE-04 — HANDOVER TO V3-BE-05 (AND BE-06 / BE-07)

**Phase completed:** V3-BE-04 Multi-Branch Fulfillment Operations — see `V3_BE04_IMPLEMENTATION_REPORT.md`.  
**Not started:** V3-BE-05 (mock gateway verification, timeout worker, reconciliation, Admin resolution, refund
simulation), V3-BE-06 (reviews). ONLINE checkout is still closed (`oneshop.checkout.online-enabled=false`).

---

## 1. Contracts provided by BE-04

### 1.1 Service API (`com.oneshop.service.fulfillment`)

| Member | Contract |
|---|---|
| `StaffFulfillmentService.getQueue(filter, page)` | Staff queue of the assigned Stores' Fulfillments; page size 20; open work first (oldest first). Authority from `SecurityContext`. |
| `StaffFulfillmentService.getFulfillment(id)` | Detail; 404 unknown, 403 other Store. Never contains the pickup code. |
| `StaffFulfillmentService.perform(id, action, input)` | One transactional step: authorize → lock Order → lock Fulfillment → re-check Store → payment gate → state machine → side effects → status + history → aggregate. |
| `StaffFulfillmentService.handover(id, pickupCode)` | Completes a DELIVERY or STORE_PICKUP part by its type. |
| `FulfillmentAction` | the only steps; no `CANCELLED` target. |
| `OrderStatusAggregator.derive(statuses, paid)` | pure rule (spec 9.3); `recompute(lockedOrder, actor, note)` writes the Order status + history when it changes. **Never** leaves `PENDING_PAYMENT`, clears `NEEDS_ATTENTION`, or writes `CANCELLED`. |
| `PaymentService.recordOfflineCollected(order, fulfillment)` | the only way COD / PAY_AT_STORE become PAID; single-Fulfillment Orders only. |
| `FulfillmentConflictException` | 409 with a code: `FULFILLMENT_STATUS_CHANGED`, `PAYMENT_PENDING`, `PAYMENT_NOT_VERIFIED`, `ORDER_NEEDS_ATTENTION`, `ORDER_CANCELLED`, `ORDER_ALREADY_PREPARING`, `PICKUP_CODE_LOCKED`, `COLLECTION_BLOCKED`, `INCONSISTENT_FULFILLMENT`. |

### 1.2 HTTP

`GET /staff/fulfillments` (alias `/staff/orders`), `/staff/fulfillments/delivery`, `/staff/fulfillments/pickup`,
`/staff/fulfillments/{id}`; `POST /staff/fulfillments/{id}/{prepare|pack|ship|ready-for-pickup|complete-delivery|
complete-pickup|fail-delivery|fail-pickup|handover}`. V2 URLs (`/staff/orders/{id}`, `/staff/orders/delivery`,
`/staff/orders/pickup`, `/staff/pickup`) are GET redirects only.

### 1.3 Data the next phases can rely on

* `order_fulfillments.status` is the operational truth; `orders.order_status` is derived from it by BE-04 (except
  `PENDING_PAYMENT`, `CANCELLED`, `NEEDS_ATTENTION`, which belong to the payment/cancel/exception flows).
* `order_fulfillments.pickup_code` is per Fulfillment, 8 characters, unique within the Store; `ready_at`,
  `picked_up_at`, `completed_at`, `prepared_at`, `packed_at`, `shipped_at`, `tracking_code`, `note` are filled by steps.
* `order_fulfillment_status_history` has one row per transition with the acting Staff user; the creation row has
  `old_status IS NULL` and no user.
* A failed part leaves the Order `NEEDS_ATTENTION`, stock deducted, no refund, `payment_status` unchanged.

---

## 2. For V3-BE-05

1. **Resolving `NEEDS_ATTENTION`.** BE-04 puts an Order there when a part is `FAILED` (delivery exception). The
   payment flow may also put an Order there (DEC-17 UNKNOWN result). The aggregator never clears it. Admin
   resolution must decide, per case, what happens to the `FAILED` part(s): there is **no** action in BE-04 that
   moves a `FAILED` Fulfillment anywhere (no re-delivery, no cancel). If re-delivery is wanted, BE-05 needs a new
   explicit Admin action (`FAILED → CONFIRMED`/`SHIPPING`) with its own history; do not reuse `perform`.
2. **Whole-Order cancel with a `FAILED` part.** `OrderServiceImpl.cancelFulfillments` only cancels `CONFIRMED` parts
   and refuses otherwise (bridge from BE-03). A FAILED part therefore blocks a plain customer cancel (which is
   correct: the customer cannot cancel in `NEEDS_ATTENTION`; `ORDER_NEEDS_ATTENTION` is returned). Admin
   cancel-and-restore of such an Order is BE-05 work: restore exactly the quantities of the `OrderItem`s of the
   parts being cancelled, once, with `CANCEL_ORDER` movements.
3. **Locking.** Take the Order lock first (`OrderRepository.findByIdForUpdate`) in any new writer; then
   `OrderFulfillmentRepository.findByIdForUpdate` / `findByOrderIdForUpdate`; never the other way. Do not lock
   StoreProducts while holding a Fulfillment lock.
4. **Gate.** `PaymentDeadlinePolicy.allowsFulfillmentWork` is evaluated on the locked Order for every step. When
   BE-05 confirms an ONLINE payment it must set `payment_status = PAID` and `order_status = CONFIRMED` together
   (that is what the BE-04 tests simulate with SQL, `FulfillmentFixtures.markPaid`).
5. **Customer cancel race.** Staff `PREPARE` flips the Order to `PROCESSING` in the same transaction under the Order
   lock; cancel checks the status after taking the same lock. Keep that order in the BE-05 cancel/timeout worker so
   a timeout cannot cancel an Order whose Fulfillment is already `PREPARING`.
6. **Refund simulation** applies to ONLINE `PAID` Orders only; BE-04 never writes `refund_status`.
7. **`recordOfflineCollected` limits.** It refuses an Order with more than one Fulfillment. COD and PAY_AT_STORE
   Orders are single-Store by construction (COD split = independent Orders; pickup = one Store). If a future rule
   creates multi-Store offline Orders, collection per part needs a decision (amount per Fulfillment is not stored).

## 3. For V3-BE-06 (reviews)

`orders.order_status = COMPLETED` is now reachable only when every live part is `COMPLETED` and the Order is `PAID`.
`PARTIALLY_COMPLETED` Orders contain parts that are already received; a per-item review rule (OPEN-05, one SKU may
have several OrderItems) must read `order_fulfillments.status = COMPLETED` of the item's part rather than the Order
status if early review is wanted. `ReviewServiceImpl` still uses `order.getStore()` (BE-03 handover §4).

## 4. For V3-BE-07 (hardening / test rebuild)

* The V2 order-level services (`DeliveryFulfillmentServiceImpl`, `PickupFulfillmentServiceImpl`, the legacy methods of
  `StaffOperationsService`, `DeliveryAction`, `PickupAction`, `OrderTransitionPolicy` fulfilment graph, V2
  `recordCodCollected`/`recordPayAtStoreCollected`) are **dead code behind guards**. Remove them together with
  their V2 unit/DB tests once the V2 suite is rebuilt (`V3_BE04_TEST_RESULTS.md` §5 lists the 383 cases still failing
  exactly as before BE-03).
* `OrderStatus` Java enum still has `PREPARING`, `PACKED`, `READY_FOR_PICKUP` which the Order CHECK constraint
  rejects; remove from the enum and from `OrderTransitionPolicy` after the legacy tests are rebuilt.
* Queue ordering and the held/blocked counters use SQL that is index-friendly with the spec's proposed indexes
  `(store_id, status, created_at)` and `(order_id, status)`; the schema has the DDL as is.

## 5. Known limits

* The pickup code limiter is in memory (per application instance, reset on restart).
* Staff cannot re-open or amend a `FAILED`/`COMPLETED` part (by design until BE-05).
* No Staff notes field is offered on steps other than SHIP (tracking) and FAIL (reason).
* A COD Order collected at delivery uses the whole `orders.total_amount` (items + shipping fee); there is no partial
  collection.
* Two Stores with a stale assignment cache: authorization is resolved from the database on every call, so a revoked
  assignment takes effect on the next request.
* The queue's date filters use `assigned_at` (DATETIME2(0)); `from`/`to` are inclusive dates.

## 6. Feature gates at hand-over

| Flow | State | Condition to change |
|---|---|---|
| Staff Fulfillment operations (COD delivery, PAY_AT_STORE pickup, ONLINE after payment) | **open** | — |
| ONLINE checkout | **closed** | BE-05 complete and its race matrix passing, then `oneshop.checkout.online-enabled=true` |
| Admin handling of `NEEDS_ATTENTION` / `FAILED` parts | **absent** | BE-05 |
