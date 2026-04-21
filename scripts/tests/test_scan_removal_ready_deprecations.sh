#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT="$ROOT/scripts/scan-removal-ready-deprecations.py"

cleanup() {
  if [[ -n "${TMP:-}" && -d "$TMP" ]]; then
    rm -rf "$TMP"
  fi
}
trap cleanup EXIT

fail() { echo "[FAIL] $1" >&2; exit 1; }
pass() { echo "[PASS] $1"; }

run_test() {
  TMP="$(mktemp -d "${TMPDIR:-/tmp}/scan-removal-ready-deprecations.XXXXXX")"
  mkdir -p "$TMP/scripts"
  cp "$SCRIPT" "$TMP/scripts/scan-removal-ready-deprecations.py"
  chmod +x "$TMP/scripts/scan-removal-ready-deprecations.py"
  pushd "$TMP" >/dev/null || exit 1
}

finish_test() {
  popd >/dev/null || exit 1
  rm -rf "$TMP"
}

test_matching_snapshot_detection() {
  echo "Running test_matching_snapshot_detection"
  run_test

  cat > pom.xml <<'EOF'
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.ta4j</groupId>
  <artifactId>ta4j-parent</artifactId>
  <version>0.24.0-SNAPSHOT</version>
</project>
EOF

  mkdir -p ta4j-core/src/main/java/org/ta4j/core/legacy
  cat > ta4j-core/src/main/java/org/ta4j/core/legacy/LegacyBridge.java <<'EOF'
package org.ta4j.core.legacy;

/**
 * @deprecated Scheduled for removal in 0.24.0.
 */
@Deprecated(since = "0.20.0", forRemoval = true)
public class LegacyBridge {

    @Deprecated(since = "0.20.0", forRemoval = true)
    public static void bridge() {
    }
}
EOF

  mkdir -p ta4j-examples/src/main/java/ta4jexamples/legacy
  cat > ta4j-examples/src/main/java/ta4jexamples/legacy/FutureBridge.java <<'EOF'
package ta4jexamples.legacy;

/**
 * @deprecated Scheduled for removal in 0.25.0.
 */
@Deprecated(since = "0.20.0", forRemoval = true)
public class FutureBridge {
}
EOF

  python3 scripts/scan-removal-ready-deprecations.py \
    --output-json report.json \
    --output-md report.md >/dev/null

  python3 - <<'PY'
import json
from pathlib import Path

report = json.loads(Path("report.json").read_text())
assert report["snapshotVersion"] == "0.24.0-SNAPSHOT"
assert report["findingCount"] == 2
assert report["issuePlanCount"] == 1
plan = report["issuePlans"][0]
assert plan["module"] == "ta4j-core"
assert plan["filePath"] == "ta4j-core/src/main/java/org/ta4j/core/legacy/LegacyBridge.java"
assert [symbol["name"] for symbol in plan["symbols"]] == ["LegacyBridge", "bridge"]
assert "Remove 0.24.0-ready deprecations in LegacyBridge.java" == plan["issueTitle"]
assert "LegacyBridge" in Path("report.md").read_text()
PY

  finish_test
  pass "test_matching_snapshot_detection"
}

test_notifier_version_detection() {
  echo "Running test_notifier_version_detection"
  run_test

  cat > pom.xml <<'EOF'
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.ta4j</groupId>
  <artifactId>ta4j-parent</artifactId>
  <version>0.24.0-SNAPSHOT</version>
</project>
EOF

  mkdir -p ta4j-core/src/main/java/org/ta4j/core/legacy
  cat > ta4j-core/src/main/java/org/ta4j/core/legacy/NotifierBridge.java <<'EOF'
package org.ta4j.core.legacy;

import org.ta4j.core.utils.DeprecationNotifier;

@Deprecated(since = "0.20.0", forRemoval = true)
public class NotifierBridge {

    {
        DeprecationNotifier.warnOnce(NotifierBridge.class, "org.ta4j.core.modern.NotifierBridge", "0.24.0");
    }
}
EOF

  python3 scripts/scan-removal-ready-deprecations.py \
    --output-json report.json \
    --output-md report.md >/dev/null

  python3 - <<'PY'
import json
from pathlib import Path

report = json.loads(Path("report.json").read_text())
assert report["findingCount"] == 1
assert report["issuePlans"][0]["symbols"][0]["name"] == "NotifierBridge"
PY

  finish_test
  pass "test_notifier_version_detection"
}

test_mixed_versions_in_one_file() {
  echo "Running test_mixed_versions_in_one_file"
  run_test

  cat > pom.xml <<'EOF'
<project>
  <version>0.24.0-SNAPSHOT</version>
</project>
EOF

  mkdir -p ta4j-core/src/main/java/org/ta4j/core/legacy
  cat > ta4j-core/src/main/java/org/ta4j/core/legacy/MixedRemovalBridge.java <<'EOF'
package org.ta4j.core.legacy;

public class MixedRemovalBridge {

    /**
     * @deprecated Scheduled for removal in 0.24.0.
     */
    @Deprecated(since = "0.20.0", forRemoval = true)
    public void removeNow() {
    }

    /**
     * @deprecated Scheduled for removal in 0.25.0.
     */
    @Deprecated(since = "0.21.0", forRemoval = true)
    public void removeLater() {
    }
}
EOF

  python3 scripts/scan-removal-ready-deprecations.py \
    --output-json report.json \
    --output-md report.md >/dev/null

  python3 - <<'PY'
import json
from pathlib import Path

report = json.loads(Path("report.json").read_text())
assert report["findingCount"] == 1
assert report["issuePlanCount"] == 1
symbols = report["issuePlans"][0]["symbols"]
assert [symbol["name"] for symbol in symbols] == ["removeNow"]
assert "removeLater" not in Path("report.md").read_text()
PY

  finish_test
  pass "test_mixed_versions_in_one_file"
}

