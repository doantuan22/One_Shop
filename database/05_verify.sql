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
 (N'UQ_users_email'),(N'UQ_reviews_user_product'),(N'UX_users_user_name'),(N'UQ_products_sku'),(N'UQ_store_products_store_product'),(N'UQ_cart_items_cart_store_product'),
 (N'CK_store_products_quantity'),(N'CK_store_products_price'),(N'CK_reviews_rating'),(N'CK_reviews_reply'),(N'FK_reviews_order'),(N'IX_reviews_order'),
 (N'IX_store_products_store_status_qty'),(N'IX_store_products_product_status'),(N'IX_orders_store_status_created'),
 (N'IX_orders_user_created'),(N'IX_inventory_movements_sp_created'),(N'IX_order_status_history_order_changed');
IF EXISTS (SELECT 1 FROM @required r
           WHERE NOT EXISTS (SELECT 1 FROM sys.objects o WHERE o.name = r.name)
             AND NOT EXISTS (SELECT 1 FROM sys.indexes i WHERE i.name = r.name))
    THROW 50013, 'A required UNIQUE/CHECK/INDEX from Roadmap 7.4 is missing.', 1;

/* 1d. order_items snapshot dung 4 truong + store_product_id; khong co cot thua */
IF COL_LENGTH('dbo.order_items', 'product_sku') IS NOT NULL
    THROW 50014, 'order_items.product_sku must not exist.', 1;

/* 1e. categories co du image_url va image_public_id */
IF COL_LENGTH('dbo.categories', 'image_url') IS NULL OR COL_LENGTH('dbo.categories', 'image_public_id') IS NULL
    THROW 50016, 'categories image columns missing.', 1;

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

/* 9. Danh gia gan voi don: don phai thuoc dung khach, COMPLETED va co chua san pham cua danh gia
      (CHECK khong tham chieu bang khac duoc nen day la kiem tra du lieu; Service moi la noi bao dam khi ghi) */
IF EXISTS (
    SELECT 1 FROM dbo.reviews r
    WHERE r.order_id IS NOT NULL
      AND NOT EXISTS (
          SELECT 1 FROM dbo.orders o
          JOIN dbo.order_items oi ON oi.order_id = o.order_id
          JOIN dbo.store_products sp ON sp.store_product_id = oi.store_product_id
          WHERE o.order_id = r.order_id AND o.user_id = r.user_id
            AND o.order_status = N'COMPLETED' AND sp.product_id = r.product_id))
    THROW 50015, 'A review points to an order that is not the reviewer''s COMPLETED order of that product.', 1;

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

/* ===== PART 2: LARGE SEED CHECKS (skipped for the base-data-only test database) ===== */
/* =============================================================================
   OneShop - part 2 of 05_verify.sql: volumes, consistency rules 1-9 and Cloudinary images
   Read-only checks of the whole seed (04_seed_data.sql).
   Any violation THROWs; success prints "All large-seed checks passed."

   ========================================================================== */
SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
GO

DECLARE @n BIGINT, @pct DECIMAL(9,2);

/* ---------------- Part 1: minimum volumes ---------------- */
IF (SELECT COUNT(*) FROM dbo.brands) < 20 THROW 51001, 'brands < 20', 1;
IF (SELECT COUNT(*) FROM dbo.categories) <> 8 THROW 51002, 'categories <> 8', 1;
IF (SELECT COUNT(*) FROM dbo.products) < 87 THROW 51003, 'products < 87', 1;
IF EXISTS (SELECT 1 FROM dbo.categories c WHERE (SELECT COUNT(*) FROM dbo.products p WHERE p.category_id = c.category_id) < 6)
    THROW 51004, 'a category has < 6 products', 1;
IF EXISTS (SELECT 1 FROM dbo.brands b WHERE (SELECT COUNT(*) FROM dbo.products p WHERE p.brand_id = b.brand_id) NOT BETWEEN 2 AND 8)
    THROW 51005, 'a brand does not have 2-8 products', 1;
IF (SELECT COUNT(*) FROM dbo.products WHERE status <> N'ACTIVE') < 4 THROW 51006, 'fewer than 4 INACTIVE/HIDDEN products', 1;
IF (SELECT COUNT(DISTINCT p.product_id) FROM dbo.products p
      JOIN dbo.store_products sp ON sp.product_id = p.product_id
      JOIN dbo.order_items oi ON oi.store_product_id = sp.store_product_id
     WHERE p.status <> N'ACTIVE') < 2 THROW 51007, 'fewer than 2 non-ACTIVE products with order history', 1;
