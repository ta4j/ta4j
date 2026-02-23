# AGENTS instructions for `org.ta4j.core.strategy.named` tests

Follow `ta4j-core/src/test/java/AGENTS.md` for global test policy; this file adds strategy-specific coverage requirements.

## Coverage focus

- Mirror constructor contracts: test both `(BarSeries, String...)` convenience construction and fully typed construction.
- Validate serialization labels follow `<SimpleName>_<param...>`.
- Add negative tests for malformed/insufficient parameter lists.

## Alignment checks

- Ensure fixtures and expectations reflect eager validation behavior (`IllegalArgumentException` paths).
- When new public strategy APIs are introduced, verify corresponding production classes include `@since` tags.
