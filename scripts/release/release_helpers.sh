#!/usr/bin/env bash
set -euo pipefail

SNAPSHOT_REPOSITORY_URL="https://central.sonatype.com/repository/maven-snapshots/"
SNAPSHOT_METADATA_URL="${SNAPSHOT_REPOSITORY_URL}org/ta4j/ta4j-parent/maven-metadata.xml"
SNAPSHOT_REPOSITORY_ID="central-portal-snapshots"
SNAPSHOT_WORKFLOW_NAME="Publish Snapshot to Maven Central"
MAVEN_DEPENDENCY_PLUGIN_VERSION="3.11.0"
SNAPSHOT_CONSUMPTION_DEADLINE_SECONDS=300

TMP_HELPER_PATHS=()
cleanup_release_helper_tmps() {
  local path
  if (( ${#TMP_HELPER_PATHS[@]} == 0 )); then
    return 0
  fi
  for path in "${TMP_HELPER_PATHS[@]}"; do
    [[ -n "$path" && -e "$path" ]] && rm -rf "$path"
  done
}
trap cleanup_release_helper_tmps EXIT

new_tmp_file() {
  local path
  path="$(mktemp "${TMPDIR:-/tmp}/release-helper.XXXXXX")"
  TMP_HELPER_PATHS+=("$path")
  printf '%s\n' "$path"
}

new_tmp_dir() {
  local path
  path="$(mktemp -d "${TMPDIR:-/tmp}/release-helper.XXXXXX")"
  TMP_HELPER_PATHS+=("$path")
  printf '%s\n' "$path"
}

usage() {
  cat >&2 <<'EOF'
Usage: release_helpers.sh <command> [options]

Commands:
  signing-preflight
  build-dossier
  last-release-date
  release-pr-review-plan
  javadoc-warnings
  artifact-manifest
  snapshot-publication
  snapshot-publication-policy
  snapshot-consumption
EOF
}

die() {
  echo "::error::$*" >&2
  exit 1
}

command_signing_preflight() (
  [[ $# -eq 0 ]] || die "signing-preflight takes no arguments"
  [[ -n "${GPG_PASSPHRASE:-}" ]] || die "OpenPGP signing preflight: GPG_PASSPHRASE is missing. Restore the signing secret; if the key is expired or unusable, renew the existing OpenPGP key and update the secret and public key. See RELEASE_PROCESS.md."

  local tmpdir payload signature status fingerprint
  tmpdir="$(mktemp -d "${TMPDIR:-/tmp}/release-signing.XXXXXX")"
  trap 'rm -rf "$tmpdir"' EXIT
  trap 'exit 1' HUP INT TERM
  payload="$tmpdir/payload"
  signature="$tmpdir/payload.sig"
  printf '%s\n' 'ta4j Maven signing preflight' > "$payload"

  if ! printf '%s\n' "$GPG_PASSPHRASE" | gpg --batch --no-tty --yes \
    --pinentry-mode loopback --passphrase-fd 0 --output "$signature" \
    --detach-sign "$payload" >/dev/null 2>&1; then
    die "OpenPGP signing preflight failed: the imported signing key is missing, expired or unusable, or GPG_PASSPHRASE is incorrect. Check the key and passphrase; renew the existing OpenPGP key if expired and update the secret and public key. See RELEASE_PROCESS.md."
  fi
  if ! status="$(gpg --batch --no-tty --status-fd 1 --verify "$signature" "$payload" 2>/dev/null)"; then
    die "OpenPGP signing preflight could not verify its detached signature. Check the imported key; renew the existing OpenPGP key if expired and update the secret and public key. See RELEASE_PROCESS.md."
  fi
  if printf '%s\n' "$status" | grep -Eq '^\[GNUPG:\] (EXPKEYSIG|EXPSIG|REVKEYSIG|KEYEXPIRED|SIGEXPIRED)( |$)'; then
    die "OpenPGP signing preflight found an expired or revoked key or signature. Renew the existing OpenPGP key if expired and update the secret and public key; investigate revocation. See RELEASE_PROCESS.md."
  fi
  fingerprint="$(printf '%s\n' "$status" | awk '$1 == "[GNUPG:]" && $2 == "VALIDSIG" { count++; fingerprint=$3 } END { if (count == 1) print fingerprint }')"
  if [[ ! "$fingerprint" =~ ^[[:xdigit:]]+$ ]] || { [[ ${#fingerprint} -ne 40 ]] && [[ ${#fingerprint} -ne 64 ]]; }; then
    die "OpenPGP signing preflight did not receive a valid signing fingerprint. Check the imported key and verification setup. See RELEASE_PROCESS.md."
  fi
  append_output "signing_fingerprint" "$fingerprint"
  printf 'audit:signing_preflight=passed signing_fingerprint=%s\n' "$fingerprint"
)

require_value() {
  local option="$1"
  local value="${2:-}"
  [[ -n "$value" ]] || die "${option} requires a value"
}

write_json() {
  local output="$1"
  mkdir -p "$(dirname "$output")"
  jq -S . > "$output"
}

append_output() {
  local name="$1"
  local value="$2"
  local output_path="${3:-${GITHUB_OUTPUT:-}}"
  local delimiter
  [[ -n "$output_path" ]] || return 0
  if [[ "$value" == *$'\n'* ]]; then
    delimiter="EOF_${name}_$$"
    {
      printf '%s<<%s\n' "$name" "$delimiter"
      printf '%s\n' "$value"
      printf '%s\n' "$delimiter"
    } >> "$output_path"
  else
    printf '%s=%s\n' "$name" "$value" >> "$output_path"
  fi
}

redact_text() {
  # Redact identifiable credentials, not arbitrary long words: Java paths and
  # commit IDs are evidence, and ta4j paths commonly match generic token regexes.
  perl -0777 -pe '
    s#-----BEGIN ([A-Z ]*PRIVATE KEY(?: BLOCK)?)-----.*?-----END \1-----#[REDACTED_PRIVATE_KEY]#sg;
    s#\b(?:gh[oprsu]_|github_pat_)[A-Za-z0-9_]{20,}#[REDACTED_TOKEN]#g;
    s#\bsk-[A-Za-z0-9_-]{16,}#[REDACTED_OPENAI_KEY]#g;
    s#\b(?:AKIA|ASIA)[A-Z0-9]{16}\b#[REDACTED_ACCESS_KEY]#g;
    s#(\b(?:proxy-)?authorization["\x27]?[ \t]*[:=,][ \t]*["\x27]?basic[ \t]+)[A-Za-z0-9+/]+=*#${1}[REDACTED_CREDENTIALS]#gi;
    s#\b(Bearer[ \t]+)[A-Za-z0-9._~+/-]+=*#${1}[REDACTED_TOKEN]#gi;
    s#\b(https?://)[^\s/@]+:[^\s/@]+\@#${1}[REDACTED_CREDENTIALS]\@#gi;
    s#([?&](?:access_token|api_key|key|token|password|secret|signature|sig)=)[^&\x23\s"<>]+#${1}[REDACTED_SECRET]#gi;
    s#((?:api[_-]?key|access[_-]?token|auth[_-]?token|client[_-]?secret|password|passwd|secret[_-]?key)["\x27]?[ \t]*[:=][ \t]*)(?:"[^"\r\n]*"|\x27[^\x27\r\n]*\x27|[^\s,;<>]+)#${1}[REDACTED_SECRET]#gi;
  '
}

redact_log_text() {
  perl -pe 's#https?://\S+#[REDACTED_URL]#g; s#gh[oprsu]_[A-Za-z0-9_]{20,}#[REDACTED_TOKEN]#g'
}

iso_utc_now() {
  date -u '+%Y-%m-%dT%H:%M:%S+00:00'
}

file_size_bytes() {
  local path="$1"
  if [[ -e "$path" ]]; then
    wc -c < "$path" | tr -d ' '
  else
    printf '0'
  fi
}

copy_prefix_with_notice() {
  local input="$1"
  local output="$2"
  local max_chars="$3"
  local notice="$4"
  local size
  size="$(file_size_bytes "$input")"
  if (( size > max_chars )); then
    head -c "$max_chars" "$input" > "$output"
    printf '\n\n%s\n' "$notice" >> "$output"
  else
    cp "$input" "$output"
  fi
}

category_for() {
  local path="$1"
  if [[ "$path" == "pom.xml" || "$path" == */pom.xml ]]; then
    printf 'build metadata'
  elif [[ "/$path" == *"/src/main/"* ]]; then
    printf 'production code'
  elif [[ "/$path" == *"/src/test/"* ]]; then
    printf 'tests'
  elif [[ "$path" == .github/workflows/* ]]; then
    printf 'workflows'
  elif [[ "$path" == scripts/* ]]; then
    if [[ "$path" == *release* || "$path" == scripts/tests/* ]]; then
      printf 'release/tooling scripts'
    else
      printf 'scripts'
    fi
  elif [[ "$path" == "CHANGELOG.md" || "$path" == release/* || "$path" == "RELEASE_PROCESS.md" || "$path" == "README.md" ]]; then
    printf 'release documentation'
  else
    printf 'other'
  fi
}

extract_unreleased_changelog() {
  if [[ ! -f CHANGELOG.md ]]; then
    printf '(CHANGELOG.md not found)\n'
    return 0
  fi
  awk '
    /^##[[:space:]]+\[?Unreleased\]?/ { capture=1; next }
    capture && /^##[[:space:]]+/ { exit }
    capture { print }
  ' CHANGELOG.md | sed -e '/./,$!d' -e ':a' -e '/^\n*$/{$d;N;ba' -e '}'
}

changed_files_since() {
  local last_tag="$1"
  if [[ "$last_tag" == "none" ]]; then
    git ls-tree -rz --name-only HEAD
  else
    # Both sides of a move matter: moving a public class into tests removes it
    # from the production artifact even when Git recognizes an exact rename.
    git diff --no-renames --name-only -z "${last_tag}..HEAD"
  fi | python3 -c '
import sys
paths = [path for path in sys.stdin.buffer.read().split(b"\0") if path]
if any(any(character in path for character in (b"\n", b"\r", b"\t")) for path in paths):
    raise SystemExit("Release evidence does not support paths containing tabs or line breaks")
if paths:
    sys.stdout.buffer.write(b"\n".join(sorted(paths)) + b"\n")
'
}

public_api_signals() {
  local input="$1"
  awk '/^[+-][[:space:]]*(public|protected)[[:space:]]+/ && !seen[$0]++ {
    print
    count++
    if (count >= 80) { print "[TRUNCATED: more public API signal lines omitted]"; exit }
  }' "$input"
}

javadoc_signals() {
  local input="$1"
  awk '
    {
      line=$0
      sub(/^[[:space:]]+/, "", line)
      if (line !~ /^[+-]/) next
      if (line ~ /@since/ || line ~ /\/\*\*/ || line ~ /^[+-][[:space:]]+\*/) {
        print line
        count++
        if (count >= 80) {
          print "[TRUNCATED: more Javadoc signal lines omitted]"
          exit
        }
      }
    }
  ' "$input"
}

command_last_release_date() {
  local tag=""
  while (($#)); do
    case "$1" in
      --tag) require_value "$1" "${2:-}"; tag="$2"; shift 2 ;;
      *) die "Unknown last-release-date option: $1" ;;
    esac
  done
  [[ -n "$tag" ]] || die "--tag is required"
  if [[ "$tag" == "none" ]]; then
    printf 'none\n'
    return 0
  fi
  if ! git rev-parse --verify --quiet "refs/tags/${tag}" >/dev/null 2>&1; then
    die "Tag ${tag} cannot be resolved in this repository; refusing to fabricate a release date"
  fi
  if [[ "$(git cat-file -t "refs/tags/${tag}")" != "tag" ]]; then
    die "Tag ${tag} is a lightweight tag; a release date requires an annotated tag"
  fi
  local release_date
  release_date="$(git for-each-ref "refs/tags/${tag}" --format='%(creatordate:short)')"
  if [[ -z "$release_date" ]]; then
    die "Tag ${tag} has no resolvable creation date"
  fi
  printf '%s\n' "$release_date"
}

select_dossier_diff() {
  # Category batches make the ordering real: git sorts pathspec arguments and
  # therefore a single git diff cannot implement production-first sampling.
  # Weighted allocation and a per-file cap retain breadth in each category.
  python3 - "$1" "$2" "$3" "$4" <<'PY'
import json
from pathlib import Path
import re
import sys

manifest, output, coverage_output, limit = sys.argv[1:]
limit = int(limit)
weights = {"production code": 70, "build metadata": 10, "tests": 10,
           "release documentation": 4, "workflows": 2,
           "release/tooling scripts": 2, "scripts": 1, "other": 1}
per_file_limit = 24000
categories = []
for entry in Path(manifest).read_text(encoding="utf-8").splitlines():
    category, filename = entry.split("\t", 1)
    raw = Path(filename).read_bytes()
    sections = [section for section in re.split(rb"(?=^diff --git )", raw, flags=re.M) if section]
    categories.append((category, sections))


def allocate(demands, priorities, budget):
    allocations = [0] * len(demands)
    active = {index for index, demand in enumerate(demands) if demand}
    while budget and active:
        total_weight = sum(priorities[index] for index in active)
        remaining = budget
        for index in sorted(active):
            share = max(1, remaining * priorities[index] // total_weight)
            grant = min(share, demands[index] - allocations[index], budget)
            allocations[index] += grant
            budget -= grant
        active = {index for index in active if allocations[index] < demands[index]}
    return allocations


demands = [sum(min(len(section), per_file_limit) for section in sections)
           for _, sections in categories]
budgets = allocate(demands, [weights[category] for category, _ in categories], limit)
coverage = {"strategy": "production-first-balanced-v1", "available": True, "source_byte_budget": limit,
            "per_file_byte_limit": per_file_limit, "categories": {}}
chunks = []
for (category, sections), budget in zip(categories, budgets):
    allocations = allocate([min(len(section), per_file_limit) for section in sections],
                           [1] * len(sections), budget)
    included = truncated = emitted_bytes = 0
    for section, allowance in zip(sections, allocations):
        if len(section) <= allowance:
            excerpt = section
        elif allowance >= 80:
            notice = b"\n[TRUNCATED: remaining file diff omitted]\n"
            excerpt = section[:allowance - len(notice)].decode("utf-8", errors="ignore").encode("utf-8") + notice
            truncated += 1
        else:
            continue
        chunks.append(excerpt)
        included += 1
        emitted_bytes += len(excerpt)
    coverage["categories"][category] = {
        "available_files": len(sections), "included_files": included,
        "omitted_files": len(sections) - included, "truncated_files": truncated,
        "available_bytes": sum(len(section) for section in sections),
        "included_bytes": emitted_bytes,
    }
coverage["truncated"] = any(row["truncated_files"] or row["omitted_files"]
                            for row in coverage["categories"].values())
if not chunks and coverage["truncated"]:
    chunks.append(b"[TRUNCATED: no file diff fits the selected budget]\n"[:limit])
Path(output).write_bytes(b"".join(chunks))
Path(coverage_output).write_text(json.dumps(coverage, indent=2) + "\n", encoding="utf-8")
PY
}

command_build_dossier() {
  local last_tag="" current_version="" pom_base="" max_diff_chars=600000 output="release-dossier.md" audit_output="release-audit.json"
  while (($#)); do
    case "$1" in
      --last-tag) require_value "$1" "${2:-}"; last_tag="$2"; shift 2 ;;
      --current-version) require_value "$1" "${2:-}"; current_version="$2"; shift 2 ;;
      --pom-base) require_value "$1" "${2:-}"; pom_base="$2"; shift 2 ;;
      --max-diff-chars) require_value "$1" "${2:-}"; max_diff_chars="$2"; shift 2 ;;
      --output) require_value "$1" "${2:-}"; output="$2"; shift 2 ;;
      --audit-output) require_value "$1" "${2:-}"; audit_output="$2"; shift 2 ;;
      *) die "Unknown build-dossier option: $1" ;;
    esac
  done
  [[ -n "$last_tag" && -n "$current_version" && -n "$pom_base" ]] || die "--last-tag, --current-version, and --pom-base are required"
  [[ "$max_diff_chars" =~ ^[0-9]+$ ]] && (( max_diff_chars > 0 )) || die "--max-diff-chars must be a positive integer"

  local tmpdir files_file categories_file redacted_diff api_file doc_file test_file changelog_file generated_at diff_truncated selected_diff_chars changed_file_count
  local diff_manifest coverage_file signal_diff category batch index=0
  tmpdir="$(new_tmp_dir)"
  files_file="$tmpdir/files.txt"
  categories_file="$tmpdir/categories.tsv"
  redacted_diff="$tmpdir/redacted-diff.txt"
  diff_manifest="$tmpdir/diff-manifest.tsv"
  coverage_file="$tmpdir/diff-coverage.json"
  signal_diff="$tmpdir/signal-diff.txt"
  api_file="$tmpdir/api.txt"
  doc_file="$tmpdir/doc.txt"
  test_file="$tmpdir/tests.txt"
  changelog_file="$tmpdir/changelog.txt"

  changed_files_since "$last_tag" > "$files_file"
  : > "$categories_file"
  : > "$diff_manifest"
  : > "$signal_diff"
  while IFS= read -r path; do
    [[ -n "$path" ]] || continue
    printf '%s\t%s\n' "$(category_for "$path")" "$path" >> "$categories_file"
  done < "$files_file"

  if [[ "$last_tag" == "none" || ! -s "$files_file" ]]; then
    if [[ "$last_tag" == "none" ]]; then
      printf '(No prior release tag diff available.)\n' > "$redacted_diff"
      printf '{"strategy":"production-first-balanced-v1","available":false,"categories":{},"truncated":false}\n' > "$coverage_file"
    else
      printf '(No changed files since the release baseline.)\n' > "$redacted_diff"
      printf '{"strategy":"production-first-balanced-v1","available":true,"categories":{},"truncated":false}\n' > "$coverage_file"
    fi
    diff_truncated=false
  else
    local path_args=()
    for category in 'production code' 'build metadata' 'tests' 'release documentation' 'workflows' 'release/tooling scripts' 'scripts' 'other'; do
      path_args=()
      while IFS= read -r path; do
        [[ -n "$path" ]] && path_args+=("$path")
      done < <(awk -F '\t' -v category="$category" '$1 == category { print $2 }' "$categories_file")
      (( ${#path_args[@]} )) || continue
      batch="$tmpdir/diff-${index}.txt"
      if ! git --literal-pathspecs diff --no-color --no-ext-diff --no-textconv --no-renames --unified=8 \
        "${last_tag}..HEAD" -- "${path_args[@]}" > "$tmpdir/raw-diff.txt" 2>"$tmpdir/diff.err"; then
        die "Could not collect the release diff; assessment evidence is incomplete."
      fi
      redact_text < "$tmpdir/raw-diff.txt" > "$batch"
      printf '%s\t%s\n' "$category" "$batch" >> "$diff_manifest"
      if [[ "$category" == 'production code' ]]; then
        cat "$batch" >> "$signal_diff"
      fi
      index=$((index + 1))
    done
    select_dossier_diff "$diff_manifest" "$redacted_diff" "$coverage_file" "$max_diff_chars"
    diff_truncated="$(jq -r '.truncated' "$coverage_file")"
  fi

  # Signals scan all changed production lines before excerpts are bounded.
  public_api_signals "$signal_diff" > "$api_file"
  javadoc_signals "$signal_diff" > "$doc_file"
  awk -F '\t' '$1 == "tests" { print $2 }' "$categories_file" | redact_text > "$test_file"
  extract_unreleased_changelog | redact_text > "$changelog_file"

  generated_at="$(iso_utc_now)"
  changed_file_count="$(grep -c . "$files_file" || true)"
  selected_diff_chars="$(file_size_bytes "$redacted_diff")"
  local category_counts_json
  category_counts_json="$(cut -f1 "$categories_file" | jq -Rn '[inputs] | group_by(.) | map({(.[0]): length}) | add // {}')"
  jq -S -n \
    --arg generated_at "$generated_at" \
    --arg last_tag "$last_tag" \
    --arg current_version "$current_version" \
    --arg pom_base "$pom_base" \
    --argjson changed_file_count "$changed_file_count" \
    --argjson category_counts "$category_counts_json" \
    --argjson selected_diff_chars "$selected_diff_chars" \
    --argjson selected_diff_truncated "$diff_truncated" \
    --slurpfile diff_coverage "$coverage_file" \
    --argjson public_api_signal_count "$(grep -c . "$api_file" || true)" \
    --argjson javadoc_signal_count "$(grep -c . "$doc_file" || true)" \
    --argjson test_file_count "$(grep -c . "$test_file" || true)" \
    '{
      generated_at: $generated_at,
      last_tag: $last_tag,
      current_version: $current_version,
      pom_base: $pom_base,
      changed_file_count: $changed_file_count,
      category_counts: $category_counts,
      selected_diff_chars: $selected_diff_chars,
      selected_diff_truncated: $selected_diff_truncated,
      diff_coverage: $diff_coverage[0],
      signal_scope: "all changed production lines before diff sampling; each signal list is capped at 80 lines",
      public_api_signal_count: $public_api_signal_count,
      javadoc_signal_count: $javadoc_signal_count,
      test_file_count: $test_file_count
    }' > "$audit_output"

  {
    printf '# ta4j Release Dossier\n\n'
    printf '## Metadata\n\n'
    printf -- '- generated_at: %s\n' "$generated_at"
    printf -- '- current_version: %s\n' "$current_version"
    printf -- '- pom_base: %s\n' "$pom_base"
    printf -- '- last_reachable_tag: %s\n' "$last_tag"
    printf -- '- changed_file_count: %s\n\n' "$changed_file_count"
    printf '## Changed Files by Category\n\n'
    cut -f1 "$categories_file" | sort -u | while IFS= read -r category; do
      [[ -n "$category" ]] || continue
      local count
      count="$(awk -F '\t' -v category="$category" '$1 == category { count++ } END { print count + 0 }' "$categories_file")"
      printf '### %s (%s)\n' "$category" "$count"
      awk -F '\t' -v category="$category" '$1 == category { printf "- `%s`\n", $2 }' "$categories_file" | redact_text
      printf '\n'
    done
    printf '## Unreleased Changelog Context\n\n```markdown\n'
    cat "$changelog_file"
    printf '\n```\n\n## Public API Signals\n\n'
    if [[ -s "$api_file" ]]; then
      sed 's/.*/- `&`/' "$api_file"
    else
      printf -- '- (none detected)\n'
    fi
    printf '\n## Javadoc and @since Signals\n\n'
    if [[ -s "$doc_file" ]]; then
      sed 's/.*/- `&`/' "$doc_file"
    else
      printf -- '- (none detected)\n'
    fi
    printf '\n## Test File Signals\n\n'
    if [[ -s "$test_file" ]]; then
      sed 's/.*/- `&`/' "$test_file"
    else
      printf -- '- (none detected)\n'
    fi
    printf '\n## Evidence Coverage\n\n'
    printf 'Diff excerpts are ordered by production, build, tests, documentation, then tooling. Large files share a bounded category budget; omitted excerpts are missing evidence, not proof that a change is safe.\n\n'
    printf 'API and Javadoc signals scan all changed production lines before excerpt sampling; each signal list is capped at 80 lines.\n\n'
    printf '| Category | Available files | Included | Omitted | Truncated | Available bytes | Included bytes |\n'
    printf '|---|---:|---:|---:|---:|---:|---:|\n'
    jq -r '.categories | to_entries[] | "| \(.key) | \(.value.available_files) | \(.value.included_files) | \(.value.omitted_files) | \(.value.truncated_files) | \(.value.available_bytes) | \(.value.included_bytes) |"' "$coverage_file"
    if [[ "$last_tag" == "none" ]]; then
      printf '\nNo prior release baseline is available; release-diff coverage is unavailable.\n'
    fi
    printf '\n## Selected Diff\n\n```diff\n'
    cat "$redacted_diff"
    printf '\n```\n'
  } > "$output"

  printf 'audit:release_dossier file=%s changed_files=%s selected_diff_chars=%s selected_diff_truncated=%s\n' \
    "$output" "$changed_file_count" "$selected_diff_chars" "$diff_truncated"
  append_output "dossier_path" "$output"
  append_output "audit_path" "$audit_output"
  append_output "changed_file_count" "$changed_file_count"
  append_output "selected_diff_chars" "$selected_diff_chars"
  append_output "selected_diff_truncated" "$diff_truncated"
}

trim_text() {
  sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//'
}

lower_text() {
  tr '[:upper:]' '[:lower:]'
}

split_review_targets() {
  local value="$1"
  printf '%s' "$value" | tr ',[:space:]' '\n' | awk 'NF { print }'
}

json_array_from_lines() {
  local input="$1"
  jq -R -s 'split("\n") | map(select(length > 0))' "$input"
}

json_skipped_reviewers() {
  local input="$1"
  jq -R -s '
    split("\n")
    | map(select(length > 0))
    | map(split("\t") | {login: .[0], reason: .[1]})
  ' "$input"
}

command_release_pr_review_plan() {
  local release_owner="" pr_author="" reviewers_value="" team_reviewers_value="" output="release-review-plan.json" github_output=""
  while (($#)); do
    case "$1" in
      --release-owner) require_value "$1" "${2:-}"; release_owner="$2"; shift 2 ;;
      --pr-author) require_value "$1" "${2:-}"; pr_author="$2"; shift 2 ;;
      --reviewers) reviewers_value="${2-}"; shift 2 ;;
      --team-reviewers) team_reviewers_value="${2-}"; shift 2 ;;
      --output) require_value "$1" "${2:-}"; output="$2"; shift 2 ;;
      --github-output) require_value "$1" "${2:-}"; github_output="$2"; shift 2 ;;
      *) die "Unknown release-pr-review-plan option: $1" ;;
    esac
  done
  [[ -n "$release_owner" ]] || die "--release-owner is required"
  [[ -n "$pr_author" ]] || die "--pr-author is required"

  local owner author author_key tmpdir candidates_file reviewers_file skipped_file team_candidates_file team_reviewers_file
  owner="$(printf '%s' "$release_owner" | trim_text)"
  author="$(printf '%s' "$pr_author" | trim_text)"
  author_key="$(printf '%s' "$author" | lower_text)"
  tmpdir="$(new_tmp_dir)"
  candidates_file="$tmpdir/reviewer-candidates.txt"
  reviewers_file="$tmpdir/reviewers.txt"
  skipped_file="$tmpdir/skipped-reviewers.tsv"
  team_candidates_file="$tmpdir/team-reviewer-candidates.txt"
  team_reviewers_file="$tmpdir/team-reviewers.txt"
  : > "$reviewers_file"
  : > "$skipped_file"
  : > "$team_reviewers_file"

  split_review_targets "$reviewers_value" > "$candidates_file"
  if [[ ! -s "$candidates_file" && -n "$owner" ]]; then
    printf '%s\n' "$owner" > "$candidates_file"
  fi
  awk -v author_key="$author_key" -v skipped_file="$skipped_file" '
    function lower(value) { return tolower(value) }
    NF && !seen[lower($0)]++ {
      if (author_key != "" && lower($0) == author_key) {
        printf "%s\tmatches PR author\n", $0 >> skipped_file
      } else {
        print
      }
    }
  ' "$candidates_file" > "$reviewers_file"

  split_review_targets "$team_reviewers_value" > "$team_candidates_file"
  awk '
    function lower(value) { return tolower(value) }
    NF && !seen[lower($0)]++ { print }
  ' "$team_candidates_file" > "$team_reviewers_file"

  local reviewers_json team_reviewers_json skipped_reviewers_json has_review_targets warning
  reviewers_json="$(json_array_from_lines "$reviewers_file")"
  team_reviewers_json="$(json_array_from_lines "$team_reviewers_file")"
  skipped_reviewers_json="$(json_skipped_reviewers "$skipped_file")"
  if [[ -s "$reviewers_file" || -s "$team_reviewers_file" ]]; then
    has_review_targets=true
    warning=""
  else
    has_review_targets=false
    warning="No eligible release PR reviewers remain after filtering PR author ${author:-unknown}. Configure RELEASE_REVIEWERS or RELEASE_REVIEW_TEAMS with a non-author reviewer/team to avoid an admin or bypass merge."
  fi

  jq -S -n \
    --arg releaseOwner "$owner" \
    --arg prAuthor "$author" \
    --arg warning "$warning" \
    --argjson reviewers "$reviewers_json" \
    --argjson teamReviewers "$team_reviewers_json" \
    --argjson skippedReviewers "$skipped_reviewers_json" \
    --argjson hasReviewTargets "$has_review_targets" \
    '{
      releaseOwner: $releaseOwner,
      prAuthor: $prAuthor,
      reviewers: $reviewers,
      teamReviewers: $teamReviewers,
      skippedReviewers: $skippedReviewers,
      hasReviewTargets: $hasReviewTargets,
      warning: $warning
    }' | write_json "$output"

  append_output "reviewers_json" "$(jq -c '.reviewers' "$output")" "$github_output"
  append_output "team_reviewers_json" "$(jq -c '.teamReviewers' "$output")" "$github_output"
  append_output "has_review_targets" "$(jq -r '.hasReviewTargets' "$output")" "$github_output"
  append_output "warning" "$warning" "$github_output"
  printf 'audit:release_pr_review_plan reviewers=%s team_reviewers=%s skipped=%s output=%s\n' \
    "$(jq '.reviewers | length' "$output")" \
    "$(jq '.teamReviewers | length' "$output")" \
    "$(jq '.skippedReviewers | length' "$output")" \
    "$output"
  if [[ -n "$warning" ]]; then
    printf '::warning::%s\n' "$warning"
  fi
}

normalize_javadoc_warning_stream() {
  perl -ne '
    chomp;
    s/^\s+|\s+$//g;
    s/^\[[A-Z]+\]\s*//;
    next if $_ eq "" || $_ eq "Javadoc Warnings";
    my $lower = lc($_);
    my $is_javadoc_warning = (($lower =~ /javadoc/ && $lower =~ /warning/) || ($lower =~ /\bwarning\s*[-:]/));
    next unless $is_javadoc_warning;
    next if /\.java:\[\d+,\d+\]/;
    if (/^.*?(ta4j-(?:core|examples)\/src\/.+)$/) { $_ = $1; }
    s/(\.java):\d+:\s+warning:/$1: warning:/;
    print "$_\n";
  ' | redact_log_text
}

command_javadoc_warnings() {
  local baseline="scripts/release/javadoc-warning-baseline.txt" output="javadoc-warnings.txt" github_output="" fail_on_new=false
  local logs=()
  while (($#)); do
    case "$1" in
      --baseline) require_value "$1" "${2:-}"; baseline="$2"; shift 2 ;;
      --output) require_value "$1" "${2:-}"; output="$2"; shift 2 ;;
      --github-output) require_value "$1" "${2:-}"; github_output="$2"; shift 2 ;;
      --fail-on-new) fail_on_new=true; shift ;;
      --) shift; break ;;
      --*) die "Unknown javadoc-warnings option: $1" ;;
      *) logs+=("$1"); shift ;;
    esac
  done

  local tmpdir current baseline_values missing new resolved
  tmpdir="$(new_tmp_dir)"
  current="$tmpdir/current.txt"
  baseline_values="$tmpdir/baseline.txt"
  missing="$tmpdir/missing.txt"
  new="$tmpdir/new.txt"
  resolved="$tmpdir/resolved.txt"
  : > "$current"
  : > "$missing"

  local log_file
  for log_file in "${logs[@]}"; do
    if [[ ! -f "$log_file" ]]; then
      printf '%s\n' "$log_file" >> "$missing"
      continue
    fi
    normalize_javadoc_warning_stream < "$log_file" >> "$current"
  done
  awk '!seen[$0]++' "$current" > "$tmpdir/current.unique"
  mv "$tmpdir/current.unique" "$current"

  if [[ -f "$baseline" ]]; then
    grep -v '^[[:space:]]*#' "$baseline" | grep -v '^[[:space:]]*$' | normalize_javadoc_warning_stream > "$baseline_values" || true
    awk '!seen[$0]++' "$baseline_values" > "$tmpdir/baseline.unique"
    mv "$tmpdir/baseline.unique" "$baseline_values"
  else
    : > "$baseline_values"
  fi
  grep -Fvx -f "$baseline_values" "$current" > "$new" || true
  grep -Fvx -f "$current" "$baseline_values" > "$resolved" || true

  {
    printf '# Javadoc Warning Baseline Check\n\n'
    printf 'baseline=%s\n' "$baseline"
    printf 'current_count=%s\n' "$(grep -c . "$current" || true)"
    printf 'baseline_count=%s\n' "$(grep -c . "$baseline_values" || true)"
    printf 'new_count=%s\n' "$(grep -c . "$new" || true)"
    printf 'resolved_count=%s\n\n' "$(grep -c . "$resolved" || true)"
    printf '## Current warnings\n'
    [[ -s "$current" ]] && cat "$current" || printf '(none)\n'
    printf '\n## New warnings\n'
    [[ -s "$new" ]] && cat "$new" || printf '(none)\n'
    printf '\n## Baseline warnings not seen in this run\n'
    [[ -s "$resolved" ]] && cat "$resolved" || printf '(none)\n'
    printf '\n## Missing logs\n'
    [[ -s "$missing" ]] && cat "$missing" || printf '(none)\n'
    printf '\n'
  } > "$output"

  append_output "javadoc_warning_count" "$(grep -c . "$current" || true)" "$github_output"
  append_output "javadoc_warning_baseline_count" "$(grep -c . "$baseline_values" || true)" "$github_output"
  append_output "javadoc_warning_new_count" "$(grep -c . "$new" || true)" "$github_output"
  append_output "javadoc_warning_resolved_count" "$(grep -c . "$resolved" || true)" "$github_output"

  printf 'audit:javadoc_warnings current=%s baseline=%s new=%s resolved=%s output=%s\n' \
    "$(grep -c . "$current" || true)" "$(grep -c . "$baseline_values" || true)" "$(grep -c . "$new" || true)" "$(grep -c . "$resolved" || true)" "$output"
  while IFS= read -r log_file; do
    [[ -n "$log_file" ]] && printf '::warning::Javadoc warning log not found: %s\n' "$log_file"
  done < "$missing"
  if [[ "$fail_on_new" == true && -s "$new" ]]; then
    while IFS= read -r warning; do
      [[ -n "$warning" ]] && printf '::error::New Javadoc warning: %s\n' "$warning" >&2
    done < "$new"
    return 1
  fi
}

