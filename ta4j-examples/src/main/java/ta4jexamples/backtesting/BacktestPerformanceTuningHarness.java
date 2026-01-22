/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.backtesting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.*;
import org.ta4j.core.backtest.BacktestExecutionResult;
import org.ta4j.core.backtest.BacktestExecutor;
import org.ta4j.core.backtest.ProgressCompletion;
import org.ta4j.core.criteria.ExpectancyCriterion;
import org.ta4j.core.criteria.pnl.NetProfitCriterion;
import org.ta4j.core.indicators.NetMomentumIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.reports.TradingStatement;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.serialization.DurationTypeAdapter;
import ta4jexamples.datasources.JsonFileBarSeriesDataSource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Consumer;

/**
 * Performance tuning harness for backtesting large numbers of strategies.
 * <p>
 * This class provides a comprehensive tool for optimizing backtest performance
 * by systematically testing different parameter combinations and identifying
 * optimal settings for your hardware and dataset. It helps tune several
 * interrelated performance parameters:
 * <ul>
 * <li><b>Strategy count:</b> How many strategies to evaluate in a single
 * backtest run</li>
 * <li><b>Bar series size:</b> Number of bars to use (last-N bars from the
 * dataset)</li>
 * <li><b>Maximum bar count hint:</b> Indicator cache window size via
 * {@link BarSeries#getMaximumBarCount()} to control memory usage</li>
 * <li><b>JVM heap size:</b> Optional: fork child JVMs with different heap sizes
 * to find optimal memory configuration</li>
 * </ul>
 * <p>
 * The harness uses a non-trivial NetMomentumIndicator-based strategy workload
 * to make garbage collection (GC) and caching behavior visible. It
 * automatically detects non-linear performance degradation (e.g., excessive GC
 * overhead or slowdown beyond expected scaling) and recommends optimal
 * parameter combinations.
 * <p>
 * <h2>Execution Modes</h2>
 * <p>
 * The harness supports three execution modes:
 * <ol>
 * <li><b>Run Once (default):</b> Execute a single backtest with specified
 * parameters. Useful for quick performance checks or production runs with known
 * optimal settings.</li>
 * <li><b>Tune In-Process:</b> Run multiple backtests with varying parameters to
 * find optimal settings. Tests different strategy counts, bar counts, and
 * maximum bar count hints systematically.</li>
 * <li><b>Tune Across Heaps:</b> Fork child JVMs with different heap sizes to
 * test memory configuration impact. Each child JVM runs a full tuning
 * cycle.</li>
 * </ol>
 * <p>
 * <h2>Usage Examples</h2>
 * <p>
 * <h3>Example 1: Quick Performance Check</h3> Run a single backtest with 1000
 * strategies on the last 2000 bars:
 *
 * <pre>{@code
 * java BacktestPerformanceTuningHarness \
 *   --strategies 1000 \
 *   --barCount 2000 \
 *   --executionMode full
 * }</pre>
 * <p>
 * <h3>Example 2: Find Optimal Settings</h3> Run a tuning cycle to find optimal
 * parameters for your hardware:
 *
 * <pre>{@code
 * java BacktestPerformanceTuningHarness \
 *   --tune \
 *   --tuneStrategyStart 2000 \
 *   --tuneStrategyStep 2000 \
 *   --tuneStrategyMax 20000 \
 *   --tuneBarCounts 500,1000,2000,full \
 *   --tuneMaxBarCountHints 0,512,1024,2048 \
 *   --executionMode topK \
 *   --topK 20
 * }</pre>
 *
 * This will test strategy counts from 2000 to 20000 (in steps of 2000) across
 * different bar counts and maximum bar count hints, then recommend the best
 * configuration.
 * <p>
 * <h3>Example 3: Test Different Heap Sizes</h3> Test performance across
 * different JVM heap sizes:
 *
 * <pre>{@code
 * java BacktestPerformanceTuningHarness \
 *   --tuneHeaps 4g,8g,16g \
 *   --tuneStrategyStart 5000 \
 *   --tuneStrategyMax 50000 \
 *   --executionMode topK \
 *   --topK 20
 * }</pre>
 *
 * This forks separate JVMs with 4GB, 8GB, and 16GB heaps, running a full tuning
 * cycle in each.
 * <p>
 * <h3>Example 4: Production Run with Optimal Settings</h3> After tuning, use
 * the recommended settings for a production run:
 *
 * <pre>{@code
 * java BacktestPerformanceTuningHarness \
 *   --strategies 10000 \
 *   --barCount 2000 \
 *   --maxBarCountHint 1024 \
 *   --executionMode topK \
 *   --topK 20 \
 *   --progress
 * }</pre>
 *
 * The {@code --progress} flag enables progress logging with memory usage
 * information.
 * <p>
 * <h2>Performance Tuning Workflow</h2>
 * <ol>
 * <li><b>Initial Exploration:</b> Start with a broad tuning run to identify
 * promising regions:
 *
 * <pre>{@code --tune --tuneStrategyStart 1000 --tuneStrategyStep 5000 --tuneStrategyMax 50000}</pre>
 *
 * </li>
 * <li><b>Fine-Tuning:</b> Narrow down to the promising region with smaller
 * steps:
 *
 * <pre>{@code --tune --tuneStrategyStart 8000 --tuneStrategyStep 1000 --tuneStrategyMax 15000}</pre>
 *
 * </li>
 * <li><b>Memory Optimization:</b> Test different maximum bar count hints to
 * balance memory and performance:
 *
 * <pre>{@code --tune --tuneMaxBarCountHints 0,256,512,1024,2048,4096}</pre>
 *
 * </li>
 * <li><b>Heap Size Testing:</b> If memory is a concern, test different heap
 * sizes:
 *
 * <pre>{@code --tuneHeaps 2g,4g,8g,16g}</pre>
 *
 * </li>
 * </ol>
 * <p>
 * <h2>Understanding Results</h2>
 * <p>
 * The harness outputs several types of information:
 * <ul>
 * <li><b>HARNESS_RESULT:</b> JSON-formatted results for each run, including
 * runtime statistics, GC overhead, heap usage, and work units (strategies ×
 * bars)</li>
 * <li><b>RECOMMENDED_SETTINGS:</b> Optimal parameter combinations based on
 * linear performance behavior (before non-linear degradation is detected)</li>
 * <li><b>Non-linear detection:</b> When performance degrades beyond expected
 * scaling (excessive GC overhead or slowdown ratio), the harness flags this and
 * recommends staying below that threshold</li>
 * </ul>
 * <p>
 * <h2>Strategy Generation</h2>
 * <p>
 * The harness generates strategies using a grid search over
 * NetMomentumIndicator parameters:
 * <ul>
 * <li>RSI bar count: 7 to 49 (increment: 7)</li>
 * <li>Momentum timeframe: 100 to 400 (increment: 100)</li>
 * <li>Oversold threshold: -2000 to 0 (increment: 250)</li>
 * <li>Overbought threshold: 0 to 1500 (increment: 250)</li>
 * <li>Decay factor: 0.9 to 1.0 (increment: 0.02)</li>
 * </ul>
 * This generates approximately 10,416 unique strategy combinations. When fewer
 * strategies are requested, the harness samples from this grid. When more are
 * requested, it repeats the grid with different repetition markers.
 * <p>
 * <h2>Command-Line Options</h2>
 * <p>
 * Run with {@code --help} to see all available options. Key options include:
 * <ul>
 * <li>{@code --dataset <file>}: OHLC data file (default:
 * Coinbase-ETH-USD-PT1D-20160517_20251028.json)</li>
 * <li>{@code --strategies <N>}: Number of strategies to test (default: full
 * grid ~10,416)</li>
 * <li>{@code --barCount <N>}: Number of bars to use (default: full series)</li>
 * <li>{@code --maxBarCountHint <N>}: Maximum bar count hint for indicator
 * caching (0 = disabled)</li>
 * <li>{@code --executionMode full|topK}: Execution mode (default: full)</li>
 * <li>{@code --topK <N>}: Number of top strategies to keep when using topK mode
 * (default: 20)</li>
 * <li>{@code --tune}: Enable tuning mode</li>
 * <li>{@code --tuneStrategyStart <N>}: Starting strategy count for tuning
 * (default: 2000)</li>
 * <li>{@code --tuneStrategyStep <N>}: Strategy count increment for tuning
 * (default: 2000)</li>
 * <li>{@code --tuneStrategyMax <N>}: Maximum strategy count for tuning
 * (default: 20000)</li>
 * <li>{@code --tuneBarCounts <csv>}: Bar counts to test (default:
 * 500,1000,2000,full)</li>
 * <li>{@code --tuneMaxBarCountHints <csv>}: Maximum bar count hints to test
 * (default: 0,512,1024,2048)</li>
 * <li>{@code --nonlinearGcOverhead <0..1>}: GC overhead threshold for
 * non-linear detection (default: 0.25)</li>
 * <li>{@code --nonlinearSlowdownRatio <x>}: Slowdown ratio threshold for
 * non-linear detection (default: 1.25)</li>
 * <li>{@code --tuneHeaps <csv>}: Heap sizes to test (e.g., 4g,8g,16g)</li>
 * <li>{@code --progress}: Enable progress logging with memory information</li>
 * <li>{@code --gcBetweenRuns}: Force GC between tuning runs (default:
 * true)</li>
 * </ul>
 * <p>
 * <h2>Performance Notes</h2>
 * <ul>
 * <li>The default parameter ranges generate ~10,000+ strategies.
 * BacktestExecutor automatically uses batch processing for large strategy
 * counts (&gt;1000) to prevent memory exhaustion.</li>
 * <li>If execution is too slow, consider:
 * <ol>
 * <li>Increasing increment values to reduce grid density</li>
 * <li>Narrowing MIN/MAX ranges based on preliminary results</li>
 * <li>Using coarser increments for initial exploration, then fine-tuning
 * promising regions</li>
 * </ol>
 * </li>
 * <li>The harness performs a warm-up run before tuning to stabilize JVM
 * performance metrics.</li>
 * <li>Non-linear behavior detection helps identify when increasing strategy
 * count or bar count causes performance to degrade beyond expected linear
 * scaling.</li>
 * </ul>
 * <p>
 * <h2>See Also</h2>
 * <ul>
 * <li>{@link BacktestExecutionResult#getTopStrategies(int, AnalysisCriterion...)}
 * - Method for retrieving top-performing strategies</li>
 * <li>{@link BacktestExecutor} - The underlying executor used for
 * backtesting</li>
 * <li>{@link BarSeries#getMaximumBarCount()} - Maximum bar count hint for
 * indicator caching</li>
 * </ul>
 */
