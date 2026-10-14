#!/usr/bin/env sh
set -eu
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
REPORTS="$ROOT/reports"
rm -rf "$REPORTS"
mkdir -p "$REPORTS"

dotnet test "$ROOT/tests/VacacionesService.Tests/VacacionesService.Tests.csproj" \
  -c Release \
  --logger "trx;LogFileName=results.trx" \
  --results-directory "$REPORTS" \
  /p:CollectCoverage=true \
  /p:CoverletOutput="$REPORTS/coverage.xml" \
  /p:CoverletOutputFormat=cobertura
