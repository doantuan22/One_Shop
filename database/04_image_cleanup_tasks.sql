/* Existing database upgrade. Run only against an explicitly selected, backed-up/approved database.
   No application auto-migration; no existing catalog row or external asset is changed. */
SET XACT_ABORT ON;
SET NOCOUNT ON;
IF OBJECT_ID(N'dbo.image_cleanup_tasks',N'U') IS NULL
BEGIN
    CREATE TABLE dbo.image_cleanup_tasks (
        task_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_image_cleanup_tasks PRIMARY KEY,
        public_id NVARCHAR(255) NOT NULL,
        attempts INT NOT NULL CONSTRAINT DF_image_cleanup_attempts DEFAULT 0,
        last_error NVARCHAR(500) NULL,
        created_at DATETIME2(7) NOT NULL CONSTRAINT DF_image_cleanup_created DEFAULT SYSDATETIME(),
        CONSTRAINT CK_image_cleanup_attempts CHECK (attempts >= 0)
    );
END;
SELECT N'image_cleanup_tasks' AS verified_table,COUNT(*) AS pending FROM dbo.image_cleanup_tasks;
