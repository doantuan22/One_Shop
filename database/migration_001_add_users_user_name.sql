/* =============================================================================
   OneShop - Migration 001: them users.user_name (dang nhap bang user_name hoac email)
   - Dung cho database DA CO DU LIEU (khong drop bang, khong xoa du lieu).
   - Database moi tao bang 01..05 da co san cot va index nay, khong can chay file nay.
   - Chay lai nhieu lan an toan (idempotent).
   Chay tu thu muc database/:
     sqlcmd -S localhost,1433 -U <user> -P <password> -C -f 65001 -b -I -i migration_001_add_users_user_name.sql
   (-I = QUOTED_IDENTIFIER ON, bat buoc de tao filtered index)
   ========================================================================== */
USE [oneshop];
GO
SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
GO

/* 1. Cot user_name: NULL de cac tai khoan cu van hop le */
IF COL_LENGTH('dbo.users', 'user_name') IS NULL
    ALTER TABLE dbo.users ADD user_name NVARCHAR(50) NULL;
GO

/* 2. Duy nhat tren cac gia tri khac NULL (filtered unique index) */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UX_users_user_name' AND object_id = OBJECT_ID('dbo.users'))
    CREATE UNIQUE INDEX UX_users_user_name ON dbo.users (user_name) WHERE user_name IS NOT NULL;
GO

/* 3. (Tuy chon) Gan user_name cho cac tai khoan demo cua seed; bo qua tai khoan da co user_name
      hoac khi gia tri do da duoc nguoi khac su dung. */
UPDATE u SET user_name = v.user_name
FROM dbo.users u
JOIN (VALUES
    (N'admin@oneshop.vn',         N'admin'),
    (N'staff.thuduc@oneshop.vn',  N'staff_thuduc'),
    (N'staff.govap@oneshop.vn',   N'staff_govap'),
    (N'staff.quan7@oneshop.vn',   N'staff_quan7'),
    (N'staff.quan10@oneshop.vn',  N'staff_quan10'),
    (N'khachhang1@example.com',   N'khachhang1'),
    (N'khachhang2@example.com',   N'khachhang2')
) AS v (email, user_name) ON v.email = u.email
WHERE u.user_name IS NULL
  AND NOT EXISTS (SELECT 1 FROM dbo.users x WHERE x.user_name = v.user_name);
GO

/* 4. Kiem tra */
IF COL_LENGTH('dbo.users', 'user_name') IS NULL THROW 50101, 'users.user_name is missing.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UX_users_user_name' AND object_id = OBJECT_ID('dbo.users'))
    THROW 50102, 'UX_users_user_name is missing.', 1;
PRINT 'Migration 001 applied: users.user_name + UX_users_user_name.';
GO
