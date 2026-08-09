# License-header and Java-formatting tooling spike

Date: 2026-08-08

Baseline: `ta4j/ta4j` `origin/master` at `6d8f05a2e63512bf1b84568594165b4100293bfc`

Raw measurements: [cf-411-license-formatting-results.csv](cf-411-license-formatting-results.csv)

## Recommendation

Adopt a hybrid in a separately reviewed implementation change:

1. Upgrade `com.mycila:license-maven-plugin` from 5.0.0 to 5.1.1 and keep it as the license-header owner.
2. Replace `net.revelc.code.formatter:formatter-maven-plugin` 2.29.0 with `com.diffplug.spotless:spotless-maven-plugin` 3.9.0, configured only with Eclipse JDT 4.37 and the existing `code-formatter.xml`.
3. Do not enable Spotless license-header handling and do not add Apache RAT.

This is the best measured path because it is byte-identical to the current stack across all 1,463 tracked Java files, preserves the one legacy full MIT notice, reduces the median Java 25 full-reactor gate from 71.45s to 63.45s (11.2%), reduces warm validation from about 2.0s to about 1.3s, and reduces the statically resolved plugin runtime surface from 65 to 42 artifacts. The spike itself does not migrate production tooling.

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

The deterministic dirty corpus changed representative production and test files:

- removed the SPDX header and malformed interface spacing in `Bar.java`;
- malformed class spacing in `EMAIndicator.java`;
- converted `BarSeriesTest.java` to CRLF;
- retained Javadocs, annotations, imports, multi-module inheritance, and the legacy two-header `TrailingStopLossRuleTest.java` edge case.

For each candidate the trial first proved that non-mutating validation rejected the dirty corpus, then repaired it, compared all Java outputs, ran two idempotent repair repetitions, ran three warm validation repetitions, and ran two clean full-reactor repetitions. The aggregate Java-tree hash for the current, upgraded-specialist, and recommended hybrid outputs was:

```text
902f8eae205ce91a7b1b4cfecf45a6e873dd62e49be3f61726e0b25c317ed506
```

## Behavioral results

| Candidate | Dirty rejection | Clean validation | Output versus current | Idempotent | Multi-module / Java 25 |
| --- | --- | --- | --- | --- | --- |
| Mycila 5.0.0 + formatter 2.29.0 | Pass | Pass | Baseline | Pass | Pass |
| Mycila 5.1.1 + formatter 2.29.0 | Pass | Pass | Byte-identical, 1,463/1,463 | Pass | Pass |
| Spotless 3.9.0 for both concerns | Pass | Fails before migration churn | 1,462/1,463 identical; deletes 22 legacy MIT-notice lines | Pass after churn | Pass after churn |
| Mycila 5.1.1 + Spotless format-only | Pass | Pass | Byte-identical, 1,463/1,463 | Pass | Pass |
| Apache RAT 0.18 | Missing header rejected | Existing SPDX recognized | Does not format or repair | Not applicable | Java-only corpus passed |

The consolidated Spotless configuration is not a zero-churn replacement. Spotless treats all content before the Java delimiter as the replaceable header section. On the clean repository it proposed deleting the redundant but intentional-looking full MIT block that follows the SPDX block in `TrailingStopLossRuleTest.java`. A filename-specific workaround would add policy complexity, while excluding the file would weaken header coverage. Keeping Mycila as header owner avoids both outcomes.

Spotless and the current formatter both normalized the CRLF fixture to the repository's `.gitattributes` LF contract. Spotless's default `GIT_ATTRIBUTES_FAST_ALLSAME` mode is therefore compatible with the current tree.

## Runtime results

Wall-clock seconds; lower is better. Full-reactor rows are two independent `clean ... verify` runs. Idempotent repair and validation rows report the median of repeated post-repair runs; dirty repair is the first repair of the deliberately malformed corpus.

| Candidate | Dirty repair | Idempotent repair | Warm validation | Full-reactor range | Full-reactor median | Versus baseline |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Mycila 5.0.0 + formatter 2.29.0 | 4.32 | 2.85 | 2.04 | 67.78–75.12 | 71.45 | Baseline |
| Mycila 5.1.1 + formatter 2.29.0 | 5.02 | 2.99 | 2.05 | 77.34–93.33 | 85.34 | +19.4% |
| Spotless 3.9.0 consolidated | 5.78 | 2.03 | 1.56 | 58.77–58.99 | 58.88 | -17.6%, but with churn |
| Mycila 5.1.1 + Spotless format-only | 2.33 | 1.62 | 1.31 | 62.79–64.10 | 63.45 | -11.2%, byte-identical |