public class BacktestPerformanceTuningHarness {

    // PERFORMANCE NOTE: The current ranges generate ~10,000+ strategies.
    // BacktestExecutor automatically uses batch processing for large strategy
    // counts (>1000)
    // to prevent memory exhaustion. If execution is still too slow, consider:
    // 1. Increasing INCREMENT values to reduce grid density
    // 2. Narrowing MIN/MAX ranges based on preliminary results
    // 3. Using coarser increments for initial exploration, then fine-tuning
    // promising regions
    private static final int RSI_BARCOUNT_INCREMENT = 7;
    private static final int RSI_BARCOUNT_MIN = 7;
    private static final int RSI_BARCOUNT_MAX = 49;

    private static final int MOMENTUM_TIMEFRAME_INCREMENT = 100;
    private static final int MOMENTUM_TIMEFRAME_MIN = 100;
    private static final int MOMENTUM_TIMEFRAME_MAX = 400;

    private static final int OVERBOUGHT_THRESHOLD_INCREMENT = 250;
    private static final int OVERBOUGHT_THRESHOLD_MIN = 0;
    private static final int OVERBOUGHT_THRESHOLD_MAX = 1500;

    private static final int OVERSOLD_THRESHOLD_INCREMENT = 250;
    private static final int OVERSOLD_THRESHOLD_MIN = -2000;
    private static final int OVERSOLD_THRESHOLD_MAX = 0;

    private static final double DECAY_FACTOR_INCREMENT = 0.02;
    private static final double DECAY_FACTOR_MIN = 0.9;
    private static final double DECAY_FACTOR_MAX = 1;

    private static final Logger LOG = LogManager.getLogger(BacktestPerformanceTuningHarness.class);

    static final String DEFAULT_OHLC_RESOURCE_FILE = "Coinbase-ETH-USD-PT1D-20160517_20251028.json";

    static final int DEFAULT_TOP_K = 20;
    static final int DEFAULT_TUNE_STRATEGY_START = 2_000;
    static final int DEFAULT_TUNE_STRATEGY_STEP = 2_000;
    static final int DEFAULT_TUNE_STRATEGY_MAX = 20_000;

    static final double DEFAULT_NONLINEAR_GC_OVERHEAD = 0.25d;
    static final double DEFAULT_NONLINEAR_SLOWDOWN_RATIO = 1.25d;

    static final String HARNESS_RESULT_PREFIX = "HARNESS_RESULT: ";
    static final String RECOMMENDED_SETTINGS_PREFIX = "RECOMMENDED_SETTINGS: ";

    static final Gson GSON = new GsonBuilder().registerTypeAdapter(Duration.class, new DurationTypeAdapter()).create();

    /**
     * Main entry point for the performance tuning harness.
     * <p>
     * Parses command-line arguments and executes the requested operation:
     * <ul>
     * <li>If {@code --help} is specified, prints usage information and exits</li>
     * <li>If {@code --tuneHeaps} is specified, forks child JVMs with different heap
     * sizes and runs tuning in each</li>
     * <li>If {@code --tune} is specified, runs an in-process tuning cycle to find
     * optimal parameters</li>
     * <li>Otherwise, runs a single backtest with the specified parameters</li>
     * </ul>
     * <p>
     * Example usage:
     *
     * <pre>{@code
     * // Single run
     * java BacktestPerformanceTuningHarness --strategies 1000 --barCount 2000
     *
     * // Tuning mode
     * java BacktestPerformanceTuningHarness --tune --tuneStrategyMax 20000
     *
     * // Cross-heap tuning
     * java BacktestPerformanceTuningHarness --tuneHeaps 4g,8g,16g
     * }</pre>
     *
     * @param args Command-line arguments (see {@code --help} for full list)
     * @throws Exception If an error occurs during execution
     */
    public static void main(String[] args) throws Exception {
        HarnessCli cli = HarnessCli.parse(args);
        if (cli.help) {
            logUsage();
            return;
        }

        if (!cli.tuneHeaps.isEmpty()) {
            runTuneAcrossHeaps(cli);
            return;
        }

        BarSeries baseSeries = loadSeries(cli.ohlcResourceFile);
        Objects.requireNonNull(baseSeries, "Bar series was null");

        if (cli.tune) {
            warmupOnce(baseSeries);
            runTuneInProcess(baseSeries, cli);
            return;
        }

        RunOnceConfig runConfig = new RunOnceConfig(cli.strategyCount, cli.barCount, cli.maximumBarCountHint,
                cli.executionMode, cli.topK, cli.progress);
        RunOutcome runOutcome = runOnce(baseSeries, runConfig);
        if (cli.topK > 0) {
            logTopStrategies(runOutcome.result(), cli.topK);
        }
    }

