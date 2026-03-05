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

## API lifecycle and @since policy

- Tag every new class and new API surface with `@since <version>`.
- This is required so we can track deprecations reliably with `DeprecationNotifier` and future automation work (see Linear `CF-16`).
- New classes and APIs are considered volatile for the next 5 minor versions.
- Example: something introduced in `0.22.4` is fair game for API-breaking changes, or even removal, through `0.27.4` (inclusive).
- Treat these as experimental/beta during that window; avoid building production-critical processes on them unless you explicitly accept that risk.
