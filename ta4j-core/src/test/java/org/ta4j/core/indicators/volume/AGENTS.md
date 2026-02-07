# Volume indicator test notes

- Exercise anchored resets explicitly (for example, call `getAnchorIndex` or
  `getWindowStartIndex`) so regressions in window handling surface quickly.
- When comparing floating point results, prefer `assertNumEquals` for clarity
  and AssertJ for boolean helpers like `isNaN()`.
