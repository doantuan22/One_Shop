/* =============================================================================
   OneShop - Migration 004: categories.image_url + categories.image_public_id
   - Cho phep moi danh muc co 1 anh minh hoa (quan ly tuong tu logo thuong hieu).
   - image_url: NVARCHAR(500) NULL (Cloudinary secure_url hoac duong dan tinh trong repo).
   - image_public_id: NVARCHAR(255) NULL (Cloudinary public_id de xoa/thay anh; NULL neu la anh ngoai/tinh).
   - Dung cho database DA CO DU LIEU; database moi tao bang 01..05 da co san.
   - Khong xoa du lieu. Chay lai nhieu lan an toan (idempotent).
   Chay tu thu muc database/:
     sqlcmd -S localhost,1433 -U <user> -P <password> -C -f 65001 -b -i migration_004_category_images.sql
   ========================================================================== */
USE [oneshop];
GO
SET NOCOUNT ON;
GO

/* 1. Them cot image_url neu chua co */
IF COL_LENGTH('dbo.categories', 'image_url') IS NULL
    ALTER TABLE dbo.categories ADD image_url NVARCHAR(500) NULL;
GO

/* 2. Them cot image_public_id neu chua co */
IF COL_LENGTH('dbo.categories', 'image_public_id') IS NULL
    ALTER TABLE dbo.categories ADD image_public_id NVARCHAR(255) NULL;
GO

/* 3. Kiem tra */
IF COL_LENGTH('dbo.categories', 'image_url') IS NULL
    THROW 50401, 'categories.image_url is missing.', 1;
IF COL_LENGTH('dbo.categories', 'image_public_id') IS NULL
    THROW 50402, 'categories.image_public_id is missing.', 1;

PRINT 'Migration 004 applied: categories.image_url + categories.image_public_id.';
GO

