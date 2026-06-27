#!/usr/bin/env python3
"""Compatibility launcher for the shell release helper.

The release helper implementation lives in ``release_helpers.sh``. This
launcher remains so older local calls continue to work while repository CodeQL
configuration still analyzes Python for pull requests that remove the former
Python helper.
"""

from __future__ import annotations

import pathlib
import subprocess
import sys


def main(argv: list[str]) -> int:
    helper = pathlib.Path(__file__).with_name("release_helpers.sh")
    return subprocess.call(["bash", str(helper), *argv])


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
