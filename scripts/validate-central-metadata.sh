#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# validate-central-metadata.sh
#
# Validates Maven Central-required metadata before release publication.
# Uses Maven's evaluated effective model values (no XML parsing dependency).
#
# Usage:
#   scripts/validate-central-metadata.sh
#
# =============================================================================

for tool in mvn; do
  command -v "$tool" >/dev/null 2>&1 || {
    echo "Error: required tool '$tool' not found" >&2
    exit 1
  }
done

declare -a ERRORS=()

add_error() {
  local message="$1"
  ERRORS+=("$message")
  echo "::error::$message"
}

module_label() {
  local module="$1"
  if [[ "$module" == "ta4j-parent" ]]; then
    echo "ta4j-parent (root)"
  else
    echo "$module"
  fi
}

evaluate_expression() {
  local module="$1"
  local expression="$2"
  local -a cmd=(mvn -q)

  if [[ "$module" == "ta4j-parent" ]]; then
    cmd+=(-N)
  else
    cmd+=(-pl "$module")
  fi

  cmd+=(help:evaluate "-Dexpression=${expression}" -DforceStdout)

  local output
  if ! output="$("${cmd[@]}" 2>&1)"; then
    add_error "$(module_label "$module"): failed to evaluate '${expression}'"
    return 1
  fi

  local value
  value="$(printf '%s\n' "$output" | sed '/^[[:space:]]*$/d' | tail -n1 | tr -d '\r')"
  printf '%s' "$value"
}

is_missing_value() {
  local value="$1"
  [[ -z "$value" || "$value" == "null object or invalid expression" ]]
}

check_required() {
  local module="$1"
  local expression="$2"
  local field_name="$3"

  local value
  if ! value="$(evaluate_expression "$module" "$expression")"; then
    return
  fi

  if is_missing_value "$value"; then
    add_error "$(module_label "$module"): missing required '${field_name}' (${expression})"
  fi
}

validate_module() {
  local module="$1"

  check_required "$module" "project.name" "name"
  check_required "$module" "project.description" "description"
  check_required "$module" "project.url" "url"
  check_required "$module" "project.licenses[0].name" "licenses[0].name"
  check_required "$module" "project.scm.connection" "scm.connection"
  check_required "$module" "project.scm.url" "scm.url"
  check_required "$module" "project.developers[0].id" "developers[0].id"
  check_required "$module" "project.developers[0].name" "developers[0].name"
}

echo "Validating Maven Central metadata prerequisites..."
for module in ta4j-parent ta4j-core ta4j-examples; do
  validate_module "$module"
done

if [[ ${#ERRORS[@]} -gt 0 ]]; then
  echo "Maven Central metadata validation failed with ${#ERRORS[@]} issue(s)." >&2
  exit 1
fi

echo "Maven Central metadata validation passed for ta4j-parent, ta4j-core, ta4j-examples."
