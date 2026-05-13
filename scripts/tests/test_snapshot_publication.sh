#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT="$ROOT/scripts/release/release_helpers.py"
PYTHON="${PYTHON:-python3}"

cleanup() {
  if [[ -n "${TMP:-}" && -d "$TMP" ]]; then
    rm -rf "$TMP"
  fi
  return 0
}
trap cleanup EXIT

fail() { echo "[FAIL] $1" >&2; exit 1; }
pass() { echo "[PASS] $1"; }

new_temp_dir() {
  mktemp -d "${TMPDIR:-/tmp}/snapshot-publication.XXXXXX"
}

expect_output_value() {
  local file="$1"
  local key="$2"
  local expected="$3"
  local actual=""

  actual="$(awk -F= -v key="$key" '$1 == key { print substr($0, length($1) + 2); exit }' "$file")"
  if [[ "$actual" != "$expected" ]]; then
    fail "expected ${key}=${expected}, got ${actual:-<missing>}"
  fi
}

expect_file_contains() {
  local file="$1"
  local expected="$2"

  if ! grep -Fq -- "$expected" "$file"; then
    fail "expected ${file} to contain: ${expected}"
  fi
}

write_metadata_fixture() {
  local path="$1"
  local latest="$2"
  local versions="$3"
  local last_updated="$4"

  cat > "$path" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<metadata>
  <groupId>org.ta4j</groupId>
  <artifactId>ta4j-parent</artifactId>
  <versioning>
    <latest>${latest}</latest>
    <versions>
${versions}
    </versions>
    <lastUpdated>${last_updated}</lastUpdated>
  </versioning>
</metadata>
EOF
}

test_snapshot_version_present() {
  echo "Running test_snapshot_version_present"

  TMP="$(new_temp_dir)"
  local metadata_file="$TMP/metadata.xml"
  local github_output="$TMP/github-output.txt"
  local output_file="$TMP/snapshot-publication.json"
  write_metadata_fixture "$metadata_file" "0.22.7-SNAPSHOT" $'      <version>0.22.6-SNAPSHOT</version>\n      <version>0.22.7-SNAPSHOT</version>' "20260506001534"

  "$PYTHON" "$SCRIPT" snapshot-publication \
    --version "0.22.7-SNAPSHOT" \
    --metadata-file "$metadata_file" \
    --output "$output_file" \
    --github-output "$github_output" >/dev/null

  expect_output_value "$github_output" "snapshot_publication" "true"
  expect_output_value "$github_output" "snapshot_publication_latest" "0.22.7-SNAPSHOT"
  expect_output_value "$github_output" "snapshot_publication_last_updated" "20260506001534"

  rm -rf "$TMP"
  pass "test_snapshot_version_present"
}

test_snapshot_version_missing() {
  echo "Running test_snapshot_version_missing"

  TMP="$(new_temp_dir)"
  local metadata_file="$TMP/metadata.xml"
  local github_output="$TMP/github-output.txt"
  local output_file="$TMP/snapshot-publication.json"
  write_metadata_fixture "$metadata_file" "0.22.7-SNAPSHOT" $'      <version>0.22.6-SNAPSHOT</version>\n      <version>0.22.7-SNAPSHOT</version>' "20260506001534"

  "$PYTHON" "$SCRIPT" snapshot-publication \
    --version "0.22.8-SNAPSHOT" \
    --metadata-file "$metadata_file" \
    --output "$output_file" \
    --github-output "$github_output" >/dev/null

  expect_output_value "$github_output" "snapshot_publication" "false"
  expect_output_value "$github_output" "snapshot_publication_latest" "0.22.7-SNAPSHOT"

  rm -rf "$TMP"
  pass "test_snapshot_version_missing"
}

