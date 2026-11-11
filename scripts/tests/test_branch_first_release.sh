#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
BASH_ENV=/dev/null python3 "$root/scripts/tests/branch_first_release_test.py"