    private static void warmupOnce(BarSeries baseSeries) {
        int warmupStrategies = Math.min(250, DEFAULT_TUNE_STRATEGY_START);
        int warmupBars = Math.min(500, baseSeries.getBarCount());

        RunOnceConfig warmupConfig = new RunOnceConfig(warmupStrategies, warmupBars, 0, ExecutionMode.KEEP_TOP_K, 1,
                false);
        LOG.info("Warm-up run (strategies={}, bars={})", warmupStrategies, warmupBars);
        try {
            runOnce(baseSeries, warmupConfig);
        } catch (Exception ex) {
            LOG.warn("Warm-up failed (continuing): {}", ex.getMessage());
        }
        System.gc();
        Thread.yield();
    }

    /**
     * Executes a single backtest run with the specified configuration.
     * <p>
     * This method:
     * <ol>
     * <li>Slices the base series to the requested bar count</li>
     * <li>Applies the maximum bar count hint if specified</li>
     * <li>Creates the requested number of strategies</li>
     * <li>Executes the backtest with progress monitoring</li>
     * <li>Captures performance metrics (GC, heap, runtime statistics)</li>
     * <li>Logs results in JSON format with the {@code HARNESS_RESULT:} prefix</li>
     * </ol>
     *
     * @param baseSeries The base bar series to use
     * @param config     Configuration for this run (strategy count, bar count,
     *                   execution mode, etc.)
     * @return A {@link RunOutcome} containing both the execution result and
     *         performance metrics
     * @throws NullPointerException If baseSeries or config is null
     */
    private static RunOutcome runOnce(BarSeries baseSeries, RunOnceConfig config) {
        Objects.requireNonNull(baseSeries, "baseSeries must not be null");
        Objects.requireNonNull(config, "config must not be null");

        BarSeries series = sliceToLastBars(baseSeries, config.barCount());
        series = applyMaximumBarCountHint(series, config.maximumBarCountHint());

        long strategiesStart = System.nanoTime();
        List<Strategy> strategies = createStrategies(series, config.strategyCount());
        Duration strategiesBuildDuration = Duration.ofNanos(System.nanoTime() - strategiesStart);

        int barCount = series.getEndIndex() - series.getBeginIndex() + 1;
        long workUnits = (long) strategies.size() * (long) barCount;

        LOG.info("Backtesting {} strategies (mode={}) on {} bars (maxBarCountHint={}, heapMax={})", strategies.size(),
                config.executionMode(), barCount, series.getMaximumBarCount(),
                formatBytes(Runtime.getRuntime().maxMemory()));

        GcSnapshot gcBefore = GcSnapshot.capture();
        HeapSnapshot heapBefore = HeapSnapshot.capture();

        Consumer<Integer> progressCallback = config.progress()
                ? ProgressCompletion.loggingWithMemory(BacktestPerformanceTuningHarness.class)
                : null;
        BacktestExecutionResult result = executeBacktest(series, strategies, config.executionMode(), config.topK(),
                progressCallback);

        HeapSnapshot heapAfter = HeapSnapshot.capture();
        GcSnapshot gcAfter = GcSnapshot.capture();

        GcSnapshot gcDelta = gcAfter.delta(gcBefore);

        BacktestRuntimeStats runtimeStats = BacktestRuntimeStats.from(result.runtimeReport());

        RunResult runResult = new RunResult(config.executionMode(), strategies.size(), barCount,
                config.maximumBarCountHint(), series.getMaximumBarCount(), config.barCount(), strategiesBuildDuration,
                runtimeStats, workUnits, gcDelta, heapBefore, heapAfter,
                series.numFactory().getClass().getSimpleName());

        LOG.info("Backtest complete. runtimeReport={}", runtimeStats.runtimeReportJson());
        LOG.info(HARNESS_RESULT_PREFIX + "{}", runResult.toJson());

        return new RunOutcome(result, runResult);
    }

    private static BacktestExecutionResult executeBacktest(BarSeries series, List<Strategy> strategies,
            ExecutionMode mode, int topK, Consumer<Integer> progressCallback) {
        BacktestExecutor executor = new BacktestExecutor(series);
        Num amount = series.numFactory().numOf(1);

        if (mode == ExecutionMode.KEEP_TOP_K) {
            int effectiveTopK = Math.max(1, topK);
            AnalysisCriterion criterion = new NetProfitCriterion();
            return executor.executeAndKeepTopK(strategies, amount, Trade.TradeType.BUY, criterion, effectiveTopK,
                    progressCallback);
        }

        return executor.executeWithRuntimeReport(strategies, amount, Trade.TradeType.BUY, progressCallback);
    }

    private static void runTuneInProcess(BarSeries baseSeries, HarnessCli cli) {
        Thresholds thresholds = new Thresholds(cli.nonlinearGcOverheadThreshold, cli.nonlinearSlowdownRatioThreshold);
        TunePlan plan = TunePlan.fromCli(cli, baseSeries.getBarCount());

        LOG.info("Tuning plan: {}", plan.describe());
        LOG.info("Non-linear thresholds: {}", thresholds.describe());

        List<VariantTuningResult> variantResults = new ArrayList<>(plan.variants().size());
        for (SeriesVariant variant : plan.variants()) {
            BarSeries series = variant.apply(baseSeries);
            series = applyMaximumBarCountHint(series, variant.maximumBarCountHint());

            LOG.info("=== Series variant: {} ===", variant.describe(series));

            RunResult lastLinear = null;
            RunResult previous = null;
            RunResult firstNonLinear = null;
            for (int strategyCount : plan.strategyCounts()) {
                RunOnceConfig runConfig = new RunOnceConfig(strategyCount, variant.barCount(),
                        variant.maximumBarCountHint(), plan.executionMode(), plan.topK(), plan.progress());
                RunOutcome outcome = runOnce(series, runConfig);
                RunResult current = outcome.runResult();

                if (previous != null && isNonLinear(previous, current, thresholds)) {
                    firstNonLinear = current;
                    LOG.info("Non-linear behavior detected at strategies={} (previousLinearStrategies={})",
                            current.strategyCount(), lastLinear != null ? lastLinear.strategyCount() : null);
                    break;
                }

                lastLinear = current;
                previous = current;
                if (plan.gcBetweenRuns()) {
                    System.gc();
                    Thread.yield();
                }
            }

            if (lastLinear == null) {
                LOG.info("No linear runs recorded for {}", variant.describe(series));
            } else {
                LOG.info("Sweet spot (last linear run): {}", lastLinear.describeSweetSpot());
            }

            variantResults.add(new VariantTuningResult(variant, lastLinear, firstNonLinear));
        }

        logRecommendedSettings(cli, plan, thresholds, variantResults);
    }

    private static void logRecommendedSettings(HarnessCli cli, TunePlan plan, Thresholds thresholds,
            List<VariantTuningResult> results) {
        long heapMax = Runtime.getRuntime().maxMemory();
        LOG.info("=== Recommended settings (heapMax={}, dataset={}) ===", formatBytes(heapMax), cli.ohlcResourceFile);
        LOG.info("Non-linear definition: {}", thresholds.describe());

        RunResult best = selectBestRecommendation(results);
        if (best == null) {
            LOG.info(RECOMMENDED_SETTINGS_PREFIX + "No recommendation available (no successful linear runs).");
            return;
        }

        LOG.info(RECOMMENDED_SETTINGS_PREFIX + "BEST {}", best.describeSweetSpot());
        LOG.info(RECOMMENDED_SETTINGS_PREFIX + "BEST CLI {}", buildRunOnceArgs(cli, plan, best));

        for (VariantTuningResult result : results) {
            if (result.lastLinear() == null) {
                continue;
            }
            String label = result.variant().describeLabel();
            String transition = result.firstNonLinear() == null ? "no non-linear detected up to max tested"
                    : "non-linear at strategies=" + result.firstNonLinear().strategyCount();

            LOG.info(RECOMMENDED_SETTINGS_PREFIX + "{} strategies<={} ({}) | {}", label,
                    result.lastLinear().strategyCount(), transition, buildRunOnceArgs(cli, plan, result.lastLinear()));
        }

        LOG.info(RECOMMENDED_SETTINGS_PREFIX
                + "If you hit 'no non-linear detected', increase --tuneStrategyMax to probe further.");
    }

