## Unreleased

### Added
- **Charting time axis mode**: Added `TimeAxisMode` with `BAR_INDEX` support to compress non-trading gaps (weekends/holidays) on charts while keeping bar timestamps intact.
- **Concurrent real-time bar series pipeline**: Introduced core support for concurrent, streaming bar ingestion with
  a dedicated series (`ConcurrentBarSeries`/builder), realtime bar model (`RealtimeBar`/`BaseRealtimeBar`), and
  streaming-bar ingestion helpers to enable candle reconciliation and side/liquidity-aware trade aggregation.
- **Sharpe Ratio**: Added `SharpeRatioCriterion` and its related calculation classes
- Added **ThreeInsideUpIndicator** and **ThreeInsideDownIndicator**
- Added **MorningStarIndicator** and **EveningStarIndicator**
- Added **BullishKickerIndicator** and **BearishKickerIndicator**
- Added **PiercingIndicator** and **DarkCloudIndicator**

### Changed
- **Bar builders null handling**: Bar builders now skip null-valued bars entirely instead of inserting placeholder/null bars, leaving gaps when inputs are missing or invalid.
- **Charting overlays**: Refactored overlay renderer construction and centralized time-axis domain value selection to reduce branching without changing chart output.
- **Charting defaults**: Centralized chart styling defaults (anti-aliasing, background, title paint) for consistency across chart types.
- **TimeBarBuilder**: Enhanced with trade ingestion logic, time alignment validation, and RealtimeBar support.
- **BaseBarSeriesBuilder**: Deprecated `setConstrained` in favor of deriving constrained mode from max-bar-count configuration.
- **Release workflow notifications**: Post GitHub Discussion updates for release-scheduler and release runs with decision summaries.
- **Workflow lint hook**: Added a repo `pre-push` hook to run `actionlint` on workflow changes (see CONTRIBUTING).
- **Release health workflow**: Added scheduled checks for tag reachability, snapshot version drift, stale release PRs, and missing release notes, with summaries posted to Discussions.
- **Two-phase release workflows**: Added `prepare-release.yml` and `publish-release.yml` to split release preparation from tagging and deployment.
- **Release workflow branching**: Auto-merge the release PR by default, with optional direct push to the default branch when `RELEASE_DIRECT_PUSH=true`.
- **Agent workflow**: Allow skipping the full build when the only changes are within `.github/workflows/`, `CHANGELOG.md`, or documentation-only files (e.g., `*.md`, `docs/`).
- **Release process docs**: Overhauled with clearer steps, rationale, and example scenarios.
- **Release scheduler notifications**: Include run mode and timestamp in the discussion header.
- **Release notifications**: Include run mode and timestamp in the release discussion header.
- **Release scheduler dispatch**: Route automated releases through `prepare-release.yml` and include the binary change count in the AI prompt, with deterministic no-release outputs for zero binary changes.
- **Release automation tokens**: Use `GH_TA4J_REPO_TOKEN` for release push operations when available.
- **Release token preflight**: Fail fast when `GH_TA4J_REPO_TOKEN` lacks write permission (warn-only in dry-run mode).
- **Release scheduler enablement**: Gate scheduled runs on `RELEASE_SCHEDULER_ENABLED` (defaults to disabled when unset).
- **Factory selection from bars**: Derive the NumFactory from the first available bar price instead of assuming a specific price is always present.
- **CashFlow**: Added a realized-only calculation mode alongside the default mark-to-market cash flow curve.

### Fixed
- **TimeBarBuilder**: Preserve in-progress bars when trade ingestion skips across multiple time periods.
- **Release workflow notifications**: Fix discussion comment posting in workflows (unescaped template literals).
- **Release health workflow**: Ensure discussion notifications are posted even when summary generation fails and avoid `github-script` core redeclaration errors.
- **CashFlow**: Prevented NaN values when a position opens and closes on the same bar index.
- **BarSeries MaxBarCount**: Fixed sub-series creation to preserve the original series max bars, instead of resetting it to default Integer.MAX_VALUE 
- **Release scheduler**: Gate release decisions on binary-impacting changes (`pom.xml` or `src/main/**`) so workflow-only updates no longer trigger releases.
- **Release version validation**: Fixed version comparison in `prepare-release.yml` to properly validate that `nextVersion` is greater than `releaseVersion` using semantic version sorting, preventing invalid version sequences.

## 0.22.1 (2026-01-15)

### Added
- **Manual GitHub Release workflow trigger**: Added `workflow_dispatch` support with a required tag input so maintainers can backfill or re-run a GitHub Release directly from the Actions UI.
- **GitHub Release dry-run option**: Added a `dryRun` flag for manual GitHub Release triggers to preview notes, validate artifacts, and preflight release token permissions without publishing.
- **Manual actionlint trigger**: Added `workflow_dispatch` support so workflow linting can be run on demand.

### Changed
- **GitHub Release execution path**: Release creation now relies on tag-push triggers (and the manual dispatch option) instead of being invoked directly from `release.yml`, with the workflow checking out the target tag to align notes and artifacts.
- **Release automation tokens**: `release-scheduler.yml` and `release.yml` use `GITHUB_TOKEN`, while `github-release.yml` uses the `GH_TA4J_REPO_TOKEN` classic PAT for GitHub Release creation under org token restrictions.

### Fixed
- **GitHub Release artifacts**: Build now uses the production-release profile so javadoc jars are generated and artifact validation succeeds.
- **GitHub Release asset uploads**: Removed overlapping upload patterns to prevent duplicate asset uploads from failing the release.


## 0.22.0 (2025-12-29)

### Breaking Changes
- **CachedIndicator and RecursiveCachedIndicator synchronization changes**: `CachedIndicator` and `RecursiveCachedIndicator` no longer use `synchronized` methods. Thread safety is now achieved through internal locking using `ReentrantReadWriteLock`. **Action required**: Code that relied on external synchronization using indicator instances (e.g., `synchronized(indicator) { ... }`) must be updated to use explicit external locks.
- **SqueezeProIndicator return type change**: `SqueezeProIndicator` return type changed from `Boolean` to `Num`. Use `getSqueezeLevel(int)` or `isInSqueeze(int)` for compression state instead of direct boolean checks.

### Added
- Added GitHub Actions workflow linting (`actionlint`) to catch workflow expression and shell syntax errors early.
- **Num.isValid() utility method**: Added static `Num.isValid(Num)` method as the logical complement of `Num.isNaNOrNull()`, providing a convenient way to check if a value is non-null and represents a real number. Used extensively throughout the Elliott Wave indicators package for robust NaN handling.
- **SqueezeProIndicator correction**: Corrected **SqueezeProIndicator** to mirror TradingView/LazyBear squeeze momentum output: SMA-based Bollinger vs. Keltner compression tiers (high/mid/low), SMA true range width (not Wilder ATR), and the LazyBear detrended-price momentum baseline used by `linreg` (with regression tests on AAPL/NEE 2025-12-05).
- **SuperTrend indicator usability improvements**: Enhanced the SuperTrend indicator suite with intuitive helper methods and comprehensive documentation for traders:
    - Added `isUpTrend(int)`, `isDownTrend(int)`, and `trendChanged(int)` methods to `SuperTrendIndicator` for easy trend detection and signal generation without manual band comparisons
    - Comprehensive Javadoc with formulas, trading signals, usage examples, and references to authoritative sources (Investopedia, TradingView)
    - Added `package-info.java` documentation for the supertrend package
    - Expanded unit test coverage with trend reversal scenarios, serialization roundtrip tests, and ratcheting behavior verification

- **Trendline and swing point analysis suite**: Added a comprehensive set of indicators for automated trendline drawing and swing point detection. These indicators solve the common problem of manually identifying support/resistance levels and drawing trendlines by automating the process while maintaining the same logic traders use when drawing lines manually. Useful for breakout strategies, trend-following systems, and Elliott Wave analysis.
    - **Automated trendline indicators**: `TrendLineSupportIndicator` and `TrendLineResistanceIndicator` project support and resistance lines by connecting confirmed swing points. The indicators select the best trendline from a configurable lookback window using a scoring system based on swing point touches, proximity to current price, and recency. Historical values are automatically backfilled so trendlines stay straight and anchored on actual pivot points (not confirmation bars), avoiding the visual artifacts common in other implementations. Works with any price indicator (high/low/open/close/VWAP) and handles plateau detection, NaN values, and irregular time gaps—trendlines remain straight even across weekends and holidays. Supports both dynamic recalculation (updates on each bar) and static mode (freeze the first established line and project forward).
    - **ZigZag pattern detection**: Added ZigZag indicator suite (`ZigZagStateIndicator`, `ZigZagPivotHighIndicator`, `ZigZagPivotLowIndicator`, `RecentZigZagSwingHighIndicator`, `RecentZigZagSwingLowIndicator`) that filters out insignificant price movements to reveal underlying trend structure. Unlike window-based swing indicators that require fixed lookback periods, ZigZag adapts dynamically using percentage or absolute price thresholds, making it particularly useful in volatile markets where fixed windows can miss significant moves. Pivot signals fire immediately when reversals are confirmed (no confirmation bar delay), and you can track support/resistance levels dynamically for mean-reversion strategies. Supports both fixed thresholds (e.g., 2% price movement) and dynamic thresholds based on indicators like ATR for volatility-adaptive detection.
    - **Fractal-based swing detection**: `RecentFractalSwingHighIndicator` and `RecentFractalSwingLowIndicator` implement Bill Williams' fractal approach, identifying swing points by requiring a specified number of surrounding bars to be strictly higher or lower. Includes plateau-aware logic to handle flat tops and bottoms, making it robust for real-world market data. Good choice if you prefer the classic fractal methodology or need consistent swing detection across different market conditions.
    - **Unified swing infrastructure**: All swing indicators share a common foundation (`AbstractRecentSwingIndicator` base class and `RecentSwingIndicator` interface) that provides consistent caching, automatic purging of stale swing points, and a unified API for accessing swing point indexes and values. Makes it straightforward to build custom swing detection algorithms or integrate with trendline tools—implement the interface and you get caching and lifecycle management automatically.
    - **Swing point visualization**: `SwingPointMarkerIndicator` highlights confirmed swing points on charts without drawing connecting lines, useful for visualizing where pivots occur. Works with any swing detection algorithm (fractal-based, ZigZag, or custom implementations).

- **Unified data source interface for seamless market data loading**: Finally, a consistent way to load market data regardless of where it comes from! The new `BarSeriesDataSource` interface lets you work with trading domain concepts (ticker, interval, date range) instead of wrestling with file paths, API endpoints, or parsing logic. Want to switch from CSV files to Yahoo Finance API? Just swap the data source implementation—your strategy code stays exactly the same. Implementations include:
  - `YahooFinanceBarSeriesDataSource`: Fetch live data from Yahoo Finance (stocks, ETFs, crypto) with optional response caching to speed up development and avoid API rate limits
  - `CoinbaseBarSeriesDataSource`: Load historical crypto data from Coinbase's public API with automatic caching
  - `CsvFileBarSeriesDataSource`: Load OHLCV data from CSV files with intelligent filename pattern matching (e.g., `AAPL-PT1D-20230102_20231231.csv`)
  - `JsonFileBarSeriesDataSource`: Load Coinbase/Binance-style JSON bar data with flexible date filtering
  - `BitStampCsvTradesFileBarSeriesDataSource`: Aggregate Bitstamp trade-level CSV data into bars on-the-fly
  
  All data sources support the same domain-driven API: `loadSeries(ticker, interval, start, end)`. No more remembering whether your CSV uses `_bars_from_` or `-PT1D-` in the filename, or which API endpoint returns what format. The interface handles the implementation details so you can focus on building strategies. File-based sources automatically search for matching files, while API-based sources fetch and cache data transparently. See the new `CoinbaseBacktest` and `YahooFinanceBacktest` examples for complete workflows.
  
