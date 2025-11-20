# AGENTS Instructions for tests in `org.ta4j.core.strategy.named`

## Coverage focus
- Mirror the constructor contract: include tests for both the `(BarSeries, String...)` convenience constructor and the main, fully-typed constructor.
- Validate that serialization helpers produce `<SimpleName>_<param...>` names and reject malformed parameter lists.

## Documentation alignment
- Ensure new strategy fixtures include `@since` annotations in the corresponding production code and assert validation rules where practical.

## Cross-cutting repository guidance
- Remember the root [`AGENTS.md`](../../../../../../../../AGENTS.md) requirements for updating the changelog and using the prescribed formatting commands when changes affect these strategies.
