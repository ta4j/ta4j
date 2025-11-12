#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# prepare-release.sh
# Prepares a Maven-based repository for a release or snapshot.
#
# Responsibilities:
#  - Validate tools and repo state
#  - Determine release and next snapshot versions
#  - Update Maven versions, CHANGELOG.md, README.md
#  - Generate release notes file
#  - Emit version info for CI/CD (release_version, next_version, release_notes_file)
#
# Optional:
#  - --dry-run shows intended changes without modifying files.
# =============================================================================

usage() {
  cat <<'USAGE'
Usage:
  scripts/prepare-release.sh [release|snapshot] [options]

Commands:
  release     Prepare repository for a new release (default)
  snapshot    Prepare repository for the next development snapshot

Options:
  --release-version <version>   Override the derived release version.
  --next-version <version>      Override the inferred next development version.
  --dry-run                     Show what would change, but make no modifications.
  -h, --help                    Show this help message and exit.

This script does not commit, tag, or push changes; those are handled by CI.
USAGE
}

# -----------------------------------------------------------------------------
# Utilities
# -----------------------------------------------------------------------------
ensure_tools() {
  for tool in mvn python3; do
    command -v "$tool" >/dev/null 2>&1 || {
      echo "Error: required tool '$tool' not found in PATH" >&2
      exit 1
    }
  done
}

trim() {
  sed 's/^[[:space:]]\+//;s/[[:space:]]\+$//' <<<"$1"
}

current_project_version() {
  mvn -q -DforceStdout help:evaluate -Dexpression=project.version | tail -n 1
}

