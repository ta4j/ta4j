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

## Canonical Local Input

The canonical MVP input is a local OHLCV file. CSV input should include a header row and these columns in order:

1. `date`
2. `open`
3. `high`
4. `low`
5. `close`
6. `volume`

JSON input may use the existing ta4j example bar-series formats already supported by `JsonFileBarSeriesDataSource`.

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

## Command Surface

### Shared Options

- `--data-file`: local CSV or JSON file to load.
- `--timeframe`: resample the loaded series to `1m`, `5m`, `15m`, `1h`, `4h`, `1d`, or an ISO-8601 duration such as `PT5M`.
- `--from-date`, `--to-date`: date-only (`YYYY-MM-DD`) or full ISO timestamps used to trim the series before execution.
- `--execution-model`: `next-open` or `current-close`.
- `--capital`: portfolio size used as the default stake when `--stake-amount` is omitted.
- `--stake-amount`: per-trade amount. When both are supplied, it must not exceed `--capital`.
- `--commission`: non-negative transaction fee rate.
- `--borrow-rate`: non-negative holding cost rate.
- `--criteria`: stable criterion aliases such as `gross-return`, `net-profit`, `romad`, `sharpe`, and `total-fees`.
- `--output`: JSON file path. When omitted, JSON is written to stdout.
- `--chart`: optional JPEG output path for a trading chart artifact.
- `--progress`: emit bounded progress messages to stderr during longer runs.
- `--unstable-bars`: override the strategy unstable-bar count.

### Command-Specific Options

- `backtest`
  - `--strategy`: either a bounded alias (`sma-crossover`, `rsi2`, `cci-correction`, `global-extrema`, `moving-momentum`) or a `NamedStrategy` label such as `DayOfWeekStrategy_MONDAY_FRIDAY`.
  - `--strategy-json`: path to a serialized ta4j strategy payload. Use this instead of `--strategy` when you want to replay a previously exported strategy definition.
  - `--param key=value`: strategy parameter override. The bounded built-in strategy that currently consumes custom params is `sma-crossover`. When `--strategy` uses a `NamedStrategy` label, encode parameter values in the label instead of passing `--param`.
- `walk-forward`
  - `--strategy`: required bounded alias or `NamedStrategy` label.
  - `--param key=value`: strategy parameter override for alias-based strategies only.
  - `--min-train-bars`, `--test-bars`, `--step-bars`, `--purge-bars`, `--embargo-bars`, `--holdout-bars`, `--primary-horizon-bars`, `--optimization-top-k`, `--seed`: walk-forward splitter and ranking controls.
- `sweep`
  - `--strategy`: currently `sma-crossover`.
  - `--param key=value`: fixed parameter applied to every candidate.
  - `--param-grid key=v1,v2,...`: candidate grid dimensions.
  - `--top-k`: number of ranked candidates to keep in the output.
- `indicator-test`
  - `--indicator`: one of `sma`, `ema`, `rsi`, `cci`.
  - `--param period=<count>`: indicator period override.
  - `--entry-below`, `--entry-above`, `--exit-below`, `--exit-above`: optional threshold rules. If none are supplied, the command defaults to close-price crossovers around the selected indicator.

## Parameter Coverage Examples

The examples in this section intentionally exercise every supported option at least once.

### Backtest With Built-In Strategy Parameters

This example covers the shared execution flags plus `--strategy` and repeatable `--param`.

```bash
java -jar ta4j-cli/target/ta4j-cli-*-jar-with-dependencies.jar \
  backtest \
  --data-file /absolute/path/AAPL-PT1D-20130102_20131231.csv \
  --strategy sma-crossover \
  --param fast=5 \
  --param slow=20 \
  --timeframe 1d \
  --from-date 2013-02-01 \
  --to-date 2013-10-31 \
  --execution-model current-close \
  --capital 10000 \
  --stake-amount 2500 \
  --commission 0.001 \
  --borrow-rate 0.0001 \
  --criteria net-profit,romad,sharpe,total-fees \
  --unstable-bars 20 \
  --chart /tmp/backtest-aapl.jpg \
  --output /tmp/backtest-aapl.json \
  --progress
```

### Backtest From Serialized Strategy JSON

Use this when you already have a serialized ta4j strategy payload and want to rerun it without mapping it back to a bounded alias.

```bash
java -jar ta4j-cli/target/ta4j-cli-*-jar-with-dependencies.jar \
  backtest \
  --data-file /absolute/path/AAPL-PT1D-20130102_20131231.csv \
  --strategy-json /absolute/path/exported-strategy.json \
  --criteria net-profit \
  --output /tmp/backtest-from-json.json
```

### Backtest From A NamedStrategy Label

Use this when the strategy already has a compact serialized label of the form `<SimpleClassName>_<param1>_<param2>...`.

```bash
java -jar ta4j-cli/target/ta4j-cli-*-jar-with-dependencies.jar \
  backtest \
  --data-file /absolute/path/AAPL-PT1D-20130102_20131231.csv \
  --strategy DayOfWeekStrategy_MONDAY_FRIDAY \
  --criteria net-profit,sharpe \
  --output /tmp/backtest-day-of-week.json
```

### Walk-Forward With Full Fold Controls

This example exercises every walk-forward-specific configuration flag in one run.

