/* Read-only validation for the V3 demo seed. Run after database/02_seed.sql.
   This file never modifies data or schema. Any failed invariant throws so the
   reset/seed wrapper can roll back its outer transaction. */
SET NOCOUNT ON;
SET XACT_ABORT ON;

SELECT t.name AS table_name, SUM(p.rows) AS row_count
FROM sys.tables t
JOIN sys.partitions p ON p.object_id = t.object_id AND p.index_id IN (0, 1)
WHERE t.schema_id = SCHEMA_ID(N'dbo')
GROUP BY t.name
ORDER BY t.name;

IF (SELECT COUNT(*) FROM dbo.products WHERE status = N'ACTIVE') < 20
    THROW 51101, 'Expected at least 20 active catalog products.', 1;
IF EXISTS (
    SELECT 1 FROM dbo.products p
    WHERE p.status = N'ACTIVE'
      AND (p.price <= 0 OR NOT EXISTS (SELECT 1 FROM dbo.brands b WHERE b.brand_id = p.brand_id)
           OR NOT EXISTS (SELECT 1 FROM dbo.categories c WHERE c.category_id = p.category_id))
)
    THROW 51102, 'An active product has an invalid price, Brand or Category.', 1;
IF EXISTS (
    SELECT 1 FROM dbo.products p
    WHERE p.status = N'ACTIVE'
      AND (SELECT COUNT(*) FROM dbo.product_images i
           WHERE i.product_id = p.product_id AND i.is_primary = 1) <> 1
)
    THROW 51103, 'Every active Product must have exactly one primary image.', 1;
IF EXISTS (
    SELECT 1 FROM dbo.product_images
    WHERE image_url NOT LIKE N'https://res.cloudinary.com/%'
       OR public_id NOT LIKE N'oneshop/seed/products/%'
       OR image_url NOT LIKE N'%' + public_id + N'%'
)
    THROW 51104, 'A product image is outside the expected Cloudinary namespace.', 1;
IF NOT EXISTS (
    SELECT 1 FROM dbo.products p
    WHERE p.status = N'ACTIVE'
      AND EXISTS (SELECT 1 FROM dbo.store_products sp WHERE sp.product_id = p.product_id)
)
    THROW 51105, 'No active product is offered by any StoreProduct.', 1;
IF EXISTS (
    SELECT 1 FROM dbo.store_products
    WHERE quantity < 0 OR reserved_quantity < 0 OR reserved_quantity > quantity
)
    THROW 51106, 'StoreProduct quantity/reserved quantity is inconsistent.', 1;
IF EXISTS (
    SELECT 1 FROM dbo.staff_store_assignments
    WHERE status = N'ACTIVE'
    GROUP BY user_id HAVING COUNT(*) > 1
)
    THROW 51107, 'A Staff user has multiple ACTIVE Store assignments.', 1;
IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE is_not_trusted = 1)
   OR EXISTS (SELECT 1 FROM sys.foreign_keys WHERE is_not_trusted = 1)
   OR EXISTS (SELECT 1 FROM sys.check_constraints WHERE is_disabled = 1)
   OR EXISTS (SELECT 1 FROM sys.foreign_keys WHERE is_disabled = 1)
    THROW 51108, 'The database contains an untrusted CHECK or FOREIGN KEY.', 1;

IF EXISTS (
    SELECT 1 FROM dbo.order_items oi
    JOIN dbo.order_fulfillments f ON f.fulfillment_id = oi.fulfillment_id
    JOIN dbo.store_products sp ON sp.store_product_id = oi.store_product_id
    WHERE f.order_id <> oi.order_id OR f.store_id <> sp.store_id
)
    THROW 51109, 'OrderItem Order/Fulfillment/StoreProduct references disagree.', 1;
IF EXISTS (SELECT 1 FROM dbo.order_items WHERE subtotal <> unit_price * quantity
           OR discount_amount < 0 OR discount_amount > subtotal)
    THROW 51110, 'OrderItem subtotal or discount is inconsistent.', 1;
