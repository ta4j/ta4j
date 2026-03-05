# AGENTS instructions for `org.ta4j.core` tests

Follow `ta4j-core/src/test/java/AGENTS.md` for global test policy; this file adds package-specific guidance.

- Prefer deterministic concurrency tests using `CountDownLatch` / `AtomicBoolean` and bounded executors.
- Always apply timeouts on `Future#get` to prevent hanging builds.
- Use `MockBarBuilderFactory` for lightweight bar creation when time semantics are not under test.
- Assert snapshot semantics on `getBarData()` by verifying returned lists are unmodifiable.
- Prefer `NumFactory` convenience methods (`zero()`, `one()`, `two()`, etc.) over `numOf(...)` for common constants.
