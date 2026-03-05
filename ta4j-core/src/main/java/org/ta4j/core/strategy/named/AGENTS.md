# AGENTS instructions for `org.ta4j.core.strategy.named`

## Constructors

- Provide a constructor accepting `(BarSeries series, String... params)` alongside strongly typed constructors.
- Parse parameters in the varargs constructor, then delegate to the main constructor to avoid duplicated rule-building logic.
- Validate inputs eagerly and throw informative `IllegalArgumentException`s for malformed parameters.

## Registry and serialization contract

- Register each named strategy with `NamedStrategy.registerImplementation(YourClass.class)` so serializer lookups resolve simple names.
- Projects may call `NamedStrategy.initializeRegistry()` (defaults) or `NamedStrategy.initializeRegistry("com.mycompany.strategies")` once at startup for package scanning.
- Strategy labels/names must serialize as `<SimpleName>_<param...>`.
- JSON `type` remains `NamedStrategy`; use `NamedStrategy.buildLabel(...)` to keep label formatting consistent.
- Ensure the label encodes all reconstruction-critical data, including unstable-bar settings.

## Permutations and docs

- Prefer `NamedStrategy.buildAllStrategyPermutations(...)` when emitting parameter combinations.
- Add `@since <current-version-without-SNAPSHOT>` to new public classes/constructors in this package.
