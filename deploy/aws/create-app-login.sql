IF SUSER_ID(N'oneshop_app') IS NULL
BEGIN
    CREATE LOGIN [oneshop_app] WITH PASSWORD = N'$(AppDbPassword)', CHECK_POLICY = ON, CHECK_EXPIRATION = OFF;
END;
ELSE
BEGIN
    ALTER LOGIN [oneshop_app] WITH PASSWORD = N'$(AppDbPassword)', CHECK_POLICY = ON, CHECK_EXPIRATION = OFF;
END;
GO
USE [oneshop];
IF USER_ID(N'oneshop_app') IS NULL
BEGIN
    CREATE USER [oneshop_app] FOR LOGIN [oneshop_app];
END;
GO
IF IS_ROLEMEMBER(N'db_datareader', N'oneshop_app') <> 1
    ALTER ROLE [db_datareader] ADD MEMBER [oneshop_app];
IF IS_ROLEMEMBER(N'db_datawriter', N'oneshop_app') <> 1
    ALTER ROLE [db_datawriter] ADD MEMBER [oneshop_app];
GO
