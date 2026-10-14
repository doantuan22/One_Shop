# V3-BE-06 — HANDOVER TO BE-07 AND OPERATIONS

**BE-06 status: DONE for its scope — 14/10/2026.** New cases 53/53 PASS; existing Review Web cases 15/15 PASS. BE-01..05 V3 acceptance 343/343 PASS. The full suite still has 375 inherited fail/error cases and 42 unchanged skips; 0 regressions, 8 improvements, 0 removed cases. BE-07 has not been implemented.

## 1. Contracts provided

| API | Contract |
|---|---|
| `ReviewService.submit(email, ReviewRequest, orderId)` | Authenticated ACTIVE Customer; requires positive OrderItem id; optional Order/Product context checked; locks parent Order, verifies received source/payment, inserts one Review |
| `getOrderReviewItems(email, orderId)` | Owner-only received allocation lines, including PARTIALLY_COMPLETED Orders; review flags by item |
| `getProductReviewItems(email, productId)` | Authenticated account's most recent 20 received lines; explicit choice of OrderItem, no most-recent Order inference; other lines remain accessible from Order Detail |
| `getProductReviews(productId)` | ACTIVE reviews only; verified source name or null; official Reply preferred, legacy fallback preserved |
| `getStaffReviews(page)`, `getStaffReview(id)` | Fresh assignment scope, verified item source, visible/received Review; SQL-filtered queue/count; 403 cross-Store/untraceable, 404 unknown |
| `ReviewService.reply`, `ReviewReplyService.reply` | Staff only; locks Review, rechecks source/status/assignment, creates/edits one dedicated Reply in a write transaction |
| `AdminReviewService` | Admin list and ACTIVE/HIDDEN moderation; same Review lock as Reply |

`ReviewRequest`: `productId` (optional context), `rating`, `comment`, `orderItemId` (required). Its deprecated three-argument constructor only preserves old test compilation; it does not enable product-based submission. Existing response constructors are retained as compilation bridges, not as alternative business paths.

HTTP: Customer `POST /reviews`; Staff `GET /staff/reviews`, `GET /staff/reviews/{id}`, `POST /staff/reviews/{id}/reply`; Admin `GET /admin/reviews`, `POST /admin/reviews/{id}/status`. The old Admin Reply POST explicitly returns 403. Forms require CSRF, including requests carrying Bearer headers.

## 2. Decisions the next phase must retain

- DEC-26 / OPEN-05: one Review per received OrderItem, even two lines of one SKU from two Stores.
- DEC-27 / OPEN-06: Admin moderation only, no Admin Reply.
- Receipt is the individual Fulfillment's COMPLETED status, not whole-Order completion. PAID is required; CANCELLED/REFUNDED Orders cannot create new Reviews.
- Provenance: Review.User/Product and any legacy Order must agree with the item's Order/StoreProduct/Fulfillment. No `Order.getStore()` source fallback.
- Unknown legacy source does not grant Staff authority. No guessed backfill or destructive cleanup.

## 3. Operational upgrade prerequisite

The migration was executed **only on `oneshop_test`**. The operational database's schema/data snapshots remain unchanged. Before deploying this code against an existing operational database:

1. Take/verify the database backup under the project's operational procedure and review the selected database.
2. Run the standalone migration against that explicit database, using the normal credential mechanism:

   ```powershell
   sqlcmd -S <server> -d oneshop -E -C -b -I -f 65001 -i database/migrations/20261014_be06_review_uniqueness.sql
   ```

3. Confirm `UX_reviews_order_item` (unique non-null OrderItem) and `UX_reviews_legacy_user_product` (unique user/Product for null-source rows), enabled/trusted FKs/CHECKs, matching Review/Reply row counts, and Hibernate `ddl-auto=validate`.

The script is rerunnable and transactional, validates key columns and filters, and refuses existing duplicate item/legacy keys without deleting or merging data. It updates no rows. For a fresh database, apply it **after** base scripts 01..05. The base table/seed scripts recreate/reseed data and are not an upgrade route. `05_verify.sql` accepts both base and migrated review uniqueness; when testing its copy against `oneshop_test`, replace only its explicit USE directive.

Rollback is conditional: after same-user/Product reviews on several items have been created, the old global UNIQUE cannot be reinstated without a separate data decision. Do not delete reviews to force a downgrade. A verified backup restores the prior database; new data requires an explicit recovery plan.

## 4. BE-07 work

- Rebuild the remaining V2 fixtures/assertions, including all 24 legacy Review DB errors caused by deleting parent Reviews before existing Replies. Preserve historical rows; use isolated fixtures and V3 source relationships.
- Retire V2 product-level Review assumptions and optional-constructor compatibility only alongside updated tests/callers.
- Include BE-06 partial delivery, same-SKU sources, Staff IDOR/revocation, CSRF, moderation and the four race scenarios in the final end-to-end acceptance.
- Perform browser acceptance and any desired performance/soak tests. Reply revision history would require a separately approved schema design.
- Retain all Checkout/Allocation/Payment/Fulfillment invariants and the existing ONLINE default (`false`). No BE-06 work enables ONLINE checkout or changes BE-05 gateway/refund configuration.

## 5. Historical and technical limits

No unresolved OPEN-05/06 decision remains. No automatic source backfill is attempted. Legacy and new per-item Reviews may coexist for the same user/Product when the old source cannot be proved. Staff access remains denied for missing/mismatched provenance; Admin can view/moderate it safely.

The official Reply has one editable record, its original creation timestamp, latest Staff and updated timestamp. The schema has no per-edit revision log. Legacy Reply columns remain preserved/readable when a dedicated Reply is added. Tests restore all original row counts/checksums, but generated identity counters can advance.

Evidence: [implementation report](V3_BE06_IMPLEMENTATION_REPORT.md), [test results](V3_BE06_TEST_RESULTS.md), [per-case CSV](V3_BE06_TEST_CASE_COMPARISON.csv), [workflow](V3_BE06_REVIEW_AUTHORIZATION_AND_WORKFLOW.md), [changed files](V3_BE06_COMPONENT_CHANGE_MATRIX.md).
