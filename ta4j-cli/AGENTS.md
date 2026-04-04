# AGENTS instructions for ta4j-cli

Use `ta4j-cli` when a task needs a deterministic local command surface for ta4j experimentation instead of adding one-off Java entry points.

## Workflow

- Prefer the bounded commands: `backtest`, `walk-forward`, `sweep`, `indicator-test`, and `rule-test`.
- Prefer `--output` JSON artifacts for automation and review.
- Generate charts only when a file artifact is needed, and keep chart flows headless-safe.

## Contract

- `--data-file` accepts local CSV or JSON files.
- Keep strategy inputs on `NamedStrategy` labels or serialized strategy JSON files.
- Keep indicator inputs on serialized indicator JSON, either inline via `--indicator` or on disk via `--indicator-json-file`.
- Keep rule inputs on `NamedRule` labels or serialized rule JSON files.
- Keep criteria inputs on fully qualified `AnalysisCriterion` class names until first-class named criteria exist.
- Keep output schemas additive and backward compatible.

## Reuse

- Reuse `ta4j-core` execution and reporting APIs before adding CLI-only abstractions.
- Reuse `ta4j-examples` charting, datasource, and strategy helpers before cloning them.
- Update `README.md` when commands, flags, or output contracts change.
