# ta4j-examples Reflection Removal and Chart Focus PRD

## Context
Refactor `ta4j-examples` to eliminate reflection usage in production and test code. Tests must exercise behavior through public APIs and realistic setup flows. Chart tests should not steal desktop focus.

## Requirements
- Remove reflection usage across `ta4j-examples` production and tests.
- Replace reflection-based test access to private methods with public-API-driven coverage.
- Ensure charting tests in `ta4j-examples` set `ChartFrame#setFocusableWindowState(false)` so spawned windows do not steal focus.
- Keep behavior intact and maintain/update unit coverage.
- Update changelog with user-visible impact.
- End with a full green build using `scripts/run-full-build-quiet.sh`.

## Design Notes
- Prefer using existing chart workflow/displayer public entry points instead of probing internals.
- Centralize no-focus behavior in production chart display code if this reliably covers all charting tests.
- Avoid adding new test-only hooks unless public API coverage is impossible.

## Checklist
- [x] Identify all reflection usage under `ta4j-examples/src/main` and `ta4j-examples/src/test`.
- [x] Refactor production code to remove reflection.
- [x] Refactor tests to remove reflection and cover via public APIs.
- [x] Apply no-focus chart frame behavior for chart-related tests.
- [x] Run targeted `ta4j-examples` tests and fix failures.
- [x] Update `CHANGELOG.md`.
- [x] Run `scripts/run-full-build-quiet.sh` and verify zero failures/errors.
- [x] Stage and commit changes.

## Decisions Log
- 2026-02-26: Create dedicated worktree `examples-remove-reflection-focus` on branch `feature/examples-remove-reflection-focus`.
- 2026-02-26: Removed reflection-based access to `SwingChartDisplayer` internals in tests; teardown now relies on configured public behavior and standard frame disposal.
- 2026-02-26: Applied `setFocusableWindowState(false)` to all chart window creation points in `ta4j-examples`.
