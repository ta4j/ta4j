# AGENTS instructions for ta4j-cli

Use `ta4j-cli` when a task needs a deterministic local command surface for ta4j experimentation instead of adding one-off Java entry points.

## Workflow

- Prefer the bounded commands: `strategy backtest`, `strategy walk-forward`, `strategy sweep`, `indicator test`, `rule test`, `forecast run`, `performance run`, and `performance compare`.
- Prefer `--output` JSON artifacts for automation and review.
- Generate charts only when a file artifact is needed, and keep chart flows headless-safe.

## Contract

- `--data-file` accepts local CSV or JSON files.
- Prefer compact expressions for interactive strategy, indicator, rule, and criterion inputs; preserve `NamedStrategy` / `NamedRule` labels and canonical or version 2 JSON files for existing workflows.
- Use `forecast run` for return-state inspection and deterministic Monte Carlo return or price projections.
- Keep dynamic sizing explicit with `--position-sizing`; use `--borrow-side` whenever borrowing costs do not use the short-only default.
- Keep output schemas additive and backward compatible.

## Reuse

- Reuse `ta4j-core` execution and reporting APIs before adding CLI-only abstractions.
- Reuse `ta4j-examples` charting, datasource, and strategy helpers before cloning them.
- Update `README.md` when commands, flags, or output contracts change.