IF (SELECT COUNT(*) FROM dbo.stores) < 9 THROW 51008, 'stores < 9', 1;
IF (SELECT COUNT(*) FROM dbo.stores WHERE status = N'INACTIVE') < 2 THROW 51009, 'fewer than 2 INACTIVE stores', 1;
IF (SELECT COUNT(DISTINCT CONCAT(delivery_enabled, '/', pickup_enabled)) FROM dbo.stores) < 3 THROW 51010, 'store delivery/pickup flags not mixed', 1;
IF (SELECT COUNT(*) FROM dbo.store_products) < 350 THROW 51011, 'store_products < 350', 1;

/* every ACTIVE large product: 3-7 stores, except the intentional single-store ones */
IF EXISTS (SELECT 1 FROM dbo.products p WHERE p.product_id >= 1000 AND p.status = N'ACTIVE'
             AND (SELECT COUNT(*) FROM dbo.store_products sp WHERE sp.product_id = p.product_id) NOT IN (1, 3, 4, 5, 6, 7))
    THROW 51012, 'an ACTIVE large product is not in 3-7 stores (or exactly 1)', 1;
IF (SELECT COUNT(*) FROM dbo.products p WHERE (SELECT COUNT(*) FROM dbo.store_products sp WHERE sp.product_id = p.product_id) = 1) < 5
    THROW 51013, 'fewer than 5 single-store products', 1;
IF (SELECT COUNT(*) FROM dbo.products p WHERE p.product_id >= 1000 AND p.status = N'ACTIVE'
       AND EXISTS (SELECT 1 FROM dbo.store_products a WHERE a.product_id = p.product_id)
       AND NOT EXISTS (SELECT 1 FROM dbo.store_products a WHERE a.product_id = p.product_id AND a.quantity > 0)) < 3
    THROW 51014, 'fewer than 3 products out of stock in every store', 1;
SET @n = (SELECT COUNT(*) FROM dbo.store_products WHERE store_product_id >= 1000);
IF (SELECT COUNT(*) FROM dbo.store_products WHERE store_product_id >= 1000 AND quantity = 0) < @n * 0.08 THROW 51015, 'zero-stock share too low', 1;
IF (SELECT COUNT(*) FROM dbo.store_products WHERE store_product_id >= 1000 AND quantity BETWEEN 1 AND 5) < @n * 0.10 THROW 51016, 'low-stock share too low', 1;
IF (SELECT COUNT(*) FROM dbo.store_products WHERE status = N'INACTIVE' AND product_id IN (SELECT product_id FROM dbo.products WHERE status = N'ACTIVE')) < 1
    THROW 51017, 'no INACTIVE store product of an ACTIVE product', 1;
/* price spread between stores is at most 8% (+ rounding) for large products */
IF EXISTS (SELECT 1 FROM dbo.store_products WHERE store_product_id >= 1000 GROUP BY product_id HAVING MAX(price) > MIN(price) * 1.09 + 1000)
    THROW 51018, 'price spread between stores above 8%', 1;

IF (SELECT COUNT(*) FROM dbo.users WHERE role_id = 1 AND email LIKE N'%@seed.oneshop.test') < 30 THROW 51020, 'seed customers < 30', 1;
IF (SELECT COUNT(*) FROM dbo.users WHERE role_id = 2 AND email LIKE N'%@seed.oneshop.test') < 12 THROW 51021, 'seed staff < 12', 1;
IF NOT EXISTS (SELECT 1 FROM dbo.users WHERE role_id = 3 AND email LIKE N'%@seed.oneshop.test') THROW 51022, 'no secondary admin', 1;
IF NOT EXISTS (SELECT 1 FROM dbo.users WHERE role_id = 1 AND status = N'INACTIVE') THROW 51023, 'no locked customer', 1;
IF (SELECT COUNT(*) FROM dbo.customer_addresses) < 40 THROW 51024, 'addresses < 40', 1;
IF NOT EXISTS (SELECT 1 FROM dbo.customer_addresses GROUP BY user_id HAVING COUNT(*) > 1) THROW 51025, 'no multi-address customer', 1;
IF EXISTS (SELECT 1 FROM dbo.stores s WHERE s.status = N'ACTIVE'
             AND NOT EXISTS (SELECT 1 FROM dbo.staff_store_assignments a JOIN dbo.users u ON u.user_id = a.user_id
                              WHERE a.store_id = s.store_id AND a.status = N'ACTIVE' AND u.role_id = 2))
    THROW 51026, 'an ACTIVE store has no staff', 1;
