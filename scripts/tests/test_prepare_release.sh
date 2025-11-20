#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# test_prepare_release.sh
#
# Tests the responsibilities of prepare-release.sh:
#   - CHANGELOG transformation
#   - README version updates
#   - Release notes file creation
#   - Output variables
# =============================================================================

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT="$ROOT/scripts/prepare-release.sh"

cleanup() { [[ -d "$TMP" ]] && rm -rf "$TMP"; }
trap cleanup EXIT

fail() { echo "[FAIL] $1" >&2; exit 1; }
pass() { echo "[PASS] $1"; }

expect_contains() {
  local haystack="$1"
  local needle="$2"
  local msg="$3"
  if ! grep -Fq "$needle" <<<"$haystack"; then
    fail "$msg (missing: '$needle')"
  fi
}

expect_file_contains() {
  local file="$1"
  local needle="$2"
  local msg="$3"
  if ! grep -Fq -- "$needle" "$file"; then
    fail "$msg (missing: '$needle')"
  fi
}

expect_file_matches() {
  local file="$1"
  local regex="$2"
  local msg="$3"
  if ! grep -Eq -- "$regex" "$file"; then
    fail "$msg (regex mismatch: '$regex')"
  fi
}

run_test() {
  TMP="$(mktemp -d)"
  mkdir -p "$TMP/scripts"
  cp "$SCRIPT" "$TMP/scripts/prepare-release.sh"
  chmod +x "$TMP/scripts/prepare-release.sh"

  pushd "$TMP" >/dev/null || exit 1
}

finish_test() {
  popd >/dev/null || exit 1
  rm -rf "$TMP"
}

# -----------------------------------------------------------------------------
# TEST 1: Basic Release Flow
# -----------------------------------------------------------------------------
test_basic_flow() {
  echo "Running test_basic_flow"

  run_test

  cat > CHANGELOG.md <<'EOF'
## Unreleased

- Added new Indicator ABC.

## 1.2.3 (2024-01-01)
- Older note
EOF

  cat > README.md <<'EOF'
<dependency>
  <artifactId>ta4j-core</artifactId>
  <version>0.0.1</version>
</dependency>
EOF

  OUT="$(scripts/prepare-release.sh 1.3.0)"

  expect_contains "$OUT" "release_version=1.3.0" "script should print release version"
  expect_contains "$OUT" "release_notes_file=release/1.3.0.md" "script should print release notes file path"

  expect_file_contains CHANGELOG.md "## Unreleased" "Unreleased should be preserved"
  expect_file_contains CHANGELOG.md "- _No changes yet._" "Unreleased should be reset with placeholder"

  expect_file_matches CHANGELOG.md "## 1\\.3\\.0 \\([0-9]{4}-[0-9]{2}-[0-9]{2}\\)" "new release header should exist"
  expect_file_contains CHANGELOG.md "Added new Indicator ABC." "notes should move into release section"

  expect_file_contains release/1.3.0.md "Added new Indicator ABC." "release notes file should include notes"
  expect_file_matches release/1.3.0.md "^## 1\\.3\\.0 \\([0-9]{4}-[0-9]{2}-[0-9]{2}\\)" "release notes should have correct header"

  expect_file_contains README.md "<version>1.3.0</version>" "README version should be updated"

  finish_test
  pass "test_basic_flow"
}

# -----------------------------------------------------------------------------
# TEST 2: Missing Unreleased Section
# -----------------------------------------------------------------------------
test_missing_unreleased() {
  echo "Running test_missing_unreleased"

  run_test

  cat > CHANGELOG.md <<'EOF'
## 1.2.0 (2023-12-01)
- Old release
EOF

  echo "placeholder" > README.md

  OUT="$(scripts/prepare-release.sh 1.3.0)"

  expect_file_contains CHANGELOG.md "## Unreleased" "should create Unreleased"
  expect_file_contains CHANGELOG.md "## 1.3.0" "should add release section"

  finish_test
  pass "test_missing_unreleased"
}

# -----------------------------------------------------------------------------
# TEST 3: Empty CHANGELOG
# -----------------------------------------------------------------------------
test_empty_changelog() {
  echo "Running test_empty_changelog"

  run_test

  touch CHANGELOG.md
  echo "placeholder" > README.md

  OUT="$(scripts/prepare-release.sh 1.0.0)"

  expect_file_contains CHANGELOG.md "## Unreleased" "should add Unreleased"
  expect_file_contains CHANGELOG.md "## 1.0.0" "should add release section"
  expect_file_contains CHANGELOG.md "- _No changes yet._" "should add placeholder"

  finish_test
  pass "test_empty_changelog"
}

# -----------------------------------------------------------------------------
main() {
  test_basic_flow
  test_missing_unreleased
  test_empty_changelog

  echo "All tests passed."
}

main "$@"