command_artifact_manifest() {
  local version="" output="artifact-manifest.txt" github_output="" strict=false
  while (($#)); do
    case "$1" in
      --version) require_value "$1" "${2:-}"; version="$2"; shift 2 ;;
      --output) require_value "$1" "${2:-}"; output="$2"; shift 2 ;;
      --github-output) require_value "$1" "${2:-}"; github_output="$2"; shift 2 ;;
      --strict) strict=true; shift ;;
      *) die "Unknown artifact-manifest option: $1" ;;
    esac
  done
  [[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "Release artifact version must be major.minor.patch: $version"

  local expected_file existing_file missing_file unexpected_file
  expected_file="$(new_tmp_file)"
  existing_file="$(new_tmp_file)"
  missing_file="$(new_tmp_file)"
  unexpected_file="$(new_tmp_file)"
  cat > "$expected_file" <<EOF
ta4j-core/target/ta4j-core-${version}.jar
ta4j-core/target/ta4j-core-${version}-sources.jar
ta4j-core/target/ta4j-core-${version}-javadoc.jar
ta4j-core/target/ta4j-core-${version}-tests.jar
ta4j-examples/target/ta4j-examples-${version}.jar
ta4j-examples/target/ta4j-examples-${version}-sources.jar
ta4j-examples/target/ta4j-examples-${version}-javadoc.jar
EOF
  while IFS= read -r file; do
    [[ -e "$file" ]] || printf '%s\n' "$file"
  done < "$expected_file" | sort > "$missing_file"
  find . -path './*/target/*.jar' -type f | sed 's#^\./##' | sort > "$existing_file"
  grep -Fvx -f "$expected_file" "$existing_file" > "$unexpected_file" || true

  {
    printf 'version=%s\n\n' "$version"
    printf 'Expected release artifacts:\n'
    sed 's/^/- /' "$expected_file"
    printf '\nMissing release artifacts:\n'
    [[ -s "$missing_file" ]] && sed 's/^/- /' "$missing_file" || printf -- '- (none)\n'
    printf '\nUnexpected target jars:\n'
    [[ -s "$unexpected_file" ]] && sed 's/^/- /' "$unexpected_file" || printf -- '- (none)\n'
    printf '\n'
  } > "$output"
  append_output "files" "$(cat "$expected_file")" "$github_output"
  append_output "artifact_manifest" "$output" "$github_output"
  append_output "missing_count" "$(grep -c . "$missing_file" || true)" "$github_output"
  append_output "unexpected_count" "$(grep -c . "$unexpected_file" || true)" "$github_output"
  printf 'audit:artifact_manifest version=%s expected=%s missing=%s unexpected=%s output=%s\n' \
    "$version" "$(grep -c . "$expected_file" || true)" "$(grep -c . "$missing_file" || true)" "$(grep -c . "$unexpected_file" || true)" "$output"
  if [[ -s "$missing_file" || ( "$strict" == true && -s "$unexpected_file" ) ]]; then
    [[ -s "$missing_file" ]] && printf '::error::Missing release artifacts: %s\n' "$(paste -sd ', ' "$missing_file")" >&2
    [[ "$strict" == true && -s "$unexpected_file" ]] && printf '::error::Unexpected target jars: %s\n' "$(paste -sd ', ' "$unexpected_file")" >&2
    return 1
  fi
}

metadata_file_uri() {
  local path="$1"
  local dir base
  dir="$(cd "$(dirname "$path")" && pwd -P)"
  base="$(basename "$path")"
  printf 'file://%s/%s' "$dir" "$base"
}

write_snapshot_publication_json() {
  local output="$1" version="$2" published="$3" latest="$4" last_updated="$5" source="$6" error="$7" versions_file="${8:-}"
  local resolved_version="${9:-}" artifact_metadata_source="${10:-}" pom_url="${11:-}" jar_url="${12:-}"
  local versions_json="[]"
  if [[ -n "$versions_file" && -f "$versions_file" ]]; then
    versions_json="$(jq -R -s 'split("\n") | map(select(length > 0))' "$versions_file")"
  fi
  jq -S -n \
    --arg version "$version" \
    --arg published "$published" \
    --arg latest "$latest" \
    --arg lastUpdated "$last_updated" \
    --arg source "$source" \
    --arg error "$error" \
    --arg resolvedVersion "$resolved_version" \
    --arg artifactMetadataSource "$artifact_metadata_source" \
    --arg pomUrl "$pom_url" \
    --arg jarUrl "$jar_url" \
    --argjson versions "$versions_json" \
    '{version: $version, published: $published, latest: $latest, lastUpdated: $lastUpdated, source: $source, error: $error, versions: $versions, resolvedVersion: $resolvedVersion, artifactMetadataSource: $artifactMetadataSource, pomUrl: $pomUrl, jarUrl: $jarUrl}' > "$output"
}

emit_snapshot_publication_outputs() {
  local output="$1"
  local github_output="$2"
  append_output "snapshot_publication" "$(jq -r '.published' "$output")" "$github_output"
  append_output "snapshot_publication_latest" "$(jq -r '.latest' "$output")" "$github_output"
  append_output "snapshot_publication_last_updated" "$(jq -r '.lastUpdated' "$output")" "$github_output"
  append_output "snapshot_publication_source" "$(jq -r '.source' "$output")" "$github_output"
  append_output "snapshot_publication_error" "$(jq -r '.error' "$output")" "$github_output"
  append_output "snapshot_publication_resolved_version" "$(jq -r '.resolvedVersion' "$output")" "$github_output"
  append_output "snapshot_publication_artifact_metadata_source" "$(jq -r '.artifactMetadataSource' "$output")" "$github_output"
  append_output "snapshot_publication_pom_url" "$(jq -r '.pomUrl' "$output")" "$github_output"
  append_output "snapshot_publication_jar_url" "$(jq -r '.jarUrl' "$output")" "$github_output"
}

parse_snapshot_metadata() {
  local metadata="$1"
  local latest_file="$2"
  local last_updated_file="$3"
  local versions_file="$4"
  grep -q '<versioning>' "$metadata" && grep -q '</versioning>' "$metadata" || return 1
  sed -n 's/.*<latest>\([^<]*\)<\/latest>.*/\1/p' "$metadata" | head -n1 > "$latest_file"
  sed -n 's/.*<lastUpdated>\([^<]*\)<\/lastUpdated>.*/\1/p' "$metadata" | head -n1 > "$last_updated_file"
  sed -n 's/.*<version>\([^<]*\)<\/version>.*/\1/p' "$metadata" > "$versions_file"
}

parse_artifact_snapshot_metadata() {
  local metadata="$1"
  local output="$2"
  python3 - "$metadata" > "$output" <<'PY'
import sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1]).getroot()
version = (root.findtext("version") or "").strip()
versioning = root.find("versioning")
if versioning is None:
    raise SystemExit("snapshot artifact metadata is missing <versioning>")