IF (SELECT COUNT(*) FROM (SELECT user_id FROM dbo.staff_store_assignments WHERE status = N'ACTIVE' GROUP BY user_id HAVING COUNT(*) >= 2) x) < 2
    THROW 51027, 'fewer than 2 staff with two stores', 1;
IF NOT EXISTS (SELECT 1 FROM dbo.staff_store_assignments WHERE status = N'INACTIVE' AND user_id >= 1000) THROW 51028, 'no revoked assignment', 1;
IF NOT EXISTS (SELECT 1 FROM dbo.users u WHERE u.role_id = 2 AND u.user_id >= 1000
                AND NOT EXISTS (SELECT 1 FROM dbo.staff_store_assignments a WHERE a.user_id = u.user_id)) THROW 51029, 'no unassigned staff', 1;
IF (SELECT COUNT(*) FROM (SELECT c.cart_id FROM dbo.carts c JOIN dbo.cart_items ci ON ci.cart_id = c.cart_id
                           JOIN dbo.store_products sp ON sp.store_product_id = ci.store_product_id
                          GROUP BY c.cart_id HAVING COUNT(DISTINCT sp.store_id) >= 2) x) < 8 THROW 51030, 'fewer than 8 multi-store carts', 1;

IF (SELECT COUNT(*) FROM dbo.checkout_sessions) < 90 THROW 51040, 'checkout_sessions < 90', 1;
SET @n = (SELECT COUNT(*) FROM dbo.orders);
IF @n < 150 THROW 51041, 'orders < 150', 1;
IF (SELECT COUNT(*) FROM (SELECT checkout_id FROM dbo.orders GROUP BY checkout_id
                           HAVING COUNT(*) >= 2 AND COUNT(DISTINCT store_id) = COUNT(*)
                              AND COUNT(DISTINCT CONCAT(fulfillment_type, '/', payment_method)) = COUNT(*)) x) < 25
    THROW 51042, 'fewer than 25 multi-order sessions with distinct store and fulfillment/payment', 1;
IF (SELECT MIN(created_at) FROM dbo.orders WHERE order_id >= 1000) > DATEADD(DAY, -110, SYSDATETIME()) THROW 51043, 'orders do not span ~120 days', 1;
SET @pct = (SELECT COUNT(*) FROM dbo.orders WHERE order_status = N'COMPLETED') * 100.0 / @n;
IF @pct NOT BETWEEN 49 AND 61 THROW 51044, 'COMPLETED share not ~55%', 1;
SET @pct = (SELECT COUNT(*) FROM dbo.orders WHERE order_status = N'CANCELLED') * 100.0 / @n;
IF @pct NOT BETWEEN 9 AND 15 THROW 51045, 'CANCELLED share not ~12%', 1;
SET @pct = (SELECT COUNT(*) FROM dbo.orders WHERE order_status = N'PENDING_PAYMENT') * 100.0 / @n;
IF @pct NOT BETWEEN 3 AND 7 THROW 51046, 'PENDING_PAYMENT share not ~5%', 1;
IF EXISTS (SELECT 1 FROM (VALUES (N'CONFIRMED'), (N'PREPARING'), (N'PACKED'), (N'SHIPPING'), (N'READY_FOR_PICKUP')) v(s)
            WHERE (SELECT COUNT(*) FROM dbo.orders o WHERE o.order_status = v.s) < 8
               OR (SELECT COUNT(DISTINCT o.store_id) FROM dbo.orders o WHERE o.order_status = v.s) < 3)
    THROW 51047, 'a status has < 8 orders or < 3 stores', 1;
IF NOT EXISTS (SELECT 1 FROM dbo.orders WHERE fulfillment_type = N'DELIVERY') OR NOT EXISTS (SELECT 1 FROM dbo.orders WHERE fulfillment_type = N'STORE_PICKUP')
    THROW 51048, 'fulfillment types not mixed', 1;