test_next_javadoc_does_not_leak_backwards() {
  echo "Running test_next_javadoc_does_not_leak_backwards"
  run_test

  cat > pom.xml <<'EOF'
<project>
  <version>0.24.0-SNAPSHOT</version>
</project>
EOF

  mkdir -p ta4j-core/src/main/java/org/ta4j/core/legacy
  cat > ta4j-core/src/main/java/org/ta4j/core/legacy/AdjacentJavadocBridge.java <<'EOF'
package org.ta4j.core.legacy;

public class AdjacentJavadocBridge {

    @Deprecated(since = "0.20.0", forRemoval = true)
    public void unscheduled() {
    }

    /**
     * @deprecated Scheduled for removal in 0.24.0.
     */
    @Deprecated(since = "0.21.0", forRemoval = true)
    public void removeNow() {
    }
}
EOF

  python3 scripts/scan-removal-ready-deprecations.py \
    --output-json report.json \
    --output-md report.md >/dev/null

  python3 - <<'PY'
import json
from pathlib import Path

report = json.loads(Path("report.json").read_text())
assert report["findingCount"] == 1
symbols = report["issuePlans"][0]["symbols"]
assert [symbol["name"] for symbol in symbols] == ["removeNow"]
assert "unscheduled" not in Path("report.md").read_text()
PY

  finish_test
  pass "test_next_javadoc_does_not_leak_backwards"
}

test_type_inheritance_resets_between_types() {
  echo "Running test_type_inheritance_resets_between_types"
  run_test

  cat > pom.xml <<'EOF'
<project>
  <version>0.24.0-SNAPSHOT</version>
</project>
EOF

  mkdir -p ta4j-core/src/main/java/org/ta4j/core/legacy
  cat > ta4j-core/src/main/java/org/ta4j/core/legacy/LegacyBridge.java <<'EOF'
package org.ta4j.core.legacy;

/**
 * @deprecated Scheduled for removal in 0.24.0.
 */
@Deprecated(since = "0.20.0", forRemoval = true)
public class LegacyBridge {

    @Deprecated(since = "0.20.0", forRemoval = true)
    public void removeNow() {
    }
}

@Deprecated(since = "0.21.0", forRemoval = true)
class UnscheduledBridge {

    @Deprecated(since = "0.21.0", forRemoval = true)
    void keepAround() {
    }
}
EOF

  python3 scripts/scan-removal-ready-deprecations.py \
    --output-json report.json \
    --output-md report.md >/dev/null

  python3 - <<'PY'
import json
from pathlib import Path

report = json.loads(Path("report.json").read_text())
assert report["findingCount"] == 2
symbols = report["issuePlans"][0]["symbols"]
assert [symbol["name"] for symbol in symbols] == ["LegacyBridge", "removeNow"]
assert "keepAround" not in Path("report.md").read_text()
PY

  finish_test
  pass "test_type_inheritance_resets_between_types"
}

test_initialized_field_is_classified_as_field() {
  echo "Running test_initialized_field_is_classified_as_field"
  run_test

  cat > pom.xml <<'EOF'
<project>
  <version>0.24.0-SNAPSHOT</version>
</project>
EOF

  mkdir -p ta4j-core/src/main/java/org/ta4j/core/legacy
  cat > ta4j-core/src/main/java/org/ta4j/core/legacy/LegacyFieldHolder.java <<'EOF'
package org.ta4j.core.legacy;

/**
 * @deprecated Scheduled for removal in 0.24.0.
 */
@Deprecated(since = "0.20.0", forRemoval = true)
public class LegacyFieldHolder {

    @Deprecated(since = "0.20.0", forRemoval = true)
    public static final LegacyFieldHolder LEGACY = createLegacy();

    private static LegacyFieldHolder createLegacy() {
        return new LegacyFieldHolder();
    }
}
EOF

  python3 scripts/scan-removal-ready-deprecations.py \
    --output-json report.json \
    --output-md report.md >/dev/null

  python3 - <<'PY'
import json
from pathlib import Path

report = json.loads(Path("report.json").read_text())
symbols = report["issuePlans"][0]["symbols"]
assert any(symbol["name"] == "LEGACY" and symbol["kind"] == "field" for symbol in symbols)
assert "createLegacy" not in Path("report.md").read_text()
PY

  finish_test
  pass "test_initialized_field_is_classified_as_field"
}

test_requires_snapshot_version() {
  echo "Running test_requires_snapshot_version"
  run_test

  cat > pom.xml <<'EOF'
<project>
  <version>0.24.0</version>
</project>
EOF

  if python3 scripts/scan-removal-ready-deprecations.py \
    --output-json report.json \
    --output-md report.md >/dev/null 2>&1; then
    fail "scanner should reject non-SNAPSHOT pom versions"
  fi

  finish_test
  pass "test_requires_snapshot_version"
}

test_matching_snapshot_detection
test_notifier_version_detection
test_mixed_versions_in_one_file
test_next_javadoc_does_not_leak_backwards
test_type_inheritance_resets_between_types
test_initialized_field_is_classified_as_field
test_requires_snapshot_version
