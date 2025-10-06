# AGENTS Instructions for ta4j

## Repository-wide conventions
- Keep the existing MIT license header at the top of any new Java files by copying it from sibling classes in the same package.
- Prefer descriptive Javadoc with references to authoritative sources (e.g., Investopedia) when adding new indicators or public APIs.
- Follow the formatting conventions already present in the repository (use four-space indentation, one statement per line, and favour `final`/`var` patterns seen in neighbouring files).
- When adding tests, place them in the mirrored package inside `src/test/java` and use existing test utilities/helpers when available.
- Run the narrowest Maven test command that covers new code (typically `mvn -pl ta4j-core test -Dtest=...`) to keep the feedback loop fast.
- Update or add `AGENTS.md` files in subdirectories when you discover local conventions that are worth making explicit for future agents.
