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

line_of() {
  local needle="$1"
  local line
  line="$(grep -nF -- "$needle" "$WORKFLOW" | head -n1 | cut -d: -f1)"
  if [[ -z "$line" ]]; then
    fail "missing workflow line: '$needle'"
  fi
  printf '%s\n' "$line"
}

workflow_section() {
  local start="$1"
  local end="$2"
  awk -v start="$start" -v end="$end" '
    index($0, start) { active = 1 }
    active { print }
    index($0, end) { active = 0 }
  ' "$WORKFLOW"
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

test_deprecation_scan_uses_java_scanner() {
  echo "Running test_deprecation_scan_uses_java_scanner"

  expect_file_contains "$WORKFLOW" "exec:java" "deprecation scan should run through Maven exec"
  expect_file_contains "$WORKFLOW" "-pl ta4j-examples -am compile" \
    "deprecation scan should compile the Java tool before execution"
  expect_file_contains "$WORKFLOW" "-pl ta4j-examples exec:java" \
    "deprecation scan should run exec:java only on ta4j-examples"
  expect_file_contains "$WORKFLOW" "ta4jexamples.doc.RemovalReadyDeprecationScanner" \
    "deprecation scan should invoke the Java scanner"
  if grep -Fq "scan-removal-ready-deprecations.py" "$WORKFLOW"; then
    fail "deprecation scan should not depend on the removed Python scanner"
  fi

  pass "test_deprecation_scan_uses_java_scanner"
}

test_release_gate_runs_before_next_snapshot() {
  echo "Running test_release_gate_runs_before_next_snapshot"

  local gate_line
  local next_snapshot_line
  gate_line="$(line_of "Gate release-ready deprecations")"
  next_snapshot_line="$(line_of "Set Maven version to next snapshot")"
  if (( gate_line >= next_snapshot_line )); then
    fail "release deprecation gate should run before next snapshot version is applied"
  fi

  expect_file_contains "$WORKFLOW" "--target-removal-version \${RELEASE_VERSION}" \
    "release gate should scan against the release version"
  expect_file_contains "$WORKFLOW" "--include-overdue --fail-on-due" \
    "release gate should include overdue removals and fail on due removals"
  expect_file_contains "$WORKFLOW" "release-ready-deprecations-\${{ steps.versions.outputs.release }}" \
    "release gate report should be uploaded as its own artifact"

  pass "test_release_gate_runs_before_next_snapshot"
}

test_issue_sync_reconciles_stale_managed_issues() {
  echo "Running test_issue_sync_reconciles_stale_managed_issues"

  expect_file_contains "$WORKFLOW" "ta4j:deprecation-removal version=\${removalVersion}" \
    "issue sync should search managed deprecation issues by removal version"
  expect_file_contains "$WORKFLOW" "closedStaleCount" \
    "issue sync should report stale managed issue closures"
  expect_file_contains "$WORKFLOW" "state_reason: \"completed\"" \
    "stale managed issues should be closed as completed"
  expect_file_contains "$WORKFLOW" "closed_stale_count" \
    "closed stale issue count should be exposed as an output"

  pass "test_issue_sync_reconciles_stale_managed_issues"
}

test_deprecation_issue_sync_does_not_label_cleanup_issues() {
  echo "Running test_deprecation_issue_sync_does_not_label_cleanup_issues"

  local sync_section
  sync_section="$(workflow_section "Create or update removal-ready deprecation issues" "Upload removal-ready deprecation report")"
  if grep -Fq "labels:" <<<"$sync_section"; then
    fail "generated deprecation cleanup issues should not receive labels"
  fi

  pass "test_deprecation_issue_sync_does_not_label_cleanup_issues"
}

test_deprecation_automation_skips_dry_run_mutations() {
  echo "Running test_deprecation_automation_skips_dry_run_mutations"

  local gate_section
  local issue_section
  gate_section="$(workflow_section "Gate release-ready deprecations" "Upload release-ready deprecation gate report")"
  issue_section="$(workflow_section "Create or update removal-ready deprecation issues" "Upload removal-ready deprecation report")"
  if ! grep -Fq "if: steps.dry_run.outputs.dryRun != 'true'" <<<"$gate_section"; then
    fail "release deprecation gate should skip dry-run mutations"
  fi
  if ! grep -Fq "if: steps.dry_run.outputs.dryRun != 'true'" <<<"$issue_section"; then
    fail "deprecation issue sync should skip dry-run mutations"
  fi

  pass "test_deprecation_automation_skips_dry_run_mutations"
}

test_issue_permissions_declared
test_issue_sync_uses_targeted_search
test_deprecation_scan_uses_java_scanner
test_release_gate_runs_before_next_snapshot
test_issue_sync_reconciles_stale_managed_issues
test_deprecation_issue_sync_does_not_label_cleanup_issues
test_deprecation_automation_skips_dry_run_mutations