last_updated = (versioning.findtext("lastUpdated") or "").strip()
values = {}
snapshot_versions = versioning.find("snapshotVersions")
if snapshot_versions is not None:
    for node in snapshot_versions.findall("snapshotVersion"):
        classifier = (node.findtext("classifier") or "").strip()
        extension = (node.findtext("extension") or "").strip()
        value = (node.findtext("value") or "").strip()
        if not classifier and extension in {"pom", "jar"} and value:
            values[extension] = value
if not version or "pom" not in values or "jar" not in values:
    raise SystemExit("snapshot artifact metadata is missing the version or unclassified pom/jar entries")
print(f"version={version}")
print(f"last_updated={last_updated}")
print(f"pom_value={values['pom']}")
print(f"jar_value={values['jar']}")
PY
}

read_key_value() {
  local file="$1"
  local key="$2"
  awk -F= -v key="$key" '$1 == key { print substr($0, length($1) + 2); exit }' "$file"
}

download_snapshot_artifact() {
  local url="$1"
  local output="$2"
  local timeout_seconds="$3"
  curl --fail --silent --show-error --location --max-time "$timeout_seconds" \
    -H "User-Agent: ta4j-release-automation" "$url" > "$output"
  [[ -s "$output" ]]
}

command_snapshot_publication() {
  local version="" metadata_url="$SNAPSHOT_METADATA_URL" metadata_file="" timeout_seconds=30 output="snapshot-publication.json" github_output=""
  local repository_url="$SNAPSHOT_REPOSITORY_URL" require_artifacts=false artifact_metadata_file="" artifact_pom_file="" artifact_jar_file=""
  while (($#)); do
    case "$1" in
      --version) require_value "$1" "${2:-}"; version="$2"; shift 2 ;;
      --metadata-url) require_value "$1" "${2:-}"; metadata_url="$2"; shift 2 ;;
      --metadata-file) require_value "$1" "${2:-}"; metadata_file="$2"; shift 2 ;;
      --repository-url) require_value "$1" "${2:-}"; repository_url="$2"; shift 2 ;;
      --require-artifacts) require_artifacts=true; shift ;;
      --artifact-metadata-file) require_value "$1" "${2:-}"; artifact_metadata_file="$2"; shift 2 ;;
      --artifact-pom-file) require_value "$1" "${2:-}"; artifact_pom_file="$2"; shift 2 ;;
      --artifact-jar-file) require_value "$1" "${2:-}"; artifact_jar_file="$2"; shift 2 ;;
      --timeout-seconds) require_value "$1" "${2:-}"; timeout_seconds="$2"; shift 2 ;;
      --output) require_value "$1" "${2:-}"; output="$2"; shift 2 ;;
      --github-output) require_value "$1" "${2:-}"; github_output="$2"; shift 2 ;;
      *) die "Unknown snapshot-publication option: $1" ;;
    esac
  done
  [[ -n "$version" ]] || die "--version is required"
  if [[ -n "$artifact_pom_file" || -n "$artifact_jar_file" ]]; then
    [[ -n "$artifact_pom_file" && -n "$artifact_jar_file" ]] || die "--artifact-pom-file and --artifact-jar-file must be provided together"
  fi

  local source metadata tmpdir latest_file last_updated_file versions_file latest last_updated published error
  local artifact_metadata artifact_values artifact_metadata_source artifact_version resolved_version artifact_last_updated pom_value jar_value pom_url jar_url artifact_pom artifact_jar
  if [[ "$version" != *-SNAPSHOT ]]; then
    source="$metadata_url"
    [[ -n "$metadata_file" ]] && source="$(metadata_file_uri "$metadata_file")"
    write_snapshot_publication_json "$output" "$version" "n/a" "" "" "$source" ""
    emit_snapshot_publication_outputs "$output" "$github_output"
    printf 'audit:snapshot_publication version=%s published=n/a output=%s\n' "$version" "$output"
    return 0
  fi

  tmpdir="$(new_tmp_dir)"
  metadata="$tmpdir/metadata.xml"
  latest_file="$tmpdir/latest.txt"
  last_updated_file="$tmpdir/last-updated.txt"
  versions_file="$tmpdir/versions.txt"
  error=""
  if [[ -n "$metadata_file" ]]; then
    source="$(metadata_file_uri "$metadata_file")"
    cp "$metadata_file" "$metadata" || error="unable to read metadata file"
  else
    source="$metadata_url"
    if [[ "$metadata_url" != https://* ]]; then
      error="--metadata-url must use https"
    else
      curl --fail --silent --show-error --location --max-time "$timeout_seconds" \
        -H "Accept: application/xml" -H "User-Agent: ta4j-release-automation" \
        "$metadata_url" > "$metadata" 2>"$tmpdir/curl.err" || error="$(cat "$tmpdir/curl.err")"
    fi
  fi
  if [[ -z "$error" ]] && ! parse_snapshot_metadata "$metadata" "$latest_file" "$last_updated_file" "$versions_file"; then
    error="snapshot metadata is missing the <versioning> section"
  fi
  if [[ -n "$error" ]]; then
    write_snapshot_publication_json "$output" "$version" "unknown" "" "" "$source" "$error"
    emit_snapshot_publication_outputs "$output" "$github_output"
    printf '::warning::Unable to verify snapshot publication for %s: %s\n' "$version" "$error"
    printf 'audit:snapshot_publication version=%s published=unknown output=%s\n' "$version" "$output"
    return 0
  fi
  latest="$(cat "$latest_file")"
  last_updated="$(cat "$last_updated_file")"
  if grep -Fxq "$version" "$versions_file"; then
    published=true
  else
    published=false
  fi

  artifact_metadata_source=""
  resolved_version=""
  pom_url=""
  jar_url=""
  if [[ "$published" == true && "$require_artifacts" == true ]]; then
    artifact_metadata="$tmpdir/artifact-metadata.xml"
    artifact_values="$tmpdir/artifact-values.txt"
    artifact_pom="$tmpdir/artifact.pom"
    artifact_jar="$tmpdir/artifact.jar"
    if [[ -n "$artifact_metadata_file" ]]; then
      artifact_metadata_source="$(metadata_file_uri "$artifact_metadata_file")"
      cp "$artifact_metadata_file" "$artifact_metadata" || error="unable to read artifact metadata file"
    elif [[ "$repository_url" != https://* ]]; then
      error="--repository-url must use https"
    else
      artifact_metadata_source="${repository_url%/}/org/ta4j/ta4j-core/${version}/maven-metadata.xml"
      curl --fail --silent --show-error --location --max-time "$timeout_seconds" \
        -H "Accept: application/xml" -H "User-Agent: ta4j-release-automation" \
        "$artifact_metadata_source" > "$artifact_metadata" 2>"$tmpdir/artifact-metadata.err" || error="$(cat "$tmpdir/artifact-metadata.err")"
    fi
    if [[ -z "$error" ]] && ! parse_artifact_snapshot_metadata "$artifact_metadata" "$artifact_values" 2>"$tmpdir/artifact-parse.err"; then
      error="$(cat "$tmpdir/artifact-parse.err")"
    fi
    if [[ -z "$error" ]]; then
      artifact_version="$(read_key_value "$artifact_values" version)"
      artifact_last_updated="$(read_key_value "$artifact_values" last_updated)"
      pom_value="$(read_key_value "$artifact_values" pom_value)"
      jar_value="$(read_key_value "$artifact_values" jar_value)"
      resolved_version="$jar_value"
      pom_url="${repository_url%/}/org/ta4j/ta4j-core/${version}/ta4j-core-${pom_value}.pom"
      jar_url="${repository_url%/}/org/ta4j/ta4j-core/${version}/ta4j-core-${jar_value}.jar"
      if [[ "$artifact_version" != "$version" ]]; then
        error="artifact metadata version '${artifact_version}' does not match '${version}'"
      elif [[ -n "$artifact_pom_file" && -n "$artifact_jar_file" ]]; then
        [[ -s "$artifact_pom_file" ]] || error="snapshot POM fixture is missing or empty"
        [[ -n "$error" || -s "$artifact_jar_file" ]] || error="snapshot JAR fixture is missing or empty"
      else
        download_snapshot_artifact "$pom_url" "$artifact_pom" "$timeout_seconds" 2>"$tmpdir/artifact-pom.err" || error="$(cat "$tmpdir/artifact-pom.err")"
        if [[ -z "$error" ]]; then
          download_snapshot_artifact "$jar_url" "$artifact_jar" "$timeout_seconds" 2>"$tmpdir/artifact-jar.err" || error="$(cat "$tmpdir/artifact-jar.err")"
        fi
      fi
      [[ -z "$artifact_last_updated" ]] || last_updated="$artifact_last_updated"
    fi
    if [[ -n "$error" ]]; then
      published=false
    fi
  fi

  write_snapshot_publication_json "$output" "$version" "$published" "$latest" "$last_updated" "$source" "$error" "$versions_file" \
    "$resolved_version" "$artifact_metadata_source" "$pom_url" "$jar_url"
  emit_snapshot_publication_outputs "$output" "$github_output"
  printf 'audit:snapshot_publication version=%s published=%s latest=%s resolved=%s last_updated=%s output=%s\n' \
    "$version" "$published" "${latest:-unknown}" "${resolved_version:-unknown}" "${last_updated:-unknown}" "$output"
}

sha256_file() {
  # sha256sum ships with GNU coreutils and MSYS; shasum covers macOS.
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

resolved_snapshot_value() {
  local metadata="$1"
  [[ -f "$metadata" ]] || return 0
  python3 - "$metadata" <<'PY'
import sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1]).getroot()
snapshot_versions = root.find("./versioning/snapshotVersions")
if snapshot_versions is not None:
    values = {}
    for node in snapshot_versions.findall("snapshotVersion"):
        if not (node.findtext("classifier") or "").strip():
            extension = (node.findtext("extension") or "").strip()
            value = (node.findtext("value") or "").strip()
            if extension in {"jar", "pom"} and value:
                values[extension] = value
    print(values.get("jar") or values.get("pom") or "")
PY
}

snapshot_metadata_url() {
  local repository_url="$1" artifact="$2" version="$3" cache_buster="$4"
  printf '%s/org/ta4j/%s/%s/maven-metadata.xml?cacheBust=%s\n' \
    "${repository_url%/}" "$artifact" "$version" "$cache_buster"
}

fetch_snapshot_metadata() {
  local curl_command="$1" repository_url="$2" artifact="$3" version="$4" cache_buster="$5" timeout_seconds="$6" output="$7" error_output="$8"
  local metadata_url
  metadata_url="$(snapshot_metadata_url "$repository_url" "$artifact" "$version" "$cache_buster")"
  "$curl_command" --fail --silent --show-error --location --max-redirs 5 \
    --proto '=https' --proto-redir '=https' --max-time "$timeout_seconds" \
    -H "Accept: application/xml" -H "User-Agent: ta4j-release-automation" \
    "$metadata_url" > "$output" 2>"$error_output"
}

run_with_timeout() {
  local timeout_seconds="$1"
  shift
  # Portable Bash watchdog: the GNU `timeout -s KILL` fast path was removed
  # because macOS/BSD ship without a compatible timeout binary.
  "$@" &

  local pid=$!
  local elapsed=0
  while kill -0 "$pid" 2>/dev/null; do
    if (( elapsed >= timeout_seconds )); then
      kill -KILL "$pid" 2>/dev/null || true
      wait "$pid" 2>/dev/null || true
      return 124
    fi
    sleep 1
    elapsed=$((elapsed + 1))
  done
  wait "$pid"
}

snapshot_consumption_remaining_seconds() {
  local deadline_epoch="$1"
  local remaining=$(( deadline_epoch - $(date +%s) ))
  (( remaining > 0 )) && printf '%s\n' "$remaining" || printf '0\n'
}

xml_escape_text() {
  python3 -c 'import html, sys; sys.stdout.write(html.escape(sys.stdin.read(), quote=True))'
}

write_snapshot_consumer_pom() {
  local output="$1" repository_url="$2" parent_version="$3" core_version="$4" examples_version="$5"
  local repository_url_xml
  repository_url_xml="$(printf '%s' "$repository_url" | xml_escape_text)"
  cat > "$output" <<EOF
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.ta4j.verify</groupId>
  <artifactId>snapshot-consumer</artifactId>
  <version>1.0.0</version>
  <repositories>
    <repository>
      <id>${SNAPSHOT_REPOSITORY_ID}</id>
      <url>${repository_url_xml}</url>
      <releases><enabled>true</enabled></releases>
      <snapshots><enabled>true</enabled><updatePolicy>always</updatePolicy></snapshots>
    </repository>
  </repositories>
  <dependencies>
    <dependency><groupId>org.ta4j</groupId><artifactId>ta4j-parent</artifactId><version>${parent_version}</version><type>pom</type></dependency>
    <dependency><groupId>org.ta4j</groupId><artifactId>ta4j-core</artifactId><version>${core_version}</version></dependency>
    <dependency><groupId>org.ta4j</groupId><artifactId>ta4j-examples</artifactId><version>${examples_version}</version></dependency>
  </dependencies>
</project>
EOF
}

command_snapshot_consumption() {
  local version="" maven_command="./mvnw" repository_url="$SNAPSHOT_REPOSITORY_URL" publisher_root="$PWD"
  local curl_command="${RELEASE_HELPERS_CURL_COMMAND:-curl}"
  local max_attempts=20 retry_seconds=15 output="snapshot-consumption.json" github_output="" log="snapshot-consumption.log"
  while (($#)); do
    case "$1" in
      --version) require_value "$1" "${2:-}"; version="$2"; shift 2 ;;
      --maven-command) require_value "$1" "${2:-}"; maven_command="$2"; shift 2 ;;
      --repository-url) require_value "$1" "${2:-}"; repository_url="$2"; shift 2 ;;
      --publisher-root) require_value "$1" "${2:-}"; publisher_root="$2"; shift 2 ;;
      --max-attempts) require_value "$1" "${2:-}"; max_attempts="$2"; shift 2 ;;
      --retry-seconds) require_value "$1" "${2:-}"; retry_seconds="$2"; shift 2 ;;
      --output) require_value "$1" "${2:-}"; output="$2"; shift 2 ;;
      --github-output) require_value "$1" "${2:-}"; github_output="$2"; shift 2 ;;
      --log) require_value "$1" "${2:-}"; log="$2"; shift 2 ;;
      *) die "Unknown snapshot-consumption option: $1" ;;
    esac
  done
  [[ -n "$version" ]] || die "--version is required"
  [[ "$version" == *-SNAPSHOT ]] || die "snapshot consumption requires a -SNAPSHOT version: $version"
  [[ "$repository_url" == https://* ]] || die "--repository-url must use https"
  [[ "$repository_url" != *\?* && "$repository_url" != *\#* ]] || die "--repository-url must not contain query or fragment components"
  [[ "$max_attempts" =~ ^[1-9][0-9]*$ ]] || die "--max-attempts must be a positive integer"
  [[ "$retry_seconds" =~ ^[0-9]+$ ]] || die "--retry-seconds must be a non-negative integer"

  if [[ "$maven_command" != */* ]]; then
    maven_command="$(command -v "$maven_command" || true)"
  elif [[ "$maven_command" != /* ]]; then
    maven_command="$(cd "$(dirname "$maven_command")" && pwd -P)/$(basename "$maven_command")"
  fi
  [[ -x "$maven_command" ]] || die "Maven command is not executable: $maven_command"
  if [[ "$curl_command" != */* ]]; then
    curl_command="$(command -v "$curl_command" || true)"
  elif [[ "$curl_command" != /* ]]; then
    curl_command="$(cd "$(dirname "$curl_command")" && pwd -P)/$(basename "$curl_command")"
  fi
  [[ -x "$curl_command" ]] || die "curl command is not executable: $curl_command"
  publisher_root="$(cd "$publisher_root" && pwd -P)"

  local publisher_core="$publisher_root/ta4j-core/target/ta4j-core-${version}.jar"
  local publisher_examples="$publisher_root/ta4j-examples/target/ta4j-examples-${version}.jar"
  [[ -s "$publisher_core" ]] || die "Published core JAR is missing: $publisher_core"
  [[ -s "$publisher_examples" ]] || die "Published examples JAR is missing: $publisher_examples"

  local tmpdir consumer_pom local_repo raw_log redacted_log
  tmpdir="$(mktemp -d "${TMPDIR:-/tmp}/snapshot-consumption.XXXXXX")"
  consumer_pom="$tmpdir/pom.xml"
  local_repo="$tmpdir/repository"
  raw_log="$tmpdir/maven.log"
  redacted_log="$tmpdir/maven-redacted.log"
  mkdir -p "$local_repo" "$(dirname "$output")" "$(dirname "$log")"

  local publisher_core_sha publisher_examples_sha resolved_core resolved_examples resolved_core_sha="" resolved_examples_sha=""
  local parent_metadata="$tmpdir/ta4j-parent-metadata.xml" core_metadata="$tmpdir/ta4j-core-metadata.xml" examples_metadata="$tmpdir/ta4j-examples-metadata.xml"
  local resolved_parent_version="" resolved_core_version="" resolved_examples_version="" metadata_error="" cache_buster=""
  local snapshot_prefix="" timestamped_suffix="" resolved_candidate=""
  local attempts=0 maven_consumable=false start_time elapsed_seconds=0 deadline_epoch=0 remaining_seconds=0 metadata_timeout=0 maven_timeout=0 sleep_seconds=0
  local deadline_exhausted=false maven_status=0 metadata_artifact=""
  publisher_core_sha="$(sha256_file "$publisher_core")"
  publisher_examples_sha="$(sha256_file "$publisher_examples")"
  start_time="$(date +%s)"
  deadline_epoch=$(( start_time + SNAPSHOT_CONSUMPTION_DEADLINE_SECONDS ))
  : > "$raw_log"

  while (( attempts < max_attempts )); do
    remaining_seconds="$(snapshot_consumption_remaining_seconds "$deadline_epoch")"
    if (( remaining_seconds <= 0 )); then
      deadline_exhausted=true
      break
    fi
    attempts=$((attempts + 1))
    resolved_parent_version=""
    resolved_core_version=""
    resolved_examples_version=""
    resolved_core_sha=""
    resolved_examples_sha=""
    rm -rf "$local_repo/org/ta4j"
    rm -f "$parent_metadata" "$core_metadata" "$examples_metadata"
    printf 'attempt=%s/%s version=%s\n' "$attempts" "$max_attempts" "$version" >> "$raw_log"
    cache_buster="$(date +%s)-${attempts}"
    metadata_error=""
    remaining_seconds="$(snapshot_consumption_remaining_seconds "$deadline_epoch")"
    if (( remaining_seconds <= 0 )); then
      deadline_exhausted=true
      break
    fi
    for metadata_artifact in ta4j-parent ta4j-core ta4j-examples; do
      remaining_seconds="$(snapshot_consumption_remaining_seconds "$deadline_epoch")"
      if (( remaining_seconds <= 0 )); then
        deadline_exhausted=true
        break
      fi
      metadata_timeout=$(( remaining_seconds < 30 ? remaining_seconds : 30 ))
      fetch_snapshot_metadata "$curl_command" "$repository_url" "$metadata_artifact" "$version" "$cache_buster" \
        "$metadata_timeout" "$tmpdir/${metadata_artifact}-metadata.xml" "$tmpdir/${metadata_artifact}-metadata.err" \
        || metadata_error="${metadata_error:+$metadata_error; }${metadata_artifact}: $(cat "$tmpdir/${metadata_artifact}-metadata.err")"
    done
    if [[ "$deadline_exhausted" == true ]]; then
      break
    fi
    if [[ -n "$metadata_error" ]]; then
      printf 'fresh metadata fetch failed cache_buster=%s error=%s\n' "$cache_buster" "$metadata_error" >> "$raw_log"
    else
      resolved_parent_version="$(resolved_snapshot_value "$parent_metadata" 2>> "$raw_log" || true)"
      resolved_core_version="$(resolved_snapshot_value "$core_metadata" 2>> "$raw_log" || true)"
      resolved_examples_version="$(resolved_snapshot_value "$examples_metadata" 2>> "$raw_log" || true)"
      snapshot_prefix="${version%-SNAPSHOT}-"
      for resolved_candidate in "$resolved_parent_version" "$resolved_core_version" "$resolved_examples_version"; do
        if [[ -n "$resolved_candidate" ]]; then
          if [[ "$resolved_candidate" != "$snapshot_prefix"* ]]; then
            printf 'resolved coordinate does not match requested snapshot base: %s\n' "$resolved_candidate" >> "$raw_log"
            resolved_parent_version=""
            resolved_core_version=""
            resolved_examples_version=""
            break
          fi
          timestamped_suffix="${resolved_candidate#"$snapshot_prefix"}"
          if [[ ! "$timestamped_suffix" =~ ^[0-9]{8}\.[0-9]{6}-[0-9]+$ ]]; then
            printf 'resolved coordinate is not timestamped for the requested snapshot: %s\n' "$resolved_candidate" >> "$raw_log"
            resolved_parent_version=""
            resolved_core_version=""
            resolved_examples_version=""
            break
          fi
        fi
      done
      if [[ -z "$resolved_parent_version" || -z "$resolved_core_version" || -z "$resolved_examples_version" ]]; then
        printf 'resolved snapshot metadata is missing timestamped parent/core/examples coordinates\n' >> "$raw_log"
      else
        write_snapshot_consumer_pom "$consumer_pom" "$repository_url" "$resolved_parent_version" "$resolved_core_version" "$resolved_examples_version"
        resolved_core="$local_repo/org/ta4j/ta4j-core/${version}/ta4j-core-${resolved_core_version}.jar"
        resolved_examples="$local_repo/org/ta4j/ta4j-examples/${version}/ta4j-examples-${resolved_examples_version}.jar"
      fi
      if [[ -n "$resolved_core_version" && -n "$resolved_examples_version" && -n "$resolved_parent_version" ]]; then
        remaining_seconds="$(snapshot_consumption_remaining_seconds "$deadline_epoch")"
        if (( remaining_seconds <= 0 )); then
          deadline_exhausted=true
        else
          maven_timeout="$remaining_seconds"
          if run_with_timeout "$maven_timeout" "$maven_command" -B -U -f "$consumer_pom" -Dmaven.repo.local="$local_repo" \
            "org.apache.maven.plugins:maven-dependency-plugin:${MAVEN_DEPENDENCY_PLUGIN_VERSION}:resolve" >> "$raw_log" 2>&1; then
            if [[ -s "$resolved_core" && -s "$resolved_examples" ]]; then
              resolved_core_sha="$(sha256_file "$resolved_core")"
              resolved_examples_sha="$(sha256_file "$resolved_examples")"
              if [[ "$resolved_core_sha" == "$publisher_core_sha" && "$resolved_examples_sha" == "$publisher_examples_sha" ]]; then
                maven_consumable=true
                break
              else
                printf 'checksum mismatch core=%s/%s examples=%s/%s\n' \
                  "$resolved_core_sha" "$publisher_core_sha" "$resolved_examples_sha" "$publisher_examples_sha" >> "$raw_log"
              fi
            else
              printf 'resolved snapshot artifacts are missing from the isolated local repository\n' >> "$raw_log"
            fi
          else
            maven_status=$?
            if (( maven_status == 124 || maven_status == 137 )); then
              deadline_exhausted=true
              printf 'Maven snapshot resolution timed out with the five-minute deadline\n' >> "$raw_log"
            fi
          fi
        fi
      fi
    fi
    if [[ "$deadline_exhausted" == true ]]; then
      break
    fi
    remaining_seconds="$(snapshot_consumption_remaining_seconds "$deadline_epoch")"
    if (( attempts < max_attempts && retry_seconds > 0 && remaining_seconds > 0 )); then
      sleep_seconds=$(( retry_seconds < remaining_seconds ? retry_seconds : remaining_seconds ))
      sleep "$sleep_seconds"
    fi
  done

  if [[ "$deadline_exhausted" == true ]]; then
    printf 'snapshot consumption deadline exhausted after %s seconds\n' "$SNAPSHOT_CONSUMPTION_DEADLINE_SECONDS" >> "$raw_log"
  fi
  elapsed_seconds=$(( $(date +%s) - start_time ))
  redact_log_text < "$raw_log" > "$redacted_log"
  copy_prefix_with_notice "$redacted_log" "$log" 200000 "[TRUNCATED: snapshot consumption log exceeded 200000 bytes]"
  jq -S -n \
    --arg version "$version" \
    --arg repository "$repository_url" \
    --arg resolvedParentVersion "$resolved_parent_version" \
    --arg resolvedCoreVersion "$resolved_core_version" \
    --arg resolvedExamplesVersion "$resolved_examples_version" \
    --arg publisherCoreSha256 "$publisher_core_sha" \
    --arg resolvedCoreSha256 "$resolved_core_sha" \
    --arg publisherExamplesSha256 "$publisher_examples_sha" \
    --arg resolvedExamplesSha256 "$resolved_examples_sha" \
    --arg metadataError "$metadata_error" \
    --argjson attempts "$attempts" \
    --argjson elapsedSeconds "$elapsed_seconds" \
    --argjson mavenConsumable "$maven_consumable" \
    '{version: $version, repository: $repository, resolvedParentVersion: $resolvedParentVersion, resolvedCoreVersion: $resolvedCoreVersion, resolvedExamplesVersion: $resolvedExamplesVersion, publisherCoreSha256: $publisherCoreSha256, resolvedCoreSha256: $resolvedCoreSha256, publisherExamplesSha256: $publisherExamplesSha256, resolvedExamplesSha256: $resolvedExamplesSha256, metadataError: $metadataError, attempts: $attempts, elapsedSeconds: $elapsedSeconds, mavenConsumable: $mavenConsumable}' > "$output"
  append_output "maven_consumable" "$maven_consumable" "$github_output"
  append_output "resolved_parent_version" "$resolved_parent_version" "$github_output"
  append_output "resolved_core_version" "$resolved_core_version" "$github_output"
  append_output "resolved_examples_version" "$resolved_examples_version" "$github_output"
  append_output "snapshot_consumption_attempts" "$attempts" "$github_output"
  append_output "snapshot_consumption_elapsed_seconds" "$elapsed_seconds" "$github_output"
  append_output "publisher_core_sha256" "$publisher_core_sha" "$github_output"
  append_output "resolved_core_sha256" "$resolved_core_sha" "$github_output"
  append_output "publisher_examples_sha256" "$publisher_examples_sha" "$github_output"
  append_output "resolved_examples_sha256" "$resolved_examples_sha" "$github_output"
  printf 'audit:snapshot_consumption version=%s consumable=%s resolved_core=%s attempts=%s elapsed_seconds=%s output=%s\n' \
    "$version" "$maven_consumable" "${resolved_core_version:-unknown}" "$attempts" "$elapsed_seconds" "$output"
  rm -rf "$tmpdir"
  [[ "$maven_consumable" == true ]]
}

command_snapshot_publication_policy() {
  local event_name="" workflow_name="" output="snapshot-publication-policy.json" github_output=""
  while (($#)); do
    case "$1" in
      --event-name) require_value "$1" "${2:-}"; event_name="$2"; shift 2 ;;
      --workflow-name) workflow_name="${2-}"; shift 2 ;;
      --output) require_value "$1" "${2:-}"; output="$2"; shift 2 ;;
      --github-output) require_value "$1" "${2:-}"; github_output="$2"; shift 2 ;;
      *) die "Unknown snapshot-publication-policy option: $1" ;;
    esac
  done
  [[ -n "$event_name" ]] || die "--event-name is required"
  local enforce=false pending_reason="awaiting snapshot workflow completion before treating snapshot metadata drift as authoritative"
  if [[ "$event_name" == "schedule" || "$event_name" == "workflow_dispatch" || ( "$event_name" == "workflow_run" && "$workflow_name" == "$SNAPSHOT_WORKFLOW_NAME" ) ]]; then
    enforce=true
    pending_reason=""
  fi
  jq -S -n \
    --arg eventName "$event_name" \
    --arg workflowName "$workflow_name" \
    --arg pendingReason "$pending_reason" \
    --argjson enforce "$enforce" \
    '{eventName: $eventName, workflowName: $workflowName, enforce: $enforce, pendingReason: $pendingReason}' > "$output"
  append_output "snapshot_publication_enforced" "$enforce" "$github_output"
  append_output "snapshot_publication_pending_reason" "$pending_reason" "$github_output"
  printf 'audit:snapshot_publication_policy event=%s workflow=%s enforced=%s output=%s\n' \
    "${event_name:-unknown}" "${workflow_name:-none}" "$enforce" "$output"
}

main() {
  local command="${1:-}"
  if [[ -z "$command" ]]; then
    usage
    return 1
  fi
  shift
  case "$command" in
    signing-preflight) command_signing_preflight "$@" ;;
    build-dossier) command_build_dossier "$@" ;;
    last-release-date) command_last_release_date "$@" ;;
    release-pr-review-plan) command_release_pr_review_plan "$@" ;;
    javadoc-warnings) command_javadoc_warnings "$@" ;;
    artifact-manifest) command_artifact_manifest "$@" ;;
    snapshot-publication) command_snapshot_publication "$@" ;;
    snapshot-publication-policy) command_snapshot_publication_policy "$@" ;;
    snapshot-consumption) command_snapshot_consumption "$@" ;;
    -h|--help|help) usage ;;
    *) usage; die "Unknown command: $command" ;;
  esac
}

main "$@"
