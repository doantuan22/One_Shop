# V3-BE-05 — PAYMENT STATE MACHINE, LEDGER AND TRANSACTION DESIGN

**Date:** 14/10/2026 · **Scope:** Mock payment gateway, timeout and reconciliation, cancellation and restoration,
simulated refund, Admin resolution · **Schema:** unchanged (22 tables, `ddl-auto=validate`)

Code: `com.oneshop.service.payment.*`, `PaymentDeadlinePolicy` (BE-03), `OrderStatusAggregator` (BE-04).

---

## 1. The durable mock ledger (no table, no column added)

The gateway is a server-side component with **no state of its own**. Its ledger is the `payments` row of the ONLINE
attempt, read and written through `MockLedger`. A restart loses nothing because nothing is held in memory.

| Ledger state | `payments.status` | `paid_at` | `transaction_code` | Meaning |
|---|---|---|---|---|
| `AWAITING` | `PENDING` | NULL | `MOCK-<ref>` | created by checkout; the customer has not completed a payment |
| `IN_DOUBT` | `PENDING` | submission time | `MOCK-<ref>~UNKNOWN` | the customer submitted, the gateway cannot confirm (UNKNOWN) |
| `CAPTURED` | `PENDING` | collection time | `MOCK-<ref>` | the gateway collected; the Order has not been told yet |
| `DECLINED` | `FAILED` | NULL | `MOCK-<ref>` | nothing collected, final (verified FAILED, or the attempt was closed unpaid) |
| `SETTLED` | `SUCCESS` | collection time | `MOCK-<ref>` | collection applied to the Order |

* `payments.status` has no `UNKNOWN`; the only marker needed is the suffix of `transaction_code`, so **the reference
  itself never changes** and de-duplication is always by the reference without the suffix.
* `CHECK (status <> 'SUCCESS' OR paid_at IS NOT NULL)` is respected: `paid_at` is set before a row becomes `SUCCESS`.
* A collection that arrives for an **already cancelled** Order is recorded as a **new** row `SUCCESS` with the same
  reference (the cancelled attempt is `FAILED`); at most one `SUCCESS` row exists per reference.
* The customer never sees the marker (`PaymentResponse` strips it).

Limit (owner decision needed only if richer history is wanted): the doubt marker and the submission time live in
two columns of one row; there is no per-event gateway log. Final outcomes are in `payments`, decisions in
`order_status_history` notes.

### Gateway answer (`PaymentGateway.verify(order, rows)`, pure over the rows)

| Rows of the Order (ONLINE only) | Result |
|---|---|
| any `SETTLED` or `CAPTURED` | `SUCCESS` at the earliest `paid_at` |
| else any `IN_DOUBT` | `UNKNOWN` |
| else any `DECLINED` | `FAILED` |
| else | `NOT_PAID` |

A gateway that throws (outage, timeout) is **UNKNOWN**, never FAILED (`PaymentReconciliationService.verify`).

---

## 2. Order and payment states

```text
                    +------------- verified SUCCESS before deadline -------------+
                    |                                                            v
 (checkout) PENDING_PAYMENT ----------------------------------------------> CONFIRMED ---> (BE-04 operations)
   UNPAID           |  ^   \                                                     |
                    |  |    \--- UNKNOWN, window over ---> NEEDS_ATTENTION --+    | customer cancel (all parts CONFIRMED)
   verified FAILED  |  |                       |  Admin/recheck:              |    v
   / not paid at    |  +--- recheck, still ----+  collected in time -> CONFIRMED  CANCELLED (+ simulated refund when PAID)
   deadline         |        unknown           |  not collected -> CANCELLED
                    v                          |  collected late -> CANCELLED + refund
               CANCELLED  <--------------------+
```

