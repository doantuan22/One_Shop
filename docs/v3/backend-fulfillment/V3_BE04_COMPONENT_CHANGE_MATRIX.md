# V3-BE-04 — COMPONENT CHANGE MATRIX

Legend: **NEW** added · **MOD** changed · **DEL** removed · **GUARD** kept but made to refuse V3 Orders.
Package roots: `src/main/java/com/oneshop` (main), `src/test/java/com/oneshop` (test). Schema: unchanged.

## 1. Production code

| File | Class / method | Previous behaviour | New behaviour | Rule / decision | Test | Status |
|---|---|---|---|---|---|---|
| `service/fulfillment/FulfillmentAction.java` | enum | — | the eight Staff steps with type, from/to status, URL path | spec 9.1, plan BE-04 | `PickupAttemptLimiterTest.actionTable/actionInvariants` | NEW |
| `service/fulfillment/StaffFulfillmentService.java` | `getQueue`, `getRecent`, `getFulfillment`, `perform`, `handover` | order-level Staff services | Fulfillment-centric read and write path; scope by `order_fulfillments.store_id`; Order→Fulfillment lock; payment gate; state machine; pickup code; offline collection; aggregation | V3-BR (Staff by Fulfillment), DEC-15/17/21/22 | `FulfillmentOperationsDatabaseIntegrationTest` (49), `Fulfillment*Concurrency*`, `FulfillmentHttp*` | NEW |
| `service/fulfillment/FulfillmentQueueFilter.java` | record | — | optional Store / status / type / Order / date / open-only filters | filter narrows, never widens | `BE04-07`, `BE04-45` | NEW |
| `service/fulfillment/OrderStatusAggregator.java` | `derive`, `mayWrite`, `recompute` | none (no Order aggregation) | Order status derived from Fulfillments in the same transaction; history only on change; never leaves PENDING_PAYMENT / clears NEEDS_ATTENTION / writes CANCELLED | spec 9.2, 9.3 | `OrderStatusAggregatorTest` (23), `BE04-11/36–39` | NEW |
| `service/fulfillment/PickupAttemptLimiter.java` | `requireNotLocked`, `recordFailure`, `reset` | no limit (V2 code check only) | 5 wrong codes lock a Fulfillment's code 15 minutes | brute-force protection of the 8-char code | `PickupAttemptLimiterTest`, `BE04-24` | NEW |
| `exception/FulfillmentConflictException.java` | codes | — | business conflict with stable code (409) | — | `BE04-12/13/31–33/43`, web 409 | NEW |
| `exception/WebExceptionHandler.java`, `exception/GlobalExceptionHandler.java` | `handleFulfillmentConflict` | 400 for every `BadRequestException` | 409 for `FulfillmentConflictException` | — | `StaffFulfillmentWebIntegrationTest.friendlyErrorsWithoutLeaks` | MOD |
| `repository/OrderFulfillmentRepository.java` | `findKeyById`, `findByIdForUpdate`, `findByOrderIdForUpdate`, `searchForStaff`, `findByIdAndStoreIdIn`, `findWithStoreByOrderId`, `countByStoreAndStatus`, `findStatusesByOrderId`, `countByOrderId`, `existsByStoreIdAndPickupCode` | basic finders | scalar scope key, Fulfillment lock, scoped queue (open work first), grouped counters, status read for aggregation | locking: Order first | all DB tests | MOD |
| `repository/OrderItemRepository.java` | `findByFulfillmentIdInOrderByIdAsc` | — | items of Fulfillments in one query | — | `BE04-01/48` | MOD |
| `service/PaymentService.java` + `service/impl/PaymentServiceImpl.java` | `recordOfflineCollected(order, fulfillment)` | `recordCodCollected` / `recordPayAtStoreCollected` required Order statuses `SHIPPING` / `READY_FOR_PICKUP` (the second is not a V3 Order status) | V3 primitive: COD at `SHIPPING`, PAY_AT_STORE at `READY_FOR_PICKUP` with code; one Fulfillment only; no fake PAID | spec 9.4; DEC-21 | `BE04-11/16/21/22/53/55` | MOD (V2 primitives kept) |
| `service/OrderViewService.java` | `fulfillmentParts`, `detail` | order items only | adds the parts per Store; pickup code only for the owner while READY | customer detail with several Fulfillments | `BE04-26/65/66` | MOD |
| `service/impl/CustomerOrderServiceImpl.java` | `cancelOrder` | one generic 400 once preparation began | 409 `ORDER_ALREADY_PREPARING` when the Order is PROCESSING/SHIPPING/PARTIALLY_COMPLETED/COMPLETED, 409 `ORDER_NEEDS_ATTENTION` when NEEDS_ATTENTION | spec 10.1 | `BE04-43`, `BE04-50`, `BE04-68` | MOD |
| `service/StaffOperationsService.java` | `getDashboard` | Order counters by `orders.store_id` | Fulfillment counters per assigned Store (open, waiting pickup, shipping, held, failed) + open Fulfillments | spec 6 (Staff by Fulfillment) | `BE04-31/46/47`, `StaffOperationsDatabaseIntegrationTest` | MOD |
| `service/StaffOperationsService.java` | `getOrders`, `getOrder`, `getPickupQueue`, `performDelivery`, `performPickup` | order-level | **GUARD**: mutations refuse an Order that has Fulfillments; no route reaches them | no Order-centric Staff | `BE04-10` | GUARD (dead code, remove with V2 tests) |
| `service/impl/DeliveryFulfillmentServiceImpl.java`, `service/impl/PickupFulfillmentServiceImpl.java` | order-level mutations | wrote V2 Order statuses (rejected by the DB CHECK) | **GUARD**: refuse an Order that has Fulfillments | same | `DeliveryFulfillmentServiceTest`, `PickupFulfillmentServiceTest`, `BE04-10` | GUARD |
| `controller/staff/StaffFulfillmentController.java` | queue, delivery/pickup queues, detail, `POST {id}/{action}`, V2 redirects | — | the only Staff fulfillment controller; reads three inputs; V2 URLs redirect | no Order status / Store from the request | `StaffFulfillmentWebIntegrationTest` (24), `FulfillmentHttpDatabaseIntegrationTest` (11) | NEW |
| `controller/staff/StaffOrderController.java`, `StaffDeliveryController.java`, `StaffPickupController.java`, `StaffPickupQueueController.java` | order-level pages and POSTs | existed | removed | no Order-centric Staff | replaced (see TEST_RESULTS §6) | DEL |
| `config/SecurityConfig.java` | CSRF matcher | `/staff/orders/**` form paths | + `/staff/fulfillments/**` (CSRF required even with Bearer) | — | `everyWriteRequiresCsrfEvenWithBearer` | MOD |
| `dto/response/FulfillmentQueueItemResponse.java`, `FulfillmentDetailResponse.java`, `FulfillmentHistoryResponse.java` | records | — | Staff views (no pickup code) | privacy | `BE04-25` | NEW |
| `dto/response/StaffDashboardResponse.java`, `StaffStoreDashboardResponse.java` | records | Order counts | Fulfillment counters; V2 accessors kept as aliases | — | dashboard tests | MOD |
| `dto/response/FulfillmentSummaryResponse.java`, `OrderDetailResponse.java` | records | no tracking/code/times; no parts | + tracking, pickup code (owner only), times; + `fulfillments` list; old constructors kept | customer parts view | `BE04-26/65/66` | MOD |

