# ta4j-cli Agent Guide

Use `ta4j-cli` when an agent needs a deterministic local command surface for ta4j experimentation instead of writing one-off Java entry points.

## Preferred Workflow

1. Prepare a local OHLCV input file.
2. Run one of the bounded commands:
   - `backtest`
   - `walk-forward`
   - `sweep`
   - `indicator-test`
3. Request JSON output with `--output`.
4. Request `--chart` only when an image artifact is actually needed.

## Stable Inputs

- `--data-file` accepts local CSV or JSON paths.
- `--strategy` accepts bounded built-in aliases.
- `--strategy-json` accepts serialized ta4j strategy payloads.
- `--criteria` accepts stable criterion aliases, not raw class names.

## Stable Outputs

- JSON output is the canonical machine-readable artifact.
- Walk-forward output includes the `WalkForwardConfig` hash and fold-level metrics.
- Sweep output includes total candidate count plus the retained top-K leaderboard.

## Extension Rules

- Reuse `ta4j-core` execution/reporting APIs before adding CLI-only abstractions.
- Reuse `ta4j-examples` charting and datasource helpers before cloning them.
- Keep new command-line flags additive and backwards compatible.
- Keep chart generation file-based and headless-safe.
