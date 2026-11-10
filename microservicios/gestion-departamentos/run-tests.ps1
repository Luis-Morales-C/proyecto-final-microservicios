$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root
$env:SKIP_DB_INIT="1"
if (Test-Path "reports") { Remove-Item "reports" -Recurse -Force }
New-Item -ItemType Directory -Path "reports" | Out-Null
python -m pytest test `
  --junitxml=reports/junit.xml `
  --cov=app `
  --cov-report=xml:coverage.xml `
  --cov-report=term-missing
exit $LASTEXITCODE
