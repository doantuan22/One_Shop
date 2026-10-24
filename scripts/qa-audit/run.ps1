param([ValidateSet('baseline','audit','reproduce','coverage','package')][string]$Phase='audit')
$ErrorActionPreference='Stop'
Set-Location (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent)
$db='-Doneshop.test.db=oneshop_qa_20261024_test'
$base=@('-B',$db,'-Djacoco.destFile=target/qa-audit/session-20261024.exec','-Djacoco.dataFile=target/qa-audit/session-20261024.exec')
$agent='org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent'
$report='org.jacoco:jacoco-maven-plugin:0.8.12:report'
New-Item -ItemType Directory -Force target/qa-audit | Out-Null
switch($Phase) {
 'baseline' { & mvn @base '-Dtest=!QaAudit*' $agent test 2>&1 | Out-File -Encoding utf8 target/qa-audit/baseline.log }
 'audit' { & mvn @base '-Dtest=QaAudit*' $agent test 2>&1 | Out-File -Encoding utf8 target/qa-audit/additional.log }
 'reproduce' { & mvn @base '-Dtest=QaAuditRaceDatabaseIntegrationTest,QaAuditImageRollbackDatabaseIntegrationTest,QaAuditScreenBrowserDatabaseIntegrationTest' $agent test 2>&1 | Out-File -Encoding utf8 target/qa-audit/reproduce.log }
 'coverage' { & mvn @base $report 2>&1 | Out-File -Encoding utf8 target/qa-audit/coverage.log }
 'package' { & mvn @base '-DskipTests' package 2>&1 | Out-File -Encoding utf8 target/qa-audit/package.log }
}
$code=$LASTEXITCODE
if($Phase -eq 'baseline'){python scripts/qa-audit/results.py baseline}
if($Phase -eq 'audit'){python scripts/qa-audit/results.py additional}
if($Phase -eq 'reproduce'){python scripts/qa-audit/results.py reproduce}
exit $code
