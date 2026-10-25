/* OPEN-10.1 approved: simulated refund ledger (dbo.payment_refunds). Existing databases only: run explicitly after
   backup and approval. A fresh 01_schema.sql already contains this table.

   Data-preserving and re-runnable:
   - creates dbo.payment_refunds only when it does not exist; no existing table, column or row is altered or deleted;
   - backfills ONE WHOLE_ORDER ledger row for every payment that already carries a refund (payments.refund_*), so the
     history is complete. Rows already present are never duplicated (unique index per payment + NOT EXISTS).
   Nothing is refunded, moved or recalculated: payments, orders, stock and history are untouched.
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
    THROW 50701, 'Select the OneShop database (sqlcmd -d <database>) before running this script.', 1;
IF OBJECT_ID(N'dbo.payments', N'U') IS NULL OR COL_LENGTH(N'dbo.payments', N'refund_status') IS NULL
   OR OBJECT_ID(N'dbo.order_fulfillments', N'U') IS NULL OR OBJECT_ID(N'dbo.users', N'U') IS NULL
    THROW 50702, 'OPEN-10.1 requires the V3 payments (with refund columns), order_fulfillments and users tables.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    DECLARE @paymentsBefore bigint = (SELECT COUNT_BIG(*) FROM dbo.payments);
    DECLARE @ordersBefore   bigint = (SELECT COUNT_BIG(*) FROM dbo.orders);
    DECLARE @refundsOnPayments bigint = (SELECT COUNT_BIG(*) FROM dbo.payments
                                         WHERE refund_status <> N'NONE' AND COALESCE(refund_amount, amount) > 0);

    IF OBJECT_ID(N'dbo.payment_refunds', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.payment_refunds (
            refund_id            BIGINT IDENTITY(1,1) NOT NULL,
            payment_id           BIGINT               NOT NULL,
            order_id             BIGINT               NOT NULL,
            fulfillment_id       BIGINT               NULL,
            component            NVARCHAR(20)         NOT NULL,
            amount               DECIMAL(18,2)        NOT NULL,
            status               NVARCHAR(30)         NOT NULL,
            reason               NVARCHAR(500)        NOT NULL,
            requested_by_user_id BIGINT               NULL,
            requested_at         DATETIME2(0)         NOT NULL CONSTRAINT DF_payment_refunds_requested_at DEFAULT SYSDATETIME(),
            refunded_at          DATETIME2(0)         NULL,
            CONSTRAINT PK_payment_refunds PRIMARY KEY (refund_id),
            CONSTRAINT FK_payment_refunds_payment     FOREIGN KEY (payment_id)           REFERENCES dbo.payments (payment_id),
            CONSTRAINT FK_payment_refunds_order       FOREIGN KEY (order_id)             REFERENCES dbo.orders (order_id),
            CONSTRAINT FK_payment_refunds_fulfillment FOREIGN KEY (fulfillment_id)       REFERENCES dbo.order_fulfillments (fulfillment_id),
            CONSTRAINT FK_payment_refunds_user        FOREIGN KEY (requested_by_user_id) REFERENCES dbo.users (user_id),
            CONSTRAINT CK_payment_refunds_component CHECK (component IN (N'GOODS', N'SHIPPING', N'WHOLE_ORDER')),
            CONSTRAINT CK_payment_refunds_status CHECK (status IN (N'PENDING_SIMULATION', N'SIMULATED_REFUNDED', N'SIMULATION_FAILED')),
            CONSTRAINT CK_payment_refunds_amount CHECK (amount > 0),
            CONSTRAINT CK_payment_refunds_target CHECK (
                (component = N'GOODS' AND fulfillment_id IS NOT NULL) OR (component <> N'GOODS' AND fulfillment_id IS NULL)),
            CONSTRAINT CK_payment_refunds_refunded_at CHECK (status <> N'SIMULATED_REFUNDED' OR refunded_at IS NOT NULL)
        );
    END;

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.payment_refunds') AND name = N'UX_payment_refunds_goods')
        EXEC (N'CREATE UNIQUE INDEX UX_payment_refunds_goods ON dbo.payment_refunds (fulfillment_id) WHERE component = N''GOODS''');
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.payment_refunds') AND name = N'UX_payment_refunds_shipping')
        EXEC (N'CREATE UNIQUE INDEX UX_payment_refunds_shipping ON dbo.payment_refunds (order_id) WHERE component = N''SHIPPING''');
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.payment_refunds') AND name = N'UX_payment_refunds_whole')
        EXEC (N'CREATE UNIQUE INDEX UX_payment_refunds_whole ON dbo.payment_refunds (payment_id) WHERE component = N''WHOLE_ORDER''');
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.payment_refunds') AND name = N'IX_payment_refunds_order')
        EXEC (N'CREATE INDEX IX_payment_refunds_order ON dbo.payment_refunds (order_id, requested_at)');
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.payment_refunds') AND name = N'IX_payment_refunds_payment')
        EXEC (N'CREATE INDEX IX_payment_refunds_payment ON dbo.payment_refunds (payment_id)');

    /* history of the refunds that already exist on payments: one whole-Order row per payment, never twice */
    INSERT INTO dbo.payment_refunds (payment_id, order_id, fulfillment_id, component, amount, status, reason,
                                     requested_by_user_id, requested_at, refunded_at)
    SELECT p.payment_id, p.order_id, NULL, N'WHOLE_ORDER', COALESCE(p.refund_amount, p.amount), p.refund_status,
           COALESCE(p.refund_reason, N'Hoàn tiền mô phỏng (dữ liệu có sẵn)'), NULL,
           COALESCE(p.refund_requested_at, p.created_at),
           CASE WHEN p.refund_status = N'SIMULATED_REFUNDED'
                THEN COALESCE(p.refunded_at, p.refund_requested_at, p.created_at) END
    FROM dbo.payments p
    WHERE p.refund_status <> N'NONE' AND COALESCE(p.refund_amount, p.amount) > 0
      AND NOT EXISTS (SELECT 1 FROM dbo.payment_refunds r WHERE r.payment_id = p.payment_id AND r.component = N'WHOLE_ORDER');

    IF @paymentsBefore <> (SELECT COUNT_BIG(*) FROM dbo.payments) OR @ordersBefore <> (SELECT COUNT_BIG(*) FROM dbo.orders)
        THROW 50703, 'OPEN-10.1 must not change payments or orders; rolled back.', 1;
    IF (SELECT COUNT_BIG(*) FROM dbo.payment_refunds WHERE component = N'WHOLE_ORDER') <> @refundsOnPayments
        THROW 50704, 'OPEN-10.1 backfill count differs from the refunds recorded on payments; rolled back.', 1;

    COMMIT TRANSACTION;
    PRINT 'OPEN-10.1 applied: dbo.payment_refunds ready; ' + CAST(@refundsOnPayments AS varchar(20)) + ' existing refund(s) recorded in the ledger. Nothing else was changed.';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
