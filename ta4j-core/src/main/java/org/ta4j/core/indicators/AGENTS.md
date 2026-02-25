# AGENTS instructions for `org.ta4j.core.indicators`

Applies to this package unless a deeper `AGENTS.md` overrides it.

## Constructors and composition

- Provide a `BarSeries` convenience constructor and overloads that accept the underlying source indicator when applicable.
- Prefer existing helper indicators (`BinaryOperationIndicator`, `VolumeIndicator`, numeric/statistics helpers) over reimplementing arithmetic.
- Extract reusable building blocks before adding context-specific wrappers.
- Prefer immutability; if stateful properties are required, make sure they are thread-safe and declared as `transient`.
- Keep logical source indicators serializable; mark derived/cached helper fields (`EMA` stacks, internal arithmetic helpers) as `transient` when they are reconstruction artifacts.
- When composing indicators, avoid keeping fields that are only constructor-local intermediates and are not reused outside construction.
- Avoid look-ahead bias: result at index `i` may only depend on data from `getBeginIndex()` through `i`.

## Numerical safety

- Guard against zero-volume and undefined inputs; return `NaN.NaN` for undefined results.
- Validate both `Num.isNaN()` and `Double.isNaN(value.doubleValue())` where relevant because `DoubleNumFactory` can surface raw `Double.NaN`.
- Guard both current values and neighbors used in calculation; prefer returning `NaN` over propagating invalid data silently.
- Keep flat/plateau handling symmetric for highs and lows when scanning neighboring bars.
- For EMA-like smoothing, prefer extending `AbstractEMAIndicator` to preserve NaN reset behavior and unstable-period handling.
- Prefer exposing helper methods (for example returning the source index of a detected event) when they improve testing and downstream reuse.

## Unstable bar contract

- `getCountOfUnstableBars()` must represent the first index with stable output.
- `Indicator<Num>` implementations should guard warm-up with `if (index < getCountOfUnstableBars()) return NaN;` unless delegated safely to composed indicators.
- Non-`Num` indicators should return deterministic warm-up values (`false`, `NONE`, etc.) without reading pre-warmup history.
- Derive unstable periods from the component graph:
  - Sequential pipelines are typically additive when each stage adds warm-up.
  - Parallel merges should use `max` across branches.
  - Include explicit lookback offsets required by local logic.
  - Avoid double-counting upstream unstable contributions already included by components.
- Indicator-specific caveats still apply when composing:
  - `EMAIndicator` does not include input unstable bars in its own count, so EMA-on-EMA chains are typically additive.
  - `SMAIndicator` includes upstream unstable bars in its reported count (`input + barCount - 1`), so do not add upstream again.
- Pattern indicators that combine fixed candle lookback with trend/confirmation indicators should use the stricter boundary, for example `Math.max(patternLookback, trendIndicator.getCountOfUnstableBars())`.
- Keep test assertions aligned with the contract: assert unstable count directly and verify warm-up boundaries (`unstable - 1` versus `unstable`).

## NetMomentumIndicator specifics

- Preserve decay semantics: `decay = 1` keeps legacy running-total behavior; values below `1` apply exponential fade.
- Keep the recursive weighted-sum formulation intact (do not reintroduce `RunningTotalIndicator`).
- For deterministic expectations, use the steady-state reference formula `delta * (1 - decay^window) / (1 - decay)`.
- Prefer `NetMomentumIndicator.forRsi(...)` and `NetMomentumIndicator.forRsiWithDecay(...)` in tests/examples to avoid constructor ambiguity.