    /**
     * Selects the best recommendation from a list of variant tuning results.
     * <p>
     * The best recommendation is determined by:
     * <ol>
     * <li>Highest work units (strategies × bars) - indicates most work done
     * efficiently</li>
     * <li>Highest strategy count (tie-breaker)</li>
     * <li>Highest bar count (tie-breaker)</li>
     * <li>Highest effective maximum bar count hint (tie-breaker)</li>
     * </ol>
     * <p>
     * Only results with a non-null {@code lastLinear} (indicating successful linear
     * performance) are considered.
     *
     * @param results List of variant tuning results to evaluate
     * @return The best recommendation, or null if no valid results are found
     */
    static RunResult selectBestRecommendation(List<VariantTuningResult> results) {
        if (results == null || results.isEmpty()) {
            return null;
        }
        return results.stream()
                .map(VariantTuningResult::lastLinear)
                .filter(Objects::nonNull)
                .max(Comparator.comparingLong(RunResult::workUnits)
                        .thenComparingInt(RunResult::strategyCount)
                        .thenComparingInt(RunResult::barCount)
                        .thenComparingInt(RunResult::maximumBarCountHintEffective))
                .orElse(null);
    }

    private static String buildRunOnceArgs(HarnessCli cli, TunePlan plan, RunResult recommendation) {
        StringJoiner args = new StringJoiner(" ");
        args.add("--dataset");
        args.add(cli.ohlcResourceFile);
        args.add("--strategies");
        args.add(Integer.toString(recommendation.strategyCount()));

        if (recommendation.barCountRequested() > 0) {
            args.add("--barCount");
            args.add(Integer.toString(recommendation.barCountRequested()));
        }
        if (recommendation.maximumBarCountHintRequested() > 0) {
            args.add("--maxBarCountHint");
            args.add(Integer.toString(recommendation.maximumBarCountHintRequested()));
        }

        args.add("--executionMode");
        args.add(plan.executionMode() == ExecutionMode.KEEP_TOP_K ? "topK" : "full");

        if (plan.executionMode() == ExecutionMode.KEEP_TOP_K) {
            args.add("--topK");
            args.add(Integer.toString(plan.topK()));
        }

        return args.toString();
    }

    private static void runTuneAcrossHeaps(HarnessCli cli) throws Exception {
        List<String> childArgs = cli.toChildTuneArgs();
        String javaExecutable = javaExecutablePath();
        String classpath = System.getProperty("java.class.path");

        for (String heap : cli.tuneHeaps) {
            LOG.info("=== Forking tune run: heap={} ===", heap);
            List<String> command = new ArrayList<>();
            command.add(javaExecutable);
            command.add("-Xms" + heap);
            command.add("-Xmx" + heap);
            command.add("-cp");
            command.add(classpath);
            command.add(BacktestPerformanceTuningHarness.class.getName());
            command.addAll(childArgs);

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            Process process = builder.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOG.info(line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("Child JVM exited with code=" + exitCode + " for heap=" + heap);
            }
        }
    }

    private static String javaExecutablePath() {
        String javaHome = System.getProperty("java.home");
        String executable = isWindows() ? "java.exe" : "java";
        return Path.of(javaHome, "bin", executable).toString();
    }

    private static boolean isWindows() {
        String osName = System.getProperty("os.name");
        return osName != null && osName.toLowerCase(Locale.ROOT).contains("win");
    }

    private static BarSeries loadSeries(String jsonOhlcResourceFile) {
        try (InputStream resourceStream = BacktestPerformanceTuningHarness.class.getClassLoader()
                .getResourceAsStream(jsonOhlcResourceFile)) {
            if (resourceStream == null) {
                LOG.error("Resource not found: {}", jsonOhlcResourceFile);
                return null;
            }
            return JsonFileBarSeriesDataSource.DEFAULT_INSTANCE.loadSeries(resourceStream);
        } catch (IOException ex) {
            LOG.error("IOException while loading resource: {} - {}", jsonOhlcResourceFile, ex.getMessage());
            return null;
        }
    }

    /**
     * Determines if performance has degraded non-linearly between two runs.
     * <p>
     * Non-linear behavior is detected when either:
     * <ul>
     * <li>GC overhead exceeds the threshold (default: 25% of total runtime)</li>
     * <li>Normalized slowdown ratio exceeds the threshold (default: 1.25x)</li>
     * </ul>
     * <p>
     * The normalized slowdown ratio is calculated as:
     *
     * <pre>{@code
     * (runtimeRatio / workRatio)
     * }</pre>
     *
     * where runtimeRatio is the ratio of runtimes and workRatio is the ratio of
     * work units (strategies × bars). A value greater than 1.0 indicates that
     * runtime increased faster than work, suggesting non-linear scaling.
     * <p>
     * This method is used during tuning to identify the point where increasing
     * strategy count or bar count causes performance to degrade beyond expected
     * linear scaling.
     *
     * @param previous   The previous run result (baseline)
     * @param current    The current run result (to compare against baseline)
     * @param thresholds The thresholds for detecting non-linear behavior
     * @return true if non-linear behavior is detected, false otherwise
     * @throws NullPointerException If any parameter is null
     */
    static boolean isNonLinear(RunResult previous, RunResult current, Thresholds thresholds) {
        Objects.requireNonNull(previous, "previous must not be null");
        Objects.requireNonNull(current, "current must not be null");
        Objects.requireNonNull(thresholds, "thresholds must not be null");

        if (previous.workUnits() <= 0 || current.workUnits() <= 0) {
            return false;
        }
        if (previous.runtimeStats().overallRuntime().isZero() || current.runtimeStats().overallRuntime().isZero()) {
            return false;
        }

        double workRatio = current.workUnits() / (double) previous.workUnits();
        double runtimeRatio = current.runtimeStats().overallRuntime().toNanos()
                / (double) previous.runtimeStats().overallRuntime().toNanos();
        double normalizedSlowdown = runtimeRatio / workRatio;

        double gcOverhead = current.gcOverhead();
        boolean gcNonLinear = gcOverhead >= thresholds.gcOverheadThreshold();
        boolean slowdownNonLinear = normalizedSlowdown >= thresholds.slowdownRatioThreshold();

        if (gcNonLinear || slowdownNonLinear) {
            LOG.info("Non-linear check: gcOverhead={} (threshold={}), slowdown={} (threshold={})",
                    formatPercent(gcOverhead), formatPercent(thresholds.gcOverheadThreshold()),
                    String.format(Locale.ROOT, "%.3f", normalizedSlowdown),
                    String.format(Locale.ROOT, "%.3f", thresholds.slowdownRatioThreshold()));
            return true;
        }
        return false;
    }