- **Elliott Wave analysis package**: Added a comprehensive suite of indicators for Elliott Wave analysis, enabling automated wave counting, pattern recognition, and confidence scoring. The package handles the inherent ambiguity in Elliott Wave analysis by generating multiple plausible wave interpretations with confidence percentages, making it practical for algorithmic trading strategies. All indicators work with swing point data from `RecentSwingIndicator` implementations (fractal or ZigZag), automatically filtering and compressing swings to identify Elliott wave structures. The system treats `DoubleNum` NaN values as invalid using `Num.isNaNOrNull()` and `Num.isValid()`, preventing NaN pivots, ratios, and targets from leaking into analysis.
  - **Core swing processing**: `ElliottSwingIndicator` generates Elliott swings from `RecentSwingIndicator` swing points, `ElliottSwingCompressor` filters and compresses swings to identify wave structures, and `ElliottWaveCountIndicator` counts waves in the current pattern. `ElliottSwingMetadata` tracks swing relationships and wave structure.
  - **Fibonacci analysis**: `ElliottRatioIndicator` calculates Fibonacci retracement/extension ratios between waves, `ElliottChannelIndicator` projects parallel trend channels for wave validation, and `ElliottConfluenceIndicator` identifies price levels where multiple Fibonacci ratios converge. `ElliottFibonacciValidator` provides continuous proximity scoring methods (`waveTwoProximityScore()`, `waveThreeProximityScore()`, etc.) returning 0.0-1.0 instead of boolean pass/fail.
  - **Wave state tracking**: `ElliottPhaseIndicator` tracks the current wave phase (impulse waves 1-5, corrective waves A-B-C), and `ElliottInvalidationIndicator` surfaces invalidations when wave counts break down. `ElliottPhase` enum represents wave phases with degree information.
  - **Confidence scoring and scenario generation**: `ElliottConfidence` record captures confidence metrics with weighted factor scores (Fibonacci proximity 35%, time proportions 20%, alternation quality 15%, channel adherence 15%, structure completeness 15%). `ElliottConfidenceScorer` is a stateless utility that calculates confidence from swing data with configurable weights. `ElliottScenario` represents a single wave interpretation with confidence, invalidation level, and Fibonacci-based price targets. `ElliottScenarioSet` is an immutable container holding ranked alternative scenarios with convenience methods (`base()`, `alternatives()`, `consensus()`, `hasStrongConsensus()`, `confidenceSpread()`). `ScenarioType` enum classifies pattern types (IMPULSE, CORRECTIVE_ZIGZAG, CORRECTIVE_FLAT, CORRECTIVE_TRIANGLE, CORRECTIVE_COMPLEX, UNKNOWN). `ElliottScenarioGenerator` generates alternative interpretations by exploring different starting points, pattern types, and degree variations, with automatic pruning of low-confidence scenarios. `ElliottScenarioIndicator` is a CachedIndicator returning `ElliottScenarioSet` for each bar, integrating scenario generation with the indicator framework. `ElliottScenarioComparator` provides utilities for comparing scenarios with methods like `divergenceScore()`, `sharedInvalidation()`, `consensusPhase()`, and `commonTargetRange()`.
  - **Price projections and invalidation levels**: `ElliottProjectionIndicator` returns Fibonacci-based price targets from the base case scenario. `ElliottInvalidationLevelIndicator` returns the specific price level that would invalidate the current count, with PRIMARY/CONSERVATIVE/AGGRESSIVE modes.
  - **Facade and utilities**: `ElliottWaveFacade` provides a high-level API with scenario-based methods: `scenarios()`, `primaryScenario(int)`, `alternativeScenarios(int)`, `confidenceForPhase(int, ElliottPhase)`, `hasScenarioConsensus(int)`, `scenarioConsensus(int)`, `scenarioSummary(int)`. `ElliottDegree` enum represents wave degrees (SUBMINUETTE through GRAND_SUPERCYCLE) with methods for higher/lower degree navigation. `ElliottChannel` represents parallel trend channels for wave validation.
  - **Example and integration**: `ElliottWaveAnalysis` example demonstrates confidence percentages, scenario probability distributions, and alternative scenario display, showing how to integrate Elliott Wave analysis into trading strategies.

- **Chart annotation support**: Added `BarSeriesLabelIndicator` to the ta4j-examples charting package, enabling sparse bar-index labels for chart annotations. The indicator returns label Y-values (typically prices) at labeled indices and `NaN` elsewhere, with integrated support in `TradingChartFactory` for rendering text annotations on charts. Useful for marking significant events, wave labels, or custom annotations on trading charts.
- **Channel overlay helpers**: Added a `PriceChannel` interface (upper/lower/median, shared validity/width/contains helpers, and a boundary enum), a `ChannelBoundaryIndicator` charting helper, and `ChartBuilder.withChannelOverlay(...)` convenience methods (including a channel-indicator overload) for plotting channel boundaries like Elliott channels, Bollinger bands, and Keltner channels. Charting now validates that channel overlays include both upper and lower boundaries, provides TradingView-inspired muted defaults with channel fill shading and a dashed, lower-opacity median line, and exposes a fluent channel styling stage including interior fill controls.

- Introduced a thread-safe `ConcurrentBarSeries` with a dedicated builder and comprehensive concurrency tests for simultaneous reads and writes.
- Added streaming bar ingestion helpers to `ConcurrentBarSeries` so Coinbase WebSocket candles can populate a series without pulling in the XChange projects.
- Added realtime bar support (`RealtimeBar`/`BaseRealtimeBar`) with optional side/liquidity breakdowns; bar builders can emit realtime bars and `ConcurrentBarSeries` trade ingestion accepts optional side/liquidity metadata.
- Added configurable remainder carry-over policy for volume/amount bars to control whether side/liquidity splits follow threshold rollovers.

### Changed
- **Elliott Wave scenario probability JSON output**: Scenario probability values are now serialized as decimal ratios (0.0-1.0) rounded to two decimals instead of percentages, while log output remains in percent.
- **CachedIndicator and RecursiveCachedIndicator performance improvements**: Major refactoring to improve indicator caching performance:
    - **O(1) eviction**: Replaced ArrayList-based storage with a fixed-size ring buffer (`CachedBuffer`). When `maximumBarCount` is set, evicting old values now takes constant time instead of O(n) per-bar copies.
    - **Read-optimized locking**: Migrated from `synchronized` blocks to a non-fair `ReentrantReadWriteLock` with an optimistic (lock-free) fast path for cache hits. Cache misses and invalidation remain write-locked, while read-heavy workloads scale without serializing threads.
    - **Reduced allocation churn**: Eliminated `Collections.nCopies()` allocations in the common "advance by 1 bar" case. The ring buffer writes directly to slots without creating intermediate lists.
    - **Last-bar mutation caching**: Repeated calls to `getValue(endIndex)` on an unchanged in-progress bar now reuse the cached result. The cache automatically invalidates when the bar is mutated (via `addTrade()` or `addPrice()`) or replaced (via `addBar(..., true)`).
    - **Deadlock avoidance**: Fixed lock-order deadlock risk between last-bar caching and the main cache by ensuring last-bar computations and invalidation never run while holding locks needed by cache writes.
    - **Iterative prefill under single lock**: `RecursiveCachedIndicator` now uses `CachedBuffer.prefillUntil()` to compute gap values iteratively under one write lock, avoiding repeated lock re-entry and series lookups.
    - **Last-bar computation timeout**: Added 5-second timeout for waiting threads to prevent indefinite hangs if the thread owning a last-bar computation dies unexpectedly.
- **ta4j-examples runners and regression coverage**: Added main-source runners (`CachedIndicatorBenchmark`, `RuleNameBenchmark`, `TrendLineAndSwingPointAnalysis`) and a robust regression test (`TrendLineAndSwingPointAnalysisTest`) so full builds stay quiet while still validating trendlines/swing points and enabling chart generation via `main`.
- **BacktestPerformanceTuningHarness**: Expanded the example into a configurable tuning harness that sweeps strategy counts, bar counts, and `maximumBarCount` hints, capturing runtime + GC overhead and identifying the last linear ("sweet spot") configuration. Includes an optional heap sweep that forks a child JVM per `-Xmx` value.
- **Comprehensive README overhaul**: Completely rewrote the README to make Ta4j more approachable for new users while keeping it useful for experienced developers. Added a dedicated "Sourcing market data" section that walks through getting started with Yahoo Finance (no API key required!), explains all available data source implementations, and shows how to create custom data sources. Reorganized content with clearer navigation, better code examples, and practical tips throughout. The Quickstart example now includes proper error handling and demonstrates real-world data loading patterns. New users can go from zero to running their first backtest in minutes, while experienced quants can quickly find the advanced features they need.

### Removed
- **Legacy release workflow**: Removed `release.yml` now that `publish-release.yml` fully replaces it.
- Deleted `BuyAndSellSignalsToChartTest.java`, `CashFlowToChartTest.java`, `StrategyAnalysisTest.java`, `TradeCostTest.java`, `IndicatorsToChartTest.java`, `IndicatorsToCsvTest.java` from the ta4j-examples project. Despite designated as "tests", they simply launched the main of the associated class.
- Consolidated `SwingPointAnalysis.java` and `TrendLineAnalysis.java` into `TrendLineAndSwingPointAnalysis.java` to provide a unified analysis example combining both features.
- Removed `TopStrategiesExampleBacktest.java` and `TrendLineDefaultCapsTest.java` as part of example consolidation and test cleanup.

### Fixed
- **Rule naming now lightweight**: `Rule#getName()` is now a simple label (defaults to the class name) and no longer triggers JSON serialization. Composite rules build readable names from child labels without serialization side effects.
- **Explicit rule serialization APIs**: Added `Rule#toJson(BarSeries)`/`Rule#fromJson(BarSeries, String)` so serialization happens only when explicitly requested. Custom names are preserved via `__customName` metadata, independent of `getName()`.
- **Rule name visibility**: Made rule names volatile and added regression coverage so custom names set on one thread are always visible on others, preventing fallback to expensive default-name serialization under concurrency.
- Fixed GitHub Actions `release-scheduler.yml` failure in the `Compute new version` step (bash syntax) and hardened job `if:` conditions.
- Fixed **SuperTrendUpperBandIndicator** NaN contamination by recovering once ATR leaves its unstable window and aligning unstable-bar counts across SuperTrend components so values stabilize after ATR warms up.
- **Consistent NaN handling across all SuperTrend indicators**: `SuperTrendLowerBandIndicator` and `SuperTrendIndicator` now return NaN during the unstable period (when ATR values are not yet reliable), consistent with `SuperTrendUpperBandIndicator` and the project convention that indicators should return NaN during their unstable period. Previously, these indicators returned zero during the unstable period, which was inconsistent with other indicators and could lead to misleading calculations.
- **Test improvements for headless environment compatibility**: Updated strategy and charting tests (`RSI2StrategyTest`, `CCICorrectionStrategyTest`, `GlobalExtremaStrategyTest`, `MovingMomentumStrategyTest`, `ChartWorkflowTest`) to use `SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY` instead of `Assume.assumeFalse()` for headless environment checks. This allows tests to run reliably in both headless and non-headless environments without skipping, improving CI/CD compatibility and test coverage.

