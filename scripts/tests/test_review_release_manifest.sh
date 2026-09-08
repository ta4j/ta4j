#!/usr/bin/env bash
set -euo pipefail

# Policy check: the release artifact manifest gate
# (scripts/release/release_helpers.sh artifact-manifest --strict, invoked by
# publish-release.yml after `-Pproduction-release package`) must accept every
# artifact produced by the reactor. The ta4j-acceleration module owns optional
# platform-native classifier JARs; their absence is valid in the ordinary
# default build, while unknown target JARs must still fail strict validation.
#
# The check simulates both the default release tree and a tree containing all
# known native classifiers with fake jars, then runs the exact helper
# invocation used by publish-release.yml.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
HELPER="$ROOT/scripts/release/release_helpers.sh"
VERSION="0.24.1"

fail() { echo "[FAIL] $1" >&2; exit 1; }
pass() { echo "[PASS] $1"; }

[[ -f "$HELPER" ]] || fail "release helper must exist: $HELPER"

make_tree() {
  local tree="$1"
  shift
  local file
  for template in "$@"; do
    file="${template//%s/$VERSION}"
    mkdir -p "$tree/$(dirname "$file")"
    : > "$tree/$file"
  done
}

CORE_JARS=(
  "ta4j-core/target/ta4j-core-%s.jar"
  "ta4j-core/target/ta4j-core-%s-sources.jar"
  "ta4j-core/target/ta4j-core-%s-javadoc.jar"
  "ta4j-core/target/ta4j-core-%s-tests.jar"
)
EXAMPLES_JARS=(
  "ta4j-examples/target/ta4j-examples-%s.jar"
  "ta4j-examples/target/ta4j-examples-%s-sources.jar"
  "ta4j-examples/target/ta4j-examples-%s-javadoc.jar"
)
CLI_JARS=(
  "ta4j-cli/target/ta4j-cli-%s.jar"
  "ta4j-cli/target/ta4j-cli-%s-jar-with-dependencies.jar"
  "ta4j-cli/target/ta4j-cli-%s-sources.jar"
  "ta4j-cli/target/ta4j-cli-%s-javadoc.jar"
)
ACCELERATION_JARS=(
  "ta4j-acceleration/target/ta4j-acceleration-%s.jar"
  "ta4j-acceleration/target/ta4j-acceleration-%s-sources.jar"
  "ta4j-acceleration/target/ta4j-acceleration-%s-javadoc.jar"
)
NATIVE_CLASSIFIER_JARS=(
  "ta4j-acceleration/target/ta4j-acceleration-%s-metal-macos-aarch64.jar"
  "ta4j-acceleration/target/ta4j-acceleration-%s-cuda-windows-x86_64.jar"
  "ta4j-acceleration/target/ta4j-acceleration-%s-cuda-linux-x86_64.jar"
  "ta4j-acceleration/target/ta4j-acceleration-%s-opencl-linux-x86_64.jar"
  "ta4j-acceleration/target/ta4j-acceleration-%s-opencl-linux-aarch64.jar"
)

ALL_JARS=("${CORE_JARS[@]}" "${EXAMPLES_JARS[@]}" "${CLI_JARS[@]}" "${ACCELERATION_JARS[@]}")
tmp="$(mktemp -d "${TMPDIR:-/tmp}/ta4j-manifest-sim.XXXXXX")"
trap 'rm -rf "$tmp"' EXIT


make_tree "$tmp" "${ALL_JARS[@]}"
if (cd "$tmp" && bash "$HELPER" artifact-manifest --version "$VERSION" --output manifest.txt --strict >/dev/null 2>&1); then
  pass "artifact-manifest --strict accepts the default reactor artifact set without native profiles"
else
  fail "artifact-manifest --strict rejected the default reactor artifacts (expected success)"
fi

make_tree "$tmp" "${NATIVE_CLASSIFIER_JARS[@]}"
if (cd "$tmp" && bash "$HELPER" artifact-manifest --version "$VERSION" --output manifest.txt --strict >/dev/null 2>&1); then
  pass "artifact-manifest --strict accepts all known acceleration classifier artifacts"
else
  fail "artifact-manifest --strict rejected known acceleration classifier artifacts (expected success)"
fi

printf 'unknown classifier\n' > "$tmp/ta4j-acceleration/target/ta4j-acceleration-${VERSION}-unknown.jar"
if (cd "$tmp" && bash "$HELPER" artifact-manifest --version "$VERSION" --output manifest.txt --strict >/dev/null 2>&1); then
  fail "unknown acceleration classifier must fail strict manifest validation"
fi
rm -f "$tmp/ta4j-acceleration/target/ta4j-acceleration-${VERSION}-unknown.jar"
pass "artifact-manifest --strict rejects unknown acceleration classifiers"

# Negative control: the gate must keep failing when a required artifact is
# missing; the check is not satisfied by relaxing the manifest.
rm -f "$tmp/ta4j-core/target/ta4j-core-${VERSION}-tests.jar"
if (cd "$tmp" && bash "$HELPER" artifact-manifest --version "$VERSION" --output manifest.txt --strict >/dev/null 2>&1); then
  fail "missing core tests jar must still fail the manifest gate"
fi
pass "artifact-manifest --strict still detects a missing core artifact"

echo "release artifact manifest simulation passed"
