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

expect_text_contains() {
  local text="$1"
  local needle="$2"
  local msg="$3"
  if ! grep -Fq -- "$needle" <<<"$text"; then
    fail "$msg (missing: '$needle')"
  fi
}

expect_text_not_contains() {
  local text="$1"
  local needle="$2"
  local msg="$3"
  if grep -Fq -- "$needle" <<<"$text"; then
    fail "$msg (unexpected: '$needle')"
  fi
}

test_issue_permissions_declared() {
  echo "Running test_issue_permissions_declared"

  local permissions
  # sub() strips a CR so CRLF checkouts of the workflow compare equal.
  permissions="$(awk '
    { sub(/\r$/, "") }
    /^[[:space:]]*#/ { next }
    /^  prepare:/ { in_prepare = 1; next }
    in_prepare && /^    permissions:/ { in_permissions = 1; next }
    in_permissions && /^    [^[:space:]]/ { exit }
    in_permissions && $1 == "issues:" {
      sub(/^[[:space:]]*issues:[[:space:]]*/, "")
      print
      exit
    }
  ' "$WORKFLOW")"
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
  expect_file_contains "$WORKFLOW" "-pl ta4j-examples -am -DskipTests install" \
    "deprecation scan should install reactor dependencies before execution"
  expect_file_contains "$WORKFLOW" "-pl ta4j-examples exec:java" \
    "deprecation scan should run exec:java only on ta4j-examples"
  expect_file_contains "$WORKFLOW" "ta4jexamples.doc.RemovalReadyDeprecationScanner" \
    "deprecation scan should invoke the Java scanner"
  if grep -Fq "scan-removal-ready-deprecations.py" "$WORKFLOW"; then
    fail "deprecation scan should not depend on the removed Python scanner"
  fi

  pass "test_deprecation_scan_uses_java_scanner"
}

