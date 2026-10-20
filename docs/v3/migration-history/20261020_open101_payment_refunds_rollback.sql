/* OPEN-10.1 optional approved rollback. Stop the application first and keep a verified backup.
   Drops dbo.payment_refunds ONLY when it holds nothing but the whole-Order rows copied from payments.refund_*
   (nothing that OPEN-10.1 created: no GOODS or SHIPPING refund). If any partial refund exists, the script aborts
   unchanged: that history lives only in this table and must be exported / decided by the project owner first.
   The application of this version needs the table: after a rollback it will not start until the migration is applied
   again or the previous version is deployed. */
SET NOCOUNT ON;
SET XACT_ABORT ON;

IF DB_NAME() <> N'oneshop' AND DB_NAME() NOT LIKE N'oneshop%[_]test'
    THROW 50711, 'Select the operational OneShop database or an explicitly named OneShop *_test database.', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    IF OBJECT_ID(N'dbo.payment_refunds', N'U') IS NOT NULL
    BEGIN
        IF EXISTS (SELECT 1 FROM dbo.payment_refunds WHERE component <> N'WHOLE_ORDER')
            THROW 50712, 'Partial refunds (GOODS / SHIPPING) exist: they exist only in this table. Nothing was changed.', 1;
        IF EXISTS (SELECT 1 FROM dbo.payment_refunds r
                   WHERE NOT EXISTS (SELECT 1 FROM dbo.payments p WHERE p.payment_id = r.payment_id
                                     AND p.refund_status = r.status AND COALESCE(p.refund_amount, p.amount) = r.amount))
            THROW 50713, 'A ledger row differs from payments.refund_*: reconcile manually. Nothing was changed.', 1;
        DROP TABLE dbo.payment_refunds;
    END;
    COMMIT TRANSACTION;
    PRINT 'OPEN-10.1 rolled back: dbo.payment_refunds removed (it only mirrored payments.refund_*).';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
