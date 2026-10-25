<#
.SYNOPSIS
  Creates a NEW OneShop SQL Server database (schema + reference data + optional first admin / demo data).

.DESCRIPTION
  Runs database/schema/01_schema.sql (which refuses an existing database), then database/seed/*.sql in order, with
  sqlcmd -b (stop at the first error) and -f 65001 (UTF-8: Vietnamese text). Nothing is dropped or altered.
  The SQL login password comes from the SQLCMDPASSWORD environment variable, never from the command line.

  -Mode Production : schema + roles + first administrator (needs -AdminEmail/-AdminFullName/-AdminPasswordHash)
  -Mode Demo       : schema + roles + demo catalog/branches/demo accounts (needs -SeedPasswordHash)
  -Mode Empty      : schema + roles only
  -Mode Test       : schema + the automated-test fixture (database name must be oneshop*_test)

  A BCrypt hash is produced with scripts/db/New-PasswordHash.ps1.

.EXAMPLE
  $env:SQLCMDPASSWORD = '...'   # from your secret store
  .\scripts\db\Initialize-OneShopDatabase.ps1 -Server mydb.xxxx.rds.amazonaws.com -User admin -Database oneshop -Mode Production `
      -AdminEmail owner@example.com -AdminUserName admin -AdminFullName "Owner" -AdminPasswordHash '<bcrypt>'
#>
param(
  [Parameter(Mandatory)][string]$Server,
  [int]$Port = 1433,
  [Parameter(Mandatory)][string]$User,
  [Parameter(Mandatory)][string]$Database,
  [ValidateSet('Production','Demo','Empty','Test')][string]$Mode = 'Empty',
  [string]$AdminEmail, [string]$AdminUserName = 'admin', [string]$AdminFullName, [string]$AdminPasswordHash,
  [string]$SeedPasswordHash,
  [switch]$TrustServerCertificate   # only if the server certificate cannot be validated from this machine
)
$ErrorActionPreference = 'Stop'
if (-not $env:SQLCMDPASSWORD) { throw 'Set the SQLCMDPASSWORD environment variable first.' }
if ($Database -notmatch '^[A-Za-z0-9_]+$') { throw 'Database name may only contain letters, digits and underscore.' }
$root = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$common = @('-S', "$Server,$Port", '-U', $User, '-b', '-f', '65001')
if ($TrustServerCertificate) { $common += '-C' }

function Invoke-Sql([string[]]$extra) {
  & sqlcmd @common @extra
  if ($LASTEXITCODE -ne 0) { throw "sqlcmd failed with exit code $LASTEXITCODE" }
}

switch ($Mode) {
  'Production' { foreach ($v in 'AdminEmail','AdminFullName','AdminPasswordHash') { if (-not (Get-Variable $v -ValueOnly)) { throw "-$v is required in Production mode" } } }
  'Demo'       { if (-not $SeedPasswordHash) { throw '-SeedPasswordHash is required in Demo mode' } }
}

Write-Host "1/3 schema -> $Database"
Invoke-Sql @('-v', "DatabaseName=$Database", '-i', (Join-Path $root 'database\schema\01_schema.sql'))
if ($Mode -eq 'Test') {
  if ($Database -notmatch '^oneshop[A-Za-z0-9_]*_test$') { throw 'Test mode needs a database named oneshop*_test' }
  Write-Host '2/2 test fixture (6 branches, 8 sample orders, roles, accounts)'
  Invoke-Sql @('-v', "DatabaseName=$Database", '-i', (Join-Path $root 'database\test\test_fixture_seed.sql'))
  Write-Host 'Done.'; return
}
Write-Host '2/3 reference data'
Invoke-Sql @('-d', $Database, '-i', (Join-Path $root 'database\seed\01_reference_data.sql'))
switch ($Mode) {
  'Production' {
    Write-Host '3/3 first administrator'
    # values with spaces do not survive sqlcmd -v: sqlcmd also reads $(NAME) from the environment
    $env:AdminEmail = $AdminEmail; $env:AdminUserName = $AdminUserName; $env:AdminFullName = $AdminFullName; $env:AdminPasswordHash = $AdminPasswordHash
    try { Invoke-Sql @('-d', $Database, '-i', (Join-Path $root 'database\seed\02_bootstrap_admin.sql')) }
    finally { 'AdminEmail','AdminUserName','AdminFullName','AdminPasswordHash' | ForEach-Object { Remove-Item "Env:$_" -ErrorAction SilentlyContinue } } }
  'Demo' {
    Write-Host '3/3 demo data'
    Invoke-Sql @('-d', $Database, '-v', "SeedPasswordHash=$SeedPasswordHash", '-i', (Join-Path $root 'database\seed\20_demo_data.sql'))
    Invoke-Sql @('-d', $Database, '-i', (Join-Path $root 'database\seed\99_validate_demo_data.sql')) }
  default { Write-Host '3/3 nothing more (Empty mode)' }
}
Write-Host 'Done.'
