# Indicators package guidance

## Constructor conventions
- Provide a `BarSeries` convenience constructor and overloads that accept the underlying price indicator.
- Default to canonical time frames (e.g., 12/26/9 for MACD-style oscillators) when meaningful.

## Implementation guidelines
- **Mandatory:** reuse existing indicators/helpers wherever possible. Before adding new math/statistics logic, search the codebase (especially `helpers`, `numeric`, and `statistics`) and compose existing indicators instead of duplicating logic.
- Prefer extracting generic building blocks (e.g., `BandIndicator`, `ZScoreIndicator`) and then composing them into context-specific indicators (e.g., VWAP-specific wrappers) for discoverability and stable serialization.
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
