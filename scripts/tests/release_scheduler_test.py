#!/usr/bin/env python3
"""Exercise release policy against isolated Git history; no network or model calls."""
import contextlib
from datetime import date
import io
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts/release"))
import assessment
import scheduler


class SchedulerTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.original_cwd = Path.cwd()
        os.chdir(self.root)
        self.addCleanup(os.chdir, self.original_cwd)
        self.environment = patch.dict(os.environ, {"GITHUB_REF": "refs/heads/master", "GITHUB_OUTPUT": str(self.root / "outputs")})
        self.environment.start()
        self.addCleanup(self.environment.stop)
        self.git("init", "-q", "-b", "master")
        self.git("config", "user.name", "Fixture")
        self.git("config", "user.email", "fixture@example.invalid")
        self.git("config", "commit.gpgsign", "false")
        (self.root / "scripts").mkdir()
        shutil.copy(ROOT / "scripts/resolve-release-tags.sh", self.root / "scripts")
        (self.root / "scripts/release").mkdir()
        shutil.copy(ROOT / "scripts/release/release_helpers.sh", self.root / "scripts/release")
        (self.root / ".github/workflows").mkdir(parents=True)
        shutil.copy(ROOT / ".github/workflows/semver-rules-override.txt", self.root / ".github/workflows")
        (self.root / "ta4j-core/src/main/java").mkdir(parents=True)
        self.source_file = self.root / "ta4j-core/src/main/java/Source.java"
        self.source_file.write_text("class Source {}\n")
        self.pom("0.29.0")
        self.commit()
        self.release = self.git("rev-parse", "HEAD")
        self.git("-c", "tag.gpgSign=false", "tag", "-a", "-m", "fixture release", "0.29.0")
        self.pom("0.29.1-SNAPSHOT")
        self.source_file.write_text("class Source { public int value() { return 1; } }\n")
        self.commit()
        self.source = self.git("rev-parse", "HEAD")
        self.directory = self.root / "release-evidence"
        self.directory.mkdir()
        self.state = {"sourceSha": self.source, "defaultSha": self.source, "defaultBranch": "master",
                      "ci": {"runId": 7, "runAttempt": 1, "sourceSha": self.source, "status": "completed", "conclusion": "success"},
                      "refs": {}, "openReleasePullRequests": []}

    def git(self, *args):
        return subprocess.check_output(["git", *args], text=True, stderr=subprocess.PIPE).strip()

    def commit(self):
        self.git("add", ".")
        self.git("commit", "-qm", "fixture")

    def pom(self, version):
        (self.root / "pom.xml").write_text(f'<project xmlns="http://maven.apache.org/POM/4.0.0"><version>{version}</version></project>\n')

    def context(self, **kwargs):
        assessment.write(self.directory / "repository-state.json", self.state)
        with contextlib.redirect_stdout(io.StringIO()):
            scheduler.context(self.directory, kwargs.get("event", "workflow_dispatch"), kwargs.get("dry_run", "true"),
                              kwargs.get("mode", "full"), kwargs.get("run_date", date(2026, 11, 17)))
        return assessment.read(self.directory / "context.json")

    def frozen(self):
        value = self.context()
        (self.directory / "release-dossier.md").write_text("Changed production API with supporting tests.\n")
        assessment.package(self.directory, self.source)
        return value

    def plan(self, recommendation, status="success"):
        with contextlib.redirect_stdout(io.StringIO()):
            scheduler.plan(self.directory, recommendation, status)
        return assessment.read(self.directory / "release-plan.json")

    def test_biweekly_cadence_and_manual_dry_run_default(self):
        for day, due in ((date(2026, 6, 29), True), (date(2026, 7, 6), False), (date(2026, 7, 13), True), (date(2026, 6, 15), False)):
            with self.subTest(day=day):
                result = scheduler.cadence("schedule", "true", day)
                self.assertEqual(due, result["due"])
                self.assertFalse(result["dryRun"])
        self.assertEqual(True, scheduler.cadence("workflow_dispatch", "", date(2026, 7, 6))["dryRun"])
        self.assertEqual(False, scheduler.cadence("workflow_dispatch", "false", date(2026, 7, 6))["dryRun"])
        for invalid in ("yes", "0", "false\nshould_release=true"):
            with self.assertRaises(ValueError):
                scheduler.cadence("workflow_dispatch", invalid, date(2026, 7, 6))

    def test_eligible_context_keeps_release_recency_and_policy(self):
        value = self.context()
        self.assertEqual({"eligible": True, "blockers": []}, value["preconditions"])
        self.assertEqual(self.source, value["sourceSha"])
        self.assertEqual("0.29.0", value["lastTag"])
        self.assertRegex(value["lastReleaseDate"], r"^\d{4}-\d{2}-\d{2}$")
        self.assertEqual(2, value["metrics"]["binaryChangedFiles"])
        self.assertIn("missing_evidence", value["policy"])

    def test_exact_candidate_ci_is_required(self):
        for ci in (None, {"sourceSha": self.source, "status": "completed", "conclusion": "failure"},
                   {"sourceSha": self.source, "status": "in_progress", "conclusion": None},
                   {"sourceSha": self.release, "status": "completed", "conclusion": "success"}):
            with self.subTest(ci=ci):
                self.state["ci"] = ci
                self.assertIn("candidate_ci_not_successful", self.context()["preconditions"]["blockers"])

    def test_source_movement_skip_and_off_cadence_stop_before_model(self):
        self.state["defaultSha"] = self.release
        value = self.context(mode="skip", event="schedule", run_date=date(2026, 7, 6))
        self.assertEqual(False, value["preconditions"]["eligible"])
        self.assertTrue({"source_is_not_current_default_branch", "assessment_disabled_by_input", "outside_biweekly_window"}
                        .issubset(value["preconditions"]["blockers"]))
        self.state["sourceSha"] = self.release
        with self.assertRaises(ValueError):
            self.context()

    def test_active_and_incomplete_release_state_blocks(self):
        for refs in ({"refs/heads/release-freeze/prepare-8": self.source},
                     {"refs/heads/release-pending/0.29.0": self.source},
                     {"refs/heads/release-publishing/0.29.0": self.release}):
            with self.subTest(refs=refs):
                self.state["refs"] = refs
                self.assertFalse(self.context()["preconditions"]["eligible"])
        self.state["refs"] = {"refs/heads/release-pending/0.29.0": self.source,
                              "refs/heads/release-publishing/0.29.0": self.release,
                              "refs/heads/release-published/0.29.0": self.release}
        self.assertTrue(self.context()["preconditions"]["eligible"])
        self.state["openReleasePullRequests"] = [42]
        self.assertIn("release_pull_request_open", self.context()["preconditions"]["blockers"])

    def test_non_snapshot_and_unsupported_versions_stop(self):
        for version, expected in (("0.29.1", "default_branch_is_not_snapshot"),
                                  ("0.28.0-SNAPSHOT", "pom_version_behind_last_release"),
                                  ("1.0.0-SNAPSHOT", "major_release_requires_human_review"),
                                  ("0.30.0-RC1-SNAPSHOT", "unsupported_version")):
            with self.subTest(version=version):
                self.pom(version)
                self.assertIn(expected, self.context()["preconditions"]["blockers"])

    def test_monotonic_patch_minor_and_existing_tag_rejection(self):
        self.frozen()
        self.assertEqual("0.29.1", self.plan("release_patch")["version"])
        self.assertEqual("0.30.0", self.plan("release_minor")["version"])
        self.git("tag", "v0.30.0")
        with self.assertRaisesRegex(ValueError, "already exists"):
            self.plan("release_minor")

    def test_failed_action_with_plausible_output_cannot_release(self):
        self.frozen()
        for status in ("failure", "cancelled", "skipped", ""):
            with self.subTest(status=status):
                value = self.plan("release_minor", status)
                self.assertFalse(value["shouldRelease"])
                self.assertEqual("unavailable", value["recommendation"])
        for recommendation in ("defer", "insufficient_evidence", "unavailable"):
            self.assertFalse(self.plan(recommendation)["shouldRelease"])
        with self.assertRaises(ValueError):
            self.plan("release_major")

    def test_ahead_pom_cannot_silently_change_assessed_bump(self):
        value = self.frozen()
        for pom_version, recommendation in (("1.0.0-SNAPSHOT", "release_minor"), ("0.30.0-SNAPSHOT", "release_patch")):
            with self.subTest(pom_version=pom_version):
                value["pomVersion"] = pom_version
                assessment.write(self.directory / "context.json", value)
                assessment.package(self.directory, self.source)
                with self.assertRaises(ValueError):
                    self.plan(recommendation)

    def test_hash_mismatch_and_deterministic_blockers_reject_release(self):
        value = self.frozen()
        (self.directory / "release-dossier.md").write_text("tampered")
        with self.assertRaises(ValueError):
            self.plan("release_patch")
        value["preconditions"] = {"eligible": False, "blockers": ["candidate_ci_not_successful"]}
        assessment.write(self.directory / "context.json", value)
        assessment.package(self.directory, self.source)
        with self.assertRaises(ValueError):
            self.plan("release_patch")

    def test_outcome_differentiates_operational_failure_from_deferral(self):
        self.context()
        stages = {"eligibility": {"result": "success", "outputs": {"source_sha": self.source, "dry_run": "true"}},
                  "release_assessment": {"result": "failure", "outputs": {}},
                  "release_policy": {"result": "success", "outputs": {"recommendation": "unavailable"}},
                  "release_handoff": {"result": "skipped", "outputs": {}}}
        scheduler.summarize(self.directory, stages)
        self.assertEqual("failed", assessment.read(self.directory / "scheduler-outcome.json")["status"])
        stages["release_assessment"]["result"] = "success"
        stages["release_policy"]["outputs"]["recommendation"] = "defer"
        scheduler.summarize(self.directory, stages)
        self.assertEqual("defer", assessment.read(self.directory / "scheduler-outcome.json")["status"])


if __name__ == "__main__":
    unittest.main()
