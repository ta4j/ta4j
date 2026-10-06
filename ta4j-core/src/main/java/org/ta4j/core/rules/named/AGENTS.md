# AGENTS instructions for `org.ta4j.core.rules.named`

## Constructors

- Provide a constructor accepting `(BarSeries series, String... params)` alongside strongly typed constructors.
- Parse parameters in the varargs constructor, then delegate to the main constructor to avoid duplicated rule-building logic.
- Validate inputs eagerly and throw informative `IllegalArgumentException`s for malformed parameters.
- Validate in the argument expressions passed to `super(YourClass.class, params...)` (static helpers), not after `super(...)`: a constructor that throws after the superclass constructor leaves a partially initialized object (SpotBugs `CT_CONSTRUCTOR_THROW`).

## Registry and label contract

- Register each named rule with `NamedRule.registerImplementation(YourClass.class)` so label lookups resolve simple names.
- Projects may call `NamedRule.initializeRegistry()` (defaults) or `NamedRule.initializeRegistry("com.mycompany.rules")` once at startup for package scanning.
- Rule labels must serialize as `<SimpleName>_<param...>`.
- Pass the concrete class and label parameters to `super(YourClass.class, params...)`; `NamedRule` builds the label. Use `NamedRule.buildLabel(...)` only when a label is needed without an instance.
- Ensure the label encodes all reconstruction-critical data.
