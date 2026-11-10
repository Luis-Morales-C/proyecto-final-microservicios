#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPORTS="$ROOT/reports"
PROJECT="$ROOT/tests/AuthService.Tests/AuthService.Tests.csproj"
rm -rf "$REPORTS"
mkdir -p "$REPORTS"
dotnet test "$PROJECT" -c Release \
  --logger "trx;LogFileName=results.trx" \
  --results-directory "$REPORTS" \
  /p:CollectCoverage=true \
  /p:CoverletOutput="$REPORTS/coverage.xml" \
  /p:CoverletOutputFormat=cobertura
