# AGENTS Instructions for ta4j

## Repository-wide conventions
- All changes should be validated, formatted, and tested using Maven goal: `mvn -B clean license:format formatter:format test install`
- Prefer descriptive Javadoc with references to authoritative sources (e.g., Investopedia) when adding new indicators or public APIs.
- Follow the formatting conventions already present in the repository (use four-space indentation, one statement per line, and favour immutability patterns wherever possible).
- When adding tests, place them in the mirrored package inside `src/test/java` and use existing test utilities/helpers when available.
- Run the narrowest Maven test command that covers new code (typically `mvn -pl ta4j-core test -Dtest=...`) to keep the feedback loop fast.
- Update or add `AGENTS.md` files in subdirectories when you discover local conventions that are worth making explicit for future agents.
- The project tracks release notes in `CHANGELOG.md`; if instructions mention `CHANGES.md`, update the `CHANGELOG.md` unreleased section instead.
