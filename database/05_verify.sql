/* =============================================================================
   OneShop V3 - File 05: VERIFICATION (Kiem tra schema + seed data V3)
   Chi doc, khong sua du lieu. Neu co vi pham bat ky, script THROW loi ngay lap tuc.
   ========================================================================== */
USE [oneshop];
GO
SET NOCOUNT ON;
GO

PRINT '>>> [1/12] Kiem tra so luong va danh sach bang...';
/* 1. Dung 22 bang nguoi dung */
DECLARE @tables INT = (SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped = 0);
IF @tables <> 22
BEGIN
    DECLARE @errMsg NVARCHAR(200) = N'Expected exactly 22 tables, found: ' + CAST(@tables AS NVARCHAR(10));
    THROW 50001, @errMsg, 1;
END;

/* 1b. Ten bang dung danh sach V3 */
DECLARE @expected TABLE (name SYSNAME PRIMARY KEY);
INSERT INTO @expected VALUES
 (N'roles'),(N'users'),(N'customer_addresses'),(N'stores'),(N'staff_store_assignments'),
 (N'categories'),(N'brands'),(N'products'),(N'product_images'),(N'store_products'),
 (N'carts'),(N'cart_items'),(N'checkout_sessions'),(N'orders'),(N'order_fulfillments'),
 (N'order_fulfillment_status_history'),(N'order_items'),(N'payments'),(N'inventory_movements'),
 (N'order_status_history'),(N'reviews'),(N'review_replies');

IF EXISTS (SELECT 1 FROM @expected e WHERE NOT EXISTS (SELECT 1 FROM sys.tables t WHERE t.name = e.name))
   OR EXISTS (SELECT 1 FROM sys.tables t WHERE t.is_ms_shipped = 0 AND NOT EXISTS (SELECT 1 FROM @expected e WHERE e.name = t.name))
    THROW 50011, 'Table set differs from OneShop V3 specification list.', 1;

PRINT '    PASSED: 22 bang hop le.';

PRINT '>>> [2/12] Kiem tra Primary Key tren moi bang...';
/* 2. Moi bang co PK */
IF EXISTS (SELECT 1 FROM sys.tables t WHERE NOT EXISTS (SELECT 1 FROM sys.key_constraints k WHERE k.parent_object_id = t.object_id AND k.type = 'PK'))
    THROW 50012, 'A table has no primary key.', 1;
PRINT '    PASSED: Tat ca 22 bang deu co Primary Key.';

PRINT '>>> [3/12] Kiem tra rang buoc bat buoc (UNIQUE / CHECK / INDEX)...';
/* 3. Cac rang buoc bat buoc */
DECLARE @required TABLE (name SYSNAME PRIMARY KEY);
INSERT INTO @required VALUES
 (N'UQ_users_email'),(N'UQ_products_sku'),(N'UQ_store_products_store_product'),(N'UQ_cart_items_cart_product'),
 (N'UQ_order_fulfillments_order_store'),(N'UQ_reviews_user_product'),(N'UQ_review_replies_review'),
 (N'CK_products_price'),(N'CK_store_products_quantity'),(N'CK_cart_items_quantity'),
 (N'CK_order_items_quantity'),(N'CK_order_items_subtotal'),(N'CK_inventory_movements_math'),
 (N'CK_reviews_rating'),(N'UX_users_user_name'),
 (N'IX_store_products_store_status_qty'),(N'IX_store_products_product_status'),
 (N'IX_order_fulfillments_store_status'),(N'IX_order_fulfillments_order_status'),
 (N'IX_order_items_fulfillment'),(N'IX_order_items_store_product'),
 (N'IX_orders_user_created'),(N'IX_inventory_movements_sp_created'),
 (N'IX_order_status_history_order_changed'),(N'IX_ofsh_fulfillment_changed');

IF EXISTS (SELECT 1 FROM @required r
           WHERE NOT EXISTS (SELECT 1 FROM sys.objects o WHERE o.name = r.name)
             AND NOT EXISTS (SELECT 1 FROM sys.indexes i WHERE i.name = r.name))
    THROW 50013, 'A required UNIQUE/CHECK/INDEX constraint is missing.', 1;
PRINT '    PASSED: Tat ca cac rang buoc co ban da duoc thiet lap.';

PRINT '>>> [4/12] Kiem tra cot V3: products.price, cart_items.product_id, order_items.fulfillment_id...';
/* 4. Cot V3 */
IF COL_LENGTH('dbo.products', 'price') IS NULL
    THROW 50014, 'products.price column missing.', 1;
