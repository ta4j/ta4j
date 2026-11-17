"""Offline evidence, authority and official SDK contracts; no live inference."""
import copy
from contextlib import redirect_stdout
import importlib.util
from io import StringIO
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import types
import unittest
from unittest.mock import MagicMock, patch

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("assessment", ROOT / "scripts/release/assessment.py")
assessment = importlib.util.module_from_spec(spec)
spec.loader.exec_module(assessment)
SHA = "a" * 40


def decision(kind="release", bump="patch"):
    return dict(decision=kind, bump=bump, source_sha=SHA, reason="A supported reason",
                evidence=["core/Fix.java fixes the reported regression"], blocking_risks=[], missing_evidence=[])


def choice_body(choice="release_minor"):
    return {"model": "gpt-6-luna", "answers": [{"name": "release_recommendation", "type": "choice",
            "choice": choice, "confidence": 0.7, "probabilities": [
                {"value": name, "probability": 0.7 if name == choice else 0.1}
                for name in assessment.RECOMMENDATIONS]}],
            "usage": {"input_tokens": 100, "output_tokens": 4, "total_tokens": 104}}


class FakeAPIError(Exception):
    pass


class FakeConnectionError(FakeAPIError):
    pass


class FakeStatusError(FakeAPIError):
    def __init__(self, status_code):
        super().__init__("sensitive provider body and secret key")
        self.status_code = status_code


class AssessmentTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.context = {"schemaVersion": 2, "sourceSha": SHA, "lastTag": "0.22.2",
                        "policy": "Only supported compatible changes justify patch or minor; never major.",
                        "cadence": {"due": True}, "metrics": {"binaryChangedFiles": 2},
                        "preconditions": {"eligible": True, "blockers": []}}
        assessment.write(self.root / "context.json", self.context)
        (self.root / "release-dossier.md").write_text("complete frozen diff", encoding="utf-8")
        assessment.package(self.root, SHA)
        self.output = StringIO()
        self.addCleanup(patch.stopall)
        patch.dict(os.environ, {"GITHUB_OUTPUT": str(self.root / "outputs")}).start()

    def report(self, **kwargs):
        with redirect_stdout(self.output):
            return assessment.report(self.root, SHA, **kwargs)

    def test_manifest_binds_exact_context_and_dossier(self):
        assessment.prepare(self.root, SHA)
        manifest = assessment.verify(self.root, SHA)
        self.assertEqual(set(assessment.EVIDENCE_FILES), set(manifest["files"]))
        prompt = (self.root / "codex-prompt.md").read_text()
        self.assertIn(json.dumps(self.context), prompt)
        self.assertIn(SHA, prompt)
        self.assertIn("./candidate", prompt)
        self.assertEqual("unavailable", assessment.read(self.root / "luna-result.json")["status"])

    def test_stale_or_modified_evidence_fails_closed(self):
        for file in assessment.EVIDENCE_FILES:
            with self.subTest(file=file):
                original = (self.root / file).read_bytes()
                (self.root / file).write_bytes(original + b" ")
                with self.assertRaisesRegex(ValueError, "hash_mismatch"):
                    assessment.prepare(self.root, SHA)
                (self.root / file).write_bytes(original)
        with self.assertRaises(ValueError):
            assessment.prepare(self.root, "b" * 40)
        self.context["sourceSha"] = "b" * 40
        assessment.write(self.root / "context.json", self.context)
        with self.assertRaisesRegex(ValueError, "context_source_mismatch"):
            assessment.package(self.root, SHA)

    def test_manifest_cannot_select_arbitrary_paths(self):
        manifest = assessment.read(self.root / "release-assessment-manifest.json")
        manifest["files"] = {"../../secret": "hash"}
        assessment.write(self.root / "release-assessment-manifest.json", manifest)
        with self.assertRaisesRegex(ValueError, "invalid_manifest_file_set"):
            assessment.verify(self.root, SHA)

    def test_preconditions_are_typed_and_consistent(self):
        for value in ({"eligible": "true", "blockers": []}, {"eligible": True, "blockers": ["failed CI"]},
                      {"eligible": True}, {"eligible": False, "blockers": "error"}):
            with self.subTest(value=value), self.assertRaises(ValueError):
                context = dict(self.context, preconditions=value)
                assessment.validate_context(context, SHA)

    def test_release_validation_rejects_partial_or_contradictory_decisions(self):
        mutations = [("source_sha", "b" * 40), ("decision", True), ("bump", "major"), ("bump", "none"),
                     ("reason", ""), ("reason", "x" * 1201), ("evidence", []), ("evidence", "fact"),
                     ("evidence", [""]), ("evidence", ["x"] * 13), ("blocking_risks", ["failed tests"]),
                     ("missing_evidence", ["compatibility check"]), ("confidence", 0.99)]
        for field, value in mutations:
            candidate = decision()
            candidate[field] = value
            with self.subTest(field=field, value=value), self.assertRaises(ValueError):
                assessment.validate_decision(candidate, SHA)
        self.assertEqual(decision(), assessment.validate_decision(decision(), SHA))
        with self.assertRaises(ValueError):
            assessment.validate_decision({"refusal": "cannot assess"}, SHA)
        with self.assertRaises(ValueError):
            assessment.validate_decision(decision("defer"), SHA)
        with self.assertRaises(ValueError):
            assessment.validate_decision(decision("insufficient_evidence", "none"), SHA)

    def test_duplicate_nonfinite_and_oversize_json_is_invalid(self):
        path = self.root / "invalid.json"
        for text in ('{"bump":"none","bump":"patch"}', '{"value":NaN}', '{"value":Infinity}'):
            path.write_text(text)
            with self.subTest(text=text), self.assertRaises(ValueError):
                assessment.read(path)
        path.write_text('"' + "x" * 200 + '"')
        with self.assertRaises(ValueError):
            assessment.read(path, limit=100)

    def test_valid_release_and_nonrelease_outputs_are_distinct(self):
        for candidate, expected in ((decision(), "release_patch"), (decision(bump="minor"), "release_minor"),
                                    (decision("defer", "none"), "defer"),
                                    (dict(decision("insufficient_evidence", "none"), missing_evidence=["compatibility check"]), "insufficient_evidence")):
            with self.subTest(expected=expected):
                assessment.write(self.root / "codex-decision.json", candidate)
                result = self.report()
                self.assertEqual(expected, result["recommendation"])
                self.assertEqual(expected.startswith("release_"), result["shouldRelease"])
        (self.root / "codex-decision.json").unlink()
        self.assertEqual("unavailable", self.report()["recommendation"])

    def test_failed_provider_cannot_authorize_with_leftover_valid_json(self):
        assessment.write(self.root / "codex-decision.json", decision())
        for status in ("failure", "cancelled", "skipped", ""):
            with self.subTest(status=status):
                result = self.report(codex_status=status)
                self.assertFalse(result["shouldRelease"])
                self.assertEqual("unavailable", result["recommendation"])

    def test_deterministic_ineligibility_cannot_be_overridden(self):
        self.context["preconditions"] = {"eligible": False, "blockers": ["CI not successful"]}
        assessment.write(self.root / "context.json", self.context)
        assessment.package(self.root, SHA)
        assessment.write(self.root / "codex-decision.json", decision())
        result = self.report()
        self.assertFalse(result["shouldRelease"])
        self.assertEqual("deterministic_preconditions_failed", result["error"])

    def test_missing_context_still_produces_an_unavailable_result(self):
        (self.root / "context.json").unlink()
        result = self.report()
        self.assertEqual("unavailable", result["recommendation"])
        self.assertTrue((self.root / "assessment-result.json").exists())
        self.assertIn("should_release=false", (self.root / "outputs").read_text())

    def test_optional_luna_never_authorizes_or_vetoes(self):
        assessment.write(self.root / "codex-decision.json", decision())
        luna = {"schemaVersion": 2, "provider": "luna_decisions", "sourceSha": SHA,
                "status": "completed", "recommendation": "defer"}
        assessment.write(self.root / "luna-result.json", luna)
        result = self.report(evaluate_luna=True, luna_status="success")
        self.assertTrue(result["shouldRelease"])
        self.assertFalse(result["allAgree"])
        for status in ("failure", "skipped"):
            self.assertTrue(self.report(evaluate_luna=True, luna_status=status)["shouldRelease"])
        (self.root / "luna-result.json").unlink()
        self.assertTrue(self.report(evaluate_luna=True, luna_status="success")["shouldRelease"])
        self.assertIsNone(self.report()["allAgree"])
        (self.root / "codex-decision.json").unlink()
        luna["recommendation"] = "release_patch"
        assessment.write(self.root / "luna-result.json", luna)
        self.assertFalse(self.report(evaluate_luna=True, luna_status="success")["shouldRelease"])

    def test_summary_and_outputs_do_not_interpret_model_prose(self):
        value = decision()
        value["reason"] = "</table>\n::error::secret\n| unexpected |"
        value["evidence"] = ["<script>alert(1)</script>"]
        assessment.write(self.root / "codex-decision.json", value)
        self.report()
        for text in ((self.root / "outputs").read_text(), self.output.getvalue()):
            self.assertNotIn("<script>", text)
            self.assertNotIn("::error::", text)
            self.assertNotIn("secret", text)
        summary = (self.root / "assessment-summary.md").read_text()
        self.assertIn("&lt;script&gt;", summary)
        self.assertNotIn("<script>", summary)
        self.assertIn("<pre>&lt;/table&gt;", summary)
        self.assertEqual(value, assessment.read(self.root / "assessment-result.json")["decision"])

    def sdk(self, body=None, error=None):
        client = MagicMock()
        client.__enter__.return_value = client
        if error:
            client.decisions.create.side_effect = error
        else:
            client.decisions.create.return_value.model_dump.return_value = body or choice_body()
        factory = MagicMock(return_value=client)
        module = types.SimpleNamespace(OpenAI=factory, APIError=FakeAPIError,
                                       APIConnectionError=FakeConnectionError, APIStatusError=FakeStatusError)
        patch.dict(sys.modules, {"openai": module}).start()
        patch.dict(os.environ, {"OPENAI_API_KEY": "fixture-key"}).start()
        return factory, client

    def test_official_sdk_receives_frozen_typed_evidence_and_bounded_retries(self):
        factory, client = self.sdk()
        with redirect_stdout(self.output):
            result = assessment.decisions(self.root, SHA)
        factory.assert_called_once_with(timeout=60.0, max_retries=2)
        request = client.decisions.create.call_args.kwargs
        self.assertEqual("gpt-6-luna", request["model"])
        typed = request["input"][1]["content"][0]
        self.assertEqual("input_json", typed["type"])
        self.assertEqual(self.context, typed["data"]["context"])
        self.assertEqual("complete frozen diff", typed["data"]["releaseDossier"])
        self.assertNotIn("reasoning", request)
        self.assertEqual("completed", result["status"])
        self.assertEqual("release_minor", result["recommendation"])
        self.assertEqual(104, result["usage"]["total_tokens"])

    def test_sdk_errors_are_redacted(self):
        for error, expected in ((FakeStatusError(429), "http_429"), (FakeConnectionError("secret body"), "transport_error"),
                                (FakeAPIError("secret body"), "provider_error")):
            _, client = self.sdk(error=error)
            with self.subTest(expected=expected), redirect_stdout(self.output):
                result = assessment.decisions(self.root, SHA)
            self.assertEqual(expected, result["error"])
            self.assertEqual(1, client.decisions.create.call_count)
            self.assertNotIn("secret", (self.root / "luna-result.json").read_text())
            self.assertNotIn("fixture-key", (self.root / "luna-result.json").read_text())

    def test_refusal_and_malformed_luna_results_are_not_votes(self):
        body = {"model": "gpt-6-luna", "answers": [{"type": "refusal", "name": "release_recommendation"}]}
        self.assertEqual({"status": "refused"}, assessment.parse_luna(body))
        invalid = []
        for field, value in (("choice", True), ("confidence", True), ("confidence", float("nan")),
                             ("confidence", 1.1), ("probabilities", []), ("name", "another_question")):
            body = choice_body()
            body["answers"][0][field] = value
            invalid.append(body)
        body = choice_body()
        body["answers"][0]["probabilities"][0]["probability"] = 0.7
        invalid += [body, {"model": "gpt-6-luna", "answers": []}, dict(choice_body(), model="wrong-model")]
        for body in invalid:
            with self.subTest(body=body), self.assertRaises(ValueError):
                assessment.parse_luna(body)
        self.sdk(body={"model": "gpt-6-luna", "answers": []})
        with redirect_stdout(self.output):
            self.assertEqual("invalid_response", assessment.decisions(self.root, SHA)["error"])

    def test_oversize_or_corrupt_evidence_is_rejected_before_sdk_call(self):
        _, client = self.sdk()
        (self.root / "release-dossier.md").write_text("x" * assessment.MAX_TYPED_BYTES)
        assessment.package(self.root, SHA)
        with redirect_stdout(self.output):
            self.assertEqual("evidence_too_large", assessment.decisions(self.root, SHA)["error"])
        client.decisions.create.assert_not_called()
        (self.root / "release-dossier.md").write_text("tampered")
        with redirect_stdout(self.output):
            self.assertEqual("invalid_or_unavailable_evidence", assessment.decisions(self.root, SHA)["error"])
        client.decisions.create.assert_not_called()
        with self.assertRaisesRegex(ValueError, "too_many_values"):
            assessment.typed_input({"many": [1] * 10001}, "")
        nested = {}
        for _ in range(33):
            nested = {"nested": nested}
        with self.assertRaisesRegex(ValueError, "too_deep"):
            assessment.typed_input(nested, "")

    def test_missing_key_is_explicit_and_sdk_never_starts(self):
        factory, _ = self.sdk()
        with patch.dict(os.environ, {"OPENAI_API_KEY": ""}), redirect_stdout(self.output):
            result = assessment.decisions(self.root, SHA)
        self.assertEqual("missing_api_key", result["error"])
        factory.assert_not_called()

    def repository_fixture(self):
        candidate = self.root / "candidate"
        candidate.mkdir()
        commands = [["init", "--quiet"], ["config", "user.name", "Fixture"],
                    ["config", "user.email", "fixture@example.invalid"], ["config", "commit.gpgsign", "false"],
                    ["config", "core.hooksPath", "/dev/null"]]
        for command in commands:
            subprocess.run(["git", "-C", str(candidate), *command], check=True, capture_output=True)
        source = candidate / "source.txt"
        source.write_text("baseline content")
        subprocess.run(["git", "-C", str(candidate), "add", "source.txt"], check=True)
        subprocess.run(["git", "-C", str(candidate), "commit", "--quiet", "-m", "baseline"], check=True)
        subprocess.run(["git", "-C", str(candidate), "tag", "v1.0.0"], check=True)
        baseline = subprocess.check_output(["git", "-C", str(candidate), "rev-parse", "HEAD"], text=True).strip()
        source.write_text("candidate content")
        subprocess.run(["git", "-C", str(candidate), "commit", "--quiet", "-am", "candidate"], check=True)
        sha = subprocess.check_output(["git", "-C", str(candidate), "rev-parse", "HEAD"], text=True).strip()
        self.context.update(sourceSha=sha, lastTag="v1.0.0", lastTagSha=baseline)
        assessment.write(self.root / "context.json", self.context)
        assessment.package(self.root, sha)
        assessment.prepare(self.root, sha)
        return candidate, sha

    def test_runtime_archives_exact_candidate_and_baseline_without_git_configuration(self):
        candidate, sha = self.repository_fixture()
        (candidate / "source.txt").write_text("uncommitted corruption must not reach the model")
        env = {"ASSESSMENT_RUNNER_ENVIRONMENT": "github-hosted", "GITHUB_RUN_ID": "42",
               "GITHUB_ENV": str(self.root / "env"), "GITHUB_PATH": str(self.root / "path")}
        with patch.dict(os.environ, env), redirect_stdout(self.output):
            assessment.runner_setup(self.root, candidate, sha)
            state = assessment.read(self.root / "runner-session.json")
            session = Path(state["session"])
            try:
                self.assertEqual("candidate content", (session / "candidate/source.txt").read_text())
                self.assertEqual("baseline content", (session / "baseline/source.txt").read_text())
                self.assertFalse((session / "candidate/.git").exists())
                self.assertIn("safety_strategy=drop-sudo", (self.root / "outputs").read_text())
                self.assertIn(self.context["lastTagSha"], (session / "evidence/codex-prompt.md").read_text())
            finally:
                assessment.runner_cleanup(self.root)
            self.assertFalse(session.exists())
            self.assertTrue(candidate.exists())

    def test_baseline_tag_movement_is_rejected_before_provider_start(self):
        candidate, sha = self.repository_fixture()
        subprocess.run(["git", "-C", str(candidate), "tag", "--force", "v1.0.0", sha], check=True, capture_output=True)
        with patch.dict(os.environ, {"ASSESSMENT_RUNNER_ENVIRONMENT": "github-hosted", "GITHUB_RUN_ID": "42"}):
            with self.assertRaisesRegex(ValueError, "baseline_source_mismatch"):
                assessment.runner_setup(self.root, candidate, sha)
        self.assertFalse((self.root / "runner-session.json").exists())

    def test_selfhosted_requires_explicit_service_user_and_source_match(self):
        with patch.dict(os.environ, {"ASSESSMENT_RUNNER_ENVIRONMENT": "self-hosted"}), patch.object(subprocess, "check_output", return_value=SHA):
            with self.assertRaisesRegex(ValueError, "requires_preprovisioned"):
                assessment.runner_setup(self.root, self.root, SHA)
        with patch.object(subprocess, "check_output", return_value="b" * 40):
            with self.assertRaisesRegex(ValueError, "candidate_checkout_mismatch"):
                assessment.runner_setup(self.root, self.root, SHA)

    def test_workflow_has_independent_shadow_failure_boundary_and_policy_stage(self):
        text = (ROOT / ".github/workflows/reusable-release-assessment.yml").read_text()
        luna = text.split("  luna:\n", 1)[1].split("  policy:\n", 1)[0]
        self.assertIn("continue-on-error: true", luna)
        self.assertIn("if: ${{ inputs.evaluate_luna }}", luna)
        self.assertIn("needs: [context, codex, luna]", text)
        self.assertIn("CODEX_STATUS: ${{ needs.codex.result }}", text)
        self.assertIn("permission-profile: ':read-only'", text)
        self.assertNotIn("safety-strategy: read-only", text)
        self.assertIn("persist-credentials: false", text)
        self.assertIn("codex-version: '0.169.0'", text)
        self.assertIn("output-schema-file:", text)
        self.assertIn("steps.policy.outputs.assessment_status == 'unavailable'", text)
        self.assertIn('python3 -m venv "$sdk_directory"', luna)


if __name__ == "__main__":
    unittest.main()
