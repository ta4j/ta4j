# AGENTS instructions for `org.ta4j.core.rules.named`

## Constructors

- Provide a constructor accepting `(BarSeries series, String... params)` alongside strongly typed constructors.
- Parse parameters in the varargs constructor, then delegate to the main constructor to avoid duplicated rule-building logic.
- Validate inputs eagerly and throw informative `IllegalArgumentException`s for malformed parameters.

## Registry and label contract

- Register each named rule with `NamedRule.registerImplementation(YourClass.class)` so label lookups resolve simple names.
- Projects may call `NamedRule.initializeRegistry()` (defaults) or `NamedRule.initializeRegistry("com.mycompany.rules")` once at startup for package scanning.
- Rule labels must serialize as `<SimpleName>_<param...>`.
- Use `NamedRule.buildLabel(...)` to keep label formatting consistent.
- Ensure the label encodes all reconstruction-critical data.
