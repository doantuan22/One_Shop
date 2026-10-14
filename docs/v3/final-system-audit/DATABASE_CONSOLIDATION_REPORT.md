# Database consolidation

## Canonical layout

| File | Role |
| --- | --- |
| `database/01_schema.sql` | Fresh database, 22 tables, PK/FK/CHECK/index và DEC-26 Review filtered uniqueness. Refuses an existing database. |
| `database/02_seed.sql` | Deterministic linked V3 regression fixture. Refuses a non-empty schema and does not delete data. |
| `database/03_verify.sql` | Read-only 12-group verification. |
| `database/migrations/20261014_be06_review_uniqueness.sql` | Only retained existing-database upgrade; transactional and rerunnable. |

Fresh validation used `oneshop_v3_audit_test` and `oneshop_v3_audit_cleanup_test`. Both were new databases; schema, seed and verify completed successfully. Hibernate `ddl-auto=validate` and `DatabaseFoundationIntegrationTest` passed on the first audit database.

## Removed SQL

Removed: `00_run_all.sql`, split create/table/index/seed/verify scripts `01_create_database.sql` through `05_verify.sql`, destructive `rebuild_dev_db.ps1`, and `database/seed_large/` assets. Their functions are consolidated in the three canonical scripts. No migration for existing data was removed.

For an existing database, fresh schema/seed are prohibited. Back up and restore-test, then run only the controlled BE-06 migration if it is applicable.
