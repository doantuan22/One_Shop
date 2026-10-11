/* =============================================================================
   OneShop - Phase 2 - File 03: INDEX (dung danh sach Roadmap V2 muc 7.4)
   Cac UNIQUE index (users.email, products.sku, store_products(store_id,product_id),
   cart_items(cart_id,store_product_id)) da duoc tao boi UNIQUE constraint o file 02.
   UX_users_user_name la filtered unique index nen can QUOTED_IDENTIFIER ON (sqlcmd mac dinh OFF).
   ========================================================================== */
USE [oneshop];
GO
SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
GO

-- Catalog theo Store
DROP INDEX IF EXISTS IX_store_products_store_status_qty ON dbo.store_products;
CREATE INDEX IX_store_products_store_status_qty
    ON dbo.store_products (store_id, status, quantity);

-- Availability toan chuoi (SKU dang ban o Store nao)
DROP INDEX IF EXISTS IX_store_products_product_status ON dbo.store_products;
CREATE INDEX IX_store_products_product_status
    ON dbo.store_products (product_id, status);

-- Staff: hang doi Order theo Store
DROP INDEX IF EXISTS IX_orders_store_status_created ON dbo.orders;
CREATE INDEX IX_orders_store_status_created
    ON dbo.orders (store_id, order_status, created_at);

-- Lich su don cua khach
DROP INDEX IF EXISTS IX_orders_user_created ON dbo.orders;
CREATE INDEX IX_orders_user_created
    ON dbo.orders (user_id, created_at);

-- Lich su bien dong kho theo StoreProduct
DROP INDEX IF EXISTS IX_inventory_movements_sp_created ON dbo.inventory_movements;
CREATE INDEX IX_inventory_movements_sp_created
    ON dbo.inventory_movements (store_product_id, created_at);

-- Timeline trang thai Order
DROP INDEX IF EXISTS IX_order_status_history_order_changed ON dbo.order_status_history;
CREATE INDEX IX_order_status_history_order_changed
    ON dbo.order_status_history (order_id, changed_at);
GO

PRINT '6 indexes created.';
GO

-- Dang nhap bang user_name: duy nhat, bo qua cac tai khoan chua co user_name
DROP INDEX IF EXISTS UX_users_user_name ON dbo.users;
CREATE UNIQUE INDEX UX_users_user_name
    ON dbo.users (user_name) WHERE user_name IS NOT NULL;
GO

-- Danh gia -> don (tra chi nhanh cua danh gia qua orders.store_id)
DROP INDEX IF EXISTS IX_reviews_order ON dbo.reviews;
CREATE INDEX IX_reviews_order ON dbo.reviews (order_id);
GO
