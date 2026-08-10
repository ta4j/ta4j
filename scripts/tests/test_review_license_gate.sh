#!/usr/bin/env bash
set -euo pipefail

# Policy check: every reactor module in the root pom must resolve the
# license-maven-plugin header template, exactly like ta4j-core and
# ta4j-examples do (they override the inherited header with
# ${project.parent.basedir}). The new ta4j-cli module inherits the parent's
# ${project.basedir}/license-header.txt, which does not exist inside
# ta4j-cli/, so license:check and license:format fail for the module and the
# repository's completion gate can never be green.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

fail() { echo "[FAIL] $1" >&2; exit 1; }
pass() { echo "[PASS] $1"; }

# Resolve the license plugin header template for a module, mirroring how
# license-maven-plugin resolves ${project.basedir} (module dir) and
# ${project.parent.basedir} (root dir). Prints the resolved path, or nothing.
resolve_header_template() {
  local module="$1"
  local pom="$ROOT/$module/pom.xml"
  [[ -f "$pom" ]] || return 1
  grep -q "license-maven-plugin" "$pom" || return 1
  local header
  header="$(sed -n 's:.*<header>\(.*\)</header>.*:\1:p' "$pom" | head -1)"
  [[ -n "$header" ]] || return 1
  local base="$ROOT/$module"
  if [[ "$header" == *'${project.parent.basedir}'* ]]; then
    base="$ROOT"
  fi
  local relative
  relative="${header//\$\{project.parent.basedir\}/}"
  relative="${relative//\$\{project.basedir\}/}"
  relative="${relative#/}"
  printf '%s/%s' "$base" "$relative"
}

modules="$(sed -n 's:.*<module>\([^<]*\)</module>.*:\1:p' "$ROOT/pom.xml" | sort -u)"
count="$(printf '%s\n' "$modules" | grep -c . || true)"
if [[ "$count" -lt 3 ]]; then
  fail "expected at least ta4j-core, ta4j-examples, ta4j-cli in the reactor, found: $modules"
fi

unresolved=""
for module in $modules; do
  template="$(resolve_header_template "$module")" || template=""
  if [[ -z "$template" || ! -f "$template" ]]; then
    unresolved="$unresolved $module"
  fi
done
if [[ -n "$unresolved" ]]; then
  fail "modules without a resolvable license header template:$unresolved"
fi
pass "every reactor module resolves a license header template"

# Negative control: the established modules must keep resolving, so the check
# is not satisfied by deleting the plugin configuration.
for module in ta4j-core ta4j-examples; do
  template="$(resolve_header_template "$module")" || template=""
  [[ -n "$template" ]] || fail "$module must configure the license plugin"
  [[ -f "$template" ]] || fail "$module header template must exist: $template"
done
pass "ta4j-core and ta4j-examples still resolve their header templates"

echo "license header template resolution passed"
