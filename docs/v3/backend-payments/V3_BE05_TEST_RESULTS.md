> Historical phase evidence (up to 14/10/2026). Superseded script names and readiness claims describe that run only. Current setup: [database README](../../seed/DATABASE_SETUP.md); current acceptance: [deep audit](../final-deep-audit/DEEP_AUDIT_REPORT.md).

# V3-BE-05 — TEST RESULTS

**Date:** 14/10/2026 · **Database:** SQL Server 17.0, `oneshop_test` (pom sets `DB_NAME=oneshop_test` for the
forked JVM) · **Operational database `oneshop`:** never written (§7).

## 1. Commands

```text
./mvnw -B test                                              # full suite
./mvnw -B test -Dtest='PaymentSettlementDatabaseIntegrationTest,PaymentConcurrencyDatabaseIntegrationTest,
                       PaymentHttpDatabaseIntegrationTest,PaymentGatewayDisabledDatabaseIntegrationTest,
                       MockPaymentGatewayTest'
```

Comparison per test case (`class.method[param]`, Surefire XML): the BE-04 final run (1455 cases) against the BE-05
final run (1522 cases). Nothing was deleted or edited to hide a failure; no existing test file was changed in this
phase except the two helper classes listed in §6.

## 2. Result

| | BE-04 final | BE-05 final |
|---|---|---|
| Test cases | 1455 | **1522** |
| PASS | 1030 | **1097** |
| FAIL | 57 | 57 |
| ERROR | 326 | 326 |
| SKIPPED | 42 | 42 |
| Non-passing (FAIL + ERROR) | 383 | 383 (the same cases) |

| Per test case | Count |
|---|---|
| Passing before, not passing now (**regressions**) | **0** |
| Non-passing before, passing now | 0 |
| Non-passing before and still non-passing | 425 (383 failing + 42 skipped, the same cases) |
| Present before, absent now | 0 |
| New cases | 67 — **all PASS** |

1455 + 67 = 1522. The 383 failing cases are the V2-seed-dependent legacy cases already failing before BE-03
(`V3_BE03_TEST_RESULTS.md` §5); BE-05 neither fixes nor worsens them.

## 3. New tests (67)

| Class | Cases | Covers |
|---|---|---|
| `PaymentSettlementDatabaseIntegrationTest` | 37 | BE05-01..37: gateway ledger, callbacks, deadline, timeout, reconciliation, cancellation, restoration, refund, Admin |
| `PaymentConcurrencyDatabaseIntegrationTest` | 12 | BE05-38..49: races (3–6 committed rounds each), rollback, restart recovery, scheduled worker |
| `PaymentHttpDatabaseIntegrationTest` | 8 | BE05-50..57: customer page, CSRF/ownership/roles, Admin console |
| `PaymentGatewayDisabledDatabaseIntegrationTest` | 1 | BE05-58: gateway off (production-like) |
| `MockPaymentGatewayTest` | 9 | ledger encoding, gateway answers, Order edge table, note abbreviation |

### 3.1 BE05-xx index

| Id | What it proves |
|---|---|
| 01 | checkout leaves one AWAITING attempt: backend `MOCK-` reference, whole total, stock deducted |
| 02 | verified SUCCESS before the deadline → CONFIRMED/PAID, server `paid_at`, no stock/movement change, note `[PAYMENT_CONFIRMED]` |
| 03 | duplicate callbacks (SUCCESS, FAILED) → no second SUCCESS, no history |
| 04 | verified FAILED → CANCELLED once, 2 Stores restored once each, no retry on the same Order |
| 05 | UNKNOWN → in doubt, durable in `payments`, stock kept; customer never sees the marker |
| 06 | the old browser success/failure calls still refuse and change nothing |
| 07 | other customer 404; an attempt of another Order 404 |
| 08 | COD / PAY_AT_STORE are untouched by the scan and by reconciliation |
| 09 | expired and unpaid → scan cancels, restores once, closes the attempt (`[PAYMENT_TIMEOUT]`) |
| 10 | not expired → nothing |
| 11 | repeated scans → one restoration, no extra history |
| 12 | crash recovery: a collection before the deadline that was never applied confirms the Order |
| 13 | collection at/after the deadline → CANCELLED, restored once, refunded once, row kept, never confirmed |
| 14 | UNKNOWN inside the 15-minute window → reconciling, nothing changes |
| 15 | reconciling blocks new payment, customer cancel (409 `PAYMENT_RECONCILING`) and Staff PREPARING |
| 16 | UNKNOWN after 30 minutes → NEEDS_ATTENTION, stock kept, one history row; cancel / PREPARING refused |
| 17 | UNKNOWN then a verified collection in time → CONFIRMED |
| 18 | UNKNOWN then a verified decline → CANCELLED + restored once |
| 19 | Admin re-check without new evidence → unchanged, audited with the Admin as actor |
| 20 | NEEDS_ATTENTION + collected in time → Admin confirms, no second deduction, Order workable |
| 21 | NEEDS_ATTENTION + not collected → cancelled, 2 Stores restored once, Admin in the part history |
| 22 | NEEDS_ATTENTION + collected late → cancelled, restored once, refunded once |
| 23 | collection after the Order was cancelled → recorded once, refunded once, never revived, no second restore |
| 24 | deadline boundary: SUCCESS at `now >= deadline` is not a normal confirmation |
| 25 | a gateway that cannot answer = UNKNOWN, nothing cancelled |
| 26 | customer cancel unpaid ONLINE: UNPAID, restored once, idempotent |
| 27 | customer cancel PAID ONLINE: restored per Store once, refunded once, evidence kept |
| 28 | cancel after PREPARING → 409, nothing changes (no refund) |
| 29 | a SKU split between two Stores restored per Store (movement sum = ordered quantity) |
| 30 | COD / PAY_AT_STORE cancel behaviour unchanged |
| 31 | a part FAILED after PREPARING is never restocked; payment-domain paths leave it alone |
| 32 | refund simulation failure keeps the cancel, `SIMULATION_FAILED`; retry once; repeat is a no-op |
| 33 | no refund without a collection; retry answers `NOTHING_TO_REFUND` |
| 34 | the payment console is ADMIN only (customer, staff refused on every method) |
| 35 | no evidence → no PAID, no invented collection |
| 36 | a cancelled Order is never revived; COD is not a payment case |
| 37 | Admin list and case detail with the ledger evidence |
| 38 | SUCCESS vs TIMEOUT (5 rounds) |
| 39 | SUCCESS in time vs scan (3 rounds): CONFIRMED, never cancelled |
| 40 | CANCEL vs PAYMENT SUCCESS (6 rounds): always CANCELLED, restored once, refunded once when collected |
| 41 | customer CANCEL vs Staff PREPARING on a paid Order (5 rounds): one winner |
| 42 | duplicate callbacks at once (3 threads, 3 rounds): one SUCCESS, one CONFIRMED row |
| 43 | duplicate cancel at once (3 threads, 3 rounds): one restoration, one refund |
| 44 | two timeout scans at once over 3 Orders (one multi-Store): each cancelled once |
| 45 | scan vs Admin re-check vs late callback: one outcome |
| 46 | restore failure rolls the whole cancellation back; the scan continues; the next scan completes it |
| 47 | Order status write failure rolls the payment capture back |
| 48 | restart: a second application context continues from the database alone |
| 49 | the `@Scheduled` worker cancels an expired Order by itself |
| 50–57 | HTTP: simulate + CSRF + banner; forged fields ignored; CSRF/Bearer/ownership/role; old routes refuse; reconciling page and 409 cancel; paid cancel shows the refund; Admin console + settle; Admin refund retry |
| 58 | gateway off: nobody can settle a payment; the timeout still cancels |