## 0.21.0 (2025-11-29) Skipped 0.20.0 due to a double version incrementing bug in the release-scheduler workflow

### Changed
- **Unified return representation system**: Say goodbye to inconsistent return formats across your analysis! Return-based criteria now use a unified `ReturnRepresentation` system that lets you choose how returns are displayed—whether you prefer multiplicative (1.12 for +12%), decimal (0.12), percentage (12.0), or logarithmic formats. Set it once globally via `ReturnRepresentationPolicy` or customize per-criterion. No more mental math converting between formats—Ta4j handles it all automatically. Legacy `addBase` constructors are deprecated in favor of the more expressive `ReturnRepresentation` enum.
- **Ratio criteria now speak your language**: All ratio-producing criteria now support `ReturnRepresentation`, so you can format outputs consistently across your entire analysis pipeline. Whether you're comparing strategies, measuring risk, or tracking performance metrics, everything uses the same format. Updated criteria include:
  - `VersusEnterAndHoldCriterion`: Strategy vs. buy-and-hold comparison (e.g., 0.5 = 50% better, displayed as 0.5, 50.0, or 1.5 depending on your preference)
  - `ReturnOverMaxDrawdownCriterion`: Reward-to-risk ratio (e.g., 2.0 = return is 2x drawdown)
  - `PositionsRatioCriterion`: Win/loss percentage (e.g., 0.5 = 50% winning)
  - `InPositionPercentageCriterion`: Time in market (e.g., 0.5 = 50% of time)
  - `CommissionsImpactPercentageCriterion`: Trading cost impact (e.g., 0.05 = 5% impact)
  - `AbstractProfitLossRatioCriterion` (and subclasses): Profit-to-loss ratio (e.g., 2.0 = profit is 2x loss)

  All ratio criteria default to `ReturnRepresentation.DECIMAL` (the conventional format for ratios), but you can override per-criterion or globally. Perfect for dashboards, reports, or when you need to match external data formats. See each criterion's javadoc for detailed examples.

- **Simplified Returns class implementation**: Removed unnecessary `formatOnAccess` complexity from `Returns` class, inlined trivial `formatReturn()` wrapper method, and improved documentation clarity. The class now has a cleaner separation of concerns with better cross-references between `Returns`, `ReturnRepresentation`, and `ReturnRepresentationPolicy`.


### Added
- Added `TrueStrengthIndexIndicator`, `SchaffTrendCycleIndicator`, and `ConnorsRSIIndicator` to expand oscillator coverage
- Added `PercentRankIndicator` helper indicator to calculate the percentile rank of a value within a rolling window
- Added `DifferenceIndicator` helper indicator to calculate the difference between current and previous indicator values
- Added `StreakIndicator` helper indicator to track consecutive up or down movements in indicator values
- Added `StochasticIndicator` as a generic stochastic calculation indicator, extracted from `SchaffTrendCycleIndicator` for reuse
- **AI-powered semantic release scheduler**: Added automated GitHub workflow that uses AI to analyze changes, determine version bumps (patch/minor/major), and schedule releases every 14 days. Includes structured approval process for major version bumps and OIDC token-based authentication for AI model calls. Enhanced release workflows with improved error handling, tag checking, and logging.

### Changed
- **EMA indicators now return NaN during unstable period**: `EMAIndicator`, `MMAIndicator`, and all indicators extending `AbstractEMAIndicator` now return `NaN` for indices within the unstable period (indices < `beginIndex + getCountOfUnstableBars()`). Previously, these indicators would return calculated values during the unstable period. **Action required**: Update any code that accesses EMA indicator values during the unstable period to handle `NaN` values appropriately, or wait until after the unstable period before reading values.
- **Pivot point indicators refactored for maintainability**: Refactored `PivotPointIndicator` and `DeMarkPivotPointIndicator` to extend a new `AbstractPivotPointIndicator` base class, eliminating code duplication and centralizing period calculation logic. The refactoring improves maintainability and makes it easier to add new pivot point variants in the future. All existing functionality remains unchanged—this is purely an internal improvement that makes the codebase cleaner and more extensible.

### Fixed
- **Pivot point indicators boundary condition**: Fixed `AbstractPivotPointIndicator.getBarsOfPreviousPeriod()` to correctly include the bar at `beginIndex` when it belongs to the previous period. Previously, `DeMarkPivotPointIndicator` used `>` (strictly greater than) which excluded the bar at `beginIndex`, causing incorrect pivot calculations when the first bar in the series was part of the previous period. The fix ensures all bars from the previous period are included, which is especially important for `DeMarkPivotPointIndicator` as it uses the open price from the earliest bar in the previous period. Both `PivotPointIndicator` and `DeMarkPivotPointIndicator` now correctly include all bars from the previous period in their calculations.

## 0.19 (released November 19, 2025)

