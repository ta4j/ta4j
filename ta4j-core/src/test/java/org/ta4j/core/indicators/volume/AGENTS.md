# AGENTS instructions for volume indicator tests

Follow `ta4j-core/src/test/java/AGENTS.md` and `.../indicators/AGENTS.md`; this file adds volume-window specifics.

- Exercise anchored reset behavior explicitly (for example `getAnchorIndex` / `getWindowStartIndex`).
- Prefer `assertNumEquals` for numeric comparisons and AssertJ for boolean/`isNaN()` assertions.
