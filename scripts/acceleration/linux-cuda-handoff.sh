#!/usr/bin/env bash
set -euo pipefail

repo_root="${1:-.}"
root="$(cd "$repo_root" && pwd)"

if [[ ! -d "$root/ta4j-cli" ]]; then
  echo "ta4j-cli module not found: $root" >&2
  exit 2
fi

echo "CF-336 Linux CUDA continuation root: $root"
echo "Implementation plan: https://github.com/ta4j/ta4j-wiki/wiki/Indicator-Acceleration-CUDA-Plan"
echo "Frozen Linux handoff: https://github.com/ta4j/ta4j-wiki/wiki/Indicator-Acceleration-CUDA-Linux-Handoff"
echo
echo "Required preflight and validation commands:"
cat <<'COMMANDS'
  hostnamectl
  nvidia-smi
  nvcc --version
  gcc --version
  cmake --version
  java -version
  ./mvnw -B -pl ta4j-cli -am -Pcuda-linux-x86_64 -DskipTests package
  ./mvnw -B -pl ta4j-cli -am -Dtest=CudaNativeIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false -Dgroups=requires-cuda -Dta4j.excludedTestTags=requires-metal -Dta4j.acceleration.cuda.library="$PWD/ta4j-cli/target/native/cuda/package/META-INF/native/linux-x86_64/libta4j-cuda-accelerator.so" test
  scripts/run-full-build-quiet.sh
COMMANDS

echo
echo "Linux-only checkpoints:"
echo "  1. Build the ta4j-cli cuda-linux-x86_64 classifier from the frozen native ABI and CUDA sources."
echo "  2. Validate supported distribution, GCC, glibc, libstdc++, JNI, and CUDA runtime linkage."
echo "  3. Exercise classpath extraction, permissions, checksum, ldd, and wrong-architecture failures."
echo "  4. Run native sanitizer plus Java integration, concurrency, memory-pressure, and device-loss tests."
echo "  5. Emit the Linux manifest/report without changing Windows or Metal golden fixtures."
echo
echo "Frozen contracts: CUDA ABI 1, RNG version 1, FP64 tolerance 1e-4, shared ta4j_cuda_jni.cu."
