# ta4j-examples

`ta4j-examples` is the runnable companion module for `ta4j-core`.
It is organized as progressive learning tracks so production-minded Java developers can move from first run to robust execution workflows.

## Prerequisites

- JDK 25+
- Maven 3.9+
- Build from the repository root where `ta4j-core` and `ta4j-examples` are both available

## Run an example

From the repository root:

```bash
mvn -pl ta4j-examples exec:java -Dexec.mainClass=ta4jexamples.Quickstart
```

Replace `ta4jexamples.Quickstart` with any class listed below.

## Verify your run succeeded

Use these quick checks before moving to the next track:

- `ta4jexamples.Quickstart`: prints step-by-step run stages and trade/return metrics
- `ta4jexamples.backtesting.TradingRecordParityBacktest`: logs execution-model comparison and parity check success
- `ta4jexamples.backtesting.TradeFillRecordingExample`: logs streamed-vs-grouped fill handling and lot-matching outcomes

If chart windows do not appear, you are likely in a headless environment; switch to chart file output or run on a GUI-enabled machine.

## Learning tracks

### 1) First strategy and metrics

- `ta4jexamples.Quickstart`
- `ta4jexamples.analysis.StrategyAnalysis`

### 2) Data sourcing and normalization

- `ta4jexamples.backtesting.YahooFinanceBacktest`
- `ta4jexamples.backtesting.CoinbaseBacktest`
- `ta4jexamples.datasources.YahooFinanceHttpBarSeriesDataSource`
- `ta4jexamples.datasources.CoinbaseHttpBarSeriesDataSource`

### 3) Execution semantics and performance

- `ta4jexamples.backtesting.TradingRecordParityBacktest`
- `ta4jexamples.backtesting.TradeFillRecordingExample`
- `ta4jexamples.backtesting.SimpleMovingAverageRangeBacktest`
- `ta4jexamples.backtesting.BacktestPerformanceTuningHarness`
- `ta4jexamples.performance.PerformanceExperimentRunner`

Run a fixed throughput matrix and write `matrix_performance.json`:

```bash
mvn -pl ta4j-examples -am compile
mvn -pl ta4j-examples exec:java \
  -Dexec.mainClass=ta4jexamples.backtesting.BacktestPerformanceTuningHarness \
  -Dexec.args="--throughputControl --throughputOutputDir .agents/benchmarks/backtest-throughput/current --matrixStrategyCounts 250,500,1000 --matrixBarCounts 500,1000 --matrixMaxBarCountHints 0 --executionMode topK --topK 10 --parallelism 1"
```

Compare two refs on the same host/spec/dataset:

```bash
scripts/benchmark-backtest-throughput.sh HEAD^ HEAD
```

Both refs must include throughput-control support; use `HEAD^` vs `HEAD` after
the harness and optimization commits are in place. The JSON artifacts include a
hashed `hostId` plus JVM/OS metadata so reports can be shared without exposing a
raw machine hostname.

Run a reusable performance experiment and write `performance.json` plus
`summary.md`:

```bash
mvn -pl ta4j-examples -am install -DskipTests
mvn -pl ta4j-examples exec:java \
  -Dexec.mainClass=ta4jexamples.performance.PerformanceExperimentRunner \
  -Dexec.args="--experiment kalman-filter --barCounts 1000,5000,10000 --scenarios sequential,endOnly,endThenReverse,sparseAfterHighWatermark --repetitions 5 --outputDir .agents/benchmarks/performance/kalman-filter/current"
```

Compare the same experiment across two refs:

```bash
scripts/benchmark-performance-experiment.sh HEAD^ HEAD -- \
  --experiment kalman-filter \
  --barCounts 1000,5000,10000 \
  --scenarios sequential,endOnly,endThenReverse,sparseAfterHighWatermark \
  --repetitions 5
```

### 4) Live-style workflows

- `ta4jexamples.bots.TradingBotOnMovingBarSeries`
- `ta4jexamples.backtesting.TradeFillRecordingExample`

### 5) Charting and diagnostics

- `ta4jexamples.indicators.IndicatorsToChart`
- `ta4jexamples.indicators.CandlestickChart`
- `ta4jexamples.analysis.CashFlowToChart`

## Suggested progression

1. `ta4jexamples.Quickstart`
2. `ta4jexamples.backtesting.TradingRecordParityBacktest`
3. `ta4jexamples.backtesting.TradeFillRecordingExample`
4. `ta4jexamples.backtesting.SimpleMovingAverageRangeBacktest`
5. `ta4jexamples.backtesting.YahooFinanceBacktest` or `ta4jexamples.backtesting.CoinbaseBacktest`
6. `ta4jexamples.bots.TradingBotOnMovingBarSeries`

## Companion guides

- Troubleshooting: https://ta4j.github.io/ta4j-wiki/Troubleshooting-Hub.html
- Backtesting realism gate: https://ta4j.github.io/ta4j-wiki/Backtesting-Realism-Checklist.html
- Live operations runbook: https://ta4j.github.io/ta4j-wiki/Live-Trading-Runbook.html
- Canonical end-to-end path: https://ta4j.github.io/ta4j-wiki/Canonical-User-Journey.html
- Expected example output signatures: https://ta4j.github.io/ta4j-wiki/Examples-Expected-Outputs.html
- API migration compatibility map: https://ta4j.github.io/ta4j-wiki/Migration-and-Version-Compatibility.html
