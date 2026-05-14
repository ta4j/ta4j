# Contributing to ta4j

ta4j has been around for years and serves a large, diverse user base. Contributions are very welcome, but long‑term maintainability takes precedence over quick wins. Please keep the sections below in mind before filing an issue or opening a PR.

## Principles

1. **Public APIs are contracts.** Moving classes between packages, renaming methods, or otherwise breaking binary/source compatibility forces every downstream user into a refactor. We only accept breaking changes when the value dramatically outweighs the disruption, and even then they must ship with deprecation shims and migration notes.
2. **Opinionated implementations belong outside the core.** ta4j aims to be widely applicable. Highly subjective “feature bundles” (e.g., metric dashboards, bespoke reporting formats, hard-coded broker behaviors) are better published as separate modules or example projects. Keep contributions focused on reusable primitives.
3. **Additive code beats churn.** New indicators, rules, serialization helpers, and documentation are great. Mechanical refactors (“just moved files around”) or stylistic changes with no behavioral impact rarely get merged.
4. **Tests tell the story.** Every change—bug fix or feature—needs focused tests demonstrating the behavior and guarding against regressions.
- **Run this before opening or updating a PR:** `mvn -B verify`
  This matches the main CI path and keeps SpotBugs and JaCoCo advisory in the full contributor flow.

- **Use focused local quality loops when iterating:** `mvn -pl ta4j-core -am spotbugs:check` and `mvn -pl ta4j-core -am test jacoco:report jacoco:check`
  These are intentionally strict for the module you are changing, so you can tighten one tool at a time before rerunning the full `mvn -B verify`.

- **Fix formatting and license headers when needed:** `mvn -B license:format formatter:format`
  First-time contributors almost always hit this; run the formatter command locally before your final `mvn -B verify`.

## Contribution checklist

