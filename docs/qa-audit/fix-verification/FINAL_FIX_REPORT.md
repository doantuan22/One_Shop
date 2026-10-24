# Final fix report — OneShop V3

## Outcome

Implementation and focused regression evidence exist for all eight QA findings. Final full suite is **not clean**: Maven/Surefire reports 8 failures and 10 errors among 2,517 invocations. The generated JSON says 430/430 PASS because aggregation missed dynamic/parameterized outcomes; Maven log `target/qa-fix/full-suite-final.log` is authoritative. Details: `FULL_REGRESSION_REPORT.md`.

## Status by bug

| Bug | Status | Evidence |
|---|---|---|
| QA-001 | FIXED — focused regression verified | `evidence/qa001/results.json` (12/12); concurrency suite in `evidence/qa002/results.json` (39/39). |
| QA-002 | FIXED — SQL Server regression verified | `evidence/qa002/results.json` (39/39), filtered unique index/mutation locking. |
| QA-003 | FIXED — SQL Server + storage double verified | `evidence/qa003-004-final/results.json` (42/42); not live Cloudinary. |
| QA-004 | FIXED — SQL Server + storage double verified | `evidence/qa003-004-final/results.json` (42/42); not live Cloudinary. |
| QA-005 | FIXED — HTTP and SQL Server verified | `evidence/qa005-rerun/results.json` (667/667). |
| QA-006 | FIXED — HTTP and SQL Server verified | `evidence/date-final2/results.json` (424/424). |
| QA-007 | FIXED — Chrome browser verified | `evidence/qa007-008-rerun/results.json` (2/2). |
| QA-008 | FIXED — local resource + Chrome verified | `evidence/qa007-008-rerun/results.json` (2/2). |

## Caveats and dispositions

1. The earlier stale escaped-title assertion was corrected. Remaining assertion failures concern old synchronous image-delete/mock expectations, legacy finder assertions, and literal text/status expectations.
2. Ten multi-active-assignment test errors conflict with the enforced one-active-store constraint. Preserve SC-001; do not relax authorization/data constraints to satisfy those fixtures.
3. Cloudinary live behavior and remote cleanup remain unverified; tests use a storage double. A process crash after upload but before recording transaction compensation also remains a documented external reconciliation gap.
4. No production database/data was changed. All source/test/docs changes remain uncommitted.

See `BUG_FIX_SUMMARY.md`, `BUG_REGRESSION_RESULTS.md`, and `DATABASE_VERIFICATION.md` for implementation and evidence details.
