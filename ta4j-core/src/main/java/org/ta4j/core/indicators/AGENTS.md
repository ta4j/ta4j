# Indicators package instructions
- `CachedIndicator` now stores values inside a thread-safe windowed cache backed by a `ConcurrentHashMap` and a `ReentrantLock`.
  * Never bypass `getValue(int)` when retrieving indicator results. Direct writes to the cache should happen exclusively through
    `calculate(int)` to preserve sequential computation guarantees for recursive indicators.
  * When trimming cached entries, always retain at least one historical value prior to the current series window. This seed value
    keeps recursive indicators stable when old bars are evicted.
  * Requests for indices below the cached range automatically return the lowest retained value instead of recomputing removed
    indices.
- Recursive indicators no longer extend a dedicated `RecursiveCachedIndicator`. They should subclass `CachedIndicator`
  directly and rely on the sequential pre-computation performed in `CachedIndicator#computeAndCacheValue`.
- Keep the "last bar is not cached" behavior intact. Indicators are expected to recompute the most recent bar after intra-bar
  updates.
# Indicator conventions

- Indicators must avoid look-ahead bias: when evaluating index `i`, only data from `getBeginIndex()` through `i` may influence the result.
- Guard against `Num.isNaN()` for both the current value and all neighbours used in the calculation. Prefer returning `NaN` or skipping the candidate rather than propagating bad data silently.
- Add `@since` tags to every new public type or method introduced in this package.
- Prefer exposing helper methods (for example, returning the source index of a detected event) when they simplify testing and downstream reuse.
- Keep plateau/flat-top handling symmetric: expand equal-value runs with helper methods so highs and lows can share the same reasoning (helps Elliott Wave style logic later on).
