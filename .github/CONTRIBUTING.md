# Contributing to ta4j

## Before you submit

- **Use Java 25+ and Maven 3.9+.** The build enforces these versions during Maven validation.

- **Run this before opening or updating a PR:** `mvn -B verify`
  This matches the main CI path and keeps SpotBugs and JaCoCo advisory in the full contributor flow.

- **Use focused local quality loops when iterating:** `mvn -pl ta4j-core -am spotbugs:check` and `mvn -pl ta4j-core -am test jacoco:report jacoco:check`
  These are intentionally strict for the module you are changing, so you can tighten one tool at a time before rerunning the full `mvn -B verify`.

- **Fix formatting and license headers when needed:** `mvn -B license:format formatter:format`
  First-time contributors almost always hit this; run the formatter command locally before your final `mvn -B verify`.

- [Search existing issues](https://github.com/ta4j/ta4j/issues?q=is%3Aissue) before opening a new one.

- Fork, branch, and open a PR. Significant changes must include tests.

- Prefer [well-formed commit messages](http://tbaggery.com/2008/04/19/a-note-about-git-commit-messages.html).

Optional: to lint workflow changes before pushing, set `git config core.hooksPath .githooks` and install `actionlint` (e.g. `brew install actionlint`).

Ideas: [Roadmap](https://github.com/ta4j/ta4j/wiki/Roadmap) · [Open issues](https://github.com/ta4j/ta4j/issues?q=is%3Aissue+is%3Aopen).

## Tagged test workflows

Regular PR and push CI skips test tags configured by `ta4j.excludedTestTags`.
Run tagged suites manually from GitHub Actions, or locally with:

- `xvfb-run mvn -B test -Dgroups=integration -Dta4j.excludedTestTags=analysis-demo,elliott-macro-cycle-replay`
- `xvfb-run mvn -B test -Dgroups=benchmark -Dta4j.excludedTestTags= -Dta4j.runBenchmarks=true`
- `xvfb-run mvn -B test -Dgroups=analysis-demo -Dta4j.excludedTestTags=elliott-macro-cycle-replay -Dta4j.analysisDemoInstrument=coinbase:BTC-USD -Dta4j.analysisDemoOutputDir=target/analysis-demos/elliott-wave`
- `xvfb-run mvn -B test -Dgroups=elliott-macro-cycle-replay -Dta4j.excludedTestTags= -Dtest=ElliottWaveMacroCycleDetectorTest`

These examples match the Linux GitHub Actions runners. On macOS, use XQuartz or
run the Maven command without `xvfb-run` when your local display can satisfy
UI-dependent tests. On Windows, use WSL2, a CI runner, or an equivalent X server.

The dedicated workflows are:

- `Run Integration Tagged Tests` (`.github/workflows/test-tag-integration.yml`)
- `Run Benchmark Tagged Tests` (`.github/workflows/test-tag-benchmark.yml`)
- `Run Analysis Demo Tagged Tests` (`.github/workflows/test-tag-analysis-demo.yml`)
- `Run Elliott Macro Cycle Replay Tagged Tests` (`.github/workflows/test-tag-elliott-macro-cycle-replay.yml`)

Scheduled runs are opt-in per tag. Set `TA4J_TAGGED_TEST_<TAG>_SCHEDULE_ENABLED=true`
and `TA4J_TAGGED_TEST_<TAG>_SCHEDULE_SLOT=daily`, `weekly`, or `monthly`.
Unset variables leave scheduled runs disabled, while manual workflow dispatches run
regardless of the schedule variables. The `elliott-macro-cycle-replay` workflow
is manual-only and requires a self-hosted runner labeled `ta4j-macro-cycle-replay`.

The `analysis-demo` tag is for examples that produce analysis reports and must
be the only JUnit tag on each tagged test or class.
Its workflow defaults to `coinbase:BTC-USD`, accepts provider-qualified manual
inputs such as `coinbase:ETH-USD` or `coinbase:ETH/USD`, and uploads generated
JSON, charts, and cached provider responses from `target/analysis-demos/**`.
Version 1 supports Coinbase instruments only. For scheduled analysis-demo runs,
`weekly` is the intended slot; use
`TA4J_TAGGED_TEST_ANALYSIS_DEMO_SCHEDULE_ENABLED=true` with
`TA4J_TAGGED_TEST_ANALYSIS_DEMO_SCHEDULE_SLOT=weekly`, and set
`TA4J_ANALYSIS_DEMO_INSTRUMENT` to override the scheduled instrument.

## API lifecycle and @since policy

- Add `@since <version>` to every newly introduced class and API member, using the introducing release version without `-SNAPSHOT` (for example `0.22.4`, not `0.22.4-SNAPSHOT`).
- This gives us a reliable introduction point for deprecation tracking and lifecycle automation.
- New API is considered volatile for the next 5 minor releases after it is introduced.
- Example: API added in `0.22.4` may still change incompatibly, or be removed, through `0.27.4` (inclusive).
- Treat this window as experimental/beta and avoid production-critical dependency unless you explicitly accept migration risk.
