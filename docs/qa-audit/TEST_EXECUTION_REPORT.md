# Test execution report

## Environment

2026-10-24, Windows, JDK 21.0.12.1, Spring Boot 3.5.16, SQL Server 2025 17.0.1145.4. Database riêng `oneshop_qa_20261024_test`; Hibernate validate; timeout scheduler OFF. Embedded Tomcat thật trên random ports; Chrome headless qua CDP, profile tạm riêng. Không gọi payment/refund ngân hàng thật. Cloudinary write chỉ qua test double.

## Primary runs (không cộng rerun vào unique coverage)

| Phase | Total | PASS | FAIL | ERROR | SKIPPED |
|---|---|---|---|---|---|
| baseline | 1674 | 1656 | 5 | 10 | 3 |
| additional | 551 | 484 | 67 | 0 | 0 |

ERROR ở baseline do fixture vi phạm one-active-store constraint: phân loại BLOCKED cho scenario đó, không tự coi là bug sản phẩm. FAIL khác phải đọc BUG_REPORT và HARNESS_NOTES. Tất cả test asserts vẫn được giữ nguyên.

## Test input và expected

Test ID dưới đây ánh xạ chính xác suite/method và chỉ số parameter trong Surefire. Input/expected assertion đầy đủ nằm trong source test tương ứng; bảng không suy diễn input từ tên test. Các test bổ sung có input/expected/actual HTTP trong `evidence/boundaries/`, `evidence/routes/`; race có before/after SQL trong `evidence/races/`; rollback trong `evidence/image-rollback/`.

| ID | Suite / Scenario | Status | Seconds | Executed (suite start) | Evidence |
|---|---|---|---|---|---|
| baseline-0001 | com.oneshop.AccessControlIntegrationTest#staffWithoutActiveAssignmentGetsNoStoreData | PASS | 1.689 | None | target/surefire-reports/TEST-com.oneshop.AccessControlIntegrationTest.xml |
| baseline-0002 | com.oneshop.AccessControlIntegrationTest#publicPagesNeedNoLogin | PASS | 0.5 | None | target/surefire-reports/TEST-com.oneshop.AccessControlIntegrationTest.xml |
| baseline-0003 | com.oneshop.AccessControlIntegrationTest#adminReachesTheAdminAreaOnly | PASS | 0.407 | None | target/surefire-reports/TEST-com.oneshop.AccessControlIntegrationTest.xml |
| baseline-0004 | com.oneshop.AccessControlIntegrationTest#jwtCookieIsHttpOnlyAndNeverExposedInThePage | PASS | 0.622 | None | target/surefire-reports/TEST-com.oneshop.AccessControlIntegrationTest.xml |
| baseline-0005 | com.oneshop.AccessControlIntegrationTest#passwordLongerThan72BytesIsAFieldErrorNotA500 | PASS | 0.518 | None | target/surefire-reports/TEST-com.oneshop.AccessControlIntegrationTest.xml |
| baseline-0006 | com.oneshop.AccessControlIntegrationTest#stateChangingRequestWithTheJwtCookieButNoCsrfTokenIsRejected | PASS | 0.334 | None | target/surefire-reports/TEST-com.oneshop.AccessControlIntegrationTest.xml |
| baseline-0007 | com.oneshop.AccessControlIntegrationTest#assignedStaffPassesTheScopeCheckOnInnerPages | PASS | 0.318 | None | target/surefire-reports/TEST-com.oneshop.AccessControlIntegrationTest.xml |
| baseline-0008 | com.oneshop.AccessControlIntegrationTest#forbiddenPagesRenderTheErrorPageNotTheProtectedContent | PASS | 0.362 | None | target/surefire-reports/TEST-com.oneshop.AccessControlIntegrationTest.xml |
| baseline-0009 | com.oneshop.AccessControlIntegrationTest#registrationRejectsATakenOrMalformedUserName | PASS | 0.395 | None | target/surefire-reports/TEST-com.oneshop.AccessControlIntegrationTest.xml |
| baseline-0010 | com.oneshop.AccessControlIntegrationTest#customerReachesTheClientAreaOnly | PASS | 0.397 | None | target/surefire-reports/TEST-com.oneshop.AccessControlIntegrationTest.xml |
| baseline-0011 | com.oneshop.AccessControlIntegrationTest#staffCannotChooseTheirStoreThroughTheRequest | PASS | 0.363 | None | target/surefire-reports/TEST-com.oneshop.AccessControlIntegrationTest.xml |
| baseline-0012 | com.oneshop.AccessControlIntegrationTest#webLoginSendsEachRoleToItsArea | PASS | 0.592 | None | target/surefire-reports/TEST-com.oneshop.AccessControlIntegrationTest.xml |
| baseline-0013 | com.oneshop.AccessControlIntegrationTest#csrfTokenOfALoggedInUserStaysValidAcrossPagesAndSubmits | PASS | 0.419 | None | target/surefire-reports/TEST-com.oneshop.AccessControlIntegrationTest.xml |
| baseline-0014 | com.oneshop.AccessControlIntegrationTest#guestsAreSentToLoginForEveryProtectedArea | PASS | 0.359 | None | target/surefire-reports/TEST-com.oneshop.AccessControlIntegrationTest.xml |
| baseline-0015 | com.oneshop.AccessControlIntegrationTest#decoratorsCannotBeRequestedDirectlyEvenByAdmin | PASS | 0.319 | None | target/surefire-reports/TEST-com.oneshop.AccessControlIntegrationTest.xml |
| baseline-0016 | com.oneshop.AccessControlIntegrationTest#staffReachesTheStaffAreaOnly | PASS | 0.362 | None | target/surefire-reports/TEST-com.oneshop.AccessControlIntegrationTest.xml |
| baseline-0017 | com.oneshop.AccessControlIntegrationTest#logoutNeedsPostAndClearsTheJwtCookie | PASS | 0.375 | None | target/surefire-reports/TEST-com.oneshop.AccessControlIntegrationTest.xml |
| baseline-0018 | com.oneshop.AccessControlIntegrationTest#registrationRejectsATakenEmailAndWeakInput | PASS | 0.378 | None | target/surefire-reports/TEST-com.oneshop.AccessControlIntegrationTest.xml |
| baseline-0019 | com.oneshop.AccessControlIntegrationTest#roleInsideTheTokenIsNotTrusted | PASS | 0.399 | None | target/surefire-reports/TEST-com.oneshop.AccessControlIntegrationTest.xml |
| baseline-0020 | com.oneshop.AccessControlIntegrationTest#disabledAccountLosesAccessEvenWithAStillValidToken | PASS | 0.387 | None | target/surefire-reports/TEST-com.oneshop.AccessControlIntegrationTest.xml |
| baseline-0021 | com.oneshop.AccessControlIntegrationTest#assignedStaffSeesTheirStore | PASS | 0.321 | None | target/surefire-reports/TEST-com.oneshop.AccessControlIntegrationTest.xml |
| baseline-0022 | com.oneshop.AccessControlIntegrationTest#registrationAlwaysCreatesACustomerWithAHashedPassword | PASS | 0.474 | None | target/surefire-reports/TEST-com.oneshop.AccessControlIntegrationTest.xml |
| baseline-0023 | com.oneshop.AdminAttentionAndPaymentCasesDatabaseIntegrationTest#search | PASS | 0.939 | None | target/surefire-reports/TEST-com.oneshop.AdminAttentionAndPaymentCasesDatabaseIntegrationTest.xml |
| baseline-0024 | com.oneshop.AdminAttentionAndPaymentCasesDatabaseIntegrationTest#readsChangeNothing | PASS | 0.454 | None | target/surefire-reports/TEST-com.oneshop.AdminAttentionAndPaymentCasesDatabaseIntegrationTest.xml |
| baseline-0025 | com.oneshop.AdminAttentionAndPaymentCasesDatabaseIntegrationTest#dashboardMatchesTheConsole | PASS | 0.457 | None | target/surefire-reports/TEST-com.oneshop.AdminAttentionAndPaymentCasesDatabaseIntegrationTest.xml |
| baseline-0026 | com.oneshop.AdminAttentionAndPaymentCasesDatabaseIntegrationTest#onlyAdmins | PASS | 0.049 | None | target/surefire-reports/TEST-com.oneshop.AdminAttentionAndPaymentCasesDatabaseIntegrationTest.xml |
| baseline-0027 | com.oneshop.AdminAttentionAndPaymentCasesDatabaseIntegrationTest#insideTheDeadline | PASS | 0.261 | None | target/surefire-reports/TEST-com.oneshop.AdminAttentionAndPaymentCasesDatabaseIntegrationTest.xml |
| baseline-0028 | com.oneshop.AdminAttentionAndPaymentCasesDatabaseIntegrationTest#filtersListsAndCountsAgree | PASS | 0.596 | None | target/surefire-reports/TEST-com.oneshop.AdminAttentionAndPaymentCasesDatabaseIntegrationTest.xml |
| baseline-0029 | com.oneshop.AdminAttentionAndPaymentCasesDatabaseIntegrationTest#openRefund | PASS | 0.181 | None | target/surefire-reports/TEST-com.oneshop.AdminAttentionAndPaymentCasesDatabaseIntegrationTest.xml |
| baseline-0030 | com.oneshop.AdminAttentionAndPaymentCasesDatabaseIntegrationTest#pastTheDeadline | PASS | 0.19 | None | target/surefire-reports/TEST-com.oneshop.AdminAttentionAndPaymentCasesDatabaseIntegrationTest.xml |
| baseline-0031 | com.oneshop.AdminAttentionAndPaymentCasesDatabaseIntegrationTest#normalStaffWorkIsNotAdminWork | PASS | 0.216 | None | target/surefire-reports/TEST-com.oneshop.AdminAttentionAndPaymentCasesDatabaseIntegrationTest.xml |
| baseline-0032 | com.oneshop.AdminAttentionAndPaymentCasesDatabaseIntegrationTest#needsAttention | PASS | 0.213 | None | target/surefire-reports/TEST-com.oneshop.AdminAttentionAndPaymentCasesDatabaseIntegrationTest.xml |
| baseline-0033 | com.oneshop.AdminAttentionAndPaymentCasesDatabaseIntegrationTest#finalStates | PASS | 0.317 | None | target/surefire-reports/TEST-com.oneshop.AdminAttentionAndPaymentCasesDatabaseIntegrationTest.xml |
| baseline-0034 | com.oneshop.ApiInputValidationTest#malformedJsonIsAClientErrorWithNoParserLeak | PASS | 0.131 | None | target/surefire-reports/TEST-com.oneshop.ApiInputValidationTest.xml |
| baseline-0035 | com.oneshop.ApiInputValidationTest#unsupportedContentTypeKeepsItsHttpStatus | PASS | 0.009 | None | target/surefire-reports/TEST-com.oneshop.ApiInputValidationTest.xml |
| baseline-0036 | com.oneshop.AuthDatabaseIntegrationTest#staffScopeIsExactlyTheStoreOfTheActiveAssignment | PASS | 0.031 | None | target/surefire-reports/TEST-com.oneshop.AuthDatabaseIntegrationTest.xml |
| baseline-0037 | com.oneshop.AuthDatabaseIntegrationTest#registrationStoresACustomerThatCanLogIn | PASS | 0.326 | None | target/surefire-reports/TEST-com.oneshop.AuthDatabaseIntegrationTest.xml |
| baseline-0038 | com.oneshop.AuthDatabaseIntegrationTest#passwordIsLimitedToBcrypt72BytesNotCharacters | PASS | 0.173 | None | target/surefire-reports/TEST-com.oneshop.AuthDatabaseIntegrationTest.xml |
| baseline-0039 | com.oneshop.AuthDatabaseIntegrationTest#inactiveAssignmentGivesNoStoreScope | PASS | 0.016 | None | target/surefire-reports/TEST-com.oneshop.AuthDatabaseIntegrationTest.xml |
| baseline-0040 | com.oneshop.AuthDatabaseIntegrationTest#inactiveAccountCannotLogIn | PASS | 0.135 | None | target/surefire-reports/TEST-com.oneshop.AuthDatabaseIntegrationTest.xml |
| baseline-0041 | com.oneshop.AuthDatabaseIntegrationTest#seedAccountsLogInWithTheirRoleFromTheDatabase | PASS | 0.26 | None | target/surefire-reports/TEST-com.oneshop.AuthDatabaseIntegrationTest.xml |
| baseline-0042 | com.oneshop.AuthDatabaseIntegrationTest#inactiveStoreOrInactiveStaffAccountGivesNoStoreScope | PASS | 0.017 | None | target/surefire-reports/TEST-com.oneshop.AuthDatabaseIntegrationTest.xml |
| baseline-0043 | com.oneshop.AuthDatabaseIntegrationTest#wrongUserNameOrPasswordGivesTheSameBadCredentials | PASS | 0.171 | None | target/surefire-reports/TEST-com.oneshop.AuthDatabaseIntegrationTest.xml |
| baseline-0044 | com.oneshop.AuthDatabaseIntegrationTest#wrongPasswordAndUnknownAccountAreRejectedTheSameWay | PASS | 0.166 | None | target/surefire-reports/TEST-com.oneshop.AuthDatabaseIntegrationTest.xml |
| baseline-0045 | com.oneshop.AuthDatabaseIntegrationTest#accountsThatAreNotStaffHaveNoStoreScope | PASS | 0.016 | None | target/surefire-reports/TEST-com.oneshop.AuthDatabaseIntegrationTest.xml |
| baseline-0046 | com.oneshop.AuthDatabaseIntegrationTest#seedAccountsLogInByUserNameIgnoringCase | PASS | 0.237 | None | target/surefire-reports/TEST-com.oneshop.AuthDatabaseIntegrationTest.xml |
| baseline-0047 | com.oneshop.AuthFlowIntegrationTest#webLoginSetsHttpOnlyJwtCookieAndCookieAuthenticatesPages | PASS | 0.186 | None | target/surefire-reports/TEST-com.oneshop.AuthFlowIntegrationTest.xml |
| baseline-0048 | com.oneshop.AuthFlowIntegrationTest#apiLoginRejectsWrongPassword | PASS | 0.157 | None | target/surefire-reports/TEST-com.oneshop.AuthFlowIntegrationTest.xml |
| baseline-0049 | com.oneshop.AuthFlowIntegrationTest#apiLoginWithAnOverlongPasswordIsRejectedNotA500 | PASS | 0.156 | None | target/surefire-reports/TEST-com.oneshop.AuthFlowIntegrationTest.xml |
| baseline-0050 | com.oneshop.AuthFlowIntegrationTest#apiLoginReturnsBearerTokenThatAuthenticatesLaterRequests | PASS | 0.167 | None | target/surefire-reports/TEST-com.oneshop.AuthFlowIntegrationTest.xml |
| baseline-0051 | com.oneshop.AuthFlowIntegrationTest#tamperedTokenIsIgnored | PASS | 0.1 | None | target/surefire-reports/TEST-com.oneshop.AuthFlowIntegrationTest.xml |
| baseline-0052 | com.oneshop.AuthFlowIntegrationTest#apiRegisterWithMoreThan72BytesIsA400WithAPasswordFieldError | PASS | 0.091 | None | target/surefire-reports/TEST-com.oneshop.AuthFlowIntegrationTest.xml |
| baseline-0053 | com.oneshop.AuthFlowIntegrationTest#webLoginWithoutCsrfTokenIsRejected | PASS | 0.08 | None | target/surefire-reports/TEST-com.oneshop.AuthFlowIntegrationTest.xml |
| baseline-0054 | com.oneshop.AuthFlowIntegrationTest#apiLoginValidatesInput | PASS | 0.08 | None | target/surefire-reports/TEST-com.oneshop.AuthFlowIntegrationTest.xml |
| baseline-0055 | com.oneshop.BrowserV3DatabaseIntegrationTest#actualBrowserBusinessFlow(String)[1] | PASS | 10.36 | None | target/surefire-reports/TEST-com.oneshop.BrowserV3DatabaseIntegrationTest.xml |
| baseline-0056 | com.oneshop.BrowserV3DatabaseIntegrationTest#actualBrowserBusinessFlow(String)[2] | PASS | 11.138 | None | target/surefire-reports/TEST-com.oneshop.BrowserV3DatabaseIntegrationTest.xml |
| baseline-0057 | com.oneshop.BrowserV3DatabaseIntegrationTest#actualBrowserBusinessFlow(String)[3] | PASS | 16.316 | None | target/surefire-reports/TEST-com.oneshop.BrowserV3DatabaseIntegrationTest.xml |
| baseline-0058 | com.oneshop.BrowserV3DatabaseIntegrationTest#actualBrowserBusinessFlow(String)[4] | PASS | 18.62 | None | target/surefire-reports/TEST-com.oneshop.BrowserV3DatabaseIntegrationTest.xml |
| baseline-0059 | com.oneshop.BrowserV3DatabaseIntegrationTest#actualBrowserBusinessFlow(String)[5] | PASS | 11.993 | None | target/surefire-reports/TEST-com.oneshop.BrowserV3DatabaseIntegrationTest.xml |
| baseline-0060 | com.oneshop.BrowserV3DatabaseIntegrationTest#actualBrowserBusinessFlow(String)[6] | PASS | 13.125 | None | target/surefire-reports/TEST-com.oneshop.BrowserV3DatabaseIntegrationTest.xml |
| baseline-0061 | com.oneshop.BrowserV3DatabaseIntegrationTest#actualBrowserBusinessFlow(String)[7] | PASS | 27.961 | None | target/surefire-reports/TEST-com.oneshop.BrowserV3DatabaseIntegrationTest.xml |
| baseline-0062 | com.oneshop.CartDatabaseIntegrationTest#theCartIsCreatedOnTheFirstAddAndThereIsOnlyEverOne | PASS | 0.103 | None | target/surefire-reports/TEST-com.oneshop.CartDatabaseIntegrationTest.xml |
| baseline-0063 | com.oneshop.CartDatabaseIntegrationTest#aCustomerCannotTouchAnotherCustomersLines | PASS | 0.109 | None | target/surefire-reports/TEST-com.oneshop.CartDatabaseIntegrationTest.xml |
| baseline-0064 | com.oneshop.CartDatabaseIntegrationTest#changingTheSelectedStoreNeverChangesTheCart | PASS | 0.285 | None | target/surefire-reports/TEST-com.oneshop.CartDatabaseIntegrationTest.xml |
| baseline-0065 | com.oneshop.CartDatabaseIntegrationTest#sameSkuAtTwoStoresIsTwoIndependentLines | PASS | 0.095 | None | target/surefire-reports/TEST-com.oneshop.CartDatabaseIntegrationTest.xml |
| baseline-0066 | com.oneshop.CartDatabaseIntegrationTest#tc05_cartWithLinesOfThreeStoresIsShownAsThreeGroups | PASS | 0.131 | None | target/surefire-reports/TEST-com.oneshop.CartDatabaseIntegrationTest.xml |
| baseline-0067 | com.oneshop.CartDatabaseIntegrationTest#cartRequestsCarryNoUserPriceOrStoreAndCartViewsCarryNoStock | PASS | 0.054 | None | target/surefire-reports/TEST-com.oneshop.CartDatabaseIntegrationTest.xml |
| baseline-0068 | com.oneshop.CartDatabaseIntegrationTest#quantityMustBeAtLeastOne | PASS | 0.103 | None | target/surefire-reports/TEST-com.oneshop.CartDatabaseIntegrationTest.xml |
| baseline-0069 | com.oneshop.CartDatabaseIntegrationTest#databaseRejectsANonPositiveQuantity | PASS | 0.083 | None | target/surefire-reports/TEST-com.oneshop.CartDatabaseIntegrationTest.xml |
| baseline-0070 | com.oneshop.CartDatabaseIntegrationTest#selectionReturnsExactlyTheChosenLinesGroupedByStore | PASS | 0.126 | None | target/surefire-reports/TEST-com.oneshop.CartDatabaseIntegrationTest.xml |
| baseline-0071 | com.oneshop.CartDatabaseIntegrationTest#databaseRejectsASecondLineForTheSameStoreProductInOneCart | PASS | 0.095 | None | target/surefire-reports/TEST-com.oneshop.CartDatabaseIntegrationTest.xml |
| baseline-0072 | com.oneshop.CartDatabaseIntegrationTest#updateSetsTheQuantityAfterCheckingStock | PASS | 0.1 | None | target/surefire-reports/TEST-com.oneshop.CartDatabaseIntegrationTest.xml |
| baseline-0073 | com.oneshop.CartDatabaseIntegrationTest#updateReadsTheLatestStockNotTheOneThePageWasRenderedWith | PASS | 0.091 | None | target/surefire-reports/TEST-com.oneshop.CartDatabaseIntegrationTest.xml |
| baseline-0074 | com.oneshop.CartDatabaseIntegrationTest#whatIsNotOnSaleCannotBeAdded | PASS | 0.095 | None | target/surefire-reports/TEST-com.oneshop.CartDatabaseIntegrationTest.xml |
| baseline-0075 | com.oneshop.CartDatabaseIntegrationTest#tc05_aCustomerBuildsACartAcrossThreeStores | PASS | 0.123 | None | target/surefire-reports/TEST-com.oneshop.CartDatabaseIntegrationTest.xml |
| baseline-0076 | com.oneshop.CartDatabaseIntegrationTest#whatIsAlreadyInTheCartCountsTowardsTheStockCheck | PASS | 0.094 | None | target/surefire-reports/TEST-com.oneshop.CartDatabaseIntegrationTest.xml |
| baseline-0077 | com.oneshop.CartDatabaseIntegrationTest#tc04_addingTheSameStoreProductTwiceGivesOneLineWithTheSummedQuantity | PASS | 0.089 | None | target/surefire-reports/TEST-com.oneshop.CartDatabaseIntegrationTest.xml |
| baseline-0078 | com.oneshop.CartDatabaseIntegrationTest#lineWhoseStoreProductIsNoLongerOnSaleStaysButCanOnlyBeRemoved | PASS | 0.093 | None | target/surefire-reports/TEST-com.oneshop.CartDatabaseIntegrationTest.xml |
| baseline-0079 | com.oneshop.CartDatabaseIntegrationTest#priceShownIsTheCurrentStoreProductPriceNotASnapshot | PASS | 0.09 | None | target/surefire-reports/TEST-com.oneshop.CartDatabaseIntegrationTest.xml |
| baseline-0080 | com.oneshop.CartDatabaseIntegrationTest#cartOperationsNeverChangeStockOrCreateOrdersOrMovements | PASS | 0.086 | None | target/surefire-reports/TEST-com.oneshop.CartDatabaseIntegrationTest.xml |
| baseline-0081 | com.oneshop.CartDatabaseIntegrationTest#removeDeletesOnlyThatLine | PASS | 0.118 | None | target/surefire-reports/TEST-com.oneshop.CartDatabaseIntegrationTest.xml |
| baseline-0082 | com.oneshop.CartDatabaseIntegrationTest#stockDroppingBelowTheCartQuantityIsFlaggedButTheLineIsNotChanged | PASS | 0.085 | None | target/surefire-reports/TEST-com.oneshop.CartDatabaseIntegrationTest.xml |
| baseline-0083 | com.oneshop.CartDatabaseIntegrationTest#sellabilityFollowsTheCatalogConvention | PASS | 0.094 | None | target/surefire-reports/TEST-com.oneshop.CartDatabaseIntegrationTest.xml |
| baseline-0084 | com.oneshop.CartDatabaseIntegrationTest#addingMoreThanTheStoreHasIsRefusedAndChangesNothing | PASS | 0.089 | None | target/surefire-reports/TEST-com.oneshop.CartDatabaseIntegrationTest.xml |
| baseline-0085 | com.oneshop.CartHttpDatabaseIntegrationTest#userPriceAndStoreSentByTheClientAreIgnored | PASS | 0.208 | None | target/surefire-reports/TEST-com.oneshop.CartHttpDatabaseIntegrationTest.xml |
| baseline-0086 | com.oneshop.CartHttpDatabaseIntegrationTest#tc05_cartPageShowsOneGroupPerStoreWithItsOwnLines | PASS | 0.147 | None | target/surefire-reports/TEST-com.oneshop.CartHttpDatabaseIntegrationTest.xml |
| baseline-0087 | com.oneshop.CartHttpDatabaseIntegrationTest#aCustomerCannotChangeAnotherCustomersLineByItsId | PASS | 0.237 | None | target/surefire-reports/TEST-com.oneshop.CartHttpDatabaseIntegrationTest.xml |
| baseline-0088 | com.oneshop.CartHttpDatabaseIntegrationTest#cartWritesNeedACsrfToken | PASS | 0.159 | None | target/surefire-reports/TEST-com.oneshop.CartHttpDatabaseIntegrationTest.xml |
| baseline-0089 | com.oneshop.CartHttpDatabaseIntegrationTest#staffAndAdminHaveNoCart | PASS | 0.21 | None | target/surefire-reports/TEST-com.oneshop.CartHttpDatabaseIntegrationTest.xml |
| baseline-0090 | com.oneshop.CartHttpDatabaseIntegrationTest#updateAndRemoveThroughTheCartForms | PASS | 0.207 | None | target/surefire-reports/TEST-com.oneshop.CartHttpDatabaseIntegrationTest.xml |
| baseline-0091 | com.oneshop.CartHttpDatabaseIntegrationTest#guestsAreSentToLoginAndCannotChangeAnything | PASS | 0.091 | None | target/surefire-reports/TEST-com.oneshop.CartHttpDatabaseIntegrationTest.xml |
| baseline-0092 | com.oneshop.CartHttpDatabaseIntegrationTest#tc04_addingTheSameStoreProductTwiceShowsOneLineWithTheSummedQuantity | PASS | 0.218 | None | target/surefire-reports/TEST-com.oneshop.CartHttpDatabaseIntegrationTest.xml |
| baseline-0093 | com.oneshop.CartHttpDatabaseIntegrationTest#sameProductAtTwoStoresIsTwoLinesInTwoGroups | PASS | 0.151 | None | target/surefire-reports/TEST-com.oneshop.CartHttpDatabaseIntegrationTest.xml |
| baseline-0094 | com.oneshop.CartHttpDatabaseIntegrationTest#selectedCartItemIdsOpenTheCheckoutPageGroupedByStoreAndNothingIsCreated | PASS | 0.119 | None | target/surefire-reports/TEST-com.oneshop.CartHttpDatabaseIntegrationTest.xml |
| baseline-0095 | com.oneshop.CartHttpDatabaseIntegrationTest#addingToTheCartDoesNotChangeStockAndTheCartPageDoesNotShowIt | PASS | 0.155 | None | target/surefire-reports/TEST-com.oneshop.CartHttpDatabaseIntegrationTest.xml |
| baseline-0096 | com.oneshop.CartHttpDatabaseIntegrationTest#addIsRefusedForWhatCannotBeBoughtAndTheReasonIsShown | PASS | 0.146 | None | target/surefire-reports/TEST-com.oneshop.CartHttpDatabaseIntegrationTest.xml |
| baseline-0097 | com.oneshop.CartHttpDatabaseIntegrationTest#productDetailOffersAddToCartOnlyForAConcreteStoreProduct | PASS | 0.142 | None | target/surefire-reports/TEST-com.oneshop.CartHttpDatabaseIntegrationTest.xml |
| baseline-0098 | com.oneshop.CartHttpDatabaseIntegrationTest#changingTheSelectedStoreLeavesTheCartExactlyAsItWas | PASS | 0.189 | None | target/surefire-reports/TEST-com.oneshop.CartHttpDatabaseIntegrationTest.xml |
| baseline-0099 | com.oneshop.CartHttpDatabaseIntegrationTest#tc04_twoSimultaneousAddsOfTheSameStoreProductStillGiveOneLine | PASS | 0.158 | None | target/surefire-reports/TEST-com.oneshop.CartHttpDatabaseIntegrationTest.xml |
| baseline-0100 | com.oneshop.CartWebIntegrationTest#refusalsOfTheServiceAreShownOnTheCartPage | PASS | 0.041 | None | target/surefire-reports/TEST-com.oneshop.CartWebIntegrationTest.xml |
| baseline-0101 | com.oneshop.CartWebIntegrationTest#invalidInputNeverReachesTheService | PASS | 0.105 | None | target/surefire-reports/TEST-com.oneshop.CartWebIntegrationTest.xml |
| baseline-0102 | com.oneshop.CartWebIntegrationTest#onlyCustomersHaveACart | PASS | 0.069 | None | target/surefire-reports/TEST-com.oneshop.CartWebIntegrationTest.xml |
| baseline-0103 | com.oneshop.CartWebIntegrationTest#cartPageRendersGroupsCheckboxesAndItemStates | PASS | 0.024 | None | target/surefire-reports/TEST-com.oneshop.CartWebIntegrationTest.xml |
| baseline-0104 | com.oneshop.CartWebIntegrationTest#everyCartActionIsDoneForTheAuthenticatedCustomerOnly | PASS | 0.035 | None | target/surefire-reports/TEST-com.oneshop.CartWebIntegrationTest.xml |
| baseline-0105 | com.oneshop.CatalogDatabaseIntegrationTest#imageOfAnotherProductCannotBeAddressedThroughAProduct | PASS | 0.018 | None | target/surefire-reports/TEST-com.oneshop.CatalogDatabaseIntegrationTest.xml |
| baseline-0106 | com.oneshop.CatalogDatabaseIntegrationTest#tc02_storeCatalogOnlyReflectsTheStoreProductsOfThatStore | PASS | 0.118 | None | target/surefire-reports/TEST-com.oneshop.CatalogDatabaseIntegrationTest.xml |
| baseline-0107 | com.oneshop.CatalogDatabaseIntegrationTest#storeFinderFiltersByProvinceAndArea | PASS | 0.012 | None | target/surefire-reports/TEST-com.oneshop.CatalogDatabaseIntegrationTest.xml |
| baseline-0108 | com.oneshop.CatalogDatabaseIntegrationTest#productThatIsNotVisibleHasNoDetailPage | PASS | 0.066 | None | target/surefire-reports/TEST-com.oneshop.CatalogDatabaseIntegrationTest.xml |
| baseline-0109 | com.oneshop.CatalogDatabaseIntegrationTest#catalogIsPaged | PASS | 0.029 | None | target/surefire-reports/TEST-com.oneshop.CatalogDatabaseIntegrationTest.xml |
| baseline-0110 | com.oneshop.CatalogDatabaseIntegrationTest#adminPutsASkuOnSaleAtAStoreWithItsOwnPriceQuantityAndStatus | PASS | 0.047 | None | target/surefire-reports/TEST-com.oneshop.CatalogDatabaseIntegrationTest.xml |
| baseline-0111 | com.oneshop.CatalogDatabaseIntegrationTest#clientViewsCarryNoQuantity | PASS | 0.024 | None | target/surefire-reports/TEST-com.oneshop.CatalogDatabaseIntegrationTest.xml |
| baseline-0112 | com.oneshop.CatalogDatabaseIntegrationTest#randomPickIsAFunctionOfTheSeed | PASS | 0.139 | None | target/surefire-reports/TEST-com.oneshop.CatalogDatabaseIntegrationTest.xml |
| baseline-0113 | com.oneshop.CatalogDatabaseIntegrationTest#aPostedStorePriceIsIgnoredAndWritesNoStockMovement | PASS | 0.012 | None | target/surefire-reports/TEST-com.oneshop.CatalogDatabaseIntegrationTest.xml |
| baseline-0114 | com.oneshop.CatalogDatabaseIntegrationTest#storeCatalogKeepsTheStoreContextWhenSearchingAndFiltering | PASS | 0.019 | None | target/surefire-reports/TEST-com.oneshop.CatalogDatabaseIntegrationTest.xml |
| baseline-0115 | com.oneshop.CatalogDatabaseIntegrationTest#productNeedsAnExistingCategoryAndBrand | PASS | 0.006 | None | target/surefire-reports/TEST-com.oneshop.CatalogDatabaseIntegrationTest.xml |
| baseline-0116 | com.oneshop.CatalogDatabaseIntegrationTest#tc18_inactiveStoreCannotBeSelectedOrSoldFromButItsDataStays | PASS | 0.016 | None | target/surefire-reports/TEST-com.oneshop.CatalogDatabaseIntegrationTest.xml |
| baseline-0117 | com.oneshop.CatalogDatabaseIntegrationTest#randomPickOfAStoreIsInStockThereWithoutDuplicatesAndLimited | PASS | 0.126 | None | target/surefire-reports/TEST-com.oneshop.CatalogDatabaseIntegrationTest.xml |
| baseline-0118 | com.oneshop.CatalogDatabaseIntegrationTest#tc03_productDetailFollowsTheSelectedStoreAndLeavesCartsAlone | PASS | 0.018 | None | target/surefire-reports/TEST-com.oneshop.CatalogDatabaseIntegrationTest.xml |
| baseline-0119 | com.oneshop.CatalogDatabaseIntegrationTest#tc17_productImageGoesToCloudinaryAndTheDatabaseKeepsOnlyUrlAndPublicId | PASS | 0.093 | None | target/surefire-reports/TEST-com.oneshop.CatalogDatabaseIntegrationTest.xml |
| baseline-0120 | com.oneshop.CatalogDatabaseIntegrationTest#productDetailAtAStoreThatDoesNotSellTheSku | PASS | 0.017 | None | target/surefire-reports/TEST-com.oneshop.CatalogDatabaseIntegrationTest.xml |
| baseline-0121 | com.oneshop.CatalogDatabaseIntegrationTest#randomPickOfTheChainIsSkusSomeActiveStoreHasInStock | PASS | 0.018 | None | target/surefire-reports/TEST-com.oneshop.CatalogDatabaseIntegrationTest.xml |
| baseline-0122 | com.oneshop.CatalogDatabaseIntegrationTest#tc18_deactivatingAStoreTakesItOutOfSaleWithoutDeletingAnything | PASS | 0.013 | None | target/surefire-reports/TEST-com.oneshop.CatalogDatabaseIntegrationTest.xml |
| baseline-0123 | com.oneshop.CatalogDatabaseIntegrationTest#hiddenCategoryOrBrandHidesItsProductsFromClients | PASS | 0.032 | None | target/surefire-reports/TEST-com.oneshop.CatalogDatabaseIntegrationTest.xml |
| baseline-0124 | com.oneshop.CatalogDatabaseIntegrationTest#chainCatalogSearchesByNameAndSkuAndFiltersByCategoryAndBrand | PASS | 0.032 | None | target/surefire-reports/TEST-com.oneshop.CatalogDatabaseIntegrationTest.xml |
| baseline-0125 | com.oneshop.CatalogDatabaseIntegrationTest#cloudinaryRefusingADeleteKeepsTheImageRow | PASS | 0.015 | None | target/surefire-reports/TEST-com.oneshop.CatalogDatabaseIntegrationTest.xml |
| baseline-0126 | com.oneshop.CatalogDatabaseIntegrationTest#storeProductNeedsAnExistingStoreAndProductAndAValidQuantity | PASS | 0.008 | None | target/surefire-reports/TEST-com.oneshop.CatalogDatabaseIntegrationTest.xml |
| baseline-0127 | com.oneshop.CatalogDatabaseIntegrationTest#storeCatalogOfAnInactiveOrUnknownStoreIsEmpty | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.CatalogDatabaseIntegrationTest.xml |
| baseline-0128 | com.oneshop.CatalogDatabaseIntegrationTest#duplicateStoreProductIsRejected | PASS | 0.021 | None | target/surefire-reports/TEST-com.oneshop.CatalogDatabaseIntegrationTest.xml |
| baseline-0129 | com.oneshop.CatalogDatabaseIntegrationTest#duplicateSkuIsRejected | PASS | 0.014 | None | target/surefire-reports/TEST-com.oneshop.CatalogDatabaseIntegrationTest.xml |
| baseline-0130 | com.oneshop.CatalogDatabaseIntegrationTest#tc17_brandLogoIsStoredAsUrlAndPublicIdAndTheOldFileIsRemoved | PASS | 0.012 | None | target/surefire-reports/TEST-com.oneshop.CatalogDatabaseIntegrationTest.xml |
| baseline-0131 | com.oneshop.CatalogDatabaseIntegrationTest#productDetailWithoutSelectedStoreListsEveryStoreSellingTheSku | PASS | 0.015 | None | target/surefire-reports/TEST-com.oneshop.CatalogDatabaseIntegrationTest.xml |
| baseline-0132 | com.oneshop.CatalogDatabaseIntegrationTest#tc01_chainCatalogShowsEachSkuOnceWithPriceAndAvailabilityPerStore | PASS | 0.031 | None | target/surefire-reports/TEST-com.oneshop.CatalogDatabaseIntegrationTest.xml |
| baseline-0133 | com.oneshop.CatalogDatabaseIntegrationTest#adminCreatesAndUpdatesAProduct | PASS | 0.032 | None | target/surefire-reports/TEST-com.oneshop.CatalogDatabaseIntegrationTest.xml |
| baseline-0134 | com.oneshop.CatalogDatabaseIntegrationTest#chainCatalogLeavesOutWhatIsNotOnSale | PASS | 0.03 | None | target/surefire-reports/TEST-com.oneshop.CatalogDatabaseIntegrationTest.xml |
| baseline-0135 | com.oneshop.CatalogDatabaseIntegrationTest#selectedStoreMustExistAndBeActive | PASS | 0.012 | None | target/surefire-reports/TEST-com.oneshop.CatalogDatabaseIntegrationTest.xml |
| baseline-0136 | com.oneshop.CatalogDatabaseIntegrationTest#brandProductCountsCountVisibleProductsOnly | PASS | 0.014 | None | target/surefire-reports/TEST-com.oneshop.CatalogDatabaseIntegrationTest.xml |
| baseline-0137 | com.oneshop.CatalogDatabaseIntegrationTest#failedUploadStoresNothing | PASS | 0.009 | None | target/surefire-reports/TEST-com.oneshop.CatalogDatabaseIntegrationTest.xml |
| baseline-0138 | com.oneshop.CatalogDatabaseIntegrationTest#anActiveProductWithPriceZeroIsRefusedByTheServiceToo | PASS | 0.016 | None | target/surefire-reports/TEST-com.oneshop.CatalogDatabaseIntegrationTest.xml |
| baseline-0139 | com.oneshop.CatalogDatabaseIntegrationTest#adminCreatesAStoreAndCodeMustBeUnique | PASS | 0.021 | None | target/surefire-reports/TEST-com.oneshop.CatalogDatabaseIntegrationTest.xml |
| baseline-0140 | com.oneshop.CatalogHttpDatabaseIntegrationTest#clientPagesNeverShowTheExactQuantity | PASS | 0.413 | None | target/surefire-reports/TEST-com.oneshop.CatalogHttpDatabaseIntegrationTest.xml |
| baseline-0141 | com.oneshop.CatalogHttpDatabaseIntegrationTest#duplicateStoreCodeAndBadPhoneAreShownOnTheStoreForm | PASS | 0.19 | None | target/surefire-reports/TEST-com.oneshop.CatalogHttpDatabaseIntegrationTest.xml |
| baseline-0142 | com.oneshop.CatalogHttpDatabaseIntegrationTest#clearingTheStoreGoesBackToTheWholeChain | PASS | 0.016 | None | target/surefire-reports/TEST-com.oneshop.CatalogHttpDatabaseIntegrationTest.xml |
| baseline-0143 | com.oneshop.CatalogHttpDatabaseIntegrationTest#storeProductFormRejectsNegativeQuantity | PASS | 0.149 | None | target/surefire-reports/TEST-com.oneshop.CatalogHttpDatabaseIntegrationTest.xml |
| baseline-0144 | com.oneshop.CatalogHttpDatabaseIntegrationTest#invalidSelectedStoreFallsBackToTheWholeChainAndDropsTheCookie | PASS | 0.249 | None | target/surefire-reports/TEST-com.oneshop.CatalogHttpDatabaseIntegrationTest.xml |
| baseline-0145 | com.oneshop.CatalogHttpDatabaseIntegrationTest#productDetailListsWhereTheSkuIsInStockAsAnOptionalLookup | PASS | 0.028 | None | target/surefire-reports/TEST-com.oneshop.CatalogHttpDatabaseIntegrationTest.xml |
| baseline-0146 | com.oneshop.CatalogHttpDatabaseIntegrationTest#imageUploadReachesCloudinaryServiceThroughTheMultipartFormWithCsrf | FAIL | 0.194 | None | target/surefire-reports/TEST-com.oneshop.CatalogHttpDatabaseIntegrationTest.xml |
| baseline-0147 | com.oneshop.CatalogHttpDatabaseIntegrationTest#aStoreThatCannotBeSelectedIsRefused | PASS | 0.077 | None | target/surefire-reports/TEST-com.oneshop.CatalogHttpDatabaseIntegrationTest.xml |
| baseline-0148 | com.oneshop.CatalogHttpDatabaseIntegrationTest#storeCannotBeChosenWithARequestParameter | PASS | 0.064 | None | target/surefire-reports/TEST-com.oneshop.CatalogHttpDatabaseIntegrationTest.xml |
| baseline-0149 | com.oneshop.CatalogHttpDatabaseIntegrationTest#storeFinderShowsMetadataAndFiltersByProvinceAndArea | PASS | 0.07 | None | target/surefire-reports/TEST-com.oneshop.CatalogHttpDatabaseIntegrationTest.xml |
| baseline-0150 | com.oneshop.CatalogHttpDatabaseIntegrationTest#duplicateStoreProductIsShownOnTheForm | PASS | 0.154 | None | target/surefire-reports/TEST-com.oneshop.CatalogHttpDatabaseIntegrationTest.xml |
| baseline-0151 | com.oneshop.CatalogHttpDatabaseIntegrationTest#tc02_aStoreCookieNoLongerSwitchesTheCatalog | PASS | 0.059 | None | target/surefire-reports/TEST-com.oneshop.CatalogHttpDatabaseIntegrationTest.xml |
| baseline-0152 | com.oneshop.CatalogHttpDatabaseIntegrationTest#adminPagesAreClosedToEveryoneElse | PASS | 0.402 | None | target/surefire-reports/TEST-com.oneshop.CatalogHttpDatabaseIntegrationTest.xml |
| baseline-0153 | com.oneshop.CatalogHttpDatabaseIntegrationTest#tc18_inactiveStoreIsShownAsNotOperatingAndCannotBeChosen | FAIL | 0.047 | None | target/surefire-reports/TEST-com.oneshop.CatalogHttpDatabaseIntegrationTest.xml |
| baseline-0154 | com.oneshop.CatalogHttpDatabaseIntegrationTest#switchingStoreFromTheDetailPageReturnsToItAndLeavesTheCartAlone | PASS | 0.04 | None | target/surefire-reports/TEST-com.oneshop.CatalogHttpDatabaseIntegrationTest.xml |
| baseline-0155 | com.oneshop.CatalogHttpDatabaseIntegrationTest#productDetailStillSellsWhenThePreviouslyChosenStoreDoesNotHoldIt | PASS | 0.023 | None | target/surefire-reports/TEST-com.oneshop.CatalogHttpDatabaseIntegrationTest.xml |
| baseline-0156 | com.oneshop.CatalogHttpDatabaseIntegrationTest#selectingAStoreNeedsACsrfTokenAndOnlyRedirectsInsideTheSite | PASS | 0.043 | None | target/surefire-reports/TEST-com.oneshop.CatalogHttpDatabaseIntegrationTest.xml |
| baseline-0157 | com.oneshop.CatalogHttpDatabaseIntegrationTest#duplicateCategoryAndBrandNamesAreShownOnTheirForms | PASS | 0.254 | None | target/surefire-reports/TEST-com.oneshop.CatalogHttpDatabaseIntegrationTest.xml |
| baseline-0158 | com.oneshop.CatalogHttpDatabaseIntegrationTest#duplicateSkuIsShownOnTheProductForm | PASS | 0.183 | None | target/surefire-reports/TEST-com.oneshop.CatalogHttpDatabaseIntegrationTest.xml |
| baseline-0159 | com.oneshop.CatalogHttpDatabaseIntegrationTest#productFormValidatesRequiredFields | PASS | 0.132 | None | target/surefire-reports/TEST-com.oneshop.CatalogHttpDatabaseIntegrationTest.xml |
| baseline-0160 | com.oneshop.CatalogHttpDatabaseIntegrationTest#chainCatalogSearchAndFilters | PASS | 0.077 | None | target/surefire-reports/TEST-com.oneshop.CatalogHttpDatabaseIntegrationTest.xml |
| baseline-0161 | com.oneshop.CatalogHttpDatabaseIntegrationTest#searchAndFiltersIgnoreAStaleStoreCookie | PASS | 0.049 | None | target/surefire-reports/TEST-com.oneshop.CatalogHttpDatabaseIntegrationTest.xml |
| baseline-0162 | com.oneshop.CatalogHttpDatabaseIntegrationTest#productsThatAreNotVisibleAnswer404InsideTheClientLayout | PASS | 0.027 | None | target/surefire-reports/TEST-com.oneshop.CatalogHttpDatabaseIntegrationTest.xml |
| baseline-0163 | com.oneshop.CatalogHttpDatabaseIntegrationTest#tc03_productDetailHasOnePriceWhateverStoreCookieIsPresent | PASS | 0.079 | None | target/surefire-reports/TEST-com.oneshop.CatalogHttpDatabaseIntegrationTest.xml |
| baseline-0164 | com.oneshop.CatalogHttpDatabaseIntegrationTest#aStoreCookieDoesNotMarkAnythingInTheFinder | FAIL | 0.021 | None | target/surefire-reports/TEST-com.oneshop.CatalogHttpDatabaseIntegrationTest.xml |
| baseline-0165 | com.oneshop.CatalogHttpDatabaseIntegrationTest#storeProductFormRejectsAForgedStoreSpecificPriceBeforeCallingTheUpdateService | PASS | 0.138 | None | target/surefire-reports/TEST-com.oneshop.CatalogHttpDatabaseIntegrationTest.xml |
| baseline-0166 | com.oneshop.CatalogHttpDatabaseIntegrationTest#br15_assertionCatchesLeakedStockInHtml | PASS | 0.004 | None | target/surefire-reports/TEST-com.oneshop.CatalogHttpDatabaseIntegrationTest.xml |
| baseline-0167 | com.oneshop.CatalogHttpDatabaseIntegrationTest#adminWritesNeedACsrfToken | PASS | 0.094 | None | target/surefire-reports/TEST-com.oneshop.CatalogHttpDatabaseIntegrationTest.xml |
| baseline-0168 | com.oneshop.CatalogHttpDatabaseIntegrationTest#adminWritesAreRefusedForOtherRolesEvenWithAValidCsrfToken | PASS | 0.378 | None | target/surefire-reports/TEST-com.oneshop.CatalogHttpDatabaseIntegrationTest.xml |
| baseline-0169 | com.oneshop.CatalogHttpDatabaseIntegrationTest#adminPagesRenderInTheAdminLayoutForAdmin | PASS | 0.616 | None | target/surefire-reports/TEST-com.oneshop.CatalogHttpDatabaseIntegrationTest.xml |
| baseline-0170 | com.oneshop.CatalogHttpDatabaseIntegrationTest#tc01_chainCatalogPageListsEachSkuOnceWithItsStores | PASS | 0.025 | None | target/surefire-reports/TEST-com.oneshop.CatalogHttpDatabaseIntegrationTest.xml |
| baseline-0171 | com.oneshop.CatalogWebIntegrationTest#anUnknownOrMalformedCookieIsStillDropped | PASS | 0.029 | None | target/surefire-reports/TEST-com.oneshop.CatalogWebIntegrationTest.xml |
| baseline-0172 | com.oneshop.CatalogWebIntegrationTest#aStaleStoreCookieNoLongerScopesTheCatalog | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.CatalogWebIntegrationTest.xml |
| baseline-0173 | com.oneshop.CatalogWebIntegrationTest#adminCatalogPagesNeedTheAdminRole | PASS | 0.094 | None | target/surefire-reports/TEST-com.oneshop.CatalogWebIntegrationTest.xml |
| baseline-0174 | com.oneshop.CatalogWebIntegrationTest#selectingAStoreSetsTheCookieOnlyAfterTheServiceAcceptedIt | PASS | 0.021 | None | target/surefire-reports/TEST-com.oneshop.CatalogWebIntegrationTest.xml |
| baseline-0175 | com.oneshop.CatalogWebIntegrationTest#unknownProductIs404 | PASS | 0.013 | None | target/surefire-reports/TEST-com.oneshop.CatalogWebIntegrationTest.xml |
| baseline-0176 | com.oneshop.CatalogWebIntegrationTest#theCatalogIsAlwaysTheSharedWholeChainCatalog | PASS | 0.009 | None | target/surefire-reports/TEST-com.oneshop.CatalogWebIntegrationTest.xml |
| baseline-0177 | com.oneshop.CatalogWebIntegrationTest#selectingAStoreWithoutCsrfTokenIsRejected | PASS | 0.006 | None | target/surefire-reports/TEST-com.oneshop.CatalogWebIntegrationTest.xml |
| baseline-0178 | com.oneshop.CatalogWebIntegrationTest#productDetailIsStoreIndependentAndOnlyLooksStoresUp | PASS | 0.018 | None | target/surefire-reports/TEST-com.oneshop.CatalogWebIntegrationTest.xml |
| baseline-0179 | com.oneshop.CatalogWebIntegrationTest#adminCatalogWritesNeedTheAdminRoleAndACsrfToken | PASS | 0.01 | None | target/surefire-reports/TEST-com.oneshop.CatalogWebIntegrationTest.xml |
| baseline-0180 | com.oneshop.CatalogWebIntegrationTest#storeFinderIsPublicAndPassesTheFiltersToTheService | PASS | 0.016 | None | target/surefire-reports/TEST-com.oneshop.CatalogWebIntegrationTest.xml |
| baseline-0181 | com.oneshop.CategoryAdminSecurityWebIntegrationTest#staffIsForbidden | PASS | 0.22 | None | target/surefire-reports/TEST-com.oneshop.CategoryAdminSecurityWebIntegrationTest.xml |
| baseline-0182 | com.oneshop.CategoryAdminSecurityWebIntegrationTest#adminPostWithoutCsrfIsRejectedWith403 | PASS | 0.224 | None | target/surefire-reports/TEST-com.oneshop.CategoryAdminSecurityWebIntegrationTest.xml |
| baseline-0183 | com.oneshop.CategoryAdminSecurityWebIntegrationTest#guestIsRedirectedToLogin | PASS | 0.238 | None | target/surefire-reports/TEST-com.oneshop.CategoryAdminSecurityWebIntegrationTest.xml |
| baseline-0184 | com.oneshop.CategoryAdminSecurityWebIntegrationTest#adminCanAccessCategoriesListAndRendersTableAndUploadControls | FAIL | 0.236 | None | target/surefire-reports/TEST-com.oneshop.CategoryAdminSecurityWebIntegrationTest.xml |
| baseline-0185 | com.oneshop.CategoryAdminSecurityWebIntegrationTest#customerIsForbidden | PASS | 0.238 | None | target/surefire-reports/TEST-com.oneshop.CategoryAdminSecurityWebIntegrationTest.xml |
| baseline-0186 | com.oneshop.CentralCartIntegrationTest#be02_04_productDetailAvailabilityAcrossChain | PASS | 0.061 | None | target/surefire-reports/TEST-com.oneshop.CentralCartIntegrationTest.xml |
| baseline-0187 | com.oneshop.CentralCartIntegrationTest#be02_11_stockCheckUsesQuantityMinusReserved | PASS | 0.051 | None | target/surefire-reports/TEST-com.oneshop.CentralCartIntegrationTest.xml |
| baseline-0188 | com.oneshop.CentralCartIntegrationTest#be02_12_cartItemStatusOutOfStock | PASS | 0.047 | None | target/surefire-reports/TEST-com.oneshop.CentralCartIntegrationTest.xml |
| baseline-0189 | com.oneshop.CentralCartIntegrationTest#be02_05_addToCartByProductIdDirectly | PASS | 0.045 | None | target/surefire-reports/TEST-com.oneshop.CentralCartIntegrationTest.xml |
| baseline-0190 | com.oneshop.CentralCartIntegrationTest#be02_10_cartTotalCalculatedFromProductPrice | PASS | 0.044 | None | target/surefire-reports/TEST-com.oneshop.CentralCartIntegrationTest.xml |
| baseline-0191 | com.oneshop.CentralCartIntegrationTest#be02_07_cartItemHasNonNullProductId | PASS | 0.046 | None | target/surefire-reports/TEST-com.oneshop.CentralCartIntegrationTest.xml |
| baseline-0192 | com.oneshop.CentralCartIntegrationTest#be02_15_selectionValidatesWithoutStoreGrouping | PASS | 0.045 | None | target/surefire-reports/TEST-com.oneshop.CentralCartIntegrationTest.xml |
| baseline-0193 | com.oneshop.CentralCartIntegrationTest#be02_08_addingSameProductTwiceSumsQuantity | PASS | 0.031 | None | target/surefire-reports/TEST-com.oneshop.CentralCartIntegrationTest.xml |
| baseline-0194 | com.oneshop.CentralCartIntegrationTest#be02_13_cartItemStatusInsufficientStock | PASS | 0.048 | None | target/surefire-reports/TEST-com.oneshop.CentralCartIntegrationTest.xml |
| baseline-0195 | com.oneshop.CentralCartIntegrationTest#be02_03_productDetailWithoutStoreShowsProductPrice | PASS | 0.033 | None | target/surefire-reports/TEST-com.oneshop.CentralCartIntegrationTest.xml |
| baseline-0196 | com.oneshop.CentralCartIntegrationTest#be02_02_productCardHasUnifiedPriceAndNoFromPrice | PASS | 0.052 | None | target/surefire-reports/TEST-com.oneshop.CentralCartIntegrationTest.xml |
| baseline-0197 | com.oneshop.CentralCartIntegrationTest#be02_09_cartResponseReturnsFlatItemsList | PASS | 0.046 | None | target/surefire-reports/TEST-com.oneshop.CentralCartIntegrationTest.xml |
| baseline-0198 | com.oneshop.CentralCartIntegrationTest#be02_14_updateQuantityValidatesChainStock | PASS | 0.044 | None | target/surefire-reports/TEST-com.oneshop.CentralCartIntegrationTest.xml |
| baseline-0199 | com.oneshop.CentralCartIntegrationTest#be02_06_addToCartByStoreProductIdLegacyBridge | PASS | 0.058 | None | target/surefire-reports/TEST-com.oneshop.CentralCartIntegrationTest.xml |
| baseline-0200 | com.oneshop.CentralCartIntegrationTest#be02_01_wholeChainCatalogUsesProductPrice | PASS | 0.039 | None | target/surefire-reports/TEST-com.oneshop.CentralCartIntegrationTest.xml |
| baseline-0201 | com.oneshop.CheckoutConcurrencyDatabaseIntegrationTest#theSameCheckoutSubmittedTwiceAtOnceIsPlacedOnce | PASS | 0.111 | None | target/surefire-reports/TEST-com.oneshop.CheckoutConcurrencyDatabaseIntegrationTest.xml |
| baseline-0202 | com.oneshop.CheckoutConcurrencyDatabaseIntegrationTest#afterAFailedCheckoutTheSameCartCanBeCheckedOutSuccessfully | PASS | 0.113 | None | target/surefire-reports/TEST-com.oneshop.CheckoutConcurrencyDatabaseIntegrationTest.xml |
| baseline-0203 | com.oneshop.CheckoutConcurrencyDatabaseIntegrationTest#failureAfterTheFirstStoreGroupWasWrittenRollsBackTheWholeCheckout | PASS | 0.125 | None | target/surefire-reports/TEST-com.oneshop.CheckoutConcurrencyDatabaseIntegrationTest.xml |
| baseline-0204 | com.oneshop.CheckoutConcurrencyDatabaseIntegrationTest#customersBuyingTheSameSkusInOppositeOrderBothSucceedWithoutDeadlock | PASS | 0.146 | None | target/surefire-reports/TEST-com.oneshop.CheckoutConcurrencyDatabaseIntegrationTest.xml |
| baseline-0205 | com.oneshop.CheckoutConcurrencyDatabaseIntegrationTest#tc07_twoCustomersRacingForTheLastUnitNeverOversell | PASS | 0.119 | None | target/surefire-reports/TEST-com.oneshop.CheckoutConcurrencyDatabaseIntegrationTest.xml |
| baseline-0206 | com.oneshop.CheckoutConcurrencyDatabaseIntegrationTest#tc07_whenStockCoversOnlyOneOfTwoLargerOrdersOnlyOneIsPlaced | PASS | 0.103 | None | target/surefire-reports/TEST-com.oneshop.CheckoutConcurrencyDatabaseIntegrationTest.xml |
| baseline-0207 | com.oneshop.CheckoutDatabaseIntegrationTest#everyOrderStartsItsTimelineWithACreationEntryWrittenByTheSystem | PASS | 0.109 | None | target/surefire-reports/TEST-com.oneshop.CheckoutDatabaseIntegrationTest.xml |
| baseline-0208 | com.oneshop.CheckoutDatabaseIntegrationTest#checkoutIsRefusedWhenStockDroppedSinceTheLineWasAdded | PASS | 0.08 | None | target/surefire-reports/TEST-com.oneshop.CheckoutDatabaseIntegrationTest.xml |
| baseline-0209 | com.oneshop.CheckoutDatabaseIntegrationTest#stockIsDeductedWithOneOrderMovementPerLine | PASS | 0.097 | None | target/surefire-reports/TEST-com.oneshop.CheckoutDatabaseIntegrationTest.xml |
| baseline-0210 | com.oneshop.CheckoutDatabaseIntegrationTest#databaseRejectsNegativeStock | PASS | 0.075 | None | target/surefire-reports/TEST-com.oneshop.CheckoutDatabaseIntegrationTest.xml |
| baseline-0211 | com.oneshop.CheckoutDatabaseIntegrationTest#oneInvalidStoreGroupRefusesTheWholeCheckout | PASS | 0.112 | None | target/surefire-reports/TEST-com.oneshop.CheckoutDatabaseIntegrationTest.xml |
| baseline-0212 | com.oneshop.CheckoutDatabaseIntegrationTest#storeOfAnOrderComesFromTheCartLinesNotFromTheRequest | PASS | 0.079 | None | target/surefire-reports/TEST-com.oneshop.CheckoutDatabaseIntegrationTest.xml |
| baseline-0213 | com.oneshop.CheckoutDatabaseIntegrationTest#databaseRejectsAnOrderItemWhoseSubtotalIsNotPriceTimesQuantity | PASS | 0.1 | None | target/surefire-reports/TEST-com.oneshop.CheckoutDatabaseIntegrationTest.xml |
| baseline-0214 | com.oneshop.CheckoutDatabaseIntegrationTest#tc08_checkoutRequestCannotCarryAPriceTotalStatusOrOwner | PASS | 0.192 | None | target/surefire-reports/TEST-com.oneshop.CheckoutDatabaseIntegrationTest.xml |
| baseline-0215 | com.oneshop.CheckoutDatabaseIntegrationTest#tc14_orderItemsKeepTheirSnapshotWhenPriceAndProductNameChangeLater | PASS | 0.088 | None | target/surefire-reports/TEST-com.oneshop.CheckoutDatabaseIntegrationTest.xml |
| baseline-0216 | com.oneshop.CheckoutDatabaseIntegrationTest#checkoutIsRefusedWhenALineIsNoLongerOnSale | PASS | 0.072 | None | target/surefire-reports/TEST-com.oneshop.CheckoutDatabaseIntegrationTest.xml |
| baseline-0217 | com.oneshop.CheckoutDatabaseIntegrationTest#databaseRejectsAnOrderWithAForbiddenFulfillmentAndPaymentCombination | PASS | 0.098 | None | target/surefire-reports/TEST-com.oneshop.CheckoutDatabaseIntegrationTest.xml |
| baseline-0218 | com.oneshop.CheckoutDatabaseIntegrationTest#aCustomerCannotCheckOutAnotherCustomersLines | PASS | 0.082 | None | target/surefire-reports/TEST-com.oneshop.CheckoutDatabaseIntegrationTest.xml |
| baseline-0219 | com.oneshop.CheckoutDatabaseIntegrationTest#buyingTheWholeStockLeavesZeroNeverLess | PASS | 0.096 | None | target/surefire-reports/TEST-com.oneshop.CheckoutDatabaseIntegrationTest.xml |
| baseline-0220 | com.oneshop.CheckoutDatabaseIntegrationTest#onlyTheCheckedOutLinesLeaveTheCart | PASS | 0.102 | None | target/surefire-reports/TEST-com.oneshop.CheckoutDatabaseIntegrationTest.xml |
| baseline-0221 | com.oneshop.CheckoutDatabaseIntegrationTest#fulfillmentAndPaymentCombinationsAreChecked | PASS | 0.086 | None | target/surefire-reports/TEST-com.oneshop.CheckoutDatabaseIntegrationTest.xml |
| baseline-0222 | com.oneshop.CheckoutDatabaseIntegrationTest#preparingShowsTheChosenLinesByStoreAndSuggestsTheDefaultAddress | PASS | 0.095 | None | target/surefire-reports/TEST-com.oneshop.CheckoutDatabaseIntegrationTest.xml |
| baseline-0223 | com.oneshop.CheckoutDatabaseIntegrationTest#aStoreOnlyOffersTheWaysOfReceivingItSupports | PASS | 0.084 | None | target/surefire-reports/TEST-com.oneshop.CheckoutDatabaseIntegrationTest.xml |
| baseline-0224 | com.oneshop.CheckoutDatabaseIntegrationTest#tc06_checkoutOfThreeStoresCreatesOneSessionAndOneOrderPerStore | PASS | 0.104 | None | target/surefire-reports/TEST-com.oneshop.CheckoutDatabaseIntegrationTest.xml |
| baseline-0225 | com.oneshop.CheckoutDatabaseIntegrationTest#tc08_priceComesFromTheStoreProductAtTheMomentOfCheckoutNotFromTheCartPage | PASS | 0.096 | None | target/surefire-reports/TEST-com.oneshop.CheckoutDatabaseIntegrationTest.xml |
| baseline-0226 | com.oneshop.CheckoutDatabaseIntegrationTest#deliveryNeedsAnAddressAndEveryOrderNeedsAReceiver | PASS | 0.085 | None | target/surefire-reports/TEST-com.oneshop.CheckoutDatabaseIntegrationTest.xml |
| baseline-0227 | com.oneshop.CheckoutDatabaseIntegrationTest#aCheckoutCanOnlyBeReadByItsOwner | PASS | 0.086 | None | target/surefire-reports/TEST-com.oneshop.CheckoutDatabaseIntegrationTest.xml |
| baseline-0228 | com.oneshop.CheckoutDatabaseIntegrationTest#duplicateCartItemIdsAreOrderedOnce | PASS | 0.091 | None | target/surefire-reports/TEST-com.oneshop.CheckoutDatabaseIntegrationTest.xml |
| baseline-0229 | com.oneshop.CheckoutDatabaseIntegrationTest#checkoutOfOneStoreCreatesOneSessionOneOrderAndItsSnapshots | PASS | 0.069 | None | target/surefire-reports/TEST-com.oneshop.CheckoutDatabaseIntegrationTest.xml |
| baseline-0230 | com.oneshop.CheckoutHttpDatabaseIntegrationTest#tc14_resultPageKeepsShowingTheSnapshotAfterThePriceChanges | PASS | 0.104 | None | target/surefire-reports/TEST-com.oneshop.CheckoutHttpDatabaseIntegrationTest.xml |
| baseline-0231 | com.oneshop.CheckoutHttpDatabaseIntegrationTest#aStoreThatDoesNotDeliverOffersPickupOnly | PASS | 0.242 | None | target/surefire-reports/TEST-com.oneshop.CheckoutHttpDatabaseIntegrationTest.xml |
| baseline-0232 | com.oneshop.CheckoutHttpDatabaseIntegrationTest#oneLineOutOfStockRefusesTheWholeCheckoutAndShowsWhy | PASS | 0.133 | None | target/surefire-reports/TEST-com.oneshop.CheckoutHttpDatabaseIntegrationTest.xml |
| baseline-0233 | com.oneshop.CheckoutHttpDatabaseIntegrationTest#invalidChoicesAreRefusedAndThePageKeepsWhatWasChosen | PASS | 0.159 | None | target/surefire-reports/TEST-com.oneshop.CheckoutHttpDatabaseIntegrationTest.xml |
| baseline-0234 | com.oneshop.CheckoutHttpDatabaseIntegrationTest#theSameFormSubmittedAgainDoesNotOrderTwice | PASS | 0.195 | None | target/surefire-reports/TEST-com.oneshop.CheckoutHttpDatabaseIntegrationTest.xml |
| baseline-0235 | com.oneshop.CheckoutHttpDatabaseIntegrationTest#anotherCustomersLinesCannotBeCheckedOut | PASS | 0.256 | None | target/surefire-reports/TEST-com.oneshop.CheckoutHttpDatabaseIntegrationTest.xml |
| baseline-0236 | com.oneshop.CheckoutHttpDatabaseIntegrationTest#tc06_tc08_checkoutOfThreeStoresThroughTheFormWithTamperedPrices | PASS | 0.331 | None | target/surefire-reports/TEST-com.oneshop.CheckoutHttpDatabaseIntegrationTest.xml |
| baseline-0237 | com.oneshop.CheckoutHttpDatabaseIntegrationTest#onlyCustomersReachCheckout | PASS | 0.108 | None | target/surefire-reports/TEST-com.oneshop.CheckoutHttpDatabaseIntegrationTest.xml |
| baseline-0238 | com.oneshop.CheckoutHttpDatabaseIntegrationTest#linesThatCannotBeCheckedOutLeadBackToTheCartWithTheReason | PASS | 0.094 | None | target/surefire-reports/TEST-com.oneshop.CheckoutHttpDatabaseIntegrationTest.xml |
| baseline-0239 | com.oneshop.CheckoutHttpDatabaseIntegrationTest#checkoutPageShowsOneBlockPerStoreWithItsOwnChoicesAndNoPriceFields | PASS | 0.106 | None | target/surefire-reports/TEST-com.oneshop.CheckoutHttpDatabaseIntegrationTest.xml |
| baseline-0240 | com.oneshop.CheckoutTemplateContractTest#onlinePaymentIsClearlyIdentifiedAsSimulatedBeforeTheCustomerChoosesIt | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.CheckoutTemplateContractTest.xml |
| baseline-0241 | com.oneshop.CheckoutTemplateContractTest#anUnconfirmedCodSplitCannotBeSubmittedFromTheInitialPage | PASS | 0.01 | None | target/surefire-reports/TEST-com.oneshop.CheckoutTemplateContractTest.xml |
| baseline-0242 | com.oneshop.CheckoutTemplateContractTest#changingAnAllocationChoiceRefreshesPreviewWithGetAndCannotAccidentallyOrderByEnter | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.CheckoutTemplateContractTest.xml |
| baseline-0243 | com.oneshop.CheckoutTemplateContractTest#finalOrderSubmissionHasVisibleSingleSubmitFeedbackAndAStockFailureAlert | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.CheckoutTemplateContractTest.xml |
| baseline-0244 | com.oneshop.CheckoutV3ConcurrencyDatabaseIntegrationTest#be03_22b_lastUnitAcrossTwoStores | PASS | 0.144 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3ConcurrencyDatabaseIntegrationTest.xml |
| baseline-0245 | com.oneshop.CheckoutV3ConcurrencyDatabaseIntegrationTest#be03_13_failureInTheMiddleRollsBackAllStores | PASS | 0.075 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3ConcurrencyDatabaseIntegrationTest.xml |
| baseline-0246 | com.oneshop.CheckoutV3ConcurrencyDatabaseIntegrationTest#be03_21b_doubleSubmitOfOneCart | PASS | 0.071 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3ConcurrencyDatabaseIntegrationTest.xml |
| baseline-0247 | com.oneshop.CheckoutV3ConcurrencyDatabaseIntegrationTest#be03_30_paymentInitializationFailureRollsBack | PASS | 0.057 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3ConcurrencyDatabaseIntegrationTest.xml |
| baseline-0248 | com.oneshop.CheckoutV3ConcurrencyDatabaseIntegrationTest#f10_sameSkuSplitUnderContention | PASS | 0.118 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3ConcurrencyDatabaseIntegrationTest.xml |
| baseline-0249 | com.oneshop.CheckoutV3ConcurrencyDatabaseIntegrationTest#be03_22_lastUnitOneStore | PASS | 0.294 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3ConcurrencyDatabaseIntegrationTest.xml |
| baseline-0250 | com.oneshop.CheckoutV3ConcurrencyDatabaseIntegrationTest#f10b_sourcesChangeAfterTheCart | PASS | 0.066 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3ConcurrencyDatabaseIntegrationTest.xml |
| baseline-0251 | com.oneshop.CheckoutV3ConcurrencyDatabaseIntegrationTest#oppositeOrderDoesNotDeadlock | PASS | 0.213 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3ConcurrencyDatabaseIntegrationTest.xml |
| baseline-0252 | com.oneshop.CheckoutV3ConcurrencyDatabaseIntegrationTest#f11_codSplitRollsBackAllOrders | PASS | 0.087 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3ConcurrencyDatabaseIntegrationTest.xml |
| baseline-0253 | com.oneshop.CheckoutV3DatabaseIntegrationTest#be03_03_threeStores | PASS | 0.062 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3DatabaseIntegrationTest.xml |
| baseline-0254 | com.oneshop.CheckoutV3DatabaseIntegrationTest#be03_04_inactiveStoreIsNeverUsed | PASS | 0.026 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3DatabaseIntegrationTest.xml |
| baseline-0255 | com.oneshop.CheckoutV3DatabaseIntegrationTest#be03_29_previewPlanChangesBeforeCommit | PASS | 0.052 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3DatabaseIntegrationTest.xml |
| baseline-0256 | com.oneshop.CheckoutV3DatabaseIntegrationTest#be03_09_itemsMatchFulfillmentOrderAndStore | PASS | 0.043 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3DatabaseIntegrationTest.xml |
| baseline-0257 | com.oneshop.CheckoutV3DatabaseIntegrationTest#be03_47_browserCannotConfirmPayment | PASS | 0.036 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3DatabaseIntegrationTest.xml |
| baseline-0258 | com.oneshop.CheckoutV3DatabaseIntegrationTest#be03_18_pickupAtStoreWithMissingSkuIsRefused | PASS | 0.021 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3DatabaseIntegrationTest.xml |
| baseline-0259 | com.oneshop.CheckoutV3DatabaseIntegrationTest#legacyV2RequestIsRefused | PASS | 0.006 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3DatabaseIntegrationTest.xml |
| baseline-0260 | com.oneshop.CheckoutV3DatabaseIntegrationTest#be03_16_codSplitWithConsent | PASS | 0.054 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3DatabaseIntegrationTest.xml |
| baseline-0261 | com.oneshop.CheckoutV3DatabaseIntegrationTest#be03_19b_noNewAttemptAfterTheDeadline | PASS | 0.041 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3DatabaseIntegrationTest.xml |
| baseline-0262 | com.oneshop.CheckoutV3DatabaseIntegrationTest#be03_20_staffCannotPrepareBeforePayment | PASS | 0.056 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3DatabaseIntegrationTest.xml |
| baseline-0263 | com.oneshop.CheckoutV3DatabaseIntegrationTest#be03_11_inventoryAudit | PASS | 0.032 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3DatabaseIntegrationTest.xml |
| baseline-0264 | com.oneshop.CheckoutV3DatabaseIntegrationTest#be03_12_reservedQuantityIsRespected | PASS | 0.017 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3DatabaseIntegrationTest.xml |
| baseline-0265 | com.oneshop.CheckoutV3DatabaseIntegrationTest#be03_06b_quantityIsSplitBetweenStores | PASS | 0.032 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3DatabaseIntegrationTest.xml |
| baseline-0266 | com.oneshop.CheckoutV3DatabaseIntegrationTest#be03_02_twoStoresOnlineIsOneOrder | PASS | 0.033 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3DatabaseIntegrationTest.xml |
| baseline-0267 | com.oneshop.CheckoutV3DatabaseIntegrationTest#be03_17_pickupAtOneStore | PASS | 0.032 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3DatabaseIntegrationTest.xml |
| baseline-0268 | com.oneshop.CheckoutV3DatabaseIntegrationTest#be03_19_onlinePendingPaymentAndDeadline | PASS | 0.042 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3DatabaseIntegrationTest.xml |
| baseline-0269 | com.oneshop.CheckoutV3DatabaseIntegrationTest#be03_21_duplicateCheckout | PASS | 0.019 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3DatabaseIntegrationTest.xml |
| baseline-0270 | com.oneshop.CheckoutV3DatabaseIntegrationTest#be03_05_pickupOnlyStoreIsNotADeliverySource | PASS | 0.035 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3DatabaseIntegrationTest.xml |
| baseline-0271 | com.oneshop.CheckoutV3DatabaseIntegrationTest#be03_26_27_28_integrityAfterCheckouts | PASS | 0.069 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3DatabaseIntegrationTest.xml |
| baseline-0272 | com.oneshop.CheckoutV3DatabaseIntegrationTest#be03_01_oneStoreFullCart | PASS | 0.024 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3DatabaseIntegrationTest.xml |
| baseline-0273 | com.oneshop.CheckoutV3DatabaseIntegrationTest#be03_10_immediateDeduction | PASS | 0.032 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3DatabaseIntegrationTest.xml |
| baseline-0274 | com.oneshop.CheckoutV3DatabaseIntegrationTest#be03_14_codOneStore | PASS | 0.032 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3DatabaseIntegrationTest.xml |
| baseline-0275 | com.oneshop.CheckoutV3DatabaseIntegrationTest#be03_24_snapshotIsImmutable | PASS | 0.015 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3DatabaseIntegrationTest.xml |
| baseline-0276 | com.oneshop.CheckoutV3DatabaseIntegrationTest#be03_08_priceComesFromTheProduct | PASS | 0.016 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3DatabaseIntegrationTest.xml |
| baseline-0277 | com.oneshop.CheckoutV3DatabaseIntegrationTest#be03_25_foreignCartLines | PASS | 0.034 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3DatabaseIntegrationTest.xml |
| baseline-0278 | com.oneshop.CheckoutV3DatabaseIntegrationTest#be03_15_codSplitNeedsConsent | PASS | 0.03 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3DatabaseIntegrationTest.xml |
| baseline-0279 | com.oneshop.CheckoutV3DatabaseIntegrationTest#be03_08b_priceChangeNeedsReconfirmation | PASS | 0.016 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3DatabaseIntegrationTest.xml |
| baseline-0280 | com.oneshop.CheckoutV3DatabaseIntegrationTest#be03_23_totals | PASS | 0.032 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3DatabaseIntegrationTest.xml |
| baseline-0281 | com.oneshop.CheckoutV3DatabaseIntegrationTest#previewIsReadOnly | PASS | 0.038 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3DatabaseIntegrationTest.xml |
| baseline-0282 | com.oneshop.CheckoutV3DatabaseIntegrationTest#be03_06_deliveryOnlyStoreCannotBePickedUp | PASS | 0.01 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3DatabaseIntegrationTest.xml |
| baseline-0283 | com.oneshop.CheckoutV3DatabaseIntegrationTest#be03_07_notEnoughStockCreatesNothing | PASS | 0.023 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3DatabaseIntegrationTest.xml |
| baseline-0284 | com.oneshop.CheckoutV3HttpDatabaseIntegrationTest#onlineMultiStoreThroughTheBrowser | PASS | 0.676 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3HttpDatabaseIntegrationTest.xml |
| baseline-0285 | com.oneshop.CheckoutV3HttpDatabaseIntegrationTest#pickupThroughTheBrowser | PASS | 0.243 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3HttpDatabaseIntegrationTest.xml |
| baseline-0286 | com.oneshop.CheckoutV3HttpDatabaseIntegrationTest#codSplitThroughTheBrowser | PASS | 0.322 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3HttpDatabaseIntegrationTest.xml |
| baseline-0287 | com.oneshop.CheckoutV3PolicyGateDatabaseIntegrationTest#killSwitchClosesDelivery | PASS | 0.022 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3PolicyGateDatabaseIntegrationTest.xml |
| baseline-0288 | com.oneshop.CheckoutV3PolicyGateDatabaseIntegrationTest#pickupPayAtStoreIsFree | PASS | 0.02 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3PolicyGateDatabaseIntegrationTest.xml |
| baseline-0289 | com.oneshop.CheckoutV3PolicyGateDatabaseIntegrationTest#deliveryIsOpenByDefault | PASS | 0.038 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3PolicyGateDatabaseIntegrationTest.xml |
| baseline-0290 | com.oneshop.CheckoutV3PolicyGateDatabaseIntegrationTest#onlineIsOpenByDefaultAndTheSwitchStillClosesIt | PASS | 0.02 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3PolicyGateDatabaseIntegrationTest.xml |
| baseline-0291 | com.oneshop.CheckoutV3SplitAndFeeDatabaseIntegrationTest#f08_reservedQuantityLimitsWhatIsAllocated | PASS | 0.049 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3SplitAndFeeDatabaseIntegrationTest.xml |
| baseline-0292 | com.oneshop.CheckoutV3SplitAndFeeDatabaseIntegrationTest#f25_f26_codSplitFeePerOrder | PASS | 0.1 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3SplitAndFeeDatabaseIntegrationTest.xml |
| baseline-0293 | com.oneshop.CheckoutV3SplitAndFeeDatabaseIntegrationTest#f15_deliveryDisabledStoreIsNotUsed | PASS | 0.027 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3SplitAndFeeDatabaseIntegrationTest.xml |
| baseline-0294 | com.oneshop.CheckoutV3SplitAndFeeDatabaseIntegrationTest#f17_invalidAddressIsRefused | PASS | 0.015 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3SplitAndFeeDatabaseIntegrationTest.xml |
| baseline-0295 | com.oneshop.CheckoutV3SplitAndFeeDatabaseIntegrationTest#f13_northernStoreServesASouthernAddress | PASS | 0.033 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3SplitAndFeeDatabaseIntegrationTest.xml |
| baseline-0296 | com.oneshop.CheckoutV3SplitAndFeeDatabaseIntegrationTest#f05_totalShortageCreatesNothing | PASS | 0.03 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3SplitAndFeeDatabaseIntegrationTest.xml |
| baseline-0297 | com.oneshop.CheckoutV3SplitAndFeeDatabaseIntegrationTest#f09_planIsDeterministic | PASS | 0.034 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3SplitAndFeeDatabaseIntegrationTest.xml |
| baseline-0298 | com.oneshop.CheckoutV3SplitAndFeeDatabaseIntegrationTest#f27_f28_pickupIsFree | PASS | 0.045 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3SplitAndFeeDatabaseIntegrationTest.xml |
| baseline-0299 | com.oneshop.CheckoutV3SplitAndFeeDatabaseIntegrationTest#f21_f22_f23_onlineFeeIsOnePerOrder | PASS | 0.148 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3SplitAndFeeDatabaseIntegrationTest.xml |
| baseline-0300 | com.oneshop.CheckoutV3SplitAndFeeDatabaseIntegrationTest#f19_f20_pickupDoesNotBorrow | PASS | 0.044 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3SplitAndFeeDatabaseIntegrationTest.xml |
| baseline-0301 | com.oneshop.CheckoutV3SplitAndFeeDatabaseIntegrationTest#f24_codOneStore | PASS | 0.025 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3SplitAndFeeDatabaseIntegrationTest.xml |
| baseline-0302 | com.oneshop.CheckoutV3SplitAndFeeDatabaseIntegrationTest#c5_codSplitOfOneSku | PASS | 0.028 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3SplitAndFeeDatabaseIntegrationTest.xml |
| baseline-0303 | com.oneshop.CheckoutV3SplitAndFeeDatabaseIntegrationTest#f04_mixedCartWithOneSplitSku | PASS | 0.045 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3SplitAndFeeDatabaseIntegrationTest.xml |
| baseline-0304 | com.oneshop.CheckoutV3SplitAndFeeDatabaseIntegrationTest#fingerprintIsBoundToCustomerAndAddress | PASS | 0.029 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3SplitAndFeeDatabaseIntegrationTest.xml |
| baseline-0305 | com.oneshop.CheckoutV3SplitAndFeeDatabaseIntegrationTest#f06_f07_inactiveSourcesAreNotUsed | PASS | 0.032 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3SplitAndFeeDatabaseIntegrationTest.xml |
| baseline-0306 | com.oneshop.CheckoutV3SplitAndFeeDatabaseIntegrationTest#f02_sameSkuSplitOverTwoStores | PASS | 0.048 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3SplitAndFeeDatabaseIntegrationTest.xml |
| baseline-0307 | com.oneshop.CheckoutV3SplitAndFeeDatabaseIntegrationTest#f30_feeChangedAfterPreview | PASS | 0.016 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3SplitAndFeeDatabaseIntegrationTest.xml |
| baseline-0308 | com.oneshop.CheckoutV3SplitAndFeeDatabaseIntegrationTest#f03_sameSkuSplitOverThreeStores | PASS | 0.031 | None | target/surefire-reports/TEST-com.oneshop.CheckoutV3SplitAndFeeDatabaseIntegrationTest.xml |
| baseline-0309 | com.oneshop.CheckoutWebIntegrationTest#onlyCustomersWithACsrfTokenCanCheckOut | PASS | 0.04 | None | target/surefire-reports/TEST-com.oneshop.CheckoutWebIntegrationTest.xml |
| baseline-0310 | com.oneshop.CheckoutWebIntegrationTest#aRefusalShowsTheCheckoutPageAgainWithTheReasonAndTheChoicesMade | PASS | 0.024 | None | target/surefire-reports/TEST-com.oneshop.CheckoutWebIntegrationTest.xml |
| baseline-0311 | com.oneshop.CheckoutWebIntegrationTest#invalidFormValuesNeverReachTheService | PASS | 0.055 | None | target/surefire-reports/TEST-com.oneshop.CheckoutWebIntegrationTest.xml |
| baseline-0312 | com.oneshop.CheckoutWebIntegrationTest#codFromSeveralStoresAsksForExplicitSplitConsent | PASS | 0.016 | None | target/surefire-reports/TEST-com.oneshop.CheckoutWebIntegrationTest.xml |
| baseline-0313 | com.oneshop.CheckoutWebIntegrationTest#aLockConflictIsReportedAsBusyNotAsAnError | PASS | 0.02 | None | target/surefire-reports/TEST-com.oneshop.CheckoutWebIntegrationTest.xml |
| baseline-0314 | com.oneshop.CheckoutWebIntegrationTest#theFormIsBoundToOneChoiceForTheWholeOrderAndPlacedForTheAuthenticatedCustomer | PASS | 0.016 | None | target/surefire-reports/TEST-com.oneshop.CheckoutWebIntegrationTest.xml |
| baseline-0315 | com.oneshop.CheckoutWebIntegrationTest#aBusinessRefusalWithoutAPreviewFallsBackToTheCart | PASS | 0.016 | None | target/surefire-reports/TEST-com.oneshop.CheckoutWebIntegrationTest.xml |
| baseline-0316 | com.oneshop.CheckoutWebIntegrationTest#closedFlowsAreShownAsClosedNotHidden | PASS | 0.019 | None | target/surefire-reports/TEST-com.oneshop.CheckoutWebIntegrationTest.xml |
| baseline-0317 | com.oneshop.CheckoutWebIntegrationTest#aPickupFormNamesTheStoreItWantsToCollectAt | PASS | 0.014 | None | target/surefire-reports/TEST-com.oneshop.CheckoutWebIntegrationTest.xml |
| baseline-0318 | com.oneshop.CheckoutWebIntegrationTest#checkoutPageHasNoStoreGroupsAndShowsOneOrderWithItsFulfillments | PASS | 0.013 | None | target/surefire-reports/TEST-com.oneshop.CheckoutWebIntegrationTest.xml |
| baseline-0319 | com.oneshop.config.V3SchemaCompatibilityVerifierTest#refusesWrongFilterAndColumns | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.config.V3SchemaCompatibilityVerifierTest.xml |
| baseline-0320 | com.oneshop.config.V3SchemaCompatibilityVerifierTest#refusesDisabledOrNonUniqueKeys | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.config.V3SchemaCompatibilityVerifierTest.xml |
| baseline-0321 | com.oneshop.config.V3SchemaCompatibilityVerifierTest#refusesMissingMigration | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.config.V3SchemaCompatibilityVerifierTest.xml |
| baseline-0322 | com.oneshop.config.V3SchemaCompatibilityVerifierTest#acceptsCanonicalReviewKeys | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.config.V3SchemaCompatibilityVerifierTest.xml |
| baseline-0323 | com.oneshop.config.V3SchemaCompatibilityVerifierTest#refusesRetainedObsoleteGlobalKey | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.config.V3SchemaCompatibilityVerifierTest.xml |
| baseline-0324 | com.oneshop.controller.client.AccountControllerTest#testUpdateProfileCannotChangeUserName | PASS | 0.155 | None | target/surefire-reports/TEST-com.oneshop.controller.client.AccountControllerTest.xml |
| baseline-0325 | com.oneshop.controller.client.AccountControllerTest#testUpdateProfileSuccess | PASS | 0.006 | None | target/surefire-reports/TEST-com.oneshop.controller.client.AccountControllerTest.xml |
| baseline-0326 | com.oneshop.controller.client.AccountControllerTest#testUpdateProfileBlankNameError | PASS | 0.009 | None | target/surefire-reports/TEST-com.oneshop.controller.client.AccountControllerTest.xml |
| baseline-0327 | com.oneshop.controller.client.AccountControllerTest#testGetProfile | PASS | 0.003 | None | target/surefire-reports/TEST-com.oneshop.controller.client.AccountControllerTest.xml |
| baseline-0328 | com.oneshop.DatabaseFoundationIntegrationTest#ordersCheckoutAndPaymentsAreMappedPerOrder | PASS | 0.004 | None | target/surefire-reports/TEST-com.oneshop.DatabaseFoundationIntegrationTest.xml |
| baseline-0329 | com.oneshop.DatabaseFoundationIntegrationTest#oneSkuIsSoldAtSeveralStoresWithItsOwnPriceAndQuantity | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.DatabaseFoundationIntegrationTest.xml |
| baseline-0330 | com.oneshop.DatabaseFoundationIntegrationTest#storeAndOwnerScopedQueriesNeverCrossTheBoundary | PASS | 0.016 | None | target/surefire-reports/TEST-com.oneshop.DatabaseFoundationIntegrationTest.xml |
| baseline-0331 | com.oneshop.DatabaseFoundationIntegrationTest#seedDataIsReadThroughJpa | PASS | 0.009 | None | target/surefire-reports/TEST-com.oneshop.DatabaseFoundationIntegrationTest.xml |
| baseline-0332 | com.oneshop.DatabaseFoundationIntegrationTest#all23TablesAreMappedAndEveryRepositoryCanReadItsSeedData | PASS | 0.017 | None | target/surefire-reports/TEST-com.oneshop.DatabaseFoundationIntegrationTest.xml |
| baseline-0333 | com.oneshop.DatabaseFoundationIntegrationTest#storeProductRowsAreLockedWithPessimisticWrite | PASS | 2.033 | None | target/surefire-reports/TEST-com.oneshop.DatabaseFoundationIntegrationTest.xml |
| baseline-0334 | com.oneshop.DatabaseV3RegressionIntegrationTest#all23TablesHaveTrustedConstraintsAndHibernateOnlyValidates | PASS | 0.403 | None | target/surefire-reports/TEST-com.oneshop.DatabaseV3RegressionIntegrationTest.xml |
| baseline-0335 | com.oneshop.DatabaseV3RegressionIntegrationTest#approvedReviewMigrationIsIdempotentAndPreservesEveryTable | PASS | 0.686 | None | target/surefire-reports/TEST-com.oneshop.DatabaseV3RegressionIntegrationTest.xml |
| baseline-0336 | com.oneshop.DatabaseV3RegressionIntegrationTest#noOrphansInvalidSourcesNegativeStockOrRepeatedRestoration | PASS | 0.079 | None | target/surefire-reports/TEST-com.oneshop.DatabaseV3RegressionIntegrationTest.xml |
| baseline-0337 | com.oneshop.DeliveryDatabaseIntegrationTest#inconsistentOnlineUnpaidAtAnyStageIsRejected(DeliveryAction)[1] | PASS | 0.168 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0338 | com.oneshop.DeliveryDatabaseIntegrationTest#inconsistentOnlineUnpaidAtAnyStageIsRejected(DeliveryAction)[2] | PASS | 0.104 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0339 | com.oneshop.DeliveryDatabaseIntegrationTest#inconsistentOnlineUnpaidAtAnyStageIsRejected(DeliveryAction)[3] | PASS | 0.11 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0340 | com.oneshop.DeliveryDatabaseIntegrationTest#inconsistentOnlineUnpaidAtAnyStageIsRejected(DeliveryAction)[4] | PASS | 0.112 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0341 | com.oneshop.DeliveryDatabaseIntegrationTest#codPaidBeforeCompletionCannotBeFulfilledOrCollectedAgain(DeliveryAction)[1] | PASS | 0.095 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0342 | com.oneshop.DeliveryDatabaseIntegrationTest#codPaidBeforeCompletionCannotBeFulfilledOrCollectedAgain(DeliveryAction)[2] | PASS | 0.095 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0343 | com.oneshop.DeliveryDatabaseIntegrationTest#codPaidBeforeCompletionCannotBeFulfilledOrCollectedAgain(DeliveryAction)[3] | PASS | 0.112 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0344 | com.oneshop.DeliveryDatabaseIntegrationTest#codPaidBeforeCompletionCannotBeFulfilledOrCollectedAgain(DeliveryAction)[4] | PASS | 0.142 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0345 | com.oneshop.DeliveryDatabaseIntegrationTest#onlinePendingCannotBeFulfilledOrConfirmedByStaff(DeliveryAction)[1] | PASS | 0.104 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0346 | com.oneshop.DeliveryDatabaseIntegrationTest#onlinePendingCannotBeFulfilledOrConfirmedByStaff(DeliveryAction)[2] | PASS | 0.112 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0347 | com.oneshop.DeliveryDatabaseIntegrationTest#onlinePendingCannotBeFulfilledOrConfirmedByStaff(DeliveryAction)[3] | PASS | 0.11 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0348 | com.oneshop.DeliveryDatabaseIntegrationTest#onlinePendingCannotBeFulfilledOrConfirmedByStaff(DeliveryAction)[4] | PASS | 0.097 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0349 | com.oneshop.DeliveryDatabaseIntegrationTest#inactiveAssignmentStoreOrStaffRevokesReadAndMutationScope(String)[1] | PASS | 0.098 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0350 | com.oneshop.DeliveryDatabaseIntegrationTest#inactiveAssignmentStoreOrStaffRevokesReadAndMutationScope(String)[2] | PASS | 0.092 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0351 | com.oneshop.DeliveryDatabaseIntegrationTest#inactiveAssignmentStoreOrStaffRevokesReadAndMutationScope(String)[3] | PASS | 0.099 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0352 | com.oneshop.DeliveryDatabaseIntegrationTest#readScopeSupportsMultipleAssignmentsAndOnlyDeliverySnapshots | ERROR | 0.092 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0353 | com.oneshop.DeliveryDatabaseIntegrationTest#realSqlServerConcurrentRequestsProduceOneTransitionAndAtMostOneCollection(DeliveryAction, int)[1] | PASS | 0.129 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0354 | com.oneshop.DeliveryDatabaseIntegrationTest#realSqlServerConcurrentRequestsProduceOneTransitionAndAtMostOneCollection(DeliveryAction, int)[2] | PASS | 0.118 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0355 | com.oneshop.DeliveryDatabaseIntegrationTest#realSqlServerConcurrentRequestsProduceOneTransitionAndAtMostOneCollection(DeliveryAction, int)[3] | PASS | 0.132 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0356 | com.oneshop.DeliveryDatabaseIntegrationTest#realSqlServerConcurrentRequestsProduceOneTransitionAndAtMostOneCollection(DeliveryAction, int)[4] | PASS | 0.111 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0357 | com.oneshop.DeliveryDatabaseIntegrationTest#realSqlServerConcurrentRequestsProduceOneTransitionAndAtMostOneCollection(DeliveryAction, int)[5] | PASS | 0.101 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0358 | com.oneshop.DeliveryDatabaseIntegrationTest#realSqlServerConcurrentRequestsProduceOneTransitionAndAtMostOneCollection(DeliveryAction, int)[6] | PASS | 0.106 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0359 | com.oneshop.DeliveryDatabaseIntegrationTest#realSqlServerConcurrentRequestsProduceOneTransitionAndAtMostOneCollection(DeliveryAction, int)[7] | PASS | 0.129 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0360 | com.oneshop.DeliveryDatabaseIntegrationTest#realSqlServerConcurrentRequestsProduceOneTransitionAndAtMostOneCollection(DeliveryAction, int)[8] | PASS | 0.113 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0361 | com.oneshop.DeliveryDatabaseIntegrationTest#realSqlServerConcurrentRequestsProduceOneTransitionAndAtMostOneCollection(DeliveryAction, int)[9] | PASS | 0.102 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0362 | com.oneshop.DeliveryDatabaseIntegrationTest#onlineDeliveryNeverRewritesItsReceiptAndKeepsInventoryAndItemSnapshots | PASS | 0.14 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0363 | com.oneshop.DeliveryDatabaseIntegrationTest#storeScopeIsRecheckedInServiceForEveryAction(DeliveryAction)[1] | PASS | 0.095 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0364 | com.oneshop.DeliveryDatabaseIntegrationTest#storeScopeIsRecheckedInServiceForEveryAction(DeliveryAction)[2] | PASS | 0.091 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0365 | com.oneshop.DeliveryDatabaseIntegrationTest#storeScopeIsRecheckedInServiceForEveryAction(DeliveryAction)[3] | PASS | 0.094 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0366 | com.oneshop.DeliveryDatabaseIntegrationTest#storeScopeIsRecheckedInServiceForEveryAction(DeliveryAction)[4] | PASS | 0.12 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0367 | com.oneshop.DeliveryDatabaseIntegrationTest#tc09CodLifecyclePreservesInventorySnapshotsAndCreatesPaymentOnlyAtCompletion | PASS | 0.118 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0368 | com.oneshop.DeliveryDatabaseIntegrationTest#pickupOrderCannotUseAnyDeliveryAction(DeliveryAction)[1] | PASS | 0.28 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0369 | com.oneshop.DeliveryDatabaseIntegrationTest#pickupOrderCannotUseAnyDeliveryAction(DeliveryAction)[2] | PASS | 0.09 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0370 | com.oneshop.DeliveryDatabaseIntegrationTest#pickupOrderCannotUseAnyDeliveryAction(DeliveryAction)[3] | PASS | 0.079 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0371 | com.oneshop.DeliveryDatabaseIntegrationTest#pickupOrderCannotUseAnyDeliveryAction(DeliveryAction)[4] | PASS | 0.081 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0372 | com.oneshop.DeliveryDatabaseIntegrationTest#codCollectionHelperRequiresSurroundingTransaction | PASS | 0.078 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0373 | com.oneshop.DeliveryDatabaseIntegrationTest#inconsistentSuccessfulCodReceiptCannotBeCollectedAgain | PASS | 0.105 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0374 | com.oneshop.DeliveryDatabaseIntegrationTest#codCompletionRollsBackEveryWriteAtAllFailurePoints(String)[1] | PASS | 0.111 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0375 | com.oneshop.DeliveryDatabaseIntegrationTest#codCompletionRollsBackEveryWriteAtAllFailurePoints(String)[2] | PASS | 0.099 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0376 | com.oneshop.DeliveryDatabaseIntegrationTest#codCompletionRollsBackEveryWriteAtAllFailurePoints(String)[3] | PASS | 0.109 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0377 | com.oneshop.DeliveryDatabaseIntegrationTest#codCompletionRollsBackEveryWriteAtAllFailurePoints(String)[4] | PASS | 0.124 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0378 | com.oneshop.DeliveryDatabaseIntegrationTest#codCompletionRollsBackEveryWriteAtAllFailurePoints(String)[5] | PASS | 0.105 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0379 | com.oneshop.DeliveryDatabaseIntegrationTest#detailAndCodCollectionUsePurchaseSnapshotsAfterCatalogNameAndPriceChange | PASS | 0.143 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0380 | com.oneshop.DeliveryDatabaseIntegrationTest#realHttpStaffCanRunFullFlowWithSnapshotsAndForgedFieldsIgnored(PaymentMethod)[1] | PASS | 0.395 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0381 | com.oneshop.DeliveryDatabaseIntegrationTest#realHttpStaffCanRunFullFlowWithSnapshotsAndForgedFieldsIgnored(PaymentMethod)[2] | PASS | 0.321 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0382 | com.oneshop.DeliveryDatabaseIntegrationTest#cancelledOnlineOrderCannotRunAnyDeliveryAction | PASS | 0.131 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0383 | com.oneshop.DeliveryDatabaseIntegrationTest#realHttpRejectsCrossStorePickupPendingMissingCsrfAndGetMutations | PASS | 0.245 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0384 | com.oneshop.DeliveryDatabaseIntegrationTest#skipBackwardAndDuplicateActionsCannotWrite(OrderStatus, DeliveryAction)[1] | PASS | 0.094 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0385 | com.oneshop.DeliveryDatabaseIntegrationTest#skipBackwardAndDuplicateActionsCannotWrite(OrderStatus, DeliveryAction)[2] | PASS | 0.099 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0386 | com.oneshop.DeliveryDatabaseIntegrationTest#skipBackwardAndDuplicateActionsCannotWrite(OrderStatus, DeliveryAction)[3] | PASS | 0.095 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0387 | com.oneshop.DeliveryDatabaseIntegrationTest#skipBackwardAndDuplicateActionsCannotWrite(OrderStatus, DeliveryAction)[4] | PASS | 0.097 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0388 | com.oneshop.DeliveryDatabaseIntegrationTest#skipBackwardAndDuplicateActionsCannotWrite(OrderStatus, DeliveryAction)[5] | PASS | 0.109 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0389 | com.oneshop.DeliveryDatabaseIntegrationTest#skipBackwardAndDuplicateActionsCannotWrite(OrderStatus, DeliveryAction)[6] | PASS | 0.098 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0390 | com.oneshop.DeliveryDatabaseIntegrationTest#skipBackwardAndDuplicateActionsCannotWrite(OrderStatus, DeliveryAction)[7] | PASS | 0.101 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0391 | com.oneshop.DeliveryDatabaseIntegrationTest#skipBackwardAndDuplicateActionsCannotWrite(OrderStatus, DeliveryAction)[8] | PASS | 0.097 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0392 | com.oneshop.DeliveryDatabaseIntegrationTest#skipBackwardAndDuplicateActionsCannotWrite(OrderStatus, DeliveryAction)[9] | PASS | 0.092 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0393 | com.oneshop.DeliveryDatabaseIntegrationTest#skipBackwardAndDuplicateActionsCannotWrite(OrderStatus, DeliveryAction)[10] | PASS | 0.1 | None | target/surefire-reports/TEST-com.oneshop.DeliveryDatabaseIntegrationTest.xml |
| baseline-0394 | com.oneshop.DeliveryWebIntegrationTest#customerAdminGuestAndStaffWithoutAssignmentCannotReadOrMutate | PASS | 0.176 | None | target/surefire-reports/TEST-com.oneshop.DeliveryWebIntegrationTest.xml |
| baseline-0395 | com.oneshop.DeliveryWebIntegrationTest#everyWriteRequiresCsrfEvenWithBearer | PASS | 0.044 | None | target/surefire-reports/TEST-com.oneshop.DeliveryWebIntegrationTest.xml |
| baseline-0396 | com.oneshop.FulfillmentConcurrencyDatabaseIntegrationTest#be04_50_cancelAgainstPreparing | PASS | 0.302 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentConcurrencyDatabaseIntegrationTest.xml |
| baseline-0397 | com.oneshop.FulfillmentConcurrencyDatabaseIntegrationTest#be04_56_rejectedStepLeavesOthersAlone | PASS | 0.096 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentConcurrencyDatabaseIntegrationTest.xml |
| baseline-0398 | com.oneshop.FulfillmentConcurrencyDatabaseIntegrationTest#be04_55_pickupCollectionFailureRollsBack | PASS | 0.073 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentConcurrencyDatabaseIntegrationTest.xml |
| baseline-0399 | com.oneshop.FulfillmentConcurrencyDatabaseIntegrationTest#be04_52_lastTwoPartsCompleteTogether | PASS | 0.287 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentConcurrencyDatabaseIntegrationTest.xml |
| baseline-0400 | com.oneshop.FulfillmentConcurrencyDatabaseIntegrationTest#be04_49_doubleClick | PASS | 0.118 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentConcurrencyDatabaseIntegrationTest.xml |
| baseline-0401 | com.oneshop.FulfillmentConcurrencyDatabaseIntegrationTest#be04_51_twoStoresAtOnce | PASS | 0.178 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentConcurrencyDatabaseIntegrationTest.xml |
| baseline-0402 | com.oneshop.FulfillmentConcurrencyDatabaseIntegrationTest#be04_54_aggregationFailureRollsBack | PASS | 0.064 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentConcurrencyDatabaseIntegrationTest.xml |
| baseline-0403 | com.oneshop.FulfillmentConcurrencyDatabaseIntegrationTest#be04_53_collectionFailureRollsBack | PASS | 0.076 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentConcurrencyDatabaseIntegrationTest.xml |
| baseline-0404 | com.oneshop.FulfillmentExceptionDatabaseIntegrationTest#undeliverableLostOrUnconfirmed | PASS | 0.11 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentExceptionDatabaseIntegrationTest.xml |
| baseline-0405 | com.oneshop.FulfillmentExceptionDatabaseIntegrationTest#onlyAdministrators | PASS | 0.079 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentExceptionDatabaseIntegrationTest.xml |
| baseline-0406 | com.oneshop.FulfillmentExceptionDatabaseIntegrationTest#completedPartIsNeverTouched | PASS | 0.123 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentExceptionDatabaseIntegrationTest.xml |
| baseline-0407 | com.oneshop.FulfillmentExceptionDatabaseIntegrationTest#dashboardFollowsTheDecision | PASS | 0.186 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentExceptionDatabaseIntegrationTest.xml |
| baseline-0408 | com.oneshop.FulfillmentExceptionDatabaseIntegrationTest#undeliverableReturnedCod | PASS | 0.097 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentExceptionDatabaseIntegrationTest.xml |
| baseline-0409 | com.oneshop.FulfillmentExceptionDatabaseIntegrationTest#redeliverPickup | PASS | 0.087 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentExceptionDatabaseIntegrationTest.xml |
| baseline-0410 | com.oneshop.FulfillmentExceptionDatabaseIntegrationTest#undeliverableOnlinePaid | PASS | 0.088 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentExceptionDatabaseIntegrationTest.xml |
| baseline-0411 | com.oneshop.FulfillmentExceptionDatabaseIntegrationTest#concurrentDifferentParts | PASS | 0.157 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentExceptionDatabaseIntegrationTest.xml |
| baseline-0412 | com.oneshop.FulfillmentExceptionDatabaseIntegrationTest#concurrentSameDecision | PASS | 0.086 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentExceptionDatabaseIntegrationTest.xml |
| baseline-0413 | com.oneshop.FulfillmentExceptionDatabaseIntegrationTest#redeliverValidation | PASS | 0.102 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentExceptionDatabaseIntegrationTest.xml |
| baseline-0414 | com.oneshop.FulfillmentExceptionDatabaseIntegrationTest#undeliverableValidation | PASS | 0.097 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentExceptionDatabaseIntegrationTest.xml |
| baseline-0415 | com.oneshop.FulfillmentExceptionDatabaseIntegrationTest#paymentAttentionIsNotADeliveryFailure | PASS | 0.064 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentExceptionDatabaseIntegrationTest.xml |
| baseline-0416 | com.oneshop.FulfillmentExceptionDatabaseIntegrationTest#twoFailedParts | PASS | 0.152 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentExceptionDatabaseIntegrationTest.xml |
| baseline-0417 | com.oneshop.FulfillmentExceptionDatabaseIntegrationTest#redeliverDelivery | PASS | 0.119 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentExceptionDatabaseIntegrationTest.xml |
| baseline-0418 | com.oneshop.FulfillmentExceptionDatabaseIntegrationTest#failedCodDeliveryIsVisibleToAdmin | PASS | 0.109 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentExceptionDatabaseIntegrationTest.xml |
| baseline-0419 | com.oneshop.FulfillmentExceptionDatabaseIntegrationTest#repeatedDecisionIsIdempotent | PASS | 0.099 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentExceptionDatabaseIntegrationTest.xml |
| baseline-0420 | com.oneshop.FulfillmentExceptionDatabaseIntegrationTest#mixedOrderRefundsOnlyThePart | PASS | 0.171 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentExceptionDatabaseIntegrationTest.xml |
| baseline-0421 | com.oneshop.FulfillmentExceptionDatabaseIntegrationTest#undeliverablePayAtStore | PASS | 0.079 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentExceptionDatabaseIntegrationTest.xml |
| baseline-0422 | com.oneshop.FulfillmentExceptionDatabaseIntegrationTest#concurrentOpposingDecisions | PASS | 0.079 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentExceptionDatabaseIntegrationTest.xml |
| baseline-0423 | com.oneshop.FulfillmentExceptionDatabaseIntegrationTest#redeliverOneOfTwo | PASS | 0.149 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentExceptionDatabaseIntegrationTest.xml |
| baseline-0424 | com.oneshop.FulfillmentExceptionHttpDatabaseIntegrationTest#undeliverableOverHttp | PASS | 0.568 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentExceptionHttpDatabaseIntegrationTest.xml |
| baseline-0425 | com.oneshop.FulfillmentExceptionHttpDatabaseIntegrationTest#redeliverOverHttp | PASS | 0.308 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentExceptionHttpDatabaseIntegrationTest.xml |
| baseline-0426 | com.oneshop.FulfillmentExceptionHttpDatabaseIntegrationTest#adminSeesTheException | PASS | 0.315 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentExceptionHttpDatabaseIntegrationTest.xml |
| baseline-0427 | com.oneshop.FulfillmentExceptionHttpDatabaseIntegrationTest#accessControl | PASS | 0.426 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentExceptionHttpDatabaseIntegrationTest.xml |
| baseline-0428 | com.oneshop.FulfillmentExceptionHttpDatabaseIntegrationTest#wrongOrderAndHealthyOrder | PASS | 0.287 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentExceptionHttpDatabaseIntegrationTest.xml |
| baseline-0429 | com.oneshop.FulfillmentHttpDatabaseIntegrationTest#be04_62_pickupOverHttp | PASS | 0.419 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentHttpDatabaseIntegrationTest.xml |
| baseline-0430 | com.oneshop.FulfillmentHttpDatabaseIntegrationTest#be04_63_legacyUrls | PASS | 0.352 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentHttpDatabaseIntegrationTest.xml |
| baseline-0431 | com.oneshop.FulfillmentHttpDatabaseIntegrationTest#be04_58_detailAuthorization | PASS | 0.382 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentHttpDatabaseIntegrationTest.xml |
| baseline-0432 | com.oneshop.FulfillmentHttpDatabaseIntegrationTest#be04_65_customerOrderPage | PASS | 0.475 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentHttpDatabaseIntegrationTest.xml |
| baseline-0433 | com.oneshop.FulfillmentHttpDatabaseIntegrationTest#be04_57_queuePage | PASS | 0.298 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentHttpDatabaseIntegrationTest.xml |
| baseline-0434 | com.oneshop.FulfillmentHttpDatabaseIntegrationTest#be04_61_conflictStatus | PASS | 0.3 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentHttpDatabaseIntegrationTest.xml |
| baseline-0435 | com.oneshop.FulfillmentHttpDatabaseIntegrationTest#be04_60_postRefused | PASS | 0.397 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentHttpDatabaseIntegrationTest.xml |
| baseline-0436 | com.oneshop.FulfillmentHttpDatabaseIntegrationTest#be04_67_handover | PASS | 0.648 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentHttpDatabaseIntegrationTest.xml |
| baseline-0437 | com.oneshop.FulfillmentHttpDatabaseIntegrationTest#be04_64_dashboard | PASS | 0.208 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentHttpDatabaseIntegrationTest.xml |
| baseline-0438 | com.oneshop.FulfillmentHttpDatabaseIntegrationTest#be04_59_postStepIgnoresInjectedFields | PASS | 0.19 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentHttpDatabaseIntegrationTest.xml |
| baseline-0439 | com.oneshop.FulfillmentHttpDatabaseIntegrationTest#be04_66_multiStoreCustomerPage | PASS | 0.314 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentHttpDatabaseIntegrationTest.xml |
| baseline-0440 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_01_queueIsScopedToTheStaffStore | PASS | 0.096 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0441 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_16_codCollectedOnlyAtCompletion | PASS | 0.086 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0442 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_17_failureNeedsReason | PASS | 0.079 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0443 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_42_noStockChangeInPickup | PASS | 0.068 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0444 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_27_onlinePickup | PASS | 0.065 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0445 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_12_noSkipping | PASS | 0.068 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0446 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_38_failedBesideCompleted | PASS | 0.1 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0447 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_02_otherStoreDetailIsForbidden | PASS | 0.056 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0448 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_18_failedOnlyNeedsAttention | PASS | 0.064 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0449 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_10_legacyOrderLevelPathIsClosed | PASS | 0.063 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0450 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_48_detailOnlyOwnLines | PASS | 0.064 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0451 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_30_codesAreIndependent | PASS | 0.089 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0452 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_05_customerIsRefused | PASS | 0.065 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0453 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_21_payAtStoreHappyPath | PASS | 0.062 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0454 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_28_pickupOrder | PASS | 0.064 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0455 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_44_cancelBeforePreparing | PASS | 0.064 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0456 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_08_twoAssignments | ERROR | 0.081 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0457 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_13_noRepeat | PASS | 0.064 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0458 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_15_trackingCode | PASS | 0.079 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0459 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_03_otherStoreCannotAct | PASS | 0.054 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0460 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_41_noStockChangeInLifecycle | PASS | 0.072 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0461 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_47_dashboardIsScoped | PASS | 0.064 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0462 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_31_pendingPaymentIsBlocked | PASS | 0.08 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0463 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_45_queueFilters | PASS | 0.143 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0464 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_39_orderHistoryOnlyOnChange | PASS | 0.079 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0465 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_04_unknownFulfillment | PASS | 0.035 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0466 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_29_pickupOnlyStore | PASS | 0.063 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0467 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_07_storeFilterCannotWiden | PASS | 0.086 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0468 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_25_staffNeverSeesTheCode | PASS | 0.074 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0469 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_22_wrongCode | PASS | 0.082 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0470 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_35_onlineNotPaidIsHeld | PASS | 0.056 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0471 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_37_partialThenComplete | PASS | 0.1 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0472 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_46_dashboardCounts | PASS | 0.124 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0473 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_06_inactiveAssignment | PASS | 0.059 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0474 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_36_oneStorePreparing | PASS | 0.081 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0475 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_20_history | PASS | 0.069 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0476 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_23_codeNormalized | PASS | 0.067 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0477 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_32_needsAttentionIsHeld | PASS | 0.063 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0478 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_14_wrongType | PASS | 0.087 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0479 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_09_ordersStoreIdIsNotAuthority | PASS | 0.06 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0480 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_11_codDeliveryHappyPath | PASS | 0.09 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0481 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_34_paidOnlineIsWorkable | PASS | 0.073 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0482 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_40_noOrderStatusInput | PASS | 0.025 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0483 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_68_cancelNeedsAttentionIsRefused | PASS | 0.076 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0484 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_26_customerSeesTheCode | PASS | 0.081 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0485 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_24_attemptLimit | PASS | 0.083 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0486 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_33_cancelledIsHeld | PASS | 0.063 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0487 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_43_cancelAfterPreparingIsRefused | PASS | 0.063 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0488 | com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_19_failedIsTerminalAndNotCleared | PASS | 0.097 | None | target/surefire-reports/TEST-com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-0489 | com.oneshop.HomePageWebIntegrationTest#aGuestIsSentToLoginInsteadOfGettingAnAddToCartForm | PASS | 0.021 | None | target/surefire-reports/TEST-com.oneshop.HomePageWebIntegrationTest.xml |
| baseline-0490 | com.oneshop.HomePageWebIntegrationTest#chipRowIsHiddenWithoutCategoriesAndBrands | PASS | 0.007 | None | target/surefire-reports/TEST-com.oneshop.HomePageWebIntegrationTest.xml |
| baseline-0491 | com.oneshop.HomePageWebIntegrationTest#atMostThreeCategoryAndThreeBrandChips | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.HomePageWebIntegrationTest.xml |
| baseline-0492 | com.oneshop.HomePageWebIntegrationTest#categoryTilesUseTheBundledPhotoOrANeutralTile | PASS | 0.022 | None | target/surefire-reports/TEST-com.oneshop.HomePageWebIntegrationTest.xml |
| baseline-0493 | com.oneshop.HomePageWebIntegrationTest#cardsAreOneLinkWithTheSharedPriceAndAChainWideStockFlag | PASS | 0.009 | None | target/surefire-reports/TEST-com.oneshop.HomePageWebIntegrationTest.xml |
| baseline-0494 | com.oneshop.HomePageWebIntegrationTest#imagesBelowTheFoldAreLazyAndOnlyTheHeroIsEager | PASS | 0.016 | None | target/surefire-reports/TEST-com.oneshop.HomePageWebIntegrationTest.xml |
| baseline-0495 | com.oneshop.HomePageWebIntegrationTest#aCustomerAddsFromACardByProductIdWithoutAnyStore | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.HomePageWebIntegrationTest.xml |
| baseline-0496 | com.oneshop.HomePageWebIntegrationTest#brandTilesAreTextOnlyLinksWithProductCounts | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.HomePageWebIntegrationTest.xml |
| baseline-0497 | com.oneshop.HomePageWebIntegrationTest#headerAndFooterNoLongerHardCodeTheHotline | PASS | 0.016 | None | target/surefire-reports/TEST-com.oneshop.HomePageWebIntegrationTest.xml |
| baseline-0498 | com.oneshop.HomePageWebIntegrationTest#theSeedIsStableWithinTheHour | PASS | 0.02 | None | target/surefire-reports/TEST-com.oneshop.HomePageWebIntegrationTest.xml |
| baseline-0499 | com.oneshop.HomePageWebIntegrationTest#categoryLayoutFollowsTheNumberOfCategories | PASS | 0.019 | None | target/surefire-reports/TEST-com.oneshop.HomePageWebIntegrationTest.xml |
| baseline-0500 | com.oneshop.HomePageWebIntegrationTest#sectionsWithoutDataAreHidden | PASS | 0.018 | None | target/surefire-reports/TEST-com.oneshop.HomePageWebIntegrationTest.xml |
| baseline-0501 | com.oneshop.HomePageWebIntegrationTest#storesSectionIsALookupWithoutAnyStorePicker | PASS | 0.008 | None | target/surefire-reports/TEST-com.oneshop.HomePageWebIntegrationTest.xml |
| baseline-0502 | com.oneshop.HomePageWebIntegrationTest#productsSectionIsHiddenWithoutProducts | PASS | 0.008 | None | target/surefire-reports/TEST-com.oneshop.HomePageWebIntegrationTest.xml |
| baseline-0503 | com.oneshop.HomePageWebIntegrationTest#categoryTileTransformsCloudinaryUrlAndRendersDynamically | PASS | 0.006 | None | target/surefire-reports/TEST-com.oneshop.HomePageWebIntegrationTest.xml |
| baseline-0504 | com.oneshop.HomePageWebIntegrationTest#moreThanSixBrandsBecomeAScrollingRow | PASS | 0.018 | None | target/surefire-reports/TEST-com.oneshop.HomePageWebIntegrationTest.xml |
| baseline-0505 | com.oneshop.HomePageWebIntegrationTest#suggestionChipsAreRealCategoriesAndBrandsAndThereIsNoStoreScopeLine | PASS | 0.012 | None | target/surefire-reports/TEST-com.oneshop.HomePageWebIntegrationTest.xml |
| baseline-0506 | com.oneshop.HomePageWebIntegrationTest#theRevealScriptIsLoadedAsAFileAndPageStylesAreNotInline | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.HomePageWebIntegrationTest.xml |
| baseline-0507 | com.oneshop.HomePageWebIntegrationTest#searchBarIsTheOnlyMainActionAndPostsToTheCatalogSearch | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.HomePageWebIntegrationTest.xml |
| baseline-0508 | com.oneshop.HomePageWebIntegrationTest#everyImageHasAltWidthAndHeight | PASS | 0.016 | None | target/surefire-reports/TEST-com.oneshop.HomePageWebIntegrationTest.xml |
| baseline-0509 | com.oneshop.HomePageWebIntegrationTest#noStockQuantityIsPrintedAnywhere | PASS | 0.024 | None | target/surefire-reports/TEST-com.oneshop.HomePageWebIntegrationTest.xml |
| baseline-0510 | com.oneshop.ImageCreditsPageIntegrationTest#theCreditsPageIsPublicAndNamesSourceAuthorAndLicence | PASS | 0.01 | None | target/surefire-reports/TEST-com.oneshop.ImageCreditsPageIntegrationTest.xml |
| baseline-0511 | com.oneshop.ImageCreditsPageIntegrationTest#everyPageLinksToTheCredits | PASS | 0.012 | None | target/surefire-reports/TEST-com.oneshop.ImageCreditsPageIntegrationTest.xml |
| baseline-0512 | com.oneshop.JpaV3ReadIntegrityTest#orderAndFulfillmentIntegrity | PASS | 0.013 | None | target/surefire-reports/TEST-com.oneshop.JpaV3ReadIntegrityTest.xml |
| baseline-0513 | com.oneshop.JpaV3ReadIntegrityTest#reviewAndReplyIntegrity | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.JpaV3ReadIntegrityTest.xml |
| baseline-0514 | com.oneshop.JpaV3ReadIntegrityTest#paymentRefundMappingIntegrity | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.JpaV3ReadIntegrityTest.xml |
| baseline-0515 | com.oneshop.JpaV3ReadIntegrityTest#fulfillmentStatusHistoryIntegrity | PASS | 0.016 | None | target/surefire-reports/TEST-com.oneshop.JpaV3ReadIntegrityTest.xml |
| baseline-0516 | com.oneshop.JpaV3ReadIntegrityTest#storeProductStockMappingIntegrity | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.JpaV3ReadIntegrityTest.xml |
| baseline-0517 | com.oneshop.JpaV3ReadIntegrityTest#orderItemFulfillmentIntegrity | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.JpaV3ReadIntegrityTest.xml |
| baseline-0518 | com.oneshop.JpaV3ReadIntegrityTest#all23EntitiesAndRepositoriesExist | PASS | 0.016 | None | target/surefire-reports/TEST-com.oneshop.JpaV3ReadIntegrityTest.xml |
| baseline-0519 | com.oneshop.JpaV3ReadIntegrityTest#cartItemMappingIntegrity | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.JpaV3ReadIntegrityTest.xml |
| baseline-0520 | com.oneshop.JpaV3ReadIntegrityTest#productPriceMappingIntegrity | PASS | 0.016 | None | target/surefire-reports/TEST-com.oneshop.JpaV3ReadIntegrityTest.xml |
| baseline-0521 | com.oneshop.LayoutsIntegrationTest#adminAreaUsesTheAdminLayoutWithChainWideScope | PASS | 0.01 | None | target/surefire-reports/TEST-com.oneshop.LayoutsIntegrationTest.xml |
| baseline-0522 | com.oneshop.LayoutsIntegrationTest#aChosenStoreNoLongerChangesTheClientLayout | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.LayoutsIntegrationTest.xml |
| baseline-0523 | com.oneshop.LayoutsIntegrationTest#staffMenuMarksCurrentPageAndEnablesCompletedStockAndHistoryPages | PASS | 0.025 | None | target/surefire-reports/TEST-com.oneshop.LayoutsIntegrationTest.xml |
| baseline-0524 | com.oneshop.LayoutsIntegrationTest#staffAreaUsesTheStaffLayout | PASS | 0.013 | None | target/surefire-reports/TEST-com.oneshop.LayoutsIntegrationTest.xml |
| baseline-0525 | com.oneshop.LayoutsIntegrationTest#layoutsAreResponsive | PASS | 0.026 | None | target/surefire-reports/TEST-com.oneshop.LayoutsIntegrationTest.xml |
| baseline-0526 | com.oneshop.LayoutsIntegrationTest#adminOverviewUsesSharedComponentsAndEnablesChainOperations | PASS | 0.015 | None | target/surefire-reports/TEST-com.oneshop.LayoutsIntegrationTest.xml |
| baseline-0527 | com.oneshop.LayoutsIntegrationTest#clientLayoutHasNoStoreContextInV3 | PASS | 0.007 | None | target/surefire-reports/TEST-com.oneshop.LayoutsIntegrationTest.xml |
| baseline-0528 | com.oneshop.LayoutsIntegrationTest#staffLayoutShowsTheAssignedStore | PASS | 0.004 | None | target/surefire-reports/TEST-com.oneshop.LayoutsIntegrationTest.xml |
| baseline-0529 | com.oneshop.LayoutsIntegrationTest#staffAndAdminAreasKeepTheirAccessRules | PASS | 0.009 | None | target/surefire-reports/TEST-com.oneshop.LayoutsIntegrationTest.xml |
| baseline-0530 | com.oneshop.MaxUploadSizeExceededIntegrationTest#handlerRedirectsToCategoryListWhenAddingNewCategory | PASS | 0.08 | None | target/surefire-reports/TEST-com.oneshop.MaxUploadSizeExceededIntegrationTest.xml |
| baseline-0531 | com.oneshop.MaxUploadSizeExceededIntegrationTest#productImageFormRendersVietnameseSizeErrorAndPreservesData | PASS | 0.08 | None | target/surefire-reports/TEST-com.oneshop.MaxUploadSizeExceededIntegrationTest.xml |
| baseline-0532 | com.oneshop.MaxUploadSizeExceededIntegrationTest#brandFormRendersVietnameseSizeErrorAndPreservesData | PASS | 0.096 | None | target/surefire-reports/TEST-com.oneshop.MaxUploadSizeExceededIntegrationTest.xml |
| baseline-0533 | com.oneshop.MaxUploadSizeExceededIntegrationTest#handlerRedirectsToProductFormWithVietnameseMessage | PASS | 0.068 | None | target/surefire-reports/TEST-com.oneshop.MaxUploadSizeExceededIntegrationTest.xml |
| baseline-0534 | com.oneshop.MaxUploadSizeExceededIntegrationTest#handlerRedirectsToCategoryFormWithVietnameseMessage | PASS | 0.079 | None | target/surefire-reports/TEST-com.oneshop.MaxUploadSizeExceededIntegrationTest.xml |
| baseline-0535 | com.oneshop.MaxUploadSizeExceededIntegrationTest#categoryFormRendersVietnameseSizeErrorAndPreservesData | PASS | 0.083 | None | target/surefire-reports/TEST-com.oneshop.MaxUploadSizeExceededIntegrationTest.xml |
| baseline-0536 | com.oneshop.MaxUploadSizeExceededIntegrationTest#handlerRedirectsToBrandFormWithVietnameseMessage | PASS | 0.085 | None | target/surefire-reports/TEST-com.oneshop.MaxUploadSizeExceededIntegrationTest.xml |
| baseline-0537 | com.oneshop.MaxUtf8BytesValidationTest#multiByteCharactersCountAsTheirUtf8Bytes | PASS | 0.006 | None | target/surefire-reports/TEST-com.oneshop.MaxUtf8BytesValidationTest.xml |
| baseline-0538 | com.oneshop.MaxUtf8BytesValidationTest#asciiPasswordIsLimitedTo72 | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.MaxUtf8BytesValidationTest.xml |
| baseline-0539 | com.oneshop.OneShopApplicationTests#passwordsAreHashedWithBcrypt | PASS | 0.16 | None | target/surefire-reports/TEST-com.oneshop.OneShopApplicationTests.xml |
| baseline-0540 | com.oneshop.OneShopApplicationTests#contextLoadsWithoutDatabase | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.OneShopApplicationTests.xml |
| baseline-0541 | com.oneshop.OrderStateDatabaseIntegrationTest#concurrentDuplicateHasOneWinnerAndExactlyOneAudit()[1] | PASS | 0.143 | None | target/surefire-reports/TEST-com.oneshop.OrderStateDatabaseIntegrationTest.xml |
| baseline-0542 | com.oneshop.OrderStateDatabaseIntegrationTest#concurrentDuplicateHasOneWinnerAndExactlyOneAudit()[2] | PASS | 0.11 | None | target/surefire-reports/TEST-com.oneshop.OrderStateDatabaseIntegrationTest.xml |
| baseline-0543 | com.oneshop.OrderStateDatabaseIntegrationTest#concurrentDuplicateHasOneWinnerAndExactlyOneAudit()[3] | PASS | 0.101 | None | target/surefire-reports/TEST-com.oneshop.OrderStateDatabaseIntegrationTest.xml |
| baseline-0544 | com.oneshop.OrderStateDatabaseIntegrationTest#genericPrimitiveCannotUsePaymentEdgesAndPaymentHelpersRequireTransaction | PASS | 0.092 | None | target/surefire-reports/TEST-com.oneshop.OrderStateDatabaseIntegrationTest.xml |
| baseline-0545 | com.oneshop.OrderStateDatabaseIntegrationTest#invalidSameBackwardSkipTerminalAndWrongBranchLeaveNoHistory(FulfillmentType, OrderStatus, OrderStatus)[1] | PASS | 0.087 | None | target/surefire-reports/TEST-com.oneshop.OrderStateDatabaseIntegrationTest.xml |
| baseline-0546 | com.oneshop.OrderStateDatabaseIntegrationTest#invalidSameBackwardSkipTerminalAndWrongBranchLeaveNoHistory(FulfillmentType, OrderStatus, OrderStatus)[2] | PASS | 0.073 | None | target/surefire-reports/TEST-com.oneshop.OrderStateDatabaseIntegrationTest.xml |
| baseline-0547 | com.oneshop.OrderStateDatabaseIntegrationTest#invalidSameBackwardSkipTerminalAndWrongBranchLeaveNoHistory(FulfillmentType, OrderStatus, OrderStatus)[3] | PASS | 0.098 | None | target/surefire-reports/TEST-com.oneshop.OrderStateDatabaseIntegrationTest.xml |
| baseline-0548 | com.oneshop.OrderStateDatabaseIntegrationTest#invalidSameBackwardSkipTerminalAndWrongBranchLeaveNoHistory(FulfillmentType, OrderStatus, OrderStatus)[4] | PASS | 0.095 | None | target/surefire-reports/TEST-com.oneshop.OrderStateDatabaseIntegrationTest.xml |
| baseline-0549 | com.oneshop.OrderStateDatabaseIntegrationTest#invalidSameBackwardSkipTerminalAndWrongBranchLeaveNoHistory(FulfillmentType, OrderStatus, OrderStatus)[5] | PASS | 0.095 | None | target/surefire-reports/TEST-com.oneshop.OrderStateDatabaseIntegrationTest.xml |
| baseline-0550 | com.oneshop.OrderStateDatabaseIntegrationTest#invalidSameBackwardSkipTerminalAndWrongBranchLeaveNoHistory(FulfillmentType, OrderStatus, OrderStatus)[6] | PASS | 0.08 | None | target/surefire-reports/TEST-com.oneshop.OrderStateDatabaseIntegrationTest.xml |
| baseline-0551 | com.oneshop.OrderStateDatabaseIntegrationTest#invalidSameBackwardSkipTerminalAndWrongBranchLeaveNoHistory(FulfillmentType, OrderStatus, OrderStatus)[7] | PASS | 0.08 | None | target/surefire-reports/TEST-com.oneshop.OrderStateDatabaseIntegrationTest.xml |
| baseline-0552 | com.oneshop.OrderStateDatabaseIntegrationTest#invalidSameBackwardSkipTerminalAndWrongBranchLeaveNoHistory(FulfillmentType, OrderStatus, OrderStatus)[8] | PASS | 0.125 | None | target/surefire-reports/TEST-com.oneshop.OrderStateDatabaseIntegrationTest.xml |
| baseline-0553 | com.oneshop.OrderStateDatabaseIntegrationTest#invalidSameBackwardSkipTerminalAndWrongBranchLeaveNoHistory(FulfillmentType, OrderStatus, OrderStatus)[9] | PASS | 0.084 | None | target/surefire-reports/TEST-com.oneshop.OrderStateDatabaseIntegrationTest.xml |
| baseline-0554 | com.oneshop.OrderStateDatabaseIntegrationTest#invalidSameBackwardSkipTerminalAndWrongBranchLeaveNoHistory(FulfillmentType, OrderStatus, OrderStatus)[10] | PASS | 0.105 | None | target/surefire-reports/TEST-com.oneshop.OrderStateDatabaseIntegrationTest.xml |
| baseline-0555 | com.oneshop.OrderStateDatabaseIntegrationTest#invalidSameBackwardSkipTerminalAndWrongBranchLeaveNoHistory(FulfillmentType, OrderStatus, OrderStatus)[11] | PASS | 0.085 | None | target/surefire-reports/TEST-com.oneshop.OrderStateDatabaseIntegrationTest.xml |
| baseline-0556 | com.oneshop.OrderStateDatabaseIntegrationTest#paymentOutcomesUseCentralPolicyAndKeepSystemHistory(boolean)[1] | PASS | 0.091 | None | target/surefire-reports/TEST-com.oneshop.OrderStateDatabaseIntegrationTest.xml |
| baseline-0557 | com.oneshop.OrderStateDatabaseIntegrationTest#paymentOutcomesUseCentralPolicyAndKeepSystemHistory(boolean)[2] | PASS | 0.096 | None | target/surefire-reports/TEST-com.oneshop.OrderStateDatabaseIntegrationTest.xml |
| baseline-0558 | com.oneshop.OrderStateDatabaseIntegrationTest#packedVersusShippingCannotSkipFromStalePreparingIntent | PASS | 0.106 | None | target/surefire-reports/TEST-com.oneshop.OrderStateDatabaseIntegrationTest.xml |
| baseline-0559 | com.oneshop.OrderStateDatabaseIntegrationTest#validTransitionHasOneHistoryActorTimestampNoteAndNoSideEffects(FulfillmentType)[1] | PASS | 0.095 | None | target/surefire-reports/TEST-com.oneshop.OrderStateDatabaseIntegrationTest.xml |
| baseline-0560 | com.oneshop.OrderStateDatabaseIntegrationTest#validTransitionHasOneHistoryActorTimestampNoteAndNoSideEffects(FulfillmentType)[2] | PASS | 0.096 | None | target/surefire-reports/TEST-com.oneshop.OrderStateDatabaseIntegrationTest.xml |
| baseline-0561 | com.oneshop.OrderStateDatabaseIntegrationTest#noteLimitIsCheckedBeforeWritesAndNullSystemActorIsStored | PASS | 0.079 | None | target/surefire-reports/TEST-com.oneshop.OrderStateDatabaseIntegrationTest.xml |
| baseline-0562 | com.oneshop.OrderStateDatabaseIntegrationTest#historyFailureRollsBackAlreadyFlushedOrderAndOptionalFlushedHistory(boolean)[1] | PASS | 0.111 | None | target/surefire-reports/TEST-com.oneshop.OrderStateDatabaseIntegrationTest.xml |
| baseline-0563 | com.oneshop.OrderStateDatabaseIntegrationTest#historyFailureRollsBackAlreadyFlushedOrderAndOptionalFlushedHistory(boolean)[2] | PASS | 0.1 | None | target/surefire-reports/TEST-com.oneshop.OrderStateDatabaseIntegrationTest.xml |
| baseline-0564 | com.oneshop.OrderStateDatabaseIntegrationTest#repeatedPaymentSuccessAfterPreparingDoesNotRepeatHistoryOrRewindOrder | PASS | 0.092 | None | target/surefire-reports/TEST-com.oneshop.OrderStateDatabaseIntegrationTest.xml |
| baseline-0565 | com.oneshop.PartialRefundDatabaseIntegrationTest#refundViews | PASS | 0.198 | None | target/surefire-reports/TEST-com.oneshop.PartialRefundDatabaseIntegrationTest.xml |
| baseline-0566 | com.oneshop.PartialRefundDatabaseIntegrationTest#unconfirmedKeepsTheExceptionOpen | PASS | 0.122 | None | target/surefire-reports/TEST-com.oneshop.PartialRefundDatabaseIntegrationTest.xml |
| baseline-0567 | com.oneshop.PartialRefundDatabaseIntegrationTest#wholeOrderPathAndLegacy | PASS | 0.08 | None | target/surefire-reports/TEST-com.oneshop.PartialRefundDatabaseIntegrationTest.xml |
| baseline-0568 | com.oneshop.PartialRefundDatabaseIntegrationTest#codHasNoLedger | PASS | 0.082 | None | target/surefire-reports/TEST-com.oneshop.PartialRefundDatabaseIntegrationTest.xml |
| baseline-0569 | com.oneshop.PartialRefundDatabaseIntegrationTest#partialRefundGoodsOnly | PASS | 0.127 | None | target/surefire-reports/TEST-com.oneshop.PartialRefundDatabaseIntegrationTest.xml |
| baseline-0570 | com.oneshop.PartialRefundDatabaseIntegrationTest#concurrentSamePartialRefund | PASS | 0.111 | None | target/surefire-reports/TEST-com.oneshop.PartialRefundDatabaseIntegrationTest.xml |
| baseline-0571 | com.oneshop.PartialRefundDatabaseIntegrationTest#databaseConstraints | PASS | 0.079 | None | target/surefire-reports/TEST-com.oneshop.PartialRefundDatabaseIntegrationTest.xml |
| baseline-0572 | com.oneshop.PartialRefundDatabaseIntegrationTest#redeliverOneRefundTheOther | PASS | 0.112 | None | target/surefire-reports/TEST-com.oneshop.PartialRefundDatabaseIntegrationTest.xml |
| baseline-0573 | com.oneshop.PartialRefundDatabaseIntegrationTest#neverMoreThanCollected | PASS | 0.097 | None | target/surefire-reports/TEST-com.oneshop.PartialRefundDatabaseIntegrationTest.xml |
| baseline-0574 | com.oneshop.PartialRefundDatabaseIntegrationTest#wholeRefundWhenNothingDelivered | PASS | 0.126 | None | target/surefire-reports/TEST-com.oneshop.PartialRefundDatabaseIntegrationTest.xml |
| baseline-0575 | com.oneshop.PartialRefundDatabaseIntegrationTest#concurrentLastParts | PASS | 0.144 | None | target/surefire-reports/TEST-com.oneshop.PartialRefundDatabaseIntegrationTest.xml |
| baseline-0576 | com.oneshop.PartialRefundDatabaseIntegrationTest#returnedVersusLost | PASS | 0.176 | None | target/surefire-reports/TEST-com.oneshop.PartialRefundDatabaseIntegrationTest.xml |
| baseline-0577 | com.oneshop.PartialRefundDatabaseIntegrationTest#failedSimulationAndRetry | PASS | 0.16 | None | target/surefire-reports/TEST-com.oneshop.PartialRefundDatabaseIntegrationTest.xml |
| baseline-0578 | com.oneshop.PartialRefundDatabaseIntegrationTest#shippingGoesBackOnlyWithTheLastPart | PASS | 0.128 | None | target/surefire-reports/TEST-com.oneshop.PartialRefundDatabaseIntegrationTest.xml |
| baseline-0579 | com.oneshop.PartialRefundHttpDatabaseIntegrationTest#adminPanel | PASS | 0.297 | None | target/surefire-reports/TEST-com.oneshop.PartialRefundHttpDatabaseIntegrationTest.xml |
| baseline-0580 | com.oneshop.PartialRefundHttpDatabaseIntegrationTest#accessControl | PASS | 0.56 | None | target/surefire-reports/TEST-com.oneshop.PartialRefundHttpDatabaseIntegrationTest.xml |
| baseline-0581 | com.oneshop.PartialRefundHttpDatabaseIntegrationTest#customerView | PASS | 0.392 | None | target/surefire-reports/TEST-com.oneshop.PartialRefundHttpDatabaseIntegrationTest.xml |
| baseline-0582 | com.oneshop.PartialRefundHttpDatabaseIntegrationTest#unconfirmedThenLost | PASS | 0.382 | None | target/surefire-reports/TEST-com.oneshop.PartialRefundHttpDatabaseIntegrationTest.xml |
| baseline-0583 | com.oneshop.PaymentConcurrencyDatabaseIntegrationTest#be05_42_duplicateCallbacks | PASS | 0.142 | None | target/surefire-reports/TEST-com.oneshop.PaymentConcurrencyDatabaseIntegrationTest.xml |
| baseline-0584 | com.oneshop.PaymentConcurrencyDatabaseIntegrationTest#be05_38_successVsTimeout | PASS | 0.221 | None | target/surefire-reports/TEST-com.oneshop.PaymentConcurrencyDatabaseIntegrationTest.xml |
| baseline-0585 | com.oneshop.PaymentConcurrencyDatabaseIntegrationTest#be05_44_twoScans | PASS | 0.142 | None | target/surefire-reports/TEST-com.oneshop.PaymentConcurrencyDatabaseIntegrationTest.xml |
| baseline-0586 | com.oneshop.PaymentConcurrencyDatabaseIntegrationTest#be05_41_cancelVsPreparing | PASS | 0.256 | None | target/surefire-reports/TEST-com.oneshop.PaymentConcurrencyDatabaseIntegrationTest.xml |
| baseline-0587 | com.oneshop.PaymentConcurrencyDatabaseIntegrationTest#be05_43_duplicateCancel | PASS | 0.162 | None | target/surefire-reports/TEST-com.oneshop.PaymentConcurrencyDatabaseIntegrationTest.xml |
| baseline-0588 | com.oneshop.PaymentConcurrencyDatabaseIntegrationTest#be05_49_scheduledWorker | PASS | 2.877 | None | target/surefire-reports/TEST-com.oneshop.PaymentConcurrencyDatabaseIntegrationTest.xml |
| baseline-0589 | com.oneshop.PaymentConcurrencyDatabaseIntegrationTest#be05_46_restoreFailureRollsBack | PASS | 0.113 | None | target/surefire-reports/TEST-com.oneshop.PaymentConcurrencyDatabaseIntegrationTest.xml |
| baseline-0590 | com.oneshop.PaymentConcurrencyDatabaseIntegrationTest#be05_47_statusWriteFailureRollsBack | PASS | 0.063 | None | target/surefire-reports/TEST-com.oneshop.PaymentConcurrencyDatabaseIntegrationTest.xml |
| baseline-0591 | com.oneshop.PaymentConcurrencyDatabaseIntegrationTest#be05_40_cancelVsPayment | PASS | 0.267 | None | target/surefire-reports/TEST-com.oneshop.PaymentConcurrencyDatabaseIntegrationTest.xml |
| baseline-0592 | com.oneshop.PaymentConcurrencyDatabaseIntegrationTest#be05_39_successInTimeVsScan | PASS | 0.108 | None | target/surefire-reports/TEST-com.oneshop.PaymentConcurrencyDatabaseIntegrationTest.xml |
| baseline-0593 | com.oneshop.PaymentConcurrencyDatabaseIntegrationTest#be05_48_restartRecovery | PASS | 2.814 | None | target/surefire-reports/TEST-com.oneshop.PaymentConcurrencyDatabaseIntegrationTest.xml |
| baseline-0594 | com.oneshop.PaymentConcurrencyDatabaseIntegrationTest#be05_45_scanAdminCallback | PASS | 0.08 | None | target/surefire-reports/TEST-com.oneshop.PaymentConcurrencyDatabaseIntegrationTest.xml |
| baseline-0595 | com.oneshop.PaymentDatabaseIntegrationTest#historyFailureRollsBackWholeResult(boolean)[1] | PASS | 0.143 | None | target/surefire-reports/TEST-com.oneshop.PaymentDatabaseIntegrationTest.xml |
| baseline-0596 | com.oneshop.PaymentDatabaseIntegrationTest#historyFailureRollsBackWholeResult(boolean)[2] | PASS | 0.091 | None | target/surefire-reports/TEST-com.oneshop.PaymentDatabaseIntegrationTest.xml |
| baseline-0597 | com.oneshop.PaymentDatabaseIntegrationTest#failureCancelsAndRestoresEverySnapshotToItsOwnStoreProduct | PASS | 0.11 | None | target/surefire-reports/TEST-com.oneshop.PaymentDatabaseIntegrationTest.xml |
| baseline-0598 | com.oneshop.PaymentDatabaseIntegrationTest#repeatedStartReusesPendingAndSchemaRemainsOneToMany | PASS | 0.097 | None | target/surefire-reports/TEST-com.oneshop.PaymentDatabaseIntegrationTest.xml |
| baseline-0599 | com.oneshop.PaymentDatabaseIntegrationTest#duplicateResultDoesNotRepeatAnyWrites(boolean)[1] | PASS | 0.078 | None | target/surefire-reports/TEST-com.oneshop.PaymentDatabaseIntegrationTest.xml |
| baseline-0600 | com.oneshop.PaymentDatabaseIntegrationTest#duplicateResultDoesNotRepeatAnyWrites(boolean)[2] | PASS | 0.097 | None | target/surefire-reports/TEST-com.oneshop.PaymentDatabaseIntegrationTest.xml |
| baseline-0601 | com.oneshop.PaymentDatabaseIntegrationTest#clientPaymentRoutesAreCustomerOnly(String)[1] | PASS | 0.092 | None | target/surefire-reports/TEST-com.oneshop.PaymentDatabaseIntegrationTest.xml |
| baseline-0602 | com.oneshop.PaymentDatabaseIntegrationTest#clientPaymentRoutesAreCustomerOnly(String)[2] | PASS | 0.084 | None | target/surefire-reports/TEST-com.oneshop.PaymentDatabaseIntegrationTest.xml |
| baseline-0603 | com.oneshop.PaymentDatabaseIntegrationTest#clientPaymentRoutesAreCustomerOnly(String)[3] | PASS | 0.081 | None | target/surefire-reports/TEST-com.oneshop.PaymentDatabaseIntegrationTest.xml |
| baseline-0604 | com.oneshop.PaymentDatabaseIntegrationTest#concurrentFailuresOfDifferentOrdersSharingStockDoNotLoseRestores | PASS | 0.101 | None | target/surefire-reports/TEST-com.oneshop.PaymentDatabaseIntegrationTest.xml |
| baseline-0605 | com.oneshop.PaymentDatabaseIntegrationTest#secondRestoreFailureRollsBackFirstFlushedItemPaymentOrderAndAudit | PASS | 0.113 | None | target/surefire-reports/TEST-com.oneshop.PaymentDatabaseIntegrationTest.xml |
| baseline-0606 | com.oneshop.PaymentDatabaseIntegrationTest#ownershipAndMissingIdsAreCheckedForEveryOperation | PASS | 0.087 | None | target/surefire-reports/TEST-com.oneshop.PaymentDatabaseIntegrationTest.xml |
| baseline-0607 | com.oneshop.PaymentDatabaseIntegrationTest#httpWorkflowIgnoresForgedFieldsAndShowsResult(boolean)[1] | PASS | 0.196 | None | target/surefire-reports/TEST-com.oneshop.PaymentDatabaseIntegrationTest.xml |
| baseline-0608 | com.oneshop.PaymentDatabaseIntegrationTest#httpWorkflowIgnoresForgedFieldsAndShowsResult(boolean)[2] | PASS | 0.201 | None | target/surefire-reports/TEST-com.oneshop.PaymentDatabaseIntegrationTest.xml |
| baseline-0609 | com.oneshop.PaymentDatabaseIntegrationTest#pendingBelongsToOrderAndUsesItsAmountAndMethod | PASS | 0.09 | None | target/surefire-reports/TEST-com.oneshop.PaymentDatabaseIntegrationTest.xml |
| baseline-0610 | com.oneshop.PaymentDatabaseIntegrationTest#paymentDoesNotChangeOtherOrdersInSameCheckout(boolean)[1] | PASS | 0.109 | None | target/surefire-reports/TEST-com.oneshop.PaymentDatabaseIntegrationTest.xml |
| baseline-0611 | com.oneshop.PaymentDatabaseIntegrationTest#paymentDoesNotChangeOtherOrdersInSameCheckout(boolean)[2] | PASS | 0.112 | None | target/surefire-reports/TEST-com.oneshop.PaymentDatabaseIntegrationTest.xml |
| baseline-0612 | com.oneshop.PaymentDatabaseIntegrationTest#successConfirmsOrderWithAuditAndDoesNotTouchStockOrSnapshots | PASS | 0.082 | None | target/surefire-reports/TEST-com.oneshop.PaymentDatabaseIntegrationTest.xml |
| baseline-0613 | com.oneshop.PaymentDatabaseIntegrationTest#offlineOrdersStayConfirmedUnpaid(PaymentMethod)[1] | PASS | 0.097 | None | target/surefire-reports/TEST-com.oneshop.PaymentDatabaseIntegrationTest.xml |
| baseline-0614 | com.oneshop.PaymentDatabaseIntegrationTest#offlineOrdersStayConfirmedUnpaid(PaymentMethod)[2] | PASS | 0.08 | None | target/surefire-reports/TEST-com.oneshop.PaymentDatabaseIntegrationTest.xml |
| baseline-0615 | com.oneshop.PaymentDatabaseIntegrationTest#inconsistentPaymentIsRefusedWithoutMutation(String)[1] | PASS | 0.082 | None | target/surefire-reports/TEST-com.oneshop.PaymentDatabaseIntegrationTest.xml |
| baseline-0616 | com.oneshop.PaymentDatabaseIntegrationTest#inconsistentPaymentIsRefusedWithoutMutation(String)[2] | PASS | 0.093 | None | target/surefire-reports/TEST-com.oneshop.PaymentDatabaseIntegrationTest.xml |
| baseline-0617 | com.oneshop.PaymentDatabaseIntegrationTest#concurrentSameResultTransitionsAndRestoresAtMostOnce(boolean)[1] | PASS | 0.08 | None | target/surefire-reports/TEST-com.oneshop.PaymentDatabaseIntegrationTest.xml |
| baseline-0618 | com.oneshop.PaymentDatabaseIntegrationTest#concurrentSameResultTransitionsAndRestoresAtMostOnce(boolean)[2] | PASS | 0.128 | None | target/surefire-reports/TEST-com.oneshop.PaymentDatabaseIntegrationTest.xml |
| baseline-0619 | com.oneshop.PaymentDatabaseIntegrationTest#concurrentStartCreatesOnePendingAttempt | PASS | 0.098 | None | target/surefire-reports/TEST-com.oneshop.PaymentDatabaseIntegrationTest.xml |
| baseline-0620 | com.oneshop.PaymentDatabaseIntegrationTest#csrfRequiredForStartAndBothResultsEvenWithBearer | PASS | 0.159 | None | target/surefire-reports/TEST-com.oneshop.PaymentDatabaseIntegrationTest.xml |
| baseline-0621 | com.oneshop.PaymentDatabaseIntegrationTest#httpOwnershipAndPaymentOrderMismatchReturn404 | PASS | 0.221 | None | target/surefire-reports/TEST-com.oneshop.PaymentDatabaseIntegrationTest.xml |
| baseline-0622 | com.oneshop.PaymentDatabaseIntegrationTest#orderNoLongerPendingCannotCompletePayment | PASS | 0.097 | None | target/surefire-reports/TEST-com.oneshop.PaymentDatabaseIntegrationTest.xml |
| baseline-0623 | com.oneshop.PaymentDatabaseIntegrationTest#conflictingConcurrentResultsHaveOneWinner | PASS | 0.094 | None | target/surefire-reports/TEST-com.oneshop.PaymentDatabaseIntegrationTest.xml |
| baseline-0624 | com.oneshop.PaymentDatabaseIntegrationTest#httpOfflineOrderHasNoOnlineControlsAndCannotStart(PaymentMethod)[1] | PASS | 0.08 | None | target/surefire-reports/TEST-com.oneshop.PaymentDatabaseIntegrationTest.xml |
| baseline-0625 | com.oneshop.PaymentDatabaseIntegrationTest#httpOfflineOrderHasNoOnlineControlsAndCannotStart(PaymentMethod)[2] | PASS | 0.095 | None | target/surefire-reports/TEST-com.oneshop.PaymentDatabaseIntegrationTest.xml |
| baseline-0626 | com.oneshop.PaymentGatewayDisabledDatabaseIntegrationTest#be05_58_gatewayDisabled | PASS | 0.121 | None | target/surefire-reports/TEST-com.oneshop.PaymentGatewayDisabledDatabaseIntegrationTest.xml |
| baseline-0627 | com.oneshop.PaymentHttpDatabaseIntegrationTest#be05_56_adminConsole | PASS | 0.705 | None | target/surefire-reports/TEST-com.oneshop.PaymentHttpDatabaseIntegrationTest.xml |
| baseline-0628 | com.oneshop.PaymentHttpDatabaseIntegrationTest#be05_54_reconcilingPage | PASS | 0.203 | None | target/surefire-reports/TEST-com.oneshop.PaymentHttpDatabaseIntegrationTest.xml |
| baseline-0629 | com.oneshop.PaymentHttpDatabaseIntegrationTest#be05_50_simulateSuccessOverHttp | PASS | 0.186 | None | target/surefire-reports/TEST-com.oneshop.PaymentHttpDatabaseIntegrationTest.xml |
| baseline-0630 | com.oneshop.PaymentHttpDatabaseIntegrationTest#be05_52_security | PASS | 0.574 | None | target/surefire-reports/TEST-com.oneshop.PaymentHttpDatabaseIntegrationTest.xml |
| baseline-0631 | com.oneshop.PaymentHttpDatabaseIntegrationTest#be05_57_adminRefundRetry | PASS | 0.407 | None | target/surefire-reports/TEST-com.oneshop.PaymentHttpDatabaseIntegrationTest.xml |
| baseline-0632 | com.oneshop.PaymentHttpDatabaseIntegrationTest#be05_55_cancelPaidOverHttp | PASS | 0.316 | None | target/surefire-reports/TEST-com.oneshop.PaymentHttpDatabaseIntegrationTest.xml |
| baseline-0633 | com.oneshop.PaymentHttpDatabaseIntegrationTest#be05_53_oldRoutesRefused | PASS | 0.223 | None | target/surefire-reports/TEST-com.oneshop.PaymentHttpDatabaseIntegrationTest.xml |
| baseline-0634 | com.oneshop.PaymentHttpDatabaseIntegrationTest#be05_51_forgedFieldsIgnored | PASS | 0.171 | None | target/surefire-reports/TEST-com.oneshop.PaymentHttpDatabaseIntegrationTest.xml |
| baseline-0635 | com.oneshop.PaymentSettlementDatabaseIntegrationTest#be05_12_unappliedCollectionRecovered | PASS | 0.08 | None | target/surefire-reports/TEST-com.oneshop.PaymentSettlementDatabaseIntegrationTest.xml |
| baseline-0636 | com.oneshop.PaymentSettlementDatabaseIntegrationTest#be05_32_refundFailureAndRetry | PASS | 0.08 | None | target/surefire-reports/TEST-com.oneshop.PaymentSettlementDatabaseIntegrationTest.xml |
| baseline-0637 | com.oneshop.PaymentSettlementDatabaseIntegrationTest#be05_11_scanIsIdempotent | PASS | 0.063 | None | target/surefire-reports/TEST-com.oneshop.PaymentSettlementDatabaseIntegrationTest.xml |
| baseline-0638 | com.oneshop.PaymentSettlementDatabaseIntegrationTest#be05_10_notExpired | PASS | 0.064 | None | target/surefire-reports/TEST-com.oneshop.PaymentSettlementDatabaseIntegrationTest.xml |
| baseline-0639 | com.oneshop.PaymentSettlementDatabaseIntegrationTest#be05_14_reconcilingInsideWindow | PASS | 0.065 | None | target/surefire-reports/TEST-com.oneshop.PaymentSettlementDatabaseIntegrationTest.xml |
| baseline-0640 | com.oneshop.PaymentSettlementDatabaseIntegrationTest#be05_06_browserResultRefused | PASS | 0.055 | None | target/surefire-reports/TEST-com.oneshop.PaymentSettlementDatabaseIntegrationTest.xml |
| baseline-0641 | com.oneshop.PaymentSettlementDatabaseIntegrationTest#be05_17_unknownThenSuccess | PASS | 0.063 | None | target/surefire-reports/TEST-com.oneshop.PaymentSettlementDatabaseIntegrationTest.xml |
| baseline-0642 | com.oneshop.PaymentSettlementDatabaseIntegrationTest#be05_09_timeoutCancels | PASS | 0.079 | None | target/surefire-reports/TEST-com.oneshop.PaymentSettlementDatabaseIntegrationTest.xml |
| baseline-0643 | com.oneshop.PaymentSettlementDatabaseIntegrationTest#be05_08_offlineMethodsAreNotPaymentCases | PASS | 0.065 | None | target/surefire-reports/TEST-com.oneshop.PaymentSettlementDatabaseIntegrationTest.xml |
| baseline-0644 | com.oneshop.PaymentSettlementDatabaseIntegrationTest#be05_24_boundary | PASS | 0.076 | None | target/surefire-reports/TEST-com.oneshop.PaymentSettlementDatabaseIntegrationTest.xml |
| baseline-0645 | com.oneshop.PaymentSettlementDatabaseIntegrationTest#be05_30_offlineCancel | PASS | 0.091 | None | target/surefire-reports/TEST-com.oneshop.PaymentSettlementDatabaseIntegrationTest.xml |
| baseline-0646 | com.oneshop.PaymentSettlementDatabaseIntegrationTest#be05_15_reconcilingBlocksEverything | PASS | 0.071 | None | target/surefire-reports/TEST-com.oneshop.PaymentSettlementDatabaseIntegrationTest.xml |
| baseline-0647 | com.oneshop.PaymentSettlementDatabaseIntegrationTest#be05_20_adminConfirms | PASS | 0.084 | None | target/surefire-reports/TEST-com.oneshop.PaymentSettlementDatabaseIntegrationTest.xml |
| baseline-0648 | com.oneshop.PaymentSettlementDatabaseIntegrationTest#be05_18_unknownThenFailed | PASS | 0.073 | None | target/surefire-reports/TEST-com.oneshop.PaymentSettlementDatabaseIntegrationTest.xml |
| baseline-0649 | com.oneshop.PaymentSettlementDatabaseIntegrationTest#be05_36_noRevive | PASS | 0.091 | None | target/surefire-reports/TEST-com.oneshop.PaymentSettlementDatabaseIntegrationTest.xml |
| baseline-0650 | com.oneshop.PaymentSettlementDatabaseIntegrationTest#be05_22_adminLateCollection | PASS | 0.064 | None | target/surefire-reports/TEST-com.oneshop.PaymentSettlementDatabaseIntegrationTest.xml |
| baseline-0651 | com.oneshop.PaymentSettlementDatabaseIntegrationTest#be05_01_attemptAtCheckout | PASS | 0.05 | None | target/surefire-reports/TEST-com.oneshop.PaymentSettlementDatabaseIntegrationTest.xml |
| baseline-0652 | com.oneshop.PaymentSettlementDatabaseIntegrationTest#be05_13_lateCollectionRefunded | PASS | 0.077 | None | target/surefire-reports/TEST-com.oneshop.PaymentSettlementDatabaseIntegrationTest.xml |
| baseline-0653 | com.oneshop.PaymentSettlementDatabaseIntegrationTest#be05_35_noEvidenceNoPaid | PASS | 0.065 | None | target/surefire-reports/TEST-com.oneshop.PaymentSettlementDatabaseIntegrationTest.xml |
| baseline-0654 | com.oneshop.PaymentSettlementDatabaseIntegrationTest#be05_25_gatewayOutageIsUnknown | PASS | 0.047 | None | target/surefire-reports/TEST-com.oneshop.PaymentSettlementDatabaseIntegrationTest.xml |
| baseline-0655 | com.oneshop.PaymentSettlementDatabaseIntegrationTest#be05_05_unknownIsRecorded | PASS | 0.048 | None | target/surefire-reports/TEST-com.oneshop.PaymentSettlementDatabaseIntegrationTest.xml |
| baseline-0656 | com.oneshop.PaymentSettlementDatabaseIntegrationTest#be05_29_splitSameSkuRestoredPerStore | PASS | 0.087 | None | target/surefire-reports/TEST-com.oneshop.PaymentSettlementDatabaseIntegrationTest.xml |
| baseline-0657 | com.oneshop.PaymentSettlementDatabaseIntegrationTest#be05_27_cancelPaidRefunds | PASS | 0.086 | None | target/surefire-reports/TEST-com.oneshop.PaymentSettlementDatabaseIntegrationTest.xml |
| baseline-0658 | com.oneshop.PaymentSettlementDatabaseIntegrationTest#be05_19_recheckStillUnknown | PASS | 0.074 | None | target/surefire-reports/TEST-com.oneshop.PaymentSettlementDatabaseIntegrationTest.xml |
| baseline-0659 | com.oneshop.PaymentSettlementDatabaseIntegrationTest#be05_07_ownership | PASS | 0.065 | None | target/surefire-reports/TEST-com.oneshop.PaymentSettlementDatabaseIntegrationTest.xml |
| baseline-0660 | com.oneshop.PaymentSettlementDatabaseIntegrationTest#be05_23_lateCallbackOnCancelled | PASS | 0.075 | None | target/surefire-reports/TEST-com.oneshop.PaymentSettlementDatabaseIntegrationTest.xml |
| baseline-0661 | com.oneshop.PaymentSettlementDatabaseIntegrationTest#be05_37_adminList | PASS | 0.07 | None | target/surefire-reports/TEST-com.oneshop.PaymentSettlementDatabaseIntegrationTest.xml |
| baseline-0662 | com.oneshop.PaymentSettlementDatabaseIntegrationTest#be05_33_noRefundWithoutCollection | PASS | 0.068 | None | target/surefire-reports/TEST-com.oneshop.PaymentSettlementDatabaseIntegrationTest.xml |
| baseline-0663 | com.oneshop.PaymentSettlementDatabaseIntegrationTest#be05_02_successConfirms | PASS | 0.057 | None | target/surefire-reports/TEST-com.oneshop.PaymentSettlementDatabaseIntegrationTest.xml |
| baseline-0664 | com.oneshop.PaymentSettlementDatabaseIntegrationTest#be05_16_needsAttentionAfterWindow | PASS | 0.057 | None | target/surefire-reports/TEST-com.oneshop.PaymentSettlementDatabaseIntegrationTest.xml |
| baseline-0665 | com.oneshop.PaymentSettlementDatabaseIntegrationTest#be05_28_cancelAfterPreparing | PASS | 0.081 | None | target/surefire-reports/TEST-com.oneshop.PaymentSettlementDatabaseIntegrationTest.xml |
| baseline-0666 | com.oneshop.PaymentSettlementDatabaseIntegrationTest#be05_31_failedPartIsNotRestocked | PASS | 0.111 | None | target/surefire-reports/TEST-com.oneshop.PaymentSettlementDatabaseIntegrationTest.xml |
| baseline-0667 | com.oneshop.PaymentSettlementDatabaseIntegrationTest#be05_04_failedIsTerminal | PASS | 0.08 | None | target/surefire-reports/TEST-com.oneshop.PaymentSettlementDatabaseIntegrationTest.xml |
| baseline-0668 | com.oneshop.PaymentSettlementDatabaseIntegrationTest#be05_26_cancelUnpaid | PASS | 0.047 | None | target/surefire-reports/TEST-com.oneshop.PaymentSettlementDatabaseIntegrationTest.xml |
| baseline-0669 | com.oneshop.PaymentSettlementDatabaseIntegrationTest#be05_03_duplicateCallback | PASS | 0.064 | None | target/surefire-reports/TEST-com.oneshop.PaymentSettlementDatabaseIntegrationTest.xml |
| baseline-0670 | com.oneshop.PaymentSettlementDatabaseIntegrationTest#be05_21_adminCancels | PASS | 0.095 | None | target/surefire-reports/TEST-com.oneshop.PaymentSettlementDatabaseIntegrationTest.xml |
| baseline-0671 | com.oneshop.PaymentSettlementDatabaseIntegrationTest#be05_34_adminOnly | PASS | 0.069 | None | target/surefire-reports/TEST-com.oneshop.PaymentSettlementDatabaseIntegrationTest.xml |
| baseline-0672 | com.oneshop.PaymentWebIntegrationTest#csrfIsRequiredOnEveryWrite | PASS | 0.032 | None | target/surefire-reports/TEST-com.oneshop.PaymentWebIntegrationTest.xml |
| baseline-0673 | com.oneshop.PaymentWebIntegrationTest#lockConflictAndConstraintFailureDoNotExposeSqlToUi | PASS | 0.047 | None | target/surefire-reports/TEST-com.oneshop.PaymentWebIntegrationTest.xml |
| baseline-0674 | com.oneshop.PaymentWebIntegrationTest#formsUsePrincipalAndIgnoreAllClientPaymentData | PASS | 0.031 | None | target/surefire-reports/TEST-com.oneshop.PaymentWebIntegrationTest.xml |
| baseline-0675 | com.oneshop.PaymentWebIntegrationTest#unknownOwnerOrAttemptGetsFriendly404 | PASS | 0.016 | None | target/surefire-reports/TEST-com.oneshop.PaymentWebIntegrationTest.xml |
| baseline-0676 | com.oneshop.PaymentWebIntegrationTest#staffAdminAndGuestCannotUseClientFlow | PASS | 0.034 | None | target/surefire-reports/TEST-com.oneshop.PaymentWebIntegrationTest.xml |
| baseline-0677 | com.oneshop.PaymentWebIntegrationTest#viewHasOrderStoreTotalDeadlineAndNoBrowserPaymentResult | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.PaymentWebIntegrationTest.xml |
| baseline-0678 | com.oneshop.PaymentWorkerDefaultsTest#theScheduledWorkerIsNotCreatedUnlessExplicitlyEnabled | PASS | 0.037 | None | target/surefire-reports/TEST-com.oneshop.PaymentWorkerDefaultsTest.xml |
| baseline-0679 | com.oneshop.PaymentWorkerDefaultsTest#javaDefaultIsOff | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.PaymentWorkerDefaultsTest.xml |
| baseline-0680 | com.oneshop.PaymentWorkerDefaultsTest#productionKeepsOnlineClosedBecauseThereIsNoGatewayThere | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.PaymentWorkerDefaultsTest.xml |
| baseline-0681 | com.oneshop.PaymentWorkerDefaultsTest#theShippedConfigurationLeavesTheWorkerOff | PASS | 0.011 | None | target/surefire-reports/TEST-com.oneshop.PaymentWorkerDefaultsTest.xml |
| baseline-0682 | com.oneshop.PaymentWorkerDefaultsTest#anExplicitEnvironmentSettingTurnsItOn | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.PaymentWorkerDefaultsTest.xml |
| baseline-0683 | com.oneshop.Phase10IntegrationWebDatabaseTest#tc13PhysicalCountCreatesOneCompleteAuditAndImmediatelyUpdatesDashboardAndExactStock | PASS | 0.159 | None | target/surefire-reports/TEST-com.oneshop.Phase10IntegrationWebDatabaseTest.xml |
| baseline-0684 | com.oneshop.Phase10IntegrationWebDatabaseTest#tc11PeerOrderAndInventoryCannotBeReadOrMutatedByForgingAnyStoreSource(String)[1] | PASS | 0.236 | None | target/surefire-reports/TEST-com.oneshop.Phase10IntegrationWebDatabaseTest.xml |
| baseline-0685 | com.oneshop.Phase10IntegrationWebDatabaseTest#tc11PeerOrderAndInventoryCannotBeReadOrMutatedByForgingAnyStoreSource(String)[2] | PASS | 0.205 | None | target/surefire-reports/TEST-com.oneshop.Phase10IntegrationWebDatabaseTest.xml |
| baseline-0686 | com.oneshop.Phase10IntegrationWebDatabaseTest#tc11PeerOrderAndInventoryCannotBeReadOrMutatedByForgingAnyStoreSource(String)[3] | PASS | 0.16 | None | target/surefire-reports/TEST-com.oneshop.Phase10IntegrationWebDatabaseTest.xml |
| baseline-0687 | com.oneshop.Phase10IntegrationWebDatabaseTest#tc11PeerOrderAndInventoryCannotBeReadOrMutatedByForgingAnyStoreSource(String)[4] | PASS | 0.149 | None | target/surefire-reports/TEST-com.oneshop.Phase10IntegrationWebDatabaseTest.xml |
| baseline-0688 | com.oneshop.Phase10IntegrationWebDatabaseTest#failedOnlineSiblingUpdatesOnlyItsStoreDashboardAndInventoryWhileOtherStaffOrdersContinue | PASS | 0.111 | None | target/surefire-reports/TEST-com.oneshop.Phase10IntegrationWebDatabaseTest.xml |
| baseline-0689 | com.oneshop.Phase10IntegrationWebDatabaseTest#racingHttpPickupCompletionCollectsPayAtStoreOnceAndRejectsTheDuplicateWithoutStockChanges | PASS | 0.158 | None | target/surefire-reports/TEST-com.oneshop.Phase10IntegrationWebDatabaseTest.xml |
| baseline-0690 | com.oneshop.Phase10IntegrationWebDatabaseTest#absentOrInactiveAssignmentBlocksEveryStaffModuleAndMutationWithForgedStore(String)[1] | PASS | 0.124 | None | target/surefire-reports/TEST-com.oneshop.Phase10IntegrationWebDatabaseTest.xml |
| baseline-0691 | com.oneshop.Phase10IntegrationWebDatabaseTest#absentOrInactiveAssignmentBlocksEveryStaffModuleAndMutationWithForgedStore(String)[2] | PASS | 0.109 | None | target/surefire-reports/TEST-com.oneshop.Phase10IntegrationWebDatabaseTest.xml |
| baseline-0692 | com.oneshop.Phase10IntegrationWebDatabaseTest#racingHttpAdjustmentAndCheckoutMaintainOneSerialStockLedgerAndCorrectSnapshot | PASS | 0.109 | None | target/surefire-reports/TEST-com.oneshop.Phase10IntegrationWebDatabaseTest.xml |
| baseline-0693 | com.oneshop.Phase10IntegrationWebDatabaseTest#staffOperationsFollowOneMultiStoreCheckoutThroughInventoryDashboardDeliveryAndBothPickups | PASS | 0.978 | None | target/surefire-reports/TEST-com.oneshop.Phase10IntegrationWebDatabaseTest.xml |
| baseline-0694 | com.oneshop.Phase10IntegrationWebDatabaseTest#revokingOneOfMultipleAssignmentsImmediatelyRemovesPeerReadsAndWritesAcrossAllModules | ERROR | 0.057 | None | target/surefire-reports/TEST-com.oneshop.Phase10IntegrationWebDatabaseTest.xml |
| baseline-0695 | com.oneshop.Phase11AdminDatabaseIntegrationTest#adminOrderFilterMatchesSqlAcrossAllStoresAndDetailReusesSnapshotPaymentHistory | PASS | 1.039 | None | target/surefire-reports/TEST-com.oneshop.Phase11AdminDatabaseIntegrationTest.xml |
| baseline-0696 | com.oneshop.Phase11AdminDatabaseIntegrationTest#customerAndStaffCannotWriteAdminAndNoBusinessRowsChange | PASS | 0.425 | None | target/surefire-reports/TEST-com.oneshop.Phase11AdminDatabaseIntegrationTest.xml |
| baseline-0697 | com.oneshop.Phase11AdminDatabaseIntegrationTest#orderStockUserAssignmentReviewPaginationKeepsFiltersAndStableTotals | PASS | 2.689 | None | target/surefire-reports/TEST-com.oneshop.Phase11AdminDatabaseIntegrationTest.xml |
| baseline-0698 | com.oneshop.Phase11AdminDatabaseIntegrationTest#multipleAssignmentsAndStoreChangeDoNotTrustBrowserSelector | FAIL | 0.218 | None | target/surefire-reports/TEST-com.oneshop.Phase11AdminDatabaseIntegrationTest.xml |
| baseline-0699 | com.oneshop.Phase11AdminDatabaseIntegrationTest#tc18InactiveStoreRejectsNewCheckoutButAdminAndCustomerKeepHistoricalOrders | PASS | 0.704 | None | target/surefire-reports/TEST-com.oneshop.Phase11AdminDatabaseIntegrationTest.xml |
| baseline-0700 | com.oneshop.Phase11AdminDatabaseIntegrationTest#inventoryUsesExistingExactStockQueryAndFiltersEveryStoreIncludingInactive | PASS | 0.317 | None | target/surefire-reports/TEST-com.oneshop.Phase11AdminDatabaseIntegrationTest.xml |
| baseline-0701 | com.oneshop.Phase11AdminDatabaseIntegrationTest#userCreateUsesBcryptAndNeverRendersPasswordOrHash | PASS | 0.466 | None | target/surefire-reports/TEST-com.oneshop.Phase11AdminDatabaseIntegrationTest.xml |
| baseline-0702 | com.oneshop.Phase11AdminDatabaseIntegrationTest#customerAndStaffCannotReadAnyAdminPage(String)[1] | PASS | 0.265 | None | target/surefire-reports/TEST-com.oneshop.Phase11AdminDatabaseIntegrationTest.xml |
| baseline-0703 | com.oneshop.Phase11AdminDatabaseIntegrationTest#customerAndStaffCannotReadAnyAdminPage(String)[2] | PASS | 0.264 | None | target/surefire-reports/TEST-com.oneshop.Phase11AdminDatabaseIntegrationTest.xml |
| baseline-0704 | com.oneshop.Phase11AdminDatabaseIntegrationTest#customerAndStaffCannotReadAnyAdminPage(String)[3] | PASS | 0.261 | None | target/surefire-reports/TEST-com.oneshop.Phase11AdminDatabaseIntegrationTest.xml |
| baseline-0705 | com.oneshop.Phase11AdminDatabaseIntegrationTest#customerAndStaffCannotReadAnyAdminPage(String)[4] | PASS | 0.253 | None | target/surefire-reports/TEST-com.oneshop.Phase11AdminDatabaseIntegrationTest.xml |
| baseline-0706 | com.oneshop.Phase11AdminDatabaseIntegrationTest#customerAndStaffCannotReadAnyAdminPage(String)[5] | PASS | 0.255 | None | target/surefire-reports/TEST-com.oneshop.Phase11AdminDatabaseIntegrationTest.xml |
| baseline-0707 | com.oneshop.Phase11AdminDatabaseIntegrationTest#customerAndStaffCannotReadAnyAdminPage(String)[6] | PASS | 0.246 | None | target/surefire-reports/TEST-com.oneshop.Phase11AdminDatabaseIntegrationTest.xml |
| baseline-0708 | com.oneshop.Phase11AdminDatabaseIntegrationTest#customerAndStaffCannotReadAnyAdminPage(String)[7] | PASS | 0.249 | None | target/surefire-reports/TEST-com.oneshop.Phase11AdminDatabaseIntegrationTest.xml |
| baseline-0709 | com.oneshop.Phase11AdminDatabaseIntegrationTest#customerAndStaffCannotReadAnyAdminPage(String)[8] | PASS | 0.254 | None | target/surefire-reports/TEST-com.oneshop.Phase11AdminDatabaseIntegrationTest.xml |
| baseline-0710 | com.oneshop.Phase11AdminDatabaseIntegrationTest#customerAndStaffCannotReadAnyAdminPage(String)[9] | PASS | 0.272 | None | target/surefire-reports/TEST-com.oneshop.Phase11AdminDatabaseIntegrationTest.xml |
| baseline-0711 | com.oneshop.Phase11AdminDatabaseIntegrationTest#customerAndStaffCannotReadAnyAdminPage(String)[10] | PASS | 0.272 | None | target/surefire-reports/TEST-com.oneshop.Phase11AdminDatabaseIntegrationTest.xml |
| baseline-0712 | com.oneshop.Phase11AdminDatabaseIntegrationTest#customerAndStaffCannotReadAnyAdminPage(String)[11] | PASS | 0.272 | None | target/surefire-reports/TEST-com.oneshop.Phase11AdminDatabaseIntegrationTest.xml |
| baseline-0713 | com.oneshop.Phase11AdminDatabaseIntegrationTest#newAdminServicesEnforceRoleEvenWithoutHttp | PASS | 0.084 | None | target/surefire-reports/TEST-com.oneshop.Phase11AdminDatabaseIntegrationTest.xml |
| baseline-0714 | com.oneshop.Phase11AdminDatabaseIntegrationTest#invalidUserFormsDuplicateEmailAndInvalidEnumDoNotWriteOrReflectPassword | PASS | 0.287 | None | target/surefire-reports/TEST-com.oneshop.Phase11AdminDatabaseIntegrationTest.xml |
| baseline-0715 | com.oneshop.Phase11AdminDatabaseIntegrationTest#adminGetsEveryPageAndFormsUseAdminLayoutCsrf | PASS | 0.809 | None | target/surefire-reports/TEST-com.oneshop.Phase11AdminDatabaseIntegrationTest.xml |
| baseline-0716 | com.oneshop.Phase11AdminDatabaseIntegrationTest#userCannotChangeEmailPasswordOrDisableOwnAdmin | PASS | 0.274 | None | target/surefire-reports/TEST-com.oneshop.Phase11AdminDatabaseIntegrationTest.xml |
| baseline-0717 | com.oneshop.Phase11AdminDatabaseIntegrationTest#reuseStoreCrudCreatesAndEditsFinderMetadata | PASS | 0.209 | None | target/surefire-reports/TEST-com.oneshop.Phase11AdminDatabaseIntegrationTest.xml |
| baseline-0718 | com.oneshop.Phase11AdminDatabaseIntegrationTest#overviewMatchesIndependentSqlForEveryStoreIncludingInactive | PASS | 0.266 | None | target/surefire-reports/TEST-com.oneshop.Phase11AdminDatabaseIntegrationTest.xml |
| baseline-0719 | com.oneshop.Phase11AdminDatabaseIntegrationTest#assignmentsRejectCustomerAdminInactiveStaffAndInactiveStore | PASS | 0.33 | None | target/surefire-reports/TEST-com.oneshop.Phase11AdminDatabaseIntegrationTest.xml |
| baseline-0720 | com.oneshop.Phase11AdminDatabaseIntegrationTest#userRoleStatusChangesRevokeOldJwtAndDoNotRestoreOldAssignments | PASS | 0.422 | None | target/surefire-reports/TEST-com.oneshop.Phase11AdminDatabaseIntegrationTest.xml |
| baseline-0721 | com.oneshop.Phase11AdminDatabaseIntegrationTest#reviewManagementOnlyChangesVisibilityAndEscapesContent | PASS | 0.286 | None | target/surefire-reports/TEST-com.oneshop.Phase11AdminDatabaseIntegrationTest.xml |
| baseline-0722 | com.oneshop.Phase11AdminDatabaseIntegrationTest#productStoreAndStoreProductWithHistoryAreSoftDisabledAndNotHardDeleted | PASS | 0.549 | None | target/surefire-reports/TEST-com.oneshop.Phase11AdminDatabaseIntegrationTest.xml |
| baseline-0723 | com.oneshop.Phase11AdminDatabaseIntegrationTest#writesRequireCsrfForCookieAndInvalidRequestsDoNotMutate | PASS | 0.129 | None | target/surefire-reports/TEST-com.oneshop.Phase11AdminDatabaseIntegrationTest.xml |
| baseline-0724 | com.oneshop.Phase11AdminDatabaseIntegrationTest#assignmentRevocationAndReactivationAffectTheSameOldJwtImmediately | PASS | 0.267 | None | target/surefire-reports/TEST-com.oneshop.Phase11AdminDatabaseIntegrationTest.xml |
| baseline-0725 | com.oneshop.Phase12CloudinaryLiveVerification#tc18LiveBrandLogoUploadReplaceDeleteWithRealJpegAndWebp | SKIPPED | 0.0 | None | target/surefire-reports/TEST-com.oneshop.Phase12CloudinaryLiveVerification.xml |
| baseline-0726 | com.oneshop.Phase12CloudinaryLiveVerification#tc19LiveUploadRejectsInvalidFileAndOversizedFile | SKIPPED | 0.0 | None | target/surefire-reports/TEST-com.oneshop.Phase12CloudinaryLiveVerification.xml |
| baseline-0727 | com.oneshop.Phase12CloudinaryLiveVerification#tc17LiveUploadStoresOnlyUrlAndPublicIdAndRemovesOnlyItsFixture | SKIPPED | 0.0 | None | target/surefire-reports/TEST-com.oneshop.Phase12CloudinaryLiveVerification.xml |
| baseline-0728 | com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest#cancellationRejectsPreparingAndLaterAndPaidOrdersWithoutAnyWrite(String)[1] | PASS | 0.127 | None | target/surefire-reports/TEST-com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest.xml |
| baseline-0729 | com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest#cancellationRejectsPreparingAndLaterAndPaidOrdersWithoutAnyWrite(String)[2] | PASS | 0.091 | None | target/surefire-reports/TEST-com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest.xml |
| baseline-0730 | com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest#cancellationRejectsPreparingAndLaterAndPaidOrdersWithoutAnyWrite(String)[3] | PASS | 0.092 | None | target/surefire-reports/TEST-com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest.xml |
| baseline-0731 | com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest#cancellationRejectsPreparingAndLaterAndPaidOrdersWithoutAnyWrite(String)[4] | PASS | 0.101 | None | target/surefire-reports/TEST-com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest.xml |
| baseline-0732 | com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest#cancellationRejectsPreparingAndLaterAndPaidOrdersWithoutAnyWrite(String)[5] | PASS | 0.089 | None | target/surefire-reports/TEST-com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest.xml |
| baseline-0733 | com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest#cancellationRejectsPreparingAndLaterAndPaidOrdersWithoutAnyWrite(String)[6] | PASS | 0.121 | None | target/surefire-reports/TEST-com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest.xml |
| baseline-0734 | com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest#existingSqlDataHasCompleteTimelinesConservedStockAndNoDuplicateOrderMovements | PASS | 0.072 | None | target/surefire-reports/TEST-com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest.xml |
| baseline-0735 | com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest#cancellationRacesSerializePaymentFulfillmentAndInventoryWithoutDuplicateAudit(String)[1] | PASS | 0.11 | None | target/surefire-reports/TEST-com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest.xml |
| baseline-0736 | com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest#cancellationRacesSerializePaymentFulfillmentAndInventoryWithoutDuplicateAudit(String)[2] | PASS | 0.09 | None | target/surefire-reports/TEST-com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest.xml |
| baseline-0737 | com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest#cancellationRacesSerializePaymentFulfillmentAndInventoryWithoutDuplicateAudit(String)[3] | PASS | 0.085 | None | target/surefire-reports/TEST-com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest.xml |
| baseline-0738 | com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest#cancellationRacesSerializePaymentFulfillmentAndInventoryWithoutDuplicateAudit(String)[4] | PASS | 0.087 | None | target/surefire-reports/TEST-com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest.xml |
| baseline-0739 | com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest#stockOverflowRejectsCancellationAndRollsBackClosedPendingAttempt | PASS | 0.088 | None | target/surefire-reports/TEST-com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest.xml |
| baseline-0740 | com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest#inconsistentSuccessfulReceiptBlocksCustomerCancellationAtomically | PASS | 0.128 | None | target/surefire-reports/TEST-com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest.xml |
| baseline-0741 | com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest#cancellationAfterStoreIsInactiveStillRestoresTheOriginalStockAndPreservesHistory | PASS | 0.098 | None | target/surefire-reports/TEST-com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest.xml |
| baseline-0742 | com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest#customerCancellationRestoresOnlyItsStoreAndKeepsSnapshotsAndSiblings(String)[1] | PASS | 0.1 | None | target/surefire-reports/TEST-com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest.xml |
| baseline-0743 | com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest#customerCancellationRestoresOnlyItsStoreAndKeepsSnapshotsAndSiblings(String)[2] | PASS | 0.123 | None | target/surefire-reports/TEST-com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest.xml |
| baseline-0744 | com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest#customerCancellationRestoresOnlyItsStoreAndKeepsSnapshotsAndSiblings(String)[3] | PASS | 0.106 | None | target/surefire-reports/TEST-com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest.xml |
| baseline-0745 | com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest#customerCancellationRestoresOnlyItsStoreAndKeepsSnapshotsAndSiblings(String)[4] | PASS | 0.113 | None | target/surefire-reports/TEST-com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest.xml |
| baseline-0746 | com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest#customerCancellationRestoresOnlyItsStoreAndKeepsSnapshotsAndSiblings(String)[5] | PASS | 0.085 | None | target/surefire-reports/TEST-com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest.xml |
| baseline-0747 | com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest#customerCancellationRestoresOnlyItsStoreAndKeepsSnapshotsAndSiblings(String)[6] | PASS | 0.088 | None | target/surefire-reports/TEST-com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest.xml |
| baseline-0748 | com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest#customerCancellationRestoresOnlyItsStoreAndKeepsSnapshotsAndSiblings(String)[7] | PASS | 0.099 | None | target/surefire-reports/TEST-com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest.xml |
| baseline-0749 | com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest#internalCancellationPrimitivesRequireAnExistingTransaction | PASS | 0.085 | None | target/surefire-reports/TEST-com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest.xml |
| baseline-0750 | com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest#simultaneousDuplicateHttpCancellationsRestoreExactlyOnce | PASS | 0.244 | None | target/surefire-reports/TEST-com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest.xml |
| baseline-0751 | com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest#adminStockEditMustLockBeforeLoadingQuantityToPreserveConcurrentMovementLedger | PASS | 0.068 | None | target/surefire-reports/TEST-com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest.xml |
| baseline-0752 | com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest#cancellationRequiresOwnedActiveCustomerAndCsrfEvenForBearerAndEncodedPaths | PASS | 0.192 | None | target/surefire-reports/TEST-com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest.xml |
| baseline-0753 | com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest#inactiveCustomerCannotCancelUsingAnAlreadyIssuedJwt | PASS | 0.088 | None | target/surefire-reports/TEST-com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest.xml |
| baseline-0754 | com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest#concurrentCancellationsOfMultiItemOrdersWithOppositeItemOrderRestoreEverySnapshotExactlyOnce | PASS | 0.151 | None | target/surefire-reports/TEST-com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest.xml |
| baseline-0755 | com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest#failureOnSecondItemRestorationRollsBackFirstItemAndPaymentClosure | PASS | 0.099 | None | target/surefire-reports/TEST-com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest.xml |
| baseline-0756 | com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest#cancellingDifferentStoreCanCommitWhileFirstCancellationTransactionRemainsOpen | PASS | 0.138 | None | target/surefire-reports/TEST-com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest.xml |
| baseline-0757 | com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest#cancellationRollsBackRealFlushedPaymentStockOrderAndAuditAndCanRetry(String)[1] | PASS | 0.136 | None | target/surefire-reports/TEST-com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest.xml |
| baseline-0758 | com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest#cancellationRollsBackRealFlushedPaymentStockOrderAndAuditAndCanRetry(String)[2] | PASS | 0.081 | None | target/surefire-reports/TEST-com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest.xml |
| baseline-0759 | com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest#cancellationRollsBackRealFlushedPaymentStockOrderAndAuditAndCanRetry(String)[3] | PASS | 0.089 | None | target/surefire-reports/TEST-com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest.xml |
| baseline-0760 | com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest#cancellationRollsBackRealFlushedPaymentStockOrderAndAuditAndCanRetry(String)[4] | PASS | 0.084 | None | target/surefire-reports/TEST-com.oneshop.Phase12HistoryHardeningDatabaseIntegrationTest.xml |
| baseline-0761 | com.oneshop.Phase9IntegrationWebDatabaseTest#invalidMatrixPairingsAreRejectedByBusinessAndActualSqlChecks(String, String)[1] | PASS | 0.108 | None | target/surefire-reports/TEST-com.oneshop.Phase9IntegrationWebDatabaseTest.xml |
| baseline-0762 | com.oneshop.Phase9IntegrationWebDatabaseTest#invalidMatrixPairingsAreRejectedByBusinessAndActualSqlChecks(String, String)[2] | PASS | 0.114 | None | target/surefire-reports/TEST-com.oneshop.Phase9IntegrationWebDatabaseTest.xml |
| baseline-0763 | com.oneshop.Phase9IntegrationWebDatabaseTest#csrfEncodedPathsRolesAndCrossFlowPrepareAreEnforcedOverHttp | PASS | 0.414 | None | target/surefire-reports/TEST-com.oneshop.Phase9IntegrationWebDatabaseTest.xml |
| baseline-0764 | com.oneshop.Phase9IntegrationWebDatabaseTest#allThreeStaffScopesAndOtherCustomerStayIsolatedWithinSameCheckout | PASS | 0.218 | None | target/surefire-reports/TEST-com.oneshop.Phase9IntegrationWebDatabaseTest.xml |
| baseline-0765 | com.oneshop.Phase9IntegrationWebDatabaseTest#mixedFailedPaymentRestoresOnlyItsOrderAndSiblingsStillCompleteOverHttp | PASS | 0.234 | None | target/surefire-reports/TEST-com.oneshop.Phase9IntegrationWebDatabaseTest.xml |
| baseline-0766 | com.oneshop.Phase9IntegrationWebDatabaseTest#multipleAssignmentsAllowOnlyActiveStoreAndUseAuthenticatedActor | ERROR | 0.054 | None | target/surefire-reports/TEST-com.oneshop.Phase9IntegrationWebDatabaseTest.xml |
| baseline-0767 | com.oneshop.Phase9IntegrationWebDatabaseTest#composedSubsystemWritesRollbackTogetherIncludingSiblings(String)[1] | PASS | 0.088 | None | target/surefire-reports/TEST-com.oneshop.Phase9IntegrationWebDatabaseTest.xml |
| baseline-0768 | com.oneshop.Phase9IntegrationWebDatabaseTest#composedSubsystemWritesRollbackTogetherIncludingSiblings(String)[2] | PASS | 0.09 | None | target/surefire-reports/TEST-com.oneshop.Phase9IntegrationWebDatabaseTest.xml |
| baseline-0769 | com.oneshop.Phase9IntegrationWebDatabaseTest#composedSubsystemWritesRollbackTogetherIncludingSiblings(String)[3] | PASS | 0.076 | None | target/surefire-reports/TEST-com.oneshop.Phase9IntegrationWebDatabaseTest.xml |
| baseline-0770 | com.oneshop.Phase9IntegrationWebDatabaseTest#composedSubsystemWritesRollbackTogetherIncludingSiblings(String)[4] | PASS | 0.096 | None | target/surefire-reports/TEST-com.oneshop.Phase9IntegrationWebDatabaseTest.xml |
| baseline-0771 | com.oneshop.Phase9IntegrationWebDatabaseTest#sameOrderRepresentativeRacesKeepSiblingAggregatesAndNeverDuplicateReceiptsOrHistory(String)[1] | PASS | 0.082 | None | target/surefire-reports/TEST-com.oneshop.Phase9IntegrationWebDatabaseTest.xml |
| baseline-0772 | com.oneshop.Phase9IntegrationWebDatabaseTest#sameOrderRepresentativeRacesKeepSiblingAggregatesAndNeverDuplicateReceiptsOrHistory(String)[2] | PASS | 0.085 | None | target/surefire-reports/TEST-com.oneshop.Phase9IntegrationWebDatabaseTest.xml |
| baseline-0773 | com.oneshop.Phase9IntegrationWebDatabaseTest#sameOrderRepresentativeRacesKeepSiblingAggregatesAndNeverDuplicateReceiptsOrHistory(String)[3] | PASS | 0.095 | None | target/surefire-reports/TEST-com.oneshop.Phase9IntegrationWebDatabaseTest.xml |
| baseline-0774 | com.oneshop.Phase9IntegrationWebDatabaseTest#sameOrderRepresentativeRacesKeepSiblingAggregatesAndNeverDuplicateReceiptsOrHistory(String)[4] | PASS | 0.088 | None | target/surefire-reports/TEST-com.oneshop.Phase9IntegrationWebDatabaseTest.xml |
| baseline-0775 | com.oneshop.Phase9IntegrationWebDatabaseTest#sameOrderRepresentativeRacesKeepSiblingAggregatesAndNeverDuplicateReceiptsOrHistory(String)[5] | PASS | 0.098 | None | target/surefire-reports/TEST-com.oneshop.Phase9IntegrationWebDatabaseTest.xml |
| baseline-0776 | com.oneshop.Phase9IntegrationWebDatabaseTest#tc15FullHttpLifecycleWithOptionalCatalogChanges(boolean)[1] | PASS | 0.646 | None | target/surefire-reports/TEST-com.oneshop.Phase9IntegrationWebDatabaseTest.xml |
| baseline-0777 | com.oneshop.Phase9IntegrationWebDatabaseTest#tc15FullHttpLifecycleWithOptionalCatalogChanges(boolean)[2] | PASS | 0.527 | None | target/surefire-reports/TEST-com.oneshop.Phase9IntegrationWebDatabaseTest.xml |
| baseline-0778 | com.oneshop.Phase9IntegrationWebDatabaseTest#listQueryCountsStayConstantWhenThreeMoreStoreOrdersAreAddedAndDetailsRemainBounded | PASS | 0.204 | None | target/surefire-reports/TEST-com.oneshop.Phase9IntegrationWebDatabaseTest.xml |
| baseline-0779 | com.oneshop.Phase9IntegrationWebDatabaseTest#pickupSiblingCommitsWhileDeliveryPackTransactionRemainsOpen | PASS | 0.111 | None | target/surefire-reports/TEST-com.oneshop.Phase9IntegrationWebDatabaseTest.xml |
| baseline-0780 | com.oneshop.PickupDatabaseIntegrationTest#skipBackwardAndDuplicateRejectWithoutChangingRows(OrderStatus, PickupAction)[1] | PASS | 0.081 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0781 | com.oneshop.PickupDatabaseIntegrationTest#skipBackwardAndDuplicateRejectWithoutChangingRows(OrderStatus, PickupAction)[2] | PASS | 0.078 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0782 | com.oneshop.PickupDatabaseIntegrationTest#skipBackwardAndDuplicateRejectWithoutChangingRows(OrderStatus, PickupAction)[3] | PASS | 0.091 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0783 | com.oneshop.PickupDatabaseIntegrationTest#skipBackwardAndDuplicateRejectWithoutChangingRows(OrderStatus, PickupAction)[4] | PASS | 0.078 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0784 | com.oneshop.PickupDatabaseIntegrationTest#skipBackwardAndDuplicateRejectWithoutChangingRows(OrderStatus, PickupAction)[5] | PASS | 0.084 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0785 | com.oneshop.PickupDatabaseIntegrationTest#skipBackwardAndDuplicateRejectWithoutChangingRows(OrderStatus, PickupAction)[6] | PASS | 0.099 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0786 | com.oneshop.PickupDatabaseIntegrationTest#codeVisibilityOwnerAndReadOnlyListsAreEnforced | PASS | 0.194 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0787 | com.oneshop.PickupDatabaseIntegrationTest#mixedCheckoutPickupDoesNotMutateNeighborOrdersOrPayments | PASS | 0.104 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0788 | com.oneshop.PickupDatabaseIntegrationTest#sqlServerRaceSerializesReadyAndBothCompletionMethods(PaymentMethod, PickupAction, int)[1] | PASS | 0.089 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0789 | com.oneshop.PickupDatabaseIntegrationTest#sqlServerRaceSerializesReadyAndBothCompletionMethods(PaymentMethod, PickupAction, int)[2] | PASS | 0.089 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0790 | com.oneshop.PickupDatabaseIntegrationTest#sqlServerRaceSerializesReadyAndBothCompletionMethods(PaymentMethod, PickupAction, int)[3] | PASS | 0.083 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0791 | com.oneshop.PickupDatabaseIntegrationTest#sqlServerRaceSerializesReadyAndBothCompletionMethods(PaymentMethod, PickupAction, int)[4] | PASS | 0.095 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0792 | com.oneshop.PickupDatabaseIntegrationTest#sqlServerRaceSerializesReadyAndBothCompletionMethods(PaymentMethod, PickupAction, int)[5] | PASS | 0.093 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0793 | com.oneshop.PickupDatabaseIntegrationTest#sqlServerRaceSerializesReadyAndBothCompletionMethods(PaymentMethod, PickupAction, int)[6] | PASS | 0.101 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0794 | com.oneshop.PickupDatabaseIntegrationTest#sqlServerRaceSerializesReadyAndBothCompletionMethods(PaymentMethod, PickupAction, int)[7] | PASS | 0.104 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0795 | com.oneshop.PickupDatabaseIntegrationTest#sqlServerRaceSerializesReadyAndBothCompletionMethods(PaymentMethod, PickupAction, int)[8] | PASS | 0.114 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0796 | com.oneshop.PickupDatabaseIntegrationTest#sqlServerRaceSerializesReadyAndBothCompletionMethods(PaymentMethod, PickupAction, int)[9] | PASS | 0.132 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0797 | com.oneshop.PickupDatabaseIntegrationTest#inconsistentPaymentStatusAtEveryStageIsRejected(PaymentMethod, PickupAction)[1] | PASS | 0.09 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0798 | com.oneshop.PickupDatabaseIntegrationTest#inconsistentPaymentStatusAtEveryStageIsRejected(PaymentMethod, PickupAction)[2] | PASS | 0.099 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0799 | com.oneshop.PickupDatabaseIntegrationTest#inconsistentPaymentStatusAtEveryStageIsRejected(PaymentMethod, PickupAction)[3] | PASS | 0.11 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0800 | com.oneshop.PickupDatabaseIntegrationTest#inconsistentPaymentStatusAtEveryStageIsRejected(PaymentMethod, PickupAction)[4] | PASS | 0.078 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0801 | com.oneshop.PickupDatabaseIntegrationTest#inconsistentPaymentStatusAtEveryStageIsRejected(PaymentMethod, PickupAction)[5] | PASS | 0.077 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0802 | com.oneshop.PickupDatabaseIntegrationTest#inconsistentPaymentStatusAtEveryStageIsRejected(PaymentMethod, PickupAction)[6] | PASS | 0.083 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0803 | com.oneshop.PickupDatabaseIntegrationTest#successfulOfflineReceiptCannotBeCollectedOrRepairedAtAnyStage(PickupAction)[1] | PASS | 0.09 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0804 | com.oneshop.PickupDatabaseIntegrationTest#successfulOfflineReceiptCannotBeCollectedOrRepairedAtAnyStage(PickupAction)[2] | PASS | 0.093 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0805 | com.oneshop.PickupDatabaseIntegrationTest#successfulOfflineReceiptCannotBeCollectedOrRepairedAtAnyStage(PickupAction)[3] | PASS | 0.093 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0806 | com.oneshop.PickupDatabaseIntegrationTest#purchaseSnapshotsRemainAfterCatalogueChange | PASS | 0.111 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0807 | com.oneshop.PickupDatabaseIntegrationTest#existingSeedCodeConventionWorksWithoutRewritingHistoricalCode | PASS | 0.111 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0808 | com.oneshop.PickupDatabaseIntegrationTest#pendingOnlineWrongFlowScopeAndOwnershipAreProtected(PickupAction)[1] | PASS | 0.094 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0809 | com.oneshop.PickupDatabaseIntegrationTest#pendingOnlineWrongFlowScopeAndOwnershipAreProtected(PickupAction)[2] | PASS | 0.078 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0810 | com.oneshop.PickupDatabaseIntegrationTest#pendingOnlineWrongFlowScopeAndOwnershipAreProtected(PickupAction)[3] | PASS | 0.079 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0811 | com.oneshop.PickupDatabaseIntegrationTest#inactiveScopeRejectsReadAndEveryAction(String)[1] | PASS | 0.093 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0812 | com.oneshop.PickupDatabaseIntegrationTest#inactiveScopeRejectsReadAndEveryAction(String)[2] | PASS | 0.079 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0813 | com.oneshop.PickupDatabaseIntegrationTest#inactiveScopeRejectsReadAndEveryAction(String)[3] | PASS | 0.078 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0814 | com.oneshop.PickupDatabaseIntegrationTest#wrongAndMalformedCodeDoNotMutateAnyRows(String)[1] | PASS | 0.087 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0815 | com.oneshop.PickupDatabaseIntegrationTest#wrongAndMalformedCodeDoNotMutateAnyRows(String)[2] | PASS | 0.083 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0816 | com.oneshop.PickupDatabaseIntegrationTest#wrongAndMalformedCodeDoNotMutateAnyRows(String)[3] | PASS | 0.084 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0817 | com.oneshop.PickupDatabaseIntegrationTest#wrongAndMalformedCodeDoNotMutateAnyRows(String)[4] | PASS | 0.083 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0818 | com.oneshop.PickupDatabaseIntegrationTest#wrongAndMalformedCodeDoNotMutateAnyRows(String)[5] | PASS | 0.111 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0819 | com.oneshop.PickupDatabaseIntegrationTest#wrongAndMalformedCodeDoNotMutateAnyRows(String)[6] | PASS | 0.1 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0820 | com.oneshop.PickupDatabaseIntegrationTest#wrongAndMalformedCodeDoNotMutateAnyRows(String)[7] | PASS | 0.084 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0821 | com.oneshop.PickupDatabaseIntegrationTest#wrongAndMalformedCodeDoNotMutateAnyRows(String)[8] | PASS | 0.083 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0822 | com.oneshop.PickupDatabaseIntegrationTest#readyFieldsAndHistoryRollBackAtEveryFailurePoint(String)[1] | PASS | 0.1 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0823 | com.oneshop.PickupDatabaseIntegrationTest#readyFieldsAndHistoryRollBackAtEveryFailurePoint(String)[2] | PASS | 0.087 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0824 | com.oneshop.PickupDatabaseIntegrationTest#readyFieldsAndHistoryRollBackAtEveryFailurePoint(String)[3] | PASS | 0.084 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0825 | com.oneshop.PickupDatabaseIntegrationTest#readyFieldsAndHistoryRollBackAtEveryFailurePoint(String)[4] | PASS | 0.091 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0826 | com.oneshop.PickupDatabaseIntegrationTest#onlineCompletionRollsBackTimeStatusAndHistoryWithoutTouchingReceipt(String)[1] | PASS | 0.1 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0827 | com.oneshop.PickupDatabaseIntegrationTest#onlineCompletionRollsBackTimeStatusAndHistoryWithoutTouchingReceipt(String)[2] | PASS | 0.097 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0828 | com.oneshop.PickupDatabaseIntegrationTest#onlineCompletionRollsBackTimeStatusAndHistoryWithoutTouchingReceipt(String)[3] | PASS | 0.105 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0829 | com.oneshop.PickupDatabaseIntegrationTest#payAtStoreHelperRequiresExistingTransaction | PASS | 0.046 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0830 | com.oneshop.PickupDatabaseIntegrationTest#cancelledOnlinePickupIsTerminal | PASS | 0.106 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0831 | com.oneshop.PickupDatabaseIntegrationTest#payAtStoreCompletionRollsBackEveryWrite(String)[1] | PASS | 0.105 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0832 | com.oneshop.PickupDatabaseIntegrationTest#payAtStoreCompletionRollsBackEveryWrite(String)[2] | PASS | 0.116 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0833 | com.oneshop.PickupDatabaseIntegrationTest#payAtStoreCompletionRollsBackEveryWrite(String)[3] | PASS | 0.12 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0834 | com.oneshop.PickupDatabaseIntegrationTest#payAtStoreCompletionRollsBackEveryWrite(String)[4] | PASS | 0.109 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0835 | com.oneshop.PickupDatabaseIntegrationTest#payAtStoreCompletionRollsBackEveryWrite(String)[5] | PASS | 0.111 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0836 | com.oneshop.PickupDatabaseIntegrationTest#payAtStoreCompletionRollsBackEveryWrite(String)[6] | PASS | 0.109 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0837 | com.oneshop.PickupDatabaseIntegrationTest#payAtStoreCompletionRollsBackEveryWrite(String)[7] | PASS | 0.102 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0838 | com.oneshop.PickupDatabaseIntegrationTest#realHttpPickupCustomerCodeWrongCodeAndForgedFields(PaymentMethod)[1] | PASS | 0.377 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0839 | com.oneshop.PickupDatabaseIntegrationTest#realHttpPickupCustomerCodeWrongCodeAndForgedFields(PaymentMethod)[2] | PASS | 0.248 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0840 | com.oneshop.PickupDatabaseIntegrationTest#tc10AndOnlineLifecyclePreserveInventoryAndSnapshots(PaymentMethod)[1] | PASS | 0.134 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0841 | com.oneshop.PickupDatabaseIntegrationTest#tc10AndOnlineLifecyclePreserveInventoryAndSnapshots(PaymentMethod)[2] | PASS | 0.151 | None | target/surefire-reports/TEST-com.oneshop.PickupDatabaseIntegrationTest.xml |
| baseline-0842 | com.oneshop.PickupWebIntegrationTest#customerDetailShowsCodeAndTimelineAndOffersNoCompletion | PASS | 0.086 | None | target/surefire-reports/TEST-com.oneshop.PickupWebIntegrationTest.xml |
| baseline-0843 | com.oneshop.PickupWebIntegrationTest#roleAndAssignmentBoundariesBlockCustomerAdminGuestAndUnassignedStaff | PASS | 0.065 | None | target/surefire-reports/TEST-com.oneshop.PickupWebIntegrationTest.xml |
| baseline-0844 | com.oneshop.PickupWebIntegrationTest#customerOwnership404AndRoleBoundariesPreventLeaks | PASS | 0.019 | None | target/surefire-reports/TEST-com.oneshop.PickupWebIntegrationTest.xml |
| baseline-0845 | com.oneshop.PickupWebIntegrationTest#allMutationsRequireCsrfForCookiesBearerAndEncodedPaths | PASS | 0.037 | None | target/surefire-reports/TEST-com.oneshop.PickupWebIntegrationTest.xml |
| baseline-0846 | com.oneshop.ProductRequestValidationTest#anActiveProductWithPriceZeroIsRejected | PASS | 0.004 | None | target/surefire-reports/TEST-com.oneshop.ProductRequestValidationTest.xml |
| baseline-0847 | com.oneshop.ProductRequestValidationTest#anActiveProductWithARealPriceIsAccepted | PASS | 0.003 | None | target/surefire-reports/TEST-com.oneshop.ProductRequestValidationTest.xml |
| baseline-0848 | com.oneshop.ProductRequestValidationTest#aNonActiveProductMayStillHavePriceZero | PASS | 0.004 | None | target/surefire-reports/TEST-com.oneshop.ProductRequestValidationTest.xml |
| baseline-0849 | com.oneshop.RealProductSeedIntegrityTest#simulatedDataIsLabelled | PASS | 0.013 | None | target/surefire-reports/TEST-com.oneshop.RealProductSeedIntegrityTest.xml |
| baseline-0850 | com.oneshop.RealProductSeedIntegrityTest#catalogIsVariedEnoughForTheScreens | PASS | 0.039 | None | target/surefire-reports/TEST-com.oneshop.RealProductSeedIntegrityTest.xml |
| baseline-0851 | com.oneshop.RealProductSeedIntegrityTest#everyProductHasEvidence | PASS | 0.021 | None | target/surefire-reports/TEST-com.oneshop.RealProductSeedIntegrityTest.xml |
| baseline-0852 | com.oneshop.RealProductSeedIntegrityTest#everyProductImageIsOurOwnCreditedAsset | PASS | 0.027 | None | target/surefire-reports/TEST-com.oneshop.RealProductSeedIntegrityTest.xml |
| baseline-0853 | com.oneshop.RealProductSeedIntegrityTest#databaseMatchesTheSourcedManifest | PASS | 0.013 | None | target/surefire-reports/TEST-com.oneshop.RealProductSeedIntegrityTest.xml |
| baseline-0854 | com.oneshop.RepositoryHardeningDatabaseIntegrationTest#invalidProductPricesStayOnTheFormAndPreserveDatabase | PASS | 0.44 | None | target/surefire-reports/TEST-com.oneshop.RepositoryHardeningDatabaseIntegrationTest.xml |
| baseline-0855 | com.oneshop.RepositoryHardeningDatabaseIntegrationTest#conflictingProductAndStoreProductCannotCorruptCartProvenance | PASS | 0.079 | None | target/surefire-reports/TEST-com.oneshop.RepositoryHardeningDatabaseIntegrationTest.xml |
| baseline-0856 | com.oneshop.RepositoryHardeningDatabaseIntegrationTest#adminProductFormRoundTripsBothOnlinePrices | PASS | 0.144 | None | target/surefire-reports/TEST-com.oneshop.RepositoryHardeningDatabaseIntegrationTest.xml |
| baseline-0857 | com.oneshop.RepositoryHardeningDatabaseIntegrationTest#legacyAddResolvesTheSkuButUsesChainStock | PASS | 0.088 | None | target/surefire-reports/TEST-com.oneshop.RepositoryHardeningDatabaseIntegrationTest.xml |
| baseline-0858 | com.oneshop.RepositoryHardeningDatabaseIntegrationTest#chainStockCannotOverflowTheCartQuantity | PASS | 0.079 | None | target/surefire-reports/TEST-com.oneshop.RepositoryHardeningDatabaseIntegrationTest.xml |
| baseline-0859 | com.oneshop.RepositoryHardeningDatabaseIntegrationTest#outOfRangeStaffDateFiltersCannotCauseAServerError | PASS | 0.188 | None | target/surefire-reports/TEST-com.oneshop.RepositoryHardeningDatabaseIntegrationTest.xml |
| baseline-0860 | com.oneshop.RepositoryHardeningDatabaseIntegrationTest#productServiceRejectsInvalidPricesWithoutDependingOnMvcValidation | PASS | 0.057 | None | target/surefire-reports/TEST-com.oneshop.RepositoryHardeningDatabaseIntegrationTest.xml |
| baseline-0861 | com.oneshop.RepositoryHardeningDatabaseIntegrationTest#stockAdjustmentBelowLegacyReservationIsABusinessRefusal | PASS | 0.087 | None | target/surefire-reports/TEST-com.oneshop.RepositoryHardeningDatabaseIntegrationTest.xml |
| baseline-0862 | com.oneshop.RepositoryHardeningDatabaseIntegrationTest#anUnpricedCartLineIsNotSelectableLikeCheckout | PASS | 0.078 | None | target/surefire-reports/TEST-com.oneshop.RepositoryHardeningDatabaseIntegrationTest.xml |
| baseline-0863 | com.oneshop.RepositoryHardeningDatabaseIntegrationTest#aCentralCartLineWithLegacyMetadataCanGrowAcrossStores | PASS | 0.08 | None | target/surefire-reports/TEST-com.oneshop.RepositoryHardeningDatabaseIntegrationTest.xml |
| baseline-0864 | com.oneshop.ReviewDatabaseIntegrationTest#anOrderWithoutACompletedHistoryRowFallsBackToItsUpdatedAt | PASS | 0.183 | None | target/surefire-reports/TEST-com.oneshop.ReviewDatabaseIntegrationTest.xml |
| baseline-0865 | com.oneshop.ReviewDatabaseIntegrationTest#replyColumnsAreCheckedByTheDatabase | PASS | 0.182 | None | target/surefire-reports/TEST-com.oneshop.ReviewDatabaseIntegrationTest.xml |
| baseline-0866 | com.oneshop.ReviewDatabaseIntegrationTest#uniqueConstraintRejectsASecondReviewOfTheSameProduct | PASS | 0.144 | None | target/surefire-reports/TEST-com.oneshop.ReviewDatabaseIntegrationTest.xml |
| baseline-0867 | com.oneshop.ReviewDatabaseIntegrationTest#productNotInACompletedOrderCannotBeReviewed | PASS | 0.095 | None | target/surefire-reports/TEST-com.oneshop.ReviewDatabaseIntegrationTest.xml |
| baseline-0868 | com.oneshop.ReviewDatabaseIntegrationTest#ratingMustBeOneToFiveAndCommentAtMost1000Characters | PASS | 0.133 | None | target/surefire-reports/TEST-com.oneshop.ReviewDatabaseIntegrationTest.xml |
| baseline-0869 | com.oneshop.ReviewDatabaseIntegrationTest#editingTheReviewKeepsTheStoredReply | PASS | 0.141 | None | target/surefire-reports/TEST-com.oneshop.ReviewDatabaseIntegrationTest.xml |
| baseline-0870 | com.oneshop.ReviewDatabaseIntegrationTest#staffOfAnotherStoreCannotSeeOrAnswerIt | PASS | 0.209 | None | target/surefire-reports/TEST-com.oneshop.ReviewDatabaseIntegrationTest.xml |
| baseline-0871 | com.oneshop.ReviewDatabaseIntegrationTest#theOrderIsNotChangedByASecondAttempt | PASS | 0.144 | None | target/surefire-reports/TEST-com.oneshop.ReviewDatabaseIntegrationTest.xml |
| baseline-0872 | com.oneshop.ReviewDatabaseIntegrationTest#productPageShowsTheReplyWithTheStoreOfTheOrder | PASS | 0.284 | None | target/surefire-reports/TEST-com.oneshop.ReviewDatabaseIntegrationTest.xml |
| baseline-0873 | com.oneshop.ReviewDatabaseIntegrationTest#blankCommentIsStoredAsNull | PASS | 0.156 | None | target/surefire-reports/TEST-com.oneshop.ReviewDatabaseIntegrationTest.xml |
| baseline-0874 | com.oneshop.ReviewDatabaseIntegrationTest#hiddenReviewsAreNotShownCountedOrAveraged | PASS | 0.155 | None | target/surefire-reports/TEST-com.oneshop.ReviewDatabaseIntegrationTest.xml |
| baseline-0875 | com.oneshop.ReviewDatabaseIntegrationTest#cancelledOrderOrSomeoneElsesPurchaseDoesNotCount | PASS | 0.156 | None | target/surefire-reports/TEST-com.oneshop.ReviewDatabaseIntegrationTest.xml |
| baseline-0876 | com.oneshop.ReviewDatabaseIntegrationTest#unknownProductIsNotFound | PASS | 0.158 | None | target/surefire-reports/TEST-com.oneshop.ReviewDatabaseIntegrationTest.xml |
| baseline-0877 | com.oneshop.ReviewDatabaseIntegrationTest#whenCompletionMomentsTieTheHigherOrderIdWins | PASS | 0.232 | None | target/surefire-reports/TEST-com.oneshop.ReviewDatabaseIntegrationTest.xml |
| baseline-0878 | com.oneshop.ReviewDatabaseIntegrationTest#staffAdminAndAnInactiveCustomerAreRefused | PASS | 0.145 | None | target/surefire-reports/TEST-com.oneshop.ReviewDatabaseIntegrationTest.xml |
| baseline-0879 | com.oneshop.ReviewDatabaseIntegrationTest#replyLengthAndWhitespaceRulesAndCustomerIsRefused | PASS | 0.144 | None | target/surefire-reports/TEST-com.oneshop.ReviewDatabaseIntegrationTest.xml |
| baseline-0880 | com.oneshop.ReviewDatabaseIntegrationTest#staffOfTheStoreAnswersTrimmedAndCanEditTheAnswer | PASS | 0.127 | None | target/surefire-reports/TEST-com.oneshop.ReviewDatabaseIntegrationTest.xml |
| baseline-0881 | com.oneshop.ReviewDatabaseIntegrationTest#reviewWithoutAnOrderOrHiddenIsAdminOnly | PASS | 0.121 | None | target/surefire-reports/TEST-com.oneshop.ReviewDatabaseIntegrationTest.xml |
| baseline-0882 | com.oneshop.ReviewDatabaseIntegrationTest#withoutAnOrderTheMostRecentCompletedOneIsUsed | PASS | 0.146 | None | target/surefire-reports/TEST-com.oneshop.ReviewDatabaseIntegrationTest.xml |
| baseline-0883 | com.oneshop.ReviewDatabaseIntegrationTest#reviewIsAttachedToTheOrderTheFormWasOpenedFrom | PASS | 0.134 | None | target/surefire-reports/TEST-com.oneshop.ReviewDatabaseIntegrationTest.xml |
| baseline-0884 | com.oneshop.ReviewDatabaseIntegrationTest#adminAnswersAnyReviewAndTheLatestEditorIsRecorded | PASS | 0.163 | None | target/surefire-reports/TEST-com.oneshop.ReviewDatabaseIntegrationTest.xml |
| baseline-0885 | com.oneshop.ReviewDatabaseIntegrationTest#customerWhoCompletedAnOrderReviewsTheProductOnceAndItBecomesVisible | PASS | 0.147 | None | target/surefire-reports/TEST-com.oneshop.ReviewDatabaseIntegrationTest.xml |
| baseline-0886 | com.oneshop.ReviewDatabaseIntegrationTest#orderPageOffersOnlyTheProductsOfTheOwnersCompletedOrder | PASS | 0.135 | None | target/surefire-reports/TEST-com.oneshop.ReviewDatabaseIntegrationTest.xml |
| baseline-0887 | com.oneshop.ReviewDatabaseIntegrationTest#customerCannotAttachAReviewToAnOrderThatIsNotTheirCompletedPurchaseOfTheProduct | PASS | 0.148 | None | target/surefire-reports/TEST-com.oneshop.ReviewDatabaseIntegrationTest.xml |
| baseline-0888 | com.oneshop.ReviewV3ConcurrencyDatabaseIntegrationTest#twoConcurrentReviewsOfOneItemCommitExactlyOneReview | PASS | 0.317 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3ConcurrencyDatabaseIntegrationTest.xml |
| baseline-0889 | com.oneshop.ReviewV3ConcurrencyDatabaseIntegrationTest#sameSkuDifferentReceivedItemsBothCommitReviews | PASS | 0.325 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3ConcurrencyDatabaseIntegrationTest.xml |
| baseline-0890 | com.oneshop.ReviewV3ConcurrencyDatabaseIntegrationTest#moderationAndReplyShareTheReviewLock | PASS | 0.312 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3ConcurrencyDatabaseIntegrationTest.xml |
| baseline-0891 | com.oneshop.ReviewV3ConcurrencyDatabaseIntegrationTest#twoStaffRepliesSerializeIntoOneOfficialRow | ERROR | 0.044 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3ConcurrencyDatabaseIntegrationTest.xml |
| baseline-0892 | com.oneshop.ReviewV3DatabaseIntegrationTest#inconsistentHistoricalUserOrProductNeverGrantsStaffScope | PASS | 0.213 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3DatabaseIntegrationTest.xml |
| baseline-0893 | com.oneshop.ReviewV3DatabaseIntegrationTest#refundOrCancellationNeverGrantsReviewEligibility | PASS | 0.128 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3DatabaseIntegrationTest.xml |
| baseline-0894 | com.oneshop.ReviewV3DatabaseIntegrationTest#multipleStoreAssignmentsAllowOnlyThoseStores | ERROR | 0.12 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3DatabaseIntegrationTest.xml |
| baseline-0895 | com.oneshop.ReviewV3DatabaseIntegrationTest#sqlUniqueAndForeignKeysRejectInvalidRowsWithoutAffectingExistingRows | PASS | 0.18 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3DatabaseIntegrationTest.xml |
| baseline-0896 | com.oneshop.ReviewV3DatabaseIntegrationTest#sourceMismatchCannotGrantReplyAuthorityOrAcceptCustomerSubmission | PASS | 0.184 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3DatabaseIntegrationTest.xml |
| baseline-0897 | com.oneshop.ReviewV3DatabaseIntegrationTest#orderAndProductContextCannotBeForged | PASS | 0.121 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3DatabaseIntegrationTest.xml |
| baseline-0898 | com.oneshop.ReviewV3DatabaseIntegrationTest#invalidRatingRefusedAtServiceBoundary(Integer)[1] | PASS | 0.117 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3DatabaseIntegrationTest.xml |
| baseline-0899 | com.oneshop.ReviewV3DatabaseIntegrationTest#invalidRatingRefusedAtServiceBoundary(Integer)[2] | PASS | 0.12 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3DatabaseIntegrationTest.xml |
| baseline-0900 | com.oneshop.ReviewV3DatabaseIntegrationTest#invalidRatingRefusedAtServiceBoundary(Integer)[3] | PASS | 0.122 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3DatabaseIntegrationTest.xml |
| baseline-0901 | com.oneshop.ReviewV3DatabaseIntegrationTest#invalidRatingRefusedAtServiceBoundary(Integer)[4] | PASS | 0.114 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3DatabaseIntegrationTest.xml |
| baseline-0902 | com.oneshop.ReviewV3DatabaseIntegrationTest#staffQueueAndDetailUseFulfillmentStoreNotOrderStore | PASS | 0.131 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3DatabaseIntegrationTest.xml |
| baseline-0903 | com.oneshop.ReviewV3DatabaseIntegrationTest#inactiveCustomerIsRejectedEvenWithAStaleAuthenticatedContext | PASS | 0.165 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3DatabaseIntegrationTest.xml |
| baseline-0904 | com.oneshop.ReviewV3DatabaseIntegrationTest#unauthorizedRolesCannotModerateOrSubmit | PASS | 0.216 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3DatabaseIntegrationTest.xml |
| baseline-0905 | com.oneshop.ReviewV3DatabaseIntegrationTest#aFulfillmentFromAnotherOrderCannotBeUsedAsThePurchasedSource | PASS | 0.25 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3DatabaseIntegrationTest.xml |
| baseline-0906 | com.oneshop.ReviewV3DatabaseIntegrationTest#staffCreatesAndEditsOneDedicatedReplyWithoutEditingTheReview | PASS | 0.174 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3DatabaseIntegrationTest.xml |
| baseline-0907 | com.oneshop.ReviewV3DatabaseIntegrationTest#completedPartIsReviewableWhileOtherPartShips | PASS | 0.158 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3DatabaseIntegrationTest.xml |
| baseline-0908 | com.oneshop.ReviewV3DatabaseIntegrationTest#legacyWithoutOrderItemIsVisibleToAdminAndPublicButNotStaff | PASS | 0.185 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3DatabaseIntegrationTest.xml |
| baseline-0909 | com.oneshop.ReviewV3DatabaseIntegrationTest#contentBoundaryAndEscapableMarkupAreStoredAsPlainText | PASS | 0.137 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3DatabaseIntegrationTest.xml |
| baseline-0910 | com.oneshop.ReviewV3DatabaseIntegrationTest#adminModerationPreservesReplyAndCannotReplyEvenThroughService | PASS | 0.138 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3DatabaseIntegrationTest.xml |
| baseline-0911 | com.oneshop.ReviewV3DatabaseIntegrationTest#editingAProvenLegacyReviewPreservesItsOriginalReplyColumns | PASS | 0.147 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3DatabaseIntegrationTest.xml |
| baseline-0912 | com.oneshop.ReviewV3DatabaseIntegrationTest#sameSkuFromTwoCompletedStoresHasTwoDistinctReviewsAndReplyScopes | PASS | 0.154 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3DatabaseIntegrationTest.xml |
| baseline-0913 | com.oneshop.ReviewV3DatabaseIntegrationTest#noPartReceivedMeansNoFormsAndSubmissionRefused | PASS | 0.1 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3DatabaseIntegrationTest.xml |
| baseline-0914 | com.oneshop.ReviewV3DatabaseIntegrationTest#inactiveStoreOrStaffAccountCannotReply | PASS | 0.154 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3DatabaseIntegrationTest.xml |
| baseline-0915 | com.oneshop.ReviewV3DatabaseIntegrationTest#customerCannotReviewAnotherCustomersItemOrForgeEmail | PASS | 0.121 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3DatabaseIntegrationTest.xml |
| baseline-0916 | com.oneshop.ReviewV3DatabaseIntegrationTest#duplicateItemRejectedEvenAfterModerationHidesTheOriginal | PASS | 0.141 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3DatabaseIntegrationTest.xml |
| baseline-0917 | com.oneshop.ReviewV3DatabaseIntegrationTest#sameSkuReceivedLineDoesNotGrantPermissionForShippingLine | PASS | 0.127 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3DatabaseIntegrationTest.xml |
| baseline-0918 | com.oneshop.ReviewV3DatabaseIntegrationTest#invalidReplyAndRolledBackReplyEditKeepTheExistingReply | PASS | 0.149 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3DatabaseIntegrationTest.xml |
| baseline-0919 | com.oneshop.ReviewV3DatabaseIntegrationTest#reviewAndReplyFlushesRollbackAtomically | PASS | 0.133 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3DatabaseIntegrationTest.xml |
| baseline-0920 | com.oneshop.ReviewV3DatabaseIntegrationTest#revokedAssignmentBlocksReplyOnNextRequest | PASS | 0.133 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3DatabaseIntegrationTest.xml |
| baseline-0921 | com.oneshop.ReviewV3HttpDatabaseIntegrationTest#historicalReviewWithoutItemRendersSafelyAndNeverGrantsStaffAuthority | PASS | 0.608 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3HttpDatabaseIntegrationTest.xml |
| baseline-0922 | com.oneshop.ReviewV3HttpDatabaseIntegrationTest#cookieAndBearerReviewFormsBothRequireCsrf | PASS | 0.395 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3HttpDatabaseIntegrationTest.xml |
| baseline-0923 | com.oneshop.ReviewV3HttpDatabaseIntegrationTest#adminModeratesButCannotReplyAndHiddenReviewRetainsItsReply | PASS | 0.535 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3HttpDatabaseIntegrationTest.xml |
| baseline-0924 | com.oneshop.ReviewV3HttpDatabaseIntegrationTest#partialOrderAndProductPagesOfferOnlyReceivedItemForms | PASS | 0.29 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3HttpDatabaseIntegrationTest.xml |
| baseline-0925 | com.oneshop.ReviewV3HttpDatabaseIntegrationTest#ratingLengthAndMissingItemAreRejectedBeforeWriting | PASS | 0.45 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3HttpDatabaseIntegrationTest.xml |
| baseline-0926 | com.oneshop.ReviewV3HttpDatabaseIntegrationTest#customerIdorAndInvalidOrderContextReturn404 | PASS | 0.378 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3HttpDatabaseIntegrationTest.xml |
| baseline-0927 | com.oneshop.ReviewV3HttpDatabaseIntegrationTest#submissionDerivesOwnerAndSourceFromItemAndEscapesProductOutput | PASS | 0.293 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3HttpDatabaseIntegrationTest.xml |
| baseline-0928 | com.oneshop.ReviewV3HttpDatabaseIntegrationTest#staffReplyAndDetailUseActualSourceAndCrossStoreGets403 | PASS | 0.539 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3HttpDatabaseIntegrationTest.xml |
| baseline-0929 | com.oneshop.ReviewV3HttpDatabaseIntegrationTest#revokedAssignmentIsEnforcedWithThePreviouslyIssuedJwt | PASS | 0.279 | None | target/surefire-reports/TEST-com.oneshop.ReviewV3HttpDatabaseIntegrationTest.xml |
| baseline-0930 | com.oneshop.ReviewWebIntegrationTest#reviewFromTheOrderPageGoesBackToTheOrder | PASS | 0.067 | None | target/surefire-reports/TEST-com.oneshop.ReviewWebIntegrationTest.xml |
| baseline-0931 | com.oneshop.ReviewWebIntegrationTest#productPageWithNoReviewsSaysSo | PASS | 0.015 | None | target/surefire-reports/TEST-com.oneshop.ReviewWebIntegrationTest.xml |
| baseline-0932 | com.oneshop.ReviewWebIntegrationTest#businessRejectionIsShownAsAGenericCodeNotAsArbitraryText | PASS | 0.015 | None | target/surefire-reports/TEST-com.oneshop.ReviewWebIntegrationTest.xml |
| baseline-0933 | com.oneshop.ReviewWebIntegrationTest#staffWithoutAssignmentIsStoppedBeforeTheReplyController | PASS | 0.015 | None | target/surefire-reports/TEST-com.oneshop.ReviewWebIntegrationTest.xml |
| baseline-0934 | com.oneshop.ReviewWebIntegrationTest#reviewFormAppearsOnlyWhenTheServiceSaysTheCustomerCanReview | PASS | 0.02 | None | target/surefire-reports/TEST-com.oneshop.ReviewWebIntegrationTest.xml |
| baseline-0935 | com.oneshop.ReviewWebIntegrationTest#completedOrderOffersAFormForEachProductNotReviewedYet | PASS | 0.024 | None | target/surefire-reports/TEST-com.oneshop.ReviewWebIntegrationTest.xml |
| baseline-0936 | com.oneshop.ReviewWebIntegrationTest#invalidRatingNeverReachesTheService | PASS | 0.033 | None | target/surefire-reports/TEST-com.oneshop.ReviewWebIntegrationTest.xml |
| baseline-0937 | com.oneshop.ReviewWebIntegrationTest#assignedStaffReplyReachesTheServiceWithTheTextOnlyAndIsSentBackToTheList | PASS | 0.017 | None | target/surefire-reports/TEST-com.oneshop.ReviewWebIntegrationTest.xml |
| baseline-0938 | com.oneshop.ReviewWebIntegrationTest#customerReviewIsSubmittedForThePrincipalAndSentBackToTheProduct | PASS | 0.005 | None | target/surefire-reports/TEST-com.oneshop.ReviewWebIntegrationTest.xml |
| baseline-0939 | com.oneshop.ReviewWebIntegrationTest#guestAndNonCustomerRolesCannotPostAReview | PASS | 0.019 | None | target/surefire-reports/TEST-com.oneshop.ReviewWebIntegrationTest.xml |
| baseline-0940 | com.oneshop.ReviewWebIntegrationTest#adminReplyUsesTheSameServiceAndIsCsrfProtected | PASS | 0.004 | None | target/surefire-reports/TEST-com.oneshop.ReviewWebIntegrationTest.xml |
| baseline-0941 | com.oneshop.ReviewWebIntegrationTest#postWithoutCsrfTokenIsRefused | PASS | 0.005 | None | target/surefire-reports/TEST-com.oneshop.ReviewWebIntegrationTest.xml |
| baseline-0942 | com.oneshop.ReviewWebIntegrationTest#staffReviewListRendersTheReplyFormAndEscapesContent | PASS | 0.017 | None | target/surefire-reports/TEST-com.oneshop.ReviewWebIntegrationTest.xml |
| baseline-0943 | com.oneshop.ReviewWebIntegrationTest#customerAndGuestCannotUseTheStaffReviewRoutes | PASS | 0.012 | None | target/surefire-reports/TEST-com.oneshop.ReviewWebIntegrationTest.xml |
| baseline-0944 | com.oneshop.ReviewWebIntegrationTest#productPageListsVisibleReviewsAndEscapesTheirContent | PASS | 0.012 | None | target/surefire-reports/TEST-com.oneshop.ReviewWebIntegrationTest.xml |
| baseline-0945 | com.oneshop.security.jwt.JwtServiceTest#missingOrWeakSecretFailsFast | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.security.jwt.JwtServiceTest.xml |
| baseline-0946 | com.oneshop.security.jwt.JwtServiceTest#tokenStaysValidWhenTheEmailOrUserNameChanges | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.security.jwt.JwtServiceTest.xml |
| baseline-0947 | com.oneshop.security.jwt.JwtServiceTest#tokenOfAnotherUserIsRejected | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.security.jwt.JwtServiceTest.xml |
| baseline-0948 | com.oneshop.security.jwt.JwtServiceTest#tokenSignedWithAnotherSecretIsRejected | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.security.jwt.JwtServiceTest.xml |
| baseline-0949 | com.oneshop.security.jwt.JwtServiceTest#generatedTokenIsValidAndCarriesTheUserId | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.security.jwt.JwtServiceTest.xml |
| baseline-0950 | com.oneshop.security.jwt.JwtServiceTest#expiredTokenIsRejected | PASS | 0.004 | None | target/surefire-reports/TEST-com.oneshop.security.jwt.JwtServiceTest.xml |
| baseline-0951 | com.oneshop.security.jwt.JwtServiceTest#tokenIsOnlyIssuedForAnApplicationUserWithAnId | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.security.jwt.JwtServiceTest.xml |
| baseline-0952 | com.oneshop.service.allocation.AllocationEngineTest#noNeedlessSplit | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.allocation.AllocationEngineTest.xml |
| baseline-0953 | com.oneshop.service.allocation.AllocationEngineTest#signatureIdentifiesThePlan | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.allocation.AllocationEngineTest.xml |
| baseline-0954 | com.oneshop.service.allocation.AllocationEngineTest#noStoreProductIsUsedTwice | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.allocation.AllocationEngineTest.xml |
| baseline-0955 | com.oneshop.service.allocation.AllocationEngineTest#oneSkuOfSeveralNeedsASplit | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.allocation.AllocationEngineTest.xml |
| baseline-0956 | com.oneshop.service.allocation.AllocationEngineTest#oneStoreBeatsASplit | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.allocation.AllocationEngineTest.xml |
| baseline-0957 | com.oneshop.service.allocation.AllocationEngineTest#twoStoresCooperate | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.allocation.AllocationEngineTest.xml |
| baseline-0958 | com.oneshop.service.allocation.AllocationEngineTest#pickupAtUnknownStoreIsRefused | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.allocation.AllocationEngineTest.xml |
| baseline-0959 | com.oneshop.service.allocation.AllocationEngineTest#sameSkuIsSplitOverThreeStoresWithoutOverTaking | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.allocation.AllocationEngineTest.xml |
| baseline-0960 | com.oneshop.service.allocation.AllocationEngineTest#greedyPrefersTheStoreCoveringMostLines | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.allocation.AllocationEngineTest.xml |
| baseline-0961 | com.oneshop.service.allocation.AllocationEngineTest#invariantsOnGeneratedData | PASS | 0.029 | None | target/surefire-reports/TEST-com.oneshop.service.allocation.AllocationEngineTest.xml |
| baseline-0962 | com.oneshop.service.allocation.AllocationEngineTest#outOfStockFailsWholeCart | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.allocation.AllocationEngineTest.xml |
| baseline-0963 | com.oneshop.service.allocation.AllocationEngineTest#pickupNeedsTheChosenStoreToHoldEverything | PASS | 0.003 | None | target/surefire-reports/TEST-com.oneshop.service.allocation.AllocationEngineTest.xml |
| baseline-0964 | com.oneshop.service.allocation.AllocationEngineTest#sameSkuIsSplitBetweenTwoStores | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.allocation.AllocationEngineTest.xml |
| baseline-0965 | com.oneshop.service.allocation.AllocationEngineTest#oneStoreSuppliesTheWholeCart | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.allocation.AllocationEngineTest.xml |
| baseline-0966 | com.oneshop.service.allocation.AllocationEngineTest#emptyInputs | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.allocation.AllocationEngineTest.xml |
| baseline-0967 | com.oneshop.service.allocation.AllocationEngineTest#threeStoresCooperate | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.allocation.AllocationEngineTest.xml |
| baseline-0968 | com.oneshop.service.allocation.AllocationEngineTest#onlyAvailableQuantityCounts | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.allocation.AllocationEngineTest.xml |
| baseline-0969 | com.oneshop.service.allocation.AllocationEngineTest#noTransferBetweenStores | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.allocation.AllocationEngineTest.xml |
| baseline-0970 | com.oneshop.service.allocation.AllocationEngineTest#totalShortageFailsTheWholeCart | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.allocation.AllocationEngineTest.xml |
| baseline-0971 | com.oneshop.service.allocation.AllocationEngineTest#tieBreakIsDeterministic | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.allocation.AllocationEngineTest.xml |
| baseline-0972 | com.oneshop.service.allocation.ShippingAddressValidatorTest#lengthAndWhitespace | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.allocation.ShippingAddressValidatorTest.xml |
| baseline-0973 | com.oneshop.service.allocation.ShippingAddressValidatorTest#unusableTextIsRefused(String)[1] | PASS | 0.004 | None | target/surefire-reports/TEST-com.oneshop.service.allocation.ShippingAddressValidatorTest.xml |
| baseline-0974 | com.oneshop.service.allocation.ShippingAddressValidatorTest#unusableTextIsRefused(String)[2] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.allocation.ShippingAddressValidatorTest.xml |
| baseline-0975 | com.oneshop.service.allocation.ShippingAddressValidatorTest#unusableTextIsRefused(String)[3] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.allocation.ShippingAddressValidatorTest.xml |
| baseline-0976 | com.oneshop.service.allocation.ShippingAddressValidatorTest#unusableTextIsRefused(String)[4] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.allocation.ShippingAddressValidatorTest.xml |
| baseline-0977 | com.oneshop.service.allocation.ShippingAddressValidatorTest#unusableTextIsRefused(String)[5] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.allocation.ShippingAddressValidatorTest.xml |
| baseline-0978 | com.oneshop.service.allocation.ShippingAddressValidatorTest#unusableTextIsRefused(String)[6] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.allocation.ShippingAddressValidatorTest.xml |
| baseline-0979 | com.oneshop.service.allocation.ShippingAddressValidatorTest#unusableTextIsRefused(String)[7] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.allocation.ShippingAddressValidatorTest.xml |
| baseline-0980 | com.oneshop.service.allocation.ShippingAddressValidatorTest#unusableTextIsRefused(String)[8] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.allocation.ShippingAddressValidatorTest.xml |
| baseline-0981 | com.oneshop.service.allocation.ShippingAddressValidatorTest#unusableTextIsRefused(String)[9] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.allocation.ShippingAddressValidatorTest.xml |
| baseline-0982 | com.oneshop.service.allocation.ShippingAddressValidatorTest#realLookingAddressesAreAccepted(String)[1] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.allocation.ShippingAddressValidatorTest.xml |
| baseline-0983 | com.oneshop.service.allocation.ShippingAddressValidatorTest#realLookingAddressesAreAccepted(String)[2] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.allocation.ShippingAddressValidatorTest.xml |
| baseline-0984 | com.oneshop.service.allocation.ShippingAddressValidatorTest#realLookingAddressesAreAccepted(String)[3] | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.allocation.ShippingAddressValidatorTest.xml |
| baseline-0985 | com.oneshop.service.allocation.ShippingAddressValidatorTest#realLookingAddressesAreAccepted(String)[4] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.allocation.ShippingAddressValidatorTest.xml |
| baseline-0986 | com.oneshop.service.CartServiceRetryTest#losingTheRaceToCreateTheCartIsRetriedOnTheCartThatWon | PASS | 0.015 | None | target/surefire-reports/TEST-com.oneshop.service.CartServiceRetryTest.xml |
| baseline-0987 | com.oneshop.service.CartServiceRetryTest#losingTheRaceToCreateTheLineAddsToTheLineThatWon | PASS | 0.004 | None | target/surefire-reports/TEST-com.oneshop.service.CartServiceRetryTest.xml |
| baseline-0988 | com.oneshop.service.CartServiceRetryTest#aBusinessRefusalIsNotRetried | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.CartServiceRetryTest.xml |
| baseline-0989 | com.oneshop.service.CartServiceRetryTest#theRetryStillChecksStockAgainstTheMergedQuantity | PASS | 0.003 | None | target/surefire-reports/TEST-com.oneshop.service.CartServiceRetryTest.xml |
| baseline-0990 | com.oneshop.service.CartServiceV3Test#addItem_withProductId_successfullyAddsToCart | PASS | 0.17 | None | target/surefire-reports/TEST-com.oneshop.service.CartServiceV3Test.xml |
| baseline-0991 | com.oneshop.service.CartServiceV3Test#getCart_setsCorrectStatuses | PASS | 0.016 | None | target/surefire-reports/TEST-com.oneshop.service.CartServiceV3Test.xml |
| baseline-0992 | com.oneshop.service.CartServiceV3Test#updateItemQuantity_rejectsWhenExceedsStock | PASS | 0.003 | None | target/surefire-reports/TEST-com.oneshop.service.CartServiceV3Test.xml |
| baseline-0993 | com.oneshop.service.CartServiceV3Test#addItem_rejectsWhenExceedsChainStock | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.CartServiceV3Test.xml |
| baseline-0994 | com.oneshop.service.CartServiceV3Test#getCart_returnsFlatItemsListAndCalculatesTotals | PASS | 0.003 | None | target/surefire-reports/TEST-com.oneshop.service.CartServiceV3Test.xml |
| baseline-0995 | com.oneshop.service.CartServiceV3Test#updateItemQuantity_successWithinChainStock | PASS | 0.003 | None | target/surefire-reports/TEST-com.oneshop.service.CartServiceV3Test.xml |
| baseline-0996 | com.oneshop.service.CartServiceV3Test#addItem_sumsQuantity_whenProductAlreadyInCart | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.CartServiceV3Test.xml |
| baseline-0997 | com.oneshop.service.CategoryServiceTest#createCategory_withImage_uploadFails_doesNotSaveCategory | PASS | 0.488 | None | target/surefire-reports/TEST-com.oneshop.service.CategoryServiceTest.xml |
| baseline-0998 | com.oneshop.service.CategoryServiceTest#createCategory_withImage_success | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.CategoryServiceTest.xml |
| baseline-0999 | com.oneshop.service.CategoryServiceTest#removeCategoryImage_whenPublicIdNotNull_deletesFromCloudinaryAndClearsDb | PASS | 0.004 | None | target/surefire-reports/TEST-com.oneshop.service.CategoryServiceTest.xml |
| baseline-1000 | com.oneshop.service.CategoryServiceTest#updateCategory_withNewImage_whenDbSaveFails_cleansUpNewImageAndKeepsOld | PASS | 0.003 | None | target/surefire-reports/TEST-com.oneshop.service.CategoryServiceTest.xml |
| baseline-1001 | com.oneshop.service.CategoryServiceTest#updateCategory_withoutNewImage_keepsExistingImage | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.CategoryServiceTest.xml |
| baseline-1002 | com.oneshop.service.CategoryServiceTest#updateCategory_withNewImage_whenOldImageHasNullPublicId_doesNotCallCloudinaryDelete | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.CategoryServiceTest.xml |
| baseline-1003 | com.oneshop.service.CategoryServiceTest#removeCategoryImage_whenPublicIdNull_clearsDbWithoutCallingCloudinary | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.CategoryServiceTest.xml |
| baseline-1004 | com.oneshop.service.CategoryServiceTest#createCategory_withoutImage | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.CategoryServiceTest.xml |
| baseline-1005 | com.oneshop.service.CategoryServiceTest#updateCategory_withNewImage_replacesAndDeletesOldOnCloudinary | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.CategoryServiceTest.xml |
| baseline-1006 | com.oneshop.service.CloudinaryServiceTest#otherProviderFailuresOnUploadBecomeImageStorageException | PASS | 0.371 | None | target/surefire-reports/TEST-com.oneshop.service.CloudinaryServiceTest.xml |
| baseline-1007 | com.oneshop.service.CloudinaryServiceTest#uploadNonImageDisguisedAsPngIsRejectedByFileSignature | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.CloudinaryServiceTest.xml |
| baseline-1008 | com.oneshop.service.CloudinaryServiceTest#validJpegAndWebpPassValidation | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.CloudinaryServiceTest.xml |
| baseline-1009 | com.oneshop.service.CloudinaryServiceTest#applicationCanBeBuiltWithoutCredentialsButUploadFailsClearly | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.CloudinaryServiceTest.xml |
| baseline-1010 | com.oneshop.service.CloudinaryServiceTest#uploadSvgFileIsRejectedLocally | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.CloudinaryServiceTest.xml |
| baseline-1011 | com.oneshop.service.CloudinaryServiceTest#uploadOversizedFileFailsAsBadRequest | PASS | 0.017 | None | target/surefire-reports/TEST-com.oneshop.service.CloudinaryServiceTest.xml |
| baseline-1012 | com.oneshop.service.CloudinaryServiceTest#configurationIsBuiltFromProperties | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.CloudinaryServiceTest.xml |
| baseline-1013 | com.oneshop.service.CloudinaryServiceTest#timeoutConfiguredInCloudinary | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.CloudinaryServiceTest.xml |
| baseline-1014 | com.oneshop.service.CloudinaryServiceTest#providerFailureOnDeleteBecomesImageStorageException | PASS | 0.003 | None | target/surefire-reports/TEST-com.oneshop.service.CloudinaryServiceTest.xml |
| baseline-1015 | com.oneshop.service.CloudinaryServiceTest#successfulUploadReturnsUrlAndPublicId | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.CloudinaryServiceTest.xml |
| baseline-1016 | com.oneshop.service.CloudinaryServiceTest#transformUrlInsertsTransformationOnlyForCloudinaryUploads | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.CloudinaryServiceTest.xml |
| baseline-1017 | com.oneshop.service.CloudinaryServiceTest#contentCloudinaryRefusesAsNotAnImageBecomesABadRequest | PASS | 0.004 | None | target/surefire-reports/TEST-com.oneshop.service.CloudinaryServiceTest.xml |
| baseline-1018 | com.oneshop.service.CloudinaryServiceTest#timeoutOnUploadThrowsImageStorageException | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.CloudinaryServiceTest.xml |
| baseline-1019 | com.oneshop.service.CloudinaryServiceTest#uploadEmptyFileFailsAsBadRequest | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.CloudinaryServiceTest.xml |
| baseline-1020 | com.oneshop.service.CloudinaryServiceTest#ioFailureOnUploadStaysAnImageStorageException | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.CloudinaryServiceTest.xml |
| baseline-1021 | com.oneshop.service.CodPaymentServiceTest#cannotCollectEarlyOrAtTerminalState(OrderStatus)[1] | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.CodPaymentServiceTest.xml |
| baseline-1022 | com.oneshop.service.CodPaymentServiceTest#cannotCollectEarlyOrAtTerminalState(OrderStatus)[2] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.CodPaymentServiceTest.xml |
| baseline-1023 | com.oneshop.service.CodPaymentServiceTest#cannotCollectEarlyOrAtTerminalState(OrderStatus)[3] | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.CodPaymentServiceTest.xml |
| baseline-1024 | com.oneshop.service.CodPaymentServiceTest#cannotCollectEarlyOrAtTerminalState(OrderStatus)[4] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.CodPaymentServiceTest.xml |
| baseline-1025 | com.oneshop.service.CodPaymentServiceTest#cannotCollectEarlyOrAtTerminalState(OrderStatus)[5] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.CodPaymentServiceTest.xml |
| baseline-1026 | com.oneshop.service.CodPaymentServiceTest#cannotCollectEarlyOrAtTerminalState(OrderStatus)[6] | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.CodPaymentServiceTest.xml |
| baseline-1027 | com.oneshop.service.CodPaymentServiceTest#cannotCollectEarlyOrAtTerminalState(OrderStatus)[7] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.CodPaymentServiceTest.xml |
| baseline-1028 | com.oneshop.service.CodPaymentServiceTest#cannotCollectEarlyOrAtTerminalState(OrderStatus)[8] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.CodPaymentServiceTest.xml |
| baseline-1029 | com.oneshop.service.CodPaymentServiceTest#cannotCollectEarlyOrAtTerminalState(OrderStatus)[9] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.CodPaymentServiceTest.xml |
| baseline-1030 | com.oneshop.service.CodPaymentServiceTest#cannotCollectEarlyOrAtTerminalState(OrderStatus)[10] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.CodPaymentServiceTest.xml |
| baseline-1031 | com.oneshop.service.CodPaymentServiceTest#collectedPaymentUsesLockedOrderSnapshotAndDoesNotTransitionOrTouchInventory | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.CodPaymentServiceTest.xml |
| baseline-1032 | com.oneshop.service.CodPaymentServiceTest#cannotCollectPaidOrFailedOrder(OrderPaymentStatus)[1] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.CodPaymentServiceTest.xml |
| baseline-1033 | com.oneshop.service.CodPaymentServiceTest#cannotCollectPaidOrFailedOrder(OrderPaymentStatus)[2] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.CodPaymentServiceTest.xml |
| baseline-1034 | com.oneshop.service.CodPaymentServiceTest#cannotCollectPaidOrFailedOrder(OrderPaymentStatus)[3] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.CodPaymentServiceTest.xml |
| baseline-1035 | com.oneshop.service.CodPaymentServiceTest#successfulReceiptAlreadyPresentCannotBeCollectedAgainEvenIfOrderSaysUnpaid | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.CodPaymentServiceTest.xml |
| baseline-1036 | com.oneshop.service.CodPaymentServiceTest#cannotCollectOtherMethods(PaymentMethod)[1] | PASS | 0.004 | None | target/surefire-reports/TEST-com.oneshop.service.CodPaymentServiceTest.xml |
| baseline-1037 | com.oneshop.service.CodPaymentServiceTest#cannotCollectOtherMethods(PaymentMethod)[2] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.CodPaymentServiceTest.xml |
| baseline-1038 | com.oneshop.service.CodPaymentServiceTest#cannotCollectPickupOrMalformedOrder | PASS | 0.003 | None | target/surefire-reports/TEST-com.oneshop.service.CodPaymentServiceTest.xml |
| baseline-1039 | com.oneshop.service.DeliveryFulfillmentServiceTest#paidOnlineNeverCollectsOrRewritesPayment(DeliveryAction)[1] | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1040 | com.oneshop.service.DeliveryFulfillmentServiceTest#paidOnlineNeverCollectsOrRewritesPayment(DeliveryAction)[2] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1041 | com.oneshop.service.DeliveryFulfillmentServiceTest#paidOnlineNeverCollectsOrRewritesPayment(DeliveryAction)[3] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1042 | com.oneshop.service.DeliveryFulfillmentServiceTest#paidOnlineNeverCollectsOrRewritesPayment(DeliveryAction)[4] | PASS | 0.003 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1043 | com.oneshop.service.DeliveryFulfillmentServiceTest#inactiveOrNonStaffActorIsRejectedEvenWithMockedScope | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1044 | com.oneshop.service.DeliveryFulfillmentServiceTest#malformedDeliveryPayAtStoreIsRejectedWithoutChangingMethod(DeliveryAction)[1] | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1045 | com.oneshop.service.DeliveryFulfillmentServiceTest#malformedDeliveryPayAtStoreIsRejectedWithoutChangingMethod(DeliveryAction)[2] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1046 | com.oneshop.service.DeliveryFulfillmentServiceTest#malformedDeliveryPayAtStoreIsRejectedWithoutChangingMethod(DeliveryAction)[3] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1047 | com.oneshop.service.DeliveryFulfillmentServiceTest#malformedDeliveryPayAtStoreIsRejectedWithoutChangingMethod(DeliveryAction)[4] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1048 | com.oneshop.service.DeliveryFulfillmentServiceTest#scopeRejectionCannotBeBypassedByCallingServiceDirectly(DeliveryAction)[1] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1049 | com.oneshop.service.DeliveryFulfillmentServiceTest#scopeRejectionCannotBeBypassedByCallingServiceDirectly(DeliveryAction)[2] | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1050 | com.oneshop.service.DeliveryFulfillmentServiceTest#scopeRejectionCannotBeBypassedByCallingServiceDirectly(DeliveryAction)[3] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1051 | com.oneshop.service.DeliveryFulfillmentServiceTest#scopeRejectionCannotBeBypassedByCallingServiceDirectly(DeliveryAction)[4] | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1052 | com.oneshop.service.DeliveryFulfillmentServiceTest#unknownOrderIs404AndNullOrderIs400 | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1053 | com.oneshop.service.DeliveryFulfillmentServiceTest#listIsFilteredByTrustedAssignmentIdsAndDelivery | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1054 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[1] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1055 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[2] | PASS | 0.003 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1056 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[3] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1057 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[4] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1058 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[5] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1059 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[6] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1060 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[7] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1061 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[8] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1062 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[9] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1063 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[10] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1064 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[11] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1065 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[12] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1066 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[13] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1067 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[14] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1068 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[15] | PASS | 0.007 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1069 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[16] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1070 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[17] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1071 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[18] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1072 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[19] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1073 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[20] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1074 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[21] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1075 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[22] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1076 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[23] | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1077 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[24] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1078 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[25] | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1079 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[26] | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1080 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[27] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1081 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[28] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1082 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[29] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1083 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[30] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1084 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[31] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1085 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[32] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1086 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[33] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1087 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[34] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1088 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[35] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1089 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[36] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1090 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[37] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1091 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[38] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1092 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[39] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1093 | com.oneshop.service.DeliveryFulfillmentServiceTest#wrongSkipBackwardSameAndTerminalRequestsNeverReachStateMachineOrPayment(DeliveryAction, OrderStatus)[40] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1094 | com.oneshop.service.DeliveryFulfillmentServiceTest#unpaidOnlineIsRejectedAtEveryStage(DeliveryAction)[1] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1095 | com.oneshop.service.DeliveryFulfillmentServiceTest#unpaidOnlineIsRejectedAtEveryStage(DeliveryAction)[2] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1096 | com.oneshop.service.DeliveryFulfillmentServiceTest#unpaidOnlineIsRejectedAtEveryStage(DeliveryAction)[3] | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1097 | com.oneshop.service.DeliveryFulfillmentServiceTest#unpaidOnlineIsRejectedAtEveryStage(DeliveryAction)[4] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1098 | com.oneshop.service.DeliveryFulfillmentServiceTest#pickupFlowIsRejectedForEveryDeliveryAction(DeliveryAction)[1] | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1099 | com.oneshop.service.DeliveryFulfillmentServiceTest#pickupFlowIsRejectedForEveryDeliveryAction(DeliveryAction)[2] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1100 | com.oneshop.service.DeliveryFulfillmentServiceTest#pickupFlowIsRejectedForEveryDeliveryAction(DeliveryAction)[3] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1101 | com.oneshop.service.DeliveryFulfillmentServiceTest#pickupFlowIsRejectedForEveryDeliveryAction(DeliveryAction)[4] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1102 | com.oneshop.service.DeliveryFulfillmentServiceTest#fixedActionsLockScopeAndUseStateMachineWithTrustedActor(DeliveryAction)[1] | PASS | 0.008 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1103 | com.oneshop.service.DeliveryFulfillmentServiceTest#fixedActionsLockScopeAndUseStateMachineWithTrustedActor(DeliveryAction)[2] | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1104 | com.oneshop.service.DeliveryFulfillmentServiceTest#fixedActionsLockScopeAndUseStateMachineWithTrustedActor(DeliveryAction)[3] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1105 | com.oneshop.service.DeliveryFulfillmentServiceTest#fixedActionsLockScopeAndUseStateMachineWithTrustedActor(DeliveryAction)[4] | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.DeliveryFulfillmentServiceTest.xml |
| baseline-1106 | com.oneshop.service.InventoryRestoreTest#restoreLocksAscendingIdsBeforeWritingAndAuditsPositiveChanges | PASS | 0.003 | None | target/surefire-reports/TEST-com.oneshop.service.InventoryRestoreTest.xml |
| baseline-1107 | com.oneshop.service.InventoryRestoreTest#stockIntegerOverflowIsRefused | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.InventoryRestoreTest.xml |
| baseline-1108 | com.oneshop.service.InventoryRestoreTest#noItemsIsRefused | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.InventoryRestoreTest.xml |
| baseline-1109 | com.oneshop.service.InventoryRestoreTest#missingStockRefusesBeforeAnyWrite | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.InventoryRestoreTest.xml |
| baseline-1110 | com.oneshop.service.MockPaymentGatewayTest#newAttemptAwaiting | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.MockPaymentGatewayTest.xml |
| baseline-1111 | com.oneshop.service.MockPaymentGatewayTest#enabledFlag | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.MockPaymentGatewayTest.xml |
| baseline-1112 | com.oneshop.service.MockPaymentGatewayTest#settleInDoubt | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.MockPaymentGatewayTest.xml |
| baseline-1113 | com.oneshop.service.MockPaymentGatewayTest#productionCannotEnableMockSettlementThroughAnEnvironmentOverride | PASS | 0.007 | None | target/surefire-reports/TEST-com.oneshop.service.MockPaymentGatewayTest.xml |
| baseline-1114 | com.oneshop.service.MockPaymentGatewayTest#inDoubtKeepsReference | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.MockPaymentGatewayTest.xml |
| baseline-1115 | com.oneshop.service.MockPaymentGatewayTest#precedence | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.MockPaymentGatewayTest.xml |
| baseline-1116 | com.oneshop.service.MockPaymentGatewayTest#onlyOnlineRowsCount | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.MockPaymentGatewayTest.xml |
| baseline-1117 | com.oneshop.service.MockPaymentGatewayTest#statusWriterEdges | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.MockPaymentGatewayTest.xml |
| baseline-1118 | com.oneshop.service.MockPaymentGatewayTest#auditNoteIsAbbreviated | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.MockPaymentGatewayTest.xml |
| baseline-1119 | com.oneshop.service.MockPaymentGatewayTest#transitionsAreGuarded | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.MockPaymentGatewayTest.xml |
| baseline-1120 | com.oneshop.service.OrderServiceTest#missingOrderOrNullInputDoesNotWrite | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.OrderServiceTest.xml |
| baseline-1121 | com.oneshop.service.OrderServiceTest#rejectsLongNoteBeforeMutatingAndAcceptsBoundary | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.OrderServiceTest.xml |
| baseline-1122 | com.oneshop.service.OrderServiceTest#invalidSameOrStaleRequestsDoNotWrite | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.OrderServiceTest.xml |
| baseline-1123 | com.oneshop.service.OrderServiceTest#preservesActorNoteAndOnlyChangesStatusAfterLock | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.OrderServiceTest.xml |
| baseline-1124 | com.oneshop.service.OrderServiceTest#genericPrimitiveCannotConfirmOrCancelPendingOnlineOrder | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.OrderServiceTest.xml |
| baseline-1125 | com.oneshop.service.OrderStatusAggregatorTest#readyIsNotCompleted | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.OrderStatusAggregatorTest.xml |
| baseline-1126 | com.oneshop.service.OrderStatusAggregatorTest#derive(List, boolean, OrderStatus)[1] | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.OrderStatusAggregatorTest.xml |
| baseline-1127 | com.oneshop.service.OrderStatusAggregatorTest#derive(List, boolean, OrderStatus)[2] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderStatusAggregatorTest.xml |
| baseline-1128 | com.oneshop.service.OrderStatusAggregatorTest#derive(List, boolean, OrderStatus)[3] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.OrderStatusAggregatorTest.xml |
| baseline-1129 | com.oneshop.service.OrderStatusAggregatorTest#derive(List, boolean, OrderStatus)[4] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderStatusAggregatorTest.xml |
| baseline-1130 | com.oneshop.service.OrderStatusAggregatorTest#derive(List, boolean, OrderStatus)[5] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderStatusAggregatorTest.xml |
| baseline-1131 | com.oneshop.service.OrderStatusAggregatorTest#derive(List, boolean, OrderStatus)[6] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderStatusAggregatorTest.xml |
| baseline-1132 | com.oneshop.service.OrderStatusAggregatorTest#derive(List, boolean, OrderStatus)[7] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.OrderStatusAggregatorTest.xml |
| baseline-1133 | com.oneshop.service.OrderStatusAggregatorTest#derive(List, boolean, OrderStatus)[8] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderStatusAggregatorTest.xml |
| baseline-1134 | com.oneshop.service.OrderStatusAggregatorTest#derive(List, boolean, OrderStatus)[9] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderStatusAggregatorTest.xml |
| baseline-1135 | com.oneshop.service.OrderStatusAggregatorTest#derive(List, boolean, OrderStatus)[10] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderStatusAggregatorTest.xml |
| baseline-1136 | com.oneshop.service.OrderStatusAggregatorTest#derive(List, boolean, OrderStatus)[11] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderStatusAggregatorTest.xml |
| baseline-1137 | com.oneshop.service.OrderStatusAggregatorTest#derive(List, boolean, OrderStatus)[12] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderStatusAggregatorTest.xml |
| baseline-1138 | com.oneshop.service.OrderStatusAggregatorTest#derive(List, boolean, OrderStatus)[13] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderStatusAggregatorTest.xml |
| baseline-1139 | com.oneshop.service.OrderStatusAggregatorTest#derive(List, boolean, OrderStatus)[14] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderStatusAggregatorTest.xml |
| baseline-1140 | com.oneshop.service.OrderStatusAggregatorTest#derive(List, boolean, OrderStatus)[15] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderStatusAggregatorTest.xml |
| baseline-1141 | com.oneshop.service.OrderStatusAggregatorTest#derive(List, boolean, OrderStatus)[16] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderStatusAggregatorTest.xml |
| baseline-1142 | com.oneshop.service.OrderStatusAggregatorTest#noWriteWithoutChange | PASS | 0.162 | None | target/surefire-reports/TEST-com.oneshop.service.OrderStatusAggregatorTest.xml |
| baseline-1143 | com.oneshop.service.OrderStatusAggregatorTest#failedMovesToNeedsAttention | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.OrderStatusAggregatorTest.xml |
| baseline-1144 | com.oneshop.service.OrderStatusAggregatorTest#neverCancels | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.OrderStatusAggregatorTest.xml |
| baseline-1145 | com.oneshop.service.OrderStatusAggregatorTest#protectedStatesAreKept | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderStatusAggregatorTest.xml |
| baseline-1146 | com.oneshop.service.OrderStatusAggregatorTest#writesOnChange | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderStatusAggregatorTest.xml |
| baseline-1147 | com.oneshop.service.OrderStatusAggregatorTest#emptyIsNull | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderStatusAggregatorTest.xml |
| baseline-1148 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[1] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1149 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[2] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1150 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[3] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1151 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[4] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1152 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[5] | PASS | 0.007 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1153 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[6] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1154 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[7] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1155 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[8] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1156 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[9] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1157 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[10] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1158 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[11] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1159 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[12] | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1160 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[13] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1161 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[14] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1162 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[15] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1163 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[16] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1164 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[17] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1165 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[18] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1166 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[19] | PASS | 0.005 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1167 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[20] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1168 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[21] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1169 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[22] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1170 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[23] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1171 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[24] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1172 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[25] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1173 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[26] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1174 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[27] | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1175 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[28] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1176 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[29] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1177 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[30] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1178 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[31] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1179 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[32] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1180 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[33] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1181 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[34] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1182 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[35] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1183 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[36] | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1184 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[37] | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1185 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[38] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1186 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[39] | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1187 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[40] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1188 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[41] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1189 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[42] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1190 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[43] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1191 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[44] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1192 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[45] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1193 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[46] | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1194 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[47] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1195 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[48] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1196 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[49] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1197 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[50] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1198 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[51] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1199 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[52] | PASS | 0.006 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1200 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[53] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1201 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[54] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1202 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[55] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1203 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[56] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1204 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[57] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1205 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[58] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1206 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[59] | PASS | 0.003 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1207 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[60] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1208 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[61] | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1209 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[62] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1210 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[63] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1211 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[64] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1212 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[65] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1213 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[66] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1214 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[67] | PASS | 0.004 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1215 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[68] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1216 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[69] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1217 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[70] | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1218 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[71] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1219 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[72] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1220 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[73] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1221 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[74] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1222 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[75] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1223 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[76] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1224 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[77] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1225 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[78] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1226 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[79] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1227 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[80] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1228 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[81] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1229 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[82] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1230 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[83] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1231 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[84] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1232 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[85] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1233 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[86] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1234 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[87] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1235 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[88] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1236 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[89] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1237 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[90] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1238 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[91] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1239 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[92] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1240 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[93] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1241 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[94] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1242 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[95] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1243 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[96] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1244 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[97] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1245 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[98] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1246 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[99] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1247 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[100] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1248 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[101] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1249 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[102] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1250 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[103] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1251 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[104] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1252 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[105] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1253 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[106] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1254 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[107] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1255 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[108] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1256 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[109] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1257 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[110] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1258 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[111] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1259 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[112] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1260 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[113] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1261 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[114] | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1262 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[115] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1263 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[116] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1264 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[117] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1265 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[118] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1266 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[119] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1267 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[120] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1268 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[121] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1269 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[122] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1270 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[123] | PASS | 0.005 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1271 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[124] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1272 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[125] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1273 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[126] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1274 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[127] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1275 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[128] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1276 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[129] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1277 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[130] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1278 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[131] | PASS | 0.004 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1279 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[132] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1280 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[133] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1281 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[134] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1282 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[135] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1283 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[136] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1284 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[137] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1285 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[138] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1286 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[139] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1287 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[140] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1288 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[141] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1289 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[142] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1290 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[143] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1291 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[144] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1292 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[145] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1293 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[146] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1294 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[147] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1295 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[148] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1296 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[149] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1297 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[150] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1298 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[151] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1299 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[152] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1300 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[153] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1301 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[154] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1302 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[155] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1303 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[156] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1304 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[157] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1305 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[158] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1306 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[159] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1307 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[160] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1308 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[161] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1309 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[162] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1310 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[163] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1311 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[164] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1312 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[165] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1313 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[166] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1314 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[167] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1315 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[168] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1316 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[169] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1317 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[170] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1318 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[171] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1319 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[172] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1320 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[173] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1321 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[174] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1322 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[175] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1323 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[176] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1324 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[177] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1325 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[178] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1326 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[179] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1327 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[180] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1328 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[181] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1329 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[182] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1330 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[183] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1331 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[184] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1332 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[185] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1333 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[186] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1334 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[187] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1335 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[188] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1336 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[189] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1337 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[190] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1338 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[191] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1339 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[192] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1340 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[193] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1341 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[194] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1342 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[195] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1343 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[196] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1344 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[197] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1345 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[198] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1346 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[199] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1347 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[200] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1348 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[201] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1349 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[202] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1350 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[203] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1351 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[204] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1352 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[205] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1353 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[206] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1354 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[207] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1355 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[208] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1356 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[209] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1357 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[210] | PASS | 0.007 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1358 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[211] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1359 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[212] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1360 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[213] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1361 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[214] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1362 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[215] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1363 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[216] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1364 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[217] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1365 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[218] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1366 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[219] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1367 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[220] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1368 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[221] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1369 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[222] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1370 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[223] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1371 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[224] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1372 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[225] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1373 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[226] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1374 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[227] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1375 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[228] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1376 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[229] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1377 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[230] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1378 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[231] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1379 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[232] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1380 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[233] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1381 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[234] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1382 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[235] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1383 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[236] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1384 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[237] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1385 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[238] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1386 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[239] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1387 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[240] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1388 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[241] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1389 | com.oneshop.service.OrderTransitionPolicyTest#completeMatrixAndPaymentCauseIsolation(FulfillmentType, OrderStatus, OrderStatus)[242] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1390 | com.oneshop.service.OrderTransitionPolicyTest#nullIsNotACreationTransitionOrValidContext | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderTransitionPolicyTest.xml |
| baseline-1391 | com.oneshop.service.OrderViewServiceTest#visibilityIsDeterminedByOwnerViewFulfillmentAndStage(OrderStatus)[1] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderViewServiceTest.xml |
| baseline-1392 | com.oneshop.service.OrderViewServiceTest#visibilityIsDeterminedByOwnerViewFulfillmentAndStage(OrderStatus)[2] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderViewServiceTest.xml |
| baseline-1393 | com.oneshop.service.OrderViewServiceTest#visibilityIsDeterminedByOwnerViewFulfillmentAndStage(OrderStatus)[3] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderViewServiceTest.xml |
| baseline-1394 | com.oneshop.service.OrderViewServiceTest#visibilityIsDeterminedByOwnerViewFulfillmentAndStage(OrderStatus)[4] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderViewServiceTest.xml |
| baseline-1395 | com.oneshop.service.OrderViewServiceTest#visibilityIsDeterminedByOwnerViewFulfillmentAndStage(OrderStatus)[5] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderViewServiceTest.xml |
| baseline-1396 | com.oneshop.service.OrderViewServiceTest#visibilityIsDeterminedByOwnerViewFulfillmentAndStage(OrderStatus)[6] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderViewServiceTest.xml |
| baseline-1397 | com.oneshop.service.OrderViewServiceTest#visibilityIsDeterminedByOwnerViewFulfillmentAndStage(OrderStatus)[7] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderViewServiceTest.xml |
| baseline-1398 | com.oneshop.service.OrderViewServiceTest#visibilityIsDeterminedByOwnerViewFulfillmentAndStage(OrderStatus)[8] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderViewServiceTest.xml |
| baseline-1399 | com.oneshop.service.OrderViewServiceTest#visibilityIsDeterminedByOwnerViewFulfillmentAndStage(OrderStatus)[9] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderViewServiceTest.xml |
| baseline-1400 | com.oneshop.service.OrderViewServiceTest#visibilityIsDeterminedByOwnerViewFulfillmentAndStage(OrderStatus)[10] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.OrderViewServiceTest.xml |
| baseline-1401 | com.oneshop.service.OrderViewServiceTest#visibilityIsDeterminedByOwnerViewFulfillmentAndStage(OrderStatus)[11] | PASS | 0.009 | None | target/surefire-reports/TEST-com.oneshop.service.OrderViewServiceTest.xml |
| baseline-1402 | com.oneshop.service.PayAtStorePaymentServiceTest#cannotCollectEarlyOrAtTerminalState(OrderStatus)[1] | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.PayAtStorePaymentServiceTest.xml |
| baseline-1403 | com.oneshop.service.PayAtStorePaymentServiceTest#cannotCollectEarlyOrAtTerminalState(OrderStatus)[2] | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.PayAtStorePaymentServiceTest.xml |
| baseline-1404 | com.oneshop.service.PayAtStorePaymentServiceTest#cannotCollectEarlyOrAtTerminalState(OrderStatus)[3] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PayAtStorePaymentServiceTest.xml |
| baseline-1405 | com.oneshop.service.PayAtStorePaymentServiceTest#cannotCollectEarlyOrAtTerminalState(OrderStatus)[4] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.PayAtStorePaymentServiceTest.xml |
| baseline-1406 | com.oneshop.service.PayAtStorePaymentServiceTest#cannotCollectEarlyOrAtTerminalState(OrderStatus)[5] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PayAtStorePaymentServiceTest.xml |
| baseline-1407 | com.oneshop.service.PayAtStorePaymentServiceTest#cannotCollectEarlyOrAtTerminalState(OrderStatus)[6] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PayAtStorePaymentServiceTest.xml |
| baseline-1408 | com.oneshop.service.PayAtStorePaymentServiceTest#cannotCollectEarlyOrAtTerminalState(OrderStatus)[7] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PayAtStorePaymentServiceTest.xml |
| baseline-1409 | com.oneshop.service.PayAtStorePaymentServiceTest#cannotCollectEarlyOrAtTerminalState(OrderStatus)[8] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PayAtStorePaymentServiceTest.xml |
| baseline-1410 | com.oneshop.service.PayAtStorePaymentServiceTest#cannotCollectEarlyOrAtTerminalState(OrderStatus)[9] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.PayAtStorePaymentServiceTest.xml |
| baseline-1411 | com.oneshop.service.PayAtStorePaymentServiceTest#cannotCollectEarlyOrAtTerminalState(OrderStatus)[10] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.PayAtStorePaymentServiceTest.xml |
| baseline-1412 | com.oneshop.service.PayAtStorePaymentServiceTest#collectedPaymentUsesLockedOrderSnapshotAndDoesNotTransitionOrTouchInventory | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.PayAtStorePaymentServiceTest.xml |
| baseline-1413 | com.oneshop.service.PayAtStorePaymentServiceTest#cannotCollectPaidOrFailedOrder(OrderPaymentStatus)[1] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.PayAtStorePaymentServiceTest.xml |
| baseline-1414 | com.oneshop.service.PayAtStorePaymentServiceTest#cannotCollectPaidOrFailedOrder(OrderPaymentStatus)[2] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PayAtStorePaymentServiceTest.xml |
| baseline-1415 | com.oneshop.service.PayAtStorePaymentServiceTest#cannotCollectPaidOrFailedOrder(OrderPaymentStatus)[3] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.PayAtStorePaymentServiceTest.xml |
| baseline-1416 | com.oneshop.service.PayAtStorePaymentServiceTest#successfulReceiptAlreadyPresentCannotBeCollectedAgainEvenIfOrderSaysUnpaid | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PayAtStorePaymentServiceTest.xml |
| baseline-1417 | com.oneshop.service.PayAtStorePaymentServiceTest#cannotCollectOtherMethods(PaymentMethod)[1] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PayAtStorePaymentServiceTest.xml |
| baseline-1418 | com.oneshop.service.PayAtStorePaymentServiceTest#cannotCollectOtherMethods(PaymentMethod)[2] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PayAtStorePaymentServiceTest.xml |
| baseline-1419 | com.oneshop.service.PayAtStorePaymentServiceTest#cannotCollectPickupOrMalformedOrder | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PayAtStorePaymentServiceTest.xml |
| baseline-1420 | com.oneshop.service.PaymentDeadlinePolicyTest#boundary | PASS | 0.005 | None | target/surefire-reports/TEST-com.oneshop.service.PaymentDeadlinePolicyTest.xml |
| baseline-1421 | com.oneshop.service.PaymentDeadlinePolicyTest#propertiesAreValidated | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.PaymentDeadlinePolicyTest.xml |
| baseline-1422 | com.oneshop.service.PaymentDeadlinePolicyTest#feeAmountIsConfigurable | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PaymentDeadlinePolicyTest.xml |
| baseline-1423 | com.oneshop.service.PaymentDeadlinePolicyTest#deadlines | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PaymentDeadlinePolicyTest.xml |
| baseline-1424 | com.oneshop.service.PaymentDeadlinePolicyTest#paymentGate | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PaymentDeadlinePolicyTest.xml |
| baseline-1425 | com.oneshop.service.PaymentDeadlinePolicyTest#deadlineIsFixed | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PaymentDeadlinePolicyTest.xml |
| baseline-1426 | com.oneshop.service.PaymentDeadlinePolicyTest#onlyOnlineHasADeadline | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PaymentDeadlinePolicyTest.xml |
| baseline-1427 | com.oneshop.service.PaymentDeadlinePolicyTest#killSwitchesCloseDelivery | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PaymentDeadlinePolicyTest.xml |
| baseline-1428 | com.oneshop.service.PaymentDeadlinePolicyTest#approvedDeliveryPolicyIsTheDefault | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PaymentDeadlinePolicyTest.xml |
| baseline-1429 | com.oneshop.service.PaymentDeadlinePolicyTest#onlyTheInjectedClockIsUsed | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PaymentDeadlinePolicyTest.xml |
| baseline-1430 | com.oneshop.service.PaymentServiceTest#ownershipIsCheckedBeforeAccessingPayments | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PaymentServiceTest.xml |
| baseline-1431 | com.oneshop.service.PaymentServiceTest#noNewAttemptAfterThePaymentDeadline | PASS | 0.004 | None | target/surefire-reports/TEST-com.oneshop.service.PaymentServiceTest.xml |
| baseline-1432 | com.oneshop.service.PaymentServiceTest#inconsistentAmountOrMethodDoesNotChangeState | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.PaymentServiceTest.xml |
| baseline-1433 | com.oneshop.service.PaymentServiceTest#attemptUsesDatabaseOrderFieldsAndReusesPending | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.PaymentServiceTest.xml |
| baseline-1434 | com.oneshop.service.PaymentServiceTest#aBrowserCanNeitherConfirmNorFailAPayment | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.PaymentServiceTest.xml |
| baseline-1435 | com.oneshop.service.PaymentServiceTest#nonPayableOrderCannotCompleteAPendingAttempt | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PaymentServiceTest.xml |
| baseline-1436 | com.oneshop.service.PaymentServiceTest#missingOrderIsNotFound | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PaymentServiceTest.xml |
| baseline-1437 | com.oneshop.service.PaymentServiceTest#offlineMethodsCannotEnterOnlineWorkflow(PaymentMethod)[1] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.PaymentServiceTest.xml |
| baseline-1438 | com.oneshop.service.PaymentServiceTest#offlineMethodsCannotEnterOnlineWorkflow(PaymentMethod)[2] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PaymentServiceTest.xml |
| baseline-1439 | com.oneshop.service.PickupAttemptLimiterTest#unlocksAfterWindow | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PickupAttemptLimiterTest.xml |
| baseline-1440 | com.oneshop.service.PickupAttemptLimiterTest#perFulfillmentAndReset | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PickupAttemptLimiterTest.xml |
| baseline-1441 | com.oneshop.service.PickupAttemptLimiterTest#locksAtFive | PASS | 0.004 | None | target/surefire-reports/TEST-com.oneshop.service.PickupAttemptLimiterTest.xml |
| baseline-1442 | com.oneshop.service.PickupAttemptLimiterTest#actionInvariants | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PickupAttemptLimiterTest.xml |
| baseline-1443 | com.oneshop.service.PickupAttemptLimiterTest#actionTable | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PickupAttemptLimiterTest.xml |
| baseline-1444 | com.oneshop.service.PickupFulfillmentServiceTest#existingReadyFieldsNeverGetOverwritten | PASS | 0.003 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1445 | com.oneshop.service.PickupFulfillmentServiceTest#wrongStaleTerminalStateDoesNotGenerateOrCollect(PickupAction, OrderStatus)[1] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1446 | com.oneshop.service.PickupFulfillmentServiceTest#wrongStaleTerminalStateDoesNotGenerateOrCollect(PickupAction, OrderStatus)[2] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1447 | com.oneshop.service.PickupFulfillmentServiceTest#wrongStaleTerminalStateDoesNotGenerateOrCollect(PickupAction, OrderStatus)[3] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1448 | com.oneshop.service.PickupFulfillmentServiceTest#wrongStaleTerminalStateDoesNotGenerateOrCollect(PickupAction, OrderStatus)[4] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1449 | com.oneshop.service.PickupFulfillmentServiceTest#wrongStaleTerminalStateDoesNotGenerateOrCollect(PickupAction, OrderStatus)[5] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1450 | com.oneshop.service.PickupFulfillmentServiceTest#wrongStaleTerminalStateDoesNotGenerateOrCollect(PickupAction, OrderStatus)[6] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1451 | com.oneshop.service.PickupFulfillmentServiceTest#wrongStaleTerminalStateDoesNotGenerateOrCollect(PickupAction, OrderStatus)[7] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1452 | com.oneshop.service.PickupFulfillmentServiceTest#wrongStaleTerminalStateDoesNotGenerateOrCollect(PickupAction, OrderStatus)[8] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1453 | com.oneshop.service.PickupFulfillmentServiceTest#wrongStaleTerminalStateDoesNotGenerateOrCollect(PickupAction, OrderStatus)[9] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1454 | com.oneshop.service.PickupFulfillmentServiceTest#wrongStaleTerminalStateDoesNotGenerateOrCollect(PickupAction, OrderStatus)[10] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1455 | com.oneshop.service.PickupFulfillmentServiceTest#wrongStaleTerminalStateDoesNotGenerateOrCollect(PickupAction, OrderStatus)[11] | PASS | 0.007 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1456 | com.oneshop.service.PickupFulfillmentServiceTest#wrongStaleTerminalStateDoesNotGenerateOrCollect(PickupAction, OrderStatus)[12] | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1457 | com.oneshop.service.PickupFulfillmentServiceTest#wrongStaleTerminalStateDoesNotGenerateOrCollect(PickupAction, OrderStatus)[13] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1458 | com.oneshop.service.PickupFulfillmentServiceTest#wrongStaleTerminalStateDoesNotGenerateOrCollect(PickupAction, OrderStatus)[14] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1459 | com.oneshop.service.PickupFulfillmentServiceTest#wrongStaleTerminalStateDoesNotGenerateOrCollect(PickupAction, OrderStatus)[15] | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1460 | com.oneshop.service.PickupFulfillmentServiceTest#wrongStaleTerminalStateDoesNotGenerateOrCollect(PickupAction, OrderStatus)[16] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1461 | com.oneshop.service.PickupFulfillmentServiceTest#wrongStaleTerminalStateDoesNotGenerateOrCollect(PickupAction, OrderStatus)[17] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1462 | com.oneshop.service.PickupFulfillmentServiceTest#wrongStaleTerminalStateDoesNotGenerateOrCollect(PickupAction, OrderStatus)[18] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1463 | com.oneshop.service.PickupFulfillmentServiceTest#wrongStaleTerminalStateDoesNotGenerateOrCollect(PickupAction, OrderStatus)[19] | PASS | 0.003 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1464 | com.oneshop.service.PickupFulfillmentServiceTest#wrongStaleTerminalStateDoesNotGenerateOrCollect(PickupAction, OrderStatus)[20] | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1465 | com.oneshop.service.PickupFulfillmentServiceTest#wrongStaleTerminalStateDoesNotGenerateOrCollect(PickupAction, OrderStatus)[21] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1466 | com.oneshop.service.PickupFulfillmentServiceTest#wrongStaleTerminalStateDoesNotGenerateOrCollect(PickupAction, OrderStatus)[22] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1467 | com.oneshop.service.PickupFulfillmentServiceTest#wrongStaleTerminalStateDoesNotGenerateOrCollect(PickupAction, OrderStatus)[23] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1468 | com.oneshop.service.PickupFulfillmentServiceTest#wrongStaleTerminalStateDoesNotGenerateOrCollect(PickupAction, OrderStatus)[24] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1469 | com.oneshop.service.PickupFulfillmentServiceTest#wrongStaleTerminalStateDoesNotGenerateOrCollect(PickupAction, OrderStatus)[25] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1470 | com.oneshop.service.PickupFulfillmentServiceTest#wrongStaleTerminalStateDoesNotGenerateOrCollect(PickupAction, OrderStatus)[26] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1471 | com.oneshop.service.PickupFulfillmentServiceTest#wrongStaleTerminalStateDoesNotGenerateOrCollect(PickupAction, OrderStatus)[27] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1472 | com.oneshop.service.PickupFulfillmentServiceTest#wrongStaleTerminalStateDoesNotGenerateOrCollect(PickupAction, OrderStatus)[28] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1473 | com.oneshop.service.PickupFulfillmentServiceTest#wrongStaleTerminalStateDoesNotGenerateOrCollect(PickupAction, OrderStatus)[29] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1474 | com.oneshop.service.PickupFulfillmentServiceTest#wrongStaleTerminalStateDoesNotGenerateOrCollect(PickupAction, OrderStatus)[30] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1475 | com.oneshop.service.PickupFulfillmentServiceTest#onlineCodeAcceptsOuterSpacesAndLowercaseAndPreservesReceipt | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1476 | com.oneshop.service.PickupFulfillmentServiceTest#missingStoredCodeRejectsCompletion | PASS | 0.006 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1477 | com.oneshop.service.PickupFulfillmentServiceTest#existingSixCharacterSeedCodeCanBeVerifiedOnlyOnItsOwnOrder | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1478 | com.oneshop.service.PickupFulfillmentServiceTest#fixedEdgesAuthorizeActorAndDelegateStatus(PickupAction)[1] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1479 | com.oneshop.service.PickupFulfillmentServiceTest#fixedEdgesAuthorizeActorAndDelegateStatus(PickupAction)[2] | PASS | 0.003 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1480 | com.oneshop.service.PickupFulfillmentServiceTest#fixedEdgesAuthorizeActorAndDelegateStatus(PickupAction)[3] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1481 | com.oneshop.service.PickupFulfillmentServiceTest#deliveryOrCodPickupAreRejectedEvenOnSharedPrepareEdge(PickupAction)[1] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1482 | com.oneshop.service.PickupFulfillmentServiceTest#deliveryOrCodPickupAreRejectedEvenOnSharedPrepareEdge(PickupAction)[2] | PASS | 0.004 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1483 | com.oneshop.service.PickupFulfillmentServiceTest#deliveryOrCodPickupAreRejectedEvenOnSharedPrepareEdge(PickupAction)[3] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1484 | com.oneshop.service.PickupFulfillmentServiceTest#missingOrderOrIdFailsSafely | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1485 | com.oneshop.service.PickupFulfillmentServiceTest#receiptAlreadyExistsCannotBeCollectedOrRepaired(PickupAction)[1] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1486 | com.oneshop.service.PickupFulfillmentServiceTest#receiptAlreadyExistsCannotBeCollectedOrRepaired(PickupAction)[2] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1487 | com.oneshop.service.PickupFulfillmentServiceTest#receiptAlreadyExistsCannotBeCollectedOrRepaired(PickupAction)[3] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1488 | com.oneshop.service.PickupFulfillmentServiceTest#scopeAndActiveStaffAreCheckedBeforeCodeVerificationOrMutation | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1489 | com.oneshop.service.PickupFulfillmentServiceTest#everyActionRequiresPaidOnlineOrUnpaidPayAtStore(PickupAction)[1] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1490 | com.oneshop.service.PickupFulfillmentServiceTest#everyActionRequiresPaidOnlineOrUnpaidPayAtStore(PickupAction)[2] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1491 | com.oneshop.service.PickupFulfillmentServiceTest#everyActionRequiresPaidOnlineOrUnpaidPayAtStore(PickupAction)[3] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1492 | com.oneshop.service.PickupFulfillmentServiceTest#invalidCodeNeverChangesPaymentTimeOrFields(String)[1] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1493 | com.oneshop.service.PickupFulfillmentServiceTest#invalidCodeNeverChangesPaymentTimeOrFields(String)[2] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1494 | com.oneshop.service.PickupFulfillmentServiceTest#invalidCodeNeverChangesPaymentTimeOrFields(String)[3] | PASS | 0.003 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1495 | com.oneshop.service.PickupFulfillmentServiceTest#invalidCodeNeverChangesPaymentTimeOrFields(String)[4] | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1496 | com.oneshop.service.PickupFulfillmentServiceTest#invalidCodeNeverChangesPaymentTimeOrFields(String)[5] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1497 | com.oneshop.service.PickupFulfillmentServiceTest#invalidCodeNeverChangesPaymentTimeOrFields(String)[6] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1498 | com.oneshop.service.PickupFulfillmentServiceTest#invalidCodeNeverChangesPaymentTimeOrFields(String)[7] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1499 | com.oneshop.service.PickupFulfillmentServiceTest#invalidCodeNeverChangesPaymentTimeOrFields(String)[8] | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1500 | com.oneshop.service.PickupFulfillmentServiceTest#invalidCodeNeverChangesPaymentTimeOrFields(String)[9] | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1501 | com.oneshop.service.PickupFulfillmentServiceTest#invalidCodeNeverChangesPaymentTimeOrFields(String)[10] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1502 | com.oneshop.service.PickupFulfillmentServiceTest#invalidCodeNeverChangesPaymentTimeOrFields(String)[11] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1503 | com.oneshop.service.PickupFulfillmentServiceTest#invalidCodeNeverChangesPaymentTimeOrFields(String)[12] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1504 | com.oneshop.service.PickupFulfillmentServiceTest#readyFieldsExistBeforeStateMachineFlush | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1505 | com.oneshop.service.PickupFulfillmentServiceTest#generatedCodesAreShortRandomAndKeepStringRepresentation | PASS | 0.009 | None | target/surefire-reports/TEST-com.oneshop.service.PickupFulfillmentServiceTest.xml |
| baseline-1506 | com.oneshop.service.ProductServiceV3Test#updateProduct_updatesPriceAndOriginalPrice | PASS | 0.003 | None | target/surefire-reports/TEST-com.oneshop.service.ProductServiceV3Test.xml |
| baseline-1507 | com.oneshop.service.ProductServiceV3Test#catalogNeverFallsBackToLegacyStorePrice | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.ProductServiceV3Test.xml |
| baseline-1508 | com.oneshop.service.ProductServiceV3Test#productCardResponse_unifiedPriceAndNoFromPrice | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.ProductServiceV3Test.xml |
| baseline-1509 | com.oneshop.service.ProductServiceV3Test#storeProductMapperUsesOnlyProductPrice | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.ProductServiceV3Test.xml |
| baseline-1510 | com.oneshop.service.ProductServiceV3Test#productDetailResponse_computesInStockAcrossStores | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.ProductServiceV3Test.xml |
| baseline-1511 | com.oneshop.service.ProductServiceV3Test#productMapper_mapsPriceAndOriginalPrice | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.ProductServiceV3Test.xml |
| baseline-1512 | com.oneshop.service.ProductServiceV3Test#createProduct_savesPriceAndOriginalPrice | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.service.ProductServiceV3Test.xml |
| baseline-1513 | com.oneshop.service.ProductServiceV3Test#catalogItemResponse_usesProductPrice | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.service.ProductServiceV3Test.xml |
| baseline-1514 | com.oneshop.service.ReviewRulesTest#legacyReviewWithoutAnItemHasNoInferredSource | PASS | 0.003 | None | target/surefire-reports/TEST-com.oneshop.service.ReviewRulesTest.xml |
| baseline-1515 | com.oneshop.service.ReviewRulesTest#contentAllowsVietnameseMarkupAndTheExactSqlLengthBoundary | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.ReviewRulesTest.xml |
| baseline-1516 | com.oneshop.service.ReviewRulesTest#mismatchedOwnerProductOrStoreCannotGrantStaffAuthority | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.ReviewRulesTest.xml |
| baseline-1517 | com.oneshop.service.ReviewRulesTest#refundedOrCancelledOrdersAreNeverReviewable | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.ReviewRulesTest.xml |
| baseline-1518 | com.oneshop.service.ReviewRulesTest#oldReplyRemainsReadableAndDedicatedReplyTakesDisplayPriority | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.ReviewRulesTest.xml |
| baseline-1519 | com.oneshop.service.ReviewRulesTest#eligibilityFollowsCompletedPartOfPartiallyCompletedOrder | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.ReviewRulesTest.xml |
| baseline-1520 | com.oneshop.service.ReviewRulesTest#blankOrControlCharacterRepliesAreRefused(String)[1] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.ReviewRulesTest.xml |
| baseline-1521 | com.oneshop.service.ReviewRulesTest#blankOrControlCharacterRepliesAreRefused(String)[2] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.ReviewRulesTest.xml |
| baseline-1522 | com.oneshop.service.ReviewRulesTest#blankOrControlCharacterRepliesAreRefused(String)[3] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.ReviewRulesTest.xml |
| baseline-1523 | com.oneshop.service.ReviewRulesTest#blankOrControlCharacterRepliesAreRefused(String)[4] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.ReviewRulesTest.xml |
| baseline-1524 | com.oneshop.service.ReviewRulesTest#blankOrControlCharacterRepliesAreRefused(String)[5] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.service.ReviewRulesTest.xml |
| baseline-1525 | com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest#everyCrossStoreMutationIsDeniedBeforeBusinessRulesOrWrites(String)[1] | PASS | 0.268 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-1526 | com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest#everyCrossStoreMutationIsDeniedBeforeBusinessRulesOrWrites(String)[2] | PASS | 0.169 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-1527 | com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest#everyCrossStoreMutationIsDeniedBeforeBusinessRulesOrWrites(String)[3] | PASS | 0.163 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-1528 | com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest#everyCrossStoreMutationIsDeniedBeforeBusinessRulesOrWrites(String)[4] | PASS | 0.152 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-1529 | com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest#everyCrossStoreMutationIsDeniedBeforeBusinessRulesOrWrites(String)[5] | PASS | 0.244 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-1530 | com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest#everyCrossStoreMutationIsDeniedBeforeBusinessRulesOrWrites(String)[6] | PASS | 0.133 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-1531 | com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest#everyCrossStoreMutationIsDeniedBeforeBusinessRulesOrWrites(String)[7] | PASS | 0.215 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-1532 | com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest#absentAssignmentDeniesQueueAndMutationsUsingTheExistingJwt | PASS | 0.147 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-1533 | com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest#jumpingAheadOfConfirmedIsRejectedRegardlessOfForgedTargetStatus(FulfillmentType, String)[1] | PASS | 0.13 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-1534 | com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest#jumpingAheadOfConfirmedIsRejectedRegardlessOfForgedTargetStatus(FulfillmentType, String)[2] | PASS | 0.144 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-1535 | com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest#jumpingAheadOfConfirmedIsRejectedRegardlessOfForgedTargetStatus(FulfillmentType, String)[3] | PASS | 0.151 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-1536 | com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest#jumpingAheadOfConfirmedIsRejectedRegardlessOfForgedTargetStatus(FulfillmentType, String)[4] | PASS | 0.135 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-1537 | com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest#jumpingAheadOfConfirmedIsRejectedRegardlessOfForgedTargetStatus(FulfillmentType, String)[5] | PASS | 0.12 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-1538 | com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest#otherRolesCannotReachNewQueueOrMutateOrders(String)[1] | PASS | 0.147 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-1539 | com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest#otherRolesCannotReachNewQueueOrMutateOrders(String)[2] | PASS | 0.148 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-1540 | com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest#pickupQueueUsesAssignedStoreTypeOpenStatesPaginationAndHidesAllCodes | PASS | 0.21 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-1541 | com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest#deliveryHttpUiFollowsEveryStateAndUsesExistingPaymentHistoryRules(PaymentMethod)[1] | PASS | 0.451 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-1542 | com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest#deliveryHttpUiFollowsEveryStateAndUsesExistingPaymentHistoryRules(PaymentMethod)[2] | PASS | 0.343 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-1543 | com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest#actionsOfOtherFulfillmentCannotMutateAnOwnOrder(FulfillmentType)[1] | PASS | 0.097 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-1544 | com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest#actionsOfOtherFulfillmentCannotMutateAnOwnOrder(FulfillmentType)[2] | PASS | 0.099 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-1545 | com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest#wrongOrMissingPickupCodeCannotCollectPaymentOrComplete(String)[1] | PASS | 0.17 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-1546 | com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest#wrongOrMissingPickupCodeCannotCollectPaymentOrComplete(String)[2] | PASS | 0.163 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-1547 | com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest#wrongOrMissingPickupCodeCannotCollectPaymentOrComplete(String)[3] | PASS | 0.155 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-1548 | com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest#wrongOrMissingPickupCodeCannotCollectPaymentOrComplete(String)[4] | PASS | 0.184 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-1549 | com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest#wrongOrMissingPickupCodeCannotCollectPaymentOrComplete(String)[5] | PASS | 0.168 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-1550 | com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest#missingOrderActionReturns404WithoutMutation | PASS | 0.147 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-1551 | com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest#wrapperKeepsScopeFulfillmentReceiptHistoryAndTimestampsInOneRollbackTransaction(String)[1] | PASS | 0.1 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-1552 | com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest#wrapperKeepsScopeFulfillmentReceiptHistoryAndTimestampsInOneRollbackTransaction(String)[2] | PASS | 0.113 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-1553 | com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest#wrapperKeepsScopeFulfillmentReceiptHistoryAndTimestampsInOneRollbackTransaction(String)[3] | PASS | 0.118 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-1554 | com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest#payAtStoreInconsistentPriorPaidStateOrDuplicateReceiptCannotCompleteAgain | PASS | 0.105 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-1555 | com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest#multipleAssignmentsAllowPeerPickupThenRevocationBlocksTheNextMutationImmediately | ERROR | 0.06 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-1556 | com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest#newActionsRequireCsrfForCookieBearerAndEncodedPaths(String)[1] | PASS | 0.104 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-1557 | com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest#newActionsRequireCsrfForCookieBearerAndEncodedPaths(String)[2] | PASS | 0.101 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-1558 | com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest#pickupHttpUiVerifiesCustomerCodeAndCompletesPaymentBeforeHistory(PaymentMethod)[1] | PASS | 0.357 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-1559 | com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest#pickupHttpUiVerifiesCustomerCodeAndCompletesPaymentBeforeHistory(PaymentMethod)[2] | PASS | 0.266 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-1560 | com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest#racingPickupCompletionCreatesOnePaymentAndOneCompletionWithNoInventoryWrites | PASS | 0.107 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest.xml |
| baseline-1561 | com.oneshop.StaffFulfillmentWebIntegrationTest#fixedPostsBindOnlyThreeInputs | PASS | 0.049 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentWebIntegrationTest.xml |
| baseline-1562 | com.oneshop.StaffFulfillmentWebIntegrationTest#friendlyErrorsWithoutLeaks | PASS | 0.051 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentWebIntegrationTest.xml |
| baseline-1563 | com.oneshop.StaffFulfillmentWebIntegrationTest#getCannotMutateAndNoGenericTransition | PASS | 0.04 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentWebIntegrationTest.xml |
| baseline-1564 | com.oneshop.StaffFulfillmentWebIntegrationTest#queueAndDetailUseStaffLayoutAndSafeHtml | PASS | 0.029 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentWebIntegrationTest.xml |
| baseline-1565 | com.oneshop.StaffFulfillmentWebIntegrationTest#everyWriteRequiresCsrfEvenWithBearer | PASS | 0.061 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentWebIntegrationTest.xml |
| baseline-1566 | com.oneshop.StaffFulfillmentWebIntegrationTest#sidebarMarksOneCurrentEntry | PASS | 0.012 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentWebIntegrationTest.xml |
| baseline-1567 | com.oneshop.StaffFulfillmentWebIntegrationTest#legacyUrlsRedirect | PASS | 0.013 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentWebIntegrationTest.xml |
| baseline-1568 | com.oneshop.StaffFulfillmentWebIntegrationTest#pickupCodeIsNeverPrinted | PASS | 0.01 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentWebIntegrationTest.xml |
| baseline-1569 | com.oneshop.StaffFulfillmentWebIntegrationTest#heldOrderHasNoAction(OrderStatus)[1] | PASS | 0.012 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentWebIntegrationTest.xml |
| baseline-1570 | com.oneshop.StaffFulfillmentWebIntegrationTest#heldOrderHasNoAction(OrderStatus)[2] | PASS | 0.016 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentWebIntegrationTest.xml |
| baseline-1571 | com.oneshop.StaffFulfillmentWebIntegrationTest#heldOrderHasNoAction(OrderStatus)[3] | PASS | 0.01 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentWebIntegrationTest.xml |
| baseline-1572 | com.oneshop.StaffFulfillmentWebIntegrationTest#roleAndAssignmentBoundaries | PASS | 0.098 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentWebIntegrationTest.xml |
| baseline-1573 | com.oneshop.StaffFulfillmentWebIntegrationTest#terminalHasNoAction(OrderFulfillmentStatus)[1] | PASS | 0.008 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentWebIntegrationTest.xml |
| baseline-1574 | com.oneshop.StaffFulfillmentWebIntegrationTest#terminalHasNoAction(OrderFulfillmentStatus)[2] | PASS | 0.008 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentWebIntegrationTest.xml |
| baseline-1575 | com.oneshop.StaffFulfillmentWebIntegrationTest#terminalHasNoAction(OrderFulfillmentStatus)[3] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentWebIntegrationTest.xml |
| baseline-1576 | com.oneshop.StaffFulfillmentWebIntegrationTest#handoverBindsOnlyPickupCode | PASS | 0.021 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentWebIntegrationTest.xml |
| baseline-1577 | com.oneshop.StaffFulfillmentWebIntegrationTest#showsOnlyTheStatesActionsWithCsrf(FulfillmentAction)[1] | PASS | 0.009 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentWebIntegrationTest.xml |
| baseline-1578 | com.oneshop.StaffFulfillmentWebIntegrationTest#showsOnlyTheStatesActionsWithCsrf(FulfillmentAction)[2] | PASS | 0.005 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentWebIntegrationTest.xml |
| baseline-1579 | com.oneshop.StaffFulfillmentWebIntegrationTest#showsOnlyTheStatesActionsWithCsrf(FulfillmentAction)[3] | PASS | 0.016 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentWebIntegrationTest.xml |
| baseline-1580 | com.oneshop.StaffFulfillmentWebIntegrationTest#showsOnlyTheStatesActionsWithCsrf(FulfillmentAction)[4] | PASS | 0.007 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentWebIntegrationTest.xml |
| baseline-1581 | com.oneshop.StaffFulfillmentWebIntegrationTest#showsOnlyTheStatesActionsWithCsrf(FulfillmentAction)[5] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentWebIntegrationTest.xml |
| baseline-1582 | com.oneshop.StaffFulfillmentWebIntegrationTest#showsOnlyTheStatesActionsWithCsrf(FulfillmentAction)[6] | PASS | 0.014 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentWebIntegrationTest.xml |
| baseline-1583 | com.oneshop.StaffFulfillmentWebIntegrationTest#showsOnlyTheStatesActionsWithCsrf(FulfillmentAction)[7] | PASS | 0.009 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentWebIntegrationTest.xml |
| baseline-1584 | com.oneshop.StaffFulfillmentWebIntegrationTest#showsOnlyTheStatesActionsWithCsrf(FulfillmentAction)[8] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.StaffFulfillmentWebIntegrationTest.xml |
| baseline-1585 | com.oneshop.StaffInventoryDatabaseIntegrationTest#invalidOrNegativeQuantityIsRejectedOnBackendWithoutAnyWrite(String)[1] | PASS | 0.274 | None | target/surefire-reports/TEST-com.oneshop.StaffInventoryDatabaseIntegrationTest.xml |
| baseline-1586 | com.oneshop.StaffInventoryDatabaseIntegrationTest#invalidOrNegativeQuantityIsRejectedOnBackendWithoutAnyWrite(String)[2] | PASS | 0.147 | None | target/surefire-reports/TEST-com.oneshop.StaffInventoryDatabaseIntegrationTest.xml |
| baseline-1587 | com.oneshop.StaffInventoryDatabaseIntegrationTest#invalidOrNegativeQuantityIsRejectedOnBackendWithoutAnyWrite(String)[3] | PASS | 0.156 | None | target/surefire-reports/TEST-com.oneshop.StaffInventoryDatabaseIntegrationTest.xml |
| baseline-1588 | com.oneshop.StaffInventoryDatabaseIntegrationTest#invalidOrNegativeQuantityIsRejectedOnBackendWithoutAnyWrite(String)[4] | PASS | 0.153 | None | target/surefire-reports/TEST-com.oneshop.StaffInventoryDatabaseIntegrationTest.xml |
| baseline-1589 | com.oneshop.StaffInventoryDatabaseIntegrationTest#invalidOrNegativeQuantityIsRejectedOnBackendWithoutAnyWrite(String)[5] | PASS | 0.153 | None | target/surefire-reports/TEST-com.oneshop.StaffInventoryDatabaseIntegrationTest.xml |
| baseline-1590 | com.oneshop.StaffInventoryDatabaseIntegrationTest#outerRollbackAlsoRestoresCommittedLookingStockAuditAndTimestampRows | PASS | 0.039 | None | target/surefire-reports/TEST-com.oneshop.StaffInventoryDatabaseIntegrationTest.xml |
| baseline-1591 | com.oneshop.StaffInventoryDatabaseIntegrationTest#crossStoreAndMissingStockFormsOrHistoryReturnNoResource(long)[1] | PASS | 0.232 | None | target/surefire-reports/TEST-com.oneshop.StaffInventoryDatabaseIntegrationTest.xml |
| baseline-1592 | com.oneshop.StaffInventoryDatabaseIntegrationTest#crossStoreAndMissingStockFormsOrHistoryReturnNoResource(long)[2] | PASS | 0.199 | None | target/surefire-reports/TEST-com.oneshop.StaffInventoryDatabaseIntegrationTest.xml |
| baseline-1593 | com.oneshop.StaffInventoryDatabaseIntegrationTest#guestsAndCustomerCatalogCannotReadExactInventory | PASS | 0.166 | None | target/surefire-reports/TEST-com.oneshop.StaffInventoryDatabaseIntegrationTest.xml |
| baseline-1594 | com.oneshop.StaffInventoryDatabaseIntegrationTest#stockFormsRequireCsrfForCookieBearerAndEncodedPaths | PASS | 0.171 | None | target/surefire-reports/TEST-com.oneshop.StaffInventoryDatabaseIntegrationTest.xml |
| baseline-1595 | com.oneshop.StaffInventoryDatabaseIntegrationTest#noActiveAssignmentDeniesStockHistoryAndAdjustmentWithTheSameJwt | PASS | 0.163 | None | target/surefire-reports/TEST-com.oneshop.StaffInventoryDatabaseIntegrationTest.xml |
| baseline-1596 | com.oneshop.StaffInventoryDatabaseIntegrationTest#concurrentAdjustmentsKeepSerialBeforeAfterValuesAndOneMovementPerActualChange | PASS | 0.054 | None | target/surefire-reports/TEST-com.oneshop.StaffInventoryDatabaseIntegrationTest.xml |
| baseline-1597 | com.oneshop.StaffInventoryDatabaseIntegrationTest#customerAndAdminCannotUseStaffInventoryRoutes(String)[1] | PASS | 0.161 | None | target/surefire-reports/TEST-com.oneshop.StaffInventoryDatabaseIntegrationTest.xml |
| baseline-1598 | com.oneshop.StaffInventoryDatabaseIntegrationTest#customerAndAdminCannotUseStaffInventoryRoutes(String)[2] | PASS | 0.156 | None | target/surefire-reports/TEST-com.oneshop.StaffInventoryDatabaseIntegrationTest.xml |
| baseline-1599 | com.oneshop.StaffInventoryDatabaseIntegrationTest#stockPaginationAndTotalStayScopedWhenMoreThanTwentyProductsExist | PASS | 0.425 | None | target/surefire-reports/TEST-com.oneshop.StaffInventoryDatabaseIntegrationTest.xml |
| baseline-1600 | com.oneshop.StaffInventoryDatabaseIntegrationTest#staffAdjustmentRequiresAnAuditNoteWithinTheExistingColumnLimit(String)[1] | PASS | 0.164 | None | target/surefire-reports/TEST-com.oneshop.StaffInventoryDatabaseIntegrationTest.xml |
| baseline-1601 | com.oneshop.StaffInventoryDatabaseIntegrationTest#staffAdjustmentRequiresAnAuditNoteWithinTheExistingColumnLimit(String)[2] | PASS | 0.17 | None | target/surefire-reports/TEST-com.oneshop.StaffInventoryDatabaseIntegrationTest.xml |
| baseline-1602 | com.oneshop.StaffInventoryDatabaseIntegrationTest#staffAdjustmentRequiresAnAuditNoteWithinTheExistingColumnLimit(String)[3] | PASS | 0.165 | None | target/surefire-reports/TEST-com.oneshop.StaffInventoryDatabaseIntegrationTest.xml |
| baseline-1603 | com.oneshop.StaffInventoryDatabaseIntegrationTest#historyIncludesSystemOrderCancellationAndStaffAdjustmentWithAccurateActors | PASS | 0.475 | None | target/surefire-reports/TEST-com.oneshop.StaffInventoryDatabaseIntegrationTest.xml |
| baseline-1604 | com.oneshop.StaffInventoryDatabaseIntegrationTest#multipleActiveAssignmentsAllowOnlyTheirStockAndRevocationBlocksFurtherWrites | ERROR | 0.128 | None | target/surefire-reports/TEST-com.oneshop.StaffInventoryDatabaseIntegrationTest.xml |
| baseline-1605 | com.oneshop.StaffInventoryDatabaseIntegrationTest#validHttpAdjustmentWritesExactlyOneAccurateMovementAndKeepsOtherResourcesUnchanged(int)[1] | PASS | 0.154 | None | target/surefire-reports/TEST-com.oneshop.StaffInventoryDatabaseIntegrationTest.xml |
| baseline-1606 | com.oneshop.StaffInventoryDatabaseIntegrationTest#validHttpAdjustmentWritesExactlyOneAccurateMovementAndKeepsOtherResourcesUnchanged(int)[2] | PASS | 0.178 | None | target/surefire-reports/TEST-com.oneshop.StaffInventoryDatabaseIntegrationTest.xml |
| baseline-1607 | com.oneshop.StaffInventoryDatabaseIntegrationTest#validHttpAdjustmentWritesExactlyOneAccurateMovementAndKeepsOtherResourcesUnchanged(int)[3] | PASS | 0.18 | None | target/surefire-reports/TEST-com.oneshop.StaffInventoryDatabaseIntegrationTest.xml |
| baseline-1608 | com.oneshop.StaffInventoryDatabaseIntegrationTest#stockAndHistorySelectionShowOnlyOwnSkusAllStatusesAndExactQuantity | PASS | 0.256 | None | target/surefire-reports/TEST-com.oneshop.StaffInventoryDatabaseIntegrationTest.xml |
| baseline-1609 | com.oneshop.StaffInventoryDatabaseIntegrationTest#postingTheCurrentQuantityIsAnExplicitNoChangeWithNoZeroMovement | PASS | 0.13 | None | target/surefire-reports/TEST-com.oneshop.StaffInventoryDatabaseIntegrationTest.xml |
| baseline-1610 | com.oneshop.StaffInventoryDatabaseIntegrationTest#writeFailureRollsBackRealSqlStockAndMovementTogether(String)[1] | PASS | 0.161 | None | target/surefire-reports/TEST-com.oneshop.StaffInventoryDatabaseIntegrationTest.xml |
| baseline-1611 | com.oneshop.StaffInventoryDatabaseIntegrationTest#writeFailureRollsBackRealSqlStockAndMovementTogether(String)[2] | PASS | 0.153 | None | target/surefire-reports/TEST-com.oneshop.StaffInventoryDatabaseIntegrationTest.xml |
| baseline-1612 | com.oneshop.StaffInventoryDatabaseIntegrationTest#serviceValidationAlsoRejectsNegativeMissingQuantityAndMissingNote | PASS | 0.047 | None | target/surefire-reports/TEST-com.oneshop.StaffInventoryDatabaseIntegrationTest.xml |
| baseline-1613 | com.oneshop.StaffInventoryDatabaseIntegrationTest#inventoryHistoryPaginationHasStableOrderAndCountsOnlyTheScopedProduct | PASS | 0.272 | None | target/surefire-reports/TEST-com.oneshop.StaffInventoryDatabaseIntegrationTest.xml |
| baseline-1614 | com.oneshop.StaffOperationsDatabaseIntegrationTest#forgedQueryHeaderCookieAndGetBodyCannotChangeAssignedScope | PASS | 0.316 | None | target/surefire-reports/TEST-com.oneshop.StaffOperationsDatabaseIntegrationTest.xml |
| baseline-1615 | com.oneshop.StaffOperationsDatabaseIntegrationTest#noActiveAssignmentDeniesAllDataRoutesEvenWithOldJwtAndForgedStore | PASS | 0.18 | None | target/surefire-reports/TEST-com.oneshop.StaffOperationsDatabaseIntegrationTest.xml |
| baseline-1616 | com.oneshop.StaffOperationsDatabaseIntegrationTest#lowStockBoundariesInactiveSkusAndOtherStoreInventoryAreCountedSeparately | PASS | 0.197 | None | target/surefire-reports/TEST-com.oneshop.StaffOperationsDatabaseIntegrationTest.xml |
| baseline-1617 | com.oneshop.StaffOperationsDatabaseIntegrationTest#dashboardAndLandingShowOnlyAssignedStoreWithExactSqlCounts | PASS | 0.174 | None | target/surefire-reports/TEST-com.oneshop.StaffOperationsDatabaseIntegrationTest.xml |
| baseline-1618 | com.oneshop.StaffOperationsDatabaseIntegrationTest#otherRolesCannotOpenAnyStaffOperationsPage(String)[1] | PASS | 0.1 | None | target/surefire-reports/TEST-com.oneshop.StaffOperationsDatabaseIntegrationTest.xml |
| baseline-1619 | com.oneshop.StaffOperationsDatabaseIntegrationTest#otherRolesCannotOpenAnyStaffOperationsPage(String)[2] | PASS | 0.1 | None | target/surefire-reports/TEST-com.oneshop.StaffOperationsDatabaseIntegrationTest.xml |
| baseline-1620 | com.oneshop.StaffOperationsDatabaseIntegrationTest#multipleAssignmentsKeepPerStoreCountsAndRevocationImmediatelyRemovesPeerResources | ERROR | 0.092 | None | target/surefire-reports/TEST-com.oneshop.StaffOperationsDatabaseIntegrationTest.xml |
| baseline-1621 | com.oneshop.StaffOperationsDatabaseIntegrationTest#queueHasOnlyAssignedOrdersAllRequiredColumnsAndNoTransitionControls | PASS | 0.173 | None | target/surefire-reports/TEST-com.oneshop.StaffOperationsDatabaseIntegrationTest.xml |
| baseline-1622 | com.oneshop.StaffOperationsDatabaseIntegrationTest#eachOrderStateCountsOnlyInTheCorrectDashboardMetricAndStore(String)[1] | PASS | 0.085 | None | target/surefire-reports/TEST-com.oneshop.StaffOperationsDatabaseIntegrationTest.xml |
| baseline-1623 | com.oneshop.StaffOperationsDatabaseIntegrationTest#eachOrderStateCountsOnlyInTheCorrectDashboardMetricAndStore(String)[2] | PASS | 0.051 | None | target/surefire-reports/TEST-com.oneshop.StaffOperationsDatabaseIntegrationTest.xml |
| baseline-1624 | com.oneshop.StaffOperationsDatabaseIntegrationTest#eachOrderStateCountsOnlyInTheCorrectDashboardMetricAndStore(String)[3] | PASS | 0.101 | None | target/surefire-reports/TEST-com.oneshop.StaffOperationsDatabaseIntegrationTest.xml |
| baseline-1625 | com.oneshop.StaffOperationsDatabaseIntegrationTest#eachOrderStateCountsOnlyInTheCorrectDashboardMetricAndStore(String)[4] | PASS | 0.039 | None | target/surefire-reports/TEST-com.oneshop.StaffOperationsDatabaseIntegrationTest.xml |
| baseline-1626 | com.oneshop.StaffOperationsDatabaseIntegrationTest#eachOrderStateCountsOnlyInTheCorrectDashboardMetricAndStore(String)[5] | PASS | 0.071 | None | target/surefire-reports/TEST-com.oneshop.StaffOperationsDatabaseIntegrationTest.xml |
| baseline-1627 | com.oneshop.StaffOperationsDatabaseIntegrationTest#eachOrderStateCountsOnlyInTheCorrectDashboardMetricAndStore(String)[6] | PASS | 0.064 | None | target/surefire-reports/TEST-com.oneshop.StaffOperationsDatabaseIntegrationTest.xml |
| baseline-1628 | com.oneshop.StaffOperationsDatabaseIntegrationTest#eachOrderStateCountsOnlyInTheCorrectDashboardMetricAndStore(String)[7] | PASS | 0.057 | None | target/surefire-reports/TEST-com.oneshop.StaffOperationsDatabaseIntegrationTest.xml |
| baseline-1629 | com.oneshop.StaffOperationsDatabaseIntegrationTest#eachOrderStateCountsOnlyInTheCorrectDashboardMetricAndStore(String)[8] | PASS | 0.049 | None | target/surefire-reports/TEST-com.oneshop.StaffOperationsDatabaseIntegrationTest.xml |
| baseline-1630 | com.oneshop.StaffOperationsDatabaseIntegrationTest#ownDeliveryAndPickupDetailReuseSnapshotsPaymentsTimelineAndHideStoredPickupCode(long)[1] | PASS | 0.153 | None | target/surefire-reports/TEST-com.oneshop.StaffOperationsDatabaseIntegrationTest.xml |
| baseline-1631 | com.oneshop.StaffOperationsDatabaseIntegrationTest#ownDeliveryAndPickupDetailReuseSnapshotsPaymentsTimelineAndHideStoredPickupCode(long)[2] | PASS | 0.133 | None | target/surefire-reports/TEST-com.oneshop.StaffOperationsDatabaseIntegrationTest.xml |
| baseline-1632 | com.oneshop.StaffOperationsDatabaseIntegrationTest#crossStoreAndMissingDetailReturnSame404WithoutOrderResource(long)[1] | PASS | 0.111 | None | target/surefire-reports/TEST-com.oneshop.StaffOperationsDatabaseIntegrationTest.xml |
| baseline-1633 | com.oneshop.StaffOperationsDatabaseIntegrationTest#crossStoreAndMissingDetailReturnSame404WithoutOrderResource(long)[2] | PASS | 0.104 | None | target/surefire-reports/TEST-com.oneshop.StaffOperationsDatabaseIntegrationTest.xml |
| baseline-1634 | com.oneshop.StaffOperationsDatabaseIntegrationTest#crossStoreAndMissingDetailReturnSame404WithoutOrderResource(long)[3] | PASS | 0.101 | None | target/surefire-reports/TEST-com.oneshop.StaffOperationsDatabaseIntegrationTest.xml |
| baseline-1635 | com.oneshop.StaffOperationsDatabaseIntegrationTest#crossStoreAndMissingDetailReturnSame404WithoutOrderResource(long)[4] | PASS | 0.096 | None | target/surefire-reports/TEST-com.oneshop.StaffOperationsDatabaseIntegrationTest.xml |
| baseline-1636 | com.oneshop.StaffOperationsDatabaseIntegrationTest#paginationAndTotalAreScopedInSqlWithStableOrderingAndBoundedPages | PASS | 0.398 | None | target/surefire-reports/TEST-com.oneshop.StaffOperationsDatabaseIntegrationTest.xml |
| baseline-1637 | com.oneshop.StaffStoreScopeDatabaseIntegrationTest#ownOrderAndStoreProductAreAllowedIncludingInventoryScope | PASS | 0.016 | None | target/surefire-reports/TEST-com.oneshop.StaffStoreScopeDatabaseIntegrationTest.xml |
| baseline-1638 | com.oneshop.StaffStoreScopeDatabaseIntegrationTest#activeStaffResolvesActualAssignmentAndStore | PASS | 0.017 | None | target/surefire-reports/TEST-com.oneshop.StaffStoreScopeDatabaseIntegrationTest.xml |
| baseline-1639 | com.oneshop.StaffStoreScopeDatabaseIntegrationTest#forgingStoreIdOnHttpCannotChangeScopeOrExposePeerStock(String)[1] | PASS | 0.204 | None | target/surefire-reports/TEST-com.oneshop.StaffStoreScopeDatabaseIntegrationTest.xml |
| baseline-1640 | com.oneshop.StaffStoreScopeDatabaseIntegrationTest#forgingStoreIdOnHttpCannotChangeScopeOrExposePeerStock(String)[2] | PASS | 0.143 | None | target/surefire-reports/TEST-com.oneshop.StaffStoreScopeDatabaseIntegrationTest.xml |
| baseline-1641 | com.oneshop.StaffStoreScopeDatabaseIntegrationTest#forgingStoreIdOnHttpCannotChangeScopeOrExposePeerStock(String)[3] | PASS | 0.127 | None | target/surefire-reports/TEST-com.oneshop.StaffStoreScopeDatabaseIntegrationTest.xml |
| baseline-1642 | com.oneshop.StaffStoreScopeDatabaseIntegrationTest#forgingStoreIdOnHttpCannotChangeScopeOrExposePeerStock(String)[4] | PASS | 0.111 | None | target/surefire-reports/TEST-com.oneshop.StaffStoreScopeDatabaseIntegrationTest.xml |
| baseline-1643 | com.oneshop.StaffStoreScopeDatabaseIntegrationTest#nonStaffCannotUseFoundationEvenWithAnAssignedStaffEmail(String)[1] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.StaffStoreScopeDatabaseIntegrationTest.xml |
| baseline-1644 | com.oneshop.StaffStoreScopeDatabaseIntegrationTest#nonStaffCannotUseFoundationEvenWithAnAssignedStaffEmail(String)[2] | PASS | 0.016 | None | target/surefire-reports/TEST-com.oneshop.StaffStoreScopeDatabaseIntegrationTest.xml |
| baseline-1645 | com.oneshop.StaffStoreScopeDatabaseIntegrationTest#absentOrInvalidAssignmentDeniesServiceAndRealStaffHttp(String)[1] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.StaffStoreScopeDatabaseIntegrationTest.xml |
| baseline-1646 | com.oneshop.StaffStoreScopeDatabaseIntegrationTest#absentOrInvalidAssignmentDeniesServiceAndRealStaffHttp(String)[2] | PASS | 0.111 | None | target/surefire-reports/TEST-com.oneshop.StaffStoreScopeDatabaseIntegrationTest.xml |
| baseline-1647 | com.oneshop.StaffStoreScopeDatabaseIntegrationTest#absentOrInvalidAssignmentDeniesServiceAndRealStaffHttp(String)[3] | PASS | 0.089 | None | target/surefire-reports/TEST-com.oneshop.StaffStoreScopeDatabaseIntegrationTest.xml |
| baseline-1648 | com.oneshop.StaffStoreScopeDatabaseIntegrationTest#absentOrInvalidAssignmentDeniesServiceAndRealStaffHttp(String)[4] | PASS | 0.111 | None | target/surefire-reports/TEST-com.oneshop.StaffStoreScopeDatabaseIntegrationTest.xml |
| baseline-1649 | com.oneshop.StaffStoreScopeDatabaseIntegrationTest#absentOrInvalidAssignmentDeniesServiceAndRealStaffHttp(String)[5] | PASS | 0.091 | None | target/surefire-reports/TEST-com.oneshop.StaffStoreScopeDatabaseIntegrationTest.xml |
| baseline-1650 | com.oneshop.StaffStoreScopeDatabaseIntegrationTest#crossStoreAndUnknownResourcesAreIndistinguishablyDenied | PASS | 0.034 | None | target/surefire-reports/TEST-com.oneshop.StaffStoreScopeDatabaseIntegrationTest.xml |
| baseline-1651 | com.oneshop.StaffStoreScopeDatabaseIntegrationTest#unauthenticatedServiceCallIsDeniedBeforeResourceLookup | PASS | 0.012 | None | target/surefire-reports/TEST-com.oneshop.StaffStoreScopeDatabaseIntegrationTest.xml |
| baseline-1652 | com.oneshop.StaffStoreScopeDatabaseIntegrationTest#multipleAssignmentsPreserveOnlyActiveScopeAndResolveFreshAfterRevocation | ERROR | 0.015 | None | target/surefire-reports/TEST-com.oneshop.StaffStoreScopeDatabaseIntegrationTest.xml |
| baseline-1653 | com.oneshop.StaffStoreScopeDatabaseIntegrationTest#existingOrderUrlsStillEnforceStoreScopeWithForgedRequestStore | PASS | 0.157 | None | target/surefire-reports/TEST-com.oneshop.StaffStoreScopeDatabaseIntegrationTest.xml |
| baseline-1654 | com.oneshop.TestDatabaseSafetyInitializerTest#refusesADestructiveHibernateOverrideBeforeBeanCreation | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.TestDatabaseSafetyInitializerTest.xml |
| baseline-1655 | com.oneshop.TestDatabaseSafetyInitializerTest#refusesOperationalMissingOrAmbiguousTargets(String)[1] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.TestDatabaseSafetyInitializerTest.xml |
| baseline-1656 | com.oneshop.TestDatabaseSafetyInitializerTest#refusesOperationalMissingOrAmbiguousTargets(String)[2] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.TestDatabaseSafetyInitializerTest.xml |
| baseline-1657 | com.oneshop.TestDatabaseSafetyInitializerTest#refusesOperationalMissingOrAmbiguousTargets(String)[3] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.TestDatabaseSafetyInitializerTest.xml |
| baseline-1658 | com.oneshop.TestDatabaseSafetyInitializerTest#refusesOperationalMissingOrAmbiguousTargets(String)[4] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.TestDatabaseSafetyInitializerTest.xml |
| baseline-1659 | com.oneshop.TestDatabaseSafetyInitializerTest#refusesOperationalMissingOrAmbiguousTargets(String)[5] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.TestDatabaseSafetyInitializerTest.xml |
| baseline-1660 | com.oneshop.TestDatabaseSafetyInitializerTest#refusesAHikariOverridePointingAtTheOperationalDatabase | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.TestDatabaseSafetyInitializerTest.xml |
| baseline-1661 | com.oneshop.TestDatabaseSafetyInitializerTest#allowsOnlyExplicitOneShopTestTargets(String)[1] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.TestDatabaseSafetyInitializerTest.xml |
| baseline-1662 | com.oneshop.TestDatabaseSafetyInitializerTest#allowsOnlyExplicitOneShopTestTargets(String)[2] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.TestDatabaseSafetyInitializerTest.xml |
| baseline-1663 | com.oneshop.TestDatabaseSafetyInitializerTest#allowsOnlyExplicitOneShopTestTargets(String)[3] | PASS | 0.002 | None | target/surefire-reports/TEST-com.oneshop.TestDatabaseSafetyInitializerTest.xml |
| baseline-1664 | com.oneshop.WebExceptionHandlerSecurityTest#oversizedUploadCannotRedirectToAnUntrustedReferer(String)[1] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.WebExceptionHandlerSecurityTest.xml |
| baseline-1665 | com.oneshop.WebExceptionHandlerSecurityTest#oversizedUploadCannotRedirectToAnUntrustedReferer(String)[2] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.WebExceptionHandlerSecurityTest.xml |
| baseline-1666 | com.oneshop.WebExceptionHandlerSecurityTest#oversizedUploadCannotRedirectToAnUntrustedReferer(String)[3] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.WebExceptionHandlerSecurityTest.xml |
| baseline-1667 | com.oneshop.WebExceptionHandlerSecurityTest#oversizedUploadCannotRedirectToAnUntrustedReferer(String)[4] | PASS | 0.001 | None | target/surefire-reports/TEST-com.oneshop.WebExceptionHandlerSecurityTest.xml |
| baseline-1668 | com.oneshop.WebRoutesIntegrationTest#healthEndpointIsPublicAndNotDecorated | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.WebRoutesIntegrationTest.xml |
| baseline-1669 | com.oneshop.WebRoutesIntegrationTest#protectedWebRoutesRedirectAnonymousUsersToLogin | PASS | 0.016 | None | target/surefire-reports/TEST-com.oneshop.WebRoutesIntegrationTest.xml |
| baseline-1670 | com.oneshop.WebRoutesIntegrationTest#decoratorIsNotReachableDirectly | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.WebRoutesIntegrationTest.xml |
| baseline-1671 | com.oneshop.WebRoutesIntegrationTest#staticAssetsAreServed | PASS | 0.016 | None | target/surefire-reports/TEST-com.oneshop.WebRoutesIntegrationTest.xml |
| baseline-1672 | com.oneshop.WebRoutesIntegrationTest#protectedApiRoutesAnswer401ForAnonymousUsers | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.WebRoutesIntegrationTest.xml |
| baseline-1673 | com.oneshop.WebRoutesIntegrationTest#basicPagesRenderInsideTheLayout | PASS | 0.034 | None | target/surefire-reports/TEST-com.oneshop.WebRoutesIntegrationTest.xml |
| baseline-1674 | com.oneshop.WebRoutesIntegrationTest#homePageIsRenderedByThymeleafAndDecoratedBySiteMesh | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.WebRoutesIntegrationTest.xml |
| additional-0001 | com.oneshop.QaAuditAllocationInvariantTest#generatedAllocationConservesStockAndDemand(long)[1] | PASS | 0.294 | None | target/surefire-reports/TEST-com.oneshop.QaAuditAllocationInvariantTest.xml |
| additional-0002 | com.oneshop.QaAuditAllocationInvariantTest#generatedAllocationConservesStockAndDemand(long)[2] | PASS | 0.038 | None | target/surefire-reports/TEST-com.oneshop.QaAuditAllocationInvariantTest.xml |
| additional-0003 | com.oneshop.QaAuditAllocationInvariantTest#generatedAllocationConservesStockAndDemand(long)[3] | PASS | 0.039 | None | target/surefire-reports/TEST-com.oneshop.QaAuditAllocationInvariantTest.xml |
| additional-0004 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[1] | PASS | 1.216 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0005 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[2] | PASS | 0.08 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0006 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[3] | PASS | 0.113 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0007 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[4] | PASS | 0.065 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0008 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[5] | PASS | 0.109 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0009 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[6] | PASS | 0.05 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0010 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[7] | PASS | 0.045 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0011 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[8] | PASS | 0.056 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0012 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[9] | PASS | 0.045 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0013 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[10] | PASS | 0.047 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0014 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[11] | PASS | 0.037 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0015 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[12] | PASS | 0.042 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0016 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[13] | PASS | 0.035 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0017 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[14] | PASS | 0.038 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0018 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[15] | PASS | 0.039 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0019 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[16] | PASS | 0.045 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0020 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[17] | FAIL | 0.069 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0021 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[18] | PASS | 0.051 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0022 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[19] | FAIL | 0.025 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0023 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[20] | PASS | 0.042 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0024 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[21] | FAIL | 0.027 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0025 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[22] | PASS | 0.047 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0026 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[23] | PASS | 0.026 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0027 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[24] | PASS | 0.022 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0028 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[25] | PASS | 0.019 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0029 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[26] | PASS | 0.018 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0030 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[27] | PASS | 0.019 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0031 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[28] | PASS | 0.018 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0032 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[29] | PASS | 0.366 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0033 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[30] | PASS | 0.076 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0034 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[31] | PASS | 0.13 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0035 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[32] | PASS | 0.069 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0036 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[33] | PASS | 0.079 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0037 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[34] | PASS | 0.061 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0038 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[35] | PASS | 0.056 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0039 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[36] | PASS | 0.064 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0040 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[37] | PASS | 0.056 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0041 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[38] | PASS | 0.052 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0042 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[39] | FAIL | 0.025 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0043 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[40] | PASS | 0.05 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0044 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[41] | FAIL | 0.028 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0045 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[42] | PASS | 0.063 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0046 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[43] | FAIL | 0.027 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0047 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[44] | PASS | 0.058 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0048 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[45] | FAIL | 0.032 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0049 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[46] | PASS | 0.065 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0050 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[47] | FAIL | 0.031 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0051 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[48] | PASS | 0.078 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0052 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[49] | FAIL | 0.042 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0053 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[50] | PASS | 0.078 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0054 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[51] | PASS | 0.033 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0055 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[52] | PASS | 0.034 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0056 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[53] | PASS | 0.038 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0057 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[54] | PASS | 0.036 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0058 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[55] | PASS | 0.037 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0059 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[56] | PASS | 0.036 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0060 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[57] | PASS | 0.177 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0061 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[58] | PASS | 0.082 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0062 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[59] | PASS | 0.079 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0063 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[60] | PASS | 0.049 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0064 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[61] | PASS | 0.061 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0065 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[62] | PASS | 0.046 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0066 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[63] | PASS | 0.042 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0067 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[64] | PASS | 0.048 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0068 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[65] | PASS | 0.044 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0069 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[66] | PASS | 0.039 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0070 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[67] | PASS | 0.036 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0071 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[68] | PASS | 0.038 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0072 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[69] | PASS | 0.043 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0073 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[70] | PASS | 0.043 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0074 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[71] | PASS | 0.038 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0075 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[72] | PASS | 0.042 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0076 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[73] | PASS | 0.036 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0077 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[74] | PASS | 0.045 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0078 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[75] | PASS | 0.045 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0079 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[76] | PASS | 0.046 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0080 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[77] | PASS | 0.042 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0081 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[78] | PASS | 0.047 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0082 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[79] | PASS | 0.032 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0083 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[80] | PASS | 0.028 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0084 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[81] | PASS | 0.026 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0085 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[82] | PASS | 0.022 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0086 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[83] | PASS | 0.027 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0087 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[84] | PASS | 0.024 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0088 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[85] | PASS | 0.063 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0089 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[86] | PASS | 0.045 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0090 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[87] | PASS | 0.052 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0091 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[88] | PASS | 0.043 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0092 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[89] | PASS | 0.041 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0093 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[90] | PASS | 0.041 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0094 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[91] | PASS | 0.04 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0095 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[92] | PASS | 0.043 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0096 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[93] | PASS | 0.042 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0097 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[94] | PASS | 0.039 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0098 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[95] | FAIL | 0.031 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0099 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[96] | PASS | 0.068 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0100 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[97] | FAIL | 0.034 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0101 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[98] | PASS | 0.063 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0102 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[99] | FAIL | 0.035 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0103 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[100] | PASS | 0.048 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0104 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[101] | FAIL | 0.03 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0105 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[102] | PASS | 0.048 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0106 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[103] | FAIL | 0.03 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0107 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[104] | PASS | 0.051 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0108 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[105] | FAIL | 0.031 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0109 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[106] | PASS | 0.04 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0110 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[107] | PASS | 0.027 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0111 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[108] | PASS | 0.027 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0112 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[109] | PASS | 0.026 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0113 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[110] | PASS | 0.029 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0114 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[111] | PASS | 0.026 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0115 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[112] | PASS | 0.026 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0116 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[113] | PASS | 0.156 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0117 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[114] | PASS | 0.099 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0118 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[115] | PASS | 0.101 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0119 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[116] | PASS | 0.066 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0120 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[117] | PASS | 0.101 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0121 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[118] | PASS | 0.058 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0122 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[119] | PASS | 0.048 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0123 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[120] | PASS | 0.053 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0124 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[121] | PASS | 0.045 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0125 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[122] | PASS | 0.056 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0126 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[123] | FAIL | 0.036 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0127 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[124] | PASS | 0.072 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0128 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[125] | FAIL | 0.035 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0129 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[126] | PASS | 0.066 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0130 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[127] | FAIL | 0.035 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0131 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[128] | PASS | 0.063 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0132 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[129] | FAIL | 0.037 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0133 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[130] | PASS | 0.063 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0134 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[131] | FAIL | 0.036 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0135 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[132] | PASS | 0.058 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0136 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[133] | FAIL | 0.035 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0137 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[134] | PASS | 0.06 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0138 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[135] | PASS | 0.021 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0139 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[136] | PASS | 0.02 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0140 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[137] | PASS | 0.025 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0141 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[138] | PASS | 0.022 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0142 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[139] | PASS | 0.021 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0143 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[140] | PASS | 0.021 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0144 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[141] | PASS | 0.159 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0145 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[142] | PASS | 0.063 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0146 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[143] | PASS | 0.073 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0147 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[144] | PASS | 0.052 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0148 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[145] | PASS | 0.106 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0149 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[146] | PASS | 0.045 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0150 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[147] | PASS | 0.046 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0151 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[148] | PASS | 0.059 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0152 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[149] | PASS | 0.045 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0153 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[150] | PASS | 0.052 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0154 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[151] | FAIL | 0.025 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0155 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[152] | PASS | 0.047 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0156 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[153] | FAIL | 0.026 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0157 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[154] | PASS | 0.048 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0158 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[155] | FAIL | 0.027 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0159 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[156] | PASS | 0.052 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0160 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[157] | FAIL | 0.025 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0161 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[158] | PASS | 0.043 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0162 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[159] | FAIL | 0.023 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0163 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[160] | PASS | 0.045 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0164 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[161] | FAIL | 0.022 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0165 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[162] | PASS | 0.041 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0166 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[163] | PASS | 0.019 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0167 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[164] | PASS | 0.021 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0168 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[165] | PASS | 0.02 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0169 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[166] | PASS | 0.021 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0170 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[167] | PASS | 0.019 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0171 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[168] | PASS | 0.018 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0172 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[169] | PASS | 0.198 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0173 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[170] | PASS | 0.056 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0174 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[171] | PASS | 0.087 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0175 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[172] | PASS | 0.039 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0176 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[173] | PASS | 0.06 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0177 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[174] | PASS | 0.034 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0178 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[175] | PASS | 0.039 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0179 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[176] | PASS | 0.043 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0180 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[177] | PASS | 0.039 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0181 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[178] | PASS | 0.043 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0182 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[179] | FAIL | 0.021 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0183 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[180] | PASS | 0.042 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0184 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[181] | FAIL | 0.023 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0185 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[182] | PASS | 0.046 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0186 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[183] | FAIL | 0.022 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0187 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[184] | PASS | 0.04 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0188 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[185] | FAIL | 0.022 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0189 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[186] | PASS | 0.042 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0190 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[187] | FAIL | 0.024 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0191 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[188] | PASS | 0.039 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0192 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[189] | FAIL | 0.022 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0193 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[190] | PASS | 0.04 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0194 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[191] | PASS | 0.019 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0195 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[192] | PASS | 0.022 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0196 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[193] | PASS | 0.021 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0197 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[194] | PASS | 0.021 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0198 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[195] | PASS | 0.022 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0199 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[196] | PASS | 0.02 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0200 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[197] | PASS | 0.1 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0201 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[198] | PASS | 0.084 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0202 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[199] | PASS | 0.05 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0203 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[200] | PASS | 0.044 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0204 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[201] | PASS | 0.074 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0205 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[202] | PASS | 0.05 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0206 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[203] | PASS | 0.044 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0207 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[204] | PASS | 0.046 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0208 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[205] | PASS | 0.042 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0209 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[206] | PASS | 0.042 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0210 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[207] | PASS | 0.034 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0211 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[208] | PASS | 0.048 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0212 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[209] | PASS | 0.034 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0213 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[210] | PASS | 0.049 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0214 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[211] | PASS | 0.03 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0215 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[212] | PASS | 0.044 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0216 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[213] | PASS | 0.03 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0217 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[214] | PASS | 0.039 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0218 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[215] | PASS | 0.032 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0219 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[216] | PASS | 0.04 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0220 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[217] | PASS | 0.028 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0221 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[218] | PASS | 0.035 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0222 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[219] | PASS | 0.018 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0223 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[220] | PASS | 0.021 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0224 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[221] | PASS | 0.02 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0225 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[222] | PASS | 0.021 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0226 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[223] | PASS | 0.02 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0227 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[224] | PASS | 0.019 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0228 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[225] | PASS | 0.089 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0229 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[226] | PASS | 0.048 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0230 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[227] | PASS | 0.057 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0231 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[228] | PASS | 0.048 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0232 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[229] | PASS | 0.047 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0233 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[230] | PASS | 0.041 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0234 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[231] | PASS | 0.039 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0235 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[232] | PASS | 0.04 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0236 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[233] | PASS | 0.043 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0237 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[234] | PASS | 0.042 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0238 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[235] | PASS | 0.026 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0239 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[236] | PASS | 0.039 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0240 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[237] | PASS | 0.03 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0241 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[238] | PASS | 0.035 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0242 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[239] | PASS | 0.023 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0243 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[240] | PASS | 0.033 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0244 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[241] | PASS | 0.025 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0245 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[242] | PASS | 0.042 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0246 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[243] | PASS | 0.031 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0247 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[244] | PASS | 0.035 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0248 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[245] | PASS | 0.023 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0249 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[246] | PASS | 0.033 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0250 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[247] | PASS | 0.016 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0251 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[248] | PASS | 0.016 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0252 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[249] | PASS | 0.017 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0253 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[250] | PASS | 0.016 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0254 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[251] | PASS | 0.019 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0255 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[252] | PASS | 0.02 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0256 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[253] | PASS | 0.151 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0257 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[254] | PASS | 0.075 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0258 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[255] | PASS | 0.041 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0259 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[256] | PASS | 0.047 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0260 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[257] | PASS | 0.112 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0261 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[258] | PASS | 0.039 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0262 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[259] | PASS | 0.038 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0263 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[260] | PASS | 0.036 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0264 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[261] | PASS | 0.035 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0265 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[262] | PASS | 0.034 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0266 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[263] | FAIL | 0.018 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0267 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[264] | PASS | 0.041 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0268 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[265] | FAIL | 0.018 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0269 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[266] | PASS | 0.044 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0270 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[267] | FAIL | 0.024 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0271 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[268] | PASS | 0.043 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0272 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[269] | FAIL | 0.019 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0273 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[270] | PASS | 0.054 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0274 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[271] | FAIL | 0.027 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0275 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[272] | PASS | 0.049 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0276 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[273] | FAIL | 0.024 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0277 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[274] | PASS | 0.048 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0278 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[275] | PASS | 0.02 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0279 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[276] | PASS | 0.021 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0280 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[277] | PASS | 0.021 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0281 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[278] | PASS | 0.024 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0282 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[279] | PASS | 0.021 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0283 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[280] | PASS | 0.022 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0284 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[281] | PASS | 0.117 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0285 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[282] | PASS | 0.049 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0286 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[283] | PASS | 0.095 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0287 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[284] | PASS | 0.041 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0288 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[285] | PASS | 0.044 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0289 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[286] | PASS | 0.04 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0290 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[287] | PASS | 0.039 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0291 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[288] | PASS | 0.037 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0292 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[289] | PASS | 0.032 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0293 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[290] | PASS | 0.035 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0294 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[291] | PASS | 0.034 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0295 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[292] | PASS | 0.038 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0296 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[293] | PASS | 0.03 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0297 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[294] | PASS | 0.035 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0298 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[295] | PASS | 0.026 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0299 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[296] | PASS | 0.033 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0300 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[297] | PASS | 0.027 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0301 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[298] | PASS | 0.034 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0302 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[299] | PASS | 0.026 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0303 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[300] | PASS | 0.035 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0304 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[301] | PASS | 0.023 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0305 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[302] | PASS | 0.035 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0306 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[303] | PASS | 0.02 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0307 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[304] | PASS | 0.019 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0308 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[305] | PASS | 0.032 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0309 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[306] | PASS | 0.018 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0310 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[307] | PASS | 0.018 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0311 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[308] | PASS | 0.018 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0312 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[309] | PASS | 0.092 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0313 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[310] | PASS | 0.074 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0314 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[311] | PASS | 0.059 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0315 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[312] | PASS | 0.068 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0316 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[313] | PASS | 0.044 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0317 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[314] | PASS | 0.044 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0318 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[315] | PASS | 0.053 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0319 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[316] | PASS | 0.049 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0320 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[317] | PASS | 0.055 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0321 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[318] | PASS | 0.053 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0322 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[319] | PASS | 0.032 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0323 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[320] | PASS | 0.032 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0324 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[321] | PASS | 0.032 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0325 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[322] | PASS | 0.032 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0326 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[323] | PASS | 0.031 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0327 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[324] | PASS | 0.029 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0328 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[325] | PASS | 0.026 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0329 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[326] | PASS | 0.032 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0330 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[327] | PASS | 0.032 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0331 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[328] | PASS | 0.028 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0332 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[329] | PASS | 0.032 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0333 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[330] | PASS | 0.026 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0334 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[331] | PASS | 0.019 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0335 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[332] | PASS | 0.021 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0336 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[333] | PASS | 0.017 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0337 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[334] | PASS | 0.017 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0338 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[335] | PASS | 0.018 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0339 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[336] | PASS | 0.021 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0340 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[337] | PASS | 0.083 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0341 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[338] | PASS | 0.05 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0342 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[339] | PASS | 0.039 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0343 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[340] | PASS | 0.034 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0344 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[341] | PASS | 0.069 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0345 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[342] | PASS | 0.032 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0346 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[343] | PASS | 0.032 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0347 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[344] | PASS | 0.035 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0348 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[345] | PASS | 0.041 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0349 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[346] | PASS | 0.037 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0350 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[347] | PASS | 0.028 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0351 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[348] | PASS | 0.037 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0352 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[349] | PASS | 0.028 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0353 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[350] | PASS | 0.033 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0354 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[351] | PASS | 0.031 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0355 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[352] | PASS | 0.041 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0356 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[353] | PASS | 0.03 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0357 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[354] | PASS | 0.035 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0358 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[355] | PASS | 0.036 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0359 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[356] | PASS | 0.036 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0360 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[357] | PASS | 0.029 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0361 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[358] | PASS | 0.036 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0362 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[359] | PASS | 0.023 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0363 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[360] | PASS | 0.021 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0364 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[361] | PASS | 0.021 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0365 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[362] | PASS | 0.018 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0366 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[363] | PASS | 0.019 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0367 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[364] | PASS | 0.018 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0368 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[365] | PASS | 0.114 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0369 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[366] | PASS | 0.026 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0370 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[367] | PASS | 0.025 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0371 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[368] | PASS | 0.025 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0372 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[369] | PASS | 0.1 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0373 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[370] | PASS | 0.023 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0374 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[371] | PASS | 0.026 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0375 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[372] | PASS | 0.029 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0376 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[373] | PASS | 0.027 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0377 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[374] | PASS | 0.027 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0378 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[375] | FAIL | 0.017 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0379 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[376] | FAIL | 0.021 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0380 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[377] | FAIL | 0.021 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0381 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[378] | FAIL | 0.009 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0382 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[379] | FAIL | 0.034 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0383 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[380] | FAIL | 0.028 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0384 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[381] | FAIL | 0.027 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0385 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[382] | FAIL | 0.031 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0386 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[383] | FAIL | 0.024 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0387 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[384] | FAIL | 0.029 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0388 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[385] | FAIL | 0.028 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0389 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[386] | FAIL | 0.026 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0390 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[387] | PASS | 0.022 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0391 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[388] | PASS | 0.02 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0392 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[389] | PASS | 0.02 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0393 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[390] | PASS | 0.022 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0394 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[391] | PASS | 0.02 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0395 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[392] | PASS | 0.021 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0396 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[393] | PASS | 0.052 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0397 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[394] | PASS | 0.054 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0398 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[395] | PASS | 0.05 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0399 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[396] | PASS | 0.031 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0400 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[397] | PASS | 0.023 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0401 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[398] | PASS | 0.02 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0402 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[399] | PASS | 0.04 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0403 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[400] | PASS | 0.016 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0404 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[401] | PASS | 0.017 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0405 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[402] | PASS | 0.061 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0406 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[403] | PASS | 0.065 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0407 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[404] | PASS | 0.07 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0408 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[405] | FAIL | 0.024 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0409 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[406] | PASS | 0.017 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0410 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[407] | PASS | 0.017 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0411 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[408] | PASS | 0.044 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0412 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[409] | PASS | 0.096 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0413 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[410] | PASS | 0.021 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0414 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[411] | FAIL | 0.026 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0415 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[412] | PASS | 0.021 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0416 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[413] | PASS | 0.019 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0417 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[414] | PASS | 0.046 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0418 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[415] | PASS | 0.042 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0419 | com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[416] | PASS | 0.039 | None | target/surefire-reports/TEST-com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.xml |
| additional-0420 | com.oneshop.QaAuditImageRollbackDatabaseIntegrationTest#replacingBrandImageMustNotDeleteOldFileBeforeCommit()[1] | FAIL | 0.194 | None | target/surefire-reports/TEST-com.oneshop.QaAuditImageRollbackDatabaseIntegrationTest.xml |
| additional-0421 | com.oneshop.QaAuditImageRollbackDatabaseIntegrationTest#replacingBrandImageMustNotDeleteOldFileBeforeCommit()[2] | FAIL | 0.099 | None | target/surefire-reports/TEST-com.oneshop.QaAuditImageRollbackDatabaseIntegrationTest.xml |
| additional-0422 | com.oneshop.QaAuditImageRollbackDatabaseIntegrationTest#uploadedProductFileMustBeCompensatedWhenTransactionRollsBack()[1] | FAIL | 0.181 | None | target/surefire-reports/TEST-com.oneshop.QaAuditImageRollbackDatabaseIntegrationTest.xml |
| additional-0423 | com.oneshop.QaAuditImageRollbackDatabaseIntegrationTest#uploadedProductFileMustBeCompensatedWhenTransactionRollsBack()[2] | FAIL | 0.093 | None | target/surefire-reports/TEST-com.oneshop.QaAuditImageRollbackDatabaseIntegrationTest.xml |
| additional-0424 | com.oneshop.QaAuditRaceDatabaseIntegrationTest#profileMustNotReactivateAnAccountDisabledDuringRequest()[1] | FAIL | 0.553 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRaceDatabaseIntegrationTest.xml |
| additional-0425 | com.oneshop.QaAuditRaceDatabaseIntegrationTest#profileMustNotReactivateAnAccountDisabledDuringRequest()[2] | FAIL | 0.217 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRaceDatabaseIntegrationTest.xml |
| additional-0426 | com.oneshop.QaAuditRaceDatabaseIntegrationTest#profileMustNotReactivateAnAccountDisabledDuringRequest()[3] | FAIL | 0.214 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRaceDatabaseIntegrationTest.xml |
| additional-0427 | com.oneshop.QaAuditRaceDatabaseIntegrationTest#parallelPrimaryImageSelectionMustLeaveExactlyOnePrimary()[1] | FAIL | 0.114 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRaceDatabaseIntegrationTest.xml |
| additional-0428 | com.oneshop.QaAuditRaceDatabaseIntegrationTest#parallelPrimaryImageSelectionMustLeaveExactlyOnePrimary()[2] | FAIL | 0.105 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRaceDatabaseIntegrationTest.xml |
| additional-0429 | com.oneshop.QaAuditRaceDatabaseIntegrationTest#parallelPrimaryImageSelectionMustLeaveExactlyOnePrimary()[3] | FAIL | 0.101 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRaceDatabaseIntegrationTest.xml |
| additional-0430 | com.oneshop.QaAuditRaceDatabaseIntegrationTest#profileMustNotRestoreAStaffRoleRevokedDuringRequest()[1] | FAIL | 0.262 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRaceDatabaseIntegrationTest.xml |
| additional-0431 | com.oneshop.QaAuditRaceDatabaseIntegrationTest#profileMustNotRestoreAStaffRoleRevokedDuringRequest()[2] | FAIL | 0.177 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRaceDatabaseIntegrationTest.xml |
| additional-0432 | com.oneshop.QaAuditRaceDatabaseIntegrationTest#profileMustNotRestoreAStaffRoleRevokedDuringRequest()[3] | FAIL | 0.187 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRaceDatabaseIntegrationTest.xml |
| additional-0433 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[1] | PASS | 0.06 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0434 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[2] | PASS | 0.01 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0435 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[3] | PASS | 0.011 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0436 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[4] | PASS | 0.071 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0437 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[5] | PASS | 0.197 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0438 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[6] | PASS | 0.184 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0439 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[7] | PASS | 0.176 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0440 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[8] | PASS | 0.095 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0441 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[9] | PASS | 0.188 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0442 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[10] | PASS | 0.068 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0443 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[11] | PASS | 0.183 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0444 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[12] | PASS | 0.171 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0445 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[13] | PASS | 0.178 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0446 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[14] | PASS | 0.054 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0447 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[15] | PASS | 0.155 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0448 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[16] | PASS | 0.063 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0449 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[17] | PASS | 0.057 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0450 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[18] | PASS | 0.173 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0451 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[19] | PASS | 0.145 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0452 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[20] | PASS | 0.056 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0453 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[21] | PASS | 0.168 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0454 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[22] | PASS | 0.138 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0455 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[23] | PASS | 0.217 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0456 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[24] | PASS | 0.052 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0457 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[25] | PASS | 0.058 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0458 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[26] | PASS | 0.056 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0459 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[27] | PASS | 0.055 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0460 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[28] | PASS | 0.058 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0461 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[29] | PASS | 0.139 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0462 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[30] | PASS | 0.138 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0463 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[31] | PASS | 0.137 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0464 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[32] | PASS | 0.143 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0465 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[33] | PASS | 0.052 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0466 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[34] | PASS | 0.138 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0467 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[35] | PASS | 0.059 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0468 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[36] | PASS | 0.136 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0469 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[37] | PASS | 0.137 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0470 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[38] | PASS | 0.057 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0471 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[39] | PASS | 0.054 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0472 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[40] | PASS | 0.133 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0473 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[41] | PASS | 0.142 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0474 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[42] | PASS | 0.056 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0475 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[43] | PASS | 0.148 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0476 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[44] | PASS | 0.058 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0477 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[45] | PASS | 0.057 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0478 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[46] | PASS | 0.128 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0479 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[47] | PASS | 0.049 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0480 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[48] | PASS | 0.139 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0481 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[49] | PASS | 0.057 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0482 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[50] | PASS | 0.048 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0483 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[51] | PASS | 0.055 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0484 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[52] | PASS | 0.133 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0485 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[53] | PASS | 0.047 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0486 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[54] | PASS | 0.142 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0487 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[55] | PASS | 0.051 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0488 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[56] | PASS | 0.056 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0489 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[57] | PASS | 0.056 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0490 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[58] | PASS | 0.145 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0491 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[59] | PASS | 0.05 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0492 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[60] | PASS | 0.143 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0493 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[61] | PASS | 0.049 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0494 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[62] | PASS | 0.059 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0495 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[63] | PASS | 0.014 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0496 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[64] | PASS | 0.122 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0497 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[65] | PASS | 0.129 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0498 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[66] | PASS | 0.131 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0499 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[67] | PASS | 0.05 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0500 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[68] | PASS | 0.057 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0501 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[69] | PASS | 0.054 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0502 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[70] | PASS | 0.153 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0503 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[71] | PASS | 0.003 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0504 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[72] | PASS | 0.003 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0505 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[73] | PASS | 0.003 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0506 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[74] | PASS | 0.014 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0507 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[75] | PASS | 0.015 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0508 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[76] | PASS | 0.009 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0509 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[77] | PASS | 0.012 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0510 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[78] | PASS | 0.015 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0511 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[79] | PASS | 0.007 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0512 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[80] | PASS | 0.013 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0513 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[81] | PASS | 0.135 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0514 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[82] | PASS | 0.144 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0515 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[83] | PASS | 0.151 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0516 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[84] | PASS | 0.155 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0517 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[85] | PASS | 0.147 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0518 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[86] | PASS | 0.056 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0519 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[87] | PASS | 0.05 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0520 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[88] | PASS | 0.061 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0521 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[89] | PASS | 0.009 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0522 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[90] | PASS | 0.021 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0523 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[91] | PASS | 0.05 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0524 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[92] | PASS | 0.014 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0525 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[93] | PASS | 0.008 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0526 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[94] | PASS | 0.136 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0527 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[95] | PASS | 0.056 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0528 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[96] | PASS | 0.06 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0529 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[97] | PASS | 0.053 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0530 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[98] | PASS | 0.06 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0531 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[99] | PASS | 0.15 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0532 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[100] | PASS | 0.049 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0533 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[101] | PASS | 0.052 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0534 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[102] | PASS | 0.051 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0535 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[103] | PASS | 0.066 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0536 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[104] | PASS | 0.045 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0537 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[105] | PASS | 0.047 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0538 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[106] | PASS | 0.05 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0539 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[107] | PASS | 0.046 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0540 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[108] | PASS | 0.047 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0541 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[109] | PASS | 0.122 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0542 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[110] | PASS | 0.05 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0543 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[111] | PASS | 0.046 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0544 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[112] | PASS | 0.046 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0545 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[113] | PASS | 0.117 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0546 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[114] | PASS | 0.046 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0547 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[115] | PASS | 0.049 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0548 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[116] | PASS | 0.007 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0549 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[117] | PASS | 0.0 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0550 | com.oneshop.QaAuditRouteDatabaseIntegrationTest#anonymousEndpointGuards()[118] | PASS | 0.022 | None | target/surefire-reports/TEST-com.oneshop.QaAuditRouteDatabaseIntegrationTest.xml |
| additional-0551 | com.oneshop.QaAuditScreenBrowserDatabaseIntegrationTest#roleScreensAndSmallUiActions | FAIL | 39.016 | None | target/surefire-reports/TEST-com.oneshop.QaAuditScreenBrowserDatabaseIntegrationTest.xml |

## Failures / errors: exact assertion excerpts

### baseline-0146 — com.oneshop.CatalogHttpDatabaseIntegrationTest#imageUploadReachesCloudinaryServiceThroughTheMultipartFormWithCsrf

```text

Expecting actual:
  "<!DOCTYPE html>

<html lang="vi">
<head>
    <head>
    
        <meta charset="UTF-8">
        <m ... [HTML/large body omitted] ... 
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </section>
    </div>
</div>
<script src="/js/upload-validator.js"></script>
</main>
    </div>
</div>


    <script src="/vendor/bootstrap/bootstrap.bundle.min.js"></script>
    <script src="/js/main.js"></script>

<script src="/js/admin-staff.js" defer></script>
<script src="/js/os-suggest.js" defer></script>
</body>
</html>
"
to contain:
  ["Không thể làm việc với Cloudinary", "id="product-images""]
but could not find:
  ["Không thể làm việc với Cloudinary"]
 
at com.oneshop.CatalogHttpDatabaseIntegrationTest.imageUploadReachesCloudinaryServiceThroughTheMultipartFormWithCsrf(CatalogHttpDatabaseIntegrationTest.java:702)
```

### baseline-0153 — com.oneshop.CatalogHttpDatabaseIntegrationTest#tc18_inactiveStoreIsShownAsNotOperatingAndCannotBeChosen

```text

expected: 0
 but was: 5
at com.oneshop.CatalogHttpDatabaseIntegrationTest.tc18_inactiveStoreIsShownAsNotOperatingAndCannotBeChosen(CatalogHttpDatabaseIntegrationTest.java:456)
```

### baseline-0164 — com.oneshop.CatalogHttpDatabaseIntegrationTest#aStoreCookieDoesNotMarkAnythingInTheFinder

```text

expected: 0
 but was: 5
at com.oneshop.CatalogHttpDatabaseIntegrationTest.aStoreCookieDoesNotMarkAnythingInTheFinder(CatalogHttpDatabaseIntegrationTest.java:466)
```

### baseline-0184 — com.oneshop.CategoryAdminSecurityWebIntegrationTest#adminCanAccessCategoriesListAndRendersTableAndUploadControls

```text

Expecting actual:
  "<!DOCTYPE html>

<html lang="vi">
<head>
    <head>
    
        <meta charset="UTF-8">
        <m ... [HTML/large body omitted] ... button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Hủy</button>
                <button type="submit" class="btn btn-primary" form="category-form">Lưu danh mục</button>
            </div>
        </div>
    </div>
</div>




<script src="/js/upload-validator.js"></script>
<script src="/js/admin-form-modal.js"></script>
</main>
    </div>
</div>


    <script src="/vendor/bootstrap/bootstrap.bundle.min.js"></script>
    <script src="/js/main.js"></script>

<script src="/js/admin-staff.js" defer></script>
<script src="/js/os-suggest.js" defer></script>
</body>
</html>
"
to contain:
  "Danh mục dùng chung toàn chuỗi" 
at com.oneshop.CategoryAdminSecurityWebIntegrationTest.adminCanAccessCategoriesListAndRendersTableAndUploadControls(CategoryAdminSecurityWebIntegrationTest.java:102)
```

### baseline-0352 — com.oneshop.DeliveryDatabaseIntegrationTest#readScopeSupportsMultipleAssignmentsAndOnlyDeliverySnapshots

```text
PreparedStatementCallback; SQL [insert into dbo.staff_store_assignments(user_id,store_id,status) select u.user_id,s.store_id,'ACTIVE' from dbo.users u cross join dbo.stores s where u.email=? and s.code=?]; Cannot insert duplicate key row in object 'dbo.staff_store_assignments' with unique index 'UX_ssa_one_active_store_per_user'. The duplicate key value is (3).
at com.oneshop.ReviewV3TestSupport.assign(ReviewV3TestSupport.java:154)
at com.oneshop.DeliveryDatabaseIntegrationTest.readScopeSupportsMultipleAssignmentsAndOnlyDeliverySnapshots(DeliveryDatabaseIntegrationTest.java:121)
```

### baseline-0456 — com.oneshop.FulfillmentOperationsDatabaseIntegrationTest#be04_08_twoAssignments

```text
StatementCallback; SQL [insert into dbo.staff_store_assignments (user_id, store_id, status) values (2, 2, 'ACTIVE')]; Cannot insert duplicate key row in object 'dbo.staff_store_assignments' with unique index 'UX_ssa_one_active_store_per_user'. The duplicate key value is (2).
at com.oneshop.FulfillmentOperationsDatabaseIntegrationTest.be04_08_twoAssignments(FulfillmentOperationsDatabaseIntegrationTest.java:263)
```

### baseline-0694 — com.oneshop.Phase10IntegrationWebDatabaseTest#revokingOneOfMultipleAssignmentsImmediatelyRemovesPeerReadsAndWritesAcrossAllModules

```text
PreparedStatementCallback; SQL [insert into dbo.staff_store_assignments(user_id,store_id,status) select u.user_id,s.store_id,'ACTIVE' from dbo.users u cross join dbo.stores s where u.email=? and s.code=?]; Cannot insert duplicate key row in object 'dbo.staff_store_assignments' with unique index 'UX_ssa_one_active_store_per_user'. The duplicate key value is (3).
at com.oneshop.ReviewV3TestSupport.assign(ReviewV3TestSupport.java:154)
at com.oneshop.Phase10IntegrationWebDatabaseTest.revokingOneOfMultipleAssignmentsImmediatelyRemovesPeerReadsAndWritesAcrossAllModules(Phase10IntegrationWebDatabaseTest.java:47)
```

### baseline-0698 — com.oneshop.Phase11AdminDatabaseIntegrationTest#multipleAssignmentsAndStoreChangeDoNotTrustBrowserSelector

```text

expected: 302
 but was: 200
at com.oneshop.Phase11AdminDatabaseIntegrationTest.multipleAssignmentsAndStoreChangeDoNotTrustBrowserSelector(Phase11AdminDatabaseIntegrationTest.java:196)
```

### baseline-0725 — com.oneshop.Phase12CloudinaryLiveVerification#tc18LiveBrandLogoUploadReplaceDeleteWithRealJpegAndWebp

```text
Environment variable [CLOUDINARY_LIVE_TEST] does not exist

```

### baseline-0726 — com.oneshop.Phase12CloudinaryLiveVerification#tc19LiveUploadRejectsInvalidFileAndOversizedFile

```text
Environment variable [CLOUDINARY_LIVE_TEST] does not exist

```

### baseline-0727 — com.oneshop.Phase12CloudinaryLiveVerification#tc17LiveUploadStoresOnlyUrlAndPublicIdAndRemovesOnlyItsFixture

```text
Environment variable [CLOUDINARY_LIVE_TEST] does not exist

```

### baseline-0766 — com.oneshop.Phase9IntegrationWebDatabaseTest#multipleAssignmentsAllowOnlyActiveStoreAndUseAuthenticatedActor

```text
PreparedStatementCallback; SQL [insert into dbo.staff_store_assignments(user_id,store_id,status) select u.user_id,s.store_id,'ACTIVE' from dbo.users u cross join dbo.stores s where u.email=? and s.code=?]; Cannot insert duplicate key row in object 'dbo.staff_store_assignments' with unique index 'UX_ssa_one_active_store_per_user'. The duplicate key value is (3).
at com.oneshop.ReviewV3TestSupport.assign(ReviewV3TestSupport.java:154)
at com.oneshop.Phase9IntegrationWebDatabaseTest.multipleAssignmentsAllowOnlyActiveStoreAndUseAuthenticatedActor(Phase9IntegrationWebDatabaseTest.java:47)
```

### baseline-0891 — com.oneshop.ReviewV3ConcurrencyDatabaseIntegrationTest#twoStaffRepliesSerializeIntoOneOfficialRow

```text
PreparedStatementCallback; SQL [insert into dbo.staff_store_assignments(user_id,store_id,status) select u.user_id,s.store_id,'ACTIVE' from dbo.users u cross join dbo.stores s where u.email=? and s.code=?]; Cannot insert duplicate key row in object 'dbo.staff_store_assignments' with unique index 'UX_ssa_one_active_store_per_user'. The duplicate key value is (3).
at com.oneshop.ReviewV3TestSupport.assign(ReviewV3TestSupport.java:154)
at com.oneshop.ReviewV3ConcurrencyDatabaseIntegrationTest.twoStaffRepliesSerializeIntoOneOfficialRow(ReviewV3ConcurrencyDatabaseIntegrationTest.java:53)
```

### baseline-0894 — com.oneshop.ReviewV3DatabaseIntegrationTest#multipleStoreAssignmentsAllowOnlyThoseStores

```text
PreparedStatementCallback; SQL [insert into dbo.staff_store_assignments(user_id,store_id,status) select u.user_id,s.store_id,'ACTIVE' from dbo.users u cross join dbo.stores s where u.email=? and s.code=?]; Cannot insert duplicate key row in object 'dbo.staff_store_assignments' with unique index 'UX_ssa_one_active_store_per_user'. The duplicate key value is (4).
at com.oneshop.ReviewV3TestSupport.assign(ReviewV3TestSupport.java:154)
at com.oneshop.ReviewV3DatabaseIntegrationTest.multipleStoreAssignmentsAllowOnlyThoseStores(ReviewV3DatabaseIntegrationTest.java:151)
```

### baseline-1555 — com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest#multipleAssignmentsAllowPeerPickupThenRevocationBlocksTheNextMutationImmediately

```text
PreparedStatementCallback; SQL [insert into dbo.staff_store_assignments(user_id,store_id,status) select u.user_id,s.store_id,'ACTIVE' from dbo.users u cross join dbo.stores s where u.email=? and s.code=?]; Cannot insert duplicate key row in object 'dbo.staff_store_assignments' with unique index 'UX_ssa_one_active_store_per_user'. The duplicate key value is (3).
at com.oneshop.ReviewV3TestSupport.assign(ReviewV3TestSupport.java:154)
at com.oneshop.StaffFulfillmentOperationsDatabaseIntegrationTest.multipleAssignmentsAllowPeerPickupThenRevocationBlocksTheNextMutationImmediately(StaffFulfillmentOperationsDatabaseIntegrationTest.java:82)
```

### baseline-1604 — com.oneshop.StaffInventoryDatabaseIntegrationTest#multipleActiveAssignmentsAllowOnlyTheirStockAndRevocationBlocksFurtherWrites

```text
PreparedStatementCallback; SQL [insert into dbo.staff_store_assignments(user_id,store_id,status,assigned_at) values (?,2,'ACTIVE',SYSDATETIME()),(?,4,'INACTIVE',SYSDATETIME())]; Cannot insert duplicate key row in object 'dbo.staff_store_assignments' with unique index 'UX_ssa_one_active_store_per_user'. The duplicate key value is (2).
at com.oneshop.StaffInventoryDatabaseIntegrationTest.multipleActiveAssignmentsAllowOnlyTheirStockAndRevocationBlocksFurtherWrites(StaffInventoryDatabaseIntegrationTest.java:242)
```

### baseline-1620 — com.oneshop.StaffOperationsDatabaseIntegrationTest#multipleAssignmentsKeepPerStoreCountsAndRevocationImmediatelyRemovesPeerResources

```text
PreparedStatementCallback; SQL [insert into dbo.staff_store_assignments(user_id,store_id,status,assigned_at) values (?,2,'ACTIVE',SYSDATETIME()),(?,4,'INACTIVE',SYSDATETIME())]; Cannot insert duplicate key row in object 'dbo.staff_store_assignments' with unique index 'UX_ssa_one_active_store_per_user'. The duplicate key value is (2).
at com.oneshop.StaffOperationsDatabaseIntegrationTest.multipleAssignmentsKeepPerStoreCountsAndRevocationImmediatelyRemovesPeerResources(StaffOperationsDatabaseIntegrationTest.java:211)
```

### baseline-1652 — com.oneshop.StaffStoreScopeDatabaseIntegrationTest#multipleAssignmentsPreserveOnlyActiveScopeAndResolveFreshAfterRevocation

```text
PreparedStatementCallback; SQL [insert into dbo.staff_store_assignments(user_id,store_id,status,assigned_at) values (?,2,'ACTIVE',SYSDATETIME()),(?,4,'INACTIVE',SYSDATETIME())]; Cannot insert duplicate key row in object 'dbo.staff_store_assignments' with unique index 'UX_ssa_one_active_store_per_user'. The duplicate key value is (2).
at com.oneshop.StaffStoreScopeDatabaseIntegrationTest.multipleAssignmentsPreserveOnlyActiveScopeAndResolveFreshAfterRevocation(StaffStoreScopeDatabaseIntegrationTest.java:129)
```

### additional-0020 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[17]

```text
[/products?page=178956971] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0022 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[19]

```text
[/products?page=2147483646] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0024 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[21]

```text
[/products?page=2147483647] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0042 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[39]

```text
[/admin/products?page=107374183] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0044 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[41]

```text
[/admin/products?page=178956969] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0046 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[43]

```text
[/admin/products?page=178956970] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0048 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[45]

```text
[/admin/products?page=178956971] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0050 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[47]

```text
[/admin/products?page=2147483646] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0052 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[49]

```text
[/admin/products?page=2147483647] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0098 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[95]

```text
[/admin/stores?page=107374183] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0100 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[97]

```text
[/admin/stores?page=178956969] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0102 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[99]

```text
[/admin/stores?page=178956970] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0104 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[101]

```text
[/admin/stores?page=178956971] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0106 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[103]

```text
[/admin/stores?page=2147483646] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0108 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[105]

```text
[/admin/stores?page=2147483647] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0126 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[123]

```text
[/admin/store-products?page=107374183] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0128 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[125]

```text
[/admin/store-products?page=178956969] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0130 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[127]

```text
[/admin/store-products?page=178956970] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0132 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[129]

```text
[/admin/store-products?page=178956971] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0134 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[131]

```text
[/admin/store-products?page=2147483646] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0136 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[133]

```text
[/admin/store-products?page=2147483647] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0154 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[151]

```text
[/admin/inventory?page=107374183] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0156 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[153]

```text
[/admin/inventory?page=178956969] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0158 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[155]

```text
[/admin/inventory?page=178956970] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0160 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[157]

```text
[/admin/inventory?page=178956971] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0162 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[159]

```text
[/admin/inventory?page=2147483646] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0164 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[161]

```text
[/admin/inventory?page=2147483647] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0182 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[179]

```text
[/admin/orders?page=107374183] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0184 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[181]

```text
[/admin/orders?page=178956969] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0186 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[183]

```text
[/admin/orders?page=178956970] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0188 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[185]

```text
[/admin/orders?page=178956971] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0190 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[187]

```text
[/admin/orders?page=2147483646] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0192 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[189]

```text
[/admin/orders?page=2147483647] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0266 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[263]

```text
[/admin/payments?page=107374183] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0268 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[265]

```text
[/admin/payments?page=178956969] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0270 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[267]

```text
[/admin/payments?page=178956970] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0272 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[269]

```text
[/admin/payments?page=178956971] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0274 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[271]

```text
[/admin/payments?page=2147483646] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0276 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[273]

```text
[/admin/payments?page=2147483647] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0378 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[375]

```text
[/staff/reviews?page=107374183] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0379 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[376]

```text
[/staff/reviews?page=107374183&q=qa-no-match-999] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0380 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[377]

```text
[/staff/reviews?page=178956969] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0381 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[378]

```text
[/staff/reviews?page=178956969&q=qa-no-match-999] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0382 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[379]

```text
[/staff/reviews?page=178956970] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0383 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[380]

```text
[/staff/reviews?page=178956970&q=qa-no-match-999] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0384 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[381]

```text
[/staff/reviews?page=178956971] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0385 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[382]

```text
[/staff/reviews?page=178956971&q=qa-no-match-999] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0386 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[383]

```text
[/staff/reviews?page=2147483646] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0387 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[384]

```text
[/staff/reviews?page=2147483646&q=qa-no-match-999] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0388 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[385]

```text
[/staff/reviews?page=2147483647] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0389 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[386]

```text
[/staff/reviews?page=2147483647&q=qa-no-match-999] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0408 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[405]

```text
[/admin/orders?from=0001-01-01&to=9999-12-31] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0414 — com.oneshop.QaAuditBoundaryDatabaseIntegrationTest#readerParameterBoundaries()[411]

```text
[/admin/payments?from=0001-01-01&to=9999-12-31] 
Expecting actual:
  500
to be between:
  [200, 499]

at com.oneshop.QaAuditBoundaryDatabaseIntegrationTest.lambda$readerParameterBoundaries$0(QaAuditBoundaryDatabaseIntegrationTest.java:34)
```

### additional-0420 — com.oneshop.QaAuditImageRollbackDatabaseIntegrationTest#replacingBrandImageMustNotDeleteOldFileBeforeCommit()[1]

```text
[a rollback must keep the file still referenced by the database] 
Expecting KeySetView:
  ["qa-audit/new"]
to contain:
  ["qa-audit/old"]
but could not find the following element(s):
  ["qa-audit/old"]

at com.oneshop.QaAuditImageRollbackDatabaseIntegrationTest.replacingBrandImageMustNotDeleteOldFileBeforeCommit(QaAuditImageRollbackDatabaseIntegrationTest.java:59)
```

### additional-0421 — com.oneshop.QaAuditImageRollbackDatabaseIntegrationTest#replacingBrandImageMustNotDeleteOldFileBeforeCommit()[2]

```text
[a rollback must keep the file still referenced by the database] 
Expecting KeySetView:
  ["qa-audit/new"]
to contain:
  ["qa-audit/old"]
but could not find the following element(s):
  ["qa-audit/old"]

at com.oneshop.QaAuditImageRollbackDatabaseIntegrationTest.replacingBrandImageMustNotDeleteOldFileBeforeCommit(QaAuditImageRollbackDatabaseIntegrationTest.java:59)
```

### additional-0422 — com.oneshop.QaAuditImageRollbackDatabaseIntegrationTest#uploadedProductFileMustBeCompensatedWhenTransactionRollsBack()[1]

```text
[rolled-back upload must not leak an unreferenced external file] 
Expecting empty but was: ["qa-audit/new"]
at com.oneshop.QaAuditImageRollbackDatabaseIntegrationTest.uploadedProductFileMustBeCompensatedWhenTransactionRollsBack(QaAuditImageRollbackDatabaseIntegrationTest.java:48)
```

### additional-0423 — com.oneshop.QaAuditImageRollbackDatabaseIntegrationTest#uploadedProductFileMustBeCompensatedWhenTransactionRollsBack()[2]

```text
[rolled-back upload must not leak an unreferenced external file] 
Expecting empty but was: ["qa-audit/new"]
at com.oneshop.QaAuditImageRollbackDatabaseIntegrationTest.uploadedProductFileMustBeCompensatedWhenTransactionRollsBack(QaAuditImageRollbackDatabaseIntegrationTest.java:48)
```

### additional-0424 — com.oneshop.QaAuditRaceDatabaseIntegrationTest#profileMustNotReactivateAnAccountDisabledDuringRequest()[1]

```text
[profile must preserve security changes committed by Admin] 
expected: {"role_id"=2L, "status"="INACTIVE"}
 but was: {"role_id"=2L, "status"="ACTIVE"}
at com.oneshop.QaAuditRaceDatabaseIntegrationTest.profileRace(QaAuditRaceDatabaseIntegrationTest.java:89)
at com.oneshop.QaAuditRaceDatabaseIntegrationTest.profileMustNotReactivateAnAccountDisabledDuringRequest(QaAuditRaceDatabaseIntegrationTest.java:55)
```

### additional-0425 — com.oneshop.QaAuditRaceDatabaseIntegrationTest#profileMustNotReactivateAnAccountDisabledDuringRequest()[2]

```text
[profile must preserve security changes committed by Admin] 
expected: {"role_id"=2L, "status"="INACTIVE"}
 but was: {"role_id"=2L, "status"="ACTIVE"}
at com.oneshop.QaAuditRaceDatabaseIntegrationTest.profileRace(QaAuditRaceDatabaseIntegrationTest.java:89)
at com.oneshop.QaAuditRaceDatabaseIntegrationTest.profileMustNotReactivateAnAccountDisabledDuringRequest(QaAuditRaceDatabaseIntegrationTest.java:55)
```

### additional-0426 — com.oneshop.QaAuditRaceDatabaseIntegrationTest#profileMustNotReactivateAnAccountDisabledDuringRequest()[3]

```text
[profile must preserve security changes committed by Admin] 
expected: {"role_id"=2L, "status"="INACTIVE"}
 but was: {"role_id"=2L, "status"="ACTIVE"}
at com.oneshop.QaAuditRaceDatabaseIntegrationTest.profileRace(QaAuditRaceDatabaseIntegrationTest.java:89)
at com.oneshop.QaAuditRaceDatabaseIntegrationTest.profileMustNotReactivateAnAccountDisabledDuringRequest(QaAuditRaceDatabaseIntegrationTest.java:55)
```

### additional-0427 — com.oneshop.QaAuditRaceDatabaseIntegrationTest#parallelPrimaryImageSelectionMustLeaveExactlyOnePrimary()[1]

```text
[exactly one primary per Product after overlapping Admin selections] 
expected: 1L
 but was: 2L
at com.oneshop.QaAuditRaceDatabaseIntegrationTest.parallelPrimaryImageSelectionMustLeaveExactlyOnePrimary(QaAuditRaceDatabaseIntegrationTest.java:121)
```

### additional-0428 — com.oneshop.QaAuditRaceDatabaseIntegrationTest#parallelPrimaryImageSelectionMustLeaveExactlyOnePrimary()[2]

```text
[exactly one primary per Product after overlapping Admin selections] 
expected: 1L
 but was: 2L
at com.oneshop.QaAuditRaceDatabaseIntegrationTest.parallelPrimaryImageSelectionMustLeaveExactlyOnePrimary(QaAuditRaceDatabaseIntegrationTest.java:121)
```

### additional-0429 — com.oneshop.QaAuditRaceDatabaseIntegrationTest#parallelPrimaryImageSelectionMustLeaveExactlyOnePrimary()[3]

```text
[exactly one primary per Product after overlapping Admin selections] 
expected: 1L
 but was: 2L
at com.oneshop.QaAuditRaceDatabaseIntegrationTest.parallelPrimaryImageSelectionMustLeaveExactlyOnePrimary(QaAuditRaceDatabaseIntegrationTest.java:121)
```

### additional-0430 — com.oneshop.QaAuditRaceDatabaseIntegrationTest#profileMustNotRestoreAStaffRoleRevokedDuringRequest()[1]

```text
[profile must preserve security changes committed by Admin] 
expected: {"role_id"=1L, "status"="ACTIVE"}
 but was: {"role_id"=2L, "status"="ACTIVE"}
at com.oneshop.QaAuditRaceDatabaseIntegrationTest.profileRace(QaAuditRaceDatabaseIntegrationTest.java:89)
at com.oneshop.QaAuditRaceDatabaseIntegrationTest.profileMustNotRestoreAStaffRoleRevokedDuringRequest(QaAuditRaceDatabaseIntegrationTest.java:59)
```

### additional-0431 — com.oneshop.QaAuditRaceDatabaseIntegrationTest#profileMustNotRestoreAStaffRoleRevokedDuringRequest()[2]

```text
[profile must preserve security changes committed by Admin] 
expected: {"role_id"=1L, "status"="ACTIVE"}
 but was: {"role_id"=2L, "status"="ACTIVE"}
at com.oneshop.QaAuditRaceDatabaseIntegrationTest.profileRace(QaAuditRaceDatabaseIntegrationTest.java:89)
at com.oneshop.QaAuditRaceDatabaseIntegrationTest.profileMustNotRestoreAStaffRoleRevokedDuringRequest(QaAuditRaceDatabaseIntegrationTest.java:59)
```

### additional-0432 — com.oneshop.QaAuditRaceDatabaseIntegrationTest#profileMustNotRestoreAStaffRoleRevokedDuringRequest()[3]

```text
[profile must preserve security changes committed by Admin] 
expected: {"role_id"=1L, "status"="ACTIVE"}
 but was: {"role_id"=2L, "status"="ACTIVE"}
at com.oneshop.QaAuditRaceDatabaseIntegrationTest.profileRace(QaAuditRaceDatabaseIntegrationTest.java:89)
at com.oneshop.QaAuditRaceDatabaseIntegrationTest.profileMustNotRestoreAStaffRoleRevokedDuringRequest(QaAuditRaceDatabaseIntegrationTest.java:59)
```

### additional-0551 — com.oneshop.QaAuditScreenBrowserDatabaseIntegrationTest#roleScreensAndSmallUiActions

```text
[see docs/qa-audit/evidence/screens/results.json and node.log] 
expected: 0
 but was: 1
at com.oneshop.QaAuditScreenBrowserDatabaseIntegrationTest.roleScreensAndSmallUiActions(QaAuditScreenBrowserDatabaseIntegrationTest.java:20)
```

## Browser checkpoints (không cộng thêm vào số JUnit cases)

| Scenario | Status | Actual / evidence |
|---|---|---|
| guest / | PASS | {'url': '/', 'title': 'Mỹ phẩm chính hãng, một giá online toàn hệ thống \| OneShop', 'forms': [{'action': '/products', 'method': 'get'}], 'buttons': 2} |
| guest /login | PASS | {'url': '/login', 'title': 'Đăng nhập - OneShop \| OneShop', 'forms': [{'action': '/login', 'method': 'post'}], 'buttons': 2} |
| guest /register | PASS | {'url': '/register', 'title': 'Đăng ký - OneShop \| OneShop', 'forms': [{'action': '/register', 'method': 'post'}], 'buttons': 2} |
| guest /products | PASS | {'url': '/products', 'title': 'Mỹ phẩm &amp; Chăm sóc sắc đẹp - OneShop \| OneShop', 'forms': [{'action': '/products', 'method': 'get'}], 'buttons': 2} |
| guest /products?q=khongtimthayqa999 | PASS | {'url': '/products?q=khongtimthayqa999', 'title': 'Mỹ phẩm &amp; Chăm sóc sắc đẹp - OneShop \| OneShop', 'forms': [{'action': '/products', 'method': 'get'}], 'buttons': 2} |
| guest /products?page=1 | PASS | {'url': '/products?page=1', 'title': 'Mỹ phẩm &amp; Chăm sóc sắc đẹp - OneShop \| OneShop', 'forms': [{'action': '/products', 'method': 'get'}], 'buttons': 2} |
| guest /products?categoryId=1&brandId=1 | PASS | {'url': '/products?categoryId=1&brandId=1', 'title': 'Mỹ phẩm &amp; Chăm sóc sắc đẹp - OneShop \| OneShop', 'forms': [{'action': '/products', 'method': 'get'}], 'buttons': 2} |
| guest /products/1 | PASS | {'url': '/products/1', 'title': 'Kem dưỡng ẩm CeraVe Moisturising Cream 340g - OneShop \| OneShop', 'forms': [], 'buttons': 1} |
| guest /stores | PASS | {'url': '/stores', 'title': 'Hệ thống cửa hàng - OneShop \| OneShop', 'forms': [{'action': '/stores', 'method': 'get'}, {'action': '/stores/select', 'method': 'post'}, {'action': '/stores/select', 'method': 'post'}, {'action': '/stores/select', 'method': 'post'}, {'action': '/stores/select', 'method': 'post'}, {'action': '/stores/select', 'method': 'post'}], 'buttons': 7} |
| guest /stores?provinceCity=invalid | PASS | {'url': '/stores?provinceCity=invalid', 'title': 'Hệ thống cửa hàng - OneShop \| OneShop', 'forms': [{'action': '/stores', 'method': 'get'}], 'buttons': 2} |
| guest /image-credits | PASS | {'url': '/image-credits', 'title': 'Ghi công hình ảnh sản phẩm - OneShop \| OneShop', 'forms': [], 'buttons': 1} |
| catalog browser title decodes entities /products | FAIL | Error: Literal HTML entity in document.title: Mỹ phẩm &amp; Chăm sóc sắc đẹp - OneShop \| OneShop     at file:///D:/One_Shop/scripts/qa-audit/screens.mjs:25:38     at async check (file:///D:/One_Shop/scripts/qa-audit/screens.mjs:11:46)     at async file:///D:/One_Shop/scripts/qa-audit/screens.mjs:23:55 |
| catalog browser title decodes entities /products?q=CeraVe | FAIL | Error: Literal HTML entity in document.title: Mỹ phẩm &amp; Chăm sóc sắc đẹp - OneShop \| OneShop     at file:///D:/One_Shop/scripts/qa-audit/screens.mjs:25:38     at async check (file:///D:/One_Shop/scripts/qa-audit/screens.mjs:11:46)     at async file:///D:/One_Shop/scripts/qa-audit/screens.mjs:23:55 |
| guest search form native submission | PASS | /products |
| customer login | PASS | / |
| customer /account | PASS | {'url': '/account', 'title': 'Thông tin cá nhân \| OneShop', 'forms': [{'action': '/logout', 'method': 'post'}, {'action': '/logout', 'method': 'post'}, {'action': '/logout', 'method': 'post'}, {'action': '/account', 'method': 'post'}], 'buttons': 7} |
| customer /orders | PASS | {'url': '/orders', 'title': 'Đơn hàng của tôi \| OneShop', 'forms': [{'action': '/logout', 'method': 'post'}, {'action': '/logout', 'method': 'post'}, {'action': '/logout', 'method': 'post'}], 'buttons': 6} |
| customer /cart | PASS | {'url': '/cart', 'title': 'Giỏ hàng - OneShop \| OneShop', 'forms': [{'action': '/logout', 'method': 'post'}, {'action': '/logout', 'method': 'post'}, {'action': '/logout', 'method': 'post'}, {'action': '/cart/items/1', 'method': 'post'}, {'action': '/cart/items/1/delete', 'method': 'post'}, {'action': '/cart/items/2', 'method': 'post'}, {'action': '/cart/items/2/delete', 'method': 'post'}, {'action': '/checkout', 'method': 'get'}], 'buttons': 13} |
| profile blank name rejected without write | PASS | server validation rendered |
| customer logout | PASS | authenticated cookie cleared |
| staff login | PASS | /staff |
| staff /account | PASS | {'url': '/account', 'title': 'Thông tin cá nhân \| OneShop', 'forms': [{'action': '/logout', 'method': 'post'}, {'action': '/logout', 'method': 'post'}, {'action': '/logout', 'method': 'post'}, {'action': '/account', 'method': 'post'}], 'buttons': 7} |
| staff /staff/dashboard | PASS | {'url': '/staff/dashboard', 'title': 'Tổng quan chi nhánh \| OneShop Staff', 'forms': [{'action': '/logout', 'method': 'post'}], 'buttons': 3} |
| staff /staff/fulfillments | PASS | {'url': '/staff/fulfillments', 'title': 'Đơn cần xử lý của chi nhánh \| OneShop Staff', 'forms': [{'action': '/logout', 'method': 'post'}, {'action': '/staff/fulfillments', 'method': 'get'}], 'buttons': 4} |
| staff /staff/fulfillments/delivery | PASS | {'url': '/staff/fulfillments/delivery', 'title': 'Đơn cần xử lý · Giao tận nơi \| OneShop Staff', 'forms': [{'action': '/logout', 'method': 'post'}, {'action': '/staff/fulfillments/delivery', 'method': 'get'}], 'buttons': 4} |
| staff /staff/fulfillments/pickup | PASS | {'url': '/staff/fulfillments/pickup', 'title': 'Đơn cần xử lý · Nhận tại cửa hàng \| OneShop Staff', 'forms': [{'action': '/logout', 'method': 'post'}, {'action': '/staff/fulfillments/pickup', 'method': 'get'}], 'buttons': 4} |
| staff /staff/stock | PASS | {'url': '/staff/stock', 'title': 'Tồn kho chi nhánh \| OneShop Staff', 'forms': [{'action': '/logout', 'method': 'post'}, {'action': '/staff/stock', 'method': 'get'}, {'action': '/staff/stock/13/adjust', 'method': 'post'}, {'action': '/staff/stock/25/adjust', 'method': 'post'}, {'action': '/staff/stock/3/adjust', 'method': 'post'}, {'action': '/staff/stock/5/adjust', 'method': 'post'}, {'action': '/staff/stock/31/adjust', 'method': 'post'}, {'action': '/staff/stock/14/adjust', 'method': 'post'}, {'action': '/staff/stock/42/adjust', 'method': 'post'}, {'action': '/staff/stock/44/adjust', 'method': 'post'}, {'action': '/staff/stock/2/adjust', 'method': 'post'}, {'action': '/staff/stock/40/adjust', 'method': 'post'}, {'action': '/staff/stock/35/adjust', 'method': 'post'}, {'action': '/staff/stock/47/adjust', 'method': 'post'}, {'action': '/staff/stock/29/adjust', 'method': 'post'}, {'action': '/staff/stock/11/adjust', 'method': 'post'}, {'action': '/staff/stock/8/adjust', 'method': 'post'}, {'action': '/staff/stock/16/adjust', 'method': 'post'}, {'action': '/staff/stock/10/adjust', 'method': 'post'}, {'action': '/staff/stock/1/adjust', 'method': 'post'}], 'buttons': 76} |
| staff /staff/stock/1/adjust | PASS | {'url': '/staff/stock/1/adjust', 'title': 'Điều chỉnh tồn kho \| OneShop Staff', 'forms': [{'action': '/logout', 'method': 'post'}, {'action': '/staff/stock/1/adjust', 'method': 'post'}], 'buttons': 4} |
| staff /staff/inventory-history | PASS | {'url': '/staff/inventory-history', 'title': 'Nhật ký biến động tồn \| OneShop Staff', 'forms': [{'action': '/logout', 'method': 'post'}, {'action': '/staff/inventory-history', 'method': 'get'}], 'buttons': 6} |
| staff /staff/inventory-history/1 | PASS | {'url': '/staff/inventory-history/1', 'title': 'Lịch sử biến động tồn kho \| OneShop Staff', 'forms': [{'action': '/logout', 'method': 'post'}], 'buttons': 3} |
| staff /staff/reviews | PASS | {'url': '/staff/reviews', 'title': 'Đánh giá của chi nhánh \| OneShop Staff', 'forms': [{'action': '/logout', 'method': 'post'}, {'action': '/staff/reviews/2/reply', 'method': 'post'}, {'action': '/staff/reviews/1/reply', 'method': 'post'}], 'buttons': 5} |
| staff logout | PASS | authenticated cookie cleared |
| admin login | PASS | /admin |
| admin /account | PASS | {'url': '/account', 'title': 'Thông tin cá nhân \| OneShop', 'forms': [{'action': '/logout', 'method': 'post'}, {'action': '/logout', 'method': 'post'}, {'action': '/logout', 'method': 'post'}, {'action': '/account', 'method': 'post'}], 'buttons': 7} |
| admin /admin | PASS | {'url': '/admin', 'title': 'Tổng quan toàn chuỗi \| OneShop Admin', 'forms': [{'action': '/logout', 'method': 'post'}, {'action': '/admin', 'method': 'get'}], 'buttons': 10} |
| admin /admin/users | PASS | {'url': '/admin/users', 'title': 'Người dùng \| OneShop Admin', 'forms': [{'action': '/logout', 'method': 'post'}, {'action': '/admin/users', 'method': 'get'}], 'buttons': 4} |
| admin /admin/users/new | PASS | {'url': '/admin/users/new', 'title': 'Quản lý người dùng \| OneShop Admin', 'forms': [{'action': '/logout', 'method': 'post'}, {'action': '/admin/users', 'method': 'post'}], 'buttons': 4} |
| admin /admin/users/7/edit | PASS | {'url': '/admin/users/7/edit', 'title': 'Quản lý người dùng \| OneShop Admin', 'forms': [{'action': '/logout', 'method': 'post'}, {'action': '/admin/users/7', 'method': 'post'}], 'buttons': 4} |
| admin /admin/stores | PASS | {'url': '/admin/stores', 'title': 'Chi nhánh \| OneShop Admin', 'forms': [{'action': '/logout', 'method': 'post'}, {'action': '/admin/stores', 'method': 'get'}], 'buttons': 5} |
| admin /admin/stores/new | PASS | {'url': '/admin/stores/new', 'title': 'Thêm chi nhánh \| OneShop Admin', 'forms': [{'action': '/logout', 'method': 'post'}, {'action': '/admin/stores', 'method': 'post'}], 'buttons': 4} |
| admin /admin/stores/1/edit | PASS | {'url': '/admin/stores/1/edit', 'title': 'Sửa chi nhánh \| OneShop Admin', 'forms': [{'action': '/logout', 'method': 'post'}, {'action': '/admin/stores/1', 'method': 'post'}], 'buttons': 4} |
| admin /admin/products | PASS | {'url': '/admin/products', 'title': 'Sản phẩm / SKU \| OneShop Admin', 'forms': [{'action': '/logout', 'method': 'post'}, {'action': '/admin/products', 'method': 'get'}], 'buttons': 4} |
| admin /admin/products/new | PASS | {'url': '/admin/products/new', 'title': 'Thêm sản phẩm \| OneShop Admin', 'forms': [{'action': '/logout', 'method': 'post'}, {'action': '/admin/products', 'method': 'post'}], 'buttons': 4} |
| admin /admin/products/1/edit | PASS | {'url': '/admin/products/1/edit', 'title': 'Sửa sản phẩm \| OneShop Admin', 'forms': [{'action': '/logout', 'method': 'post'}, {'action': '/admin/products/1', 'method': 'post'}, {'action': '/admin/products/1/images', 'method': 'post'}, {'action': '/admin/products/1/images/1/delete', 'method': 'post'}], 'buttons': 6} |
| admin /admin/categories | PASS | {'url': '/admin/categories', 'title': 'Danh mục \| OneShop Admin', 'forms': [{'action': '/logout', 'method': 'post'}, {'action': '/admin/categories', 'method': 'post'}], 'buttons': 7} |
| admin /admin/categories/1/edit | PASS | {'url': '/admin/categories/1/edit', 'title': 'Danh mục \| OneShop Admin', 'forms': [{'action': '/logout', 'method': 'post'}, {'action': '/admin/categories/1', 'method': 'post'}, {'action': '/admin/categories/1/image/delete', 'method': 'post'}], 'buttons': 8} |
| admin /admin/brands | PASS | {'url': '/admin/brands', 'title': 'Thương hiệu \| OneShop Admin', 'forms': [{'action': '/logout', 'method': 'post'}, {'action': '/admin/brands', 'method': 'post'}], 'buttons': 7} |
| admin /admin/brands/1/edit | PASS | {'url': '/admin/brands/1/edit', 'title': 'Thương hiệu \| OneShop Admin', 'forms': [{'action': '/logout', 'method': 'post'}, {'action': '/admin/brands/1', 'method': 'post'}, {'action': '/admin/brands/1/logo', 'method': 'post'}], 'buttons': 8} |
| admin /admin/store-products | PASS | {'url': '/admin/store-products', 'title': 'Sản phẩm theo chi nhánh \| OneShop Admin', 'forms': [{'action': '/logout', 'method': 'post'}, {'action': '/admin/store-products', 'method': 'get'}], 'buttons': 4} |
| admin /admin/store-products/new | PASS | {'url': '/admin/store-products/new', 'title': 'Gắn sản phẩm vào chi nhánh \| OneShop Admin', 'forms': [{'action': '/logout', 'method': 'post'}, {'action': '/admin/store-products', 'method': 'post'}], 'buttons': 4} |
| admin /admin/store-products/1/edit | PASS | {'url': '/admin/store-products/1/edit', 'title': 'Sửa sản phẩm tại chi nhánh \| OneShop Admin', 'forms': [{'action': '/logout', 'method': 'post'}, {'action': '/admin/store-products/1', 'method': 'post'}], 'buttons': 4} |
| admin /admin/staff-assignments | PASS | {'url': '/admin/staff-assignments', 'title': 'Phân công nhân viên \| OneShop Admin', 'forms': [{'action': '/logout', 'method': 'post'}, {'action': '/admin/staff-assignments', 'method': 'get'}, {'action': '/admin/staff-assignments/1/status', 'method': 'post'}, {'action': '/admin/staff-assignments/2/status', 'method': 'post'}, {'action': '/admin/staff-assignments/3/status', 'method': 'post'}, {'action': '/admin/staff-assignments/4/status', 'method': 'post'}, {'action': '/admin/staff-assignments/5/status', 'method': 'post'}, {'action': '/admin/staff-assignments', 'method': 'post'}, {'action': '/admin/staff-assignments/transfer', 'method': 'post'}], 'buttons': 21} |
| admin /admin/inventory | PASS | {'url': '/admin/inventory', 'title': 'Tồn kho toàn chuỗi \| OneShop Admin', 'forms': [{'action': '/logout', 'method': 'post'}, {'action': '/admin/inventory', 'method': 'get'}], 'buttons': 4} |
| admin /admin/orders | PASS | {'url': '/admin/orders', 'title': 'Đơn hàng toàn hệ thống \| OneShop Admin', 'forms': [{'action': '/logout', 'method': 'post'}, {'action': '/admin/orders', 'method': 'get'}], 'buttons': 4} |
| admin /admin/orders/1 | PASS | {'url': '/admin/orders/1', 'title': 'Chi tiết đơn hàng \| OneShop Admin', 'forms': [{'action': '/logout', 'method': 'post'}], 'buttons': 3} |
| admin /admin/payments | PASS | {'url': '/admin/payments', 'title': 'Thanh toán trực tuyến \| OneShop Admin', 'forms': [{'action': '/logout', 'method': 'post'}, {'action': '/admin/payments', 'method': 'get'}], 'buttons': 4} |
| admin /admin/reviews | PASS | {'url': '/admin/reviews', 'title': 'Đánh giá \| OneShop Admin', 'forms': [{'action': '/logout', 'method': 'post'}, {'action': '/admin/reviews', 'method': 'get'}, {'action': '/admin/reviews/3/status', 'method': 'post'}, {'action': '/admin/reviews/2/status', 'method': 'post'}, {'action': '/admin/reviews/1/status', 'method': 'post'}], 'buttons': 7} |
| category modal open/cancel/invalid submit/reopen | PASS | cancel made no request; empty submit refused |
| brand modal open/cancel/invalid submit/reopen | PASS | cancel made no request; empty submit refused |
| admin user filter and reset | PASS | 10 |
| admin logout | PASS | authenticated cookie cleared |
| seed category image resource resolves | FAIL | Error: Seed category image failed to load: [{"url":"https://res.cloudinary.com/dfyfpuguj/image/upload/f_auto,q_auto,w_800/v1791803748/oneshop/seed/categories/trang-diem.webp","status":404,"type":"Image"},{"url":"https://res.cloudinary.com/dfyfpuguj/image/upload/f_auto,q_auto,w_800/v1791803748/oneshop/seed/categories/trang-diem.webp","status":404,"type":"Image"},{"url":"https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803748/oneshop/seed/categories/trang-diem.webp","status":404,"type":"Image"},{"url":"https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803748/oneshop/seed/categories/trang-diem.webp","status":404,"type":"Image"},{"url":"https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803748/oneshop/seed/categories/trang-diem.webp","status":404,"type":"Image"},{"url":"https://res.cloudinary.com/dfyfpuguj/image/upload/v1791803748/oneshop/seed/categories/trang-diem.webp","status":404,"type":"Image"}]     at file:///D:/One_Shop/scripts/qa-audit/screens.mjs:62:29     at check (file:///D:/One_Shop/scripts/qa-audit/screens.mjs:11:52)     at file:///D:/One_Shop/scripts/qa-audit/screens.mjs:60:9 |

Bảy business journeys COD/COD_SPLIT/PICKUP/ONLINE/FAILED/REFUND/REVIEW được chạy bằng Chrome và xác nhận SQL trong BrowserV3DatabaseIntegrationTest. Trace và screenshots: `evidence/browser/`. Screen render PASS không chứng minh mọi button trên screen đã được click.

## Reproduction

{
  "phase": "reproduce",
  "counts": {
    "FAIL": 14
  },
  "total": 14,
  "suites": 3
}

## Blocked / chưa có bằng chứng đầy đủ

- Cloudinary thật: BLOCKED_BY_SAFETY — chưa có tenant test được xác định; mock SDK/storage và fault injection đã chạy, không thay thế live verification.
- Mười fixture multi-assignment baseline: BLOCKED_BY_SPEC_CONFLICT — SQL hiện tại từ chối trước hành vi cần test.
- Semantic true/false condition coverage và mapping từng UI action: NOT_RECONCILED, không thể tính số chưa chạy chính xác từ line markers.
- Cắt kết nối SQL Server thật, HTTP connection loss sau commit, mọi phối hợp hai Admin sửa cùng một record: chưa có execution evidence đầy đủ. Không phá dịch vụ local dùng chung.
- Scheduler cadence/restart recovery thực tế: worker được giữ OFF; tests gọi engine trực tiếp.

## Build / final SQL check

`target/qa-audit/package.log`: BUILD SUCCESS với `-DskipTests` sau các test runs độc lập; không coi package thành công là full-test PASS. `target/qa-audit/coverage.log`: JaCoCo report success. Final SQL check trong `evidence/database-inspection-inspect.txt`: bảy invariant queries đều 0 violations; constraints được DBCC kiểm tra. Đây là trạng thái sau fixture cleanup, không xóa các bằng chứng lỗi trung gian.
