# Full regression report

## Verdict

Final full-suite process completed on isolated SQL Server database `oneshop_fix_full_20261024_test`. Maven/Surefire reports **2,517 invocations, 8 failures, 10 errors** and **BUILD FAILURE** in `target/qa-fix/full-suite-final.log`.

The generated `evidence/full-suite-final/results.json` says **430/430 PASS**, but the runner's result aggregation omits failing parameterized/dynamic invocations. It is not a valid suite verdict; Maven/Surefire log and reports are authoritative. Full-suite sign-off is therefore **NOT CLEAN**.

## Eight assertion failures

- `CatalogDatabaseIntegrationTest.tc17_productImageGoesToCloudinaryAndTheDatabaseKeepsOnlyUrlAndPublicId` and `tc17_brandLogoIsStoredAsUrlAndPublicIdAndTheOldFileIsRemoved`: expectations require synchronous external delete before transaction completion; implementation defers deletion until commit.
- `CatalogDatabaseIntegrationTest.cloudinaryRefusingADeleteKeepsTheImageRow`: expects synchronous delete failure to preserve metadata row rather than cleanup journal/retry behavior.
- `CatalogHttpDatabaseIntegrationTest.imageUploadReachesCloudinaryServiceThroughTheMultipartFormWithCsrf`: old mock expects a direct Cloudinary service call instead of the transaction coordinator.
- `CatalogHttpDatabaseIntegrationTest.tc18_inactiveStoreIsShownAsNotOperatingAndCannotBeChosen` and `aStoreCookieDoesNotMarkAnythingInTheFinder`: legacy Store finder assertions; finder was intentionally retained pending approved spec.
- `CategoryAdminSecurityWebIntegrationTest.adminCanAccessCategoriesListAndRendersTableAndUploadControls`: stale literal UI text assertion.
- `Phase11AdminDatabaseIntegrationTest.multipleAssignmentsAndStoreChangeDoNotTrustBrowserSelector`: expects 302 while runtime returns 200 validation response; existing fixture/spec discrepancy.

These are real test failures, not passes. Their status as production defects is not assumed; old contract/spec expectations require review and focused repairs without weakening requirements.

## Ten constraint/fixture errors

Each attempts multiple ACTIVE Store assignments for one Staff account, rejected by `UX_ssa_one_active_store_per_user`:

1. `DeliveryDatabaseIntegrationTest.readScopeSupportsMultipleAssignmentsAndOnlyDeliverySnapshots`
2. `FulfillmentOperationsDatabaseIntegrationTest.be04_08_twoAssignments`
3. `Phase10IntegrationWebDatabaseTest.revokingOneOfMultipleAssignmentsImmediatelyRemovesPeerReadsAndWritesAcrossAllModules`
4. `Phase9IntegrationWebDatabaseTest.multipleAssignmentsAllowOnlyActiveStoreAndUseAuthenticatedActor`
5. `ReviewV3ConcurrencyDatabaseIntegrationTest.twoStaffRepliesSerializeIntoOneOfficialRow`
6. `ReviewV3DatabaseIntegrationTest.multipleStoreAssignmentsAllowOnlyThoseStores`
7. `StaffFulfillmentOperationsDatabaseIntegrationTest.multipleAssignmentsAllowPeerPickupThenRevocationBlocksTheNextMutationImmediately`
8. `StaffInventoryDatabaseIntegrationTest.multipleActiveAssignmentsAllowOnlyTheirStockAndRevocationBlocksFurtherWrites`
9. `StaffOperationsDatabaseIntegrationTest.multipleAssignmentsKeepPerStoreCountsAndRevocationImmediatelyRemovesPeerResources`
10. `StaffStoreScopeDatabaseIntegrationTest.multipleAssignmentsPreserveOnlyActiveScopeAndResolveFreshAfterRevocation`

This is the previously documented SC-001 fixture/spec conflict. Do not relax the one-active-Store policy, unique constraint, or authorization to satisfy these tests.

## QA-targeted evidence

All focused regression phases passed: QA-001 12/12; QA-002 race suite 39/39; QA-003/004 42/42; QA-005 667/667; QA-006 424/424; QA-007/008 browser 2/2. See `BUG_REGRESSION_RESULTS.md` and per-phase JSON. The earlier 429/430 run had only a stale escaped-title screen assertion, which was updated; the final run now exposes the unrelated failures above.

Cloudinary live behavior is not covered: image lifecycle integration uses a storage double. No production DB was modified.
