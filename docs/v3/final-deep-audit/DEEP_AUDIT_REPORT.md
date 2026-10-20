# OneShop V3 deep audit

**Status: DONE at 19 October 2026 (ICT).** All planned isolated and production verification completed. The DEC-26/BE-06 review-uniqueness migration was applied once to `oneshop` only after a new checksum backup, successful `RESTORE VERIFYONLY`, an inactive-app preflight and a clean isolated regression.

## Scope and evidence

The audit read the V3 migration plan, BE-01 to BE-07 and final-audit history; main/test Java, JPA mappings, repositories, services, controllers, DTOs, security/JWT/configuration, templates/static assets, SQL, Maven graph and Markdown links. The initial tree had 599 tracked files. The initial user edits to `SiteMeshConfig`, `OrderTransitionPolicy`, and `templates/layouts/client.html` were retained unchanged.

Raw, ignored evidence is retained in `backup/final-deep-audit-20261018/` (including SQL metadata/integrity snapshots, migration logs and Maven logs). `target/` is deliberately disposable because `mvn clean` removes it. This report does not claim a line-by-line proof that no defect exists.

## Confirmed defects and remediation

| Area | Confirmed cause | Remediation | Verification available |
|---|---|---|---|
| Cart stock/provenance | A legacy `StoreProduct` could constrain a central V3 cart line to one store, and a supplied product could disagree with its store product. | `CartServiceImpl` validates the pair, sums chain availability with `long`, guards integer overflow, and treats legacy metadata as SKU provenance only. | `RepositoryHardeningDatabaseIntegrationTest`, targeted pass before final UI-only change. |
| Inventory adjustment | Staff could reduce stock below already reserved quantity. | `InventoryServiceImpl.adjustStock` refuses it under the locked-row update path. | Integration test in the hardening suite. |
| Product price | Admin form did not round-trip both prices; service validation could be bypassed outside MVC. | Price/original-price fields, bean validation and service-level decimal/range/cross-field checks were added. Zero-priced drafts remain possible but cannot enter cart or checkout. | Product MVC/service tests passed. |
| Error handling | Malformed JSON and unsupported media type fell through to HTTP 500; a Referer fallback could redirect outside the intended local route. | `GlobalExceptionHandler` returns generic 400/415 responses; `WebExceptionHandler` uses fixed local fallbacks. | 3 failure reproductions followed by `ApiInputValidationTest` and `WebExceptionHandlerSecurityTest` passing. |
| Staff date filter | Extreme valid-looking years could overflow `plusDays` or fail JDBC conversion as HTTP 500. | `StaffFulfillmentService.parseDate` accepts only the bounded application range and treats out-of-range input as absent. | Staff integration tests passed. |
| Cart browser behaviour | V2 shared JavaScript listened to `.os-cart-group-toggle` but V3 has no `data-store-id`; it reset the V3 select-all state after the inline handler ran. Cart delete confirmation was also attached twice. | Removed obsolete cart/checkout handlers from `static/js/main.js`; retained the V3 cart handler as the sole selection owner and the shared confirmation handler as the sole confirmation owner. | The post-fix browser regression passed all 7 flows; `node --check` passes. |
| Payment safety | Timeout worker was enabled by default and a true environment flag could enable mock payment in production. | Timeout worker is explicit opt-in; production hard-codes `ddl-auto=validate`; mock gateway rejects the `prod` profile even when its flag is true. | Scheduler/payment and mock-gateway tests passed. |
| Schema mismatch | Hibernate validates columns but not the DEC-26 filtered review uniqueness. `oneshop` initially had the obsolete global review constraint. | New `V3SchemaCompatibilityVerifier` performs a read-only startup prerequisite and refuses startup until the approved migration has created both filtered indexes. | Five verifier tests passed; the approved migration then created both required indexes on `oneshop`, and non-test startup passed. |
| Test database safety | A developer test could accidentally point at a non-test database or use mutable Hibernate DDL. | Global `TestDatabaseSafetyInitializer`, isolated `application-test.properties`, and defence in depth in `SeedGuardV3`. | Ten initializer tests passed. |

## Security and dependency review

The review found no hard-coded runtime credential in the inspected source/configuration locations; `.env` is untracked. JWT still requires an environment secret. Production retains `ddl-auto=validate`, online checkout defaults to `false`, and the payment timeout worker defaults to `false`.

