/* =============================================================================
   OneShop - REFERENCE data required in EVERY environment (development, test, production).

   The three application roles. The application and the CHECK constraint on dbo.roles know exactly these names.
   Idempotent: safe to run again; it never changes an existing role row.

   Usage: sqlcmd -f 65001 -b -d <db> -i database/seed/01_reference_data.sql
   ============================================================================= */
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON; SET ANSI_PADDING ON; SET ANSI_WARNINGS ON; SET ARITHABORT ON; SET CONCAT_NULL_YIELDS_NULL ON;  -- required by the filtered indexes on dbo.users

IF NOT EXISTS (SELECT 1 FROM dbo.roles WHERE name = N'CUSTOMER') INSERT INTO dbo.roles (name, description) VALUES (N'CUSTOMER', N'Khách hàng mua sắm');
IF NOT EXISTS (SELECT 1 FROM dbo.roles WHERE name = N'STAFF')    INSERT INTO dbo.roles (name, description) VALUES (N'STAFF',    N'Nhân viên chi nhánh cửa hàng');
IF NOT EXISTS (SELECT 1 FROM dbo.roles WHERE name = N'ADMIN')    INSERT INTO dbo.roles (name, description) VALUES (N'ADMIN',    N'Quản trị viên toàn chuỗi');

IF (SELECT COUNT(*) FROM dbo.roles) <> 3 THROW 51301, 'dbo.roles must hold exactly CUSTOMER, STAFF and ADMIN.', 1;
PRINT 'Reference data ready: roles CUSTOMER, STAFF, ADMIN.';
