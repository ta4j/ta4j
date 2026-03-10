# ta4jexamples.analysis test instructions

- Regression tests under this package should assert on underlying analysis data and persisted artifacts, not rendered chart labels or screenshots, unless the visual rendering itself is the feature under test.
- Tests that cover long-running analysis or calibration flows should lock down the incremental persistence contract:
  - primary dataset results are written before portability or secondary checks begin
  - phase-complete summaries are durable even if later phases fail
  - final aggregate output appends to earlier persisted artifacts instead of replacing them
