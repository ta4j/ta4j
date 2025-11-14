#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT_SOURCE="$REPO_ROOT/scripts/prepare-release.sh"

# Global cleanup tracking
CLEANUP_DIRS=()

cleanup_all() {
  local dir
  for dir in "${CLEANUP_DIRS[@]}"; do
    # Skip empty strings (from array removal operations)
    [[ -z "$dir" ]] && continue
    if [[ -d "$dir" ]]; then
      rm -rf "$dir" 2>/dev/null || true
    fi
  done
}

# Set up global cleanup trap for all exit scenarios
trap cleanup_all EXIT INT TERM ERR

fail() {
  echo "[FAIL] $1" >&2
  exit 1
}

pass() {
  echo "[PASS] $1"
}

create_repo() {
  local pom_version="$1"
  local repo
  repo="$(mktemp -d)"
  # Track this directory for global cleanup
  CLEANUP_DIRS+=("$repo")
  
  mkdir -p "$repo/scripts"
  cp "$SCRIPT_SOURCE" "$repo/scripts/prepare-release.sh"
  chmod +x "$repo/scripts/prepare-release.sh"

  cat <<POM > "$repo/pom.xml"
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.ta4j</groupId>
  <artifactId>ta4j-parent</artifactId>
  <version>${pom_version}</version>
</project>
POM

  mkdir -p "$repo/bin"
  cat <<'STUB' > "$repo/bin/mvn"
#!/usr/bin/env bash
set -euo pipefail
joined=" $* "
if [[ "$joined" == *" help:evaluate "* ]]; then
  echo "${STUB_MVN_CURRENT_VERSION:-1.0.0-SNAPSHOT}"
  exit 0
fi
if [[ "$joined" == *" versions:set "* ]]; then
  new_version=""
  for arg in "$@"; do
    case "$arg" in
      -DnewVersion=*) new_version="${arg#-DnewVersion=}" ;;
    esac
  done
  if [[ -z "$new_version" ]]; then
    echo "stub mvn missing -DnewVersion" >&2
    exit 1
  fi
  python3 - "$new_version" <<'PY'
import re
import sys
from pathlib import Path
pom = Path("pom.xml")
text = pom.read_text()
# More precise regex: match <version> tag content without crossing tag boundaries
new_version = sys.argv[1]
text = re.sub(r"(<version>)[^<]+(</version>)", lambda m: m.group(1) + new_version + m.group(2), text, count=1)
pom.write_text(text)
PY
  exit 0
fi
if [[ "$joined" == *" versions:commit "* ]]; then
  exit 0
fi
echo "stub mvn unsupported invocation: $*" >&2
exit 1
STUB
  chmod +x "$repo/bin/mvn"

  # Initialize git repo for staging tests
  (cd "$repo" && git init -q && \
    git config user.email "test@example.com" && \
    git config user.name "Test User" && \
    git add -A && \
    git commit -q -m "Initial commit" || true)

  echo "$repo"
}

run_prepare_release() {
  local repo="$1"
  local mode="$2"
  local current_version="$3"
  shift 3
  local extra_args=("$@")
  local output
  if ! output="$(
    cd "$repo" && \
      PATH="$repo/bin:$PATH" \
      STUB_MVN_CURRENT_VERSION="$current_version" \
      bash scripts/prepare-release.sh "$mode" "${extra_args[@]}" 2>&1
  )"; then
    echo "$output" >&2
    fail "prepare-release.sh failed in $mode mode"
  fi
  printf '%s\n' "$output"
}

expect_contains() {
  local content="$1"
  local needle="$2"
  local description="$3"
  if ! grep -Fq -- "$needle" <<<"$content"; then
    fail "$description (missing '$needle')"
  fi
}

expect_file_contains() {
  local file="$1"
  local pattern="$2"
  local description="$3"
  if ! grep -Fq -- "$pattern" "$file"; then
    fail "$description (pattern '$pattern' not found in $file)"
  fi
}

