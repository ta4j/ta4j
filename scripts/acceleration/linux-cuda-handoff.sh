#!/usr/bin/env bash
set -euo pipefail

repo_root="${1:-.}"
root="$(cd "$repo_root" && pwd)"
plan="$root/docs/indicator-acceleration-cuda-plan.md"

if [[ ! -f "$plan" ]]; then
  echo "CUDA implementation plan not found: $plan" >&2
  exit 2
fi

echo "CF-336 Linux CUDA continuation root: $root"
echo "Implementation plan: $plan"
echo "Frozen Linux handoff: $root/docs/indicator-acceleration-cuda-linux-handoff.md"
echo
echo "Required preflight and validation commands:"
cat <<'COMMANDS'
  hostnamectl
  nvidia-smi
  nvcc --version
  gcc --version
  cmake --version
  java -version
  ./mvnw -B -pl ta4j-accelerator -am test
  ./mvnw -B -pl ta4j-accelerator -am -Dgroups=integration -Dta4j.excludedTestTags= test
  scripts/run-full-build-quiet.sh
COMMANDS

echo
echo "Linux-only checkpoints:"
echo "  1. Build libta4j-cuda-accelerator.so from the frozen native ABI and CUDA sources."
echo "  2. Validate supported distribution, GCC, glibc, libstdc++, JNI, and CUDA runtime linkage."
echo "  3. Exercise classpath extraction, permissions, checksum, ldd, and wrong-architecture failures."
echo "  4. Run native sanitizer plus Java integration, concurrency, memory-pressure, and device-loss tests."
echo "  5. Emit the Linux manifest/report without changing Windows or Metal golden fixtures."
echo
echo "Frozen contracts: CUDA ABI 1, RNG version 1, FP64 tolerance 1e-4, shared ta4j_cuda_jni.cu."