    private static String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value * 100d);
    }

    /**
     * Slices a bar series to contain only the last N bars.
     * <p>
     * If barCount is 0, negative, or greater than or equal to the available bars,
     * the original series is returned unchanged. Otherwise, returns a sub-series
     * containing the last barCount bars.
     * <p>
     * This is useful for testing performance with different dataset sizes without
     * loading multiple files.
     *
     * @param series   The bar series to slice
     * @param barCount The number of bars to keep (0 or negative = keep all)
     * @return A sub-series containing the last barCount bars, or the original
     *         series if no slicing is needed
     * @throws NullPointerException If series is null
     */
    static BarSeries sliceToLastBars(BarSeries series, int barCount) {
        Objects.requireNonNull(series, "series must not be null");
        if (barCount <= 0) {
            return series;
        }

        int availableBars = series.getBarCount();
        if (barCount >= availableBars) {
            return series;
        }

        int endExclusive = series.getEndIndex() + 1;
        int startIndex = Math.max(0, endExclusive - barCount);
        return series.getSubSeries(startIndex, endExclusive);
    }

    /**
     * Applies a maximum bar count hint to a bar series for indicator caching
     * optimization.
     * <p>
     * The maximum bar count hint controls the size of the indicator cache window.
     * When set, indicators will only cache values for the most recent N bars,
     * reducing memory usage for large datasets.
     * <p>
     * If maximumBarCountHint is 0 or negative, the original series is returned
     * unchanged. If it matches the series' current maximum bar count, the original
     * series is returned. Otherwise, returns a wrapper that overrides
     * {@link BarSeries#getMaximumBarCount()}.
     * <p>
     * This is useful for testing the impact of indicator caching on performance and
     * memory usage.
     *
     * @param series              The bar series to wrap
     * @param maximumBarCountHint The maximum bar count hint (0 = disabled, use
     *                            series default)
     * @return A series with the maximum bar count hint applied, or the original
     *         series if no change is needed
     * @throws NullPointerException If series is null
     */
    static BarSeries applyMaximumBarCountHint(BarSeries series, int maximumBarCountHint) {
        Objects.requireNonNull(series, "series must not be null");
        if (maximumBarCountHint <= 0) {
            return series;
        }
        if (maximumBarCountHint == series.getMaximumBarCount()) {
            return series;
        }
        return new MaxBarCountHintSeries(series, maximumBarCountHint);
    }

    /**
     * Creates a variety of strategies using NetMomentumIndicator with different
     * parameter combinations for performance testing.
     * <p>
     * Generates strategies by systematically varying:
     * <ul>
     * <li>RSI bar count: 7 to 49 (increment: 7)</li>
     * <li>Momentum timeframe: 100 to 400 (increment: 100)</li>
     * <li>Oversold threshold: -2000 to 0 (increment: 250)</li>
     * <li>Overbought threshold: 0 to 1500 (increment: 250)</li>
     * <li>Decay factor: 0.9 to 1.0 (increment: 0.02)</li>
     * </ul>
     * <p>
     * This generates approximately 10,416 unique strategy combinations. When fewer
     * strategies are requested, the method samples from this grid. When more are
     * requested, it repeats the grid with different repetition markers.
     * <p>
     * Strategies use:
     * <ul>
     * <li>Entry rule: CrossedUpIndicatorRule when NetMomentumIndicator crosses
     * above oversold threshold</li>
     * <li>Exit rule: CrossedDownIndicatorRule when NetMomentumIndicator crosses
     * below overbought threshold</li>
     * </ul>
     *
     * @param series                 The bar series to use for indicator
     *                               calculations
     * @param requestedStrategyCount The number of strategies to create. Use -1 for
     *                               full grid, or a positive number to sample that
     *                               many strategies
     * @return A list of strategies to test
     * @throws NullPointerException     If series is null
     * @throws IllegalArgumentException If requestedStrategyCount is zero
     */
    static List<Strategy> createStrategies(BarSeries series, int requestedStrategyCount) {
        Objects.requireNonNull(series, "series cannot be null");

        int effectiveTarget;
        if (requestedStrategyCount < 0) {
            effectiveTarget = Integer.MAX_VALUE;
        } else if (requestedStrategyCount == 0) {
            throw new IllegalArgumentException("requestedStrategyCount must not be zero");
        } else {
            effectiveTarget = requestedStrategyCount;
        }

        List<Strategy> strategies = new ArrayList<>(requestedStrategyCount > 0 ? requestedStrategyCount : 10_416);
        int created = 0;

        int repetition = 0;
        while (created < effectiveTarget) {
            boolean fullGrid = requestedStrategyCount < 0;
            boolean addedAny = false;

            for (int rsiBarCount = RSI_BARCOUNT_MIN; rsiBarCount <= RSI_BARCOUNT_MAX; rsiBarCount += RSI_BARCOUNT_INCREMENT) {
                for (int timeFrame = MOMENTUM_TIMEFRAME_MIN; timeFrame <= MOMENTUM_TIMEFRAME_MAX; timeFrame += MOMENTUM_TIMEFRAME_INCREMENT) {
                    for (int oversoldThreshold = OVERSOLD_THRESHOLD_MIN; oversoldThreshold <= OVERSOLD_THRESHOLD_MAX; oversoldThreshold += OVERSOLD_THRESHOLD_INCREMENT) {
                        for (int overboughtThreshold = OVERBOUGHT_THRESHOLD_MIN; overboughtThreshold <= OVERBOUGHT_THRESHOLD_MAX; overboughtThreshold += OVERBOUGHT_THRESHOLD_INCREMENT) {
                            if (oversoldThreshold >= overboughtThreshold) {
                                continue;
                            }
                            for (double decayFactor = DECAY_FACTOR_MIN; decayFactor <= DECAY_FACTOR_MAX; decayFactor += DECAY_FACTOR_INCREMENT) {
                                try {
                                    Strategy strategy = createStrategy(series, rsiBarCount, timeFrame,
                                            oversoldThreshold, overboughtThreshold, decayFactor, repetition);
                                    strategies.add(strategy);
                                    created++;
                                    addedAny = true;
                                    if (created >= effectiveTarget) {
                                        return strategies;
                                    }
                                } catch (Exception e) {
                                    LOG.debug(
                                            "Skipping invalid strategy combination: rsiBarCount={}, timeFrame={}, oversoldThreshold={}, overboughtThreshold={}, decayFactor={}: {}",
                                            rsiBarCount, timeFrame, oversoldThreshold, overboughtThreshold, decayFactor,
                                            e.getMessage());
                                }
                            }
                        }
                    }
                }
            }

            if (fullGrid) {
                break;
            }
            if (!addedAny) {
                break;
            }
            repetition++;
        }

        return strategies;
    }

    /**
     * Creates a single strategy using NetMomentumIndicator with the specified
     * parameters.
     * <p>
     * The strategy uses:
     * <ul>
     * <li>RSI indicator with the specified bar count</li>
     * <li>NetMomentumIndicator wrapping the RSI with the specified timeframe and
     * decay factor</li>
     * <li>Entry rule: Buy when NetMomentumIndicator crosses above the oversold
     * threshold</li>
     * <li>Exit rule: Sell when NetMomentumIndicator crosses below the overbought
     * threshold</li>
     * </ul>
     * <p>
     * The repetition parameter is used to create multiple strategies with the same
     * parameters when more strategies are requested than the grid can provide. It's
     * included in the strategy name for identification.
     *
     * @param series              The bar series to use for indicator calculations
     * @param rsiBarCount         The number of bars to use for RSI calculation
     *                            (must be positive)
     * @param timeFrame           The timeframe for NetMomentumIndicator (must be
     *                            positive)
     * @param oversoldThreshold   The oversold threshold for entry signals
     * @param overboughtThreshold The overbought threshold for exit signals
     * @param decayFactor         The decay factor for NetMomentumIndicator
     *                            (typically 0.9 to 1.0)
     * @param repetition          The repetition number (0 for first occurrence,
     *                            incremented for repeats)
     * @return A new strategy with the specified parameters
     * @throws NullPointerException     If series is null
     * @throws IllegalArgumentException If rsiBarCount or timeFrame is not positive
     */
    static Strategy createStrategy(BarSeries series, int rsiBarCount, int timeFrame, int oversoldThreshold,
            int overboughtThreshold, double decayFactor, int repetition) {
        Objects.requireNonNull(series, "series cannot be null");

        if (rsiBarCount <= 0) {
            throw new IllegalArgumentException("rsiBarCount should be positive");
        }
        if (timeFrame <= 0) {
            throw new IllegalArgumentException("timeFrame should be positive");
        }

        ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator(series);
        NetMomentumIndicator rsiM = NetMomentumIndicator
                .forRsiWithDecay(new RSIIndicator(closePriceIndicator, rsiBarCount), timeFrame, decayFactor);
        Rule entryRule = new CrossedUpIndicatorRule(rsiM, oversoldThreshold);
        Rule exitRule = new CrossedDownIndicatorRule(rsiM, overboughtThreshold);

        String suffix = repetition > 0 ? " (rep=" + repetition + ")" : "";
        String strategyName = "Entry Crossed Up: {rsiBarCount=" + rsiBarCount + ", timeFrame=" + timeFrame
                + ", oversoldThreshold=" + oversoldThreshold + "}, Exit Crossed Down: {rsiBarCount=" + rsiBarCount
                + ", timeFrame=" + timeFrame + ", overboughtThreshold=" + overboughtThreshold + ", decayFactor="
                + decayFactor + "}" + suffix;

        return new BaseStrategy(strategyName, entryRule, exitRule);
    }

    private static void logTopStrategies(BacktestExecutionResult result, int topK) {
        AnalysisCriterion netProfitCriterion = new NetProfitCriterion();
        AnalysisCriterion expectancyCriterion = new ExpectancyCriterion();

        List<TradingStatement> topStrategies = result.getTopStrategies(topK, netProfitCriterion, expectancyCriterion);
        LOG.debug("=== Top {} Strategies ===", topStrategies.size());

        for (int i = 0; i < topStrategies.size(); i++) {
            TradingStatement statement = topStrategies.get(i);
            Strategy strategy = statement.getStrategy();

            Num netProfit = statement.getCriterionScore(netProfitCriterion)
                    .orElseGet(() -> netProfitCriterion.calculate(result.barSeries(), statement.getTradingRecord()));
            Num expectancy = statement.getCriterionScore(expectancyCriterion)
                    .orElseGet(() -> expectancyCriterion.calculate(result.barSeries(), statement.getTradingRecord()));

            LOG.debug("{}. {}", (i + 1), strategy.getName());
            LOG.debug("    Net Profit: {}", netProfit);
            LOG.debug("    Expectancy: {}", expectancy);
            LOG.debug("    Positions:  {}", statement.getTradingRecord().getPositionCount());
        }
    }

    private static void logUsage() {
        StringJoiner usage = new StringJoiner(System.lineSeparator());
        usage.add("BacktestPerformanceTuningHarness - performance harness");
        usage.add("");
        usage.add("Run once (default):");
        usage.add("  --strategies <N>           (default: full grid ~10,416)");
        usage.add("  --barCount <N>             (default: full series)");
        usage.add("  --maxBarCountHint <N>      (0 disables; default: 0)");
        usage.add("  --executionMode full|topK  (default: full)");
        usage.add("  --topK <N>                 (default: 20)");
        usage.add("  --progress                 (enable progress+memory logging)");
        usage.add("");
        usage.add("Tune in current JVM:");
        usage.add("  --tune");
        usage.add("  --tuneStrategyStart <N>        (default: " + DEFAULT_TUNE_STRATEGY_START + ")");
        usage.add("  --tuneStrategyStep <N>         (default: " + DEFAULT_TUNE_STRATEGY_STEP + ")");
        usage.add("  --tuneStrategyMax <N>          (default: " + DEFAULT_TUNE_STRATEGY_MAX + ")");
        usage.add("  --tuneBarCounts <csv>          (default: 500,1000,2000,full)");
        usage.add("  --tuneMaxBarCountHints <csv>   (default: 0,512,1024,2048)");
        usage.add("  --nonlinearGcOverhead <0..1>   (default: " + DEFAULT_NONLINEAR_GC_OVERHEAD + ")");
        usage.add("  --nonlinearSlowdownRatio <x>   (default: " + DEFAULT_NONLINEAR_SLOWDOWN_RATIO + ")");
        usage.add("  --gcBetweenRuns                (default: true)");
        usage.add("");
        usage.add("Tune across heaps (fork child JVM per heap):");
        usage.add("  --tuneHeaps <csv>  (e.g. 4g,8g,16g)");
        LOG.info(System.lineSeparator() + usage);
    }

    /**
     * Formats a byte count as a human-readable string with appropriate units.
     * <p>
     * Formats bytes using binary units (KiB, MiB, GiB, TiB) with 2 decimal places.
     * Examples:
     * <ul>
     * <li>1023 bytes → "1023 B"</li>
     * <li>1024 bytes → "1.00 KiB"</li>
     * <li>1048576 bytes → "1.00 MiB"</li>
     * </ul>
     *
     * @param bytes The number of bytes to format
     * @return A formatted string with appropriate unit (B, KiB, MiB, GiB, or TiB)
     */
    static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = new String[] { "B", "KiB", "MiB", "GiB", "TiB" };
        int unitIndex = 0;
        while (value >= 1024d && unitIndex < units.length - 1) {
            value /= 1024d;
            unitIndex++;
        }
        return String.format(Locale.ROOT, "%.2f %s", value, units[unitIndex]);
    }
}