expect_file_matches() {
  local file="$1"
  local regex="$2"
  local description="$3"
  if ! grep -Eq -- "$regex" "$file"; then
    fail "$description (regex '$regex' did not match $file)"
  fi
}

expect_occurrence_count() {
  local expected="$1"
  local file="$2"
  local literal="$3"
  local description="$4"
  local count
  count=$(grep -cF -- "$literal" "$file" || true)
  if [[ "$count" -ne "$expected" ]]; then
    fail "$description (expected $expected occurrences of '$literal' in $file, found $count)"
  fi
}

test_release_flow() {
  local repo
  repo="$(create_repo "1.2.3-SNAPSHOT")"

  cat <<'CHANGELOG' > "$repo/CHANGELOG.md"
## Unreleased

- Added brand-new trading strategy helper.

## 1.2.2 (2024-01-01)
- Previous release note.
CHANGELOG

  cat <<'README' > "$repo/README.md"
<dependency>
  <groupId>org.ta4j</groupId>
  <artifactId>ta4j-core</artifactId>
  <version>0.0.1</version>
</dependency>

<dependency>
  <groupId>org.ta4j</groupId>
  <artifactId>ta4j-examples</artifactId>
  <version>0.0.1</version>
</dependency>

The current ***snapshot version*** is `0.0.1-SNAPSHOT`.
Use `0.0.1-SNAPSHOT` everywhere.
README

  # Commit initial files
  (cd "$repo" && git add -A && git commit -q -m "Add initial files" || true)

  local output
  output="$(run_prepare_release "$repo" release "1.2.3-SNAPSHOT")"
  expect_contains "$output" "release_version=1.2.3" "release flow should print release_version"
  expect_contains "$output" "next_version=1.2.4-SNAPSHOT" "release flow should print next_version"

  expect_file_contains "$repo/pom.xml" "<version>1.2.3</version>" "pom.xml should be set to release version"
  expect_file_contains "$repo/CHANGELOG.md" "- _No changes yet._" "CHANGELOG should have placeholder"
  expect_file_matches "$repo/CHANGELOG.md" '## 1\.2\.3 \([0-9]{4}-[0-9]{2}-[0-9]{2}\)' "CHANGELOG should include release heading"
  expect_file_contains "$repo/CHANGELOG.md" "Added brand-new trading strategy helper." "CHANGELOG should include rolled-forward notes"

  expect_occurrence_count 2 "$repo/README.md" "<version>1.2.3</version>" "README dependencies should be bumped to release version"
  expect_file_contains "$repo/README.md" "The current ***snapshot version*** is \`1.2.4-SNAPSHOT\`." "README snapshot callout should mention next snapshot"
  expect_file_contains "$repo/README.md" "Use \`1.2.4-SNAPSHOT\` everywhere." "README instructions should mention next snapshot"

  if [[ ! -f "$repo/release/release-notes.md" ]]; then
    fail "release notes file should be created"
  fi
  expect_file_matches "$repo/release/release-notes.md" '^## 1\.2\.3 \([0-9]{4}-[0-9]{2}-[0-9]{2}\)' "release notes should have correct heading format"
  expect_file_contains "$repo/release/release-notes.md" "Added brand-new trading strategy helper." "release notes should include rolled-forward note"

  # Verify git staging worked
  local staged_files
  staged_files="$(cd "$repo" && git diff --cached --name-only 2>/dev/null || true)"
  if [[ -z "$staged_files" ]]; then
    fail "Expected files to be staged, but none were staged"
  fi

  # Remove from cleanup list since we're done with it
  CLEANUP_DIRS=("${CLEANUP_DIRS[@]/$repo}")
  rm -rf "$repo"
  pass "release flow"
}

