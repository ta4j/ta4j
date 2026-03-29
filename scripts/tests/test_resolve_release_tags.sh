#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# test_resolve_release_tags.sh
#
# Validates release tag baseline selection and first-parent diagnostics.
# =============================================================================

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT="$ROOT/scripts/resolve-release-tags.sh"

cleanup() {
  if [[ -n "${TMP:-}" && -d "$TMP" ]]; then
    rm -rf "$TMP"
  fi
  return 0
}
trap cleanup EXIT

fail() { echo "[FAIL] $1" >&2; exit 1; }
pass() { echo "[PASS] $1"; }

expect_output_value() {
  local output="$1"
  local key="$2"
  local expected="$3"
  local actual=""

  actual="$(printf '%s\n' "$output" | awk -F= -v key="$key" '$1 == key { print substr($0, length($1) + 2); exit }')"
  if [[ "$actual" != "$expected" ]]; then
    fail "expected ${key}=${expected}, got ${actual:-<missing>}"
  fi
}

run_test() {
  TMP="$(mktemp -d)"
  mkdir -p "$TMP/scripts"
  cp "$SCRIPT" "$TMP/scripts/resolve-release-tags.sh"
  chmod +x "$TMP/scripts/resolve-release-tags.sh"

  pushd "$TMP" >/dev/null || exit 1
  git init -q -b master
  git config user.name "Test User"
  git config user.email "test@example.com"
}

finish_test() {
  popd >/dev/null || exit 1
  rm -rf "$TMP"
}

commit_file() {
  local path="$1"
  local content="$2"
  local message="$3"
  local date="$4"

  mkdir -p "$(dirname "$path")"
  printf '%s\n' "$content" > "$path"
  git add "$path"
  GIT_AUTHOR_DATE="$date" GIT_COMMITTER_DATE="$date" git commit -q -m "$message"
}

tag_release() {
  local tag="$1"
  local date="$2"

  GIT_COMMITTER_DATE="$date" git tag -a "$tag" -m "Release $tag"
}

merge_branch() {
  local branch="$1"
  local message="$2"
  local date="$3"

  GIT_AUTHOR_DATE="$date" GIT_COMMITTER_DATE="$date" git merge --no-ff -m "$message" "$branch" >/dev/null
}

test_returns_none_when_no_release_tags_exist() {
  echo "Running test_returns_none_when_no_release_tags_exist"
  run_test

  commit_file README.md "fixture" "Initial commit" "2024-01-01T00:00:00Z"

  local out
  out="$(scripts/resolve-release-tags.sh HEAD)"

  expect_output_value "$out" "latest_tag" "none"
  expect_output_value "$out" "last_reachable_tag" "none"
  expect_output_value "$out" "last_first_parent_tag" "none"
  expect_output_value "$out" "latest_tag_reachable" "n/a"

  finish_test
  pass "test_returns_none_when_no_release_tags_exist"
}

test_prefers_reachable_tag_for_release_baseline() {
  echo "Running test_prefers_reachable_tag_for_release_baseline"
  run_test

  commit_file README.md "base" "Initial commit" "2024-01-01T00:00:00Z"
  tag_release "0.21.0" "2024-01-02T00:00:00Z"
  commit_file main.txt "after 0.21.0" "Master commit after 0.21.0" "2024-01-03T00:00:00Z"

  git checkout -q -b release/0.22.4
  commit_file release.txt "release payload" "Release 0.22.4" "2024-01-04T00:00:00Z"
  tag_release "0.22.4" "2024-01-05T00:00:00Z"

  git checkout -q master
  merge_branch "release/0.22.4" "Merge release/0.22.4" "2024-01-06T00:00:00Z"

  local out
  out="$(scripts/resolve-release-tags.sh HEAD)"

  expect_output_value "$out" "latest_tag" "0.22.4"
  expect_output_value "$out" "last_reachable_tag" "0.22.4"
  expect_output_value "$out" "last_first_parent_tag" "0.21.0"
  expect_output_value "$out" "latest_tag_reachable" "true"

  finish_test
  pass "test_prefers_reachable_tag_for_release_baseline"
}

