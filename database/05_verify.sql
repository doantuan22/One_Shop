/* =============================================================================
   OneShop - Phase 2 - File 05: kiem tra schema + seed (chi doc, khong sua du lieu)
   Neu co vi pham, script THROW loi.
   ========================================================================== */
USE [oneshop];
GO
SET NOCOUNT ON;
GO

/* 1. Dung 19 bang nguoi dung */
DECLARE @tables INT = (SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped = 0);
IF @tables <> 19 THROW 50001, 'Expected exactly 19 tables.', 1;

/* 1b. Ten bang dung danh sach Roadmap 7.2 (khong thieu, khong thua) */
DECLARE @expected TABLE (name SYSNAME PRIMARY KEY);
INSERT INTO @expected VALUES (N'roles'),(N'users'),(N'customer_addresses'),(N'stores'),(N'staff_store_assignments'),
 (N'categories'),(N'brands'),(N'products'),(N'product_images'),(N'store_products'),(N'inventory_movements'),
 (N'carts'),(N'cart_items'),(N'checkout_sessions'),(N'orders'),(N'order_items'),(N'payments'),
 (N'order_status_history'),(N'reviews');
IF EXISTS (SELECT 1 FROM @expected e WHERE NOT EXISTS (SELECT 1 FROM sys.tables t WHERE t.name = e.name))
   OR EXISTS (SELECT 1 FROM sys.tables t WHERE t.is_ms_shipped = 0 AND NOT EXISTS (SELECT 1 FROM @expected e WHERE e.name = t.name))
    THROW 50011, 'Table set differs from Roadmap V2 list.', 1;

/* 1c. Moi bang co PK; UNIQUE / CHECK / INDEX bat buoc ton tai */
IF EXISTS (SELECT 1 FROM sys.tables t WHERE NOT EXISTS (SELECT 1 FROM sys.key_constraints k WHERE k.parent_object_id = t.object_id AND k.type = 'PK'))
    THROW 50012, 'A table has no primary key.', 1;
DECLARE @required TABLE (name SYSNAME PRIMARY KEY);
INSERT INTO @required VALUES
 (N'UQ_users_email'),(N'UQ_products_sku'),(N'UQ_store_products_store_product'),(N'UQ_cart_items_cart_store_product'),
 (N'CK_store_products_quantity'),(N'CK_store_products_price'),(N'CK_reviews_rating'),
 (N'IX_store_products_store_status_qty'),(N'IX_store_products_product_status'),(N'IX_orders_store_status_created'),
 (N'IX_orders_user_created'),(N'IX_inventory_movements_sp_created'),(N'IX_order_status_history_order_changed');
IF EXISTS (SELECT 1 FROM @required r
           WHERE NOT EXISTS (SELECT 1 FROM sys.objects o WHERE o.name = r.name)
             AND NOT EXISTS (SELECT 1 FROM sys.indexes i WHERE i.name = r.name))
    THROW 50013, 'A required UNIQUE/CHECK/INDEX from Roadmap 7.4 is missing.', 1;

/* 1d. order_items snapshot dung 4 truong + store_product_id; khong co cot thua */
IF COL_LENGTH('dbo.order_items', 'product_sku') IS NOT NULL
    THROW 50014, 'order_items.product_sku must not exist.', 1;

/* 2. Payment gan Order, khong gan CheckoutSession */
IF COL_LENGTH('dbo.payments', 'order_id') IS NULL OR COL_LENGTH('dbo.payments', 'checkout_id') IS NOT NULL
    THROW 50002, 'payments must reference order_id, not checkout_id.', 1;

/* 3. Ton kho khong am, gia khong am */
IF EXISTS (SELECT 1 FROM dbo.store_products WHERE quantity < 0 OR price < 0)
    THROW 50003, 'Negative quantity/price found.', 1;

/* 4. Toi thieu 3 Store; co SKU o >= 3 Store voi price/quantity/status khac nhau */
IF (SELECT COUNT(*) FROM dbo.stores) < 3 THROW 50004, 'Need at least 3 stores.', 1;
IF NOT EXISTS (
    SELECT 1 FROM dbo.store_products
    GROUP BY product_id
    HAVING COUNT(*) >= 3 AND COUNT(DISTINCT price) > 1 AND COUNT(DISTINCT quantity) > 1 AND COUNT(DISTINCT status) > 1)
    THROW 50005, 'No SKU spread across >=3 stores with differing price/quantity/status.', 1;

/* 5. Ton hien tai khop tong cac movement (before/after lien tuc theo StoreProduct) */
IF EXISTS (
    SELECT 1 FROM dbo.inventory_movements m
    WHERE m.quantity_after <> m.quantity_before + m.quantity_change)
    THROW 50006, 'Inventory movement arithmetic mismatch.', 1;

/* 6. Order.total = tong OrderItem.subtotal; Checkout.total = tong Order.total */
IF EXISTS (
    SELECT 1 FROM dbo.orders o
    WHERE o.total_amount <> (SELECT SUM(subtotal) FROM dbo.order_items WHERE order_id = o.order_id))
    THROW 50007, 'Order total <> sum(order_items).', 1;
IF EXISTS (
    SELECT 1 FROM dbo.checkout_sessions c
    WHERE c.total_amount <> (SELECT SUM(total_amount) FROM dbo.orders WHERE checkout_id = c.checkout_id))
    THROW 50008, 'Checkout total <> sum(orders).', 1;

/* 7. Moi Order co item cung Store voi Order (one Order - one Store) */
IF EXISTS (
    SELECT 1 FROM dbo.order_items oi
    JOIN dbo.orders o ON o.order_id = oi.order_id
    JOIN dbo.store_products sp ON sp.store_product_id = oi.store_product_id
    WHERE sp.store_id <> o.store_id)
    THROW 50009, 'Order contains item from another store.', 1;

/* 8. Moi constraint deu duoc tin cay (khong bi tao WITH NOCHECK) */
IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE is_not_trusted = 1)
   OR EXISTS (SELECT 1 FROM sys.foreign_keys WHERE is_not_trusted = 1)
    THROW 50010, 'Untrusted constraint found.', 1;

PRINT 'All Phase 2 checks passed.';
GO

/* ---- Minh hoa TC-01: mot SKU tai nhieu Store voi price/quantity/status rieng ---- */
SELECT p.sku, s.name AS store_name, sp.price, sp.quantity, sp.status AS store_product_status, s.status AS store_status
FROM dbo.store_products sp
JOIN dbo.products p ON p.product_id = sp.product_id
JOIN dbo.stores   s ON s.store_id   = sp.store_id
WHERE p.sku = N'COCOON-SERUM-30'
ORDER BY s.store_id;

/* ---- Minh hoa TC-15: cung checkout, moi Order co Store / fulfillment / payment rieng ---- */
SELECT o.checkout_id, o.order_id, s.name AS store_name, o.fulfillment_type, o.payment_method,
       o.payment_status, o.order_status, pay.status AS payment_row_status
FROM dbo.orders o
JOIN dbo.stores s ON s.store_id = o.store_id
LEFT JOIN dbo.payments pay ON pay.order_id = o.order_id
WHERE o.checkout_id = 1
ORDER BY o.order_id;
GO
