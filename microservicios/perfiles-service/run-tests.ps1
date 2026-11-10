$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

if (Test-Path "coverage.out") {
    Remove-Item "coverage.out" -Force
}

New-Item -ItemType Directory -Path "reports" -Force | Out-Null

go test ./... -coverprofile coverage.out

if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

go tool cover -func coverage.out |
    Tee-Object -FilePath .\reports\coverage.txt

go tool cover -html coverage.out -o coverage.html

exit $LASTEXITCODE