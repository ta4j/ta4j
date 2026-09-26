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

# Find matches under root, including hidden and ignored files,
# so AGENTS candidates are discovered even in filtered directories.
if command -v rg >/dev/null 2>&1; then
  file_listing() {
    rg --files --no-ignore --hidden -g "$target_glob" "$root" 2>/dev/null
  }
else
  # Portable fallback: BSD and GNU find both support -name and -path glob matching.
  # MSYS/Git Bash find emits backslash separators; normalize them so the
  # upward AGENTS.md walk sees ordinary slash-separated paths.
  # Path-shaped targets (containing "/") preserve the rg path-input contract
  # including recursive glob expressions (such as "**").
  file_listing() {
    case "$target_glob" in
      */*)
        if [ -f "$root/$target_glob" ]; then
          printf '%s\n' "$root/$target_glob"
          return
        fi
        local clean_glob="${target_glob#./}"
        case "$clean_glob" in
          *\*\*/*)
            local prefix="${clean_glob%%\*\*/*}"
            local suffix="${clean_glob#*\*\*/*}"
            prefix="${prefix%/}"
            if [ -n "$prefix" ]; then
              local p0="./$prefix/$suffix"
              local p1="./$prefix/*/$suffix"
              local p2="./$prefix/*/*/$suffix"
              local p3="./$prefix/*/*/*/$suffix"
              local p4="./$prefix/*/*/*/*/$suffix"
            else
              local p0="./$suffix"
              local p1="./*/$suffix"
              local p2="./*/*/$suffix"
              local p3="./*/*/*/$suffix"
              local p4="./*/*/*/*/$suffix"
            fi
            (
              cd "$root" || exit
              find . -type f \( -path "$p0" -o -path "$p1" -o -path "$p2" -o -path "$p3" -o -path "$p4" \) 2>/dev/null \
                | sed "s|^\./|$root/|" \
                | tr '\\' '/'
            )
            ;;
          *)
            local dir_prefix="${clean_glob%/*}"
            local base_name="${clean_glob##*/}"
            (
              cd "$root" || exit
              if [ -d "$dir_prefix" ] && case "$dir_prefix" in *\**|*\?*) false;; *) true;; esac; then
                find "$dir_prefix" -maxdepth 1 -type f -name "$base_name" 2>/dev/null \
                  | sed "s|^|$root/|" \
                  | tr '\\' '/'
              else
                find . -type f -path "./$clean_glob" 2>/dev/null \
                  | sed "s|^\./|$root/|" \
                  | tr '\\' '/'
              fi
            )
            ;;
        esac
        ;;
      *)
        find "$root" -type f -name "$target_glob" 2>/dev/null | tr '\\' '/'
        ;;
    esac
  }
fi

file_listing \
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
