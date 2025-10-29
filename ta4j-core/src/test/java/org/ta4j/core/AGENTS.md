# AGENTS instructions for org.ta4j.core tests

- Prefer deterministic concurrency tests by coordinating threads with `CountDownLatch`/`AtomicBoolean` and bounded executor services. Always apply timeouts on `Future#get` to avoid hanging builds.
- Use `MockBarBuilderFactory` when a test needs lightweight bar creation that bypasses time calculations.
- Assert snapshot semantics on `getBarData()` by checking that returned lists are unmodifiable when testing concurrent series implementations.
