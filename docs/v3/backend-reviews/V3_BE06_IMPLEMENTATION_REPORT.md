> Historical phase evidence (up to 14/10/2026). Superseded script names and readiness claims describe that run only. Current setup: [database README](../../../database/README.md); current acceptance: [deep audit](../final-deep-audit/DEEP_AUDIT_REPORT.md).

# V3-BE-06 — IMPLEMENTATION REPORT

**Phase Status: DONE for the BE-06 scope — 14/10/2026.**

Reviews now reference received OrderItems. Staff Replies follow the verified supplying branch. Admin moderation is retained, historical Review/Reply data is preserved, and all BE-06 acceptance tests pass. BE-07 is not implemented; ONLINE checkout remains disabled by default.

## 1. Owner decisions and schema exception

The preparation audit found OPEN-05/06 unresolved and verified that the real SQL Server `UQ_reviews_user_product` prevented two Reviews for same-SKU allocations. The owner explicitly approved:

- **DEC-26:** one Review per OrderItem, including the same SKU from two Stores; a data-preserving UNIQUE migration is permitted.
- **DEC-27:** Admin moderation only; Staff with current ACTIVE assignments at the source Store create/edit Replies.

The migration was applied only to `oneshop_test`, successfully including reruns. It replaces the global user/Product key with unique non-null OrderItem and unique legacy user/Product keys. The 22 tables, columns, FKs, CHECKs and seed rows remain. `database/05_verify.sql` accepts both base and migrated review uniqueness. Operational `oneshop` was only read for before/after checks; its schema and row-count/checksum snapshot is identical.

## 2. Implemented functions

| Area | Result |
|---|---|
| Customer Review eligibility | COMPLETED item Fulfillment, authenticated owner, correct Order/Product/Store source, PAID and not CANCELLED/REFUNDED; whole Order completion not required |
| Partial delivery | A COMPLETED / B SHIPPING: A reviewable, B refused; PARTIALLY_COMPLETED supported |
| Same SKU, two Stores | Separate Reviews for the two received OrderItems, with separate source/Reply scopes |
| Input/security | Rating 1..5; length/control validation in MVC and Service; optional context checked; no trusted client user/Store/Product authority |
| Duplicate/race safety | Parent Order lock + SQL UNIQUE; one committed Review per item |
| Staff queue/detail | SQL content/count filtering by verified source and current assignments; 403 cross-Store, 404 unknown id |
| Official Reply | Dedicated `review_replies`, latest authenticated Staff/content/timestamps, one row; create/edit and moderation serialize on Review lock |
| Admin | Safe all-chain reads, ACTIVE/HIDDEN moderation; Reply URL and direct Admin Reply service calls refused |
| Historical Reviews | No guessed backfill; null/mismatched source excluded from Staff authority; legacy Replies retained and readable |
| UI | Existing Thymeleaf/SiteMesh/Bootstrap; per-item Order/Product forms, Staff detail/Reply, Admin read/moderation |

A pre-existing Product Detail availability fragment dereferenced a null selected-Store offer before rendering Reviews. Its conditional was moved outside the replacement fragment. Admin Review mapping was also made null-safe without deriving the source from `Order.store`. These necessary integration fixes improve eight pre-existing failing cases.

## 3. Verification

Real SQL Server `oneshop_test`, dev profile with Hibernate `ddl-auto=validate`. Fixtures use production Checkout/Payment/Fulfillment services and committed transactions. HTTP tests use real Tomcat, JWT, CSRF, controllers, services, repositories, SiteMesh and Thymeleaf. New database suites fail if the database is not `oneshop_test` or the required migration is absent.

| BE-06 suite | PASS | Fail/Error/Skip |
|---|---:|---:|
| ReviewV3DatabaseIntegrationTest | 29 | 0 |
| ReviewV3ConcurrencyDatabaseIntegrationTest | 4 | 0 |
| ReviewV3HttpDatabaseIntegrationTest | 9 | 0 |
| service.ReviewRulesTest | 11 | 0 |
| **New BE-06 cases** | **53** | **0** |
| Existing ReviewWebIntegrationTest, updated V3 contracts | 15 | 0 |

The four races run three committed rounds each: duplicate Review, same-SKU distinct item Reviews, concurrent first Replies, and moderation vs Reply. Rollback is verified after Review insert, Reply insert and existing Reply update have actually flushed SQL.

| Full suite | BE-05 baseline reproduced | BE-06 final |
|---|---:|---:|
| Total | 1522 | **1575** |
| PASS | 1097 | **1158** |
| FAIL | 57 | 49 |
| ERROR | 326 | 326 |
| FAIL + ERROR | 383 | **375** |
| SKIP | 42 | 42 |

Per-case comparison: **0 regressions, 8 improved cases, 53 new cases all PASS, 0 removed cases**. All 42 skip identities are unchanged. The full Maven invocation still exits BUILD FAILURE because of the 375 inherited failures/errors; full-suite success is not claimed. The V3 BE-01..05 acceptance classes remain green. Details and case inventory: [TEST RESULTS](V3_BE06_TEST_RESULTS.md), [CSV comparison](V3_BE06_TEST_CASE_COMPARISON.csv).

Before/after row counts and content checksums match for all 22 tables in both databases. In `oneshop_test`, the only index/FK/CHECK snapshot difference is the approved replacement of review uniqueness. The SQL verification's 12 groups pass. No operational data writes, no test suppression and no deletion of legacy test cases.

## 4. Important implementation findings

- A first test caught Reply edits joining the Review reader's read-only transaction. The public Reply entry now explicitly starts a write transaction; actual update/rollback tests pass.
- Original V2 Review DB tests still fail at their old fixture's `DELETE FROM reviews`, blocked by `FK_review_replies_review`. Their 24 case identities remain; the fixture and obsolete product-level expectations are classified for BE-07 rather than deleting history or tests to make them pass.
- Existing Web test identities were retained; request/form assertions now identify OrderItems and Admin Reply assertions follow the owner's DEC-27 decision.

## 5. Limits and handover

OPEN-05/06 are resolved. No Review business decision remains blocked. Source-less legacy Reviews have no Staff Reply permission until an independently verified backfill is approved. No per-edit Reply revision log exists in the schema; the original legacy fields and current official Reply are preserved.

The operational database still needs the reviewed migration before deploying BE-06. Base 01..05 scripts recreate/reseed a database and are not an upgrade procedure; use the standalone migration on an existing database. No browser automation/manual browser acceptance was performed; HTTP rendering and authorization were tested. Test fixture inserts may advance identity counters; existing rows are restored without reseeding.

Full implementation inventory: [COMPONENT CHANGE MATRIX](V3_BE06_COMPONENT_CHANGE_MATRIX.md). Rules and transaction design: [AUTHORIZATION AND WORKFLOW](V3_BE06_REVIEW_AUTHORIZATION_AND_WORKFLOW.md). Deployment prerequisites and BE-07 scope: [HANDOVER](V3_BE06_HANDOVER.md).
