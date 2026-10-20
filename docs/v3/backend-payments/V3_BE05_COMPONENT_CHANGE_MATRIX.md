> Historical phase evidence (up to 14/10/2026). Superseded script names and readiness claims describe that run only. Current setup: [database README](../../seed/DATABASE_SETUP.md); current acceptance: [deep audit](../final-deep-audit/DEEP_AUDIT_REPORT.md).

# V3-BE-05 — COMPONENT CHANGE MATRIX

Legend: **NEW** added · **MOD** changed · **KEEP** unchanged on purpose. Schema/DDL/seed: **unchanged**.
Roots: `src/main/java/com/oneshop` (main), `src/test/java/com/oneshop` (test).

## 1. Production code

| File | Class / method | Previous behaviour | New behaviour | Rule | Test | Status |
|---|---|---|---|---|---|---|
| `service/payment/MockLedger.java`, `LedgerState.java` | encoding of the gateway ledger in `payments` | reference only | AWAITING / IN_DOUBT / CAPTURED / DECLINED / SETTLED derived from status, `paid_at`, reference suffix | DEC-21, ledger persistence | `MockPaymentGatewayTest`, `BE05-01/05` | NEW |
| `service/payment/PaymentGateway.java`, `MockPaymentGateway.java`, `GatewayVerification.java` | `verify`, `capture`, `decline`, `markInDoubt`, `settleInDoubt` | none (browser "success/failure" refused) | stateless server-side gateway over the ledger rows; outage = UNKNOWN | DEC-21 | `MockPaymentGatewayTest` (9), `BE05-25` | NEW |
| `service/payment/PaymentReconciliationService.java` | `simulate`, `reconcile`, `reconcileLocked`, `currentVerification` | none | the one engine: confirm in time, cancel on FAILED/NOT_PAID, late collection → cancel + refund, UNKNOWN window → NEEDS_ATTENTION; duplicate callbacks idempotent; late collection on CANCELLED recorded once | DEC-15/17/18/21, plan 5.1/5.6 | `BE05-02..25`, races | NEW |
| `service/payment/OrderCancellationService.java` | `cancelAndRestore` | V2 per-path code | single cancel-and-restore: parts locked, restore per StoreProduct, history, attempts closed, Order CANCELLED; refuses started/failed parts; double-restore guard | plan task 7, DEC-22 | `BE05-04/09/21/26..31` | NEW |
| `service/payment/OrderStatusWriter.java` | `write`, `audit` | `OrderServiceImpl.writeStatusAndHistory` (private) | allowed payment-domain edges only, history always, note abbreviated to 500 | DEC-19 | `MockPaymentGatewayTest`, `BE05-19` | NEW |
| `service/payment/RefundService.java`, `RefundSimulator.java`, `DefaultRefundSimulator.java` | `simulate` | none | whole simulated refund of collected ONLINE rows; failure recorded (`SIMULATION_FAILED`), retry safe; Order `REFUNDED` | plan task 11 | `BE05-13/27/32/33` | NEW |
| `service/payment/CustomerOrderCancellationService.java` | `cancel` | `CustomerOrderServiceImpl.cancelOrder` (unpaid only) | payment result applied first; refused while UNKNOWN; paid ONLINE cancel + refund; refused after PREPARING / in NEEDS_ATTENTION; idempotent | spec 10.1, DEC-17 | `BE05-15/26..31`, race 3/4/6 | NEW |
| `service/payment/PaymentTimeoutService.java`, `PaymentTimeoutWorker.java`, `config/PaymentSchedulingConfig.java` | `scan`, `@Scheduled run` | none | candidate scan (expired, settled-but-unapplied, payment NEEDS_ATTENTION), one transaction per Order, failure isolated; worker off by property | plan 5.4 | `BE05-09..18`, `BE05-46/48/49` | NEW |
| `service/payment/AdminPaymentService.java` | `getCases`, `getCase`, `recheck`, `settleGateway`, `retryRefund` | none | ADMIN-only; resolution by ledger evidence only; audited; refund retry for CANCELLED Orders only | DEC-19 | `BE05-19..22/34..37`, HTTP `BE05-56/57` | NEW |
| `exception/PaymentConflictException.java` + `WebExceptionHandler`, `GlobalExceptionHandler` | codes `PAYMENT_RECONCILING`, `GATEWAY_DISABLED`, … | — | 409 with a stable code | — | `BE05-15/58`, HTTP 409 | NEW / MOD |
| `repository/OrderRepository.java` | `findPaymentCandidateIds`, `findPaymentCasesForAdmin` | — | keyset candidate query; Admin case list | — | scan tests | MOD |
| `repository/InventoryMovementRepository.java` | `existsByReferenceOrderIdAndType`, `countBy…` | — | double-restore guard | — | `OrderCancellationService` paths | MOD |
| `service/PaymentDeadlinePolicy.java` | `expiredBefore()` | — | one place for the "expired before" instant | DEC-20 | scan tests | MOD |
| `service/impl/PaymentServiceImpl.java` | `getOrderPayments`, `response`, `mockReference` | no gateway info; raw code | ledger state, reconciling flag, gateway switch for the page; reference without the marker; one reference format | — | `BE05-05`, HTTP `BE05-50/54` | MOD |
| `service/impl/CustomerOrderServiceImpl.java` | `cancelOrder` | inline cancel (UNPAID only) | delegates to `CustomerOrderCancellationService` (setter injection; old code kept as fallback) | — | all cancel tests | MOD |
| `service/OrderTransitionPolicy.java` | `canCustomerCancel` | UNPAID only | + ONLINE PAID `CONFIRMED` (button shown) | spec 10.1 | `BE05-27`, `BE05-55` | MOD |
| `controller/client/PaymentController.java` | `POST …/{paymentId}/simulate` | success/failure routes (refused) | demo scenario (SUCCESS / FAILED / UNKNOWN) enacted by the gateway, then verified; old routes still refuse | DEC-21 | HTTP `BE05-50..53` | MOD |
| `controller/admin/AdminPaymentController.java` | list, detail, `recheck`, `gateway`, `refund` | — | fixed POSTs; no status / amount / time input | DEC-19 | HTTP `BE05-56/57` | NEW |
| `dto/response/OrderPaymentResponse.java` | + `gatewayEnabled`, `ledgerState`, `reconciling`, `canSimulate()` | deadline only | old constructors kept | — | `PaymentWebIntegrationTest` (unchanged) | MOD |
| `dto/response/AdminPaymentCaseResponse.java`, `AdminPaymentCaseDetailResponse.java`, `AdminPaymentAttemptResponse.java` | records | — | Admin console views (evidence, ledger state, refund) | — | `BE05-37` | NEW |
| `config/PaymentProperties.java`, `application.properties`, `application-prod.properties` | `timeoutWorkerEnabled`, `scanBatchSize`, `mockGatewayEnabled` | 3 timing properties | + 3; gateway **off in prod**; `online-enabled` stays `false` | DEC-20/21 | `BE05-58` | MOD |
| `config/SecurityConfig.java` | CSRF matcher | — | `/admin/payments/**` form paths require CSRF even with Bearer | — | HTTP `BE05-56` | MOD |
| `pom.xml` | surefire system property | — | `oneshop.payment.timeout-worker-enabled=false` for the suite (the scan would cancel the seeded expired Order) | — | whole suite | MOD |
| `InventoryServiceImpl`, `OrderServiceImpl`, `StaffFulfillmentService`, `OrderStatusAggregator`, checkout | — | — | **unchanged** (reused: stock restore, Fulfillment steps, aggregation, allocation) | BE-01..04 kept | regression run | KEEP |

