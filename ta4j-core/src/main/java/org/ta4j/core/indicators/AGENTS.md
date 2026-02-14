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
- Indicator's calculate should return `NaN` for bar indexes within the unstable period. Unstable periods are typically either the sum or max of all component indicators' unstable periods, determined based off context.

## Calculating unstable periods when chaining indicators

**Sequential chaining (A -> B -> C):** Typically **additive**, but check if each indicator accounts for its input's unstable period:
- `EMAIndicator` does **NOT** account for input's unstable period (returns only `barCount`), but propagates NaN in `calculate()`. Chain EMAs by summing: `period1 + period2 + ...`
- `SMAIndicator` **DOES** account for input: `indicator.getCountOfUnstableBars() + barCount - 1`
- Earlier periods may be covered by later ones (e.g., `DifferenceIndicator`'s 1 bar covered by first EMA if `firstPeriod >= 1`)

**Parallel indicators:** Take **maximum**: `Math.max(indicatorA.getCountOfUnstableBars(), indicatorB.getCountOfUnstableBars())`

**Examples:** `SchaffTrendCycleIndicator` sums all periods; `TrueStrengthIndexIndicator` sums EMA periods; `BinaryOperationIndicator` takes max. Always verify with tests.
- Guard against `Num.isNaN()` for both the current value and all neighbours used in the calculation. Prefer returning `NaN` or skipping the candidate rather than propagating bad data silently.
- `DoubleNumFactory` can surface `Double.NaN` values that do not satisfy `Num.isNaN()`. Also check `Double.isNaN(value.doubleValue())` when validating inputs.
- Keep plateau/flat-top handling symmetric: expand equal-value runs with helper methods so highs and lows can share the same reasoning (helps Elliott Wave style logic later on).
- When chaining multiple smoothing stages (EMA on derived data such as price changes), prefer small `RecursiveCachedIndicator` wrappers that reset gracefully after encountering `NaN` inputs; this avoids contaminating later bars with an early invalid value and keeps look-ahead guarantees intact.
- `AbstractEMAIndicator` already enforces the NaN rules: it returns `NaN` during the unstable window and resets to the current value when a prior value is `NaN`. Extend it instead of re-implementing EMA math, and never bypass its `calculate` logic with custom smoothing.
- Prefer exposing helper methods (e.g., returning the source index of a detected event) when they simplify testing and downstream reuse.

## Unstable bar contract
- `getCountOfUnstableBars()` must return the first index at which indicator output is considered stable and safe to consume.
- For `Indicator<Num>`, guard `calculate` with `if (index < getCountOfUnstableBars()) return NaN;` unless the indicator intentionally delegates that guard to a composed indicator.
- For non-`Num` indicators (for example boolean signal indicators), do not read pre-warmup history (`index - 1`, backtracking loops) before the unstable boundary; return a deterministic fallback (`false`/`NONE`) during warm-up.
- Never hardcode unstable counts when they depend on component indicators; derive from the current component graph so constructor and deserialized instances behave identically. For pattern indicators that combine a fixed candle lookback with trend/confirmation indicators, use the stricter boundary: `Math.max(patternLookback, trendIndicator.getCountOfUnstableBars())`.
- Include lookback offsets explicitly: if logic requires previous bars (`index - n`) beyond component unstable bars, add that offset (`+ n`) to the unstable count.
- Keep unstable count formulas aligned with dataflow shape:
  - Sequential chain where each stage adds new warm-up: sum stage contributions.
  - Parallel branch merge: max branch unstable counts.
  - Mixed pipelines: compute branch-local unstable counts first, then combine with `max` at merge points.
- Avoid double counting: if a component already includes upstream unstable bars in its own `getCountOfUnstableBars()`, do not add upstream values again.
