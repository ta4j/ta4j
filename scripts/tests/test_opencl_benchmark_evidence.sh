#!/usr/bin/env bash
set -euo pipefail

# OpenCL qualification claims in the docs must reference committed evidence.
# The feature plan requires the validation report to be committed with the
# change (the Metal lane commits docs/indicator-acceleration-metal-benchmark.md);
# .agents/ is gitignored, so a doc reference to a path under .agents/ is a
# dangling pointer that no reviewer or CI can ever verify.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PLAN_DOC="$ROOT/docs/indicator-acceleration-opencl-plan.md"
ACCEL_DOC="$ROOT/docs/indicator-acceleration.md"
EVIDENCE_DOC="$ROOT/docs/indicator-acceleration-opencl-benchmark.md"
GITIGNORED_REF=".agents/benchmarks/cf-336-validation/cf-336-transparent-opencl-backtest.json"

fail() { echo "[FAIL] $1" >&2; exit 1; }
pass() { echo "[PASS] $1"; }

if [[ ! -f "$PLAN_DOC" ]]; then
    fail "OpenCL plan document is absent: $PLAN_DOC"
fi

if grep -Fq "$GITIGNORED_REF" "$PLAN_DOC"; then
    fail "docs/indicator-acceleration-opencl-plan.md references the gitignored runtime artifact $GITIGNORED_REF instead of committed evidence"
fi
if grep -Fq "$GITIGNORED_REF" "$ACCEL_DOC"; then
    fail "docs/indicator-acceleration.md references the gitignored runtime artifact $GITIGNORED_REF instead of committed evidence"
fi

if [[ ! -f "$EVIDENCE_DOC" ]]; then
    fail "OpenCL benchmark evidence doc is absent: $EVIDENCE_DOC"
fi
if ! git -C "$ROOT" ls-files --error-unmatch "$EVIDENCE_DOC" >/dev/null 2>&1; then
    fail "OpenCL benchmark evidence doc is not committed: $EVIDENCE_DOC"
fi

pass "OpenCL qualification evidence is committed and referenced"
