/* Promotion codes (promotions, promotion_usages) and the discount snapshot on orders / order_items. Existing databases
   only: run explicitly after backup and approval. A fresh 01_schema.sql already contains all of this.

   Re-runnable and data-preserving:
   - creates dbo.promotions and dbo.promotion_usages only when they do not exist;
   - adds orders.discount_amount (default 0), orders.promotion_code (NULL) and order_items.discount_amount (default 0);
     every existing Order keeps its total: total = goods - 0 + shipping fee still holds;
   - no existing row is changed, moved or deleted.
   The application refuses to start (V3SchemaCompatibilityVerifier) until this has been applied. */
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
    THROW 50901, 'Select the OneShop database (sqlcmd -d <database>) before running this script.', 1;
IF OBJECT_ID(N'dbo.orders', N'U') IS NULL OR OBJECT_ID(N'dbo.order_items', N'U') IS NULL
   OR OBJECT_ID(N'dbo.checkout_sessions', N'U') IS NULL OR OBJECT_ID(N'dbo.users', N'U') IS NULL
    THROW 50902, 'Promotions require the V3 orders, order_items, checkout_sessions and users tables.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    IF COL_LENGTH(N'dbo.orders', N'discount_amount') IS NULL
        EXEC (N'ALTER TABLE dbo.orders ADD discount_amount DECIMAL(18,2) NOT NULL CONSTRAINT DF_orders_discount_amount DEFAULT 0');
    IF COL_LENGTH(N'dbo.orders', N'promotion_code') IS NULL
        EXEC (N'ALTER TABLE dbo.orders ADD promotion_code NVARCHAR(50) NULL');
    IF COL_LENGTH(N'dbo.order_items', N'discount_amount') IS NULL
        EXEC (N'ALTER TABLE dbo.order_items ADD discount_amount DECIMAL(18,2) NOT NULL CONSTRAINT DF_order_items_discount DEFAULT 0');
    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_orders_discount' AND parent_object_id = OBJECT_ID(N'dbo.orders'))
        EXEC (N'ALTER TABLE dbo.orders WITH CHECK ADD CONSTRAINT CK_orders_discount CHECK (discount_amount >= 0)');
    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_order_items_discount' AND parent_object_id = OBJECT_ID(N'dbo.order_items'))
        EXEC (N'ALTER TABLE dbo.order_items WITH CHECK ADD CONSTRAINT CK_order_items_discount CHECK (discount_amount >= 0 AND discount_amount <= subtotal)');

    IF OBJECT_ID(N'dbo.promotions', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.promotions (
            promotion_id        BIGINT IDENTITY(1,1) NOT NULL,
            code                NVARCHAR(50)         NOT NULL,
            name                NVARCHAR(150)        NOT NULL,
            description         NVARCHAR(500)        NULL,
            discount_type       NVARCHAR(20)         NOT NULL,
            discount_value      DECIMAL(18,2)        NOT NULL,
            max_discount_amount DECIMAL(18,2)        NULL,
            min_order_amount    DECIMAL(18,2)        NOT NULL CONSTRAINT DF_promotions_min_order DEFAULT 0,
            usage_limit         INT                  NOT NULL,
            used_count          INT                  NOT NULL CONSTRAINT DF_promotions_used_count DEFAULT 0,
            per_user_limit      INT                  NOT NULL CONSTRAINT DF_promotions_per_user DEFAULT 1,
            start_at            DATETIME2(0)         NULL,
            end_at              DATETIME2(0)         NULL,
            status              NVARCHAR(20)         NOT NULL CONSTRAINT DF_promotions_status DEFAULT N'ACTIVE',
            created_at          DATETIME2(0)         NOT NULL CONSTRAINT DF_promotions_created_at DEFAULT SYSDATETIME(),
            updated_at          DATETIME2(0)         NOT NULL CONSTRAINT DF_promotions_updated_at DEFAULT SYSDATETIME(),
            CONSTRAINT PK_promotions PRIMARY KEY (promotion_id),
            CONSTRAINT UQ_promotions_code UNIQUE (code),
            CONSTRAINT CK_promotions_type CHECK (discount_type IN (N'PERCENT', N'FIXED')),
            CONSTRAINT CK_promotions_status CHECK (status IN (N'ACTIVE', N'INACTIVE')),
            CONSTRAINT CK_promotions_value CHECK (discount_value > 0 AND (discount_type <> N'PERCENT' OR discount_value <= 100)),
            CONSTRAINT CK_promotions_cap CHECK (max_discount_amount IS NULL OR (discount_type = N'PERCENT' AND max_discount_amount > 0)),
            CONSTRAINT CK_promotions_min_order CHECK (min_order_amount >= 0),
            CONSTRAINT CK_promotions_limits CHECK (usage_limit >= 1 AND per_user_limit >= 1 AND used_count >= 0 AND used_count <= usage_limit),
            CONSTRAINT CK_promotions_dates CHECK (start_at IS NULL OR end_at IS NULL OR end_at > start_at)
        );
    END;

    IF OBJECT_ID(N'dbo.promotion_usages', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.promotion_usages (
            usage_id        BIGINT IDENTITY(1,1) NOT NULL,
            promotion_id    BIGINT               NOT NULL,
            user_id         BIGINT               NOT NULL,
            checkout_id     BIGINT               NOT NULL,
            discount_amount DECIMAL(18,2)        NOT NULL,
            status          NVARCHAR(20)         NOT NULL CONSTRAINT DF_promotion_usages_status DEFAULT N'USED',
            used_at         DATETIME2(0)         NOT NULL CONSTRAINT DF_promotion_usages_used_at DEFAULT SYSDATETIME(),
            released_at     DATETIME2(0)         NULL,
            CONSTRAINT PK_promotion_usages PRIMARY KEY (usage_id),
            CONSTRAINT UQ_promotion_usages_checkout UNIQUE (checkout_id),
            CONSTRAINT FK_promotion_usages_promotion FOREIGN KEY (promotion_id) REFERENCES dbo.promotions (promotion_id),
            CONSTRAINT FK_promotion_usages_user      FOREIGN KEY (user_id)      REFERENCES dbo.users (user_id),
            CONSTRAINT FK_promotion_usages_checkout  FOREIGN KEY (checkout_id)  REFERENCES dbo.checkout_sessions (checkout_id),
            CONSTRAINT CK_promotion_usages_status CHECK (status IN (N'USED', N'RELEASED')),
            CONSTRAINT CK_promotion_usages_amount CHECK (discount_amount >= 0),
            CONSTRAINT CK_promotion_usages_released CHECK ((status = N'USED' AND released_at IS NULL) OR (status = N'RELEASED' AND released_at IS NOT NULL))
        );
        CREATE INDEX IX_promotion_usages_user ON dbo.promotion_usages (promotion_id, user_id, status);
    END;

    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE is_not_trusted = 1) OR EXISTS (SELECT 1 FROM sys.foreign_keys WHERE is_not_trusted = 1)
        THROW 50903, 'An untrusted constraint exists after the promotions migration.', 1;

    COMMIT TRANSACTION;
    PRINT 'Promotions applied: promotions, promotion_usages, orders.discount_amount / promotion_code, order_items.discount_amount.';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