## 4. Concurrency, rollback and recovery evidence (committed, restored)

| Case | Rounds | Result |
|---|---|---|
| SUCCESS vs TIMEOUT (expired) | 5 | CANCELLED; 1 restoration; 1 SUCCESS row; 1 refund; no `CONFIRMED` history |
| SUCCESS in time vs scan | 3 | CONFIRMED; 0 restorations; 1 `CONFIRMED` history row |
| CANCEL vs PAYMENT SUCCESS | 6 | CANCELLED; 1 restoration; refund 1 when collected; never both confirmed and refunded |
| CANCEL vs Staff PREPARING (paid) | 5 | one winner; cancel: all parts cancelled, 2 restorations, 1 refund; PREPARING: `PROCESSING`, 0 restorations, 0 refunds |
| duplicate callback ×3 | 3 | 1 `CONFIRMED` outcome, 1 SUCCESS |
| duplicate cancel ×3 | 3 | 1 restoration, 1 refund, 3 successful responses |
| 3 scans at once, 3 Orders | 1 | 3 CANCELLED, 1 / 1 / 2 restorations, 3 CANCELLED history rows |
| scan ‖ Admin ‖ callback | 1 | CANCELLED, 1 restoration, 1 SUCCESS, 1 refund |
| restore throws | 1 | first Order rolled back whole (PENDING_PAYMENT, parts CONFIRMED, attempt PENDING, no movement); second cancelled; next scan finishes the first once |
| status write throws | 1 | capture rolled back (`paid_at` NULL, attempt PENDING); the retry confirms |
| restart | 2 contexts | state recovered from `payments` only: unapplied collection confirmed, expired cancelled, doubt kept then escalated |
| scheduled worker | 1 | expired Order cancelled within 40 s at a 1 s interval |

## 5. Not covered / BLOCKED

* No fake `Clock`: the deadline is moved by moving `orders.created_at` (the production code reads the injected
  `Clock`; the collection time of a gateway answer is `Clock.now`).
* No browser (Selenium) run; HTTP tests use the real Tomcat/security/SiteMesh/Thymeleaf stack.
* A real bank gateway and real refund: out of scope by design.
* BE-06 (reviews) and BE-07 (final regression): not started.

## 6. Test infrastructure changed

| File | Change |
|---|---|
| `FulfillmentFixtures.java` | helpers: `onlineSplitSameSku`, `ageOrder`, `attemptId`, `payment(s)`, `cancelRestores`, `stockAt`, `quantityAt` |
| `SeedGuardV3.java` | the timeout scan is global; seeded ONLINE Orders that are still pending look expired, so their `created_at` is moved to "now" during a test and put back by `restore()` (otherwise the first scan cancelled seed Order 8) |

## 7. Data and schema validation

| Check | Result |
|---|---|
| Row counts/checksums of `orders`, `order_items`, `order_fulfillments`, `payments`, `inventory_movements`, `store_products`, `cart_items`, `checkout_sessions`, `order_status_history`, `users`, `products` | **identical before and after the full run, in `oneshop` and in `oneshop_test`** |
| Seed Order 8 (ONLINE, pending) in `oneshop_test` after the run | `PENDING_PAYMENT / UNPAID`, part `CONFIRMED`, payment `PENDING`, `created_at` as seeded |
| `database/*.sql` | unmodified |
| Operational database writes | none |

One incident is recorded for honesty: during development, a scan ran **before** `SeedGuardV3` shielded the seeded
Order and cancelled seed Order 8 in `oneshop_test`. It was restored by hand with the original values (copied from
`oneshop`) and re-verified by checksum; the guard now prevents it.
