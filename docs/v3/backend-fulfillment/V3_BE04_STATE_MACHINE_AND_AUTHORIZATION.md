# V3-BE-04 — STATE MACHINES, AUTHORIZATION AND CONCURRENCY DESIGN

**Date:** 13/10/2026 · **Scope:** Multi-Branch Fulfillment Operations · **Schema:** unchanged

Everything below is implemented in `com.oneshop.service.fulfillment.*` (`FulfillmentAction`, `StaffFulfillmentService`,
`OrderStatusAggregator`, `PickupAttemptLimiter`, `FulfillmentQueueFilter`) and `PaymentService.recordOfflineCollected`.

---

## 1. Authorization

### 1.1 Rule

> A Staff member may read or change a Fulfillment **only if `order_fulfillments.store_id` is the Store of one of
> their ACTIVE `staff_store_assignments`** (and that Store is ACTIVE).

`orders.store_id` is **not** an authorization source (it is still filled for single-Store Orders as a legacy
convenience). Test `BE04-09` proves it: moving `orders.store_id` to another Store neither grants nor removes access.

### 1.2 Where it is enforced

| Layer | Mechanism |
|---|---|
| URL | `SecurityConfig`: `/staff/**` requires `ROLE_STAFF`; the Staff POST area is CSRF-protected even with a Bearer header (`/staff/fulfillments/**` added to the form matchers). |
| Identity | Always the `SecurityContext` (`StaffStoreScopeService`), never a request field. A non-STAFF principal, a STAFF without ACTIVE assignment or with only INACTIVE Stores gets `AccessDeniedException` (403) before any data is read. |
| Queue | SQL `f.store_id IN (:assignedStoreIds)` in `OrderFulfillmentRepository.searchForStaff`. The optional `storeId` filter can only *narrow* the assigned set: a Store outside it raises 403 (it is not silently ignored). |
| Detail | `findKeyById` (scalar) → 404 if unknown, **403 if the Store is not assigned**; then `findByIdAndStoreIdIn`. |
| Write | scalar scope check → lock Order → lock Fulfillment → **re-check the locked row's Store** (`requireAssignedStore`) before any write. A change of assignment between the check and the lock is therefore still caught. |
| Response data | A Staff view contains only the lines of that Fulfillment (`order_items.fulfillment_id`). The customer's `shipping_address`/phone are shown only for DELIVERY (the Store has to ship). The pickup code is never in a Staff DTO (`pickupCodeIssued` only), nor in history notes. |

### 1.3 Outcomes

| Situation | Result |
|---|---|
| Unknown Fulfillment id | `404` (`ResourceNotFoundException`) |
| Fulfillment of a Store the Staff is not assigned to | `403` (`AccessDeniedException`), nothing written |
| CUSTOMER / ADMIN principal | `403` |
| Staff without any ACTIVE assignment | `403` |
| Wrong step / repeated step / other state | `409` + code `FULFILLMENT_STATUS_CHANGED` |
| Order held by the payment gate or exception | `409` + one of `PAYMENT_PENDING`, `PAYMENT_NOT_VERIFIED`, `ORDER_NEEDS_ATTENTION`, `ORDER_CANCELLED` |
| Customer cancels after a Store started | `409` + `ORDER_ALREADY_PREPARING` |
| Pickup code locked after 5 wrong tries | `409` + `PICKUP_CODE_LOCKED` |
| Wrong pickup code / bad reason / bad tracking code | `400` (nothing written) |

A `403` for another Store's id (rather than `404`) follows the specification (V3-T16 allows 403 or 404). It reveals that the id
exists; the Fulfillment id carries no other information.

### 1.4 What Staff can never do

* send or change an Order status (no method of `StaffFulfillmentService` accepts an `OrderStatus`; `BE04-40`);
* choose the Store of an operation (the POST reads only `trackingCode`, `pickupCode`, `reason`; extra fields such as
  `orderStatus`, `storeId`, `paymentStatus` are ignored, `BE04-59`);
* cancel or refund (`CANCELLED` is not the target of any action; `FAILED` only holds the Order);
* touch stock (`StaffFulfillmentService` has no inventory dependency; `BE04-41/42`).

---

## 2. Fulfillment state machines

### 2.1 DELIVERY

```text
CONFIRMED --PREPARE--> PREPARING --PACK--> PACKED --SHIP--> SHIPPING --COMPLETE_DELIVERY--> COMPLETED
                                                              |
                                                              +--FAIL_DELIVERY (reason)--> FAILED
```

### 2.2 STORE_PICKUP