IF NOT EXISTS (SELECT 1 FROM dbo.orders WHERE payment_method = N'ONLINE' AND payment_status = N'FAILED' AND order_status = N'CANCELLED')
    THROW 51049, 'no cancelled ONLINE FAILED order', 1;
IF NOT EXISTS (SELECT 1 FROM dbo.orders GROUP BY store_id HAVING COUNT(*) >= 60) THROW 51050, 'no store with >= 60 orders', 1;
IF NOT EXISTS (SELECT 1 FROM dbo.orders o JOIN dbo.stores s ON s.store_id = o.store_id WHERE s.status = N'INACTIVE') THROW 51051, 'no history in INACTIVE store', 1;
IF NOT EXISTS (SELECT 1 FROM dbo.order_items oi JOIN dbo.store_products sp ON sp.store_product_id = oi.store_product_id
                WHERE oi.unit_price <> sp.price AND oi.order_item_id >= 1000) THROW 51052, 'no order item with snapshot price <> current price', 1;
IF (SELECT COUNT(*) FROM dbo.reviews) < 50 THROW 51060, 'reviews < 50', 1;
IF (SELECT COUNT(*) FROM dbo.reviews WHERE status = N'HIDDEN') < 6 THROW 51061, 'hidden reviews < 6', 1;
IF (SELECT COUNT(*) FROM dbo.reviews WHERE reply IS NOT NULL) < 8 THROW 51062, 'replied reviews < 8', 1;
SET @pct = (SELECT COUNT(*) FROM dbo.reviews WHERE rating >= 4) * 100.0 / (SELECT COUNT(*) FROM dbo.reviews);
IF @pct < 60 THROW 51063, 'ratings not skewed to 4-5', 1;
IF (SELECT COUNT(*) FROM dbo.inventory_movements WHERE type = N'STOCK_ADJUST' AND movement_id >= 1000) < 30 THROW 51064, 'STOCK_ADJUST < 30', 1;
IF NOT EXISTS (SELECT 1 FROM dbo.inventory_movements WHERE type = N'CANCEL_ORDER' AND movement_id >= 1000) THROW 51065, 'no CANCEL_ORDER movement', 1;
/* edge cases */
IF NOT EXISTS (SELECT 1 FROM dbo.products WHERE LEN(name) > 200) THROW 51070, 'no very long product name', 1;
IF NOT EXISTS (SELECT 1 FROM dbo.products WHERE name LIKE N'%&%') OR NOT EXISTS (SELECT 1 FROM dbo.products WHERE name LIKE N'%''%') THROW 51071, 'no & / quote product names', 1;
IF NOT EXISTS (SELECT 1 FROM dbo.products WHERE description LIKE N'%<script>%') THROW 51072, 'no HTML description edge case', 1;
GO

/* ---------------- Rules 1-9 (whole database) ---------------- */
DECLARE @bad BIGINT;

/* R1 stock chains: before = previous after, no negatives, last after = store_products.quantity */
;WITH mv AS (
    SELECT store_product_id, quantity_before, quantity_after, quantity_change, movement_id,
           LAG(quantity_after) OVER (PARTITION BY store_product_id ORDER BY created_at, movement_id) AS prev_after,
           ROW_NUMBER() OVER (PARTITION BY store_product_id ORDER BY created_at DESC, movement_id DESC) AS rn_desc
    FROM dbo.inventory_movements)
SELECT @bad = COUNT(*) FROM mv
WHERE (prev_after IS NOT NULL AND prev_after <> quantity_before) OR quantity_before < 0 OR quantity_after < 0;
IF @bad > 0 THROW 52001, 'R1: inventory movement chain breaks or goes negative', 1;
;WITH last_mv AS (
    SELECT store_product_id, quantity_after,
           ROW_NUMBER() OVER (PARTITION BY store_product_id ORDER BY created_at DESC, movement_id DESC) AS rn
    FROM dbo.inventory_movements)
SELECT @bad = COUNT(*) FROM last_mv l JOIN dbo.store_products sp ON sp.store_product_id = l.store_product_id
WHERE l.rn = 1 AND l.quantity_after <> sp.quantity;
IF @bad > 0 THROW 52002, 'R1: last movement quantity_after differs from store_products.quantity', 1;
IF EXISTS (SELECT 1 FROM dbo.store_products WHERE quantity < 0) THROW 52003, 'R1: negative stock', 1;
IF EXISTS (SELECT 1 FROM dbo.inventory_movements m WHERE m.type = N'STOCK_ADJUST' AND m.movement_id >= 1000
             AND NOT EXISTS (SELECT 1 FROM dbo.staff_store_assignments a JOIN dbo.store_products sp ON sp.store_id = a.store_id
                              WHERE a.user_id = m.staff_id AND sp.store_product_id = m.store_product_id))
    THROW 52004, 'R1: STOCK_ADJUST by staff of another store', 1;