## 2. Templates

| Template | Change | Status |
|---|---|---|
| `staff/fulfillments/index.html`, `detail.html`, `fragments.html` | queue (filters, pagination, held reason), detail with fixed action forms, status badges | NEW |
| `staff/dashboard.html` | Fulfillment counters (`pending-orders`, `waiting-pickup`, `shipping`, `blocked`, `failed`, stock), open Fulfillments table | MOD |
| `fragments/staff.html` | sidebar: Phần giao hàng / Giao tận nơi / Nhận tại cửa hàng; one current entry | MOD |
| `staff/delivery/fragments.html` | labels for PROCESSING, PARTIALLY_COMPLETED, NEEDS_ATTENTION, FAILED | MOD |
| `orders/fragments.html` | "Phần giao theo chi nhánh" section with status, items, tracking, pickup code (owner) | MOD |
| `staff/orders/{index,detail,fragments}.html`, `staff/pickup/{index,detail,queue}.html`, `staff/delivery/{index,detail}.html` | order-level screens | DEL |

## 3. Tests

| File | Cases | Status |
|---|---|---|
| `FulfillmentFixtures.java` | builders through the real checkout | NEW |
| `FulfillmentOperationsDatabaseIntegrationTest.java` | 49 (BE04-01..48, 68) | NEW |
| `FulfillmentConcurrencyDatabaseIntegrationTest.java` | 8 (BE04-49..56) | NEW |
| `FulfillmentHttpDatabaseIntegrationTest.java` | 11 (BE04-57..67) | NEW |
| `StaffFulfillmentWebIntegrationTest.java` | 24 | NEW |
| `service/OrderStatusAggregatorTest.java`, `service/PickupAttemptLimiterTest.java` | 23 + 5 | NEW |
| `SeedGuardV3.java` | restores StoreProducts a test created; same guard deletes fulfillment history | MOD |
| `SeedGuard.java` | deletes Fulfillments (and history) of Orders a test created | MOD |
| `AbstractIntegrationTest.java` | mock `StaffFulfillmentService` for layout/security tests | MOD |
| `DeliveryWebIntegrationTest.java`, `PickupWebIntegrationTest.java` | V2 Staff route cases removed (12 + 11 cases) and replaced by `StaffFulfillmentWebIntegrationTest`; CSRF/role/customer cases kept | MOD |
| `StaffOperationsDatabaseIntegrationTest.java` | counters and queue SQL changed from Orders to Fulfillments; fixture creates the Fulfillment | MOD |
| `Phase10IntegrationWebDatabaseTest.java` | `verifyReads` uses Fulfillment counters and queues | MOD |
| `StaffStoreScopeDatabaseIntegrationTest.java` | forged-Store URL test uses the Fulfillment URL (200 / 403 / 404) | MOD |
| `AccessControlIntegrationTest.java`, `LayoutsIntegrationTest.java` | label "Phần giao cần xử lý", table id `fulfillments-body` | MOD |

## 4. Not changed on purpose

Schema/DDL, seed data, BE-03 checkout/allocation, `InventoryServiceImpl` (no stock change in any step), ONLINE gate
(`oneshop.checkout.online-enabled=false`), Admin screens, reviews (BE-06), payment timeout/reconciliation/refund/Admin
resolution (BE-05).
