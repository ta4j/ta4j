#!/usr/bin/env bash
set -euo pipefail

# Policy check: the release artifact manifest gate
# (scripts/release/release_helpers.sh artifact-manifest --strict, invoked by
# publish-release.yml after `-Pproduction-release package`) must accept the
# artifacts the reactor produces. The new ta4j-cli module is part of the
# reactor and produces ta4j-cli/target/ta4j-cli-<version>[-sources|-javadoc].jar
# during the release build, but the manifest's expected list only covered
# ta4j-core and ta4j-examples, so --strict failed the release with
# "Unexpected target jars".
#
# The check simulates the release build tree with fake jars and runs the exact
# helper invocation used by publish-release.yml.

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

ALL_JARS=("${CORE_JARS[@]}" "${EXAMPLES_JARS[@]}" "${CLI_JARS[@]}")

tmp="$(mktemp -d "${TMPDIR:-/tmp}/ta4j-manifest-sim.XXXXXX")"
trap 'rm -rf "$tmp"' EXIT

# Positive case: the full reactor artifact set must pass --strict.
make_tree "$tmp" "${ALL_JARS[@]}"
if (cd "$tmp" && bash "$HELPER" artifact-manifest --version "$VERSION" --output manifest.txt --strict >/dev/null 2>&1); then
  pass "artifact-manifest --strict accepts the ta4j-cli reactor artifacts"
else
  fail "artifact-manifest --strict rejected the reactor artifacts (expected success)"
fi

# Negative control: the gate must keep failing when a required artifact is
# missing; the check is not satisfied by relaxing the manifest.
rm -f "$tmp/ta4j-core/target/ta4j-core-${VERSION}-tests.jar"
if (cd "$tmp" && bash "$HELPER" artifact-manifest --version "$VERSION" --output manifest.txt --strict >/dev/null 2>&1); then
  fail "missing core tests jar must still fail the manifest gate"
fi
pass "artifact-manifest --strict still detects a missing core artifact"

echo "release artifact manifest simulation passed"
