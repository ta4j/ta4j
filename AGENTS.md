# AGENTS Instructions for ta4j

## MANDATORY WORKFLOW - DO NOT SKIP

**CRITICAL: Before completing ANY code changes, you MUST:**

1. ✅ Run the full build script: `scripts/run-full-build-quiet.sh`
   - **This is NOT optional.** Do not skip this step, even for "simple" changes.
   - **When:** After every code change that affects build/test behavior (which is almost always)
   - **Windows:** Use Git Bash or MSYS2 (not WSL) for native NTFS paths. Example: `bash scripts/run-full-build-quiet.sh` from the workspace root.
   - **What it does:** Runs `mvn -B clean license:format formatter:format test install`
   - **Required outcome:** Build must be GREEN (all tests pass, no failures/errors)
   - **Report:** Always include the script's output showing `Tests run / Failures / Errors / Skipped` numbers and the log path

2. ✅ Verify the build succeeded with zero failures and zero errors
3. ✅ Include the test results in your completion message

**If you skip the full build, you have NOT completed the task. The build script is the ONLY acceptable way to validate changes.**

## Repository-wide conventions

### Build and Test Workflow

- **During development:** Use narrow Maven test commands for fast feedback (e.g., `mvn -pl ta4j-core test -Dtest=...`). For focused module testing, you may use `scripts/run-full-build-quiet.sh -pl ta4j-core` to get filtered logs while still validating a single module.
- **Before completion:** ALWAYS run `scripts/run-full-build-quiet.sh` (without `-pl` flags) - this is mandatory, not optional
- The script stores the full log under `.agents/logs/` and prints aggregated test totals
- All new or changed code from feature work or bug fixes must be covered by comprehensive unit tests that demonstrate correctness and serve as a shield against future regressions.
- When debugging issues, take every opportunity to create focused unit tests that allow you to tighten the feedback loop. When possible design the tests to also serve as regression bulwarks for future changes.
- At the end of every development cycle (code materially impacted) capture the changes into the CHANGELOG.md under the appropriate section using existing style/format conventions. Avoid duplicates and consolidate Unreleased section items as needed.
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