test_snapshot_flow() {
  local repo
  repo="$(create_repo "2.5.0")"

  cat <<'CHANGELOG' > "$repo/CHANGELOG.md"
## Unreleased

## 2.4.0 (2024-02-01)
- Older release entry.
CHANGELOG

  echo "README placeholder" > "$repo/README.md"

  # Commit initial files
  (cd "$repo" && git add -A && git commit -q -m "Add initial files" || true)

  local output
  output="$(run_prepare_release "$repo" snapshot "2.5.0")"
  expect_contains "$output" "next_version=2.5.1-SNAPSHOT" "snapshot flow should report next version"

  expect_file_contains "$repo/pom.xml" "<version>2.5.1-SNAPSHOT</version>" "pom.xml should be bumped to next snapshot"
  expect_file_contains "$repo/CHANGELOG.md" "- _No changes yet._" "snapshot should ensure placeholder"

  # Remove from cleanup list since we're done with it
  CLEANUP_DIRS=("${CLEANUP_DIRS[@]/$repo}")
  rm -rf "$repo"
  pass "snapshot flow"
}

test_release_dry_run() {
  local repo
  repo="$(create_repo "1.2.3-SNAPSHOT")"

  cat <<'CHANGELOG' > "$repo/CHANGELOG.md"
## Unreleased
- Test change
CHANGELOG

  cat <<'README' > "$repo/README.md"
<dependency>
  <groupId>org.ta4j</groupId>
  <artifactId>ta4j-core</artifactId>
  <version>0.0.1</version>
</dependency>
README

  local output
  output="$(run_prepare_release "$repo" release "1.2.3-SNAPSHOT" --dry-run)"
  expect_contains "$output" "DRY-RUN" "dry-run should indicate DRY-RUN mode"
  expect_contains "$output" "release_version=1.2.3" "dry-run should still output version"
  expect_contains "$output" "next_version=1.2.4-SNAPSHOT" "dry-run should output next version"

  # Verify files were NOT modified
  expect_file_contains "$repo/pom.xml" "<version>1.2.3-SNAPSHOT</version>" "pom.xml should not be modified in dry-run"
  expect_file_contains "$repo/CHANGELOG.md" "## Unreleased" "CHANGELOG should not be modified in dry-run"
  expect_file_contains "$repo/CHANGELOG.md" "Test change" "CHANGELOG content should be unchanged in dry-run"
  expect_file_contains "$repo/README.md" "<version>0.0.1</version>" "README should not be modified in dry-run"

  # Verify release notes file was NOT created
  if [[ -f "$repo/release/release-notes.md" ]]; then
    fail "release notes file should not be created in dry-run"
  fi

  # Remove from cleanup list since we're done with it
  CLEANUP_DIRS=("${CLEANUP_DIRS[@]/$repo}")
  rm -rf "$repo"
  pass "release dry-run flow"
}

test_snapshot_dry_run() {
  local repo
  repo="$(create_repo "2.5.0")"

  cat <<'CHANGELOG' > "$repo/CHANGELOG.md"
## Unreleased
CHANGELOG

  local output
  output="$(run_prepare_release "$repo" snapshot "2.5.0" --dry-run)"
  expect_contains "$output" "DRY-RUN" "dry-run should indicate DRY-RUN mode"
  expect_contains "$output" "next_version=2.5.1-SNAPSHOT" "dry-run should output next version"

  # Verify files were NOT modified
  expect_file_contains "$repo/pom.xml" "<version>2.5.0</version>" "pom.xml should not be modified in dry-run"

  # Remove from cleanup list since we're done with it
  CLEANUP_DIRS=("${CLEANUP_DIRS[@]/$repo}")
  rm -rf "$repo"
  pass "snapshot dry-run flow"
}

test_release_with_version_overrides() {
  local repo
  repo="$(create_repo "1.0.0-SNAPSHOT")"

  cat <<'CHANGELOG' > "$repo/CHANGELOG.md"
## Unreleased
- Test change
CHANGELOG

  echo "README placeholder" > "$repo/README.md"
  (cd "$repo" && git add -A && git commit -q -m "Add initial files" || true)

  local output
  output="$(run_prepare_release "$repo" release "1.0.0-SNAPSHOT" --release-version "2.0.0" --next-version "3.0.0")"
  expect_contains "$output" "release_version=2.0.0" "should use custom release version"
  expect_contains "$output" "next_version=3.0.0-SNAPSHOT" "should use custom next version"

  expect_file_contains "$repo/pom.xml" "<version>2.0.0</version>" "pom.xml should use custom release version"
  expect_file_matches "$repo/CHANGELOG.md" '## 2\.0\.0 \([0-9]{4}-[0-9]{2}-[0-9]{2}\)' "CHANGELOG should use custom release version"

  # Remove from cleanup list since we're done with it
  CLEANUP_DIRS=("${CLEANUP_DIRS[@]/$repo}")
  rm -rf "$repo"
  pass "release with version overrides"
}

