# ta4j-cli

`ta4j-cli` ships a first-class command-line entry point for bounded ta4j workflows. The MVP stays local-file-first and reuses the existing ta4j backtest, walk-forward, reporting, and charting APIs instead of introducing a parallel runtime.

## Supported Commands

- `backtest`
- `walk-forward`
- `sweep`
- `indicator-test`

## Build

```bash
./mvnw -pl ta4j-cli -am package
```

The package phase produces a runnable fat jar at `ta4j-cli/target/ta4j-cli-<version>-jar-with-dependencies.jar`.

## Quick Start

```bash
java -jar ta4j-cli/target/ta4j-cli-*-jar-with-dependencies.jar \
  backtest \
  --data-file /absolute/path/AAPL-PT1D-20130102_20131231.csv \
  --strategy sma-crossover \
  --param fast=5 \
  --param slow=20 \
  --criteria net-profit,romad \
  --output /tmp/backtest.json
```

```bash
java -jar ta4j-cli/target/ta4j-cli-*-jar-with-dependencies.jar \
  walk-forward \
  --data-file /absolute/path/AAPL-PT1D-20130102_20131231.csv \
  --strategy sma-crossover \
  --param fast=5 \
  --param slow=20 \
  --criteria gross-return \
  --output /tmp/walk-forward.json
```

## Canonical Local Input

The canonical MVP input is a local OHLCV file. CSV input should include a header row and these columns in order:

1. `date`
2. `open`
3. `high`
4. `low`
5. `close`
6. `volume`

JSON input may use the existing ta4j example bar-series formats already supported by `JsonFileBarSeriesDataSource`.

## Command Notes

- Criterion aliases are stable CLI names such as `gross-return`, `net-profit`, `romad`, `sharpe`, and `total-fees`.
- Charts are opt-in via `--chart <jpeg-path>` and save directly to disk without opening a window.
- `sweep` ranks candidate strategies deterministically and keeps only the requested top K output set.
- `indicator-test` is intentionally bounded around common indicators and sanity-check strategy scaffolding.
