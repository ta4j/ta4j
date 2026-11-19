#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
python3 - "$root" <<'PY'
from pathlib import Path
import re
import sys
import unittest

root = Path(sys.argv.pop())
pattern = re.compile(r'(?m)^( *)group: release-freeze-status-\$\{\{ github.repository \}\}\n\1cancel-in-progress: false\n\1queue: max$')

def valid(text, expected):
    return (len(pattern.findall(text)) == expected and
            len(re.findall(r'(?m)^ *queue:', text)) == expected)

class QueueTests(unittest.TestCase):
    def test_all_five_shared_freeze_sections_queue_without_cancelling(self):
        names = {'release-freeze.yml': 1, 'prepare-release.yml': 2, 'publish-release.yml': 2}
        for path in (root / '.github/workflows').glob('*.yml'):
            self.assertTrue(valid(path.read_text(), names.get(path.name, 0)), str(path))

    def test_missing_invalid_or_misplaced_queue_is_rejected(self):
        text = (root / '.github/workflows/release-freeze.yml').read_text()
        for invalid in (text.replace('  queue: max\n', ''),
                        text.replace('queue: max', 'queue: other'),
                        text.replace('queue: max', 'queue: single'),
                        text.replace('cancel-in-progress: false', 'cancel-in-progress: true'),
                        text.replace('  queue: max', '    queue: max')):
            self.assertFalse(valid(invalid, 1))

unittest.main(verbosity=2)
PY