```text
CONFIRMED --PREPARE--> PREPARING --MARK_READY--> READY_FOR_PICKUP --COMPLETE_PICKUP (code)--> COMPLETED
                                                       |
                                                       +--FAIL_PICKUP (reason)--> FAILED
```

`CONFIRMED → SHIPPING`, `PACKED` for pickup, `READY_FOR_PICKUP` for delivery, skipping a step and repeating a step are
all refused with `FULFILLMENT_STATUS_CHANGED` (`BE04-12/13/14/28`). `COMPLETED`, `FAILED` and `CANCELLED` are
terminal for Staff.

### 2.3 Side effects per action

| Action | Fulfillment fields | History | Other |
|---|---|---|---|
| `PREPARE` | `prepared_at` | `CONFIRMED→PREPARING` | Order derived (`PROCESSING`) |
| `PACK` | `packed_at` | `PREPARING→PACKED` | — |
| `SHIP` | `shipped_at`, optional `tracking_code` (`[A-Za-z0-9._-]{3,50}`) | `PACKED→SHIPPING` | Order `SHIPPING` |
| `MARK_READY` | `pickup_code` (8 chars, unique per Store), `ready_at` | `PREPARING→READY_FOR_PICKUP` | code never shown to Staff |
| `COMPLETE_DELIVERY` | `completed_at` | `SHIPPING→COMPLETED` | COD: payment collected (see 4) |
| `COMPLETE_PICKUP` | `picked_up_at`, `completed_at` | `READY_FOR_PICKUP→COMPLETED` | code verified; PAY_AT_STORE: payment collected |
| `FAIL_DELIVERY` / `FAIL_PICKUP` | `note` = reason (5–500 chars) | `→FAILED` + reason | Order `NEEDS_ATTENTION`; **no restock, no refund, no payment change** |

History rows carry the acting Staff user. Order history is written only when the derived status changes.

### 2.4 Failure policy (spec 10.2, proposal accepted as the minimum)

`FAILED` is reachable from `SHIPPING` (DELIVERY) and `READY_FOR_PICKUP` (STORE_PICKUP) with a mandatory reason. It
moves the Order to `NEEDS_ATTENTION` and stops there: stock stays deducted, nothing is refunded, COMPLETED parts are
not reverted, and the other Stores' parts are held (the Order is not workable) until Admin handling in BE-05.
`BE04-18/19/38`.

---

## 3. Order status aggregation (`OrderStatusAggregator`)

Computed from `order_fulfillments.status` in the **same transaction** as the Fulfillment change, from the database
(`findStatusesByOrderId`), after the Fulfillment has been flushed.

| # | Condition (first match) | Order status |
|---|---|---|
| 1 | any part `FAILED` | `NEEDS_ATTENTION` |
| 2 | every part `CANCELLED` | `CANCELLED` (derived only; the aggregator never writes it, see below) |
| 3 | every live part `COMPLETED` and `payment_status = PAID` | `COMPLETED` |
| 3b | every live part `COMPLETED` but not PAID | `NEEDS_ATTENTION` (money condition not met) |
| 4 | some part `COMPLETED` | `PARTIALLY_COMPLETED` |
| 5 | some part `SHIPPING` | `SHIPPING` |
| 6 | every live part `CONFIRMED` | `CONFIRMED` |
| 7 | otherwise (PREPARING / PACKED / READY_FOR_PICKUP present) | `PROCESSING` |

Protections (`OrderStatusAggregator.mayWrite`):

* an Order in **`PENDING_PAYMENT`** is never changed (only payment success or cancellation leaves it);
* **`NEEDS_ATTENTION`** is never cleared;
* **`CANCELLED`** and **`COMPLETED`** are never overwritten, and the aggregator never *writes* `CANCELLED`
  (cancellation also restores stock and refunds: `OrderServiceImpl`/BE-05);
* a `READY_FOR_PICKUP` part never makes the Order `COMPLETED`.

Unit table: `OrderStatusAggregatorTest` (16 table rows + recompute rules). Database: `BE04-11/36/37/38/39` (V3-T17, V3-T18, V3-T19), `BE04-51/52`.

---

## 4. Payment gate and offline collection

### 4.1 Gate (`PaymentDeadlinePolicy.allowsFulfillmentWork`, checked under the Order lock for every step)

| Order | Work allowed? | Refusal code |
|---|---|---|
| `PENDING_PAYMENT` | no | `PAYMENT_PENDING` |
| `NEEDS_ATTENTION` | no | `ORDER_NEEDS_ATTENTION` |
| `CANCELLED` | no | `ORDER_CANCELLED` |
| ONLINE and `payment_status != PAID` | no | `PAYMENT_NOT_VERIFIED` |
| ONLINE `PAID`, COD, PAY_AT_STORE | yes | — |

