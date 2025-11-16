# AGENTS Instructions for ta4j

## Repository-wide conventions
- All code changes must be validated, formatted, and tested by running `scripts/run-full-build-quiet.sh` (wraps `mvn -B clean license:format formatter:format test install`). The build must be green at the end of every development cycle (changes that affect build/test behavior).
- The script stores the full log under `.agents/logs/` and prints aggregated test totals. Always include its reported `Tests run / Failures / Errors / Skipped` numbers plus the log path when you describe your verification results.
- To keep the feedback loop fast run the narrowest Maven test command that covers new code (typically `mvn -pl ta4j-core test -Dtest=...`) from the repo root. Be sure to finish every change-set by re-running the quiet full-build script above.
- All new or changed code from feature work or bug fixes must be covered by comprehensive unit tests that both demonstrates the correctness of the solution as well as serves as a shield against future regressions.
- When debugging issues, take every opportunity to create focused unit tests that allow you to tighten the feedback loop. When possible design the tests to also serve as regression bulwarks for future changes.
- Update or add `AGENTS.md` files in subdirectories when you discover local conventions that are worth making explicit for future agents.
- When tweaking rule/indicator/strategy serialization tests, prefer canonical comparisons instead of brittle string equality. The helper `RuleSerializationRoundTripTestSupport` already normalizes `ComponentDescriptor`s (sorted children, normalized numeric strings) — reuse it rather than hand-rolled assertions so that constructor inference-induced ordering changes don’t break tests.


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

## Finding Scoped Agent Guides
- Many packages (e.g., `ta4j-core/src/main/java/org/ta4j/core/serialization`) have their own `AGENTS.md` with domain-specific conventions. Before editing a feature area, run `rg --files -g 'AGENTS.md'` or `fd AGENTS.md` from the repo root and open the closest file to your working directory.
- When adding new modules or significant subsystems, include an `AGENTS.md` in that directory and link back to it from higher-level docs when possible.
