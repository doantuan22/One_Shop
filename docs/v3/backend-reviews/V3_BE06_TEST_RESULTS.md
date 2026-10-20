> Historical phase evidence (up to 14/10/2026). Superseded script names and readiness claims describe that run only. Current setup: [database README](../../seed/DATABASE_SETUP.md); current acceptance: [deep audit](../final-deep-audit/DEEP_AUDIT_REPORT.md).

# V3-BE-06 — TEST RESULTS

Date: 14/10/2026. SQL Server: `oneshop_test`. Operational `oneshop`: read-only snapshots; no test writes. Hibernate dev schema validation passed. The owner-approved UNIQUE migration was applied only to the test database.

## 1. Commands actually run

```powershell
# Initial BE-05 working-tree baseline, before changing BE-06 production code/migration
mvn -o -B test

# Compile after initial production refactor
mvn -o -B -DskipTests test-compile

# Domain/authorization/concurrency iterations
mvn -o -B '-Dtest=ReviewRulesTest,ReviewV3DatabaseIntegrationTest,ReviewV3ConcurrencyDatabaseIntegrationTest' test
mvn -o -B '-Dtest=ReviewRulesTest,ReviewV3DatabaseIntegrationTest,ReviewV3ConcurrencyDatabaseIntegrationTest,ReviewV3HttpDatabaseIntegrationTest,ReviewWebIntegrationTest' test

# HTTP/UI iteration after fixing the selected-Store fragment null dereference
mvn -o -B '-Dtest=ReviewV3HttpDatabaseIntegrationTest,ReviewWebIntegrationTest' test

# Final full regression, including the strengthened SQL/FK/legacy/rollback tests
mvn -o -B test
```

SQL checks: the standalone migration was run successfully including repeated execution. `05_verify.sql` was copied to a scratch file with only `USE [oneshop]` replaced by `USE [oneshop_test]` and executed with `sqlcmd -b -I -f 65001`; **12/12 verification groups passed**. No operational migration or rebuild was run. Surefire already routes the forked JVM to `oneshop_test` and disables the payment timeout worker; these settings were retained.

Session evidence is in `target/be06-evidence/` (ignored build output): baseline/final full logs, Surefire report copies, per-case JSON, and before/migrated/after snapshots. The accompanying [per-case CSV](V3_BE06_TEST_CASE_COMPARISON.csv) contains only test identities/status/classification, without environment properties or credentials.

## 2. Full baseline comparison

| Measure | BE-05 baseline | BE-06 final |
|---|---:|---:|
| Cases | 1522 | **1575** |
| PASS | 1097 | **1158** |
| FAIL | 57 | 49 |
| ERROR | 326 | 326 |
| FAIL + ERROR | 383 | **375** |
| SKIP | 42 | 42 |

Compared by `class + method/parameterized case` from Surefire XML, never by stack-frame counts:

- Passing baseline cases that no longer pass: **0**.
- Previously non-passing cases now passing: **8**.
- Added cases: **53**, all PASS, none skipped.
- Baseline cases absent from final: **0**.
- The remaining 375 failing/error identities all existed in the baseline. All 42 skipped identities are unchanged.
- Final full-suite Maven status: **BUILD FAILURE**, caused by the inherited 375 fail/error cases. It is not represented as a green full suite.

## 3. BE-06 acceptance

| Suite | Cases | Result |
|---|---:|---|
| ReviewV3DatabaseIntegrationTest | 29 | PASS |
| ReviewV3ConcurrencyDatabaseIntegrationTest | 4 | PASS (three committed rounds per race) |
| ReviewV3HttpDatabaseIntegrationTest | 9 | PASS, no business mocks |
| service.ReviewRulesTest | 11 | PASS |
| **New cases** | **53** | **PASS** |
| Existing ReviewWebIntegrationTest | 15 | PASS; V3 request/Reply contract expectations updated, case identities retained |

All 68 cases above pass in the final full run.

| Required scenario | Evidence |
|---|---|
| A COMPLETED / B SHIPPING; PARTIALLY_COMPLETED review eligibility | `completedPartIsReviewableWhileOtherPartShips`, `sameSkuReceivedLineDoesNotGrantPermissionForShippingLine`, HTTP partial Order/Product forms |
| Unreceived item refused | `noPartReceivedMeansNoFormsAndSubmissionRefused` |
| Another Customer's item / forged email / Order / Product | `customerCannotReviewAnotherCustomersItemOrForgeEmail`, `orderAndProductContextCannotBeForged`, HTTP IDOR 404 |
| Duplicate same item, including hidden original | `duplicateItemRejectedEvenAfterModerationHidesTheOriginal` |
| Two Review requests concurrently | `twoConcurrentReviewsOfOneItemCommitExactlyOneReview`, three rounds |
| Staff correct Store, dedicated Reply edit | `staffCreatesAndEditsOneDedicatedReplyWithoutEditingTheReview`, HTTP source detail/Reply |
| Cross-Store Staff reply returns 403 | `staffReplyAndDetailUseActualSourceAndCrossStoreGets403` |
| Inactive/revoked assignment with stale JWT | `revokedAssignmentBlocksReplyOnNextRequest`, `revokedAssignmentIsEnforcedWithThePreviouslyIssuedJwt` |
| Multiple Store assignments | `multipleStoreAssignmentsAllowOnlyThoseStores`; inactive Store/account also tested |
| Two Staff Replies concurrently | `twoStaffRepliesSerializeIntoOneOfficialRow`, three rounds; one PK/official row |
| Same SKU, two Stores | `sameSkuFromTwoCompletedStoresHasTwoDistinctReviewsAndReplyScopes`, concurrent distinct-line test |
| Historical Review missing source | `legacyWithoutOrderItemIsVisibleToAdminAndPublicButNotStaff`, matching HTTP test |
| Admin moderation / ownership / no Admin Reply | `adminModerationPreservesReplyAndCannotReplyEvenThroughService`, HTTP Admin-only moderation |
| Rating/content / FKs / UNIQUE / CHECK / rollback | Parameterized rating and content boundary tests; raw SQL constraint tests; rollback after Review insert, Reply insert and Reply edit flush |
| Inconsistent Order/Fulfillment/Store/Product/User provenance | `sourceMismatchCannotGrantReplyAuthorityOrAcceptCustomerSubmission`, `aFulfillmentFromAnotherOrderCannotBeUsedAsThePurchasedSource`, `inconsistentHistoricalUserOrProductNeverGrantsStaffScope` |
| Historical Reply preserved alongside official Reply | `editingAProvenLegacyReviewPreservesItsOriginalReplyColumns` |
| Moderation vs Reply | `moderationAndReplyShareTheReviewLock`, three rounds |
| CSRF, including Bearer forms and forged Staff fields | HTTP CSRF test, Staff rating/content unchanged assertion, existing Web security tests |

