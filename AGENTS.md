# AGENTS instructions for ta4j (root)

This file contains only repository-wide requirements.
If your framework already injected this file into context, do not spend extra tool calls reopening it repeatedly.

## 1) Completion gate (MUST)
Run the full verification script once when you have a candidate final patch:

- `scripts/run-full-build-quiet.sh`

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

- Use `bash scripts/agents_for_target.sh <file-or-class>` to list prevailing guides in precedence order.
- Deeper/closer `AGENTS.md` files override broader ones.
- Apply only instructions relevant to the path you are changing.

## 5) Process/worktree guidance
Worktree lifecycle and PRD/checklist process conventions live in `scripts/AGENTS.md`.

## 6) Reuse-first policy (MUST)

- Before adding a new type or API, search for existing equivalents and reuse them when possible.
- Do not introduce a new enum when an existing project enum already models the behavior.
- Prefer extending/adapting existing classes over creating parallel abstractions.
- If a new type is still required, document in the PRD/checklist or PR notes why existing types were insufficient.

## 7) Local typing style (MUST)

- Prefer explicit local variable types.
- Use `var` only when the type is immediately and unambiguously obvious from the right-hand side.
- Do not use `var` for method-return values unless the type is trivial and fully clear from constructor/factory literal context.
