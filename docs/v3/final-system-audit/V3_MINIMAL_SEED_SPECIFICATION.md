> Historical phase evidence (up to 14/10/2026). Superseded script names and readiness claims describe that run only. Current setup: [database README](../../seed/DATABASE_SETUP.md); current acceptance: [deep audit](../final-deep-audit/DEEP_AUDIT_REPORT.md).

# V3 seed specification

`02_seed.sql` is a deterministic regression fixture, not a production catalog import. It contains the smallest linked dataset retained by the current V3 regression contracts: three roles, customer/staff/admin accounts, ACTIVE and INACTIVE assignments/stores, catalog/stock allocation conditions, checkout/payment states, Fulfillment history, inventory movements, Reviews, Replies and moderation data.

It uses repository-local product image URLs and performs no Cloudinary action. Seed execution is transactional and refuses a non-empty schema; a repeat requires another fresh database instead of deleting an existing database.

The fixture still contains the established 40-product catalog because active catalog/page/seed contracts in the remaining V3 suite depend on it. Large generated data, Cloudinary upload tools and large-seed tests were removed. Reducing the retained catalog further requires separately rebasing those catalog contracts; it is not hidden as a destructive data cleanup in this audit.
