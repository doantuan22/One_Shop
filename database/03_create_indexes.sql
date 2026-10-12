/* =============================================================================
   OneShop V3 - File 03: INDEXES
   Kien truc: Centralized Commerce - Distributed Inventory & Fulfillment
   Dac ta: docs/OneShop_V3_DacTaNghiepVu_MaTranChuyenDoiCSDL.md
   ========================================================================== */
USE [oneshop];
GO
SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
GO

-- 1. Availability & Catalog theo Store
DROP INDEX IF EXISTS IX_store_products_store_status_qty ON dbo.store_products;
CREATE INDEX IX_store_products_store_status_qty
    ON dbo.store_products (store_id, status, quantity);

-- 2. Availability toan chuoi (SKU dang ban o Store nao)
DROP INDEX IF EXISTS IX_store_products_product_status ON dbo.store_products;
CREATE INDEX IX_store_products_product_status
    ON dbo.store_products (product_id, status);

-- 3. Hang doi Fulfillment theo Store (Staff queue)
DROP INDEX IF EXISTS IX_order_fulfillments_store_status ON dbo.order_fulfillments;
CREATE INDEX IX_order_fulfillments_store_status
    ON dbo.order_fulfillments (store_id, status, created_at);

-- 4. Kien hang theo Order
DROP INDEX IF EXISTS IX_order_fulfillments_order_status ON dbo.order_fulfillments;
CREATE INDEX IX_order_fulfillments_order_status
    ON dbo.order_fulfillments (order_id, status);

-- 5. Lich su don cua khach
DROP INDEX IF EXISTS IX_orders_user_created ON dbo.orders;
CREATE INDEX IX_orders_user_created
    ON dbo.orders (user_id, created_at);

-- 6. Hang doi Order legacy (store_id)
DROP INDEX IF EXISTS IX_orders_store_status_created ON dbo.orders;
CREATE INDEX IX_orders_store_status_created
    ON dbo.orders (store_id, order_status, created_at)
    WHERE store_id IS NOT NULL;

-- 7. Chi tiet don theo Fulfillment
DROP INDEX IF EXISTS IX_order_items_fulfillment ON dbo.order_items;
CREATE INDEX IX_order_items_fulfillment
    ON dbo.order_items (fulfillment_id);

-- 8. Chi tiet don theo StoreProduct
DROP INDEX IF EXISTS IX_order_items_store_product ON dbo.order_items;
CREATE INDEX IX_order_items_store_product
    ON dbo.order_items (store_product_id);

-- 9. Lich su bien dong kho theo StoreProduct
DROP INDEX IF EXISTS IX_inventory_movements_sp_created ON dbo.inventory_movements;
CREATE INDEX IX_inventory_movements_sp_created
    ON dbo.inventory_movements (store_product_id, created_at);

-- 10. Timeline trang thai Order tong the
DROP INDEX IF EXISTS IX_order_status_history_order_changed ON dbo.order_status_history;
CREATE INDEX IX_order_status_history_order_changed
    ON dbo.order_status_history (order_id, changed_at);

-- 11. Timeline trang thai Fulfillment chi nhanh
DROP INDEX IF EXISTS IX_ofsh_fulfillment_changed ON dbo.order_fulfillment_status_history;
CREATE INDEX IX_ofsh_fulfillment_changed
    ON dbo.order_fulfillment_status_history (fulfillment_id, changed_at);

-- 12. Dang nhap bang user_name: unique filtered index
DROP INDEX IF EXISTS UX_users_user_name ON dbo.users;
CREATE UNIQUE INDEX UX_users_user_name
    ON dbo.users (user_name) WHERE user_name IS NOT NULL;

-- 13. Danh gia theo Order (legacy)
DROP INDEX IF EXISTS IX_reviews_order ON dbo.reviews;
CREATE INDEX IX_reviews_order ON dbo.reviews (order_id)
    WHERE order_id IS NOT NULL;

-- 14. Danh gia theo OrderItem (V3)
DROP INDEX IF EXISTS IX_reviews_order_item ON dbo.reviews;
CREATE INDEX IX_reviews_order_item ON dbo.reviews (order_item_id)
    WHERE order_item_id IS NOT NULL;

-- 15. Gio hang theo Product (V3)
DROP INDEX IF EXISTS IX_cart_items_product ON dbo.cart_items;
CREATE INDEX IX_cart_items_product ON dbo.cart_items (product_id);
GO

PRINT 'OneShop V3 indexes created.';
GO
