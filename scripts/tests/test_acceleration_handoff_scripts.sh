#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

grep -q "libta4j-metal-accelerator.dylib" "$ROOT/scripts/acceleration/build-metal-provider.sh"
grep -q "MTLCreateSystemDefaultDevice" "$ROOT/scripts/acceleration/metal-provider-smoke.m"
grep -q "NOT_IMPLEMENTED" "$ROOT/ta4j-accelerator/src/main/java/org/ta4j/acceleration/internal/providers/CudaAccelerationProviderFactory.java"
grep -q "nvidia-smi" "$ROOT/scripts/acceleration/windows-cuda-handoff.ps1"
grep -q "ta4j.forecast.monte-carlo-price.v1" "$ROOT/scripts/acceleration/windows-cuda-handoff.ps1"

echo "acceleration handoff script fixtures passed"
