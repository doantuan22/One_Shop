/* =============================================================================
   OneShop - Migration 003: reviews.order_id + phan hoi danh gia (reply)
   - order_id: don COMPLETED khach dung de danh gia; chi nhanh suy ra tu orders.store_id.
   - reply / replied_by / replied_at: mot phan hoi (sua duoc, khong xoa); nguoi + thoi diem gan nhat.
   - Dung cho database DA CO DU LIEU; database moi tao bang 01..05 da co san.
   - Khong xoa du lieu. Chay lai nhieu lan an toan (idempotent).
   Chay tu thu muc database/:
     sqlcmd -S localhost,1433 -U <user> -P <password> -C -f 65001 -b -i migration_003_review_order_and_reply.sql
   ========================================================================== */
USE [oneshop];
GO
SET NOCOUNT ON;
GO

/* 1. Cot moi (NULL de danh gia cu van hop le) */
IF COL_LENGTH('dbo.reviews', 'order_id') IS NULL
    ALTER TABLE dbo.reviews ADD order_id BIGINT NULL;
IF COL_LENGTH('dbo.reviews', 'reply') IS NULL
    ALTER TABLE dbo.reviews ADD reply NVARCHAR(1000) NULL;
IF COL_LENGTH('dbo.reviews', 'replied_by') IS NULL
    ALTER TABLE dbo.reviews ADD replied_by BIGINT NULL;
IF COL_LENGTH('dbo.reviews', 'replied_at') IS NULL
    ALTER TABLE dbo.reviews ADD replied_at DATETIME2(0) NULL;
GO

/* 2. Khoa ngoai */
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_reviews_order' AND parent_object_id = OBJECT_ID('dbo.reviews'))
    ALTER TABLE dbo.reviews ADD CONSTRAINT FK_reviews_order FOREIGN KEY (order_id) REFERENCES dbo.orders (order_id);
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_reviews_replied_by' AND parent_object_id = OBJECT_ID('dbo.reviews'))
    ALTER TABLE dbo.reviews ADD CONSTRAINT FK_reviews_replied_by FOREIGN KEY (replied_by) REFERENCES dbo.users (user_id);
GO

/* 3. Phan hoi: cung NULL hoac cung co gia tri, noi dung khong rong sau khi trim */
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_reviews_reply' AND parent_object_id = OBJECT_ID('dbo.reviews'))
    ALTER TABLE dbo.reviews ADD CONSTRAINT CK_reviews_reply CHECK (
        (reply IS NULL AND replied_by IS NULL AND replied_at IS NULL)
        OR
        (reply IS NOT NULL AND LEN(LTRIM(RTRIM(reply))) > 0
         AND replied_by IS NOT NULL AND replied_at IS NOT NULL));
GO

/* 4. Index noi nhanh voi orders */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_reviews_order' AND object_id = OBJECT_ID('dbo.reviews'))
    CREATE INDEX IX_reviews_order ON dbo.reviews (order_id);
GO

/* 5. Dien order_id cho danh gia da co (chi dong NULL, nen chay lai an toan).
      Chon don cua dung khach, COMPLETED, co chua san pham cua danh gia, moc hoan tat khong muon hon luc tao danh gia;
      lay don co moc hoan tat lon nhat (bang nhau thi order_id lon nhat). Khong tra duoc thi giu NULL. */
UPDATE r
SET r.order_id = x.order_id
FROM dbo.reviews r
OUTER APPLY (
    SELECT TOP (1) o.order_id
    FROM dbo.orders o
    OUTER APPLY (
        SELECT MAX(h.changed_at) AS completed_at
        FROM dbo.order_status_history h
        WHERE h.order_id = o.order_id AND h.new_status = N'COMPLETED'
    ) c
    WHERE o.user_id = r.user_id
      AND o.order_status = N'COMPLETED'
      AND EXISTS (SELECT 1
                  FROM dbo.order_items oi
                  JOIN dbo.store_products sp ON sp.store_product_id = oi.store_product_id
                  WHERE oi.order_id = o.order_id AND sp.product_id = r.product_id)
      AND COALESCE(c.completed_at, o.updated_at) <= r.created_at
    ORDER BY COALESCE(c.completed_at, o.updated_at) DESC, o.order_id DESC
) x
WHERE r.order_id IS NULL AND x.order_id IS NOT NULL;
GO

/* 6. Kiem tra */
IF COL_LENGTH('dbo.reviews', 'order_id') IS NULL OR COL_LENGTH('dbo.reviews', 'reply') IS NULL
   OR COL_LENGTH('dbo.reviews', 'replied_by') IS NULL OR COL_LENGTH('dbo.reviews', 'replied_at') IS NULL
    THROW 50301, 'reviews columns are missing.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_reviews_reply')
    THROW 50302, 'CK_reviews_reply is missing.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_reviews_order' AND object_id = OBJECT_ID('dbo.reviews'))
    THROW 50303, 'IX_reviews_order is missing.', 1;
IF EXISTS (
    SELECT 1 FROM dbo.reviews r
    WHERE r.order_id IS NOT NULL
      AND NOT EXISTS (
          SELECT 1 FROM dbo.orders o
          JOIN dbo.order_items oi ON oi.order_id = o.order_id
          JOIN dbo.store_products sp ON sp.store_product_id = oi.store_product_id
          WHERE o.order_id = r.order_id AND o.user_id = r.user_id
            AND o.order_status = N'COMPLETED' AND sp.product_id = r.product_id))
    THROW 50304, 'A review points to an order that is not the reviewer''s COMPLETED order of that product.', 1;
PRINT 'Migration 003 applied: reviews.order_id, reply, replied_by, replied_at.';
GO
