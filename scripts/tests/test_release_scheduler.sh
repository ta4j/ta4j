#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
node --test "$ROOT/scripts/tests/release_scheduler_test.cjs"
python3 "$ROOT/scripts/tests/release_scheduler_test.py"
