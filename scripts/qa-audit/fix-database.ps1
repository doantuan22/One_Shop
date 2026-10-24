param([ValidateSet('provision','inspect','migration','full-provision','upgrade-set')][string]$Action='inspect',[string]$Migration,
      [ValidatePattern('^oneshop[a-z0-9_]*_test$')][string]$Database='oneshop_fix_20261024_test')
$ErrorActionPreference='Stop'
Set-Location (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent)
$db=$Database
$out="docs/qa-audit/fix-verification/evidence/$db"
New-Item -ItemType Directory -Force $out | Out-Null
$cfg=@{}; Get-Content .env | ForEach-Object {if($_ -match '^\s*([^#=\s]+)\s*=(.*)$'){$cfg[$matches[1]]=$matches[2].Trim()}}
$hostName=if($cfg['DB_HOST']){$cfg['DB_HOST']}else{'localhost'}
$port=if($cfg['DB_PORT']){$cfg['DB_PORT']}else{'1433'}
$sqlArgs=@('-S',"$hostName,$port",'-C','-b','-l','10','-f','65001')
if($cfg['DB_USERNAME']){$sqlArgs+=@('-U',$cfg['DB_USERNAME']);$env:SQLCMDPASSWORD=$cfg['DB_PASSWORD']}else{$sqlArgs+='-E'}
try {
 if($Action -eq 'provision') {
  & sqlcmd @sqlArgs -v "DatabaseName=$db" -i database/01_schema.sql -o "$out/schema-install.log"
  if($LASTEXITCODE -ne 0){throw 'Refusing seed after schema failure/existing database.'}
  & sqlcmd @sqlArgs -v "DatabaseName=$db" -i database/02_seed.sql -o "$out/seed-install.log"
  if($LASTEXITCODE -ne 0){throw 'Seed failed.'}
 }
 if($Action -eq 'full-provision') {
  & sqlcmd @sqlArgs -v "DatabaseName=$db" -i database/01_schema.sql -o "$out/schema-install.log"
  if($LASTEXITCODE -ne 0){throw 'Refusing seed after schema failure/existing database.'}
  & sqlcmd @sqlArgs -v "DatabaseName=$db" -i database/02_seed.sql -o "$out/seed-install.log"
  if($LASTEXITCODE -ne 0){throw 'Seed failed.'}
  foreach($migrationPath in @('docs/v3/migration-history/20261014_be06_review_uniqueness.sql','docs/v3/migration-history/20261020_open101_payment_refunds.sql')) {
   & sqlcmd @sqlArgs -d $db -i $migrationPath -o "$out/migration-$(Split-Path $migrationPath -Leaf).log"
   if($LASTEXITCODE -ne 0){throw "Migration failed: $migrationPath"}
  }
 }
 if($Action -eq 'migration') {
  if(!$Migration -or !(Test-Path $Migration)){throw 'Specify an existing migration file.'}
  & sqlcmd @sqlArgs -d $db -i $Migration -o "$out/migration-$(Split-Path $Migration -Leaf).log"
  if($LASTEXITCODE -ne 0){throw 'Migration failed.'}
 }
 if($Action -eq 'upgrade-set') {
  foreach($migrationPath in @('database/04_image_cleanup_tasks.sql','database/05_product_primary_image_unique.sql')) {
   & sqlcmd @sqlArgs -d $db -i $migrationPath -o "$out/migration-$(Split-Path $migrationPath -Leaf).log"
   if($LASTEXITCODE -ne 0){throw "Application fix migration failed: $migrationPath"}
  }
 }
 & sqlcmd @sqlArgs -d $db -i scripts/qa-audit/inspect.sql -o "$out/database-$Action.txt"
 if($LASTEXITCODE -ne 0){throw 'Inspection failed.'}
} finally {Remove-Item Env:\SQLCMDPASSWORD -ErrorAction SilentlyContinue}