test_reports_unreachable_latest_tag() {
  echo "Running test_reports_unreachable_latest_tag"
  run_test

  commit_file README.md "base" "Initial commit" "2024-02-01T00:00:00Z"
  tag_release "0.22.3" "2024-02-02T00:00:00Z"
  commit_file main.txt "master work" "Master commit" "2024-02-03T00:00:00Z"

  git checkout -q -b release/0.22.4
  commit_file release.txt "detached release" "Detached release commit" "2024-02-04T00:00:00Z"
  tag_release "0.22.4" "2024-02-05T00:00:00Z"

  git checkout -q master

  local out
  out="$(scripts/resolve-release-tags.sh HEAD)"

  expect_output_value "$out" "latest_tag" "0.22.4"
  expect_output_value "$out" "last_reachable_tag" "0.22.3"
  expect_output_value "$out" "last_first_parent_tag" "0.22.3"
  expect_output_value "$out" "latest_tag_reachable" "false"

  finish_test
  pass "test_reports_unreachable_latest_tag"
}

test_falls_back_to_v_prefixed_tags() {
  echo "Running test_falls_back_to_v_prefixed_tags"
  run_test

  commit_file README.md "base" "Initial commit" "2024-03-01T00:00:00Z"
  tag_release "v1.2.3" "2024-03-02T00:00:00Z"

  local out
  out="$(scripts/resolve-release-tags.sh HEAD)"

  expect_output_value "$out" "latest_tag" "v1.2.3"
  expect_output_value "$out" "last_reachable_tag" "v1.2.3"
  expect_output_value "$out" "last_first_parent_tag" "v1.2.3"
  expect_output_value "$out" "latest_tag_reachable" "true"

  finish_test
  pass "test_falls_back_to_v_prefixed_tags"
}

test_prefers_numeric_tags_over_v_prefixed_tags() {
  echo "Running test_prefers_numeric_tags_over_v_prefixed_tags"
  run_test

  commit_file README.md "base" "Initial commit" "2024-04-01T00:00:00Z"
  tag_release "1.2.3" "2024-04-02T00:00:00Z"
  commit_file main.txt "master work" "Master commit" "2024-04-03T00:00:00Z"
  tag_release "v9.9.9" "2024-04-04T00:00:00Z"

  local out
  out="$(scripts/resolve-release-tags.sh HEAD)"

  expect_output_value "$out" "latest_tag" "1.2.3"
  expect_output_value "$out" "last_reachable_tag" "1.2.3"
  expect_output_value "$out" "last_first_parent_tag" "1.2.3"
  expect_output_value "$out" "latest_tag_reachable" "true"

  finish_test
  pass "test_prefers_numeric_tags_over_v_prefixed_tags"
}

test_returns_head_values_when_target_ref_is_missing() {
  echo "Running test_returns_head_values_when_target_ref_is_missing"
  run_test

  commit_file README.md "base" "Initial commit" "2024-05-01T00:00:00Z"
  tag_release "0.30.0" "2024-05-02T00:00:00Z"

  local out
  out="$(scripts/resolve-release-tags.sh refs/remotes/origin/master)"

  expect_output_value "$out" "latest_tag" "0.30.0"
  expect_output_value "$out" "last_reachable_tag" "0.30.0"
  expect_output_value "$out" "last_first_parent_tag" "0.30.0"
  expect_output_value "$out" "latest_tag_reachable" "true"

  finish_test
  pass "test_returns_head_values_when_target_ref_is_missing"
}

test_returns_none_when_no_release_tags_exist
test_prefers_reachable_tag_for_release_baseline
test_reports_unreachable_latest_tag
test_falls_back_to_v_prefixed_tags
test_prefers_numeric_tags_over_v_prefixed_tags
test_returns_head_values_when_target_ref_is_missing

echo
echo "All resolve-release-tags tests passed."
