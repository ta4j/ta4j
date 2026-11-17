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

expect_file_not_contains() {
  local file="$1"
  local needle="$2"
  local msg="$3"
  if grep -Fq -- "$needle" "$file"; then
    fail "$msg (unexpected: '$needle' in ${file#"$ROOT"/})"
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

    MAVEN_RUN_REGEX="$maven_run_regex" awk -v file="${workflow#"$ROOT"/}" '
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
      $0 ~ ENVIRON["MAVEN_RUN_REGEX"] {
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

  expect_file_contains "$WORKFLOWS/release-scheduler.yml" 'DRY_RUN_INPUT: ${{ inputs.dryRun }}' \
    "release scheduler should pass the typed manual flag to tested deterministic policy"
  expect_file_contains "$WORKFLOWS/release-scheduler.yml" 'python3 scripts/release/scheduler.py context' \
    "release scheduler should use the tested event and cadence normalization"

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

  expect_file_contains "$ROOT/scripts/release/scheduler.cjs" 'inputs: { releaseVersion: version, dryRun, sourceSha }' \
    "release scheduler dispatch should bind preparation version, dry-run flag and assessed source"

  local prepare_dispatch
  prepare_dispatch="$(workflow_section "$WORKFLOWS/prepare-release.yml" '  publish_automatic:' '    secrets: inherit')"
  expect_section_contains "$prepare_dispatch" "dryRun" \
    "prepare-release reusable call should explicitly pass dryRun to publish-release"

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

  expect_file_contains "$WORKFLOWS/release-scheduler.yml" "needs.eligibility.outputs.dry_run == 'false' && needs.eligibility.outputs.due == 'true'" \
    "release scheduler discussion mutation should require an explicitly live due run"

  expect_file_contains "$WORKFLOWS/prepare-release.yml" "if: needs.plan.outputs.dry_run != 'true'" \
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

  expect_file_contains "$WORKFLOWS/release-scheduler.yml" "release-evidence/scheduler-outcome.json" \
    "release scheduler should retain its typed outcome and decision path"
  expect_file_contains "$ROOT/scripts/release/scheduler.py" "For a stale candidate or changed evidence, start a new scheduler run." \
    "release scheduler should explain recovery without encouraging blind model retries"
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
  expect_section_contains "$consumption_section" 'SNAPSHOT_VERSION: ${{ steps.version.outputs.snapshotVersion }}' \
    "snapshot workflow should map the computed version through the step environment"
  expect_section_contains "$consumption_section" '--version "$SNAPSHOT_VERSION"' \
    "snapshot workflow should pass the environment-mapped version to the shell helper"
  expect_section_not_contains "$consumption_section" '--version "${{ steps.version.outputs.snapshotVersion }}"' \
    "snapshot workflow should not interpolate an Actions expression directly into shell code"
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
  expect_file_contains "$ROOT/scripts/release/release_helpers.sh" "cacheBust=" \
    "snapshot consumption should bypass stale CDN metadata with a cache-busted fetch"
  expect_file_contains "$ROOT/scripts/release/release_helpers.sh" "write_snapshot_consumer_pom" \
    "snapshot consumption should resolve immutable timestamped coordinates"
  expect_file_contains "$ROOT/scripts/release/release_helpers.sh" "SNAPSHOT_CONSUMPTION_DEADLINE_SECONDS=300" \
    "snapshot consumption should enforce the documented five-minute deadline"
  expect_file_contains "$ROOT/scripts/release/release_helpers.sh" "run_with_timeout" \
    "snapshot consumption should bound Maven resolution by the remaining deadline"

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


test_release_scheduler_graph_and_permissions() {
  echo "Running test_release_scheduler_graph_and_permissions"

  python3 - "$WORKFLOWS" <<'PYTHON'
from pathlib import Path
import os
import re
import subprocess
import sys
import tempfile
import textwrap

root = Path(sys.argv[1])


def jobs(path):
    source = path.read_text().split("\njobs:\n", 1)[1]
    matches = list(re.finditer(r"^  ([a-z_]+):\s*$", source, re.MULTILINE))
    return {match[1]: source[match.end():matches[index + 1].start() if index + 1 < len(matches) else len(source)]
            for index, match in enumerate(matches)}


def field(block, name):
    match = re.search(r"^    " + name + r":\s*(.+)$", block, re.MULTILINE)
    return match[1].strip() if match else ""


def dependencies(block):
    return set(field(block, "needs").strip("[]").replace(" ", "").split(",")) - {""}


def enabled(block, states, inputs=None):
    expression = field(block, "if").removeprefix("${{").removesuffix("}}").strip() or "True"
    # GitHub supplies success() implicitly unless the job condition explicitly
    # uses a status function. Exercise that skipped/failed dependency boundary.
    if not re.search(r"\b(always|success|failure|cancelled)\(\)", expression):
        if any(states.get(name, {}).get("result") != "success" for name in dependencies(block)):
            return False
    expression = expression.replace("always()", "True").replace("&&", " and ").replace("||", " or ")
    expression = re.sub(r"needs\.([a-z_]+)\.outputs\.([a-z_]+)",
                        lambda match: repr(states.get(match[1], {}).get("outputs", {}).get(match[2], "")), expression)
    expression = re.sub(r"needs\.([a-z_]+)\.result", lambda match: repr(states.get(match[1], {}).get("result", "")), expression)
    expression = re.sub(r"inputs\.([a-z_]+)", lambda match: repr((inputs or {}).get(match[1], False)), expression)
    return bool(eval(expression, {"__builtins__": {}}, {}))


scheduler = jobs(root / "release-scheduler.yml")
assessment = jobs(root / "reusable-release-assessment.yml")
required = {"eligibility", "context_preparation", "release_assessment", "release_policy", "release_handoff", "outcome", "notification"}
assert required <= scheduler.keys(), "named release stages must remain separate jobs"
assert dependencies(scheduler["context_preparation"]) == {"eligibility"}
assert dependencies(scheduler["release_assessment"]) == {"eligibility", "context_preparation"}
assert dependencies(scheduler["release_policy"]) == {"eligibility", "context_preparation", "release_assessment"}
assert dependencies(scheduler["release_handoff"]) == {"eligibility", "release_policy"}
assert dependencies(scheduler["outcome"]) >= required - {"outcome", "notification"}
assert dependencies(assessment["policy"]) == {"context", "codex", "luna"}
for name in required:
    assert field(scheduler[name], "name"), name + " needs a human-readable graph name"
    writes = re.findall(r"^      ([a-z-]+): write\s*$", scheduler[name], re.MULTILINE)
    assert set(writes) == ({"actions"} if name == "release_handoff" else {"discussions"} if name == "notification" else set()), (name, writes)
for name, block in assessment.items():
    assert not re.search(r"^\s+[a-z-]+: write\s*$", block, re.MULTILINE), name + " must not mutate repository state"
assert field(assessment["luna"], "continue-on-error") == "true", "shadow infrastructure failure must stay advisory"
assert field(assessment["codex"], "continue-on-error") != "true", "authoritative provider failure must not be hidden"

# Executable controllers must come from the pinned called workflow itself.
# A caller-supplied candidate can be inspected, but cannot select controller code
# in a job with an API credential or shared Actions artifact/cache scope.
framework_checkouts = candidate_checkouts = 0
for name, block in assessment.items():
    steps = re.split(r"(?=^      - name:)", block, flags=re.MULTILINE)
    identity_guard = next(step for step in steps if "Require the reusable workflow's trusted source identity" in step)
    assert 'FRAMEWORK_REPOSITORY: ${{ fromJSON(toJSON(job)).workflow_repository }}' in identity_guard
    assert 'FRAMEWORK_SHA: ${{ fromJSON(toJSON(job)).workflow_sha }}' in identity_guard
    assert '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$' in identity_guard and '^[0-9a-f]{40}$' in identity_guard
    assert 'exit 1' in identity_guard
    assert 'id: framework' in identity_guard
    assert 'inputs.' not in identity_guard, 'native identity must not accept caller input'
    # Execute the real guard: missing/malformed metadata must not produce even a
    # partial checkout identity. A valid native identity is forwarded unchanged.
    script = textwrap.dedent(identity_guard.split('        run: |\n', 1)[1])
    cases = [('ta4j/ta4j', 'a' * 40, True), ('example/framework', 'b' * 40, True),
             ('', '', False), (None, None, False), ('ta4j/ta4j', '', False),
             ('ta4j/ta4j', 'master', False), ('owner/repo/extra', 'a' * 40, False),
             ('ta4j/ta4j', 'A' * 40, False), ('ta4j/ta4j\nrepository=other/repo', 'a' * 40, False),
             ('ta4j/ta4j', 'a' * 40 + '\nrevision=master', False)]
    with tempfile.TemporaryDirectory(prefix='ta4j-framework-identity-') as temporary:
        output = Path(temporary) / 'outputs'
        for repository, revision, valid in cases:
            output.write_text('')
            environment = dict(os.environ, BASH_ENV='/dev/null', GITHUB_OUTPUT=str(output))
            environment.pop('FRAMEWORK_REPOSITORY', None)
            environment.pop('FRAMEWORK_SHA', None)
            if repository is not None:
                environment['FRAMEWORK_REPOSITORY'] = repository
            if revision is not None:
                environment['FRAMEWORK_SHA'] = revision
            result = subprocess.run(['bash', '-c', script], cwd=temporary, env=environment,
                                    text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            assert (result.returncode == 0) == valid, (name, repository, revision, result.stderr)
            assert output.read_text() == (f'repository={repository}\nrevision={revision}\n' if valid else '')
    for step in steps:
        if 'uses: actions/checkout@' not in step:
            assert not re.search(r'(?:run:|python3|bash|node|source)\s+["\']?(?:\./)?release-assessment-work/candidate/', step)
            assert not re.search(r'working-directory:\s*["\']?(?:\./)?release-assessment-work/candidate\s*$', step, re.MULTILINE)
            continue
        assert 'persist-credentials: false' in step
        if 'path: release-assessment-work/framework' in step:
            framework_checkouts += 1
            assert 'repository: ${{ steps.framework.outputs.repository }}' in step
            assert 'ref: ${{ steps.framework.outputs.revision }}' in step
            assert 'inputs.' not in step, 'caller input must not select executable framework code'
            assert block.index(identity_guard) < block.index(step), 'native identity must fail closed before checkout'
        else:
            candidate_checkouts += 1
            assert name == 'codex' and 'path: release-assessment-work/candidate' in step
            assert 'ref: ${{ inputs.source_sha }}' in step, 'candidate must remain isolated assessment data'
assert framework_checkouts == len(assessment) and candidate_checkouts == 1
source = (root / 'reusable-release-assessment.yml').read_text()
assert 'framework_repository:' not in source and 'framework_sha:' not in source


def states():
    value = {name: {"result": "success", "outputs": {}} for name in required}
    value["eligibility"]["outputs"] = {"eligible": "true", "dry_run": "false", "due": "true"}
    value["release_policy"]["outputs"] = {"should_release": "true"}
    return value


current = states()
assert enabled(scheduler["context_preparation"], current)
assert enabled(scheduler["release_assessment"], current)
assert enabled(scheduler["release_policy"], current)
assert enabled(scheduler["release_handoff"], current)
assert enabled(scheduler["notification"], current)
current["eligibility"]["outputs"]["eligible"] = "false"
assert not enabled(scheduler["context_preparation"], current)
assert not enabled(scheduler["release_assessment"], current)
for stage in ("eligibility", "context_preparation", "release_assessment", "release_policy", "release_handoff"):
    for result in ("failure", "cancelled", "skipped"):
        current = states()
        current[stage]["result"] = result
        assert enabled(scheduler["outcome"], current), (stage, result, "must still report")
        if stage in ("eligibility", "release_policy"):
            assert not enabled(scheduler["release_handoff"], current), (stage, result, "must not dispatch")
        if stage == "context_preparation":
            assert not enabled(scheduler["release_assessment"], current)
            assert not enabled(scheduler["release_policy"], current)
        if stage == "release_assessment":
            assert enabled(scheduler["release_policy"], current), "policy must record unavailable assessment"
current = states()
current["release_policy"]["outputs"]["should_release"] = "false"
assert not enabled(scheduler["release_handoff"], current), "defer, insufficient and unavailable must not dispatch"
for output, value in (("dry_run", "true"), ("due", "false"), ("dry_run", ""), ("due", "")):
    current = states()
    current["eligibility"]["outputs"][output] = value
    assert not enabled(scheduler["notification"], current), (output, value, "must not notify")
for codex in ("success", "failure", "skipped", "cancelled"):
    for luna in ("success", "failure", "skipped", "cancelled"):
        current = {"context": {"result": "success"}, "codex": {"result": codex}, "luna": {"result": luna}}
        assert enabled(assessment["policy"], current), (codex, luna, "must record a fail-closed result")
assert not enabled(assessment["luna"], {"context": {"result": "success"}}, {"evaluate_luna": False})
assert enabled(assessment["luna"], {"context": {"result": "success"}}, {"evaluate_luna": True})
PYTHON

  pass "test_release_scheduler_graph_and_permissions"
}

test_release_scheduler_provider_and_evidence_contracts() {
  echo "Running test_release_scheduler_provider_and_evidence_contracts"

  expect_file_contains "$WORKFLOWS/release-scheduler.yml" 'cron: "0 9 * * 1"' \
    "scheduler should retain the weekly trigger for tested biweekly eligibility"
  expect_file_contains "$WORKFLOWS/release-scheduler.yml" "vars.RELEASE_SCHEDULER_ENABLED == 'true'" \
    "scheduled inference should remain explicitly enabled"
  expect_file_contains "$WORKFLOWS/release-scheduler.yml" 'options: [full, skip]' \
    "manual runs should support a charge-free deterministic-only path"
  expect_file_contains "$WORKFLOWS/release-scheduler.yml" 'evaluateLuna:' \
    "additional shadow inference should be explicit"
  expect_file_contains "$WORKFLOWS/release-scheduler.yml" 'uses: ./.github/workflows/reusable-release-assessment.yml' \
    "scheduler should call the shared assessment workflow"
  expect_file_contains "$WORKFLOWS/reusable-release-assessment.yml" "permission-profile: ':read-only'" \
    "model execution should use the native read-only permission profile"
  if ! grep -Eq 'uses: openai/codex-action@[0-9a-f]{40}( |$)' "$WORKFLOWS/reusable-release-assessment.yml"; then
    fail "release assessment must pin the official Codex action to an immutable commit"
  fi
  expect_file_contains "$WORKFLOWS/reusable-release-assessment.yml" 'output-schema-file:' \
    "official Codex action should enforce a native typed output contract"
  expect_file_contains "$WORKFLOWS/reusable-release-assessment.yml" 'CODEX_STATUS: ${{ needs.codex.result }}' \
    "policy must reject failed provider jobs even when they leave valid JSON"
  expect_file_contains "$WORKFLOWS/release-scheduler.yml" 'ASSESSOR_STATUS: ${{ needs.release_assessment.result }}' \
    "outer policy must preserve assessment workflow failures"
  expect_file_contains "$WORKFLOWS/reusable-release-assessment.yml" '--requirement release-assessment-work/framework/scripts/release/requirements-assessment.txt' \
    "shadow inference should install the pinned official SDK"
  expect_file_contains "$WORKFLOWS/release-scheduler.yml" 'release-evidence/release-assessment-manifest.json' \
    "assessment inputs must retain the source-bound evidence manifest"
  expect_file_contains "$WORKFLOWS/release-scheduler.yml" 'source_sha: ${{ needs.eligibility.outputs.source_sha }}' \
    "assessment should receive the exact eligible source"
  expect_file_not_contains "$WORKFLOWS/release-scheduler.yml" 'api.openai.com' \
    "scheduler should not implement a custom model HTTP client"
  expect_file_not_contains "$ROOT/scripts/release/assessment.py" 'urllib.request' \
    "Decisions should use the official SDK rather than custom HTTP"

  pass "test_release_scheduler_provider_and_evidence_contracts"
}

test_release_scheduler_output_contracts() {
  echo "Running test_release_scheduler_output_contracts"

  expect_file_contains "$WORKFLOWS/reusable-release-assessment.yml" '"--json"' \
    "Codex should suppress its default full prompt banner using supported structured output"
  expect_file_contains "$WORKFLOWS/reusable-release-assessment.yml" 'model_reasoning_summary=' \
    "routine provider output should not request a reasoning transcript"
  expect_file_contains "$WORKFLOWS/release-scheduler.yml" 'release-evidence/scheduler-outcome.md' \
    "one concise final report should explain the decision path"
  expect_file_contains "$WORKFLOWS/reusable-release-assessment.yml" 'assessment-result.json' \
    "the validated provider result should remain available for diagnosis"
  local workflow
  for workflow in release-scheduler.yml reusable-release-assessment.yml; do
    expect_file_not_contains "$WORKFLOWS/$workflow" 'response-headers.txt' \
      "provider transport headers should not be retained by default"
    expect_file_not_contains "$WORKFLOWS/$workflow" 'curl-error.log' \
      "removed custom transport diagnostics should not remain in artifacts"
    expect_file_not_contains "$WORKFLOWS/$workflow" '*.log' \
      "release artifacts should use explicit paths rather than collecting every log"
  done

  pass "test_release_scheduler_output_contracts"
}

test_maven_workflow_jobs_setup_jdk25_before_maven
test_mutating_manual_workflows_default_to_dry_run
test_official_triggers_normalize_to_non_dry_run
test_downstream_dispatches_explicitly_pass_dry_run
test_mutating_steps_remain_dry_run_gated
test_dry_run_summaries_and_audits_show_rerun_guidance
test_snapshot_and_health_manual_dry_runs_do_not_mutate
test_snapshot_workflow_guarantees_exact_consumability
test_line_of_reports_missing_needles_cleanly
test_publish_release_existing_tag_only_fails_real_runs
test_github_release_preserves_workflow_support_checkout
test_github_script_blocks_do_not_redeclare_injected_bindings
test_github_script_binding_scan_rejects_bad_fixture

test_release_scheduler_graph_and_permissions
test_release_scheduler_provider_and_evidence_contracts
test_release_scheduler_output_contracts