## 2. Templates

| Template | Change | Status |
|---|---|---|
| `payment/index.html` | outcome banner, reconciling notice, demo gateway scenarios (hidden unless payable and AWAITING), REFUNDED / NEEDS_ATTENTION labels, paid time only for SUCCESS | MOD |
| `admin/payments/index.html`, `admin/payments/detail.html` | case list; case detail with ledger rows, parts, actions, history | NEW |
| `fragments/admin.html`, `admin/order-detail.html` | menu entry; link "Xử lý thanh toán" | MOD |
| `staff/delivery/fragments.html` | payment label `REFUNDED` | MOD |

## 3. Tests

| File | Cases | Covers |
|---|---|---|
| `PaymentSettlementDatabaseIntegrationTest` | 37 (BE05-01..37) | ledger, deadline, timeout, reconciliation, cancel, restore (multi-Store, split SKU), refund, Admin |
| `PaymentConcurrencyDatabaseIntegrationTest` | 12 (BE05-38..49) | race matrix, rollback, restart, scheduled worker |
| `PaymentHttpDatabaseIntegrationTest` | 8 (BE05-50..57) | customer page, security, Admin console over HTTP |
| `PaymentGatewayDisabledDatabaseIntegrationTest` | 1 (BE05-58) | production-like gateway switch |
| `service/MockPaymentGatewayTest` | 9 | ledger encoding, gateway answers, Order edges |
| `FulfillmentFixtures`, `SeedGuardV3` | helpers | split-SKU order, order ageing, stock helpers; guard shields seeded expired ONLINE Orders from the scan |
