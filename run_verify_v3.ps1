$ErrorActionPreference = 'Continue'
$ts = Get-Date -Format 'yyyyMMdd_HHmmss'
$log = "D:\One_Shop\verify_run_v3verify_$ts.log"
"START $ts DB=oneshop_v3_verify_test" | Out-File $log -Encoding utf8
Set-Location D:\One_Shop
& .\mvnw.cmd -B clean verify "-Doneshop.test.db=oneshop_v3_verify_test" 2>&1 | Tee-Object -FilePath $log -Append
"EXIT_CODE=$LASTEXITCODE" | Out-File $log -Append -Encoding utf8
