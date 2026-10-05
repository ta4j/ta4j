# Candlestick indicator conventions

- Each candlestick pattern indicator extends `CachedIndicator<Boolean>` and returns `false` for indices that cannot be evaluated yet.
- Reuse the existing helper indicators in this package (e.g., `CandleBodyIndicator`, `CandleRangeIndicator`, `UpperShadowIndicator`, `LowerShadowIndicator`) instead of duplicating calculations. `CandleBodyIndicator` provides the body magnitude (`|close - open|`); the legacy signed `RealBodyIndicator` is deprecated — prefer `Bar#isBullish()`/`Bar#isBearish()` for direction.
- Provide a default constructor with sensible default parameters and an additional constructor that exposes all tunable thresholds.
- Document pattern-specific defaults directly in the class-level Javadoc, referencing at least one external resource that explains the pattern.
- Keep private fields `final` whenever practical and prefer `final var` for local variables when the type is obvious.
