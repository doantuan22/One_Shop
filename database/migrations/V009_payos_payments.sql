/* payOS real-payment integration. Existing databases only: run explicitly, after a verified backup and approval.
   A fresh 01_schema.sql already contains everything below. The application does NOT run this script.

   Additive and re-runnable:
   - dbo.payments: provider columns (provider, provider_order_code, provider_link_id, checkout_url, provider_status,
     provider_amount_paid, provider_reference, provider_paid_at, expires_at, last_synced_at). Existing ONLINE rows are
     labelled provider = MOCK (they were simulated); COD / PAY_AT_STORE rows keep provider NULL. No row is deleted and
     no status, amount or timestamp of an existing row changes.
   - dbo.payment_provider_events: durable webhook de-duplication (unique provider + event_key).
   - dbo.payment_refunds: real/manual refund evidence columns; status gains MANUAL_REQUESTED and REFUNDED.
   - payments.refund_status gains MANUAL_REQUESTED and REFUNDED.
   Simulated refunds (PENDING_SIMULATION / SIMULATED_REFUNDED / SIMULATION_FAILED) are preserved untouched and remain
   distinct from real ones. */
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
IF OBJECT_ID(N'dbo.payments', N'U') IS NULL OR OBJECT_ID(N'dbo.payment_refunds', N'U') IS NULL
   OR OBJECT_ID(N'dbo.users', N'U') IS NULL
    THROW 50902, 'payOS integration requires V002 (payment_refunds) to be applied first.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    DECLARE @paymentsBefore bigint = (SELECT COUNT_BIG(*) FROM dbo.payments);
    DECLARE @refundsBefore  bigint = (SELECT COUNT_BIG(*) FROM dbo.payment_refunds);

    /* ---- payments: provider columns ---- */
    IF COL_LENGTH(N'dbo.payments', N'provider') IS NULL
        EXEC (N'ALTER TABLE dbo.payments ADD
            provider             NVARCHAR(20)  NULL,
            provider_order_code  BIGINT        NULL,
            provider_link_id     NVARCHAR(64)  NULL,
            checkout_url         NVARCHAR(500) NULL,
            provider_status      NVARCHAR(20)  NULL,
            provider_amount_paid BIGINT        NULL,
            provider_reference   NVARCHAR(100) NULL,
            provider_paid_at     DATETIME2(0)  NULL,
            expires_at           DATETIME2(0)  NULL,
            last_synced_at       DATETIME2(0)  NULL');

    /* existing ONLINE rows were simulated */
    EXEC (N'UPDATE dbo.payments SET provider = N''MOCK'' WHERE method = N''ONLINE'' AND provider IS NULL');

    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_payments_provider')
        EXEC (N'ALTER TABLE dbo.payments ADD CONSTRAINT CK_payments_provider CHECK (
            provider IS NULL OR provider IN (N''MOCK'', N''PAYOS''))');
    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_payments_payos_order_code')
        EXEC (N'ALTER TABLE dbo.payments ADD CONSTRAINT CK_payments_payos_order_code CHECK (
            provider IS NULL OR provider <> N''PAYOS''
            OR (provider_order_code IS NOT NULL AND provider_order_code BETWEEN 1 AND 9007199254740991))');
    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_payments_provider_status')
        EXEC (N'ALTER TABLE dbo.payments ADD CONSTRAINT CK_payments_provider_status CHECK (
            provider_status IS NULL OR provider_status IN (N''PENDING'', N''PROCESSING'', N''PAID'', N''UNDERPAID'',
                                                          N''CANCELLED'', N''EXPIRED'', N''FAILED''))');

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.payments') AND name = N'UX_payments_provider_order_code')
        EXEC (N'CREATE UNIQUE INDEX UX_payments_provider_order_code ON dbo.payments (provider_order_code) WHERE provider_order_code IS NOT NULL');
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.payments') AND name = N'UX_payments_provider_link')
        EXEC (N'CREATE UNIQUE INDEX UX_payments_provider_link ON dbo.payments (provider_link_id) WHERE provider_link_id IS NOT NULL');
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.payments') AND name = N'UX_payments_one_pending_payos')
        EXEC (N'CREATE UNIQUE INDEX UX_payments_one_pending_payos ON dbo.payments (order_id) WHERE provider = N''PAYOS'' AND status = N''PENDING''');

    /* ---- payments.refund_status: add the real-refund values ---- */
    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_payments_refund_status'
               AND definition NOT LIKE N'%MANUAL_REQUESTED%')
        ALTER TABLE dbo.payments DROP CONSTRAINT CK_payments_refund_status;
    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_payments_refund_status')
        EXEC (N'ALTER TABLE dbo.payments ADD CONSTRAINT CK_payments_refund_status CHECK (refund_status IN (
            N''NONE'', N''PENDING_SIMULATION'', N''SIMULATED_REFUNDED'', N''SIMULATION_FAILED'',
            N''MANUAL_REQUESTED'', N''REFUNDED''))');

    /* ---- webhook / provider event journal ---- */
    IF OBJECT_ID(N'dbo.payment_provider_events', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.payment_provider_events (
            event_id        BIGINT IDENTITY(1,1) NOT NULL,
            provider        NVARCHAR(20)  NOT NULL,
            event_key       NVARCHAR(128) NOT NULL,
            payment_id      BIGINT        NULL,
            order_code      BIGINT        NULL,
            payment_link_id NVARCHAR(64)  NULL,
            reference       NVARCHAR(100) NULL,
            amount          BIGINT        NULL,
            event_code      NVARCHAR(10)  NULL,
            transaction_at  DATETIME2(0)  NULL,
            outcome         NVARCHAR(30)  NOT NULL,
            received_at     DATETIME2(0)  NOT NULL CONSTRAINT DF_payment_provider_events_received_at DEFAULT SYSDATETIME(),
            CONSTRAINT PK_payment_provider_events PRIMARY KEY (event_id),
            CONSTRAINT UQ_payment_provider_events_key UNIQUE (provider, event_key),
            CONSTRAINT FK_payment_provider_events_payment FOREIGN KEY (payment_id) REFERENCES dbo.payments (payment_id),
            CONSTRAINT CK_payment_provider_events_outcome CHECK (outcome IN (
                N'APPLIED', N'TRIGGERED_SYNC', N'UNKNOWN_ORDER', N'REJECTED_MISMATCH'))
        );
        CREATE INDEX IX_payment_provider_events_payment ON dbo.payment_provider_events (payment_id);
    END;

    /* ---- payment_refunds: real/manual refund evidence ---- */
    IF COL_LENGTH(N'dbo.payment_refunds', N'evidence_reference') IS NULL
        EXEC (N'ALTER TABLE dbo.payment_refunds ADD
            evidence_reference   NVARCHAR(100) NULL,
            processed_by_user_id BIGINT        NULL,
            processed_at         DATETIME2(0)  NULL,
            processed_note       NVARCHAR(500) NULL');
    IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_payment_refunds_processed_by')
        EXEC (N'ALTER TABLE dbo.payment_refunds ADD CONSTRAINT FK_payment_refunds_processed_by
                FOREIGN KEY (processed_by_user_id) REFERENCES dbo.users (user_id)');

    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_payment_refunds_status'
               AND definition NOT LIKE N'%MANUAL_REQUESTED%')
        ALTER TABLE dbo.payment_refunds DROP CONSTRAINT CK_payment_refunds_status;
    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_payment_refunds_status')
        EXEC (N'ALTER TABLE dbo.payment_refunds ADD CONSTRAINT CK_payment_refunds_status CHECK (status IN (
            N''PENDING_SIMULATION'', N''SIMULATED_REFUNDED'', N''SIMULATION_FAILED'', N''MANUAL_REQUESTED'', N''REFUNDED''))');

    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_payment_refunds_refunded_at'
               AND definition NOT LIKE N'%''REFUNDED''%')
        ALTER TABLE dbo.payment_refunds DROP CONSTRAINT CK_payment_refunds_refunded_at;
    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_payment_refunds_refunded_at')
        EXEC (N'ALTER TABLE dbo.payment_refunds ADD CONSTRAINT CK_payment_refunds_refunded_at CHECK (
            status NOT IN (N''SIMULATED_REFUNDED'', N''REFUNDED'') OR refunded_at IS NOT NULL)');
    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_payment_refunds_real_evidence')
        EXEC (N'ALTER TABLE dbo.payment_refunds ADD CONSTRAINT CK_payment_refunds_real_evidence CHECK (
            status <> N''REFUNDED'' OR (evidence_reference IS NOT NULL AND processed_by_user_id IS NOT NULL AND processed_at IS NOT NULL))');
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.payment_refunds') AND name = N'UX_payment_refunds_evidence')
        EXEC (N'CREATE UNIQUE INDEX UX_payment_refunds_evidence ON dbo.payment_refunds (evidence_reference) WHERE evidence_reference IS NOT NULL');

    IF @paymentsBefore <> (SELECT COUNT_BIG(*) FROM dbo.payments) OR @refundsBefore <> (SELECT COUNT_BIG(*) FROM dbo.payment_refunds)
        THROW 50903, 'payOS migration must not add or delete payments or refunds; rolled back.', 1;

    COMMIT TRANSACTION;
    PRINT 'payOS migration applied: payments provider columns, payment_provider_events and real-refund evidence are ready.';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
