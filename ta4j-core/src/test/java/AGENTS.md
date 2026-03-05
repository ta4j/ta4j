# AGENTS instructions for `ta4j-core/src/test/java`

These are cross-cutting test rules for ta4j-core. Deeper package guides can add stricter local conventions.

## Non-negotiable test policy

- Never skip tests without explicit user approval.
- Never hide failures with `@Ignore`, unconditional assumptions, or silent try/catch blocks.
- Treat failing tests as defects to fix or escalate, not as noise.

## Test design conventions

- Use `assertThrows` for exception assertions (avoid `@Test(expected = ...)` and manual try/catch assertions).
- Avoid reflection-based access to private APIs; test through public behavior or refactor for dependency injection.
- Prefer dependency injection when production code is hard to test.
- Keep assertion intent explicit and deterministic; avoid flaky timing assumptions.

## Editing hygiene

- Keep diffs surgical in test files (touch only lines relevant to the behavior change).
- Add imports incrementally rather than replacing entire import blocks.
- Match production typing standards in tests: prefer explicit local variable types.
- Use `var` in tests only when the type is immediately obvious from a constructor or literal.
