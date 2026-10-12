<#
.SYNOPSIS
  Rebuilds the LOCAL OneShop development database (drop + schema + seed).

.DESCRIPTION
  Order: S1 target check -> S2 backup -> S3 export of Cloudinary public ids -> drop -> 01..04 (schema + full seed)
         -> verification (05_verify.sql).
  Nothing is dropped before the target is validated and the backup (with RESTORE VERIFYONLY) succeeded.
  Run it with the application STOPPED. Never run it against a cloud / deployed database: the server must be
  localhost, 127.0.0.1, (local), "." or ".\instance", and $env:REBUILD_CONFIRM must equal the database name.

.PARAMETER Database     Target database. Default: DB_NAME of .env (or "oneshop").
.PARAMETER Server       Target server. Default: DB_HOST[,DB_PORT] of .env (or "localhost").
.PARAMETER DeleteOldCloudinaryAssets  After the backup, delete the exported old public ids on Cloudinary (default OFF: only the count is printed).
.PARAMETER BaseDataOnly  Load only the base data of 04_seed_data.sql (without the generated LARGE SEED block) and run only part 1 of 05_verify.sql.
                 Used for the test database (oneshop_test): the database tests assert exact values of the base data.
.PARAMETER CheckOnly    Validate the target and print the plan; touch nothing.
.PARAMETER EnvFile      Path of the .env file (default: <repo>/.env).

.EXAMPLE
  $env:REBUILD_CONFIRM = "oneshop"; .\database\rebuild_dev_db.ps1
.EXAMPLE
  $env:REBUILD_CONFIRM = "oneshop_test"; .\database\rebuild_dev_db.ps1 -Database oneshop_test
#>
[CmdletBinding()]
param(
    [string]$Database,
    [string]$Server,
    [switch]$DeleteOldCloudinaryAssets,
    [switch]$BaseDataOnly,
    [switch]$CheckOnly,
    [string]$EnvFile
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$dbDir = Join-Path $repo 'database'
if (-not $EnvFile) { $EnvFile = Join-Path $repo '.env' }

function Fail([string]$message, [int]$code = 1) {
    [Console]::Error.WriteLine("REFUSED: $message")
    exit $code
}

# ---- read .env (values are never printed) ----
$cfg = @{}
if (Test-Path $EnvFile) {
    foreach ($line in Get-Content $EnvFile -Encoding UTF8) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$') { $cfg[$Matches[1]] = $Matches[2].Trim('"') }
    }
}
function Cfg([string]$key, [string]$default = '') {
    $v = [Environment]::GetEnvironmentVariable($key)
    if ($v) { return $v }
    if ($cfg.ContainsKey($key) -and $cfg[$key]) { return $cfg[$key] }
    return $default
}

if (-not $Database) { $Database = Cfg 'DB_NAME' 'oneshop' }
$port = Cfg 'DB_PORT' '1433'
if (-not $Server) { $Server = Cfg 'DB_HOST' 'localhost' }
$serverArg = $Server
if ($Server -notmatch ',' -and $Server -notmatch '\\') { $serverArg = "$Server,$port" }

# ---- S1: target validation ----
$hostPart = ($Server -split ',')[0].Trim()
$allowedHost = ($hostPart -in @('localhost', '127.0.0.1', '(local)', '.', '[::1]', '::1')) -or
               ($hostPart -match '^(\.|\(local\)|localhost|127\.0\.0\.1)\\[A-Za-z0-9_$#@-]+$')
if (-not $allowedHost) { Fail "server '$hostPart' is not local. This script only rebuilds a database on localhost / 127.0.0.1 / (local) / .\instance." 10 }
if ($Database -notmatch '^[A-Za-z][A-Za-z0-9_]{0,63}$') { Fail "database name '$Database' is not a plain identifier." 11 }
if ($Database -in @('master', 'model', 'msdb', 'tempdb')) { Fail "'$Database' is a system database." 12 }
if ($env:REBUILD_CONFIRM -ne $Database) { Fail "REBUILD_CONFIRM must be set to exactly the database name ('$Database') to confirm the drop." 13 }

Write-Host "Target server  : $Server"
Write-Host "Target database: $Database"
if ($CheckOnly) { Write-Host 'CheckOnly: target accepted, nothing was touched.'; exit 0 }

