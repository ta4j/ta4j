#!/usr/bin/env bash
# Validates the Linux OpenCL acceleration classifier inside a PoCL container.
# Usage: validate-opencl-linux.sh <repo-root> <x86_64|aarch64> [--benchmark]
# Exit code is non-zero on any failure.
set -euo pipefail

ROOT="$(cd "$1" && pwd)"
ARCH="$2"
BENCHMARK="${3:-}"

if [[ "$ARCH" != "x86_64" && "$ARCH" != "aarch64" ]]; then
    echo "error: arch must be x86_64 or aarch64, found '$ARCH'" >&2
    exit 2
fi

cd "$ROOT"

echo "==> OpenCL validation on linux-$ARCH (pwd: $PWD)"

echo "==> OpenCL platform check"
if ! command -v clinfo >/dev/null 2>&1; then
    echo "error: clinfo is not installed" >&2
    exit 1
fi
CLINFO_FILE="$(mktemp "${TMPDIR:-/tmp}/ta4j-opencl-clinfo.XXXXXX")"
trap 'rm -f "$CLINFO_FILE"' EXIT
clinfo >"$CLINFO_FILE"
if ! grep -qi 'cl_khr_fp64' "$CLINFO_FILE"; then
    echo "error: no OpenCL device advertises cl_khr_fp64" >&2
    cat "$CLINFO_FILE" >&2 || true
    exit 1
fi
grep -iE 'Platform Name|Device Name|Device Type|cl_khr_fp64' "$CLINFO_FILE" | head -20

LIBRARY="$ROOT/ta4j-cli/target/native/opencl-$ARCH/package/META-INF/native/linux-$ARCH/libta4j-opencl-accelerator.so"
EXCLUDED="requires-cuda,requires-metal,requires-display,requires-headless"

echo "==> Building ta4j-cli classifier for opencl-linux-$ARCH"
./mvnw -B -ntp -pl ta4j-cli -am "-Popencl-linux-$ARCH" package
if [[ ! -f "$LIBRARY" ]]; then
    echo "error: native library was not built at $LIBRARY" >&2
    exit 1
fi
sha256sum "$LIBRARY"

echo "==> Running OpenClNativeIntegrationTest (probe self-test + scalar parity)"
./mvnw -B -ntp -pl ta4j-cli -am \
    -Dtest=OpenClNativeIntegrationTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Dgroups=requires-opencl \
    -Dta4j.excludedTestTags="$EXCLUDED" \
    -Dta4j.acceleration.opencl.library="$LIBRARY" \
    test

if [[ "$BENCHMARK" == "--benchmark" ]]; then
    REPORT="$ROOT/.agents/benchmarks/cf-336-validation/cf-336-transparent-opencl-backtest.json"
    rm -f "$REPORT"
    echo "==> Running OpenClBacktestBenchmarkTest (transparent end-to-end backtest)"
    ./mvnw -B -ntp -pl ta4j-cli -am \
        -Dtest=OpenClBacktestBenchmarkTest \
        -Dsurefire.failIfNoSpecifiedTests=false \
        -Dgroups=benchmark \
        -Dta4j.runBenchmarks=true \
        -Dta4j.excludedTestTags="$EXCLUDED" \
        -Dta4j.acceleration.opencl.library="$LIBRARY" \
        test
    if [[ ! -f "$REPORT" ]]; then
        echo "error: benchmark report was not written at $REPORT" >&2
        exit 1
    fi
    echo "==> Benchmark report: $REPORT"
fi

echo "==> OpenCL validation PASSED on linux-$ARCH"