test_prepare_release_sets_up_jdk25_before_maven() {
  echo "Running test_prepare_release_sets_up_jdk25_before_maven"

  local setup_line
  local read_version_line
  local gate_line
  setup_line="$(line_of "Set up JDK 25")"
  read_version_line="$(line_of "Read current version from POM")"
  gate_line="$(line_of "Gate release-ready deprecations")"
  if (( setup_line >= read_version_line )); then
    fail "prepare-release should configure JDK 25 before reading the Maven project version"
  fi
  if (( setup_line >= gate_line )); then
    fail "prepare-release should configure JDK 25 before the deprecation release gate"
  fi

  expect_file_contains "$WORKFLOW" "uses: actions/setup-java@v5" \
    "prepare-release should use the standard setup-java action"
  expect_file_contains "$WORKFLOW" "distribution: temurin" \
    "prepare-release should use the same Temurin distribution as other workflows"
  expect_file_contains "$WORKFLOW" "java-version: 25" \
    "prepare-release should satisfy the Maven enforcer Java version"
  expect_file_contains "$WORKFLOW" "cache: maven" \
    "prepare-release should enable Maven cache for Java setup"

  pass "test_prepare_release_sets_up_jdk25_before_maven"
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

test_next_snapshot_issue_sync_runs_after_snapshot_commit() {
  echo "Running test_next_snapshot_issue_sync_runs_after_snapshot_commit"

  local snapshot_commit_line
  local scan_line
  local sync_line
  local push_line
  snapshot_commit_line="$(line_of "Commit next snapshot version")"
  scan_line="$(line_of "Scan removal-ready deprecations")"
  sync_line="$(line_of "Create or update removal-ready deprecation issues")"
  push_line="$(line_of "Push release branch")"
  if (( scan_line <= snapshot_commit_line )); then
    fail "next-snapshot deprecation scan should run after the next snapshot commit"
  fi
  if (( sync_line <= scan_line )); then
    fail "deprecation issue sync should run after the next-snapshot scan"
  fi
  if (( sync_line >= push_line )); then
    fail "deprecation issue sync should finish before the release branch is pushed"
  fi
  expect_file_contains "$WORKFLOW" "NEXT_REMOVAL_VERSION=\"\${NEXT_VERSION%-SNAPSHOT}\"" \
    "next-snapshot scan should derive the planned removal target from nextVersion"
  expect_file_contains "$WORKFLOW" "--target-removal-version \${NEXT_REMOVAL_VERSION}" \
    "next-snapshot scan should not rely on a dry-run POM mutation"
  expect_file_contains "$WORKFLOW" "--target-removal-version \${NEXT_REMOVAL_VERSION} --include-overdue" \
    "next-snapshot scan should include overdue removal versions when release versions jump"

  pass "test_next_snapshot_issue_sync_runs_after_snapshot_commit"
}

test_release_audit_includes_deprecation_counts() {
  echo "Running test_release_audit_includes_deprecation_counts"

  expect_file_contains "$WORKFLOW" "\"removalIssuePlans\"" \
    "release audit JSON should include removal issue plan counts"
  expect_file_contains "$WORKFLOW" "\"removalIssuesCreated\"" \
    "release audit JSON should include created removal issue counts"
  expect_file_contains "$WORKFLOW" "\"removalIssuesClosedStale\"" \
    "release audit JSON should include stale issue closure counts"
  expect_file_contains "$WORKFLOW" "removal-ready deprecation issue plans" \
    "workflow summary should include removal-ready deprecation plan counts"
  expect_file_contains "$WORKFLOW" "prepare-release-audit-\${{ github.run_id }}" \
    "prepare-release audit artifact should still be uploaded"

  pass "test_release_audit_includes_deprecation_counts"
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

test_deprecation_issue_sync_does_not_add_metadata_to_cleanup_issues() {
  echo "Running test_deprecation_issue_sync_does_not_add_metadata_to_cleanup_issues"

  local sync_section
  sync_section="$(workflow_section "Create or update removal-ready deprecation issues" "Upload removal-ready deprecation report")"
  if grep -Fq "labels:" <<<"$sync_section"; then
    fail "generated deprecation cleanup issues should not receive labels"
  fi
  if grep -Fq "assignees:" <<<"$sync_section"; then
    fail "generated deprecation cleanup issues should not receive assignees"
  fi

  pass "test_deprecation_issue_sync_does_not_add_metadata_to_cleanup_issues"
}

test_deprecation_scans_run_during_dry_runs() {
  echo "Running test_deprecation_scans_run_during_dry_runs"

  local gate_section
  local scan_section
  local issue_section
  gate_section="$(workflow_section "Gate release-ready deprecations" "Upload release-ready deprecation gate report")"
  scan_section="$(workflow_section "Scan removal-ready deprecations" "Create or update removal-ready deprecation issues")"
  issue_section="$(workflow_section "Create or update removal-ready deprecation issues" "Upload removal-ready deprecation report")"
  if grep -Fq "if: needs.plan.outputs.dry_run != 'true'" <<<"$gate_section"; then
    fail "release deprecation gate should run during dry-run"
  fi
  if grep -Fq "if: needs.plan.outputs.dry_run != 'true'" <<<"$scan_section"; then
    fail "next-snapshot deprecation scan should run during dry-run"
  fi
  expect_file_contains "$WORKFLOW" "scan_status=\$scan_status" \
    "release gate should expose scan status for delayed dry-run failure"
  expect_file_contains "$WORKFLOW" "if: needs.plan.outputs.dry_run == 'true' && steps.deprecation_release_gate.outputs.scan_status != '0'" \
    "dry-run should fail after report uploads when release-ready deprecations are found"
  if ! grep -Fq "if: needs.plan.outputs.dry_run != 'true'" <<<"$issue_section"; then
    fail "deprecation issue sync should skip dry-run mutations"
  fi

  pass "test_deprecation_scans_run_during_dry_runs"
}

test_release_pr_review_request_uses_review_plan() {
  echo "Running test_release_pr_review_request_uses_review_plan"

  local create_line
  local plan_line
  local request_line
  local warning_line
  create_line="$(line_of "Create or update release PR")"
  plan_line="$(line_of "Plan release PR reviewers")"
  request_line="$(line_of "Request release PR review")"
  warning_line="$(line_of "Report release PR review warning")"

  if (( create_line >= plan_line || plan_line >= request_line || request_line >= warning_line )); then
    fail "release PR review planning should run after PR creation and before review request/warning"
  fi

  local create_section
  local plan_section
  local request_section
  local warning_section
  create_section="$(workflow_section "Create or update release PR" "Plan release PR reviewers")"
  plan_section="$(workflow_section "Plan release PR reviewers" "Request release PR review")"
  request_section="$(workflow_section "Request release PR review" "Report release PR review warning")"
  warning_section="$(workflow_section "Report release PR review warning" "Prepare release summary")"

  expect_text_contains "$create_section" 'core.setOutput("pr_number"' \
    "release PR creation should expose the PR number"
  expect_text_contains "$create_section" 'core.setOutput("pr_author"' \
    "release PR creation should expose the PR author"
  expect_text_not_contains "$create_section" "requestReviewers" \
    "release PR creation should not request reviewers directly"

  expect_text_contains "$plan_section" "release-pr-review-plan" \
    "prepare-release should use the tested review-plan helper"
  expect_text_contains "$plan_section" "RELEASE_REVIEWERS" \
    "prepare-release should pass fallback reviewer users"
  expect_text_contains "$plan_section" "RELEASE_REVIEW_TEAMS" \
    "prepare-release should pass fallback reviewer teams"
  expect_text_contains "$plan_section" "--pr-author" \
    "review plan should receive the actual PR author"

  expect_text_contains "$request_section" "steps.release_review_plan.outputs.has_review_targets == 'true'" \
    "review request should run only when targets exist"
  expect_text_not_contains "$request_section" "uses: actions/github-script@" \
    "review request should not add another tag-pinned action"
  expect_text_contains "$request_section" "GH_TOKEN:" \
    "review request should authenticate gh with the selected workflow token"
  expect_text_contains "$request_section" 'json.loads(sys.argv[2] or "[]")' \
    "review request should consume planned reviewer JSON"
  expect_text_contains "$request_section" 'payload["team_reviewers"] = team_reviewers' \
    "review request should support team reviewers"
  expect_text_contains "$request_section" "release-review-request.json" \
    "review request should write a JSON API payload"
  expect_text_contains "$request_section" '"repos/${GITHUB_REPOSITORY}/pulls/${PR_NUMBER}/requested_reviewers"' \
    "review request should call the GitHub reviewers API"

  expect_text_contains "$warning_section" "::warning::\${warning}" \
    "prepare-release should emit a warning when no reviewer target remains"
  expect_file_contains "$WORKFLOW" '"releaseReviewWarning"' \
    "prepare-release audit should include review warning state"

  pass "test_release_pr_review_request_uses_review_plan"
}


test_assessed_source_binding() {
  echo "Running test_assessed_source_binding"

  expect_file_contains "$WORKFLOW" 'ref: ${{ steps.source.outputs.sha }}' \
    "handoff checkout should use the validated assessment source"
  expect_file_contains "$WORKFLOW" 'ref: ${{ needs.plan.outputs.base }}' \
    "preparation checkout should retain the validated source"
  expect_file_contains "$WORKFLOW" 'SOURCE_SHA: ${{ steps.source.outputs.sha }}' \
    "handoff freshness check should compare the validated source"
  expect_file_contains "$WORKFLOW" 'PLANNED_BASE: ${{ needs.plan.outputs.base }}' \
    "preparation should recheck the validated source before mutation"
  expect_file_contains "$WORKFLOW" '"sourceSha": "${SOURCE_SHA:-}"' \
    "release audit should retain the validated assessment source"
  expect_file_contains "$WORKFLOW" '- source commit: ${SOURCE_SHA:-unknown}' \
    "prepare summary should make the source identity reviewable"

  python3 - "$WORKFLOW" <<'PYTHON'
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

workflow = Path(sys.argv.pop()).read_text()


def shell_step(name):
    section = workflow.split("      - name: " + name + "\n", 1)[1]
    run = section.split("        run: |\n", 1)[1]
    lines = []
    for line in run.splitlines():
        if line and not line.startswith("          "):
            break
        lines.append(line[10:] if line else "")
    return "\n".join(lines)


identity = shell_step("Validate assessed source identity")
plan = shell_step("Capture starting master before acquiring the freeze")
prepare = shell_step("Capture immutable starting master")


class SourceBindingTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix="ta4j-source-binding-")
        cls.root = Path(cls.temp.name)
        cls.repo = cls.root / "work"
        cls.repo.mkdir()
        cls.remote = cls.root / "origin.git"
        cls.env = {key: value for key, value in os.environ.items()
                   if not key.startswith(("GIT_", "GITHUB_"))}
        cls.env.update(GIT_CONFIG_GLOBAL="/dev/null", GIT_CONFIG_NOSYSTEM="1",
                       GIT_AUTHOR_NAME="Fixture", GIT_AUTHOR_EMAIL="fixture@example.invalid",
                       GIT_COMMITTER_NAME="Fixture", GIT_COMMITTER_EMAIL="fixture@example.invalid",
                       BASH_ENV="/dev/null", AUTOMATIC="false", DEFAULT_BRANCH="master")
        cls.git("init", "-q", "-b", "master")
        cls.git("init", "-q", "--bare", str(cls.remote))
        cls.git("remote", "add", "origin", str(cls.remote))
        (cls.repo / "pom.xml").write_text("1.2.3-SNAPSHOT")
        cls.git("add", ".")
        cls.git("commit", "-qm", "assessed")
        cls.base = cls.git("rev-parse", "HEAD")
        (cls.repo / "pom.xml").write_text("1.2.4-SNAPSHOT")
        cls.git("commit", "-qam", "later change")
        cls.later = cls.git("rev-parse", "HEAD")
        # Publish both objects once; each case only changes local fixture refs.
        cls.git("push", "-q", "origin", "HEAD:refs/heads/master")
        cls.env.update(GITHUB_WORKFLOW_SHA=cls.base, DISPATCH_SHA=cls.base,
                       SOURCE_SHA=cls.base, PLANNED_BASE=cls.base)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    @classmethod
    def git(cls, *args):
        return subprocess.check_output(["git", *args], cwd=cls.repo, env=cls.env,
                                       text=True, stderr=subprocess.PIPE).strip()

    def setUp(self):
        self.git("checkout", "-q", "--detach", self.base)
        self.git("--git-dir", str(self.remote), "update-ref", "refs/heads/master", self.base)
        self.git("update-ref", "refs/remotes/origin/master", self.base)
        self.output = self.root / "outputs"
        self.output.write_text("")

    def run_step(self, script, **env):
        return subprocess.run(["bash", "-c", script], cwd=self.repo,
                              env=dict(self.env, GITHUB_OUTPUT=str(self.output), **env),
                              text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

    def assert_success(self, script, **env):
        result = self.run_step(script, **env)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("sha=" + self.base + "\n", self.output.read_text())

    def test_exact_assessed_commit_passes_every_boundary(self):
        self.assert_success(identity, ASSESSED_SOURCE_SHA=self.base)
        self.assert_success(plan)
        self.assert_success(prepare)

    def test_manual_omission_keeps_dispatch_source(self):
        self.assert_success(identity, ASSESSED_SOURCE_SHA="")
        self.assert_success(plan)
        self.assert_success(prepare)

    def test_malformed_source_fails_without_output_or_interpolation(self):
        for value in ("master", self.base[:12], self.base.upper(), "g" * 40,
                      self.base + "\n", "$(touch injected)"):
            with self.subTest(value=value):
                result = self.run_step(identity, ASSESSED_SOURCE_SHA=value)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(self.output.read_text(), "")
                self.assertFalse((self.repo / "injected").exists())

    def test_stale_assessment_rejects_newer_dispatch(self):
        result = self.run_step(identity, ASSESSED_SOURCE_SHA=self.base,
                               GITHUB_WORKFLOW_SHA=self.later, DISPATCH_SHA=self.later)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Reassess", result.stdout)
        self.assertEqual(self.output.read_text(), "")

    def test_default_branch_movement_rejects_before_freeze_and_preparation(self):
        self.git("--git-dir", str(self.remote), "update-ref", "refs/heads/master", self.later)
        for script in (plan, prepare):
            result = self.run_step(script)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("Default branch advanced", result.stdout)
            self.assertEqual(self.output.read_text(), "")

    def test_different_checkout_cannot_replace_assessed_source(self):
        self.git("checkout", "-q", "--detach", self.later)
        for script in (plan, prepare):
            result = self.run_step(script)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("does not match", result.stdout)
            self.assertEqual(self.output.read_text(), "")

    def test_source_and_workflow_identity_are_both_required(self):
        for script, changed in ((plan, {"SOURCE_SHA": self.later}),
                                (prepare, {"PLANNED_BASE": self.later}),
                                (plan, {"GITHUB_WORKFLOW_SHA": self.later}),
                                (prepare, {"GITHUB_WORKFLOW_SHA": self.later})):
            result = self.run_step(script, **changed)
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(self.output.read_text(), "")


unittest.main()
PYTHON

  pass "test_assessed_source_binding"
}

test_issue_permissions_declared
test_issue_sync_uses_targeted_search
test_deprecation_scan_uses_java_scanner
test_prepare_release_sets_up_jdk25_before_maven
test_release_gate_runs_before_next_snapshot
test_next_snapshot_issue_sync_runs_after_snapshot_commit
test_release_audit_includes_deprecation_counts
test_issue_sync_reconciles_stale_managed_issues
test_deprecation_issue_sync_does_not_add_metadata_to_cleanup_issues
test_deprecation_scans_run_during_dry_runs
test_release_pr_review_request_uses_review_plan

test_assessed_source_binding
