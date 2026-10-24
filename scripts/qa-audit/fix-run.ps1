param([string]$Phase='baseline',[string]$Tests='QaAuditRaceDatabaseIntegrationTest,QaAuditImageRollbackDatabaseIntegrationTest,QaAuditBoundaryDatabaseIntegrationTest,QaAuditScreenBrowserDatabaseIntegrationTest',
      [ValidatePattern('^oneshop[a-z0-9_]*_test$')][string]$Database='oneshop_fix_20261024_test',[switch]$FullSuite)
$ErrorActionPreference='Stop'
Set-Location (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent)
$output="docs/qa-audit/fix-verification/evidence/$Phase"
New-Item -ItemType Directory -Force $output | Out-Null
New-Item -ItemType Directory -Force target/qa-fix | Out-Null
$arguments=@('-B',"-Doneshop.test.db=$Database","-Doneshop.qa.evidence-dir=$output",'-Djacoco.destFile=target/qa-fix/fix.exec')
if(!$FullSuite -and $Tests){$arguments+="-Dtest=$Tests"}
& mvn @arguments org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent test 2>&1 | Out-File -Encoding utf8 "target/qa-fix/$Phase.log"
$code=$LASTEXITCODE
python scripts/qa-audit/fix-results.py $Phase $Tests
exit $code
