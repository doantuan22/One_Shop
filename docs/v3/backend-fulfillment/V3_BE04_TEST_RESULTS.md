> Historical phase evidence (up to 14/10/2026). Superseded script names and readiness claims describe that run only. Current setup: [database README](../../../database/README.md); current acceptance: [deep audit](../final-deep-audit/DEEP_AUDIT_REPORT.md).

# V3-BE-04 — TEST RESULTS

**Date:** 13/10/2026 · **Database:** SQL Server 17.0, `oneshop_test` (the pom sets `DB_NAME=oneshop_test` for the
forked test JVM) · **Operational database `oneshop`:** never written (snapshot before/after identical, §7).

## 1. Commands

```text
./mvnw -B test                                   # full suite (pom: DB_NAME=oneshop_test)
./mvnw -B test -Dtest='FulfillmentOperationsDatabaseIntegrationTest,FulfillmentConcurrencyDatabaseIntegrationTest,
                       FulfillmentHttpDatabaseIntegrationTest,StaffFulfillmentWebIntegrationTest,
                       OrderStatusAggregatorTest,PickupAttemptLimiterTest'
```

Per-test-case comparison: Surefire XML of the BE-03 final run (1358 cases) against the BE-04 final run (1455 cases),
by `class.method[param]` (not by stack frame; the earlier "197 failing tests" figure of BE-03 was a stack-frame
count and is already corrected there).

## 2. Result

| | BE-03 final | BE-04 final |
|---|---|---|
| Test cases | 1358 | **1455** |
| PASS | 933 | **1030** |
| FAIL | 52 | 57 |
| ERROR | 331 | 326 |
| SKIPPED | 42 | 42 |
| Non-passing (FAIL + ERROR) | 383 | 383 |

| Comparison, per test case | Count |
|---|---|
| Passed before, not passing now (**regressions**) | **0** |
| Failing before, passing now | 0 |
| Non-passing before and still non-passing | 425 (383 failing + 42 skipped; the same cases) |
| Present before, absent now (**replaced**, §6) | 23 |
| New cases | 120 — **all PASS** |

1358 − 23 + 120 = 1455. The 383 failing cases are exactly the V2-seed-dependent cases already failing before BE-03
(classification in `V3_BE03_TEST_RESULTS.md` §5); BE-04 neither fixes nor worsens them. 57 + 326 = 383; the
FAIL/ERROR split moved by five cases that were already non-passing
(`StaffOperationsDatabaseIntegrationTest.forgedQueryHeaderCookieAndGetBodyCannotChangeAssignedScope` and
`crossStoreAndMissingDetailReturnSame404WithoutOrderResource[1..4]`: ERROR → FAIL because the V2 order detail URL
`/staff/orders/{id}` is now a redirect). They test the removed V2 order detail page; their V3 equivalents are
`BE04-02/04/58`.

## 3. New tests (120)

| Class | Cases | Covers |
|---|---|---|
| `FulfillmentOperationsDatabaseIntegrationTest` | 49 | BE04-01..48 and 68: authorization, DELIVERY / STORE_PICKUP state machines, pickup code, COD / PAY_AT_STORE collection, payment gate, aggregation, stock invariants, queue, dashboard |
| `FulfillmentConcurrencyDatabaseIntegrationTest` | 8 | BE04-49..56: double click, cancel vs PREPARING (6 committed rounds), two Stores at once, last two parts, three rollback faults |
| `FulfillmentHttpDatabaseIntegrationTest` | 11 | BE04-57..67: real Tomcat/CSRF/SiteMesh/Thymeleaf with real data |
| `StaffFulfillmentWebIntegrationTest` | 24 | UI, CSRF, binding, error mapping, redirects, sidebar (service mocked) |
| `OrderStatusAggregatorTest` | 23 | spec 9.3 table (16 rows), `recompute` protections |
| `PickupAttemptLimiterTest` | 5 | lock at 5, unlock after 15 min, per Fulfillment, action table invariants |

### 3.1 BE04-xx index