```bash
java -jar ta4j-cli/target/ta4j-cli-*-jar-with-dependencies.jar \
  walk-forward \
  --data-file /absolute/path/AAPL-PT1D-20130102_20131231.csv \
  --strategy sma-crossover \
  --param fast=5 \
  --param slow=20 \
  --timeframe 1d \
  --from-date 2013-01-02 \
  --to-date 2013-12-31 \
  --execution-model next-open \
  --capital 10000 \
  --stake-amount 1000 \
  --commission 0.001 \
  --borrow-rate 0.0001 \
  --criteria gross-return \
  --unstable-bars 20 \
  --min-train-bars 120 \
  --test-bars 40 \
  --step-bars 20 \
  --purge-bars 3 \
  --embargo-bars 2 \
  --holdout-bars 20 \
  --primary-horizon-bars 5 \
  --optimization-top-k 4 \
  --seed 99 \
  --chart /tmp/walk-forward-aapl.jpg \
  --output /tmp/walk-forward-aapl.json \
  --progress
```

### Sweep With Fixed And Grid Parameters

`sweep` combines shared execution flags with both fixed params and candidate grids.

```bash
java -jar ta4j-cli/target/ta4j-cli-*-jar-with-dependencies.jar \
  sweep \
  --data-file /absolute/path/AAPL-PT1D-20130102_20131231.csv \
  --strategy sma-crossover \
  --param slow=50 \
  --param-grid fast=3,5,8 \
  --criteria net-profit \
  --top-k 3 \
  --output /tmp/sweep-aapl.json \
  --progress
```

### Indicator Test With Mean-Reversion Thresholds

This example demonstrates `--indicator`, `--entry-below`, and `--exit-above`.

```bash
java -jar ta4j-cli/target/ta4j-cli-*-jar-with-dependencies.jar \
  indicator-test \
  --data-file /absolute/path/AAPL-PT1D-20130102_20131231.csv \
  --indicator rsi \
  --param period=14 \
  --entry-below 30 \
  --exit-above 70 \
  --criteria net-profit,sharpe \
  --output /tmp/indicator-rsi.json
```

### Indicator Test With Breakout Thresholds

This companion example demonstrates `--entry-above` and `--exit-below`.

```bash
java -jar ta4j-cli/target/ta4j-cli-*-jar-with-dependencies.jar \
  indicator-test \
  --data-file /absolute/path/Binance-ETH-USD-PT5M-20230313_20230315.json \
  --indicator ema \
  --param period=21 \
  --entry-above 1800 \
  --exit-below 1750 \
  --output /tmp/indicator-ema-breakout.json \
  --progress
```

## Common Use Cases

These recipes are shorter than the coverage examples and focus on the workflows most users are likely to repeat.

### Local Equity Backtest With JSON Output And Chart

```bash
java -jar ta4j-cli/target/ta4j-cli-*-jar-with-dependencies.jar \
  backtest \
  --data-file /absolute/path/AAPL-PT1D-20130102_20131231.csv \
  --strategy sma-crossover \
  --param fast=5 \
  --param slow=20 \
  --criteria net-profit,romad \
  --chart /tmp/aapl-backtest.jpg \
  --output /tmp/aapl-backtest.json
```

### Walk-Forward Validation Before Promoting A Strategy

```bash
java -jar ta4j-cli/target/ta4j-cli-*-jar-with-dependencies.jar \
  walk-forward \
  --data-file /absolute/path/AAPL-PT1D-20130102_20131231.csv \
  --strategy sma-crossover \
  --param fast=5 \
  --param slow=20 \
  --min-train-bars 120 \
  --test-bars 40 \
  --step-bars 20 \
  --holdout-bars 20 \
  --criteria gross-return \
  --output /tmp/aapl-walk-forward.json
```

### Replay A Named Intraday Strategy Label

```bash
java -jar ta4j-cli/target/ta4j-cli-*-jar-with-dependencies.jar \
  backtest \
  --data-file /absolute/path/Binance-ETH-USD-PT5M-20230313_20230315.json \
  --strategy HourOfDayStrategy_9_17 \
  --output /tmp/hour-of-day-backtest.json
```

### Rank A Small SMA Search Grid

```bash
java -jar ta4j-cli/target/ta4j-cli-*-jar-with-dependencies.jar \
  sweep \
  --data-file /absolute/path/AAPL-PT1D-20130102_20131231.csv \
  --strategy sma-crossover \
  --param-grid fast=3,5 \
  --param-grid slow=20,30 \
  --top-k 2 \
  --criteria net-profit \
  --output /tmp/aapl-sweep.json
```

### Prototype A Threshold-Based Indicator Rule

```bash
java -jar ta4j-cli/target/ta4j-cli-*-jar-with-dependencies.jar \
  indicator-test \
  --data-file /absolute/path/Binance-ETH-USD-PT5M-20230313_20230315.json \
  --indicator rsi \
  --param period=14 \
  --entry-below 35 \
  --exit-above 65 \
  --criteria net-profit,sharpe \
  --output /tmp/eth-rsi-threshold.json
```

## Notes

- Criterion aliases are stable CLI names such as `gross-return`, `net-profit`, `romad`, `sharpe`, and `total-fees`.
- Charts are opt-in via `--chart <jpeg-path>` and save directly to disk without opening a window.
- `sweep` ranks candidate strategies deterministically and keeps only the requested top-K output set.
- `indicator-test` is intentionally bounded around common indicators and sanity-check strategy scaffolding.
- `NamedStrategy` labels follow the compact format `<SimpleClassName>_<param1>_<param2>...`, for example `HourOfDayStrategy_9_17` or `DayOfWeekStrategy_MONDAY_FRIDAY`.
