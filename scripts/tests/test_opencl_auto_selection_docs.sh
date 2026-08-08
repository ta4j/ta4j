#!/usr/bin/env bash
set -euo pipefail

# The OpenCL auto-selection gate documented in docs/indicator-acceleration.md
# must match OpenClCrossoverModel. The remediation that added the 2 GiB device
# global-memory floor to the crossover model updated the platform-boundary
# paragraph but left the qualification-status bullet describing only the
# 16,777,216 path-step workload floor, so readers believe a small-memory GPU is
# auto-selected when the code rejects it.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ACCEL_DOC="$ROOT/docs/indicator-acceleration.md"

fail() { echo "[FAIL] $1" >&2; exit 1; }
pass() { echo "[PASS] $1"; }

if ! grep -q 'Automatic selection is gated to GPU' "$ACCEL_DOC"; then
    fail "docs/indicator-acceleration.md no longer documents the OpenCL auto-selection gate"
fi
if ! grep -q '16,777,216 path-steps' "$ACCEL_DOC"; then
    fail "docs/indicator-acceleration.md no longer documents the OpenCL workload floor"
fi
if ! grep -qE '16,777,216 path-steps and (at least )?2 GiB' "$ACCEL_DOC"; then
    fail "docs/indicator-acceleration.md documents only the workload floor and omits the 2 GiB device-memory gate that OpenClCrossoverModel enforces"
fi

pass "OpenCL auto-selection gate is documented consistently with the crossover model"
