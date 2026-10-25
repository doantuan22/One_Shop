<# Prints the BCrypt hash the application accepts. Needs the built jar (.\mvnw.cmd -DskipTests package) and Java 21.
   The password comes from $env:ONESHOP_PASSWORD or is typed at the prompt (not echoed); it is never printed. #>
$ErrorActionPreference = 'Stop'
$jar = Join-Path $PSScriptRoot '..\..\target\oneshop.jar'
if (-not (Test-Path $jar)) { throw "Build first: .\mvnw.cmd -DskipTests package ($jar not found)" }
if (-not $env:ONESHOP_PASSWORD) {
  $s = Read-Host 'Password (8-72 characters)' -AsSecureString
  $env:ONESHOP_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringUni([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s))
}
try { & java '-Dloader.main=com.oneshop.tools.PasswordHashTool' -cp $jar org.springframework.boot.loader.launch.PropertiesLauncher }
finally { Remove-Item Env:ONESHOP_PASSWORD -ErrorAction SilentlyContinue }
