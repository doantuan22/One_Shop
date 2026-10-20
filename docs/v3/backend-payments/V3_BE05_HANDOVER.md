> Historical phase evidence (up to 14/10/2026). Superseded script names and readiness claims describe that run only. Current setup: [database README](../../seed/DATABASE_SETUP.md); current acceptance: [deep audit](../final-deep-audit/DEEP_AUDIT_REPORT.md).

# V3-BE-05 — HANDOVER TO V3-BE-06 / V3-BE-07 AND TO OPERATIONS

**Phase completed:** V3-BE-05 Payments, Timeout and Simulated Refunds — `V3_BE05_IMPLEMENTATION_REPORT.md`.  
**Not started:** V3-BE-06 (reviews), V3-BE-07 (final regression). **Default configuration still has
`oneshop.checkout.online-enabled=false`** (see section 5).

---

## 1. Contracts provided

### 1.1 Services (`com.oneshop.service.payment`)

| Member | Contract |
|---|---|
| `PaymentReconciliationService.simulate(email, orderId, paymentId, scenario)` | demo gateway enacts `SUCCESS` / `FAILED` / `UNKNOWN` for the owner's attempt, then verifies; returns a `PaymentOutcome`; idempotent |
| `PaymentReconciliationService.reconcile(orderId, trigger)` | one Order under its lock (worker, recovery) |
| `PaymentReconciliationService.reconcileLocked(order, trigger, actor)` | the decision engine; caller holds the Order lock |
| `OrderCancellationService.cancelAndRestore(...)` | the only cancel-and-restore; `MANDATORY` transaction |
| `CustomerOrderCancellationService.cancel(customer, orderId)` | whole-Order customer cancel, idempotent |
| `RefundService.simulate(order, reason)` | whole simulated refund of collected ONLINE rows, idempotent |
| `PaymentTimeoutService.scan()` | one scan; `PaymentTimeoutWorker` calls it on `@Scheduled` |
| `AdminPaymentService` | ADMIN only: `getCases`, `getCase`, `recheck`, `settleGateway`, `retryRefund` |
| `OrderStatusWriter.write/audit` | the payment-domain Order transitions with history |
| Seams: `PaymentGateway`, `RefundSimulator` | the two places a real integration would plug in |

Error codes (HTTP 409): `PAYMENT_RECONCILING`, `PAYMENT_NOT_PAYABLE`, `PAYMENT_ALREADY_FINAL`, `GATEWAY_DISABLED`,
`NOT_A_PAYMENT_CASE`, `NOTHING_TO_REFUND`, plus BE-04's `ORDER_ALREADY_PREPARING`, `ORDER_NEEDS_ATTENTION`.

### 1.2 HTTP

Customer: `POST /orders/{id}/payments/{paymentId}/simulate` (`scenario=SUCCESS|FAILED|UNKNOWN`),
`POST /orders/{id}/cancel`. Admin: `GET /admin/payments`, `GET /admin/payments/{orderId}`,
`POST /admin/payments/{orderId}/recheck`, `…/gateway` (`answer=COLLECTED|NOT_COLLECTED`), `…/refund`.

### 1.3 Data rules later phases can rely on

* **Ledger in `payments`** (no new table/column): see `V3_BE05_PAYMENT_STATE_MACHINE_AND_TRANSACTIONS.md` §1.
  `payments.transaction_code` of an ONLINE row is `MOCK-<ref>` with an optional `~UNKNOWN` suffix; always compare by
  `MockLedger.reference(code)`.
* `orders.payment_status`: `PAID` only through a verified collection or `recordOfflineCollected`; `REFUNDED` only after
  `SIMULATED_REFUNDED`; `FAILED` for a verified decline or an unpaid timeout; `UNPAID` for a customer cancel before
  payment.
* `CANCELLED` is final. A `CANCEL_ORDER` movement exists at most once per Order.
* `NEEDS_ATTENTION` with `payment_status = UNPAID` is a **payment case**; with `PAID` it is a **delivery exception**
  (BE-04) and is not touched by any BE-05 path.

---

## 2. What the Admin can and cannot do

