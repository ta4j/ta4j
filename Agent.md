# Agent Notes: Unit Testing updates

These reminders capture the potential friction points when working on unit tests

## Preferred Change Workflow
- Under Windows OS: Use apply_patch for small Java test edits; it preserves CRLF line endings and avoids full-file rewrites.
- Touch only the lines around the assertions being converted; keep the rest of the file untouched to prevent outsized diffs.
- When adding new imports, insert them with apply_patch in the existing import block. Do not replace the whole block.

## Handling Exception Assertions
- Replace try/catch plus fail blocks with assertThrows, for example assertThrows(IllegalArgumentException.class, () -> ...).
- Check for an existing assertThrows import before adding a new one to avoid duplicates.
- Keep the assertion messages implicit; JUnit 5 expresses the expectation via assertThrows semantics.

## Command Usage Tips (Windows/PowerShell)
- Stick with bash -lc commands unless PowerShell is strictly required; mixing shells can introduce quoting issues.
- Before reaching for helper scripts, confirm the change can be handled with apply_patch or a focused Perl one liner.
- For Windows paths, rely on the workspace-relative form (ta4j-core/...) in commands to avoid drive-letter confusion.

## Diff And Status Hygiene
- Use git diff -- path/to/file to keep diffs scoped when full-file output is large.
- When git status times out, narrow the scope with git status --short path instead of retrying broad commands.

Following these best practices should keep future unit test development tight and predictable.
