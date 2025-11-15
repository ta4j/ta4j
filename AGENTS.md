# AGENTS Instructions for ta4j

## Repository-wide conventions
- All code changes must be validated, formatted, and tested using Maven goal: `mvn -B clean license:format formatter:format test install`. The build must be green at the end of every development cycle.
- Run the narrowest Maven test command that covers new code (typically `mvn -pl ta4j-core test -Dtest=...`) to keep the feedback loop fast.
- All new or changed code from feature work or bug fixes must be covered by comprehensive unit tests that both demonstrates the correctness of the solution as well as serves as a shield against future regressions.
- When debugging issues, take every opportunity to create focused unit tests that allow you to tighten the feedback loop. When possible design the tests to also serve as regression bulwarks for future changes.
- Update or add `AGENTS.md` files in subdirectories when you discover local conventions that are worth making explicit for future agents.


## Code Organization
- Prefer descriptive Javadoc with references to authoritative sources (e.g., Investopedia) when adding new indicators or public APIs.
- Follow the formatting conventions already present in the repository (use four-space indentation, one statement per line, and favour immutability patterns wherever possible).
- When adding tests, place them in the mirrored package inside `src/test/java` and use existing test utilities/helpers when available.
- Group imports, fields, and methods by logical purpose. Within each group, order lines by decreasing length (“reverse Christmas tree”: longer lines above shorter ones).

## Domain Model and DTO class Design
Favor immutability and simplicity: record > public final fields > private fields + getters/setters.
For toString(), output JSON — prefer Gson serialization > manual JSON > custom/ad hoc formatting.
- The project uses Gson for component (rule, indicator, strategy) metadata. Prefer the helper classes in
  `org.ta4j.core.serialization` (see `ComponentDescriptor` and `ComponentSerialization`) instead of hand-rolling JSON when you
  need structured names or serialization glue.
- Composite rule names should be represented as nested component descriptors. Use
  `ComponentSerialization.parse(rule.getName())` to walk existing rule names safely.

## Serialization Notes
- Rule/strategy serialization depends on `RuleSerialization` reconstructing indicators directly from their descriptors. Do **not** reintroduce positional indicator matching or mutable cursor state (e.g., `indicatorMatchIndex`): JSON round-trips strip labels, so every descriptor must be self-contained.
- `ReconstructionContext` should only track label lookups for cross-component references; use the parent-context chain when Strategy-level descriptors need to be visible to nested rules.
- Keep the focused regression tests consolidated in `ta4j-core/src/test/java/org/ta4j/core/serialization/AndRuleSerializationTest.java`. It already covers descriptor-only, JSON round-trip, labeled-component, and Strategy round-trip scenarios—add future composite-rule cases there instead of creating new test classes.
- Temporary/printf debug tests (e.g., `AndRuleSerializationDebugTest`) should not live in the suite. If you need one locally, delete it before committing.
