# OneShop V3 database

`database/` contains exactly two scripts. Nothing in them drops a database or deletes existing data.

| File | Purpose |
| --- | --- |
| `01_schema.sql` | Creates a **new** database (name from `-v DatabaseName=...`) with all 23 V3 tables, keys, checks and indexes, including the DEC-26 Review filtered unique indexes and the OPEN-10.1 simulated refund ledger `payment_refunds`. It refuses to run if the database already exists, and verifies the structure (23 tables, required constraints and indexes, trusted constraints) before it finishes. |
| `02_seed.sql` | Loads the demo data into an **empty** schema: real cosmetics products (see below) plus simulated Stores, accounts, orders and stock. It refuses a non-empty database and verifies price, stock, order and payment arithmetic inside its own transaction (any violation rolls everything back). |

## What is real and what is simulated

| Real (traceable) | Simulated demo data |
| --- | --- |
| Product names, brands, sizes and shades; reference prices with the source URL and verification date of every price ([products.json](products.json)); pack images from Open Beauty Facts under CC BY-SA 3.0 ([IMAGE_CREDITS.md](IMAGE_CREDITS.md)). | Stock per Store (`store_products`), the 6 Stores, accounts, orders, Mock-gateway payments, simulated refunds, inventory movements, reviews and replies. Demo reviews are prefixed `[Dữ liệu mô phỏng]`; they are not customer reviews. The stock numbers are **not** any retailer's real stock. |

## Fresh install (the official database is `oneshop`)

```powershell
$db = 'oneshop'
sqlcmd -S localhost,1433 -E -C -b -I -f 65001 -v "DatabaseName=$db" -i database/01_schema.sql
sqlcmd -S localhost,1433 -E -C -b -I -f 65001 -v "DatabaseName=$db" -i database/02_seed.sql
```

For SQL authentication replace `-E` with `-U <user>` and pass the password through `SQLCMDPASSWORD`. `-f 65001` preserves UTF-8 (Vietnamese text). Default demo password of every seeded account: `OneShop@123`.

## Tests

Tests that write data run only on a database whose name matches `oneshop*_test` (a safety guard). Create one for the run with the two scripts, run `./mvnw test -Doneshop.test.db=<name>`, and **drop it afterwards by its exact name**. No test database is kept long term.

## Changing an existing database

Never run `01_schema.sql` or `02_seed.sql` against an existing database. Back up and restore-test first. The migrations that were applied to the existing database during V3 development are archived (with their rollbacks) in [docs/v3/migration-history/](../v3/migration-history/); a fresh install already contains their result. Resetting `oneshop` to the real-product seed destroys the demo orders, payments, reviews and accounts it currently holds and needs explicit approval and a verified backup.
