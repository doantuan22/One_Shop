> Historical phase evidence (up to 14/10/2026). Superseded script names and readiness claims describe that run only. Current setup: [database README](../../seed/DATABASE_SETUP.md); current acceptance: [deep audit](../final-deep-audit/DEEP_AUDIT_REPORT.md).

# Legacy test removal matrix

| Removed test/tooling group | Count | V3 replacement / retained coverage |
| --- | ---: | --- |
| `LargeSeedGeneratorTest` and generator | 16 | `03_verify.sql`, `DatabaseV3RegressionIntegrationTest`, checkout/allocation/inventory integration tests |
| `LargeSeedWebIntegrationTest` | 3 | Catalog HTTP/web tests, pagination tests and browser E2E |
| `RebuildScriptGuardTest` | 5 | Canonical schema refuses existing databases; seed refuses non-empty schema; SQL Server fresh-install verification |
| `SeedCategoryImagesIntegrityTest` and signature parity | 3 | `WebRoutesIntegrationTest`, catalog HTTP/web tests and repository-local asset loading |
| Cloudinary/seed image helper | tool only | No Cloudinary provisioning is in scope |

Total removed test cases: **27**. Baseline 1.585 cases therefore becomes final 1.558 cases. No runtime V3 test was removed solely to obtain a green Maven result.
