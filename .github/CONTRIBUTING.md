# Contributing to ta4j

ta4j has been around for years and serves a large, diverse user base. Contributions are very welcome, but long‑term maintainability takes precedence over quick wins. Please keep the sections below in mind before filing an issue or opening a PR.

## Principles

1. **Public APIs are contracts.** Moving classes between packages, renaming methods, or otherwise breaking binary/source compatibility forces every downstream user into a refactor. We only accept breaking changes when the value dramatically outweighs the disruption, and even then they must ship with deprecation shims and migration notes.
2. **Opinionated implementations belong outside the core.** ta4j aims to be widely applicable. Highly subjective “feature bundles” (e.g., metric dashboards, bespoke reporting formats, hard-coded broker behaviors) are better published as separate modules or example projects. Keep contributions focused on reusable primitives.
3. **Additive code beats churn.** New indicators, rules, serialization helpers, and documentation are great. Mechanical refactors (“just moved files around”) or stylistic changes with no behavioral impact rarely get merged.
4. **Tests tell the story.** Every change—bug fix or feature—needs focused tests demonstrating the behavior and guarding against regressions.

## Contribution checklist

1. **Use Java 25+ and Maven 3.9+.** The build enforces these versions during Maven validation.
2. **Start with an issue** for anything non-trivial. Use it to confirm fit with the [Roadmap](https://github.com/ta4j/ta4j-wiki/wiki/Roadmap-and-Tasks) and to align on scope. [Search existing issues](https://github.com/ta4j/ta4j/issues?q=is%3Aissue) before opening a new one.
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

1. Items on the [Roadmap](https://github.com/ta4j/ta4j-wiki/wiki/Roadmap-and-Tasks).
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

### Snapshots

A SNAPSHOT is the latest version of the **next** release. For instance a 0.22.0-SNAPSHOT is the current build that should become the next 0.22.0 release. You can use the current SNAPSHOT version by adding the following dependency to your `pom.xml` file:
```xml
<dependency>
  <groupId>org.ta4j</groupId>
  <artifactId>ta4j-core</artifactId>
  <version>0.22.0-SNAPSHOT</version>
</dependency>
```

The SNAPSHOT version contains all fixes, enhancements and new features that have been added to the master build so far and that will be part of the next release.

### Release Process

For maintainers, the release process is fully automated using GitHub Actions workflows. The process includes:

- **Automated release scheduling**: AI-powered scheduler analyzes changes and determines version bumps (patch/minor/major)
- **Two-phase release workflow**: `prepare-release.yml` prepares release commits and PRs, `publish-release.yml` handles tagging and deployment
- **Release health monitoring**: Automated checks for tag reachability, version drift, and stale release PRs
- **GitHub Release automation**: Automatic creation of GitHub Releases with artifacts and release notes

For detailed information about the release process, see [RELEASE_PROCESS.md](RELEASE_PROCESS.md) in the main repository.

## Quick tips

- Use `series.getBeginIndex()` instead of `0` when iterating a `BarSeries`.
- Remember the difference between `DecimalNum.min(...)` and `DecimalNum.minus(...)`.
- When in doubt, ask. It’s easier (and faster) to course-correct early than to rework a large PR later.