| Can | Cannot |
|---|---|
| view the case: Order, ledger rows, parts, history | set any `order_status` / `payment_status` / amount / time |
| re-check (re-verify with the gateway and apply evidence) | mark PAID without a collection in the ledger |
| give the mock gateway's answer for an in-doubt attempt, then re-check | revive a CANCELLED Order |
| retry a failed simulated refund on a CANCELLED Order | edit `StoreProduct.quantity`, create a manual movement |
| | refund partially, or per part |
| | touch a part that is `FAILED` after PREPARING (no automatic restock; BE-04 exception policy) |

Every Admin action is audited in `order_status_history` (actor, time, tag, result, reason ≤ 500 characters).

---

## 3. Operations

* The worker runs every `oneshop.payment.timeout-scan-interval-ms` (60 s). **The first scan on a database that has
  an old pending ONLINE Order cancels it and restores its stock** (this is the intended behaviour; the dev seed has
  one such Order, id 8, which will be cancelled the first time the application runs against `oneshop`).
* Several instances may run the worker: decisions are taken under the Order lock and re-check the state.
* The suite sets `oneshop.payment.timeout-worker-enabled=false` (pom, surefire) so that test runs do not cancel seed
  data; real scans in tests call `PaymentTimeoutService.scan()` or start a second context.
* `oneshop.payment.mock-gateway-enabled` is `false` in `application-prod.properties`: in production nobody can settle
  a payment; ONLINE Orders can then only be settled by a real gateway integration (not built) or time out.

---

## 4. For V3-BE-06 and V3-BE-07

* **Reviews (BE-06):** `orders.order_status = COMPLETED` still requires every live part `COMPLETED` and `PAID`
  (BE-04). A CANCELLED/REFUNDED Order is never reviewable. A SKU split between Stores gives several OrderItems.
* **BE-07 scenarios D, E, F, G, H, I of the plan are covered by BE05-xx** (cancel with refund, repeated
  cancel/failure/timeout, payment in time, verified FAILED, timeout, UNKNOWN); BE-07 should add them to the
  end-to-end suite rather than rebuild the services.
* Legacy: `PaymentServiceImpl.complete(...)` (private, unused), `closePendingForCustomerCancellation`,
  `OrderServiceImpl.cancelByCustomer`, `cancelAfterOnlinePaymentFailure`, `confirmAfterOnlinePayment` are no longer
  on any production path (kept because V2 unit tests still call them). Remove them with the V2 test rebuild.

---

## 5. Opening ONLINE checkout (decision for the owner)

`oneshop.checkout.online-enabled` is **still `false` by default.** The conditions of the BE-03 handover are now
implemented and verified on `oneshop_test` (with the switch turned on **only** by the test classes' own properties):

| Condition | Evidence |
|---|---|
| verified payment result, not the browser | BE05-02/06/51/53 |
| 15-minute deadline, boundary decided by collection time | BE05-09/10/13/24 |
| reconciliation window and NEEDS_ATTENTION | BE05-14..16 |
| cancel + restore once, per StoreProduct, incl. split SKU | BE05-26/27/29 |
| simulated refund once, retry safe | BE05-27/32/33 |
| Admin resolution with evidence only | BE05-19..22/34..37 |
| race matrix | BE05-38..47 |
| restart recovery, scheduled worker | BE05-48/49 |

What is **not** verified and therefore keeps the default closed: a browser end-to-end run, a load/soak run of the
worker on a large pending set, and an owner review of the demo gateway UX. Turning it on is
`ONESHOP_CHECKOUT_ONLINE_ENABLED=true` for a test/demo environment only; never production (the gateway is off there,
so ONLINE Orders could only expire).

---

## 6. Known limits

1. The gateway doubt marker and submission time are encoded in `payments.transaction_code` / `paid_at` of one row;
   there is no per-event gateway log.
2. The DDL has no unique index on a successful payment or a reference: "one SUCCESS per reference" and
   "one refund per collection" are enforced in the Service under the Order lock.
3. The Admin list query scans ONLINE Orders by status; with very many Orders add an index on
   `(payment_method, order_status, created_at)` in a future schema change (not done: no schema change in BE-05).
4. A refund simulation that keeps failing stays `SIMULATION_FAILED` and visible in the Admin list until retried.
5. Customers cannot cancel while a result is UNKNOWN (rule from DEC-17), even before the deadline.