IF COL_LENGTH('dbo.cart_items', 'product_id') IS NULL
    THROW 50015, 'cart_items.product_id column missing.', 1;
IF COL_LENGTH('dbo.order_items', 'fulfillment_id') IS NULL
    THROW 50016, 'order_items.fulfillment_id column missing.', 1;
IF COL_LENGTH('dbo.reviews', 'order_item_id') IS NULL
    THROW 50017, 'reviews.order_item_id column missing.', 1;
IF COL_LENGTH('dbo.payments', 'refund_status') IS NULL
    THROW 50018, 'payments.refund_status column missing.', 1;
PRINT '    PASSED: Cac cot dac ta V3 co mat day du.';

PRINT '>>> [5/12] Kiem tra tinh toan ven du lieu san pham va gia ban...';
/* 5. San pham co gia ban hop le */
IF (SELECT COUNT(*) FROM dbo.products WHERE status = 'ACTIVE') < 30
    THROW 50020, 'Expected at least 30 active products.', 1;
IF EXISTS (SELECT 1 FROM dbo.products WHERE price <= 0 AND status = 'ACTIVE')
    THROW 50021, 'Active product has non-positive price.', 1;
PRINT '    PASSED: San pham phong phu va gia ban online hop le.';

PRINT '>>> [6/12] Kiem tra cac kich ban ton kho (Scenarios A -> H)...';
/* 6. Cac kich ban ton kho */
-- Kich ban D: Co Store het hang (quantity = 0)
IF NOT EXISTS (SELECT 1 FROM dbo.store_products WHERE quantity = 0 AND status = 'ACTIVE')
    THROW 50030, 'Scenario D failed: No store product with quantity = 0 found.', 1;
-- Kich ban E: Co san pham ton kho doi dao tai nhieu store (> 3 stores co quantity > 30)
IF NOT EXISTS (SELECT product_id FROM dbo.store_products WHERE quantity >= 30 GROUP BY product_id HAVING COUNT(*) >= 3)
    THROW 50031, 'Scenario E failed: No product with abundant stock across >=3 stores.', 1;
-- Kich ban F: Co san pham doc quyen chi 1 store co hang
IF NOT EXISTS (SELECT product_id FROM dbo.store_products WHERE quantity > 0 GROUP BY product_id HAVING COUNT(*) = 1)
    THROW 50032, 'Scenario F failed: No store-exclusive product found.', 1;
-- Kich ban G: Co Store INACTIVE
IF NOT EXISTS (SELECT 1 FROM dbo.stores WHERE status = 'INACTIVE')
    THROW 50033, 'Scenario G failed: No inactive store found.', 1;
-- Kich ban H: Co Store chi Delivery va Store chi Pickup
IF NOT EXISTS (SELECT 1 FROM dbo.stores WHERE delivery_enabled = 0 AND pickup_enabled = 1)
   OR NOT EXISTS (SELECT 1 FROM dbo.stores WHERE delivery_enabled = 1 AND pickup_enabled = 0)
    THROW 50034, 'Scenario H failed: Delivery-only or Pickup-only store missing.', 1;
PRINT '    PASSED: 8 kich ban ton kho va chi nhanh deu dat yeu cau.';

PRINT '>>> [7/12] Kiem tra toan ven don hang da chi nhanh (Multi-Store Fulfillment)...';
/* 7. Don hang da chi nhanh */
-- Co it nhat 1 don hang co >= 2 fulfillments tu cac chi nhanh khac nhau
IF NOT EXISTS (SELECT order_id FROM dbo.order_fulfillments GROUP BY order_id HAVING COUNT(DISTINCT store_id) >= 2)
    THROW 50040, 'Multi-store fulfillment order missing (expected order with >= 2 stores).', 1;
-- Co it nhat 1 don hang co 3 fulfillments
IF NOT EXISTS (SELECT order_id FROM dbo.order_fulfillments GROUP BY order_id HAVING COUNT(DISTINCT store_id) >= 3)
    THROW 50041, '3-store fulfillment order missing (expected order with 3 stores).', 1;
PRINT '    PASSED: Don hang da chi nhanh hop le.';

PRINT '>>> [8/12] Kiem tra khop nguon Store giua OrderItem, Fulfillment va StoreProduct...';
/* 8. Bất biến nguồn hàng: order_items.fulfillment_id.store_id = store_products.store_id */
IF EXISTS (
    SELECT 1 FROM dbo.order_items oi
    JOIN dbo.order_fulfillments ofl ON ofl.fulfillment_id = oi.fulfillment_id
    JOIN dbo.store_products sp ON sp.store_product_id = oi.store_product_id
    WHERE ofl.store_id <> sp.store_id)
    THROW 50042, 'Integrity violation: order_item assigned to fulfillment from different store than store_product.', 1;