Six direct dependency overrides were updated: Tomcat 10.1.60, Jackson BOM 2.21.7, Log4j 2.25.5, Commons Lang 3.18.0, HttpClient 5.6.3 and HttpCore 5.4.3. The final OSV snapshot still reports two Spring MVC advisories on the Boot 3.5.16 / Spring 6.2.19 line: [CVE-2026-47890](https://spring.io/security/cve-2026-47890/) and [CVE-2026-47884](https://spring.io/security/cve-2026-47884/). Source search found no SSE emitter, `XsltView`, XML view or related route in this application, so their stated prerequisites are not used by the current implementation. This is a reachability assessment, not a claim that the dependencies contain no remaining vulnerability. Addressing the vendor-fixed release requires a separately scoped Spring platform lifecycle decision.

## Architecture and residual risks

V3 checkout, allocation, payment, refund/cancellation, review authorization and staff fulfillment flows were traced through their controllers/services and existing integration tests. Legacy order-facing compatibility services remain because active callers and dashboard reads use them; their mutating V2 paths fail closed. The removed payment method had no caller and the removed smoke helpers were not JUnit tests and targeted a default operational database.

The audit observed potential scale work, not a current correctness failure: customer order views, admin payment summaries and dashboard per-store counts can make bounded but repeated repository queries. No production workload or execution-plan benchmark was available, so no speculative query rewrite was made.

## Database conclusion

`oneshop` matches the V3 canonical schema for 22 tables, 192 columns, 37 foreign keys, 44 checks and 51 defaults. All recorded integrity checks are zero and all FK/check constraints are trusted. DEC-26 review uniqueness now consists of enabled filtered unique indexes `UX_reviews_order_item` and `UX_reviews_legacy_user_product`; the obsolete `UQ_reviews_user_product` is absent. The migration preserved the review row count and `CHECKSUM_AGG(BINARY_CHECKSUM(*))` exactly: `3 / 340090263` before and after.

The application is configured to name `oneshop`, and the compatibility verifier now accepts its DEC-26 indexes. The post-migration non-test startup and safe public read checks passed.

## Database switch and verification

**Status: COMPLETE.** This section records the completed controlled production migration and its verification.

### Datasource and runtime guards

- `src/main/resources/application.properties` resolves `DB_NAME` to `oneshop` by default and takes host, credentials and TLS settings only from environment variables / ignored `.env`.
- `spring.jpa.hibernate.ddl-auto=validate` is fixed for runtime; `application-prod.properties` also fixes `validate` rather than accepting a mutable DDL setting.
- `oneshop.checkout.online-enabled=false` and `oneshop.payment.timeout-worker-enabled=false` remain the defaults.
- Tests resolve only `${oneshop.test.db:oneshop_test}`. `TestDatabaseSafetyInitializer` rejects non-`oneshop*_test` URLs and mutable Hibernate DDL before Spring creates beans.
- `V3SchemaCompatibilityVerifier` reads `sys.indexes` at non-test startup and refuses an incompatible DEC-26 schema before background work begins. It never changes schema or data.

### Pre-migration audit of `oneshop`

The 18 October 2026 snapshot compared SQL Server metadata from `oneshop` with a fresh canonical V3 database. The only difference was then resolved by the 19 October migration:

| Object class | Main | Canonical | Result |
|---|---:|---:|---|
| Tables | 22 | 22 | Equal |
| Columns | 192 | 192 | Equal |
| Foreign keys | 37 | 37 | Equal |
| CHECK constraints | 44 | 44 | Equal |
| Defaults | 51 | 51 | Equal |
| Indexes | 50 | 51 | Pre-migration DEC-26 mismatch only |

`oneshop` has the enabled unique constraint `UQ_reviews_user_product(user_id, product_id)`. Canonical V3 instead requires these enabled filtered unique indexes:

```sql
UX_reviews_order_item(order_item_id) WHERE order_item_id IS NOT NULL
UX_reviews_legacy_user_product(user_id, product_id) WHERE order_item_id IS NULL
```

The integrity snapshot reports zero invalid stock, source/provenance, totals, payment, review, collection and inventory-movement violations; zero untrusted FK/check constraints; and one historical ONLINE `PENDING_PAYMENT` order. The timeout worker remains off so switching configuration cannot silently mutate that historical record.

### Rehearsal and recovery assets

1. A checksum backup of `oneshop` was written outside Git at `backup/oneshop_deep_audit_20261018.bak`; `RESTORE VERIFYONLY` passed.
2. The backup was restored only to `oneshop_v3_deep_audit_copy_20261018_test`.
3. `database/migrations/20261014_be06_review_uniqueness.sql` was applied twice to that copy. It preserved counts and table checksums and was rerunnable.
4. `database/migrations/20261018_be06_review_uniqueness_rollback.sql` was added and applied twice to that copy. It preserved counts/checksums and restored the obsolete key only when no user/product duplicate exists. It never deletes, merges or relinks reviews.

The rollback is a controlled downgrade aid, not an ordinary operating procedure. The V3 startup guard correctly refuses the downgraded form. A final negative rehearsal restored a new isolated copy, applied DEC-26, committed a fourth review for a distinct order item of an already reviewed user/SKU, then attempted the downgrade. The rollback returned the expected `50623` refusal; review count/checksum, metadata and integrity reports were byte-for-byte unchanged before and after. No row was deleted, merged or relinked.

### Completed controlled change

On 19 October 2026 (ICT), no user SQL session was active on `oneshop` and no application listener was present on ports 8080 or 18080. A new `COPY_ONLY, CHECKSUM` backup was created at `backup/oneshop_pre_dec26_20261019.bak`; `RESTORE VERIFYONLY ... WITH CHECKSUM` passed. The audit captured pre-migration metadata, integrity results and the review checksum, then applied [20261014_be06_review_uniqueness.sql](../../../database/migrations/20261014_be06_review_uniqueness.sql) once against `oneshop`.

Post-migration metadata shows both required filtered indexes enabled and no obsolete constraint. Metadata changed only for this approved uniqueness definition; integrity output and review count/checksum are unchanged. `../../seed/DATABASE_SETUP.md` passed all 12 checks. The JAR then started with datasource `oneshop`, port 18080 and the timeout worker explicitly disabled; `GET /`, `GET /products` and `GET /stores` each returned HTTP 200. The temporary verification process was stopped and port 18080 was confirmed closed.

## Final test and handover

**Current conclusion: DONE.** No commit or deployment was performed. The approved DEC-26 migration was the only production mutation; it changed the review uniqueness indexes without changing review rows.

### Executed evidence

| Run | Environment | Result | Evidence |
|---|---|---|---|
| Initial full regression | Fresh isolated `oneshop_v3_deep_audit_20261018_test` | **1,558 passed, 0 failed, 0 errors, 0 skipped**; 80 suites; 4:27 | `target/final-deep-audit/baseline-maven.log` |
| Hardening targeted regression | Same isolated test database | **136 passed, 0 failed, 0 errors, 0 skipped**; 51.067 s | `hardening-targeted.log` |
| Final server-side targeted set | Same isolated test database | **66 passed, 0 failed, 0 errors, 0 skipped**; 23.442 s | `final-targeted.log` |
| HTTP defect reproduction | Same isolated test database | 3 defects reproduced: malformed JSON and unsupported media were 500; extreme staff date was 500 | `http-defect-reproduction.log` |
| HTTP remediation | Same isolated test database | API input and staff/controller paths passed in the final targeted set | `final-targeted.log` |
| Browser defect reproduction | Same isolated test database | **7/7 failed** at native `#select-all-items`, proving the central-cart selection conflict | `cart-browser-reproduction.log` |
| Static JavaScript check after browser fix | Workspace only | `node --check src/main/resources/static/js/main.js` passed; `git diff --check` passed | command output, 19 Oct |
| Final browser E2E regression | Fresh isolated `oneshop_v3_deep_audit_20261018_test` | **7/7 passed** across COD, split COD, pickup, online-disabled, failed payment, refund and review paths | `backup/final-deep-audit-20261018/cart-browser-final.log` |
| Fresh schema/seed verification | Fresh isolated `oneshop_v3_final_utf8_20261019_test` | Schema created; deterministic UTF-8 seed loaded; **12/12 passed** | `final-utf8-{schema,seed,verify,encoding-check}.log` |
| Final full Maven regression | Same fresh UTF-8 isolated database | **1,590 passed, 0 failed, 0 errors, 0 skipped**; `clean package` success; 5:19 | `final-full-maven-utf8.log` |
| Negative rollback safety | Restored isolated `oneshop_v3_rollback_negative_20261019_test` | Duplicate fixture committed; rollback rejected with expected **50623**; pre/post review snapshot, metadata and integrity output identical | `rollback-negative-*.log` |
| Production backup and migration | `oneshop` | New checksum COPY_ONLY backup and `VERIFYONLY` passed; DEC-26 migration completed once | `main-pre-dec26-backup*.log`, `main-dec26-migration.log` |
| Production post-migration verification | `oneshop` | Review count/checksum preserved; required indexes enabled; integrity unchanged; **03_verify 12/12 passed** | `main-{pre,post}-dec26-*.{log,json}` |
| Production startup/read check | JAR using `oneshop`, port 18080 | Startup accepted traffic; `GET /`, `/products`, `/stores` each **200**; process stopped | `main-startup.log`, `main-safe-read-endpoints.log` |

The initial browser failure was intentionally captured before the source fix. It is evidence of the defect, not a final E2E result.

### Completion conditions

All deferred work is complete: fresh database verification, final browser E2E, full Maven regression, negative rollback refusal rehearsal, approved production migration, post-migration integrity verification and non-test startup/read validation. The first full regression attempted with a database seeded through SQLCMD's default codepage failed because Vietnamese fixture strings were double-encoded; it was discarded as environment setup evidence. Recreating a new database with SQLCMD UTF-8 input/output (`-f i:65001,o:65001`) produced the passing 1,590-test final result above.

### Handover checks completed

- Original user edits were preserved: recursive SiteMesh decorator routes, `OrderTransitionPolicy` documentation and removal of a missing Bootstrap stylesheet reference.
- Obsolete default-database live smoke helpers and two temporary root notes were removed only after caller/reference review. No JUnit test was deleted to improve a result.
- Historical BE reports were retained with a historical-status banner; current operational instructions live in `README.md`, `../../seed/DATABASE_SETUP.md`, and this document.
- The working diff contains no whitespace error at the latest check.

## Repository cleanup matrix

Inventory baseline: 599 tracked files (277 main Java, 92 test Java, 54 templates and 44 Markdown). Every tracked file was placed in one of the path groups below; the detailed rows record every changed/deleted file and every group has a single disposition. “KEEP” means retained after dependency/reference review, not that it received a new functional test.

| Inventory path(s) | Decision | Reason |
|---|---|---|
| `.gitignore`, Maven wrapper files, `.mvn/**`, `pom.xml` | KEEP / REFACTOR | Build scaffolding retained; `pom.xml` has targeted security overrides only. |
| `.env.example` | REFACTOR | Documents `oneshop`, validation-only runtime DDL and disabled write flags; contains no secret. |
| `README.md` | REPLACE | Current V3 setup, roles, isolated-test and release-flag instructions replace broken V2/phase references. |
| `database/01_schema.sql`, `03_verify.sql`, `04_reset.sql`, `05_check_and_repair.sql` | KEEP | Official canonical schema and verification assets remain. |
| `database/02_seed.sql` | REFACTOR | Seed now refuses every non-empty user table, preventing partial-seed damage. |
| `database/migrations/20261014_be06_review_uniqueness.sql` | REFACTOR | Header and prerequisite wording point to current explicit migration workflow; data-preserving logic retained. |
| `database/migrations/20261018_be06_review_uniqueness_rollback.sql` | KEEP (new) | Controlled, transaction-protected rollback that refuses data loss. |
| `../../seed/DATABASE_SETUP.md` | REFACTOR | Current seed, upgrade, rollback and `oneshop` gate. |
| `src/main/java/**` except rows below | KEEP | Retained after caller/dependency review. Compatibility services remain because active routes/tests use them. |
| `src/main/java/com/oneshop/config/{PaymentProperties,PaymentSchedulingConfig,V3SchemaCompatibilityVerifier}.java` | REFACTOR / KEEP | Explicit scheduler opt-in and read-only DEC-26 startup guard. |
| `src/main/java/com/oneshop/controller/admin/AdminProductController.java` | REFACTOR | Round-trips price fields. |
| `src/main/java/com/oneshop/dto/request/ProductRequest.java` | REFACTOR | Money precision/range/cross-field validation. |
| `src/main/java/com/oneshop/exception/{GlobalExceptionHandler,WebExceptionHandler}.java` | REFACTOR | Safe local redirects and 400/415 parser errors. |
| `src/main/java/com/oneshop/service/{StaffOperationsService,OrderTransitionPolicy}.java` | REFACTOR / KEEP | One dead constant removed; user-owned documentation retained. |
| `src/main/java/com/oneshop/service/allocation/AllocationEngine.java` | REFACTOR | Dead imports removed. |
| `src/main/java/com/oneshop/service/fulfillment/StaffFulfillmentService.java` | REFACTOR | Date-bound hardening. |
| `src/main/java/com/oneshop/service/impl/{CartServiceImpl,CheckoutServiceImpl,InventoryServiceImpl,PaymentServiceImpl,ProductServiceImpl}.java` | REFACTOR | Confirmed business/integrity defects and dead private payment branch corrected/removed. |
| `src/main/java/com/oneshop/service/payment/{MockPaymentGateway,OrderCancellationService,PaymentTimeoutWorker}.java` | REFACTOR | Production flag defence and dead import cleanup. |
| `src/main/resources/application.properties`, `application-prod.properties` | REFACTOR | `oneshop` default, validate-only DDL and safe flags. |
| `src/main/resources/templates/**` except rows below | KEEP | Template/fragment and local-static reference review; no visual redesign. |
| `src/main/resources/templates/admin/{product-form,products}.html` | REFACTOR | Price entry/display is complete and validated. |
| `src/main/resources/templates/cart/index.html` | REFACTOR | Removes duplicate confirmation listener; V3 owns selection behaviour. |
| `src/main/resources/templates/layouts/client.html` | KEEP (user edit) | User removed a missing local Bootstrap reference; preserved exactly. |
| `src/main/resources/static/**` except `js/main.js` | KEEP | Static assets retained after reference scan. |
| `src/main/resources/static/js/main.js` | REFACTOR | Removes obsolete V2 cart/checkout listeners which conflict with V3 markup. |
| `src/test/java/**` except rows below | KEEP | Test coverage retained; no test suppressed to obtain success. |
| `src/test/java/com/oneshop/{ApiInputValidationTest,RepositoryHardeningDatabaseIntegrationTest,TestDatabaseSafetyInitializer,TestDatabaseSafetyInitializerTest,WebExceptionHandlerSecurityTest}.java` | KEEP (new) | Regression coverage for confirmed defects and database safety. |
| `src/test/java/com/oneshop/config/V3SchemaCompatibilityVerifierTest.java` | KEEP (new) | Verifies missing, obsolete, malformed and disabled DEC-26 keys fail closed. |
| `src/test/java/com/oneshop/{SeedGuardV3,service/CartServiceRetryTest,service/CodPaymentServiceTest,service/MockPaymentGatewayTest,service/PayAtStorePaymentServiceTest,service/PaymentServiceTest}.java` | REFACTOR | Fixtures/constructors updated to match retained production behaviour. |
| `src/test/resources/application-test.properties`, `src/test/resources/META-INF/spring.factories` | REFACTOR / KEEP | Enforces isolated test database bootstrap. |
| `src/test/browser/be07-e2e.mjs` | REFACTOR | Existing seven workflows now assert select-all/select-none behaviour. |
| `src/test/java/com/oneshop/{DeliveryLiveSmoke,PaymentLiveSmoke,Phase9IntegrationLiveSmoke,PickupLiveSmoke,SeedGuard}.java` | DELETE | Non-JUnit, obsolete live helpers that could use the default operational database; callers were only the deleted helpers/docs. |
| `DEXUAT.md`, `note.md` | DELETE | Temporary V2 proposal/notes contradicted current DEC-26/V3 behaviour and were superseded by retained evidence. |
| `docs/v3/backend-audit/**`, `backend-catalog-cart/**`, `backend-checkout-allocation/**`, `backend-fulfillment/**`, `backend-payments/**`, `backend-regression/**`, `backend-reviews/**`, `final-system-audit/**` | REFACTOR / KEEP | Historical BE/final evidence retained in place and labelled as historical; it is not current operating instruction. |
| `docs/ONESHOP_V3_DATABASE_MIGRATION_AND_SEED_REPORT.md` and other root `docs/**` history | REFACTOR / KEEP | Retained as migration provenance; obsolete file links are replaced by current paths or explicitly marked retired. |
| `docs/v3/final-deep-audit/DEEP_AUDIT_REPORT.md` | KEEP (new) | Single current handover document. |
| Images, browser evidence and other tracked binary assets | KEEP | Retained as provenance; visual redesign was out of scope. |

The machine-readable initial tracked-file inventory is retained as ignored evidence at `target/final-deep-audit/tracked-files.txt`. No ignored backup, database file, cache or user-created untracked material was deleted.