The specialist full-gate variance is too large to attribute to Mycila itself; its focused results are effectively equal to the baseline. The Spotless-backed candidates consistently reduce formatter overhead. With only two full-reactor repetitions, these values are directional rather than a general performance guarantee.

## Dependency and advisory surface

`dependency:resolve-plugins` supplied each plugin's isolated runtime classpath. Each exact Maven coordinate/version was queried through the [OSV batch API](https://google.github.io/osv.dev/api/#tag/api/operation/OSV_QueryAffectedBatch) on 2026-08-08.

| Plugin | Runtime artifacts | OSV advisory IDs | Affected components |
| --- | ---: | ---: | ---: |
| Mycila 5.0.0 | 13 | 2 | 2 |
| Mycila 5.1.1 | 14 | 0 | 0 |
| formatter-maven-plugin 2.29.0 | 52 | 9 | 4 |
| Spotless Maven 3.9.0 | 28 | 0 | 0 |
| Apache RAT 0.18 | 49 | 2 | 2 |

The current two-plugin stack resolves 65 artifacts. The proposed Mycila/Spotless stack resolves 42, a 35% reduction. Matches in the current stack include `commons-io` 2.11.0 and `plexus-utils` 4.0.2 under Mycila 5.0.0, plus Jackson 2.20.0, `plexus-utils` 4.0.2, and jsoup 1.21.2 under formatter-maven-plugin. These are inventory matches, not an exploitability finding for ta4j's trusted build inputs.

Spotless provisions the selected Eclipse formatter through its Eclipse/P2 path at runtime. Those dynamically provisioned artifacts are not included in the 28-artifact Maven plugin classpath or the OSV count, so the security comparison is a useful lower-bound inventory rather than a complete software-bill-of-materials claim.

## Decision matrix

Scores are 1–5. Weighted total is out of 100: behavior parity 30%, migration churn/safety 20%, measured performance 15%, maintenance 15%, dependency/advisory surface 10%, contributor and IDE ergonomics 10%.

| Candidate | Parity | Safety | Performance | Maintenance | Surface | Ergonomics | Weighted |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Keep pinned stack | 5 | 5 | 3 | 3 | 2 | 4 | 80.0 |
| Upgrade Mycila, keep formatter | 5 | 5 | 3 | 5 | 2 | 4 | 86.0 |
| Consolidate fully on Spotless | 4 | 3 | 5 | 5 | 4 | 5 | 84.0 |
| Mycila 5.1.1 + Spotless format-only | 5 | 5 | 4.5 | 5 | 4 | 4 | **94.5** |

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

1. Change Mycila to 5.1.1, replace formatter-maven-plugin with format-only Spotless, and retain both `license-header.txt` and `code-formatter.xml` unchanged.
2. Update the shell and PowerShell quiet-build goals, quality-scan contract fixtures, README, contributing guide, PR template, and any hosted validation references from `formatter:format` / `formatter:validate` to `spotless:apply` / `spotless:check`.
3. Assert a zero-source-diff migration before committing, then run the canonical Java 25 gate and hosted CI.
4. Add an `Unreleased` changelog entry because the contributor and maintainer command contract changes.

Rollback is a single configuration/command-map revert: restore formatter-maven-plugin 2.29.0, Mycila 5.0.0 if necessary, and the former formatter goals. Because the proposed migration is byte-identical, rollback requires no source reformat or data migration.

## Reproduction commands

Candidate copies were built from `git archive 6d8f05a2e63512bf1b84568594165b4100293bfc`. With `JAVA_HOME` set to Homebrew OpenJDK 25, the decisive commands were:

### Exact candidate construction

Create five independent copies from the pinned baseline:

