# Indicators package guidance

## Constructor conventions
- Provide a `BarSeries` convenience constructor and overloads that accept the underlying price indicator.
- Default to canonical time frames (e.g., 12/26/9 for MACD-style oscillators) when meaningful.

## Implementation guidelines
- Prefer composing with existing helper indicators (e.g., `BinaryOperation`, `VolumeIndicator`) rather than reimplementing arithmetic.
- Always guard against zero-volume or NaN inputs and propagate `NaN` using `NaN.NaN` for undefined results.
- Indicators must avoid look-ahead bias: when evaluating index `i`, only data from `getBeginIndex()` through `i` may influence the result.
- Guard against `Num.isNaN()` for both the current value and all neighbours used in the calculation. Prefer returning `NaN` or skipping the candidate rather than propagating bad data silently.
- `DoubleNumFactory` can surface `Double.NaN` values that do not satisfy `Num.isNaN()`. Also check `Double.isNaN(value.doubleValue())` when validating inputs.
- Keep plateau/flat-top handling symmetric: expand equal-value runs with helper methods so highs and lows can share the same reasoning (helps Elliott Wave style logic later on).
- When chaining multiple smoothing stages (EMA on derived data such as price changes), prefer small `RecursiveCachedIndicator`
  wrappers that reset gracefully after encountering `NaN` inputs; this avoids contaminating later bars with an early invalid
  value and keeps look-ahead guarantees intact.
- Prefer exposing helper methods (e.g., returning the source index of a detected event) when they simplify testing and downstream reuse.
