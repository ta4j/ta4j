# Indicators package guidance

- Follow indicator constructor conventions:
  - Provide a `BarSeries` convenience constructor and overloads that accept the underlying price indicator.
  - Default to the canonical time frames (e.g., 12/26/9 for MACD-style oscillators) when meaningful.
- Prefer composing with existing helper indicators (e.g., `BinaryOperation`, `VolumeIndicator`) rather than reimplementing arithmetic.
- Always guard against zero-volume or NaN inputs and propagate `NaN` using `NaN.NaN` for undefined results.
- Expose companion helpers such as signal lines or histograms when an indicator has a standard derived series.
- Annotate every new public type or method with the current `@since` version (omit the -SNAPSHOT portion if applicable).


# Indicator conventions

- Indicators must avoid look-ahead bias: when evaluating index `i`, only data from `getBeginIndex()` through `i` may influence the result.
- Guard against `Num.isNaN()` for both the current value and all neighbours used in the calculation. Prefer returning `NaN` or skipping the candidate rather than propagating bad data silently.
- Add `@since` tags to every new public type or method introduced in this package.
- Prefer exposing helper methods (for example, returning the source index of a detected event) when they simplify testing and downstream reuse.
- Keep plateau/flat-top handling symmetric: expand equal-value runs with helper methods so highs and lows can share the same reasoning (helps Elliott Wave style logic later on).
