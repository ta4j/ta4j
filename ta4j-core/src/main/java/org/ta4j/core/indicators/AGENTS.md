# Indicators package guidance

## Constructor conventions
- Provide a `BarSeries` convenience constructor and overloads that accept the underlying price indicator.
- Default to canonical time frames (e.g., 12/26/9 for MACD-style oscillators) when meaningful.

## Implementation guidelines
- Prefer composing with existing helper indicators (e.g., `BinaryOperationIndicator`, `VolumeIndicator`) rather than reimplementing arithmetic.
- Prefer immutability but if stateful properties are required, make sure they are thread-safe and declared as transient
- When composing indicators avoid declaring any as global that are not referenced outside the constructor. 
- When serialization matters, keep a reference to the logical source indicator(s) that feed your composition and mark derived caches (EMA stacks, arithmetic helpers, etc.) as `transient`. This preserves accurate descriptor trees while still allowing internal reuse.
- Always guard against zero-volume or NaN inputs and propagate `NaN` using `NaN.NaN` for undefined results.
- Indicators must avoid look-ahead bias: when evaluating index `i`, only data from `getBeginIndex()` through `i` may influence the result.

## Unstable bar guidance
- `getCountOfUnstableBars()` must return the first index at which indicator output is considered stable and safe to consume.
- For `Indicator<Num>`, guard `calculate` with `if (index < getCountOfUnstableBars()) return NaN;` unless the indicator intentionally delegates that guard to a composed indicator.
- For non-`Num` indicators (for example boolean signal indicators), do not read pre-warmup history (`index - 1`, backtracking loops) before the unstable boundary; return a deterministic fallback (`false`/`NONE`) during warm-up.
- Derive unstable counts from the active component graph so constructor and deserialized instances behave identically; avoid hardcoded constants when composition determines warm-up.
- Include explicit lookback offsets: if logic reads `index - n`, add `+ n` beyond component unstable counts.
- Combine unstable counts based on dataflow:
  - Sequential chains (A -> B -> C): additive for stages that add new warm-up.
  - Parallel merges: `max` across branches.
  - Mixed pipelines: compute each branch first, then `max` at merge points.
- Indicator-specific caveats still apply when composing:
  - `EMAIndicator` does not include input unstable bars in its own count, so EMA-on-EMA chains are typically additive.
  - `SMAIndicator` includes upstream unstable bars in its reported count (`input + barCount - 1`), so do not add upstream again.
- Pattern indicators that combine fixed candle lookback with trend/confirmation indicators should use the stricter boundary, for example `Math.max(patternLookback, trendIndicator.getCountOfUnstableBars())`.
- Avoid double counting: if a component already includes upstream unstable bars in its own `getCountOfUnstableBars()`, do not add upstream values again.
- Keep test assertions aligned with the contract: assert unstable count directly and verify warm-up boundaries (`unstable - 1` vs `unstable`).

## Robustness and NaN handling
- Guard against `Num.isNaN()` for both the current value and all neighbours used in the calculation. Prefer returning `NaN` or skipping the candidate rather than propagating bad data silently.
- `DoubleNumFactory` can surface `Double.NaN` values that do not satisfy `Num.isNaN()`. Also check `Double.isNaN(value.doubleValue())` when validating inputs.
- Keep plateau/flat-top handling symmetric: expand equal-value runs with helper methods so highs and lows can share the same reasoning (helps Elliott Wave style logic later on).
- When chaining multiple smoothing stages (EMA on derived data such as price changes), prefer small `RecursiveCachedIndicator` wrappers that reset gracefully after encountering `NaN` inputs; this avoids contaminating later bars with an early invalid value and keeps look-ahead guarantees intact.
- `AbstractEMAIndicator` already enforces the NaN rules: it returns `NaN` during the unstable window and resets to the current value when a prior value is `NaN`. Extend it instead of re-implementing EMA math, and never bypass its `calculate` logic with custom smoothing.
- Prefer exposing helper methods (e.g., returning the source index of a detected event) when they simplify testing and downstream reuse.