### Breaking
- **`TradingStatement` is now an interface**: Converted to an interface implemented by `BaseTradingStatement`. This exposes the underlying `Strategy` and `TradingRecord` for advanced analysis workflows. **Action required**: Update any code that directly instantiates `TradingStatement` to use `BaseTradingStatement` instead.
- **PnL and return criteria refactored into net/gross variants**: Split `ProfitLossCriterion`, `ProfitCriterion`, `LossCriterion`, `AverageProfitCriterion`, `AverageLossCriterion`, `ReturnCriterion`, `ProfitLossRatioCriterion`, and `ProfitLossPercentageCriterion` into separate net and gross concrete classes. This provides explicit control over whether trading costs are included in calculations. **Action required**: Update imports and class names to use the appropriate net or gross variant based on your analysis needs.
- **Indicator operation classes consolidated**: [#1266](https://github.com/ta4j/ta4j/issues/1266) Unified `BinaryOperation`, `UnaryOperation`, `TransformIndicator`, and `CombineIndicator` into a cleaner API. **Action required**: Replace deprecated `TransformIndicator` and `CombineIndicator` usage with the new consolidated classes.
- **Drawdown criteria moved to sub-package**: Relocated `MaximumDrawdownCriterion` and `ReturnOverMaxDrawdownCriterion` to the `criteria/drawdown/` sub-package for better organization. **Action required**: Update import statements to reflect the new package location.

### Added
- **Rule naming support**: Added `Rule#getName()` and `Rule#setName(String)` methods to allow rules to have custom names for improved trace logging and serialization. Rules now default to JSON-formatted names that include type and component information, but can be overridden with custom labels for better readability in logs and debugging output.
- **Time-based trading rules**: Added `HourOfDayRule` and `MinuteOfHourRule` to enable trading strategies based on specific hours of the day (0-23) or minutes of the hour (0-59). These rules work with `DateTimeIndicator` to filter trading signals by time, enabling time-of-day based strategies.
- **Time-based strategy examples**: Added `HourOfDayStrategy` and `MinuteOfHourStrategy` as example implementations demonstrating how to use the new time-based rules in complete trading strategies.
- **Enhanced backtesting with performance tracking**: Introduced `BacktestExecutionResult` and `BacktestRuntimeReport` with new `BacktestExecutor` entry points. Users can now track per-strategy execution times, receive progress callbacks during long-running backtests, and efficiently stream top-k strategy selection for large strategy grids without loading all results into memory.
- **Strategy serialization for persistence**: Added `StrategySerialization` with `Strategy#toJson()` and `Strategy#fromJson(BarSeries, String)` methods. This enables users to save and restore complete strategy configurations (including entry/exit rules) as JSON, making it easy to share strategies, version control configurations, and build strategy libraries.
- **NamedStrategy serialization with compact format**: [#1349](https://github.com/ta4j/ta4j/issues/1349) Enabled `NamedStrategy` serialization/deserialization with compact labels (e.g., `ToggleNamedStrategy_true_false_u3`). Users can now persist strategy presets alongside their parameters in a human-readable format. Added registry/permutation helper APIs and lazy package scanning via `NamedStrategy.initializeRegistry(...)` for efficient strategy discovery.
- **Renko chart indicators**: [#1187](https://github.com/ta4j/ta4j/issues/1187) Added `RenkoUpIndicator`, `RenkoDownIndicator`, and `RenkoXIndicator` to detect Renko brick sequences, enabling users to build strategies based on Renko chart patterns.
- **Advanced drawdown analysis**: Added `CumulativePnL`, `MaximumAbsoluteDrawdownCriterion`, `MaximumDrawdownBarLengthCriterion`, and `MonteCarloMaximumDrawdownCriterion`. Users can now analyze drawdowns in absolute terms, measure drawdown duration, and estimate drawdown risk distributions through Monte Carlo simulation of different trade orderings.
- **Comprehensive commission tracking**: Added `CommissionsCriterion` to total commissions paid across positions and `CommissionsImpactPercentageCriterion` to measure how much trading costs reduce gross profit. This helps users understand the real impact of transaction costs on strategy performance.
- **Streak and extreme position analysis**: Added `MaxConsecutiveLossCriterion`, `MaxConsecutiveProfitCriterion`, `MaxPositionNetLossCriterion`, and `MaxPositionNetProfitCriterion`. Users can now identify worst loss streaks, best win streaks, and extreme per-position outcomes to better understand strategy risk and consistency.
- **Position timing analysis**: Added `InPositionPercentageCriterion` to calculate the percentage of time a strategy remains invested, helping users understand capital utilization and exposure.
- **Flexible bar building options**: Added `AmountBarBuilder` to aggregate bars after a fixed number of amount have been traded. Bars can now be built by `beginTime` instead of `endTime`, providing more flexibility in bar aggregation strategies.
- **Volume-weighted MACD**: Added `MACDVIndicator` to volume-weight MACD calculations, providing an alternative MACD variant that incorporates volume information.
- **Net momentum indicator**: Added `NetMomentumIndicator` for momentum-based strategy development.
- **Vote-based rule composition**: Added `VoteRule` class, enabling users to create rules that trigger based on majority voting from multiple underlying rules.
- **Enhanced data loading**: Added `AdaptiveJsonBarsSerializer` to support OHLC bar data from Coinbase or Binance, and new `JsonBarsSerializer.loadSeries(InputStream)` overload for easier data loading from streams.
- **Improved charting and examples**: Expanded charting utilities to overlay indicators with trading records, added `NetMomentumStrategy` and `TopStrategiesExample`, and bundled a Coinbase ETH/USD sample data set to demonstrate the new APIs.
- **Automated release pipeline**: Added GitHub workflow to automatically version, build, and publish artifacts to Maven Central. The pipeline uses `prepare-release.sh` to prepare release versions, creates release branches and tags, and publishes to Maven Central. Added `scripts/tests/test_prepare_release.sh` to validate release preparation functionality.
- **Enhanced performance reporting**: Added Gson `DurationTypeAdapter`, `BasePerformanceReport`, and revised `TradingStatementGenerator` so generated statements always carry their source strategy and trading record for complete traceability.
- **UnaryOperation helper**: Added `substitute` helper function to `UnaryOperation` for easier indicator transformations.
- **Testing infrastructure**: Added tests for `DoubleNumFactory` and `DecimalNumFactory`, unit tests around indicator concurrency in preparation for future multithreading features, and `DecimalNumPrecisionPerformanceTest` to demonstrate precision vs performance trade-offs.

### Changed
- **Robust NaN handling in EMA indicators**: Enhanced `AbstractEMAIndicator` (and thus `EMAIndicator` and `MMAIndicator`) with comprehensive NaN handling to prevent contamination of future values. When a NaN value is detected in the current input, the indicator returns `NaN` immediately. If a previous EMA value is `NaN`, the indicator gracefully recovers by resetting to the current input value, preventing NaN contamination of all future calculations. This aligns with project guidelines for robust NaN handling and improves data quality in composite indicators.
- **Consolidated EMA implementations**: Removed duplicate `SmoothingIndicator` class and replaced all usages with `EMAIndicator`, eliminating code duplication and ensuring consistent behavior across all EMA-based calculations.
- **Enhanced rule serialization with custom name preservation**: Improved `RuleSerialization` to preserve custom rule names set via `setName()` during serialization and deserialization. Custom names are now properly distinguished from default JSON-formatted names, enabling better strategy persistence and debugging workflows.
- **Improved trace logging with rule names**: Enhanced trace logging in `AbstractRule` and `BaseStrategy` to use rule names (custom or default) in log output, making it easier to identify which rules are being evaluated during strategy execution.
- **Unified logging backend**: Replaced Logback bindings with Log4j 2 `log4j-slf4j2-impl` so examples and tests share a single logging backend. Added Log4j 2 configurations for modules and tests. This simplifies logging configuration and ensures consistent behavior across all modules. Set unit test logging level to INFO and cleaned build output of all extraneous logging. 
- **More accurate return calculations**: Changed `AverageReturnPerBarCriterion`, `EnterAndHoldCriterion`, and `ReturnOverMaxDrawdownCriterion` to use `NetReturnCriterion` instead of `GrossReturnCriterion` to avoid optimistic bias. This provides more realistic performance metrics that account for trading costs.
- **Improved drawdown criterion behavior**: `ReturnOverMaxDrawdownCriterion` now returns 0 instead of `NaN` for strategies that never operate, and returns net profit instead of `NaN` for strategies with no drawdown. This makes the criterion more robust and easier to use in automated analysis.
- **More flexible stop rules**: `StopGainRule` and `StopLossRule` now accept any price `Indicator` instead of only `ClosePriceIndicator`. Users can now create stop rules based on high, low, open, or custom price indicators for more sophisticated exit strategies.
- **Enhanced swing indicators**: Reworked `RecentSwingHighIndicator` and `RecentSwingLowIndicator` with plateau-aware, NaN-safe logic and exposed `getLatestSwingIndex` for downstream analysis. This improves reliability and enables more advanced swing-based strategies.
- **Configurable numeric precision**: Reduced default `DecimalNum` precision from 32 to 16 digits, improving performance while still maintaining sufficient accuracy for most use cases. Users can configure precision based on their specific needs.
- **Improved numeric indicator chaining**: `NumericIndicator`'s `previous` method now returns a `NumericIndicator`, enabling fluent method chaining for indicator composition.
- **Enhanced trading statements**: Added `TradingRecord` property to `TradingStatement` for more downstream flexibility around analytics, enabling users to access the full trading record from performance reports.
- **Better code maintainability**: Removed magic number 25 in `UpTrendIndicator` and `DownTrendIndicator`, making the code more maintainable and self-documenting.
- **Modernized build infrastructure**: [#1399](https://github.com/ta4j/ta4j/issues/1399) Refreshed dependencies, plugins, and build tooling while enforcing Java 21 and Maven 3.9+. This ensures compatibility with modern development environments and security updates.
- **Maven Central distribution**: Changed snapshot distribution to Maven Central after OSSRH end-of-life, ensuring continued availability of snapshot builds.
- **Improved bar series builder**: `BaseBarSeriesBuilder` now automatically uses the `NumFactory` from given bars instead of the default one, ensuring consistent numeric types throughout bar series construction.

### Fixed
- **NaN contamination in EMA calculations**: Fixed issue where NaN values in EMA indicator inputs would contaminate all future EMA values. The indicator now gracefully recovers from NaN inputs by resetting to the current value, preventing propagation of invalid data through the calculation chain.
- **Kalman filter robustness**: Guarded `KalmanFilterIndicator` against NaN/Infinity measurements to keep the Kalman state consistent, preventing calculation errors when input data contains invalid values.
- **Recursive indicator stack overflow**: Fixed recursion bug in `RecursiveCachedIndicator` that could lead to stack overflow in certain situations, improving reliability for complex indicator calculations.
- **Cost tracking in enter-and-hold**: Fixed `EnterAndHoldCriterion` to properly keep track of transaction and hold costs, ensuring accurate performance comparisons.
- **Convergence divergence indicator**: Fixed strict rules of `ConvergenceDivergenceIndicator` for more accurate divergence detection.
- **Return over max drawdown calculation**: Fixed calculation for `ReturnOverMaxDrawdownCriterion` and `VersusEnterAndHoldCriterion` to ensure accurate performance metrics.
- **Profit/loss percentage calculation**: Refactored `ProfitLossPercentageCriterion` to correctly calculate aggregated return, fixing previous calculation errors.
- **Bar series trade parameter order**: Fixed swapped parameter naming in `BaseBarSeries#addTrade(final Number tradeVolume, final Number tradePrice)` to match the method signature order.
- **Bar builder aggregation**: Fixed aggregation of amount and trades in `VolumeBarBuilder` and `TickBarBuilder` to ensure accurate bar construction.
- **SMA unstable period calculation**: Corrected the calculation of unstable bars for the SMA indicator, ensuring indicators report accurate stability periods.
- **Java 25 compatibility**: Fixed `PivotPointIndicatorTest` to work with Java 25, ensuring compatibility with the latest Java versions. Note that this does not mean Ta4j as a whole now supports Java 25, that will come in a future release.
- **JSON data loading**: Fixed bug in `MovingAverageCrossOverRangeBacktest` that prevented successfully loading test JSON bar data.
- **Build performance**: Updated GitHub test workflow to cache dependencies for quicker builds, reducing CI/CD execution time.
- **Documentation**: Updated test status badge on README and clarified PnL criterion comments about trading costs for better user understanding.

### Removed/Deprecated
- **Deprecated indicator classes**: Removed `TransformIndicator` and `CombineIndicator` in favor of the consolidated `BinaryOperationIndicator` and `UnaryOperationIndicator` classes. **Action required**: Migrate any code using these deprecated classes to the new consolidated API.

## 0.18 (released May 15, 2025)

### Breaking
- Updated project Java JDK from 11 > 21
- Updated Github workflows to use JDK 21
- Extracted NumFactory as source of numbers with defined precision
- Replaced `ZonedDateTime` with `Instant`
- Renamed `FixedDecimalIndicator` with `FixedNumIndicator`
- Moved `BaseBarBuilder` and `BaseBarBuilderFactory` to `bars`-package and renamed to `TimeBarBuilder` and `TimeBarBuilderFactory`
- Renamed `BaseBarConvertibleBuilderTest` to `BaseBarSeriesBuilderTest`
- Renamed  `Indicator.getUnstableBars` to  `Indicator.getCountOfUnstableBars`
- Moved `indicators/AbstractEMAIndicator` to `indicators/averages`-package
- Moved `indicators/DoubleEMAIndicator` to `indicators/averages`-package
- Moved `indicators/EMAIndicator` to `indicators/averages`-package
- Moved `indicators/HMAIndicator` to `indicators/averages`-package
- Moved `indicators/KAMAIndicator` to `indicators/averages`-package
- Moved `indicators/LWMAIndicator` to `indicators/averages`-package
- Moved `indicators/MMAIndicator` to `indicators/averages`-package
- Moved `indicators/SMAIndicator` to `indicators/averages`-package
- Moved `indicators/TripleEMAIndicator` to `indicators/averages`-package
- Moved `indicators/WMAIndicator` to `indicators/averages`-package
- Moved `indicators/ZLEMAIndicator` to `indicators/averages`-package
- Implemented sharing of `MathContext` in `DecimalNum`. For creating numbers, `NumFactory` implementations are the preferred way.

### Fixed
- Fixed `BaseBar.toString()` to avoid `NullPointerException` if any of its property is null
- Fixed `SMAIndicatorTest` to set the endTime of the next bar correctly
- Fixed `SMAIndicatorMovingSeriesTest` to set the endTime of the next bar correctly
- Use UTC TimeZone for `AroonOscillatorIndicatorTest`, `PivotPointIndicatorTest`
- Fixed `MockBarBuilder` to use `Instant.now` for beginTime
- Fixed `RecentSwingHighIndicatorTest` to create bars consistently
- Fixed `LSMAIndicator` to fix lsma calculation for incorrect values
- Fixed `RSIIndicator` getCountOfUnstableBars to return barCount value instead of 0 
- Fixed `RSIIndicator` calculate to return NaN during unstable period

### Changed
- Updated **jfreechart** dependency in **ta4j-examples** project from 1.5.3 to 1.5.5 to resolve [CVE-2023-52070](https://ossindex.sonatype.org/vulnerability/CVE-2023-6481?component-type=maven&component-name=ch.qos.logback%2Flogback-core)
- Updated **logback-classic** 1.4.12 > 1.5.6 to resolve [CVE-2023-6481](https://ossindex.sonatype.org/vulnerability/CVE-2023-6481?component-type=maven&component-name=ch.qos.logback%2Flogback-core)
- Cleaned code by using new java syntax `text blocks`
- Faster test execution by using `String.lines()` instead of `String` concatenation
- Improve Javadoc for `DecimalNum`and `DoubleNum`
- Allowed JUnit5 for new tests. Old remain as is.
- Updated `StochasticOscillatorKIndicator` constructor to use generic params
- Updated `StochasticRSIIndicator` to use `StochasticOscillatorKIndicator` instead of duplicating the logic
- Updated `TestUtils` assertIndicatorEquals and assertIndicatorNotEquals to handle NaN values

### Removed/Deprecated


### Added
- added `HeikinAshiBarAggregator`: Heikin-Ashi bar aggregator implementation
- added `HeikinAshiBarBuilder`: Heikin-Ashi bar builder implementation
- added `Bar.getZonedBeginTime`: the bar's begin time usable as ZonedDateTime
- added `Bar.getZonedEndTime`: the bar's end time usable as ZonedDateTime
- added `Bar.getSystemZonedBeginTime`: the bar's begin time converted to system time zone
- added `Bar.getSystemZonedEndTime`: the bar's end time converted to system time zone
- added `BarSeries.getSeriesPeriodDescriptionInSystemTimeZone`: with times printed in system's default time zone
- added `KRIIndicator`
- Added constructor with `amount` for  `EnterAndHoldCriterion`
- Added constructor with `amount` for  `VersusEnterAndHoldCriterion`
- Added `TickBarBuilder` to `bars`-package to aggregate bars after a fixed number of ticks
- Added `VolumeBarBuilder` to `bars`-package to  aggregate bars after a fixed number of contracts (volume)
- Added `TickBarBuilder` to `bars`-package
- Added `VolumeBarBuilder` to `bars`-package
- Added `Indicator.isStable`: is `true` if the indicator no longer produces incorrect values due to insufficient data
- Added `WildersMAIndicator` to `indicators.averages`-package: Wilder's moving average indicator
- Added `DMAIndicator` to `indicators.averages`-package: Displaced Moving Average (DMA) indicator
- Added `EDMAIndicator` to `indicators.averages`-package: Exponential Displaced Moving Average (EDMA) indicator
- Added `JMAIndicator` to `indicators.averages`-package: Jurik Moving Average (JMA) indicator
- Added `TMAIndicator` to `indicators.averages`-package: Trangular Moving Average (TMA) indicator
- Added `ATMAIndicator` to `indicators.averages`-package: Asymmetric Trangular Moving Average (TMA) indicator
- Added `MCGinleyMAIndicator` to `indicators.averages`-package: McGinley Moving Average (McGinleyMA) indicator
- Added `SMMAIndicator` to `indicators.averages`-package: Smoothed Moving Average (SMMA) indicator
- Added `SGMAIndicator` to `indicators.averages`-package: Savitzky-Golay Moving Average (SGMA) indicator
- Added `LSMAIndicator` to `indicators.averages`-package: Least Squares Moving Average (LSMA) indicator
- Added `KiJunV2Indicator` to `indicators.averages`-package: Kihon Moving Average (KiJunV2) indicator
- Added `VIDYAIndicator` to `indicators.averages`-package: Chande’s Variable Index Dynamic Moving Average (VIDYA) indicator
- Added `VWMAIndicator` to `indicators.averages`-package: Volume Weighted Moving Average (VWMA) indicator
- added `AverageIndicator`

## 0.17 (released September 9, 2024)

### Breaking
- Renamed **SMAIndicatorMovingSerieTest** to **SMAIndicatorMovingSeriesTest**

### Fixed
- Fixed **ta4jexamples** project still pointing to old (0.16) version of **ta4j-core**
- Fixed **SMAIndicatorMovingSeriesTest** test flakiness where on fast enough build machines the mock bars are created with the exact same end time
- Fixed NaN in **DXIndicator, MinusDIIndicator, PlusDIIndicator** if there is no trend
- Fixed look ahead bias in **RecentSwingHighIndicator** and **RecentSwingLowIndicator**

### Changed
- Implemented inner cache for **SMAIndicator**
- **BooleanTransformIndicator** remove enum constraint in favor of more flexible `Predicate`
- **EnterAndHoldReturnCriterion** replaced by `EnterAndHoldCriterion` to calculate the "enter and hold"-strategy of any criteria.
- **ATRIndicator** re-use tr by passing it as a constructor param when initializing averageTrueRangeIndicator

### Removed/Deprecated

### Added
- Added signal line and histogram to **MACDIndicator**
- Added getTransactionCostModel, getHoldingCostModel, getTrades in **TradingRecord**
- Added `Num.bigDecimalValue(DoubleNum)` to convert Num to a BigDecimal
- Added **AverageTrueRangeTrailingStopLossRule**
- Added **AverageTrueRangeStopLossRule**
- Added **AverageTrueRangeStopGainRule**
- Added **SqueezeProIndicator**
- Added **RecentSwingHighIndicator**
- Added **RecentSwingLowIndicator**
- Added **KalmanFilterIndicator**
- Added **HammerIndicator**
- Added **InvertedHammerIndicator**
- Added **HangingManIndicator**
- Added **ShootingStarIndicator**
- Added **DownTrendIndicator**
- Added **UpTrendIndicator**

## 0.16 (released May 15, 2024)

### Breaking
- **Upgraded to Java 11**
- **VersusBuyAndHoldCriterion** renamed to **`VersusEnterAndHoldCriterion`**
- **BarSeries** constructors use any instance of Num instead of Num-Function
- **GrossReturnCriterion** renamed to **`ReturnCriterion`**
- **NetProfitCriterion** and **GrossProfitCriterion** replaced by **`ProfitCriterion`**
- **NetLossCriterion** and **GrossLossCriterion** replaced by **`LossCriterion`**
- **LosingPositionsRatioCriterion** replaced by **`PositionsRatioCriterion`**
- **WinningPositionsRatioCriterion** replaced by **`PositionsRatioCriterion`**
- **Strategy#unstablePeriod** renamed to **`Strategy#unstableBars*`**
- **DateTimeIndicator** moved to package **`indicators/helpers`**
- **UnstableIndicator** moved to package **`indicators/helpers`**
- **ConvertableBaseBarBuilder** renamed to **`BaseBarConvertableBuilder`**
- **BarSeriesManager** updated to use **`TradeOnNextOpenModel`** by default, which opens new trades at index `t + 1` at the open price.
  - For strategies require the previous behaviour, i.e. trades seconds or minutes before the closing prices, **`TradeOnCurerentCloseModel`** can be passed to **BarSeriesManager**
    - For example:
      - `BarSeriesManager manager = new BarSeriesManager(barSeries, new TradeOnCurrentCloseModel())`
      - `BarSeriesManager manager = new BarSeriesManager(barSeries, transactionCostModel, holdingCostModel, tradeExecutionModel)`
- **BarSeriesManager** and **BacktestExecutor** moved to package **`backtest`**
- **BarSeries#getBeginIndex()** method returns correct begin index for bar series with max bar count

### Fixed
- **Fixed** **SuperTrendIndicator** fixed calculation when close price is the same as the previous Super Trend indicator value
- **Fixed** **ParabolicSarIndicator** fixed calculation for sporadic indices
- **ExpectancyCriterion** fixed calculation
- catch NumberFormatException if `DecimalNum.valueOf(Number)` is `NaN`
- **ProfitCriterion** fixed excludeCosts functionality as it was reversed
- **LossCriterion** fixed excludeCosts functionality as it was reversed
- **PerformanceReportGenerator** fixed netProfit and netLoss calculations to include costs
- **DifferencePercentageIndicator** fixed re-calculate instance variable on every iteration
- **ThreeWhiteSoldiersIndicator** fixed eliminated instance variable holding possible wrong value
- **ThreeBlackCrowsIndicator** fixed eliminated instance variable holding possible wrong value
- **TrailingStopLossRule** removed instance variable `currentStopLossLimitActivation` because it may not be always the correct (last) value
- sets `ClosePriceDifferenceIndicator#getUnstableBars` = `1`
- sets `ClosePriceRatioIndicator#getUnstableBars` = `1`
- sets `ConvergenceDivergenceIndicator#getUnstableBars` = `barCount`
- sets `GainIndicator#getUnstableBars` = `1`
- sets `HighestValueIndicator#getUnstableBars` = `barCount`
- sets `LossIndicator#getUnstableBars` = `1`
- sets `LowestValueIndicator#getUnstableBars` = `barCount`
- sets `TRIndicator#getUnstableBars` = `1`
- sets `PreviousValueIndicator#getUnstableBars` = `n` (= the n-th previous index)
- **PreviousValueIndicator** returns `NaN` if the (n-th) previous value of an indicator does not exist, i.e. if the (n-th) previous is below the first available index. 
- **EnterAndHoldReturnCriterion** fixes exception thrown when bar series was empty
- **BaseBarSeries** fixed `UnsupportedOperationException` when creating a bar series that is based on an unmodifiable collection
- **Num** implements Serializable

### Changed
- **BarSeriesManager** consider finishIndex when running backtest
- **BarSeriesManager** add **`holdingTransaction`**
- **BacktestExecutor** evaluates strategies in parallel when possible
- **CachedIndicator** synchronize on getValue()
- **BaseBar** defaults to **`DecimalNum`** type in all constructors
- **TRIndicator** improved calculation
- **WMAIndicator** improved calculation
- **KSTIndicator** improved calculation
- **RSIIndicator** simplify calculation
- **FisherIndicator** improved calculation
- **DoubleEMAIndicator** improved calculation
- **CMOIndicator** improved calculation
- **PearsonCorrelationIndicator** improved calculation
- **PivotPoint**-Indicators improved calculations
- **ValueAtRiskCriterion** improved calculation
- **ExpectedShortfallCriterion** improved calculation
- **SqnCriterion** improved calculation
- **NumberOfBreakEvenPositionsCriterion** shorten code
- **AverageReturnPerBarCriterion** improved calculation
- **ZLEMAIndicator** improved calculation
- **InPipeRule** improved calculation
- **SumIndicator** improved calculation
- updated pom.xml: slf4j-api to 2.0.7
- updated pom.xml: org.apache.poi to 5.2.3
- updated pom.xml: maven-jar-plugin to 3.3.0
- add `final` to properties where possible
- improved javadoc
- **SuperTrendIndicator**,**SuperTrendUpperIndicator**,**SuperTrendLowerIndicator**: optimized calculation
- **SuperTrendIndicator**, **SuperTrendLowerBandIndicator**, **SuperTrendUpperBandIndicator**: `multiplier` changed to from `Integer` to `Double`
- add missing `@Override` annotation
- **RecursiveCachedIndicator**: simplified code
- **LossIndicator**: optimize calculation
- **GainIndicator**: improved calculation
- **PriceVariationIndicator** renamed to **ClosePriceRatioIndicator** for consistency with new **ClosePriceDifferenceIndicator**
- made **UnaryOperation** and **BinaryOperation** public 

### Removed/Deprecated
- removed **Serializable** from `CostModel`
- removed `@Deprecated Bar#addTrade(double tradeVolume, double tradePrice, Function<Number, Num> numFunction)`; use `Bar#addTrade(Num tradeVolume, Num tradePrice)` instead.
- removed `@Deprecated Bar#addTrade(String tradeVolume, String tradePrice, Function<Number, Num> numFunction)`; use `Bar#addTrade(Num tradeVolume, Num tradePrice)` instead.
- removed `DecimalNum.valueOf(DecimalNum)`
- delete `.travis.yml` as this project is managed by "Github actions"

### Added
- added `TradingRecord.getStartIndex()` and `TradingRecord.getEndIndex()` to track start and end of the recording
- added **SuperTrendIndicator**
- added **SuperTrendUpperBandIndicator**
- added **SuperTrendLowerBandIndicator**
- added **Donchian Channel indicators (Upper, Lower, and Middle)**
- added `Indicator.getUnstableBars()`
- added `TransformIndicator.pow()`
- added `BarSeriesManager.getHoldingCostModel()` and `BarSeriesManager.getTransactionCostModel()`  to allow extending BarSeriesManager and reimplementing `run()`
- added `MovingAverageCrossOverRangeBacktest.java` and `ETH-USD-PT5M-2023-3-13_2023-3-15.json` test data file to demonstrate parallel strategy evaluation
- added javadoc improvements for percentage criteria
- added "lessIsBetter"-property for **AverageCriterion**
- added "lessIsBetter"-property for **RelativeStandardDeviation**
- added "lessIsBetter"-property for **StandardDeviationCriterion**
- added "lessIsBetter"-property for **StandardErrorCriterion**
- added "lessIsBetter"-property for **VarianceCriterion**
- added "lessIsBetter"-property for **NumberOfPositionsCriterion**
- added "addBase"-property for **ReturnCriterion** to include or exclude the base percentage of 1
- added **RelativeVolumeStandardDeviationIndicator**
- added **MoneyFlowIndexIndicator**
- added **IntraDayMomentumIndexIndicator**
- added **ClosePriceDifferenceIndicator**
- added **TimeSegmentedVolumeIndicator**
- added `DecimalNum.valueOf(DoubleNum)` to convert a DoubleNum to a DecimalNum.
- added `DoubleNum.valueOf(DecimalNum)` to convert a DecimalNum to a DoubleNum.
- added "TradeExecutionModel" to modify trade execution during backtesting
- added **NumIndicator** to calculate any `Num`-value for a `Bar`
- added **RunningTotalIndicator** to calculate a cumulative sum for a period.

### Fixed
- **Fixed** **CashFlow** fixed calculation with custom startIndex and endIndex
- **Fixed** **Returns** fixed calculation with custom startIndex and endIndex
- **Fixed** **ExpectedShortfallCriterion** fixed calculation with custom startIndex and endIndex
- **Fixed** **MaximumDrawDownCriterion** fixed calculation with custom startIndex and endIndex
- **Fixed** **EnterAndHoldReturnCriterion** fixed calculation with custom startIndex and endIndex
- **Fixed** **VersusEnterAndHoldCriterion** fixed calculation with custom startIndex and endIndex
- **Fixed** **BarSeriesManager** consider finishIndex when running backtest

## 0.15 (released September 11, 2022)

### Breaking
- **NumberOfConsecutiveWinningPositions** renamed to **`NumberOfConsecutivePositions`**
- **DifferencePercentage** renamed to **`DifferencePercentageIndicator`**
- **BuyAndHoldCriterion** renamed to **`EnterAndHoldCriterion`**
- **DXIndicator** moved to adx-package
- **PlusDMIndicator** moved to adx-package
- **MinusDMIndicator** moved to adx-package
- `analysis/criterion`-package moved to root
- `cost`-package moved to `analysis/cost`-package
- **AroonXXX** indicators moved to aroon package

### Fixed
- **LosingPositionsRatioCriterion** correct betterThan
- **VersusBuyAndHoldCriterionTest** NaN-Error.
- **Fixed** **`ChaikinOscillatorIndicatorTest`**
- **DecimalNum#remainder()** adds NaN-check
- **Fixed** **ParabolicSarIndicatorTest** fixed openPrice always 0 and highPrice lower than lowPrice
- **UlcerIndexIndicator** using the max price of current period instead of the highest value of last n bars
- **DurationBarAggregator** fixed aggregation of bars with gaps


### Changed
- **KeltnerChannelMiddleIndicator** changed superclass to AbstractIndicator; add GetBarCount() and toString()
- **KeltnerChannelUpperIndicator** add constructor to accept pre-constructed ATR; add GetBarCount() and toString()
- **KeltnerChannelLowerIndicator** add constructor to accept pre-constructed ATR; add GetBarCount() and toString()
- **BarSeriesManager** removed empty args constructor
- **Open|High|Low|Close** do not cache price values anymore
- **DifferenceIndicator(i1,i2)** replaced by the more flexible CombineIndicator.minus(i1,i2)
- **DoubleNum** replace redundant `toString()` call in `DoubleNum.valueOf(Number i)` with `i.doubleValue()`
- **ZeroCostModel** now extends from `FixedTransactionCostModel`

### Removed/Deprecated
- **Num** removed Serializable
- **PriceIndicator** removed

### Added
- **NumericIndicator** new class providing a fluent and lightweight api for indicator creation
- **AroonFacade**, **BollingerBandFacade**, **KeltnerChannelFacade** new classes providing a facade for indicator groups by using lightweight `NumericIndicators`
- **AbstractEMAIndicator** added getBarCount() to support future enhancements
- **ATRIndicator** "uncached" by changing superclass to AbstractIndicator; added constructor to accept TRIndicator and getter for same; added toString(); added getBarCount() to support future enhancements
- :tada: **Enhancement** added possibility to use CostModels when backtesting with the BacktestExecutor
- :tada: **Enhancement** added Num#zero, Num#one, Num#hundred
- :tada: **Enhancement** added possibility to use CostModels when backtesting with the BacktestExecutor
- :tada: **Enhancement** added Indicator#stream() method
- :tada: **Enhancement** added a new CombineIndicator, which can combine the values of two Num Indicators with a given combine-function
- **Example** added a json serialization and deserialization example of BarSeries using google-gson library
- **EnterAndHoldCriterion** added constructor with TradeType to begin with buy or sell
- :tada: **Enhancement** added Position#getStartingType() method
- :tada: **Enhancement** added **`SqnCriterion`**
- :tada: **Enhancement** added **`StandardDeviationCriterion`**
- :tada: **Enhancement** added **`RelativeStandardDeviationCriterion`**
- :tada: **Enhancement** added **`StandardErrorCriterion`**
- :tada: **Enhancement** added **`VarianceCriterion`**
- :tada: **Enhancement** added **`AverageCriterion`**
- :tada: **Enhancement** added javadoc for all rules to make clear which rule makes use of a TradingRecord
- **Enhancement** prevent Object[] allocation for varargs log.trace and log.debug calls by wrapping them in `if` blocks
- :tada: **Enhancement** added **`FixedTransactionCostModel`**
- :tada: **Enhancement** added **`AnalysisCriterion.PositionFilter`** to handle both sides within one Criterion.

## 0.14 (released April 25, 2021)

### Breaking
- **Breaking:** **`PrecisionNum`** renamed to **`DecimalNum`**
- **Breaking:** **`AverageProfitableTradesCriterion`** renamed to **`WinningTradesRatioCriterion`**
- **Breaking:** **`AverageProfitCriterion`** renamed to **`AverageReturnPerBarCriterion`**
- **Breaking:** **`BuyAndHoldCriterion`** renamed to **`BuyAndHoldReturnCriterion`**
- **Breaking:** **`RewardRiskRatioCriterion`** renamed to **`ReturnOverMaxDrawdownCriterion`**
- **Breaking:** **`ProfitLossCriterion`** moved to PnL-Package
- **Breaking:** **`ProfitLossPercentageCriterion`** moved to PnL-Package
- **Breaking:** **`TotalProfitCriterion`** renamed to **`GrossReturnCriterion`** and moved to PnL-Package.
- **Breaking:** **`TotalProfit2Criterion`** renamed to **`GrossProfitCriterion`** and moved to PnL-Package.
- **Breaking:** **`TotalLossCriterion`** renamed to **`NetLossCriterion`** and moved to PnL-Package.
- **Breaking:** package "tradereports" renamed to "reports"
- **Breaking:** **`NumberOfTradesCriterion`** renamed to **`NumberOfPositionsCriterion`**
- **Breaking:** **`NumberOfLosingTradesCriterion`** renamed to **`NumberOfLosingPositionsCriterion`**
- **Breaking:** **`NumberOfWinningTradesCriterion`** renamed to **`NumberOfWinningPositionsCriterion`**
- **Breaking:** **`NumberOfBreakEvenTradesCriterion`** renamed to **`NumberOfBreakEvenPositionsCriterion`**
- **Breaking:** **`WinningTradesRatioCriterion`** renamed to **`WinningPositionsRatioCriterion`**
- **Breaking:** **`TradeStatsReport`** renamed to **`PositionStatsReport`**
- **Breaking:** **`TradeStatsReportGenerator`** renamed to **`PositionStatsReportGenerator`**
- **Breaking:** **`TradeOpenedMinimumBarCountRule`** renamed to **`OpenedPositionMinimumBarCountRule`**
- **Breaking:** **`Trade.class`** renamed to **`Position.class`**
- **Breaking:** **`Order.class`** renamed to **`Trade.class`**
- **Breaking:** package "tradereports" renamed to "reports"
- **Breaking:** package "trading/rules" renamed to "rules"
- **Breaking:** remove Serializable from all indicators
- **Breaking:** Bar#trades: changed type from int to long


### Fixed
- **Fixed `Trade`**: problem with profit calculations on short trades.
- **Fixed `TotalLossCriterion`**: problem with profit calculations on short trades.
- **Fixed `BarSeriesBuilder`**: removed the Serializable interface
- **Fixed `ParabolicSarIndicator`**: problem with calculating in special cases
- **Fixed `BaseTimeSeries`**: can now be serialized
- **Fixed `ProfitLossPercentageCriterion`**: use entryPrice#getValue() instead of entryPrice#getPricePerAsset()

### Changed
- **Trade**: Changed the way Nums are created.
- **WinningTradesRatioCriterion** (previously AverageProfitableTradesCriterion): Changed to calculate trade profits using Trade's getProfit().
- **BuyAndHoldReturnCriterion** (previously BuyAndHoldCriterion): Changed to calculate trade profits using Trade's getProfit().
- **ExpectedShortfallCriterion**: Removed unnecessary primitive boxing.
- **NumberOfBreakEvenTradesCriterion**: Changed to calculate trade profits using Trade's getProfit().
- **NumberOfLosingTradesCriterion**: Changed to calculate trade profits using Trade's getProfit().
- **NumberOfWinningTradesCriterion**: Changed to calculate trade profits using Trade's getProfit().
- **ProfitLossPercentageCriterion**: Changed to calculate trade profits using Trade's entry and exit prices.
- **TotalLossCriterion**: Changed to calculate trade profits using Trade's getProfit().
- **TotalReturnCriterion** (previously TotalProfitCriterion): Changed to calculate trade profits using Trade's getProfit().
- **WMAIndicator**: reduced complexity of WMAIndicator implementation

### Removed/Deprecated
- **MultiplierIndicator**: replaced by TransformIndicator.
- **AbsoluteIndicator**: replaced by TransformIndicator.

### Added
- **Enhancement** Improvements on gitignore
- **Enhancement** Added TradeOpenedMinimumBarCountRule - rule to specify minimum bar count for opened trade.
- **Enhancement** Added DateTimeIndicator a new Indicator for dates.
- **Enhancement** Added DayOfWeekRule for specifying days of the week to trade.
- **Enhancement** Added TimeRangeRule for trading within time ranges.
- **Enhancement** Added floor() and ceil() to Num.class
- **Enhancement** Added getters getLow() and getUp() in CrossedDownIndicatorRule
- **Enhancement** Added BarSeriesUtils: common helpers and shortcuts for BarSeries methods.
- **Enhancement** Improvements for PreviousValueIndicator: more descriptive toString() method, validation of n-th previous bars in
- **Enhancement** Added Percentage Volume Oscillator Indicator, PVOIndicator.
- **Enhancement** Added Distance From Moving Average Indicator, DistanceFromMAIndicator.
- **Enhancement** Added Know Sure Thing Indicator, KSTIndicator.
 constructor of PreviousValueIndicator
- :tada: **Enhancement** added getGrossProfit() and getGrossProfit(BarSeries) on Trade.
- :tada: **Enhancement** added getPricePerAsset(BarSeries) on Order.
- :tada: **Enhancement** added convertBarSeries(BarSeries, conversionFunction) to BarSeriesUtils.
- :tada: **Enhancement** added UnstableIndicator.
- :tada: **Enhancement** added Chainrule.
- :tada: **Enhancement** added BarSeriesUtils#sortBars.
- :tada: **Enhancement** added BarSeriesUtils#addBars.
- :tada: **Enhancement** added Num.negate() to negate a Num value.
- :tada: **Enhancement** added **`GrossLossCriterion.class`**.
- :tada: **Enhancement** added **`NetProfitCriterion.class`**.
- :tada: **Enhancement** added chooseBest() method with parameter tradeType in AnalysisCriterion.
- :tada: **Enhancement** added **`AverageLossCriterion.class`**.
- :tada: **Enhancement** added **`AverageProfitCriterion.class`**.
- :tada: **Enhancement** added **`ProfitLossRatioCriterion.class`**.
- :tada: **Enhancement** added **`ExpectancyCriterion.class`**.
- :tada: **Enhancement** added **`ConsecutiveWinningPositionsCriterion.class`**.
- :tada: **Enhancement** added **`LosingPositionsRatioCriterion.class`**
- :tada: **Enhancement** added Position#hasProfit.
- :tada: **Enhancement** added Position#hasLoss.
- :tada: **Enhancement** exposed both EMAs in MACD indicator


## 0.13 (released November 5, 2019)

### Breaking
- **Breaking** Refactored from Max/Min to High/Low in Bar class
- **Breaking** Removed redundant constructors from BaseBar class
- **Breaking** Renamed `TimeSeries` to `BarSeries`

### Fixed
- **Fixed `BaseBarSeries`**: problem with getSubList for series with specified `maximumBarCount`.
- **Fixed return `BigDecimal` instead of `Number` in**: `PrecisionNum.getDelegate`.
- **Fixed `java.lang.ClassCastException` in**: `PrecisionNum.equals()`.
- **Fixed `java.lang.ClassCastException` in**: `DoubleNum.equals()`.
- **Fixed `java.lang.NullPointerException` in**: `NumberOfBarsCriterion.calculate(TimeSeries, Trade)` for opened trade.
- **Fixed `java.lang.NullPointerException` in**: `AverageProfitableTradesCriterion.calculate(TimeSeries, Trade)` for opened trade.
- **StopGainRule**: now correctly handles stops for sell orders
- **StopLossRule**: now correctly handles stops for sell orders
- **ProfitLossCriterion**: fixed to work properly for short trades
- **PivotPointIndicator**: fixed possible npe if first bar is not in same period
- **`IchimokuChikouSpanIndicator`**: fixed calculations - applied correct formula.
- **CloseLocationValueIndicator**: fixed special case, return zero instead of NaN if high price == low price

### Changed
- **PrecisionNum**: improve performance for methods isZero/isPositive/isPositiveOrZero/isNegative/isNegativeOrZero.
- **BaseTimeSeriesBuilder** moved from inner class to own class
- **TrailingStopLossRule** added ability to look back the last x bars for calculating the trailing stop loss

### Added
- **Enhancement** Added getters for AroonDownIndicator and AroonUpIndicator in AroonOscillatorIndicator
- **Enhancement** Added common constructors in BaseBar for BigDecimal, Double and String values
- **Enhancement** Added constructor in BaseBar with trades property
- **Enhancement** Added BaseBarBuilder and ConvertibleBaseBarBuilder - BaseBar builder classes
- **Enhancement** Added BarAggregator and TimeSeriesAggregator to allow aggregates bars and time series
- **Enhancement** Added LWMA Linearly Weighted Moving Average Indicator
- **Enhancement** Implemented trading cost models (linear transaction and borrowing cost models)
- **Enhancement** Implemented Value at Risk Analysis Criterion
- **Enhancement** Implemented Expected Shortfall Analysis Criterion
- **Enhancement** Implemented Returns class to analyze the time series of return rates. Supports logarithmic and arithmetic returns
- **Enhancement** Implemented a way to find the best result for multiple strategies by submitting a range of numbers while backtesting
- **Enhancement** Implemented NumberOfBreakEvenTradesCriterion for counting break even trades
- **Enhancement** Implemented NumberOfLosingTradesCriterion for counting losing trades
- **Enhancement** Implemented NumberOfWinningTradesCriterion for counting winning trades
- **Enhancement** Implemented NumberOfWinningTradesCriterion for counting winning trades
- **Enhancement** Implemented ProfitLossPercentageCriterion for calculating the total performance percentage of your trades
- **Enhancement** Implemented TotalProfit2Criterion for calculating the total profit of your trades
- **Enhancement** Implemented TotalLossCriterion for calculating the total loss of your trades
- **Enhancement** Added ADX indicator based strategy to ta4j-examples
- **Enhancement** TrailingStopLossRule: added possibility of calculations of TrailingStopLossRule also for open, high, low price. Added getter
for currentStopLossLimitActivation
- **Enhancement** Add constructors with parameters to allow custom implementation of ReportGenerators in BacktestExecutor
- **Enhancement** Added license checker goal on CI's pipeline
- **Enhancement** Added source format checker goal on CI's pipeline

### Removed/Deprecated

## 0.12 (released September 10, 2018)

### Breaking:
   - `Decimal` class has been replaced by new `Num` interface. Enables using `Double`, `BigDecimal` and custom data types for calculations.
   - Big changes in `TimeSeries` and `BaseTimeSeries`. Multiple new `addBar(..)` functions in `TimeSeries` allow to add data directly to the series


### Fixed
- **TradingBotOnMovingTimeSeries**: fixed calculations and ArithmeticException Overflow
- **Fixed wrong indexing in**: `Indicator.toDouble()`.
- **PrecisionNum.sqrt()**: using DecimalFormat.parse().
- **RandomWalk[High|Low]Indicator**: fixed formula (max/min of formula with n iterating from 2 to barCount)

### Changed
- **ALL INDICATORS**: `Decimal` replaced by `Num`.
- **ALL CRITERION**: Calculations modified to use `Num`.
- **AbstractIndicator**: new `AbstractIndicator#numOf(Number n)` function as counterpart of dropped `Decimal.valueOf(double|int|..)`
- **TimeSeries | Bar**: preferred way to add bar data to a `TimeSeries` is directly to the series via new `TimeSeries#addBar(time,open,high,..)` functions. It ensures to use the correct `Num` implementation of the series
- **XlsTestsUtils**: now processes xls with one or more days between data rows (daily, weekly, monthly, etc).  Also handle xls #DIV/0! calculated cells (imported as NaN.NaN)
- **CachedIndicator**: Last bar is not cached to support real time indicators
- **TimeSeries | Bar **: added new `#addPrice(price)` function that adds price to (last) bar.
- Parameter **timeFrame** renamed to **barCount**.
- **Various Rules**: added constructor that provides `Number` parameters
- **AroonUpIndicator**: redundant TimeSeries call was removed from constructor
- **AroonDownIndicator**: redundant TimeSeries call was removed from constructor
- **BaseTimeSeries**: added setDefaultFunction() to SeriesBuilder for setting the default Num type function for all new TimeSeries built by that SeriesBuilder, updated BuildTimeSeries example
- **<various>CriterionTest**: changed from explicit constructor calls to `AbstractCriterionTest.getCriterion()` calls.
- **ChopIndicator**: transparent fixes
- **StochasticRSIIndicator**: comments and params names changes to reduce confusion
- **ConvergenceDivergenceIndicator**: remove unused method
- **ChopIndicatorTest**: spelling, TODO: add better tests
- **Various Indicators**: remove double math operations, change `Math.sqrt(double)` to `Num.sqrt()`, other small improvements
- **RandomWalk[High|Low]Indicator**: renamed to `RWI[High|Low]Indicator`

### Added
- **BaseTimeSeries.SeriesBuilder**: simplifies creation of BaseTimeSeries.
- **Num**: Extracted interface of dropped `Decimal` class
- **DoubleNum**: `Num` implementation to support calculations based on `double` primitive
- **BigDecimalNum**: Default `Num` implementation of `BaseTimeSeries`
- **DifferencePercentageIndicator**: New indicator to get the difference in percentage from last value
- **PrecisionNum**: `Num` implementation to support arbitrary precision
- **TestUtils**: removed convenience methods for permuted parameters, fixed all unit tests
- **TestUtils**: added parameterized abstract test classes to allow two test runs with `DoubleNum` and `BigDecimalNum`
- **ChopIndicator** new common indicator of market choppiness (low volatility), and related 'ChopIndicatorTest' JUnit test and 'CandlestickChartWithChopIndicator' example
- **BollingerBandWidthIndicator**: added missing constructor documentation.
- **BollingerBandsLowerIndicator**: added missing constructor documentation.
- **BollingerBandsMiddleIndicator**: added missing constructor documentation.
- **TrailingStopLossRule**: new rule that is satisfied if trailing stop loss is reached
- **Num**: added Num sqrt(int) and Num sqrt()
- **pom.xml**: added support to generate ta4j-core OSGi artifact.

### Removed/Deprecated
- **Decimal**: _removed_. Replaced by `Num` interface
- **TimeSeries#addBar(Bar bar)**: _deprecated_. Use `TimeSeries#addBar(Time, open, high, low, ...)`
- **BaseTimeSeries**: _Constructor_ `BaseTimeSeries(TimeSeries defaultSeries, int seriesBeginIndex, int seriesEndIndex)` _removed_. Use `TimeSeries.getSubseries(int i, int i)` instead
- **FisherIndicator**: commented constructor removed.
- **TestUtils**: removed convenience methods for permuted parameters, fixed all unit tests
- **BaseTimeSeries**: _Constructor_ `BaseTimeSeries(TimeSeries defaultSeries, int seriesBeginIndex, int seriesEndIndex)` _removed_. Use `TimeSeries.getSubseries(int i, int i)` instead
- **BigDecimalNum**: _removed_.  Replaced by `PrecisionNum`
- **AbstractCriterionTest**: removed constructor `AbstractCriterionTest(Function<Number, Num)`.  Use `AbstractCriterionTest(CriterionFactory, Function<Number, Num>)`.
- **<various>Indicator**: removed redundant `private TimeSeries`

## 0.11 (released January 25, 2018)

- **BREAKING**: Tick has been renamed to **Bar**

### Fixed
- **ATRIndicator**: fixed calculations
- **PlusDI, MinusDI, ADX**: fixed calculations
- **LinearTransactionCostCriterion**: fixed calculations, added xls file and unit tests
- **FisherIndicator**: fixed calculations
- **ConvergenceDivergenceIndicator**: fixed NPE of optional "minStrenght"-property

### Changed
- **TotalProfitCriterion**: If not `NaN` the criterion uses the price of the `Order` and not just the close price of underlying `TimeSeries`
- **Order**: Now constructors and static `sell/buyAt(..)` functions need a price and amount parameter to satisfy correct be
behaviour of criterions (entry/exit prices can differ from corresponding close prices in `Order`)
- **JustOnceRule**: now it is possible to add another rule so that this rule is satisfied if the inner rule is satisfied for the first time
- **MeanDeviationIndicator**: moved to statistics package
- **Decimal**: use `BigDecimal::valueof` instead of instantiating a new BigDecimal for double, int and long
    - now `Decimal` extends `Number`
- **Strategy:** can now have a optional parameter "name".
- **Tick:** `Tick` has been renamed to **`Bar`** for a more appropriate description of the price movement over a set period of time.
- **MMAIndicator**: restructured and moved from `helpers` to `indicators` package
- **AverageTrueRangeIndicator**: renamed to **ATRIndicator**
- **AverageDirectionalMovementDownIndicator**: renamed to **ADXIndicator**
-  **ADXIndicator**: added new two argument constructor
- **DirectionalMovementPlusIndicator** and **DirectionalMovementPlusIndicator**: renamed to **PlusDIIndicator** and **MinusDIIndicator**
- **XlsTestsUtils**: rewritten to provide getSeries(), getIndicator(), getFinalCriterionValue(), and getTradingRecord() in support of XLSCriterionTest and XLSIndicatorTest.
- **IndicatorFactory**: made generic and renamed createIndicator() to getIndicator()
- **RSIIndicatorTest**: example showing usage of new generic unit testing of indicators
- **LinearTransactionCostCriterionTest**: example showing usage of new generic unit testing of criteria

## Added
- **ConvergenceDivergenceIndicator**: new Indicator for positive/negative convergence and divergence.
- **BooleanTransformIndicator**: new indicator to transform any decimal indicator to a boolean indicator by using logical operations.
- **DecimalTransformIndicator**: new indicator to transforms any indicator by using common math operations.
- **Decimal**: added functions `Decimal valueOf(BigDecimal)` and `BigDecimal getDelegate()`
- **AbstractEMAIndicator**: new abstract indicator for ema based indicators like MMAIndicator
- **PearsonCorrelationIndicator**: new statistic indicator with pearson correlation
- **TimeSeries**: new method `getSubSeries(int, int)` to create a sub series of the TimeSeries that stores bars exclusively between `startIndex` and `endIndex` parameters
- **IIIIndicator**: Intraday Intensity Index
- **CriterionFactory**: new functional interface to support CriterionTest
- **IndicatorTest**: new class for storing an indicator factory, allows for generic calls like getIndicator(D data, P... params) after the factory is set once in the constructor call.  Facilitates standardization across unit tests.
- **CriterionTest**: new class for storing a criterion factory, allows for generic calls like getCriterion(P... params) after the factory is set once in the constructor call.  Facilitates standardization across unit tests.
- **ExternalIndicatorTest**: new interface for fetching indicators and time series from external sources
- **ExternalCriterionTest**: new interface for fetching criteria, trading records, and time series from external sources
- **XLSIndicatorTest**: new class implementing ExternalIndicatorTest for XLS files, for use in XLS unit tests
- **XLSCriterionTest**: new class implementing ExternalCriterionTest for XLS files, for use in XLS unit tests

## Removed
- **TraillingStopLossIndicator**: no need for this as indicator. No further calculations possible after price falls below stop loss. Use `StopLossRule` or `DifferenceIndicator`

## Deprecated
- **BaseTimeSeries**: Constructor: `BaseTimeSeries(TimeSeries defaultSeries, int seriesBeginIndex, int seriesEndIndex)` use `getSubSeries(int, int)`
- **Decimal**: Method `toDouble()` use `doubleValue()`

## 0.10 (released October 30, 2017)

### VERY Important note!!!!

with the release 0.10 we have changed the previous java package definition to org.ta4j or to be more specific to org.ta4j.core (the new organisation). You have to reorganize all your references to the new packages!
In eclipse you can do this easily by selecting your sources and run "Organize imports"
_Changed ownership of the ta4j repository_: from mdeverdelhan/ta4j (stopped the maintenance) to ta4j/ta4j (new organization)

### Fixed
- **ParabolicSarIndicator**: wrong calculation fixed
- **KAMAIndicator**: stack overflow bug fixed
- **AroonUpIndicator and AroonDownIndicator**: wrong calculations fixed and can handle NaN values now

### Changed
- **BREAKING**: **new package structure**: change eu.verdelhan.ta4j to org.ta4j.ta4j-core
- **new package adx**: new location of AverageDirectionalMovementIndicator and DMI+/DMI-
- **Ownership of the ta4j repository**: from mdeverdelhan/ta4j (stopped the maintenance) to ta4j/ta4j (new organization)
- **ParabolicSarIndicator**: old constructor removed (there was no need for time frame parameter after big fix). Three new constructors for default and custom parameters.
- **HighestValueIndicator and LowestValueIndicator:** ignore also NaN values if they are at the current index


## Added
- **AroonOscillatorIndicator**: new indicator based on AroonUp/DownIndicator
- **AroonUpIndicator** and **AroonDownIndicator**: New constructor with parameter for custom indicator for min price and max price calculation
- **ROCVIndicator**: rate of Change of Volume
- **DirectionalMovementPlusIndicator**: new indicator for Directional Movement System (DMI+)
- **DirectionalMovementDownIndicator**: new indicator for Directional Movement System (DMI-)
- **ChaikinOscillatorIndicator**: new indicator.
- **InSlopeRule**: new rule that is satisfied if the slope of two indicators are within a boundary
- **IsEqualRule**: new rule that is satisfied if two indicators are equal
- **AroonUpIndicator** and **AroonDownIndicator**: new constructor with parameter for custom indicator for min price and max price calculation
- **Pivot Point Indicators Package**: new package with Indicators for calculating standard, Fibonacci and DeMark pivot points and reversals
    - **PivotPointIndicator**: new indicator for calculating the standard pivot point
        - **StandardReversalIndicator**: new indicator for calculating the standard reversals (R3,R2,R1,S1,S2,S3)
        - **FibonacciReversalIndicator**: new indicator for calculating the Fibonacci reversals (R3,R2,R1,S1,S2,S3)
    - **DeMarkPivotPointIndicator**: new indicator for calculating the DeMark pivot point
        - **DeMarkReversalIndicator**: new indicator for calculating the DeMark resistance and the DeMark support
- **IsFallingRule**: new rule that is satisfied if indicator strictly decreases within the timeFrame.
- **IsRisingRule**: new rule that is satisfied if indicator strictly increases within the timeFrame.
- **IsLowestRule**: new rule that is satisfied if indicator is the lowest within the timeFrame.
- **IsHighestRule**: new rule that is satisfied if indicator is the highest within the timeFrame.

## 0.9 (released September 7, 2017)
  - **BREAKING** drops Java 7 support
  - use `java.time` instead of `java.util.Date`
  * Added interfaces for some API basic objects
  * Cleaned whole API
  * Reordered indicators
  * Added PreviousValueIndicator
  * Fixed #162 - Added amount field into Tick constructor
  * Fixed #183 - addTrade bad calculation
  * Fixed #153, #170 - Updated StopGainRule and StopLossRule for short trades
  * Removed dependency to Joda-time
  * Dropped Java 6 and Java 7 compatibility
  * Fixed #120 - ZLEMAIndicator StackOverflowError
  * Added stochastic RSI indicator
  * Added smoothed RSI indicator
  * Fixed examples
  * Fixed #81 - Tick uses Period of 24H when it possibly means 1 Day
  * Fixed #80 - TimeSeries always iterates over all the data
  * Removed the `timePeriod` field in time series
  * Fixed #102 - RSIIndicator returns NaN when rsi == 100
  * Added periodical growth rate indicator
  * Fixed #105 - Strange calculation with Ichimoku Indicator
  * Added Random Walk Index (high/low) indicators
  * Improved performance for Williams %R indicator
  * Moved mock indicators to regular scope (renamed in Fixed*Indicator)

## 0.8 (released February 25, 2016)

  * Fixed StackOverflowErrors on recursive indicators (see #60 and #68)
  * Fixed #74 - Question on backtesting strategies with indicators calculated with enough ticks
  * Added Chande Momentum Oscillator indicator
  * Added cumulated losses/gains indicators
  * Added Range Action Verification Index indicator
  * Added MVWAP indicator
  * Added VWAP indicator
  * Added Chandelier exit indicators
  * Improved Decimal performances
  * Added Fisher indicator
  * Added KAMA indicator
  * Added Detrended Price Oscillator
  * Added Ichimoku clouds indicators
  * Added statistical indicators: Simple linear regression, Correlation coefficient, Variance, Covariance, Standard error
  * Moved standard deviation
  * Added Bollinger BandWidth and %B indicator
  * Added Keltner channel indicators
  * Added Ulcer Index and Mass Index indicators
  * Added a trailing stop-loss indicator
  * Added Coppock Curve indicator
  * Added sum indicator
  * Added candle indicators: Real body, Upper/Lower shadow, Doji, 3 black crows, 3 white soldiers, Bullish/Bearish Harami, Bullish/Bearish Engulfing
  * Added absolute indicator
  * Added Hull Moving Average indicator
  * Updated Bollinger Bands (variable multiplier, see #53)
  * Fixed #39 - Possible update for TimeSeries.run()
  * Added Chaikin Money Flow indicator
  * Improved volume indicator
  * Added Close Location Value indicator
  * Added Positive Volume Index and Negative Volume Index indicators
  * Added zero-lag EMA indicator

## 0.7 (released May 21, 2015)

  * Fixed #35 - Fix max drawdown criterion
  * Improved documentation: user's guide & contributor's guidelines
  * Fixed #37 - Update Tick.toString method
  * Fixed #36 - Missing 'Period timePeriod' in full Tick constructor
  * Updated examples
  * Improved analysis criteria (to use actual entry/exit prices instead of close prices)
  * Added price and amount to `Order`
  * Added helpers for order creation
  * Renamed `Operation` to `Order`
  * Added a record/history of a trading session (`TradingRecord`)
  * Moved the trading logic from strategies to rules
  * Refactored trade operations
  * Added a difference indicator
  * Small other API changes

## 0.6 (released February 5, 2015)

  * Added `NaN` to Decimals
  * Renamed `TADecimal` to `Decimal`
  * Fixed #24 - Error in standard deviation calculation
  * Added moving time series (& cache: #25)
  * Refactored time series and ticks
  * Added entry-pass filter and exit-pass filter strategies
  * Replaced `JustBuyOnceStrategy` and `CombinedBuyAndSellStrategy` by `JustEnterOnceStrategy` and `CombinedEntryAndExitStrategy` respectively
  * Added examples
  * Added operation type helpers
  * Added strategy execution traces through SLF4J
  * Removed `.summarize(...)` methods and `Decision` (analysis)
  * Improved performance of some indicators and strategies
  * Generalized cache to all indicators (#20)
  * Removed AssertJ dependency
  * Fixed #16 - Division by zero in updated WalkForward example

## 0.5 (released October 22, 2014)

  * Switched doubles for TADecimals (BigDecimals) in indicators
  * Semantic improvement for IndicatorOverIndicatorStrategy
  * Fixed #11 - UnknownFormatConversionException when using toString() for 4 strategies
  * Added a maximum value starter strategy
  * Added linear transaction cost (analysis criterion)
  * Removed evaluators (replaced by `.chooseBest(...)` and `.betterThan(...)` methods)
  * Added triple EMA indicator
  * Added double EMA indicator
  * Removed slicers (replaced by `.split(...)` methods)
  * Removed runner (replaced by `.run(...)` methods)
  * Added more tests
  * Removed `ConstrainedTimeSeries` (replaced by `.subseries(...)` methods)
  * Added/refactored examples (including walk-forward and candlestick chart)

## 0.4 (released May 28, 2014)

  * Fixed #2 - Tests failing in JDK8
  * Added indicators: Mean deviation, Commodity channel index, Percentage price oscillator (tests)
  * Added distance between indicator and constant
  * Added opposite strategy
  * Removed some runners
  * Added strategy runs on whole series
  * Refactored slicers
  * Removed log4j dependency
  * Added examples

## 0.3 (released March 11, 2014)

  * First public release
  * 100% Pure Java - works on any Java Platform version 6 or later
  * More than 40 technical indicators (Aroon, ATR, moving averages, parabolic SAR, RSI, etc.)
  * A powerful engine for building custom trading strategies
  * Utilities to run and compare strategies
  * Minimal 3rd party dependencies
  * MIT license
