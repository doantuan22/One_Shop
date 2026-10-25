/* Post-delivery return flow (order_returns, order_return_images, order_return_items). Existing databases only: run
   explicitly after backup and approval. A fresh 01_schema.sql already contains all of this.

   Re-runnable and data-preserving:
   - creates the three new tables only when they do not exist;
   - payment_refunds gets a nullable return_id, the RETURN component and the UX_payment_refunds_return index; its CHECK
     constraints are replaced by wider ones (existing rows already satisfy them: return_id is NULL for all of them);
   - inventory_movements gets the RETURN_ORDER type; its CHECK constraints are replaced by wider ones.
   No row of any existing table is changed, moved or deleted. */
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
    THROW 50801, 'Select the OneShop database (sqlcmd -d <database>) before running this script.', 1;
IF OBJECT_ID(N'dbo.payment_refunds', N'U') IS NULL OR OBJECT_ID(N'dbo.inventory_movements', N'U') IS NULL
   OR OBJECT_ID(N'dbo.order_items', N'U') IS NULL OR OBJECT_ID(N'dbo.stores', N'U') IS NULL
    THROW 50802, 'The return flow requires the V3 payment_refunds, inventory_movements, order_items and stores tables.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    IF OBJECT_ID(N'dbo.order_returns', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.order_returns (
            return_id           BIGINT IDENTITY(1,1) NOT NULL,
            order_id            BIGINT               NOT NULL,
            user_id             BIGINT               NOT NULL,
            reason              NVARCHAR(500)        NOT NULL,
            status              NVARCHAR(20)         NOT NULL CONSTRAINT DF_order_returns_status DEFAULT N'REQUESTED',
            received_at         DATETIME2(0)         NOT NULL,
            refund_amount       DECIMAL(18,2)        NOT NULL,
            requested_at        DATETIME2(0)         NOT NULL CONSTRAINT DF_order_returns_requested_at DEFAULT SYSDATETIME(),
            reviewed_by_user_id BIGINT               NULL,
            reviewed_at         DATETIME2(0)         NULL,
            review_note         NVARCHAR(500)        NULL,
            completed_at        DATETIME2(0)         NULL,
            CONSTRAINT PK_order_returns PRIMARY KEY (return_id),
            CONSTRAINT UQ_order_returns_order UNIQUE (order_id),
            CONSTRAINT FK_order_returns_order    FOREIGN KEY (order_id)            REFERENCES dbo.orders (order_id),
            CONSTRAINT FK_order_returns_user     FOREIGN KEY (user_id)             REFERENCES dbo.users (user_id),
            CONSTRAINT FK_order_returns_reviewer FOREIGN KEY (reviewed_by_user_id) REFERENCES dbo.users (user_id),
            CONSTRAINT CK_order_returns_status CHECK (status IN (N'REQUESTED', N'APPROVED', N'REJECTED', N'COMPLETED')),
            CONSTRAINT CK_order_returns_reason CHECK (LEN(LTRIM(RTRIM(reason))) > 0),
            CONSTRAINT CK_order_returns_amount CHECK (refund_amount > 0),
            CONSTRAINT CK_order_returns_review CHECK (
                (status = N'REQUESTED' AND reviewed_by_user_id IS NULL AND reviewed_at IS NULL AND completed_at IS NULL)
                OR (status = N'REJECTED' AND reviewed_by_user_id IS NOT NULL AND reviewed_at IS NOT NULL
                    AND review_note IS NOT NULL AND LEN(LTRIM(RTRIM(review_note))) > 0 AND completed_at IS NULL)
                OR (status = N'APPROVED' AND reviewed_by_user_id IS NOT NULL AND reviewed_at IS NOT NULL AND completed_at IS NULL)
                OR (status = N'COMPLETED' AND reviewed_by_user_id IS NOT NULL AND reviewed_at IS NOT NULL AND completed_at IS NOT NULL))
        );
        CREATE INDEX IX_order_returns_status_requested ON dbo.order_returns (status, requested_at);
    END;

    IF OBJECT_ID(N'dbo.order_return_images', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.order_return_images (
            image_id   BIGINT IDENTITY(1,1) NOT NULL,
            return_id  BIGINT               NOT NULL,
            image_url  NVARCHAR(500)        NOT NULL,
            public_id  NVARCHAR(255)        NOT NULL,
            sort_order INT                  NOT NULL CONSTRAINT DF_order_return_images_sort DEFAULT 0,
            created_at DATETIME2(0)         NOT NULL CONSTRAINT DF_order_return_images_created DEFAULT SYSDATETIME(),
            CONSTRAINT PK_order_return_images PRIMARY KEY (image_id),
            CONSTRAINT FK_order_return_images_return FOREIGN KEY (return_id) REFERENCES dbo.order_returns (return_id)
        );
        CREATE INDEX IX_order_return_images_return ON dbo.order_return_images (return_id, sort_order);
    END;

    IF OBJECT_ID(N'dbo.order_return_items', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.order_return_items (
            return_item_id     BIGINT IDENTITY(1,1) NOT NULL,
            return_id          BIGINT               NOT NULL,
            order_item_id      BIGINT               NOT NULL,
            fulfillment_id     BIGINT               NOT NULL,
            store_id           BIGINT               NOT NULL,
            store_product_id   BIGINT               NOT NULL,
            product_name       NVARCHAR(255)        NOT NULL,
            quantity           INT                  NOT NULL,
            unit_price         DECIMAL(18,2)        NOT NULL,
            subtotal           DECIMAL(18,2)        NOT NULL,
            disposition        NVARCHAR(20)         NOT NULL CONSTRAINT DF_order_return_items_disposition DEFAULT N'PENDING',
            handled_by_user_id BIGINT               NULL,
            handled_at         DATETIME2(0)         NULL,
            handle_note        NVARCHAR(500)        NULL,
            CONSTRAINT PK_order_return_items PRIMARY KEY (return_item_id),
            CONSTRAINT UQ_order_return_items_line UNIQUE (return_id, order_item_id),
            CONSTRAINT FK_order_return_items_return        FOREIGN KEY (return_id)          REFERENCES dbo.order_returns (return_id),
            CONSTRAINT FK_order_return_items_order_item    FOREIGN KEY (order_item_id)      REFERENCES dbo.order_items (order_item_id),
            CONSTRAINT FK_order_return_items_fulfillment   FOREIGN KEY (fulfillment_id)     REFERENCES dbo.order_fulfillments (fulfillment_id),
            CONSTRAINT FK_order_return_items_store         FOREIGN KEY (store_id)           REFERENCES dbo.stores (store_id),
            CONSTRAINT FK_order_return_items_store_product FOREIGN KEY (store_product_id)   REFERENCES dbo.store_products (store_product_id),
            CONSTRAINT FK_order_return_items_handler       FOREIGN KEY (handled_by_user_id) REFERENCES dbo.users (user_id),
            CONSTRAINT CK_order_return_items_quantity CHECK (quantity > 0),
            CONSTRAINT CK_order_return_items_subtotal CHECK (subtotal = unit_price * quantity),
            CONSTRAINT CK_order_return_items_disposition CHECK (disposition IN (N'PENDING', N'RESTOCKED', N'DISPOSED')),
            CONSTRAINT CK_order_return_items_handled CHECK (
                (disposition = N'PENDING' AND handled_by_user_id IS NULL AND handled_at IS NULL)
                OR (disposition <> N'PENDING' AND handled_by_user_id IS NOT NULL AND handled_at IS NOT NULL))
        );
        CREATE INDEX IX_order_return_items_store_disposition ON dbo.order_return_items (store_id, disposition);
    END;

    /* payment_refunds: RETURN component */
    IF COL_LENGTH(N'dbo.payment_refunds', N'return_id') IS NULL
    BEGIN
        ALTER TABLE dbo.payment_refunds ADD return_id BIGINT NULL;
        ALTER TABLE dbo.payment_refunds ADD CONSTRAINT FK_payment_refunds_return
            FOREIGN KEY (return_id) REFERENCES dbo.order_returns (return_id);
    END;
    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_payment_refunds_component' AND parent_object_id = OBJECT_ID(N'dbo.payment_refunds'))
        ALTER TABLE dbo.payment_refunds DROP CONSTRAINT CK_payment_refunds_component;
    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_payment_refunds_target' AND parent_object_id = OBJECT_ID(N'dbo.payment_refunds'))
        ALTER TABLE dbo.payment_refunds DROP CONSTRAINT CK_payment_refunds_target;
    EXEC (N'ALTER TABLE dbo.payment_refunds WITH CHECK ADD CONSTRAINT CK_payment_refunds_component
            CHECK (component IN (N''GOODS'', N''SHIPPING'', N''WHOLE_ORDER'', N''RETURN''))');
    EXEC (N'ALTER TABLE dbo.payment_refunds WITH CHECK ADD CONSTRAINT CK_payment_refunds_target CHECK (
            (component = N''GOODS'' AND fulfillment_id IS NOT NULL AND return_id IS NULL)
            OR (component = N''RETURN'' AND fulfillment_id IS NULL AND return_id IS NOT NULL)
            OR (component IN (N''SHIPPING'', N''WHOLE_ORDER'') AND fulfillment_id IS NULL AND return_id IS NULL))');
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.payment_refunds') AND name = N'UX_payment_refunds_return')
        EXEC (N'CREATE UNIQUE INDEX UX_payment_refunds_return ON dbo.payment_refunds (return_id) WHERE component = N''RETURN''');

    /* inventory_movements: RETURN_ORDER type */
    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_inventory_movements_type' AND parent_object_id = OBJECT_ID(N'dbo.inventory_movements'))
        ALTER TABLE dbo.inventory_movements DROP CONSTRAINT CK_inventory_movements_type;
    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_inventory_movements_by_type' AND parent_object_id = OBJECT_ID(N'dbo.inventory_movements'))
        ALTER TABLE dbo.inventory_movements DROP CONSTRAINT CK_inventory_movements_by_type;
    EXEC (N'ALTER TABLE dbo.inventory_movements WITH CHECK ADD CONSTRAINT CK_inventory_movements_type
            CHECK (type IN (N''ORDER'', N''CANCEL_ORDER'', N''STOCK_ADJUST'', N''RETURN_ORDER''))');
    EXEC (N'ALTER TABLE dbo.inventory_movements WITH CHECK ADD CONSTRAINT CK_inventory_movements_by_type CHECK (
            (type = N''ORDER''        AND quantity_change < 0 AND reference_order_id IS NOT NULL) OR
            (type = N''CANCEL_ORDER'' AND quantity_change > 0 AND reference_order_id IS NOT NULL) OR
            (type = N''STOCK_ADJUST'' AND quantity_change <> 0 AND staff_id IS NOT NULL) OR
            (type = N''RETURN_ORDER'' AND quantity_change > 0 AND reference_order_id IS NOT NULL AND staff_id IS NOT NULL))');

    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE is_not_trusted = 1) OR EXISTS (SELECT 1 FROM sys.foreign_keys WHERE is_not_trusted = 1)
        THROW 50803, 'An untrusted constraint exists after the return-flow migration.', 1;

    COMMIT TRANSACTION;
    PRINT 'Return flow applied: order_returns, order_return_images, order_return_items, payment_refunds RETURN, inventory_movements RETURN_ORDER.';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
