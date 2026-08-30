# AGENTS instructions for `org.ta4j.core.indicators`

Applies to this package unless a deeper `AGENTS.md` overrides it.

## Constructors and composition

- First-class public indicators in this package tree must implement `Indicator`
  and be named `*Indicator`. Do not introduce a public top-level `*Detector`,
  `*Config`, `*Result`, `*Filter`, `*Runner`, `*Facade`, or generic helper in
  `org.ta4j.core.indicators...` unless it is a nested support type owned by a
  specific indicator or an explicitly documented compatibility exception.
- Non-indicator strategy, analysis, detector, configuration, result, runner,
  facade, and model APIs belong outside the indicator namespace, typically under
  `org.ta4j.core.analysis...`, `org.ta4j.core.strategy...`,
  `org.ta4j.core.backtest...`, or another domain package that matches their
  primary abstraction.
- Indicator factories may use plural/factory names such as
  `RecentSwingIndicators`, but their Javadocs must state that they are factories,
  not `Indicator` implementations, and they must return first-class
  `*Indicator` types or clearly named value objects.
- Before adding or moving public types in this package tree, audit the file name,
  package, implemented interfaces, Javadocs, wiki inventory, examples, and
  imports together so configuration/helper APIs are not presented as indicators.

- Provide a `BarSeries` convenience constructor and overloads that accept the underlying source indicator when applicable.
- Prefer existing helper indicators (`BinaryOperationIndicator`, `VolumeIndicator`, numeric/statistics helpers) over reimplementing arithmetic.
- Extract reusable building blocks before adding context-specific wrappers.
- Prefer immutability; if stateful properties are required, make sure they are thread-safe.
- Keep logical source indicators serializable; mark derived/cached helper fields (`EMA` stacks, internal arithmetic helpers) as `transient` when they are reconstruction artifacts.
- Thread-safety and `transient` are orthogonal: only reconstruction-only runtime helpers should be `transient`.
- When composing indicators, avoid keeping fields that are only constructor-local intermediates and are not reused outside construction.
- Avoid look-ahead bias: result at index `i` may only depend on data from `getBeginIndex()` through `i`.

## Numerical safety

- Guard against zero-volume and undefined inputs; return `NaN.NaN` for undefined results.
- `Indicator<Num>` calculations should preserve `Num` through rolling windows, comparisons, accumulation, and return conversion. Limit `doubleValue()` to explicit `DoubleNum` validation, external interop, or unavoidable primitive-only math.
- Validate both `Num.isNaN()` and raw `Double`/`Float` delegate NaN or infinity where relevant because primitive-backed factories can surface non-finite values.
- Finite-value checks must not reject finite `DecimalNum` values just because their primitive `double` representation overflows.
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
- The head-advance cache floor in `CachedIndicator` applies by default to
  every indicator; only indicators with genuinely unbounded historical
  dependencies (opting in via `hasRecursiveDependencies()`) keep their
  pre-advance values on bounded series, so do not rely on reseeding
  semantics for them. Fixed-window recursive indicators such as
  `VolumeIndicator` and `PearsonCorrelationIndicator` keep the default
  floor and are recomputed like any windowed indicator.
- Conditionally recursive indicators (recursion only along special
  stretches, e.g. flat windows or equal-basis Klinger trend stretches)
  override `minimumCacheableIndexAfterHeadAdvance(int)` to return
  `Integer.MAX_VALUE`: every value is recomputable from the retained
  window, so the whole cache is discarded on head advance instead of
  preserving stale pre-advance results. `StochasticIndicator` and the
  Klinger trend-direction/cumulative-measurement components follow this
  policy.

## NetMomentumIndicator specifics

- Preserve battery semantics: below-pivot oscillator pressure contributes positive rebound energy,
  above-pivot pressure contributes negative depletion, and distance from the pivot is convex-weighted.
- Preserve decay semantics: `decay = 1` keeps the undecayed windowed battery sum; values below `1`
  apply exponential fade.
- Keep the recursive weighted-sum formulation intact (do not reintroduce `RunningTotalIndicator`).
- For deterministic expectations, use the steady-state reference formula
  `contribution * (1 - decay^window) / (1 - decay)`.
- Prefer `NetMomentumIndicator.forRsi(...)` and `NetMomentumIndicator.forRsiWithDecay(...)` in tests/examples to avoid constructor ambiguity.
