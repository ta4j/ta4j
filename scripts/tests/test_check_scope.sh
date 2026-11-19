#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
python3 - "$root" <<'PY'
import importlib.util
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

root = Path(sys.argv.pop())
spec = importlib.util.spec_from_file_location('scope', root / 'scripts/check_scope.py')
scope = importlib.util.module_from_spec(spec)
spec.loader.exec_module(scope)

class ScopeTests(unittest.TestCase):
    def test_routes_only_relevant_lanes(self):
        cases = [(['ta4j-core/src/main/java/X.java', 'CHANGELOG.md'], (True, False)),
                 (['scripts/release/a.py', 'RELEASE_PROCESS.md'], (False, True)),
                 (['.github/workflows/test.yml'], (False, True)),
                 (['README.md', 'docs/guide.md'], (False, False)),
                 (['pom.xml'], (True, False)), (['mvnw', '.mvn/wrapper/maven-wrapper.properties'], (False, True)),
                 (['ta4j-core/src/main/resources/help.md'], (True, False)),
                 (['ta4j-core/src/test/resources/expected.rst'], (True, False)),
                 (['pom.xml', 'scripts/x.sh'], (True, True))]
        for paths, expected in cases:
            with self.subTest(paths=paths):
                self.assertEqual(scope.classify(paths), dict(zip(('maven', 'scripts'), expected)))

    def test_missing_zero_or_malformed_shas_select_both_lanes(self):
        for args in ([], ['--base', '0' * 40, '--head', 'a' * 40], ['--base', 'abc', '--head', 'def']):
            with self.subTest(args=args):
                result = subprocess.check_output(['python3', str(root / 'scripts/check_scope.py'), *args], text=True)
                self.assertIn('"maven": true', result)
                self.assertIn('"scripts": true', result)

    def test_deleted_or_renamed_java_still_requires_maven(self):
        with tempfile.TemporaryDirectory(prefix='ta4j-scope-') as temp:
            def git(*args):
                return subprocess.check_output(['git', '-C', temp, '-c', 'user.name=Fixture', '-c',
                                               'user.email=fixture@example.invalid', '-c', 'commit.gpgsign=false', *args], text=True).strip()
            git('init', '-q'); path = Path(temp)
            (path / 'X.java').write_text('source')
            git('add', '.'); git('commit', '-qm', 'base'); base = git('rev-parse', 'HEAD')
            (path / 'scripts').mkdir(); (path / 'X.java').rename(path / 'scripts/X.fixture')
            git('add', '.'); git('commit', '-qm', 'rename'); head = git('rev-parse', 'HEAD')
            result = subprocess.check_output(['python3', str(root / 'scripts/check_scope.py'), '--base', base, '--head', head], cwd=temp, text=True)
            self.assertIn('"maven": true', result); self.assertIn('"scripts": true', result)

    def test_maven_wrapper_never_runs_script_suite_implicitly(self):
        bash = (root / 'scripts/run-full-build-quiet.sh').read_text()
        ps = (root / 'scripts/run-full-build-quiet.ps1').read_text()
        self.assertNotIn('run_repository_preflight()', bash)
        self.assertNotIn('$defaultGate -or $preflightOnly', ps)
        workflow = (root / '.github/workflows/test.yml').read_text()
        self.assertIn('name: Build and Verify', workflow)
        self.assertIn('needs: [scope, maven, scripts]', workflow)
        self.assertIn('if: always()', workflow)
        self.assertIn("if: needs.scope.outputs.scripts == 'true'", workflow)
        self.assertIn("if: needs.scope.outputs.maven == 'true'", workflow)
        self.assertIn('bash scripts/run-script-checks.sh', workflow)
        self.assertIn('TESTED_MERGE:', workflow)

unittest.main(verbosity=2)
PY
