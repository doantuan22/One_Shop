# Repository cleanup report

Removed non-runtime, superseded artefacts:

- Split and destructive SQL bootstrap/rebuild files; replaced by the fresh-install SQL trio.
- Large seed generator, large seed images/manifests, Cloudinary cleanup/upload helper and their tests.
- Seed-image and rebuild-script guard tests that only covered deleted tooling.

Retained:

- `migrations/20261014_be06_review_uniqueness.sql`, because it is the only controlled upgrade path for an existing database.
- Historic BE-01…BE-07 reports, even where they mention superseded paths; they are project history, not current runbooks.
- V3 Cart/Order compatibility fields that still have valid runtime callers. They are documented as compatibility data only and do not choose checkout price or allocation.

No Git reset, clean, commit, operational database mutation, production ONLINE enablement or Cloudinary operation was performed.
