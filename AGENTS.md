# AGENTS instructions for ta4j (root)

This file contains only repository-wide requirements.
If your framework already injected this file into context, do not spend extra tool calls reopening it repeatedly.

## 1) Completion gate (MUST)
Run the platform-native full verification script once when you have a candidate final patch:

- Windows: `scripts/run-full-build-quiet.ps1`
- macOS/Linux/Git Bash/WSL: `scripts/run-full-build-quiet.sh`
You may skip this only when all changed files are exclusively within `.github/workflows/`, `CHANGELOG.md`, or documentation-only files (for example `*.md`, `docs/`).

Required outcome:

- Build is GREEN (`Failures: 0`, `Errors: 0`)
- Final report includes aggregated `Tests run / Failures / Errors / Skipped`
- Final report includes the log path emitted by the script (under `.agents/logs/`)

Do not rerun the full build after every edit. Use targeted tests while iterating, then run the full gate on the candidate final patch.

## 2) Fast feedback loop (SHOULD)
During implementation and debugging, prefer targeted test commands for speed (for example `mvn -pl ta4j-core test -Dtest=...`).

## 3) Test failure policy (MUST)

- Never ignore build or test failures.
- Never skip tests without explicit user approval.

## 4) Scoped AGENTS lookup (MUST)
Before editing a feature area, discover and follow all applicable scoped `AGENTS.md` files.

- Use `bash scripts/agents_for_target.sh <file-or-class>` to list path-scoped `AGENTS.md` guides for that target in precedence order from the current repo/workspace root.
- Deeper/closer `AGENTS.md` files override broader ones.
- Apply only instructions relevant to the path you are changing.
- This helper is only for path-scoped `AGENTS.md` discovery. Agents must still apply normal system, developer, user, and workflow-specific instructions independently, including any personal PR workflow guidance.

## 5) Process/worktree guidance
Load `scripts/AGENTS.md` for worktree lifecycle or PRD/checklist operations, and when it is an ancestor of a changed script. Do not load it merely to read unrelated code.

## Task-specific engineering requirements

Before Java implementation, API design, test changes, test-runtime optimization, or reviews of those surfaces, read [JAVA_REVIEW.md](JAVA_REVIEW.md). It owns the reuse audit, consolidation/public-surface rules, local typing style, and test-runtime discipline. Documentation-only work need not load those details.
