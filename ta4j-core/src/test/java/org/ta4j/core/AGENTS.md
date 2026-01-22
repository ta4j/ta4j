# AGENTS instructions for org.ta4j.core tests

- Prefer deterministic concurrency tests by coordinating threads with `CountDownLatch`/`AtomicBoolean` and bounded executor services. Always apply timeouts on `Future#get` to avoid hanging builds.
- Use `MockBarBuilderFactory` when a test needs lightweight bar creation that bypasses time calculations.
- Assert snapshot semantics on `getBarData()` by checking that returned lists are unmodifiable when testing concurrent series implementations.
- Prefer `NumFactory` static methods over `numOf()` for common values: use `numFactory.zero()`, `numFactory.one()`, `numFactory.two()`, `numFactory.three()`, `numFactory.hundred()`, `numFactory.thousand()`, and `numFactory.minusOne()` instead of `numFactory.numOf(0)`, `numFactory.numOf(1)`, `numFactory.numOf(2)`, `numFactory.numOf(3)`, `numFactory.numOf(100)`, `numFactory.numOf(1000)`, and `numFactory.numOf(-1)` respectively.
