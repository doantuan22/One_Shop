# Fix plan / baseline

Original audit evidence remains immutable. Verification uses the newly provisioned `oneshop_fix_20261024_test`, never the operational databases. Source/schema/runtime remain V3. Baseline on the new database: 430 invocations, 363 PASS, 67 FAIL; the original eight defects reproduce (including the screen resource assertion).

## Root cause remediation and file scope

1. QA-001: `AccountController` delegates a validated DTO to `AccountProfileService`; `UserRepository` executes an atomic ACTIVE-only two-column profile patch. No detached User merge. JWT/status/role/assignment code remains unchanged. Adapt the original race hook to the new SQL write boundary, retain the assertions and independent Admin transaction.
2. QA-003/004: replace every Product/Brand/Category external asset write/delete pattern in `ProductServiceImpl` with a shared transaction coordinator. New files are compensated after rollback, old files deleted only after commit. A compact SQL cleanup-task journal persists failed deletion intents for explicit Admin retry; reference checks protect shared assets. No new queue, worker or microservice. Document remaining crash gap between an external upload and registering a rollback callback (provider reconciliation is still required for that gap).
3. QA-002: serialize image mutations under the Product row lock, flush old primary flags before promoting another, add a filtered unique index via fail-closed migration (report duplicates, never pick/delete actual images automatically). Add lock protocol to add/delete as well as select.
4. QA-005: one shared paging policy validates long offset and bounded size before query/search branches; all pagination call sites and Pageable service entry points reviewed. Invalid input becomes controlled HTTP 400, not a caught Hibernate exception.
5. QA-006: shared SQL datetime2 day-range validator; inclusive maximum end date represented by no upper bound after validating supported years. Reject inverted ranges. Orders/Payments use identical semantics; review Staff date readers for same pattern.
6. QA-008: replace the broken canonical seed reference with an explicitly local placeholder, remove Cloudinary public_id for the local file, add image error fallback without layout/theme changes. Provision another fresh fixture from the corrected canonical seed for browser verification; no bulk updates to operational data.
7. QA-007: decode HTML entities exactly once at the SiteMesh title bridge, then keep escaped `th:text` output. Add title injection and real-browser checks.

## Test maintenance, not production defects

Keep one-active-Store assignment constraint and legacy Store select UI. Existing tests tied to removed merge/deletion timing must test the new persistence/commit boundary without dropping their business assertions. Drifted heading/validation text and illegal multi-assignment fixtures are tracked separately; no disabled tests or weakened expectations.
