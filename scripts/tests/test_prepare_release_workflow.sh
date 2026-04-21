#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORKFLOW="$ROOT/.github/workflows/prepare-release.yml"

fail() { echo "[FAIL] $1" >&2; exit 1; }
pass() { echo "[PASS] $1"; }

expect_file_contains() {
  local file="$1"
  local needle="$2"
  local msg="$3"
  if ! grep -Fq -- "$needle" "$file"; then
    fail "$msg (missing: '$needle')"
  fi
}

test_issue_permissions_declared() {
  echo "Running test_issue_permissions_declared"

  local permissions
  permissions="$(ruby -e 'require "yaml"; workflow = YAML.load_file(ARGV[0]); puts workflow.fetch("permissions").fetch("issues")' "$WORKFLOW")"
  if [[ "$permissions" != "write" ]]; then
    fail "prepare-release workflow should request issues: write permission"
  fi

  pass "test_issue_permissions_declared"
}

test_issue_sync_uses_targeted_search() {
  echo "Running test_issue_sync_uses_targeted_search"

  expect_file_contains "$WORKFLOW" "search.issuesAndPullRequests" "issue sync should use targeted issue search"
  expect_file_contains "$WORKFLOW" "is:issue in:body" "issue sync should search by marker in the body"
  if grep -Fq "issues.listForRepo" "$WORKFLOW"; then
    fail "issue sync should not paginate every repo issue"
  fi

  pass "test_issue_sync_uses_targeted_search"
}

test_issue_permissions_declared
test_issue_sync_uses_targeted_search