/**
 * Execution mode for backtest runs.
 * <ul>
 * <li>{@link #FULL_RESULT}: Execute all strategies and return full results for
 * all strategies</li>
 * <li>{@link #KEEP_TOP_K}: Execute all strategies but only keep results for the
 * top K performers (more memory-efficient for large strategy counts)</li>
 * </ul>
 */
enum ExecutionMode {
    /** Execute all strategies and return full results. */
    FULL_RESULT,
    /** Execute all strategies but only keep top K results. */
    KEEP_TOP_K
}

/**
 * Configuration for a single backtest run.
 *
 * @param strategyCount       Number of strategies to test (-1 for full grid)
 * @param barCount            Number of bars to use (0 or negative for full
 *                            series)
 * @param maximumBarCountHint Maximum bar count hint for indicator caching (0 to
 *                            disable)
 * @param executionMode       Execution mode (FULL_RESULT or KEEP_TOP_K)
 * @param topK                Number of top strategies to keep when using
 *                            KEEP_TOP_K mode
 * @param progress            Whether to enable progress logging with memory
 *                            information
 */
record RunOnceConfig(int strategyCount, int barCount, int maximumBarCountHint, ExecutionMode executionMode, int topK,
        boolean progress) {
}

/**
 * Thresholds for detecting non-linear performance behavior.
 *
 * @param gcOverheadThreshold    GC overhead threshold (0.0 to 1.0, e.g., 0.25 =
 *                               25% of runtime)
 * @param slowdownRatioThreshold Normalized slowdown ratio threshold (e.g., 1.25
 *                               = 25% slowdown)
 */
record Thresholds(double gcOverheadThreshold, double slowdownRatioThreshold) {

    String describe() {
        return String.format(Locale.ROOT, "{gcOverhead=%s, slowdownRatio>=%.3f}", formatPercent(gcOverheadThreshold),
                slowdownRatioThreshold);
    }

    private static String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value * 100d);
    }
}

record TunePlan(List<Integer> strategyCounts, List<SeriesVariant> variants, ExecutionMode executionMode, int topK,
        boolean progress, boolean gcBetweenRuns) {

    static TunePlan fromCli(HarnessCli cli, int fullBarCount) {
        List<Integer> strategyCounts = cli.buildTuneStrategyCounts();
        List<SeriesVariant> variants = cli.buildSeriesVariants(fullBarCount);
        return new TunePlan(strategyCounts, variants, cli.executionMode, cli.topK, cli.progress, cli.gcBetweenRuns);
    }

    String describe() {
        return String.format(Locale.ROOT, "{strategyCounts=%s, variants=%d, executionMode=%s, topK=%d}", strategyCounts,
                variants.size(), executionMode, topK);
    }
}

