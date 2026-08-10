#!/usr/bin/env bash
set -euo pipefail

# Policy check: every assertTrue(readme.contains(...)) statement in
# ReadmeContentManagerTest must be well formed (string argument present and
# statement closed). The branch's master merge dropped one string argument and
# left an orphaned opening parenthesis, which broke test compilation of
# ta4j-examples and with it every Maven test/verify invocation of the reactor.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TARGET="$ROOT/ta4j-examples/src/test/java/ta4jexamples/doc/ReadmeContentManagerTest.java"

fail() { echo "[FAIL] $1" >&2; exit 1; }
pass() { echo "[PASS] $1"; }

[[ -f "$TARGET" ]] || fail "test file must exist: $TARGET"

# Faithful port of the Python scanner: for every marker
# `assertTrue(readme.contains(`, consume characters until the paren depth
# returns to zero or a `;` appears at depth > 0. A statement is invalid when it
# terminates with `;` before balancing (missing string argument / missing
# closing parens) or when its argument contains no string literal.
awk '
BEGIN {
  depth = 0; in_string = 0; escaped = 0; arg = ""; scanning = 0
  markers = 0; failed = 0
}
{
  line = $0
  start = 1
  while (1) {
    if (!scanning) {
      pos = index(substr(line, start), "assertTrue(readme.contains(")
      if (pos == 0) break
      start = start + pos - 1 + 27
      scanning = 1
      markers++
      depth = 2; in_string = 0; escaped = 0; arg = ""
    }
    len = length(line)
    for (i = start; i <= len; i++) {
      c = substr(line, i, 1)
      if (in_string) {
        arg = arg c
        if (escaped) escaped = 0
        else if (c == "\\") escaped = 1
        else if (c == "\"") in_string = 0
      } else if (c == "\"") {
        in_string = 1; arg = arg c
      } else if (c == "(") {
        depth++; arg = arg c
      } else if (c == ")") {
        depth--; arg = arg c
        if (depth == 0) {
          if (arg !~ /"/) { print "statement at line " NR " has no string argument"; failed = 1 }
          scanning = 0; start = i + 1
          break
        }
      } else if (c == ";") {
        print "unclosed assertTrue(readme.contains( at line " NR " (missing string argument or closing parens)"
        failed = 1
        scanning = 0; start = i + 1
        break
      } else {
        arg = arg c
      }
    }
    if (scanning) { start = 1; break }
  }
}
END {
  if (markers < 4) { print "expected at least 4 assertTrue(readme.contains( assertions, found " markers; failed = 1 }
  exit failed
}
' "$TARGET"

pass "all assertTrue(readme.contains( statements are well formed"

echo "ReadmeContentManagerTest structure check passed"
