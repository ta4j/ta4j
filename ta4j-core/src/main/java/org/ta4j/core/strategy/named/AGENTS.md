# AGENTS Instructions for `org.ta4j.core.strategy.named`

## Constructors
- Provide a constructor accepting `(BarSeries series, String... params)` alongside any main, fully-typed constructor.
- The varargs constructor should delegate to the primary constructor after validating and parsing the `params` values.

## Serialization format
- Strategy names must serialize to `<SimpleName>_<param...>` (e.g., `MyStrategy_10_0.5`).
- Ensure the serialization helper mirrors the argument order of the primary constructor and omits redundant whitespace.

## Documentation and validation
- Add `@since` tags to new public classes and constructors.
- Validate inputs eagerly; surface informative `IllegalArgumentException`s for bad parameters before strategy execution begins.

## Cross-cutting repository guidance
- Follow the root `AGENTS.md` for changelog expectations and the Maven-driven formatting pipeline described there.