record SeriesVariant(int barCount, int maximumBarCountHint) {

    BarSeries apply(BarSeries baseSeries) {
        return BacktestPerformanceTuningHarness.sliceToLastBars(baseSeries, barCount);
    }

    String describeLabel() {
        return String.format(Locale.ROOT, "barCount=%s, maxBarCountHint=%s",
                barCount <= 0 ? "full" : Integer.toString(barCount),
                maximumBarCountHint <= 0 ? "default" : Integer.toString(maximumBarCountHint));
    }

    String describe(BarSeries series) {
        return String.format(Locale.ROOT, "{barCount=%s, maxBarCountHint=%s, effectiveBars=%d}",
                barCount <= 0 ? "full" : Integer.toString(barCount),
                maximumBarCountHint <= 0 ? "default" : Integer.toString(maximumBarCountHint),
                series.getEndIndex() - series.getBeginIndex() + 1);
    }
}

record VariantTuningResult(SeriesVariant variant, RunResult lastLinear, RunResult firstNonLinear) {
}

record BacktestRuntimeStats(Duration overallRuntime, Duration minStrategyRuntime, Duration maxStrategyRuntime,
        Duration averageStrategyRuntime, Duration medianStrategyRuntime, String runtimeReportJson) {

    static BacktestRuntimeStats from(org.ta4j.core.backtest.BacktestRuntimeReport report) {
        return new BacktestRuntimeStats(report.overallRuntime(), report.minStrategyRuntime(),
                report.maxStrategyRuntime(), report.averageStrategyRuntime(), report.medianStrategyRuntime(),
                report.toString());
    }
}

record RunOutcome(BacktestExecutionResult result, RunResult runResult) {
}

/**
 * Results from a single backtest run, including performance metrics.
 *
 * @param executionMode                The execution mode used
 * @param strategyCount                Number of strategies tested
 * @param barCount                     Actual number of bars used
 * @param maximumBarCountHintRequested The maximum bar count hint that was
 *                                     requested
 * @param maximumBarCountHintEffective The effective maximum bar count hint
 *                                     applied
 * @param barCountRequested            The bar count that was requested (0 =
 *                                     full series)
 * @param strategyBuildDuration        Time taken to build all strategies
 * @param runtimeStats                 Runtime statistics from the backtest
 *                                     execution
 * @param workUnits                    Total work units (strategies × bars)
 * @param gcDelta                      GC statistics delta (after - before)
 * @param heapBefore                   Heap snapshot before execution
 * @param heapAfter                    Heap snapshot after execution
 * @param numFactory                   The NumFactory class name used
 */
record RunResult(ExecutionMode executionMode, int strategyCount, int barCount, int maximumBarCountHintRequested,
        int maximumBarCountHintEffective, int barCountRequested, Duration strategyBuildDuration,
        BacktestRuntimeStats runtimeStats, long workUnits, GcSnapshot gcDelta, HeapSnapshot heapBefore,
        HeapSnapshot heapAfter, String numFactory) {

    String toJson() {
        return BacktestPerformanceTuningHarness.GSON.toJson(this);
    }

    double gcOverhead() {
        if (runtimeStats.overallRuntime().isZero()) {
            return 0d;
        }
        return gcDelta.collectionTime().toNanos() / (double) runtimeStats.overallRuntime().toNanos();
    }

    String describeSweetSpot() {
        return String.format(Locale.ROOT,
                "{strategies=%d, bars=%d, barCount=%s, maxBarCountHint=%s (effective=%s), heapMax=%s, overallRuntime=%s, gcOverhead=%s}",
                strategyCount, barCount, barCountRequested <= 0 ? "full" : Integer.toString(barCountRequested),
                maximumBarCountHintRequested <= 0 ? "default" : Integer.toString(maximumBarCountHintRequested),
                Integer.toString(maximumBarCountHintEffective),
                BacktestPerformanceTuningHarness.formatBytes(heapAfter.maxBytes()), runtimeStats.overallRuntime(),
                String.format(Locale.ROOT, "%.2f%%", gcOverhead() * 100d));
    }
}

record HeapSnapshot(long maxBytes, long committedBytes, long usedBytes) {

    static HeapSnapshot capture() {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        return new HeapSnapshot(Runtime.getRuntime().maxMemory(), heap.getCommitted(), heap.getUsed());
    }
}

record GcSnapshot(long collections, Duration collectionTime) {

    static GcSnapshot capture() {
        long count = 0;
        long timeMillis = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long beanCount = bean.getCollectionCount();
            if (beanCount >= 0) {
                count += beanCount;
            }
            long beanTime = bean.getCollectionTime();
            if (beanTime >= 0) {
                timeMillis += beanTime;
            }
        }
        return new GcSnapshot(count, Duration.ofMillis(timeMillis));
    }

    GcSnapshot delta(GcSnapshot before) {
        return new GcSnapshot(collections - before.collections, collectionTime.minus(before.collectionTime));
    }
}

/**
 * A wrapper around a BarSeries that overrides the maximum bar count hint for
 * indicator caching.
 * <p>
 * This wrapper is used during performance tuning to test the impact of
 * different maximum bar count hints on performance and memory usage. It
 * delegates all BarSeries operations to the underlying series but overrides
 * {@link #getMaximumBarCount()} to return the specified hint.
 * <p>
 * The maximum bar count hint cannot be changed after construction
 * (setMaximumBarCount throws UnsupportedOperationException) since this is a
 * hint-only override for benchmarking purposes.
 */
final class MaxBarCountHintSeries implements BarSeries {

    private static final long serialVersionUID = 4398573823756330718L;

    private final BarSeries delegate;
    private final int maximumBarCountHint;

    MaxBarCountHintSeries(BarSeries delegate, int maximumBarCountHint) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.maximumBarCountHint = maximumBarCountHint;
    }

    @Override
    public NumFactory numFactory() {
        return delegate.numFactory();
    }

    @Override
    public BarBuilder barBuilder() {
        return delegate.barBuilder();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public Bar getBar(int i) {
        return delegate.getBar(i);
    }

    @Override
    public int getBarCount() {
        return delegate.getBarCount();
    }

    @Override
    public List<Bar> getBarData() {
        return delegate.getBarData();
    }

    @Override
    public int getBeginIndex() {
        return delegate.getBeginIndex();
    }

    @Override
    public int getEndIndex() {
        return delegate.getEndIndex();
    }

    @Override
    public int getMaximumBarCount() {
        return maximumBarCountHint;
    }

    @Override
    public void setMaximumBarCount(int maximumBarCount) {
        throw new UnsupportedOperationException("Maximum bar count is a hint-only override for benchmarking");
    }

    @Override
    public int getRemovedBarsCount() {
        return delegate.getRemovedBarsCount();
    }

    @Override
    public void addBar(Bar bar, boolean replace) {
        delegate.addBar(bar, replace);
    }

    @Override
    public void addTrade(Num tradeVolume, Num tradePrice) {
        delegate.addTrade(tradeVolume, tradePrice);
    }

    @Override
    public void addPrice(Num price) {
        delegate.addPrice(price);
    }

    @Override
    public BarSeries getSubSeries(int startIndex, int endIndex) {
        return delegate.getSubSeries(startIndex, endIndex);
    }
}

final class HarnessCli {

    boolean help;
    boolean tune;
    boolean progress;
    boolean gcBetweenRuns = true;

    int topK = BacktestPerformanceTuningHarness.DEFAULT_TOP_K;
    int barCount;
    int strategyCount = -1;
    int maximumBarCountHint;

