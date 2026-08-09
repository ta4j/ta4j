# License-header and Java-formatting tooling spike

Date: 2026-08-09

Baseline: `ta4j/ta4j` `origin/master` at `6d8f05a2e63512bf1b84568594165b4100293bfc`

Raw measurements: [cf-411-license-formatting-results.csv](cf-411-license-formatting-results.csv)

## Recommendation

Adopt a hybrid in a separately reviewed implementation change:

1. Upgrade `com.mycila:license-maven-plugin` from 5.0.0 to 5.1.1 and keep it as the license-header owner.
2. Replace `net.revelc.code.formatter:formatter-maven-plugin` 2.29.0 with `com.diffplug.spotless:spotless-maven-plugin` 3.9.0, configured only with Eclipse JDT 4.37 and the existing `code-formatter.xml`.
3. Do not enable Spotless license-header handling and do not add Apache RAT.

This is the best-grounded path because it is byte-identical to the current stack across all 1,463 tracked Java files, preserves the one legacy full MIT notice, and reduces the statically resolved plugin runtime surface from 65 to 42 artifacts. Its observed Java 25 median was 63.45s versus the baseline's 71.45s, but the fixed candidate order shared Maven and operating-system caches, so the 11.2% difference is directional and is not used as causal evidence or as a decision-score advantage. The spike itself does not migrate production tooling.

## Current state and current releases

| Concern | Pinned | Current release | Status on 2026-08-08 |
| --- | ---: | ---: | --- |
| License writer/checker | Mycila 5.0.0 | 5.1.1 | Active; 5.1.1 released 2026-07-21; requires Java 11+ |
| Java formatter/checker | formatter-maven-plugin 2.29.0 | 2.29.0 | Already current; released 2025-09-11; Eclipse JDT 4.37 / JDT Core 3.43; requires Java 17+ |
| Consolidation candidate | None | Spotless Maven 3.9.0 | Active; released 2026-07-27; Maven 3.1+ and Java 17+ |
| Audit complement | None | Apache RAT 0.18 | Active; released 2026-03-11; audit-only and heuristic; Java 17+ |

Primary release metadata came from Maven Central and the projects' own release/documentation pages:

