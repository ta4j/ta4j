# AGENTS Instructions for ta4j

## Repository-wide conventions
- **MANDATORY** All changes should be validated, formatted, and tested using Maven goal: `mvn -B clean license:format formatter:format test install`
- Run the narrowest Maven test command that covers new code (typically `mvn -pl ta4j-core test -Dtest=...`) to keep the feedback loop fast.
- Use `git diff -- path/to/file` to keep diffs scoped when full-file output is large
- When git status times out, narrow the scope with `git status --short path` instead of retrying broad commands
- Update or add `AGENTS.md` files in subdirectories when you discover local conventions that are worth making explicit for future agents.
- The project tracks release notes in `CHANGELOG.md`; if instructions mention `CHANGES.md`, update the `CHANGELOG.md` within the appropriate section.

## Code Organization
- Prefer descriptive Javadoc with references to authoritative sources (e.g., Investopedia) when adding new indicators or public APIs.
- Follow the formatting conventions already present in the repository (use four-space indentation, one statement per line, and favour immutability patterns wherever possible).
- When adding tests, place them in the mirrored package inside `src/test/java` and use existing test utilities/helpers when available.
- Group imports, fields, and methods by logical purpose. Within each group, order lines by decreasing length (“reverse Christmas tree”: longer lines above shorter ones).

## Domain Model and DTO class Design
- Favor immutability and simplicity: record > public final fields > private fields + getters/setters.
- For toString(), output JSON — prefer Gson serialization > manual JSON > custom/ad hoc formatting.

## Preferred Change Workflow
- **Windows OS**: Use `apply_patch` for small Java test edits; it preserves CRLF line endings and avoids full-file rewrites
- **Windows OS**: For Windows paths, rely on the workspace-relative form (`ta4j-core/...`) in commands to avoid drive-letter confusion
- Touch only the lines around the assertions being converted; keep the rest of the file untouched to prevent outsized diffs
- When adding new imports, insert them with `apply_patch` in the existing import block. Do not replace the whole block

## Exception Handling in Tests
- Replace try/catch plus fail blocks with `assertThrows`, for example `assertThrows(IllegalArgumentException.class, () -> ...)`
- Check for an existing `assertThrows` import before adding a new one to avoid duplicates
- Keep the assertion messages implicit; JUnit 5 expresses the expectation via `assertThrows` semantics