1. **Use Java 25+ and Maven 3.9+.** The build enforces these versions during Maven validation.
2. **Start with an issue** for anything non-trivial. Use it to confirm fit with the [Roadmap](https://ta4j.github.io/ta4j-wiki/Roadmap-and-Tasks.html) and to align on scope. [Search existing issues](https://github.com/ta4j/ta4j/issues?q=is%3Aissue) before opening a new one.
3. **Fork & branch** from `master`.
   ```bash
   git clone https://github.com/<you>/ta4j.git
   cd ta4j
   git checkout -b feature/your-topic
   ```
4. **Implement + test.** Run the full build before pushing:
   ```bash
   mvn -B clean license:format formatter:format test install
   ```
   CI will fail if your changes are not formatted or lack the project license header. First-time contributors almost always hit this; run the command locally first.
   Update `CHANGELOG.md` when you add, fix, or change behavior.
5. **Open the PR** against `ta4j/master`. Draft PRs are encouraged for early feedback. Prefer [well-formed commit messages](http://tbaggery.com/2008/04/19/a-note-about-git-commit-messages.html).

**Optional: Enable workflow linting hook** - If you're modifying GitHub Actions workflows, enable the pre-push hook to catch syntax errors early:
```bash
git config core.hooksPath .githooks
```
Then install `actionlint` (e.g., `brew install actionlint`). The hook will automatically lint any modified files under `.github/workflows/` before pushing.

## Contribution priorities

1. Items on the [Roadmap](https://ta4j.github.io/ta4j-wiki/Roadmap-and-Tasks.html).
2. Additive indicators/criteria/rules that do not change existing behavior.
3. Test coverage or documentation improvements.
4. Bug fixes (smaller, localized fixes are easier to land; large refactors should be discussed first).
5. API changes: only with clear justification, deprecation shims, and migration docs.

## Coding expectations

- Favor clarity over cleverness; write the code you’d want to debug a year from now.
- Keep PRs scoped. If you find unrelated issues, file them or send separate PRs.
- Every new public class/method needs Javadoc with `@since <version>`.
- Use primitives for indicator parameters (e.g., `int timeFrame`). Convert to `Num` inside using `series.numFactory()`.
- Do not cache `Num` instances globally—always obtain them from the relevant factory.

## Indicator contributions

Open an issue to discuss the new indicator first. Every indicator must ship with matching tests:
- `src/main/java/org/ta4j/core/indicators/.../NewIndicator.java`
- `src/test/java/org/ta4j/core/indicators/.../NewIndicatorTest.java`

## Tagged test workflows

Regular PR and push CI skips test tags configured by `ta4j.excludedTestTags`.
Run tagged suites manually from GitHub Actions, or locally with:

- `xvfb-run mvn -B test -Dgroups=integration -Dta4j.excludedTestTags=analysis-demo,lppl-sector-rotation-demo,elliott-macro-cycle-replay`
- `xvfb-run mvn -B test -Dgroups=benchmark -Dta4j.excludedTestTags= -Dta4j.runBenchmarks=true`
- `xvfb-run mvn -B test -Dgroups=analysis-demo -Dta4j.excludedTestTags=lppl-sector-rotation-demo,elliott-macro-cycle-replay -Dta4j.analysisDemoInstrument=coinbase:BTC-USD -Dta4j.analysisDemoOutputDir=target/analysis-demos/elliott-wave`
- `xvfb-run mvn -B test -Dgroups=lppl-sector-rotation-demo -Dta4j.excludedTestTags=analysis-demo,elliott-macro-cycle-replay -Dta4j.lpplDemoOutputDir=target/analysis-demos/lppl-sector-rotation -Dta4j.lpplReferenceDataDir=ta4j-examples/src/main/resources`
- `xvfb-run mvn -B test -Dgroups=elliott-macro-cycle-replay -Dta4j.excludedTestTags= -Dtest=ElliottWaveMacroCycleDetectorTest,ElliottWaveBtcMacroCycleDemoTest`

These examples match the Linux GitHub Actions runners. On macOS, use XQuartz or
run the Maven command without `xvfb-run` when your local display can satisfy
UI-dependent tests. On Windows, use WSL2, a CI runner, or an equivalent X server.

The dedicated workflows are:

- `Run Integration Tagged Tests` (`.github/workflows/test-tag-integration.yml`)
- `Run Benchmark Tagged Tests` (`.github/workflows/test-tag-benchmark.yml`)
- `Run Analysis Demo Tagged Tests` (`.github/workflows/test-tag-analysis-demo.yml`)
- `Run LPPL Sector Rotation Demo Tagged Tests` (`.github/workflows/test-tag-lppl-sector-rotation-demo.yml`)
- `Run Elliott Macro Cycle Replay Tagged Tests` (`.github/workflows/test-tag-elliott-macro-cycle-replay.yml`)

Scheduled runs are opt-in per tag except `lppl-sector-rotation-demo`, which runs
weekly by default unless
`TA4J_TAGGED_TEST_LPPL_SECTOR_ROTATION_DEMO_SCHEDULE_ENABLED=false`. For other
tagged schedules, set `TA4J_TAGGED_TEST_<TAG>_SCHEDULE_ENABLED=true` and
`TA4J_TAGGED_TEST_<TAG>_SCHEDULE_SLOT=daily`, `weekly`, or `monthly`. Daily and
monthly LPPL runs also require the LPPL schedule variable to be enabled and the
slot set to `daily` or `monthly`. Manual workflow dispatches run regardless of
the schedule variables. The `elliott-macro-cycle-replay` workflow is manual-only
and requires a self-hosted runner labeled `ta4j-macro-cycle-replay`.

The `analysis-demo` tag is for Elliott examples that produce analysis reports
and must be the only JUnit tag on each tagged test or class.
Its workflow defaults to `coinbase:BTC-USD`, accepts provider-qualified manual
inputs such as `coinbase:ETH-USD` or `coinbase:ETH/USD`, and uploads generated
JSON, charts, and cached provider responses from
`target/analysis-demos/elliott-wave/**`. Set `TA4J_ANALYSIS_DEMO_INSTRUMENT` to
override the scheduled Elliott instrument.

The `lppl-sector-rotation-demo` tag is for the SPDR LPPL sector rotation report.
Its workflow writes LPPL reports, refreshed reference-data copies, and Yahoo
response caches under `target/analysis-demos/lppl-sector-rotation/**`. Scheduled
and manually dispatched LPPL workflow runs set `ta4j.lpplUpdateReferenceData=true`;
when adjusted SPDR resource files change, the workflow opens or updates the
`automation/lppl-spdr-reference-data` branch instead of pushing directly to
`master`.

## API lifecycle and @since policy

- Add `@since <version>` to every newly introduced class and API member, using the introducing release version without `-SNAPSHOT` (for example `0.22.4`, not `0.22.4-SNAPSHOT`).
- This gives us a reliable introduction point for deprecation tracking and lifecycle automation.
- New API is considered volatile for the next 5 minor releases after it is introduced.
- Example: API added in `0.22.4` may still change incompatibly, or be removed, through `0.27.4` (inclusive).
- Treat this window as experimental/beta and avoid production-critical dependency unless you explicitly accept migration risk.

## Branching model

Enhancements, new features and fixes should be pushed to a [fork](https://help.github.com/articles/fork-a-repo/) of the master branch. Once completed they will be merged with the master branch during a [pull request](https://help.github.com/articles/about-pull-requests/). GitHub actions are configured to run the tests, validate the licence header and source code format. After the PR has been merged a new SNAPSHOT will be deployed.

This development process is similar to [github flow](https://docs.github.com/en/get-started/quickstart/github-flow).

* **Only the content of the master branch is going to become a release.**
* **There is no release branch nor a mandatory develop branch**

### Release Process

For maintainers, the release process is fully automated using GitHub Actions workflows. The process includes:

- **Automated release scheduling**: AI-powered scheduler analyzes changes and determines version bumps (patch/minor/major)
- **Two-phase release workflow**: `prepare-release.yml` prepares release commits and PRs, `publish-release.yml` handles tagging and deployment
- **Release health monitoring**: Automated checks for tag reachability, version drift, and stale release PRs
- **GitHub Release automation**: Automatic creation of GitHub Releases with artifacts and release notes

For detailed information about the release process, see [RELEASE_PROCESS.md](../RELEASE_PROCESS.md) in the main repository.

## Quick tips

- Use `series.getBeginIndex()` instead of `0` when iterating a `BarSeries`.
- Remember the difference between `DecimalNum.min(...)` and `DecimalNum.minus(...)`.
- When in doubt, ask. It’s easier (and faster) to course-correct early than to rework a large PR later.