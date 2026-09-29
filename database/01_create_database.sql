/* =============================================================================
   OneShop - Phase 2 - SQL Server schema
   File 01: tao database (bo qua neu da ton tai)
   Chay bang sqlcmd voi -f 65001 (UTF-8). Xem database/README.md.
   ========================================================================== */
SET NOCOUNT ON;
GO

IF DB_ID(N'oneshop') IS NULL
BEGIN
    -- Collation tieng Viet, khong phan biet hoa/thuong va dau -> tim kiem san pham thuan tien.
    CREATE DATABASE [oneshop] COLLATE Vietnamese_100_CI_AI;
    PRINT 'Database oneshop created.';
END
ELSE
    PRINT 'Database oneshop already exists - reused.';
GO