test_non_snapshot_version_returns_na() {
  echo "Running test_non_snapshot_version_returns_na"

  TMP="$(new_temp_dir)"
  local metadata_file="$TMP/metadata.xml"
  local github_output="$TMP/github-output.txt"
  local output_file="$TMP/snapshot-publication.json"
  write_metadata_fixture "$metadata_file" "0.22.7-SNAPSHOT" $'      <version>0.22.7-SNAPSHOT</version>' "20260506001534"

  "$PYTHON" "$SCRIPT" snapshot-publication \
    --version "0.22.7" \
    --metadata-file "$metadata_file" \
    --output "$output_file" \
    --github-output "$github_output" >/dev/null

  expect_output_value "$github_output" "snapshot_publication" "n/a"

  rm -rf "$TMP"
  pass "test_non_snapshot_version_returns_na"
}

test_malformed_metadata_returns_unknown() {
  echo "Running test_malformed_metadata_returns_unknown"

  TMP="$(new_temp_dir)"
  local metadata_file="$TMP/metadata.xml"
  local github_output="$TMP/github-output.txt"
  local output_file="$TMP/snapshot-publication.json"
  printf '%s\n' '<metadata><versioning>' > "$metadata_file"

  "$PYTHON" "$SCRIPT" snapshot-publication \
    --version "0.22.7-SNAPSHOT" \
    --metadata-file "$metadata_file" \
    --output "$output_file" \
    --github-output "$github_output" >/dev/null

  expect_output_value "$github_output" "snapshot_publication" "unknown"

  rm -rf "$TMP"
  pass "test_malformed_metadata_returns_unknown"
}

test_non_https_metadata_url_returns_unknown() {
  echo "Running test_non_https_metadata_url_returns_unknown"

  TMP="$(new_temp_dir)"
  local github_output="$TMP/github-output.txt"
  local output_file="$TMP/snapshot-publication.json"

  "$PYTHON" "$SCRIPT" snapshot-publication \
    --version "0.22.7-SNAPSHOT" \
    --metadata-url "file:///tmp/snapshot-publication.xml" \
    --output "$output_file" \
    --github-output "$github_output" >/dev/null

  expect_output_value "$github_output" "snapshot_publication" "unknown"
  expect_file_contains "$output_file" "\"error\": \"--metadata-url must use https\""

  rm -rf "$TMP"
  pass "test_non_https_metadata_url_returns_unknown"
}

test_snapshot_publication_policy_defers_push_runs() {
  echo "Running test_snapshot_publication_policy_defers_push_runs"

  TMP="$(new_temp_dir)"
  local github_output="$TMP/github-output.txt"
  local output_file="$TMP/snapshot-publication-policy.json"

  "$PYTHON" "$SCRIPT" snapshot-publication-policy \
    --event-name "push" \
    --workflow-name "" \
    --output "$output_file" \
    --github-output "$github_output" >/dev/null

  expect_output_value "$github_output" "snapshot_publication_enforced" "false"
  expect_output_value "$github_output" "snapshot_publication_pending_reason" "awaiting snapshot workflow completion before treating snapshot metadata drift as authoritative"

  rm -rf "$TMP"
  pass "test_snapshot_publication_policy_defers_push_runs"
}

test_snapshot_publication_policy_enforces_snapshot_workflow_runs() {
  echo "Running test_snapshot_publication_policy_enforces_snapshot_workflow_runs"

  TMP="$(new_temp_dir)"
  local github_output="$TMP/github-output.txt"
  local output_file="$TMP/snapshot-publication-policy.json"

  "$PYTHON" "$SCRIPT" snapshot-publication-policy \
    --event-name "workflow_run" \
    --workflow-name "Publish Snapshot to Maven Central" \
    --output "$output_file" \
    --github-output "$github_output" >/dev/null

  expect_output_value "$github_output" "snapshot_publication_enforced" "true"
  expect_output_value "$github_output" "snapshot_publication_pending_reason" ""

  rm -rf "$TMP"
  pass "test_snapshot_publication_policy_enforces_snapshot_workflow_runs"
}

test_snapshot_version_present
test_snapshot_version_missing
test_non_snapshot_version_returns_na
test_malformed_metadata_returns_unknown
test_non_https_metadata_url_returns_unknown
test_snapshot_publication_policy_defers_push_runs
test_snapshot_publication_policy_enforces_snapshot_workflow_runs

echo
echo "All snapshot publication tests passed."