IF EXISTS (SELECT 1 FROM dbo.inventory_movements WHERE note IS NULL OR LEN(LTRIM(RTRIM(note))) = 0) THROW 52005, 'R1: movement without note', 1;

/* R2 totals */
IF EXISTS (SELECT 1 FROM dbo.orders o WHERE o.total_amount <> (SELECT ISNULL(SUM(subtotal), 0) FROM dbo.order_items WHERE order_id = o.order_id))
    THROW 52010, 'R2: order total <> sum of items', 1;
IF EXISTS (SELECT 1 FROM dbo.orders o WHERE NOT EXISTS (SELECT 1 FROM dbo.order_items WHERE order_id = o.order_id)) THROW 52011, 'R2: order without items', 1;
IF EXISTS (SELECT 1 FROM dbo.checkout_sessions c WHERE c.total_amount <> (SELECT ISNULL(SUM(total_amount), 0) FROM dbo.orders WHERE checkout_id = c.checkout_id))
    THROW 52012, 'R2: checkout total <> sum of orders', 1;
IF EXISTS (SELECT 1 FROM dbo.order_items WHERE subtotal <> unit_price * quantity) THROW 52013, 'R2: subtotal <> unit_price * quantity', 1;

/* R3 one store per order, every item has a StoreProduct of that store */
IF EXISTS (SELECT 1 FROM dbo.order_items oi JOIN dbo.orders o ON o.order_id = oi.order_id
             JOIN dbo.store_products sp ON sp.store_product_id = oi.store_product_id WHERE sp.store_id <> o.store_id)
    THROW 52020, 'R3: order item from another store', 1;
IF EXISTS (SELECT 1 FROM dbo.orders o JOIN dbo.stores s ON s.store_id = o.store_id
            WHERE (o.fulfillment_type = N'DELIVERY' AND s.delivery_enabled = 0) OR (o.fulfillment_type = N'STORE_PICKUP' AND s.pickup_enabled = 0))
    THROW 52021, 'R3: order uses a fulfillment type the store does not support', 1;

/* R4 status history: chain, valid transitions, time order, last = current, valid actors */
DECLARE @tr TABLE (f NVARCHAR(20), o NVARCHAR(30), n NVARCHAR(30));
INSERT @tr VALUES
 (N'DELIVERY', N'PENDING_PAYMENT', N'CONFIRMED'), (N'DELIVERY', N'PENDING_PAYMENT', N'CANCELLED'), (N'DELIVERY', N'CONFIRMED', N'PREPARING'),
 (N'DELIVERY', N'CONFIRMED', N'CANCELLED'), (N'DELIVERY', N'PREPARING', N'PACKED'), (N'DELIVERY', N'PACKED', N'SHIPPING'), (N'DELIVERY', N'SHIPPING', N'COMPLETED'),
 (N'STORE_PICKUP', N'PENDING_PAYMENT', N'CONFIRMED'), (N'STORE_PICKUP', N'PENDING_PAYMENT', N'CANCELLED'), (N'STORE_PICKUP', N'CONFIRMED', N'PREPARING'),
 (N'STORE_PICKUP', N'CONFIRMED', N'CANCELLED'), (N'STORE_PICKUP', N'PREPARING', N'READY_FOR_PICKUP'), (N'STORE_PICKUP', N'READY_FOR_PICKUP', N'COMPLETED');
;WITH h AS (
    SELECT oh.*, o.fulfillment_type, o.payment_method, o.order_status, o.store_id, o.user_id AS cust,
           LAG(oh.new_status) OVER (PARTITION BY oh.order_id ORDER BY oh.changed_at, oh.history_id) AS prev_new,
           LAG(oh.changed_at) OVER (PARTITION BY oh.order_id ORDER BY oh.changed_at, oh.history_id) AS prev_at,
           ROW_NUMBER() OVER (PARTITION BY oh.order_id ORDER BY oh.changed_at, oh.history_id) AS rn
    FROM dbo.order_status_history oh JOIN dbo.orders o ON o.order_id = oh.order_id)