```bash
SPIKE_ROOT=$(mktemp -d /tmp/cf411-formatting-spike.XXXXXX)
for candidate in baseline specialist spotless hybrid rat; do
  mkdir -p "$SPIKE_ROOT/$candidate"
  git archive --format=tar 6d8f05a2e63512bf1b84568594165b4100293bfc \
    | tar -xf - -C "$SPIKE_ROOT/$candidate"
done
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

In `hybrid/pom.xml`, change Mycila to 5.1.1, remove formatter-maven-plugin, and add the format-only Spotless block from [Proposed follow-up configuration](#proposed-follow-up-configuration). Do not configure Spotless's `licenseHeader` step.

In `rat/pom.xml`, add the following audit-only plugin configuration. The explicit `inputInclude` is what limits the passing result to the Java corpus; an unscoped repository-wide run instead reports 27 unapproved documentation and configuration files.

```xml
<plugin>
    <groupId>org.apache.rat</groupId>
    <artifactId>apache-rat-plugin</artifactId>
    <version>0.18</version>
    <configuration>
        <inputInclude>**/*.java</inputInclude>
    </configuration>
</plugin>
```

Apply the same deterministic dirty corpus to each formatting candidate before its rejection and repair trials. Keep the `rat` copy clean for its audit-only sequence below.

```bash
for candidate in baseline specialist spotless hybrid; do
  root="$SPIKE_ROOT/$candidate"
  sed -i '' '1,3d' "$root/ta4j-core/src/main/java/org/ta4j/core/Bar.java"
  perl -0pi -e \
    's/public interface Bar extends Serializable \{/public   interface Bar extends Serializable\{/' \
    "$root/ta4j-core/src/main/java/org/ta4j/core/Bar.java"
  perl -0pi -e \
    's/public class EMAIndicator extends AbstractEMAIndicator \{/public  class EMAIndicator extends AbstractEMAIndicator\{/' \
    "$root/ta4j-core/src/main/java/org/ta4j/core/indicators/averages/EMAIndicator.java"
  perl -pi -e 's/\n/\r\n/g' \
    "$root/ta4j-core/src/test/java/org/ta4j/core/BarSeriesTest.java"
done
```

After repair, calculate the reported aggregate Java-tree hash from each formatting candidate root:

```bash
for candidate in baseline specialist spotless hybrid; do
  (
    cd "$SPIKE_ROOT/$candidate" || exit 1
    find ta4j-core/src ta4j-examples/src -type f -name '*.java' -print \
      | LC_ALL=C sort \
      | xargs shasum -a 256 \
      | shasum -a 256
  )
done
```

The consolidated candidate's differing aggregate hash is fully accounted for by:

```bash
(
  cd "$SPIKE_ROOT" || exit 1
  diff -rq baseline/ta4j-core/src spotless/ta4j-core/src
  diff -rq baseline/ta4j-examples/src spotless/ta4j-examples/src
  diff -u \
    baseline/ta4j-core/src/test/java/org/ta4j/core/rules/TrailingStopLossRuleTest.java \
    spotless/ta4j-core/src/test/java/org/ta4j/core/rules/TrailingStopLossRuleTest.java
)
```

### Candidate executions

```bash
# Current and upgraded-specialist candidates
for candidate in baseline specialist; do
  (
    cd "$SPIKE_ROOT/$candidate" || exit 1
    ./mvnw -q -B license:check formatter:validate
    ./mvnw -q -B license:format formatter:format
    ./mvnw -q -B clean license:check formatter:validate verify \
      -Dta4j.excludedTestTags=analysis-demo,benchmark,requires-display,requires-headless
  )
done

# Consolidated Spotless candidate
(
  cd "$SPIKE_ROOT/spotless" || exit 1
  ./mvnw -q -B spotless:check
  ./mvnw -q -B spotless:apply
  ./mvnw -q -B clean spotless:check verify \
    -Dta4j.excludedTestTags=analysis-demo,benchmark,requires-display,requires-headless
)

# Recommended hybrid candidate
(
  cd "$SPIKE_ROOT/hybrid" || exit 1
  ./mvnw -q -B license:check spotless:check
  ./mvnw -q -B license:format spotless:apply
  ./mvnw -q -B clean license:check spotless:check verify \
    -Dta4j.excludedTestTags=analysis-demo,benchmark,requires-display,requires-headless
)

# RAT's audit-only Java corpus
(
  cd "$SPIKE_ROOT/rat" || exit 1
  ./mvnw -q -B apache-rat:check
  sed -i '' '1,3d' ta4j-core/src/main/java/org/ta4j/core/Bar.java
  if ./mvnw -q -B apache-rat:check; then
    echo "Expected RAT to reject the missing Bar.java header" >&2
    exit 1
  fi
)
```

No candidate POM, candidate header, generated output, or repository-wide reformat from the temporary benchmark copies is present in this spike branch.
