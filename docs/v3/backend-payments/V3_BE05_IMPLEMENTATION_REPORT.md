> Historical phase evidence (up to 14/10/2026). Superseded script names and readiness claims describe that run only. Current setup: [database README](../../../database/README.md); current acceptance: [deep audit](../final-deep-audit/DEEP_AUDIT_REPORT.md).

# V3-BE-05 — IMPLEMENTATION REPORT
## Payments, 15-Minute Timeout, Cancellation & Simulated Refunds

**Phase status: DONE for the BE-05 scope** (14/10/2026).  
DONE does **not** open ONLINE checkout: `oneshop.checkout.online-enabled` stays `false` by default; the owner decides
(`V3_BE05_HANDOVER.md` §5). BE-06 and BE-07 were not started. Schema, DDL and seed data are unchanged.

---

## 1. Executive summary

| Area | Result |
|---|---|
| Mock payment gateway, verified by the backend; browser can never set a result | Implemented (`PaymentGateway`, `MockPaymentGateway`) |
| Durable ledger **without a new table or column** | Implemented in the `payments` row (`MockLedger`); survives restarts |
| Duplicate callbacks / double payment | Idempotent under the Order lock; one `SUCCESS` per reference |
| 15-minute deadline, boundary by collection time | Implemented (`PaymentDeadlinePolicy` + engine) |
| `@Scheduled` timeout worker (60 s), failure-isolated, restart-safe | Implemented (`PaymentTimeoutService`, `PaymentTimeoutWorker`) |
| UNKNOWN → 15-minute reconciliation → `NEEDS_ATTENTION` | Implemented; blocks new payment, customer cancel, Staff PREPARING |
| Verified FAILED is terminal; late callbacks never revive an Order | Implemented |
| Cancel and restore (per StoreProduct, split SKU, `CANCEL_ORDER`, atomic, once) | Implemented (`OrderCancellationService`) |
| Customer cancel: before PREPARING only, paid ONLINE refunded, races safe | Implemented (`CustomerOrderCancellationService`) |
| Simulated full refund, retry-safe, no double refund | Implemented (`RefundService`) |
| Admin resolution of `NEEDS_ATTENTION` by evidence only, audited | Implemented (`AdminPaymentService`, `/admin/payments`) |
| Regression (per test case) | **0 regressions**; 67 new cases, all pass |
| Default configuration | `oneshop.checkout.online-enabled=false`; mock gateway **off in prod** |

---

## 2. Audit (what existed at the start)

| Element | Finding | Action |
|---|---|---|
| `payments` table | status `PENDING/SUCCESS/FAILED` only; refund columns present | used; no UNKNOWN value, so the doubt is a reference suffix |
| `PaymentServiceImpl.markOnlinePaymentSuccess/Failed` | refused (BE-03) | kept refused; new simulate route |
| `PaymentServiceImpl.complete(...)` private, `closePendingForCustomerCancellation` | V2 paths rewriting the reference to `PAY-…` | no longer used by production; kept for V2 unit tests |
| `CustomerOrderServiceImpl.cancelOrder` | UNPAID only, no refund, no payment-race handling | delegated to `CustomerOrderCancellationService` |
| `OrderServiceImpl` | cancel bridge of BE-03 (restores only CONFIRMED parts) | untouched; payment flows use the shared service |
| No `@EnableScheduling`, no worker | — | added, conditional on a property |
| Seed Order 8 (ONLINE pending, expired) | a scan would cancel it | shielded in tests; documented for operators |

---

## 3. Architecture

```text
Customer browser ──POST scenario──> PaymentController ──> PaymentReconciliationService.simulate
Timeout worker ──scan ids──> PaymentTimeoutService ──reconcile(id)──> PaymentReconciliationService
Admin console ──POST──> AdminPaymentService ──────────────────────────> PaymentReconciliationService
                                                                 │ lock Order → read payments rows
                                                                 │ PaymentGateway.verify(order, rows)   (mock ledger)
                                                                 ▼ decision (deadline, window, evidence)
               OrderStatusWriter ◄── confirm | wait | NEEDS_ATTENTION | OrderCancellationService.cancelAndRestore
                                                                        └─ InventoryService.restoreForCancelledOrder
                                                              RefundService.simulate (late / paid cancel)
Customer cancel ──> CustomerOrderCancellationService ──(same engine first, then cancel/refund)
```