increment_version() {
  local version="$1"
  IFS='.' read -r -a parts <<<"$version"
  if [[ ${#parts[@]} -lt 1 ]]; then
    echo "Invalid version: $version" >&2
    exit 1
  fi
  local idx=$(( ${#parts[@]} - 1 ))
  if ! [[ ${parts[$idx]} =~ ^[0-9]+$ ]]; then
    echo "Version segment '${parts[$idx]}' is not numeric" >&2
    exit 1
  fi
  parts[$idx]=$(( parts[$idx] + 1 ))
  local next="${parts[0]}"
  for ((i=1; i<${#parts[@]}; i++)); do next+=".${parts[$i]}"; done
  echo "$next"
}

# -----------------------------------------------------------------------------
# CHANGELOG + README management
# -----------------------------------------------------------------------------
update_changelog_for_release() {
  local release_version="$1"
  local release_notes_file="$2"
  local dry_run="$3"

  if [[ "$dry_run" == "true" ]]; then
    echo "[DRY-RUN] Would update CHANGELOG.md: move 'Unreleased' to '${release_version}' section." >&2
    return
  fi

  python3 - "$release_version" "$release_notes_file" <<'PY'
import datetime, sys, re
from pathlib import Path

release_version = sys.argv[1]
release_notes_path = Path(sys.argv[2])
changelog = Path("CHANGELOG.md")
text = changelog.read_text()

# Split sections by headings
headings = list(re.finditer(r'^##\s+.*$', text, flags=re.MULTILINE))
if not headings:
    raise SystemExit("CHANGELOG.md missing headings")

sections = []
for idx, match in enumerate(headings):
    start = match.start()
    end = headings[idx + 1].start() if idx + 1 < len(headings) else len(text)
    heading = match.group().strip()
    sections.append((heading, text[match.end():end]))

unreleased_idx = next((i for i,(h,_) in enumerate(sections) if h == "## Unreleased"), None)
if unreleased_idx is None:
    raise SystemExit("Missing '## Unreleased' section in CHANGELOG.md")

unreleased_content = sections[unreleased_idx][1].strip()
if not unreleased_content:
    unreleased_content = "_No notable changes recorded._"

sections[unreleased_idx] = ("## Unreleased", "\n\n_No changes yet._\n\n")
release_heading = f"## {release_version} ({datetime.date.today():%Y-%m-%d})"
sections.insert(unreleased_idx + 1, (release_heading, f"\n\n{unreleased_content}\n\n"))

changelog.write_text("\n".join(f"{h}\n{b.strip()}" for h,b in sections) + "\n")

# Write standalone release notes file
release_notes_path.parent.mkdir(parents=True, exist_ok=True)
release_notes_path.write_text(f"{release_heading}\n\n{unreleased_content.strip()}\n")
PY
}

ensure_unreleased_placeholder() {
  local dry_run="$1"
  if [[ "$dry_run" == "true" ]]; then
    echo "[DRY-RUN] Would ensure CHANGELOG.md contains placeholder under 'Unreleased'." >&2
    return
  fi

  python3 - <<'PY'
from pathlib import Path
p = Path("CHANGELOG.md")
t = p.read_text()
if "## Unreleased" not in t:
    raise SystemExit("Missing ## Unreleased section in CHANGELOG.md")
if "- _No changes yet._" not in t.split("## Unreleased",1)[1]:
    t = t.replace("## Unreleased", "## Unreleased\n\n- _No changes yet._\n", 1)
    p.write_text(t)
PY
}

update_readme_versions() {
  local dry_run="$1"
  if [[ "$dry_run" == "true" ]]; then
    echo "[DRY-RUN] Would update README.md version references." >&2
    return
  fi

  perl -0pi -e 's/(<artifactId>ta4j-(?:core|examples)<\/artifactId>\s*<version>)(?![^<]*-SNAPSHOT)([^<]+)(<\/version>)/${1}$ENV{RELEASE_VERSION}$3/g' README.md || true
  perl -0pi -e 's/(The current \*\*\*snapshot version\*\*\* is `)[^`]+(`)/${1}$ENV{SNAPSHOT_VERSION}$2/g' README.md || true
  perl -0pi -e 's/`[0-9]+\.[0-9]+(?:\.[0-9]+)?-SNAPSHOT`/`$ENV{SNAPSHOT_VERSION}`/g' README.md || true
}

# -----------------------------------------------------------------------------
# Core workflows
# -----------------------------------------------------------------------------
run_release() {
  ensure_tools
  local release_version=""
  local next_version=""
  local dry_run="false"

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --release-version) release_version="$2"; shift 2 ;;
      --next-version) next_version="$2"; shift 2 ;;
      --dry-run) dry_run="true"; shift ;;
      -h|--help) usage; exit 0 ;;
      *) echo "Unknown option: $1" >&2; usage; exit 1 ;;
    esac
  done

  local current="$(trim "$(current_project_version)")"
  [[ -z "$release_version" ]] && release_version="${current%-SNAPSHOT}"
  [[ -z "$next_version" ]] && next_version="$(increment_version "$release_version")"

  local snapshot_version="${next_version}-SNAPSHOT"
  local release_notes_file="target/release-notes.md"

  echo "Preparing release:" >&2
  echo "  Release version: $release_version" >&2
  echo "  Next version:    $snapshot_version" >&2
  [[ "$dry_run" == "true" ]] && echo "  Mode:            DRY-RUN (no changes applied)" >&2
  echo >&2

  if [[ "$dry_run" == "true" ]]; then
    echo "[DRY-RUN] Would set Maven version to $release_version and update changelog/README." >&2
    echo "[DRY-RUN] Snapshot bump to $snapshot_version is handled separately by CI workflow." >&2
  else
    mvn -B versions:set -DnewVersion="$release_version" >&2
    mvn -B versions:commit >&2
  fi

  export RELEASE_VERSION="$release_version" SNAPSHOT_VERSION="$snapshot_version"
  update_changelog_for_release "$release_version" "$release_notes_file" "$dry_run"
  update_readme_versions "$dry_run"

  # Note: The snapshot version bump is handled separately by the CI workflow
  # after the release branch is created, committed, tagged, and deployed.
  # This ensures the release branch and tag contain the release version.

  echo >&2
  echo "release_version=${release_version}"
  echo "next_version=${snapshot_version}"
  echo "release_notes_file=${release_notes_file}"
}

run_snapshot() {
  ensure_tools
  local next_version=""
  local dry_run="false"

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --next-version) next_version="$2"; shift 2 ;;
      --dry-run) dry_run="true"; shift ;;
      -h|--help) usage; exit 0 ;;
      *) echo "Unknown option: $1" >&2; usage; exit 1 ;;
    esac
  done

  local current="$(trim "$(current_project_version)")"
  [[ -z "$next_version" ]] && next_version="$(increment_version "${current%-SNAPSHOT}")-SNAPSHOT"

  echo "Preparing next snapshot: $next_version" >&2
  [[ "$dry_run" == "true" ]] && echo "  Mode: DRY-RUN (no changes applied)" >&2
  echo >&2

  if [[ "$dry_run" == "true" ]]; then
    echo "[DRY-RUN] Would bump Maven version to $next_version and ensure CHANGELOG placeholder." >&2
  else
    mvn -B versions:set -DnewVersion="$next_version" >&2
    mvn -B versions:commit >&2
    ensure_unreleased_placeholder "$dry_run"
  fi

  echo >&2
  echo "next_version=${next_version}"
}

# -----------------------------------------------------------------------------
# Entry point
# -----------------------------------------------------------------------------
cmd="${1:-release}"
shift || true

case "$cmd" in
  release)
    run_release "$@"
    ;;
  snapshot)
    run_snapshot "$@"
    ;;
  -h|--help)
    usage
    ;;
  *)
    echo "Unknown command: $cmd" >&2
    usage >&2
    exit 1
    ;;
esac