$sqlcmd = (Get-Command sqlcmd -ErrorAction SilentlyContinue)
if (-not $sqlcmd) { Fail 'sqlcmd was not found in PATH.' 14 }

# credentials: SQL login from .env/environment, otherwise Windows authentication
$authArgs = @('-E')
$dbUser = Cfg 'DB_USERNAME'
if ($dbUser) {
    $authArgs = @('-U', $dbUser)
    $env:SQLCMDPASSWORD = Cfg 'DB_PASSWORD'   # read by sqlcmd; never echoed
}
$common = @('-S', $serverArg) + $authArgs + @('-C', '-f', '65001', '-b', '-I')

function Invoke-Sql([string[]]$extra, [string]$what) {
    & sqlcmd @common @extra
    if ($LASTEXITCODE -ne 0) { Fail "sqlcmd failed during: $what (exit $LASTEXITCODE)." 20 }
}
function Query-Sql([string]$query, [string]$db = 'master') {
    $out = & sqlcmd @common -d $db -h -1 -W -s '|' -Q "SET NOCOUNT ON; $query"
    if ($LASTEXITCODE -ne 0) { Fail "sqlcmd query failed: $query" 21 }
    return $out
}

# make sure the server really is this machine's SQL Server and not a forwarded remote one
$serverName = (Query-Sql "SELECT CAST(SERVERPROPERTY('MachineName') AS NVARCHAR(100))" | Select-Object -First 1).Trim()
if ($serverName -and $serverName -ne $env:COMPUTERNAME) { Fail "SQL Server machine '$serverName' is not this computer '$($env:COMPUTERNAME)'." 15 }

$exists = (Query-Sql "SELECT COUNT(*) FROM sys.databases WHERE name = N'$Database'" | Select-Object -First 1).Trim() -eq '1'
$stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$backupDir = Join-Path $repo 'backup'
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null

