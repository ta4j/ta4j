# AGENTS Instructions for `org.ta4j.core.strategy.named`

## Constructors
- Provide a constructor accepting `(BarSeries series, String... params)` alongside any strongly-typed constructor.
- Parse the parameters inside the varargs constructor and delegate to the main constructor to avoid duplicating rule-building logic.
- Register every named strategy via `NamedStrategy.registerImplementation(YourClass.class)` (typically from a static initializer) so the serializer can resolve the simple class name that appears in JSON payloads.
- Projects can opt-out of manual registration by calling `NamedStrategy.initializeRegistry()` (for Ta4j defaults) or `NamedStrategy.initializeRegistry("com.mycompany.strategies")` to scan additional packages once at startup.

## Serialization format
- Strategy names (and JSON labels) must serialize to `<SimpleName>_<param...>` (e.g., `MyStrategy_10_0.5`). The JSON `"type"` is always `NamedStrategy`.
- Use `NamedStrategy.buildLabel(...)` when constructing the superclass to guarantee consistent token formatting.
- The label must encode every piece of information required to reconstruct the instance (including unstable bar counts).

## Permutation helpers
- Prefer `NamedStrategy.buildAllStrategyPermutations(...)` when emitting preset combinations. Supply a `BiConsumer` failure handler if you need to log or skip invalid parameter sets.

## Documentation and validation
- Add `@since` tags to new public classes and constructors (use current version, omit -SNAPSHOT if applicable).
- Validate inputs eagerly; surface informative `IllegalArgumentException`s for bad parameters before strategy execution begins.

## Cross-cutting repository guidance
- Follow the root [`AGENTS.md`](../../../../../../../AGENTS.md) for PR-gated changelog expectations (update changelog only when the user indicates PR readiness or asks for PR creation) and the Maven-driven formatting pipeline described there.
