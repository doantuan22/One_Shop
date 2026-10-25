/* Fail closed: never choose a primary image on behalf of an existing installation. */
SET XACT_ABORT ON;
SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
BEGIN TRANSACTION;
IF EXISTS (SELECT product_id FROM dbo.product_images WITH (TABLOCKX,HOLDLOCK)
           WHERE is_primary=1 GROUP BY product_id HAVING COUNT(*)>1)
BEGIN
    SELECT product_id,COUNT(*) AS primary_count FROM dbo.product_images
    WHERE is_primary=1 GROUP BY product_id HAVING COUNT(*)>1;
    ROLLBACK TRANSACTION;
    THROW 51031, 'Duplicate primary images found. Obtain an approved per-Product resolution; no data was changed.', 1;
END;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.product_images') AND name=N'UX_product_images_one_primary')
    CREATE UNIQUE INDEX UX_product_images_one_primary ON dbo.product_images(product_id) WHERE is_primary=1;
COMMIT TRANSACTION;
SELECT name,is_unique,filter_definition FROM sys.indexes
WHERE object_id=OBJECT_ID(N'dbo.product_images') AND name=N'UX_product_images_one_primary';