if ($exists) {
    # ---- S2: backup, abort when it fails ----
    $bak = Join-Path $backupDir "${Database}_$stamp.bak"
    Write-Host "Backup         : $bak"
    & sqlcmd @common -Q "BACKUP DATABASE [$Database] TO DISK = N'$bak' WITH INIT, CHECKSUM, NAME = N'$Database before rebuild $stamp'; RESTORE VERIFYONLY FROM DISK = N'$bak' WITH CHECKSUM;" | Out-Null
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $bak)) { Fail 'backup failed; the database was NOT dropped.' 30 }
    Write-Host "Backup verified: $((Get-Item $bak).Length) bytes. Restore with:"
    Write-Host "  sqlcmd -S $serverArg -E -C -Q `"ALTER DATABASE [$Database] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; RESTORE DATABASE [$Database] FROM DISK = N'$bak' WITH REPLACE; ALTER DATABASE [$Database] SET MULTI_USER;`""

    # ---- S3: export every non-NULL Cloudinary public id before dropping ----
    $idsFile = Join-Path $backupDir 'old_cloudinary_public_ids.txt'
    $ids = Query-Sql @"
SELECT public_id FROM dbo.product_images WHERE public_id IS NOT NULL
UNION SELECT logo_public_id FROM dbo.brands WHERE logo_public_id IS NOT NULL
UNION SELECT image_public_id FROM dbo.categories WHERE image_public_id IS NOT NULL
"@ $Database
    $ids = @($ids | Where-Object { $_ -and $_.Trim() -and $_ -notmatch '^\(\d+ rows' } | ForEach-Object { $_.Trim() } | Sort-Object -Unique)
    [IO.File]::WriteAllLines($idsFile, [string[]]$ids, (New-Object Text.UTF8Encoding($false)))
    Write-Host "Old Cloudinary public ids exported: $($ids.Count) -> $idsFile"
    if ($DeleteOldCloudinaryAssets) {
        if ($ids.Count -gt 0) {
            Write-Host 'Deleting the exported public ids on Cloudinary (and nothing else)...'
            & java (Join-Path $repo 'src/test/java/com/oneshop/seed/SeedImages.java') --delete-ids $idsFile
            if ($LASTEXITCODE -ne 0) { Fail 'deleting old Cloudinary assets failed; database NOT dropped.' 31 }
        }
    } else {
        Write-Host "Old Cloudinary assets are kept (use -DeleteOldCloudinaryAssets to delete these $($ids.Count) ids)."
    }

    # ---- S4: disconnect everybody and drop; MULTI_USER is restored if the drop fails ----
    Write-Host 'Dropping database (stop the application first)...'
    & sqlcmd @common -Q "ALTER DATABASE [$Database] SET SINGLE_USER WITH ROLLBACK IMMEDIATE;"
    if ($LASTEXITCODE -ne 0) { Fail 'could not take the database to SINGLE_USER; nothing dropped.' 32 }
    & sqlcmd @common -d master -Q "DROP DATABASE [$Database];"
    if ($LASTEXITCODE -ne 0) {
        & sqlcmd @common -Q "ALTER DATABASE [$Database] SET MULTI_USER;" | Out-Null
        Fail 'DROP DATABASE failed; MULTI_USER restored.' 33
    }
} else {
    Write-Host 'Database does not exist yet: nothing to back up or drop.'
}

# ---- create schema + seed: the repo scripts name [oneshop]; use temp copies with the target name ----
$tmp = Join-Path ([IO.Path]::GetTempPath()) "oneshop_rebuild_$stamp"
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
function Retarget([string]$file, [string]$cutFrom = '', [string]$cutTo = '') {
    $text = [IO.File]::ReadAllText($file, [Text.Encoding]::UTF8)
    if ($BaseDataOnly -and $cutFrom) {
        $a = $text.IndexOf($cutFrom)
        if ($a -ge 0) {
            if ($cutTo) { $b = $text.IndexOf($cutTo, $a); $text = $text.Substring(0, $a) + $text.Substring($b + $cutTo.Length) }
            else { $text = $text.Substring(0, $a) }
        }
    }
    $text = $text -replace '(?i)\[oneshop\]', "[$Database]"
    $text = $text -replace "(?i)N'oneshop'", "N'$Database'"
    $text = $text -replace 'Database oneshop ', "Database $Database "
    $out = Join-Path $tmp (Split-Path $file -Leaf)
    [IO.File]::WriteAllText($out, $text, (New-Object Text.UTF8Encoding($false)))
    return $out
}
try {
    Write-Host '01_create_database'; Invoke-Sql @('-d', 'master', '-i', (Retarget (Join-Path $dbDir '01_create_database.sql'))) '01_create_database'
    Write-Host '02_create_tables';   Invoke-Sql @('-d', $Database, '-i', (Retarget (Join-Path $dbDir '02_create_tables.sql'))) '02_create_tables'
    Write-Host '03_create_indexes';  Invoke-Sql @('-d', $Database, '-i', (Retarget (Join-Path $dbDir '03_create_indexes.sql'))) '03_create_indexes'
    Write-Host '04_seed_data';       Invoke-Sql @('-d', $Database, '-i', (Retarget (Join-Path $dbDir '04_seed_data.sql') '/* ===== BEGIN LARGE SEED' '/* ===== END LARGE SEED ===== */')) '04_seed_data'
    Write-Host '05_verify'
    Invoke-Sql @('-d', $Database, '-i', (Retarget (Join-Path $dbDir '05_verify.sql') '/* ===== PART 2: LARGE SEED CHECKS')) '05_verify'
} finally {
    Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
}

Write-Host ''
Write-Host "Rebuild finished: [$Database] on $Server ."
$counts = Query-Sql "SELECT 'users=' + CAST((SELECT COUNT(*) FROM dbo.users) AS VARCHAR) + ' products=' + CAST((SELECT COUNT(*) FROM dbo.products) AS VARCHAR) + ' store_products=' + CAST((SELECT COUNT(*) FROM dbo.store_products) AS VARCHAR) + ' orders=' + CAST((SELECT COUNT(*) FROM dbo.orders) AS VARCHAR) + ' reviews=' + CAST((SELECT COUNT(*) FROM dbo.reviews) AS VARCHAR)" $Database
Write-Host ($counts | Select-Object -First 1)
Remove-Item Env:\SQLCMDPASSWORD -ErrorAction SilentlyContinue
