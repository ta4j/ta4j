#!/usr/bin/env python3
"""Offline release lifecycle tests. All remotes and publish destinations are fixtures."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import textwrap
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[2]
HELPER = ROOT / 'scripts/release/release_candidate.py'


class ReleaseLifecycleTest(unittest.TestCase):
    def run_command(self, *command, env=None, ok=True):
        result = subprocess.run(command, cwd=self.repo, env=env or self.env,
                                text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if ok:
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        return result

    def git(self, *args):
        return self.run_command('git', *args).stdout.strip()

    def helper(self, command, *args, ok=True, env=None):
        return self.run_command('python3', str(HELPER), command, *args, env=env, ok=ok)

    def write_poms(self, version):
        for module in ('.', 'ta4j-core', 'ta4j-examples'):
            path = self.repo / module / 'pom.xml'
            path.parent.mkdir(exist_ok=True)
            artifact = 'ta4j-parent' if module == '.' else module
            path.write_text(f'<project xmlns="http://maven.apache.org/POM/4.0.0"><groupId>org.ta4j</groupId>'
                            f'<artifactId>{artifact}</artifactId><version>{version}</version></project>')

    def commit(self, message):
        self.git('add', '.')
        self.git('commit', '-qm', message)
        return self.git('rev-parse', 'HEAD')

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='ta4j-release-')
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name) / 'work'
        self.repo.mkdir()
        self.remote = Path(self.temp.name) / 'remote.git'
        self.env = {key: value for key, value in os.environ.items() if not key.startswith(('GIT_', 'GITHUB_'))}
        self.env.update(GIT_CONFIG_GLOBAL='/dev/null', GIT_CONFIG_NOSYSTEM='1', BASH_ENV='/dev/null',
                        GIT_AUTHOR_NAME='Fixture', GIT_AUTHOR_EMAIL='fixture@example.invalid',
                        GIT_COMMITTER_NAME='Fixture', GIT_COMMITTER_EMAIL='fixture@example.invalid')
        self.git('init', '-q', '-b', 'master')
        self.git('init', '-q', '--bare', str(self.remote))
        self.git('remote', 'add', 'origin', str(self.remote))
        self.write_poms('1.2.3-SNAPSHOT')
        self.base = self.commit('base')
        self.git('push', '-q', 'origin', 'HEAD:refs/heads/master')
        self.write_poms('1.2.3')
        (self.repo / 'release').mkdir()
        (self.repo / 'release/1.2.3.md').write_text('Release 1.2.3\n')
        self.release = self.commit('Release 1.2.3')
        self.write_poms('1.2.4-SNAPSHOT')
        self.snapshot = self.commit('Start 1.2.4-SNAPSHOT')
        self.env.update(BASE_SHA=self.base, RELEASE_SHA=self.release, SNAPSHOT_SHA=self.snapshot,
                        RELEASE_VERSION='1.2.3', NEXT_VERSION='1.2.4-SNAPSHOT', CANDIDATE_BRANCH='release/1.2.3',
                        GITHUB_REPOSITORY='fixture/ta4j', GITHUB_WORKFLOW_REF='fixture/ta4j/.github/workflows/prepare-release.yml@refs/heads/master',
                        GITHUB_WORKFLOW_SHA=self.base, GITHUB_RUN_ID='123', GITHUB_RUN_ATTEMPT='1', DEFAULT_BRANCH='master')

    def ref(self, name):
        result = self.run_command('git', '--git-dir', str(self.remote), 'rev-parse', '--verify', name, ok=False)
        return result.stdout.strip() if result.returncode == 0 else None

    def stage_and_verify(self):
        self.helper('stage')
        for kind, sha in (('release', self.release), ('snapshot', self.snapshot)):
            self.git('checkout', '-q', '--detach', sha)
            path = self.repo / kind / 'receipt.json'
            path.parent.mkdir(exist_ok=True)
            evidence = Path(self.temp.name) / f'{kind}.log'
            evidence.write_text('Tests run: 42, Failures: 0, Errors: 0, Skipped: 0\n')
            self.helper('receipt', '--kind', kind, '--evidence', str(evidence), '--output', str(path))
            self.env[f'{kind.upper()}_RECEIPT_SHA256'] = hashlib.sha256(path.read_bytes()).hexdigest()
        self.git('checkout', '-q', '--detach', self.base)

    def test_validation_failure_never_changes_master_tags_or_publication(self):
        self.helper('stage')
        self.assertNotEqual(self.helper('promote', ok=False).returncode, 0)
        self.assertEqual(self.ref('refs/heads/master'), self.base)
        self.assertIsNone(self.ref('refs/tags/1.2.3'))
        self.assertIsNone(self.ref('refs/heads/release-publishing/1.2.3'))
        self.assertNotEqual(self.helper('pending', ok=False).returncode, 0)

    def test_success_consumes_candidate_promotes_s_but_publishes_r(self):
        self.stage_and_verify()
        self.helper('promote')
        self.assertEqual(self.ref('refs/heads/master'), self.snapshot)
        self.assertIsNone(self.ref('refs/heads/release/1.2.3'))
        self.assertIsNone(self.ref('refs/tags/1.2.3'))
        self.helper('publication-start')
        self.assertEqual(self.ref('refs/heads/release-publishing/1.2.3'), self.release)

    def test_master_advancing_invalidates_candidate(self):
        self.stage_and_verify()
        self.git('push', '-q', 'origin', f'{self.release}:refs/heads/master')
        self.assertNotEqual(self.helper('promote', ok=False).returncode, 0)
        self.assertEqual(self.ref('refs/heads/master'), self.release)

    def test_candidate_mutation_invalidates_receipts(self):
        self.stage_and_verify()
        self.git('push', '-q', '--force', 'origin', f'{self.release}:refs/heads/release/1.2.3')
        self.assertNotEqual(self.helper('promote', ok=False).returncode, 0)
        self.assertEqual(self.ref('refs/heads/master'), self.base)

    def test_stale_receipt_identity_and_digest_fail_closed(self):
        self.stage_and_verify()
        for field, value in (('GITHUB_RUN_ID', '124'), ('GITHUB_RUN_ATTEMPT', '2'),
                             ('GITHUB_REPOSITORY', 'other/repo'), ('NEXT_VERSION', '1.3.0-SNAPSHOT'),
                             ('GITHUB_WORKFLOW_REF', 'other/workflow@ref')):
            env = dict(self.env, **{field: value})
            self.assertNotEqual(self.helper('promote', env=env, ok=False).returncode, 0, field)
        (self.repo / 'release/receipt.json').write_text('{}')
        self.assertNotEqual(self.helper('promote', ok=False).returncode, 0)
        self.assertEqual(self.ref('refs/heads/master'), self.base)

    def test_forged_ancestry_fails_before_stage(self):
        self.env['BASE_SHA'] = self.release
        self.env['GITHUB_WORKFLOW_SHA'] = self.release
        self.assertNotEqual(self.helper('stage', ok=False).returncode, 0)
        self.assertIsNone(self.ref('refs/heads/release/1.2.3'))

    def test_tracked_changes_do_not_get_receipts(self):
        (self.repo / 'pom.xml').write_text('modified')
        self.assertNotEqual(self.helper('receipt', '--kind', 'snapshot', '--evidence', 'pom.xml', ok=False).returncode, 0)
        self.assertFalse((self.repo / 'receipt.json').exists())

    def test_stage_cannot_replace_existing_candidate(self):
        self.helper('stage')
        self.assertNotEqual(self.helper('stage', ok=False).returncode, 0)
        self.assertEqual(self.ref('refs/heads/release/1.2.3'), self.snapshot)

    def test_concurrent_promotions_only_one_succeeds(self):
        self.stage_and_verify()
        processes = [subprocess.Popen(['python3', str(HELPER), 'promote'], cwd=self.repo, env=self.env,
                                      stdout=subprocess.PIPE, stderr=subprocess.PIPE) for _ in range(2)]
        for process in processes:
            process.communicate(timeout=20)
        self.assertEqual(sorted(p.returncode for p in processes), [0, 1])
        self.assertEqual(self.ref('refs/heads/master'), self.snapshot)

    def test_atomic_remote_races_reject_all_updates(self):
        self.stage_and_verify()
        real_git = self.run_command('which', 'git').stdout.strip()
        wrapper = Path(self.temp.name) / 'bin'
        wrapper.mkdir()
        (wrapper / 'git').write_text('#!/bin/sh\nif [ "$1" = push ]; then\n'
                                    '  "$REAL_GIT" --git-dir "$FIXTURE_REMOTE" update-ref "$RACE_REF" "$RACE_SHA"\n'
                                    'fi\nexec "$REAL_GIT" "$@"\n')
        (wrapper / 'git').chmod(0o755)
        for ref in ('refs/heads/master', 'refs/heads/release/1.2.3'):
            env = dict(self.env, PATH=f"{wrapper}:{self.env['PATH']}", REAL_GIT=real_git,
                       FIXTURE_REMOTE=str(self.remote), RACE_REF=ref, RACE_SHA=self.release)
            self.assertNotEqual(self.helper('promote', env=env, ok=False).returncode, 0)
            self.assertEqual(self.ref('refs/heads/master'), self.release if ref.endswith('master') else self.base)
            self.git('--git-dir', str(self.remote), 'update-ref', 'refs/heads/master', self.base)
            self.git('--git-dir', str(self.remote), 'update-ref', 'refs/heads/release/1.2.3', self.snapshot)

    def test_resume_after_success_skips_upload_and_unblocks_next_release(self):
        self.stage_and_verify()
        self.helper('promote')
        self.assertIn('skip_deploy=false', self.helper('publication-status').stdout)
        self.helper('publication-start')
        # Mock the Maven destination: only this local file is written.
        destination = Path(self.temp.name) / 'mock-central-upload'
        destination.write_text(self.release)
        self.helper('publication-complete')
        self.assertIn('skip_deploy=true', self.helper('publication-status').stdout)
        self.assertNotEqual(self.helper('publication-start', ok=False).returncode, 0)
        self.git('tag', '-a', '1.2.3', '-m', 'Release 1.2.3', self.release)
        self.git('push', '-q', 'origin', 'refs/tags/1.2.3')
        self.helper('pending')
        self.assertEqual(destination.read_text(), self.release)
        self.assertEqual(self.ref('refs/tags/1.2.3^{}'), self.release)

    def test_ambiguous_partial_upload_refuses_blind_redeploy(self):
        self.helper('publication-start')
        self.assertNotEqual(self.helper('publication-status', ok=False).returncode, 0)
        self.assertNotEqual(self.helper('publication-start', ok=False).returncode, 0)
        self.assertIsNone(self.ref('refs/tags/1.2.3'))

    def test_conflicting_publication_checkpoint_fails(self):
        self.git('push', '-q', 'origin', f'{self.snapshot}:refs/heads/release-publishing/1.2.3')
        self.assertNotEqual(self.helper('publication-status', ok=False).returncode, 0)

    def test_manifest_validates_maven_metadata_and_hashes(self):
        self.git('checkout', '-q', '--detach', self.release)
        for module in ('ta4j-core', 'ta4j-examples'):
            target = self.repo / module / 'target'
            target.mkdir()
            with zipfile.ZipFile(target / f'{module}-1.2.3.jar', 'w') as archive:
                archive.writestr(f'META-INF/maven/org.ta4j/{module}/pom.properties',
                                 f'groupId=org.ta4j\nartifactId={module}\nversion=1.2.3\n')
        self.helper('manifest', '--output', 'manifest.json')
        self.assertEqual(len(json.loads((self.repo / 'manifest.json').read_text())), 5)
        with zipfile.ZipFile(self.repo / 'ta4j-core/target/ta4j-core-1.2.3.jar', 'w') as archive:
            archive.writestr('META-INF/maven/org.ta4j/ta4j-core/pom.properties', 'version=9.9.9')
        self.assertNotEqual(self.helper('manifest', ok=False).returncode, 0)

    def test_failed_canonical_validation_never_issues_receipt(self):
        self.helper('stage')
        scripts = self.repo / 'scripts'
        scripts.mkdir()
        (scripts / 'run-full-build-quiet.sh').write_text('#!/bin/sh\nexit 17\n')
        (scripts / 'run-full-build-quiet.sh').chmod(0o755)
        tools = Path(self.temp.name) / 'tools'
        tools.mkdir()
        (tools / 'xvfb-run').write_text('#!/bin/sh\nexec "$@"\n')
        (tools / 'xvfb-run').chmod(0o755)
        evidence = Path(self.temp.name) / 'evidence'
        env = dict(self.env, PATH=f"{tools}:{self.env['PATH']}")
        result = self.run_command('bash', str(ROOT / 'scripts/release/verify_candidate.sh'),
                                  'snapshot', str(evidence), env=env, ok=False)
        self.assertEqual(result.returncode, 17)
        self.assertFalse((evidence / 'receipt.json').exists())
        self.assertEqual(self.ref('refs/heads/master'), self.base)
        self.assertIsNone(self.ref('refs/tags/1.2.3'))
        self.assertIsNone(self.ref('refs/heads/release-publishing/1.2.3'))

    def test_workflow_separates_unprivileged_verification_and_serializes_publication(self):
        prepare = (ROOT / '.github/workflows/prepare-release.yml').read_text()
        publish = (ROOT / '.github/workflows/publish-release.yml').read_text()
        for kind in ('release', 'snapshot'):
            # Extract until the next job, preserving indented step declarations.
            import re
            job = re.split(r'\n  [a-z_]+:\n', prepare.split(f'  verify_{kind}:\n', 1)[1])[0]
            self.assertIn('contents: read', job)
            self.assertIn('persist-credentials: false', job)
            self.assertNotIn('secrets.', job)
            self.assertNotIn('contents: write', job)
        self.assertIn('needs: [prepare, verify_release, verify_snapshot]', prepare)
        self.assertIn('needs: [prepare, promote]', prepare)
        self.assertIn('uses: ./.github/workflows/publish-release.yml', prepare)
        self.assertNotIn('workflow_id: "publish-release.yml"', prepare)
        self.assertIn('group: release-lifecycle-${{ github.repository }}', prepare)
        self.assertIn("format('release-lifecycle-{0}', github.repository)", publish)
        self.assertLess(publish.index('Verify release candidate tree is clean'), publish.index('GPG_PRIVATE_KEY:'))
        self.assertLess(publish.index('Record publication attempt before any Maven upload'), publish.index('      - name: Deploy Release to Maven Central'))
        self.assertLess(publish.index('Record successful Maven publication before pushing tag'), publish.index('      - name: Push release tag'))

    def ancestry_script(self):
        workflow = (ROOT / '.github/workflows/publish-release.yml').read_text()
        step = workflow.split('      - name: Verify release commit ancestry\n', 1)[1].split('\n      - name:', 1)[0]
        return textwrap.dedent(step.split('        run: |\n', 1)[1])

    def test_pr_only_release_commit_after_squash_is_rejected(self):
        env = dict(self.env, RELEASE_COMMIT=self.release, RELEASE_COMMIT_IN_PR='true',
                   MERGE_COMMIT=self.base, EVENT_NAME='pull_request', RECOVERY_MODE='false', AUTOMATIC='false')
        result = self.run_command('bash', '-c', self.ancestry_script(), env=env, ok=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('tree does not match', result.stdout)

    def test_automatic_path_never_uses_recovery_to_accept_candidate(self):
        env = dict(self.env, RELEASE_COMMIT=self.release, EVENT_NAME='workflow_dispatch',
                   RECOVERY_MODE='true', AUTOMATIC='true')
        self.assertNotEqual(self.run_command('bash', '-c', self.ancestry_script(), env=env, ok=False).returncode, 0)
        env['AUTOMATIC'] = 'false'
        self.run_command('bash', '-c', self.ancestry_script(), env=env)


if __name__ == '__main__':
    unittest.main(verbosity=2)
