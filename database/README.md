# OneShop — SQL Server

Microsoft SQL Server, collation `Vietnamese_100_CI_AI`. Hibernate only **validates** (`ddl-auto=validate` in every profile): the schema is owned by the SQL in this folder, never by the application.

```text
database/
├── schema/01_schema.sql             Fresh install: creates the database and all 31 tables, constraints and indexes
├── seed/
│   ├── 01_reference_data.sql        EVERY environment: the 3 roles (idempotent)
│   ├── 02_bootstrap_admin.sql       PRODUCTION: the first administrator, from a BCrypt hash (idempotent)
│   ├── 20_demo_data.sql             DEMO ONLY: catalog, branches, demo accounts, stock, one promotion
│   └── 99_validate_demo_data.sql    Read-only checks of the demo data
├── migrations/                      Existing databases only: V001…V009 (re-runnable) and rollback/
└── test/test_fixture_seed.sql       Automated tests only: scenario fixture (6 branches, 8 sample orders)
```

## New database (nothing to migrate)

`scripts/db/Initialize-OneShopDatabase.ps1` (Windows) or `scripts/db/initialize-database.sh` (Linux/macOS) run the files in order, stop at the first error and read the SQL password from `SQLCMDPASSWORD` (never from the command line). `schema/01_schema.sql` refuses an existing database; it never drops or alters anything.

| Mode | Runs | Use for |
|---|---|---|
| `Production` | schema → reference data → first administrator | AWS production: no demo accounts, no demo orders |
| `Demo` | schema → reference data → demo data → validation | development, demos, UAT |
| `Test` | schema → test fixture | databases named `oneshop*_test` for `./mvnw test` |
| `Empty` | schema → reference data | when you create users yourself |

A password is never written into SQL. Produce the BCrypt hash with the application's own encoder: `scripts/db/New-PasswordHash.ps1` / `new-password-hash.sh` (needs `target/oneshop.jar`).

Demo accounts (`seed/20_demo_data.sql`) share the password whose hash you pass in; log in with the e-mail or user name: `admin@example.com` (admin), `staff.thuduc|govap|quan7|quan10|binhthanh@example.com` (one Store each; Bình Thạnh is suspended), `khachhang1…6@example.com`. The demo data refuses any database that already holds other users/stores, and loading it twice changes nothing. Orders, payments and reviews for the demo are created by the application services with the opt-in profile `demo-seed` (local Developer/Express SQL Server only).

## Existing database

`migrations/V001…V009` bring a V3-era database up to the current schema. Every script is re-runnable, non-destructive and refuses to run in a system database; V007 stops (changing nothing) if duplicate primary images exist. Run them in order, on a backed-up database, with `sqlcmd -b -f 65001 -d <db> -i <file>`.

| Script | Adds |
|---|---|
| V001 | one review per purchased OrderItem (DEC-26) |
| V002 | `payment_refunds` ledger (OPEN-10.1) |
| V003 | post-delivery returns (`order_returns*`) |
| V004 | promotions (`promotions`, `promotion_usages`, discount columns) |
| V005 | one ACTIVE Store per Staff (stops if a Staff has two) |
| V006 | `image_cleanup_tasks` (Cloudinary clean-up journal) |
| V007 | unique primary image per Product (stops on duplicates) |
| V008 | `password_reset_otps` (forgot-password codes) |
| V009 | payOS real payments: provider columns on `payments`, `payment_provider_events` (webhook de-duplication), real-refund evidence on `payment_refunds` (see `docs/PAYOS_INTEGRATION.md`) |

The application checks the critical ones at start-up (`V3SchemaCompatibilityVerifier`) and refuses to start rather than run on a schema that is too old. `migrations/rollback/` holds the approved downgrades (R001 reviews, R002 refund ledger, R009 payOS: refuses while PAYOS payments or real refunds exist); they need a verified backup and a separate approval. The one-off V2 → V3 conversion (Product/StoreProduct split, Fulfillments) is **not** in this folder; it exists only in git history, and a V2 database must first be taken to V3 with it.

## Verified equivalence (2026-10-25)

`schema/01_schema.sql` builds a database whose 480 catalog objects (columns, defaults, indexes, foreign keys, check constraints, collation) are identical to the operational `oneshop` database and to a database built by the previous 01_schema + V008 path; running V001…V008 on it changes nothing. See `docs/audit/PRE_AWS_DEPLOY_AUDIT.md`.
