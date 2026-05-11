# ta4j-examples

`ta4j-examples` is the runnable companion module for `ta4j-core`.
It is organized as progressive learning tracks so production-minded Java developers can move from first run to robust execution workflows.

## Run an example

From the repository root:

```bash
mvn -pl ta4j-examples exec:java -Dexec.mainClass=ta4jexamples.Quickstart
```

Replace `ta4jexamples.Quickstart` with any class listed below.

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
