/* BE-06 / OPEN-05 approved: one Review per OrderItem. Data-preserving, rerunnable migration.
   Existing databases only: run explicitly after backup/approval. Fresh 01_schema.sql already includes DEC-26.
   Does not relink, update or delete any Review/Reply. No changes outside review uniqueness.
   Reverting to UNIQUE(user_id, product_id) is impossible after per-item duplicates exist without a data decision. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;

IF DB_NAME() IN (N'master', N'model', N'msdb', N'tempdb')
    THROW 50601, 'Select the OneShop database (sqlcmd -d <database>) before applying this migration.', 1;
IF OBJECT_ID(N'dbo.reviews', N'U') IS NULL OR COL_LENGTH(N'dbo.reviews', N'order_item_id') IS NULL
    THROW 50602, 'BE-06 requires the existing V3 reviews table.', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @reviewCount bigint;
    SELECT @reviewCount = COUNT_BIG(*) FROM dbo.reviews WITH (TABLOCKX, HOLDLOCK);
    IF EXISTS (SELECT order_item_id FROM dbo.reviews WHERE order_item_id IS NOT NULL
               GROUP BY order_item_id HAVING COUNT(*) > 1)
        THROW 50603, 'Duplicate OrderItem reviews exist: reconcile manually; no data was changed.', 1;
    IF EXISTS (SELECT user_id, product_id FROM dbo.reviews WHERE order_item_id IS NULL
               GROUP BY user_id, product_id HAVING COUNT(*) > 1)
        THROW 50604, 'Duplicate legacy reviews exist: reconcile manually; no data was changed.', 1;

    IF EXISTS (SELECT 1 FROM sys.key_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.reviews')
               AND name = N'UQ_reviews_user_product')
        ALTER TABLE dbo.reviews DROP CONSTRAINT UQ_reviews_user_product;

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.reviews') AND name = N'UX_reviews_order_item')
        CREATE UNIQUE INDEX UX_reviews_order_item ON dbo.reviews(order_item_id) WHERE order_item_id IS NOT NULL;
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.reviews') AND name = N'UX_reviews_legacy_user_product')
        CREATE UNIQUE INDEX UX_reviews_legacy_user_product ON dbo.reviews(user_id, product_id) WHERE order_item_id IS NULL;

    IF @reviewCount <> (SELECT COUNT_BIG(*) FROM dbo.reviews)
        THROW 50605, 'Review count changed during migration.', 1;
    IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.reviews')
               AND name IN (N'UX_reviews_order_item', N'UX_reviews_legacy_user_product')
               AND (is_unique=0 OR has_filter=0 OR is_disabled=1))
        THROW 50606, 'An existing BE-06 index has an unexpected definition.', 1;
    IF (SELECT COUNT(*) FROM sys.index_columns ic JOIN sys.indexes i ON i.object_id=ic.object_id AND i.index_id=ic.index_id
        JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id
        WHERE i.object_id=OBJECT_ID(N'dbo.reviews') AND i.name=N'UX_reviews_order_item' AND ic.key_ordinal>0) <> 1
       OR NOT EXISTS (SELECT 1 FROM sys.index_columns ic JOIN sys.indexes i ON i.object_id=ic.object_id AND i.index_id=ic.index_id
        JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id
        WHERE i.object_id=OBJECT_ID(N'dbo.reviews') AND i.name=N'UX_reviews_order_item'
        AND ic.key_ordinal=1 AND c.name=N'order_item_id')
        THROW 50607, 'The per-OrderItem unique key has unexpected columns.', 1;
    IF (SELECT COUNT(*) FROM sys.index_columns ic JOIN sys.indexes i ON i.object_id=ic.object_id AND i.index_id=ic.index_id
        WHERE i.object_id=OBJECT_ID(N'dbo.reviews') AND i.name=N'UX_reviews_legacy_user_product' AND ic.key_ordinal>0) <> 2
       OR (SELECT COUNT(*) FROM sys.index_columns ic JOIN sys.indexes i ON i.object_id=ic.object_id AND i.index_id=ic.index_id
        JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id
        WHERE i.object_id=OBJECT_ID(N'dbo.reviews') AND i.name=N'UX_reviews_legacy_user_product'
        AND ((ic.key_ordinal=1 AND c.name=N'user_id') OR (ic.key_ordinal=2 AND c.name=N'product_id'))) <> 2
        THROW 50608, 'The legacy unique key has unexpected columns.', 1;
    IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.reviews')
        AND ((name=N'UX_reviews_order_item'
              AND REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(UPPER(filter_definition),'[',''),']',''),' ',''),'(',''),')','') <> N'ORDER_ITEM_IDISNOTNULL')
          OR (name=N'UX_reviews_legacy_user_product'
              AND REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(UPPER(filter_definition),'[',''),']',''),' ',''),'(',''),')','') <> N'ORDER_ITEM_IDISNULL')))
        THROW 50609, 'The BE-06 unique key filter has an unexpected predicate.', 1;
    COMMIT TRANSACTION;
    PRINT 'BE-06 review uniqueness migrated; existing reviews and replies preserved.';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
