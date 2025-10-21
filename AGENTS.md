# AGENTS Instructions for ta4j

## Repository-wide conventions
- All changes should be validated, formatted, and tested using Maven goal: `mvn -B clean license:format formatter:format test install`
- Run the narrowest Maven test command that covers new code (typically `mvn -pl ta4j-core test -Dtest=...`) to keep the feedback loop fast.
- Update or add `AGENTS.md` files in subdirectories when you discover local conventions that are worth making explicit for future agents.
- The project tracks release notes in `CHANGELOG.md`; if instructions mention `CHANGES.md`, update the `CHANGELOG.md` unreleased section instead.

## Code Organization
- Prefer descriptive Javadoc with references to authoritative sources (e.g., Investopedia) when adding new indicators or public APIs.
- Follow the formatting conventions already present in the repository (use four-space indentation, one statement per line, and favour immutability patterns wherever possible).
- When adding tests, place them in the mirrored package inside `src/test/java` and use existing test utilities/helpers when available.
- Group imports, fields, and methods by logical purpose. Within each group, order lines by decreasing length (“reverse Christmas tree”: longer lines above shorter ones).

## Domain Model and DTO class Design
Favor immutability and simplicity: record > public final fields > private fields + getters/setters.
For toString(), output JSON — prefer Gson serialization > manual JSON > custom/ad hoc formatting.