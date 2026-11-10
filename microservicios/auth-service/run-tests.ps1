$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$reports = Join-Path $root "reports"
$project = Join-Path $root "tests\AuthService.Tests\AuthService.Tests.csproj"
if (Test-Path $reports) { Remove-Item $reports -Recurse -Force }
New-Item -ItemType Directory -Path $reports | Out-Null
$coverage = (Join-Path $reports "coverage.xml").Replace("\", "/")
dotnet test $project -c Release `
  --logger "trx;LogFileName=results.trx" `
  --results-directory $reports `
  /p:CollectCoverage=true `
  /p:CoverletOutput=$coverage `
  /p:CoverletOutputFormat=cobertura
exit $LASTEXITCODE
