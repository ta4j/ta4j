# Contributing to ta4j

## Before you submit

- **Run this before every PR (Maven Wrapper recommended):** `./mvnw -B clean license:format formatter:format test install`  
  On Windows, use `mvnw.cmd -B clean license:format formatter:format test install`. If you prefer system Maven, `mvn -B clean license:format formatter:format test install` is also supported.  
  CI will fail if your changes are not formatted or lack the project license header. First-time contributors almost always hit this; run the command locally first.

- [Search existing issues](https://github.com/ta4j/ta4j/issues?q=is%3Aissue) before opening a new one.

- Fork, branch, and open a PR. Significant changes must include tests.

- Prefer [well-formed commit messages](http://tbaggery.com/2008/04/19/a-note-about-git-commit-messages.html).

Optional: to lint workflow changes before pushing, set `git config core.hooksPath .githooks` and install `actionlint` (e.g. `brew install actionlint`).

Ideas: [Roadmap](https://github.com/ta4j/ta4j/wiki/Roadmap) · [Open issues](https://github.com/ta4j/ta4j/issues?q=is%3Aissue+is%3Aopen).

## Tagged test workflows

Regular PR and push CI skips test tags configured by `ta4j.excludedTestTags`.
Run tagged suites manually from GitHub Actions, or locally with:

- `xvfb-run mvn -B test -Dgroups=integration -Dta4j.excludedTestTags=`
- `xvfb-run mvn -B test -Dgroups=slow -Dta4j.excludedTestTags=`
- `xvfb-run mvn -B test -Dgroups=benchmark -Dta4j.excludedTestTags= -Dta4j.runBenchmarks=true`

The dedicated workflows are:

- `Run Integration Tagged Tests` (`.github/workflows/test-tag-integration.yml`)
- `Run Slow Tagged Tests` (`.github/workflows/test-tag-slow.yml`)
- `Run Benchmark Tagged Tests` (`.github/workflows/test-tag-benchmark.yml`)

Scheduled runs are opt-in per tag. Set `TA4J_TAGGED_TEST_<TAG>_SCHEDULE_ENABLED=true`
and `TA4J_TAGGED_TEST_<TAG>_SCHEDULE_SLOT=daily`, `weekly`, or `monthly`.
Unset variables leave scheduled runs disabled, while manual workflow dispatches run
regardless of the schedule variables. Tests with multiple tags run in each matching
workflow.

## API lifecycle and @since policy

- Add `@since <version>` to every newly introduced class and API member, using the introducing release version without `-SNAPSHOT` (for example `0.22.4`, not `0.22.4-SNAPSHOT`).
- This gives us a reliable introduction point for deprecation tracking and lifecycle automation.
- New API is considered volatile for the next 5 minor releases after it is introduced.
- Example: API added in `0.22.4` may still change incompatibly, or be removed, through `0.27.4` (inclusive).
- Treat this window as experimental/beta and avoid production-critical dependency unless you explicitly accept migration risk.
