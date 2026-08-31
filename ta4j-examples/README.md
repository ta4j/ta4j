# ta4j-examples

`ta4j-examples` is the runnable companion module for `ta4j-core`.
It is organized as progressive learning tracks so production-minded Java developers can move from first run to robust execution workflows.

## Prerequisites

- JDK 25+
- Maven 3.9+
- Build from the repository root where `ta4j-core` and `ta4j-examples` are both available

## Run an example
From the repository root, run the canonical command (install first so the
example resolves `ta4j-core` from the local repository, then execute only in
`ta4j-examples`; a directly invoked goal would also run on upstream reactor
modules, and `ta4j-core` has no main class):

```bash
mvn -pl ta4j-examples -am install -DskipTests \
  && mvn -pl ta4j-examples exec:java -Dexec.mainClass=ta4jexamples.Quickstart
```

The install step is required on a clean clone: without it, `exec:java` cannot
resolve the ta4j-core snapshot from the local repository. Replace
`ta4jexamples.Quickstart` with any class listed below.

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
- `ta4jexamples.research.RelationshipObjectiveSearchExample`
- `ta4jexamples.backtesting.BacktestPerformanceTuningHarness`

Run a fixed throughput matrix and write `matrix_performance.json`:

```bash
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

### 4) Live-style workflows

- `ta4jexamples.bots.TradingBotOnMovingBarSeries`
- `ta4jexamples.backtesting.TradeFillRecordingExample`

### 5) Charting and diagnostics

- `ta4jexamples.indicators.IndicatorsToChart`
- `ta4jexamples.indicators.CandlestickChart`
- `ta4jexamples.analysis.CashFlowToChart`

### 6) Forecast modeling and calibration

- `ta4jexamples.analysis.forecast.RollingConformalForecastExample`
- `ta4jexamples.analysis.forecast.KinematicKalmanForecastExample`
- `ta4jexamples.analysis.forecast.CorrentropyKalmanExample`

Run the ossified BTC daily analog and rolling-conformal walkthrough:

```bash
./mvnw -pl ta4j-examples -am install \
  && ./mvnw -pl ta4j-examples exec:java \
  -Dexec.mainClass=ta4jexamples.analysis.forecast.RollingConformalForecastExample
```

Run the ossified S&P 500 weekly kinematic Kalman walkthrough:

```bash
./mvnw -pl ta4j-examples -am install \
  && ./mvnw -pl ta4j-examples exec:java \
  -Dexec.mainClass=ta4jexamples.analysis.forecast.KinematicKalmanForecastExample
```

This example composes ATR-derived process variance with an inverted-CHOP
measurement-variance regime, shares one cached state across one-, four-, and
thirteen-week forecasts, and applies rolling conformal calibration to the
four-week interval. The bundled Yahoo Finance snapshot is fixed through July
30, 2026; its final July 27 weekly aggregate is an as-of partial week.

Run the robust correntropy Kalman walkthrough over the same ossified S&P 500
weekly series:

```bash
./mvnw -pl ta4j-examples -am install \
  && ./mvnw -pl ta4j-examples exec:java \
  -Dexec.mainClass=ta4jexamples.analysis.forecast.CorrentropyKalmanExample
```

This example derives illustrative squared-price Q/R variances from ATR,
smooths the close with the correntropy Kalman filter (dimensionless kernel
bandwidth), and logs the robust estimate, residual, and measurement weight at
an isolated wick, across a sustained move, and as rejection-weighted residual
evidence without activating a trading strategy.

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
