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

## Backtesting a live series

- `BarSeriesManager` and `BacktestExecutor` run on the series you pass, keeping its begin index; they never copy it.
- A run never trades or prices positions after its window. A signal on the last bar that needs a later bar to fill (for example next-open execution at the end of a walk-forward fold) does not fill, and a position still open at the window end is marked at the last window close or ignored, per the criterion's open-position handling. Walk-forward folds instead always end flat: a position open at a fold's last bar is exited at its close and pays the transaction cost, so fold records can be chained. To end other runs flat the same way, wrap the execution model: `new ExitOnRunEndModel(new TradeOnNextOpenModel())`.
- A position entered before the analysed window (for example on a rolling series that evicted its entry) is valued at the window's first close by mark-to-market curves, so the window's results cover only what happened inside it.
- A backtest captures the series window when it starts and runs every strategy over exactly that window. It holds no series lock while strategies run, so a live `ConcurrentBarSeries` keeps accepting writes, and bars appended meanwhile are ignored.
- `BacktestExecutor` and walk-forward runs throw `IllegalStateException` if bars inside the window are replaced, updated in place (including a forming last bar), or evicted by retention before they finish, rather than return results computed from mixed bar revisions. A rolling series with a maximum bar count evicts on every append.
- Strategies should decide from values at the evaluated index. Whole-series properties such as `getBarCount()` or `Indicator.isStable()` depend on bars after that index and see bars appended during a backtest.
- For live data, pause writes during the backtest, or build the strategies on a stable copy, for example `series.getSubSeries(series.getBeginIndex(), series.getEndIndex())`, which also leaves out the forming bar.
- `BarSeries.withReadLock(...)` is for short, bar-only reads. Do not evaluate indicators or strategies inside it: indicator caches take their own locks and then read bars, so that order can deadlock with other readers once a writer is waiting.

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
scores are computed from the training window only. With a holdout window
configured, `topK` evaluations are reserved from the budget for the holdout
rebuild, so objective calls across training and holdout stay budget-exact.

| Situation | Engine | Notes |
| --- | --- | --- |
| Small, enumerable space that must be covered completely | `SearchPlan.grid(maxEvaluations)` | Lazy Cartesian iteration in deterministic order; reports `SEARCH_SPACE_EXHAUSTED` once every combination has been proposed or processed, including those rejected by the normalizer or validator |
| Large or mixed integer/decimal/boolean/categorical space | `SearchPlan.genetic(maxEvaluations, seed)` | Tournament selection with domain-aware crossover/mutation and elitism; the seeded run-local RNG keeps runs reproducible |
| Large numeric-only space | `SearchPlan.particleSwarm(maxEvaluations, seed)` | Global-best swarm with velocity clamping; integer dimensions are rounded deterministically, and boolean/categorical domains are rejected before any evaluation |
| The objective is noisy, the space is trivial, or a single baseline would do | Do not optimize | Search cannot create predictive value; a hand-picked baseline checked on a holdout window is the cheaper honest answer |

See `ta4jexamples.backtesting.SimpleMovingAverageRangeBacktest` for a
backtest-scored workflow and
`ta4jexamples.research.RelationshipObjectiveSearchExample` for an
event-synchronization (F1) workflow with a one-line grid/GA/PSO switch.

## Run Elliott wave pattern research (maintainers)

The experimental Elliott pattern study (test scope, not public API) has a
launcher, `ElliottResearch`, that writes one relocatable run directory per run.
Prerequisites: JDK 25, Maven, and dependencies already cached; the `smoke`
recipe is offline and finishes in seconds. Run from the repository root.

Unix shells:

```bash
mvn -q -pl ta4j-core test-compile exec:java -Dexec.args="run smoke --out target/elliott-research/smoke"
mvn -q -pl ta4j-core test-compile exec:java -Dexec.args="summarize target/elliott-research/smoke"
mvn -q -pl ta4j-core test-compile exec:java -Dexec.args="inspect target/elliott-research/smoke '<key from comparisons.csv>'"
```

PowerShell (quote the whole `-D` argument):

```powershell
mvn -q -pl ta4j-core test-compile exec:java "-Dexec.args=run smoke --out target/elliott-research/smoke"
mvn -q -pl ta4j-core test-compile exec:java "-Dexec.args=summarize target/elliott-research/smoke"
mvn -q -pl ta4j-core test-compile exec:java "-Dexec.args=inspect target/elliott-research/smoke '<key from comparisons.csv>'"
```

Relative paths resolve against the directory you run Maven from, here the
repository root. Recipes: `smoke` (synthetic,
real-data trace on by default), `frozen-cf525` (the frozen study over the bundled
datasets) and `explore --source candles.json --recipe recipe.json` (your own
candles and study settings). `--trace off|real|selected-null-member` with
`--block L --member M` controls evidence capture; `--overwrite` reuses an existing
run directory. `help` lists every option.

