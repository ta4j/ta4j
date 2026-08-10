#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/ta4j-acceleration-handoff.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

grep -q "libta4j-metal-accelerator.dylib" "$ROOT/scripts/acceleration/build-metal-provider.sh"
grep -q "MTLCreateSystemDefaultDevice" "$ROOT/ta4j-cli/src/main/native/metal/ta4j_metal_jni.m"
if grep -q "NOT_IMPLEMENTED" "$ROOT/ta4j-cli/src/main/java/org/ta4j/cli/acceleration/internal/providers/CudaAccelerationProviderFactory.java"; then
  echo "CUDA factory must not retain its unavailable skeleton" >&2
  exit 1
fi
grep -Fq 'mvnw.cmd' "$ROOT/scripts/acceleration/windows-cuda-handoff.ps1"
grep -q "ta4j.forecast.monte-carlo-price.v1" "$ROOT/scripts/acceleration/windows-cuda-handoff.ps1"
grep -q "cuda-windows-x86_64" "$ROOT/scripts/acceleration/windows-cuda-handoff.ps1"
grep -q "ta4j.runBenchmarks=true" "$ROOT/scripts/acceleration/benchmark-cuda-provider.ps1"
grep -q "status --porcelain" "$ROOT/scripts/acceleration/benchmark-cuda-provider.ps1"
grep -q "uname -m" "$ROOT/scripts/acceleration/build-metal-provider.sh"
grep -Fq '<extraJar>${ta4j.native.resourceJar}</extraJar>' "$ROOT/ta4j-cli/pom.xml"
if grep -Fq '<directory>${ta4j.native.package}</directory>' "$ROOT/ta4j-cli/pom.xml"; then
  echo "native profiles must not copy platform libraries into the unclassified CLI jar" >&2
  exit 1
fi

BASH_ENV=/dev/null bash "$ROOT/scripts/acceleration/linux-cuda-handoff.sh" "$ROOT" >"$TMP/linux-output"
grep -q "nvidia-smi" "$TMP/linux-output"
grep -q "./mvnw -B -pl ta4j-cli -am -Pcuda-linux-x86_64" "$TMP/linux-output"
grep -q "libta4j-cuda-accelerator.so" "$TMP/linux-output"
grep -q "ta4j-wiki/wiki/Indicator-Acceleration-CUDA-Plan" "$TMP/linux-output"
grep -q "ta4j-wiki/wiki/Indicator-Acceleration-CUDA-Linux-Handoff" "$TMP/linux-output"

if BASH_ENV=/dev/null bash "$ROOT/scripts/acceleration/linux-cuda-handoff.sh" "$TMP" >"$TMP/missing-output" 2>&1; then
  echo "linux CUDA handoff should reject a root without the ta4j-cli module" >&2
  exit 1
fi
grep -q "ta4j-cli module not found" "$TMP/missing-output"

echo "acceleration handoff script fixtures passed"
