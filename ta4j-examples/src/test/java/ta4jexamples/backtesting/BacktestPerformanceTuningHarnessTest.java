/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.backtesting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Strategy;
import org.ta4j.core.indicators.NetMomentumIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;

class BacktestPerformanceTuningHarnessTest {

    @TempDir
    Path tempDir;

    @Test
    void sliceToLastBarsReturnsSameInstanceWhenNoTruncationRequested() {
        BarSeries series = buildSeries(50);

        assertSame(series, BacktestPerformanceTuningHarness.sliceToLastBars(series, 0),
                "barCount=0 should keep full series");
        assertSame(series, BacktestPerformanceTuningHarness.sliceToLastBars(series, 50),
                "barCount==available bars should keep full series");
        assertSame(series, BacktestPerformanceTuningHarness.sliceToLastBars(series, 500),
                "barCount>available bars should keep full series");
    }

    @Test
    void sliceToLastBarsTruncatesToRequestedSizeAndPreservesLastBar() {
        BarSeries series = buildSeries(100);
        Num expectedLastClose = series.getLastBar().getClosePrice();

        BarSeries sliced = BacktestPerformanceTuningHarness.sliceToLastBars(series, 10);

        assertNotSame(series, sliced, "Sliced series should be a new instance");
        assertEquals(10, sliced.getBarCount(), "Sliced series should expose requested number of bars");
        assertEquals(expectedLastClose, sliced.getLastBar().getClosePrice(), "Sliced series should preserve last bar");
    }

    @Test
    void applyMaximumBarCountHintOverridesMaximumBarCountWithoutMutatingDelegate() {
        BarSeries series = buildSeries(20);

        assertSame(series, BacktestPerformanceTuningHarness.applyMaximumBarCountHint(series, 0),
                "hint=0 should be a no-op");

        BarSeries wrapped = BacktestPerformanceTuningHarness.applyMaximumBarCountHint(series, 123);
        assertEquals(123, wrapped.getMaximumBarCount(), "Wrapper should override BarSeries.getMaximumBarCount");
        assertEquals(series.getBarCount(), wrapped.getBarCount(), "Wrapper should preserve bar count");
        assertEquals(series.getLastBar().getClosePrice(), wrapped.getLastBar().getClosePrice(),
                "Wrapper should delegate bar access");
        assertThrows(UnsupportedOperationException.class, () -> wrapped.setMaximumBarCount(1),
                "Hint wrapper should not allow mutating maximum bar count");
    }

    @Test
    void createStrategiesRespectsRequestedCount() {
        BarSeries series = buildSeries(500);

        List<Strategy> strategies = BacktestPerformanceTuningHarness.createStrategies(series, 5);

        assertEquals(5, strategies.size(), "Should only create the requested number of strategies");
        assertTrue(strategies.stream().allMatch(strategy -> strategy != null),
                "Strategies should not contain null entries");
        assertThrows(IllegalArgumentException.class, () -> BacktestPerformanceTuningHarness.createStrategies(series, 0),
                "Zero strategies is not a meaningful request");
    }

    @Test
    void createStrategiesReusesEquivalentNetMomentumIndicatorGraphs() {
        BarSeries series = buildSeries(500);

        List<Strategy> strategies = BacktestPerformanceTuningHarness.createStrategies(series, 64);

        NetMomentumIndicator firstIndicator = entryIndicator(strategies.get(0));
        assertSame(firstIndicator, exitIndicator(strategies.get(0)),
                "Entry and exit thresholds should share one momentum graph within each strategy");
        assertTrue(strategies.stream().skip(1).anyMatch(strategy -> entryIndicator(strategy) == firstIndicator),
                "Strategies with the same RSI/timeframe/decay inputs should reuse cached indicator work");
    }

    @Test
    void isNonLinearReturnsFalseForRoughlyLinearScaling() {
        Thresholds thresholds = new Thresholds(0.25d, 1.25d);

        RunResult previous = sampleRun(1_000L, Duration.ofSeconds(10), Duration.ofSeconds(1));
        RunResult current = sampleRun(1_300L, Duration.ofSeconds(13), Duration.ofSeconds(1));

        assertFalse(BacktestPerformanceTuningHarness.isNonLinear(previous, current, thresholds),
                "Near-linear scaling should not be flagged as non-linear");
    }

    @Test
    void isNonLinearFlagsGcDominatedRuns() {
        Thresholds thresholds = new Thresholds(0.25d, 1.25d);

        RunResult previous = sampleRun(1_000L, Duration.ofSeconds(10), Duration.ofSeconds(1));
        RunResult current = sampleRun(1_300L, Duration.ofSeconds(13), Duration.ofSeconds(4));

        assertTrue(BacktestPerformanceTuningHarness.isNonLinear(previous, current, thresholds),
                "Runs dominated by GC time should be flagged as non-linear");
    }

