# ta4j-core

`ta4j-core` contains the production API surface for strategy modeling, backtesting, analysis, and live-style record management.

## Start here

- Series model: `BarSeries`, `BaseBarSeries`, `ConcurrentBarSeries`
- Strategy model: `Indicator`, `Rule`, `Strategy`
- Execution model: `BarSeriesManager`, `BacktestExecutor`, `TradeExecutionModel`
- Trade/fill model: `TradingRecord`, `BaseTradingRecord`, `Trade`, `TradeFill`
- Analysis model: `AnalysisCriterion` and criteria packages

## Choose the right execution path

- Single strategy over one series: use `BarSeriesManager`
- Many candidates, tuning, weighted ranking: use `BacktestExecutor`
- Broker-confirmed/partial-fill replay: use manual evaluation loop + `BaseTradingRecord.operate(fill)`

## Live evaluation semantics (important)

- ta4j evaluates the bar state you provide at the requested index; it does not force closed-candle-only evaluation.
- If your feed uses `addBar(bar, true)` or equivalent replace-last-bar updates, you are evaluating a live (still-forming) candle.
- If you evaluate only after adding a completed bar, you are evaluating closed candles.
- For live execution, call `shouldEnter(index, tradingRecord)` / `shouldExit(index, tradingRecord)` and keep `tradingRecord` synchronized with broker-confirmed fills.
- Add an integration guard (for example, one entry per bar index) to avoid duplicate orders when a live candle keeps the same rule state across multiple updates.

## Trace rule decisions

- To answer "why did this fire?" or "why did this not fire?", enable SLF4J `TRACE` on the relevant `Rule` or `Strategy` logger and run the normal `isSatisfied(...)`, `shouldEnter(...)`, or `shouldExit(...)` call.
- TRACE logging is the off switch; there is no mutable trace mode to set on shared rule or strategy instances.
- Default trace output is `Rule.TraceMode.VERBOSE`, which emits the evaluated rule plus child-rule path/depth fields where a composite rule evaluates children.
- Use `Rule#isSatisfiedWithTraceMode(..., Rule.TraceMode.SUMMARY)` or `Strategy#shouldEnterWithTraceMode(...)` / `shouldExitWithTraceMode(...)` for a one-shot parent summary when child logs would be too noisy.
- Price and numeric comparison rules include the values they compared, the operator or window, and a short `reason` so a single rule trace line explains the decision.
- Stop rules include flat `key=value` decision fields such as `currentPrice`, `entryPrice`, `stopPrice`, `side`, trailing extremes, and configured amount or percentage fields.

## Choose the right series type

- Single-threaded backtests and deterministic local runs: `BaseBarSeries`
- Concurrent ingestion/evaluation pipelines: `ConcurrentBarSeries`

## Choose the right numeric model

- Precision-first workflows: `DecimalNum`
- Throughput-first workflows with accepted floating-point tradeoffs: `DoubleNum`

## Choose the right correlation metric

All rolling correlation indicators live under
`org.ta4j.core.indicators.statistics` and return `NaN` when the requested
window is not ready or the statistic is undefined.

| Question | Indicator | Notes |
| --- | --- | --- |
| Are two continuous signals linearly related in the same window? | `CorrelationCoefficientIndicator` | Pearson-style baseline for dense, simultaneous numeric series |
| Is the relationship monotonic but not necessarily linear? | `SpearmanRankCorrelationIndicator` | Uses average ranks for ties before applying Pearson correlation |
| Do ordered samples agree when ties matter? | `KendallTauIndicator` | Rolling Kendall tau-b with tie correction |
| Does one signal lead or trail another by a fixed number of bars? | `LaggedCorrelationIndicator` | Positive lag means the first indicator leads the second |
| Do two signals share non-linear structure? | `DistanceCorrelationIndicator` | Builds centered pairwise distance matrices; `O(window^2)` per calculated index |
| Does knowing one discretized state reduce uncertainty about another? | `MutualInformationIndicator` | Equal-width bins for v1; reports natural-log mutual information in nats |
| Does correlation only matter inside a trend, volatility, or custom state? | `RegimeSegmentedCorrelationIndicator` | Filters each rolling window with an `Indicator<Boolean>` regime selector |

## Measure lead/lag, shape, and event dependence

Three complementary analyzers under `org.ta4j.core.indicators.statistics` and
`org.ta4j.core.analysis.event` answer relationship questions the rolling
indicators leave open:

