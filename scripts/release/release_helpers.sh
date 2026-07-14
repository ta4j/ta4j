#!/usr/bin/env bash
set -euo pipefail

SNAPSHOT_REPOSITORY_URL="https://central.sonatype.com/repository/maven-snapshots/"
SNAPSHOT_METADATA_URL="${SNAPSHOT_REPOSITORY_URL}org/ta4j/ta4j-parent/maven-metadata.xml"
SNAPSHOT_WORKFLOW_NAME="Publish Snapshot to Maven Central"
MAVEN_DEPENDENCY_PLUGIN_VERSION="3.11.0"
AI_REQUEST_METADATA_SCHEMA_VERSION=1
AI_TRANSPORT_DIAGNOSTICS_SCHEMA_VERSION=1
DEFAULT_AI_REQUEST_MAX_BYTES=600000

TMP_HELPER_PATHS=()
cleanup_release_helper_tmps() {
  local path
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
  catalog-preflight
  build-dossier
  build-ai-request
  ai-transport-diagnostics
  parse-decision
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

require_value() {
  local option="$1"
  local value="${2:-}"
  [[ -n "$value" ]] || die "${option} requires a value"
}

json_string() {
  jq -Rs .
}

write_json() {
  local output="$1"
  mkdir -p "$(dirname "$output")"
  jq -S . > "$output"
}

write_text() {
  local output="$1"
  mkdir -p "$(dirname "$output")"
  cat > "$output"
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
  perl -pe 's#https?://\S+#[REDACTED_URL]#g; s#gh[oprsu]_[A-Za-z0-9_]{20,}#[REDACTED_TOKEN]#g; s#(?<![A-Za-z0-9_])(?=[A-Za-z0-9+/=_-]{32,})(?=.*[0-9])[A-Za-z0-9+/=_-]{32,}#[REDACTED_SECRET]#g'
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

bounded_file() {
  local input="$1"
  local max_chars="$2"
  local label="$3"
  local size
  if (( max_chars <= 0 )); then
    printf '[OMITTED: %s omitted to keep request under transport budget]\n' "$label"
    return 0
  fi
  size="$(file_size_bytes "$input")"
  if (( size <= max_chars )); then
    sed -e '${/^$/d;}' "$input"
  else
    head -c "$max_chars" "$input"
    printf '\n\n[TRUNCATED: %s exceeded compact request budget]\n' "$label"
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
    git ls-tree -r --name-only HEAD
  else
    git diff --name-only "${last_tag}..HEAD"
  fi | sort
}

public_api_signals() {
  local input="$1"
  grep -E '^[+-][[:space:]]*(public|protected)[[:space:]]+((static|final|abstract|default|sealed|non-sealed)[[:space:]]+)*(class|interface|enum|record|@interface|[A-Za-z0-9_<>\[\], ?]+)[[:space:]]+[A-Za-z0-9_]+' "$input" \
    | awk '!seen[$0]++ { print; count++; if (count >= 80) { print "[TRUNCATED: more public API signal lines omitted]"; exit } }' || true
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

extract_markdown_section() {
  local input="$1"
  local heading="$2"
  awk -v heading="$heading" '
    $0 ~ "^##[[:space:]]+" heading "[[:space:]]*$" { capture=1; next }
    capture && /^##[[:space:]]+/ { exit }
    capture { print }
  ' "$input" | sed -e '/./,$!d' -e ':a' -e '/^\n*$/{$d;N;ba' -e '}'
}

compact_changed_files() {
  local input="$1"
  local max_files="$2"
  awk -v limit="$max_files" '
    function flush_omitted() {
      if (omitted) {
        print "- [TRUNCATED: " omitted " additional file path(s) omitted in this category]"
        omitted=0
      }
    }
    /^### / {
      flush_omitted()
      print
      emitted=0
      next
    }
    /^- `/ {
      if (emitted < limit) {
        print
        emitted++
      } else {
        omitted++
      }
      next
    }
    NF { print }
    END { flush_omitted() }
  ' "$input"
}

min_int() {
  local a="$1"
  local b="$2"
  if (( a < b )); then
    printf '%s' "$a"
  else
    printf '%s' "$b"
  fi
}

build_compact_dossier() {
  local input="$1"
  local output="$2"
  local category_limit="$3"
  local changelog_chars="$4"
  local signals_chars="$5"
  local diff_chars="$6"
  local metadata changed_files changelog api_signals doc_signals test_signals selected_diff

  metadata="$(new_tmp_file)"
  changed_files="$(new_tmp_file)"
  changelog="$(new_tmp_file)"
  api_signals="$(new_tmp_file)"
  doc_signals="$(new_tmp_file)"
  test_signals="$(new_tmp_file)"
  selected_diff="$(new_tmp_file)"

  extract_markdown_section "$input" "Metadata" > "$metadata"
  extract_markdown_section "$input" "Changed Files by Category" > "$changed_files"
  extract_markdown_section "$input" "Unreleased Changelog Context" > "$changelog"
  extract_markdown_section "$input" "Public API Signals" > "$api_signals"
  extract_markdown_section "$input" "Javadoc and @since Signals" > "$doc_signals"
  extract_markdown_section "$input" "Test File Signals" > "$test_signals"
  extract_markdown_section "$input" "Selected Diff" > "$selected_diff"

  {
    printf '# ta4j Release Dossier (compact transport-safe prompt)\n\n'
    printf 'The full release-dossier.md is preserved in the scheduler audit artifact. This compact prompt keeps the release decision resumable without sending the entire artifact inline.\n\n'
    printf '## Metadata\n\n'
    bounded_file "$metadata" 4000 "metadata"
    printf '\n## Changed Files by Category\n\n'
    compact_changed_files "$changed_files" "$category_limit"
    printf '\n\n## Unreleased Changelog Context\n\n'
    bounded_file "$changelog" "$changelog_chars" "unreleased changelog context"
    printf '\n## Public API Signals\n\n'
    bounded_file "$api_signals" "$signals_chars" "public API signals"
    printf '\n## Javadoc and @since Signals\n\n'
    bounded_file "$doc_signals" "$signals_chars" "Javadoc signals"
    printf '\n## Test File Signals\n\n'
    bounded_file "$test_signals" "$signals_chars" "test file signals"
    printf '\n## Selected Diff Excerpt\n\n'
    bounded_file "$selected_diff" "$diff_chars" "selected diff"
    printf '\n'
  } > "$output"
}

build_ai_request_payload() {
  local model="$1"
  local semver_file="$2"
  local dossier_file="$3"
  local prompt_profile="$4"
  local artifact_note=""
  if [[ "$prompt_profile" == compact* ]]; then
    artifact_note=$'\nThe full unabridged release dossier is preserved as release-dossier.md in the workflow audit artifact. Base the decision on this compact, artifact-backed dossier summary and explicitly call out uncertainty in missing or risks when the compact prompt omits detail.'
  fi
  jq -S -n \
    --arg model "$model" \
    --rawfile semver "$semver_file" \
    --rawfile dossier "$dossier_file" \
    --arg artifact_note "$artifact_note" \
    '{
      model: $model,
      temperature: 0,
      messages: [
        {
          role: "system",
          content: "You are a SemVer release reviewer for a Java library. Return JSON only. Base every conclusion on the release dossier."
        },
        {
          role: "user",
          content: (
            "Decide whether ta4j should cut a release from this dossier. If yes, choose bump patch or minor. Major is disabled for this workflow.\n\n"
            + "SemVer rules:\n" + $semver + "\n\n"
            + "Return JSON only with this shape:\n"
            + "{\"should_release\": true|false, \"bump\": \"patch|minor\", \"confidence\": 0.0-1.0, \"reason\": \"1-2 sentences\", \"evidence\": [\"specific dossier facts\"], \"risks\": [\"release risks or empty array\"], \"missing\": [\"missing changelog/javadoc/test evidence or empty array\"]}."
            + $artifact_note + "\n\n" + $dossier
          )
        }
      ]
    }'
}

command_catalog_preflight() {
  local model="" catalog_url="https://models.github.ai/catalog/models" catalog_file="" timeout_seconds=30 output="release-ai-model.json"
  while (($#)); do
    case "$1" in
      --model) require_value "$1" "${2:-}"; model="$2"; shift 2 ;;
      --catalog-url) require_value "$1" "${2:-}"; catalog_url="$2"; shift 2 ;;
      --catalog-file) require_value "$1" "${2:-}"; catalog_file="$2"; shift 2 ;;
      --timeout-seconds) require_value "$1" "${2:-}"; timeout_seconds="$2"; shift 2 ;;
      --output) require_value "$1" "${2:-}"; output="$2"; shift 2 ;;
      *) die "Unknown catalog-preflight option: $1" ;;
    esac
  done
  [[ -n "$model" ]] || die "--model is required"

  local catalog_json selected available
  catalog_json="$(new_tmp_file)"
  selected="$(new_tmp_file)"
  if [[ -n "$catalog_file" ]]; then
    cp "$catalog_file" "$catalog_json"
  else
    curl --fail --silent --show-error --location --max-time "$timeout_seconds" \
      -H "Accept: application/json" -H "User-Agent: ta4j-release-automation" \
      "$catalog_url" > "$catalog_json"
  fi
  jq -e 'type == "array"' "$catalog_json" >/dev/null || die "model catalog response must be a JSON array"

  if ! jq -S --arg model "$model" 'map(select(.id == $model)) | first // empty' "$catalog_json" > "$selected" || [[ ! -s "$selected" ]]; then
    available="$(jq -r '.[].id // empty' "$catalog_json" | sort | paste -sd ', ' -)"
    echo "::error::Configured RELEASE_AI_MODEL '$model' was not found in the GitHub Models catalog." >&2
    echo "Available models: $available" >&2
    return 1
  fi

  jq -S --arg fallback "$model" '{
    id: (.id // $fallback),
    name: (.name // ""),
    publisher: (.publisher // ""),
    summary: (.summary // ""),
    rate_limit_tier: (.rate_limit_tier // ""),
    max_input_tokens: ((.limits.max_input_tokens // "") | tostring),
    max_output_tokens: ((.limits.max_output_tokens // "") | tostring),
    html_url: (.html_url // "")
  }' "$selected" > "$output"

  append_output "model_id" "$(jq -r '.id' "$output")"
  append_output "model_name" "$(jq -r '.name' "$output")"
  append_output "model_summary" "$(jq -r '.summary' "$output")"
  append_output "model_rate_limit_tier" "$(jq -r '.rate_limit_tier' "$output")"
  append_output "model_max_input_tokens" "$(jq -r '.max_input_tokens' "$output")"
  append_output "model_max_output_tokens" "$(jq -r '.max_output_tokens' "$output")"
  append_output "model_html_url" "$(jq -r '.html_url' "$output")"
  printf 'audit:model_catalog_preflight model=%s max_input_tokens=%s max_output_tokens=%s rate_limit_tier=%s\n' \
    "$(jq -r '.id' "$output")" \
    "$(jq -r '.max_input_tokens // "unknown"' "$output")" \
    "$(jq -r '.max_output_tokens // "unknown"' "$output")" \
    "$(jq -r '.rate_limit_tier // "unknown"' "$output")"
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

  local tmpdir files_file categories_file diff_file redacted_diff api_file doc_file test_file changelog_file generated_at diff_truncated selected_diff_chars changed_file_count
  tmpdir="$(new_tmp_dir)"
  files_file="$tmpdir/files.txt"
  categories_file="$tmpdir/categories.tsv"
  diff_file="$tmpdir/diff.txt"
  redacted_diff="$tmpdir/redacted-diff.txt"
  api_file="$tmpdir/api.txt"
  doc_file="$tmpdir/doc.txt"
  test_file="$tmpdir/tests.txt"
  changelog_file="$tmpdir/changelog.txt"

  changed_files_since "$last_tag" > "$files_file"
  while IFS= read -r path; do
    [[ -n "$path" ]] || continue
    printf '%s\t%s\n' "$(category_for "$path")" "$path" >> "$categories_file"
  done < "$files_file"
  : > "$categories_file"
  while IFS= read -r path; do
    [[ -n "$path" ]] || continue
    printf '%s\t%s\n' "$(category_for "$path")" "$path" >> "$categories_file"
  done < "$files_file"

  if [[ "$last_tag" == "none" || ! -s "$files_file" ]]; then
    printf '(No prior release tag diff available.)\n' > "$redacted_diff"
    diff_truncated=false
  else
    local diff_status=0
    local path_args=()
    while IFS= read -r path; do
      [[ -n "$path" ]] && path_args+=("$path")
    done < <(awk -F '\t' '{ pri=($1 == "production code" || $1 == "build metadata") ? 0 : 1; print pri "\t" $2 }' "$categories_file" | sort -k1,1n -k2,2 | cut -f2-)
    git diff --no-ext-diff --find-renames --unified=80 "${last_tag}..HEAD" -- "${path_args[@]}" > "$diff_file" 2>"$tmpdir/diff.err" || diff_status=$?
    if (( diff_status != 0 )); then
      cat "$tmpdir/diff.err" >> "$diff_file"
    fi
    redact_text < "$diff_file" > "$tmpdir/redacted-full-diff.txt"
    if (( $(file_size_bytes "$tmpdir/redacted-full-diff.txt") > max_diff_chars )); then
      head -c "$max_diff_chars" "$tmpdir/redacted-full-diff.txt" > "$redacted_diff"
      printf '\n\n[TRUNCATED: selected diff exceeded dossier budget]\n' >> "$redacted_diff"
      diff_truncated=true
    else
      cp "$tmpdir/redacted-full-diff.txt" "$redacted_diff"
      diff_truncated=false
    fi
  fi

  public_api_signals "$redacted_diff" > "$api_file"
  javadoc_signals "$redacted_diff" > "$doc_file"
  awk -F '\t' '$1 == "tests" { print $2 }' "$categories_file" > "$test_file"
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
      awk -F '\t' -v category="$category" '$1 == category { printf "- `%s`\n", $2 }' "$categories_file"
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

command_build_ai_request() {
  local model="" dossier="release-dossier.md" semver_rules=".github/workflows/semver-rules-override.txt" max_dossier_chars=900000 max_request_bytes="$DEFAULT_AI_REQUEST_MAX_BYTES" output="request.json" metadata_output="release-ai-request-metadata.json"
  while (($#)); do
    case "$1" in
      --model) require_value "$1" "${2:-}"; model="$2"; shift 2 ;;
      --dossier) require_value "$1" "${2:-}"; dossier="$2"; shift 2 ;;
      --semver-rules) require_value "$1" "${2:-}"; semver_rules="$2"; shift 2 ;;
      --max-dossier-chars) require_value "$1" "${2:-}"; max_dossier_chars="$2"; shift 2 ;;
      --max-request-bytes) require_value "$1" "${2:-}"; max_request_bytes="$2"; shift 2 ;;
      --output) require_value "$1" "${2:-}"; output="$2"; shift 2 ;;
      --metadata-output) require_value "$1" "${2:-}"; metadata_output="$2"; shift 2 ;;
      *) die "Unknown build-ai-request option: $1" ;;
    esac
  done
  [[ -n "$model" ]] || die "--model is required"
  [[ "$max_request_bytes" =~ ^[0-9]+$ && "$max_request_bytes" -gt 0 ]] || die "--max-request-bytes must be positive."

  local tmpdir full_dossier prompt_dossier semver_file rules_source request_tmp full_request_size request_size full_dossier_truncated=false prompt_profile="full-inline" compacted=false compaction_level=0 selected_diff_excerpt_chars=0
  tmpdir="$(new_tmp_dir)"
  full_dossier="$dossier"
  prompt_dossier="$tmpdir/prompt-dossier.md"
  copy_prefix_with_notice "$full_dossier" "$prompt_dossier" "$max_dossier_chars" "[TRUNCATED: dossier exceeded request budget]"
  if (( $(file_size_bytes "$full_dossier") > max_dossier_chars )); then
    full_dossier_truncated=true
  fi
  semver_file="$tmpdir/semver-rules.txt"
  if [[ -s "$semver_rules" ]]; then
    cp "$semver_rules" "$semver_file"
    rules_source="$semver_rules"
  else
    cat > "$semver_file" <<'EOF'
Decide release go/no-go from unreleased binary-impacting changes.
If go, choose bump: patch|minor.
MINOR: backward-compatible, user-visible new features, and breaking-change cases.
PATCH: backward-compatible bug fixes or internal improvements.
If binary change count is 0 => should_release=false.
If binary change list empty or changelog-only => should_release=false.
If unsure between MINOR and PATCH, prefer PATCH.
Pre-1.0.0: breaking changes can be MINOR.
EOF
    rules_source="default"
  fi

  request_tmp="$tmpdir/request.json"
  build_ai_request_payload "$model" "$semver_file" "$prompt_dossier" "$prompt_profile" > "$request_tmp"
  full_request_size="$(file_size_bytes "$request_tmp")"
  local selected_diff_file
  selected_diff_file="$tmpdir/selected-diff.txt"
  extract_markdown_section "$prompt_dossier" "Selected Diff" > "$selected_diff_file"
  selected_diff_excerpt_chars="$(file_size_bytes "$selected_diff_file")"

  if (( full_request_size > max_request_bytes )); then
    compacted=true
    local limits=(50 25 10 5 2)
    local changelog_limits signals_limits diff_limits
    changelog_limits=(
      "$(min_int 40000 $((max_request_bytes / 6)))"
      "$(min_int 20000 $((max_request_bytes / 8)))"
      "$(min_int 8000 $((max_request_bytes / 12)))"
      "$(min_int 3000 $((max_request_bytes / 16)))"
      "$(min_int 1000 $((max_request_bytes / 24)))"
    )
    signals_limits=(
      "$(min_int 30000 $((max_request_bytes / 8)))"
      "$(min_int 15000 $((max_request_bytes / 10)))"
      "$(min_int 8000 $((max_request_bytes / 12)))"
      "$(min_int 3000 $((max_request_bytes / 16)))"
      "$(min_int 1000 $((max_request_bytes / 24)))"
    )
    diff_limits=(
      "$((max_request_bytes / 2))"
      "$((max_request_bytes / 3))"
      "$((max_request_bytes / 5))"
      "$((max_request_bytes / 10))"
      "0"
    )
    local i candidate_dossier candidate_request candidate_profile candidate_size
    for i in 0 1 2 3 4; do
      compaction_level=$((i + 1))
      candidate_dossier="$tmpdir/compact-${compaction_level}.md"
      candidate_request="$tmpdir/request-${compaction_level}.json"
      candidate_profile="compact-artifact-backed-v${compaction_level}"
      build_compact_dossier "$full_dossier" "$candidate_dossier" "${limits[$i]}" "${changelog_limits[$i]}" "${signals_limits[$i]}" "${diff_limits[$i]}"
      build_ai_request_payload "$model" "$semver_file" "$candidate_dossier" "$candidate_profile" > "$candidate_request"
      candidate_size="$(file_size_bytes "$candidate_request")"
      if (( candidate_size <= max_request_bytes )); then
        prompt_dossier="$candidate_dossier"
        request_tmp="$candidate_request"
        prompt_profile="$candidate_profile"
        selected_diff_excerpt_chars="${diff_limits[$i]}"
        break
      fi
    done
    if [[ "$prompt_profile" == "full-inline" ]]; then
      prompt_profile="compact-artifact-backed-minimal"
      prompt_dossier="$tmpdir/compact-minimal.md"
      request_tmp="$tmpdir/request-minimal.json"
      build_compact_dossier "$dossier" "$prompt_dossier" 1 400 400 0
      build_ai_request_payload "$model" "$semver_file" "$prompt_dossier" "$prompt_profile" > "$request_tmp"
      selected_diff_excerpt_chars=0
    fi
  fi

  cp "$request_tmp" "$output"
  request_size="$(file_size_bytes "$output")"
  jq -S -n \
    --arg generatedAt "$(iso_utc_now)" \
    --arg model "$model" \
    --arg semverRulesSource "$rules_source" \
    --arg promptProfile "$prompt_profile" \
    --arg fullDossierPath "$dossier" \
    --arg compactedBecause "$([[ "$compacted" == true ]] && printf 'full request exceeded transport budget')" \
    --arg metadataOutput "$metadata_output" \
    --argjson schemaVersion "$AI_REQUEST_METADATA_SCHEMA_VERSION" \
    --argjson artifactBackedContext "$compacted" \
    --argjson fullDossierChars "$(file_size_bytes "$dossier")" \
    --argjson promptDossierChars "$(file_size_bytes "$prompt_dossier")" \
    --argjson maxDossierChars "$max_dossier_chars" \
    --argjson fullDossierTruncatedForPrompt "$full_dossier_truncated" \
    --argjson fullRequestJsonSizeBytes "$full_request_size" \
    --argjson requestJsonSizeBytes "$request_size" \
    --argjson maxRequestBytes "$max_request_bytes" \
    --argjson requestWithinTransportBudget "$([[ "$request_size" -le "$max_request_bytes" ]] && printf true || printf false)" \
    --argjson compactionLevel "$([[ "$compacted" == true ]] && printf '%s' "$compaction_level" || printf 0)" \
    --argjson selectedDiffExcerptChars "$selected_diff_excerpt_chars" \
    '{
      schemaVersion: $schemaVersion,
      generatedAt: $generatedAt,
      model: $model,
      semverRulesSource: $semverRulesSource,
      promptProfile: $promptProfile,
      artifactBackedContext: $artifactBackedContext,
      fullDossierPath: $fullDossierPath,
      fullDossierChars: $fullDossierChars,
      promptDossierChars: $promptDossierChars,
      maxDossierChars: $maxDossierChars,
      fullDossierTruncatedForPrompt: $fullDossierTruncatedForPrompt,
      fullRequestJsonSizeBytes: $fullRequestJsonSizeBytes,
      requestJsonSizeBytes: $requestJsonSizeBytes,
      maxRequestBytes: $maxRequestBytes,
      requestWithinTransportBudget: $requestWithinTransportBudget,
      compactedBecause: $compactedBecause,
      compactionLevel: $compactionLevel,
      selectedDiffExcerptChars: $selectedDiffExcerptChars,
      auditArtifacts: ["release-dossier.md", "release-audit.json", $metadataOutput]
    }' > "$metadata_output"

  if (( request_size > max_request_bytes )); then
    echo "::error::AI request JSON is ${request_size} bytes, above transport budget ${max_request_bytes} bytes." >&2
    return 1
  fi
  printf 'audit:ai_request file=%s model=%s semver_rules_source=%s prompt_profile=%s request_json_size_bytes=%s max_request_bytes=%s\n' \
    "$output" "$model" "$rules_source" "$prompt_profile" "$request_size" "$max_request_bytes"
  append_output "request_json_size_bytes" "$request_size"
  append_output "request_max_bytes" "$max_request_bytes"
  append_output "request_metadata_path" "$metadata_output"
  append_output "prompt_profile" "$prompt_profile"
  append_output "dossier_chars" "$(file_size_bytes "$dossier")"
  append_output "prompt_dossier_chars" "$(file_size_bytes "$prompt_dossier")"
}

json_or_unparsed_file() {
  local input="$1"
  local output="$2"
  if [[ ! -s "$input" ]]; then
    printf 'null\n' > "$output"
  elif jq -e . "$input" >/dev/null 2>&1; then
    jq -S . "$input" > "$output"
  else
    local preview
    preview="$(tail -n 80 "$input" | redact_text | head -c 4000)"
    jq -S -n --arg unparsed "$preview" '{unparsed: $unparsed}' > "$output"
  fi
}

tail_redacted_file() {
  local input="$1"
  local max_lines="$2"
  local max_chars="$3"
  if [[ ! -f "$input" ]]; then
    return 0
  fi
  tail -n "$max_lines" "$input" | redact_text | head -c "$max_chars"
}

parse_key_value_log_file() {
  local input="$1"
  local output="$2"
  if [[ ! -f "$input" ]]; then
    printf '[]\n' > "$output"
    return 0
  fi
  redact_text < "$input" | jq -Rs '
    def parsepairs:
      [match("([A-Za-z_][A-Za-z0-9_]*)=([^=]*?)(?=\\s+[A-Za-z_][A-Za-z0-9_]*=|$)"; "g")
        | {(.captures[0].string): .captures[1].string}] | add // {};
    reduce (split("\n")[] | select(length > 0)) as $line
      ({entries: [], current: {}};
        if (($line | startswith("attempt=")) and ((.current | length) > 0)) then
          .entries += [.current] | .current = ($line | parsepairs)
        else
          ($line | parsepairs) as $pairs |
          if (($pairs | length) > 0) then
            .current += $pairs
          else
            .current.stderr = (((.current.stderr // "") + (if ((.current.stderr // "") == "") then "" else "\n" end) + $line))
          end
        end
      )
    | .entries + (if ((.current | length) > 0) then [.current] else [] end)
  ' > "$output"
}

command_ai_transport_diagnostics() {
  local ai_mode="full" model="" response_status="000" curl_exit_code="unknown" attempts="1" request_metadata="release-ai-request-metadata.json" release_audit="release-audit.json" curl_error="curl-error.log" curl_metrics="curl-metrics.log" response_headers="response-headers.txt" response="response.json" output="release-ai-transport-diagnostics.json" fallback_output="ai-content.txt"
  while (($#)); do
    case "$1" in
      --ai-mode) require_value "$1" "${2:-}"; ai_mode="$2"; shift 2 ;;
      --model) require_value "$1" "${2:-}"; model="$2"; shift 2 ;;
      --response-status) require_value "$1" "${2:-}"; response_status="$2"; shift 2 ;;
      --curl-exit-code) require_value "$1" "${2:-}"; curl_exit_code="$2"; shift 2 ;;
      --attempts) require_value "$1" "${2:-}"; attempts="$2"; shift 2 ;;
      --request-metadata) require_value "$1" "${2:-}"; request_metadata="$2"; shift 2 ;;
      --release-audit) require_value "$1" "${2:-}"; release_audit="$2"; shift 2 ;;
      --curl-error) require_value "$1" "${2:-}"; curl_error="$2"; shift 2 ;;
      --curl-metrics) require_value "$1" "${2:-}"; curl_metrics="$2"; shift 2 ;;
      --response-headers) require_value "$1" "${2:-}"; response_headers="$2"; shift 2 ;;
      --response) require_value "$1" "${2:-}"; response="$2"; shift 2 ;;
      --output) require_value "$1" "${2:-}"; output="$2"; shift 2 ;;
      --fallback-output) require_value "$1" "${2:-}"; fallback_output="$2"; shift 2 ;;
      *) die "Unknown ai-transport-diagnostics option: $1" ;;
    esac
  done

  local classification="http_error" connection_closed_during="not_applicable" reason response_bytes tmpdir request_json audit_json attempts_json metrics_json headers_tail response_preview
  if [[ "$response_status" == "000" ]]; then
    classification="transport_failure_before_http_response"
    connection_closed_during="unknown_before_response"
  fi
  if [[ "$curl_exit_code" == "18" ]]; then
    classification="curl_partial_file_transport_close"
    connection_closed_during="response_read"
  fi
  if [[ "$curl_exit_code" == "18" ]]; then
    reason="AI response transfer closed before completion (curl exit ${curl_exit_code}, HTTP ${response_status})"
  elif [[ "$curl_exit_code" != "0" && "$curl_exit_code" != "unknown" ]]; then
    reason="AI transport failed with curl exit ${curl_exit_code} (HTTP ${response_status})"
  elif [[ "$response_status" != "000" ]]; then
    reason="AI call failed with HTTP ${response_status}"
  else
    reason="AI call failed before an HTTP response (curl exit ${curl_exit_code})"
  fi

  response_bytes="$(file_size_bytes "$response")"
  tmpdir="$(new_tmp_dir)"
  request_json="$tmpdir/request.json"
  audit_json="$tmpdir/audit.json"
  attempts_json="$tmpdir/attempts.json"
  metrics_json="$tmpdir/metrics.json"
  json_or_unparsed_file "$request_metadata" "$request_json"
  json_or_unparsed_file "$release_audit" "$audit_json"
  parse_key_value_log_file "$curl_error" "$attempts_json"
  parse_key_value_log_file "$curl_metrics" "$metrics_json"
  headers_tail="$(tail_redacted_file "$response_headers" 80 8000)"
  response_preview="$(tail_redacted_file "$response" 20 2000)"

  jq -S -n \
    --arg generatedAt "$(iso_utc_now)" \
    --arg classification "$classification" \
    --arg connectionClosedDuring "$connection_closed_during" \
    --arg aiMode "$ai_mode" \
    --arg model "$model" \
    --arg attempts "$attempts" \
    --arg responseStatus "$response_status" \
    --arg curlExitCode "$curl_exit_code" \
    --arg responseHeadersTail "$headers_tail" \
    --arg responsePreview "$response_preview" \
    --argjson schemaVersion "$AI_TRANSPORT_DIAGNOSTICS_SCHEMA_VERSION" \
    --argjson responseBytes "$response_bytes" \
    --slurpfile request "$request_json" \
    --slurpfile releaseAudit "$audit_json" \
    --slurpfile curlAttempts "$attempts_json" \
    --slurpfile curlMetrics "$metrics_json" \
    '{
      schemaVersion: $schemaVersion,
      generatedAt: $generatedAt,
      classification: $classification,
      connectionClosedDuring: $connectionClosedDuring,
      aiMode: $aiMode,
      model: $model,
      attempts: $attempts,
      responseStatus: $responseStatus,
      curlExitCode: $curlExitCode,
      responseBytes: $responseBytes,
      request: $request[0],
      releaseAudit: $releaseAudit[0],
      curlAttempts: $curlAttempts[0],
      curlMetrics: $curlMetrics[0],
      responseHeadersTail: $responseHeadersTail,
      responsePreview: $responsePreview,
      recovery: [
        "Do not rerun billed aiMode=full blindly with the same request.",
        "Inspect release-ai-request-metadata.json and release-ai-transport-diagnostics.json from the audit artifact.",
        "Use aiMode=probe to validate GitHub Models connectivity without sending the full release dossier.",
        "Retry aiMode=full only after request size, provider status, or scheduler compaction policy has been reviewed."
      ]
    }' > "$output"

  jq -S -n \
    --arg warning "${reason}; see ${output}" \
    --arg reason "$reason" \
    --arg output "$output" \
    '{
      should_release: false,
      bump: "patch",
      confidence: 0.0,
      warning: $warning,
      reason: $reason,
      evidence: [],
      risks: ["GitHub Models transport failed before a usable release decision was returned"],
      missing: ["Review " + $output + " before another billed full AI scheduler call"]
    }' > "$fallback_output"
  printf 'audit:ai_transport_diagnostics classification=%s status=%s curl_exit=%s response_bytes=%s output=%s\n' \
    "$classification" "$response_status" "$curl_exit_code" "$response_bytes" "$output"
  append_output "transport_diagnostics_path" "$output"
}

command_parse_decision() {
  local raw_file="ai-content.txt" output="release-decision.json" github_output=""
  while (($#)); do
    case "$1" in
      --raw-file) require_value "$1" "${2:-}"; raw_file="$2"; shift 2 ;;
      --output) require_value "$1" "${2:-}"; output="$2"; shift 2 ;;
      --github-output) require_value "$1" "${2:-}"; github_output="$2"; shift 2 ;;
      *) die "Unknown parse-decision option: $1" ;;
    esac
  done

  local tmpdir candidate parsed parsed_status=0 hint
  tmpdir="$(new_tmp_dir)"
  candidate="$tmpdir/candidate.txt"
  parsed="$tmpdir/parsed.json"
  if [[ -f "$raw_file" ]]; then
    perl -0777 -pe 's/^\s*```(?:json)?\s*//; s/\s*```\s*$//' "$raw_file" > "$candidate"
  else
    : > "$candidate"
  fi
  jq -e 'select(type == "object")' "$candidate" > "$parsed" 2>/dev/null || parsed_status=$?
  if (( parsed_status != 0 )); then
    perl -0777 -ne 'if (/(\{.*\})/s) { print $1 }' "$candidate" > "$tmpdir/extracted.json"
    jq -e 'select(type == "object")' "$tmpdir/extracted.json" > "$parsed" 2>/dev/null || parsed_status=$?
  fi
  if (( parsed_status != 0 )); then
    hint="$(tr '\n' ' ' < "$candidate" | head -c 200)"
    jq -S -n --arg hint "$hint" '{
      should_release: false,
      bump: "patch",
      confidence: 0.0,
      warning: "Invalid AI JSON",
      reason: (if $hint == "" then "AI response was not valid JSON" else "AI response was not valid JSON: " + $hint end),
      evidence: [],
      risks: ["AI response could not be parsed"],
      missing: []
    }' > "$output"
  else
    jq -S '
      def append_warning($base; $msg):
        if $base == "" then $msg else $base + "; " + $msg end;
      def flag($value):
        if ($value | type) == "boolean" then {value: $value, warning: ""}
        elif (($value | type) == "number") and ($value == 1) then {value: true, warning: ""}
        elif (($value | type) == "number") and ($value == 0) then {value: false, warning: ""}
        elif ($value | type) == "string" then
          (($value | gsub("^\\s+|\\s+$"; "") | ascii_downcase) as $normalized
            | if ($normalized | IN("true", "1", "yes", "y", "on")) then {value: true, warning: ""}
              elif ($normalized | IN("false", "0", "no", "n", "off", "")) then {value: false, warning: ""}
              else {value: false, warning: ("invalid should_release '\''" + ($value | tostring) + "'\'', defaulted to false")}
              end)
        else {value: false, warning: ("invalid should_release '\''" + ($value | tostring) + "'\'', defaulted to false")}
        end;
      def string_list($key):
        (.[ $key ] // []) as $value
        | if ($value | type) == "array" then [$value[] | tostring] else [($value | tostring)] end;
      (flag(.should_release // false)) as $flag
      | ((.bump // "patch") | tostring | gsub("^\\s+|\\s+$"; "") | ascii_downcase) as $raw_bump
      | ((.warning // "") | tostring) as $base_warning
      | (if $flag.warning == "" then $base_warning else append_warning($base_warning; $flag.warning) end) as $warning1
      | (if $raw_bump == "major" then {bump: "minor", warning: append_warning($warning1; "major bump disabled, downgraded to minor")}
         elif ($raw_bump | IN("patch", "minor")) then {bump: $raw_bump, warning: $warning1}
         else {bump: "patch", warning: append_warning($warning1; ("invalid bump '\''" + $raw_bump + "'\'', defaulted to patch"))}
         end) as $bump_state
      | {
          should_release: $flag.value,
          bump: (if $flag.value then $bump_state.bump else "patch" end),
          confidence: ([0, ([1, ((.confidence | tonumber?) // 0)] | min)] | max),
          warning: $bump_state.warning,
          reason: ((.reason // "") | tostring),
          evidence: string_list("evidence"),
          risks: string_list("risks"),
          missing: string_list("missing")
        }
    ' "$parsed" > "$output"
  fi
  append_output "should_release" "$(jq -r '.should_release' "$output")" "$github_output"
  append_output "bump" "$(jq -r '.bump' "$output")" "$github_output"
  append_output "confidence" "$(jq -r '.confidence' "$output")" "$github_output"
  append_output "warning" "$(jq -r '.warning' "$output")" "$github_output"
  append_output "reason" "$(jq -r '.reason' "$output")" "$github_output"
  printf 'audit:ai_decision should_release=%s bump=%s confidence=%s output=%s\n' \
    "$(jq -r '.should_release' "$output")" "$(jq -r '.bump' "$output")" "$(jq -r '.confidence' "$output")" "$output"
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
  shasum -a 256 "$1" | awk '{print $1}'
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

snapshot_metadata_file() {
  local artifact_directory="$1"
  local candidate
  for candidate in "$artifact_directory"/maven-metadata-*.xml; do
    if [[ -f "$candidate" ]]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done
  return 0
}

command_snapshot_consumption() {
  local version="" maven_command="./mvnw" repository_url="$SNAPSHOT_REPOSITORY_URL" publisher_root="$PWD"
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
  [[ "$max_attempts" =~ ^[1-9][0-9]*$ ]] || die "--max-attempts must be a positive integer"
  [[ "$retry_seconds" =~ ^[0-9]+$ ]] || die "--retry-seconds must be a non-negative integer"

  if [[ "$maven_command" != */* ]]; then
    maven_command="$(command -v "$maven_command" || true)"
  elif [[ "$maven_command" != /* ]]; then
    maven_command="$(cd "$(dirname "$maven_command")" && pwd -P)/$(basename "$maven_command")"
  fi
  [[ -x "$maven_command" ]] || die "Maven command is not executable: $maven_command"
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
  cat > "$consumer_pom" <<EOF
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.ta4j.verify</groupId>
  <artifactId>snapshot-consumer</artifactId>
  <version>1.0.0</version>
  <repositories>
    <repository>
      <id>central-portal-snapshots</id>
      <url>${repository_url}</url>
      <releases><enabled>false</enabled></releases>
      <snapshots><enabled>true</enabled><updatePolicy>always</updatePolicy></snapshots>
    </repository>
  </repositories>
  <dependencies>
    <dependency><groupId>org.ta4j</groupId><artifactId>ta4j-parent</artifactId><version>${version}</version><type>pom</type></dependency>
    <dependency><groupId>org.ta4j</groupId><artifactId>ta4j-core</artifactId><version>${version}</version></dependency>
    <dependency><groupId>org.ta4j</groupId><artifactId>ta4j-examples</artifactId><version>${version}</version></dependency>
  </dependencies>
</project>
EOF

  local publisher_core_sha publisher_examples_sha resolved_core resolved_examples resolved_core_sha="" resolved_examples_sha=""
  local parent_metadata core_metadata examples_metadata resolved_parent_version="" resolved_core_version="" resolved_examples_version=""
  local attempts=0 maven_consumable=false start_time elapsed_seconds=0
  publisher_core_sha="$(sha256_file "$publisher_core")"
  publisher_examples_sha="$(sha256_file "$publisher_examples")"
  resolved_core="$local_repo/org/ta4j/ta4j-core/${version}/ta4j-core-${version}.jar"
  resolved_examples="$local_repo/org/ta4j/ta4j-examples/${version}/ta4j-examples-${version}.jar"
  start_time="$(date +%s)"
  : > "$raw_log"

  while (( attempts < max_attempts )); do
    attempts=$((attempts + 1))
    resolved_parent_version=""
    resolved_core_version=""
    resolved_examples_version=""
    rm -rf "$local_repo/org/ta4j"
    printf 'attempt=%s/%s version=%s\n' "$attempts" "$max_attempts" "$version" >> "$raw_log"
    if "$maven_command" -B -U -f "$consumer_pom" -Dmaven.repo.local="$local_repo" \
      "org.apache.maven.plugins:maven-dependency-plugin:${MAVEN_DEPENDENCY_PLUGIN_VERSION}:resolve" >> "$raw_log" 2>&1; then
      if [[ -s "$resolved_core" && -s "$resolved_examples" ]]; then
        resolved_core_sha="$(sha256_file "$resolved_core")"
        resolved_examples_sha="$(sha256_file "$resolved_examples")"
        if [[ "$resolved_core_sha" == "$publisher_core_sha" && "$resolved_examples_sha" == "$publisher_examples_sha" ]]; then
          maven_consumable=true
          parent_metadata="$(snapshot_metadata_file "$local_repo/org/ta4j/ta4j-parent/${version}")"
          core_metadata="$(snapshot_metadata_file "$local_repo/org/ta4j/ta4j-core/${version}")"
          examples_metadata="$(snapshot_metadata_file "$local_repo/org/ta4j/ta4j-examples/${version}")"
          resolved_parent_version="$(resolved_snapshot_value "$parent_metadata" 2>> "$raw_log" || true)"
          resolved_core_version="$(resolved_snapshot_value "$core_metadata" 2>> "$raw_log" || true)"
          resolved_examples_version="$(resolved_snapshot_value "$examples_metadata" 2>> "$raw_log" || true)"
          if [[ -z "$resolved_parent_version" || -z "$resolved_core_version" || -z "$resolved_examples_version" ]]; then
            maven_consumable=false
            printf 'resolved snapshot metadata is missing timestamped parent/core/examples coordinates\n' >> "$raw_log"
          else
            break
          fi
        else
          printf 'checksum mismatch core=%s/%s examples=%s/%s\n' \
            "$resolved_core_sha" "$publisher_core_sha" "$resolved_examples_sha" "$publisher_examples_sha" >> "$raw_log"
        fi
      else
        printf 'resolved snapshot artifacts are missing from the isolated local repository\n' >> "$raw_log"
      fi
    fi
    if (( attempts < max_attempts && retry_seconds > 0 )); then
      sleep "$retry_seconds"
    fi
  done

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
    --argjson attempts "$attempts" \
    --argjson elapsedSeconds "$elapsed_seconds" \
    --argjson mavenConsumable "$maven_consumable" \
    '{version: $version, repository: $repository, resolvedParentVersion: $resolvedParentVersion, resolvedCoreVersion: $resolvedCoreVersion, resolvedExamplesVersion: $resolvedExamplesVersion, publisherCoreSha256: $publisherCoreSha256, resolvedCoreSha256: $resolvedCoreSha256, publisherExamplesSha256: $publisherExamplesSha256, resolvedExamplesSha256: $resolvedExamplesSha256, attempts: $attempts, elapsedSeconds: $elapsedSeconds, mavenConsumable: $mavenConsumable}' > "$output"
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
    catalog-preflight) command_catalog_preflight "$@" ;;
    build-dossier) command_build_dossier "$@" ;;
    build-ai-request) command_build_ai_request "$@" ;;
    ai-transport-diagnostics) command_ai_transport_diagnostics "$@" ;;
    parse-decision) command_parse_decision "$@" ;;
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
