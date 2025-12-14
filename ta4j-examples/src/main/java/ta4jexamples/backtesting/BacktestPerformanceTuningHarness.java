/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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
 * Performance harness + example for
 * {@link BacktestExecutionResult#getTopStrategies(int, AnalysisCriterion...)}.
 * <p>
 * The harness helps tune several interrelated parameters on your hardware:
 * <ul>
 * <li>strategy count (how many strategies to evaluate)</li>
 * <li>bar series size (last-N bars)</li>
 * <li>maximum bar count hint (indicator cache window size via
 * {@link BarSeries#getMaximumBarCount()})</li>
 * <li>JVM heap size (optional: fork a child JVM per heap size)</li>
 * </ul>
 * <p>
 * The workload is a non-trivial NetMomentumIndicator-based strategy to make GC
 * and caching behavior visible.
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
     * Creates a variety of strategies using different moving average periods for
     * testing.
     *
     * @param series the bar series
     * @return a list of strategies to test
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
        LOG.info("=== Top {} Strategies ===", topStrategies.size());

        for (int i = 0; i < topStrategies.size(); i++) {
            TradingStatement statement = topStrategies.get(i);
            Strategy strategy = statement.getStrategy();

            Num netProfit = statement.getCriterionScore(netProfitCriterion)
                    .orElseGet(() -> netProfitCriterion.calculate(result.barSeries(), statement.getTradingRecord()));
            Num expectancy = statement.getCriterionScore(expectancyCriterion)
                    .orElseGet(() -> expectancyCriterion.calculate(result.barSeries(), statement.getTradingRecord()));

            LOG.info("{}. {}", (i + 1), strategy.getName());
            LOG.info("    Net Profit: {}", netProfit);
            LOG.info("    Expectancy: {}", expectancy);
            LOG.info("    Positions:  {}", statement.getTradingRecord().getPositionCount());
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

enum ExecutionMode {
    FULL_RESULT, KEEP_TOP_K
}

record RunOnceConfig(int strategyCount, int barCount, int maximumBarCountHint, ExecutionMode executionMode, int topK,
        boolean progress) {
}

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
