# Candlestick indicator conventions

- Each candlestick pattern indicator extends `CachedIndicator<Boolean>` and returns `false` for indices that cannot be evaluated yet.
- Reuse the existing helper indicators in this package (e.g., `RealBodyIndicator`, `UpperShadowIndicator`, `LowerShadowIndicator`) instead of duplicating calculations.
- Provide a default constructor with sensible default parameters and an additional constructor that exposes all tunable thresholds.
- Document pattern-specific defaults directly in the class-level Javadoc, referencing at least one external resource that explains the pattern.
- Keep private fields `final` whenever practical and prefer `final var` for local variables when the type is obvious.
