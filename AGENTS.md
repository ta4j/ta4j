# AI Agent Instructions for ta4j

## Repository-wide Conventions

### Build and Validation
- **MANDATORY**: All changes must be validated using: `mvn -B clean license:format formatter:format test install`
- Run the narrowest Maven test command that covers new code (typically `mvn -pl ta4j-core test -Dtest=...`) to keep the feedback loop fast
- Use `git diff -- path/to/file` to keep diffs scoped when full-file output is large
- When git status times out, narrow the scope with `git status --short path` instead of retrying broad commands

### Code Quality Standards
- Prefer descriptive Javadoc with references to authoritative sources (e.g., Investopedia) when adding new indicators or public APIs
- Follow existing formatting conventions: four-space indentation, one statement per line, favor immutability patterns
- When adding tests, place them in the mirrored package inside `src/test/java` and use existing test utilities/helpers when available
- Update or add `AGENTS.md` files in subdirectories when you discover local conventions worth making explicit for future agents

### Documentation and Release Management
- The project tracks release notes in `CHANGELOG.md`; if instructions mention `CHANGES.md`, update the `CHANGELOG.md` unreleased section instead
- A temporary changelog placeholder (`#0000`) is used when an originating ticket number is unknown. Replace it with the real reference once available

## Unit Test Development Best Practices

### Preferred Change Workflow
- **Windows OS**: Use `apply_patch` for small Java test edits; it preserves CRLF line endings and avoids full-file rewrites
- Touch only the lines around the assertions being converted; keep the rest of the file untouched to prevent outsized diffs
- When adding new imports, insert them with `apply_patch` in the existing import block. Do not replace the whole block

### Exception Handling in Tests
- Replace try/catch plus fail blocks with `assertThrows`, for example `assertThrows(IllegalArgumentException.class, () -> ...)`
- Check for an existing `assertThrows` import before adding a new one to avoid duplicates
- Keep the assertion messages implicit; JUnit 5 expresses the expectation via `assertThrows` semantics

### Command Usage Tips (Windows/PowerShell)
- Stick with `bash -lc` commands unless PowerShell is strictly required; mixing shells can introduce quoting issues
- Before reaching for helper scripts, confirm the change can be handled with `apply_patch` or a focused Perl one-liner
- For Windows paths, rely on the workspace-relative form (`ta4j-core/...`) in commands to avoid drive-letter confusion

## Specific Component Guidelines

### NetMomentumIndicator Enhancements
- The indicator now supports a configurable decay factor. A decay of `1` preserves the legacy running-total behavior, while values below `1` apply an exponential fade to older contributions
- Tests rely on this recursive formulation, so prefer reasoning in terms of weighted sums rather than reintroducing `RunningTotalIndicator`
- For deterministic expectations in tests, constant oscillators are a reliable way to assert the closed-form steady-state values: `delta * (1 - decay^window) / (1 - decay)`
- RSI convenience accessors are exposed as static factories (`forRsi`, `forRsiWithDecay`). Use those in tests and documentation snippets to avoid constructor ambiguity with the general-purpose overloads

## Workflow Summary
Following these best practices should keep future development tight and predictable, ensuring code quality, consistency, and proper integration with the existing codebase.