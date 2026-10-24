# Coverage report

## Đo bằng công cụ

| Counter | Covered | Total | Percent |
|---|---|---|---|
| INSTRUCTION | 29767 | 33183 | 89.71 |
| BRANCH | 2329 | 3128 | 74.46 |
| LINE | 5642 | 6206 | 90.91 |
| COMPLEXITY | 2505 | 3353 | 74.71 |
| METHOD | 1621 | 1775 | 91.32 |
| CLASS | 286 | 290 | 98.62 |

JaCoCo 0.8.12, source production không đổi, execution file riêng phiên audit `target/qa-audit/session-20261024.exec`. Báo cáo máy đọc `evidence/jacoco.xml`, `evidence/jacoco.csv`; HTML ở `target/site/jacoco/index.html`. Bao gồm unit, mock-backed HTTP và real-SQL tests; không được gọi đây là E2E coverage.

## Coverage nghiệp vụ

| Metric | Actual | Meaning / limitation |
|---|---|---|
| Controller route declarations | 115 | structural scan; constants resolved |
| Runtime mapping records | 118 | includes two Boot /error handlers + Security logout |
| Unique HTTP verb/path pairs | 117 | ANY represented by one GET probe; all verbs not exhausted |
| Guard probe requests | 307 | one or more negative auth/input probes per route; not happy path |
| Primary JUnit invocations | 2225 | parameterized/repeated invocations, not semantic scenarios |
| UI screen checkpoints | 62 | do not add again to JUnit totals |
| Source decision candidates | 1430 | heuristic lines, not a semantic branch denominator |
| Source UI candidates | 1113 | line-level links/forms/events/conditions; not unique user actions |
| Semantic function / flow / condition coverage | NOT MEASURED | full de-duplication, manual branch mapping not completed |
| Unexecuted semantic test cases | UNKNOWN | cannot subtract source line candidates from JUnit counts |

## Uncovered / partially covered code

Every source line with missed instructions or branches is listed in `evidence/jacoco-uncovered-lines.json`. Concrete source candidates and 34 enum/state declarations are in `inventory.json`. Remaining work includes all UI events, every condition in validation chains, every persistence failure point, stale record updates across all CRUD modules, and transport-failure retry.

No percentage is supplied for function/flow coverage without a valid denominator. High line coverage cannot negate the confirmed failures.