test_snapshot_with_version_override() {
  local repo
  repo="$(create_repo "1.0.0")"

  cat <<'CHANGELOG' > "$repo/CHANGELOG.md"
## Unreleased
CHANGELOG

  echo "README placeholder" > "$repo/README.md"
  (cd "$repo" && git add -A && git commit -q -m "Add initial files" || true)

  local output
  output="$(run_prepare_release "$repo" snapshot "1.0.0" --next-version "2.5.0-SNAPSHOT")"
  expect_contains "$output" "next_version=2.5.0-SNAPSHOT" "should use custom next version"

  expect_file_contains "$repo/pom.xml" "<version>2.5.0-SNAPSHOT</version>" "pom.xml should use custom snapshot version"

  # Remove from cleanup list since we're done with it
  CLEANUP_DIRS=("${CLEANUP_DIRS[@]/$repo}")
  rm -rf "$repo"
  pass "snapshot with version override"
}

test_release_empty_changelog() {
  local repo
  repo="$(create_repo "1.0.0-SNAPSHOT")"

  # Create empty CHANGELOG
  touch "$repo/CHANGELOG.md"
  echo "README placeholder" > "$repo/README.md"
  (cd "$repo" && git add -A && git commit -q -m "Add initial files" || true)

  local output
  output="$(run_prepare_release "$repo" release "1.0.0-SNAPSHOT")"
  expect_contains "$output" "release_version=1.0.0" "should handle empty changelog"

  expect_file_contains "$repo/CHANGELOG.md" "## Unreleased" "should create Unreleased section"
  expect_file_contains "$repo/CHANGELOG.md" "## 1.0.0" "should create release section"
  expect_file_contains "$repo/CHANGELOG.md" "- _No changes yet._" "should add placeholder"

  # Remove from cleanup list since we're done with it
  CLEANUP_DIRS=("${CLEANUP_DIRS[@]/$repo}")
  rm -rf "$repo"
  pass "release with empty changelog"
}

test_release_missing_unreleased_section() {
  local repo
  repo="$(create_repo "1.0.0-SNAPSHOT")"

  cat <<'CHANGELOG' > "$repo/CHANGELOG.md"
## 0.9.0 (2024-01-01)
- Previous release
CHANGELOG

  echo "README placeholder" > "$repo/README.md"
  (cd "$repo" && git add -A && git commit -q -m "Add initial files" || true)

  local output
  output="$(run_prepare_release "$repo" release "1.0.0-SNAPSHOT")"
  expect_contains "$output" "release_version=1.0.0" "should handle missing Unreleased section"

  expect_file_contains "$repo/CHANGELOG.md" "## Unreleased" "should create Unreleased section"
  expect_file_contains "$repo/CHANGELOG.md" "## 1.0.0" "should create release section"
  expect_file_contains "$repo/CHANGELOG.md" "- _No changes yet._" "should add placeholder"

  # Remove from cleanup list since we're done with it
  CLEANUP_DIRS=("${CLEANUP_DIRS[@]/$repo}")
  rm -rf "$repo"
  pass "release with missing Unreleased section"
}

main() {
  test_release_flow
  test_snapshot_flow
  test_release_dry_run
  test_snapshot_dry_run
  test_release_with_version_overrides
  test_snapshot_with_version_override
  test_release_empty_changelog
  test_release_missing_unreleased_section
  echo "All prepare-release.sh tests passed."
}

main "$@"