| Situation | `order_status` | `payment_status` | attempt row | `refund_status` | parts | Stock |
|---|---|---|---|---|---|---|
| ONLINE created | PENDING_PAYMENT | UNPAID | PENDING / AWAITING | NONE | CONFIRMED (gated) | deducted |
| Verified SUCCESS before the deadline | CONFIRMED | PAID | SUCCESS | NONE | CONFIRMED (workable) | unchanged |
| Verified FAILED | CANCELLED | FAILED | FAILED | NONE | CANCELLED | restored once |
| Deadline passed, nothing collected | CANCELLED | FAILED | FAILED | NONE | CANCELLED | restored once |
| UNKNOWN, before the deadline | PENDING_PAYMENT | UNPAID | PENDING / IN_DOUBT | NONE | CONFIRMED (gated) | deducted |
| UNKNOWN, deadline .. +15 min (reconciling) | PENDING_PAYMENT | UNPAID | PENDING / IN_DOUBT | NONE | CONFIRMED (gated) | deducted |
| UNKNOWN after +15 min | NEEDS_ATTENTION | UNPAID | PENDING / IN_DOUBT | NONE | CONFIRMED (gated) | deducted |
| Collected at or after the deadline | CANCELLED | REFUNDED | SUCCESS (kept) | SIMULATED_REFUNDED | CANCELLED | restored once |
| Collection arrives on a CANCELLED Order | CANCELLED (never revived) | REFUNDED | + new SUCCESS row | SIMULATED_REFUNDED | unchanged | unchanged |
| Customer cancels unpaid | CANCELLED | UNPAID | FAILED | NONE | CANCELLED | restored once |
| Customer cancels PAID (before PREPARING) | CANCELLED | REFUNDED | SUCCESS (kept) | SIMULATED_REFUNDED | CANCELLED | restored once |
| Refund simulation fails | CANCELLED | PAID | SUCCESS | SIMULATION_FAILED | CANCELLED | restored once (not repeated on retry) |

`PENDING_PAYMENT → NEEDS_ATTENTION → CONFIRMED/CANCELLED` and `CONFIRMED → CANCELLED` are written only by
`OrderStatusWriter` (allowed edges enforced, history always written). `CANCELLED` has no outgoing edge.

---

## 3. Decision table (`PaymentReconciliationService.reconcileLocked`)

`deadline = created_at + 15 min`; `reconciliation end = deadline + 15 min`; all with the injected `Clock`.

| Order | Verification | Decision |
|---|---|---|
| PENDING_PAYMENT / NEEDS_ATTENTION (payment case) | SUCCESS at `t < deadline` | CONFIRMED + PAID (no stock change) |
| same | SUCCESS at `t >= deadline` | cancel + restore + simulated refund (never confirmed) |
| same | FAILED | cancel + restore, payment_status FAILED (terminal) |
| same | NOT_PAID, `now < deadline` | wait |
| same | NOT_PAID, `now >= deadline` | cancel + restore, payment_status FAILED |
| same | UNKNOWN, `now < deadline` | wait |
| same | UNKNOWN, `deadline <= now < end` | reconciling: nothing changes |
| PENDING_PAYMENT | UNKNOWN, `now >= end` | NEEDS_ATTENTION, stock kept, evidence in the history note |
| NEEDS_ATTENTION | UNKNOWN | unchanged (waits for evidence or an administrator) |
| CANCELLED | money collected | recorded once, refund simulated once, Order untouched |
| CONFIRMED and later | any | no change |
| NEEDS_ATTENTION with `payment_status = PAID` (delivery exception) | any | **not a payment case: no change** |

Whether a payment is in time is decided by the **collection time against the deadline**, never by when the worker
runs; the 15 + 15 minutes are `oneshop.payment.pending-timeout-minutes` and `…reconciliation-window-minutes`.

---

## 4. Transactions and locking

### 4.1 Order of locks (all flows)

```text
Order (PESSIMISTIC_WRITE, PK only)
  -> Fulfillments (ascending id, PESSIMISTIC_WRITE)        only when parts change
    -> StoreProducts (ascending id, inside restoreForCancelledOrder)   only when stock changes
```

Callback, timeout scan, customer cancel, Admin re-check, Admin gateway answer and Staff `PREPARING` (BE-04) all take
the Order lock first and re-read the state under it. No flow takes a Fulfillment or StoreProduct lock before the
Order lock; checkout creates the Order after locking cart and StoreProducts (new row, no existing lock to cross).

### 4.2 One transaction per decision

| Entry | Transaction | What is atomic |
|---|---|---|
| `PaymentReconciliationService.simulate` | `@Transactional` | ledger write + decision + cancel/restore/refund |
| `PaymentReconciliationService.reconcile` (scan) | `@Transactional`, one per Order | decision of one Order |
| `CustomerOrderCancellationService.cancel` | `@Transactional` | state check, restore, parts, attempts, Order, refund |
| `AdminPaymentService.*` | `@Transactional` | ledger answer + decision + audit row |
| `OrderCancellationService.cancelAndRestore` | `MANDATORY` | parts (with history), movements, attempts, Order status + history |