SELECT @bad = COUNT(*) FROM h
WHERE (rn = 1 AND old_status IS NOT NULL)
   OR (rn > 1 AND ISNULL(old_status, N'') <> prev_new)
   OR (rn > 1 AND NOT EXISTS (SELECT 1 FROM @tr t WHERE t.f = h.fulfillment_type AND t.o = h.old_status AND t.n = h.new_status))
   OR (rn > 1 AND changed_at < prev_at)
   OR (rn > 1 AND order_id >= 1000 AND changed_at <= prev_at);
IF @bad > 0 THROW 52030, 'R4: invalid status history chain / transition / time order', 1;
IF EXISTS (SELECT 1 FROM dbo.orders o WHERE ISNULL((SELECT TOP 1 new_status FROM dbo.order_status_history WHERE order_id = o.order_id ORDER BY changed_at DESC, history_id DESC), N'') <> o.order_status)
    THROW 52031, 'R4: order status differs from last history row', 1;
IF EXISTS (SELECT 1 FROM dbo.order_status_history oh JOIN dbo.orders o ON o.order_id = oh.order_id
            WHERE oh.order_id >= 1000 AND oh.changed_by_user_id IS NOT NULL
              AND NOT (oh.new_status = N'CANCELLED' AND oh.changed_by_user_id = o.user_id)
              AND NOT EXISTS (SELECT 1 FROM dbo.staff_store_assignments a WHERE a.user_id = oh.changed_by_user_id AND a.store_id = o.store_id))
    THROW 52032, 'R4: status changed by a user who is not staff of the order store', 1;
IF EXISTS (SELECT 1 FROM dbo.order_status_history WHERE order_id >= 1000 AND old_status IS NULL AND new_status NOT IN (N'PENDING_PAYMENT', N'CONFIRMED'))
    THROW 52033, 'R4: invalid first history status', 1;

/* R5 payments */
IF EXISTS (SELECT 1 FROM dbo.payments p JOIN dbo.orders o ON o.order_id = p.order_id WHERE p.amount <> o.total_amount OR p.method <> o.payment_method)
    THROW 52040, 'R5: payment amount/method differs from order', 1;
IF EXISTS (SELECT 1 FROM dbo.orders o WHERE (SELECT COUNT(*) FROM dbo.payments WHERE order_id = o.order_id) <> 1) THROW 52041, 'R5: order without exactly one payment', 1;
IF EXISTS (SELECT 1 FROM dbo.orders o JOIN dbo.payments p ON p.order_id = o.order_id
            WHERE o.payment_method = N'ONLINE'
              AND (  (p.status = N'SUCCESS' AND o.order_status IN (N'PENDING_PAYMENT', N'CANCELLED'))
                  OR (p.status <> N'SUCCESS' AND o.order_status NOT IN (N'PENDING_PAYMENT', N'CANCELLED'))
                  OR (p.status = N'FAILED' AND o.order_status <> N'CANCELLED')
                  OR (o.order_status = N'PENDING_PAYMENT' AND p.status <> N'PENDING')))
    THROW 52042, 'R5: ONLINE payment/order status mismatch', 1;
IF EXISTS (SELECT 1 FROM dbo.orders o JOIN dbo.payments p ON p.order_id = o.order_id
            WHERE o.payment_method <> N'ONLINE' AND (CASE WHEN o.order_status = N'COMPLETED' THEN 1 ELSE 0 END <> CASE WHEN p.status = N'SUCCESS' THEN 1 ELSE 0 END))
    THROW 52043, 'R5: COD / PAY_AT_STORE paid state mismatch', 1;
IF EXISTS (SELECT 1 FROM dbo.orders o JOIN dbo.payments p ON p.order_id = o.order_id WHERE CASE WHEN p.status = N'SUCCESS' THEN 1 ELSE 0 END <> CASE WHEN o.payment_status = N'PAID' THEN 1 ELSE 0 END)
    THROW 52044, 'R5: orders.payment_status <> payments.status', 1;
IF EXISTS (SELECT 1 FROM dbo.orders WHERE payment_status = N'FAILED' AND NOT (payment_method = N'ONLINE' AND order_status = N'CANCELLED'))
    THROW 52045, 'R5: FAILED payment status on a live order', 1;
