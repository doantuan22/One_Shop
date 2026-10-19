> Historical phase evidence (up to 14/10/2026). Superseded script names and readiness claims describe that run only. Current setup: [database README](../../../database/README.md); current acceptance: [deep audit](../final-deep-audit/DEEP_AUDIT_REPORT.md).

# V3-BE-07 — Kết quả full regression

## Lệnh và kết quả

```text
mvn -o -B test
Tests run: 1585, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS — 05:06 min — 14-10-2026 11:36 ICT
```

85 XML Surefire được lưu trong `target/be07-evidence/final-reports`. Baseline và final được so bằng key `classname.method`: 0 regression, 417 cải thiện, 0 removed, 10 new.

## Trace quyết định payment A–U

| Decision | Bằng chứng PASS |
| --- | --- |
| A | `PaymentSettlementDatabaseIntegrationTest.be05_04`, `be05_18` |
| B | `PaymentSettlementDatabaseIntegrationTest.be05_01`, `be05_17` |
| C | `PaymentSettlementDatabaseIntegrationTest.be05_15` |
| D | `PaymentSettlementDatabaseIntegrationTest.be05_16`, `be05_20` |
| E | `PaymentSettlementDatabaseIntegrationTest.be05_02`, `be05_11` |
| F | `PaymentSettlementDatabaseIntegrationTest.be05_01`, browser `ONLINE` |
| G | `PaymentSettlementDatabaseIntegrationTest.be05_04`, browser `FAILED` |
| H | `PaymentSettlementDatabaseIntegrationTest.be05_09` |
| I | `PaymentSettlementDatabaseIntegrationTest.be05_15` |
| J | `PaymentSettlementDatabaseIntegrationTest.be05_17`, `be05_19` |
| K | `PaymentSettlementDatabaseIntegrationTest.be05_18` |
| L | `PaymentSettlementDatabaseIntegrationTest.be05_16` |
| M | `PaymentSettlementDatabaseIntegrationTest.be05_20`, `be05_35` |
| N | `PaymentSettlementDatabaseIntegrationTest.be05_21`, `be05_34` |
| O | `PaymentSettlementDatabaseIntegrationTest.be05_23` |
| P | `PaymentSettlementDatabaseIntegrationTest.be05_22` |
| Q | `PaymentSettlementDatabaseIntegrationTest.be05_02`, `be05_29` |
| R | `CheckoutV3ConcurrencyDatabaseIntegrationTest`, `FulfillmentConcurrencyDatabaseIntegrationTest`, `PaymentConcurrencyDatabaseIntegrationTest` |
| S | `PaymentHttpDatabaseIntegrationTest`, `PaymentGatewayDisabledDatabaseIntegrationTest` |
| T | `PaymentSettlementDatabaseIntegrationTest.be05_29`…`be05_33` |
| U | `PaymentSettlementDatabaseIntegrationTest.be05_10`, `Phase12HistoryHardeningDatabaseIntegrationTest` |

## Trace V3-T01…T49

Tất cả hàng có nhãn PASS là test đã chạy trong full suite; chi tiết method identity ở XML Surefire.

