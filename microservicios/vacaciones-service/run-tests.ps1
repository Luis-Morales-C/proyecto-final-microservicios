$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$reports = Join-Path $root "reports"
$testProject = Join-Path $root "tests\VacacionesService.Tests\VacacionesService.Tests.csproj"

if (Test-Path $reports) {
    Remove-Item $reports -Recurse -Force
}
New-Item -ItemType Directory -Path $reports | Out-Null

$coveragePath = (Join-Path $reports "coverage.xml").Replace("\\", "/")

dotnet test $testProject `
    -c Release `
    --logger "trx;LogFileName=results.trx" `
    --results-directory $reports `
    /p:CollectCoverage=true `
    /p:CoverletOutput=$coveragePath `
    /p:CoverletOutputFormat=cobertura

exit $LASTEXITCODE
