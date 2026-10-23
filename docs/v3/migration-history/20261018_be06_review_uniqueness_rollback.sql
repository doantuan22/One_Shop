/* Optional DEC-26 downgrade, only with separate approval and a verified backup.
   Preserves every row. Refuses a downgrade once the same user has reviewed multiple items of one SKU.
   Never delete/merge Reviews to force the old key. Reapply 20261014_be06_review_uniqueness.sql to return to V3.
   The V3 application must remain stopped while this obsolete key is present. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;

IF DB_NAME() <> N'oneshop' AND DB_NAME() NOT LIKE N'oneshop%[_]test'
    THROW 50621, 'Select the explicit OneShop database before the controlled downgrade.', 1;
IF OBJECT_ID(N'dbo.reviews', N'U') IS NULL
    THROW 50622, 'The existing reviews table is required.', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @reviewCount bigint;
    SELECT @reviewCount=COUNT_BIG(*) FROM dbo.reviews WITH (TABLOCKX,HOLDLOCK);
    IF EXISTS (SELECT user_id,product_id FROM dbo.reviews GROUP BY user_id,product_id HAVING COUNT(*)>1)
        THROW 50623, 'Downgrade refused: per-item reviews cannot fit the obsolete global key. No data changed.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.reviews') AND name=N'UQ_reviews_user_product')
        ALTER TABLE dbo.reviews ADD CONSTRAINT UQ_reviews_user_product UNIQUE (user_id,product_id);
    IF (SELECT COUNT(*) FROM sys.index_columns ic JOIN sys.indexes i ON i.object_id=ic.object_id AND i.index_id=ic.index_id
        JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id
        WHERE i.object_id=OBJECT_ID(N'dbo.reviews') AND i.name=N'UQ_reviews_user_product' AND ic.key_ordinal>0)<>2
       OR (SELECT COUNT(*) FROM sys.index_columns ic JOIN sys.indexes i ON i.object_id=ic.object_id AND i.index_id=ic.index_id
        JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id
        WHERE i.object_id=OBJECT_ID(N'dbo.reviews') AND i.name=N'UQ_reviews_user_product'
        AND ((ic.key_ordinal=1 AND c.name=N'user_id') OR (ic.key_ordinal=2 AND c.name=N'product_id')))<>2
        THROW 50625, 'The existing obsolete key has unexpected columns; no downgrade was performed.', 1;
    IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.reviews') AND name=N'UX_reviews_order_item')
        DROP INDEX UX_reviews_order_item ON dbo.reviews;
    IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.reviews') AND name=N'UX_reviews_legacy_user_product')
        DROP INDEX UX_reviews_legacy_user_product ON dbo.reviews;
    IF @reviewCount<>(SELECT COUNT_BIG(*) FROM dbo.reviews)
        THROW 50624, 'Review count changed during downgrade.', 1;
    COMMIT TRANSACTION;
    PRINT 'Review uniqueness downgraded without changing data. V3 startup must remain blocked.';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
