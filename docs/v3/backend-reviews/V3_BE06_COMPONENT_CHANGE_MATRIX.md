> Historical phase evidence (up to 14/10/2026). Superseded script names and readiness claims describe that run only. Current setup: [database README](../../seed/DATABASE_SETUP.md); current acceptance: [deep audit](../final-deep-audit/DEEP_AUDIT_REPORT.md).

# V3-BE-06 — COMPONENT CHANGE MATRIX

Date: 14/10/2026. This inventory covers BE-06 changes only; the pre-existing uncommitted BE-05 work is retained.

## Production

| File / group | Change |
|---|---|
| `service/ReviewService.java`, `service/impl/ReviewServiceImpl.java` | OrderItem-based submission, receipt eligibility, ownership, duplicate protection, received-line readers, scoped Staff detail/queue, write-transaction Reply delegation |
| `service/review/ReviewSource.java` (new) | Consistency/receipt/provenance rules; no Order.Store fallback |
| `service/review/ReviewContent.java` (new) | Shared service-side comment/Reply validation |
| `service/review/ReviewReadMapper.java` (new) | Public/Staff/Admin view models; safe historical source and Reply fallback |
| `service/review/ReviewReplyService.java` (new) | Staff-only dedicated Reply create/edit, Review row lock, fresh assignment authorization |
| `service/AdminReviewService.java` | Safe source mapper; moderation uses Review lock |
| `repository/ReviewRepository.java` | SQL-scoped provenance/content/count, singular read graphs, item duplicate checks and Review lock |
| `repository/OrderItemRepository.java` | Scalar ownership key, item reader and received-line queries; existing other-domain methods retained |
| `dto/request/ReviewRequest.java` | Required positive orderItemId; productId optional checked context; deprecated constructor only for old callers' compilation |
| `dto/response/ReviewableItemResponse.java` | Per-item/order/Fulfillment/Store context and reviewable flag |
| `dto/response/StaffReviewResponse.java` | OrderItem/Fulfillment ids for correct links |
| `dto/response/AdminReviewResponse.java` | Verified source and preserved historical Reply metadata |
| `dto/response/ReviewResponse.java` | Documentation of verified source corrected; record shape unchanged |
| `entity/Review.java` | Source documentation corrected; mapping/columns unchanged |
| `controller/client/CustomerOrderController.java` | Received-item Review options on partially received Orders |
| `controller/client/ProductController.java` | Explicit per-item choices, no inferred most-recent Order |
| `controller/client/ReviewController.java` | Documentation of V3 request/eligibility; existing validation/redirect handling retained |
| `controller/staff/StaffReviewController.java` | Scoped Review detail route; existing 403 handler reused |
| `controller/admin/AdminReviewController.java` | Admin Reply explicitly denied; moderation retained |
| `config/SecurityConfig.java` | Review Customer/Admin forms always require CSRF, including Bearer requests; pre-existing BE-05 changes preserved |

## Views and database

| File | Change |
|---|---|
| `templates/orders/detail.html` | Per-received-item forms and unique ids |
| `templates/product/detail.html` | Per-item forms, verified branch display, escaped Review/Reply; null-safe availability fragment wrapper |
| `templates/staff/reviews/index.html` | Correct source/Fulfillment links and detail links |
| `templates/staff/reviews/detail.html` (new) | Scoped detail and official Reply form |
| `templates/admin/reviews.html` | Read-only Reply/history, unknown source label, moderation form |
| `database/migrations/20261014_be06_review_uniqueness.sql` (new) | Owner-authorized transactional, rerunnable UNIQUE-only migration |
| `database/05_verify.sql` | Accepts original or migrated review uniqueness |

No edits to base table DDL, seed scripts, Checkout/Allocation/Payment/Refund/Fulfillment implementations, ONLINE defaults, BE-01..05 acceptance reports or Git history.

## Tests and documents

| File | Purpose |
|---|---|
| `ReviewV3TestSupport.java` (new) | Real services/SQL fixtures, test-database guard, restore + checksums of all 22 tables |
| `ReviewV3DatabaseIntegrationTest.java` (new) | Eligibility, source, same-SKU reviews, moderation, historical data, FKs/UNIQUE/CHECK, flushed rollback |
| `ReviewV3ConcurrencyDatabaseIntegrationTest.java` (new) | Four races, three committed rounds each |
| `ReviewV3HttpDatabaseIntegrationTest.java` (new) | Real Tomcat/JWT/CSRF/controllers/services/Thymeleaf/SQL, no business mocks |
| `service/ReviewRulesTest.java` (new) | Provenance, money/receipt guards, historical Reply precedence and content boundaries |
| `ReviewWebIntegrationTest.java` | Existing case identities retained; request/form expectations updated to OrderItem; Admin Reply expectation updated to denied |
| Business specification + Backend Migration Plan | DEC-26/27 decisions, migration exception, eligibility correction and BE-06 status |
| `docs/v3/backend-reviews/` | Five phase documents and per-case baseline comparison evidence |
