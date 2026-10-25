/* =============================================================================
   OneShop - FIRST ADMINISTRATOR for a production database (no demo accounts, no demo data).

   The administrator then creates Stores, Staff and the catalog from the /admin screens.
   The password is NEVER passed in clear text: supply the BCrypt hash produced by the application's own encoder
   (scripts/db/New-PasswordHash.ps1 or scripts/db/new-password-hash.sh).

   Usage: sqlcmd -f 65001 -b -d <db> -i database/seed/02_bootstrap_admin.sql ^
            (values may also be supplied as environment variables of the same names)
   Idempotent: does nothing if an ADMIN account already exists. Run database/seed/01_reference_data.sql first.
   ============================================================================= */
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON; SET ANSI_PADDING ON; SET ANSI_WARNINGS ON; SET ARITHABORT ON; SET CONCAT_NULL_YIELDS_NULL ON;  -- required by the filtered indexes on dbo.users

DECLARE @email nvarchar(255) = N'$(AdminEmail)', @userName nvarchar(50) = N'$(AdminUserName)',
        @fullName nvarchar(100) = N'$(AdminFullName)', @hash nvarchar(100) = N'$(AdminPasswordHash)';
IF @hash NOT LIKE N'$2%$%' OR LEN(@hash) <> 60 THROW 51401, 'AdminPasswordHash must be a 60 character BCrypt hash.', 1;
IF @email NOT LIKE N'_%@_%._%' THROW 51402, 'AdminEmail is not an e-mail address.', 1;
DECLARE @adminRole bigint = (SELECT role_id FROM dbo.roles WHERE name = N'ADMIN');
IF @adminRole IS NULL THROW 51403, 'Run database/seed/01_reference_data.sql first (ADMIN role is missing).', 1;
IF EXISTS (SELECT 1 FROM dbo.users WHERE role_id = @adminRole) BEGIN PRINT 'An administrator already exists; nothing changed.'; RETURN; END;
IF EXISTS (SELECT 1 FROM dbo.users WHERE email = @email OR user_name = @userName) THROW 51404, 'That e-mail or user name is already used by another account.', 1;

INSERT INTO dbo.users (role_id, email, user_name, password_hash, full_name, status)
VALUES (@adminRole, @email, @userName, @hash, @fullName, N'ACTIVE');
PRINT 'Administrator account created.';