IF EXISTS (SELECT 1 FROM dbo.orders WHERE payment_method = N'PAY_AT_STORE' AND fulfillment_type <> N'STORE_PICKUP') THROW 52046, 'R5: PAY_AT_STORE on delivery', 1;

/* R6 pickup codes */
IF EXISTS (SELECT 1 FROM dbo.orders WHERE fulfillment_type = N'STORE_PICKUP' AND order_status = N'READY_FOR_PICKUP' AND (pickup_code IS NULL OR ready_at IS NULL))
    THROW 52050, 'R6: READY_FOR_PICKUP without code / ready_at', 1;
IF EXISTS (SELECT 1 FROM dbo.orders WHERE order_status IN (N'PENDING_PAYMENT', N'CONFIRMED', N'PREPARING', N'CANCELLED') AND (pickup_code IS NOT NULL OR ready_at IS NOT NULL OR picked_up_at IS NOT NULL))
    THROW 52051, 'R6: pickup data before READY_FOR_PICKUP', 1;
IF EXISTS (SELECT 1 FROM dbo.orders WHERE pickup_code IS NOT NULL GROUP BY store_id, pickup_code HAVING COUNT(*) > 1) THROW 52052, 'R6: duplicate pickup_code in a store', 1;
IF EXISTS (SELECT 1 FROM dbo.orders WHERE fulfillment_type = N'STORE_PICKUP' AND order_status = N'COMPLETED' AND (picked_up_at IS NULL OR ready_at IS NULL OR picked_up_at < ready_at))
    THROW 52053, 'R6: COMPLETED pickup without consistent ready_at / picked_up_at', 1;
IF EXISTS (SELECT 1 FROM dbo.orders WHERE pickup_code IS NOT NULL AND LEN(pickup_code) NOT IN (6, 8)) THROW 52054, 'R6: pickup_code format', 1;

/* R7 ORDER movements = items; CANCEL movements restore exactly the cancelled orders */
IF EXISTS (SELECT 1 FROM (
        SELECT oi.order_id, oi.store_product_id, SUM(oi.quantity) AS q FROM dbo.order_items oi GROUP BY oi.order_id, oi.store_product_id) i
    LEFT JOIN (SELECT reference_order_id AS order_id, store_product_id, SUM(-quantity_change) AS q FROM dbo.inventory_movements WHERE type = N'ORDER'
               GROUP BY reference_order_id, store_product_id) m
      ON m.order_id = i.order_id AND m.store_product_id = i.store_product_id
    WHERE ISNULL(m.q, -1) <> i.q)
    THROW 52060, 'R7: ORDER movements differ from order items', 1;
IF EXISTS (SELECT 1 FROM (
        SELECT oi.order_id, oi.store_product_id, SUM(oi.quantity) AS q FROM dbo.order_items oi JOIN dbo.orders o ON o.order_id = oi.order_id
         WHERE o.order_status = N'CANCELLED' GROUP BY oi.order_id, oi.store_product_id) i
    LEFT JOIN (SELECT reference_order_id AS order_id, store_product_id, SUM(quantity_change) AS q FROM dbo.inventory_movements WHERE type = N'CANCEL_ORDER'
               GROUP BY reference_order_id, store_product_id) m
      ON m.order_id = i.order_id AND m.store_product_id = i.store_product_id
    WHERE ISNULL(m.q, -1) <> i.q)
    THROW 52061, 'R7: cancelled order not restored exactly', 1;
IF EXISTS (SELECT 1 FROM dbo.inventory_movements m JOIN dbo.orders o ON o.order_id = m.reference_order_id WHERE m.type = N'CANCEL_ORDER' AND o.order_status <> N'CANCELLED')
    THROW 52062, 'R7: CANCEL_ORDER for a non-cancelled order', 1;

/* R8 reviews */
IF EXISTS (SELECT 1 FROM dbo.reviews r JOIN dbo.orders o ON o.order_id = r.order_id
            WHERE o.order_status <> N'COMPLETED' OR o.user_id <> r.user_id
               OR NOT EXISTS (SELECT 1 FROM dbo.order_items oi JOIN dbo.store_products sp ON sp.store_product_id = oi.store_product_id
                               WHERE oi.order_id = o.order_id AND sp.product_id = r.product_id))
    THROW 52070, 'R8: review not backed by a COMPLETED order of the customer containing the product', 1;