- [Mycila Maven metadata](https://repo.maven.apache.org/maven2/com/mycila/license-maven-plugin/maven-metadata.xml), [5.1.1 release](https://github.com/mathieucarbou/license-maven-plugin/releases/tag/v5.1.1), and [documentation](https://mathieu.carbou.me/license-maven-plugin/)
- [formatter-maven-plugin Maven metadata](https://repo.maven.apache.org/maven2/net/revelc/code/formatter/formatter-maven-plugin/maven-metadata.xml), [2.29.0 tag](https://github.com/revelc/formatter-maven-plugin/tree/formatter-maven-plugin-2.29.0), and [Eclipse-version matrix](https://code.revelc.net/formatter-maven-plugin/eclipse-versions.html)
- [Spotless Maven metadata](https://repo.maven.apache.org/maven2/com/diffplug/spotless/spotless-maven-plugin/maven-metadata.xml), [3.9.0 release](https://github.com/diffplug/spotless/releases/tag/maven/3.9.0), and [Maven plugin documentation](https://github.com/diffplug/spotless/blob/main/plugin-maven/README.md)
- [Apache RAT Maven metadata](https://repo.maven.apache.org/maven2/org/apache/rat/apache-rat-plugin/maven-metadata.xml), [0.18 release](https://github.com/apache/creadur-rat/releases/tag/apache-rat-project-0.18), and [plugin documentation](https://creadur.apache.org/rat/apache-rat-plugin/)

## Method

The benchmark used isolated tar snapshots of the same baseline, not the delivery worktree. Every candidate ran with:

- OpenJDK 25.0.4 from Homebrew `openjdk@25`
- Maven Wrapper 3.9.16
- macOS 26.6.1, Apple Silicon
- 1,463 tracked Java files in the two-module reactor

The recorded candidates ran once in fixed order—baseline, upgraded specialist, consolidated Spotless, then hybrid—against the same pre-existing Maven cache, without filesystem-cache eviction. Repetitions within each candidate were warm. This controls the source tree and command sequence but not cross-candidate cache state; runtime comparisons are therefore descriptive only. A follow-up performance claim would require a separate counterbalanced or isolated-cache benchmark.

The deterministic dirty corpus changed representative production and test files:

- removed the SPDX header and malformed interface spacing in `Bar.java`;
- malformed class spacing in `EMAIndicator.java`;
- converted `BarSeriesTest.java` to CRLF;
- retained Javadocs, annotations, imports, multi-module inheritance, and the legacy two-header `TrailingStopLossRuleTest.java` edge case.

For each formatting candidate the trial first ran pre-mutation clean validation, then mutated the corpus and proved that non-mutating validation rejected it, repaired it, compared all Java outputs, ran two idempotent repair repetitions, ran three warm validation repetitions, and ran two clean full-reactor repetitions. The aggregate Java-tree hash for the current, upgraded-specialist, and recommended hybrid outputs was:

```text
902f8eae205ce91a7b1b4cfecf45a6e873dd62e49be3f61726e0b25c317ed506
```

## Behavioral results

| Candidate | Dirty rejection | Pre-mutation clean validation | Output versus current | Idempotent | Multi-module / Java 25 |
| --- | --- | --- | --- | --- | --- |
| Mycila 5.0.0 + formatter 2.29.0 | Pass | Pass | Baseline | Pass | Pass |
| Mycila 5.1.1 + formatter 2.29.0 | Pass | Pass | Byte-identical, 1,463/1,463 | Pass | Pass |
| Spotless 3.9.0 for both concerns | Pass | Fails before migration churn | 1,462/1,463 identical; deletes 22 legacy MIT-notice lines | Pass after churn | Pass after churn |
| Mycila 5.1.1 + Spotless format-only | Pass | Pass | Byte-identical, 1,463/1,463 | Pass | Pass |
| Apache RAT 0.18 | Missing header rejected | Existing SPDX recognized | Does not format or repair | Not applicable | Java-only corpus passed |

The consolidated Spotless configuration is not a zero-churn replacement. Spotless treats all content before the Java delimiter as the replaceable header section. On the clean repository it proposed deleting the redundant but intentional-looking full MIT block that follows the SPDX block in `TrailingStopLossRuleTest.java`. A filename-specific workaround would add policy complexity, while excluding the file would weaken header coverage. Keeping Mycila as header owner avoids both outcomes.

Spotless and the current formatter both normalized the CRLF fixture to the repository's `.gitattributes` LF contract. Spotless's default `GIT_ATTRIBUTES_FAST_ALLSAME` mode is therefore compatible with the current tree.

## Runtime results

Wall-clock seconds; lower is better. Full-reactor rows are two independent `clean ... verify` runs. Idempotent repair and validation rows report the median of repeated post-repair runs; dirty repair is the first repair of the deliberately malformed corpus. Displayed values use conventional half-up rounding.

| Candidate | Dirty repair | Idempotent repair | Warm validation | Full-reactor range | Full-reactor median | Observed versus baseline |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Mycila 5.0.0 + formatter 2.29.0 | 4.32 | 2.85 | 2.04 | 67.78–75.12 | 71.45 | Baseline |
| Mycila 5.1.1 + formatter 2.29.0 | 5.02 | 2.99 | 2.05 | 77.34–93.33 | 85.34 | +19.4% |
| Spotless 3.9.0 consolidated | 5.78 | 2.03 | 1.56 | 58.77–58.99 | 58.88 | -17.6%, but with churn |
| Mycila 5.1.1 + Spotless format-only | 2.33 | 1.62 | 1.31 | 62.79–64.10 | 63.45 | -11.2%, byte-identical |

The specialist full-gate variance is too large to attribute to Mycila itself; its focused results are effectively equal to the baseline. Spotless-backed candidates were faster in this fixed-order run, but the hybrid ran after Spotless artifacts and filesystem data had already been warmed. With only two full-reactor repetitions and no counterbalancing, these observations are not a causal performance comparison.

## Dependency and advisory surface

`dependency:resolve-plugins` supplied each plugin's isolated runtime classpath. Each exact Maven coordinate/version was queried through the [OSV batch API](https://google.github.io/osv.dev/api/#tag/api/operation/OSV_QueryAffectedBatch) on 2026-08-09. The durable evidence is the indexed [query manifest](cf-411-osv-query-manifest.csv), exact [batch request](cf-411-osv-querybatch-request.json), exact [batch response](cf-411-osv-querybatch-response.json), and [SHA-256 checksum](cf-411-osv-querybatch-response.sha256). The response preserves every returned advisory ID and OSV modification timestamp.

| Plugin | Runtime artifacts | OSV advisory IDs | Queried artifacts with advisories |
| --- | ---: | ---: | ---: |
| Mycila 5.0.0 | 13 | 2 | 2 |
| Mycila 5.1.1 | 14 | 0 | 0 |
| formatter-maven-plugin 2.29.0 | 52 | 9 | 4 |
| Spotless Maven 3.9.0 | 28 | 0 | 0 |
| Apache RAT 0.18 | 49 | 2 | 2 |

Validate the stored response checksum, one-to-one query/result mapping, and the exact advisory and queried-artifact counts used above:

```bash
(
  evidence_root=$(git rev-parse --show-toplevel)
  cd "$evidence_root/docs/research" || exit 1
  shasum -a 256 -c cf-411-osv-querybatch-response.sha256
  python3 - \
    cf-411-osv-query-manifest.csv \
    cf-411-osv-querybatch-request.json \
    cf-411-osv-querybatch-response.json <<'PY'
import csv
import json
import sys
from collections import defaultdict

with open(sys.argv[1], newline="", encoding="utf-8") as source:
    manifest = list(csv.DictReader(source))
with open(sys.argv[2], encoding="utf-8") as source:
    queries = json.load(source)["queries"]
with open(sys.argv[3], encoding="utf-8") as source:
    results = json.load(source)["results"]
if len(manifest) != len(queries) or len(queries) != len(results):
    raise SystemExit("OSV manifest, request, and response lengths differ")

expected_query_counts = {
    "mycila-5.0.0": 13,
    "mycila-5.1.1": 14,
    "formatter-2.29.0": 52,
    "spotless-3.9.0": 28,
    "rat-0.18": 49,
}
query_counts = defaultdict(int)
observed = defaultdict(lambda: {"ids": set(), "queried_artifacts": 0})
for index, (row, query, result) in enumerate(zip(manifest, queries, results)):
    plugin = row["plugin"]
    if plugin not in expected_query_counts:
        raise SystemExit(f"unexpected OSV plugin label at query {index}: {plugin}")
    query_counts[plugin] += 1
    if int(row["query_index"]) != index:
        raise SystemExit(f"non-contiguous OSV query index at {index}")
    expected_package = {"ecosystem": "Maven", "name": row["package"]}
    if query != {"package": expected_package, "version": row["version"]}:
        raise SystemExit(f"OSV query does not match manifest row {index}")
    vulns = result.get("vulns", [])
    if vulns:
        observed[row["plugin"]]["queried_artifacts"] += 1
    for vuln in vulns:
        if set(vuln) != {"id", "modified"}:
            raise SystemExit(f"incomplete OSV metadata at query {index}")
        observed[row["plugin"]]["ids"].add(vuln["id"])

if dict(query_counts) != expected_query_counts:
    raise SystemExit(
        f"OSV query counts differ: expected={expected_query_counts}, actual={dict(query_counts)}"
    )

expected = {
    "mycila-5.0.0": (2, 2),
    "mycila-5.1.1": (0, 0),
    "formatter-2.29.0": (9, 4),
    "spotless-3.9.0": (0, 0),
    "rat-0.18": (2, 2),
}
actual = {
    plugin: (len(observed[plugin]["ids"]), observed[plugin]["queried_artifacts"])
    for plugin in expected
}
if actual != expected:
    raise SystemExit(f"OSV counts differ: expected={expected}, actual={actual}")
PY
)
```

The current two-plugin stack resolves 65 artifacts. The proposed Mycila/Spotless stack resolves 42, a 35% reduction. Matches in the current stack include `commons-io` 2.11.0 and `plexus-utils` 4.0.2 under Mycila 5.0.0, plus Jackson 2.20.0, `plexus-utils` 4.0.2, and jsoup 1.21.2 under formatter-maven-plugin. These are inventory matches, not an exploitability finding for ta4j's trusted build inputs.

Spotless provisions the selected Eclipse formatter through its Eclipse/P2 path at runtime. Those dynamically provisioned artifacts are not included in the 28-artifact Maven plugin classpath or the OSV count, so the security comparison is a useful lower-bound inventory rather than a complete software-bill-of-materials claim.

## Decision matrix

Scores are 1–5. Weighted total is out of 100: behavior parity 30%, migration churn/safety 20%, performance 15%, maintenance 15%, dependency/advisory surface 10%, contributor and IDE ergonomics 10%. Performance is held neutral at 3 for every candidate because the fixed-order shared-cache measurements are order-confounded.

| Candidate | Parity | Safety | Performance | Maintenance | Surface | Ergonomics | Weighted |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Keep pinned stack | 5 | 5 | 3 | 3 | 2 | 4 | 80.0 |
| Upgrade Mycila, keep formatter | 5 | 5 | 3 | 5 | 2 | 4 | 86.0 |
| Consolidate fully on Spotless | 4 | 3 | 3 | 5 | 4 | 5 | 78.0 |
| Mycila 5.1.1 + Spotless format-only | 5 | 5 | 3 | 5 | 4 | 4 | **90.0** |

Apache RAT is not scored as a replacement because it is deliberately a heuristic release-audit tool: it neither formats Java nor writes headers. A naive repository-wide RAT configuration also reported 27 unapproved root documentation/configuration files, meaning ta4j would need a separate reviewed audit policy and exclusion set. Adding 49 runtime artifacts and a second licensing concept is not justified by this spike.

## Proposed follow-up configuration

Keep the existing Mycila configuration, changing only its version, and replace formatter-maven-plugin with this format-only Spotless configuration:

```xml
<plugin>
    <groupId>com.mycila</groupId>
    <artifactId>license-maven-plugin</artifactId>
    <version>5.1.1</version>
    <configuration>
        <licenseSets>
            <licenseSet>
                <header>${project.basedir}/license-header.txt</header>
                <includes>
                    <include>**/*.java</include>
                </includes>
            </licenseSet>
        </licenseSets>
    </configuration>
</plugin>
<plugin>
    <groupId>com.diffplug.spotless</groupId>
    <artifactId>spotless-maven-plugin</artifactId>
    <version>3.9.0</version>
    <configuration>
        <java>
            <eclipse>
                <version>4.37</version>
                <file>${maven.multiModuleProjectDirectory}/code-formatter.xml</file>
            </eclipse>
        </java>
    </configuration>
</plugin>
```

Retain the existing `ta4j-core` and `ta4j-examples` Mycila overrides so their headers continue to resolve through `${project.parent.basedir}/license-header.txt`. The parent keeps `${project.basedir}/license-header.txt`; only the child formatter overrides are removed because Spotless uses the reactor-root-stable `${maven.multiModuleProjectDirectory}` path above.

Keep validation explicit rather than also binding Spotless to `verify`, matching ta4j's current command contract without double execution:

```bash
# Repair
./mvnw -B license:format spotless:apply

# Validate
./mvnw -B license:check spotless:check

# Full local contributor gate
./mvnw -B clean license:format spotless:apply verify \
  -Dta4j.excludedTestTags=analysis-demo,benchmark,requires-display,requires-headless

# Full hosted/non-mutating gate
./mvnw -B clean license:check spotless:check verify \
  -Dta4j.excludedTestTags=analysis-demo,benchmark,requires-display,requires-headless
```

Eclipse users can continue importing `code-formatter.xml`; IntelliJ users can continue using an Eclipse-format-profile integration. The formatter-specific m2e configurator is no longer relevant, so the implementation PR should explicitly exercise Maven-driven formatting from Eclipse or document the import path rather than implying automatic m2e execution.

## Migration and rollback

The follow-up implementation should be deliberately bounded:

1. Run `bash scripts/agents_for_target.sh <target>` for every migration target, including `pom.xml`, `ta4j-core/pom.xml`, and `ta4j-examples/pom.xml`, and apply all scoped instructions before editing.
2. Change Mycila to 5.1.1, remove formatter-maven-plugin from the parent and its overrides from `ta4j-core/pom.xml` and `ta4j-examples/pom.xml`, add format-only Spotless, retain both child Mycila header-path overrides, and retain both `license-header.txt` and `code-formatter.xml` unchanged.
3. Update the shell and PowerShell quiet-build goals, quality-scan contract fixtures, README, contributing guide, PR template, and any hosted validation references from `formatter:format` / `formatter:validate` to `spotless:apply` / `spotless:check`.
4. Assert a zero-source-diff migration before committing, then run the canonical Java 25 gate and hosted CI.
5. Add an `Unreleased` changelog entry because the contributor and maintainer command contract changes.

Rollback by reverting the complete implementation change set: POMs, shell and PowerShell commands, quality-scan fixtures, documentation, PR template, changelog, and hosted validation references. Then verify formatter-maven-plugin 2.29.0, Mycila 5.0.0, and the former formatter goals are restored and that no Spotless reference remains. Because the proposed migration is byte-identical, rollback requires no source reformat or data migration.

## Reproduction commands

Candidate copies were built from `git archive 6d8f05a2e63512bf1b84568594165b4100293bfc`. With `JAVA_HOME` set to Homebrew OpenJDK 25, the decisive commands were:

### Exact candidate construction

Run the following Bash blocks in the same shell session. Before starting, set `CF411_RESULTS_FILE` to a new absolute CSV path outside `/tmp` so the raw rerun survives candidate cleanup. The script refuses to overwrite an existing capture. Then create five independent copies from the pinned baseline:

```bash
set -euo pipefail
export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
export PATH="/opt/homebrew/opt/openjdk@25/bin:$PATH"
SPIKE_SOURCE_ROOT=$(git rev-parse --show-toplevel)
java_version=$(java --version | awk 'NR == 1 {print $2}')
architecture=$(uname -m)
if [[ "$java_version" != 25.0.4 ]] \
  || [[ "$architecture" != arm64 ]]; then
  printf 'Expected OpenJDK 25.0.4 and arm64; got %s and %s\n' \
    "$java_version" "$architecture" >&2
  exit 1
fi
: "${CF411_RESULTS_FILE:?Set CF411_RESULTS_FILE to a new absolute CSV path}"
if [[ "$CF411_RESULTS_FILE" != /* ]] || [[ -e "$CF411_RESULTS_FILE" ]]; then
  echo "CF411_RESULTS_FILE must be a new absolute path" >&2
  exit 1
fi
case "$CF411_RESULTS_FILE" in
  /tmp|/tmp/*|/private/tmp|/private/tmp/*)
    echo "CF411_RESULTS_FILE must be outside /tmp" >&2
    exit 1
    ;;
esac
SPIKE_ROOT=$(mktemp -d /tmp/cf411-formatting-spike.XXXXXX)
if [[ "$CF411_RESULTS_FILE" == "$SPIKE_ROOT"/* ]]; then
  echo "CF411_RESULTS_FILE must survive SPIKE_ROOT cleanup" >&2
  exit 1
fi
mkdir -p "$(dirname "$CF411_RESULTS_FILE")"
RESULTS_FILE=$CF411_RESULTS_FILE
cleanup_spike() {
  if [[ "${SPIKE_ROOT:-}" == /tmp/cf411-formatting-spike.* ]]; then
    rm -rf -- "$SPIKE_ROOT"
  fi
}
trap cleanup_spike EXIT
for candidate in baseline specialist spotless hybrid rat; do
  mkdir -p "$SPIKE_ROOT/$candidate"
  git -C "$SPIKE_SOURCE_ROOT" archive \
    --format=tar 6d8f05a2e63512bf1b84568594165b4100293bfc \
    | tar -xf - -C "$SPIKE_ROOT/$candidate"
done
maven_distribution_version=$(
  "$SPIKE_ROOT/baseline/mvnw" --version | awk 'NR == 1 {print $3}'
)
if [[ "$maven_distribution_version" != 3.9.16 ]]; then
  printf 'Expected pinned Maven distribution 3.9.16; got %s\n' \
    "$maven_distribution_version" >&2
  exit 1
fi
printf 'candidate,operation,repeat,wall_seconds,exit_code\n' >"$RESULTS_FILE"
CF411_UNTOUCHED_JAVA_HASH=$(
  cd "$SPIKE_ROOT/baseline" || exit 1
  find ta4j-core/src ta4j-examples/src -type f -name '*.java' -print \
    | LC_ALL=C sort \
    | xargs shasum -a 256 \
    | shasum -a 256 \
    | awk '{print $1}'
)
readonly CF411_UNTOUCHED_JAVA_HASH
```

Leave `baseline` unchanged. In `specialist/pom.xml`, change only Mycila's version from 5.0.0 to 5.1.1.

In the disposable `spotless` copy, leave the current plugins present but invoke only Spotless goals; because neither current plugin has a lifecycle execution, this isolates the candidate behavior without changing the benchmark path. Add `spotless-license-header.txt` at the repository root with the following exact content because Spotless does not wrap ta4j's existing raw `license-header.txt` in a Java block comment:

```text
/*
 * SPDX-License-Identifier: MIT
 */
```

Add this plugin alongside the existing plugins:

```xml
<plugin>
    <groupId>com.diffplug.spotless</groupId>
    <artifactId>spotless-maven-plugin</artifactId>
    <version>3.9.0</version>
    <configuration>
        <java>
            <eclipse>
                <version>4.37</version>
                <file>${maven.multiModuleProjectDirectory}/code-formatter.xml</file>
            </eclipse>
            <licenseHeader>
                <file>${maven.multiModuleProjectDirectory}/spotless-license-header.txt</file>
            </licenseHeader>
        </java>
    </configuration>
</plugin>
```

The scored migration removes the two superseded plugins; their presence in this goal-isolation copy does not affect `spotless:apply`, `spotless:check`, or `verify`, and the dependency/advisory table resolves each candidate plugin classpath independently.

In `hybrid/pom.xml`, change Mycila to 5.1.1, remove formatter-maven-plugin, and add the format-only Spotless block from [Proposed follow-up configuration](#proposed-follow-up-configuration). Also remove the formatter-maven-plugin overrides from `hybrid/ta4j-core/pom.xml` and `hybrid/ta4j-examples/pom.xml`; leaving either child declaration behind would retain a stale or versionless effective plugin. Preserve both child Mycila overrides and their `${project.parent.basedir}/license-header.txt` paths. Do not configure Spotless's `licenseHeader` step. Generate effective POMs for the parent, `ta4j-core`, and `ta4j-examples`; require Spotless in all three and formatter-maven-plugin in none; then repeat the isolated plugin-resolution inventory and require the intended 14 Mycila plus 28 Spotless runtime artifacts.

```bash
(
  (
    cd "$SPIKE_ROOT/baseline" || exit 1
    ./mvnw -q -N \
      org.apache.maven.plugins:maven-dependency-plugin:3.7.0:resolve-plugins \
      -DoutputFile=target/resolved-plugins.txt
  )
  cd "$SPIKE_ROOT/hybrid" || exit 1
  ./mvnw -q -N help:effective-pom -Doutput=target/effective-parent.xml
  (
    cd ta4j-core || exit 1
    ../mvnw -q -N help:effective-pom -Doutput=target/effective-core.xml
  )
  (
    cd ta4j-examples || exit 1
    ../mvnw -q -N help:effective-pom -Doutput=target/effective-examples.xml
  )
  python3 - \
    "$PWD/code-formatter.xml" \
    target/effective-parent.xml \
    ta4j-core/target/effective-core.xml \
    ta4j-examples/target/effective-examples.xml <<'PY'
import os
import sys
import xml.etree.ElementTree as ET

def local_name(node):
    return node.tag.rsplit("}", 1)[-1]

def child_text(node, name):
    for child in node:
        if local_name(child) == name:
            return (child.text or "").strip()
    return ""

formatter_file = sys.argv[1]
if not os.path.isfile(formatter_file):
    raise SystemExit(f"missing Eclipse formatter file: {formatter_file}")

for path in sys.argv[2:]:
    plugins = [node for node in ET.parse(path).getroot().iter() if local_name(node) == "plugin"]
    by_artifact = {}
    for plugin in plugins:
        by_artifact.setdefault(child_text(plugin, "artifactId"), []).append(plugin)
    if "formatter-maven-plugin" in by_artifact:
        raise SystemExit(f"stale formatter-maven-plugin remains in {path}")
    expected = {"license-maven-plugin": "5.1.1", "spotless-maven-plugin": "3.9.0"}
    for artifact, version in expected.items():
        versions = {child_text(plugin, "version") for plugin in by_artifact.get(artifact, [])}
        if versions != {version}:
            raise SystemExit(f"unexpected {artifact} versions in {path}: {versions}")
    license_headers = {
        (node.text or "").strip()
        for plugin in by_artifact["license-maven-plugin"]
        for node in plugin.iter()
        if local_name(node) == "header"
    }
    if path == sys.argv[2]:
        if len(license_headers) != 1 or not next(iter(license_headers)).endswith("/license-header.txt"):
            raise SystemExit(f"unexpected parent Mycila header path in {path}: {license_headers}")
    elif license_headers != {"${project.parent.basedir}/license-header.txt"}:
        raise SystemExit(f"unexpected child Mycila header path in {path}: {license_headers}")
    for plugin in by_artifact["spotless-maven-plugin"]:
        if any(local_name(node) == "licenseHeader" for node in plugin.iter()):
            raise SystemExit(f"Spotless owns a licenseHeader step in {path}")
        eclipse_nodes = [node for node in plugin.iter() if local_name(node) == "eclipse"]
        versions = {child_text(node, "version") for node in eclipse_nodes}
        files = {child_text(node, "file") for node in eclipse_nodes}
        if versions != {"4.37"}:
            raise SystemExit(f"unexpected Eclipse formatter versions in {path}: {versions}")
        allowed_files = {
            formatter_file,
            "${maven.multiModuleProjectDirectory}/code-formatter.xml",
        }
        if len(files) != 1 or not files.issubset(allowed_files):
            raise SystemExit(f"unexpected Eclipse formatter paths in {path}: {files}")
PY
  ./mvnw -q -N \
    org.apache.maven.plugins:maven-dependency-plugin:3.7.0:resolve-plugins \
    -DoutputFile=target/resolved-plugins.txt
  python3 - \
    "$SPIKE_ROOT/baseline/target/resolved-plugins.txt" \
    target/resolved-plugins.txt \
    "$SPIKE_SOURCE_ROOT/docs/research/cf-411-osv-query-manifest.csv" <<'PY'
import csv
import sys

inventories = {
    "baseline": {
        "path": sys.argv[1],
        "expected": {
            "com.mycila:license-maven-plugin:maven-plugin:5.0.0:runtime": 13,
            "net.revelc.code.formatter:formatter-maven-plugin:maven-plugin:2.29.0:runtime": 52,
        },
        "labels": {
            "com.mycila:license-maven-plugin:maven-plugin:5.0.0:runtime": "mycila-5.0.0",
            "net.revelc.code.formatter:formatter-maven-plugin:maven-plugin:2.29.0:runtime": "formatter-2.29.0",
        },
    },
    "hybrid": {
        "path": sys.argv[2],
        "expected": {
            "com.mycila:license-maven-plugin:maven-plugin:5.1.1:runtime": 14,
            "com.diffplug.spotless:spotless-maven-plugin:maven-plugin:3.9.0:runtime": 28,
        },
        "labels": {
            "com.mycila:license-maven-plugin:maven-plugin:5.1.1:runtime": "mycila-5.1.1",
            "com.diffplug.spotless:spotless-maven-plugin:maven-plugin:3.9.0:runtime": "spotless-3.9.0",
        },
    },
}
managed_plugins = {
    "com.mycila:license-maven-plugin",
    "net.revelc.code.formatter:formatter-maven-plugin",
    "com.diffplug.spotless:spotless-maven-plugin",
}

def parse_inventory(name, inventory):
    expected = inventory["expected"]
    counts = {coordinate: 0 for coordinate in expected}
    actual_artifacts = {coordinate: set() for coordinate in expected}
    current = None
    sections = 0
    managed_headers = set()
    with open(inventory["path"], encoding="utf-8") as source:
        for line in source:
            if line.startswith("   ") and not line.startswith("      "):
                # The header labels a section; its first six-space row is the
                # plugin JAR itself, followed by the remaining runtime artifacts.
                coordinate = line.strip()
                current = coordinate if coordinate in expected else None
                sections += 1
                parts = coordinate.split(":")
                if len(parts) >= 2 and f"{parts[0]}:{parts[1]}" in managed_plugins:
                    managed_headers.add(coordinate)
            elif line.startswith("      ") and current is not None:
                parts = line.strip().split(":")
                if len(parts) < 4:
                    raise SystemExit(f"unexpected dependency coordinate: {line.strip()}")
                version = parts[-2] if parts[-1] == "runtime" else parts[-1]
                actual_artifacts[current].add((f"{parts[0]}:{parts[1]}", version))
                counts[current] += 1
    if sections == 0:
        raise SystemExit(f"unsupported {name} dependency:resolve-plugins output format")
    if managed_headers != set(expected):
        raise SystemExit(
            f"unexpected {name} formatting-plugin headers: {sorted(managed_headers)}"
        )
    if counts != expected:
        raise SystemExit(f"unexpected {name} plugin runtime inventory: {counts}")
    return actual_artifacts

with open(sys.argv[3], newline="", encoding="utf-8") as source:
    manifest = list(csv.DictReader(source))
for name, inventory in inventories.items():
    actual_artifacts = parse_inventory(name, inventory)
    for coordinate, label in inventory["labels"].items():
        manifest_artifacts = {
            (row["package"], row["version"])
            for row in manifest
            if row["plugin"] == label
        }
        if actual_artifacts[coordinate] != manifest_artifacts:
            raise SystemExit(
                f"{name} inventory differs from OSV manifest for {label}: "
                f"actual={sorted(actual_artifacts[coordinate])}, "
                f"manifest={sorted(manifest_artifacts)}"
            )
PY
)
```

In `rat/pom.xml`, add the following audit-only plugin configuration. The explicit `inputSource` file is an exclusive list of report inputs; an unscoped repository-wide run instead reports 27 unapproved documentation and configuration files.

```xml
<plugin>
    <groupId>org.apache.rat</groupId>
    <artifactId>apache-rat-plugin</artifactId>
    <version>0.18</version>
    <inherited>false</inherited>
    <configuration>
        <basedir>${maven.multiModuleProjectDirectory}/target/rat-empty</basedir>
        <inputSource>${maven.multiModuleProjectDirectory}/rat-java-sources.txt</inputSource>
        <outputStyle>xml</outputStyle>
    </configuration>
</plugin>
```

Generate the exclusive input list and reject any scope drift before invoking RAT:

```bash
(
  cd "$SPIKE_ROOT/rat" || exit 1
  find ta4j-core/src ta4j-examples/src -type f -name '*.java' -print \
    | LC_ALL=C sort >rat-java-sources.txt
  git -C "$SPIKE_SOURCE_ROOT" ls-tree -r --name-only \
    6d8f05a2e63512bf1b84568594165b4100293bfc -- \
    ta4j-core/src ta4j-examples/src \
    | rg '\.java$' \
    | LC_ALL=C sort >"$SPIKE_ROOT/pinned-java-manifest.txt"
  mkdir -p target/rat-empty
  if [[ $(wc -l <rat-java-sources.txt) -ne 1463 ]] \
    || rg -n -v '\.java$' rat-java-sources.txt \
    || ! diff -u "$SPIKE_ROOT/pinned-java-manifest.txt" rat-java-sources.txt; then
    echo "RAT input list is not the expected Java-only corpus" >&2
    exit 1
  fi
)
```

Define the shared timing helper, then reproduce pre-mutation clean validation for the three compatible formatting candidates:

```bash
measure() {
  local candidate=$1
  local operation=$2
  local repeat=$3
  shift 3
  local timing_file
  timing_file=$(mktemp "$SPIKE_ROOT/timing.XXXXXX")
  local status
  if /usr/bin/time -p -o "$timing_file" "$@"; then
    status=0
  else
    status=$?
  fi
  local elapsed
  elapsed=$(awk '$1 == "real" {print $2}' "$timing_file")
  rm -f -- "$timing_file"
  printf '%s,%s,%s,%s,%s\n' \
    "$candidate" "$operation" "$repeat" "$elapsed" "$status" \
    | tee -a "$RESULTS_FILE"
  return "$status"
}

for candidate in baseline specialist; do
  (
    cd "$SPIKE_ROOT/$candidate" || exit 1
    measure "$candidate" clean-validation 1 \
      ./mvnw -q -B license:check formatter:validate
  )
done
(
  cd "$SPIKE_ROOT/hybrid" || exit 1
  measure hybrid clean-validation 1 ./mvnw -q -B license:check spotless:check
)
```

Also reproduce the consolidated Spotless candidate's pre-mutation clean-tree incompatibility and require the failure to identify the legacy two-header fixture:

```bash
(
  cd "$SPIKE_ROOT/spotless" || exit 1
  clean_log="$SPIKE_ROOT/spotless-clean.log"
  if measure spotless clean-validation 1 ./mvnw -q -B spotless:check \
    >"$clean_log" 2>&1; then
    echo "Expected clean Spotless validation to report migration churn" >&2
    exit 1
  else
    clean_status=$?
  fi
  if [[ "$clean_status" -ne 1 ]] \
    || ! rg -q 'TrailingStopLossRuleTest\.java' "$clean_log"; then
    cat "$clean_log" >&2
    echo "Clean Spotless validation failed for an unexpected reason" >&2
    exit 1
  fi
)
```

Apply the same deterministic dirty corpus to each formatting candidate before its rejection and repair trials. Keep the `rat` copy clean for its audit-only sequence below.

```bash
for candidate in baseline specialist spotless hybrid; do
  root="$SPIKE_ROOT/$candidate"
  python3 - "$root" <<'PY'
import sys
from pathlib import Path

root = Path(sys.argv[1])
bar_path = root / "ta4j-core/src/main/java/org/ta4j/core/Bar.java"
ema_path = root / "ta4j-core/src/main/java/org/ta4j/core/indicators/averages/EMAIndicator.java"
series_path = root / "ta4j-core/src/test/java/org/ta4j/core/BarSeriesTest.java"

header = b"/*\n * SPDX-License-Identifier: MIT\n */\n"
bar = bar_path.read_bytes()
ema = ema_path.read_bytes()
series = series_path.read_bytes()
if not bar.startswith(header):
    raise SystemExit(f"unexpected Bar.java header in {root}")

def replace_once(data, old, new, label):
    if data.count(old) != 1:
        raise SystemExit(f"expected exactly one {label} preimage in {root}")
    result = data.replace(old, new, 1)
    if result.count(new) != 1 or old in result:
        raise SystemExit(f"failed to establish {label} postimage in {root}")
    return result

dirty_bar = replace_once(
    bar[len(header):],
    b"public interface Bar extends Serializable {",
    b"public   interface Bar extends Serializable{",
    "Bar.java spacing",
)
dirty_ema = replace_once(
    ema,
    b"public class EMAIndicator extends AbstractEMAIndicator {",
    b"public  class EMAIndicator extends AbstractEMAIndicator{",
    "EMAIndicator.java spacing",
)
if b"\r" in series or b"\n" not in series:
    raise SystemExit(f"BarSeriesTest.java is not the expected LF preimage in {root}")
dirty_series = series.replace(b"\n", b"\r\n")
if dirty_series.count(b"\r\n") != series.count(b"\n"):
    raise SystemExit(f"failed to establish the CRLF postimage in {root}")

bar_path.write_bytes(dirty_bar)
ema_path.write_bytes(dirty_ema)
series_path.write_bytes(dirty_series)
if bar_path.read_bytes() != dirty_bar \
        or ema_path.read_bytes() != dirty_ema \
        or series_path.read_bytes() != dirty_series:
    raise SystemExit(f"dirty-corpus write verification failed in {root}")
PY
done
```

### Candidate executions

The timing helper records wall seconds and exit status for every measured invocation. Expected dirty-corpus failures are accepted only when their logs identify `Bar.java`; unrelated failures stop the script.

```bash
java_tree_hash() {
  find ta4j-core/src ta4j-examples/src -type f -name '*.java' -print \
    | LC_ALL=C sort \
    | xargs shasum -a 256 \
    | shasum -a 256 \
    | awk '{print $1}'
}

assert_idempotent_repair() {
  local candidate=$1
  local repeat=$2
  shift 2
  local before_hash
  before_hash=$(java_tree_hash)
  measure "$candidate" idempotent-repair "$repeat" "$@"
  local after_hash
  after_hash=$(java_tree_hash)
  if [[ "$after_hash" != "$before_hash" ]]; then
    echo "$candidate repair repeat $repeat changed the Java tree" >&2
    exit 1
  fi
}

expect_dirty_failure() {
  local candidate=$1
  local operation=$2
  local repeat=$3
  local expected=$4
  shift 4
  local log_file
  log_file=$(mktemp "$SPIKE_ROOT/expected-failure.XXXXXX")
  if measure "$candidate" "$operation" "$repeat" "$@" >"$log_file" 2>&1; then
    cat "$log_file" >&2
    echo "Expected dirty validation to fail for $candidate" >&2
    exit 1
  fi
  if ! rg -q "$expected" "$log_file"; then
    cat "$log_file" >&2
    echo "Dirty validation for $candidate failed for an unexpected reason" >&2
    exit 1
  fi
  rm -f -- "$log_file"
}

# Current and upgraded-specialist candidates
for candidate in baseline specialist; do
  (
    cd "$SPIKE_ROOT/$candidate" || exit 1
    expect_dirty_failure "$candidate" dirty-license-check 1 'Bar\.java' \
      ./mvnw -q -B license:check
    expect_dirty_failure "$candidate" dirty-format-check 1 \
      'Bar\.java|EMAIndicator\.java|BarSeriesTest\.java' \
      ./mvnw -q -B formatter:validate
    measure "$candidate" dirty-repair 1 \
      ./mvnw -q -B license:format formatter:format
    for repeat in 1 2; do
      assert_idempotent_repair "$candidate" "$repeat" \
        ./mvnw -q -B license:format formatter:format
    done
    for repeat in 1 2 3; do
      measure "$candidate" warm-validation "$repeat" \
        ./mvnw -q -B license:check formatter:validate
    done
    for repeat in 1 2; do
      measure "$candidate" full-reactor "$repeat" \
        ./mvnw -q -B clean license:check formatter:validate verify \
        -Dta4j.excludedTestTags=analysis-demo,benchmark,requires-display,requires-headless
    done
  )
done

# Consolidated Spotless candidate
(
  cd "$SPIKE_ROOT/spotless" || exit 1
  expect_dirty_failure spotless dirty-validation 1 'Bar\.java' \
    ./mvnw -q -B spotless:check
  measure spotless dirty-repair 1 ./mvnw -q -B spotless:apply
  for repeat in 1 2; do
    assert_idempotent_repair spotless "$repeat" ./mvnw -q -B spotless:apply
  done
  for repeat in 1 2 3; do
    measure spotless warm-validation "$repeat" ./mvnw -q -B spotless:check
  done
  for repeat in 1 2; do
    measure spotless full-reactor "$repeat" \
      ./mvnw -q -B clean spotless:check verify \
      -Dta4j.excludedTestTags=analysis-demo,benchmark,requires-display,requires-headless
  done
)

# Recommended hybrid candidate
(
  cd "$SPIKE_ROOT/hybrid" || exit 1
  expect_dirty_failure hybrid dirty-license-check 1 'Bar\.java' \
    ./mvnw -q -B license:check
  expect_dirty_failure hybrid dirty-format-check 1 \
    'Bar\.java|EMAIndicator\.java|BarSeriesTest\.java' \
    ./mvnw -q -B spotless:check
  measure hybrid dirty-repair 1 ./mvnw -q -B license:format spotless:apply
  for repeat in 1 2; do
    assert_idempotent_repair hybrid "$repeat" \
      ./mvnw -q -B license:format spotless:apply
  done
  for repeat in 1 2 3; do
    measure hybrid warm-validation "$repeat" \
      ./mvnw -q -B license:check spotless:check
  done
  for repeat in 1 2; do
    measure hybrid full-reactor "$repeat" \
      ./mvnw -q -B clean license:check spotless:check verify \
      -Dta4j.excludedTestTags=analysis-demo,benchmark,requires-display,requires-headless
  done
)

# RAT's audit-only Java corpus
(
  cd "$SPIKE_ROOT/rat" || exit 1
  measure rat clean-validation 1 \
    ./mvnw -q -N -B org.apache.rat:apache-rat-plugin:0.18:check
  python3 - rat-java-sources.txt target/rat.txt <<'PY'
import sys
import xml.etree.ElementTree as ET

with open(sys.argv[1], encoding="utf-8") as source:
    expected = [line.rstrip("\n") for line in source]
resources = [
    node.attrib["name"].lstrip("/")
    for node in ET.parse(sys.argv[2]).getroot().findall("resource")
]
if len(expected) != len(set(expected)) or len(resources) != len(set(resources)):
    raise SystemExit("RAT input or report contains duplicate resources")
if set(resources) != set(expected):
    missing = sorted(set(expected) - set(resources))
    extra = sorted(set(resources) - set(expected))
    raise SystemExit(f"RAT report scope mismatch: missing={missing}, extra={extra}")
PY
  mv target/rat.txt "$SPIKE_ROOT/rat-clean-report.xml"
  rat_bar=ta4j-core/src/main/java/org/ta4j/core/Bar.java
  if [[ $(sed -n '1,3p' "$rat_bar") != $'/*\n * SPDX-License-Identifier: MIT\n */' ]]; then
    echo "Unexpected RAT Bar.java header preimage" >&2
    exit 1
  fi
  sed -i '' '1,3d' "$rat_bar"
  if head -n 3 "$rat_bar" | rg -q 'SPDX-License-Identifier'; then
    echo "RAT Bar.java header removal did not establish the expected postimage" >&2
    exit 1
  fi
  rat_log="$SPIKE_ROOT/rat-dirty.log"
  if measure rat dirty-validation 1 \
    ./mvnw -q -N -B org.apache.rat:apache-rat-plugin:0.18:check \
    >"$rat_log" 2>&1; then
    cat "$rat_log" >&2
    echo "Expected RAT to reject the missing Bar.java header" >&2
    exit 1
  fi
  if [[ ! -s target/rat.txt ]] \
    || ! rg -q 'ta4j-core/src/main/java/org/ta4j/core/Bar\.java|Bar\.java' \
      target/rat.txt; then
    cat "$rat_log" >&2
    echo "RAT did not produce a fresh report for the mutated Bar.java" >&2
    exit 1
  fi
)
```

After the repair and repeated validation runs, calculate the aggregate Java-tree hashes and compare both reactor modules:

```bash
reported_hash=902f8eae205ce91a7b1b4cfecf45a6e873dd62e49be3f61726e0b25c317ed506
document_hashes=$(rg -o '^[0-9a-f]{64}$' \
  "$SPIKE_SOURCE_ROOT/docs/research/cf-411-license-formatting-tooling-spike.md" \
  | LC_ALL=C sort -u)
csv_hashes=$(awk -F, \
  'NR > 1 && $1 != "spotless-3.9.0-consolidated" && $7 != "" {print $7}' \
  "$SPIKE_SOURCE_ROOT/docs/research/cf-411-license-formatting-results.csv" \
  | LC_ALL=C sort -u)
if [[ "$document_hashes" != "$reported_hash" ]] \
  || [[ "$csv_hashes" != "$reported_hash" ]]; then
  echo "The report and raw-results CSV disagree on the canonical Java hash" >&2
  exit 1
fi
: "${CF411_UNTOUCHED_JAVA_HASH:?Run exact candidate construction first}"
for candidate in baseline specialist hybrid spotless; do
  hash=$(
    cd "$SPIKE_ROOT/$candidate" || exit 1
    find ta4j-core/src ta4j-examples/src -type f -name '*.java' -print \
      | LC_ALL=C sort \
      | xargs shasum -a 256 \
      | shasum -a 256 \
      | awk '{print $1}'
  )
  printf '%s\t%s\n' "$candidate" "$hash"
  case "$candidate" in
    baseline)
      if [[ "$hash" != "$CF411_UNTOUCHED_JAVA_HASH" ]] \
        || [[ "$CF411_UNTOUCHED_JAVA_HASH" != "$reported_hash" ]]; then
        echo "Repaired baseline differs from the untouched pinned Java tree" >&2
        exit 1
      fi
      ;;
    specialist|hybrid)
      if [[ "$hash" != "$CF411_UNTOUCHED_JAVA_HASH" ]]; then
        echo "$candidate output differs from the untouched pinned Java tree" >&2
        exit 1
      fi
      ;;
  esac
done

(
  cd "$SPIKE_ROOT" || exit 1
  core_diff="$SPIKE_ROOT/spotless-core.diff"
  if diff -rq baseline/ta4j-core/src spotless/ta4j-core/src >"$core_diff"; then
    echo "Expected the consolidated candidate's bounded legacy-header difference" >&2
    exit 1
  else
    diff_status=$?
  fi
  if [[ "$diff_status" -ne 1 ]] \
    || [[ $(wc -l <"$core_diff") -ne 1 ]] \
    || ! rg -q 'TrailingStopLossRuleTest\.java' "$core_diff"; then
    cat "$core_diff" >&2
    echo "Unexpected consolidated candidate core diff" >&2
    exit 1
  fi
  diff -rq baseline/ta4j-examples/src spotless/ta4j-examples/src
  legacy_diff="$SPIKE_ROOT/spotless-legacy-header.diff"
  if diff -u \
    baseline/ta4j-core/src/test/java/org/ta4j/core/rules/TrailingStopLossRuleTest.java \
    spotless/ta4j-core/src/test/java/org/ta4j/core/rules/TrailingStopLossRuleTest.java \
    >"$legacy_diff"; then
    echo "Expected the detailed legacy-header diff" >&2
    exit 1
  else
    legacy_diff_status=$?
  fi
  if [[ "$legacy_diff_status" -ne 1 ]]; then
    echo "Unable to compare the legacy-header outputs" >&2
    exit 1
  fi
  deleted_lines=$(awk \
    'substr($0, 1, 1) == "-" && substr($0, 1, 3) != "---" {count++} END {print count+0}' \
    "$legacy_diff")
  added_lines=$(awk \
    'substr($0, 1, 1) == "+" && substr($0, 1, 3) != "+++" {count++} END {print count+0}' \
    "$legacy_diff")
  removed_hash=$(awk \
    'substr($0, 1, 1) == "-" && substr($0, 1, 3) != "---" {print substr($0, 2)}' \
    "$legacy_diff" | shasum -a 256 | awk '{print $1}')
  if [[ "$deleted_lines" -ne 22 ]] \
    || [[ "$added_lines" -ne 0 ]] \
    || [[ "$removed_hash" != 5cb436fd41471c9326522d5e835a49a8dbef5f34e3e432bc2457c56e2fdccbb6 ]]; then
    cat "$legacy_diff" >&2
    echo "Consolidated candidate changed more than the exact legacy MIT block" >&2
    exit 1
  fi
)
```

Finally, validate the stable raw capture's exact candidate/operation/repetition matrix, summarize it, and compare every corresponding duration with the checked-in raw-results CSV at two-decimal precision. The block intentionally exits nonzero on timing drift so a rerun cannot silently replace the published observations; retain the caller-provided CSV and update the report and checked-in CSV together if new measurements are accepted.

```bash
python3 - \
  "$RESULTS_FILE" \
  "$SPIKE_SOURCE_ROOT/docs/research/cf-411-license-formatting-results.csv" \
  "$SPIKE_SOURCE_ROOT/docs/research/cf-411-license-formatting-tooling-spike.md" <<'PY'
import csv
import statistics
import sys
from collections import defaultdict
from decimal import Decimal, ROUND_HALF_UP

capture_path, published_path, report_path = sys.argv[1:]

def rounded(value, places):
    quantum = Decimal(1).scaleb(-places)
    return format(value.quantize(quantum, rounding=ROUND_HALF_UP), f".{places}f")

with open(capture_path, newline="", encoding="utf-8") as source:
    rows = list(csv.DictReader(source))

expected_repeats = {}
for candidate in ("baseline", "specialist", "hybrid"):
    expected_repeats.update({
        (candidate, "clean-validation"): [1],
        (candidate, "dirty-license-check"): [1],
        (candidate, "dirty-format-check"): [1],
        (candidate, "dirty-repair"): [1],
        (candidate, "idempotent-repair"): [1, 2],
        (candidate, "warm-validation"): [1, 2, 3],
        (candidate, "full-reactor"): [1, 2],
    })
expected_repeats.update({
    ("spotless", "clean-validation"): [1],
    ("spotless", "dirty-validation"): [1],
    ("spotless", "dirty-repair"): [1],
    ("spotless", "idempotent-repair"): [1, 2],
    ("spotless", "warm-validation"): [1, 2, 3],
    ("spotless", "full-reactor"): [1, 2],
    ("rat", "clean-validation"): [1],
    ("rat", "dirty-validation"): [1],
})

actual_repeats = defaultdict(list)
for row in rows:
    if not all(row.get(field) for field in (
        "candidate", "operation", "repeat", "wall_seconds", "exit_code"
    )):
        raise SystemExit(f"incomplete raw timing row: {row}")
    actual_repeats[(row["candidate"], row["operation"])].append(int(row["repeat"]))
actual_repeats = {key: sorted(values) for key, values in actual_repeats.items()}
if actual_repeats != expected_repeats:
    raise SystemExit(
        f"unexpected timing matrix: expected={expected_repeats}, actual={actual_repeats}"
    )

groups = defaultdict(list)
for row in rows:
    if row["exit_code"] == "0" and row["operation"] in {
        "dirty-repair", "idempotent-repair", "warm-validation", "full-reactor"
    }:
        groups[(row["candidate"], row["operation"])].append(
            Decimal(row["wall_seconds"])
        )

for (candidate, operation), values in sorted(groups.items()):
    print(
        candidate,
        operation,
        f"runs={len(values)}",
        f"median={rounded(statistics.median(values), 2)}",
        f"range={rounded(min(values), 2)}-{rounded(max(values), 2)}",
        sep="\t",
    )

with open(published_path, newline="", encoding="utf-8") as source:
    published_rows = list(csv.DictReader(source))
published_keys = [
    (row["candidate"], row["operation"], row["state"], int(row["repeat"]))
    for row in published_rows
]
if len(published_keys) != len(set(published_keys)):
    raise SystemExit("duplicate published timing key")
published = {
    (row["candidate"], row["operation"], row["state"], int(row["repeat"])): row
    for row in published_rows
}
aliases = {
    "baseline": "mycila-5.0.0_formatter-2.29.0",
    "specialist": "mycila-5.1.1_formatter-2.29.0",
    "spotless": "spotless-3.9.0-consolidated",
    "hybrid": "mycila-5.1.1_spotless-3.9.0-format-only",
}
operation_map = {
    "dirty-repair": ("repair", "dirty"),
    "idempotent-repair": ("repair", "idempotent"),
    "warm-validation": ("clean-check", "warm"),
    "full-reactor": ("full-reactor", "clean"),
}
drift = []
for row in rows:
    candidate = row["candidate"]
    operation = row["operation"]
    if candidate == "rat" and operation == "clean-validation":
        key = ("apache-rat-0.18", "java-corpus-check", "clean", 1)
    elif operation == "clean-validation" and candidate in aliases:
        key = (aliases[candidate], "clean-check", "initial", 1)
    elif candidate in aliases and operation in operation_map:
        published_operation, state = operation_map[operation]
        key = (aliases[candidate], published_operation, state, int(row["repeat"]))
    else:
        continue
    expected = published.get(key)
    if expected is None or not expected["wall_seconds"]:
        raise SystemExit(f"missing published timing for {key}")
    if f'{float(row["wall_seconds"]):.2f}' != f'{float(expected["wall_seconds"]):.2f}':
        drift.append((key, expected["wall_seconds"], row["wall_seconds"]))

if drift:
    raise SystemExit(
        f"runtime drift detected; preserve {capture_path} and review: {drift}"
    )

published_groups = defaultdict(list)
for row in published_rows:
    if row["wall_seconds"]:
        published_groups[(row["candidate"], row["operation"], row["state"])].append(
            Decimal(row["wall_seconds"])
        )

report_labels = {
    aliases["baseline"]: "Mycila 5.0.0 + formatter 2.29.0",
    aliases["specialist"]: "Mycila 5.1.1 + formatter 2.29.0",
    aliases["spotless"]: "Spotless 3.9.0 consolidated",
    aliases["hybrid"]: "Mycila 5.1.1 + Spotless format-only",
}
full_medians = {
    candidate: statistics.median(
        published_groups[(candidate, "full-reactor", "clean")]
    )
    for candidate in report_labels
}
baseline_median = full_medians[aliases["baseline"]]
expected_report_rows = {}
for candidate, label in report_labels.items():
    dirty = published_groups[(candidate, "repair", "dirty")]
    idempotent = published_groups[(candidate, "repair", "idempotent")]
    warm = published_groups[(candidate, "clean-check", "warm")]
    full = published_groups[(candidate, "full-reactor", "clean")]
    if candidate == aliases["baseline"]:
        observed = "Baseline"
    else:
        percent = (full_medians[candidate] / baseline_median - 1) * 100
        observed = rounded(percent, 1)
        if not observed.startswith("-"):
            observed = f"+{observed}"
        observed += "%"
        if candidate == aliases["spotless"]:
            observed += ", but with churn"
        elif candidate == aliases["hybrid"]:
            observed += ", byte-identical"
    expected_report_rows[label] = [
        rounded(statistics.median(dirty), 2),
        rounded(statistics.median(idempotent), 2),
        rounded(statistics.median(warm), 2),
        f"{rounded(min(full), 2)}–{rounded(max(full), 2)}",
        rounded(full_medians[candidate], 2),
        observed,
    ]

with open(report_path, encoding="utf-8") as source:
    report_lines = source.readlines()
runtime_header = (
    "| Candidate | Dirty repair | Idempotent repair | Warm validation | "
    "Full-reactor range | Full-reactor median | Observed versus baseline |"
)
try:
    table_start = next(
        index for index, line in enumerate(report_lines) if line.strip() == runtime_header
    )
except StopIteration as error:
    raise SystemExit("runtime results table header not found in report") from error
actual_report_rows = {}
for line in report_lines[table_start + 2:]:
    if not line.startswith("|"):
        break
    cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
    if len(cells) != 7:
        raise SystemExit(f"unexpected runtime results row: {line.rstrip()}")
    if cells[0] in actual_report_rows:
        raise SystemExit(f"duplicate runtime results row: {cells[0]}")
    actual_report_rows[cells[0]] = cells[1:]
if actual_report_rows != expected_report_rows:
    raise SystemExit(
        "runtime results table differs from the checked-in CSV: "
        f"expected={expected_report_rows}, actual={actual_report_rows}"
    )
PY
```

No candidate POM, candidate header, generated output, or repository-wide reformat from the temporary benchmark copies is present in this spike branch.
