from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
SCRIPT_SOURCE = REPO_ROOT / "scripts" / "prepare-release.sh"
POM_TEMPLATE = textwrap.dedent(
    """
    <project>
      <modelVersion>4.0.0</modelVersion>
      <groupId>org.ta4j</groupId>
      <artifactId>ta4j-parent</artifactId>
      <version>{version}</version>
    </project>
    """
).strip()

STUB_MVN_SCRIPT = textwrap.dedent(
    """#!/usr/bin/env bash
    set -euo pipefail

    joined=" $* "
    if [[ "$joined" == *" help:evaluate "* ]]; then
      echo "${STUB_MVN_CURRENT_VERSION:-1.0.0-SNAPSHOT}"
      exit 0
    fi

    if [[ "$joined" == *" versions:set "* ]]; then
      new_version=""
      for arg in "$@"; do
        case "$arg" in
          -DnewVersion=*) new_version="${arg#-DnewVersion=}" ;;
        esac
      done
      if [[ -z "$new_version" ]]; then
        echo "stub mvn missing -DnewVersion" >&2
        exit 1
      fi
      python3 - "$new_version" <<'PY'
import re
import sys
from pathlib import Path

pom = Path("pom.xml")
text = pom.read_text()
text = re.sub(r"<version>.*?</version>", f"<version>{sys.argv[1]}</version>", text, count=1, flags=re.DOTALL)
pom.write_text(text)
PY
      exit 0
    fi

    if [[ "$joined" == *" versions:commit "* ]]; then
      exit 0
    fi

    echo "stub mvn unsupported invocation: $*" >&2
    exit 1
    """
).strip()


class PrepareReleaseScriptTests(unittest.TestCase):
    maxDiff = None

    def _create_repo(self, pom_version: str, changelog: str, readme: str) -> tuple[Path, tempfile.TemporaryDirectory]:
        tempdir = tempfile.TemporaryDirectory()
        repo = Path(tempdir.name)
        (repo / "scripts").mkdir(parents=True)
        shutil.copy2(SCRIPT_SOURCE, repo / "scripts" / "prepare-release.sh")
        (repo / "scripts" / "prepare-release.sh").chmod(0o755)

        (repo / "pom.xml").write_text(POM_TEMPLATE.format(version=pom_version))
        (repo / "CHANGELOG.md").write_text(textwrap.dedent(changelog).lstrip())
        (repo / "README.md").write_text(textwrap.dedent(readme).lstrip())

        bin_dir = repo / "bin"
        bin_dir.mkdir()
        stub_path = bin_dir / "mvn"
        stub_path.write_text(STUB_MVN_SCRIPT)
        stub_path.chmod(0o755)

        return repo, tempdir

    def _run_script(self, repo: Path, args: list[str], *, current_version: str) -> subprocess.CompletedProcess:
        env = os.environ.copy()
        env["PATH"] = f"{repo / 'bin'}:{env['PATH']}"
        env["STUB_MVN_CURRENT_VERSION"] = current_version
        return subprocess.run(
            ["bash", "scripts/prepare-release.sh", *args],
            cwd=repo,
            env=env,
            check=True,
            text=True,
            capture_output=True,
        )

    def test_release_updates_versions_and_changelog(self):
        repo, tempdir = self._create_repo(
            pom_version="1.2.3-SNAPSHOT",
            changelog="""
            ## Unreleased

            - Added brand-new trading strategy helper.

            ## 1.2.2 (2024-01-01)
            - Previous release note.
            """,
            readme="""
            <dependency>
              <groupId>org.ta4j</groupId>
              <artifactId>ta4j-core</artifactId>
              <version>0.0.1</version>
            </dependency>

            <dependency>
              <groupId>org.ta4j</groupId>
              <artifactId>ta4j-examples</artifactId>
              <version>0.0.1</version>
            </dependency>

            The current ***snapshot version*** is `0.0.1-SNAPSHOT`.
            Use `0.0.1-SNAPSHOT` everywhere.
            """,
        )
        self.addCleanup(tempdir.cleanup)

        result = self._run_script(repo, ["release"], current_version="1.2.3-SNAPSHOT")
        self.assertIn("release_version=1.2.3", result.stdout)
        self.assertIn("next_version=1.2.4-SNAPSHOT", result.stdout)

        pom_text = (repo / "pom.xml").read_text()
        self.assertIn("<version>1.2.3</version>", pom_text)

        changelog_text = (repo / "CHANGELOG.md").read_text()
        self.assertIn("- _No changes yet._", changelog_text)
        self.assertRegex(changelog_text, r"## 1.2.3 \(\d{4}-\d{2}-\d{2}\)")
        self.assertIn("Added brand-new trading strategy helper.", changelog_text)

        readme_text = (repo / "README.md").read_text()
        self.assertEqual(readme_text.count("<version>1.2.3</version>"), 2)
        self.assertIn("The current ***snapshot version*** is `1.2.4-SNAPSHOT`.", readme_text)
        self.assertIn("Use `1.2.4-SNAPSHOT` everywhere.", readme_text)

        release_notes = repo / "target" / "release-notes.md"
        self.assertTrue(release_notes.exists(), "Release notes file was not created")
        self.assertIn("Added brand-new trading strategy helper.", release_notes.read_text())

    def test_snapshot_bumps_version_and_restores_placeholder(self):
        repo, tempdir = self._create_repo(
            pom_version="2.5.0",
            changelog="""
            ## Unreleased

            ## 2.4.0 (2024-02-01)
            - Older release entry.
            """,
            readme="README placeholder",  # README is irrelevant for snapshot mode
        )
        self.addCleanup(tempdir.cleanup)

        result = self._run_script(repo, ["snapshot"], current_version="2.5.0")
        self.assertIn("next_version=2.5.1-SNAPSHOT", result.stdout)

        pom_text = (repo / "pom.xml").read_text()
        self.assertIn("<version>2.5.1-SNAPSHOT</version>", pom_text)

        changelog_text = (repo / "CHANGELOG.md").read_text()
        self.assertIn("- _No changes yet._", changelog_text)

    
if __name__ == "__main__":
    unittest.main()