IF EXISTS (SELECT 1 FROM dbo.reviews WHERE review_id >= 1000 AND order_id IS NULL) THROW 52071, 'R8: seed review without order_id', 1;
IF EXISTS (SELECT 1 FROM dbo.reviews r JOIN dbo.orders o ON o.order_id = r.order_id WHERE r.created_at < o.updated_at) THROW 52072, 'R8: review before order completion', 1;
IF EXISTS (SELECT 1 FROM dbo.reviews WHERE reply IS NOT NULL AND replied_at < created_at) THROW 52073, 'R8: reply before review', 1;

/* R9 no future times; updated >= created */
DECLARE @now DATETIME2(0) = DATEADD(MINUTE, 1, SYSDATETIME());
IF EXISTS (SELECT 1 FROM dbo.orders WHERE created_at > @now OR updated_at > @now OR updated_at < created_at OR ready_at > @now OR picked_up_at > @now)
    THROW 52080, 'R9: order time in the future or updated_at < created_at', 1;
IF EXISTS (SELECT 1 FROM dbo.inventory_movements WHERE created_at > @now) OR EXISTS (SELECT 1 FROM dbo.order_status_history WHERE changed_at > @now)
   OR EXISTS (SELECT 1 FROM dbo.payments WHERE created_at > @now OR paid_at > @now) OR EXISTS (SELECT 1 FROM dbo.reviews WHERE created_at > @now OR replied_at > @now)
   OR EXISTS (SELECT 1 FROM dbo.checkout_sessions WHERE created_at > @now) OR EXISTS (SELECT 1 FROM dbo.users WHERE created_at > @now OR updated_at > @now)
    THROW 52081, 'R9: a time is in the future', 1;
IF EXISTS (SELECT 1 FROM dbo.users WHERE updated_at < created_at) OR EXISTS (SELECT 1 FROM dbo.store_products WHERE updated_at > @now)
    THROW 52082, 'R9: users.updated_at < created_at', 1;
GO

/* ---------------- Images (Cloudinary) ---------------- */
BEGIN
    IF EXISTS (SELECT 1 FROM dbo.brands WHERE logo_url IS NULL OR logo_url NOT LIKE N'https://res.cloudinary.com/%' OR logo_public_id IS NULL)
        THROW 53001, 'a brand logo is not a Cloudinary image with public_id', 1;
    IF EXISTS (SELECT 1 FROM dbo.categories WHERE image_url IS NULL OR image_url NOT LIKE N'https://res.cloudinary.com/%' OR image_public_id IS NULL)
        THROW 53002, 'a category image is not a Cloudinary image with public_id', 1;
    IF EXISTS (SELECT 1 FROM dbo.product_images WHERE image_url NOT LIKE N'https://res.cloudinary.com/%' OR public_id IS NULL OR LEN(public_id) = 0)
        THROW 53003, 'a product image is not a Cloudinary image with public_id', 1;
    IF EXISTS (SELECT 1 FROM dbo.products p WHERE p.status = N'ACTIVE'
                AND NOT EXISTS (SELECT 1 FROM dbo.product_images i WHERE i.product_id = p.product_id AND i.is_primary = 1))
        THROW 53004, 'an ACTIVE product has no primary image', 1;
    IF EXISTS (SELECT 1 FROM dbo.product_images WHERE is_primary = 1 GROUP BY product_id HAVING COUNT(*) > 1) THROW 53005, 'a product has 2 primary images', 1;
    IF EXISTS (SELECT 1 FROM (
            SELECT public_id AS pid, image_url AS url FROM dbo.product_images
            UNION ALL SELECT logo_public_id, logo_url FROM dbo.brands WHERE logo_public_id IS NOT NULL
            UNION ALL SELECT image_public_id, image_url FROM dbo.categories WHERE image_public_id IS NOT NULL) x
        GROUP BY pid HAVING COUNT(*) > 1)
        THROW 53006, 'duplicate public_id across images', 1;
    IF EXISTS (SELECT 1 FROM (
            SELECT image_url AS url FROM dbo.product_images
            UNION ALL SELECT logo_url FROM dbo.brands WHERE logo_url IS NOT NULL
            UNION ALL SELECT image_url FROM dbo.categories WHERE image_url IS NOT NULL) x
        GROUP BY url HAVING COUNT(*) > 1)
        THROW 53007, 'duplicate image URL', 1;
END
GO

PRINT 'All large-seed checks passed.';
GO
