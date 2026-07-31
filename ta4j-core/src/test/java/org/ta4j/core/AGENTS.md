# AGENTS instructions for `org.ta4j.core` tests

Follow `ta4j-core/src/test/java/AGENTS.md` for global test policy; this file adds package-specific guidance.

- Prefer deterministic concurrency tests using `CountDownLatch` / `AtomicBoolean` and bounded executors.
- Always apply timeouts on `Future#get` to prevent hanging builds.
- Use `MockBarBuilderFactory` for lightweight bar creation when time semantics are not under test.
- Keep `BarSeries` histories and walk-forward folds at the smallest size that still exercises the boundary under test; do not copy production-scale histories into neighboring unit tests.
- Reuse immutable parsed fixtures such as `XlsTestsUtils` across test methods instead of reparsing the same resource, and keep one exhaustive numeric-factory or permutation sweep as the behavior owner.
- Assert snapshot semantics on `getBarData()` by verifying returned lists are unmodifiable.
- Prefer `NumFactory` convenience methods (`zero()`, `one()`, `two()`, etc.) over `numOf(...)` for common constants.