    @Test
    void isNonLinearFlagsSlowdownBeyondThreshold() {
        Thresholds thresholds = new Thresholds(0.25d, 1.25d);

        RunResult previous = sampleRun(1_000L, Duration.ofSeconds(10), Duration.ofSeconds(1));
        RunResult current = sampleRun(1_300L, Duration.ofSeconds(25), Duration.ofSeconds(1));

        assertTrue(BacktestPerformanceTuningHarness.isNonLinear(previous, current, thresholds),
                "Runs that slow down beyond the threshold should be flagged as non-linear");
    }

    @Test
    void selectBestRecommendationPrefersHighestWorkUnits() {
        RunResult smaller = sampleRun(1_000L, Duration.ofSeconds(10), Duration.ofSeconds(1));
        RunResult larger = sampleRun(5_000L, Duration.ofSeconds(20), Duration.ofSeconds(2));

        List<VariantTuningResult> results = List.of(new VariantTuningResult(new SeriesVariant(500, 0), smaller, null),
                new VariantTuningResult(new SeriesVariant(2000, 0), larger, null));

        assertEquals(larger, BacktestPerformanceTuningHarness.selectBestRecommendation(results),
                "Best recommendation should be the last linear run with highest work units");
    }

    @Test
    void throughputPlanBuildsDeterministicMatrixCellsAndAcceptsFullBarCount() {
        HarnessCli cli = HarnessCli.parse(new String[] { "--throughputControl", "--throughputOutputDir",
                tempDir.resolve("matrix").toString(), "--matrixStrategyCounts", "2,3", "--matrixBarCounts", "5,full",
                "--matrixMaxBarCountHints", "0,4", "--executionMode", "topK", "--topK", "1", "--parallelism", "auto" });

        ThroughputControlPlan plan = ThroughputControlPlan.fromCli(cli, 10);

        assertEquals(8, plan.cells().size());
        assertEquals("s2-b5-m0", plan.cells().get(0).cellId());
        assertEquals(0, plan.cells().get(2).barCount(), "full should be represented as barCount=0");
        assertTrue(plan.resolvedParallelism() >= 1);
        assertEquals(tempDir.resolve("matrix").toAbsolutePath().normalize(), plan.outputDir());
    }

    @Test
    void throughputPlanDeduplicatesRepeatedMatrixInputs() {
        HarnessCli cli = HarnessCli.parse(new String[] { "--throughputControl", "--matrixStrategyCounts", "2,2",
                "--matrixBarCounts", "5,5,full,full", "--matrixMaxBarCountHints", "0,0" });

        ThroughputControlPlan plan = ThroughputControlPlan.fromCli(cli, 10);

        assertEquals(2, plan.cells().size(), "Duplicate matrix input values should not duplicate output cells");
        long uniqueCellIds = plan.cells().stream().map(ThroughputMatrixCell::cellId).distinct().count();
        assertEquals(plan.cells().size(), uniqueCellIds, "cellId values should remain unambiguous");
    }

    @Test
    void throughputPlanFingerprintIncludesExecutionKnobs() {
        HarnessCli base = HarnessCli.parse(new String[] { "--throughputControl", "--matrixStrategyCounts", "2",
                "--matrixBarCounts", "5", "--matrixMaxBarCountHints", "0", "--parallelism", "1" });
        HarnessCli withProgress = HarnessCli.parse(new String[] { "--throughputControl", "--matrixStrategyCounts", "2",
                "--matrixBarCounts", "5", "--matrixMaxBarCountHints", "0", "--parallelism", "1", "--progress" });
        HarnessCli withoutGc = HarnessCli.parse(new String[] { "--throughputControl", "--matrixStrategyCounts", "2",
                "--matrixBarCounts", "5", "--matrixMaxBarCountHints", "0", "--parallelism", "1", "--noGcBetweenRuns" });

        ThroughputControlPlan basePlan = ThroughputControlPlan.fromCli(base, 10);

        assertNotEquals(basePlan.specFingerprint(), ThroughputControlPlan.fromCli(withProgress, 10).specFingerprint());
        assertNotEquals(basePlan.specFingerprint(), ThroughputControlPlan.fromCli(withoutGc, 10).specFingerprint());
    }

