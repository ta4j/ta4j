# ta4jexamples.analysis test instructions

- Regression tests under this package should assert on underlying analysis data and persisted artifacts, not rendered chart labels or screenshots, unless the visual rendering itself is the feature under test.
- For long-running analysis/calibration flows, tests should lock down incremental persistence:
  - primary results land before portability/secondary checks
  - phase outputs survive later failures
  - final aggregate output appends rather than replaces
