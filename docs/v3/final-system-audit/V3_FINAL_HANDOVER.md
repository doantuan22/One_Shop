# V3 final handover

OneShop V3 is ready for the next UI/deployment phase with a verified fresh-install database path, controlled existing-database Review migration and a green full regression suite.

## Use

1. Create a new isolated database with `01_schema.sql`.
2. Load `02_seed.sql` only into that new database.
3. Run `03_verify.sql`.
4. Start Spring Boot with `spring.jpa.hibernate.ddl-auto=validate`.
5. For an existing operational database, do not run schema/seed; use a backup-approved migration process.

## Limits

- Payment and refund are server-side mock simulations, not banking integration.
- V2-to-V3 historical Order backfill remains outside the accepted scope because the repository has no V2 dataset.
- The retained fixture catalog has 40 products for present V3 regression compatibility; generated large seed and Cloudinary tooling are gone.
- `oneshop` was never deployed or migrated in this audit.
