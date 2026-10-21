$ErrorActionPreference = 'Continue'
$ts = Get-Date -Format 'yyyyMMdd_HHmmss'
$log = "D:\One_Shop\targeted_phase11_$ts.log"
"START $ts" | Out-File $log -Encoding utf8
Set-Location D:\One_Shop
& .\mvnw.cmd -B '-Dtest=Phase11AdminDatabaseIntegrationTest#productStoreAndStoreProductWithHistoryAreSoftDisabledAndNotHardDeleted' -DfailIfNoTests=false '-Doneshop.test.db=oneshop_v3_verify_test' test 2>&1 | Tee-Object -FilePath $log -Append
"EXIT_CODE=$LASTEXITCODE" | Out-File $log -Append -Encoding utf8
