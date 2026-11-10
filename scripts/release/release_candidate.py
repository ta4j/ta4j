#!/usr/bin/env python3
"""Branch-first release invariants. No Maven or publication credentials required."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile


def git(*args):
    return subprocess.check_output(['git', *args], text=True).strip()


def require(condition, message):
    if not condition:
        raise ValueError(message)


def output(key, value):
    print(f'audit:{key}={value}')
    if os.environ.get('GITHUB_OUTPUT'):
        with open(os.environ['GITHUB_OUTPUT'], 'a', encoding='utf-8') as stream:
            stream.write(f'{key}={value}\n')


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def identity():
    """Bind candidate evidence to this repository, controller, run and exact B/R/S."""
    names = ('BASE_SHA', 'RELEASE_SHA', 'SNAPSHOT_SHA', 'RELEASE_VERSION',
             'NEXT_VERSION', 'CANDIDATE_BRANCH', 'GITHUB_REPOSITORY',
             'GITHUB_WORKFLOW_REF', 'GITHUB_WORKFLOW_SHA', 'GITHUB_RUN_ID',
             'GITHUB_RUN_ATTEMPT')
    data = {name: os.environ[name] for name in names}
    data['FREEZE_REF'] = f"refs/heads/release-freeze/{data['RELEASE_VERSION']}"
    for name in ('BASE_SHA', 'RELEASE_SHA', 'SNAPSHOT_SHA', 'GITHUB_WORKFLOW_SHA'):
        require(re.fullmatch(r'[0-9a-f]{40}', data[name]), f'Invalid {name}')
    require(re.fullmatch(r'\d+\.\d+\.\d+', data['RELEASE_VERSION']), 'Invalid release version')
    require(re.fullmatch(r'\d+\.\d+\.\d+-SNAPSHOT', data['NEXT_VERSION']), 'Invalid next version')
    require(data['CANDIDATE_BRANCH'] == f"release/{data['RELEASE_VERSION']}", 'Unexpected candidate branch')
    require(data['GITHUB_WORKFLOW_SHA'] == data['BASE_SHA'], 'Controller must be starting master B')
    return data


def refs():
    return {ref: sha for sha, ref in (line.split() for line in git('ls-remote', '--heads', '--tags', 'origin').splitlines())}


def create_refs(*refspecs, deletes=None):
    """A successful no-op push is not a create-only claim, even with a lease."""
    deletes = deletes or {}
    targets = {refspec.split(':', 1)[1] for refspec in refspecs}
    report = git('push', '--atomic', '--porcelain',
                 *[f'--force-with-lease={ref}:' for ref in sorted(targets)],
                 *[f'--force-with-lease={ref}:{sha}' for ref, sha in deletes.items()],
                 'origin', *refspecs, *[f':{ref}' for ref in deletes])
    created = set()
    deleted = set()
    for line in report.splitlines():
        fields = line.split('\t')
        if len(fields) >= 2 and fields[0] == '*' and ':' in fields[1]:
            created.add(fields[1].split(':', 1)[1])
        if len(fields) >= 2 and fields[0] == '-' and ':' in fields[1]:
            deleted.add(fields[1].split(':', 1)[1])
    require(targets <= created and set(deletes) <= deleted, 'Ref claim was not newly created/consumed; refusing a concurrent/no-op push')


def pom_version(sha, path='pom.xml'):
    root = ET.fromstring(git('show', f'{sha}:{path}'))
    return root.findtext('{*}version') or root.findtext('{*}parent/{*}version')


def candidate(data):
    """Require the two-commit B → R → S chain and matching reactor versions."""
    base, release, snapshot = (data[name] for name in ('BASE_SHA', 'RELEASE_SHA', 'SNAPSHOT_SHA'))
    require(git('rev-list', '--parents', '-n', '1', release).split() == [release, base], 'R must have only parent B')
    require(git('rev-list', '--parents', '-n', '1', snapshot).split() == [snapshot, release], 'S must have only parent R')
    git('merge-base', '--is-ancestor', base, snapshot)
    for module in ('pom.xml', 'ta4j-core/pom.xml', 'ta4j-examples/pom.xml'):
        require(pom_version(release, module) == data['RELEASE_VERSION'], f'Release POM mismatch: {module}')
        require(pom_version(snapshot, module) == data['NEXT_VERSION'], f'Snapshot POM mismatch: {module}')
    git('cat-file', '-e', f"{release}:release/{data['RELEASE_VERSION']}.md")


def clean():
    require(not git('status', '--porcelain', '--untracked-files=no'), 'Validation changed tracked files/index')


def pending_guard():
    """Prevent another preparation until every previous automatic release is complete."""
    remote = refs()
    for ref, snapshot in remote.items():
        if not ref.startswith('refs/heads/release-pending/'):
            continue
        version = ref.rsplit('/', 1)[1]
        complete = remote.get(f'refs/heads/release-published/{version}')
        require(complete and remote.get(f'refs/tags/{version}^{{}}', remote.get(f'refs/tags/{version}')) == complete,
                f'Unfinished automatic release {version} at {snapshot}; resume or reconcile it before preparing another version')
        require(git('rev-parse', f'{snapshot}^') == complete, 'Completed release must be R, the parent of pending S')


FREEZE_PREFIX = 'refs/heads/release-freeze/'


def freeze_assert():
    """Require the owned freeze and master to retain their captured identities."""
    remote = refs()
    require(remote.get(os.environ['FREEZE_REF']) == os.environ['FREEZE_SHA'], 'Release freeze was removed or mutated')
    require(remote.get(f"refs/heads/{os.environ['DEFAULT_BRANCH']}") == os.environ['FROZEN_MASTER'],
            'Master advanced while release was frozen; reconcile before publishing')


def publication_identity():
    """Resolve preliminary version/R without replacing the publisher ancestry checks."""
    # Preliminary resolution only; publisher still enforces its full PR/ancestry checks.
    event = json.loads(Path(os.environ.get('GITHUB_EVENT_PATH', '/dev/null')).read_text() or '{}')
    inputs = event.get('inputs', {})
    body = event.get('pull_request', {}).get('body') or ''
    meta = re.search(r'<!--\s*release-meta([\s\S]*?)-->', body)
    def field(name):
        found = re.search(rf'^\s*{name}\s*:\s*(.+)$', meta[1], re.MULTILINE) if meta else None
        return found[1].strip() if found else ''
    version = os.environ.get('RELEASE_VERSION') or inputs.get('releaseVersion') or field('releaseVersion')
    require(version and re.fullmatch(r'\d+\.\d+\.\d+', version), 'Invalid release version for freeze')
    release = os.environ.get('RELEASE_SHA') or inputs.get('releaseCommit') or field('releaseCommit')
    if release:
        require(re.fullmatch(r'[0-9a-fA-F]{7,40}', release), 'Invalid release SHA for freeze')
        if subprocess.run(['git', 'cat-file', '-e', f'{release}^{{commit}}'],
                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode:
            # Recovery may name an unreachable full SHA, just as the publisher can.
            # Fetching it supplies identity only; approval/ancestry remain mandatory.
            git('fetch', '--no-tags', 'origin', release)
        release = git('rev-parse', f'{release}^{{commit}}')
    else:
        history = git('log', '--diff-filter=A', '--format=%H',
                      f"origin/{os.environ['DEFAULT_BRANCH']}", '--', f'release/{version}.md')
        require(history, 'Cannot resolve release R for publication freeze')
        release = history.splitlines()[0]
    return version, release


def freeze_command(command):
    """Acquire, transfer, recover or consume only the exact release freeze journal."""
    remote = refs()
    active = {ref: sha for ref, sha in remote.items() if ref.startswith(FREEZE_PREFIX)}
    default = f"refs/heads/{os.environ['DEFAULT_BRANCH']}"
    if command == 'freeze-prepare':
        base = os.environ['BASE_SHA']
        require(not active, 'Another release freeze exists; resume/reconcile it first')
        require(remote.get(default) == base, 'Master advanced before freeze acquisition')
        run = os.environ['GITHUB_RUN_ID']
        require(run.isdigit(), 'Invalid workflow run id')
        create_refs(f'{base}:{FREEZE_PREFIX}prepare-{run}')
    elif command == 'freeze-publish':
        version, release = publication_identity()
        ref = FREEZE_PREFIX + version
        require(not set(active) - {ref}, 'Another release is frozen; refusing competing publication')
        if ref in active:
            marker = active[ref]
            message = git('show', '-s', '--format=%B', marker)
            if message.startswith('{'):
                record = json.loads(message)
                require(record.get('schema') == 1 and record.get('version') == version and record.get('release') == release,
                        'Freeze belongs to a different publication')
                master = record['master']
                parents = git('rev-list', '--parents', '-n', '1', marker).split()[1:]
                require(parents == list(dict.fromkeys((release, master))), 'Invalid publication freeze parents')
                require(git('rev-parse', f'{marker}^{{tree}}') == git('rev-parse', f'{release}^{{tree}}'), 'Invalid freeze tree')
            else:
                require(git('rev-list', '--parents', '-n', '1', marker).split() == [marker, release],
                        'Automatic freeze must identify S with parent R')
                master = marker
        else:
            require(os.environ.get('AUTOMATIC') != 'true', 'Automatic publication lost its verified freeze')
            master = remote[default]
            record = json.dumps({'schema': 1, 'version': version, 'release': release, 'master': master}, sort_keys=True)
            parents = list(dict.fromkeys((release, master)))
            args = [item for parent in parents for item in ('-p', parent)]
            marker = git('-c', 'user.name=github-actions[bot]', '-c',
                         'user.email=github-actions[bot]@users.noreply.github.com',
                         'commit-tree', git('rev-parse', f'{release}^{{tree}}'), *args, '-m', record)
            create_refs(f'{marker}:{ref}')
        require(refs().get(default) == master, 'Master moved while acquiring/recovering publication freeze')
        output('freeze_ref', ref)
        output('freeze_sha', marker)
        output('frozen_master', master)
        output('release_sha', release)
        output('release_version', version)
    elif command == 'freeze-assert':
        freeze_assert()
    elif command == 'freeze-abort':
        # Only a proven pre-promotion failure may unfreeze automatically.
        require(remote.get(default) == os.environ['BASE_SHA'], 'Promotion outcome changed/uncertain; retain freeze')
        deletes = {}
        prep = FREEZE_PREFIX + 'prepare-' + os.environ['GITHUB_RUN_ID']
        if prep in active:
            require(active[prep] == os.environ['BASE_SHA'], 'Preparation freeze mutated')
            deletes[prep] = active[prep]
        version = os.environ.get('RELEASE_VERSION', '')
        if version:
            ref = FREEZE_PREFIX + version
            if ref in active:
                require(active[ref] == os.environ.get('SNAPSHOT_SHA'), 'Candidate freeze mutated')
                require(f'refs/heads/release-publishing/{version}' not in remote, 'Publication attempted; retain freeze')
                deletes[ref] = active[ref]
        if deletes:
            create_refs(deletes=deletes)
    else:
        version, release = os.environ['RELEASE_VERSION'], os.environ['RELEASE_SHA']
        require(remote.get(f'refs/heads/release-published/{version}') == release, 'Maven success not recorded; retain freeze')
        require(remote.get(f'refs/tags/{version}^{{}}', remote.get(f'refs/tags/{version}')) == release,
                'Exact R tag not verified; retain freeze')
        freeze_assert()
        create_refs(deletes={os.environ['FREEZE_REF']: os.environ['FREEZE_SHA']})


def manifest(version, path):
    """Supplement the existing strict artifact inventory with hashes and Maven metadata."""
    entries = []
    ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
    for module in ('.', 'ta4j-core', 'ta4j-examples'):
        pom = Path(module) / 'pom.xml'
        root = ET.parse(pom).getroot()
        artifact = root.findtext('m:artifactId', namespaces=ns)
        effective_version = root.findtext('m:version', namespaces=ns) or root.findtext('m:parent/m:version', namespaces=ns)
        require(effective_version == version, f'POM version mismatch: {pom}')
        group = root.findtext('m:groupId', namespaces=ns) or root.findtext('m:parent/m:groupId', namespaces=ns)
        require(group == 'org.ta4j', f'POM group mismatch: {pom}')
        entries.append({'path': str(pom), 'sha256': digest(pom), 'groupId': group,
                        'artifactId': artifact, 'version': version, 'packaging': root.findtext('m:packaging', 'jar', ns)})
        if module == '.':
            continue
        for jar in sorted((Path(module) / 'target').glob('*.jar')):
            with zipfile.ZipFile(jar) as archive:
                metadata_path = f'META-INF/maven/{group}/{artifact}/pom.properties'
                if metadata_path not in archive.namelist():
                    require(jar.name.endswith(('-sources.jar', '-javadoc.jar')), f'Missing Maven metadata: {jar}')
                    entries.append({'path': str(jar), 'sha256': digest(jar), 'size': jar.stat().st_size,
                                    'groupId': group, 'artifactId': artifact, 'version': version})
                    continue
                metadata = archive.read(metadata_path).decode()
                properties = dict(line.split('=', 1) for line in metadata.splitlines() if '=' in line and not line.startswith('#'))
                require(all(properties.get(key) == value for key, value in
                            (('groupId', group), ('artifactId', artifact), ('version', version))), f'JAR metadata mismatch: {jar}')
            entries.append({'path': str(jar), 'sha256': digest(jar), 'size': jar.stat().st_size,
                            'groupId': group, 'artifactId': artifact, 'version': version})
    Path(path).write_text(json.dumps(entries, sort_keys=True, indent=2) + '\n')
    output('production_manifest_sha256', digest(path))


def main():
    """Dispatch a guarded release transition or write bound validation evidence."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['pending', 'stage', 'receipt', 'promote', 'manifest',
                                           'publication-status', 'publication-start', 'publication-complete',
                                           'freeze-prepare', 'freeze-publish', 'freeze-assert', 'freeze-abort', 'freeze-finish'])
    parser.add_argument('--kind', choices=['release', 'snapshot'])
    parser.add_argument('--evidence', action='append', default=[])
    parser.add_argument('--output', default='receipt.json')
    args = parser.parse_args()
    if args.command.startswith('freeze-'):
        freeze_command(args.command)
    elif args.command == 'pending':
        pending_guard()
    elif args.command == 'manifest':
        manifest(os.environ['RELEASE_VERSION'], args.output)
    elif args.command.startswith('publication-'):
        version, release = os.environ['RELEASE_VERSION'], os.environ['RELEASE_SHA']
        require(re.fullmatch(r'\d+\.\d+\.\d+', version), 'Invalid release version')
        require(re.fullmatch(r'[0-9a-f]{40}', release), 'Invalid release SHA')
        started, completed = (f'refs/heads/{prefix}/{version}' for prefix in ('release-publishing', 'release-published'))
        remote = refs()
        for ref in (started, completed):
            require(remote.get(ref) in (None, release), f'Publication checkpoint conflicts with R: {ref}')
        require(not remote.get(completed) or remote.get(started), 'Completed checkpoint has no start checkpoint')
        if args.command == 'publication-status':
            # An incomplete attempt is ambiguous even if Central is eventually consistent.
            require(not remote.get(started) or remote.get(completed),
                    'Maven publication may have partially succeeded. Reconcile Central; refusing blind redeploy')
            output('skip_deploy', 'true' if remote.get(completed) else 'false')
        elif args.command == 'publication-start':
            if os.environ.get('FREEZE_REF'):
                freeze_assert()
            require(not remote.get(started) and not remote.get(completed), 'Publication already attempted; refusing redeploy')
            create_refs(f'{release}:{started}')
        else:
            require(remote.get(started) == release and not remote.get(completed), 'Missing/duplicate publication attempt')
            create_refs(f'{release}:{completed}')
    else:
        data = identity()
        candidate(data)
        branch = f"refs/heads/{data['CANDIDATE_BRANCH']}"
        pending = f"refs/heads/release-pending/{data['RELEASE_VERSION']}"
        if args.command == 'stage':
            clean()
            pending_guard()
            require(git('rev-parse', 'HEAD') == data['SNAPSHOT_SHA'], 'Preparation must end at S')
            prep = FREEZE_PREFIX + 'prepare-' + data['GITHUB_RUN_ID']
            remote = refs()
            require(remote.get(prep) == data['BASE_SHA'], 'Preparation freeze missing or mutated')
            require(remote.get(f"refs/heads/{os.environ['DEFAULT_BRANCH']}") == data['BASE_SHA'], 'Master advanced during preparation')
            create_refs(f"{data['SNAPSHOT_SHA']}:{branch}", f"{data['SNAPSHOT_SHA']}:{pending}",
                        f"{data['SNAPSHOT_SHA']}:{data['FREEZE_REF']}", deletes={prep: data['BASE_SHA']})
        elif args.command == 'receipt':
            require(args.kind, '--kind required')
            expected = data['RELEASE_SHA' if args.kind == 'release' else 'SNAPSHOT_SHA']
            require(git('rev-parse', 'HEAD') == expected, 'Validation checked out the wrong commit')
            clean()
            require(args.evidence and all(Path(path).is_file() for path in args.evidence), 'Missing validation evidence')
            receipt = {'schema': 1, 'identity': data, 'kind': args.kind, 'verified_sha': expected,
                       'evidence': {Path(path).name: digest(path) for path in args.evidence}}
            Path(args.output).write_text(json.dumps(receipt, sort_keys=True, indent=2) + '\n')
            output('receipt_sha256', digest(args.output))
        else:
            for kind, field in (('release', 'RELEASE_SHA'), ('snapshot', 'SNAPSHOT_SHA')):
                path = Path(kind) / 'receipt.json'
                require(digest(path) == os.environ[f'{kind.upper()}_RECEIPT_SHA256'], f'{kind} receipt digest mismatch')
                receipt = json.loads(path.read_text())
                require(receipt.get('schema') == 1 and receipt.get('identity') == data and
                        receipt.get('kind') == kind and receipt.get('verified_sha') == data[field] and receipt.get('evidence'),
                        f'Stale/foreign {kind} validation receipt')
            default = os.environ['DEFAULT_BRANCH']
            git('check-ref-format', f'refs/heads/{default}')
            master = f'refs/heads/{default}'
            remote = refs()
            require(remote.get(data['FREEZE_REF']) == data['SNAPSHOT_SHA'], 'Candidate freeze missing or mutated')
            require(remote.get(branch) == data['SNAPSHOT_SHA'], 'Candidate branch moved after verification')
            require(remote.get(pending) == data['SNAPSHOT_SHA'], 'Pending candidate changed')
            require(remote.get(master) == data['BASE_SHA'], 'Master advanced after preparation; revalidation required')
            # Deleting the consumed candidate makes its expected-S lease a real update.
            # A no-op S:S push would NOT check that ref atomically with master.
            git('push', '--atomic', f"--force-with-lease={master}:{data['BASE_SHA']}",
                f"--force-with-lease={branch}:{data['SNAPSHOT_SHA']}", 'origin',
                f"{data['SNAPSHOT_SHA']}:{master}", f':{branch}')
            require(refs().get(master) == data['SNAPSHOT_SHA'], 'Promotion outcome uncertain; inspect remote before retrying')
            output('promoted_release', data['RELEASE_SHA'])


if __name__ == '__main__':
    try:
        main()
    except (ValueError, KeyError, subprocess.CalledProcessError) as error:
        print(f'::error::{error}', file=sys.stderr)
        sys.exit(1)
