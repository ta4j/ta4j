# AGENTS instructions for ta4j-cli

Use `ta4j-cli` when a task needs a deterministic local command surface for ta4j experimentation instead of adding one-off Java entry points.

## Workflow

- Prefer the bounded commands: `backtest`, `walk-forward`, `sweep`, and `indicator-test`.
- Prefer `--output` JSON artifacts for automation and review.
- Generate charts only when a file artifact is needed, and keep chart flows headless-safe.

## Contract

- `--data-file` accepts local CSV or JSON files.
- Keep strategy and criterion inputs on stable CLI aliases rather than raw class names.
- Keep output schemas additive and backward compatible.

## Reuse

- Reuse `ta4j-core` execution and reporting APIs before adding CLI-only abstractions.
- Reuse `ta4j-examples` charting, datasource, and strategy helpers before cloning them.
- Update `README.md` when commands, flags, or output contracts change.
