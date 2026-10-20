SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
-- Read-only fingerprint: structure + per-table row count and content checksum.
PRINT '### TABLES (rows | checksum_agg(binary_checksum(*)))';
DECLARE @sql nvarchar(max) = N'';
SELECT @sql += N'SELECT ''' + t.name + N''' AS tbl, COUNT_BIG(*) AS n, CHECKSUM_AGG(BINARY_CHECKSUM(*)) AS chk FROM dbo.' + QUOTENAME(t.name) + N' UNION ALL '
FROM sys.tables t WHERE t.name <> 'payment_refunds' ORDER BY t.name;
SET @sql = LEFT(@sql, LEN(@sql) - 10) + N' ORDER BY tbl';
EXEC sp_executesql @sql;

PRINT '### COLUMNS';
SELECT t.name AS tbl, c.name AS col, ty.name AS typ, c.max_length, c.precision, c.scale, c.is_nullable, c.is_identity
FROM sys.tables t JOIN sys.columns c ON c.object_id = t.object_id JOIN sys.types ty ON ty.user_type_id = c.user_type_id
WHERE t.name <> 'payment_refunds' ORDER BY t.name, c.column_id;

PRINT '### FOREIGN KEYS (trusted?)';
SELECT OBJECT_NAME(parent_object_id) AS tbl, name, is_disabled, is_not_trusted FROM sys.foreign_keys WHERE OBJECT_NAME(parent_object_id) <> 'payment_refunds' ORDER BY 1, 2;

PRINT '### CHECK CONSTRAINTS (trusted?)';
SELECT OBJECT_NAME(parent_object_id) AS tbl, name, is_disabled, is_not_trusted FROM sys.check_constraints WHERE OBJECT_NAME(parent_object_id) <> 'payment_refunds' ORDER BY 1, 2;

PRINT '### INDEXES';
SELECT OBJECT_NAME(i.object_id) AS tbl, i.name, i.is_unique, i.is_primary_key, i.filter_definition
FROM sys.indexes i JOIN sys.tables t ON t.object_id = i.object_id
WHERE i.name IS NOT NULL AND t.name <> 'payment_refunds' ORDER BY 1, 2;

PRINT '### MONEY / STOCK AGGREGATES';
SELECT 'orders.total_amount' AS k, CAST(SUM(total_amount) AS varchar(40)) AS v FROM dbo.orders
UNION ALL SELECT 'payments.amount(SUCCESS)', CAST(SUM(amount) AS varchar(40)) FROM dbo.payments WHERE status = 'SUCCESS'
UNION ALL SELECT 'payments.refund_amount', CAST(COALESCE(SUM(refund_amount), 0) AS varchar(40)) FROM dbo.payments
UNION ALL SELECT 'store_products.quantity', CAST(SUM(quantity) AS varchar(40)) FROM dbo.store_products
UNION ALL SELECT 'store_products.reserved', CAST(SUM(reserved_quantity) AS varchar(40)) FROM dbo.store_products;

PRINT '### PAYMENTS WITH REFUND DATA';
SELECT payment_id, order_id, status, amount, refund_status, refund_amount, refunded_at FROM dbo.payments WHERE refund_status <> 'NONE' ORDER BY payment_id;

PRINT '### ORDER / FULFILLMENT STATUS DISTRIBUTION';
SELECT 'order:' + order_status + '/' + payment_status AS k, COUNT(*) AS n FROM dbo.orders GROUP BY order_status, payment_status
UNION ALL SELECT 'fulfillment:' + status, COUNT(*) FROM dbo.order_fulfillments GROUP BY status ORDER BY 1;

PRINT '### INTEGRITY PROBES (all must be 0)';
SELECT 'orders without fulfillment' AS k, COUNT(*) AS n FROM dbo.orders o WHERE NOT EXISTS (SELECT 1 FROM dbo.order_fulfillments f WHERE f.order_id = o.order_id)
UNION ALL SELECT 'negative stock', COUNT(*) FROM dbo.store_products WHERE quantity < 0 OR reserved_quantity < 0
UNION ALL SELECT 'orders with >1 SUCCESS payment', COUNT(*) FROM (SELECT order_id FROM dbo.payments WHERE status = 'SUCCESS' GROUP BY order_id HAVING COUNT(*) > 1) x
UNION ALL SELECT 'refund > collected', COUNT(*) FROM dbo.payments WHERE refund_amount IS NOT NULL AND refund_amount > amount
UNION ALL SELECT 'refunded but payment_status not REFUNDED', COUNT(*) FROM dbo.payments p JOIN dbo.orders o ON o.order_id = p.order_id WHERE p.refund_status = 'SIMULATED_REFUNDED' AND o.payment_status <> 'REFUNDED';
