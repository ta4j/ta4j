# AGENTS instructions for ta4j (root)

This file contains only repository-wide requirements.
If your framework already injected this file into context, do not spend extra tool calls reopening it repeatedly.

## 1) Completion gate (MUST)
Select verification by the changed surface; CI uses `scripts/check_scope.py` for the same routing.

- Java, Maven POM/build inputs and compiled resources: run `scripts/run-full-build-quiet.sh` (Windows: `scripts/run-full-build-quiet.ps1`). These wrappers run only the Maven gate.
- Scripts, GitHub Actions, wrapper scripts and workflow instructions: run `scripts/run-script-checks.sh` (Windows adapter: `scripts/run-full-build-quiet.ps1 --preflight-only`). This standalone suite runs actionlint and isolated script fixtures, with expensive tools mocked.
- Mixed changes require both lanes. Documentation-only changes may omit tests unless they change executable workflow instructions or script contracts.
- Do not run Maven solely because a script/action file changed. Do not run script/action fixtures as part of an ordinary Maven build or a Java-only CI change.

Required outcome: every selected lane is green. Report its result and emitted log path under `.agents/logs/`; when Maven ran, also report aggregated `Tests run / Failures / Errors / Skipped` with zero failures/errors. Run focused tests during iteration and each selected final gate once on the candidate patch.

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

Before Java implementation, API design (including documentation-only PRDs and checklists), test changes, test-runtime optimization, or reviews of those surfaces, read [JAVA_REVIEW.md](JAVA_REVIEW.md). It owns the reuse audit, consolidation/public-surface rules, local typing style, and test-runtime discipline. Documentation-only work may omit it only when it does not design, specify, or review Java/API/test behavior. The documentation-only completion-gate exception above does not waive these engineering requirements.