    @Test
    void throughputPlanRejectsNegativeBarCounts() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> HarnessCli.parse(new String[] { "--matrixBarCounts", "-5" }));

        assertEquals("--matrixBarCounts values must be >= 0 or full", exception.getMessage());
    }

    @Test
    void matrixPerformanceTelemetryReportsCellsAndHypothesesPerMinute() {
        ThroughputMatrixPerformanceTracker tracker = new ThroughputMatrixPerformanceTracker();
        ThroughputMatrixCell first = new ThroughputMatrixCell("first", 2, 10, 0, ExecutionMode.FULL_RESULT, 1);
        ThroughputMatrixCell second = new ThroughputMatrixCell("second", 3, 10, 0, ExecutionMode.FULL_RESULT, 1);
        tracker.record(new ThroughputCellResult(first, sampleRun(2, 20L, Duration.ofMillis(10), Duration.ZERO), 10L));
        tracker.record(new ThroughputCellResult(second, sampleRun(3, 30L, Duration.ofMillis(20), Duration.ZERO), 20L));
        HarnessCli cli = HarnessCli
                .parse(new String[] { "--throughputOutputDir", tempDir.resolve("matrix").toString() });
        ThroughputControlPlan plan = ThroughputControlPlan.fromCli(cli, 10);

        JsonObject telemetry = tracker.toJson(30_000L, plan, HostTelemetry.capture());

        assertEquals(2, telemetry.get("cellCount").getAsInt());
        assertFalse(telemetry.getAsJsonObject("host").has("hostname"),
                "Shared benchmark artifacts should not expose raw hostnames");
        assertTrue(telemetry.getAsJsonObject("host").get("hostId").getAsString().equals("unknown")
                || telemetry.getAsJsonObject("host").get("hostId").getAsString().startsWith("sha256:"));
        assertFalse(telemetry.get("progress").getAsBoolean());
        assertTrue(telemetry.get("gcBetweenRuns").getAsBoolean());
        assertEquals(4.0d, telemetry.get("cellsPerMinute").getAsDouble());
        assertEquals(5, telemetry.get("hypothesisCount").getAsInt());
        assertEquals(10.0d, telemetry.get("hypothesesPerMinute").getAsDouble());
        assertEquals("strategy", telemetry.get("hypothesisKind").getAsString());
    }

    @Test
    void throughputControlWritesManifestCellsAndMatrixPerformance() throws Exception {
        HarnessCli cli = HarnessCli.parse(new String[] { "--throughputControl", "--throughputOutputDir",
                tempDir.resolve("output").toString(), "--matrixStrategyCounts", "2", "--matrixBarCounts", "10",
                "--matrixMaxBarCountHints", "0", "--executionMode", "topK", "--topK", "1", "--parallelism", "1" });

        BacktestPerformanceTuningHarness.runThroughputControl(buildSeries(30), cli);

        Path performancePath = tempDir.resolve("output")
                .resolve(BacktestPerformanceTuningHarness.MATRIX_PERFORMANCE_FILE);
        Path manifestPath = tempDir.resolve("output")
                .resolve(BacktestPerformanceTuningHarness.THROUGHPUT_MANIFEST_FILE);
        Path cellsPath = tempDir.resolve("output").resolve(BacktestPerformanceTuningHarness.MATRIX_CELLS_FILE);
        assertTrue(Files.isRegularFile(performancePath));
        assertTrue(Files.isRegularFile(manifestPath));
        assertTrue(Files.isRegularFile(cellsPath));
        JsonObject performance = JsonParser.parseString(Files.readString(performancePath, StandardCharsets.UTF_8))
                .getAsJsonObject();
        assertEquals(1, performance.get("cellCount").getAsInt());
        assertEquals(2, performance.get("hypothesisCount").getAsInt());
        assertTrue(performance.get("cellsPerMinute").getAsDouble() > 0.0d);
        assertTrue(performance.get("hypothesesPerMinute").getAsDouble() > 0.0d);
        assertTrue(performance.getAsJsonObject("phases").has("backtest"));
    }

    private RunResult sampleRun(long workUnits, Duration runtime, Duration gcTime) {
        return sampleRun(1, workUnits, runtime, gcTime);
    }

    private RunResult sampleRun(int strategyCount, long workUnits, Duration runtime, Duration gcTime) {
        BacktestRuntimeStats runtimeStats = new BacktestRuntimeStats(runtime, Duration.ZERO, Duration.ZERO,
                Duration.ZERO, Duration.ZERO, "{}");
        HeapSnapshot heap = new HeapSnapshot(1024L * 1024L, 1024L * 1024L, 512L * 1024L);
        GcSnapshot gcDelta = new GcSnapshot(0L, gcTime);
        return new RunResult(ExecutionMode.FULL_RESULT, strategyCount, 1, 0, Integer.MAX_VALUE, 0, Duration.ZERO,
                runtimeStats, workUnits, gcDelta, heap, heap, "MockNumFactory");
    }

    private BarSeries buildSeries(int barCount) {
        double[] data = new double[barCount];
        for (int i = 0; i < barCount; i++) {
            data[i] = i + 1d;
        }
        return new MockBarSeriesBuilder().withData(data).build();
    }

    private NetMomentumIndicator entryIndicator(Strategy strategy) {
        CrossedUpIndicatorRule rule = (CrossedUpIndicatorRule) strategy.getEntryRule();
        Indicator<Num> indicator = rule.getLow();
        return (NetMomentumIndicator) indicator;
    }

    private NetMomentumIndicator exitIndicator(Strategy strategy) {
        CrossedDownIndicatorRule rule = (CrossedDownIndicatorRule) strategy.getExitRule();
        Indicator<Num> indicator = rule.getUp();
        return (NetMomentumIndicator) indicator;
    }
}
