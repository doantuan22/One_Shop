# Final full regression results

| Stage | Database | Result |
| --- | --- | --- |
| Baseline before audit | `oneshop_test` | 1.585 PASS, 0 failure, 0 error, 0 skipped |
| Fresh canonical SQL before cleanup | `oneshop_v3_audit_test` | 1.585 PASS, 0 failure, 0 error, 0 skipped |
| Fresh canonical SQL after cleanup | `oneshop_v3_audit_cleanup_test` | **1.558 PASS, 0 failure, 0 error, 0 skipped** |

Evidence is retained under `target/final-system-audit/`: baseline and final Maven logs; schema/seed/verify logs for both fresh databases. The final test count differs only by the 27 removed tooling tests documented in `LEGACY_TEST_REMOVAL_MATRIX.md`.