IF EXISTS (
    SELECT 1 FROM dbo.orders o
    WHERE o.total_amount <> (SELECT COALESCE(SUM(oi.subtotal), 0) FROM dbo.order_items oi WHERE oi.order_id = o.order_id)
                              - o.discount_amount + o.shipping_fee
)
    THROW 51111, 'Order total does not match its items, discount and shipping fee.', 1;
IF EXISTS (
    SELECT 1 FROM dbo.orders
    WHERE (fulfillment_type = N'DELIVERY' AND shipping_fee <> 30000)
       OR (fulfillment_type = N'STORE_PICKUP' AND shipping_fee <> 0)
)
    THROW 51112, 'Order shipping fee does not match the active checkout policy.', 1;
IF EXISTS (
    SELECT 1 FROM dbo.payments p
    JOIN dbo.orders o ON o.order_id = p.order_id
    WHERE p.amount <> o.total_amount
)
    THROW 51113, 'Payment amount does not match its Order total.', 1;
IF EXISTS (
    SELECT 1 FROM dbo.payment_refunds r
    JOIN dbo.payments p ON p.payment_id = r.payment_id
    WHERE r.status <> N'SIMULATION_FAILED' AND r.amount > p.amount
)
    THROW 51114, 'A simulated refund exceeds the associated Payment amount.', 1;
IF EXISTS (
    SELECT 1 FROM dbo.inventory_movements
    WHERE quantity_before < 0 OR quantity_after < 0
       OR quantity_after <> quantity_before + quantity_change
)
    THROW 51115, 'An InventoryMovement has invalid quantities or arithmetic.', 1;
IF EXISTS (
    SELECT 1 FROM dbo.store_products sp
    WHERE EXISTS (SELECT 1 FROM dbo.inventory_movements m WHERE m.store_product_id = sp.store_product_id)
      AND sp.quantity <> (
          SELECT TOP (1) m.quantity_after FROM dbo.inventory_movements m
          WHERE m.store_product_id = sp.store_product_id ORDER BY m.movement_id DESC
      )
)
    THROW 51116, 'StoreProduct quantity differs from its latest InventoryMovement.', 1;
IF EXISTS (
    SELECT 1 FROM dbo.reviews r
    JOIN dbo.order_items oi ON oi.order_item_id = r.order_item_id
    WHERE r.product_id <> (SELECT sp.product_id FROM dbo.store_products sp WHERE sp.store_product_id = oi.store_product_id)
       OR r.order_id <> oi.order_id
)
    THROW 51117, 'Review OrderItem/Product references disagree.', 1;
IF EXISTS (
    SELECT 1 FROM dbo.payments
    WHERE transaction_code IS NOT NULL AND
      ((method=N'ONLINE' AND transaction_code NOT LIKE N'MOCK-%') OR
       (method IN(N'COD',N'PAY_AT_STORE') AND transaction_code NOT LIKE N'PAY-%'))
)
    THROW 51118, 'A seeded Payment contains a non-mock transaction reference.', 1;

