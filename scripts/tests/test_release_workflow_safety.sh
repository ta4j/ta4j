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
    fail "$msg (missing: '$needle' in ${file#"$ROOT"/})"
  fi
}

line_of() {
  local file="$1"
  local needle="$2"
  local line
  line="$(grep -nFm1 -- "$needle" "$file" || true)"
  line="${line%%:*}"
  if [[ -z "$line" ]]; then
    fail "missing expected workflow line '$needle' in ${file#"$ROOT"/}"
  fi
  printf '%s\n' "$line"
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

expect_section_not_contains() {
  local section="$1"
  local needle="$2"
  local msg="$3"
  if grep -Fq -- "$needle" <<<"$section"; then
    fail "$msg (unexpected: '$needle')"
  fi
}

assert_no_github_script_injected_binding_redeclarations() {
  local workflow="$1"
  awk -v file="${workflow#"$ROOT"/}" '
    /uses: actions\/github-script@/ {
      pending_script = 1
      in_script = 0
      next
    }
    pending_script && /^[[:space:]]*script: \|/ {
      in_script = 1
      next
    }
    in_script && /^[[:space:]]{6}- name:/ {
      pending_script = 0
      in_script = 0
    }
    in_script && /^[[:space:]]*(const|let|var)[[:space:]]+(core|github|context|glob|io|exec)[[:space:]]*=/ {
      printf("[FAIL] %s redeclares actions/github-script injected binding at line %d: %s\n", file, NR, $0) > "/dev/stderr"
      exit 1
    }
  ' "$workflow"
}

test_maven_workflow_jobs_setup_jdk25_before_maven() {
  echo "Running test_maven_workflow_jobs_setup_jdk25_before_maven"

  local maven_run_regex='(^|[[:space:]"({;])xvfb-run[[:space:]]+(\./)?mvnw?([[:space:]-]|\.cmd)|(^|[[:space:]"({;])(\./)?mvnw?([[:space:]-]|\.cmd)'
  local workflow
  for workflow in "$WORKFLOWS"/*.yml; do
    if ! grep -Eq "$maven_run_regex" "$workflow"; then
      continue
    fi

    awk -v file="${workflow#"$ROOT"/}" -v maven_run_regex="$maven_run_regex" '
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
      $0 ~ maven_run_regex {
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

test_release_scheduler_uses_true_biweekly_cadence_guard() {
  echo "Running test_release_scheduler_uses_true_biweekly_cadence_guard"

  expect_file_contains "$WORKFLOWS/release-scheduler.yml" 'cron: "0 9 * * 1"' \
    "release scheduler should use a weekly cron for exact biweekly gating"
  expect_file_contains "$WORKFLOWS/release-scheduler.yml" 'anchor_date="2026-06-29"' \
    "release scheduler cadence should be anchored to the intended first biweekly run"
  expect_file_contains "$WORKFLOWS/release-scheduler.yml" "scheduler_due:" \
    "release scheduler should expose scheduler_due as an analyze output"
  expect_file_contains "$WORKFLOWS/release-scheduler.yml" "scheduler_reason:" \
    "release scheduler should expose scheduler_reason as an analyze output"

  local cadence_section
  cadence_section="$(workflow_section "$WORKFLOWS/release-scheduler.yml" "Resolve scheduler cadence" "Resolve AI execution mode")"
  expect_section_contains "$cadence_section" 'run_date="$(date -u +%F)"' \
    "release scheduler cadence should evaluate the UTC run date"
  expect_section_contains "$cadence_section" 'manual dispatch bypasses biweekly cadence guard' \
    "manual release scheduler runs should bypass the cadence gate"
  expect_section_contains "$cadence_section" 'delta_days=$(( (run_epoch - anchor_epoch) / 86400 ))' \
    "release scheduler should compute date distance from the anchor"
  expect_section_contains "$cadence_section" '$((delta_days % 14))' \
    "release scheduler should allow only exact 14-day intervals"
  expect_section_contains "$cadence_section" 'due=false' \
    "release scheduler should mark off-cadence scheduled runs as not due"

  local model_section
  model_section="$(workflow_section "$WORKFLOWS/release-scheduler.yml" "Resolve AI model" "Read version from pom.xml")"
  expect_section_contains "$model_section" "if: steps.scheduler_cadence.outputs.due == 'true'" \
    "release scheduler should skip model setup before off-cadence scheduled runs"

  local gate_section
  gate_section="$(workflow_section "$WORKFLOWS/release-scheduler.yml" "Check if changes detected" "No release - AI execution skipped")"
  expect_section_contains "$gate_section" 'SCHEDULER_DUE: ${{ steps.scheduler_cadence.outputs.due }}' \
    "release scheduler gate should read the cadence output"
  expect_section_contains "$gate_section" 'if [ "$scheduler_due" != "true" ]; then' \
    "release scheduler gate should short-circuit off-cadence scheduled runs"
  expect_section_contains "$gate_section" "No release - off biweekly cadence" \
    "release scheduler should have an explicit off-cadence no-release path"

  local post_section
  post_section="$(workflow_section "$WORKFLOWS/release-scheduler.yml" "Post to Release Scheduler discussion" "with:")"
  expect_section_contains "$post_section" "needs.analyze.outputs.scheduler_due != 'false'" \
    "release scheduler should skip production discussion posts for off-cadence scheduled runs"

  pass "test_release_scheduler_uses_true_biweekly_cadence_guard"
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
  publish_dispatch="$(workflow_section "$WORKFLOWS/publish-release.yml" 'Dispatch snapshot publication' 'core.endGroup()')"
  expect_section_contains "$publish_dispatch" "github.event_name == 'workflow_dispatch'" \
    "publish-release should dispatch snapshots only for explicit recovery runs"
  expect_section_contains "$publish_dispatch" 'workflow_id: "snapshot.yml"' \
    "publish-release should preserve manual recovery snapshot dispatch"
  expect_section_contains "$publish_dispatch" 'dryRun: "false"' \
    "publish-release should explicitly dispatch snapshot publication as non-dry-run"

  pass "test_downstream_dispatches_explicitly_pass_dry_run"
}

test_mutating_steps_remain_dry_run_gated() {
  echo "Running test_mutating_steps_remain_dry_run_gated"

  expect_file_contains "$WORKFLOWS/release-scheduler.yml" "if: always() && needs.analyze.result != 'skipped' && needs.analyze.outputs.dryRun != 'true' && needs.analyze.outputs.scheduler_due != 'false'" \
    "release scheduler discussion mutation should skip dry-run, skipped, and off-cadence scheduled runs"

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

test_release_scheduler_ai_modes_protect_manual_debug_budget() {
  echo "Running test_release_scheduler_ai_modes_protect_manual_debug_budget"

  local input_section
  input_section="$(workflow_section "$WORKFLOWS/release-scheduler.yml" "aiMode:" "permissions:")"
  expect_section_contains "$input_section" "default: probe" \
    "release scheduler manual AI mode should default to probe"
  expect_section_contains "$input_section" "type: choice" \
    "release scheduler AI mode should be a choice input"
  expect_section_contains "$input_section" "- probe" \
    "release scheduler AI mode should support probe"
  expect_section_contains "$input_section" "- full" \
    "release scheduler AI mode should support full"
  expect_section_contains "$input_section" "- skip" \
    "release scheduler AI mode should support skip"

  local mode_section
  mode_section="$(workflow_section "$WORKFLOWS/release-scheduler.yml" "Resolve AI execution mode" "Read version from pom.xml")"
  expect_section_contains "$mode_section" 'raw="${AI_MODE_INPUT:-probe}"' \
    "manual release scheduler runs should default AI mode to probe"
  expect_section_contains "$mode_section" 'raw="full"' \
    "scheduled release scheduler runs should keep full AI analysis"
  expect_section_contains "$mode_section" 'full|probe|skip)' \
    "release scheduler should validate supported AI modes"

  expect_file_contains "$WORKFLOWS/release-scheduler.yml" "No release - AI execution skipped" \
    "release scheduler should expose an explicit no-model-call skip path"
  expect_file_contains "$WORKFLOWS/release-scheduler.yml" "Resolve AI model" \
    "release scheduler should resolve the configured model without a remote catalog call"
  expect_file_contains "$WORKFLOWS/release-scheduler.yml" "if: steps.gate.outputs.proceed == 'true' && steps.ai_mode.outputs.mode != 'skip'" \
    "release scheduler model token checks should skip aiMode=skip"
  expect_file_contains "$WORKFLOWS/release-scheduler.yml" "if: steps.gate.outputs.proceed == 'true' && steps.ai_mode.outputs.mode == 'full'" \
    "release scheduler should only run full-analysis setup in full AI mode"

  local request_section
  request_section="$(workflow_section "$WORKFLOWS/release-scheduler.yml" "Build and validate AI request JSON" "Call AI API once")"
	  expect_section_contains "$request_section" "audit:ai_probe_request" \
	    "release scheduler probe mode should build a tiny probe request"
	  expect_section_contains "$request_section" "max_tokens: 64" \
	    "release scheduler probe request should cap response size"
	  expect_section_contains "$request_section" "release-ai-request-metadata.json" \
	    "release scheduler should emit request metadata for probe and full AI modes"
	  expect_section_contains "$request_section" "--max-request-bytes" \
	    "release scheduler should enforce a non-billed AI request transport budget before the model call"

	  local call_section
	  call_section="$(workflow_section "$WORKFLOWS/release-scheduler.yml" "Call AI API once" "Handle AI API failure")"
	  expect_section_not_contains "$call_section" "--retry" \
	    "release scheduler should avoid nested curl retries that multiply model calls"
	  expect_section_contains "$call_section" 'attempts=1' \
	    "release scheduler full and probe AI modes should make one model call"
	  expect_section_contains "$call_section" 'billed_full_retry=false' \
	    "release scheduler should not repeat the same billed full AI request after transport failure"

	  expect_file_contains "$WORKFLOWS/release-scheduler.yml" "decision:ai_mode=" \
	    "release scheduler summaries should include AI mode"
	  expect_file_contains "$WORKFLOWS/release-scheduler.yml" "decision:prompt_profile=" \
	    "release scheduler summaries should include prompt profile"
	  expect_file_contains "$WORKFLOWS/release-scheduler.yml" "- ai mode: \${ai_mode}" \
	    "release scheduler notification should include AI mode"
	  expect_file_contains "$WORKFLOWS/release-scheduler.yml" "- prompt profile: \${prompt_profile}" \
	    "release scheduler notification should include prompt profile"

  pass "test_release_scheduler_ai_modes_protect_manual_debug_budget"
}

test_release_scheduler_ai_failures_remain_diagnostic_and_red() {
  echo "Running test_release_scheduler_ai_failures_remain_diagnostic_and_red"

  local catalog_section
  catalog_section="$(workflow_section "$WORKFLOWS/release-scheduler.yml" "Preflight GitHub Models catalog" "Build release dossier")"
  expect_section_contains "$catalog_section" "audit:model_catalog_retry" \
    "release scheduler catalog preflight should retry transient GitHub Models failures"

  local ai_call_section
  ai_call_section="$(workflow_section "$WORKFLOWS/release-scheduler.yml" "Call AI API once" "Handle AI API failure")"
	  expect_section_contains "$ai_call_section" "curl-error.log" \
	    "release scheduler should retain curl diagnostics for AI calls"
	  expect_section_contains "$ai_call_section" "response-headers.txt" \
	    "release scheduler should retain response headers when available"
	  expect_section_contains "$ai_call_section" "curl-metrics.log" \
	    "release scheduler should retain curl transfer metrics"
	  expect_section_contains "$ai_call_section" "--show-error" \
	    "release scheduler curl invocation should preserve transport errors"
  expect_section_contains "$ai_call_section" "--http1.1" \
    "release scheduler should avoid GitHub Models HTTP/2 stream cancellations"
	  expect_section_contains "$ai_call_section" "curl_exit_code=\$?" \
	    "release scheduler should capture curl exit status"
	  expect_section_contains "$ai_call_section" "audit:ai_request_attempt attempt=\$attempt status=\$response_status curl_exit=\$curl_exit_code response_bytes=\$response_bytes" \
	    "release scheduler AI retry audit should include status, curl exit, and response bytes"

	  local ai_failure_section
	  ai_failure_section="$(workflow_section "$WORKFLOWS/release-scheduler.yml" "Handle AI API failure" "Extract AI response content")"
	  expect_section_contains "$ai_failure_section" "steps.ai_call.outputs.curl_exit_code != '0'" \
	    "release scheduler failure path should run when curl fails even if HTTP status is 200"
	  expect_section_contains "$ai_failure_section" "curl diagnostics (last 20 lines)" \
	    "release scheduler failure path should print bounded curl diagnostics"
	  expect_section_contains "$ai_failure_section" "ai-transport-diagnostics" \
	    "release scheduler failure path should write structured transport diagnostics"
	  expect_section_contains "$ai_failure_section" "release-ai-transport-diagnostics.json" \
	    "release scheduler failure path should persist diagnostics as an artifact"
	  expect_section_contains "$ai_failure_section" 'error_annotation="${err_msg//' \
	    "release scheduler should escape AI failure messages before creating workflow annotations"
  expect_section_contains "$ai_failure_section" 'echo "::error::$error_annotation"' \
    "release scheduler should surface escaped AI failure text as a GitHub Actions error"
	  expect_section_contains "$ai_failure_section" "exit 1" \
	    "release scheduler should fail when required AI inference does not answer"

	  local ai_extract_section
	  ai_extract_section="$(workflow_section "$WORKFLOWS/release-scheduler.yml" "Extract AI response content" "Parse AI JSON")"
	  expect_section_contains "$ai_extract_section" "steps.ai_call.outputs.curl_exit_code == '0'" \
	    "release scheduler should parse AI content only when curl completed cleanly"

	  local artifact_section
	  artifact_section="$(workflow_section "$WORKFLOWS/release-scheduler.yml" "Upload release scheduler audit artifacts" "retention-days")"
	  expect_section_contains "$artifact_section" "curl-error.log" \
	    "release scheduler audit artifact should include curl diagnostics"
	  expect_section_contains "$artifact_section" "curl-metrics.log" \
	    "release scheduler audit artifact should include curl metrics"
	  expect_section_contains "$artifact_section" "response-headers.txt" \
	    "release scheduler audit artifact should include response headers"
	  expect_section_contains "$artifact_section" "release-ai-request-metadata.json" \
	    "release scheduler audit artifact should include request metadata"
	  expect_section_contains "$artifact_section" "release-ai-transport-diagnostics.json" \
	    "release scheduler audit artifact should include structured transport diagnostics"

	  local summary_section
	  summary_section="$(workflow_section "$WORKFLOWS/release-scheduler.yml" "Debug decision summary" "Upload release scheduler audit artifacts")"
	  expect_section_contains "$summary_section" "do not rerun billed aiMode=full blindly" \
	    "release scheduler mutation plan should include non-blind recovery guidance"

	  pass "test_release_scheduler_ai_failures_remain_diagnostic_and_red"
	}

test_snapshot_and_health_manual_dry_runs_do_not_mutate() {
  echo "Running test_snapshot_and_health_manual_dry_runs_do_not_mutate"

  local deploy_section
  deploy_section="$(workflow_section "$WORKFLOWS/snapshot.yml" "Deploy Snapshot" "Snapshot publication summary")"
  expect_section_contains "$deploy_section" "if: steps.dry_run.outputs.dryRun != 'true'" \
    "snapshot deployment and exact consumer verification should skip dry-run"
  expect_section_contains "$deploy_section" "Verify exact snapshot consumption" \
    "snapshot dry-run boundary should include exact consumer verification"

  local health_post_section
  health_post_section="$(workflow_section "$WORKFLOWS/release-health.yml" "Post to Release Scheduler discussion" "with:")"
  expect_section_contains "$health_post_section" "if: always() && steps.dry_run.outputs.dryRun != 'true'" \
    "release-health discussion post should skip dry-run"

  pass "test_snapshot_and_health_manual_dry_runs_do_not_mutate"
}

test_snapshot_workflow_guarantees_exact_consumability() {
  echo "Running test_snapshot_workflow_guarantees_exact_consumability"

  expect_file_contains "$WORKFLOWS/snapshot.yml" "branches:" \
    "snapshot workflow should retain a branch push trigger"
  expect_file_contains "$WORKFLOWS/snapshot.yml" "- master" \
    "snapshot workflow should publish every master push"
  expect_file_contains "$WORKFLOWS/snapshot.yml" "cancel-in-progress: false" \
    "snapshot publications should queue instead of cancelling an active deployment"

  local deploy_line consumption_line summary_line
  deploy_line="$(line_of "$WORKFLOWS/snapshot.yml" "Deploy Snapshot")"
  consumption_line="$(line_of "$WORKFLOWS/snapshot.yml" "Verify exact snapshot consumption")"
  summary_line="$(line_of "$WORKFLOWS/snapshot.yml" "Snapshot publication summary")"
  if (( consumption_line <= deploy_line || consumption_line >= summary_line )); then
    fail "exact snapshot consumption verification should run after deployment and before the summary"
  fi

  local consumption_section
  consumption_section="$(workflow_section "$WORKFLOWS/snapshot.yml" "Verify exact snapshot consumption" "Snapshot publication summary")"
  expect_section_contains "$consumption_section" "snapshot-consumption" \
    "snapshot workflow should invoke the isolated Maven consumer"
  expect_section_contains "$consumption_section" "--max-attempts 20" \
    "snapshot consumption should use the five-minute bounded retry budget"
  expect_section_contains "$consumption_section" "--retry-seconds 15" \
    "snapshot consumption should retry every 15 seconds"

  expect_file_contains "$WORKFLOWS/snapshot.yml" "snapshot-consumption.json" \
    "snapshot audit artifact should retain structured consumer evidence"
  expect_file_contains "$WORKFLOWS/snapshot.yml" "snapshot-consumption.log" \
    "snapshot audit artifact should retain the bounded Maven consumer log"
  expect_file_contains "$WORKFLOWS/snapshot.yml" '"mavenConsumable"' \
    "snapshot audit should expose exact Maven consumability"
  expect_file_contains "$WORKFLOWS/release-health.yml" "--require-artifacts" \
    "release health should retrieve exact version-level POM/JAR artifacts"
  expect_file_contains "$WORKFLOWS/release-health.yml" "snapshot metadata latest (informational)" \
    "release health should not treat top-level latest as authoritative"

  pass "test_snapshot_workflow_guarantees_exact_consumability"
}

test_line_of_reports_missing_needles_cleanly() {
  echo "Running test_line_of_reports_missing_needles_cleanly"

  local tmp
  local output
  tmp="$(mktemp "${TMPDIR:-/tmp}/release-workflow-safety.XXXXXX")"
  printf 'present\n' > "$tmp"

  if output="$( (line_of "$tmp" "missing") 2>&1 )"; then
    rm -f "$tmp"
    fail "line_of should fail when the workflow line is missing"
  fi

  rm -f "$tmp"
  expect_section_contains "$output" "missing expected workflow line 'missing'" \
    "line_of should surface the explicit missing-line failure message"

  pass "test_line_of_reports_missing_needles_cleanly"
}

test_publish_release_existing_tag_only_fails_real_runs() {
  echo "Running test_publish_release_existing_tag_only_fails_real_runs"

  expect_file_contains "$WORKFLOWS/publish-release.yml" \
    "if: steps.check_tag.outputs.exists == 'true' && steps.dry_run.outputs.dryRun != 'true'" \
    "publish-release should fail existing tags only during real runs"
  expect_file_contains "$WORKFLOWS/publish-release.yml" "Report existing tag in dry-run mode" \
    "publish-release should keep a dry-run audit path for existing tags"
  expect_file_contains "$WORKFLOWS/publish-release.yml" "audit:existing_tag_dry_run" \
    "publish-release dry-run tag detection should emit an audit line"

  pass "test_publish_release_existing_tag_only_fails_real_runs"
}

test_github_release_preserves_workflow_support_checkout() {
  echo "Running test_github_release_preserves_workflow_support_checkout"

  local full_checkout_line
  local support_checkout_line
  local manifest_line
  full_checkout_line="$(line_of "$WORKFLOWS/github-release.yml" "Checkout full history")"
  support_checkout_line="$(line_of "$WORKFLOWS/github-release.yml" "Checkout workflow support files")"
  manifest_line="$(line_of "$WORKFLOWS/github-release.yml" "workflow-support/scripts/release/release_helpers.sh artifact-manifest")"

  if (( support_checkout_line <= full_checkout_line )); then
    fail "github-release should checkout workflow support after the release tag checkout"
  fi
  if (( manifest_line <= support_checkout_line )); then
    fail "github-release should validate artifacts after workflow support checkout"
  fi
  expect_file_contains "$WORKFLOWS/github-release.yml" "path: workflow-support" \
    "github-release should keep support helpers outside the release tag checkout"

  pass "test_github_release_preserves_workflow_support_checkout"
}

test_github_script_blocks_do_not_redeclare_injected_bindings() {
  echo "Running test_github_script_blocks_do_not_redeclare_injected_bindings"

  local workflow
  for workflow in "$WORKFLOWS"/*.yml; do
    assert_no_github_script_injected_binding_redeclarations "$workflow"
  done

  pass "test_github_script_blocks_do_not_redeclare_injected_bindings"
}

test_github_script_binding_scan_rejects_bad_fixture() {
  echo "Running test_github_script_binding_scan_rejects_bad_fixture"

  local tmp
  local output
  tmp="$(mktemp "${TMPDIR:-/tmp}/release-workflow-safety.XXXXXX")"
  cat > "$tmp" <<'YAML'
name: Bad fixture
jobs:
  bad:
    steps:
      - name: Bad GitHub Script
        uses: actions/github-script@v9
        with:
          script: |
            const core = require("@actions/core");
YAML

  if output="$(assert_no_github_script_injected_binding_redeclarations "$tmp" 2>&1)"; then
    rm -f "$tmp"
    fail "github-script injected binding scan should fail on a core redeclaration"
  fi

  rm -f "$tmp"
  expect_section_contains "$output" "redeclares actions/github-script injected binding" \
    "github-script injected binding scan should report the redeclaration"

  pass "test_github_script_binding_scan_rejects_bad_fixture"
}

test_maven_workflow_jobs_setup_jdk25_before_maven
test_mutating_manual_workflows_default_to_dry_run
test_official_triggers_normalize_to_non_dry_run
test_release_scheduler_uses_true_biweekly_cadence_guard
test_downstream_dispatches_explicitly_pass_dry_run
test_mutating_steps_remain_dry_run_gated
test_dry_run_summaries_and_audits_show_rerun_guidance
test_release_scheduler_ai_modes_protect_manual_debug_budget
test_release_scheduler_ai_failures_remain_diagnostic_and_red
test_snapshot_and_health_manual_dry_runs_do_not_mutate
test_snapshot_workflow_guarantees_exact_consumability
test_line_of_reports_missing_needles_cleanly
test_publish_release_existing_tag_only_fails_real_runs
test_github_release_preserves_workflow_support_checkout
test_github_script_blocks_do_not_redeclare_injected_bindings
test_github_script_binding_scan_rejects_bad_fixture