Rules: `V3_BE05_PAYMENT_STATE_MACHINE_AND_TRANSACTIONS.md`. Files: `V3_BE05_COMPONENT_CHANGE_MATRIX.md`.

---

## 4. Decisions taken inside the approved rules

| # | Decision | Why |
|---|---|---|
| D1 | Ledger = the attempt's `payments` row; UNKNOWN = suffix `~UNKNOWN` on `transaction_code` + `paid_at` as submission time | no schema change; restart-safe; reference never changes (de-duplication) |
| D2 | A collection verified for an Order that is past its deadline (still `PENDING_PAYMENT` or already cancelled) → cancel, restore once, refund once, never confirmed | plan 5.1 / 5.6 |
| D3 | A `payment` NEEDS_ATTENTION is told apart from a delivery NEEDS_ATTENTION by `payment_status` (`UNPAID` vs `PAID`) | BE-04 failed parts are never touched here |
| D4 | The customer cannot cancel while the result is UNKNOWN, before or after the deadline | DEC-17; money may have been collected |
| D5 | Refund simulation failure keeps the cancellation; `SIMULATION_FAILED`, Admin retry (CANCELLED Orders only) | plan 5.2 |
| D6 | The worker is global and idempotent; tests shield seeded expired Orders | the worker must not depend on the caller |
| D7 | The mock gateway is on by default and off in `application-prod.properties` | DEC-21: no simulated result in production |
| D8 | An Admin gateway answer (`COLLECTED` / `NOT_COLLECTED`) stands in for "ask the bank" for an in-doubt attempt | the only way a mock UNKNOWN can ever be concluded; it records evidence, it never sets a status |

No approved rule (DEC-01..25) was changed.

---

## 5. Definition of Done checklist

| Item | Status |
|---|---|
| Mock payment, backend verification, no browser result | DONE |
| Duplicate callback / double payment / double refund / double restock | DONE (tests 03, 11, 23, 33, 42–44) |
| 15-minute timeout with `@Scheduled` (60 s) | DONE (tests 09–13, 49) |
| UNKNOWN reconciliation 15 min, then NEEDS_ATTENTION; blocks pay / cancel / PREPARING | DONE (14–16) |
| FAILED terminal; late callback never revives | DONE (04, 23, 24) |
| Cancel before PREPARING only, whole Order, restore per StoreProduct incl. split SKU | DONE (26–29) |
| No automatic restock of a FAILED part | DONE (31) |
| Simulated full refund, retry-safe | DONE (27, 32, 33) |
| Admin: view, recheck, evidence-only resolution, audit | DONE (19–22, 34–37, HTTP 56/57) |
| Reuse `PaymentDeadlinePolicy`, `OrderStatusAggregator`, `recordOfflineCollected` | DONE (COD / PAY_AT_STORE untouched: 08, 30) |
| Race matrix, rollback, restart | DONE (38–49) |
| No regression vs 1455 baseline; no schema/DDL/seed change; `online-enabled=false` default | DONE |
| Documents in `docs/v3/backend-payments/`, plan and spec updated | DONE |

---

## 6. Known limits and follow-ups

1. Single-row ledger encoding (no per-event log) — `HANDOVER` §6.
2. Uniqueness of "one SUCCESS / one refund" is enforced by the Service under the Order lock, not by an index.
3. Not exercised: a browser end-to-end session; a soak test of the worker over a very large pending set; a real
   gateway (none exists by design).
4. V2 payment/cancel primitives are dead code behind V2 unit tests; remove in BE-07.
5. ONLINE checkout is not opened; the owner decides after reviewing `HANDOVER` §5.

---

## 7. Phase status

| Phase | Status |
|---|---|
| V3-BE-05 Payments, Timeout & Simulated Refunds | **DONE** |
| V3-BE-06 Reviews | NOT STARTED |
| V3-BE-07 Full regression / live verification | NOT STARTED |