A run directory holds `run.json` (revision and whether the worktree was dirty,
recipe, configuration, dataset
sources and status), `reports/` (one study report per dataset),
`comparisons.csv` (one row per dataset, section, mode, detector, partition,
metric and null block length), `coverage.csv`, `summary.md`, `traces/`
(JSON lines per as-of bar) and `.run.lock`, which a run holds while it writes so
a second run into the same directory is refused. Each comparison sets the observed value against a
null reference band of member-level 2.5%-97.5% quantiles and an empirical
reference rank; neither is a confidence interval or a p-value, and partitions
are never pooled. `inspect` rebuilds a row's support from its trace and lists
counterexamples and rule disagreements; when the trace is missing or truncated
it exits with status 2 and prints the recapture command, quoted for a POSIX shell.
The command carries `--expect-fingerprint` (and, for `explore`, `--expect-source-sha256`),
so a recapture against a changed configuration or edited candles fails instead of
tracing different data. A run that declares `calibration` but captured no trace is
the exception: `inspect` prints the row's calibration estimates with a "real trace
not captured" note and exits with status 0. Coverage marks a partition `partial`
when it has no bars or a gap longer than seven bar periods.

Every run also records causal forward-outcome evidence for enrolled `MOTIVE_5`
events (events enroll once per placement, when it first becomes visible to the
detector at its decision bar): `events.jsonl` (header, one lifecycle record per
event, completion footer), `outcomes.csv` (one row per event and horizon with the
structural label and the price outcome) and `outcomes-summary.csv` (per dataset,
stream, partition and horizon tallies, comparators and a non-overlapping
cohort). Horizons are clipped to the partition, so labels never read beyond it
and unresolved events are censored rather than counted as failures. Structural
labels are success, invalidated/withdrawn, expired or censored; price outcomes
report raw and direction-aligned returns, excursions, and target/invalidation
touch order under the research rule and the released
`ElliottWaveOutcomeLabeler` same-bar rule. Comparators are the unconditional and
momentum-matched forward returns on the same tape. Summaries report counts and
bounds only, never significance, because events overlap. Null-member events are
included for the member chosen with `--trace selected-null-member`. An `explore`
recipe may add an optional `"outcomes"` object
(`{"horizons":[5,20,60],"structuralMode":"classical-all","invalidation":"origin-pivot"}`;
`invalidation` is `origin-pivot` or `origin-price`); the choices are recorded in
`run.json` and fixed before labels are read.

An `explore` recipe may also add an optional `"calibration"` object
(`{"horizon":20,"fit":"calibration","validation":"validation","evaluation":"holdout","minGroups":3}`;
only `horizon` is required and it must be one of `outcomes.horizons`). Without it
a run is unchanged, every artifact above is identical apart from the fingerprint
that hashes the recipe. With it the run adds `calibration-tables.json`,
`calibration-predictions.csv`, `calibration-summary.csv`,
`calibration-reliability.csv` and an "Outcome calibration" section in `summary.md`. The
target is the structural label "the correction completes before invalidation
within `horizon` bars"; the only feature is the heuristic rule score
`pass/(pass+fail)` frozen at enrollment (pending, unavailable and not-applicable
rules are coverage states, not failures), kept as a separate field from the
estimated probability. The estimator is a five-bin smoothed weighted frequency
`(S + 1) / (W + 2)` per available-rule mask; each eligible alternative of one
decision bar weighs `1 / m`, and a bin with fewer than `minGroups` distinct
decision groups or without both a success and a failure abstains with the exact
reason and raw support instead of emitting a probability. Fitting is
chronological: the `validation` table is fitted on the `fit` partition and the
`evaluation` table on `fit` plus `validation`, and both use only labels whose
whole window had ended by the cutoff, so a score never uses a table fitted after
its decision bar. A candidate lineage (the same candidate key) that has any
fit-stage row is purged from the scored stage and counted in the `purged`
column of `calibration-summary.csv`, so one lineage never feeds both a table
and its score. A table is bound to the primary detector's actual
configuration (factory and parameters, not just its name) and momentum
lookback in addition to grammar, mode, rules and horizon, and `estimate`
rejects any other scope. Summaries report coverage, Brier and log-loss against
the unconditional base rate (reported as differences, never as significance),
and reliability bins for the group-weighted and non-overlapping-cohort views.
Estimates are marginal per alternative and are not normalised across
simultaneous alternatives. `inspect` shows each alternative's decision-time
estimate and fit support by default only for the scope the table was fitted on
(the primary detector's H1 `MOTIVE_5` rows); robustness-detector, H2 and
competing-grammar comparison keys print a "calibration not applicable to this
scope" line naming the differing field instead. It hides realised outcomes
unless `--retrospective` is given, and ranks by enrollment order unless
`--rank probability` asks for the estimate. This is calibration of a heuristic
score, not a forecast: poor calibration or worse-than-baseline loss are
reported results, and nothing here claims predictive efficacy.

## Companion user guides

- Backtesting: https://ta4j.github.io/ta4j-wiki/Backtesting.html
- Live trading: https://ta4j.github.io/ta4j-wiki/Live-trading.html
- Risk/criteria: https://ta4j.github.io/ta4j-wiki/Analysis-Criteria-and-Risk-Metrics.html
