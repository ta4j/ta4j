# AGENTS instructions for `ta4j-core/src/test/java`

These are cross-cutting test rules for ta4j-core. Deeper package guides can add stricter local conventions.

## Non-negotiable test policy

- Never skip tests without explicit user approval.
- Never hide failures with `@Ignore`, unconditional assumptions, or silent try/catch blocks.
- Treat failing tests as defects to fix or escalate, not as noise.

## Test design conventions

- Default unit-test ownership is 1:1: `MyType` belongs in `MyTypeTest`. Do not add roll-up, inventory, or broad
  suite-style unit test classes for new coverage; place focused tests in the owning production class's test file.
- Use `assertThrows` for exception assertions (avoid `@Test(expected = ...)` and manual try/catch assertions).
- Avoid reflection-based access to private APIs; test through public behavior or refactor for dependency injection.
- Prefer dependency injection when production code is hard to test.
- Keep assertion intent explicit and deterministic; avoid flaky timing assumptions.
- Profile slow test work from the full gate's Surefire class timings before optimizing; keep measured before/after evidence when a class is targeted.
- Avoid blanket `AbstractIndicatorTest` parameterization for structural tests. Use both `DoubleNum` and `DecimalNum` only when the behavior under test depends on `NumFactory`, precision, arithmetic, or factory coercion.
- Keep unit tests isolated from heavyweight fixtures, generated histories, sleeps, and broad end-to-end workflows unless that boundary is the behavior under test.

## Editing hygiene

- Keep diffs surgical in test files (touch only lines relevant to the behavior change).
- Add imports incrementally rather than replacing entire import blocks.
- Match production typing standards in tests: prefer explicit local variable types.
- Use `var` in tests only when the type is immediately obvious from a constructor or literal.
