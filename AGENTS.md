# AGENTS Instructions for ta4j

## MANDATORY WORKFLOW - DO NOT SKIP

**CRITICAL: Before completing ANY code changes, you MUST:**

1. ✅ Run the full build script: `scripts/run-full-build-quiet.sh`
   - **This is NOT optional** for code changes outside `.github/workflows/`.
   - **Exception:** You may skip the full build ONLY when ALL changes are exclusively within `.github/workflows/`, `CHANGELOG.md`, or documentation-only files (for example: `*.md`, `docs/`).
   - If any other files are modified (code, docs, configs, etc.), the full build is required.
   - **When:** After every code change that affects build/test behavior (which is almost always)
   - **Permissions:** **ALWAYS run with `required_permissions: ['all']`** to avoid Maven repository permission issues. The build script requires full filesystem access to read/write Maven cache and repository files. If the environment forbids approval prompts (e.g., approval policy is `never`) but already grants full access, run the script directly without requesting permissions.
   - **Windows:** Invoke Git Bash or MSYS2 binary directly (never WSL or the CLI's default `/bin/bash`) for native performance (5x vs WSL). Always call it explicitly, e.g. `& "C:\Program Files\Git\bin\bash.exe" -c "cd /c/Users/David/Workspace/github/ta4j && ./scripts/run-full-build-quiet.sh"` (convert Windows path `C:\...` to `/c/...` format).
   - **What it does:** Runs `mvn -B clean license:format formatter:format test install`
   - **Required outcome:** Build must be GREEN (all tests pass, no failures/errors)
   - **Report:** Always include the script's output showing `Tests run / Failures / Errors / Skipped` numbers and the log path

2. ✅ Verify the build succeeded with zero failures and zero errors
3. ✅ Include the test results in your completion message

**If you skip the full build, you have NOT completed the task. The build script is the ONLY acceptable way to validate changes.**

## Repository-wide conventions

### Build and Test Workflow

- **During development:** Use narrow Maven test commands for fast feedback (e.g., `mvn -pl ta4j-core test -Dtest=...`). For focused module testing, you may use `scripts/run-full-build-quiet.sh -pl ta4j-core` to get filtered logs while still validating a single module.
- **Before completion:** ALWAYS run `scripts/run-full-build-quiet.sh` (without `-pl` flags), unless the only changes are within `.github/workflows/`, `CHANGELOG.md`, or documentation-only files (for example: `*.md`, `docs/`).
- The script stores the full log under `.agents/logs/` and prints aggregated test totals
- Use `git diff -- path/to/file` to keep diffs scoped when full-file output is large
- When `git status` times out, narrow the scope with `git status --short path` instead of retrying broad commands
- All new or changed code from feature work or bug fixes must be covered by comprehensive unit tests that demonstrate correctness and serve as a shield against future regressions.
- When debugging issues, take every opportunity to create focused unit tests that allow you to tighten the feedback loop. When possible design the tests to also serve as regression bulwarks for future changes.
- At the end of every development cycle (code materially impacted) capture the changes into the CHANGELOG.md under the appropriate section using existing style/format conventions. Avoid duplicates and consolidate Unreleased section items as needed.
- **🚫 CRITICAL: Do not ignore build errors even if you think they were pre-existing.** Suppressing/ignoring/assuming or otherwise skipping over the errors is forbidden. **All errors that surface must be investigated and root cause resolved.** Surface to the user any fixes that require complex refactoring or design changes. **Never skip over a failing test without explicit approval from user.**
- Update or add `AGENTS.md` files in subdirectories when you discover local conventions that are worth making explicit for future agents.
- When tweaking rule/indicator/strategy serialization tests, prefer canonical comparisons instead of brittle string equality. The helper `RuleSerializationRoundTripTestSupport` already normalizes `ComponentDescriptor`s (sorted children, normalized numeric strings) — reuse it rather than hand-rolled assertions so that constructor inference-induced ordering changes don't break tests.

### Unit Testing Best Practices

- **🚫 NEVER SKIP TESTS WITHOUT EXPLICIT USER APPROVAL.** This is a CRITICAL rule. If a test fails, you MUST:
  1. Investigate the root cause
  2. Fix the underlying issue, OR
  3. Surface the problem to the user and ask for explicit approval before skipping
  - **DO NOT** use `Assume.assumeNoException()`, `Assume.assumeTrue(false)`, `@Ignore`, or any other mechanism to skip failing tests
  - **DO NOT** wrap test code in try-catch blocks that silently skip on exceptions
  - **DO NOT** assume tests can be skipped because "serialization isn't supported" or similar reasons
  - If you encounter a failing test, treat it as a bug that must be fixed, not something to skip
  - **Note:** Environment-based assumptions (e.g., `Assume.assumeFalse("Headless environment", GraphicsEnvironment.isHeadless())` for GUI tests) are acceptable when tests genuinely cannot run in certain environments. This is different from skipping tests that fail due to code issues.
- **Never use reflection to access private APIs in tests.** Always test through the nearest public API, even if it requires additional setup. If testing private methods is necessary, refactor the production code to support dependency injection and mocks, or extract the logic into a testable public method. Reflection-based tests are brittle, harder to maintain, and don't reflect real usage patterns.
- **Use `assertThrows` for exception testing.** Always use `org.junit.jupiter.api.Assertions.assertThrows()` (JUnit 5) or `org.junit.Assert.assertThrows()` (JUnit 4) instead of `@Test(expected = ...)` annotations or try-catch blocks. The `assertThrows` API provides better error messages and allows you to verify exception properties.
- When adding `assertThrows`, check for an existing `assertThrows` import before adding a new one to avoid duplicates.
- Keep assertion messages implicit; JUnit expresses the expectation via `assertThrows` semantics.
- **Prefer dependency injection for testability.** When code is difficult to test, refactor to accept dependencies through constructors or methods rather than accessing them statically or creating them internally. This enables mocking and makes tests more focused and maintainable.
- **Windows OS**: Use `apply_patch` for small Java test edits; it preserves CRLF line endings and avoids full-file rewrites.
- Touch only the lines around the assertions being converted; keep the rest of the file untouched to prevent outsized diffs.
- When adding new imports, insert them with `apply_patch` in the existing import block. Do not replace the whole block.
- Stick with `bash -lc` commands unless PowerShell is strictly required; mixing shells can introduce quoting issues.
- For Windows paths, rely on the workspace-relative form (`ta4j-core/...`) in commands to avoid drive-letter confusion.

## Code Organization
- Prefer descriptive Javadoc with references to authoritative sources (e.g., Investopedia) when adding new indicators or public APIs.
- Follow the formatting conventions already present in the repository (use four-space indentation, one statement per line, and favour immutability patterns wherever possible).
- When adding tests, place them in the mirrored package inside `src/test/java` and use existing test utilities/helpers when available.
- **Always use loggers instead of System.out/System.err.** Use `org.apache.logging.log4j.LogManager` and `org.apache.logging.log4j.Logger`. Create a static logger: `private static final Logger LOG = LogManager.getLogger(ClassName.class);`. Use parameterized logging: `LOG.error("Message: {} - {}", param1, param2);` instead of string concatenation.
- **Avoid fully qualified namespaces in code.** Always use imports instead of fully qualified class names (e.g., use `Num` instead of `org.ta4j.core.num.Num`, `BarSeries` instead of `org.ta4j.core.BarSeries`). This improves readability and maintains consistency with the codebase style. Add the necessary import statements at the top of the file rather than using fully qualified names in variable initializations, method calls, or type casts.

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

## Specific Component Guidelines

### TimeBarBuilder Gap Semantics
- TimeBarBuilder should create bars aligned to the time periods it is given.
- It does not perform gap reconciliation or backfilling; missing periods remain
  missing, but gaps should appear at the correct positions in the series.

### NetMomentumIndicator Enhancements
- The indicator now supports a configurable decay factor. A decay of `1` preserves the legacy running-total behavior, while values below `1` apply an exponential fade to older contributions.
- Tests rely on this recursive formulation, so prefer reasoning in terms of weighted sums rather than reintroducing `RunningTotalIndicator`.
- For deterministic expectations in tests, constant oscillators are a reliable way to assert the closed-form steady-state values: `delta * (1 - decay^window) / (1 - decay)`.
- RSI convenience accessors are exposed as static factories (`forRsi`, `forRsiWithDecay`). Use those in tests and documentation snippets to avoid constructor ambiguity with the general-purpose overloads.
