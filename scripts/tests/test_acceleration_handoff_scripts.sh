#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/ta4j-acceleration-handoff.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

BASH_ENV=/dev/null bash "$ROOT/scripts/acceleration/linux-cuda-handoff.sh" "$ROOT" >"$TMP/linux-output"
grep -q "nvidia-smi" "$TMP/linux-output"
grep -q "./mvnw -B -pl ta4j-acceleration -am -Pcuda-linux-x86_64" "$TMP/linux-output"
grep -q "libta4j-cuda-accelerator.so" "$TMP/linux-output"
grep -q "ta4j-wiki/wiki/Indicator-Acceleration#linux-cuda-qualification" "$TMP/linux-output"
grep -q "ta4j-wiki/wiki/Indicator-Acceleration" "$TMP/linux-output"

if BASH_ENV=/dev/null bash "$ROOT/scripts/acceleration/linux-cuda-handoff.sh" "$TMP" >"$TMP/missing-output" 2>&1; then
  echo "linux CUDA handoff should reject a root without the ta4j-acceleration module" >&2
  exit 1
fi
grep -q "ta4j-acceleration module not found" "$TMP/missing-output"

echo "acceleration handoff script fixtures passed"
