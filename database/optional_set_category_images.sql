/* =============================================================================
   OneShop - Optional Script: Cap nhat anh mac dinh cho cac danh muc seed
   - Chi cap nhat danh muc co image_url IS NULL theo ten danh muc seed.
   - Dung cho database da co du lieu can khoi tao anh danh muc tu anh tinh san co.
   - Chay lai nhieu lan an toan (idempotent), khong ghi de anh da co hoac do Admin cap nhat.
   Chay tu thu muc database/:
     sqlcmd -S localhost,1433 -U <user> -P <password> -C -f 65001 -b -i optional_set_category_images.sql
   ========================================================================== */
USE [oneshop];
GO
SET NOCOUNT ON;
GO

UPDATE dbo.categories
SET image_url = N'/images/site/categories/category-skincare.webp',
    image_public_id = NULL
WHERE name = N'Chăm sóc da' AND image_url IS NULL;

UPDATE dbo.categories
SET image_url = N'/images/site/categories/category-makeup.webp',
    image_public_id = NULL
WHERE name = N'Trang điểm' AND image_url IS NULL;

UPDATE dbo.categories
SET image_url = N'/images/site/categories/category-haircare.webp',
    image_public_id = NULL
WHERE name = N'Chăm sóc tóc' AND image_url IS NULL;
GO

PRINT 'Optional script executed: Seed category images updated for categories with NULL image_url.';
GO