    int tuneStrategyStart = BacktestPerformanceTuningHarness.DEFAULT_TUNE_STRATEGY_START;
    int tuneStrategyStep = BacktestPerformanceTuningHarness.DEFAULT_TUNE_STRATEGY_STEP;
    int tuneStrategyMax = BacktestPerformanceTuningHarness.DEFAULT_TUNE_STRATEGY_MAX;

    double nonlinearGcOverheadThreshold = BacktestPerformanceTuningHarness.DEFAULT_NONLINEAR_GC_OVERHEAD;
    double nonlinearSlowdownRatioThreshold = BacktestPerformanceTuningHarness.DEFAULT_NONLINEAR_SLOWDOWN_RATIO;

    String ohlcResourceFile = BacktestPerformanceTuningHarness.DEFAULT_OHLC_RESOURCE_FILE;

    ExecutionMode executionMode = ExecutionMode.FULL_RESULT;

    List<Integer> tuneBarCounts = List.of();
    List<Integer> tuneMaxBarCountHints = List.of();
    List<String> tuneHeaps = List.of();

    static HarnessCli parse(String[] args) {
        HarnessCli cli = new HarnessCli();
        if (args == null || args.length == 0) {
            return cli;
        }

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
            case "-h", "--help" -> cli.help = true;
            case "--tune" -> cli.tune = true;
            case "--progress" -> cli.progress = true;
            case "--gcBetweenRuns" -> cli.gcBetweenRuns = true;
            case "--noGcBetweenRuns" -> cli.gcBetweenRuns = false;
            case "--topK" -> cli.topK = Integer.parseInt(requireValue(args, ++i, arg));
            case "--bars", "--barCount" -> cli.barCount = Integer.parseInt(requireValue(args, ++i, arg));
            case "--strategies" -> cli.strategyCount = Integer.parseInt(requireValue(args, ++i, arg));
            case "--maxBarCountHint" -> cli.maximumBarCountHint = Integer.parseInt(requireValue(args, ++i, arg));
            case "--dataset" -> cli.ohlcResourceFile = requireValue(args, ++i, arg);
            case "--executionMode" -> cli.executionMode = parseExecutionMode(requireValue(args, ++i, arg));
            case "--tuneStrategyStart" -> cli.tuneStrategyStart = Integer.parseInt(requireValue(args, ++i, arg));
            case "--tuneStrategyStep" -> cli.tuneStrategyStep = Integer.parseInt(requireValue(args, ++i, arg));
            case "--tuneStrategyMax" -> cli.tuneStrategyMax = Integer.parseInt(requireValue(args, ++i, arg));
            case "--tuneBarCounts" -> cli.tuneBarCounts = parseCsvInts(requireValue(args, ++i, arg));
            case "--tuneMaxBarCountHints" -> cli.tuneMaxBarCountHints = parseCsvInts(requireValue(args, ++i, arg));
            case "--nonlinearGcOverhead" ->
                cli.nonlinearGcOverheadThreshold = Double.parseDouble(requireValue(args, ++i, arg));
            case "--nonlinearSlowdownRatio" ->
                cli.nonlinearSlowdownRatioThreshold = Double.parseDouble(requireValue(args, ++i, arg));
            case "--tuneHeaps" -> cli.tuneHeaps = parseCsvStrings(requireValue(args, ++i, arg));
            default -> throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }

        if (!cli.tuneHeaps.isEmpty()) {
            cli.tune = true;
        }

        return cli;
    }

    List<String> toChildTuneArgs() {
        List<String> args = new ArrayList<>();
        args.add("--tune");
        args.add("--dataset");
        args.add(ohlcResourceFile);
        args.add("--executionMode");
        args.add(executionMode == ExecutionMode.KEEP_TOP_K ? "topK" : "full");
        args.add("--topK");
        args.add(Integer.toString(topK));

        args.add("--tuneStrategyStart");
        args.add(Integer.toString(tuneStrategyStart));
        args.add("--tuneStrategyStep");
        args.add(Integer.toString(tuneStrategyStep));
        args.add("--tuneStrategyMax");
        args.add(Integer.toString(tuneStrategyMax));

        if (!tuneBarCounts.isEmpty()) {
            args.add("--tuneBarCounts");
            args.add(joinCsvInts(tuneBarCounts));
        }
        if (!tuneMaxBarCountHints.isEmpty()) {
            args.add("--tuneMaxBarCountHints");
            args.add(joinCsvInts(tuneMaxBarCountHints));
        }

        args.add("--nonlinearGcOverhead");
        args.add(Double.toString(nonlinearGcOverheadThreshold));
        args.add("--nonlinearSlowdownRatio");
        args.add(Double.toString(nonlinearSlowdownRatioThreshold));

        if (progress) {
            args.add("--progress");
        }
        if (gcBetweenRuns) {
            args.add("--gcBetweenRuns");
        } else {
            args.add("--noGcBetweenRuns");
        }

        return args;
    }

    List<Integer> buildTuneStrategyCounts() {
        if (tuneStrategyStart <= 0 || tuneStrategyStep <= 0 || tuneStrategyMax <= 0) {
            throw new IllegalArgumentException("Tune strategy counts must be positive");
        }
        if (tuneStrategyStart > tuneStrategyMax) {
            throw new IllegalArgumentException("tuneStrategyStart must be <= tuneStrategyMax");
        }

        List<Integer> counts = new ArrayList<>();
        for (int strategies = tuneStrategyStart; strategies <= tuneStrategyMax; strategies += tuneStrategyStep) {
            counts.add(strategies);
        }
        return counts;
    }

    List<SeriesVariant> buildSeriesVariants(int fullBarCount) {
        List<SeriesVariant> variants = new ArrayList<>();

        List<Integer> barCounts = tuneBarCounts.isEmpty() ? List.of(500, 1_000, 2_000, 0) : tuneBarCounts;
        for (int barCount : barCounts) {
            int normalized = barCount <= 0 ? 0 : Math.min(barCount, fullBarCount);
            variants.add(new SeriesVariant(normalized, 0));
        }

        List<Integer> hints = tuneMaxBarCountHints.isEmpty() ? List.of(0, 512, 1_024, 2_048) : tuneMaxBarCountHints;
        for (int hint : hints) {
            if (hint < 0) {
                continue;
            }
            variants.add(new SeriesVariant(0, hint));
        }

        return dedupeVariants(variants);
    }

    private List<SeriesVariant> dedupeVariants(List<SeriesVariant> variants) {
        List<SeriesVariant> deduped = new ArrayList<>();
        for (SeriesVariant candidate : variants) {
            boolean exists = false;
            for (SeriesVariant existing : deduped) {
                if (existing.barCount() == candidate.barCount()
                        && existing.maximumBarCountHint() == candidate.maximumBarCountHint()) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                deduped.add(candidate);
            }
        }
        return deduped;
    }

    private static ExecutionMode parseExecutionMode(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
        case "topk", "top_k", "keeptopk", "keep_top_k" -> ExecutionMode.KEEP_TOP_K;
        case "full", "all", "full_result", "fullresult" -> ExecutionMode.FULL_RESULT;
        default -> throw new IllegalArgumentException("Unknown executionMode: " + raw);
        };
    }

    private static String requireValue(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + flag);
        }
        return args[index];
    }

    private static List<String> parseCsvStrings(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(",")).map(String::trim).filter(part -> !part.isEmpty()).toList();
    }

    private static List<Integer> parseCsvInts(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .map(Integer::parseInt)
                .toList();
    }

    private static String joinCsvInts(List<Integer> values) {
        return values.stream().map(Object::toString).reduce((left, right) -> left + "," + right).orElse("");
    }
}
