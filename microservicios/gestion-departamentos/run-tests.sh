#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"
export SKIP_DB_INIT=1
rm -rf reports
mkdir -p reports
python -m pytest test \
  --junitxml=reports/junit.xml \
  --cov=app \
  --cov-report=xml:coverage.xml \
  --cov-report=term-missing
