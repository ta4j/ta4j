#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
node --test "$root/scripts/tests/release_freeze_test.cjs"