| Id | What it proves |
|---|---|
| 01 | queue lists only the Staff's Store's Fulfillments, own lines only |
| 02 | other Store's detail → 403; own → 200 |
| 03 | other Store's step → 403, nothing written |
| 04 | unknown id → 404 |
| 05 | customer principal refused (queue, detail, step) |
| 06 | INACTIVE assignment → nothing visible/changeable |
| 07 | `storeId` filter narrows only; foreign Store → 403 |
| 08 | two assignments → both Stores, no third |
| 09 | `orders.store_id` is not authority |
| 10 | V2 order-level mutation refused for an Order with Fulfillments |
| 11 | COD delivery full path, Order derived, COD collected once with the Order total, history counts |
| 12 | no step skipping (409) |
| 13 | no repeat; one history row |
| 14 | wrong-type steps refused |
| 15 | tracking code validated and stored |
| 16 | COD collected only at completion; second completion cannot collect |
| 17 | failure needs a reason |
| 18 | FAILED → NEEDS_ATTENTION only: no restock, no movement, no payment change |
| 19 | FAILED terminal; Order held for other Stores |
| 20 | history with actor; creation row; Order history only on change |
| 21 | PAY_AT_STORE pickup: code at READY, payment at COMPLETE |
| 22 | wrong / missing code changes nothing, collects nothing |
| 23 | code case/space insensitive |
| 24 | 5 wrong codes lock even the right code |
| 25 | no Staff DTO / queue / history note contains the code |
| 26 | owner sees the code while READY; other customer 404 |
| 27 | ONLINE paid pickup: no extra payment |
| 28 | READY needs PREPARING; code absent before |
| 29 | pickup-only Store runs pickup |
| 30 | codes independent per Fulfillment |
| 31 | PENDING_PAYMENT held (409), shown as blocked, counted as held not as work |
| 32 | NEEDS_ATTENTION held, not cleared |
| 33 | CANCELLED not workable |
| 34 | ONLINE paid workable |
| 35 | ONLINE `CONFIRMED` but not PAID is not workable |
| 36 | one Store PREPARING → PROCESSING, other part untouched (V3-T17) |
| 37 | COMPLETED + SHIPPING → PARTIALLY_COMPLETED; both → COMPLETED (V3-T18/T19) |
| 38 | FAILED beside COMPLETED → NEEDS_ATTENTION, completed part kept |
| 39 | Order history only on change |
| 40 | no Staff method takes an Order status; no action targets CANCELLED |
| 41 | delivery lifecycle: stock and movements unchanged |
| 42 | pickup lifecycle: stock and movements unchanged |
| 43 | cancel after PREPARING → 409 `ORDER_ALREADY_PREPARING`, nothing restored |
| 44 | cancel before any step: stock restored once, Fulfillment CANCELLED, idempotent |
| 45 | queue filters and ordering |
| 46 | dashboard counters per Store |
| 47 | dashboard scoped |
| 48 | detail lists only own lines |
| 49 | double click on PREPARE (3 rounds) |
| 50 | cancel vs PREPARING (6 rounds) |
| 51 | two Stores' Staff on one Order at once (3 rounds) |
| 52 | last two parts completed at once → one COMPLETED, one payment |
| 53 | collection failure at COD completion rolls back |
| 54 | aggregation failure rolls back the part |
| 55 | collection failure at pickup keeps READY and the code |
| 56 | rejected step leaves other Fulfillment and Order alone |
| 57–67 | HTTP: queue/alias, 403/404, POST ignoring forged fields, CSRF/other Store/customer, 409/404 codes, pickup over HTTP, V2 URLs, dashboard, customer page (owner/other), multi-Store page, generic handover |
| 68 | customer cannot cancel `NEEDS_ATTENTION` (409 `ORDER_NEEDS_ATTENTION`) |

## 4. Concurrency and rollback evidence (SQL Server, committed, restored)

Races run with separate transactions and 60 s timeouts; the database is restored by `SeedGuardV3` after each case.

| Case | Rounds | Outcome |
|---|---|---|
| double click PREPARE | 3 | exactly one success, loser `FULFILLMENT_STATUS_CHANGED`, +1 Fulfillment history, +1 Order history |
| cancel vs Staff PREPARING | 6 | exactly one winner every round; cancel wins → Order and part `CANCELLED`, one `CANCEL_ORDER` movement, Staff gets `ORDER_CANCELLED`; Staff wins → `PROCESSING`/`PREPARING`, no restore, customer gets `ORDER_ALREADY_PREPARING`; never half cancelled |
| two Stores' Staff, same Order | 3 | both succeed, no deadlock, one Order transition |
| last two parts complete together | 3 | one `COMPLETED` Order history row, one SUCCESS payment |
| collection failure (COD / pickup) | 1 + 1 | whole step rolled back; code still valid; unpaid |
| aggregation failure | 1 | Fulfillment change rolled back (`prepared_at` null, no history) |

## 5. The 383 non-passing cases

Unchanged set; V2-seed-dependent classes (Phase 9/10/11/12 database and web tests, V2 checkout/payment DB tests,
review DB tests). They belong to the V3-BE-04/05/07 rebuild listed in `V3_BE03_TEST_RESULTS.md` §5. BE-04 rebuilt the
Staff-facing part of that backlog as the 120 new cases and migrated the legacy Staff tests below.

## 6. Test cases replaced / modified (and why)

### 6.1 Removed (23 cases) — their routes no longer exist (order-level Staff URLs); replaced one for one