## 4. BE-01..05 regression, in the final full suite

| Phase acceptance group | Cases | Fail/Error/Skip |
|---|---:|---:|
| BE-01: JpaV3ReadIntegrityTest | 9 | 0 |
| BE-02: CentralCartIntegrationTest + CartServiceV3Test + ProductServiceV3Test | 28 | 0 |
| BE-03: AllocationEngine, ShippingAddressValidator, PaymentDeadlinePolicy, CheckoutV3 DB/split/fee/gates/concurrency/HTTP + CheckoutWeb | 119 | 0 |
| BE-04: FulfillmentOperations/Concurrency/HTTP, StaffFulfillmentWeb, OrderStatusAggregator, PickupAttemptLimiter | 120 | 0 |
| BE-05: PaymentSettlement/Concurrency/HTTP, GatewayDisabled, MockPaymentGateway | 67 | 0 |

These 343 V3 acceptance cases all pass. No Checkout/Allocation/Payment/Refund/Fulfillment implementation was rewritten.

## 5. Eight improved baseline cases

| Case | Integration fix |
|---|---|
| CatalogHttpDatabaseIntegrationTest.clientPagesNeverShowTheExactQuantity | Null-safe Product Detail availability fragment |
| StaffInventoryDatabaseIntegrationTest.guestsAndCustomerCatalogCannotReadExactInventory | Same Product Detail fix |
| ReviewWebIntegrationTest.productPageWithNoReviewsSaysSo | Same Product Detail fix |
| ReviewWebIntegrationTest.reviewFormAppearsOnlyWhenTheServiceSaysTheCustomerCanReview | Product Detail fix plus explicit per-item form contract |
| ReviewWebIntegrationTest.productPageListsVisibleReviewsAndEscapesTheirContent | Same Product Detail fix |
| Phase11AdminDatabaseIntegrationTest.orderStockUserAssignmentReviewPaginationKeepsFiltersAndStableTotals | Safe Admin Review source mapping |
| Phase11AdminDatabaseIntegrationTest.adminGetsEveryPageAndFormsUseAdminLayoutCsrf | Same Admin Review mapping fix |
| Phase11AdminDatabaseIntegrationTest.reviewManagementOnlyChangesVisibilityAndEscapesContent | Same Admin Review mapping fix |

## 6. Legacy failures and changed tests

`ReviewDatabaseIntegrationTest` retains **24 ERROR cases**. Every case fails before its assertion in the existing V2 fixture's `DELETE FROM dbo.reviews`, refused by `FK_review_replies_review`. The baseline had the same errors. Removing historical Reply rows to unblock that fixture was not done. Several assertions also encode retired rules (one Review per Product, whole-Order COMPLETED, Order.Store source); their rebuild belongs to BE-07. The case CSV preserves every identity.

Other remaining cases are the baseline V2 fixture/schema/cart/order-state incompatibilities documented by earlier phases. No new failing identity or newly skipped case was introduced. The old `ReviewWebIntegrationTest` keeps all 15 case identities; form bodies/DTO expectations now include OrderItem, and the former Admin Reply success expectation is deliberately changed to 403 for the owner-approved DEC-27. This is explicitly recorded rather than treating obsolete expectations as the current contract.

## 7. Data and schema safety

- All **22 tables** have identical before/after row counts and content checksums in both `oneshop` and `oneshop_test`.
- Operational `oneshop` index/FK/CHECK and data-checksum snapshot is also byte-for-byte identical.
- In `oneshop_test`, only review uniqueness changed: old user/Product key replaced by the two approved filtered unique indexes. FKs and CHECKs remain trusted/enabled; all other indexes are unchanged.
- New fixtures delete only their own new Review/Reply rows before removing their own Order fixtures; assignment/account changes are restored. Existing seed data remains. No identity reseeding is attempted; generated counters can advance.
- The test suites use real committed races and actual flushed SQL rollback, not sleeps or mocked business outcomes.

## 8. Limits

No browser automation or manual browser acceptance, no load/soak test, and no per-edit Reply revision log were added. OPEN-05/06 are decided. The standalone operational migration remains a deployment prerequisite; its execution is outside these test runs. BE-07 and ONLINE checkout activation are not part of this phase.
