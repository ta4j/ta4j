#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# prepare-release.sh
#
# Responsibilities:
#   - Validate tools
#   - Update CHANGELOG.md (move "Unreleased" -> new release section)
#   - Update README.md version references
#   - Generate standalone release notes file
#
# What it does NOT do:
#   - No Maven calls
#   - No version bumping
#   - No tagging
#   - No Git commits
#
# Usage:
#   scripts/prepare-release.sh <release-version>
#
# Output:
#   release_version=<version>
#   release_notes_file=<file>
#
# =============================================================================

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <release-version>" >&2
  exit 1
fi

RELEASE_VERSION="$1"
RELEASE_NOTES_FILE="release/${RELEASE_VERSION}.md"

# -----------------------------------------------------------------------------
# Tools check
# -----------------------------------------------------------------------------
for tool in git perl python3; do
  command -v "$tool" >/dev/null 2>&1 || {
    echo "Error: required tool '$tool' not found" >&2
    exit 1
  }
done

# -----------------------------------------------------------------------------
# Update CHANGELOG.md
# -----------------------------------------------------------------------------
update_changelog() {
  local version="$1"
  local outfile="$2"

  python3 - "$version" "$outfile" <<'PY'
import datetime, sys, re
from pathlib import Path

version = sys.argv[1]
outfile = Path(sys.argv[2])

changelog = Path("CHANGELOG.md")
text = changelog.read_text()

today = datetime.date.today().strftime("%Y-%m-%d")
release_header = f"## {version} ({today})"

# Find the "Unreleased" section
m = list(re.finditer(r"^##\s*\[?Unreleased\]?", text, re.IGNORECASE | re.MULTILINE))
if not m:
    # No Unreleased section → create an empty one
    sections = f"## Unreleased\n\n- _No changes yet._\n\n{release_header}\n\n_No changes recorded._\n"
    changelog.write_text(sections)
    outfile.parent.mkdir(exist_ok=True, parents=True)
    outfile.write_text(f"{release_header}\n\n_No changes recorded._\n")
    sys.exit(0)

start = m[0].end()
# Find next section after Unreleased
next_sec = list(re.finditer(r"^##\s+", text[start:], re.MULTILINE))
end = start + (next_sec[0].start() if next_sec else len(text[start:]))

unreleased_block = text[start:end].strip()
if not unreleased_block:
    unreleased_block = "_No notable changes recorded._"

# New changelog structure:
#   1. Unreleased (reset)
#   2. New release version section
#   3. Everything after the old Unreleased section

new_unreleased = "## Unreleased\n\n- _No changes yet._\n\n"
new_release = f"{release_header}\n\n{unreleased_block}\n\n"

new_text = new_unreleased + new_release + text[end:].lstrip()
changelog.write_text(new_text)

# Write standalone release notes
outfile.parent.mkdir(exist_ok=True, parents=True)
outfile.write_text(f"{release_header}\n\n{unreleased_block}\n")

PY
}

# -----------------------------------------------------------------------------
# README Sentinel Validation
# -----------------------------------------------------------------------------
require_readme_sentinels() {
  if [[ ! -f README.md ]]; then
    echo "Error: README.md not found" >&2
    exit 1
  fi

  local missing=0
  local markers=(
    "TA4J_VERSION_BLOCK:core:stable:begin"
    "TA4J_VERSION_BLOCK:core:stable:end"
    "TA4J_VERSION_BLOCK:core:snapshot:begin"
    "TA4J_VERSION_BLOCK:core:snapshot:end"
    "TA4J_VERSION_BLOCK:examples:stable:begin"
    "TA4J_VERSION_BLOCK:examples:stable:end"
    "TA4J_VERSION_BLOCK:examples:snapshot:begin"
    "TA4J_VERSION_BLOCK:examples:snapshot:end"
  )

  for marker in "${markers[@]}"; do
    if ! grep -Eq "<!--[[:space:]]*${marker}[[:space:]]*-->" README.md; then
      echo "Error: missing README sentinel <!-- ${marker} -->" >&2
      missing=1
    fi
  done

  if [[ $missing -ne 0 ]]; then
    exit 1
  fi
}

# -----------------------------------------------------------------------------
# Snapshot Version Calculator
# -----------------------------------------------------------------------------
compute_snapshot_version() {
  local version="$1"
  python3 - "$version" <<'PY'
import re
import sys

version = sys.argv[1]
m = re.match(r"^(\d+)\.(\d+)\.(\d+)$", version)
if not m:
    print(f"Error: invalid release version '{version}' (expected major.minor.patch)", file=sys.stderr)
    sys.exit(1)

major, minor, patch = map(int, m.groups())
print(f"{major}.{minor}.{patch + 1}-SNAPSHOT")
PY
}

# -----------------------------------------------------------------------------
# README / Version Reference Updater
# -----------------------------------------------------------------------------
update_readme() {
  local version="$1"
  local snapshot_version="$2"

  export VERSION="$version"
  export SNAPSHOT_VERSION="$snapshot_version"

  perl -0777 -i -pe '
    sub bump_version_tags {
      my ($block, $v) = @_;
      $block =~ s{(<version>)[^<]+(</version>)}{$1$v$2}g;
      return $block;
    }

    # core stable
    s{
      (<!--\s*TA4J_VERSION_BLOCK:core:stable:begin\s*-->)
      (.*?)
      (<!--\s*TA4J_VERSION_BLOCK:core:stable:end\s*-->)
    }{$1 . bump_version_tags($2, $ENV{VERSION}) . $3}gsex;

    # core snapshot
    s{
      (<!--\s*TA4J_VERSION_BLOCK:core:snapshot:begin\s*-->)
      (.*?)
      (<!--\s*TA4J_VERSION_BLOCK:core:snapshot:end\s*-->)
    }{$1 . bump_version_tags($2, $ENV{SNAPSHOT_VERSION}) . $3}gsex;

    # examples stable
    s{
      (<!--\s*TA4J_VERSION_BLOCK:examples:stable:begin\s*-->)
      (.*?)
      (<!--\s*TA4J_VERSION_BLOCK:examples:stable:end\s*-->)
    }{$1 . bump_version_tags($2, $ENV{VERSION}) . $3}gsex;

    # examples snapshot
    s{
      (<!--\s*TA4J_VERSION_BLOCK:examples:snapshot:begin\s*-->)
      (.*?)
      (<!--\s*TA4J_VERSION_BLOCK:examples:snapshot:end\s*-->)
    }{$1 . bump_version_tags($2, $ENV{SNAPSHOT_VERSION}) . $3}gsex;
  ' README.md

  perl -0pi -e "s|Current version: \`[0-9]+\\.[0-9]+(\\.[0-9]+)?\`|Current version: \`${version}\`|g" README.md || true

  if ! grep -Fq "<version>${VERSION}</version>" README.md; then
    echo "Error: expected release version ${VERSION} to appear in README.md" >&2
    exit 1
  fi

  if ! grep -Fq "<version>${SNAPSHOT_VERSION}</version>" README.md; then
    echo "Error: expected snapshot version ${SNAPSHOT_VERSION} to appear in README.md" >&2
    exit 1
  fi

}


echo "Preparing release: $RELEASE_VERSION"

require_readme_sentinels
SNAPSHOT_VERSION="$(compute_snapshot_version "$RELEASE_VERSION")"
update_changelog "$RELEASE_VERSION" "$RELEASE_NOTES_FILE"
update_readme "$RELEASE_VERSION" "$SNAPSHOT_VERSION"

echo
echo "release_version=${RELEASE_VERSION}"
echo "release_notes_file=${RELEASE_NOTES_FILE}"
