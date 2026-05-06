#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORKFLOWS="$ROOT/.github/workflows"

fail() { echo "[FAIL] $1" >&2; exit 1; }
pass() { echo "[PASS] $1"; }

expect_file_contains() {
  local file="$1"
  local needle="$2"
  local msg="$3"
  if ! grep -Fq -- "$needle" "$file"; then
    fail "$msg (missing: '$needle' in ${file#$ROOT/})"
  fi
}

input_section() {
  local file="$1"
  local input="$2"
  awk -v needle="      ${input}:" '
    index($0, needle) { active = 1 }
    active { print }
    active && index($0, "type: boolean") { exit }
  ' "$file"
}

workflow_section() {
  local file="$1"
  local start="$2"
  local end="$3"
  awk -v start="$start" -v end="$end" '
    index($0, start) { active = 1 }
    active { print }
    active && index($0, end) { exit }
  ' "$file"
}

expect_section_contains() {
  local section="$1"
  local needle="$2"
  local msg="$3"
  if ! grep -Fq -- "$needle" <<<"$section"; then
    fail "$msg (missing: '$needle')"
  fi
}

test_maven_workflow_jobs_setup_jdk25_before_maven() {
  echo "Running test_maven_workflow_jobs_setup_jdk25_before_maven"

  local workflow
  for workflow in "$WORKFLOWS"/*.yml; do
    if ! grep -Eq '(^|[[:space:]"({;])xvfb-run[[:space:]]+mvn[[:space:]-]|(^|[[:space:]"({;])mvn[[:space:]-]' "$workflow"; then
      continue
    fi

    awk -v file="${workflow#$ROOT/}" '
      /^jobs:/ { in_jobs = 1; next }
      in_jobs && /^  [A-Za-z0-9_-]+:$/ {
        job = $1
        sub(/:$/, "", job)
        setup = 0
        temurin = 0
        jdk25 = 0
        next
      }
      /^[^[:space:]]/ && $0 !~ /^jobs:/ {
        in_jobs = 0
      }
      /uses: actions\/setup-java@v5/ {
        setup = 1
        temurin = 0
        jdk25 = 0
      }
      setup && /distribution: temurin/ { temurin = 1 }
      setup && /java-version: 25/ { jdk25 = 1 }
      /^[[:space:]]*#/ { next }
      /(^|[[:space:]"({;])xvfb-run[[:space:]]+mvn[[:space:]-]|(^|[[:space:]"({;])mvn[[:space:]-]/ {
        if (!setup || !temurin || !jdk25) {
          printf("[FAIL] %s job %s runs Maven before Temurin JDK 25 setup at line %d\n", file, job ? job : "(unknown)", NR) > "/dev/stderr"
          exit 1
        }
      }
    ' "$workflow"
  done

  pass "test_maven_workflow_jobs_setup_jdk25_before_maven"
}

test_mutating_manual_workflows_default_to_dry_run() {
  echo "Running test_mutating_manual_workflows_default_to_dry_run"

  local workflow
  for workflow in \
    release-scheduler.yml \
    prepare-release.yml \
    publish-release.yml \
    github-release.yml \
    snapshot.yml \
    release-health.yml; do
    local file="$WORKFLOWS/$workflow"
    local section
    section="$(input_section "$file" dryRun)"
    expect_section_contains "$section" "default: true" "${workflow} manual dryRun input should default true"
    expect_section_contains "$section" "type: boolean" "${workflow} manual dryRun input should be typed as boolean"
  done

  pass "test_mutating_manual_workflows_default_to_dry_run"
}

test_official_triggers_normalize_to_non_dry_run() {
  echo "Running test_official_triggers_normalize_to_non_dry_run"

  expect_file_contains "$WORKFLOWS/release-scheduler.yml" 'if [ "$event" = "workflow_dispatch" ]; then' \
    "release scheduler should branch normalization by event"
  expect_file_contains "$WORKFLOWS/release-scheduler.yml" "raw=\"\${DRY_RUN_INPUT:-true}\"" \
    "release scheduler manual default should normalize to dry-run"
  expect_file_contains "$WORKFLOWS/release-scheduler.yml" "dry_run=false" \
    "release scheduler scheduled runs should normalize to non-dry-run"

  expect_file_contains "$WORKFLOWS/publish-release.yml" 'if [ "${{ github.event_name }}" = "pull_request" ]; then' \
    "publish-release should normalize merged release PRs to non-dry-run"
  expect_file_contains "$WORKFLOWS/github-release.yml" 'if [ "${GITHUB_EVENT_NAME}" = "workflow_dispatch" ]; then' \
    "github-release should keep tag pushes non-dry-run"
  expect_file_contains "$WORKFLOWS/snapshot.yml" 'if [ "${GITHUB_EVENT_NAME}" = "workflow_dispatch" ]; then' \
    "snapshot should keep master pushes non-dry-run"
  expect_file_contains "$WORKFLOWS/release-health.yml" 'if [ "${GITHUB_EVENT_NAME}" = "workflow_dispatch" ]; then' \
    "release-health should keep schedule/push/workflow-run triggers non-dry-run"

  pass "test_official_triggers_normalize_to_non_dry_run"
}

test_downstream_dispatches_explicitly_pass_dry_run() {
  echo "Running test_downstream_dispatches_explicitly_pass_dry_run"

  local scheduler_dispatch
  scheduler_dispatch="$(workflow_section "$WORKFLOWS/release-scheduler.yml" 'workflow_id: "prepare-release.yml"' "dryRun")"
  expect_section_contains "$scheduler_dispatch" "dryRun" \
    "release scheduler dispatch should pass dryRun to prepare-release"

  local prepare_dispatch
  prepare_dispatch="$(workflow_section "$WORKFLOWS/prepare-release.yml" 'workflow_id: "publish-release.yml"' "dryRun")"
  expect_section_contains "$prepare_dispatch" "dryRun" \
    "prepare-release dispatch should pass dryRun to publish-release"

  local publish_dispatch
  publish_dispatch="$(workflow_section "$WORKFLOWS/publish-release.yml" 'workflow_id: "snapshot.yml"' 'dryRun: "false"')"
  expect_section_contains "$publish_dispatch" 'dryRun: "false"' \
    "publish-release should explicitly dispatch snapshot publication as non-dry-run"

  pass "test_downstream_dispatches_explicitly_pass_dry_run"
}

test_mutating_steps_remain_dry_run_gated() {
  echo "Running test_mutating_steps_remain_dry_run_gated"

  expect_file_contains "$WORKFLOWS/release-scheduler.yml" "if: always() && needs.analyze.outputs.dryRun != 'true'" \
    "release scheduler discussion mutation should skip dry-run"

  expect_file_contains "$WORKFLOWS/prepare-release.yml" "if: steps.dry_run.outputs.dryRun != 'true'" \
    "prepare-release mutations should be dry-run gated"
  expect_file_contains "$WORKFLOWS/prepare-release.yml" "Create or update removal-ready deprecation issues" \
    "prepare-release should still have managed issue sync"
  expect_file_contains "$WORKFLOWS/prepare-release.yml" "Create or update release PR" \
    "prepare-release should still have release PR mutation"

  expect_file_contains "$WORKFLOWS/publish-release.yml" "Deploy Release to Maven Central" \
    "publish-release should still have Maven Central deployment"
  expect_file_contains "$WORKFLOWS/publish-release.yml" "if: steps.dry_run.outputs.dryRun != 'true'" \
    "publish-release mutations should be dry-run gated"
  expect_file_contains "$WORKFLOWS/publish-release.yml" "if: always() && steps.dry_run.outputs.dryRun != 'true'" \
    "publish-release discussion mutation should skip dry-run"

  expect_file_contains "$WORKFLOWS/github-release.yml" "Create GitHub Release" \
    "github-release should still publish GitHub releases"
  expect_file_contains "$WORKFLOWS/github-release.yml" "if: steps.dry_run.outputs.dryRun != 'true'" \
    "github-release publication should skip dry-run"

  expect_file_contains "$WORKFLOWS/snapshot.yml" "Deploy Snapshot" \
    "snapshot workflow should still publish snapshots"
  expect_file_contains "$WORKFLOWS/snapshot.yml" "if: steps.dry_run.outputs.dryRun != 'true'" \
    "snapshot deployment and publishing-secret steps should skip dry-run"

  expect_file_contains "$WORKFLOWS/release-health.yml" "if: always() && steps.dry_run.outputs.dryRun != 'true'" \
    "release-health discussion mutation should skip dry-run"

  pass "test_mutating_steps_remain_dry_run_gated"
}

test_dry_run_summaries_and_audits_show_rerun_guidance() {
  echo "Running test_dry_run_summaries_and_audits_show_rerun_guidance"

  expect_file_contains "$WORKFLOWS/release-scheduler.yml" "release-scheduler-mutation-plan.txt" \
    "release scheduler audit artifact should include the dry-run mutation plan"
  expect_file_contains "$WORKFLOWS/prepare-release.yml" '"mutationPlan"' \
    "prepare-release audit JSON should include mutation plan"
  expect_file_contains "$WORKFLOWS/publish-release.yml" '"mutationPlan"' \
    "publish-release audit JSON should include mutation plan"
  expect_file_contains "$WORKFLOWS/github-release.yml" '"mutationPlan"' \
    "github-release audit JSON should include mutation plan"
  expect_file_contains "$WORKFLOWS/snapshot.yml" '"snapshotVersion"' \
    "snapshot audit JSON should include computed snapshot version"
  expect_file_contains "$WORKFLOWS/snapshot.yml" '"mutationPlan"' \
    "snapshot audit JSON should include mutation plan"
  expect_file_contains "$WORKFLOWS/release-health.yml" '"mutationPlan"' \
    "release-health audit JSON should include mutation plan"
  expect_file_contains "$WORKFLOWS/prepare-release.yml" "rerun guidance" \
    "prepare-release summary should include rerun guidance"
  expect_file_contains "$WORKFLOWS/publish-release.yml" "rerun guidance" \
    "publish-release summary should include rerun guidance"
  expect_file_contains "$WORKFLOWS/snapshot.yml" "rerun guidance" \
    "snapshot summary should include rerun guidance"

  pass "test_dry_run_summaries_and_audits_show_rerun_guidance"
}

test_snapshot_and_health_manual_dry_runs_do_not_mutate() {
  echo "Running test_snapshot_and_health_manual_dry_runs_do_not_mutate"

  local deploy_section
  deploy_section="$(workflow_section "$WORKFLOWS/snapshot.yml" "Deploy Snapshot" "Snapshot publication summary")"
  expect_section_contains "$deploy_section" "if: steps.dry_run.outputs.dryRun != 'true'" \
    "snapshot deployment should skip dry-run"

  local health_post_section
  health_post_section="$(workflow_section "$WORKFLOWS/release-health.yml" "Post to Release Scheduler discussion" "with:")"
  expect_section_contains "$health_post_section" "if: always() && steps.dry_run.outputs.dryRun != 'true'" \
    "release-health discussion post should skip dry-run"

  pass "test_snapshot_and_health_manual_dry_runs_do_not_mutate"
}

test_maven_workflow_jobs_setup_jdk25_before_maven
test_mutating_manual_workflows_default_to_dry_run
test_official_triggers_normalize_to_non_dry_run
test_downstream_dispatches_explicitly_pass_dry_run
test_mutating_steps_remain_dry_run_gated
test_dry_run_summaries_and_audits_show_rerun_guidance
test_snapshot_and_health_manual_dry_runs_do_not_mutate
