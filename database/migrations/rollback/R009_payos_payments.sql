/* Optional rollback of V009 (payOS). Stop the application first and keep a verified backup.
   Refuses (changing nothing) when any PAYOS payment, provider event or real refund exists: that history exists only in
   these columns/tables and must be exported and decided by the project owner first. Simulated (MOCK) data is kept. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
IF DB_NAME() IN (N'master', N'model', N'msdb', N'tempdb')
    THROW 50911, 'Select the OneShop database (sqlcmd -d <database>) before running this script.', 1;
BEGIN TRY
    BEGIN TRANSACTION;
    IF COL_LENGTH(N'dbo.payments', N'provider') IS NOT NULL
       AND EXISTS (SELECT 1 FROM dbo.payments WHERE provider = N'PAYOS' OR refund_status IN (N'MANUAL_REQUESTED', N'REFUNDED'))
        THROW 50912, 'PAYOS payments or real refunds exist. Nothing was changed.', 1;
    IF OBJECT_ID(N'dbo.payment_provider_events', N'U') IS NOT NULL
       AND EXISTS (SELECT 1 FROM dbo.payment_provider_events)
        THROW 50913, 'Provider events exist. Nothing was changed.', 1;
    IF COL_LENGTH(N'dbo.payment_refunds', N'evidence_reference') IS NOT NULL
       AND EXISTS (SELECT 1 FROM dbo.payment_refunds WHERE status IN (N'MANUAL_REQUESTED', N'REFUNDED'))
        THROW 50914, 'Real refund rows exist. Nothing was changed.', 1;

    IF OBJECT_ID(N'dbo.payment_provider_events', N'U') IS NOT NULL DROP TABLE dbo.payment_provider_events;

    IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UX_payment_refunds_evidence') DROP INDEX UX_payment_refunds_evidence ON dbo.payment_refunds;
    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_payment_refunds_real_evidence') ALTER TABLE dbo.payment_refunds DROP CONSTRAINT CK_payment_refunds_real_evidence;
    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_payment_refunds_refunded_at') ALTER TABLE dbo.payment_refunds DROP CONSTRAINT CK_payment_refunds_refunded_at;
    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_payment_refunds_status') ALTER TABLE dbo.payment_refunds DROP CONSTRAINT CK_payment_refunds_status;
    IF EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_payment_refunds_processed_by') ALTER TABLE dbo.payment_refunds DROP CONSTRAINT FK_payment_refunds_processed_by;
    IF COL_LENGTH(N'dbo.payment_refunds', N'evidence_reference') IS NOT NULL
        ALTER TABLE dbo.payment_refunds DROP COLUMN evidence_reference, processed_by_user_id, processed_at, processed_note;
    ALTER TABLE dbo.payment_refunds ADD CONSTRAINT CK_payment_refunds_status CHECK (status IN (N'PENDING_SIMULATION', N'SIMULATED_REFUNDED', N'SIMULATION_FAILED'));
    ALTER TABLE dbo.payment_refunds ADD CONSTRAINT CK_payment_refunds_refunded_at CHECK (status <> N'SIMULATED_REFUNDED' OR refunded_at IS NOT NULL);

    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_payments_refund_status') ALTER TABLE dbo.payments DROP CONSTRAINT CK_payments_refund_status;
    ALTER TABLE dbo.payments ADD CONSTRAINT CK_payments_refund_status CHECK (refund_status IN (N'NONE', N'PENDING_SIMULATION', N'SIMULATED_REFUNDED', N'SIMULATION_FAILED'));

    IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UX_payments_one_pending_payos') DROP INDEX UX_payments_one_pending_payos ON dbo.payments;
    IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UX_payments_provider_link') DROP INDEX UX_payments_provider_link ON dbo.payments;
    IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UX_payments_provider_order_code') DROP INDEX UX_payments_provider_order_code ON dbo.payments;
    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_payments_provider_status') ALTER TABLE dbo.payments DROP CONSTRAINT CK_payments_provider_status;
    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_payments_payos_order_code') ALTER TABLE dbo.payments DROP CONSTRAINT CK_payments_payos_order_code;
    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_payments_provider') ALTER TABLE dbo.payments DROP CONSTRAINT CK_payments_provider;
    IF COL_LENGTH(N'dbo.payments', N'provider') IS NOT NULL
        ALTER TABLE dbo.payments DROP COLUMN provider, provider_order_code, provider_link_id, checkout_url, provider_status,
            provider_amount_paid, provider_reference, provider_paid_at, expires_at, last_synced_at;
    COMMIT TRANSACTION;
    PRINT 'V009 rolled back.';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
