#!/usr/bin/env python3
"""Policy check: the release artifact manifest gate
(scripts/release/release_helpers.sh artifact-manifest --strict, invoked by
.github/workflows/publish-release.yml after `-Pproduction-release package`)
must accept the artifacts the reactor produces. The new ta4j-cli module is part
of the reactor (root pom <modules>) and therefore produces
ta4j-cli/target/ta4j-cli-<version>[-sources|-javadoc].jar during the release
build, but the manifest's expected list only covers ta4j-core and
ta4j-examples, so --strict fails the release with "Unexpected target jars".

The check simulates the release build tree with fake jars and runs the exact
helper invocation used by publish-release.yml.

Run from the repository root:
  /opt/homebrew/opt/python@3.14/bin/python3.14 -m unittest discover -s scripts/tests -p 'test_review_release_manifest.py' -v
"""

import pathlib
import shutil
import subprocess
import tempfile
import unittest

REPOSITORY_ROOT = pathlib.Path(__file__).resolve().parents[2]
HELPER = REPOSITORY_ROOT / "scripts/release/release_helpers.sh"

VERSION = "0.24.1"

CORE_JARS = (
    "ta4j-core/target/ta4j-core-%s.jar",
    "ta4j-core/target/ta4j-core-%s-sources.jar",
    "ta4j-core/target/ta4j-core-%s-javadoc.jar",
    "ta4j-core/target/ta4j-core-%s-tests.jar",
)
EXAMPLES_JARS = (
    "ta4j-examples/target/ta4j-examples-%s.jar",
    "ta4j-examples/target/ta4j-examples-%s-sources.jar",
    "ta4j-examples/target/ta4j-examples-%s-javadoc.jar",
)
CLI_JARS = (
    "ta4j-cli/target/ta4j-cli-%s.jar",
    "ta4j-cli/target/ta4j-cli-%s-sources.jar",
    "ta4j-cli/target/ta4j-cli-%s-javadoc.jar",
)


def _make_tree(root, relative_jars):
    for template in relative_jars:
        path = root / (template % VERSION)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(b"fake jar for manifest simulation")


def _run_manifest(tree):
    return subprocess.run(
        ["bash", str(HELPER), "artifact-manifest", "--version", VERSION, "--output", "manifest.txt", "--strict"],
        cwd=str(tree),
        capture_output=True,
        text=True,
    )


class ReleaseArtifactManifestTest(unittest.TestCase):
    def test_manifest_accepts_ta4j_cli_reactor_artifacts(self):
        self.assertTrue(HELPER.is_file(), "release helper must exist")
        with tempfile.TemporaryDirectory(prefix="ta4j-manifest-sim.") as tmp:
            tree = pathlib.Path(tmp)
            _make_tree(tree, CORE_JARS + EXAMPLES_JARS + CLI_JARS)
            result = _run_manifest(tree)
            self.assertEqual(
                result.returncode, 0,
                "artifact-manifest --strict rejected the reactor artifacts:\n%s\n%s"
                % (result.stdout, result.stderr),
            )

    def test_manifest_still_detects_missing_core_artifact(self):
        # Negative control: the gate must keep failing when a required artifact
        # is missing; the check is not satisfied by relaxing the manifest.
        with tempfile.TemporaryDirectory(prefix="ta4j-manifest-sim.") as tmp:
            tree = pathlib.Path(tmp)
            missing = ("ta4j-core/target/ta4j-core-%s-tests.jar",)
            _make_tree(tree, CORE_JARS + EXAMPLES_JARS + CLI_JARS)
            (tree / (missing[0] % VERSION)).unlink()
            result = _run_manifest(tree)
            self.assertNotEqual(result.returncode, 0, "missing core tests jar must still fail the manifest gate")


if __name__ == "__main__":
    unittest.main()
