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
- `ta4jexamples.portfolio.StaticPortfolioBacktest`: logs buy-and-hold versus monthly-rebalanced summaries, each rebalance's turnover and cost, and a criteria-computed net return that matches the result's total return
- `ta4jexamples.portfolio.PortfolioCorrelationAnalysis`: prints price, simple-return, and log-return matrices plus complete-linkage clusters for anonymous sample universes
- `ta4jexamples.portfolio.DiversifiedPortfolioAnalysis`: writes adjusted-data correlation charts, allocation comparisons, HTML, CSV tables, and an optional external-AI response

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

### 4) Portfolio simulation

- `ta4jexamples.portfolio.StaticPortfolioBacktest`: `PortfolioSeries` -> `PortfolioAllocation` -> `PortfolioSeriesManager.run(...)`, with a cash sleeve, calendar rebalancing, and the equity curve fed to existing criteria
- `ta4jexamples.portfolio.PortfolioCorrelationAnalysis`
- `ta4jexamples.portfolio.DiversifiedPortfolioAnalysis`

Generate the complete YTD report:

```bash
./mvnw -pl ta4j-examples -am compile
./mvnw -pl ta4j-examples exec:java \
  -Dexec.mainClass=ta4jexamples.portfolio.DiversifiedPortfolioAnalysis
```

The default output directory is `ta4j-examples/target/portfolio-analysis`. To embed a response from an external model safely in the HTML report, rerun with `-Dexec.args="--ai-analysis=/path/to/response.md"`.

### 5) Live-style workflows

- `ta4jexamples.bots.TradingBotOnMovingBarSeries`
- `ta4jexamples.backtesting.TradeFillRecordingExample`

### 6) Charting and diagnostics

- `ta4jexamples.indicators.IndicatorsToChart`
- `ta4jexamples.indicators.CandlestickChart`
- `ta4jexamples.analysis.CashFlowToChart`

### 6) Forecast modeling and calibration

- `ta4jexamples.analysis.forecast.RollingConformalForecastExample`
- `ta4jexamples.analysis.forecast.KinematicKalmanForecastExample`
- `ta4jexamples.analysis.forecast.AdaptiveKalmanNoiseExample`
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

Run the adaptive ATR/relative-volume Kalman noise comparison:

```bash
./mvnw -pl ta4j-examples -am install \
  && ./mvnw -pl ta4j-examples exec:java \
  -Dexec.mainClass=ta4jexamples.analysis.forecast.AdaptiveKalmanNoiseExample
```

This opt-in recipe scores fixed-noise, ATR-squared, and ATR/relative-volume
models against a last-close baseline on identical one-step forecast origins.
Add `-Dexec.args="--lag-noise"` to use prior-bar dynamic noise. It excludes the
snapshot's incomplete terminal week from scoring and does not fit parameters or
claim automatic calibration. The
[adaptive Kalman noise walkthrough](adaptive-kalman-noise.md) explains units,
warm-up, missing versus zero volume, clipping, and evaluation.

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
an isolated wick, across a sustained move, and as rejected-residual evidence
using `(1 - measurement weight) * residual`, without activating a trading
strategy.

Each dynamic source/Q/R value is normalized through the series `NumFactory`,
allowing inputs that return a different `Num` implementation when conversion
remains finite. The residual view accepts source indicators that expose either
the backing series or its read-only view.
Finite extreme endpoint innovations are whitened before subtraction, so
`DoubleNum` range limits do not turn a representable robust update into
`NaN.NaN`.

### 7) Replay an Elliott research run bar by bar

`ElliottReplayInspector` replays what a research run (`ta4j-core/README.md`,
"Run Elliott wave pattern research") knew at each bar. It reads only the run
directory: it never recomputes a count, fetches data, or writes into the run, and
it rejects a trace whose dataset, run revision, fingerprint, source digest or
null block/member coordinates differ from `run.json`.

Reproducible smoke walkthrough (synthetic data, no network):

```bash
./mvnw -q -pl ta4j-core test-compile exec:java "-Dexec.args=run smoke --out target/elliott-research/smoke --overwrite"
./mvnw -q -pl ta4j-core test-compile exec:java "-Dexec.args=inspect target/elliott-research/smoke '<key from comparisons.csv>'"
./mvnw -pl ta4j-examples -am install -DskipTests   # once, for exec:java
./mvnw -q -pl ta4j-examples exec:java \
  -Dexec.mainClass=ta4jexamples.charting.replay.ElliottReplayInspector \
  "-Dexec.args=target/elliott-research/smoke --key '<key>' --commands 'tnext;select 1;history;seek 40;export' --out target/elliott-replay"
```

`inspect` prints the matching `Replay:` command for a comparison, so the key to
replay is the one whose counterexample or rule disagreement you want to see. The
cursor opens at `--at` when given, otherwise where the session opens; `next`/`prev` step by bar,
`tnext`/`tprev` jump to the next recorded state change, `seek <bar|instant>` moves
to the latest record at or before it, `select <candidate key|#>` follows one
candidate across bars, `history` lists its recorded version changes (when the
stream is longer than the 5,000-record lookback it says where the scanned history
starts instead of inventing a change), and `export` writes
`frame-<bar>.json`, `frame-<bar>.txt` and `frame-<bar>.jpg`. `--out` must not be
the run directory or inside it, and none of the three frame files may be a
symbolic link; the replay refuses before writing anything.
`--interactive` starts a prompt (`help` lists the commands); `--display` opens the
chart in a window.

Real-data counterexample walkthrough: run the frozen study with trace capture
(`run frozen-cf525 --trace real --out <dir>`; `frozen-cf525` traces nothing by
default and replay needs the trace), `inspect <dir> '<key>'` for a row whose
observed value sits outside its null band, replay that key with the printed
command, jump to the listed counterexample bar with `seek`, `select` the candidate
the report names, and `export` the frame as evidence.

To replay the resampled null member behind a null reference band, capture it
with `run ... --trace selected-null-member --block <L> --member <M>` (`L` must be
the row's null block length) and replay with
`--trace selected-null-member`. Only `h1` and `h2` rows map to the producer's
`null` stream (mode is the grammar for `h1`, the ablation mode for `h2`); other
rows are refused with an explanation. The member's own recorded prices are shown,
never the real series.

## Suggested progression

1. `ta4jexamples.Quickstart`
2. `ta4jexamples.backtesting.TradingRecordParityBacktest`
3. `ta4jexamples.backtesting.TradeFillRecordingExample`
4. `ta4jexamples.backtesting.SimpleMovingAverageRangeBacktest`
5. `ta4jexamples.portfolio.StaticPortfolioBacktest`
6. `ta4jexamples.portfolio.DiversifiedPortfolioAnalysis`
7. `ta4jexamples.backtesting.YahooFinanceBacktest` or `ta4jexamples.backtesting.CoinbaseBacktest`
8. `ta4jexamples.bots.TradingBotOnMovingBarSeries`

## Companion guides

- Troubleshooting: https://ta4j.github.io/ta4j-wiki/Troubleshooting-Hub.html
- Backtesting realism gate: https://ta4j.github.io/ta4j-wiki/Backtesting-Realism-Checklist.html
- Live operations runbook: https://ta4j.github.io/ta4j-wiki/Live-Trading-Runbook.html
- Canonical end-to-end path: https://ta4j.github.io/ta4j-wiki/Canonical-User-Journey.html
- Expected example output signatures: https://ta4j.github.io/ta4j-wiki/Examples-Expected-Outputs.html
- API migration compatibility map: https://ta4j.github.io/ta4j-wiki/Migration-and-Version-Compatibility.html
