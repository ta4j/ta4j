#!/usr/bin/env python3
"""Select independent Maven and script/action checks from the actual changed paths."""
import argparse
import json
import os
from pathlib import PurePosixPath
import re
import subprocess


def classify(paths):
    """Select independent lanes; documentation alone does not require Maven."""
    maven = scripts = False
    for path in paths:
        script = path.startswith(('scripts/', '.github/', '.githooks/', '.mvn/wrapper/')) or path in ('mvnw', 'mvnw.cmd')
        workflow_guide = path in ('AGENTS.md', 'PULL_REQUESTS.md')
        scripts |= script or workflow_guide
        compiled_resource = re.search(r'(^|/)src/(main|test)/resources/', path) is not None
        documentation = PurePosixPath(path).suffix.lower() in ('.md', '.rst') or path.startswith('docs/')
        maven |= compiled_resource or not (script or documentation)
    return {'maven': maven, 'scripts': scripts}


def main():
    """Classify a complete rename-aware diff, conservatively selecting both if unspecified."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', default='')
    parser.add_argument('--head', default='')
    args = parser.parse_args()
    # Explicit manual runs or missing/initial history conservatively select both.
    selected = {'maven': True, 'scripts': True}
    if all(re.fullmatch('[0-9a-f]{40}', sha) and set(sha) != {'0'} for sha in (args.base, args.head)):
        changed = subprocess.check_output(['git', 'diff', '--no-renames', '--name-only', '-z',
                                           f'{args.base}...{args.head}'])
        selected = classify(changed.decode().rstrip('\0').split('\0') if changed else [])
    print(json.dumps(selected, sort_keys=True))
    if os.environ.get('GITHUB_OUTPUT'):
        with open(os.environ['GITHUB_OUTPUT'], 'a', encoding='utf-8') as stream:
            for key, value in selected.items():
                stream.write(f'{key}={str(value).lower()}\n')


if __name__ == '__main__':
    main()
