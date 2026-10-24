# OneShop V3 database

`database/` contains the canonical fresh-install path for OneShop V3. It has no drop/reseed utility and never upgrades an existing database implicitly.

| File | Purpose |
| --- | --- |
| `01_schema.sql` | Creates a new, empty database with all 23 V3 tables (22 + `payment_refunds`, the OPEN-10.1 simulated refund ledger), keys, checks and indexes. It refuses an existing database. DEC-26 Review filtered unique indexes are included. |
| `02_seed.sql` | Loads the deterministic V3 fixture only when the schema has no data. It never deletes existing rows. |
| `03_verify.sql` | Read-only validation of schema, seed and integrity. |
| `migrations/20261014_be06_review_uniqueness.sql` | Controlled, data-preserving upgrade for an existing database that still has the old Review uniqueness key. |
| `migrations/20261020_open101_payment_refunds.sql` | OPEN-10.1: creates the simulated refund ledger `payment_refunds` on an existing database and backfills one `WHOLE_ORDER` row per refund already recorded on `payments.refund_*`. Idempotent; changes no existing row. The application refuses to start until it is applied. |
| `migrations/20261020_open101_payment_refunds_rollback.sql` | Optional approved downgrade; drops the table only when it holds nothing but the backfilled `WHOLE_ORDER` rows (partial refunds exist only here, so it refuses otherwise). |
| `migrations/20261018_be06_review_uniqueness_rollback.sql` | Optional approved downgrade; refuses when per-item Reviews cannot fit the old global key. Never deletes or merges data. |

## Fresh install

Choose a new database name. The commands below create **only** that database; `01_schema.sql` fails if it already exists.

```powershell
$db = 'oneshop_v3_local_test'
sqlcmd -S localhost,1433 -E -C -b -I -f 65001 -v "DatabaseName=$db" -i database/01_schema.sql
sqlcmd -S localhost,1433 -E -C -b -I -f 65001 -v "DatabaseName=$db" -i database/02_seed.sql
sqlcmd -S localhost,1433 -E -C -b -I -f 65001 -v "DatabaseName=$db" -i database/03_verify.sql
```

For SQL authentication, replace `-E` with `-U <user>` and provide the password through `SQLCMDPASSWORD` or the platform secret mechanism. `-f 65001` preserves UTF-8 content.

The seed supplies the V3 regression fixture: CUSTOMER/STAFF/ADMIN accounts, active and inactive Store assignments, catalog and stock allocation cases, Order/Fulfillment/Payment history, Reviews and Staff Replies. Product image rows use repository-local URLs; no Cloudinary upload or external image workflow runs as part of the SQL installation.

## Existing database upgrade

Do **not** run `01_schema.sql` or `02_seed.sql` against an existing database. Back up and restore-test first. The upgrade script is `migrations/20261014_be06_review_uniqueness.sql`; it checks for duplicates, changes no rows and replaces the obsolete global Review `(user_id, product_id)` uniqueness with the approved filtered keys. `02_seed.sql` checks all user tables and refuses any existing data, including when `roles` is empty. Run `03_verify.sql` and application validation after the controlled upgrade. Verification includes deterministic fixture assertions; an existing non-fixture database also needs the metadata/data checks in the audit runbook.

As verified on 18/10/2026, local `oneshop` has not yet received DEC-26. Applying it requires separate approval. [Deep audit evidence, database switch, backup and rollback](../docs/v3/final-deep-audit/DEEP_AUDIT_REPORT.md) records the tested copy and exact prerequisite. Both runtime profiles validate columns, and `V3SchemaCompatibilityVerifier` also checks the filtered unique keys before scheduling starts. The payment timeout worker defaults to off; review historical ONLINE orders before opting in.

The rollback script is not a normal V3 upgrade step. Stop the application, obtain separate approval and retain a verified backup. It only reinstates the old global uniqueness if every Review can fit it, otherwise it aborts unchanged. V3 startup stays blocked after a downgrade until the forward migration is reapplied. Backup recovery after new operational writes needs an explicit recovery plan; never overwrite the operational database merely to force a downgrade.

The old split create/index/seed scripts, destructive rebuild helper, large-seed generator and Cloudinary seed tooling were removed after the canonical SQL path passed fresh database verification and full regression.
