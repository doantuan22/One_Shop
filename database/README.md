# OneShop V3 database

`database/` contains the canonical fresh-install path for OneShop V3. It has no drop/reseed utility and never upgrades an existing database implicitly.

| File | Purpose |
| --- | --- |
| `01_schema.sql` | Creates a new, empty database with all 22 V3 tables, keys, checks and indexes. It refuses an existing database. DEC-26 Review filtered unique indexes are included. |
| `02_seed.sql` | Loads the deterministic V3 fixture only when the schema has no data. It never deletes existing rows. |
| `03_verify.sql` | Read-only validation of schema, seed and integrity. |
| `migrations/20261014_be06_review_uniqueness.sql` | Controlled, data-preserving upgrade for an existing database that still has the old Review uniqueness key. |

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

Do **not** run `01_schema.sql` or `02_seed.sql` against an existing database. Back up and restore-test first. The only retained upgrade script is `migrations/20261014_be06_review_uniqueness.sql`; it checks for duplicates, changes no rows and replaces the obsolete global Review `(user_id, product_id)` uniqueness with the approved filtered keys. Run `03_verify.sql` and application validation after the controlled upgrade.

The old split create/index/seed scripts, destructive rebuild helper, large-seed generator and Cloudinary seed tooling were removed after the canonical SQL path passed fresh database verification and full regression.