| TC | Chứng cứ PASS |
| --- | --- |
| T01 | `CatalogDatabaseIntegrationTest`, `CentralCartIntegrationTest` |
| T02 | `CartDatabaseIntegrationTest`, `CartHttpDatabaseIntegrationTest` |
| T03 | `CheckoutV3DatabaseIntegrationTest`, `AllocationEngineTest` |
| T04 | `CheckoutV3DatabaseIntegrationTest` |
| T05 | `CheckoutV3SplitAndFeeDatabaseIntegrationTest` |
| T06 | `CheckoutV3SplitAndFeeDatabaseIntegrationTest`, `CheckoutV3ConcurrencyDatabaseIntegrationTest` |
| T07 | `AllocationEngineTest`, `CheckoutV3PolicyGateDatabaseIntegrationTest` |
| T08 | `CheckoutV3DatabaseIntegrationTest` rollback cases |
| T09 | `CheckoutV3ConcurrencyDatabaseIntegrationTest` |
| T10 | `CheckoutV3HttpDatabaseIntegrationTest`, browser `ONLINE` |
| T11 | browser `COD`, `CheckoutV3PolicyGateDatabaseIntegrationTest` |
| T12 | `CheckoutV3PolicyGateDatabaseIntegrationTest` |
| T13 | browser `COD_SPLIT`, `CheckoutV3PolicyGateDatabaseIntegrationTest` |
| T14 | browser `PICKUP`, `PickupDatabaseIntegrationTest` |
| T15 | `PickupDatabaseIntegrationTest` |
| T16 | `StaffStoreScopeDatabaseIntegrationTest`, `FulfillmentHttpDatabaseIntegrationTest` |
| T17 | `FulfillmentOperationsDatabaseIntegrationTest` |
| T18 | `FulfillmentOperationsDatabaseIntegrationTest`, browser `REVIEW` |
| T19 | `FulfillmentOperationsDatabaseIntegrationTest` |
| T20 | `PaymentSettlementDatabaseIntegrationTest.be05_26` |
| T21 | `PaymentSettlementDatabaseIntegrationTest.be05_28` |
| T22 | `PaymentSettlementDatabaseIntegrationTest.be05_27`, browser `REFUND` |
| T23 | `PaymentSettlementDatabaseIntegrationTest.be05_29`…`be05_32` |
| T24 | `Phase12HistoryHardeningDatabaseIntegrationTest` |
| T25 | `ReviewV3DatabaseIntegrationTest`, browser `REVIEW` |
| T26 | `ReviewV3HttpDatabaseIntegrationTest`, `StaffStoreScopeDatabaseIntegrationTest` |
| T27 | `ReviewV3ConcurrencyDatabaseIntegrationTest`, BE-06 migration test |
| T28 | `CheckoutV3PolicyGateDatabaseIntegrationTest` |
| T29 | `Phase11AdminDatabaseIntegrationTest`, `StaffStoreScopeDatabaseIntegrationTest` |
| T30 | Excluded by owner: no V2 backup/dataset. Current-V3 history/integrity only: `DatabaseV3RegressionIntegrationTest` + `05_verify.sql` PASS |
| T31 | `PaymentSettlementDatabaseIntegrationTest.be05_01`…`be05_03`, browser `ONLINE` |
| T32 | `PaymentSettlementDatabaseIntegrationTest.be05_09`…`be05_11` |
| T33 | `PaymentSettlementDatabaseIntegrationTest.be05_04`, browser `FAILED` |
| T34 | `PaymentSettlementDatabaseIntegrationTest.be05_26` |
| T35 | `PaymentSettlementDatabaseIntegrationTest.be05_13`, `be05_23` |
| T36 | `PaymentConcurrencyDatabaseIntegrationTest`, `be05_02`, `be05_29` |
| T37 | `PaymentConcurrencyDatabaseIntegrationTest`, `FulfillmentConcurrencyDatabaseIntegrationTest` |
| T38 | `PaymentSettlementDatabaseIntegrationTest.be05_10`, `be05_28` |
| T39 | `PaymentSettlementDatabaseIntegrationTest.be05_15`, `be05_16` |
| T40 | `PaymentSettlementDatabaseIntegrationTest.be05_17`, `be05_19` |
| T41 | `PaymentSettlementDatabaseIntegrationTest.be05_18` |
| T42 | `PaymentSettlementDatabaseIntegrationTest.be05_16` |
| T43 | `PaymentSettlementDatabaseIntegrationTest.be05_04`, `be05_25` |
| T44 | `PaymentSettlementDatabaseIntegrationTest.be05_20`, `be05_35` |
| T45 | `PaymentSettlementDatabaseIntegrationTest.be05_21`, `be05_34` |
| T46 | `PaymentSettlementDatabaseIntegrationTest.be05_22`, `be05_23` |
| T47 | `PaymentHttpDatabaseIntegrationTest`, `PaymentGatewayDisabledDatabaseIntegrationTest` |
| T48 | `CheckoutV3SplitAndFeeDatabaseIntegrationTest`, `DatabaseV3RegressionIntegrationTest` |
| T49 | `PaymentSettlementDatabaseIntegrationTest.be05_24`, `PaymentConcurrencyDatabaseIntegrationTest` |

Full case-level evidence nằm trong XML Surefire và JSON đối chiếu đã nêu.