| Removed case (class) | Replacement |
|---|---|
| `DeliveryWebIntegrationTest.listAndDetailUseStaffLayoutSnapshotsAuditAndSafeHtml` | `StaffFulfillmentWebIntegrationTest.queueAndDetailUseStaffLayoutAndSafeHtml` |
| `DeliveryWebIntegrationTest.showsOnlyFixedNextActionAndCsrf[1..4]` | `showsOnlyTheStatesActionsWithCsrf` (8 parameters, every action) |
| `DeliveryWebIntegrationTest.blockedPaymentOrTerminalHasNoAction[1..4]` | `heldOrderHasNoAction` (3) + `terminalHasNoAction` (3) |
| `DeliveryWebIntegrationTest.fixedPostsUsePrincipalAndIgnoreClientStoreActorStatusAmountMethodCode` | `fixedPostsBindOnlyThreeInputs`, `handoverBindsOnlyPickupCode`, `BE04-59` |
| `DeliveryWebIntegrationTest.getCannotMutateAndThereIsNoGenericTransitionEndpoint` | `getCannotMutateAndNoGenericTransition` |
| `DeliveryWebIntegrationTest.unknownOrderScopeAndDatabaseFailuresShowFriendlyErrorsWithoutSql` | `friendlyErrorsWithoutLeaks`, `BE04-58`, `BE04-61` |
| `PickupWebIntegrationTest.staffSeesSnapshotsHistoryAndOnlyNextFixedActionWithCsrf[1..3]` | `showsOnlyTheStatesActionsWithCsrf`, `pickupCodeIsNeverPrinted` |
| `PickupWebIntegrationTest.staffListHasWorkingSidebarAndNoCode` | `queueAndDetailUseStaffLayoutAndSafeHtml`, `sidebarMarksOneCurrentEntry`, `BE04-57` |
| `PickupWebIntegrationTest.blockedOrTerminalOrderHasNoStaffAction[1..4]` | `heldOrderHasNoAction`, `terminalHasNoAction` |
| `PickupWebIntegrationTest.fixedPostsBindOnlyPrincipalOrderAndPickupCode` | `fixedPostsBindOnlyThreeInputs`, `BE04-62` |
| `PickupWebIntegrationTest.getCannotMutateAndGenericTransitionDoesNotExist` | `getCannotMutateAndNoGenericTransition` |
| `PickupWebIntegrationTest.safeErrorsHideDatabaseDetailsAndPickupCode` | `friendlyErrorsWithoutLeaks` |

Kept and still passing in those two classes: `everyWriteRequiresCsrfEvenWithBearer`,
`customerAdminGuestAndStaffWithoutAssignmentCannotReadOrMutate`, `allMutationsRequireCsrfForCookiesBearerAndEncodedPaths`,
`roleAndAssignmentBoundariesBlockCustomerAdminGuestAndUnassignedStaff`, `customerDetailShowsCodeAndTimelineAndOffersNoCompletion`,
`customerOwnership404AndRoleBoundariesPreventLeaks`.

### 6.2 Modified (assertions changed because the Staff unit became the Fulfillment; same intent, still passing)

| Case | Change |
|---|---|
| `StaffOperationsDatabaseIntegrationTest` dashboard / low-stock / per-state counters / queue / pagination | SQL expectations count `order_fulfillments` (open, waiting pickup) excluding Orders held by the payment gate; fixture Order also creates its Fulfillment; HTML ids are `data-fulfillment-id`; the queue shows open work first, `?open=false` shows every status |
| `Phase10IntegrationWebDatabaseTest.tc13…` (`verifyReads`) | same Fulfillment counters and queue SQL; pickup queue URL `/staff/fulfillments/pickup` |
| `StaffStoreScopeDatabaseIntegrationTest.existingOrderUrlsStillEnforceStoreScopeWithForgedRequestStore` | uses `/staff/fulfillments/{id}`: own 200, other Store 403, unknown 404, forged `store_id` ignored |
| `AccessControlIntegrationTest.assignedStaffSeesTheirStore`, `staffWithoutActiveAssignmentGetsNoStoreData` | card label "Phần giao cần xử lý" |
| `LayoutsIntegrationTest.staffAreaUsesTheStaffLayout` | label and table id `fulfillments-body` |
| `StaffOperationsDatabaseIntegrationTest` (compile) | `recentOrders()` elements are Fulfillment rows |

No test was deleted to hide a failure; every removed case has the replacement above and the per-case comparison shows
zero regressions.

## 7. Data and schema validation

| Check | Result |
|---|---|
| Row counts/checksums of `orders`, `order_items`, `order_fulfillments`, `payments`, `inventory_movements`, `store_products`, `cart_items`, `checkout_sessions`, `order_status_history`, `users`, `products` | **identical before and after the full run, in `oneshop` and in `oneshop_test`** |
| `database/*.sql` | unmodified (`git diff --stat -- database` empty) |
| Operational DB writes | none (tests run on `oneshop_test` only; the committing tests refuse a database whose name does not end in `_test`) |

## 8. BLOCKED / not covered

* Verified payment result, timeout worker, reconciliation, Admin resolution of `NEEDS_ATTENTION`, refund simulation:
  BE-05. Tests simulate "payment verified" with SQL (`FulfillmentFixtures.markPaid`).
* Reviews: BE-06.
* A browser end-to-end (Selenium) run was not performed; HTTP-level tests use the real server stack.
