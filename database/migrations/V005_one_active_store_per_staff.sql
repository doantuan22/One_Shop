/* ============================================================================
   Migration: a Staff works at exactly one Store.
   Adds a filtered unique index: at most one ACTIVE assignment per user.
   INACTIVE rows (suspended work) stay unrestricted.

   Run once on an existing database. It refuses to run while any Staff still has
   two or more ACTIVE assignments; fix them first in the admin console with
   "Chuyen chi nhanh" (or "Tam dinh chi") and run this script again.
   ========================================================================== */
IF EXISTS (SELECT 1 FROM dbo.staff_store_assignments
           WHERE status = N'ACTIVE' GROUP BY user_id HAVING COUNT(*) > 1)
BEGIN
    SELECT u.user_id, u.email, COUNT(*) AS active_stores
    FROM dbo.staff_store_assignments a JOIN dbo.users u ON u.user_id = a.user_id
    WHERE a.status = N'ACTIVE' GROUP BY u.user_id, u.email HAVING COUNT(*) > 1;
    THROW 50021, 'Some Staff work at more than one Store. Resolve them before adding the index.', 1;
END
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UX_ssa_one_active_store_per_user'
               AND object_id = OBJECT_ID(N'dbo.staff_store_assignments'))
    CREATE UNIQUE INDEX UX_ssa_one_active_store_per_user
        ON dbo.staff_store_assignments (user_id) WHERE status = N'ACTIVE';
GO
