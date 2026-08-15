#!/usr/bin/env bash
set -euo pipefail

# Policy check: the hosted tag-selection workflows must exclude the hardware
# test tags. The branch introduced @Tag("requires-cuda"), @Tag("requires-metal"),
# and @Tag("requires-opencl") tests whose first action asserts a configured
# native library path; GitHub-hosted runners have no such library, so any job
# that selects them fails. The branch updated the root pom default,
# run-full-build-quiet.sh, and README but missed both workflow files.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
INTEGRATION_WORKFLOW="$ROOT/.github/workflows/test-tag-integration.yml"
BENCHMARK_WORKFLOW="$ROOT/.github/workflows/test-tag-benchmark.yml"

HARDWARE_TAGS="requires-cuda requires-metal requires-opencl"
BASELINE_TAGS="analysis-demo benchmark requires-display requires-headless"

fail() { echo "[FAIL] $1" >&2; exit 1; }
pass() { echo "[PASS] $1"; }

extract_excluded_tags() {
  grep -o -- '-Dta4j\.excludedTestTags=[^"'"'"'[:space:]]*' "$1" | head -1 | cut -d= -f2-
}

require_tags() {
  local workflow="$1" tags="$2" label="$3"
  [[ -f "$workflow" ]] || fail "$(basename "$workflow") must exist"
  local excluded
  excluded="$(extract_excluded_tags "$workflow")" || true
  [[ -n "$excluded" ]] || fail "$(basename "$workflow") must pass -Dta4j.excludedTestTags"
  local tag
  for tag in $tags; do
    if [[ ",$excluded," != *",$tag,"* ]]; then
      fail "$(basename "$workflow") must exclude $tag ($label)"
    fi
  done
}

require_tags "$INTEGRATION_WORKFLOW" "$HARDWARE_TAGS" "the new native integration tests assert a configured library and fail on hosted runners"
pass "test-tag-integration.yml excludes all hardware test tags"

require_tags "$BENCHMARK_WORKFLOW" "$HARDWARE_TAGS" "the new hardware backtest benchmarks assert a configured library and fail on hosted runners"
pass "test-tag-benchmark.yml excludes all hardware test tags"

# Negative control: existing non-hardware exclusions must remain.
excluded="$(extract_excluded_tags "$INTEGRATION_WORKFLOW")"
for tag in $BASELINE_TAGS; do
  if [[ ",$excluded," != *",$tag,"* ]]; then
    fail "baseline exclusion $tag must remain present in test-tag-integration.yml"
  fi
done
pass "test-tag-integration.yml keeps baseline exclusions"

echo "hosted tag-selection workflow exclusions passed"
