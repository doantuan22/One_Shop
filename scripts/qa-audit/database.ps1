param([ValidateSet('provision','inspect')][string]$Action='inspect')
$ErrorActionPreference='Stop'
$root=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location $root
$db='oneshop_qa_20261024_test'
$cfg=@{}
Get-Content .env | ForEach-Object {
    if ($_ -match '^\s*([^#=\s]+)\s*=(.*)$') {$cfg[$matches[1]]=$matches[2].Trim()}
}
$hostName=if($cfg['DB_HOST']){$cfg['DB_HOST']}else{'localhost'}
$port=if($cfg['DB_PORT']){$cfg['DB_PORT']}else{'1433'}
$argsSql=@('-S',"$hostName,$port",'-C','-b','-l','10','-f','65001')
if($cfg['DB_USERNAME']){$argsSql+=@('-U',$cfg['DB_USERNAME']);$env:SQLCMDPASSWORD=$cfg['DB_PASSWORD']}
else {$argsSql+='-E'}
try {
    if($Action -eq 'provision') {
        & sqlcmd @argsSql -v "DatabaseName=$db" -i database/01_schema.sql -o docs/qa-audit/evidence/schema-install.log
        if($LASTEXITCODE -ne 0){throw 'Schema failed; will not seed.'}
        & sqlcmd @argsSql -v "DatabaseName=$db" -i database/02_seed.sql -o docs/qa-audit/evidence/seed-install.log
        if($LASTEXITCODE -ne 0){throw 'Seed failed.'}
        Write-Output "Created isolated database $db"
    }
    & sqlcmd @argsSql -d $db -i scripts/qa-audit/inspect.sql -o "docs/qa-audit/evidence/database-inspection-$Action.txt"
    if($LASTEXITCODE -ne 0){throw 'Inspection failed.'}
} finally {Remove-Item Env:\SQLCMDPASSWORD -ErrorAction SilentlyContinue}
