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
