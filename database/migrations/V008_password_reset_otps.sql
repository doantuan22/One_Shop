/* Existing database upgrade: one-time codes (OTP) for "forgot password".
   Run only against an explicitly selected, backed-up/approved database. No application auto-migration.
   Additive only: a new table; no existing row is changed.

   A code is bound to ONE account: user_id + the e-mail it was sent to. Only a hash of the code is stored. */
SET XACT_ABORT ON;
SET NOCOUNT ON;
IF OBJECT_ID(N'dbo.password_reset_otps', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.password_reset_otps (
        otp_id      BIGINT IDENTITY(1,1) NOT NULL,
        user_id     BIGINT        NOT NULL,
        email       NVARCHAR(255) NOT NULL,
        otp_hash    NVARCHAR(100) NOT NULL,
        expires_at  DATETIME2(0)  NOT NULL,
        attempts    INT           NOT NULL CONSTRAINT DF_password_reset_otps_attempts DEFAULT 0,
        consumed_at DATETIME2(0)  NULL,
        created_at  DATETIME2(0)  NOT NULL CONSTRAINT DF_password_reset_otps_created_at DEFAULT SYSDATETIME(),
        CONSTRAINT PK_password_reset_otps PRIMARY KEY (otp_id),
        CONSTRAINT FK_password_reset_otps_user FOREIGN KEY (user_id) REFERENCES dbo.users (user_id),
        CONSTRAINT CK_password_reset_otps_attempts CHECK (attempts >= 0)
    );
    CREATE INDEX IX_password_reset_otps_email ON dbo.password_reset_otps (email, created_at DESC);
END;
SELECT N'password_reset_otps' AS verified_table, COUNT(*) AS rows_now FROM dbo.password_reset_otps;
