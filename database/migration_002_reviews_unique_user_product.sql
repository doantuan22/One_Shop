/* =============================================================================
   OneShop - Migration 002: moi khach chi danh gia mot Product mot lan
   - Them UNIQUE (user_id, product_id) tren dbo.reviews: ReviewService kiem tra truoc, rang buoc nay chan
     truong hop hai request gui dong thoi.
   - Dung cho database DA CO DU LIEU; database moi tao bang 01..05 da co san rang buoc.
   - Khong xoa du lieu: neu dang co review trung (user, product) script dung va liet ke de xu ly tay.
   - Chay lai nhieu lan an toan (idempotent).
   Chay tu thu muc database/:
     sqlcmd -S localhost,1433 -U <user> -P <password> -C -f 65001 -b -i migration_002_reviews_unique_user_product.sql
   ========================================================================== */
USE [oneshop];
GO
SET NOCOUNT ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = N'UQ_reviews_user_product'
               AND parent_object_id = OBJECT_ID('dbo.reviews'))
BEGIN
    IF EXISTS (SELECT 1 FROM dbo.reviews GROUP BY user_id, product_id HAVING COUNT(*) > 1)
    BEGIN
        SELECT user_id, product_id, COUNT(*) AS duplicate_reviews, MIN(review_id) AS keep_review_id
        FROM dbo.reviews GROUP BY user_id, product_id HAVING COUNT(*) > 1;
        THROW 50201, 'Duplicate (user_id, product_id) reviews exist: resolve them (HIDDEN/delete) before this migration.', 1;
    END;
    ALTER TABLE dbo.reviews ADD CONSTRAINT UQ_reviews_user_product UNIQUE (user_id, product_id);
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = N'UQ_reviews_user_product'
               AND parent_object_id = OBJECT_ID('dbo.reviews'))
    THROW 50202, 'UQ_reviews_user_product is missing.', 1;
PRINT 'Migration 002 applied: UQ_reviews_user_product.';
GO