In the queue and dashboard such rows are shown **held** (`blockedReason`, no actions, counted under "Đang bị giữ", not
under "cần xử lý"). `BE04-31..35`.

### 4.2 Collection of COD / PAY_AT_STORE

`PaymentService.recordOfflineCollected(order, fulfillment)` (`MANDATORY` transaction) is the **only** place that marks
an offline Order PAID. It requires: Order UNPAID and workable, method/type/status match (COD: DELIVERY and `SHIPPING`;
PAY_AT_STORE: STORE_PICKUP and `READY_FOR_PICKUP` with a code), **exactly one Fulfillment on the Order** (the amount
is the whole Order, never a part), and no existing SUCCESS payment. It writes one `payments` row (`SUCCESS`, amount =
`orders.total_amount`, `paid_at`) and sets `orders.payment_status = PAID`.

* COD: collected when delivery is confirmed (`COMPLETE_DELIVERY`).
* PAY_AT_STORE: collected after the pickup code is verified (`COMPLETE_PICKUP`).
* If collection fails, the whole step rolls back: the Fulfillment stays `SHIPPING` / `READY_FOR_PICKUP`, the code
  stays valid, nothing is PAID (`BE04-53/55`).
* A COD split is N independent single-Store Orders, so each is collected independently and exactly once (BE-03).
* An ONLINE Order creates no payment here; its money was handled by the payment phase.

---

## 5. Pickup code

| Item | Rule |
|---|---|
| Generation | `PickupCodeService.generate()` (8 chars of `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`, `SecureRandom`) at `MARK_READY`, unique within the Store (`existsByStoreIdAndPickupCode`, up to 8 attempts). |
| Storage | `order_fulfillments.pickup_code` (per Fulfillment, **not** per Order); `ready_at`. |
| Visibility | Customer who owns the Order, only while `READY_FOR_PICKUP` (customer order page). Never in a Staff DTO, template or history note (`BE04-25`, `BE04-62`, `BE04-65`). |
| Verification | `PickupCodeService.verify`: trimmed, upper-cased, constant-time comparison; legacy 6-char codes still accepted if that is what is stored. |
| Brute force | `PickupAttemptLimiter`: 5 wrong codes lock that Fulfillment's code for 15 minutes (in memory by design: the wrong attempt rolls the transaction back, so a database counter could not persist). A restart clears it. |
| Wrong code | `400`, nothing written, payment untouched (`BE04-22`). |

---

## 6. Locking and race safety

### 6.1 Order

```text
Staff step:      Order (PESSIMISTIC_WRITE) → Fulfillment (PESSIMISTIC_WRITE) → writes → aggregate
Customer cancel: Order (PESSIMISTIC_WRITE) → StoreProducts (ascending id, restore) → Fulfillments cancelled (writes)
Checkout:        Cart → StoreProducts (ascending id) → new Order rows
Payment (BE-05): Order → …
```

* Every Fulfillment writer takes the **Order lock first**, so two Stores' Staff on one Order serialize on the Order row
  and a Fulfillment lock is never requested while holding a StoreProduct lock (no cycle). `BE04-51` (two Stores at
  once) and `BE04-52` (last two parts completed at once) ran three rounds each without deadlock.
* The scalar scope lookup (`findKeyById`) loads no entity before the lock, so no stale persistence-context row is
  trusted after waiting for the lock.

### 6.2 Cancel against PREPARING (spec 10.1)

Both sides lock the Order first and then re-read the state:

* Staff first → Order becomes `PROCESSING` in the same transaction; the customer's cancel then fails with
  `ORDER_ALREADY_PREPARING` (409), nothing restored.
* Cancel first → Order and Fulfillments are `CANCELLED` and stock restored once; Staff's step fails with
  `ORDER_CANCELLED` (409).

`BE04-50` ran six committed rounds; every round ended with exactly one winner, a consistent Order/Fulfillment pair and
at most one `CANCEL_ORDER` movement.

### 6.3 Idempotency

A double click gives one success and one `FULFILLMENT_STATUS_CHANGED` (`BE04-13`, `BE04-49`); a second
`COMPLETE_DELIVERY` cannot collect twice (`BE04-16`).

---

## 7. Stock invariants (spec: no second deduction)

Stock was deducted when the Order was created (BE-03). No Fulfillment status change reads or writes
`store_products.quantity`/`reserved_quantity` and no `inventory_movements` row is created by any step (`BE04-41`,
`BE04-42`, `BE04-18`). The only movement types in the system remain `ORDER`, `CANCEL_ORDER` and `STOCK_ADJUST`.