- **Lead/lag structure over a lag range** —
  `LeadLagCorrelationIndicator` scans an inclusive lag range as a rolling
  indicator: `getValue(index)` is the selected lag's signed correlation and
  `getProfile(index)` returns the full `Profile` (one `Point` per lag,
  undefined lags retained), every lag tying for the best score, and one
  deterministic selected lag (smallest absolute, then smallest signed). The
  lag sign convention matches `LaggedCorrelationIndicator`: positive means
  the first indicator leads the second. The symmetric convenience
  constructor searches the normal `[-maximumLag, maximumLag]` range;
  selection policy picks the maximum signed or maximum absolute correlation,
  and the selected correlation always keeps its original sign.
- **Shape similarity under time distortion** —
  `DynamicTimeWarpingDistanceIndicator` computes the minimum-cost monotonic
  alignment between two rolling windows. The recommended configuration
  (`DynamicTimeWarpingDistanceIndicator.Config.shapeComparison(radius)`;
  see `LeadLagDtwEventAnalysisExample`) compares shapes (z-score
  normalization, squared local distance) inside a bounded Sakoe–Chiba band
  with path-length normalization; unconstrained warping is an explicit
  opt-in. A zero radius forces diagonal pointwise alignment; the reported
  cost is the sum or mean of the local costs, according to path-cost
  normalization. Complexity is `O(W * min(W, 2r + 1))` time and `O(W)`
  memory for window `W` and radius `r`.
- **Continuous predictor vs sparse current-or-future event** —
  `EventMutualInformationEvaluator` measures how much a continuous indicator
  state reduces uncertainty about whether a target event occurs in an
  explicit `[start, end]` bar window ahead of the sample (offset zero
  labels the sample's own bar; a positive start offset makes the window
  future-only). It reports raw MI (nats), target
  entropy, normalized MI (`MI / H(Y)`), event prevalence, and bin
  diagnostics. Equal-frequency binning never splits tied predictor values;
  a non-finite sample makes the result undefined instead of silently
  dropping data, and target windows never cross the evaluation partition
  boundary (no look-ahead into validation).

These tools describe association, not causation. The deterministic
cross-capability demo `ta4jexamples.analysis.LeadLagDtwEventAnalysisExample`
shows Net Momentum versus close price through all three lenses on a
committed daily BTC dataset.

## Choose the right parameter search engine

`org.ta4j.core.research.ParameterResearch` runs budget-exact parameter
searches from one fluent builder: declare typed domains, build a candidate
per evaluation window, score it with an objective, and rank the top
candidates against an untouched holdout window. All engines share one
contract — the evaluation budget is never exceeded, duplicate proposals and
cache hits are not charged, seeded engines are deterministic, and training
scores are computed from the training window only.

| Situation | Engine | Notes |
| --- | --- | --- |
| Small, enumerable space that must be covered completely | `SearchPlan.grid(maxEvaluations)` | Lazy Cartesian iteration in deterministic order; reports `SEARCH_SPACE_EXHAUSTED` only when every combination was evaluated |
| Large or mixed integer/decimal/boolean/categorical space | `SearchPlan.genetic(maxEvaluations, seed)` | Tournament selection with domain-aware crossover/mutation and elitism; the seeded run-local RNG keeps runs reproducible |
| Large numeric-only space | `SearchPlan.particleSwarm(maxEvaluations, seed)` | Global-best swarm with velocity clamping; integer dimensions are rounded deterministically, and boolean/categorical domains are rejected before any evaluation |
| The objective is noisy, the space is trivial, or a single baseline would do | Do not optimize | Search cannot create predictive value; a hand-picked baseline checked on a holdout window is the cheaper honest answer |

See `ta4jexamples.backtesting.SimpleMovingAverageRangeBacktest` for a
backtest-scored workflow and
`ta4jexamples.research.RelationshipObjectiveSearchExample` for an
event-synchronization (F1) workflow with a one-line grid/GA/PSO switch.

## Companion user guides

- Backtesting: https://ta4j.github.io/ta4j-wiki/Backtesting.html
- Live trading: https://ta4j.github.io/ta4j-wiki/Live-trading.html
- Risk/criteria: https://ta4j.github.io/ta4j-wiki/Analysis-Criteria-and-Risk-Metrics.html
