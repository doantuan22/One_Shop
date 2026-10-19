> Historical phase evidence (up to 14/10/2026). Superseded script names and readiness claims describe that run only. Current setup: [database README](../../../database/README.md); current acceptance: [deep audit](../final-deep-audit/DEEP_AUDIT_REPORT.md).

# V3-BE-06 — REVIEW AUTHORIZATION AND WORKFLOW

Date: 14/10/2026. Architecture and Checkout/Allocation/Payment/Fulfillment rules from BE-01..05 are retained.

## 1. Decisions confirmed by the project owner

During BE-06 the actual `oneshop_test` index was inspected: `UQ_reviews_user_product` restricted a Customer to one Review per Product. OPEN-05 and OPEN-06 were still open in the specification. The owner explicitly decided:

- **DEC-26:** one Review per OrderItem. Two received allocations of the same SKU have two independent Reviews. A data-preserving UNIQUE migration is permitted as an exception to the schema freeze.
- **DEC-27:** Admin views/moderates only. Only Staff with a current ACTIVE assignment at the verified source Store creates/edits the official Reply.

## 2. Review eligibility and identity

`POST /reviews` accepts `orderItemId`, rating 1..5 and optional comment (up to 1000 UTF-16 code units, matching SQL Server NVARCHAR(1000)). Optional `orderId` and `productId` are checked navigation context, never ownership authority. Customer identity comes from the authenticated principal and is revalidated as an ACTIVE CUSTOMER in the database. Foreign-owned/mismatched context returns 404.

Receipt requires:

1. The Order belongs to the authenticated Customer.
2. OrderItem belongs to the locked Order; its Fulfillment belongs to that same Order.
3. StoreProduct.Store equals Fulfillment.Store. Product is derived from that StoreProduct.
4. The item's Fulfillment is COMPLETED. The Order is PAID and not CANCELLED; REFUNDED cannot pass.
5. No Review already references this OrderItem, including HIDDEN Reviews.

The Order need not be COMPLETED. A COMPLETED part of a PARTIALLY_COMPLETED Order is reviewable while another part is SHIPPING. Two received lines of one SKU remain distinct, including on Product Detail.

No catalog ACTIVE restriction is imposed on historical purchases. No stock or payment mutation occurs in a Review operation. Input markup is stored as plain text and rendered using `th:text`; control characters other than CR/LF/TAB are refused. Blank comments are optional; blank Replies are invalid.

## 3. Transaction and concurrency rules

Review creation first reads a scalar OrderItem ownership key, then locks the parent Order with PESSIMISTIC_WRITE, and only then loads/checks the receipt facts and inserts/flushes the Review. The lock matches the parent lock used by BE-04/05. Duplicate concurrent requests for one item produce one Review; a second received line of the same SKU is independently allowed.

Reply creation/editing locks the Review before reading its status or existing optional Reply. This handles concurrent first Replies without relying on locking a row that does not exist. Admin moderation takes the same Review lock. Hiding first blocks a subsequent Reply; a Reply that commits first remains stored when hidden. Existing Reply edits preserve its PK/created_at and update the latest Staff/content/updated_at.

The public `ReviewService.reply` method is explicitly a **write** transaction; its delegated Reply service also requires a write transaction. This avoids joining the Review reader's class-level read-only transaction. Tests verify actual SQL updates and rollback after insert/update flush.

## 4. Staff source and authorization

`ReviewSource.verified` checks all relationships, including Review.User = Order.User, Review.Product = StoreProduct.Product, and any legacy Review.Order matching the item's Order. `Order.getStore()` is never an authorization source.

Staff queue content and pagination count are filtered by verified source in SQL, ACTIVE Review status, receipt/payment conditions and the Store ids resolved from current ACTIVE assignments. Detail and mutation recheck this scope in the Service. An ACTIVE STAFF account and ACTIVE Store are required by the existing assignment resolver. Revocation applies on the next request even with the old JWT. Multiple assignments grant exactly those Stores.

| Request | Result |
|---|---|
| Staff, verified received Review in assigned Store | detail/Reply permitted |
| Staff, other Store or unverified legacy source | 403; no mutation |
| Staff, hidden Review / inactive assignment, Store or account | 403 |
| Unknown Review id | 404 |
| Customer-owned received item | Review permitted |
| Foreign-owned item or inconsistent Order/Product context | 404 |
| Shipping/preparing item, duplicate item, invalid content | refused; no new Review |
| Admin Reply, including direct service call | 403 |
| Admin moderation | ACTIVE/HIDDEN only; existing Review/Reply history retained |

## 5. SQL Server uniqueness migration

`database/migrations/20261014_be06_review_uniqueness.sql` runs in the explicitly selected database. It takes a table write lock inside an XACT_ABORT transaction, refuses existing duplicate item/legacy keys, drops `UQ_reviews_user_product`, and installs:

| Index | Key | Filter |
|---|---|---|
| `UX_reviews_order_item` | UNIQUE(order_item_id) | order_item_id IS NOT NULL |
| `UX_reviews_legacy_user_product` | UNIQUE(user_id, product_id) | order_item_id IS NULL |
| `UQ_review_replies_review` (existing) | UNIQUE(review_id) | unchanged |

The script validates existing index columns/filters on rerun and never updates/deletes/relinks rows. It adds no tables, columns or enum values. Existing FKs/CHECKs remain trusted/enabled. `05_verify.sql` accepts the base V3 or migrated review uniqueness; other checks remain intact. Base create/seed scripts are retained; a fresh install must apply this migration after the base scripts.

## 6. Historical data

No automatic backfill is performed. A Review without a provable OrderItem remains visible under existing moderation and keeps its legacy Reply, but grants no Staff access. Admin can read it without dereferencing Order.Store. Dedicated Replies take display priority; existing legacy Reply columns are kept and Admin can view a distinct historical reply. A legacy Review and a new per-item Review may coexist for one Product when the old source cannot be proved.

There is one editable official Reply, as the existing schema specifies. Its latest author and timestamps are stored; the schema has no per-edit revision log. Source relations are checked, never repaired or guessed in this phase.

## 7. HTTP and views

Customer: `POST /reviews`; forms on `/orders/{id}` and `/products/{id}` identify each received OrderItem and its branch. Staff: `GET /staff/reviews`, `GET /staff/reviews/{id}`, `POST /staff/reviews/{id}/reply`; links use the Fulfillment id. Admin: `GET /admin/reviews`, `POST /admin/reviews/{id}/status`; the old Reply URL explicitly returns 403.

JWT/role checks, method security and CSRF protect these forms, including a Bearer header combined with form routes. Thymeleaf/SiteMesh/Bootstrap styles are reused. A pre-existing Product Detail null dereference was corrected by placing the conditional outside the replaced availability fragment; this is needed to render the Review section without selecting a Store.