The scan only reads candidate ids (`OrderRepository.findPaymentCandidateIds`, keyset paging); a failure of one Order
is caught, logged and retried by the next scan, and does not stop the others. No external call is made inside a
transaction (the mock gateway reads the rows the transaction already holds; a real gateway would be queried
before the lock and re-verified under it).

### 4.3 Cancel and restore (`OrderCancellationService`)

1. lock the parts; refuse unless every part is `CONFIRMED` or `CANCELLED` (**a `FAILED` or later part is never
   restocked**, `ORDER_ALREADY_PREPARING`);
2. refuse if a `CANCEL_ORDER` movement already exists for the Order (second line of defence against restoring twice);
3. `InventoryService.restoreForCancelledOrder`: each OrderItem back to **its own** StoreProduct, positive
   `CANCEL_ORDER` movement (a SKU split between Stores is restored per Store);
4. parts to `CANCELLED` with history (actor, note); pending attempts to `FAILED` keeping the reference;
5. `orders.payment_status`, then `CANCELLED` with history.

It happens only on the transition into `CANCELLED`; the Order-status edge table and the movement guard make a second
execution impossible.

### 4.4 Simulated refund (`RefundService`)

Whole amount of every collected ONLINE row, never partial, never per part. `NONE → PENDING_SIMULATION →
SIMULATED_REFUNDED` (`orders.payment_status = REFUNDED`) or `SIMULATION_FAILED` (`payment_status` stays `PAID`,
cancel and restore **kept**, Admin retry safe and idempotent). A failing simulation is caught inside the transaction,
so it can never roll back the restoration.

### 4.5 Audit with existing columns

`order_status_history.changed_by_user_id` (actor), `changed_at`, `old_status`/`new_status`, and a note
`[TAG] nguồn=<trigger>; key=value; …` of at most 500 characters (longer evidence is abbreviated). Tags:
`PAYMENT_CONFIRMED`, `PAYMENT_FAILED`, `PAYMENT_TIMEOUT`, `LATE_PAYMENT`, `RECONCILIATION_TIMEOUT`,
`ADMIN_RECHECK`, `ADMIN_GATEWAY_COLLECTED|NOT_COLLECTED`, `ADMIN_REFUND_RETRY`. An action that changes no status
writes a note-only row (old = new). Parts get `order_fulfillment_status_history` rows with the same actor.

---

## 5. Race matrix (all verified on SQL Server, `PaymentConcurrencyDatabaseIntegrationTest`)

| # | Race | Outcome (every round) |
|---|---|---|
| 1 | SUCCESS in time vs scan | CONFIRMED, never cancelled, no restore |
| 2 | SUCCESS vs timeout (expired) | CANCELLED once, restored once, collection refunded once, never confirmed |
| 3 | cancel vs payment SUCCESS | CANCELLED, restored once, one collection, refunded once (paid first: confirmed then cancelled; cancel first: late collection recorded) |
| 4 | cancel vs Staff PREPARING (paid ONLINE) | exactly one winner; cancel wins: all parts cancelled, restored, refunded; PREPARING wins: `PROCESSING`, cancel refused 409, no restore/refund |
| 5 | two timeout scans | each expired Order cancelled once, restored once |
| 6 | duplicate cancel | one restoration, one refund |
| 7 | duplicate callback | one `SUCCESS`, one `CONFIRMED` history row |
| 8 | payment at the deadline | decided by the collection time (`t >= deadline` is late) |
| 9 | Admin vs scan vs late callback | one transition, one restoration, one refund |
| 10 | restore fails | whole cancellation rolled back, the rest of the scan continues, next scan completes it |
| 11 | status write fails | the payment capture rolls back with it |
| 12 | restart | a second application continues from the database alone |

---

## 6. Configuration

| Property | Default | Meaning |
|---|---|---|
| `oneshop.payment.pending-timeout-minutes` | 15 | time to pay (owner-approved; not for environment tuning) |
| `oneshop.payment.reconciliation-window-minutes` | 15 | UNKNOWN reconciliation after the deadline |
| `oneshop.payment.timeout-scan-interval-ms` | 60000 | worker interval (minimum 1000) |
| `oneshop.payment.timeout-worker-enabled` | true | run the `@Scheduled` worker (the suite switches it off) |
| `oneshop.payment.scan-batch-size` | 200 | candidates per page of one scan |
| `oneshop.payment.mock-gateway-enabled` | true; **false in `application-prod.properties`** | demo gateway: customer scenarios and Admin gateway answers |
| `oneshop.checkout.online-enabled` | **false** | ONLINE checkout stays closed by default |
