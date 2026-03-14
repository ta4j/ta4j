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

- **During development:** Use narrow Maven test commands for fast feedback (e.g., `mvn -pl ta4j-core test -Dtest=...`). For focused module testing, you may use `scripts/run-full-build-quiet.sh -pl ta4j-core` to get filtered logs while still validating a single module.
- **Before completion:** ALWAYS run `scripts/run-full-build-quiet.sh` (without `-pl` flags), unless the only changes are within `.github/workflows/`, `CHANGELOG.md`, or documentation-only files (for example: `*.md`, `docs/`).
- The script stores the full log under `.agents/logs/` and prints aggregated test totals.
- Use `git diff -- path/to/file` to keep diffs scoped when full-file output is large.
- When `git status` times out, narrow the scope with `git status --short path` instead of retrying broad commands.
- All new or changed code from feature work or bug fixes must be covered by comprehensive unit tests that demonstrate correctness and serve as a shield against future regressions.
- When debugging issues, take every opportunity to create focused unit tests that allow you to tighten the feedback loop. When possible design the tests to also serve as regression bulwarks for future changes.
- At the end of every development cycle (code materially impacted), capture the changes into `CHANGELOG.md` under the appropriate section using existing style/format conventions. Avoid duplicates and consolidate `Unreleased` items as needed.
- CHANGELOG entries must be public-facing release notes only. Do **not** log internal-only refactors/churn (for example private test fixture moves, helper renames, or files never published in `master`).
- Scope CHANGELOG updates to changes that are already publicly visible in `master` or are pending inclusion for end users in the current release line.
- **Critical:** Do not ignore build errors even if they appear pre-existing. Investigate and resolve root causes, and never skip failing tests without explicit user approval.
- Update or add `AGENTS.md` files in subdirectories when you discover local conventions that are worth making explicit for future agents.
- When tweaking rule/indicator/strategy serialization tests, prefer canonical comparisons instead of brittle string equality. The helper `RuleSerializationRoundTripTestSupport` already normalizes `ComponentDescriptor`s (sorted children, normalized numeric strings); reuse it rather than hand-rolled assertions so constructor inference-induced ordering changes do not break tests.
- **Public API stability:** Breaking changes are acceptable for public APIs introduced or tagged within `0.1.0` of the current release (including APIs tagged at the current latest version); treat those as not contract-breaking.

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
- Reuse audit checkpoint: before introducing a new type, run a targeted code search for equivalent behavior and capture the reuse decision in 1-2 lines in PRD/checklist or PR notes.

## 7) Consolidation and API exposure (MUST)

- Consolidation-first helper rule: inline new logic into the owning type (private static methods or package-private nested helpers) before creating a new utility class.
- Only extract to a dedicated helper/utility class after at least two concrete call sites require shared behavior, or when testability/complexity clearly demands extraction.
- Readability-first helper rule: do not extract private helpers that are only 1-3 lines and used once unless the extracted name carries important domain meaning or materially simplifies a long method.
- When a tiny helper forces readers to jump around for one or two lines of logic, inline it back into the main flow.
- When stronger invariants already hold at the call site, prefer direct code over generic fallback helpers that obscure those invariants.
- New classes and methods should be package-private by default; treat public API additions as exceptional and require a short rationale in PRD/checklist or PR notes.
- Redundancy cleanup requirement: when new code overlaps existing behavior, remove or fold the redundant abstraction in the same change unless there is a documented blocker.

## 8) Local typing style (MUST)

- Prefer explicit local variable types.
- Use `var` only when the type is immediately and unambiguously obvious from the right-hand side.
- Do not use `var` for method-return values unless the type is trivial and fully clear from constructor/factory literal context.
