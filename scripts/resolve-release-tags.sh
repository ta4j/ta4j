#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# resolve-release-tags.sh
#
# Emits release-tag state as key=value lines for the supplied target ref.
#
# Usage:
#   scripts/resolve-release-tags.sh [target-ref]
#
# Output:
#   latest_tag=<tag|none>
#   last_reachable_tag=<tag|none>
#   last_first_parent_tag=<tag|none>
#   latest_tag_reachable=<true|false|n/a>
# =============================================================================

target_ref="${1:-HEAD}"

if ! git rev-parse --verify --quiet "${target_ref}^{commit}" >/dev/null; then
  target_ref="HEAD"
fi

list_tags() {
  local merged_ref="${1:-}"

  if [ -n "$merged_ref" ]; then
    git for-each-ref --sort=-creatordate --merged "$merged_ref" --format='%(refname:strip=2)' refs/tags
  else
    git for-each-ref --sort=-creatordate --format='%(refname:strip=2)' refs/tags
  fi
}

find_preferred_release_tag() {
  local merged_ref="${1:-}"
  local selected_tag="none"
  local tag=""
  local tag_list=""

  tag_list="$(list_tags "$merged_ref")"

  if [ -n "$tag_list" ]; then
    while IFS= read -r tag; do
      case "$tag" in
        [0-9]*)
          selected_tag="$tag"
          break
          ;;
      esac
    done <<< "$tag_list"

    if [ "$selected_tag" = "none" ]; then
      while IFS= read -r tag; do
        case "$tag" in
          v[0-9]*)
            selected_tag="$tag"
            break
            ;;
        esac
      done <<< "$tag_list"
    fi
  fi

  printf '%s\n' "$selected_tag"
}

describe_first_parent_release_tag() {
  local ref="$1"
  local tag=""

  tag=$(git describe --tags --abbrev=0 --first-parent --match '[0-9]*' "$ref" 2>/dev/null || true)
  if [ -z "$tag" ]; then
    tag=$(git describe --tags --abbrev=0 --first-parent --match 'v[0-9]*' "$ref" 2>/dev/null || true)
  fi

  if [ -z "$tag" ]; then
    printf 'none\n'
    return 0
  fi

  printf '%s\n' "$tag"
}

latest_tag="$(find_preferred_release_tag)"
last_reachable_tag="$(find_preferred_release_tag "$target_ref")"
last_first_parent_tag="$(describe_first_parent_release_tag "$target_ref")"
latest_tag_reachable="n/a"

if [ "$latest_tag" != "none" ]; then
  if git merge-base --is-ancestor "$latest_tag" "$target_ref"; then
    latest_tag_reachable="true"
  else
    latest_tag_reachable="false"
  fi
fi

printf 'latest_tag=%s\n' "$latest_tag"
printf 'last_reachable_tag=%s\n' "$last_reachable_tag"
printf 'last_first_parent_tag=%s\n' "$last_first_parent_tag"
printf 'latest_tag_reachable=%s\n' "$latest_tag_reachable"
