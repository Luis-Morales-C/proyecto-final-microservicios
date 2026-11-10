#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"
mkdir -p reports
go test ./... -coverprofile=coverage.out
go tool cover -func=coverage.out | tee reports/coverage.txt
