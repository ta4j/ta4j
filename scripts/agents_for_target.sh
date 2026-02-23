#!/usr/bin/env sh
set -eu

target_glob="${1:-}"
if [ -z "$target_glob" ]; then
  echo "Usage: $(basename "$0") <target_filename_or_glob>" >&2
  exit 2
fi

workspace_root() {
  # Prefer git root if available
  if command -v git >/dev/null 2>&1; then
    root="$(git rev-parse --show-toplevel 2>/dev/null || true)"
    if [ -n "$root" ]; then
      echo "$root"
      return
    fi
  fi

  # Otherwise, walk up looking for common project sentinels
  d="$PWD"
  while [ "$d" != "/" ]; do
    if [ -d "$d/.git" ] || \
       [ -f "$d/pyproject.toml" ] || \
       [ -f "$d/package.json" ] || \
       [ -f "$d/pom.xml" ] || \
       [ -f "$d/build.gradle" ] || \
       [ -f "$d/go.mod" ] || \
       [ -f "$d/Cargo.toml" ]; then
      echo "$d"
      return
    fi
    d="$(dirname "$d")"
  done

  # Fallback: current directory
  echo "$PWD"
}

root="$(workspace_root)"

# If rg isn't available, fail fast with a clear message
if ! command -v rg >/dev/null 2>&1; then
  echo "Error: ripgrep (rg) not found in PATH." >&2
  exit 127
fi

# Find matches under root. Respect ignore files by default.
# If you want to include ignored + hidden: add --no-ignore --hidden
rg --files --no-ignore --hidden -g "$target_glob" "$root" 2>/dev/null \
| while IFS= read -r file; do
    [ -z "$file" ] && continue
    dir="$(dirname "$file")"

    # Walk upward from the target's directory to the workspace root
    while :; do
      agents="$dir/AGENTS.md"
      if [ -f "$agents" ]; then
        echo "$agents"
      fi

      [ "$dir" = "$root" ] && break
      parent="$(dirname "$dir")"
      [ "$parent" = "$dir" ] && break
      dir="$parent"
    done
  done \
| awk '!seen[$0]++'