-- Inspect actual CHECK and FK contents, rather than relying on trusted constraint metadata alone.
CREATE TABLE #constraint_violations ([Table] nvarchar(512), [Constraint] nvarchar(512), [Where] nvarchar(max));
INSERT INTO #constraint_violations EXEC(N'DBCC CHECKCONSTRAINTS WITH ALL_CONSTRAINTS, ALL_ERRORMSGS');
IF EXISTS (SELECT 1 FROM #constraint_violations)
    THROW 51119, 'DBCC CHECKCONSTRAINTS found a CHECK/FK violation.', 1;
DROP TABLE #constraint_violations;
IF EXISTS (SELECT sku FROM dbo.products GROUP BY sku HAVING COUNT(*)>1)
 OR EXISTS (SELECT store_id,product_id FROM dbo.store_products GROUP BY store_id,product_id HAVING COUNT(*)>1)
 OR EXISTS (SELECT cart_id,product_id FROM dbo.cart_items GROUP BY cart_id,product_id HAVING COUNT(*)>1)
    THROW 51120, 'Duplicate SKU, StoreProduct or CartItem found.', 1;
IF EXISTS (SELECT 1 FROM dbo.staff_store_assignments a JOIN dbo.users u ON u.user_id=a.user_id
           JOIN dbo.roles r ON r.role_id=u.role_id WHERE r.name<>N'STAFF')
 OR EXISTS (SELECT 1 FROM dbo.staff_store_assignments a JOIN dbo.users u ON u.user_id=a.user_id
            JOIN dbo.stores s ON s.store_id=a.store_id WHERE a.status=N'ACTIVE' AND (s.status<>N'ACTIVE' OR u.status<>N'ACTIVE'))
 OR EXISTS (SELECT 1 FROM dbo.users WHERE email NOT LIKE N'%@example.com' OR password_hash NOT LIKE N'$2%$%')
    THROW 51121, 'Demo account email, PasswordEncoder hash or Staff role is invalid.', 1;
IF EXISTS (SELECT 1 FROM dbo.reviews r JOIN dbo.order_items i ON i.order_item_id=r.order_item_id
           JOIN dbo.orders o ON o.order_id=i.order_id JOIN dbo.order_fulfillments f ON f.fulfillment_id=i.fulfillment_id
           WHERE r.user_id<>o.user_id OR o.payment_status<>N'PAID' OR f.status<>N'COMPLETED' OR r.rating NOT BETWEEN 1 AND 5)
    THROW 51122, 'Review lacks a received and paid purchase by its owner.', 1;
IF EXISTS (SELECT 1 FROM dbo.review_replies reply JOIN dbo.reviews r ON r.review_id=reply.review_id
           JOIN dbo.order_items i ON i.order_item_id=r.order_item_id JOIN dbo.store_products sp ON sp.store_product_id=i.store_product_id
           WHERE NOT EXISTS(SELECT 1 FROM dbo.staff_store_assignments a
                            WHERE a.user_id=reply.staff_user_id AND a.store_id=sp.store_id AND a.status=N'ACTIVE'))
    THROW 51123, 'Review reply is outside the Staff source Store.', 1;
IF EXISTS (SELECT 1 FROM dbo.orders o WHERE o.discount_amount<>(SELECT COALESCE(SUM(discount_amount),0) FROM dbo.order_items i WHERE i.order_id=o.order_id))
 OR EXISTS (SELECT 1 FROM dbo.checkout_sessions c WHERE c.total_amount<>(SELECT COALESCE(SUM(total_amount),0) FROM dbo.orders o WHERE o.checkout_id=c.checkout_id))
    THROW 51124, 'Discount or checkout totals do not match their children.', 1;

-- Every stock ledger is continuous; the first opening balance starts at zero.
IF EXISTS (SELECT 1 FROM (
    SELECT quantity_before,quantity_after,LAG(quantity_after,1,0) OVER(PARTITION BY store_product_id ORDER BY movement_id) previous_after
    FROM dbo.inventory_movements) ledger WHERE quantity_before<>previous_after)
    THROW 51125, 'Inventory ledger has a gap or an inconsistent opening balance.', 1;
IF EXISTS (SELECT 1 FROM dbo.order_items i WHERE
    -(SELECT COALESCE(SUM(m.quantity_change),0) FROM dbo.inventory_movements m
      WHERE m.reference_order_id=i.order_id AND m.store_product_id=i.store_product_id AND m.type=N'ORDER')<>i.quantity)
    THROW 51126, 'Purchased quantity differs from ORDER inventory deductions.', 1;
IF EXISTS (SELECT 1 FROM dbo.order_items i JOIN dbo.orders o ON o.order_id=i.order_id WHERE o.order_status=N'CANCELLED' AND
    (SELECT COALESCE(SUM(m.quantity_change),0) FROM dbo.inventory_movements m
     WHERE m.reference_order_id=i.order_id AND m.store_product_id=i.store_product_id AND m.type=N'CANCEL_ORDER')<>i.quantity)
    THROW 51127, 'Cancelled order stock was not restored exactly once.', 1;

IF EXISTS (SELECT 1 FROM (
    SELECT fulfillment_id,old_status,new_status,changed_at,
      LAG(new_status) OVER(PARTITION BY fulfillment_id ORDER BY history_id) previous_status,
      LAG(changed_at) OVER(PARTITION BY fulfillment_id ORDER BY history_id) previous_time,
      ROW_NUMBER() OVER(PARTITION BY fulfillment_id ORDER BY history_id) rn
    FROM dbo.order_fulfillment_status_history) h
    WHERE (rn=1 AND old_status IS NOT NULL) OR (rn>1 AND (old_status IS NULL OR old_status<>previous_status OR changed_at<previous_time)))
    THROW 51128, 'Fulfillment history is discontinuous or out of time order.', 1;
IF EXISTS (SELECT 1 FROM dbo.order_fulfillment_status_history h JOIN dbo.order_fulfillments f ON f.fulfillment_id=h.fulfillment_id
    WHERE h.old_status IS NOT NULL AND NOT (
      (h.old_status=N'PENDING_PAYMENT' AND h.new_status IN(N'CONFIRMED',N'CANCELLED')) OR
      (h.old_status=N'CONFIRMED' AND h.new_status IN(N'PREPARING',N'CANCELLED')) OR
      (h.old_status=N'PREPARING' AND ((f.fulfillment_type=N'DELIVERY' AND h.new_status=N'PACKED') OR (f.fulfillment_type=N'STORE_PICKUP' AND h.new_status=N'READY_FOR_PICKUP'))) OR
      (h.old_status=N'PACKED' AND h.new_status=N'SHIPPING') OR
      (h.old_status IN(N'SHIPPING',N'READY_FOR_PICKUP') AND h.new_status IN(N'COMPLETED',N'FAILED'))))
    THROW 51129, 'Fulfillment history contains an illegal transition.', 1;
IF EXISTS (SELECT 1 FROM dbo.order_fulfillments f WHERE NOT EXISTS(SELECT 1 FROM dbo.order_fulfillment_status_history h WHERE h.fulfillment_id=f.fulfillment_id) OR f.status<>
    (SELECT TOP(1) new_status FROM dbo.order_fulfillment_status_history h WHERE h.fulfillment_id=f.fulfillment_id ORDER BY history_id DESC))
 OR EXISTS (SELECT 1 FROM dbo.orders o WHERE NOT EXISTS(SELECT 1 FROM dbo.order_status_history h WHERE h.order_id=o.order_id) OR o.order_status<>
    (SELECT TOP(1) new_status FROM dbo.order_status_history h WHERE h.order_id=o.order_id ORDER BY history_id DESC))
    THROW 51130, 'Current order/fulfillment status differs from its latest history.', 1;
IF EXISTS (SELECT 1 FROM (
    SELECT old_status,new_status,changed_at,LAG(new_status) OVER(PARTITION BY order_id ORDER BY history_id) previous_status,
      LAG(changed_at) OVER(PARTITION BY order_id ORDER BY history_id) previous_time,ROW_NUMBER() OVER(PARTITION BY order_id ORDER BY history_id) rn
    FROM dbo.order_status_history) h WHERE (rn=1 AND old_status IS NOT NULL)
    OR (rn>1 AND (old_status IS NULL OR old_status<>previous_status OR changed_at<previous_time)))
    THROW 51131, 'Order history is discontinuous or out of time order.', 1;
SELECT N'PASS' validation_status,N'Constraints, ownership, stock ledgers and legal state histories checked' message;