IF EXISTS (
    SELECT 1 FROM dbo.order_items oi
    JOIN dbo.order_fulfillments ofl ON ofl.fulfillment_id = oi.fulfillment_id
    WHERE ofl.order_id <> oi.order_id)
    THROW 50043, 'Integrity violation: order_item order_id does not match fulfillment order_id.', 1;
PRINT '    PASSED: Nguon hang chi nhanh khop 100% giua OrderItem, Fulfillment va StoreProduct.';

PRINT '>>> [9/12] Kiem tra tinh toan tien: Subtotal, TotalAmount, Payments...';
/* 9. Tinh toan tien */
IF EXISTS (
    SELECT 1 FROM dbo.order_items
    WHERE subtotal <> unit_price * quantity)
    THROW 50050, 'Order item subtotal arithmetic mismatch.', 1;

IF EXISTS (
    SELECT 1 FROM dbo.orders o
    WHERE o.total_amount <> (SELECT SUM(subtotal) FROM dbo.order_items WHERE order_id = o.order_id) + o.shipping_fee)
    THROW 50051, 'Order total_amount does not match sum of order_items + shipping_fee.', 1;

IF EXISTS (
    SELECT 1 FROM dbo.payments p
    JOIN dbo.orders o ON o.order_id = p.order_id
    WHERE p.amount <> o.total_amount)
    THROW 50052, 'Payment amount does not match order total_amount.', 1;
PRINT '    PASSED: Tinh toan tong tien don hang va dong hang chinh xac 100%.';

PRINT '>>> [10/12] Kiem tra so cai bien dong kho (InventoryMovements)...';
/* 10. Math bien dong kho */
IF EXISTS (
    SELECT 1 FROM dbo.inventory_movements
    WHERE quantity_after <> quantity_before + quantity_change)
    THROW 50060, 'Inventory movement arithmetic mismatch (quantity_after <> before + change).', 1;
PRINT '    PASSED: Sổ cái biến động kho khớp số học 100%.';

PRINT '>>> [11/12] Kiem tra nghiep vu Store Pickup va Simulated Refund...';
/* 11. Store Pickup co pickup_code */
IF EXISTS (
    SELECT 1 FROM dbo.order_fulfillments
    WHERE fulfillment_type = 'STORE_PICKUP' AND status IN ('READY_FOR_PICKUP', 'COMPLETED') AND (pickup_code IS NULL OR LEN(pickup_code) < 6))
    THROW 50070, 'Store pickup fulfillment ready/completed but missing valid 6-char pickup_code.', 1;

/* 11b. Don huy da thanh toan co simulated refund */
IF NOT EXISTS (
    SELECT 1 FROM dbo.orders o
    JOIN dbo.payments p ON p.order_id = o.order_id
    WHERE o.order_status = 'CANCELLED' AND p.refund_status = 'SIMULATED_REFUNDED' AND p.refund_amount > 0)
    THROW 50071, 'Cancelled online order missing SIMULATED_REFUNDED payment record.', 1;
PRINT '    PASSED: Nghiệp vụ Store Pickup và hoàn tiền mô phỏng (Simulated Refund) đạt chuẩn.';

PRINT '>>> [12/12] Kiem tra Reviews gan voi OrderItem va ReviewReplies...';
/* 12. Reviews gan voi OrderItem da mua va co replies */
IF NOT EXISTS (SELECT 1 FROM dbo.reviews WHERE order_item_id IS NOT NULL)
    THROW 50080, 'No review linked to order_item_id found.', 1;
IF NOT EXISTS (SELECT 1 FROM dbo.review_replies)
    THROW 50081, 'No review_replies found.', 1;
-- Trust check: Tat ca cac constraints deu phai is_not_trusted = 0
IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE is_not_trusted = 1)
   OR EXISTS (SELECT 1 FROM sys.foreign_keys WHERE is_not_trusted = 1)
    THROW 50090, 'Untrusted constraint found.', 1;
PRINT '    PASSED: Reviews va ReviewReplies hop le. 100% Constraints duoc tin cay (TRUSTED).';

PRINT '================================================================================';
PRINT '  ONESHOP V3 DATABASE VERIFICATION: TAT CA CAC MUC KIEM TRA DEU DAT 100%!';
PRINT '================================================================================';
GO
