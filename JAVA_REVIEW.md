# Java and test review requirements

Load before Java implementation, API design, test changes, test-runtime optimization, or reviews of those surfaces. Apply these requirements alongside the target's ancestor-scoped guides; this file does not replace them or the root completion gate.

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

## 9) Test runtime discipline (MUST)

- Use the full gate's Surefire class timings to identify the slowest five classes before optimizing and retain comparable before/after evidence for every targeted class.
- During iteration, run focused `mvn -pl <module> -am -Dtest=<TestClass> test` commands without `clean`; reserve the clean full wrapper for the candidate final patch.
- Keep one exhaustive owner for a meaningful numeric, history, or permutation matrix. Use representative nominal, boundary, invalid, and interaction cases in neighboring tests rather than repeating the full sweep.
- Replace deterministic waits with package-private, production-default-preserving seams; never increase timeouts or skip tests merely to improve wall-clock time.
